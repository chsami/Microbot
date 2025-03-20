package net.runelite.client.plugins.microbot.dailytasks;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
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
    private DailyTasksConfig config;

    private final List<DailyTask> tasksToComplete = new ArrayList<>();
    private DailyTask currentTask = null;
    private boolean initialized = false;

    @Inject
    public DailyTasksScript(DailyTasksConfig config) {
        this.config = config;
    }


    @Override
    public boolean run() {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!Microbot.isLoggedIn()) return;

            if (!initialized) {
                initialized = true;
                initializeTasks();
                inventorySetup = new Rs2InventorySetup(config.inventorySetup(), mainScheduledFuture);

                if (tasksToComplete.isEmpty()) {
                    Microbot.log("No daily tasks available to complete");
                    shutdown();
                    return;
                }

                Microbot.log("Found " + tasksToComplete.size() + " daily tasks to complete");
            }

            if (!super.run()) return;

            if (currentTask == null) {
                if (tasksToComplete.isEmpty()) {
                    DailyTasksPlugin.currentState = "Finished";
                    if (config.goToBank()) {
                        BankLocation bankLocation = Rs2Bank.getNearestBank();
                        boolean arrived = Rs2Walker.walkTo(bankLocation.getWorldPoint());
                        sleepUntil(() -> arrived, 20000);
                    }
                    shutdown();
                    return;
                }
                currentTask = tasksToComplete.remove(0);
                DailyTasksPlugin.currentState = currentTask.getName();
            }

            System.out.println("Current task: " + currentTask.getName());
            if (Rs2Player.distanceTo(currentTask.getLocation()) > 5) {
                DailyTasksPlugin.currentState = "Walking to: " + currentTask.getName();
                boolean arrived = Rs2Walker.walkTo(currentTask.getLocation(), 5);
                sleepUntil(() -> arrived);
            }

            DailyTasksPlugin.currentState = "Executing: " + currentTask.getName();
            currentTask.execute();
            currentTask = null;

        }, 0, 1000, TimeUnit.MILLISECONDS);

        return true;
    }

    private void initializeTasks() {
        clientThread.runOnClientThread(() -> {
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