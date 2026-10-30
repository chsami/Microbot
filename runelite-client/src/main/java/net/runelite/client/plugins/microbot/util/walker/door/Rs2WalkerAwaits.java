package net.runelite.client.plugins.microbot.util.walker.door;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.door.model.AwaitTicket;
import net.runelite.client.plugins.microbot.util.walker.door.model.DoorResolution;
import net.runelite.client.plugins.microbot.util.walker.WebWalkLog;
import net.runelite.client.plugins.microbot.util.walker.shared.Rs2WalkerProgress;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

public final class Rs2WalkerAwaits {
    private static final int DOOR_INTERACTION_START_WAIT_MS = 700;
    private static final int DOOR_TRAVERSAL_PROGRESS_WAIT_MS = 2200;
    /** Stationary and not animating for longer than this, with the edge unresolved, means the click didn't land. */
    private static final long DOOR_IDLE_ACCEPT_MIN_MS = 1_200L;
    /** Above this combined wait, say which condition released the door await. */
    private static final long DOOR_AWAIT_SLOW_LOG_MS = 900L;
    /**
     * An unlocked door opens within one game tick of the click landing, so there is nothing to observe
     * before then and polling earlier only spends client-thread time. Checked on an interval rather
     * than every poll because the observation is a scene scan (~60ms measured), not a field read.
     */
    private static final long DOOR_OPEN_POLL_START_MS = 250L;
    private static final long DOOR_OPEN_POLL_INTERVAL_MS = 250L;
    /**
     * A click issued from further than the legacy dispatch band is a RANGED click: the server has to
     * walk us to the door before anything can open, so the wait is an approach, not a traversal.
     */
    static final int RANGED_CLICK_MIN_TILES = 3;
    /** Walking pace is one tile per 0.6s; running arrives sooner and releases early via the edge read. */
    private static final long APPROACH_MS_PER_TILE = 600L;
    /** Hard ceiling on any door await. The stall release keeps long budgets from ever stranding us. */
    private static final long DOOR_TRAVERSAL_MAX_BUDGET_MS = 8_000L;

    /**
     * How long the traversal phase may hold, given how far from the door the click was issued.
     * <p>
     * The flat 2200ms cap was sized for adjacent clicks — walk a step, door opens, step through. A
     * ranged click spends its first seconds being WALKED to the door by the server, so the flat cap
     * expired mid-approach: the wait released by timeout, the recovery machinery got its window (the
     * competing-clicks race), and the door was then handled a second time from adjacent. Measured as
     * two full interactions per ranged door.
     */
    static long traversalBudgetMs(int clickDistanceTiles) {
        if (clickDistanceTiles < RANGED_CLICK_MIN_TILES) {
            return DOOR_TRAVERSAL_PROGRESS_WAIT_MS;
        }
        return Math.min(DOOR_TRAVERSAL_PROGRESS_WAIT_MS + (clickDistanceTiles - 2) * APPROACH_MS_PER_TILE,
                DOOR_TRAVERSAL_MAX_BUDGET_MS);
    }

    private Rs2WalkerAwaits() {
    }

    public static AwaitTicket beginTicket() {
        return new AwaitTicket(System.currentTimeMillis(), Rs2Player.getWorldLocation());
    }

    /**
     * A door that answered with a conversation is never going to move us while we stand here waiting
     * for movement, so waiting out the full budget just burns the window in which the menu could be
     * answered — measured at up to ~2.2s per attempt, which is why answering a guarded door used to
     * take two or three tries to catch. Reads a cached, client-thread-refreshed flag, so polling it
     * costs nothing here.
     */
    private static boolean conversationOpened() {
        return Rs2Dialogue.hasSelectAnOption();
    }

    public static void awaitDoorInteractionProgress(AwaitTicket ticket, WorldPoint fromWp, WorldPoint toWp) {
        awaitDoorInteractionProgress(ticket, fromWp, toWp, null);
    }

    /**
     * @param doorOpened observes the DOOR (its "Open" action is gone), as opposed to every other
     *                   release condition here, which observes the PLAYER. May be {@code null}.
     */
    public static void awaitDoorInteractionProgress(AwaitTicket ticket, WorldPoint fromWp, WorldPoint toWp,
                                                    java.util.function.BooleanSupplier doorOpened) {
        awaitDoorInteractionProgress(ticket, fromWp, toWp, doorOpened, null);
    }

    /**
     * @param doorObservation describes what the door observation last SAW, carried onto the slow log.
     *                        Two live runs failed to explain why {@code door-opened} never fires, and
     *                        "the poll ran and said no" is not an explanation without the reading
     *                        behind it. Evaluated once, only when the log is about to print.
     */
    public static void awaitDoorInteractionProgress(AwaitTicket ticket, WorldPoint fromWp, WorldPoint toWp,
                                                    java.util.function.BooleanSupplier doorOpened,
                                                    java.util.function.Supplier<String> doorObservation) {
        awaitDoorInteractionProgress(ticket, fromWp, toWp, doorOpened, doorObservation, null);
    }

    /**
     * @param cancelled the walk this door belongs to was cancelled or re-targeted; holding an await
     *                  for a route that no longer exists serves nobody. Matters now that ranged
     *                  budgets can reach seconds where the flat cap bounded the stale hold at 2.2s.
     */
    public static void awaitDoorInteractionProgress(AwaitTicket ticket, WorldPoint fromWp, WorldPoint toWp,
                                                    java.util.function.BooleanSupplier doorOpened,
                                                    java.util.function.Supplier<String> doorObservation,
                                                    java.util.function.BooleanSupplier cancelled) {
        awaitDoorInteractionProgress(ticket, fromWp, toWp, doorOpened, doorObservation, cancelled, null);
    }

    /**
     * @param doorCrossed observes the WALL FACE: the player already stands on the far side of the
     *                    door's own face relative to the approach tile. The one reading that stays
     *                    true when a moves-you gate deposits the player DIAGONALLY off the planned
     *                    to-tile — where arrived-far-side and crossedDoorAxis both go blind (the
     *                    attempt edge itself can be diagonal, and the deposit tile is not toWp).
     *                    Cheap per poll; may be {@code null} when the door is not a wall object.
     */
    public static void awaitDoorInteractionProgress(AwaitTicket ticket, WorldPoint fromWp, WorldPoint toWp,
                                                    java.util.function.BooleanSupplier doorOpened,
                                                    java.util.function.Supplier<String> doorObservation,
                                                    java.util.function.BooleanSupplier cancelled,
                                                    java.util.function.BooleanSupplier doorCrossed) {
        if (ticket == null) {
            return;
        }
        long startPhaseAt = System.currentTimeMillis();
        sleepUntil(() -> {
            if (Thread.currentThread().isInterrupted() || conversationOpened()) {
                return true;
            }
            WorldPoint now = Rs2Player.getWorldLocation();
            if (ticket.beforePosition() != null && now != null && !ticket.beforePosition().equals(now)) {
                return true;
            }
            return Rs2Player.isMoving() || Rs2Player.isAnimating() || isDoorEdgeResolved(fromWp, toWp);
        }, DOOR_INTERACTION_START_WAIT_MS);
        long startWaitMs = System.currentTimeMillis() - startPhaseAt;

        // Which condition releases the traversal wait is the whole question for door latency: it
        // already returns the moment the door EDGE resolves, so a wait that runs to its 2200ms cap
        // means the edge is not being observed quickly (the live overlay needs a scene recapture
        // before it sees the door open) rather than the door being slow. Naming the winner turns
        // "doors feel slow" into a specific target — the same play that took the transport problem
        // from four rounds of guessing to a one-shot fix.
        final String[] releasedBy = {"timeout"};
        final long[] lastOpenPollAt = {0L};
        // Carried into the slow log: a release that is NOT door-opened is ambiguous between "the
        // observation never ran" and "it ran and the door was shut", and the first live run could not
        // tell those apart. The count settles it without another round trip.
        final int[] openPolls = {0};
        final String[] lastEdge = {"-"};
        final int[] totalPolls = {0};
        final int[] movingPolls = {0};
        final int[] animatingPolls = {0};

        // A ranged click is an APPROACH, not a traversal: the server walks us to the door, the door
        // opens on arrival (its 0-1 tick is measured from the interaction, not from the click), and
        // only then is there anything to traverse. Live edge= data proved every "still shut" reading
        // during the walk was CORRECT — the door genuinely is shut until we get there. The positional
        // conditions are therefore wrong for this phase: "progress" fired at Chebyshev 2 mid-approach
        // and "edge-resolved" fires on reaching the near side, both before the door opened, so every
        // ranged door failed verification and was interacted twice. While approaching, a ranged wait
        // holds until a DOOR outcome (edge open / opening action gone / conversation) or a stall.
        final int clickDistance = ticket.beforePosition() == null || fromWp == null
                || ticket.beforePosition().getPlane() != fromWp.getPlane()
                ? 0
                : ticket.beforePosition().distanceTo2D(fromWp);
        final boolean ranged = clickDistance >= RANGED_CLICK_MIN_TILES;

        long traversalPhaseAt = System.currentTimeMillis();
        sleepUntil(() -> {
            if (Thread.currentThread().isInterrupted() || conversationOpened()) {
                releasedBy[0] = "conversation-or-interrupt";
                return true;
            }
            if (cancelled != null && cancelled.getAsBoolean()) {
                releasedBy[0] = "cancelled-or-replanned";
                return true;
            }
            WorldPoint now = Rs2Player.getWorldLocation();
            if (now == null) {
                return false;
            }
            boolean edgeResolved = !ranged && isDoorEdgeResolved(fromWp, toWp);
            if (edgeResolved) {
                releasedBy[0] = "edge-resolved";
                return true;
            }
            if (doorCrossed != null && doorCrossed.getAsBoolean()) {
                releasedBy[0] = "crossed-face";
                return true;
            }
            // The one positional reading a ranged hold may trust: we are ON the far side, or past the
            // door along its own axis. Near-side proximity stays disabled for ranged clicks — that was
            // the premature release — but "past" is unambiguous, and it is how a hold ends when the
            // server walks us to the far side through another opening without the door ever needing to
            // open. Measured as a 6.9s ranged timeout with the player standing on toWp, door shut.
            if (ranged && (now.equals(toWp) || Rs2DoorGeometry.crossedDoorAxis(fromWp, toWp, now))) {
                releasedBy[0] = "passed-door";
                return true;
            }
            // The door observations. The collision edge is authoritative — the client's flags are
            // server-driven, so an opened door clears its block on that tick, whatever its menu says.
            // Throttled because the fallback is a scene scan, not a field read.
            long nowMs = System.currentTimeMillis();
            if (shouldPollDoorOpen(nowMs - traversalPhaseAt, nowMs - lastOpenPollAt[0])) {
                lastOpenPollAt[0] = nowMs;
                openPolls[0]++;
                boolean edgeOpen = Rs2Tile.isEdgePassable(fromWp, toWp);
                // Captured HERE, not when the log prints: the previous diagnostic read the door after
                // the wait had already released and reported the state at the wrong instant.
                lastEdge[0] = Rs2Tile.lastEdgeDecision();
                if (edgeOpen) {
                    releasedBy[0] = "door-edge-open";
                    return true;
                }
                // A "blocked" edge reading is definitive — the door is shut — so the scene-scan
                // fallback only runs when the edge could not be decided (instance, off-scene, ...).
                if (!"blocked".equals(lastEdge[0])
                        && doorOpened != null && doorOpened.getAsBoolean()) {
                    releasedBy[0] = "door-opened";
                    return true;
                }
            }
            if (!ranged && Rs2WalkerProgress.isWithinChebyshev(now, toWp, 1)) {
                releasedBy[0] = "arrived-far-side";
                return true;
            }
            if (!ranged && hasMeaningfulDoorProgress(ticket.beforePosition(), now, fromWp, toWp)) {
                releasedBy[0] = "progress";
                return true;
            }
            long elapsedMs = System.currentTimeMillis() - ticket.startedAtMs();
            // Counted so a timeout can say why the stall release never fired — "idle-accept was
            // silent" is ambiguous between the player walking the whole budget (correct silence)
            // and the pose-based isMoving trap (a bug). The tally answers it from one log line.
            boolean moving = Rs2Player.isMoving();
            boolean animating = Rs2Player.isAnimating();
            totalPolls[0]++;
            if (moving) {
                movingPolls[0]++;
            }
            if (animating) {
                animatingPolls[0]++;
            }
            boolean idleAccepted = shouldAcceptIdleDoorAwait(moving, animating, elapsedMs, edgeResolved);
            if (idleAccepted) {
                releasedBy[0] = "idle-accepted";
            }
            return idleAccepted;
        }, (int) traversalBudgetMs(clickDistance));
        long traversalWaitMs = System.currentTimeMillis() - traversalPhaseAt;

        // A conversation the questing layer has choices for is answered here, not on its slower tick,
        // which is not reached while the walker loop runs. No published choices => no-op.
        if (conversationOpened()
                && net.runelite.client.plugins.microbot.util.dialogues.DialogueChoiceProvider.answerOpenDialogue(10_000L)) {
            WebWalkLog.spInfo("door_dialogue_answered | from={} to={}", fromWp, toWp);
        }

        if (startWaitMs + traversalWaitMs >= DOOR_AWAIT_SLOW_LOG_MS) {
            String saw = "-";
            if (doorObservation != null && !"door-opened".equals(releasedBy[0])) {
                try {
                    String detail = doorObservation.get();
                    saw = detail == null ? "-" : detail;
                } catch (RuntimeException ignored) {
                    saw = "error";
                }
            }
            WebWalkLog.spInfo("door_await | releasedBy={} startWaitMs={} traversalWaitMs={} clickDist={} ranged={} openPolls={} edge={} polls={} movingPolls={} animPolls={} saw={} from={} to={}",
                    releasedBy[0], startWaitMs, traversalWaitMs, clickDistance, ranged, openPolls[0], lastEdge[0],
                    totalPolls[0], movingPolls[0], animatingPolls[0], saw, fromWp, toWp);
        }
    }

    /**
     * Whether to stop waiting because nothing is happening.
     * <p>
     * This used to end in {@code return edgeResolved}, which made it dead code: the traversal wait
     * returns early the moment the edge resolves, so by the time this runs {@code edgeResolved} is
     * always false and the branch could never fire. The wait therefore always ran to its full
     * {@link #DOOR_TRAVERSAL_PROGRESS_WAIT_MS} budget whenever a door interaction simply did not take.
     * <p>
     * An unresolved edge with the player standing still and not animating well past the interaction
     * tick means the click did not land; the caller re-enters and tries again, which is strictly
     * better than burning the remaining second. Movement, animation and the minimum elapsed time all
     * still veto — a resolved edge does not bypass them, it is simply no longer required.
     * <p>
     * {@code edgeResolved} is retained in the signature because callers pass their own observation
     * and it keeps the decision table explicit about the case that used to be the only one accepted.
     */
    /**
     * Whether to spend a door-open observation on this poll.
     * <p>
     * Two rules, both about cost rather than correctness. An unlocked door opens within one game tick
     * of the click landing, so an observation before {@link #DOOR_OPEN_POLL_START_MS} can only ever
     * report "still shut" and is pure waste. And the observation is a scene scan (~60ms measured), not
     * a field read, so at the poll rate of the surrounding wait it would otherwise run several times a
     * second for the whole budget — the cost that made door handling expensive in the first place.
     */
    static boolean shouldPollDoorOpen(long sinceTraversalStartMs, long sinceLastPollMs) {
        return sinceTraversalStartMs >= DOOR_OPEN_POLL_START_MS
                && sinceLastPollMs >= DOOR_OPEN_POLL_INTERVAL_MS;
    }

    @SuppressWarnings("unused")
    static boolean shouldAcceptIdleDoorAwait(boolean moving, boolean animating, long elapsedMs, boolean edgeResolved) {
        if (moving || animating) {
            return false;
        }
        return elapsedMs > DOOR_IDLE_ACCEPT_MIN_MS;
    }

    public static DoorResolution awaitDoorEdgeResolution(WorldPoint fromWp, WorldPoint toWp, int timeoutMs) {
        if (fromWp == null || toWp == null) {
            return DoorResolution.FAILED_INVALID;
        }
        if (isDoorEdgeResolved(fromWp, toWp)) {
            return DoorResolution.RESOLVED;
        }
        sleepUntil(() -> isDoorEdgeResolved(fromWp, toWp), timeoutMs);
        return isDoorEdgeResolved(fromWp, toWp) ? DoorResolution.RESOLVED : DoorResolution.FAILED_TIMEOUT;
    }

    public static boolean isDoorEdgeResolved(WorldPoint fromWp, WorldPoint toWp) {
        if (fromWp == null || toWp == null) {
            return false;
        }
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null || player.getPlane() != toWp.getPlane()) {
            return false;
        }
        if (Rs2WalkerProgress.isWithinChebyshev(player, toWp, 1)) {
            return true;
        }
        int toDist = player.distanceTo2D(toWp);
        int fromDist = player.distanceTo2D(fromWp);
        if (toDist + 1 < fromDist) {
            return true;
        }
        try {
            if (!Rs2Player.isMoving() && toDist <= 4 && Rs2Tile.isTileReachable(toWp)) {
                return true;
            }
        } catch (RuntimeException ex) {
            // Script shutdown can interrupt client-thread invoke in reachability probe.
            Throwable cause = ex.getCause();
            if (Thread.currentThread().isInterrupted() || cause instanceof InterruptedException) {
                return false;
            }
            throw ex;
        }
        return false;
    }

    private static boolean hasMeaningfulDoorProgress(WorldPoint before, WorldPoint now, WorldPoint fromWp, WorldPoint toWp) {
        if (before == null || now == null || toWp == null) {
            return false;
        }
        if (!now.equals(before) && Rs2WalkerProgress.isWithinChebyshev(now, toWp, 2)) {
            return true;
        }
        if (fromWp == null || before.getPlane() != now.getPlane() || now.getPlane() != toWp.getPlane()) {
            return false;
        }
        int beforeTo = before.distanceTo2D(toWp);
        int nowTo = now.distanceTo2D(toWp);
        int beforeFrom = before.distanceTo2D(fromWp);
        int nowFrom = now.distanceTo2D(fromWp);
        return nowTo < beforeTo && nowFrom >= beforeFrom;
    }
}
