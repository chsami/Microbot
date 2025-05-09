package net.runelite.client.plugins.microbot.bee.salamanders;

import net.runelite.api.Item;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.concurrent.TimeUnit;

public class SalamanderGroundItemLooter extends Script {
    private final String itemsToLoot = "rope,small fishing net";
    private SalamanderConfig config;
    private SalamanderScript script;

    public boolean run(SalamanderConfig config, SalamanderScript script) {
        this.config = config;
        this.script = script;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (!Microbot.isLoggedIn()) return;
                if (!this.isRunning()) return;
                if (!script.isRunning()) return;
                if (Rs2Player.isMoving()) return;
                if (Rs2Player.isAnimating()) return;
                lootRobeAndNets();
                if (Rs2Inventory.size() > 20) {
                    cleanInventory();
                }
            } catch (Exception ex) {
                System.out.println("Salamander Looter: " + ex.getMessage());
                ex.printStackTrace();
            }
        }, 0, 1800, TimeUnit.MILLISECONDS);
        return true;
    }

    private void cleanInventory() {
        for (Rs2ItemModel item : Rs2Inventory.items()) {
            if (item.getId() == ItemID.BLACK_SALAMANDER || item.getId() == ItemID.GREEN_SALAMANDER || item.getId() == ItemID.ORANGE_SALAMANDER || item.getId() == ItemID.RED_SALAMANDER || item.getId() == ItemID.IMMATURE_MOUNTAIN_SALAMANDER) {
                Rs2Inventory.interact(item, "Release");
                sleep(150, 350);
            }
        }
    }

    private void lootRobeAndNets() {
        LootingParameters valueParams = new LootingParameters(
                15,
                1,
                1,
                1,
                false,
                true,
                itemsToLoot.trim().split(",")
        );
        if (Rs2GroundItem.lootItemsBasedOnNames(valueParams)) {
            Microbot.pauseAllScripts = false;
        }
    }
}
