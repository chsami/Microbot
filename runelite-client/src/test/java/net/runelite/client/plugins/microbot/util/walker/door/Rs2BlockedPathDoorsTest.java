package net.runelite.client.plugins.microbot.util.walker.door;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Juliet's house, upstairs (plane 1): player at (3155,3426), staircase at (3156,3435), closed doors
 * (3158,3426) then (3157,3430), both blocking their east/south edge. The walker used to loop on the
 * minimap fallback (3156,3426) instead of opening them.
 */
public class Rs2BlockedPathDoorsTest {

    private static WorldPoint wp(int x, int y) {
        return new WorldPoint(x, y, 1);
    }

    private static final WorldPoint PLAYER = wp(3155, 3426);

    private static List<WorldPoint> path() {
        List<WorldPoint> p = new ArrayList<>();
        for (int x = 3155; x <= 3158; x++) p.add(wp(x, 3426));
        p.add(wp(3157, 3427));
        for (int y = 3428; y <= 3430; y++) p.add(wp(3157, y));
        p.add(wp(3157, 3431));
        for (int y = 3432; y <= 3435; y++) p.add(wp(3156 + (y > 3431 ? 0 : 1), y));
        return p;
    }

    /** Reachable set as BFS would give it with the given doors shut: everything on the path before the first shut door. */
    private static Set<WorldPoint> reachableBeforeDoor(List<WorldPoint> path, WorldPoint doorTile, WorldPoint beyond) {
        Set<WorldPoint> r = new HashSet<>();
        for (WorldPoint t : path) {
            if (t.equals(beyond)) break;
            r.add(t);
        }
        return r;
    }

    private static Rs2BlockedPathDoors.DoorSnapshot door(int x, int y, int o, String id) {
        return new Rs2BlockedPathDoors.DoorSnapshot(wp(x, y), o, 0, id);
    }

    @Test
    public void firstDoorIsChosenThenSecondOnceFirstIsOpen() {
        List<WorldPoint> path = path();
        List<Rs2BlockedPathDoors.DoorSnapshot> doors = new ArrayList<>();
        Rs2BlockedPathDoors.DoorSnapshot d1 = door(3157, 3426, 4, "first"); // east edge of 3157 -> 3158
        Rs2BlockedPathDoors.DoorSnapshot d2 = door(3157, 3430, 2, "second"); // north edge 3157,3430 -> 3157,3431
        doors.add(d2);
        doors.add(d1);

        // Both shut: reachable stops at (3157,3426).
        Set<WorldPoint> reach = reachableBeforeDoor(path, d1.getTile(), wp(3158, 3426));
        int idx = Rs2BlockedPathDoors.firstBlockedPathIndex(path, 1, PLAYER, 10, reach::contains);
        assertEquals(wp(3158, 3426), path.get(idx));
        assertSame(d1, Rs2BlockedPathDoors.selectDoorForEdge(path, idx, PLAYER, doors));

        // First door open: reachable extends to (3157,3430); the next blocked edge is the second door.
        Set<WorldPoint> reach2 = reachableBeforeDoor(path, d2.getTile(), wp(3157, 3431));
        int idx2 = Rs2BlockedPathDoors.firstBlockedPathIndex(path, 1, PLAYER, 10, reach2::contains);
        assertEquals(wp(3157, 3431), path.get(idx2));
        assertSame(d2, Rs2BlockedPathDoors.selectDoorForEdge(path, idx2, PLAYER, doors));
    }

    @Test
    public void doorAdjacentToEdgeIsAcceptedAndOtherPlaneIgnored() {
        List<WorldPoint> path = path();
        Rs2BlockedPathDoors.DoorSnapshot adjacent = door(3158, 3427, 0, "adj");
        Rs2BlockedPathDoors.DoorSnapshot otherPlane = new Rs2BlockedPathDoors.DoorSnapshot(
                new WorldPoint(3158, 3426, 0), 4, 0, "ground");
        List<Rs2BlockedPathDoors.DoorSnapshot> doors = List.of(otherPlane, adjacent);
        assertSame(adjacent, Rs2BlockedPathDoors.selectDoorForEdge(path, 3, PLAYER, doors));
        assertNull(Rs2BlockedPathDoors.selectDoorForEdge(path, 3, PLAYER, List.of(otherPlane)));
    }

    @Test
    public void noBlockedIndexWhenEverythingIsReachable() {
        List<WorldPoint> path = path();
        assertEquals(-1, Rs2BlockedPathDoors.firstBlockedPathIndex(path, 1, PLAYER, 10, t -> true));
    }

    @Test
    public void fallbackOnOrNextToPlayerIsNoProgress() {
        assertTrue(Rs2BlockedPathDoors.fallbackIsNoProgress(PLAYER, wp(3156, 3426)));
        assertTrue(Rs2BlockedPathDoors.fallbackIsNoProgress(PLAYER, PLAYER));
        assertFalse(Rs2BlockedPathDoors.fallbackIsNoProgress(PLAYER, wp(3158, 3426)));
    }

    @Test
    public void loopDetectedAfterTwoIdenticalFallbacks() {
        Rs2BlockedPathDoors.FallbackLoopGuard g = new Rs2BlockedPathDoors.FallbackLoopGuard();
        g.record(wp(3156, 3426));
        assertFalse(g.looping());
        g.record(wp(3156, 3426));
        assertTrue(g.looping());
        g.record(wp(3157, 3426));
        assertFalse(g.looping());
    }
}
