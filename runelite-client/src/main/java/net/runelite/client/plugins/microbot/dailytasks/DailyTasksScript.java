package net.runelite.client.plugins.microbot.dailytasks;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
public class DailyTasksScript extends Script {


    @Inject
    private ClientThread clientThread;

    @Inject
    private DailyTasksPlugin plugin;
    @Inject
    private DailyTasksConfig config;

    private final List<DailyTask> tasksToComplete = new ArrayList<>();
    private DailyTask currentTask = null;
    private boolean initialized = false;

    @Inject
    public DailyTasksScript(DailyTasksPlugin plugin, DailyTasksConfig config) {
        this.plugin = plugin;
        this.config = config;
    }


    @Override
    public boolean run() {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!Microbot.isLoggedIn()) return;

            if (!initialized) {
                initialized = true;
                initializeTasks();
                if (tasksToComplete.isEmpty()) {
                    Microbot.log("No daily tasks available to complete");
                    Microbot.stopPlugin(plugin);
                    return;
                }

                var inventorySetup = new Rs2InventorySetup(config.inventorySetup(), mainScheduledFuture);
                if (!inventorySetup.doesInventoryMatch() || !inventorySetup.doesEquipmentMatch()) {
                    Rs2Walker.walkTo(Rs2Bank.getNearestBank().getWorldPoint(), 20);
                    if (!inventorySetup.loadEquipment() || !inventorySetup.loadInventory()) {
                        Microbot.log("Failed to load inventory setup");
                        Microbot.stopPlugin(plugin);
                        return;
                    }
                    Rs2Bank.closeBank();
                }

                Microbot.log("Found " + tasksToComplete.size() + " daily tasks to complete");
            }

            if (!super.run()) return;

            if (currentTask == null) {
                if (tasksToComplete.isEmpty()) {
                    DailyTasksPlugin.currentState = "Finished";
                    if (config.goToBank()) {
                        BankLocation bankLocation = Rs2Bank.getNearestBank();
                        Rs2Walker.walkTo(bankLocation.getWorldPoint());
                    }
                    Microbot.stopPlugin(plugin);
                    return;
                }
                currentTask = tasksToComplete.remove(0);
                DailyTasksPlugin.currentState = currentTask.getName();
            }

            System.out.println("Current task: " + currentTask.getName());
            if (Rs2Player.distanceTo(currentTask.getLocation()) > 5) {
                DailyTasksPlugin.currentState = "Walking to: " + currentTask.getName();
                Rs2Walker.walkTo(currentTask.getLocation(), 5);
            }

            DailyTasksPlugin.currentState = "Executing: " + currentTask.getName();
            currentTask.execute();
            currentTask = null;

        }, 0, 1000, TimeUnit.MILLISECONDS);

        return true;
    }

    private void initializeTasks() {
        clientThread.runOnClientThreadOptional(() -> {
            for (DailyTask task : DailyTask.values()) {
                if (task.isEnabled(config) && task.isAvailable()) {
                    tasksToComplete.add(task);
                }
            }
            return true;
        });
    }


    @Override
    public void shutdown() {
        initialized = false;
        currentTask = null;
        tasksToComplete.clear();
        super.shutdown();
    }
}