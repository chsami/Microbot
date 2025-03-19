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
        Global.sleep().until(time).execute();
    }

    public void sleep(int start, int end) {
        Global.sleep().between(start, end).execute();
    }

    public boolean sleepUntil(BooleanSupplier awaitedCondition) {
        return Global.sleep().until(awaitedCondition).execute();
    }

    public boolean sleepUntil(BooleanSupplier awaitedCondition, int time) {
        return Global.sleep().until(awaitedCondition).timeout(time).execute();
    }

    /**
     * sleeps for a specified number of game ticks
     *
     * @param ticksToWait
     * @return
     */
    public boolean sleepUntilTick(int ticksToWait) {
        return Global.sleep().ticks(ticksToWait).execute();
    }

    public boolean sleepUntil(BooleanSupplier awaitedCondition, Runnable action, long timeoutMillis, int sleepMillis) {
        return Global.sleep().until(awaitedCondition).withAction(action).timeout(timeoutMillis).interval(sleepMillis).execute();
    }


    public boolean sleepUntil(BooleanSupplier awaitedCondition, BooleanSupplier resetCondition, int timeout) {
        return Global.sleep().until(awaitedCondition).withReset(resetCondition).timeout(timeout).execute();
    }

    public void sleepUntilOnClientThread(BooleanSupplier awaitedCondition) {
        Global.sleep().until(awaitedCondition).onClientThread().execute();
    }

    public void sleepUntilOnClientThread(BooleanSupplier awaitedCondition, int time) {
        Global.sleep().until(awaitedCondition).onClientThread().timeout(time).execute();
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

        if (Microbot.pauseAllScripts)
            return false;

        //Avoid executing any blocking events if the player hasn't finished Tutorial Island
        if (!Rs2Player.isInTutorialIsland())
            return true;

        // Synchronizing on BlockingEventManager.class to ensure thread safety
        // This prevents multiple threads from modifying the blocking event list simultaneously.
        synchronized (BlockingEventManager.class) {
            // Check if there are any blocking events registered in the BlockingEventManager
            if (!Microbot.getBlockingEventManager().getBlockingEvents().isEmpty()) {
                // Iterate through each blocking event to check if any should be executed
                for (BlockingEvent blockingEvent : Microbot.getBlockingEventManager().getBlockingEvents()) {
                    // If the event's validation condition is met, it means we need to execute it
                    if (blockingEvent.validate()) {
                        // Execute the blocking event to resolve the issue
                        blockingEvent.execute();
                        // Return false to indicate that an event was executed and further processing should stop
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
