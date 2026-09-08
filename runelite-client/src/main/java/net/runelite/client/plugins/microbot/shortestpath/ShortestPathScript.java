package net.runelite.client.plugins.microbot.shortestpath;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.walker.WalkerState;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
public class ShortestPathScript extends Script {

    @Getter
    // used for calling the walker from a mainthread
    // running the walker on a seperate thread is a lot easier for debugging
    private volatile WorldPoint triggerWalker;
    private volatile ShortestPathConfig config;
    private final Consumer<WorldPoint> preview;
    private final Consumer<ManualWalkingNotice> notice;

    enum ManualWalkingNotice {
        PATH_SET, ENABLED, PAUSED, CLEARED
    }
    private boolean walkingEnabled = true;
    private boolean stopped;
    private boolean walkTaskRunning;
    private Thread walkThread;
    private boolean previewPending;
    private long revision;
    private volatile WorldPoint lastExitRetryTarget;
    private volatile int consecutiveExitRetries;
    private static final int MAX_CONSECUTIVE_EXIT_RETRIES = 3;
    private static final long USER_STOP_REASON_WINDOW_MS = 3_000L;

    public ShortestPathScript(Consumer<WorldPoint> preview) {
        this(preview, ignored -> {});
    }

    ShortestPathScript(Consumer<WorldPoint> preview, Consumer<ManualWalkingNotice> notice) {
        this.preview = preview;
        this.notice = notice;
    }

    public boolean run(ShortestPathConfig config) {
        this.config = config;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (loggedIn()) {
                    startWalkTask();
                }
            } catch (Exception ex) {
                log.error("Exception in ShortestPathScript", ex);
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        synchronized (this) {
            stopped = true;
            revision++;
            triggerWalker = null;
            interruptWalk();
        }
        super.shutdown();
    }

    public synchronized boolean isWalkingEnabled() {
        return walkingEnabled;
    }

    public synchronized void toggleWalking() {
        if (stopped) {
            return;
        }
        walkingEnabled = !walkingEnabled;
        notice.accept(walkingEnabled ? ManualWalkingNotice.ENABLED : ManualWalkingNotice.PAUSED);
        revision++;
        resetExitRetryState();
        interruptWalk();
        // The old worker must finish before a preview or a replacement walk can own the route.
        if (!walkTaskRunning && triggerWalker != null) {
            refreshPreview();
        }
    }

    public void setTriggerWalker(WorldPoint point) {
        setTriggerWalker(point, null);
    }

    public synchronized void setTriggerWalker(WorldPoint point, String stopReason) {
        if (stopped) {
            return;
        }
        revision++;
        previewPending = false;
        triggerWalker = point;
        resetExitRetryState();
        interruptWalk();
        if (point == null) {
            clearRoute(stopReason == null ? "shortest-path-script:trigger-null" : stopReason);
        }
        if (!walkTaskRunning && point != null) {
            refreshPreview();
        }
        if (point == null) {
            notice.accept(ManualWalkingNotice.CLEARED);
        } else if (!walkingEnabled) {
            notice.accept(ManualWalkingNotice.PATH_SET);
        }
    }

    private void interruptWalk() {
        if (walkThread != null) {
            walkThread.interrupt();
        }
    }

    private void refreshPreview() {
        previewPending = true;
        final long expectedRevision = revision;
        final WorldPoint target = triggerWalker;
        onClientThread(() -> {
            synchronized (ShortestPathScript.this) {
                if (!stopped && revision == expectedRevision && !walkTaskRunning) {
                    try {
                        if (loggedIn()) {
                            preview.accept(target);
                        }
                    } finally {
                        previewPending = false;
                    }
                }
            }
        });
    }

    synchronized void startWalkTask() {
        if (stopped || !walkingEnabled || triggerWalker == null || walkTaskRunning || previewPending) {
            return;
        }
        final long taskRevision = revision;
        final WorldPoint target = triggerWalker;
        walkTaskRunning = true;
        scheduledExecutorService.submit(() -> {
            try {
                synchronized (ShortestPathScript.this) {
                    if (stopped || taskRevision != revision) {
                        return;
                    }
                    walkThread = Thread.currentThread();
                }
                if (!loggedIn()) {
                    return;
                }
                WalkerState state = executeWalk(target);
                // Client-thread reads must not hold this monitor: hotkeys also acquire it.
                boolean retryAllowed = state == WalkerState.EXIT
                        && !Thread.currentThread().isInterrupted() && !isLocalPlayerDead() && !isRecentUserStopClear();
                synchronized (ShortestPathScript.this) {
                    if (taskRevision != revision || stopped) {
                        return;
                    }
                    if (state == WalkerState.EXIT && retryAllowed && shouldRetryAfterExit(target)) {
                        return;
                    }
                    if (state == WalkerState.ARRIVED || state == WalkerState.UNREACHABLE || state == WalkerState.EXIT) {
                        resetExitRetryState();
                        triggerWalker = null;
                        revision++;
                        clearRoute("shortest-path-script:walk-task-terminal-state");
                    }
                }
            } catch (Exception ex) {
                if (!Thread.currentThread().isInterrupted()) {
                    log.error("Exception in ShortestPathScript walk task", ex);
                }
            } finally {
                synchronized (ShortestPathScript.this) {
                    walkThread = null;
                    walkTaskRunning = false;
                    // Clear the cancellation flag before scheduling client-thread work on this pool thread.
                    Thread.interrupted();
                    if (!stopped && taskRevision != revision) {
                        clearRoute("shortest-path-script:manual-route-changed");
                        if (triggerWalker != null) {
                            refreshPreview();
                        }
                    }
                }
            }
        });
    }

    boolean loggedIn() {
        return Microbot.isLoggedIn();
    }

    WalkerState executeWalk(WorldPoint target) {
        return config.walkWithBankedTransports()
                ? Rs2Walker.walkWithBankedTransportsAndState(target, 10, false)
                : Rs2Walker.walkWithState(target);
    }

    void clearRoute(String reason) {
        ShortestPathPlugin.invalidatePendingPathfinding();
        Rs2Walker.clearWalkingRoute(reason);
    }

    void onClientThread(Runnable action) {
        Microbot.getClientThread().invokeLater(action);
    }

    private boolean shouldRetryAfterExit(WorldPoint target) {
        if (target == null || !target.equals(getTriggerWalker())) {
            resetExitRetryState();
            return false;
        }
        if (!target.equals(lastExitRetryTarget)) {
            lastExitRetryTarget = target;
            consecutiveExitRetries = 0;
        }
        if (consecutiveExitRetries >= MAX_CONSECUTIVE_EXIT_RETRIES) {
            log.warn("[ShortestPathScript] EXIT retry limit reached for target={} retries={}",
                    target, consecutiveExitRetries);
            resetExitRetryState();
            return false;
        }
        consecutiveExitRetries++;
        log.info("[ShortestPathScript] EXIT auto-retry {}/{} for target={}",
                consecutiveExitRetries, MAX_CONSECUTIVE_EXIT_RETRIES, target);
        return true;
    }

    private boolean isRecentUserStopClear() {
        long clearAt = Rs2Walker.getLastRouteClearAtMs();
        if (clearAt <= 0 || System.currentTimeMillis() - clearAt > USER_STOP_REASON_WINDOW_MS) {
            return false;
        }
        String reason = Rs2Walker.getLastRouteClearReason();
        if (reason == null) {
            return false;
        }
        String normalized = reason.toLowerCase();
        return normalized.contains("ctrl+x")
                || normalized.contains("stop-walking-button")
                || normalized.contains("trigger-null");
    }

    private boolean isLocalPlayerDead() {
        return Microbot.getClientThread()
                .runOnClientThreadOptional(() -> {
                    Player local = Microbot.getClient().getLocalPlayer();
                    return local != null && local.isDead();
                })
                .orElse(false);
    }

    private void resetExitRetryState() {
        lastExitRetryTarget = null;
        consecutiveExitRetries = 0;
    }
}
