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
        // everything 10 tiles away -> out of range.
        assertFalse(Rs2DoorGeometry.isDoorInteractionWithinRange(null, wp(3210, 3200), wp(3210, 3201),
                wp(3211, 3200), player, 2));
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
}
