package net.runelite.client.plugins.microbot.util.walker.state;

import net.runelite.api.coords.WorldPoint;

/**
 * Consolidated mutable route state for the walker, extracted from {@code Rs2Walker}'s scattered static
 * fields. This is the enabling step for P1 of the walker audit: once the state the {@code processWalk}
 * loop, recovery, and transport handling all share lives in one place, those pieces can be lifted into
 * their own classes ({@code WalkExecutor} / {@code RouteRecovery} / {@code TransportService}) without
 * threading a dozen parameters through every call.
 * <p>
 * Fields are migrated in cohesive clusters, one increment at a time, each verified by compilation (which
 * catches any missed reference) and the walker test suite. Access stays {@code volatile} to preserve the
 * cross-thread visibility the original static fields had (the script thread writes, overlays/other reads).
 * Fields are public for now to keep the migration a pure mechanical move; encapsulation can follow once
 * all clusters are in.
 */
public final class WalkerRouteState {

    // ---- transport handoff: set when a transport (stairs, ladder, shortcut, teleport) is taken, read by
    // the post-transport settling/window logic in processWalk. ----

    /**
     * Wall-clock ms when the last transport was handled; 0 when none this session.
     *
     * <p>This is the field every post-transport window check actually reads, so it is the one that
     * decides whether handlers are suppressed. Clearing the locations below without clearing this
     * leaves the window armed — see {@link #clearRecentTransportContext()}.
     */
    public volatile long lastTransportHandledAtMs = 0L;
    /** Origin tile of the last handled transport. */
    public volatile WorldPoint lastTransportOriginLocation = null;
    /** Destination tile of the last handled transport. */
    public volatile WorldPoint lastTransportDestinationLocation = null;

    /**
     * Ends the post-transport window: the handoff belongs to the route that took the transport.
     *
     * <p>Clear all of it together. Nulling only the locations leaves
     * {@link #lastTransportHandledAtMs} set, and every window check keys off that timestamp — the
     * window stays armed for its full duration while the destination it is supposed to be about is
     * already gone.
     */
    public void clearRecentTransportContext() {
        lastTransportHandledAtMs = 0L;
        lastTransportOriginLocation = null;
        lastTransportDestinationLocation = null;
    }

    // ---- route progress: tracks how far along the current route the player has advanced, used to detect
    // real forward progress (vs thrashing) and to decide when to reset on a new/changed route. ----

    /** Furthest path index reached on the current route; -1 when none. */
    public volatile int routeProgressIdx = -1;
    /** Target the current progress tracking is for. */
    public volatile WorldPoint routeProgressTarget = null;
    /** Start tile of the path the current progress tracking is for. */
    public volatile WorldPoint routeProgressPathStart = null;
    /** End tile of the path the current progress tracking is for. */
    public volatile WorldPoint routeProgressPathEnd = null;
    /** Size of the path the current progress tracking is for; -1 when none. */
    public volatile int routeProgressPathSize = -1;
    /** Wall-clock ms when route progress last advanced. */
    public volatile long routeProgressAdvancedAtMs = 0L;
    /** Stagnation replans this walk has spent (TailDecision.decideRouteStagnation). */
    public volatile int stagnationReplansSpent = 0;
    /** Furthest raw-path index the player has stood at on the current route; -1 when none. */
    public volatile int rawProgressHighIdx = -1;

    // ---- interim target: a reachable point clicked toward when the true next tile is off the minimap;
    // held until the player gets close or progress stalls. ----

    /** Currently held interim click target, or null. */
    public volatile WorldPoint interimTargetWp = null;
    /** Path index of the interim target; -1 when none. */
    public volatile int interimTargetIdx = -1;
    /** Wall-clock ms the interim target was set. */
    public volatile long interimSetAtMs = 0L;
    /** Wall-clock ms the interim target last showed progress. */
    public volatile long interimLastProgressAtMs = 0L;
    /** Best path index reached while holding the interim target; -1 when none. */
    public volatile int interimLastBestPathIdx = -1;
    /** Best (smallest) distance-to-target observed while holding the interim target. */
    public volatile int interimLastDistanceToTarget = Integer.MAX_VALUE;
    /** Wall-clock ms the interim target was last re-chosen. */
    public volatile long interimLastRetargetAtMs = 0L;

    // ---- idle nudge: detects a stationary player mid-route and re-clicks to un-stick. ----

    /** Player tile last observed by the idle-nudge check. */
    public volatile WorldPoint idleNudgeLastObservedLocation = null;
    /** Wall-clock ms since the player has been stationary (for idle-nudge). */
    public volatile long idleNudgeStationarySinceMs = 0L;
    /** Wall-clock ms of the last active-route idle nudge. */
    public volatile long lastActiveRouteIdleNudgeAtMs = 0L;

    // ---- stuck detection / movement tracking: the processWalk loop's evidence that the player is
    // actually moving, plus the cooldowns recovery uses when it is not. ----

    /** Consecutive stuck-check hits without movement; reset on any walker-issued click. */
    public volatile int stuckCount = 0;
    /** Player tile at the last stuck-check sample. */
    public volatile WorldPoint lastPosition = null;
    /** Wall-clock ms the player last changed tiles (or a click granted grace). */
    public volatile long lastMovedTimeMs = 0L;
    /**
     * Wall-clock ms the player last actually CHANGED TILE — no click grace, no pose, no animation.
     *
     * <p>Distinct from {@link #lastMovedTimeMs}, which several places refresh to buy grace and which
     * therefore cannot answer "is the player really covering ground". This one only ever moves when
     * the observed tile differs from the previous sample, which is what makes it a usable check on
     * the pose-based movement flag.
     */
    public volatile long lastTileChangeAtMs = 0L;
    /** Rising-edge detection for animation progress without tile delta in the stuck check. */
    public volatile boolean prevAnimatingForStuckCheck = false;
    /** Wall-clock ms of the last walled-recovery replan (cooldown selects replan vs wait). */
    public volatile long lastWalledRecoveryReplanAtMs = 0L;
    /** Cooldown so partial-segment in-transit path recalculation does not spam. */
    public volatile long lastPartialTransRecalcMs = 0L;
    /**
     * The route edge the walled-click net most recently refused BECAUSE a scene door sits on it
     * ({@code walled_edge_not_learned}). Replanning cannot help there — the planner's graph crosses
     * that door, so it returns the same route and the refusal loops (three identical replans over
     * 24s at the Rogues' Den pub door). Recovery consumes this to approach the door instead.
     */
    public volatile WorldPoint walledDoorEdgeFrom = null;
    public volatile WorldPoint walledDoorEdgeTo = null;
    public volatile long walledDoorEdgeAtMs = 0L;
    /** Immutable caller intent for this walk; currentTarget may temporarily become an effective rim. */
    public volatile WorldPoint requestedGoal = null;
    /** Sealed-goal rim retargets consumed this walk; bounds the chain (a rim tile can itself probe sealed). */
    public volatile int sealedRimRetargets = 0;

    // ---- door interaction (D3 slice 4: settle window, raw-scan focus, pass budget and the global
    // cooldown migrated to DoorAttemptLedger; the diagnostics timestamps below remain). ----

    /** Wall-clock ms a door-edge pass was last skipped (per-edge cooldown diagnostics). */
    public volatile long lastDoorEdgePassSkipAtMs = 0L;
    /** Cooldown for the expensive path-adjacent door scan on unreachable tiles. */
    public volatile long lastDoorPathAdjAttemptAtMs = 0L;
    /**
     * When the walker first held off a door interaction because an option menu was open; 0 when no
     * such hold-off is active. Bounds the wait so an unanswered conversation cannot stall the walk.
     */
    public volatile long doorDialogueDeferSinceMs = 0L;

    // ---- misc per-walk-session timers / flags. ----

    /** Wall-clock ms of the last unreachable-tile recovery click. */
    public volatile long lastUnreachableRecoveryClickAtMs = 0L;

    /**
     * Wall-clock ms the bank-mirror bootstrap last found no bank at all. The retry backs off from
     * this: the attempt runs a full pathfind, so repeating it every tick costs as much as it achieves.
     */
    public volatile long lastBankBootstrapMissAtMs = 0L;

    /**
     * Last time the recovery block hit the door-recovery-suppressed branch (an unresolved door sits on the
     * blocked route edge but every door handler declined due to settling/cooldowns). While this is recent,
     * the idle nudge must NOT fire: its forward click is not door-aware, so it can select a statically
     * walkable tile on the far side of the closed door — the server then paths the player AROUND the
     * building, pulling the walk off the route (seen at Clock Tower: nudge past the door, then recovery
     * clicked the unreachable end tile).
     */
    public volatile long doorRecoverySuppressedAtMs = 0L;
    /** Wall-clock ms the current walk session started. */
    public volatile long walkSessionStartedAtMs = 0L;
    /** Whether the first movement click of this walk session has been marked. */
    public volatile boolean firstMovementClickMarked = false;
    /** Suppress tryDirectShortWalk minimap clicks until this wall-clock ms (post door-nudge window). */
    public volatile long suppressTryDirectShortWalkUntilMs = 0L;
    /** Reason string of the last route clear (diagnostics). */
    public volatile String lastRouteClearReason = "";
    /** Wall-clock ms of the last route clear. */
    public volatile long lastRouteClearAtMs = 0L;
    /** Tier label of the last route click (diagnostics). */
    public volatile String lastRouteClickTier = "none";
}
