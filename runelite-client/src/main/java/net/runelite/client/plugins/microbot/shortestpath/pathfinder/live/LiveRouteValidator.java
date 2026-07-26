package net.runelite.client.plugins.microbot.shortestpath.pathfinder.live;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.CollisionMap;

import java.util.List;

/**
 * Validates the walking steps of an in-progress route against a {@link CollisionMap}, so the walker can
 * proactively recalculate when live collision has blocked the road ahead (a door closed, a temporary
 * object appeared) rather than walking into it and stalling.
 * <p>
 * Only walking edges between adjacent same-plane tiles are checked. Transport jumps (non-adjacent, or
 * cross-plane — stairs, ladders, the wilderness ditch "Cross") are skipped: those are graph edges the
 * pathfinder adds independently of collision, and openable doors are already left unknown in the overlay
 * so their steps read as passable and the runtime door handler owns them.
 */
public final class LiveRouteValidator {

    private LiveRouteValidator() {
    }

    /**
     * @return the index of the path tile nearest the player (same plane), or 0 when the path is empty.
     * Used as the start of the look-ahead so a player slightly off the exact path tile is handled.
     */
    public static int nearestIndex(List<WorldPoint> path, WorldPoint player) {
        if (path == null || path.isEmpty() || player == null) {
            return 0;
        }
        int best = 0;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < path.size(); i++) {
            final WorldPoint p = path.get(i);
            if (p.getPlane() != player.getPlane()) {
                continue;
            }
            final int d = Math.abs(p.getX() - player.getX()) + Math.abs(p.getY() - player.getY());
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    /**
     * @return the first path index at or after {@code fromIndex}, within {@code lookahead} steps, whose
     * walking step to the next tile is no longer traversable per {@code map}; or {@code -1} when the near
     * route is clear. Caller must have pinned the map's snapshot ({@link CollisionMap#beginSearch()}).
     */
    public static int firstBlockedStep(List<WorldPoint> path, int fromIndex, int lookahead, CollisionMap map) {
        if (path == null || map == null) {
            return -1;
        }
        int checked = 0;
        for (int i = Math.max(0, fromIndex); i + 1 < path.size() && checked < lookahead; i++, checked++) {
            final WorldPoint a = path.get(i);
            final WorldPoint b = path.get(i + 1);
            if (a.getPlane() != b.getPlane()) {
                continue; // transport (stairs/ladder/teleport)
            }
            final int dx = b.getX() - a.getX();
            final int dy = b.getY() - a.getY();
            if (dx == 0 && dy == 0) {
                continue;
            }
            if (Math.abs(dx) > 1 || Math.abs(dy) > 1) {
                continue; // non-adjacent: a transport jump, not a walking step
            }
            if (!map.canStep(a.getX(), a.getY(), a.getPlane(), dx, dy)) {
                return i;
            }
        }
        return -1;
    }
}
