package net.runelite.client.plugins.microbot.util.walker.segment;

import net.runelite.client.plugins.microbot.util.walker.segment.SegmentGate.SegmentAction;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Decision table for whether one route segment's obstacle handlers run.
 *
 * <p>These conditions were inline boolean soup in {@code processWalk}, and the interaction between
 * them — a skipped segment silently withdrawing the right to click a door at range — is what
 * produced the Falador U-turn.
 */
public class SegmentGateTest
{
	/** Steady state, nothing special: examine the segment. */
	private static SegmentAction decide(boolean recentTransportWindow,
										boolean upcomingNearbyTransport,
										boolean recentDoorAttemptNearSegment,
										boolean doorSettling,
										boolean recoveryInFlight,
										boolean tileReachable,
										boolean startupBeforeFirstClick,
										boolean startupTransportStep,
										int segmentIdx,
										int routeStartIdx)
	{
		return SegmentGate.decide(recentTransportWindow, upcomingNearbyTransport,
			recentDoorAttemptNearSegment, doorSettling, recoveryInFlight, tileReachable,
			startupBeforeFirstClick, startupTransportStep, segmentIdx, routeStartIdx);
	}

	@Test
	public void steadyStateRunsTheHandlers()
	{
		assertEquals(SegmentAction.RUN,
			decide(false, false, false, false, false, true, false, false, 5, 5));
	}

	@Test
	public void postTransportWindowSkipsWhenNoTransportIsComingUp()
	{
		assertEquals(SegmentAction.SKIP_POST_TRANSPORT_WINDOW,
			decide(true, false, false, false, false, true, false, false, 5, 5));
	}

	/** The window must not hide the transport it is a window for. */
	@Test
	public void aPlannedTransportNearbyOverridesThePostTransportSkip()
	{
		assertEquals(SegmentAction.RUN,
			decide(true, true, false, false, false, true, false, false, 5, 5));
	}

	/** An unreachable segment tile is the case the handlers exist for, so it is never skipped. */
	@Test
	public void anUnreachableSegmentIsNeverSkippedByTheTransportWindow()
	{
		assertEquals(SegmentAction.RUN,
			decide(true, false, false, false, false, false, false, false, 5, 5));
	}

	@Test
	public void doorWorkInFlightOverridesThePostTransportSkip()
	{
		assertEquals("a recent door attempt near this segment must still be examined",
			SegmentAction.RUN, decide(true, false, true, false, false, true, false, false, 5, 5));
		assertEquals("a settling door must still be examined",
			SegmentAction.RUN, decide(true, false, false, true, false, true, false, false, 5, 5));
		assertEquals("recovery movement in flight must still be examined",
			SegmentAction.RUN, decide(true, false, false, false, true, true, false, false, 5, 5));
	}

	@Test
	public void startupSkipsSegmentsUntilTheFirstMovementClick()
	{
		assertEquals(SegmentAction.SKIP_STARTUP_PRECLICK,
			decide(false, false, false, false, false, true, true, false, 5, 5));
	}

	/** A nearby or ranged first object transport is taken at startup rather than approached first. */
	@Test
	public void aStartupTransportStepIsNotSkippedAtStartup()
	{
		assertEquals(SegmentAction.RUN,
			decide(false, false, false, false, false, true, true, true, 5, 5));
	}

	@Test
	public void startupSkipDoesNotApplyBehindTheRouteStartOrOutsideStartup()
	{
		assertEquals("segments behind the route start are not startup-skipped",
			SegmentAction.RUN, decide(false, false, false, false, false, true, true, false, 3, 5));
		assertEquals("a negative route start means we do not know where the route begins",
			SegmentAction.RUN, decide(false, false, false, false, false, true, true, false, 5, -1));
		assertEquals("not in startup",
			SegmentAction.RUN, decide(false, false, false, false, false, true, false, false, 8, 5));
	}

	@Test
	public void startupSkipYieldsToDoorWorkInFlight()
	{
		assertEquals(SegmentAction.RUN,
			decide(false, false, true, false, false, true, true, false, 8, 5));
	}

	/** Both apply: the post-transport reason wins, matching the original reason ternary. */
	@Test
	public void postTransportReasonWinsWhenBothSkipsApply()
	{
		assertEquals(SegmentAction.SKIP_POST_TRANSPORT_WINDOW,
			decide(true, false, false, false, false, true, true, false, 5, 5));
	}

	/** Log consumers key off these strings; they must not drift. */
	@Test
	public void wireReasonsAreStable()
	{
		assertEquals("no_nearby_planned_transport",
			SegmentAction.SKIP_POST_TRANSPORT_WINDOW.wireReason());
		assertEquals("startup_before_first_click", SegmentAction.SKIP_STARTUP_PRECLICK.wireReason());
		assertFalse(SegmentAction.RUN.isSkip());
		assertTrue(SegmentAction.SKIP_POST_TRANSPORT_WINDOW.isSkip());
		assertTrue(SegmentAction.SKIP_STARTUP_PRECLICK.isSkip());
	}

	/**
	 * The Falador invariant. A skipped segment was never examined, so the first segment that DOES run
	 * is not the nearest unresolved obstacle just because it is the first one handled — and only the
	 * nearest may be clicked at range.
	 */
	@Test
	public void aSkippedSegmentWithdrawsTheRightToClickADoorAtRange()
	{
		assertTrue("first handler this pass, nothing skipped before it",
			SegmentGate.mayDispatchDoorAtRange(false, false));
		assertFalse("an earlier segment was skipped and never examined",
			SegmentGate.mayDispatchDoorAtRange(false, true));
		assertFalse("something already handled this pass, so this is not the nearest",
			SegmentGate.mayDispatchDoorAtRange(true, false));
		assertFalse(SegmentGate.mayDispatchDoorAtRange(true, true));
	}
}
