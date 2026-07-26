package net.runelite.client.plugins.microbot.util.walker;
import net.runelite.client.plugins.microbot.util.walker.geometry.WalkerPathGeometry;
import net.runelite.client.plugins.microbot.util.walker.obstacle.Rs2ObstacleHandler;
import net.runelite.client.plugins.microbot.util.walker.recovery.RouteRecovery;
import net.runelite.client.plugins.microbot.util.walker.door.Rs2DoorGeometry;

import net.runelite.api.WallObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests covering the pure-logic walker helpers extracted during the Tier-3
 * robustness sprint: sidestep-recovery ranking (#15), minimap forward-scan (#21),
 * quest-lock dialogue heuristic (#19), and UNREACHABLE telemetry (#22).
 *
 * The main walker is static-heavy and deeply coupled to the RuneLite client — the
 * integration contract is covered by {@link Rs2WalkerIntegrationTest}. These tests
 * pin the pieces that CAN run without a live client so regressions in the
 * refactored code paths get caught by {@code runUnitTests}.
 */
public class Rs2WalkerUnitTest {

    @Before
    public void resetTelemetry() {
        Rs2Walker.clearWalkerDedupeForTesting();
        Rs2Walker.Telemetry.reset();
        Rs2Walker.sessionBlacklistedDoors.clear();
    }

    @After
    public void tearDown() {
        Rs2Walker.clearWalkerDedupeForTesting();
        Rs2Walker.Telemetry.reset();
        Rs2Walker.sessionBlacklistedDoors.clear();
    }

    @Test
    public void adjacentTransportSuppression_onlyAdjacentSamePlaneTransports() {
        Transport door = new Transport(
                new WorldPoint(3123, 3360, 0),
                new WorldPoint(3123, 3361, 0),
                "Door",
                TransportType.TRANSPORT,
                false,
                "Open",
                "Door",
                136);

        assertEquals(new HashSet<>(Arrays.asList(
                        new WorldPoint(3123, 3360, 0),
                        new WorldPoint(3123, 3361, 0))),
                Rs2Walker.adjacentSamePlaneTransportSuppressionPoints(door, null));
    }

    /**
     * Agility shortcuts catalogued as two opposing adjacent entries must be suppressible like doors.
     * Observed live near (3150..3151, 3363): the walker crossed the shortcut, the strict landing
     * check failed because it landed a tile off the catalogued destination, so suppression was
     * skipped — leaving the inverse entry immediately eligible. It took the same shortcut straight
     * back and could not recover. Suppression is deliberately type-agnostic; only adjacency and
     * plane matter.
     */
    @Test
    public void adjacentTransportSuppression_coversAgilityShortcuts() {
        Transport shortcut = new Transport(
                new WorldPoint(3151, 3363, 0),
                new WorldPoint(3150, 3363, 0),
                "Shortcut",
                TransportType.AGILITY_SHORTCUT,
                false,
                "Climb-over",
                "Stile",
                0);

        assertEquals("An adjacent same-plane agility shortcut must yield both tiles for suppression",
                new HashSet<>(Arrays.asList(
                        new WorldPoint(3151, 3363, 0),
                        new WorldPoint(3150, 3363, 0))),
                Rs2Walker.adjacentSamePlaneTransportSuppressionPoints(shortcut, null));
    }

    @Test
    public void adjacentTransportSuppression_ignoresNonAdjacentTransports() {
        Transport ladder = new Transport(
                new WorldPoint(3092, 3361, 0),
                new WorldPoint(3117, 9753, 0),
                "Ladder",
                TransportType.TRANSPORT,
                false,
                "Climb-down",
                "Ladder",
                133);

        assertTrue(Rs2Walker.adjacentSamePlaneTransportSuppressionPoints(ladder, null).isEmpty());
    }

    @Test
    public void shouldRecalculatePathAfterTransport_includesOriginlessTeleport() {
        Transport varrockTeleport = new Transport(
                new WorldPoint(3213, 3424, 0),
                "Varrock Teleport",
                TransportType.TELEPORTATION_SPELL,
                false,
                20,
                Collections.emptyMap());

        assertTrue(Rs2Walker.shouldRecalculatePathAfterTransport(varrockTeleport));
    }

    @Test
    public void rawTransportDispatch_allowsImmediateOriginlessTeleportEdge() {
        List<WorldPoint> rawPath = Arrays.asList(
                new WorldPoint(2610, 3100, 0),
                new WorldPoint(3213, 3424, 0),
                new WorldPoint(3214, 3424, 0));

        assertTrue(Rs2Walker.isRawTransportOriginNearPlayer(
                rawPath, 0, new WorldPoint(2610, 3100, 0), 2));
    }

    @Test
    public void rawTransportDispatch_defersFutureTransportUntilApproach() {
        List<WorldPoint> rawPath = Arrays.asList(
                new WorldPoint(2610, 3100, 0),
                new WorldPoint(2611, 3100, 0),
                new WorldPoint(2623, 3093, 0),
                new WorldPoint(3303, 3333, 0));

        assertFalse("A future edge inside the broad handler window must not start a long run",
                Rs2Walker.isRawTransportOriginNearPlayer(
                        rawPath, 2, new WorldPoint(2610, 3100, 0), 2));
        assertTrue("The same edge becomes dispatchable once the route has approached it",
                Rs2Walker.isRawTransportOriginNearPlayer(
                        rawPath, 2, new WorldPoint(2622, 3093, 0), 2));
    }

    @Test
    public void rawTransportDispatch_rejectsOtherPlane() {
        List<WorldPoint> rawPath = Arrays.asList(
                new WorldPoint(2775, 3234, 1),
                new WorldPoint(2772, 3234, 0));

        assertFalse(Rs2Walker.isRawTransportOriginNearPlayer(
                rawPath, 0, new WorldPoint(2775, 3234, 0), 2));
    }

    @Test
    public void plannedTransportApproach_clicksUntilDispatchRange() {
        WorldPoint player = new WorldPoint(2760, 3229, 0);
        WorldPoint charterOrigin = new WorldPoint(2760, 3238, 0);

        assertTrue("A planned transport nine tiles ahead still needs a route approach click",
                Rs2Walker.shouldApproachPlannedTransportOrigin(
                        true, charterOrigin, player, 2));
        assertFalse("Once beside the transport, its handler owns the next action",
                Rs2Walker.shouldApproachPlannedTransportOrigin(
                        true, charterOrigin, new WorldPoint(2760, 3236, 0), 2));
    }

    @Test
    public void plannedTransportApproach_rejectsOrdinaryAndOtherPlaneSteps() {
        WorldPoint player = new WorldPoint(2760, 3229, 0);

        assertFalse(Rs2Walker.shouldApproachPlannedTransportOrigin(
                false, new WorldPoint(2760, 3238, 0), player, 2));
        assertFalse(Rs2Walker.shouldApproachPlannedTransportOrigin(
                true, new WorldPoint(2760, 3238, 1), player, 2));
    }

    @Test
    public void shouldRecalculatePathAfterTransport_skipsAdjacentSamePlaneTransport() {
        Transport door = new Transport(
                new WorldPoint(3123, 3360, 0),
                new WorldPoint(3123, 3361, 0),
                "Door",
                TransportType.TRANSPORT,
                false,
                "Open",
                "Door",
                136);

        assertFalse(Rs2Walker.shouldRecalculatePathAfterTransport(door));
    }

    @Test
    public void isSettledNearAdjacentSamePlaneLanding_acceptsNearDestinationOffOrigin() {
        Transport door = new Transport(
                new WorldPoint(3152, 3363, 0),
                new WorldPoint(3153, 3363, 0),
                "Door",
                TransportType.TRANSPORT,
                false,
                "Open",
                "Door",
                136);

        assertTrue(Rs2Walker.isSettledNearAdjacentSamePlaneLanding(
                door,
                new WorldPoint(3154, 3363, 0),
                new WorldPoint(3153, 3363, 0),
                0));
    }

    @Test
    public void isSettledNearAdjacentSamePlaneLanding_rejectsOriginTile() {
        Transport door = new Transport(
                new WorldPoint(3152, 3363, 0),
                new WorldPoint(3153, 3363, 0),
                "Door",
                TransportType.TRANSPORT,
                false,
                "Open",
                "Door",
                136);

        assertFalse(Rs2Walker.isSettledNearAdjacentSamePlaneLanding(
                door,
                new WorldPoint(3152, 3363, 0),
                new WorldPoint(3153, 3363, 0),
                0));
    }

    @Test
    public void isSettledNearAdjacentSamePlaneLanding_rejectsTilesTooFarFromDestination() {
        Transport door = new Transport(
                new WorldPoint(3152, 3363, 0),
                new WorldPoint(3153, 3363, 0),
                "Door",
                TransportType.TRANSPORT,
                false,
                "Open",
                "Door",
                136);

        assertFalse(Rs2Walker.isSettledNearAdjacentSamePlaneLanding(
                door,
                new WorldPoint(3155, 3363, 0),
                new WorldPoint(3153, 3363, 0),
                0));
    }

    @Test
    public void isSettledNearAdjacentSamePlaneLanding_acceptsBoundedForwardAgilityOvershoot() {
        Transport steppingStone = new Transport(
                new WorldPoint(3154, 3363, 0),
                new WorldPoint(3153, 3363, 0),
                "Stepping stone",
                TransportType.AGILITY_SHORTCUT,
                false,
                "Jump-onto",
                "Stepping stone",
                16533);

        assertTrue(Rs2Walker.isSettledNearAdjacentSamePlaneLanding(
                steppingStone,
                new WorldPoint(3149, 3363, 0),
                steppingStone.getDestination(),
                0));
    }

    @Test
    public void isSettledNearAdjacentSamePlaneLanding_rejectsReverseOrUnboundedAgilityMovement() {
        Transport steppingStone = new Transport(
                new WorldPoint(3154, 3363, 0),
                new WorldPoint(3153, 3363, 0),
                "Stepping stone",
                TransportType.AGILITY_SHORTCUT,
                false,
                "Jump-onto",
                "Stepping stone",
                16533);

        assertFalse(Rs2Walker.isSettledNearAdjacentSamePlaneLanding(
                steppingStone,
                new WorldPoint(3155, 3363, 0),
                steppingStone.getDestination(),
                0));
        assertFalse(Rs2Walker.isSettledNearAdjacentSamePlaneLanding(
                steppingStone,
                new WorldPoint(3147, 3363, 0),
                steppingStone.getDestination(),
                0));
        assertFalse(Rs2Walker.isSettledNearAdjacentSamePlaneLanding(
                steppingStone,
                new WorldPoint(3149, 3365, 0),
                steppingStone.getDestination(),
                0));
    }

    @Test
    public void shouldRecalculatePathAfterTransport_includesLongDistanceTransport() {
        Transport ship = new Transport(
                new WorldPoint(3054, 3245, 0),
                new WorldPoint(2956, 3146, 0),
                "Port Sarim to Karamja",
                TransportType.SHIP,
                false,
                "Cross",
                "Gangplank",
                2082);

        assertTrue(Rs2Walker.shouldRecalculatePathAfterTransport(ship));
    }

    @Test
    public void shouldRecalculatePathAfterTransport_includesSamePlaneCoordinateBandTransport() {
        Transport varrockSewerLadder = new Transport(
                new WorldPoint(3237, 9858, 0),
                new WorldPoint(3236, 3458, 0),
                "Varrock Sewers ladder",
                TransportType.TRANSPORT,
                false,
                "Climb-up",
                "Ladder",
                11806);

        assertTrue(Rs2Walker.shouldRecalculatePathAfterTransport(varrockSewerLadder));
    }

    @Test
    public void hasPendingRouteStepBeforeArrival_detectsTransportBeforeDestination() {
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(3220, 3473, 0),
                new WorldPoint(3221, 3473, 0),
                new WorldPoint(3222, 3473, 0),
                new WorldPoint(3229, 3473, 0));

        assertTrue(Rs2Walker.hasPendingRouteStepBeforeArrival(
                path,
                new WorldPoint(3229, 3473, 0),
                0,
                i -> i == 1));
    }

    @Test
    public void hasPendingRouteStepBeforeArrival_ignoresStepsInsideArrivalTolerance() {
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(3220, 3473, 0),
                new WorldPoint(3227, 3473, 0),
                new WorldPoint(3228, 3473, 0),
                new WorldPoint(3229, 3473, 0));

        assertFalse(Rs2Walker.hasPendingRouteStepBeforeArrival(
                path,
                new WorldPoint(3229, 3473, 0),
                2,
                i -> i == 2));
    }

    // ---------------------------------------------------------------------------
    // #15 — Sidestep recovery ranking
    // ---------------------------------------------------------------------------

    @Test
    public void rankSidestep_nullReachable_returnsEmpty() {
        List<WorldPoint> ranked = Rs2Walker.rankSidestepTilesToward(null, new WorldPoint(3200, 3200, 0));
        assertTrue("null input must yield an empty ranking, never NPE", ranked.isEmpty());
    }

    @Test
    public void rankSidestep_emptyReachable_returnsEmpty() {
        List<WorldPoint> ranked = Rs2Walker.rankSidestepTilesToward(
                Collections.emptySet(), new WorldPoint(3200, 3200, 0));
        assertTrue(ranked.isEmpty());
    }

    @Test
    public void rankSidestep_singleTile_returnsSingleton() {
        WorldPoint only = new WorldPoint(3200, 3200, 0);
        List<WorldPoint> ranked = Rs2Walker.rankSidestepTilesToward(
                Collections.singleton(only), new WorldPoint(3210, 3210, 0));
        assertEquals(1, ranked.size());
        assertSame(only, ranked.get(0));
    }

    /**
     * The whole point of the ranking: closer-to-target tiles come first. The old
     * implementation picked randomly from reachable tiles and could walk AWAY from
     * the target, which caused repeated stall loops in narrow corridors.
     */
    @Test
    public void rankSidestep_closestToTargetIsFirst() {
        WorldPoint target = new WorldPoint(3220, 3200, 0);
        WorldPoint near = new WorldPoint(3210, 3200, 0);   // 10 away
        WorldPoint mid = new WorldPoint(3205, 3200, 0);    // 15 away
        WorldPoint far = new WorldPoint(3200, 3200, 0);    // 20 away

        List<WorldPoint> ranked = Rs2Walker.rankSidestepTilesToward(
                Arrays.asList(far, near, mid), target);

        assertEquals(3, ranked.size());
        assertSame("closest tile must lead the ranking", near, ranked.get(0));
        assertSame(mid, ranked.get(1));
        assertSame(far, ranked.get(2));
    }

    @Test
    public void rankSidestep_preservesAllTilesIncludingEquidistant() {
        WorldPoint target = new WorldPoint(3200, 3200, 0);
        // Both 5 tiles from target in Chebyshev, on opposite sides.
        WorldPoint east = new WorldPoint(3205, 3200, 0);
        WorldPoint west = new WorldPoint(3195, 3200, 0);
        WorldPoint north = new WorldPoint(3200, 3205, 0);
        Set<WorldPoint> reachable = new HashSet<>(Arrays.asList(east, west, north));

        List<WorldPoint> ranked = Rs2Walker.rankSidestepTilesToward(reachable, target);

        assertEquals("no tile may be dropped by the ranking", 3, ranked.size());
        assertTrue(ranked.contains(east));
        assertTrue(ranked.contains(west));
        assertTrue(ranked.contains(north));
    }

    @Test
    public void getClosestTileIndex_usesReachableDistanceWhenAvailable() {
        WorldPoint player = new WorldPoint(3200, 3200, 0);
        WorldPoint farByWorldDistance = new WorldPoint(3210, 3200, 0);
        WorldPoint nearReachable = new WorldPoint(3220, 3200, 0);
        List<WorldPoint> path = Arrays.asList(farByWorldDistance, nearReachable);
        Map<WorldPoint, Integer> reachable = new HashMap<>();
        reachable.put(farByWorldDistance, 8);
        reachable.put(nearReachable, 3);

        assertEquals(1, WalkerPathGeometry.getClosestTileIndex(path, player, reachable));
    }

    @Test
    public void getClosestTileIndex_fallsBackToWorldDistanceWhenNoReachablePathTile() {
        WorldPoint player = new WorldPoint(3200, 3200, 0);
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(3210, 3200, 0),
                new WorldPoint(3202, 3200, 0),
                new WorldPoint(3220, 3200, 0));

        assertEquals(1, WalkerPathGeometry.getClosestTileIndex(path, player, Collections.emptyMap()));
    }

    // ---------------------------------------------------------------------------
    // #21 — Minimap forward-scan
    // ---------------------------------------------------------------------------

    /**
     * When no path tile past the start index qualifies, the function must return the
     * start index — the walker still needs something to click at.
     */
    @Test
    public void findFurthest_shortPath_returnsStart() {
        List<WorldPoint> path = Collections.singletonList(new WorldPoint(3200, 3200, 0));
        int idx = RouteRecovery.findFurthestClickableIndex(path, 0, new WorldPoint(3200, 3200, 0),
                wp -> false, 14);
        assertEquals(0, idx);
    }

    @Test
    public void findFurthest_outOfBoundsStart_returnsStartUnchanged() {
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(3200, 3200, 0), new WorldPoint(3201, 3200, 0));
        assertEquals(-1, RouteRecovery.findFurthestClickableIndex(path, -1, new WorldPoint(3200, 3200, 0),
                wp -> false, 14));
        assertEquals(5, RouteRecovery.findFurthestClickableIndex(path, 5, new WorldPoint(3200, 3200, 0),
                wp -> false, 14));
    }

    /**
     * Cross-plane path steps are transports (stairs/ladders). Clicking past them would
     * make the walker walk into an unreachable spot on the other plane.
     */
    @Test
    public void findFurthest_stopsAtPlaneChange() {
        WorldPoint player = new WorldPoint(3200, 3200, 0);
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(3200, 3200, 0),  // 0: player
                new WorldPoint(3201, 3200, 0),  // 1: same plane
                new WorldPoint(3202, 3200, 0),  // 2: same plane
                new WorldPoint(3203, 3200, 1),  // 3: plane change (stairs up)
                new WorldPoint(3204, 3200, 1)); // 4: beyond plane change

        int idx = RouteRecovery.findFurthestClickableIndex(path, 1, player, wp -> false, 14);

        assertEquals("scan must stop at the tile BEFORE the plane change", 2, idx);
    }

    /**
     * Transport origins need explicit `handleTransports` interaction. Skipping past a
     * transport origin via a long minimap click would bypass that interaction.
     */
    @Test
    public void findFurthest_stopsAtTransportOrigin() {
        WorldPoint player = new WorldPoint(3200, 3200, 0);
        WorldPoint transportOrigin = new WorldPoint(3203, 3200, 0);
        Predicate<WorldPoint> isTransportOrigin = transportOrigin::equals;
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(3200, 3200, 0),
                new WorldPoint(3201, 3200, 0),
                new WorldPoint(3202, 3200, 0),
                transportOrigin,                  // 3: transport — scan must stop HERE
                new WorldPoint(3204, 3200, 0));

        int idx = RouteRecovery.findFurthestClickableIndex(path, 1, player, isTransportOrigin, 14);

        assertEquals("scan must stop at the tile before a transport origin", 2, idx);
    }

    @Test
    public void findFurthest_stopsAtEuclideanLimitCardinal() {
        // On a cardinal axis, Euclidean distance equals |dx|, so the scan reaches
        // the full reach value (14) — diagonals are bounded tighter.
        WorldPoint player = new WorldPoint(3200, 3200, 0);
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(3205, 3200, 0),  // 0: 5 away
                new WorldPoint(3210, 3200, 0),  // 1: 10 away
                new WorldPoint(3214, 3200, 0),  // 2: 14 away (AT limit)
                new WorldPoint(3215, 3200, 0),  // 3: 15 away (OVER limit)
                new WorldPoint(3220, 3200, 0)); // 4: 20 away

        int idx = RouteRecovery.findFurthestClickableIndex(path, 0, player, wp -> false, 14);

        assertEquals("scan must include the tile at the limit and stop at the tile over it",
                2, idx);
    }

    @Test
    public void findFurthest_boundedByEuclideanCircleOnDiagonal() {
        // The reach parameter is a Euclidean radius because the minimap's clickable
        // area is a circle. On a diagonal each step adds sqrt(2) to Euclidean
        // distance, so with reach=14 the furthest reachable diagonal tile is at
        // Chebyshev 9 (Euclidean sqrt(162)≈12.73 ≤ 14); the tile at Chebyshev 10
        // is Euclidean sqrt(200)≈14.14 and must be rejected.
        WorldPoint player = new WorldPoint(3200, 3200, 0);
        List<WorldPoint> diagonalPath = Arrays.asList(
                new WorldPoint(3201, 3201, 0),
                new WorldPoint(3205, 3205, 0),
                new WorldPoint(3209, 3209, 0),  // Chebyshev 9, Euclidean ~12.73 — in
                new WorldPoint(3210, 3210, 0)); // Chebyshev 10, Euclidean ~14.14 — out

        int idx = RouteRecovery.findFurthestClickableIndex(diagonalPath, 0, player, wp -> false, 14);

        assertEquals("scan must stop at the last diagonal tile inside the Euclidean circle",
                2, idx);
    }

    @Test
    public void findFurthest_nullPredicate_treatsAsNoTransport() {
        WorldPoint player = new WorldPoint(3200, 3200, 0);
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(3201, 3200, 0),
                new WorldPoint(3202, 3200, 0),
                new WorldPoint(3203, 3200, 0));

        int idx = RouteRecovery.findFurthestClickableIndex(path, 0, player, null, 14);

        assertEquals("null predicate must not NPE and must allow full scan", 2, idx);
    }

    /**
     * The anchored forward scan is shared by every route-click predicate (line-of-sight, reachable,
     * walkable, off-scene). If the smoothed-&gt;raw anchor points past the player's vicinity, the scan
     * breaks immediately on the Euclidean bound and returns null for ALL of them — observed live as
     * {@code sel=none}, which dropped route clicks onto the off-route wall-nudge clamp. Selection
     * must therefore retry anchored at the player's own closest raw tile.
     */
    @Test
    public void rawPathScan_staleAnchorPastPlayerYieldsNothingForEveryPredicate() {
        WorldPoint player = new WorldPoint(3183, 3435, 0);
        List<WorldPoint> raw = new java.util.ArrayList<>();
        for (int i = 0; i < 40; i++) {
            raw.add(new WorldPoint(3183, 3435 - i, 0)); // 40-tile route running south
        }

        assertNotNull("anchored at the player, the scan must find a forward route point",
                Rs2Walker.findFurthestRawPathPointMatching(raw, player, 10, 0, wp -> true));

        assertNull("a stale anchor near the goal must yield nothing even for an always-true predicate",
                Rs2Walker.findFurthestRawPathPointMatching(raw, player, 10, 38, wp -> true));
    }

    /**
     * Anti-ban: consecutive clicks must not all cover the same tile span, but the jittered reach has
     * hard bounds. Too short and the click lands inside the interim-close threshold, clearing the
     * checkpoint immediately and causing click thrash; too long and it falls outside the minimap
     * clip. Varying reach is the safe axis because it only changes how far ALONG the route we click,
     * never sideways — lateral tile offsets leave the route and were removed for that reason.
     */
    @Test
    public void routeClickReach_staysWithinSafeBandAndActuallyVaries() {
        int max = 10;
        java.util.Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 400; i++) {
            int reach = Rs2Walker.routeClickReach(max);
            assertTrue("reach must never exceed the caller's minimap reach, got " + reach, reach <= max);
            assertTrue("reach must stay clear of the interim-close threshold, got " + reach, reach >= 7);
            seen.add(reach);
        }
        assertTrue("reach must actually vary between clicks, saw only " + seen, seen.size() > 1);
    }

    /** A caller reach at or below the floor must be returned unchanged rather than inverted. */
    @Test
    public void routeClickReach_degenerateBoundsAreSafe() {
        for (int max : new int[]{0, 1, 5, 7}) {
            int reach = Rs2Walker.routeClickReach(max);
            assertEquals("a reach at/below the floor must pass through unchanged", max, reach);
        }
    }

    /**
     * Leaving the Motherlode Mine must still be able to clear a rockfall standing in the path.
     *
     * <p>The gate used to require the walk TARGET to be in region 14936, so a walk from inside the
     * mine to the Dwarven Mine (region 12185) bailed before inspecting anything. Observed live:
     * {@code at=(3751,5672) goal=(3040,9810)} — every outbound route was unable to mine through.
     */
    @Test
    public void rockfallGateAllowsWalksLeavingTheMotherlodeMine() {
        WorldPoint insideMlm = new WorldPoint(3751, 5672, 0);
        WorldPoint dwarvenMineGoal = new WorldPoint(3040, 9810, 0);

        assertEquals("precondition: this tile is the Motherlode Mine region",
                Rs2ObstacleHandler.MOTHERLODE_MINE_REGION, insideMlm.getRegionID());
        assertNotEquals("precondition: the outbound goal is in a different region — this is exactly "
                        + "what the old target-based gate rejected",
                Rs2ObstacleHandler.MOTHERLODE_MINE_REGION, dwarvenMineGoal.getRegionID());

        List<WorldPoint> path = Arrays.asList(insideMlm, new WorldPoint(3750, 5672, 0), dwarvenMineGoal);
        assertTrue("standing in the mine must qualify regardless of where the walk ends",
                Rs2ObstacleHandler.isMotherlodeRockfallCandidate(insideMlm, path, 0));
    }

    /** A rockfall ahead on the path qualifies even when the player has not entered the mine yet. */
    @Test
    public void rockfallGateAllowsApproachingTheMineFromOutside() {
        WorldPoint outside = new WorldPoint(3060, 9766, 0); // Dwarven Mine cave mouth
        List<WorldPoint> path = Arrays.asList(outside, new WorldPoint(3728, 5692, 0));

        assertNotEquals("precondition: the player is not in the mine yet",
                Rs2ObstacleHandler.MOTHERLODE_MINE_REGION, outside.getRegionID());
        assertTrue("a path tile inside the mine must still arm the handler",
                Rs2ObstacleHandler.isMotherlodeRockfallCandidate(outside, path, 0));
    }

    /** The gate must stay closed everywhere else — rockfalls exist only in the Motherlode Mine. */
    @Test
    public void rockfallGateStaysClosedAwayFromTheMine() {
        WorldPoint varrock = new WorldPoint(3210, 3424, 0);
        List<WorldPoint> path = Arrays.asList(varrock, new WorldPoint(3211, 3424, 0));

        assertFalse("an unrelated surface walk must not pay for the scene lookup",
                Rs2ObstacleHandler.isMotherlodeRockfallCandidate(varrock, path, 0));
        assertFalse("a null or empty path must never arm the handler",
                Rs2ObstacleHandler.isMotherlodeRockfallCandidate(varrock, null, 0));
    }

    /**
     * A handled door/transport/blocker must NOT be charged against the partial-retry budget.
     *
     * <p>Regression for a walk to an underground goal that reported UNREACHABLE while still
     * advancing. The path end sat 31 tiles short of the goal, so {@code partialPath} was true on
     * every iteration and the budget was armed for the whole walk. Opening one door ended the
     * iteration, landed in the partial branch and spent a retry; the next iteration spent the last
     * one a second later without the player ever walking. Three retries were gone ~100 tiles into a
     * route that was working, and the walker gave up on the surface having never reached the ladder.
     */
    @Test
    public void routeProgressExits_areNotChargedAgainstThePartialRetryBudget() {
        for (String progress : new String[]{
                "door-handled",
                "door-handled-local-reachability",
                "door-handled-during-interim",
                "door-handled-before-minimap-click",
                "transport-handled",
                "current-tile-transport-handled",
                "post-click-current-tile-transport-handled",
                "raw-path-scene-object-handled",
                "post-click-raw-path-scene-object-handled",
                "rockfall-handled",
                "path-blocker-handled",
                "interim-in-flight",
                "recovery-move-in-flight",
                "route-fold-continuation-click"}) {
            assertTrue("'" + progress + "' means the walker advanced the route, so it must not spend "
                            + "a partial retry", Rs2Walker.isRouteProgressExit(progress));
        }
    }

    /**
     * The exemption must stay narrow: reasons that mean the walker failed to advance still have to
     * consume the budget, otherwise a genuinely unreachable goal never terminates and the walk spins
     * until the outer tail cap trips.
     */
    @Test
    public void nonProgressExits_stillConsumeThePartialRetryBudget() {
        for (String stuck : new String[]{
                "end-of-path",
                "not-near-path",
                "player-location-null",
                "click-failed-off-minimap",
                "door-edge-waiting-retry",
                "door-edge-nearby-waiting-retry",
                "door-recovery-suppressed",
                "local-reachability-miss-no-click",
                null}) {
            assertFalse("'" + stuck + "' is not route progress and must still spend a retry",
                    Rs2Walker.isRouteProgressExit(stuck));
        }
    }

    /**
     * Off-path recovery must be able to step BACKWARD onto the route. When the player is pushed off
     * the path (e.g. stuck flush against a castle wall) and nothing ahead is reachable, the rejoin
     * helper picks the nearest reachable raw point even if it sits behind the anchor. Forward-only
     * selection would return null here and the walker would stall.
     */
    @Test
    public void findReachableRejoin_stepsBackwardWhenNothingAheadReachable() {
        WorldPoint player = new WorldPoint(3203, 3201, 0); // one tile off the line, beside the wall
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(3200, 3200, 0),  // 0
                new WorldPoint(3201, 3200, 0),  // 1
                new WorldPoint(3202, 3200, 0),  // 2
                new WorldPoint(3203, 3200, 0),  // 3 (anchor)
                new WorldPoint(3204, 3200, 0),  // 4
                new WorldPoint(3205, 3200, 0),  // 5
                new WorldPoint(3206, 3200, 0)); // 6
        // Only the tiles behind the anchor are reachable; the wall blocks everything ahead.
        Set<WorldPoint> reachable = new HashSet<>(Arrays.asList(
                new WorldPoint(3200, 3200, 0),
                new WorldPoint(3201, 3200, 0),
                new WorldPoint(3202, 3200, 0)));

        WorldPoint rejoin = Rs2Walker.findReachableRejoinRawPathPoint(path, player, 10, 3, reachable::contains);

        assertEquals("must rejoin the route by stepping back onto the nearest reachable raw tile",
                new WorldPoint(3202, 3200, 0), rejoin);
    }

    /**
     * When points ahead of the anchor are reachable, rejoin must prefer the furthest-forward one so
     * recovery never sacrifices progress or snaps back to an already-travelled branch.
     */
    @Test
    public void findReachableRejoin_prefersFurthestForwardReachable() {
        WorldPoint player = new WorldPoint(3203, 3201, 0);
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(3200, 3200, 0),  // 0
                new WorldPoint(3201, 3200, 0),  // 1
                new WorldPoint(3202, 3200, 0),  // 2
                new WorldPoint(3203, 3200, 0),  // 3 (anchor)
                new WorldPoint(3204, 3200, 0),  // 4
                new WorldPoint(3205, 3200, 0),  // 5
                new WorldPoint(3206, 3200, 0)); // 6
        Set<WorldPoint> reachable = new HashSet<>(path); // everything reachable

        // maxEuclidean = 3 -> (3206,3200) is Euclidean sqrt(10) > 3 and must be rejected;
        // (3205,3200) is sqrt(5) <= 3 and is the furthest-forward admissible point.
        WorldPoint rejoin = Rs2Walker.findReachableRejoinRawPathPoint(path, player, 3, 3, reachable::contains);

        assertEquals("rejoin must prefer the furthest-forward reachable raw tile inside the reach circle",
                new WorldPoint(3205, 3200, 0), rejoin);
    }

    @Test
    public void findReachableRejoin_returnsNullWhenNothingReachable() {
        WorldPoint player = new WorldPoint(3203, 3201, 0);
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(3202, 3200, 0),
                new WorldPoint(3203, 3200, 0),
                new WorldPoint(3204, 3200, 0));

        assertNull("no reachable raw point => null (caller lets stall/recalc take over)",
                Rs2Walker.findReachableRejoinRawPathPoint(path, player, 10, 1, wp -> false));
    }

    @Test
    public void findReachableRejoin_nullInputsAreSafe() {
        WorldPoint player = new WorldPoint(3203, 3201, 0);
        List<WorldPoint> path = Collections.singletonList(new WorldPoint(3203, 3200, 0));
        assertNull(Rs2Walker.findReachableRejoinRawPathPoint(null, player, 10, 0, wp -> true));
        assertNull(Rs2Walker.findReachableRejoinRawPathPoint(path, null, 10, 0, wp -> true));
        assertNull(Rs2Walker.findReachableRejoinRawPathPoint(path, player, 10, 0, null));
        assertNull(Rs2Walker.findReachableRejoinRawPathPoint(Collections.emptyList(), player, 10, 0, wp -> true));
    }

    @Test
    public void stabilizeRouteProgressIndex_doesNotJumpBackToEarlierNearbyBranch() {
        WorldPoint target = new WorldPoint(3200, 3201, 0);
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(3200, 3200, 0),
                new WorldPoint(3201, 3200, 0),
                new WorldPoint(3202, 3200, 0),
                new WorldPoint(3203, 3200, 0),
                new WorldPoint(3203, 3201, 0),
                new WorldPoint(3202, 3201, 0),
                new WorldPoint(3201, 3201, 0),
                target);

        assertEquals(5, Rs2Walker.stabilizeRouteProgressIndex(path, 5, target, new WorldPoint(3202, 3201, 0)));
        assertEquals("nearby earlier branch must not become the active route index",
                5,
                Rs2Walker.stabilizeRouteProgressIndex(path, 2, target, new WorldPoint(3202, 3201, 0)));
        assertEquals(6, Rs2Walker.stabilizeRouteProgressIndex(path, 6, target, new WorldPoint(3201, 3201, 0)));
    }

    @Test
    public void hintRouteProgressIndex_keepsProgressAnchoredAheadOfEarlierBranch() {
        WorldPoint target = new WorldPoint(3200, 3201, 0);
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(3200, 3200, 0),
                new WorldPoint(3201, 3200, 0),
                new WorldPoint(3202, 3200, 0),
                new WorldPoint(3203, 3200, 0),
                new WorldPoint(3203, 3201, 0),
                new WorldPoint(3202, 3201, 0),
                new WorldPoint(3201, 3201, 0),
                target);

        Rs2Walker.hintRouteProgressIndex(path, 5, target);

        assertEquals("a clicked-ahead route checkpoint should not snap back to a nearby earlier branch",
                5,
                Rs2Walker.stabilizeRouteProgressIndex(path, 2, target, new WorldPoint(3202, 3201, 0)));
    }

    @Test
    public void findForwardRecoveryIndex_prefersLaterReachableBranch() {
        WorldPoint player = new WorldPoint(1000, 1000, 0);
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(998, 1000, 0),
                new WorldPoint(999, 1000, 0),
                new WorldPoint(1000, 1001, 0),
                new WorldPoint(1015, 1000, 0),
                new WorldPoint(1016, 1001, 0),
                new WorldPoint(1002, 1002, 0),
                new WorldPoint(1003, 1001, 0));
        Set<WorldPoint> reachable = new HashSet<>(Arrays.asList(
                new WorldPoint(1002, 1002, 0),
                new WorldPoint(1003, 1001, 0)));

        int idx = RouteRecovery.findForwardRecoveryIndex(path, 3, player, 13, reachable, wp -> true);

        assertEquals("recovery should scan forward before falling back to an earlier branch", 6, idx);
    }

    @Test
    public void findFurthestClickableIndex_canReturnEarlierNearbyBranch() {
        WorldPoint player = new WorldPoint(1000, 1000, 0);
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(998, 1000, 0),
                new WorldPoint(999, 1000, 0),
                new WorldPoint(1000, 1001, 0),
                new WorldPoint(1015, 1000, 0),
                new WorldPoint(1016, 1001, 0));

        int idx = RouteRecovery.findFurthestClickableIndex(path, 3, player, wp -> false, 13);

        assertEquals("generic fallback is allowed to backtrack; recovery clamps this at the call site", 2, idx);
    }

    @Test
    public void findFurthestForwardClickableIndex_doesNotBacktrackToEarlierNearbyBranch() {
        WorldPoint player = new WorldPoint(1000, 1000, 0);
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(998, 1000, 0),
                new WorldPoint(999, 1000, 0),
                new WorldPoint(1000, 1001, 0),
                new WorldPoint(1015, 1000, 0),
                new WorldPoint(1016, 1001, 0));

        int idx = RouteRecovery.findFurthestForwardClickableIndex(path, 3, player, wp -> false, 13);

        assertEquals("normal route following should interpolate toward the forward tile, not backtrack", 3, idx);
    }

    @Test
    public void findFurthestRawPathPointMatching_keepsPrimaryClickOnForwardRawRoute() {
        WorldPoint player = new WorldPoint(1000, 1000, 0);
        List<WorldPoint> rawPath = Arrays.asList(
                new WorldPoint(998, 1000, 0),
                new WorldPoint(999, 1000, 0),
                new WorldPoint(1000, 1001, 0),
                new WorldPoint(1010, 1000, 0),
                new WorldPoint(1009, 1000, 0),
                new WorldPoint(1008, 1000, 0));

        WorldPoint target = Rs2Walker.findFurthestRawPathPointMatching(
                rawPath,
                player,
                13,
                3,
                wp -> true);

        assertEquals("raw-route selection must not snap back to an earlier nearby branch",
                new WorldPoint(1008, 1000, 0), target);
    }

    @Test
    public void findFurthestRawPathPointMatching_honorsCandidatePredicate() {
        WorldPoint player = new WorldPoint(3200, 3200, 0);
        WorldPoint blocked = new WorldPoint(3203, 3200, 0);
        WorldPoint allowed = new WorldPoint(3202, 3200, 0);
        List<WorldPoint> rawPath = Arrays.asList(
                new WorldPoint(3200, 3200, 0),
                new WorldPoint(3201, 3200, 0),
                allowed,
                blocked);

        WorldPoint target = Rs2Walker.findFurthestRawPathPointMatching(
                rawPath,
                player,
                13,
                0,
                wp -> !wp.equals(blocked));

        assertEquals("primary raw-route target must be the furthest acceptable raw tile",
                allowed, target);
    }

    @Test
    public void findFurthestRawPathPointMatching_doesNotReturnCurrentTile() {
        WorldPoint player = new WorldPoint(3200, 3200, 0);
        List<WorldPoint> rawPath = Arrays.asList(
                player,
                new WorldPoint(3215, 3200, 0));

        WorldPoint target = Rs2Walker.findFurthestRawPathPointMatching(
                rawPath,
                player,
                10,
                0,
                wp -> true);

        assertNull("same-tile route targets are no-op clicks, not recovery candidates", target);
    }

    @Test
    public void findFurthestRawPathPointMatching_stopsBeforeRouteStepBudgetIsExceeded() {
        WorldPoint player = new WorldPoint(3200, 3200, 0);
        List<WorldPoint> rawPath = Arrays.asList(
                new WorldPoint(3200, 3200, 0),
                new WorldPoint(3205, 3200, 0),
                new WorldPoint(3210, 3200, 0),
                new WorldPoint(3216, 3200, 0));

        WorldPoint target = Rs2Walker.findFurthestRawPathPointMatching(
                rawPath,
                player,
                13,
                0,
                wp -> true);

        assertEquals("route step budget keeps one minimap click from spanning too far ahead",
                new WorldPoint(3210, 3200, 0), target);
    }

    @Test
    public void findFurthestForwardClickableIndex_stopsBeforeTransportOrigin() {
        WorldPoint player = new WorldPoint(3200, 3200, 0);
        WorldPoint transportOrigin = new WorldPoint(3203, 3200, 0);
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(3201, 3200, 0),
                new WorldPoint(3202, 3200, 0),
                transportOrigin,
                new WorldPoint(3204, 3200, 0));

        int idx = RouteRecovery.findFurthestForwardClickableIndex(path, 0, player, transportOrigin::equals, 13);

        assertEquals("route clicks must not skip a planned transport origin", 1, idx);
    }

    @Test
    public void offPathRecalcDeferralReason_prefersSettlingAndBusyState() {
        long now = 10_000L;

        assertEquals("door-settling", Rs2Walker.offPathRecalcDeferralReason(
                true, false, false, true, false, false,
                now, 0L, 0L, 0L, 0L));
        assertEquals("transport-settling", Rs2Walker.offPathRecalcDeferralReason(
                true, false, false, false, true, false,
                now, 0L, 0L, 0L, 0L));
        assertEquals("moving", Rs2Walker.offPathRecalcDeferralReason(
                true, false, false, false, false, false,
                now, 0L, 0L, 0L, 0L));
    }

    @Test
    public void offPathRecalcDeferralReason_recentProgressDefersAfterMovementStops() {
        long now = 10_000L;

        assertEquals("route-progress", Rs2Walker.offPathRecalcDeferralReason(
                false, false, false, false, false, false,
                now, 0L, 8_000L, 0L, 0L));
        assertEquals("recent-click", Rs2Walker.offPathRecalcDeferralReason(
                false, false, false, false, false, false,
                now, 0L, 0L, 8_000L, 0L));
        assertEquals("interim-progress", Rs2Walker.offPathRecalcDeferralReason(
                false, false, false, false, false, true,
                now, 0L, 0L, 0L, 8_000L));
    }

    @Test
    public void offPathRecalcDeferralReason_allowsRecalcWhenSignalsExpired() {
        long now = 10_000L;

        assertEquals(null, Rs2Walker.offPathRecalcDeferralReason(
                false, false, false, false, false, false,
                now, 7_000L, 6_000L, 7_000L, 7_000L));
    }

    @Test
    public void offPathRecalcDeferredWaitMs_isBounded() {
        assertEquals(1200, Rs2Walker.offPathRecalcDeferredWaitMs(
                "route-progress", 10_000L, 0L, 9_700L, 0L, 0L));
        assertEquals(250, Rs2Walker.offPathRecalcDeferredWaitMs(
                "route-progress", 10_000L, 0L, 6_600L, 0L, 0L));
    }

    @Test
    public void interpolateClickableTarget_usesInterpolatedPointWhenUsable() {
        WorldPoint player = new WorldPoint(3200, 3200, 0);
        WorldPoint fallback = new WorldPoint(3206, 3200, 0);
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(3200, 3200, 0),
                fallback,
                new WorldPoint(3220, 3200, 0));

        WorldPoint target = RouteRecovery.interpolateClickableTarget(path, 2, player, fallback, 12, wp -> true);

        assertEquals(new WorldPoint(3212, 3200, 0), target);
    }

    @Test
    public void interpolateClickableTarget_fallsBackWhenInterpolatedPointUnusable() {
        WorldPoint player = new WorldPoint(3200, 3200, 0);
        WorldPoint fallback = new WorldPoint(3206, 3200, 0);
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(3200, 3200, 0),
                fallback,
                new WorldPoint(3220, 3200, 0));

        WorldPoint target = RouteRecovery.interpolateClickableTarget(path, 2, player, fallback, 12, wp -> false);

        assertEquals("unusable interpolated tiles must not replace the known path waypoint",
                fallback, target);
    }

    @Test
    public void interpolateClickableTarget_shortensOutOfReachForwardWaypoint() {
        WorldPoint player = new WorldPoint(3200, 3200, 0);
        WorldPoint forward = new WorldPoint(3220, 3200, 0);
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(3200, 3200, 0),
                forward);

        WorldPoint target = RouteRecovery.interpolateClickableTarget(path, 1, player, forward, 12, wp -> true);

        assertEquals("out-of-minimap forward waypoints should be shortened to a clickable tile",
                new WorldPoint(3212, 3200, 0), target);
    }

    @Test
    public void clampToEuclideanRadius_shortensDiagonalTargetInsideCircle() {
        WorldPoint player = new WorldPoint(2875, 3418, 0);
        WorldPoint target = new WorldPoint(2886, 3428, 0);

        WorldPoint clamped = RouteRecovery.clampToEuclideanRadius(player, target, 10);

        assertTrue(clamped.distanceTo2D(player) <= 10);
        assertTrue(clamped.getX() > player.getX());
        assertTrue(clamped.getY() > player.getY());
    }

    @Test
    public void clampToEuclideanRadius_keepsInRangeTarget() {
        WorldPoint player = new WorldPoint(3200, 3200, 0);
        WorldPoint target = new WorldPoint(3206, 3203, 0);

        assertEquals(target, RouteRecovery.clampToEuclideanRadius(player, target, 10));
    }

    @Test
    public void directMinimapTarget_usesEuclideanRatherThanChebyshevRange() {
        WorldPoint player = new WorldPoint(3289, 3476, 0);

        assertFalse("A diagonal endpoint can be Chebyshev-close but outside the circular minimap reach",
                Rs2Walker.shouldAttemptDirectMinimapTarget(
                        new WorldPoint(3300, 3487, 0), player, 12));
        assertTrue("A cardinal endpoint on the Euclidean boundary remains eligible",
                Rs2Walker.shouldAttemptDirectMinimapTarget(
                        new WorldPoint(3301, 3476, 0), player, 12));
        assertFalse("A different plane is never a direct minimap target",
                Rs2Walker.shouldAttemptDirectMinimapTarget(
                        new WorldPoint(3289, 3476, 1), player, 12));
    }

    @Test
    public void localRecoveryCandidate_rejectsSpatiallyNearFutureRouteFold() {
        List<WorldPoint> raw = new java.util.ArrayList<>();
        int x = 2855;
        int y = 3440;
        raw.add(new WorldPoint(x, y, 0));
        for (int i = 0; i < 15; i++) {
            raw.add(new WorldPoint(--x, y, 0));
        }
        for (int i = 0; i < 15; i++) {
            raw.add(new WorldPoint(x, ++y, 0));
        }
        for (int i = 0; i < 15; i++) {
            raw.add(new WorldPoint(++x, y, 0));
        }
        for (int i = 0; i < 15; i++) {
            raw.add(new WorldPoint(x, --y, 0));
        }
        // The future branch returns spatially close to the player, but remains 60 raw route steps
        // ahead. Its proximity must not override the immediate route frontier.
        int[] smoothedToRaw = {0, 6, 60};

        assertTrue(RouteRecovery.isLocalRecoveryCandidateOnForwardRoute(
                raw, smoothedToRaw, 0, 1, 48));
        assertFalse(RouteRecovery.isLocalRecoveryCandidateOnForwardRoute(
                raw, smoothedToRaw, 0, 2, 48));
    }

    @Test
    public void localRecoveryCandidate_rejectsTransportJumpAndBackwardCandidate() {
        List<WorldPoint> raw = Arrays.asList(
                new WorldPoint(2808, 3436, 0),
                new WorldPoint(2809, 3436, 0),
                new WorldPoint(2900, 3400, 0));
        int[] smoothedToRaw = {0, 1, 2};

        assertFalse(RouteRecovery.isLocalRecoveryCandidateOnForwardRoute(
                raw, smoothedToRaw, 0, 2, 48));
        assertFalse(RouteRecovery.isLocalRecoveryCandidateOnForwardRoute(
                raw, smoothedToRaw, 1, 0, 48));
    }

    // ---------------------------------------------------------------------------
    // Raw-path wall-door segment probing
    // ---------------------------------------------------------------------------

    @Test
    public void wallDoorTouchesSegment_crossingDoorEdge_returnsTrue() {
        WallObject door = mock(WallObject.class);
        when(door.getWorldLocation()).thenReturn(new WorldPoint(3123, 3361, 0));
        when(door.getOrientationA()).thenReturn(8); // south-facing door edge

        assertTrue(Rs2DoorGeometry.wallDoorTouchesSegment(door,
                new WorldPoint(3123, 3361, 0),
                new WorldPoint(3123, 3360, 0)));
        assertTrue(Rs2DoorGeometry.wallDoorTouchesSegment(door,
                new WorldPoint(3123, 3360, 0),
                new WorldPoint(3123, 3361, 0)));
    }

    @Test
    public void wallDoorTouchesSegment_diagonalStepThroughGateCorner_returnsTrue() {
        WallObject gate = mock(WallObject.class);
        when(gate.getWorldLocation()).thenReturn(new WorldPoint(3240, 3302, 0));
        when(gate.getOrientationA()).thenReturn(4); // gate blocks 3240,3302 <-> 3241,3302

        assertTrue(Rs2DoorGeometry.wallDoorTouchesSegment(gate,
                new WorldPoint(3240, 3301, 0),
                new WorldPoint(3241, 3302, 0)));
        assertTrue(Rs2DoorGeometry.wallDoorTouchesSegment(gate,
                new WorldPoint(3241, 3302, 0),
                new WorldPoint(3240, 3301, 0)));
    }

    @Test
    public void wallDoorTouchesSegment_startingBesideDoorAndMovingAway_returnsFalse() {
        WallObject door = mock(WallObject.class);
        when(door.getWorldLocation()).thenReturn(new WorldPoint(3123, 3361, 0));
        when(door.getOrientationA()).thenReturn(8); // door blocks 3123,3361 <-> 3123,3360

        assertFalse("standing on the door's south neighbor and walking southwest must not re-open the door",
                Rs2DoorGeometry.wallDoorTouchesSegment(door,
                        new WorldPoint(3123, 3360, 0),
                        new WorldPoint(3122, 3359, 0)));
    }

    @Test
    public void isDoorEdgeNudgeResolved_movesToWrongNeighbor_returnsFalse() {
        assertFalse(Rs2Walker.isDoorEdgeNudgeResolved(
                new WorldPoint(3240, 3301, 0),
                new WorldPoint(3239, 3302, 0),
                new WorldPoint(3240, 3301, 0),
                new WorldPoint(3241, 3302, 0)));
    }

    @Test
    public void isDoorEdgeNudgeResolved_crossesToDoorTarget_returnsTrue() {
        assertTrue(Rs2Walker.isDoorEdgeNudgeResolved(
                new WorldPoint(3240, 3301, 0),
                new WorldPoint(3241, 3302, 0),
                new WorldPoint(3240, 3301, 0),
                new WorldPoint(3241, 3302, 0)));
    }

    @Test
    public void shouldClearInterimTarget_closeToCheckpoint_returnsTrue() {
        assertTrue(Rs2Walker.shouldClearInterimTarget(
                new WorldPoint(2890, 3396, 0),
                new WorldPoint(2889, 3396, 0),
                1_000L,
                1_500L,
                2_000L));
    }

    @Test
    public void shouldClearInterimTarget_preclickDistanceStillKeepsCheckpoint() {
        assertFalse(Rs2Walker.shouldClearInterimTarget(
                new WorldPoint(2890, 3396, 0),
                new WorldPoint(2884, 3396, 0),
                1_000L,
                1_500L,
                2_000L));
    }

    @Test
    public void distanceToInterimOrMax_samePlaneReturnsDistance() {
        assertEquals(8, Rs2Walker.distanceToInterimOrMax(
                new WorldPoint(2850, 3506, 0),
                new WorldPoint(2849, 3498, 0)));
    }

    @Test
    public void shouldClearInterimTarget_expiredCheckpoint_returnsTrue() {
        assertTrue(Rs2Walker.shouldClearInterimTarget(
                new WorldPoint(2890, 3396, 0),
                new WorldPoint(2880, 3396, 0),
                1_000L,
                1_500L,
                12_000L));
    }

    @Test
    public void shouldClearInterimTarget_staleProgress_returnsTrue() {
        assertTrue(Rs2Walker.shouldClearInterimTarget(
                new WorldPoint(2890, 3396, 0),
                new WorldPoint(2880, 3396, 0),
                1_000L,
                1_500L,
                5_000L));
    }

    @Test
    public void shouldClearInterimTarget_activeFarCheckpoint_returnsFalse() {
        assertFalse(Rs2Walker.shouldClearInterimTarget(
                new WorldPoint(2890, 3396, 0),
                new WorldPoint(2880, 3396, 0),
                1_000L,
                4_500L,
                5_000L));
    }

    @Test
    public void shouldYieldForActiveRecoveryInterim_recentProgress_returnsTrue() {
        assertTrue(Rs2Walker.shouldYieldForActiveRecoveryInterim(
                new WorldPoint(2890, 3396, 0),
                new WorldPoint(2884, 3396, 0),
                1_000L,
                2_500L,
                3_000L,
                0L,
                0L,
                false));
    }

    @Test
    public void shouldYieldForActiveRecoveryInterim_staleProgress_returnsFalse() {
        assertFalse(Rs2Walker.shouldYieldForActiveRecoveryInterim(
                new WorldPoint(2890, 3396, 0),
                new WorldPoint(2880, 3396, 0),
                1_000L,
                1_500L,
                5_000L,
                0L,
                0L,
                false));
    }

    @Test
    public void shouldYieldForActiveRecoveryInterim_recentRecoveryClick_returnsTrue() {
        assertTrue(Rs2Walker.shouldYieldForActiveRecoveryInterim(
                new WorldPoint(2890, 3396, 0),
                new WorldPoint(2880, 3396, 0),
                1_000L,
                0L,
                3_000L,
                0L,
                2_000L,
                false));
    }

    @Test
    public void shouldDeferRouteWorkForActiveInterim_movingFarCheckpoint_returnsTrue() {
        assertTrue(Rs2Walker.shouldDeferRouteWorkForActiveInterim(
                new WorldPoint(2890, 3396, 0),
                new WorldPoint(2880, 3396, 0),
                1_000L,
                4_500L,
                5_000L,
                0L,
                true,
                5));
    }

    @Test
    public void shouldDeferRouteWorkForActiveInterim_recentProgressStoppedFar_returnsTrue() {
        assertTrue(Rs2Walker.shouldDeferRouteWorkForActiveInterim(
                new WorldPoint(2890, 3396, 0),
                new WorldPoint(2880, 3396, 0),
                1_000L,
                4_900L,
                5_000L,
                0L,
                false,
                5));
    }

    @Test
    public void shouldDeferRouteWorkForActiveInterim_closeCheckpoint_returnsFalse() {
        assertFalse(Rs2Walker.shouldDeferRouteWorkForActiveInterim(
                new WorldPoint(2890, 3396, 0),
                new WorldPoint(2886, 3396, 0),
                1_000L,
                4_500L,
                5_000L,
                0L,
                true,
                5));
    }

    @Test
    public void shouldDeferRouteWorkForActiveInterim_staleStoppedCheckpoint_returnsFalse() {
        assertFalse(Rs2Walker.shouldDeferRouteWorkForActiveInterim(
                new WorldPoint(2890, 3396, 0),
                new WorldPoint(2880, 3396, 0),
                1_000L,
                1_500L,
                5_000L,
                0L,
                false,
                5));
    }

    @Test
    public void interimPreclickTiles_runHandsOffEarlierThanWalk() {
        assertEquals(6, Rs2Walker.interimPreclickTiles(false));
        assertEquals(8, Rs2Walker.interimPreclickTiles(true));
    }

    @Test
    public void routeMovementClickPhase_labelsContinuationSeparatelyFromRecovery() {
        assertEquals("stall_recovery_click", Rs2Walker.routeMovementClickPhase("stall recovery click"));
        assertEquals("active_route_idle_nudge", Rs2Walker.routeMovementClickPhase("active route idle nudge"));
        assertEquals("interim_close_route_click", Rs2Walker.routeMovementClickPhase("interim close route click"));
        assertEquals("route_movement_click", Rs2Walker.routeMovementClickPhase("other"));
    }

    @Test
    public void routeArrivalSatisfied_preventsRecoveryClickAtGoal() {
        WorldPoint goal = new WorldPoint(3304, 3336, 0);
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(3300, 3333, 0),
                goal);

        assertTrue(Rs2Walker.routeArrivalSatisfied(goal, goal, path, 10));
    }

    @Test
    public void routeArrivalSatisfied_usesTightFinalApproachThreshold() {
        WorldPoint goal = new WorldPoint(3304, 3336, 0);
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(3300, 3333, 0),
                goal);

        assertTrue(Rs2Walker.routeArrivalSatisfied(
                new WorldPoint(3303, 3336, 0), goal, path, 10));
        assertFalse(Rs2Walker.routeArrivalSatisfied(
                new WorldPoint(3302, 3336, 0), goal, path, 10));
        assertFalse(Rs2Walker.routeArrivalSatisfied(
                new WorldPoint(3304, 3336, 1), goal, path, 10));
    }

    @Test
    public void shouldRunActiveRouteIdleNudge_waitsForImmediateTransport() {
        assertFalse(Rs2Walker.shouldRunActiveRouteIdleNudge(true, true));
        assertTrue(Rs2Walker.shouldRunActiveRouteIdleNudge(true, false));
        assertFalse(Rs2Walker.shouldRunActiveRouteIdleNudge(false, false));
    }

    @Test
    public void shouldSkipStartupPreclickSegmentHandlers_skipsBeforeFirstMovementClick() {
        assertTrue(Rs2Walker.shouldSkipStartupPreclickSegmentHandlers(
                true,
                5,
                5,
                false,
                false,
                false));
    }

    @Test
    public void shouldSkipStartupPreclickSegmentHandlers_keepsDoorRecoveryAndSteadyEdges() {
        assertFalse(Rs2Walker.shouldSkipStartupPreclickSegmentHandlers(
                true,
                8,
                5,
                true,
                false,
                false));
        assertFalse(Rs2Walker.shouldSkipStartupPreclickSegmentHandlers(
                false,
                8,
                5,
                false,
                false,
                false));
    }

    @Test
    public void rawPathForwardAnchorIndex_keepsFallbackAheadOfAnchor() {
        List<WorldPoint> rawPath = Arrays.asList(
                new WorldPoint(3200, 3200, 0),
                new WorldPoint(3201, 3200, 0),
                new WorldPoint(3202, 3200, 0),
                new WorldPoint(3203, 3200, 0),
                new WorldPoint(3204, 3200, 0),
                new WorldPoint(3205, 3200, 0),
                new WorldPoint(3206, 3200, 0),
                new WorldPoint(3207, 3200, 0),
                new WorldPoint(3208, 3200, 0),
                new WorldPoint(3209, 3200, 0),
                new WorldPoint(3210, 3200, 0),
                new WorldPoint(3210, 3201, 0),
                new WorldPoint(3210, 3202, 0),
                new WorldPoint(3210, 3203, 0),
                new WorldPoint(3210, 3204, 0),
                new WorldPoint(3209, 3204, 0),
                new WorldPoint(3208, 3204, 0),
                new WorldPoint(3207, 3204, 0),
                new WorldPoint(3206, 3204, 0),
                new WorldPoint(3205, 3204, 0),
                new WorldPoint(3204, 3204, 0));
        WorldPoint playerOnReturnBranch = rawPath.get(20);

        assertEquals("forward anchor must keep raw fallback on the current return branch",
                20,
                Rs2Walker.rawPathForwardAnchorIndex(rawPath, playerOnReturnBranch, 14));
    }

    @Test
    public void didTraverseInteractedDoor_crossesDoorTowardSegmentDestination_returnsTrue() {
        assertTrue(Rs2Walker.didTraverseInteractedDoor(
                new WorldPoint(2465, 3494, 0),
                new WorldPoint(2465, 3493, 0),
                new WorldPoint(2465, 3493, 0),
                new WorldPoint(2465, 3494, 0),
                new WorldPoint(2465, 3493, 0)));
    }

    @Test
    public void didTraverseInteractedDoor_movesWithoutCrossingObject_returnsFalse() {
        assertFalse(Rs2Walker.didTraverseInteractedDoor(
                new WorldPoint(2465, 3494, 0),
                new WorldPoint(2465, 3495, 0),
                new WorldPoint(2465, 3493, 0),
                new WorldPoint(2465, 3494, 0),
                new WorldPoint(2465, 3493, 0)));
    }

    @Test
    public void didTraverseInteractedDoor_crossesObjectButMovesAwayFromDestination_returnsFalse() {
        assertFalse(Rs2Walker.didTraverseInteractedDoor(
                new WorldPoint(1987, 5568, 0),
                new WorldPoint(1986, 5568, 0),
                new WorldPoint(1987, 5568, 0),
                new WorldPoint(1987, 5568, 0),
                new WorldPoint(1988, 5568, 0)));
    }

    @Test
    public void shouldBlacklistDoorAfterWrongTraversal_teleportAway_returnsTrue() {
        assertTrue(Rs2Walker.shouldBlacklistDoorAfterWrongTraversal(
                new WorldPoint(1987, 5568, 0),
                new WorldPoint(2435, 3519, 0),
                new WorldPoint(1987, 5568, 0),
                new WorldPoint(1988, 5569, 0)));
    }

    @Test
    public void shouldBlacklistDoorAfterWrongTraversal_startedFarFromDoor_returnsFalse() {
        assertFalse("movement from an earlier minimap click must not blacklist a valid gate",
                Rs2Walker.shouldBlacklistDoorAfterWrongTraversal(
                        new WorldPoint(3270, 3320, 0),
                        new WorldPoint(3275, 3325, 0),
                        new WorldPoint(3262, 3322, 0),
                        new WorldPoint(3261, 3321, 0)));
    }

    @Test
    public void shouldBlacklistDoorAfterWrongTraversal_progressTowardEdge_returnsFalse() {
        assertFalse(Rs2Walker.shouldBlacklistDoorAfterWrongTraversal(
                new WorldPoint(2465, 3494, 0),
                new WorldPoint(2465, 3493, 0),
                new WorldPoint(2465, 3494, 0),
                new WorldPoint(2465, 3493, 0)));
    }

    @Test
    public void markDoorEdgeAttemptThisPass_allowsFirstAttemptOnly() {
        java.util.Map<String, WorldPoint> attempted = new java.util.HashMap<>();
        WorldPoint[] segment = new WorldPoint[] {
                new WorldPoint(2465, 3494, 0),
                new WorldPoint(2465, 3493, 0)
        };

        WorldPoint playerPos = new WorldPoint(2465, 3494, 0);
        assertTrue(Rs2Walker.markDoorEdgeAttemptThisPass(attempted, segment, playerPos));
        assertFalse(Rs2Walker.markDoorEdgeAttemptThisPass(attempted, segment, playerPos));
    }

    @Test
    public void markDoorEdgeAttemptThisPass_treatsReverseEdgeAsDuplicate() {
        java.util.Map<String, WorldPoint> attempted = new java.util.HashMap<>();
        WorldPoint[] forward = new WorldPoint[] {
                new WorldPoint(2465, 3494, 0),
                new WorldPoint(2465, 3493, 0)
        };
        WorldPoint[] reverse = new WorldPoint[] {
                new WorldPoint(2465, 3493, 0),
                new WorldPoint(2465, 3494, 0)
        };

        WorldPoint playerPos = new WorldPoint(2465, 3494, 0);
        assertTrue(Rs2Walker.markDoorEdgeAttemptThisPass(attempted, forward, playerPos));
        assertFalse(Rs2Walker.markDoorEdgeAttemptThisPass(attempted, reverse, playerPos));
    }

    @Test
    public void markDoorEdgeAttemptThisPass_allowsRetryAfterPlayerProgress() {
        java.util.Map<String, WorldPoint> attempted = new java.util.HashMap<>();
        WorldPoint[] segment = new WorldPoint[] {
                new WorldPoint(2465, 3494, 0),
                new WorldPoint(2465, 3493, 0)
        };

        assertTrue(Rs2Walker.markDoorEdgeAttemptThisPass(attempted, segment, new WorldPoint(2465, 3494, 0)));
        assertTrue("retry should be allowed after moving away from same-edge attempt tile",
                Rs2Walker.markDoorEdgeAttemptThisPass(attempted, segment, new WorldPoint(2462, 3491, 0)));
    }

    // ---------------------------------------------------------------------------
    // #19 — Quest-lock dialogue heuristic
    // ---------------------------------------------------------------------------

    @Test
    public void questLock_nullAndEmpty_returnFalse() {
        assertFalse(Rs2Walker.hasQuestLockKeywords(null));
        assertFalse(Rs2Walker.hasQuestLockKeywords(""));
    }

    @Test
    public void questLock_benignDialogueReturnsFalse() {
        assertFalse(Rs2Walker.hasQuestLockKeywords("Hello there, adventurer!"));
        assertFalse(Rs2Walker.hasQuestLockKeywords("Would you like to trade?"));
        assertFalse(Rs2Walker.hasQuestLockKeywords("Click to continue"));
    }

    @Test
    public void questLock_commonGatingPhrasesReturnTrue() {
        assertTrue(Rs2Walker.hasQuestLockKeywords("You need to have completed Cook's Assistant."));
        assertTrue(Rs2Walker.hasQuestLockKeywords("You must first finish the quest."));
        assertTrue(Rs2Walker.hasQuestLockKeywords("You have not yet proven yourself."));
        assertTrue(Rs2Walker.hasQuestLockKeywords("You cannot enter until you're a member."));
        assertTrue(Rs2Walker.hasQuestLockKeywords("You can't enter without the key."));
        assertTrue(Rs2Walker.hasQuestLockKeywords("This area requires you to have level 50 Agility."));
    }

    @Test
    public void questLock_isCaseInsensitive() {
        assertTrue(Rs2Walker.hasQuestLockKeywords("YOU MUST COMPLETE THE QUEST"));
        assertTrue(Rs2Walker.hasQuestLockKeywords("you Need To finish first"));
    }

    @Test
    public void questLock_detectsBareQuestMention() {
        // The standalone "quest" keyword is a last-resort safety net — gate dialogues
        // almost always include it even when phrasing is unusual.
        assertTrue(Rs2Walker.hasQuestLockKeywords("Only those who have finished the holy quest may pass."));
    }

    // ---------------------------------------------------------------------------
    // Session blacklist invariants (#19 support)
    // ---------------------------------------------------------------------------

    @Test
    public void sessionBlacklist_addAndMembership() {
        WorldPoint door = new WorldPoint(3210, 3220, 0);
        assertFalse(Rs2Walker.sessionBlacklistedDoors.contains(door));
        Rs2Walker.sessionBlacklistedDoors.add(door);
        assertTrue(Rs2Walker.sessionBlacklistedDoors.contains(door));
    }

    @Test
    public void sessionBlacklist_worldPointEqualityDrivesMembership() {
        // Two WorldPoints built from the same coords must hash/equal the same way —
        // otherwise the blacklist guard at handleDoors entry would miss re-attempts.
        Rs2Walker.sessionBlacklistedDoors.add(new WorldPoint(3210, 3220, 0));
        assertTrue(Rs2Walker.sessionBlacklistedDoors.contains(new WorldPoint(3210, 3220, 0)));
        assertFalse(Rs2Walker.sessionBlacklistedDoors.contains(new WorldPoint(3210, 3221, 0)));
        assertFalse("different plane must not collide",
                Rs2Walker.sessionBlacklistedDoors.contains(new WorldPoint(3210, 3220, 1)));
    }

    // ---------------------------------------------------------------------------
    // #22 — UNREACHABLE telemetry
    // ---------------------------------------------------------------------------

    @Test
    public void telemetry_recordUnreachable_incrementsCounterAndSetsReason() {
        assertEquals(0, Rs2Walker.Telemetry.unreachableCount.get());
        assertEquals("", Rs2Walker.Telemetry.lastReason);

        Rs2Walker.Telemetry.recordUnreachable("no-walkable-path",
                new WorldPoint(3200, 3200, 0), new WorldPoint(3300, 3300, 0),
                new WorldPoint(3250, 3250, 0), 42, 2, null);

        assertEquals(1, Rs2Walker.Telemetry.unreachableCount.get());
        assertEquals("unreachable:no-walkable-path", Rs2Walker.Telemetry.lastReason);
        assertNotSame("lastEventAtMs must have been stamped", 0L,
                Rs2Walker.Telemetry.lastEventAtMs.get());
    }

    @Test
    public void telemetry_recordUnreachable_nullPathfinderDoesNotThrow() {
        Rs2Walker.Telemetry.recordUnreachable("partial-retries-exhausted",
                null, null, null, 0, 2, null);
        assertEquals(1, Rs2Walker.Telemetry.unreachableCount.get());
    }

    @Test
    public void telemetry_recordUnreachable_withPathfinderReadsStats() {
        Pathfinder pathfinder = mock(Pathfinder.class);
        Pathfinder.PathfinderStats stats = new Pathfinder.PathfinderStats();
        when(pathfinder.getStats()).thenReturn(stats);

        Rs2Walker.Telemetry.recordUnreachable("no-walkable-path",
                new WorldPoint(3200, 3200, 0), new WorldPoint(3201, 3201, 0),
                null, 0, 0, pathfinder);

        verify(pathfinder).getStats();
        assertEquals(1, Rs2Walker.Telemetry.unreachableCount.get());
    }

    @Test
    public void telemetry_counterIsIndependentOfOtherReasons() {
        Rs2Walker.Telemetry.recordOffPathRecalc(new WorldPoint(3200, 3200, 0), 10);
        Rs2Walker.Telemetry.recordStallRecalc(11_000L, new WorldPoint(3200, 3200, 0));
        Rs2Walker.Telemetry.recordPartialRetry(1, 5);

        assertEquals("unreachable counter must not move for other events",
                0, Rs2Walker.Telemetry.unreachableCount.get());
        assertEquals(1, Rs2Walker.Telemetry.offPathRecalcCount.get());
        assertEquals(1, Rs2Walker.Telemetry.stallRecalcCount.get());
        assertEquals(1, Rs2Walker.Telemetry.partialRetryCount.get());
    }

    @Test
    public void telemetry_recordOffPathRecalcDeferred_setsReasonButDoesNotCountAsRecalc() {
        Rs2Walker.Telemetry.recordOffPathRecalcDeferred("route-progress",
                new WorldPoint(3200, 3200, 0),
                new WorldPoint(3210, 3210, 0),
                20);

        assertEquals(1, Rs2Walker.Telemetry.offPathRecalcDeferredCount.get());
        assertEquals("off-path-deferred:route-progress", Rs2Walker.Telemetry.lastReason);
        assertEquals(0, Rs2Walker.Telemetry.totalRecalcs());
    }

    @Test
    public void telemetry_reset_clearsUnreachable() {
        Rs2Walker.Telemetry.recordUnreachable("no-walkable-path",
                new WorldPoint(0, 0, 0), new WorldPoint(1, 1, 0), null, 0, 0, null);
        Rs2Walker.Telemetry.recordUnreachable("partial-retries-exhausted",
                new WorldPoint(0, 0, 0), new WorldPoint(1, 1, 0), null, 0, 0, null);
        assertEquals(2, Rs2Walker.Telemetry.unreachableCount.get());

        Rs2Walker.Telemetry.reset();

        assertEquals(0, Rs2Walker.Telemetry.unreachableCount.get());
        assertEquals("", Rs2Walker.Telemetry.lastReason);
    }

    @Test
    public void telemetry_totalRecalcs_doesNotIncludeUnreachable() {
        // totalRecalcs() feeds health dashboards — an UNREACHABLE is a terminal state,
        // not a recalc, so it must be counted separately.
        Rs2Walker.Telemetry.recordOffPathRecalc(new WorldPoint(3200, 3200, 0), 5);
        Rs2Walker.Telemetry.recordUnreachable("no-walkable-path",
                new WorldPoint(0, 0, 0), new WorldPoint(1, 1, 0), null, 0, 0, null);

        assertEquals(1, Rs2Walker.Telemetry.totalRecalcs());
    }

    @Test
    public void walkUntil_immediatelySatisfiedConditionReturnsArrivedWithoutWalkerSetup() {
        WorldPoint target = new WorldPoint(3200, 3200, 0);

        assertTrue(Rs2Walker.walkUntil(target, 2, () -> true));
        assertEquals(WalkerState.ARRIVED,
                Rs2Walker.walkWithStateUntil(target, 2, () -> true));
    }

    @Test
    public void walkUntil_failedConditionFallsBackToNormalWalkerResult() {
        WorldPoint target = new WorldPoint(3200, 3200, 0);

        assertFalse(Rs2Walker.walkUntil(target, 2, () -> {
            throw new IllegalStateException("test condition failure");
        }));
    }

    @Test(expected = NullPointerException.class)
    public void walkUntil_rejectsNullCondition() {
        Rs2Walker.walkUntil(new WorldPoint(3200, 3200, 0), 2, null);
    }
}
