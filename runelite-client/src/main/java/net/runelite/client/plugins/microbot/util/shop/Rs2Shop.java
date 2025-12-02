package net.runelite.client.plugins.microbot.util.shop;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuAction;
import net.runelite.api.NPCComposition;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static net.runelite.client.plugins.microbot.Microbot.updateItemContainer;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntilOnClientThread;

@Slf4j
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
        sleepUntilOnClientThread(() -> !isOpen() );
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
     * Opens the shop interface by interacting with the specified NPC.
     *
     * @param npcName The name of the shop NPC to interact with
     * @param exact   Whether to match the name exactly or allow partial matches
     * @return true if the shop is successfully opened, false otherwise.
     */
    public static boolean openShop(String npcName, boolean exact) {
        Microbot.status = "Opening Shop";
        try {
            if (isOpen()) return true;
            
            Rs2NpcModel shopNpc = getNearestShopNpc(npcName, exact);
            if (shopNpc == null) {
                log.warn("No shop NPC found with name '{}' and Trade action", npcName);
                return false;
            }
            
            Rs2Npc.interact(shopNpc, "Trade");
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
     * Finds the nearest shop NPC with the specified name and "Trade" action.
     *
     * @param npcName The name of the shop NPC to find
     * @param exact   Whether to match the name exactly or allow partial matches
     * @return The nearest shop NPC with "Trade" action, or null if not found
     */
    public static Rs2NpcModel getNearestShopNpc(String npcName, boolean exact) {
        return Rs2Npc.getNpcs(npcName, exact)
                .filter(npc -> {
                    // Check if NPC has "Trade" action
                    try {
                        NPCComposition composition = npc.getComposition();
                        if (composition == null) return false;
                        
                        String[] actions = composition.getActions();
                        if (actions == null) return false;
                        
                        // Check both base and transformed compositions for "Trade" action
                        boolean hasTradeAction = Arrays.stream(actions)
                                .anyMatch(action -> action != null && action.equals("Trade"));
                        
                        // Also check transformed composition if available
                        if (!hasTradeAction) {
                            NPCComposition transformedComposition = npc.getTransformedComposition();
                            if (transformedComposition != null && transformedComposition.getActions() != null) {
                                hasTradeAction = Arrays.stream(transformedComposition.getActions())
                                        .anyMatch(action -> action != null && action.equals("Trade"));
                            }
                        }
                        
                        return hasTradeAction;
                    } catch (Exception e) {
                        log.error("Error checking NPC actions for {}: {}", npc.getName(), e.getMessage());
                        return false;
                    }
                })
                .sorted((npc1, npc2) -> Integer.compare(npc1.getDistanceFromPlayer(), npc2.getDistanceFromPlayer()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Finds the nearest shop NPC with the specified name and "Trade" action (partial name matching).
     *
     * @param npcName The name of the shop NPC to find
     * @return The nearest shop NPC with "Trade" action, or null if not found
     */
    public static Rs2NpcModel getNearestShopNpc(String npcName) {
        return getNearestShopNpc(npcName, false);
    }

    /**
     * Buy Item from the shop
     *
     * @param itemName The name of the item to buy
     * @param quantity The quantity to buy
     * @return true if successful, false otherwise
     */
    public static boolean buyItem(String itemName, String quantity) {
        Microbot.status = "Buying " + quantity + " " + itemName;
        try {
            Rs2ItemModel rs2Item = shopItems.stream()
                    .filter(item -> item.getName().equalsIgnoreCase(itemName))
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
     * Buy Item from the shop
     *
     * @param itemId The ID of the item to buy
     * @param quantity The quantity to buy
     * @return true if successful, false otherwise
     */
    public static boolean buyItem(int itemId, String quantity) {
        Microbot.status = "Buying " + quantity + " item with ID " + itemId;
        try {
            Rs2ItemModel rs2Item = shopItems.stream()
                    .filter(item -> item.getId() == itemId)
                    .findFirst().orElse(null);
            String actionAndQuantity = "Buy " + quantity;
            System.out.println(actionAndQuantity);
            // Check if the item is in stock
            if (hasStock(itemId)) {
                System.out.println("We Have Stock of item with ID " + itemId);
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
        System.out.println("Checking if item " + itemName + " is in stock in the shop");
        System.out.println("Amount of items in the shop: " + shopItems.size());

        // Check if the item ID matches the specified item ID
        for (Rs2ItemModel item : shopItems) {
            if (item.getName().equalsIgnoreCase(itemName) && item.getQuantity() > 0) {
                return true; // Item found in stock
            }
        }
        System.out.println(itemName + " isn't in stock in the shop");
        return false; // Item not found in stock
    }

    /**
     * Checks if the specified item is in stock in the shop. **Note** if the item has stock 0 this will still return true.
     *
     * @param itemId The ID of the item to check.
     *
     * @return true if the item is in stock, false otherwise.
     */
    public static boolean hasStock(int itemId) {
        // Iterate through the shop items to find the specified item
        System.out.println("Checking if item with ID " + itemId + " is in stock in the shop");
        System.out.println("Amount of items in the shop: " + shopItems.size());

        for (Rs2ItemModel item : shopItems) {
            // Check if the item ID matches the specified item ID
            if (item.getId() == itemId && item.getQuantity() > 0) {
                System.out.println("Item with ID " + itemId + " is in stock. Quantity: " + item.getQuantity() + ", Slot: " + item.getSlot());
                return true; // Item found in stock
            }
        }
        System.out.println("Item with ID " + itemId + " isn't in stock in the shop");
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
            if (item.getName().equalsIgnoreCase(itemName) && item.getQuantity() >= minimumQuantity) {
                return true; // Item found in stock with sufficient quantity
            }
        }
        System.out.println(itemName + " isn't in stock in the shop with minimum quantity of " + minimumQuantity);
        return false; // Item not found in stock or with sufficient quantity
    }

    /**
     * Checks if the specified item is in stock in the shop with quantity >= minimumQuantity.
     *
     * @param itemId          The ID of the item to check.
     * @param minimumQuantity The minimum quantity required.
     *
     * @return true if the item is in stock with quantity >= minimumQuantity, false otherwise.
     */
    public static boolean hasMinimumStock(int itemId, int minimumQuantity) {
        System.out.println("Checking if item with ID " + itemId + " is in stock in the shop");

        if (shopItems == null || shopItems.isEmpty()) {
            System.out.println("Shop items list is empty or null, cannot check stock for item with ID " + itemId);
            return false; // No items in the shop to check
        }

        // Iterate through the shop items to find the specified item
        for (Rs2ItemModel item : shopItems) {
            // Check if the item ID matches the specified item ID and quantity is >= minimumQuantity
            if (item.getId() == itemId && item.getQuantity() >= minimumQuantity) {
                return true; // Item found in stock with sufficient quantity
            }
        }

        System.out.println("Item with ID " + itemId + " isn't in stock in the shop with minimum quantity of " + minimumQuantity);
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
            if (item.getName().equalsIgnoreCase(itemName)) {
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

        Microbot.status = action + " " + rs2Item.getName();

        int param0;
        int param1;
        int identifier = 3;
        MenuAction menuAction = MenuAction.CC_OP;
        ItemComposition itemComposition = Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getItemDefinition(rs2Item.getId()))
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
        param0 = getSlot(rs2Item.getName()) + 1; // Use the getSlot method to get the slot number
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

        Microbot.doInvoke(new NewMenuEntry()
                .param0(param0)
                .param1(param1)
                .opcode(menuAction.getId())
                .identifier(identifier)
                .itemId(rs2Item.getId())
                .target(rs2Item.getName())
                ,
                (itemBounds(rs2Item) == null) ? new Rectangle(1, 1) : itemBounds(rs2Item));
        //Rs2Reflection.invokeMenu(param0, param1, menuAction.getId(), identifier, rs2Item.id, action, target, -1, -1);
    }

    /**
     * Waits for the shop to change by comparing the current items with the cached values.
     *
     * @return true if the shop has changed, false if it remains the same.
     */
    public static boolean waitForShopChanges() {
        final List<Rs2ItemModel> initialShopItems = shopItems;

        return Global.sleepUntil(() -> hasShopChanged(initialShopItems));
    }

    /**
     * Checks if the shop has changed since the initial items were stored.
     *
     * @param initialShopItems The initial list of shop items to compare against.
     * @return true if the shop has changed, false otherwise.
     */
    private static boolean hasShopChanged(List<Rs2ItemModel> initialShopItems) {
        return shopItems != initialShopItems;
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
