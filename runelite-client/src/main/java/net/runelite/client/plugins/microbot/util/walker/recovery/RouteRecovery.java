package net.runelite.client.plugins.microbot.util.walker.recovery;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Pure route-recovery selection logic, extracted from {@code Rs2Walker} (P1 walker decomposition). The
 * algorithm here is deliberately free of walker/game state — callers pass in the path, the player tile,
 * the reachable-tile set, and a clickability predicate — so it is unit-testable in isolation (see
 * {@code Rs2WalkerUnitTest}). The game-state-coupled wrapper (which reads reachable tiles and binds the
 * minimap-clickable predicate) stays in {@code Rs2Walker} and delegates here.
 */
public final class RouteRecovery {

    /**
     * How many path tiles ahead of the stuck index the forward-recovery scan considers. Beyond this the
     * route is re-evaluated as the player advances, so scanning further wastes work.
     */
    private static final int FORWARD_SCAN_TILES = 24;

    private RouteRecovery() {
    }

    /**
     * Picks the furthest forward path tile (from {@code startIdx}, within {@code FORWARD_SCAN_TILES}) that
     * is on the player's plane, within {@code maxEuclidean} of the player, reachable (when a reachable set
     * is supplied), and clickable per {@code isClickable}; ties break toward the tile nearer the player.
     *
     * @return the chosen path index, or {@code -1} when none qualifies.
     */
    public static int findForwardRecoveryIndex(List<WorldPoint> path,
                                               int startIdx,
                                               WorldPoint playerLoc,
                                               int maxEuclidean,
                                               Set<WorldPoint> reachable,
                                               Predicate<WorldPoint> isClickable) {
        if (path == null || path.isEmpty() || playerLoc == null || startIdx < 0 || startIdx >= path.size()) {
            return -1;
        }
        int maxSq = maxEuclidean * maxEuclidean;
        int endExclusive = Math.min(path.size(), startIdx + FORWARD_SCAN_TILES + 1);
        int bestIdx = -1;
        int bestDistFromPlayer = Integer.MAX_VALUE;
        for (int idx = startIdx; idx < endExclusive; idx++) {
            WorldPoint candidate = path.get(idx);
            if (candidate == null || candidate.getPlane() != playerLoc.getPlane()) {
                continue;
            }
            if (candidate.equals(playerLoc) || euclideanSq(candidate, playerLoc) > maxSq) {
                continue;
            }
            if (reachable != null && !reachable.isEmpty() && !reachable.contains(candidate)) {
                continue;
            }
            if (isClickable != null && !isClickable.test(candidate)) {
                continue;
            }
            int distFromPlayer = euclideanSq(candidate, playerLoc);
            if (bestIdx < 0 || idx > bestIdx || (idx == bestIdx && distFromPlayer > bestDistFromPlayer)) {
                bestIdx = idx;
                bestDistFromPlayer = distFromPlayer;
            }
        }
        return bestIdx;
    }

    /**
     * When the furthest in-range path tile is still beyond the minimap clip (e.g. a long diagonal
     * segment), returns a point on the line from the player toward {@code path[forwardIdx]} at roughly
     * {@code targetEuclidean} tiles out, provided it passes {@code isUsableClickTarget}; otherwise the
     * {@code fallbackWp}. Lets the recovery click as far along the route as the minimap allows.
     */
    public static WorldPoint interpolateClickableTarget(List<WorldPoint> path,
                                                        int forwardIdx,
                                                        WorldPoint playerLoc,
                                                        WorldPoint fallbackWp,
                                                        int targetEuclidean,
                                                        Predicate<WorldPoint> isUsableClickTarget) {
        if (path == null || playerLoc == null || fallbackWp == null
                || forwardIdx < 0 || forwardIdx >= path.size()) {
            return fallbackWp;
        }

        int fallbackDistSq = euclideanSq(fallbackWp, playerLoc);
        if (fallbackDistSq == targetEuclidean * targetEuclidean) {
            return fallbackWp;
        }

        WorldPoint beyond = path.get(forwardIdx);
        int dxB = beyond.getX() - playerLoc.getX();
        int dyB = beyond.getY() - playerLoc.getY();
        double distB = Math.sqrt(dxB * dxB + dyB * dyB);
        if (distB <= 1) {
            return fallbackWp;
        }

        double scale = targetEuclidean / distB;
        WorldPoint interpolated = new WorldPoint(
                playerLoc.getX() + (int) Math.round(dxB * scale),
                playerLoc.getY() + (int) Math.round(dyB * scale),
                playerLoc.getPlane());

        if (isUsableClickTarget == null || isUsableClickTarget.test(interpolated)) {
            return interpolated;
        }

        return fallbackWp;
    }

    /**
     * Whether the raw-path tile at {@code candidateIdx} is on the forward route within
     * {@code maxRawRouteSteps} raw steps of {@code routeStartIdx} — i.e. a spatially-near unreachable tile
     * is genuinely ahead on the route rather than a fold-back branch, so local recovery should target it.
     */
    public static boolean isLocalRecoveryCandidateOnForwardRoute(List<WorldPoint> rawPath,
                                                                 int[] smoothedToRaw,
                                                                 int routeStartIdx,
                                                                 int candidateIdx,
                                                                 int maxRawRouteSteps) {
        if (rawPath == null || rawPath.isEmpty() || smoothedToRaw == null
                || routeStartIdx < 0 || candidateIdx < routeStartIdx
                || routeStartIdx >= smoothedToRaw.length || candidateIdx >= smoothedToRaw.length
                || maxRawRouteSteps < 0) {
            return false;
        }
        int rawStart = smoothedToRaw[routeStartIdx];
        int rawCandidate = smoothedToRaw[candidateIdx];
        if (rawStart < 0 || rawCandidate < rawStart
                || rawStart >= rawPath.size() || rawCandidate >= rawPath.size()) {
            return false;
        }

        int routeSteps = 0;
        for (int i = rawStart; i < rawCandidate; i++) {
            int step = rawPathStepDistance(rawPath.get(i), rawPath.get(i + 1));
            if (step > maxRawRouteSteps - routeSteps) {
                return false;
            }
            routeSteps += step;
        }
        return true;
    }

    /**
     * When the walker is stuck at an unreachable frontier that is really the far side of a transport /
     * agility shortcut (e.g. a River Lum stepping stone), returns the nearest REACHABLE transport origin on
     * the forward raw route (from {@code startIndex}, within {@code forwardScanTiles} steps and
     * {@code maxEuclidean} tiles of the player), so recovery can walk the player ONTO it and let the normal
     * transport handler cross next tick. Returns {@code null} when none qualifies.
     * <p>
     * Pure and fully injected ({@code reachable} tiles and the {@code transports} map are parameters), so it
     * is exercised headlessly by {@code RouteRecoveryTest} rather than requiring a live walk. Rationale: the
     * far-side fallback otherwise clicks the opposite bank, which the client cannot reach, so the player
     * loops on the near bank and the transport (which only dispatches while standing on its origin) never
     * fires.
     */
    public static WorldPoint findReachableTransportOriginAhead(List<WorldPoint> rawPath,
                                                               int startIndex,
                                                               WorldPoint playerLoc,
                                                               Set<WorldPoint> reachable,
                                                               Map<WorldPoint, Set<Transport>> transports,
                                                               int maxEuclidean,
                                                               int forwardScanTiles) {
        if (rawPath == null || rawPath.isEmpty() || playerLoc == null || reachable == null
                || transports == null || transports.isEmpty() || startIndex < 0 || startIndex >= rawPath.size()) {
            return null;
        }
        int maxSq = maxEuclidean * maxEuclidean;
        int endExclusive = Math.min(rawPath.size(), startIndex + forwardScanTiles + 1);
        for (int ri = startIndex; ri < endExclusive; ri++) {
            WorldPoint wp = rawPath.get(ri);
            if (wp == null || wp.getPlane() != playerLoc.getPlane() || wp.equals(playerLoc)) {
                continue;
            }
            if (!reachable.contains(wp)) {
                continue; // must be walk-reachable from where the player is stranded right now
            }
            if (euclideanSq(wp, playerLoc) > maxSq) {
                continue; // within minimap-click reach
            }
            Set<Transport> ts = transports.get(wp);
            if (ts != null && !ts.isEmpty()) {
                return wp; // nearest reachable transport / shortcut origin ahead
            }
        }
        return null;
    }

    /**
     * From {@code startIdx}, returns the furthest path index still within {@code maxEuclidean} of the
     * player, stopping at the first transport origin (per {@code isTransportOrigin}) or a plane change. If
     * the start tile is already out of reach, walks backward to the nearest in-reach tile instead. Used by
     * recovery to pick how far to click along the route.
     */
    public static int findFurthestClickableIndex(List<WorldPoint> path, int startIdx, WorldPoint playerLoc,
                                                 Predicate<WorldPoint> isTransportOrigin, int maxEuclidean) {
        if (path == null || startIdx < 0 || startIdx >= path.size()) return startIdx;
        WorldPoint startWp = path.get(startIdx);
        final int maxSq = maxEuclidean * maxEuclidean;
        if (playerLoc != null && euclideanSq(startWp, playerLoc) > maxSq) {
            for (int j = startIdx - 1; j >= 0; j--) {
                WorldPoint candidate = path.get(j);
                if (candidate.getPlane() != playerLoc.getPlane()) continue;
                if (euclideanSq(candidate, playerLoc) <= maxSq) {
                    return j;
                }
            }
            return startIdx;
        }
        int bestIdx = startIdx;
        for (int j = startIdx + 1; j < path.size(); j++) {
            WorldPoint candidate = path.get(j);
            if (candidate.getPlane() != startWp.getPlane()) break;
            if (isTransportOrigin != null && isTransportOrigin.test(candidate)) break;
            if (playerLoc != null && euclideanSq(candidate, playerLoc) > maxSq) break;
            bestIdx = j;
        }
        return bestIdx;
    }

    /**
     * Like {@link #findFurthestClickableIndex} but always scanning forward from {@code startIdx} (never
     * walking backward): the furthest same-plane path index within {@code maxEuclidean} of the player,
     * stopping just before a transport origin.
     */
    public static int findFurthestForwardClickableIndex(List<WorldPoint> path, int startIdx, WorldPoint playerLoc,
                                                        Predicate<WorldPoint> isTransportOrigin, int maxEuclidean) {
        if (path == null || startIdx < 0 || startIdx >= path.size()) return startIdx;
        WorldPoint startWp = path.get(startIdx);
        if (startWp == null) return startIdx;
        final int maxSq = maxEuclidean * maxEuclidean;

        int bestIdx = startIdx;
        for (int j = startIdx; j < path.size(); j++) {
            WorldPoint candidate = path.get(j);
            if (candidate == null || candidate.getPlane() != startWp.getPlane()) break;
            if (j > startIdx && isTransportOrigin != null && isTransportOrigin.test(candidate)) break;
            if (playerLoc != null && euclideanSq(candidate, playerLoc) > maxSq) break;
            bestIdx = j;
        }
        return bestIdx;
    }

    /**
     * Clamps {@code target} to lie within {@code maxEuclidean} tiles of the player (same plane), stepping
     * back along the player→target line until it fits, so a recovery click never lands outside the minimap
     * clip. Returns {@code target} unchanged when it is already in range or cannot be clamped meaningfully.
     */
    public static WorldPoint clampToEuclideanRadius(WorldPoint playerLoc, WorldPoint target, int maxEuclidean) {
        if (playerLoc == null || target == null || target.getPlane() != playerLoc.getPlane() || maxEuclidean <= 0) {
            return target;
        }
        int maxSq = maxEuclidean * maxEuclidean;
        if (euclideanSq(playerLoc, target) <= maxSq) {
            return target;
        }

        int dx = target.getX() - playerLoc.getX();
        int dy = target.getY() - playerLoc.getY();
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance <= 1) {
            return target;
        }

        double scale = maxEuclidean / distance;
        int stepX = (int) Math.round(dx * scale);
        int stepY = (int) Math.round(dy * scale);
        if (stepX == 0 && stepY == 0) {
            if (Math.abs(dx) >= Math.abs(dy)) {
                stepX = Integer.signum(dx);
            } else {
                stepY = Integer.signum(dy);
            }
        }

        WorldPoint clamped = new WorldPoint(playerLoc.getX() + stepX, playerLoc.getY() + stepY, playerLoc.getPlane());
        while (!clamped.equals(playerLoc) && euclideanSq(playerLoc, clamped) > maxSq) {
            if (Math.abs(stepX) >= Math.abs(stepY) && stepX != 0) {
                stepX -= Integer.signum(stepX);
            } else if (stepY != 0) {
                stepY -= Integer.signum(stepY);
            } else {
                break;
            }
            clamped = new WorldPoint(playerLoc.getX() + stepX, playerLoc.getY() + stepY, playerLoc.getPlane());
        }
        return clamped.equals(playerLoc) ? target : clamped;
    }

    /** Squared Euclidean distance; local copy of the trivial helper to keep this class self-contained. */
    private static int euclideanSq(WorldPoint a, WorldPoint b) {
        int dx = a.getX() - b.getX();
        int dy = a.getY() - b.getY();
        return dx * dx + dy * dy;
    }

    /** One-step raw-path distance (cross-plane treated as effectively infinite); local trivial copy. */
    private static int rawPathStepDistance(WorldPoint previous, WorldPoint current) {
        if (previous == null || current == null || previous.getPlane() != current.getPlane()) {
            return Integer.MAX_VALUE / 4;
        }
        return Math.max(1, previous.distanceTo2D(current));
    }
}
