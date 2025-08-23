package net.runelite.client.plugins.microbot;

import java.time.Instant;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import java.time.Duration;
import java.time.LocalTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import org.jetbrains.annotations.NotNull;

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
	@Deprecated(since = "1.9.7 - Blocking events are now handling turning off the level up dialog", forRemoval = true)
    public static boolean hasLeveledUp = false;

    public boolean isRunning() {
        return mainScheduledFuture != null && !mainScheduledFuture.isDone();
    }

    @Getter
    protected static WorldPoint initialPlayerLocation;
    public Instant startTime;

    /**
     * Get the total runtime of the script
     *
     * @return the total runtime of the script
     */
    public Duration getRunTime() {
        if (startTime == null) return Duration.ofSeconds(0);
        return Duration.between(startTime, Instant.now());
    }

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
        startTime = null;
    }

    public boolean run() {
        if (startTime == null) {
            startTime = Instant.now();
            //init - things that have to be checked once can be added here
        }
        if (Microbot.pauseAllScripts.get())
            return false;
        if (Thread.currentThread().isInterrupted())
            return false;
        //Avoid executing any blocking events if the player hasn't finished Tutorial Island
        if (Microbot.isLoggedIn() && !Rs2Player.hasCompletedTutorialIsland())
            return true;
		if (Microbot.getBlockingEventManager().shouldBlockAndProcess()) {
			// A blocking event was found & is executing
			return false;
		}
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

    @Deprecated(since = "1.6.9 - Use Rs2Keyboard.keyPress", forRemoval = true)
    public void keyPress(char c) {
        Rs2Keyboard.keyPress(c);
    }

    @Deprecated(since = "Use Rs2Player.logout()", forRemoval = true)
    public void logout() {
        Rs2Tab.switchToLogout();
        sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.LOGOUT);
        sleep(600, 1000);
        Rs2Widget.clickWidget("Click here to logout");
    }
}
