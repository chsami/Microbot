package net.runelite.client.plugins.microbot;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public abstract class Script extends Global implements IScript {
	protected ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(10,
		new ThreadFactory() {
			private final AtomicInteger threadNumber = new AtomicInteger(1);
			@Override
			public Thread newThread(@NotNull Runnable r) {
				Thread t = new Thread(r);
				t.setName(Script.this.getClass().getSimpleName() + "-" + threadNumber.getAndIncrement());
				return t;
			}
		});
    protected ScheduledFuture<?> scheduledFuture;
    protected ScheduledFuture<?> mainScheduledFuture;

    public boolean isRunning() {
        return mainScheduledFuture != null && !mainScheduledFuture.isDone();
    }

    @Getter
    protected static WorldPoint initialPlayerLocation;

    public void shutdown() {
        if (mainScheduledFuture != null && !mainScheduledFuture.isDone()) {
            mainScheduledFuture.cancel(true);
            ShortestPathPlugin.exit();
            if (Microbot.getClientThread().scheduledFuture != null)
                Microbot.getClientThread().scheduledFuture.cancel(true);
            initialPlayerLocation = null;
            Microbot.pauseAllScripts.set(false);
            Rs2Walker.disableTeleports = false;
            Microbot.getSpecialAttackConfigs().reset();
            Rs2Walker.setTarget(null);
        }
        if (scheduledFuture != null && !scheduledFuture.isDone()) {
            scheduledFuture.cancel(true);
        }
    }

    public boolean run() {
        //Avoid executing any blocking events if the player hasn't finished Tutorial Island
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
            boolean hasRunEnergy = Microbot.getClient().getEnergy() > Microbot.runEnergyThreshold;
            if (Microbot.enableAutoRunOn && hasRunEnergy)
                Rs2Player.toggleRunEnergy(true);
            if (!hasRunEnergy && Microbot.useStaminaPotsIfNeeded && Rs2Player.isMoving()) {
                Rs2Inventory.useRestoreEnergyItem();
            }
        }
        return true;
    }
}
