package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Rs2PlannerShadowContextTest
{
	@Test
	public void classifiesReplanUndergroundTransportAndResolvedPolicyWithoutCoordinates()
	{
		WorldPoint start = new WorldPoint(2876, 9878, 0);
		WorldPoint target = new WorldPoint(2820, 9882, 0);
		Rs2RoutePolicy policy = policy(true, true);
		Rs2RouteRequest request = Rs2RouteRequest.to(start, target)
			.withPurpose(Rs2RouteRequest.Purpose.BANK_ROUTE_FROM_BANK)
			.withPolicy(policy);
		Rs2TransportEdge transport = new Rs2TransportEdge(
			start,
			target,
			Rs2TransportType.TRANSPORT,
			Rs2TransportExecutor.OBJECT,
			Rs2TerminalTravelMode.UNSUPPORTED,
			"test",
			"Climb",
			"Stairs",
			1,
			1,
			false,
			false,
			true,
			0,
			"Coins",
			30,
			Collections.emptyList(),
			true,
			true,
			true,
			null);
		Rs2RouteResult result = new Rs2RouteResult(
			start,
			Set.of(target),
			List.of(start, target),
			List.of(Rs2RouteStep.transport(start, target, transport)),
			Rs2RouteTermination.TARGET_REACHED,
			new Rs2RouteMetrics(10L, 1L, 2L, 1L, 4L));

		Rs2PlannerShadowContext context = Rs2PlannerShadowContext.from(
			Rs2PlannerShadowContext.Invocation.RECOVERY_REPLAN, true, request, result);

		assertEquals(Rs2PlannerShadowContext.Invocation.RECOVERY_REPLAN,
			context.getInvocation());
		assertTrue(context.getCoverage().contains(
			Rs2PlannerShadowContext.Coverage.RECOVERY_REPLAN));
		assertTrue(context.getCoverage().contains(
			Rs2PlannerShadowContext.Coverage.UNDERGROUND_COORDINATES));
		assertTrue(context.getCoverage().contains(
			Rs2PlannerShadowContext.Coverage.WALKING_ONLY_SELECTED));
		assertTrue(context.getCoverage().contains(
			Rs2PlannerShadowContext.Coverage.USES_TRANSPORT));
		assertTrue(context.getCoverage().contains(
			Rs2PlannerShadowContext.Coverage.MEMBERS_WORLD_POLICY));
		assertTrue(context.getCoverage().contains(
			Rs2PlannerShadowContext.Coverage.SELECTS_MEMBERS_TRANSPORT));
		assertTrue(context.getCoverage().contains(
			Rs2PlannerShadowContext.Coverage.SELECTS_ITEM_GATED_TRANSPORT));
		assertTrue(context.getCoverage().contains(
			Rs2PlannerShadowContext.Coverage.SELECTS_SKILL_GATED_TRANSPORT));
		assertTrue(context.getCoverage().contains(
			Rs2PlannerShadowContext.Coverage.SELECTS_QUEST_GATED_TRANSPORT));
		assertTrue(context.getCoverage().contains(
			Rs2PlannerShadowContext.Coverage.SELECTS_STATE_GATED_TRANSPORT));
		assertTrue(context.getCoverage().contains(
			Rs2PlannerShadowContext.Coverage.SELECTS_NON_ITEM_REQUIREMENT_GATED_TRANSPORT));
		assertTrue(context.getCoverage().contains(
			Rs2PlannerShadowContext.Coverage.BANK_ITEMS_ENABLED));
		assertTrue(context.getCoverage().contains(
			Rs2PlannerShadowContext.Coverage.BANK_ROUTE_FROM_BANK));
		assertTrue(context.getCoverage().contains(
			Rs2PlannerShadowContext.Coverage.BANK_ROUTE_FROM_BANK_SELECTS_ITEM_GATED_TRANSPORT));
		assertTrue(context.getCoverage().contains(
			Rs2PlannerShadowContext.Coverage.LIVE_COLLISION_ENABLED));
		assertTrue(context.getCoverage().contains(
			Rs2PlannerShadowContext.Coverage.LIVE_COLLISION_CONSULTED));
		assertEquals(Set.of(Rs2TransportExecutor.OBJECT), context.getTransportExecutors());
		assertEquals(Set.of(Rs2TransportType.TRANSPORT), context.getTransportTypes());
		assertFalse(context.getCoverage().contains(
			Rs2PlannerShadowContext.Coverage.SURFACE_COORDINATES_ONLY));
	}

	@Test
	public void f2pWalkingRouteDoesNotClaimMembersOrRequirementEvidence()
	{
		WorldPoint start = new WorldPoint(3222, 3218, 0);
		WorldPoint target = new WorldPoint(3223, 3218, 0);
		Rs2RouteRequest request = Rs2RouteRequest.to(start, target)
			.withPolicy(policy(false, false, false));
		Rs2RouteResult result = new Rs2RouteResult(
			start,
			Set.of(target),
			List.of(start, target),
			List.of(Rs2RouteStep.walk(start, target)),
			Rs2RouteTermination.TARGET_REACHED,
			new Rs2RouteMetrics(10L, 1L, 2L, 0L));

		Rs2PlannerShadowContext context = Rs2PlannerShadowContext.from(
			Rs2PlannerShadowContext.Invocation.ACTIVE_ROUTE, false, request, result);

		assertFalse(context.getCoverage().contains(
			Rs2PlannerShadowContext.Coverage.MEMBERS_WORLD_POLICY));
		assertFalse(context.getCoverage().contains(
			Rs2PlannerShadowContext.Coverage.SELECTS_MEMBERS_TRANSPORT));
		assertFalse(context.getCoverage().contains(
			Rs2PlannerShadowContext.Coverage.SELECTS_SKILL_GATED_TRANSPORT));
		assertFalse(context.getCoverage().contains(
			Rs2PlannerShadowContext.Coverage.SELECTS_QUEST_GATED_TRANSPORT));
		assertFalse(context.getCoverage().contains(
			Rs2PlannerShadowContext.Coverage.SELECTS_STATE_GATED_TRANSPORT));
		assertFalse(context.getCoverage().contains(
			Rs2PlannerShadowContext.Coverage.SELECTS_NON_ITEM_REQUIREMENT_GATED_TRANSPORT));
	}

	@Test(expected = UnsupportedOperationException.class)
	public void coverageIsImmutable()
	{
		WorldPoint start = new WorldPoint(3222, 3218, 0);
		WorldPoint target = new WorldPoint(3232, 3218, 0);
		Rs2RouteRequest request = Rs2RouteRequest.to(start, target)
			.withPolicy(policy(false, false));
		Rs2RouteResult result = new Rs2RouteResult(
			start,
			Set.of(target),
			List.of(start, target),
			List.of(Rs2RouteStep.walk(start, target)),
			Rs2RouteTermination.TARGET_REACHED,
			new Rs2RouteMetrics(10L, 10L, 11L, 0L));
		Rs2PlannerShadowContext context = Rs2PlannerShadowContext.from(
			Rs2PlannerShadowContext.Invocation.SYNCHRONOUS_QUERY,
			false,
			request,
			result);

		context.getCoverage().add(Rs2PlannerShadowContext.Coverage.USES_TRANSPORT);
	}

	@Test
	public void equalSemanticRoutesRetainExactShapeDifferenceAsDiagnostic()
	{
		WorldPoint start = new WorldPoint(3222, 3218, 0);
		WorldPoint target = new WorldPoint(3224, 3220, 0);
		WorldPoint localMid = new WorldPoint(3223, 3219, 0);
		WorldPoint shadowMid = new WorldPoint(3223, 3220, 0);
		Rs2RouteRequest request = Rs2RouteRequest.to(start, target)
			.withPolicy(policy(false, false));
		Rs2RouteResult local = walkingResult(start, localMid, target);
		Rs2RouteResult shadow = walkingResult(start, shadowMid, target);
		Rs2PlannerShadowComparison comparison = Rs2PlannerShadowComparison.compare(
			"candidate",
			Rs2PlannerShadowContext.from(
				Rs2PlannerShadowContext.Invocation.SYNCHRONOUS_QUERY,
				false,
				request,
				local),
			local,
			shadow);

		assertEquals(Rs2PlannerShadowComparison.Status.MATCH, comparison.getStatus());
		assertFalse(comparison.isPathMatches());
	}

	private static Rs2RouteResult walkingResult(
		WorldPoint start, WorldPoint middle, WorldPoint target)
	{
		return new Rs2RouteResult(
			start,
			Set.of(target),
			List.of(start, middle, target),
			List.of(Rs2RouteStep.walk(start, middle), Rs2RouteStep.walk(middle, target)),
			Rs2RouteTermination.TARGET_REACHED,
			new Rs2RouteMetrics(10L, 2L, 3L, 0L));
	}

	private static Rs2RoutePolicy policy(boolean bankItems, boolean liveCollision)
	{
		return policy(bankItems, liveCollision, true);
	}

	private static Rs2RoutePolicy policy(
		boolean bankItems, boolean liveCollision, boolean membersWorld)
	{
		return new Rs2RoutePolicy(
			bankItems,
			true,
			false,
			false,
			false,
			membersWorld,
			liveCollision,
			10_000L,
			0,
			Rs2RoutePolicy.TeleportationItemMode.NONE,
			EnumSet.allOf(Rs2TransportType.class),
			Collections.emptySet());
	}
}
