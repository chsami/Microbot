package net.runelite.client.plugins.microbot.util.item;

import net.runelite.api.ItemID;
import net.runelite.api.Varbits;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.itemcharges.ItemChargeConfig;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.settings.Rs2Settings;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

public class Rs2ExplorersRing {

    // Array of Explorer's Rings (1-4) item IDs
    private static int[] explorerRings = new int[]{
            ItemID.EXPLORERS_RING_1,
            ItemID.EXPLORERS_RING_2,
            ItemID.EXPLORERS_RING_3,
            ItemID.EXPLORERS_RING_4
    };

    /**
     * Retrieves the total number of charges for the Explorer's Ring from the configuration.
     * If the configuration exists, it is migrated to the RSProfile configuration.
     *
     * @param key The configuration key for the Explorer's Ring charges.
     * @return The total charges available or -1 if not found.
     */
    private static int getTotalChargesExplorersRing(String key) {
        Integer charges = Microbot.getConfigManager().getConfiguration(ItemChargeConfig.GROUP, key, Integer.class);
        if (charges != null) {
            // Migrate configuration to RSProfile
            Microbot.getConfigManager().unsetConfiguration(ItemChargeConfig.GROUP, key);
            Microbot.getConfigManager().setRSProfileConfiguration(ItemChargeConfig.GROUP, key, charges);
            return charges;
        }

        // Retrieve charges from RSProfile configuration
        charges = Microbot.getConfigManager().getRSProfileConfiguration(ItemChargeConfig.GROUP, key, Integer.class);
        return charges == null ? -1 : charges;
    }

    /**
     * Interacts with a specific item in the Explorer's Ring inventory.
     *
     * @param item The item to interact with.
     * @return True if the interaction was successful, false otherwise.
     */
    private static boolean interact(Rs2ItemModel item) {
        boolean didInteract = false;

        // Retrieve total charges for the Explorer's Ring
        int totalCharges = getTotalChargesExplorersRing(ItemChargeConfig.KEY_EXPLORERS_RING);
        if (totalCharges > 0) {
            // Ensure the Alchemy function is active
            if (!isExplorersRingInventoryVisible()) {
                Rs2Inventory.interact(explorerRings, "Functions");
                sleepUntil(Rs2Dialogue::isInDialogue);
                Rs2Keyboard.keyPress('2');
                sleepUntil(Rs2ExplorersRing::isExplorersRingInventoryVisible);
            }

            // Locate the item in the Explorer's Ring inventory and interact with it
            Widget explorersRingInventory = Rs2Widget.getWidget(ComponentID.EXPLORERS_RING_INVENTORY);
            if (explorersRingInventory == null) return false;
            for (Widget explorersRingInventoryItem : explorersRingInventory.getDynamicChildren()) {
                if (explorersRingInventoryItem == null) continue;
                if (explorersRingInventoryItem.getItemId() == item.getId()) {
                    didInteract = true;
                    Rs2Widget.clickWidget(explorersRingInventoryItem);
                    if (item.getHaPrice() > Rs2Settings.getMinimumItemValueAlchemyWarning()) {
                        sleepUntil(() -> Rs2Widget.hasWidget("Proceed to cast High Alchemy on it"));
                        if (Rs2Widget.hasWidget("Proceed to cast High Alchemy on it")) {
                            Rs2Keyboard.keyPress('1');
                        }
                    }
                    Rs2Player.waitForAnimation();
                }
            }
        }
        return didInteract;
    }

    /**
     * Performs the High Alchemy action using the Explorer's Ring.
     *
     * @param item The item to be alchemized.
     * @return True if the High Alchemy action was successful, false otherwise.
     */
    public static boolean highAlch(Rs2ItemModel item) {
        // Check if any Explorer's Ring is in the inventory
        if ((Rs2Inventory.hasItem(ItemID.EXPLORERS_RING_1) ||
                Rs2Inventory.hasItem(ItemID.EXPLORERS_RING_2) ||
                Rs2Inventory.hasItem(ItemID.EXPLORERS_RING_3) ||
                Rs2Inventory.hasItem(ItemID.EXPLORERS_RING_4))) {

            // Retrieve total charges and ensure Alchemy is enabled
            int totalCharges = getTotalChargesExplorersRing(ItemChargeConfig.KEY_EXPLORERS_RING);
            if (totalCharges <= 0) return false;

            return interact(item);
        }

        // Check if any Explorer's Ring is equipped
        if ((Rs2Equipment.hasEquipped(ItemID.EXPLORERS_RING_1) ||
                Rs2Equipment.hasEquipped(ItemID.EXPLORERS_RING_2) ||
                Rs2Equipment.hasEquipped(ItemID.EXPLORERS_RING_3) ||
                Rs2Equipment.hasEquipped(ItemID.EXPLORERS_RING_4))) {
            return interact(item);
        }
        return false;
    }

    /**
     * Checks if the inventory is visible
     *
     * @return true if visible
     */
    public static boolean isExplorersRingInventoryVisible() {
        return Rs2Widget.getWidget(ComponentID.EXPLORERS_RING_INVENTORY) != null && Rs2Widget.isWidgetVisible((ComponentID.EXPLORERS_RING_INVENTORY));
    }

    /**
     * Close the inventory widget for the explorers ring
     *
     * @return true if closed succesfully
     */
    public static boolean closeInterface() {
        if (Microbot.getVarbitValue(Varbits.EXPLORER_RING_ALCHTYPE) == 0) return true;

        return Rs2Widget.clickWidget(31653892);
    }

    /**
     * check if player has explorers ring and charges
     * @return if in your inventory or equipped
     */
    public static boolean hasRing() {
        return Rs2Inventory.hasItem(ItemID.EXPLORERS_RING_1) ||
                Rs2Inventory.hasItem(ItemID.EXPLORERS_RING_2) ||
                Rs2Inventory.hasItem(ItemID.EXPLORERS_RING_3) ||
                Rs2Inventory.hasItem(ItemID.EXPLORERS_RING_4) ||
                Rs2Equipment.hasEquipped(ItemID.EXPLORERS_RING_1) ||
                Rs2Equipment.hasEquipped(ItemID.EXPLORERS_RING_2) ||
                Rs2Equipment.hasEquipped(ItemID.EXPLORERS_RING_3) ||
                Rs2Equipment.hasEquipped(ItemID.EXPLORERS_RING_4);
    }

    /**
     * check if the player has atleast 1 or more charges
     * @return true if more than one charge
     */
    public static boolean hasCharges() {
        int totalCharges = getTotalChargesExplorersRing(ItemChargeConfig.KEY_EXPLORERS_RING);
        return totalCharges > 0;
    }
}
