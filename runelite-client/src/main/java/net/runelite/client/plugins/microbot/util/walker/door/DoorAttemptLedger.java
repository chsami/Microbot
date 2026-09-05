package net.runelite.client.plugins.microbot.util.walker.door;

import net.runelite.api.coords.WorldPoint;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The single owner of "which door edges has this walker ATTEMPTED, and when" — D3 slice 1 of the
 * door-attempt lifecycle (DETECTED → ATTEMPTED → CROSSED | REFUSED | EXPIRED).
 *
 * <p>Before the ledger, this one fact lived in two independent stores with different lifetimes:
 * a per-edge timestamp map ({@code recentDoorAttemptByEdge}, session-lived, decayed on read) feeding
 * the anti-hammer cooldown, and a most-recent-attempt triple ({@code routeState.lastDoorAttempt*},
 * walk-lived) feeding the post-attempt nudge, the active-edge claim and the same-edge cooldown
 * variant. Their disagreement was a live bug: the Stronghold of Security's chained gates (2026-08-12)
 * had the triple still pointing at a conquered gate while the map knew about the next one, and the
 * nudge victory-lapped the door already crossed. One owner makes that class of disagreement
 * unrepresentable.
 *
 * <p>The two lifetimes are preserved as two facets of one record set, not two stores:
 * <ul>
 *   <li><b>per-edge attempt times</b> survive walk boundaries and decay by cooldown — hammering the
 *       same door across two walks is still hammering;</li>
 *   <li><b>the latest attempt</b> (the walker's current claim on an edge) is dropped at walk start
 *       and the moment a crossing is observed — a claim on the previous walk's door, or on a door
 *       already behind us, satisfies nothing.</li>
 * </ul>
 *
 * <p>All time is injected ({@code nowMs}) so decision tables can drive the clock. The class is
 * instance-based for the same reason; the walker holds one static instance.
 */
public final class DoorAttemptLedger {

    /** One attempted door edge — an immutable snapshot of the walker's claim on it. */
    public static final class Attempt {
        public final WorldPoint from;
        public final WorldPoint to;
        public final long attemptedAtMs;

        Attempt(WorldPoint from, WorldPoint to, long attemptedAtMs) {
            this.from = from;
            this.to = to;
            this.attemptedAtMs = attemptedAtMs;
        }

        /** Direction-blind edge identity — the active-edge claim covers both crossing directions. */
        public boolean matchesEdge(WorldPoint a, WorldPoint b) {
            if (a == null || b == null) {
                return false;
            }
            return (a.equals(from) && b.equals(to)) || (a.equals(to) && b.equals(from));
        }

        /** Direction-AWARE identity — the same-edge cooldown deliberately binds one direction only. */
        public boolean isSameDirectedEdge(WorldPoint fromWp, WorldPoint toWp) {
            return from.equals(fromWp) && to.equals(toWp);
        }
    }

    /** Outcome of registering one concluded-but-uncrossed attempt against an edge. */
    public enum Strike {
        /** The sample cannot prove a refusal (player still moving, or the walk was cancelled mid-wait). */
        NOT_COUNTED,
        /** Counted; the edge has strikes left. */
        COUNTED,
        /** The edge has struck out: block it for this walk and replan. */
        STRIKE_OUT
    }

    private final Map<String, Long> attemptAtByEdgeKey = new ConcurrentHashMap<>();
    private final Map<String, long[]> crossFailuresByEdgeKey = new ConcurrentHashMap<>();
    private final Map<WorldPoint, Long> stationaryDoorOpenedAtByTile = new ConcurrentHashMap<>();
    private final Set<WorldPoint> blacklistedDoorTiles = ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.ConcurrentLinkedQueue<WorldPoint[]> walkScopedBlocks =
            new java.util.concurrent.ConcurrentLinkedQueue<>();
    private volatile Attempt latest;

    /**
     * Records an attempt. Edge-keyed attempts (both endpoints known) also become the latest claim;
     * tile-keyed attempts (probe-only door, no resolved edge) feed the cooldown map alone, exactly
     * as the pre-ledger stores behaved.
     */
    public void markAttempt(WorldPoint doorTile, WorldPoint fromWp, WorldPoint toWp, long nowMs) {
        attemptAtByEdgeKey.put(Rs2DoorHandler.doorAttemptKey(doorTile, fromWp, toWp), nowMs);
        if (fromWp != null && toWp != null) {
            latest = new Attempt(fromWp, toWp, nowMs);
        }
    }

    /**
     * Transfers an exact detected route edge to the door transaction without starting the
     * anti-hammer cooldown. Detection may happen while the player is still approaching; delaying
     * the first real interaction because the approach itself counted as a click would add a full
     * cooldown at every door.
     */
    public void claimDetectedEdge(WorldPoint fromWp, WorldPoint toWp, long nowMs) {
        if (fromWp != null && toWp != null) {
            latest = new Attempt(fromWp, toWp, nowMs);
        }
    }

    /**
     * The anti-hammer gate: true while the edge's last attempt is younger than the cooldown.
     * Purges every expired entry as a side effect, as the map-based version always did.
     */
    public boolean shouldThrottleAttempt(WorldPoint doorTile, WorldPoint fromWp, WorldPoint toWp,
                                         long cooldownMs, long nowMs) {
        attemptAtByEdgeKey.entrySet().removeIf(entry -> nowMs - entry.getValue() > cooldownMs);
        Long last = attemptAtByEdgeKey.get(Rs2DoorHandler.doorAttemptKey(doorTile, fromWp, toWp));
        return last != null && nowMs - last < cooldownMs;
    }

    /** Raw attempt time for an edge, or null — the age query behind the nearby-wait heuristics. */
    public Long attemptAtMs(WorldPoint fromWp, WorldPoint toWp) {
        return attemptAtByEdgeKey.get(Rs2DoorHandler.doorAttemptKey(null, fromWp, toWp));
    }

    /** The current claim regardless of age (the same-edge cooldown never age-filtered). */
    public Attempt latestAttempt() {
        return latest;
    }

    /** The current claim if it is younger than {@code maxAgeMs}; null once it has gone stale. */
    public Attempt latestAttempt(long maxAgeMs, long nowMs) {
        Attempt attempt = latest;
        if (attempt == null) {
            return null;
        }
        long ageMs = nowMs - attempt.attemptedAtMs;
        return (ageMs < 0L || ageMs > maxAgeMs) ? null : attempt;
    }

    /**
     * Withdraws the latest claim — at walk start (the claim belongs to the previous walk) and when a
     * crossing is observed (done means fall through; a spent claim must not nudge again). Per-edge
     * attempt times deliberately survive: the cooldown is anti-hammer, not a claim.
     */
    public void clearLatestAttempt() {
        latest = null;
    }

    // ---- the REFUSED facet (D3 slice 2): strike counting and walk-scoped blocks ----

    /**
     * Counts attempts that CONCLUDED at the door without crossing it — a click that opened nothing
     * (action still present), or a cross-click past an apparently open door that moved the player
     * nowhere. Doors that refuse for game-state reasons (Tithe Farm's seed gate, key doors, favour
     * gates) produce exactly this signature and nothing else: no dialogue, no traversal, no collision
     * change. Without a strike-out the walker retries the same edge forever — measured at 4+ minutes
     * of door/recovery ping-pong on Farm door 27445 before a human cancelled it.
     *
     * <p>{@code conclusiveSample} is the caller's evidence gate: the player must be stationary at the
     * near side when sampled. A moving sample proves only that the approach was still in flight —
     * the same trap that once blacklisted Wydin's door off a mid-walk position. Strikes are keyed by
     * the normalized (direction-blind) edge, decay after {@code decayMs}, and a strike-out consumes
     * the entry so a re-attempted edge starts fresh.
     */
    public Strike registerCrossFailure(WorldPoint fromWp, WorldPoint toWp, boolean conclusiveSample,
                                       long nowMs, long decayMs, int strikeLimit) {
        if (!conclusiveSample || fromWp == null || toWp == null) {
            return Strike.NOT_COUNTED;
        }
        String edgeKey = Rs2DoorHandler.doorAttemptKey(null, fromWp, toWp);
        crossFailuresByEdgeKey.entrySet().removeIf(entry -> nowMs - entry.getValue()[1] > decayMs);
        long[] entry = crossFailuresByEdgeKey.compute(edgeKey, (k, v) ->
                v == null ? new long[]{1, nowMs} : new long[]{v[0] + 1, nowMs});
        if (entry[0] >= strikeLimit) {
            crossFailuresByEdgeKey.remove(edgeKey);
            return Strike.STRIKE_OUT;
        }
        return Strike.COUNTED;
    }

    /** A successful crossing forgives the edge's strikes (transient refusals should not accumulate). */
    public void clearCrossFailures(WorldPoint fromWp, WorldPoint toWp) {
        if (fromWp != null && toWp != null) {
            crossFailuresByEdgeKey.remove(Rs2DoorHandler.doorAttemptKey(null, fromWp, toWp));
        }
    }

    /**
     * Remembers a planner edge-block earned by a strike-out so the NEXT walk can withdraw it. The
     * block is walk-scoped, not session-scoped: a door that refuses for game-state reasons opens the
     * moment the condition is met, and a session block would stop the owning plugin's own walk-in
     * from ever routing through it — the museum lesson.
     */
    public void recordWalkScopedBlock(WorldPoint fromWp, WorldPoint toWp) {
        walkScopedBlocks.add(new WorldPoint[]{fromWp, toWp});
    }

    /** Returns and forgets every walk-scoped block — called once at walk session start. */
    public java.util.List<WorldPoint[]> drainWalkScopedBlocks() {
        java.util.List<WorldPoint[]> drained = new java.util.ArrayList<>();
        WorldPoint[] edge;
        while ((edge = walkScopedBlocks.poll()) != null) {
            drained.add(edge);
        }
        return drained;
    }

    // ---- tile-keyed facets (D3 slice 3): recently-opened suppression and the session blacklist ----

    /**
     * Records that a stationary (non-moves-you) door at this tile was just opened. For the suppress
     * window that follows, probes must not re-find it — re-clicking an open door closes it, which
     * was the original two-clicks-per-door bug.
     */
    public void markStationaryDoorOpened(WorldPoint doorTile, long nowMs) {
        if (doorTile != null) {
            stationaryDoorOpenedAtByTile.put(doorTile, nowMs);
        }
    }

    /**
     * Whether a recently-opened stationary door sits on (within 2 tiles of either end of) the
     * {@code fromWp -> toWp} segment. Purges expired entries as a side effect, as the map-based
     * version always did.
     */
    public boolean recentlyOpenedDoorOnSegment(WorldPoint fromWp, WorldPoint toWp, long suppressMs, long nowMs) {
        if (fromWp == null || toWp == null) {
            return false;
        }
        final int segmentDoorSuppressDist = 2;
        stationaryDoorOpenedAtByTile.entrySet().removeIf(entry -> nowMs - entry.getValue() > suppressMs);
        return stationaryDoorOpenedAtByTile.keySet().stream()
                .anyMatch(door -> door != null
                        && door.getPlane() == fromWp.getPlane()
                        && (door.distanceTo2D(fromWp) <= segmentDoorSuppressDist
                                || door.distanceTo2D(toWp) <= segmentDoorSuppressDist));
    }

    /** Exact-tile variant; expires the entry on a stale read exactly as the old direct-map read did. */
    public boolean wasStationaryDoorOpenedWithin(WorldPoint doorTile, long suppressMs, long nowMs) {
        if (doorTile == null) {
            return false;
        }
        Long openedAt = stationaryDoorOpenedAtByTile.get(doorTile);
        if (openedAt == null) {
            return false;
        }
        if (nowMs - openedAt > suppressMs) {
            stationaryDoorOpenedAtByTile.remove(doorTile);
            return false;
        }
        return true;
    }

    /**
     * Session-permanent refusal: a door proven quest/stat-locked by a failed interact (dialogue with
     * lock keywords, or a locked message). Unlike the walk-scoped strike-out blocks, these never
     * come back within the session — the lock will not open because the walker retried.
     */
    public void blacklistDoor(WorldPoint doorTile) {
        if (doorTile != null) {
            blacklistedDoorTiles.add(doorTile);
        }
    }

    public boolean isDoorBlacklisted(WorldPoint doorTile) {
        return doorTile != null && blacklistedDoorTiles.contains(doorTile);
    }

    /** Test hook: the blacklist is session-permanent by design, so only tests may empty it. */
    public void clearBlacklist() {
        blacklistedDoorTiles.clear();
    }

    // ---- walk-runtime facets (D3 slice 4): pass claims, settle window, cooldown, raw-scan focus ----

    private final Map<String, WorldPoint> edgeAttemptPosByKeyThisPass = new ConcurrentHashMap<>();
    private volatile long settleStartedAtMs;
    private volatile long settleUntilMs;
    private volatile WorldPoint settleFarSideWp;
    private volatile long globalCooldownUntilMs;
    private volatile Integer rawScanFocusDoorIdx;
    private volatile long rawScanFocusSetAtMs;
    private volatile int rawScanFocusAttempts;

    /** A new tail pass gets a fresh per-edge attempt budget (formerly doorEdgesAttemptedThisTail). */
    public void beginTailPass() {
        edgeAttemptPosByKeyThisPass.clear();
    }

    /**
     * One-shot budget per edge per pass, re-armed once the player has genuinely MOVED since the
     * previous attempt (within one tile of the recorded position = still the same stand, refuse).
     * A null recorded position never binds — preserving the old map's null-value semantics.
     */
    public boolean tryClaimEdgeThisPass(WorldPoint fromWp, WorldPoint toWp, WorldPoint playerBeforeAttempt) {
        if (fromWp == null || toWp == null) {
            return true;
        }
        String edgeKey = Rs2DoorHandler.doorAttemptKey(null, fromWp, toWp);
        WorldPoint previous = edgeAttemptPosByKeyThisPass.get(edgeKey);
        if (previous != null && playerBeforeAttempt != null
                && previous.getPlane() == playerBeforeAttempt.getPlane()
                && previous.distanceTo2D(playerBeforeAttempt) <= 1) {
            return false;
        }
        if (playerBeforeAttempt != null) {
            edgeAttemptPosByKeyThisPass.put(edgeKey, playerBeforeAttempt);
        } else {
            edgeAttemptPosByKeyThisPass.remove(edgeKey);
        }
        return true;
    }

    /** Hands the budget back when no interaction happened — a later resolver may try the edge this pass. */
    public void releaseEdgeThisPass(WorldPoint fromWp, WorldPoint toWp) {
        if (fromWp != null && toWp != null) {
            edgeAttemptPosByKeyThisPass.remove(Rs2DoorHandler.doorAttemptKey(null, fromWp, toWp));
        }
    }

    /** Starts the door settle window, remembering the far-side tile so it can end when the edge opens. */
    public void markSettling(WorldPoint farSideWp, long nowMs, long settleMs) {
        settleStartedAtMs = nowMs;
        settleUntilMs = nowMs + settleMs;
        settleFarSideWp = farSideWp;
    }

    public long settleStartedAtMs() {
        return settleStartedAtMs;
    }

    public long settleUntilMs() {
        return settleUntilMs;
    }

    public WorldPoint settleFarSide() {
        return settleFarSideWp;
    }

    /** The far side proved reachable: the edge is open, there is nothing left to settle. */
    public void endSettleEarly() {
        settleUntilMs = 0L;
        settleFarSideWp = null;
    }

    public void markGlobalCooldownUntil(long untilMs) {
        globalCooldownUntilMs = untilMs;
    }

    public long globalCooldownUntilMs() {
        return globalCooldownUntilMs;
    }

    /** The raw scene scan commits to one door index for a bounded number of attempts. */
    public void setRawScanFocus(int index, long nowMs) {
        rawScanFocusDoorIdx = index;
        rawScanFocusSetAtMs = nowMs;
        rawScanFocusAttempts = 0;
    }

    public Integer rawScanFocusDoorIdx() {
        return rawScanFocusDoorIdx;
    }

    public long rawScanFocusSetAtMs() {
        return rawScanFocusSetAtMs;
    }

    public int rawScanFocusAttempts() {
        return rawScanFocusAttempts;
    }

    public void recordRawScanFocusAttempt() {
        rawScanFocusAttempts++;
    }

    public void clearRawScanFocus() {
        rawScanFocusDoorIdx = null;
        rawScanFocusSetAtMs = 0L;
        rawScanFocusAttempts = 0;
    }
}
