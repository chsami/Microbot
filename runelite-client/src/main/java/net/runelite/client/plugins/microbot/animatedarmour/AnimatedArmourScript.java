package net.runelite.client.plugins.microbot.animatedarmour;

import net.runelite.api.GameObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.concurrent.TimeUnit;


public class AnimatedArmourScript extends Script {

    public static boolean test = false;
    public boolean run(AnimatedArmourConfig config) {
        Microbot.enableAutoRunOn = false;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();

                boolean hasArmorPieces = Rs2Inventory.contains(item -> item.getName().contains("platebody")) &&
                        Rs2Inventory.contains(item -> item.getName().contains("platelegs")) &&
                        Rs2Inventory.contains(item -> item.getName().contains("full helm"));
                if(hasArmorPieces) {
                    animateArmor();
                } else {
                    Rs2Player.eatAt(Rs2Random.randomGaussian(30,3));
                    loot();
                }

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    public void animateArmor() {
        WorldPoint armorStandLocation = new WorldPoint(2851,3536,0);
        GameObject armorStand = Rs2GameObject.getGameObject(armorStandLocation);
        if(armorStand != null) {
            Rs2GameObject.interact(armorStand);
            Rs2Player.waitForAnimation();
        }

    }


    public void loot(){
        LootingParameters valueParams = new LootingParameters(
                5,
                1,
                1,
                1,
                false,
                true,
                "platebody,platelegs,full helm,Warrior guild token".split(",")
        );
        if (Rs2GroundItem.lootItemsBasedOnNames(valueParams)) {
            Microbot.pauseAllScripts = false;
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}