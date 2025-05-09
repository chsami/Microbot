package net.runelite.client.plugins.microbot.maxxin;

import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

public class MXUtil {
    public static void switchInventoryTabIfNeeded() {
        if( Rs2Tab.getCurrentTab() != InterfaceTab.INVENTORY ) {
            Rs2Tab.switchToInventoryTab();
            sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.INVENTORY);
        }
    }

    public static void closeWorldMapIfNeeded() {
        var worldMapOpen = Rs2Widget.findWidget("Game features") != null;
        if( worldMapOpen ) {
            var exitButton = Rs2Widget.findWidget(539, null);
            if( exitButton != null ) {
                Rs2Widget.clickWidget(exitButton);
            }
        }
    }

    public static void handlePouchOutOfSync(boolean hasEmptySlots, Rs2ItemModel colossalPouch) {
        if( Rs2Bank.isOpen() && !hasEmptySlots && !Rs2Inventory.allPouchesFull() ) {
            if( Rs2Inventory.anyPouchUnknown() ) {
                Rs2Inventory.checkPouches();
                Rs2Inventory.waitForInventoryChanges(200);
            }
            var emptySlots = Rs2Inventory.getEmptySlots();
            Rs2Inventory.interact(colossalPouch, "Fill");
            var nextEmptySlots = Rs2Inventory.getEmptySlots();
            // Handle edge case where pouch goes out-of-sync
            if( !Rs2Inventory.waitForInventoryChanges(400) || emptySlots == nextEmptySlots ) {
                Rs2Bank.closeBank();
                sleep(800);
                Rs2Inventory.interact(colossalPouch, "Check");
                sleep(800);
            }
        }
    }
}
