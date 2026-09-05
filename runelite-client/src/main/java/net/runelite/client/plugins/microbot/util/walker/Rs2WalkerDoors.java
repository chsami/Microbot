package net.runelite.client.plugins.microbot.util.walker;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.annotations.Component;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.*;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.devtools.MovementFlag;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.shortestpath.*;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.coords.Rs2LocalPoint;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldArea;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldPoint;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2Pvp;
import net.runelite.client.plugins.microbot.util.leaguetransport.Rs2LeaguesTransport;
import net.runelite.client.plugins.microbot.util.leaguetransport.SeasonalTransportHandler;
import net.runelite.client.plugins.microbot.util.leaguetransport.SeasonalTransportHandlers;
import net.runelite.client.plugins.microbot.util.logging.Rs2LogRateLimit;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.slf4j.event.Level;
import net.runelite.client.plugins.microbot.util.poh.PohTeleports;
import net.runelite.client.plugins.microbot.util.poh.PohTransport;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.leaguetransport.LeaguesRegion;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.walker.door.DoorAttemptLedger;
import net.runelite.client.plugins.microbot.util.walker.door.Rs2DoorClassifier;
import net.runelite.client.plugins.microbot.util.walker.door.DoorProbeContext;
import net.runelite.client.plugins.microbot.util.walker.door.Rs2DoorDetection;
import net.runelite.client.plugins.microbot.util.walker.door.Rs2DoorProbe;
import net.runelite.client.plugins.microbot.util.walker.door.Rs2DoorAheadResolver;
import net.runelite.client.plugins.microbot.util.walker.door.Rs2DoorGeometry;
import net.runelite.client.plugins.microbot.util.walker.geometry.WalkerPathGeometry;
import net.runelite.client.plugins.microbot.util.walker.obstacle.MineableResolver;
import net.runelite.client.plugins.microbot.util.walker.obstacle.ObstacleResolution;
import net.runelite.client.plugins.microbot.util.walker.obstacle.PlannedEdge;
import net.runelite.client.plugins.microbot.util.walker.recovery.FrontierDecision;
import net.runelite.client.plugins.microbot.util.walker.recovery.RouteRecovery;
import net.runelite.client.plugins.microbot.util.walker.segment.SegmentGate;
import net.runelite.client.plugins.microbot.util.walker.recovery.TailDecision;
import net.runelite.client.plugins.microbot.util.walker.state.WalkExit;
import net.runelite.client.plugins.microbot.util.walker.state.WalkerRouteState;
import net.runelite.client.plugins.microbot.util.walker.door.Rs2DoorHandler;
import net.runelite.client.plugins.microbot.util.walker.door.Rs2WalkerAwaits;
import net.runelite.client.plugins.microbot.util.walker.door.model.AwaitTicket;
import net.runelite.client.plugins.microbot.util.walker.door.model.DoorResolution;
import net.runelite.client.plugins.microbot.util.walker.banking.Rs2WalkerBankingPlanner;
import net.runelite.client.plugins.microbot.util.walker.awaits.Rs2WalkerRuntimeAwaits;
import net.runelite.client.plugins.microbot.util.walker.puzzles.DraynorBasementSolver;
import net.runelite.client.plugins.microbot.util.walker.stall.Rs2WalkerStallPolicy;
import net.runelite.client.plugins.microbot.util.walker.transport.Rs2WalkerTransportAwaits;
import net.runelite.client.plugins.microbot.util.walker.lifecycle.Rs2WalkerLifecycleRuntime;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import javax.inject.Named;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import static net.runelite.client.plugins.microbot.util.Global.*;
import static net.runelite.client.plugins.microbot.util.walker.Rs2Walker.*;
import static net.runelite.client.plugins.microbot.util.walker.Rs2WalkerMovement.*;
import static net.runelite.client.plugins.microbot.util.walker.Rs2WalkerTransports.*;

/**
 * The door component extracted from {@code Rs2Walker} (Phase E2, 2026-08-13): the door cascade's
 * entry points (segment handler, segment probe, raw-scan focus, pending/unresolved scans), the
 * interaction pipeline (throttle, exception, await, verify, nudge), the crossed-face guards and the
 * settle/defer predicates — the door-named methods and their exclusive helpers, moved verbatim.
 * Extraction, not unification: the cascade's branching is untouched. Members are package-private so
 * the walker and the transport component consume them via static import.
 */
@lombok.extern.slf4j.Slf4j
final class Rs2WalkerDoors {

    private static final ThreadLocal<Boolean> ADJACENT_BLOCKED_EDGE_ALLOWED = new ThreadLocal<>();

    private Rs2WalkerDoors() {
    }

    /** Same package (e.g. unit tests) only — not part of the script API. */
    static DoorAttemptLedger doorAttemptLedgerForTesting() {
        return doorAttemptLedger;
    }


    /**
     * An object standing ON the walk target is the destination, not an obstacle en route. The
     * Stronghold's Gift of Peace chest sits on the corridor walk's goal tile: the plan honestly ends
     * on the chest's tile, the tile reads sealed, and the blocker scan "opened" the goal itself —
     * ~9s of failed traversal per corridor run before arrived-within-distance conceded (observed on
     * three consecutive runs, 2026-08-13). Wall doors are exempt: a door on the goal tile's EDGE may
     * genuinely need opening to step onto the goal. The skip only applies when the walk is allowed
     * to finish from the near side without crossing, so a distance-0 walk onto an openable tile
     * still attempts the open honestly.
     */
    static boolean goalTileObjectIsNotAnObstacle(boolean wallDoor, WorldPoint target, int configuredDistance,
                                                 WorldPoint probe, WorldPoint fromWp, WorldPoint toWp) {
        if (wallDoor || target == null || fromWp == null || fromWp.getPlane() != target.getPlane()) {
            return false;
        }
        if (!target.equals(probe) && !target.equals(toWp)) {
            return false;
        }
        int finishThreshold = tightFinishThreshold(target, target, configuredDistance);
        return fromWp.distanceTo2D(target) <= finishThreshold;
    }

    static boolean isGoalTileObjectNotObstacle(TileObject object, WorldPoint probe,
                                                       WorldPoint fromWp, WorldPoint toWp) {
        return goalTileObjectIsNotAnObstacle(object instanceof WallObject, currentTarget, currentWalkDistance,
                probe, fromWp, toWp);
    }

    /** Door / gate from main path loop vs {@link #handleNearbyRawPathSceneObjects} raw-path scan (same nudge UX). */
    /**
     * Exit reasons meaning the path loop ended because the walker <em>did</em> something that
     * advances the route — opened a door, took a transport, cleared a blocker — or because
     * movement is already in flight. These are progress, not a failed attempt.
     *
     * <p>The partial-retry budget exists for "the goal is unreachable and we are stuck". Spending
     * it on these instead conflated the two: a door open ends the iteration, lands in the partial
     * branch, and burns a retry even though the walker just made progress. On a route whose path
     * end is permanently short of the goal (any partial path), the budget is armed for the whole
     * walk, so an ordinary door could exhaust it ~100 tiles into a working route and report
     * UNREACHABLE while the player was still advancing. See {@code movement.md} #25.
     */
    /** @return true only when a canvas click was actually issued, so the caller can size its minimap hold-off. */
    static boolean maybeCanvasNudgeAfterDoor(WorldPoint goal, int configuredDistance, List<WorldPoint> path) {
        if (goal == null || path == null || path.isEmpty()) {
            return false;
        }
        WorldPoint p = Rs2Player.getWorldLocation();
        if (p == null || goal.getPlane() != p.getPlane()) {
            return false;
        }
        if (isWalkCancelled(goal)) {
            return false;
        }
        WorldPoint pathLast = path.get(path.size() - 1);
        int finishTh = tightFinishThreshold(goal, pathLast, configuredDistance);
        int dGoal = p.distanceTo2D(goal);
        if (dGoal <= finishTh) {
            return false;
        }
        // Only nudge with fast-canvas when we are effectively on the final approach.
        // This avoids immediate scene-click jumps after ordinary mid-route door opens.
        if (dGoal > finishTh + FINAL_ADJACENT_CANVAS_NUDGE_CHEBYSHEV) {
            return false;
        }
        if (dGoal > DOOR_OPEN_CANVAS_NUDGE_MAX_GOAL_DIST) {
            return false;
        }
        LocalPoint goalLocal = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), goal);
        if (goalLocal == null || !Rs2Camera.isTileOnScreen(goalLocal)) {
            return false;
        }
        Map<WorldPoint, Integer> around = Rs2Tile.getReachableTilesFromTile(goal, DOOR_OPEN_CANVAS_NUDGE_GOAL_SAMPLE_RADIUS);
        if (around == null || around.isEmpty()) {
            return false;
        }
        List<WorldPoint> candidates = new ArrayList<>();
        for (WorldPoint t : around.keySet()) {
            if (t == null || !Rs2Tile.isTileReachable(t)) {
                continue;
            }
            if (p.distanceTo2D(t) > DOOR_OPEN_CANVAS_NUDGE_MAX_FROM_PLAYER) {
                continue;
            }
            candidates.add(t);
        }
        if (candidates.isEmpty()) {
            return false;
        }
        // candidates non-empty: index range [0, size-1] is valid for betweenInclusive.
        WorldPoint pick = candidates.get(Rs2Random.betweenInclusive(0, candidates.size() - 1));
        if (walkFastCanvas(pick)) {
            log.debug("[Walker] door nudge: canvas -> {} (goal={} dGoal={})", pick, goal, dGoal);
            waitUntilIdleAfterSceneWalk(goal, POST_SCENE_WALK_IDLE_WAIT_MS_MAX, goal, finishTh);
            routeState.lastMovedTimeMs = System.currentTimeMillis();
            routeState.stuckCount = 0;
            return true;
        }
        return false;
    }


    /**
     * Identity-only "is this walk still the active one": target unchanged, thread not interrupted.
     * <p>
     * Deliberately does NOT evaluate the caller's completion condition, and that is the whole point.
     * {@link #isWalkCancelled} runs a user-supplied {@code walkUntil} callback, which for the quester
     * means a radius-40 BFS inside {@code runOnClientThreadOptional}. Calling it from a 100ms wait
     * loop starved the client thread until every other thread's client-thread hop timed out — seen
     * live as TimeoutExceptions in an unrelated BlockingEvent and in the wait's own isMoving() read.
     * Completion is still evaluated by the walker's outer loop at its own checkpoints, so a
     * bounded wait costs at most its own budget before the caller's condition is honoured.
     */
    static boolean isWalkSuperseded(WorldPoint target) {
        WorldPoint activeTarget = currentTarget;
        return target == null || activeTarget == null || !target.equals(activeTarget)
                || Thread.currentThread().isInterrupted();
    }

    /**
     * Whether an ACTIONED scene door sits within one tile of either endpoint of the edge — the
     * double-gate wing case above. One scene scan (this path is rare and about to replan anyway),
     * geometric filter via {@link #doorTileAdjacentToEdgeEndpoints}.
     */
    static boolean sceneDoorAdjacentToEdge(WorldPoint a, WorldPoint b) {
        List<String> doorActions = List.of("pay-toll", "pick-lock", "walk-through", "go-through", "open", "pass");
        return !Rs2GameObject.getAll(o -> {
            WorldPoint loc = o.getWorldLocation();
            if (!doorTileAdjacentToEdgeEndpoints(loc, a, b)) {
                return false;
            }
            if (!Rs2DoorDetection.isDoorLikeSceneObject(o)) {
                return false;
            }
            ObjectComposition comp = Rs2DoorDetection.resolveCompositionForDoorProbe(o);
            return Rs2DoorClassifier.getDoorAction(comp, doorActions) != null;
        }, a, 3).isEmpty();
    }

    /**
     * Own the actionable wing beside the FIRST live-collision frontier before any route-ahead
     * scan runs. Double gates can put the action on a neighbouring wall object while the raw route
     * crosses the actionless wing. In that layout an exact-segment scan sees no first door and can
     * otherwise click a later, geometrically exact door through it.
     *
     * <p>Finding the candidate is enough to end this pass. The interaction pipeline may legitimately
     * return false while throttled, settling, or after an accepted click that has not traversed; none
     * of those states authorises a later door click.
     */
    static boolean handleDoorAdjacentToFirstBlockedFrontier(List<WorldPoint> rawPath,
                                                              WorldPoint playerLoc,
                                                              Map<WorldPoint, Integer> reachableCache) {
        WorldPoint[] edge = firstWalledRawEdge(rawPath, playerLoc, reachableCache,
                CLOSEST_INDEX_REACHABLE_STEP_BUDGET);
        if (edge == null) {
            return false;
        }

        List<String> doorActions = List.of("pay-toll", "pick-lock", "walk-through", "go-through", "open", "pass");
        AdjacentDoorCandidate candidate = captureActionedDoorAdjacentToBlockedEdge(
                edge[0], edge[1], playerLoc, doorActions);
        if (candidate == null) {
            return false;
        }

        WebWalkLog.spInfo("frontier_adjacent_door_owned | probe={} edge={}->{} player={}",
                compactWorldPoint(candidate.location), compactWorldPoint(edge[0]), compactWorldPoint(edge[1]),
                compactWorldPoint(playerLoc));
        tryHandleAdjacentBlockedDoorObject(candidate.object, candidate.location,
                edge[0], edge[1], doorActions, rawPath);
        return true;
    }

    static boolean handleFirstRecoveryDoor(List<WorldPoint> rawPath, long timeoutMs,
                                           WorldPoint playerLoc,
                                           Map<WorldPoint, Integer> reachableCache) {
        return handleDoorAdjacentToFirstBlockedFrontier(rawPath, playerLoc, reachableCache)
                || handlePendingDoorNearRawPath(rawPath, timeoutMs, playerLoc, 2, 14, reachableCache);
    }

    /** Pure geometry: same plane, and the door tile within one tile (Chebyshev) of either endpoint. */
    static boolean doorTileAdjacentToEdgeEndpoints(WorldPoint doorTile, WorldPoint a, WorldPoint b) {
        if (doorTile == null || a == null || b == null || doorTile.getPlane() != a.getPlane()) {
            return false;
        }
        return doorTile.distanceTo2D(a) <= 1 || doorTile.distanceTo2D(b) <= 1;
    }

    static boolean hasPendingDoorLikeSceneObjectBeforeDirectClick(List<WorldPoint> rawPath,
                                                                          List<WorldPoint> path,
                                                                          WorldPoint playerLoc,
                                                                          int directClickMaxDistance) {
        List<WorldPoint> route = rawPath != null && rawPath.size() >= 2 ? rawPath : path;
        if (route == null || route.size() < 2 || playerLoc == null) {
            return false;
        }

        int closest = getClosestTileIndex(route, playerLoc);
        if (closest < 0 || closest >= route.size()) {
            return false;
        }

        int maxEdges = 12;
        int radius = Math.max(3, directClickMaxDistance + 2);
        int start = Math.max(0, closest - 2);
        int endExclusive = Math.min(route.size() - 1, start + maxEdges);
        for (int i = start; i < endExclusive; i++) {
            WorldPoint from = route.get(i);
            WorldPoint to = route.get(i + 1);
            if (from == null || to == null) {
                continue;
            }
            if (from.getPlane() != playerLoc.getPlane() || to.getPlane() != playerLoc.getPlane()) {
                break;
            }
            if (from.distanceTo2D(playerLoc) > radius && to.distanceTo2D(playerLoc) > radius) {
                break;
            }
            if (shouldDeferDoorHandlingToTransport(route, i)) {
                continue;
            }
            if (hasDoorLikeSceneObjectOnSegment(from, to, playerLoc, radius)) {
                return true;
            }
        }
        return false;
    }

    static boolean handlePendingDoorDuringInterim(List<WorldPoint> rawPath,
                                                          long timeoutMs,
                                                          WorldPoint playerLoc) {
        // Timed even though the guards "do nothing": this runs once per tile per pass while an
        // interim is in flight, and the guard chain itself pays client-thread hops (isMoving).
        long passT0 = System.currentTimeMillis();
        try {
            if (rawPath == null || rawPath.size() < 2 || playerLoc == null
                    || isDoorInteractionSettling() || isDoorEdgePassSkipCoolingDown()
                    || isRecoveryMovementInFlight() || Rs2Player.isMoving()) {
                return false;
            }

            return handlePendingDoorNearRawPath(rawPath, timeoutMs, playerLoc, 2, 14);
        } finally {
            WalkPassStats.doorProbeMs.addAndGet(System.currentTimeMillis() - passT0);
        }
    }

    static boolean handlePendingDoorNearRawPath(List<WorldPoint> rawPath,
                                                        long timeoutMs,
                                                        WorldPoint playerLoc,
                                                        int backtrackEdges,
                                                        int lookaheadEdges) {
        return handlePendingDoorNearRawPath(rawPath, timeoutMs, playerLoc,
                backtrackEdges, lookaheadEdges, null);
    }

    static boolean handlePendingDoorNearRawPath(List<WorldPoint> rawPath,
                                                long timeoutMs,
                                                WorldPoint playerLoc,
                                                int backtrackEdges,
                                                int lookaheadEdges,
                                                Map<WorldPoint, Integer> reachableCache) {
        if (rawPath == null || rawPath.size() < 2 || playerLoc == null) {
            return false;
        }
        if (Rs2Player.isMoving()) {
            return false;
        }

        int rawStart = getClosestTileIndex(rawPath, playerLoc);
        if (rawStart < 0) {
            return false;
        }

        int start = Math.max(0, rawStart - Math.max(0, backtrackEdges));
        int endExclusive = Math.min(rawPath.size() - 1, rawStart + Math.max(1, lookaheadEdges));
        for (int ri = start; ri < endExclusive && ri < rawPath.size() - 1; ri++) {
            WorldPoint a = rawPath.get(ri);
            WorldPoint b = rawPath.get(ri + 1);
            if (a == null || b == null) {
                continue;
            }
            if (a.getPlane() != playerLoc.getPlane() || b.getPlane() != playerLoc.getPlane()) {
                break;
            }
            // Route order is also interaction order. If the near side of this edge cannot be
            // reached in the live collision map, an earlier obstacle is still unresolved; do not
            // look through it and click a later visible door.
            if (!isDoorNearSideReachable(reachableCache, a)) {
                break;
            }
            if (a.distanceTo2D(playerLoc) > HANDLER_RANGE && b.distanceTo2D(playerLoc) > HANDLER_RANGE) {
                continue;
            }
            if (shouldDeferDoorHandlingToTransport(rawPath, ri)) {
                continue;
            }
            if (!hasDoorLikeSceneObjectOnSegment(a, b, playerLoc, HANDLER_RANGE)) {
                continue;
            }
            if (handleDoorsWithTimeoutBudgeted(rawPath, ri, timeoutMs, true)) {
                return true;
            }
        }
        return false;
    }

    static boolean isDoorNearSideReachable(Map<WorldPoint, Integer> reachableCache, WorldPoint fromWp) {
        return reachableCache == null || fromWp != null && reachableCache.containsKey(fromWp);
    }

    /** Wraps the current scan-scoped probe caches for the extracted door-probe logic. */
    static DoorProbeContext doorProbeContext() {
        return new DoorProbeContext(rawScanWallSnapshot, rawScanGameObjectSnapshot,
                rawScanDoorLocationSnapshot, rawScanDoorCompositionCache, rawScanDoorSegmentCache,
                rawScanDoorEligibilityCache);
    }

    /**
     * The door menu click, timed. "doorOther" is the residual left after the probe and both waits, and
     * at ~790ms of a 3181ms scan it is the only part of door handling that is neither the player
     * walking nor a scan — so it needs its own number before anyone optimises against it.
     */
    static boolean interactDoorTimed(TileObject object, String action) {
        long startedAt = System.currentTimeMillis();
        try {
            return Rs2GameObject.interact(object, action);
        } finally {
            long tookMs = System.currentTimeMillis() - startedAt;
            if (rawScanWallSnapshot != null || rawScanGameObjectSnapshot != null) {
                rawScanDoorInteractMs += tookMs;
            }
            doorLegInteractMs += tookMs;
        }
    }

    static void resetDoorLegStages() {
        doorLegFindMs = 0L;
        doorLegInteractMs = 0L;
        doorLegAwaitMs = 0L;
        doorLegVerifyMs = 0L;
        doorLegNudgeMs = 0L;
        doorLegExceptionMs = 0L;
    }

    static String doorLegStageDetail(long totalMs) {
        long accounted = doorLegFindMs + doorLegInteractMs + doorLegAwaitMs + doorLegVerifyMs
                + doorLegNudgeMs + doorLegExceptionMs;
        return " find=" + doorLegFindMs + " interact=" + doorLegInteractMs + " await=" + doorLegAwaitMs
                + " verify=" + doorLegVerifyMs + " nudge=" + doorLegNudgeMs + " exception=" + doorLegExceptionMs
                + " other=" + Math.max(0L, totalMs - accounted);
    }

    /**
     * "Is THIS door still shut?" — a radius-{@link #HANDLER_RANGE} rescan that resolves a composition per
     * candidate OUTSIDE the scan-scoped memo, so nothing is cached. Only runs when traversal failed, but
     * that is exactly the slow path a stuck door repeats, so it is timed separately.
     * <p>
     * STRICT on the probe tile, for the same reason {@code doorObservedOpen} is: this answer decides
     * whether the door we just interacted with opened, and the loose two-tile radius let a NEIGHBOURING
     * shut door answer for it. Measured live as {@code saw=strict=false loose=true} — this door open,
     * a neighbour shut — reading as "did not traverse", which suppressed markStationaryDoorOpened and
     * the post-door route click entirely; the walker stood still ~2s until the generic click machinery
     * caught up. In a door-heavy area (the exact place chaining matters) that was every door.
     */
    static boolean doorStillHasActionTimed(WorldPoint probe, WorldPoint fromWp, WorldPoint toWp,
                                                   List<String> doorActions, String action) {
        long startedAt = System.currentTimeMillis();
        try {
            return doorStillHasAction(probe, fromWp, toWp, doorActions, action, true);
        } finally {
            long tookMs = System.currentTimeMillis() - startedAt;
            if (rawScanWallSnapshot != null || rawScanGameObjectSnapshot != null) {
                rawScanDoorVerifyMs += tookMs;
            }
            doorLegVerifyMs += tookMs;
        }
    }

    /**
     * The door segment probe, timed. "doorProbe" in the slow-scan line is a RESIDUAL — the whole
     * handleDoors call minus the interaction wait — so it silently absorbed the edge-resolution wait,
     * the menu interaction and the post-interaction verification too. Attributing the probe itself is
     * the only way to tell an expensive scan from an expensive wait, and they want opposite fixes.
     */
    static TileObject findDoorNearSegmentTimed(WorldPoint fromWp, WorldPoint toWp, List<String> doorActions) {
        long startedAt = System.currentTimeMillis();
        try {
            return Rs2DoorProbe.findDoorNearSegment(doorProbeContext(), doorAttemptLedger,
                    STATIONARY_DOOR_SUPPRESS_MS, fromWp, toWp, doorActions);
        } finally {
            long tookMs = System.currentTimeMillis() - startedAt;
            if (rawScanWallSnapshot != null || rawScanGameObjectSnapshot != null) {
                rawScanDoorFindMs += tookMs;
            }
            doorLegFindMs += tookMs;
        }
    }


    /**
     * Exact-tile match first, then a one-tile adjacency fallback — the same preference order the
     * previous pair of bounded queries produced.
     */
    static WallObject resolveProbeWallObject(WorldPoint probe) {
        List<WallObject> snapshot = rawScanWallSnapshot;
        if (snapshot != null) {
            WallObject adjacent = null;
            for (WallObject candidate : snapshot) {
                if (candidate == null) {
                    continue;
                }
                WorldPoint loc = candidate.getWorldLocation();
                if (loc == null) {
                    continue;
                }
                if (loc.equals(probe)) {
                    return candidate;
                }
                if (adjacent == null && loc.distanceTo2D(probe) <= 1) {
                    adjacent = candidate;
                }
            }
            return adjacent;
        }
        WallObject wall = Rs2GameObject.getWallObject(o -> o.getWorldLocation().equals(probe), probe, 3);
        if (wall == null) {
            wall = Rs2GameObject.getWallObject(o -> o.getWorldLocation().distanceTo2D(probe) <= 1, probe, 3);
        }
        return wall;
    }

    /** @see #resolveProbeWallObject(WorldPoint) */
    static TileObject resolveProbeGameObject(WorldPoint probe) {
        List<GameObject> snapshot = rawScanGameObjectSnapshot;
        if (snapshot != null) {
            GameObject adjacent = null;
            for (GameObject candidate : snapshot) {
                if (candidate == null) {
                    continue;
                }
                WorldPoint loc = candidate.getWorldLocation();
                if (loc == null) {
                    continue;
                }
                if (loc.equals(probe)) {
                    return candidate;
                }
                if (adjacent == null && loc.distanceTo2D(probe) <= 1) {
                    adjacent = candidate;
                }
            }
            return adjacent;
        }
        TileObject object = Rs2GameObject.getGameObject(o -> o.getWorldLocation().equals(probe), probe, 3);
        if (object == null) {
            object = Rs2GameObject.getGameObject(o -> o.getWorldLocation().distanceTo2D(probe) <= 1, probe, 3);
        }
        return object;
    }

    static boolean hasDoorCandidateOnRawSegment(List<WorldPoint> rawPath, int index) {
        if (rawPath == null || index < 0 || index >= rawPath.size() - 1) {
            return false;
        }
        if (shouldDeferDoorHandlingToTransport(rawPath, index)) {
            return false;
        }
        boolean isInstance = Microbot.getClient()
                .getTopLevelWorldView()
                .getScene()
                .isInstance();
        WorldPoint rawFrom = rawPath.get(index);
        WorldPoint rawTo = rawPath.get(index + 1);
        WorldPoint fromWp = isInstance ? Rs2WorldPoint.convertInstancedWorldPoint(rawFrom) : rawFrom;
        WorldPoint toWp = isInstance ? Rs2WorldPoint.convertInstancedWorldPoint(rawTo) : rawTo;
        if (fromWp == null || toWp == null || fromWp.getPlane() != toWp.getPlane()) {
            return false;
        }
        List<String> doorActions = List.of("pay-toll", "pick-lock", "walk-through", "go-through", "open", "pass");
        return findDoorNearSegmentTimed(fromWp, toWp, doorActions) != null;
    }

    static void setRawScanDoorFocus(int index) {
        doorAttemptLedger.setRawScanFocus(index, System.currentTimeMillis());
    }

    static boolean shouldUseFocusedRawDoorIndex(List<WorldPoint> rawPath, int rawStartIdx) {
        Integer idx = doorAttemptLedger.rawScanFocusDoorIdx();
        if (idx == null) {
            return false;
        }
        if (routeState.interimTargetWp != null) {
            return false;
        }
        if (System.currentTimeMillis() - doorAttemptLedger.rawScanFocusSetAtMs() > RAW_SCAN_DOOR_FOCUS_MAX_MS) {
            return false;
        }
        if (doorAttemptLedger.rawScanFocusAttempts() >= RAW_SCAN_DOOR_FOCUS_MAX_ATTEMPTS) {
            return false;
        }
        if (idx < 0 || idx >= rawPath.size() - 1) {
            return false;
        }
        if (rawStartIdx > idx + 1) {
            return false;
        }
        return Math.abs(rawStartIdx - idx) <= 2;
    }

    static void clearRawScanDoorFocus(String reason) {
        if (doorAttemptLedger.rawScanFocusDoorIdx() != null && debug) {
            walkerDiag("clear raw door focus: %s", reason);
        }
        doorAttemptLedger.clearRawScanFocus();
    }

    static boolean hasQuestLockKeywords(String text) {
        if (text == null || text.isEmpty()) return false;
        String lc = text.toLowerCase();
        // Phrases that consistently appear on quest/stat-gated doors and gates.
        return lc.contains("quest") || lc.contains("you need to") || lc.contains("you must")
                || lc.contains("you have not") || lc.contains("cannot enter")
                || lc.contains("can't enter") || lc.contains("requires you");
    }

    static boolean isQuestLockedDoorDialogue() {
        if (!Rs2Dialogue.isInDialogue()) return false;
        return hasQuestLockKeywords(Rs2Dialogue.getDialogueText());
    }

    static boolean handleDoors(List<WorldPoint> path, int index) {
        return handleDoors(path, index, false);
    }

    static boolean handleDoors(List<WorldPoint> path, int index, boolean allowSegmentProbe) {
        if (!Rs2PathApi.getActiveRouteStatus().isPresent() || index >= path.size() - 1) return false;

        // Skip any door whose tile was blacklisted after a prior quest-lock detection —
        // avoid re-triggering the same failed interact loop this session.
        WorldPoint skipFrom = path.get(index);
        WorldPoint skipTo = index + 1 < path.size() ? path.get(index + 1) : null;
        if (doorAttemptLedger.isDoorBlacklisted(skipFrom)
                || (skipTo != null && doorAttemptLedger.isDoorBlacklisted(skipTo))) {
            return false;
        }

        List<String> doorActions = List.of("pay-toll", "pick-lock", "walk-through", "go-through", "open", "pass");
        boolean isInstance = Microbot.getClient()
                .getTopLevelWorldView()
                .getScene()
                .isInstance();

        WorldPoint rawFrom = path.get(index);
        WorldPoint rawTo = path.get(index + 1);
        WorldPoint fromWp = isInstance
                ? Rs2WorldPoint.convertInstancedWorldPoint(rawFrom)
                : rawFrom;
        WorldPoint toWp = isInstance
                ? Rs2WorldPoint.convertInstancedWorldPoint(rawTo)
                : rawTo;

        if (isInstance && (toWp == null || fromWp == null)) {
            // Expected inside the PoH when the next tile is a teleport destination
            // (convertInstancedWorldPoint -> fromWorldInstance returns null for tiles
            // that aren't in the current instance chunk). Log path context so
            // unexpected occurrences outside that case can be diagnosed.
            log.debug("[Walker] handleDoors: POH/instance conversion returned null (rawFrom={} fromWp={} rawTo={} toWp={}) idx={}/{} — skipping door check",
                    rawFrom, fromWp, rawTo, toWp, index, path.size());
            return false;
        }

        // Cross-plane path steps are always transports (stairs, ladders, trapdoors) —
        // door probes on mismatched planes would emit wrong-plane corner coordinates
        // and the plane-guard below would reject them anyway. Let handleTransports
        // take it.
        if (fromWp.getPlane() != toWp.getPlane()) {
            return false;
        }

        // A door edge the player has already CROSSED (in route direction) is resolved for this walk,
        // whatever the door reads now. The Fight Arena quest doors shut themselves the moment you are
        // through, so "shut door on my route" stayed true after crossing and the machinery kept
        // re-engaging a door behind the player — watched live as the character stepping BACK through
        // the door it had just passed, then oscillating. The axis reading is directional, so a walk
        // genuinely routed back the other way derives the reversed edge from its own route tiles and
        // is unaffected.
        WorldPoint playerForCrossing = Rs2Player.getWorldLocation();
        if (playerForCrossing != null
                && Rs2DoorGeometry.crossedDoorAxis(fromWp, toWp, playerForCrossing)) {
            return false;
        }

        if (shouldDeferDoorHandlingToTransport(path, index)) {
            return false;
        }

        if (recentlyOpenedStationaryDoorOnSegment(fromWp, toWp)) {
            return false;
        }

        // A broad raw scan already owns immutable wall/game-object snapshots. Resolve the
        // segment directly from them instead of running the probe loop, which repeatedly
        // requested the same object definitions on the client thread for adjacent raw edges.
        if (allowSegmentProbe
                && (rawScanWallSnapshot != null || rawScanGameObjectSnapshot != null)) {
            TileObject snapshotDoor = findDoorNearSegmentTimed(fromWp, toWp, doorActions);
            if (snapshotDoor == null) {
                return false;
            }
            if (snapshotDoor instanceof WallObject) {
                return tryHandleDoorObject(snapshotDoor, snapshotDoor.getWorldLocation(),
                        fromWp, toWp, doorActions, true, path);
            }
        }

        for (int offset = 0; offset <= 1; offset++) {
            int doorIdx = index + offset;
            if (doorIdx >= path.size()) continue;

            WorldPoint rawDoorWp = path.get(doorIdx);
            WorldPoint doorWp = isInstance
                    ? Rs2WorldPoint.convertInstancedWorldPoint(rawDoorWp)
                    : rawDoorWp;

            List<WorldPoint> probes = Rs2DoorAheadResolver.buildSegmentProbes(fromWp, toWp, doorWp);

            for (WorldPoint probe : probes) {
                if (recentlyOpenedStationaryDoorOnSegment(fromWp, toWp)) {
                    return false;
                }
                boolean adjacentToPath = probe.distanceTo(fromWp) <= 1 || probe.distanceTo(toWp) <= 1;
                WorldPoint playerLoc = Rs2Player.getWorldLocation();
                if (!adjacentToPath || playerLoc == null || !Objects.equals(probe.getPlane(), playerLoc.getPlane())) continue;

                // WallObjects can report their world location as an adjacent tile depending on
                // orientation / scene representation. Use exact match first, then allow a small
                // adjacency fallback so door handling triggers reliably.
                WallObject wall = resolveProbeWallObject(probe);

                TileObject object = (wall != null) ? wall : resolveProbeGameObject(probe);
                if (object == null) continue;
                if (!Rs2DoorGeometry.isDoorInteractionWithinRange(object, probe, fromWp, toWp, playerLoc, HANDLER_RANGE)) {
                    Telemetry.recordDoorReject("door-out-of-range");
                    continue;
                }
                if (Rs2DoorProbe.isCatalogTransportObject(object)) {
                    Telemetry.recordDoorReject("catalog-transport-object");
                    continue;
                }

                ObjectComposition baseComp = Rs2GameObject.convertToObjectComposition(object);
                ObjectComposition comp = Rs2DoorDetection.resolveCompositionForDoorProbe(object);
                if (comp == null) {
                    Telemetry.recordDoorReject("composition-null");
                    continue;
                }
                if (baseComp != null && baseComp.getImpostorIds() != null
                        && !Rs2DoorClassifier.isNullOrPlaceholderObjectName(baseComp.getName())
                        && Rs2DoorClassifier.isNullOrPlaceholderObjectName(comp.getName())) {
                    Telemetry.recordDoorReject("impostor-rejected");
                    continue;
                }
                if (Rs2DoorClassifier.isNullOrPlaceholderObjectName(comp.getName())) {
                    Telemetry.recordDoorReject("name-not-door");
                    continue;
                }

                if (Rs2DoorClassifier.doorCompositionSpecifiesOnlyCloseOrShut(comp)) {
                    Telemetry.recordDoorReject("skip-close-only-open");
                    continue;
                }

                String action = Rs2DoorClassifier.pickWalkDoorAction(comp);
                if (action == null) {
                    Telemetry.recordDoorReject("no-walk-action");
                    continue;
                }
                if (Rs2DoorClassifier.doorActionPriorityIndex(action) == Integer.MAX_VALUE) {
                    Telemetry.recordDoorReject("non-standard-door-action");
                    continue;
                }

                boolean found = false;

                final String name = comp.getName();

                if (object instanceof WallObject) {
                    // Validate the door's ACTUAL blocked edge against the segment, not the probe
                    // tile. The probe can sit a tile off the wall (adjacency fallback above), and the
                    // old probe-orientation check plus the pathTouchesBothEnds shortcut opened doors
                    // merely beside the path. isDoorOnSegment walks the segment against the wall's
                    // real edge, matching the GameObject branch and findDoorNearSegment.
                    if (Rs2DoorGeometry.isDoorOnSegment(object, fromWp, toWp)) {
                        if (isPlayerBeyondDoorFace((WallObject) object, fromWp)) {
                            WebWalkLog.spInfo("door_skip_crossed | mode=segment-door probe={} from={} — already past the face; clicking would carry us back",
                                    compactWorldPoint(probe), compactWorldPoint(fromWp));
                            return false;
                        }
                        log.debug("Found WallObject door - name {} with action {} at {} - from {} to {}", name, action, probe, fromWp, toWp);
                        found = true;
                    } else {
                        Telemetry.recordDoorReject("orient-mismatch");
                    }
                } else {
                    if (!Rs2DoorClassifier.isRouteDoorObject(false, name, action)) {
                        Telemetry.recordDoorReject("gameobject-not-a-door");
                        continue;
                    }
                    if (isGoalTileObjectNotObstacle(object, probe, fromWp, toWp)) {
                        WebWalkLog.spInfo("door_skip_goal_object | mode=segment-door probe={} from={} — the goal tile's own object is the destination, not an obstacle; finishing within distance",
                                compactWorldPoint(probe), compactWorldPoint(fromWp));
                        return false;
                    }
                    if (Rs2DoorGeometry.isDoorOnSegment(object, fromWp, toWp)) {
                        log.debug("Found GameObject door - name {} with action {} at {} - from {} to {}", name, action, probe, fromWp, toWp);
                        found = true;
                    } else {
                        Telemetry.recordDoorReject("gameobject-segment-mismatch");
                    }
                }

                if (found) {
                    if (!handleDoorException(object, action)) {
                        if (shouldThrottleDoorAttempt(probe, fromWp, toWp)) {
                            WebWalkLog.spInfo("door_attempt_throttled | mode=segment-door probe={} from={} to={}",
                                    compactWorldPoint(probe), compactWorldPoint(fromWp), compactWorldPoint(toWp));
                            return false;
                        }
                        if (shouldThrottleGlobalDoorInteraction(fromWp, toWp)) {
                            WebWalkLog.spInfo("door_global_await | mode=segment-door probe={} from={} to={}",
                                    compactWorldPoint(probe), compactWorldPoint(fromWp), compactWorldPoint(toWp));
                            return false;
                        }
                        // Detection transfers this route edge to the door transaction before a
                        // movement defer can yield. Keep it after the global check: that check uses
                        // the previous claim to distinguish same-edge hammering from chained doors.
                        doorAttemptLedger.claimDetectedEdge(fromWp, toWp, System.currentTimeMillis());
                        if (doorInteractionDeferredForMovement(probe)) {
                            WebWalkLog.spInfo("door_interact_deferred | reason=moving mode=segment-door probe={} from={} to={}",
                                    compactWorldPoint(probe), compactWorldPoint(fromWp), compactWorldPoint(toWp));
                            return false;
                        }
                        markDoorAttempt(probe, fromWp, toWp);
                        markGlobalDoorInteractionCooldown();
                        WorldPoint posBefore = Rs2Player.getWorldLocation();
                        boolean interacted;
                        try {
                            interacted = interactDoorTimed(object, action);
                        } catch (Exception ex) {
                            WebWalkLog.spInfo("door_interact_exception | mode=segment-door probe={} from={} to={} ex={}",
                                    compactWorldPoint(probe), compactWorldPoint(fromWp), compactWorldPoint(toWp), ex.getClass().getSimpleName());
                            return false;
                        }
                        if (!interacted) {
                            WebWalkLog.spInfo("door_interact_failed | mode=segment-door probe={} from={} to={}",
                                    compactWorldPoint(probe), compactWorldPoint(fromWp), compactWorldPoint(toWp));
                            return false;
                        }
                        markDoorInteractionSettling(toWp);
                        waitForDoorInteractionProgress(fromWp, toWp, probe, doorActions, action, object);
                        WorldPoint posAfter = Rs2Player.getWorldLocation();
                        boolean traversed = didTraverseInteractedDoor(posBefore, posAfter, probe, fromWp, toWp);
                        if (!traversed && isQuestLockedDoorDialogue()) {
                            String dialogue = Rs2Dialogue.getDialogueText();
                            log.warn("[Walker] Door at {} ({} action={}) appears quest/stat-locked — dialogue=\"{}\" — blacklisting tile, refreshing restrictions, recalculating",
                                    probe, name, action, dialogue);
                            doorAttemptLedger.blacklistDoor(probe);
                            Rs2Dialogue.clickContinue();
                            Rs2PathApi.refreshPlanningConfiguration();
                            recalculatePath();
                            // Resolved by rerouting; return before the wrong-traversal branch so a
                            // quest/skill-locked door is never learned as a blocked edge (it unlocks when the
                            // requirement is met). Matches the tryHandleDoorObject quest-locked path.
                            return true;
                        }
                        if (!traversed) {
                            if (shouldBlacklistDoorAfterWrongTraversal(posBefore, posAfter, fromWp, toWp, Rs2Player.isMoving())) {
                                doorAttemptLedger.blacklistDoor(probe);
                                log.warn("[Walker] Blacklisting door after wrong traversal: door={} from={} to={} before={} after={}",
                                        probe, fromWp, toWp, posBefore, posAfter);
                                // Wrong-traversal is a stable map property (one-way / mis-encoded door geometry),
                                // so persist it as a learned block that survives restarts and reroutes future paths.
                                // (Quest/skill-locked doors take the isQuestLockedDoorDialogue() branch above and are
                                // deliberately NOT learned — they unlock when the requirement is met.)
                                //
                                // Unless the CATALOG declares this edge. There we asserted the crossing ourselves,
                                // so a disagreement is a door-handling failure or a bad row — either way something
                                // to fix at the source, not to record silently. Wydin's back-room door (transport
                                // 2069, added to make Pirate's Treasure work at all) was learned blocked here and
                                // would have quietly undone that fix with nothing in the log to connect the two.
                                // Warn instead, so a genuinely wrong catalog row is visible rather than absorbed.
                                if (Rs2PathApi.hasCatalogTransportEdge(fromWp, toWp)) {
                                    log.warn("[Walker] Wrong traversal across CATALOG transport edge {} -> {} (door={}); "
                                                    + "not learning it blocked — fix the transport row if this recurs",
                                            fromWp, toWp, probe);
                                } else {
                                    Rs2PathApi.learnBlockedEdge(fromWp, toWp,
                                            "wrong-traversal door @ " + compactWorldPoint(probe));
                                }
                            }
                            if (doorStillHasActionTimed(probe, fromWp, toWp, doorActions, action)) {
                                log.debug("[Walker] Door interaction did not traverse; action still present at {} ({} -> {})",
                                        probe, fromWp, toWp);
                                registerDoorCrossFailure(fromWp, toWp,
                                        isConclusiveRefusedOpenSample(posAfter, fromWp), "refused-open");
                            } else {
                                markStationaryDoorOpened(probe);
                                if (tryDoorEdgeCrossNudge(fromWp, toWp, currentTarget, path)) {
                                    markNearbyDoorFamilyOpened(object, probe, action, SEGMENT_DOOR_FAMILY_MARK_RADIUS);
                                    return true;
                                }
                            }
                            return false;
                        }
                        clearDoorCrossFailures(fromWp, toWp);
                        markStationaryDoorOpened(probe);
                        markNearbyDoorFamilyOpened(object, probe, action, SEGMENT_DOOR_FAMILY_MARK_RADIUS);
                    }
                    return true;
                }
            }
        }

        TileObject nearbyDoor = allowSegmentProbe ? findDoorNearSegmentTimed(fromWp, toWp, doorActions) : null;
        if (nearbyDoor != null && tryHandleDoorObject(nearbyDoor, nearbyDoor.getWorldLocation(), fromWp, toWp, doorActions, true, path)) {
            return true;
        }

        return false;
    }

    static boolean tryHandleDoorObject(TileObject object, WorldPoint probe, WorldPoint fromWp, WorldPoint toWp,
                                               List<String> doorActions, boolean allowSegmentProbe,
                                               List<WorldPoint> routePath) {
        boolean allowAdjacentBlockedEdge = Boolean.TRUE.equals(ADJACENT_BLOCKED_EDGE_ALLOWED.get());
        if (object == null || probe == null) return false;
        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        if (!Rs2DoorGeometry.isDoorInteractionWithinRange(object, probe, fromWp, toWp, playerLoc, HANDLER_RANGE)) {
            return false;
        }
        if (Rs2DoorProbe.isCatalogTransportObject(object)) {
            return false;
        }

        ObjectComposition comp = Rs2DoorProbe.resolveDoorComposition(doorProbeContext(), object);
        if (!Rs2DoorClassifier.isDoorComposition(comp, doorActions)) return false;

        String action = Rs2DoorClassifier.getDoorAction(comp, doorActions);
        if (action == null) return false;

        boolean found = false;
        final String name = comp.getName();

        if (object instanceof WallObject) {
            int orientation = ((WallObject) object).getOrientationA();

            if (searchNeighborPoint(orientation, probe, fromWp)
                    || searchNeighborPoint(orientation, probe, toWp)
                    || (allowSegmentProbe && Rs2DoorGeometry.wallDoorTouchesSegment((WallObject) object, fromWp, toWp))
                    || (allowAdjacentBlockedEdge && doorTileAdjacentToEdgeEndpoints(probe, fromWp, toWp))) {
                if (!allowAdjacentBlockedEdge && isPlayerBeyondDoorFace((WallObject) object, fromWp)) {
                    WebWalkLog.spInfo("door_skip_crossed | mode=segment-probe probe={} from={} — already past the face; clicking would carry us back",
                            compactWorldPoint(probe), compactWorldPoint(fromWp));
                    return false;
                }
                log.debug("Found WallObject door - name {} with action {} at {} - from {} to {}", name, action, probe, fromWp, toWp);
                found = true;
            }
        } else if (Rs2DoorClassifier.isRouteDoorObject(false, name, action)) {
            if (isGoalTileObjectNotObstacle(object, probe, fromWp, toWp)) {
                WebWalkLog.spInfo("door_skip_goal_object | mode=segment-probe probe={} from={} — the goal tile's own object is the destination, not an obstacle; finishing within distance",
                        compactWorldPoint(probe), compactWorldPoint(fromWp));
                return false;
            }
            if (Rs2DoorGeometry.isDoorOnSegment(object, fromWp, toWp)
                    || (allowAdjacentBlockedEdge && doorTileAdjacentToEdgeEndpoints(probe, fromWp, toWp))) {
                log.debug("Found GameObject door - name {} with action {} at {} - from {} to {}", name, action, probe, fromWp, toWp);
                found = true;
            }
        }

        if (!found) return false;

        if (handleDoorException(object, action)) {
            return true;
        }

        if (shouldThrottleDoorAttempt(probe, fromWp, toWp)) {
            WebWalkLog.spInfo("door_attempt_throttled | mode=segment-probe probe={} from={} to={}",
                    compactWorldPoint(probe), compactWorldPoint(fromWp), compactWorldPoint(toWp));
            return false;
        }
        if (shouldThrottleGlobalDoorInteraction(fromWp, toWp)) {
            WebWalkLog.spInfo("door_global_await | mode=segment-probe probe={} from={} to={}",
                    compactWorldPoint(probe), compactWorldPoint(fromWp), compactWorldPoint(toWp));
            return false;
        }
        // Claim on detection, not only after the movement gate has passed; recovery must not issue
        // a competing click while this exact route-door interaction approaches the edge.
        doorAttemptLedger.claimDetectedEdge(fromWp, toWp, System.currentTimeMillis());
        if (doorInteractionDeferredForMovement(probe)) {
            WebWalkLog.spInfo("door_interact_deferred | reason=moving mode=segment-probe probe={} from={} to={}",
                    compactWorldPoint(probe), compactWorldPoint(fromWp), compactWorldPoint(toWp));
            return false;
        }
        markDoorAttempt(probe, fromWp, toWp);
        markGlobalDoorInteractionCooldown();
        WorldPoint posBefore = Rs2Player.getWorldLocation();
        boolean interacted;
        try {
            interacted = interactDoorTimed(object, action);
        } catch (Exception ex) {
            WebWalkLog.spInfo("door_interact_exception | mode=segment-probe probe={} from={} to={} ex={}",
                    compactWorldPoint(probe), compactWorldPoint(fromWp), compactWorldPoint(toWp), ex.getClass().getSimpleName());
            return false;
        }
        if (!interacted) {
            WebWalkLog.spInfo("door_interact_failed | mode=segment-probe probe={} from={} to={}",
                    compactWorldPoint(probe), compactWorldPoint(fromWp), compactWorldPoint(toWp));
            return false;
        }
        markDoorInteractionSettling(toWp);
        waitForDoorInteractionProgress(fromWp, toWp, probe, doorActions, action, object);
        WorldPoint posAfter = Rs2Player.getWorldLocation();
        boolean traversed = didTraverseInteractedDoor(posBefore, posAfter, probe, fromWp, toWp);
        if (traversed) {
            clearDoorCrossFailures(fromWp, toWp);
            markStationaryDoorOpened(probe);
            markNearbyDoorFamilyOpened(object, probe, action, SEGMENT_DOOR_FAMILY_MARK_RADIUS);
            return true;
        }
        if (shouldBlacklistDoorAfterWrongTraversal(posBefore, posAfter, fromWp, toWp, Rs2Player.isMoving())) {
            doorAttemptLedger.blacklistDoor(probe);
            log.warn("[Walker] Blacklisting door after wrong traversal: door={} from={} to={} before={} after={}",
                    probe, fromWp, toWp, posBefore, posAfter);
        }
        if (isQuestLockedDoorDialogue()) {
            String dialogue = Rs2Dialogue.getDialogueText();
            log.warn("[Walker] Door at {} ({} action={}) appears quest/stat-locked — dialogue=\"{}\" — blacklisting tile, refreshing restrictions, recalculating",
                    probe, name, action, dialogue);
            doorAttemptLedger.blacklistDoor(probe);
            Rs2Dialogue.clickContinue();
            Rs2PathApi.refreshPlanningConfiguration();
            recalculatePath();
            return true;
        }

        if (doorStillHasActionTimed(probe, fromWp, toWp, doorActions, action)) {
            log.debug("[Walker] Segment door interaction did not traverse; action still present at {} ({} -> {})",
                    probe, fromWp, toWp);
            registerDoorCrossFailure(fromWp, toWp,
                    isConclusiveRefusedOpenSample(posAfter, fromWp), "refused-open");
        } else {
            markStationaryDoorOpened(probe);
            if (tryDoorEdgeCrossNudge(fromWp, toWp, currentTarget, routePath)) {
                markNearbyDoorFamilyOpened(object, probe, action, SEGMENT_DOOR_FAMILY_MARK_RADIUS);
                return true;
            }
        }
        return false;
    }

    static boolean tryHandleAdjacentBlockedDoorObject(TileObject object, WorldPoint probe,
                                                       WorldPoint fromWp, WorldPoint toWp,
                                                       List<String> doorActions,
                                                       List<WorldPoint> routePath) {
        Boolean previous = ADJACENT_BLOCKED_EDGE_ALLOWED.get();
        ADJACENT_BLOCKED_EDGE_ALLOWED.set(Boolean.TRUE);
        try {
            return tryHandleDoorObject(object, probe, fromWp, toWp,
                    doorActions, true, routePath);
        } finally {
            if (previous != null) {
                ADJACENT_BLOCKED_EDGE_ALLOWED.set(previous);
            } else {
                ADJACENT_BLOCKED_EDGE_ALLOWED.remove();
            }
        }
    }

    /**
     * THE door we clicked is open — not "some door near here is open".
     * <p>
     * The first version of this delegated to {@link #doorStillHasAction}, whose predicate accepts any
     * door-like object within TWO tiles of the probe. That is right for its own job (verify, then
     * retry) but wrong as a release condition, and it is why the first live run produced no
     * {@code releasedBy=door-opened} at all: in a door-heavy area a neighbouring shut door keeps the
     * answer "still closed" forever, so the wait ran on to its positional conditions exactly as before.
     * Matching on the probe tile itself, or on the geometry of the edge we are crossing, asks about the
     * one door the click was aimed at.
     * <p>
     * The other half of the old reading was an ambiguity: "no object matched" was indistinguishable
     * from "the action is gone", so anything that put the door out of scan range reported a shut door
     * as open. Here the two are separated — an opened door must actually be SEEN without its opening
     * action. Seeing nothing is unknown, and unknown is not open, so the wait falls through to the
     * positional conditions rather than releasing on an absence.
     * <p>
     * TRANSPORT DOORS (the moves-you class) stay correct through this. They keep their action after
     * relocating us, so this stays false and the positional conditions release the wait instead — and
     * those fire at once, because being moved is precisely what they detect.
     */
    static boolean doorObservedOpen(WorldPoint probe, WorldPoint fromWp, WorldPoint toWp,
                                            List<String> doorActions, String action) {
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null || probe == null || action == null
                || player.getPlane() != probe.getPlane()
                || player.distanceTo2D(probe) > HANDLER_RANGE) {
            return false;
        }
        return !doorStillHasAction(probe, fromWp, toWp, doorActions, action, true);
    }

    /**
     * What the door observation actually sees, for the {@code door_await} log.
     * <p>
     * Two live runs have now ended without a single {@code releasedBy=door-opened}, and neither could
     * say why: the poll count proves the check ran, but not what it read. This names every object the
     * strict match considers and the action currently on it, which separates the remaining candidates
     * — nothing matched the tile at all, versus something matched and still offers the opening action.
     */
    static String describeDoorObservation(WorldPoint probe, WorldPoint fromWp, WorldPoint toWp,
                                                  List<String> doorActions, String action) {
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null || probe == null) {
            return "no-player";
        }
        if (player.getPlane() != probe.getPlane()) {
            return "plane-mismatch";
        }
        int distance = player.distanceTo2D(probe);
        if (distance > HANDLER_RANGE) {
            return "out-of-range dist=" + distance;
        }
        try {
            // Only the two existing readings, so this adds no new off-client-thread call site of its
            // own. They separate the remaining candidates on their own:
            //   strict=false  -> the check said OPEN, so a release that is not door-opened is plumbing
            //   strict=true   -> the door on this very tile still offers the opening action
            //   strict!=loose -> the tighten worked and a neighbour was answering before
            boolean strict = doorStillHasAction(probe, fromWp, toWp, doorActions, action, true);
            boolean loose = doorStillHasAction(probe, fromWp, toWp, doorActions, action, false);
            return "strict=" + strict + " loose=" + loose + " dist=" + distance + " want=" + action;
        } catch (RuntimeException ex) {
            return "scan-error:" + ex.getClass().getSimpleName();
        }
    }

    /**
     * @param strictTile match only the door ON the probe tile or ON the {@code fromWp -> toWp} edge,
     *                   instead of anything within two tiles. Required when the answer decides whether
     *                   THIS door opened; the loose radius lets a neighbouring shut door answer for it.
     *                   Every decision-making caller is strict now — loose remains only for the
     *                   {@code saw=} diagnostic, which reports both readings side by side.
     */
    static boolean doorStillHasAction(WorldPoint probe, WorldPoint fromWp, WorldPoint toWp,
                                              List<String> doorActions, String action, boolean strictTile) {
        if (probe == null || action == null) {
            return false;
        }

        WorldPoint anchor = Rs2Player.getWorldLocation();
        if (anchor == null || anchor.getPlane() != probe.getPlane()) {
            anchor = probe;
        }

        TileObject object = Rs2GameObject.getAll(
                        o -> doorObjectStillHasAction(o, probe, fromWp, toWp, doorActions, action, strictTile),
                        anchor, Math.max(3, HANDLER_RANGE))
                .stream()
                .findFirst()
                .orElse(null);
        return object != null;
    }

    static boolean doorObjectStillHasAction(TileObject object, WorldPoint probe, WorldPoint fromWp, WorldPoint toWp,
                                                    List<String> doorActions, String action, boolean strictTile) {
        if (object == null || object.getWorldLocation() == null || action == null) {
            return false;
        }
        if (!(object instanceof WallObject) && !(object instanceof GameObject)) {
            return false;
        }
        WorldPoint loc = object.getWorldLocation();
        if (probe != null && loc.getPlane() != probe.getPlane()) {
            return false;
        }
        if (Rs2DoorProbe.isCatalogTransportObject(object)) {
            return false;
        }
        // The two-tile radius is right for "is anything here still shut" (verify, then retry) but wrong
        // for "did THIS door open" — a neighbouring shut door answers for it and the answer never changes.
        boolean nearProbe = probe != null
                && (strictTile ? loc.equals(probe) : loc.distanceTo2D(probe) <= 2);
        boolean onSegment = fromWp != null && toWp != null && Rs2DoorGeometry.isDoorOnSegment(object, fromWp, toWp);
        if (!nearProbe && !onSegment) {
            return false;
        }
        ObjectComposition composition = Rs2DoorDetection.resolveCompositionForDoorProbe(object);
        String currentAction = Rs2DoorClassifier.getDoorAction(composition, doorActions);
        return currentAction != null && currentAction.equalsIgnoreCase(action);
    }

    static void markStationaryDoorOpened(WorldPoint doorTile) {
        doorAttemptLedger.markStationaryDoorOpened(doorTile, System.currentTimeMillis());
    }

    /**
     * Whether the player already stands on the far side of this wall door's face relative to the
     * segment's approach tile — in which case the crossing has happened and clicking the door again
     * can only undo it (a moves-you gate carries the player straight back). Shell wrapper over
     * {@link Rs2DoorGeometry#playerBeyondWallFace}; see there for the Stronghold bounce this exists
     * to prevent.
     */
    static boolean isPlayerBeyondDoorFace(WallObject wall, WorldPoint fromWp) {
        return Rs2DoorGeometry.playerBeyondWallFace(wall.getOrientationA(), wall.getWorldLocation(),
                fromWp, Rs2Player.getWorldLocation());
    }

    static String doorAttemptKey(WorldPoint doorTile, WorldPoint fromWp, WorldPoint toWp) {
        return Rs2DoorHandler.doorAttemptKey(doorTile, fromWp, toWp);
    }

    static boolean shouldThrottleDoorAttempt(WorldPoint doorTile, WorldPoint fromWp, WorldPoint toWp) {
        return doorAttemptLedger.shouldThrottleAttempt(doorTile, fromWp, toWp,
                DOOR_ATTEMPT_EDGE_COOLDOWN_MS, System.currentTimeMillis());
    }

    static boolean hasRecentDoorAttemptOnEdge(WorldPoint fromWp, WorldPoint toWp) {
        return shouldThrottleDoorAttempt(null, fromWp, toWp);
    }

    static boolean hasRecentDoorAttemptNearIndex(List<WorldPoint> path, int edgeIdx) {
        if (path == null || path.size() < 2 || edgeIdx < 0) {
            return false;
        }
        int start = Math.max(0, edgeIdx - 1);
        int end = Math.min(path.size() - 2, edgeIdx + 1);
        for (int i = start; i <= end; i++) {
            WorldPoint from = path.get(i);
            WorldPoint to = path.get(i + 1);
            if (!isLikelyDoorEdgeTransition(from, to)) {
                continue;
            }
            if (hasRecentDoorAttemptOnEdge(from, to)) {
                return true;
            }
        }
        return false;
    }

    static boolean waitForRecentDoorEdgeResolutionNearIndex(List<WorldPoint> path, int edgeIdx, int timeoutMs) {
        if (path == null || path.size() < 2 || edgeIdx < 0) {
            return false;
        }
        int start = Math.max(0, edgeIdx - 1);
        int end = Math.min(path.size() - 2, edgeIdx + 1);
        for (int i = start; i <= end; i++) {
            WorldPoint from = path.get(i);
            WorldPoint to = path.get(i + 1);
            if (!isLikelyDoorEdgeTransition(from, to)) {
                continue;
            }
            if (hasRecentDoorAttemptOnEdge(from, to)) {
                return waitForDoorEdgeResolution(from, to, timeoutMs);
            }
        }
        return false;
    }

    static long recentDoorAttemptAgeNearIndex(List<WorldPoint> path, int edgeIdx) {
        if (path == null || path.size() < 2 || edgeIdx < 0) {
            return -1L;
        }
        long now = System.currentTimeMillis();
        long newestAttemptAt = -1L;
        int start = Math.max(0, edgeIdx - 1);
        int end = Math.min(path.size() - 2, edgeIdx + 1);
        for (int i = start; i <= end; i++) {
            WorldPoint from = path.get(i);
            WorldPoint to = path.get(i + 1);
            if (!isLikelyDoorEdgeTransition(from, to)) {
                continue;
            }
            Long attemptedAt = doorAttemptLedger.attemptAtMs(from, to);
            if (attemptedAt != null) {
                newestAttemptAt = Math.max(newestAttemptAt, attemptedAt);
            }
        }
        return newestAttemptAt < 0 ? -1L : Math.max(0L, now - newestAttemptAt);
    }

    static boolean isLikelyDoorEdgeTransition(WorldPoint from, WorldPoint to) {
        if (from == null || to == null || from.getPlane() != to.getPlane()) {
            return false;
        }
        // Door crossings are local transitions. Ignore long smoothed hops that can
        // accidentally reuse old door attempt keys and stall nearby-wait logic.
        return from.distanceTo2D(to) >= 1 && from.distanceTo2D(to) <= 2;
    }

    static boolean tryPostDoorFastMinimapClick(List<WorldPoint> path, int edgeIdx, WorldPoint playerLoc, WorldPoint target) {
        if (path == null || path.size() < 2 || playerLoc == null) {
            return false;
        }
        int from = Math.max(0, edgeIdx + 1);
        int to = Math.min(path.size() - 1, from + 8);
        WorldPoint candidate = null;
        int bestDistToTarget = Integer.MAX_VALUE;
        for (int i = from; i <= to; i++) {
            WorldPoint wp = path.get(i);
            if (wp == null || wp.getPlane() != playerLoc.getPlane()) {
                break;
            }
            if (euclideanSq(wp, playerLoc) > POST_DOOR_FAST_CLICK_MAX_EUCLIDEAN * POST_DOOR_FAST_CLICK_MAX_EUCLIDEAN) {
                break;
            }
            if (!Rs2Tile.isTileReachable(wp)) {
                continue;
            }
            int d = target == null ? 0 : wp.distanceTo2D(target);
            if (candidate == null || d < bestDistToTarget) {
                candidate = wp;
                bestDistToTarget = d;
            }
        }
        if (candidate == null || candidate.equals(playerLoc)) {
            return false;
        }
        // Do not issue an immediate fast click while the player is still traversing
        // (moving/animation in flight) from the just-handled door edge.
        if (Rs2Player.isMoving() || Rs2Player.isAnimating()) {
            return false;
        }
        boolean clicked = walkMiniMap(candidate);
        if (!clicked) {
            clicked = walkMiniMapToward(candidate, playerLoc, POST_DOOR_FAST_CLICK_MAX_EUCLIDEAN - 1);
        }
        if (clicked) {
            markFirstMovementClick("post_door_fast_click", target, playerLoc,
                    "to=" + compactWorldPoint(candidate));
        }
        return clicked;
    }

    static boolean tryDoorEdgeCrossNudge(WorldPoint fromWp, WorldPoint toWp, WorldPoint target) {
        return tryDoorEdgeCrossNudge(fromWp, toWp, target, null);
    }

    /**
     * Route-aware variant: with the route in hand, the follow-through click goes to the furthest
     * REACHABLE route point past the door instead of the single far-side tile. Crossing the edge is
     * still crossing it if the destination is further along — the server paths us through the open
     * door either way — so one click replaces the nudge-then-route-click pair, which is both faster
     * and what a player actually does after opening a door.
     * <p>
     * The reachability gate is the whole safety argument. The previous attempt at this (reverted)
     * clicked a tile the walled-route net had just REFUSED, because it selected without the gate.
     * Here every candidate must be in the player-origin BFS — the same collision evidence the refusal
     * uses — and the BFS runs AFTER the door opened, so it sees through the doorway. No candidate, or
     * no route: the single-tile nudge behaves exactly as before. The success test is unchanged.
     */
    static boolean tryDoorEdgeCrossNudge(WorldPoint fromWp, WorldPoint toWp, WorldPoint target,
                                                 List<WorldPoint> routePath) {
        long nudgeStartedAt = System.currentTimeMillis();
        try {
            return tryDoorEdgeCrossNudgeInner(fromWp, toWp, target, routePath);
        } finally {
            doorLegNudgeMs += System.currentTimeMillis() - nudgeStartedAt;
        }
    }

    static boolean tryDoorEdgeCrossNudgeInner(WorldPoint fromWp, WorldPoint toWp, WorldPoint target,
                                                      List<WorldPoint> routePath) {
        if (fromWp == null || toWp == null || fromWp.getPlane() != toWp.getPlane()) {
            return false;
        }
        WorldPoint before = Rs2Player.getWorldLocation();
        if (before == null || before.getPlane() != toWp.getPlane()) {
            return false;
        }
        // At or past the far side: the crossing this nudge exists to produce has happened. Without
        // this, a player one step BEYOND toWp still passed the distance gate and the fallback click
        // aimed at toWp — one tile backward, straight back into a self-closing door.
        if (before.equals(toWp) || Rs2DoorGeometry.crossedDoorAxis(fromWp, toWp, before)) {
            return true;
        }
        if (before.distanceTo2D(toWp) > POST_DOOR_EDGE_NUDGE_MAX_FROM_PLAYER) {
            return false;
        }
        if (Rs2Player.isMoving() || Rs2Player.isAnimating()) {
            return false;
        }

        WorldPoint clickTo = toWp;
        if (routePath != null && !routePath.isEmpty()) {
            Map<WorldPoint, Integer> reachable = getClosestIndexReachableTiles(before);
            WorldPoint routeTarget = selectPostDoorRouteTarget(routePath, fromWp, toWp, before, reachable,
                    POST_DOOR_FAST_CLICK_MAX_EUCLIDEAN);
            if (routeTarget != null) {
                clickTo = routeTarget;
            }
        }

        boolean clicked = walkFastCanvas(clickTo);
        if (!clicked) {
            clicked = walkMiniMapToward(clickTo, before, POST_DOOR_FAST_CLICK_MAX_EUCLIDEAN - 1);
        }
        if (!clicked) {
            return false;
        }

        markFirstMovementClick("first_door_edge_nudge", target, before,
                "to=" + compactWorldPoint(clickTo)
                        + (clickTo.equals(toWp) ? "" : " pastDoorOf=" + compactWorldPoint(toWp)));
        sleepUntil(() -> {
            if (isWalkCancelled(target)) {
                return true;
            }
            WorldPoint now = Rs2Player.getWorldLocation();
            return isDoorEdgeNudgeResolved(before, now, fromWp, toWp);
        }, POST_DOOR_EDGE_NUDGE_WAIT_MS);

        WorldPoint after = Rs2Player.getWorldLocation();
        boolean progressed = isDoorEdgeNudgeResolved(before, after, fromWp, toWp);
        if (progressed) {
            WebWalkLog.tmark("door_edge_nudge", System.currentTimeMillis() - routeState.walkSessionStartedAtMs,
                    target, before, "from=" + compactWorldPoint(fromWp) + " to=" + compactWorldPoint(toWp));
            routeState.lastMovedTimeMs = System.currentTimeMillis();
            routeState.stuckCount = 0;
            clearDoorCrossFailures(fromWp, toWp);
        } else {
            WebWalkLog.spInfo("door_edge_nudge_unresolved | from={} to={} before={} after={}",
                    compactWorldPoint(fromWp), compactWorldPoint(toWp), compactWorldPoint(before), compactWorldPoint(after));
            // A stationary player who clicked past an "open" door and moved nowhere is the seed-gate
            // signature: the door reads open (or opens and instantly re-shuts) while the game refuses
            // the crossing. A cancelled wait or an in-flight sample proves nothing.
            registerDoorCrossFailure(fromWp, toWp,
                    before.equals(after) && !Rs2Player.isMoving()
                            && (target == null || !isWalkCancelled(target)),
                    "cross-nudge");
        }
        return progressed;
    }


    static boolean tryRecentDoorAttemptEdgeNudge(WorldPoint playerLoc, WorldPoint target) {
        return tryRecentDoorAttemptEdgeNudge(playerLoc, target, null);
    }

    static boolean tryRecentDoorAttemptEdgeNudge(WorldPoint playerLoc, WorldPoint target,
                                                         List<WorldPoint> routePath) {
        DoorAttemptLedger.Attempt claim =
                doorAttemptLedger.latestAttempt(POST_DOOR_NUDGE_RECENT_ATTEMPT_MS, System.currentTimeMillis());
        if (playerLoc == null || claim == null) {
            return false;
        }
        WorldPoint from = claim.from;
        WorldPoint to = claim.to;
        // A crossing that has ALREADY happened satisfies nothing: at the Stronghold's chained gates
        // (2026-08-12) the player stood two tiles past gate 1 while gate 2 blocked the route ahead,
        // and this branch kept ending the pass "resolved" over the conquered door — starving the
        // miss branch that would have probed gate 2. Same principle as the crossed-face guard:
        // done means fall through, and the spent attempt is cleared so it cannot fire again.
        if (Rs2DoorGeometry.crossedDoorAxis(from, to, playerLoc)) {
            doorAttemptLedger.clearLatestAttempt();
            return false;
        }
        if (playerLoc.getPlane() != to.getPlane() || playerLoc.distanceTo2D(to) > POST_DOOR_EDGE_NUDGE_MAX_FROM_PLAYER) {
            return false;
        }
        if (Rs2Player.isMoving() || Rs2Player.isAnimating()) {
            return false;
        }
        boolean nudged = tryDoorEdgeCrossNudge(from, to, target, routePath);
        if (nudged) {
            WebWalkLog.tmark("recent_door_edge_nudge", System.currentTimeMillis() - routeState.walkSessionStartedAtMs,
                    target, playerLoc, "from=" + compactWorldPoint(from) + " to=" + compactWorldPoint(to));
        }
        return nudged;
    }

    /**
     * The furthest route point past the just-opened door that the player can PROVABLY walk to.
     * <p>
     * Pure selection over the supplied reachability map — one BFS in the caller, map lookups here —
     * rather than a reachability probe per candidate, which is the client-thread cost that froze
     * MLM's loop. The edge must be located ON the route (a fold that merely passes nearby proves
     * nothing about what lies beyond the door), candidates keep to the player's plane and the
     * Euclidean cap, and each must be in the map: a tile the BFS cannot reach is on the far side of
     * some OTHER wall, and clicking it is the exact regression the walled-route net exists to refuse.
     * Null when nothing qualifies — the caller then keeps the single-tile nudge.
     */
    static WorldPoint selectPostDoorRouteTarget(List<WorldPoint> routePath, WorldPoint fromWp, WorldPoint toWp,
                                                WorldPoint player, Map<WorldPoint, Integer> reachable,
                                                int maxEuclidean) {
        if (routePath == null || routePath.size() < 2 || fromWp == null || toWp == null
                || player == null || reachable == null || reachable.isEmpty()) {
            return null;
        }
        int edgeIdx = -1;
        for (int i = 0; i + 1 < routePath.size(); i++) {
            if (fromWp.equals(routePath.get(i)) && toWp.equals(routePath.get(i + 1))) {
                edgeIdx = i;
                break;
            }
        }
        if (edgeIdx < 0) {
            return null;
        }
        WorldPoint best = null;
        for (int i = edgeIdx + 2; i < routePath.size(); i++) {
            WorldPoint wp = routePath.get(i);
            if (wp == null || wp.getPlane() != player.getPlane()) {
                break;
            }
            if (player.distanceTo2D(wp) > maxEuclidean) {
                break;
            }
            if (wp.equals(player)) {
                continue;
            }
            if (reachable.containsKey(wp)) {
                best = wp;
            }
        }
        return best;
    }

    static boolean isDoorEdgeNudgeResolved(WorldPoint before, WorldPoint after, WorldPoint fromWp, WorldPoint toWp) {
        if (before == null || after == null || fromWp == null || toWp == null) {
            return false;
        }
        if (before.equals(after)) {
            return false;
        }
        if (before.getPlane() != after.getPlane()
                || after.getPlane() != fromWp.getPlane()
                || after.getPlane() != toWp.getPlane()) {
            return false;
        }
        int beforeTo = before.distanceTo2D(toWp);
        int afterTo = after.distanceTo2D(toWp);
        if (after.equals(toWp) || afterTo == 0) {
            return true;
        }
        if (afterTo <= 1 && afterTo < beforeTo) {
            return true;
        }
        // The near-toWp rule alone cannot see a crossing that keeps going, and with the nudge now
        // clicking a route point PAST the door, keeping going is the intended outcome. It also has a
        // blind spot the live log caught even for short hops: a nudge starts on fromWp (beforeTo=1),
        // so afterTo < beforeTo only fires on exactly toWp — and a RUNNING player covers two tiles a
        // tick and skips that tile entirely (observed 3369 -> 3367 -> 3365, reported unresolved).
        // Crossing the door's axis is the fact being tested, so test it directly.
        return hasCrossedDoorAxis(fromWp, toWp, after);
    }

    /**
     * Whether {@code after} lies at or beyond the far side of the {@code fromWp -> toWp} door edge.
     * Shared with the ranged door await, which uses the same reading as its "passed the door" release.
     */
    static boolean hasCrossedDoorAxis(WorldPoint fromWp, WorldPoint toWp, WorldPoint after) {
        return Rs2DoorGeometry.crossedDoorAxis(fromWp, toWp, after);
    }

    /**
     * Edge-aware variant: the full window only binds a re-click of the SAME edge; a different door
     * right after a successful open is chaining, not hammering, and owes one tick. The dialogue
     * defer is unconditional either way — an open quest dialogue blocks every door equally.
     */
    static boolean shouldThrottleGlobalDoorInteraction(WorldPoint fromWp, WorldPoint toWp) {
        DoorAttemptLedger.Attempt lastClaim = doorAttemptLedger.latestAttempt();
        boolean sameEdge = fromWp != null && toWp != null && lastClaim != null
                && lastClaim.isSameDirectedEdge(fromWp, toWp);
        return Rs2DoorHandler.shouldThrottleGlobalDoorInteraction(System.currentTimeMillis(),
                doorAttemptLedger.globalCooldownUntilMs(), sameEdge,
                DOOR_INTERACTION_GLOBAL_COOLDOWN_MS, DOOR_INTERACTION_CROSS_EDGE_COOLDOWN_MS)
                || shouldDeferDoorInteractionForDialogue();
    }

    /**
     * A guarded door answers with a conversation instead of opening ("you can't go in there"). The
     * walker reads the lack of movement as "no progress, retry" and clicks again — and that click
     * CANCELS the menu the previous click just opened, destroying the only thing that can get us
     * through. Whatever answers dialogue (the questing layer) then never sees a menu that survives
     * long enough to act on, so the walk livelocks at the door.
     *
     * <p>Deferring is BOUNDED: if nothing answers within {@link #DOOR_DIALOGUE_DEFER_MAX_MS} the
     * walker resumes clicking, so a stray conversation with no handler cannot stall a plain walk
     * that has no dialogue logic behind it.
     */
    static boolean shouldDeferDoorInteractionForDialogue() {
        if (!Rs2Dialogue.hasSelectAnOption()) {
            routeState.doorDialogueDeferSinceMs = 0L;
            return false;
        }
        long now = System.currentTimeMillis();
        if (routeState.doorDialogueDeferSinceMs == 0L) {
            routeState.doorDialogueDeferSinceMs = now;
            WebWalkLog.spInfo("door_dialogue_defer | an option menu is open — not re-clicking the door");
        }
        return doorDialogueDeferActive(routeState.doorDialogueDeferSinceMs, now, DOOR_DIALOGUE_DEFER_MAX_MS);
    }

    /**
     * Pure half of the dialogue hold-off: defer only while the menu has been up for less than
     * {@code maxDeferMs}. Split out because an unbounded version of this gate would trade a livelock
     * at a guarded door for a permanent stall at any unanswered conversation.
     */
    static boolean doorDialogueDeferActive(long deferSinceMs, long nowMs, long maxDeferMs) {
        return deferSinceMs > 0L && nowMs - deferSinceMs < maxDeferMs;
    }

    static boolean isDoorInteractionSettling() {
        long now = System.currentTimeMillis();
        if (now >= doorAttemptLedger.settleUntilMs()) {
            return false;
        }
        // Early exit: the interaction's purpose was opening the door — once its far side is reachable,
        // the edge is open and there is nothing left to settle (previously this was a flat 900ms freeze
        // after every door). One-tick floor for object-state flux; the window is cleared on success so
        // repeated checks this tick don't re-run the reachability probe.
        WorldPoint farSide = doorAttemptLedger.settleFarSide();
        if (farSide != null
                && now - doorAttemptLedger.settleStartedAtMs() >= POST_INTERACT_SETTLE_MIN_MS
                && Rs2Tile.isTileReachable(farSide)) {
            doorAttemptLedger.endSettleEarly();
            return false;
        }
        return true;
    }

    static boolean isDoorEdgePassSkipCoolingDown() {
        return System.currentTimeMillis() - routeState.lastDoorEdgePassSkipAtMs < DOOR_EDGE_SKIP_COOLDOWN_MS;
    }

    static void markDoorInteractionSettling(WorldPoint farSideWp) {
        doorAttemptLedger.markSettling(farSideWp, System.currentTimeMillis(), DOOR_POST_INTERACT_SETTLE_MS);
    }

    static void markGlobalDoorInteractionCooldown() {
        doorAttemptLedger.markGlobalCooldownUntil(
                Rs2DoorHandler.markGlobalDoorInteractionCooldown(DOOR_INTERACTION_GLOBAL_COOLDOWN_MS));
    }

    static void markDoorAttempt(WorldPoint doorTile, WorldPoint fromWp, WorldPoint toWp) {
        doorAttemptLedger.markAttempt(doorTile, fromWp, toWp, System.currentTimeMillis());
    }

    /**
     * Registers a door attempt that concluded without crossing its edge; on the third such failure
     * the edge is blocked in the planner and the route recalculated, so the walk routes around or
     * ends honestly instead of ping-ponging. The block is scoped to the CURRENT walk, not the
     * session: a door that refuses for game-state reasons (Tithe Farm's seed gate) opens the moment
     * the condition is met, and a session block would stop the Tithe plugin's own seeded walk-in
     * from ever routing through it — the museum lesson, where one layer's block silently broke the
     * other layer's fix. {@link #withdrawWalkScopedDoorBlocks} returns the edges at the next walk
     * session start. Not a door-tile blacklist either: the planner, not the door handler, owes the
     * reroute.
     */
    static void registerDoorCrossFailure(WorldPoint fromWp, WorldPoint toWp,
                                                 boolean conclusiveSample, String mode) {
        if (fromWp == null || toWp == null) {
            return;
        }
        DoorAttemptLedger.Strike strike = doorAttemptLedger.registerCrossFailure(
                fromWp, toWp,
                conclusiveSample,
                System.currentTimeMillis(),
                DOOR_CROSS_FAILURE_DECAY_MS,
                DOOR_CROSS_FAILURE_STRIKE_LIMIT);
        if (strike != DoorAttemptLedger.Strike.STRIKE_OUT) {
            return;
        }
        String reason = "door-strike-out (" + mode + ")";
        if (Rs2PathApi.learnBlockedEdge(fromWp, toWp, reason)) {
            doorAttemptLedger.recordWalkScopedBlock(fromWp, toWp);
        }
        if (Rs2PathApi.learnBlockedEdge(toWp, fromWp, reason)) {
            doorAttemptLedger.recordWalkScopedBlock(toWp, fromWp);
        }
        WebWalkLog.spInfo("door_strike_out | from={} to={} mode={} — {} concluded attempts never crossed; "
                        + "blocking edge for this walk and replanning",
                compactWorldPoint(fromWp), compactWorldPoint(toWp), mode, DOOR_CROSS_FAILURE_STRIKE_LIMIT);
        recalculatePath();
    }

    /**
     * Withdraws every strike-out block the previous walk earned. Called at walk session start: the
     * new walk may run under changed conditions (seeds acquired, key obtained), so each refused door
     * gets a fresh chance — and a walk retried without the condition just re-earns the strike-out in
     * a few attempts, loudly, instead of inheriting a stale block silently.
     */
    static void withdrawWalkScopedDoorBlocks() {
        for (WorldPoint[] edge : doorAttemptLedger.drainWalkScopedBlocks()) {
            Rs2PathApi.unlearnBlockedEdge(edge[0], edge[1], "walk-scoped door strike-out expired");
        }
    }

    static void clearDoorCrossFailures(WorldPoint fromWp, WorldPoint toWp) {
        doorAttemptLedger.clearCrossFailures(fromWp, toWp);
    }

    /**
     * A refused-open only counts when the attempt genuinely concluded AT the door: player stationary
     * on (or beside) the near-side tile. A ranged click whose wait expired mid-approach samples a
     * player still tiles away and proves nothing about the door.
     */
    static boolean isConclusiveRefusedOpenSample(WorldPoint posAfter, WorldPoint fromWp) {
        return posAfter != null && fromWp != null
                && !Rs2Player.isMoving()
                && posAfter.getPlane() == fromWp.getPlane()
                && posAfter.distanceTo2D(fromWp) <= 1;
    }

    static boolean recentlyOpenedStationaryDoorOnSegment(WorldPoint fromWp, WorldPoint toWp) {
        return doorAttemptLedger.recentlyOpenedDoorOnSegment(
                fromWp, toWp, STATIONARY_DOOR_SUPPRESS_MS, System.currentTimeMillis());
    }

    static boolean wasStationaryDoorOpenedRecently(WorldPoint doorTile) {
        return doorAttemptLedger.wasStationaryDoorOpenedWithin(
                doorTile, STATIONARY_DOOR_SUPPRESS_MS, System.currentTimeMillis());
    }

    static boolean isDoorLikeCatalogTransportSegment(List<WorldPoint> path, int index) {
        if (path == null || index < 0 || index >= path.size() - 1) {
            return false;
        }
        return isDoorLikeCatalogTransportSegment(path.get(index), path.get(index + 1));
    }

    static boolean isDoorLikeCatalogTransportSegment(WorldPoint from, WorldPoint to) {
        if (from == null || to == null) {
            return false;
        }
        return hasDoorLikeDirectedCatalogTransport(from, to)
                || hasDoorLikeDirectedCatalogTransport(to, from)
                || hasDoorLikeAdjacentOriginShortTransportHop(from, to)
                || hasDoorLikeAdjacentOriginShortTransportHop(to, from);
    }

    /**
     * Catalog object transports bypass generic door probing so the exact selected edge keeps
     * execution ownership. This includes ordinary Open/Pass doors: the object executor now uses
     * the server's approach path and owns the interaction through observed landing. Non-object
     * catalog rows retain the legacy door classification, with the Al Kharid toll gate remaining
     * an explicit dialogue/landing exception.
     */
    static boolean shouldDeferDoorHandlingToTransport(List<WorldPoint> path, int index) {
        if (!isCatalogBackedTransportSegment(path, index)) {
            return false;
        }
        if (Rs2PathApi.getActiveTransportEdge(path.get(index), path.get(index + 1))
                .map(edge -> edge.getExecutor() == Rs2TransportExecutor.OBJECT)
                .orElse(false)) {
            return true;
        }
        return !isDoorLikeCatalogTransportSegment(path, index)
                || isAlKharidTollGateSegment(path.get(index), path.get(index + 1));
    }

    static boolean isAlKharidTollGateSegment(WorldPoint from, WorldPoint to) {
        return from != null
                && to != null
                && AL_KHARID_TOLL_GATE_POINTS.contains(from)
                && AL_KHARID_TOLL_GATE_POINTS.contains(to)
                && from.getPlane() == to.getPlane()
                && Math.abs(from.getX() - to.getX()) == 1
                && from.getY() == to.getY();
    }

	static boolean matchesDirectedTransportCatalogEdge(WorldPoint origin, WorldPoint dest) {
		return Rs2PathApi.hasCatalogTransportEdge(origin, dest);
	}

    static boolean hasDoorLikeDirectedCatalogTransport(WorldPoint origin, WorldPoint dest) {
        if (origin == null || dest == null) {
            return false;
        }
		return Rs2PathApi.getCatalogTransportEdges(origin).stream()
				.anyMatch(t -> Objects.equals(t.getDestination(), dest) && Rs2DoorProbe.isDoorLikeCatalogTransport(t));
    }

    /**
     * True when some catalog origin one step from {@code from} has a same-plane adjacent transport to {@code to}.
     * Restricted to {@link #isAdjacentSamePlaneTransport} rows so long-distance transports do not suppress doors.
     */
    static boolean matchesAdjacentOriginShortTransportHop(WorldPoint from, WorldPoint to) {
        if (from == null || to == null || from.getPlane() != to.getPlane()) {
            return false;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                WorldPoint catalogOrigin = new WorldPoint(from.getX() + dx, from.getY() + dy, from.getPlane());
				for (Rs2TransportEdge t : Rs2PathApi.getCatalogTransportEdges(catalogOrigin)) {
					if (Objects.equals(t.getDestination(), to) && isAdjacentSamePlaneTransport(t)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static boolean hasDoorLikeAdjacentOriginShortTransportHop(WorldPoint from, WorldPoint to) {
        if (from == null || to == null || from.getPlane() != to.getPlane()) {
            return false;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                WorldPoint catalogOrigin = new WorldPoint(from.getX() + dx, from.getY() + dy, from.getPlane());
				for (Rs2TransportEdge t : Rs2PathApi.getCatalogTransportEdges(catalogOrigin)) {
                    if (Objects.equals(t.getDestination(), to)
                            && isAdjacentSamePlaneTransport(t)
                            && Rs2DoorProbe.isDoorLikeCatalogTransport(t)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static void waitForDoorInteractionProgress(WorldPoint fromWp, WorldPoint toWp) {
        waitForDoorInteractionProgress(fromWp, toWp, null, null, null, null);
    }

    /**
     * Door-identified variant: lets the await release the moment the door is OPEN rather than when we
     * have finished walking through it. An unlocked door opens within a game tick, so the traversal
     * that used to be waited out is time the server is already spending walking us — time in which the
     * next door on the route could be clicked. Falls back to the positional conditions when the door
     * cannot be identified or the config switch is off.
     */
    static void waitForDoorInteractionProgress(WorldPoint fromWp, WorldPoint toWp,
                                                       WorldPoint probe, List<String> doorActions,
                                                       String action) {
        waitForDoorInteractionProgress(fromWp, toWp, probe, doorActions, action, null);
    }

    static void waitForDoorInteractionProgress(WorldPoint fromWp, WorldPoint toWp,
                                                       WorldPoint probe, List<String> doorActions,
                                                       String action, TileObject object) {
        long startedAt = System.currentTimeMillis();
        AwaitTicket ticket = Rs2WalkerAwaits.beginTicket();
        java.util.function.BooleanSupplier doorOpened =
                (probe == null || action == null || !doorInteractionWhileApproachingEnabled())
                        ? null
                        : () -> doorObservedOpen(probe, fromWp, toWp, doorActions, action);
        java.util.function.Supplier<String> observation =
                (probe == null || action == null) ? null
                        : () -> describeDoorObservation(probe, fromWp, toWp, doorActions, action);
        // Ranged budgets can hold for seconds, so a hold must release when the plan it belongs to
        // stops existing — the walk cancelled or re-targeted, OR the route replanned under the same
        // target. The second case is what live collision does when it sees the awaited edge blocked:
        // it recalculates and routes around, and holding the old plan's door after that is pure
        // waste (measured: the replan fired a second before a 6.9s ranged timeout expired).
        // A new Pathfinder instance IS the replan signal; the reference is captured at click time.
        WorldPoint walkTarget = currentTarget;
        Object plannerAtClick = Rs2PathApi.getPathfinder();
        java.util.function.BooleanSupplier cancelled = () -> {
            // isWalkSuperseded(null) answers true, and a door can legitimately be handled outside a
            // walk session (recovery paths); no target means there is nothing to be cancelled.
            // Identity-only by necessity — see isWalkSuperseded: the completion-evaluating variant
            // runs a caller callback that can be a client-thread BFS, and this is a 100ms loop.
            if (walkTarget != null && isWalkSuperseded(walkTarget)) {
                return true;
            }
            Object plannerNow = Rs2PathApi.getPathfinder();
            return plannerAtClick != null && plannerNow != null && plannerNow != plannerAtClick;
        };
        // The wall-face reading that stays true when a moves-you gate deposits the player a tile
        // off the planned route -- the case every positional release condition goes blind on.
        // Orientation and tile are captured ONCE: both are immutable for the object's lifetime, and
        // reading a TileObject inside a poll loop risks a stale scene reference mid-await.
        java.util.function.BooleanSupplier doorCrossed = null;
        if (object instanceof WallObject) {
            final int wallOrientation = ((WallObject) object).getOrientationA();
            final WorldPoint wallTile = object.getWorldLocation();
            doorCrossed = () -> {
                WorldPoint now = Rs2Player.getWorldLocation();
                return now != null && Rs2DoorGeometry.playerBeyondWallFace(wallOrientation, wallTile, fromWp, now);
            };
        }
        try {
            Rs2WalkerAwaits.awaitDoorInteractionProgress(ticket, fromWp, toWp, doorOpened, observation, cancelled, doorCrossed);
        } finally {
            long tookMs = System.currentTimeMillis() - startedAt;
            if (rawScanWallSnapshot != null || rawScanGameObjectSnapshot != null) {
                rawScanDoorInteractionWaitMs += tookMs;
            }
            doorLegAwaitMs += tookMs;
        }
    }

    static boolean waitForDoorEdgeResolution(WorldPoint fromWp, WorldPoint toWp, int timeoutMs) {
        long startedAt = System.currentTimeMillis();
        DoorResolution resolution = Rs2WalkerAwaits.awaitDoorEdgeResolution(fromWp, toWp, timeoutMs);
        long elapsed = System.currentTimeMillis() - startedAt;
        // Counted separately or it lands in the scan's doorProbe residual and reads as probe cost —
        // this wait alone has been measured at 1897ms (FAILED_TIMEOUT).
        if (rawScanWallSnapshot != null || rawScanGameObjectSnapshot != null) {
            rawScanDoorEdgeWaitMs += elapsed;
        }
        WebWalkLog.tmark("door_edge_wait_done", elapsed, currentTarget,
                Rs2Player.getWorldLocation(),
                "result=" + resolution + " from=" + compactWorldPoint(fromWp) + " to=" + compactWorldPoint(toWp));
        return resolution == DoorResolution.RESOLVED;
    }

    static boolean isDoorEdgeResolved(WorldPoint fromWp, WorldPoint toWp) {
        return Rs2WalkerAwaits.isDoorEdgeResolved(fromWp, toWp);
    }

    static boolean didTraverseInteractedDoor(WorldPoint start, WorldPoint end, WorldPoint objectLoc,
                                             WorldPoint fromWp, WorldPoint toWp) {
        if (start == null || end == null || objectLoc == null || toWp == null) {
            return false;
        }
        if (start.getPlane() != end.getPlane() || end.getPlane() != objectLoc.getPlane() || end.getPlane() != toWp.getPlane()) {
            return false;
        }
        if (start.equals(end)) {
            return false;
        }
        if (!movedAcrossInteractedObject(start, end, objectLoc)) {
            return false;
        }
        int beforeTo = start.distanceTo2D(toWp);
        int afterTo = end.distanceTo2D(toWp);
        if (afterTo >= beforeTo) {
            return false;
        }
        // Keep the traversal check anchored to the active segment.
        return fromWp == null || fromWp.getPlane() == end.getPlane();
    }

    static boolean shouldBlacklistDoorAfterWrongTraversal(WorldPoint start, WorldPoint end, WorldPoint fromWp, WorldPoint toWp) {
        return shouldBlacklistDoorAfterWrongTraversal(start, end, fromWp, toWp, false);
    }

    /**
     * As {@link #shouldBlacklistDoorAfterWrongTraversal(WorldPoint, WorldPoint, WorldPoint, WorldPoint)}
     * but aware of whether the {@code end} position was sampled while the player was STILL WALKING. The
     * interact walks the player to the door first and the progress wait can time out en route, so a
     * moving sample is just a point along the path — not a traversal verdict. Deciding from one poisoned
     * Wydin's shop door: before=3008,3207 (en route), after=3012,3211 (seven tiles from the edge, mid
     * walk) was blacklisted AND learn-persisted as a blocked edge. A same-plane moving sample must never
     * blacklist; a plane change is still trusted (the door acted — walking cannot change plane).
     */
    static boolean shouldBlacklistDoorAfterWrongTraversal(WorldPoint start, WorldPoint end, WorldPoint fromWp,
                                                          WorldPoint toWp, boolean sampledWhileMoving) {
        if (start == null || end == null || toWp == null) {
            return false;
        }
        if (start.equals(end)) {
            return false;
        }
        if (start.getPlane() != end.getPlane()) {
            return true;
        }
        if (sampledWhileMoving) {
            return false;
        }
        if (!startedNearDoorEdge(start, fromWp, toWp)) {
            return false;
        }
        int moved = start.distanceTo2D(end);
        if (moved < 3) {
            return false;
        }
        int startTo = start.distanceTo2D(toWp);
        int endTo = end.distanceTo2D(toWp);
        if (endTo <= startTo + 1) {
            return false;
        }
        if (fromWp == null || fromWp.getPlane() != end.getPlane()) {
            return true;
        }
        int startFrom = start.distanceTo2D(fromWp);
        int endFrom = end.distanceTo2D(fromWp);
        return endFrom >= startFrom + 2;
    }

    static boolean startedNearDoorEdge(WorldPoint start, WorldPoint fromWp, WorldPoint toWp) {
        if (start == null) {
            return false;
        }
        final int maxDoorStartDistance = 3;
        boolean nearFrom = fromWp != null
                && fromWp.getPlane() == start.getPlane()
                && start.distanceTo2D(fromWp) <= maxDoorStartDistance;
        boolean nearTo = toWp != null
                && toWp.getPlane() == start.getPlane()
                && start.distanceTo2D(toWp) <= maxDoorStartDistance;
        return nearFrom || nearTo;
    }

    static boolean movedAcrossInteractedObject(WorldPoint start, WorldPoint end, WorldPoint objectLoc) {
        int startRelX = Integer.compare(start.getX(), objectLoc.getX());
        int endRelX = Integer.compare(end.getX(), objectLoc.getX());
        int startRelY = Integer.compare(start.getY(), objectLoc.getY());
        int endRelY = Integer.compare(end.getY(), objectLoc.getY());
        return startRelX != endRelX || startRelY != endRelY;
    }

    static boolean hasDoorLikeSceneObjectOnSegment(WorldPoint fromWp, WorldPoint toWp,
                                                           WorldPoint playerLoc, int radiusTiles) {
        if (fromWp == null || toWp == null || playerLoc == null || radiusTiles <= 0) {
            return false;
        }
        if (fromWp.getPlane() != toWp.getPlane() || fromWp.getPlane() != playerLoc.getPlane()) {
            return false;
        }
        if (recentlyOpenedStationaryDoorOnSegment(fromWp, toWp)) {
            return false;
        }

        for (WallObject wall : Rs2GameObject.getWallObjects(o -> true, playerLoc, radiusTiles)) {
            if (isPendingRouteDoorObject(wall, fromWp, toWp, playerLoc, radiusTiles)) {
                return true;
            }
        }
        for (GameObject object : Rs2GameObject.getGameObjects(o -> true, playerLoc, radiusTiles)) {
            if (isPendingRouteDoorObject(object, fromWp, toWp, playerLoc, radiusTiles)) {
                return true;
            }
        }
        return false;
    }

    static boolean hasUnresolvedDoorLikeObjectNearRawPath(List<WorldPoint> rawPath,
                                                                  int rawEdgeStart,
                                                                  WorldPoint playerLoc,
                                                                  int backtrackEdges,
                                                                  int lookaheadEdges,
                                                                  int radiusTiles) {
        if (rawPath == null || rawPath.size() < 2 || playerLoc == null || rawEdgeStart < 0) {
            return false;
        }

        int start = Math.max(0, rawEdgeStart - Math.max(0, backtrackEdges));
        int endExclusive = Math.min(rawPath.size() - 1, rawEdgeStart + Math.max(1, lookaheadEdges));
        for (int ri = start; ri < endExclusive && ri < rawPath.size() - 1; ri++) {
            WorldPoint from = rawPath.get(ri);
            WorldPoint to = rawPath.get(ri + 1);
            if (from == null || to == null) {
                continue;
            }
            if (from.getPlane() != playerLoc.getPlane() || to.getPlane() != playerLoc.getPlane()) {
                break;
            }
            if (from.distanceTo2D(playerLoc) > radiusTiles && to.distanceTo2D(playerLoc) > radiusTiles) {
                continue;
            }
            if (shouldDeferDoorHandlingToTransport(rawPath, ri)) {
                continue;
            }
            if (hasUnresolvedDoorLikeSceneObjectOnSegment(from, to, playerLoc, radiusTiles)) {
                return true;
            }
        }
        return false;
    }

    static boolean hasUnresolvedDoorLikeSceneObjectOnSegment(WorldPoint fromWp, WorldPoint toWp,
                                                                     WorldPoint playerLoc, int radiusTiles) {
        if (fromWp == null || toWp == null || playerLoc == null || radiusTiles <= 0) {
            return false;
        }
        if (fromWp.getPlane() != toWp.getPlane() || fromWp.getPlane() != playerLoc.getPlane()) {
            return false;
        }

        for (WallObject wall : Rs2GameObject.getWallObjects(o -> true, playerLoc, radiusTiles)) {
            if (isUnresolvedRouteDoorObject(wall, fromWp, toWp, playerLoc, radiusTiles)) {
                return true;
            }
        }
        for (GameObject object : Rs2GameObject.getGameObjects(o -> true, playerLoc, radiusTiles)) {
            if (isUnresolvedRouteDoorObject(object, fromWp, toWp, playerLoc, radiusTiles)) {
                return true;
            }
        }
        return false;
    }

    static boolean isUnresolvedRouteDoorObject(TileObject object, WorldPoint fromWp, WorldPoint toWp,
                                                       WorldPoint playerLoc, int radiusTiles) {
        if (object == null || object.getWorldLocation() == null) {
            return false;
        }
        WorldPoint location = object.getWorldLocation();
        if (location.getPlane() != playerLoc.getPlane()
                || location.distanceTo2D(playerLoc) > radiusTiles
                || Rs2DoorProbe.isCatalogTransportObject(object)
                || !Rs2DoorGeometry.isDoorOnSegment(object, fromWp, toWp)) {
            return false;
        }
        // A wall door whose face the player is already beyond is resolved, not unresolved: conquered
        // moves-you gates keep their Open action forever, and counting one as an obstacle vetoed the
        // continuation click that ends the fold stall. Same truth as door_skip_crossed.
        if (object instanceof WallObject && Rs2DoorGeometry.playerBeyondWallFace(
                ((WallObject) object).getOrientationA(), location, fromWp, playerLoc)) {
            return false;
        }

        ObjectComposition comp = Rs2DoorDetection.resolveCompositionForDoorProbe(object);
        if (comp == null
                || Rs2DoorClassifier.isNullOrPlaceholderObjectName(comp.getName())
                || Rs2DoorClassifier.doorCompositionSpecifiesOnlyCloseOrShut(comp)) {
            return false;
        }
        String action = Rs2DoorClassifier.pickWalkDoorAction(comp);
        return Rs2DoorClassifier.isRouteDoorObject(object instanceof WallObject, comp.getName(), action);
    }

    static boolean isPendingRouteDoorObject(TileObject object, WorldPoint fromWp, WorldPoint toWp,
                                                    WorldPoint playerLoc, int radiusTiles) {
        if (object == null || object.getWorldLocation() == null) {
            return false;
        }
        WorldPoint location = object.getWorldLocation();
        if (location.getPlane() != playerLoc.getPlane()
                || location.distanceTo2D(playerLoc) > radiusTiles
                || doorAttemptLedger.isDoorBlacklisted(location)
                || Rs2DoorProbe.isCatalogTransportObject(object)
                || !Rs2DoorGeometry.isDoorOnSegment(object, fromWp, toWp)) {
            return false;
        }
        // Same crossed-face resolution as isUnresolvedRouteDoorObject: a conquered gate behind the
        // player must not defer short walks as a "pending" route door.
        if (object instanceof WallObject && Rs2DoorGeometry.playerBeyondWallFace(
                ((WallObject) object).getOrientationA(), location, fromWp, playerLoc)) {
            return false;
        }

        ObjectComposition comp = Rs2DoorDetection.resolveCompositionForDoorProbe(object);
        if (comp == null
                || Rs2DoorClassifier.isNullOrPlaceholderObjectName(comp.getName())
                || Rs2DoorClassifier.doorCompositionSpecifiesOnlyCloseOrShut(comp)) {
            return false;
        }
        String action = Rs2DoorClassifier.pickWalkDoorAction(comp);
        return Rs2DoorClassifier.isRouteDoorObject(object instanceof WallObject, comp.getName(), action);
    }

	/**
	 * Door handling can include dialogue and waits; bound it so the walker cannot hang
	 * indefinitely on a bad interact. If the timeout elapses, return false so the main
	 * loop can continue (stall detection / replans).
	 */
	static boolean handleDoorsWithTimeout(List<WorldPoint> path, int index, long timeoutMs) {
        return handleDoorsWithTimeout(path, index, timeoutMs, false, false);
    }

    static boolean handleDoorsWithTimeoutBudgeted(List<WorldPoint> path, int index, long timeoutMs,
                                                          boolean allowSegmentProbe) {
        return handleDoorsWithTimeout(path, index, timeoutMs, true, allowSegmentProbe);
    }

    static boolean handleDoorsWithTimeout(List<WorldPoint> path, int index, long timeoutMs,
                                                  boolean passBudgeted, boolean allowSegmentProbe) {
		long start = System.currentTimeMillis();
        WorldPoint[] segment = resolveDoorSegment(path, index);
        boolean claimableSegment = segment != null && segment.length >= 2
                && segment[0] != null && segment[1] != null;
        WorldPoint playerBeforeAttempt = Rs2Player.getWorldLocation();
        resetDoorLegStages();
        if (passBudgeted && claimableSegment
                && !doorAttemptLedger.tryClaimEdgeThisPass(segment[0], segment[1], playerBeforeAttempt)) {
            routeState.lastDoorEdgePassSkipAtMs = System.currentTimeMillis();
            WebWalkLog.spInfo("door_edge_pass_skip | idx={}", index);
            return false;
        }
		boolean handled = handleDoors(path, index, allowSegmentProbe);
		if (!handled) {
            // Do not consume one-shot budget when no interaction happened; allow
            // a later resolver in the same pass to attempt this edge.
            if (passBudgeted && claimableSegment) {
                doorAttemptLedger.releaseEdgeThisPass(segment[0], segment[1]);
            }
			return false;
		}
        WebWalkLog.tmark("door_interaction_done", System.currentTimeMillis() - start, currentTarget, playerBeforeAttempt,
                "idx=" + index + doorLegStageDetail(System.currentTimeMillis() - start));
		long remaining = timeoutMs - (System.currentTimeMillis() - start);
		if (remaining <= 0) {
			return true;
		}
		WorldPoint before = Rs2Player.getWorldLocation();
		int remainingInt = (int) Math.min(Integer.MAX_VALUE, remaining);
		sleepUntil(() -> {
			WorldPoint now = Rs2Player.getWorldLocation();
			if (before != null && now != null && !before.equals(now)) return true;
			return Rs2Player.isMoving() || Rs2Dialogue.isInDialogue();
		}, remainingInt);

        if (segment != null && !isDoorEdgeResolved(segment[0], segment[1])) {
            WebWalkLog.spInfo("door_edge_post_unresolved | idx={} from={} to={}",
                    index, compactWorldPoint(segment[0]), compactWorldPoint(segment[1]));
        } else if (segment != null) {
            WebWalkLog.tmark("door_edge_resolved", System.currentTimeMillis() - start, currentTarget,
                    Rs2Player.getWorldLocation(),
                    "from=" + compactWorldPoint(segment[0]) + " to=" + compactWorldPoint(segment[1]));
        }
        return true;
	}

    static WorldPoint[] resolveDoorSegment(List<WorldPoint> path, int index) {
        if (path == null || index < 0 || index >= path.size() - 1) {
            return null;
        }
        WorldPoint fromWp = path.get(index);
        WorldPoint toWp = path.get(index + 1);
        if (fromWp == null || toWp == null) {
            return null;
        }
        boolean isInstance = Microbot.getClient()
                .getTopLevelWorldView()
                .getScene()
                .isInstance();
        if (!isInstance) {
            return new WorldPoint[] {fromWp, toWp};
        }
        WorldPoint convertedFrom = Rs2WorldPoint.convertInstancedWorldPoint(fromWp);
        WorldPoint convertedTo = Rs2WorldPoint.convertInstancedWorldPoint(toWp);
        if (convertedFrom == null || convertedTo == null) {
            return null;
        }
        return new WorldPoint[] {convertedFrom, convertedTo};
    }

	/**
	 * Last-resort door resolver for "tile unreachable near player" stalls.
	 * Scans a very small radius around the player for door-like wall/game objects
	 * and interacts with the best candidate action.
	 */
	static boolean tryResolveNearbyDoorBlocker(WorldPoint playerLoc, int radiusTiles) {
		if (playerLoc == null || radiusTiles <= 0) return false;

		TileObject best = null;
		String bestAction = null;
		int bestActionPri = Integer.MAX_VALUE;
		int bestDist = Integer.MAX_VALUE;
		int scannedWalls = 0;
		int scannedGames = 0;
		int candidates = 0;

		for (WallObject w : Rs2GameObject.getWallObjects(o -> true, playerLoc, radiusTiles)) {
			if (w == null) continue;
			scannedWalls++;
			ObjectComposition comp = Rs2DoorDetection.resolveCompositionForDoorProbe(w);
			if (comp == null || Rs2DoorClassifier.isNullOrPlaceholderObjectName(comp.getName())) continue;
			if (Rs2DoorClassifier.doorCompositionSpecifiesOnlyCloseOrShut(comp)) continue;

			String action = Rs2DoorClassifier.pickWalkDoorAction(comp);
			boolean doorLike = Rs2DoorClassifier.isRouteDoorObject(true, comp.getName(), action);
			if (!doorLike) continue;
			if (Rs2DoorProbe.isCatalogTransportObject(w)) continue;
			candidates++;

			// Allow empty-action doors: use default interact.
			String actionFinal = action == null ? "" : action;
			int dist = w.getWorldLocation() == null ? Integer.MAX_VALUE : w.getWorldLocation().distanceTo2D(playerLoc);
			int pri = actionFinal.isEmpty() ? Integer.MAX_VALUE : Rs2DoorClassifier.doorActionPriorityIndex(actionFinal);
			if (best == null || pri < bestActionPri || (pri == bestActionPri && dist < bestDist)) {
				best = w;
				bestAction = actionFinal;
				bestActionPri = pri;
				bestDist = dist;
			}
		}

		for (GameObject g : Rs2GameObject.getGameObjects(o -> true, playerLoc, radiusTiles)) {
			if (g == null) continue;
			scannedGames++;
			ObjectComposition comp = Rs2DoorDetection.resolveCompositionForDoorProbe(g);
			if (comp == null || Rs2DoorClassifier.isNullOrPlaceholderObjectName(comp.getName())) continue;
			if (Rs2DoorClassifier.doorCompositionSpecifiesOnlyCloseOrShut(comp)) continue;

			String action = Rs2DoorClassifier.pickWalkDoorAction(comp);
			boolean doorLike = Rs2DoorClassifier.isRouteDoorObject(false, comp.getName(), action);
			if (!doorLike) continue;
			if (Rs2DoorProbe.isCatalogTransportObject(g)) continue;
			candidates++;

			String actionFinal = action == null ? "" : action;
			int dist = g.getWorldLocation() == null ? Integer.MAX_VALUE : g.getWorldLocation().distanceTo2D(playerLoc);
			int pri = actionFinal.isEmpty() ? Integer.MAX_VALUE : Rs2DoorClassifier.doorActionPriorityIndex(actionFinal);
			if (best == null || pri < bestActionPri || (pri == bestActionPri && dist < bestDist)) {
				best = g;
				bestAction = actionFinal;
				bestActionPri = pri;
				bestDist = dist;
			}
		}

		if (best == null || bestAction == null) {
			log.info("[Walker] fallback door-scan: no candidates (radius={} player={} scannedWalls={} scannedGames={} candidates={})",
					radiusTiles, playerLoc, scannedWalls, scannedGames, candidates);
			return false;
		}

		WorldPoint before = Rs2Player.getWorldLocation();
		log.info("[Walker] fallback door-scan: action={} at {}", bestAction.isEmpty() ? "<default>" : bestAction, best.getWorldLocation());
		if (bestAction.isEmpty()) {
			Rs2GameObject.interact(best);
		} else {
			Rs2GameObject.interact(best, bestAction);
		}
		Rs2Player.waitForWalking();
		sleepUntil(() -> {
			WorldPoint now = Rs2Player.getWorldLocation();
			if (before != null && now != null && !before.equals(now)) return true;
			return Rs2Player.isMoving() || Rs2Dialogue.isInDialogue();
		}, 1500);
		return true;
	}

	/**
	 * LOS-based door resolution: when a path says "go through that door" but local reachability
	 * says "unreachable", we may be a few tiles away from the actual door object. Scan door-like
	 * objects in a wider radius and require line-of-sight from the player, then interact with the
	 * best candidate (closest to the upcoming path tiles).
	 */
	static boolean tryResolveDoorBlockerLineOfSight(WorldPoint playerLoc, List<WorldPoint> path, int startIdx, int radiusTiles) {
		if (playerLoc == null || path == null || path.size() < 2) return false;
		if (startIdx < 0 || startIdx >= path.size()) return false;

		TileObject best = null;
		String bestAction = null;
		int bestScore = Integer.MAX_VALUE;

		// Look a little ahead along the path to bias toward the intended door edge.
		int endIdx = Math.min(path.size() - 1, startIdx + 10);

		for (WallObject w : Rs2GameObject.getWallObjects(o -> true, playerLoc, radiusTiles)) {
			if (w == null) continue;
			if (!Rs2GameObject.hasLineOfSight(playerLoc, w)) continue;

			ObjectComposition comp = Rs2DoorDetection.resolveCompositionForDoorProbe(w);
			if (comp == null || Rs2DoorClassifier.isNullOrPlaceholderObjectName(comp.getName())) continue;
			if (Rs2DoorClassifier.doorCompositionSpecifiesOnlyCloseOrShut(comp)) continue;

			String action = Rs2DoorClassifier.pickWalkDoorAction(comp);

			boolean doorLike = Rs2DoorClassifier.isRouteDoorObject(true, comp.getName(), action);
			if (!doorLike) continue;
			if (Rs2DoorProbe.isCatalogTransportObject(w)) continue;

			String actionFinal = action == null ? "" : action;

			// Score by proximity to upcoming path tiles (lower is better).
			int score = Integer.MAX_VALUE;
			WorldPoint objWp = w.getWorldLocation();
			if (objWp != null) {
				for (int j = startIdx; j <= endIdx; j++) {
					WorldPoint pj = path.get(j);
					if (pj == null) continue;
					score = Math.min(score, objWp.distanceTo2D(pj));
				}
				// Tie-break toward closer objects.
				score = score * 10 + objWp.distanceTo2D(playerLoc);
			}

			if (best == null || score < bestScore) {
				best = w;
				bestAction = actionFinal;
				bestScore = score;
			}
		}

		for (GameObject g : Rs2GameObject.getGameObjects(o -> true, playerLoc, radiusTiles)) {
			if (g == null) continue;
			if (!Rs2GameObject.hasLineOfSight(playerLoc, g)) continue;

			ObjectComposition comp = Rs2DoorDetection.resolveCompositionForDoorProbe(g);
			if (comp == null || Rs2DoorClassifier.isNullOrPlaceholderObjectName(comp.getName())) continue;
			if (Rs2DoorClassifier.doorCompositionSpecifiesOnlyCloseOrShut(comp)) continue;

			String action = Rs2DoorClassifier.pickWalkDoorAction(comp);

			boolean doorLike = Rs2DoorClassifier.isRouteDoorObject(false, comp.getName(), action);
			if (!doorLike) continue;
			if (Rs2DoorProbe.isCatalogTransportObject(g)) continue;

			String actionFinal = action == null ? "" : action;

			int score = Integer.MAX_VALUE;
			WorldPoint objWp = g.getWorldLocation();
			if (objWp != null) {
				for (int j = startIdx; j <= endIdx; j++) {
					WorldPoint pj = path.get(j);
					if (pj == null) continue;
					score = Math.min(score, objWp.distanceTo2D(pj));
				}
				score = score * 10 + objWp.distanceTo2D(playerLoc);
			}

			if (best == null || score < bestScore) {
				best = g;
				bestAction = actionFinal;
				bestScore = score;
			}
		}

		if (best == null) {
			log.info("[Walker] LOS door-scan: no candidates (radius={} player={} idx={}/{})", radiusTiles, playerLoc, startIdx, path.size());
			return false;
		}

		log.info("[Walker] LOS door-scan: score={} action={} at {}", bestScore, (bestAction == null || bestAction.isEmpty()) ? "<default>" : bestAction, best.getWorldLocation());
		if (bestAction == null || bestAction.isEmpty()) {
			Rs2GameObject.interact(best);
		} else {
			Rs2GameObject.interact(best, bestAction);
		}
		Rs2Player.waitForWalking();
		return true;
	}

    static String normalizePathAdjFamilyKey(TileObject object, String action) {
        ObjectComposition comp = Rs2DoorDetection.resolveCompositionForDoorProbe(object);
        String name = comp != null && comp.getName() != null ? comp.getName().toLowerCase(Locale.ROOT).trim() : "unknown";
        String act = action == null ? "" : action.toLowerCase(Locale.ROOT).trim();
        WorldPoint loc = object != null ? object.getWorldLocation() : null;
        int plane = loc != null ? loc.getPlane() : -1;
        int objectId = object != null ? object.getId() : -1;
        int idRangeLow = objectId >= 0 ? objectId - 1 : -1;
        int idRangeHigh = objectId >= 0 ? objectId + 1 : -1;
        return name + "|" + act + "|p" + plane + "|id=" + idRangeLow + "-" + idRangeHigh;
    }

    static boolean arePathAdjFamiliesCompatible(String a, String b) {
        if (Objects.equals(a, b)) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        int aIdTag = a.indexOf("|id=");
        int bIdTag = b.indexOf("|id=");
        if (aIdTag <= 0 || bIdTag <= 0) {
            return false;
        }
        String aBase = a.substring(0, aIdTag);
        String bBase = b.substring(0, bIdTag);
        if (!Objects.equals(aBase, bBase)) {
            return false;
        }
        int[] aRange = parsePathAdjIdRange(a.substring(aIdTag + 4));
        int[] bRange = parsePathAdjIdRange(b.substring(bIdTag + 4));
        if (aRange == null || bRange == null) {
            return false;
        }
        return Math.max(aRange[0], bRange[0]) <= Math.min(aRange[1], bRange[1]);
    }

    static int[] parsePathAdjIdRange(String range) {
        if (range == null || range.isEmpty()) {
            return null;
        }
        int sep = range.indexOf('-');
        if (sep <= 0 || sep >= range.length() - 1) {
            return null;
        }
        try {
            int low = Integer.parseInt(range.substring(0, sep));
            int high = Integer.parseInt(range.substring(sep + 1));
            if (high < low) {
                return null;
            }
            return new int[] {low, high};
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static void markNearbyDoorFamilyOpened(TileObject originObject, WorldPoint originLocation, String action, int radiusTiles) {
        if (originObject == null || originLocation == null || radiusTiles <= 0) {
            return;
        }
        String familyKey = normalizePathAdjFamilyKey(originObject, action);
        if (familyKey == null || familyKey.isEmpty()) {
            markStationaryDoorOpened(originLocation);
            return;
        }
        markStationaryDoorOpened(originLocation);
        for (WallObject wall : Rs2GameObject.getWallObjects(o -> true, originLocation, radiusTiles)) {
            if (wall == null || wall.getWorldLocation() == null) {
                continue;
            }
            if (wall.getWorldLocation().getPlane() != originLocation.getPlane()) {
                continue;
            }
            ObjectComposition comp = Rs2DoorDetection.resolveCompositionForDoorProbe(wall);
            String neighborFamily = normalizePathAdjFamilyKey(wall, comp == null ? null : Rs2DoorClassifier.pickWalkDoorAction(comp));
            if (arePathAdjFamiliesCompatible(familyKey, neighborFamily)) {
                markStationaryDoorOpened(wall.getWorldLocation());
            }
        }
        for (GameObject game : Rs2GameObject.getGameObjects(o -> true, originLocation, radiusTiles)) {
            if (game == null || game.getWorldLocation() == null) {
                continue;
            }
            if (game.getWorldLocation().getPlane() != originLocation.getPlane()) {
                continue;
            }
            ObjectComposition comp = Rs2DoorDetection.resolveCompositionForDoorProbe(game);
            String neighborFamily = normalizePathAdjFamilyKey(game, comp == null ? null : Rs2DoorClassifier.pickWalkDoorAction(comp));
            if (arePathAdjFamiliesCompatible(familyKey, neighborFamily)) {
                markStationaryDoorOpened(game.getWorldLocation());
            }
        }
    }

    static List<PathAdjDoorComponent> buildPathAdjDoorComponents(
            Collection<PathAdjDoorCandidate> candidates,
            int startIdx,
            WorldPoint playerLoc) {
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }
        List<PathAdjDoorCandidate> list = new ArrayList<>(candidates);
        boolean[] visited = new boolean[list.size()];
        List<PathAdjDoorComponent> components = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            if (visited[i]) {
                continue;
            }
            PathAdjDoorCandidate seed = list.get(i);
            visited[i] = true;
            java.util.Deque<Integer> queue = new ArrayDeque<>();
            queue.add(i);
            List<PathAdjDoorCandidate> members = new ArrayList<>();
            members.add(seed);
            while (!queue.isEmpty()) {
                int idx = queue.removeFirst();
                PathAdjDoorCandidate a = list.get(idx);
                for (int j = 0; j < list.size(); j++) {
                    if (visited[j]) {
                        continue;
                    }
                    PathAdjDoorCandidate b = list.get(j);
                    if (!arePathAdjFamiliesCompatible(a.familyKey, b.familyKey)) {
                        continue;
                    }
                    if (a.location == null || b.location == null) {
                        continue;
                    }
                    int tileGap = a.location.distanceTo2D(b.location);
                    int edgeGap = Math.abs(a.edgeIdx - b.edgeIdx);
                    if (tileGap > PATH_ADJ_COMPONENT_LINK_MAX_TILE_GAP
                            && edgeGap > PATH_ADJ_COMPONENT_LINK_MAX_EDGE_GAP) {
                        continue;
                    }
                    visited[j] = true;
                    queue.addLast(j);
                    members.add(b);
                }
            }
            PathAdjDoorCandidate best = null;
            int earliestEdge = Integer.MAX_VALUE;
            int bestLocalScore = Integer.MAX_VALUE;
            Set<WorldPoint> locs = new LinkedHashSet<>();
            for (PathAdjDoorCandidate c : members) {
                locs.add(c.location);
                earliestEdge = Math.min(earliestEdge, c.edgeIdx);
                int pri = c.actionPriority == Integer.MAX_VALUE ? 100 : c.actionPriority;
                int localScore = c.edgeDist * 100 + pri * 10
                        + (playerLoc != null && c.location != null ? c.location.distanceTo2D(playerLoc) : 0);
                if (best == null || localScore < bestLocalScore) {
                    best = c;
                    bestLocalScore = localScore;
                }
            }
            int edgeOffset = Math.max(0, earliestEdge - startIdx);
            int componentScore = edgeOffset * 1000 + bestLocalScore;
            components.add(new PathAdjDoorComponent(best, componentScore, locs));
        }
        return components;
    }



	/**
	 * Scan a few path indices near the player (<= radius tiles) and attempt to resolve
	 * any door/gate blocks before issuing further minimap clicks.
	 */
	static boolean tryHandleNearbyDoorsWithTimeout(List<WorldPoint> path, int startIdx, int radiusTiles, long timeoutMs) {
		if (path == null || path.isEmpty() || startIdx < 0) return false;
		final WorldPoint playerLoc = Rs2Player.getWorldLocation();
		if (playerLoc == null) return false;

		int start = Math.min(startIdx, path.size() - 2);
		for (int j = start; j < path.size() - 1; j++) {
			WorldPoint wp = path.get(j);
			if (wp == null) continue;
			if (wp.getPlane() != playerLoc.getPlane()) break;
			if (wp.distanceTo2D(playerLoc) > radiusTiles) {
				// Path is ordered; once we're beyond radius, later indices will likely be further.
				break;
			}
			if (handleDoorsWithTimeout(path, j, timeoutMs)) {
				return true;
			}
		}
		return false;
	}

    static boolean handleDoorException(TileObject object, String action) {
        long startedAt = System.currentTimeMillis();
        try {
            if (isInStrongholdOfSecurity()) {
                return handleStrongholdOfSecurityAnswer(object, action);
            }
            return false;
        } finally {
            doorLegExceptionMs += System.currentTimeMillis() - startedAt;
        }
    }

    static boolean isInStrongholdOfSecurity() {
        List<Integer> mapRegionIds = List.of(7505, 7504, 7760, 7503, 7759, 7758, 7757, 8013, 7756, 8012, 8017, 8530, 9297);
        return mapRegionIds.contains(Rs2Player.getWorldLocation().getRegionID());
    }

    static boolean handleStrongholdOfSecurityAnswer(TileObject object, String action) {
        // Captured before the click: crossing is judged against where the approach started, and the
        // wall's orientation/tile are immutable for the object's lifetime.
        final WorldPoint before = Rs2Player.getWorldLocation();
        final int wallOrientation = object instanceof WallObject ? ((WallObject) object).getOrientationA() : -1;
        final WorldPoint wallTile = object.getWorldLocation();
        Rs2GameObject.interact(object, action);
        // The gates only ask their question until it has been answered; every later crossing just
        // carries the player through. The old sleepUntilInDialogue here waited its FULL flat timeout
        // on every questionless gate — the leg breakdown traced the corridor's constant ~5.4s per
        // gate (find=0 interact=0 await=0 verify=0 nudge=0, all of it "other") to this one line,
        // ~60 seconds of sleeps across eleven gates for dialogues that never came. Wait for
        // whichever actually happens: the dialogue, or the crossing itself.
        // Distance-scaled, like the door await's traversal budget: a ranged click spends its first
        // seconds being server-walked to the gate, and the flat 5s expired MID-APPROACH — measured
        // as every far-clicked gate paying the full budget and then a duplicate re-attempt from up
        // close (5399ms + 576ms for one gate), while near clicks released in ~0.3-2.7s.
        final int clickDistance = before != null && wallTile != null && before.getPlane() == wallTile.getPlane()
                ? before.distanceTo2D(wallTile) : 0;
        final int strongholdWaitMs = 5000 + Math.min(6000, clickDistance * 600);
        sleepUntil(() -> {
            if (Rs2Dialogue.isInDialogue()) {
                return true;
            }
            WorldPoint now = Rs2Player.getWorldLocation();
            return wallOrientation > 0 && now != null
                    && Rs2DoorGeometry.playerBeyondWallFace(wallOrientation, wallTile, before, now);
        }, strongholdWaitMs);

        // Not all the doors ask questions, so only if dialogue is shown we will attempt to get the answer
        if (!Rs2Dialogue.isInDialogue()) return true;

        // Skip over first door dialogue & don't forget to set up two-factor warning
        if (Rs2Dialogue.getDialogueText().toLowerCase().contains("two-factor authentication options") || Rs2Dialogue.getDialogueText().toLowerCase().contains("hopefully you will learn<br>much from us.")) {
            Rs2Dialogue.sleepUntilHasContinue();
            sleepUntil(() -> !Rs2Dialogue.hasContinue() || Rs2Dialogue.getDialogueText().toLowerCase().contains("to pass you must answer me"), Rs2Dialogue::clickContinue, 5000, Rs2Random.between(600, 800));
            if (!Rs2Dialogue.isInDialogue()) return true;
        }

        String dialogueAnswer = null;
        int attempts = 0;
        final int maxAttempts = 5;

        // We attempt to find the answer multiple times in-case there is dialogue that appears before the question
        while (dialogueAnswer == null && attempts < maxAttempts) {
            if (currentTarget == null) break;
            dialogueAnswer = StrongholdAnswer.findAnswer(Rs2Dialogue.getDialogueText());
            if (dialogueAnswer == null) {
                Rs2Dialogue.clickContinue();
                Rs2Random.waitEx(800, 100);
            }
            attempts++;
        }

        if (dialogueAnswer != null) {
            Rs2Dialogue.clickContinue();
            Rs2Dialogue.sleepUntilSelectAnOption();
            Rs2Dialogue.clickOption(dialogueAnswer);
            Rs2Dialogue.sleepUntilHasContinue();
            sleepUntil(() -> !Rs2Dialogue.hasContinue(), Rs2Dialogue::clickContinue, 5000, Rs2Random.between(600, 800));
            Rs2Player.waitForAnimation(1200);
            return true;
        }

        return false;
    }

    /**
     * Determines whether a given neighbor tile lies immediately adjacent to
     * a reference tile, in the direction specified by a wall orientation code.
     *
     * @param orientation the wall orientation code:
     *                    <ul>
     *                      <li>1 = west</li>
     *                      <li>2 = north</li>
     *                      <li>4 = east</li>
     *                      <li>8 = south</li>
     *                      <li>16 = northwest</li>
     *                      <li>32 = northeast</li>
     *                      <li>64 = southeast</li>
     *                      <li>128 = southwest</li>
     *                    </ul>
     * @param point       the reference {@link WorldPoint} representing the tile at the wall’s base
     * @param neighbor    the {@link WorldPoint} to test for adjacency
     * @return {@code true} if {@code neighbor} is exactly one tile away from {@code point}
     *         in the direction indicated by {@code orientation}, {@code false} otherwise
     */
    static boolean searchNeighborPoint(int orientation, WorldPoint point, WorldPoint neighbor) {
        int dx = neighbor.getX() - point.getX();
        int dy = neighbor.getY() - point.getY();

        switch (orientation) {
            case 1:   // west
                return dx == -1 && dy == 0;
            case 2:   // north
                return dx == 0  && dy == 1;
            case 4:   // east
                return dx == 1  && dy == 0;
            case 8:   // south
                return dx == 0  && dy == -1;
            case 16:  // northwest
                return dx == -1 && dy == 1;
            case 32:  // northeast
                return dx == 1  && dy == 1;
            case 64:  // southeast
                return dx == 1  && dy == -1;
            case 128: // southwest
                return dx == -1 && dy == -1;
            default:
                return false;
        }
    }

    static boolean handleDoorsInRawSegment(List<WorldPoint> rawPath, int rawFrom, int rawTo,
                                                    long timeoutMs,
                                                    Map<WorldPoint, Integer> reachableCache) {
        long passT0 = System.currentTimeMillis();
        try {
            return handleDoorsInRawSegmentInner(rawPath, rawFrom, rawTo, timeoutMs, reachableCache);
        } finally {
            WalkPassStats.segDoorMs.addAndGet(System.currentTimeMillis() - passT0);
        }
    }

    private static boolean handleDoorsInRawSegmentInner(List<WorldPoint> rawPath, int rawFrom, int rawTo,
                                                    long timeoutMs,
                                                    Map<WorldPoint, Integer> reachableCache) {
        WorldPoint playerLoc = reachableCache != null ? Rs2Player.getWorldLocation() : null;
        long startedAt = System.currentTimeMillis();
        for (int ri = rawFrom; ri < rawTo && ri < rawPath.size() - 1; ri++) {
            long elapsed = System.currentTimeMillis() - startedAt;
            if (elapsed >= timeoutMs) {
                return false;
            }
            if (!isDoorNearSideReachable(reachableCache, rawPath.get(ri))) {
                return false;
            }
            if (reachableCache != null && reachableCache.containsKey(rawPath.get(ri))
                    && reachableCache.containsKey(rawPath.get(ri + 1))
                    && !hasDoorLikeSceneObjectOnSegment(rawPath.get(ri), rawPath.get(ri + 1),
                            playerLoc, HANDLER_RANGE)) {
                continue;
            }
            long remainingTimeoutMs = Math.max(1L, timeoutMs - elapsed);
            if (handleDoorsWithTimeoutBudgeted(rawPath, ri, remainingTimeoutMs, false)) {
                return true;
            }
            if (isDoorInteractionSettling()) {
                return false;
            }
        }
        return false;
    }

    /** Same switch, for opening the nearest route door without waiting out the approach walk. */
    static boolean doorInteractionWhileApproachingEnabled() {
        return rangedTransportDispatchEnabled();
    }

    /**
     * Whether a door interaction must wait because the player is moving.
     * <p>
     * Relaxing only the caller-side gate was not enough: the interaction sites carry their own
     * {@code isMoving()} checks, so the handler ran during the approach and then declined anyway.
     * <p>
     * Scoping the permission to the segment loop was ALSO not enough — door handling is reached from
     * the recovery path and the raw scene scan as well, and a Falador castle run on the fixed build
     * still logged {@code door_interact_deferred | reason=moving mode=segment-door} from the
     * reachability-miss recovery. Those entry points each act on the door blocking the route RIGHT
     * NOW, so there is no ordering left to protect at this level: the only question here is whether
     * the walker is allowed to interrupt its own walk, which is exactly what the feature is for.
     * Route ordering is enforced where it belongs — the segment loop, which iterates many segments
     * and still only lets the nearest one act while moving.
     */
    static boolean doorInteractionDeferredForMovement(WorldPoint doorTile) {
        if (!Rs2Player.isMoving()) {
            return false;
        }
        if (!doorInteractionWhileApproachingEnabled()) {
            return true;
        }
        // While MOVING, only act on a door we are practically standing at. The probe searches ten
        // tiles, which was harmless while interaction required standing still — arriving implied
        // proximity. Acting mid-walk removed that implication, and the walker opened the door at
        // (2985,3341) from nine tiles out while (2981,3340) was still shut in front of it: the
        // interaction timed out against the closed near door, the player drifted backwards, and the
        // walk lost ~15s to recovery clicks before the real blocker was handled.
        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        return playerLoc == null
                || doorTile == null
                || doorTile.getPlane() != playerLoc.getPlane()
                || doorTile.distanceTo2D(playerLoc) > DOOR_APPROACH_INTERACT_MAX_TILES;
    }

    static void logRouteClear(String reason) {
        routeState.lastRouteClearReason = reason == null ? "" : reason;
        routeState.lastRouteClearAtMs = System.currentTimeMillis();
        if (reason == null || reason.isBlank()) {
            WebWalkLog.routeClearMissingReason(Thread.currentThread().getName());
        } else {
            WebWalkLog.routeClear(reason);
        }
    }

    static boolean walkReachableMiniMapToward(WorldPoint target, WorldPoint playerLoc, int maxEuclidean) {
        int currentDistance = euclideanSq(playerLoc, target);
        return Rs2Tile.getReachableTilesFromTile(playerLoc, Math.max(2, maxEuclidean)).keySet().stream()
                .filter(tile -> tile != null
                        && tile.getPlane() == playerLoc.getPlane()
                        && !tile.equals(playerLoc)
                        && euclideanSq(playerLoc, tile) <= maxEuclidean * maxEuclidean
                        && euclideanSq(tile, target) < currentDistance)
                .sorted(Comparator
                        .comparingInt((WorldPoint tile) -> euclideanSq(tile, target))
                        .thenComparing(Comparator.comparingInt((WorldPoint tile) -> euclideanSq(playerLoc, tile)).reversed()))
                .filter(Rs2Walker::walkMiniMap)
                .findFirst()
                .map(tile -> {
                    log.info("[Walker] Minimap click target {} was outside clip; used reachable fallback {}", target, tile);
                    return true;
                })
                .orElse(false);
    }

    static HashMap<WorldPoint, Integer> nearbyTilesIgnoringCollision(
            WorldPoint origin, int radius) {
        HashMap<WorldPoint, Integer> result = new HashMap<>();
        if (origin == null || radius < 0) {
            return result;
        }
        int boundedRadius = Math.min(radius, CLOSEST_INDEX_REACHABLE_STEP_BUDGET);
        for (int dx = -boundedRadius; dx <= boundedRadius; dx++) {
            for (int dy = -boundedRadius; dy <= boundedRadius; dy++) {
                int distance = Math.max(Math.abs(dx), Math.abs(dy));
                if (distance <= boundedRadius) {
                    result.put(new WorldPoint(
                            origin.getX() + dx,
                            origin.getY() + dy,
                            origin.getPlane()), distance);
                }
            }
        }
        return result;
    }

    /**
     * Updates world-map marker and restarts pathfinding for {@code target}. Does not assign
     * {@link #currentTarget}; callers set it when appropriate.
     */
    static void applyWalkerDestination(WorldPoint target) {
        Rs2WalkerLifecycleRuntime.applyWalkerDestination(target);
    }

    private static AdjacentDoorCandidate captureActionedDoorAdjacentToBlockedEdge(
            WorldPoint fromWp, WorldPoint toWp, WorldPoint playerLoc, List<String> doorActions) {
        if (fromWp == null || toWp == null || playerLoc == null || doorActions == null) {
            return null;
        }
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            TileObject best = null;
            WorldPoint bestLocation = null;
            int bestDistance = Integer.MAX_VALUE;
            for (TileObject candidate : Rs2GameObject.getAll()) {
                if (candidate == null) {
                    continue;
                }
                WorldPoint location = candidate.getWorldLocation();
                if (!doorTileAdjacentToEdgeEndpoints(location, fromWp, toWp)
                        || location.getPlane() != playerLoc.getPlane()
                        || location.distanceTo2D(playerLoc) > HANDLER_RANGE
                        || doorAttemptLedger.isDoorBlacklisted(location)
                        || wasStationaryDoorOpenedRecently(location)
                        || Rs2DoorProbe.isCatalogTransportObject(candidate)
                        || !Rs2DoorDetection.isDoorLikeSceneObject(candidate)) {
                    continue;
                }
                ObjectComposition comp = Rs2DoorDetection.resolveCompositionForDoorProbe(candidate);
                if (Rs2DoorClassifier.getDoorAction(comp, doorActions) == null) {
                    continue;
                }
                int distance = location.distanceTo2D(playerLoc);
                if (best == null || distance < bestDistance) {
                    best = candidate;
                    bestLocation = location;
                    bestDistance = distance;
                }
            }
            return best == null ? null : new AdjacentDoorCandidate(best, bestLocation);
        }).orElse(null);
    }

    private static final class AdjacentDoorCandidate {
        private final TileObject object;
        private final WorldPoint location;

        private AdjacentDoorCandidate(TileObject object, WorldPoint location) {
            this.object = object;
            this.location = location;
        }
    }
}
