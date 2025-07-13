package net.runelite.client.plugins.microbot.TaF.stonechests;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;

public class StoneChestThieverScript extends Script {
    private static void handleThieving() {
        if (Rs2Player.isAnimating()) return;
        // Chest ID
        Rs2GameObject.interact(34429, "Picklock");
        Rs2Player.waitForAnimation(600);
        Rs2Player.waitForXpDrop(Skill.THIEVING);
    }

    private static void handleFullInventory(StoneChestThieverConfig config) {
        // Xerician fabric
        Rs2Inventory.dropAll(13383);
        // Low value seeds
        Rs2Inventory.dropAll("Jangerberry seed", "Marrentill seed", "Strawberry seed",
                "Tarromin seed", "Limpwurt seed", "Harralander seed", "Belladonna seed",
                "Wildblood seed", "Mushroom spore", "Cactus seed", "Poison ivy seed",
                "Whiteberry seed", "Potato cactus seed");
        if (config.cutGems() && Rs2Inventory.hasItem("uncut") && Rs2Inventory.hasItem("Chisel")) {
            Rs2Inventory.interact("Chisel", "use");
            Rs2Inventory.interact("uncut", "use");
            sleep(800, 1400);
            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
            sleep(2000, 2400);
        }
        Rs2Inventory.dropAll("Sapphire", "Ruby");
    }

    public boolean run(StoneChestThieverConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                // Location next to the chest
                var chestLocation = new WorldPoint(1300, 10087, 0);

                if (Rs2Player.distanceTo(chestLocation) > 5) {
                    Rs2Walker.walkTo(chestLocation);
                    return;
                }
                final int poison = Microbot.getClient().getVarpValue(VarPlayerID.POISON);
                if (poison > 0) {
                    Rs2Player.drinkAntiPoisonPotion();
                }
                Rs2Player.eatAt(50);
                if (Rs2Inventory.getInventoryFood().isEmpty()) {
                    Microbot.log("No food in inventory, stopping script.");
                    shutdown();
                    return;
                }

                if (Rs2Inventory.isFull()) {
                    handleFullInventory(config);
                    return;
                }
                handleThieving();

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}