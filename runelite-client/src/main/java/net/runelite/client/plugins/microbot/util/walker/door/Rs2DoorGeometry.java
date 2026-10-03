package net.runelite.client.plugins.microbot.util.walker.door;

import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.coords.WorldPoint;

/**
 * Stateless geometry helpers for deciding whether a door / gate object actually sits on the
 * planned path segment (as opposed to merely being nearby), and whether it is close enough to
 * interact with. Extracted verbatim from {@code Rs2Walker} as the first step of the walker
 * decomposition — pure functions over {@link WorldPoint}/{@link WallObject}, no walker state.
 */
public final class Rs2DoorGeometry {

    private Rs2DoorGeometry() {
    }

    public static boolean isDoorOnSegment(TileObject object, WorldPoint fromWp, WorldPoint toWp) {
        return isDoorOnSegment(object, object == null ? null : object.getWorldLocation(), fromWp, toWp);
    }

    /**
     * Whether {@code at} lies at or beyond the far side of the cardinal {@code from -> to} door edge,
     * measured along the edge's own axis. This is the unambiguous "we are past the door" reading —
     * unlike near-side proximity, which fires while still approaching. Door edges are cardinal;
     * anything else answers false.
     */
    public static boolean crossedDoorAxis(WorldPoint from, WorldPoint to, WorldPoint at) {
        if (from == null || to == null || at == null
                || from.getPlane() != to.getPlane() || at.getPlane() != to.getPlane()) {
            return false;
        }
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        if (dx != 0 && dy == 0) {
            int travelled = at.getX() - from.getX();
            return dx > 0 ? travelled >= 1 : travelled <= -1;
        }
        if (dy != 0 && dx == 0) {
            int travelled = at.getY() - from.getY();
            return dy > 0 ? travelled >= 1 : travelled <= -1;
        }
        return false;
    }

    /**
     * Whether {@code at} already stands on the FAR side of this wall door's face relative to
     * {@code from} — the crossing the door exists to produce has happened, so clicking it again can
     * only carry the player backward.
     *
     * <p>Anchored to the wall's own face rather than the route segment, unlike
     * {@link #crossedDoorAxis}, which is cardinal-only and reads the segment. Both properties
     * mattered at the Stronghold of Security's paired Gates of War (2026-08-12): the route step
     * (1886,5244)->(1887,5243) was DIAGONAL, and the moves-you gate deposited the player at
     * (1887,5244) — a tile off the planned to-tile — so the segment-based reading answered false
     * while the raw scan's backtrack window kept re-finding the gate; each re-click carried the
     * player back through it, a two-sided bounce every ~6 seconds.
     *
     * <p>Corner walls (orientation 16..128) answer false: their face does not divide the plane
     * along a single axis.
     *
     * @param orientationA the wall's {@code getOrientationA()}: 1=west, 2=north, 4=east, 8=south
     */
    public static boolean playerBeyondWallFace(int orientationA, WorldPoint wallTile,
                                               WorldPoint from, WorldPoint at) {
        if (wallTile == null || from == null || at == null
                || wallTile.getPlane() != from.getPlane() || at.getPlane() != from.getPlane()) {
            return false;
        }
        switch (orientationA) {
            case 1: // west face: boundary between x = wallTile.x-1 and x = wallTile.x
                return sidesDiffer(from.getX(), at.getX(), wallTile.getX());
            case 4: // east face: boundary between x = wallTile.x and x = wallTile.x+1
                return sidesDiffer(from.getX(), at.getX(), wallTile.getX() + 1);
            case 2: // north face: boundary between y = wallTile.y and y = wallTile.y+1
                return sidesDiffer(from.getY(), at.getY(), wallTile.getY() + 1);
            case 8: // south face: boundary between y = wallTile.y-1 and y = wallTile.y
                return sidesDiffer(from.getY(), at.getY(), wallTile.getY());
            default:
                return false;
        }
    }

    /** Opposite sides of the boundary that lies just before {@code boundary} ({@code >=} vs {@code <}). */
    private static boolean sidesDiffer(int a, int b, int boundary) {
        return (a >= boundary) != (b >= boundary);
    }

    /** As above, with the object's location supplied (see {@link #wallDoorTouchesSegment}). */
    public static boolean isDoorOnSegment(TileObject object, WorldPoint objectLocation,
                                          WorldPoint fromWp, WorldPoint toWp) {
        if (object == null || objectLocation == null) return false;
        if (object instanceof WallObject) {
            return wallDoorTouchesSegment((WallObject) object, objectLocation, fromWp, toWp);
        }
        return isPointNearSegment(objectLocation, fromWp, toWp, 1);
    }

    public static boolean isDoorInteractionWithinRange(TileObject object, WorldPoint probe,
                                                       WorldPoint fromWp, WorldPoint toWp,
                                                       WorldPoint playerLoc, int rangeTiles) {
        if (playerLoc == null || rangeTiles <= 0) {
            return false;
        }
        WorldPoint objectLoc = object != null ? object.getWorldLocation() : null;
        if (objectLoc != null) {
            // A long smoothed route segment may start beside the player while the actual door
            // is much farther away. Its endpoint must not make that distant object eligible.
            return objectLoc.getPlane() == playerLoc.getPlane()
                    && objectLoc.distanceTo2D(playerLoc) <= rangeTiles;
        }
        int best = Integer.MAX_VALUE;
        if (probe != null && probe.getPlane() == playerLoc.getPlane()) {
            best = Math.min(best, probe.distanceTo2D(playerLoc));
        }
        if (fromWp != null && fromWp.getPlane() == playerLoc.getPlane()) {
            best = Math.min(best, fromWp.distanceTo2D(playerLoc));
        }
        if (toWp != null && toWp.getPlane() == playerLoc.getPlane()) {
            best = Math.min(best, toWp.distanceTo2D(playerLoc));
        }
        return best <= rangeTiles;
    }

    public static boolean wallDoorTouchesSegment(WallObject wall, WorldPoint fromWp, WorldPoint toWp) {
        return wallDoorTouchesSegment(wall, wall == null ? null : wall.getWorldLocation(), fromWp, toWp);
    }

    /**
     * As above, but with the wall's location supplied by the caller. The door probe runs this over
     * every wall in the scene snapshot for every route segment, and the location-less form resolved
     * {@code getWorldLocation()} three times per call — measured at 1219ms of {@code doorProbe} in a
     * single scan even after the segment-independent predicates were memoised. The scan already
     * captured every object's location on the client thread, so pass it in.
     */
    public static boolean wallDoorTouchesSegment(WallObject wall, WorldPoint wallLocation,
                                                 WorldPoint fromWp, WorldPoint toWp) {
        if (wall == null || wallLocation == null || fromWp == null || toWp == null) return false;
        if (wallLocation.getPlane() != fromWp.getPlane() || fromWp.getPlane() != toWp.getPlane()) return false;

        WorldPoint doorTile = wallLocation;
        // A door panel can advertise a blocked edge on either orientation; check both so a
        // legitimately-on-path door is never missed.
        WorldPoint blockedNeighborA = getWallDoorNeighborPoint(wall.getOrientationA(), doorTile);
        WorldPoint blockedNeighborB = getWallDoorNeighborPoint(wall.getOrientationB(), doorTile);
        if (blockedNeighborA == null && blockedNeighborB == null) return false;

        if (fromWp.getX() != toWp.getX() && fromWp.getY() != toWp.getY()) {
            WorldPoint xThenY = new WorldPoint(toWp.getX(), fromWp.getY(), fromWp.getPlane());
            WorldPoint yThenX = new WorldPoint(fromWp.getX(), toWp.getY(), fromWp.getPlane());
            if (isDoorEdgeTransitionAny(fromWp, xThenY, doorTile, blockedNeighborA, blockedNeighborB)
                    || isDoorEdgeTransitionAny(xThenY, toWp, doorTile, blockedNeighborA, blockedNeighborB)
                    || isDoorEdgeTransitionAny(fromWp, yThenX, doorTile, blockedNeighborA, blockedNeighborB)
                    || isDoorEdgeTransitionAny(yThenX, toWp, doorTile, blockedNeighborA, blockedNeighborB)) {
                return true;
            }
        }

        int x = fromWp.getX();
        int y = fromWp.getY();
        int steps = 0;
        WorldPoint previous = new WorldPoint(x, y, fromWp.getPlane());
        while (steps++ <= 64) {
            if (x == toWp.getX() && y == toWp.getY()) {
                return false;
            }
            x += Integer.signum(toWp.getX() - x);
            y += Integer.signum(toWp.getY() - y);
            WorldPoint next = new WorldPoint(x, y, fromWp.getPlane());
            if (isDoorEdgeTransitionAny(previous, next, doorTile, blockedNeighborA, blockedNeighborB)) {
                return true;
            }
            previous = next;
        }
        return false;
    }

    private static WorldPoint getWallDoorNeighborPoint(int orientation, WorldPoint point) {
        switch (orientation) {
            case 1:   // west
                return point.dx(-1);
            case 2:   // north
                return point.dy(1);
            case 4:   // east
                return point.dx(1);
            case 8:   // south
                return point.dy(-1);
            case 16:  // northwest
                return point.dx(-1).dy(1);
            case 32:  // northeast
                return point.dx(1).dy(1);
            case 64:  // southeast
                return point.dx(1).dy(-1);
            case 128: // southwest
                return point.dx(-1).dy(-1);
            default:
                return null;
        }
    }

    private static boolean isDoorEdgeTransition(WorldPoint a, WorldPoint b, WorldPoint doorTile, WorldPoint blockedNeighbor) {
        return (a.equals(doorTile) && b.equals(blockedNeighbor))
                || (a.equals(blockedNeighbor) && b.equals(doorTile));
    }

    private static boolean isDoorEdgeTransitionAny(WorldPoint a, WorldPoint b, WorldPoint doorTile,
                                                   WorldPoint blockedNeighborA, WorldPoint blockedNeighborB) {
        return (blockedNeighborA != null && isDoorEdgeTransition(a, b, doorTile, blockedNeighborA))
                || (blockedNeighborB != null && isDoorEdgeTransition(a, b, doorTile, blockedNeighborB));
    }

    private static boolean isPointNearSegment(WorldPoint point, WorldPoint fromWp, WorldPoint toWp, int distance) {
        if (point == null || fromWp == null || toWp == null || point.getPlane() != fromWp.getPlane() || fromWp.getPlane() != toWp.getPlane()) {
            return false;
        }

        int x = fromWp.getX();
        int y = fromWp.getY();
        int steps = 0;
        while (steps++ <= 64) {
            if (point.distanceTo2D(new WorldPoint(x, y, fromWp.getPlane())) <= distance) {
                return true;
            }
            if (x == toWp.getX() && y == toWp.getY()) {
                return false;
            }
            x += Integer.signum(toWp.getX() - x);
            y += Integer.signum(toWp.getY() - y);
        }
        return false;
    }
}
