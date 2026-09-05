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
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
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
import static net.runelite.client.plugins.microbot.util.walker.Rs2WalkerMovement.*;
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
    // stuck/movement tracking state migrated to WalkerRouteState (see routeState)
    static volatile WorldPoint currentTarget;
    /** The active walk's configured finish distance — the goal-object guard needs it outside processWalk. */
    static volatile int currentWalkDistance;
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
	// interim-target state migrated to WalkerRouteState (see routeState)

	private static final long PARTIAL_TRANS_RECAL_COOLDOWN_MS = 3500L;
	/** A single delayed client tick must not turn an otherwise healthy blocking walk into EXIT. */
	private static final int CLIENT_THREAD_TIMEOUT_RETRIES = 2;

	static final int INTERIM_CLOSE_TILES = 5;

	/**
	 * Floor for the jittered per-click route reach. Deliberately above {@link #INTERIM_CLOSE_TILES}
	 * so a short click cannot land inside the interim-close threshold, which would clear the
	 * checkpoint immediately and cause click thrash.
	 */
	static final int ROUTE_CLICK_REACH_MIN_TILES = 7;
	static final int INTERIM_PRECLICK_TILES = 6;
	static final int INTERIM_RUN_PRECLICK_TILES = 8;
	private static final int INTERIM_MOVING_POLL_MS = 450;
	static final long INTERIM_PROGRESS_TIMEOUT_MS = 2500L;
	/**
	 * How much further than its closest approach the player may get from an interim checkpoint before
	 * it counts as abandoned. Wide enough to tolerate rounding a wall or a corner on the way to it.
	 */
	static final int INTERIM_ABANDON_MARGIN_TILES = 4;
	static final long INTERIM_MAX_AGE_MS = 10_000L;
	private static final long INTERIM_RETARGET_COOLDOWN_MS = 900L;
    private static final long ROUTE_PROGRESS_STALL_GRACE_MS = 4_000L;
    private static final long OFF_PATH_RECALC_RECENT_MOVEMENT_MS = 2_000L;
    private static final long OFF_PATH_RECALC_ROUTE_PROGRESS_GRACE_MS = 3_500L;
    private static final long OFF_PATH_RECALC_MINIMAP_CLICK_GRACE_MS = 2_500L;
    // Busy state (moving/animating/interacting) only defers the off-path recalc and only
    // preempts recovery clicks while the walker plausibly CAUSED it — within this window of
    // the walker's last issued click or interaction. A single minimap click moves the player
    // for at most ~8s (13 tiles, walking), so anything past 10s is external movement: combat
    // retaliation, aggro pathing, another script. Unbounded "moving" deferral let ogre combat
    // drag a player a tile per second for 27s while the walker stayed fully passive.
    private static final long WALKER_MOVEMENT_OWNERSHIP_WINDOW_MS = 10_000L;
    private static final int OFF_PATH_RECALC_DEFER_WAIT_MIN_MS = 250;
    private static final int OFF_PATH_RECALC_DEFER_WAIT_MAX_MS = 1_200;
    static final long RAW_SCAN_DOOR_FOCUS_MAX_MS = 2200L;
    static final int RAW_SCAN_DOOR_FOCUS_MAX_ATTEMPTS = 3;
    static final long DOOR_POST_INTERACT_SETTLE_MS = 900L;
    static final long DOOR_EDGE_SKIP_COOLDOWN_MS = 700L;
    /** Longest the walker will hold off re-clicking a door while an unanswered option menu is up. */
    static final long DOOR_DIALOGUE_DEFER_MAX_MS = 5_000L;
    /** Above this, a single transport object scan is worth naming in the log. */
    static final long TRANSPORT_OBJECT_SCAN_SLOW_MS = 400L;
    /** Furthest a door may be and still be opened while the player is mid-walk toward it. */
    static final int DOOR_APPROACH_INTERACT_MAX_TILES = 4;
    static final long RECOVERY_MOVEMENT_IN_FLIGHT_MS = 3_500L;
    private static final long DOOR_TRAVERSAL_RECOVERY_BLOCK_MS = 2_200L;
    static final long POST_DOOR_NUDGE_RECENT_ATTEMPT_MS = 6_000L;
    private static final int PATHFINDER_DONE_POLL_WAIT_MS = 1200;
    private static final int PATHFINDER_DONE_RETRY_SLEEP_MIN_MS = 120;
    private static final int PATHFINDER_DONE_RETRY_SLEEP_MAX_MS = 220;
    static final int POST_DOOR_FAST_CLICK_MAX_EUCLIDEAN = 13;
    static final int POST_DOOR_EDGE_NUDGE_MAX_FROM_PLAYER = 3;
    static final int POST_DOOR_EDGE_NUDGE_WAIT_MS = 1200;
    static final int HANDLER_RANGE = 13;
    // Position-staleness allowance for the far-unreachable pre-gate: the gate compares against the
    // pass-stale snapshot position, and this covers ground run since it was read.
    static final int FAR_UNREACHABLE_STALENESS_MARGIN = 10;
    // Raw/smoothed segments can span several walkable tiles before their transport edge.
    // Do not let the transport handler turn that future edge into a long movement command:
    // normal route clicks own the approach, then the handler takes over beside the origin.
    private static final int RAW_TRANSPORT_DISPATCH_MAX_DISTANCE = 2;
    static final int QUETZAL_MAP_VISIBLE_WAIT_MS = 7_000;
    static final int QUETZAL_ICON_READY_WAIT_MS = 3_000;
    static final int FINAL_ADJACENT_CANVAS_NUDGE_CHEBYSHEV = 1;
    static final int PATH_ADJ_COMPONENT_LINK_MAX_TILE_GAP = 6;
    static final int PATH_ADJ_COMPONENT_LINK_MAX_EDGE_GAP = 6;
    static final int SEGMENT_DOOR_FAMILY_MARK_RADIUS = 2;
    static final int UNREACHABLE_DOOR_RECOVERY_BACKTRACK_EDGES = 2;
    static final int UNREACHABLE_DOOR_RECOVERY_LOOKAHEAD_EDGES = 10;
    static final int STALL_RECOVERY_MINIMAP_REACH_EUCLIDEAN = 10;
    /**
     * A spatially-near smoothed waypoint can be hundreds of raw route steps ahead when a route
     * doubles back around a mountain or fence. Do not treat that future branch as the immediate
     * local blocker. The bounded reachability sample is roughly 39 tiles, so 48 preserves ordinary
     * false-negative recovery while rejecting distant route folds.
     */
    private static final int LOCAL_RECOVERY_RAW_ROUTE_LOOKAHEAD_STEPS = 48;
    static final int NORMAL_MINIMAP_REACH_EUCLIDEAN = 11;
    /**
     * Ceiling for zoom-extended minimap strides. NOT the minimap's limit — zoomed out it shows ~38
     * tiles — but the walled-click net's: every stride target must sit inside the player-origin
     * reachability BFS ({@link #CLOSEST_INDEX_REACHABLE_STEP_BUDGET} = 20 steps), or a wall between
     * could not be detected and the Clock Tower click-through-the-wall class comes back. 18 leaves
     * two steps of path-vs-Euclidean slack inside that budget.
     */
    static final int ZOOMED_OUT_MINIMAP_REACH_CAP = 18;
    /**
     * Floor for zoom-shrunk strides. The first cut of zoom awareness floored at the flat
     * {@link #NORMAL_MINIMAP_REACH_EUCLIDEAN}, which quietly broke the zoomed-IN half of the
     * feature: a fully zoomed-in minimap shows ~8 tiles of radius, so an 11-tile stride selected a
     * point on or past the rim. The floor exists only to keep the walker functional at degenerate
     * zooms, not to preserve the old reach.
     */
    static final int MIN_MINIMAP_REACH_EUCLIDEAN = 5;


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
    static final long ACTIVE_ROUTE_IDLE_NUDGE_MS = 1_200L;
	static final long ACTIVE_ROUTE_IDLE_NUDGE_COOLDOWN_MS = 2_000L;
    /**
     * How long after a door-recovery-suppressed tick the idle nudge stays disabled. Rolling — the suppress
     * branch re-stamps it every tick the door stays unresolved, so the nudge is held off for the whole
     * suppression episode plus this tail. Long enough to cover the door cooldowns that cause suppression;
     * short enough that a genuinely abandoned door (player walked away, route replanned) frees the nudge.
     */
    static final long DOOR_SUPPRESS_NUDGE_HOLDOFF_MS = 6_000L;
	private static final long POST_TRANSPORT_PATH_TMARK_WINDOW_MS = 15_000L;
	/** Floor for the post-plane-change settle sleep, so an unbounded Gaussian draw cannot go negative. */
	static final int MIN_PLANE_CHANGE_SETTLE_MS = 60;
	static final int ROUTE_PROGRESS_FORWARD_SEARCH_TILES = 40;

	/**
	 * How long to wait for an active route to be published at route start.
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
    static final int TRANSPORT_DEST_MATCH_CHEBYSHEV = 1;
    private static final int PATH_VARIANCE_TOLERANCE_CHEBYSHEV = 6;
    static final int POST_TRANSPORT_RAW_SCAN_TRANSPORT_LOOKAHEAD_EDGES = 6;
    static final int POST_TRANSPORT_RAW_SCAN_TRANSPORT_MAX_DIST = 15;
    static final long TRANSPORT_POST_INTERACT_SETTLE_MS = 900L;
    private static final long RECENT_TRANSPORT_EDGE_SUPPRESS_MS = 8_000L;
    // door-interaction state migrated to WalkerRouteState (see routeState)
    /**
     * Minimum settle after a door/transport interaction before the early exit may fire: one game tick of
     * post-action state flux (position sync, object state update). The 900ms constants above remain the
     * CEILING for when the early-exit signal never confirms — previously they were the fixed cost of every
     * single door and transport, because the transport early-exit compared against where the player stood
     * when the transport was marked handled (always true while standing at the destination) and the door
     * settle had no early exit at all.
     */
    static final long POST_INTERACT_SETTLE_MIN_MS = 300L;
    // misc route-timer state migrated to WalkerRouteState (see routeState)
    /**
     * Consolidated route state (P1 walker decomposition, enabling step). Fields are migrated here in
     * cohesive clusters; first cluster: transport handoff. See {@link WalkerRouteState}.
     */
    static final WalkerRouteState routeState = new WalkerRouteState();
    // idle-nudge state migrated to WalkerRouteState (see routeState)
    // route-progress state migrated to WalkerRouteState (see routeState)
    static final java.util.Deque<WorldPoint> expectedTransportDestinations = new ArrayDeque<>();
    private static final Set<String> startupPhasesLogged = ConcurrentHashMap.newKeySet();
    static final Set<Integer> AL_KHARID_TOLL_GATE_OBJECT_IDS = Set.of(
            net.runelite.api.ObjectID.CITY_GATE_2786,
            net.runelite.api.ObjectID.CITY_GATE_2787,
            net.runelite.api.ObjectID.CITY_GATE_2788,
            net.runelite.api.ObjectID.CITY_GATE_2789);
    static final Set<WorldPoint> AL_KHARID_TOLL_GATE_POINTS = Set.of(
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
    static final int TRANSPORT_LANDING_WAIT_POLL_MS = 100;
    static final int TRANSPORT_LANDING_WAIT_TIMEOUT_MS = 12_000;

    /** Ship / charter / glider — landing predicate uses {@link #isPlayerWithinChebyshevOf} with this exclusive bound. */
    static final int TRANSPORT_NEAR_LANDING_CHEBYSHEV = 10;

    /** Max wait after ship/NPC/boat dialogue until near destination (must match {@link #sleepUntil} timeout + warn text). */
    static final int SHIP_NPC_BOAT_LANDING_WAIT_MS = 10_000;

    /** After scene-object transport {@link #handleObject} — landing poll timeout + matching warn (cf. {@link #SHIP_NPC_BOAT_LANDING_WAIT_MS}). */
    static final int POST_HANDLE_OBJECT_LANDING_WAIT_MS = 5_000;
    static final int POST_HANDLE_OBJECT_FAILED_SETTLE_MS = 800;
    static final int AL_KHARID_TOLL_INTERACTION_START_WAIT_MS = 2_500;

    /** Teleport “already near destination” skip in path loop — same semantics as prior {@code distanceTo2D &lt; 3}. */
    static final int TELEPORT_NEAR_SKIP_CHEBYSHEV = 3;

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

    /**
     * Verbose walker traces — enable DEBUG logging for {@code net.runelite.client.plugins.microbot}.
     * Uses {@link Microbot#log(Level, String, Object...)} so levels route consistently.
     */
    static void walkerDiag(String format, Object... args) {
        Microbot.log(Level.DEBUG, "[WalkerDiag] " + format, args);
    }

    /**
     * Compact {@code x,y,p} for logs (world API coords). Similar comma coords exist in test harnesses — keep here until
     * a shared microbot util is justified.
     */
    static String compactWorldPoint(WorldPoint wp) {
        if (wp == null) {
            return "?";
        }
        return wp.getX() + "," + wp.getY() + ",p" + wp.getPlane();
    }

    private static void markWalkSessionStart(WorldPoint target) {
		testRecoveryReplanRequests.set(0);
		WalkEvidenceContext evidence = walkEvidenceContext.get();
		if (evidence != null)
		{
			evidence.started = true;
		}
        resetWalkSessionState();
        routeState.requestedGoal = target;
        WebWalkLog.tmark("walk_start", 0, target, Rs2Player.getWorldLocation(), "target_set");
    }

    /**
     * The per-walk state reset. Split out from {@link #markWalkSessionStart} because it performs no
     * game reads, so the staleness invariants below can be unit-tested instead of re-discovered live.
     *
     * <p>Every {@code processWalk} entry runs through here ({@code walkWithStateInternal} is its only
     * caller, banked walks included), which is why clearing here is sufficient and the walk-ending
     * paths do not each need their own clear.
     */
    static void resetWalkSessionState() {
        routeState.walkSessionStartedAtMs = System.currentTimeMillis();
        routeState.firstMovementClickMarked = false;
        startupPhasesLogged.clear();
        TERMINAL_TRAVEL_ATTEMPTED_EDGES.clear();
        // The transport handoff belongs to the PREVIOUS walk. Only the three location fields used to
        // be nulled here, leaving lastTransportHandledAtMs — the field every window check actually
        // reads — armed for its full 15s. A walk starting inside that window (after an interrupted,
        // errored or tail-exceeded walk, which do not clear the target) then ran degraded: raw scene
        // scan skipped, per-segment door/rockfall/transport handlers skipped, ranged door dispatch
        // disabled for the whole pass, and off-path recalc bypassed entirely. setTarget(null) already
        // cleared all four on the normal completion path; this makes the two agree.
        clearRecentTransportContext();
        lastExemptRunLocation = null;
        reachableBfsCalls.set(0);
        reachableBfsMillis.set(0L);
        // Seed rather than zero: a fresh walk has not moved yet, and an unknown tile-change time
        // credits the pose flag, which would hand a spinning player the benefit of the doubt for the
        // whole first stall window.
        routeState.lastTileChangeAtMs = System.currentTimeMillis();
        // The interim target belongs to the PREVIOUS route's click; letting it survive into a fresh walk
        // makes the new walk yield to (and report progress against) a stale objective — repeatedly seen as
        // interim=<old goal> camping at Clock Tower when the script restarts walks every ~40s.
        clearInterimTarget("walk-start");
        // Same staleness, door flavour: the latest door claim belongs to the PREVIOUS walk, and its
        // 6s window comfortably spans a script's walk-to-walk gap. A fresh walk re-nudged the old
        // door — observed as a first_door_edge_nudge pointing BACKWARD at walk start, ~2s of standing
        // still (or worse, a step the wrong way) before the new route's first click. Per-edge
        // cooldowns survive on purpose: hammering one door across two walks is still hammering.
        doorAttemptLedger.clearLatestAttempt();
        routeState.walledDoorEdgeFrom = null;
        routeState.walledDoorEdgeTo = null;
        routeState.walledDoorEdgeAtMs = 0L;
        routeState.requestedGoal = null;
        routeState.sealedRimRetargets = 0;
        resetRouteProgress();
        synchronized (expectedTransportDestinations) {
            expectedTransportDestinations.clear();
        }
    }

    /** Same package (e.g. unit tests) only — not part of the script API. */
    static WalkerRouteState routeStateForTesting() {
        return routeState;
    }


    private static void clearRecentTransportContext() {
        routeState.clearRecentTransportContext();
    }


    private static void markStartupPhase(String phase, WorldPoint target, String detail) {
        if (routeState.firstMovementClickMarked || !startupPhasesLogged.add(phase)) {
            return;
        }
        long startedAt = routeState.walkSessionStartedAtMs;
        if (startedAt <= 0) {
            return;
        }
        WebWalkLog.tmark(phase, System.currentTimeMillis() - startedAt, target, Rs2Player.getWorldLocation(), detail);
    }

    private static void tmarkPostTransport(String phase, WorldPoint target, String detail) {
        long handledAt = routeState.lastTransportHandledAtMs;
        if (handledAt <= 0L) {
            return;
        }
        long elapsed = System.currentTimeMillis() - handledAt;
        if (elapsed < 0L || elapsed > POST_TRANSPORT_PATH_TMARK_WINDOW_MS) {
            return;
        }
        // These exist to explain a post-transport pass, not to report an outcome, and they fire several
        // times per pass for the whole 15s window. At INFO they were the bulk of the walker's console
        // output; the verbose toggle brings them back when someone is actually reading them.
        WebWalkLog.tmarkDebug(phase, elapsed, target, Rs2Player.getWorldLocation(), detail);
    }

    private enum WalkerPhase {
        STARTUP,
        STEADY
    }

    /**
     * One consistent view of the world per loop pass (B2). Captured at the top of the pass and
     * RE-CAPTURED after any branch that blocks (a click-and-sleep, a handler wait) — a pass-start
     * position is a lie after a second of sleeping, which is the same staleness class the
     * reachable-recapture above the recovery scan exists for. Consumers between blocking points
     * share the snapshot instead of re-reading the client, so they cannot disagree about where the
     * player is — the disagreement that produced the Stronghold gate bounce.
     */
    private static final class WalkLoopSnapshot {
        private final WorldPoint playerLoc;
        private final boolean moving;
        private final boolean animating;
        private final boolean interacting;
        // Lazy: capture() is cheap enough to run once per SEGMENT iteration; the reachability BFS
        // only runs if a consumer actually asks for the closest index (once per snapshot).
        private HashMap<WorldPoint, Integer> closestReachableTiles;

        private WalkLoopSnapshot(WorldPoint playerLoc, boolean moving, boolean animating, boolean interacting) {
            this.playerLoc = playerLoc;
            this.moving = moving;
            this.animating = animating;
            this.interacting = interacting;
        }

        private static WalkLoopSnapshot capture() {
            return new WalkLoopSnapshot(Rs2Player.getWorldLocation(),
                    Rs2Player.isMoving(), Rs2Player.isAnimating(), Rs2Player.isInteracting());
        }

        private boolean idle() {
            return !moving && !animating && !interacting;
        }

        private int closestTileIndex(List<WorldPoint> path) {
            if (closestReachableTiles == null) {
                closestReachableTiles = getClosestIndexReachableTiles(playerLoc);
            }
            return WalkerPathGeometry.getClosestTileIndex(path, playerLoc, closestReachableTiles);
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
        if (routeState.firstMovementClickMarked) {
            return WalkerPhase.STEADY;
        }
        // Taking a transport IS the walker acting. The startup phase holds the broad handlers back
        // until the walker has committed to the route, but it only ever advanced on a MOVEMENT CLICK
        // — so a walk that begins standing on a staircase origin never clicks, never leaves STARTUP,
        // and keeps the broad handlers suppressed through every FURTHER transport. Measured: at
        // (2958,3337,p2), two tiles from the next staircase origin, the raw scan reported
        // why=policy-startup three passes running until the idle nudge walked onto the origin.
        // Scoped to this walk session, since lastTransportHandledAtMs deliberately outlives it.
        long sessionStartedAtMs = routeState.walkSessionStartedAtMs;
        if (sessionStartedAtMs > 0L && routeState.lastTransportHandledAtMs >= sessionStartedAtMs) {
            return WalkerPhase.STEADY;
        }
        return WalkerPhase.STARTUP;
    }

    private static boolean isClientThread() {
        Client client = Microbot.getClient();
        return client != null && client.isClientThread();
    }

    static int reachedDistanceOrDefault() {
        return config != null ? config.reachedDistance() : 10;
    }

    private static ObstaclePolicy obstaclePolicyForCurrentPhase() {
        return currentWalkerPhase() == WalkerPhase.STARTUP
                ? STARTUP_OBSTACLE_POLICY
                : STEADY_OBSTACLE_POLICY;
    }




    /**
     * Caps configured finish distance when the route already ends very close to the marked goal.
     * Without this, a large "Finish distance" (e.g. 5) allows {@link WalkerState#ARRIVED} on the
     * wrong side of a wall/door for small interiors. When {@code dLast &lt; TIGHT_PATH_GOAL_GAP}, cap is {@code 1};
     * when {@code dLast == TIGHT_PATH_GOAL_GAP}, cap is {@code 2} (outdoor micro-walking relief at the gap radius).
     */
    static int tightFinishThreshold(WorldPoint goal, WorldPoint pathLastWalkable, int configuredChebyshev) {
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
    static final int DOOR_OPEN_CANVAS_NUDGE_MAX_GOAL_DIST = 18;
    static final int DOOR_OPEN_CANVAS_NUDGE_GOAL_SAMPLE_RADIUS = 3;
    static final int DOOR_OPEN_CANVAS_NUDGE_MAX_FROM_PLAYER = 15;

    /**
     * After a successful door canvas nudge, {@link #tryDirectShortWalk} is skipped briefly so the next
     * movement beat is not minimap (same-frame minimap after scene click looks robotic).
     */
    // routeState.suppressTryDirectShortWalkUntilMs migrated to WalkerRouteState (see routeState)
    static final long POST_DOOR_NUDGE_SUPPRESS_TRY_DIRECT_MS = 2200L;
    /**
     * Hold-off when the door opened but no canvas nudge was issued (the mid-route case). Long enough that
     * the next beat is not on the door interaction's own tick, short enough that the walk does not stall
     * waiting for a scene click that was never made.
     */
    private static final long POST_DOOR_NO_NUDGE_SUPPRESS_TRY_DIRECT_MS = 500L;

    /** Max wait after scene canvas / recovery clicks until movement stops (avoids minimap churn while in-flight). */
    static final int POST_SCENE_WALK_IDLE_WAIT_MS_MAX = 10_000;

    /** If phase 1 exits on arrival distance while still moving, wait briefly for idle-only (reduces tail churn). */
    static final int POST_SCENE_WALK_IDLE_SECOND_PHASE_MS_MAX = 4_000;
    private static final int POST_RECOVERY_MOVEMENT_START_WAIT_MS = 1_200;



    /**
     * Whether any tile within {@code distance} of {@code target} is walkable in the collision map.
     *
     * <p>Pre-flight guard so a destination that does not exist as walkable terrain fails
     * immediately instead of after a full route. A walk to {@code (3087,9720)} — inside the rock
     * east of the Dwarven Mine, with 0 walkable tiles within 6 — spent ~80s covering 100+ tiles to
     * the nearest reachable tile and then reported {@code partial-retries-exhausted}, which reads
     * as a walker fault rather than a bad coordinate.
     *
     * <p><b>Permissive by design.</b> An unmapped collision region reads as fully blocked, so every
     * ambiguous case returns {@code true} and lets the
     * pathfinder decide. Only a target sitting in mapped, wholly blocked terrain is rejected —
     * otherwise this would refuse instances and any region missing from the collision map.
     */
    /**
     * Whether the pathfinder's collision map considers this tile standable.
     *
     * <p>Anyone <em>choosing</em> a destination should filter candidates through this. {@link #walkTo}
     * pre-flights the target and rejects it outright when nothing walkable lies within the arrival
     * distance, so picking an unwalkable tile fails the entire walk rather than degrading to something
     * close by. The scene's own notion of walkability and this map do not always agree — the Corsair
     * Cove staircase approach at (2531,2834) reads walkable in the scene and blocked here.
     */
    public static boolean isWalkableInCollisionMap(WorldPoint tile) {
        return Rs2PathApi.hasWalkableTileWithin(tile, 0);
    }


    /** A single scene-transition sample must not kill a healthy, moving route. */
    private static boolean isStableLoggedOut() {
        boolean initiallyLoggedIn = Microbot.isLoggedIn();
        if (initiallyLoggedIn) {
            return false;
        }
        Rs2WalkerRuntimeAwaits.awaitCondition(
                () -> Microbot.isLoggedIn() || currentTarget == null || Thread.currentThread().isInterrupted(),
                100, 750);
        boolean cancelled = currentTarget == null || Thread.currentThread().isInterrupted();
        return LoginStabilityPolicy.shouldExit(false, Microbot.isLoggedIn(), cancelled);
    }

    private static void traceProcessWalkExit(String reason, WorldPoint target, int processWalkTail) {
        WorldPoint activeTarget = currentTarget;
        WebWalkLog.exitDetailDebug(
                "trace={} target={} currentTarget={} interim={} stuck={} tailIdx={}/{} intr={} player={}",
                reason,
                target,
                activeTarget,
                routeState.interimTargetWp,
                routeState.stuckCount,
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

    // lastRouteClearReason / lastRouteClearAtMs migrated to WalkerRouteState; the @Getter-generated
    // accessors are preserved explicitly here because ShortestPathScript reads them as public API.
    public static String getLastRouteClearReason() {
        return routeState.lastRouteClearReason;
    }

    public static long getLastRouteClearAtMs() {
        return routeState.lastRouteClearAtMs;
    }


    /** Substrings for game-object names treated like doors (pathing heuristics). */

    /** Max age for {@link Rs2LeaguesTransport#isLeaguesAreaTeleportPending(long)} in stall / stuck gates. */
    static final long LEAGUES_AREA_PENDING_STALL_MAX_AGE_MS = 60_000L;

    @Named("disableWalkerUpdate")
    static boolean disableWalkerUpdate;

    public static boolean disableTeleports = false;

    // Serializes stateful walker entry points so concurrent scripts don't corrupt
    // routeState.stuckCount / routeState.lastPosition / routeState.lastMovedTimeMs / currentTarget / nextWalkingDistance.
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
	private static final ThreadLocal<WalkEvidenceContext> walkEvidenceContext = new ThreadLocal<>();
	private static final AtomicInteger testRecoveryReplanRequests = new AtomicInteger();

	private static final class WalkEvidenceContext
	{
		private boolean started;
		private boolean comparisonEligible;
		private boolean recoveryTriggered;
	}

	private static void captureActiveRouteComparisonEligibility(long routeGeneration)
	{
		WalkEvidenceContext evidence = walkEvidenceContext.get();
		if (evidence != null && !evidence.comparisonEligible
			&& Rs2PathApi.isActiveRouteComparisonEligible(routeGeneration))
		{
			evidence.comparisonEligible = true;
		}
	}

	private static WalkerState withShadowExecutionEvidence(Supplier<WalkerState> action)
	{
		WalkEvidenceContext existing = walkEvidenceContext.get();
		if (existing != null)
		{
			return action.get();
		}
		WalkEvidenceContext evidence = new WalkEvidenceContext();
		walkEvidenceContext.set(evidence);
		try
		{
			WalkerState result = Objects.requireNonNull(action.get(), "walker result");
			if (evidence.started)
			{
				Rs2PathApi.recordShadowWalkerOutcome(
					result, evidence.recoveryTriggered, evidence.comparisonEligible);
			}
			return result;
		}
		finally
		{
			testRecoveryReplanRequests.set(0);
			walkEvidenceContext.remove();
		}
	}

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
    static final Set<String> SEASONAL_HANDLER_MISS_LOGGED = ConcurrentHashMap.newKeySet();
    static final AtomicInteger SEASONAL_HANDLER_MISS_LOGGED_COUNT = new AtomicInteger(0);
    static final int SEASONAL_HANDLER_MISS_LOG_CAP = 128;
    /** Terminal NPC edges already clicked during the current top-level walk invocation. */
    static final Set<String> TERMINAL_TRAVEL_ATTEMPTED_EDGES = ConcurrentHashMap.newKeySet();
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
        testRecoveryReplanRequests.set(0);
        recentCurrentTileTransportByEdge.clear();
        TERMINAL_TRAVEL_ATTEMPTED_EDGES.clear();
        clearRecentTransportContext();
        resetRouteProgress();
    }

    static volatile List<SeasonalTransportHandler> seasonalTransportHandlers =
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

        public static void incrementSeasonalHandlerMiss() {
            seasonalHandlerMissCount.incrementAndGet();
        }

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

        static final ConcurrentHashMap<String, AtomicInteger> doorRejectByCause = new ConcurrentHashMap<>();
        static final AtomicInteger doorRejectSummaryLogSeq = new AtomicInteger(0);
        static final int DOOR_REJECT_SUMMARY_LOG_INTERVAL = 40;
        private static final ConcurrentHashMap<String, AtomicInteger> offPathDeferredByReason = new ConcurrentHashMap<>();
        private static final AtomicInteger offPathDeferredSummaryLogSeq = new AtomicInteger(0);
        private static final int OFF_PATH_DEFERRED_SUMMARY_LOG_INTERVAL = 20;


        public static void incrementLeaguesLockAttributed() {
            leaguesLockAttributedCount.incrementAndGet();
        }

        public static void incrementLeaguesLockStale() {
            leaguesLockStaleCount.incrementAndGet();
        }

        public static void incrementLeaguesLockParseMiss() {
            leaguesLockParseMissCount.incrementAndGet();
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
                                             Rs2RouteMetrics routeMetrics) {
            unreachableCount.incrementAndGet();
            lastReason = "unreachable:" + cause;
            lastEventAtMs.set(System.currentTimeMillis());
            int distToTarget = (pathEndpoint != null && target != null) ? pathEndpoint.distanceTo(target) : -1;
            String stats = routeMetrics == null ? "null" : String.format(
                    "RouteMetrics(nodes=%d,transports=%d,time=%dms,cost=%d)",
                    routeMetrics.getNodesChecked(),
                    routeMetrics.getTransportsChecked(),
                    routeMetrics.hasSearchNanos() ? routeMetrics.getSearchNanos() / 1_000_000L : -1L,
                    routeMetrics.getPathCost());
            log.warn("[WalkerTelemetry] UNREACHABLE cause={} player={} target={} pathEndpoint={} pathSize={} endpointToTarget={} threshold={} routeMetrics={} totalUnreachable={}",
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
    static final Map<Integer, Integer> OPEN_TO_CLOSED_MAPPINGS = Map.of(
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
			return withShadowExecutionEvidence(() -> config.walkWithBankedTransports()
					? walkWithBankedTransportsAndStateLocked(target, distance, false)
					: walkWithStateInternal(target, distance));
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
			return withShadowExecutionEvidence(() -> config.walkWithBankedTransports()
					? walkWithBankedTransportsAndStateLocked(target, distance, false)
					: walkWithStateInternal(target, distance));
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
        currentWalkDistance = Math.max(0, distance);
        if (isClientThread()) {
            log.warn("Please do not call the walker from the main thread");
            return WalkerState.EXIT;
        }
        // BEFORE any planning. The first version withdrew these inside markWalkSessionStart, which
        // runs after setTarget has already kicked the pathfinder off — measured live at the Tithe
        // door: the retry's plan ran against the previous walk's blocks (SEARCH_EXHAUSTED against a
        // sealed goal), collapsed to a 1-tile path, and the retry burned itself on it while the
        // unlearn arrived two lines later.
        withdrawWalkScopedDoorBlocks();
        WorldPoint playerLocWalk = Rs2Player.getWorldLocation();
        if (playerLocWalk == null) {
            return WalkerState.MOVING;
        }
        int distToTarget = playerLocWalk.distanceTo(target);
        LocalPoint localTarget = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), target);
        boolean walkableCheck = Rs2Tile.isWalkable(localTarget);
        Map<WorldPoint, Integer> reachableWithinDistance = distToTarget <= distance
                ? Rs2Tile.getReachableTilesFromTile(playerLocWalk, distance)
                : Collections.emptyMap();
        boolean reachableTileCheck = distToTarget <= distance && reachableWithinDistance.containsKey(target);

        // An unwalkable target is normal — you cannot stand ON a door, chest or bank booth, so the
        // walk has to finish beside it. But distanceTo is straight-line and knows nothing about walls,
        // so "within distance of an object" was reported as ARRIVED even with a wall between: the
        // caller then tried to interact from the wrong side of it and the script failed with the
        // walker claiming success. Require somewhere we can actually STAND next to the target.
        //
        // Falls back to the old distance-only answer when the BFS is unavailable, so a reachability
        // hiccup cannot turn arrival into a walk that never terminates.
        boolean unwalkableTargetReached = !walkableCheck && distToTarget <= distance
                && (reachableWithinDistance.isEmpty()
                || hasReachableNeighbour(target, reachableWithinDistance));

        if (reachableTileCheck || unwalkableTargetReached) {
            return WalkerState.ARRIVED;
        }
        if (!walkableCheck && distToTarget <= distance && !reachableWithinDistance.isEmpty()) {
            WebWalkLog.spInfo("arrival_declined_unreachable | target={} player={} dist={} — within distance "
                            + "but no reachable tile beside it; continuing",
                    compactWorldPoint(target), compactWorldPoint(playerLocWalk), distToTarget);
        }

        final Rs2ActiveRouteStatus routeStatus = Rs2PathApi.getActiveRouteStatus();
        if (routeStatus.isCalculating()) {
            return WalkerState.MOVING;
        }
        boolean hasCurrentPath = routeStatus.isReady()
                && routeStatus.getTargets().contains(target);
        if (!hasCurrentPath) {
            setTarget(target);
        } else {
            currentTarget = target;
        }
        Rs2PathApi.setReachedDistance(distance);
        routeState.stuckCount = 0;
        routeState.lastMovedTimeMs = System.currentTimeMillis();
		routeState.interimTargetWp = null;
		routeState.interimTargetIdx = -1;
		routeState.interimSetAtMs = 0L;
        routeState.interimLastProgressAtMs = 0L;
        routeState.interimLastBestPathIdx = -1;
        routeState.interimLastDistanceToTarget = Integer.MAX_VALUE;
        routeState.interimLastRetargetAtMs = 0L;
        routeState.lastPartialTransRecalcMs = 0L;
        routeState.idleNudgeLastObservedLocation = playerLocWalk;
        routeState.idleNudgeStationarySinceMs = System.currentTimeMillis();
        routeState.lastActiveRouteIdleNudgeAtMs = 0L;

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
     * Non-blocking single-step walk. Unlike {@link #walkWithState(WorldPoint, int)} — which owns the
     * loop and blocks (via {@link #processWalk}) until it arrives or gives up — this advances the walk by
     * at most one action and returns immediately. The caller owns the loop: call it every tick and it
     * paths toward {@code target}, one minimap click at a time, so between calls the caller can re-check
     * its own condition (e.g. "is the NPC in range yet?") and act the instant it is.
     *
     * <p>Return values: {@link WalkerState#ARRIVED} when within {@code distance} of {@code target};
     * {@link WalkerState#MOVING} while still approaching (path computing, in transit, or a click issued);
     * {@link WalkerState#UNREACHABLE} when no walkable path reaches within {@code distance};
     * {@link WalkerState#EXIT} on a bad call (null target / client thread / no config).
     *
     * <p>Scope: plain approach-walking. It reuses the shared pathfinder + minimap machinery but NOT the
     * full {@link #processWalk} transport/door/stuck-recovery pipeline, so a route that needs a transport
     * or a door won't be driven here — use the blocking {@link #walkTo} for those. Clear the goal with
     * {@link #setTarget(WorldPoint, String) setTarget(null, reason)} when you stop (e.g. once you interact).
     */
    public static WalkerState walkStep(WorldPoint target, int distance) {
        if (config == null) {
            return WalkerState.EXIT;
        }
        if (target == null) {
            log.warn("[Walker] walkStep rejected: null target");
            return WalkerState.EXIT;
        }
        if (isClientThread()) {
            log.warn("Please do not call the walker from the main thread");
            return WalkerState.EXIT;
        }
        // Caller-driven: one click per call, never enters processWalk's loop, so isWalkCancelled
        // never runs for it.
        if (InputArbiter.isHuman()) {
            return WalkerState.EXIT;
        }

        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        if (playerLoc == null) {
            return WalkerState.MOVING;
        }

        // Arrived? (mirrors walkWithStateInternal's arrival test)
        int distToTarget = playerLoc.distanceTo(target);
        LocalPoint localTarget = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), target);
        boolean walkableCheck = localTarget != null && Rs2Tile.isWalkable(localTarget);
        boolean reachableTileCheck = distToTarget <= distance
                && Rs2Tile.getReachableTilesFromTile(playerLoc, distance).containsKey(target);
        if (reachableTileCheck || (!walkableCheck && distToTarget <= distance)) {
            return WalkerState.ARRIVED;
        }

        // (Re)start pathfinding ONLY when the caller's goal changes. Do NOT re-target while the path is
        // still computing — setTarget() restarts pathfinding, so re-calling it every tick would reset the
        // pathfinder forever (path drawn, but never finished, so we never click).
        if (currentTarget == null || !currentTarget.equals(target)) {
            setTarget(target);
            return WalkerState.MOVING;
        }
        Rs2ActiveRouteStatus routeStatus = Rs2PathApi.getActiveRouteStatus();
        if (!routeStatus.isReady()) {
            return WalkerState.MOVING; // path still computing — wait, don't reset it
        }

        // Already in transit toward the last click — let it resolve instead of spamming clicks.
        if (Rs2Player.isMoving()) {
            return WalkerState.MOVING;
        }

        final List<WorldPoint> rawPath = routeStatus.getRawPath();
        final List<WorldPoint> path = routeStatus.getWalkablePath();
        if (!walkStepPathReachesTarget(path, target, distance)) {
            setTarget(null, "rs2walker:walkStep:no-walkable-path");
            return WalkerState.UNREACHABLE;
        }

        // One minimap click toward the target (falls back to the furthest visible path point off-clip).
        // Click the target on the minimap, else fall back to the furthest visible planned-path point.
        // The geometric directional fallback (a straight-line b-line toward the target) is gated to NEAR
        // targets only: for a far target it's never right and produces off-route drift. When neither the
        // target nor a planned-path point is clickable (e.g. the route needs a transport walkStep can't
        // cross), no click is issued and we hold on the line rather than wander off it — walkStep is not
        // built for transport routes; use the blocking walkTo/walkUntil for those.
        int walkStepReach = normalMinimapReach();
        boolean allowDirectionalFallback = playerLoc.distanceTo(target) <= walkStepReach;
        clickMiniMapOrFallback(rawPath, target, playerLoc, walkStepReach - 1, allowDirectionalFallback, -1);
        return WalkerState.MOVING;
    }

    /** Wall-clock ms of the last walker heartbeat line. */
    private static volatile long lastHeartbeatAtMs = 0L;
    /** Minimum gap between heartbeat lines — one per second is enough to size a stall. */
    private static final long WALKER_HEARTBEAT_INTERVAL_MS = 1_000L;

    /**
     * One throttled line per processWalk pass, so a silent stretch in the log can be told apart from a
     * stalled one.
     * <p>
     * Two ten-second freezes before the same ladder were diagnosed twice from the log alone and both
     * diagnoses were wrong — the scene-walk idle wait (its instrumentation never fired) and the interim
     * target (it clears at 5 tiles and the player was 2 away). The reason both were guesses is that
     * nothing logs in that window at all: the post_transport_* tmarks stop 15s after a transport, and
     * everything else in the walk is event-driven. A heartbeat settles the question outright — if these
     * lines appear across the gap the loop is spinning without acting and the state here says why, and
     * if they stop the thread is blocked inside a wait and the last line says which pass entered it.
     */
    /**
     * How long a walk may go WITHOUT CLOSING DISTANCE on its goal before it is reported as a
     * probable livelock.
     *
     * <p>Originally measured from walk start, which made it a slow-journey detector: the 18:23 run
     * (two quetzal flights, two shortcuts, 314s, arrived cleanly) warned at the 300s mark while
     * cruising. The clock now restarts every time the player gets {@link #WALK_BUDGET_PROGRESS_TILES}
     * closer to the goal than ever before this walk, so only a walk that has genuinely stopped
     * converging trips it. Legs that move AWAY first (a flight via a distant hub) do not restart the
     * clock, so they must complete within the budget — at 300s that holds comfortably. Currently
     * OBSERVE-ONLY — it logs and does not abort — because a budget that kills a working walk would
     * be a worse bug than the livelock it guards against.
     */
    private static final long WALK_WALL_CLOCK_BUDGET_MS = 300_000L;
    /** Distance improvement (tiles, 2D, vs the walk's best so far) that counts as real progress. */
    private static final int WALK_BUDGET_PROGRESS_TILES = 10;
    /** Uninterrupted tail-exempt iterations before the loop is reported as yielding without advancing. */
    private static final int MAX_CONSECUTIVE_EXEMPT_ITERATIONS = 24;
    /** One budget report per walk session; 0 when this session has not reported yet. */
    private static volatile long walkBudgetReportedForSessionAtMs = 0L;
    /** Walk session the progress tracker below belongs to. */
    private static long walkBudgetTrackedSessionMs = 0L;
    /** Closest (2D) the player has been to this walk's goal, and when that record was last beaten. */
    private static int walkBudgetBestDistToGoal = Integer.MAX_VALUE;
    private static long walkBudgetLastProgressAtMs = 0L;

    /**
     * Reports a walk that has outlived its no-progress budget.
     *
     * <p>{@code MAX_PROCESS_WALK_TAIL_ITERATIONS} is not a bound on its own: several exit reasons
     * decrement the tail counter, so a walk that keeps producing one of them loops forever, and
     * nothing else in the call chain imposes a time limit. This makes that state visible in the log
     * instead of silent.
     */
    private static void reportWalkBudgetIfExhausted(WorldPoint target, long nowMs, int processWalkTail) {
        long startedAt = routeState.walkSessionStartedAtMs;
        if (walkBudgetTrackedSessionMs != startedAt) {
            walkBudgetTrackedSessionMs = startedAt;
            walkBudgetBestDistToGoal = Integer.MAX_VALUE;
            walkBudgetLastProgressAtMs = startedAt;
        }
        WorldPoint at = Rs2Player.getWorldLocation();
        if (at != null && target != null) {
            int dist = at.distanceTo2D(target);
            if (dist <= walkBudgetBestDistToGoal - WALK_BUDGET_PROGRESS_TILES
                    || walkBudgetBestDistToGoal == Integer.MAX_VALUE) {
                walkBudgetBestDistToGoal = Math.min(dist, walkBudgetBestDistToGoal);
                walkBudgetLastProgressAtMs = nowMs;
            }
        }
        if (!TailDecision.isWallClockExhausted(walkBudgetLastProgressAtMs, nowMs, WALK_WALL_CLOCK_BUDGET_MS)
                || walkBudgetReportedForSessionAtMs == startedAt) {
            return;
        }
        walkBudgetReportedForSessionAtMs = startedAt;
        log.warn("[Walker] walk exceeded its {}ms no-progress budget (no distance gain for {}ms,"
                        + " running {}ms total) target={} at={} tail={} — probable livelock; the tail"
                        + " cap cannot catch this because exempt exits refund it",
                WALK_WALL_CLOCK_BUDGET_MS, nowMs - walkBudgetLastProgressAtMs, nowMs - startedAt,
                target, Rs2Player.getWorldLocation(), processWalkTail);
    }

    /** How long the route progress index may hold still before the route is declared stagnant. */
    private static final long ROUTE_STAGNATION_BUDGET_MS = 60_000L;
    /** Stagnation replans per walk before the goal is called unreachable. */
    private static final int MAX_ROUTE_STAGNATION_REPLANS = 2;

    /**
     * The enforced oscillation bound (TailDecision.decideRouteStagnation). Unlike the two observe-only
     * budgets above, this one acts: the wall-clock budget is sized for whole journeys and the
     * exempt-run counter resets on any movement, so a walk ping-ponging between two tiles — the Tithe
     * Farm door/recovery oscillation ran 4+ minutes until a human cancelled it — trips neither.
     * Returns null to continue the loop (spending a replan restarts the clock), or the honest
     * terminal state.
     */
    private static WalkerState handleRouteStagnation(WorldPoint target, int distance, List<WorldPoint> path) {
        long now = System.currentTimeMillis();
        TailDecision.StagnationAction action = TailDecision.decideRouteStagnation(
                routeState.routeProgressAdvancedAtMs, now, ROUTE_STAGNATION_BUDGET_MS,
                routeState.stagnationReplansSpent, MAX_ROUTE_STAGNATION_REPLANS);
        if (action == TailDecision.StagnationAction.NONE) {
            return null;
        }
        if (action == TailDecision.StagnationAction.REPLAN) {
            routeState.stagnationReplansSpent++;
            // Restart the clock by hand: a replan that returns the identical route never trips the
            // route-changed re-stamp, and each replan is owed a full budget of its own.
            routeState.routeProgressAdvancedAtMs = now;
            WebWalkLog.spInfo("route_stagnation_replan | spent={}/{} idx={} at={} goal={}",
                    routeState.stagnationReplansSpent, MAX_ROUTE_STAGNATION_REPLANS,
                    routeState.routeProgressIdx, compactWorldPoint(Rs2Player.getWorldLocation()),
                    compactWorldPoint(target));
            recalculatePath();
            return null;
        }
        WorldPoint endpoint = path == null || path.isEmpty() ? null : path.get(path.size() - 1);
        WebWalkLog.spInfo("route_stagnation_exhausted | idx={} replans={} at={} goal={} — route index "
                        + "never advanced; movement without progress is not progress",
                routeState.routeProgressIdx, routeState.stagnationReplansSpent,
                compactWorldPoint(Rs2Player.getWorldLocation()), compactWorldPoint(target));
        Telemetry.recordUnreachable("route-stagnation-exhausted", Rs2Player.getWorldLocation(),
                target, endpoint, path == null ? 0 : path.size(), distance,
                Rs2PathApi.getActiveRouteStatus().getMetrics().orElse(null));
        setTarget(null, "rs2walker:processWalk:route-stagnation-exhausted");
        return WalkerState.UNREACHABLE;
    }

    /** Player tile at the last tail-exempt iteration; a change means the run was making progress. */
    private static volatile WorldPoint lastExemptRunLocation = null;

    /**
     * Counts consecutive tail-exempt iterations THAT DID NOT MOVE THE PLAYER.
     *
     * <p>Counting every exempt iteration was wrong, and a real farm-run log proved it: a completely
     * healthy Catherby-to-Ardougne walk yielded {@code interim-in-flight} 28 times in a row while
     * steadily covering ground, because that is simply what travelling between minimap clicks looks
     * like. A bound on yields is a bound on walking; the state actually worth reporting is yielding
     * while STATIONARY, which no number of tail refunds can ever surface through the iteration cap.
     */
    private static int trackExemptRun(int run, WorldPoint target, WalkExit exit, String detail) {
        WorldPoint at = Rs2Player.getWorldLocation();
        int next = (at != null && !at.equals(lastExemptRunLocation)) ? 1 : run + 1;
        lastExemptRunLocation = at;
        if (TailDecision.isExemptRunTooLong(next, MAX_CONSECUTIVE_EXEMPT_ITERATIONS)) {
            reportExemptRunTooLong(target, exit.wireName(detail), next);
        }
        return next;
    }

    /**
     * Reports a loop that keeps yielding without advancing. Every one of these iterations refunds
     * its own tail charge, so no number of them can trip the iteration cap.
     */
    private static void reportExemptRunTooLong(WorldPoint target, String exitWireName, int run) {
        if (run % MAX_CONSECUTIVE_EXEMPT_ITERATIONS != 1) {
            return;
        }
        log.warn("[Walker] {} consecutive tail-exempt iterations (exit={}) target={} at={} —"
                        + " the loop is yielding without advancing and cannot exhaust the tail cap",
                run, exitWireName, target, Rs2Player.getWorldLocation());
    }

    // Pass-anatomy tracking (task #25 slice 2): walkerHeartbeat is called at every pass start, so
    // the gap since the previous call IS the previous pass's duration. Stage timers accumulate
    // inside the handler bodies (WalkPassStats); the residual names what they do not explain.
    private static long lastPassStartAtMs;
    private static int lastPassTail = -1;
    private static final long SLOW_PASS_EMIT_MS = 2_000;

    private static void walkerHeartbeat(WorldPoint target, int processWalkTail) {
        long now = System.currentTimeMillis();
        if (processWalkTail > 0 && lastPassStartAtMs > 0) {
            long prevPassMs = now - lastPassStartAtMs;
            if (prevPassMs >= SLOW_PASS_EMIT_MS) {
                WebWalkLog.spInfo("pass_slow | tail={} prevPassMs={} {}",
                        lastPassTail, prevPassMs, WalkPassStats.snapshot(prevPassMs));
            }
        }
        lastPassStartAtMs = now;
        lastPassTail = processWalkTail;
        WalkPassStats.reset();
        reportWalkBudgetIfExhausted(target, now, processWalkTail);
        if (now - lastHeartbeatAtMs < WALKER_HEARTBEAT_INTERVAL_MS) {
            return;
        }
        lastHeartbeatAtMs = now;
        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        // DEBUG, not INFO: this fires every second for the whole of every walk, and it exists to
        // diagnose stalls, not to narrate healthy ones. Behind the verbose toggle it costs nothing
        // until someone is actually chasing a silent stretch in the log.
        WebWalkLog.spDebug("walker_heartbeat | tail={} at={} goal={} moving={} animating={} interim={} interimAgeMs={} sinceMovedMs={} sinceDoorSettleMs={} bfs={}/{}ms",
                processWalkTail,
                compactWorldPoint(playerLoc), compactWorldPoint(target),
                Rs2Player.isMoving(), Rs2Player.isAnimating(),
                compactWorldPoint(routeState.interimTargetWp),
                routeState.interimSetAtMs > 0L ? now - routeState.interimSetAtMs : -1L,
                routeState.lastMovedTimeMs > 0L ? now - routeState.lastMovedTimeMs : -1L,
                doorAttemptLedger.settleStartedAtMs() > 0L
                        ? now - doorAttemptLedger.settleStartedAtMs() : -1L,
                reachableBfsCalls.get(), reachableBfsMillis.get());
    }

    /**
     * A completed pathfinder result is usable by {@link #walkStep(WorldPoint, int)} only when its
     * endpoint reaches the requested arrival radius. A multi-tile partial path is still terminal for
     * this non-blocking API: unlike {@link #processWalk}, walkStep has no partial-path retry loop, so
     * accepting it would repeatedly click the same endpoint and report {@link WalkerState#MOVING}
     * forever.
     */
    static boolean walkStepPathReachesTarget(List<WorldPoint> path, WorldPoint target, int distance) {
        if (path == null || path.isEmpty() || target == null) {
            return false;
        }
        WorldPoint endpoint = path.get(path.size() - 1);
        return endpoint != null && endpoint.distanceTo(target) <= Math.max(0, distance);
    }

    /**
     * Core walk method contains all the logic to successfully walk to the destination
     * this contains doors, game objects, teleports, spells etc...
     *
     * @param target
     * @param distance
     */
    /**
     * Whether any tile orthogonally or diagonally adjacent to {@code target} is in the player-origin
     * reachable set — i.e. there is somewhere we can actually stand to interact with it.
     * <p>
     * This is the difference between "close to the object" and "able to use the object". Straight-line
     * distance says yes through a wall; this says no.
     */
    static boolean hasReachableNeighbour(WorldPoint target, Map<WorldPoint, Integer> reachable) {
        if (target == null || reachable == null || reachable.isEmpty()) {
            return false;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                if (reachable.containsKey(
                        new WorldPoint(target.getX() + dx, target.getY() + dy, target.getPlane()))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static WalkerState processWalk(WorldPoint target, int distance) {
        // Stage entry and solve the Draynor basement lever puzzle before pathfinding to a basement
        // room. This avoids accepting a closer partial route into an unrelated underground map.
        // The solver's internal walkTo calls clear currentTarget, so restore it before the real walk.
        if (DraynorBasementSolver.isBasementTarget(target)) {
            DraynorBasementSolver.solveIfNeeded(target);
            // The solver's nested walkTo calls clear currentTarget; restore it so the real walk
            // runs — but not if this walk was interrupted/cancelled while the (blocking) solver
            // ran (the solver itself never interrupts, so an interrupt here is an external cancel).
            if (Thread.currentThread().isInterrupted()) {
                return WalkerState.EXIT;
            }
            if (!DraynorBasementSolver.isBasementTarget(Rs2Player.getWorldLocation())) {
                WebWalkLog.spWarn("basement_entry_failed | target={} at={} - refusing disconnected partial route",
                        target, Rs2Player.getWorldLocation());
                setTarget(null, "rs2walker:processWalk:basement-entry-failed");
                return WalkerState.UNREACHABLE;
            }
            setTarget(target, "rs2walker:basement-solve-restore");
        }
        return processWalk(target, distance, 0);
    }

    private static WalkerState processWalk(WorldPoint target, int distance, int partialRetries) {
        if (debug) {
            return WalkerState.EXIT;
        }
        // Pre-flight: a destination with no walkable tile within the arrival distance can never be
        // reached, so reject it here rather than after a full route ending at the nearest wall.
        if (!Rs2PathApi.hasWalkableTileWithin(target, distance)) {
            WorldPoint nearestWalkable = Rs2PathApi.nearestWalkableTile(target, 48);
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
        int clientThreadTimeoutRetries = 0;
        // When the last partial retry was spent, so route progress made after it can refill the
        // budget. Without this the counter is monotonic for the entire walk.
        long lastPartialRetryAtMs = 0L;
        WorldPoint lastPartialRetryAtLoc = null;
        int consecutiveExemptIterations = 0;
        WorldPoint lastAttemptedMinimapClick = null;
        boolean lastAttemptedMinimapClickOk = false;
        long lastAttemptedMinimapClickAtMs = 0L;
        long pathfinderPendingSinceMs = 0L;

        Map<WorldPoint, Integer> reachableTilesCache = null;
        WorldPoint reachableTilesCacheOrigin = null;
        for (int processWalkTail = 0; processWalkTail < MAX_PROCESS_WALK_TAIL_ITERATIONS; processWalkTail++) {
        walkerHeartbeat(target, processWalkTail);
        try {
            walkerDiag("tail iteration begin idx=%d/%d target=%s current=%s interim=%s partialRetries=%d",
                    processWalkTail,
                    MAX_PROCESS_WALK_TAIL_ITERATIONS,
                    target,
                    currentTarget,
                    routeState.interimTargetWp,
                    partialRetriesWorking);
            if (isStableLoggedOut()) {
                traceProcessWalkExit("not-logged-in", target, processWalkTail);
                setTarget(null, "rs2walker:processWalk:not-logged-in");
                return WalkerState.EXIT;
            }
            if (walkCancelledDiag(target, "processWalk:entry", processWalkTail)) {
                return WalkerState.EXIT;
            }

            Rs2ActiveRouteStatus routeStatus = Rs2PathApi.getActiveRouteStatus();
            if (!routeStatus.isPresent()) {
                markStartupPhase("pf_wait_enter", target, "reason=pathfinder_null");
                walkerDiag("pathfinder null; waiting up to %dms", PATHFINDER_NULL_WAIT_MS);
                Rs2WalkerRuntimeAwaits.awaitCondition(
                        () -> Rs2PathApi.getActiveRouteStatus().isPresent(),
                        100,
                        PATHFINDER_NULL_WAIT_MS);
                routeStatus = Rs2PathApi.getActiveRouteStatus();
                if (walkCancelledDiag(target, "processWalk:after-wait-pathfinder", processWalkTail)) {
                    return WalkerState.EXIT;
                }
                if (!routeStatus.isPresent()) {
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

            if (routeStatus.isCalculating()) {
                long observedGeneration = routeStatus.getGeneration();
                markStartupPhase("pf_wait_retry", target, "slice=" + PATHFINDER_DONE_POLL_WAIT_MS);
                if (pathfinderPendingSinceMs == 0L) {
                    pathfinderPendingSinceMs = System.currentTimeMillis();
                }
                walkerDiag("pathfinder not done; short-poll max %dms", PATHFINDER_DONE_POLL_WAIT_MS);
                Rs2WalkerRuntimeAwaits.awaitCondition(() -> {
                    Rs2ActiveRouteStatus current = Rs2PathApi.getActiveRouteStatus();
                    return !current.isPresent()
                            || current.getGeneration() != observedGeneration
                            || current.isReady();
                }, 100, PATHFINDER_DONE_POLL_WAIT_MS);
                routeStatus = Rs2PathApi.getActiveRouteStatus();
                if (walkCancelledDiag(target, "processWalk:after-wait-done", processWalkTail)) {
                    return WalkerState.EXIT;
                }
                if (!routeStatus.isReady()) {
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
			captureActiveRouteComparisonEligibility(routeStatus.getGeneration());

			if (consumeRecoveryReplanForTest())
			{
				WebWalkLog.spDebug("test_recovery_replan | target={}", target);
				recalculatePathForRecovery();
				continue;
			}

            if (Rs2PathApi.getMarker() == null) {
                restoreTargetMarker(target);
            }

            final List<WorldPoint> rawPath = routeStatus.getRawPath();
            final List<WorldPoint> path = routeStatus.getWalkablePath();
            final int[] smoothedToRaw = mapSmoothedToRaw(path, rawPath);
            int rawSize = rawPath == null ? -1 : rawPath.size();
            int walkSize = path == null ? -1 : path.size();
            markStartupPhase("path_snapshot", target, "raw=" + rawSize + " walk=" + walkSize);
            WalkLoopSnapshot walkLoop = WalkLoopSnapshot.capture();
            final WorldPoint dst;
            if (path == null || path.isEmpty()) {
                dst = walkLoop.playerLoc;
            } else {
                dst = path.get(path.size()-1);
            }

            boolean partialPath = false;
            if (dst == null || dst.distanceTo(target) > distance) {
                final WorldPoint sealedRim = consumeSealedRimRetarget(target, dst);
                if (sealedRim != null) { return processWalk(sealedRim, distance, partialRetries); }
                if (path != null && path.size() > 1) {
                    WebWalkLog.partialSegment(dst, dst.distanceTo(target), target, path.size());
                    partialPath = true;
                } else {
                    Telemetry.recordUnreachable("no-walkable-path", walkLoop.playerLoc,
                            target, dst, path == null ? 0 : path.size(), distance,
                            routeStatus.getMetrics().orElse(null));
                    setTarget(null, "rs2walker:processWalk:no-walkable-path");
                    return WalkerState.UNREACHABLE;
                }
            }

            if (path == null || path.isEmpty()) {
                return WalkerState.ARRIVED;
            }

            // Partial segment: refresh routing before the endpoint so the continuation is ready.
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
                        if (now - routeState.lastPartialTransRecalcMs >= PARTIAL_TRANS_RECAL_COOLDOWN_MS) {
                            routeState.lastPartialTransRecalcMs = now;
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

            int earlyRouteStartIdx = stabilizeRouteProgressWithRawWatermark(rawPath, path, walkLoop.closestTileIndex(path), target, walkLoop.playerLoc);
            boolean immediateRouteTransportPending = hasImmediatePlannedTransportStep(path, earlyRouteStartIdx, walkLoop.playerLoc);

            // Do not clear walk target while a sticky minimap interim is active — breaks
            // isWalkCancelled and forces EXIT while the flag is still carrying the player.
            // Partial paths end at an intermediate waypoint (dst still far from {@code target});
            // clearing here would drop currentTarget before the partial-path retry/recalc branch.
            if (!partialPath && isNear(dst, walkLoop.playerLoc) && routeState.interimTargetWp == null) {
                setTarget(null, "rs2walker:processWalk:reached-path-endpoint");
            }

            boolean shouldIssueActiveRouteIdleNudge = shouldIssueActiveRouteIdleNudge();
            checkIfStuck();
            if (walkCancelledDiag(target, "processWalk:after-stuck-check", processWalkTail)) {
                return WalkerState.EXIT;
            }
            if (isStuckTooLong() && !hasFreshActiveDoorClaim(System.currentTimeMillis())) {
				// Leagues area teleports can have long animations. Never trigger stall-recalc
				// while the transport is in-flight, or we will interrupt and re-click.
				if (Rs2LeaguesTransport.isTeleportInProgress()
						|| Rs2LeaguesTransport.isLeaguesAreaTeleportPending(LEAGUES_AREA_PENDING_STALL_MAX_AGE_MS))
				{
					return WalkerState.MOVING;
				}
                long sinceMoved = System.currentTimeMillis() - routeState.lastMovedTimeMs;
                long threshold = stallThresholdMs();
                Telemetry.recordStallRecalc(sinceMoved, walkLoop.playerLoc);
                WebWalkLog.stallRecalc(sinceMoved, threshold,
                        Rs2Player.isInCombat(), walkLoop.animating, walkLoop.interacting);
                if (lastAttemptedMinimapClick != null) {
                    WebWalkLog.stallContextDebug(
                            lastAttemptedMinimapClick,
                            lastAttemptedMinimapClickOk,
                            Math.max(0L, System.currentTimeMillis() - lastAttemptedMinimapClickAtMs),
                            routeState.interimTargetWp);
                }
                routeState.lastMovedTimeMs = System.currentTimeMillis();
                routeState.stuckCount = 0;
                clearInterimTarget("stall-recalc");
                if (immediateRouteTransportPending) {
                    WebWalkLog.spDebug("stall_recovery_suppressed | reason=immediate-route-transport idx={}", earlyRouteStartIdx);
                } else if (walkLoop.idle()) {
					recalculatePathForRecovery();
                    tryIssueRouteRecoveryClick(rawPath, path, target, distance, "stall recovery click");
                    continue;
                } else {
					recalculatePathForRecovery();
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
                routeState.lastActiveRouteIdleNudgeAtMs = System.currentTimeMillis();
            }
            if (routeState.stuckCount > 10) {
                var reachable = Rs2Tile.getReachableTilesFromTile(walkLoop.playerLoc, 5).keySet();
                if (!reachable.isEmpty()) {
                    // Rank sidestep candidates by distance-toward-target so recovery
                    // biases toward the goal instead of wandering. Keep a top-K pool
                    // with weighted randomness so repeat stalls don't lock onto the
                    // same blocked tile.
                    List<WorldPoint> ranked = rankSidestepTilesToward(reachable, target);
                    int poolSize = Math.min(3, ranked.size());
                    WorldPoint sidestep = ranked.get(Rs2Random.between(0, poolSize));
                    log.info("[Walker] stuck sidestep: clicked to={} player={} routeState.stuckCount={}",
                            sidestep, walkLoop.playerLoc, routeState.stuckCount);
                    walkMiniMap(sidestep);
                    sleepGaussian(1000, 300);
                    routeState.stuckCount = 0;
                    // The sleep above made the pass-start snapshot a lie; every read below this
                    // point (playerLocForIndex first among them) must see the post-sidestep world.
                    walkLoop = WalkLoopSnapshot.capture();
                }
            }

            WorldPoint playerLocForIndex = walkLoop.playerLoc;
            int indexOfStartPoint = stabilizeRouteProgressIndex(path, walkLoop.closestTileIndex(path), target, playerLocForIndex);
            indexOfStartPoint = advanceIndexPastRecentTransportEdge(path, indexOfStartPoint, playerLocForIndex);
            if (indexOfStartPoint == -1) {
                walkerDiag("getClosestTileIndex=-1 pathSize=%d player=%s pathFirst=%s pathLast=%s", path.size(),
                        playerLocForIndex, path.isEmpty() ? null : path.get(0),
                        path.isEmpty() ? null : path.get(path.size() - 1));
                traceProcessWalkExit("closest-index-none", target, processWalkTail);
                setTarget(null, "rs2walker:processWalk:closest-index-none");
                return WalkerState.EXIT;
            }
            primeExpectedTransportDestinations(path, indexOfStartPoint);

            routeState.lastPosition = playerLocForIndex;
            boolean clearedInterimTarget = clearInterimTargetIfReachedOrExpired(routeState.lastPosition, path, System.currentTimeMillis());
            WorldPoint plImmediate = routeState.lastPosition;

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
            // Continuation clicks are for flowing ALONG the route after an interim clears. Off-path they
            // are actively harmful: the click keeps the player moving, movement defers the off-path
            // recalc, the interim goes stale, and the next continuation click sustains the spiral — the
            // walker can run minutes in the wrong corridor without ever replanning. Off-path, do nothing
            // here: the player stops, the "moving" deferral ends, and OFFPATH_RECALC replans properly.
            if (clearedInterimTarget
                    && isNearPath(walkLoop.playerLoc)
                    && !walkLoop.interacting
                    && !walkLoop.animating
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
            WalkExit exit = WalkExit.END_OF_PATH;
            String offPathDeferDetail = "";
            doorAttemptLedger.beginTailPass();
            ObstaclePolicy startupPolicy = obstaclePolicyForCurrentPhase();

            // Re-capture: the widget dialogs above sleep for seconds when they fire.
            walkLoop = WalkLoopSnapshot.capture();
            long activeInterimNowMs = System.currentTimeMillis();
            if (!walkLoop.interacting
                    && !walkLoop.animating
                    && !isDoorInteractionSettling()
                    && !isTransportInteractionSettling()
                    && (target == null
                    || walkLoop.playerLoc == null
                    || walkLoop.playerLoc.distanceTo(target) > immediateFinishTh)
                    && shouldYieldForActiveRouteInterim(walkLoop.playerLoc, path, activeInterimNowMs)) {
                exit = WalkExit.INTERIM_IN_FLIGHT_ROUTE;
                WebWalkLog.earlyExit(exit.wireName(offPathDeferDetail),
                        walkLoop.playerLoc,
                        target,
                        path.get(path.size() - 1),
                        indexOfStartPoint,
                        path.size());
                walkerDiag("tail exempt exitReason=%s tailBefore=%d early=true interim=%s",
                        exit.wireName(offPathDeferDetail), processWalkTail, routeState.interimTargetWp);
                processWalkTail--;
                continue;
            }

            boolean postTransportWindow = routeState.lastTransportHandledAtMs > 0
                    && System.currentTimeMillis() - routeState.lastTransportHandledAtMs <= POST_TRANSPORT_PATH_TMARK_WINDOW_MS;
            boolean allowRawSceneScan = rawPath != null && path != null
                    && (startupPolicy.allowBroadRawHandlers()
                    || hasImmediateRawTransportStepNearPlayer(rawPath));
            int rawScanTransportLookaheadStartIdx = postTransportWindow
                    ? Math.min(path.size() - 1, Math.max(0, indexOfStartPoint + 1))
                    : indexOfStartPoint;
            if (allowRawSceneScan && isTransportInteractionSettling()) {
                allowRawSceneScan = false;
                tmarkPostTransport("post_transport_raw_scene_scan_skip", target,
                        "reason=transport_settling");
            }
            if (allowRawSceneScan && postTransportWindow
                    && !hasUpcomingNearbyTransportStep(path, rawScanTransportLookaheadStartIdx, walkLoop.playerLoc,
                    POST_TRANSPORT_RAW_SCAN_TRANSPORT_LOOKAHEAD_EDGES, POST_TRANSPORT_RAW_SCAN_TRANSPORT_MAX_DIST)) {
                allowRawSceneScan = false;
                tmarkPostTransport("post_transport_raw_scene_scan_skip", target,
                        "reason=no_nearby_planned_transport");
            }
            long rawSceneStartAt = System.currentTimeMillis();
            // Transports stay enabled here even inside the post-transport window. The gate above has
            // already skipped the whole scan unless a planned transport is nearby, so reaching this
            // line during the window MEANS one is — and disabling transport handling anyway is what
            // made the walker take one staircase and then ignore the next one two tiles away, until
            // the idle nudge minimap-clicked onto its origin and the current-tile handler picked it
            // up. Observed at Falador castle: nudge to (2959,3339) instead of clicking the stairs.
            //
            // The original reason for suppressing — "broad raw transport scans can enter long
            // false-negative waits" — was the 10s object scan (composition lookup per nearby object
            // on Climb-down), fixed separately. Ranged dispatch also carries a no-progress fallback
            // and a per-edge cooldown, so a transport that does not respond degrades instead of
            // looping.
            lastRawScanEarlyReturn = allowRawSceneScan
                    ? "not-attempted"
                    : (startupPolicy.allowBroadRawHandlers() ? "gated-outer" : "policy-startup");
            boolean rawSceneHandled = allowRawSceneScan
                    && handleNearbyRawPathSceneObjects(rawPath, HANDLER_RANGE, target, true);
            tmarkPostTransport("post_transport_raw_scene_scan_why", target, "why=" + lastRawScanEarlyReturn + " handled=" + rawSceneHandled);
            tmarkPostTransport("post_transport_raw_scene_scan", target,
                    "handled=" + rawSceneHandled + " ms=" + (System.currentTimeMillis() - rawSceneStartAt));
            if (rawSceneHandled) {
                doorOrTransportResult = true;
                exit = WalkExit.RAW_PATH_SCENE_OBJECT_HANDLED;
            }

            long currentTileTransportStartAt = System.currentTimeMillis();
            boolean currentTileTransportHandled = !doorOrTransportResult
                    && startupPolicy.allowBroadRawHandlers()
                    && handleCurrentTileTransportTowardPath(rawPath, path, target);
            tmarkPostTransport("post_transport_current_tile_transport", target,
                    "handled=" + currentTileTransportHandled + " ms=" + (System.currentTimeMillis() - currentTileTransportStartAt));
            if (currentTileTransportHandled) {
                doorOrTransportResult = true;
                exit = WalkExit.CURRENT_TILE_TRANSPORT_HANDLED;
            }

            if (!doorOrTransportResult) {
                WalkerState directShortWalk = tryDirectShortWalk(target, distance, rawPath, path, inInstance);
                if (directShortWalk != WalkerState.MOVING) {
                    return directShortWalk;
                }
            }

            // Re-capture: the raw scan, current-tile transport and direct-short-walk above block.
            walkLoop = WalkLoopSnapshot.capture();
            WorldPoint currentPlayerLoc = walkLoop.playerLoc;
            reachableTilesCache = Rs2Tile.getReachableTilesFromTile(currentPlayerLoc, HANDLER_RANGE * 3);
            reachableTilesCacheOrigin = currentPlayerLoc;
            final int currentPlayerPlane = currentPlayerLoc != null ? currentPlayerLoc.getPlane() : -1;

            // Route order for interact-at-range: the loop walks segments outward from the player, so
            // the first one to reach the obstacle handlers is the nearest. Only that one may act while
            // we are still moving; anything further waits until it becomes the nearest.
            boolean segmentHandlersRanThisPass = false;
            /**
             * Whether any EARLIER segment was skipped this pass. A skipped segment was never examined,
             * so a door on it is neither resolved nor ruled out, and the first segment that actually
             * runs is not the nearest unresolved obstacle just because it is the first one handled.
             */
            boolean segmentSkippedThisPass = false;
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

                boolean recentTransportWindow = routeState.lastTransportHandledAtMs > 0
                        && System.currentTimeMillis() - routeState.lastTransportHandledAtMs <= POST_TRANSPORT_PATH_TMARK_WINDOW_MS;
                // One world per segment iteration: the previous iteration's handlers may have blocked.
                walkLoop = WalkLoopSnapshot.capture();
                WorldPoint playerForPathCheck = walkLoop.playerLoc;
                if (isTransportInteractionSettling()) {
                    tmarkPostTransport("post_transport_settling_yield", target, "at=" + compactWorldPoint(playerForPathCheck));
                    exit = WalkExit.TRANSPORT_SETTLING_YIELD;
                    break;
                }
                boolean nearPath = isNearPath(walkLoop.playerLoc);
                boolean nearPathByVariance = !nearPath && isNearPathByVariance(path, playerForPathCheck);
                if (recentTransportWindow && !nearPath) {
                    WebWalkLog.tmark("post_transport_nearpath_gate",
                            System.currentTimeMillis() - routeState.lastTransportHandledAtMs,
                            target, playerForPathCheck, "nearPath=false variance=" + nearPathByVariance);
                }
                if (!nearPath && !recentTransportWindow && !nearPathByVariance) {
                    // Avoid mid-walk recalculation while recent clicks, route progress, or busy state
                    // indicate the player may still be advancing. isStuckTooLong() will trigger a
                    // real recovery path once progress actually halts.
                    String deferReason = currentOffPathRecalcDeferralReason(lastAttemptedMinimapClickAtMs);
                    if (deferReason != null) {
                        Telemetry.recordOffPathRecalcDeferred(deferReason, playerForPathCheck, target, path.size());
                        if (routeState.lastTransportHandledAtMs > 0
                                && System.currentTimeMillis() - routeState.lastTransportHandledAtMs <= POST_TRANSPORT_PATH_TMARK_WINDOW_MS) {
                            WebWalkLog.tmark("post_transport_offpath_moving_yield",
                                    System.currentTimeMillis() - routeState.lastTransportHandledAtMs,
                                    target, playerForPathCheck, "defer=" + deferReason);
                        }
                        exit = WalkExit.OFF_PATH_DEFERRED;
                        offPathDeferDetail = deferReason;
                        break;
                    }
                    Telemetry.recordOffPathRecalc(walkLoop.playerLoc, path.size());
                    // Distinguish the drift signature in logs: off-path while still moving with no
                    // walker action in flight = something external is steering the player.
                    WebWalkLog.recalc(walkLoop.moving ? "off_path_unowned_movement" : "no_longer_near_path");
                    if (config.cancelInstead()) {
                        setTarget(null, "rs2walker:processWalk:off-path-cancel-instead");
                    } else {
						recalculatePathForRecovery();
                    }
                    exit = WalkExit.NOT_NEAR_PATH;
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
                WorldPoint playerNearSeg = walkLoop.playerLoc;
                if (playerNearSeg == null) {
                    exit = WalkExit.PLAYER_LOCATION_NULL;
                    break;
                }
                int segDistance = currentWorldPoint.distanceTo2D(playerNearSeg);
                if (segDistance <= HANDLER_RANGE) {
                    int rawI = (i < smoothedToRaw.length) ? smoothedToRaw[i] : 0;
                    int rawEnd = rawEndForSmoothedIndex(i, smoothedToRaw, rawPath, path);
                    SegmentTransportContext transportContext = segmentTransportContext(
                            rawPath, rawI, rawEnd, path, i, playerNearSeg);
                    boolean recentDoorAttemptNearSegment = hasRecentDoorAttemptNearIndex(path, i);
                    SegmentGate.SegmentAction segmentAction = SegmentGate.decide(
                            recentTransportWindow, transportContext.upcomingNearby, recentDoorAttemptNearSegment,
                            isDoorInteractionSettling(), isRecoveryMovementInFlight(),
                            reachableTilesCache.containsKey(currentWorldPoint),
                            currentWalkerPhase() == WalkerPhase.STARTUP, transportContext.startupStep, i, indexOfStartPoint);
                    if (segmentAction.isSkip()) {
                        segmentSkippedThisPass = true;
                        if (segmentAction == SegmentGate.SegmentAction.SKIP_STARTUP_PRECLICK) {
                            markStartupPhase("preclick_segment_handler_skip", target,
                                    "i=" + i + " reason=" + segmentAction.wireReason());
                        }
                        tmarkPostTransport("post_transport_segment_handler_skip",
                                target, "i=" + i + " reason=" + segmentAction.wireReason());
                    } else {
                    long segmentHandlerStartAt = System.currentTimeMillis();
                    boolean startupImmediateTransportOnly = currentWalkerPhase() == WalkerPhase.STARTUP
                            && transportContext.startupStep;
                    // The stationary requirement is why doors only ever opened AFTER the approach walk
                    // finished: the handler was skipped for the whole journey and ran on arrival. For the
                    // NEXT segment on the route the door click should simply supersede our own walk —
                    // interrupting it is the point, and the server walks us to the door either way.
                    // Everything further along still waits until it is the nearest segment, so route
                    // order holds. Settle windows and in-flight recovery still block, unchanged.
                    // "First handler to run this pass" is NOT the same as "nearest unresolved obstacle
                    // on the route", and only the latter may be clicked at range. When an earlier
                    // segment was SKIPPED — post-transport window, startup pre-click — it was never
                    // examined, so a door on it is neither resolved nor ruled out, and reaching past it
                    // is exactly the failure the interact-at-range rule exists to prevent.
                    //
                    // Measured at Falador: segments 11 and 12 skipped with no_nearby_planned_transport,
                    // then the door at (2985,3341) clicked from range while the door at (2981,3340) was
                    // still shut between us and it. The server began routing AROUND the building — the
                    // player was dragged south to (2960,3330) — and the traversal wait it could never
                    // satisfy timed out at 2275ms. Roughly ten seconds and a U-turn.
                    //
                    // With an earlier segment skipped, doors fall back to the stationary requirement,
                    // which is the behaviour from before ranged door dispatch existed.
                    boolean nearestSegmentDoor = SegmentGate.mayDispatchDoorAtRange(
                            segmentHandlersRanThisPass, segmentSkippedThisPass);
                    segmentHandlersRanThisPass = true;
                    boolean doorMovementGateOk = !Rs2Player.isMoving()
                            || (nearestSegmentDoor && doorInteractionWhileApproachingEnabled());
                    if (!startupImmediateTransportOnly
                            && doorMovementGateOk && !isDoorInteractionSettling() && !isRecoveryMovementInFlight()) {
                        doorOrTransportResult = handleDoorsInRawSegment(rawPath, rawI, rawEnd,
                                obstaclePolicy.segmentDoorTimeoutMs(),
                                reachableTilesCache);
                    }
                    if (doorOrTransportResult) {
                        tmarkPostTransport("post_transport_segment_handler", target,
                                "stage=door handled=true i=" + i + " ms=" + (System.currentTimeMillis() - segmentHandlerStartAt));
                        exit = WalkExit.DOOR_HANDLED;
                        break;
                    }

                    // Chain step 2: path-adjacent probes after exact segment-door attempt.
                    boolean allowPathAdjacentProbe = !recentTransportWindow
                            || transportContext.upcomingNearby
                            || recentDoorAttemptNearSegment;
                    if (!startupImmediateTransportOnly
                            && !Rs2Player.isMoving() && obstaclePolicy.allowPathAdjacentProbe()
                            && allowPathAdjacentProbe) {
                        if (tryHandleBlockingPathObjectsWithTimeout(rawPath, rawI, 5, 10,
                                obstaclePolicy.pathAdjacentProbeTimeoutMs())) {
                            tmarkPostTransport("post_transport_segment_handler", target,
                                    "stage=path_adj handled=true i=" + i + " ms=" + (System.currentTimeMillis() - segmentHandlerStartAt));
                            exit = WalkExit.PATH_BLOCKER_HANDLED;
                            break;
                        }
                    }

                    if (!startupImmediateTransportOnly) {
                        // P2 unified rockfall dispatch (shared with recovery). INTERACTED (mined) breaks the
                        // segment loop; ABORT (no pickaxe) clears the target and falls through, exactly as the
                        // former applyRockfall(handleRockfallInRawSegment(...)) did.
                        ObstacleResolution rockfall = resolveRockfallOnSegment(rawPath, rawI, rawEnd, reachableTilesCache);
                        doorOrTransportResult = rockfall.kind() == ObstacleResolution.Kind.INTERACTED;
                        if (rockfall.kind() == ObstacleResolution.Kind.ABORT) {
                            setTarget(null, "rs2walker:" + rockfall.reason());
                        }
                    }
                    if (doorOrTransportResult) {
                        tmarkPostTransport("post_transport_segment_handler", target,
                                "stage=rockfall handled=true i=" + i + " ms=" + (System.currentTimeMillis() - segmentHandlerStartAt));
                        exit = WalkExit.ROCKFALL_HANDLED;
                        break;
                    }

                    boolean allowSegmentTransportScan = !recentTransportWindow
                            || transportContext.upcomingNearby
                            || transportContext.startupStep;
                    if ((PohTeleports.isInHouse() || !inInstance) && allowSegmentTransportScan) {
                        // Nearest segment may take its transport from range; anything further along
                        // waits until it becomes nearest, so route order holds.
                        doorOrTransportResult = handleTransportsInRawSegment(rawPath, rawI, rawEnd,
                                nearestSegmentDoor);
                    }

                    if (doorOrTransportResult) {
                        tmarkPostTransport("post_transport_segment_handler", target,
                                "stage=transport handled=true i=" + i + " ms=" + (System.currentTimeMillis() - segmentHandlerStartAt));
                        exit = WalkExit.TRANSPORT_HANDLED;
                        break;
                    }
                    tmarkPostTransport("post_transport_segment_handler", target,
                            "stage=none handled=false i=" + i + " ms=" + (System.currentTimeMillis() - segmentHandlerStartAt));
                    }
                }

                boolean tileReachable = reachableTilesCache.containsKey(currentWorldPoint);
                // The handlers above block for seconds, so re-capture the snapshot — but ONLY for
                // tiles the recovery gate below can consume (far hops were the pre-obstacle stall).
                if (!tileReachable && !inInstance) {
                    if (FrontierDecision.shouldSkipFarUnreachableTile(currentWorldPoint,
                            walkLoop.playerLoc, HANDLER_RANGE + 2, FAR_UNREACHABLE_STALENESS_MARGIN)) {
                        continue;
                    }
                    walkLoop = WalkLoopSnapshot.capture();
                    WorldPoint playerLoc = walkLoop.playerLoc;
                    if (playerLoc != null && !playerLoc.equals(reachableTilesCacheOrigin)) {
                        reachableTilesCache = Rs2Tile.getReachableTilesFromTile(playerLoc, HANDLER_RANGE * 3);
                        reachableTilesCacheOrigin = playerLoc;
                        tileReachable = reachableTilesCache.containsKey(currentWorldPoint);
                        WebWalkLog.spDebug("reachable_recapture | from={} tile={} reachableNow={}", compactWorldPoint(playerLoc), compactWorldPoint(currentWorldPoint), tileReachable);
                    }
                }
                if (!tileReachable && !inInstance) {
                    WorldPoint playerLoc = walkLoop.playerLoc;
                    if (playerLoc != null) {
                        int unreachableDist = currentWorldPoint.distanceTo2D(playerLoc);
                        if (unreachableDist <= HANDLER_RANGE + 2) {
                            int recoveryScanStart = forwardRecoveryScanStart(rawPath, smoothedToRaw, indexOfStartPoint, playerLoc);
                            boolean candidateOnCurrentRouteFrontier = RouteRecovery.isLocalRecoveryCandidateOnForwardRoute(
                                    rawPath,
                                    smoothedToRaw,
                                    recoveryScanStart,
                                    i,
                                    LOCAL_RECOVERY_RAW_ROUTE_LOOKAHEAD_STEPS);
                            if (!candidateOnCurrentRouteFrontier) {
                                log.info("[Walker] spatially-near future route branch ignored for local recovery: tile={} idx={}/{} routeStart={} player={}", currentWorldPoint, i, path.size(), recoveryScanStart, playerLoc);
                                if (tryIssueRouteContinuationClick(rawPath, path, target, distance)) {
                                    exit = WalkExit.ROUTE_FOLD_CONTINUATION_CLICK;
                                    break;
                                }
                                // Fold stall fix: ending the pass at a behind/branch tile left nobody to
                                // handle the NEXT gate (4-26s pending per corridor). Keep scanning forward.
                                continue;
                            }
                            log.debug("[Walker] local reachability miss near player; checking blockers/recovery: tile={} idx={}/{} player={} target={}", currentWorldPoint, i, path.size(), playerLoc, target);

                            // Anti-end-camping frontier rewind. The near-player reachability check skips
                            // far-away route tiles, so on a route whose tail folds back beside the player
                            // (Clock Tower) the miss can fire on the GOAL (Euclidean-near, idx end) while the
                            // REAL blocked frontier — the door tiles at mid-route — was silently skipped.
                            // Recovery then camps on the end: door scans probe the wrong raw segment and the
                            // recovery target anchors at the goal. Rewind to the EARLIEST unreachable route
                            // tile: that is the first edge the walk actually cannot cross, which is where the
                            // door (or other obstacle) really is. Every recovery path below exits the loop,
                            // so rebinding i/currentWorldPoint here is contained.
                            int rewoundIdx = FrontierDecision.earliestBlockedIndex(
                                    path, recoveryScanStart, i, currentPlayerPlane, reachableTilesCache);
                            if (rewoundIdx != FrontierDecision.NO_EARLIER_BLOCKED_INDEX) {
                                log.info("[Walker] frontier rewind: earliest blocked route tile idx={} tile={} (miss was idx={})",
                                        rewoundIdx, path.get(rewoundIdx), i);
                                i = rewoundIdx;
                                currentWorldPoint = path.get(rewoundIdx);
                            }

                            FrontierDecision.FrontierEdge frontier =
                                    FrontierDecision.frontierEdge(rawPath, smoothedToRaw, recoveryScanStart, i);
                            int edgeIdx = frontier.edgeIndex();
                            int rawEdgeStart = frontier.rawStart();
                            int rawEdgeEnd = frontier.rawEndExclusive();
                            WorldPoint edgeFrom = frontier.from();
                            WorldPoint edgeTo = frontier.to();

                            // Unified obstacle dispatch for the blocked frontier (P2). One call resolves both
                            // a rockfall to mine here and a reachable transport/agility-shortcut approach to step
                            // onto below, replacing the former inline rockfall mine and the separate
                            // findReachableTransportApproachAhead override. A mined rockfall ends recovery this
                            // tick; a no-pickaxe rockfall clears the target (as applyRockfall did) and falls
                            // through; a transport approach is applied as the recovery target further down. The
                            // scan is MLM-/proximity-gated inside the resolver, so it is a no-op elsewhere.
                            ObstacleResolution frontierObstacle = resolveRecoveryObstacle(rawPath, rawEdgeStart,
                                    rawEdgeEnd, playerLoc, STALL_RECOVERY_MINIMAP_REACH_EUCLIDEAN, reachableTilesCache,
                                    inInstance);
                            if (frontierObstacle.kind() == ObstacleResolution.Kind.INTERACTED) {
                                // A rockfall was mined or an on-origin transport/shortcut was taken.
                                exit = WalkExit.FRONTIER_OBSTACLE_HANDLED;
                                break;
                            }
                            if (frontierObstacle.kind() == ObstacleResolution.Kind.ABORT) {
                                // e.g. rockfall with no pickaxe: clear the target so we stop hammering it, then
                                // fall through to normal recovery — identical to applyRockfall's NO_PICKAXE path.
                                setTarget(null, "rs2walker:" + frontierObstacle.reason());
                            }

                            if (hasRecentDoorAttemptOnEdge(edgeFrom, edgeTo)) {
                                boolean edgeResolved = waitForDoorEdgeResolution(edgeFrom, edgeTo,
                                        obstaclePolicy.edgeResolutionWaitTimeoutMs());
                                boolean clicked = FrontierDecision.shouldFastClickAfterEdgeWait(edgeResolved)
                                        && tryPostDoorFastMinimapClick(path, edgeIdx, playerLoc, target);
                                exit = FrontierDecision.afterEdgeWait(edgeResolved, clicked).exit();
                                break;
                            }
                            if (hasRecentDoorAttemptNearIndex(rawPath, rawEdgeStart)) {
                                boolean nearbyResolved = waitForRecentDoorEdgeResolutionNearIndex(rawPath, rawEdgeStart,
                                        obstaclePolicy.edgeResolutionWaitTimeoutMs());
                                WorldPoint afterNearbyWait = Rs2Player.getWorldLocation();
                                boolean playerMoved = afterNearbyWait != null
                                        && !afterNearbyWait.equals(playerLoc);
                                boolean clicked = FrontierDecision.shouldFastClickAfterNearbyWait(nearbyResolved, playerMoved)
                                        && tryPostDoorFastMinimapClick(path, edgeIdx, afterNearbyWait, target);
                                FrontierDecision.DoorWaitOutcome nearbyOutcome =
                                        FrontierDecision.afterNearbyWait(nearbyResolved, playerMoved, clicked);
                                if (nearbyOutcome.endsPass()) {
                                    exit = nearbyOutcome.exit();
                                    break;
                                }
                                // FALL_THROUGH: a nearby door opened but we did not move, so nothing was
                                // learned about THIS frontier — carry on to the settle checks below.
                            }
                            FrontierDecision.FrontierYield frontierYield =
                                    FrontierDecision.yieldBeforeDoorActions(
                                            isDoorInteractionSettling(),
                                            isDoorEdgePassSkipCoolingDown(),
                                            recentDoorAttemptAgeNearIndex(rawPath, rawEdgeStart),
                                            DOOR_TRAVERSAL_RECOVERY_BLOCK_MS,
                                            Rs2Player.isMoving(),
                                            shouldYieldForActiveRecoveryInterim(playerLoc, path, System.currentTimeMillis()));
                            if (frontierYield.yields()) {
                                exit = frontierYield.exit();
                                break;
                            }
                            if (tryRecentDoorAttemptEdgeNudge(playerLoc, target, rawPath)) {
                                exit = WalkExit.RECENT_DOOR_EDGE_NUDGE;
                                break;
                            }
                            if (handleFirstRecoveryDoor(rawPath, obstaclePolicy.unreachableDoorTimeoutMs(),
                                    playerLoc, reachableTilesCache)) {
                                exit = WalkExit.DOOR_HANDLED_LOCAL_REACHABILITY_RAW_SCAN;
                                break;
                            }
                            if (handleDoorsInRawSegment(rawPath, rawEdgeStart, rawEdgeEnd,
                                    obstaclePolicy.unreachableDoorTimeoutMs(),
                                    reachableTilesCache)) {
                                exit = WalkExit.DOOR_HANDLED_LOCAL_REACHABILITY;
                                break;
                            }
                            if (isRecoveryMovementInFlight()) {
                                exit = WalkExit.RECOVERY_MOVE_IN_FLIGHT;
                                break;
                            }
                            boolean unresolvedDoorNearRawPath = hasUnresolvedDoorLikeObjectNearRawPath(rawPath,
                                    rawEdgeStart, playerLoc,
                                    UNREACHABLE_DOOR_RECOVERY_BACKTRACK_EDGES,
                                    UNREACHABLE_DOOR_RECOVERY_LOOKAHEAD_EDGES,
                                    HANDLER_RANGE);
                            // No !gateDoorInteraction re-check: reaching here means the yield above
                            // returned NONE, which already proved the door-settling window closed.
                            if (unresolvedDoorNearRawPath
                                    && handleUnresolvedDoorNearRawPath(rawPath, rawEdgeStart,
                                    obstaclePolicy.unreachableDoorTimeoutMs(),
                                    playerLoc,
                                    UNREACHABLE_DOOR_RECOVERY_BACKTRACK_EDGES,
                                    UNREACHABLE_DOOR_RECOVERY_LOOKAHEAD_EDGES,
                                    HANDLER_RANGE)) {
                                exit = WalkExit.DOOR_HANDLED_NEARBY_ROUTE_DOOR;
                                break;
                            }
                            // Fallback: only interact with objects on/adjacent to blocked path edges
                            // within ~15 tiles. Prevents clicking already-open / unrelated doors.
                            final long nowMs = System.currentTimeMillis();
                            if (unresolvedDoorNearRawPath
                                    && obstaclePolicy.allowNearbyFallback()
                                    && nowMs - routeState.lastDoorPathAdjAttemptAtMs > 1200) {
                                routeState.lastDoorPathAdjAttemptAtMs = nowMs;
                                if (tryResolvePathAdjacentBlocker(playerLoc, rawPath, rawEdgeStart, 3, 10)) {
                                    exit = WalkExit.DOOR_HANDLED_PATH_ADJ_SCAN;
                                    break;
                                }
                            }
                            // A shortcut / transport on the blocked frontier is TAKEN here rather than
                            // routed around: the minimap fallback below would pick the tile on the FAR
                            // side and send the server the long way around the gap. Recovery acts on the
                            // edge blocking us right now, so it is the nearest obstacle by construction
                            // and may dispatch from range.
                            // Ordered BEFORE door suppression, which breaks out of recovery and so never
                            // let the transport have its turn. Measured near Draynor: a catalog transport
                            // at (3064,3282) was refused as walled, declined by the door handlers, then
                            // suppressed as a "nearby route door" that was this very transport — four
                            // seconds before the raw scan dispatched it. Suppression still guards the
                            // generic recovery click below; it just no longer outranks this.
                            if ((PohTeleports.isInHouse() || !inInstance)
                                    && handleTransportsInRawSegment(rawPath, rawEdgeStart, rawEdgeEnd, true)) {
                                exit = WalkExit.TRANSPORT_HANDLED_LOCAL_REACHABILITY;
                                break;
                            }

                            if (unresolvedDoorNearRawPath) {
                                // An unresolved door sits on/near the blocked edge but every door handler above
                                // declined (settling / recent-attempt cooldowns). Do NOT fall through to the
                                // generic recovery click: it selects statically-walkable tiles PAST the closed
                                // door (they are walkable on the map — the door is the only blocker), and the
                                // server then paths the player AROUND the building, pulling the walk off-route
                                // (Clock Tower: nudge past the door -> player circles outside -> recovery clicks
                                // the unreachable end tile). Instead, close the distance to the door: walk to the
                                // furthest REACHABLE route tile at/before the blocked edge — the reachability
                                // gate means the target can never be beyond the closed door — so the player is
                                // standing at the door when the cooldown expires and the handlers engage.
                                routeState.doorRecoverySuppressedAtMs = System.currentTimeMillis();
                                WorldPoint doorApproach = null;
                                if (rawPath != null && !rawPath.isEmpty() && reachableTilesCache != null) {
                                    int hi = Math.min(rawEdgeEnd, rawPath.size()) - 1;
                                    for (int ri = hi; ri >= Math.max(0, rawEdgeStart); ri--) {
                                        WorldPoint rt = rawPath.get(ri);
                                        if (rt != null && reachableTilesCache.containsKey(rt)
                                                && !rt.equals(playerLoc) && playerLoc.distanceTo2D(rt) > 1) {
                                            doorApproach = rt;
                                            break;
                                        }
                                    }
                                }
                                if (doorApproach != null && !Rs2Player.isMoving() && walkMiniMap(doorApproach)) {
                                    routeState.lastUnreachableRecoveryClickAtMs = System.currentTimeMillis();
                                    WebWalkLog.spInfo("door_suppressed_approach | to={} idx={} tile={}",
                                            compactWorldPoint(doorApproach), rawEdgeStart, compactWorldPoint(currentWorldPoint));
                                    exit = WalkExit.DOOR_SUPPRESSED_APPROACH_CLICK;
                                    break;
                                }
                                WebWalkLog.spInfo("door_recovery_suppressed | reason=nearby-route-door idx={} tile={}",
                                        rawEdgeStart, compactWorldPoint(currentWorldPoint));
                                exit = WalkExit.DOOR_RECOVERY_SUPPRESSED;
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
                            // Everything below decides recovery against playerLoc, which was read at the
                            // TOP of this pass — before the door/transport cascade above, which blocks for
                            // seconds inside interaction awaits. Measured at Falador castle: recovery
                            // reported player=(2974,3339) while the player actually stood at (2985,3340),
                            // eleven tiles and eight seconds later, declared the target walled, and threw
                            // the route away with a full recalculatePath().
                            //
                            // Refreshing playerLoc alone would be worse than leaving it: the unreachability
                            // verdict for currentWorldPoint, the route index i and the reachable-tile cache
                            // were all derived from the OLD position, so a fresh position here would pair
                            // with stale analysis. Abandon the pass and re-derive from where we actually are
                            // — the loop re-enters immediately, exactly as every other break here does.
                            WorldPoint playerLocNow = Rs2Player.getWorldLocation();
                            if (playerLocNow != null && !playerLocNow.equals(playerLoc)) {
                                WebWalkLog.spInfo("recovery_position_stale | was={} now={} idx={} re-evaluating",
                                        compactWorldPoint(playerLoc), compactWorldPoint(playerLocNow), i);
                                exit = WalkExit.RECOVERY_POSITION_STALE;
                                break;
                            }
                            final int recoveryMinimapReach = STALL_RECOVERY_MINIMAP_REACH_EUCLIDEAN;
                            int recoverIdx = findForwardReachableRecoveryIndex(path, i, playerLoc,
                                    recoveryMinimapReach);
                            if (recoverIdx < 0) {
								recoverIdx = RouteRecovery.findFurthestClickableIndex(path, i, playerLoc,
										Rs2PathApi::hasCatalogTransportOrigin,
                                        recoveryMinimapReach);
                            }
                            int minRecoveryIdx = Math.max(indexOfStartPoint, i);
                            recoverIdx = FrontierDecision.clampRecoveryIndex(recoverIdx, indexOfStartPoint, i, path.size());
                            WorldPoint recoverTarget = path.get(recoverIdx);
                            if (euclideanSq(recoverTarget, playerLoc)
                                    > recoveryMinimapReach * recoveryMinimapReach) {
                                // Furthest in-range path tile still beyond the minimap clip (e.g. a
                                // diagonal segment). Interpolate a point near the minimap edge toward
                                // path[i]; the server routes through whatever blocks line-of-sight.
                                recoverTarget = RouteRecovery.interpolateClickableTarget(path, i, playerLoc,
                                        path.get(i), recoveryMinimapReach - 1,
                                        wp -> inInstance || isKnownWalkableOrUnloaded(wp));
                            }
                            // Don't let recovery park the player on a tile next to an aggressive NPC
                            // (e.g. an undead tree). The planner avoids those via avoidDangerousNpcs,
                            // but this runtime fallback would otherwise strand us in melee. Step the
                            // target back along the path to the nearest non-hazard tile.
                            if (Rs2PathApi.shouldAvoidDangerousTile(recoverTarget)) {
                                recoverIdx = FrontierDecision.stepBackFromDanger(path, recoverIdx, minRecoveryIdx,
                                        Rs2PathApi::shouldAvoidDangerousTile);
                                recoverTarget = path.get(recoverIdx);
                            }
                            int rawAnchorIndex = rawIndexForSmoothedIndex(recoverIdx, smoothedToRaw, rawPath);
                            WorldPoint rawRecoveryTarget = inInstance ? null : findFurthestRawPathPointMatchingGated(
                                    rawPath,
                                    playerLoc,
                                    recoveryMinimapReach - 1,
                                    rawAnchorIndex,
                                    Rs2Walker::isKnownWalkableOrUnloaded);
                            WalkExit claimedDoorExit = resolveActiveDoorClaim(playerLoc,
                                    obstaclePolicy.unreachableDoorTimeoutMs(), rawPath);
                            if (claimedDoorExit != null) {
                                doorOrTransportResult = claimedDoorExit.isDoorLike();
                                exit = claimedDoorExit; break;
                            }
                            // Precedence (base < raw-gated < shortcut origin) and the hazard asymmetry
                            // between them live with the decision, pinned by its table.
                            WorldPoint transportApproach =
                                    frontierObstacle.kind() == ObstacleResolution.Kind.WALK_TO_ORIGIN
                                            ? frontierObstacle.walkTarget()
                                            : null;
                            recoverTarget = FrontierDecision.chooseRecoveryTarget(recoverTarget,
                                    rawRecoveryTarget, transportApproach, playerLoc,
                                    Rs2PathApi::shouldAvoidDangerousTile);
                            // The click decision (preemption vs walled vs cooldown vs click) is PURE and
                            // decision-table-tested in RouteRecovery — this shell only carries out the
                            // chosen action. Guard rationale (long recovery pass, walled end-snap, cooldown
                            // that throttles the replan but never re-enables the click) lives with the
                            // decision, where the interactions are pinned by tests instead of re-discovered
                            // live (Clock Tower backtrack; Port Sarim wall-click during cooldown).
                            // Movement only preempts the recovery click while the walker owns it
                            // (protecting our own in-flight click/door-open). External movement —
                            // combat dragging the player — must NOT block the corrective click.
                            RouteRecovery.RecoveryClickAction clickAction = RouteRecovery.decideRecoveryClick(
                                    recoverTarget, playerLoc,
                                    isDoorInteractionSettling(),
                                    Rs2Player.isMoving()
                                            && isMovementWalkerOwned(System.currentTimeMillis(), lastAttemptedMinimapClickAtMs),
                                    reachableTilesCache != null ? reachableTilesCache.keySet() : null,
                                    WALLED_RECOVERY_TARGET_EUCLIDEAN,
                                    System.currentTimeMillis(), routeState.lastWalledRecoveryReplanAtMs,
                                    WALLED_RECOVERY_REPLAN_COOLDOWN_MS);
                            if (clickAction == RouteRecovery.RecoveryClickAction.YIELD_ACTION_IN_FLIGHT) {
                                exit = WalkExit.RECOVERY_CLICK_PREEMPTED_BY_ACTION;
                                break;
                            }
                            if (clickAction == RouteRecovery.RecoveryClickAction.REPLAN_WALLED) {
                                routeState.lastWalledRecoveryReplanAtMs = System.currentTimeMillis();
                                WebWalkLog.spInfo("recovery_target_walled | to={} player={} replanning",
                                        compactWorldPoint(recoverTarget), compactWorldPoint(playerLoc));
								recalculatePathForRecovery();
                            }
                            WalkExit recoveryClickExit = FrontierDecision.exitForRecoveryClick(clickAction);
                            if (recoveryClickExit != null) {
                                exit = recoveryClickExit;
                                break;
                            }
                            WorldPoint clickedRecoveryTarget = null;
                            if (clickAction == RouteRecovery.RecoveryClickAction.CLICK) {
                                recoverTarget = RouteRecovery.clampToEuclideanRadius(playerLoc, recoverTarget,
                                        recoveryMinimapReach - 1);
                                clickedRecoveryTarget = clickMiniMapOrFallback(rawPath, recoverTarget, playerLoc,
                                        recoveryMinimapReach - 1, rawPath == null || rawPath.isEmpty(), rawAnchorIndex);
                            }
                            boolean clicked = clickedRecoveryTarget != null;
                            // Scene-click fallback only on final-adjacent approach (minimap click may
                            // miss the clip when very close); kept gated on reachability since it is a
                            // last resort, not the primary recovery path.
                            if (!clicked
                                    && FrontierDecision.shouldTrySceneClickFallback(playerLoc, target, recoverTarget,
                                            distance, FINAL_ADJACENT_CANVAS_NUDGE_CHEBYSHEV,
                                            DOOR_OPEN_CANVAS_NUDGE_MAX_FROM_PLAYER)
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
                                routeState.lastUnreachableRecoveryClickAtMs = System.currentTimeMillis();
                                // Sticky interim: subsequent iterations travel toward this point via the
                                // interim-in-flight path instead of re-running the (false-negative)
                                // reachability check and re-clicking every tick.
                                routeState.interimTargetWp = clickedRecoveryTarget;
                                routeState.interimTargetIdx = recoverIdx;
                                routeState.interimSetAtMs = System.currentTimeMillis();
                                routeState.interimLastProgressAtMs = routeState.interimSetAtMs;
                                routeState.interimLastBestPathIdx = getClosestTileIndex(path, playerLoc);
                                routeState.interimLastDistanceToTarget = distanceToInterimOrMax(clickedRecoveryTarget, playerLoc);
                                routeState.interimLastRetargetAtMs = routeState.interimSetAtMs;
                                WorldPoint pathLastRecovery = path.get(path.size() - 1);
                                int finishThRecovery = tightFinishThreshold(target, pathLastRecovery, distance);
                                waitForMovementStartAfterRecovery(target, playerLoc, clickedRecoveryTarget, target,
                                        finishThRecovery);
                                // Next outer iteration runs checkIfStuck/isStuckTooLong before tile delta — avoid
                                // spurious stall-recalc right after issuing recovery movement.
                                routeState.lastMovedTimeMs = System.currentTimeMillis();
                                routeState.stuckCount = 0;
                                exit = WalkExit.LOCAL_RECOVERY_CLICK;
                                break;
                            }
                            exit = WalkExit.LOCAL_REACHABILITY_MISS_NO_CLICK;
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
                    exit = WalkExit.DOOR_SETTLING_YIELD;
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
                    final int MINIMAP_REACH_EUCLIDEAN = normalMinimapReach();

					// Checkpoint-style walking: once we set a minimap flag, let the player actually
					// travel toward it. Do not keep recalculating/clicking new targets mid-run.
					WorldPoint interim = routeState.interimTargetWp;
					if (interim != null && interim.getPlane() == playerLoc.getPlane()) {
						int interimDist = interim.distanceTo2D(playerLoc);
						if (interimDist > INTERIM_CLOSE_TILES) {
							final WorldPoint interimFinal = interim;
							// If we're already moving toward the interim checkpoint, just wait until
							// we get close. If we've stopped (no movement), re-click the same interim
							// rather than spinning without issuing movement commands.
							if (Rs2Player.isMoving()) {
                                if (!inInstance && handlePendingDoorDuringInterim(rawPath,
                                        obstaclePolicy.segmentDoorTimeoutMs(),
                                        playerLoc)) {
                                    routeState.interimTargetWp = null;
                                    routeState.interimTargetIdx = -1;
                                    routeState.interimSetAtMs = 0L;
                                    routeState.interimLastProgressAtMs = 0L;
                                    routeState.interimLastBestPathIdx = -1;
                                    routeState.interimLastDistanceToTarget = Integer.MAX_VALUE;
                                    routeState.interimLastRetargetAtMs = 0L;
                                    doorOrTransportResult = true;
                                    exit = WalkExit.DOOR_HANDLED_DURING_INTERIM;
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
									routeState.lastMovedTimeMs = System.currentTimeMillis();
									routeState.stuckCount = 0;
								}
                                boolean closeEnoughForNextClick = posAfterWait != null
                                        && interimFinal.distanceTo2D(posAfterWait) <= INTERIM_CLOSE_TILES;
                                if (!closeEnoughForNextClick && Rs2Player.isMoving()) {
                                    exit = WalkExit.INTERIM_IN_FLIGHT_CLICK;
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
								routeState.interimTargetWp = null;
								routeState.interimTargetIdx = -1;
								routeState.interimSetAtMs = 0L;
								routeState.interimLastProgressAtMs = 0L;
								routeState.interimLastBestPathIdx = -1;
                                routeState.interimLastDistanceToTarget = Integer.MAX_VALUE;
								routeState.interimLastRetargetAtMs = 0L;
							}
						}
						// Close enough: allow selecting a new checkpoint.
						routeState.interimTargetWp = null;
						routeState.interimTargetIdx = -1;
						routeState.interimSetAtMs = 0L;
						routeState.interimLastProgressAtMs = 0L;
						routeState.interimLastBestPathIdx = -1;
                        routeState.interimLastDistanceToTarget = Integer.MAX_VALUE;
						routeState.interimLastRetargetAtMs = 0L;
					}

					int targetIdx = RouteRecovery.findFurthestForwardClickableIndex(path, i, playerLoc,
							Rs2PathApi::hasCatalogTransportOrigin,
                            MINIMAP_REACH_EUCLIDEAN);
                    WorldPoint targetWp = path.get(targetIdx);
                    // If the forward waypoint is outside minimap reach, interpolate a
                    // visible point in that direction instead of falling back to an
                    // earlier path tile.
                    if (euclideanSq(targetWp, playerLoc) > MINIMAP_REACH_EUCLIDEAN * MINIMAP_REACH_EUCLIDEAN) {
                        targetWp = RouteRecovery.interpolateClickableTarget(
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
					WorldPoint sticky = routeState.interimTargetWp;
					if (sticky != null && sticky.getPlane() == playerLoc.getPlane()) {
						int stickyDist = sticky.distanceTo2D(playerLoc);
                        recordInterimDistanceProgress(sticky, playerLoc, nowMs);
						if (stickyDist <= INTERIM_CLOSE_TILES || nowMs - routeState.interimSetAtMs > INTERIM_MAX_AGE_MS) {
							routeState.interimTargetWp = null;
							routeState.interimTargetIdx = -1;
							routeState.interimSetAtMs = 0L;
							routeState.interimLastProgressAtMs = 0L;
							routeState.interimLastBestPathIdx = -1;
                            routeState.interimLastDistanceToTarget = Integer.MAX_VALUE;
							routeState.interimLastRetargetAtMs = 0L;
						} else {
							// U-turn safe progress: track progress along the path index, not Euclidean
							// distance-to-target (which can increase on U-shaped routes).
							int bestIdxNow = getClosestTileIndex(path, playerLoc);
							if (bestIdxNow > routeState.interimLastBestPathIdx) {
								routeState.interimLastBestPathIdx = bestIdxNow;
								routeState.interimLastProgressAtMs = nowMs;
							}
							boolean movingOrRecentlyMoved = Rs2Player.isMoving()
									|| (routeState.lastMovedTimeMs > 0 && nowMs - routeState.lastMovedTimeMs < 1500);
							boolean makingRecentProgress = routeState.interimLastProgressAtMs > 0
									&& nowMs - routeState.interimLastProgressAtMs < INTERIM_PROGRESS_TIMEOUT_MS;
							boolean retargetCoolingDown = routeState.interimLastRetargetAtMs > 0
									&& nowMs - routeState.interimLastRetargetAtMs < INTERIM_RETARGET_COOLDOWN_MS;

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
                    WorldPoint rawRouteTarget = inInstance ? null
                            : selectRouteClickTarget(rawPath, playerLoc, MINIMAP_REACH_EUCLIDEAN - 1, rawAnchorIndex);
                    WorldPoint clickTarget;
                    String fallbackTag = "-";
                    if (rawRouteTarget != null && !rawRouteTarget.equals(playerLoc)) {
                        targetWp = rawRouteTarget;
                        clickTarget = rawRouteTarget;
                    } else {
                        clickTarget = inInstance ? targetWp : getPointWithWallDistance(targetWp, playerLoc);
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
                            smoothedToRaw, obstaclePolicy.segmentDoorTimeoutMs(),
                            playerLoc)) {
                        doorOrTransportResult = true;
                        exit = WalkExit.DOOR_HANDLED_BEFORE_MINIMAP_CLICK;
                        break;
                    }
                    WalkExit claimedDoorExit = resolveActiveDoorClaim(playerLoc,
                            obstaclePolicy.segmentDoorTimeoutMs(), rawPath);
                    if (claimedDoorExit != null) {
                        doorOrTransportResult = claimedDoorExit.isDoorLike();
                        exit = claimedDoorExit; break;
                    }
                    clickTarget = RouteRecovery.clampToEuclideanRadius(playerLoc, clickTarget, MINIMAP_REACH_EUCLIDEAN - 1);
                    long nowBeforeClick = System.currentTimeMillis();
                    if (routeState.lastTransportHandledAtMs > 0
                            && nowBeforeClick - routeState.lastTransportHandledAtMs <= POST_TRANSPORT_PATH_TMARK_WINDOW_MS) {
                        WebWalkLog.tmark("post_transport_path_selected",
                                nowBeforeClick - routeState.lastTransportHandledAtMs,
                                target,
                                posBefore,
                                "to=" + compactWorldPoint(clickTarget));
                    }
                    markStartupPhase("click_candidate_found", target,
                            "to=" + compactWorldPoint(clickTarget)
                                    + " sel=" + routeState.lastRouteClickTier + " fb=" + fallbackTag);
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
						routeState.interimTargetWp = clickedTarget;
						routeState.interimTargetIdx = targetIdx;
						routeState.interimSetAtMs = nowMs;
						routeState.interimLastProgressAtMs = nowMs;
						routeState.interimLastBestPathIdx = getClosestTileIndex(path, posBefore);
                        routeState.interimLastDistanceToTarget = distanceToInterimOrMax(clickedTarget, posBefore);
						routeState.interimLastRetargetAtMs = nowMs;

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
                                routeState.interimTargetWp = routeRetryTarget;
                                routeState.interimLastProgressAtMs = lastAttemptedMinimapClickAtMs;
                                routeState.interimLastDistanceToTarget = distanceToInterimOrMax(routeRetryTarget, before);
                                routeState.interimLastRetargetAtMs = lastAttemptedMinimapClickAtMs;
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
                                exit = WalkExit.POST_CLICK_RAW_PATH_SCENE_OBJECT_HANDLED;
                                break;
                            }
                            if (handleCurrentTileTransportTowardPath(rawPath, path, target)) {
                                doorOrTransportResult = true;
                                exit = WalkExit.POST_CLICK_CURRENT_TILE_TRANSPORT_HANDLED;
                                break;
                            }
                        }
                    }
                    // Keep stuck-detection honest: observed movement resets the movement timer.
                    // Without this, isStuckTooLong() fires after long successful walks because
                    // routeState.lastMovedTimeMs is only refreshed at processWalk entry (not during the loop).
                    if (posBefore.distanceTo2D(Rs2Player.getWorldLocation()) > 0) {
                        routeState.lastMovedTimeMs = System.currentTimeMillis();
                        routeState.stuckCount = 0;
                    }
                    // If the minimap click failed (target outside minimap radius), subsequent
                    // path tiles are further away and will also fail — break and let the outer
                    // loop wait for the player to walk closer before re-evaluating.
                    if (!clicked) {
                        exit = WalkExit.CLICK_FAILED_OFF_MINIMAP;
                        routeState.interimTargetWp = null;
                        routeState.interimTargetIdx = -1;
                        routeState.interimSetAtMs = 0L;
                        routeState.interimLastProgressAtMs = 0L;
                        routeState.interimLastBestPathIdx = -1;
                        routeState.interimLastDistanceToTarget = Integer.MAX_VALUE;
                        routeState.interimLastRetargetAtMs = 0L;
                        sleepUntil(() -> isWalkCancelled(target) || !Rs2Player.isMoving(), 1200);
                        if (walkCancelledDiag(target, "processWalk:after-click-failed-wait", processWalkTail)) {
                            return WalkerState.EXIT;
                        }
                        break;
                    }
                    // ONE CLICK PER PASS (task #25): advancing i meant 2-3 clicks per pass, each
                    // with its own post-click waits — 8-15s passes while the player stood at the
                    // first click's interim. End the pass; the next re-clicks ~1s after arrival.
                    exit = WalkExit.ROUTE_MOVE_IN_FLIGHT;
                    break;
                }
            }

            if (doorOrTransportResult && exit.isDoorLike()) {
                boolean canvasNudged = maybeCanvasNudgeAfterDoor(target, distance, path);
                // Arm after nudge returns so the window does not expire during in-nudge waits. The long
                // window exists to stop a minimap click landing on the heels of a CANVAS click, so it is
                // only owed when a canvas click actually happened — and the nudge deliberately declines
                // mid-route (see its final-approach guard), which is every ordinary door. Arming it
                // regardless froze movement for 2.2s after each one, and the idle nudge then supplied a
                // minimap click anyway: slower AND still minimap. Measured at Falador castle, door done
                // 15:25:46, idle nudge 15:25:48. The short window still keeps the next beat off the same
                // tick as the door interaction itself.
                routeState.suppressTryDirectShortWalkUntilMs = System.currentTimeMillis()
                        + (canvasNudged ? POST_DOOR_NUDGE_SUPPRESS_TRY_DIRECT_MS : POST_DOOR_NO_NUDGE_SUPPRESS_TRY_DIRECT_MS);
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

            if (exit != WalkExit.END_OF_PATH) {
                WebWalkLog.earlyExit(exit.wireName(offPathDeferDetail),
                        Rs2Player.getWorldLocation(),
                        target,
                        path.get(path.size() - 1),
                        indexOfStartPoint,
                        path.size());
                walkerDiag("early-exit detail reason=%s interim=%s doorOrTransport=%s partialPath=%s",
                        exit.wireName(offPathDeferDetail),
                        routeState.interimTargetWp,
                        doorOrTransportResult,
                        partialPath);
            }

            // Only do the final-tile canvas click if we iterated the whole path cleanly.
            // Exiting because the player left the path may still mean movement is active.
            // so don't clobber that destination.
            if (!doorOrTransportResult && exit == WalkExit.END_OF_PATH) {
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
                                    normalMinimapReach() - 1, rawAnchorIndex);
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
                    && exit == WalkExit.END_OF_PATH
                    && Rs2Player.isMoving()) {
                exit = WalkExit.ROUTE_MOVE_IN_FLIGHT;
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
                WorldPoint retryLoc = Rs2Player.getWorldLocation();
                boolean movedSinceLastRetry = lastPartialRetryAtLoc == null
                        || (retryLoc != null && !retryLoc.equals(lastPartialRetryAtLoc));
                if (TailDecision.shouldRefillPartialRetryBudget(partialRetriesWorking, movedSinceLastRetry,
                        routeState.routeProgressAdvancedAtMs, lastPartialRetryAtMs)) {
                    walkerDiag("partial retry budget refilled progressAt=%d lastRetryAt=%d spent=%d at=%s", routeState.routeProgressAdvancedAtMs, lastPartialRetryAtMs, partialRetriesWorking, retryLoc);
                    partialRetriesWorking = 0;
                }
                TailDecision.TailAction partialAction = TailDecision.decide(false, true, exit,
                        partialRetriesWorking, TailDecision.MAX_PARTIAL_RETRIES);
                if (partialAction == TailDecision.TailAction.PARTIAL_PROGRESS_REPLAN) {
                    walkerDiag("partial retry exempt exitReason=%s tail=%d spent=%d", exit.wireName(offPathDeferDetail), processWalkTail, partialRetriesWorking);
                    recalculatePath();
                    continue;
                }
                if (partialAction == TailDecision.TailAction.PARTIAL_RETRY_REPLAN) {
                    lastPartialRetryAtMs = System.currentTimeMillis();
                    lastPartialRetryAtLoc = retryLoc;
                    Telemetry.recordPartialRetry(partialRetriesWorking + 1, finalDist);
                    WebWalkLog.partialRetry(finalDist, partialRetriesWorking + 1, TailDecision.MAX_PARTIAL_RETRIES);
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
                        target, unreachableEndpoint, path.size(), distance,
                        Rs2PathApi.getActiveRouteStatus().getMetrics().orElse(null));
                setTarget(null, "rs2walker:processWalk:partial-retries-exhausted");
                return WalkerState.UNREACHABLE;
            } else {
                WalkerState stagnated = handleRouteStagnation(target, distance, path);
                if (stagnated != null) {
                    return stagnated;
                }
                if (exit == WalkExit.OFF_PATH_DEFERRED) {
                    // Wait briefly for the player to re-enter the path or for the progress signal
                    // that deferred the recalc to expire. Prevents a tight loop around isNearPath().
                    String deferReason = offPathDeferDetail;
                    long offPathWaitMs = offPathRecalcDeferredWaitMs(deferReason,
                            System.currentTimeMillis(),
                            routeState.lastMovedTimeMs,
                            routeState.routeProgressAdvancedAtMs,
                            lastAttemptedMinimapClickAtMs,
                            routeState.interimLastProgressAtMs);
                    long now = System.currentTimeMillis();
                    if (routeState.lastTransportHandledAtMs > 0
                            && now - routeState.lastTransportHandledAtMs <= POST_TRANSPORT_PATH_TMARK_WINDOW_MS) {
                        long elapsedSinceTransport = Math.max(0L, now - routeState.lastTransportHandledAtMs);
                        long remainingBudget = POST_TRANSPORT_OFFPATH_WAIT_BUDGET_MS - elapsedSinceTransport;
                        if (remainingBudget <= 0) {
                            offPathWaitMs = 0L;
                        } else {
                            offPathWaitMs = Math.min((long) POST_TRANSPORT_OFFPATH_WAIT_SLICE_MS, remainingBudget);
                        }
                    }
                    if (offPathWaitMs > 0) {
                        final long deferredLastClickAtMs = lastAttemptedMinimapClickAtMs;
                        if (routeState.lastTransportHandledAtMs > 0
                                && System.currentTimeMillis() - routeState.lastTransportHandledAtMs <= POST_TRANSPORT_PATH_TMARK_WINDOW_MS) {
                            WebWalkLog.tmark("post_transport_offpath_sleep",
                                    System.currentTimeMillis() - routeState.lastTransportHandledAtMs,
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
                if (exit.isTailExempt()) {
                    consecutiveExemptIterations = trackExemptRun(consecutiveExemptIterations, target, exit, offPathDeferDetail);
                    walkerDiag("tail exempt exitReason=%s tailBefore=%d", exit.wireName(offPathDeferDetail), processWalkTail);
                    processWalkTail--;
                } else {
                    consecutiveExemptIterations = 0;
                }
                walkerDiag("continue outer tail nextIdx=%d exitReason=%s finalDist=%d partialPath=%s",
                        processWalkTail + 1, exit.wireName(offPathDeferDetail),
                        Rs2Player.getWorldLocation().distanceTo(target), partialPath);
                continue;
            }
        } catch (Exception ex) {
            if (ex instanceof InterruptedException || ex.getCause() instanceof InterruptedException) {
                WebWalkLog.interruptedExit("pathfinder interrupted (397)");
                traceProcessWalkExit("interrupted-exception", target, MAX_PROCESS_WALK_TAIL_ITERATIONS - 1);
                setTarget(null, "rs2walker:processWalk:interrupted-exception");
                return WalkerState.EXIT;
            }
            if (isClientThreadReadTimeout(ex)
                    && clientThreadTimeoutRetries < CLIENT_THREAD_TIMEOUT_RETRIES
                    && Objects.equals(currentTarget, target)
                    && !Thread.currentThread().isInterrupted()) {
                int nextRetry = ++clientThreadTimeoutRetries;
                WebWalkLog.spInfo("client_thread_timeout_retry | attempt={}/{} target={}",
                        nextRetry, CLIENT_THREAD_TIMEOUT_RETRIES, target);
                processWalkTail--;
                continue;
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
                routeState.interimTargetWp,
                routeState.stuckCount,
                Rs2Player.getWorldLocation());
        WebWalkLog.tailExceeded(MAX_PROCESS_WALK_TAIL_ITERATIONS, target, currentTarget, routeState.interimTargetWp, routeState.stuckCount,
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
                            Math.max(2, normalMinimapReach())).keySet();

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

    static boolean isKnownWalkableOrUnloaded(WorldPoint target) {
        if (target == null) {
            return false;
        }

        LocalPoint localTarget = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), target);
        return localTarget == null || Rs2Tile.isWalkable(localTarget);
    }


    static boolean isWalkCancelled(WorldPoint target) {
        // The single choke point for stopping a walk: processWalk already consults it at every
        // checkpoint and inside the movement-wait predicates.
        if (InputArbiter.isHuman()) {
            return true;
        }
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
    static final int STAMINA_THRESHOLD_FALLBACK = 35;
    private static final int STAMINA_MIN_PATH_TILES = 20;
    private static final long STAMINA_MIN_INTERVAL_MS = 10_000;

    static volatile String staminaSeedName = null;
    static volatile int staminaThresholdCached = STAMINA_THRESHOLD_FALLBACK;

	// Door-scan cooldown state migrated to WalkerRouteState; the fallback/LOS timestamps that
	// used to sit here were dead (written by nothing, read by nothing) and are simply gone.
    /**
     * End-snap guard bounds: a recovery target within this Euclidean radius that is absent from the
     * player-origin reachability BFS is walled/doored off (the recovery BFS's 18-step budget comfortably
     * covers the full ~9-tile recovery click radius; a connected tile needing more steps than that inside
     * this radius is a pathological fold where replanning is also the right answer), so recovery replans
     * instead of clicking through the wall; the cooldown stops replans looping while the fresh path
     * computes. Covers the whole recovery click range — Clock Tower showed goal-clicks from 9 tiles out.
     */
    private static final int WALLED_RECOVERY_TARGET_EUCLIDEAN = 9;
    private static final long WALLED_RECOVERY_REPLAN_COOLDOWN_MS = 5_000L;




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

    /**
     * Explicit-zoom variant, kept for external callers that genuinely want a particular zoom. The
     * walker itself never uses it: {@code Perspective.localToMinimap} reads the LIVE zoom, so the
     * click math is correct at any setting, and pinning the minimap at max zoom on every click both
     * looked bot-like and fought the user's own zoom the moment they changed it.
     */
    public static boolean walkMiniMap(WorldPoint worldPoint, double zoomDistance) {
        if (Microbot.getClient().getMinimapZoom() != zoomDistance)
            Microbot.getClient().setMinimapZoom(zoomDistance);
        return walkMiniMap(worldPoint);
    }

    /**
     * Clicks {@code worldPoint} on the minimap at whatever zoom the user has. Zoom only moves the
     * trade-off between reach and pixel precision — zoomed IN shrinks clickable range (~16 tiles at
     * zoom 5, ~40 zoomed out), zoomed out shrinks pixels-per-tile — and every caller already has a
     * fallback for an unclickable point (nearer route point, canvas click), which is exactly what a
     * human at that zoom would do. Tile-exact clicks near walls use the canvas path, which is
     * pixel-precise at any zoom.
     */
    public static boolean walkMiniMap(WorldPoint worldPoint) {
        Point point = Rs2MiniMap.worldToMinimap(worldPoint);

        if (point == null) return false;
        if (!disableWalkerUpdate && !Rs2MiniMap.isPointInsideMinimap(point)) return false;

        Microbot.getMouse().click(point);
        return true;
    }









    /**
     * Live-click variant of {@link #findFurthestRawPathPointMatching} with the route-blocked scan gate: it
     * additionally supplies the player-origin reachability BFS so forward click selection stops at the near
     * side of a closed door / wall ON the route instead of selecting statically-walkable tiles beyond it
     * (which made the server path the player AROUND buildings — the Clock Tower off-route bug). Kept
     * separate from the ungated wrapper because the gate reads live game state (the BFS), which the
     * pure-selection unit tests must not depend on.
     */
    static WorldPoint findFurthestRawPathPointMatchingGated(List<WorldPoint> rawPath,
                                                                    WorldPoint playerLoc,
                                                                    int maxEuclidean,
                                                                    int rawAnchorIndex,
                                                                    Predicate<WorldPoint> isCandidate) {
        Map<WorldPoint, Integer> reachable = getClosestIndexReachableTiles(playerLoc);
        WorldPoint selected = WalkerPathGeometry.findFurthestRawPathPointMatching(rawPath, playerLoc, maxEuclidean,
                rawAnchorIndex, isCandidate, ROUTE_PROGRESS_FORWARD_SEARCH_TILES,
                () -> getClosestTileIndex(rawPath, playerLoc),
                reachable, CLOSEST_INDEX_REACHABLE_STEP_BUDGET);
        // Output-side net: whatever path the scan took (stale anchor, player-anchored retry, a fold the
        // along-route gate could not vouch for), a selected tile that is Euclidean-NEAR the player yet
        // absent from the player-origin BFS is on the far side of a wall/door — clicking it walks the
        // player into the wall (Wydin's shop: first click chose the goal 2 tiles away through the
        // back-room wall and the walk unravelled from there). Refuse it; every caller has a
        // reachability-aware fallback (wall-nudge clamp, rejoin, recovery). The log carries the anchor
        // context so a recurrence is diagnosable from a single line.
        if (selected != null && reachable != null && !reachable.isEmpty()
                && !reachable.containsKey(selected)
                && playerLoc != null
                && playerLoc.distanceTo2D(selected) <= CLOSEST_INDEX_REACHABLE_STEP_BUDGET - 2) {
            WebWalkLog.spInfo("route_click_walled | to={} player={} anchorIdx={} — refused, falling back",
                    compactWorldPoint(selected), compactWorldPoint(playerLoc), rawAnchorIndex);
            learnWalledRouteEdge(rawPath, playerLoc, reachable);
            return null;
        }
        return selected;
    }








    /**
     * Which tier of {@link #selectRouteClickTarget} produced the most recent click target
     * ({@code los} / {@code reach} / {@code offscene} / {@code none}, or {@code wallnudge} /
     * {@code rejoin} when selection fell through). Surfaced in the {@code click_candidate_found}
     * tmark so a log alone shows which selection path a click came from.
     */
    // routeState.lastRouteClickTier migrated to WalkerRouteState (see routeState)




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
    // findReachableRejoinRawPathPoint (pure core) moved to geometry/WalkerPathGeometry (P1); same-signature
    // wrapper so Rs2WalkerUnitTest and callers are untouched.
    static WorldPoint findReachableRejoinRawPathPoint(List<WorldPoint> rawPath, WorldPoint playerLoc,
                                                      int maxEuclidean, int rawAnchorIndex,
                                                      Predicate<WorldPoint> isReachable) {
        return WalkerPathGeometry.findReachableRejoinRawPathPoint(rawPath, playerLoc, maxEuclidean,
                rawAnchorIndex, isReachable, ROUTE_PROGRESS_FORWARD_SEARCH_TILES,
                () -> getClosestTileIndex(rawPath, playerLoc));
    }



    // rawPathStepDistance (pure) moved to geometry/WalkerPathGeometry (P1) alongside its only caller,
    // findFurthestRawPathPointMatching; no remaining Rs2Walker callers.

    // isLocalRecoveryCandidateOnForwardRoute extracted to recovery/RouteRecovery (P1)

    // rawPathForwardAnchorIndex (pure) moved to geometry/WalkerPathGeometry (P1); this game-coupled wrapper
    // supplies the forward-search window constant and the lazy reachable-closest fallback.
    static int rawPathForwardAnchorIndex(List<WorldPoint> rawPath, WorldPoint playerLoc, int rawAnchorIndex) {
        return WalkerPathGeometry.rawPathForwardAnchorIndex(rawPath, playerLoc, rawAnchorIndex,
                ROUTE_PROGRESS_FORWARD_SEARCH_TILES, () -> getClosestTileIndex(rawPath, playerLoc));
    }

    /**
     * The local-recovery scan anchor, forward-corrected past route tiles the player has already
     * passed (FrontierDecision.forwardScanStartIndex). The player's raw position is found with the
     * forward-window search, not plain-nearest, so a route tail folding back beside the player
     * (Clock Tower) cannot yank the anchor to the end of the route.
     */
    private static int forwardRecoveryScanStart(List<WorldPoint> rawPath, int[] smoothedToRaw,
                                                int indexOfStartPoint, WorldPoint playerLoc) {
        if (rawPath == null || rawPath.isEmpty() || smoothedToRaw == null || playerLoc == null
                || indexOfStartPoint < 0 || indexOfStartPoint >= smoothedToRaw.length
                || smoothedToRaw[indexOfStartPoint] < 0) {
            return indexOfStartPoint;
        }
        int playerRawIdx = rawPathForwardAnchorIndex(rawPath, playerLoc, smoothedToRaw[indexOfStartPoint]);
        return FrontierDecision.forwardScanStartIndex(smoothedToRaw, indexOfStartPoint, playerRawIdx);
    }




    static boolean tryIssueRouteMovementClick(List<WorldPoint> rawPath,
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
			int clickableIdx = RouteRecovery.findFurthestForwardClickableIndex(path, startIdx, playerLoc,
					Rs2PathApi::hasCatalogTransportOrigin,
                    maxEuclidean);
            clickableIdx = Math.max(startIdx, Math.min(clickableIdx, path.size() - 1));
            clickTarget = path.get(clickableIdx);
            targetIdx = clickableIdx;
            if (euclideanSq(clickTarget, playerLoc)
                    > maxEuclidean * maxEuclidean) {
                clickTarget = RouteRecovery.interpolateClickableTarget(
                        path,
                        startIdx,
                        playerLoc,
                        clickTarget,
                        maxEuclidean - 1,
                        Rs2Walker::isKnownWalkableOrUnloaded);
            }
            // The primary selector reaches here only after refusing every route point (e.g. the
            // walled net saw a shut door between), and this fallback vets candidates by
            // WALKABILITY, not reachability. Clicking a walkable-but-unreachable tile moves the
            // player nowhere while still arming an interim — at the Stronghold's chained gates the
            // idle nudge did exactly that every ~2s beyond the shut second gate, and the dead
            // interim's in-flight yields starved the pass that would have opened it.
            if (clickTarget != null && !Rs2Tile.isTileReachable(clickTarget)) {
                WebWalkLog.spDebug("route_click_fallback_unreachable | to={} player={}",
                        compactWorldPoint(clickTarget), compactWorldPoint(playerLoc));
                return false;
            }
        }

        boolean clicked = false;
        WorldPoint clickedTarget = null;
        if (clickTarget != null && !clickTarget.equals(playerLoc)) {
            clickTarget = RouteRecovery.clampToEuclideanRadius(playerLoc, clickTarget, maxEuclidean - 1);
            // The finish needs scene precision, not minimap reach. A minimap tile is a few pixels
            // wide, so a click at the goal from 1-2 tiles out routinely quantizes onto a neighbour —
            // measured as the last-tile dance (1784,3559 -> 1786,3559 -> 1784,3560 around a
            // 1785,3560 goal). Inside the final band, click the exact tile on screen instead.
            if (target != null && playerLoc.distanceTo2D(target) <= INTERIM_CLOSE_TILES
                    && clickTarget.getPlane() == target.getPlane()
                    && clickTarget.distanceTo2D(target) <= 1
                    && walkFastCanvas(clickTarget)) {
                clickedTarget = clickTarget;
                clicked = true;
            } else {
                clickedTarget = clickMiniMapOrFallback(rawPath, clickTarget, playerLoc,
                        maxEuclidean - 1, rawPath == null || rawPath.isEmpty(), rawAnchorIndex);
                clicked = clickedTarget != null;
            }
        }
        // EVERY movement click logs at info. The interim-continuation label used to log at debug only,
        // which made its clicks invisible: the walker appeared to "randomly click far from the path"
        // and ran for minutes with no clicks in the log while continuation clicks silently chained
        // (interim expires -> unlogged click -> player moving -> off-path recalc deferred -> repeat).
        log.info("[Walker] {}: clicked={} to={} player={} idx={}",
                logLabel, clicked, clicked ? clickedTarget : clickTarget, playerLoc, targetIdx);
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
        routeState.interimTargetWp = clickedTarget;
        routeState.interimTargetIdx = targetIdx;
        routeState.interimSetAtMs = System.currentTimeMillis();
        routeState.interimLastProgressAtMs = routeState.interimSetAtMs;
        routeState.interimLastBestPathIdx = getClosestTileIndex(path, playerLoc);
        routeState.interimLastDistanceToTarget = distanceToInterimOrMax(clickedTarget, playerLoc);
        routeState.interimLastRetargetAtMs = routeState.interimSetAtMs;
        if (markRecoveryCooldown) {
            routeState.lastUnreachableRecoveryClickAtMs = routeState.interimSetAtMs;
        }
        if ("active route idle nudge".equals(logLabel)) {
            routeState.lastActiveRouteIdleNudgeAtMs = routeState.interimSetAtMs;
        } else {
            routeState.lastMovedTimeMs = routeState.interimSetAtMs;
        }
        routeState.idleNudgeStationarySinceMs = routeState.interimSetAtMs;
        routeState.idleNudgeLastObservedLocation = playerLoc;
        routeState.stuckCount = 0;
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
        Rs2RouteResult route = Rs2PathApi.plan(Rs2RouteRequest.to(start, destination));
        List<WorldPoint> path = route.getPath();
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
        WorldArea pathArea = null;

        // Create centered WorldArea for the object instead of corner-based
        WorldPoint objectSouthWest = new WorldPoint(
                worldPoint.getX() - (sizeX + 2) / 2,
                worldPoint.getY() - (sizeY + 2) / 2,
                worldPoint.getPlane()
        );
        WorldArea objectArea = new WorldArea(objectSouthWest, sizeX + 2, sizeY + 2);

        try {
            Rs2RouteResult route = Rs2PathApi.plan(
                    Rs2RouteRequest.to(Rs2Player.getWorldLocation(), worldPoint)
                            .withRefreshTarget(worldPoint)
                            .withBankItems(useBankedItems));

            // Create centered WorldArea for the path endpoint instead of corner-based
            WorldPoint pathEndpoint = route.getEndpoint().orElseThrow(
                    () -> new IllegalStateException("planner returned no endpoint"));
            WorldPoint pathSouthWest = new WorldPoint(
                    pathEndpoint.getX() - pathSizeX / 2,
                    pathEndpoint.getY() - pathSizeY / 2,
                    pathEndpoint.getPlane()
            );
            pathArea = new WorldArea(pathSouthWest, pathSizeX, pathSizeY);
        } catch (Exception e) {
            log.trace("Exception in canReach: {} - ", e.getMessage(), e);
            return false;
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
        Rs2RouteResult route = Rs2PathApi.plan(
                Rs2RouteRequest.to(start, target)
                        .withRefreshTarget(target)
                        .withRefreshPolicy(Rs2RouteRequest.RefreshPolicy.ALWAYS));
        List<WorldPoint> path = route.getPath();
        long totalEndTime = System.nanoTime();
        double pathfinderTimeMs = route.hasSearchNanos()
                ? route.getSearchNanos() / 1_000_000.0
                : 0.0;
        double totalTimeMs = (totalEndTime - startTime) / 1_000_000.0;
        double configTimeMs = Math.max(0.0, totalTimeMs - pathfinderTimeMs);

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
        return Rs2WalkerBankingPlanner.applyTransportFiltering(transports);
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



    /** Applies a rockfall handling outcome to walker route state (facade side of the extraction). */
    /** Stateless obstacle resolver for the P2 unified dispatch (rockfall mining on a planned edge). */
    private static final MineableResolver MINEABLE_RESOLVER = new MineableResolver();

    /**
     * Rockfall resolution over a raw-path segment via {@link MineableResolver}, skipping steps whose both
     * ends are already reachable (no blocker there). First non-{@code NOT_APPLICABLE} result wins. This is
     * the single home for the former {@code handleRockfallInRawSegment}: the same per-edge {@code handleRockfall}
     * over the same tiles with the same skip rule, so behaviourally identical — now expressed in the P2
     * unified model. Returns {@link ObstacleResolution.Kind#INTERACTED} (mined), {@code ABORT} (no pickaxe),
     * or {@code NOT_APPLICABLE}.
     */
    private static ObstacleResolution resolveRockfallOnSegment(List<WorldPoint> rawPath, int rawFrom,
                                                               int rawTo, Map<WorldPoint, Integer> reachableCache) {
        if (rawPath == null || rawPath.isEmpty()) {
            return ObstacleResolution.notApplicable();
        }
        int scanTo = Math.min(rawTo, rawPath.size() - 1);
        for (int ri = Math.max(0, rawFrom); ri < scanTo; ri++) {
            WorldPoint a = rawPath.get(ri);
            WorldPoint b = rawPath.get(ri + 1);
            if (reachableCache != null && reachableCache.containsKey(a) && reachableCache.containsKey(b)) {
                continue;
            }
            ObstacleResolution mineable = MINEABLE_RESOLVER.resolve(new PlannedEdge(a, b), null, null);
            if (mineable.kind() != ObstacleResolution.Kind.NOT_APPLICABLE) {
                return mineable;
            }
        }
        return ObstacleResolution.notApplicable();
    }

    /**
     * Single-edge rockfall resolution for {@code rawPath[i] -> rawPath[i+1]} via {@link MineableResolver}
     * (the former {@code handleRockfall(rawPath, i)} in the unified model). {@code NOT_APPLICABLE} when
     * {@code i} is the last index, matching the old {@code index == size-1} guard.
     */
    private static ObstacleResolution resolveRockfallOnEdge(List<WorldPoint> rawPath, int i) {
        if (rawPath == null || i < 0 || i + 1 >= rawPath.size()) {
            return ObstacleResolution.notApplicable();
        }
        return MINEABLE_RESOLVER.resolve(new PlannedEdge(rawPath.get(i), rawPath.get(i + 1)), null, null);
    }

    /**
     * Unified obstacle resolution for a blocked route frontier (P2 dispatch cutover;
     * docs/walker-p2-unification.md). Replaces the recovery block's two special cases — the inline
     * {@code handleRockfallInRawSegment} mine and the {@code findReachableTransportApproachAhead} override —
     * with a single call returning one {@link ObstacleResolution}:
     * <ul>
     *   <li>{@link ObstacleResolution.Kind#INTERACTED} / {@link ObstacleResolution.Kind#ABORT}: a rockfall on
     *       the blocked segment was mined (or couldn't be, for lack of a pickaxe). The narrow segment scan,
     *       skipping already-reachable steps, is behaviourally identical to the former
     *       {@code handleRockfallInRawSegment} (same per-edge {@code handleRockfall}, same skip rule) — it
     *       just routes through {@link MineableResolver}.</li>
     *   <li>{@link ObstacleResolution.Kind#WALK_TO_ORIGIN}: a transport / agility-shortcut sits ahead within
     *       minimap reach; the caller should step onto its reachable origin, or the last reachable route tile
     *       before an object-occupied origin, so the normal transport handler crosses next tick. This reuses
     *       the pure, tested {@link RouteRecovery#findReachableTransportApproachAhead} scan and lifts its
     *       result into the unified model.</li>
     *   <li>{@link ObstacleResolution.Kind#NOT_APPLICABLE}: no obstacle here; fall through to door/minimap
     *       recovery.</li>
     * </ul>
     * Reads live game state (mines, scene/transport lookups) so it must run on the client thread.
     */
    private static ObstacleResolution resolveRecoveryObstacle(List<WorldPoint> rawPath, int rawEdgeStart,
                                                              int rawEdgeEnd, WorldPoint playerLoc,
                                                              int recoveryMinimapReach,
                                                              Map<WorldPoint, Integer> reachableTilesCache,
                                                              boolean inInstance) {
        if (rawPath == null || rawPath.isEmpty() || playerLoc == null) {
            return ObstacleResolution.notApplicable();
        }

        // (1) Rockfall on the blocked frontier: narrow segment scan (shared with the forward walk loop).
        ObstacleResolution rockfall = resolveRockfallOnSegment(rawPath, rawEdgeStart, rawEdgeEnd, reachableTilesCache);
        if (rockfall.kind() != ObstacleResolution.Kind.NOT_APPLICABLE) {
            return rockfall;
        }
        if (reachableTilesCache == null) {
            return ObstacleResolution.notApplicable();
        }

        int playerRawIdx = getClosestTileIndex(rawPath, playerLoc);

        // (2) A transport/agility shortcut whose ORIGIN the player is already standing on/beside.
        // TransportResolver declines this case by design ("the normal loop owns it") — but when the
        // blocked frontier IS the shortcut's far side, the normal loop never reaches its transport
        // dispatch: the reachability miss sends every tick into recovery, whose frontier-derived segment
        // window can miss the transport step entirely, and the player oscillates on the origin forever
        // (Falador crumbling wall). Dispatch the transport right here, scanning a small window around the
        // player's TRUE raw index rather than the smoothed-derived edge window.
        boolean allowTransportDispatch = PohTeleports.isInHouse() || !inInstance;
        if (playerRawIdx >= 0 && allowTransportDispatch) {
            int scanFrom = Math.max(0, playerRawIdx - RAW_TRANSPORT_DISPATCH_MAX_DISTANCE);
            int scanTo = Math.min(rawPath.size() - 1, playerRawIdx + ROUTE_PROGRESS_FORWARD_SEARCH_TILES);
            for (int ri = scanFrom; ri < scanTo; ri++) {
                if (hasExplicitTransportStep(rawPath, ri)
                        && isRawTransportOriginNearPlayer(rawPath, ri, playerLoc, RAW_TRANSPORT_DISPATCH_MAX_DISTANCE)) {
                    WebWalkLog.spInfo("recovery_on_origin_transport | origin={} player={} rawIdx={}",
                            compactWorldPoint(rawPath.get(ri)), compactWorldPoint(playerLoc), ri);
                    if (handleTransports(rawPath, ri)) {
                        return ObstacleResolution.interacted();
                    }
                }
            }
        }

        // (3) Reachable transport approach ahead: use the origin when standable, otherwise the final
        // reachable raw-route tile before an object-occupied origin (fairy rings are the common case).
		WorldPoint transportApproach = RouteRecovery.findReachableTransportApproachAhead(
				rawPath, playerRawIdx, playerLoc, reachableTilesCache.keySet(),
				(from, to) -> Rs2PathApi.getActiveTransportEdge(from, to).isPresent(),
                recoveryMinimapReach - 1, ROUTE_PROGRESS_FORWARD_SEARCH_TILES);
        if (transportApproach != null && !transportApproach.equals(playerLoc)) {
            WebWalkLog.spInfo("recovery_transport_approach | to={} player={}",
                    compactWorldPoint(transportApproach), compactWorldPoint(playerLoc));
            return ObstacleResolution.walkToOrigin(transportApproach);
        }

        return ObstacleResolution.notApplicable();
    }


    static int rawAnchorIndexForPathPosition(List<WorldPoint> rawPath,
                                                     List<WorldPoint> path,
                                                     WorldPoint playerLoc) {
        int closestPathIdx = getClosestTileIndex(path, playerLoc);
        int[] smoothedToRaw = mapSmoothedToRaw(path, rawPath);
        int rawAnchorIndex = rawIndexForSmoothedIndex(closestPathIdx, smoothedToRaw, rawPath);
        return rawPathForwardAnchorIndex(rawPath, playerLoc, rawAnchorIndex);
    }







    private static boolean handlePendingDoorBeforeRouteClick(List<WorldPoint> rawPath,
                                                             List<WorldPoint> path,
                                                             int fromPathIdx,
                                                             int targetPathIdx,
                                                             int[] smoothedToRaw,
                                                             long timeoutMs,
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



    private static boolean handleUnresolvedDoorNearRawPath(List<WorldPoint> rawPath,
                                                           int rawEdgeStart,
                                                           long timeoutMs,
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
            if (shouldDeferDoorHandlingToTransport(rawPath, ri)) {
                continue;
            }
            if (!hasUnresolvedDoorLikeSceneObjectOnSegment(from, to, playerLoc, radiusTiles)) {
                continue;
            }
            if (handleDoorsWithTimeoutBudgeted(rawPath, ri, timeoutMs, true)) {
                return true;
            }
        }
        return false;
    }

    // rawIndexForSmoothedIndex (pure) moved to geometry/WalkerPathGeometry (P1); wrapper supplies the lazy
    // closest-index fallback (only used when the smoothedToRaw table can't map the index).
    private static int rawIndexForSmoothedIndex(int smoothedIdx, int[] smoothedToRaw, List<WorldPoint> rawPath) {
        return WalkerPathGeometry.rawIndexForSmoothedIndex(smoothedIdx, smoothedToRaw, rawPath,
                () -> getClosestTileIndex(rawPath));
    }

    /**
     * Why the last raw scene scan returned without scanning. The scan bails at several guards before
     * it captures its snapshot, so a {@code handled=false ms=0} tmark says nothing about WHICH guard
     * stopped it — and that is exactly the gap that left the second staircase unhandled while the
     * idle nudge walked onto its origin instead.
     */
    private static volatile String lastRawScanEarlyReturn = "none";

    private static boolean handleNearbyRawPathSceneObjects(List<WorldPoint> rawPath, int handlerRange, WorldPoint target) {
        return handleNearbyRawPathSceneObjects(rawPath, handlerRange, target, true);
    }

    private static boolean handleNearbyRawPathSceneObjects(List<WorldPoint> rawPath,
                                                          int handlerRange,
                                                          WorldPoint target,
                                                          boolean allowTransportHandlers) {
        long passT0 = System.currentTimeMillis();
        try {
            return handleNearbyRawPathSceneObjectsInner(rawPath, handlerRange, target, allowTransportHandlers);
        } finally {
            WalkPassStats.rawSceneScanMs.addAndGet(System.currentTimeMillis() - passT0);
        }
    }

    private static boolean handleNearbyRawPathSceneObjectsInner(List<WorldPoint> rawPath,
                                                          int handlerRange,
                                                          WorldPoint target,
                                                          boolean allowTransportHandlers) {
        if (rawPath == null || rawPath.size() < 2) {
            return false;
        }

        if (isRecoveryMovementInFlight()) {
            lastRawScanEarlyReturn = "recovery-move-in-flight";
            return false;
        }

        if (routeState.interimTargetWp != null) {
            clearRawScanDoorFocus("interim-active");
            lastRawScanEarlyReturn = "interim-active";
            return false;
        }

        if (Rs2Player.isMoving()) {
            lastRawScanEarlyReturn = "moving";
            return false;
        }

        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        if (playerLoc == null) {
            lastRawScanEarlyReturn = "player-null";
            return false;
        }

        int rawStart = getClosestTileIndex(rawPath, playerLoc);
        if (rawStart < 0) {
            clearRawScanDoorFocus("raw-start-missing");
            lastRawScanEarlyReturn = "raw-start-missing";
            return false;
        }

        if (shouldUseFocusedRawDoorIndex(rawPath, rawStart)) {
            int idx = doorAttemptLedger.rawScanFocusDoorIdx();
            doorAttemptLedger.recordRawScanFocusAttempt();
            if (handleDoors(rawPath, idx, true)) {
                log.info("[Walker] Raw path focused door handler resolved obstacle near {}", playerLoc);
                return true;
            }
            return false;
        }
        clearRawScanDoorFocus("focus-invalid");

        // Bound recovery cost (P0b): a full scan that resolved nothing will resolve nothing again
        // until the scene or the player's tile changes. Skip repeat full scans within a short window
        // at the same tile so one un-handleable blocker can't cause per-tick multi-second stalls —
        // the stall-recalc / live-collision recalc path takes over instead. Door retries are
        // unaffected (they run via the focused-index short-circuit above, not the full scan).
        long rawScanNowMs = System.currentTimeMillis();
        if (lastEmptyRawScanAtMs > 0L
                && rawScanNowMs - lastEmptyRawScanAtMs < EMPTY_RAW_SCAN_COOLDOWN_MS
                && playerLoc.equals(lastEmptyRawScanPlayerLoc)) {
            lastRawScanEarlyReturn = "empty-scan-cooldown";
            return false;
        }
        lastRawScanEarlyReturn = "ran";

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
        rawScanDoorEligibilityCache = new IdentityHashMap<>();
        rawScanDoorInteractionWaitMs = 0L;
        rawScanDoorEdgeWaitMs = 0L;
        rawScanDoorFindMs = 0L;
        rawScanDoorInteractMs = 0L;
        rawScanDoorVerifyMs = 0L;
        // Route order guard for ranged transport dispatch: set once a transport step is passed over,
        // so nothing further along the route can be actioned ahead of the obstacle in front of us.
        boolean sawUndispatchedTransportStep = false;
        final boolean inInstanceScan = Microbot.getClientThread()
                .runOnClientThreadOptional(() -> Microbot.getClient().getTopLevelWorldView().isInstance())
                .orElse(Boolean.TRUE);
        try {
            for (int i = start; i < endExclusive; i++) {
                WorldPoint currentWorldPoint = rawPath.get(i);
                if (currentWorldPoint == null
                        || currentWorldPoint.getPlane() != playerLoc.getPlane()
                        || currentWorldPoint.distanceTo2D(playerLoc) > handlerRange) {
                    continue;
                }
                scannedIdx++;

                if (allowTransportHandlers && hasExplicitTransportStep(rawPath, i)) {
                    WorldPoint routeOrigin = rawPath.get(i);
                    WorldPoint expectedDest = i + 1 < rawPath.size() ? rawPath.get(i + 1) : null;
                    int originDistance = routeOrigin != null && routeOrigin.getPlane() == playerLoc.getPlane()
                            ? routeOrigin.distanceTo2D(playerLoc)
                            : -1;
                    boolean rangedAllowed = shouldDispatchTransportAtRange(
                            originDistance,
                            RAW_TRANSPORT_DISPATCH_MAX_DISTANCE,
                            handlerRange,
                            !sawUndispatchedTransportStep,
                            isObjectInteractionTransportStep(rawPath, i),
                            inInstanceScan,
                            isDoorInteractionSettling() || isTransportInteractionSettling(),
                            rangedTransportEdgeFailedRecently(routeOrigin, expectedDest),
                            rangedTransportDispatchEnabled());
                    if (!rangedAllowed) {
                        // Declining here must not let a FURTHER transport be actioned first, or the
                        // walker skips the obstacle in front of it. Later indices lose the ranged branch.
                        sawUndispatchedTransportStep = true;
                    }
                    if (rangedAllowed) {
                    boolean ranged = originDistance > RAW_TRANSPORT_DISPATCH_MAX_DISTANCE;
                    WorldPoint before = Rs2Player.getWorldLocation();
                    WorldPoint expectedDestination = expectedDest;
                    long t = System.currentTimeMillis();
                    if (ranged) {
                        WebWalkLog.spInfo("ranged_transport_dispatch | origin={} dist={} — clicking from range, server walks us",
                                compactWorldPoint(routeOrigin), originDistance);
                    }
                    boolean handledTransport = handleTransports(rawPath, i, ranged);
                    transportMs += System.currentTimeMillis() - t;
                    if (handledTransport) {
                        if (!didCurrentTileTransportProgress(before, expectedDestination, target)) {
                            WebWalkLog.spInfo("raw_path_transport_no_progress",
                                    "at=%s expected=%s target=%s",
                                    before, expectedDestination, target);
                            if (ranged) {
                                markRangedTransportEdgeFailed(routeOrigin, expectedDestination);
                            }
                        } else {
                            log.info("[Walker] Raw path transport handler resolved obstacle near {}", playerLoc);
                            resolved = true;
                            return true;
                        }
                    }
                    // Reaching here means the attempt did NOT resolve — handleTransports declined, or
                    // it reported success without moving us. Either way this transport is still in the
                    // way, so it must block a ranged dispatch at a later index for exactly the same
                    // reason a declined one does; otherwise the walker reaches past the obstacle in
                    // front of it and the server paths around.
                    sawUndispatchedTransportStep = true;
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
                // P2 unified rockfall dispatch (single edge). INTERACTED == the former MINED; ABORT
                // (no pickaxe) clears the target and is not treated as handled, matching applyRockfall.
                ObstacleResolution rockfall = resolveRockfallOnEdge(rawPath, i);
                if (rockfall.kind() == ObstacleResolution.Kind.ABORT) {
                    setTarget(null, "rs2walker:" + rockfall.reason());
                }
                boolean handledRockfall = rockfall.kind() == ObstacleResolution.Kind.INTERACTED;
                rockfallMs += System.currentTimeMillis() - t2;
                if (handledRockfall) {
                    log.info("[Walker] Raw path rockfall handler resolved obstacle near {}", playerLoc);
                    resolved = true;
                    return true;
                }
            }

            // Scanned everything in range and found nothing actionable — start the cooldown so we
            // don't repeat this whole scan next tick from the same tile.
            lastEmptyRawScanAtMs = System.currentTimeMillis();
            lastEmptyRawScanPlayerLoc = playerLoc;
            return false;
        } finally {
            rawScanWallSnapshot = null;
            rawScanGameObjectSnapshot = null;
            rawScanDoorLocationSnapshot = null;
            rawScanDoorCompositionCache = null;
            rawScanDoorSegmentCache = null;
            rawScanDoorEligibilityCache = null;
            long totalMs = System.currentTimeMillis() - scanStartMs;
            if (totalMs >= SLOW_RAW_SCENE_SCAN_LOG_MS) {
                long doorWaitMs = rawScanDoorInteractionWaitMs;
                long doorEdgeWaitMs = rawScanDoorEdgeWaitMs;
                long doorFindMs = rawScanDoorFindMs;
                // What is left after the probe and both waits: the menu interaction and the
                // post-interaction verification. Previously all of this was reported as "doorProbe".
                long doorInteractMs = rawScanDoorInteractMs;
                long doorVerifyMs = rawScanDoorVerifyMs;
                long doorOtherMs = Math.max(0L, doorMs - doorWaitMs - doorEdgeWaitMs - doorFindMs
                        - doorInteractMs - doorVerifyMs);
                log.info("[Walker] slow raw scene scan: total={}ms idx={} snapshot={}ms doorFind={}ms doorInteract={}ms doorVerify={}ms doorEdgeWait={}ms doorOther={}ms doorWait={}ms doorCand={}ms rockfall={}ms transports={}ms resolved={} allowTransports={}",
                        totalMs, scannedIdx, snapshotMs, doorFindMs, doorInteractMs, doorVerifyMs, doorEdgeWaitMs, doorOtherMs, doorWaitMs, doorCandidateMs, rockfallMs, transportMs,
                        resolved, allowTransportHandlers);
            }
            rawScanDoorInteractionWaitMs = 0L;
            rawScanDoorEdgeWaitMs = 0L;
            rawScanDoorFindMs = 0L;
            rawScanDoorInteractMs = 0L;
            rawScanDoorVerifyMs = 0L;
        }
    }

    /** A raw-scene scan slower than this is user-visible dead time between route clicks. */
    private static final long SLOW_RAW_SCENE_SCAN_LOG_MS = 500L;

    // Throttle repeated full raw-scene scans that resolve nothing at the same tile (P0b).
    private static final long EMPTY_RAW_SCAN_COOLDOWN_MS = 1500L;
    private static long lastEmptyRawScanAtMs = 0L;
    private static WorldPoint lastEmptyRawScanPlayerLoc = null;

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
    static volatile List<WallObject> rawScanWallSnapshot = null;
    static volatile List<GameObject> rawScanGameObjectSnapshot = null;
    /** Immutable locations copied on the client thread for off-thread snapshot filtering. */
    static Map<TileObject, WorldPoint> rawScanDoorLocationSnapshot = null;
    /** Object definitions and segment matches are stable for one immutable scene snapshot. */
    static Map<TileObject, Optional<ObjectComposition>> rawScanDoorCompositionCache = null;
    static Map<String, Optional<TileObject>> rawScanDoorSegmentCache = null;
    /** Scan-scoped memo for the segment-independent door-candidate test (see DoorProbeContext). */
    static Map<TileObject, Boolean> rawScanDoorEligibilityCache = null;

    /** Interaction/edge-resolution wait contained inside {@link #handleDoors}; excluded from probe cost. */
    static volatile long rawScanDoorInteractionWaitMs = 0L;
    /** Time inside {@link #waitForDoorEdgeResolution} during a raw scan (a wait, not probe work). */
    static volatile long rawScanDoorEdgeWaitMs = 0L;
    /** Time inside the door segment probe during a raw scan (the actual geometry/snapshot work). */
    static volatile long rawScanDoorFindMs = 0L;
    /** Time spent issuing the door menu click itself (composition resolve + menu entry + mouse). */
    static volatile long rawScanDoorInteractMs = 0L;
    /** Time spent verifying the outcome: traversal check, and the re-scan that asks if it is still shut. */
    static volatile long rawScanDoorVerifyMs = 0L;


    // ---- Per-leg door stage accumulators (every door path, not just raw scans). The eleven-gate
    // Stronghold run produced a suspiciously CONSTANT ~5.4s per door_interaction_done with zero
    // slow-await lines, so the time lives outside the await, and the raw-scan breakdown only covers
    // one of the three entry paths. Reset at handleDoorsWithTimeout entry; printed on its tmark.
    static volatile long doorLegFindMs;
    static volatile long doorLegInteractMs;
    static volatile long doorLegAwaitMs;
    static volatile long doorLegVerifyMs;
    static volatile long doorLegNudgeMs;
    static volatile long doorLegExceptionMs;












    private static boolean handleCurrentTileTransportTowardPath(List<WorldPoint> rawPath, List<WorldPoint> path, WorldPoint target) {
        long passT0 = System.currentTimeMillis();
        try {
            return handleCurrentTileTransportTowardPathInner(rawPath, path, target);
        } finally {
            WalkPassStats.currentTileMs.addAndGet(System.currentTimeMillis() - passT0);
        }
    }

    private static boolean handleCurrentTileTransportTowardPathInner(List<WorldPoint> rawPath, List<WorldPoint> path, WorldPoint target) {
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

        // Snappy proximity: consider exact planned transports whose origin is reachable within a few
        // tiles of the player, not just one on the exact player tile. NPC/"Follow" transports (e.g.
        // Elkoy in the Tree Gnome Village maze) roam and sit a tile off the planned path. The old code
        // rescanned every usable catalog row and inferred selection from destination membership; that is
        // ambiguous when multiple transports share an edge. The completed route now supplies both order
        // and exact identity.
        final int NEARBY_TRANSPORT_REACH = 5;
        Set<WorldPoint> reachableOrigins = new HashSet<>(
                Rs2Tile.getReachableTilesFromTile(playerLoc, NEARBY_TRANSPORT_REACH).keySet());
        reachableOrigins.add(playerLoc);
        List<Rs2PathApi.ActiveTransportSelection> plannedSelections =
                Rs2PathApi.getActiveTransportSelections(rawPath);
        if (plannedSelections.isEmpty()) {
            return false;
        }

        Map<WorldPoint, Integer> forwardIndex = new HashMap<>();
        addForwardPathIndices(forwardIndex, rawPath, playerLoc);
        addForwardPathIndices(forwardIndex, path, playerLoc);
        int rawClosestIndex = Math.max(0, getClosestTileIndex(rawPath, playerLoc));

        WorldPoint priorOrigin = routeState.lastTransportOriginLocation;
        // Trust the pathfinder: only take a nearby transport whose destination is on the
        // planned forward route, ordered by route position (earliest forward transport first). The
        // old fallback admitted off-path transports whose destination was straight-line "closer" to
        // the goal — but WorldPoint#distanceTo ignores the underground Y-offset, so an inner-region
        // tile reads numerically closer to a surface goal. That made the walker re-take transports
        // the pathfinder never chose: it looped forever on the Mor Ul Rek cave entrance/exit and
        // stalled clicking the Fossil Island rowboat. The pathfinder already routed every transport
        // it wants onto the path, so on-route membership is the correct, region-safe admission test.
        List<Rs2PathApi.ActiveTransportSelection> candidates = plannedSelections.stream()
                // One-edge backtrack permits standing just past an interaction origin while preventing
                // a repeated destination later in the route from reviving an already-passed transport.
                .filter(selection -> selection.getPathIndex() >= Math.max(0, rawClosestIndex - 1))
                .filter(selection -> {
                    Transport transport = selection.getLocalExecutionTransport();
                    WorldPoint origin = transport.getOrigin();
                    return origin == null || reachableOrigins.contains(origin);
                })
                // Local adjacent same-plane edges (doors/gates) are handled by segment door/object
                // logic; current-tile transport probing can bounce on these and create loops.
                .filter(selection -> !isAdjacentSamePlaneTransport(selection.getLocalExecutionTransport()))
                .filter(selection -> priorOrigin == null
                        || !selection.getEdge().getDestination().equals(priorOrigin))
                .filter(selection -> target == null
                        || playerLoc.getPlane() != target.getPlane()
                        || selection.getEdge().getDestination().getPlane() == target.getPlane())
                .filter(selection -> forwardIndex.containsKey(selection.getEdge().getDestination()))
                .sorted(Comparator.comparingInt(Rs2PathApi.ActiveTransportSelection::getPathIndex))
                .collect(Collectors.toList());

        for (Rs2PathApi.ActiveTransportSelection selection : candidates) {
            Transport transport = selection.getLocalExecutionTransport();
            WorldPoint origin = transport.getOrigin() != null ? transport.getOrigin() : playerLoc;
            if (shouldThrottleCurrentTileTransportAttempt(origin, transport.getDestination())) {
                continue;
            }
            markCurrentTileTransportAttempt(origin, transport.getDestination());
            WorldPoint before = Rs2Player.getWorldLocation();
            // Pass the transport's own origin so handleTransports walks the short hop to it before
            // interacting (NPC dispatch already auto-walks via canWalkTo + interact); object/door
            // interactions that can't be reached from here simply return false and we fall through.
            if (Rs2WalkerTransports.handleSelectedTransport(Arrays.asList(origin, transport.getDestination()), 0, selection)) {
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

    // D3 slice 3: the session blacklist (quest/stat-locked doors) and the recently-opened
    // suppression map live in the ledger as tile-keyed facets.
    static final long STATIONARY_DOOR_SUPPRESS_MS = 10_000;
    // D3 slice 1: ATTEMPTED lives in the ledger — one owner for the per-edge cooldown facts AND
    // the latest-claim fact that used to sit in routeState.lastDoorAttempt* and disagree with them.
    static final DoorAttemptLedger doorAttemptLedger = new DoorAttemptLedger();
    static final long DOOR_ATTEMPT_EDGE_COOLDOWN_MS = 2_500;
    // D3 slice 2: cross-failure strikes and walk-scoped blocks live in the ledger (REFUSED facet).
    static final long DOOR_CROSS_FAILURE_DECAY_MS = 300_000;
    static final int DOOR_CROSS_FAILURE_STRIKE_LIMIT = 3;
    private static final Map<String, Long> recentCurrentTileTransportByEdge = new ConcurrentHashMap<>();
    private static final long CURRENT_TILE_TRANSPORT_EDGE_COOLDOWN_MS = 2_200;
    static final long DOOR_INTERACTION_GLOBAL_COOLDOWN_MS = 1_800;



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
    // findFurthestClickableIndex extracted to recovery/RouteRecovery (P1)

    // findFurthestForwardClickableIndex extracted to recovery/RouteRecovery (P1)

    private static int findForwardReachableRecoveryIndex(List<WorldPoint> path,
                                                         int startIdx,
                                                         WorldPoint playerLoc,
                                                         int maxEuclidean) {
        Set<WorldPoint> reachable = playerLoc == null
                ? Collections.emptySet()
                : Rs2Tile.getReachableTilesFromTile(playerLoc, Math.max(2, maxEuclidean)).keySet();
        return RouteRecovery.findForwardRecoveryIndex(
                path,
                startIdx,
                playerLoc,
                maxEuclidean,
                reachable,
                Rs2Walker::isMiniMapRecoveryClickable);
    }

    // findForwardRecoveryIndex extracted to recovery/RouteRecovery (P1 walker decomposition)


    // interpolateClickableTarget extracted to recovery/RouteRecovery (P1)

    // clampToEuclideanRadius extracted to recovery/RouteRecovery (P1)

    static int euclideanSq(WorldPoint a, WorldPoint b) {
        int dx = a.getX() - b.getX();
        int dy = a.getY() - b.getY();
        return dx * dx + dy * dy;
    }

    // findReachableTransportOriginAhead extracted to recovery/RouteRecovery as a pure, unit-tested function (P1)

























    /** How long a door attempt claims its edge against outside interference (route revalidation). */
    private static final long ACTIVE_DOOR_EDGE_CLAIM_MS = 10_000L;













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





    static boolean shouldDeferRouteWorkForActiveInterim(WorldPoint interim,
                                                        WorldPoint playerLoc,
                                                        long setAtMs,
                                                        long lastProgressAtMs,
                                                        long nowMs,
                                                        int bestDistanceSeen,
                                                        long lastMovedAtMs,
                                                        boolean playerMoving,
                                                        int handoffTiles) {
        if (interim == null) {
            return false;
        }
        if (shouldClearInterimTarget(
                interim, playerLoc, setAtMs, lastProgressAtMs, nowMs, bestDistanceSeen)) {
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


    /** One game tick: the floor a DIFFERENT door still owes after any door click. */
    static final long DOOR_INTERACTION_CROSS_EDGE_COOLDOWN_MS = 600L;





    private static boolean isTransportInteractionSettling() {
        long handledAt = routeState.lastTransportHandledAtMs;
        if (handledAt <= 0L) {
            return false;
        }
        return transportSettlePending(System.currentTimeMillis() - handledAt,
                Rs2Player.getWorldLocation(),
                routeState.lastTransportDestinationLocation,
                Rs2Player.isMoving(),
                Rs2Player.isAnimating());
    }



    static boolean isRecoveryMovementInFlight() {
        return System.currentTimeMillis() - routeState.lastUnreachableRecoveryClickAtMs < RECOVERY_MOVEMENT_IN_FLIGHT_MS;
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



    /** Exact selected transport step retained by the completed active route. */
    private static boolean hasExplicitTransportStep(List<WorldPoint> path, int index) {
        if (path == null || index < 0 || index >= path.size() - 1) {
            return false;
        }
        return Rs2PathApi.getActiveTransportEdge(path.get(index), path.get(index + 1)).isPresent();
    }

    /**
     * Whether a planned transport origin sits essentially under the player's feet on the RAW path.
     * <p>
     * The startup phase suppresses broad raw handlers until the first movement click — but the first
     * transport of a walk is routinely taken with no click at all (the player already stands on its
     * origin), so the phase stays STARTUP straight through the NEXT transport. With the raw scan
     * disabled, nothing dispatches it: the walker idles until the idle nudge minimap-clicks onto the
     * origin, which is the "runs the four tiles instead of clicking the stairs" report. The segment
     * loop already carves out exactly this case; this is the raw scan's equivalent, and it is
     * deliberately as narrow — an origin within the near band, nothing else.
     */
    private static boolean hasImmediateRawTransportStepNearPlayer(List<WorldPoint> rawPath) {
        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        if (rawPath == null || rawPath.size() < 2 || playerLoc == null) {
            return false;
        }
        int rawIdx = getClosestTileIndex(rawPath, playerLoc);
        if (rawIdx < 0) {
            return false;
        }
        int lastIdx = Math.min(rawPath.size() - 2, rawIdx + RAW_TRANSPORT_DISPATCH_MAX_DISTANCE);
        for (int ri = Math.max(0, rawIdx); ri <= lastIdx; ri++) {
            if (hasImmediatePlannedTransportStep(rawPath, ri, playerLoc)) {
                return true;
            }
        }
        return false;
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
    static boolean isCatalogBackedTransportSegment(List<WorldPoint> path, int index) {
        if (path == null || index < 0 || index >= path.size() - 1) {
            return false;
        }
        return isCatalogBackedTransportSegment(path.get(index), path.get(index + 1));
    }

    static boolean isCatalogBackedTransportSegment(WorldPoint from, WorldPoint to) {
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











    /**
     * True when this scene object is the interactable listed on a transport catalog row (same
     * coordinates and object ids as TSV loaded into {@link Rs2PathApi#getTransports()}).
     * Door-ahead / fallback / LOS scans must treat it as non-door so {@link #handleTransports} owns it.
     */























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
				if (!Rs2DoorGeometry.isDoorOnSegment(w, from, to)) {
					continue;
				}

				ObjectComposition comp = Rs2DoorDetection.resolveCompositionForDoorProbe(w);
				if (comp == null || Rs2DoorClassifier.isNullOrPlaceholderObjectName(comp.getName())) continue;
				if (Rs2DoorClassifier.doorCompositionSpecifiesOnlyCloseOrShut(comp)) continue;

				String action = Rs2DoorClassifier.pickWalkDoorAction(comp);
				boolean doorLike = Rs2DoorClassifier.isRouteDoorObject(true, comp.getName(), action);
				if (!doorLike) continue;
				if (Rs2DoorProbe.isCatalogTransportObject(w)) continue;

				String actionFinal = action == null ? "" : action;

				int edgeDist = Math.min(objWp.distanceTo2D(from), objWp.distanceTo2D(to));
				int pri = actionFinal.isEmpty() ? Integer.MAX_VALUE : Rs2DoorClassifier.doorActionPriorityIndex(actionFinal);
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
				if (!Rs2DoorGeometry.isDoorOnSegment(g, from, to)) {
					continue;
				}

				ObjectComposition comp = Rs2DoorDetection.resolveCompositionForDoorProbe(g);
				if (comp == null || Rs2DoorClassifier.isNullOrPlaceholderObjectName(comp.getName())) continue;
				if (Rs2DoorClassifier.doorCompositionSpecifiesOnlyCloseOrShut(comp)) continue;

				String action = Rs2DoorClassifier.pickWalkDoorAction(comp);
				boolean doorLike = Rs2DoorClassifier.isRouteDoorObject(false, comp.getName(), action);
				if (!doorLike) continue;
				if (Rs2DoorProbe.isCatalogTransportObject(g)) continue;

				String actionFinal = action == null ? "" : action;

				int edgeDist = Math.min(objWp.distanceTo2D(from), objWp.distanceTo2D(to));
				int pri = actionFinal.isEmpty() ? Integer.MAX_VALUE : Rs2DoorClassifier.doorActionPriorityIndex(actionFinal);
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
			log.debug("[Walker] path-adj blocker-scan: no candidates (radius={} idx={}/{})", radiusTiles, startIdx, path.size());
			return false;
		}

        List<PathAdjDoorComponent> components = buildPathAdjDoorComponents(byIdentity.values(), startIdx, playerLoc);
        if (components.isEmpty()) {
            log.debug("[Walker] path-adj blocker-scan: no components (radius={} idx={}/{})", radiusTiles, startIdx, path.size());
            return false;
        }
        PathAdjDoorComponent bestComponent = components.stream()
                .min(Comparator.comparingInt(c -> c.score))
                .orElse(null);
        if (bestComponent == null || bestComponent.best == null) {
            log.debug("[Walker] path-adj blocker-scan: no component winner (radius={} idx={}/{})", radiusTiles, startIdx, path.size());
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
                    compactWorldPoint(routeState.lastTransportOriginLocation),
                    compactWorldPoint(routeState.lastTransportDestinationLocation));
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
		if (shouldThrottleGlobalDoorInteraction(bestFrom, bestTo)) {
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
        markDoorInteractionSettling(bestTo);
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
        boolean wrongTraversal = bestLoc != null && shouldBlacklistDoorAfterWrongTraversal(posBefore, posAfter, bestFrom, bestTo, Rs2Player.isMoving());
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
        if (Rs2DoorProbe.isCatalogTransportObject(object)) {
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






    static final class PathAdjDoorCandidate {
        PathAdjDoorCandidate(TileObject object, WorldPoint location, String action, int actionPriority,
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
        final TileObject object;
        final WorldPoint location;
        final String action;
        final int actionPriority;
        final int edgeIdx;
        final WorldPoint from;
        final WorldPoint to;
        final int edgeDist;
        final String familyKey;

    }

    static final class PathAdjDoorComponent {
        PathAdjDoorComponent(PathAdjDoorCandidate best, int score, Set<WorldPoint> locations) {
            this.best = best;
            this.score = score;
            this.locations = locations;
        }
        final PathAdjDoorCandidate best;
        final int score;
        final Set<WorldPoint> locations;

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
			long timeoutMs)
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

				ObjectComposition comp = Rs2DoorDetection.resolveCompositionForDoorProbe(object);
				if (comp == null || Rs2DoorClassifier.isNullOrPlaceholderObjectName(comp.getName())) continue;
				if (Rs2DoorClassifier.doorCompositionSpecifiesOnlyCloseOrShut(comp)) continue;

				// Gate by "door-like" name or by having a known door-like action.
				String action = Arrays.stream(comp.getActions())
						.filter(Objects::nonNull)
						.filter(act -> !Rs2DoorClassifier.isDoorCloseOrShutAction(act))
						.filter(act -> Rs2DoorClassifier.doorActionPriorityIndex(act) < Integer.MAX_VALUE)
						.min(Comparator.comparingInt(Rs2DoorClassifier::doorActionPriorityIndex))
						.orElse(null);
				boolean doorLike = Rs2DoorClassifier.isRouteDoorObject(object instanceof WallObject, comp.getName(), action);
				if (!doorLike) continue;
				if (Rs2DoorProbe.isCatalogTransportObject(object)) continue;

				// Found a likely blocker on-path: hand off to existing door handler (which
				// includes quest-lock detection, blacklisting, and recalculation).
				if (handleDoorsWithTimeoutBudgeted(path, j, timeoutMs, false)) {
					return true;
				}
			}
		}
		return false;
	}





    /**
     * @param path list of worldpoints
     * @return closest tile index
     */
    public static int getClosestTileIndex(List<WorldPoint> path) {
        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        return WalkerPathGeometry.getClosestTileIndex(path, playerLoc, getClosestIndexReachableTiles(playerLoc));
    }

    static int getClosestTileIndex(List<WorldPoint> path, WorldPoint playerLoc) {
        return WalkerPathGeometry.getClosestTileIndex(path, playerLoc, getClosestIndexReachableTiles(playerLoc));
    }

    // 3-arg getClosestTileIndex (pure) moved to geometry/WalkerPathGeometry (P1)

    /** Step budget of {@link #getClosestIndexReachableTiles}'s BFS; also the route-blocked scan gate's bound. */
    static final int CLOSEST_INDEX_REACHABLE_STEP_BUDGET = 20;

    /**
     * Calls and milliseconds spent in the player-origin BFS since the current walk started.
     *
     * <p>Every {@code getClosestTileIndex} runs one of these, and the walk loop asks for a route
     * index many times per iteration — route progress, interim tracking, near-path checks, click
     * selection, each recovery probe. Each one is a fresh breadth-first search executed on the CLIENT
     * thread, so the cost is a round trip, not arithmetic, and it does not show up in any existing
     * timing line. A walk that goes silent for seconds with no heartbeat is blocked inside something,
     * and this is the leading candidate; these two numbers ride on the heartbeat so the next log
     * settles it instead of another round of inference.
     */
    private static final AtomicInteger reachableBfsCalls = new AtomicInteger();
    private static final AtomicLong reachableBfsMillis = new AtomicLong();

    static HashMap<WorldPoint, Integer> getClosestIndexReachableTiles(WorldPoint playerLoc) {
        if (playerLoc == null) {
            return new HashMap<>();
        }
        HashMap<WorldPoint, Integer> tiles;
        long bfsStartedAt = System.currentTimeMillis();
        reachableBfsCalls.incrementAndGet();
        try {
            tiles = Rs2Tile.getReachableTilesFromTile(
                    playerLoc, CLOSEST_INDEX_REACHABLE_STEP_BUDGET);
        } catch (RuntimeException failure) {
            if (!isClientThreadReadTimeout(failure)) {
                throw failure;
            }
            reachableBfsMillis.addAndGet(System.currentTimeMillis() - bfsStartedAt);
            WebWalkLog.spInfo("client_thread_timeout_fallback | op=closest_route_index");
            return nearbyTilesIgnoringCollision(
                    playerLoc, CLOSEST_INDEX_REACHABLE_STEP_BUDGET);
        }
        reachableBfsMillis.addAndGet(System.currentTimeMillis() - bfsStartedAt);

        // If an animation/shortcut puts the player on a collision-odd tile, keep route progress
        // anchored by distance instead of repeatedly recalculating an empty reachable set.
        if (tiles.isEmpty()) {
            tiles = nearbyTilesIgnoringCollision(
                    playerLoc, CLOSEST_INDEX_REACHABLE_STEP_BUDGET);
        }
        return tiles;
    }



    static int stabilizeRouteProgressIndex(List<WorldPoint> path, int closestIdx, WorldPoint target, WorldPoint playerLoc) {
        if (path == null || path.isEmpty() || closestIdx < 0 || closestIdx >= path.size()) {
            return closestIdx;
        }

        WorldPoint pathStart = path.get(0);
        WorldPoint pathEnd = path.get(path.size() - 1);
        boolean routeChanged = routeState.routeProgressTarget == null
                || !routeState.routeProgressTarget.equals(target)
                || routeState.routeProgressPathSize != path.size()
                || !Objects.equals(routeState.routeProgressPathStart, pathStart)
                || !Objects.equals(routeState.routeProgressPathEnd, pathEnd)
                || routeState.routeProgressIdx >= path.size();
        if (routeChanged) {
            routeState.routeProgressTarget = target;
            routeState.routeProgressPathStart = pathStart;
            routeState.routeProgressPathEnd = pathEnd;
            routeState.routeProgressPathSize = path.size();
            routeState.routeProgressIdx = closestIdx;
            routeState.routeProgressAdvancedAtMs = System.currentTimeMillis();
            // A new route means new raw indices; a stale high-water mark from the old route would
            // silently disable the raw watermark for the rest of the walk.
            routeState.rawProgressHighIdx = -1;
            return closestIdx;
        }

        if (routeState.routeProgressIdx < 0 || closestIdx >= routeState.routeProgressIdx) {
            if (closestIdx > routeState.routeProgressIdx) {
                recordRouteProgressAdvanced();
            }
            routeState.routeProgressIdx = closestIdx;
            return closestIdx;
        }

        int forwardIdx = closestForwardPathIndex(path, routeState.routeProgressIdx, playerLoc);
        if (forwardIdx >= routeState.routeProgressIdx) {
            if (forwardIdx > routeState.routeProgressIdx) {
                routeState.routeProgressIdx = forwardIdx;
                recordRouteProgressAdvanced();
            }
            return routeState.routeProgressIdx;
        }
        return routeState.routeProgressIdx;
    }

    static void hintRouteProgressIndex(List<WorldPoint> path, int hintedIdx, WorldPoint target) {
        if (path == null || path.isEmpty() || hintedIdx < 0 || hintedIdx >= path.size()) {
            return;
        }

        WorldPoint pathStart = path.get(0);
        WorldPoint pathEnd = path.get(path.size() - 1);
        boolean routeChanged = routeState.routeProgressTarget == null
                || !routeState.routeProgressTarget.equals(target)
                || routeState.routeProgressPathSize != path.size()
                || !Objects.equals(routeState.routeProgressPathStart, pathStart)
                || !Objects.equals(routeState.routeProgressPathEnd, pathEnd)
                || routeState.routeProgressIdx >= path.size();
        if (routeChanged) {
            routeState.routeProgressTarget = target;
            routeState.routeProgressPathStart = pathStart;
            routeState.routeProgressPathEnd = pathEnd;
            routeState.routeProgressPathSize = path.size();
            routeState.routeProgressIdx = hintedIdx;
            recordRouteProgressAdvanced();
            return;
        }

        if (hintedIdx > routeState.routeProgressIdx) {
            routeState.routeProgressIdx = hintedIdx;
            recordRouteProgressAdvanced();
        }
    }

    static int advanceIndexPastRecentTransportEdge(List<WorldPoint> path, int index, WorldPoint playerLoc) {
        if (path == null || path.isEmpty() || index < 0 || index >= path.size()
                || !isRecentTransportEdgeWindow()) {
            return index;
        }
        WorldPoint origin = routeState.lastTransportOriginLocation;
        WorldPoint destination = routeState.lastTransportDestinationLocation;
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
        routeState.routeProgressIdx = -1;
        routeState.routeProgressTarget = null;
        routeState.routeProgressPathStart = null;
        routeState.routeProgressPathEnd = null;
        routeState.routeProgressPathSize = -1;
        routeState.routeProgressAdvancedAtMs = 0L;
        routeState.stagnationReplansSpent = 0;
        routeState.rawProgressHighIdx = -1;
    }

    /**
     * Per-pass progress update with RAW granularity. The smoothed index alone starves the stagnation
     * clock on healthy walks: the entire Varrock west approach — fifty tiles and three doors — sits
     * inside the final smoothed segment, so the index held one value through ~50s of honest walking
     * (measured 2026-08-12) against a 60s budget. The player's furthest-yet raw index advances tile
     * by tile on exactly that walk, and still refuses to advance during the Tithe ping-pong: two
     * tiles oscillating can set a high-water mark once, never repeatedly.
     */
    static int stabilizeRouteProgressWithRawWatermark(List<WorldPoint> rawPath, List<WorldPoint> path,
                                                      int closestIdx, WorldPoint target, WorldPoint playerLoc) {
        int stabilized = stabilizeRouteProgressIndex(path, closestIdx, target, playerLoc);
        if (rawPath != null && !rawPath.isEmpty() && playerLoc != null) {
            // Plain nearest-by-distance (no reachability BFS): a monotone high-water mark only needs
            // consistency with itself, and this runs once per loop pass.
            int rawIdx = WalkerPathGeometry.getClosestTileIndex(rawPath, playerLoc, null);
            if (rawIdx > routeState.rawProgressHighIdx) {
                routeState.rawProgressHighIdx = rawIdx;
                routeState.routeProgressAdvancedAtMs = System.currentTimeMillis();
            }
        }
        return stabilized;
    }

    private static void recordRouteProgressAdvanced() {
        long now = System.currentTimeMillis();
        routeState.routeProgressAdvancedAtMs = now;
        routeState.lastMovedTimeMs = now;
        routeState.stuckCount = 0;
    }

    private static boolean isRecentTransportEdgeWindow() {
        long handledAt = routeState.lastTransportHandledAtMs;
        if (handledAt <= 0L) {
            return false;
        }
        long ageMs = System.currentTimeMillis() - handledAt;
        return ageMs >= 0L && ageMs <= RECENT_TRANSPORT_EDGE_SUPPRESS_MS;
    }

    static boolean isNearSamePlane(WorldPoint a, WorldPoint b, int distance) {
        return a != null
                && b != null
                && a.getPlane() == b.getPlane()
                && a.distanceTo2D(b) <= distance;
    }

    private static boolean isRecentTransportEdgeCandidate(WorldPoint objectLoc, WorldPoint from, WorldPoint to) {
        if (!isRecentTransportEdgeWindow()) {
            return false;
        }
        WorldPoint origin = routeState.lastTransportOriginLocation;
        WorldPoint destination = routeState.lastTransportDestinationLocation;
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
		recalculatePath(Rs2PlannerShadowContext.Invocation.ACTIVE_REPLAN);
	}

	/**
	 * Queue one deterministic recovery replan for the active live-test walk.
	 *
	 * <p>The request is consumed by {@code processWalk} on the walker thread so the normal recovery evidence
	 * context is updated. Production callers cannot enable this hook: it is inert unless the test runner set
	 * {@code microbot.test.mode=true} and an active target exists.</p>
	 */
	public static boolean requestRecoveryReplanForTest()
	{
		if (!Boolean.getBoolean("microbot.test.mode") || currentTarget == null)
		{
			return false;
		}
		testRecoveryReplanRequests.incrementAndGet();
		return true;
	}

	static boolean consumeRecoveryReplanForTest()
	{
		if (!Boolean.getBoolean("microbot.test.mode"))
		{
			testRecoveryReplanRequests.set(0);
			return false;
		}
		return testRecoveryReplanRequests.getAndUpdate(value -> Math.max(0, value - 1)) > 0;
	}

    private static void recalculatePathForRecovery() {
		WalkEvidenceContext evidence = walkEvidenceContext.get();
		if (evidence != null)
		{
			evidence.recoveryTriggered = true;
		}
		recalculatePath(Rs2PlannerShadowContext.Invocation.RECOVERY_REPLAN);
	}

	private static void recalculatePath(Rs2PlannerShadowContext.Invocation invocation) {
        WorldPoint goal = currentTarget;
        if (goal == null) {
            return;
        }
        // Startup marks are deduped per phase per walk, so a startup that REPLANS goes silent for its
        // whole second pass — pf_wait_retry, pf_ready and path_snapshot have all been logged already.
        // That is exactly the window a walled-click replan lands in, which is why the slowest starts
        // are the least visible ones: a four-second gap with nothing in it but the replan itself.
        // Re-arm them so each startup attempt narrates its own.
        if (!routeState.firstMovementClickMarked) {
            startupPhasesLogged.clear();
        }
        // Must not call setTarget(null)+setTarget(goal): that briefly clears {@link #currentTarget},
        // and processWalk on another thread treats null as cancel (isWalkCancelled).
		Rs2WalkerLifecycleRuntime.applyWalkerDestination(goal, invocation);
    }

    /**
     * Gives a detected/attempted scene-door edge exclusive ownership over recovery. The exact edge
     * in {@link DoorAttemptLedger} wins once known; a refused route edge remains the approach/affected
     * edge until then. This prevents idle nudges, stall replans and generic recovery clicks from
     * competing between detection, interaction and crossing.
     */
    private static WalkExit resolveActiveDoorClaim(WorldPoint playerLoc, long timeoutMs,
                                                   List<WorldPoint> routePath) {
        long now = System.currentTimeMillis();
        DoorAttemptLedger.Attempt attempted = doorAttemptLedger.latestAttempt(ACTIVE_DOOR_EDGE_CLAIM_MS, now);
        WorldPoint from = attempted == null ? routeState.walledDoorEdgeFrom : attempted.from;
        WorldPoint to = attempted == null ? routeState.walledDoorEdgeTo : attempted.to;
        long claimedAtMs = attempted == null ? routeState.walledDoorEdgeAtMs : attempted.attemptedAtMs;
        long maxAgeMs = attempted == null ? WalledDoorClaimPolicy.FRESH_MS : ACTIVE_DOOR_EDGE_CLAIM_MS;
        WalledDoorClaimPolicy.Decision decision = WalledDoorClaimPolicy.decide(
                from, to, claimedAtMs, now, playerLoc, Rs2Player.isMoving(),
                from != null && Rs2Tile.isTileReachable(from), maxAgeMs);
        switch (decision) {
            case CROSSED:
                if (attempted != null) {
                    doorAttemptLedger.clearLatestAttempt();
                } else {
                    clearWalledDoorClaim();
                }
                return WalkExit.RECOVERY_POSITION_STALE;
            case ACTION_IN_FLIGHT:
                routeState.doorRecoverySuppressedAtMs = now;
                return WalkExit.RECOVERY_CLICK_PREEMPTED_BY_ACTION;
            case HANDLE_AT_EDGE:
                if (tryTraverseOwnedOpenDoor(attempted, from, to, playerLoc, routePath)) {
                    clearWalledDoorClaim();
                    return WalkExit.RECENT_DOOR_EDGE_NUDGE;
                }
                if (handleDoorsWithTimeoutBudgeted(Arrays.asList(from, to), 0, timeoutMs, true)) {
                    if (attempted != null) {
                        doorAttemptLedger.clearLatestAttempt();
                    } else {
                        clearWalledDoorClaim();
                    }
                    return WalkExit.DOOR_HANDLED_LOCAL_REACHABILITY;
                }
                // The interaction above may have opened the edge while its scene-object snapshot
                // still reported an Open action. Spend that exact live-collision transition now;
                // passively yielding until the 10s ownership claim expires is unnecessary latency.
                if (tryTraverseOwnedOpenDoor(attempted, from, to,
                        Rs2Player.getWorldLocation(), routePath)) {
                    clearWalledDoorClaim();
                    return WalkExit.RECENT_DOOR_EDGE_NUDGE;
                }
                routeState.doorRecoverySuppressedAtMs = now;
                WebWalkLog.spInfo("walled_door_owned | edge={}->{} player={} state=await-door-handler",
                        compactWorldPoint(from), compactWorldPoint(to), compactWorldPoint(playerLoc));
                return WalkExit.DOOR_TRAVERSAL_PENDING_YIELD;
            case APPROACH:
                if (!walkMiniMap(from)) {
                    routeState.doorRecoverySuppressedAtMs = now;
                    return WalkExit.DOOR_RECOVERY_SUPPRESSED;
                }
                routeState.lastUnreachableRecoveryClickAtMs = now;
                WebWalkLog.spInfo("walled_door_approach | edge={}->{} player={} - approaching claimed door instead of replanning",
                        compactWorldPoint(from), compactWorldPoint(to), compactWorldPoint(playerLoc));
                return WalkExit.DOOR_SUPPRESSED_APPROACH_CLICK;
            case EXPIRED:
            case INVALID:
                if (attempted != null) {
                    doorAttemptLedger.clearLatestAttempt();
                } else {
                    clearWalledDoorClaim();
                }
                return null;
            default:
                return null;
        }
    }

    private static void clearWalledDoorClaim() {
        routeState.walledDoorEdgeFrom = null;
        routeState.walledDoorEdgeTo = null;
        routeState.walledDoorEdgeAtMs = 0L;
    }

    static boolean hasFreshActiveDoorClaim(long nowMs) {
        return doorAttemptLedger.latestAttempt(ACTIVE_DOOR_EDGE_CLAIM_MS, nowMs) != null
                || WalledDoorClaimPolicy.isFresh(routeState.walledDoorEdgeFrom,
                routeState.walledDoorEdgeTo, routeState.walledDoorEdgeAtMs, nowMs);
    }

    /** Pathfinder normalizes nested sealed shells; the walker may change effective target once. */
    private static final int MAX_SEALED_RIM_RETARGETS = 1;

    /** Retargets a proven-sealed requested goal to one normalized, reachable approach rim. */
    private static WorldPoint consumeSealedRimRetarget(WorldPoint target, WorldPoint dst) {
        var pf = Rs2PathApi.getPathfinder();
        if (pf == null) {
            return null;
        }
        WorldPoint rim = pf.getReachedSealedSubstitute();
        if (rim != null && rim.equals(dst) && !rim.equals(target)) {
            WebWalkLog.spInfo("sealed_target_retarget | goal={} rim={} — goal proven sealed, walking to its rim",
                    target, rim);
            setTarget(rim);
            recalculatePath();
            return rim;
        }
        // Rim UNREACHED: the substitute budget (~125-tile flood radius) exhausts en route whenever
        // the sealed goal is far away, and without this the whole journey is a partial crawl — one
        // truncated search per pass (Falador->Burthorpe against a clicked hatch tile), ending
        // beside the goal but never terminating. The rim tile is an ordinary reachable tile, so
        // plan to IT with the normal full budget: one complete route, the door pipeline handles
        // route doors, and arrival lands beside the sealed tile the caller actually clicked.
        WorldPoint nearest = pf.getNearestSealedRimSubstitute();
        if (nearest == null || nearest.equals(target)
                || routeState.sealedRimRetargets >= MAX_SEALED_RIM_RETARGETS) {
            return null;
        }
        routeState.sealedRimRetargets++;
        WebWalkLog.spInfo("sealed_target_retarget | requested={} effective={} rim={} — goal sealed; planning to normalized rim ({}/{})",
                routeState.requestedGoal, target, nearest,
                routeState.sealedRimRetargets, MAX_SEALED_RIM_RETARGETS);
        setTarget(nearest);
        recalculatePath();
        return nearest;
    }

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
            Rs2PathApi.cancelAndClearActiveRoute();

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
        return handleTransports(path, indexOfStartPoint, false);
    }

    private static boolean handleTransports(List<WorldPoint> path, int indexOfStartPoint,
                                            boolean allowServerApproach) {
        Optional<Rs2PathApi.ActiveTransportSelection> selection =
                Rs2PathApi.getActiveTransportSelection(path, indexOfStartPoint);
        if (selection.isEmpty()) {
            return false;
        }
        return Rs2WalkerTransports.handleSelectedTransport(
                path, indexOfStartPoint, selection.get(), allowServerApproach);
    }












	static boolean isAdjacentSamePlaneTransport(Transport transport) {
		return transport != null
                && transport.getOrigin() != null
                && transport.getDestination() != null
                && transport.getOrigin().getPlane() == transport.getDestination().getPlane()
				&& transport.getOrigin().distanceTo(transport.getDestination()) <= 1;
	}

	static boolean isAdjacentSamePlaneTransport(Rs2TransportEdge transport) {
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



    private static boolean handleTransportsInRawSegment(List<WorldPoint> rawPath, int rawFrom, int rawTo) {
        return handleTransportsInRawSegment(rawPath, rawFrom, rawTo, false);
    }

    /**
     * Dispatches a planned transport on this raw segment.
     * <p>
     * This is the path that actually takes stairs and ladders on a normal walk — the raw scene scan's
     * ranged branch rarely gets there first, because the route click puts the player on the origin
     * before the scan runs. So gating only the scan left the walker still walking its four tiles to
     * the foot of the stairs before clicking, which is exactly what interact-at-range was meant to
     * stop. {@code allowRangedDispatch} lets the caller say "this is the nearest obstacle", and route
     * order is then held inside the loop: a transport passed over denies the ranged branch to
     * everything behind it.
     */
    private static boolean handleTransportsInRawSegment(List<WorldPoint> rawPath, int rawFrom, int rawTo,
                                                        boolean allowRangedDispatch) {
        long passT0 = System.currentTimeMillis();
        try {
            return handleTransportsInRawSegmentInner(rawPath, rawFrom, rawTo, allowRangedDispatch);
        } finally {
            WalkPassStats.segTransportMs.addAndGet(System.currentTimeMillis() - passT0);
        }
    }

    private static boolean handleTransportsInRawSegmentInner(List<WorldPoint> rawPath, int rawFrom, int rawTo,
                                                        boolean allowRangedDispatch) {
        Boolean inInstance = null;
        boolean sawUndispatchedTransportStep = false;
        for (int ri = rawFrom; ri < rawTo && ri < rawPath.size() - 1; ri++) {
            WorldPoint playerLoc = Rs2Player.getWorldLocation();
            if (isRawTransportOriginNearPlayer(
                    rawPath, ri, playerLoc, RAW_TRANSPORT_DISPATCH_MAX_DISTANCE)) {
                if (handleTransports(rawPath, ri)) {
                    return true;
                }
                if (hasExplicitTransportStep(rawPath, ri)) {
                    sawUndispatchedTransportStep = true;
                }
                continue;
            }
            if (!hasExplicitTransportStep(rawPath, ri)) {
                continue;
            }
            if (!allowRangedDispatch || sawUndispatchedTransportStep) {
                sawUndispatchedTransportStep = true;
                continue;
            }
            WorldPoint origin = rawPath.get(ri);
            WorldPoint dest = rawPath.get(ri + 1);
            int originDistance = playerLoc != null && origin != null
                    && origin.getPlane() == playerLoc.getPlane()
                    ? origin.distanceTo2D(playerLoc)
                    : -1;
            if (inInstance == null) {
                inInstance = Microbot.getClientThread()
                        .runOnClientThreadOptional(() -> Microbot.getClient().getTopLevelWorldView().isInstance())
                        .orElse(Boolean.TRUE);
            }
            boolean allowed = shouldDispatchTransportAtRange(
                    originDistance,
                    RAW_TRANSPORT_DISPATCH_MAX_DISTANCE,
                    HANDLER_RANGE,
                    true,
                    isObjectInteractionTransportStep(rawPath, ri),
                    inInstance,
                    isDoorInteractionSettling() || isTransportInteractionSettling(),
                    rangedTransportEdgeFailedRecently(origin, dest),
                    rangedTransportDispatchEnabled());
            if (!allowed) {
                sawUndispatchedTransportStep = true;
                continue;
            }
            WebWalkLog.spInfo("ranged_transport_dispatch | origin={} dist={} — clicking from range, server walks us",
                    compactWorldPoint(origin), originDistance);
            WorldPoint before = Rs2Player.getWorldLocation();
            if (handleTransports(rawPath, ri, true)) {
                if (didCurrentTileTransportProgress(before, dest, currentTarget)) {
                    return true;
                }
                markRangedTransportEdgeFailed(origin, dest);
            }
            sawUndispatchedTransportStep = true;
        }
        return false;
    }

    /**
     * Whether a planned transport may be interacted with from RANGE instead of stepping onto its
     * origin tile first.
     * <p>
     * Clicking an object makes the SERVER path the player to a valid interaction tile and perform the
     * action; it owns the collision data, so it is strictly better at choosing that tile than any
     * approach heuristic of ours. The walker already spots obstacles {@code HANDLER_RANGE} tiles out
     * but would only act within {@link #RAW_TRANSPORT_DISPATCH_MAX_DISTANCE}, so it walked to a tile
     * it had guessed at and only then clicked — and the guess is what failed at the Black Knights'
     * ladder, the Falador castle staircase and the guarded door, never the interaction itself.
     * <p>
     * ROUTE ORDER is the one thing this must not break: clicking a door twelve tiles ahead when a
     * closed gate sits between walks the player into the gate. Only the FIRST unresolved obstacle on
     * the route may be actioned at range, which {@code firstObstacleOnRoute} carries.
     *
     * @param originDistance          tiles from the player to the transport origin
     * @param maxNearDistance         the legacy on-the-origin band; always dispatchable, unchanged
     * @param maxRangedDistance       furthest the ranged branch may reach (the scan's handler range)
     * @param firstObstacleOnRoute    no earlier unresolved obstacle sits between player and origin
     * @param objectInteractionTransport the row is handled by the generic object click, not a
     *                                dialogue/widget flow that gains nothing from this
     * @param inInstance              instances keep the legacy band: raw coords make "on route" unreliable
     * @param settling                a door/transport settle window is still open
     * @param rangedAttemptFailedRecently a previous ranged attempt on this edge produced no movement
     * @param enabled                 config kill switch
     */
    static boolean shouldDispatchTransportAtRange(int originDistance,
                                                  int maxNearDistance,
                                                  int maxRangedDistance,
                                                  boolean firstObstacleOnRoute,
                                                  boolean objectInteractionTransport,
                                                  boolean inInstance,
                                                  boolean settling,
                                                  boolean rangedAttemptFailedRecently,
                                                  boolean enabled) {
        if (originDistance < 0) {
            return false;
        }
        if (originDistance <= maxNearDistance) {
            return true; // legacy behaviour, untouched
        }
        return enabled
                && !inInstance
                && !settling
                && !rangedAttemptFailedRecently
                && objectInteractionTransport
                && firstObstacleOnRoute
                && originDistance <= maxRangedDistance;
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

    /**
     * True when every transport planned at {@code rawPath[index]} is one the generic object click
     * handles (doors, stairs, ladders, gates). Dialogue and widget flows — boats, canoes, gliders,
     * fairy rings, minecarts, teleports — are excluded: the server will not walk the player into a
     * conversation, so ranged dispatch buys them nothing and risks firing them early. Agility
     * shortcuts are excluded too; they need the exact origin tile (the stepping-stone case).
     */
    private static boolean isObjectInteractionTransportStep(List<WorldPoint> rawPath, int index) {
        if (rawPath == null || index < 0 || index >= rawPath.size() - 1) {
            return false;
        }
        return Rs2PathApi.getActiveTransportEdge(rawPath.get(index), rawPath.get(index + 1))
                .map(edge -> edge.getType() == Rs2TransportType.TRANSPORT
                        && edge.getExecutor() == Rs2TransportExecutor.OBJECT)
                .orElse(false);
    }

    /**
     * Finds the first explicit transport hidden inside one smoothed segment. It may wake the startup
     * handler only when it is a visible single-click object; encountering any other transport first
     * preserves route order and leaves the startup minimap behavior unchanged.
     */
    static boolean firstObjectTransportInRawSegmentWithinRange(List<WorldPoint> rawPath,
                                                               int rawFrom,
                                                               int rawTo,
                                                               WorldPoint playerLoc,
                                                               int maxDistance) {
        if (rawPath == null || rawPath.size() < 2 || playerLoc == null) {
            return false;
        }
        int from = Math.max(0, rawFrom);
        int to = Math.min(rawPath.size() - 1, Math.max(from, rawTo));
        for (int i = from; i < to; i++) {
            if (!hasExplicitTransportStep(rawPath, i)) {
                continue;
            }
            WorldPoint origin = rawPath.get(i);
            return isObjectInteractionTransportStep(rawPath, i)
                    && origin != null
                    && origin.getPlane() == playerLoc.getPlane()
                    && origin.distanceTo2D(playerLoc) <= Math.max(0, maxDistance);
        }
        return false;
    }

    private static SegmentTransportContext segmentTransportContext(List<WorldPoint> rawPath,
                                                                    int rawFrom,
                                                                    int rawTo,
                                                                    List<WorldPoint> smoothedPath,
                                                                    int smoothedIndex,
                                                                    WorldPoint playerLoc) {
        boolean rangedObjectStep = firstObjectTransportInRawSegmentWithinRange(
                rawPath, rawFrom, rawTo, playerLoc, HANDLER_RANGE);
        boolean upcomingNearby = rangedObjectStep || hasUpcomingNearbyTransportStep(
                smoothedPath, smoothedIndex, playerLoc,
                POST_TRANSPORT_RAW_SCAN_TRANSPORT_LOOKAHEAD_EDGES,
                POST_TRANSPORT_RAW_SCAN_TRANSPORT_MAX_DIST);
        boolean startupStep = rangedObjectStep
                || hasImmediatePlannedTransportStep(smoothedPath, smoothedIndex, playerLoc);
        return new SegmentTransportContext(upcomingNearby, startupStep);
    }

    private static final class SegmentTransportContext {
        private final boolean upcomingNearby;
        private final boolean startupStep;

        private SegmentTransportContext(boolean upcomingNearby, boolean startupStep) {
            this.upcomingNearby = upcomingNearby;
            this.startupStep = startupStep;
        }
    }

    /** Config kill switch for ranged transport dispatch; on when the config is unavailable. */
    static boolean rangedTransportDispatchEnabled() {
        return config == null || config.interactWithRouteObstaclesAtRange();
    }



    /** Ranged dispatch attempts that produced no movement, keyed by origin→destination edge. */
    private static final Map<String, Long> failedRangedTransportEdges = new ConcurrentHashMap<>();
    private static final long RANGED_TRANSPORT_RETRY_COOLDOWN_MS = 30_000L;

    static String rangedTransportEdgeKey(WorldPoint from, WorldPoint to) {
        return compactWorldPoint(from) + ">" + compactWorldPoint(to);
    }

    private static boolean rangedTransportEdgeFailedRecently(WorldPoint from, WorldPoint to) {
        Long at = failedRangedTransportEdges.get(rangedTransportEdgeKey(from, to));
        return at != null && System.currentTimeMillis() - at < RANGED_TRANSPORT_RETRY_COOLDOWN_MS;
    }

    /**
     * Records that a ranged attempt on this edge produced nothing, so the walker falls back to
     * walking onto the origin for it. That is the unreachable case — the server declined to path —
     * and it must degrade to the legacy behaviour rather than re-click from range forever.
     */
    private static void markRangedTransportEdgeFailed(WorldPoint from, WorldPoint to) {
        failedRangedTransportEdges.put(rangedTransportEdgeKey(from, to), System.currentTimeMillis());
        WebWalkLog.spInfo("ranged_transport_no_progress | {} -> {} — falling back to walking onto the origin",
                compactWorldPoint(from), compactWorldPoint(to));
    }

    private static boolean isTransportOriginNearPlayer(WorldPoint routeOrigin,
                                                       WorldPoint playerLoc,
                                                       int maxDistance) {
        return routeOrigin != null
                && playerLoc != null
                && routeOrigin.getPlane() == playerLoc.getPlane()
                && routeOrigin.distanceTo2D(playerLoc) <= Math.max(0, maxDistance);
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








    /**
     * Options that open the destination list on NPCs whose right-click menu has no per-destination
     * entry. Veos answers "Can you take me somewhere?" with the Port Piscarilius / Land's End menu.
     */
    static final List<String> TERMINAL_TRAVEL_MENU_OPENERS = List.of(
            "Can you take me somewhere?",
            "Can you take me somewhere",
            "take me somewhere",
            "Travel");






























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
        final Rs2ActiveRouteStatus routeStatus = Rs2PathApi.getActiveRouteStatus();
        if (!routeStatus.isPresent()) return false; // idk are we near if we don't have a path?
        final List<WorldPoint> path = routeStatus.getRawPath();

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
        return isNear(target, Rs2Player.getWorldLocation());
    }

    /** Snapshot variant (B2): the walk loop passes its pass-start position instead of re-reading. */
    private static boolean isNear(WorldPoint target, WorldPoint playerLoc) {
        return playerLoc != null && playerLoc.equals(target);
    }

    public static boolean isNearPath() {
        return isNearPath(Rs2Player.getWorldLocation());
    }

    /**
     * Snapshot variant (B2). The two hidden client reads become the caller's {@code loc}, so the
     * walk loop's continuation gate answers from the same world as its neighbours. Note the
     * deliberate side effect carried over unchanged: {@code lastPosition} updates to {@code loc}
     * while comparing against its previous value.
     */
    private static boolean isNearPath(WorldPoint loc) {
        final Rs2ActiveRouteStatus routeStatus = Rs2PathApi.getActiveRouteStatus();
        if (!routeStatus.isPresent()) return true;

        final List<WorldPoint> path = routeStatus.getWalkablePath();
        if (path.isEmpty()) return true;

        if (loc == null) return true;

        if (config.recalculateDistance() < 0 || routeState.lastPosition.equals(routeState.lastPosition = loc)) {
            return true;
        }

        if (config.usePoh() && PohTeleports.isInHouse()) {
            //Would be nice to have access to current node here and check if the current Node is a POH transport node.
            return true;
        }

        var reachableTiles = Rs2Tile.getReachableTilesFromTile(loc, config.recalculateDistance() - 1);
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
                                              boolean movementOwned,
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
        // Busy state defers only while the walker owns the movement. Combat retaliation and
        // aggro pathing keep moving/animating/interacting true indefinitely, and an unbounded
        // defer here paralyzes the walker while something else drags the player off the route.
        if (movementOwned) {
            if (playerMoving) {
                return "moving";
            }
            if (playerAnimating) {
                return "animating";
            }
            if (playerInteracting) {
                return "interacting";
            }
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
        if (movementOwned && isRecentEvent(nowMs, lastMovedAtMs, OFF_PATH_RECALC_RECENT_MOVEMENT_MS)) {
            return "recent-movement";
        }
        return null;
    }

    /**
     * Whether current player movement is plausibly the result of a walker-issued action —
     * a route/recovery click, a door interaction, or a transport handoff — rather than an
     * external force (combat retaliation, aggro, another script). Only owned movement may
     * defer the off-path recalc or preempt a recovery click.
     */
    private static boolean isMovementWalkerOwned(long nowMs, long minimapClickAtMs) {
        long lastOwnedActionAtMs = Math.max(
                Math.max(minimapClickAtMs, doorAttemptLedger.settleStartedAtMs()),
                Math.max(routeState.lastTransportHandledAtMs,
                        Math.max(routeState.lastUnreachableRecoveryClickAtMs, routeState.interimSetAtMs)));
        return isRecentEvent(nowMs, lastOwnedActionAtMs, WALKER_MOVEMENT_OWNERSHIP_WINDOW_MS);
    }

    private static String currentOffPathRecalcDeferralReason(long minimapClickAtMs) {
        long nowMs = System.currentTimeMillis();
        return offPathRecalcDeferralReason(
                Rs2Player.isMoving(),
                Rs2Player.isAnimating(),
                Rs2Player.isInteracting(),
                isMovementWalkerOwned(nowMs, minimapClickAtMs),
                isDoorInteractionSettling(),
                isTransportInteractionSettling(),
                routeState.interimTargetWp != null,
                nowMs,
                routeState.lastMovedTimeMs,
                routeState.routeProgressAdvancedAtMs,
                minimapClickAtMs,
                routeState.interimLastProgressAtMs);
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

    static boolean isRecentEvent(long nowMs, long eventAtMs, long graceMs) {
        return eventAtMs > 0L && nowMs >= eventAtMs && nowMs - eventAtMs < graceMs;
    }

    private static long remainingRecentEventMs(long nowMs, long eventAtMs, long graceMs) {
        if (!isRecentEvent(nowMs, eventAtMs, graceMs)) {
            return OFF_PATH_RECALC_DEFER_WAIT_MIN_MS;
        }
        return graceMs - (nowMs - eventAtMs);
    }

    static boolean hasUpcomingNearbyTransportStep(List<WorldPoint> path,
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
            routeState.lastMovedTimeMs = System.currentTimeMillis();
            routeState.stuckCount = 0;
            routeState.prevAnimatingForStuckCheck = Rs2Player.isAnimating();
            return;
        }

        WorldPoint now = Rs2Player.getWorldLocation();
        boolean anim = Rs2Player.isAnimating();
        if (now != null && now.equals(routeState.lastPosition)) {
            boolean nearPath = isNearPath();
            long sinceTileChangeMs = routeState.lastTileChangeAtMs > 0L
                    ? System.currentTimeMillis() - routeState.lastTileChangeAtMs
                    : -1L;
            boolean poseWalkingNearPath = Rs2WalkerStallPolicy.poseCountsAsProgress(
                    Rs2Player.isMoving(), nearPath, sinceTileChangeMs, POSE_PROGRESS_TILE_CHANGE_WINDOW_MS);
            boolean animProgressNearPath = anim && !routeState.prevAnimatingForStuckCheck && nearPath;
            if (animProgressNearPath || poseWalkingNearPath) {
                routeState.lastMovedTimeMs = System.currentTimeMillis();
                routeState.stuckCount = 0;
            } else {
                routeState.stuckCount++;
            }
        } else {
            routeState.lastTileChangeAtMs = System.currentTimeMillis();
            routeState.stuckCount = 0;
            routeState.lastMovedTimeMs = System.currentTimeMillis();
        }
        routeState.prevAnimatingForStuckCheck = anim;
    }

    // Base stall threshold. See stallThresholdMs() for activity-aware scaling.
    // RuneLite exposes no real-time ping, so we skip pure latency scaling and rely on
    // observable activity states that also correlate with legitimately-stuck players.
    //
    // Held at 12s deliberately. The longest LEGITIMATE stationary stretch measured across four live
    // farm runs is ~7.1s, during a transport handoff — the player is standing still while a ship or
    // teleport resolves and nothing is wrong. 12s keeps roughly five seconds of margin over that.
    // Cutting the base is the obvious way to make recovery snappier and the wrong one: it trades a
    // slow recovery for a walker that interrupts its own transports.
    static final long STALL_BASE_MS = 12_000;
    static final double STALL_COMBAT_MULTIPLIER = 2.0;
    static final double STALL_ANIMATING_MULTIPLIER = 1.5;
    static final double STALL_MOVING_MULTIPLIER = 1.35;
    /**
     * A sticky interim waypoint used to buy a 1.75x threshold, on the reasoning that a long segment
     * can outlast the base stall. It cannot: while the player is walking toward the interim, every
     * tile change refreshes the clock. The multiplier only ever bound the case where the player is
     * STATIONARY with an interim live — and the idle nudge already rescues that within ~1-2s, long
     * before any stall threshold is in sight. Kept above 1.0 for the tick or two between issuing a
     * click and the first step.
     */
    static final double STALL_INTERIM_MINIMAP_MULTIPLIER = 1.25;
    static final double STALL_INTERACTING_MULTIPLIER = 1.5;
    /**
     * How recently the player must have actually changed tile for the pose-based movement flag to
     * count as route progress. A walking step is ~600ms and a running one ~300ms, so a healthy walk
     * refreshes this many times over; a player turning on the spot never does.
     */
    private static final long POSE_PROGRESS_TILE_CHANGE_WINDOW_MS = 2_500L;

    private static boolean interactingActorNearWalkablePath() {
        Rs2ActiveRouteStatus routeStatus = Rs2PathApi.getActiveRouteStatus();
        if (!routeStatus.isPresent()) {
            return false;
        }
        List<WorldPoint> path = routeStatus.getWalkablePath();
        if (path.isEmpty()) {
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
                routeState.interimTargetWp != null,
                (Rs2Player.isMoving() || Rs2Player.isAnimating()) && interactingActorNearWalkablePath());
    }

    private static boolean isStuckTooLong() {
        if (Rs2WalkerStallPolicy.shouldSkipStallAccounting(LEAGUES_AREA_PENDING_STALL_MAX_AGE_MS)) {
            return false;
        }

        long routeProgressAt = routeState.routeProgressAdvancedAtMs;
        if (routeProgressAt > 0L && System.currentTimeMillis() - routeProgressAt < ROUTE_PROGRESS_STALL_GRACE_MS) {
            return false;
        }

        return routeState.lastMovedTimeMs > 0 && System.currentTimeMillis() - routeState.lastMovedTimeMs > stallThresholdMs();
    }

    /**
     * @param start
     */
    public void setStart(WorldPoint start) {
        Set<WorldPoint> targets = Rs2PathApi.getActiveRouteTargets();
        if (targets.isEmpty()) {
            return;
        }
        Rs2PathApi.setStartPointSet(true);
        if (isClientThread()) {
            Microbot.getClientThread().runOnSeperateThread(() -> restartPathfinding(start, targets));
        } else {
            restartPathfinding(start, targets);
        }
    }

    /**
     * Of these candidate tiles, the one the pathfinder can actually reach most cheaply — or null when
     * none of them is reachable.
     *
     * <p>Choosing somewhere to stand by proximity is wrong whenever a wall or a closed door separates
     * the nearest tile from the player. A local reachability BFS does not rescue it either: the BFS
     * stops at the door, so the tile on the far side — often the only usable one — is invisible to it.
     * The pathfinder is the component that knows doors and transports, and it takes a whole set of
     * targets natively, so asking it once answers the question that actually matters: <em>which of
     * these can I get to?</em>
     *
     * <p>Worked case: approaching the Black Knights' Fortress ladder from (3024,3512), the tiles beside
     * it are walkable and adjacent but walled off, while the usable approach is east through a Sturdy
     * door. Proximity picks a walled tile every time; this picks the one with a route.
     *
     * @param start      where we are pathing from
     * @param candidates tiles worth standing on, in no particular order
     * @return the reachable candidate, or null if the pathfinder cannot reach any of them
     */
    public static WorldPoint nearestReachable(WorldPoint start, Collection<WorldPoint> candidates) {
        if (start == null || candidates == null || candidates.isEmpty()) {
            return null;
        }
        Set<WorldPoint> targets = new HashSet<>(candidates);
        if (targets.contains(start)) {
            return start;
        }
        // A partial path ends somewhere that is NOT a target; only trust an endpoint we asked for.
        return Rs2PathApi.plan(Rs2RouteRequest.toAny(start, targets))
                .getReachedTarget(0)
                .orElse(null);
    }

    /**
     * Checks the distance between startpoint and endpoint using ShortestPath
     *
     * @param startpoint
     * @param endpoint
     * @return distance
     */
    public static int getDistanceBetween(WorldPoint startpoint, WorldPoint endpoint) {
        return Rs2PathApi.plan(Rs2RouteRequest.to(startpoint, endpoint)).getPath().size();
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
     * interact with interfaces like spirit tree etc...
     *
     * @param transport
     */
    /** The Lovakengj minecart destination list: TEXT entries under 947:9, one per station. */
    static final int MINECART_MENU_GROUP = 947;
    static final int MINECART_MENU_LIST_CHILD = 9;





    // Constants for widget IDs
    static final int SLOT_ONE = 26083331;
    static final int SLOT_TWO = 26083332;
    static final int SLOT_THREE = 26083333;

    static final int SLOT_ONE_CW_ROTATION = 26083347;
    static final int SLOT_ONE_ACW_ROTATION = 26083348;
    static final int SLOT_TWO_CW_ROTATION = 26083349;
    static final int SLOT_TWO_ACW_ROTATION = 26083350;
    static final int SLOT_THREE_CW_ROTATION = 26083351;
    static final int SLOT_THREE_ACW_ROTATION = 26083352;
    static int fairyRingGraphicId = 569;




    /**
     * Checks if the specified item ID corresponds to a teleportation item.
     * This method examines all available transports and determines if the given item
     * can be used for teleportation purposes, including special items like dramen staff.
     *
     * @param itemId The item ID to check for teleportation capabilities
     * @return true if the item is a teleportation item, false otherwise
     */
    public static boolean isTeleportItem(int itemId) {
        return Rs2PathApi.isTeleportItem(
                itemId,
                ItemID.DRAMEN_STAFF,
                ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF);
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

        // Convert the list to one immutable multi-target planner request.
        Set<WorldPoint> targetSet = new HashSet<>(targets);
        Rs2RouteResult route = Rs2PathApi.plan(
                Rs2RouteRequest.toAny(startPoint, targetSet).withBankItems(useBankItems));
        WorldPoint nearestTile = route.getEndpoint().orElse(null);
        if (nearestTile == null) {
            log.debug("Unable to find path to any target from starting point: " + startPoint);
            return -1;
        }

        WorldArea nearestTileArea = new WorldArea(nearestTile, tolerance, tolerance);
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
	 * Planner-independent counterpart to {@link #getTransportsForDestination(WorldPoint, boolean)}.
	 * New banking and execution code must use this exact selected-edge view.
	 */
	public static List<Rs2TransportEdge> getTransportEdgesForDestination(
		WorldPoint destination, boolean useBankItems)
	{
		return Rs2WalkerBankingPlanner.getTransportEdgesForDestination(destination, useBankItems);
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

	public static List<Rs2TransportEdge> getMissingTransportEdges(
		List<Rs2TransportEdge> transports)
	{
		return Rs2WalkerBankingPlanner.getMissingTransportEdges(transports);
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

	public static Map<Integer, Integer> getMissingTransportEdgeItemIdsWithQuantities(
		List<Rs2TransportEdge> transports)
	{
		return Rs2WalkerBankingPlanner.getMissingTransportEdgeItemIdsWithQuantities(transports);
	}

	public static Rs2TransportLoadout getMissingTransportEdgeLoadout(
		List<Rs2TransportEdge> transports)
	{
		return Rs2WalkerBankingPlanner.getMissingTransportEdgeLoadout(transports);
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
			return withShadowExecutionEvidence(
					() -> walkWithBankedTransportsAndStateLocked(
							target, distance, forceBanking));
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
        final Rs2ActiveRouteStatus routeStatus = Rs2PathApi.getActiveRouteStatus();
        if (routeStatus.isCalculating())
            return WalkerState.MOVING;

        boolean bankTripWhenCacheUnavailable = config == null || config.bankTripWhenCacheUnavailable();
        if (!forceBanking && bankTripWhenCacheUnavailable && Rs2Bank.getBankLiveEpoch() <= 0
                && System.currentTimeMillis() - routeState.lastBankBootstrapMissAtMs > BANK_BOOTSTRAP_MISS_COOLDOWN_MS) {
            WalkerState bootstrapState = bootstrapBankMirrorForBankedPathing(distance);
            if (bootstrapState == WalkerState.EXIT || bootstrapState == WalkerState.UNREACHABLE) {
                return bootstrapState;
            }
        }
        int chebyshevToTarget = pl.distanceTo(target);
        if (!forceBanking && chebyshevToTarget <= 100) {
            // Straight-line proximity says nothing about the walkable route: the Shantay gate is
            // ~30 tiles away and ~700 by inventory-only path without a pass. Skipping the compare
            // here meant no missing-item check, so gold for a purchasable gate was never withdrawn
            // and the walker silently took the detour. One direct pathfind (cheap for a close,
            // reachable target) decides whether the short-circuit is safe; a partial path counts
            // as a detour too, since banking may be exactly what unlocks the blocked transport.
            List<WorldPoint> directProbePath = getWalkPath(pl, target);
            int directProbeTiles = getTotalTilesFromPath(directProbePath, target);
            int directPathCeiling = shortWalkDirectPathCeiling(chebyshevToTarget);
            if (directProbeTiles <= directPathCeiling) {
                WebWalkLog.spInfo("bank_walk | skip_compare_short_distance dist={} directTiles={} goal={}",
                        chebyshevToTarget, directProbeTiles, target);
                return walkWithStateInternal(target, distance);
            }
            WebWalkLog.spInfo("bank_walk | short_distance_detour dist={} directTiles={} ceiling={} goal={} — running bank compare",
                    chebyshevToTarget,
                    directProbeTiles == Integer.MAX_VALUE ? "partial" : String.valueOf(directProbeTiles),
                    directPathCeiling, target);
        }
        // Check what transport items are needed
        long compareStartedAt = System.currentTimeMillis();
        long compareFromWalkStart = routeState.walkSessionStartedAtMs > 0 ? compareStartedAt - routeState.walkSessionStartedAtMs : 0L;
        WebWalkLog.tmark("compare_start", compareFromWalkStart, target, pl, "bank_vs_direct");
        TransportRouteAnalysis comparison = compareRoutes(target);
        WebWalkLog.tmark("compare_done", System.currentTimeMillis() - compareStartedAt, target, pl,
                "direct=" + comparison.getDirectDistance() + " bank=" + comparison.getBankingRouteDistance());
        List<Rs2TransportEdge> missingTransports = getMissingTransportEdges(
                Rs2WalkerBankingPlanner.getRequiredTransportEdgesFromBank(comparison));

        Rs2TransportLoadout transportLoadout = getMissingTransportEdgeLoadout(missingTransports);
        Map<Integer, Integer> missingItemsWithQuantities = transportLoadout.getWithdrawals();
        if (!missingTransports.isEmpty()) {
            WebWalkLog.bankWalkDebug("missing_items nTrans={} to={} missingKinds={} equipKinds={} satisfiable={}",
                    missingTransports.size(), target, missingItemsWithQuantities.size(),
                    transportLoadout.getEquipmentItemIds().size(), transportLoadout.isSatisfiable());
        }
        if (!transportLoadout.isSatisfiable()) {
            WebWalkLog.spWarn("bank_walk | selected bank route has no executable loadout goal={}", target);
            return forceBanking ? WalkerState.EXIT : walkWithStateInternal(target, distance);
        }
        // If no missing transport items, go directly
        if (transportLoadout.isEmpty() && !forceBanking) {
            WebWalkLog.spInfo("bank_walk | direct_no_missing_items goal={}", target);
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
                    return walkWithBankingState(
                            comparison.getBankLocation(), transportLoadout, target, distance);
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


    /**
     * When the last bootstrap attempt found no bank it is pointless — and expensive, it runs a
     * pathfind — to retry on the very next walk. Back off instead of doing it every tick. The
     * timestamp itself lives in {@link WalkerRouteState} with the rest of the mutable route state.
     */
    private static final long BANK_BOOTSTRAP_MISS_COOLDOWN_MS = 60_000;

    private static WalkerState bootstrapBankMirrorForBankedPathing(int distance) {
        WorldPoint start = Rs2Player.getWorldLocation();
        if (start == null) {
            return WalkerState.MOVING;
        }
        BankLocation nearestBank = Rs2Bank.getNearestBank(start);
        if (nearestBank == null || nearestBank.getWorldPoint() == null) {
            // No bank we recognise from here. That is a gap in BankLocation coverage, not a reason to
            // refuse to walk: the bank mirror only unlocks transports that need banked items, and the
            // ordinary route is usually fine without it. Returning EXIT aborted the whole walk, so the
            // caller re-ran this every tick and the character never went anywhere — observed near
            // (3025,3508), where the nearest-bank search returns a point matching no BankLocation.
            routeState.lastBankBootstrapMissAtMs = System.currentTimeMillis();
            WebWalkLog.spWarn("bank_cache_bootstrap | no_nearest_bank start={} — continuing unbanked", start);
            return WalkerState.MOVING;
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
     * Handles the complete banking workflow using the immutable preparation selected for the exact
     * bank-to-target route: walk to bank, withdraw, equip, close, refresh inventory-only policy and
     * continue to the target.
     *
     * @param transportLoadout Withdrawals and equipment changes required by the selected route
     * @param finalTarget The final destination after banking
     * @return WalkerState indicating the result of the banking workflow
     */
    private static WalkerState walkWithBankingState(WorldPoint bankLocation,
                                                    Rs2TransportLoadout transportLoadout,
                                                    WorldPoint finalTarget,int distance) {
        try {
            if (bankLocation == null || finalTarget == null || transportLoadout == null
                    || !transportLoadout.isSatisfiable()) {
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
            Map<Integer, Integer> missingItemsWithQuantities = transportLoadout.getWithdrawals();
            if (!missingItemsWithQuantities.isEmpty()) {
                log.debug("Withdrawing transport items with quantities: " + missingItemsWithQuantities);

                for (Map.Entry<Integer, Integer> entry : missingItemsWithQuantities.entrySet()) {
                    if (!Rs2Bank.hasBankItem(entry.getKey(), entry.getValue())) {
                        log.warn("Required transport item {} unavailable at bank (need {}) — falling back direct",
                                entry.getKey(), entry.getValue());
                        return fallbackDirectFromBank(finalTarget, distance, "bank-quantity-changed");
                    }
                }

                // Withdraw the correct amount of each unique item
                for (Map.Entry<Integer, Integer> entry : missingItemsWithQuantities.entrySet()) {
                    int itemId = entry.getKey();
                    int amountNeeded = entry.getValue();
                    int currentCount = Rs2Inventory.count(itemId);
                    int amountToWithdraw = Math.max(0, amountNeeded );

                    if (amountToWithdraw > 0) {
                        log.debug("Withdrawing {} x {} (item ID: {})", amountToWithdraw, itemId, itemId);
                        if (!Rs2Bank.withdrawX(itemId, amountToWithdraw)
                                || !sleepUntil(() -> Rs2Inventory.count(itemId)
                                        >= currentCount + amountToWithdraw, 3000)) {
                            log.warn("Failed to withdraw required transport item {} x{} — falling back direct",
                                    itemId, amountToWithdraw);
                            return fallbackDirectFromBank(finalTarget, distance, "withdraw-failed");
                        }
                    } else {
                        log.debug("Already have enough of item {}: {} (need {})", itemId, currentCount, amountNeeded);
                    }
                }

                // Wait a bit for all withdrawals to complete
                sleepTickJitter(1);
            }

            for (Integer equipmentItemId : transportLoadout.getEquipmentItemIds()) {
                if (Rs2Equipment.isWearing(equipmentItemId)) {
                    continue;
                }
                if (!Rs2Inventory.hasItem(equipmentItemId)
                        || !Rs2Bank.wearItem(equipmentItemId)
                        || !sleepUntil(() -> Rs2Equipment.isWearing(equipmentItemId), 3000)) {
                    log.warn("Failed to equip required transport provider {}", equipmentItemId);
                    return WalkerState.EXIT;
                }
            }

            // Step 4: Close bank
            Rs2Bank.closeBank();
            sleepUntil(() -> !Rs2Bank.isOpen(), 3000);
            if (Rs2Bank.isOpen()) {
                log.warn("Failed to close bank after withdrawals");
                return WalkerState.EXIT;
            }
            if (!Rs2PathApi.prepareInventoryOnlyRoute(finalTarget)) {
                log.warn("Shortest-path configuration unavailable after bank withdrawals");
                return WalkerState.EXIT;
            }
            // Step 5: Continue to final target
            log.debug("Banking complete, continuing to final target: " + finalTarget);
            return walkWithStateInternal(finalTarget, distance);

        } catch (Exception e) {
            log.error("Error in banking workflow: " + e.getMessage(), e);
            return WalkerState.EXIT;
        }
    }

    /**
     * Lets the door transaction itself cross an edge that live collision proves open. The exact
     * ledger claim is required: a walled-route envelope alone may be adjacent to the real door and
     * must never authorize a far-side click. The existing door nudge remains route-aware and waits
     * for an observed crossing before reporting success.
     */
    private static boolean tryTraverseOwnedOpenDoor(DoorAttemptLedger.Attempt exactClaim,
                                                    WorldPoint from, WorldPoint to,
                                                    WorldPoint playerLoc,
                                                    List<WorldPoint> routePath) {
        if (playerLoc == null || exactClaim == null || from == null || to == null) {
            return false;
        }
        boolean moving = Rs2Player.isMoving();
        boolean animating = Rs2Player.isAnimating();
        boolean edgePassable = !moving && !animating && Rs2Tile.isEdgePassable(from, to);
        boolean exactEdge = exactClaim.isSameDirectedEdge(from, to);
        if (!WalledDoorClaimPolicy.shouldTraverseOpenEdge(exactEdge, edgePassable, moving, animating)) {
            return false;
        }
        boolean crossed = tryDoorEdgeCrossNudge(from, to, currentTarget, routePath);
        if (crossed) {
            doorAttemptLedger.clearLatestAttempt();
            WebWalkLog.spInfo("door_owned_open_edge_crossed | edge={}->{} player={}",
                    compactWorldPoint(from), compactWorldPoint(to), compactWorldPoint(playerLoc));
        }
        return crossed;
    }

    private static WalkerState fallbackDirectFromBank(WorldPoint finalTarget, int distance, String reason) {
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 3_000);
        if (Rs2Bank.isOpen()) {
            WebWalkLog.spWarn("bank_walk | direct_fallback_bank_close_failed reason={} goal={}", reason, finalTarget);
            return WalkerState.EXIT;
        }
        boolean prepared = Rs2PathApi.prepareInventoryOnlyRoute(finalTarget);
        WebWalkLog.spWarn("bank_walk | direct_fallback reason={} prepared={} goal={}",
                reason, prepared, finalTarget);
        return prepared ? walkWithStateInternal(finalTarget, distance) : WalkerState.EXIT;
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



    /**
     * Pure settle decision after a handled transport. Settling ends as soon as the player is confirmed
     * ARRIVED — standing at/next to the transport's planned destination, neither moving nor animating —
     * after a one-tick floor for post-action state flux; {@link #TRANSPORT_POST_INTERACT_SETTLE_MS} is
     * only the ceiling for when arrival never confirms (unknown destination, drawn-out travel). The old
     * check compared against where the player stood when the transport was MARKED handled, which after
     * landing is always true while standing still — so the settle could only ever end by timeout, a fixed
     * ~900ms freeze after every single transport.
     */
    static boolean transportSettlePending(long ageMs, WorldPoint now, WorldPoint plannedDestination,
                                          boolean moving, boolean animating) {
        if (ageMs < 0L || ageMs > TRANSPORT_POST_INTERACT_SETTLE_MS) {
            return false;
        }
        if (ageMs < POST_INTERACT_SETTLE_MIN_MS) {
            return true;
        }
        if (now == null || plannedDestination == null) {
            return ageMs <= TRANSPORT_POST_INTERACT_SETTLE_MS / 2;
        }
        boolean arrivedIdle = now.getPlane() == plannedDestination.getPlane()
                && now.distanceTo2D(plannedDestination) <= 1
                && !moving && !animating;
        return !arrivedIdle;
    }

    static boolean isClientThreadReadTimeout(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof TimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }



    static String normalizeCharterWidgetText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return Rs2UiHelper.stripTagsToSpace(text)
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }

    /**
     * Whether {@code a -> b} is inside the traversal envelope of the door this walker owns within
     * the claim window. The live-collision route validator uses this as "the executor owns that
     * step, leave it alone": a shut door on the route honestly reads blocked, and recalculating
     * the route out from under an in-progress door interaction was observed on a quest door
     * (fightarena_door1, 2585,3141) that is in no transport catalog — the catalog check alone cannot
     * cover doors the walker handles purely as scene objects.
     */
    public static boolean isActiveDoorEdge(WorldPoint a, WorldPoint b) {
        long now = System.currentTimeMillis();
        DoorAttemptLedger.Attempt claim =
                doorAttemptLedger.latestAttempt(ACTIVE_DOOR_EDGE_CLAIM_MS, now);
        if (claim != null && WalledDoorClaimPolicy.ownsTraversalEdge(claim.from, claim.to, a, b)) {
            return true;
        }
        return WalledDoorClaimPolicy.isFresh(routeState.walledDoorEdgeFrom, routeState.walledDoorEdgeTo,
                routeState.walledDoorEdgeAtMs, now)
                && WalledDoorClaimPolicy.ownsTraversalEdge(
                        routeState.walledDoorEdgeFrom, routeState.walledDoorEdgeTo, a, b);
    }

    static Map<TileObject, WorldPoint> captureRawScanDoorLocationsOnClientThread() {
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

    static boolean isMiniMapRecoveryClickable(WorldPoint worldPoint) {
        return isMiniMapClickable(worldPoint);
    }
}
