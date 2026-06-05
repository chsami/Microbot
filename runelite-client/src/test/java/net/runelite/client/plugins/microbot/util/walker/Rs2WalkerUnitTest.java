package net.runelite.client.plugins.microbot.util.walker;

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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
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
        int idx = Rs2Walker.findFurthestClickableIndex(path, 0, new WorldPoint(3200, 3200, 0),
                wp -> false, 14);
        assertEquals(0, idx);
    }

    @Test
    public void findFurthest_outOfBoundsStart_returnsStartUnchanged() {
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(3200, 3200, 0), new WorldPoint(3201, 3200, 0));
        assertEquals(-1, Rs2Walker.findFurthestClickableIndex(path, -1, new WorldPoint(3200, 3200, 0),
                wp -> false, 14));
        assertEquals(5, Rs2Walker.findFurthestClickableIndex(path, 5, new WorldPoint(3200, 3200, 0),
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

        int idx = Rs2Walker.findFurthestClickableIndex(path, 1, player, wp -> false, 14);

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

        int idx = Rs2Walker.findFurthestClickableIndex(path, 1, player, isTransportOrigin, 14);

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

        int idx = Rs2Walker.findFurthestClickableIndex(path, 0, player, wp -> false, 14);

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

        int idx = Rs2Walker.findFurthestClickableIndex(diagonalPath, 0, player, wp -> false, 14);

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

        int idx = Rs2Walker.findFurthestClickableIndex(path, 0, player, null, 14);

        assertEquals("null predicate must not NPE and must allow full scan", 2, idx);
    }

    @Test
    public void interpolateClickableTarget_usesInterpolatedPointWhenUsable() {
        WorldPoint player = new WorldPoint(3200, 3200, 0);
        WorldPoint fallback = new WorldPoint(3206, 3200, 0);
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(3200, 3200, 0),
                fallback,
                new WorldPoint(3220, 3200, 0));

        WorldPoint target = Rs2Walker.interpolateClickableTarget(path, 2, player, fallback, 12, wp -> true);

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

        WorldPoint target = Rs2Walker.interpolateClickableTarget(path, 2, player, fallback, 12, wp -> false);

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

        WorldPoint target = Rs2Walker.interpolateClickableTarget(path, 1, player, forward, 12, wp -> true);

        assertEquals("out-of-minimap forward waypoints should be shortened to a clickable tile",
                new WorldPoint(3212, 3200, 0), target);
    }

    // ---------------------------------------------------------------------------
    // Raw-path wall-door segment probing
    // ---------------------------------------------------------------------------

    @Test
    public void wallDoorTouchesSegment_crossingDoorEdge_returnsTrue() {
        WallObject door = mock(WallObject.class);
        when(door.getWorldLocation()).thenReturn(new WorldPoint(3123, 3361, 0));
        when(door.getOrientationA()).thenReturn(8); // south-facing door edge

        assertTrue(Rs2Walker.wallDoorTouchesSegment(door,
                new WorldPoint(3123, 3361, 0),
                new WorldPoint(3123, 3360, 0)));
        assertTrue(Rs2Walker.wallDoorTouchesSegment(door,
                new WorldPoint(3123, 3360, 0),
                new WorldPoint(3123, 3361, 0)));
    }

    @Test
    public void wallDoorTouchesSegment_startingBesideDoorAndMovingAway_returnsFalse() {
        WallObject door = mock(WallObject.class);
        when(door.getWorldLocation()).thenReturn(new WorldPoint(3123, 3361, 0));
        when(door.getOrientationA()).thenReturn(8); // door blocks 3123,3361 <-> 3123,3360

        assertFalse("standing on the door's south neighbor and walking southwest must not re-open the door",
                Rs2Walker.wallDoorTouchesSegment(door,
                        new WorldPoint(3123, 3360, 0),
                        new WorldPoint(3122, 3359, 0)));
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

    // ---------------------------------------------------------------------------
    // Walking-to-door state — far gate/door fix
    //
    // Root cause: clicking a gate/door several tiles away makes the server walk the
    // player there and open it in one action; the walker was cancelling that with
    // recovery clicks and then falsely suppressing the door for 10s. These pin the
    // pure decision logic of the fix (deadline scaling, progress window, segment match).
    // ---------------------------------------------------------------------------

    @Test
    public void walkToDoorDeadline_scalesWithDistance() {
        long now = 1_000_000L;
        assertEquals("distance 0 floors at base", now + 2_000L, Rs2Walker.walkToDoorDeadlineMs(now, 0));
        assertEquals("base 2000 + 5*700", now + 2_000L + 5 * 700L, Rs2Walker.walkToDoorDeadlineMs(now, 5));
    }

    @Test
    public void walkToDoorDeadline_capsAtMax() {
        long now = 1_000_000L;
        // 2000 + 100*700 = 72000, must cap at 12000 so a bad/unknown distance can't pin the walker.
        assertEquals(now + 12_000L, Rs2Walker.walkToDoorDeadlineMs(now, 100));
    }

    @Test
    public void walkToDoorDeadline_negativeDistanceFloorsAtBase() {
        long now = 1_000_000L;
        assertEquals("negative/unknown distance must not underflow the deadline",
                now + 2_000L, Rs2Walker.walkToDoorDeadlineMs(now, -5));
    }

    @Test
    public void walkToDoorWindow_openWhileBeforeDeadlineAndProgressing() {
        long now = 10_000L;
        assertTrue(Rs2Walker.walkToDoorWindowOpen(now, now + 1, now, 3_000L));
        assertTrue("recent progress (2s ago, < 3s budget) keeps the window open",
                Rs2Walker.walkToDoorWindowOpen(now, now + 5_000L, now - 2_000L, 3_000L));
    }

    @Test
    public void walkToDoorWindow_closedAfterDeadline() {
        long now = 10_000L;
        assertFalse("past the hard deadline the window must close so normal recovery resumes",
                Rs2Walker.walkToDoorWindowOpen(now, now - 1, now, 3_000L));
    }

    @Test
    public void walkToDoorWindow_closedAfterNoProgressTimeout() {
        long now = 10_000L;
        assertFalse("no progress for 3.5s (> 3s budget) must close the window even before the deadline",
                Rs2Walker.walkToDoorWindowOpen(now, now + 10_000L, now - 3_500L, 3_000L));
    }

    @Test
    public void doorTileNearSegment_withinTwoOfEitherEndpoint() {
        WorldPoint door = new WorldPoint(3189, 3275, 0);
        assertTrue("door 2 tiles from 'from' endpoint is on the segment",
                Rs2Walker.doorTileNearSegment(door,
                        new WorldPoint(3189, 3277, 0), new WorldPoint(3189, 3274, 0), 2));
    }

    @Test
    public void doorTileNearSegment_rejectsFarSegmentWrongPlaneAndNull() {
        WorldPoint door = new WorldPoint(3189, 3275, 0);
        assertFalse("segment ~10 tiles away is not this door's segment",
                Rs2Walker.doorTileNearSegment(door,
                        new WorldPoint(3189, 3285, 0), new WorldPoint(3189, 3284, 0), 2));
        assertFalse("same coords on another plane must not match",
                Rs2Walker.doorTileNearSegment(door,
                        new WorldPoint(3189, 3276, 1), new WorldPoint(3189, 3274, 1), 2));
        assertFalse("null door tile must not NPE",
                Rs2Walker.doorTileNearSegment(null,
                        new WorldPoint(3189, 3276, 0), new WorldPoint(3189, 3274, 0), 2));
    }

    @Test
    public void doorApproachDistance_takesNearerEndpointAndGuardsPlaneAndNull() {
        WorldPoint player = new WorldPoint(3187, 3285, 0);
        WorldPoint door = new WorldPoint(3189, 3275, 0);   // ~10 away
        WorldPoint from = new WorldPoint(3188, 3281, 0);   // ~4 away (nearer)
        assertEquals("uses the nearer of door/from",
                player.distanceTo2D(from), Rs2Walker.doorApproachDistance(player, door, from));
        assertEquals("null player must yield MAX_VALUE, not NPE",
                Integer.MAX_VALUE, Rs2Walker.doorApproachDistance(null, door, from));
        assertEquals("different-plane door+from must be ignored -> MAX_VALUE (no false 'adjacent')",
                Integer.MAX_VALUE,
                Rs2Walker.doorApproachDistance(player,
                        new WorldPoint(3189, 3275, 1), new WorldPoint(3188, 3281, 1)));
    }
}
