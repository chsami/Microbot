package net.runelite.client.plugins.microbot.TaF.DeadFallTrapHunter;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.concurrent.TimeUnit;

public class DeadFallTrapInventoryHandlerScript extends Script {
    private DeadFallTrapHunterConfig config;
    private DeadFallTrapHunterScript script;

    public boolean run(DeadFallTrapHunterConfig config, DeadFallTrapHunterScript script) {
        this.config = config;
        this.script = script;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (!Microbot.isLoggedIn()) return;
                if (!this.isRunning()) return;
                if (!script.isRunning()) return;
                if (!script.forceDrop) {
                    if (Rs2Player.isMoving()) return;
                    if (Rs2Player.isAnimating()) return;
                    if (Rs2Inventory.count() > Rs2Random.between(8, 28)) {
                        cleanInventory(config);
                    }
                    return;
                }
                cleanInventory(config);
            } catch (Exception ex) {
                System.out.println("DeadFallTrapInventoryHandlerScript: " + ex.getMessage());
                ex.printStackTrace();
            }
        }, 0, 5000, TimeUnit.MILLISECONDS);
        return true;
    }

    private void cleanInventory(DeadFallTrapHunterConfig config) {
        Rs2Inventory.items().forEachOrdered(item -> {
            if (config.deadFallTrapHunting().getItemsToDrop().contains(item.getId())) {
                Rs2Inventory.interact(item, "Drop");
                sleep(600, 1200);
            }
        });
        script.forceDrop = false;
    }
}
