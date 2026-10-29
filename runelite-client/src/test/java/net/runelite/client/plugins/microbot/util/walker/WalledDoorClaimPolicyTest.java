package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WalledDoorClaimPolicyTest {
    private static final long NOW = 10_000L;
    private static final WorldPoint FROM = new WorldPoint(1770, 3589, 0);
    private static final WorldPoint TO = new WorldPoint(1770, 3590, 0);

    /** Ardougne timber house, plane 1: a claim on a wall edge that has no door, held by a door beside it. */
    @Test
    public void aClaimOnAnEdgeWithoutADoorIsDroppedAfterRepeatedFruitlessRuns() {
        WalledDoorClaimPolicy.UnfulfilledClaims claims = new WalledDoorClaimPolicy.UnfulfilledClaims();
        WorldPoint from = new WorldPoint(2617, 3294, 1);
        WorldPoint to = new WorldPoint(2617, 3295, 1);
        assertFalse(drops(claims, from, to, 0, false));
        assertFalse(drops(claims, from, to, 2_000, false));
        assertTrue(drops(claims, from, to, 5_000, false));
    }

    @Test
    public void aThrottledHandlerIsNotMistakenForOneThatFoundNothing() {
        WalledDoorClaimPolicy.UnfulfilledClaims claims = new WalledDoorClaimPolicy.UnfulfilledClaims();
        WorldPoint from = new WorldPoint(2617, 3294, 1);
        WorldPoint to = new WorldPoint(2617, 3295, 1);
        // three runs within a second: too soon to tell
        assertFalse(drops(claims, from, to, 0, false));
        assertFalse(drops(claims, from, to, 300, false));
        assertFalse(drops(claims, from, to, 600, false));
    }

    private static boolean drops(WalledDoorClaimPolicy.UnfulfilledClaims claims, WorldPoint from, WorldPoint to,
                                 long nowMs, boolean doorGeometry) {
        int runs = claims.note(from, to, nowMs);
        return WalledDoorClaimPolicy.shouldDropUnfulfilledClaim(runs, claims.spanMs(nowMs), doorGeometry);
    }

    @Test
    public void aRealDoorOnTheEdgeKeepsItsClaim() {
        WalledDoorClaimPolicy.UnfulfilledClaims claims = new WalledDoorClaimPolicy.UnfulfilledClaims();
        boolean dropped = false;
        for (int i = 0; i < 6; i++) {
            dropped |= drops(claims, FROM, TO, i * 3_000L, true);
        }
        assertFalse("a door on the exact edge, open or shut, is never given up on", dropped);
    }

    @Test
    public void anotherEdgeOrAResetRestartsTheCount() {
        WalledDoorClaimPolicy.UnfulfilledClaims claims = new WalledDoorClaimPolicy.UnfulfilledClaims();
        claims.note(FROM, TO, 0);
        claims.note(FROM, TO, 1_000);
        assertEquals(1, claims.note(TO, new WorldPoint(1771, 3589, 0), 2_000));
        claims.note(FROM, TO, 3_000);
        claims.reset();
        assertEquals(1, claims.note(FROM, TO, 4_000));
        assertEquals(0, claims.spanMs(4_000));
    }

    @Test
    public void adjacentClaimDispatchesDoorHandlerInsteadOfReplan() {
        assertEquals(WalledDoorClaimPolicy.Decision.HANDLE_AT_EDGE,
                decide(new WorldPoint(1771, 3589, 0), false, true));
    }

    @Test
    public void distantReachableNearSideRequestsApproach() {
        assertEquals(WalledDoorClaimPolicy.Decision.APPROACH,
                decide(new WorldPoint(1778, 3589, 0), false, true));
    }

    @Test
    public void movingClaimRetainsOwnershipWithoutAnotherClick() {
        assertEquals(WalledDoorClaimPolicy.Decision.ACTION_IN_FLIGHT,
                decide(new WorldPoint(1778, 3589, 0), true, true));
    }

    @Test
    public void attemptedDoorCanOwnRecoveryLongerThanAnUndispatchedWalledClaim() {
        assertEquals(WalledDoorClaimPolicy.Decision.HANDLE_AT_EDGE,
                WalledDoorClaimPolicy.decide(FROM, TO,
                        NOW - WalledDoorClaimPolicy.FRESH_MS - 1, NOW,
                        new WorldPoint(1771, 3589, 0), false, true, 10_000L));
    }

    @Test
    public void crossedAndExpiredClaimsReleaseOwnership() {
        assertEquals(WalledDoorClaimPolicy.Decision.CROSSED,
                decide(new WorldPoint(1770, 3590, 0), false, true));
        assertEquals(WalledDoorClaimPolicy.Decision.EXPIRED,
                WalledDoorClaimPolicy.decide(FROM, TO,
                        NOW - WalledDoorClaimPolicy.FRESH_MS - 1, NOW,
                        new WorldPoint(1771, 3589, 0), false, true));
    }

    @Test
    public void beingFarBeyondTheAxisDoesNotPretendTheDoorWasCrossed() {
        assertEquals(WalledDoorClaimPolicy.Decision.APPROACH,
                decide(new WorldPoint(1780, 3593, 0), false, true));
    }

    @Test
    public void malformedOrUnreachableClaimsAreInvalid() {
        assertEquals(WalledDoorClaimPolicy.Decision.INVALID,
                decide(new WorldPoint(1778, 3589, 0), false, false));
        assertEquals(WalledDoorClaimPolicy.Decision.INVALID,
                WalledDoorClaimPolicy.decide(FROM, new WorldPoint(1770, 3591, 0),
                        NOW - 1, NOW, new WorldPoint(1771, 3589, 0), false, true));
    }

    @Test
    public void traversalEnvelopeIncludesDoorAndFirstLandingStepsOnly() {
        WorldPoint doorFrom = new WorldPoint(2907, 3544, 0);
        WorldPoint doorTo = new WorldPoint(2907, 3543, 0);
        assertTrue(WalledDoorClaimPolicy.ownsTraversalEdge(doorFrom, doorTo, doorFrom, doorTo));
        assertTrue(WalledDoorClaimPolicy.ownsTraversalEdge(doorFrom, doorTo, doorTo, doorFrom));
        assertTrue(WalledDoorClaimPolicy.ownsTraversalEdge(
                doorFrom, doorTo, doorTo, new WorldPoint(2906, 3543, 0)));
        assertTrue(WalledDoorClaimPolicy.ownsTraversalEdge(
                doorFrom, doorTo, doorTo, new WorldPoint(2906, 3542, 0)));
        assertFalse(WalledDoorClaimPolicy.ownsTraversalEdge(doorFrom, doorTo,
                new WorldPoint(2906, 3544, 0), new WorldPoint(2906, 3543, 0)));
        assertFalse(WalledDoorClaimPolicy.ownsTraversalEdge(doorFrom, doorTo,
                new WorldPoint(2907, 3543, 1), new WorldPoint(2906, 3542, 1)));
    }

    @Test
    public void exactIdleClaimMayTraverseWhenLiveEdgeIsOpen() {
        assertTrue(WalledDoorClaimPolicy.shouldTraverseOpenEdge(true, true, false, false));
    }

    @Test
    public void openEdgeTraversalRequiresExactClaimAndNoActionInFlight() {
        assertFalse(WalledDoorClaimPolicy.shouldTraverseOpenEdge(false, true, false, false));
        assertFalse(WalledDoorClaimPolicy.shouldTraverseOpenEdge(true, false, false, false));
        assertFalse(WalledDoorClaimPolicy.shouldTraverseOpenEdge(true, true, true, false));
        assertFalse(WalledDoorClaimPolicy.shouldTraverseOpenEdge(true, true, false, true));
    }

    private static WalledDoorClaimPolicy.Decision decide(WorldPoint player,
                                                         boolean moving,
                                                         boolean reachable) {
        return WalledDoorClaimPolicy.decide(FROM, TO, NOW - 1, NOW, player, moving, reachable);
    }
}
