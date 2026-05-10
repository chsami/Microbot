package net.runelite.client.plugins.microbot.util.leaguetransport;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.logging.Rs2LogRateLimit;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Locked-region gamemessage handling for Leagues transport blacklist attribution.
 */
@Slf4j
public final class LeaguesTransportChat
{
	private static final AtomicInteger LEAGUES_LOCK_CHAT_TRUNC_WARN = new AtomicInteger(0);
	private static final int LEAGUES_STALE_LOCK_CHAT_INFO_INTERVAL = 50;
	private static final AtomicInteger LEAGUES_STALE_LOCK_CHAT_IGNORED = new AtomicInteger(0);

	private static final String LEAGUES_AREA_TOKEN = " area";
	private static final int LEAGUES_LOCK_ATTRIBUTED_INFO_INTERVAL = 25;
	private static final AtomicInteger LEAGUES_LOCK_ATTRIBUTED_INFO = new AtomicInteger(0);
	private static final int LEAGUES_LOCK_ATTRIBUTED_DEBUG_INTERVAL = 25;
	private static final AtomicInteger LEAGUES_LOCK_ATTRIBUTED_DEBUG = new AtomicInteger(0);
	private static final int LEAGUES_LOCK_REROUTE_INFO_INTERVAL = 25;
	private static final AtomicInteger LEAGUES_LOCK_REROUTE_INFO = new AtomicInteger(0);

	private LeaguesTransportChat()
	{
	}

	static void onLockedRegionGameMessage(String msg)
	{
		if (!Rs2LeaguesTransport.isLeaguesActive())
		{
			return;
		}
		if (msg == null)
		{
			return;
		}
		boolean hasAccess = msg.contains("access") || msg.contains("Access");
		boolean hasArea = msg.contains(" area") || msg.contains(" Area");
		if (!hasAccess || !hasArea)
		{
			return;
		}
		String lower = msg.toLowerCase(Locale.ROOT);
		if (!isLeaguesLockedAccessMessageLower(lower))
		{
			return;
		}

		String rawForMatch = clipLeaguesLockChatRawForMatch(msg);
		if (msg.length() > Rs2LeaguesTransport.LEAGUES_LOCK_CHAT_MAX_NORMALIZE_CHARS
				&& Rs2LogRateLimit.everyN(LEAGUES_LOCK_CHAT_TRUNC_WARN, Rs2LeaguesTransport.LEAGUES_LOCK_CHAT_TRUNC_WARN_INTERVAL))
		{
			log.warn("[Leagues] locked-region gamemessage length {} exceeds cap {}; matching on first {} chars only",
					msg.length(), Rs2LeaguesTransport.LEAGUES_LOCK_CHAT_MAX_NORMALIZE_CHARS, rawForMatch.length());
		}

		String region = Rs2LeaguesTransport.captureLockedRegionFromChatRaw(rawForMatch).orElse(null);
		if (region != null)
		{
			handleLeaguesLockedRegionMatch(region, rawForMatch);
		}
	}

	private static void handleLeaguesLockedRegionMatch(String region, String rawForMatch)
	{
		long nowMs = System.currentTimeMillis();
		Optional<LeaguesTransportAttemptSnapshot> snapOpt = Rs2LeaguesTransport.findTransportAttemptForLockedRegionChat(
				region, nowMs, Rs2LeaguesTransport.LEAGUES_LOCK_CHAT_MAX_ATTEMPT_AGE_MS);
		if (!snapOpt.isPresent())
		{
			Rs2Walker.Telemetry.incrementLeaguesLockStale();
			handleLeaguesLockedRegionStale(region, -1);
			return;
		}
		LeaguesTransportAttemptSnapshot snap = snapOpt.get();
		Integer packedDest = snap.getPackedDest();
		String methodSafe = snap.getMethod() != null ? snap.getMethod() : "";
		long ageMs = nowMs - snap.getTsMs();

		if (packedDest == null || ageMs > Rs2LeaguesTransport.LEAGUES_LOCK_CHAT_MAX_ATTEMPT_AGE_MS)
		{
			Rs2Walker.Telemetry.incrementLeaguesLockStale();
			handleLeaguesLockedRegionStale(region, ageMs);
			Rs2LeaguesTransport.clearLastTransportAttempt();
			return;
		}

		boolean willDebug = log.isDebugEnabled()
				&& Rs2LogRateLimit.everyN(LEAGUES_LOCK_ATTRIBUTED_DEBUG, LEAGUES_LOCK_ATTRIBUTED_DEBUG_INTERVAL);
		boolean willInfo = !willDebug
				&& Rs2LogRateLimit.everyN(LEAGUES_LOCK_ATTRIBUTED_INFO, LEAGUES_LOCK_ATTRIBUTED_INFO_INTERVAL);
		if (willDebug || willInfo)
		{
			var dest = WorldPointUtil.unpackWorldPoint(packedDest);
			if (willDebug)
			{
				log.debug("[Leagues] locked-region rawMsg='{}' region='{}' method='{}' destPacked={} dest={}",
						rawForMatch,
						region,
						methodSafe,
						packedDest,
						dest);
			}
			else
			{
				log.info("[Leagues] locked-region region='{}' method='{}' destPacked={} dest={} (summary every {} msgs)",
						region,
						methodSafe,
						packedDest,
						dest,
						LEAGUES_LOCK_ATTRIBUTED_INFO_INTERVAL);
			}
		}

		boolean recorded = Rs2LeaguesTransport.recordBlockedDestinationFromChat(
				region,
				packedDest,
				methodSafe);
		if (!recorded)
		{
			return;
		}

		if (Rs2LogRateLimit.everyN(LEAGUES_LOCK_REROUTE_INFO, LEAGUES_LOCK_REROUTE_INFO_INTERVAL))
		{
			log.info("[Leagues] reroute: locked region='{}' method='{}' destPacked={} (summary every {} msgs)",
					region, methodSafe, packedDest, LEAGUES_LOCK_REROUTE_INFO_INTERVAL);
		}
		if (!Rs2LeaguesTransport.shouldRecalculatePathAfterLock(region, packedDest))
		{
			return;
		}
		Client client = Microbot.getClient();
		if (client == null)
		{
			return;
		}
		if (client.isClientThread())
		{
			Rs2Walker.recalculatePath();
		}
		else
		{
			var clientThread = Microbot.getClientThread();
			if (clientThread == null)
			{
				return;
			}
			clientThread.invokeLater(Rs2Walker::recalculatePath);
		}
	}

	private static boolean isLeaguesLockedAccessMessageLower(String lower)
	{
		if (lower == null)
		{
			return false;
		}
		int accessIdx = lower.indexOf("access to the ");
		if (accessIdx < 0)
		{
			return false;
		}
		if (lower.indexOf(LEAGUES_AREA_TOKEN, accessIdx) < 0)
		{
			return false;
		}
		return lower.indexOf("haven't unlocked access") >= 0
				|| lower.indexOf("havent unlocked access") >= 0
				|| lower.indexOf("don't have access") >= 0
				|| lower.indexOf("do not have access") >= 0
				|| lower.indexOf("cannot access to the ") >= 0
				|| lower.indexOf("cannot access the ") >= 0;
	}

	private static void handleLeaguesLockedRegionStale(String region, long ageMs)
	{
		if (!Rs2LogRateLimit.everyN(LEAGUES_STALE_LOCK_CHAT_IGNORED, LEAGUES_STALE_LOCK_CHAT_INFO_INTERVAL))
		{
			return;
		}
		int n = LEAGUES_STALE_LOCK_CHAT_IGNORED.get();
		String age = ageMs >= 0 ? Long.toString(ageMs) : "unknown";
		log.info("[Leagues] locked-region stale/no-attempt summary: count={} lastRegion='{}' lastAgeMs={}",
				n, region, age);
		if (log.isDebugEnabled())
		{
			log.debug("[Leagues] locked-region msg ignored (stale/no attempt): region='{}' ageMs={}", region, age);
		}
	}

	private static String clipLeaguesLockChatRawForMatch(String msg)
	{
		return msg.length() > Rs2LeaguesTransport.LEAGUES_LOCK_CHAT_MAX_NORMALIZE_CHARS
				? msg.substring(0, Rs2LeaguesTransport.LEAGUES_LOCK_CHAT_MAX_NORMALIZE_CHARS)
				: msg;
	}

	@VisibleForTesting
	public static boolean isLeaguesLockedAccessMessage(String msg)
	{
		if (msg == null)
		{
			return false;
		}
		return isLeaguesLockedAccessMessageLower(msg.toLowerCase(Locale.ROOT));
	}

	/**
	 * First capture after sanitize on {@code rawForMatch}, for unit tests.
	 */
	@VisibleForTesting
	public static String leaguesLockedRegionCapturedRegionAfterNormalizeForTests(String rawForMatch)
	{
		if (rawForMatch == null)
		{
			return null;
		}
		String clipped = clipLeaguesLockChatRawForMatch(rawForMatch);
		return Rs2LeaguesTransport.captureLockedRegionFromChatRaw(clipped).orElse(null);
	}
}
