package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.walker.door.DoorAttemptLedger;
import net.runelite.client.plugins.microbot.util.walker.state.WalkerRouteState;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The post-transport window must not survive into the next walk.
 *
 * <p>While the window is armed the walker deliberately runs degraded: it skips the raw scene scan,
 * skips the per-segment door / rockfall / transport handlers, disables ranged door dispatch for the
 * whole pass, and bypasses the off-path recalc. That is correct for the seconds after a landing, and
 * badly wrong for a walk that has only just started — a fresh walk would ignore the door in front of
 * it and never replan when it drifted off route.
 *
 * <p>The leak was subtle because walk-session start <em>did</em> clear the transport context — but
 * only the three location fields, not {@code lastTransportHandledAtMs}, which is the field every
 * window check actually reads. So the window stayed armed for its full 15 seconds while the
 * destination it describes was already null.
 */
public class WalkSessionStateResetTest
{
	private WalkerRouteState routeState;

	@Before
	public void setUp()
	{
		routeState = Rs2Walker.routeStateForTesting();
		routeState.clearRecentTransportContext();
	}

	/**
	 * The regression itself. A walk that ends without clearing its target — an exception, the tail
	 * cap tripping, or an external cancellation — leaves the window armed; starting the next walk has
	 * to disarm it.
	 */
	@Test
	public void startingAWalkEndsAnyPostTransportWindowLeftByThePreviousOne()
	{
		routeState.lastTransportHandledAtMs = System.currentTimeMillis();
		routeState.lastTransportOriginLocation = new WorldPoint(3200, 3200, 0);
		routeState.lastTransportDestinationLocation = new WorldPoint(3200, 3210, 1);

		Rs2Walker.resetWalkSessionState();

		assertEquals("the post-transport window must be disarmed at walk start; every window check "
				+ "reads this timestamp, so leaving it set suppresses the new walk's handlers",
			0L, routeState.lastTransportHandledAtMs);
		assertNull(routeState.lastTransportOriginLocation);
		assertNull(routeState.lastTransportDestinationLocation);
	}

	/**
	 * Clearing the locations alone is what the bug was. Pin the timestamp explicitly so a future
	 * edit cannot reintroduce a partial clear that looks right and does nothing.
	 */
	@Test
	public void clearingTheTransportContextClearsTheTimestampNotJustTheLocations()
	{
		routeState.lastTransportHandledAtMs = 1_234_567L;
		routeState.lastTransportOriginLocation = new WorldPoint(1, 2, 0);
		routeState.lastTransportDestinationLocation = new WorldPoint(3, 4, 0);

		routeState.clearRecentTransportContext();

		assertEquals(0L, routeState.lastTransportHandledAtMs);
		assertNull(routeState.lastTransportOriginLocation);
		assertNull(routeState.lastTransportDestinationLocation);
	}

	/**
	 * Walk start also withdraws the previous walk's door claim, for the same staleness reason —
	 * but deliberately NOT the per-edge cooldowns: hammering one door across two walks is still
	 * hammering. The two lifetimes used to live in two stores; the ledger keeps both facts and
	 * this test pins that the reset touches only the claim.
	 */
	@Test
	public void startingAWalkDropsThePreviousWalksDoorClaimButKeepsTheEdgeCooldown()
	{
		DoorAttemptLedger ledger = Rs2WalkerDoors.doorAttemptLedgerForTesting();
		WorldPoint from = new WorldPoint(3010, 3204, 0);
		WorldPoint to = new WorldPoint(3011, 3204, 0);
		long now = System.currentTimeMillis();
		ledger.markAttempt(null, from, to, now);

		Rs2Walker.resetWalkSessionState();

		assertNull("the latest claim belongs to the previous walk", ledger.latestAttempt());
		assertTrue("the anti-hammer cooldown must survive the walk boundary",
			ledger.shouldThrottleAttempt(null, from, to, 2_500, now + 100));
	}

	@Test
	public void startingAWalkResetsDoorClaimAndEffectiveGoalState()
	{
		routeState.walledDoorEdgeFrom = new WorldPoint(2907, 3544, 0);
		routeState.walledDoorEdgeTo = new WorldPoint(2907, 3543, 0);
		routeState.walledDoorEdgeAtMs = 456L;
		routeState.requestedGoal = new WorldPoint(2907, 3539, 0);
		routeState.sealedRimRetargets = 2;

		Rs2Walker.resetWalkSessionState();

		assertNull("a previous walk's door edge must not steer this walk's recovery",
			routeState.walledDoorEdgeFrom);
		assertNull(routeState.walledDoorEdgeTo);
		assertEquals(0L, routeState.walledDoorEdgeAtMs);
		assertNull("requested goal belongs to the previous walk", routeState.requestedGoal);
		assertEquals("rim-retarget budget belongs to the walk that spent it",
			0, routeState.sealedRimRetargets);
	}

	/** Route progress belongs to the route that made it. */
	@Test
	public void startingAWalkResetsRouteProgress()
	{
		routeState.routeProgressIdx = 42;
		routeState.routeProgressAdvancedAtMs = System.currentTimeMillis();
		routeState.stagnationReplansSpent = 2;

		Rs2Walker.resetWalkSessionState();

		assertEquals(-1, routeState.routeProgressIdx);
		assertEquals(0L, routeState.routeProgressAdvancedAtMs);
		assertEquals("a fresh walk owes a fresh stagnation budget", 0, routeState.stagnationReplansSpent);
	}
}
