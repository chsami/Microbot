package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.walker.door.Rs2DoorGeometry;

/** Pure state and traversal-envelope policy for a remembered scene-door route edge. */
final class WalledDoorClaimPolicy {
    static final long FRESH_MS = 6_000L;

    enum Decision {
        NONE,
        EXPIRED,
        CROSSED,
        ACTION_IN_FLIGHT,
        HANDLE_AT_EDGE,
        APPROACH,
        INVALID
    }

    private WalledDoorClaimPolicy() {
    }

    static boolean isFresh(WorldPoint from, WorldPoint to, long claimedAtMs, long nowMs) {
        return isFresh(from, to, claimedAtMs, nowMs, FRESH_MS);
    }

    static boolean isFresh(WorldPoint from, WorldPoint to, long claimedAtMs, long nowMs,
                           long maxAgeMs) {
        return from != null && to != null && claimedAtMs > 0L && maxAgeMs >= 0L
                && nowMs - claimedAtMs <= maxAgeMs;
    }

    static Decision decide(WorldPoint from, WorldPoint to, long claimedAtMs, long nowMs,
                           WorldPoint player, boolean moving, boolean nearSideReachable) {
        return decide(from, to, claimedAtMs, nowMs, player, moving, nearSideReachable, FRESH_MS);
    }

    static Decision decide(WorldPoint from, WorldPoint to, long claimedAtMs, long nowMs,
                           WorldPoint player, boolean moving, boolean nearSideReachable,
                           long maxAgeMs) {
        if (from == null || to == null || player == null || claimedAtMs <= 0L) {
            return Decision.NONE;
        }
        if (!isFresh(from, to, claimedAtMs, nowMs, maxAgeMs)) {
            return Decision.EXPIRED;
        }
        if (from.getPlane() != to.getPlane() || player.getPlane() != from.getPlane()
                || from.distanceTo2D(to) != 1) {
            return Decision.INVALID;
        }
        if (player.distanceTo2D(to) <= 1 && Rs2DoorGeometry.crossedDoorAxis(from, to, player)) {
            return Decision.CROSSED;
        }
        if (moving) {
            return Decision.ACTION_IN_FLIGHT;
        }
        if (player.distanceTo2D(from) <= 1) {
            return Decision.HANDLE_AT_EDGE;
        }
        return nearSideReachable ? Decision.APPROACH : Decision.INVALID;
    }

    static boolean ownsTraversalEdge(WorldPoint doorFrom, WorldPoint doorTo,
                                     WorldPoint routeFrom, WorldPoint routeTo) {
        if (doorFrom == null || doorTo == null || routeFrom == null || routeTo == null
                || doorFrom.getPlane() != doorTo.getPlane()
                || routeFrom.getPlane() != routeTo.getPlane()
                || doorFrom.getPlane() != routeFrom.getPlane()) {
            return false;
        }
        if ((doorFrom.equals(routeFrom) && doorTo.equals(routeTo))
                || (doorFrom.equals(routeTo) && doorTo.equals(routeFrom))) {
            return true;
        }
        return (doorTo.equals(routeFrom) && doorTo.distanceTo2D(routeTo) == 1)
                || (doorTo.equals(routeTo) && doorTo.distanceTo2D(routeFrom) == 1);
    }

    /**
     * An opened edge may be spent on a controlled traversal click only by the exact scene-door
     * transaction. A merely refused route edge is an approach envelope, not proof that somebody
     * interacted with a door, and movement/animation means the owned action is already in flight.
     */
    static boolean shouldTraverseOpenEdge(boolean exactDoorClaim, boolean edgePassable,
                                          boolean moving, boolean animating) {
        return exactDoorClaim && edgePassable && !moving && !animating;
    }
}
