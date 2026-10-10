package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathConfig;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportPlanningPolicy;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FarmedSpiritTreeDefaultsTest
{
	private static final List<WorldPoint> QUEST_TREES = Arrays.asList(
		new WorldPoint(2542, 3170, 0),
		new WorldPoint(2461, 3444, 0),
		new WorldPoint(2555, 3259, 0),
		new WorldPoint(3185, 3508, 0));

	@Test
	public void farmedTreeTogglesDefaultOff()
	{
		ShortestPathConfig defaults = new ShortestPathConfig() { };
		assertFalse(defaults.spiritTreeEtceteria());
		assertFalse(defaults.spiritTreeBrimhaven());
		assertFalse(defaults.spiritTreePortSarim());
		assertFalse(defaults.spiritTreeHosidius());
		assertFalse(defaults.spiritTreeFarmingGuild());
		assertTrue(defaults.useSpiritTrees());
	}

	@Test
	public void defaultConfigAdmitsOnlyQuestSpiritTrees() throws Exception
	{
		PathfinderConfig config = new PathfinderConfig(
			null, Collections.emptyMap(), Collections.emptyList(), null, null,
			Rs2TransportPlanningPolicy.INSTANCE);
		ShortestPathConfig defaults = new ShortestPathConfig() { };
		set(config, "useSpiritTreeEtceteria", defaults.spiritTreeEtceteria());
		set(config, "useSpiritTreeBrimhaven", defaults.spiritTreeBrimhaven());
		set(config, "useSpiritTreePortSarim", defaults.spiritTreePortSarim());
		set(config, "useSpiritTreeHosidius", defaults.spiritTreeHosidius());
		set(config, "useSpiritTreeFarmingGuild", defaults.spiritTreeFarmingGuild());

		Field farmedField = PathfinderConfig.class.getDeclaredField("SPIRIT_TREE_DESTINATIONS_ORDERED");
		farmedField.setAccessible(true);
		List<WorldPoint> farmed = Arrays.asList((WorldPoint[]) farmedField.get(null));
		Method enabled = PathfinderConfig.class.getDeclaredMethod("isSpiritTreeRouteEnabled", Transport.class);
		enabled.setAccessible(true);

		List<Transport> spiritTrees = Transport.loadAllFromResources().values().stream()
			.flatMap(Set::stream)
			.filter(t -> t.getType() == TransportType.SPIRIT_TREE)
			.collect(Collectors.toList());
		assertFalse(spiritTrees.isEmpty());

		int farmedRoutes = 0;
		for (Transport t : spiritTrees)
		{
			if (near(t.getOrigin(), farmed) || near(t.getDestination(), farmed))
			{
				farmedRoutes++;
				assertFalse("farmed spirit tree admitted by default: " + t.getOrigin() + " -> " + t.getDestination(),
					(boolean) enabled.invoke(config, t));
			}
		}
		assertTrue(farmedRoutes > 0);

		for (WorldPoint from : QUEST_TREES)
		{
			for (WorldPoint to : QUEST_TREES)
			{
				if (from.equals(to))
				{
					continue;
				}
				boolean admitted = spiritTrees.stream()
					.filter(t -> t.getOrigin() != null && t.getOrigin().distanceTo2D(from) <= 5)
					.filter(t -> to.equals(t.getDestination()))
					.anyMatch(t -> invoke(enabled, config, t));
				assertTrue("quest spirit tree route missing: " + from + " -> " + to, admitted);
			}
		}
	}

	private static boolean near(WorldPoint point, List<WorldPoint> targets)
	{
		return point != null && targets.stream().anyMatch(target -> point.distanceTo2D(target) <= 5);
	}

	private static boolean invoke(Method method, PathfinderConfig config, Transport transport)
	{
		try
		{
			return (boolean) method.invoke(config, transport);
		}
		catch (ReflectiveOperationException e)
		{
			throw new AssertionError(e);
		}
	}

	private static void set(PathfinderConfig config, String name, boolean value) throws Exception
	{
		Field field = PathfinderConfig.class.getDeclaredField(name);
		field.setAccessible(true);
		field.setBoolean(config, value);
	}
}
