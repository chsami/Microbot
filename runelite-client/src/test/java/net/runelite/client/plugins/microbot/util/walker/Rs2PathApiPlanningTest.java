package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.Client;
import net.runelite.api.WorldType;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;
import net.runelite.client.plugins.microbot.shortestpath.PlannerSelectionMode;
import net.runelite.client.plugins.microbot.shortestpath.PrimitiveIntHashMap;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.CollisionMap;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Node;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.SplitFlagMap;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.VisitedTiles;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class Rs2PathApiPlanningTest
{
	/** Covers two sequential canary cutoffs and cold production-catalog initialization in a full suite. */
	private static final long ACTIVE_ROUTE_TEST_TIMEOUT_SECONDS = 30L;
	private static PathfinderConfig config;

	@BeforeClass
	public static void createIsolatedConfig() throws Exception
	{
		config = new PathfinderConfig(
			SplitFlagMap.fromResources(), new HashMap<>(), Collections.emptyList(), null, null);
		setCalculationCutoff(config);
	}

	private static void setCalculationCutoff(PathfinderConfig pathfinderConfig) throws Exception
	{
		Field cutoff = PathfinderConfig.class.getDeclaredField("calculationCutoffMillis");
		cutoff.setAccessible(true);
		cutoff.setLong(pathfinderConfig, 10_000L);
	}

	private static void setPlannerMode(
		PathfinderConfig pathfinderConfig, PlannerSelectionMode mode) throws Exception
	{
		Field field = PathfinderConfig.class.getDeclaredField("plannerSelectionMode");
		field.setAccessible(true);
		field.set(pathfinderConfig, mode);
	}

	private static PathfinderConfig f2pConfig() throws Exception
	{
		Client client = mock(Client.class);
		when(client.getWorldType()).thenReturn(EnumSet.noneOf(WorldType.class));
		PathfinderConfig pathfinderConfig = new PathfinderConfig(
			SplitFlagMap.fromResources(), new HashMap<>(), Collections.emptyList(), client, null);
		setCalculationCutoff(pathfinderConfig);
		return pathfinderConfig;
	}

	private static PathfinderConfig membersConfig() throws Exception
	{
		Client client = mock(Client.class);
		when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS));
		PathfinderConfig pathfinderConfig = new PathfinderConfig(
			SplitFlagMap.fromResources(), new HashMap<>(), Collections.emptyList(), client, null);
		setCalculationCutoff(pathfinderConfig);
		return pathfinderConfig;
	}

	private static void restoreProperty(String key, String value)
	{
		if (value == null)
		{
			System.clearProperty(key);
		}
		else
		{
			System.setProperty(key, value);
		}
	}

	@Test
	public void synchronousPlanReturnsImmutableCompletedRoute()
	{
		WorldPoint start = new WorldPoint(3222, 3218, 0);
		WorldPoint target = new WorldPoint(3232, 3218, 0);
		Rs2RouteRequest request = Rs2RouteRequest.to(start, target)
			.withRefreshPolicy(Rs2RouteRequest.RefreshPolicy.NEVER);

		Rs2RouteResult result = Rs2PathApi.planWithConfig(request, config);

		assertTrue("isolated pathfinder search must terminate", result.isSearchCompleted());
		assertEquals(Rs2RouteTermination.TARGET_REACHED, result.getTerminationReason());
		assertTrue("short Lumbridge walk must reach its exact target", result.isTargetReached(0));
		assertEquals(target, result.getEndpoint().orElse(null));
		assertEquals(result.getPath().size() - 1, result.getSteps().size());
		assertTrue(result.getSteps().stream().noneMatch(Rs2RouteStep::isTransport));
		assertTrue("search timing should be captured", result.getSearchNanos() > 0);
		Rs2RouteMetrics metrics = result.getMetrics();
		assertEquals(result.getSearchNanos(), metrics.getSearchNanos());
		assertTrue("local planner must expose selected path cost", metrics.hasPathCost());
		assertEquals("ten-tile straight walk must cost ten", 10L, metrics.getPathCost());
		assertTrue("local planner must expose explored walking nodes", metrics.hasNodesChecked());
		assertTrue(metrics.getNodesChecked() > 0);
		assertTrue("local planner must expose checked transport count", metrics.hasTransportsChecked());
		assertEquals(0L, metrics.getTransportsChecked());
		try
		{
			result.getPath().add(start);
			fail("result path must be immutable");
		}
		catch (UnsupportedOperationException expected)
		{
			// expected
		}
		try
		{
			result.getSteps().clear();
			fail("result steps must be immutable");
		}
		catch (UnsupportedOperationException expected)
		{
			// expected
		}
	}

	@Test
	public void localPlannerReceivesAnExplicitImmutablePolicySnapshot()
	{
		WorldPoint start = new WorldPoint(3222, 3218, 0);
		WorldPoint target = new WorldPoint(3232, 3218, 0);
		Rs2RouteRequest unresolved = Rs2RouteRequest.to(start, target)
			.withRefreshPolicy(Rs2RouteRequest.RefreshPolicy.NEVER);

		assertTrue(unresolved.getPolicy().isEmpty());
		Rs2RouteRequest resolved = Rs2PathApi.resolvePolicy(unresolved, config);
		Rs2RoutePolicy policy = resolved.getPolicy().orElseThrow(AssertionError::new);

		assertEquals(config.isUseBankItems(), policy.isUseBankItems());
		assertEquals(config.isAvoidWilderness(), policy.isAvoidWilderness());
		assertEquals(config.isAvoidDangerousNpcs(), policy.isAvoidDangerousNpcs());
		assertEquals(config.isIgnoreTeleportAndItems(), policy.isIgnoreTeleportAndItems());
		assertEquals(config.getCalculationCutoffMillis(), policy.getCalculationCutoffMillis());
		assertTrue(policy.getEnabledTransportTypes().contains(Rs2TransportType.TRANSPORT));
		try
		{
			policy.getEnabledTransportTypes().clear();
			fail("resolved transport policy must be immutable");
		}
		catch (UnsupportedOperationException expected)
		{
			// expected
		}

		Rs2RoutePlanner planner = Rs2PathApi.localPlanner(config);
		assertEquals("microbot-local", planner.getEngineId());
		try
		{
			planner.plan(unresolved, Rs2PathApi.resolvePlanningSnapshot(resolved, config));
			fail("an engine must not receive a request backed by mutable globals");
		}
		catch (IllegalArgumentException expected)
		{
			// expected
		}
	}

	@Test
	public void pinnedUpstreamAdapterMatchesProductionBoundaryForStaticWalk()
	{
		WorldPoint start = new WorldPoint(3222, 3218, 0);
		WorldPoint target = new WorldPoint(3232, 3218, 0);
		Rs2RouteRequest request = Rs2PathApi.resolvePolicy(
			Rs2RouteRequest.to(start, target)
				.withRefreshPolicy(Rs2RouteRequest.RefreshPolicy.NEVER),
			config);
		Rs2PlanningSnapshot snapshot = Rs2PathApi.resolvePlanningSnapshot(request, config);

		Rs2RouteResult local = Rs2PathApi.localPlanner(config).plan(request, snapshot);
		Rs2RoutePlanner upstreamPlanner = Rs2PathApi.upstreamPlanner();
		Rs2RouteResult upstream = upstreamPlanner.plan(request, snapshot);
		Rs2PlannerShadowComparison comparison = Rs2PlannerShadowComparison.compare(
			upstreamPlanner.getEngineId(),
			Rs2PlannerShadowContext.from(
				Rs2PlannerShadowContext.Invocation.SYNCHRONOUS_QUERY,
				false,
				request,
				local),
			local,
			upstream);

		assertEquals(Rs2PlannerShadowComparison.Status.MATCH, comparison.getStatus());
		assertTrue(comparison.getShadowEngineId().contains(UpstreamRoutePlanner.REVISION));
		assertEquals(target, upstream.getEndpoint().orElse(null));
		assertEquals(10L, upstream.getMetrics().getPathCost());
	}

	@Test
	public void packagedUpstreamCoreHasNoSecondRuneLitePluginOwner()
	{
		assertFalse(shortestpath.ShortestPathPlugin.class.isAnnotationPresent(
			net.runelite.client.plugins.PluginDescriptor.class));
		assertTrue("upstream core must resolve the pinned root collision archive",
			shortestpath.ShortestPathPlugin.class.getResource("/collision-map.zip") != null);
	}

	@Test
	public void pinnedUpstreamAdapterRetainsExactAmbiguousTransportIdentity() throws Exception
	{
		WorldPoint origin = new WorldPoint(3222, 3218, 0);
		WorldPoint destination = new WorldPoint(3222, 9618, 0);
		Transport slow = new Transport(
			origin, destination, "slow", TransportType.TRANSPORT, false,
			"Climb-down", "Tunnel", 1001, 9);
		Transport fast = new Transport(
			origin, destination, "fast", TransportType.TRANSPORT, false,
			"Climb-down", "Tunnel", 1002, 3);
		PathfinderConfig transportConfig = configWithTransports(
			origin, new LinkedHashSet<>(List.of(slow, fast)));
		Rs2RouteRequest request = Rs2PathApi.resolvePolicy(
			Rs2RouteRequest.to(origin, destination)
				.withRefreshPolicy(Rs2RouteRequest.RefreshPolicy.NEVER),
			transportConfig);
		Rs2PlanningSnapshot snapshot = Rs2PathApi.resolvePlanningSnapshot(request, transportConfig);

		Rs2RouteResult local = Rs2PathApi.localPlanner(transportConfig).plan(request, snapshot);
		Rs2RouteResult upstream = Rs2PathApi.upstreamPlanner().plan(request, snapshot);

		assertSame(fast, local.getTransportSteps().get(0).getTransport()
			.orElseThrow(AssertionError::new).getSourceIdentity());
		assertSame(fast, upstream.getTransportSteps().get(0).getTransport()
			.orElseThrow(AssertionError::new).getSourceIdentity());
		Pathfinder materialized = Rs2PathApi.materializeUpstreamRoute(
			upstream, transportConfig);
		List<Rs2PathApi.ActiveTransportSelection> selections =
			Rs2PathApi.getTransportSelections(materialized, upstream.getPath());
		assertEquals(1, selections.size());
		assertSame("materialization must preserve the exact executable catalog object",
			fast, selections.get(0).getLocalExecutionTransport());
		assertEquals(Rs2PlannerShadowComparison.Status.MATCH,
			Rs2PlannerShadowComparison.compare(
				"upstream",
				Rs2PlannerShadowContext.from(
					Rs2PlannerShadowContext.Invocation.SYNCHRONOUS_QUERY,
					false,
					request,
					local),
				local,
				upstream).getStatus());
	}

	@Test
	public void pinnedUpstreamAdapterMatchesLocalPlannerForEdgevilleBankCanoeLeg() throws Exception
	{
		WorldPoint start = new WorldPoint(3094, 3492, 0);
		WorldPoint target = new WorldPoint(3199, 3344, 0);
		Map<WorldPoint, Set<Transport>> canoes = new HashMap<>();
		for (Map.Entry<WorldPoint, Set<Transport>> entry
			: Transport.loadAllFromResources().entrySet())
		{
			if (entry.getKey() == null)
			{
				continue;
			}
			Set<Transport> selected = new LinkedHashSet<>();
			for (Transport transport : entry.getValue())
			{
				if (transport.getType() == TransportType.CANOE)
				{
					selected.add(transport);
				}
			}
			if (!selected.isEmpty())
			{
				canoes.put(entry.getKey(), selected);
			}
		}
		PathfinderConfig transportConfig = configWithTransportCatalog(canoes);
		transportConfig.setUseBankItems(true);
		Rs2RouteRequest request = Rs2PathApi.resolvePolicy(
			Rs2RouteRequest.to(start, target)
				.withRefreshPolicy(Rs2RouteRequest.RefreshPolicy.NEVER)
				.withPurpose(Rs2RouteRequest.Purpose.BANK_ROUTE_FROM_BANK),
			transportConfig);
		Rs2PlanningSnapshot snapshot = Rs2PathApi.resolvePlanningSnapshot(request, transportConfig);

		Rs2RouteResult local = Rs2PathApi.localPlanner(transportConfig).plan(request, snapshot);
		Rs2RouteResult upstream = Rs2PathApi.upstreamPlanner().plan(request, snapshot);

		assertEquals(Rs2RouteTermination.TARGET_REACHED, local.getTerminationReason());
		assertEquals(Rs2RouteTermination.TARGET_REACHED, upstream.getTerminationReason());
		assertEquals("the real bank-to-target leg must have the same route cost",
			local.getMetrics().getPathCost(), upstream.getMetrics().getPathCost());
		assertEquals("the real bank-to-target leg must select the same exact canoe edge",
			local.getTransportSteps().stream()
				.map(step -> step.getTransport().orElseThrow(AssertionError::new).getSourceIdentity())
				.collect(java.util.stream.Collectors.toList()),
			upstream.getTransportSteps().stream()
				.map(step -> step.getTransport().orElseThrow(AssertionError::new).getSourceIdentity())
				.collect(java.util.stream.Collectors.toList()));
	}

	@Test
	public void pinnedUpstreamAdapterConsumesImmutableCollisionOverride()
	{
		WorldPoint start = new WorldPoint(3222, 3218, 0);
		WorldPoint target = new WorldPoint(3232, 3218, 0);
		Rs2RouteRequest request = Rs2PathApi.resolvePolicy(
			Rs2RouteRequest.to(start, target)
				.withRefreshPolicy(Rs2RouteRequest.RefreshPolicy.NEVER),
			config);
		Rs2PlanningSnapshot base = Rs2PathApi.resolvePlanningSnapshot(request, config);
		Rs2PlanningSnapshot closedArea = new Rs2PlanningSnapshot(
			base.getPolicy(),
			base.getAdmittedTransports(),
			(x, y, plane, flag) -> plane == start.getPlane() ? Boolean.FALSE : null,
			Collections.emptySet(),
			packed -> false);

		Rs2RouteResult result = Rs2PathApi.upstreamPlanner().plan(request, closedArea);

		assertFalse(result.isTargetReached(0));
		assertEquals(Rs2RouteTermination.SEARCH_EXHAUSTED, result.getTerminationReason());
	}

	@Test
	public void shadowFailurePublishesOnlyTheFailureType()
	{
		WorldPoint start = new WorldPoint(3222, 3218, 0);
		WorldPoint target = new WorldPoint(3232, 3218, 0);
		Rs2RouteRequest request = Rs2PathApi.resolvePolicy(
			Rs2RouteRequest.to(start, target)
				.withRefreshPolicy(Rs2RouteRequest.RefreshPolicy.NEVER),
			config);
		Rs2RouteResult local = Rs2PathApi.localPlanner(config).plan(
			request, Rs2PathApi.resolvePlanningSnapshot(request, config));
		Rs2PlannerShadowComparison comparison = Rs2PlannerShadowComparison.failed(
			"upstream",
			Rs2PlannerShadowContext.from(
				Rs2PlannerShadowContext.Invocation.SYNCHRONOUS_QUERY,
				false,
				request,
				local),
			local,
			new IllegalStateException("sensitive runtime detail"));

		assertEquals(Rs2PlannerShadowComparison.Status.FAILED, comparison.getStatus());
		assertEquals("IllegalStateException", comparison.getFailureType());
		assertFalse(comparison.getFailureType().contains("sensitive"));
	}

	@Test
	public void selectedTransportIsPreservedAsOwnedImmutableStep() throws Exception
	{
		WorldPoint origin = new WorldPoint(3222, 3218, 0);
		WorldPoint destination = new WorldPoint(3222, 3218, 1);
		Transport stairs = new Transport(
			origin, destination, "Upper floor", TransportType.TRANSPORT, false,
			"Climb-up", "Staircase", 16671);
		PathfinderConfig transportConfig = configWithTransport(origin, stairs);

		Rs2RouteResult result = Rs2PathApi.planWithConfig(
			Rs2RouteRequest.to(origin, destination)
				.withRefreshPolicy(Rs2RouteRequest.RefreshPolicy.NEVER),
			transportConfig);

		assertEquals(Rs2RouteTermination.TARGET_REACHED, result.getTerminationReason());
		assertEquals(java.util.List.of(origin, destination), result.getPath());
		assertEquals(1, result.getSteps().size());
		Rs2RouteStep step = result.getSteps().get(0);
		assertTrue(step.isTransport());
		Rs2TransportEdge edge = step.getTransport().orElseThrow(AssertionError::new);
		assertEquals(Rs2TransportType.TRANSPORT, edge.getType());
		assertEquals(origin, edge.getOrigin());
		assertEquals(destination, edge.getDestination());
		assertEquals("Climb-up", edge.getAction());
		assertEquals("Staircase", edge.getTarget());
		assertEquals(16671, edge.getObjectId());
		assertFalse(edge.isTeleport());
		assertSame("the local adapter must retain exact source identity opaquely",
			stairs, edge.getSourceIdentity());

		Transport indistinguishableReplacement = new Transport(
			origin, destination, "Upper floor", TransportType.TRANSPORT, false,
			"Climb-up", "Staircase", 16671);
		transportConfig.getTransports().put(origin, Set.of(indistinguishableReplacement));
		assertEquals("catalog refresh must not alter the selected immutable edge",
			"Staircase", edge.getTarget());
		assertEquals(16671, edge.getObjectId());
	}

	@Test
	public void activeExecutorSelectionUsesExactPlannerChoiceWithoutCatalogRematch() throws Exception
	{
		WorldPoint origin = new WorldPoint(3222, 3218, 0);
		WorldPoint destination = new WorldPoint(3222, 9618, 0);
		Transport slow = new Transport(
			origin, destination, "slow shared edge", TransportType.TRANSPORT, false,
			"Climb-down", "Tunnel", 1001, 9);
		Transport fast = new Transport(
			origin, destination, "fast shared edge", TransportType.TRANSPORT, false,
			"Climb-down", "Tunnel", 1002, 3);
		PathfinderConfig transportConfig = configWithTransports(
			origin, new LinkedHashSet<>(List.of(slow, fast)));
		Pathfinder pathfinder = new Pathfinder(transportConfig, origin, Set.of(destination));
		pathfinder.run();
		Transport replacement = new Transport(
			origin, destination, "replacement shared edge", TransportType.TRANSPORT, false,
			"Climb-down", "Tunnel", 1003, 1);
		transportConfig.getTransports().put(origin, Set.of(replacement));

		List<Rs2PathApi.ActiveTransportSelection> selections =
			Rs2PathApi.getTransportSelections(pathfinder, pathfinder.getPath());

		assertEquals(1, selections.size());
		Rs2PathApi.ActiveTransportSelection selected = selections.get(0);
		assertEquals(0, selected.getPathIndex());
		assertSame("local execution adapter must retain the exact selected object", fast,
			selected.getLocalExecutionTransport());
		assertEquals(Rs2TransportExecutor.OBJECT, selected.getExecutor());
		assertTrue(selected.isExecutable());
		assertEquals("fast shared edge", selected.getEdge().getDisplayInfo());
		assertTrue("a stale/different route must not inherit the selection",
			Rs2PathApi.getTransportSelections(pathfinder, List.of(origin)).isEmpty());
	}

	@Test
	public void balloonRouteRetainsItsDedicatedRuntimeExecutor() throws Exception
	{
		WorldPoint origin = new WorldPoint(2461, 3111, 0);
		WorldPoint destination = new WorldPoint(3299, 3482, 0);
		Transport balloon = new Transport(
			origin, destination, "Varrock", TransportType.HOT_AIR_BALLOON, true,
			"Use", "Basket", 19129, 7);
		PathfinderConfig transportConfig = configWithTransport(origin, balloon);

		Rs2RouteResult result = Rs2PathApi.planWithConfig(
			Rs2RouteRequest.to(origin, destination)
				.withRefreshPolicy(Rs2RouteRequest.RefreshPolicy.NEVER),
			transportConfig);

		assertEquals(Rs2RouteTermination.TARGET_REACHED, result.getTerminationReason());
		assertEquals(List.of(origin, destination), result.getPath());
		Rs2TransportEdge edge = result.getSteps().get(0).getTransport()
			.orElseThrow(AssertionError::new);
		assertEquals(Rs2TransportType.HOT_AIR_BALLOON, edge.getType());
		assertEquals(Rs2TransportExecutor.HOT_AIR_BALLOON, edge.getExecutor());
		assertEquals("Varrock", edge.getDisplayInfo());
	}

	@Test
	public void catalogQueriesHideConcreteMutableTransportGraph()
	{
		WorldPoint origin = new WorldPoint(3200, 3200, 0);
		WorldPoint destination = new WorldPoint(3200, 3200, 1);
		Transport stairs = new Transport(
			origin, destination, "Upper floor", TransportType.TRANSPORT, false,
			"Climb-up", "Staircase", 16671);
		Map<WorldPoint, Set<Transport>> catalog = new HashMap<>();
		catalog.put(origin, new LinkedHashSet<>(List.of(stairs)));

		assertTrue(Rs2PathApi.hasCatalogTransportOrigin(catalog, origin));
		assertTrue(Rs2PathApi.hasCatalogTransportEdge(catalog, origin, destination));
		assertFalse(Rs2PathApi.hasCatalogTransportEdge(
			catalog, origin, new WorldPoint(3201, 3200, 0)));
		List<Rs2TransportEdge> edges = Rs2PathApi.getCatalogTransportEdges(catalog, origin);
		assertEquals(1, edges.size());
		assertEquals(destination, edges.get(0).getDestination());
		assertEquals("Staircase", edges.get(0).getTarget());

		catalog.get(origin).clear();
		assertEquals("the returned catalog view must not alias the mutable graph", 1, edges.size());
		try
		{
			edges.clear();
			fail("catalog edge views must be immutable");
		}
		catch (UnsupportedOperationException expected)
		{
			// expected
		}
	}

	@Test
	public void bidirectionalTransportRouteRetainsSearchCost() throws Exception
	{
		WorldPoint origin = new WorldPoint(3222, 3218, 0);
		WorldPoint destination = new WorldPoint(3222, 9618, 0);
		Transport tunnel = new Transport(
			origin, destination, "Synthetic long-band transition",
			TransportType.TRANSPORT, false, 7);
		PathfinderConfig transportConfig = configWithTransport(origin, tunnel);

		Rs2RouteResult result = Rs2PathApi.planWithConfig(
			Rs2RouteRequest.to(origin, destination)
				.withRefreshPolicy(Rs2RouteRequest.RefreshPolicy.NEVER),
			transportConfig);

		assertEquals(Rs2RouteTermination.TARGET_REACHED, result.getTerminationReason());
		assertEquals(java.util.List.of(origin, destination), result.getPath());
		assertTrue(result.getSteps().get(0).isTransport());
		assertEquals("selected transport duration must be the joined route cost",
			7L, result.getMetrics().getPathCost());
	}

	@Test
	public void requestDefensivelyCopiesMultipleTargets()
	{
		WorldPoint start = new WorldPoint(3222, 3218, 0);
		WorldPoint first = new WorldPoint(3232, 3218, 0);
		WorldPoint second = new WorldPoint(3222, 3228, 0);
		Set<WorldPoint> mutableTargets = new LinkedHashSet<>(Set.of(first, second));

		Rs2RouteRequest request = Rs2RouteRequest.toAny(start, mutableTargets);
		mutableTargets.clear();

		assertEquals(Set.of(first, second), request.getTargets());
		assertEquals(Rs2RouteRequest.RefreshPolicy.IF_TRANSPORTS_EMPTY, request.getRefreshPolicy());
		assertFalse(request.getUseBankItems() != null);
	}

	@Test
	public void bankPolicyForcesRefreshWithoutExposingConfig()
	{
		Rs2RouteRequest request = Rs2RouteRequest.to(
			new WorldPoint(3222, 3218, 0), new WorldPoint(3232, 3218, 0))
			.withBankItems(true);

		assertEquals(Boolean.TRUE, request.getUseBankItems());
		assertEquals(Rs2RouteRequest.RefreshPolicy.ALWAYS, request.getRefreshPolicy());
	}

	@Test
	public void publicPlanRestoresTemporaryBankPolicy() throws Exception
	{
		RecordingPathfinderConfig recording = new RecordingPathfinderConfig();
		setCalculationCutoff(recording);
		PathfinderConfig original = ShortestPathPlugin.pathfinderConfig;
		WorldPoint refreshTarget = new WorldPoint(3232, 3218, 0);
		try
		{
			ShortestPathPlugin.pathfinderConfig = recording;
			Rs2RouteResult result = Rs2PathApi.plan(
				Rs2RouteRequest.to(
					new WorldPoint(3222, 3218, 0),
					refreshTarget)
					.withRefreshTarget(refreshTarget)
					.withBankItems(true));

			assertTrue(result.isTargetReached(0));
			assertFalse("shared config must be restored after bank-aware planning",
				recording.isUseBankItems());
			assertEquals("refresh must observe the temporary policy and then its restoration",
				java.util.List.of(Boolean.TRUE, Boolean.FALSE), recording.refreshPolicies);
			assertEquals("policy restoration must retain the caller's refresh target",
				java.util.List.of(refreshTarget, refreshTarget), recording.refreshTargets);
		}
		finally
		{
			ShortestPathPlugin.pathfinderConfig = original;
		}
	}

	@Test
	public void unchangedBankPolicyDoesNotPerformARedundantRestoreRefresh() throws Exception
	{
		RecordingPathfinderConfig recording = new RecordingPathfinderConfig();
		setCalculationCutoff(recording);
		PathfinderConfig original = ShortestPathPlugin.pathfinderConfig;
		WorldPoint target = new WorldPoint(3232, 3218, 0);
		try
		{
			ShortestPathPlugin.pathfinderConfig = recording;
			Rs2PathApi.plan(
				Rs2RouteRequest.to(new WorldPoint(3222, 3218, 0), target)
					.withRefreshTarget(target)
					.withBankItems(false));

			assertEquals(java.util.List.of(Boolean.FALSE), recording.refreshPolicies);
			assertEquals(java.util.List.of(target), recording.refreshTargets);
		}
		finally
		{
			ShortestPathPlugin.pathfinderConfig = original;
		}
	}

	@Test
	public void namedRuntimePolicyOperationsOwnMutableConfiguration()
	{
		PathfinderConfig original = ShortestPathPlugin.pathfinderConfig;
		PathfinderConfig recording = mock(PathfinderConfig.class);
		WorldPoint origin = new WorldPoint(3200, 3200, 0);
		WorldPoint destination = new WorldPoint(3201, 3200, 0);
		WorldPoint target = new WorldPoint(3232, 3218, 0);
		when(recording.isAvoidDangerousNpcs()).thenReturn(true);
		when(recording.isDangerousAdjacentTile(WorldPointUtil.packWorldPoint(origin))).thenReturn(true);
		when(recording.isUseSpiritTrees()).thenReturn(true);
		when(recording.learnBlockedEdge(origin, destination, "stable failure")).thenReturn(true);
		try
		{
			ShortestPathPlugin.pathfinderConfig = recording;

			assertTrue(Rs2PathApi.shouldAvoidDangerousTile(origin));
			assertTrue(Rs2PathApi.isSpiritTreeTravelEnabled());
			assertTrue(Rs2PathApi.learnBlockedEdge(origin, destination, "stable failure"));
			assertTrue(Rs2PathApi.refreshPlanningConfiguration());
			assertTrue(Rs2PathApi.invalidateTransportRefreshCache());
			assertTrue(Rs2PathApi.prepareInventoryOnlyRoute(target));

			verify(recording).learnBlockedEdge(origin, destination, "stable failure");
			verify(recording).refresh((WorldPoint) null);
			verify(recording).invalidateTransportRefreshCache();
			verify(recording).setUseBankItems(false);
			verify(recording).refresh(target);
		}
		finally
		{
			ShortestPathPlugin.pathfinderConfig = original;
		}
	}

	@Test
	public void teleportItemClassificationIncludesCatalogAndCompatibilityItems()
	{
		PathfinderConfig original = ShortestPathPlugin.pathfinderConfig;
		PathfinderConfig recording = mock(PathfinderConfig.class);
		Transport teleport = new Transport(
			new WorldPoint(3210, 3210, 0),
			"Synthetic teleport",
			TransportType.TELEPORTATION_ITEM,
			false,
			0,
			Set.of(Set.of(1234)));
		Map<WorldPoint, Set<Transport>> catalog = new HashMap<>();
		catalog.put(null, Set.of(teleport));
		when(recording.getAllTransports()).thenReturn(catalog);
		try
		{
			ShortestPathPlugin.pathfinderConfig = recording;

			assertTrue(Rs2PathApi.isTeleportItem(1234, 5678));
			assertTrue(Rs2PathApi.isTeleportItem(5678, 5678));
			assertFalse(Rs2PathApi.isTeleportItem(9999, 5678));
		}
		finally
		{
			ShortestPathPlugin.pathfinderConfig = original;
		}
	}

	@Test
	public void caveRouteSelectionChecksEveryRequestedTarget()
	{
		Pathfinder normal = mock(Pathfinder.class);
		Pathfinder walkingOnly = mock(Pathfinder.class);
		WorldPoint start = new WorldPoint(3200, 3200, 0);
		WorldPoint firstTarget = new WorldPoint(3300, 3300, 0);
		WorldPoint reachedSecondTarget = new WorldPoint(3202, 3200, 0);
		when(normal.getPath()).thenReturn(List.of(
			start,
			new WorldPoint(3201, 3201, 0),
			new WorldPoint(3202, 3201, 0),
			new WorldPoint(3203, 3201, 0)));
		when(walkingOnly.getPath()).thenReturn(List.of(
			start,
			new WorldPoint(3201, 3200, 0),
			reachedSecondTarget));

		Pathfinder selected = Rs2PathApi.selectCaveRoute(
			normal,
			walkingOnly,
			new LinkedHashSet<>(List.of(firstTarget, reachedSecondTarget)),
			0);

		assertSame("a reachable non-first target must qualify the walking-only route",
			walkingOnly, selected);
	}

	@Test(expected = IllegalArgumentException.class)
	public void activeRouteRejectsSynchronousShadowInvocation()
	{
		Rs2PathApi.restartActiveRoute(
			Rs2RouteRequest.to(
				new WorldPoint(3222, 3218, 0),
				new WorldPoint(3232, 3218, 0)),
			false,
			0,
			Rs2PlannerShadowContext.Invocation.SYNCHRONOUS_QUERY);
	}

	@Test
	public void canarySelectsRouteShapeOnlySemanticMatchAndRejectsCostDivergence()
		throws Exception
	{
		PathfinderConfig f2pConfig = f2pConfig();
		WorldPoint start = new WorldPoint(3200, 3200, 0);
		WorldPoint target = new WorldPoint(3202, 3200, 0);
		Rs2RouteRequest request = Rs2PathApi.resolvePolicy(
			Rs2RouteRequest.to(start, target)
				.withRefreshPolicy(Rs2RouteRequest.RefreshPolicy.NEVER),
			f2pConfig);
		Rs2RouteResult local = new Rs2RouteResult(
			start,
			Set.of(target),
			List.of(start, new WorldPoint(3201, 3200, 0), target),
			List.of(
				Rs2RouteStep.walk(start, new WorldPoint(3201, 3200, 0)),
				Rs2RouteStep.walk(new WorldPoint(3201, 3200, 0), target)),
			Rs2RouteTermination.TARGET_REACHED,
			new Rs2RouteMetrics(10L, 2L, 3L, 0L));
		WorldPoint alternate = new WorldPoint(3201, 3201, 0);
		Rs2RouteResult equalCostAlternate = new Rs2RouteResult(
			start,
			Set.of(target),
			List.of(start, alternate, target),
			List.of(Rs2RouteStep.walk(start, alternate), Rs2RouteStep.walk(alternate, target)),
			Rs2RouteTermination.TARGET_REACHED,
			new Rs2RouteMetrics(9L, 2L, 2L, 0L));
		Rs2PlannerShadowContext context = Rs2PlannerShadowContext.from(
			Rs2PlannerShadowContext.Invocation.SYNCHRONOUS_QUERY,
			false,
			request,
			local);

		Rs2PlannerShadowComparison match = Rs2PlannerShadowComparison.compare(
			"upstream", context, local, equalCostAlternate);
		assertEquals(Rs2PlannerShadowComparison.Status.MATCH, match.getStatus());
		assertFalse(match.isPathMatches());
		assertTrue(Rs2PathApi.shouldSelectUpstream(match));

		Rs2RouteResult higherCost = new Rs2RouteResult(
			start,
			Set.of(target),
			equalCostAlternate.getPath(),
			equalCostAlternate.getSteps(),
			Rs2RouteTermination.TARGET_REACHED,
			new Rs2RouteMetrics(9L, 3L, 2L, 0L));
		Rs2PlannerShadowComparison divergence = Rs2PlannerShadowComparison.compare(
			"upstream", context, local, higherCost);
		assertEquals(Rs2PlannerShadowComparison.Status.DIVERGENCE, divergence.getStatus());
		assertFalse(Rs2PathApi.shouldSelectUpstream(divergence));
	}

	@Test
	public void f2pCanaryEligibilityUsesResolvedWorldPolicy() throws Exception
	{
		PathfinderConfig f2pConfig = f2pConfig();
		Rs2RouteRequest f2p = Rs2PathApi.resolvePolicy(
			Rs2RouteRequest.to(
				new WorldPoint(3200, 3200, 0), new WorldPoint(3201, 3200, 0)),
			f2pConfig);
		assertTrue(Rs2PathApi.isF2pCanary(
			PlannerSelectionMode.UPSTREAM_F2P_CANARY, f2p));

		Client membersClient = mock(Client.class);
		when(membersClient.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS));
		PathfinderConfig membersConfig = new PathfinderConfig(
			SplitFlagMap.fromResources(), new HashMap<>(), Collections.emptyList(),
			membersClient, null);
		setCalculationCutoff(membersConfig);
		Rs2RouteRequest members = Rs2PathApi.resolvePolicy(
			Rs2RouteRequest.to(
				new WorldPoint(3200, 3200, 0), new WorldPoint(3201, 3200, 0)),
			membersConfig);
		assertFalse(Rs2PathApi.isF2pCanary(
			PlannerSelectionMode.UPSTREAM_F2P_CANARY, members));
		assertFalse(Rs2PathApi.isF2pCanary(PlannerSelectionMode.SHADOW, f2p));
	}

	@Test
	public void activeRouteRemainsCalculatingUntilSelectionFutureCompletes()
	{
		Pathfinder originalPathfinder = Rs2PathApi.getPathfinder();
		Future<?> originalFuture = Rs2PathApi.getPathfinderFuture();
		WorldPoint start = new WorldPoint(3222, 3218, 0);
		WorldPoint target = new WorldPoint(3232, 3218, 0);
		Pathfinder completed = new Pathfinder(config, start, target);
		completed.run();
		Future<?> selectionFuture = mock(Future.class);
		when(selectionFuture.isDone()).thenReturn(false);
		try
		{
			Rs2PathApi.setPathfinder(completed);
			Rs2PathApi.setPathfinderFuture(selectionFuture);
			assertTrue(Rs2PathApi.getActiveRouteStatus().isCalculating());
			assertTrue(Rs2PathApi.getActiveRoute().isEmpty());

			when(selectionFuture.isDone()).thenReturn(true);
			assertTrue(Rs2PathApi.getActiveRouteStatus().isReady());
			assertEquals(target, Rs2PathApi.getActiveRoute()
				.flatMap(Rs2RouteResult::getEndpoint).orElse(null));
		}
		finally
		{
			Rs2PathApi.setPathfinder(originalPathfinder);
			Rs2PathApi.setPathfinderFuture(originalFuture);
		}
	}

	@Test
	public void activeF2pCanarySelectsPinnedUpstreamRoute() throws Exception
	{
		PathfinderConfig activeConfig = f2pConfig();
		setPlannerMode(activeConfig, PlannerSelectionMode.UPSTREAM_F2P_CANARY);
		String originalFailure = System.getProperty(
			"microbot.test.walker.forceUpstreamPlannerFailure");
		System.clearProperty("microbot.test.walker.forceUpstreamPlannerFailure");

		PathfinderConfig originalConfig = ShortestPathPlugin.pathfinderConfig;
		Pathfinder originalPathfinder = Rs2PathApi.getPathfinder();
		Future<?> originalFuture = Rs2PathApi.getPathfinderFuture();
		ExecutorService originalExecutor = Rs2PathApi.getPathfindingExecutor();
		ExecutorService activeExecutor = Executors.newSingleThreadExecutor();
		Rs2PlannerShadowStats before = Rs2PathApi.getShadowStats();
		WorldPoint target = new WorldPoint(3232, 3218, 0);
		try
		{
			ShortestPathPlugin.pathfinderConfig = activeConfig;
			Rs2PathApi.setPathfindingExecutor(activeExecutor);
			assertTrue(Rs2PathApi.restartActiveRoute(
				Rs2RouteRequest.to(new WorldPoint(3222, 3218, 0), target)
					.withRefreshPolicy(Rs2RouteRequest.RefreshPolicy.NEVER),
				false,
				0));
			assertTrue(Rs2PathApi.isActiveRouteComparisonEligible());
			long routeGeneration = Rs2PathApi.getActiveRouteStatus().getGeneration();
			assertTrue(Rs2PathApi.isActiveRouteComparisonEligible(routeGeneration));
			// The canary runs the local and upstream planners sequentially. Each inherits the
			// 10-second calculation cutoff, so the lifecycle bound must cover both under a busy suite.
			Rs2PathApi.getPathfinderFuture().get(
				ACTIVE_ROUTE_TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

			assertTrue(Rs2PathApi.getActiveRouteStatus().isReady());
			assertEquals(target, Rs2PathApi.getActiveRoute()
				.flatMap(Rs2RouteResult::getEndpoint).orElse(null));
			Rs2PlannerShadowStats after = Rs2PathApi.getShadowStats();
			assertEquals(before.getSubmitted() + 1, after.getSubmitted());
			assertEquals(before.getCompleted() + 1, after.getCompleted());
			assertEquals(before.getUpstreamCanarySelections() + 1,
				after.getUpstreamCanarySelections());
			assertEquals(before.getLocalFallbackDivergences(),
				after.getLocalFallbackDivergences());
			assertEquals(before.getLocalFallbackFailures(), after.getLocalFallbackFailures());
			assertEquals(before.getCanaryPerformance().getPlanningSamples() + 1,
				after.getCanaryPerformance().getPlanningSamples());
			assertEquals(before.getCanaryPerformance().getUpstreamSearchSamples() + 1,
				after.getCanaryPerformance().getUpstreamSearchSamples());
			assertTrue(after.getCanaryPerformance().getPlanningNanosTotal()
				> before.getCanaryPerformance().getPlanningNanosTotal());
			assertTrue(after.getCanaryPerformance().getLocalSearchNanosTotal()
				> before.getCanaryPerformance().getLocalSearchNanosTotal());
			assertTrue(after.getCanaryPerformance().getUpstreamSearchNanosTotal()
				> before.getCanaryPerformance().getUpstreamSearchNanosTotal());
		}
		finally
		{
			activeExecutor.shutdownNow();
			Rs2PathApi.setPathfinder(originalPathfinder);
			Rs2PathApi.setPathfinderFuture(originalFuture);
			Rs2PathApi.setPathfindingExecutor(originalExecutor);
			ShortestPathPlugin.pathfinderConfig = originalConfig;
			restoreProperty("microbot.test.walker.forceUpstreamPlannerFailure", originalFailure);
		}
	}

	@Test
	public void activeF2pCanaryFallsBackOnForcedUpstreamFailure() throws Exception
	{
		PathfinderConfig activeConfig = f2pConfig();
		setPlannerMode(activeConfig, PlannerSelectionMode.UPSTREAM_F2P_CANARY);
		String originalTestMode = System.getProperty("microbot.test.mode");
		String originalFailure = System.getProperty(
			"microbot.test.walker.forceUpstreamPlannerFailure");
		System.setProperty("microbot.test.mode", "true");
		System.setProperty("microbot.test.walker.forceUpstreamPlannerFailure", "true");

		PathfinderConfig originalConfig = ShortestPathPlugin.pathfinderConfig;
		Pathfinder originalPathfinder = Rs2PathApi.getPathfinder();
		Future<?> originalFuture = Rs2PathApi.getPathfinderFuture();
		ExecutorService originalExecutor = Rs2PathApi.getPathfindingExecutor();
		ExecutorService activeExecutor = Executors.newSingleThreadExecutor();
		Rs2PlannerShadowStats before = Rs2PathApi.getShadowStats();
		WorldPoint target = new WorldPoint(3232, 3218, 0);
		try
		{
			ShortestPathPlugin.pathfinderConfig = activeConfig;
			Rs2PathApi.setPathfindingExecutor(activeExecutor);
			assertTrue(Rs2PathApi.restartActiveRoute(
				Rs2RouteRequest.to(new WorldPoint(3222, 3218, 0), target)
					.withRefreshPolicy(Rs2RouteRequest.RefreshPolicy.NEVER),
				false,
				0));
			assertTrue(Rs2PathApi.isActiveRouteComparisonEligible());
			Rs2PathApi.getPathfinderFuture().get(
				ACTIVE_ROUTE_TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

			assertTrue("the local rollback route must remain executable",
				Rs2PathApi.getActiveRouteStatus().isReady());
			assertEquals(target, Rs2PathApi.getActiveRoute()
				.flatMap(Rs2RouteResult::getEndpoint).orElse(null));
			Rs2PlannerShadowStats after = Rs2PathApi.getShadowStats();
			assertEquals(before.getFailures() + 1, after.getFailures());
			assertEquals(before.getLocalFallbackFailures() + 1,
				after.getLocalFallbackFailures());
			assertEquals(before.getUpstreamCanarySelections(),
				after.getUpstreamCanarySelections());
			assertEquals(before.getCanaryPerformance().getPlanningSamples() + 1,
				after.getCanaryPerformance().getPlanningSamples());
			assertEquals(before.getCanaryPerformance().getUpstreamSearchSamples(),
				after.getCanaryPerformance().getUpstreamSearchSamples());
		}
		finally
		{
			activeExecutor.shutdownNow();
			Rs2PathApi.setPathfinder(originalPathfinder);
			Rs2PathApi.setPathfinderFuture(originalFuture);
			Rs2PathApi.setPathfindingExecutor(originalExecutor);
			ShortestPathPlugin.pathfinderConfig = originalConfig;
			restoreProperty("microbot.test.mode", originalTestMode);
			restoreProperty("microbot.test.walker.forceUpstreamPlannerFailure", originalFailure);
		}
	}

	@Test
	public void activeF2pCaveCanaryCountsBothLocalCandidateSearches() throws Exception
	{
		PathfinderConfig activeConfig = f2pConfig();
		setPlannerMode(activeConfig, PlannerSelectionMode.UPSTREAM_F2P_CANARY);
		String originalFailure = System.getProperty(
			"microbot.test.walker.forceUpstreamPlannerFailure");
		System.clearProperty("microbot.test.walker.forceUpstreamPlannerFailure");
		PathfinderConfig originalConfig = ShortestPathPlugin.pathfinderConfig;
		Pathfinder originalPathfinder = Rs2PathApi.getPathfinder();
		Future<?> originalFuture = Rs2PathApi.getPathfinderFuture();
		Rs2PlannerShadowStats before = Rs2PathApi.getShadowStats();
		try
		{
			ShortestPathPlugin.pathfinderConfig = activeConfig;
			assertTrue(Rs2PathApi.restartActiveRoute(
				Rs2RouteRequest.to(
					new WorldPoint(3222, 3218, 0), new WorldPoint(3232, 3218, 0))
					.withRefreshPolicy(Rs2RouteRequest.RefreshPolicy.NEVER),
				true,
				0));

			Rs2PlannerShadowStats after = Rs2PathApi.getShadowStats();
			Rs2PlannerShadowComparison comparison = Rs2PathApi.getLastShadowComparison()
				.orElseThrow(AssertionError::new);
			long localPlanningDelta = after.getCanaryPerformance().getLocalSearchNanosTotal()
				- before.getCanaryPerformance().getLocalSearchNanosTotal();
			assertEquals(before.getCanaryPerformance().getPlanningSamples() + 1,
				after.getCanaryPerformance().getPlanningSamples());
			assertTrue("cave timing must include the unselected local candidate search",
				localPlanningDelta > comparison.getLocalSearchNanos());
		}
		finally
		{
			Rs2PathApi.setPathfinder(originalPathfinder);
			Rs2PathApi.setPathfinderFuture(originalFuture);
			ShortestPathPlugin.pathfinderConfig = originalConfig;
			restoreProperty("microbot.test.walker.forceUpstreamPlannerFailure", originalFailure);
		}
	}

	@Test
	public void activeMembersRouteInF2pCanaryDoesNotPolluteExecutionEvidence() throws Exception
	{
		PathfinderConfig activeConfig = membersConfig();
		setPlannerMode(activeConfig, PlannerSelectionMode.UPSTREAM_F2P_CANARY);
		PathfinderConfig originalConfig = ShortestPathPlugin.pathfinderConfig;
		Pathfinder originalPathfinder = Rs2PathApi.getPathfinder();
		Future<?> originalFuture = Rs2PathApi.getPathfinderFuture();
		ExecutorService originalExecutor = Rs2PathApi.getPathfindingExecutor();
		ExecutorService activeExecutor = Executors.newSingleThreadExecutor();
		Rs2PlannerShadowStats before = Rs2PathApi.getShadowStats();
		try
		{
			ShortestPathPlugin.pathfinderConfig = activeConfig;
			Rs2PathApi.setPathfindingExecutor(activeExecutor);
			assertTrue(Rs2PathApi.restartActiveRoute(
				Rs2RouteRequest.to(
					new WorldPoint(3222, 3218, 0), new WorldPoint(3232, 3218, 0))
					.withRefreshPolicy(Rs2RouteRequest.RefreshPolicy.NEVER),
				false,
				0));
			assertFalse(Rs2PathApi.isActiveRouteComparisonEligible());
			assertFalse(Rs2PathApi.isActiveRouteComparisonEligible(
				Rs2PathApi.getActiveRouteStatus().getGeneration()));
			Rs2PathApi.getPathfinderFuture().get(
				ACTIVE_ROUTE_TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

			Rs2PlannerShadowStats afterRoute = Rs2PathApi.getShadowStats();
			assertEquals(before.getSubmitted(), afterRoute.getSubmitted());
			assertEquals(before.getCanaryPerformance().getPlanningSamples(),
				afterRoute.getCanaryPerformance().getPlanningSamples());
			Rs2PathApi.recordShadowWalkerOutcome(WalkerState.ARRIVED, false, false);
			Rs2PlannerShadowStats afterOutcome = Rs2PathApi.getShadowStats();
			assertEquals(before.getExecution().getArrived(),
				afterOutcome.getExecution().getArrived());
		}
		finally
		{
			activeExecutor.shutdownNow();
			Rs2PathApi.setPathfinder(originalPathfinder);
			Rs2PathApi.setPathfinderFuture(originalFuture);
			Rs2PathApi.setPathfindingExecutor(originalExecutor);
			ShortestPathPlugin.pathfinderConfig = originalConfig;
		}
	}

	@Test
	public void activeLocalRouteDoesNotAdmitComparisonExecutionEvidence() throws Exception
	{
		PathfinderConfig activeConfig = f2pConfig();
		setPlannerMode(activeConfig, PlannerSelectionMode.LOCAL);
		PathfinderConfig originalConfig = ShortestPathPlugin.pathfinderConfig;
		Pathfinder originalPathfinder = Rs2PathApi.getPathfinder();
		Future<?> originalFuture = Rs2PathApi.getPathfinderFuture();
		ExecutorService originalExecutor = Rs2PathApi.getPathfindingExecutor();
		ExecutorService activeExecutor = Executors.newSingleThreadExecutor();
		Rs2PlannerShadowStats before = Rs2PathApi.getShadowStats();
		try
		{
			ShortestPathPlugin.pathfinderConfig = activeConfig;
			Rs2PathApi.setPathfindingExecutor(activeExecutor);
			assertTrue(Rs2PathApi.restartActiveRoute(
				Rs2RouteRequest.to(
					new WorldPoint(3222, 3218, 0), new WorldPoint(3232, 3218, 0))
					.withRefreshPolicy(Rs2RouteRequest.RefreshPolicy.NEVER),
				false,
				0));
			assertFalse(Rs2PathApi.isActiveRouteComparisonEligible());
			assertFalse(Rs2PathApi.isActiveRouteComparisonEligible(
				Rs2PathApi.getActiveRouteStatus().getGeneration()));
			Rs2PathApi.getPathfinderFuture().get(
				ACTIVE_ROUTE_TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
			assertEquals(before.getSubmitted(), Rs2PathApi.getShadowStats().getSubmitted());
			assertEquals(before.getCanaryPerformance().getPlanningSamples(),
				Rs2PathApi.getShadowStats().getCanaryPerformance().getPlanningSamples());
		}
		finally
		{
			activeExecutor.shutdownNow();
			Rs2PathApi.setPathfinder(originalPathfinder);
			Rs2PathApi.setPathfinderFuture(originalFuture);
			Rs2PathApi.setPathfindingExecutor(originalExecutor);
			ShortestPathPlugin.pathfinderConfig = originalConfig;
		}
	}

	@Test
	public void activeWalkerRoutePublishesPinnedUpstreamShadowEvidence() throws Exception
	{
		PathfinderConfig activeConfig = new PathfinderConfig(
			SplitFlagMap.fromResources(), new HashMap<>(), Collections.emptyList(), null, null);
		setCalculationCutoff(activeConfig);
		setPlannerMode(activeConfig, PlannerSelectionMode.SHADOW);

		PathfinderConfig originalConfig = ShortestPathPlugin.pathfinderConfig;
		Pathfinder originalPathfinder = Rs2PathApi.getPathfinder();
		Future<?> originalFuture = Rs2PathApi.getPathfinderFuture();
		ExecutorService originalExecutor = Rs2PathApi.getPathfindingExecutor();
		Field lastComparison = Rs2PathApi.class.getDeclaredField("lastShadowComparison");
		lastComparison.setAccessible(true);
		Object originalComparison = lastComparison.get(null);
		Rs2PlannerShadowStats statsBefore = Rs2PathApi.getShadowStats();
		ExecutorService activeExecutor = Executors.newSingleThreadExecutor();
		WorldPoint start = new WorldPoint(3222, 3218, 0);
		WorldPoint target = new WorldPoint(3232, 3218, 0);
		try
		{
			ShortestPathPlugin.pathfinderConfig = activeConfig;
			Rs2PathApi.setPathfindingExecutor(activeExecutor);
			lastComparison.set(null, null);

			assertTrue(Rs2PathApi.restartActiveRoute(
				Rs2RouteRequest.to(start, target)
					.withRefreshPolicy(Rs2RouteRequest.RefreshPolicy.NEVER),
				false,
				0));
			Rs2PathApi.getPathfinderFuture().get(
				ACTIVE_ROUTE_TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

			Rs2PlannerShadowComparison comparison = null;
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
			while (comparison == null && System.nanoTime() < deadline)
			{
				comparison = Rs2PathApi.getLastShadowComparison().orElse(null);
				Thread.yield();
			}

			assertTrue("active route must publish a completed shadow comparison",
				comparison != null);
			assertEquals(Rs2PlannerShadowComparison.Status.MATCH, comparison.getStatus());
			assertTrue(comparison.getShadowEngineId().contains(UpstreamRoutePlanner.REVISION));
			assertEquals(Rs2PlannerShadowContext.Invocation.ACTIVE_ROUTE,
				comparison.getContext().getInvocation());
			assertTrue(comparison.getContext().getCoverage().contains(
				Rs2PlannerShadowContext.Coverage.SURFACE_COORDINATES_ONLY));
			assertTrue(comparison.getLocalSearchNanos() > 0L);
			Rs2PlannerShadowStats statsAfter = Rs2PathApi.getShadowStats();
			assertEquals(statsBefore.getSubmitted() + 1, statsAfter.getSubmitted());
			assertEquals(statsBefore.getCompleted() + 1, statsAfter.getCompleted());
			assertEquals(statsBefore.getMatches() + 1, statsAfter.getMatches());
			assertEquals(
				statsBefore.getCoverage().get(Rs2PlannerShadowContext.Coverage.ACTIVE_ROUTE)
					.getMatches() + 1,
				statsAfter.getCoverage().get(Rs2PlannerShadowContext.Coverage.ACTIVE_ROUTE)
					.getMatches());
			assertEquals(
				statsBefore.getCoverage().get(
					Rs2PlannerShadowContext.Coverage.SURFACE_COORDINATES_ONLY).getMatches() + 1,
				statsAfter.getCoverage().get(
					Rs2PlannerShadowContext.Coverage.SURFACE_COORDINATES_ONLY).getMatches());
			assertEquals(0L, statsAfter.getPending());
			assertTrue(Rs2PathApi.isActiveRouteComparisonEligible());
			Rs2PathApi.recordShadowWalkerOutcome(WalkerState.ARRIVED, true, true);
			Rs2PlannerShadowStats executionAfter = Rs2PathApi.getShadowStats();
			assertEquals(statsAfter.getExecution().getArrived() + 1,
				executionAfter.getExecution().getArrived());
			assertEquals(statsAfter.getExecution().getRecoveryArrived() + 1,
				executionAfter.getExecution().getRecoveryArrived());
			Rs2PathApi.setPathfinder(null);
			assertTrue("route replacement must invalidate the previous latest evidence",
				Rs2PathApi.getLastShadowComparison().isEmpty());
		}
		finally
		{
			activeExecutor.shutdownNow();
			Rs2PathApi.setPathfinder(originalPathfinder);
			Rs2PathApi.setPathfinderFuture(originalFuture);
			Rs2PathApi.setPathfindingExecutor(originalExecutor);
			ShortestPathPlugin.pathfinderConfig = originalConfig;
			lastComparison.set(null, originalComparison);
		}
	}

	@Test
	public void cancelAndClearActiveRouteOwnsConcretePlannerCancellation()
	{
		Pathfinder originalPathfinder = Rs2PathApi.getPathfinder();
		Future<?> originalFuture = Rs2PathApi.getPathfinderFuture();
		Pathfinder active = mock(Pathfinder.class);
		Future<?> future = mock(Future.class);
		when(future.isDone()).thenReturn(false);
		try
		{
			Rs2PathApi.setPathfinder(active);
			Rs2PathApi.setPathfinderFuture(future);

			Rs2PathApi.cancelAndClearActiveRoute();

			verify(active).cancel();
			verify(future).cancel(true);
			assertNull(Rs2PathApi.getPathfinder());
			assertNull(Rs2PathApi.getPathfinderFuture());
		}
		finally
		{
			Rs2PathApi.setPathfinder(originalPathfinder);
			Rs2PathApi.setPathfinderFuture(originalFuture);
		}
	}

	@Test
	public void activeRouteStatusDefensivelySnapshotsCalculatingPlanner()
	{
		Pathfinder originalPathfinder = Rs2PathApi.getPathfinder();
		Pathfinder active = mock(Pathfinder.class);
		WorldPoint start = new WorldPoint(3200, 3200, 0);
		WorldPoint target = new WorldPoint(3210, 3200, 0);
		List<WorldPoint> partialPath = new ArrayList<>(List.of(start, new WorldPoint(3201, 3200, 0)));
		Set<WorldPoint> targets = new LinkedHashSet<>(Set.of(target));
		when(active.isDone()).thenReturn(false);
		when(active.getStart()).thenReturn(start);
		when(active.getTargets()).thenReturn(targets);
		when(active.getPath()).thenReturn(partialPath);
		try
		{
			long before = Rs2PathApi.getActiveRouteStatus().getGeneration();
			Rs2PathApi.setPathfinder(active);

			Rs2ActiveRouteStatus status = Rs2PathApi.getActiveRouteStatus();

			assertEquals(Rs2ActiveRouteStatus.Phase.CALCULATING, status.getPhase());
			assertTrue(status.isPresent());
			assertTrue(status.isCalculating());
			assertTrue(status.getGeneration() > before);
			assertEquals(start, status.getStart().orElse(null));
			assertEquals(Set.of(target), status.getTargets());
			assertEquals(partialPath, status.getRawPath());
			assertEquals(status.getRawPath(), status.getWalkablePath());
			partialPath.clear();
			targets.clear();
			assertEquals(2, status.getRawPath().size());
			assertEquals(Set.of(target), status.getTargets());
			try
			{
				status.getRawPath().clear();
				fail("active route path must be immutable");
			}
			catch (UnsupportedOperationException expected)
			{
				// expected
			}
		}
		finally
		{
			Rs2PathApi.setPathfinder(originalPathfinder);
		}
	}

	@Test
	public void activeRouteStatusPublishesReadyMetricsWithoutPlannerType()
	{
		Pathfinder originalPathfinder = Rs2PathApi.getPathfinder();
		WorldPoint start = new WorldPoint(3222, 3218, 0);
		WorldPoint target = new WorldPoint(3232, 3218, 0);
		Pathfinder active = new Pathfinder(config, start, target);
		active.run();
		try
		{
			Rs2PathApi.setPathfinder(active);

			Rs2ActiveRouteStatus status = Rs2PathApi.getActiveRouteStatus();

			assertTrue(status.isReady());
			assertEquals(Rs2RouteTermination.TARGET_REACHED,
				status.getTerminationReason().orElse(null));
			assertEquals(target, status.getEndpoint().orElse(null));
			Rs2RouteMetrics metrics = status.getMetrics().orElseThrow(AssertionError::new);
			assertTrue(metrics.hasSearchNanos());
			assertTrue(metrics.getNodesChecked() > 0);
			assertEquals(10L, metrics.getPathCost());
		}
		finally
		{
			Rs2PathApi.setPathfinder(originalPathfinder);
		}
	}

	@Test
	public void requestRejectsEmptyTargetsAndResultRejectsNegativeTolerance()
	{
		WorldPoint start = new WorldPoint(3222, 3218, 0);
		try
		{
			Rs2RouteRequest.toAny(start, Collections.emptySet());
			fail("empty targets must be rejected");
		}
		catch (IllegalArgumentException expected)
		{
			// expected
		}

		Rs2RouteResult result = Rs2PathApi.planWithConfig(
			Rs2RouteRequest.to(start, new WorldPoint(3232, 3218, 0))
				.withRefreshPolicy(Rs2RouteRequest.RefreshPolicy.NEVER),
			config);
		try
		{
			result.isTargetReached(-1);
			fail("negative tolerance must be rejected");
		}
		catch (IllegalArgumentException expected)
		{
			// expected
		}
	}

	@Test
	public void failedPlannerIsNotReportedAsCompleted()
	{
		PathfinderConfig failingConfig = mock(PathfinderConfig.class);
		CollisionMap failingMap = mock(CollisionMap.class);
		when(failingConfig.getMap()).thenReturn(failingMap);
		when(failingConfig.getCalculationCutoffMillis()).thenReturn(10_000L);
		when(failingConfig.getEnabledTransportTypes()).thenReturn(Collections.emptySet());
		when(failingConfig.getRestrictedPointsPacked()).thenReturn(Collections.emptySet());
		when(failingConfig.getTeleportationItemPolicy()).thenReturn(
			net.runelite.client.plugins.microbot.shortestpath.TeleportationItem.NONE);
		when(failingConfig.getLiveCollisionOverlay()).thenReturn(
			new net.runelite.client.plugins.microbot.shortestpath.pathfinder.live.LiveCollisionOverlay());
		when(failingMap.getNeighbors(any(Node.class), any(VisitedTiles.class),
			eq(failingConfig), anySet())).thenThrow(new IllegalStateException("synthetic planner failure"));

		Rs2RouteResult result = Rs2PathApi.planWithConfig(
			Rs2RouteRequest.to(
				new WorldPoint(3222, 3218, 0),
				new WorldPoint(3232, 3218, 0)),
			failingConfig);

		assertEquals(Rs2RouteTermination.FAILED, result.getTerminationReason());
		assertFalse("a caught planner failure must not look completed", result.isSearchCompleted());
		assertEquals(Math.max(0, result.getPath().size() - 1), result.getSteps().size());
	}

	@Test
	public void ownedItemRequirementDefensivelyCopiesAlternatives()
	{
		Map<Integer, Integer> mutable = new HashMap<>();
		mutable.put(1, 2);
		mutable.put(2, 2);
		Rs2TransportItemRequirement requirement = new Rs2TransportItemRequirement(mutable);
		mutable.clear();

		assertEquals(Map.of(1, 2, 2, 2), requirement.getAlternatives());
		assertTrue(requirement.isSatisfiedBy(itemId -> itemId == 2 ? 2 : 0));
		assertFalse(requirement.isSatisfiedBy(itemId -> 1));
		try
		{
			requirement.getAlternatives().put(3, 2);
			fail("owned item alternatives must be immutable");
		}
		catch (UnsupportedOperationException expected)
		{
			// expected
		}
	}

	@Test
	public void routeMetricsDistinguishUnavailableFromZero()
	{
		Rs2RouteMetrics metrics = new Rs2RouteMetrics(
			Rs2RouteMetrics.UNAVAILABLE,
			Rs2RouteMetrics.UNAVAILABLE,
			0L,
			Rs2RouteMetrics.UNAVAILABLE);

		assertFalse(metrics.hasSearchNanos());
		assertEquals(Rs2RouteMetrics.UNAVAILABLE, metrics.getSearchNanos());
		assertFalse(metrics.hasPathCost());
		assertEquals(Rs2RouteMetrics.UNAVAILABLE, metrics.getPathCost());
		assertTrue(metrics.hasNodesChecked());
		assertEquals(0L, metrics.getNodesChecked());
		assertFalse(metrics.hasTransportsChecked());
		try
		{
			new Rs2RouteMetrics(-2L, 0L, 0L, 0L);
			fail("negative metrics other than UNAVAILABLE must be rejected");
		}
		catch (IllegalArgumentException expected)
		{
			// expected
		}
	}

	@SuppressWarnings("unchecked")
	private static PathfinderConfig configWithTransport(WorldPoint origin, Transport transport) throws Exception
	{
		return configWithTransports(origin, Set.of(transport));
	}

	@SuppressWarnings("unchecked")
	private static PathfinderConfig configWithTransports(
		WorldPoint origin, Set<Transport> transports) throws Exception
	{
		return configWithTransportCatalog(Map.of(origin, transports));
	}

	@SuppressWarnings("unchecked")
	private static PathfinderConfig configWithTransportCatalog(
		Map<WorldPoint, Set<Transport>> catalog) throws Exception
	{
		PathfinderConfig pathfinderConfig = new PathfinderConfig(
			SplitFlagMap.fromResources(), catalog,
			Collections.emptyList(), null, null);
		setCalculationCutoff(pathfinderConfig);

		Field transportsField = PathfinderConfig.class.getDeclaredField("transports");
		transportsField.setAccessible(true);
		Map<WorldPoint, Set<Transport>> activeTransports =
			(Map<WorldPoint, Set<Transport>>) transportsField.get(pathfinderConfig);
		activeTransports.putAll(catalog);

		Field packedField = PathfinderConfig.class.getDeclaredField("transportsPacked");
		packedField.setAccessible(true);
		PrimitiveIntHashMap<Set<Transport>> packed =
			(PrimitiveIntHashMap<Set<Transport>>) packedField.get(pathfinderConfig);
		for (Map.Entry<WorldPoint, Set<Transport>> entry : catalog.entrySet())
		{
			packed.put(WorldPointUtil.packWorldPoint(entry.getKey()), entry.getValue());
		}
		return pathfinderConfig;
	}

	private static final class RecordingPathfinderConfig extends PathfinderConfig
	{
		private final java.util.List<Boolean> refreshPolicies = new ArrayList<>();
		private final java.util.List<WorldPoint> refreshTargets = new ArrayList<>();

		private RecordingPathfinderConfig()
		{
			super(SplitFlagMap.fromResources(), new HashMap<>(), Collections.emptyList(), null, null);
		}

		@Override
		public void refresh(WorldPoint target)
		{
			refreshPolicies.add(isUseBankItems());
			refreshTargets.add(target);
		}
	}
}
