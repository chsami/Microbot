package net.runelite.client.plugins.microbot.example;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.tileitem.Rs2TileItemCache;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.api.player.Rs2PlayerCache;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

/**
 * Performance test script for measuring GameObject composition retrieval speed.
 * <p>
 * This script runs every 5 seconds and performs the following:
 * - Gets all GameObjects in the scene
 * - Retrieves the ObjectComposition for each GameObject
 * - Measures and logs the total time taken
 * - Reports average time per object
 * <p>
 * Useful for performance profiling and optimization testing.
 */
@Slf4j
public class ExampleScript extends Script {

    @Inject
    Rs2TileItemCache rs2TileItemCache;
    @Inject
    Rs2TileObjectCache rs2TileObjectCache;
    @Inject
    Rs2PlayerCache rs2PlayerCache;
    @Inject
    Rs2NpcCache rs2NpcCache;
    /**
     * Main entry point for the performance test script.
     */
    public boolean run() {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;

                long startTime = System.currentTimeMillis();
                var groundItems = rs2TileItemCache.query().toList();
                var objects = rs2TileObjectCache.query().toList();
                var rs2Players = rs2PlayerCache.query().toList();
                var rs2Npcs = rs2NpcCache.query().toList();

                //groundItems.get(0).click("Take");
                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("fetched " + rs2Players.size() + " players and " + rs2Npcs.size() + " npcs.");
                System.out.println("fetched " + objects.size() + " objects.");
                System.out.println("fetched " + groundItems.size() + " ground items.");
                System.out.println("Player location: " + Rs2Player.getWorldLocation());
                System.out.println("fetched " + groundItems.size() + " ground items.");
                System.out.println("all in time: " + totalTime + " ms");
                /*var tree = rs2TileObjectCache.query().within(Rs2Player.getWorldLocation(), 20).withName("Tree");

                tree.click();

                System.out.println(tree.getId());
                System.out.println(tree.getName());
                */

            } catch (Exception ex) {
                log.error("Error in performance test loop", ex);
            }
        }, 0, 5000, TimeUnit.MILLISECONDS);

        return true;
    }
}
