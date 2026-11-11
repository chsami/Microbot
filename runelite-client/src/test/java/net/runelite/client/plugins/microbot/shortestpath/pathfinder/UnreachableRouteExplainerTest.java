package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class UnreachableRouteExplainerTest
{
	private static final WorldPoint FALADOR = new WorldPoint(2968, 3378, 0);
	private static final WorldPoint KARAMJA = new WorldPoint(2933, 3148, 0);

	private static SplitFlagMap map;
	private static HashMap<WorldPoint, Set<Transport>> catalog;

	@BeforeClass
	public static void loadFixtures()
	{
		map = SplitFlagMap.fromResources();
		catalog = Transport.loadAllFromResources();
	}

	@Test
	public void islandWithoutUsableBoatIsProvenUnreachableAndNamesTheBoat() throws Exception
	{
		PathfinderConfig live = configWithUsableTransports(UnreachableRouteExplainerTest::freeToPlayPassage);

		Pathfinder pathfinder = new Pathfinder(live, FALADOR, KARAMJA);
		pathfinder.run();
		List<WorldPoint> path = pathfinder.getPath();
		WorldPoint closest = path.get(path.size() - 1);

		assertEquals(PathTerminationReason.SEARCH_EXHAUSTED, pathfinder.getTerminationReason());
		assertTrue(closest.distanceTo(KARAMJA) > 10);

		String message = UnreachableRouteExplainer.explain(live, FALADOR, KARAMJA, closest, 60_000L);

		assertEquals("Can't reach 2933, 3148: the route needs the ship to Musa Point (Captain Tobias), "
			+ "but it is not available to you right now.", message);
	}

	@Test
	public void destinationWithNoRouteEvenIgnoringRequirementsSaysSo() throws Exception
	{
		PathfinderConfig live = configWithUsableTransports(transport -> true);
		WorldPoint sealedOff = new WorldPoint(3500, 9000, 0);

		String message = UnreachableRouteExplainer.explain(live, FALADOR, sealedOff, FALADOR);

		assertEquals("Can't reach 3500, 9000: the walker found no route it can use from where you are."
			+ " Check your Web Walker transport settings or start from somewhere else.", message);
	}

	@Test
	public void labelsReadLikeInGameNames()
	{
		Transport ship = catalog.get(new WorldPoint(3029, 3217, 0)).stream()
			.filter(t -> t.getType() == TransportType.SHIP)
			.filter(t -> "Musa Point".equals(t.getDisplayInfo()))
			.findFirst()
			.orElseThrow(AssertionError::new);

		assertEquals("the ship to Musa Point (Captain Tobias)", UnreachableRouteExplainer.label(ship));
	}

	private static boolean freeToPlayPassage(Transport transport)
	{
		return transport.getType() == TransportType.TRANSPORT
			&& !transport.isMembers()
			&& transport.getQuests().isEmpty()
			&& transport.getVarbits().isEmpty()
			&& transport.getVarplayers().isEmpty();
	}

	private interface TransportFilter
	{
		boolean usable(Transport transport);
	}

	private static PathfinderConfig configWithUsableTransports(TransportFilter filter) throws Exception
	{
		PathfinderConfig config = new PathfinderConfig(map, catalog, Collections.emptyList(), null, null);
		Field cutoff = PathfinderConfig.class.getDeclaredField("calculationCutoffMillis");
		cutoff.setAccessible(true);
		cutoff.setLong(config, 60_000L);
		for (Map.Entry<WorldPoint, Set<Transport>> entry : catalog.entrySet())
		{
			if (entry.getKey() == null)
			{
				continue;
			}
			Set<Transport> usable = new HashSet<>();
			for (Transport transport : config.getAllTransports().get(entry.getKey()))
			{
				if (filter.usable(transport))
				{
					usable.add(transport);
				}
			}
			if (!usable.isEmpty())
			{
				config.getTransports().put(entry.getKey(), usable);
				config.getTransportsPacked().put(WorldPointUtil.packWorldPoint(entry.getKey()), usable);
			}
		}
		return config;
	}
}
