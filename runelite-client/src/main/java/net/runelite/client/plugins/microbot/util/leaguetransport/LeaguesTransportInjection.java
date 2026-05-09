package net.runelite.client.plugins.microbot.util.leaguetransport;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import net.runelite.client.plugins.microbot.shortestpath.PrimitiveIntHashMap;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Pathfinder injection for Leagues Area and catalog transports.
 */
@Slf4j
final class LeaguesTransportInjection
{
	private LeaguesTransportInjection()
	{
	}

	private static volatile EnumSet<LeaguesRegion> lastInjectedUnlockedForBlacklistPrune = null;

	static void injectLeaguesTransports(
			PathfinderConfig pathfinderConfig,
			Rs2LeaguesTransport.LeaguesContext ctx,
			Set<Transport> usableTeleports,
			Map<WorldPoint, Set<Transport>> transports,
			PrimitiveIntHashMap<Set<Transport>> transportsPacked,
			Map<TransportType, int[]> typeStats)
	{
		if (pathfinderConfig == null || ctx == null || !ctx.isActive() || ctx.getUnlockedRegions().isEmpty()
				|| usableTeleports == null || transports == null || transportsPacked == null || typeStats == null)
		{
			return;
		}

		EnumSet<LeaguesRegion> unlockedNow = EnumSet.copyOf(ctx.getUnlockedRegions());
		EnumSet<LeaguesRegion> prevUnlocked = lastInjectedUnlockedForBlacklistPrune;
		if (prevUnlocked != null)
		{
			for (LeaguesRegion r : unlockedNow)
			{
				if (!prevUnlocked.contains(r))
				{
					LeaguesTransportPersistence.invalidateBlacklistFor(r);
				}
			}
		}
		lastInjectedUnlockedForBlacklistPrune = unlockedNow;

		injectLeaguesAreaTeleports(pathfinderConfig, ctx, ctx.getUnlockedRegions(), usableTeleports, typeStats);
		injectLeaguesCatalogTransports(pathfinderConfig, ctx, ctx.getUnlockedRegions(), usableTeleports, transports, transportsPacked, typeStats);
	}

	private static boolean mergeOriginlessTeleportByBestDuration(Set<Transport> usableTeleports, Transport candidate)
	{
		if (candidate == null || candidate.getOrigin() != null || candidate.getDestination() == null)
		{
			return false;
		}
		int p = WorldPointUtil.packWorldPoint(candidate.getDestination());
		int minDur = candidate.getDuration();
		for (Transport o : usableTeleports)
		{
			if (o == null || o.getOrigin() != null || o.getDestination() == null)
			{
				continue;
			}
			if (WorldPointUtil.packWorldPoint(o.getDestination()) == p)
			{
				minDur = Math.min(minDur, o.getDuration());
			}
		}
		if (candidate.getDuration() > minDur)
		{
			return false;
		}
		usableTeleports.removeIf(o -> o != null && o.getOrigin() == null && o.getDestination() != null
				&& WorldPointUtil.packWorldPoint(o.getDestination()) == p);
		return usableTeleports.add(candidate);
	}

	private static void injectLeaguesAreaTeleports(
			PathfinderConfig pathfinderConfig,
			Rs2LeaguesTransport.LeaguesContext ctx,
			EnumSet<LeaguesRegion> unlockedLeaguesRegions,
			Set<Transport> usableTeleports,
			Map<TransportType, int[]> typeStats)
	{
		int before = usableTeleports.size();
		int added = 0;

		for (LeaguesRegion region : unlockedLeaguesRegions)
		{
			Optional<WorldPoint> landingOpt = LeaguesTransportPersistence.getCachedRegionLanding(region);
			if (!landingOpt.isPresent())
			{
				continue;
			}

			WorldPoint landing = landingOpt.get();
			Transport t = new Transport(
					landing,
					"Leagues Area: " + region.getDisplayName(),
					TransportType.SEASONAL_TRANSPORT,
					true,
					31,
					java.util.Collections.emptySet());
			if (!pathfinderConfig.isTransportUsableWithLeaguesContext(t, ctx))
			{
				continue;
			}
			if (mergeOriginlessTeleportByBestDuration(usableTeleports, t))
			{
				added++;
			}
		}

		if (added > 0)
		{
			int[] stats = typeStats.computeIfAbsent(TransportType.SEASONAL_TRANSPORT, k -> new int[]{0, 0, 0});
			stats[0] += added;
			stats[1] += added;
			log.info("[Leagues] injected {} Leagues Area teleports (originless {} -> {})",
					added, before, usableTeleports.size());
		}
	}

	private static void injectLeaguesCatalogTransports(
			PathfinderConfig pathfinderConfig,
			Rs2LeaguesTransport.LeaguesContext ctx,
			EnumSet<LeaguesRegion> unlockedLeaguesRegions,
			Set<Transport> usableTeleports,
			Map<WorldPoint, Set<Transport>> transports,
			PrimitiveIntHashMap<Set<Transport>> transportsPacked,
			Map<TransportType, int[]> typeStats)
	{
		int beforeOriginless = usableTeleports.size();
		int addedOriginless = 0;
		int addedOriginBased = 0;

		java.util.List<Transport> catalog = LeaguesTransportObservations.loadCatalogTransports(unlockedLeaguesRegions);
		for (Transport t : catalog)
		{
			if (t == null || t.getDestination() == null)
			{
				continue;
			}

			if (!pathfinderConfig.isTransportUsableWithLeaguesContext(t, ctx))
			{
				continue;
			}

			TransportType tt = t.getType();
			if (t.getOrigin() == null)
			{
				if (mergeOriginlessTeleportByBestDuration(usableTeleports, t))
				{
					addedOriginless++;
					if (tt != null)
					{
						int[] stats = typeStats.computeIfAbsent(tt, k -> new int[]{0, 0, 0});
						stats[0] += 1;
						stats[1] += 1;
					}
				}
			}
			else
			{
				transports.computeIfAbsent(t.getOrigin(), k -> new HashSet<>()).add(t);

				int packedOrigin = WorldPointUtil.packWorldPoint(t.getOrigin());
				Set<Transport> packedSet = transportsPacked.get(packedOrigin);
				if (packedSet == null)
				{
					packedSet = new HashSet<>();
					transportsPacked.put(packedOrigin, packedSet);
				}
				packedSet.add(t);
				addedOriginBased++;
				if (tt != null)
				{
					int[] stats = typeStats.computeIfAbsent(tt, k -> new int[]{0, 0, 0});
					stats[0] += 1;
					stats[1] += 1;
				}
			}
		}

		if (addedOriginless + addedOriginBased > 0)
		{
			log.info("[Leagues] injected {} catalog transports (originlessAdded={} originBasedAdded={} originless {} -> {})",
					addedOriginless + addedOriginBased,
					addedOriginless,
					addedOriginBased,
					beforeOriginless,
					usableTeleports.size());
		}
	}

	static boolean isTransportAllowed(Rs2LeaguesTransport.LeaguesContext ctx, Transport transport)
	{
		if (transport == null)
		{
			return false;
		}
		if (ctx == null || !ctx.isActive())
		{
			return true;
		}
		WorldPoint dest = transport.getDestination();
		if (dest == null)
		{
			return transport.getType() != TransportType.SEASONAL_TRANSPORT;
		}
		int packed = WorldPointUtil.packWorldPoint(dest);
		if (LeaguesTransportPersistence.isDestinationBlacklisted(packed))
		{
			return false;
		}
		LeaguesRegion learned = LeaguesTransportPersistence.getBlacklistedDestinationRegionsSnapshot().get(packed);
		return learned == null || ctx.getUnlockedRegions().contains(learned);
	}
}
