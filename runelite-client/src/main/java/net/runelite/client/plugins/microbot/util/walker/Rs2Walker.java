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
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.CollisionMap;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
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
import org.slf4j.event.Level;
import net.runelite.client.plugins.microbot.util.poh.PohTeleports;
import net.runelite.client.plugins.microbot.util.poh.PohTransport;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.leaguetransport.LeaguesRegion;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.walker.door.Rs2DoorAheadResolver;
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
import java.util.concurrent.Future;
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

/**
 * TODO:
 * 1. fix teleports starting from inside the POH
 * <p>
 * Seasonal handlers ({@link Rs2LeaguesTransport#tryHandleLeaguesAreaTransport}, MoA) must not run on the client thread — same contract as {@link Rs2LeaguesTransport#leaguesTeleport}.
 */
@Slf4j
public class Rs2Walker {
    @Setter
    public static ShortestPathConfig config;
    static int stuckCount = 0;
    static WorldPoint lastPosition;
    static long lastMovedTimeMs = 0;
    /** Rising-edge detection for {@link #checkIfStuck()} animation progress without tile delta. */
    private static boolean prevAnimatingForStuckCheck = false;
    static volatile WorldPoint currentTarget;
    static int nextWalkingDistance = 10;

    /**
     * Active Microbot walk destination ({@code null} when no scripted walk). ShortestPath overlay
     * must not clear {@link Rs2PathApi#getPathfinder()} while this is non-null — otherwise
     * {@link #processWalk} loses the pathfinder while {@link #currentTarget} stays set (pathfinder-still-null EXIT).
     */
    public static WorldPoint getCurrentTarget() {
        return currentTarget;
    }

	/**
	 * Sticky interim minimap click target to avoid destination flapping when the minimap flag
	 * disappears around bends. Once we click a reachable point, keep it until we get close
	 * (<= {@link #INTERIM_CLOSE_TILES}) or progress stalls for {@link #INTERIM_PROGRESS_TIMEOUT_MS}.
	 */
	private static volatile WorldPoint interimTargetWp = null;
	private static volatile int interimTargetIdx = -1;
	private static volatile long interimSetAtMs = 0L;
	private static volatile long interimLastProgressAtMs = 0L;
	private static volatile int interimLastBestPathIdx = -1;
    private static volatile int interimLastDistanceToTarget = Integer.MAX_VALUE;
	private static volatile long interimLastRetargetAtMs = 0L;

	/** Cooldown so partial-segment in-transit {@link #recalculatePath()} does not spam. */
	private static volatile long lastPartialTransRecalcMs = 0L;
	private static final long PARTIAL_TRANS_RECAL_COOLDOWN_MS = 3500L;

	private static final int INTERIM_CLOSE_TILES = 5;

	/**
	 * Floor for the jittered per-click route reach. Deliberately above {@link #INTERIM_CLOSE_TILES}
	 * so a short click cannot land inside the interim-close threshold, which would clear the
	 * checkpoint immediately and cause click thrash.
	 */
	private static final int ROUTE_CLICK_REACH_MIN_TILES = 7;
	private static final int INTERIM_PRECLICK_TILES = 6;
	private static final int INTERIM_RUN_PRECLICK_TILES = 8;
	private static final int INTERIM_MOVING_POLL_MS = 450;
	private static final long INTERIM_PROGRESS_TIMEOUT_MS = 2500L;
	private static final long INTERIM_MAX_AGE_MS = 10_000L;
	private static final long INTERIM_RETARGET_COOLDOWN_MS = 900L;
    private static final long ROUTE_PROGRESS_STALL_GRACE_MS = 4_000L;
    private static final long OFF_PATH_RECALC_RECENT_MOVEMENT_MS = 2_000L;
    private static final long OFF_PATH_RECALC_ROUTE_PROGRESS_GRACE_MS = 3_500L;
    private static final long OFF_PATH_RECALC_MINIMAP_CLICK_GRACE_MS = 2_500L;
    private static final int OFF_PATH_RECALC_DEFER_WAIT_MIN_MS = 250;
    private static final int OFF_PATH_RECALC_DEFER_WAIT_MAX_MS = 1_200;
    private static final long RAW_SCAN_DOOR_FOCUS_MAX_MS = 2200L;
    private static final int RAW_SCAN_DOOR_FOCUS_MAX_ATTEMPTS = 3;
    private static final long DOOR_POST_INTERACT_SETTLE_MS = 900L;
    private static final long DOOR_EDGE_SKIP_COOLDOWN_MS = 700L;
    private static final long RECOVERY_MOVEMENT_IN_FLIGHT_MS = 3_500L;
    private static final long DOOR_TRAVERSAL_RECOVERY_BLOCK_MS = 2_200L;
    private static final long POST_DOOR_NUDGE_RECENT_ATTEMPT_MS = 6_000L;
    private static final int PATHFINDER_DONE_POLL_WAIT_MS = 1200;
    private static final int PATHFINDER_DONE_RETRY_SLEEP_MIN_MS = 120;
    private static final int PATHFINDER_DONE_RETRY_SLEEP_MAX_MS = 220;
    private static final int POST_DOOR_FAST_CLICK_MAX_EUCLIDEAN = 13;
    private static final int POST_DOOR_EDGE_NUDGE_MAX_FROM_PLAYER = 3;
    private static final int POST_DOOR_EDGE_NUDGE_WAIT_MS = 1200;
    private static final int HANDLER_RANGE = 13;
    // Raw/smoothed segments can span several walkable tiles before their transport edge.
    // Do not let the transport handler turn that future edge into a long movement command:
    // normal route clicks own the approach, then the handler takes over beside the origin.
    private static final int RAW_TRANSPORT_DISPATCH_MAX_DISTANCE = 2;
    private static final int QUETZAL_MAP_VISIBLE_WAIT_MS = 7_000;
    private static final int QUETZAL_ICON_READY_WAIT_MS = 3_000;
    private static final int FINAL_ADJACENT_CANVAS_NUDGE_CHEBYSHEV = 1;
    private static final int PATH_ADJ_COMPONENT_LINK_MAX_TILE_GAP = 6;
    private static final int PATH_ADJ_COMPONENT_LINK_MAX_EDGE_GAP = 6;
    private static final int SEGMENT_DOOR_FAMILY_MARK_RADIUS = 2;
    private static final int UNREACHABLE_DOOR_RECOVERY_BACKTRACK_EDGES = 2;
    private static final int UNREACHABLE_DOOR_RECOVERY_LOOKAHEAD_EDGES = 10;
    private static final int STALL_RECOVERY_MINIMAP_REACH_EUCLIDEAN = 10;
    /**
     * A spatially-near smoothed waypoint can be hundreds of raw route steps ahead when a route
     * doubles back around a mountain or fence. Do not treat that future branch as the immediate
     * local blocker. The bounded reachability sample is roughly 39 tiles, so 48 preserves ordinary
     * false-negative recovery while rejecting distant route folds.
     */
    private static final int LOCAL_RECOVERY_RAW_ROUTE_LOOKAHEAD_STEPS = 48;
    private static final int NORMAL_MINIMAP_REACH_EUCLIDEAN = 11;
    private static final int UNREACHABLE_RECOVERY_FORWARD_SCAN_TILES = 24;
    /**
     * Stationary window before an active route issues a recovery nudge.
     * <p>
     * {@link #tryIssueRouteContinuationClick} is one-shot: it only runs on the pass where the interim
     * checkpoint is cleared. If a guard (interacting/animating/door or transport settling, or a
     * pending route object) blocks it on that single pass, nothing retries and the route only resumes
     * via this nudge — so this window is the visible dead stop between hops. Keep it to a few ticks
     * (movement.md #13) rather than the old 2500ms. The nudge already requires the player to be
     * genuinely still (not moving/animating/interacting) on the same tile, and
     * {@link #ACTIVE_ROUTE_IDLE_NUDGE_COOLDOWN_MS} still prevents click spam.
     */
    private static final long ACTIVE_ROUTE_IDLE_NUDGE_MS = 1_200L;
	private static final long ACTIVE_ROUTE_IDLE_NUDGE_COOLDOWN_MS = 2_000L;
	private static final long POST_TRANSPORT_PATH_TMARK_WINDOW_MS = 15_000L;
	private static final int ROUTE_PROGRESS_FORWARD_SEARCH_TILES = 40;

	/**
	 * How long to wait for {@code Rs2PathApi.getPathfinder()} to become non-null at route start.
	 * <p>
	 * The pathfinder is only published <em>after</em> {@code PathfinderConfig.refresh()} completes
	 * (see {@code Rs2WalkerLifecycleRuntime.restartPathfinding}), and a cache-missing refresh has been
	 * measured at 2.4-2.7s. The previous 2000ms cap expired mid-refresh, sending the walker into
	 * {@code recalculatePath()} — which cancels the in-flight work and starts a second refresh, making
	 * the cold start worse instead of recovering from it. Wait comfortably past the observed refresh
	 * cost so recalculation stays a genuine failure path.
	 */
	private static final int PATHFINDER_NULL_WAIT_MS = 6_000;
	private static final long POST_TRANSPORT_OFFPATH_WAIT_BUDGET_MS = 2_500L;
    private static final int POST_TRANSPORT_OFFPATH_WAIT_SLICE_MS = 450;
    private static final int TRANSPORT_DEST_MATCH_CHEBYSHEV = 1;
    private static final int PATH_VARIANCE_TOLERANCE_CHEBYSHEV = 6;
    private static final int POST_TRANSPORT_RAW_SCAN_TRANSPORT_LOOKAHEAD_EDGES = 6;
    private static final int POST_TRANSPORT_RAW_SCAN_TRANSPORT_MAX_DIST = 15;
    private static final long TRANSPORT_POST_INTERACT_SETTLE_MS = 900L;
    private static final long RECENT_TRANSPORT_EDGE_SUPPRESS_MS = 8_000L;
    private static volatile Integer rawScanFocusedDoorIdx = null;
    private static volatile long rawScanFocusedDoorSetAtMs = 0L;
    private static volatile int rawScanFocusedDoorAttempts = 0;
    private static volatile long doorInteractionSettleUntilMs = 0L;
    private static volatile long lastDoorEdgePassSkipAtMs = 0L;
    private static volatile long lastUnreachableRecoveryClickAtMs = 0L;
    private static volatile long walkSessionStartedAtMs = 0L;
    private static volatile boolean firstMovementClickMarked = false;
    private static volatile long lastTransportHandledAtMs = 0L;
    private static volatile WorldPoint lastTransportHandledAtLocation = null;
    private static volatile WorldPoint lastTransportOriginLocation = null;
    private static volatile WorldPoint lastTransportDestinationLocation = null;
    private static volatile WorldPoint idleNudgeLastObservedLocation = null;
    private static volatile long idleNudgeStationarySinceMs = 0L;
    private static volatile long lastActiveRouteIdleNudgeAtMs = 0L;
    private static volatile int routeProgressIdx = -1;
    private static volatile WorldPoint routeProgressTarget = null;
    private static volatile WorldPoint routeProgressPathStart = null;
    private static volatile WorldPoint routeProgressPathEnd = null;
    private static volatile int routeProgressPathSize = -1;
    private static volatile long routeProgressAdvancedAtMs = 0L;
    private static final java.util.Deque<WorldPoint> expectedTransportDestinations = new ArrayDeque<>();
    private static final Set<String> startupPhasesLogged = ConcurrentHashMap.newKeySet();
    private static final Set<Integer> AL_KHARID_TOLL_GATE_OBJECT_IDS = Set.of(
            net.runelite.api.ObjectID.CITY_GATE_2786,
            net.runelite.api.ObjectID.CITY_GATE_2787,
            net.runelite.api.ObjectID.CITY_GATE_2788,
            net.runelite.api.ObjectID.CITY_GATE_2789);
    private static final Set<WorldPoint> AL_KHARID_TOLL_GATE_POINTS = Set.of(
            new WorldPoint(3267, 3227, 0),
            new WorldPoint(3267, 3228, 0),
            new WorldPoint(3268, 3227, 0),
            new WorldPoint(3268, 3228, 0));

    /**
     * Max Chebyshev "radius" for Quetzal / near-destination checks — guards use {@code distanceTo2D &lt; OFFSET}.
     * {@link WorldPoint#distanceTo(WorldPoint)} delegates to {@link WorldPoint#distanceTo2D(WorldPoint)} when both
     * points share a plane, so mixed {@code distanceTo}/{@code distanceTo2D} call sites agree for walking goals.
     * If planes differ, {@code distanceTo} returns {@link Integer#MAX_VALUE} (not {@code distanceTo2D}) — do not use
     * for cross-plane teleport semantics without an explicit plane check.
     * Integer Chebyshev distance: {@code &lt; OFFSET} is the same as {@code &lt;= OFFSET - 1}.
     *
     * @see WorldPoint#distanceTo(WorldPoint)
     */
    static final int OFFSET = 10;

    /** Post-travel poll/timeout for Spirit Tree, Quetzal, glider, fairy ring, and other same-plane landing waits. */
    private static final int TRANSPORT_LANDING_WAIT_POLL_MS = 100;
    private static final int TRANSPORT_LANDING_WAIT_TIMEOUT_MS = 12_000;

    /** Ship / charter / glider — landing predicate uses {@link #isPlayerWithinChebyshevOf} with this exclusive bound. */
    private static final int TRANSPORT_NEAR_LANDING_CHEBYSHEV = 10;

    /** Max wait after ship/NPC/boat dialogue until near destination (must match {@link #sleepUntil} timeout + warn text). */
    private static final int SHIP_NPC_BOAT_LANDING_WAIT_MS = 10_000;

    /** After scene-object transport {@link #handleObject} — landing poll timeout + matching warn (cf. {@link #SHIP_NPC_BOAT_LANDING_WAIT_MS}). */
    private static final int POST_HANDLE_OBJECT_LANDING_WAIT_MS = 5_000;
    private static final int POST_HANDLE_OBJECT_FAILED_SETTLE_MS = 800;

    /** Teleport “already near destination” skip in path loop — same semantics as prior {@code distanceTo2D &lt; 3}. */
    private static final int TELEPORT_NEAR_SKIP_CHEBYSHEV = 3;

    /**
     * When the last walkable path tile is within this Chebyshev distance of the goal, treat the leg as a
     * "short interior" finish (e.g. door → small room): cap {@link #tightFinishThreshold} so we do not
     * return {@link WalkerState#ARRIVED} while still outside the building.
     */
    private static final int TIGHT_PATH_GOAL_GAP = 4;

    // Set this to true, if you want to calculate the path but do not want to walk to it
    static boolean debug = false;

    /** Bounds tail recursion that was previously unbounded {@code processWalk} self-calls. */
    private static final int MAX_PROCESS_WALK_TAIL_ITERATIONS = 64;
    /** Motherlode Mine — the only region containing {@code MOTHERLODE_ROCKFALL_1/2} obstacles. */
    static final int MOTHERLODE_MINE_REGION = 14936;

    /**
     * Verbose walker traces — enable DEBUG logging for {@code net.runelite.client.plugins.microbot}.
     * Uses {@link Microbot#log(Level, String, Object...)} so levels route consistently.
     */
    private static void walkerDiag(String format, Object... args) {
        Microbot.log(Level.DEBUG, "[WalkerDiag] " + format, args);
    }

    /**
     * Compact {@code x,y,p} for logs (world API coords). Similar comma coords exist in test harnesses — keep here until
     * a shared microbot util is justified.
     */
    private static String compactWorldPoint(WorldPoint wp) {
        if (wp == null) {
            return "?";
        }
        return wp.getX() + "," + wp.getY() + ",p" + wp.getPlane();
    }

    private static void markWalkSessionStart(WorldPoint target) {
        walkSessionStartedAtMs = System.currentTimeMillis();
        firstMovementClickMarked = false;
        startupPhasesLogged.clear();
        lastTransportHandledAtLocation = null;
        lastTransportOriginLocation = null;
        lastTransportDestinationLocation = null;
        resetRouteProgress();
        synchronized (expectedTransportDestinations) {
            expectedTransportDestinations.clear();
        }
        WebWalkLog.tmark("walk_start", 0, target, Rs2Player.getWorldLocation(), "target_set");
    }

    private static void clearRecentTransportContext() {
        lastTransportHandledAtMs = 0L;
        lastTransportHandledAtLocation = null;
        lastTransportOriginLocation = null;
        lastTransportDestinationLocation = null;
    }

    private static void markFirstMovementClick(String phase, WorldPoint target, WorldPoint at, String detail) {
        if (firstMovementClickMarked) {
            return;
        }
        long startedAt = walkSessionStartedAtMs;
        if (startedAt <= 0) {
            return;
        }
        firstMovementClickMarked = true;
        WebWalkLog.tmark(phase, System.currentTimeMillis() - startedAt, target, at, detail);
    }

    private static void markStartupPhase(String phase, WorldPoint target, String detail) {
        if (firstMovementClickMarked || !startupPhasesLogged.add(phase)) {
            return;
        }
        long startedAt = walkSessionStartedAtMs;
        if (startedAt <= 0) {
            return;
        }
        WebWalkLog.tmark(phase, System.currentTimeMillis() - startedAt, target, Rs2Player.getWorldLocation(), detail);
    }

    private static void tmarkPostTransport(String phase, WorldPoint target, String detail) {
        long handledAt = lastTransportHandledAtMs;
        if (handledAt <= 0L) {
            return;
        }
        long elapsed = System.currentTimeMillis() - handledAt;
        if (elapsed < 0L || elapsed > POST_TRANSPORT_PATH_TMARK_WINDOW_MS) {
            return;
        }
        WebWalkLog.tmark(phase, elapsed, target, Rs2Player.getWorldLocation(), detail);
    }

    private enum WalkerPhase {
        STARTUP,
        STEADY
    }

    private static final class WalkLoopSnapshot {
        private final WorldPoint playerLoc;
        private final HashMap<WorldPoint, Integer> closestReachableTiles;

        private WalkLoopSnapshot(WorldPoint playerLoc) {
            this.playerLoc = playerLoc;
            this.closestReachableTiles = getClosestIndexReachableTiles(playerLoc);
        }

        private static WalkLoopSnapshot capture() {
            return new WalkLoopSnapshot(Rs2Player.getWorldLocation());
        }

        private int closestTileIndex(List<WorldPoint> path) {
            return getClosestTileIndex(path, playerLoc, closestReachableTiles);
        }
    }

    private interface ObstaclePolicy {
        long segmentDoorTimeoutMs();
        long unreachableDoorTimeoutMs();
        int edgeResolutionWaitTimeoutMs();
        long pathAdjacentProbeTimeoutMs();
        boolean allowBroadRawHandlers();
        boolean allowPathAdjacentProbe();
        boolean allowNearbyFallback();
    }

    private static final class StartupObstaclePolicy implements ObstaclePolicy {
        @Override
        public long segmentDoorTimeoutMs() {
            return 800L;
        }

        @Override
        public long unreachableDoorTimeoutMs() {
            return 800L;
        }

        @Override
        public int edgeResolutionWaitTimeoutMs() {
            return 700;
        }

        @Override
        public long pathAdjacentProbeTimeoutMs() {
            return 700L;
        }

        @Override
        public boolean allowBroadRawHandlers() {
            return false;
        }

        @Override
        public boolean allowPathAdjacentProbe() {
            return false;
        }

        @Override
        public boolean allowNearbyFallback() {
            return false;
        }
    }

    private static final class SteadyObstaclePolicy implements ObstaclePolicy {
        @Override
        public long segmentDoorTimeoutMs() {
            return 1500L;
        }

        @Override
        public long unreachableDoorTimeoutMs() {
            return 1500L;
        }

        @Override
        public int edgeResolutionWaitTimeoutMs() {
            return 1800;
        }

        @Override
        public long pathAdjacentProbeTimeoutMs() {
            return 1500L;
        }

        @Override
        public boolean allowBroadRawHandlers() {
            return true;
        }

        @Override
        public boolean allowPathAdjacentProbe() {
            return true;
        }

        @Override
        public boolean allowNearbyFallback() {
            return true;
        }
    }

    private static final ObstaclePolicy STARTUP_OBSTACLE_POLICY = new StartupObstaclePolicy();
    private static final ObstaclePolicy STEADY_OBSTACLE_POLICY = new SteadyObstaclePolicy();

    private static WalkerPhase currentWalkerPhase() {
        if (firstMovementClickMarked) {
            return WalkerPhase.STEADY;
        }
        return WalkerPhase.STARTUP;
    }

    private static boolean isClientThread() {
        Client client = Microbot.getClient();
        return client != null && client.isClientThread();
    }

    private static int reachedDistanceOrDefault() {
        return config != null ? config.reachedDistance() : 10;
    }

    private static ObstaclePolicy obstaclePolicyForCurrentPhase() {
        return currentWalkerPhase() == WalkerPhase.STARTUP
                ? STARTUP_OBSTACLE_POLICY
                : STEADY_OBSTACLE_POLICY;
    }

    static boolean shouldSkipStartupPreclickSegmentHandlers(boolean startupBeforeFirstClick,
                                                            int segmentIdx,
                                                            int routeStartIdx,
                                                            boolean recentDoorAttemptNearSegment,
                                                            boolean doorSettling,
                                                            boolean recoveryInFlight) {
        if (!startupBeforeFirstClick || routeStartIdx < 0 || segmentIdx < routeStartIdx) {
            return false;
        }
        if (recentDoorAttemptNearSegment || doorSettling || recoveryInFlight) {
            return false;
        }
        return true;
    }

    static boolean shouldRunActiveRouteIdleNudge(boolean idleNudgeDue,
                                                boolean immediateRouteTransportPending) {
        return idleNudgeDue && !immediateRouteTransportPending;
    }

    /**
     * Same-plane Chebyshev distance from player to {@code dest} strictly less than {@code maxChebyshevExclusive}.
     * Requires matching {@link WorldPoint#getPlane()} before using {@link WorldPoint#distanceTo2D} — that method only
     * compares X/Y, so same X/Y on different planes still reads as distance {@code 0} without an explicit plane check.
     */
    private static boolean isPlayerWithinChebyshevOf(WorldPoint dest, int maxChebyshevExclusive) {
        if (dest == null) {
            return false;
        }
        WorldPoint pl = Rs2Player.getWorldLocation();
        return pl != null && pl.getPlane() == dest.getPlane()
                && pl.distanceTo2D(dest) < maxChebyshevExclusive;
    }

    /**
     * Same-plane Chebyshev distance {@code <= maxInclusiveChebyshev} (e.g. adjacent transport uses {@code 0} for same tile).
     */
    private static boolean isPlayerWithinChebyshevInclusive(WorldPoint dest, int maxInclusiveChebyshev) {
        if (dest == null) {
            return false;
        }
        WorldPoint pl = Rs2Player.getWorldLocation();
        return pl != null && pl.getPlane() == dest.getPlane()
                && pl.distanceTo2D(dest) <= maxInclusiveChebyshev;
    }

    /**
     * Caps configured finish distance when the route already ends very close to the marked goal.
     * Without this, a large "Finish distance" (e.g. 5) allows {@link WalkerState#ARRIVED} on the
     * wrong side of a wall/door for small interiors. When {@code dLast &lt; TIGHT_PATH_GOAL_GAP}, cap is {@code 1};
     * when {@code dLast == TIGHT_PATH_GOAL_GAP}, cap is {@code 2} (outdoor micro-walking relief at the gap radius).
     */
    private static int tightFinishThreshold(WorldPoint goal, WorldPoint pathLastWalkable, int configuredChebyshev) {
        int cfg = Math.max(0, configuredChebyshev);
        if (goal == null || pathLastWalkable == null) {
            return cfg;
        }
        if (goal.getPlane() != pathLastWalkable.getPlane()) {
            return cfg;
        }
        int dLast = pathLastWalkable.distanceTo2D(goal);
        if (dLast <= TIGHT_PATH_GOAL_GAP) {
            if (dLast < TIGHT_PATH_GOAL_GAP) {
                return Math.min(cfg, 1);
            }
            return Math.min(cfg, 2);
        }
        return cfg;
    }

    /**
     * After opening a door, if the walk goal is still close, scene-click a random walkable tile near the
     * goal so the next movement is not an immediate minimap path segment (less robotic than
     * door → minimap in the same beat).
     */
    private static final int DOOR_OPEN_CANVAS_NUDGE_MAX_GOAL_DIST = 18;
    private static final int DOOR_OPEN_CANVAS_NUDGE_GOAL_SAMPLE_RADIUS = 3;
    private static final int DOOR_OPEN_CANVAS_NUDGE_MAX_FROM_PLAYER = 15;

    /**
     * After a successful door canvas nudge, {@link #tryDirectShortWalk} is skipped briefly so the next
     * movement beat is not minimap (same-frame minimap after scene click looks robotic).
     */
    private static volatile long suppressTryDirectShortWalkUntilMs = 0L;
    private static final long POST_DOOR_NUDGE_SUPPRESS_TRY_DIRECT_MS = 2200L;

    /** Max wait after scene canvas / recovery clicks until movement stops (avoids minimap churn while in-flight). */
    private static final int POST_SCENE_WALK_IDLE_WAIT_MS_MAX = 10_000;

    /** If phase 1 exits on arrival distance while still moving, wait briefly for idle-only (reduces tail churn). */
    private static final int POST_SCENE_WALK_IDLE_SECOND_PHASE_MS_MAX = 4_000;
    private static final int POST_RECOVERY_MOVEMENT_START_WAIT_MS = 1_200;

    private static void waitUntilIdleAfterSceneWalk(WorldPoint cancelGoal, int timeoutMs) {
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
    private static void waitUntilIdleAfterSceneWalk(WorldPoint cancelGoal, int timeoutMs,
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

    /**
     * Whether any tile within {@code distance} of {@code target} is walkable in the collision map.
     *
     * <p>Pre-flight guard so a destination that does not exist as walkable terrain fails
     * immediately instead of after a full route. A walk to {@code (3087,9720)} — inside the rock
     * east of the Dwarven Mine, with 0 walkable tiles within 6 — spent ~80s covering 100+ tiles to
     * the nearest reachable tile and then reported {@code partial-retries-exhausted}, which reads
     * as a walker fault rather than a bad coordinate.
     *
     * <p><b>Permissive by design.</b> An unmapped region reads as fully blocked (see
     * {@link CollisionMap#hasRegion}), so every ambiguous case returns {@code true} and lets the
     * pathfinder decide. Only a target sitting in mapped, wholly blocked terrain is rejected —
     * otherwise this would refuse instances and any region missing from the collision map.
     */
    static boolean hasWalkableTileWithin(CollisionMap map, WorldPoint target, int distance) {
        if (map == null || target == null) {
            return true;
        }
        if (!map.hasRegion(target.getX(), target.getY())) {
            return true;
        }
        int radius = Math.max(0, distance);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                int x = target.getX() + dx;
                int y = target.getY() + dy;
                if (!map.hasRegion(x, y)) {
                    return true;
                }
                if (!map.isBlocked(x, y, target.getPlane())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Nearest walkable tile to {@code target} within {@code maxRadius}, or null. Diagnostics only. */
    static WorldPoint nearestWalkableTile(CollisionMap map, WorldPoint target, int maxRadius) {
        if (map == null || target == null) {
            return null;
        }
        for (int r = 1; r <= maxRadius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) {
                        continue; // ring only; inner rings already scanned
                    }
                    int x = target.getX() + dx;
                    int y = target.getY() + dy;
                    if (map.hasRegion(x, y) && !map.isBlocked(x, y, target.getPlane())) {
                        return new WorldPoint(x, y, target.getPlane());
                    }
                }
            }
        }
        return null;
    }

    /** Door / gate from main path loop vs {@link #handleNearbyRawPathSceneObjects} raw-path scan (same nudge UX). */
    private static boolean shouldCanvasNudgeAfterDoorLikeExit(String exitReason) {
        if (exitReason == null) {
            return false;
        }
        if (exitReason.startsWith("door-handled")) {
            return true;
        }
        return "raw-path-scene-object-handled".equals(exitReason)
                || "post-click-raw-path-scene-object-handled".equals(exitReason);
    }

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
    static boolean isRouteProgressExit(String exitReason) {
        if (exitReason == null) {
            return false;
        }
        if (exitReason.startsWith("door-handled")) {
            return true;
        }
        switch (exitReason) {
            case "raw-path-scene-object-handled":
            case "post-click-raw-path-scene-object-handled":
            case "current-tile-transport-handled":
            case "post-click-current-tile-transport-handled":
            case "transport-handled":
            case "rockfall-handled":
            case "path-blocker-handled":
            case "interim-in-flight":
            case "recovery-move-in-flight":
            case "route-fold-continuation-click":
                return true;
            default:
                return false;
        }
    }

    private static void maybeCanvasNudgeAfterDoor(WorldPoint goal, int configuredDistance, List<WorldPoint> path) {
        if (goal == null || path == null || path.isEmpty()) {
            return;
        }
        WorldPoint p = Rs2Player.getWorldLocation();
        if (p == null || goal.getPlane() != p.getPlane()) {
            return;
        }
        if (isWalkCancelled(goal)) {
            return;
        }
        WorldPoint pathLast = path.get(path.size() - 1);
        int finishTh = tightFinishThreshold(goal, pathLast, configuredDistance);
        int dGoal = p.distanceTo2D(goal);
        if (dGoal <= finishTh) {
            return;
        }
        // Only nudge with fast-canvas when we are effectively on the final approach.
        // This avoids immediate scene-click jumps after ordinary mid-route door opens.
        if (dGoal > finishTh + FINAL_ADJACENT_CANVAS_NUDGE_CHEBYSHEV) {
            return;
        }
        if (dGoal > DOOR_OPEN_CANVAS_NUDGE_MAX_GOAL_DIST) {
            return;
        }
        LocalPoint goalLocal = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), goal);
        if (goalLocal == null || !Rs2Camera.isTileOnScreen(goalLocal)) {
            return;
        }
        Map<WorldPoint, Integer> around = Rs2Tile.getReachableTilesFromTile(goal, DOOR_OPEN_CANVAS_NUDGE_GOAL_SAMPLE_RADIUS);
        if (around == null || around.isEmpty()) {
            return;
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
            return;
        }
        // candidates non-empty: index range [0, size-1] is valid for betweenInclusive.
        WorldPoint pick = candidates.get(Rs2Random.betweenInclusive(0, candidates.size() - 1));
        if (walkFastCanvas(pick)) {
            log.debug("[Walker] door nudge: canvas -> {} (goal={} dGoal={})", pick, goal, dGoal);
            waitUntilIdleAfterSceneWalk(goal, POST_SCENE_WALK_IDLE_WAIT_MS_MAX, goal, finishTh);
            lastMovedTimeMs = System.currentTimeMillis();
            stuckCount = 0;
        }
    }

    private static void traceProcessWalkExit(String reason, WorldPoint target, int processWalkTail) {
        WorldPoint activeTarget = currentTarget;
        WebWalkLog.exitDetailDebug(
                "trace={} target={} currentTarget={} interim={} stuck={} tailIdx={}/{} intr={} player={}",
                reason,
                target,
                activeTarget,
                interimTargetWp,
                stuckCount,
                processWalkTail,
                MAX_PROCESS_WALK_TAIL_ITERATIONS,
                Thread.currentThread().isInterrupted(),
                Rs2Player.getWorldLocation());
        boolean nullCurrent = activeTarget == null;
        boolean mismatch = target != null && activeTarget != null && !target.equals(activeTarget);
        WebWalkLog.exitWarn(
                reason,
                nullCurrent,
                mismatch,
                Thread.currentThread().isInterrupted(),
                target,
                activeTarget,
                processWalkTail,
                MAX_PROCESS_WALK_TAIL_ITERATIONS,
                Rs2Player.getWorldLocation());
    }

    private static boolean walkCancelledDiag(WorldPoint target, String where, int processWalkTail) {
        if (!isWalkCancelled(target)) {
            return false;
        }
        traceProcessWalkExit("cancel:" + where, target, processWalkTail);
        return true;
    }

    /**
     * Clears walker goal and ShortestPath artifacts. Prefer over {@code setTarget(null)} so logs show why.
     */
    public static void clearWalkingRoute(String reason) {
        setTarget(null, reason != null && !reason.isBlank() ? reason : "unspecified");
    }

    @Getter
    private static volatile String lastRouteClearReason = "";

    @Getter
    private static volatile long lastRouteClearAtMs = 0L;

    private static void logRouteClear(String reason) {
        lastRouteClearReason = reason == null ? "" : reason;
        lastRouteClearAtMs = System.currentTimeMillis();
        if (reason == null || reason.isBlank()) {
            WebWalkLog.routeClearMissingReason(Thread.currentThread().getName());
        } else {
            WebWalkLog.routeClear(reason);
        }
    }

    /** Substrings for game-object names treated like doors (pathing heuristics). */
    private static final String[] DOOR_LIKE_NAME_FRAGMENTS = {
            "door", "gate", "barrier", "stile", "portcullis", "archway", "cattlegate", "fence"
    };

    /** {@code fence} must be whole-word — substring matches {@code defence} ("fence" inside) otherwise. */
    private static final Pattern FENCE_AS_WORD = Pattern.compile("\\bfence\\b", Pattern.CASE_INSENSITIVE);

    /** Lower index = higher priority when multiple actions match (prefix, ASCII lower). */
    private static final List<String> DOOR_ACTION_PRIORITY = List.of(
            "pay-toll", "pick-lock", "walk-through", "go-through", "open", "pass", "enter",
            "push", "climb-over", "climb-through", "squeeze-through", "cross", "force", "exit"
    );

    /** Max age for {@link Rs2LeaguesTransport#isLeaguesAreaTeleportPending(long)} in stall / stuck gates. */
    private static final long LEAGUES_AREA_PENDING_STALL_MAX_AGE_MS = 60_000L;

    @Named("disableWalkerUpdate")
    static boolean disableWalkerUpdate;

    public static boolean disableTeleports = false;

    // Serializes stateful walker entry points so concurrent scripts don't corrupt
    // stuckCount / lastPosition / lastMovedTimeMs / currentTarget / nextWalkingDistance.
    // Reentrant: same-thread dispatch (walkWithState -> walkWithBankedTransportsAndState
    // -> walkWithStateInternal -> recursive processWalk) reacquires freely.
    // setTarget() stays unlocked — cross-thread cancel; volatile currentTarget read in the loop
    // can still see null only when setTarget(null) is intended. recalculatePath no longer nulls
    // currentTarget between restarts (avoids false cancel during sleepUntil).
    private static final ReentrantLock walkerLock = new ReentrantLock();
    /**
     * Optional completion rule owned by the thread currently executing {@link #walkUntil}.
     *
     * <p>The walker is globally serialized, but a thread-local keeps nested helper walks from
     * inheriting a rule intended for a different target. Existing walk methods never install a
     * context and therefore retain their exact behaviour.</p>
     */
    private static final ThreadLocal<WalkCompletionContext> walkCompletionContext = new ThreadLocal<>();

    private static final class WalkCompletionContext {
        private final WorldPoint target;
        private final BooleanSupplier condition;
        private boolean met;
        private boolean failed;

        private WalkCompletionContext(WorldPoint target, BooleanSupplier condition) {
            this.target = target;
            this.condition = condition;
        }
    }

    /**
     * First-seen dedupe keys when both seasonal handlers decline (debug-only): packed destination hex (or {@code nodest}),
     * then truncated {@code displayInfo} plus {@code |h} + hex {@link String#hashCode()} so long-prefix collisions split by dest.
     * At most {@link #SEASONAL_HANDLER_MISS_LOG_CAP} distinct keys ever log — then new misses are silent until JVM restart.
     */
    private static final Set<String> SEASONAL_HANDLER_MISS_LOGGED = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger SEASONAL_HANDLER_MISS_LOGGED_COUNT = new AtomicInteger(0);
    private static final int SEASONAL_HANDLER_MISS_LOG_CAP = 128;
    /**
     * One-shot DEBUG when {@link WorldMapPointManager} is null during route clear (shutdown race).
     * Later races same JVM stay silent — intentional noise cap.
     */
    private static final AtomicBoolean WORLD_MAP_REMOVE_NULL_LOGGED = new AtomicBoolean();

    /** Same package (e.g. unit tests) only — not part of script API. Resets seasonal miss dedupe + world-map remove-null log token. */
    static void clearWalkerDedupeForTesting()
    {
        SEASONAL_HANDLER_MISS_LOGGED.clear();
        SEASONAL_HANDLER_MISS_LOGGED_COUNT.set(0);
        WORLD_MAP_REMOVE_NULL_LOGGED.set(false);
        recentCurrentTileTransportByEdge.clear();
        clearRecentTransportContext();
        resetRouteProgress();
    }

    private static volatile List<SeasonalTransportHandler> seasonalTransportHandlers =
            SeasonalTransportHandlers.defaultHandlerList();

    /**
     * Replaces the seasonal transport handler chain. Non-null, non-empty list; pass
     * {@link SeasonalTransportHandlers#defaultHandlerList()} to restore built-ins.
     * {@link net.runelite.client.plugins.microbot.MicrobotPlugin#startUp} resets defaults each session.
     */
    public static void setSeasonalTransportHandlers(List<SeasonalTransportHandler> handlers)
    {
        if (handlers == null || handlers.isEmpty())
        {
            seasonalTransportHandlers = SeasonalTransportHandlers.defaultHandlerList();
        }
        else
        {
            seasonalTransportHandlers = List.copyOf(handlers);
        }
    }

    public static List<SeasonalTransportHandler> getSeasonalTransportHandlers()
    {
        return seasonalTransportHandlers;
    }

    /**
     * Externally observable counters for walker health checks. The benchmark probe
     * (or any diagnostic script) reads these to decide whether a walk completed
     * without a stall-triggered or off-path-triggered recalculation mid-walk.
     */
    public static final class Telemetry {
        public static final AtomicInteger offPathRecalcCount = new AtomicInteger();
        public static final AtomicInteger offPathRecalcDeferredCount = new AtomicInteger();
        public static final AtomicInteger stallRecalcCount = new AtomicInteger();
        public static final AtomicInteger partialRetryCount = new AtomicInteger();
        public static final AtomicInteger unreachableCount = new AtomicInteger();
        /** Locked-region chat attributed to a recent transport attempt and blacklisted. */
        public static final AtomicInteger leaguesLockAttributedCount = new AtomicInteger();
        /** Locked-region chat with no matching recent attempt or expired attempt snapshot. */
        public static final AtomicInteger leaguesLockStaleCount = new AtomicInteger();
        /** Locked-region chat where region text did not map to {@link LeaguesRegion} (dest-only blacklist path). */
        public static final AtomicInteger leaguesLockParseMissCount = new AtomicInteger();
        /** Neither Leagues Area nor MoA handler accepted a seasonal transport row. */
        public static final AtomicInteger seasonalHandlerMissCount = new AtomicInteger();
        public static final AtomicLong lastEventAtMs = new AtomicLong();
        public static volatile String lastReason = "";

        private static final ConcurrentHashMap<String, AtomicInteger> doorRejectByCause = new ConcurrentHashMap<>();
        private static final AtomicInteger doorRejectSummaryLogSeq = new AtomicInteger(0);
        private static final int DOOR_REJECT_SUMMARY_LOG_INTERVAL = 40;
        private static final ConcurrentHashMap<String, AtomicInteger> offPathDeferredByReason = new ConcurrentHashMap<>();
        private static final AtomicInteger offPathDeferredSummaryLogSeq = new AtomicInteger(0);
        private static final int OFF_PATH_DEFERRED_SUMMARY_LOG_INTERVAL = 20;

        /**
         * Rate-limited debug summary of {@link #doorRejectByCause} tallies (noise control on tight door clusters).
         */
        public static void recordDoorReject(String cause) {
            if (cause == null || cause.isEmpty()) {
                cause = "unknown";
            }
            doorRejectByCause.computeIfAbsent(cause, k -> new AtomicInteger()).incrementAndGet();
            if (Rs2LogRateLimit.everyN(doorRejectSummaryLogSeq, DOOR_REJECT_SUMMARY_LOG_INTERVAL)
                    && log.isDebugEnabled()) {
                log.debug("[WalkerTelemetry] DOOR_REJECT summary={}", doorRejectByCause);
            }
        }

        public static void incrementLeaguesLockAttributed() {
            leaguesLockAttributedCount.incrementAndGet();
        }

        public static void incrementLeaguesLockStale() {
            leaguesLockStaleCount.incrementAndGet();
        }

        public static void incrementLeaguesLockParseMiss() {
            leaguesLockParseMissCount.incrementAndGet();
        }

        public static void incrementSeasonalHandlerMiss() {
            seasonalHandlerMissCount.incrementAndGet();
        }

        public static void recordOffPathRecalc(WorldPoint playerPos, int pathSize) {
            offPathRecalcCount.incrementAndGet();
            lastReason = "off-path";
            lastEventAtMs.set(System.currentTimeMillis());
            log.info("[WalkerTelemetry] OFFPATH_RECALC player={} pathSize={} totalOffPath={} totalStall={}",
                    playerPos, pathSize, offPathRecalcCount.get(), stallRecalcCount.get());
        }

        public static void recordOffPathRecalcDeferred(String reason, WorldPoint playerPos,
                                                       WorldPoint target, int pathSize) {
            if (reason == null || reason.isEmpty()) {
                reason = "unknown";
            }
            offPathRecalcDeferredCount.incrementAndGet();
            offPathDeferredByReason.computeIfAbsent(reason, k -> new AtomicInteger()).incrementAndGet();
            lastReason = "off-path-deferred:" + reason;
            lastEventAtMs.set(System.currentTimeMillis());
            if (Rs2LogRateLimit.everyN(offPathDeferredSummaryLogSeq, OFF_PATH_DEFERRED_SUMMARY_LOG_INTERVAL)
                    && log.isDebugEnabled()) {
                log.debug("[WalkerTelemetry] OFFPATH_RECALC_DEFERRED player={} target={} pathSize={} summary={}",
                        playerPos, target, pathSize, offPathDeferredByReason);
            }
        }

        public static void recordStallRecalc(long sinceMovedMs, WorldPoint playerPos) {
            stallRecalcCount.incrementAndGet();
            lastReason = "stall";
            lastEventAtMs.set(System.currentTimeMillis());
            log.info("[WalkerTelemetry] STALL_RECALC sinceMoved={}ms player={} totalStall={} totalOffPath={}",
                    sinceMovedMs, playerPos, stallRecalcCount.get(), offPathRecalcCount.get());
        }

        public static void recordPartialRetry(int attempt, int finalDist) {
            partialRetryCount.incrementAndGet();
            lastReason = "partial-retry";
            lastEventAtMs.set(System.currentTimeMillis());
            log.info("[WalkerTelemetry] PARTIAL_RETRY attempt={} finalDist={} totalPartial={}",
                    attempt, finalDist, partialRetryCount.get());
        }

        public static void recordUnreachable(String cause, WorldPoint player, WorldPoint target,
                                             WorldPoint pathEndpoint, int pathSize, int distanceThreshold,
                                             Pathfinder pathfinder) {
            unreachableCount.incrementAndGet();
            lastReason = "unreachable:" + cause;
            lastEventAtMs.set(System.currentTimeMillis());
            int distToTarget = (pathEndpoint != null && target != null) ? pathEndpoint.distanceTo(target) : -1;
            Pathfinder.PathfinderStats pfStats = (pathfinder != null) ? pathfinder.getStats() : null;
            String stats = (pfStats != null) ? pfStats.toString() : "null";
            log.warn("[WalkerTelemetry] UNREACHABLE cause={} player={} target={} pathEndpoint={} pathSize={} endpointToTarget={} threshold={} pathfinderStats={} totalUnreachable={}",
                    cause, player, target, pathEndpoint, pathSize, distToTarget, distanceThreshold, stats, unreachableCount.get());
        }

        public static void reset() {
            offPathRecalcCount.set(0);
            offPathRecalcDeferredCount.set(0);
            stallRecalcCount.set(0);
            partialRetryCount.set(0);
            unreachableCount.set(0);
            leaguesLockAttributedCount.set(0);
            leaguesLockStaleCount.set(0);
            leaguesLockParseMissCount.set(0);
            seasonalHandlerMissCount.set(0);
            doorRejectByCause.clear();
            doorRejectSummaryLogSeq.set(0);
            offPathDeferredByReason.clear();
            offPathDeferredSummaryLogSeq.set(0);
            lastEventAtMs.set(0);
            lastReason = "";
            log.info("[WalkerTelemetry] counters reset");
        }

        public static int totalRecalcs() {
            return offPathRecalcCount.get() + stallRecalcCount.get() + partialRetryCount.get();
        }
    }

    // Trapdoor and manhole mappings for open/closed states
    private static final Map<Integer, Integer> OPEN_TO_CLOSED_MAPPINGS = Map.of(
        1581, 1579, // open trapdoor -> closed trapdoor
        882, 881    // open manhole -> closed manhole
    );

    public static boolean walkTo(int x, int y, int plane) {
        return walkTo(x, y, plane, reachedDistanceOrDefault());
    }

    /**
     * @see #walkTo(WorldPoint)
     */
    public static boolean walkTo(int x, int y, int plane, int distance) {
        return walkWithState(new WorldPoint(x, y, plane), distance) == WalkerState.ARRIVED;
    }


    /**
     * {@code null} {@code target} is rejected by {@link #walkWithState(WorldPoint, int)} ({@link WalkerState#EXIT});
     * result is {@code false}, same as any non-arrival outcome.
     */
    public static boolean walkTo(WorldPoint target) {
        return walkWithState(target, reachedDistanceOrDefault()) == WalkerState.ARRIVED;
    }

    /**
     * @see #walkTo(WorldPoint)
     * <p>{@code null} {@code target}: {@link #walkWithState(WorldPoint, int)} returns {@link WalkerState#EXIT}; this method returns {@code false}.
     */
    public static boolean walkTo(WorldPoint target, int distance) {
        return walkWithState(target, distance) == WalkerState.ARRIVED;
    }

    /**
     * Walks toward {@code target} until either the normal arrival distance is reached or
     * {@code completionCondition} becomes true.
     *
     * <p>The condition is polled by the thread that owns the walk at the walker's existing
     * cancellation checkpoints. It must be fast, read-only, and safe to call from a script
     * thread. The caller should perform the actual NPC/object interaction after this method
     * returns; the condition must not click or mutate game state.</p>
     *
     * <p>This is opt-in. Existing {@link #walkTo(WorldPoint, int)} and
     * {@link #walkWithState(WorldPoint, int)} callers are unaffected.</p>
     *
     * @return {@code true} when the destination is reached or the completion condition is met;
     * otherwise {@code false}
     */
    public static boolean walkUntil(WorldPoint target, int distance, BooleanSupplier completionCondition) {
        return walkWithStateUntil(target, distance, completionCondition) == WalkerState.ARRIVED;
    }

    /**
     * State-returning variant of {@link #walkUntil(WorldPoint, int, BooleanSupplier)}.
     * A satisfied completion condition is reported as {@link WalkerState#ARRIVED}, because the
     * caller-defined interaction destination is ready even if the coordinate destination has
     * not yet reached its distance threshold.
     */
    public static WalkerState walkWithStateUntil(
            WorldPoint target,
            int distance,
            BooleanSupplier completionCondition) {
        Objects.requireNonNull(completionCondition, "completionCondition");
        if (target == null) {
            return walkWithState(null, distance);
        }

        WalkCompletionContext previous = walkCompletionContext.get();
        WalkCompletionContext context = new WalkCompletionContext(target, completionCondition);
        walkCompletionContext.set(context);
        try {
            if (evaluateWalkCompletion(context)) {
                return WalkerState.ARRIVED;
            }
            WalkerState result = walkWithState(target, distance);
            return context.met ? WalkerState.ARRIVED : result;
        } finally {
            if (previous == null) {
                walkCompletionContext.remove();
            } else {
                walkCompletionContext.set(previous);
            }
        }
    }

    /**
     * Runs {@code action} while temporarily releasing {@link #walkerLock} for the current thread.
     * Used by long-running Leagues teleport wait so a second {@link #walkWithState} can proceed instead of blocking
     * on {@link java.util.concurrent.locks.ReentrantLock#lockInterruptibly()} for the full teleport timeout.
     * <p>No-op release path when the current thread does not hold the lock (e.g. calibration daemon).
     */
    public static void runWithWalkerLockReleased(Runnable action)
    {
        if (action == null)
        {
            throw new NullPointerException("action");
        }
        if (!walkerLock.isHeldByCurrentThread())
        {
            action.run();
            return;
        }
        int depth = walkerLock.getHoldCount();
        for (int i = 0; i < depth; i++)
        {
            walkerLock.unlock();
        }
        try
        {
            action.run();
        }
        finally
        {
            for (int i = 0; i < depth; i++)
            {
                walkerLock.lock();
            }
        }
    }

    public static WalkerState walkWithState(WorldPoint target, int distance) {
        if (config == null) {
            return WalkerState.EXIT;
        }
        if (target == null) {
            log.warn("[Walker] walk rejected: null target");
            return WalkerState.EXIT;
        }
        if (isClientThread()) {
            log.warn("Please do not call the walker from the main thread");
            return WalkerState.EXIT;
        }
        if (!walkerLock.tryLock()) {
            log.warn("[Walker] concurrent walk request detected, waiting for in-flight walk (held by {}); new target={}",
                    Thread.currentThread().getName(), target);
            try {
                walkerLock.lockInterruptibly();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return WalkerState.EXIT;
            }
        }
        try {
            if (config.walkWithBankedTransports()) {
                return walkWithBankedTransportsAndState(target, distance, false);
            } else {
                return walkWithStateInternal(target, distance);
            }
        } finally {
            walkerLock.unlock();
        }
    }

    /**
     * Like {@link #walkWithState} but bounds how long this thread waits for {@link #walkerLock}.
     * Use when another walk may hold the lock during Leagues UI (see {@link Rs2LeaguesTransport#leaguesTeleport})
     * or when a bounded wait is preferable to {@link java.util.concurrent.locks.ReentrantLock#lockInterruptibly()}.
     *
     * @param lockWaitMs max wait for the lock; {@code 0} = {@link ReentrantLock#tryLock()} only (no blocking)
     * @return {@link WalkerState#EXIT} if the lock is not acquired in time or the thread is interrupted
     */
    public static WalkerState walkWithStateTry(WorldPoint target, int distance, long lockWaitMs)
    {
        if (config == null)
        {
            return WalkerState.EXIT;
        }
        if (target == null)
        {
            log.warn("[Walker] walk rejected: null target");
            return WalkerState.EXIT;
        }
        if (isClientThread())
        {
            log.warn("Please do not call the walker from the main thread");
            return WalkerState.EXIT;
        }
        if (lockWaitMs < 0)
        {
            throw new IllegalArgumentException("lockWaitMs must be >= 0");
        }
        boolean locked;
        try
        {
            if (lockWaitMs == 0)
            {
                locked = walkerLock.tryLock();
            }
            else
            {
                locked = walkerLock.tryLock(lockWaitMs, TimeUnit.MILLISECONDS);
            }
        }
        catch (InterruptedException ie)
        {
            Thread.currentThread().interrupt();
            return WalkerState.EXIT;
        }
        if (!locked)
        {
            log.warn("[Walker] walkWithStateTry: walkerLock not acquired within {}ms (thread={}) target={}",
                    lockWaitMs, Thread.currentThread().getName(), target);
            return WalkerState.EXIT;
        }
        try
        {
            if (config.walkWithBankedTransports())
            {
                return walkWithBankedTransportsAndStateLocked(target, distance, false);
            }
            return walkWithStateInternal(target, distance);
        }
        finally
        {
            walkerLock.unlock();
        }
    }
    /**
     * Replaces the walkTo method
     *
     * @param target goal tile — non-null enforced at entry ({@code Objects.requireNonNull}); {@link #walkWithState} exits on null before delegating (same intent as {@link #walkWithStateTry}).
     * @param distance
     * @return
     */
    private static WalkerState walkWithStateInternal(WorldPoint target, int distance) {
        Objects.requireNonNull(target, "walk target");
        if (isClientThread()) {
            log.warn("Please do not call the walker from the main thread");
            return WalkerState.EXIT;
        }
        WorldPoint playerLocWalk = Rs2Player.getWorldLocation();
        if (playerLocWalk == null) {
            return WalkerState.MOVING;
        }
        int distToTarget = playerLocWalk.distanceTo(target);
        LocalPoint localTarget = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), target);
        boolean walkableCheck = Rs2Tile.isWalkable(localTarget);
        boolean reachableTileCheck = distToTarget <= distance && Rs2Tile.getReachableTilesFromTile(playerLocWalk, distance).containsKey(target);

        if (reachableTileCheck || (!walkableCheck && distToTarget <= distance)) {
            return WalkerState.ARRIVED;
        }

        final Pathfinder pathfinder = Rs2PathApi.getPathfinder();
        if (pathfinder != null && !pathfinder.isDone()) {
            return WalkerState.MOVING;
        }
        boolean hasCurrentPath = pathfinder != null
                && pathfinder.isDone()
                && pathfinder.getTargets().contains(target);
        if (!hasCurrentPath) {
            setTarget(target);
        } else {
            currentTarget = target;
        }
        Rs2PathApi.setReachedDistance(distance);
        stuckCount = 0;
        lastMovedTimeMs = System.currentTimeMillis();
		interimTargetWp = null;
		interimTargetIdx = -1;
		interimSetAtMs = 0L;
        interimLastProgressAtMs = 0L;
        interimLastBestPathIdx = -1;
        interimLastDistanceToTarget = Integer.MAX_VALUE;
        interimLastRetargetAtMs = 0L;
        lastPartialTransRecalcMs = 0L;
        idleNudgeLastObservedLocation = playerLocWalk;
        idleNudgeStationarySinceMs = System.currentTimeMillis();
        lastActiveRouteIdleNudgeAtMs = 0L;

		closeWorldMap();
        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
        }
        markWalkSessionStart(target);
        return processWalk(target, distance);
    }

    /**
     * @param target
     * @return
     */
    public static WalkerState walkWithState(WorldPoint target) {
        return walkWithState(target, reachedDistanceOrDefault());
    }

    /**
     * Core walk method contains all the logic to successfully walk to the destination
     * this contains doors, game objects, teleports, spells etc...
     *
     * @param target
     * @param distance
     */
    private static WalkerState processWalk(WorldPoint target, int distance) {
        // Solve the Draynor basement lever puzzle first if walking to a basement tile, so the
        // door-transports are unlocked before pathfinding. No-op outside the basement. The
        // solver's internal walkTo calls clear currentTarget, so restore it before the real walk.
        if (DraynorBasementSolver.isBasementTarget(target)) {
            DraynorBasementSolver.solveIfNeeded(target);
            // The solver's nested walkTo calls clear currentTarget; restore it so the real walk
            // runs — but not if this walk was interrupted/cancelled while the (blocking) solver
            // ran (the solver itself never interrupts, so an interrupt here is an external cancel).
            if (!Thread.currentThread().isInterrupted()) {
                setTarget(target, "rs2walker:basement-solve-restore");
            }
        }
        return processWalk(target, distance, 0);
    }

    private static WalkerState processWalk(WorldPoint target, int distance, int partialRetries) {
        if (debug) {
            return WalkerState.EXIT;
        }
        // Pre-flight: a destination with no walkable tile within the arrival distance can never be
        // reached, so reject it here rather than after a full route ending at the nearest wall.
        PathfinderConfig preflightConfig = Rs2PathApi.getPathfinderConfig();
        CollisionMap preflightMap = preflightConfig != null ? preflightConfig.getMap() : null;
        if (!hasWalkableTileWithin(preflightMap, target, distance)) {
            WorldPoint nearestWalkable = nearestWalkableTile(preflightMap, target, 48);
            log.warn("[Walker] walk rejected: target {} has no walkable tile within {} in the collision map"
                            + " (nearest walkable {}); check the destination coordinate",
                    target, distance,
                    nearestWalkable != null ? nearestWalkable : "none within 48");
            Telemetry.recordUnreachable("target-not-walkable", Rs2Player.getWorldLocation(),
                    target, nearestWalkable, 0, distance, null);
            setTarget(null, "rs2walker:processWalk:target-not-walkable");
            return WalkerState.UNREACHABLE;
        }
        int partialRetriesWorking = partialRetries;
        // When the last partial retry was spent, so route progress made after it can refill the
        // budget. Without this the counter is monotonic for the entire walk.
        long lastPartialRetryAtMs = 0L;
        WorldPoint lastPartialRetryAtLoc = null;
        WorldPoint lastAttemptedMinimapClick = null;
        boolean lastAttemptedMinimapClickOk = false;
        long lastAttemptedMinimapClickAtMs = 0L;
        long pathfinderPendingSinceMs = 0L;

        Map<WorldPoint, Integer> reachableTilesCache = null;
        WorldPoint reachableTilesCacheOrigin = null;
        for (int processWalkTail = 0; processWalkTail < MAX_PROCESS_WALK_TAIL_ITERATIONS; processWalkTail++) {
        try {
            walkerDiag("tail iteration begin idx=%d/%d target=%s current=%s interim=%s partialRetries=%d",
                    processWalkTail,
                    MAX_PROCESS_WALK_TAIL_ITERATIONS,
                    target,
                    currentTarget,
                    interimTargetWp,
                    partialRetriesWorking);
            if (!Microbot.isLoggedIn()) {
                traceProcessWalkExit("not-logged-in", target, processWalkTail);
                setTarget(null, "rs2walker:processWalk:not-logged-in");
                return WalkerState.EXIT;
            }
            if (walkCancelledDiag(target, "processWalk:entry", processWalkTail)) {
                return WalkerState.EXIT;
            }

            Pathfinder pathfinder = Rs2PathApi.getPathfinder();
            if (pathfinder == null) {
                markStartupPhase("pf_wait_enter", target, "reason=pathfinder_null");
                walkerDiag("pathfinder null; waiting up to %dms", PATHFINDER_NULL_WAIT_MS);
                pathfinder = sleepUntilNotNull(Rs2PathApi::getPathfinder, PATHFINDER_NULL_WAIT_MS);
                if (walkCancelledDiag(target, "processWalk:after-wait-pathfinder", processWalkTail)) {
                    return WalkerState.EXIT;
                }
                if (pathfinder == null) {
                    if (currentTarget != null && currentTarget.equals(target)) {
                        walkerDiag("pathfinder null but target still set; recalculating");
                        recalculatePath();
                        continue;
                    }
                    traceProcessWalkExit("pathfinder-still-null", target, processWalkTail);
                    setTarget(null, "rs2walker:processWalk:pathfinder-still-null");
                    return WalkerState.EXIT;
                }
                markStartupPhase("pf_ready", target, "source=pathfinder_not_null");
            }

            if (!pathfinder.isDone()) {
                markStartupPhase("pf_wait_retry", target, "slice=" + PATHFINDER_DONE_POLL_WAIT_MS);
                if (pathfinderPendingSinceMs == 0L) {
                    pathfinderPendingSinceMs = System.currentTimeMillis();
                }
                walkerDiag("pathfinder not done; short-poll max %dms", PATHFINDER_DONE_POLL_WAIT_MS);
                boolean isDone = Rs2WalkerRuntimeAwaits.awaitPathfinderDone(pathfinder, PATHFINDER_DONE_POLL_WAIT_MS);
                if (walkCancelledDiag(target, "processWalk:after-wait-done", processWalkTail)) {
                    return WalkerState.EXIT;
                }
                if (!isDone) {
                    if (System.currentTimeMillis() - pathfinderPendingSinceMs > 10_000L) {
                        traceProcessWalkExit("pathfinder-timeout-not-done", target, processWalkTail);
                        setTarget(null, "rs2walker:processWalk:pathfinder-timeout-not-done");
                        return WalkerState.EXIT;
                    }
                    // Non-blocking startup: keep polling in short slices so first click can happen
                    // as soon as pathfinder finishes, instead of one long 10s stall.
                    processWalkTail--;
                    sleep(Rs2Random.between(PATHFINDER_DONE_RETRY_SLEEP_MIN_MS, PATHFINDER_DONE_RETRY_SLEEP_MAX_MS));
                    continue;
                }
                markStartupPhase("pf_ready", target, "source=pathfinder_done");
            }
            pathfinderPendingSinceMs = 0L;

            if (Rs2PathApi.getMarker() == null) {
                restoreTargetMarker(target);
            }

            final List<WorldPoint> rawPath = pathfinder.getPath();
            final List<WorldPoint> path = pathfinder.getWalkablePath();
            final int[] smoothedToRaw = mapSmoothedToRaw(path, rawPath);
            int rawSize = rawPath == null ? -1 : rawPath.size();
            int walkSize = path == null ? -1 : path.size();
            markStartupPhase("path_snapshot", target, "raw=" + rawSize + " walk=" + walkSize);
            final WalkLoopSnapshot walkLoop = WalkLoopSnapshot.capture();
            final WorldPoint dst;
            if (path == null || path.isEmpty()) {
                dst = walkLoop.playerLoc;
            } else {
                dst = path.get(path.size()-1);
            }

            boolean partialPath = false;
            if (dst == null || dst.distanceTo(target) > distance) {
                if (path != null && path.size() > 1) {
                    WebWalkLog.partialSegment(dst, dst.distanceTo(target), target, path.size());
                    partialPath = true;
                } else {
                    Telemetry.recordUnreachable("no-walkable-path", walkLoop.playerLoc,
                            target, dst, path == null ? 0 : path.size(), distance, pathfinder);
                    setTarget(null, "rs2walker:processWalk:no-walkable-path");
                    return WalkerState.UNREACHABLE;
                }
            }

            if (path == null || path.isEmpty()) {
                return WalkerState.ARRIVED;
            }

            // Partial segment: before standing on the segment endpoint, refresh routing from current
            // position so the continuation is ready (smooth handoff vs dead stop at segment end).
            if (partialPath) {
                WorldPoint playerPt = walkLoop.playerLoc;
                if (playerPt != null && dst != null) {
                    int distToDstSeg = playerPt.distanceTo2D(dst);
                    int distToGoal = playerPt.distanceTo2D(target);
                    int closestEarly = walkLoop.closestTileIndex(path);
                    int remainingSteps = closestEarly >= 0 ? (path.size() - 1 - closestEarly) : Integer.MAX_VALUE;
                    final int nearSegmentEndTiles = 12;
                    final int nearSegmentEndSteps = 10;
                    boolean approachingSegmentEnd = distToDstSeg <= nearSegmentEndTiles
                            || (remainingSteps != Integer.MAX_VALUE && remainingSteps <= nearSegmentEndSteps);
                    if (approachingSegmentEnd && distToGoal > distance) {
                        long now = System.currentTimeMillis();
                        if (now - lastPartialTransRecalcMs >= PARTIAL_TRANS_RECAL_COOLDOWN_MS) {
                            lastPartialTransRecalcMs = now;
                            WebWalkLog.partialRecalc(
                                    remainingSteps == Integer.MAX_VALUE ? -1 : remainingSteps,
                                    distToDstSeg,
                                    distToGoal,
                                    dst,
                                    target);
                            recalculatePath();
                            continue;
                        }
                    }
                }
            }

            int earlyRouteStartIdx = stabilizeRouteProgressIndex(path, walkLoop.closestTileIndex(path), target, walkLoop.playerLoc);
            boolean immediateRouteTransportPending = hasImmediatePlannedTransportStep(path, earlyRouteStartIdx, walkLoop.playerLoc);

            // Do not clear walk target while a sticky minimap interim is active — breaks
            // isWalkCancelled and forces EXIT while the flag is still carrying the player.
            // Partial paths end at an intermediate waypoint (dst still far from {@code target});
            // clearing here would drop currentTarget before the partial-path retry/recalc branch.
            if (!partialPath && isNear(dst) && interimTargetWp == null) {
                setTarget(null, "rs2walker:processWalk:reached-path-endpoint");
            }

            boolean shouldIssueActiveRouteIdleNudge = shouldIssueActiveRouteIdleNudge();
            long nowTickGraceMs = System.currentTimeMillis();
            if (lastAttemptedMinimapClickOk && lastAttemptedMinimapClickAtMs > 0L
                    && !shouldIssueActiveRouteIdleNudge
                    && nowTickGraceMs - lastAttemptedMinimapClickAtMs < MINIMAP_CLICK_STALL_GRACE_MS) {
                lastMovedTimeMs = nowTickGraceMs;
            }

            checkIfStuck();
            if (walkCancelledDiag(target, "processWalk:after-stuck-check", processWalkTail)) {
                return WalkerState.EXIT;
            }
            if (isStuckTooLong()) {
				// Leagues area teleports can have long animations. Never trigger stall-recalc
				// while the transport is in-flight, or we will interrupt and re-click.
				if (Rs2LeaguesTransport.isTeleportInProgress()
						|| Rs2LeaguesTransport.isLeaguesAreaTeleportPending(LEAGUES_AREA_PENDING_STALL_MAX_AGE_MS))
				{
					return WalkerState.MOVING;
				}
                long sinceMoved = System.currentTimeMillis() - lastMovedTimeMs;
                long threshold = stallThresholdMs();
                Telemetry.recordStallRecalc(sinceMoved, Rs2Player.getWorldLocation());
                WebWalkLog.stallRecalc(sinceMoved, threshold,
                        Rs2Player.isInCombat(), Rs2Player.isAnimating(), Rs2Player.isInteracting());
                if (lastAttemptedMinimapClick != null) {
                    WebWalkLog.stallContextDebug(
                            lastAttemptedMinimapClick,
                            lastAttemptedMinimapClickOk,
                            Math.max(0L, System.currentTimeMillis() - lastAttemptedMinimapClickAtMs),
                            interimTargetWp);
                }
                lastMovedTimeMs = System.currentTimeMillis();
                stuckCount = 0;
                clearInterimTarget("stall-recalc");
                if (immediateRouteTransportPending) {
                    WebWalkLog.spDebug("stall_recovery_suppressed | reason=immediate-route-transport idx={}", earlyRouteStartIdx);
                } else if (!Rs2Player.isMoving() && !Rs2Player.isAnimating() && !Rs2Player.isInteracting()) {
                    setTarget(target);
                    tryIssueRouteRecoveryClick(rawPath, path, target, distance, "stall recovery click");
                    continue;
                } else {
                    setTarget(target);
                    continue;
                }
            }
            if (shouldRunActiveRouteIdleNudge(shouldIssueActiveRouteIdleNudge, immediateRouteTransportPending)) {
                if (tryIssueRouteRecoveryClick(rawPath, path, target, distance, "active route idle nudge")) {
                    lastAttemptedMinimapClick = null;
                    lastAttemptedMinimapClickOk = false;
                    lastAttemptedMinimapClickAtMs = 0L;
                    continue;
                }
                lastActiveRouteIdleNudgeAtMs = System.currentTimeMillis();
            }
            if (stuckCount > 10) {
                var reachable = Rs2Tile.getReachableTilesFromTile(Rs2Player.getWorldLocation(), 5).keySet();
                if (!reachable.isEmpty()) {
                    // Rank sidestep candidates by distance-toward-target so recovery
                    // biases toward the goal instead of wandering. Keep a top-K pool
                    // with weighted randomness so repeat stalls don't lock onto the
                    // same blocked tile.
                    List<WorldPoint> ranked = rankSidestepTilesToward(reachable, target);
                    int poolSize = Math.min(3, ranked.size());
                    walkMiniMap(ranked.get(Rs2Random.between(0, poolSize)));
                    sleepGaussian(1000, 300);
                    stuckCount = 0;
                }
            }

            WorldPoint playerLocForIndex = walkLoop.playerLoc;
            int indexOfStartPoint = stabilizeRouteProgressIndex(path, walkLoop.closestTileIndex(path), target, playerLocForIndex);
            indexOfStartPoint = advanceIndexPastRecentTransportEdge(path, indexOfStartPoint, playerLocForIndex);
            if (indexOfStartPoint == -1) {
                walkerDiag("getClosestTileIndex=-1 pathSize=%d player=%s pathFirst=%s pathLast=%s",
                        path.size(),
                        playerLocForIndex,
                        path.isEmpty() ? null : path.get(0),
                        path.isEmpty() ? null : path.get(path.size() - 1));
                traceProcessWalkExit("closest-index-none", target, processWalkTail);
                setTarget(null, "rs2walker:processWalk:closest-index-none");
                return WalkerState.EXIT;
            }
            primeExpectedTransportDestinations(path, indexOfStartPoint);

            lastPosition = playerLocForIndex;
            boolean clearedInterimTarget = clearInterimTargetIfReachedOrExpired(lastPosition, path, System.currentTimeMillis());
            WorldPoint plImmediate = lastPosition;

            WorldPoint pathLastForImmediate = path.isEmpty() ? null : path.get(path.size() - 1);
            int immediateFinishTh = tightFinishThreshold(target, pathLastForImmediate, distance);
            // Exact tile, or degenerate path (≤1 tile) within `immediateFinishTh` (from `tightFinishThreshold`, same as downstream finish).
            if (plImmediate != null && plImmediate.getPlane() == target.getPlane()) {
                // WorldPoint#distanceTo2D is Chebyshev (max |dx|,|dy|) — same metric as isPlayerWithinChebyshevInclusive.
                int d2dToGoal = plImmediate.distanceTo2D(target);
                if (d2dToGoal <= 0 || (path.size() <= 1 && d2dToGoal <= immediateFinishTh)) {
                    setTarget(null, "rs2walker:processWalk:arrived-immediate");
                    return WalkerState.ARRIVED;
                }
            }
            if (clearedInterimTarget
                    && !Rs2Player.isInteracting()
                    && !Rs2Player.isAnimating()
                    && !isDoorInteractionSettling()
                    && !isTransportInteractionSettling()
                    && tryIssueRouteContinuationClick(rawPath, path, target, distance)) {
                walkerDiag("tail exempt exitReason=interim-close-route-click tailBefore=%d", processWalkTail);
                processWalkTail--;
                continue;
            }

            manageRunEnergy(path.size());

            // Edgeville/ardy wilderness lever warning
            if (Rs2Widget.isWidgetVisible(229, 1)) {
                if (Rs2Dialogue.getDialogueText().equalsIgnoreCase("Warning! The lever will teleport you deep into the Wilderness.")) {
                    log.info("Detected Wilderness lever warning, interacting...");
                    Rs2Dialogue.clickContinue();
                    Rs2Dialogue.sleepUntilHasQuestion("Are you sure you wish to pull it?");
                    Rs2Dialogue.clickOption("Yes, I'm brave.");
                    sleep(1200, 2400);
                }
            }

            // entering desert warning
            if (Rs2Widget.isWidgetVisible(565, 20) && Rs2Widget.clickWidget(565, 20)) {
                sleepUntil(() -> {
                    Widget checkBoxWidget = Rs2Widget.getWidget(565, 20);
                    if (checkBoxWidget == null) return false;
                    return checkBoxWidget.getSpriteId() != 941;
                });
                Rs2Widget.clickWidget(565, 17);
            }

            // entering down ladder strong hold of security
            if (Rs2Widget.isWidgetVisible(579, 20) && Rs2Widget.clickWidget(579, 20)) {
                sleepUntil(() -> {
                    Widget checkBoxWidget = Rs2Widget.getWidget(579, 20);
                    if (checkBoxWidget == null) return false;
                    return checkBoxWidget.getSpriteId() != 941;
                });
                Rs2Widget.clickWidget(579, 17);
            }


            if (Rs2Widget.enterWilderness()) {
                sleepUntil(Rs2Player::isAnimating);
            }

            boolean doorOrTransportResult = false;
            boolean inInstance = Microbot.getClient().getTopLevelWorldView().isInstance();
            String exitReason = "end-of-path";
            Map<String, WorldPoint> doorEdgesAttemptedThisTail = new HashMap<>();
            ObstaclePolicy startupPolicy = obstaclePolicyForCurrentPhase();

            WorldPoint activeInterimPlayer = Rs2Player.getWorldLocation();
            long activeInterimNowMs = System.currentTimeMillis();
            if (!Rs2Player.isInteracting()
                    && !Rs2Player.isAnimating()
                    && !isDoorInteractionSettling()
                    && !isTransportInteractionSettling()
                    && (target == null
                    || activeInterimPlayer == null
                    || activeInterimPlayer.distanceTo(target) > immediateFinishTh)
                    && shouldYieldForActiveRouteInterim(activeInterimPlayer, path, activeInterimNowMs)) {
                exitReason = "interim-in-flight";
                WebWalkLog.earlyExit(exitReason,
                        activeInterimPlayer,
                        target,
                        path.get(path.size() - 1),
                        indexOfStartPoint,
                        path.size());
                walkerDiag("tail exempt exitReason=%s tailBefore=%d early=true interim=%s",
                        exitReason,
                        processWalkTail,
                        interimTargetWp);
                processWalkTail--;
                continue;
            }

            boolean postTransportWindow = lastTransportHandledAtMs > 0
                    && System.currentTimeMillis() - lastTransportHandledAtMs <= POST_TRANSPORT_PATH_TMARK_WINDOW_MS;
            boolean allowRawSceneScan = startupPolicy.allowBroadRawHandlers() && rawPath != null && path != null;
            int rawScanTransportLookaheadStartIdx = postTransportWindow
                    ? Math.min(path.size() - 1, Math.max(0, indexOfStartPoint + 1))
                    : indexOfStartPoint;
            if (allowRawSceneScan && isTransportInteractionSettling()) {
                allowRawSceneScan = false;
                tmarkPostTransport("post_transport_raw_scene_scan_skip", target,
                        "reason=transport_settling");
            }
            if (allowRawSceneScan && postTransportWindow
                    && !hasUpcomingNearbyTransportStep(path, rawScanTransportLookaheadStartIdx, Rs2Player.getWorldLocation(),
                    POST_TRANSPORT_RAW_SCAN_TRANSPORT_LOOKAHEAD_EDGES, POST_TRANSPORT_RAW_SCAN_TRANSPORT_MAX_DIST)) {
                allowRawSceneScan = false;
                tmarkPostTransport("post_transport_raw_scene_scan_skip", target,
                        "reason=no_nearby_planned_transport");
            }
            long rawSceneStartAt = System.currentTimeMillis();
            // After a handled transport, let the main path loop dispatch the next planned transport.
            // Broad raw transport scans can enter long false-negative waits before returning handled=false.
            boolean rawSceneHandled = allowRawSceneScan
                    && handleNearbyRawPathSceneObjects(rawPath, HANDLER_RANGE, target, !postTransportWindow);
            tmarkPostTransport("post_transport_raw_scene_scan", target,
                    "handled=" + rawSceneHandled + " ms=" + (System.currentTimeMillis() - rawSceneStartAt));
            if (rawSceneHandled) {
                doorOrTransportResult = true;
                exitReason = "raw-path-scene-object-handled";
            }

            long currentTileTransportStartAt = System.currentTimeMillis();
            boolean currentTileTransportHandled = !doorOrTransportResult
                    && startupPolicy.allowBroadRawHandlers()
                    && handleCurrentTileTransportTowardPath(rawPath, path, target);
            tmarkPostTransport("post_transport_current_tile_transport", target,
                    "handled=" + currentTileTransportHandled + " ms=" + (System.currentTimeMillis() - currentTileTransportStartAt));
            if (currentTileTransportHandled) {
                doorOrTransportResult = true;
                exitReason = "current-tile-transport-handled";
            }

            if (!doorOrTransportResult) {
                WalkerState directShortWalk = tryDirectShortWalk(target, distance, rawPath, path, inInstance);
                if (directShortWalk != WalkerState.MOVING) {
                    return directShortWalk;
                }
            }

            WorldPoint currentPlayerLoc = Rs2Player.getWorldLocation();
            reachableTilesCache = Rs2Tile.getReachableTilesFromTile(currentPlayerLoc, HANDLER_RANGE * 3);
            reachableTilesCacheOrigin = currentPlayerLoc;
            final int currentPlayerPlane = currentPlayerLoc != null ? currentPlayerLoc.getPlane() : -1;

            for (int i = indexOfStartPoint; !doorOrTransportResult && i < path.size(); i++) {
                WorldPoint currentWorldPoint = path.get(i);
                if (currentWorldPoint.getPlane() != currentPlayerPlane) {
                    continue;
                }
                if (walkCancelledDiag(target, "processWalk:path-loop", processWalkTail)) {
                    return WalkerState.EXIT;
                }

                if (Rs2PathApi.getMarker() == null) {
                    restoreTargetMarker(target);
                }
                // Marker is a UI/overlay artifact (ShortestPath plugin). Walking must not depend
                // on its presence; scripts can clear it mid-walk.
                ObstaclePolicy obstaclePolicy = obstaclePolicyForCurrentPhase();

                boolean recentTransportWindow = lastTransportHandledAtMs > 0
                        && System.currentTimeMillis() - lastTransportHandledAtMs <= POST_TRANSPORT_PATH_TMARK_WINDOW_MS;
                WorldPoint playerForPathCheck = Rs2Player.getWorldLocation();
                if (isTransportInteractionSettling()) {
                    tmarkPostTransport("post_transport_settling_yield", target,
                            "at=" + compactWorldPoint(playerForPathCheck));
                    exitReason = "transport-settling-yield";
                    break;
                }
                boolean nearPath = isNearPath();
                boolean nearPathByVariance = !nearPath && isNearPathByVariance(path, playerForPathCheck);
                if (recentTransportWindow && !nearPath) {
                    WebWalkLog.tmark("post_transport_nearpath_gate",
                            System.currentTimeMillis() - lastTransportHandledAtMs,
                            target,
                            playerForPathCheck,
                            "nearPath=false variance=" + nearPathByVariance);
                }
                if (!nearPath && !recentTransportWindow && !nearPathByVariance) {
                    // Avoid mid-walk recalculation while recent clicks, route progress, or busy state
                    // indicate the player may still be advancing. isStuckTooLong() will trigger a
                    // real recovery path once progress actually halts.
                    String deferReason = currentOffPathRecalcDeferralReason(lastAttemptedMinimapClickAtMs);
                    if (deferReason != null) {
                        Telemetry.recordOffPathRecalcDeferred(deferReason, playerForPathCheck, target, path.size());
                        if (lastTransportHandledAtMs > 0
                                && System.currentTimeMillis() - lastTransportHandledAtMs <= POST_TRANSPORT_PATH_TMARK_WINDOW_MS) {
                            WebWalkLog.tmark("post_transport_offpath_moving_yield",
                                    System.currentTimeMillis() - lastTransportHandledAtMs,
                                    target,
                                    playerForPathCheck,
                                    "defer=" + deferReason);
                        }
                        exitReason = "off-path-deferred:" + deferReason;
                        break;
                    }
                    Telemetry.recordOffPathRecalc(Rs2Player.getWorldLocation(), path.size());
                    WebWalkLog.recalc("no_longer_near_path");
                    if (config.cancelInstead()) {
                        setTarget(null, "rs2walker:processWalk:off-path-cancel-instead");
                    } else {
                        recalculatePath();
                    }
                    exitReason = "not-near-path";
                    break;
                }
                if (!nearPath && recentTransportWindow) {
                    walkerDiag("post-transport near-path bypass at=%s target=%s", playerForPathCheck, target);
                } else if (nearPathByVariance) {
                    walkerDiag("near-path variance bypass at=%s target=%s tolerance=%d",
                            playerForPathCheck, target, PATH_VARIANCE_TOLERANCE_CHEBYSHEV);
                }

                // Gate scene-object handlers to segments near the player. Doors/rockfalls/transports
                // can only be interacted with when the object is in the loaded scene (near the player),
                // and these calls do scene-object scans that add up across 100+ segment paths.
                WorldPoint playerNearSeg = Rs2Player.getWorldLocation();
                if (playerNearSeg == null) {
                    exitReason = "player-location-null";
                    break;
                }
                int segDistance = currentWorldPoint.distanceTo2D(playerNearSeg);
                if (segDistance <= HANDLER_RANGE) {
                    boolean upcomingNearbyTransport = hasUpcomingNearbyTransportStep(path, i, playerNearSeg,
                            POST_TRANSPORT_RAW_SCAN_TRANSPORT_LOOKAHEAD_EDGES,
                            POST_TRANSPORT_RAW_SCAN_TRANSPORT_MAX_DIST);
                    boolean startupBeforeFirstClick = currentWalkerPhase() == WalkerPhase.STARTUP;
                    boolean immediateSegmentTransportStep = hasImmediatePlannedTransportStep(path, i, playerNearSeg);
                    boolean recentDoorAttemptNearSegment = hasRecentDoorAttemptNearIndex(path, i);
                    boolean skipPostTransportSegmentHandlers = recentTransportWindow
                            && !upcomingNearbyTransport
                            && !recentDoorAttemptNearSegment
                            && !isDoorInteractionSettling()
                            && !isRecoveryMovementInFlight()
                            && reachableTilesCache.containsKey(currentWorldPoint);
                    boolean skipStartupPreclickSegmentHandlers = !immediateSegmentTransportStep
                            && shouldSkipStartupPreclickSegmentHandlers(
                            startupBeforeFirstClick,
                            i,
                            indexOfStartPoint,
                            recentDoorAttemptNearSegment,
                            isDoorInteractionSettling(),
                            isRecoveryMovementInFlight());
                    if (skipPostTransportSegmentHandlers || skipStartupPreclickSegmentHandlers) {
                        if (skipStartupPreclickSegmentHandlers) {
                            markStartupPhase("preclick_segment_handler_skip", target,
                                    "i=" + i + " reason=startup_before_first_click");
                        }
                        tmarkPostTransport("post_transport_segment_handler_skip",
                                target,
                                "i=" + i + " reason=" + (skipPostTransportSegmentHandlers
                                        ? "no_nearby_planned_transport"
                                        : "startup_before_first_click"));
                    } else {
                    long segmentHandlerStartAt = System.currentTimeMillis();
                    int rawI = (i < smoothedToRaw.length) ? smoothedToRaw[i] : 0;
                    int rawEnd = rawEndForSmoothedIndex(i, smoothedToRaw, rawPath, path);
                    boolean startupImmediateTransportOnly = startupBeforeFirstClick && immediateSegmentTransportStep;
                    if (!startupImmediateTransportOnly
                            && !Rs2Player.isMoving() && !isDoorInteractionSettling() && !isRecoveryMovementInFlight()) {
                        doorOrTransportResult = handleDoorsInRawSegment(rawPath, rawI, rawEnd,
                                obstaclePolicy.segmentDoorTimeoutMs(), doorEdgesAttemptedThisTail,
                                reachableTilesCache);
                    }
                    if (doorOrTransportResult) {
                        tmarkPostTransport("post_transport_segment_handler", target,
                                "stage=door handled=true i=" + i + " ms=" + (System.currentTimeMillis() - segmentHandlerStartAt));
                        exitReason = "door-handled";
                        break;
                    }

                    // Chain step 2: path-adjacent probes after exact segment-door attempt.
                    boolean allowPathAdjacentProbe = !recentTransportWindow
                            || upcomingNearbyTransport
                            || recentDoorAttemptNearSegment;
                    if (!startupImmediateTransportOnly
                            && !Rs2Player.isMoving() && obstaclePolicy.allowPathAdjacentProbe()
                            && allowPathAdjacentProbe) {
                        if (tryHandleBlockingPathObjectsWithTimeout(rawPath, rawI, 5, 10,
                                obstaclePolicy.pathAdjacentProbeTimeoutMs(), doorEdgesAttemptedThisTail)) {
                            tmarkPostTransport("post_transport_segment_handler", target,
                                    "stage=path_adj handled=true i=" + i + " ms=" + (System.currentTimeMillis() - segmentHandlerStartAt));
                            exitReason = "path-blocker-handled";
                            break;
                        }
                    }

                    if (!startupImmediateTransportOnly) {
                        doorOrTransportResult = handleRockfallInRawSegment(rawPath, rawI, rawEnd,
                                reachableTilesCache);
                    }
                    if (doorOrTransportResult) {
                        tmarkPostTransport("post_transport_segment_handler", target,
                                "stage=rockfall handled=true i=" + i + " ms=" + (System.currentTimeMillis() - segmentHandlerStartAt));
                        exitReason = "rockfall-handled";
                        break;
                    }

                    boolean allowSegmentTransportScan = !recentTransportWindow
                            || upcomingNearbyTransport
                            || immediateSegmentTransportStep;
                    if ((PohTeleports.isInHouse() || !inInstance) && allowSegmentTransportScan) {
                        doorOrTransportResult = handleTransportsInRawSegment(rawPath, rawI, rawEnd);
                    }

                    if (doorOrTransportResult) {
                        tmarkPostTransport("post_transport_segment_handler", target,
                                "stage=transport handled=true i=" + i + " ms=" + (System.currentTimeMillis() - segmentHandlerStartAt));
                        exitReason = "transport-handled";
                        break;
                    }
                    tmarkPostTransport("post_transport_segment_handler", target,
                            "stage=none handled=false i=" + i + " ms=" + (System.currentTimeMillis() - segmentHandlerStartAt));
                    }
                }

                boolean tileReachable = reachableTilesCache.containsKey(currentWorldPoint);
                if (!tileReachable && !inInstance) {
                    WorldPoint playerLoc = Rs2Player.getWorldLocation();
                    if (playerLoc != null) {
                        int unreachableDist = currentWorldPoint.distanceTo2D(playerLoc);
                        if (unreachableDist <= HANDLER_RANGE + 2) {
                            reachableTilesCache = Rs2Tile.getReachableTilesFromTile(playerLoc, HANDLER_RANGE + 5);
                            reachableTilesCacheOrigin = playerLoc;
                            tileReachable = reachableTilesCache.containsKey(currentWorldPoint);
                            if (tileReachable) {
                                log.debug("[Walker] tile {} reachable after cache refresh from {}", currentWorldPoint, playerLoc);
                            }
                        }
                    }
                }
                if (!tileReachable && !inInstance) {
                    WorldPoint playerLoc = Rs2Player.getWorldLocation();
                    if (playerLoc != null) {
                        int unreachableDist = currentWorldPoint.distanceTo2D(playerLoc);
                        if (unreachableDist <= HANDLER_RANGE + 2) {
                            boolean candidateOnCurrentRouteFrontier = isLocalRecoveryCandidateOnForwardRoute(
                                    rawPath,
                                    smoothedToRaw,
                                    indexOfStartPoint,
                                    i,
                                    LOCAL_RECOVERY_RAW_ROUTE_LOOKAHEAD_STEPS);
                            if (!candidateOnCurrentRouteFrontier) {
                                log.info("[Walker] spatially-near future route branch ignored for local recovery: "
                                                + "tile={} idx={}/{} routeStart={} player={}",
                                        currentWorldPoint, i, path.size(), indexOfStartPoint, playerLoc);
                                if (tryIssueRouteContinuationClick(rawPath, path, target, distance)) {
                                    exitReason = "route-fold-continuation-click";
                                } else {
                                    exitReason = "route-fold-continuation-pending";
                                }
                                break;
                            }
                            log.info("[Walker] local reachability miss near player; checking blockers/recovery: tile={} idx={}/{} player={} target={}",
                                    currentWorldPoint, i, path.size(), playerLoc, target);

                            int edgeIdx = Math.max(indexOfStartPoint, i - 1);
                            int rawEdgeStart = (edgeIdx < smoothedToRaw.length) ? smoothedToRaw[edgeIdx] : 0;
                            int rawEdgeEnd = (i < smoothedToRaw.length) ? smoothedToRaw[i] + 1 : rawPath.size();
                            WorldPoint edgeFrom = rawEdgeStart >= 0 && rawEdgeStart < rawPath.size() ? rawPath.get(rawEdgeStart) : null;
                            WorldPoint edgeTo = rawEdgeEnd - 1 >= 0 && rawEdgeEnd - 1 < rawPath.size() ? rawPath.get(rawEdgeEnd - 1) : null;
                            if (hasRecentDoorAttemptOnEdge(edgeFrom, edgeTo)) {
                                boolean resolvedAfterWait = waitForDoorEdgeResolution(edgeFrom, edgeTo,
                                        obstaclePolicy.edgeResolutionWaitTimeoutMs());
                                if (resolvedAfterWait && tryPostDoorFastMinimapClick(path, edgeIdx, playerLoc, target)) {
                                    exitReason = "door-edge-resolved-fast-click";
                                } else {
                                    exitReason = resolvedAfterWait ? "door-edge-resolved-after-wait" : "door-edge-waiting-retry";
                                }
                                break;
                            }
                            if (hasRecentDoorAttemptNearIndex(rawPath, rawEdgeStart)) {
                                boolean resolvedAfterNearbyWait = waitForRecentDoorEdgeResolutionNearIndex(rawPath, rawEdgeStart,
                                        obstaclePolicy.edgeResolutionWaitTimeoutMs());
                                WorldPoint afterNearbyWait = Rs2Player.getWorldLocation();
                                boolean progressedAfterNearbyWait = afterNearbyWait != null
                                        && !afterNearbyWait.equals(playerLoc);
                                if (resolvedAfterNearbyWait && progressedAfterNearbyWait) {
                                    if (tryPostDoorFastMinimapClick(path, edgeIdx, afterNearbyWait, target)) {
                                        exitReason = "door-edge-resolved-fast-click";
                                    } else {
                                        exitReason = "door-edge-resolved-after-nearby-wait";
                                    }
                                    break;
                                }
                                if (!resolvedAfterNearbyWait) {
                                    exitReason = "door-edge-nearby-waiting-retry";
                                    break;
                                }
                            }
                            boolean gateDoorInteraction = isDoorInteractionSettling() || isDoorEdgePassSkipCoolingDown();
                            long recentDoorAgeMs = recentDoorAttemptAgeNearIndex(rawPath, rawEdgeStart);
                            boolean pendingDoorTraversal = recentDoorAgeMs >= 0
                                    && recentDoorAgeMs <= DOOR_TRAVERSAL_RECOVERY_BLOCK_MS
                                    && !Rs2Player.isMoving();
                            if (gateDoorInteraction) {
                                // Avoid any follow-up door probing right after an interaction;
                                // resolver is still settling and re-probes can loop.
                                exitReason = "door-settling-yield";
                                break;
                            }
                            if (pendingDoorTraversal) {
                                // Keep one-shot behavior after door open: let traversal finish
                                // before issuing fallback path-adj/recovery actions.
                                exitReason = "door-traversal-pending-yield";
                                break;
                            }
                            if (shouldYieldForActiveRecoveryInterim(playerLoc, path, System.currentTimeMillis())) {
                                exitReason = "interim-in-flight";
                                break;
                            }
                            if (tryRecentDoorAttemptEdgeNudge(playerLoc, target)) {
                                exitReason = "recent-door-edge-nudge";
                                break;
                            }
                            if (handlePendingDoorNearRawPath(rawPath, obstaclePolicy.unreachableDoorTimeoutMs(),
                                    doorEdgesAttemptedThisTail, playerLoc, 2, 14)) {
                                exitReason = "door-handled-local-reachability-raw-scan";
                                break;
                            }
                            if (handleDoorsInRawSegment(rawPath, rawEdgeStart, rawEdgeEnd,
                                    obstaclePolicy.unreachableDoorTimeoutMs(), doorEdgesAttemptedThisTail,
                                    null)) {
                                exitReason = "door-handled-local-reachability";
                                break;
                            }
                            if (isRecoveryMovementInFlight()) {
                                exitReason = "recovery-move-in-flight";
                                break;
                            }
                            boolean unresolvedDoorNearRawPath = hasUnresolvedDoorLikeObjectNearRawPath(rawPath,
                                    rawEdgeStart, playerLoc,
                                    UNREACHABLE_DOOR_RECOVERY_BACKTRACK_EDGES,
                                    UNREACHABLE_DOOR_RECOVERY_LOOKAHEAD_EDGES,
                                    HANDLER_RANGE);
                            if (!gateDoorInteraction
                                    && unresolvedDoorNearRawPath
                                    && handleUnresolvedDoorNearRawPath(rawPath, rawEdgeStart,
                                    obstaclePolicy.unreachableDoorTimeoutMs(), doorEdgesAttemptedThisTail,
                                    playerLoc,
                                    UNREACHABLE_DOOR_RECOVERY_BACKTRACK_EDGES,
                                    UNREACHABLE_DOOR_RECOVERY_LOOKAHEAD_EDGES,
                                    HANDLER_RANGE)) {
                                exitReason = "door-handled-nearby-route-door";
                                break;
                            }
                            // Fallback: only interact with objects on/adjacent to blocked path edges
                            // within ~15 tiles. Prevents clicking already-open / unrelated doors.
                            final long nowMs = System.currentTimeMillis();
                            if (!gateDoorInteraction
                                    && unresolvedDoorNearRawPath
                                    && obstaclePolicy.allowNearbyFallback()
                                    && nowMs - lastDoorPathAdjAttemptAtMs > 1200) {
                                lastDoorPathAdjAttemptAtMs = nowMs;
                                if (tryResolvePathAdjacentBlocker(playerLoc, rawPath, rawEdgeStart, 3, 10)) {
                                    exitReason = "door-handled-path-adj-scan";
                                    break;
                                }
                            }
                            if (unresolvedDoorNearRawPath) {
                                WebWalkLog.spInfo("door_recovery_suppressed | reason=nearby-route-door idx={} tile={}",
                                        rawEdgeStart, compactWorldPoint(currentWorldPoint));
                                exitReason = "door-recovery-suppressed";
                                break;
                            }

                            // Door/obstacle detection above found nothing to open. The local
                            // reachability BFS is bounded (~39 tiles) and is frequently a FALSE
                            // negative — a viable route exists, just longer than the BFS radius or
                            // behind a collision-map quirk. Rather than stall on an uncertain verdict,
                            // click toward the actual path route on the minimap and let the server's
                            // walk-here pathfinder take us as far as it can, then recover from there —
                            // like a human clicking the furthest visible tile. We trust the server path
                            // (no reachability gate on the click); isKnownWalkableOrUnloaded only keeps
                            // us from clicking into a known wall, it is NOT the bounded BFS check.
                            final int recoveryMinimapReach = STALL_RECOVERY_MINIMAP_REACH_EUCLIDEAN;
                            int recoverIdx = findForwardReachableRecoveryIndex(path, i, playerLoc,
                                    recoveryMinimapReach);
                            if (recoverIdx < 0) {
                                recoverIdx = findFurthestClickableIndex(path, i, playerLoc,
                                        wp -> {
                                            Set<Transport> ts = Rs2PathApi.getTransports().get(wp);
                                            return ts != null && !ts.isEmpty();
                                        },
                                        recoveryMinimapReach);
                            }
                            int minRecoveryIdx = Math.max(indexOfStartPoint, i);
                            recoverIdx = Math.min(Math.max(recoverIdx, minRecoveryIdx), path.size() - 1);
                            WorldPoint recoverTarget = path.get(recoverIdx);
                            if (euclideanSq(recoverTarget, playerLoc)
                                    > recoveryMinimapReach * recoveryMinimapReach) {
                                // Furthest in-range path tile still beyond the minimap clip (e.g. a
                                // diagonal segment). Interpolate a point near the minimap edge toward
                                // path[i]; the server routes through whatever blocks line-of-sight.
                                recoverTarget = interpolateClickableTarget(path, i, playerLoc,
                                        path.get(i), recoveryMinimapReach - 1,
                                        wp -> inInstance || isKnownWalkableOrUnloaded(wp));
                            }
                            // Don't let recovery park the player on a tile next to an aggressive NPC
                            // (e.g. an undead tree). The planner avoids those via avoidDangerousNpcs,
                            // but this runtime fallback would otherwise strand us in melee. Step the
                            // target back along the path to the nearest non-hazard tile.
                            PathfinderConfig dangerCfg = Rs2PathApi.getPathfinderConfig();
                            if (dangerCfg != null && dangerCfg.isAvoidDangerousNpcs() && recoverTarget != null
                                    && dangerCfg.isDangerousAdjacentTile(WorldPointUtil.packWorldPoint(recoverTarget))) {
                                int safeIdx = recoverIdx;
                                while (safeIdx > minRecoveryIdx
                                        && dangerCfg.isDangerousAdjacentTile(WorldPointUtil.packWorldPoint(path.get(safeIdx)))) {
                                    safeIdx--;
                                }
                                recoverIdx = safeIdx;
                                recoverTarget = path.get(safeIdx);
                            }
                            int rawAnchorIndex = rawIndexForSmoothedIndex(recoverIdx, smoothedToRaw, rawPath);
                            WorldPoint rawRecoveryTarget = inInstance ? null : findFurthestRawPathPointMatching(
                                    rawPath,
                                    playerLoc,
                                    recoveryMinimapReach - 1,
                                    rawAnchorIndex,
                                    Rs2Walker::isKnownWalkableOrUnloaded);
                            if (rawRecoveryTarget != null
                                    && !rawRecoveryTarget.equals(playerLoc)
                                    && (dangerCfg == null
                                    || !dangerCfg.isAvoidDangerousNpcs()
                                    || !dangerCfg.isDangerousAdjacentTile(WorldPointUtil.packWorldPoint(rawRecoveryTarget)))) {
                                recoverTarget = rawRecoveryTarget;
                            }
                            WorldPoint clickedRecoveryTarget = null;
                            if (recoverTarget != null && !recoverTarget.equals(playerLoc)) {
                                recoverTarget = clampToEuclideanRadius(playerLoc, recoverTarget,
                                        recoveryMinimapReach - 1);
                                clickedRecoveryTarget = clickMiniMapOrFallback(rawPath, recoverTarget, playerLoc,
                                        recoveryMinimapReach - 1, rawPath == null || rawPath.isEmpty(), rawAnchorIndex);
                            }
                            boolean clicked = clickedRecoveryTarget != null;
                            // Scene-click fallback only on final-adjacent approach (minimap click may
                            // miss the clip when very close); kept gated on reachability since it is a
                            // last resort, not the primary recovery path.
                            if (!clicked && recoverTarget != null
                                    && target != null
                                    && playerLoc.distanceTo2D(target) <= Math.max(2, distance + FINAL_ADJACENT_CANVAS_NUDGE_CHEBYSHEV)
                                    && playerLoc.distanceTo2D(recoverTarget) <= DOOR_OPEN_CANVAS_NUDGE_MAX_FROM_PLAYER
                                    && Rs2Tile.isTileReachable(recoverTarget)
                                    && walkFastCanvas(recoverTarget)) {
                                clicked = true;
                                clickedRecoveryTarget = recoverTarget;
                                log.debug("[Walker] unreachable recovery: scene click -> {}", recoverTarget);
                            }
                            log.info("[Walker] route-backed local recovery click: clicked={} to={} pathTile={} idx={}",
                                    clicked, clicked ? clickedRecoveryTarget : recoverTarget, currentWorldPoint, recoverIdx);
                            if (clicked) {
                                markFirstMovementClick("first_local_recovery_click", target, playerLoc,
                                        "to=" + compactWorldPoint(clickedRecoveryTarget));
                                hintRouteProgressIndex(path,
                                        Math.min(recoverIdx, i + INTERIM_CLOSE_TILES),
                                        target);
                                lastUnreachableRecoveryClickAtMs = System.currentTimeMillis();
                                // Sticky interim: subsequent iterations travel toward this point via the
                                // interim-in-flight path instead of re-running the (false-negative)
                                // reachability check and re-clicking every tick.
                                interimTargetWp = clickedRecoveryTarget;
                                interimTargetIdx = recoverIdx;
                                interimSetAtMs = System.currentTimeMillis();
                                interimLastProgressAtMs = interimSetAtMs;
                                interimLastBestPathIdx = getClosestTileIndex(path, playerLoc);
                                interimLastDistanceToTarget = distanceToInterimOrMax(clickedRecoveryTarget, playerLoc);
                                interimLastRetargetAtMs = interimSetAtMs;
                                WorldPoint pathLastRecovery = path.get(path.size() - 1);
                                int finishThRecovery = tightFinishThreshold(target, pathLastRecovery, distance);
                                waitForMovementStartAfterRecovery(target, playerLoc, clickedRecoveryTarget, target,
                                        finishThRecovery);
                                // Next outer iteration runs checkIfStuck/isStuckTooLong before tile delta — avoid
                                // spurious stall-recalc right after issuing recovery movement.
                                lastMovedTimeMs = System.currentTimeMillis();
                                stuckCount = 0;
                                exitReason = "local-recovery-click";
                                break;
                            }
                            exitReason = "local-reachability-miss-no-click";
                            break;
                        }
                    }
                    continue;
                }
                // A door was just interacted with (settling window still active) but traversal isn't
                // yet confirmed, so this forward tile may sit *behind* the opening door. Issuing the
                // minimap click now cancels the in-progress open ("click door, then immediately click
                // the tile behind it"). Yield this pass; the next settled pass walks through. The
                // unreachable / door-edge-resolution branch above is intentionally left alone — it
                // waits on the door edge itself and issues its own resolution-aware fast click.
                if (isDoorInteractionSettling()) {
                    exitReason = "door-settling-yield";
                    break;
                }
                nextWalkingDistance = path.size() <= 5 ? 0 : Rs2Random.between(9, 12);
                int dist2d = currentWorldPoint.distanceTo2D(Rs2Player.getWorldLocation());
                boolean approachPlannedTransportOrigin = shouldApproachPlannedTransportOrigin(
                        hasExplicitTransportStep(path, i),
                        currentWorldPoint,
                        Rs2Player.getWorldLocation(),
                        RAW_TRANSPORT_DISPATCH_MAX_DISTANCE);
                if (dist2d > nextWalkingDistance || approachPlannedTransportOrigin) {
                    tmarkPostTransport("post_transport_click_eligibility", target,
                            "i=" + i + " dist2d=" + dist2d + " threshold=" + nextWalkingDistance
                                    + (approachPlannedTransportOrigin ? " reason=transport_approach" : ""));
                    // Minimap clickable area is a circle, so reach is a Euclidean radius —
                    // cardinal tiles reach ~13, diagonals ~9. Empirically 14 was too
                    // optimistic (clicks at 13.5–13.9 Euclidean missed the clip).
                    WorldPoint playerLoc = Rs2Player.getWorldLocation();
                    final int MINIMAP_REACH_EUCLIDEAN = NORMAL_MINIMAP_REACH_EUCLIDEAN;

					// Checkpoint-style walking: once we set a minimap flag, let the player actually
					// travel toward it. Do not keep recalculating/clicking new targets mid-run.
					WorldPoint interim = interimTargetWp;
					if (interim != null && interim.getPlane() == playerLoc.getPlane()) {
						int interimDist = interim.distanceTo2D(playerLoc);
						if (interimDist > INTERIM_CLOSE_TILES) {
							final WorldPoint interimFinal = interim;
							// If we're already moving toward the interim checkpoint, just wait until
							// we get close. If we've stopped (no movement), re-click the same interim
							// rather than spinning without issuing movement commands.
							if (Rs2Player.isMoving()) {
                                if (!inInstance && handlePendingDoorDuringInterim(rawPath,
                                        obstaclePolicy.segmentDoorTimeoutMs(), doorEdgesAttemptedThisTail,
                                        playerLoc)) {
                                    interimTargetWp = null;
                                    interimTargetIdx = -1;
                                    interimSetAtMs = 0L;
                                    interimLastProgressAtMs = 0L;
                                    interimLastBestPathIdx = -1;
                                    interimLastDistanceToTarget = Integer.MAX_VALUE;
                                    interimLastRetargetAtMs = 0L;
                                    doorOrTransportResult = true;
                                    exitReason = "door-handled-during-interim";
                                    break;
                                }
								final WorldPoint posBeforeWait = playerLoc;
								sleepUntil(() ->
												interimFinal.distanceTo2D(Rs2Player.getWorldLocation()) <= interimPreclickTiles()
														|| !Rs2Player.isMoving(),
										INTERIM_MOVING_POLL_MS);
                                WorldPoint posAfterWait = Rs2Player.getWorldLocation();
                                recordInterimDistanceProgress(interimFinal, posAfterWait, System.currentTimeMillis());
								if ((posAfterWait != null && posBeforeWait.distanceTo2D(posAfterWait) > 0)
                                        || Rs2Player.isMoving()) {
									lastMovedTimeMs = System.currentTimeMillis();
									stuckCount = 0;
								}
                                boolean closeEnoughForNextClick = posAfterWait != null
                                        && interimFinal.distanceTo2D(posAfterWait) <= INTERIM_CLOSE_TILES;
                                if (!closeEnoughForNextClick && Rs2Player.isMoving()) {
                                    exitReason = "interim-in-flight";
                                    walkerDiag("interim-in-flight interim=%s interimDist=%d player=%s moving=true",
                                            interimFinal,
                                            posAfterWait == null ? interimDist : interimFinal.distanceTo2D(posAfterWait),
                                            posAfterWait == null ? playerLoc : posAfterWait);
                                    break;
                                }
							} else {
								// Not moving but still far from the interim checkpoint. Treat the interim
								// as stale and pick a fresh checkpoint below (could still resolve to the
								// same tile, but ensures we actually issue a new click).
								interimTargetWp = null;
								interimTargetIdx = -1;
								interimSetAtMs = 0L;
								interimLastProgressAtMs = 0L;
								interimLastBestPathIdx = -1;
                                interimLastDistanceToTarget = Integer.MAX_VALUE;
								interimLastRetargetAtMs = 0L;
							}
						}
						// Close enough: allow selecting a new checkpoint.
						interimTargetWp = null;
						interimTargetIdx = -1;
						interimSetAtMs = 0L;
						interimLastProgressAtMs = 0L;
						interimLastBestPathIdx = -1;
                        interimLastDistanceToTarget = Integer.MAX_VALUE;
						interimLastRetargetAtMs = 0L;
					}

                    int targetIdx = findFurthestForwardClickableIndex(path, i, playerLoc,
                            wp -> {
                                Set<Transport> ts = Rs2PathApi.getTransports().get(wp);
                                return ts != null && !ts.isEmpty();
                            },
                            MINIMAP_REACH_EUCLIDEAN);
                    WorldPoint targetWp = path.get(targetIdx);
                    // If the forward waypoint is outside minimap reach, interpolate a
                    // visible point in that direction instead of falling back to an
                    // earlier path tile.
                    if (euclideanSq(targetWp, playerLoc) > MINIMAP_REACH_EUCLIDEAN * MINIMAP_REACH_EUCLIDEAN) {
                        targetWp = interpolateClickableTarget(
                                path,
                                i,
                                playerLoc,
                                targetWp,
                                MINIMAP_REACH_EUCLIDEAN - 1,
                                wp -> inInstance || isKnownWalkableOrUnloaded(wp));
                    }

					// Sticky interim target: if we recently clicked a minimap point and are still
					// moving/progressing toward it, don't switch to a different waypoint just because
					// path smoothing/minimap flag visibility changed.
					final long nowMs = System.currentTimeMillis();
					WorldPoint sticky = interimTargetWp;
					if (sticky != null && sticky.getPlane() == playerLoc.getPlane()) {
						int stickyDist = sticky.distanceTo2D(playerLoc);
                        recordInterimDistanceProgress(sticky, playerLoc, nowMs);
						if (stickyDist <= INTERIM_CLOSE_TILES || nowMs - interimSetAtMs > INTERIM_MAX_AGE_MS) {
							interimTargetWp = null;
							interimTargetIdx = -1;
							interimSetAtMs = 0L;
							interimLastProgressAtMs = 0L;
							interimLastBestPathIdx = -1;
                            interimLastDistanceToTarget = Integer.MAX_VALUE;
							interimLastRetargetAtMs = 0L;
						} else {
							// U-turn safe progress: track progress along the path index, not Euclidean
							// distance-to-target (which can increase on U-shaped routes).
							int bestIdxNow = getClosestTileIndex(path, playerLoc);
							if (bestIdxNow > interimLastBestPathIdx) {
								interimLastBestPathIdx = bestIdxNow;
								interimLastProgressAtMs = nowMs;
							}
							boolean movingOrRecentlyMoved = Rs2Player.isMoving()
									|| (lastMovedTimeMs > 0 && nowMs - lastMovedTimeMs < 1500);
							boolean makingRecentProgress = interimLastProgressAtMs > 0
									&& nowMs - interimLastProgressAtMs < INTERIM_PROGRESS_TIMEOUT_MS;
							boolean retargetCoolingDown = interimLastRetargetAtMs > 0
									&& nowMs - interimLastRetargetAtMs < INTERIM_RETARGET_COOLDOWN_MS;

							// While moving and making progress, keep the existing interim target.
							// Cooldown prevents thrash when the route bends and the minimap flag drops.
							if ((movingOrRecentlyMoved && makingRecentProgress) || retargetCoolingDown) {
								targetWp = sticky;
								// Keep the loop index conservative: the sticky point might be interpolated
								// and not exist in the path.
								targetIdx = Math.max(targetIdx, i);
							}
						}
					}

                    WorldPoint posBefore = playerLoc;
                    int rawAnchorIndex = rawIndexForSmoothedIndex(i, smoothedToRaw, rawPath);
                    // Prefer a collision-REACHABLE raw-route point. "Walkable" (tile not fully
                    // blocked) is not the same as "reachable from the player": a tile flush on the
                    // far side of a castle wall is walkable yet only reachable via a long detour, so
                    // a Euclidean-close click there sends the player into the wall. Reachability
                    // gating excludes the wrong side outright. See movement.md #19.
                    WorldPoint rawRouteTarget = inInstance ? null
                            : selectRouteClickTarget(rawPath, playerLoc, MINIMAP_REACH_EUCLIDEAN - 1, rawAnchorIndex);
                    WorldPoint clickTarget;
                    String fallbackTag = "-";
                    if (rawRouteTarget != null && !rawRouteTarget.equals(playerLoc)) {
                        targetWp = rawRouteTarget;
                        clickTarget = rawRouteTarget;
                    } else {
                        clickTarget = inInstance ? targetWp : getPointWithWallDistance(targetWp, playerLoc);
                        // getPointWithWallDistance computes tiles reachable FROM THE TARGET, so its
                        // wall nudge can land on the far side of a wall or inside a building; a stale
                        // smoothed waypoint right after a teleport can also be unreachable. If the
                        // resulting click is not reachable, rejoin the route via the nearest reachable
                        // raw point on EITHER side of the anchor rather than clicking a wrong-side /
                        // random-far tile.
                        fallbackTag = "wallnudge";
                        if (!inInstance && !Rs2Tile.isTileReachable(clickTarget)) {
                            WorldPoint rejoin = findReachableRejoinRawPathPoint(rawPath, playerLoc,
                                    MINIMAP_REACH_EUCLIDEAN - 1, rawAnchorIndex);
                            if (rejoin != null && !rejoin.equals(playerLoc)) {
                                targetWp = rejoin;
                                clickTarget = rejoin;
                                fallbackTag = "rejoin";
                            }
                        }
                    }
                    if (!inInstance && handlePendingDoorBeforeRouteClick(rawPath, path, i, targetIdx,
                            smoothedToRaw, obstaclePolicy.segmentDoorTimeoutMs(), doorEdgesAttemptedThisTail,
                            playerLoc)) {
                        doorOrTransportResult = true;
                        exitReason = "door-handled-before-minimap-click";
                        break;
                    }
                    clickTarget = clampToEuclideanRadius(playerLoc, clickTarget, MINIMAP_REACH_EUCLIDEAN - 1);
                    long nowBeforeClick = System.currentTimeMillis();
                    if (lastTransportHandledAtMs > 0
                            && nowBeforeClick - lastTransportHandledAtMs <= POST_TRANSPORT_PATH_TMARK_WINDOW_MS) {
                        WebWalkLog.tmark("post_transport_path_selected",
                                nowBeforeClick - lastTransportHandledAtMs,
                                target,
                                posBefore,
                                "to=" + compactWorldPoint(clickTarget));
                    }
                    markStartupPhase("click_candidate_found", target,
                            "to=" + compactWorldPoint(clickTarget)
                                    + " sel=" + lastRouteClickTier + " fb=" + fallbackTag);
                    WorldPoint clickedTarget = clickMiniMapOrFallback(rawPath, clickTarget, playerLoc,
                            MINIMAP_REACH_EUCLIDEAN - 1, rawPath == null || rawPath.isEmpty(), rawAnchorIndex);
                    boolean clicked = clickedTarget != null;
                    if (walkCancelledDiag(target, "processWalk:after-minimap-click", processWalkTail)) {
                        return WalkerState.EXIT;
                    }
                    lastAttemptedMinimapClick = clicked ? clickedTarget : clickTarget;
                    lastAttemptedMinimapClickOk = clicked;
                    lastAttemptedMinimapClickAtMs = nowMs;
                    if (clicked) {
                        markFirstMovementClick("first_minimap_click", target, posBefore,
                                "to=" + compactWorldPoint(clickedTarget));
                        hintRouteProgressIndex(path,
                                Math.min(targetIdx, i + INTERIM_CLOSE_TILES),
                                target);
						interimTargetWp = clickedTarget;
						interimTargetIdx = targetIdx;
						interimSetAtMs = nowMs;
						interimLastProgressAtMs = nowMs;
						interimLastBestPathIdx = getClosestTileIndex(path, posBefore);
                        interimLastDistanceToTarget = distanceToInterimOrMax(clickedTarget, posBefore);
						interimLastRetargetAtMs = nowMs;

                        final WorldPoint b = targetWp;
                        final WorldPoint before = posBefore;
                        // Proximity-primary wake: let each click cover most of its distance
                        // before re-clicking, like a human. The progress cap is a safety net
                        // for the rare case where proximity never fires (player detoured, got
                        // blocked by another entity, etc.) — set just above max reach so it
                        // only triggers when something is actually wrong.
                        final int proximityWake = Rs2Random.between(2, 4);
                        final int progressCap = 16;
                        final long clickedAt = System.currentTimeMillis();
                        sleepUntil(() -> {
                            if (isWalkCancelled(target)) return true;
                            long elapsed = System.currentTimeMillis() - clickedAt;
                            if (elapsed < 600) return false;
                            if (!Rs2Player.isMoving()) return true;
                            WorldPoint now = Rs2Player.getWorldLocation();
                            if (b.distanceTo2D(now) <= proximityWake) return true;
                            return before.distanceTo2D(now) >= progressCap;
                        }, 1200);
                        WorldPoint afterClickWait = Rs2Player.getWorldLocation();
                        boolean routeRetryClicked = false;
                        if (afterClickWait != null && afterClickWait.equals(before) && !Rs2Player.isMoving()) {
                            WorldPoint routeRetryTarget = clickMiniMapOrFallback(rawPath, b, before,
                                    MINIMAP_REACH_EUCLIDEAN - 1, false, rawAnchorIndex);
                            if (routeRetryTarget != null) {
                                routeRetryClicked = true;
                                hintRouteProgressIndex(path,
                                        Math.min(targetIdx, i + INTERIM_CLOSE_TILES),
                                        target);
                                lastAttemptedMinimapClick = routeRetryTarget;
                                lastAttemptedMinimapClickOk = true;
                                lastAttemptedMinimapClickAtMs = System.currentTimeMillis();
                                interimTargetWp = routeRetryTarget;
                                interimLastProgressAtMs = lastAttemptedMinimapClickAtMs;
                                interimLastDistanceToTarget = distanceToInterimOrMax(routeRetryTarget, before);
                                interimLastRetargetAtMs = lastAttemptedMinimapClickAtMs;
                            }
                        }
                        if (afterClickWait != null && afterClickWait.equals(before) && !Rs2Player.isMoving()
                                && routeRetryClicked) {
                            sleepUntil(() -> {
                                if (isWalkCancelled(target)) return true;
                                WorldPoint now = Rs2Player.getWorldLocation();
                                return now != null && (b.distanceTo2D(now) <= proximityWake || !now.equals(before) || Rs2Player.isMoving());
                            }, 1200);
                        }
                        if (walkCancelledDiag(target, "processWalk:after-click-wait", processWalkTail)) {
                            return WalkerState.EXIT;
                        }

                        if (!Rs2Player.isMoving()) {
                            if (handleNearbyRawPathSceneObjects(rawPath, HANDLER_RANGE, target)) {
                                doorOrTransportResult = true;
                                exitReason = "post-click-raw-path-scene-object-handled";
                                break;
                            }
                            if (handleCurrentTileTransportTowardPath(rawPath, path, target)) {
                                doorOrTransportResult = true;
                                exitReason = "post-click-current-tile-transport-handled";
                                break;
                            }
                        }
                    }
                    // Keep stuck-detection honest: observed movement resets the movement timer.
                    // Without this, isStuckTooLong() fires after long successful walks because
                    // lastMovedTimeMs is only refreshed at processWalk entry (not during the loop).
                    if (posBefore.distanceTo2D(Rs2Player.getWorldLocation()) > 0) {
                        lastMovedTimeMs = System.currentTimeMillis();
                        stuckCount = 0;
                    }
                    // If the minimap click failed (target outside minimap radius), subsequent
                    // path tiles are further away and will also fail — break and let the outer
                    // loop wait for the player to walk closer before re-evaluating.
                    if (!clicked) {
                        exitReason = "click-failed-off-minimap";
                        interimTargetWp = null;
                        interimTargetIdx = -1;
                        interimSetAtMs = 0L;
                        interimLastProgressAtMs = 0L;
                        interimLastBestPathIdx = -1;
                        interimLastDistanceToTarget = Integer.MAX_VALUE;
                        interimLastRetargetAtMs = 0L;
                        sleepUntil(() -> isWalkCancelled(target) || !Rs2Player.isMoving(), 1200);
                        if (walkCancelledDiag(target, "processWalk:after-click-failed-wait", processWalkTail)) {
                            return WalkerState.EXIT;
                        }
                        break;
                    }
                    // Advance past intermediate tiles we've effectively walked over so the
                    // outer loop doesn't re-run door/rockfall/transport handlers for indices
                    // now behind the player.
                    i = targetIdx;
                }
            }

            if (doorOrTransportResult && shouldCanvasNudgeAfterDoorLikeExit(exitReason)) {
                maybeCanvasNudgeAfterDoor(target, distance, path);
                // Arm after nudge returns so the window does not expire during in-nudge waits; covers path-adj
                // door opens even when canvas nudge had no candidates / failed (still defer tryDirectShortWalk minimap).
                suppressTryDirectShortWalkUntilMs = System.currentTimeMillis() + POST_DOOR_NUDGE_SUPPRESS_TRY_DIRECT_MS;
                WorldPoint plAfterDoor = Rs2Player.getWorldLocation();
                if (!path.isEmpty() && plAfterDoor != null && target != null) {
                    WorldPoint pathLastDoor = path.get(path.size() - 1);
                    int finishAfterDoor = tightFinishThreshold(target, pathLastDoor, distance);
                    if (plAfterDoor.distanceTo(target) <= finishAfterDoor) {
                        setTarget(null, "rs2walker:processWalk:arrived-after-door-canvas-nudge");
                        return WalkerState.ARRIVED;
                    }
                }
            }

            if (!"end-of-path".equals(exitReason)) {
                WebWalkLog.earlyExit(exitReason,
                        Rs2Player.getWorldLocation(),
                        target,
                        path.get(path.size() - 1),
                        indexOfStartPoint,
                        path.size());
                walkerDiag("early-exit detail reason=%s interim=%s doorOrTransport=%s partialPath=%s",
                        exitReason,
                        interimTargetWp,
                        doorOrTransportResult,
                        partialPath);
            }

            // Only do the final-tile canvas click if we iterated the whole path cleanly.
            // Exiting because the player left the path may still mean movement is active.
            // so don't clobber that destination.
            if (!doorOrTransportResult && "end-of-path".equals(exitReason)) {
                if (walkCancelledDiag(target, "processWalk:before-final-canvas", processWalkTail)) {
                    return WalkerState.EXIT;
                }
                if (!path.isEmpty()) {
                    WorldPoint pathLast = path.get(path.size() - 1);
                    int finishTh = tightFinishThreshold(target, pathLast, distance);
                    WorldPoint finalTile = pathLast;
                    boolean pinGoal = target != null && pathLast.getPlane() == target.getPlane()
                            && pathLast.distanceTo2D(target) <= TIGHT_PATH_GOAL_GAP
                            && Rs2Tile.isTileReachable(target);
                    if (pinGoal) {
                        finalTile = target;
                    } else if (config.randomizeFinalTile()) {
                        var moveableTiles = Rs2Tile.getReachableTilesFromTile(pathLast, Math.min(3, distance)).keySet().toArray(new WorldPoint[0]);
                        if (moveableTiles.length > 0) {
                            finalTile = moveableTiles[Rs2Random.between(0, moveableTiles.length)];
                        }
                    }

                    if (Rs2Tile.isTileReachable(finalTile) && Rs2Player.getWorldLocation().distanceTo(finalTile) >= finishTh) {
                        final WorldPoint canvasClickWp = finalTile;
                        WorldPoint finalPlayerLoc = Rs2Player.getWorldLocation();
                        boolean finalClick;
                        if (rawPath != null && !rawPath.isEmpty() && finalPlayerLoc != null) {
                            int rawAnchorIndex = rawAnchorIndexForPathPosition(rawPath, path, finalPlayerLoc);
                            finalClick = clickRouteBackedShortWalk(rawPath, canvasClickWp, finalPlayerLoc,
                                    NORMAL_MINIMAP_REACH_EUCLIDEAN - 1, rawAnchorIndex);
                        } else {
                            finalClick = Rs2Walker.walkFastCanvas(canvasClickWp);
                        }
                        if (finalClick) {
                            waitUntilIdleAfterSceneWalk(target, POST_SCENE_WALK_IDLE_WAIT_MS_MAX, target, finishTh);
                            if (walkCancelledDiag(target, "processWalk:after-final-canvas-wait", processWalkTail)) {
                                return WalkerState.EXIT;
                            }
                        }
                    }
                }
            }
            // A route scan can legitimately find no new click while the server is still completing
            // the previous movement command. Charging those passes as failures can exhaust the
            // bounded tail loop before the player reaches a nearby transport origin.
            if (!doorOrTransportResult
                    && "end-of-path".equals(exitReason)
                    && Rs2Player.isMoving()) {
                exitReason = "route-move-in-flight";
            }
            WorldPoint pathLastForFinish = path.get(path.size() - 1);
            int finishThreshold = tightFinishThreshold(target, pathLastForFinish, distance);
            int finalDist = Rs2Player.getWorldLocation().distanceTo(target);
            if (finalDist <= finishThreshold) {
                setTarget(null, "rs2walker:processWalk:arrived-within-distance");
                return WalkerState.ARRIVED;
            } else if (partialPath) {
                if (walkCancelledDiag(target, "processWalk:partial-path-branch", processWalkTail)) {
                    return WalkerState.EXIT;
                }
                // Route progress since the last retry means the walk is working — refill the budget.
                // It otherwise only ever increments, so "3 retries" meant three outer-loop iterations
                // for the whole journey rather than three consecutive failures to advance.
                //
                // Standing somewhere new is required as well as the progress timestamp:
                // routeProgressAdvancedAtMs is also bumped whenever the route is merely REPLACED, and
                // each retry calls recalculatePath(), so the timestamp alone would let a retry refill
                // the budget it just spent. When the target is genuinely unreachable the player stops
                // moving, so requiring movement is what still lets the budget drain and terminate.
                WorldPoint retryLoc = Rs2Player.getWorldLocation();
                boolean movedSinceLastRetry = lastPartialRetryAtLoc == null
                        || (retryLoc != null && !retryLoc.equals(lastPartialRetryAtLoc));
                if (partialRetriesWorking > 0
                        && movedSinceLastRetry
                        && routeProgressAdvancedAtMs > lastPartialRetryAtMs) {
                    walkerDiag("partial retry budget refilled progressAt=%d lastRetryAt=%d spent=%d at=%s",
                            routeProgressAdvancedAtMs, lastPartialRetryAtMs, partialRetriesWorking, retryLoc);
                    partialRetriesWorking = 0;
                }
                // A handled door/transport/blocker ended the iteration because work was done, not
                // because the walker is stuck. Still re-route, but do not charge the budget for it.
                if (isRouteProgressExit(exitReason)) {
                    walkerDiag("partial retry exempt exitReason=%s tail=%d spent=%d",
                            exitReason, processWalkTail, partialRetriesWorking);
                    recalculatePath();
                    continue;
                }
                if (partialRetriesWorking < 3) {
                    lastPartialRetryAtMs = System.currentTimeMillis();
                    lastPartialRetryAtLoc = retryLoc;
                    Telemetry.recordPartialRetry(partialRetriesWorking + 1, finalDist);
                    WebWalkLog.partialRetry(finalDist, partialRetriesWorking + 1, 3);
                    recalculatePath();
                    partialRetriesWorking++;
                    continue;
                }
                WebWalkLog.partialExhausted(finalDist);
                // Report the real path endpoint and size. These were previously hardcoded to the
                // player's location and 0, so the log read as "pathfinder returned an empty path at
                // your feet" — and endpointToTarget became a surface-to-underground y delta rather
                // than the actual shortfall.
                WorldPoint unreachableEndpoint = path.isEmpty() ? null : path.get(path.size() - 1);
                Telemetry.recordUnreachable("partial-retries-exhausted", Rs2Player.getWorldLocation(),
                        target, unreachableEndpoint, path.size(), distance, Rs2PathApi.getPathfinder());
                setTarget(null, "rs2walker:processWalk:partial-retries-exhausted");
                return WalkerState.UNREACHABLE;
            } else {
                if (isOffPathRecalcDeferredExit(exitReason)) {
                    // Wait briefly for the player to re-enter the path or for the progress signal
                    // that deferred the recalc to expire. Prevents a tight loop around isNearPath().
                    String deferReason = offPathDeferredReasonFromExit(exitReason);
                    long offPathWaitMs = offPathRecalcDeferredWaitMs(deferReason,
                            System.currentTimeMillis(),
                            lastMovedTimeMs,
                            routeProgressAdvancedAtMs,
                            lastAttemptedMinimapClickAtMs,
                            interimLastProgressAtMs);
                    long now = System.currentTimeMillis();
                    if (lastTransportHandledAtMs > 0
                            && now - lastTransportHandledAtMs <= POST_TRANSPORT_PATH_TMARK_WINDOW_MS) {
                        long elapsedSinceTransport = Math.max(0L, now - lastTransportHandledAtMs);
                        long remainingBudget = POST_TRANSPORT_OFFPATH_WAIT_BUDGET_MS - elapsedSinceTransport;
                        if (remainingBudget <= 0) {
                            offPathWaitMs = 0L;
                        } else {
                            offPathWaitMs = Math.min((long) POST_TRANSPORT_OFFPATH_WAIT_SLICE_MS, remainingBudget);
                        }
                    }
                    if (offPathWaitMs > 0) {
                        final long deferredLastClickAtMs = lastAttemptedMinimapClickAtMs;
                        if (lastTransportHandledAtMs > 0
                                && System.currentTimeMillis() - lastTransportHandledAtMs <= POST_TRANSPORT_PATH_TMARK_WINDOW_MS) {
                            WebWalkLog.tmark("post_transport_offpath_sleep",
                                    System.currentTimeMillis() - lastTransportHandledAtMs,
                                    target,
                                    Rs2Player.getWorldLocation(),
                                    "ms=" + offPathWaitMs);
                        }
                        sleepUntil(() -> isWalkCancelled(target)
                                        || isNearPath()
                                        || currentOffPathRecalcDeferralReason(deferredLastClickAtMs) == null,
                                (int) offPathWaitMs);
                    }
                    if (walkCancelledDiag(target, "processWalk:after-off-path-wait", processWalkTail)) {
                        return WalkerState.EXIT;
                    }
                }
                // Benign yields: outer for-loop increments processWalkTail each iteration; exempt so
                // long minimap interim waits cannot exhaust MAX_PROCESS_WALK_TAIL_ITERATIONS and EXIT.
                if ("interim-in-flight".equals(exitReason)
                        || "recovery-move-in-flight".equals(exitReason)
                        || "route-move-in-flight".equals(exitReason)
                        || "route-fold-continuation-click".equals(exitReason)
                        || isOffPathRecalcDeferredExit(exitReason)) {
                    walkerDiag("tail exempt exitReason=%s tailBefore=%d", exitReason, processWalkTail);
                    processWalkTail--;
                }
                walkerDiag("continue outer tail nextIdx=%d exitReason=%s finalDist=%d partialPath=%s",
                        processWalkTail + 1,
                        exitReason,
                        Rs2Player.getWorldLocation().distanceTo(target),
                        partialPath);
                continue;
            }
        } catch (Exception ex) {
            if (ex instanceof InterruptedException || ex.getCause() instanceof InterruptedException) {
                WebWalkLog.interruptedExit("pathfinder interrupted (397)");
                traceProcessWalkExit("interrupted-exception", target, MAX_PROCESS_WALK_TAIL_ITERATIONS - 1);
                setTarget(null, "rs2walker:processWalk:interrupted-exception");
                return WalkerState.EXIT;
            }
            log.error("Exception in Rs2Walker:", ex);
            WebWalkLog.interruptedExit("walker exception exit (403)");
            traceProcessWalkExit("exception-" + ex.getClass().getSimpleName(), target, MAX_PROCESS_WALK_TAIL_ITERATIONS - 1);
            return WalkerState.EXIT;
        }
        }
        Microbot.log(Level.WARN,
                "[WalkerDiag] exceeded MAX_PROCESS_WALK_TAIL_ITERATIONS (%d) target=%s currentTarget=%s interim=%s stuck=%d player=%s — enable DEBUG for per-iteration traces",
                MAX_PROCESS_WALK_TAIL_ITERATIONS,
                target,
                currentTarget,
                interimTargetWp,
                stuckCount,
                Rs2Player.getWorldLocation());
        WebWalkLog.tailExceeded(MAX_PROCESS_WALK_TAIL_ITERATIONS, target, currentTarget, interimTargetWp, stuckCount,
                Rs2Player.getWorldLocation());
        return WalkerState.EXIT;
    }

    public static boolean walkNextTo(GameObject target) {
        Rs2WorldArea gameObjectArea = new Rs2WorldArea(Objects.requireNonNull(Rs2GameObject.getWorldArea(target)));
        List<WorldPoint> interactablePoints = gameObjectArea.getInteractable();

        if (interactablePoints.isEmpty()) {
            interactablePoints.addAll(gameObjectArea.offset(1).toWorldPointList());
            interactablePoints.removeIf(gameObjectArea::contains);
        }

        WorldPoint walkableInteractPoint = interactablePoints.stream()
                .filter(Rs2Tile::isWalkable)
                .findFirst()
                .orElse(null);
        // Priority to a walkable tile, otherwise walk to the first tile next to locatable

        if(walkableInteractPoint != null && walkableInteractPoint.equals(Rs2Player.getWorldLocation()))
            return true;
        return walkableInteractPoint != null ? walkTo(walkableInteractPoint) : walkTo(interactablePoints.get(0));
    }

    public static void walkNextToInstance(GameObject target) {
        Rs2WorldArea gameObjectArea = new Rs2WorldArea(Objects.requireNonNull(Rs2GameObject.getWorldArea(target)));
        List<WorldPoint> interactablePoints = gameObjectArea.getInteractable();

        if (interactablePoints.isEmpty()) {
            interactablePoints.addAll(gameObjectArea.offset(1).toWorldPointList());
            interactablePoints.removeIf(gameObjectArea::contains);
        }

        WorldPoint walkableInteractPoint = interactablePoints.stream()
                .filter(Rs2Tile::isWalkable).min(Comparator.comparingInt(Rs2Player.getWorldLocation()::distanceTo))
                .orElse(null);
        // Priority to a walkable tile, otherwise walk to the first tile next to locatable
        if (walkableInteractPoint != null) {
            if(walkableInteractPoint.equals(Rs2Player.getWorldLocation()))
                return;
            walkFastLocal(LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), walkableInteractPoint));
        } else {
            walkFastLocal(LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), Objects.requireNonNull(interactablePoints.stream().min(Comparator.comparingInt(Rs2Player.getWorldLocation()::distanceTo))
                    .orElse(null))));
        }
    }

    public static WorldPoint getPointWithWallDistance(WorldPoint target) {
        return getPointWithWallDistance(target, null);
    }

    /**
     * Nudges a click target off a wall edge onto an open neighbour. The candidate set
     * ({@link Rs2Tile#getReachableTilesFromTile} radius 1) is an unordered {@link Set}, so returning
     * the first clean tile picks an arbitrary side — that is how the walker ends up clicking the far
     * side of a wall or into a building. When {@code playerLoc} is supplied, choose the clean
     * neighbour reachable from the player and nearest to the player, keeping the nudge on the
     * player's side (the road). See movement.md #19.
     */
    public static WorldPoint getPointWithWallDistance(WorldPoint target, WorldPoint playerLoc) {
        var tiles = Rs2Tile.getReachableTilesFromTile(target, 1);

        var wv = Microbot.getClient().getTopLevelWorldView();
        var localPoint = LocalPoint.fromWorld(wv, target);
        if (wv.getCollisionMaps() != null && localPoint != null) {
            int[][] flags = wv.getCollisionMaps()[wv.getPlane()].getFlags();

            Set<WorldPoint> reachableFromPlayer = playerLoc == null
                    ? Collections.emptySet()
                    : Rs2Tile.getReachableTilesFromTile(playerLoc,
                            Math.max(2, NORMAL_MINIMAP_REACH_EUCLIDEAN)).keySet();

            if (hasMinimapRelevantMovementFlag(localPoint, flags)) {
                WorldPoint best = bestWallDistanceNeighbor(tiles.keySet(), playerLoc, reachableFromPlayer,
                        tile -> {
                            var lp = LocalPoint.fromWorld(wv, tile);
                            return lp != null && !hasMinimapRelevantMovementFlag(lp, flags);
                        });
                if (best != null) {
                    return best;
                }
            }

            int data = flags[localPoint.getSceneX()][localPoint.getSceneY()];

            Set<MovementFlag> movementFlags = MovementFlag.getSetFlags(data);

            if (movementFlags.contains(MovementFlag.BLOCK_MOVEMENT_EAST)
                    || movementFlags.contains(MovementFlag.BLOCK_MOVEMENT_WEST)
                    || movementFlags.contains(MovementFlag.BLOCK_MOVEMENT_NORTH)
                    || movementFlags.contains(MovementFlag.BLOCK_MOVEMENT_SOUTH)) {
                WorldPoint best = bestWallDistanceNeighbor(tiles.keySet(), playerLoc, reachableFromPlayer,
                        tile -> {
                            var lp = LocalPoint.fromWorld(wv, tile);
                            if (lp == null) {
                                return false;
                            }
                            return MovementFlag.getSetFlags(flags[lp.getSceneX()][lp.getSceneY()]).isEmpty();
                        });
                if (best != null) {
                    return best;
                }
            }
        }

        return target;
    }

    /**
     * Picks the wall-distance neighbour that best keeps the click on the player's side of the wall:
     * prefer tiles reachable from the player, then the one nearest the player. Falls back to the
     * first clean tile when the player position is unknown, preserving the original behaviour.
     */
    private static WorldPoint bestWallDistanceNeighbor(Collection<WorldPoint> candidates,
                                                       WorldPoint playerLoc,
                                                       Set<WorldPoint> reachableFromPlayer,
                                                       Predicate<WorldPoint> isClean) {
        WorldPoint best = null;
        boolean bestReachable = false;
        int bestDist = Integer.MAX_VALUE;
        for (WorldPoint tile : candidates) {
            if (tile == null || !isClean.test(tile)) {
                continue;
            }
            if (playerLoc == null) {
                return tile; // no player context: preserve original "first clean tile" behaviour
            }
            boolean reachable = reachableFromPlayer.contains(tile);
            int dist = tile.distanceTo2D(playerLoc);
            boolean better = best == null
                    || (reachable && !bestReachable)
                    || (reachable == bestReachable && dist < bestDist);
            if (better) {
                best = tile;
                bestReachable = reachable;
                bestDist = dist;
            }
        }
        return best;
    }

    private static boolean isKnownWalkableOrUnloaded(WorldPoint target) {
        if (target == null) {
            return false;
        }

        LocalPoint localTarget = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), target);
        return localTarget == null || Rs2Tile.isWalkable(localTarget);
    }

    private static boolean isWalkCancelled(WorldPoint target) {
        WalkCompletionContext completion = walkCompletionContext.get();
        if (completion != null && Objects.equals(completion.target, target)
                && evaluateWalkCompletion(completion)) {
            if (Objects.equals(currentTarget, target)) {
                setTarget(null, "rs2walker:completion-condition-met");
            }
            return true;
        }
        WorldPoint activeTarget = currentTarget;
        return target == null || activeTarget == null || !target.equals(activeTarget)
                || Thread.currentThread().isInterrupted();
    }

    /**
     * Completion callbacks are user-supplied extension code. One bad callback must not strand
     * the global walker lock or abort an otherwise valid route, so disable it after its first
     * exception and let normal distance-based walking continue.
     */
    private static boolean evaluateWalkCompletion(WalkCompletionContext context) {
        if (context.met) {
            return true;
        }
        if (context.failed) {
            return false;
        }
        try {
            context.met = context.condition.getAsBoolean();
        } catch (RuntimeException ex) {
            context.failed = true;
            log.warn("[Walker] completion condition failed; continuing with distance-based walking: {}",
                    ex.toString());
        }
        return context.met;
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

    // Enable run (if energy permits) and drink a stamina/restore-energy potion when
    // energy drops below a threshold on a long walk. Short hops don't justify a dose.
    private static long lastStaminaDoseAtMs = 0;
    static final int STAMINA_THRESHOLD_MIN = 12;
    static final int STAMINA_THRESHOLD_MAX = 55;
    static final int STAMINA_CASUAL_MIN = 35;
    static final int STAMINA_CASUAL_MAX = 55;
    static final int STAMINA_HARDCORE_MIN = 12;
    static final int STAMINA_HARDCORE_MAX = 24;
    static final double STAMINA_HARDCORE_PROBABILITY = 0.3;
    private static final int STAMINA_THRESHOLD_FALLBACK = 35;
    private static final int STAMINA_MIN_PATH_TILES = 20;
    private static final long STAMINA_MIN_INTERVAL_MS = 10_000;

    private static volatile String staminaSeedName = null;
    private static volatile int staminaThresholdCached = STAMINA_THRESHOLD_FALLBACK;

	// Cooldown to avoid spamming expensive door fallback scans on unreachable tiles.
	private static long lastDoorFallbackAttemptAtMs = 0L;
	private static long lastDoorLosAttemptAtMs = 0L;
	private static long lastDoorPathAdjAttemptAtMs = 0L;

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

    private static long mix64(String s) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x100000001b3L;
        }
        return h;
    }

    private static int staminaThreshold() {
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

    private static void manageRunEnergy(int pathRemaining) {
        try {
            if (!Rs2Player.isRunEnabled() && Rs2Player.getRunEnergy() > 10) {
                Rs2Player.toggleRunEnergy(true);
            }
            if (pathRemaining < STAMINA_MIN_PATH_TILES) return;
            if (Rs2Player.getRunEnergy() >= staminaThreshold()) return;
            if (Rs2Player.hasStaminaBuffActive()) return;
            long now = System.currentTimeMillis();
            if (now - lastStaminaDoseAtMs < STAMINA_MIN_INTERVAL_MS) return;
            if (Rs2Inventory.hasItem("stamina potion") || Rs2Inventory.hasItem("energy potion")
                    || Rs2Inventory.hasItem("super energy")) {
                Rs2Inventory.useRestoreEnergyItem();
                lastStaminaDoseAtMs = now;
            }
        } catch (Exception ex) {
            // Never let stamina management break the walk — log and move on.
            log.debug("[Walker] manageRunEnergy failed: {}", ex.getMessage());
        }
    }

    public static boolean walkMiniMap(WorldPoint worldPoint, double zoomDistance) {
        if (Microbot.getClient().getMinimapZoom() != zoomDistance)
            Microbot.getClient().setMinimapZoom(zoomDistance);

        Point point = Rs2MiniMap.worldToMinimap(worldPoint);

        if (point == null) return false;
        if (!disableWalkerUpdate && !Rs2MiniMap.isPointInsideMinimap(point)) return false;

        Microbot.getMouse().click(point);
        return true;
    }


    public static boolean walkMiniMap(WorldPoint worldPoint) {
        return walkMiniMap(worldPoint, 5);
    }

    private static boolean isMiniMapClickable(WorldPoint worldPoint, double zoomDistance) {
        if (worldPoint == null) {
            return false;
        }
        if (Microbot.getClient().getMinimapZoom() != zoomDistance) {
            Microbot.getClient().setMinimapZoom(zoomDistance);
        }
        Point point = Rs2MiniMap.worldToMinimap(worldPoint);
        return point != null && (disableWalkerUpdate || Rs2MiniMap.isPointInsideMinimap(point));
    }

    private static boolean walkRawPathMiniMapToward(List<WorldPoint> rawPath,
                                                    WorldPoint target,
                                                    WorldPoint playerLoc,
                                                    int maxEuclidean) {
        return walkRawPathMiniMapTargetToward(rawPath, target, playerLoc, maxEuclidean, -1) != null;
    }

    private static WorldPoint clickMiniMapOrFallback(List<WorldPoint> rawPath,
                                                     WorldPoint target,
                                                     WorldPoint playerLoc,
                                                     int maxEuclidean,
                                                     boolean allowDirectionalFallback) {
        return clickMiniMapOrFallback(rawPath, target, playerLoc, maxEuclidean, allowDirectionalFallback, -1);
    }

    private static WorldPoint clickMiniMapOrFallback(List<WorldPoint> rawPath,
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

    private static WorldPoint walkRawPathMiniMapTargetToward(List<WorldPoint> rawPath,
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

    private static boolean walkReachableMiniMapToward(WorldPoint target, WorldPoint playerLoc, int maxEuclidean) {
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

    static WorldPoint findFurthestRawPathPointMatching(List<WorldPoint> rawPath,
                                                       WorldPoint playerLoc,
                                                       int maxEuclidean,
                                                       int rawAnchorIndex,
                                                       Predicate<WorldPoint> isCandidate) {
        if (rawPath == null || rawPath.isEmpty() || playerLoc == null) {
            return null;
        }
        int closestRawIndex = rawPathForwardAnchorIndex(rawPath, playerLoc, rawAnchorIndex);
        if (closestRawIndex < 0) {
            return null;
        }

        int maxSq = maxEuclidean * maxEuclidean;
        int maxRouteSteps = maxEuclidean + 2;
        int routeSteps = 0;
        WorldPoint best = null;
        for (int rawIndex = closestRawIndex; rawIndex < rawPath.size(); rawIndex++) {
            WorldPoint candidate = rawPath.get(rawIndex);
            if (candidate == null || candidate.getPlane() != playerLoc.getPlane()) {
                break;
            }
            if (rawIndex > closestRawIndex) {
                WorldPoint previous = rawPath.get(rawIndex - 1);
                routeSteps += rawPathStepDistance(previous, candidate);
                if (routeSteps > maxRouteSteps) {
                    break;
                }
            }
            if (euclideanSq(candidate, playerLoc) > maxSq) {
                break;
            }
            if (!candidate.equals(playerLoc) && (isCandidate == null || isCandidate.test(candidate))) {
                best = candidate;
            }
        }
        return best;
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
    private static WorldPoint selectRouteClickTarget(List<WorldPoint> rawPath, WorldPoint playerLoc,
                                                     int maxEuclidean, int rawAnchorIndex) {
        if (rawPath == null || rawPath.isEmpty() || playerLoc == null) {
            lastRouteClickTier = "norawpath";
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
                lastRouteClickTier = lastRouteClickTier + "@player";
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

    private static WorldPoint selectRouteClickTargetAnchored(List<WorldPoint> rawPath, WorldPoint playerLoc,
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
        WorldPoint forward = findFurthestRawPathPointMatching(rawPath, playerLoc, maxEuclidean,
                rawAnchorIndex, Rs2Walker::isKnownWalkableOrUnloaded);
        if (forward != null && !forward.equals(playerLoc)) {
            lastRouteClickTier = "route";
            return forward;
        }
        lastRouteClickTier = "none";
        return null;
    }

    /**
     * Which tier of {@link #selectRouteClickTarget} produced the most recent click target
     * ({@code los} / {@code reach} / {@code offscene} / {@code none}, or {@code wallnudge} /
     * {@code rejoin} when selection fell through). Surfaced in the {@code click_candidate_found}
     * tmark so a log alone shows which selection path a click came from.
     */
    private static volatile String lastRouteClickTier = "none";




    /**
     * Finds a reachable raw-path point to rejoin the route after the player has been pushed off it
     * (stuck against a wall, knocked back, or landed off-path after a teleport). Unlike the primary
     * forward-only selection, this scans a bounded index window on BOTH sides of the anchor so the
     * walker can step slightly backward onto the path line when nothing ahead is reachable. Forward
     * points are still preferred (highest index first), so normal progress is never sacrificed and
     * the walker cannot snap all the way back to an already-travelled branch.
     */
    private static WorldPoint findReachableRejoinRawPathPoint(List<WorldPoint> rawPath, WorldPoint playerLoc,
                                                              int maxEuclidean, int rawAnchorIndex) {
        if (playerLoc == null) {
            return null;
        }
        Set<WorldPoint> reachable = Rs2Tile
                .getReachableTilesFromTile(playerLoc, Math.max(2, maxEuclidean * 2)).keySet();
        return findReachableRejoinRawPathPoint(rawPath, playerLoc, maxEuclidean, rawAnchorIndex,
                reachable::contains);
    }

    /**
     * Testable core of {@link #findReachableRejoinRawPathPoint(List, WorldPoint, int, int)} with an
     * injectable reachability predicate (so it can be exercised without a live client).
     */
    static WorldPoint findReachableRejoinRawPathPoint(List<WorldPoint> rawPath, WorldPoint playerLoc,
                                                      int maxEuclidean, int rawAnchorIndex,
                                                      Predicate<WorldPoint> isReachable) {
        if (rawPath == null || rawPath.isEmpty() || playerLoc == null || isReachable == null) {
            return null;
        }
        int anchor = rawPathForwardAnchorIndex(rawPath, playerLoc, rawAnchorIndex);
        if (anchor < 0) {
            return null;
        }
        int window = maxEuclidean + 2;
        int lo = Math.max(0, anchor - window);
        int hi = Math.min(rawPath.size() - 1, anchor + window);
        int maxSq = maxEuclidean * maxEuclidean;
        // Highest index first => prefer the furthest-forward reachable point; only fall back to
        // points behind the anchor when nothing ahead in the window is reachable.
        for (int idx = hi; idx >= lo; idx--) {
            WorldPoint candidate = rawPath.get(idx);
            if (candidate == null || candidate.getPlane() != playerLoc.getPlane()
                    || candidate.equals(playerLoc)) {
                continue;
            }
            if (euclideanSq(candidate, playerLoc) > maxSq) {
                continue;
            }
            if (isReachable.test(candidate)) {
                return candidate;
            }
        }
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

        return findFurthestRawPathPointMatching(rawPath, playerLoc, maxEuclidean, rawAnchorIndex,
                candidate -> !candidate.equals(playerLoc)
                        && isKnownWalkableOrUnloaded(candidate)
                        && isMiniMapClickable(candidate, 5));
    }

    private static int rawPathStepDistance(WorldPoint previous, WorldPoint current) {
        if (previous == null || current == null || previous.getPlane() != current.getPlane()) {
            return Integer.MAX_VALUE / 4;
        }
        return Math.max(1, previous.distanceTo2D(current));
    }

    static boolean isLocalRecoveryCandidateOnForwardRoute(List<WorldPoint> rawPath,
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

    static int rawPathForwardAnchorIndex(List<WorldPoint> rawPath, WorldPoint playerLoc, int rawAnchorIndex) {
        if (rawPath == null || rawPath.isEmpty() || playerLoc == null) {
            return -1;
        }
        if (rawAnchorIndex < 0) {
            return getClosestTileIndex(rawPath, playerLoc);
        }

        int start = Math.max(0, Math.min(rawAnchorIndex, rawPath.size() - 1));
        int endExclusive = Math.min(rawPath.size(), start + ROUTE_PROGRESS_FORWARD_SEARCH_TILES + 1);
        int bestIdx = -1;
        int bestDist = Integer.MAX_VALUE;
        for (int i = start; i < endExclusive; i++) {
            WorldPoint point = rawPath.get(i);
            if (point == null) {
                continue;
            }
            if (point.getPlane() != playerLoc.getPlane()) {
                break;
            }
            int dist = point.distanceTo2D(playerLoc);
            if (dist < bestDist) {
                bestIdx = i;
                bestDist = dist;
                if (dist == 0) {
                    break;
                }
            }
        }
        return bestIdx >= 0 ? bestIdx : start;
    }

    private static boolean shouldIssueActiveRouteIdleNudge() {
        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        long now = System.currentTimeMillis();
        if (playerLoc == null || Rs2Player.isMoving() || Rs2Player.isAnimating() || Rs2Player.isInteracting()
                || Rs2LeaguesTransport.isTeleportInProgress()
                || Rs2LeaguesTransport.isLeaguesAreaTeleportPending(LEAGUES_AREA_PENDING_STALL_MAX_AGE_MS)) {
            idleNudgeLastObservedLocation = playerLoc;
            idleNudgeStationarySinceMs = now;
            return false;
        }
        if (!playerLoc.equals(idleNudgeLastObservedLocation)) {
            idleNudgeLastObservedLocation = playerLoc;
            idleNudgeStationarySinceMs = now;
            return false;
        }
        if (idleNudgeStationarySinceMs <= 0L) {
            idleNudgeStationarySinceMs = now;
            return false;
        }
        return now - idleNudgeStationarySinceMs >= ACTIVE_ROUTE_IDLE_NUDGE_MS
                && now - lastActiveRouteIdleNudgeAtMs >= ACTIVE_ROUTE_IDLE_NUDGE_COOLDOWN_MS;
    }

    private static boolean tryIssueRouteRecoveryClick(List<WorldPoint> rawPath,
                                                      List<WorldPoint> path,
                                                      WorldPoint target,
                                                      int configuredDistance,
                                                      String logLabel) {
        return tryIssueRouteMovementClick(rawPath, path, target, configuredDistance, logLabel,
                STALL_RECOVERY_MINIMAP_REACH_EUCLIDEAN, true);
    }

    private static boolean tryIssueRouteContinuationClick(List<WorldPoint> rawPath,
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
        return tryIssueRouteMovementClick(rawPath, path, target, configuredDistance, "interim close route click",
                NORMAL_MINIMAP_REACH_EUCLIDEAN, false);
    }

    private static boolean tryIssueRouteMovementClick(List<WorldPoint> rawPath,
                                                      List<WorldPoint> path,
                                                      WorldPoint target,
                                                      int configuredDistance,
                                                      String logLabel,
                                                      int maxEuclidean,
                                                      boolean markRecoveryCooldown) {
        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        if (playerLoc == null || path == null || path.isEmpty()) {
            return false;
        }
        if (routeArrivalSatisfied(playerLoc, target, path, configuredDistance)) {
            return false;
        }
        int targetIdx = stabilizeRouteProgressIndex(path, getClosestTileIndex(path, playerLoc), target, playerLoc);
        int startIdx = Math.max(0, targetIdx);
        int[] routeSmoothedToRaw = mapSmoothedToRaw(path, rawPath);
        int rawAnchorIndex = rawIndexForSmoothedIndex(targetIdx, routeSmoothedToRaw, rawPath);

        // Use the same route-backed selector as the main route loop. Recovery/idle-nudge and
        // interim continuation clicks previously had their own walkable-only selection, so which
        // policy applied depended on which path happened to fire (timing-dependent, e.g. a slow
        // pathfinder makes the idle nudge issue the first click instead of the main loop).
        WorldPoint clickTarget = selectRouteClickTarget(rawPath, playerLoc, maxEuclidean, rawAnchorIndex);
        if (clickTarget == null) {
            int clickableIdx = findFurthestForwardClickableIndex(path, startIdx, playerLoc,
                    wp -> {
                        Set<Transport> ts = Rs2PathApi.getTransports().get(wp);
                        return ts != null && !ts.isEmpty();
                    },
                    maxEuclidean);
            clickableIdx = Math.max(startIdx, Math.min(clickableIdx, path.size() - 1));
            clickTarget = path.get(clickableIdx);
            targetIdx = clickableIdx;
            if (euclideanSq(clickTarget, playerLoc)
                    > maxEuclidean * maxEuclidean) {
                clickTarget = interpolateClickableTarget(
                        path,
                        startIdx,
                        playerLoc,
                        clickTarget,
                        maxEuclidean - 1,
                        Rs2Walker::isKnownWalkableOrUnloaded);
            }
        }

        boolean clicked = false;
        WorldPoint clickedTarget = null;
        if (clickTarget != null && !clickTarget.equals(playerLoc)) {
            clickTarget = clampToEuclideanRadius(playerLoc, clickTarget, maxEuclidean - 1);
            clickedTarget = clickMiniMapOrFallback(rawPath, clickTarget, playerLoc,
                    maxEuclidean - 1, rawPath == null || rawPath.isEmpty(), rawAnchorIndex);
            clicked = clickedTarget != null;
        }
        if (clicked && "interim close route click".equals(logLabel)) {
            log.debug("[Walker] {}: clicked={} to={} player={} idx={}",
                    logLabel, true, clickedTarget, playerLoc, targetIdx);
        } else {
            log.info("[Walker] {}: clicked={} to={} player={} idx={}",
                    logLabel, clicked, clicked ? clickedTarget : clickTarget, playerLoc, targetIdx);
        }
        if (!clicked) {
            return false;
        }

        markFirstMovementClick(routeMovementClickPhase(logLabel),
                target,
                playerLoc,
                "to=" + compactWorldPoint(clickedTarget));
        hintRouteProgressIndex(path,
                Math.min(targetIdx, startIdx + INTERIM_CLOSE_TILES),
                target);
        interimTargetWp = clickedTarget;
        interimTargetIdx = targetIdx;
        interimSetAtMs = System.currentTimeMillis();
        interimLastProgressAtMs = interimSetAtMs;
        interimLastBestPathIdx = getClosestTileIndex(path, playerLoc);
        interimLastDistanceToTarget = distanceToInterimOrMax(clickedTarget, playerLoc);
        interimLastRetargetAtMs = interimSetAtMs;
        if (markRecoveryCooldown) {
            lastUnreachableRecoveryClickAtMs = interimSetAtMs;
        }
        if ("active route idle nudge".equals(logLabel)) {
            lastActiveRouteIdleNudgeAtMs = interimSetAtMs;
        } else {
            lastMovedTimeMs = interimSetAtMs;
        }
        idleNudgeStationarySinceMs = interimSetAtMs;
        idleNudgeLastObservedLocation = playerLoc;
        stuckCount = 0;
        return true;
    }

    static boolean routeArrivalSatisfied(WorldPoint playerLoc,
                                         WorldPoint target,
                                         List<WorldPoint> path,
                                         int configuredDistance) {
        if (playerLoc == null || target == null || path == null || path.isEmpty()
                || playerLoc.getPlane() != target.getPlane()) {
            return false;
        }
        WorldPoint pathEnd = path.get(path.size() - 1);
        int finishThreshold = tightFinishThreshold(target, pathEnd, configuredDistance);
        return playerLoc.distanceTo2D(target) <= finishThreshold;
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

    /**
     * Used in instances like vorkath, jad, nmz
     *
     * @param localPoint A two-dimensional point in the local coordinate space.
     */
    public static void walkFastLocal(LocalPoint localPoint) {
        Point canv = Perspective.localToCanvas(Microbot.getClient(), localPoint, Microbot.getClient().getTopLevelWorldView().getPlane());
        int canvasX = canv != null ? canv.getX() : -1;
        int canvasY = canv != null ? canv.getY() : -1;

        NewMenuEntry entry = new NewMenuEntry()
                .param0(canvasX)
                .param1(canvasY)
                .type(MenuAction.WALK)
                .identifier(0)
                .itemId(-1)
                .option("Walk here");

        Microbot.doInvoke(entry,
                new Rectangle(1, 1, Microbot.getClient().getCanvasWidth(), Microbot.getClient().getCanvasHeight()));
        //Rs2Reflection.invokeMenu(canvasX, canvasY, MenuAction.WALK.getId(), 0, -1, "Walk here", "", -1, -1);
    }

    public static boolean walkFastCanvas(WorldPoint worldPoint) {
        return walkFastCanvas(worldPoint, true);
    }

    private static boolean walkFastCanvasOnScreenOnly(WorldPoint worldPoint, boolean toggleRun) {
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

    private static LocalPoint localPointForWorld(WorldPoint worldPoint) {
        if (worldPoint == null) {
            return null;
        }
        LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), worldPoint);
        if (Microbot.getClient().getTopLevelWorldView().isInstance() && localPoint == null) {
            localPoint = Rs2LocalPoint.fromWorldInstance(worldPoint);
        }
        return localPoint;
    }

    public static boolean walkFastCanvas(WorldPoint worldPoint, boolean toggleRun) {
        if (worldPoint == null) {
            log.debug("[Walker] walkFastCanvas rejected: null worldPoint");
            return false;
        }
        Rs2Player.toggleRunEnergy(toggleRun);
        Point canv;
        LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), worldPoint);

        if (Microbot.getClient().getTopLevelWorldView().isInstance() && localPoint == null) {
            localPoint = Rs2LocalPoint.fromWorldInstance(worldPoint);
        }

        if (localPoint == null) {
            WorldPoint playerLoc = Rs2Player.getWorldLocation();
            if (playerLoc != null
                    && playerLoc.getPlane() == worldPoint.getPlane()
                    && walkMiniMapToward(worldPoint, playerLoc, 13)) {
                return true;
            }
            log.debug("[Walker] walkFastCanvas localpoint null for {}", worldPoint);
            return false;
        }

        canv = Perspective.localToCanvas(Microbot.getClient(), localPoint, Microbot.getClient().getTopLevelWorldView().getPlane());

        int canvasX = canv != null ? canv.getX() : -1;
        int canvasY = canv != null ? canv.getY() : -1;

        //if the tile is not on screen, use minimap
        if (!Rs2Camera.isTileOnScreen(localPoint) || canvasX < 0 || canvasY < 0) {
            WorldPoint playerLoc = Rs2Player.getWorldLocation();
            if (playerLoc != null
                    && playerLoc.getPlane() == worldPoint.getPlane()
                    && walkMiniMapToward(worldPoint, playerLoc, 13)) {
                return true;
            }
            return Rs2Walker.walkMiniMap(worldPoint);
        }

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

    public static WorldPoint walkCanvas(WorldPoint worldPoint) {
        LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), worldPoint);
        if (localPoint == null) {
            log.error("Tried to walkCanvas but localpoint returned null");
            return null;
        }
        Point point = Perspective.localToCanvas(Microbot.getClient(), localPoint, Microbot.getClient().getTopLevelWorldView().getPlane());

        if (point == null) return null;

        Microbot.getMouse().click(point);

        return worldPoint;
    }

    /**
     * Gets the total amount of tiles to travel to destination
     * @param start source
     * @param destination destination
     * @return total amount of tiles
     */
    public static int getTotalTiles(WorldPoint start, WorldPoint destination) {
        if (Rs2PathApi.getPathfinderConfig().getTransports().isEmpty()) {
            Rs2PathApi.getPathfinderConfig().refresh();
        }
        Pathfinder pathfinder = new Pathfinder(Rs2PathApi.getPathfinderConfig(), start, destination);
        pathfinder.run();
        List<WorldPoint> path = pathfinder.getPath();
        if (path.isEmpty() || path.get(path.size() - 1).getPlane() != destination.getPlane()) return Integer.MAX_VALUE;
        // Create a WorldArea centered on the worldPoint by calculating the south-west corner
        WorldPoint pathPoint_SW = new WorldPoint(
                path.get(path.size() - 1).getX() - 2,
                path.get(path.size() - 1).getY() - 2,
                path.get(path.size() - 1).getPlane()
        );
        // Create a WorldArea centered on the worldPoint by calculating the south-west corner
        WorldPoint objectPoint_SW = new WorldPoint(
                destination.getX() - 2,
                destination.getY() - 2,
                destination.getPlane()
        );
        WorldArea pathArea = new WorldArea(pathPoint_SW, 5, 5);
        WorldArea objectArea = new WorldArea(objectPoint_SW, 5, 5);
        if (!pathArea.intersectsWith2D(objectArea)) {
            return Integer.MAX_VALUE;
        }

        return path.size();
    }

    /**
     * Calculates the total number of tiles from a given path to a destination.
     * This method validates that the path can actually reach the destination by checking
     * if the path's endpoint intersects with the destination area.
     *
     * @param path A list of WorldPoint objects representing the calculated path
     * @param destination The target WorldPoint destination to validate against
     * @return The total number of tiles in the path if valid, or Integer.MAX_VALUE if the path
     *         is empty, on different planes, or doesn't reach the destination
     */
    public static int getTotalTilesFromPath(List<WorldPoint> path, WorldPoint destination) {
        if (path.isEmpty() || path.get(path.size() - 1).getPlane() != destination.getPlane()) return Integer.MAX_VALUE;

        // Create centered WorldAreas instead of corner-based
        WorldPoint pathEndpoint = path.get(path.size() - 1);
        WorldPoint pathSouthWest = new WorldPoint(
                pathEndpoint.getX() - 4,
                pathEndpoint.getY() - 4,
                pathEndpoint.getPlane()
        );
        WorldArea pathArea = new WorldArea(pathSouthWest, 8, 8);

        WorldPoint destSouthWest = new WorldPoint(
                destination.getX() - 4,
                destination.getY() - 4,
                destination.getPlane()
        );
        WorldArea objectArea = new WorldArea(destSouthWest, 8, 8);

        if (!pathArea.intersectsWith2D(objectArea)) {
            return Integer.MAX_VALUE;
        }
        return path.size();
    }

    /**
     * Gets the total amount of tiles to travel to destination
     * @param destination destination
     * @return total amount of tiles
     */
    public static int getTotalTiles(WorldPoint destination) {
        return getTotalTiles(Rs2Player.getWorldLocation(), destination);
    }

    // takes an avg 200-300 ms
    // Used mainly for agility, might have to tweak this for other stuff
    public static boolean canReach(WorldPoint worldPoint, int sizeX, int sizeY, int pathSizeX, int pathSizeY,boolean useBankedItems) {
        boolean originalUseBankItems = Rs2PathApi.getPathfinderConfig().isUseBankItems();
        WorldArea pathArea = null;

        // Create centered WorldArea for the object instead of corner-based
        WorldPoint objectSouthWest = new WorldPoint(
                worldPoint.getX() - (sizeX + 2) / 2,
                worldPoint.getY() - (sizeY + 2) / 2,
                worldPoint.getPlane()
        );
        WorldArea objectArea = new WorldArea(objectSouthWest, sizeX + 2, sizeY + 2);

        try {
            Rs2PathApi.getPathfinderConfig().setUseBankItems(useBankedItems);
            Rs2PathApi.getPathfinderConfig().refresh(worldPoint);
            if (Rs2PathApi.getPathfinderConfig().getTransports().isEmpty()) {
                Rs2PathApi.getPathfinderConfig().refresh(worldPoint);
            }
            Pathfinder pathfinder = new Pathfinder(Rs2PathApi.getPathfinderConfig(), Rs2Player.getWorldLocation(), worldPoint);
            pathfinder.run();

            // Create centered WorldArea for the path endpoint instead of corner-based
            WorldPoint pathEndpoint = pathfinder.getPath().get(pathfinder.getPath().size() - 1);
            WorldPoint pathSouthWest = new WorldPoint(
                    pathEndpoint.getX() - pathSizeX / 2,
                    pathEndpoint.getY() - pathSizeY / 2,
                    pathEndpoint.getPlane()
            );
            pathArea = new WorldArea(pathSouthWest, pathSizeX, pathSizeY);
        } catch (Exception e) {
            log.trace("Exception in canReach: {} - ", e.getMessage(), e);
            return false;
        } finally {
            Rs2PathApi.getPathfinderConfig().setUseBankItems(originalUseBankItems);
            Rs2PathApi.getPathfinderConfig().refresh(worldPoint);
        }
        return pathArea != null ? pathArea.intersectsWith2D(objectArea) : false;
    }
    public static boolean canReach(WorldPoint worldPoint, int sizeX, int sizeY, int pathSizeX, int pathSizeY) {
        return canReach(worldPoint, sizeX, sizeY, pathSizeX, pathSizeY, false);
    }

    // takes an avg 200-300 ms
    // Used mainly for agility, might have to tweak this for other stuff
    public static boolean canReach(WorldPoint worldPoint, int sizeX, int sizeY) {
        return canReach(worldPoint, sizeX, sizeY, 3, 3);
    }

    /**
     * used for quest script interacting with object
     * also used for finding the nearest bank
     * @param worldPoint
     * @return
     */
    public static boolean canReach(WorldPoint worldPoint) {
        return canReach(worldPoint, 2, 2, 2, 2);
    }
    public static boolean canReach(WorldPoint worldPoint, int sizeX, int sizeY, boolean useBankedItems) {
        return canReach(worldPoint, sizeX, sizeY, 2, 2, useBankedItems);
    }
    public static boolean canReach(WorldPoint worldPoint, boolean useBankedItems) {
        return canReach(worldPoint, 2, 2, 2, 2, useBankedItems);
    }
    public static boolean canReach(WorldPoint worldPoint, int sizeX, int sizeY, boolean useBankedItems, int pathSizeX, int pathSizeY) {
        return canReach(worldPoint, sizeX, sizeY, pathSizeX, pathSizeY, useBankedItems);
    }


    /**
     * Retrieves the walk path from the player's current location to the specified target location.
     * @param start The starting `WorldPoint` from which the path should be calculated.
     * @param target The target `WorldPoint` to which the path should be calculated.
     * @return A list of `WorldPoint` objects representing the path from the player's current location to the target.
     */
    public static List<WorldPoint> getWalkPath(WorldPoint start, WorldPoint target) {
        long startTime = System.nanoTime();
        Rs2PathApi.getPathfinderConfig().refresh(target);
        long pathfinderStartTime = System.nanoTime();
        Pathfinder pathfinderLocal = new Pathfinder(Rs2PathApi.getPathfinderConfig(), start, target);
        pathfinderLocal.run();
        List<WorldPoint> path = pathfinderLocal.getPath();
        long pathfinderEndTime = System.nanoTime();
        long totalEndTime = System.nanoTime();
        double configTimeMs = (pathfinderStartTime - startTime) / 1_000_000.0;
        double pathfinderTimeMs = (pathfinderEndTime - pathfinderStartTime) / 1_000_000.0;
        double totalTimeMs = (totalEndTime - startTime) / 1_000_000.0;

        StringBuilder performanceLog = new StringBuilder();
        performanceLog.append("getWalkPath Performance: ")
                .append("Config: ").append(String.format("%.2f ms", configTimeMs))
                .append(", Pathfinder: ").append(String.format("%.2f ms", pathfinderTimeMs))
                .append(", Total: ").append(String.format("%.2f ms", totalTimeMs))
                .append(" | Path: ").append(start).append(" -> ").append(target)
                .append(" (").append(path.size()).append(" waypoints)");

        log.debug(performanceLog.toString());

        return path;
    }
    /**
     * Retrieves the walk path from the player's current location to the specified target location.
     *
     * @param target The target `WorldPoint` to which the path should be calculated.
     * @return A list of `WorldPoint` objects representing the path from the player's current location to the target.
     */
    public static List<WorldPoint> getWalkPath(WorldPoint target) {
        return getWalkPath(Rs2Player.getWorldLocation(), target);
    }

    /**
     * Retrieves all transports found along the given path starting from a specific index.
     * Uses the default preferred transport type of TELEPORTATION_ITEM.
     *
     * @param path A list of WorldPoint objects representing the path to analyze
     * @param indexOfStartPoint The starting index in the path to begin searching for transports
     * @return A list of Transport objects found along the path, prioritizing teleportation items
     */
    public static List<Transport> getTransportsForPath(List<WorldPoint> path, int indexOfStartPoint) {
        return getTransportsForPath(path, indexOfStartPoint, TransportType.TELEPORTATION_ITEM, false);
    }

    /**
     * Retrieves all transports found along the given path starting from a specific index.
     * Analyzes the path for available transport options, prioritizing the specified transport type.
     *
     * This method examines each point in the path starting from the given index and identifies
     * available transport options (teleportation items, spells, objects, etc.) that can be used
     * to optimize travel. Transport types are sorted with teleportation items getting highest priority.
     *
     * @param path A list of WorldPoint objects representing the path to analyze
     * @param indexOfStartPoint The starting index in the path to begin searching for transports
     * @param prefTransportType The preferred transport type to prioritize in the search
     * @return A list of Transport objects found along the path, sorted by transport type priority
     */
    public static List<Transport> getTransportsForPath(List<WorldPoint> path, int indexOfStartPoint, TransportType prefTransportType) {
        return getTransportsForPath(path, indexOfStartPoint, prefTransportType, false);
    }

    /**
     * Retrieves all transports found along the given path starting from a specific index.
     * Analyzes the path for available transport options, prioritizing the specified transport type.
     * This version applies filtering and requirement setup for transports that require items.
     *
     * This method examines each point in the path starting from the given index and identifies
     * available transport options (teleportation items, spells, objects, etc.) that can be used
     * to optimize travel. Transport types are sorted with teleportation items getting highest priority.
     *
     * @param path A list of WorldPoint objects representing the path to analyze
     * @param indexOfStartPoint The starting index in the path to begin searching for transports
     * @param prefTransportType The preferred transport type to prioritize in the search
     * @param applyFiltering Whether to apply transport filtering and requirement setup
     * @return A list of Transport objects found along the path, sorted by transport type priority
     */
    public static List<Transport> getTransportsForPath(List<WorldPoint> path, int indexOfStartPoint, TransportType prefTransportType, boolean applyFiltering) {
        List<Transport> transportList = new ArrayList<>();
        if (path == null || path.isEmpty() || indexOfStartPoint < 0 || indexOfStartPoint >= path.size()) {
            return transportList;
        }
        Map<WorldPoint, Integer> pathFirstIndex = buildPathFirstIndex(path);
        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        int currentIndex = indexOfStartPoint;

        // Loop through the path until the end
        while (currentIndex < path.size()) {
            WorldPoint currentPoint = path.get(currentIndex);
            // Get any transports that start at this point (or keyed by this point)
            Set<Transport> transportsAtPoint = Rs2PathApi.getTransports().get(currentPoint);
            if (transportsAtPoint == null || transportsAtPoint.isEmpty()) {
                currentIndex++;
                continue;
            }
            boolean foundTransport = false;
            // sort by type to prioritize teleportation items first, then other types
            List<Transport> orderedTransports = new ArrayList<>(transportsAtPoint);
            orderedTransports.sort(Comparator.comparing(Transport::getType, (type1, type2) -> {
                // sort teleportation items by preference transport type for the current path point.
                if (type1 == prefTransportType && type2 != prefTransportType) {
                    return -1;
                }
                if (type2 == prefTransportType && type1 != prefTransportType) {
                    return 1;
                }
                // For all other types, use natural enum ordering
                return type1.compareTo(type2);
            }));
            // Iterate over each available transport
            for (Transport transport : orderedTransports) {

                // Special handling for teleportation-like transports (originless)
                // NOTE: Leagues "Area" teleports are injected as SEASONAL_TRANSPORT with null origin.
                String di = transport.getDisplayInfo();
                boolean isLeaguesAreaTeleport = transport.getType() == TransportType.SEASONAL_TRANSPORT
                        && di != null
                        && di.toLowerCase().startsWith("leagues area:");

                if (transport.getType() == TransportType.TELEPORTATION_ITEM
                        || transport.getType() == TransportType.TELEPORTATION_SPELL
                        || isLeaguesAreaTeleport)
                {
                    // For teleportation, we assume origin is null and simply check if the destination exists in the path.
                    Integer destIndex = pathFirstIndex.get(transport.getDestination());
                    if (destIndex != null) {
                        transportList.add(transport);
                        // Advance the current index to the destination tile (or at least one forward)
                        currentIndex = destIndex > currentIndex ? destIndex : currentIndex + 1;
                        foundTransport = true;
                        break;
                    }
                }

                // For non-teleportation transports (or if teleportation had a valid origin, though typically null):
                Collection<WorldPoint> originPoints;
                if (transport.getOrigin() == null) {
                    originPoints = Collections.singleton(null);
                } else {
                    originPoints = WorldPoint.toLocalInstance(
                            Microbot.getClient().getTopLevelWorldView(), transport.getOrigin());
                }

                for (WorldPoint origin : originPoints) {
                    // If an origin is defined but the player's plane doesn't match, skip it.
                    if (transport.getOrigin() != null && playerLoc != null
                            && playerLoc.getPlane() != transport.getOrigin().getPlane()) {
                        continue;
                    }

                    // For non-teleportation transports, ensure both origin and destination exist in the path
                    // and that the destination comes after the origin.
                    Integer indexOfDestinationValue = pathFirstIndex.get(transport.getDestination());
                    int indexOfDestination = indexOfDestinationValue != null ? indexOfDestinationValue : -1;
                    if (transport.getType() != TransportType.TELEPORTATION_ITEM
                            && transport.getType() != TransportType.TELEPORTATION_SPELL
                            && !isLeaguesAreaTeleport) {
                        Integer indexOfOriginValue = pathFirstIndex.get(transport.getOrigin());
                        int indexOfOrigin = indexOfOriginValue != null ? indexOfOriginValue : -1;
                        if (indexOfOrigin == -1 || indexOfDestination == -1 || indexOfDestination < indexOfOrigin) {
                            continue;
                        }
                    }

                    // If the current path point equals the transport's origin then add it.
                    if (currentPoint.equals(origin)) {
                        transportList.add(transport);
                        currentIndex = indexOfDestination > currentIndex ? indexOfDestination : currentIndex + 1;
                        foundTransport = true;
                        break;
                    }
                }
                if (foundTransport) {
                    break;
                }
            }

            if (!foundTransport) {
                currentIndex++;
            }
        }

        WebWalkLog.bankPathTransportsDebug(transportList.size(), path.get(0), path.get(path.size() - 1));

        // Apply filtering and requirement setup if requested
        if (applyFiltering) {
            transportList = applyTransportFiltering(transportList);
        }

        return transportList;
    }

    private static Map<WorldPoint, Integer> buildPathFirstIndex(List<WorldPoint> path) {
        Map<WorldPoint, Integer> pathFirstIndex = new HashMap<>(path.size());
        for (int i = 0; i < path.size(); i++) {
            pathFirstIndex.putIfAbsent(path.get(i), i);
        }
        return pathFirstIndex;
    }

    /**
     * Applies transport filtering and requirement setup for transport items.
     * This method filters transports to only include those that require items and
     * sets up item requirements for fairy rings and currency-based transports.
     *
     * @param transports The list of transports to filter and process
     * @return The filtered and processed list of transports
     */
    private static List<Transport> applyTransportFiltering(List<Transport> transports) {
        return transports.stream()
                .filter(t -> t.getType() == TransportType.TELEPORTATION_ITEM || t.getType() == TransportType.FAIRY_RING ||
                        t.getType() == TransportType.TELEPORTATION_SPELL || t.getType() == TransportType.CANOE ||
                        t.getType() == TransportType.BOAT || t.getType() == TransportType.CHARTER_SHIP ||
                        t.getType() == TransportType.SHIP || t.getType() == TransportType.MINECART ||
                        t.getType() == TransportType.MAGIC_CARPET || t.getType() == TransportType.SPIRIT_TREE ||
                        (t.getType() == TransportType.TRANSPORT && t.getCurrencyAmount() > 0) ||
						(t.getType() == TransportType.SEASONAL_TRANSPORT
								&& Rs2LeaguesTransport.isLeaguesActive()
								&& t.getDisplayInfo() != null
								&& t.getDisplayInfo().toLowerCase().startsWith("leagues area:")))
                .peek(t -> {
                    // Set fairy ring requirements if not already set
                    if (t.getType() == TransportType.FAIRY_RING &&
                            ((t.getItemIdRequirements() == null || t.getItemIdRequirements().isEmpty()) ) &&  Microbot.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE)  != 1) {
                        t.setItemIdRequirements(Set.of(Set.of(
                                ItemID.DRAMEN_STAFF,
                                ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF
                        )));
                    }

                    // Set currency requirements for currency-based transports
                    if (isCurrencyBasedTransport(t.getType()) &&
                            (t.getItemIdRequirements() == null || t.getItemIdRequirements().isEmpty()) &&
                            t.getCurrencyName() != null && !t.getCurrencyName().isEmpty() && t.getCurrencyAmount() > 0) {
                        int currencyItemId = getCurrencyItemId(t.getCurrencyName());
                        if (currencyItemId != -1) {
                            t.setItemIdRequirements(Set.of(Set.of(currencyItemId)));
                            log.debug("Set currency requirement for {}: {} x{} (ID: {})",
                                    t.getType(), t.getCurrencyName(), t.getCurrencyAmount(), currencyItemId);
                        }
                    }
                })
                .collect(Collectors.toList());
    }



    public static boolean isCloseToRegion(int distance, int regionX, int regionY) {
        WorldPoint worldPoint = WorldPoint.fromRegion(Rs2Player.getWorldLocation().getRegionID(),
                regionX,
                regionY,
                Microbot.getClient().getTopLevelWorldView().getPlane());

        return worldPoint.distanceTo(Rs2Player.getWorldLocation()) < distance;
    }

    public static int distanceToRegion(int regionX, int regionY) {
        WorldPoint worldPoint = WorldPoint.fromRegion(Rs2Player.getWorldLocation().getRegionID(),
                regionX,
                regionY,
                Microbot.getClient().getTopLevelWorldView().getPlane());

        return worldPoint.distanceTo(Rs2Player.getWorldLocation());
    }

    /**
     * Whether a rockfall could plausibly sit at {@code path[index]} or {@code path[index + 1]}, used
     * to gate the (comparatively expensive) scene lookup in {@link #handleRockfall}.
     *
     * <p>Rockfalls only exist in the Motherlode Mine, so the region check is legitimate — but it must
     * test where the rockfall could <em>be</em>: the player's tile, or the path tiles about to be
     * inspected. It must never test the walk TARGET. Gating on the target meant a walk out of the
     * mine — destination in another region, e.g. the Dwarven Mine at 12185 — returned false before
     * looking at anything, so no outbound route could clear a rockfall standing directly in it.
     */
    static boolean isMotherlodeRockfallCandidate(WorldPoint playerLoc, List<WorldPoint> path, int index) {
        if (path == null || path.isEmpty() || index < 0 || index >= path.size()) {
            return false;
        }
        if (playerLoc != null && playerLoc.getRegionID() == MOTHERLODE_MINE_REGION) {
            return true;
        }
        int lastCandidate = Math.min(index + 1, path.size() - 1);
        for (int i = index; i <= lastCandidate; i++) {
            WorldPoint candidate = path.get(i);
            if (candidate != null && candidate.getRegionID() == MOTHERLODE_MINE_REGION) {
                return true;
            }
        }
        return false;
    }

    private static boolean handleRockfall(List<WorldPoint> path, int index) {
        if (Rs2PathApi.getPathfinder() == null) return false;

        if (index == path.size() - 1) return false;

        // If we are in instance, ignore checking RegionID
        if(Microbot.getClient().getTopLevelWorldView().isInstance()) return false;

        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        if (playerLoc == null) return false;

        final int lastCandidate = Math.min(index + 1, path.size() - 1);
        if (!isMotherlodeRockfallCandidate(playerLoc, path, index)) return false;

        // Check current index & next index for rockfall
        for (int rockIndex = index; rockIndex <= lastCandidate; rockIndex++) {
            var point = path.get(rockIndex);
            if (point == null || point.getRegionID() != MOTHERLODE_MINE_REGION) continue;

            TileObject object = null;
            var tile = Rs2GameObject.getTiles(3).stream()
                    .filter(x -> x.getWorldLocation().equals(point))
                    .findFirst().orElse(null);

            if (tile != null)
                object = Rs2GameObject.getGameObject(point);

            if (object == null) continue;

            if (object.getId() == ObjectID.MOTHERLODE_ROCKFALL_1 || object.getId() == ObjectID.MOTHERLODE_ROCKFALL_2) {
                // Only abandon the walk once a rockfall is actually in the way. Checking for a
                // pickaxe up front killed any route merely passing through the mine.
                if (!Rs2Inventory.hasItem("pickaxe") && !Rs2Equipment.isWearing("pickaxe")) {
                    log.error("Unable to find pickaxe to mine rockfall at {}", point);
                    setTarget(null, "rs2walker:motherlode-rockfall-no-pickaxe");
                    return false;
                }
                Rs2GameObject.interact(object, "mine");
                return sleepUntil(() -> Rs2GameObject.getGameObject(point) == null);
            }
        }

        return false;
    }

    private static WalkerState tryDirectShortWalk(WorldPoint target,
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
        long suppressUntil = suppressTryDirectShortWalkUntilMs;
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

    private static int rawAnchorIndexForPathPosition(List<WorldPoint> rawPath,
                                                     List<WorldPoint> path,
                                                     WorldPoint playerLoc) {
        int closestPathIdx = getClosestTileIndex(path, playerLoc);
        int[] smoothedToRaw = mapSmoothedToRaw(path, rawPath);
        int rawAnchorIndex = rawIndexForSmoothedIndex(closestPathIdx, smoothedToRaw, rawPath);
        return rawPathForwardAnchorIndex(rawPath, playerLoc, rawAnchorIndex);
    }

    private static boolean clickRouteBackedShortWalk(List<WorldPoint> rawPath,
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

    private static boolean hasPendingExplicitTransportStepBeforeArrival(List<WorldPoint> path,
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

    private static boolean localRouteDetoursFromComputedRoute(List<WorldPoint> rawPath,
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

    private static boolean hasPendingDoorLikeSceneObjectBeforeDirectClick(List<WorldPoint> rawPath,
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
            if (isCatalogBackedTransportSegment(route, i) && !isDoorLikeCatalogTransportSegment(route, i)) {
                continue;
            }
            if (hasDoorLikeSceneObjectOnSegment(from, to, playerLoc, radius)) {
                return true;
            }
        }
        return false;
    }

    private static boolean handlePendingDoorBeforeRouteClick(List<WorldPoint> rawPath,
                                                             List<WorldPoint> path,
                                                             int fromPathIdx,
                                                             int targetPathIdx,
                                                             int[] smoothedToRaw,
                                                             long timeoutMs,
                                                             Map<String, WorldPoint> attempted,
                                                             WorldPoint playerLoc) {
        if (rawPath == null || rawPath.size() < 2 || path == null || path.isEmpty()
                || playerLoc == null || targetPathIdx < fromPathIdx) {
            return false;
        }
        if (Rs2Player.isMoving()) {
            return false;
        }

        int rawStart = rawIndexForSmoothedIndex(fromPathIdx, smoothedToRaw, rawPath);
        int rawTarget = rawIndexForSmoothedIndex(targetPathIdx, smoothedToRaw, rawPath);
        if (rawStart < 0 || rawTarget < 0) {
            return false;
        }

        int from = Math.max(0, Math.min(rawStart, rawTarget) - 2);
        int toExclusive = Math.min(rawPath.size() - 1, Math.max(rawStart, rawTarget) + 1);
        for (int ri = from; ri < toExclusive && ri < rawPath.size() - 1; ri++) {
            WorldPoint a = rawPath.get(ri);
            WorldPoint b = rawPath.get(ri + 1);
            if (a == null || b == null) {
                continue;
            }
            if (a.getPlane() != playerLoc.getPlane() || b.getPlane() != playerLoc.getPlane()) {
                break;
            }
            if (a.distanceTo2D(playerLoc) > HANDLER_RANGE && b.distanceTo2D(playerLoc) > HANDLER_RANGE) {
                continue;
            }
            if (isCatalogBackedTransportSegment(rawPath, ri) && !isDoorLikeCatalogTransportSegment(rawPath, ri)) {
                continue;
            }
            if (!hasDoorLikeSceneObjectOnSegment(a, b, playerLoc, HANDLER_RANGE)) {
                continue;
            }
            if (handleDoorsWithTimeout(rawPath, ri, timeoutMs, attempted, true)) {
                return true;
            }
        }
        return false;
    }

    private static boolean handlePendingDoorDuringInterim(List<WorldPoint> rawPath,
                                                          long timeoutMs,
                                                          Map<String, WorldPoint> attempted,
                                                          WorldPoint playerLoc) {
        if (rawPath == null || rawPath.size() < 2 || playerLoc == null
                || isDoorInteractionSettling() || isDoorEdgePassSkipCoolingDown()
                || isRecoveryMovementInFlight() || Rs2Player.isMoving()) {
            return false;
        }

        return handlePendingDoorNearRawPath(rawPath, timeoutMs, attempted, playerLoc, 2, 14);
    }

    private static boolean handlePendingDoorNearRawPath(List<WorldPoint> rawPath,
                                                        long timeoutMs,
                                                        Map<String, WorldPoint> attempted,
                                                        WorldPoint playerLoc,
                                                        int backtrackEdges,
                                                        int lookaheadEdges) {
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
            if (a.distanceTo2D(playerLoc) > HANDLER_RANGE && b.distanceTo2D(playerLoc) > HANDLER_RANGE) {
                continue;
            }
            if (isCatalogBackedTransportSegment(rawPath, ri) && !isDoorLikeCatalogTransportSegment(rawPath, ri)) {
                continue;
            }
            if (!hasDoorLikeSceneObjectOnSegment(a, b, playerLoc, HANDLER_RANGE)) {
                continue;
            }
            if (handleDoorsWithTimeout(rawPath, ri, timeoutMs, attempted, true)) {
                return true;
            }
        }
        return false;
    }

    private static boolean handleUnresolvedDoorNearRawPath(List<WorldPoint> rawPath,
                                                           int rawEdgeStart,
                                                           long timeoutMs,
                                                           Map<String, WorldPoint> attempted,
                                                           WorldPoint playerLoc,
                                                           int backtrackEdges,
                                                           int lookaheadEdges,
                                                           int radiusTiles) {
        if (rawPath == null || rawPath.size() < 2 || rawEdgeStart < 0 || playerLoc == null || Rs2Player.isMoving()) {
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
            if (isCatalogBackedTransportSegment(rawPath, ri) && !isDoorLikeCatalogTransportSegment(rawPath, ri)) {
                continue;
            }
            if (!hasUnresolvedDoorLikeSceneObjectOnSegment(from, to, playerLoc, radiusTiles)) {
                continue;
            }
            if (handleDoorsWithTimeout(rawPath, ri, timeoutMs, attempted, true)) {
                return true;
            }
        }
        return false;
    }

    private static int rawIndexForSmoothedIndex(int smoothedIdx, int[] smoothedToRaw, List<WorldPoint> rawPath) {
        if (rawPath == null || rawPath.isEmpty()) {
            return -1;
        }
        if (smoothedToRaw != null && smoothedIdx >= 0 && smoothedIdx < smoothedToRaw.length) {
            return Math.max(0, Math.min(smoothedToRaw[smoothedIdx], rawPath.size() - 1));
        }
        int closest = getClosestTileIndex(rawPath);
        return closest >= 0 ? closest : -1;
    }

    private static boolean handleNearbyRawPathSceneObjects(List<WorldPoint> rawPath, int handlerRange, WorldPoint target) {
        return handleNearbyRawPathSceneObjects(rawPath, handlerRange, target, true);
    }

    private static boolean handleNearbyRawPathSceneObjects(List<WorldPoint> rawPath,
                                                          int handlerRange,
                                                          WorldPoint target,
                                                          boolean allowTransportHandlers) {
        if (rawPath == null || rawPath.size() < 2) {
            return false;
        }

        if (isRecoveryMovementInFlight()) {
            return false;
        }

        if (interimTargetWp != null) {
            clearRawScanDoorFocus("interim-active");
            return false;
        }

        if (Rs2Player.isMoving()) {
            return false;
        }

        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        if (playerLoc == null) {
            return false;
        }

        int rawStart = getClosestTileIndex(rawPath, playerLoc);
        if (rawStart < 0) {
            clearRawScanDoorFocus("raw-start-missing");
            return false;
        }

        if (shouldUseFocusedRawDoorIndex(rawPath, rawStart)) {
            int idx = rawScanFocusedDoorIdx;
            rawScanFocusedDoorAttempts++;
            if (handleDoors(rawPath, idx, true)) {
                log.info("[Walker] Raw path focused door handler resolved obstacle near {}", playerLoc);
                return true;
            }
            return false;
        }
        clearRawScanDoorFocus("focus-invalid");

        int start = Math.max(0, rawStart - 2);
        int endExclusive = Math.min(rawPath.size() - 1, rawStart + 12);
        // Per-stage timing: this scan has been measured at 5.6s returning handled=false after a
        // transport (each probe does several client-thread scene lookups). Attribute the cost so a
        // single log line shows which handler dominates instead of needing another repro round.
        final long scanStartMs = System.currentTimeMillis();
        int scannedIdx = 0;
        long snapshotMs = 0L;
        long transportMs = 0L;
        long doorMs = 0L;
        long doorCandidateMs = 0L;
        long rockfallMs = 0L;
        boolean resolved = false;
        // Probes stay within handlerRange+1 of the player (the loop skips indices beyond
        // handlerRange) and only match objects within one tile of a probe, so a handlerRange+2
        // radius is a superset of what the per-probe queries could have found.
        final int snapshotRadius = handlerRange + 2;
        long snapshotStartedAt = System.currentTimeMillis();
        rawScanWallSnapshot = Rs2GameObject.getWallObjects(o -> true, playerLoc, snapshotRadius);
        rawScanGameObjectSnapshot = Rs2GameObject.getGameObjects(o -> true, playerLoc, snapshotRadius);
        rawScanDoorLocationSnapshot = Microbot.getClientThread()
                .invoke(Rs2Walker::captureRawScanDoorLocationsOnClientThread);
        snapshotMs = System.currentTimeMillis() - snapshotStartedAt;
        rawScanDoorCompositionCache = new IdentityHashMap<>();
        rawScanDoorSegmentCache = new HashMap<>();
        rawScanDoorInteractionWaitMs = 0L;
        try {
            for (int i = start; i < endExclusive; i++) {
                WorldPoint currentWorldPoint = rawPath.get(i);
                if (currentWorldPoint == null
                        || currentWorldPoint.getPlane() != playerLoc.getPlane()
                        || currentWorldPoint.distanceTo2D(playerLoc) > handlerRange) {
                    continue;
                }
                scannedIdx++;

                if (allowTransportHandlers
                        && hasExplicitTransportStep(rawPath, i)
                        && isRawTransportOriginNearPlayer(
                        rawPath, i, playerLoc, RAW_TRANSPORT_DISPATCH_MAX_DISTANCE)) {
                    WorldPoint before = Rs2Player.getWorldLocation();
                    WorldPoint expectedDestination = i + 1 < rawPath.size() ? rawPath.get(i + 1) : null;
                    long t = System.currentTimeMillis();
                    boolean handledTransport = handleTransports(rawPath, i);
                    transportMs += System.currentTimeMillis() - t;
                    if (handledTransport) {
                        if (!didCurrentTileTransportProgress(before, expectedDestination, target)) {
                            WebWalkLog.spInfo("raw_path_transport_no_progress",
                                    "at=%s expected=%s target=%s",
                                    before, expectedDestination, target);
                        } else {
                            log.info("[Walker] Raw path transport handler resolved obstacle near {}", playerLoc);
                            resolved = true;
                            return true;
                        }
                    }
                }

                long t0 = System.currentTimeMillis();
                boolean handledDoor = handleDoors(rawPath, i, true);
                doorMs += System.currentTimeMillis() - t0;
                if (handledDoor) {
                    log.info("[Walker] Raw path door handler resolved obstacle near {}", playerLoc);
                    resolved = true;
                    return true;
                }
                long t1 = System.currentTimeMillis();
                boolean doorCandidate = hasDoorCandidateOnRawSegment(rawPath, i);
                doorCandidateMs += System.currentTimeMillis() - t1;
                if (doorCandidate) {
                    setRawScanDoorFocus(i);
                    return false;
                }

                long t2 = System.currentTimeMillis();
                boolean handledRockfall = handleRockfall(rawPath, i);
                rockfallMs += System.currentTimeMillis() - t2;
                if (handledRockfall) {
                    log.info("[Walker] Raw path rockfall handler resolved obstacle near {}", playerLoc);
                    resolved = true;
                    return true;
                }
            }

            return false;
        } finally {
            rawScanWallSnapshot = null;
            rawScanGameObjectSnapshot = null;
            rawScanDoorLocationSnapshot = null;
            rawScanDoorCompositionCache = null;
            rawScanDoorSegmentCache = null;
            long totalMs = System.currentTimeMillis() - scanStartMs;
            if (totalMs >= SLOW_RAW_SCENE_SCAN_LOG_MS) {
                long doorWaitMs = rawScanDoorInteractionWaitMs;
                long doorProbeMs = Math.max(0L, doorMs - doorWaitMs);
                log.info("[Walker] slow raw scene scan: total={}ms idx={} snapshot={}ms doorProbe={}ms doorWait={}ms doorCand={}ms rockfall={}ms transports={}ms resolved={} allowTransports={}",
                        totalMs, scannedIdx, snapshotMs, doorProbeMs, doorWaitMs, doorCandidateMs, rockfallMs, transportMs,
                        resolved, allowTransportHandlers);
            }
            rawScanDoorInteractionWaitMs = 0L;
        }
    }

    /** A raw-scene scan slower than this is user-visible dead time between route clicks. */
    private static final long SLOW_RAW_SCENE_SCAN_LOG_MS = 500L;

    /**
     * Scene snapshot scoped to one {@link #handleNearbyRawPathSceneObjects} pass.
     * <p>
     * Door probing previously issued up to four bounded scene queries <em>per probe</em>, and a scan
     * walks ~12 raw indices x 2 offsets x several probes — hundreds of client-thread round trips.
     * Measured live at 4564ms of a 5539ms scan that resolved nothing. Taking one bounded snapshot up
     * front and matching probes against it in memory collapses that to two queries per scan.
     * <p>
     * Null outside a raw scan, so every other {@code handleDoors} caller keeps the original
     * query-per-probe behaviour.
     */
    private static volatile List<WallObject> rawScanWallSnapshot = null;
    private static volatile List<GameObject> rawScanGameObjectSnapshot = null;
    /** Immutable locations copied on the client thread for off-thread snapshot filtering. */
    private static Map<TileObject, WorldPoint> rawScanDoorLocationSnapshot = null;
    /** Object definitions and segment matches are stable for one immutable scene snapshot. */
    private static Map<TileObject, Optional<ObjectComposition>> rawScanDoorCompositionCache = null;
    private static Map<String, Optional<TileObject>> rawScanDoorSegmentCache = null;
    /** Interaction/edge-resolution wait contained inside {@link #handleDoors}; excluded from probe cost. */
    private static volatile long rawScanDoorInteractionWaitMs = 0L;

    private static Map<TileObject, WorldPoint> captureRawScanDoorLocationsOnClientThread() {
        Map<TileObject, WorldPoint> locations = new IdentityHashMap<>();
        if (rawScanWallSnapshot != null) {
            for (WallObject wall : rawScanWallSnapshot) {
                if (wall != null) {
                    locations.put(wall, ((TileObject) wall).getWorldLocation());
                }
            }
        }
        if (rawScanGameObjectSnapshot != null) {
            for (GameObject object : rawScanGameObjectSnapshot) {
                if (object != null) {
                    locations.put(object, ((TileObject) object).getWorldLocation());
                }
            }
        }
        return locations;
    }

    /**
     * Exact-tile match first, then a one-tile adjacency fallback — the same preference order the
     * previous pair of bounded queries produced.
     */
    private static WallObject resolveProbeWallObject(WorldPoint probe) {
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
    private static TileObject resolveProbeGameObject(WorldPoint probe) {
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

    private static boolean hasDoorCandidateOnRawSegment(List<WorldPoint> rawPath, int index) {
        if (rawPath == null || index < 0 || index >= rawPath.size() - 1) {
            return false;
        }
        if (isCatalogBackedTransportSegment(rawPath, index) && !isDoorLikeCatalogTransportSegment(rawPath, index)) {
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
        return findDoorNearSegment(fromWp, toWp, doorActions) != null;
    }

    private static void setRawScanDoorFocus(int index) {
        rawScanFocusedDoorIdx = index;
        rawScanFocusedDoorSetAtMs = System.currentTimeMillis();
        rawScanFocusedDoorAttempts = 0;
    }

    private static boolean shouldUseFocusedRawDoorIndex(List<WorldPoint> rawPath, int rawStartIdx) {
        Integer idx = rawScanFocusedDoorIdx;
        if (idx == null) {
            return false;
        }
        if (interimTargetWp != null) {
            return false;
        }
        if (System.currentTimeMillis() - rawScanFocusedDoorSetAtMs > RAW_SCAN_DOOR_FOCUS_MAX_MS) {
            return false;
        }
        if (rawScanFocusedDoorAttempts >= RAW_SCAN_DOOR_FOCUS_MAX_ATTEMPTS) {
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

    private static void clearRawScanDoorFocus(String reason) {
        if (rawScanFocusedDoorIdx != null && debug) {
            walkerDiag("clear raw door focus: %s", reason);
        }
        rawScanFocusedDoorIdx = null;
        rawScanFocusedDoorSetAtMs = 0L;
        rawScanFocusedDoorAttempts = 0;
    }

    private static boolean handleCurrentTileTransportTowardPath(List<WorldPoint> rawPath, List<WorldPoint> path, WorldPoint target) {
        if (Rs2Player.isMoving()) {
            return false;
        }
        if (isDoorEdgePassSkipCoolingDown() || isDoorInteractionSettling()) {
            return false;
        }

        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        if (playerLoc == null) {
            return false;
        }

        // Snappy proximity: consider usable transports whose origin is reachable within a few tiles
        // of the player, not just the one on the exact player tile. NPC/"Follow" transports (e.g. Elkoy
        // in the Tree Gnome Village maze) roam and sit a tile off the planned path, so exact-tile
        // matching never sees them. The destination-on-forward-route gate below keeps this safe against
        // off-path loops, and getTransports() is already the usable (config/quest/level-filtered) set,
        // so we never grab a transport the pathfinder excluded.
        final int NEARBY_TRANSPORT_REACH = 5;
        Map<WorldPoint, Set<Transport>> transportsByOrigin = Rs2PathApi.getTransports();
        Set<Transport> transports = new HashSet<>();
        Set<Transport> transportsOnPlayerTile = transportsByOrigin.get(playerLoc);
        if (transportsOnPlayerTile != null) {
            transports.addAll(transportsOnPlayerTile);
        }
        for (WorldPoint reachableTile : Rs2Tile.getReachableTilesFromTile(playerLoc, NEARBY_TRANSPORT_REACH).keySet()) {
            Set<Transport> ts = transportsByOrigin.get(reachableTile);
            if (ts != null) {
                transports.addAll(ts);
            }
        }
        if (transports.isEmpty()) {
            return false;
        }

        Map<WorldPoint, Integer> forwardIndex = new HashMap<>();
        addForwardPathIndices(forwardIndex, rawPath, playerLoc);
        addForwardPathIndices(forwardIndex, path, playerLoc);

        WorldPoint priorOrigin = lastTransportOriginLocation;
        // Trust the pathfinder: only take a nearby transport whose destination is on the
        // planned forward route, ordered by route position (earliest forward transport first). The
        // old fallback admitted off-path transports whose destination was straight-line "closer" to
        // the goal — but WorldPoint#distanceTo ignores the underground Y-offset, so an inner-region
        // tile reads numerically closer to a surface goal. That made the walker re-take transports
        // the pathfinder never chose: it looped forever on the Mor Ul Rek cave entrance/exit and
        // stalled clicking the Fossil Island rowboat. The pathfinder already routed every transport
        // it wants onto the path, so on-route membership is the correct, region-safe admission test.
        List<Transport> candidates = transports.stream()
                .filter(t -> t.getDestination() != null)
                // Local adjacent same-plane edges (doors/gates) are handled by segment door/object
                // logic; current-tile transport probing can bounce on these and create loops.
                .filter(t -> !isAdjacentSamePlaneTransport(t))
                .filter(t -> priorOrigin == null
                        || !t.getDestination().equals(priorOrigin))
                .filter(t -> target == null
                        || playerLoc.getPlane() != target.getPlane()
                        || t.getDestination().getPlane() == target.getPlane())
                .filter(t -> forwardIndex.containsKey(t.getDestination()))
                .sorted(Comparator.comparingInt(t -> forwardIndex.get(t.getDestination())))
                .collect(Collectors.toList());

        for (Transport transport : candidates) {
            WorldPoint origin = transport.getOrigin() != null ? transport.getOrigin() : playerLoc;
            if (shouldThrottleCurrentTileTransportAttempt(origin, transport.getDestination())) {
                continue;
            }
            markCurrentTileTransportAttempt(origin, transport.getDestination());
            WorldPoint before = Rs2Player.getWorldLocation();
            // Pass the transport's own origin so handleTransports walks the short hop to it before
            // interacting (NPC dispatch already auto-walks via canWalkTo + interact); object/door
            // interactions that can't be reached from here simply return false and we fall through.
            if (handleTransports(Arrays.asList(origin, transport.getDestination()), 0)) {
                if (didCurrentTileTransportProgress(before, transport.getDestination(), target)) {
                    log.info("[Walker] Nearby transport handler resolved obstacle: origin={} dest={} (player {})",
                            origin, transport.getDestination(), playerLoc);
                    return true;
                }
                WebWalkLog.spInfo(
                        "current_tile_transport_no_progress | origin={} dest={} before={} after={} goal={}",
                        compactWorldPoint(origin),
                        compactWorldPoint(transport.getDestination()),
                        compactWorldPoint(before),
                        compactWorldPoint(Rs2Player.getWorldLocation()),
                        compactWorldPoint(target));
            }
        }

        return false;
    }

    private static boolean didCurrentTileTransportProgress(WorldPoint before, WorldPoint expectedDestination, WorldPoint target) {
        return Rs2WalkerTransportAwaits.didCurrentTileTransportProgress(before, expectedDestination, target);
    }

    // Maps each tile on the planned route at/after the player's closest index to its route position.
    // Earliest index wins (putIfAbsent) so the raw path's index space is authoritative when the same
    // tile appears in both the raw and smoothed paths (the smoothed path is a subset of the raw one).
    private static void addForwardPathIndices(Map<WorldPoint, Integer> forwardIndex, List<WorldPoint> path, WorldPoint playerLoc) {
        if (path == null || path.isEmpty() || playerLoc == null) {
            return;
        }

        int closestIndex = 0;
        int closestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < path.size(); i++) {
            WorldPoint point = path.get(i);
            if (point == null) {
                continue;
            }
            int distance = playerLoc.distanceTo(point);
            if (distance < closestDistance) {
                closestDistance = distance;
                closestIndex = i;
            }
        }
        for (int i = closestIndex; i < path.size(); i++) {
            forwardIndex.putIfAbsent(path.get(i), i);
        }
    }

    // Session-local set of door tiles the walker detected as quest/stat-locked after a
    // failed interact. Cleared when the client restarts. Prevents infinite retry loops
    // through the same restricted door when the restriction isn't in restrictions.tsv.
    static final Set<WorldPoint> sessionBlacklistedDoors = ConcurrentHashMap.newKeySet();
    private static final Map<WorldPoint, Long> recentlyOpenedStationaryDoors = new ConcurrentHashMap<>();
    private static final long STATIONARY_DOOR_SUPPRESS_MS = 10_000;
    private static final Map<String, Long> recentDoorAttemptByEdge = new ConcurrentHashMap<>();
    private static final long DOOR_ATTEMPT_EDGE_COOLDOWN_MS = 2_500;
    private static volatile WorldPoint lastDoorAttemptFrom = null;
    private static volatile WorldPoint lastDoorAttemptTo = null;
    private static volatile long lastDoorAttemptAtMs = 0L;
    private static final Map<String, Long> recentCurrentTileTransportByEdge = new ConcurrentHashMap<>();
    private static final long CURRENT_TILE_TRANSPORT_EDGE_COOLDOWN_MS = 2_200;
    private static final long DOOR_INTERACTION_GLOBAL_COOLDOWN_MS = 1_800;
    private static volatile long nextDoorInteractionAllowedAtMs = 0L;

    static boolean hasQuestLockKeywords(String text) {
        if (text == null || text.isEmpty()) return false;
        String lc = text.toLowerCase();
        // Phrases that consistently appear on quest/stat-gated doors and gates.
        return lc.contains("quest") || lc.contains("you need to") || lc.contains("you must")
                || lc.contains("you have not") || lc.contains("cannot enter")
                || lc.contains("can't enter") || lc.contains("requires you");
    }

    private static boolean isQuestLockedDoorDialogue() {
        if (!Rs2Dialogue.isInDialogue()) return false;
        return hasQuestLockKeywords(Rs2Dialogue.getDialogueText());
    }

    /**
     * Rank sidestep-recovery candidate tiles by Chebyshev distance to the walk target so
     * the random pick biases toward the goal instead of wandering. Pure function — no
     * dependency on client state; safe to unit-test.
     */
    static List<WorldPoint> rankSidestepTilesToward(Collection<WorldPoint> reachable, WorldPoint target) {
        if (reachable == null || reachable.isEmpty()) return Collections.emptyList();
        return reachable.stream()
                .sorted(Comparator.comparingInt(t -> t.distanceTo(target)))
                .collect(Collectors.toList());
    }

    /**
     * Given a path and a starting index, return the index of the furthest path tile that:
     *  - is on the same plane as {@code path.get(startIdx)}
     *  - is not a transport origin (per {@code isTransportOrigin})
     *  - lies within {@code maxEuclidean} 2D Euclidean distance of {@code playerLoc}
     *
     * <p>Euclidean (not Chebyshev) because the minimap clickable area is a circle: a
     * Chebyshev-bounded cap either wastes reach on cardinal directions (where the circle
     * extends to ~{@code maxEuclidean}) or lets diagonal clicks escape the disk (where
     * Chebyshev-{@code maxEuclidean} is {@code maxEuclidean}·√2 away).
     *
     * <p>If {@code path.get(startIdx)} itself is already beyond reach — which happens
     * when the player has drifted off path and the next smoothed waypoint is out of
     * minimap range — the function scans <em>backward</em> for the latest in-range path
     * tile. Clicking that earlier tile brings the player back onto the path so forward
     * progress can resume; without this, the walker would spam off-minimap clicks
     * against {@code path.get(startIdx)} until the 10-second stall-recalc fires.
     */
    static int findFurthestClickableIndex(List<WorldPoint> path, int startIdx, WorldPoint playerLoc,
                                          java.util.function.Predicate<WorldPoint> isTransportOrigin,
                                          int maxEuclidean) {
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

    static int findFurthestForwardClickableIndex(List<WorldPoint> path, int startIdx, WorldPoint playerLoc,
                                                 java.util.function.Predicate<WorldPoint> isTransportOrigin,
                                                 int maxEuclidean) {
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

    private static int findForwardReachableRecoveryIndex(List<WorldPoint> path,
                                                         int startIdx,
                                                         WorldPoint playerLoc,
                                                         int maxEuclidean) {
        Set<WorldPoint> reachable = playerLoc == null
                ? Collections.emptySet()
                : Rs2Tile.getReachableTilesFromTile(playerLoc, Math.max(2, maxEuclidean)).keySet();
        return findForwardRecoveryIndex(
                path,
                startIdx,
                playerLoc,
                maxEuclidean,
                reachable,
                Rs2Walker::isMiniMapRecoveryClickable);
    }

    static int findForwardRecoveryIndex(List<WorldPoint> path,
                                        int startIdx,
                                        WorldPoint playerLoc,
                                        int maxEuclidean,
                                        Set<WorldPoint> reachable,
                                        java.util.function.Predicate<WorldPoint> isClickable) {
        if (path == null || path.isEmpty() || playerLoc == null || startIdx < 0 || startIdx >= path.size()) {
            return -1;
        }
        int maxSq = maxEuclidean * maxEuclidean;
        int endExclusive = Math.min(path.size(), startIdx + UNREACHABLE_RECOVERY_FORWARD_SCAN_TILES + 1);
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

    private static boolean isMiniMapRecoveryClickable(WorldPoint worldPoint) {
        return isMiniMapClickable(worldPoint, 5);
    }

    static WorldPoint interpolateClickableTarget(List<WorldPoint> path,
                                                 int forwardIdx,
                                                 WorldPoint playerLoc,
                                                 WorldPoint fallbackWp,
                                                 int targetEuclidean,
                                                 java.util.function.Predicate<WorldPoint> isUsableClickTarget) {
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

    static WorldPoint clampToEuclideanRadius(WorldPoint playerLoc, WorldPoint target, int maxEuclidean) {
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

    private static int euclideanSq(WorldPoint a, WorldPoint b) {
        int dx = a.getX() - b.getX();
        int dy = a.getY() - b.getY();
        return dx * dx + dy * dy;
    }

    private static ObjectComposition resolveCompositionForDoorProbe(TileObject object) {
        ObjectComposition comp = Rs2GameObject.convertToObjectComposition(object);
        if (comp == null) {
            return null;
        }
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            ObjectComposition c = comp;
            for (int depth = 0; depth < 4 && c != null && c.getImpostorIds() != null; depth++) {
                c = c.getImpostor();
            }
            return c;
        }).orElse(comp);
    }

    private static boolean isNullOrPlaceholderObjectName(String name) {
        if (name == null) {
            return true;
        }
        String t = name.trim();
        return t.isEmpty() || "null".equalsIgnoreCase(t);
    }

    private static int doorActionPriorityIndex(String action) {
        if (action == null) {
            return Integer.MAX_VALUE;
        }
        String al = action.toLowerCase(Locale.ROOT);
        for (int i = 0; i < DOOR_ACTION_PRIORITY.size(); i++) {
            if (al.startsWith(DOOR_ACTION_PRIORITY.get(i))) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    /** Walker must never choose menu actions that close an open door/gate. */
    private static boolean isDoorCloseOrShutAction(String action) {
        if (action == null) {
            return false;
        }
        String al = action.toLowerCase(Locale.ROOT).trim();
        return al.startsWith("close") || al.startsWith("shut");
    }

    /** True when every non-null action is Close/Shut (typical open-door state). */
    private static boolean doorCompositionSpecifiesOnlyCloseOrShut(ObjectComposition comp) {
        if (comp == null || comp.getActions() == null) {
            return false;
        }
        boolean sawNonNull = false;
        for (String a : comp.getActions()) {
            if (a == null) {
                continue;
            }
            sawNonNull = true;
            if (!isDoorCloseOrShutAction(a)) {
                return false;
            }
        }
        return sawNonNull;
    }

    /**
     * Best door action for walking through, excluding close/shut. {@code null} if none
     * (empty defs or only close/shut).
     */
    private static String pickWalkDoorAction(ObjectComposition comp) {
        if (comp == null || comp.getActions() == null) {
            return null;
        }
        return Arrays.stream(comp.getActions())
                .filter(Objects::nonNull)
                .filter(a -> !isDoorCloseOrShutAction(a))
                .min(Comparator.comparingInt(Rs2Walker::doorActionPriorityIndex))
                .orElse(null);
    }

    private static boolean isDoorLikeGameObjectName(String name) {
        if (name == null) {
            return false;
        }
        String n = name.toLowerCase(Locale.ROOT);
        for (String f : DOOR_LIKE_NAME_FRAGMENTS) {
            if ("fence".equals(f)) {
                if (FENCE_AS_WORD.matcher(n).find()) {
                    return true;
                }
            } else if (n.contains(f)) {
                return true;
            }
        }
        return false;
    }

    private static boolean handleDoors(List<WorldPoint> path, int index) {
        return handleDoors(path, index, false);
    }

    private static boolean handleDoors(List<WorldPoint> path, int index, boolean allowSegmentProbe) {
        if (Rs2PathApi.getPathfinder() == null || index >= path.size() - 1) return false;

        // Skip any door whose tile was blacklisted after a prior quest-lock detection —
        // avoid re-triggering the same failed interact loop this session.
        WorldPoint skipFrom = path.get(index);
        WorldPoint skipTo = index + 1 < path.size() ? path.get(index + 1) : null;
        if (sessionBlacklistedDoors.contains(skipFrom)
                || (skipTo != null && sessionBlacklistedDoors.contains(skipTo))) {
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

        if (isCatalogBackedTransportSegment(path, index) && !isDoorLikeCatalogTransportSegment(path, index)) {
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
            TileObject snapshotDoor = findDoorNearSegment(fromWp, toWp, doorActions);
            if (snapshotDoor == null) {
                return false;
            }
            if (snapshotDoor instanceof WallObject) {
                return tryHandleDoorObject(snapshotDoor, snapshotDoor.getWorldLocation(),
                        fromWp, toWp, doorActions, true);
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
                if (!isDoorInteractionWithinRange(object, probe, fromWp, toWp, playerLoc, HANDLER_RANGE)) {
                    Telemetry.recordDoorReject("door-out-of-range");
                    continue;
                }
                if (isCatalogTransportObject(object) && !isDoorLikeSceneObject(object)) {
                    Telemetry.recordDoorReject("catalog-transport-object");
                    continue;
                }

                ObjectComposition baseComp = Rs2GameObject.convertToObjectComposition(object);
                ObjectComposition comp = resolveCompositionForDoorProbe(object);
                if (comp == null) {
                    Telemetry.recordDoorReject("composition-null");
                    continue;
                }
                if (baseComp != null && baseComp.getImpostorIds() != null
                        && !isNullOrPlaceholderObjectName(baseComp.getName())
                        && isNullOrPlaceholderObjectName(comp.getName())) {
                    Telemetry.recordDoorReject("impostor-rejected");
                    continue;
                }
                if (isNullOrPlaceholderObjectName(comp.getName())) {
                    Telemetry.recordDoorReject("name-not-door");
                    continue;
                }

                if (doorCompositionSpecifiesOnlyCloseOrShut(comp)) {
                    Telemetry.recordDoorReject("skip-close-only-open");
                    continue;
                }

                String action = pickWalkDoorAction(comp);
                if (action == null) {
                    Telemetry.recordDoorReject("no-walk-action");
                    continue;
                }
                if (doorActionPriorityIndex(action) == Integer.MAX_VALUE) {
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
                    if (isDoorOnSegment(object, fromWp, toWp)) {
                        log.info("Found WallObject door - name {} with action {} at {} - from {} to {}", name, action, probe, fromWp, toWp);
                        found = true;
                    } else {
                        Telemetry.recordDoorReject("orient-mismatch");
                    }
                } else {
                    if (isDoorOnSegment(object, fromWp, toWp)) {
                        log.info("Found GameObject door - name {} with action {} at {} - from {} to {}", name, action, probe, fromWp, toWp);
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
                        if (shouldThrottleGlobalDoorInteraction()) {
                            WebWalkLog.spInfo("door_global_await | mode=segment-door probe={} from={} to={}",
                                    compactWorldPoint(probe), compactWorldPoint(fromWp), compactWorldPoint(toWp));
                            return false;
                        }
                        if (Rs2Player.isMoving()) {
                            WebWalkLog.spInfo("door_interact_deferred | reason=moving mode=segment-door probe={} from={} to={}",
                                    compactWorldPoint(probe), compactWorldPoint(fromWp), compactWorldPoint(toWp));
                            return false;
                        }
                        markDoorAttempt(probe, fromWp, toWp);
                        markGlobalDoorInteractionCooldown();
                        WorldPoint posBefore = Rs2Player.getWorldLocation();
                        boolean interacted;
                        try {
                            interacted = Rs2GameObject.interact(object, action);
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
                        markDoorInteractionSettling();
                        waitForDoorInteractionProgress(fromWp, toWp);
                        WorldPoint posAfter = Rs2Player.getWorldLocation();
                        boolean traversed = didTraverseInteractedDoor(posBefore, posAfter, probe, fromWp, toWp);
                        if (!traversed && isQuestLockedDoorDialogue()) {
                            String dialogue = Rs2Dialogue.getDialogueText();
                            log.warn("[Walker] Door at {} ({} action={}) appears quest/stat-locked — dialogue=\"{}\" — blacklisting tile, refreshing restrictions, recalculating",
                                    probe, name, action, dialogue);
                            sessionBlacklistedDoors.add(probe);
                            Rs2Dialogue.clickContinue();
                            if (Rs2PathApi.getPathfinderConfig() != null) {
                                Rs2PathApi.getPathfinderConfig().refresh();
                            }
                            recalculatePath();
                        }
                        if (!traversed) {
                            if (shouldBlacklistDoorAfterWrongTraversal(posBefore, posAfter, fromWp, toWp)) {
                                sessionBlacklistedDoors.add(probe);
                                log.warn("[Walker] Blacklisting door after wrong traversal: door={} from={} to={} before={} after={}",
                                        probe, fromWp, toWp, posBefore, posAfter);
                            }
                            if (doorStillHasAction(probe, fromWp, toWp, doorActions, action)) {
                                log.debug("[Walker] Door interaction did not traverse; action still present at {} ({} -> {})",
                                        probe, fromWp, toWp);
                            } else {
                                markStationaryDoorOpened(probe);
                                if (tryDoorEdgeCrossNudge(fromWp, toWp, currentTarget)) {
                                    markNearbyDoorFamilyOpened(object, probe, action, SEGMENT_DOOR_FAMILY_MARK_RADIUS);
                                    return true;
                                }
                            }
                            return false;
                        }
                        markStationaryDoorOpened(probe);
                        markNearbyDoorFamilyOpened(object, probe, action, SEGMENT_DOOR_FAMILY_MARK_RADIUS);
                    }
                    return true;
                }
            }
        }

        TileObject nearbyDoor = allowSegmentProbe ? findDoorNearSegment(fromWp, toWp, doorActions) : null;
        if (nearbyDoor != null && tryHandleDoorObject(nearbyDoor, nearbyDoor.getWorldLocation(), fromWp, toWp, doorActions, true)) {
            return true;
        }

        return false;
    }

    private static TileObject findDoorNearSegment(WorldPoint fromWp, WorldPoint toWp, List<String> doorActions) {
        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        if (playerLoc == null || fromWp == null || toWp == null || fromWp.getPlane() != toWp.getPlane()) {
            return null;
        }
        if (recentlyOpenedStationaryDoorOnSegment(fromWp, toWp)) {
            return null;
        }

        final int searchDistance = 10;
        List<WallObject> wallSnapshot = rawScanWallSnapshot;
        List<GameObject> gameObjectSnapshot = rawScanGameObjectSnapshot;
        if (wallSnapshot != null || gameObjectSnapshot != null) {
            Map<TileObject, WorldPoint> locations = rawScanDoorLocationSnapshot;
            if (locations == null) {
                return null;
            }
            Map<String, Optional<TileObject>> segmentCache = rawScanDoorSegmentCache;
            String segmentKey = fromWp.getX() + "," + fromWp.getY() + "," + fromWp.getPlane()
                    + ">" + toWp.getX() + "," + toWp.getY() + "," + toWp.getPlane();
            if (segmentCache != null && segmentCache.containsKey(segmentKey)) {
                return segmentCache.get(segmentKey).orElse(null);
            }
            List<TileObject> candidates = new ArrayList<>(
                    (wallSnapshot != null ? wallSnapshot.size() : 0)
                            + (gameObjectSnapshot != null ? gameObjectSnapshot.size() : 0));
            if (wallSnapshot != null) {
                candidates.addAll(wallSnapshot);
            }
            if (gameObjectSnapshot != null) {
                candidates.addAll(gameObjectSnapshot);
            }
            TileObject match = candidates.stream()
                    .filter(o -> isDoorCandidateOnSegment(o, locations.get(o),
                            playerLoc, fromWp, toWp,
                            doorActions, searchDistance))
                    .min(Comparator.comparingInt(o -> locations.get(o).distanceTo2D(playerLoc)))
                    .orElse(null);
            if (segmentCache != null) {
                segmentCache.put(segmentKey, Optional.ofNullable(match));
            }
            return match;
        }
        return Rs2GameObject.getAll(o -> {
                    return isDoorCandidateOnSegment(o, o.getWorldLocation(),
                            playerLoc, fromWp, toWp,
                            doorActions, searchDistance);
                }, playerLoc, searchDistance).stream()
                .min(Comparator.comparingInt(o -> o.getWorldLocation().distanceTo2D(playerLoc)))
                .orElse(null);
    }

    private static boolean isDoorCandidateOnSegment(TileObject object,
                                                    WorldPoint objectLocation,
                                                    WorldPoint playerLoc,
                                                    WorldPoint fromWp,
                                                    WorldPoint toWp,
                                                    List<String> doorActions,
                                                    int searchDistance) {
        if (object == null || objectLocation == null) {
            return false;
        }
        WorldPoint loc = objectLocation;
        if (loc.getPlane() != playerLoc.getPlane()
                || loc.distanceTo2D(playerLoc) > searchDistance
                || sessionBlacklistedDoors.contains(loc)
                || (!(object instanceof WallObject) && !(object instanceof GameObject))
                || (isCatalogTransportObject(object) && !isDoorLikeSceneObject(object))
                || !isDoorOnSegment(object, fromWp, toWp)) {
            return false;
        }
        ObjectComposition comp = resolveRawScanDoorComposition(object);
        return isDoorComposition(comp, doorActions);
    }

    private static ObjectComposition resolveRawScanDoorComposition(TileObject object) {
        if (object == null) {
            return null;
        }
        Map<TileObject, Optional<ObjectComposition>> compositionCache = rawScanDoorCompositionCache;
        if (compositionCache == null) {
            return Rs2GameObject.convertToObjectComposition(object);
        }
        return compositionCache.computeIfAbsent(object,
                        ignored -> Optional.ofNullable(Rs2GameObject.convertToObjectComposition(object)))
                .orElse(null);
    }

    private static boolean tryHandleDoorObject(TileObject object, WorldPoint probe, WorldPoint fromWp, WorldPoint toWp,
                                               List<String> doorActions, boolean allowSegmentProbe) {
        if (object == null || probe == null) return false;
        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        if (!isDoorInteractionWithinRange(object, probe, fromWp, toWp, playerLoc, HANDLER_RANGE)) {
            return false;
        }
        if (isCatalogTransportObject(object) && !isDoorLikeSceneObject(object)) {
            return false;
        }

        ObjectComposition comp = resolveRawScanDoorComposition(object);
        if (!isDoorComposition(comp, doorActions)) return false;

        String action = getDoorAction(comp, doorActions);
        if (action == null) return false;

        boolean found = false;
        final String name = comp.getName();

        if (object instanceof WallObject) {
            int orientation = ((WallObject) object).getOrientationA();

            if (searchNeighborPoint(orientation, probe, fromWp)
                    || searchNeighborPoint(orientation, probe, toWp)
                    || (allowSegmentProbe && wallDoorTouchesSegment((WallObject) object, fromWp, toWp))) {
                log.info("Found WallObject door - name {} with action {} at {} - from {} to {}", name, action, probe, fromWp, toWp);
                found = true;
            }
        } else if (name != null && name.toLowerCase().contains("door")) {
            if (isDoorOnSegment(object, fromWp, toWp)) {
                log.info("Found GameObject door - name {} with action {} at {} - from {} to {}", name, action, probe, fromWp, toWp);
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
        if (shouldThrottleGlobalDoorInteraction()) {
            WebWalkLog.spInfo("door_global_await | mode=segment-probe probe={} from={} to={}",
                    compactWorldPoint(probe), compactWorldPoint(fromWp), compactWorldPoint(toWp));
            return false;
        }
        if (Rs2Player.isMoving()) {
            WebWalkLog.spInfo("door_interact_deferred | reason=moving mode=segment-probe probe={} from={} to={}",
                    compactWorldPoint(probe), compactWorldPoint(fromWp), compactWorldPoint(toWp));
            return false;
        }
        markDoorAttempt(probe, fromWp, toWp);
        markGlobalDoorInteractionCooldown();
        WorldPoint posBefore = Rs2Player.getWorldLocation();
        boolean interacted;
        try {
            interacted = Rs2GameObject.interact(object, action);
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
        markDoorInteractionSettling();
        waitForDoorInteractionProgress(fromWp, toWp);
        WorldPoint posAfter = Rs2Player.getWorldLocation();
        boolean traversed = didTraverseInteractedDoor(posBefore, posAfter, probe, fromWp, toWp);
        if (traversed) {
            markStationaryDoorOpened(probe);
            markNearbyDoorFamilyOpened(object, probe, action, SEGMENT_DOOR_FAMILY_MARK_RADIUS);
            return true;
        }
        if (shouldBlacklistDoorAfterWrongTraversal(posBefore, posAfter, fromWp, toWp)) {
            sessionBlacklistedDoors.add(probe);
            log.warn("[Walker] Blacklisting door after wrong traversal: door={} from={} to={} before={} after={}",
                    probe, fromWp, toWp, posBefore, posAfter);
        }
        if (isQuestLockedDoorDialogue()) {
            String dialogue = Rs2Dialogue.getDialogueText();
            log.warn("[Walker] Door at {} ({} action={}) appears quest/stat-locked — dialogue=\"{}\" — blacklisting tile, refreshing restrictions, recalculating",
                    probe, name, action, dialogue);
            sessionBlacklistedDoors.add(probe);
            Rs2Dialogue.clickContinue();
            if (Rs2PathApi.getPathfinderConfig() != null) {
                Rs2PathApi.getPathfinderConfig().refresh();
            }
            recalculatePath();
            return true;
        }

        if (doorStillHasAction(probe, fromWp, toWp, doorActions, action)) {
            log.debug("[Walker] Segment door interaction did not traverse; action still present at {} ({} -> {})",
                    probe, fromWp, toWp);
        } else {
            markStationaryDoorOpened(probe);
            if (tryDoorEdgeCrossNudge(fromWp, toWp, currentTarget)) {
                markNearbyDoorFamilyOpened(object, probe, action, SEGMENT_DOOR_FAMILY_MARK_RADIUS);
                return true;
            }
        }
        return false;
    }

    private static boolean doorStillHasAction(WorldPoint probe, WorldPoint fromWp, WorldPoint toWp,
                                              List<String> doorActions, String action) {
        if (probe == null || action == null) {
            return false;
        }

        WorldPoint anchor = Rs2Player.getWorldLocation();
        if (anchor == null || anchor.getPlane() != probe.getPlane()) {
            anchor = probe;
        }

        TileObject object = Rs2GameObject.getAll(o -> doorObjectStillHasAction(o, probe, fromWp, toWp, doorActions, action),
                        anchor, Math.max(3, HANDLER_RANGE))
                .stream()
                .findFirst()
                .orElse(null);
        return object != null;
    }

    private static boolean doorObjectStillHasAction(TileObject object, WorldPoint probe, WorldPoint fromWp, WorldPoint toWp,
                                                    List<String> doorActions, String action) {
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
        if (isCatalogTransportObject(object) && !isDoorLikeSceneObject(object)) {
            return false;
        }
        boolean nearProbe = probe != null && loc.distanceTo2D(probe) <= 2;
        boolean onSegment = fromWp != null && toWp != null && isDoorOnSegment(object, fromWp, toWp);
        if (!nearProbe && !onSegment) {
            return false;
        }
        ObjectComposition composition = resolveCompositionForDoorProbe(object);
        String currentAction = getDoorAction(composition, doorActions);
        return currentAction != null && currentAction.equalsIgnoreCase(action);
    }

    private static void markStationaryDoorOpened(WorldPoint doorTile) {
        Rs2DoorHandler.markStationaryDoorOpened(recentlyOpenedStationaryDoors, doorTile);
    }

    private static String doorAttemptKey(WorldPoint doorTile, WorldPoint fromWp, WorldPoint toWp) {
        return Rs2DoorHandler.doorAttemptKey(doorTile, fromWp, toWp);
    }

    private static boolean shouldThrottleDoorAttempt(WorldPoint doorTile, WorldPoint fromWp, WorldPoint toWp) {
        return Rs2DoorHandler.shouldThrottleDoorAttempt(
                recentDoorAttemptByEdge,
                DOOR_ATTEMPT_EDGE_COOLDOWN_MS,
                doorTile,
                fromWp,
                toWp);
    }

    private static boolean hasRecentDoorAttemptOnEdge(WorldPoint fromWp, WorldPoint toWp) {
        return shouldThrottleDoorAttempt(null, fromWp, toWp);
    }

    private static boolean hasRecentDoorAttemptNearIndex(List<WorldPoint> path, int edgeIdx) {
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

    private static boolean waitForRecentDoorEdgeResolutionNearIndex(List<WorldPoint> path, int edgeIdx, int timeoutMs) {
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

    private static long recentDoorAttemptAgeNearIndex(List<WorldPoint> path, int edgeIdx) {
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
            Long attemptedAt = recentDoorAttemptByEdge.get(doorAttemptKey(null, from, to));
            if (attemptedAt != null) {
                newestAttemptAt = Math.max(newestAttemptAt, attemptedAt);
            }
        }
        return newestAttemptAt < 0 ? -1L : Math.max(0L, now - newestAttemptAt);
    }

    private static boolean isLikelyDoorEdgeTransition(WorldPoint from, WorldPoint to) {
        if (from == null || to == null || from.getPlane() != to.getPlane()) {
            return false;
        }
        // Door crossings are local transitions. Ignore long smoothed hops that can
        // accidentally reuse old door attempt keys and stall nearby-wait logic.
        return from.distanceTo2D(to) >= 1 && from.distanceTo2D(to) <= 2;
    }

    private static boolean tryPostDoorFastMinimapClick(List<WorldPoint> path, int edgeIdx, WorldPoint playerLoc, WorldPoint target) {
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

    private static boolean tryDoorEdgeCrossNudge(WorldPoint fromWp, WorldPoint toWp, WorldPoint target) {
        if (fromWp == null || toWp == null || fromWp.getPlane() != toWp.getPlane()) {
            return false;
        }
        WorldPoint before = Rs2Player.getWorldLocation();
        if (before == null || before.getPlane() != toWp.getPlane()) {
            return false;
        }
        if (before.equals(toWp)) {
            return true;
        }
        if (before.distanceTo2D(toWp) > POST_DOOR_EDGE_NUDGE_MAX_FROM_PLAYER) {
            return false;
        }
        if (Rs2Player.isMoving() || Rs2Player.isAnimating()) {
            return false;
        }

        boolean clicked = walkFastCanvas(toWp);
        if (!clicked) {
            clicked = walkMiniMapToward(toWp, before, POST_DOOR_FAST_CLICK_MAX_EUCLIDEAN - 1);
        }
        if (!clicked) {
            return false;
        }

        markFirstMovementClick("first_door_edge_nudge", target, before, "to=" + compactWorldPoint(toWp));
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
            WebWalkLog.tmark("door_edge_nudge", System.currentTimeMillis() - walkSessionStartedAtMs,
                    target, before, "from=" + compactWorldPoint(fromWp) + " to=" + compactWorldPoint(toWp));
            lastMovedTimeMs = System.currentTimeMillis();
            stuckCount = 0;
        } else {
            WebWalkLog.spInfo("door_edge_nudge_unresolved | from={} to={} before={} after={}",
                    compactWorldPoint(fromWp), compactWorldPoint(toWp), compactWorldPoint(before), compactWorldPoint(after));
        }
        return progressed;
    }

    private static boolean tryRecentDoorAttemptEdgeNudge(WorldPoint playerLoc, WorldPoint target) {
        WorldPoint from = lastDoorAttemptFrom;
        WorldPoint to = lastDoorAttemptTo;
        long attemptedAt = lastDoorAttemptAtMs;
        if (playerLoc == null || from == null || to == null || attemptedAt <= 0L) {
            return false;
        }
        long ageMs = System.currentTimeMillis() - attemptedAt;
        if (ageMs < 0L || ageMs > POST_DOOR_NUDGE_RECENT_ATTEMPT_MS) {
            return false;
        }
        if (playerLoc.getPlane() != to.getPlane() || playerLoc.distanceTo2D(to) > POST_DOOR_EDGE_NUDGE_MAX_FROM_PLAYER) {
            return false;
        }
        if (Rs2Player.isMoving() || Rs2Player.isAnimating()) {
            return false;
        }
        boolean nudged = tryDoorEdgeCrossNudge(from, to, target);
        if (nudged) {
            WebWalkLog.tmark("recent_door_edge_nudge", System.currentTimeMillis() - walkSessionStartedAtMs,
                    target, playerLoc, "from=" + compactWorldPoint(from) + " to=" + compactWorldPoint(to));
        }
        return nudged;
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
        return afterTo <= 1 && afterTo < beforeTo;
    }

    private static int interimPreclickTiles() {
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
        if (interim == null) {
            return false;
        }
        if (playerLoc == null || playerLoc.getPlane() != interim.getPlane()) {
            return true;
        }
        if (playerLoc.distanceTo2D(interim) <= INTERIM_CLOSE_TILES) {
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

    private static void recordInterimDistanceProgress(WorldPoint interim, WorldPoint playerLoc, long nowMs) {
        int distance = distanceToInterimOrMax(interim, playerLoc);
        if (distance < interimLastDistanceToTarget) {
            interimLastDistanceToTarget = distance;
            interimLastProgressAtMs = nowMs;
        }
    }

    private static void waitForMovementStartAfterRecovery(WorldPoint cancelGoal,
                                                          WorldPoint playerBefore,
                                                          WorldPoint interimGoal,
                                                          WorldPoint arrivalGoal,
                                                          int arrivalMaxChebyshev) {
        if (cancelGoal == null || playerBefore == null) {
            return;
        }
        sleepUntil(() -> {
            if (isWalkCancelled(cancelGoal)) {
                return true;
            }
            WorldPoint playerNow = Rs2Player.getWorldLocation();
            if (playerNow == null) {
                return false;
            }
            if (!playerNow.equals(playerBefore) || Rs2Player.isMoving()) {
                return true;
            }
            if (interimGoal != null
                    && interimGoal.getPlane() == playerNow.getPlane()
                    && playerNow.distanceTo2D(interimGoal) <= INTERIM_CLOSE_TILES) {
                return true;
            }
            return arrivalGoal != null
                    && arrivalMaxChebyshev >= 0
                    && arrivalGoal.getPlane() == playerNow.getPlane()
                    && playerNow.distanceTo2D(arrivalGoal) <= arrivalMaxChebyshev;
        }, POST_RECOVERY_MOVEMENT_START_WAIT_MS);
    }

    private static boolean clearInterimTargetIfReachedOrExpired(WorldPoint playerLoc,
                                                                List<WorldPoint> path,
                                                                long nowMs) {
        WorldPoint interim = interimTargetWp;
        recordInterimDistanceProgress(interim, playerLoc, nowMs);
        if (interim != null && path != null && !path.isEmpty()) {
            int bestIdxNow = getClosestTileIndex(path, playerLoc);
            if (bestIdxNow > interimLastBestPathIdx) {
                interimLastBestPathIdx = bestIdxNow;
                interimLastProgressAtMs = nowMs;
            }
        }
        if (!shouldClearInterimTarget(interim, playerLoc, interimSetAtMs, interimLastProgressAtMs, nowMs)) {
            return false;
        }
        String reason;
        if (playerLoc == null || interim == null || playerLoc.getPlane() != interim.getPlane()) {
            reason = "invalid";
        } else if (playerLoc.distanceTo2D(interim) <= INTERIM_CLOSE_TILES) {
            reason = "close";
        } else if (interimLastProgressAtMs > 0L && nowMs - interimLastProgressAtMs > INTERIM_PROGRESS_TIMEOUT_MS) {
            reason = "stale-progress";
        } else {
            reason = "expired";
        }
        clearInterimTarget(reason);
        return true;
    }

    private static boolean shouldYieldForActiveRecoveryInterim(WorldPoint playerLoc,
                                                               List<WorldPoint> path,
                                                               long nowMs) {
        WorldPoint interim = interimTargetWp;
        if (interim == null) {
            return false;
        }
        recordInterimDistanceProgress(interim, playerLoc, nowMs);
        if (playerLoc != null && path != null && !path.isEmpty()) {
            int bestIdxNow = getClosestTileIndex(path, playerLoc);
            if (bestIdxNow > interimLastBestPathIdx) {
                interimLastBestPathIdx = bestIdxNow;
                interimLastProgressAtMs = nowMs;
            }
        }
        return shouldYieldForActiveRecoveryInterim(interim,
                playerLoc,
                interimSetAtMs,
                interimLastProgressAtMs,
                nowMs,
                lastMovedTimeMs,
                lastUnreachableRecoveryClickAtMs,
                Rs2Player.isMoving());
    }

    private static boolean shouldYieldForActiveRouteInterim(WorldPoint playerLoc,
                                                            List<WorldPoint> path,
                                                            long nowMs) {
        WorldPoint interim = interimTargetWp;
        if (interim == null) {
            return false;
        }
        recordInterimDistanceProgress(interim, playerLoc, nowMs);
        if (playerLoc != null && path != null && !path.isEmpty()) {
            int bestIdxNow = getClosestTileIndex(path, playerLoc);
            if (bestIdxNow > interimLastBestPathIdx) {
                interimLastBestPathIdx = bestIdxNow;
                interimLastProgressAtMs = nowMs;
            }
        }
        return shouldDeferRouteWorkForActiveInterim(interim,
                playerLoc,
                interimSetAtMs,
                interimLastProgressAtMs,
                nowMs,
                lastMovedTimeMs,
                Rs2Player.isMoving(),
                INTERIM_CLOSE_TILES);
    }

    static boolean shouldYieldForActiveRecoveryInterim(WorldPoint interim,
                                                       WorldPoint playerLoc,
                                                       long setAtMs,
                                                       long lastProgressAtMs,
                                                       long nowMs,
                                                       long lastMovedAtMs,
                                                       long lastRecoveryClickAtMs,
                                                       boolean playerMoving) {
        if (interim == null) {
            return false;
        }
        if (shouldClearInterimTarget(interim, playerLoc, setAtMs, lastProgressAtMs, nowMs)) {
            return false;
        }
        if (shouldDeferRouteWorkForActiveInterim(interim,
                playerLoc,
                setAtMs,
                lastProgressAtMs,
                nowMs,
                lastMovedAtMs,
                playerMoving,
                INTERIM_CLOSE_TILES)) {
            return true;
        }
        return isRecentEvent(nowMs, lastRecoveryClickAtMs, RECOVERY_MOVEMENT_IN_FLIGHT_MS);
    }

    static boolean shouldDeferRouteWorkForActiveInterim(WorldPoint interim,
                                                        WorldPoint playerLoc,
                                                        long setAtMs,
                                                        long lastProgressAtMs,
                                                        long nowMs,
                                                        long lastMovedAtMs,
                                                        boolean playerMoving,
                                                        int handoffTiles) {
        if (interim == null) {
            return false;
        }
        if (shouldClearInterimTarget(interim, playerLoc, setAtMs, lastProgressAtMs, nowMs)) {
            return false;
        }
        if (playerLoc == null || playerLoc.getPlane() != interim.getPlane()) {
            return false;
        }
        if (playerLoc.distanceTo2D(interim) <= Math.max(0, handoffTiles)) {
            return false;
        }
        if (playerMoving) {
            return true;
        }
        if (isRecentEvent(nowMs, lastProgressAtMs, INTERIM_PROGRESS_TIMEOUT_MS)) {
            return true;
        }
        return isRecentEvent(nowMs, lastMovedAtMs, RECOVERY_MOVEMENT_IN_FLIGHT_MS);
    }

    private static void clearInterimTarget(String reason) {
        WorldPoint old = interimTargetWp;
        if (old != null) {
            if ("close".equals(reason)) {
                WebWalkLog.spDebug("interim_clear | reason={} interim={}", reason, compactWorldPoint(old));
            } else {
                WebWalkLog.spInfo("interim_clear | reason={} interim={}", reason, compactWorldPoint(old));
            }
        }
        interimTargetWp = null;
        interimTargetIdx = -1;
        interimSetAtMs = 0L;
        interimLastProgressAtMs = 0L;
        interimLastBestPathIdx = -1;
        interimLastDistanceToTarget = Integer.MAX_VALUE;
        interimLastRetargetAtMs = 0L;
    }

    private static boolean shouldThrottleGlobalDoorInteraction() {
        return Rs2DoorHandler.shouldThrottleGlobalDoorInteraction(nextDoorInteractionAllowedAtMs);
    }

    private static boolean isDoorInteractionSettling() {
        return System.currentTimeMillis() < doorInteractionSettleUntilMs;
    }

    private static boolean isTransportInteractionSettling() {
        long handledAt = lastTransportHandledAtMs;
        if (handledAt <= 0L) {
            return false;
        }
        long ageMs = System.currentTimeMillis() - handledAt;
        if (ageMs < 0L || ageMs > TRANSPORT_POST_INTERACT_SETTLE_MS) {
            return false;
        }
        WorldPoint now = Rs2Player.getWorldLocation();
        WorldPoint landedAt = lastTransportHandledAtLocation;
        if (now == null || landedAt == null) {
            return ageMs <= TRANSPORT_POST_INTERACT_SETTLE_MS / 2;
        }
        return now.getPlane() == landedAt.getPlane()
                && now.distanceTo2D(landedAt) <= 1
                && !Rs2Player.isMoving();
    }

    private static boolean isDoorEdgePassSkipCoolingDown() {
        return System.currentTimeMillis() - lastDoorEdgePassSkipAtMs < DOOR_EDGE_SKIP_COOLDOWN_MS;
    }

    private static boolean isRecoveryMovementInFlight() {
        return System.currentTimeMillis() - lastUnreachableRecoveryClickAtMs < RECOVERY_MOVEMENT_IN_FLIGHT_MS;
    }

    private static void markDoorInteractionSettling() {
        doorInteractionSettleUntilMs = System.currentTimeMillis() + DOOR_POST_INTERACT_SETTLE_MS;
    }

    private static void markGlobalDoorInteractionCooldown() {
        nextDoorInteractionAllowedAtMs = Rs2DoorHandler.markGlobalDoorInteractionCooldown(DOOR_INTERACTION_GLOBAL_COOLDOWN_MS);
    }

    private static void markDoorAttempt(WorldPoint doorTile, WorldPoint fromWp, WorldPoint toWp) {
        Rs2DoorHandler.markDoorAttempt(recentDoorAttemptByEdge, doorTile, fromWp, toWp);
        if (fromWp != null && toWp != null) {
            lastDoorAttemptFrom = fromWp;
            lastDoorAttemptTo = toWp;
            lastDoorAttemptAtMs = System.currentTimeMillis();
        }
    }

    private static boolean shouldThrottleCurrentTileTransportAttempt(WorldPoint fromWp, WorldPoint toWp) {
        if (fromWp == null || toWp == null) {
            return false;
        }
        String edgeKey = doorAttemptKey(null, fromWp, toWp);
        long now = System.currentTimeMillis();
        recentCurrentTileTransportByEdge.entrySet()
                .removeIf(entry -> now - entry.getValue() > CURRENT_TILE_TRANSPORT_EDGE_COOLDOWN_MS);
        Long last = recentCurrentTileTransportByEdge.get(edgeKey);
        return last != null && now - last < CURRENT_TILE_TRANSPORT_EDGE_COOLDOWN_MS;
    }

    private static void markCurrentTileTransportAttempt(WorldPoint fromWp, WorldPoint toWp) {
        if (fromWp == null || toWp == null) {
            return;
        }
        recentCurrentTileTransportByEdge.put(
                doorAttemptKey(null, fromWp, toWp),
                System.currentTimeMillis());
    }

    private static boolean recentlyOpenedStationaryDoorOnSegment(WorldPoint fromWp, WorldPoint toWp) {
        return Rs2DoorHandler.recentlyOpenedStationaryDoorOnSegment(
                recentlyOpenedStationaryDoors,
                STATIONARY_DOOR_SUPPRESS_MS,
                fromWp,
                toWp);
    }

    private static boolean wasStationaryDoorOpenedRecently(WorldPoint doorTile) {
        if (doorTile == null) {
            return false;
        }
        Long openedAt = recentlyOpenedStationaryDoors.get(doorTile);
        if (openedAt == null) {
            return false;
        }
        long ageMs = System.currentTimeMillis() - openedAt;
        if (ageMs > STATIONARY_DOOR_SUPPRESS_MS) {
            recentlyOpenedStationaryDoors.remove(doorTile);
            return false;
        }
        return true;
    }

    /**
     * Strict catalog step: path tile equals transport origin in {@link Rs2PathApi#getTransports()}
     * and next tile equals that row's destination. Used where the walker must dispatch {@code handleTransports}
     * from the path index (origin keyed in the TSV-fed multimap).
     */
    private static boolean hasExplicitTransportStep(List<WorldPoint> path, int index) {
        if (path == null || index < 0 || index >= path.size() - 1) {
            return false;
        }
        return matchesDirectedTransportCatalogEdge(path.get(index), path.get(index + 1));
    }

    private static boolean hasImmediatePlannedTransportStep(List<WorldPoint> path,
                                                            int routeStartIdx,
                                                            WorldPoint playerLoc) {
        if (path == null || routeStartIdx < 0 || routeStartIdx >= path.size() - 1 || playerLoc == null) {
            return false;
        }
        WorldPoint origin = path.get(routeStartIdx);
        return hasExplicitTransportStep(path, routeStartIdx)
                && isTransportOriginNearPlayer(
                origin, playerLoc, RAW_TRANSPORT_DISPATCH_MAX_DISTANCE);
    }

    static boolean shouldApproachPlannedTransportOrigin(boolean explicitTransportStep,
                                                        WorldPoint routeOrigin,
                                                        WorldPoint playerLoc,
                                                        int dispatchMaxDistance) {
        return explicitTransportStep
                && routeOrigin != null
                && playerLoc != null
                && routeOrigin.getPlane() == playerLoc.getPlane()
                && routeOrigin.distanceTo2D(playerLoc) > Math.max(0, dispatchMaxDistance);
    }

    /**
     * Whether this path edge is covered by a transport catalog row (same coordinates loaded from TSV into
     * {@link Rs2PathApi#getTransports()}). Includes strict origin-destination steps (including
     * cross-plane rows such as ladders) and same-plane hops where the path starts on a tile Chebyshev-adjacent
     * to the catalog origin but still targets that row's destination, so door probing does not fight
     * {@code handleTransports}.
     */
    private static boolean isCatalogBackedTransportSegment(List<WorldPoint> path, int index) {
        if (path == null || index < 0 || index >= path.size() - 1) {
            return false;
        }
        return isCatalogBackedTransportSegment(path.get(index), path.get(index + 1));
    }

    private static boolean isCatalogBackedTransportSegment(WorldPoint from, WorldPoint to) {
        if (from == null || to == null) {
            return false;
        }
        if (matchesDirectedTransportCatalogEdge(from, to)) {
            return true;
        }
        if (matchesDirectedTransportCatalogEdge(to, from)) {
            return true;
        }
        if (matchesAdjacentOriginShortTransportHop(from, to)) {
            return true;
        }
        if (matchesAdjacentOriginShortTransportHop(to, from)) {
            return true;
        }
        return false;
    }

    private static boolean isDoorLikeCatalogTransportSegment(List<WorldPoint> path, int index) {
        if (path == null || index < 0 || index >= path.size() - 1) {
            return false;
        }
        return isDoorLikeCatalogTransportSegment(path.get(index), path.get(index + 1));
    }

    private static boolean isDoorLikeCatalogTransportSegment(WorldPoint from, WorldPoint to) {
        if (from == null || to == null) {
            return false;
        }
        return hasDoorLikeDirectedCatalogTransport(from, to)
                || hasDoorLikeDirectedCatalogTransport(to, from)
                || hasDoorLikeAdjacentOriginShortTransportHop(from, to)
                || hasDoorLikeAdjacentOriginShortTransportHop(to, from);
    }

    private static boolean matchesDirectedTransportCatalogEdge(WorldPoint origin, WorldPoint dest) {
        if (origin == null || dest == null) {
            return false;
        }
        Set<Transport> transports = Rs2PathApi.getTransports().get(origin);
        if (transports == null || transports.isEmpty()) {
            return false;
        }
        return transports.stream().anyMatch(t -> Objects.equals(t.getDestination(), dest));
    }

    private static boolean hasDoorLikeDirectedCatalogTransport(WorldPoint origin, WorldPoint dest) {
        if (origin == null || dest == null) {
            return false;
        }
        Set<Transport> transports = Rs2PathApi.getTransports().get(origin);
        if (transports == null || transports.isEmpty()) {
            return false;
        }
        return transports.stream()
                .anyMatch(t -> Objects.equals(t.getDestination(), dest) && isDoorLikeCatalogTransport(t));
    }

    /**
     * True when some catalog origin one step from {@code from} has a same-plane adjacent transport to {@code to}.
     * Restricted to {@link #isAdjacentSamePlaneTransport} rows so long-distance transports do not suppress doors.
     */
    private static boolean matchesAdjacentOriginShortTransportHop(WorldPoint from, WorldPoint to) {
        if (from == null || to == null || from.getPlane() != to.getPlane()) {
            return false;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                WorldPoint catalogOrigin = new WorldPoint(from.getX() + dx, from.getY() + dy, from.getPlane());
                Set<Transport> transports = Rs2PathApi.getTransports().get(catalogOrigin);
                if (transports == null || transports.isEmpty()) {
                    continue;
                }
                for (Transport t : transports) {
                    if (Objects.equals(t.getDestination(), to) && isAdjacentSamePlaneTransport(t)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean hasDoorLikeAdjacentOriginShortTransportHop(WorldPoint from, WorldPoint to) {
        if (from == null || to == null || from.getPlane() != to.getPlane()) {
            return false;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                WorldPoint catalogOrigin = new WorldPoint(from.getX() + dx, from.getY() + dy, from.getPlane());
                Set<Transport> transports = Rs2PathApi.getTransports().get(catalogOrigin);
                if (transports == null || transports.isEmpty()) {
                    continue;
                }
                for (Transport t : transports) {
                    if (Objects.equals(t.getDestination(), to)
                            && isAdjacentSamePlaneTransport(t)
                            && isDoorLikeCatalogTransport(t)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isDoorLikeCatalogTransport(Transport transport) {
        if (transport == null || transport.getType() != TransportType.TRANSPORT) {
            return false;
        }
        return isDoorLikeGameObjectName(transport.getName())
                || isDoorLikeGameObjectName(transport.getDisplayInfo())
                || isDoorLikeTransportAction(transport.getAction());
    }

    private static boolean isDoorLikeTransportAction(String action) {
        if (action == null) {
            return false;
        }
        return doorActionPriorityIndex(action) != Integer.MAX_VALUE;
    }

    /**
     * True when this scene object is the interactable listed on a transport catalog row (same
     * coordinates and object ids as TSV loaded into {@link Rs2PathApi#getTransports()}).
     * Door-ahead / fallback / LOS scans must treat it as non-door so {@link #handleTransports} owns it.
     */
    private static boolean isCatalogTransportObject(TileObject object) {
        if (object == null) {
            return false;
        }
        WorldPoint loc = object.getWorldLocation();
        if (loc == null) {
            return false;
        }
        int id = object.getId();
        if (id <= 0) {
            return false;
        }
        Map<WorldPoint, Set<Transport>> map = Rs2PathApi.getTransports();
        if (map == null || map.isEmpty()) {
            return false;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                WorldPoint catalogOrigin = new WorldPoint(loc.getX() + dx, loc.getY() + dy, loc.getPlane());
                Set<Transport> transports = map.get(catalogOrigin);
                if (transports == null || transports.isEmpty()) {
                    continue;
                }
                for (Transport t : transports) {
                    if (t != null && t.getObjectId() == id && !isDoorLikeCatalogTransport(t)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void waitForDoorInteractionProgress(WorldPoint fromWp, WorldPoint toWp) {
        long startedAt = System.currentTimeMillis();
        AwaitTicket ticket = Rs2WalkerAwaits.beginTicket();
        try {
            Rs2WalkerAwaits.awaitDoorInteractionProgress(ticket, fromWp, toWp);
        } finally {
            if (rawScanWallSnapshot != null || rawScanGameObjectSnapshot != null) {
                rawScanDoorInteractionWaitMs += System.currentTimeMillis() - startedAt;
            }
        }
    }

    private static boolean waitForDoorEdgeResolution(WorldPoint fromWp, WorldPoint toWp, int timeoutMs) {
        long startedAt = System.currentTimeMillis();
        DoorResolution resolution = Rs2WalkerAwaits.awaitDoorEdgeResolution(fromWp, toWp, timeoutMs);
        WebWalkLog.tmark("door_edge_wait_done", System.currentTimeMillis() - startedAt, currentTarget,
                Rs2Player.getWorldLocation(),
                "result=" + resolution + " from=" + compactWorldPoint(fromWp) + " to=" + compactWorldPoint(toWp));
        return resolution == DoorResolution.RESOLVED;
    }

    private static boolean isDoorEdgeResolved(WorldPoint fromWp, WorldPoint toWp) {
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
        if (start == null || end == null || toWp == null) {
            return false;
        }
        if (start.equals(end)) {
            return false;
        }
        if (start.getPlane() != end.getPlane()) {
            return true;
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

    private static boolean startedNearDoorEdge(WorldPoint start, WorldPoint fromWp, WorldPoint toWp) {
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

    private static boolean movedAcrossInteractedObject(WorldPoint start, WorldPoint end, WorldPoint objectLoc) {
        int startRelX = Integer.compare(start.getX(), objectLoc.getX());
        int endRelX = Integer.compare(end.getX(), objectLoc.getX());
        int startRelY = Integer.compare(start.getY(), objectLoc.getY());
        int endRelY = Integer.compare(end.getY(), objectLoc.getY());
        return startRelX != endRelX || startRelY != endRelY;
    }

    private static boolean isDoorComposition(ObjectComposition comp, List<String> doorActions) {
        if (comp == null || comp.getImpostorIds() != null || comp.getName().equals("null") || comp.getActions() == null) {
            return false;
        }
        return getDoorAction(comp, doorActions) != null;
    }

    private static String getDoorAction(ObjectComposition comp, List<String> doorActions) {
        if (comp == null || comp.getActions() == null) {
            return null;
        }
        return Arrays.stream(comp.getActions())
                .filter(Objects::nonNull)
                .filter(act -> doorActions.stream().anyMatch(dact -> act.toLowerCase().startsWith(dact.toLowerCase())))
                .min(Comparator.comparing(act -> doorActions.indexOf(doorActions.stream()
                        .filter(dact -> act.toLowerCase().startsWith(dact))
                        .findFirst()
                        .orElse(""))))
                .orElse(null);
    }

    private static boolean hasDoorLikeSceneObjectOnSegment(WorldPoint fromWp, WorldPoint toWp,
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

    private static boolean hasUnresolvedDoorLikeObjectNearRawPath(List<WorldPoint> rawPath,
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
            if (isCatalogBackedTransportSegment(rawPath, ri) && !isDoorLikeCatalogTransportSegment(rawPath, ri)) {
                continue;
            }
            if (hasUnresolvedDoorLikeSceneObjectOnSegment(from, to, playerLoc, radiusTiles)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasUnresolvedDoorLikeSceneObjectOnSegment(WorldPoint fromWp, WorldPoint toWp,
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

    private static boolean isUnresolvedRouteDoorObject(TileObject object, WorldPoint fromWp, WorldPoint toWp,
                                                       WorldPoint playerLoc, int radiusTiles) {
        if (object == null || object.getWorldLocation() == null) {
            return false;
        }
        WorldPoint location = object.getWorldLocation();
        if (location.getPlane() != playerLoc.getPlane()
                || location.distanceTo2D(playerLoc) > radiusTiles
                || (isCatalogTransportObject(object) && !isDoorLikeSceneObject(object))
                || !isDoorOnSegment(object, fromWp, toWp)) {
            return false;
        }

        ObjectComposition comp = resolveCompositionForDoorProbe(object);
        if (comp == null
                || isNullOrPlaceholderObjectName(comp.getName())
                || doorCompositionSpecifiesOnlyCloseOrShut(comp)) {
            return false;
        }
        String action = pickWalkDoorAction(comp);
        return isDoorLikeGameObjectName(comp.getName())
                || (action != null && doorActionPriorityIndex(action) < Integer.MAX_VALUE);
    }

    private static boolean isPendingRouteDoorObject(TileObject object, WorldPoint fromWp, WorldPoint toWp,
                                                    WorldPoint playerLoc, int radiusTiles) {
        if (object == null || object.getWorldLocation() == null) {
            return false;
        }
        WorldPoint location = object.getWorldLocation();
        if (location.getPlane() != playerLoc.getPlane()
                || location.distanceTo2D(playerLoc) > radiusTiles
                || sessionBlacklistedDoors.contains(location)
                || (isCatalogTransportObject(object) && !isDoorLikeSceneObject(object))
                || !isDoorOnSegment(object, fromWp, toWp)) {
            return false;
        }

        ObjectComposition comp = resolveCompositionForDoorProbe(object);
        if (comp == null
                || isNullOrPlaceholderObjectName(comp.getName())
                || doorCompositionSpecifiesOnlyCloseOrShut(comp)) {
            return false;
        }
        String action = pickWalkDoorAction(comp);
        return isDoorLikeGameObjectName(comp.getName())
                || (action != null && doorActionPriorityIndex(action) < Integer.MAX_VALUE);
    }

    private static boolean isDoorLikeSceneObject(TileObject object) {
        if (object == null || object.getWorldLocation() == null) {
            return false;
        }
        ObjectComposition comp = resolveCompositionForDoorProbe(object);
        if (comp == null
                || isNullOrPlaceholderObjectName(comp.getName())
                || doorCompositionSpecifiesOnlyCloseOrShut(comp)) {
            return false;
        }
        String action = pickWalkDoorAction(comp);
        return isDoorLikeGameObjectName(comp.getName())
                || (action != null && doorActionPriorityIndex(action) < Integer.MAX_VALUE);
    }

    private static boolean isDoorOnSegment(TileObject object, WorldPoint fromWp, WorldPoint toWp) {
        if (object == null || object.getWorldLocation() == null) return false;
        if (object instanceof WallObject) {
            return wallDoorTouchesSegment((WallObject) object, fromWp, toWp);
        }
        return isPointNearSegment(object.getWorldLocation(), fromWp, toWp, 1);
    }

    private static boolean isDoorInteractionWithinRange(TileObject object, WorldPoint probe,
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

    static boolean wallDoorTouchesSegment(WallObject wall, WorldPoint fromWp, WorldPoint toWp) {
        if (wall == null || wall.getWorldLocation() == null || fromWp == null || toWp == null) return false;
        if (wall.getWorldLocation().getPlane() != fromWp.getPlane() || fromWp.getPlane() != toWp.getPlane()) return false;

        WorldPoint doorTile = wall.getWorldLocation();
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

	/**
	 * Door handling can include dialogue and waits; bound it so the walker cannot hang
	 * indefinitely on a bad interact. If the timeout elapses, return false so the main
	 * loop can continue (stall detection / replans).
	 */
	private static boolean handleDoorsWithTimeout(List<WorldPoint> path, int index, long timeoutMs) {
        return handleDoorsWithTimeout(path, index, timeoutMs, null);
    }

    private static boolean handleDoorsWithTimeout(List<WorldPoint> path, int index, long timeoutMs,
                                                  Map<String, WorldPoint> attemptedDoorEdgesThisPass) {
        return handleDoorsWithTimeout(path, index, timeoutMs, attemptedDoorEdgesThisPass, false);
    }

    private static boolean handleDoorsWithTimeout(List<WorldPoint> path, int index, long timeoutMs,
                                                  Map<String, WorldPoint> attemptedDoorEdgesThisPass,
                                                  boolean allowSegmentProbe) {
		long start = System.currentTimeMillis();
        WorldPoint[] segment = resolveDoorSegment(path, index);
        String edgeKey = segment != null && segment.length >= 2 && segment[0] != null && segment[1] != null
                ? doorAttemptKey(null, segment[0], segment[1])
                : null;
        WorldPoint playerBeforeAttempt = Rs2Player.getWorldLocation();
        if (!markDoorEdgeAttemptThisPass(attemptedDoorEdgesThisPass, segment, playerBeforeAttempt)) {
            lastDoorEdgePassSkipAtMs = System.currentTimeMillis();
            WebWalkLog.spInfo("door_edge_pass_skip | idx={}", index);
            return false;
        }
		boolean handled = handleDoors(path, index, allowSegmentProbe);
		if (!handled) {
            // Do not consume one-shot budget when no interaction happened; allow
            // a later resolver in the same pass to attempt this edge.
            if (attemptedDoorEdgesThisPass != null && edgeKey != null) {
                attemptedDoorEdgesThisPass.remove(edgeKey);
            }
			return false;
		}
        WebWalkLog.tmark("door_interaction_done", System.currentTimeMillis() - start, currentTarget, playerBeforeAttempt,
                "idx=" + index);
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

    private static WorldPoint[] resolveDoorSegment(List<WorldPoint> path, int index) {
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

    static boolean markDoorEdgeAttemptThisPass(Map<String, WorldPoint> attemptedDoorEdgesThisPass,
                                               WorldPoint[] segment,
                                               WorldPoint playerBeforeAttempt) {
        if (attemptedDoorEdgesThisPass == null || segment == null || segment.length < 2
                || segment[0] == null || segment[1] == null) {
            return true;
        }
        String edgeKey = doorAttemptKey(null, segment[0], segment[1]);
        WorldPoint previousAttemptPos = attemptedDoorEdgesThisPass.get(edgeKey);
        if (previousAttemptPos != null && playerBeforeAttempt != null
                && previousAttemptPos.getPlane() == playerBeforeAttempt.getPlane()
                && previousAttemptPos.distanceTo2D(playerBeforeAttempt) <= 1) {
            return false;
        }
        attemptedDoorEdgesThisPass.put(edgeKey, playerBeforeAttempt);
        return true;
    }

	/**
	 * Last-resort door resolver for "tile unreachable near player" stalls.
	 * Scans a very small radius around the player for door-like wall/game objects
	 * and interacts with the best candidate action.
	 */
	private static boolean tryResolveNearbyDoorBlocker(WorldPoint playerLoc, int radiusTiles) {
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
			ObjectComposition comp = resolveCompositionForDoorProbe(w);
			if (comp == null || isNullOrPlaceholderObjectName(comp.getName())) continue;
			if (doorCompositionSpecifiesOnlyCloseOrShut(comp)) continue;

			String action = pickWalkDoorAction(comp);
			boolean doorLike = isDoorLikeGameObjectName(comp.getName())
					|| (action != null && doorActionPriorityIndex(action) < Integer.MAX_VALUE);
			if (!doorLike) continue;
			if (isCatalogTransportObject(w) && !isDoorLikeSceneObject(w)) continue;
			candidates++;

			// Allow empty-action doors: use default interact.
			String actionFinal = action == null ? "" : action;
			int dist = w.getWorldLocation() == null ? Integer.MAX_VALUE : w.getWorldLocation().distanceTo2D(playerLoc);
			int pri = actionFinal.isEmpty() ? Integer.MAX_VALUE : doorActionPriorityIndex(actionFinal);
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
			ObjectComposition comp = resolveCompositionForDoorProbe(g);
			if (comp == null || isNullOrPlaceholderObjectName(comp.getName())) continue;
			if (doorCompositionSpecifiesOnlyCloseOrShut(comp)) continue;

			String action = pickWalkDoorAction(comp);
			boolean doorLike = isDoorLikeGameObjectName(comp.getName())
					|| (action != null && doorActionPriorityIndex(action) < Integer.MAX_VALUE);
			if (!doorLike) continue;
			if (isCatalogTransportObject(g) && !isDoorLikeSceneObject(g)) continue;
			candidates++;

			String actionFinal = action == null ? "" : action;
			int dist = g.getWorldLocation() == null ? Integer.MAX_VALUE : g.getWorldLocation().distanceTo2D(playerLoc);
			int pri = actionFinal.isEmpty() ? Integer.MAX_VALUE : doorActionPriorityIndex(actionFinal);
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
	private static boolean tryResolveDoorBlockerLineOfSight(WorldPoint playerLoc, List<WorldPoint> path, int startIdx, int radiusTiles) {
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

			ObjectComposition comp = resolveCompositionForDoorProbe(w);
			if (comp == null || isNullOrPlaceholderObjectName(comp.getName())) continue;
			if (doorCompositionSpecifiesOnlyCloseOrShut(comp)) continue;

			String action = pickWalkDoorAction(comp);

			boolean doorLike = isDoorLikeGameObjectName(comp.getName())
					|| (action != null && doorActionPriorityIndex(action) < Integer.MAX_VALUE);
			if (!doorLike) continue;
			if (isCatalogTransportObject(w) && !isDoorLikeSceneObject(w)) continue;

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

			ObjectComposition comp = resolveCompositionForDoorProbe(g);
			if (comp == null || isNullOrPlaceholderObjectName(comp.getName())) continue;
			if (doorCompositionSpecifiesOnlyCloseOrShut(comp)) continue;

			String action = pickWalkDoorAction(comp);

			boolean doorLike = isDoorLikeGameObjectName(comp.getName())
					|| (action != null && doorActionPriorityIndex(action) < Integer.MAX_VALUE);
			if (!doorLike) continue;
			if (isCatalogTransportObject(g) && !isDoorLikeSceneObject(g)) continue;

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

	private static boolean hasLineOfSightBetween(WorldPoint a, WorldPoint b) {
		if (a == null || b == null) return false;
		return a.toWorldArea().hasLineOfSightTo(
				Microbot.getClient().getTopLevelWorldView(),
				b.toWorldArea());
	}

	/**
	 * Path-adjacent door resolver: only interact with objects that are on/adjacent to
	 * a blocked path edge near the player. Prevents clicking random "door-like" junk
	 * that isn't the blocker.
	 */
	private static boolean tryResolvePathAdjacentBlocker(WorldPoint playerLoc, List<WorldPoint> path, int startIdx, int scanAheadEdges, int radiusTiles) {
		if (playerLoc == null || path == null || path.size() < 2) return false;
		if (startIdx < 0) startIdx = 0;
		if (startIdx >= path.size() - 1) return false;

		int endEdgeIdx = Math.min(path.size() - 2, startIdx + Math.max(0, scanAheadEdges));
        final int pathEdgeDoorMaxDist = 4;
        Map<String, PathAdjDoorCandidate> byIdentity = new LinkedHashMap<>();

		for (int edgeIdx = startIdx; edgeIdx <= endEdgeIdx; edgeIdx++) {
			WorldPoint from = path.get(edgeIdx);
			WorldPoint to = path.get(edgeIdx + 1);
			if (from == null || to == null) continue;

			// Only edges "near enough" to matter.
			int dFrom = from.distanceTo2D(playerLoc);
			int dTo = to.distanceTo2D(playerLoc);
			if (Math.min(dFrom, dTo) > radiusTiles) continue;

			// Only treat as blocker if the next tile is unreachable OR the edge has no LOS.
			boolean blocked = Rs2DoorAheadResolver.isPathEdgeBlocked(from, to);
			if (!blocked) continue;

			// Scan candidates in loaded scene radius around player.
			for (WallObject w : Rs2GameObject.getWallObjects(o -> true, playerLoc, radiusTiles)) {
				if (w == null) continue;
				WorldPoint objWp = w.getWorldLocation();
				if (objWp == null) continue;
				if (!Rs2GameObject.hasLineOfSight(playerLoc, w)) continue;
                if (wasStationaryDoorOpenedRecently(objWp)) continue;

				if (objWp.distanceTo2D(from) > pathEdgeDoorMaxDist && objWp.distanceTo2D(to) > pathEdgeDoorMaxDist) {
					continue;
				}
				if (!isDoorOnSegment(w, from, to)) {
					continue;
				}

				ObjectComposition comp = resolveCompositionForDoorProbe(w);
				if (comp == null || isNullOrPlaceholderObjectName(comp.getName())) continue;
				if (doorCompositionSpecifiesOnlyCloseOrShut(comp)) continue;

				String action = pickWalkDoorAction(comp);
				boolean doorLike = isDoorLikeGameObjectName(comp.getName())
						|| (action != null && doorActionPriorityIndex(action) < Integer.MAX_VALUE);
				if (!doorLike) continue;
				if (isCatalogTransportObject(w) && !isDoorLikeSceneObject(w)) continue;

				String actionFinal = action == null ? "" : action;

				int edgeDist = Math.min(objWp.distanceTo2D(from), objWp.distanceTo2D(to));
				int pri = actionFinal.isEmpty() ? Integer.MAX_VALUE : doorActionPriorityIndex(actionFinal);
                mergePathAdjCandidate(
                        byIdentity,
                        w,
                        objWp,
                        actionFinal,
                        pri,
                        edgeIdx,
                        from,
                        to,
                        edgeDist);
			}

			for (GameObject g : Rs2GameObject.getGameObjects(o -> true, playerLoc, radiusTiles)) {
				if (g == null) continue;
				WorldPoint objWp = g.getWorldLocation();
				if (objWp == null) continue;
				if (!Rs2GameObject.hasLineOfSight(playerLoc, g)) continue;
                if (wasStationaryDoorOpenedRecently(objWp)) continue;
				if (objWp.distanceTo2D(from) > pathEdgeDoorMaxDist && objWp.distanceTo2D(to) > pathEdgeDoorMaxDist) {
					continue;
				}
				if (!isDoorOnSegment(g, from, to)) {
					continue;
				}

				ObjectComposition comp = resolveCompositionForDoorProbe(g);
				if (comp == null || isNullOrPlaceholderObjectName(comp.getName())) continue;
				if (doorCompositionSpecifiesOnlyCloseOrShut(comp)) continue;

				String action = pickWalkDoorAction(comp);
				boolean doorLike = isDoorLikeGameObjectName(comp.getName())
						|| (action != null && doorActionPriorityIndex(action) < Integer.MAX_VALUE);
				if (!doorLike) continue;
				if (isCatalogTransportObject(g) && !isDoorLikeSceneObject(g)) continue;

				String actionFinal = action == null ? "" : action;

				int edgeDist = Math.min(objWp.distanceTo2D(from), objWp.distanceTo2D(to));
				int pri = actionFinal.isEmpty() ? Integer.MAX_VALUE : doorActionPriorityIndex(actionFinal);
                mergePathAdjCandidate(
                        byIdentity,
                        g,
                        objWp,
                        actionFinal,
                        pri,
                        edgeIdx,
                        from,
                        to,
                        edgeDist);
			}
		}
        if (byIdentity.isEmpty()) {
			log.info("[Walker] path-adj blocker-scan: no candidates (radius={} idx={}/{})", radiusTiles, startIdx, path.size());
			return false;
		}

        List<PathAdjDoorComponent> components = buildPathAdjDoorComponents(byIdentity.values(), startIdx, playerLoc);
        if (components.isEmpty()) {
            log.info("[Walker] path-adj blocker-scan: no components (radius={} idx={}/{})", radiusTiles, startIdx, path.size());
            return false;
        }
        PathAdjDoorComponent bestComponent = components.stream()
                .min(Comparator.comparingInt(c -> c.score))
                .orElse(null);
        if (bestComponent == null || bestComponent.best == null) {
            log.info("[Walker] path-adj blocker-scan: no component winner (radius={} idx={}/{})", radiusTiles, startIdx, path.size());
            return false;
        }
        PathAdjDoorCandidate chosen = bestComponent.best;
        TileObject best = chosen.object;
        String bestAction = chosen.action;
        int bestScore = bestComponent.score;
        WorldPoint bestFrom = chosen.from;
        WorldPoint bestTo = chosen.to;
        if (isRecentTransportEdgeCandidate(chosen.location, bestFrom, bestTo)) {
            WebWalkLog.spInfo("path_adj_recent_transport_skip | probe={} from={} to={} origin={} dest={}",
                    compactWorldPoint(chosen.location),
                    compactWorldPoint(bestFrom),
                    compactWorldPoint(bestTo),
                    compactWorldPoint(lastTransportOriginLocation),
                    compactWorldPoint(lastTransportDestinationLocation));
            return false;
        }
		log.info("[Walker] path-adj blocker-scan: score={} action={} at {}", bestScore, (bestAction == null || bestAction.isEmpty()) ? "<default>" : bestAction, chosen.location);
		WorldPoint bestLoc = chosen.location;
		if (shouldThrottleDoorAttempt(bestLoc, bestFrom, bestTo)) {
			WebWalkLog.spInfo("door_attempt_throttled | mode=path-adj probe={} from={} to={}",
					compactWorldPoint(bestLoc), compactWorldPoint(bestFrom), compactWorldPoint(bestTo));
            for (WorldPoint loc : bestComponent.locations) {
                if (loc != null) {
                    markStationaryDoorOpened(loc);
                }
            }
			return false;
		}
		if (shouldThrottleGlobalDoorInteraction()) {
			WebWalkLog.spInfo("door_global_await | mode=path-adj probe={} from={} to={}",
					compactWorldPoint(bestLoc), compactWorldPoint(bestFrom), compactWorldPoint(bestTo));
			return false;
		}
		markDoorAttempt(bestLoc, bestFrom, bestTo);
		markGlobalDoorInteractionCooldown();
		WorldPoint posBefore = Rs2Player.getWorldLocation();
		boolean interacted;
		try {
			if (bestAction == null || bestAction.isEmpty()) {
				interacted = Rs2GameObject.interact(best);
			} else {
				interacted = Rs2GameObject.interact(best, bestAction);
			}
		} catch (Exception ex) {
			WebWalkLog.spInfo("door_interact_exception | mode=path-adj probe={} from={} to={} ex={}",
					compactWorldPoint(bestLoc), compactWorldPoint(bestFrom), compactWorldPoint(bestTo), ex.getClass().getSimpleName());
            for (WorldPoint loc : bestComponent.locations) {
                if (loc != null) {
                    markStationaryDoorOpened(loc);
                }
            }
			return false;
		}
		if (!interacted) {
			WebWalkLog.spInfo("door_interact_failed | mode=path-adj probe={} from={} to={}",
					compactWorldPoint(bestLoc), compactWorldPoint(bestFrom), compactWorldPoint(bestTo));
            for (WorldPoint loc : bestComponent.locations) {
                if (loc != null) {
                    markStationaryDoorOpened(loc);
                }
            }
			return false;
		}
        markDoorInteractionSettling();
		waitForDoorInteractionProgress(bestFrom, bestTo);
		WorldPoint posAfter = Rs2Player.getWorldLocation();
		boolean traversed = didTraverseInteractedDoor(posBefore, posAfter, bestLoc, bestFrom, bestTo);
		if (traversed) {
            for (WorldPoint loc : bestComponent.locations) {
                if (loc != null) {
                    markStationaryDoorOpened(loc);
                }
            }
			return true;
		}
        boolean wrongTraversal = bestLoc != null && shouldBlacklistDoorAfterWrongTraversal(posBefore, posAfter, bestFrom, bestTo);
        if (wrongTraversal) {
            log.warn("[Walker] Path-adj door traversed wrong way; not session-blacklisting fallback candidate: door={} from={} to={} before={} after={}",
                    bestLoc, bestFrom, bestTo, posBefore, posAfter);
        } else {
            for (WorldPoint loc : bestComponent.locations) {
                if (loc != null) {
                    markStationaryDoorOpened(loc);
                }
            }
        }
		log.debug("[Walker] path-adj blocker-scan interact did not traverse (at={} from={} to={} before={} after={})",
				bestLoc, bestFrom, bestTo, posBefore, posAfter);
        // Interaction was sent and awaited; yield this pass so unreachable recovery
        // does not immediately fire a minimap click while door traversal settles.
		return true;
	}

    private static void mergePathAdjCandidate(
            Map<String, PathAdjDoorCandidate> byIdentity,
            TileObject object,
            WorldPoint location,
            String action,
            int actionPriority,
            int edgeIdx,
            WorldPoint from,
            WorldPoint to,
            int edgeDist) {
        if (object == null || location == null) {
            return;
        }
        if (isCatalogTransportObject(object) && !isDoorLikeSceneObject(object)) {
            return;
        }
        String identity = object.getClass().getSimpleName() + "|" + object.getId() + "|"
                + location.getX() + "," + location.getY() + "," + location.getPlane();
        String familyKey = normalizePathAdjFamilyKey(object, action);
        PathAdjDoorCandidate incoming = new PathAdjDoorCandidate(
                object,
                location,
                action == null ? "" : action,
                actionPriority,
                edgeIdx,
                from,
                to,
                edgeDist,
                familyKey);
        PathAdjDoorCandidate existing = byIdentity.get(identity);
        if (existing == null) {
            byIdentity.put(identity, incoming);
            return;
        }
        if (incoming.edgeIdx < existing.edgeIdx
                || (incoming.edgeIdx == existing.edgeIdx && incoming.edgeDist < existing.edgeDist)) {
            byIdentity.put(identity, incoming);
        }
    }

    private static String normalizePathAdjFamilyKey(TileObject object, String action) {
        ObjectComposition comp = resolveCompositionForDoorProbe(object);
        String name = comp != null && comp.getName() != null ? comp.getName().toLowerCase(Locale.ROOT).trim() : "unknown";
        String act = action == null ? "" : action.toLowerCase(Locale.ROOT).trim();
        WorldPoint loc = object != null ? object.getWorldLocation() : null;
        int plane = loc != null ? loc.getPlane() : -1;
        int objectId = object != null ? object.getId() : -1;
        int idRangeLow = objectId >= 0 ? objectId - 1 : -1;
        int idRangeHigh = objectId >= 0 ? objectId + 1 : -1;
        return name + "|" + act + "|p" + plane + "|id=" + idRangeLow + "-" + idRangeHigh;
    }

    private static boolean arePathAdjFamiliesCompatible(String a, String b) {
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

    private static int[] parsePathAdjIdRange(String range) {
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

    private static void markNearbyDoorFamilyOpened(TileObject originObject, WorldPoint originLocation, String action, int radiusTiles) {
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
            ObjectComposition comp = resolveCompositionForDoorProbe(wall);
            String neighborFamily = normalizePathAdjFamilyKey(wall, comp == null ? null : pickWalkDoorAction(comp));
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
            ObjectComposition comp = resolveCompositionForDoorProbe(game);
            String neighborFamily = normalizePathAdjFamilyKey(game, comp == null ? null : pickWalkDoorAction(comp));
            if (arePathAdjFamiliesCompatible(familyKey, neighborFamily)) {
                markStationaryDoorOpened(game.getWorldLocation());
            }
        }
    }

    private static List<PathAdjDoorComponent> buildPathAdjDoorComponents(
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

    private static final class PathAdjDoorCandidate {
        private final TileObject object;
        private final WorldPoint location;
        private final String action;
        private final int actionPriority;
        private final int edgeIdx;
        private final WorldPoint from;
        private final WorldPoint to;
        private final int edgeDist;
        private final String familyKey;

        private PathAdjDoorCandidate(TileObject object, WorldPoint location, String action, int actionPriority,
                                     int edgeIdx, WorldPoint from, WorldPoint to, int edgeDist, String familyKey) {
            this.object = object;
            this.location = location;
            this.action = action;
            this.actionPriority = actionPriority;
            this.edgeIdx = edgeIdx;
            this.from = from;
            this.to = to;
            this.edgeDist = edgeDist;
            this.familyKey = familyKey;
        }
    }

    private static final class PathAdjDoorComponent {
        private final PathAdjDoorCandidate best;
        private final int score;
        private final Set<WorldPoint> locations;

        private PathAdjDoorComponent(PathAdjDoorCandidate best, int score, Set<WorldPoint> locations) {
            this.best = best;
            this.score = score;
            this.locations = locations;
        }
    }

	/**
	 * Scan a few path indices near the player (<= radius tiles) and attempt to resolve
	 * any door/gate blocks before issuing further minimap clicks.
	 */
	private static boolean tryHandleNearbyDoorsWithTimeout(List<WorldPoint> path, int startIdx, int radiusTiles, long timeoutMs) {
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

	/**
	 * Predict blockers on the path by probing the next few path edges for door/gate-like
	 * objects (including diagonal corners). If any probe tile contains a door-like object
	 * within {@code radiusTiles} of the player, run door handling with a bounded wait.
	 */
	private static boolean tryHandleBlockingPathObjectsWithTimeout(
			List<WorldPoint> path,
			int startIdx,
			int radiusTiles,
			int maxEdges,
			long timeoutMs,
            Map<String, WorldPoint> attemptedDoorEdgesThisPass)
	{
		if (path == null || path.size() < 2) return false;
		if (startIdx < 0) return false;
		final WorldPoint playerLoc = Rs2Player.getWorldLocation();
		if (playerLoc == null) return false;

		int start = Math.min(startIdx, path.size() - 2);
		int edgesChecked = 0;
		for (int j = start; j < path.size() - 1 && edgesChecked < maxEdges; j++, edgesChecked++) {
			WorldPoint from = path.get(j);
			WorldPoint to = path.get(j + 1);
			if (from == null || to == null) continue;
			if (from.getPlane() != playerLoc.getPlane() || to.getPlane() != playerLoc.getPlane()) break;

			// Only bother probing edges near the player; far edges are not loaded in scene.
			if (from.distanceTo2D(playerLoc) > radiusTiles && to.distanceTo2D(playerLoc) > radiusTiles) {
				break;
			}

			boolean diagonal = from.getX() != to.getX() && from.getY() != to.getY();
			List<WorldPoint> probes = new ArrayList<>();
			probes.add(from);
			probes.add(to);
			if (diagonal) {
				probes.add(new WorldPoint(to.getX(), from.getY(), from.getPlane()));
				probes.add(new WorldPoint(from.getX(), to.getY(), from.getPlane()));
			}

			for (WorldPoint probe : probes) {
				if (probe == null) continue;
				if (probe.getPlane() != playerLoc.getPlane()) continue;
				if (probe.distanceTo2D(playerLoc) > radiusTiles) continue;

				WallObject wall = Rs2GameObject.getWallObject(o -> o.getWorldLocation().equals(probe), probe, 3);
				TileObject object = (wall != null)
						? wall
						: Rs2GameObject.getGameObject(o -> o.getWorldLocation().equals(probe), probe, 3);
				if (object == null) continue;

				ObjectComposition comp = resolveCompositionForDoorProbe(object);
				if (comp == null || isNullOrPlaceholderObjectName(comp.getName())) continue;
				if (doorCompositionSpecifiesOnlyCloseOrShut(comp)) continue;

				// Gate by "door-like" name or by having a known door-like action.
				String action = Arrays.stream(comp.getActions())
						.filter(Objects::nonNull)
						.filter(act -> !isDoorCloseOrShutAction(act))
						.filter(act -> doorActionPriorityIndex(act) < Integer.MAX_VALUE)
						.min(Comparator.comparingInt(Rs2Walker::doorActionPriorityIndex))
						.orElse(null);
				boolean doorLike = isDoorLikeGameObjectName(comp.getName()) || action != null;
				if (!doorLike) continue;
				if (isCatalogTransportObject(object) && !isDoorLikeSceneObject(object)) continue;

				// Found a likely blocker on-path: hand off to existing door handler (which
				// includes quest-lock detection, blacklisting, and recalculation).
				if (handleDoorsWithTimeout(path, j, timeoutMs, attemptedDoorEdgesThisPass)) {
					return true;
				}
			}
		}
		return false;
	}

    private static boolean handleDoorException(TileObject object, String action) {
        if (isInStrongholdOfSecurity()) {
            return handleStrongholdOfSecurityAnswer(object, action);
        }
        return false;
    }

    private static boolean isInStrongholdOfSecurity() {
        List<Integer> mapRegionIds = List.of(7505, 7504, 7760, 7503, 7759, 7758, 7757, 8013, 7756, 8012, 8017, 8530, 9297);
        return mapRegionIds.contains(Rs2Player.getWorldLocation().getRegionID());
    }

    private static boolean handleStrongholdOfSecurityAnswer(TileObject object, String action) {
        Rs2GameObject.interact(object, action);
        boolean isInDialogue = Rs2Dialogue.sleepUntilInDialogue();

        // Not all the doors ask questions, so only if dialogue is shown we will attempt to get the answer
        if (!isInDialogue) return true;

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
    private static boolean searchNeighborPoint(int orientation, WorldPoint point, WorldPoint neighbor) {
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

    /**
     * @param path list of worldpoints
     * @return closest tile index
     */
    public static int getClosestTileIndex(List<WorldPoint> path) {
        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        return getClosestTileIndex(path, playerLoc, getClosestIndexReachableTiles(playerLoc));
    }

    static int getClosestTileIndex(List<WorldPoint> path, WorldPoint playerLoc) {
        return getClosestTileIndex(path, playerLoc, getClosestIndexReachableTiles(playerLoc));
    }

    static int getClosestTileIndex(List<WorldPoint> path,
                                   WorldPoint playerLoc,
                                   Map<WorldPoint, Integer> reachableTiles) {
        if (path == null || path.isEmpty() || playerLoc == null) {
            return -1;
        }

        int bestReachableIndex = -1;
        int bestReachableDistance = Integer.MAX_VALUE;
        if (reachableTiles != null && !reachableTiles.isEmpty()) {
            for (int i = 0; i < path.size(); i++) {
                WorldPoint point = path.get(i);
                if (point == null) {
                    continue;
                }
                int reachableDistance = reachableTiles.getOrDefault(point, Integer.MAX_VALUE);
                if (reachableDistance < bestReachableDistance) {
                    bestReachableDistance = reachableDistance;
                    bestReachableIndex = i;
                    if (reachableDistance == 0) {
                        break;
                    }
                }
            }
        }

        if (bestReachableIndex >= 0 && bestReachableDistance != Integer.MAX_VALUE) {
            return bestReachableIndex;
        }

        int closestIndex = -1;
        int closestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < path.size(); i++) {
            WorldPoint point = path.get(i);
            if (point == null) {
                continue;
            }
            int distance = playerLoc.distanceTo(point);
            if (distance < closestDistance) {
                closestDistance = distance;
                closestIndex = i;
            }
        }
        return closestIndex;
    }

    private static HashMap<WorldPoint, Integer> getClosestIndexReachableTiles(WorldPoint playerLoc) {
        if (playerLoc == null) {
            return new HashMap<>();
        }
        HashMap<WorldPoint, Integer> tiles = Rs2Tile.getReachableTilesFromTile(playerLoc, 20);

        // If an animation/shortcut puts the player on a collision-odd tile, keep route progress
        // anchored by distance instead of repeatedly recalculating an empty reachable set.
        if (tiles.isEmpty()) {
            tiles = Rs2Tile.getReachableTilesFromTileIgnoreCollision(playerLoc, 20);
        }
        return tiles;
    }

    static int stabilizeRouteProgressIndex(List<WorldPoint> path, int closestIdx, WorldPoint target, WorldPoint playerLoc) {
        if (path == null || path.isEmpty() || closestIdx < 0 || closestIdx >= path.size()) {
            return closestIdx;
        }

        WorldPoint pathStart = path.get(0);
        WorldPoint pathEnd = path.get(path.size() - 1);
        boolean routeChanged = routeProgressTarget == null
                || !routeProgressTarget.equals(target)
                || routeProgressPathSize != path.size()
                || !Objects.equals(routeProgressPathStart, pathStart)
                || !Objects.equals(routeProgressPathEnd, pathEnd)
                || routeProgressIdx >= path.size();
        if (routeChanged) {
            routeProgressTarget = target;
            routeProgressPathStart = pathStart;
            routeProgressPathEnd = pathEnd;
            routeProgressPathSize = path.size();
            routeProgressIdx = closestIdx;
            routeProgressAdvancedAtMs = System.currentTimeMillis();
            return closestIdx;
        }

        if (routeProgressIdx < 0 || closestIdx >= routeProgressIdx) {
            if (closestIdx > routeProgressIdx) {
                recordRouteProgressAdvanced();
            }
            routeProgressIdx = closestIdx;
            return closestIdx;
        }

        int forwardIdx = closestForwardPathIndex(path, routeProgressIdx, playerLoc);
        if (forwardIdx >= routeProgressIdx) {
            if (forwardIdx > routeProgressIdx) {
                routeProgressIdx = forwardIdx;
                recordRouteProgressAdvanced();
            }
            return routeProgressIdx;
        }
        return routeProgressIdx;
    }

    static void hintRouteProgressIndex(List<WorldPoint> path, int hintedIdx, WorldPoint target) {
        if (path == null || path.isEmpty() || hintedIdx < 0 || hintedIdx >= path.size()) {
            return;
        }

        WorldPoint pathStart = path.get(0);
        WorldPoint pathEnd = path.get(path.size() - 1);
        boolean routeChanged = routeProgressTarget == null
                || !routeProgressTarget.equals(target)
                || routeProgressPathSize != path.size()
                || !Objects.equals(routeProgressPathStart, pathStart)
                || !Objects.equals(routeProgressPathEnd, pathEnd)
                || routeProgressIdx >= path.size();
        if (routeChanged) {
            routeProgressTarget = target;
            routeProgressPathStart = pathStart;
            routeProgressPathEnd = pathEnd;
            routeProgressPathSize = path.size();
            routeProgressIdx = hintedIdx;
            recordRouteProgressAdvanced();
            return;
        }

        if (hintedIdx > routeProgressIdx) {
            routeProgressIdx = hintedIdx;
            recordRouteProgressAdvanced();
        }
    }

    static int advanceIndexPastRecentTransportEdge(List<WorldPoint> path, int index, WorldPoint playerLoc) {
        if (path == null || path.isEmpty() || index < 0 || index >= path.size()
                || !isRecentTransportEdgeWindow()) {
            return index;
        }
        WorldPoint origin = lastTransportOriginLocation;
        WorldPoint destination = lastTransportDestinationLocation;
        if (origin == null || destination == null || playerLoc == null
                || playerLoc.getPlane() != destination.getPlane()
                || playerLoc.distanceTo2D(destination) > 3) {
            return index;
        }

        int scanEndExclusive = Math.min(path.size(), index + 8);
        int lastTransportEdgeIdx = -1;
        for (int i = index; i < scanEndExclusive; i++) {
            WorldPoint point = path.get(i);
            if (isNearSamePlane(point, origin, 2) || isNearSamePlane(point, destination, 2)) {
                lastTransportEdgeIdx = i;
            }
        }
        if (lastTransportEdgeIdx >= index && lastTransportEdgeIdx + 1 < path.size()) {
            return lastTransportEdgeIdx + 1;
        }
        return index;
    }

    private static int closestForwardPathIndex(List<WorldPoint> path, int fromIdx, WorldPoint playerLoc) {
        if (path == null || path.isEmpty() || playerLoc == null || fromIdx < 0 || fromIdx >= path.size()) {
            return -1;
        }
        int bestIdx = -1;
        int bestDist = Integer.MAX_VALUE;
        int toIdxExclusive = Math.min(path.size(), fromIdx + ROUTE_PROGRESS_FORWARD_SEARCH_TILES + 1);
        for (int i = fromIdx; i < toIdxExclusive; i++) {
            WorldPoint point = path.get(i);
            if (point == null || point.getPlane() != playerLoc.getPlane()) {
                continue;
            }
            int dist = playerLoc.distanceTo2D(point);
            if (dist < bestDist) {
                bestIdx = i;
                bestDist = dist;
            }
        }
        return bestIdx;
    }

    private static void resetRouteProgress() {
        routeProgressIdx = -1;
        routeProgressTarget = null;
        routeProgressPathStart = null;
        routeProgressPathEnd = null;
        routeProgressPathSize = -1;
        routeProgressAdvancedAtMs = 0L;
    }

    private static void recordRouteProgressAdvanced() {
        long now = System.currentTimeMillis();
        routeProgressAdvancedAtMs = now;
        lastMovedTimeMs = now;
        stuckCount = 0;
    }

    private static boolean isRecentTransportEdgeWindow() {
        long handledAt = lastTransportHandledAtMs;
        if (handledAt <= 0L) {
            return false;
        }
        long ageMs = System.currentTimeMillis() - handledAt;
        return ageMs >= 0L && ageMs <= RECENT_TRANSPORT_EDGE_SUPPRESS_MS;
    }

    private static boolean isNearSamePlane(WorldPoint a, WorldPoint b, int distance) {
        return a != null
                && b != null
                && a.getPlane() == b.getPlane()
                && a.distanceTo2D(b) <= distance;
    }

    private static boolean isRecentTransportEdgeCandidate(WorldPoint objectLoc, WorldPoint from, WorldPoint to) {
        if (!isRecentTransportEdgeWindow()) {
            return false;
        }
        WorldPoint origin = lastTransportOriginLocation;
        WorldPoint destination = lastTransportDestinationLocation;
        if (origin == null || destination == null) {
            return false;
        }
        boolean objectNearTransport = isNearSamePlane(objectLoc, origin, 2)
                || isNearSamePlane(objectLoc, destination, 2);
        boolean edgeMatchesTransport = (isNearSamePlane(from, origin, 2) && isNearSamePlane(to, destination, 2))
                || (isNearSamePlane(from, destination, 2) && isNearSamePlane(to, origin, 2))
                || (objectNearTransport
                && (isNearSamePlane(from, origin, 2)
                || isNearSamePlane(from, destination, 2)
                || isNearSamePlane(to, origin, 2)
                || isNearSamePlane(to, destination, 2)));
        return objectNearTransport && edgeMatchesTransport;
    }

    /**
     * Force the walker to recalculate path
     */
    public static void recalculatePath() {
        WorldPoint goal = currentTarget;
        if (goal == null) {
            return;
        }
        // Must not call setTarget(null)+setTarget(goal): that briefly clears {@link #currentTarget},
        // and processWalk on another thread treats null as cancel (isWalkCancelled).
        Rs2WalkerLifecycleRuntime.applyWalkerDestination(goal);
    }

    /**
     * Updates world-map marker and restarts pathfinding for {@code target}. Does not assign
     * {@link #currentTarget}; callers set it when appropriate.
     */
    private static void applyWalkerDestination(WorldPoint target) {
        Rs2WalkerLifecycleRuntime.applyWalkerDestination(target);
    }

    /**
     * @param target destination, or {@code null} to clear (prefer {@link #clearWalkingRoute(String)} for observability)
     */
    public static void setTarget(WorldPoint target) {
        setTarget(target, null);
    }

    /**
     * @param clearReasonWhenNull logged when {@code target} is {@code null}; omit only from tests or legacy paths.
     *                         Clearing ({@code target == null}) runs without a {@link net.runelite.client.Client}
     *                         (teardown-safe). Non-null destinations still require a live client and login/player checks.
     */
    public static void setTarget(WorldPoint target, String clearReasonWhenNull) {
        if (target != null && !Microbot.isLoggedIn()) {
            log.warn("Unable to set target: not logged in");
            return;
        }
        if (target != null) {
            Client client = Microbot.getClient();
            if (client == null) {
                log.warn("Unable to set target: client unavailable");
                return;
            }
            Player localPlayer = client.getLocalPlayer();
            if (!Rs2PathApi.isStartPointSet() && localPlayer == null) {
                log.warn("Start point is not set and player is null");
                return;
            }
        }

        currentTarget = target;

        if (target == null) {
            // A completed/cancelled route owns its transport handoff context. Keeping the
            // timestamp alive made an unrelated walk started within 15 seconds inherit
            // post-transport handler suppression and misleading elapsed-time markers.
            clearRecentTransportContext();
            resetRouteProgress();
            logRouteClear(clearReasonWhenNull);
            synchronized (Rs2PathApi.getPathfinderMutex()) {
                final Pathfinder pathfinder = Rs2PathApi.getPathfinder();
                if (pathfinder != null) {
                    pathfinder.cancel();
                }
                Future<?> pathfinderFuture = Rs2PathApi.getPathfinderFuture();
                if (pathfinderFuture != null && !pathfinderFuture.isDone()) {
                    pathfinderFuture.cancel(true);
                }
                Rs2PathApi.setPathfinderFuture(null);
                Rs2PathApi.setPathfinder(null);
            }

            WorldMapPointManager wmm = Microbot.getWorldMapPointManager();
            if (wmm != null) {
                wmm.remove(Rs2PathApi.getMarker());
            } else if (Rs2LogRateLimit.once(WORLD_MAP_REMOVE_NULL_LOGGED)) {
                log.debug("[Walker] WorldMapPointManager null during route clear — marker may linger until teardown");
            }
            Rs2PathApi.setMarker(null);
            Rs2PathApi.setStartPointSet(false);
        } else {
            applyWalkerDestination(target);
        }
    }

    private static void restoreTargetMarker(WorldPoint target) {
        if (target == null || Rs2PathApi.getMarker() != null) {
            return;
        }

        try {
            WorldMapPointManager wmm = Microbot.getWorldMapPointManager();
            if (wmm == null) {
                log.debug("[Walker] Cannot restore marker: WorldMapPointManager unavailable");
                return;
            }
            Rs2PathApi.setMarker(new WorldMapPoint(target, Rs2PathApi.MARKER_IMAGE));
            Rs2PathApi.getMarker().setName("Target");
            Rs2PathApi.getMarker().setTarget(Rs2PathApi.getMarker().getWorldPoint());
            Rs2PathApi.getMarker().setJumpOnClick(true);
            wmm.add(Rs2PathApi.getMarker());
            log.info("[Walker] Restored missing path target marker at {}", target);
        } catch (Exception ex) {
            log.debug("[Walker] Failed to restore target marker at {}", target, ex);
        }
    }

    /**
     * @param start
     * @param end
     */
    public static boolean restartPathfinding(WorldPoint start, WorldPoint end) {
        return Rs2WalkerLifecycleRuntime.restartPathfinding(start, end);
    }

    public static boolean restartPathfinding(WorldPoint start, Set<WorldPoint> ends) {
        return Rs2WalkerLifecycleRuntime.restartPathfinding(start, ends);
    }

    /**
     * @param point
     * @return
     */
    public static Tile getTile(WorldPoint point) {
        LocalPoint a;
        if (Microbot.getClient().getTopLevelWorldView().isInstance()) {
            WorldPoint instancedWorldPoint = WorldPoint.toLocalInstance(Microbot.getClient().getTopLevelWorldView(), point).stream().findFirst().orElse(null);
            if (instancedWorldPoint == null) {
                log.error("getTile instancedWorldPoint is null");
                return null;
            }
            a = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), instancedWorldPoint);
        } else {
            a = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), point);
        }
        if (a == null) {
            return null;
        }
        return Microbot.getClient().getTopLevelWorldView().getScene().getTiles()[point.getPlane()][a.getSceneX()][a.getSceneY()];
    }

    /**
     * @param path
     * @param indexOfStartPoint
     * @return
     */
    private static boolean handleTransports(List<WorldPoint> path, int indexOfStartPoint) {
        if (path != null && indexOfStartPoint >= 0 && indexOfStartPoint < path.size() - 1
                && recentlyOpenedStationaryDoorOnSegment(path.get(indexOfStartPoint), path.get(indexOfStartPoint + 1))) {
            return false;
        }
        Set<Transport> transports = Rs2PathApi.getTransports().get(path.get(indexOfStartPoint));
        if (transports == null || transports.isEmpty()) {
            return false;
        }
        if (log.isDebugEnabled()) {
            log.debug("[Walker] handleTransports at {}: {} candidates — {}", path.get(indexOfStartPoint),
                    transports.size(),
                    transports.stream().map(Transport::getDisplayInfo).collect(Collectors.joining(", ")));
        }
        // When the player is inside a POH instance, the player's raw world-location plane is
        // the instance-template plane and has no relationship to the POH-transport origin plane.
        // Skip the plane guard in that case so POH transports can actually be considered.
        boolean inPohInstance = Microbot.getClient().getTopLevelWorldView().getScene().isInstance()
                && net.runelite.client.plugins.microbot.shortestpath.PohPanel.getExitPortalTile() != null;

        // Pre-compute path point index map for O(1) lookups instead of repeated O(n) scans
        Map<WorldPoint, Integer> pathFirstIndex = new HashMap<>(path.size());
        for (int idx = 0; idx < path.size(); idx++) {
            pathFirstIndex.putIfAbsent(path.get(idx), idx);
        }

        List<Transport> orderedTransports = new ArrayList<>(transports);
        orderedTransports.sort(Comparator.comparingInt(Rs2Walker::transportHandlingPreference));

        for (Transport transport : orderedTransports) {
            Collection<WorldPoint> worldPointCollections;
            //in some cases the getOrigin is null, for teleports that start the player location
            if (transport.getOrigin() == null) {
                worldPointCollections = Collections.singleton(null);
            } else if (inPohInstance && transport.getType() == TransportType.POH) {
                // POH fix: when the player is inside a POH instance, the transport's exit-portal
                // origin is an overworld tile that doesn't map into the player's instance chunks,
                // so toLocalInstance() returns an empty collection and the inner loop never runs.
                // Pass the origin through directly so the per-i dispatch below can execute.
                worldPointCollections = Collections.singleton(transport.getOrigin());
            } else {
                worldPointCollections = WorldPoint.toLocalInstance(Microbot.getClient().getTopLevelWorldView(), transport.getOrigin());
            }
            log.debug("[Walker] Considering transport: {} (type={}, origin={}, wpCount={})",
                    transport.getDisplayInfo(), transport.getType(), transport.getOrigin(), worldPointCollections.size());
            for (WorldPoint origin : worldPointCollections) {
                WorldPoint plOriginLoop = Rs2Player.getWorldLocation();
                if (!inPohInstance && transport.getOrigin() != null && plOriginLoop != null
                        && plOriginLoop.getPlane() != transport.getOrigin().getPlane()) {
                    continue;
                }

                // Hoist path-constant checks out of the inner loop: destination must exist in path
                if (!pathFirstIndex.containsKey(transport.getDestination())) {
                    log.debug("[Walker] skip {}: destination {} not in path", transport.getDisplayInfo(), transport.getDestination());
                    continue;
                }
                // QUETZAL is not {@link TransportType#isTeleport} — without this, stall/off-path recalc can re-open the map and
                // click the same landing repeatedly while already there (no movement → infinite stall loop).
                if (transport.getType() == TransportType.QUETZAL) {
                    if (isPlayerWithinChebyshevOf(transport.getDestination(), OFFSET)) {
                        log.debug("[Walker] skip {}: already within {} tiles of Quetzal destination {}",
                                transport.getDisplayInfo(), OFFSET, transport.getDestination());
                        continue;
                    }
                }
                if (TransportType.isTeleport(transport.getType(), transport.getOrigin())) {
                    if (isPlayerWithinChebyshevOf(transport.getDestination(), TELEPORT_NEAR_SKIP_CHEBYSHEV)) {
                        log.debug("[Walker] skip {}: already near destination", transport.getDisplayInfo());
                        continue;
                    }
                }

                // Pre-compute origin/destination indices once per transport (not per inner iteration)
                int precomputedIndexOfOrigin = -1;
                int precomputedIndexOfDest = -1;
                if (!TransportType.isTeleport(transport.getType(), transport.getOrigin())) {
                    Integer originIdx = pathFirstIndex.get(transport.getOrigin());
                    Integer destIdx = pathFirstIndex.get(transport.getDestination());
                    precomputedIndexOfOrigin = originIdx != null ? originIdx : -1;
                    precomputedIndexOfDest = destIdx != null ? destIdx : -1;
                    if (log.isDebugEnabled()) {
                        log.debug("[Walker] filter4 {}: indexOfOrigin={}, indexOfDestination={}, pathSize={}, originInPath={}, destInPath={}",
                                transport.getDisplayInfo(), precomputedIndexOfOrigin, precomputedIndexOfDest, path.size(),
                                precomputedIndexOfOrigin != -1, precomputedIndexOfDest != -1);
                    }
                    if (precomputedIndexOfDest == -1) continue;
                    if (precomputedIndexOfOrigin == -1) continue;
                    if (precomputedIndexOfDest < precomputedIndexOfOrigin) continue;
                }

                for (int i = indexOfStartPoint; i < path.size(); i++) {
                    WorldPoint plPathLoop = Rs2Player.getWorldLocation();
                    if (plPathLoop == null) {
                        // Cannot verify plane / dispatch — do not burn remaining path indices this tick.
                        break;
                    }
                    if (!inPohInstance && origin != null && origin.getPlane() != plPathLoop.getPlane()) {
                        log.debug("[Walker] skip {} (i={}): plane mismatch", transport.getDisplayInfo(), i);
                        break; // plane won't change across iterations, so break instead of continue
                    }

                    if (i == indexOfStartPoint) {
                        log.debug("[Walker] reached pre-dispatch for {}: i={}, path[i]={}, origin={}, equalsOrigin={}",
                                transport.getDisplayInfo(), i, path.get(i), origin, path.get(i).equals(origin));
                    }

                    if (path.get(i).equals(origin)) {
                        if (transport.getType() == TransportType.SHIP || transport.getType() == TransportType.NPC || transport.getType() == TransportType.BOAT) {

                            Rs2NpcModel npc = Rs2Npc.getNpc(transport.getName());

                            // Wrap with observation so Leagues blocked-region chat can attribute this attempt.
                            if (attemptObserved(transport, () -> npc != null && Rs2Npc.canWalkTo(npc, 20) && Rs2Npc.interact(npc, transport.getAction()))) {
                                Rs2Player.waitForWalking();
                                sleepUntil(Rs2Dialogue::isInDialogue,600*2);

                                if (Objects.equals(transport.getName(), "Veos") && Objects.equals(transport.getAction(), "Talk-to")) {
                                    sleepUntil(() -> !Rs2Dialogue.hasContinue(), Rs2Dialogue::clickContinue, 5000, Rs2Random.between(600, 800));
                                    Rs2Dialogue.clickOption("Can you take me somewhere?");
                                    sleepUntil(() -> !Rs2Dialogue.hasContinue() && !Rs2Dialogue.hasSelectAnOption(), Rs2Dialogue::clickContinue, 5000, Rs2Random.between(600, 800));
                                    Rs2Dialogue.clickOption(transport.getDisplayInfo());
                                    sleepUntil(() -> !Rs2Dialogue.hasContinue() && !Rs2Dialogue.hasSelectAnOption(), Rs2Dialogue::clickContinue, 5000, Rs2Random.between(600, 800));
                                }

                                if (Objects.equals(transport.getName(), "Captain Magoro") && Objects.equals(transport.getAction(), "Talk-to")) {
                                    sleepUntil(() -> !Rs2Dialogue.hasContinue(), Rs2Dialogue::clickContinue, 5000, Rs2Random.between(600, 800));
                                    Rs2Dialogue.clickOption(transport.getDisplayInfo());
                                    sleepUntil(() -> !Rs2Dialogue.hasContinue() && !Rs2Dialogue.hasSelectAnOption(), Rs2Dialogue::clickContinue, 5000, Rs2Random.between(600, 800));
                                }

                                if (Rs2Dialogue.clickOption("I'm just going to Pirates' cove")){
                                    sleepTickJitter(2);
                                    Rs2Dialogue.clickContinue();
                                } else if (Objects.equals(transport.getName(), "Mountain Guide")) {
                                    Rs2Dialogue.clickOption(transport.getDisplayInfo());
                                }
                                sleepUntil(() -> !Rs2Player.isAnimating());
                                boolean shipNearDest = sleepUntil(
                                        () -> isPlayerWithinChebyshevOf(transport.getDestination(), TRANSPORT_NEAR_LANDING_CHEBYSHEV),
                                        SHIP_NPC_BOAT_LANDING_WAIT_MS);
                                if (!shipNearDest) {
                                    WebWalkLog.spWarn(
                                            "ship/npc/boat post-travel wait timed out ({}ms) dest={} at={}",
                                            SHIP_NPC_BOAT_LANDING_WAIT_MS,
                                            compactWorldPoint(transport.getDestination()),
                                            compactWorldPoint(Rs2Player.getWorldLocation()));
                                }
                                boolean reachedDestination = shipNearDest;
                                sleepTickJitter(6);
                                if (reachedDestination) {
                                    return finishHandledTransport(transport);
                                }
                            } else {
                                WorldPoint originTile = path.get(i);
                                boolean clicked = Rs2Walker.walkFastCanvas(originTile);
                                if (!clicked) {
                                    WorldPoint playerLoc = Rs2Player.getWorldLocation();
                                    if (playerLoc != null) {
                                        clicked = walkMiniMapToward(originTile, playerLoc, 13);
                                    }
                                }
                                if (!clicked) {
                                    clicked = Rs2Walker.walkMiniMap(originTile);
                                }
                                if (!clicked) {
                                    log.debug("[Walker] ship/npc/boat fallback click failed for {}", originTile);
                                }
                                sleep(1200, 1600);
                            }
                        }

                        if (transport.getType() == TransportType.CHARTER_SHIP) {
                            if (attemptObserved(transport, () -> handleCharterShip(transport))) {
                                sleepUntil(() -> !Rs2Player.isAnimating());
                                boolean charterLanded = Rs2WalkerRuntimeAwaits.awaitCondition(
                                        () -> isPlayerWithinChebyshevOf(transport.getDestination(), TRANSPORT_NEAR_LANDING_CHEBYSHEV),
                                        TRANSPORT_LANDING_WAIT_POLL_MS,
                                        TRANSPORT_LANDING_WAIT_TIMEOUT_MS);
                                if (!charterLanded) {
                                    WebWalkLog.spWarn(
                                            "charter ship post-travel wait timed out ({}ms) dest={} at={}",
                                            TRANSPORT_LANDING_WAIT_TIMEOUT_MS,
                                            compactWorldPoint(transport.getDestination()),
                                            compactWorldPoint(Rs2Player.getWorldLocation()));
                                }
                                sleepTickJitter(4); // wait 4 extra ticks before walking
                                return finishHandledTransport(transport);
                            }
                        }
                    }

                    log.debug("[Walker] Handling {} transport: {} (i={}, path[i]={}, origin={})",
                            transport.getType(), transport.getDisplayInfo(), i, path.get(i), origin);
                    if (transport.getType() == TransportType.POH) {
                        boolean pohResult = attemptObserved(transport, () -> handlePohTransport(transport));
                        log.debug("[Walker] handlePohTransport({}) returned {}", transport.getDisplayInfo(), pohResult);
                        if (pohResult) {
                            // Shares ship/NPC/boat 10s landing budget — intentional single timeout constant.
                            boolean pohNearDest = sleepUntil(
                                    () -> isPlayerWithinChebyshevOf(transport.getDestination(), OFFSET),
                                    SHIP_NPC_BOAT_LANDING_WAIT_MS);
                            if (!pohNearDest) {
                                WebWalkLog.spWarn(
                                        "POH post-travel wait timed out ({}ms) dest={} at={}",
                                        SHIP_NPC_BOAT_LANDING_WAIT_MS,
                                        compactWorldPoint(transport.getDestination()),
                                        compactWorldPoint(Rs2Player.getWorldLocation()));
                            }
                            if (pohNearDest) {
                                return finishHandledTransport(transport);
                            }
                        }
                    }

                    if (transport.getType() == TransportType.CANOE) {
                        if (attemptObserved(transport, () -> handleCanoe(transport))) {
                            sleepTickJitter(2);
                            return finishHandledTransport(transport);
                        }
                    }

                    if (transport.getType() == TransportType.SPIRIT_TREE) {
                        if (!Rs2PathApi.getPathfinderConfig().isUseSpiritTrees()) {
                            log.debug("[Walker] skip spirit tree transport — setting is off");
                            continue;
                        }
                        if (attemptObserved(transport, () -> handleSpiritTree(transport))) {
                            sleepUntil(() -> !Rs2Player.isAnimating());
                            boolean spiritLanded = Rs2WalkerRuntimeAwaits.awaitCondition(
                                    () -> isPlayerWithinChebyshevOf(transport.getDestination(), OFFSET),
                                    TRANSPORT_LANDING_WAIT_POLL_MS,
                                    TRANSPORT_LANDING_WAIT_TIMEOUT_MS);
                            if (!spiritLanded) {
                                WebWalkLog.spWarn(
                                        "spirit tree post-travel wait timed out ({}ms) dest={} at={}",
                                        TRANSPORT_LANDING_WAIT_TIMEOUT_MS,
                                        compactWorldPoint(transport.getDestination()),
                                        compactWorldPoint(Rs2Player.getWorldLocation()));
                            }
                            if (spiritLanded) {
                                return finishHandledTransport(transport);
                            }
                        }
                    }

                    if (transport.getType() == TransportType.QUETZAL) {
                        if (attemptObserved(transport, () -> handleQuetzal(transport))) {
                            boolean landedNearDest = Rs2WalkerRuntimeAwaits.awaitCondition(
                                    () -> isPlayerWithinChebyshevOf(transport.getDestination(), OFFSET),
                                    TRANSPORT_LANDING_WAIT_POLL_MS,
                                    TRANSPORT_LANDING_WAIT_TIMEOUT_MS);
                            if (!landedNearDest) {
                                WebWalkLog.spWarn(
                                        "quetzal post-travel wait timed out ({}ms) dest={} at={}",
                                        TRANSPORT_LANDING_WAIT_TIMEOUT_MS,
                                        compactWorldPoint(transport.getDestination()),
                                        compactWorldPoint(Rs2Player.getWorldLocation()));
                            }
                            sleepTickJitter(2);
                            return finishHandledTransport(transport);
                        }
                    }

                    if (transport.getType() == TransportType.MAGIC_CARPET) {
                        if (attemptObserved(transport, () -> handleMagicCarpet(transport))) {
                            sleepTickJitter(2);
                            return finishHandledTransport(transport);
                        }
                    }

                    if (transport.getType() == TransportType.WILDERNESS_OBELISK) {
                        if (attemptObserved(transport, () -> handleWildernessObelisk(transport))) {
                            sleepTickJitter(2);
                            return finishHandledTransport(transport);
                        }
                    }

                    if (transport.getType() == TransportType.GNOME_GLIDER) {
                        if (attemptObserved(transport, () -> handleGlider(transport))) {
                            sleepUntil(() -> !Rs2Player.isAnimating());
                            sleepUntilTrue(() -> isPlayerWithinChebyshevOf(transport.getDestination(),
                                            TRANSPORT_NEAR_LANDING_CHEBYSHEV),
                                    TRANSPORT_LANDING_WAIT_POLL_MS, TRANSPORT_LANDING_WAIT_TIMEOUT_MS);
                            sleepTickJitter(3);
                            return finishHandledTransport(transport);
                        }
                    }

                    if (transport.getType() == TransportType.FAIRY_RING) {
                        WorldPoint plFairy = Rs2Player.getWorldLocation();
                        WorldPoint tdFairy = transport.getDestination();
                        boolean alreadyAtFairyDest = plFairy != null && tdFairy != null && plFairy.equals(tdFairy);
                        if (!alreadyAtFairyDest && attemptObserved(transport, () -> handleFairyRing(transport))) {
                            sleepUntilTrue(() -> isPlayerWithinChebyshevOf(transport.getDestination(), OFFSET),
                                    TRANSPORT_LANDING_WAIT_POLL_MS, TRANSPORT_LANDING_WAIT_TIMEOUT_MS);
                            return finishHandledTransport(transport);
                        }
                    }

                    if (transport.getType() == TransportType.TELEPORTATION_MINIGAME) {
                        if (attemptObserved(transport, () -> handleMinigameTeleport(transport))) {
                            sleepUntilTrue(() -> isPlayerWithinChebyshevOf(transport.getDestination(), OFFSET * 2),
                                    TRANSPORT_LANDING_WAIT_POLL_MS, TRANSPORT_LANDING_WAIT_TIMEOUT_MS);
                            return finishHandledTransport(transport);
                        }
                    }

                    if (transport.getType() == TransportType.TELEPORTATION_ITEM) {
                        if (attemptObserved(transport, () -> handleTeleportItem(transport))) {
                            sleepUntil(() -> !Rs2Player.isAnimating());
                            sleepUntilTrue(() -> isPlayerWithinChebyshevOf(transport.getDestination(), OFFSET),
                                    TRANSPORT_LANDING_WAIT_POLL_MS, TRANSPORT_LANDING_WAIT_TIMEOUT_MS);
                            return finishHandledTransport(transport);
                        }
                    }

                    if (transport.getType() == TransportType.TELEPORTATION_SPELL) {
                        if (attemptObserved(transport, () -> handleTeleportSpell(transport))) {
                            if (isLumbridgeHomeTeleport(transport)) {
                                sleepUntilTrue(() -> isPlayerWithinChebyshevOf(transport.getDestination(), OFFSET), 600, 35000);
                            } else {
                                sleepUntil(() -> !Rs2Player.isAnimating());
                                sleepUntilTrue(() -> isPlayerWithinChebyshevOf(transport.getDestination(), OFFSET),
                                        TRANSPORT_LANDING_WAIT_POLL_MS, TRANSPORT_LANDING_WAIT_TIMEOUT_MS);
                            }
                            Rs2Tab.switchTo(InterfaceTab.INVENTORY);
                            return finishHandledTransport(transport);
                        }
                    }

                    if (transport.getType() == TransportType.SEASONAL_TRANSPORT) {
                        if (attemptObservedWithoutAttemptRecord(transport, () -> handleSeasonalTransport(transport))) {
                            sleepUntil(() -> !Rs2Player.isAnimating());
                            sleepUntilTrue(() -> isPlayerWithinChebyshevOf(transport.getDestination(), OFFSET),
                                    TRANSPORT_LANDING_WAIT_POLL_MS, TRANSPORT_LANDING_WAIT_TIMEOUT_MS);
                            return finishHandledTransport(transport);
                        }
                    }

                    if (transport.getObjectId() <= 0) break;

                    final int transportObjectId = transport.getObjectId();
                    final String transportAction = transport.getAction();
                    final List<String> transportActions = getTransportActionOptions(transportAction);
                    // Climb-down transports have a closed-variant (trapdoor/manhole/grate/hatch)
                    // that shares the same tile but a different object ID. Infer the closed
                    // variant from ObjectComposition (any nearby object with an "Open" action
                    // and a matching name) rather than a hardcoded ID pair, so new variants
                    // work without a code change.
                    final boolean allowClosedVariant = "Climb-down".equalsIgnoreCase(transportAction)
                            || "Climb down".equalsIgnoreCase(transportAction);

                    final boolean allowAlKharidTollGateVariant = isAlKharidTollGateObjectId(transportObjectId);
                    List<TileObject> objects = Rs2GameObject.getAll(o -> {
                        if (o.getId() == transportObjectId) return true;
                        if (allowAlKharidTollGateVariant && isAlKharidTollGateObjectId(o.getId())) return true;
                        Integer legacyClosed = OPEN_TO_CLOSED_MAPPINGS.get(transportObjectId);
                        if (legacyClosed != null && o.getId() == legacyClosed) return true;
                        if (!allowClosedVariant) return false;
                        ObjectComposition comp = Rs2GameObject.convertToObjectComposition(o);
                        if (comp == null || comp.getActions() == null) return false;
                        String nm = comp.getName() == null ? "" : comp.getName().toLowerCase();
                        boolean nameMatches = nm.contains("trapdoor") || nm.contains("manhole")
                                || nm.contains("grate") || nm.contains("hatch");
                        if (!nameMatches) return false;
                        return Arrays.stream(comp.getActions()).filter(Objects::nonNull)
                                .anyMatch(a -> a.equalsIgnoreCase("Open"));
                    }, transport.getOrigin(), 10).stream()
                            .sorted(Comparator
                                    .comparingInt((TileObject o) -> resolveTransportObjectAction(o, transportActions).isPresent() ? 0 : 1)
                                    .thenComparingInt(o -> o.getWorldLocation().distanceTo(transport.getOrigin())))
                            .collect(Collectors.toList());

                    TileObject object = objects.stream().findFirst().orElse(null);
                    if (object instanceof GroundObject) {
                        object = objects.stream()
                                .filter(o -> !Objects.equals(o.getWorldLocation(), Rs2Player.getWorldLocation()))
                                .min(Comparator.comparing(o -> ((TileObject) o).getWorldLocation().distanceTo(transport.getOrigin()))
                                        .thenComparing(o -> ((TileObject) o).getWorldLocation().distanceTo(transport.getDestination()))).orElse(null);
                    }

                    if (object != null) {
                        // Skip reachability check for GroundObjects and Magic Mushtrees
                        if (!(object instanceof GroundObject) && !MagicMushtree.isMagicMushtree(transport.getObjectId())) {
                            if (!Rs2Tile.isTileReachable(transport.getOrigin())) {
                                break;
                            }
                        }

                        // Closed variant detection: if the found object doesn't advertise the
                        // transport action but does advertise "Open", open it first and re-find
                        // the now-open object before invoking handleObject.
                        ObjectComposition comp = Rs2GameObject.convertToObjectComposition(object);
                        if (comp != null && comp.getActions() != null) {
                            String[] actions = comp.getActions();
                            boolean hasTransportAction = resolveTransportObjectAction(actions, transportActions).isPresent();
                            boolean hasOpen = Arrays.stream(actions).filter(Objects::nonNull)
                                    .anyMatch(a -> a.equalsIgnoreCase("Open"));
                            if (!hasTransportAction && hasOpen) {
                                log.info("[Walker] Closed transport variant at {} (id={} name={}) — opening before {}",
                                        transport.getOrigin(), object.getId(), comp.getName(), transportAction);
                                final int closedId = object.getId();
                                Rs2GameObject.interact(object, "Open");
                                Rs2Player.waitForAnimation(2000);
                                TileObject reopened = Rs2GameObject.getAll(o -> {
                                    if (o.getId() == closedId) return false;
                                    ObjectComposition c = Rs2GameObject.convertToObjectComposition(o);
                                    if (c == null || c.getActions() == null) return false;
                                    return resolveTransportObjectAction(c.getActions(), transportActions).isPresent();
                                }, transport.getOrigin(), 3).stream()
                                        .min(Comparator.comparingInt(o -> o.getWorldLocation().distanceTo(transport.getOrigin())))
                                        .orElse(null);
                                if (reopened != null) object = reopened;
                            }
                        }

                        String interactionAction = resolveTransportObjectAction(object, transportActions)
                                .orElse(transportAction);
                        if (!Objects.equals(interactionAction, transportAction)) {
                            log.debug("[Walker] Using object action '{}' for transport action '{}' at {} (id={})",
                                    interactionAction, transportAction, object.getWorldLocation(), object.getId());
                        }
                        prepareTransportObjectForInteraction(object);
                        if (!handleObject(transport, object, interactionAction)) {
                            return false;
                        }
                        sleepUntil(() -> !Rs2Player.isAnimating());
                        WorldPoint destWait = transport.getDestination();
                        int maxInclusive = isAdjacentSamePlaneTransport(transport) ? 0 : OFFSET;
                        if (destWait == null) {
                            return false;
                        }
                        boolean landedAfterObject = waitForPostHandleObjectLanding(transport, destWait, maxInclusive);
                        if (!landedAfterObject) {
                            WorldPoint afterInteraction = Rs2Player.getWorldLocation();
                            // Adjacent same-plane transports demand landing on the EXACT destination
                            // tile (maxInclusive == 0), and agility shortcuts routinely deposit the
                            // player a tile off it — so a crossing can physically succeed while this
                            // check still fails. Suppression previously ran only on the success path,
                            // which left the inverse transport immediately eligible: the walker
                            // crossed, took the same shortcut straight back, and stranded itself. If
                            // we are no longer on the origin we did cross, so suppress both tiles
                            // regardless of the landing verdict. The landing result itself is
                            // unchanged — this still returns false and replans.
                            if (isAdjacentSamePlaneTransport(transport)
                                    && afterInteraction != null
                                    && !afterInteraction.equals(transport.getOrigin())) {
                                markAdjacentSamePlaneTransportHandled(transport, object);
                            }
                            WebWalkLog.spWarn(
                                    "post-handleObject landing unresolved (timeout={}ms) dest={} at={}",
                                    POST_HANDLE_OBJECT_LANDING_WAIT_MS,
                                    compactWorldPoint(destWait),
                                    compactWorldPoint(afterInteraction));
                        }
                        if (landedAfterObject) {
                            markAdjacentSamePlaneTransportHandled(transport, object);
                            return finishHandledTransport(transport);
                        }
                        return false;
                    }
                }
            }
        }
        return false;
    }

    private static boolean waitForPostHandleObjectLanding(Transport transport,
                                                          WorldPoint destWait,
                                                          int maxInclusive) {
        long waitStartedAt = System.currentTimeMillis();
        AtomicBoolean settledAwayFromAdjacentDestination = new AtomicBoolean(false);
        AtomicBoolean settledNearAdjacentDestination = new AtomicBoolean(false);
        boolean completed = sleepUntil(() -> {
            if (isPlayerWithinChebyshevInclusive(destWait, maxInclusive)) {
                return true;
            }
            if (!isAdjacentSamePlaneTransport(transport)
                    || System.currentTimeMillis() - waitStartedAt < POST_HANDLE_OBJECT_FAILED_SETTLE_MS) {
                return false;
            }
            WorldPoint playerLoc = Rs2Player.getWorldLocation();
            if (playerLoc == null || destWait == null || playerLoc.getPlane() != destWait.getPlane()
                    || Rs2Player.isMoving() || Rs2Player.isAnimating()) {
                return false;
            }
            if (isSettledNearAdjacentSamePlaneLanding(transport, playerLoc, destWait, maxInclusive)) {
                settledNearAdjacentDestination.set(true);
                return true;
            }
            WorldPoint origin = transport == null ? null : transport.getOrigin();
            boolean settledAwayFromOrigin = origin != null && playerLoc.distanceTo2D(origin) > 1;
            if (playerLoc.distanceTo2D(destWait) > Math.max(1, maxInclusive)
                    && settledAwayFromOrigin) {
                settledAwayFromAdjacentDestination.set(true);
                return true;
            }
            return false;
        }, POST_HANDLE_OBJECT_LANDING_WAIT_MS);

        if (settledNearAdjacentDestination.get()) {
            WebWalkLog.spInfo("post-handleObject adjacent landing accepted | dest={} at={}",
                    compactWorldPoint(destWait), compactWorldPoint(Rs2Player.getWorldLocation()));
            return true;
        }
        if (settledAwayFromAdjacentDestination.get()) {
            WebWalkLog.spInfo("post-handleObject adjacent landing failed | dest={} at={}",
                    compactWorldPoint(destWait), compactWorldPoint(Rs2Player.getWorldLocation()));
            return false;
        }
        return completed;
    }

    static boolean isSettledNearAdjacentSamePlaneLanding(Transport transport,
                                                         WorldPoint playerLoc,
                                                         WorldPoint destWait,
                                                         int maxInclusive) {
        if (!isAdjacentSamePlaneTransport(transport)
                || playerLoc == null
                || destWait == null
                || playerLoc.getPlane() != destWait.getPlane()) {
            return false;
        }
        WorldPoint origin = transport.getOrigin();
        if (origin == null || playerLoc.equals(origin)) {
            return false;
        }
        int destinationDistance = playerLoc.distanceTo2D(destWait);
        if (destinationDistance <= Math.max(1, maxInclusive)
                && playerLoc.distanceTo2D(origin) > 0) {
            return true;
        }
        if (transport.getType() != TransportType.AGILITY_SHORTCUT) {
            return false;
        }

        // Some adjacent shortcut catalogues describe a multi-object animation as one-tile
        // hops. The Falador stepping stones, for example, can carry 3154 -> 3149 while the
        // selected edge says 3154 -> 3153. Accept only a tightly bounded forward, collinear
        // overshoot; sideways movement, reverse movement, and arbitrary teleports still fail.
        int edgeX = destWait.getX() - origin.getX();
        int edgeY = destWait.getY() - origin.getY();
        int movedX = playerLoc.getX() - origin.getX();
        int movedY = playerLoc.getY() - origin.getY();
        int forwardProgress = movedX * edgeX + movedY * edgeY;
        int lateralOffset = Math.abs(movedX * edgeY - movedY * edgeX);
        return forwardProgress > 0
                && forwardProgress <= 6
                && lateralOffset <= 1;
    }

    /**
     * Handles the transportation process specifically for instances of PohTransport.
     * Any Transport param that reaches this is assumed to be a PohTransport.
     *
     * @param transport the transport object to be checked and processed
     * @return true if the transport is an instance of PohTransport and its transport method executes successfully, false otherwise
     */
    private static boolean handlePohTransport(Transport transport) {
        if(!(transport instanceof PohTransport)) {
            throw new IllegalStateException("handlePohTransport should not be called for non-PohTransports");
        }
        return ((PohTransport)transport).execute();
    }

    private static List<String> getTransportActionOptions(String action) {
        if (action == null || action.isBlank()) {
            return Collections.emptyList();
        }

        List<String> actions = new ArrayList<>();
        actions.add(action);
        if ("Bottom-floor".equalsIgnoreCase(action)) {
            actions.add("Climb-down");
            actions.add("Climb down");
        } else if ("Top-floor".equalsIgnoreCase(action)) {
            actions.add("Climb-up");
            actions.add("Climb up");
        }
        return actions;
    }

    private static Optional<String> resolveTransportObjectAction(TileObject object, List<String> actionOptions) {
        ObjectComposition comp = Rs2GameObject.convertToObjectComposition(object);
        if (comp == null || comp.getActions() == null) {
            return Optional.empty();
        }
        return resolveTransportObjectAction(comp.getActions(), actionOptions);
    }

    private static Optional<String> resolveTransportObjectAction(String[] objectActions, List<String> actionOptions) {
        if (objectActions == null || actionOptions == null || actionOptions.isEmpty()) {
            return Optional.empty();
        }

        for (String desired : actionOptions) {
            for (String actual : objectActions) {
                if (actual != null && desired.equalsIgnoreCase(Rs2UiHelper.stripColTags(actual))) {
                    return Optional.of(actual);
                }
            }
        }
        return Optional.empty();
    }

    private static void prepareTransportObjectForInteraction(TileObject tileObject) {
        if (tileObject == null || tileObject.getLocalLocation() == null) {
            return;
        }
        if (!Rs2Camera.isTileOnScreen(tileObject)) {
            Rs2Camera.turnTo(tileObject);
            sleepUntil(() -> Rs2Camera.isTileOnScreen(tileObject), 1200);
        }
    }

    private static boolean handleObject(Transport transport, TileObject tileObject) {
        return handleObject(transport, tileObject, transport.getAction());
    }

    private static boolean handleObject(Transport transport, TileObject tileObject, String action) {
        WorldPoint before = Rs2Player.getWorldLocation();
        Rs2GameObject.interact(tileObject, action);
        if (handleObjectExceptions(transport, tileObject)) return true;
        WorldPoint tdObj = transport.getDestination();
        WorldPoint plObj = Rs2Player.getWorldLocation();
        if (tdObj == null || plObj == null) {
            return false;
        }
        if (tdObj.getPlane() == plObj.getPlane()) {
            if (transport.getType() == TransportType.AGILITY_SHORTCUT) {
                Rs2Player.waitForAnimation();
                sleepUntil(() -> {
                    WorldPoint now = Rs2Player.getWorldLocation();
                    return isPlayerWithinChebyshevInclusive(tdObj, 2)
                            || isSettledNearAdjacentSamePlaneLanding(transport, now, tdObj, 0);
                }, 10000);
            } else if (transport.getType() == TransportType.MINECART) {
                if (interactWithAdventureLog(transport)) {
                    sleepTickJitter(2); // wait extra 2 game ticks before moving
                } else {
                    sleepUntil(() -> Rs2Player.getPoseAnimation() == 2148, 5000);
                    sleepUntil(() -> Rs2Player.getPoseAnimation() != 2148, 10000);
                }
            } else if (transport.getType() == TransportType.TELEPORTATION_PORTAL) {
                sleepTickJitter(2); // wait extra 2 game ticks before moving
            } else {
                Rs2Player.waitForWalking();
                Rs2Dialogue.clickOption("Yes please"); //shillo village cart
                if (isAdjacentSamePlaneTransport(transport)) {
                    sleepUntil(() -> {
                        WorldPoint now = Rs2Player.getWorldLocation();
                        return now != null && (now.equals(transport.getDestination())
                                || !now.equals(before)
                                || !Rs2Player.isMoving());
                    }, 2000);
                    WorldPoint afterOpen = Rs2Player.getWorldLocation();
                    if (afterOpen != null && !afterOpen.equals(transport.getDestination())) {
                        boolean clicked = walkMiniMap(transport.getDestination());
                        if (!clicked) {
                            clicked = walkFastCanvas(transport.getDestination());
                        }
                        if (clicked) {
                            sleepUntil(() -> {
                                WorldPoint now = Rs2Player.getWorldLocation();
                                WorldPoint td = transport.getDestination();
                                return now != null && td != null && now.equals(td);
                            }, 3000);
                        }
                    }
                }
            }
            return true;
        } else {
            WorldPoint plZ = Rs2Player.getWorldLocation();
            if (plZ == null) {
                return false;
            }
            int z = plZ.getPlane();
            boolean started = sleepUntil(() -> {
                WorldPoint p = Rs2Player.getWorldLocation();
                return p != null && (p.getPlane() != z || Rs2Player.isMoving() || Rs2Player.isAnimating());
            }, 1800);
            if (!started) {
                log.debug("[Walker] {} transport click on {} produced no movement/animation; retrying",
                        transport.getAction(), tileObject.getId());
                return false;
            }
            WorldPoint plAfterStart = Rs2Player.getWorldLocation();
            boolean planeChanged = plAfterStart != null && plAfterStart.getPlane() != z
                    || sleepUntil(() -> {
                        WorldPoint p = Rs2Player.getWorldLocation();
                        return p != null && p.getPlane() != z;
                    }, 5000);
            if (planeChanged) {
                sleep((int) Rs2Random.gaussRand(300.0, 120.0));
            }
            return planeChanged;
        }
    }

    private static boolean isAdjacentSamePlaneTransport(Transport transport) {
        return transport != null
                && transport.getOrigin() != null
                && transport.getDestination() != null
                && transport.getOrigin().getPlane() == transport.getDestination().getPlane()
                && transport.getOrigin().distanceTo(transport.getDestination()) <= 1;
    }

    private static int[] mapSmoothedToRaw(List<WorldPoint> smoothed, List<WorldPoint> raw) {
        if (smoothed == null || raw == null || smoothed.isEmpty() || raw.isEmpty()) {
            return new int[0];
        }
        int[] mapping = new int[smoothed.size()];
        int rawIdx = 0;
        for (int si = 0; si < smoothed.size(); si++) {
            WorldPoint sp = smoothed.get(si);
            while (rawIdx < raw.size() && !raw.get(rawIdx).equals(sp)) {
                rawIdx++;
            }
            mapping[si] = Math.min(rawIdx, raw.size() - 1);
        }
        return mapping;
    }

    private static int rawEndForSmoothedIndex(int smoothedIdx, int[] smoothedToRaw,
                                               List<WorldPoint> rawPath, List<WorldPoint> path) {
        if (smoothedIdx + 1 < path.size() && smoothedIdx + 1 < smoothedToRaw.length) {
            return smoothedToRaw[smoothedIdx + 1];
        }
        return rawPath.size();
    }

    private static boolean handleDoorsInRawSegment(List<WorldPoint> rawPath, int rawFrom, int rawTo,
                                                    long timeoutMs, Map<String, WorldPoint> attempted,
                                                    Map<WorldPoint, Integer> reachableCache) {
        WorldPoint playerLoc = reachableCache != null ? Rs2Player.getWorldLocation() : null;
        long startedAt = System.currentTimeMillis();
        for (int ri = rawFrom; ri < rawTo && ri < rawPath.size() - 1; ri++) {
            long elapsed = System.currentTimeMillis() - startedAt;
            if (elapsed >= timeoutMs) {
                return false;
            }
            if (reachableCache != null && reachableCache.containsKey(rawPath.get(ri))
                    && reachableCache.containsKey(rawPath.get(ri + 1))
                    && !hasDoorLikeSceneObjectOnSegment(rawPath.get(ri), rawPath.get(ri + 1),
                            playerLoc, HANDLER_RANGE)) {
                continue;
            }
            long remainingTimeoutMs = Math.max(1L, timeoutMs - elapsed);
            if (handleDoorsWithTimeout(rawPath, ri, remainingTimeoutMs, attempted)) {
                return true;
            }
            if (isDoorInteractionSettling()) {
                return false;
            }
        }
        return false;
    }

    private static boolean handleRockfallInRawSegment(List<WorldPoint> rawPath, int rawFrom, int rawTo,
                                                       Map<WorldPoint, Integer> reachableCache) {
        for (int ri = rawFrom; ri < rawTo && ri < rawPath.size() - 1; ri++) {
            if (reachableCache != null && reachableCache.containsKey(rawPath.get(ri))
                    && reachableCache.containsKey(rawPath.get(ri + 1))) {
                continue;
            }
            if (handleRockfall(rawPath, ri)) {
                return true;
            }
        }
        return false;
    }

    private static boolean handleTransportsInRawSegment(List<WorldPoint> rawPath, int rawFrom, int rawTo) {
        for (int ri = rawFrom; ri < rawTo && ri < rawPath.size() - 1; ri++) {
            WorldPoint playerLoc = Rs2Player.getWorldLocation();
            if (!isRawTransportOriginNearPlayer(
                    rawPath, ri, playerLoc, RAW_TRANSPORT_DISPATCH_MAX_DISTANCE)) {
                continue;
            }
            if (handleTransports(rawPath, ri)) {
                return true;
            }
        }
        return false;
    }

    static boolean isRawTransportOriginNearPlayer(List<WorldPoint> rawPath,
                                                   int transportIndex,
                                                   WorldPoint playerLoc,
                                                   int maxDistance) {
        if (rawPath == null || playerLoc == null
                || transportIndex < 0 || transportIndex >= rawPath.size() - 1) {
            return false;
        }
        WorldPoint routeOrigin = rawPath.get(transportIndex);
        return isTransportOriginNearPlayer(routeOrigin, playerLoc, maxDistance);
    }

    private static boolean isTransportOriginNearPlayer(WorldPoint routeOrigin,
                                                       WorldPoint playerLoc,
                                                       int maxDistance) {
        return routeOrigin != null
                && playerLoc != null
                && routeOrigin.getPlane() == playerLoc.getPlane()
                && routeOrigin.distanceTo2D(playerLoc) <= Math.max(0, maxDistance);
    }

    private static boolean finishHandledTransport(Transport transport) {
        long handoffStartedAt = System.currentTimeMillis();
        lastTransportHandledAtMs = handoffStartedAt;
        lastTransportHandledAtLocation = Rs2Player.getWorldLocation();
        lastTransportOriginLocation = transport != null ? transport.getOrigin() : null;
        lastTransportDestinationLocation = transport != null ? transport.getDestination() : null;
        WorldPoint goal = currentTarget;
        WorldPoint transportDest = transport != null ? transport.getDestination() : null;
        boolean expectedTransport = consumeExpectedTransportDestination(transportDest);
        boolean hasPrecomputedContinuation = hasPrecomputedContinuationFromTransport(transport);
        if (goal != null) {
            WebWalkLog.tmark("transport_handoff_enter",
                    0L,
                    goal,
                    Rs2Player.getWorldLocation(),
                    "dest=" + compactWorldPoint(transportDest)
                            + " expected=" + expectedTransport
                            + " precomputed=" + hasPrecomputedContinuation
                            + " type=" + (transport != null ? transport.getType() : "null"));
        }
        if ((expectedTransport || hasPrecomputedContinuation) && goal != null) {
            WebWalkLog.tmark(expectedTransport ? "transport_handoff_expected_hit" : "transport_handoff_precomputed_hit",
                    System.currentTimeMillis() - handoffStartedAt,
                    goal,
                    Rs2Player.getWorldLocation(),
                    "dest=" + compactWorldPoint(transportDest));
            return true;
        }
        if (goal != null && transportDest != null) {
            // Destination-aware handoff: prepare next path from known landing tile.
            boolean queued = restartPathfinding(transportDest, goal);
            WebWalkLog.tmark("transport_handoff_restart",
                    System.currentTimeMillis() - handoffStartedAt,
                    goal,
                    Rs2Player.getWorldLocation(),
                    "queued=" + queued + " dest=" + compactWorldPoint(transportDest));
            if (!queued && shouldRecalculatePathAfterTransport(transport)) {
                recalculatePath();
                WebWalkLog.tmark("transport_handoff_recalc_fallback",
                        System.currentTimeMillis() - handoffStartedAt,
                        goal,
                        Rs2Player.getWorldLocation(),
                        "dest=" + compactWorldPoint(transportDest));
            }
        } else if (goal != null && shouldRecalculatePathAfterTransport(transport)) {
            recalculatePath();
            WebWalkLog.tmark("transport_handoff_recalc_goal_only",
                    System.currentTimeMillis() - handoffStartedAt,
                    goal,
                    Rs2Player.getWorldLocation(),
                    "dest=" + compactWorldPoint(transportDest));
        }
        return true;
    }

    private static void primeExpectedTransportDestinations(List<WorldPoint> path, int startIdx) {
        if (path == null || path.size() < 2) {
            synchronized (expectedTransportDestinations) {
                expectedTransportDestinations.clear();
            }
            return;
        }
        int start = Math.max(0, startIdx);
        java.util.Deque<WorldPoint> next = new ArrayDeque<>();
        WorldPoint lastAdded = null;
        for (int i = start; i < path.size() - 1; i++) {
            if (!isCatalogBackedTransportSegment(path, i)) {
                continue;
            }
            WorldPoint destination = path.get(i + 1);
            if (destination == null) {
                continue;
            }
            if (lastAdded == null || !lastAdded.equals(destination)) {
                next.addLast(destination);
                lastAdded = destination;
            }
        }
        synchronized (expectedTransportDestinations) {
            expectedTransportDestinations.clear();
            expectedTransportDestinations.addAll(next);
        }
    }

    private static boolean consumeExpectedTransportDestination(WorldPoint destination) {
        if (destination == null) {
            return false;
        }
        synchronized (expectedTransportDestinations) {
            while (!expectedTransportDestinations.isEmpty()) {
                WorldPoint expected = expectedTransportDestinations.peekFirst();
                if (expected == null) {
                    expectedTransportDestinations.pollFirst();
                    continue;
                }
                if (sameOrNearTransportDestination(expected, destination)) {
                    expectedTransportDestinations.pollFirst();
                    return true;
                }
                break;
            }
            return false;
        }
    }

    private static boolean sameOrNearTransportDestination(WorldPoint a, WorldPoint b) {
        return a != null
                && b != null
                && a.getPlane() == b.getPlane()
                && a.distanceTo2D(b) <= TRANSPORT_DEST_MATCH_CHEBYSHEV;
    }

    private static boolean hasPrecomputedContinuationFromTransport(Transport transport) {
        if (transport == null || transport.getDestination() == null) {
            return false;
        }
        Pathfinder pathfinder = Rs2PathApi.getPathfinder();
        if (pathfinder == null || !pathfinder.isDone()) {
            return false;
        }
        List<WorldPoint> walkPath = pathfinder.getWalkablePath();
        if (walkPath == null || walkPath.size() < 2) {
            return false;
        }
        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        int closest = getClosestTileIndex(walkPath, playerLoc);
        if (closest < 0) {
            return false;
        }
        WorldPoint destination = transport.getDestination();
        for (int i = Math.max(0, closest - 2); i < walkPath.size(); i++) {
            WorldPoint point = walkPath.get(i);
            if (sameOrNearTransportDestination(point, destination)) {
                return i < walkPath.size() - 1;
            }
        }
        return false;
    }

    static boolean shouldRecalculatePathAfterTransport(Transport transport) {
        if (transport == null || transport.getDestination() == null) {
            return false;
        }
        if (TransportType.isTeleport(transport.getType())) {
            return true;
        }
        if (transport.getOrigin() == null) {
            return false;
        }
        return transport.getOrigin().getPlane() != transport.getDestination().getPlane()
                || transport.getOrigin().distanceTo2D(transport.getDestination()) > OFFSET;
    }

    private static void markAdjacentSamePlaneTransportHandled(Transport transport, TileObject tileObject) {
        for (WorldPoint point : adjacentSamePlaneTransportSuppressionPoints(transport, tileObject)) {
            markStationaryDoorOpened(point);
        }
    }

    static Set<WorldPoint> adjacentSamePlaneTransportSuppressionPoints(Transport transport, TileObject tileObject) {
        if (!isAdjacentSamePlaneTransport(transport)) {
            return Collections.emptySet();
        }

        Set<WorldPoint> points = new LinkedHashSet<>();
        points.add(transport.getOrigin());
        points.add(transport.getDestination());
        if (tileObject != null && tileObject.getWorldLocation() != null) {
            points.add(tileObject.getWorldLocation());
        }
        return points;
    }

    private static int transportHandlingPreference(Transport transport) {
        if (isAlKharidTollGateTransport(transport) && transport.getCurrencyAmount() > 0) {
            return 1;
        }
        return 0;
    }

    private static boolean isAlKharidTollGateTransport(Transport transport) {
        return transport != null
                && isAlKharidTollGateObjectId(transport.getObjectId())
                && AL_KHARID_TOLL_GATE_POINTS.contains(transport.getOrigin())
                && AL_KHARID_TOLL_GATE_POINTS.contains(transport.getDestination());
    }

    private static boolean isAlKharidTollGateObjectId(int objectId) {
        return AL_KHARID_TOLL_GATE_OBJECT_IDS.contains(objectId);
    }

    private static boolean isPayTollAction(String action) {
        return action != null && action.toLowerCase(Locale.ROOT).startsWith("pay-toll");
    }

    private static boolean handleAlKharidTollGate(Transport transport) {
        if (Rs2Player.isMoving()) {
            Rs2Player.waitForWalking();
        }

        boolean confirmed = false;
        if (sleepUntil(Rs2Dialogue::hasSelectAnOption, 2500)) {
            confirmed = Rs2Dialogue.clickOption("Yes, okay", "Yes");
        }

        boolean reachedDestination = sleepUntil(() -> {
            WorldPoint now = Rs2Player.getWorldLocation();
            WorldPoint destination = transport.getDestination();
            return now != null
                    && destination != null
                    && now.getPlane() == destination.getPlane()
                    && now.distanceTo2D(destination) <= 1;
        }, POST_HANDLE_OBJECT_LANDING_WAIT_MS);
        if (!confirmed && !reachedDestination) {
            WebWalkLog.spWarn(
                    "Al Kharid toll gate confirmation unresolved dest={} at={}",
                    compactWorldPoint(transport.getDestination()),
                    compactWorldPoint(Rs2Player.getWorldLocation()));
        }
        return true;
    }

    private static boolean handleObjectExceptions(Transport transport, TileObject tileObject) {
        for (Map.Entry<Integer, Integer> entry : OPEN_TO_CLOSED_MAPPINGS.entrySet()) {
            final int closedTrapdoorId = entry.getKey();
            final int openTrapdoorId = entry.getValue();

            if (transport.getObjectId() == openTrapdoorId) {
                if (tileObject.getId() == closedTrapdoorId) {
                    Rs2GameObject.interact(tileObject, "Open");
                    sleepUntil(() -> Rs2GameObject.exists(openTrapdoorId));
                    TileObject openTrapdoor = Rs2GameObject.getAll(o -> o.getId() == openTrapdoorId, tileObject.getWorldLocation(), 10).stream().findFirst().orElse(null);
                    if (openTrapdoor != null) {
                        Rs2GameObject.interact(openTrapdoor, transport.getAction());
                    }
                } else if (tileObject.getId() == openTrapdoorId) {
                    Rs2GameObject.interact(tileObject, transport.getAction());
                }
                sleepUntil(() -> !Rs2Player.isAnimating());
                boolean trapdoorLanded = sleepUntilTrue(
                        () -> isPlayerWithinChebyshevOf(transport.getDestination(), OFFSET),
                        TRANSPORT_LANDING_WAIT_POLL_MS, TRANSPORT_LANDING_WAIT_TIMEOUT_MS);
                if (!trapdoorLanded) {
                    WebWalkLog.spWarn(
                            "trapdoor post-travel wait timed out ({}ms) dest={} at={}",
                            TRANSPORT_LANDING_WAIT_TIMEOUT_MS,
                            compactWorldPoint(transport.getDestination()),
                            compactWorldPoint(Rs2Player.getWorldLocation()));
                }
                return true;
            }
        }

        if (isAlKharidTollGateTransport(transport) && isPayTollAction(transport.getAction())) {
            return handleAlKharidTollGate(transport);
        }

        //Al kharid broken wall will animate once and then stop and then animate again
        if (tileObject.getId() == ObjectID.KHARID_POSHWALL_TOPLESS || tileObject.getId() == ObjectID.KHARID_BIGWINDOW) {
            Rs2Player.waitForAnimation();
            Rs2Player.waitForAnimation();
            return true;
        }
        // Handle Leaves Traps in Isafdar Forest
        if (tileObject.getId() == ObjectID.REGICIDE_PITFALL_SIDE) {
            Rs2Player.waitForAnimation(1200);
            if (Rs2Player.getWorldLocation().getY() > 6400) {
                Rs2GameObject.interact(ObjectID.REGICIDE_TRAP_HAND_HOLDS);
                sleepUntil(() -> Rs2Player.getWorldLocation().getY() < 6400);
            } else {
                sleepUntil(() -> !Rs2Player.isMoving() && !Rs2Player.isAnimating());
            }
            return true;
        }
        // Handle Ferox Encalve Barrier
        if (tileObject.getId() == ObjectID.WILDY_HUB_ENTRY_BARRIER || tileObject.getId() == ObjectID.WILDY_HUB_ENTRY_BARRIER_M) {
            if (Rs2Dialogue.isInDialogue()) {
                if (Rs2Dialogue.getDialogueText().toLowerCase().contains("when returning to the enclave")) {
                    Rs2Dialogue.clickContinue();
                    Rs2Dialogue.sleepUntilSelectAnOption();
                    Rs2Dialogue.keyPressForDialogueOption("Yes, and don't ask again.");
                    Rs2Dialogue.sleepUntilNotInDialogue();
                    return true;
                }
            }
        }
        // Handle Cobwebs blocking path
        if (tileObject.getId() == ObjectID.BIGWEB_SLASHABLE && !Rs2Equipment.isWearing(ItemID.ARANEA_BOOTS)) {
            sleepUntil(() -> !Rs2Player.isMoving() && !Rs2Player.isAnimating(1200));
            final WorldPoint webLocation = tileObject.getWorldLocation();
            final WorldPoint currentPlayerPoint = Rs2Player.getWorldLocation();
            boolean doesWebStillExist = Rs2GameObject.getAll(o -> Objects.equals(webLocation, o.getWorldLocation()) && o.getId() == ObjectID.BIGWEB_SLASHABLE).stream().findFirst().isPresent();
            if (doesWebStillExist) {
                sleepUntil(() -> Rs2GameObject.getAll(o -> Objects.equals(webLocation, o.getWorldLocation()) && o.getId() == ObjectID.BIGWEB_SLASHABLE).stream().findFirst().isEmpty(),
                        () -> {
                            Rs2GameObject.interact(tileObject, "slash");
                            Rs2Player.waitForAnimation();
                        }, 8000, 1200);
            }
            Rs2Walker.walkFastCanvas(transport.getDestination());
            return sleepUntil(() -> !Objects.equals(currentPlayerPoint, Rs2Player.getWorldLocation()));
        }

        // Handle Brimhaven Dungeon Entrance
        if (tileObject.getId() == 20877) {
            if (Rs2Player.isMoving()) {
                Rs2Player.waitForWalking();
            }
            Rs2Dialogue.sleepUntilHasQuestion("Pay 875 coins to enter?");
            Rs2Dialogue.clickOption("Yes");
            sleepUntil(() -> {
                WorldPoint now = Rs2Player.getWorldLocation();
                WorldPoint td = transport.getDestination();
                return now != null && td != null && now.equals(td);
            });
            return true;
        }
        // Handle Brimhaven Dungeon Stepping Stones
        if (tileObject.getId() == ObjectID.KARAM_DUNGEON_STONE1 || tileObject.getId() == ObjectID.KARAM_DUNGEON_STONE2) {
            Rs2Player.waitForAnimation(600 * 7);
            return true;
        }

        // Handle Morte Myre Cave Agility Shortcut
        if (tileObject.getId() == ObjectID.FAIRY2_ROUTE_CAVEWALLTUNNEL) {
            Rs2Player.waitForAnimation((600 * 4 ) + 300);
            return true;
        }

        // Handle Crash Site Cavern Gate
        if (tileObject.getId() == 28807 && transport.getOrigin().equals(new WorldPoint(2435,3519, 0))) {
            if (Rs2Player.isMoving()) {
                Rs2Player.waitForWalking();
            }
            Rs2Dialogue.sleepUntilInDialogue();
            Rs2Dialogue.clickOption("yes");
            return true;
        }

        // Handle Cave Entrance inside of Asgarnia Ice Caves
        if (tileObject.getId() == ObjectID.CAVEWALL_SHORTCUT_ROYAL_TITANS_EAST || tileObject.getId() == ObjectID.CAVEWALL_SHORTCUT_ROYAL_TITANS_WEST) {
            Rs2Player.waitForAnimation();
        }

        // Handle Rev Cave Dialogue
        if (tileObject.getId() == ObjectID.WILD_CAVE_ENTRANCE_LOW) {
            if (Rs2Player.isMoving()) {
                Rs2Player.waitForWalking();
            }
            Widget dialogueSprite = Rs2Dialogue.getDialogueSprite();
            if (dialogueSprite != null && dialogueSprite.getItemId() == 1004) {
                Rs2Dialogue.clickContinue();
                Rs2Dialogue.sleepUntilSelectAnOption();
                Rs2Dialogue.clickOption("Yes, don't ask again");
                Rs2Dialogue.sleepUntilNotInDialogue();
            }
            return true;
        }

        if (tileObject.getId() == ObjectID.HEROROCKSLIDE) {
            Rs2Player.waitForAnimation(600 * 4);
            return true;
        }

        if (Rs2GameObject.getObjectIdsByName("Fossil_Rowboat").contains(tileObject.getId())) {
            if (transport.getDisplayInfo() == null || transport.getDisplayInfo().isEmpty()) return false;

            char option = transport.getDisplayInfo().charAt(0);
            Rs2Dialogue.sleepUntilSelectAnOption();
            Rs2Keyboard.keyPress(option);
            sleepUntil(() -> {
                WorldPoint pl = Rs2Player.getWorldLocation();
                WorldPoint td = transport.getDestination();
                return pl != null && td != null && pl.getPlane() == td.getPlane()
                        && pl.distanceTo2D(td) < OFFSET;
            }, 10000);
            return true;
        }

        // Handle door/gate near wilderness agility course
        if (tileObject.getId() == ObjectID.BALANCEGATE52A || tileObject.getId() == ObjectID.BALANCEGATE52B_RIGHT || tileObject.getId() == ObjectID.BALANCEGATE52B_LEFT) {
            Rs2Player.waitForAnimation(600 * 4);
            return true;
        }

        if (tileObject.getId() == ObjectID.AERIAL_FISHING_BOAT) {
            Rs2Dialogue.sleepUntilSelectAnOption();
            Rs2Dialogue.clickOption(transport.getDisplayInfo(), true);
            sleepUntil(() -> {
                WorldPoint pl = Rs2Player.getWorldLocation();
                WorldPoint td = transport.getDestination();
                return pl != null && td != null && pl.getPlane() == td.getPlane()
                        && pl.distanceTo2D(td) < OFFSET;
            }, 10000);
            return true;
        }

        // Handle Magic Mushtree (Fossil Island Mycelium Transportation System)
        if (MagicMushtree.isMagicMushtree(tileObject)) {
            return MagicMushtree.handleTransport(transport);
        }
        return false;
    }

    private static boolean handleWildernessObelisk(Transport transport) {
        GameObject obelisk = Rs2GameObject.getGameObject(obj -> obj.getId() == transport.getObjectId(), transport.getOrigin());

        if (obelisk != null) {
            Rs2GameObject.interact(obelisk, transport.getAction());
            sleepUntil(() -> Rs2GameObject.getGameObject(obj -> obj.getId() == transport.getObjectId(), transport.getOrigin()) != null);
            walkFastCanvas(transport.getOrigin());
            return sleepUntilTrue(() -> {
                WorldPoint pl = Rs2Player.getWorldLocation();
                WorldPoint td = transport.getDestination();
                return pl != null && td != null && pl.getPlane() == td.getPlane()
                        && pl.distanceTo2D(td) < OFFSET;
            }, 100, 10000);
        }
        return false;
    }

    private static boolean handleTeleportSpell(Transport transport) {
        if (Rs2Pvp.isInWilderness() && (Rs2Pvp.getWildernessLevelFrom(Rs2Player.getWorldLocation()) > (transport.getMaxWildernessLevel() + 1))) return false;
        boolean hasMultipleDestination = transport.getDisplayInfo().contains(":");

        String spellName = hasMultipleDestination
                ? transport.getDisplayInfo().split(":")[0].trim().toLowerCase()
                : transport.getDisplayInfo().toLowerCase();

        String option = hasMultipleDestination
                ? transport.getDisplayInfo().split(":")[1].trim().toLowerCase()
                : "cast";

        int identifier = hasMultipleDestination
                ? 2
                : 1;

        MagicAction magicSpell = Arrays.stream(MagicAction.values()).filter(x -> x.getName().toLowerCase().contains(spellName)).findFirst().orElse(null);
        if (magicSpell != null) {
            if (magicSpell == MagicAction.LUMBRIDGE_HOME_TELEPORT) {
                return Rs2Magic.quickCast(magicSpell);
            }
            return Rs2Magic.cast(magicSpell, option, identifier);
        }
        return false;
    }

    private static boolean isLumbridgeHomeTeleport(Transport transport) {
        return transport.getDisplayInfo() != null
                && transport.getDisplayInfo().toLowerCase().startsWith("lumbridge home teleport");
    }

    private static boolean handleTeleportItem(Transport transport) {
        WorldPoint plWild = Rs2Player.getWorldLocation();
        if (Rs2Pvp.isInWilderness() && plWild != null
                && Rs2Pvp.getWildernessLevelFrom(plWild) > (transport.getMaxWildernessLevel() + 1)) {
            return false;
        }
        boolean succesfullAction = false;
        for (Set<Integer> itemIds : transport.getItemIdRequirements()) {
            if (succesfullAction)
                break;
            for (Integer itemId : itemIds) {
                if (Rs2Walker.currentTarget == null) break;
                // reachedDistance <= 0: do not treat as "already at destination" (legacy: raw distance < 0 never true).
                int reachRd = reachedDistanceOrDefault();
                if (reachRd > 0 && isPlayerWithinChebyshevOf(transport.getDestination(), reachRd)) {
                    break;
                }
                if (succesfullAction) break;

                //If an action is succesfully we break out of the loop
                succesfullAction = handleWearableTeleports(transport, itemId) || handleInventoryTeleports(transport, itemId);
            }
        }
        return succesfullAction;
    }

    private static boolean handleInventoryTeleports(Transport transport, int itemId) {
        Rs2ItemModel rs2Item = Rs2Inventory.get(itemId);
        if (rs2Item == null) return false;

        // A list of generic teleports that can be used if no parsable destination action is found
        List<String> genericKeyWords = Arrays.asList(
                "invoke", "empty", "consume", "open", "teleport", "rub", "break", "reminisce", "signal", "play", "commune", "squash", "blow"
        );

        // Return true when the item does not use a generic keyword to teleport to its destination
        boolean hasParsableDestination = transport.getDisplayInfo().contains(":");
        String destination = hasParsableDestination
                ? transport.getDisplayInfo().split(":")[1].trim().toLowerCase()
                : transport.getDisplayInfo().trim().toLowerCase();

        boolean wildernessTransport = PathfinderConfig.isInWilderness(WorldPointUtil.packWorldPoint(transport.getDestination()));

        log.debug("Trying to find action for destination={}", destination);
        // Check if item has destination as direct action
        String itemAction = rs2Item.getAction(destination);

        // Check if item has destination as sub-menu action
        Map.Entry<String,Integer> sub = rs2Item.getIndexOfSubAction(destination);
        if (itemAction == null && sub != null && sub.getKey() != null) {
            itemAction = destination;
        }

        // If there's only one destination with the item possible, a generic action will also work
        if (itemAction == null && !hasParsableDestination) {
            itemAction = rs2Item.getActionFromList(genericKeyWords);
        }

        if (itemAction != null) {
            boolean interaction = Rs2Inventory.interact(rs2Item, itemAction);
            if (!interaction) {
                return false;
            } else if (wildernessTransport) {
                Rs2Dialogue.sleepUntilInDialogue();
                return Rs2Dialogue.clickOption("Yes", "Okay");
            } else if (isQuetzalWhistleItemId(itemId)) {
                return finishQuetzalWhistleTransport(transport);
            }
            return true;
        }

        // If no location-based action found, try generic actions
        itemAction = rs2Item.getActionFromList(genericKeyWords);

        if (itemAction == null) {
            log.debug("No generic keyword found for={}, genericKeywords={}", itemAction, String.join(",", genericKeyWords));
            return false;
        }

        if (Rs2Inventory.interact(itemId, itemAction)) {
            log.debug("Traveling with genericAction={}, to {} - ({})", itemAction, transport.getDisplayInfo(), transport.getDestination());

            if (itemAction.equalsIgnoreCase("open") && itemId == ItemID.BOOKOFSCROLLS_CHARGED) {
                return handleMasterScrollBook(destination);
            } else if (isQuetzalWhistleItemId(itemId)) {
                return finishQuetzalWhistleTransport(transport);
            } else if (isDialogueBasedTeleportItem(transport.getDisplayInfo())) {
                // Multi-destination teleport items: wait for destination selection dialogue
                Rs2Dialogue.sleepUntilSelectAnOption();
                Rs2Dialogue.clickOption(destination);
                log.info("Traveling to {} - ({})", transport.getDisplayInfo(), transport.getDestination());
                return true;
            } else if (transport.getDisplayInfo().toLowerCase().contains("burning amulet")) {
                // Burning amulet in inventory: confirm wilderness teleport
                Rs2Dialogue.sleepUntilInDialogue();
                Rs2Dialogue.clickOption("Okay, teleport to level");
                log.info("Traveling to {} - ({})", transport.getDisplayInfo(), transport.getDestination());
                return true;
            } else if (wildernessTransport) {
                Rs2Dialogue.sleepUntilInDialogue();
                return Rs2Dialogue.clickOption("Yes", "Okay");
            } else {
                Rs2Player.waitForAnimation();
                log.info("Unsure how to handle this itemTransport={} action={}", transport, itemAction);
            }
        }
        return false;
    }

    private static boolean handleWearableTeleports(Transport transport, int itemId) {
        Rs2ItemModel rs2Item = Rs2Equipment.get(itemId);
        if (rs2Item == null) return false;
        if (transport.getDisplayInfo().contains(":")) {
            String[] values = transport.getDisplayInfo().split(":");
            String destination = values[1].trim().toLowerCase();

            if (transport.getDisplayInfo().toLowerCase().contains("slayer ring")) {
                Rs2Equipment.invokeMenu(rs2Item, "teleport");
                Rs2Dialogue.sleepUntilSelectAnOption();
                Rs2Dialogue.clickOption(destination);
            } else {
                Rs2Equipment.invokeMenu(rs2Item, destination);
                if (transport.getDisplayInfo().toLowerCase().contains("burning amulet")) {
                    Rs2Dialogue.sleepUntilInDialogue();
                    Rs2Dialogue.clickOption("Okay, teleport to level");
                }
            }
            log.info("Traveling to {} - ({})", transport.getDisplayInfo(), transport.getDestination());
            return true;
        }
        return false;
    }

    /**
     * Checks if the teleport item requires dialogue-based destination selection.
     * These are items that, when rubbed/activated, show a dialogue menu to choose destination.
     *
     * @param displayInfo the displayInfo from the transport
     * @return true if the item requires dialogue handling
     */
    private static boolean isDialogueBasedTeleportItem(String displayInfo) {
        if (displayInfo == null) return false;
        String lowerDisplayInfo = displayInfo.toLowerCase();
        return lowerDisplayInfo.contains("slayer ring")
                || lowerDisplayInfo.contains("games necklace")
                || lowerDisplayInfo.contains("skills necklace")
                || lowerDisplayInfo.contains("ring of dueling")
                || lowerDisplayInfo.contains("ring of wealth")
                || lowerDisplayInfo.contains("amulet of glory")
                || lowerDisplayInfo.contains("combat bracelet")
                || lowerDisplayInfo.contains("digsite pendant")
                || lowerDisplayInfo.contains("necklace of passage")
                || lowerDisplayInfo.contains("giantsoul amulet");
    }

    /**
     * Checks if the player's current location is within the specified area defined by the given world points.
     *
     * @param worldPoints an array of two world points of the NW and SE corners of the area
     * @return true if the player's current location is within the specified area, false otherwise
     */
    public static boolean isInArea(WorldPoint... worldPoints) {
        if (worldPoints == null || worldPoints.length < 2 || worldPoints[0] == null || worldPoints[1] == null) {
            throw new IllegalArgumentException("isInArea requires two WorldPoints.");
        }
        WorldPoint a = worldPoints[0];
        WorldPoint b = worldPoints[1];
        final int aX = a.getX(), aY = a.getY();
        final int bX = b.getX(), bY = b.getY();

        final int minX = Math.min(aX, bX);
        final int maxX = Math.max(aX, bX);
        final int minY = Math.min(aY, bY);
        final int maxY = Math.max(aY, bY);

        final WorldPoint playerLocation = Rs2Player.getWorldLocation();
        final int playerX = playerLocation.getX();
        final int playerY = playerLocation.getY();

        // draws box from 2 points to check against all variations of player X,Y from said points.
        return (playerX >= minX && playerX <= maxX && playerY >= minY && playerY <= maxY);
    }

    /**
     * Checks if the player's current location is within the specified range from the given center point.
     *
     * @param centerOfArea a WorldPoint which is the center of the desired area,
     * @param range        an int of range to which the boundaries will be drawn in a square,
     * @return true if the player's current location is within the specified area, false otherwise
     */
    public static boolean isInArea(WorldPoint centerOfArea, int range) {
        WorldPoint seCorner = new WorldPoint(centerOfArea.getX() + range, centerOfArea.getY() - range, centerOfArea.getPlane());
        WorldPoint nwCorner = new WorldPoint(centerOfArea.getX() - range, centerOfArea.getY() + range, centerOfArea.getPlane());
        return isInArea(seCorner, nwCorner); // call to our sibling
    }

    public static boolean isNear() {
        final Pathfinder pathfinder = Rs2PathApi.getPathfinder();
        if (pathfinder == null) return false; // idk are we near if we don't have a path?
        final List<WorldPoint> path = pathfinder.getPath();
        if (path == null) return false;

        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null) {
            return false;
        }
        int index = IntStream.range(0, path.size())
                .filter(f -> {
                    WorldPoint wp = path.get(f);
                    return wp.getPlane() == playerLocation.getPlane()
                            && wp.distanceTo2D(playerLocation) < 3;
                })
                .findFirst().orElse(-1);
        return index >= Math.max(path.size() - 10, 0);
    }

    /**
     * @param target
     * @return
     */
    public static boolean isNear(WorldPoint target) {
        WorldPoint pl = Rs2Player.getWorldLocation();
        return pl != null && pl.equals(target);
    }

    public static boolean isNearPath() {
        final Pathfinder pathfinder = Rs2PathApi.getPathfinder();
        if (pathfinder == null) return true;

        final List<WorldPoint> path = pathfinder.getWalkablePath();
        if (path == null || path.isEmpty()) return true;

        final WorldPoint loc = Rs2Player.getWorldLocation();
        if (loc == null) return true;

        if (config.recalculateDistance() < 0 || lastPosition.equals(lastPosition = loc)) {
            return true;
        }

        if (config.usePoh() && PohTeleports.isInHouse()) {
            //Would be nice to have access to current node here and check if the current Node is a POH transport node.
            return true;
        }

        var reachableTiles = Rs2Tile.getReachableTilesFromTile(Rs2Player.getWorldLocation(), config.recalculateDistance() - 1);
        for (WorldPoint point : path) {
            if (reachableTiles.containsKey(point)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isNearPathByVariance(List<WorldPoint> path, WorldPoint playerLoc) {
        if (path == null || path.isEmpty() || playerLoc == null) {
            return false;
        }
        int closestIdx = getClosestTileIndex(path, playerLoc);
        if (closestIdx < 0 || closestIdx >= path.size()) {
            return false;
        }
        WorldPoint closest = path.get(closestIdx);
        return closest != null
                && closest.getPlane() == playerLoc.getPlane()
                && closest.distanceTo2D(playerLoc) <= PATH_VARIANCE_TOLERANCE_CHEBYSHEV;
    }

    static String offPathRecalcDeferralReason(boolean playerMoving,
                                              boolean playerAnimating,
                                              boolean playerInteracting,
                                              boolean doorSettling,
                                              boolean transportSettling,
                                              boolean interimActive,
                                              long nowMs,
                                              long lastMovedAtMs,
                                              long routeProgressAtMs,
                                              long minimapClickAtMs,
                                              long interimProgressAtMs) {
        if (doorSettling) {
            return "door-settling";
        }
        if (transportSettling) {
            return "transport-settling";
        }
        if (playerMoving) {
            return "moving";
        }
        if (playerAnimating) {
            return "animating";
        }
        if (playerInteracting) {
            return "interacting";
        }
        if (isRecentEvent(nowMs, routeProgressAtMs, OFF_PATH_RECALC_ROUTE_PROGRESS_GRACE_MS)) {
            return "route-progress";
        }
        if (isRecentEvent(nowMs, minimapClickAtMs, OFF_PATH_RECALC_MINIMAP_CLICK_GRACE_MS)) {
            return "recent-click";
        }
        if (interimActive && isRecentEvent(nowMs, interimProgressAtMs, INTERIM_PROGRESS_TIMEOUT_MS)) {
            return "interim-progress";
        }
        if (isRecentEvent(nowMs, lastMovedAtMs, OFF_PATH_RECALC_RECENT_MOVEMENT_MS)) {
            return "recent-movement";
        }
        return null;
    }

    private static String currentOffPathRecalcDeferralReason(long minimapClickAtMs) {
        return offPathRecalcDeferralReason(
                Rs2Player.isMoving(),
                Rs2Player.isAnimating(),
                Rs2Player.isInteracting(),
                isDoorInteractionSettling(),
                isTransportInteractionSettling(),
                interimTargetWp != null,
                System.currentTimeMillis(),
                lastMovedTimeMs,
                routeProgressAdvancedAtMs,
                minimapClickAtMs,
                interimLastProgressAtMs);
    }

    static int offPathRecalcDeferredWaitMs(String reason,
                                           long nowMs,
                                           long lastMovedAtMs,
                                           long routeProgressAtMs,
                                           long minimapClickAtMs,
                                           long interimProgressAtMs) {
        long remainingMs = OFF_PATH_RECALC_DEFER_WAIT_MAX_MS;
        if ("route-progress".equals(reason)) {
            remainingMs = remainingRecentEventMs(nowMs, routeProgressAtMs, OFF_PATH_RECALC_ROUTE_PROGRESS_GRACE_MS);
        } else if ("recent-click".equals(reason)) {
            remainingMs = remainingRecentEventMs(nowMs, minimapClickAtMs, OFF_PATH_RECALC_MINIMAP_CLICK_GRACE_MS);
        } else if ("interim-progress".equals(reason)) {
            remainingMs = remainingRecentEventMs(nowMs, interimProgressAtMs, INTERIM_PROGRESS_TIMEOUT_MS);
        } else if ("recent-movement".equals(reason)) {
            remainingMs = remainingRecentEventMs(nowMs, lastMovedAtMs, OFF_PATH_RECALC_RECENT_MOVEMENT_MS);
        }
        return (int) Math.max(OFF_PATH_RECALC_DEFER_WAIT_MIN_MS,
                Math.min(OFF_PATH_RECALC_DEFER_WAIT_MAX_MS, remainingMs));
    }

    private static boolean isOffPathRecalcDeferredExit(String exitReason) {
        return exitReason != null && exitReason.startsWith("off-path-deferred:");
    }

    private static String offPathDeferredReasonFromExit(String exitReason) {
        if (!isOffPathRecalcDeferredExit(exitReason)) {
            return "";
        }
        return exitReason.substring("off-path-deferred:".length());
    }

    private static boolean isRecentEvent(long nowMs, long eventAtMs, long graceMs) {
        return eventAtMs > 0L && nowMs >= eventAtMs && nowMs - eventAtMs < graceMs;
    }

    private static long remainingRecentEventMs(long nowMs, long eventAtMs, long graceMs) {
        if (!isRecentEvent(nowMs, eventAtMs, graceMs)) {
            return OFF_PATH_RECALC_DEFER_WAIT_MIN_MS;
        }
        return graceMs - (nowMs - eventAtMs);
    }

    private static boolean hasUpcomingNearbyTransportStep(List<WorldPoint> path,
                                                          int startIdx,
                                                          WorldPoint playerLoc,
                                                          int lookaheadEdges,
                                                          int maxDist) {
        if (path == null || path.size() < 2 || startIdx < 0 || playerLoc == null) {
            return false;
        }
        int from = Math.max(0, startIdx);
        int to = Math.min(path.size() - 2, from + Math.max(0, lookaheadEdges));
        for (int i = from; i <= to; i++) {
            if (!isCatalogBackedTransportSegment(path, i)) {
                continue;
            }
            WorldPoint segFrom = path.get(i);
            WorldPoint segTo = path.get(i + 1);
            if (segFrom == null || segTo == null || segFrom.getPlane() != playerLoc.getPlane()) {
                continue;
            }
            int d = Math.min(segFrom.distanceTo2D(playerLoc), segTo.distanceTo2D(playerLoc));
            if (d <= Math.max(1, maxDist)) {
                return true;
            }
        }
        return false;
    }

    private static void checkIfStuck() {
        // Leagues pending teleports, dialogue, and fairy ring widget should not burn stall budget.
        if (Rs2WalkerStallPolicy.shouldSkipStallAccounting(LEAGUES_AREA_PENDING_STALL_MAX_AGE_MS)) {
            lastMovedTimeMs = System.currentTimeMillis();
            stuckCount = 0;
            prevAnimatingForStuckCheck = Rs2Player.isAnimating();
            return;
        }

        WorldPoint now = Rs2Player.getWorldLocation();
        boolean anim = Rs2Player.isAnimating();
        if (now != null && now.equals(lastPosition)) {
            boolean nearPath = isNearPath();
            boolean poseWalkingNearPath = Rs2Player.isMoving() && nearPath;
            boolean animProgressNearPath = anim && !prevAnimatingForStuckCheck && nearPath;
            if (animProgressNearPath || poseWalkingNearPath) {
                lastMovedTimeMs = System.currentTimeMillis();
                stuckCount = 0;
            } else {
                stuckCount++;
            }
        } else {
            stuckCount = 0;
            lastMovedTimeMs = System.currentTimeMillis();
        }
        prevAnimatingForStuckCheck = anim;
    }

    // Base stall threshold. See stallThresholdMs() for activity-aware scaling.
    // RuneLite exposes no real-time ping, so we skip pure latency scaling and rely on
    // observable activity states that also correlate with legitimately-stuck players.
    private static final long STALL_BASE_MS = 12_000;
    private static final double STALL_COMBAT_MULTIPLIER = 2.0;
    private static final double STALL_ANIMATING_MULTIPLIER = 1.5;
    private static final double STALL_MOVING_MULTIPLIER = 1.35;
    /** While a sticky minimap interim waypoint is active, path segments can exceed base stall easily. */
    private static final double STALL_INTERIM_MINIMAP_MULTIPLIER = 1.75;
    private static final double STALL_INTERACTING_MULTIPLIER = 1.5;
    /**
     * After a successful minimap walk click, refresh the stall clock this long — blocked tiles / long
     * segments sometimes delay tile deltas without {@link Rs2Player#isMoving()} flipping immediately.
     */
    private static final long MINIMAP_CLICK_STALL_GRACE_MS = 12_000L;

    private static boolean interactingActorNearWalkablePath() {
        Pathfinder pf = Rs2PathApi.getPathfinder();
        if (pf == null) {
            return false;
        }
        List<WorldPoint> path = pf.getWalkablePath();
        if (path == null || path.isEmpty()) {
            return false;
        }
        Actor actor = Rs2Player.getInteracting();
        if (actor == null) {
            return false;
        }
        WorldPoint loc = actor.getWorldLocation();
        if (loc == null) {
            return false;
        }
        for (WorldPoint p : path) {
            if (p == null || p.getPlane() != loc.getPlane()) {
                continue;
            }
            if (p.distanceTo2D(loc) <= 2) {
                return true;
            }
        }
        return false;
    }

    private static long stallThresholdMs() {
        return Rs2WalkerStallPolicy.computeThresholdMs(
                STALL_BASE_MS,
                STALL_COMBAT_MULTIPLIER,
                STALL_ANIMATING_MULTIPLIER,
                STALL_MOVING_MULTIPLIER,
                STALL_INTERIM_MINIMAP_MULTIPLIER,
                STALL_INTERACTING_MULTIPLIER,
                Rs2Player.isInCombat(),
                Rs2Player.isAnimating(),
                Rs2Player.isMoving(),
                interimTargetWp != null,
                (Rs2Player.isMoving() || Rs2Player.isAnimating()) && interactingActorNearWalkablePath());
    }

    private static boolean isStuckTooLong() {
        if (Rs2WalkerStallPolicy.shouldSkipStallAccounting(LEAGUES_AREA_PENDING_STALL_MAX_AGE_MS)) {
            return false;
        }

        long routeProgressAt = routeProgressAdvancedAtMs;
        if (routeProgressAt > 0L && System.currentTimeMillis() - routeProgressAt < ROUTE_PROGRESS_STALL_GRACE_MS) {
            return false;
        }

        return lastMovedTimeMs > 0 && System.currentTimeMillis() - lastMovedTimeMs > stallThresholdMs();
    }

    /**
     * @param start
     */
    public void setStart(WorldPoint start) {
        Pathfinder pathfinder = Rs2PathApi.getPathfinder();
        if (pathfinder == null) {
            return;
        }
        Set<WorldPoint> targets = pathfinder.getTargets();
        Rs2PathApi.setStartPointSet(true);
        if (isClientThread()) {
            Microbot.getClientThread().runOnSeperateThread(() -> restartPathfinding(start, targets));
        } else {
            restartPathfinding(start, targets);
        }
    }

    /**
     * Checks the distance between startpoint and endpoint using ShortestPath
     *
     * @param startpoint
     * @param endpoint
     * @return distance
     */
    public static int getDistanceBetween(WorldPoint startpoint, WorldPoint endpoint) {
        Set<WorldPoint> ends = Set.of(endpoint);
        Pathfinder pathfinder = new Pathfinder(Rs2PathApi.getPathfinderConfig(), startpoint, ends);
        pathfinder.run();
        return pathfinder.getPath().size();
    }

    /**
     * Forwards to {@link Rs2LeaguesTransport#recordTransportAttempt} for Leagues locked-region chat correlation.
     * Delegate records only teleport-like transports while Leagues is active (seasonal + spells/items, e.g. ectophial).
     */
    public static void recordTransportAttempt(Transport transport)
    {
        Rs2LeaguesTransport.recordTransportAttempt(transport);
    }

    /**
     * Writes {@code phase="result"} for {@link Rs2LeaguesTransport#appendTransportObservation} (seasonal rows only).
     */
    private static void recordTransportResult(Transport transport, boolean success)
    {
        if (transport == null || transport.getType() != TransportType.SEASONAL_TRANSPORT)
        {
            return;
        }
        if (!Rs2LeaguesTransport.isLeaguesActive())
        {
            return;
        }
        Rs2LeaguesTransport.appendTransportObservation("result", transport, success, success ? "ok" : "fail");
    }

    /** Wraps an action with {@link #recordTransportAttempt} + {@link #recordTransportResult} (seasonal JSONL, Leagues snapshot for teleports).
     * @see net.runelite.client.plugins.microbot.util.leaguetransport.Rs2LeaguesTransport
     */
    private static boolean attemptObserved(Transport transport, BooleanSupplier action)
    {
        if (transport == null || action == null)
        {
            return false;
        }
        boolean leaguesActive = Rs2LeaguesTransport.isLeaguesActive();
        // Snapshot attempt for Leagues locked-region chat correlation (avoid churn outside leagues).
        if (leaguesActive)
        {
            recordTransportAttempt(transport);
        }
        boolean ok = action.getAsBoolean();
        if (leaguesActive)
        {
            recordTransportResult(transport, ok);
        }
        return ok;
    }

    /**
     * Like {@link #attemptObserved} but does not call {@link #recordTransportAttempt} before the action.
     * Seasonal handlers record attempts at their click sites so {@link Rs2LeaguesTransport#getLastTransportAttemptSnapshot}
     * matches the handler that actually ran (Leagues Area vs MoA).
     */
    private static boolean attemptObservedWithoutAttemptRecord(Transport transport, BooleanSupplier action)
    {
        if (transport == null || action == null)
        {
            return false;
        }
        boolean leaguesActive = Rs2LeaguesTransport.isLeaguesActive();
        boolean ok = action.getAsBoolean();
        if (leaguesActive)
        {
            recordTransportResult(transport, ok);
        }
        return ok;
    }

    /**
     * Tries configured seasonal transport handlers for the same {@link Transport} row.
     * Attempt recording is done inside each handler (for built-ins, {@link Rs2LeaguesTransport#tryHandleLeaguesAreaTransportResult})
     * — use {@link #attemptObservedWithoutAttemptRecord} at the call site.
     */
    private static boolean handleSeasonalTransport(Transport transport) {
        if (transport == null) {
            return false;
        }
        String displayInfo = transport.getDisplayInfo();
        if (displayInfo == null) return false;

        List<SeasonalTransportHandler> handlers = seasonalTransportHandlers;
        for (SeasonalTransportHandler h : handlers)
        {
            if (h == null)
            {
                continue;
            }
            if (!h.matches(transport))
            {
                continue;
            }
            if (h.tryUse(transport))
            {
                return true;
            }
        }
        Telemetry.incrementSeasonalHandlerMiss();
        if (log.isDebugEnabled() && SEASONAL_HANDLER_MISS_LOGGED_COUNT.get() < SEASONAL_HANDLER_MISS_LOG_CAP)
        {
            WorldPoint destWp = transport.getDestination();
            String hash = Integer.toHexString(displayInfo.hashCode());
            String tail = displayInfo.length() > 160
                    ? displayInfo.substring(0, 160) + "|h" + hash
                    : displayInfo + "|h" + hash;
            final String missKey;
            Integer packedTileOrNull = null;
            if (destWp != null)
            {
                packedTileOrNull = WorldPointUtil.packWorldPoint(destWp);
                missKey = Integer.toHexString(packedTileOrNull) + "|" + tail;
            }
            else
            {
                missKey = "nodest|" + tail;
            }
            if (SEASONAL_HANDLER_MISS_LOGGED.add(missKey))
            {
                // Best-effort cap: only increment while below cap; duplicates and races are fine for debug-only logs.
                for (;;)
                {
                    int prev = SEASONAL_HANDLER_MISS_LOGGED_COUNT.get();
                    if (prev >= SEASONAL_HANDLER_MISS_LOG_CAP)
                    {
                        break;
                    }
                    if (SEASONAL_HANDLER_MISS_LOGGED_COUNT.compareAndSet(prev, prev + 1))
                    {
                        break;
                    }
                }
                String sample = displayInfo.length() > 160 ? displayInfo.substring(0, 160) + "…" : displayInfo;
                if (packedTileOrNull != null)
                {
                    sample = sample + " destPacked=" + Integer.toHexString(packedTileOrNull);
                }
                log.debug("[Walker] seasonal transport unmatched by configured handlers (expect pathfinder-only matching rows); key={} sample={}",
                        missKey, sample);
            }
        }
        return false;
    }

    private static boolean handleSpiritTree(Transport transport) {
        // Get Transport Information
        String displayInfo = transport.getDisplayInfo();
        int objectId = transport.getObjectId();
        if (log.isDebugEnabled())
        {
            log.debug("[Walker] handleSpiritTree: displayInfo={}, objectId={}", displayInfo, objectId);
        }
        if (displayInfo == null || displayInfo.isEmpty()) {
            if (log.isDebugEnabled())
            {
                log.debug("[Walker] handleSpiritTree: displayInfo empty, returning false");
            }
            return false;
        }

        if (!Rs2Widget.isWidgetVisible(ComponentID.ADVENTURE_LOG_CONTAINER)) {
            TileObject spiritTree = Rs2GameObject.findObjectById(objectId);
            if (log.isDebugEnabled())
            {
                log.debug("[Walker] handleSpiritTree: findObjectById({}) returned {}",
                        objectId, spiritTree != null ? "non-null @ " + spiritTree.getWorldLocation() : "NULL");
            }
            if (spiritTree == null) {
                // POH fix: handleSpiritTree's findObjectById uses the transport's objectId
                // which is keyed from the TSV. Inside a POH the spirit tree is a different
                // object id than the overworld TSV expects. Fall back to the PohTeleports
                // helper which knows the full set of POH spirit-tree ids.
                spiritTree = PohTeleports.getSpiritTree();
                if (log.isDebugEnabled())
                {
                    log.debug("[Walker] handleSpiritTree: POH fallback getSpiritTree() returned {}",
                            spiritTree != null ? "non-null @ " + spiritTree.getWorldLocation() : "NULL");
                }
            }
            boolean interactResult = Rs2GameObject.interact(spiritTree, "Travel");
            if (log.isDebugEnabled())
            {
                log.debug("[Walker] handleSpiritTree: interact(spiritTree, Travel) returned {}", interactResult);
            }
            if (!interactResult) {
                return false;
            }
        }

        boolean result = interactWithAdventureLog(transport);
        if (log.isDebugEnabled())
        {
            log.debug("[Walker] handleSpiritTree: interactWithAdventureLog returned {}", result);
        }
        return result;
    }

    private static boolean handleMinigameTeleport(Transport transport) {
        final Object[] selectedOpListener = new Object[]{489, 0, 0};
        final List<Integer> teleportGraphics = List.of(800, 802, 803, 804);

        @Component final int GROUPING_BUTTON_COMPONENT_ID = 46333957; // 707.5

        @Component final int DROPDOWN_BUTTON_COMPONENT_ID = 4980760; // 76.24
        final int DROPDOWN_SELECTED_SPRITE_ID = 773;

        @Component final int MINIGAME_LIST = 4980758; // 76.22
        @Component final int SELECTED_MINIGAME = 4980747; // 76.11
        @Component final int TELEPORT_BUTTON = 4980768; // 76.32

        // Minigame teleports cant be used if a dialogue is open.
        if (Rs2Dialogue.isInDialogue()) {
            var playerLocation = Rs2Player.getLocalLocation();
            walkFastLocal(playerLocation);
        }

        if (Rs2Tab.getCurrentTab() != InterfaceTab.CHAT) {
            Rs2Tab.switchTo(InterfaceTab.CHAT);
            sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.CHAT);
        }

        Widget groupingBtn = Rs2Widget.getWidget(GROUPING_BUTTON_COMPONENT_ID);
        if (groupingBtn == null) return false;

        if (!Arrays.equals(groupingBtn.getOnOpListener(), selectedOpListener)) {
            Rs2Widget.clickWidget(groupingBtn);
            sleepUntil(() -> Arrays.equals(groupingBtn.getOnOpListener(), selectedOpListener));
        }

        boolean hasMultipleDestination = transport.getDisplayInfo().contains(":");
        String destination = hasMultipleDestination
                ? transport.getDisplayInfo().split(":")[0].trim().toLowerCase()
                : transport.getDisplayInfo().trim().toLowerCase();

        Widget selectedWidget = Rs2Widget.getWidget(SELECTED_MINIGAME);
        if (selectedWidget == null) return false;
        if (!selectedWidget.getText().equalsIgnoreCase(destination)) {
            Widget dropdownBtn = Rs2Widget.getWidget(DROPDOWN_BUTTON_COMPONENT_ID);
            if (dropdownBtn == null) return false;

            if (dropdownBtn.getSpriteId() != DROPDOWN_SELECTED_SPRITE_ID) {
                Rs2Widget.clickWidget(dropdownBtn);
                sleepUntil(() -> Rs2Widget.findWidget(DROPDOWN_SELECTED_SPRITE_ID, List.of(Rs2Widget.getWidget(DROPDOWN_BUTTON_COMPONENT_ID))) != null);
            }

            Widget minigameWidgetParent = Rs2Widget.getWidget(MINIGAME_LIST);
            if (minigameWidgetParent == null) return false;
            List<Widget> minigameWidgetList = Arrays.stream(minigameWidgetParent.getDynamicChildren())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            Widget destinationWidget = Rs2Widget.findWidget(destination, minigameWidgetList);
            if (destinationWidget == null) return false;

            NewMenuEntry destinationMenuEntry = new NewMenuEntry()
                    .option("Select")
                    .target("")
                    .identifier(1)
                    .type(MenuAction.CC_OP)
                    .param0(destinationWidget.getIndex())
                    .param1(minigameWidgetParent.getId())
                    .forceLeftClick(false);

            Microbot.doInvoke(destinationMenuEntry, new Rectangle(1, 1));
            sleepUntil(() -> Rs2Widget.getWidget(SELECTED_MINIGAME).getText().equalsIgnoreCase(destination));
        }

        Widget teleportBtn = Rs2Widget.getWidget(TELEPORT_BUTTON);
        if (teleportBtn == null) return false;
        Rs2Widget.clickWidget(teleportBtn);

        if (transport.getDisplayInfo().toLowerCase().contains("rat pits")) {
            Rs2Dialogue.sleepUntilSelectAnOption();
            Rs2Dialogue.clickOption(transport.getDisplayInfo().split(":")[1].trim().toLowerCase());
        }

        sleepUntil(Rs2Player::isAnimating);
        return sleepUntilTrue(() -> !Rs2Player.isAnimating() && teleportGraphics.stream().noneMatch(Rs2Player::hasSpotAnimation), 100, 20000);
    }

    private static boolean handleCanoe(Transport transport) {
        String displayInfo = transport.getDisplayInfo();
        if (displayInfo == null || displayInfo.isEmpty()) return false;

        List<String> validActions = List.of("chop-down", "shape-canoe", "float canoe", "paddle canoe");
        ObjectComposition CANOE_COMPOSITION = Rs2GameObject.convertToObjectComposition(transport.getObjectId());
        if (CANOE_COMPOSITION == null) return false;

        String currentAction = Arrays.stream(CANOE_COMPOSITION.getActions())
                .filter(Objects::nonNull)
                .filter(act -> validActions.contains(act.toLowerCase())).findFirst().orElse(null);
        if (currentAction == null || currentAction.isEmpty()) {
            log.error("Unable to find canoe action");
            return false;
        }

        switch (currentAction) {
            case "Chop-down":
                Rs2GameObject.interact(transport.getObjectId(), "Chop-down");
                sleepUntil(() -> Rs2Player.isAnimating(1200));
                return sleepUntilTrue(() -> {
                    ObjectComposition composition = Rs2GameObject.convertToObjectComposition(transport.getObjectId());

                    if (composition == null) return false;
                    return Arrays.stream(composition.getActions()).filter(Objects::nonNull).noneMatch(currentAction::equals) && !Rs2Player.isAnimating();
                }, 300, 10000);
            case "Shape-Canoe":
                @Component final int CANOE_SELECTION_PARENT = 27262976; // 416.3
                @Component final int CANOE_SHAPING_TEXT = 27262986; // 416.10

                Rs2GameObject.interact(transport.getObjectId(), "Shape-Canoe");
                boolean isCanoeShapeTextVisible = sleepUntilTrue(() -> Rs2Widget.isWidgetVisible(CANOE_SHAPING_TEXT), 100, 10000);
                if (!isCanoeShapeTextVisible) {
                    log.error("Canoe shape text is not visible within timeout period");
                    return false;
                }

                final int woodcuttingLevel = Rs2Player.getRealSkillLevel(Skill.WOODCUTTING);
                String canoeOption;
                if (woodcuttingLevel >= 57) {
                    canoeOption = "Waka canoe";
                } else if (woodcuttingLevel >= 42) {
                    canoeOption = "Stable dugout canoe";
                } else if (woodcuttingLevel >= 27) {
                    canoeOption = "Dugout canoe";
                } else if (woodcuttingLevel >= 12) {
                    canoeOption = "Log canoe";
                } else {
                    // Not high enough level to make any canoe
                    return false;
                }

                Widget canoeSelectionParentWidget = Rs2Widget.getWidget(CANOE_SELECTION_PARENT);
                if (canoeSelectionParentWidget == null) return false;
                Widget canoeSelectionWidget = Rs2Widget.findWidget("Make " + canoeOption, List.of(canoeSelectionParentWidget));
                Rs2Widget.clickWidget(canoeSelectionWidget);
                sleepUntil(() -> Rs2Player.isAnimating(1200));
                return sleepUntilTrue(() -> {
                    ObjectComposition composition = Rs2GameObject.convertToObjectComposition(transport.getObjectId());

                    if (composition == null) return false;
                    return Arrays.stream(composition.getActions()).filter(Objects::nonNull).noneMatch(currentAction::equals) && !Rs2Player.isAnimating();
                }, 300, 10000);
            case "Float Canoe":
                Rs2GameObject.interact(transport.getObjectId(), "Float Canoe");
                sleepUntil(() -> Rs2Player.isAnimating(1200));
                return sleepUntilTrue(() -> {
                    ObjectComposition composition = Rs2GameObject.convertToObjectComposition(transport.getObjectId());

                    if (composition == null) return false;
                    return Arrays.stream(composition.getActions()).filter(Objects::nonNull).noneMatch(currentAction::equals) && !Rs2Player.isAnimating();
                }, 300, 10000);
            case "Paddle Canoe":
                if (!Rs2GameObject.interact(transport.getObjectId(), "Paddle Canoe")) {
                    log.error("Failed to interact with canoe station");
                    return false;
                }

                // Wait for the player to actually walk to the canoe station and stop moving
                // before checking for the destination map widget. The interact call only
                // queues the click; the player still has to walk there.
                sleepUntil(Rs2Player::isMoving, 2000);
                sleepUntilTrue(() -> !Rs2Player.isMoving(), 100, 30000);

                // OSRS update moved the canoe destination map from group 647 to
                // CanoeMapLum (953) for the river Lum chain. CanoeMapDougne (952)
                // is for a different chain not currently used by canoes.tsv.
                boolean isDestinationMapVisible = sleepUntilTrue(
                        () -> Rs2Widget.isWidgetVisible(InterfaceID.CanoeMapLum.MAIN_MAP),
                        100, 10000);
                if (!isDestinationMapVisible) {
                    log.error("Canoe destination map (CanoeMapLum) not visible within timeout period");
                    return false;
                }

                Widget destinationListWidget = Rs2Widget.getWidget(InterfaceID.CanoeMapLum.DESTINATIONS);
                if (destinationListWidget == null) return false;
                Widget destination = Rs2Widget.findWidget("Travel to " + displayInfo, List.of(destinationListWidget), false);
                if (destination == null) {
                    log.error("Could not find canoe destination widget for: {}", displayInfo);
                    return false;
                }
                Rs2Widget.clickWidget(destination);

                Rs2Dialogue.waitForCutScene(100, 15000);
                return sleepUntilTrue(() -> isPlayerWithinChebyshevOf(transport.getDestination(), OFFSET * 2), 100, 5000);
        }
        return false;
    }

    private static boolean isQuetzalWhistleItemId(int itemId) {
        return itemId == ItemID.HG_QUETZALWHISTLE_BASIC
                || itemId == ItemID.HG_QUETZALWHISTLE_ENHANCED
                || itemId == ItemID.HG_QUETZALWHISTLE_PERFECTED
                || itemId == ItemID.HG_QUETZALWHISTLE_PERFECTED_INFINITE;
    }

    /**
     * Inventory menu action order for opening the Quetzal map from the whistle.
     * Generic teleport keyword lists put {@code invoke} before {@code blow}; matching Invoke first often does not open the map.
     */
    private static final List<String> QUETZAL_WHISTLE_OPEN_ACTION_PRIORITY = Arrays.asList(
            "blow", "use", "invoke", "open", "teleport", "rub", "commune", "play");

    private static String pickQuetzalWhistleInventoryMenuAction(Rs2ItemModel rs2Item) {
        assert rs2Item != null;
        String primary = rs2Item.getActionFromList(QUETZAL_WHISTLE_OPEN_ACTION_PRIORITY);
        if (primary != null) {
            return primary;
        }
        return rs2Item.getActionFromList(Arrays.asList(
                "invoke", "empty", "consume", "reminisce", "signal", "squash"));
    }

    /**
     * Labels match {@code quetzals.tsv} destination rows (map icon text).
     */
    private static String quetzalMapLabelForDestination(WorldPoint dest) {
        assert dest != null;
        final int[][] coords = {
                {1389, 2901, 0}, {1697, 3140, 0}, {1585, 3053, 0}, {1510, 3221, 0}, {1548, 2995, 0},
                {1437, 3171, 0}, {1779, 3111, 0}, {1700, 3037, 0}, {1670, 2933, 0}, {1446, 3108, 0},
                {1613, 3300, 0}, {1226, 3091, 0}, {1344, 3022, 0}, {1411, 3361, 0},
        };
        final String[] labels = {
                "Aldarin", "Civitas illa Fortis", "Hunter Guild", "Quetzacalli Gorge", "Sunset Coast",
                "The Teomat", "Fortis Colosseum", "Outer Fortis", "Colossal Wyrm Remains", "Cam Torum Entrance",
                "Salvager Overlook", "Tal Teklan", "Kastori", "Auburnvale",
        };
        assert coords.length == labels.length;
        // Bank / script targets often sit several tiles off quetzals.tsv landing coords.
        final int matchTiles = 15;
        for (int i = 0; i < coords.length; i++) {
            WorldPoint p = new WorldPoint(coords[i][0], coords[i][1], coords[i][2]);
            if (dest.distanceTo2D(p) <= matchTiles && dest.getPlane() == p.getPlane()) {
                return labels[i];
            }
        }
        return null;
    }

    /**
     * Option text on the Quetzal map — Renu uses {@link InterfaceID.QuetzalMenu}, whistle uses {@link InterfaceID.QuetzalwhistleMenu}
     * (same icon labels). Prefers resolving from {@link Transport#getDestination()} so bank/custom tiles match.
     */
    private static String resolveQuetzalMapOptionLabel(Transport transport) {
        assert transport != null;
        WorldPoint dest = transport.getDestination();
        if (dest != null) {
            String byCoords = quetzalMapLabelForDestination(dest);
            if (byCoords != null && !byCoords.isEmpty()) {
                return byCoords;
            }
        }
        String di = transport.getDisplayInfo();
        if (di != null && di.contains(":")) {
            String[] parts = di.split(":", 2);
            if (parts.length >= 2) {
                String loc = parts[1].trim();
                if (!loc.isEmpty()) {
                    return loc;
                }
            }
        }
        return dest != null ? quetzalMapLabelForDestination(dest) : null;
    }

    /** True when any Quetzal or whistle-map layer is visible (CONTENTS alone can stay hidden while MAP/ICONS show). */
    private static boolean isQuetzalMapInterfaceVisible() {
        return Rs2Widget.isWidgetVisible(InterfaceID.QuetzalMenu.UNIVERSE)
                || Rs2Widget.isWidgetVisible(InterfaceID.QuetzalMenu.MAP)
                || Rs2Widget.isWidgetVisible(InterfaceID.QuetzalMenu.ICONS)
                || Rs2Widget.isWidgetVisible(InterfaceID.QuetzalMenu.CONTENTS)
                || Rs2Widget.isWidgetVisible(InterfaceID.QuetzalwhistleMenu.UNIVERSE)
                || Rs2Widget.isWidgetVisible(InterfaceID.QuetzalwhistleMenu.MAP)
                || Rs2Widget.isWidgetVisible(InterfaceID.QuetzalwhistleMenu.ICONS)
                || Rs2Widget.isWidgetVisible(InterfaceID.QuetzalwhistleMenu.CONTENTS);
    }

    private static boolean finishQuetzalWhistleTransport(Transport transport) {
        assert transport != null;
        WorldPoint dest = transport.getDestination();
        assert dest != null;
        WorldPoint pl = Rs2Player.getWorldLocation();
        if (pl != null && pl.getPlane() == dest.getPlane() && pl.distanceTo2D(dest) < OFFSET) {
            log.debug("Quetzal whistle: already within {} tiles of {}, skipping map", OFFSET, dest);
            return true;
        }
        String mapLabel = resolveQuetzalMapOptionLabel(transport);
        if (mapLabel == null || mapLabel.isEmpty()) {
            log.warn("Quetzal whistle: could not resolve map label (displayInfo={}, destination={})",
                    transport.getDisplayInfo(), dest);
            return false;
        }
        Rs2Player.waitForAnimation(1800);
        sleepUntil(() -> isQuetzalMapInterfaceVisible() || !Rs2Player.isAnimating(), 1400);
        sleep(Rs2Random.between(120, 260));
        return clickQuetzalMapDestination(mapLabel, dest);
    }

    /**
     * Finds destination row/icon; map can open before icon layer is built — search full subtree from several roots,
     * not only {@link Widget#getDynamicChildren()} of {@link InterfaceID.QuetzalMenu#ICONS}.
     */
    private static Widget findQuetzalMapDestinationWidget(String mapOptionLabel) {
        assert mapOptionLabel != null && !mapOptionLabel.isEmpty();
        int[] roots = {
                InterfaceID.QuetzalMenu.ICONS,
                InterfaceID.QuetzalMenu.MAP,
                InterfaceID.QuetzalMenu.SCROLL,
                InterfaceID.QuetzalMenu.CONTENTS,
                InterfaceID.QuetzalMenu.UNIVERSE,
                InterfaceID.QuetzalwhistleMenu.ICONS,
                InterfaceID.QuetzalwhistleMenu.MAP,
                InterfaceID.QuetzalwhistleMenu.SCROLL,
                InterfaceID.QuetzalwhistleMenu.CONTENTS,
                InterfaceID.QuetzalwhistleMenu.UNIVERSE,
        };
        for (int rootId : roots) {
            // Widget#getDynamicChildren / isHidden must not run off the client thread — use marshalled helpers.
            if (Rs2Widget.isHidden(rootId)) {
                continue;
            }
            Widget root = Rs2Widget.getWidget(rootId);
            if (root == null) {
                continue;
            }
            Widget hit = Rs2Widget.findWidget(mapOptionLabel, List.of(root), false);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    /**
     * Opens no NPC — caller must already have opened the Quetzal map (whistle or Renu).
     */
    private static boolean clickQuetzalMapDestination(String mapOptionLabel, WorldPoint expectedDestination) {
        assert mapOptionLabel != null && !mapOptionLabel.isEmpty();
        assert expectedDestination != null;
        long quetzalStartAt = System.currentTimeMillis();

        WorldPoint here = Rs2Player.getWorldLocation();
        if (here != null && here.getPlane() == expectedDestination.getPlane()
                && here.distanceTo2D(expectedDestination) < OFFSET) {
            log.debug("Quetzal map: already within {} tiles of {}, skipping map click", OFFSET, expectedDestination);
            return true;
        }

        boolean mapVisible = sleepUntilTrue(() -> isQuetzalMapInterfaceVisible(), 100, QUETZAL_MAP_VISIBLE_WAIT_MS);
        if (!mapVisible) {
            log.error("Quetzal map UI not visible within timeout (label={}, checked UNIVERSE/MAP/ICONS/CONTENTS)",
                    mapOptionLabel);
            return false;
        }
        WebWalkLog.tmark("quetzal_ui_opened", System.currentTimeMillis() - quetzalStartAt, expectedDestination, Rs2Player.getWorldLocation(),
                "label=" + mapOptionLabel);

        // ICONS subtree can attach shortly after the shell — brief pause before walking widget tree from walker thread.
        sleep(Rs2Random.between(80, 160));

        AtomicReference<Widget> destRef = new AtomicReference<>();
        boolean iconReady = sleepUntilTrue(() -> {
            Widget w = findQuetzalMapDestinationWidget(mapOptionLabel);
            destRef.set(w);
            return w != null;
        }, 120, QUETZAL_ICON_READY_WAIT_MS);
        Widget actionWidget = destRef.get();
        if (!iconReady || actionWidget == null) {
            log.error("Could not find Quetzal map icon for: {} (waited for widget tree after map visible)", mapOptionLabel);
            return false;
        }
        WebWalkLog.tmark("quetzal_option_found", System.currentTimeMillis() - quetzalStartAt, expectedDestination, Rs2Player.getWorldLocation(),
                "label=" + mapOptionLabel);

        Rs2Widget.clickWidget(actionWidget);
        log.info("Quetzal map: traveling to {} -> {}", mapOptionLabel, expectedDestination);
        WebWalkLog.tmark("quetzal_click_sent", System.currentTimeMillis() - quetzalStartAt, expectedDestination, Rs2Player.getWorldLocation(),
                "label=" + mapOptionLabel);
        return sleepUntilTrue(() -> isPlayerWithinChebyshevOf(expectedDestination, OFFSET), 100, 8000);
    }

    private static boolean handleQuetzal(Transport transport) {
        String displayInfo = transport.getDisplayInfo();
        if (displayInfo == null || displayInfo.isEmpty()) return false;

        WorldPoint destCheck = transport.getDestination();
        WorldPoint plCheck = Rs2Player.getWorldLocation();
        if (destCheck != null && plCheck != null && plCheck.getPlane() == destCheck.getPlane()
                && plCheck.distanceTo2D(destCheck) < OFFSET) {
            log.debug("Quetzal Renu: already within {} tiles of {}, skip travel UI", OFFSET, destCheck);
            return true;
        }

        Rs2NpcModel renu = Rs2Npc.getNpc(NpcID.QUETZAL_CHILD_GREEN);

        if (Rs2Tile.isTileReachable(transport.getOrigin()) && Rs2Npc.interact(renu, "travel")) {
            Rs2Player.waitForWalking();
            WorldPoint dest = transport.getDestination();
            String mapLabel = resolveQuetzalMapOptionLabel(transport);
            if (mapLabel == null || mapLabel.isEmpty() || dest == null) {
                return false;
            }
            return clickQuetzalMapDestination(mapLabel, dest);
        }
        return false;
    }

    private static boolean handleMasterScrollBook(String destination) {
        boolean isMasterScrollBookOpen = sleepUntilTrue(() -> Rs2Widget.isWidgetVisible(InterfaceID.Bookofscrolls.CONTENTS), 100, 10000);
        if (!isMasterScrollBookOpen) {
            log.error("Master Scroll Book did not open within timeout period");
            return false;
        }

        Widget bookOfScrollsWidget = Rs2Widget.getWidget(InterfaceID.Bookofscrolls.CONTENTS);
        List<Widget> bookOfScrollsChildren = Arrays.stream(bookOfScrollsWidget.getStaticChildren())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Widget destinationWidget = Rs2Widget.findWidget(destination, bookOfScrollsChildren, false);
        if (destinationWidget == null) return false;
        boolean interaction = Rs2Widget.clickWidget(destinationWidget);
        if (interaction && destination.equalsIgnoreCase("Revenant cave")) {
            Rs2Dialogue.sleepUntilInDialogue();
            return Rs2Dialogue.clickOption("Yes, teleport me now");
        }
        return interaction;
    }

    private static boolean handleMagicCarpet(Transport transport) {
        final int flyingPoseAnimation = 6936;
        var rugMerchant = Rs2Npc.getNpc(transport.getObjectId());
        if (rugMerchant == null) return false;

        Rs2Npc.interact(rugMerchant, transport.getAction());
        Rs2Dialogue.sleepUntilInDialogue();
        Rs2Dialogue.clickOption(transport.getDisplayInfo());
        sleepUntil(() -> Rs2Player.getPoseAnimation() == flyingPoseAnimation, 10000);
        return sleepUntilTrue(() -> Rs2Player.getPoseAnimation() != flyingPoseAnimation, 600,60000);
    }

    private static boolean handleCharterShip(Transport transport) {
        String npcName = transport.getName();

        Rs2NpcModel npc = Rs2Npc.getNpc(npcName);
        log.info("Charter Ship NPC: " + npcName + " - " + (npc != null ? npc.getId() : "not found"));
        if (Rs2Npc.canWalkTo(npc, 20) && Rs2Npc.interact(npc, transport.getAction())) {
            Rs2Player.waitForWalking();
            if (!sleepUntil(() -> Rs2Widget.isWidgetVisible(885, 4), 5000)) {
                return false;
            }

            Widget destinationWidget = findCharterDestinationWidget(transport.getDisplayInfo());
            if (!invokeCharterDestinationWidget(destinationWidget, transport.getDisplayInfo())) {
                return false;
            }
            confirmCharterTravelIfPrompted();
            return true;
        }
        return false;
    }

    private static Widget findCharterDestinationWidget(String destinationText) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget root = Microbot.getClient().getWidget(885, 4);
            if (root == null || root.isHidden()) {
                return null;
            }

            Widget textMatch = findCharterDestinationTextWidget(root, destinationText);
            if (textMatch == null) {
                return null;
            }

            Widget clickable = findClickableCharterWidget(textMatch, root);
            return clickable != null ? clickable : textMatch;
        }).orElse(null);
    }

    private static Widget findCharterDestinationTextWidget(Widget widget, String destinationText) {
        if (widget == null || widget.isHidden()) {
            return null;
        }
        if (charterWidgetMatchesDestination(widget, destinationText)) {
            return widget;
        }

        Widget[] staticChildren = widget.getStaticChildren();
        Widget found = findCharterDestinationTextWidget(staticChildren, destinationText);
        if (found != null) {
            return found;
        }

        Widget[] dynamicChildren = widget.getDynamicChildren();
        found = findCharterDestinationTextWidget(dynamicChildren, destinationText);
        if (found != null) {
            return found;
        }

        return findCharterDestinationTextWidget(widget.getNestedChildren(), destinationText);
    }

    private static Widget findCharterDestinationTextWidget(Widget[] widgets, String destinationText) {
        if (widgets == null) {
            return null;
        }
        for (Widget widget : widgets) {
            Widget found = findCharterDestinationTextWidget(widget, destinationText);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static boolean charterWidgetMatchesDestination(Widget widget, String destinationText) {
        String needle = normalizeCharterWidgetText(destinationText);
        if (needle.isEmpty()) {
            return false;
        }
        if (normalizeCharterWidgetText(widget.getText()).contains(needle)
                || normalizeCharterWidgetText(widget.getName()).contains(needle)) {
            return true;
        }
        String[] actions = widget.getActions();
        if (actions == null) {
            return false;
        }
        return Arrays.stream(actions)
                .filter(Objects::nonNull)
                .map(Rs2Walker::normalizeCharterWidgetText)
                .anyMatch(action -> action.contains(needle));
    }

    private static String normalizeCharterWidgetText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return Rs2UiHelper.stripTagsToSpace(text)
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }

    private static Widget findClickableCharterWidget(Widget widget, Widget root) {
        Widget current = widget;
        while (current != null) {
            if (hasWidgetActions(current)) {
                return current;
            }
            if (current == root) {
                return null;
            }
            current = current.getParent();
        }
        return null;
    }

    private static boolean hasWidgetActions(Widget widget) {
        String[] actions = widget.getActions();
        return actions != null && Arrays.stream(actions).anyMatch(action -> action != null && !action.isEmpty());
    }

    private static boolean invokeCharterDestinationWidget(Widget widget, String destinationText) {
        if (widget == null) {
            return false;
        }

        String option = getFirstWidgetAction(widget);
        if (option == null || option.isBlank()) {
            option = destinationText;
        }

        NewMenuEntry destinationMenuEntry = new NewMenuEntry()
                .option(option)
                .target("")
                .identifier(1)
                .type(MenuAction.CC_OP)
                .param0(widget.getIndex())
                .param1(widget.getId())
                .forceLeftClick(false);

        Rectangle bounds = widget.getBounds();
        Microbot.doInvoke(destinationMenuEntry, bounds != null ? bounds : Rs2UiHelper.getDefaultRectangle());
        return true;
    }

    private static String getFirstWidgetAction(Widget widget) {
        String[] actions = widget.getActions();
        if (actions == null) {
            return null;
        }
        return Arrays.stream(actions)
                .filter(action -> action != null && !action.isEmpty())
                .findFirst()
                .orElse(null);
    }

    private static void confirmCharterTravelIfPrompted() {
        if (sleepUntil(Rs2Dialogue::hasSelectAnOption, 2000)) {
            Rs2Dialogue.clickOption("Yes", true);
        }
    }
    /**
     * interact with interfaces like spirit tree etc...
     *
     * @param transport
     */
    private static boolean  interactWithAdventureLog(Transport transport) {
        if (transport.getDisplayInfo() == null || transport.getDisplayInfo().isEmpty()) return false;

        // Wait for the widget to become visible
        boolean isAdventureLogVisible = sleepUntilTrue(() -> !Rs2Widget.isHidden(ComponentID.ADVENTURE_LOG_CONTAINER), Rs2Player::isMoving, 100, 10000);

        if (!isAdventureLogVisible) {
            log.error("Widget did not become visible within the timeout.");
            return false;
        }

        String destinationString = transport.getDisplayInfo().replaceAll("^\\d+:\\s*", "");
        Widget destinationWidget = Rs2Widget.findWidget(destinationString, List.of(Rs2Widget.getWidget(187, 3)));
        if (destinationWidget == null) return false;

        Rs2Widget.clickWidget(destinationWidget);
        log.info("Traveling to {} - ({})", transport.getDisplayInfo(), transport.getDestination());
        return sleepUntilTrue(() -> isPlayerWithinChebyshevOf(transport.getDestination(), OFFSET), 100, 5000);
    }

    private static boolean handleGlider(Transport transport) {
        int TA_QUIR_PRIW = 9043972;
        int SINDARPOS = 9043975;
        int LEMANTO_ANDRA = 9043978;
        int KAR_HEWO = 9043981;
        int GANDIUS = 9043984;
        int OOKOOKOLLY_UNDRI = 9043993;
        int LEMANTOLLY_UNDRI = 9043989;

        // Get Transport Information
        String displayInfo = transport.getDisplayInfo();
        String npcName = transport.getName();
        String action = transport.getAction();

        final int GLIDER_PARENT_WIDGET = 138;
        final int GLIDER_CHILD_WIDGET = 0;

        // Check if the widget is already visible
        boolean isGliderMenuVisible = Rs2Widget.getWidget(GLIDER_PARENT_WIDGET, GLIDER_CHILD_WIDGET) != null;
        if (!isGliderMenuVisible) {
            // Find the glider NPC
            var gnome = Rs2Npc.getNpc(npcName);  // Use the NPC name to find the NPC
            if (gnome == null) {
                return false;
            }

            // Interact with the gnome glider NPC
            if (Rs2Npc.interact(gnome, action)) {
                sleepUntil(() -> !Rs2Widget.isHidden(GLIDER_PARENT_WIDGET, GLIDER_CHILD_WIDGET));
            }
        }


        // Wait for the widget to become visible
        boolean widgetVisible = sleepUntilTrue(() -> !Rs2Widget.isHidden(GLIDER_PARENT_WIDGET, GLIDER_CHILD_WIDGET), Rs2Player::isMoving, 100, 10000);

        if (!widgetVisible) {
            log.error("Widget did not become visible within the timeout.");
            return false;
        }

        if (displayInfo.isEmpty()) return false;

        switch (displayInfo) {
            case "Kar-Hewo":
                return Rs2Widget.clickWidget(KAR_HEWO);
            case "Ta Quir Priw":
                return Rs2Widget.clickWidget(TA_QUIR_PRIW);
            case "Sindarpos":
                return Rs2Widget.clickWidget(SINDARPOS);
            case "Lemanto Andra":
                return Rs2Widget.clickWidget(LEMANTO_ANDRA);
            case "Gandius":
                return Rs2Widget.clickWidget(GANDIUS);
            case "Ookookolly Undri":
                return Rs2Widget.clickWidget(OOKOOKOLLY_UNDRI);
            case "Lemantolly Undri":
                return Rs2Widget.clickWidget(LEMANTOLLY_UNDRI);
            default:
                log.error("{} not found on the interface.", displayInfo);
                return false;
        }
    }

    // Constants for widget IDs
    private static final int SLOT_ONE = 26083331;
    private static final int SLOT_TWO = 26083332;
    private static final int SLOT_THREE = 26083333;

    private static final int SLOT_ONE_CW_ROTATION = 26083347;
    private static final int SLOT_ONE_ACW_ROTATION = 26083348;
    private static final int SLOT_TWO_CW_ROTATION = 26083349;
    private static final int SLOT_TWO_ACW_ROTATION = 26083350;
    private static final int SLOT_THREE_CW_ROTATION = 26083351;
    private static final int SLOT_THREE_ACW_ROTATION = 26083352;
    private static int fairyRingGraphicId = 569;

    private static boolean handleFairyRing(Transport transport) {

        Rs2ItemModel startingWeapon = null;

        TileObject fairyRingObject = PohTeleports.isInHouse() ? PohTeleports.getFairyRings() : Rs2GameObject.getAll(o -> Objects.equals(o.getWorldLocation(), transport.getOrigin())).stream().findFirst().orElse(null);
        if (fairyRingObject == null) return false;

        if (!PohTeleports.isInHouse() && !Rs2GameObject.canWalkTo(fairyRingObject, 25)) return false;

        boolean hasLumbridgeElite = Microbot.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE) == 1;

        if (!hasLumbridgeElite) {
            if (Rs2Equipment.isWearing(EquipmentInventorySlot.WEAPON)) {
                startingWeapon = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
            }

            if (!Rs2Equipment.isWearing("Dramen staff") && !Rs2Equipment.isWearing("Lunar staff")) {
                if (Rs2Inventory.contains("Dramen staff")) {
                    Rs2Inventory.equip("Dramen staff");
                    sleepUntil(() -> Rs2Equipment.isWearing("Dramen staff"));
                } else if (Rs2Inventory.contains("Lunar staff")) {
                    Rs2Inventory.equip("Lunar staff");
                    sleepUntil(() -> Rs2Equipment.isWearing("Lunar staff"));
                } else {
                    return false;
                }
            }
        }

        String lastDestinationAction = "last-destination (" + transport.getDisplayInfo() + ")";
        String treeLastDestinationAction = "Ring-last-destination (" + transport.getDisplayInfo() + ")";
        ObjectComposition composition = Rs2GameObject.convertToObjectComposition(fairyRingObject);
        log.info("Interacting with Fairy Ring @ {}", fairyRingObject.getWorldLocation());

        // we can use the last-destination to handle fairy rings
        if (Rs2GameObject.hasAction(composition, lastDestinationAction, true)) {
            Rs2GameObject.interact(fairyRingObject, lastDestinationAction);
        } else if (Rs2GameObject.hasAction(composition, treeLastDestinationAction, true)) {
            Rs2GameObject.interact(fairyRingObject, treeLastDestinationAction);
        } else {
            // We have to configure fairy rings through the interface
            if (Rs2GameObject.hasAction(composition, "Configure", true)) {
                Rs2GameObject.interact(fairyRingObject, "Configure");
            } else if (Rs2GameObject.hasAction(composition, "Ring-configure", true)) {
                Rs2GameObject.interact(fairyRingObject, "Ring-configure");
            }
            sleepUntil(() -> !Rs2Player.isMoving() && !Rs2Widget.isHidden(ComponentID.FAIRY_RING_TELEPORT_BUTTON), 10000);

            if (Rs2Widget.isHidden(ComponentID.FAIRY_RING_TELEPORT_BUTTON)) {
                log.warn("Fairy ring interface did not open (interrupted by combat?). Retrying next iteration.");
                return false;
            }

            Widget slotOne = Rs2Widget.getWidget(SLOT_ONE);
            Widget slotTwo = Rs2Widget.getWidget(SLOT_TWO);
            Widget slotThree = Rs2Widget.getWidget(SLOT_THREE);
            if (slotOne == null || slotTwo == null || slotThree == null) {
                log.warn("Fairy ring slot widget(s) are null; interface may have closed unexpectedly.");
                return false;
            }

            rotateSlotToDesiredRotation(SLOT_ONE, slotOne.getRotationY(), getDesiredRotation(transport.getDisplayInfo().charAt(0)), SLOT_ONE_ACW_ROTATION, SLOT_ONE_CW_ROTATION);
            rotateSlotToDesiredRotation(SLOT_TWO, slotTwo.getRotationY(), getDesiredRotation(transport.getDisplayInfo().charAt(1)), SLOT_TWO_ACW_ROTATION, SLOT_TWO_CW_ROTATION);
            rotateSlotToDesiredRotation(SLOT_THREE, slotThree.getRotationY(), getDesiredRotation(transport.getDisplayInfo().charAt(2)), SLOT_THREE_ACW_ROTATION, SLOT_THREE_CW_ROTATION);
            Rs2Widget.clickWidget(ComponentID.FAIRY_RING_TELEPORT_BUTTON);
        }

        sleepUntil(() -> Rs2Player.getGraphicId() == fairyRingGraphicId, 5000);
        sleepUntil(() -> Objects.equals(Rs2Player.getWorldLocation(), transport.getDestination()) && Rs2Player.getGraphicId() != fairyRingGraphicId, 10000);

        if (startingWeapon != null) {
            Rs2ItemModel finalStartingWeapon = startingWeapon;
            Rs2Inventory.equip(finalStartingWeapon.getId());
            sleepUntil(() -> Rs2Equipment.isWearing(finalStartingWeapon.getId()));
        }
        return true;
    }

    /**
     * Rotates a fairy ring slot to the desired rotation value.
     * Calculates the most efficient rotation direction (clockwise or anticlockwise)
     * and performs the necessary number of rotations to reach the target.
     *
     * @param slotId The widget ID of the slot to rotate
     * @param currentRotation The current rotation value of the slot
     * @param desiredRotation The target rotation value to achieve
     * @param slotAcwRotationId The widget ID for anticlockwise rotation button
     * @param slotCwRotationId The widget ID for clockwise rotation button
     */
    private static void rotateSlotToDesiredRotation(int slotId, int currentRotation, int desiredRotation, int slotAcwRotationId, int slotCwRotationId) {
        int anticlockwiseTurns = (desiredRotation - currentRotation + 2048) % 2048;
        int clockwiseTurns = (currentRotation - desiredRotation + 2048) % 2048;

        int turns = Math.min(clockwiseTurns, anticlockwiseTurns) / 512;
        boolean rotateCW = clockwiseTurns <= anticlockwiseTurns;
        int rotationWidget = rotateCW ? slotCwRotationId : slotAcwRotationId;

        for (int i = 0; i < turns; i++) {
            final int previousRotation = currentRotation;
            Rs2Widget.clickWidget(rotationWidget);

            sleepUntil(() -> {
                Widget slotWidget = Rs2Widget.getWidget(slotId);
                return slotWidget != null && slotWidget.getRotationY() != previousRotation;
            }, 2000);

            Widget slotWidget = Rs2Widget.getWidget(slotId);
            if (slotWidget != null) {
                currentRotation = slotWidget.getRotationY();
            } else {
                break;
            }
        }

        sleepUntil(() -> {
            Widget slotWidget = Rs2Widget.getWidget(slotId);
            return slotWidget != null && slotWidget.getRotationY() == desiredRotation;
        }, 3000);
    }

    /**
     * Maps fairy ring letters to their corresponding rotation values.
     * Each letter corresponds to a specific rotation degree needed for fairy ring teleportation.
     *
     * @param letter The fairy ring letter (A-Z) to get rotation for
     * @return The rotation value (0, 512, 1024, or 1536) for the letter, or -1 if invalid
     */
    private static int getDesiredRotation(char letter) {
        switch (letter) {
            case 'A':
            case 'I':
            case 'P':
                return 0;
            case 'B':
            case 'J':
            case 'Q':
                return 512;
            case 'C':
            case 'K':
            case 'R':
                return 1024;
            case 'D':
            case 'L':
            case 'S':
                return 1536;
            default:
                return -1;
        }
    }

    /**
     * Checks if the specified item ID corresponds to a teleportation item.
     * This method examines all available transports and determines if the given item
     * can be used for teleportation purposes, including special items like dramen staff.
     *
     * @param itemId The item ID to check for teleportation capabilities
     * @return true if the item is a teleportation item, false otherwise
     */
    public static boolean isTeleportItem(int itemId) {
        if (Rs2PathApi.getPathfinderConfig().getAllTransports().isEmpty()) {
            Rs2PathApi.getPathfinderConfig().refresh();
        }

        Set<Integer> teleportItemIds = Rs2PathApi.getPathfinderConfig().getAllTransports().values()
                .stream()
                .flatMap(Set::stream)
                .filter(t -> TransportType.isTeleport(t.getType(), t.getOrigin()))
                .map(Transport::getItemIdRequirements)
                .flatMap(Set::stream)
                .flatMap(Set::stream)
                .collect(Collectors.toSet());

        // Items that are not included in transports
        teleportItemIds.add(ItemID.DRAMEN_STAFF);
        teleportItemIds.add(ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF);

        return teleportItemIds.contains(itemId);
    }


    /**
     * Finds the nearest accessible target from a list of WorldPoints using pathfinding.
     * This is a generalized version of the logic used in Rs2Bank.getNearestBank().
     *
     * @param startPoint The starting location for pathfinding
     * @param targets List of target WorldPoints to evaluate
     * @param tolerance Tolerance in tiles for matching the final path point to targets (default: 2)
     * @return The index of the nearest accessible target in the list, or -1 if none are reachable
     */
    public static int findNearestAccessibleTarget(WorldPoint startPoint, List<WorldPoint> targets, boolean useBankItems, int tolerance) {
        if (targets == null || targets.isEmpty()) {
            return -1;
        }

        if (startPoint == null) {
            startPoint = Rs2Player.getWorldLocation();
        }

        if (startPoint == null) {
            log.warn("Unable to determine starting point for pathfinding");
            return -1;
        }

        // Convert list to set for pathfinder
        Set<WorldPoint> targetSet = new HashSet<>(targets);

        // Store original configuration to restore later
        boolean originalUseBankItems =  Rs2PathApi.getPathfinderConfig().isUseBankItems();
        try {
            Rs2PathApi.getPathfinderConfig().setUseBankItems(useBankItems);
            // Configure pathfinder
            Rs2PathApi.getPathfinderConfig().refresh();
            // Run pathfinder
            Pathfinder pf = new Pathfinder(Rs2PathApi.getPathfinderConfig(), startPoint, targetSet);
            pf.run();

            List<WorldPoint> path = pf.getPath();
            if (path.isEmpty()) {
                log.debug("Unable to find path to any target from starting point: " + startPoint);
                return -1;
            }

            // Find which target corresponds to the end of the path
            WorldPoint nearestTile = path.get(path.size() - 1);
            WorldArea nearestTileArea = new WorldArea(nearestTile, tolerance, tolerance);

            // Find the target that matches the final path destination
            for (int i = 0; i < targets.size(); i++) {
                WorldPoint target = targets.get(i);
                WorldArea targetArea = new WorldArea(target, tolerance, tolerance);
                if (targetArea.intersectsWith2D(nearestTileArea)) {
                    log.debug("Found nearest accessible target at index " + i + ": " + target + " (path ended at: " + nearestTile + ")");
                    return i;
                }
            }

            log.debug("Path found but no target matched the destination: " + nearestTile);
            return -1;

        } finally {
            // Always restore original configuration
            Rs2PathApi.getPathfinderConfig().setUseBankItems(originalUseBankItems);
            Rs2PathApi.getPathfinderConfig().refresh();
        }
    }

    /**
     * Finds the nearest accessible target from a list of WorldPoints using pathfinding.
     * Uses default tolerance of 2 tiles and no bank item usage.
     *
     * @param startPoint The starting location for pathfinding
     * @param targets List of target WorldPoints to evaluate
     * @return The index of the nearest accessible target in the list, or -1 if none are reachable
     */
    public static int findNearestAccessibleTarget(WorldPoint startPoint, List<WorldPoint> targets) {
        return findNearestAccessibleTarget(startPoint, targets, false, 2);
    }

    /**
     * Finds the nearest accessible target from a list of WorldPoints using pathfinding.
     * Uses the player's current location as starting point.
     *
     * @param targets List of target WorldPoints to evaluate
     * @param useBankItems Whether to enable bank item usage for transport calculations
     * @return The index of the nearest accessible target in the list, or -1 if none are reachable
     */
    public static int findNearestAccessibleTarget(List<WorldPoint> targets, boolean useBankItems) {
        return findNearestAccessibleTarget(Rs2Player.getWorldLocation(), targets, useBankItems, 2);
    }

    /**
     * Finds the nearest accessible target from a list of WorldPoints using pathfinding.
     * Uses the player's current location as starting point and no bank item usage.
     *
     * @param targets List of target WorldPoints to evaluate
     * @return The index of the nearest accessible target in the list, or -1 if none are reachable
     */
    public static int findNearestAccessibleTarget(List<WorldPoint> targets) {
        return findNearestAccessibleTarget(Rs2Player.getWorldLocation(), targets, false, 2);
    }

    /**
     * Prepares and analyzes required transport items for reaching a destination.
     * Similar but improved to Rs2Slayer.prepareItemTransports()
     *
     * @param destination The target location to reach
     * @param useBankItems Whether to consider bank items in pathfinding
     * @return List of Transport objects that are missing required items
     */
    public static List<Transport> getTransportsForDestination(WorldPoint destination, boolean useBankItems) {
        return getTransportsForDestination(destination, useBankItems, TransportType.TELEPORTATION_ITEM);
    }

    /**
     * Prepares and analyzes required transport items for reaching a destination.
     * Similar but improved to Rs2Slayer.prepareItemTransports()
     *
     * @param destination The target location to reach
     * @param useBankItems Whether to consider bank items in pathfinding
     * @param prefTransportType The preferred transport type to prioritize
     * @return List of Transport objects that are missing required items
     */
    public static List<Transport> getTransportsForDestination(WorldPoint destination, boolean useBankItems, TransportType prefTransportType) {
        return Rs2WalkerBankingPlanner.getTransportsForDestination(destination, useBankItems, prefTransportType);
    }

    /**
     * Prepares and analyzes required transport items for reaching a destination.
     * Uses bank items in calculations by default.
     *
     * @param destination The target location to reach
     * @return List of Transport objects that are missing required items
     */
    public static List<Transport> prepareTransportsForDestination(WorldPoint destination) {
        return getTransportsForDestination(destination, true);
    }

    /**
     * Checks if the player has the required items for a specific transport.
     * Similar to Rs2Slayer.hasRequiredTeleportItem() but accessible in Rs2Walker.
     *
     * @param transport The transport to check requirements for
     * @return true if the player has all required items, false otherwise
     */
    public static boolean hasRequiredTransportItems(Transport transport) {
        return Rs2WalkerBankingPlanner.hasRequiredTransportItems(transport);
    }

    /**
     * Filters a list of transports to return only those missing required items.
     * Similar to Rs2Slayer.getMissingItemTransports() but accessible in Rs2Walker.
     *
     * @param transports List of transports to check
     * @return List of transports that are missing required items
     */
    public static List<Transport> getMissingTransports(List<Transport> transports) {
        return Rs2WalkerBankingPlanner.getMissingTransports(transports);
    }

    /**
     * Extracts item IDs and their required quantities for the given transports that are missing and available in bank.
     * Enhanced version that uses Rs2Magic and Rs2Spells systems for actual rune quantities on teleportation spells.
     *
     * @param transports List of transports to check for missing items
     * @return Map where key=itemId and value=quantity needed (actual quantities for teleportation spells)
     */
    public static Map<Integer, Integer> getMissingTransportItemIdsWithQuantities(List<Transport> transports) {
        return Rs2WalkerBankingPlanner.getMissingTransportItemIdsWithQuantities(transports);
    }

    /**
     * Extracts item IDs that are missing for the given transports and available in bank.
     * Legacy method maintained for backward compatibility.
     * Similar to Rs2Slayer.getMissingItemIds() but accessible in Rs2Walker.
     *
     * @param transports List of transports to check for missing items
     * @return List of item IDs that are needed and available in bank
     */
    public static List<Integer> getMissingTransportItemIds(List<Transport> transports) {
        return Rs2WalkerBankingPlanner.getMissingTransportItemIds(transports);
    }

    private static boolean isCurrencyBasedTransport(TransportType transportType) {
        return transportType == TransportType.BOAT
                || transportType == TransportType.CHARTER_SHIP
                || transportType == TransportType.SHIP
                || transportType == TransportType.MINECART
                || transportType == TransportType.MAGIC_CARPET
                || transportType == TransportType.TRANSPORT;
    }

    private static int getCurrencyItemId(String currencyName) {
        if (currencyName == null || currencyName.trim().isEmpty()) {
            return -1;
        }

        String currency = currencyName.trim().toLowerCase();
        switch (currency) {
            case "coins":
                return ItemID.COINS;
            case "ecto-token":
                return ItemID.ECTOTOKEN;
            default:
                log.warn("Unknown currency type: {}", currencyName);
                return -1;
        }
    }

    /**
     * Compares the efficiency of traveling directly to a target versus going via bank first.
     * This is useful when transport items may be needed from the bank.
     *
     * @param target The target destination
     * @param startPoint Starting location (null to use current player location)
     * @return TransportRouteAnalysis containing the analysis of both routes
     */
    public static TransportRouteAnalysis compareRoutes(WorldPoint startPoint,WorldPoint target) {
        return Rs2WalkerBankingPlanner.compareRoutes(startPoint, target);
    }

    /**
     * Compares direct vs banking route using current player location as start point.
     */
    public static TransportRouteAnalysis compareRoutes(WorldPoint target) {
        return compareRoutes(null,target);
    }

    /**
     * Travels to the target destination using the legacy walkTo-based approach with transport support.
     * Uses default settings: considers bank items and allows efficiency-based banking decisions.
     *
     * @param target The destination to travel to
     * @return true if travel was successful, false otherwise
     */
    public static boolean walkWithBankedTransports(WorldPoint target) {
        return walkWithBankedTransports(target, false);
    }
    public static boolean walkWithBankedTransports(WorldPoint target, boolean forceBanking) {
        int d = reachedDistanceOrDefault();
        return walkWithBankedTransportsAndState(target, d, forceBanking) == WalkerState.ARRIVED;
    }
    public static boolean walkWithBankedTransports(WorldPoint target, int distance, boolean forceBanking){
        WalkerState state = walkWithBankedTransportsAndState(target, distance, forceBanking);
        return state == WalkerState.ARRIVED;

    }
    /**
     * Travels to the target destination using the legacy walkTo-based approach with transport support.
     * Analyzes whether to go directly or via bank first for transport items.
     *
     * @param target The destination to travel to
     * @param forceBanking If true, forces banking route regardless of efficiency
     * @return true if travel was successful, false otherwise
     */
    public static WalkerState walkWithBankedTransportsAndState(WorldPoint target, int distance, boolean forceBanking) {
        if (target == null) {
            log.warn("Cannot travel to null target location");
            return WalkerState.EXIT;
        }
        if (isClientThread()) {
            log.error("Please do not call the walker from the main thread");
            return WalkerState.EXIT;
        }
        if (!walkerLock.tryLock()) {
            log.warn("[Walker] concurrent banked-transport walk detected, waiting for in-flight walk (held by {}); new target={}",
                    Thread.currentThread().getName(), target);
            try {
                walkerLock.lockInterruptibly();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return WalkerState.EXIT;
            }
        }
        try {
            return walkWithBankedTransportsAndStateLocked(target, distance, forceBanking);
        } finally {
            walkerLock.unlock();
        }
    }

    private static WalkerState walkWithBankedTransportsAndStateLocked(WorldPoint target, int distance, boolean forceBanking) {
        WorldPoint pl = Rs2Player.getWorldLocation();
        if (pl == null) {
            // Transient snapshot; main walk / `processWalk` exits when not logged in — MOVING retries next beat.
            return WalkerState.MOVING;
        }
        Client rlClient = Microbot.getClient();
        WorldView wv = rlClient != null ? rlClient.getTopLevelWorldView() : null;
        LocalPoint targetLocal = wv != null ? LocalPoint.fromWorld(wv, target) : null;
        boolean nearUnwalkableGoal = targetLocal != null
                && !Rs2Tile.isWalkable(targetLocal)
                && pl.distanceTo(target) <= distance;
        if (Rs2Tile.getReachableTilesFromTile(pl, distance).containsKey(target) || nearUnwalkableGoal) {
            return WalkerState.ARRIVED;
        }
        final Pathfinder pathfinder = Rs2PathApi.getPathfinder();
        if (pathfinder != null && !pathfinder.isDone())
            return WalkerState.MOVING;

        boolean bankTripWhenCacheUnavailable = config == null || config.bankTripWhenCacheUnavailable();
        if (!forceBanking && bankTripWhenCacheUnavailable && Rs2Bank.getBankLiveEpoch() <= 0) {
            WalkerState bootstrapState = bootstrapBankMirrorForBankedPathing(distance);
            if (bootstrapState == WalkerState.EXIT || bootstrapState == WalkerState.UNREACHABLE) {
                return bootstrapState;
            }
        }
        int chebyshevToTarget = pl.distanceTo(target);
        if (!forceBanking && chebyshevToTarget <= 100) {
            WebWalkLog.bankWalkDebug("skip_compare_short_distance dist={} goal={}", chebyshevToTarget, target);
            return walkWithStateInternal(target, distance);
        }
        // Check what transport items are needed
        long compareStartedAt = System.currentTimeMillis();
        long compareFromWalkStart = walkSessionStartedAtMs > 0 ? compareStartedAt - walkSessionStartedAtMs : 0L;
        WebWalkLog.tmark("compare_start", compareFromWalkStart, target, pl, "bank_vs_direct");
        TransportRouteAnalysis comparison = compareRoutes(target);
        WebWalkLog.tmark("compare_done", System.currentTimeMillis() - compareStartedAt, target, pl,
                "direct=" + comparison.getDirectDistance() + " bank=" + comparison.getBankingRouteDistance());
        List<Transport> missingTransports = getMissingTransports(getTransportsForDestination(target, true, TransportType.TELEPORTATION_SPELL));

        Map<Integer, Integer> missingItemsWithQuantities = getMissingTransportItemIdsWithQuantities(missingTransports);
        if (!missingTransports.isEmpty()) {
            WebWalkLog.bankWalkDebug("missing_items nTrans={} to={} missingKinds={}",
                    missingTransports.size(), target, missingItemsWithQuantities.size());
        }
        // If no missing transport items, go directly
        if (missingItemsWithQuantities.isEmpty() && !forceBanking) {
            WebWalkLog.bankWalkDebug("direct_no_missing_items goal={}", target);
            WalkerState state = walkWithStateInternal(target, distance);
            if (state == WalkerState.ARRIVED) {
                WebWalkLog.bankWalkDebug("arrived goal={}", target);
            } else {
                WebWalkLog.bankWalkFailed(target, state);
                setTarget(null, "rs2walker:walkWithBankedTransports:direct-walk-failed");
                return state;

            }
            return state;
        } else {
            // Compare routes if we have missing items that could be obtained from bank
            // Use config for minimum bank route savings
            int minBankRouteSavings = config != null ? config.minBankRouteSavings() : 0;
            boolean preferTransportToTarget = config != null && config.preferTransportToTarget();
            int tileSavings = comparison.getTileSavings();
            boolean tieAndPreferBank = comparison.isTie() && preferTransportToTarget;
            boolean bankRouteIsBetter = (!comparison.isDirectIsFaster() && tileSavings >= minBankRouteSavings)
                    || (tieAndPreferBank && tileSavings >= minBankRouteSavings);
            // If forced banking or banking route is more efficient (with min savings), go via bank
            if (forceBanking || bankRouteIsBetter) {
                if (comparison.getNearestBank() != null) {
                    log.info("\n\tUsing banking route: \n\t\tStart: {} -> Bank: {} -> Target: {}",
                            Rs2Player.getWorldLocation(), comparison.getBankLocation(), target);
                    // Handle the complete banking workflow using legacy walkTo approach
                    return walkWithBankingState(comparison.getBankLocation(), missingItemsWithQuantities, target, distance);
                } else {
                    log.warn("\n\tBanking route requested but no accessible bank found, trying direct route");
                    return walkWithStateInternal(target, distance);
                }
            } else {
                log.info("\n\tDirect route is more efficient despite missing items or does not meet min savings, traveling directly");
                return walkWithStateInternal(target, distance);
            }
        }


    }

    private static WalkerState bootstrapBankMirrorForBankedPathing(int distance) {
        WorldPoint start = Rs2Player.getWorldLocation();
        if (start == null) {
            return WalkerState.MOVING;
        }
        BankLocation nearestBank = Rs2Bank.getNearestBank(start);
        if (nearestBank == null || nearestBank.getWorldPoint() == null) {
            WebWalkLog.spWarn("bank_cache_bootstrap | no_nearest_bank start={}", start);
            return WalkerState.EXIT;
        }

        WorldPoint bankLocation = nearestBank.getWorldPoint();
        WebWalkLog.spInfo("bank_cache_bootstrap | epoch={} start={} bank={}",
                Rs2Bank.getBankLiveEpoch(), start, bankLocation);

        WalkerState walkToBank = walkWithStateInternal(bankLocation, distance);
        if (walkToBank != WalkerState.ARRIVED) {
            WebWalkLog.spWarn("bank_cache_bootstrap | walk_to_bank_failed state={} bank={}",
                    walkToBank, bankLocation);
            return walkToBank;
        }

        int epochBefore = Rs2Bank.getBankLiveEpoch();
        boolean wasOpen = Rs2Bank.isOpen();
        closeWorldMap();
        if (!Rs2Bank.openBank()) {
            WebWalkLog.spWarn("bank_cache_bootstrap | open_bank_failed bank={}", bankLocation);
            return WalkerState.EXIT;
        }
        boolean mirrorReady = Rs2Bank.verifyBankMirrorAfterOpen(wasOpen, epochBefore);
        WebWalkLog.spInfo("bank_cache_bootstrap_done | ready={} epochBefore={} epochAfter={} bank={}",
                mirrorReady, epochBefore, Rs2Bank.getBankLiveEpoch(), bankLocation);

        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 3_000);
        return WalkerState.ARRIVED;
    }





    /**
     * Handles the complete banking workflow using legacy walkTo: walk to bank, open, withdraw items, close, continue to target.
     * Enhanced version that accepts a map of item IDs with their required quantities and returns boolean.
     *
     * @param bankLocation The bank location to visit
     * @param missingItemsWithQuantities Map of item IDs and their required quantities
     * @param finalTarget The final destination after banking
     * @return true if the banking workflow was successful, false otherwise
     */
    private static boolean walkWithBanking(WorldPoint bankLocation, Map<Integer, Integer> missingItemsWithQuantities, WorldPoint finalTarget) {
        return walkWithBankingState(bankLocation, missingItemsWithQuantities, finalTarget, 10)== WalkerState.ARRIVED;
    }

    /**
     * Handles the complete banking workflow using walkWithState: walk to bank, open, withdraw items, close, continue to target.
     * Enhanced version that accepts a map of item IDs with their required quantities and returns WalkerState.
     *
     * @param missingItemsWithQuantities Map of item IDs and their required quantities
     * @param finalTarget The final destination after banking
     * @return WalkerState indicating the result of the banking workflow
     */
    private static WalkerState walkWithBankingState(WorldPoint bankLocation,
                                                    Map<Integer, Integer> missingItemsWithQuantities,
                                                    WorldPoint finalTarget,int distance) {
        try {
            if (bankLocation == null || finalTarget == null) {
                log.warn("Cannot perform banking workflow with null locations");
                return WalkerState.EXIT;
            }
            // Step 1: Walk to bank
            WalkerState bankWalkResult = walkWithStateInternal(bankLocation, distance);
            if (bankWalkResult != WalkerState.ARRIVED) {
                log.warn("Failed to arrive at bank at: " + bankLocation + ", state: " + bankWalkResult);
                return bankWalkResult;
            }
            log.info("Arrived at bank location: " + bankLocation);
            // Step 2: Open bank
            closeWorldMap();
            if (!Rs2Bank.openBank()) {
                log.warn("Failed to open bank at: " + bankLocation);
                return WalkerState.EXIT;
            }
            if(!sleepUntil(()-> Rs2Bank.isOpen(), 8000)) {
                log.warn("Failed to open bank within timeout at: " + bankLocation);
                return WalkerState.EXIT;
            }

            // Step 3: Withdraw missing transport items
            if (!missingItemsWithQuantities.isEmpty()) {
                log.debug("Withdrawing transport items with quantities: " + missingItemsWithQuantities);

                // Withdraw the correct amount of each unique item
                for (Map.Entry<Integer, Integer> entry : missingItemsWithQuantities.entrySet()) {
                    int itemId = entry.getKey();
                    int amountNeeded = entry.getValue();
                    int currentCount = Rs2Inventory.count(itemId);
                    int amountToWithdraw = Math.max(0, amountNeeded );

                    if (amountToWithdraw > 0) {
                        if (Rs2Bank.hasBankItem(itemId, amountToWithdraw)) {
                            log.debug("Withdrawing {} x {} (item ID: {})", amountToWithdraw, itemId, itemId);
                            Rs2Bank.withdrawX(itemId, amountToWithdraw);
                            sleepUntil(() -> Rs2Inventory.count(itemId) >= currentCount + amountToWithdraw, 3000);
                        } else {
                            log.warn("Required transport item {} not found in bank (need {} but bank has less)",
                                    itemId, amountToWithdraw);
                        }
                    } else {
                        log.debug("Already have enough of item {}: {} (need {})", itemId, currentCount, amountNeeded);
                    }
                }

                // Wait a bit for all withdrawals to complete
                sleepTickJitter(1);
            }

            // Step 4: Close bank
            Rs2Bank.closeBank();
            sleepUntil(() -> !Rs2Bank.isOpen(), 3000);
            if (Rs2Bank.isOpen()) {
                log.warn("Failed to close bank after withdrawals");
                return WalkerState.EXIT;
            }
            Rs2PathApi.getPathfinderConfig().setUseBankItems(false);
            Rs2PathApi.getPathfinderConfig().refresh(finalTarget);
            // Step 5: Continue to final target
            log.debug("Banking complete, continuing to final target: " + finalTarget);
            return walkWithStateInternal(finalTarget, distance);

        } catch (Exception e) {
            log.error("Error in banking workflow: " + e.getMessage(), e);
            return WalkerState.EXIT;
        }
    }

    public static boolean closeWorldMap() {
        if (!Rs2Widget.isWidgetVisible(InterfaceID.Worldmap.CLOSE)) return false;
        Widget closeButton = Rs2Widget.getWidget(InterfaceID.Worldmap.CLOSE);
        if (closeButton != null) {
            Rectangle closeButtonBounds = closeButton.getBounds();
            NewMenuEntry closeEntry = new NewMenuEntry()
                    .option("Close")
                    .target("")
                    .identifier(1)
                    .type(MenuAction.CC_OP)
                    .param0(-1)
                    .param1(InterfaceID.Worldmap.CLOSE)
                    .forceLeftClick(false);

            Microbot.doInvoke(closeEntry, closeButtonBounds != null && Rs2UiHelper.isRectangleWithinCanvas(closeButtonBounds) ? closeButtonBounds : Rs2UiHelper.getDefaultRectangle());
        }
        return sleepUntil(() -> !Rs2Widget.isWidgetVisible(InterfaceID.Worldmap.CLOSE), 3000);
    }
}

