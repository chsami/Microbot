package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathConfig;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

public class TransportPlanningPolicyTest
{
	@Test
	public void localCoreRetainsInjectedAdmissionAndZeroRunePolicies() throws Exception
	{
		Transport admitted = transport("Allowed");
		Transport rejected = transport("Rejected");
		Transport home = transport("Home");
		TransportPlanningPolicy policy = new TransportPlanningPolicy()
		{
			@Override
			public boolean isAdmitted(Transport transport)
			{
				return transport != rejected;
			}

			@Override
			public boolean isZeroRuneSpell(Transport transport)
			{
				return transport == home;
			}
		};
		PathfinderConfig config = new PathfinderConfig(
			SplitFlagMap.fromResources(), new HashMap<>(), Collections.emptyList(), null, null, policy);

		Field field = PathfinderConfig.class.getDeclaredField("transportPlanningPolicy");
		field.setAccessible(true);
		TransportPlanningPolicy installed = (TransportPlanningPolicy) field.get(config);

		assertSame(policy, installed);
		assertTrue(installed.isAdmitted(admitted));
		assertFalse(installed.isAdmitted(rejected));
		assertTrue(installed.isZeroRuneSpell(home));
	}

	@Test
	public void nullTransportIsRejectedBeforeFeatureChecks() throws Exception
	{
		PathfinderConfig config = new PathfinderConfig(
			SplitFlagMap.fromResources(), new HashMap<>(), Collections.emptyList(), null, null);
		Method useTransport = PathfinderConfig.class.getDeclaredMethod(
			"useTransport", Transport.class, boolean.class);
		useTransport.setAccessible(true);

		assertFalse((Boolean) useTransport.invoke(config, null, false));
	}

	@Test
	public void offlineRefreshDoesNotReadLiveConfig()
	{
		ShortestPathConfig liveConfig = mock(ShortestPathConfig.class);
		PathfinderConfig config = new PathfinderConfig(
			SplitFlagMap.fromResources(), new HashMap<>(), Collections.emptyList(), null, liveConfig);

		config.refresh();

		verifyNoInteractions(liveConfig);
		assertNotNull(config.getMap());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void constructorFiltersNullTransportElements() throws Exception
	{
		WorldPoint origin = new WorldPoint(3200, 3200, 0);
		Transport valid = transport("Valid");
		Set<Transport> inputSet = new HashSet<>();
		inputSet.add(valid);
		inputSet.add(null);
		Map<WorldPoint, Set<Transport>> input = new HashMap<>();
		input.put(origin, inputSet);
		PathfinderConfig config = new PathfinderConfig(
			SplitFlagMap.fromResources(), input, Collections.emptyList(), null, null);
		Field field = PathfinderConfig.class.getDeclaredField("allTransports");
		field.setAccessible(true);
		Map<WorldPoint, Set<Transport>> stored = (Map<WorldPoint, Set<Transport>>) field.get(config);

		assertEquals(Collections.singleton(valid), stored.get(origin));
	}

	private static Transport transport(String displayInfo)
	{
		return new Transport(
			new WorldPoint(3200, 3200, 0),
			new WorldPoint(3200, 3201, 0),
			displayInfo,
			TransportType.TRANSPORT,
			false,
			"Open",
			"Door",
			1);
	}
}
