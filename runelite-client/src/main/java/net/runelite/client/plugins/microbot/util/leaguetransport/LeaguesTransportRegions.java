package net.runelite.client.plugins.microbot.util.leaguetransport;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.util.logging.Rs2LogRateLimit;
import net.runelite.client.plugins.microbot.util.text.Rs2TextSanitizer;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Leagues region parsing and locked-region chat handling.
 */
@Slf4j
final class LeaguesTransportRegions
{
	private LeaguesTransportRegions()
	{
	}

	static final Pattern LEAGUES_AREA_PREFIX = Pattern.compile("(?i)leagues\\s*area:\\s*");
	static final Pattern LEAGUES_LOCKED_REGION_CHAT = Pattern.compile(
			"(?:"
					+ "haven[''\\u2019\\u2018\\u02BC\\u2032`]*t\\s+unlocked\\s+access\\s+to\\s+the"
					+ "|don[''\\u2019\\u2018\\u02BC\\u2032`]*t\\s+have\\s+access\\s+to\\s+the"
					+ "|do\\s+not\\s+have\\s+access\\s+to\\s+the"
					+ ")\\s+(.+)\\s+area(?:\\s*\\([^)]+\\))?(?:\\s+\\p{Alnum}+)*\\s*[\\p{Punct}]*$");

	private static final Set<String> LEAGUES_LOCKED_REGION_PARSE_MISS_SAMPLES = ConcurrentHashMap.newKeySet();
	private static final Set<String> LEAGUES_PARSE_MISS_AT_CAP_SEEN = ConcurrentHashMap.newKeySet();
	private static final AtomicInteger LEAGUES_PARSE_MISS_AT_CAP_SEEN_COUNT = new AtomicInteger(0);
	private static final int LEAGUES_PARSE_MISS_AT_CAP_SEEN_MAX = 512;
	private static final Object LEAGUES_PARSE_MISS_SAMPLES_LOCK = new Object();
	private static final Object LEAGUES_PARSE_MISS_AT_CAP_LOCK = new Object();
	private static final int LEAGUES_PARSE_MISS_DISTINCT_LOG_CAP = 64;
	private static final AtomicInteger LEAGUES_PARSE_MISS_AFTER_CAP_COUNT = new AtomicInteger(0);
	private static final int LEAGUES_PARSE_MISS_AFTER_CAP_LOG_INTERVAL = 50;
	private static final AtomicInteger LEAGUES_PARSE_MISS_INFO = new AtomicInteger(0);
	private static final int LEAGUES_PARSE_MISS_INFO_INTERVAL = 25;
	private static final AtomicInteger LEAGUES_PARSE_MISS_AT_CAP_SEEN_FULL_LOG = new AtomicInteger(0);
	private static final int LEAGUES_PARSE_MISS_AT_CAP_SEEN_FULL_LOG_INTERVAL = 25;

	private static final AtomicInteger LEAGUES_BLOCKED_DEST_FROM_CHAT_INFO = new AtomicInteger(0);
	private static final int LEAGUES_BLOCKED_DEST_FROM_CHAT_INFO_INTERVAL = 25;

	private static final long LEAGUES_REROUTE_DEDUPE_WINDOW_MS = 10_000L;
	private static final Object LEAGUES_REROUTE_LOCK = new Object();
	private static volatile long lastLeaguesRerouteMs = 0L;
	private static volatile int lastLeaguesReroutePackedDest = Integer.MIN_VALUE;
	private static volatile String lastLeaguesRerouteRegion = "";

	static LeaguesRegion parseRegionName(String regionNameRaw)
	{
		String s = normalizeRegionNameForLockedChat(regionNameRaw);
		if (s.isEmpty())
		{
			return null;
		}
		return parseRegionNameNormalized(s);
	}

	static LeaguesRegion parseRegionNameNormalized(String s)
	{
		if (s.contains("misthalin"))
		{
			return LeaguesRegion.MISTHALIN;
		}
		if (s.contains("kourend") || s.contains("kebos"))
		{
			return LeaguesRegion.KEBOS_AND_KOUREND;
		}
		if (s.contains("varlamore"))
		{
			return LeaguesRegion.VARLAMORE;
		}
		if (s.contains("fremennik"))
		{
			return LeaguesRegion.FREMENNIK;
		}
		if (s.contains("tirannwn"))
		{
			return LeaguesRegion.TIRANNWN;
		}
		if (s.contains("morytania"))
		{
			return LeaguesRegion.MORYTANIA;
		}
		if (s.contains("wilderness"))
		{
			return LeaguesRegion.WILDERNESS;
		}
		if (s.contains("karamja"))
		{
			return LeaguesRegion.KARAMJA;
		}
		if (s.contains("kandarin"))
		{
			return LeaguesRegion.KANDARIN;
		}
		if (s.contains("asgarnia"))
		{
			return LeaguesRegion.ASGARNIA;
		}
		if (s.contains("kharidian"))
		{
			return LeaguesRegion.DESERT;
		}
		if (s.contains("desert"))
		{
			return LeaguesRegion.DESERT;
		}
		return null;
	}

	static String normalizeRegionNameForLockedChat(String regionNameRaw)
	{
		if (regionNameRaw == null)
		{
			return "";
		}
		return regionNameRaw.replace('’', '\'').trim().toLowerCase(Locale.ROOT);
	}

	static Optional<String> captureLockedRegionFromChatRaw(String rawForMatch)
	{
		return captureLockedRegionFromSanitizedLower(Rs2TextSanitizer.sanitizeForParsing(rawForMatch));
	}

	static Optional<String> captureLockedRegionFromSanitizedLower(String sanitizedLower)
	{
		return Rs2TextSanitizer.captureFirstGroup(LEAGUES_LOCKED_REGION_CHAT, sanitizedLower);
	}

	static boolean recordBlockedDestinationFromChat(String regionNameRaw, Integer packedDest, String method)
	{
		if (packedDest == null)
		{
			return false;
		}
		String norm = normalizeRegionNameForLockedChat(regionNameRaw);
		LeaguesRegion region = norm.isEmpty() ? null : parseRegionNameNormalized(norm);
		if (region == null)
		{
			if (norm.isEmpty())
			{
				return false;
			}
			LeaguesTransportPersistence.persistBlacklistDestination(packedDest, null, method);
			LeaguesTransportAttempts.clearLastTransportAttempt();
			String missKey = norm.length() > 160
					? norm.substring(0, 160) + "|h" + Integer.toHexString(norm.hashCode())
					: norm;
			boolean emitSampleLog = false;
			boolean atCapSkip = false;

			synchronized (LEAGUES_PARSE_MISS_SAMPLES_LOCK)
			{
				int distinct = LEAGUES_LOCKED_REGION_PARSE_MISS_SAMPLES.size();
				if (distinct < LEAGUES_PARSE_MISS_DISTINCT_LOG_CAP)
				{
					if (!LEAGUES_LOCKED_REGION_PARSE_MISS_SAMPLES.add(missKey))
					{
						return true;
					}
					emitSampleLog = true;
					Rs2Walker.Telemetry.incrementLeaguesLockParseMiss();
				}
				else
				{
					if (LEAGUES_LOCKED_REGION_PARSE_MISS_SAMPLES.contains(missKey))
					{
						return true;
					}
					atCapSkip = true;
				}
			}

			if (atCapSkip)
			{
				synchronized (LEAGUES_PARSE_MISS_AT_CAP_LOCK)
				{
					if (LEAGUES_PARSE_MISS_AT_CAP_SEEN_COUNT.get() >= LEAGUES_PARSE_MISS_AT_CAP_SEEN_MAX
							&& !LEAGUES_PARSE_MISS_AT_CAP_SEEN.contains(missKey))
					{
						boolean emitOverflowSummary = Rs2LogRateLimit.everyN(
								LEAGUES_PARSE_MISS_AT_CAP_SEEN_FULL_LOG, LEAGUES_PARSE_MISS_AT_CAP_SEEN_FULL_LOG_INTERVAL);
						if (emitOverflowSummary)
						{
							log.info("[Leagues] locked-region parse-miss: at-cap dedupe set full (max={}); dropped novel keys — extend parseRegionName or raise cap",
									LEAGUES_PARSE_MISS_AT_CAP_SEEN_MAX);
						}
						if (log.isDebugEnabled() && emitOverflowSummary)
						{
							log.debug("[Leagues] locked-region parse-miss dropped (at-cap dedupe set full); missKey prefix='{}'",
									missKey.length() > 80 ? missKey.substring(0, 80) + "…" : missKey);
						}
						return true;
					}
					if (!LEAGUES_PARSE_MISS_AT_CAP_SEEN.add(missKey))
					{
						return true;
					}
					Rs2Walker.Telemetry.incrementLeaguesLockParseMiss();
					int prev = LEAGUES_PARSE_MISS_AT_CAP_SEEN_COUNT.get();
					if (prev < LEAGUES_PARSE_MISS_AT_CAP_SEEN_MAX)
					{
						LEAGUES_PARSE_MISS_AT_CAP_SEEN_COUNT.set(prev + 1);
					}
				}
				int n = LEAGUES_PARSE_MISS_AFTER_CAP_COUNT.incrementAndGet();
				if (n == 1 || n % LEAGUES_PARSE_MISS_AFTER_CAP_LOG_INTERVAL == 0)
				{
					log.info("[Leagues] locked-region parse-miss skipped={} (distinct-sample cap {}); extend parseRegionName",
							n, LEAGUES_PARSE_MISS_DISTINCT_LOG_CAP);
				}
				return true;
			}

			if (emitSampleLog && Rs2LogRateLimit.everyN(LEAGUES_PARSE_MISS_INFO, LEAGUES_PARSE_MISS_INFO_INTERVAL))
			{
				String sample = regionNameRaw == null ? ""
						: regionNameRaw.length() > 120 ? regionNameRaw.substring(0, 120) + "…" : regionNameRaw;
				log.info("[Leagues] locked-region chat did not map to LeaguesRegion; dest-only blacklist applied. sample='{}'", sample);
			}
			return true;
		}
		if (Rs2LogRateLimit.everyN(LEAGUES_BLOCKED_DEST_FROM_CHAT_INFO, LEAGUES_BLOCKED_DEST_FROM_CHAT_INFO_INTERVAL))
		{
			log.info("[Leagues] blocked transport destPacked={} region='{}' method='{}'",
					packedDest, regionNameRaw, method != null ? method : "");
		}
		LeaguesTransportPersistence.persistBlacklistDestination(packedDest, region, method);
		LeaguesTransportAttempts.clearLastTransportAttempt();
		Rs2Walker.Telemetry.incrementLeaguesLockAttributed();
		return true;
	}

	static boolean shouldRecalculatePathAfterLock(String region, Integer packedDest)
	{
		if (region == null || packedDest == null)
		{
			return true;
		}
		synchronized (LEAGUES_REROUTE_LOCK)
		{
			long now = System.currentTimeMillis();
			String prevRegion = lastLeaguesRerouteRegion;
			int prevPacked = lastLeaguesReroutePackedDest;
			long prevMs = lastLeaguesRerouteMs;
			if (packedDest == prevPacked && region.equals(prevRegion) && (now - prevMs) <= LEAGUES_REROUTE_DEDUPE_WINDOW_MS)
			{
				if (log.isDebugEnabled())
				{
					log.debug("[Leagues] reroute deduped: region='{}' destPacked={} ageMs={}", region, packedDest, (now - prevMs));
				}
				return false;
			}
			lastLeaguesRerouteRegion = region;
			lastLeaguesReroutePackedDest = packedDest;
			lastLeaguesRerouteMs = now;
			return true;
		}
	}
}
