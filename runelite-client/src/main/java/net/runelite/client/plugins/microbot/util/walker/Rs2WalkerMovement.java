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
import static net.runelite.client.plugins.microbot.util.walker.Rs2WalkerDoors.*;
import static net.runelite.client.plugins.microbot.util.Global.*;
import static net.runelite.client.plugins.microbot.util.walker.Rs2Walker.*;
import static net.runelite.client.plugins.microbot.util.walker.Rs2WalkerDoors.*;
import static net.runelite.client.plugins.microbot.util.walker.Rs2WalkerTransports.*;

/**
 * The movement/click component extracted from {@code Rs2Walker} (Phase E3, 2026-08-14): minimap,
 * canvas and scene click issuance, route click-target selection, interim-target lifecycle, short
 * walks, idle nudges and stamina — the movement-family methods and their exclusive helpers, moved
 * verbatim. The route loop keeps deciding WHEN to move; this class owns HOW a movement is issued.
 * Members are package-private; the four walker classes consume each other via static imports.
 */
@lombok.extern.slf4j.Slf4j
final class Rs2WalkerMovement {

    private Rs2WalkerMovement() {
    }

    /**
     * How far a minimap stride may reach at {@code minimapZoom}, in tiles — for EVERY zoom level, in
     * both directions. The minimap shows {@code 20 * 4 / zoom} tiles of radius (the scale
     * Perspective.localToMinimap uses), so reach follows what the user's zoom makes visible: zoomed
     * out, big strides (capped at the BFS horizon); zoomed in, short ones (a click must land inside
     * the visible circle, two tiles off the rim). An unreadable zoom falls back to the flat reach
     * the walker always had.
     */
    static int zoomAwareMinimapReach(double minimapZoom, int minTiles, int capTiles, int fallbackTiles) {
        if (minimapZoom <= 0) {
            return fallbackTiles;
        }
        int visibleRadius = (int) Math.floor(20.0 * 4.0 / minimapZoom) - 2;
        return Math.max(minTiles, Math.min(visibleRadius, capTiles));
    }

    /** Shell wrapper: the live zoom read, clamped to [functional floor, BFS horizon]. */
    static int normalMinimapReach() {
        try {
            return zoomAwareMinimapReach(Microbot.getClient().getMinimapZoom(),
                    MIN_MINIMAP_REACH_EUCLIDEAN, ZOOMED_OUT_MINIMAP_REACH_CAP,
                    NORMAL_MINIMAP_REACH_EUCLIDEAN);
        } catch (Exception e) {
            return NORMAL_MINIMAP_REACH_EUCLIDEAN;
        }
    }

    static void markFirstMovementClick(String phase, WorldPoint target, WorldPoint at, String detail) {
        if (routeState.firstMovementClickMarked) {
            return;
        }
        long startedAt = routeState.walkSessionStartedAtMs;
        if (startedAt <= 0) {
            return;
        }
        routeState.firstMovementClickMarked = true;
        WebWalkLog.tmark(phase, System.currentTimeMillis() - startedAt, target, at, detail);
    }

    static boolean shouldRunActiveRouteIdleNudge(boolean idleNudgeDue,
                                                boolean immediateRouteTransportPending) {
        return idleNudgeDue && !immediateRouteTransportPending;
    }

    static void waitUntilIdleAfterSceneWalk(WorldPoint cancelGoal, int timeoutMs) {
        waitUntilIdleAfterSceneWalk(cancelGoal, timeoutMs, null, 0);
    }

    /**
     * Waits until idle, walk cancel, or player within {@code arrivalMaxChebyshev} Chebyshev steps of
     * {@code arrivalGoal} (same plane; see {@link WorldPoint#distanceTo2D(WorldPoint)}) — avoids burning full
     * timeout when {@code Rs2Player#isMoving()} lies during animations. Arrival uses an <em>inclusive</em> bound:
     * {@code distanceTo2D(arrivalGoal) <= arrivalMaxChebyshev} (unlike {@link #OFFSET}-style guards that use
     * {@code distanceTo2D &lt; OFFSET}). If arrival distance triggers while still
     * moving, runs a short second phase idle-only wait. Phase 2 does not run when phase 1 ends only due to the
     * outer timeout while still far from {@code arrivalGoal} (by design).
     */
    static void waitUntilIdleAfterSceneWalk(WorldPoint cancelGoal, int timeoutMs,
            WorldPoint arrivalGoal, int arrivalMaxChebyshev) {
        assert cancelGoal != null;
        assert timeoutMs > 0;
        sleepUntil(() -> {
            if (isWalkCancelled(cancelGoal)) {
                return true;
            }
            WorldPoint pl = Rs2Player.getWorldLocation();
            if (arrivalGoal != null && arrivalMaxChebyshev >= 0 && pl != null
                    && arrivalGoal.getPlane() == pl.getPlane()
                    && pl.distanceTo2D(arrivalGoal) <= arrivalMaxChebyshev) {
                return true;
            }
            return !Rs2Player.isMoving();
        }, timeoutMs);
        // Sample player once after phase 1 — rare tick skew vs isMoving(); phase 2 only refines idle after arrival exit.
        WorldPoint plAfter = Rs2Player.getWorldLocation();
        boolean withinArrival = arrivalGoal != null && arrivalMaxChebyshev >= 0 && plAfter != null
                && arrivalGoal.getPlane() == plAfter.getPlane()
                && plAfter.distanceTo2D(arrivalGoal) <= arrivalMaxChebyshev;
        if (withinArrival && Rs2Player.isMoving()) {
            sleepUntil(() -> isWalkCancelled(cancelGoal) || !Rs2Player.isMoving(),
                    POST_SCENE_WALK_IDLE_SECOND_PHASE_MS_MAX);
        }
    }

    static boolean hasMinimapRelevantMovementFlag(LocalPoint point, int[][] flagMap) {
        int data = flagMap[point.getSceneX()][point.getSceneY()];
        Set<MovementFlag> movementFlags = MovementFlag.getSetFlags(data);

        if (movementFlags.contains(MovementFlag.BLOCK_MOVEMENT_EAST)
                && Rs2Tile.isWalkable(point.dx(1)))
            return true;

        if (movementFlags.contains(MovementFlag.BLOCK_MOVEMENT_WEST)
                && Rs2Tile.isWalkable(point.dx(-1)))
            return true;

        if (movementFlags.contains(MovementFlag.BLOCK_MOVEMENT_NORTH)
                && Rs2Tile.isWalkable(point.dy(1)))
            return true;

        return movementFlags.contains(MovementFlag.BLOCK_MOVEMENT_SOUTH)
                && Rs2Tile.isWalkable(point.dy(-1));
    }

    static int computeStaminaThreshold(String playerName, long installSeed) {
        if (playerName == null || playerName.isEmpty()) {
            return STAMINA_THRESHOLD_FALLBACK;
        }
        long nameHash = mix64(playerName.toLowerCase());
        long seed = nameHash ^ installSeed;
        java.util.Random rng = new java.util.Random(seed);
        if (rng.nextDouble() < STAMINA_HARDCORE_PROBABILITY) {
            int span = STAMINA_HARDCORE_MAX - STAMINA_HARDCORE_MIN + 1;
            return STAMINA_HARDCORE_MIN + rng.nextInt(span);
        }
        int span = STAMINA_CASUAL_MAX - STAMINA_CASUAL_MIN + 1;
        return STAMINA_CASUAL_MIN + rng.nextInt(span);
    }

    static long mix64(String s) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x100000001b3L;
        }
        return h;
    }

    static int staminaThreshold() {
        String name = null;
        try {
            var player = Microbot.getClient().getLocalPlayer();
            if (player != null) name = player.getName();
        } catch (Exception ignored) {
        }
        if (name == null || name.isEmpty()) {
            return staminaThresholdCached;
        }
        if (!name.equals(staminaSeedName)) {
            staminaSeedName = name;
            staminaThresholdCached = computeStaminaThreshold(name, Microbot.getInstallSeed());
        }
        return staminaThresholdCached;
    }

    /** Side-effect free: a "could I click this?" probe must never move the user's zoom. */
    static boolean isMiniMapClickable(WorldPoint worldPoint) {
        if (worldPoint == null) {
            return false;
        }
        Point point = Rs2MiniMap.worldToMinimap(worldPoint);
        return point != null && (disableWalkerUpdate || Rs2MiniMap.isPointInsideMinimap(point));
    }

    static boolean walkRawPathMiniMapToward(List<WorldPoint> rawPath,
                                                    WorldPoint target,
                                                    WorldPoint playerLoc,
                                                    int maxEuclidean) {
        return walkRawPathMiniMapTargetToward(rawPath, target, playerLoc, maxEuclidean, -1) != null;
    }

    static WorldPoint clickMiniMapOrFallback(List<WorldPoint> rawPath,
                                                     WorldPoint target,
                                                     WorldPoint playerLoc,
                                                     int maxEuclidean,
                                                     boolean allowDirectionalFallback) {
        return clickMiniMapOrFallback(rawPath, target, playerLoc, maxEuclidean, allowDirectionalFallback, -1);
    }

    static WorldPoint clickMiniMapOrFallback(List<WorldPoint> rawPath,
                                                     WorldPoint target,
                                                     WorldPoint playerLoc,
                                                     int maxEuclidean,
                                                     boolean allowDirectionalFallback,
                                                     int rawAnchorIndex) {
        long passT0 = System.currentTimeMillis();
        try {
            return clickMiniMapOrFallbackInner(rawPath, target, playerLoc, maxEuclidean,
                    allowDirectionalFallback, rawAnchorIndex);
        } finally {
            WalkPassStats.clickIssueMs.addAndGet(System.currentTimeMillis() - passT0);
        }
    }

    private static WorldPoint clickMiniMapOrFallbackInner(List<WorldPoint> rawPath,
                                                     WorldPoint target,
                                                     WorldPoint playerLoc,
                                                     int maxEuclidean,
                                                     boolean allowDirectionalFallback,
                                                     int rawAnchorIndex) {
        if (target == null || playerLoc == null || target.equals(playerLoc)) {
            return null;
        }
        if (walkMiniMap(target)) {
            return target;
        }
        WorldPoint rawFallback = walkRawPathMiniMapTargetToward(rawPath, target, playerLoc,
                maxEuclidean, rawAnchorIndex);
        if (rawFallback != null) {
            return rawFallback;
        }
        if (allowDirectionalFallback && walkMiniMapToward(target, playerLoc, maxEuclidean)) {
            return target;
        }
        return null;
    }

    static WorldPoint walkRawPathMiniMapTargetToward(List<WorldPoint> rawPath,
                                                             WorldPoint target,
                                                             WorldPoint playerLoc,
                                                             int maxEuclidean,
                                                             int rawAnchorIndex) {
        WorldPoint fallback = findFurthestVisibleKnownRawPathPoint(rawPath, playerLoc,
                maxEuclidean, rawAnchorIndex);
        if (fallback == null || fallback.equals(playerLoc) || fallback.equals(target)) {
            return null;
        }
        if (walkMiniMap(fallback)) {
            log.info("[Walker] Minimap click target {} was outside clip; used route fallback {}", target, fallback);
            return fallback;
        }
        return null;
    }

    static boolean walkMiniMapToward(WorldPoint target, WorldPoint playerLoc, int maxEuclidean) {
        if (target == null || playerLoc == null || target.getPlane() != playerLoc.getPlane()) {
            return false;
        }

        int dx = target.getX() - playerLoc.getX();
        int dy = target.getY() - playerLoc.getY();
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance <= 1) {
            return false;
        }

        if (walkReachableMiniMapToward(target, playerLoc, maxEuclidean)) {
            return true;
        }

        int cappedRadius = Math.max(2, maxEuclidean);
        // The scaled-radius points below are geometric guesses toward an off-clip target. Right
        // after a teleport (or when the target sits behind a wall) that guess can be an unreachable
        // tile far off the route, producing the "random click far from the path" behaviour. Only
        // click a guess that is actually reachable from the player.
        Set<WorldPoint> reachable = Rs2Tile
                .getReachableTilesFromTile(playerLoc, Math.max(2, cappedRadius)).keySet();
        int[] radii = new int[] {cappedRadius, 10, 8, 6, 4};
        for (int radius : radii) {
            if (radius >= distance) {
                continue;
            }

            double scale = radius / distance;
            WorldPoint fallback = new WorldPoint(
                    playerLoc.getX() + (int) Math.round(dx * scale),
                    playerLoc.getY() + (int) Math.round(dy * scale),
                    playerLoc.getPlane());
            if (fallback.equals(playerLoc)) {
                continue;
            }
            if (!reachable.contains(fallback)) {
                continue;
            }
            if (Rs2Walker.walkMiniMap(fallback)) {
                log.info("[Walker] Minimap click target {} was outside clip; used fallback {}", target, fallback);
                return true;
            }
        }

        return false;
    }

    // findFurthestRawPathPointMatching (pure) moved to geometry/WalkerPathGeometry (P1); this game-coupled
    // wrapper supplies the constant forward-search window and the lazy reachable-closest fallback. UNGATED —
    // it is the pure-selection unit the tests exercise; live click paths use the gated variant below.
    static WorldPoint findFurthestRawPathPointMatching(List<WorldPoint> rawPath,
                                                       WorldPoint playerLoc,
                                                       int maxEuclidean,
                                                       int rawAnchorIndex,
                                                       Predicate<WorldPoint> isCandidate) {
        return WalkerPathGeometry.findFurthestRawPathPointMatching(rawPath, playerLoc, maxEuclidean,
                rawAnchorIndex, isCandidate, ROUTE_PROGRESS_FORWARD_SEARCH_TILES,
                () -> getClosestTileIndex(rawPath, playerLoc));
    }

    /**
     * The route crosses from reachable to unreachable at some edge; that edge is impassable in reality,
     * whatever the shipped map says. Learn it so the pathfinder routes around it instead of replanning
     * the same way forever.
     * <p>
     * Refusing the click was always correct, but on its own it is not a recovery: the planner keeps
     * producing the same route, the net keeps refusing it, and the walker oscillates. Seen at Sinclair
     * Mansion, where the shipped map has no walls at all for the building — probed as n/s/e/w all open
     * on every tile the walker kept trying — while the live scene reported 330 blocked edges the static
     * map calls open. Four refusals, no progress, no escape.
     * <p>
     * The first strike blocks the edge for THIS session (see
     * {@code PathfinderConfig#learnBlockedEdge}), so the replan below routes around it immediately;
     * persistence across sessions still needs an independent second strike, which is what stops a
     * transient refusal poisoning the store. learnBlockedEdge returns false for an edge already known,
     * so the replan fires once per edge rather than on every refusal.
     */
    static void learnWalledRouteEdge(List<WorldPoint> rawPath, WorldPoint playerLoc,
                                             Map<WorldPoint, Integer> reachable) {
        WorldPoint[] edge = firstWalledRawEdge(rawPath, playerLoc, reachable,
                CLOSEST_INDEX_REACHABLE_STEP_BUDGET);
        if (edge == null) {
            return;
        }
        // A shut door is not a wall. The catalog already says this edge is crossable BY ACTION, so a
        // refused click across it means the door is closed, not that the way is blocked — and learning
        // it poisons the exact edge the route depends on. Dwarf Cannon showed this: Captain Lawgof's
        // outpost gates ship as transports 15604 and 15605 in both directions, and both were learned as
        // walled at strike 1 of 2 while the quester tried to reach him through the fence. A second
        // independent strike would have persisted them and routed around that outpost permanently.
        //
        // The sibling fix for this ("a shut transport door is not a blocked route step") taught the
        // route-step VALIDATOR the same thing; the learning path was never covered.
        if (Rs2PathApi.hasCatalogTransportEdge(edge[0], edge[1])) {
            WebWalkLog.spInfo("walled_edge_not_learned | {} -> {} — catalog transport, a shut door is not a wall",
                    compactWorldPoint(edge[0]), compactWorldPoint(edge[1]));
            return;
        }
        // The same rule for ORDINARY scene doors, which have no catalog row to hit the guard above.
        // A refused click across a shut door means the door is closed, not that the way is walled —
        // the door pipeline (and its strike-out) owns that edge. Without this, the Tithe Farm run
        // (2026-08-12) learned the lobby door edge as walled for the WHOLE SESSION one second after
        // the strike-out had deliberately scoped its own block to the walk — so the plugin's later
        // seeded walk-in would have found the door unroutable until a client restart.
        if (findDoorNearSegmentTimed(edge[0], edge[1],
                List.of("pay-toll", "pick-lock", "walk-through", "go-through", "open", "pass")) != null) {
            WebWalkLog.spInfo("walled_edge_not_learned | {} -> {} — scene door on the edge, the door pipeline owns it",
                    compactWorldPoint(edge[0]), compactWorldPoint(edge[1]));
            rememberWalledDoorEdge(edge);
            return;
        }
        // ADJACENCY, not just the exact edge. Double gates (Stronghold "Gate of War") are two wall
        // objects: only the primary wing carries the Open action; the slave wing is actionless. A raw
        // route step through the slave wing's line finds no door ON its own segment — the check above
        // passes — and the edge gets learned as walled while the door pipeline is opening the primary
        // wing one tile away. Measured 2026-08-13 14:00: edges (1875,5240)->(1876,5240) (parallel
        // beside the gate) and (1903,5242)->(1904,5243) (diagonal sharing the gate's corner) both
        // learned mid-corridor, each costing a replan. Not learning is always recoverable — the
        // refused click just falls back as before; learning wrongly poisons routing for the session.
        if (sceneDoorAdjacentToEdge(edge[0], edge[1])) {
            WebWalkLog.spInfo("walled_edge_not_learned | {} -> {} — scene door adjacent to the edge (double-gate wing), the door pipeline owns it",
                    compactWorldPoint(edge[0]), compactWorldPoint(edge[1]));
            rememberWalledDoorEdge(edge);
            return;
        }
        // Via the Rs2PathApi wrapper rather than the config directly: it takes the pathfinder mutex,
        // which matters because the replan below runs straight after. Same return contract — true only
        // when the edge was newly blocked for this session.
        if (Rs2PathApi.learnBlockedEdge(edge[0], edge[1], "route-click-walled")) {
            WebWalkLog.spInfo("walled_edge_learned | {} -> {} — replanning around it",
                    compactWorldPoint(edge[0]), compactWorldPoint(edge[1]));
            recalculatePath();
        }
    }

    /** Hands the door-bearing walled edge to recovery so it approaches the door instead of replanning. */
    private static void rememberWalledDoorEdge(WorldPoint[] edge) {
        routeState.walledDoorEdgeFrom = edge[0];
        routeState.walledDoorEdgeTo = edge[1];
        routeState.walledDoorEdgeAtMs = System.currentTimeMillis();
    }

    /**
     * First raw-path step that leaves the player-origin BFS: {@code a} reachable, {@code b} not.
     * <p>
     * Both endpoints must sit inside the BFS budget, or "not reachable" means merely far away and the
     * edge is innocent — the same guard the refusal itself uses.
     * <p>
     * That proximity guard is Chebyshev, and the BFS budget counts STEPS, so on its own it does not
     * mean what it looks like: a tile thirteen tiles away as the crow flies can be thirty steps away
     * around a building, and it is then absent from the BFS for want of budget rather than because
     * anything blocks it. Refusing a click on that evidence is merely conservative; LEARNING a blocked
     * edge from it corrupts routing for the rest of the session.
     * <p>
     * Measured at the Port Sarim / Land's End docks: a click to (2760,3238) was refused as walled and
     * the edge (2759,3230)->(2759,3231) was learned — and nine seconds later the walker was standing on
     * (2760,3238), having simply walked there. So {@code a} must also be strictly INSIDE the frontier:
     * the BFS expands every tile below its budget, so an interior {@code a} whose neighbour {@code b} is
     * still missing proves {@code b} unreachable, whereas an {@code a} sitting AT the budget never had
     * its neighbours enumerated at all and proves nothing.
     */
    static WorldPoint[] firstWalledRawEdge(List<WorldPoint> rawPath, WorldPoint playerLoc,
                                           Map<WorldPoint, Integer> reachable, int stepBudget) {
        if (rawPath == null || rawPath.isEmpty() || playerLoc == null
                || reachable == null || reachable.isEmpty()) {
            return null;
        }
        // Deliberately no getClosestTileIndex here: that reads the scene on the client thread, and this
        // must stay pure so the decision table can cover it. The reachable set already confines the
        // answer to the player's immediate surroundings, so a full scan is both cheap and sufficient.
        final int maxDistance = stepBudget - 2;
        for (int i = 0; i + 1 < rawPath.size(); i++) {
            WorldPoint a = rawPath.get(i);
            WorldPoint b = rawPath.get(i + 1);
            if (a == null || b == null
                    || a.getPlane() != playerLoc.getPlane() || b.getPlane() != playerLoc.getPlane()) {
                continue;
            }
            // Both ends inside the BFS budget, or "unreachable" only means "far" and the edge is
            // innocent. Skip rather than stop: a route may leave and re-enter the budget.
            if (playerLoc.distanceTo2D(a) > maxDistance || playerLoc.distanceTo2D(b) > maxDistance) {
                continue;
            }
            Integer stepsToA = reachable.get(a);
            // At the budget, a's neighbours were never enumerated, so b's absence is ignorance, not a
            // wall. Only an interior a can convict the edge.
            if (stepsToA == null || stepsToA >= stepBudget) {
                continue;
            }
            if (!reachable.containsKey(b)) {
                return new WorldPoint[]{a, b};
            }
        }
        return null;
    }

    /**
     * Selects the next minimap click target from the raw route, gated on collision reachability.
     * <p>
     * Preference order:
     * <ol>
     *   <li>Furthest-forward raw point that is collision-reachable from the player. A point on the
     *       far side of a wall is Euclidean-close but not reachable within the sampled area, so it is
     *       excluded — this is what stops the walker clicking through castle walls / into buildings.</li>
     *   <li>Furthest-forward raw point that is off the loaded scene. Collision cannot be verified for
     *       unloaded tiles, but a minimap click toward a distant route point is still correct, so long
     *       outdoor routes keep flowing.</li>
     * </ol>
     * Returns {@code null} when neither exists; the caller then falls back to wall-distance nudging
     * plus {@link #findReachableRejoinRawPathPoint} rejoin handling.
     */
    static WorldPoint selectRouteClickTarget(List<WorldPoint> rawPath, WorldPoint playerLoc,
                                                     int maxEuclidean, int rawAnchorIndex) {
        long passT0 = System.currentTimeMillis();
        try {
            return selectRouteClickTargetInner(rawPath, playerLoc, maxEuclidean, rawAnchorIndex);
        } finally {
            WalkPassStats.clickSelectMs.addAndGet(System.currentTimeMillis() - passT0);
        }
    }

    private static WorldPoint selectRouteClickTargetInner(List<WorldPoint> rawPath, WorldPoint playerLoc,
                                                     int maxEuclidean, int rawAnchorIndex) {
        if (rawPath == null || rawPath.isEmpty() || playerLoc == null) {
            routeState.lastRouteClickTier = "norawpath";
            return null;
        }
        // Anti-ban: vary HOW FAR ALONG the route we click. Selection otherwise always returns the
        // furthest candidate inside a fixed radius, so every click covers the same tile span — a
        // deterministic signature. Varying the reach is the safe axis: it only changes how far
        // forward we pick, never sideways, so the target stays on the planned route (#20). Lateral
        // tile offsets are the wrong axis and were removed for exactly that reason (#15); lateral
        // randomness belongs inside the tile (click-point jitter), not in tile selection.
        int jitteredReach = routeClickReach(maxEuclidean);
        WorldPoint selected = selectRouteClickTargetAnchored(rawPath, playerLoc, jitteredReach, rawAnchorIndex);
        if (selected == null && jitteredReach < maxEuclidean) {
            // A shortened reach must never be the reason selection fails — that would drop the click
            // onto the caller's off-route wall-nudge clamp. Retry at full reach before giving up.
            selected = selectRouteClickTargetAnchored(rawPath, playerLoc, maxEuclidean, rawAnchorIndex);
        }
        if (selected == null && rawAnchorIndex >= 0) {
            // The smoothed->raw anchor can point past the player's vicinity (stale mapping, sparse
            // smoothing, or a replanned route). The anchored forward scan then breaks immediately on
            // the Euclidean bound and yields nothing for EVERY predicate — which is exactly the
            // sel=none case that dropped route clicks onto the off-route wall-nudge clamp. Retry
            // anchored at the player's own closest raw tile before giving up.
            // Keep the jitter on this path too. The player-anchored retry fires on most first clicks
            // of a route, so using full reach here bypassed the reach variation exactly where it is
            // most visible — measured click distances clustered at 9.0-10.0 instead of spreading.
            selected = selectRouteClickTargetAnchored(rawPath, playerLoc, jitteredReach, -1);
            if (selected == null && jitteredReach < maxEuclidean) {
                selected = selectRouteClickTargetAnchored(rawPath, playerLoc, maxEuclidean, -1);
            }
            if (selected != null) {
                routeState.lastRouteClickTier = routeState.lastRouteClickTier + "@player";
            }
        }
        return selected;
    }

    /**
     * Per-click route reach, jittered below {@code maxEuclidean} so consecutive clicks do not all
     * cover the same tile span.
     * <p>
     * The floor matters: it must stay clear of {@link #INTERIM_CLOSE_TILES} or the interim
     * checkpoint clears almost immediately and the walker re-clicks constantly, producing visible
     * stop-start movement. The ceiling is the caller's reach, which is already tuned to the minimap
     * clip — going above it just produces outside-clip fallbacks.
     */
    static int routeClickReach(int maxEuclidean) {
        int floor = Math.min(ROUTE_CLICK_REACH_MIN_TILES, maxEuclidean);
        if (maxEuclidean <= floor) {
            return maxEuclidean;
        }
        return Rs2Random.betweenInclusive(floor, maxEuclidean);
    }

    static WorldPoint selectRouteClickTargetAnchored(List<WorldPoint> rawPath, WorldPoint playerLoc,
                                                             int maxEuclidean, int rawAnchorIndex) {
        // Click the furthest forward point ON THE RAW ROUTE that is within minimap reach.
        //
        // A minimap click is resolved by the GAME's own pathing, so line of sight is irrelevant to
        // walking: a player clicks past a corner, through a doorway, or around a building and the
        // server routes them there. Requiring straight LOS made the walker advance corner-to-corner,
        // stopping at each one to re-aim — a visible tell, and it bought no correctness. The
        // invariant that actually matters is that the target sits ON the planned route, so wherever
        // the server routes us we still arrive on that route.
        //
        // The off-route click (3176,3428) that started this came from the caller's
        // smoothed-waypoint Euclidean clamp after selection returned null on a stale anchor — not
        // from a lack of line of sight. Pending doors/gates are handled by
        // handlePendingDoorBeforeRouteClick, not by shortening the click.
        WorldPoint forward = findFurthestRawPathPointMatchingGated(rawPath, playerLoc, maxEuclidean,
                rawAnchorIndex, Rs2Walker::isKnownWalkableOrUnloaded);
        if (forward != null && !forward.equals(playerLoc)) {
            routeState.lastRouteClickTier = "route";
            return forward;
        }
        routeState.lastRouteClickTier = "none";
        return null;
    }

    static WorldPoint findFurthestVisibleKnownRawPathPoint(List<WorldPoint> rawPath,
                                                           WorldPoint playerLoc,
                                                           int maxEuclidean) {
        return findFurthestVisibleKnownRawPathPoint(rawPath, playerLoc, maxEuclidean, -1);
    }

    static WorldPoint findFurthestVisibleKnownRawPathPoint(List<WorldPoint> rawPath,
                                                           WorldPoint playerLoc,
                                                           int maxEuclidean,
                                                           int rawAnchorIndex) {
        if (rawPath == null || rawPath.isEmpty() || playerLoc == null) {
            return null;
        }

        return findFurthestRawPathPointMatchingGated(rawPath, playerLoc, maxEuclidean, rawAnchorIndex,
                candidate -> !candidate.equals(playerLoc)
                        && isKnownWalkableOrUnloaded(candidate)
                        && isMiniMapClickable(candidate));
    }

    static boolean shouldIssueActiveRouteIdleNudge() {
        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        long now = System.currentTimeMillis();
        if (playerLoc == null || Rs2Player.isMoving() || Rs2Player.isAnimating() || Rs2Player.isInteracting()
                || Rs2LeaguesTransport.isTeleportInProgress()
                || Rs2LeaguesTransport.isLeaguesAreaTeleportPending(LEAGUES_AREA_PENDING_STALL_MAX_AGE_MS)) {
            routeState.idleNudgeLastObservedLocation = playerLoc;
            routeState.idleNudgeStationarySinceMs = now;
            return false;
        }
        // While door recovery is actively suppressed (unresolved door on the blocked edge, handlers cooling
        // down), the nudge MUST NOT fire: its forward click is not door-aware and can select a tile on the
        // far side of the closed door, which routes the player around the building and off the route. The
        // suppress branch itself walks the player to the door's near side; standing there waiting for the
        // cooldown is the correct behavior, not idleness to nudge out of.
        if (now - routeState.doorRecoverySuppressedAtMs < DOOR_SUPPRESS_NUDGE_HOLDOFF_MS) {
            routeState.idleNudgeLastObservedLocation = playerLoc;
            routeState.idleNudgeStationarySinceMs = now;
            return false;
        }
        if (hasFreshActiveDoorClaim(now)) {
            routeState.idleNudgeLastObservedLocation = playerLoc;
            routeState.idleNudgeStationarySinceMs = now;
            return false;
        }
        if (!playerLoc.equals(routeState.idleNudgeLastObservedLocation)) {
            routeState.idleNudgeLastObservedLocation = playerLoc;
            routeState.idleNudgeStationarySinceMs = now;
            return false;
        }
        if (routeState.idleNudgeStationarySinceMs <= 0L) {
            routeState.idleNudgeStationarySinceMs = now;
            return false;
        }
        return now - routeState.idleNudgeStationarySinceMs >= ACTIVE_ROUTE_IDLE_NUDGE_MS
                && now - routeState.lastActiveRouteIdleNudgeAtMs >= ACTIVE_ROUTE_IDLE_NUDGE_COOLDOWN_MS;
    }

    static boolean tryIssueRouteRecoveryClick(List<WorldPoint> rawPath,
                                                      List<WorldPoint> path,
                                                      WorldPoint target,
                                                      int configuredDistance,
                                                      String logLabel) {
        if (hasFreshActiveDoorClaim(System.currentTimeMillis())) {
            return false;
        }
        return tryIssueRouteMovementClick(rawPath, path, target, configuredDistance, logLabel,
                STALL_RECOVERY_MINIMAP_REACH_EUCLIDEAN, true);
    }

    static boolean tryIssueRouteContinuationClick(List<WorldPoint> rawPath,
                                                          List<WorldPoint> path,
                                                          WorldPoint target,
                                                          int configuredDistance) {
        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        if (playerLoc == null || path == null || path.isEmpty()) {
            return false;
        }
        if (rawPath != null && !rawPath.isEmpty()) {
            int rawIdx = getClosestTileIndex(rawPath, playerLoc);
            if (rawIdx >= 0 && hasUnresolvedDoorLikeObjectNearRawPath(rawPath,
                    rawIdx,
                    playerLoc,
                    UNREACHABLE_DOOR_RECOVERY_BACKTRACK_EDGES,
                    UNREACHABLE_DOOR_RECOVERY_LOOKAHEAD_EDGES,
                    HANDLER_RANGE)) {
                return false;
            }
        }
        int pathIdx = Math.max(0, getClosestTileIndex(path, playerLoc));
        if (hasUpcomingNearbyTransportStep(path, pathIdx, playerLoc,
                POST_TRANSPORT_RAW_SCAN_TRANSPORT_LOOKAHEAD_EDGES,
                POST_TRANSPORT_RAW_SCAN_TRANSPORT_MAX_DIST)) {
            return false;
        }
        if (target != null && TailDecision.suppressTailReclick(Rs2Player.isMoving(),
                playerLoc.distanceTo2D(target), INTERIM_CLOSE_TILES)) {
            return false;
        }
        return tryIssueRouteMovementClick(rawPath, path, target, configuredDistance, "interim close route click",
                normalMinimapReach(), false);
    }

    static String routeMovementClickPhase(String logLabel) {
        if ("stall recovery click".equals(logLabel)) {
            return "stall_recovery_click";
        }
        if ("active route idle nudge".equals(logLabel)) {
            return "active_route_idle_nudge";
        }
        if ("interim close route click".equals(logLabel)) {
            return "interim_close_route_click";
        }
        return "route_movement_click";
    }

    static boolean walkFastCanvasOnScreenOnly(WorldPoint worldPoint, boolean toggleRun) {
        LocalPoint localPoint = localPointForWorld(worldPoint);
        if (localPoint == null || !Rs2Camera.isTileOnScreen(localPoint)) {
            return false;
        }
        Point canvasPoint = Perspective.localToCanvas(
                Microbot.getClient(),
                localPoint,
                Microbot.getClient().getTopLevelWorldView().getPlane());
        int canvasX = canvasPoint != null ? canvasPoint.getX() : -1;
        int canvasY = canvasPoint != null ? canvasPoint.getY() : -1;
        if (canvasX < 0 || canvasY < 0) {
            return false;
        }

        Rs2Player.toggleRunEnergy(toggleRun);
        NewMenuEntry entry = new NewMenuEntry()
                .param0(canvasX)
                .param1(canvasY)
                .type(MenuAction.WALK)
                .identifier(0)
                .itemId(0)
                .option("Walk here");

        Microbot.doInvoke(entry,
                new Rectangle(canvasX, canvasY, Microbot.getClient().getCanvasWidth(), Microbot.getClient().getCanvasHeight()));
        return true;
    }

    static LocalPoint localPointForWorld(WorldPoint worldPoint) {
        if (worldPoint == null) {
            return null;
        }
        LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), worldPoint);
        if (Microbot.getClient().getTopLevelWorldView().isInstance() && localPoint == null) {
            localPoint = Rs2LocalPoint.fromWorldInstance(worldPoint);
        }
        return localPoint;
    }

    static WalkerState tryDirectShortWalk(WorldPoint target,
                                                  int distance,
                                                  List<WorldPoint> rawPath,
                                                  List<WorldPoint> path,
                                                  boolean inInstance) {
        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        if (target == null || playerLoc == null || path == null || path.isEmpty()) {
            return WalkerState.MOVING;
        }

        WorldPoint end = path.get(path.size() - 1);
        int finishTh = tightFinishThreshold(target, end, distance);

        int initialDist = playerLoc.distanceTo(target);
        if (initialDist <= finishTh) {
            setTarget(null, "rs2walker:tryDirectShortWalk:already-within-distance");
            return WalkerState.ARRIVED;
        }

        final int directClickMaxDistance = 13;
        if (playerLoc.getPlane() != target.getPlane() || initialDist > directClickMaxDistance) {
            return WalkerState.MOVING;
        }

        if (end == null || end.getPlane() != target.getPlane() || end.distanceTo(target) > distance) {
            return WalkerState.MOVING;
        }

        if (hasPendingExplicitTransportStepBeforeArrival(rawPath, target, distance)
                || hasPendingExplicitTransportStepBeforeArrival(path, target, distance)) {
            return WalkerState.MOVING;
        }
        if (!inInstance && hasPendingDoorLikeSceneObjectBeforeDirectClick(rawPath, path, playerLoc,
                directClickMaxDistance)) {
            log.debug("[Walker] defer tryDirectShortWalk minimap: route has pending door/gate scene object");
            return WalkerState.MOVING;
        }

        if (!inInstance && !Rs2Tile.isWalkable(end)) {
            return WalkerState.MOVING;
        }
        if (!inInstance && !Rs2Tile.isTileReachable(end)) {
            return WalkerState.MOVING;
        }
        if (!inInstance && localRouteDetoursFromComputedRoute(rawPath, end, directClickMaxDistance)) {
            return WalkerState.MOVING;
        }
        long suppressUntil = routeState.suppressTryDirectShortWalkUntilMs;
        if (suppressUntil != 0L && System.currentTimeMillis() < suppressUntil) {
            log.debug("[Walker] defer tryDirectShortWalk minimap (post door canvas nudge, {}ms window)",
                    POST_DOOR_NUDGE_SUPPRESS_TRY_DIRECT_MS);
            return WalkerState.MOVING;
        }

        boolean routeBacked = rawPath != null && !rawPath.isEmpty();
        int rawAnchorIndex = routeBacked ? rawAnchorIndexForPathPosition(rawPath, path, playerLoc) : -1;
        boolean clicked;
        if (routeBacked) {
            clicked = clickRouteBackedShortWalk(rawPath, end, playerLoc,
                    directClickMaxDistance - 1, rawAnchorIndex);
        } else {
            clicked = walkMiniMap(end);
            if (!clicked) {
                clicked = walkMiniMapToward(end, playerLoc, directClickMaxDistance - 1);
            }
            if (!clicked) {
                clicked = walkFastCanvas(end);
            }
        }
        if (!clicked) {
            return WalkerState.MOVING;
        }

        final WorldPoint before = playerLoc;
        boolean moved = sleepUntil(() -> {
            WorldPoint now = Rs2Player.getWorldLocation();
            return now != null && (now.distanceTo(target) <= finishTh || !now.equals(before) || Rs2Player.isMoving());
        }, 800);

        if (!moved) {
            WorldPoint retryPlayerLoc = Rs2Player.getWorldLocation();
            if (routeBacked && retryPlayerLoc != null) {
                int retryRawAnchorIndex = rawPathForwardAnchorIndex(rawPath, retryPlayerLoc, rawAnchorIndex);
                clicked = clickRouteBackedShortWalk(rawPath, end, retryPlayerLoc,
                        directClickMaxDistance - 1, retryRawAnchorIndex);
            } else {
                clicked = walkFastCanvas(end);
            }
            if (!clicked) {
                return WalkerState.MOVING;
            }
            sleepUntil(() -> {
                WorldPoint now = Rs2Player.getWorldLocation();
                return now != null && (now.distanceTo(target) <= finishTh || !now.equals(before) || Rs2Player.isMoving());
            }, 800);
        }

        WorldPoint afterClick = Rs2Player.getWorldLocation();
        if (afterClick != null && afterClick.distanceTo(target) <= finishTh) {
            setTarget(null, "rs2walker:tryDirectShortWalk:arrived-after-click");
            return WalkerState.ARRIVED;
        }

        sleepUntil(() -> {
            WorldPoint now = Rs2Player.getWorldLocation();
            return now != null && (now.distanceTo(target) <= finishTh || !Rs2Player.isMoving());
        }, 4000);

        WorldPoint afterWalk = Rs2Player.getWorldLocation();
        if (afterWalk != null && afterWalk.distanceTo(target) <= finishTh) {
            setTarget(null, "rs2walker:tryDirectShortWalk:arrived-after-walk");
            return WalkerState.ARRIVED;
        }

        return WalkerState.MOVING;
    }

    static boolean clickRouteBackedShortWalk(List<WorldPoint> rawPath,
                                                     WorldPoint end,
                                                     WorldPoint playerLoc,
                                                     int maxEuclidean,
                                                     int rawAnchorIndex) {
        boolean directTargetInRange = shouldAttemptDirectMinimapTarget(end, playerLoc, maxEuclidean);
        if (directTargetInRange && walkMiniMap(end)) {
            return true;
        }

        // distanceTo() is Chebyshev distance, while the minimap clip is effectively circular.
        // A diagonal endpoint can therefore pass the short-walk gate while being well outside the
        // clip. In that case select a normal forward raw-route point immediately instead of first
        // issuing a predictably rejected endpoint click and reporting the continuation as a fallback.
        WorldPoint routeTarget = findFurthestVisibleKnownRawPathPoint(
                rawPath, playerLoc, maxEuclidean, rawAnchorIndex);
        if (routeTarget != null
                && !routeTarget.equals(playerLoc)
                && !routeTarget.equals(end)
                && walkMiniMap(routeTarget)) {
            if (directTargetInRange) {
                log.debug("[Walker] Direct short-walk target {} was outside the minimap clip; continuing via route {}",
                        end, routeTarget);
            }
            return true;
        }
        return walkFastCanvasOnScreenOnly(end, true);
    }

    static boolean shouldAttemptDirectMinimapTarget(WorldPoint target,
                                                    WorldPoint playerLoc,
                                                    int maxEuclidean) {
        if (target == null || playerLoc == null || maxEuclidean < 0
                || target.getPlane() != playerLoc.getPlane()) {
            return false;
        }
        long dx = (long) target.getX() - playerLoc.getX();
        long dy = (long) target.getY() - playerLoc.getY();
        long radius = maxEuclidean;
        return dx * dx + dy * dy <= radius * radius;
    }

    static boolean hasPendingExplicitTransportStepBeforeArrival(List<WorldPoint> path,
                                                                        WorldPoint target,
                                                                        int distance) {
        return hasPendingRouteStepBeforeArrival(path, target, distance, i -> isCatalogBackedTransportSegment(path, i));
    }

    static boolean hasPendingRouteStepBeforeArrival(List<WorldPoint> path,
                                                    WorldPoint target,
                                                    int distance,
                                                    java.util.function.IntPredicate routeStepAtIndex) {
        if (path == null || path.size() < 2 || routeStepAtIndex == null) {
            return false;
        }

        for (int i = 0; i < path.size() - 1; i++) {
            WorldPoint point = path.get(i);
            if (target != null && point != null && point.distanceTo(target) <= distance) {
                return false;
            }
            if (routeStepAtIndex.test(i)) {
                return true;
            }
        }
        return false;
    }

    static boolean localRouteDetoursFromComputedRoute(List<WorldPoint> rawPath,
                                                              WorldPoint end,
                                                              int directClickMaxDistance) {
        if (rawPath == null || rawPath.size() < 2 || end == null) {
            return false;
        }

        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        if (playerLoc == null || playerLoc.getPlane() != end.getPlane()) {
            return false;
        }

        int rawStart = getClosestTileIndex(rawPath, playerLoc);
        if (rawStart < 0 || rawStart >= rawPath.size() - 1) {
            return false;
        }

        int rawEnd = -1;
        for (int i = rawStart; i < rawPath.size(); i++) {
            WorldPoint point = rawPath.get(i);
            if (point == null || point.getPlane() != end.getPlane()) {
                break;
            }
            if (point.equals(end)) {
                rawEnd = i;
                break;
            }
        }
        if (rawEnd < 0) {
            return false;
        }

        int computedSteps = rawEnd - rawStart;
        if (computedSteps <= 0) {
            return false;
        }

        final int detourSlackTiles = 4;
        int searchDistance = Math.max(directClickMaxDistance * 3, computedSteps + detourSlackTiles + 1);
        Integer localSteps = Rs2Tile.getReachableTilesFromTile(playerLoc, searchDistance).get(end);
        return localSteps == null || localSteps > computedSteps + detourSlackTiles;
    }


    static int interimPreclickTiles() {
        try {
            return interimPreclickTiles(Rs2Player.isRunEnabled());
        } catch (Exception e) {
            return INTERIM_PRECLICK_TILES;
        }
    }

    static int interimPreclickTiles(boolean runEnabled) {
        return runEnabled ? INTERIM_RUN_PRECLICK_TILES : INTERIM_PRECLICK_TILES;
    }

    static boolean shouldClearInterimTarget(WorldPoint interim,
                                            WorldPoint playerLoc,
                                            long setAtMs,
                                            long lastProgressAtMs,
                                            long nowMs) {
        return shouldClearInterimTarget(interim, playerLoc, setAtMs, lastProgressAtMs, nowMs, Integer.MAX_VALUE);
    }

    /**
     * @param bestDistanceSeen closest the player has been to {@code interim} while holding it, or
     *                         {@link Integer#MAX_VALUE} when unknown (then the abandon check is inert).
     */
    static boolean shouldClearInterimTarget(WorldPoint interim,
                                            WorldPoint playerLoc,
                                            long setAtMs,
                                            long lastProgressAtMs,
                                            long nowMs,
                                            int bestDistanceSeen) {
        if (interim == null) {
            return false;
        }
        if (playerLoc == null || playerLoc.getPlane() != interim.getPlane()) {
            return true;
        }
        if (playerLoc.distanceTo2D(interim) <= INTERIM_CLOSE_TILES) {
            return true;
        }
        // An interim the player is walking AWAY from is dead, and nothing else here notices.
        // interimLastProgressAtMs is renewed whenever the ROUTE INDEX advances, so a player making
        // honest progress along the route — in the opposite direction to a checkpoint the route has
        // since moved past — renews the interim every pass and the stale-progress escape can never
        // fire. Measured: interim held at (2973,3350) while the player walked 2961,3349 -> 2960,3343,
        // moving=true throughout, renewed until interimAgeMs=9999 and only then "expired" — with a
        // transport dispatch waiting behind it the whole time.
        if (bestDistanceSeen != Integer.MAX_VALUE
                && playerLoc.distanceTo2D(interim) > bestDistanceSeen + INTERIM_ABANDON_MARGIN_TILES) {
            return true;
        }
        if (lastProgressAtMs > 0L && nowMs - lastProgressAtMs > INTERIM_PROGRESS_TIMEOUT_MS) {
            return true;
        }
        return setAtMs > 0L && nowMs - setAtMs > INTERIM_MAX_AGE_MS;
    }

    static int distanceToInterimOrMax(WorldPoint interim, WorldPoint playerLoc) {
        if (interim == null || playerLoc == null || interim.getPlane() != playerLoc.getPlane()) {
            return Integer.MAX_VALUE;
        }
        return playerLoc.distanceTo2D(interim);
    }

    static void recordInterimDistanceProgress(WorldPoint interim, WorldPoint playerLoc, long nowMs) {
        int distance = distanceToInterimOrMax(interim, playerLoc);
        if (distance < routeState.interimLastDistanceToTarget) {
            routeState.interimLastDistanceToTarget = distance;
            routeState.interimLastProgressAtMs = nowMs;
        }
    }

    static boolean clearInterimTargetIfReachedOrExpired(WorldPoint playerLoc,
                                                                List<WorldPoint> path,
                                                                long nowMs) {
        WorldPoint interim = routeState.interimTargetWp;
        recordInterimDistanceProgress(interim, playerLoc, nowMs);
        if (interim != null && path != null && !path.isEmpty()) {
            int bestIdxNow = getClosestTileIndex(path, playerLoc);
            if (bestIdxNow > routeState.interimLastBestPathIdx) {
                routeState.interimLastBestPathIdx = bestIdxNow;
                routeState.interimLastProgressAtMs = nowMs;
            }
        }
        if (!shouldClearInterimTarget(interim, playerLoc, routeState.interimSetAtMs,
                routeState.interimLastProgressAtMs, nowMs, routeState.interimLastDistanceToTarget)) {
            return false;
        }
        String reason;
        if (playerLoc == null || interim == null || playerLoc.getPlane() != interim.getPlane()) {
            reason = "invalid";
        } else if (playerLoc.distanceTo2D(interim) <= INTERIM_CLOSE_TILES) {
            reason = "close";
        } else if (routeState.interimLastDistanceToTarget != Integer.MAX_VALUE
                && playerLoc.distanceTo2D(interim)
                > routeState.interimLastDistanceToTarget + INTERIM_ABANDON_MARGIN_TILES) {
            reason = "moving-away";
        } else if (routeState.interimLastProgressAtMs > 0L && nowMs - routeState.interimLastProgressAtMs > INTERIM_PROGRESS_TIMEOUT_MS) {
            reason = "stale-progress";
        } else {
            reason = "expired";
        }
        clearInterimTarget(reason);
        return true;
    }

    static boolean shouldYieldForActiveRecoveryInterim(WorldPoint interim,
                                                       WorldPoint playerLoc,
                                                       long setAtMs,
                                                       long lastProgressAtMs,
                                                       long nowMs,
                                                       int bestDistanceSeen,
                                                       long lastMovedAtMs,
                                                       long lastRecoveryClickAtMs,
                                                       boolean playerMoving) {
        if (interim == null) {
            return false;
        }
        if (shouldClearInterimTarget(
                interim, playerLoc, setAtMs, lastProgressAtMs, nowMs, bestDistanceSeen)) {
            return false;
        }
        if (shouldDeferRouteWorkForActiveInterim(interim,
                playerLoc,
                setAtMs,
                lastProgressAtMs,
                nowMs,
                bestDistanceSeen,
                lastMovedAtMs,
                playerMoving,
                INTERIM_CLOSE_TILES)) {
            return true;
        }
        return isRecentEvent(nowMs, lastRecoveryClickAtMs, RECOVERY_MOVEMENT_IN_FLIGHT_MS);
    }

    static boolean shouldYieldForActiveRecoveryInterim(WorldPoint playerLoc,
                                                               List<WorldPoint> path,
                                                               long nowMs) {
        WorldPoint interim = routeState.interimTargetWp;
        if (interim == null) {
            return false;
        }
        recordInterimDistanceProgress(interim, playerLoc, nowMs);
        if (playerLoc != null && path != null && !path.isEmpty()) {
            int bestIdxNow = getClosestTileIndex(path, playerLoc);
            if (bestIdxNow > routeState.interimLastBestPathIdx) {
                routeState.interimLastBestPathIdx = bestIdxNow;
                routeState.interimLastProgressAtMs = nowMs;
            }
        }
        return shouldYieldForActiveRecoveryInterim(interim,
                playerLoc,
                routeState.interimSetAtMs,
                routeState.interimLastProgressAtMs,
                nowMs,
                routeState.interimLastDistanceToTarget,
                routeState.lastMovedTimeMs,
                routeState.lastUnreachableRecoveryClickAtMs,
                Rs2Player.isMoving());
    }

    static boolean shouldYieldForActiveRouteInterim(WorldPoint playerLoc,
                                                            List<WorldPoint> path,
                                                            long nowMs) {
        WorldPoint interim = routeState.interimTargetWp;
        if (interim == null) {
            return false;
        }
        recordInterimDistanceProgress(interim, playerLoc, nowMs);
        if (playerLoc != null && path != null && !path.isEmpty()) {
            int bestIdxNow = getClosestTileIndex(path, playerLoc);
            if (bestIdxNow > routeState.interimLastBestPathIdx) {
                routeState.interimLastBestPathIdx = bestIdxNow;
                routeState.interimLastProgressAtMs = nowMs;
            }
        }
        return shouldDeferRouteWorkForActiveInterim(interim,
                playerLoc,
                routeState.interimSetAtMs,
                routeState.interimLastProgressAtMs,
                nowMs,
                routeState.interimLastDistanceToTarget,
                routeState.lastMovedTimeMs,
                Rs2Player.isMoving(),
                INTERIM_CLOSE_TILES);
    }

    static void clearInterimTarget(String reason) {
        WorldPoint old = routeState.interimTargetWp;
        if (old != null) {
            if ("close".equals(reason)) {
                WebWalkLog.spDebug("interim_clear | reason={} interim={}", reason, compactWorldPoint(old));
            } else {
                WebWalkLog.spInfo("interim_clear | reason={} interim={}", reason, compactWorldPoint(old));
            }
        }
        routeState.interimTargetWp = null;
        routeState.interimTargetIdx = -1;
        routeState.interimSetAtMs = 0L;
        routeState.interimLastProgressAtMs = 0L;
        routeState.interimLastBestPathIdx = -1;
        routeState.interimLastDistanceToTarget = Integer.MAX_VALUE;
        routeState.interimLastRetargetAtMs = 0L;
    }

    /**
     * Ceiling for how long the inventory-only path may be before a "close" target (&le;100
     * chebyshev) loses its right to skip the bank compare. 3x straight-line absorbs honest
     * wall-hugging and indoor zigzags; the 60-tile floor keeps tiny distances from tripping
     * on ordinary detours around buildings. Anything above this is a real detour — a gate the
     * player lacks the item/fare for — and the banked flow must get its chance to fetch it.
     */
    static int shortWalkDirectPathCeiling(int chebyshevDistance) {
        return Math.max(60, chebyshevDistance * 3);
    }
}
