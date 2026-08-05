package net.runelite.client.plugins.microbot.util.walker.geometry;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Headless tests for the pure {@link WalkerPathGeometry} route helpers extracted from {@code Rs2Walker}
 * (P1). These are the switch-back guards for recovery target selection: {@code rawPathForwardAnchorIndex}
 * must never snap the anchor backward, and {@code findFurthestRawPathPointMatching} must respect both the
 * Euclidean radius and the along-route step budget. The game-coupled reachable-closest fallback is injected
 * as a lambda, so we also assert it stays lazy (invoked only when no anchor is known).
 */
public class WalkerPathGeometryTest {

    private static final int FORWARD_SEARCH_TILES = 40;

    private static WorldPoint wp(int x, int y) {
        return new WorldPoint(x, y, 0);
    }

    /** A straight west-to-east run of {@code length} tiles starting at {@code (startX, y)}. */
    private static List<WorldPoint> straightRun(int startX, int y, int length) {
        List<WorldPoint> path = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            path.add(wp(startX + i, y));
        }
        return path;
    }

    // --- rawPathForwardAnchorIndex ------------------------------------------------------------------

    @Test
    public void anchorForwardIndexPicksClosestWithinWindowAndNeverGoesBackward() {
        List<WorldPoint> path = straightRun(3200, 3200, 60);
        WorldPoint player = wp(3210, 3200); // physically closest to index 10
        // Anchor at 5: the window [5, 45] contains index 10 (the true closest ahead); must return 10.
        int idx = WalkerPathGeometry.rawPathForwardAnchorIndex(path, player, 5, FORWARD_SEARCH_TILES, failFallback());
        assertEquals(10, idx);
    }

    @Test
    public void anchorForwardIndexDoesNotSnapBehindTheAnchorOnASwitchback() {
        // A route that runs east, then folds back west on a parallel row (a switch-back). The player sits on
        // the return leg, Euclidean-near an early outbound tile — but the anchor is on the return leg, and we
        // must stay forward of it rather than snapping back to the outbound tile.
        List<WorldPoint> path = new ArrayList<>();
        for (int i = 0; i < 20; i++) path.add(wp(3200 + i, 3200)); // outbound east, idx 0..19
        for (int i = 0; i < 20; i++) path.add(wp(3219 - i, 3201)); // return west one row north, idx 20..39
        // Player sits on outbound idx 5's tile (Euclidean-near the early outbound leg) but offset from the
        // return-leg anchor tile path.get(34)=(3205,3201), so this exercises the forward-only guard rather
        // than a trivial exact-anchor (dist 0) match.
        WorldPoint player = wp(3205, 3200);
        int anchor = 34; // on the return leg
        int idx = WalkerPathGeometry.rawPathForwardAnchorIndex(path, player, anchor, FORWARD_SEARCH_TILES, failFallback());
        // Must stay at or ahead of the anchor, never snap back to the Euclidean-near outbound tile ~idx 5.
        assertTrue("stays forward of the switch-back anchor", idx >= anchor);
    }

    @Test
    public void anchorForwardIndexUsesFallbackOnlyWhenNoAnchor() {
        List<WorldPoint> path = straightRun(3200, 3200, 10);
        AtomicInteger calls = new AtomicInteger();
        int idx = WalkerPathGeometry.rawPathForwardAnchorIndex(path, wp(3203, 3200), -1, FORWARD_SEARCH_TILES,
                () -> {
                    calls.incrementAndGet();
                    return 7;
                });
        assertEquals(7, idx);
        assertEquals("fallback invoked exactly once for anchor < 0", 1, calls.get());
    }

    @Test
    public void anchorForwardIndexNeverInvokesFallbackWhenAnchorKnown() {
        List<WorldPoint> path = straightRun(3200, 3200, 10);
        WalkerPathGeometry.rawPathForwardAnchorIndex(path, wp(3203, 3200), 2, FORWARD_SEARCH_TILES, failFallback());
        // failFallback() throws if invoked; reaching here means the lazy fallback stayed untouched.
    }

    // --- findFurthestRawPathPointMatching -----------------------------------------------------------

    @Test
    public void furthestMatchingReturnsFarthestPointWithinEuclideanRadius() {
        List<WorldPoint> path = straightRun(3200, 3200, 30);
        WorldPoint player = wp(3200, 3200);
        WorldPoint got = WalkerPathGeometry.findFurthestRawPathPointMatching(path, player, 10, 0,
                p -> true, FORWARD_SEARCH_TILES, failFallback());
        assertEquals(wp(3210, 3200), got); // 10 tiles east — the radius edge
    }

    @Test
    public void furthestMatchingHonorsCandidatePredicate() {
        List<WorldPoint> path = straightRun(3200, 3200, 30);
        WorldPoint player = wp(3200, 3200);
        // Only accept tiles up to x=3205; the furthest *matching* point is then 3205 even though 3210 is in range.
        WorldPoint got = WalkerPathGeometry.findFurthestRawPathPointMatching(path, player, 10, 0,
                p -> p.getX() <= 3205, FORWARD_SEARCH_TILES, failFallback());
        assertEquals(wp(3205, 3200), got);
    }

    @Test
    public void furthestMatchingNeverReturnsThePlayerTile() {
        List<WorldPoint> path = straightRun(3200, 3200, 1); // only the player's own tile
        WorldPoint got = WalkerPathGeometry.findFurthestRawPathPointMatching(path, wp(3200, 3200), 10, 0,
                p -> true, FORWARD_SEARCH_TILES, failFallback());
        assertNull(got);
    }

    // The along-route step-budget cutoff (which excludes switch-back fold tiles that are Euclidean-near but
    // many route steps away) is exercised through the Rs2Walker wrapper in Rs2WalkerUnitTest, so it is not
    // re-asserted here — these tests focus on the pure method's radius/predicate/anchor behavior.

    // --- route-blocked scan gate (closed door / wall ON the route) -----------------------------------

    /** Player-origin BFS map over the given tiles, values = along-route step counts (contents irrelevant). */
    private static java.util.Map<WorldPoint, Integer> bfs(List<WorldPoint> tiles) {
        java.util.Map<WorldPoint, Integer> m = new java.util.HashMap<>();
        for (int i = 0; i < tiles.size(); i++) {
            m.put(tiles.get(i), i);
        }
        return m;
    }

    @Test
    public void gateStopsScanAtClosedDoorAndReturnsNearSide() {
        List<WorldPoint> path = straightRun(3200, 3200, 12);
        WorldPoint player = wp(3200, 3200); // on the anchor
        // Door between idx 3 and 4: BFS from the player only reaches tiles 0..3.
        java.util.Map<WorldPoint, Integer> reachable = bfs(path.subList(0, 4));
        WorldPoint got = WalkerPathGeometry.findFurthestRawPathPointMatching(path, player, 10, 0,
                p -> true, FORWARD_SEARCH_TILES, failFallback(), reachable, 20);
        assertEquals("selection must stop at the door's near side, not click the far side",
                wp(3203, 3200), got);
    }

    @Test
    public void gateIgnoresAbsenceBeyondTheBfsBudget() {
        List<WorldPoint> path = straightRun(3200, 3200, 12);
        WorldPoint player = wp(3200, 3200);
        // Budget 5 -> the gate can only vouch for candidates within 3 route steps; farther tiles absent
        // from the BFS are budget false-negatives and must NOT stop the scan (old trusting behavior).
        java.util.Map<WorldPoint, Integer> reachable = bfs(path.subList(0, 4)); // tiles 0..3 present
        WorldPoint got = WalkerPathGeometry.findFurthestRawPathPointMatching(path, player, 10, 0,
                p -> true, FORWARD_SEARCH_TILES, failFallback(), reachable, 5);
        assertEquals("beyond the budget the scan trusts the route again", wp(3210, 3200), got);
    }

    @Test
    public void gateInactiveWhenAnchorNotAtPlayer() {
        List<WorldPoint> path = straightRun(3200, 3200, 12);
        WorldPoint player = wp(3200, 3205); // 5 tiles off the anchor tile -> BFS origin != anchor
        java.util.Map<WorldPoint, Integer> reachable = bfs(path.subList(0, 2));
        WorldPoint got = WalkerPathGeometry.findFurthestRawPathPointMatching(path, player, 10, 0,
                p -> true, FORWARD_SEARCH_TILES, failFallback(), reachable, 20);
        // Gate off -> plain radius-bounded selection (furthest within Euclidean 10 of the player).
        org.junit.Assert.assertNotNull(got);
        assertTrue("gate must not fire when the anchor is away from the BFS origin",
                got.getX() > 3201);
    }

    @Test
    public void gateInactiveOnEmptyOrNullCache() {
        List<WorldPoint> path = straightRun(3200, 3200, 12);
        WorldPoint player = wp(3200, 3200);
        assertEquals(wp(3210, 3200), WalkerPathGeometry.findFurthestRawPathPointMatching(path, player, 10, 0,
                p -> true, FORWARD_SEARCH_TILES, failFallback(), new java.util.HashMap<>(), 20));
        assertEquals(wp(3210, 3200), WalkerPathGeometry.findFurthestRawPathPointMatching(path, player, 10, 0,
                p -> true, FORWARD_SEARCH_TILES, failFallback(), null, 20));
    }

    // --- findReachableRejoinRawPathPoint ------------------------------------------------------------

    @Test
    public void rejoinPrefersFurthestForwardReachablePoint() {
        List<WorldPoint> path = straightRun(3200, 3200, 20);
        WorldPoint player = wp(3205, 3200); // anchor 5
        // Everything reachable: highest index within the window/radius wins (forward preference).
        WorldPoint got = WalkerPathGeometry.findReachableRejoinRawPathPoint(path, player, 4, 5,
                p -> true, FORWARD_SEARCH_TILES, failFallback());
        assertEquals(wp(3209, 3200), got); // anchor 5 + window edge within radius 4
    }

    @Test
    public void rejoinFallsBackBehindAnchorWhenNothingAheadIsReachable() {
        List<WorldPoint> path = straightRun(3200, 3200, 20);
        WorldPoint player = wp(3205, 3200); // anchor 5
        // Only a tile behind the anchor is reachable: the two-sided window must step backward onto it.
        WorldPoint behind = wp(3203, 3200);
        WorldPoint got = WalkerPathGeometry.findReachableRejoinRawPathPoint(path, player, 4, 5,
                behind::equals, FORWARD_SEARCH_TILES, failFallback());
        assertEquals(behind, got);
    }

    // --- rawIndexForSmoothedIndex -------------------------------------------------------------------

    @Test
    public void smoothedIndexMapsThroughTableWithoutFallback() {
        List<WorldPoint> path = straightRun(3200, 3200, 10);
        int[] smoothedToRaw = {0, 3, 7};
        int raw = WalkerPathGeometry.rawIndexForSmoothedIndex(1, smoothedToRaw, path, failFallback());
        assertEquals(3, raw);
    }

    @Test
    public void smoothedIndexClampsTableValueIntoRange() {
        List<WorldPoint> path = straightRun(3200, 3200, 5); // valid indices 0..4
        int[] smoothedToRaw = {0, 99}; // 99 out of range -> clamp to 4
        assertEquals(4, WalkerPathGeometry.rawIndexForSmoothedIndex(1, smoothedToRaw, path, failFallback()));
    }

    @Test
    public void smoothedIndexUsesFallbackWhenTableCannotMap() {
        List<WorldPoint> path = straightRun(3200, 3200, 10);
        AtomicInteger calls = new AtomicInteger();
        int raw = WalkerPathGeometry.rawIndexForSmoothedIndex(5, null, path, () -> {
            calls.incrementAndGet();
            return 4;
        });
        assertEquals(4, raw);
        assertEquals(1, calls.get());
    }

    // --- anchor fold-jump protection (Clock Tower: route tail folds back beside the start) ----------

    /**
     * The Clock Tower shape: the goal is 3 tiles from the start but wall-separated; the planned route loops
     * away (east) and returns on the far side, so its TAIL tiles are Euclidean-closest to the player.
     * Outward-leg tiles are walk-connected (in the BFS); tail tiles are not.
     */
    private static List<WorldPoint> foldedRoute() {
        List<WorldPoint> path = new ArrayList<>();
        for (int i = 0; i <= 12; i++) path.add(wp(3200 + i, 3200)); // outward east, idx 0..12
        for (int i = 1; i <= 3; i++) path.add(wp(3212, 3200 + i));  // up, idx 13..15
        for (int i = 1; i <= 12; i++) path.add(wp(3212 - i, 3203)); // tail folds back west, idx 16..27
        return path;
    }

    @Test
    public void anchorPrefersReachableWindowTileOverEuclideanNearFoldTail() {
        List<WorldPoint> path = foldedRoute();
        WorldPoint player = wp(3200, 3200); // at the route start; the tail's end is 3 tiles north, walled
        // Only the outward leg is walk-connected; the tail (behind the wall) is not.
        java.util.Map<WorldPoint, Integer> reachable = bfs(path.subList(0, 13));
        // Anchor window starts ahead of the player (idx 6, as when the loop index has advanced): the
        // fold-tail tiles (dist 3, through the wall) are Euclidean-closer than any outward window tile
        // (dist >= 6) — the old snap would leap the wall. With the BFS, the anchor must stay outward.
        int anchor = WalkerPathGeometry.rawPathForwardAnchorIndex(path, player, 6, FORWARD_SEARCH_TILES,
                failFallback(), reachable);
        assertTrue("anchor must stay on the walk-connected outward leg, not leap the wall to the tail",
                anchor >= 6 && anchor <= 12);
    }

    @Test
    public void anchorWithoutCacheStillSnapsEuclidean() {
        List<WorldPoint> path = foldedRoute();
        WorldPoint player = wp(3200, 3200);
        // Old behavior preserved when no cache is supplied: the Euclidean-closest window tile wins,
        // which from anchor 6 is a fold-tail tile right across the wall.
        int anchor = WalkerPathGeometry.rawPathForwardAnchorIndex(path, player, 6, FORWARD_SEARCH_TILES,
                failFallback(), null);
        assertTrue("without a BFS the legacy Euclidean snap remains", anchor >= 16);
    }

    @Test
    public void gatedSelectionStaysOnNearSideOfTheFold() {
        List<WorldPoint> path = foldedRoute();
        WorldPoint player = wp(3200, 3200);
        java.util.Map<WorldPoint, Integer> reachable = bfs(path.subList(0, 13));
        // End-to-end through the gated scan: even anchored past the player (idx 6), selection must come
        // back with an outward-leg tile — never a tail tile like the old "click the goal through the wall".
        WorldPoint got = WalkerPathGeometry.findFurthestRawPathPointMatching(path, player, 10, 6,
                p -> true, FORWARD_SEARCH_TILES, failFallback(), reachable, 20);
        org.junit.Assert.assertNotNull(got);
        assertTrue("selected tile must be on the outward leg (y=3200), not the fold tail",
                got.getY() == 3200);
    }

    /** A fallback that fails the test if ever invoked — proves the lazy path stayed untouched. */
    private static java.util.function.IntSupplier failFallback() {
        return () -> {
            throw new AssertionError("reachable-closest fallback must not be invoked when an anchor is known");
        };
    }
}
