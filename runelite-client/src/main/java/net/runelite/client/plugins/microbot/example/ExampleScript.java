package net.runelite.client.plugins.microbot.example;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;

import java.util.List;

public class ExampleScript extends Script {

    // ✅ Must be public and static
    public static void printLockedItems() {
        List<Rs2ItemModel> items = Rs2Inventory.items();
        if (items.isEmpty()) {
            Microbot.log("Inventory is empty.");
            return;
        }

        Microbot.log("=== Locked Items ===");
        for (Rs2ItemModel item : items) {
            if (Rs2Bank.isLockedSlot(item.getSlot())) {
                Microbot.log("Slot %02d: %s (ID: %d)", item.getSlot(), item.getName(), item.getId());
            }
        }
    }

    @Override
    public boolean run(ExampleConfig config) {
        Microbot.enableAutoRunOn = false;
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
