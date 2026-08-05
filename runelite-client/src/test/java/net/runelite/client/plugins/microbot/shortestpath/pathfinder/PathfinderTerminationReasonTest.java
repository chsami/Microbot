package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PathfinderTerminationReasonTest
{
	private static final int START = WorldPointUtil.packWorldPoint(new WorldPoint(3200, 3200, 0));
	private static final int TARGET = WorldPointUtil.packWorldPoint(new WorldPoint(3201, 3200, 0));
	private static final int FAR_TARGET = WorldPointUtil.packWorldPoint(new WorldPoint(6000, 3200, 0));

	@BeforeClass
	public static void initializeCollisionExtents()
	{
		// VisitedTiles uses the resource-derived global region extents even when its CollisionMap is mocked.
		SplitFlagMap.fromResources();
	}

	@Test
	public void exactTargetReportsReached()
	{
		Scenario scenario = scenario(10_000L);
		Pathfinder pathfinder = new Pathfinder(scenario.config, START, Collections.singleton(START));

		pathfinder.run();

		assertEquals(PathTerminationReason.TARGET_REACHED, pathfinder.getTerminationReason());
		assertTrue(pathfinder.isDone());
	}

	@Test
	public void drainedFrontierReportsSearchExhausted()
	{
		Scenario scenario = scenario(10_000L);
		when(scenario.map.getNeighbors(any(Node.class), any(VisitedTiles.class),
			eq(scenario.config), anySet())).thenReturn(Collections.emptyList());
		Pathfinder pathfinder = new Pathfinder(scenario.config, START, Collections.singleton(TARGET));

		pathfinder.run();

		assertEquals(PathTerminationReason.SEARCH_EXHAUSTED, pathfinder.getTerminationReason());
		assertTrue(pathfinder.isDone());
	}

	@Test
	public void elapsedCutoffReportsCutoffReached()
	{
		Scenario scenario = scenario(-1L);
		Pathfinder pathfinder = new Pathfinder(scenario.config, START, Collections.singleton(TARGET));

		pathfinder.run();

		assertEquals(PathTerminationReason.CUTOFF_REACHED, pathfinder.getTerminationReason());
		assertTrue(pathfinder.isDone());
	}

	@Test
	public void bidirectionalDrainedFrontiersReportSearchExhausted()
	{
		Scenario scenario = scenario(10_000L);
		when(scenario.map.getNeighbors(any(Node.class), any(VisitedTiles.class),
			eq(scenario.config), anySet())).thenReturn(Collections.emptyList());
		when(scenario.map.getReverseNeighbors(any(Node.class), any(VisitedTiles.class),
			eq(scenario.config), anySet(), anyMap())).thenReturn(Collections.emptyList());
		Pathfinder pathfinder = new Pathfinder(
			scenario.config, START, Collections.singleton(FAR_TARGET));

		pathfinder.run();

		assertEquals(PathTerminationReason.SEARCH_EXHAUSTED, pathfinder.getTerminationReason());
		assertTrue(pathfinder.isDone());
	}

	@Test
	public void cancellationReportsCancelled()
	{
		Scenario scenario = scenario(10_000L);
		Pathfinder pathfinder = new Pathfinder(scenario.config, START, Collections.singleton(TARGET));
		pathfinder.cancel();

		pathfinder.run();

		assertEquals(PathTerminationReason.CANCELLED, pathfinder.getTerminationReason());
		assertFalse(pathfinder.isDone());
	}

	@Test
	public void caughtPlannerExceptionReportsFailed()
	{
		Scenario scenario = scenario(10_000L);
		when(scenario.map.getNeighbors(any(Node.class), any(VisitedTiles.class),
			eq(scenario.config), anySet())).thenThrow(new IllegalStateException("synthetic planner failure"));
		Pathfinder pathfinder = new Pathfinder(scenario.config, START, Collections.singleton(TARGET));

		pathfinder.run();

		assertEquals(PathTerminationReason.FAILED, pathfinder.getTerminationReason());
		assertTrue("the worker stopped even though planning failed", pathfinder.isDone());
	}

	@Test
	public void reverseChainRetainsForwardTransportIdentity()
	{
		WorldPoint start = new WorldPoint(3200, 3200, 0);
		WorldPoint meeting = new WorldPoint(3201, 3200, 0);
		WorldPoint goal = new WorldPoint(3201, 3200, 1);
		Transport stairs = new Transport(
			meeting, goal, "Upper floor", TransportType.TRANSPORT, false,
			"Climb-up", "Staircase", 16671);

		Node forwardStart = new Node(start, null);
		Node forwardMeeting = new Node(meeting, forwardStart);
		Node backwardGoal = new Node(goal, null);
		Node backwardMeeting = new TransportNode(meeting, backwardGoal, 1, stairs);

		java.util.List<PathEdge> edges =
			PathEdge.fromBidirectionalChains(forwardMeeting, backwardMeeting);

		assertEquals(2, edges.size());
		assertFalse(edges.get(0).isTransport());
		assertTrue(edges.get(1).isTransport());
		assertEquals(meeting, edges.get(1).getFrom());
		assertEquals(goal, edges.get(1).getTo());
		assertSame(stairs, edges.get(1).getTransport());
	}

	private static Scenario scenario(long cutoffMillis)
	{
		PathfinderConfig config = mock(PathfinderConfig.class);
		CollisionMap map = mock(CollisionMap.class);
		when(config.getMap()).thenReturn(map);
		when(config.getCalculationCutoffMillis()).thenReturn(cutoffMillis);
		when(config.getTransports()).thenReturn(new ConcurrentHashMap<>());
		return new Scenario(config, map);
	}

	private static final class Scenario
	{
		private final PathfinderConfig config;
		private final CollisionMap map;

		private Scenario(PathfinderConfig config, CollisionMap map)
		{
			this.config = config;
			this.map = map;
		}
	}
}
