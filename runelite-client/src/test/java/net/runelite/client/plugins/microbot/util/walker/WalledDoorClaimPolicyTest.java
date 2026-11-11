package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WalledDoorClaimPolicyTest {
    private static final long NOW = 100_000L;
    private static final WorldPoint FROM = new WorldPoint(1770, 3589, 0);
    private static final WorldPoint TO = new WorldPoint(1770, 3590, 0);

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

    @Test
    public void refreshingTheSameEdgeKeepsTheOriginalClaimStart() {
        WorldPoint wallFrom = new WorldPoint(2656, 3292, 0);
        WorldPoint wallTo = new WorldPoint(2656, 3291, 0);
        long since = NOW - 4_000L;
        assertEquals(since, WalledDoorClaimPolicy.claimSinceMs(wallFrom, wallTo, NOW - 500L, since,
                wallFrom, wallTo, NOW));
    }

    @Test
    public void aNewEdgeOrALapsedClaimStartsANewClaimWindow() {
        WorldPoint wallFrom = new WorldPoint(2656, 3292, 0);
        WorldPoint wallTo = new WorldPoint(2656, 3291, 0);
        long since = NOW - 4_000L;
        assertEquals(NOW, WalledDoorClaimPolicy.claimSinceMs(FROM, TO, NOW - 500L, since,
                wallFrom, wallTo, NOW));
        assertEquals(NOW, WalledDoorClaimPolicy.claimSinceMs(wallFrom, wallTo,
                NOW - WalledDoorClaimPolicy.ADJACENT_REFUSAL_GAP_MS - 1, since, wallFrom, wallTo, NOW));
        assertEquals(NOW, WalledDoorClaimPolicy.claimSinceMs(null, null, 0L, 0L,
                wallFrom, wallTo, NOW));
    }

    @Test
    public void adjacentClaimKeptFreshByTheSameRefusalIsSpentAfterOneWindow() {
        WorldPoint wallFrom = new WorldPoint(2656, 3292, 0);
        WorldPoint wallTo = new WorldPoint(2656, 3291, 0);
        long longAgo = NOW - WalledDoorClaimPolicy.FRESH_MS - 1;
        assertTrue(WalledDoorClaimPolicy.isAdjacentClaimSpent(wallFrom, wallTo, NOW - 500L, longAgo,
                wallFrom, wallTo, NOW));
    }

    @Test
    public void adjacentClaimIsNotSpentWithinItsWindowOrForAnotherEdge() {
        WorldPoint wallFrom = new WorldPoint(2656, 3292, 0);
        WorldPoint wallTo = new WorldPoint(2656, 3291, 0);
        long longAgo = NOW - WalledDoorClaimPolicy.FRESH_MS - 1;
        assertFalse(WalledDoorClaimPolicy.isAdjacentClaimSpent(wallFrom, wallTo, NOW - 500L, NOW - 2_000L,
                wallFrom, wallTo, NOW));
        assertFalse(WalledDoorClaimPolicy.isAdjacentClaimSpent(FROM, TO, NOW - 500L, longAgo,
                wallFrom, wallTo, NOW));
        long lapsed = NOW - WalledDoorClaimPolicy.ADJACENT_REFUSAL_GAP_MS - 1;
        assertFalse(WalledDoorClaimPolicy.isAdjacentClaimSpent(wallFrom, wallTo,
                lapsed, lapsed, wallFrom, wallTo, NOW));
        assertFalse(WalledDoorClaimPolicy.isAdjacentClaimSpent(null, null, 0L, 0L,
                wallFrom, wallTo, NOW));
    }

    @Test
    public void refusalsSlowerThanTheClaimWindowStillSpendTheAdjacentClaim() {
        WorldPoint wallFrom = new WorldPoint(2656, 3292, 0);
        WorldPoint wallTo = new WorldPoint(2656, 3291, 0);
        long firstRefusal = NOW - WalledDoorClaimPolicy.FRESH_MS - 1_000L;
        long since = WalledDoorClaimPolicy.claimSinceMs(null, null, 0L, 0L, wallFrom, wallTo, firstRefusal);
        assertTrue(WalledDoorClaimPolicy.isAdjacentClaimSpent(wallFrom, wallTo, firstRefusal, since,
                wallFrom, wallTo, NOW));
    }

    @Test
    public void onlyTheEdgeRefusedBesideADoorIsAnAdjacentClaim() {
        WorldPoint wallFrom = new WorldPoint(2656, 3292, 0);
        WorldPoint wallTo = new WorldPoint(2656, 3291, 0);
        assertTrue(WalledDoorClaimPolicy.isAdjacentClaimEdge(wallFrom, wallTo, wallFrom, wallTo));
        assertFalse(WalledDoorClaimPolicy.isAdjacentClaimEdge(wallFrom, wallTo, wallTo, wallFrom));
        assertFalse(WalledDoorClaimPolicy.isAdjacentClaimEdge(wallFrom, wallTo, FROM, TO));
        assertFalse(WalledDoorClaimPolicy.isAdjacentClaimEdge(null, null, wallFrom, wallTo));
        assertFalse(WalledDoorClaimPolicy.isAdjacentClaimEdge(wallFrom, wallTo, null, null));
    }

    @Test
    public void adjacentDoorHandlerRunsWhileTheClaimIsHeldAndNoActionIsInFlight() {
        assertTrue(WalledDoorClaimPolicy.dispatchesAdjacentDoorHandler(
                WalledDoorClaimPolicy.Decision.HANDLE_AT_EDGE));
        assertTrue(WalledDoorClaimPolicy.dispatchesAdjacentDoorHandler(
                WalledDoorClaimPolicy.Decision.APPROACH));
        assertFalse(WalledDoorClaimPolicy.dispatchesAdjacentDoorHandler(
                WalledDoorClaimPolicy.Decision.ACTION_IN_FLIGHT));
        assertFalse(WalledDoorClaimPolicy.dispatchesAdjacentDoorHandler(
                WalledDoorClaimPolicy.Decision.CROSSED));
        assertFalse(WalledDoorClaimPolicy.dispatchesAdjacentDoorHandler(
                WalledDoorClaimPolicy.Decision.EXPIRED));
        assertFalse(WalledDoorClaimPolicy.dispatchesAdjacentDoorHandler(
                WalledDoorClaimPolicy.Decision.INVALID));
        assertFalse(WalledDoorClaimPolicy.dispatchesAdjacentDoorHandler(
                WalledDoorClaimPolicy.Decision.NONE));
    }

    @Test
    public void aStrikeOutExpiresSoTheDoorIsTriedAgainLater() {
        assertTrue(WalledDoorClaimPolicy.isAdjacentStrikeOutActive(NOW - 1_000L, NOW));
        assertTrue(WalledDoorClaimPolicy.isAdjacentStrikeOutActive(
                NOW - WalledDoorClaimPolicy.ADJACENT_STRIKE_OUT_MS, NOW));
        assertFalse(WalledDoorClaimPolicy.isAdjacentStrikeOutActive(
                NOW - WalledDoorClaimPolicy.ADJACENT_STRIKE_OUT_MS - 1L, NOW));
        assertFalse(WalledDoorClaimPolicy.isAdjacentStrikeOutActive(null, NOW));
    }

    @Test
    public void onlyADoorTheHandlerCanActOnHoldsTheEdgeAndOnlyWhileUnspentAndNotStruckOut() {
        assertTrue(WalledDoorClaimPolicy.holdsAdjacentClaim(
                WalledDoorClaimPolicy.AdjacentDoor.ACTIONABLE, false, false));
        assertFalse(WalledDoorClaimPolicy.holdsAdjacentClaim(
                WalledDoorClaimPolicy.AdjacentDoor.ACTIONABLE, true, false));
        assertFalse(WalledDoorClaimPolicy.holdsAdjacentClaim(
                WalledDoorClaimPolicy.AdjacentDoor.ACTIONABLE, false, true));
        assertFalse(WalledDoorClaimPolicy.holdsAdjacentClaim(
                WalledDoorClaimPolicy.AdjacentDoor.UNUSABLE, false, false));
        assertFalse(WalledDoorClaimPolicy.holdsAdjacentClaim(
                WalledDoorClaimPolicy.AdjacentDoor.NONE, false, false));
    }

    @Test
    public void anEdgeIsStruckOutOnlyAfterTheHandlerTriedItsDoorAndTheEdgeStayedWalled() {
        assertTrue(WalledDoorClaimPolicy.strikesOutAdjacentDoor(
                WalledDoorClaimPolicy.AdjacentDoor.ACTIONABLE, false, true, true));
        assertFalse("a claim spent while the player was still walking up never tried the door",
                WalledDoorClaimPolicy.strikesOutAdjacentDoor(
                        WalledDoorClaimPolicy.AdjacentDoor.ACTIONABLE, false, true, false));
        assertFalse(WalledDoorClaimPolicy.strikesOutAdjacentDoor(
                WalledDoorClaimPolicy.AdjacentDoor.ACTIONABLE, false, false, true));
        assertFalse("an active strike-out is not renewed by the walks it skips",
                WalledDoorClaimPolicy.strikesOutAdjacentDoor(
                        WalledDoorClaimPolicy.AdjacentDoor.ACTIONABLE, true, true, true));
        assertTrue("a tried door that opened and left the edge walled strikes out before the window is spent",
                WalledDoorClaimPolicy.strikesOutAdjacentDoor(
                        WalledDoorClaimPolicy.AdjacentDoor.UNUSABLE, false, false, true));
        assertTrue(WalledDoorClaimPolicy.strikesOutAdjacentDoor(
                WalledDoorClaimPolicy.AdjacentDoor.NONE, false, false, true));
        assertFalse("a door that was never tried does not strike the edge out",
                WalledDoorClaimPolicy.strikesOutAdjacentDoor(
                        WalledDoorClaimPolicy.AdjacentDoor.UNUSABLE, false, false, false));
        assertFalse(WalledDoorClaimPolicy.strikesOutAdjacentDoor(
                WalledDoorClaimPolicy.AdjacentDoor.UNUSABLE, true, false, true));
    }

    private static WalledDoorClaimPolicy.Decision decide(WorldPoint player,
                                                         boolean moving,
                                                         boolean reachable) {
        return WalledDoorClaimPolicy.decide(FROM, TO, NOW - 1, NOW, player, moving, reachable);
    }
}
