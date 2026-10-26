package net.runelite.client.plugins.microbot.shortestpath;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathEdge;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.SplitFlagMap;
import org.junit.BeforeClass;
import org.junit.Test;

/** Land-reachable destinations must be reachable without ships, charters, quetzals or other travel transports. */
public class NoShipWalkRegressionTest
{
	private static SplitFlagMap collisionMap;
	private static HashMap<WorldPoint, Set<Transport>> localTransports;

	private static boolean travel(TransportType t)
	{
		return !(t == TransportType.TRANSPORT || t == TransportType.AGILITY_SHORTCUT || t == TransportType.GRAPPLE_SHORTCUT);
	}

	@BeforeClass
	public static void load()
	{
		collisionMap = SplitFlagMap.fromResources();
		HashMap<WorldPoint, Set<Transport>> all = Transport.loadAllFromResources();
		localTransports = new HashMap<>();
		for (Map.Entry<WorldPoint, Set<Transport>> e : all.entrySet())
		{
			Set<Transport> kept = e.getValue().stream().filter(t -> !travel(t.getType())).collect(Collectors.toSet());
			if (!kept.isEmpty() && e.getKey() != null)
			{
				localTransports.put(e.getKey(), kept);
			}
		}
	}

	private Pathfinder run(WorldPoint from, WorldPoint to) throws Exception
	{
		PathfinderConfig config = new PathfinderConfig(collisionMap, localTransports, Collections.emptyList(), null, null);
		Field f = PathfinderConfig.class.getDeclaredField("calculationCutoffMillis");
		f.setAccessible(true);
		f.setLong(config, 10_000);
		for (Map.Entry<WorldPoint, Set<Transport>> e : localTransports.entrySet())
		{
			config.getTransports().put(e.getKey(), e.getValue());
			config.getTransportsPacked().put(WorldPointUtil.packWorldPoint(e.getKey()), e.getValue());
		}
		Pathfinder pf = new Pathfinder(config, from, to);
		pf.run();
		return pf;
	}

	private void assertOnFoot(WorldPoint from, WorldPoint to) throws Exception
	{
		Pathfinder pf = run(from, to);
		assertFalse(pf.getPath().isEmpty());
		WorldPoint end = pf.getPath().get(pf.getPath().size() - 1);
		assertTrue("stopped at " + end, end.distanceTo2D(to) <= 3);
		for (PathEdge e : pf.getPathEdges())
		{
			assertFalse("travel transport " + (e.isTransport() ? e.getTransport().getType() : ""),
				e.isTransport() && travel(e.getTransport().getType()));
		}
	}

	@Test
	public void geToRimmingtonOnFoot() throws Exception
	{
		assertOnFoot(new WorldPoint(3164, 3485, 0), new WorldPoint(2968, 3205, 0));
	}

	@Test
	public void rimmingtonToFaladorAndDraynorBanksOnFoot() throws Exception
	{
		WorldPoint rimmington = new WorldPoint(2969, 3206, 0);
		assertOnFoot(rimmington, new WorldPoint(2946, 3368, 0)); // Falador west bank
		assertOnFoot(rimmington, new WorldPoint(3092, 3243, 0)); // Draynor bank
	}
}
