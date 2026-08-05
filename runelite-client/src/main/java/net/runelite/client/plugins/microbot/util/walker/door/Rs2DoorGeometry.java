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
        int best = Integer.MAX_VALUE;
        WorldPoint objectLoc = object != null ? object.getWorldLocation() : null;
        if (objectLoc != null && objectLoc.getPlane() == playerLoc.getPlane()) {
            best = Math.min(best, objectLoc.distanceTo2D(playerLoc));
        }
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
