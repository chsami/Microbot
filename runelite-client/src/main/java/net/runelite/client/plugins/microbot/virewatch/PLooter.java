package net.runelite.client.plugins.microbot.virewatch;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class PLooter extends Script {
    public boolean run(PVirewatchKillerConfig config) {
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {

            try{
                if (!super.run()) return;

                if (Rs2Inventory.isFull() || Rs2Inventory.getEmptySlots() <= 1) {
                    bank(config);
                    return;
                }
                System.out.println("toggleLootItems: " + config.toggleLootItems());
                if (!config.toggleLootItems()) return;

                loot(config);
            }catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 200, TimeUnit.MILLISECONDS);
        return true;
    }

    private void bank(PVirewatchKillerConfig config) {
        WorldPoint currentLocation = Rs2Player.getWorldLocation();

        // Equip disguise
        Rs2Inventory.equip("Vyre noble legs");
        Rs2Inventory.equip("Vyre noble shoes");
        Rs2Inventory.equip("Vyre noble top");

        BankLocation nearestBank = Rs2Bank.getNearestBank();
        Rs2Walker.walkTo(nearestBank.getWorldPoint());

        Rs2Bank.openBank();
        if (!Rs2Bank.isOpen()) return;
        Rs2Bank.depositAll("Blood shard");
        Rs2Bank.depositAll("Runite ore");
        Rs2Bank.depositAll("Runite bar");
        Rs2Bank.depositAll("Vampyre dust");
        Rs2Bank.depositAll("Dragonstone");
        Rs2Bank.depositAll("Ranarr seed");
        Rs2Bank.depositAll("Tooth half of key");
        Rs2Bank.depositAll("Dragon med helm");
        Rs2Bank.depositAll("Dragonstone bolt tips");

        Rs2Inventory.waitForInventoryChanges(1800);
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen());

        Rs2Walker.walkTo(currentLocation);

        // Re-equip combat gear from config
        Rs2Inventory.equip(config.top());
        Rs2Inventory.equip(config.legs());
        Rs2Inventory.equip(config.boots());
    }

    private void loot(PVirewatchKillerConfig config) {
        var nearbyItems = Rs2GroundItem.getAll(7);
        var itemsToPickup = Arrays.stream(nearbyItems)
                .filter(item -> config.customLootItems().contains(item.getItem().getName()))
                .collect(Collectors.toList());

        if (config.teleGrabLoot()) {
            for (var item : itemsToPickup) {
                int distance = Microbot.getClient().getLocalPlayer().getWorldLocation().distanceTo(item.getTile().getWorldLocation());
                if (distance > 2) {
                    Rs2Magic.cast(MagicAction.TELEKINETIC_GRAB);
                }
                Rs2GroundItem.interact(item);
                sleepUntil(() -> Rs2Inventory.waitForInventoryChanges(1800));
                Rs2Tab.switchToInventoryTab();
            }
        } else {
            for (var item : itemsToPickup) {
                Rs2GroundItem.interact(item);
                sleepUntil(() -> Rs2Inventory.waitForInventoryChanges(1800));
                Rs2Tab.switchToInventoryTab();
            }
        }
    }

    public void shutdown() {
        super.shutdown();
    }
}
