package net.runelite.client.plugins.microbot;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Slf4j
public abstract class Script extends Global implements IScript  {

    protected ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(10);
    protected ScheduledFuture<?> scheduledFuture;
    protected ScheduledFuture<?> preRequisiteFuture;
    protected ScheduledFuture<?> mainScheduledFuture;
    public static boolean hasLeveledUp = false;
    public static boolean useStaminaPotsIfNeeded = true;

    public boolean isRunning() {
        return mainScheduledFuture != null && !mainScheduledFuture.isDone();
    }

    @Getter
    protected static WorldPoint initialPlayerLocation;

    public LocalTime startTime;

    @Getter
    protected List<ScriptItem> requiredItems = new ArrayList<>();

    private static boolean requiredItemsHandled = false;

    /**
     * Get the total runtime of the script
     *
     * @return the total runtime of the script
     */
    public Duration getRunTime() {
        if (startTime == null) return Duration.ofSeconds(0);

        LocalTime currentTime = LocalTime.now();

        return Duration.between(startTime, currentTime);
    }

    public void shutdown() {
        if (mainScheduledFuture != null && !mainScheduledFuture.isDone()) {
            mainScheduledFuture.cancel(true);
        }
        if (scheduledFuture != null && !scheduledFuture.isDone()) {
            scheduledFuture.cancel(true);
        }
        if (preRequisiteFuture != null && !preRequisiteFuture.isDone()) {
            preRequisiteFuture.cancel(true);
        }
        scheduledFuture = null;
        preRequisiteFuture = null;

        ShortestPathPlugin.exit();
        if (Microbot.getClientThread().scheduledFuture != null)
            Microbot.getClientThread().scheduledFuture.cancel(true);
        initialPlayerLocation = null;
        Microbot.pauseAllScripts = false;
        Rs2Walker.disableTeleports = false;
        Microbot.getSpecialAttackConfigs().reset();
        Rs2Walker.setTarget(null);
        startTime = null;
        requiredItemsHandled = false;
    }

    /**
     * Handles retrieving required items from bank and equipping required equipment
     * @return true if all required items and equipment are handled, false otherwise
     */
    public boolean handlePrerequisites() {
        if (requiredItemsHandled) {
            return true;
        }

        // If no required items or equipment, mark as handled and continue
        if (requiredItems.isEmpty()) {
            requiredItemsHandled = true;
            return true;
        }

        // Create lists of missing items and equipment
        List<ScriptItem> missingItems = new ArrayList<>();

        // Check if we have all required items in inventory
        for (ScriptItem item : requiredItems) {
            if(item.isEquipped() && ! item.isWearing()) {
                missingItems.add(item);
            } else if (!item.hasInInventory()) {
                missingItems.add(item);
            }
        }

        // If nothing is missing, we're done
        if (missingItems.isEmpty()) {
            requiredItemsHandled = true;
            return true;
        }

        // We need to go to the bank
        if (!Rs2Bank.openBank()) {
            BankLocation bankLocation = Rs2Bank.getNearestBank();
            boolean arrived = Rs2Walker.walkTo(bankLocation.getWorldPoint());
            sleepUntil(() -> arrived);
            Rs2Bank.openBank();
        }

        // Only proceed if bank is actually open
        if (Rs2Bank.isOpen()) {

            // We could check for required items here, but lets just do this for now.
            Rs2Bank.depositAll();
            Rs2Bank.depositEquipment();

            if (requiredItems.stream().anyMatch(ScriptItem::isEquipped)) {
                Rs2Bank.depositEquipment();
            }

            // Withdraw required items
            for (ScriptItem item : requiredItems) {
                item.withdraw();
            }

            // Close bank
            Rs2Bank.closeBank();
            sleepUntil(() -> !Rs2Bank.isOpen());
        }

        // Equip required equipment
        for (ScriptItem item : requiredItems) {
            if (item.isEquipped()) {
                item.wear();
            }
        }

        // Verify all items and equipment are handled
        StringBuilder missingItemsMessage = new StringBuilder();
        boolean allItemsObtained = true;

        for (ScriptItem item : requiredItems) {
            if ((item.isEquipped() && !item.isWearing()) || (!item.isEquipped() && !item.hasInInventory())) {
                String itemName = item.getName() != null ? item.getName() : "ID:" + item.getId();
                missingItemsMessage.append("Missing required item: ").append(itemName)
                        .append(" x").append(item.getQuantity()).append("\n");
                allItemsObtained = false;
            }
        }

        // If any items are still missing, show message and shutdown
        if (!allItemsObtained) {
            String errorMessage = "Cannot continue script execution.\n" + missingItemsMessage.toString() +
                    "Please ensure you have all required items in your bank.";
            Microbot.showMessage(errorMessage);
            log.error(errorMessage);
            shutdown();
            return false;
        }

        // All items are present
        requiredItemsHandled = true;
        return true;
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

            // Handle required items and equipment before proceeding with script
            if (!requiredItemsHandled && preRequisiteFuture == null) {
                preRequisiteFuture = scheduledExecutorService.schedule(this::handlePrerequisites, 0, TimeUnit.MILLISECONDS);
            }

            if (!hasRunEnergy && Microbot.useStaminaPotsIfNeeded && Rs2Player.isMoving()) {
                Rs2Inventory.useRestoreEnergyItem();
            }
        }

        return preRequisiteFuture == null || preRequisiteFuture.isDone();
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
