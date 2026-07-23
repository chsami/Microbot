package net.runelite.client.plugins.microbot.shortestpath;

import net.runelite.api.CollisionDataFlag;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.CollisionMap;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.SplitFlagMap;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.live.LiveCollisionCapture;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.live.LiveCollisionOverlay;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.live.LiveCollisionSnapshot;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.live.LiveRouteValidator;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static net.runelite.api.Constants.SCENE_SIZE;
import static net.runelite.client.plugins.microbot.shortestpath.pathfinder.live.LiveCollisionSnapshot.FLAG_EAST;
import static net.runelite.client.plugins.microbot.shortestpath.pathfinder.live.LiveCollisionSnapshot.FLAG_NORTH;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Stage 1 hybrid live-collision coverage: the pure flag translation in {@link LiveCollisionCapture#build},
 * the snapshot's edge/validity semantics, and that {@link CollisionMap} lets an enabled overlay win inside
 * the captured scene while falling back to the static map everywhere else. With no overlay, a
 * {@code CollisionMap} must read byte-for-byte as the static-only map — the guard that keeps every other
 * shortestpath test deterministic.
 */
public class LiveCollisionTest {

    private static final int BASE_X = 3200;
    private static final int BASE_Y = 3200;

    private static SplitFlagMap staticFlags;

    @BeforeClass
    public static void loadCollisionMap() {
        staticFlags = SplitFlagMap.fromResources();
    }

    // ---- pure translation (no client, no static map) ----

    private static int[][][] openScene() {
        // One plane, all tiles standable, no walls -> every interior edge open.
        return new int[][][]{new int[SCENE_SIZE][SCENE_SIZE]};
    }

    @Test
    public void openScene_interiorEdgesOpen_rimUnknown() {
        LiveCollisionSnapshot snap = LiveCollisionCapture.build(BASE_X, BASE_Y, 1, openScene());

        // interior tile: both edges known and open
        assertEquals(Boolean.TRUE, snap.edge(BASE_X + 10, BASE_Y + 10, 0, FLAG_NORTH));
        assertEquals(Boolean.TRUE, snap.edge(BASE_X + 10, BASE_Y + 10, 0, FLAG_EAST));

        // north rim: north neighbour is outside the scene -> unknown -> null (static fallback)
        assertNull(snap.edge(BASE_X + 10, BASE_Y + SCENE_SIZE - 1, 0, FLAG_NORTH));
        // east rim: east neighbour outside -> unknown
        assertNull(snap.edge(BASE_X + SCENE_SIZE - 1, BASE_Y + 10, 0, FLAG_EAST));

        // entirely outside the scene -> null
        assertNull(snap.edge(BASE_X - 5, BASE_Y + 10, 0, FLAG_NORTH));
        assertNull(snap.edge(BASE_X + 10, BASE_Y + 10, 1, FLAG_NORTH)); // plane 1 not captured
    }

    @Test
    public void wallBitBlocksTheEdge() {
        int[][][] flags = openScene();
        flags[0][10][10] |= CollisionDataFlag.BLOCK_MOVEMENT_NORTH;
        flags[0][20][20] |= CollisionDataFlag.BLOCK_MOVEMENT_EAST;

        LiveCollisionSnapshot snap = LiveCollisionCapture.build(BASE_X, BASE_Y, 1, flags);

        assertEquals(Boolean.FALSE, snap.edge(BASE_X + 10, BASE_Y + 10, 0, FLAG_NORTH));
        assertEquals(Boolean.FALSE, snap.edge(BASE_X + 20, BASE_Y + 20, 0, FLAG_EAST));
        // the untouched edge of the same tile is still open
        assertEquals(Boolean.TRUE, snap.edge(BASE_X + 10, BASE_Y + 10, 0, FLAG_EAST));
    }

    @Test
    public void wallIsSymmetric_neighbourSouthBitAlsoBlocks() {
        // A south wall on the north tile must block the same edge, since s(x,y)=n(x,y-1).
        int[][][] flags = openScene();
        flags[0][30][31] |= CollisionDataFlag.BLOCK_MOVEMENT_SOUTH; // south wall on the northern tile

        LiveCollisionSnapshot snap = LiveCollisionCapture.build(BASE_X, BASE_Y, 1, flags);

        // north edge of (30,30) connects (30,30)<->(30,31); the south wall on (30,31) closes it
        assertEquals(Boolean.FALSE, snap.edge(BASE_X + 30, BASE_Y + 30, 0, FLAG_NORTH));
    }

    @Test
    public void sceneBorderIsUnknown_soBoundaryArtifactsFallBackToStatic() {
        // RuneLite's collision for the scene's outer ring is unreliable (verified live: a uniform band
        // of false walls ~5 tiles deep). The capture leaves that border unknown -> static fallback.
        int[][][] flags = openScene();
        // put a "wall" both deep in the interior and out at the border
        flags[0][50][50] |= CollisionDataFlag.BLOCK_MOVEMENT_NORTH; // interior
        flags[0][2][50] |= CollisionDataFlag.BLOCK_MOVEMENT_NORTH;  // border (sx=2, within margin 8)

        LiveCollisionSnapshot snap = LiveCollisionCapture.build(BASE_X, BASE_Y, 1, flags);

        // interior wall is captured
        assertEquals(Boolean.FALSE, snap.edge(BASE_X + 50, BASE_Y + 50, 0, FLAG_NORTH));
        // border tile is unknown regardless of its flags -> defer to static
        assertNull(snap.edge(BASE_X + 2, BASE_Y + 50, 0, FLAG_NORTH));
        // a tile just inside the margin is known again
        assertEquals(Boolean.TRUE, snap.edge(BASE_X + 8, BASE_Y + 50, 0, FLAG_NORTH));
        // a tile just outside the margin is unknown
        assertNull(snap.edge(BASE_X + 7, BASE_Y + 50, 0, FLAG_NORTH));
    }

    @Test
    public void doorTileEdgesAreExemptEvenWhenLiveFlagsBlockThem() {
        // A closed door blocks its doorway in the live flags; the door mask must leave those edges
        // unknown so they fall back to the static map (passable) and the runtime door handler opens it.
        int[][][] flags = openScene();
        final int dx = 50, dy = 50;
        // simulate a closed door: walls on all four sides of the door tile
        flags[0][dx][dy] |= CollisionDataFlag.BLOCK_MOVEMENT_NORTH
                | CollisionDataFlag.BLOCK_MOVEMENT_EAST
                | CollisionDataFlag.BLOCK_MOVEMENT_SOUTH
                | CollisionDataFlag.BLOCK_MOVEMENT_WEST;

        boolean[][][] doorTile = new boolean[1][SCENE_SIZE][SCENE_SIZE];
        doorTile[0][dx][dy] = true;

        LiveCollisionSnapshot snap = LiveCollisionCapture.build(BASE_X, BASE_Y, 1, flags, doorTile);

        // every edge touching the door tile is unknown -> static fallback, not a fabricated wall
        assertNull(snap.edge(BASE_X + dx, BASE_Y + dy, 0, FLAG_NORTH));
        assertNull(snap.edge(BASE_X + dx, BASE_Y + dy, 0, FLAG_EAST));
        assertNull(snap.edge(BASE_X + dx, BASE_Y + dy - 1, 0, FLAG_NORTH)); // south edge (neighbour's north)
        assertNull(snap.edge(BASE_X + dx - 1, BASE_Y + dy, 0, FLAG_EAST));  // west edge (neighbour's east)

        // a tile two away from the door is unaffected and still overlaid normally
        assertEquals(Boolean.TRUE, snap.edge(BASE_X + dx + 2, BASE_Y + dy + 2, 0, FLAG_NORTH));
    }

    @Test
    public void nullDoorMask_behavesLikeNoDoors() {
        int[][][] flags = openScene();
        flags[0][60][60] |= CollisionDataFlag.BLOCK_MOVEMENT_NORTH;

        LiveCollisionSnapshot withNull = LiveCollisionCapture.build(BASE_X, BASE_Y, 1, flags, null);
        LiveCollisionSnapshot fourArg = LiveCollisionCapture.build(BASE_X, BASE_Y, 1, flags);

        assertEquals(Boolean.FALSE, withNull.edge(BASE_X + 60, BASE_Y + 60, 0, FLAG_NORTH));
        assertEquals(fourArg.edge(BASE_X + 60, BASE_Y + 60, 0, FLAG_NORTH),
                withNull.edge(BASE_X + 60, BASE_Y + 60, 0, FLAG_NORTH));
    }

    @Test
    public void fullBlockClosesAllFourEdgesAroundTheTile() {
        int[][][] flags = openScene();
        final int fx = 40, fy = 40;
        flags[0][fx][fy] |= CollisionDataFlag.BLOCK_MOVEMENT_FULL;

        LiveCollisionSnapshot snap = LiveCollisionCapture.build(BASE_X, BASE_Y, 1, flags);

        // the full tile's own outgoing north/east edges are closed
        assertEquals(Boolean.FALSE, snap.edge(BASE_X + fx, BASE_Y + fy, 0, FLAG_NORTH));
        assertEquals(Boolean.FALSE, snap.edge(BASE_X + fx, BASE_Y + fy, 0, FLAG_EAST));
        // and the incoming edges: south neighbour's north, west neighbour's east
        assertEquals(Boolean.FALSE, snap.edge(BASE_X + fx, BASE_Y + fy - 1, 0, FLAG_NORTH));
        assertEquals(Boolean.FALSE, snap.edge(BASE_X + fx - 1, BASE_Y + fy, 0, FLAG_EAST));
    }

    // ---- CollisionMap integration against the real static map ----

    @Test
    public void noOverlay_readsIdenticalToStaticMap() {
        CollisionMap plain = new CollisionMap(staticFlags);
        CollisionMap withEmptyOverlay = new CollisionMap(staticFlags, new LiveCollisionOverlay());
        withEmptyOverlay.beginSearch(); // overlay disabled -> pins null

        // sweep a mapped area and confirm every edge matches
        for (int x = 3200; x < 3260; x++) {
            for (int y = 3200; y < 3260; y++) {
                assertEquals(plain.n(x, y, 0), withEmptyOverlay.n(x, y, 0));
                assertEquals(plain.e(x, y, 0), withEmptyOverlay.e(x, y, 0));
                assertEquals(plain.isBlocked(x, y, 0), withEmptyOverlay.isBlocked(x, y, 0));
            }
        }
    }

    @Test
    public void overlayBlocksAnOpenStaticEdge_andFallsBackOutsideScene() {
        CollisionMap staticMap = new CollisionMap(staticFlags);

        // find an interior mapped tile with an open north edge in Lumbridge
        int tx = -1, ty = -1;
        for (int x = 3210; x < 3250 && tx < 0; x++) {
            for (int y = 3210; y < 3250; y++) {
                if (staticFlags.hasRegion(x, y) && staticMap.n(x, y, 0)) {
                    tx = x;
                    ty = y;
                    break;
                }
            }
        }
        assertTrue("expected an open north edge in Lumbridge", tx > 0);

        // snapshot centred so the target tile is interior (not on the unknown rim)
        final int baseX = tx - 52;
        final int baseY = ty - 52;
        int[][][] flags = openScene();
        flags[0][tx - baseX][ty - baseY] |= CollisionDataFlag.BLOCK_MOVEMENT_NORTH;

        LiveCollisionOverlay overlay = new LiveCollisionOverlay();
        overlay.setEnabled(true);
        overlay.set(LiveCollisionCapture.build(baseX, baseY, 1, flags));

        CollisionMap live = new CollisionMap(staticFlags, overlay);
        live.beginSearch();

        // overlay wins inside the scene
        assertTrue("precondition: static edge open", staticMap.n(tx, ty, 0));
        assertFalse("overlay must block the edge", live.n(tx, ty, 0));

        // a tile far outside the snapshot falls back to the static map
        int farX = baseX + 5000;
        int farY = baseY + 5000;
        assertEquals(staticMap.n(farX, farY, 0), live.n(farX, farY, 0));
        assertEquals(staticMap.e(farX, farY, 0), live.e(farX, farY, 0));
    }

    // ---- Stage 3: route validation (LiveRouteValidator) ----

    /** Overlay-blocks the north edge of {@code (tx,ty)} and returns a CollisionMap with it pinned. */
    private CollisionMap mapBlockingNorthEdge(int tx, int ty) {
        final int baseX = tx - 52, baseY = ty - 52;
        int[][][] flags = openScene();
        flags[0][tx - baseX][ty - baseY] |= CollisionDataFlag.BLOCK_MOVEMENT_NORTH;
        LiveCollisionOverlay overlay = new LiveCollisionOverlay();
        overlay.setEnabled(true);
        overlay.set(LiveCollisionCapture.build(baseX, baseY, 1, flags));
        CollisionMap map = new CollisionMap(staticFlags, overlay);
        map.beginSearch();
        return map;
    }

    /** A tile with a run of open north edges from y-2..y+1, so multi-step north paths are clear in static. */
    private int[] findOpenNorthEdgeTile() {
        CollisionMap staticMap = new CollisionMap(staticFlags);
        for (int x = 3200; x < 3260; x++) {
            for (int y = 3210; y < 3250; y++) {
                if (staticFlags.hasRegion(x, y)
                        && staticMap.n(x, y - 2, 0) && staticMap.n(x, y - 1, 0)
                        && staticMap.n(x, y, 0) && staticMap.n(x, y + 1, 0)) {
                    return new int[]{x, y};
                }
            }
        }
        throw new AssertionError("no run of open north edges found in Lumbridge");
    }

    @Test
    public void routeValidator_nearestIndex() {
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(3200, 3200, 0), new WorldPoint(3200, 3201, 0), new WorldPoint(3200, 3202, 0));
        assertEquals(1, LiveRouteValidator.nearestIndex(path, new WorldPoint(3200, 3201, 0)));
        assertEquals(2, LiveRouteValidator.nearestIndex(path, new WorldPoint(3204, 3202, 0)));
        assertEquals(0, LiveRouteValidator.nearestIndex(path, new WorldPoint(3200, 3201, 1))); // other plane ignored
    }

    @Test
    public void routeValidator_detectsLiveBlockedWalkingStep() {
        int[] t = findOpenNorthEdgeTile();
        int tx = t[0], ty = t[1];
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(tx, ty, 0), new WorldPoint(tx, ty + 1, 0), new WorldPoint(tx, ty + 2, 0));

        CollisionMap clear = new CollisionMap(staticFlags);
        clear.beginSearch();
        assertEquals("clear route", -1, LiveRouteValidator.firstBlockedStep(path, 0, 15, clear));

        CollisionMap blocked = mapBlockingNorthEdge(tx, ty);
        assertEquals("first step now blocked", 0, LiveRouteValidator.firstBlockedStep(path, 0, 15, blocked));
    }

    @Test
    public void routeValidator_skipsTransportJumps() {
        CollisionMap clear = new CollisionMap(staticFlags);
        clear.beginSearch();
        // a non-adjacent jump (walk transport) then a cross-plane jump (stairs) are not walking steps
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(3200, 3200, 0),
                new WorldPoint(3260, 3260, 0),
                new WorldPoint(3260, 3260, 1));
        assertEquals(-1, LiveRouteValidator.firstBlockedStep(path, 0, 15, clear));
    }

    @Test
    public void routeValidator_respectsLookahead() {
        int[] t = findOpenNorthEdgeTile();
        int tx = t[0], ty = t[1];
        // blocked edge is the step from index 2 -> 3
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(tx, ty - 2, 0), new WorldPoint(tx, ty - 1, 0),
                new WorldPoint(tx, ty, 0), new WorldPoint(tx, ty + 1, 0));
        CollisionMap blocked = mapBlockingNorthEdge(tx, ty);

        assertEquals("lookahead too short to see the block", -1,
                LiveRouteValidator.firstBlockedStep(path, 0, 2, blocked));
        assertEquals("longer lookahead finds it at index 2", 2,
                LiveRouteValidator.firstBlockedStep(path, 0, 3, blocked));
    }

    @Test
    public void disabledOverlaySnapshot_isIgnored() {
        int[][][] flags = openScene();
        LiveCollisionOverlay overlay = new LiveCollisionOverlay();
        // set() while disabled must not take effect
        overlay.set(LiveCollisionCapture.build(3148, 3148, 1, flags));
        assertNull(overlay.current());

        CollisionMap staticMap = new CollisionMap(staticFlags);
        CollisionMap live = new CollisionMap(staticFlags, overlay);
        live.beginSearch();
        for (int x = 3200; x < 3210; x++) {
            for (int y = 3200; y < 3210; y++) {
                assertEquals(staticMap.n(x, y, 0), live.n(x, y, 0));
                assertEquals(staticMap.e(x, y, 0), live.e(x, y, 0));
            }
        }
    }
}
