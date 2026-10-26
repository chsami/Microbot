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
				if (e.isTransport() && !baseTypes.contains(e.getTransport().getType()))
				{
					assertTrue("added " + e.getTransport().getType(), PathJitter.mayAddTransport(e.getTransport().getType()));
				}
			}
		}
	}

	@Test
	public void policyRejectsForbiddenAndCostlyRoutes()
	{
		assertFalse(PathJitter.mayAddTransport(TransportType.QUETZAL));
		assertFalse(PathJitter.mayAddTransport(TransportType.CHARTER_SHIP));
		assertFalse(PathJitter.mayAddTransport(TransportType.TELEPORTATION_ITEM));
		assertTrue(PathJitter.mayAddTransport(TransportType.TRANSPORT));
		assertFalse(PathJitter.acceptVaried(100, 100, false, true));
		assertTrue(PathJitter.acceptVaried(120, 100, false, false));
		assertFalse(PathJitter.acceptVaried(121, 100, false, false));
		assertTrue(PathJitter.acceptVaried(110, 100, true, false));
		assertFalse(PathJitter.acceptVaried(111, 100, true, false));
	}
}
