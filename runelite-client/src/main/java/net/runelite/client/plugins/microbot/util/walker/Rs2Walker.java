package net.runelite.client.plugins.microbot.util.walker;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
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
import net.runelite.client.plugins.microbot.util.leaguetransport.Rs2MapOfAlacrityTransport;
import net.runelite.client.plugins.microbot.util.leaguetransport.SeasonalTransportHandler;
import net.runelite.client.plugins.microbot.util.leaguetransport.SeasonalTransportHandlers;
import net.runelite.client.plugins.microbot.util.logging.Rs2LogRateLimit;
import java.util.function.BooleanSupplier;
import net.runelite.client.plugins.microbot.util.poh.PohTeleports;
import net.runelite.client.plugins.microbot.util.poh.PohTransport;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.leaguetransport.LeaguesRegion;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;

import javax.inject.Named;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;
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
	 * Sticky interim minimap click target to avoid destination flapping when the minimap flag
	 * disappears around bends. Once we click a reachable point, keep it until we get close
	 * (<= {@link #INTERIM_CLOSE_TILES}) or progress stalls for {@link #INTERIM_PROGRESS_TIMEOUT_MS}.
	 */
	private static volatile WorldPoint interimTargetWp = null;
	private static volatile int interimTargetIdx = -1;
	private static volatile long interimSetAtMs = 0L;
	private static volatile long interimLastProgressAtMs = 0L;
	private static volatile int interimLastBestPathIdx = -1;
	private static volatile long interimLastRetargetAtMs = 0L;

	private static final int INTERIM_CLOSE_TILES = 4;
	private static final long INTERIM_PROGRESS_TIMEOUT_MS = 2500L;
	private static final long INTERIM_MAX_AGE_MS = 10_000L;
	private static final long INTERIM_RETARGET_COOLDOWN_MS = 900L;

    static final int OFFSET = 10; // max offset of the exact area we teleport to

    // Set this to true, if you want to calculate the path but do not want to walk to it
    static boolean debug = false;

    /** Bounds tail recursion that was previously unbounded {@code processWalk} self-calls. */
    private static final int MAX_PROCESS_WALK_TAIL_ITERATIONS = 64;

    /** Substrings for game-object names treated like doors (pathing heuristics). */
    private static final String[] DOOR_LIKE_NAME_FRAGMENTS = {
            "door", "gate", "barrier", "stile", "portcullis", "archway", "cattlegate", "fence"
    };

    /** Lower index = higher priority when multiple actions match (prefix, ASCII lower). */
    private static final List<String> DOOR_ACTION_PRIORITY = List.of(
            "pay-toll", "pick-lock", "walk-through", "go-through", "open", "enter",
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
    // setTarget() / recalculatePath() stay unlocked — they are the cross-thread cancel
    // path and the volatile currentTarget read inside the walker loop picks up nulls.
    private static final ReentrantLock walkerLock = new ReentrantLock();

    /**
     * First-seen dedupe keys when both seasonal handlers decline (debug-only): packed destination hex (or {@code nodest}),
     * then truncated {@code displayInfo} plus {@code |h} + hex {@link String#hashCode()} so long-prefix collisions split by dest.
     * At most {@link #SEASONAL_HANDLER_MISS_LOG_CAP} distinct keys ever log — then new misses are silent until JVM restart.
     */
    private static final Set<String> SEASONAL_HANDLER_MISS_LOGGED = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger SEASONAL_HANDLER_MISS_LOGGED_COUNT = new AtomicInteger(0);
    private static final int SEASONAL_HANDLER_MISS_LOG_CAP = 128;

    /** Same package (e.g. unit tests) only — not part of script API. */
    static void clearSeasonalHandlerMissDedupeForTesting()
    {
        SEASONAL_HANDLER_MISS_LOGGED.clear();
        SEASONAL_HANDLER_MISS_LOGGED_COUNT.set(0);
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
            stallRecalcCount.set(0);
            partialRetryCount.set(0);
            unreachableCount.set(0);
            leaguesLockAttributedCount.set(0);
            leaguesLockStaleCount.set(0);
            leaguesLockParseMissCount.set(0);
            seasonalHandlerMissCount.set(0);
            doorRejectByCause.clear();
            doorRejectSummaryLogSeq.set(0);
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
        return walkTo(x, y, plane, config.reachedDistance());
    }

    public static boolean walkTo(int x, int y, int plane, int distance) {
        return walkWithState(new WorldPoint(x, y, plane), distance) == WalkerState.ARRIVED;
    }


    public static boolean walkTo(WorldPoint target) {
        return walkWithState(target, config.reachedDistance()) == WalkerState.ARRIVED;
    }

    public static boolean walkTo(WorldPoint target, int distance) {
        return walkWithState(target, distance) == WalkerState.ARRIVED;
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
            log.warn("[Walker] walkWithStateTry: null target");
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
     * @param target
     * @param distance
     * @return
     */
    private static WalkerState walkWithStateInternal(WorldPoint target, int distance) {
        int distToTarget = Rs2Player.getWorldLocation().distanceTo(target);
        LocalPoint localTarget = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), target);
        boolean walkableCheck = Rs2Tile.isWalkable(localTarget);
        boolean reachableTileCheck = distToTarget <= distance && Rs2Tile.getReachableTilesFromTile(Rs2Player.getWorldLocation(), distance).containsKey(target);

        if (reachableTileCheck || (!walkableCheck && distToTarget <= distance)) {
            return WalkerState.ARRIVED;
        }

        final Pathfinder pathfinder = ShortestPathPlugin.getPathfinder();
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
        ShortestPathPlugin.setReachedDistance(distance);
        stuckCount = 0;
        lastMovedTimeMs = System.currentTimeMillis();
		interimTargetWp = null;
		interimTargetIdx = -1;
		interimSetAtMs = 0L;
		interimLastProgressAtMs = 0L;
		interimLastBestPathIdx = -1;
		interimLastRetargetAtMs = 0L;

        if (Microbot.getClient().isClientThread()) {
            log.warn("Please do not call the walker from the main thread");
            return WalkerState.EXIT;
        }

		closeWorldMap();
        kickStartShortLocalWalk(target, distance);
        return processWalk(target, distance);
    }

    /**
     * @param target
     * @return
     */
    public static WalkerState walkWithState(WorldPoint target) {
        return walkWithState(target, config.reachedDistance());
    }

    /**
     * Core walk method contains all the logic to successfully walk to the destination
     * this contains doors, game objects, teleports, spells etc...
     *
     * @param target
     * @param distance
     */
    private static WalkerState processWalk(WorldPoint target, int distance) {
        return processWalk(target, distance, 0);
    }

    private static WalkerState processWalk(WorldPoint target, int distance, int partialRetries) {
        if (debug) {
            return WalkerState.EXIT;
        }
        int partialRetriesWorking = partialRetries;
        WorldPoint lastAttemptedMinimapClick = null;
        boolean lastAttemptedMinimapClickOk = false;
        long lastAttemptedMinimapClickAtMs = 0L;
        for (int processWalkTail = 0; processWalkTail < MAX_PROCESS_WALK_TAIL_ITERATIONS; processWalkTail++) {
        try {
            if (!Microbot.isLoggedIn()) {
                setTarget(null);
                return WalkerState.EXIT;
            }
            if (isWalkCancelled(target)) {
                return WalkerState.EXIT;
            }

            Pathfinder pathfinder = ShortestPathPlugin.getPathfinder();
            if (pathfinder == null) {
                pathfinder = sleepUntilNotNull(ShortestPathPlugin::getPathfinder, 2_000);
                if (isWalkCancelled(target)) {
                    return WalkerState.EXIT;
                }
                if (pathfinder == null) {
                    setTarget(null);
                    return WalkerState.EXIT;
                }
            }

            if (!pathfinder.isDone()) {
                boolean isDone = sleepUntilTrue(pathfinder::isDone, 100, 10_000);
                if (isWalkCancelled(target)) {
                    return WalkerState.EXIT;
                }
                if (!isDone) {
                    setTarget(null);
                    return WalkerState.EXIT;
                }
            }

            if (ShortestPathPlugin.getMarker() == null) {
                restoreTargetMarker(target);
            }

            final List<WorldPoint> rawPath = pathfinder.getPath();
            final List<WorldPoint> path = pathfinder.getWalkablePath();
            final WorldPoint dst;
            if (path == null || path.isEmpty()) {
                dst = Rs2Player.getWorldLocation();
            } else {
                dst = path.get(path.size()-1);
            }

            boolean partialPath = false;
            if (dst == null || dst.distanceTo(target) > distance) {
                if (path != null && path.size() > 1) {
                    log.info("[Walker] Path endpoint {} is {} tiles from target {}, walking partial path ({} tiles)",
                            dst, dst.distanceTo(target), target, path.size());
                    partialPath = true;
                } else {
                    Telemetry.recordUnreachable("no-walkable-path", Rs2Player.getWorldLocation(),
                            target, dst, path == null ? 0 : path.size(), distance, pathfinder);
                    setTarget(null);
                    return WalkerState.UNREACHABLE;
                }
            }

            if (path == null || path.isEmpty()) {
                return WalkerState.ARRIVED;
            }

            if (isNear(dst)) {
                setTarget(null);
            }

            checkIfStuck();
            if (isWalkCancelled(target)) {
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
                log.info("[Walker] Stall recalc: sinceMoved={}ms threshold={}ms (inCombat={} animating={} interacting={})",
                        sinceMoved, threshold,
                        Rs2Player.isInCombat(), Rs2Player.isAnimating(), Rs2Player.isInteracting());
                if (lastAttemptedMinimapClick != null) {
                    log.info("[Walker] Stall recalc context: lastClick={} ok={} age={}ms interim={}",
                            lastAttemptedMinimapClick,
                            lastAttemptedMinimapClickOk,
                            Math.max(0L, System.currentTimeMillis() - lastAttemptedMinimapClickAtMs),
                            interimTargetWp);
                }
                lastMovedTimeMs = System.currentTimeMillis();
                stuckCount = 0;
                setTarget(target);
                continue;
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

            int indexOfStartPoint = getClosestTileIndex(path);
            if (indexOfStartPoint == -1) {
                setTarget(null);
                return WalkerState.EXIT;
            }

            lastPosition = Rs2Player.getWorldLocation();

            if (Rs2Player.getWorldLocation().distanceTo(target) == 0 || path.size() <= 1) {
                setTarget(null);
                return WalkerState.ARRIVED;
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
            final int HANDLER_RANGE = 8;
            WalkerState directShortWalk = tryDirectShortWalk(target, distance, path, inInstance);
            if (directShortWalk != WalkerState.MOVING) {
                return directShortWalk;
            }

            if (rawPath != null && path != null
                    && handleNearbyRawPathSceneObjects(rawPath, HANDLER_RANGE)) {
                doorOrTransportResult = true;
                exitReason = "raw-path-scene-object-handled";
            }

            if (!doorOrTransportResult
                    && handleCurrentTileTransportTowardPath(rawPath, path, target)) {
                doorOrTransportResult = true;
                exitReason = "current-tile-transport-handled";
            }

            for (int i = indexOfStartPoint; !doorOrTransportResult && i < path.size(); i++) {
                WorldPoint currentWorldPoint = path.get(i);
                if (isWalkCancelled(target)) {
                    return WalkerState.EXIT;
                }

                if (ShortestPathPlugin.getMarker() == null) {
                    restoreTargetMarker(target);
                }
                // Marker is a UI/overlay artifact (ShortestPath plugin). Walking must not depend
                // on its presence; scripts can clear it mid-walk.

                // Proactively resolve doors/gates near the player. When pathing stalls at a door,
                // the next minimap click often repeats the same blocked segment. Scan for any
                // door-like obstruction on the next few path steps within a short radius and
                // handle it with a bounded wait.
                if (!Rs2Player.isMoving()) {
                    if (tryHandleBlockingPathObjectsWithTimeout(path, i, 5, 10, 1500)) {
                        exitReason = "path-blocker-handled";
                        break;
                    }
                }

                if (!isNearPath()) {
                    // Avoid mid-walk recalculation while the player is still moving. recalculatePath()
                    // cancels the pathfinder and waits up to 10s for a new one — a visible stall.
                    // isStuckTooLong() will trigger a real recalculation if progress actually halts.
                    boolean movingOrRecentlyMoved = Rs2Player.isMoving()
                            || (lastMovedTimeMs > 0 && System.currentTimeMillis() - lastMovedTimeMs < 2000);
                    if (movingOrRecentlyMoved) {
                        exitReason = "off-path-but-moving";
                        break;
                    }
                    Telemetry.recordOffPathRecalc(Rs2Player.getWorldLocation(), path.size());
                    log.info("[Walker] No longer near path, recalculating");
                    if (config.cancelInstead()) {
                        setTarget(null);
                    } else {
                        recalculatePath();
                    }
                    exitReason = "not-near-path";
                    break;
                }

                // Gate scene-object handlers to segments near the player. Doors/rockfalls/transports
                // can only be interacted with when the object is in the loaded scene (near the player),
                // and these calls do scene-object scans that add up across 100+ segment paths.
                int segDistance = currentWorldPoint.distanceTo2D(Rs2Player.getWorldLocation());
                if (segDistance <= HANDLER_RANGE) {
                    doorOrTransportResult = handleDoorsWithTimeout(path, i, 1500);
                    if (doorOrTransportResult) {
                        exitReason = "door-handled";
                        break;
                    }

                    doorOrTransportResult = handleRockfall(path, i);
                    if (doorOrTransportResult) {
                        exitReason = "rockfall-handled";
                        break;
                    }

                    if (PohTeleports.isInHouse() || !inInstance) {
                        doorOrTransportResult = handleTransports(path, i);
                    }

                    if (doorOrTransportResult) {
                        exitReason = "transport-handled";
                        break;
                    }
                }

                boolean tileReachable = Rs2Tile.isTileReachable(currentWorldPoint);
                if (!tileReachable && !inInstance) {
                    // Common stall case: path steps beyond a closed door are unreachable, so the
                    // loop would otherwise "continue" without ever issuing a minimap click and
                    // without triggering door logic (if the unreachable tile is further than the
                    // handler range). Treat the first unreachable tile as a blocker signal:
                    // try door handling on the edge that leads into it, then break so the outer
                    // loop can re-evaluate.
                    WorldPoint playerLoc = Rs2Player.getWorldLocation();
                    if (playerLoc != null) {
                        int unreachableDist = currentWorldPoint.distanceTo2D(playerLoc);
                        if (unreachableDist <= HANDLER_RANGE + 2) {
                            log.info("[Walker] unreachable path tile near player: tile={} idx={}/{} player={} target={}",
                                    currentWorldPoint, i, path.size(), playerLoc, target);

                            int edgeIdx = Math.max(indexOfStartPoint, i - 1);
                            if (handleDoorsWithTimeout(path, edgeIdx, 1500)) {
                                exitReason = "door-handled-unreachable";
                                break;
                            }
                            // Fallback: only interact with objects on/adjacent to blocked path edges
                            // within ~15 tiles. Prevents clicking already-open / unrelated doors.
                            final long nowMs = System.currentTimeMillis();
                            if (nowMs - lastDoorPathAdjAttemptAtMs > 1200) {
                                lastDoorPathAdjAttemptAtMs = nowMs;
                                if (tryResolvePathAdjacentBlocker(playerLoc, path, edgeIdx, 8, 15)) {
                                    exitReason = "door-handled-path-adj-scan";
                                    break;
                                }
                            }

                            // If we still can't resolve a blocker by interaction, do not stall.
                            // Click a reachable "progress" tile that advances toward the target/path.
                            // This keeps the walker responsive and usually moves us into the door's
                            // interaction range.
                            Set<WorldPoint> reachable = Rs2Tile.getReachableTilesFromTile(playerLoc, 5).keySet();
                            if (reachable != null && !reachable.isEmpty()) {
                                WorldPoint best = null;
                                int bestDist = Integer.MAX_VALUE;
                                for (WorldPoint wp : reachable) {
                                    if (wp == null) continue;
                                    // Prefer tiles that move closer to ultimate target.
                                    int d = wp.distanceTo2D(target);
                                    if (best == null || d < bestDist) {
                                        best = wp;
                                        bestDist = d;
                                    }
                                }
                                if (best != null && !best.equals(playerLoc)) {
                                    boolean clicked = Rs2Walker.walkMiniMap(getPointWithWallDistance(best));
                                    log.info("[Walker] unreachable recovery click: clicked={} to={} distToTarget={}",
                                            clicked, best, bestDist);
                                    exitReason = "unreachable-recovery-click";
                                    break;
                                }
                            }
                            exitReason = "tile-unreachable-near-player";
                            break;
                        }
                    }
                    continue;
                }
                nextWalkingDistance = Rs2Random.between(9, 12);
                int dist2d = currentWorldPoint.distanceTo2D(Rs2Player.getWorldLocation());
                if (dist2d > nextWalkingDistance) {
                    // Minimap clickable area is a circle, so reach is a Euclidean radius —
                    // cardinal tiles reach ~13, diagonals ~9. Empirically 14 was too
                    // optimistic (clicks at 13.5–13.9 Euclidean missed the clip).
                    final int MINIMAP_REACH_EUCLIDEAN = 13;
                    WorldPoint playerLoc = Rs2Player.getWorldLocation();

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
								final WorldPoint posBeforeWait = playerLoc;
								sleepUntil(() ->
												interimFinal.distanceTo2D(Rs2Player.getWorldLocation()) <= INTERIM_CLOSE_TILES
														|| !Rs2Player.isMoving(),
										2000);
								if (posBeforeWait.distanceTo2D(Rs2Player.getWorldLocation()) > 0 || Rs2Player.isMoving()) {
									lastMovedTimeMs = System.currentTimeMillis();
									stuckCount = 0;
								}
								exitReason = "interim-in-flight";
								break;
							} else {
								// Not moving but still far from the interim checkpoint. Treat the interim
								// as stale and pick a fresh checkpoint below (could still resolve to the
								// same tile, but ensures we actually issue a new click).
								interimTargetWp = null;
								interimTargetIdx = -1;
								interimSetAtMs = 0L;
								interimLastProgressAtMs = 0L;
								interimLastBestPathIdx = -1;
								interimLastRetargetAtMs = 0L;
							}
						}
						// Close enough: allow selecting a new checkpoint.
						interimTargetWp = null;
						interimTargetIdx = -1;
						interimSetAtMs = 0L;
						interimLastProgressAtMs = 0L;
						interimLastBestPathIdx = -1;
						interimLastRetargetAtMs = 0L;
					}

                    int targetIdx = findFurthestClickableIndex(path, i, playerLoc,
                            wp -> {
                                Set<Transport> ts = ShortestPathPlugin.getTransports().get(wp);
                                return ts != null && !ts.isEmpty();
                            },
                            MINIMAP_REACH_EUCLIDEAN);
                    WorldPoint targetWp = path.get(targetIdx);
                    // Forward waypoint out of minimap reach (e.g., diagonal PathSmoother
                    // segment at Chebyshev 10 = Euclidean 14.1). Backward-scan returns
                    // the previous in-reach waypoint, but clicking it walks backward or
                    // barely advances. Instead, interpolate a point close to the minimap
                    // edge toward the forward waypoint — the server's walk-here
                    // pathfinder routes through whatever is blocking line-of-sight
                    // (door, gate, diagonal offset). Each interpolated click covers
                    // ~12 tiles, matching a human clicking the furthest visible tile.
                    if (targetIdx < i) {
                        targetWp = interpolateClickableTarget(
                                path,
                                i,
                                playerLoc,
                                targetWp,
                                MINIMAP_REACH_EUCLIDEAN - 1,
                                wp -> inInstance || isKnownWalkableOrUnloaded(wp));
                        targetIdx = Math.max(indexOfStartPoint, i - 1);
                    } else if (euclideanSq(targetWp, playerLoc) > MINIMAP_REACH_EUCLIDEAN * MINIMAP_REACH_EUCLIDEAN) {
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
						if (stickyDist <= INTERIM_CLOSE_TILES || nowMs - interimSetAtMs > INTERIM_MAX_AGE_MS) {
							interimTargetWp = null;
							interimTargetIdx = -1;
							interimSetAtMs = 0L;
							interimLastProgressAtMs = 0L;
							interimLastBestPathIdx = -1;
							interimLastRetargetAtMs = 0L;
						} else {
							// U-turn safe progress: track progress along the path index, not Euclidean
							// distance-to-target (which can increase on U-shaped routes).
							int bestIdxNow = getClosestTileIndex(path);
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
                    WorldPoint clickTarget = inInstance ? targetWp : getPointWithWallDistance(targetWp);
                    if (!inInstance && !Rs2Tile.isTileReachable(clickTarget)) {
                        WorldPoint rawReachableTarget = findFurthestReachableRawPathPoint(rawPath, playerLoc,
                                MINIMAP_REACH_EUCLIDEAN - 1);
                        if (rawReachableTarget != null) {
                            targetWp = rawReachableTarget;
                            clickTarget = rawReachableTarget;
                        }
                    }
                    boolean clicked = Rs2Walker.walkMiniMap(clickTarget);
                    if (!clicked) {
                        clicked = walkMiniMapToward(clickTarget, playerLoc, MINIMAP_REACH_EUCLIDEAN - 1);
                    }
                    if (isWalkCancelled(target)) {
                        return WalkerState.EXIT;
                    }
                    lastAttemptedMinimapClick = targetWp;
                    lastAttemptedMinimapClickOk = clicked;
                    lastAttemptedMinimapClickAtMs = nowMs;
                    if (clicked) {
						interimTargetWp = targetWp;
						interimTargetIdx = targetIdx;
						interimSetAtMs = nowMs;
						interimLastProgressAtMs = nowMs;
						interimLastBestPathIdx = getClosestTileIndex(path);
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
                            WorldPoint now = Rs2Player.getWorldLocation();
                            if (b.distanceTo2D(now) <= proximityWake) return true;
                            return before.distanceTo2D(now) >= progressCap;
                        }, 2000);
                        WorldPoint afterClickWait = Rs2Player.getWorldLocation();
                        if (afterClickWait != null && afterClickWait.equals(before) && !Rs2Player.isMoving()
                                && walkReachableMiniMapToward(b, before, MINIMAP_REACH_EUCLIDEAN - 1)) {
                            sleepUntil(() -> {
                                if (isWalkCancelled(target)) return true;
                                WorldPoint now = Rs2Player.getWorldLocation();
                                return now != null && (b.distanceTo2D(now) <= proximityWake || !now.equals(before) || Rs2Player.isMoving());
                            }, 2000);
                        }
                        if (isWalkCancelled(target)) {
                            return WalkerState.EXIT;
                        }

                        if (!Rs2Player.isMoving()) {
                            if (handleNearbyRawPathSceneObjects(rawPath, HANDLER_RANGE)) {
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
                        interimLastRetargetAtMs = 0L;
                        sleepUntil(() -> isWalkCancelled(target) || !Rs2Player.isMoving(), 2000);
                        if (isWalkCancelled(target)) {
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

            if (!"end-of-path".equals(exitReason)) {
                log.info("[Walker] processWalk early-exit: reason={} player={} target={} pathEnd={} idxStart={}/{}",
                        exitReason,
                        Rs2Player.getWorldLocation(),
                        target,
                        path.get(path.size() - 1),
                        indexOfStartPoint,
                        path.size());
            }

            // Only do the final-tile canvas click if we iterated the whole path cleanly.
            // Exiting because the player left the path ("off-path-but-moving"/"not-near-path")
            // means the player is still walking somewhere else — don't clobber that destination.
            if (!doorOrTransportResult && "end-of-path".equals(exitReason)) {
                if (isWalkCancelled(target)) {
                    return WalkerState.EXIT;
                }
                if (!path.isEmpty()) {
                    var moveableTiles = Rs2Tile.getReachableTilesFromTile(path.get(path.size() - 1), Math.min(3, distance)).keySet().toArray(new WorldPoint[0]);
                    var finalTile = (config.randomizeFinalTile() && moveableTiles.length > 0) ? moveableTiles[Rs2Random.between(0, moveableTiles.length)] : path.get(path.size() - 1);

                    if (Rs2Tile.isTileReachable(finalTile) && Rs2Player.getWorldLocation().distanceTo(finalTile) >= distance) {
                        if (Rs2Walker.walkFastCanvas(finalTile)) {
                            sleepUntil(() -> isWalkCancelled(target) || Rs2Player.getWorldLocation().distanceTo(finalTile) < 2, 3000);
                            if (isWalkCancelled(target)) {
                                return WalkerState.EXIT;
                            }
                        }
                    }
                }
            }
            int finalDist = Rs2Player.getWorldLocation().distanceTo(target);
            if (finalDist <= distance) {
                setTarget(null);
                return WalkerState.ARRIVED;
            } else if (partialPath) {
                if (isWalkCancelled(target)) {
                    return WalkerState.EXIT;
                }
                if (partialRetriesWorking < 3) {
                    Telemetry.recordPartialRetry(partialRetriesWorking + 1, finalDist);
                    log.info("[Walker] Walked partial path ({} tiles remaining), retrying from current position (attempt {}/3)",
                            finalDist, partialRetriesWorking + 1);
                    recalculatePath();
                    partialRetriesWorking++;
                    continue;
                }
                log.info("[Walker] Walked partial path, exhausted retries. final distance to target: {}", finalDist);
                Telemetry.recordUnreachable("partial-retries-exhausted", Rs2Player.getWorldLocation(),
                        target, Rs2Player.getWorldLocation(), 0, distance, ShortestPathPlugin.getPathfinder());
                setTarget(null);
                return WalkerState.UNREACHABLE;
            } else {
                if ("off-path-but-moving".equals(exitReason)) {
                    // Wait for the player to re-enter the path or to stop moving. Prevents a tight
                    // recursion loop that would spin on isNearPath() while the player is walking.
                    sleepUntil(() -> isWalkCancelled(target) || isNearPath() || !Rs2Player.isMoving(), 2000);
                    if (isWalkCancelled(target)) {
                        return WalkerState.EXIT;
                    }
                }
                continue;
            }
        } catch (Exception ex) {
            if (ex instanceof InterruptedException || ex.getCause() instanceof InterruptedException) {
                log.info("Pathfinder was interrupted, exiting: 397");
                setTarget(null);
                return WalkerState.EXIT;
            }
            log.error("Exception in Rs2Walker:", ex);
            log.info("Exiting walker: 403");
            return WalkerState.EXIT;
        }
        }
        log.warn("[Walker] processWalk: exceeded max tail iterations ({}) for target={}",
                MAX_PROCESS_WALK_TAIL_ITERATIONS, target);
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
        var tiles = Rs2Tile.getReachableTilesFromTile(target, 1);

        var localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), target);
        if (Microbot.getClient().getTopLevelWorldView().getCollisionMaps() != null && localPoint != null) {
            int[][] flags = Microbot.getClient().getTopLevelWorldView().getCollisionMaps()[Microbot.getClient().getTopLevelWorldView().getPlane()].getFlags();

            if (hasMinimapRelevantMovementFlag(localPoint, flags)) {
                for (var tile : tiles.keySet()) {
                    var localTilePoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), tile);
                    if (localTilePoint == null)
                        continue;

                    if (!hasMinimapRelevantMovementFlag(localTilePoint, flags))
                        return tile;
                }
            }

            int data = flags[localPoint.getSceneX()][localPoint.getSceneY()];

            Set<MovementFlag> movementFlags = MovementFlag.getSetFlags(data);

            if (movementFlags.contains(MovementFlag.BLOCK_MOVEMENT_EAST)
                    || movementFlags.contains(MovementFlag.BLOCK_MOVEMENT_WEST)
                    || movementFlags.contains(MovementFlag.BLOCK_MOVEMENT_NORTH)
                    || movementFlags.contains(MovementFlag.BLOCK_MOVEMENT_SOUTH)) {
                for (var tile : tiles.keySet()) {
                    var localTilePoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), tile);
                    if (localTilePoint == null)
                        continue;

                    int tileData = flags[localTilePoint.getSceneX()][localTilePoint.getSceneY()];
                    Set<MovementFlag> tileFlags = MovementFlag.getSetFlags(tileData);

                    if (tileFlags.isEmpty())
                        return tile;
                }
            }
        }

        return target;
    }

    private static boolean isKnownWalkableOrUnloaded(WorldPoint target) {
        if (target == null) {
            return false;
        }

        LocalPoint localTarget = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), target);
        return localTarget == null || Rs2Tile.isWalkable(localTarget);
    }

    private static boolean isWalkCancelled(WorldPoint target) {
        WorldPoint activeTarget = currentTarget;
        return target == null || activeTarget == null || !target.equals(activeTarget)
                || Thread.currentThread().isInterrupted();
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

    private static WorldPoint findFurthestReachableRawPathPoint(List<WorldPoint> rawPath,
                                                                WorldPoint playerLoc,
                                                                int maxEuclidean) {
        if (rawPath == null || rawPath.isEmpty() || playerLoc == null) {
            return null;
        }
        int closestRawIndex = getClosestTileIndex(rawPath);
        if (closestRawIndex < 0) {
            return null;
        }

        int maxSq = maxEuclidean * maxEuclidean;
        Set<WorldPoint> reachable = Rs2Tile.getReachableTilesFromTile(playerLoc, Math.max(2, maxEuclidean)).keySet();
        WorldPoint best = null;
        for (int rawIndex = closestRawIndex; rawIndex < rawPath.size(); rawIndex++) {
            WorldPoint candidate = rawPath.get(rawIndex);
            if (candidate == null || candidate.getPlane() != playerLoc.getPlane()) {
                break;
            }
            if (euclideanSq(candidate, playerLoc) > maxSq) {
                break;
            }
            if (reachable.contains(candidate)) {
                best = candidate;
            }
        }
        return best;
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

        Rs2Player.toggleRunEnergy(toggleRun);
        Point canv;
        LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), worldPoint);

        if (Microbot.getClient().getTopLevelWorldView().isInstance() && localPoint == null) {
            localPoint = Rs2LocalPoint.fromWorldInstance(worldPoint);
        }

        if (localPoint == null) {
            log.error("Tried to walk worldpoint {} using the canvas but localpoint returned null", worldPoint);
            return false;
        }

        canv = Perspective.localToCanvas(Microbot.getClient(), localPoint, Microbot.getClient().getTopLevelWorldView().getPlane());

        int canvasX = canv != null ? canv.getX() : -1;
        int canvasY = canv != null ? canv.getY() : -1;

        //if the tile is not on screen, use minimap
        if (!Rs2Camera.isTileOnScreen(localPoint) || canvasX < 0 || canvasY < 0) {
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
        if (ShortestPathPlugin.getPathfinderConfig().getTransports().isEmpty()) {
            ShortestPathPlugin.getPathfinderConfig().refresh();
        }
        Pathfinder pathfinder = new Pathfinder(ShortestPathPlugin.getPathfinderConfig(), start, destination);
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
        boolean originalUseBankItems = ShortestPathPlugin.getPathfinderConfig().isUseBankItems();
        WorldArea pathArea = null;

        // Create centered WorldArea for the object instead of corner-based
        WorldPoint objectSouthWest = new WorldPoint(
                worldPoint.getX() - (sizeX + 2) / 2,
                worldPoint.getY() - (sizeY + 2) / 2,
                worldPoint.getPlane()
        );
        WorldArea objectArea = new WorldArea(objectSouthWest, sizeX + 2, sizeY + 2);

        try {
            ShortestPathPlugin.getPathfinderConfig().setUseBankItems(useBankedItems);
            ShortestPathPlugin.getPathfinderConfig().refresh(worldPoint);
            if (ShortestPathPlugin.getPathfinderConfig().getTransports().isEmpty()) {
                ShortestPathPlugin.getPathfinderConfig().refresh(worldPoint);
            }
            Pathfinder pathfinder = new Pathfinder(ShortestPathPlugin.getPathfinderConfig(), Rs2Player.getWorldLocation(), worldPoint);
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
            ShortestPathPlugin.getPathfinderConfig().setUseBankItems(originalUseBankItems);
            ShortestPathPlugin.getPathfinderConfig().refresh(worldPoint);
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
        ShortestPathPlugin.getPathfinderConfig().refresh(target);
        long pathfinderStartTime = System.nanoTime();
        Pathfinder pathfinderLocal = new Pathfinder(ShortestPathPlugin.getPathfinderConfig(), start, target);
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
        int currentIndex = indexOfStartPoint;

        // Loop through the path until the end
        while (currentIndex < path.size()) {
            WorldPoint currentPoint = path.get(currentIndex);
            // Get any transports that start at this point (or keyed by this point)
            Set<Transport> transportsAtPoint = ShortestPathPlugin.getTransports()
                    .getOrDefault(currentPoint, new HashSet<>());
            boolean foundTransport = false;
            // sort by type to prioritize teleportation items first, then other types
            transportsAtPoint = transportsAtPoint.stream()
                    .sorted(Comparator.comparing(Transport::getType, (type1, type2) -> {
                        // sort teleportation items by preference transport type for the current path point.

                        if (type1 == prefTransportType && type2 != prefTransportType) {
                            return -1;
                        }
                        if (type2 == prefTransportType && type1 != prefTransportType) {
                            return 1;
                        }
                        // For all other types, use natural enum ordering
                        return type1.compareTo(type2);
                    }))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            // Iterate over each available transport
            for (Transport transport : transportsAtPoint) {

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
                    int destIndex = path.indexOf(transport.getDestination());
                    if (destIndex != -1) {
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
                    if (transport.getOrigin() != null &&
                            Rs2Player.getWorldLocation().getPlane() != transport.getOrigin().getPlane()) {
                        continue;
                    }

                    // For non-teleportation transports, ensure both origin and destination exist in the path
                    // and that the destination comes after the origin.
                    int indexOfDestination = path.indexOf(transport.getDestination());
                    if (transport.getType() != TransportType.TELEPORTATION_ITEM
                            && transport.getType() != TransportType.TELEPORTATION_SPELL
                            && !isLeaguesAreaTeleport) {
                        int indexOfOrigin = path.indexOf(transport.getOrigin());
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

        log.info("\n\nFound {} transports for path from {} to {}", transportList.size(), path.get(0), path.get(path.size() - 1));

        // Apply filtering and requirement setup if requested
        if (applyFiltering) {
            transportList = applyTransportFiltering(transportList);
        }

        return transportList;
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
                        t.getType() == TransportType.MAGIC_CARPET ||
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

    private static boolean handleRockfall(List<WorldPoint> path, int index) {
        if (ShortestPathPlugin.getPathfinder() == null) return false;

        if (index == path.size() - 1) return false;

        // If we are in instance, ignore checking RegionID
        if(Microbot.getClient().getTopLevelWorldView().isInstance()) return false;

        // If we are not inside of the Motherloade mine, ignore the following logic
        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        if (playerLoc == null || playerLoc.getRegionID() != 14936 || currentTarget == null || currentTarget.getRegionID() != 14936) return false;

        // We kill the path if no pickaxe is found to avoid walking around like an idiot
        if (!Rs2Inventory.hasItem("pickaxe")) {
            if (!Rs2Equipment.isWearing("pickaxe")) {
                log.error("Unable to find pickaxe to mine rockfall");
                setTarget(null);
                return false;
            }
        }

        // Check current index & next index for rockfall
        for (int rockIndex = index; rockIndex < index + 2; rockIndex++) {
            var point = path.get(rockIndex);

            TileObject object = null;
            var tile = Rs2GameObject.getTiles(3).stream()
                    .filter(x -> x.getWorldLocation().equals(point))
                    .findFirst().orElse(null);

            if (tile != null)
                object = Rs2GameObject.getGameObject(point);

            if (object == null) continue;

            if (object.getId() == ObjectID.MOTHERLODE_ROCKFALL_1 || object.getId() == ObjectID.MOTHERLODE_ROCKFALL_2) {
                Rs2GameObject.interact(object, "mine");
                return sleepUntil(() -> Rs2GameObject.getGameObject(point) == null);
            }
        }

        return false;
    }

    private static WalkerState tryDirectShortWalk(WorldPoint target,
                                                  int distance,
                                                  List<WorldPoint> path,
                                                  boolean inInstance) {
        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        if (target == null || playerLoc == null || path == null || path.isEmpty()) {
            return WalkerState.MOVING;
        }

        int initialDist = playerLoc.distanceTo(target);
        if (initialDist <= distance) {
            setTarget(null);
            return WalkerState.ARRIVED;
        }

        final int directClickMaxDistance = 13;
        if (playerLoc.getPlane() != target.getPlane() || initialDist > directClickMaxDistance) {
            return WalkerState.MOVING;
        }

        WorldPoint end = path.get(path.size() - 1);
        if (end == null || end.getPlane() != target.getPlane() || end.distanceTo(target) > distance) {
            return WalkerState.MOVING;
        }

        if (!inInstance && !Rs2Tile.isWalkable(end)) {
            return WalkerState.MOVING;
        }
        if (!inInstance && !Rs2Tile.isTileReachable(end)) {
            return WalkerState.MOVING;
        }

        boolean clicked = walkMiniMap(end);
        if (!clicked) {
            clicked = walkMiniMapToward(end, playerLoc, directClickMaxDistance - 1);
        }
        if (!clicked) {
            clicked = walkFastCanvas(end);
        }
        if (!clicked) {
            return WalkerState.MOVING;
        }

        final WorldPoint before = playerLoc;
        boolean moved = sleepUntil(() -> {
            WorldPoint now = Rs2Player.getWorldLocation();
            return now != null && (now.distanceTo(target) <= distance || !now.equals(before) || Rs2Player.isMoving());
        }, 800);

        if (!moved) {
            clicked = walkFastCanvas(end);
            if (!clicked) {
                return WalkerState.MOVING;
            }
            sleepUntil(() -> {
                WorldPoint now = Rs2Player.getWorldLocation();
                return now != null && (now.distanceTo(target) <= distance || !now.equals(before) || Rs2Player.isMoving());
            }, 800);
        }

        WorldPoint afterClick = Rs2Player.getWorldLocation();
        if (afterClick != null && afterClick.distanceTo(target) <= distance) {
            setTarget(null);
            return WalkerState.ARRIVED;
        }

        sleepUntil(() -> {
            WorldPoint now = Rs2Player.getWorldLocation();
            return now != null && (now.distanceTo(target) <= distance || !Rs2Player.isMoving());
        }, 4000);

        WorldPoint afterWalk = Rs2Player.getWorldLocation();
        if (afterWalk != null && afterWalk.distanceTo(target) <= distance) {
            setTarget(null);
            return WalkerState.ARRIVED;
        }

        return WalkerState.MOVING;
    }

    private static void kickStartShortLocalWalk(WorldPoint target, int distance) {
        try {
            WorldPoint playerLoc = Rs2Player.getWorldLocation();
            if (target == null || playerLoc == null || playerLoc.getPlane() != target.getPlane()) {
                return;
            }

            int initialDist = playerLoc.distanceTo(target);
            if (initialDist <= distance || initialDist > 13) {
                return;
            }

            LocalPoint localTarget = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), target);
            if (localTarget == null || !Rs2Tile.isWalkable(localTarget)) {
                return;
            }

            boolean clicked = walkMiniMap(target);
            if (!clicked) {
                walkMiniMapToward(target, playerLoc, 12);
            }
        } catch (Exception ex) {
            log.debug("[Walker] short local kick-start failed: {}", ex.getMessage());
        }
    }

    private static boolean handleNearbyRawPathSceneObjects(List<WorldPoint> rawPath, int handlerRange) {
        if (rawPath == null || rawPath.size() < 2) {
            return false;
        }

        if (Rs2Player.isMoving()) {
            return false;
        }

        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        if (playerLoc == null) {
            return false;
        }

        int rawStart = getClosestTileIndex(rawPath);
        if (rawStart < 0) {
            return false;
        }

        int start = Math.max(0, rawStart - 1);
        int endExclusive = Math.min(rawPath.size() - 1, rawStart + 12);
        for (int i = start; i < endExclusive; i++) {
            WorldPoint currentWorldPoint = rawPath.get(i);
            if (currentWorldPoint == null
                    || currentWorldPoint.getPlane() != playerLoc.getPlane()
                    || currentWorldPoint.distanceTo2D(playerLoc) > handlerRange) {
                continue;
            }

            if (hasExplicitTransportStep(rawPath, i) && handleTransports(rawPath, i)) {
                log.info("[Walker] Raw path transport handler resolved obstacle near {}", playerLoc);
                return true;
            }

            if (handleDoors(rawPath, i, true)) {
                log.info("[Walker] Raw path door handler resolved obstacle near {}", playerLoc);
                return true;
            }

            if (handleRockfall(rawPath, i)) {
                log.info("[Walker] Raw path rockfall handler resolved obstacle near {}", playerLoc);
                return true;
            }
        }

        return false;
    }

    private static boolean handleCurrentTileTransportTowardPath(List<WorldPoint> rawPath, List<WorldPoint> path, WorldPoint target) {
        if (Rs2Player.isMoving()) {
            return false;
        }

        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        if (playerLoc == null) {
            return false;
        }

        Set<Transport> transports = ShortestPathPlugin.getTransports().get(playerLoc);
        if (transports == null || transports.isEmpty()) {
            return false;
        }

        Set<WorldPoint> pathPoints = new HashSet<>();
        addForwardPathPoints(pathPoints, rawPath, playerLoc);
        addForwardPathPoints(pathPoints, path, playerLoc);

        List<Transport> candidates = transports.stream()
                .filter(t -> t.getDestination() != null)
                .filter(t -> target == null
                        || playerLoc.getPlane() != target.getPlane()
                        || t.getDestination().getPlane() == target.getPlane())
                .filter(t -> pathPoints.contains(t.getDestination())
                        || (target != null && t.getDestination().distanceTo(target) < playerLoc.distanceTo(target)))
                .sorted(Comparator
                        .comparingInt((Transport t) -> pathPoints.contains(t.getDestination()) ? 0 : 1)
                        .thenComparingInt(t -> target == null ? 0 : t.getDestination().distanceTo(target)))
                .collect(Collectors.toList());

        for (Transport transport : candidates) {
            if (handleTransports(Arrays.asList(playerLoc, transport.getDestination()), 0)) {
                log.info("[Walker] Current-tile transport handler resolved obstacle near {}", playerLoc);
                return true;
            }
        }

        return false;
    }

    private static void addForwardPathPoints(Set<WorldPoint> pathPoints, List<WorldPoint> path, WorldPoint playerLoc) {
        if (path == null || path.isEmpty() || playerLoc == null) {
            return;
        }

        int closestIndex = IntStream.range(0, path.size())
                .boxed()
                .min(Comparator.comparingInt(i -> playerLoc.distanceTo(path.get(i))))
                .orElse(0);
        for (int i = closestIndex; i < path.size(); i++) {
            pathPoints.add(path.get(i));
        }
    }

    // Session-local set of door tiles the walker detected as quest/stat-locked after a
    // failed interact. Cleared when the client restarts. Prevents infinite retry loops
    // through the same restricted door when the restriction isn't in restrictions.tsv.
    static final Set<WorldPoint> sessionBlacklistedDoors = ConcurrentHashMap.newKeySet();
    private static final Map<WorldPoint, Long> recentlyOpenedStationaryDoors = new ConcurrentHashMap<>();
    private static final long STATIONARY_DOOR_SUPPRESS_MS = 10_000;

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
            if (n.contains(f)) {
                return true;
            }
        }
        return false;
    }

    private static boolean handleDoors(List<WorldPoint> path, int index) {
        return handleDoors(path, index, false);
    }

    private static boolean handleDoors(List<WorldPoint> path, int index, boolean allowSegmentProbe) {
        if (ShortestPathPlugin.getPathfinder() == null || index >= path.size() - 1) return false;

        // Skip any door whose tile was blacklisted after a prior quest-lock detection —
        // avoid re-triggering the same failed interact loop this session.
        WorldPoint skipFrom = path.get(index);
        WorldPoint skipTo = index + 1 < path.size() ? path.get(index + 1) : null;
        if (sessionBlacklistedDoors.contains(skipFrom)
                || (skipTo != null && sessionBlacklistedDoors.contains(skipTo))) {
            return false;
        }

        List<String> doorActions = List.of("pay-toll", "pick-lock", "walk-through", "go-through", "open");
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

        if (hasExplicitTransportStep(path, index)) {
            return false;
        }

        if (recentlyOpenedStationaryDoorOnSegment(fromWp, toWp)) {
            return false;
        }

        boolean diagonal = Math.abs(fromWp.getX() - toWp.getX()) > 0
                && Math.abs(fromWp.getY() - toWp.getY()) > 0;

        for (int offset = 0; offset <= 1; offset++) {
            int doorIdx = index + offset;
            if (doorIdx >= path.size()) continue;

            WorldPoint rawDoorWp = path.get(doorIdx);
            WorldPoint doorWp = isInstance
                    ? Rs2WorldPoint.convertInstancedWorldPoint(rawDoorWp)
                    : rawDoorWp;

            List<WorldPoint> probes = new ArrayList<>();
            probes.add(doorWp);
            if (diagonal) {
                probes.add(new WorldPoint(toWp.getX(), fromWp.getY(), doorWp.getPlane()));
                probes.add(new WorldPoint(fromWp.getX(), toWp.getY(), doorWp.getPlane()));
            }

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
                WallObject wall = Rs2GameObject.getWallObject(o -> o.getWorldLocation().equals(probe), probe, 3);
                if (wall == null) {
                    wall = Rs2GameObject.getWallObject(o -> o.getWorldLocation().distanceTo2D(probe) <= 1, probe, 3);
                }

                TileObject object = (wall != null)
                        ? wall
                        : Rs2GameObject.getGameObject(o -> o.getWorldLocation().equals(probe), probe, 3);
                if (object == null) {
                    object = Rs2GameObject.getGameObject(o -> o.getWorldLocation().distanceTo2D(probe) <= 1, probe, 3);
                }
                if (object == null) continue;

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

                // Some doors/wall-objects have no actions in the definition but are still
                // interactable via default left-click (menu entry supplied at runtime).
                // Treat door-like names as valid even when actions[] is empty.
                if (action == null && !isDoorLikeGameObjectName(comp.getName())) {
                    Telemetry.recordDoorReject("no-action");
                    continue;
                }

                // If we can't find a known "door action" but the object is named like a door/gate,
                // still allow it (e.g. "Enter" on house portals/doors).
                if (action != null
                        && doorActionPriorityIndex(action) == Integer.MAX_VALUE
                        && !isDoorLikeGameObjectName(comp.getName())) {
                    Telemetry.recordDoorReject("no-action");
                    continue;
                }

                boolean found = false;

                final String name = comp.getName();

                if (object instanceof WallObject) {
                    WallObject wallObj = (WallObject) object;
                    int orientationA = wallObj.getOrientationA();
                    int orientationB = wallObj.getOrientationB();
                    boolean pathTouchesBothEnds = probe.distanceTo(fromWp) <= 1 && probe.distanceTo(toWp) <= 1
                            && fromWp.distanceTo(toWp) >= 1 && fromWp.distanceTo(toWp) <= 2;
                    boolean orientOk = false;
                    if (orientationA != 0) {
                        orientOk = searchNeighborPoint(orientationA, probe, fromWp)
                                || searchNeighborPoint(orientationA, probe, toWp);
                    }
                    if (!orientOk && orientationB != 0) {
                        orientOk = searchNeighborPoint(orientationB, probe, fromWp)
                                || searchNeighborPoint(orientationB, probe, toWp);
                    }
                    if (!orientOk && pathTouchesBothEnds) {
                        orientOk = true;
                    }
                    if (orientOk) {
                        log.info("Found WallObject door - name {} with action {} at {} - from {} to {}", name, action, probe, fromWp, toWp);
                        found = true;
                    } else {
                        Telemetry.recordDoorReject("orient-mismatch");
                    }
                } else {
                    if (isDoorLikeGameObjectName(name)) {
                        log.info("Found GameObject door - name {} with action {} at {} - from {} to {}", name, action, probe, fromWp, toWp);
                        found = true;
                    } else {
                        Telemetry.recordDoorReject("name-not-door");
                    }
                }

                if (found) {
                    String actionFinal = action == null ? "" : action;
                    if (!handleDoorException(object, actionFinal)) {
                        WorldPoint posBefore = Rs2Player.getWorldLocation();
                        if (actionFinal.isEmpty()) {
                            Rs2GameObject.interact(object);
                        } else {
                            Rs2GameObject.interact(object, actionFinal);
                        }
                        Rs2Player.waitForWalking();
                        WorldPoint posAfter = Rs2Player.getWorldLocation();
                        boolean moved = posBefore != null && posAfter != null && !posBefore.equals(posAfter);
                        if (!moved && isQuestLockedDoorDialogue()) {
                            String dialogue = Rs2Dialogue.getDialogueText();
                            log.warn("[Walker] Door at {} ({} action={}) appears quest/stat-locked — dialogue=\"{}\" — blacklisting tile, refreshing restrictions, recalculating",
                                    probe, name, actionFinal, dialogue);
                            sessionBlacklistedDoors.add(probe);
                            Rs2Dialogue.clickContinue();
                            if (ShortestPathPlugin.pathfinderConfig != null) {
                                ShortestPathPlugin.pathfinderConfig.refresh();
                            }
                            recalculatePath();
                        }
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
        return Rs2GameObject.getAll(o -> {
                    if (o == null || o.getWorldLocation() == null) return false;
                    WorldPoint loc = o.getWorldLocation();
                    if (loc.getPlane() != playerLoc.getPlane()) return false;
                    if (loc.distanceTo2D(playerLoc) > searchDistance) return false;
                    if (sessionBlacklistedDoors.contains(loc)) return false;
                    if (!(o instanceof WallObject) && !(o instanceof GameObject)) return false;
                    if (!isDoorOnSegment(o, fromWp, toWp)) return false;
                    ObjectComposition comp = Rs2GameObject.convertToObjectComposition(o);
                    if (!isDoorComposition(comp, doorActions)) return false;
                    return true;
                }, playerLoc, searchDistance).stream()
                .min(Comparator.comparingInt(o -> o.getWorldLocation().distanceTo2D(playerLoc)))
                .orElse(null);
    }

    private static boolean tryHandleDoorObject(TileObject object, WorldPoint probe, WorldPoint fromWp, WorldPoint toWp,
                                               List<String> doorActions, boolean allowSegmentProbe) {
        if (object == null || probe == null) return false;

        ObjectComposition comp = Rs2GameObject.convertToObjectComposition(object);
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
            log.info("Found GameObject door - name {} with action {} at {} - from {} to {}", name, action, probe, fromWp, toWp);
            found = true;
        }

        if (!found) return false;

        if (handleDoorException(object, action)) {
            return true;
        }

        WorldPoint posBefore = Rs2Player.getWorldLocation();
        Rs2GameObject.interact(object, action);
        waitForDoorInteractionProgress(fromWp, toWp);
        WorldPoint posAfter = Rs2Player.getWorldLocation();
        boolean moved = posBefore != null && posAfter != null && !posBefore.equals(posAfter);
        if (moved) {
            return true;
        }
        if (isQuestLockedDoorDialogue()) {
            String dialogue = Rs2Dialogue.getDialogueText();
            log.warn("[Walker] Door at {} ({} action={}) appears quest/stat-locked — dialogue=\"{}\" — blacklisting tile, refreshing restrictions, recalculating",
                    probe, name, action, dialogue);
            sessionBlacklistedDoors.add(probe);
            Rs2Dialogue.clickContinue();
            if (ShortestPathPlugin.pathfinderConfig != null) {
                ShortestPathPlugin.pathfinderConfig.refresh();
            }
            recalculatePath();
            return true;
        }

        if (doorStillHasAction(probe, doorActions, action)) {
            return true;
        }

        markStationaryDoorOpened(probe);
        return false;
    }

    private static boolean doorStillHasAction(WorldPoint probe, List<String> doorActions, String action) {
        if (probe == null || action == null) {
            return false;
        }

        WallObject wall = Rs2GameObject.getWallObject(o -> o.getWorldLocation().equals(probe), probe, 3);
        TileObject object = wall != null
                ? wall
                : Rs2GameObject.getGameObject(o -> o.getWorldLocation().equals(probe), probe, 3);
        if (object == null) {
            return false;
        }
        ObjectComposition composition = Rs2GameObject.convertToObjectComposition(object);
        String currentAction = getDoorAction(composition, doorActions);
        return currentAction != null && currentAction.equalsIgnoreCase(action);
    }

    private static void markStationaryDoorOpened(WorldPoint doorTile) {
        if (doorTile != null) {
            recentlyOpenedStationaryDoors.put(doorTile, System.currentTimeMillis());
        }
    }

    private static boolean recentlyOpenedStationaryDoorOnSegment(WorldPoint fromWp, WorldPoint toWp) {
        if (fromWp == null || toWp == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        recentlyOpenedStationaryDoors.entrySet().removeIf(entry -> now - entry.getValue() > STATIONARY_DOOR_SUPPRESS_MS);
        return recentlyOpenedStationaryDoors.keySet().stream()
                .anyMatch(door -> door != null
                        && door.getPlane() == fromWp.getPlane()
                        && (door.distanceTo2D(fromWp) <= 1 || door.distanceTo2D(toWp) <= 1));
    }

    private static boolean hasExplicitTransportStep(List<WorldPoint> path, int index) {
        if (path == null || index < 0 || index >= path.size() - 1) {
            return false;
        }
        Set<Transport> transports = ShortestPathPlugin.getTransports().get(path.get(index));
        if (transports == null || transports.isEmpty()) {
            return false;
        }
        WorldPoint destination = path.get(index + 1);
        return transports.stream().anyMatch(t -> Objects.equals(t.getDestination(), destination));
    }

    private static void waitForDoorInteractionProgress(WorldPoint fromWp, WorldPoint toWp) {
        final long startedAt = System.currentTimeMillis();
        Rs2Player.waitForWalking();
        sleepUntil(() -> {
            WorldPoint now = Rs2Player.getWorldLocation();
            if (now == null) {
                return false;
            }
            if (toWp != null && now.distanceTo2D(toWp) <= 1) {
                return true;
            }
            return !Rs2Player.isMoving() && System.currentTimeMillis() - startedAt > 1_200;
        }, 5000);
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

    private static boolean isDoorOnSegment(TileObject object, WorldPoint fromWp, WorldPoint toWp) {
        if (object == null || object.getWorldLocation() == null) return false;
        if (object instanceof WallObject) {
            return wallDoorTouchesSegment((WallObject) object, fromWp, toWp);
        }
        return isPointNearSegment(object.getWorldLocation(), fromWp, toWp, 1);
    }

    static boolean wallDoorTouchesSegment(WallObject wall, WorldPoint fromWp, WorldPoint toWp) {
        if (wall == null || wall.getWorldLocation() == null || fromWp == null || toWp == null) return false;
        if (wall.getWorldLocation().getPlane() != fromWp.getPlane() || fromWp.getPlane() != toWp.getPlane()) return false;

        WorldPoint doorTile = wall.getWorldLocation();
        WorldPoint blockedNeighbor = getWallDoorNeighborPoint(wall.getOrientationA(), doorTile);
        if (blockedNeighbor == null) return false;

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
            if (isDoorEdgeTransition(previous, next, doorTile, blockedNeighbor)) {
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
		long start = System.currentTimeMillis();
		boolean handled = handleDoors(path, index);
		if (!handled) {
			return false;
		}
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

		TileObject best = null;
		String bestAction = null;
		int bestScore = Integer.MAX_VALUE;

		for (int edgeIdx = startIdx; edgeIdx <= endEdgeIdx; edgeIdx++) {
			WorldPoint from = path.get(edgeIdx);
			WorldPoint to = path.get(edgeIdx + 1);
			if (from == null || to == null) continue;

			// Only edges "near enough" to matter.
			int dFrom = from.distanceTo2D(playerLoc);
			int dTo = to.distanceTo2D(playerLoc);
			if (Math.min(dFrom, dTo) > radiusTiles) continue;

			// Only treat as blocker if the next tile is unreachable OR the edge has no LOS.
			boolean blocked = !Rs2Tile.isTileReachable(to) || !hasLineOfSightBetween(from, to);
			if (!blocked) continue;

			// Chebyshev: doors often sit 2 tiles from a path step (jamb vs step past doorway).
			final int pathEdgeDoorMaxDist = 2;

			// Scan candidates in loaded scene radius around player.
			for (WallObject w : Rs2GameObject.getWallObjects(o -> true, playerLoc, radiusTiles)) {
				if (w == null) continue;
				WorldPoint objWp = w.getWorldLocation();
				if (objWp == null) continue;
				if (!Rs2GameObject.hasLineOfSight(playerLoc, w)) continue;

				if (objWp.distanceTo2D(from) > pathEdgeDoorMaxDist && objWp.distanceTo2D(to) > pathEdgeDoorMaxDist) {
					continue;
				}

				ObjectComposition comp = resolveCompositionForDoorProbe(w);
				if (comp == null || isNullOrPlaceholderObjectName(comp.getName())) continue;
				if (doorCompositionSpecifiesOnlyCloseOrShut(comp)) continue;

				String action = pickWalkDoorAction(comp);
				boolean doorLike = isDoorLikeGameObjectName(comp.getName())
						|| (action != null && doorActionPriorityIndex(action) < Integer.MAX_VALUE);
				if (!doorLike) continue;

				String actionFinal = action == null ? "" : action;

				int edgeDist = Math.min(objWp.distanceTo2D(from), objWp.distanceTo2D(to));
				int pri = actionFinal.isEmpty() ? Integer.MAX_VALUE : doorActionPriorityIndex(actionFinal);
				int score = edgeDist * 100 + pri * 10 + objWp.distanceTo2D(playerLoc);

				if (best == null || score < bestScore) {
					best = w;
					bestAction = actionFinal;
					bestScore = score;
				}
			}

			for (GameObject g : Rs2GameObject.getGameObjects(o -> true, playerLoc, radiusTiles)) {
				if (g == null) continue;
				WorldPoint objWp = g.getWorldLocation();
				if (objWp == null) continue;
				if (!Rs2GameObject.hasLineOfSight(playerLoc, g)) continue;
				if (objWp.distanceTo2D(from) > pathEdgeDoorMaxDist && objWp.distanceTo2D(to) > pathEdgeDoorMaxDist) {
					continue;
				}

				ObjectComposition comp = resolveCompositionForDoorProbe(g);
				if (comp == null || isNullOrPlaceholderObjectName(comp.getName())) continue;
				if (doorCompositionSpecifiesOnlyCloseOrShut(comp)) continue;

				String action = pickWalkDoorAction(comp);
				boolean doorLike = isDoorLikeGameObjectName(comp.getName())
						|| (action != null && doorActionPriorityIndex(action) < Integer.MAX_VALUE);
				if (!doorLike) continue;

				String actionFinal = action == null ? "" : action;

				int edgeDist = Math.min(objWp.distanceTo2D(from), objWp.distanceTo2D(to));
				int pri = actionFinal.isEmpty() ? Integer.MAX_VALUE : doorActionPriorityIndex(actionFinal);
				int score = edgeDist * 100 + pri * 10 + objWp.distanceTo2D(playerLoc);

				if (best == null || score < bestScore) {
					best = g;
					bestAction = actionFinal;
					bestScore = score;
				}
			}
		}

		if (best == null) {
			log.info("[Walker] path-adj blocker-scan: no candidates (radius={} idx={}/{})", radiusTiles, startIdx, path.size());
			return false;
		}

		log.info("[Walker] path-adj blocker-scan: score={} action={} at {}", bestScore, (bestAction == null || bestAction.isEmpty()) ? "<default>" : bestAction, best.getWorldLocation());
		if (bestAction == null || bestAction.isEmpty()) {
			Rs2GameObject.interact(best);
		} else {
			Rs2GameObject.interact(best, bestAction);
		}
		Rs2Player.waitForWalking();
		return true;
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

				// Found a likely blocker on-path: hand off to existing door handler (which
				// includes quest-lock detection, blacklisting, and recalculation).
				if (handleDoorsWithTimeout(path, j, timeoutMs)) {
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

        var tiles = Rs2Tile.getReachableTilesFromTile(Rs2Player.getWorldLocation(), 20);

        /**
         * Exception to handle objects that handle long animations or walk
         * ignore colission if we did not find a valid tile to walk on
         * this is to ensure we stay on the path even if we are on a agility obstacle
         */
        if (tiles.keySet().isEmpty()) {
            tiles = Rs2Tile.getReachableTilesFromTileIgnoreCollision(Rs2Player.getWorldLocation(), 20);
        }
        final HashMap<WorldPoint, Integer> _tiles = tiles;

        WorldPoint startPoint = path.stream()
                .min(Comparator.comparingInt(a -> _tiles.getOrDefault(a, Integer.MAX_VALUE)))
                .orElse(null);

        /**
         * Check if the startPoint is null or no matching tile is found
         * If either condition is true, proceed to find the closest index in the path list.
         */
        if (startPoint == null || _tiles.getOrDefault(startPoint, Integer.MAX_VALUE) == Integer.MAX_VALUE) {
            Optional<Integer> closestIndexOptional = IntStream.range(0, path.size())
                    .boxed()
                    .min(Comparator.comparingInt(i -> Rs2Player.getWorldLocation().distanceTo(path.get(i))));
            if (closestIndexOptional.isPresent()) {
                return closestIndexOptional.get();
            }
        }

        return IntStream.range(0, path.size())
                .filter(i -> path.get(i).equals(startPoint))
                .findFirst()
                .orElse(-1);
    }

    /**
     * Force the walker to recalculate path
     */
    public static void recalculatePath() {
        WorldPoint _currentTarget = currentTarget;
        Rs2Walker.setTarget(null);
        Rs2Walker.setTarget(_currentTarget);
    }

    /**
     * @param target
     */
    public static void setTarget(WorldPoint target) {
        if (target != null && !Microbot.isLoggedIn()) {
            log.warn("Unable to set target: not logged in");
            return;
        }
        Player localPlayer = Microbot.getClient().getLocalPlayer();
        if (!ShortestPathPlugin.isStartPointSet() && localPlayer == null) {
            log.warn("Start point is not set and player is null");
            return;
        }

        currentTarget = target;

        if (target == null) {
            synchronized (ShortestPathPlugin.getPathfinderMutex()) {
                final Pathfinder pathfinder = ShortestPathPlugin.getPathfinder();
                if (pathfinder != null) {
                    pathfinder.cancel();
                }
                ShortestPathPlugin.setPathfinder(null);
            }

            Microbot.getWorldMapPointManager().remove(ShortestPathPlugin.getMarker());
            ShortestPathPlugin.setMarker(null);
            ShortestPathPlugin.setStartPointSet(false);
        } else {
            Microbot.getWorldMapPointManager().removeIf(x -> x == ShortestPathPlugin.getMarker());
            ShortestPathPlugin.setMarker(new WorldMapPoint(target, ShortestPathPlugin.MARKER_IMAGE));
            ShortestPathPlugin.getMarker().setName("Target");
            ShortestPathPlugin.getMarker().setTarget(ShortestPathPlugin.getMarker().getWorldPoint());
            ShortestPathPlugin.getMarker().setJumpOnClick(true);
            Microbot.getWorldMapPointManager().add(ShortestPathPlugin.getMarker());

            WorldPoint start;
            if (Microbot.getClient().getTopLevelWorldView().isInstance()) {
                LocalPoint localLoc = Rs2Player.getLocalLocation();
                start = localLoc != null
                        ? WorldPoint.fromLocalInstance(Microbot.getClient(), localLoc)
                        : null;
                if (start == null) {
                    log.warn("[Walker] setTarget: instance localPoint conversion returned null (localLoc={} target={}) — falling back to raw world location",
                            localLoc, target);
                    start = Rs2Player.getWorldLocation();
                }
            } else {
                start = Rs2Player.getWorldLocation();
            }
            // POH fix: when inside a POH instance, the raw instance-template tile doesn't match
            // any registered POH transport origin (PohPanel registers them keyed to the exit
            // portal tile). Remap the pathfinder start to the configured exit portal so the
            // pathfinder can consider all POH teleports as step 0.
            if (Microbot.getClient().getTopLevelWorldView().isInstance()) {
                WorldPoint exitPortal = net.runelite.client.plugins.microbot.shortestpath.PohPanel.getExitPortalTile();
                if (exitPortal != null) {
                    Microbot.log("[Walker] In POH instance — remapping pathfinder start " + start
                            + " -> exit portal " + exitPortal);
                    start = exitPortal;
                }
            }
            ShortestPathPlugin.setLastLocation(start);
            final Pathfinder pathfinder = ShortestPathPlugin.getPathfinder();
            if (ShortestPathPlugin.isStartPointSet() && pathfinder != null) {
                start = pathfinder.getStart();
            }
            if (Microbot.getClient().isClientThread()) {
                final WorldPoint _start = start;
                Microbot.getClientThread().runOnSeperateThread(() -> restartPathfinding(_start, target));
            } else {
                restartPathfinding(start, target);
            }
        }
    }

    private static void restoreTargetMarker(WorldPoint target) {
        if (target == null || ShortestPathPlugin.getMarker() != null) {
            return;
        }

        try {
            ShortestPathPlugin.setMarker(new WorldMapPoint(target, ShortestPathPlugin.MARKER_IMAGE));
            ShortestPathPlugin.getMarker().setName("Target");
            ShortestPathPlugin.getMarker().setTarget(ShortestPathPlugin.getMarker().getWorldPoint());
            ShortestPathPlugin.getMarker().setJumpOnClick(true);
            Microbot.getWorldMapPointManager().add(ShortestPathPlugin.getMarker());
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
        return restartPathfinding(start, Set.of(end));
    }

    public static boolean restartPathfinding(WorldPoint start, Set<WorldPoint> ends) {
        if (Microbot.getClient().isClientThread()) {
            return false;
        }

        Pathfinder pathfinder = ShortestPathPlugin.getPathfinder();
        if (pathfinder != null) {
            pathfinder.cancel();
            if (ShortestPathPlugin.getPathfinderFuture() != null) {
                ShortestPathPlugin.getPathfinderFuture().cancel(true);
            }
        }

        if (ShortestPathPlugin.getPathfindingExecutor() == null) {
            ThreadFactory shortestPathNaming = new ThreadFactoryBuilder().setNameFormat("shortest-path-%d").build();
            ShortestPathPlugin.setPathfindingExecutor(Executors.newSingleThreadExecutor(shortestPathNaming));
        }

        ShortestPathPlugin.getPathfinderConfig().refresh();
        if (Rs2Player.isInCave()) {
            pathfinder = new Pathfinder(ShortestPathPlugin.getPathfinderConfig(), start, ends);
            pathfinder.run();
            ShortestPathPlugin.getPathfinderConfig().setIgnoreTeleportAndItems(true);
            Pathfinder pathfinderWithoutTeleports = new Pathfinder(ShortestPathPlugin.getPathfinderConfig(), start, ends);
            pathfinderWithoutTeleports.run();
            var lastPath = pathfinderWithoutTeleports.getPath().get(pathfinderWithoutTeleports.getPath().size()-1);
            var pathWithoutTeleportsIsReachable = lastPath.distanceTo(ends.stream().findFirst().orElse(lastPath)) <= config.reachedDistance();
            if (pathWithoutTeleportsIsReachable && pathfinder.getPath().size() >= pathfinderWithoutTeleports.getPath().size()) {
                ShortestPathPlugin.setPathfinder(pathfinderWithoutTeleports);
            } else {
                ShortestPathPlugin.setPathfinder(pathfinder);
            }
            ShortestPathPlugin.getPathfinderConfig().setIgnoreTeleportAndItems(false);
        } else {
            ShortestPathPlugin.setPathfinder(new Pathfinder(ShortestPathPlugin.getPathfinderConfig(), start, ends));
            ShortestPathPlugin.setPathfinderFuture(ShortestPathPlugin.getPathfindingExecutor().submit(ShortestPathPlugin.getPathfinder()));
        }
        return true;
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
        Set<Transport> transports = ShortestPathPlugin.getTransports().get(path.get(indexOfStartPoint));
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

        for (Transport transport : transports) {
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
                if (!inPohInstance && transport.getOrigin() != null && Rs2Player.getWorldLocation().getPlane() != transport.getOrigin().getPlane()) {
                    continue;
                }

                // Hoist path-constant checks out of the inner loop: destination must exist in path
                if (!pathFirstIndex.containsKey(transport.getDestination())) {
                    log.debug("[Walker] skip {}: destination {} not in path", transport.getDisplayInfo(), transport.getDestination());
                    continue;
                }
                if (TransportType.isTeleport(transport.getType(), transport.getOrigin()) && Rs2Player.getWorldLocation().distanceTo(transport.getDestination()) < 3) {
                    log.debug("[Walker] skip {}: already near destination", transport.getDisplayInfo());
                    continue;
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
                    if (!inPohInstance && origin != null && origin.getPlane() != Rs2Player.getWorldLocation().getPlane()) {
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
                                sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(transport.getDestination()) < 10);
                                sleepTickJitter(6);
                            } else {
                                Rs2Walker.walkFastCanvas(path.get(i));
                                sleep(1200, 1600);
                            }
                        }

                        if (transport.getType() == TransportType.CHARTER_SHIP) {
                            if (attemptObserved(transport, () -> handleCharterShip(transport))) {
                                sleepUntil(() -> !Rs2Player.isAnimating());
                                sleepUntilTrue(() -> Rs2Player.getWorldLocation().distanceTo(transport.getDestination()) < 10);
                                sleepTickJitter(4); // wait 4 extra ticks before walking
                                break;
                            }
                        }
                    }

                    log.debug("[Walker] Handling {} transport: {} (i={}, path[i]={}, origin={})",
                            transport.getType(), transport.getDisplayInfo(), i, path.get(i), origin);
                    if (transport.getType() == TransportType.POH) {
                        boolean pohResult = attemptObserved(transport, () -> handlePohTransport(transport));
                        log.debug("[Walker] handlePohTransport({}) returned {}", transport.getDisplayInfo(), pohResult);
                        if (pohResult) {
                            sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(transport.getDestination()) < OFFSET, 10000);
                            break;
                        }
                    }

                    if (transport.getType() == TransportType.CANOE) {
                        if (attemptObserved(transport, () -> handleCanoe(transport))) {
                            sleepTickJitter(2);
                            break;
                        }
                    }

                    if (transport.getType() == TransportType.SPIRIT_TREE) {
                        if (attemptObserved(transport, () -> handleSpiritTree(transport))) {
                            sleepUntil(() -> !Rs2Player.isAnimating());
                            sleepUntilTrue(() -> Rs2Player.getWorldLocation().distanceTo(transport.getDestination()) < 10);
                            break;
                        }
                    }

                    if (transport.getType() == TransportType.QUETZAL) {
                        if (attemptObserved(transport, () -> handleQuetzal(transport))) {
                            sleepTickJitter(2);
                            break;
                        }
                    }

                    if (transport.getType() == TransportType.MAGIC_CARPET) {
                        if (attemptObserved(transport, () -> handleMagicCarpet(transport))) {
                            sleepTickJitter(2);
                            break;
                        }
                    }

                    if (transport.getType() == TransportType.WILDERNESS_OBELISK) {
                        if (attemptObserved(transport, () -> handleWildernessObelisk(transport))) {
                            sleepTickJitter(2);
                            break;
                        }
                    }

                    if (transport.getType() == TransportType.GNOME_GLIDER) {
                        if (attemptObserved(transport, () -> handleGlider(transport))) {
                            sleepUntil(() -> !Rs2Player.isAnimating());
                            sleepUntilTrue(() -> Rs2Player.getWorldLocation().distanceTo(transport.getDestination()) < 10);
                            sleepTickJitter(3);
                            break;
                        }
                    }

                    if (transport.getType() == TransportType.FAIRY_RING && !Rs2Player.getWorldLocation().equals(transport.getDestination())) {
                        if (attemptObserved(transport, () -> handleFairyRing(transport))) {
                            sleepUntilTrue(() -> Rs2Player.getWorldLocation().distanceTo(transport.getDestination()) < OFFSET);
                            break;
                        }
                    }

                    if (transport.getType() == TransportType.TELEPORTATION_MINIGAME) {
                        if (attemptObserved(transport, () -> handleMinigameTeleport(transport))) {
                            sleepUntilTrue(() -> Rs2Player.getWorldLocation().distanceTo(transport.getDestination()) < (OFFSET * 2));
                            break;
                        }
                    }

                    if (transport.getType() == TransportType.TELEPORTATION_ITEM) {
                        if (attemptObserved(transport, () -> handleTeleportItem(transport))) {
                            sleepUntil(() -> !Rs2Player.isAnimating());
                            sleepUntilTrue(() -> Rs2Player.getWorldLocation().distanceTo(transport.getDestination()) < OFFSET);
                            break;
                        }
                    }

                    if (transport.getType() == TransportType.TELEPORTATION_SPELL) {
                        if (attemptObserved(transport, () -> handleTeleportSpell(transport))) {
                            if (isLumbridgeHomeTeleport(transport)) {
                                sleepUntilTrue(() -> Rs2Player.getWorldLocation().distanceTo(transport.getDestination()) < OFFSET, 600, 35000);
                            } else {
                                sleepUntil(() -> !Rs2Player.isAnimating());
                                sleepUntilTrue(() -> Rs2Player.getWorldLocation().distanceTo(transport.getDestination()) < OFFSET);
                            }
                            Rs2Tab.switchTo(InterfaceTab.INVENTORY);
                            break;
                        }
                    }

                    if (transport.getType() == TransportType.SEASONAL_TRANSPORT) {
                        if (attemptObservedWithoutAttemptRecord(transport, () -> handleSeasonalTransport(transport))) {
                            sleepUntil(() -> !Rs2Player.isAnimating());
                            sleepUntilTrue(() -> Rs2Player.getWorldLocation().distanceTo(transport.getDestination()) < OFFSET);
                            break;
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

                    List<TileObject> objects = Rs2GameObject.getAll(o -> {
                        if (o.getId() == transportObjectId) return true;
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
                        int destinationTolerance = isAdjacentSamePlaneTransport(transport) ? 0 : OFFSET;
                        return sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(transport.getDestination()) <= destinationTolerance, 5000);
                    }
                }
            }
        }
        return false;
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
        if (transport.getDestination().getPlane() == Rs2Player.getWorldLocation().getPlane()) {
            if (transport.getType() == TransportType.AGILITY_SHORTCUT) {
                Rs2Player.waitForAnimation();
                sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(transport.getDestination()) <= 2, 10000);
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
                            sleepUntil(() -> Rs2Player.getWorldLocation().equals(transport.getDestination()), 3000);
                        }
                    }
                }
            }
            return true;
        } else {
            int z = Rs2Player.getWorldLocation().getPlane();
            boolean started = sleepUntil(() -> Rs2Player.getWorldLocation().getPlane() != z
                    || Rs2Player.isMoving()
                    || Rs2Player.isAnimating(), 1800);
            if (!started) {
                log.debug("[Walker] {} transport click on {} produced no movement/animation; retrying",
                        transport.getAction(), tileObject.getId());
                return false;
            }
            boolean planeChanged = Rs2Player.getWorldLocation().getPlane() != z
                    || sleepUntil(() -> Rs2Player.getWorldLocation().getPlane() != z, 5000);
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
                sleepUntilTrue(() -> Rs2Player.getWorldLocation().distanceTo(transport.getDestination()) < OFFSET);
                return true;
            }
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
            sleepUntil(() -> Rs2Player.getWorldLocation().equals(transport.getDestination()));
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
            sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo2D(transport.getDestination()) < OFFSET, 10000);
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
            sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo2D(transport.getDestination()) < OFFSET, 10000);
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
            return sleepUntilTrue(() -> Rs2Player.getWorldLocation().distanceTo2D(transport.getDestination()) < OFFSET, 100, 10000);
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
        if (Rs2Pvp.isInWilderness() && (Rs2Pvp.getWildernessLevelFrom(Rs2Player.getWorldLocation()) > (transport.getMaxWildernessLevel() + 1)))
            return false;
        boolean succesfullAction = false;
        for (Set<Integer> itemIds : transport.getItemIdRequirements()) {
            if (succesfullAction)
                break;
            for (Integer itemId : itemIds) {
                if (Rs2Walker.currentTarget == null) break;
                if (Rs2Player.getWorldLocation().distanceTo2D(transport.getDestination()) < config.reachedDistance())
                    break;
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
        final Pathfinder pathfinder = ShortestPathPlugin.getPathfinder();
        if (pathfinder == null) return false; // idk are we near if we don't have a path?
        final List<WorldPoint> path = pathfinder.getPath();
        if (path == null) return false;

        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        int index = IntStream.range(0, pathfinder.getPath().size())
                .filter(f -> path.get(f).distanceTo2D(playerLocation) < 3)
                .findFirst().orElse(-1);
        return index >= Math.max(path.size() - 10, 0);
    }

    /**
     * @param target
     * @return
     */
    public static boolean isNear(WorldPoint target) {
        return Rs2Player.getWorldLocation().equals(target);
    }

    public static boolean isNearPath() {
        final Pathfinder pathfinder = ShortestPathPlugin.getPathfinder();
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

    private static void checkIfStuck() {
        // Leagues area teleports sit on the same tile for a long animation before
        // the actual location update. Don't treat that as "stuck" progress.
        if (Rs2LeaguesTransport.isTeleportInProgress()) {
            return;
        }
        if (Rs2LeaguesTransport.isLeaguesAreaTeleportPending(LEAGUES_AREA_PENDING_STALL_MAX_AGE_MS)) {
            return;
        }

        WorldPoint now = Rs2Player.getWorldLocation();
        boolean anim = Rs2Player.isAnimating();
        if (now != null && now.equals(lastPosition)) {
            if (anim && !prevAnimatingForStuckCheck && isNearPath()) {
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
    private static final long STALL_BASE_MS = 10_000;
    private static final double STALL_COMBAT_MULTIPLIER = 2.0;
    private static final double STALL_ANIMATING_MULTIPLIER = 1.5;
    private static final double STALL_INTERACTING_MULTIPLIER = 1.5;

    private static boolean interactingActorNearWalkablePath() {
        Pathfinder pf = ShortestPathPlugin.getPathfinder();
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
        double multiplier = 1.0;
        if (Rs2Player.isInCombat()) multiplier = Math.max(multiplier, STALL_COMBAT_MULTIPLIER);
        if (Rs2Player.isAnimating()) multiplier = Math.max(multiplier, STALL_ANIMATING_MULTIPLIER);
        if (Rs2Player.isInteracting()) {
            double interactMult = interactingActorNearWalkablePath()
                    ? STALL_INTERACTING_MULTIPLIER
                    : 1.0;
            multiplier = Math.max(multiplier, interactMult);
        }
        return Math.round(STALL_BASE_MS * multiplier);
    }

    private static boolean isStuckTooLong() {
        // Leagues teleport lifecycle must own "arrival vs timeout" detection.
        // Walker stall-recalc during an in-flight teleport causes re-click loops.
        if (Rs2LeaguesTransport.isTeleportInProgress()) {
            return false;
        }
        if (Rs2LeaguesTransport.isLeaguesAreaTeleportPending(LEAGUES_AREA_PENDING_STALL_MAX_AGE_MS)) {
            return false;
        }

        return lastMovedTimeMs > 0 && System.currentTimeMillis() - lastMovedTimeMs > stallThresholdMs();
    }

    /**
     * @param start
     */
    public void setStart(WorldPoint start) {
        if (ShortestPathPlugin.getPathfinder() == null) {
            return;
        }
        ShortestPathPlugin.setStartPointSet(true);
        if (Microbot.getClient().isClientThread()) {
            Microbot.getClientThread().runOnSeperateThread(() -> restartPathfinding(start, ShortestPathPlugin.getPathfinder().getTargets()));
        } else {
            restartPathfinding(start, ShortestPathPlugin.getPathfinder().getTargets());
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
        Pathfinder pathfinder = new Pathfinder(ShortestPathPlugin.getPathfinderConfig(), startpoint, ends);
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
     * Tries Leagues Area UI then Map of Alacrity for the same {@link Transport} row.
     * Attempt recording is done inside each handler ({@link Rs2LeaguesTransport#tryHandleLeaguesAreaTransportResult},
     * {@link Rs2MapOfAlacrityTransport#tryUse}) — use {@link #attemptObservedWithoutAttemptRecord} at the call site.
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
                log.debug("[Walker] seasonal transport unmatched by Leagues Area + MoA (expect pathfinder-only matching rows); key={} sample={}",
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
                return sleepUntilTrue(() -> Rs2Player.getWorldLocation().distanceTo2D(transport.getDestination()) < (OFFSET * 2), 100, 5000);
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
     * Option text on the Quetzal map (icons under {@link InterfaceID.QuetzalMenu#ICONS}).
     * Prefers resolving from {@link Transport#getDestination()} so bank/custom tiles match (e.g. Auburnvale vs Hunter Guild row).
     */
    private static String resolveQuetzalWhistleMapOptionLabel(Transport transport) {
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

    /** True when any Quetzal menu layer is visible (CONTENTS alone can stay hidden while MAP/ICONS show). */
    private static boolean isQuetzalMapInterfaceVisible() {
        return Rs2Widget.isWidgetVisible(InterfaceID.QuetzalMenu.UNIVERSE)
                || Rs2Widget.isWidgetVisible(InterfaceID.QuetzalMenu.MAP)
                || Rs2Widget.isWidgetVisible(InterfaceID.QuetzalMenu.ICONS)
                || Rs2Widget.isWidgetVisible(InterfaceID.QuetzalMenu.CONTENTS);
    }

    private static boolean finishQuetzalWhistleTransport(Transport transport) {
        assert transport != null;
        WorldPoint dest = transport.getDestination();
        assert dest != null;
        String mapLabel = resolveQuetzalWhistleMapOptionLabel(transport);
        if (mapLabel == null || mapLabel.isEmpty()) {
            log.warn("Quetzal whistle: could not resolve map label (displayInfo={}, destination={})",
                    transport.getDisplayInfo(), dest);
            return false;
        }
        Rs2Player.waitForAnimation(4000);
        sleep(Rs2Random.between(400, 700));
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
        };
        for (int rootId : roots) {
            Widget root = Rs2Widget.getWidget(rootId);
            if (root == null || root.isHidden()) {
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

        boolean mapVisible = sleepUntilTrue(() -> isQuetzalMapInterfaceVisible(), 100, 15000);
        if (!mapVisible) {
            log.error("Quetzal map UI not visible within timeout (label={}, checked UNIVERSE/MAP/ICONS/CONTENTS)",
                    mapOptionLabel);
            return false;
        }

        AtomicReference<Widget> destRef = new AtomicReference<>();
        boolean iconReady = sleepUntilTrue(() -> {
            Widget w = findQuetzalMapDestinationWidget(mapOptionLabel);
            destRef.set(w);
            return w != null;
        }, 150, 8000);
        Widget actionWidget = destRef.get();
        if (!iconReady || actionWidget == null) {
            log.error("Could not find Quetzal map icon for: {} (waited for widget tree after map visible)", mapOptionLabel);
            return false;
        }

        Rs2Widget.clickWidget(actionWidget);
        log.info("Quetzal map: traveling to {} -> {}", mapOptionLabel, expectedDestination);
        return sleepUntilTrue(() -> Rs2Player.getWorldLocation().distanceTo2D(expectedDestination) < OFFSET, 100, 8000);
    }

    private static boolean handleQuetzal(Transport transport) {
        String displayInfo = transport.getDisplayInfo();
        if (displayInfo == null || displayInfo.isEmpty()) return false;

        Rs2NpcModel renu = Rs2Npc.getNpc(NpcID.QUETZAL_CHILD_GREEN);

        if (Rs2Tile.isTileReachable(transport.getOrigin()) && Rs2Npc.interact(renu, "travel")) {
            Rs2Player.waitForWalking();
            return clickQuetzalMapDestination(displayInfo, transport.getDestination());
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
            sleepUntil(() -> Rs2Widget.isWidgetVisible(885, 4));
            List<Widget> destinationWidgets = Arrays.stream(Rs2Widget.getWidget(885, 4).getDynamicChildren())
                    .filter(w -> w.getActions() != null)
                    .collect(Collectors.toList());

            if (destinationWidgets.isEmpty()) return false;

            String destinationText = transport.getDisplayInfo();

            Widget destinationWidget = Rs2Widget.findWidget(destinationText, destinationWidgets);
            if (destinationWidget == null) return false;

            boolean isWidgetVisible = Microbot.getClientThread().runOnClientThreadOptional(() -> !destinationWidget.isHidden()).orElse(false);

            NewMenuEntry destinationMenuEntry = new NewMenuEntry()
                    .option(destinationText)
                    .target("")
                    .identifier(1)
                    .type(MenuAction.CC_OP)
                    .param0(destinationWidget.getIndex())
                    .param1(destinationWidget.getId())
                    .forceLeftClick(false);

            Microbot.doInvoke(destinationMenuEntry, new Rectangle(1, 1));
            return true;
        }
        return false;
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
        return sleepUntilTrue(() -> Rs2Player.getWorldLocation().distanceTo2D(transport.getDestination()) < OFFSET, 100, 5000);
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
        if (ShortestPathPlugin.getPathfinderConfig().getAllTransports().isEmpty()) {
            ShortestPathPlugin.getPathfinderConfig().refresh();
        }

        Set<Integer> teleportItemIds = ShortestPathPlugin.getPathfinderConfig().getAllTransports().values()
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
        boolean originalUseBankItems =  ShortestPathPlugin.getPathfinderConfig().isUseBankItems();
        try {
            ShortestPathPlugin.getPathfinderConfig().setUseBankItems(useBankItems);
            // Configure pathfinder
            ShortestPathPlugin.getPathfinderConfig().refresh();
            // Run pathfinder
            Pathfinder pf = new Pathfinder(ShortestPathPlugin.getPathfinderConfig(), startPoint, targetSet);
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
            ShortestPathPlugin.getPathfinderConfig().setUseBankItems(originalUseBankItems);
            ShortestPathPlugin.getPathfinderConfig().refresh();
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
        if (destination == null) {
            return new ArrayList<>();
        }

        boolean originalUseBankItems = ShortestPathPlugin.getPathfinderConfig().isUseBankItems();
        try {
            // Store and configure pathfinder settings
            ShortestPathPlugin.getPathfinderConfig().setUseBankItems(useBankItems);
            ShortestPathPlugin.getPathfinderConfig().refresh();
            // Run pathfinder
            Pathfinder pf = new Pathfinder(ShortestPathPlugin.getPathfinderConfig(), Rs2Player.getWorldLocation(), destination);
            pf.run();

            List<WorldPoint> path = pf.getPath();
            if (path.isEmpty()) {
                log.debug("Unable to find path to destination: " + destination);
                return new ArrayList<>();
            }

            // Get transports along the path
            List<Transport> transports = getTransportsForPath(path, 0, prefTransportType, true);

            // Log found transports for debugging
            transports.forEach(t -> log.debug("Transport found: " + t));

            return transports;

        } finally {
            // Always restore original configuration
            ShortestPathPlugin.getPathfinderConfig().setUseBankItems(originalUseBankItems);
            ShortestPathPlugin.getPathfinderConfig().refresh();
        }
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
        if (transport == null) {
            return false;
        }

        if (transport.getType() == TransportType.FAIRY_RING) {
            return Rs2Inventory.hasItem(ItemID.DRAMEN_STAFF) ||
                    Rs2Equipment.isWearing(ItemID.DRAMEN_STAFF) ||
                    Rs2Inventory.hasItem(ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF) ||
                    Rs2Equipment.isWearing(ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF) ||  Microbot.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE)  == 1;
        } else if (transport.getType() == TransportType.TELEPORTATION_ITEM ||
                transport.getType() == TransportType.TELEPORTATION_SPELL || transport.getType() == TransportType.CANOE ||
                transport.getType() == TransportType.BOAT || transport.getType() == TransportType.CHARTER_SHIP ||
                transport.getType() == TransportType.SHIP || transport.getType() == TransportType.MINECART ||
                transport.getType() == TransportType.MAGIC_CARPET
        ) {
            if (transport.getType() == TransportType.TELEPORTATION_SPELL && transport.getDisplayInfo() != null) {
                // Extract spell name from displayInfo (handle potential format "spellname:option")
                String spellName = transport.getDisplayInfo().contains(":")
                        ? transport.getDisplayInfo().split(":")[0].trim()
                        : transport.getDisplayInfo().trim();
                // Find matching Rs2Spells enum by name (case-insensitive partial match)
                boolean hasMultipleDestination = transport.getDisplayInfo().contains(":");
                String displayInfo = hasMultipleDestination
                        ? transport.getDisplayInfo().split(":")[0].trim().toLowerCase()
                        : transport.getDisplayInfo();
                log.debug("Looking for spell rune requirements for: '{}' - display info {}", spellName, displayInfo);
                Rs2Spells rs2Spell = Rs2Magic.getRs2Spell(displayInfo);
                return Rs2Magic.hasRequiredRunes(rs2Spell);
            }
            if (isCurrencyBasedTransport(transport.getType()) &&
                    (transport.getItemIdRequirements() == null || transport.getItemIdRequirements().isEmpty()) &&
                    transport.getCurrencyName() != null && !transport.getCurrencyName().isEmpty() && transport.getCurrencyAmount() > 0) {
                int currencyItemId = getCurrencyItemId(transport.getCurrencyName());
                return Rs2Inventory.count(currencyItemId) >= transport.getCurrencyAmount();
            }
            if (transport.getItemIdRequirements() == null || transport.getItemIdRequirements().isEmpty()) {
                return true; // No requirements specified
            }

            return transport.getItemIdRequirements()
                    .stream()
                    .flatMap(Collection::stream)
                    .anyMatch(itemId -> Rs2Equipment.isWearing(itemId) || Rs2Inventory.hasItem(itemId));
        }

        return true; // For other transport types, assume available for now -> we need to think about later
    }

    /**
     * Filters a list of transports to return only those missing required items.
     * Similar to Rs2Slayer.getMissingItemTransports() but accessible in Rs2Walker.
     *
     * @param transports List of transports to check
     * @return List of transports that are missing required items
     */
    public static List<Transport> getMissingTransports(List<Transport> transports) {
        if (transports == null) {
            return new ArrayList<>();
        }

        return transports.stream()
                .filter(t -> !hasRequiredTransportItems(t))
                .collect(Collectors.toList());
    }

    /**
     * Extracts item IDs and their required quantities for the given transports that are missing and available in bank.
     * Enhanced version that uses Rs2Magic and Rs2Spells systems for actual rune quantities on teleportation spells.
     *
     * @param transports List of transports to check for missing items
     * @return Map where key=itemId and value=quantity needed (actual quantities for teleportation spells)
     */
    public static Map<Integer, Integer> getMissingTransportItemIdsWithQuantities(List<Transport> transports) {
        if (transports == null) {
            return new HashMap<>();
        }

        Map<Integer, Integer> itemQuantityMap = new HashMap<>();

        transports.stream()
                .forEach(transport -> {
                    // Special handling for teleportation spells - use actual rune requirements
                    if (transport.getType() == TransportType.TELEPORTATION_SPELL) {
                        Map<Integer, Integer> spellRuneRequirements = getSpellRuneRequirements(transport);
                        if (!spellRuneRequirements.isEmpty()) {
                            // Check if any of the required runes are available in bank
                            spellRuneRequirements.forEach((runeItemId, requiredQuantity) -> {
                                try {
                                    int bankQuantity = Rs2Bank.count(runeItemId);
                                    if (bankQuantity >= requiredQuantity) {
                                        int currentQuantity = itemQuantityMap.getOrDefault(runeItemId, 0);
                                        itemQuantityMap.put(runeItemId, currentQuantity + requiredQuantity);
                                        log.debug("Added teleportation spell rune requirement: {} (ID: {}) x{} (bank has: {})",
                                                runeItemId, runeItemId, requiredQuantity, bankQuantity);
                                    }
                                } catch (Exception e) {
                                    log.debug("Could not check bank for rune " + runeItemId + ": " + e.getMessage());
                                }
                            });
                        }
                        return; // Skip normal item requirement processing for spell transports
                    }

                    // Normal processing for non-spell transports
                    if (transport.getItemIdRequirements() != null) {
                        for (Set<Integer> alternativeItems : transport.getItemIdRequirements()) {
                            // For each alternative set, we need ANY one of these items
                            // Check if we have any of the alternatives in bank
                            boolean hasAnyAlternative = alternativeItems.stream()
                                    .anyMatch(itemId -> {
                                        try {
                                            if (isCurrencyBasedTransport(transport.getType()) && transport.getCurrencyAmount() > 0) {
                                                // For currency-based transports, check if we have enough currency
                                                return Rs2Bank.count(itemId) >= transport.getCurrencyAmount();
                                            } else {
                                                // For regular items, just check if we have the item
                                                return Rs2Bank.hasItem(itemId);
                                            }
                                        } catch (Exception e) {
                                            log.debug("Could not check bank for item " + itemId + ": " + e.getMessage());
                                            return false;
                                        }
                                    });

                            if (hasAnyAlternative) {
                                // Find the first available alternative in bank and add it to our map
                                alternativeItems.stream()
                                        .filter(itemId -> {
                                            try {
                                                if (isCurrencyBasedTransport(transport.getType()) && transport.getCurrencyAmount() > 0) {
                                                    // For currency-based transports, check if we have enough currency
                                                    return Rs2Bank.count(itemId) >= transport.getCurrencyAmount();
                                                } else {
                                                    // For regular items, just check if we have the item
                                                    return Rs2Bank.hasItem(itemId);
                                                }
                                            } catch (Exception e) {
                                                log.debug("Could not check bank for item " + itemId + ": " + e.getMessage());
                                                return false;
                                            }
                                        })
                                        .findFirst()
                                        .ifPresent(itemId -> {
                                            // Determine required quantity based on transport type
                                            int requiredQuantity;
                                            if (isCurrencyBasedTransport(transport.getType()) && transport.getCurrencyAmount() > 0) {
                                                // For currency-based transports, use the actual currency amount
                                                requiredQuantity = transport.getCurrencyAmount();
                                                log.debug("Currency-based transport {} requires {} x{}",
                                                        transport.getType(), transport.getCurrencyName(), requiredQuantity);
                                            } else {
                                                // For regular items (teleportation items, fairy rings, etc.), assume 1 is needed
                                                requiredQuantity = 1;
                                            }

                                            int currentQuantity = itemQuantityMap.getOrDefault(itemId, 0);
                                            itemQuantityMap.put(itemId, currentQuantity + requiredQuantity);
                                        });
                                break; // Only need one item from this alternative set
                            }
                        }
                    }
                });

        return itemQuantityMap;
    }

    /**
     * Gets the actual rune requirements for a teleportation spell transport.
     * Maps spell names to Rs2Spells enum and extracts rune quantities.
     *
     * @param transport The teleportation spell transport
     * @return Map of item IDs to required quantities for the spell's runes
     */
    private static Map<Integer, Integer> getSpellRuneRequirements(Transport transport) {
        Map<Integer, Integer> runeRequirements = new HashMap<>();
        if (transport.getType() != TransportType.TELEPORTATION_SPELL || transport.getDisplayInfo() == null) {
            return runeRequirements;
        }
        try {
            // Extract spell name from displayInfo (handle potential format "spellname:option")
            String spellName = transport.getDisplayInfo().contains(":")
                    ? transport.getDisplayInfo().split(":")[0].trim()
                    : transport.getDisplayInfo().trim();
            // Find matching Rs2Spells enum by name (case-insensitive partial match)
            boolean hasMultipleDestination = transport.getDisplayInfo().contains(":");
            String displayInfo = hasMultipleDestination
                    ? transport.getDisplayInfo().split(":")[0].trim().toLowerCase()
                    : transport.getDisplayInfo();
            log.debug("Looking for spell rune requirements for: '{}' - display info {}", spellName, displayInfo);
            Rs2Spells rs2Spell = Rs2Magic.getRs2Spell(displayInfo);
            if (rs2Spell == null) return runeRequirements;
            // Get rune requirements and check for elemental runes that might be provided by staves
            Map<Runes, Integer> requiredRunes = Rs2Magic.getRequiredRunes(rs2Spell,1,true);
            List<Runes> elementalRunes = rs2Spell.getElementalRunes();
            log.debug("Spell '{}' requires {} runes, including {} elemental runes",
                    spellName, requiredRunes.size(), elementalRunes.size());
            // Convert rune requirements to item IDs with quantities
            requiredRunes.forEach((rune, quantity) -> {
                int runeItemId = rune.getItemId();
                runeRequirements.put(runeItemId, quantity);
                log.debug("Spell '{}' requires {} x {} (ID: {})",
                        spellName, quantity, rune.name(), runeItemId);
            });

        } catch (Exception e) {
            log.warn("Error getting spell rune requirements for transport '{}': {}",
                    transport.getDisplayInfo(), e.getMessage());
        }

        return runeRequirements;
    }

    /**
     * Checks if a transport type is currency-based (requires coins or other currency).
     *
     * @param transportType The transport type to check
     * @return true if the transport type requires currency
     */
    private static boolean isCurrencyBasedTransport(TransportType transportType) {
        return transportType == TransportType.BOAT ||
                transportType == TransportType.CHARTER_SHIP ||
                transportType == TransportType.SHIP ||
                transportType == TransportType.MINECART ||
                transportType == TransportType.MAGIC_CARPET;
    }

    /**
     * Maps currency name to item ID from RuneLite API.
     *
     * @param currencyName The name of the currency (e.g., "Coins")
     * @return The item ID for the currency, or -1 if not found
     */
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
            // Add more currencies as needed
            default:
                log.warn("Unknown currency type: {}", currencyName);
                return -1;
        }
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
        return new ArrayList<>(getMissingTransportItemIdsWithQuantities(transports).keySet());
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
        long totalStartTime = System.nanoTime();
        StringBuilder performanceLog = new StringBuilder();
        performanceLog.append("\n\t=== compareRoutes Performance Analysis ===\n");
        if (target == null) {
            return new TransportRouteAnalysis(new ArrayList<>(), null, null,new ArrayList<>(),new ArrayList<>(), "Target location is null");
        }

        if (startPoint == null) {
            startPoint = Rs2Player.getWorldLocation();
        }

        if (startPoint == null) {
            return new TransportRouteAnalysis(new ArrayList<>(), null, null, new ArrayList<>(),new ArrayList<>(),"Cannot determine starting location");
        }

        try {
            // Get direct path distance with timing
            performanceLog.append("\tStart Point: ").append(startPoint).append(", Target: ").append(target).append("\n");
            long directPathStartTime = System.nanoTime();
            List<WorldPoint> directPath = getWalkPath(startPoint, target);
            long directPathEndTime = System.nanoTime();
            double directPathTimeMs = (directPathEndTime - directPathStartTime) / 1_000_000.0;

            int directDistance = getTotalTilesFromPath(directPath, target);
            performanceLog.append("\t-Direct path calculation: ").append(String.format("%.2f ms", directPathTimeMs))
                    .append(" (").append(directPath.size()).append(" waypoints, ").append(directDistance).append(" tiles)\n");

            // Find nearest bank and calculate banking route distance
            BankLocation nearestBank = null;
            List<WorldPoint> pathToBank  = new ArrayList<>();
            List<WorldPoint> pathWithBankedItemsToTarget = new ArrayList<>();
            int bankingRouteDistance = -1;

            try {




                boolean originalUseBankItems = ShortestPathPlugin.getPathfinderConfig().isUseBankItems();
                try {
                    ShortestPathPlugin.getPathfinderConfig().setUseBankItems(true);
                    ShortestPathPlugin.getPathfinderConfig().refresh(target);

                    performanceLog.append("\t-Bank items available: ").append(Rs2Bank.bankItems().size()).append("\n");

                    long pathWithBankedItemsStartTime = System.nanoTime();
                    pathWithBankedItemsToTarget = getWalkPath(startPoint, target);
                    long pathWithBankedItemsEndTime = System.nanoTime();
                    double pathWithBankedItemsTimeMs = (pathWithBankedItemsEndTime - pathWithBankedItemsStartTime) / 1_000_000.0;

                    int distanceWithBankedItemsToTarget = getTotalTilesFromPath(pathWithBankedItemsToTarget, target);
                    bankingRouteDistance = distanceWithBankedItemsToTarget;

                    performanceLog.append("\t-Path from start to target with banked items: ").append(String.format("%.2f ms", pathWithBankedItemsTimeMs))
                            .append(" (").append(pathWithBankedItemsToTarget.size()).append(" waypoints, ").append(distanceWithBankedItemsToTarget).append(" tiles)\n");
                    performanceLog.append("\t-Total banking route distance: ").append(bankingRouteDistance).append(" tiles\n");

                } finally {
                    // Always restore original configuration
                    ShortestPathPlugin.getPathfinderConfig().setUseBankItems(false);
                    ShortestPathPlugin.getPathfinderConfig().refresh();
                }
                if (bankingRouteDistance<directDistance){
                    long bankSearchStartTime = System.nanoTime();
                    nearestBank = Rs2Bank.getNearestBank(startPoint);
                    long bankSearchEndTime = System.nanoTime();
                    double bankSearchTimeMs = (bankSearchEndTime - bankSearchStartTime) / 1_000_000.0;
                    if (nearestBank != null) {
                        performanceLog.append("\t-Nearest bank search: ").append(String.format("%.2f ms", bankSearchTimeMs));
                        WorldPoint bankLocation = nearestBank.getWorldPoint();
                        performanceLog.append("\t -> Found: ").append(nearestBank).append(" at ").append(bankLocation).append("\n");

                        // Calculate distance from start point to bank
                        long pathToBankStartTime = System.nanoTime();
                        pathToBank = getWalkPath(startPoint, bankLocation);
                        long pathToBankEndTime = System.nanoTime();
                        double pathToBankTimeMs = (pathToBankEndTime - pathToBankStartTime) / 1_000_000.0;

                        int distanceToBank = getTotalTilesFromPath(pathToBank, bankLocation);
                        performanceLog.append("\t-Path to bank calculation: ").append(String.format("%.2f ms", pathToBankTimeMs))
                                .append(" (").append(pathToBank.size()).append(" waypoints, ").append(distanceToBank).append(" tiles)\n");
                        bankingRouteDistance += distanceToBank;
                    } else {
                        performanceLog.append("\t -> No accessible bank found\n");
                    }
                }

            } catch (Exception e) {
                performanceLog.append("Banking route calculation failed: ").append(e.getMessage()).append("\n");
                log.debug("Could not calculate banking route: " + e.getMessage());
            }

            long totalEndTime = System.nanoTime();
            double totalTimeMs = (totalEndTime - totalStartTime) / 1_000_000.0;
            performanceLog.append("\t=== Total compareRoutes time: ").append(String.format("%.2f ms", totalTimeMs)).append(" ===\n");

            if (bankingRouteDistance == -1) {
                performanceLog.append("\tResult: Direct route only (banking route unavailable)\n");
                log.info(performanceLog.toString());
                return new TransportRouteAnalysis(directPath, null, null, new ArrayList<>(),new ArrayList<>(),
                        "Direct route only (banking route unavailable)");
            }

            boolean directIsFaster = directDistance <= bankingRouteDistance;
            String recommendation = directIsFaster ?
                    String.format("\tDirect route is faster (%d vs %d tiles)", directDistance, bankingRouteDistance) :
                    String.format("\tBanking route is faster (%d vs %d tiles)", bankingRouteDistance, directDistance);

            performanceLog.append("\tResult:\n\t\t ").append(recommendation).append("\n");
            log.info(performanceLog.toString());

            return new TransportRouteAnalysis(directPath,
                    nearestBank, nearestBank != null ? nearestBank.getWorldPoint() : null,pathToBank,pathWithBankedItemsToTarget, recommendation);

        } catch (Exception e) {
            long totalEndTime = System.nanoTime();
            double totalTimeMs = (totalEndTime - totalStartTime) / 1_000_000.0;
            performanceLog.append("ERROR after ").append(String.format("%.2f ms", totalTimeMs)).append(": ").append(e.getMessage()).append("\n");
            log.warn(performanceLog.toString());
            log.warn("Error comparing routes to {}: {}", target, e.getMessage());
            return new TransportRouteAnalysis(new ArrayList<>(), null, null,new ArrayList<>(),new ArrayList<>(), "Error calculating routes: " + e.getMessage());
        }
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
        int d = config != null ? config.reachedDistance() : 10;
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
        if (Microbot.getClient().isClientThread()) {
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
        if (Rs2Tile.getReachableTilesFromTile(Rs2Player.getWorldLocation(), distance).containsKey(target)
                || !Rs2Tile.isWalkable(LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), target)) && Rs2Player.getWorldLocation().distanceTo(target) <= distance) {
            return WalkerState.ARRIVED;
        }
        final Pathfinder pathfinder = ShortestPathPlugin.getPathfinder();
        if (pathfinder != null && !pathfinder.isDone())
            return WalkerState.MOVING;
        // Check what transport items are needed
        TransportRouteAnalysis comparison = compareRoutes(target);
        List<Transport> missingTransports = getMissingTransports(getTransportsForDestination(target, true, TransportType.TELEPORTATION_SPELL));

        Map<Integer, Integer> missingItemsWithQuantities = getMissingTransportItemIdsWithQuantities(missingTransports);
        if(!missingTransports.isEmpty()){
            log.info("\n\tFor {} transports to destination in the bank to target {} we found {} missing items",
                    missingTransports.size(), target, missingItemsWithQuantities.size());
        }
        // If no missing transport items, go directly
        if (missingItemsWithQuantities.isEmpty() && !forceBanking) {
            log.info("\n\tNo missing transport items, traveling directly to: \n\t" + target);
            WalkerState state = walkWithStateInternal(target, distance);
            if (state == WalkerState.ARRIVED) {
                log.info("\n\tArrived directly at target: " + target);
            } else {
                log.warn("\n\tFailed to arrive directly at target: " + target + ", state: " + state);
                setTarget(null);
                return state;

            }
            return state;
        } else {
            // Compare routes if we have missing items that could be obtained from bank
            // Use config for minimum bank route savings
            int minBankRouteSavings = config != null ? config.minBankRouteSavings() : 0;
            int tileSavings = comparison.getTileSavings();
            boolean bankRouteIsBetter = !comparison.isDirectIsFaster() && tileSavings >= minBankRouteSavings;
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
            ShortestPathPlugin.getPathfinderConfig().setUseBankItems(false);
            ShortestPathPlugin.getPathfinderConfig().refresh(finalTarget);
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

