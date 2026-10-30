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

    /** Consecutive door-handler runs on one claimed edge that found nothing to open before the claim is dropped. */
    static final int UNFULFILLED_CLAIM_LIMIT = 3;

    /**
     * Counts consecutive door-handler runs that did nothing on the same claimed edge. A claim can come from a door
     * beside the edge (a double-gate wing) that the exact edge does not have; the handler then finds nothing, again and
     * again, and the walker waits on a door that does not exist.
     */
    static final class UnfulfilledClaims {
        private WorldPoint from;
        private WorldPoint to;
        private int count;
        private long firstAtMs;

        /** Notes one fruitless run on the edge; returns how many in a row there have been. */
        int note(WorldPoint edgeFrom, WorldPoint edgeTo, long nowMs) {
            if (edgeFrom.equals(from) && edgeTo.equals(to)) {
                count++;
            } else {
                from = edgeFrom;
                to = edgeTo;
                count = 1;
                firstAtMs = nowMs;
            }
            return count;
        }

        /** How long the current edge has been fruitless. */
        long spanMs(long nowMs) {
            return count == 0 ? 0L : nowMs - firstAtMs;
        }

        void reset() {
            from = null;
            to = null;
            count = 0;
            firstAtMs = 0L;
        }
    }

    /** The claim must stay fruitless for at least this long, so a handler that is merely throttled is not mistaken for one that found nothing. */
    static final long UNFULFILLED_CLAIM_MIN_SPAN_MS = 4_000L;

    /**
     * Whether the claimed edge should be given up on: repeatedly fruitless over a few seconds, and no door geometry sits
     * on the exact edge, open or shut.
     */
    static boolean shouldDropUnfulfilledClaim(int fruitlessRuns, long spanMs, boolean doorGeometryOnEdge) {
        return fruitlessRuns >= UNFULFILLED_CLAIM_LIMIT && spanMs >= UNFULFILLED_CLAIM_MIN_SPAN_MS && !doorGeometryOnEdge;
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
