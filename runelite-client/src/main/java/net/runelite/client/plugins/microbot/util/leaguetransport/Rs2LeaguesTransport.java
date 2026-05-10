package net.runelite.client.plugins.microbot.util.leaguetransport;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import net.runelite.client.plugins.microbot.shortestpath.PrimitiveIntHashMap;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.text.Rs2TextSanitizer;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;

/**
 * Leagues transport façade. Implementation split across {@code LeaguesTransport*} types (audit T6.2).
 *
 * <p><b>Threading:</b> Do not call {@link #leaguesTeleport} (or {@link #tryHandleLeaguesAreaTransport}) from the RuneLite
 * client thread — they synchronize with client-thread UI and would stall the client.
 */
@Slf4j
public final class Rs2LeaguesTransport
{
	/** Defensive bound before sanitize+regex on locked-region chat messages. */
	public static final int LEAGUES_LOCK_CHAT_MAX_NORMALIZE_CHARS = 4096;
	public static final int LEAGUES_LOCK_CHAT_TRUNC_WARN_INTERVAL = 200;

	/**
	 * Max age of last transport attempt for correlating locked-region gamemessages.
	 *
	 * @apiNote Stable for scripts; document in changelog when changing semantics.
	 */
	public static final long LEAGUES_LOCK_CHAT_MAX_ATTEMPT_AGE_MS = 15_000L;

	public static final class LeaguesContext
	{
		private final boolean active;
		private final EnumSet<LeaguesRegion> unlockedRegions;

		private LeaguesContext(boolean active, EnumSet<LeaguesRegion> unlockedRegions)
		{
			this.active = active;
			this.unlockedRegions = unlockedRegions != null ? unlockedRegions : EnumSet.noneOf(LeaguesRegion.class);
		}

		public boolean isActive()
		{
			return active;
		}

		public EnumSet<LeaguesRegion> getUnlockedRegions()
		{
			return unlockedRegions;
		}
	}

	private static final LeaguesContext INACTIVE_CONTEXT =
			new LeaguesContext(false, EnumSet.noneOf(LeaguesRegion.class));

	private Rs2LeaguesTransport()
	{
	}

	public static Integer getLastTransportAttemptPackedDest()
	{
		return LeaguesTransportAttempts.getLastTransportAttemptPackedDest();
	}

	public static String getLastTransportAttemptMethod()
	{
		return LeaguesTransportAttempts.getLastTransportAttemptMethod();
	}

	public static boolean isLeaguesAreaTeleportPending(long maxAgeMs)
	{
		return LeaguesTransportAttempts.isLeaguesAreaTeleportPending(maxAgeMs);
	}

	public static LeaguesTransportAttemptSnapshot getLastTransportAttemptSnapshot()
	{
		return LeaguesTransportAttempts.getLastTransportAttemptSnapshot();
	}

	public static void clearLastTransportAttempt()
	{
		LeaguesTransportAttempts.clearLastTransportAttempt();
	}

	/**
	 * Latest transport attempt if younger than {@code maxAgeMs}. Matches {@code 1a5c485}: no geographic or label filtering;
	 * {@code regionCaptured} is ignored at lookup time (region comes from chat when persisting the blacklist row).
	 */
	public static Optional<LeaguesTransportAttemptSnapshot> findTransportAttemptForLockedRegionChat(
			String regionCaptured, long nowMs, long maxAgeMs)
	{
		return LeaguesTransportAttempts.findTransportAttemptForLockedRegionChat(regionCaptured, nowMs, maxAgeMs);
	}

	public static void recordTransportAttempt(Transport transport)
	{
		LeaguesTransportAttempts.recordTransportAttempt(transport, null);
	}

	public static void recordTransportAttempt(Transport transport, String attemptHandler)
	{
		LeaguesTransportAttempts.recordTransportAttempt(transport, attemptHandler);
	}

	public static boolean tryHandleLeaguesAreaTransport(Transport transport)
	{
		return tryHandleLeaguesAreaTransportResult(transport).map(LeaguesTeleportResult::isSuccess).orElse(false);
	}

	public static boolean matchesLeaguesAreaTransportPrefix(Transport transport)
	{
		if (transport == null || transport.getDisplayInfo() == null || !isLeaguesActive())
		{
			return false;
		}
		String displayInfoForPrefix = Rs2TextSanitizer.normalizeAsciiColons(transport.getDisplayInfo());
		Matcher areaPrefix = LeaguesTransportRegions.LEAGUES_AREA_PREFIX.matcher(displayInfoForPrefix);
		return areaPrefix.lookingAt();
	}

	public static Optional<LeaguesTeleportResult> tryHandleLeaguesAreaTransportResult(Transport transport)
	{
		if (transport == null || transport.getDisplayInfo() == null)
		{
			return Optional.empty();
		}
		if (!isLeaguesActive())
		{
			return Optional.empty();
		}

		String displayInfo = transport.getDisplayInfo();
		String displayInfoForPrefix = Rs2TextSanitizer.normalizeAsciiColons(displayInfo);
		Matcher areaPrefix = LeaguesTransportRegions.LEAGUES_AREA_PREFIX.matcher(displayInfoForPrefix);
		if (!areaPrefix.lookingAt())
		{
			return Optional.empty();
		}

		String regionRaw = areaPrefix.replaceFirst("").trim();
		String sanitizedRegion = Rs2TextSanitizer.sanitizeLeaguesLockedRegionName(regionRaw);
		LeaguesRegion region = LeaguesTransportRegions.parseRegionName(sanitizedRegion);
		if (region == null)
		{
			if (log.isDebugEnabled())
			{
				log.debug("Leagues Area transport: parseRegionName miss after sanitize; rawLabel='{}' sanitized='{}'",
						regionRaw, sanitizedRegion);
			}
			return Optional.empty();
		}

		recordTransportAttempt(transport, "LeaguesArea");
		LeaguesTeleportResult res = LeaguesTransportTeleport.leaguesTeleport(region);
		if (!res.isSuccess() && log.isDebugEnabled())
		{
			log.debug("Leagues Area transport failed: region={} reason={} message={}",
					region, res.getFailureReason(), res.getMessage());
		}
		if (res.isSuccess())
		{
			WorldPoint after = Rs2Player.getWorldLocation();
			if (after != null)
			{
				LeaguesTransportPersistence.persistRegionLanding(region, after);
			}
			clearLastTransportAttempt();
		}
		return Optional.of(res);
	}

	public static void onLockedRegionGameMessage(String msg)
	{
		LeaguesTransportChat.onLockedRegionGameMessage(msg);
	}

	public static LeaguesContext leaguesContext()
	{
		if (!isLeaguesActive())
		{
			return INACTIVE_CONTEXT;
		}
		return new LeaguesContext(true, LeaguesTransportTeleport.unlockedRegions());
	}

	public static boolean isTransportAllowed(LeaguesContext ctx, Transport transport)
	{
		return LeaguesTransportInjection.isTransportAllowed(ctx, transport);
	}

	public static void invalidateContext()
	{
		PathfinderConfig cfg = ShortestPathPlugin.pathfinderConfig;
		if (cfg != null)
		{
			cfg.invalidateTransportRefreshCache();
		}
	}

	public static boolean isDestinationBlacklisted(int packedWorldPoint)
	{
		return LeaguesTransportPersistence.isDestinationBlacklisted(packedWorldPoint);
	}

	public static void invalidateBlacklistFor(LeaguesRegion region)
	{
		LeaguesTransportPersistence.invalidateBlacklistFor(region);
	}

	public static Map<Integer, LeaguesRegion> getBlacklistedDestinationRegionsSnapshot()
	{
		return LeaguesTransportPersistence.getBlacklistedDestinationRegionsSnapshot();
	}

	public static void persistBlacklistDestination(int packedWorldPoint, LeaguesRegion region, String method)
	{
		LeaguesTransportPersistence.persistBlacklistDestination(packedWorldPoint, region, method);
	}

	public static void appendTransportAttemptObservation(Transport transport, String detail)
	{
		LeaguesTransportObservations.appendTransportObservationInternal("attempt", transport, null, detail);
	}

	public static void appendTransportObservation(String phase, Transport transport, boolean success, String detail)
	{
		if (!"result".equals(phase))
		{
			throw new IllegalArgumentException("appendTransportObservation(boolean): phase must be \"result\"");
		}
		LeaguesTransportObservations.appendTransportObservationInternal(phase, transport, Boolean.valueOf(success), detail);
	}

	public static void appendCatalogTransport(LeaguesRegion requiredRegion, Transport transport, String note)
	{
		LeaguesTransportObservations.appendCatalogTransport(requiredRegion, transport, note);
	}

	public static java.util.List<Transport> loadCatalogTransports(EnumSet<LeaguesRegion> unlockedRegions)
	{
		return LeaguesTransportObservations.loadCatalogTransports(unlockedRegions);
	}

	public static void injectLeaguesTransports(
			PathfinderConfig pathfinderConfig,
			LeaguesContext ctx,
			Set<Transport> usableTeleports,
			Map<WorldPoint, Set<Transport>> transports,
			PrimitiveIntHashMap<Set<Transport>> transportsPacked,
			Map<TransportType, int[]> typeStats)
	{
		LeaguesTransportInjection.injectLeaguesTransports(
				pathfinderConfig, ctx, usableTeleports, transports, transportsPacked, typeStats);
	}

	public static LeaguesRegion parseRegionName(String regionNameRaw)
	{
		return LeaguesTransportRegions.parseRegionName(regionNameRaw);
	}

	public static Optional<String> captureLockedRegionFromChatRaw(String rawForMatch)
	{
		return LeaguesTransportRegions.captureLockedRegionFromChatRaw(rawForMatch);
	}

	public static Optional<String> captureLockedRegionFromSanitizedLower(String sanitizedLower)
	{
		return LeaguesTransportRegions.captureLockedRegionFromSanitizedLower(sanitizedLower);
	}

	public static boolean recordBlockedDestinationFromChat(String regionNameRaw, Integer packedDest, String method)
	{
		return LeaguesTransportRegions.recordBlockedDestinationFromChat(regionNameRaw, packedDest, method);
	}

	public static boolean shouldRecalculatePathAfterLock(String region, Integer packedDest)
	{
		return LeaguesTransportRegions.shouldRecalculatePathAfterLock(region, packedDest);
	}

	public static Optional<WorldPoint> getCachedRegionLanding(LeaguesRegion region)
	{
		return LeaguesTransportPersistence.getCachedRegionLanding(region);
	}

	public static void persistRegionLanding(LeaguesRegion region, WorldPoint landing)
	{
		LeaguesTransportPersistence.persistRegionLanding(region, landing);
	}

	public static void calibrateMissingLandingsAsync(EnumSet<LeaguesRegion> unlockedRegions)
	{
		calibrateMissingLandingsAsync(unlockedRegions, false);
	}

	/**
	 * @param logNoOpWhenFullyCalibrated when {@code true}, logs one INFO line if every unlocked region
	 *                                   already has a persisted landing (e.g. varbit-driven refresh).
	 */
	public static void calibrateMissingLandingsAsync(EnumSet<LeaguesRegion> unlockedRegions,
			boolean logNoOpWhenFullyCalibrated)
	{
		LeaguesTransportTeleport.calibrateMissingLandingsAsync(unlockedRegions, logNoOpWhenFullyCalibrated);
	}

	public static boolean isTeleportInProgress()
	{
		return LeaguesTransportTeleport.isTeleportInProgress();
	}

	public static void tickLeaguesCalibration()
	{
		LeaguesTransportTeleport.tickLeaguesCalibration();
	}

	public static void onLogout()
	{
		LeaguesTransportTeleport.onLogout();
	}

	public static boolean isLeaguesContext()
	{
		return LeaguesTransportTeleport.verifyLeaguesContextOrNull() == null;
	}

	public static boolean isLeaguesActive()
	{
		return net.runelite.client.plugins.microbot.Microbot.getVarbitValue(
				net.runelite.api.gameval.VarbitID.LEAGUE_TYPE) > 0;
	}

	public static EnumSet<LeaguesRegion> unlockedRegions()
	{
		return LeaguesTransportTeleport.unlockedRegions();
	}

	public static LeaguesTeleportResult leaguesTeleport(LeaguesRegion region)
	{
		return LeaguesTransportTeleport.leaguesTeleport(region);
	}

	public static LeaguesTeleportResult leaguesTeleport(LeaguesRegion region, int timeoutMs)
	{
		return LeaguesTransportTeleport.leaguesTeleport(region, timeoutMs);
	}

	/**
	 * Non-blocking driver for advanced callers. Call {@link #tick()} from script loop until inactive.
	 */
	public static final class LeaguesTeleportDriver extends LeaguesTransportTeleport.LeaguesTeleportDriver
	{
		public LeaguesTeleportDriver(LeaguesRegion targetRegion)
		{
			super(targetRegion);
		}
	}
}
