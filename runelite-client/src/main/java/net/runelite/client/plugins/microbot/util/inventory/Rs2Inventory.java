package net.runelite.client.plugins.microbot.util.inventory;

import net.runelite.api.Point;
import net.runelite.api.*;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.pouch.Pouch;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;
import net.runelite.client.plugins.microbot.util.misc.Rs2Potion;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.util.Pair;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.shop.Rs2Shop;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.event.Level;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.runelite.client.plugins.microbot.Microbot.log;
import static net.runelite.client.plugins.microbot.util.Global.*;

public class Rs2Inventory {
    // The maximum capacity of the inventory
    private static final int COLUMNS = 4;
    private static final int ROWS = 7;
    private static final int CAPACITY = COLUMNS * ROWS;
    private static final String[] EMPTY_ARRAY = new String[0];

    private static List<Rs2ItemModel> inventoryItems = Collections.emptyList();

    public static ItemContainer inventory() {
        return Microbot.getClient().getItemContainer(InventoryID.INV);
    }

    public static void storeInventoryItemsInMemory(ItemContainerChanged e) {
        assert Microbot.getClient().isClientThread();

        if (e.getContainerId() != InventoryID.INV) return;
        final ItemContainer itemContainer = e.getItemContainer();
        if (itemContainer == null) return;

        List<Rs2ItemModel> _inventoryItems = new ArrayList<>();
        for (int i = 0; i < itemContainer.getItems().length; i++) {
            final Item item = itemContainer.getItems()[i];
            if (item.getId() == -1) continue;
            final ItemComposition itemComposition = Microbot.getClient().getItemDefinition(item.getId());
            _inventoryItems.add(new Rs2ItemModel(item, itemComposition, i));
        }
        inventoryItems = Collections.unmodifiableList(_inventoryItems);
    }

    public static Stream<Rs2ItemModel> items() {
        return inventoryItems.stream();
    }

    public static Stream<Rs2ItemModel> items(Predicate<Rs2ItemModel> predicate) {
        return items().filter(predicate);
    }

    /**
     * Gets all the items in the inventory.
     *
     * @return A list of all items in the inventory.
     */
    public static List<Rs2ItemModel> all() {
        return items().collect(Collectors.toList());
    }

    /**
     * A list of all the items that meet a specified filter criteria.
     *
     * @param filter The filter to apply when selecting items.
     *
     * @return A list of items that match the filter.
     */
    public static List<Rs2ItemModel> all(Predicate<Rs2ItemModel> filter) {
        return items(filter).collect(Collectors.toList());
    }

    /**
     * Returns the capacity of your inventory (28).
     *
     * @return The maximum number of items that can be held in the inventory.
     */
    public static int capacity() {
        return CAPACITY;
    }

    /**
     * Combines two items in the inventory by their IDs, ensuring distinct items are used.
     *
     * @param primaryItemId   The ID of the primary item.
     * @param secondaryItemId The ID of the secondary item.
     *
     * @return True if the combine operation was successful, false otherwise.
     */
    public static boolean combine(int primaryItemId, int secondaryItemId) {
        return combine(get(primaryItemId), get(secondaryItemId));
    }

    /**
     * Combines two items in the inventory by their names, ensuring distinct items are used.
     *
     * @param primaryItemName   The name of the primary item.
     * @param secondaryItemName The name of the secondary item.
     *
     * @return True if the combine operation was successful, false otherwise.
     */
    public static boolean combine(String primaryItemName, String secondaryItemName) {
        return combine(get(primaryItemName, false), get(secondaryItemName, false));
    }

    /**
     * Combines two items in the inventory using Rs2ItemModel objects, ensuring distinct items are used.
     *
     * @param primary   The primary item.
     * @param secondary The secondary item.
     *
     * @return True if the combine operation was successful, false otherwise.
     */
    public static boolean combine(Rs2ItemModel primary, Rs2ItemModel secondary) {
        // Get the primary item
        Rs2ItemModel primaryItem = get(item -> item.getId() == primary.getId());
        if (primaryItem == null) {
            Microbot.log("Primary item not found in the inventory.");
            return false;
        }

        // Select the primary item
        if (!use(primaryItem)) return false;
        sleep(100, 175);

        // Get a secondary item that isn't the same as the primary
        Rs2ItemModel secondaryItem = get(item -> item.getId() == secondary.getId() && item.getSlot() != primaryItem.getSlot());
        if (secondaryItem == null) {
            Microbot.log("No valid secondary item found to combine with.");
            return false;
        }

        // Interact with the secondary item
        return use(secondaryItem);
    }

    private static Pair<Rs2ItemModel, Rs2ItemModel> getClosest(List<Rs2ItemModel> first, List<Rs2ItemModel> second) {
        Rs2ItemModel closestPrimaryItem = null;
        Rs2ItemModel closestSecondaryItem = null;
        int minSlotDifference = Integer.MAX_VALUE;

        // Compare each primary item with each secondary item to find the closest slots
        for (Rs2ItemModel primaryItem : first) {
            for (Rs2ItemModel secondaryItem : second) {
                if (primaryItem == secondaryItem) continue;

                int slotDifference = calculateSlotDifference(primaryItem.getSlot(), secondaryItem.getSlot());
                if (slotDifference <= minSlotDifference) {
                    minSlotDifference = slotDifference;
                    closestPrimaryItem = primaryItem;
                    closestSecondaryItem = secondaryItem;
                }
            }
        }

        return new Pair<>(closestPrimaryItem,closestSecondaryItem);
    }

    /**
     * Combines the closest items in the inventory based on their names.
     * <p>
     * This method searches for items in the inventory by their names, then finds the pair of primary and
     * secondary items with the smallest slot difference and combines them.
     * <p>
     * For combining items by their IDs, see {@link #combineClosest(int, int) combineClosest}.
     *
     * @param primaryItemName   the name of the primary item to combine
     * @param secondaryItemName the name of the secondary item to combine
     *
     * @return true if the items were successfully combined, false otherwise
     */
    public static boolean combineClosest(String primaryItemName, String secondaryItemName) {
        // TODO:
        List<Rs2ItemModel> primaryItems = items(item -> item.getName().equalsIgnoreCase(primaryItemName)).collect(Collectors.toList());
        List<Rs2ItemModel> secondaryItems = items(item -> item.getName().equalsIgnoreCase(secondaryItemName)).collect(Collectors.toList());

        if (primaryItems.isEmpty() || secondaryItems.isEmpty()) return false;

        final Pair<Rs2ItemModel,Rs2ItemModel> closest = getClosest(primaryItems,secondaryItems);
        if (!use(closest.x)) return false;
        sleep(100, 175);
        return use(closest.y);
    }

    /**
     * Combines the closest items in the inventory based on their IDs.
     * <p>
     * This method searches for items in the inventory by their IDs, then finds the pair of primary and
     * secondary items with the smallest slot difference and combines them.
     * <p>
     * For combining items by their names, see {@link #combineClosest(String, String) combineClosest}.
     *
     * @param primaryItemId   the ID of the primary item to combine
     * @param secondaryItemId the ID of the secondary item to combine
     *
     * @return true if the items were successfully combined, false otherwise
     */
    public static boolean combineClosest(int primaryItemId, int secondaryItemId) {
        List<Rs2ItemModel> primaryItems = items(x -> x.getId() == primaryItemId).collect(Collectors.toList());
        List<Rs2ItemModel> secondaryItems = items(x -> x.getId() == secondaryItemId).collect(Collectors.toList());

        if (primaryItems.isEmpty() || secondaryItems.isEmpty()) return false;

        final Pair<Rs2ItemModel,Rs2ItemModel> closest = getClosest(primaryItems,secondaryItems);
        if (!use(closest.x)) return false;
        sleep(100, 175);
        return use(closest.y);
    }

    private static int getRow(int slot) {
        return (slot - 1) / 4;
    }

    private static int getColumn(int slot) {
        return (slot - 1) % 4;
    }

    // Helper method to calculate the Manhattan distance between two inventory slots
    private static int calculateSlotDifference(int slot1, int slot2) {
        // Calculate the Manhattan distance between the two slots
        return Math.abs(getRow(slot1) - getRow(slot2)) + Math.abs(getColumn(slot1) - getColumn(slot2));
    }

    /**
     * Checks if the inventory contains an item that matches the specified filter.
     *
     * @param predicate The filter to apply.
     *
     * @return True if the inventory contains an item that matches the filter, false otherwise.
     */
    public static boolean contains(Predicate<Rs2ItemModel> predicate) {
        return items().anyMatch(predicate);
    }

    /**
     * Checks if the inventory contains items with the specified IDs.
     *
     * @param ids The IDs to check for.
     *
     * @return True if the inventory contains all the specified IDs, false otherwise.
     */
    public static boolean contains(int... ids) {
        return contains(item -> Arrays.stream(ids).anyMatch(id -> id == item.getId()));
    }

    /**
     * Checks if the inventory contains items with the specified names.
     *
     * @param names The names to check for.
     *
     * @return True if the inventory contains all the specified names, false otherwise.
     */
    public static boolean contains(String... names) {
        return contains(true, names);
    }

    public static boolean contains(boolean exact, String... names) {
        if(exact) {
            return contains(item -> Arrays.stream(names).anyMatch(name -> name.equalsIgnoreCase(item.getName())));
        }
        else {
            return contains(item -> Arrays.stream(names).map(String::toLowerCase).anyMatch(name -> item.getName().toLowerCase().contains(name)));
        }
    }

    public static boolean contains(String name, boolean exact) {
        return contains(exact, name);
    }

    /**
     * Checks if the inventory contains all the specified IDs.
     *
     * @param ids The IDs to check for.
     *
     * @return True if the inventory contains all the specified IDs, false otherwise.
     */
    public static boolean containsAll(int... ids) {
        return Arrays.stream(ids).allMatch(Rs2Inventory::contains);
    }

    /**
     * Checks if the inventory contains all the specified names.
     *
     * @param names The names to check for.
     *
     * @return True if the inventory contains all the specified names, false otherwise.
     */
    public static boolean containsAll(String... names) {
        return Arrays.stream(names).allMatch(Rs2Inventory::contains);
    }

    /**
     * Counts the number of items in the inventory
     *
     * @return The count of items
     */
    public static int count() {
        return (int) items().count();
    }

    /**
     * Counts the number of items in the inventory that match the specified filter.
     *
     * @param predicate The filter to apply.
     *
     * @return The count of items that match the filter.
     */
    public static int count(Predicate<Rs2ItemModel> predicate) {
        return (int) items(predicate).count();
    }

    /**
     * Counts the number of items in the inventory that match the specified ID.
     *
     * @param id The ID to match.
     *
     * @return The count of items that match the ID.
     */
    public static int count(int id) {
        return count(item -> item.getId() == id);
    }

    /**
     * Counts the number of items in the inventory that match the specified name.
     *
     * @param name The name to match.
     *
     * @return The count of items that match the name.
     */
    public static int count(String name, boolean exact) {
        return count(exact ? item -> item.getName().equalsIgnoreCase(name) :
                item -> item.getName().toLowerCase().contains(name.toLowerCase()));
    }

    /**
     * Counts the number of items in the inventory that match the specified name.
     *
     * @param name The name to match.
     *
     * @return The count of items that match the name.
     */
    public static int count(String name) {
        return count(item -> item.getName().toLowerCase().contains(name.toLowerCase()));
    }

    /**
     * Deselects any item if it is selected.
     *
     * @return True if an item was deselected, false otherwise.
     */
    public static boolean deselect() {
        return isItemSelected() && use(getSelectedItemId());
    }

    private static boolean drop(Rs2ItemModel item) {
        if (item == null) return false;

        invokeMenu(item, "Drop");
        return true;
    }

    /**
     * Drops the item from the inventory that matches the specified filter.
     *
     * @param predicate The filter to identify the item to drop.
     *
     * @return True if the item was successfully dropped, false otherwise.
     */
    public static boolean drop(Predicate<Rs2ItemModel> predicate) {
        return drop(get(predicate));
    }

    /**
     * Drops the item with the specified ID from the inventory.
     *
     * @param id The ID of the item to drop.
     *
     * @return True if the item was successfully dropped, false otherwise.
     */
    public static boolean drop(int id) {
        return drop(get(id));
    }

    /**
     * Drops the item with the specified name from the inventory.
     *
     * @param name The name of the item to drop.
     *
     * @return True if the item was successfully dropped, false otherwise.
     */
    public static boolean drop(String name) {
        return drop(name, false);
    }

    /**
     *
     * @param name
     * @return
     */
    public static boolean drop(String name, boolean exact) {
        return drop(get(name,exact));
    }

    /**
     * Drops all items in the inventory matching the specified filter.
     *
     * @param predicate The filter to apply.
     *
     * @return True if all matching items were successfully dropped, false otherwise.
     */
    public static boolean dropAll(Predicate<Rs2ItemModel> predicate) {
        items(predicate).forEachOrdered(item -> {
            drop(item);
            if (!Rs2AntibanSettings.naturalMouse) sleep(150, 300);
        });
        return true;
    }

    /**
     * Drops all items in the inventory.
     *
     * @return True if all items were successfully dropped, false otherwise.
     */
    public static boolean dropAll() {
        return dropAll(item -> true);
    }

    /**
     * Drops all items in the inventory matching the specified IDs.
     *
     * @param ids The IDs to match.
     *
     * @return True if all matching items were successfully dropped, false otherwise.
     */
    public static boolean dropAll(int... ids) {
        return dropAll(Rs2ItemModel.matches(ids));
    }

    /**
     * Drops all items in the inventory matching the specified names.
     *
     * @param names The names to match.
     *
     * @return True if all matching items were successfully dropped, false otherwise.
     */
    public static boolean dropAll(String... names) {
        return dropAll(true, names); // this was an exact match - standard should be inexact
    }

    /**
     * Drops all items in the inventory that match a specified filter, in a specified order.
     *
     * @param predicate The filter to apply. Only items that match this filter will be dropped.
     * @param dropOrder The order in which to drop the items. This can be one of the following:
     *                  - STANDARD: Items are dropped row by row, from left to right.
     *                  - EFFICIENT_ROW: Items are dropped row by row. For even rows, items are dropped from left to right. For odd rows, items are dropped from right to left.
     *                  - COLUMN: Items are dropped column by column, from top to bottom.
     *                  - EFFICIENT_COLUMN: Items are dropped column by column. For even columns, items are dropped from top to bottom. For odd columns, items are dropped from bottom to top.
     *
     * @return True if all matching items were successfully dropped, false otherwise.
     */
    public static boolean dropAll(Predicate<Rs2ItemModel> predicate, InteractOrder dropOrder) {
        List<Rs2ItemModel> itemsToDrop = calculateInteractOrder(items(predicate).collect(Collectors.toList()), dropOrder);
        for (Rs2ItemModel item : itemsToDrop) {
            if (item == null) continue;
            invokeMenu(item, "Drop");
            if (!Rs2AntibanSettings.naturalMouse)
                sleep(150, 300);
        }
        return true;
    }

    /**
     * Drops all items in the inventory matching the specified names.
     *
     * @param names The names to match.
     *
     * @return True if all matching items were successfully dropped, false otherwise.
     */
    public static boolean dropAll(boolean exact, String... names) {
        return dropAll(Rs2ItemModel.matches(exact, names), InteractOrder.STANDARD);
    }

    /**
     * Drops all items in the inventory that don't match the given IDs.
     *
     * @param ids The IDs to exclude.
     *
     * @return True if all non-matching items were successfully dropped, false otherwise.
     */
    public static boolean dropAllExcept(int... ids) {
        return dropAll(Rs2ItemModel.matches(ids).negate());
    }

    /**
     * Drops all items in the inventory that don't match the given names.
     *
     * @param names The names to exclude.
     *
     * @return True if all non-matching items were successfully dropped, false otherwise.
     */
    public static boolean dropAllExcept(String... names) {
        return dropAllExcept(false, InteractOrder.STANDARD, names);
    }

    /**
     * Drops all items from the inventory except for the ones specified by the names parameter.
     * The exactness of the name matching and the order in which items are dropped can be controlled.
     *
     * @param exact     If true, items are kept in the inventory if their name exactly matches one of the names in the names parameter.
     *                  If false, items are kept in the inventory if their name contains one of the names in the names parameter.
     * @param dropOrder The order in which items are dropped from the inventory. This can be one of the following:
     *                  - STANDARD: Items are dropped row by row, from left to right.
     *                  - EFFICIENT_ROW: Items are dropped row by row. For even rows, items are dropped from left to right. For odd rows, items are dropped from right to left.
     *                  - COLUMN: Items are dropped column by column, from top to bottom.
     *                  - EFFICIENT_COLUMN: Items are dropped column by column. For even columns, items are dropped from top to bottom. For odd columns, items are dropped from bottom to top.
     * @param names     The names of the items to keep in the inventory.
     *
     * @return True if all non-matching items were successfully dropped, false otherwise.
     */
    public static boolean dropAllExcept(boolean exact, InteractOrder dropOrder, String... names) {
        return dropAll(Rs2ItemModel.matches(exact, names).negate(), dropOrder);
    }

    /**
     * Drops all items in the inventory that are not filtered.
     *
     * @param predicate The filter to apply.
     *
     * @return True if all non-matching items were successfully dropped, false otherwise.
     */
    public static boolean dropAllExcept(Predicate<Rs2ItemModel> predicate) {
        return dropAll(predicate.negate());
    }

    /**
     * Drops a specific number of items matching the given name with the specified drop order.
     *
     * @param itemName The name of the items to drop
     * @param quantity The number of items to drop
     * @param dropOrder The order in which to drop the items
     * @return actual number of items dropped
     */
    public static int dropAmount(String itemName, int quantity, InteractOrder dropOrder) {
        return dropAmount(itemName, quantity, dropOrder, false);
    }

    /**
     * Drops a specific number of items matching the given name with the specified drop order.
     *
     * @param itemName The name of the items to drop
     * @param quantity The number of items to drop
     * @param dropOrder The order in which to drop the items
     * @param exact Whether to use exact name matching
     * @return actual number of items dropped
     */
    public static int dropAmount(String itemName, int quantity, InteractOrder dropOrder, boolean exact) {
        if (quantity <= 0) return 0;
        
        List<Rs2ItemModel> matchingItems = items(Rs2ItemModel.matches(exact, itemName))
                .collect(Collectors.toList());
        
        if (matchingItems.isEmpty()) return 0;
        
        int itemsToDrop = Math.min(quantity, matchingItems.size());
        List<Rs2ItemModel> sortedItems = calculateInteractOrder(matchingItems, dropOrder);
        
        int droppedCount = 0;
        for (Rs2ItemModel item : sortedItems) {
            if (droppedCount >= itemsToDrop) break;
            if (item == null) continue;
            
            invokeMenu(item, "Drop");
            droppedCount++;
            
            if (!Rs2AntibanSettings.naturalMouse)
                sleep(150, 300);
        }
        
        return droppedCount;
    }

    /**
     * Drops a specific number of items matching the given item ID with the specified drop order.
     *
     * @param itemId The ID of the items to drop
     * @param quantity The number of items to drop
     * @param dropOrder The order in which to drop the items
     * @return actual number of items dropped
     */
    public static int dropAmount(int itemId, int quantity, InteractOrder dropOrder) {
        if (quantity <= 0) return 0;
        
        List<Rs2ItemModel> matchingItems = items(Rs2ItemModel.matches(itemId))
                .collect(Collectors.toList());
        
        if (matchingItems.isEmpty()) return 0;
        
        int itemsToDrop = Math.min(quantity, matchingItems.size());
        List<Rs2ItemModel> sortedItems = calculateInteractOrder(matchingItems, dropOrder);
        
        int droppedCount = 0;
        for (Rs2ItemModel item : sortedItems) {
            if (droppedCount >= itemsToDrop) break;
            if (item == null) continue;
            
            invokeMenu(item, "Drop");
            droppedCount++;
            
            if (!Rs2AntibanSettings.naturalMouse)
                sleep(150, 300);
        }
        
        return droppedCount;
    }

    /**
     * Drop all items that fall under the gpValue
     *
     * @param gpValue minimum amount of gp required to not drop the item
     *
     * @return
     */
    public static boolean dropAllExcept(int gpValue) {
        return dropAllExcept(gpValue, EMPTY_ARRAY);
    }

    /**
     * Drop all items that fall under the gpValue
     *
     * @param gpValue     minimum amount of gp required to not drop the item
     * @param ignoreItems List of items to not drop
     *
     * @return
     */
    public static boolean dropAllExcept(int gpValue, String[] ignoreItems) {
        final Predicate<Rs2ItemModel> ignore = item -> Arrays.stream(ignoreItems).anyMatch(x -> x.equalsIgnoreCase(item.getName()));
        final Predicate<Rs2ItemModel> price = item -> (long) Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getItemManager().getItemPrice(item.getId()) * item.getQuantity()).orElse(0) >= gpValue;
        return dropAllExcept(ignore.or(price));
    }

    /**
     * Returns the count of empty slots in your inventory.
     *
     * @return The number of empty slots.
     */
    public static int emptySlotCount() {
        return capacity() - fullSlotCount();
    }

    /**
     * Returns a list of items that do not fit the given criteria based on the provided filter.
     *
     * @param predicate The filter to apply.
     *
     * @return A list of items that do not match the filter criteria.
     */
    public static List<Rs2ItemModel> except(Predicate<Rs2ItemModel> predicate) {
        return items(predicate.negate()).collect(Collectors.toList());
    }

    /**
     * Returns the count of full slots in your inventory.
     *
     * @return The number of full slots.
     */
    public static int fullSlotCount() {
        return (int) items().count();
    }

    /**
     * Gets the last item in the inventory that matches the specified item ID.
     *
     * @param id The ID to match.
     *
     * @return The last item that matches the ID, or null if not found.
     */
    public static Rs2ItemModel getLast(int id) {
        final Rs2ItemModel[] items = items(item -> item.getId() == id).toArray(Rs2ItemModel[]::new);
        return items.length == 0 ? null : items[items.length-1];
    }

    /**
     * Gets the item in the inventory that matches the specified filter criteria.
     *
     * @param predicate The filter to apply.
     *
     * @return The item that matches the filter criteria, or null if not found.
     */
    public static Rs2ItemModel get(Predicate<Rs2ItemModel> predicate) {
        return items(predicate).findFirst().orElse(null);
    }

    /**
     * Gets the first item in the inventory that matches one of the given IDs.
     *
     * @param ids The IDs to match.
     *
     * @return The first item that matches one of the IDs, or null if not found.
     */
    public static Rs2ItemModel get(int... ids) {
        return get(item -> Arrays.stream(ids).anyMatch(id -> id == item.getId()));
    }

    /**
     * Gets the item in the inventory with one of the specified names.
     *
     * @param names to match.
     *
     * @return The item with one of the specified names, or null if not found.
     */
    public static Rs2ItemModel get(String... names) {
        return get(names, false);
    }

    /**
     * Gets the item in the inventory with the specified name.
     * this method ignores casing
     *
     * @param name The name to match.
     *
     * @return The item with the specified name, or null if not found.
     */
    public static Rs2ItemModel get(String name, boolean exact) {
        return get(name, false, exact);
    }

    /**
     * Gets the item in the inventory with one of the specified names.
     *
     * @param names The names to match.
     * @param exact true to match the exact name
     *
     * @return The item with one of the specified names, or null if not found.
     */
    public static Rs2ItemModel get(String[] names, boolean exact) {
        return get(names, false, exact);
    }

    /**
     * Gets the item in the inventory with the specified name.
     * this method ignores casing
     *
     * @param name The name to match.
     *
     * @return The item with the specified name, or null if not found.
     */
    public static Rs2ItemModel get(String name, boolean stackable, boolean exact) {
        return get(new String[] {name}, stackable, exact);
    }

    /**
     * Gets the item in the inventory with the specified name.
     * this method ignores casing
     *
     * @param names to match.
     *
     * @return The item with the specified name, or null if not found.
     */
    public static Rs2ItemModel get(String[] names, boolean stackable, boolean exact) {
        Predicate<Rs2ItemModel> filter = Rs2ItemModel.matches(exact, names);
        if (stackable) filter = filter.and(Rs2ItemModel::isStackable);
        return exact ? items(filter).findFirst().orElse(null) :
                items(filter).min(Comparator.comparingInt(item -> item.getName().length())).orElse(null);
    }

    /**
     * Gets list of item in the inventory that matches the specified filter criteria.
     *
     * @param predicate The filter to apply.
     *
     * @return list of item that matches the filter criteria
     */
    public static List<Rs2ItemModel> getList(Predicate<Rs2ItemModel> predicate) {
        return items(predicate).collect(Collectors.toList());
    }

    /**
     * Retrieves the quantity of item(s) based on the filter.
     *
     * @param filter The filter for the item(s)
     *
     * @return The quantity of the item(s) if found, otherwise 0.
     */
    public static int itemQuantity(Predicate<Rs2ItemModel> filter) {
        return items(filter).mapToInt(Rs2ItemModel::getQuantity).sum();
    }


    /**
     * Retrieves the quantity of an item based on its ID.
     *
     * @param id The ID of the item.
     *
     * @return The quantity of the item if found, otherwise 0.
     */
    public static int itemQuantity(int id) {
        return itemQuantity(item -> item.getId() == id);
    }

    /**
     * Retrieves the quantity of an item based on its name.
     *
     * @param itemName The name of the item.
     *
     * @return The quantity of the item if found, otherwise 0.
     */
    public static int itemQuantity(String itemName, boolean exact) {
        return itemQuantity(exact ? item -> item.getName().equalsIgnoreCase(itemName) :
                item -> item.getName().toLowerCase().contains(itemName.toLowerCase()));
    }

    /**
     * Retrieves the quantity of an item based on its name.
     *
     * @param itemName The name of the item.
     *
     * @return The quantity of the item if found, otherwise 0.
     */
    public static int itemQuantity(String itemName) {
        return itemQuantity(itemName,true);
    }

    /**
     * Checks if the player has a certain quantity of an item.
     *
     * @param id     The id of the item to check.
     * @param amount The desired quantity of the item.
     *
     * @return True if the player has the specified quantity of the item, false otherwise.
     */
    public static boolean hasItemAmount(int id, int amount) {
        return hasItemAmount(id, amount, false);
    }

    /**
     * Checks if the player has a certain quantity of an item.
     *
     * @param id     The id of the item to check.
     * @param amount The desired quantity of the item.
     *
     * @return True if the player has the specified quantity of the item, false otherwise.
     */
    public static boolean hasItemAmount(int id, int amount, boolean exact) {
        return exact ? itemQuantity(id) == amount : itemQuantity(id) >= amount;
    }


    /**
     * Checks if the player has a certain quantity of an item.
     *
     * @param name   The name of the item to check.
     * @param amount The desired quantity of the item.
     *
     * @return True if the player has the specified quantity of the item, false otherwise.
     */
    public static boolean hasItemAmount(String name, int amount) {
        Rs2ItemModel item = get(name);
        if (item == null) return false;
        return hasItemAmount(name, amount, item.isStackable(), false);
    }

    /**
     * Checks if the player has a certain quantity of an item.
     *
     * @param name      The name of the item to check.
     * @param amount    The desired quantity of the item.
     * @param stackable A boolean indicating if the item is stackable.
     *
     * @return True if the player has the specified quantity of the item, false otherwise.
     */
    public static boolean hasItemAmount(String name, int amount, boolean stackable) {
        return hasItemAmount(name, amount, stackable, false);
    }

    /**
     * Checks if the player has a certain quantity of an item.
     *
     * @param name      The name of the item to check.
     * @param amount    The desired quantity of the item.
     * @param stackable A boolean indicating if the item is stackable.
     * @param exact     A boolean indicating whether the check should be exact or partial for non-stackable items.
     *
     * @return True if the player has the specified quantity of the item, false otherwise.
     */
    public static boolean hasItemAmount(String name, int amount, boolean stackable, boolean exact) {
        if (stackable) return itemQuantity(name, exact) >= amount;
        return count(name, exact) >= amount;
    }

    /**
     * Checks if the inventory has any item with the specified IDs.
     *
     * @param ids The array of IDs to check.
     * @return true if any item with the specified IDs is found, false otherwise.
     */
    public static boolean hasItem(int... ids) {
        return get(ids) != null;
    }

    /**
     * @param names
     *
     * @return boolean
     */
    public static boolean hasItem(String... names) {
        return get(names) != null;
    }

    public static boolean hasItem(String[] names, boolean exact) {
        return get(names, exact) != null;
    }

    /**
     * @param name
     *
     * @return boolean
     */
    public static boolean hasItem(String name, boolean exact) {
        return get(name, false, exact) != null;
    }

    /**
     * Gets the actions available for the item in the specified slot.
     *
     * @param slot The slot to check.
     *
     * @return An array of available actions for the item in the slot.
     */
    public static String[] getActionsForSlot(int slot) {
        return items(x -> x.getSlot() == slot)
                .map(Rs2ItemModel::getInventoryActions)
                .findFirst().orElse(EMPTY_ARRAY);
    }

    /**
     * Retrieves a list of all edible food items currently in the player's inventory.
     * <p>
     * This includes:
     * <ul>
     *   <li>Items with an "Eat" inventory action</li>
     *   <li>Items named "Jug of wine"</li>
     * </ul>
     * This excludes:
     * <ul>
     *   <li>Noted items</li>
     *   <li>Items containing "rock cake" in the name</li>
     * </ul>
     *
     * @return a list of {@link Rs2ItemModel} representing edible food in the inventory
     */
    public static List<Rs2ItemModel> getInventoryFood() {
        return items(Rs2ItemModel::isFood).collect(Collectors.toList());
    }

    /**
     * Retrieves a list of fast food items (tick delay = 1) from the player's inventory.
     * <p>
     * This is a filtered subset of {@link #getInventoryFood()}, using known food IDs
     * with a 1-tick consumption delay.
     *
     * @return a list of {@link Rs2ItemModel} representing fast food in the inventory
     */
    public static List<Rs2ItemModel> getInventoryFastFood() {
        return Rs2Inventory.getInventoryFood().stream()
                .filter(item -> Rs2Food.getFastFoodIds().contains(item.getId()))
                .collect(Collectors.toList());
    }

    public static List<Rs2ItemModel> getPotions() {
        return items(item -> Arrays.stream(item.getInventoryActions()).anyMatch("drink"::equalsIgnoreCase))
                .collect(Collectors.toList());
    }

    // get bones with the action "bury"
    public static List<Rs2ItemModel> getBones() {
        return items(item -> Arrays.stream(item.getInventoryActions()).anyMatch("bury"::equalsIgnoreCase))
                .collect(Collectors.toList());
    }

    // get items with the action "scatter"
    public static List<Rs2ItemModel> getAshes() {
        return items(item -> Arrays.stream(item.getInventoryActions()).anyMatch("scatter"::equalsIgnoreCase))
                .collect(Collectors.toList());
    }

    /**
     * Gets the count of empty slots in your inventory.
     *
     * @return The number of empty slots.
     */
    @Deprecated(since = "Use emptySlotCount")
    public static int getEmptySlots() {
        return emptySlotCount();
    }

    /**
     * Gets the index of the first empty slot in your inventory.
     * returns -1 if no empty slot is found
     *
     * @return The index of the first empty slot, or -1 if none are found.
     */
    public static int getFirstEmptySlot() {
        // TODO: might be broken
        if (isFull()) return -1;
        for (int i = 0; i < inventory().getItems().length; i++) {
            if (inventory().getItems()[i].getId() == -1) return i;
        }
        return -1;
    }

    /**
     * Gets the index of the next full slot in your inventory.
     * return -1 if no full slot has been found
     *
     * @return The index of the next full slot, or -1 if none are found.
     */
    public static int getFirstFullSlot() {
        // TODO: sorted should be unnecessary due to natural ordering
        return items().sorted().findFirst().map(Rs2ItemModel::getSlot).orElse(-1);
    }

    /**
     * Gets the basic inventory widget. Basic means the bank is not open, the Grand Exchange is not open, the shop is not open, etc.
     *
     * @return The basic inventory widget.
     */
    public static Widget getInventoryWidget() {
        return Rs2Widget.getWidget(InterfaceID.INVENTORY, 0);
    }

    /**
     * Gets the item in the specified slot of the inventory.
     *
     * @param slot The index of the slot to retrieve.
     *
     * @return The item in the specified slot, or null if the slot is empty.
     */
    public static Rs2ItemModel getItemInSlot(int slot) {
        return get(x -> x.getSlot() == slot);
    }

    /**
     * Gets the ID of the item in the specified slot.
     * Returns -1 if the slot has not been found or no item has been found
     *
     * @param slot The slot to check.
     *
     * @return The ID of the item in the slot, or -1 if the slot is empty.
     */
    public static int getIdForSlot(int slot) {
        final Rs2ItemModel item = getItemInSlot(slot);
        return item == null ? -1 : item.getId();
    }

    /**
     * Gets the name of the item in the specified slot of the inventory.
     *
     * @param slot The slot to retrieve the name for.
     *
     * @return The name of the item in the slot, or an empty string if the slot is empty.
     */
    public static String getNameForSlot(int slot) {
        final Rs2ItemModel item = getItemInSlot(slot);
        return item == null ? null : item.getName();
    }

    /**
     * Gets a random item from the inventory that matches the specified item filter.
     *
     * @param filter The filter to apply.
     *
     * @return A random item that matches the filter criteria, or null if no matching items are found.
     */
    public static Rs2ItemModel getRandom(Predicate<Rs2ItemModel> filter) {
        final Rs2ItemModel[] matchingItems = items(filter).toArray(Rs2ItemModel[]::new);
        if (matchingItems.length == 0) return null;

        java.util.Random random = new java.util.Random();
        return matchingItems[random.nextInt(matchingItems.length)];
    }

    /**
     * Gets a random item from the inventory that matches the specified item IDs.
     *
     * @param ids The item IDs to match.
     *
     * @return A random item that matches the item IDs, or null if no matching items are found.
     */
    public static Rs2ItemModel getRandom(int... ids) {
        return getRandom(item -> Arrays.stream(ids).anyMatch(id -> id == item.getId()));
    }


    /**
     * Gets a random item from the inventory that matches the specified item names.
     *
     * @param names The item names to match.
     *
     * @return A random item that matches the item names, or null if no matching items are found.
     */
    public static Rs2ItemModel getRandom(String... names) {
        return getRandom(item -> Arrays.stream(names).anyMatch(name -> item.getName().equalsIgnoreCase(name)));
    }

    private static <T> T getSelectedValueOrDefault(Function<Widget, T> valueSupplier, T _default) {
        final Widget widget = Microbot.getClient().getSelectedWidget();
        return widget == null ? _default : valueSupplier.apply(widget);
    }

    /**
     * Gets the ID of the currently selected item in the inventory.
     * Returns -1 if none is found
     *
     * @return The ID of the currently selected item, or -1 if no item is selected.
     */
    public static int getSelectedItemId() {
        return getSelectedValueOrDefault(Widget::getItemId, -1);
    }

    /**
     * Gets the index of the currently selected item in the inventory.
     * Returns -1 if none is found
     *
     * @return The index of the currently selected item, or -1 if no item is selected.
     */
    public static int getSelectedItemIndex() {
        return getSelectedValueOrDefault(Widget::getIndex, -1);
    }

    /**
     * Gets the name of the currently selected item in the inventory.
     *
     * @return The name of the currently selected item, or an empty string if no item is selected.
     */
    public static String getSelectedItemName() {
        return getSelectedValueOrDefault(w -> Rs2UiHelper.stripColTags(w.getName()), null);
    }

    /**
     * Interacts with a given item in the inventory using the specified action.
     * If the item has an invalid slot value, it will find the slot based on the item ID.
     *
     * @param item   The item to interact with.
     * @param action The action to perform on the item.
     *
     * @return True if the interaction was successful, false otherwise.
     */
    public static boolean interact(Rs2ItemModel item, String action) {
        if (item == null) return false;
        invokeMenu(item, action);
        return true;
    }

    /**
     * Interacts with an item with the specified ID in the inventory using the first available action.
     *
     * @param id The ID of the item to interact with.
     *
     * @return True if the interaction was successful, false otherwise.
     */
    public static boolean interact(int id) {
        return interact(id, "");
    }

    /**
     * Interacts with an item with the specified ID in the inventory using the specified action.
     *
     * @param id     The ID of the item to interact with.
     * @param action The action to perform on the item.
     *
     * @return True if the interaction was successful, false otherwise.
     */
    public static boolean interact(int id, String action) {
        return interact(get(id), action);
    }

    /**
     * Interacts with an item with the specified name in the inventory using the first available action.
     *
     * @param name The name of the item to interact with.
     *
     * @return True if the interaction was successful, false otherwise.
     */
    public static boolean interact(String name) {
        return interact(name, "", false);
    }

    /**
     * Interacts with an item with the specified name in the inventory using the specified action.
     *
     * @param name   The name of the item to interact with.
     * @param action The action to perform on the item.
     *
     * @return True if the interaction was successful, false otherwise.
     */
    public static boolean interact(String name, String action) {
        return interact(name, action, false);
    }

    /**
     * Interacts with an item with the specified id(s) in the inventory using the specified action.
     *
     * @param ids  The ids of the item to interact with.
     * @param action The action to perform on the item.
     *
     * @return True if the interaction was successful, false otherwise.
     */
    public static boolean interact(int[] ids, String action) {
        return Arrays.stream(ids).sequential().anyMatch(id -> interact(id, action));
    }

    /**
     * Interacts with an item with the specified name in the inventory using the specified action.
     *
     * @param names  The name of the item to interact with.
     * @param action The action to perform on the item.
     *
     * @return True if the interaction was successful, false otherwise.
     */
    public static boolean interact(String[] names, String action) {
        return Arrays.stream(names).anyMatch(name -> interact(name, action));
    }

    /**
     * Interacts with an item with the specified name in the inventory using the specified action.
     *
     * @param name   The name of the item to interact with.
     * @param action The action to perform on the item.
     *
     * @return True if the interaction was successful, false otherwise.
     */
    public static boolean interact(String name, String action, boolean exact) {
        return interact(get(name, exact),action);
    }

    /**
     * Interacts with an item in the inventory using the first available action based on the specified filter.
     *
     * @param filter The filter to apply.
     *
     * @return True if the interaction was successful, false otherwise.
     */
    public static boolean interact(Predicate<Rs2ItemModel> filter) {
        return interact(filter, "Use");
    }

    /**
     * Interacts with an item in the inventory using the specified action based on the specified filter.
     *
     * @param filter The filter to apply.
     * @param action The action to perform on the item.
     *
     * @return True if the interaction was successful, false otherwise.
     */
    public static boolean interact(Predicate<Rs2ItemModel> filter, String action) {
        return interact(get(filter),action);
    }

    /**
     * Interacts with a given item in the inventory using the first available action.
     * If the item has an invalid slot value, it will find the slot based on the item ID.
     *
     * @param item The item to interact with.
     *
     * @return True if the interaction was successful, false otherwise.
     */
    public static boolean interact(Rs2ItemModel item) {
        return interact(item, "");
    }

    /**
     * Checks whether the inventory is empty (contains no items).
     *
     * @return True if the inventory is empty, false otherwise.
     */
    public static boolean isEmpty() {
        return items().findAny().isEmpty();
    }

    /**
     * Checks whether the inventory is configured to ignore whether shift interactions are enabled or not.
     *
     * @return True if the inventory ignores shift interactions, false otherwise.
     */
    public static boolean isForceNoShift() {
        throw new NotImplementedException("TODO");
    }

    /**
     * Determines whether the inventory is full (all slots are occupied).
     *
     * @return True if the inventory is full, false otherwise.
     */
    public static boolean isFull() {
        return count() == CAPACITY;
    }

    /**
     * Checks if the inventory is full based on the item name.
     *
     * @param name The name of the item to check.
     *
     * @return true if the inventory is full, false otherwise.
     */
    public static boolean isFull(String name) {
        return count(name) == CAPACITY;
    }

    /**
     * Checks if the inventory is full based on the item ID.
     *
     * @param id The ID of the item to check.
     *
     * @return true if the inventory is full, false otherwise.
     */
    public static boolean isFull(int id) {
        return count(id) == CAPACITY;
    }

    /**
     * Checks whether an item is currently selected in your inventory.
     *
     * @return True if an item is selected, false otherwise.
     */
    public static boolean isItemSelected() {
        // TODO: this will also return true if a spell is selected
        return Microbot.getClient().isWidgetSelected();
    }

    /**
     * Checks whether the inventory is open.
     *
     * @return True if the inventory is open, false otherwise.
     */
    public static boolean isOpen() {
        return Rs2Tab.getCurrentTab() == InterfaceTab.INVENTORY;
    }

    /**
     * Checks if the given slot in the inventory is empty.
     *
     * @param slot The slot to check.
     *
     * @return True if the slot is empty, false otherwise.
     */
    public static boolean isSlotEmpty(int slot) {
        return getItemInSlot(slot) == null;
    }

    /**
     * Checks if the given slot in the inventory is empty.
     *
     * @param slots The slots to check.
     *
     * @return True if the slot is empty, false otherwise.
     */
    public static boolean isSlotsEmpty(int... slots) {
        return Arrays.stream(slots).allMatch(Rs2Inventory::isSlotEmpty);
    }

    /**
     * Checks if the given slot in the inventory is full (contains an item).
     *
     * @param slots The slot(s) to check.
     *
     * @return True if the slot is full, false otherwise.
     */
    public static boolean isSlotFull(int... slots) {
        return !isSlotsEmpty(slots);
    }

    /**
     * Gets the bounding rectangle for the slot of the specified item in the inventory.
     *
     * @param rs2Item The item to get the bounds for.
     *
     * @return The bounding rectangle for the item's slot, or null if the item is not found.
     */
    public static Rectangle itemBounds(Rs2ItemModel rs2Item) {
        Widget inventory = getInventory();
        if (inventory == null) return null;

        return Arrays.stream(inventory.getDynamicChildren())
                .filter(widget -> widget.getIndex() == rs2Item.getSlot())
                .findFirst().map(Widget::getBounds).orElse(null);
    }

    /**
     * Checks if your inventory only contains items that match the specified filter.
     *
     * @param predicate The filter to apply.
     *
     * @return True if the inventory only contains items that match the filter, false otherwise.
     */
    public static boolean onlyContains(Predicate<Rs2ItemModel> predicate) {
        return items().allMatch(predicate);
    }

    /**
     * Checks if your inventory only contains items with the specified ID.
     *
     * @param ids The IDs to check.
     *
     * @return True if the inventory only contains items with the specified IDs, false otherwise.
     */
    public static boolean onlyContains(int... ids) {
        return onlyContains(item -> Arrays.stream(ids).anyMatch(id -> item.getId() == id));
    }

    /**
     * Checks if your inventory only contains items with the specified names.
     *
     * @param names The names to check.
     *
     * @return True if the inventory only contains items with the specified names, false otherwise.
     */
    public static boolean onlyContains(String... names) {
        return onlyContains(item -> Arrays.stream(names).anyMatch(name -> item.getName().equalsIgnoreCase(name)));
    }

    /**
     * Opens the inventory.
     *
     * @return True if the inventory is successfully opened, false otherwise.
     */
    public static boolean open() {
        return Rs2Tab.switchToInventoryTab();
    }

    /**
     * Gets the size of the inventory.
     *
     * @return The size of the inventory.
     */
    public static int size() {
        return CAPACITY;
    }

    /**
     * Gets the total size of stackables of the inventory.
     *
     * @return The total size of stackable items of the inventory.
     */
    public static int stackableSize() {
        return count(item -> item.isNoted() || item.isStackable());
    }

    private static int slot(Rs2ItemModel item) {
        return item == null ? -1 : item.getSlot();
    }

    /**
     * Gets the slot for the item with the specified ID.
     *
     * @param id The ID of the item.
     *
     * @return The slot index for the item, or -1 if not found.
     */
    public static int slot(int id) {
        return slot(get(id));
    }

    /**
     * Gets the slot for the item with the specified name.
     *
     * @param name The name of the item.
     *
     * @return The slot index for the item, or -1 if not found.
     */
    public static int slot(String name) {
        return slot(get(name,true));
    }

    /**
     * Gets the slot for the item that matches the specified filter.
     *
     * @param filter The filter to apply.
     *
     * @return The slot index for the item, or -1 if not found.
     */
    public static int slot(Predicate<Rs2ItemModel> filter) {
        return slot(get(filter));
    }

    /**
     * Checks if the specified slot contains items that match the given IDs.
     *
     * @param slot The slot to check.
     * @param ids  The IDs to match.
     *
     * @return True if the slot contains items that match the IDs, false otherwise.
     */
    public static boolean slotContains(int slot, int... ids) {
        final Rs2ItemModel item = getItemInSlot(slot);
        return item != null && Arrays.stream(ids).anyMatch(id -> id == item.getId());
    }

    /**
     * Checks if the specified slot contains items that match the given names.
     *
     * @param slot  The slot to check.
     * @param names The names to match.
     *
     * @return True if the slot contains items that match the names, false otherwise.
     */
    public static boolean slotContains(int slot, String... names) {
        final Rs2ItemModel item = getItemInSlot(slot);
        return item != null && Arrays.stream(names).anyMatch(name -> name.equalsIgnoreCase(item.getName()));
    }


    /**
     * Interacts with the specified slot in the inventory using the first available action.
     *
     * @param slot The slot to interact with.
     *
     * @return True if the interaction is successful, false otherwise.
     */
    public static boolean slotInteract(int slot) {
        return slotInteract(slot, "");
    }

    /**
     * Interacts with the specified slot in the inventory using the specified action.
     *
     * @param slot   The slot to interact with.
     * @param action The action to perform.
     *
     * @return True if the interaction is successful, false otherwise.
     */
    public static boolean slotInteract(int slot, String action) {
        final Rs2ItemModel item = getItemInSlot(slot);
        if (item == null) return false;

        if (action == null || action.isEmpty()) action = Arrays.stream(item.getInventoryActions())
                .filter(Objects::nonNull).findFirst().orElse("");

        return interact(item, action);
    }

    /**
     * Checks if the specified slot contains items whose names contain the given substring.
     *
     * @param slot The slot to check.
     * @param sub  The substring to search for in item names.
     *
     * @return True if the slot contains items with names containing the substring, false otherwise.
     */
    public static boolean slotNameContains(int slot, String sub) {
        final Rs2ItemModel item = getItemInSlot(slot);
        return item != null && item.getName().toLowerCase().contains(sub.toLowerCase());
    }


    /**
     * Uses the last item with the specified ID in the inventory.
     *
     * @param id The ID to match.
     *
     * @return The last item that matches the ID, or null if not found.
     */
    public static boolean useLast(int id) {
        return use(getLast(id));
    }

    /**
     * Uses the item with the specified name in the inventory.
     *
     * @param name The name of the item to use.
     *
     * @return True if the item is successfully used, false otherwise.
     */
    public static boolean useUnNoted(String name) {
        return interact(get(_item -> _item.getName().toLowerCase().contains(name.toLowerCase()) && !_item.isNoted()), "Use");
    }

    /**
     * Uses the item with the specified ID in the inventory.
     *
     * @param id The ID of the item to use.
     *
     * @return True if the item is successfully used, false otherwise.
     */
    public static boolean use(int id) {
        return interact(id, "Use");
    }

    /**
     * Uses the item with the specified name in the inventory.
     *
     * @param name The name of the item to use.
     *
     * @return True if the item is successfully used, false otherwise.
     */
    public static boolean use(String name) {
        return interact(name, "Use");
    }

    /**
     * Uses the given item in the inventory.
     *
     * @param rs2Item The item to use.
     *
     * @return True if the item is successfully used, false otherwise.
     */
    public static boolean use(Rs2ItemModel rs2Item) {
        if (rs2Item == null) return false;
        return interact(rs2Item, "Use");
    }

    /**
     * @param names
     */
    // TODO: deprecate wield instead?
    @Deprecated(since = "Use wield")
    public static void equip(String... names) {
        wield(names);
    }

    /**
     * @param names possible item names to wield
     *
     * @return
     */
    public static boolean wield(String... names) {
        final Optional<String> name = Arrays.stream(names).filter(Rs2Inventory::hasItem).findFirst();
        if (name.isEmpty()) return false;
        if (!Rs2Equipment.isWearing(name.get(), true)) invokeMenu(get(name.get()), "wield");
        return true;
    }

    /**
     * @param ids possible item ids to wield
     */
    public static boolean wield(int... ids) {
        final OptionalInt id = Arrays.stream(ids).filter(Rs2Inventory::hasItem).findFirst();
        if (id.isEmpty()) return false;
        if (!Rs2Equipment.isWearing(id.getAsInt())) invokeMenu(get(id.getAsInt()), "wield");
        return true;
    }

    /**
     * @param names item name(s)
     */
    public static boolean wear(String... names) {
        return wield(names);
    }

    /**
     * @param ids item id(s)
     */
    public static boolean equip(int... ids) {
        return wield(ids);
    }

    /**
     * @param id item id
     */
    public static boolean wear(int id) {
        if (!Rs2Inventory.hasItem(id)) return false;
        if (Rs2Equipment.isWearing(id)) return false;
        invokeMenu(get(id), "wear");
        return true;
    }

    /**
     * use unnoted inventory item on in-game object
     *
     * @param item     name of the item to use
     * @param objectID to use item on
     *
     * @return
     */
    public static boolean useUnNotedItemOnObject(String item, int objectID) {
        if (Rs2Bank.isOpen()) return false;
        useUnNoted(item);
        Rs2GameObject.interact(objectID);
        return true;
    }

    /**
     * use unnoted inventory item on ingame object
     *
     * @param item   name of the item to use
     * @param object to use item on
     *
     * @return
     */
    public static boolean useUnNotedItemOnObject(String item, TileObject object) {
        if (Rs2Bank.isOpen()) return false;
        if (!useUnNoted(item)) return false;
        sleep(100);
        return isItemSelected() && Rs2GameObject.interact(object);
    }

    /**
     * use inventory item on ingame object
     *
     * @param item
     * @param objectID
     *
     * @return
     */
    public static boolean useItemOnObject(int item, int objectID) {
        if (Rs2Bank.isOpen()) return false;
        if (!use(item)) return false;
        sleep(100);
        return isItemSelected() && Rs2GameObject.interact(objectID);
    }

    /**
     * @param itemId
     * @param npcID
     *
     * @return
     */
    public static boolean useItemOnNpc(int itemId, int npcID) {
        if (Rs2Bank.isOpen()) return false;
        if (!use(itemId)) return false;
        sleep(100);
        return isItemSelected() && Rs2Npc.interact(npcID);
    }

    /**
     * @param itemId
     * @param npc
     *
     * @return
     * @deprecated since 1.8.6, forRemoval = true {@link #useItemOnNpc(int, Rs2NpcModel)}
     */
    @Deprecated(since = "1.8.6" , forRemoval = true)
    public static boolean useItemOnNpc(int itemId, NPC npc) {
        return npc != null && useItemOnNpc(itemId, new Rs2NpcModel(npc));
    }

    public static boolean useItemOnNpc(int item, Rs2NpcModel npc) {
        if (Rs2Bank.isOpen()) return false;
        if (!use(item)) return false;
        if (!sleepUntil(Rs2Inventory::isItemSelected, 1_000)) return false;
        return Rs2Npc.interact(npc);
    }

    /**
     * @param name
     * @param exact
     *
     * @return
     */
    public static Rs2ItemModel getNotedItem(String name, boolean exact) {
        return items(Rs2ItemModel::isNoted).filter(exact ? item -> item.getName().equalsIgnoreCase(name) :
                item -> item.getName().toLowerCase().contains(name.toLowerCase())).findFirst().orElse(null);
    }

    /**
     * @param name
     *
     * @return
     */
    public static boolean hasNotedItem(String name) {
        return getNotedItem(name, false) != null;
    }

    /**
     * @param name
     * @param exact
     *
     * @return
     */
    public static boolean hasNotedItem(String name, boolean exact) {
        return getNotedItem(name, exact) != null;
    }

    public static Rs2ItemModel getUnNotedItem(String name, boolean exact) {
        return get(exact ? item -> item.getName().equalsIgnoreCase(name) && !item.isNoted() :
                item -> item.getName().toLowerCase().contains(name.toLowerCase()) && !item.isNoted());
    }

    public static boolean hasUnNotedItem(String name) {
        return getUnNotedItem(name, false) != null;
    }

    public static boolean hasUnNotedItem(String name, boolean exact) {
        return getUnNotedItem(name, exact) != null;
    }

    /**
     * Method will search for restore energy items in inventory & use them
     */
    public static void useRestoreEnergyItem() {
        Rs2ItemModel restoreItem = getPotionsInInventory(Rs2Potion.getStaminaPotion()).findFirst().orElse(null);
        if (restoreItem == null || Rs2Player.hasStaminaBuffActive()) {
            restoreItem = getPotionsInInventory(Rs2Potion.getRestoreEnergyPotionsVariants().toArray(String[]::new)).findFirst().orElse(null);
            if (restoreItem == null) return;
        }

        Rs2Inventory.interact(restoreItem.getName(), "drink");
    }

    /**
     * Method fetches list of potion items in Inventory, will ignore uses
     *
     * @param potionName Potion Name
     *
     * @return List of Potion Items in Inventory
     */
    public static List<Rs2ItemModel> getFilteredPotionItemsInInventory(String potionName) {
        return getFilteredPotionItemsInInventory(Collections.singletonList(potionName));
    }

    private static final Pattern usesRegexPattern = Pattern.compile("^(.*?)(?:\\(\\d+\\))?$");
    private static Stream<Rs2ItemModel> getPotionsInInventory(String... potionNames) {
        return getPotions().stream().filter(item -> {
            final Matcher matcher = usesRegexPattern.matcher(item.getName());
            return matcher.matches() && Arrays.stream(potionNames).anyMatch(name -> name.equalsIgnoreCase(matcher.group(1).trim()));
        });
    }

    /**
     * Method fetches list of potion items in Inventory, will ignore uses
     *
     * @param potionNames List of Potion Names
     *
     * @return List of Potion Items in Inventory
     */
    public static List<Rs2ItemModel> getFilteredPotionItemsInInventory(List<String> potionNames) {
        return getPotionsInInventory(potionNames.toArray(String[]::new)).collect(Collectors.toList());
    }

    /**
     * Checks if the player has any type of rune pouch in the inventory.
     *
     * @return true if a rune pouch is found in the inventory, false otherwise.
     */
    public static boolean hasRunePouch() {
        return Rs2Inventory.hasItem(RunePouchType.getPouchIds());
    }

    /**
     * Executes menu actions with a provided identifier.
     * If the provided identifier is -1, the old logic is used to determine the identifier.
     *
     * @param rs2Item            The current item to interact with.
     * @param action             The action to be used on the item.
     */
    private static void invokeMenu(Rs2ItemModel rs2Item, String action) {
        if (rs2Item == null) return;
        Rs2Tab.switchToInventoryTab();
        Microbot.status = action + " " + rs2Item.getName();

        int param0;
        int param1;
        int identifier = -1;
        String target = rs2Item.getName();
        MenuAction menuAction = MenuAction.CC_OP;
        Widget[] inventoryWidgets;
        param0 = rs2Item.getSlot();
        boolean isDepositBoxOpen = !Microbot.getClientThread().runOnClientThreadOptional(() -> Rs2Widget.getWidget(ComponentID.DEPOSIT_BOX_INVENTORY_ITEM_CONTAINER) == null
                || Rs2Widget.getWidget(ComponentID.DEPOSIT_BOX_INVENTORY_ITEM_CONTAINER).isHidden()).orElse(false);

        Widget widget;

        if (Rs2Bank.isOpen()) {
            param1 = ComponentID.BANK_INVENTORY_ITEM_CONTAINER;
            widget = Rs2Widget.getWidget(param1);
        } else if (isDepositBoxOpen) {
            param1 = ComponentID.DEPOSIT_BOX_INVENTORY_ITEM_CONTAINER;
            widget = Rs2Widget.getWidget(param1);
        } else if (Rs2GrandExchange.isOpen()) {
            param1 = ComponentID.GRAND_EXCHANGE_INVENTORY_INVENTORY_ITEM_CONTAINER;
            widget = Rs2Widget.getWidget(param1);
        } else if (Rs2Shop.isOpen()) {
            param1 = 19726336;
            widget = Rs2Widget.getWidget(param1);
        } else {
            param1 = ComponentID.INVENTORY_CONTAINER;
            widget = Rs2Widget.getWidget(param1);
        }

        if (widget != null && widget.getChildren() != null) {
            inventoryWidgets = widget.getChildren();
        } else {
            inventoryWidgets = null;
        }

        if (inventoryWidgets == null) return;

        if (!action.isEmpty()) {
            var itemWidget = Arrays.stream(inventoryWidgets).filter(x -> x != null && x.getIndex() == rs2Item.getSlot()).findFirst().orElseGet(null);

            String[] actions = itemWidget != null && itemWidget.getActions() != null ?
                    itemWidget.getActions() :
                    rs2Item.getInventoryActions();

            int simpleIndex = indexOfIgnoreCase(stripColTags(actions), action);
            if (simpleIndex != -1) {
                identifier = simpleIndex + 1;
            } else {
                // We could not find the action in the item widget's actions, so we try to find it in the sub-menu actions
                Map.Entry<String, Integer> subActionMap = rs2Item.getIndexOfSubAction(action);
                if (subActionMap != null) {
                    // The main menu index depends on the inventory interface from which this item is interacted with
                    int mainMenuIndex = java.util.Arrays.asList(actions).indexOf(subActionMap.getKey());
                    identifier = NewMenuEntry.findIdentifier(subActionMap.getValue() + 1, mainMenuIndex + 1);
                    target = "";
                }
            }
        }


        if (isItemSelected()) {
            menuAction = MenuAction.WIDGET_TARGET_ON_WIDGET;
        } else if (action.equalsIgnoreCase("use")) {
            menuAction = MenuAction.WIDGET_TARGET;
        } else if (action.equalsIgnoreCase("cast")) {
            menuAction = MenuAction.WIDGET_TARGET_ON_WIDGET;
        }

        Microbot.doInvoke(new NewMenuEntry()
                .option(action)
                .param0(param0)
                .param1(param1)
                .opcode(menuAction.getId())
                .identifier(identifier)
                .itemId(rs2Item.getId())
                .target(target)
                ,
                (itemBounds(rs2Item) == null) ? new Rectangle(1, 1) : itemBounds(rs2Item));

        if (action.equalsIgnoreCase("destroy")) {
            sleepUntil(() -> Rs2Widget.isWidgetVisible(584, 0));
            Rs2Widget.clickWidget(Rs2Widget.getWidget(584, 1).getId());
        }
    }


    public static Widget getInventory() {
        final int BANK_PIN_INVENTORY_ITEM_CONTAINER = 17563648;
        final int SHOP_INVENTORY_ITEM_CONTAINER = 19726336;
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            for (int id : new int[] {ComponentID.INVENTORY_CONTAINER, ComponentID.BANK_INVENTORY_ITEM_CONTAINER,
                    BANK_PIN_INVENTORY_ITEM_CONTAINER, SHOP_INVENTORY_ITEM_CONTAINER,
                    ComponentID.GRAND_EXCHANGE_INVENTORY_INVENTORY_ITEM_CONTAINER,
                    ComponentID.DEPOSIT_BOX_INVENTORY_ITEM_CONTAINER}) {
                final Widget widget = Microbot.getClient().getWidget(id);
                if (widget != null && widget.getDynamicChildren() != null && !widget.isHidden()) return widget;
            }
            return null;
        }).orElse(null);
    }

    /**
     * Sell item to the shop
     *
     * @param itemName name of the item to sell
     * @param quantity string quantity of items to sell
     *
     * @return true if the item was successfully sold, false otherwise
     */
    public static boolean sellItem(String itemName, String quantity) {
        assert Set.of("1","5","10","50").contains(quantity); // I think these should be all valid quantities
        final Rs2ItemModel item = get(itemName,true);
        if (item == null) {
            Microbot.log("Item not found in inventory.");
            return false;
        }
        invokeMenu(item, "Sell " + quantity);
        return true;
    }

    /**
     * Sell item to the shop
     *
     * @param itemID ID of the item to sell
     * @param quantity string quantity of items to sell
     *
     * @return true if the item was successfully sold, false otherwise
     */
    public static boolean sellItem(int itemID, String quantity) {
        assert Set.of("1","5","10","50").contains(quantity); // I think these should be all valid quantities
        final Rs2ItemModel item = get(itemID);
        if (item == null) {
            Microbot.log("Item not found in inventory.");
            return false;
        }
        invokeMenu(item, "Sell " + quantity);
        Rs2Shop.waitForShopChanges();
        return true;
    }

    /**
     * Waits for the inventory to change within a specified time.
     * Detects changes in inventory size, stackable size, or item quantities.
     *
     * @param timeout The maximum time to wait, in milliseconds.
     * @return True if the inventory changes within the specified time, false otherwise.
     */
    public static boolean waitForInventoryChanges(int timeout) {
        return waitForInventoryChanges(() -> {}, 100, timeout);
    }

    /**
     * Waits for the inventory to change while running a specified action repeatedly.
     * Detects changes in inventory size, stackable size, or item quantities.
     *
     * @param actionWhileWaiting The action to execute while waiting for the inventory to change.
     * @return True if the inventory changes while waiting, false otherwise.
     */
    public static boolean waitForInventoryChanges(Runnable actionWhileWaiting) {
        return waitForInventoryChanges(actionWhileWaiting, Rs2Random.between(300, 600), Rs2Random.between(600, 2400));
    }

    /**
     * Waits for the inventory to change within a specified timeout and executes a specified action repeatedly.
     * Detects changes in inventory size, stackable size, or item quantities.
     *
     * @param actionWhileWaiting The action to execute while waiting for the inventory to change.
     * @param time               The interval in milliseconds between checks for inventory changes.
     * @param timeout            The maximum time to wait, in milliseconds.
     * @return True if the inventory changes within the specified timeout, false otherwise.
     */
    public static boolean waitForInventoryChanges(Runnable actionWhileWaiting, int time, int timeout) {
        final List<Rs2ItemModel> initialInventory = inventoryItems;

        return sleepUntilTrue(() -> {
            actionWhileWaiting.run();
            return hasInventoryChanged(initialInventory);
        }, time, timeout);
    }

    /**
     * Checks whether the inventory has changed.
     *
     * @param initialInventory The snapshot of the inventory to compare against
     *                         initially initialInventory == inventoryItems must be true, otherwise this check won't work
     *                         additionally two inventories which are identical can still count as changed i.e.
     *                         adding and removing a log
     * @return True if the inventory is unchanged, false otherwise.
     */
    private static boolean hasInventoryChanged(List<Rs2ItemModel> initialInventory) {
        return inventoryItems != initialInventory; // TODO: && !inventoryItems.equals(initialInventory);
    }

    /**
     * Moves the specified item to the specified slot in the inventory.
     *
     * @return
     */
    public static boolean moveItemToSlot(Rs2ItemModel item, int slot) {
        if (item == null) return false;
        if (slot < 0 || slot >= CAPACITY) return false;
        if (item.getSlot() == slot) return false;

        Widget inventory = getInventory();
        if (inventory == null) {
            Rs2Tab.switchToInventoryTab();
            sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.INVENTORY);
            inventory = getInventory();
            if (inventory == null) {
                Microbot.log("Inventory widget is null", Level.ERROR);
                return false;
            }
        }
        Rectangle itemBounds = itemBounds(item);
        Rectangle slotBounds = inventory.getDynamicChildren()[slot].getBounds();

        Microbot.drag(itemBounds, slotBounds);

        return true;
    }

    @Deprecated(since = "Use dropAll")
    public static boolean dropEmptyVials() {
        return dropAll("empty vial");
    }

    private static int getIndex(String[] terms, String term) {
        for (int i = 0; i < terms.length; i++) {
            if (terms[i] != null && (terms[i].equalsIgnoreCase(term))) return i;
        }
        return -1;
    }

    private static int indexOfIgnoreCase(String[] sourceList, String searchString) {
        if (sourceList == null || searchString == null) return -1;  // or throw an IllegalArgumentException

        int idx = getIndex(sourceList, searchString);
        if (idx != -1) return idx;

        if (searchString.equalsIgnoreCase("wield")) return getIndex(sourceList, "wear");
        else if (searchString.equalsIgnoreCase("wear")) return getIndex(sourceList, "wield");
        else return idx;  // return -1 if the string is not found
    }

    private static String[] stripColTags(String[] sourceList) {
        List<String> resultList = new ArrayList<>();
        String regex = "<col=[^>]*>";

        for (String item : sourceList) {
            if (item != null) {
                resultList.add(item.replaceAll(regex, ""));
            } else {
                resultList.add(null); // Handle null elements if needed
            }
        }

        return resultList.toArray(String[]::new);
    }

    public static boolean fillPouches() {
        log("Fill pouches...");
        Arrays.stream(Pouch.values()).filter(Pouch::hasRequiredRunecraftingLevel).forEachOrdered(Pouch::fill);
        return true;
    }

    public static boolean emptyPouches() {
        if (isFull()) return false;
        log("Empty pouches...");
        Arrays.stream(Pouch.values()).filter(Pouch::hasRequiredRunecraftingLevel).forEachOrdered(Pouch::empty);
        return true;
    }

    public static boolean checkPouches() {
        if (isFull()) return false;
        log("Checking pouches...");
        Arrays.stream(Pouch.values()).filter(Pouch::hasRequiredRunecraftingLevel).forEachOrdered(Pouch::check);
        return true;
    }

    public static boolean anyPouchUnknown() {
        return Arrays.stream(Pouch.values()).filter(Pouch::hasPouchInInventory).anyMatch(x -> x.hasRequiredRunecraftingLevel() && x.isUnknown());
    }

    public static boolean anyPouchEmpty() {
        return Arrays.stream(Pouch.values()).filter(Pouch::hasPouchInInventory).anyMatch(x -> x.hasRequiredRunecraftingLevel() && x.getRemaining() > 0);
    }

    public static boolean anyPouchFull() {
        return Arrays.stream(Pouch.values()).filter(Pouch::hasPouchInInventory).anyMatch(x -> x.hasRequiredRunecraftingLevel() && x.getHolding() > 0);
    }

    public static boolean allPouchesFull() {
        return Arrays.stream(Pouch.values()).filter(Pouch::hasPouchInInventory).allMatch(x -> (x.hasRequiredRunecraftingLevel() && x.getRemaining() <= 0) || !x.hasRequiredRunecraftingLevel());
    }

    public static boolean allPouchesEmpty() {
        return Arrays.stream(Pouch.values()).filter(Pouch::hasPouchInInventory).allMatch(x -> (x.hasRequiredRunecraftingLevel() && x.getHolding() <= 0) || !x.hasRequiredRunecraftingLevel());
    }

    public static boolean hasDegradedPouch() {
        return Arrays.stream(Pouch.values()).anyMatch(Pouch::isDegraded);
    }

    public static boolean hasAnyPouch() {
        return Arrays.stream(Pouch.values()).anyMatch(Pouch::hasPouchInInventory);
    }

    public static int getRemainingCapacityInPouches() {
        return Arrays.stream(Pouch.values())
                .filter(Pouch::hasRequiredRunecraftingLevel)
                .filter(Pouch::hasPouchInInventory)
                .mapToInt(Pouch::getRemaining)
                .sum();
    }

    /**
     * clean herb in random order
     * @return
     */
    public static void cleanHerbs(InteractOrder interactOrder) {
        if (!Rs2Inventory.hasItem("grimy")) return;

        List<Rs2ItemModel> inventorySlots = calculateInteractOrder(items(x -> x.getName().toLowerCase().contains("grimy"))
                .collect(Collectors.toList()), interactOrder);

        // Shuffle the list to randomize the order

        // Interact with each slot in the random order
        for (Rs2ItemModel item : inventorySlots) {
            if (item.getName().toLowerCase().contains("grimy")) interact(item, "clean");
        }
    }

    public static List<Rs2ItemModel> calculateInteractOrder(List<Rs2ItemModel> rs2Items, InteractOrder interactOrder) {
        switch (interactOrder) {

            case EFFICIENT_ROW:
                rs2Items.sort((item1, item2) -> {
                    int index1 = item1.getSlot();
                    int index2 = item2.getSlot();
                    int row1 = index1 / COLUMNS;
                    int row2 = index2 / COLUMNS;
                    if (row1 != row2) {
                        return Integer.compare(row1, row2);
                    } else {
                        int col1 = index1 % COLUMNS;
                        int col2 = index2 % COLUMNS;
                        if (row1 % 2 == 0) {
                            // For even rows, sort columns normally (left to right)
                            return Integer.compare(col1, col2);
                        } else {
                            // For odd rows, sort columns in reverse (right to left)
                            return Integer.compare(col2, col1);
                        }
                    }
                });
                return rs2Items;

            case COLUMN:
                rs2Items.sort((item1, item2) -> {
                    int index1 = item1.getSlot();
                    int index2 = item2.getSlot();
                    int col1 = index1 % COLUMNS;
                    int col2 = index2 % COLUMNS;
                    if (col1 != col2) {
                        return Integer.compare(col1, col2);
                    } else {
                        return Integer.compare(index1 / COLUMNS, index2 / COLUMNS);
                    }
                });
                return rs2Items;

            case EFFICIENT_COLUMN:
                rs2Items.sort((item1, item2) -> {
                    int index1 = item1.getSlot();
                    int index2 = item2.getSlot();
                    int col1 = index1 % COLUMNS;
                    int col2 = index2 % COLUMNS;
                    if (col1 != col2) {
                        return Integer.compare(col1, col2);
                    } else {
                        int row1 = index1 / COLUMNS;
                        int row2 = index2 / COLUMNS;
                        if (col1 % 2 == 0) {
                            // For even columns, sort rows normally (top to bottom)
                            return Integer.compare(row1, row2);
                        } else {
                            // For odd columns, sort rows in reverse (bottom to top)
                            return Integer.compare(row2, row1);
                        }
                    }
                });
                return rs2Items;

            case ZIGZAG:
                int[] customOrder = {
                        0, 4, 1, 5, 2, 6, 3, 7,
                        11, 15, 10, 14, 9, 13, 8, 12,
                        16, 20, 17, 21, 18, 22, 19, 23,
                        27, 26, 25, 24
                };

                Map<Integer, Integer> orderMap = new HashMap<>();
                for (int i = 0; i < customOrder.length; i++) {
                    orderMap.put(customOrder[i], i);
                }

                rs2Items.sort((item1, item2) -> {
                    int index1 = item1.getSlot();
                    int index2 = item2.getSlot();
                    return Integer.compare(orderMap.getOrDefault(index1, Integer.MAX_VALUE),
                            orderMap.getOrDefault(index2, Integer.MAX_VALUE));
                });
                return rs2Items;

            case STANDARD:
            default:
                return rs2Items;
        }
    }

    // hover over item in inventory
    public static boolean hover(Rs2ItemModel item) {
        if (item == null) return false;
        if (!Rs2AntibanSettings.naturalMouse) {
            if(Rs2AntibanSettings.devDebug)
                Microbot.log("Natural mouse is not enabled, can't hover");
            return false;
        }
        Point point = Rs2UiHelper.getClickingPoint(itemBounds(item), true);
        // if the point is 1,1 then the object is not on screen and we should return false
        if (point.getX() == 1 && point.getY() == 1) {
            return false;
        }
        Microbot.getNaturalMouse().moveTo(point.getX(), point.getY());
        return true;
    }
}
