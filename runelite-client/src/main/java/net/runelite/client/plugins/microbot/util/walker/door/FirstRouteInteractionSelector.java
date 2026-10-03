package net.runelite.client.plugins.microbot.util.walker.door;

import net.runelite.api.coords.WorldPoint;

import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;

/** Selects the first reachable, pending door or explicit transport on the remaining raw route. */
public final class FirstRouteInteractionSelector {

    private FirstRouteInteractionSelector() {
    }

    public enum Kind {
        NONE,
        INTERACTION,
        BLOCKED_TRANSPORT
    }

    public static final class Selection {
        private static final Selection NONE = new Selection(Kind.NONE, -1);

        private final Kind kind;
        private final int edgeIndex;

        private Selection(Kind kind, int edgeIndex) {
            this.kind = kind;
            this.edgeIndex = edgeIndex;
        }

        public Kind kind() {
            return kind;
        }

        public int edgeIndex() {
            return edgeIndex;
        }
    }

    /** Backtracked transports must not block or redispatch before the player's raw anchor. */
    public static boolean isTransportAtOrAhead(int edgeIndex, int rawAnchor) {
        return edgeIndex >= rawAnchor;
    }

    /**
     * Include the two raw edges before the forward anchor: a reachable tile beyond a gate does
     * not prove the edge to it has opened. Only a recently handled, exact transport edge with
     * observed crossing evidence may advance the scan past that backtracked edge.
     */
    public static int scanStartEdge(List<WorldPoint> rawPath, int anchor, WorldPoint playerLoc,
                                    WorldPoint recentOrigin, WorldPoint recentDestination,
                                    boolean recentlyHandled) {
        return scanStartEdge(rawPath, anchor, playerLoc, recentOrigin, recentDestination,
                recentlyHandled, 2);
    }

    /** Applies the same crossing check to a caller-selected backtrack window. */
    public static int scanStartEdge(List<WorldPoint> rawPath, int anchor, WorldPoint playerLoc,
                                    WorldPoint recentOrigin, WorldPoint recentDestination,
                                    boolean recentlyHandled, int backtrackEdges) {
        if (rawPath == null || rawPath.size() < 2) {
            return 0;
        }
        int boundedAnchor = Math.max(0, Math.min(anchor, rawPath.size() - 1));
        int start = Math.max(0, boundedAnchor - Math.max(0, backtrackEdges));
        if (!recentlyHandled || playerLoc == null || recentOrigin == null || recentDestination == null) {
            return start;
        }

        boolean crossed = playerLoc.equals(recentDestination)
                || recentOrigin.getPlane() != recentDestination.getPlane()
                && playerLoc.getPlane() == recentDestination.getPlane()
                || recentOrigin.getPlane() == recentDestination.getPlane()
                && recentOrigin.distanceTo2D(recentDestination) == 1
                && Rs2DoorGeometry.crossedDoorAxis(recentOrigin, recentDestination, playerLoc)
                || recentOrigin.getPlane() == recentDestination.getPlane()
                && playerLoc.getPlane() == recentDestination.getPlane()
                && recentOrigin.distanceTo2D(recentDestination) > 1
                && playerLoc.distanceTo2D(recentDestination) <= 1
                && playerLoc.distanceTo2D(recentOrigin)
                > playerLoc.distanceTo2D(recentDestination);
        if (!crossed) {
            return start;
        }
        for (int edge = start; edge < boundedAnchor; edge++) {
            if (recentOrigin.equals(rawPath.get(edge))
                    && recentDestination.equals(rawPath.get(edge + 1))) {
                return edge + 1;
            }
        }
        return start;
    }

    /**
     * Compatibility form for callers that only need the selected interaction index.
     */
    public static int firstInteractionEdge(List<WorldPoint> rawPath, int startEdge, int maxEdges,
                                           WorldPoint playerLoc, Map<WorldPoint, Integer> reachable,
                                           int maxDistance, IntPredicate pendingInteractionAtEdge,
                                           IntPredicate transportAtEdge) {
        Selection selection = selectFirst(rawPath, startEdge, maxEdges, playerLoc, reachable,
                maxDistance, pendingInteractionAtEdge, transportAtEdge);
        return selection.kind() == Kind.INTERACTION ? selection.edgeIndex() : -1;
    }

    /**
     * Selects the first pending interaction in route order. An explicit transport that cannot be
     * selected owns its edge and blocks later interactions; the caller can preserve that fact even
     * when no action was dispatched. A pending interaction likewise owns its edge when the eventual
     * click is deferred or fails.
     */
    public static Selection selectFirst(List<WorldPoint> rawPath, int startEdge, int maxEdges,
                                        WorldPoint playerLoc, Map<WorldPoint, Integer> reachable,
                                        int maxDistance, IntPredicate pendingInteractionAtEdge,
                                        IntPredicate transportAtEdge) {
        if (rawPath == null || rawPath.size() < 2 || startEdge < 0
                || startEdge >= rawPath.size() - 1 || maxEdges <= 0
                || playerLoc == null || maxDistance < 0) {
            return Selection.NONE;
        }

        int endExclusive = (int) Math.min((long) rawPath.size() - 1,
                (long) startEdge + maxEdges);
        for (int edge = startEdge; edge < endExclusive; edge++) {
            WorldPoint from = rawPath.get(edge);
            WorldPoint to = rawPath.get(edge + 1);
            if (from == null || to == null || from.getPlane() != playerLoc.getPlane()) {
                break;
            }
            if (transportAtEdge != null && transportAtEdge.test(edge)) {
                // A transport can change plane or jump across the map. Only its reachable origin
                // or an adjacent reachable approach tile and its interaction matter. The origin
                // itself may be blocked by the object, as with a ladder or trapdoor.
                if (from.distanceTo2D(playerLoc) > maxDistance) {
                    return new Selection(Kind.BLOCKED_TRANSPORT, edge);
                }
                if (reachable != null && !reachable.containsKey(from)) {
                    WorldPoint approach = edge == startEdge ? playerLoc : rawPath.get(edge - 1);
                    if (approach == null || approach.getPlane() != from.getPlane()
                            || approach.distanceTo2D(from) > 1
                            || !reachable.containsKey(approach)) {
                        return new Selection(Kind.BLOCKED_TRANSPORT, edge);
                    }
                }
                return pendingInteractionAtEdge != null && pendingInteractionAtEdge.test(edge)
                        ? new Selection(Kind.INTERACTION, edge)
                        : new Selection(Kind.BLOCKED_TRANSPORT, edge);
            }
            if (to.getPlane() != playerLoc.getPlane()) {
                break;
            }
            if (from.distanceTo2D(playerLoc) > maxDistance
                    && to.distanceTo2D(playerLoc) > maxDistance) {
                break;
            }
            if (reachable != null && !reachable.containsKey(from)) {
                break;
            }
            if (pendingInteractionAtEdge != null && pendingInteractionAtEdge.test(edge)) {
                return new Selection(Kind.INTERACTION, edge);
            }
            if (reachable != null && !reachable.containsKey(to)) {
                // A blocked object tile may be the exact origin of the NEXT edge's selected
                // transport. Allow that one step so its reachable near-side can be used;
                // otherwise this is an ordinary wall and the scan must stop here.
                int next = edge + 1;
                if (next >= endExclusive || from.distanceTo2D(to) > 1
                        || transportAtEdge == null || !transportAtEdge.test(next)
                        || pendingInteractionAtEdge == null || !pendingInteractionAtEdge.test(next)) {
                    break;
                }
            }
        }
        return Selection.NONE;
    }
}
