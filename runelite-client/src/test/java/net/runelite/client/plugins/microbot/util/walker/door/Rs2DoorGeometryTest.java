package net.runelite.client.plugins.microbot.util.walker.door;

import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Headless tests for {@link Rs2DoorGeometry} — deciding whether a door/gate actually sits ON a path segment
 * (versus merely nearby) and whether it is within interaction range. This is the geometry the door recovery
 * relies on to avoid clicking unrelated doors; pinning it under test is part of the "harness first" work
 * ahead of folding door handling into the P2 obstacle model (docs/walker-p2-unification.md).
 */
public class Rs2DoorGeometryTest {

    private static WorldPoint wp(int x, int y) {
        return new WorldPoint(x, y, 0);
    }

    private static WorldPoint wp(int x, int y, int plane) {
        return new WorldPoint(x, y, plane);
    }

    /** A wall door at {@code tile} whose panel blocks the given orientations (0 = none on B). */
    private static WallObject wallDoor(WorldPoint tile, int orientationA, int orientationB) {
        WallObject w = mock(WallObject.class);
        when(w.getWorldLocation()).thenReturn(tile);
        when(w.getOrientationA()).thenReturn(orientationA);
        when(w.getOrientationB()).thenReturn(orientationB);
        return w;
    }

    // --- wallDoorTouchesSegment --------------------------------------------------------------------

    @Test
    public void wallDoorOnSegmentEdgeIsDetected() {
        // Door at (3200,3200) blocking its EAST edge (orientation 4 -> neighbor (3201,3200)).
        WallObject door = wallDoor(wp(3200, 3200), 4, 0);
        // A segment stepping across that exact edge must be detected.
        assertTrue(Rs2DoorGeometry.wallDoorTouchesSegment(door, wp(3200, 3200), wp(3201, 3200)));
    }

    @Test
    public void wallDoorNotOnSegmentIsIgnored() {
        // Same east-blocking door, but the segment steps NORTH — it never crosses the blocked east edge.
        WallObject door = wallDoor(wp(3200, 3200), 4, 0);
        assertFalse(Rs2DoorGeometry.wallDoorTouchesSegment(door, wp(3200, 3200), wp(3200, 3201)));
    }

    @Test
    public void wallDoorOnDifferentPlaneIsIgnored() {
        WallObject door = wallDoor(wp(3200, 3200, 0), 4, 0);
        assertFalse(Rs2DoorGeometry.wallDoorTouchesSegment(door, wp(3200, 3200, 1), wp(3201, 3200, 1)));
    }

    @Test
    public void wallDoorWithNoBlockingOrientationIsIgnored() {
        WallObject door = wallDoor(wp(3200, 3200), 0, 0); // neither orientation blocks anything
        assertFalse(Rs2DoorGeometry.wallDoorTouchesSegment(door, wp(3200, 3200), wp(3201, 3200)));
    }

    // --- isDoorOnSegment (non-wall object dispatch) ------------------------------------------------

    @Test
    public void nonWallObjectOnSegmentIsDetectedByProximity() {
        TileObject object = mock(TileObject.class);
        when(object.getWorldLocation()).thenReturn(wp(3202, 3200)); // sits on the segment
        assertTrue(Rs2DoorGeometry.isDoorOnSegment(object, wp(3200, 3200), wp(3205, 3200)));
    }

    @Test
    public void nonWallObjectOffSegmentIsIgnored() {
        TileObject object = mock(TileObject.class);
        when(object.getWorldLocation()).thenReturn(wp(3202, 3208)); // 8 tiles off the segment line
        assertFalse(Rs2DoorGeometry.isDoorOnSegment(object, wp(3200, 3200), wp(3205, 3200)));
    }

    // --- isDoorInteractionWithinRange --------------------------------------------------------------

    @Test
    public void interactionWithinRangeUsesNearestOfProbeAndEndpoints() {
        WorldPoint player = wp(3200, 3200);
        // probe one tile away, range 2 -> within.
        assertTrue(Rs2DoorGeometry.isDoorInteractionWithinRange(null, wp(3201, 3200), null, null, player, 2));
        // probe out of range (10 away) but an endpoint (fromWp) within range -> true via the nearest candidate.
        assertTrue(Rs2DoorGeometry.isDoorInteractionWithinRange(null, wp(3210, 3200), wp(3201, 3200),
                wp(3211, 3200), player, 2));
        // everything 10 tiles away -> out of range.
        assertFalse(Rs2DoorGeometry.isDoorInteractionWithinRange(null, wp(3210, 3200), wp(3210, 3201),
                wp(3211, 3200), player, 2));
    }

    @Test
    public void locatedDoorMustItselfBeWithinInteractionRange() {
        WorldPoint player = wp(3200, 3200);
        TileObject farDoor = mock(TileObject.class);
        when(farDoor.getWorldLocation()).thenReturn(wp(3210, 3200));
        assertFalse(Rs2DoorGeometry.isDoorInteractionWithinRange(farDoor, wp(3201, 3200),
                player, wp(3210, 3200), player, 2));

        TileObject nearDoor = mock(TileObject.class);
        when(nearDoor.getWorldLocation()).thenReturn(wp(3202, 3200));
        assertTrue(Rs2DoorGeometry.isDoorInteractionWithinRange(nearDoor, wp(3210, 3200),
                wp(3210, 3201), wp(3211, 3200), player, 2));
    }

    @Test
    public void interactionOnDifferentPlaneIsOutOfRange() {
        // Probe is adjacent in 2D but on another plane, so it must not count as reachable.
        assertFalse(Rs2DoorGeometry.isDoorInteractionWithinRange(null, wp(3201, 3200, 1), null, null,
                wp(3200, 3200, 0), 2));
    }

    @Test
    public void interactionRejectsNonPositiveRangeAndNullPlayer() {
        assertFalse(Rs2DoorGeometry.isDoorInteractionWithinRange(null, wp(3200, 3200), null, null,
                wp(3200, 3200), 0));
        assertFalse(Rs2DoorGeometry.isDoorInteractionWithinRange(null, wp(3200, 3200), null, null, null, 2));
    }

    // ---- playerBeyondWallFace --------------------------------------------------------------------
    //
    // THE STRONGHOLD GATE BOUNCE (2026-08-12). A west-facing moves-you Gate of War at (1887,5244);
    // the route step (1886,5244)->(1887,5243) crossed its face DIAGONALLY, and the gate deposited
    // the player at (1887,5244) — off the planned to-tile — so the segment-based crossing test
    // answered false while the raw scan's backtrack window kept re-finding the gate. Each re-click
    // carried the player back through it.

    private static final int WEST = 1;
    private static final int NORTH = 2;
    private static final int EAST = 4;
    private static final int SOUTH = 8;

    /** The bounce itself: carried past the face, even a tile off the planned to-tile, is crossed. */
    @Test
    public void depositedBeyondTheFaceIsCrossedEvenOffThePlannedTile() {
        assertTrue(Rs2DoorGeometry.playerBeyondWallFace(WEST, wp(1887, 5244),
                wp(1886, 5244), wp(1887, 5244)));
    }

    /** Approaching from the near side — including standing ON the approach tile — is not crossed. */
    @Test
    public void approachingTheFaceIsNotCrossed() {
        assertFalse(Rs2DoorGeometry.playerBeyondWallFace(WEST, wp(1887, 5244),
                wp(1886, 5244), wp(1885, 5244)));
        assertFalse(Rs2DoorGeometry.playerBeyondWallFace(WEST, wp(1887, 5244),
                wp(1886, 5244), wp(1886, 5244)));
    }

    /** Standing on the wall's own tile counts as its side of the face: the second Stronghold gate. */
    @Test
    public void standingOnTheWallTileIsBeyondAWestFaceApproachedFromTheWest() {
        assertTrue(Rs2DoorGeometry.playerBeyondWallFace(WEST, wp(1904, 5242),
                wp(1903, 5242), wp(1904, 5242)));
    }

    /** The same boundary read from the other direction: crossing east-to-west is symmetric. */
    @Test
    public void crossingIsSymmetricAcrossTheFace() {
        assertTrue(Rs2DoorGeometry.playerBeyondWallFace(WEST, wp(1887, 5244),
                wp(1887, 5244), wp(1886, 5244)));
        assertFalse(Rs2DoorGeometry.playerBeyondWallFace(WEST, wp(1887, 5244),
                wp(1887, 5244), wp(1888, 5244)));
    }

    @Test
    public void everyCardinalFaceDividesAlongItsOwnAxis() {
        // East face of (10,10): boundary between x=10 and x=11.
        assertTrue(Rs2DoorGeometry.playerBeyondWallFace(EAST, wp(10, 10), wp(10, 10), wp(11, 10)));
        assertFalse(Rs2DoorGeometry.playerBeyondWallFace(EAST, wp(10, 10), wp(10, 10), wp(9, 10)));
        // North face of (10,10): boundary between y=10 and y=11.
        assertTrue(Rs2DoorGeometry.playerBeyondWallFace(NORTH, wp(10, 10), wp(10, 10), wp(10, 11)));
        // South face of (10,10): boundary between y=9 and y=10.
        assertTrue(Rs2DoorGeometry.playerBeyondWallFace(SOUTH, wp(10, 10), wp(10, 10), wp(10, 9)));
        assertFalse(Rs2DoorGeometry.playerBeyondWallFace(SOUTH, wp(10, 10), wp(10, 10), wp(10, 10)));
    }

    /** A corner wall's face does not divide the plane along one axis: never claim crossed. */
    @Test
    public void cornerWallsNeverReadAsCrossed() {
        assertFalse(Rs2DoorGeometry.playerBeyondWallFace(16, wp(10, 10), wp(9, 10), wp(11, 10)));
        assertFalse(Rs2DoorGeometry.playerBeyondWallFace(128, wp(10, 10), wp(9, 10), wp(11, 10)));
    }
}
