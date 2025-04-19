package net.runelite.client.plugins.microbot.qualityoflife.scripts;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.qualityoflife.QoLConfig;
import net.runelite.client.plugins.microbot.util.inventory.InteractOrder;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class AutoItemDropperScript extends Script {

    public boolean run(QoLConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (config.autoDrop()) {
                    handleAutoDropItems(config);
                }
            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    public void shutdown() {
        super.shutdown();
    }

    private void handleAutoDropItems(QoLConfig config) {
        if (Rs2Inventory.isFull()) {
            List<String> itemsToDrop = Arrays.asList(config.autoDropItems().toLowerCase().trim().split(","));
            InteractOrder dropOrder = InteractOrder.RANDOM;
            if(config.excludeItems()) {
                Rs2Inventory.dropAllExcept(x -> x.getName() != null && !itemsToDrop.contains(x.getName().toLowerCase()));
            }
            else
                Rs2Inventory.dropAll(x -> x.getName() != null && itemsToDrop.contains(x.getName().toLowerCase()), dropOrder);
        }
    }
}
