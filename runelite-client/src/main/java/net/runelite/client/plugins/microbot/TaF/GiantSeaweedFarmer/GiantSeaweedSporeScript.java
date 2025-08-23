package net.runelite.client.plugins.microbot.TaF.GiantSeaweedFarmer;

import net.runelite.api.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.concurrent.TimeUnit;

public class GiantSeaweedSporeScript extends Script {
    private GiantSeaweedFarmerConfig config;
    
    public boolean run(GiantSeaweedFarmerConfig config) {
        this.config = config;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run() || !Microbot.isLoggedIn()) return;
                if (!config.lootSeaweedSpores()) return;
                
                // Check for seaweed spores - but respect critical farming operations
                if (Rs2GroundItem.exists(ItemID.SEAWEED_SPORE, 15) && 
                    !GiantSeaweedFarmerScript.inCriticalSection) {
                    // Pause all other scripts while we loot
                    Microbot.pauseAllScripts.set(true);
                    try {
                        lootAllSpores();
                    } finally {
                        // Always unpause when done
                        Microbot.pauseAllScripts.set(false);
                    }
                }
            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
                // Ensure we unpause if there's an error
                Microbot.pauseAllScripts.set(false);
            }
        }, 0, 300, TimeUnit.MILLISECONDS); // Fast checking interval for quick spore detection
        return true;
    }
    
    private void lootAllSpores() {
        while (Rs2GroundItem.exists(ItemID.SEAWEED_SPORE, 15) && this.isRunning()) {
            Microbot.log("Seaweed spore detected - looting");
            boolean looted = Rs2GroundItem.loot(ItemID.SEAWEED_SPORE, 15);
            if (looted) {
                // Wait for movement to start and complete
                sleepUntil(Rs2Player::isMoving, 2000);
                if (Rs2Player.isMoving()) {
                    sleepUntil(() -> !Rs2Player.isMoving(), 5000);
                }
                
                // Wait for inventory change
                Rs2Inventory.waitForInventoryChanges(2000);
                sleep(300, 500);
            } else {
                // If loot failed, try again after a small delay
                sleep(200, 300);
            }
        }
        Microbot.log("Finished looting seaweed spores");
    }
    
    @Override
    public void shutdown() {
        // Ensure we unpause when shutting down
        Microbot.pauseAllScripts.set(false);
        super.shutdown();
    }
}