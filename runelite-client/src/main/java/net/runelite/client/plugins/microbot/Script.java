package net.runelite.client.plugins.microbot;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.agentserver.handler.ScriptHeartbeatRegistry;
import net.runelite.client.plugins.microbot.util.antiban.SessionFatigue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base class for Microbot automation scripts.
 * Provides scheduling helpers, guards against client-thread misuse, and common shutdown/reset logic.
 */
@Slf4j
public abstract class Script extends Global implements IScript {
    protected ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(10,
        new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            @Override
            public Thread newThread(@NotNull Runnable r) {
                Thread t = new Thread(r);
                t.setName(Script.this.getClass().getSimpleName() + "-" + threadNumber.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        });
    protected ScheduledFuture<?> scheduledFuture;
    protected ScheduledFuture<?> mainScheduledFuture;

    /**
     * Indicates whether the main scheduled script loop is still active.
     */
    public boolean isRunning() {
        return mainScheduledFuture != null && !mainScheduledFuture.isDone();
    }

    @Getter
    protected static WorldPoint initialPlayerLocation;

    /**
     * Cancel scheduled tasks, clear shared state, and reset helpers.
     * Safe to call multiple times; no-ops if already shut down.
     */
    public void shutdown() {
        ScriptHeartbeatRegistry.remove(this.getClass().getName());
        if (mainScheduledFuture != null && !mainScheduledFuture.isDone()) {
            mainScheduledFuture.cancel(true);
            ShortestPathPlugin.exit();
            if (Microbot.getClientThread().scheduledFuture != null)
                Microbot.getClientThread().scheduledFuture.cancel(true);
            initialPlayerLocation = null;
            Microbot.pauseAllScripts.set(false);
            Rs2Walker.disableTeleports = false;
            Microbot.getSpecialAttackConfigs().reset();
        }
        if (scheduledFuture != null && !scheduledFuture.isDone()) {
            scheduledFuture.cancel(true);
        }
    }

    /**
     * Default pre-loop guard invoked by script schedulers.
     * Returns {@code false} to pause a loop when a blocking event is executing, scripts are paused,
     * tutorial island is incomplete, or the current thread is interrupted.
     */
    public boolean run() {
        ScriptHeartbeatRegistry.recordHeartbeat(this.getClass().getName());

        if (Microbot.isLoggedIn() && !SessionFatigue.isActive()) {
            SessionFatigue.startSession();
        }

        if (Microbot.isLoggedIn() && !Rs2Player.hasCompletedTutorialIsland())
            return true;

        if (Rs2Player.hasCompletedTutorialIsland() && Microbot.getBlockingEventManager().shouldBlockAndProcess()) {
            // A blocking event was found & is executing
            return false;
        }
        if (Microbot.pauseAllScripts.get())
            return false;
        if (Thread.currentThread().isInterrupted())
            return false;

        if (Microbot.isLoggedIn()) {
            boolean hasRunEnergy = Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getEnergy()).orElse(0) > Microbot.runEnergyThreshold;
            if (Microbot.enableAutoRunOn && hasRunEnergy)
                Rs2Player.toggleRunEnergy(true);
            if (!hasRunEnergy && Microbot.useStaminaPotsIfNeeded && Rs2Player.isMoving()) {
                Rs2Inventory.useRestoreEnergyItem();
            }
        }
        return true;
    }
}
