package shortestpath.leagues;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;
import shortestpath.ShortestPathPlugin;
import shortestpath.Util;
import shortestpath.WorldPointUtil;

/**
 * Resolves a packed world point to its {@link LeagueRegion} for the Demonic
 * Pacts League.
 *
 * <p>
 * Mirrors the static-utility shape of
 * {@link shortestpath.pathfinder.WildernessChecker}. The mapping is keyed by
 * OSRS map region id (a 64x64-tile chunk identifier matching
 * {@code WorldPoint.getRegionID()}: {@code (x >> 6) << 8 | (y >> 6)}) and
 * loaded from {@code /leagues/regions.tsv} on first access.
 * </p>
 *
 * <p>
 * Tiles whose region id is absent from the mapping fall back to
 * {@link LeagueRegion#NEUTRAL}. This is deliberate: it covers instances and
 * dynamic regions (POH, raids, dungeon entrances) which are universally
 * accessible during the league regardless of unlocked areas.
 * </p>
 */
@Slf4j
public class LeagueRegionChecker
{
	private static final String RESOURCE_PATH = "/leagues/regions.tsv";

	private static volatile Map<Integer, LeagueRegion> regionsById;

	private LeagueRegionChecker()
	{
	}

	/**
	 * Returns the league region containing the supplied packed world point.
	 * Never returns {@code null}; unmapped tiles resolve to
	 * {@link LeagueRegion#NEUTRAL}.
	 */
	public static LeagueRegion getRegion(int packedPoint)
	{
		final int x = WorldPointUtil.unpackWorldX(packedPoint);
		final int y = WorldPointUtil.unpackWorldY(packedPoint);
		final int regionId = ((x >> 6) << 8) | (y >> 6);
		return regions().getOrDefault(regionId, LeagueRegion.NEUTRAL);
	}

	/**
	 * Convenience predicate for the always-blocked region.
	 */
	public static boolean isInMisthalin(int packedPoint)
	{
		return getRegion(packedPoint) == LeagueRegion.MISTHALIN;
	}

	/**
	 * Returns the underlying region-id-to-region map. Lazily initialised on
	 * first call. Visible to tests via {@link #reload(String)}.
	 */
	private static Map<Integer, LeagueRegion> regions()
	{
		Map<Integer, LeagueRegion> snapshot = regionsById;
		if (snapshot == null)
		{
			synchronized (LeagueRegionChecker.class)
			{
				snapshot = regionsById;
				if (snapshot == null)
				{
					snapshot = loadFromResource();
					regionsById = snapshot;
				}
			}
		}
		return snapshot;
	}

	/**
	 * Replaces the in-memory mapping with a parsed copy of the supplied
	 * TSV body. Intended for tests. Pass {@code null} to clear the cache so
	 * the next call re-loads from the resource file.
	 */
	static synchronized void reload(String tsv)
	{
		if (tsv == null)
		{
			regionsById = null;
			return;
		}
		regionsById = parse(tsv);
	}

	private static Map<Integer, LeagueRegion> loadFromResource()
	{
		try (InputStream in = ShortestPathPlugin.class.getResourceAsStream(RESOURCE_PATH))
		{
			if (in == null)
			{
				log.warn("League regions resource not found at {}; defaulting all tiles to NEUTRAL", RESOURCE_PATH);
				return new HashMap<>();
			}
			String body = new String(Util.readAllBytes(Objects.requireNonNull(in)), StandardCharsets.UTF_8);
			return parse(body);
		}
		catch (IOException e)
		{
			log.error("Failed to load league regions from {}", RESOURCE_PATH, e);
			return new HashMap<>();
		}
	}

	private static Map<Integer, LeagueRegion> parse(String tsv)
	{
		Map<Integer, LeagueRegion> result = new HashMap<>();
		if (tsv == null || tsv.isEmpty())
		{
			return result;
		}
		int lineNumber = 0;
		for (String rawLine : tsv.split("\\R"))
		{
			lineNumber++;
			String line = rawLine.trim();
			if (line.isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			String[] parts = line.split("\\s+", 2);
			if (parts.length != 2)
			{
				log.warn("Skipping malformed league regions row {}: '{}'", lineNumber, rawLine);
				continue;
			}
			final int regionId;
			try
			{
				regionId = Integer.parseInt(parts[0]);
			}
			catch (NumberFormatException e)
			{
				log.warn("Skipping league regions row {} with non-numeric region id '{}'", lineNumber, parts[0]);
				continue;
			}
			final LeagueRegion region;
			try
			{
				region = LeagueRegion.valueOf(parts[1]);
			}
			catch (IllegalArgumentException e)
			{
				log.warn("Skipping league regions row {} with unknown region '{}'", lineNumber, parts[1]);
				continue;
			}
			result.put(regionId, region);
		}
		return result;
	}
}
