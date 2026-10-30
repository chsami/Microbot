package net.runelite.client.plugins.microbot.shortestpath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathEdge;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathJitter;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.QuestWalkRule;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.SplitFlagMap;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

/** Path variation must never wander far or add transports the base route did not use. */
public class PathVariationRegressionTest
{
	private static SplitFlagMap collisionMap;
	private static HashMap<WorldPoint, Set<Transport>> allTransports;

	@BeforeClass
	public static void load()
	{
		collisionMap = SplitFlagMap.fromResources();
		allTransports = Transport.loadAllFromResources();
	}

	@After
	public void off()
	{
		PathJitter.configure(false, 1);
		QuestWalkRule.setActive(false);
	}

	private PathfinderConfig config() throws Exception
	{
		PathfinderConfig config = new PathfinderConfig(collisionMap, allTransports, Collections.emptyList(), null, null);
		Field f = PathfinderConfig.class.getDeclaredField("calculationCutoffMillis");
		f.setAccessible(true);
		f.setLong(config, 10_000);
		for (Map.Entry<WorldPoint, Set<Transport>> entry : allTransports.entrySet())
		{
			if (entry.getKey() != null)
			{
				config.getTransports().put(entry.getKey(), entry.getValue());
				config.getTransportsPacked().put(WorldPointUtil.packWorldPoint(entry.getKey()), entry.getValue());
			}
		}
		return config;
	}

	@Test
	public void geToRimmingtonStaysInMisthalinAndAsgarniaWithBaseTransports() throws Exception
	{
		WorldPoint ge = new WorldPoint(3164, 3485, 0);
		WorldPoint rimmington = new WorldPoint(2968, 3205, 0);

		PathJitter.configure(false, 1);
		Pathfinder base = new Pathfinder(config(), ge, rimmington);
		base.run();
		Set<TransportType> baseTypes = new HashSet<>();
		for (PathEdge e : base.getPathEdges())
		{
			if (e.isTransport())
			{
				baseTypes.add(e.getTransport().getType());
			}
		}

		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = 0, maxY = 0;
		for (WorldPoint p : base.getPath())
		{
			minX = Math.min(minX, p.getX());
			maxX = Math.max(maxX, p.getX());
			minY = Math.min(minY, p.getY());
			maxY = Math.max(maxY, p.getY());
		}
		PathJitter.configure(true, 3);
		for (int i = 0; i < 12; i++)
		{
			Pathfinder varied = new Pathfinder(config(), ge, rimmington);
			varied.run();
			List<WorldPoint> path = varied.getPath();
			assertFalse(path.isEmpty());
			for (WorldPoint p : path)
			{
				assertTrue("plane " + p, p.getPlane() <= 1);
				assertTrue("left the region at " + p, p.getX() >= minX - 150 && p.getX() <= maxX + 150
					&& p.getY() >= minY - 150 && p.getY() <= maxY + 150);
			}
			for (PathEdge e : varied.getPathEdges())
			{
				if (e.isTransport())
				{
					assertTrue("added " + e.getTransport().getType(), baseTypes.contains(e.getTransport().getType()));
				}
			}
		}
	}

	/**
	 * The transports a route uses, as a sorted list of their types. Equal-cost twins (the two Varrock east gates, the
	 * two ends of a double door) differ in tile, so the comparison is by type and count: an added or dropped
	 * staircase, door, shortcut, boat or teleport still changes the list.
	 */
	private static List<String> transportList(Pathfinder pathfinder)
	{
		List<String> keys = new java.util.ArrayList<>();
		for (PathEdge e : pathfinder.getPathEdges())
		{
			if (e.isTransport())
			{
				keys.add(e.getTransport().getType().name());
			}
		}
		Collections.sort(keys);
		return keys;
	}

	private static Pathfinder variedRun(PathfinderConfig config, WorldPoint from, WorldPoint to)
	{
		int target = WorldPointUtil.packWorldPoint(to);
		// a fresh seed (and detour) for each run, as a new walk would get
		PathJitter.reroll(target, System.nanoTime() / 1_000, WorldPointUtil.packWorldPoint(from), target);
		Pathfinder pathfinder = new Pathfinder(config, from, to);
		pathfinder.run();
		return pathfinder;
	}

	private void assertVariationKeepsTheTransportsAndTheCost(WorldPoint from, WorldPoint to, int seeds) throws Exception
	{
		// the quest-walking rule keeps the base route on foot, where it is stable; ties between equal routes are still
		// broken at random, so the base is sampled a few times and a varied route must match one of them
		QuestWalkRule.setActive(true);
		PathJitter.configure(false, 1);
		Pathfinder base = new Pathfinder(config(), from, to);
		base.run();
		assertFalse(base.getPath().isEmpty());
		Set<List<String>> baseTransportLists = new HashSet<>();
		baseTransportLists.add(transportList(base));
		for (int i = 0; i < 5; i++)
		{
			Pathfinder again = new Pathfinder(config(), from, to);
			again.run();
			baseTransportLists.add(transportList(again));
		}
		int baseLength = PathJitter.routeLength(base.getPath());

		PathJitter.configure(true, 3);
		boolean anyDifferent = false;
		for (int i = 0; i < seeds; i++)
		{
			Pathfinder varied = variedRun(config(), from, to);
			assertTrue("transports changed on seed " + i + ": " + transportList(varied) + " not in " + baseTransportLists,
				baseTransportLists.contains(transportList(varied)));
			int variedLength = PathJitter.routeLength(varied.getPath());
			assertTrue("length " + variedLength + " vs base " + baseLength,
				variedLength <= Math.ceil(baseLength * 1.1) + 2);
			assertTrue("restricted transport in a varied quest route",
				varied.getPathEdges().stream().noneMatch(e -> e.isTransport() && QuestWalkRule.isRestricted(e.getTransport())));
			anyDifferent |= !varied.getPath().equals(base.getPath());
		}
		assertTrue("variation never produced a different route in " + seeds + " seeds", anyDifferent);
	}

	@Test
	public void manySeedsKeepGeToRimmingtonOnLandWithTheSameTransports() throws Exception
	{
		assertVariationKeepsTheTransportsAndTheCost(new WorldPoint(3164, 3485, 0), new WorldPoint(2968, 3205, 0), 40);
	}

	@Test
	public void manySeedsKeepVarrockToArdougneOffTheFaladorCastle() throws Exception
	{
		WorldPoint from = new WorldPoint(3283, 3382, 0);
		WorldPoint to = new WorldPoint(2592, 3336, 0);
		assertVariationKeepsTheTransportsAndTheCost(from, to, 25);
		PathJitter.configure(true, 3);
		for (int i = 0; i < 10; i++)
		{
			for (PathEdge e : variedRun(config(), from, to).getPathEdges())
			{
				if (e.isTransport())
				{
					int id = e.getTransport().getObjectId();
					assertFalse("castle object " + id, id >= 24070 && id <= 24075);
				}
			}
		}
	}

	@Test
	public void shortRoutesAreNeverVaried() throws Exception
	{
		WorldPoint from = new WorldPoint(3222, 3218, 0);
		WorldPoint to = new WorldPoint(3228, 3221, 0);
		PathJitter.configure(false, 1);
		Pathfinder base = new Pathfinder(config(), from, to);
		base.run();
		assertTrue(base.getPath().size() < PathJitter.MIN_VARIED_ROUTE_TILES);
		int baseLength = PathJitter.routeLength(base.getPath());
		PathJitter.configure(true, 3);
		for (int i = 0; i < 20; i++)
		{
			// equal-length routes are still picked at random; a short route must stay a shortest one
			assertEquals(baseLength, PathJitter.routeLength(variedRun(config(), from, to).getPath()));
		}
	}

	@Test
	public void undergroundAndShortRoutesAreNotEligible()
	{
		List<WorldPoint> longRoute = new java.util.ArrayList<>();
		for (int i = 0; i < 40; i++)
		{
			longRoute.add(new WorldPoint(3200 + i, 3200, 0));
		}
		assertTrue(PathJitter.mayVary(longRoute));
		assertFalse(PathJitter.mayVary(longRoute.subList(0, 14)));
		assertTrue(PathJitter.mayVary(longRoute.subList(0, 15)));
		List<WorldPoint> dungeon = new java.util.ArrayList<>();
		for (int i = 0; i < 40; i++)
		{
			dungeon.add(new WorldPoint(3200 + i, 9900, 0));
		}
		assertFalse(PathJitter.mayVary(dungeon));
		assertFalse(PathJitter.mayVary(null));
	}

	@Test
	public void policyRejectsAnyTransportChangeAndCostlyRoutes()
	{
		assertFalse(PathJitter.acceptVaried(100, 100, true));
		assertTrue(PathJitter.acceptVaried(100, 100, false));
		assertTrue(PathJitter.acceptVaried(110, 100, false));
		assertFalse(PathJitter.acceptVaried(111, 100, false));
	}
}
