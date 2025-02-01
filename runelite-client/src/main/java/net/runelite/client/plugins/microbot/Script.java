package net.runelite.client.plugins.microbot;

import com.google.common.base.Stopwatch;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.InterfaceID;
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
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

@Slf4j
public abstract class Script implements IScript {

    protected ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(10);
    protected ScheduledFuture<?> scheduledFuture;
    public ScheduledFuture<?> mainScheduledFuture;
    public static boolean hasLeveledUp = false;
    public static boolean useStaminaPotsIfNeeded = true;

    public boolean isRunning() {
        return mainScheduledFuture != null && !mainScheduledFuture.isDone();
    }

    @Getter
    protected static WorldPoint initialPlayerLocation;

    public LocalTime startTime;

    /**
     * Get the total runtime of the script
     *
     * @return
     */
    public Duration getRunTime() {
        if (startTime == null) return Duration.ofSeconds(0);

        LocalTime currentTime = LocalTime.now();

        Duration runtime = Duration.between(startTime, currentTime); // Calculate runtime

        return runtime;
    }

    public void sleep(int time) {
        Global.sleep(time);
    }

    public void sleep(int start, int end) {
        Global.sleep(start, end);
    }

    public boolean sleepUntil(BooleanSupplier awaitedCondition) {
        return Global.sleepUntil(awaitedCondition, 5000);
    }

    public boolean sleepUntil(BooleanSupplier awaitedCondition, int time) {
        boolean done;
        long startTime = System.currentTimeMillis();
        do {
            done = awaitedCondition.getAsBoolean();
        } while (!done && System.currentTimeMillis() - startTime < time);
        return done;
    }

    /**
     * sleeps for a specified number of game ticks
     *
     * @param ticksToWait
     * @return
     */
    public boolean sleepUntilTick(int ticksToWait) {
        int startTick = Microbot.getClient().getTickCount();
        return Global.sleepUntil(() -> Microbot.getClient().getTickCount() >= startTick + ticksToWait, ticksToWait * 600 + 2000);
    }

    /**
     * Sleeps until a specified condition is met, running an action periodically, or until a timeout is reached.
     *
     * @param awaitedCondition The condition to wait for.
     * @param action           The action to run periodically while waiting.
     * @param timeoutMillis    The maximum time to wait in milliseconds.
     * @param sleepMillis      The time to sleep between action executions in milliseconds.
     * @return true if the condition was met within the timeout, false otherwise.
     */
    public boolean sleepUntil(BooleanSupplier awaitedCondition, Runnable action, long timeoutMillis, int sleepMillis) {
        long startTime = System.nanoTime();
        long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        try {
            while (System.nanoTime() - startTime < timeoutNanos) {
                action.run();
                if (awaitedCondition.getAsBoolean()) {
                    return true;
                }
                sleep(sleepMillis);
            }
        } catch (Exception e) {
            Thread.currentThread().interrupt(); // Restore the interrupt status
        }
        return false; // Timeout reached without satisfying the condition
    }


    public boolean sleepUntil(BooleanSupplier awaitedCondition, BooleanSupplier resetCondition, int timeout) {
        final Stopwatch watch = Stopwatch.createStarted();
        while (!awaitedCondition.getAsBoolean() && watch.elapsed(TimeUnit.MILLISECONDS) < timeout) {
            sleep(100);
            if (resetCondition.getAsBoolean() && Microbot.isLoggedIn()) {
                watch.reset();
                watch.start();
            }
        }
        return awaitedCondition.getAsBoolean();
    }

    public void sleepUntilOnClientThread(BooleanSupplier awaitedCondition) {
        sleepUntilOnClientThread(awaitedCondition, 5000);
    }

    public void sleepUntilOnClientThread(BooleanSupplier awaitedCondition, int time) {
        boolean done;
        long startTime = System.currentTimeMillis();
        do {
            Microbot.status = "[ConditionalSleep] for " + time / 1000 + " seconds";
            done = Microbot.getClientThread().runOnClientThread(() -> awaitedCondition.getAsBoolean() || hasLeveledUp);
        } while (!done && System.currentTimeMillis() - startTime < time);
    }


    public void shutdown() {
        if (scheduledFuture != null && !scheduledFuture.isDone()) {
            scheduledFuture.cancel(true);
        }
        if (mainScheduledFuture != null && !mainScheduledFuture.isDone()) {
            mainScheduledFuture.cancel(true);
            ShortestPathPlugin.exit();
            if (Microbot.getClientThread().scheduledFuture != null)
                Microbot.getClientThread().scheduledFuture.cancel(true);
            initialPlayerLocation = null;
            Microbot.pauseAllScripts = false;
            Rs2Walker.disableTeleports = false;
            Microbot.getSpecialAttackConfigs().reset();
            Rs2Walker.setTarget(null);
        }
        startTime = null;
    }

    public boolean run() {
        if (startTime == null) {
            startTime = LocalTime.now();
            //init - things that have to be checked once can be added here
        }

        hasLeveledUp = false;
        //Microbot.getSpecialAttackConfigs().useSpecWeapon();

        if (Microbot.pauseAllScripts)
            return false;

        synchronized (BlockingEventManager.class) {
            if (!Microbot.getBlockingEventManager().getBlockingEvents().isEmpty()) {
                for (BlockingEvent blockingEvent : Microbot.getBlockingEventManager().getBlockingEvents()) {
                    if (blockingEvent.validate()) {
                        blockingEvent.execute();
                        return false;
                    }
                }
            }
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

    public void onWidgetLoaded(WidgetLoaded event) {
        int groupId = event.getGroupId();

        if (groupId == InterfaceID.LEVEL_UP) {
            hasLeveledUp = true;
        }
    }
}
