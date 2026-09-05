package net.runelite.client.plugins.microbot.shortestpath;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.walker.WalkerState;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class ShortestPathScript extends Script {

    @Getter
    // used for calling the walker from a mainthread
    // running the walker on a seperate thread is a lot easier for debugging
    private volatile WorldPoint triggerWalker;
    private volatile ShortestPathConfig config;
    private volatile Future<?> walkTaskFuture;
    private final AtomicBoolean walkTaskRunning = new AtomicBoolean(false);
    private volatile WorldPoint lastExitRetryTarget;
    private volatile int consecutiveExitRetries = 0;
    private static final int MAX_CONSECUTIVE_EXIT_RETRIES = 3;
    private static final long USER_STOP_REASON_WINDOW_MS = 3_000L;

    public boolean run(ShortestPathConfig config) {
        this.config = config;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;

                if (getTriggerWalker() != null) {
                    startWalkTask();
                }

            } catch (Exception ex) {
                log.error("Exception in ShortestPathScript: {} - ", ex.getMessage(), ex);
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }

	public void setTriggerWalker(WorldPoint point) {
		setTriggerWalker(point, null);
	}

	/**
	 * @param stopReason when {@code point} is null, passed to {@link Rs2Walker#clearWalkingRoute(String)} (e.g. {@code hotkey:ctrl+x})
	 */
	public void setTriggerWalker(WorldPoint point, String stopReason) {
		if (point == null)
		{
			resetExitRetryState();
			String r = stopReason != null && !stopReason.isBlank()
					? stopReason
					: "shortest-path-script:trigger-null";
			triggerWalker = null;
			Rs2Walker.clearWalkingRoute(r);
            Future<?> future = walkTaskFuture;
            if (future != null && !future.isDone()) {
                future.cancel(true);
            }
            walkTaskRunning.set(false);
		} else {
			if (!point.equals(triggerWalker))
			{
				resetExitRetryState();
			}
			triggerWalker = point;
            startWalkTask();
		}
	}

    private void startWalkTask() {
        if (InputArbiter.isHuman() || !walkTaskRunning.compareAndSet(false, true)) {
            return;
        }

        walkTaskFuture = scheduledExecutorService.submit(() -> {
            try {
                WorldPoint target = getTriggerWalker();
                if (target == null || config == null || !Microbot.isLoggedIn()) {
                    return;
                }

                WalkerState state;
                if (config.walkWithBankedTransports()) {
                    state = Rs2Walker.walkWithBankedTransportsAndState(target, 10, false);
                } else {
                    state = Rs2Walker.walkWithState(target);
                }

                if (target.equals(getTriggerWalker())) {
                    if (shouldPreserveTargetAfterExit(state, InputArbiter.isHuman())) {
                        return;
                    }
                    if (state == WalkerState.EXIT && shouldRetryAfterExit(target)) {
                        return;
                    }
                    if (state == WalkerState.ARRIVED || state == WalkerState.UNREACHABLE || state == WalkerState.EXIT) {
                        resetExitRetryState();
                        triggerWalker = null;
                        Rs2Walker.clearWalkingRoute("shortest-path-script:walk-task-terminal-state");
                    }
                }
            } catch (Exception ex) {
                log.error("Exception in ShortestPathScript walk task: {} - ", ex.getMessage(), ex);
            } finally {
                walkTaskRunning.set(false);
            }
        });
    }

    static boolean shouldPreserveTargetAfterExit(WalkerState state, boolean humanOwnsInput) {
        return state == WalkerState.EXIT && humanOwnsInput;
    }

    private boolean shouldRetryAfterExit(WorldPoint target) {
        if (target == null || !target.equals(getTriggerWalker())) {
            resetExitRetryState();
            return false;
        }
        if (isLocalPlayerDead()) {
            resetExitRetryState();
            return false;
        }
        if (isRecentUserStopClear()) {
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
