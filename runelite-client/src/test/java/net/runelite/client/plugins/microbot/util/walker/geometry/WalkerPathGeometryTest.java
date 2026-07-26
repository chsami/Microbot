package net.runelite.client.plugins.microbot.util.walker.geometry;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
        WorldPoint player = wp(3205, 3201); // near outbound idx 5 (row 3200) but actually on return leg
        int anchor = 34; // on the return leg
        int idx = WalkerPathGeometry.rawPathForwardAnchorIndex(path, player, anchor, FORWARD_SEARCH_TILES, failFallback());
        // Must stay at or ahead of the anchor, never snap back to the Euclidean-near outbound tile ~idx 5.
        org.junit.Assert.assertEquals("stays forward of the switch-back anchor", true, idx >= anchor);
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

    /** A fallback that fails the test if ever invoked — proves the lazy path stayed untouched. */
    private static java.util.function.IntSupplier failFallback() {
        return () -> {
            throw new AssertionError("reachable-closest fallback must not be invoked when an anchor is known");
        };
    }
}
