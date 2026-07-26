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

    /** Wall-clock ms when the last transport was handled; 0 when none this session. */
    public volatile long lastTransportHandledAtMs = 0L;
    /** Player tile immediately after the last transport handoff. */
    public volatile WorldPoint lastTransportHandledAtLocation = null;
    /** Origin tile of the last handled transport. */
    public volatile WorldPoint lastTransportOriginLocation = null;
    /** Destination tile of the last handled transport. */
    public volatile WorldPoint lastTransportDestinationLocation = null;

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

    // ---- misc per-walk-session timers / flags. ----

    /** Wall-clock ms of the last unreachable-tile recovery click. */
    public volatile long lastUnreachableRecoveryClickAtMs = 0L;
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
