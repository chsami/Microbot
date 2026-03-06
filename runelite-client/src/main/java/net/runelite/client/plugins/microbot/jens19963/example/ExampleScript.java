package net.runelite.client.plugins.microbot.jens19963.example;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.player.Rs2PlayerCache;
import net.runelite.client.plugins.microbot.api.tileitem.Rs2TileItemCache;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2PlayerModel;

import javax.inject.Inject;
import java.util.ArrayList;
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
    private static final WorldPoint HOPPER_DEPOSIT_DOWN = new WorldPoint(3748, 5672, 0);

    public boolean run() {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;

/*
                if (Microbot.getClient().getTopLevelWorldView().getScene().isInstance()) {
                    LocalPoint l = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), Microbot.getClient().getLocalPlayer().getWorldLocation());
                    System.out.println("was here");
                     WorldPoint.fromLocalInstance(Microbot.getClient(), l);
                } else {
                    System.out.println("was here lol");
                    // this needs to ran on client threaad if we are on the sea
                   var a =  Microbot.getClient().getLocalPlayer().getWorldLocation();
                    System.out.println(a);
                }*/

                var shipwreck = rs2TileObjectCache.query()
                        .where(x -> x.getName() != null && x.getName().toLowerCase().contains("shipwreck"))
                        .within(5)
                        .nearestOnClientThread();
                var player = new Rs2PlayerModel();

                var isInvFull = Rs2Inventory.count() >= Rs2Random.between(24, 28);
                if (isInvFull && Rs2Inventory.count("salvage") > 0 && player.getAnimation() == -1) {
                    // Rs2Inventory.dropAll("large salvage");
                    rs2TileObjectCache.query()
                            .fromWorldView()
                            .where(x -> x.getName() != null && x.getName().equalsIgnoreCase("salvaging station"))
                            .where(x -> x.getWorldView().getId() == new Rs2PlayerModel().getWorldView().getId())
                            .nearestOnClientThread()
                            .click();
                    sleepUntil(() -> Rs2Inventory.count("salvage") == 0, 60000);
                } else if (isInvFull) {
                    dropJunk();
                } else {
                    if (player.getAnimation() != -1) {
                        log.info("Currently salvaging, waiting...");
                        sleep(5000, 10000);
                        return;
                    }

                    if (shipwreck == null) {
                        log.info("No shipwreck found nearby");
                        sleep(5000);
                        dropJunk();
                        return;
                    }

                    rs2TileObjectCache.query().fromWorldView().where(x -> x.getName() != null &&  x.getName().toLowerCase().contains("salvaging hook")).nearestOnClientThread().click("Deploy");
                    sleepUntil(() -> player.getAnimation() != -1, 5000);

                }

            } catch (Exception ex) {
                log.error("Error in performance test loop", ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);

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
