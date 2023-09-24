package net.runelite.client.plugins.nateplugins.nateminer.nateminer;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

import java.util.concurrent.TimeUnit;


public class MiningScript extends Script {

    public static double version = 1.2;

    public boolean run(MiningConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!super.run()) return;

            try {
                if (Microbot.isMoving() || Microbot.isAnimating() || Microbot.pauseAllScripts) return;
                if (Rs2Inventory.isFull()) {
                    if (config.hasPickaxeInventory()) {
                        Rs2Inventory.dropAllStartingFrom(1);
                    } else {
                        Rs2Inventory.dropAll();
                    }
                    return;
                }
                Rs2GameObject.interact(config.ORE().getName());
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }
}
