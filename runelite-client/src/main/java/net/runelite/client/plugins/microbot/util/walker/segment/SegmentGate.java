package net.runelite.client.plugins.microbot.util.walker.segment;

/**
 * Whether the obstacle handlers run for one route segment, and whether a door on it may be clicked
 * from range.
 *
 * <p>Two independent reasons skip a segment — the window after a transport, and startup before the
 * first movement click — and both were computed inline as boolean soup with the log reason derived
 * from a ternary. The pair matters more than it looks: a skipped segment was never <em>examined</em>,
 * so an obstacle on it is neither resolved nor ruled out, and that is precisely what makes reaching
 * past it dangerous.
 *
 * <p>Pure and fully injected, so the interaction can be pinned in a decision table rather than
 * rediscovered at Falador.
 */
public final class SegmentGate
{
	private SegmentGate()
	{
	}

	public enum SegmentAction
	{
		/** Examine this segment: run the door / blocker / rockfall / transport handlers. */
		RUN("run"),
		/**
		 * Inside the post-transport window with no planned transport nearby. The scene has just
		 * changed under us and the handlers would thrash against a route we are about to re-derive.
		 */
		SKIP_POST_TRANSPORT_WINDOW("no_nearby_planned_transport"),
		/**
		 * Startup, before the first movement click. Broad handlers here delay the first click for
		 * every ordinary segment; a visible first object transport is an explicit exception.
		 */
		SKIP_STARTUP_PRECLICK("startup_before_first_click");

		private final String wireReason;

		SegmentAction(String wireReason)
		{
			this.wireReason = wireReason;
		}

		/** The exact reason string this decision has always been logged as. */
		public String wireReason()
		{
			return wireReason;
		}

		public boolean isSkip()
		{
			return this != RUN;
		}
	}

	/**
	 * Post-transport skip wins over the startup skip when both apply, matching the original
	 * {@code skipPostTransport ? … : …} reason ternary.
	 *
	 * @param tileReachable whether the segment tile is reachable from the player right now; an
	 *                      unreachable tile is never skipped, because that is the case the handlers
	 *                      exist for
	 */
	public static SegmentAction decide(boolean recentTransportWindow,
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
		if (recentTransportWindow
			&& !upcomingNearbyTransport
			&& !recentDoorAttemptNearSegment
			&& !doorSettling
			&& !recoveryInFlight
			&& tileReachable)
		{
			return SegmentAction.SKIP_POST_TRANSPORT_WINDOW;
		}
		if (!startupTransportStep
			&& skipStartupPreclick(startupBeforeFirstClick, segmentIdx, routeStartIdx,
			recentDoorAttemptNearSegment, doorSettling, recoveryInFlight))
		{
			return SegmentAction.SKIP_STARTUP_PRECLICK;
		}
		return SegmentAction.RUN;
	}

	static boolean skipStartupPreclick(boolean startupBeforeFirstClick,
									   int segmentIdx,
									   int routeStartIdx,
									   boolean recentDoorAttemptNearSegment,
									   boolean doorSettling,
									   boolean recoveryInFlight)
	{
		if (!startupBeforeFirstClick || routeStartIdx < 0 || segmentIdx < routeStartIdx)
		{
			return false;
		}
		return !recentDoorAttemptNearSegment && !doorSettling && !recoveryInFlight;
	}

	/**
	 * Whether a door on this segment may be clicked from range.
	 *
	 * <p>"First handler to run this pass" is NOT the same as "nearest unresolved obstacle on the
	 * route". A segment that was SKIPPED was never examined, so an obstacle on it is neither resolved
	 * nor ruled out, and reaching past it is exactly the failure that ranged dispatch must avoid.
	 *
	 * <p>Measured at Falador: segments 11 and 12 skipped with {@code no_nearby_planned_transport},
	 * then the door at (2985,3341) clicked from range while the door at (2981,3340) was still shut
	 * between us and it. The server began routing AROUND the building, dragging the player south to
	 * (2960,3330), and the traversal wait it could never satisfy timed out. Ten seconds and a U-turn.
	 */
	public static boolean mayDispatchDoorAtRange(boolean handlersAlreadyRanThisPass,
												 boolean anySegmentSkippedThisPass)
	{
		return !handlersAlreadyRanThisPass && !anySegmentSkippedThisPass;
	}
}
