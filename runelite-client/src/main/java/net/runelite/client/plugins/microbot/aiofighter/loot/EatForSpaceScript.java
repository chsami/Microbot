package net.runelite.client.plugins.microbot.aiofighter.loot;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterConfig;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.concurrent.TimeUnit;

/**
 * Script that handles eating food to make inventory space.
 * This runs before banking decisions to prevent unnecessary banking trips.
 */
public class EatForSpaceScript extends Script {

    private AIOFighterConfig config;

    public boolean run(AIOFighterConfig config) {
        this.config = config;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (!config.eatFoodForSpace()) return;
                
                checkAndEatForSpace();
                
            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    /**
     * Check if we need to eat food for space and do so if needed.
     * @return true if food was eaten, false otherwise
     */
    public boolean checkAndEatForSpace() {
        if (!config.eatFoodForSpace()) {
            return false;
        }

        // Check if we have food to eat
        if (Rs2Inventory.getInventoryFood().isEmpty()) {
            return false;
        }

        // Determine threshold: use minFreeSlots if banking enabled, otherwise only when full
        int threshold = config.bank() ? config.minFreeSlots() : 0;
        
        // Check if we need to eat food for space
        if (Rs2Inventory.getEmptySlots() <= threshold) {
            Microbot.log("Eating food for space - Empty slots: " + Rs2Inventory.getEmptySlots() + " <= threshold: " + threshold);
            // Eat food to make space
            if (Rs2Player.eatAt(100)) {
                // Wait a bit for the eating action to complete
                sleep(600, 1200);
                return true;
            }
        }
        
        return false;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}