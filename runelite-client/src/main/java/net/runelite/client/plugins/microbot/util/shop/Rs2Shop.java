package net.runelite.client.plugins.microbot.util.shop;

import net.runelite.api.ItemComposition;
import net.runelite.api.MenuAction;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static net.runelite.client.plugins.microbot.Microbot.updateItemContainer;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntilOnClientThread;

public class Rs2Shop {
    public static final int SHOP_INVENTORY_ITEM_CONTAINER = 19660800;
    public static final int SHOP_CLOSE_BUTTON = 196960801;
    public static List<Rs2ItemModel> shopItems = new ArrayList<Rs2ItemModel>();


    /**
     * close the shop interface
     */
    public static void closeShop() {
        Microbot.status = "Closing Shop";
        if (!isOpen()) return;
        Rs2Widget.clickChildWidget(19660801, 11);
        sleepUntilOnClientThread(() -> Rs2Widget.getWidget(19660800) == null);
    }

    /**
     * check if the shop screen is open
     *
     * @return
     */
    public static boolean isOpen() {
        return Rs2Widget.getWidget(ComponentID.SHOP_INVENTORY_ITEM_CONTAINER) != null
                && !Rs2Widget.isHidden(WidgetInfo.SHOP_INVENTORY_ITEMS_CONTAINER.getId());
    }

    /**
     * Opens the shop
     *
     * @return
     */
    public static boolean openShop(String NPC, boolean exact) {
        Microbot.status = "Opening Shop";
        try {
            if (isOpen()) return true;
            Rs2NpcModel npc = Rs2Npc.getNpc(NPC, exact);
            if (npc == null) return false;
            Rs2Npc.interact(npc, "Trade");
            sleepUntil(Rs2Shop::isOpen, 5000);
            return true;
        } catch (Exception ex) {
            Microbot.logStackTrace("Rs2Shop", ex);
        }
        return false;
    }

    public static boolean openShop(String npc) {
        return openShop(npc, false);
    }

    /**
     * Buy Item from the shop
     *
     * @return
     */
    public static boolean buyItem(String itemName, String quantity) {
        Microbot.status = "Buying " + quantity + " " + itemName;
        try {
            Rs2ItemModel rs2Item = shopItems.stream()
                    .filter(item -> item.name.equalsIgnoreCase(itemName))
                    .findFirst().orElse(null);
            String actionAndQuantity = "Buy " + quantity;
            System.out.println(actionAndQuantity);
            // Check if the item is in stock
            if (hasStock(itemName)) {
                System.out.println("We Have Stock of " + itemName);
                invokeMenu(rs2Item, actionAndQuantity);
            } else {
                return false;
            }

        } catch (Exception ex) {
            Microbot.logStackTrace("Rs2Shop", ex);
        }
        return true;
    }

    /**
     * Buys an item in an optimal way given the desired total quantity.
     * The allowed quantities per purchase are: 1, 5, 10, and 50.
     *
     * @param itemName The name of the item to buy.
     * @param desiredQuantity The total quantity of the item to buy.
     */
    public static void buyItemOptimally(String itemName, int desiredQuantity) {
        // Allowed quantities in descending order to ensure optimal (minimal) calls.
        int[] allowedQuantities = {50, 10, 5, 1};

        for (int allowed : allowedQuantities) {
            // While the remaining quantity is at least the current allowed denomination,
            // execute the buy method for that denomination.
            while (desiredQuantity >= allowed) {
                buyItem(itemName, String.valueOf(allowed));
                desiredQuantity -= allowed;
                Rs2Random.waitEx(900, 300);
            }
        }
    }

    /**
     * Checks if the shop is completely full
     * 
     * @return
     */
    public static boolean isFull() {
        return shopItems.size() >= 40 && shopItems.stream().noneMatch(item -> item.getId() == -1);
    }

    /**
     * Checks if the specified item is in stock in the shop. **Note** if the item has stock 0 this will still return true.
     *
     * @param itemName The name of the item to check.
     *
     * @return true if the item is in stock, false otherwise.
     */
    public static boolean hasStock(String itemName) {
        // Iterate through the shop items to find the specified item
        for (Rs2ItemModel item : shopItems) {
            // Check if the item name matches the specified item name
            if (item.name.equalsIgnoreCase(itemName)) {
                // System.out.println(item.name + " is in stock. Quantity: " + item.quantity + ", Slot: " + item.getSlot());
                return true; // Item found in stock
            }
        }
        System.out.println(itemName + " isn't in stock in the shop");
        return false; // Item not found in stock
    }

    /**
     * Checks if the specified item is in stock in the shop with quantity >= minimumQuantity.
     *
     * @param itemName        The name of the item to check.
     * @param minimumQuantity The minimum quantity required.
     *
     * @return true if the item is in stock with quantity >= minimumQuantity, false otherwise.
     */
    public static boolean hasMinimumStock(String itemName, int minimumQuantity) {
        
        // Iterate through the shop items to find the specified item
        for (Rs2ItemModel item : shopItems) {
            // Check if the item name matches the specified item name and quantity is >= minimumQuantity
            if (item.name.equalsIgnoreCase(itemName) && item.quantity >= minimumQuantity) {
                return true; // Item found in stock with sufficient quantity
            }
        }
        System.out.println(itemName + " isn't in stock in the shop with minimum quantity of " + minimumQuantity);
        return false; // Item not found in stock or with sufficient quantity
    }


    /**
     * Updates the shop items in memory based on the provided event.
     *
     * @param e The event containing the latest shop items.
     */
    public static void storeShopItemsInMemory(ItemContainerChanged e, int id) {
        List<Rs2ItemModel> list = updateItemContainer(id, e);
        if (list != null) {
            System.out.println("Storing shopItems");
            shopItems = list;

            /*Print each item's name
            System.out.println("Shop items:");
            for (Rs2Item item : shopItems) {
                System.out.println(item.name);
                System.out.println(item.quantity);
                System.out.println(item.slot);
            }
            */

        }
    }

    /**
     * Retrieves the slot number of the specified item in the shop.
     *
     * @param itemName The name of the item to find.
     *
     * @return The slot number of the item, or -1 if the item is not found.
     */
    public static int getSlot(String itemName) {
        // Iterate through the shop items to find the specified item
        for (int i = 0; i < shopItems.size(); i++) {
            Rs2ItemModel item = shopItems.get(i);
            // Check if the item name matches the specified item name
            if (item.name.equalsIgnoreCase(itemName)) {
                return item.getSlot(); // Return the slot number of the item
            }
        }
        // Item not found, return -1
        return -1;
    }


    /**
     * Method executes menu actions
     *
     * @param rs2Item Current item to interact with
     * @param action  Action used on the item
     */
    private static void invokeMenu(Rs2ItemModel rs2Item, String action) {
        if (rs2Item == null) return;

        Microbot.status = action + " " + rs2Item.name;

        int param0;
        int param1;
        int identifier = 3;
        MenuAction menuAction = MenuAction.CC_OP;
        ItemComposition itemComposition = Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getItemDefinition(rs2Item.id))
                .orElse(null);
        if (!action.isEmpty()) {
            String[] actions;
            actions = itemComposition.getInventoryActions();

            for (int i = 0; i < actions.length; i++) {
                if (action.equalsIgnoreCase(actions[i])) {
                    identifier = i + 2;
                    break;
                }
            }
        }
        // Determine param0 (item slot in the shop)
        param0 = getSlot(rs2Item.name) + 1; // Use the getSlot method to get the slot number
        System.out.println(param0);

        // Shop Inventory
        switch (action) {
            case "Value":
                // Logic to check Value of item
                identifier = 1;
                param1 = 19660816;
            case "Buy 1":
                // Logic to sell one item
                identifier = 2;
                param1 = 19660816;
                break;
            case "Buy 5":
                // Logic to sell five items
                identifier = 3;
                param1 = 19660816;
                break;
            case "Buy 10":
                // Logic to sell ten items
                identifier = 4;
                param1 = 19660816;
                break;
            case "Buy 50":
                // Logic to sell fifty items
                identifier = 5;
                param1 = 19660816;
                break;
            default:
                System.out.println(action);
                throw new IllegalArgumentException("Invalid action");

        }

        Microbot.doInvoke(new NewMenuEntry(param0, param1, menuAction.getId(), identifier, rs2Item.id, rs2Item.name), (itemBounds(rs2Item) == null) ? new Rectangle(1, 1) : itemBounds(rs2Item));
        //Rs2Reflection.invokeMenu(param0, param1, menuAction.getId(), identifier, rs2Item.id, action, target, -1, -1);
    }

    /**
     * Method to get the bounds of the item
     *
     * @param rs2Item Current item to interact with
     *
     * @return Rectangle of the item
     */
    private static Rectangle itemBounds(Rs2ItemModel rs2Item) {
        return Rs2Widget.getWidget(19660816).getDynamicChildren()[getSlot(rs2Item.getName())+1].getBounds();
    }
}

