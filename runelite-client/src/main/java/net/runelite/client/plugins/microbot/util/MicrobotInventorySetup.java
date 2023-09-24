package net.runelite.client.plugins.microbot.util;

import net.runelite.client.plugins.inventorysetups.InventorySetup;
import net.runelite.client.plugins.inventorysetups.InventorySetupsItem;
import net.runelite.client.plugins.inventorysetups.InventorySetupsPlugin;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

import java.util.Objects;

public class MicrobotInventorySetup {
    public static void loadInventory(String name) {
        Rs2Bank.openBank();
        if (Rs2Bank.isOpen()) {
            InventorySetup inventorySetup = InventorySetupsPlugin.getInventorySetups().stream().filter(Objects::nonNull).filter(x -> x.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
            if (inventorySetup == null) return;
            for (int i = 0; i < inventorySetup.getInventory().size(); i++) {
                InventorySetupsItem inventorySetupsItem = inventorySetup.getInventory().get(i);
                if (Rs2Bank.inventoryItems.stream().filter(x -> x.getItemId() == inventorySetupsItem.getId()).count() ==
                        inventorySetup.getInventory().stream().filter(x -> x.getId() == inventorySetupsItem.getId()).count())
                    continue;
                Rs2Bank.withdrawFast(inventorySetupsItem.getId(), inventorySetupsItem.getQuantity());
            }
        }
    }

    public static void loadEquipment(String name) {
        Rs2Bank.openBank();
        if (Rs2Bank.isOpen()) {
            InventorySetup inventorySetup = InventorySetupsPlugin.getInventorySetups().stream().filter(Objects::nonNull).filter(x -> x.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
            if (inventorySetup == null) return;
            for (InventorySetupsItem inventorySetupsItem : inventorySetup.getEquipment()) {
                if (inventorySetupsItem.getId() == -1) continue;
                if (Rs2Inventory.hasItem(inventorySetupsItem.getId())) {
                    Rs2Bank.wearItem(inventorySetupsItem.getId());
                    continue;
                }
                if (inventorySetupsItem.getQuantity() > 1) {
                    Rs2Bank.withdrawAllAndEquipFast(inventorySetupsItem.getId());
                } else {
                    Rs2Bank.withdrawAndEquipFast(inventorySetupsItem.getId());
                }
            }
        }
    }
}
