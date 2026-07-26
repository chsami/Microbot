package net.runelite.client.plugins.microbot.util.walker.door;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Headless tests for {@link Rs2DoorAheadResolver#buildSegmentProbes} — the pure geometry that decides which
 * tiles to probe for a door blocking a route step. A straight step probes only the door tile; a diagonal
 * step also probes the two L-corner tiles (the door could sit on either leg of the corner), carrying the
 * door's plane. Part of harnessing the door detection layer ahead of any P2 door work.
 */
public class Rs2DoorAheadResolverTest {

    private static WorldPoint wp(int x, int y) {
        return new WorldPoint(x, y, 0);
    }

    private static WorldPoint wp(int x, int y, int plane) {
        return new WorldPoint(x, y, plane);
    }

    @Test
    public void straightSegmentProbesOnlyTheDoorTile() {
        List<WorldPoint> probes = Rs2DoorAheadResolver.buildSegmentProbes(wp(3200, 3200), wp(3205, 3200),
                wp(3202, 3200));
        assertEquals(1, probes.size());
        assertEquals(wp(3202, 3200), probes.get(0));
    }

    @Test
    public void diagonalSegmentAddsBothLCornerProbes() {
        List<WorldPoint> probes = Rs2DoorAheadResolver.buildSegmentProbes(wp(3200, 3200), wp(3203, 3203),
                wp(3201, 3201));
        assertEquals(3, probes.size());
        assertEquals("door tile is probed first", wp(3201, 3201), probes.get(0));
        assertTrue("corner (toX, fromY)", probes.contains(wp(3203, 3200)));
        assertTrue("corner (fromX, toY)", probes.contains(wp(3200, 3203)));
    }

    @Test
    public void cornerProbesCarryTheDoorsPlane() {
        // Endpoints on plane 0, door on plane 1: the L-corner probes must use the door's plane, not the path's.
        List<WorldPoint> probes = Rs2DoorAheadResolver.buildSegmentProbes(wp(3200, 3200, 0), wp(3203, 3203, 0),
                wp(3201, 3201, 1));
        assertTrue(probes.contains(wp(3203, 3200, 1)));
        assertTrue(probes.contains(wp(3200, 3203, 1)));
    }

    @Test
    public void nullInputsYieldEmpty() {
        assertTrue(Rs2DoorAheadResolver.buildSegmentProbes(null, wp(1, 1), wp(1, 1)).isEmpty());
        assertTrue(Rs2DoorAheadResolver.buildSegmentProbes(wp(1, 1), null, wp(1, 1)).isEmpty());
        assertTrue(Rs2DoorAheadResolver.buildSegmentProbes(wp(1, 1), wp(1, 1), null).isEmpty());
    }
}
