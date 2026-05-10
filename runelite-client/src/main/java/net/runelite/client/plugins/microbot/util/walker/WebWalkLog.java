package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Single tag {@code [WebWalk]} for walker + ShortestPath + pathfinder observability:
 * INFO/WARN for outcomes and decisions; DEBUG for per-run volume (pf/cfg/leagues/yields).
 */
public final class WebWalkLog {
    private static final Logger LOG = LoggerFactory.getLogger(WebWalkLog.class);

    private WebWalkLog() {
    }

    public static void routeClear(String reason) {
        LOG.info("[WebWalk] clear | {}", reason);
    }

    public static void routeClearMissingReason(String threadName) {
        LOG.warn("[WebWalk] clear | reason=<missing> thread={}", threadName);
    }

    /** Cancel / EXIT / traceProcessWalkExit — compact WARN for production diagnosis. */
    public static void exitWarn(String reason, boolean nullCurrent, boolean targetMismatch, boolean interrupted,
            WorldPoint goal, WorldPoint current, int tailIdx, int tailMax, WorldPoint player) {
        LOG.warn("[WebWalk] exit | r={} nullCur={} mismatch={} intr={} goal={} cur={} tail={}/{} at={}",
                reason, nullCurrent, targetMismatch, interrupted, goal, current, tailIdx, tailMax, player);
    }

    public static void exitDetailDebug(String fmt, Object... args) {
        LOG.debug("[WebWalk] exit_dbg | " + fmt, args);
    }

    /** interim-in-flight and similar yields — DEBUG to avoid tick spam. */
    public static void yieldDebug(String reason, WorldPoint player, WorldPoint goal, WorldPoint pathEnd, int idxStart, int pathLen) {
        LOG.debug("[WebWalk] yield | r={} player={} goal={} end={} idx={}/{}",
                reason, player, goal, pathEnd, idxStart, pathLen);
    }

    public static void earlyExit(String reason, WorldPoint player, WorldPoint goal, WorldPoint pathEnd, int idxStart, int pathLen) {
        LOG.info("[WebWalk] early_exit | r={} at={} goal={} end={} idx={}/{}",
                reason, player, goal, pathEnd, idxStart, pathLen);
    }

    /** Path ends far from goal — walking multi-hop segment. */
    public static void partialSegment(WorldPoint pathEnd, int distToGoal, WorldPoint goal, int waypointCount) {
        LOG.info("[WebWalk] partial_seg | end={} dGoal={} goal={} nWp={}", pathEnd, distToGoal, goal, waypointCount);
    }

    public static void partialRetry(int finalDist, int attempt, int maxAttempts) {
        LOG.info("[WebWalk] partial_retry | dist={} attempt={}/{}", finalDist, attempt, maxAttempts);
    }

    public static void partialExhausted(int finalDist) {
        LOG.info("[WebWalk] partial_exhausted | finalDist={}", finalDist);
    }

    public static void interruptedExit(String detail) {
        LOG.info("[WebWalk] interrupt | {}", detail);
    }

    public static void stallContextDebug(WorldPoint lastClick, boolean clickOk, long clickAgeMs, WorldPoint interim) {
        LOG.debug("[WebWalk] stall_ctx | lastClick={} ok={} ageMs={} interim={}", lastClick, clickOk, clickAgeMs, interim);
    }

    /** Off-path / unreachable recovery / generic replan — single INFO line. */
    public static void recalc(String reason) {
        LOG.info("[WebWalk] recalc | {}", reason);
    }

    public static void partialRecalc(int remainingSteps, int distToSeg, int distToGoal, WorldPoint segEnd, WorldPoint goal) {
        LOG.info("[WebWalk] partial_recalc | remSteps={} dSeg={} dGoal={} segEnd={} goal={}",
                remainingSteps, distToSeg, distToGoal, segEnd, goal);
    }

    public static void stallRecalc(long sinceMovedMs, long thresholdMs, boolean combat, boolean anim, boolean interact) {
        LOG.info("[WebWalk] stall_recalc | sinceMs={} thrMs={} combat={} anim={} interact={}",
                sinceMovedMs, thresholdMs, combat, anim, interact);
    }

    public static void tailExceeded(int maxTail, WorldPoint target, WorldPoint current, WorldPoint interim, int stuck,
            WorldPoint player) {
        LOG.warn("[WebWalk] tail_max | max={} goal={} cur={} interim={} stuck={} at={}",
                maxTail, target, current, interim, stuck, player);
    }

    /** Pathfinder {@link net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder} — DEBUG volume. */
    public static void pf(String fmt, Object... args) {
        LOG.debug("[WebWalk] pf | " + fmt, args);
    }

    /** {@link net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig} refresh — DEBUG volume. */
    public static void cfg(String fmt, Object... args) {
        LOG.debug("[WebWalk] cfg | " + fmt, args);
    }

    public static void leagues(String fmt, Object... args) {
        LOG.debug("[WebWalk] leagues | " + fmt, args);
    }

    /** Leagues calibration, region unlock, explicit no-op — INFO (not tick-spam paths). */
    public static void leaguesInfo(String fmt, Object... args) {
        LOG.info("[WebWalk] leagues | " + fmt, args);
    }

    public static void spWarn(String fmt, Object... args) {
        LOG.warn("[WebWalk] sp | " + fmt, args);
    }

    public static void spInfo(String fmt, Object... args) {
        LOG.info("[WebWalk] sp | " + fmt, args);
    }

    public static void spDebug(String fmt, Object... args) {
        LOG.debug("[WebWalk] sp | " + fmt, args);
    }

    /** One INFO line; full blob only at DEBUG. */
    public static void compareSummary(double totalMs, int directTiles, int bankTiles, String verdictOneLine) {
        LOG.info("[WebWalk] compare | {}ms direct={}t bank={}t | {}",
                String.format("%.1f", totalMs), directTiles, bankTiles, verdictOneLine);
    }

    public static void compareDetail(String multiline) {
        LOG.debug("[WebWalk] compare_detail\n{}", multiline);
    }

    public static void compareError(double totalMs, WorldPoint target, String err) {
        LOG.warn("[WebWalk] compare_err | {}ms target={} err={}", String.format("%.1f", totalMs), target, err);
    }

    /** Banked-route helper: per-path transport scan — DEBUG volume. */
    public static void bankPathTransportsDebug(int count, WorldPoint from, WorldPoint to) {
        LOG.debug("[WebWalk] bank_path | transports={} {} -> {}", count, from, to);
    }

    public static void bankWalkDebug(String fmt, Object... args) {
        LOG.debug("[WebWalk] bank_walk | " + fmt, args);
    }

    public static void bankWalkFailed(WorldPoint goal, Object walkerState) {
        LOG.warn("[WebWalk] bank_walk | failed goal={} state={}", goal, walkerState);
    }
}
