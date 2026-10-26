package net.runelite.client.plugins.microbot.util.walker.door;

import net.runelite.api.coords.WorldPoint;

import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/**
 * Pure geometry for "the next path tiles are unreachable because a closed door sits on the path".
 * No client access: callers snapshot the scene's wall objects into {@link DoorSnapshot}s and the
 * reachable set into a predicate, so the live bug geometry (two chained doors between the player and a
 * staircase) can be pinned in a headless test.
 */
public final class Rs2BlockedPathDoors {

    /** Consecutive identical fallback picks after which the walker is considered to be looping. */
    public static final int NO_PROGRESS_ATTEMPTS = 2;

    private Rs2BlockedPathDoors() {
    }

    /** A closed-door candidate: a WALL object with an "Open" action. {@code handle} is the caller's object. */
    public static final class DoorSnapshot {
        private final WorldPoint tile;
        private final int orientationA;
        private final int orientationB;
        private final Object handle;

        public DoorSnapshot(WorldPoint tile, int orientationA, int orientationB, Object handle) {
            this.tile = tile;
            this.orientationA = orientationA;
            this.orientationB = orientationB;
            this.handle = handle;
        }

        public WorldPoint getTile() {
            return tile;
        }

        public Object getHandle() {
            return handle;
        }

        /** True when this wall blocks the edge between {@code a} and {@code b} (either direction). */
        boolean blocksEdge(WorldPoint a, WorldPoint b) {
            return blocksFrom(a, b) || blocksFrom(b, a);
        }

        private boolean blocksFrom(WorldPoint from, WorldPoint to) {
            if (!tile.equals(from)) {
                return false;
            }
            return neighbor(orientationA, tile) != null && neighbor(orientationA, tile).equals(to)
                    || neighbor(orientationB, tile) != null && neighbor(orientationB, tile).equals(to);
        }
    }

    private static WorldPoint neighbor(int orientation, WorldPoint p) {
        switch (orientation) {
            case 1: return p.dx(-1);
            case 2: return p.dy(1);
            case 4: return p.dx(1);
            case 8: return p.dy(-1);
            default: return null;
        }
    }

    /**
     * Index of the first path tile within {@code radius} of the player (same plane) that is not
     * reachable, or -1. Index 0 is never returned: the edge is {@code path[i-1] -> path[i]}.
     */
    public static int firstBlockedPathIndex(List<WorldPoint> path, int startIdx, WorldPoint player,
                                            int radius, Predicate<WorldPoint> reachable) {
        if (path == null || player == null || reachable == null) {
            return -1;
        }
        for (int i = Math.max(1, startIdx); i < path.size(); i++) {
            WorldPoint tile = path.get(i);
            if (tile == null || tile.getPlane() != player.getPlane()) {
                continue;
            }
            if (tile.distanceTo2D(player) > radius) {
                continue;
            }
            if (!reachable.test(tile) && reachable.test(path.get(i - 1))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Best closed door for the blocked edge {@code path[blockedIdx-1] -> path[blockedIdx]}: one whose wall
     * blocks exactly that edge, else one standing on either endpoint, else one adjacent to the edge.
     */
    public static DoorSnapshot selectDoorForEdge(List<WorldPoint> path, int blockedIdx, WorldPoint player,
                                                 Collection<DoorSnapshot> doors) {
        if (path == null || blockedIdx < 1 || blockedIdx >= path.size() || doors == null) {
            return null;
        }
        WorldPoint a = path.get(blockedIdx - 1);
        WorldPoint b = path.get(blockedIdx);
        DoorSnapshot best = null;
        int bestScore = Integer.MAX_VALUE;
        for (DoorSnapshot door : doors) {
            if (door == null || door.tile == null || a == null || b == null
                    || door.tile.getPlane() != a.getPlane()) {
                continue;
            }
            int rank;
            if (door.blocksEdge(a, b)) {
                rank = 0;
            } else if (door.tile.equals(a) || door.tile.equals(b)) {
                rank = 1;
            } else if (door.tile.distanceTo2D(a) <= 1 || door.tile.distanceTo2D(b) <= 1) {
                rank = 2;
            } else {
                continue;
            }
            int score = rank * 1000 + (player == null ? 0 : door.tile.distanceTo2D(player));
            if (score < bestScore) {
                bestScore = score;
                best = door;
            }
        }
        return best;
    }

    /** True when {@code fallback} is where the player already stands or an adjacent tile: a click there is no progress. */
    public static boolean fallbackIsNoProgress(WorldPoint player, WorldPoint fallback) {
        return player != null && fallback != null && player.getPlane() == fallback.getPlane()
                && player.distanceTo2D(fallback) <= 1;
    }

    /** Counts consecutive identical fallback picks so a no-progress loop is detected after two attempts. */
    public static final class FallbackLoopGuard {
        private WorldPoint last;
        private int count;

        public synchronized int record(WorldPoint fallback) {
            if (fallback != null && fallback.equals(last)) {
                count++;
            } else {
                last = fallback;
                count = fallback == null ? 0 : 1;
            }
            return count;
        }

        public synchronized boolean looping() {
            return count >= NO_PROGRESS_ATTEMPTS;
        }

        public synchronized void reset() {
            last = null;
            count = 0;
        }
    }
}
