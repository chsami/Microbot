package net.runelite.client.plugins.microbot.example;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.tileitem.Rs2TileItemCache;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.models.TileObjectType;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.api.player.Rs2PlayerCache;
import net.runelite.client.plugins.microbot.util.player.Rs2PlayerModel;
import net.runelite.client.plugins.microbot.util.reachable.Rs2Reachable;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
    private static final WorldPoint HOPPER_DEPOSIT_DOWN = new WorldPoint(3748, 5672, 0);

    public boolean run() {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;

                if (Rs2Inventory.isFull()) {
                    Rs2Inventory.dropAll("Logs");
                    return;
                }

                if (Rs2Player.isAnimating()) return;

                var tree = Microbot.getRs2TileObjectCache().query()
                        .withName("Tree")
                        .nearest();

                if (tree != null) {
                    tree.click("Chop down");
                    sleepUntil(Rs2Player::isAnimating, 3000);
                }

            } catch (Exception ex) {
                log.error("Error in example script", ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);

        return true;
    }

    private void dropJunk() {
        var junkItems = new ArrayList<String>();
        junkItems.add("gold ring");
        junkItems.add("sapphire ring");
        junkItems.add("emerald ring");
        junkItems.add("ruby ring");
        junkItems.add("diamond ring");
        junkItems.add("casket");
        junkItems.add("oyster pearl");
        junkItems.add("oyster pearls");
        junkItems.add("teak logs");
        junkItems.add("steel nails");
        junkItems.add("mithril nails");
        junkItems.add("giant seaweed");
        junkItems.add("mithril cannonball");
        junkItems.add("adamant cannonball");
        junkItems.add("elkhorn frag");
        junkItems.add("plank");
        junkItems.add("oak plank");
        junkItems.add("hemp seed");
        junkItems.add("flax seed");
        junkItems.add("ruby bracelet");
        junkItems.add("emerald bracelet");
        junkItems.add("mithril scimitar");
        junkItems.add("mahogany repair kit");
        junkItems.add("teak repair kit");
        junkItems.add("rum");
        junkItems.add("diamond bracelet");
        junkItems.add("sapphire ring");
        junkItems.add("emerald ring");
        junkItems.add("emerald bracelet");
        Rs2Inventory.dropAll( junkItems.toArray(new String[0]));
    }
}
