package net.runelite.client.plugins.microbot.util.depositbox;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.MenuAction;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2BankID;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.util.Global.*;

@Slf4j
public class Rs2DepositBox {

    private static final int DEPOSITBOX_PARENT_WIDGET_ID = 192;
    private static final int DEPOSITBOX_INVENTORY_ITEM_CONTAINER_COMPONENT_ID = 12582935;

    /**
     * Checks if the deposit box interface is open.
     *
     * @return true if the deposit box interface is open, false otherwise.
     */
    public static boolean isOpen() {
        return Rs2Widget.isDepositBoxWidgetOpen();
    }

    /**
     * Retrieves the widget for the deposit box if it is currently open.
     *
     * @return the deposit box widget, or {@code null} if the deposit box is not open
     */
    private static Widget getDepositBoxWidget() {
        if (!isOpen()) return null;
        return Rs2Widget.getWidget(DEPOSITBOX_PARENT_WIDGET_ID, 0);
    }

    /**
     * Closes the deposit box interface.
     *
     * @return true if the deposit box interface was successfully closed, false otherwise.
     */
    public static boolean closeDepositBox() {
        if (!isOpen()) return false;
        Widget closeDepositBox = Rs2Widget.findWidget("Close", List.of(getDepositBoxWidget()), false);
        if (closeDepositBox == null) return false;
        Rs2Widget.clickWidget(closeDepositBox);
        sleepUntilOnClientThread(() -> !isOpen());
        return true;
    }

    /**
     * Opens the deposit box interface by interacting with a nearby deposit box.
     *
     * @return true if the deposit box was successfully opened, false otherwise.
     */
    public static boolean openDepositBox() {
        Microbot.status = "Opening deposit box";
        try {
            if (Microbot.getClient().isWidgetSelected())
                Microbot.getMouse().click();
            // Assuming interaction logic with a nearby deposit box
            if (isOpen()) return true;
            GameObject depositBox = Rs2GameObject.findDepositBox();
            boolean action = false;
            if (depositBox != null) {
                action = Rs2GameObject.interact(depositBox, "Deposit");
            }
            if (action) {
                sleepUntil(Rs2DepositBox::isOpen, 2500);
            }
            return action;
        } catch (Exception e) {
            Microbot.log("Error opening deposit box: " + e.getMessage());
        }
        return false;
    }

    /**
     * Deposits all items from the inventory into the deposit box, if open.
     */
    public static void depositAll() {
        Microbot.status = "Depositing all items";
        if (Rs2Inventory.isEmpty()) return;
        if (!isOpen()) return;
        Widget depositAllWidget = Rs2Widget.findWidget("Deposit Inventory", List.of(getDepositBoxWidget()), false);
        if (depositAllWidget == null) return;

        Rs2Widget.clickWidget(depositAllWidget);
        sleepUntil(Rs2Inventory::isEmpty);
    }

    /**
     * Deposits all inventory items that match the given predicate into the deposit box.
     *
     * @param predicate a condition to filter which items to deposit
     * @return {@code true} if at least one item was deposited, {@code false} otherwise
     */
    public static boolean depositAll(Predicate<Rs2ItemModel> predicate) {
        if (!isOpen()) return false;
        boolean result = false;
        List<Rs2ItemModel> items = Rs2Inventory.items().stream().filter(predicate).distinct().collect(Collectors.toList());
        for (Rs2ItemModel item : items) {
            if (item == null) continue;
            invokeMenu(6, item);
            result = true;
        }
        return result;
    }

    /**
     * Deposits all inventory items except those with the specified IDs.
     *
     * @param ids the IDs of items to exclude from depositing
     * @return {@code true} if at least one item was deposited, {@code false} otherwise
     */
    public static boolean depositAllExcept(Integer... ids) {
        return depositAll(x -> Arrays.stream(ids).noneMatch(id -> id == x.getId()));
    }


    /**
     * Deposits all inventory items except those with the specified names.
     *
     * @param names the names of items to exclude from depositing
     * @return {@code true} if at least one item was deposited, {@code false} otherwise
     */
    public static boolean depositAllExcept(String... names) {
        return depositAll(x -> Arrays.stream(names).noneMatch(name -> x.getName().equalsIgnoreCase(name)));
    }

    /**
     * Deposits all inventory items into the deposit box, excluding items with the specified names.
     *
     * @param names the names of items to exclude from depositing
     * @return {@code true} if any items were deposited, {@code false} otherwise
     */
    public static boolean depositAllExcept(List<String> names) {
        return depositAllExcept(names, false);
    }

    /**
     * Deposits all inventory items into the deposit box, excluding items with the specified names.
     *
     * @param names the names of items to exclude from depositing
     * @param exact {@code true} for exact name matches, {@code false} for partial matches
     * @return {@code true} if any items were deposited, {@code false} otherwise
     */
    public static boolean depositAllExcept(List<String> names, boolean exact) {
        return depositAll(x -> exact ? 
                names.stream().noneMatch(name -> x.getName().equalsIgnoreCase(name)) : 
                names.stream().noneMatch(name -> x.getName().toLowerCase().contains(name.toLowerCase())));
    }

    /**
     * Deposits all inventory items that match the specified names into the deposit box.
     *
     * @param names the names of items to exclude from depositing
     * @return {@code true} if any items were deposited, {@code false} otherwise
     */
    public static boolean depositAll(List<String> names) {
        return depositAll(names, false);
    }

    /**
     * Deposits all inventory items that match the specified names into the deposit box.
     *
     * @param names the names of items to exclude from depositing
     * @param exact {@code true} for exact name matches, {@code false} for partial matches
     * @return {@code true} if any items were deposited, {@code false} otherwise
     */
    public static boolean depositAll(List<String> names, boolean exact) {
        return depositAll(x -> exact ? 
                names.stream().anyMatch(name -> x.getName().equalsIgnoreCase(name)) : 
                names.stream().anyMatch(name -> x.getName().toLowerCase().contains(name.toLowerCase())));
    }

    /**
     * Deposits only the inventory items that match the specified IDs into the deposit box.
     *
     * @param ids the IDs of items to include in the deposit
     * @return {@code true} if any items were deposited, {@code false} otherwise
     */
    public static boolean depositAll(Integer... ids) {
        return depositAll(x -> Arrays.stream(ids).anyMatch(id -> id == x.getId()));
    }


    /**
     * Deposits all inventory items into the deposit box, excluding items with the specified names.
     *
     * @param names the names of items to exclude from depositing
     * @return {@code true} if any items were deposited, {@code false} otherwise
     */
    public static boolean depositAll(String... names) {
        return depositAll(x -> Arrays.stream(names).anyMatch(name -> x.getName().equalsIgnoreCase(name)));
    }

    /**
     * Deposits a specific item by its name.
     *
     * @param itemName the name of the item to deposit
     */
    public static void depositOne(String itemName) {
       depositOne(itemName, false);
    }

    /**
     * Deposits a specific item by its ID.
     *
     * @param itemId the ID of the item to deposit
     */
    public static void depositOne(int itemId) {
        Rs2ItemModel item = Rs2Inventory.get(itemId);
        if (item == null) return;
        depositOne(item);
    }

    /**
     * Deposits a specific item by its name, with an option for partial or exact matching.
     *
     * @param itemName the name of the item to deposit
     * @param exact {@code true} for exact name matching, {@code false} for partial matching
     */
    public static void depositOne(String itemName, boolean exact) {
        Rs2ItemModel item = Rs2Inventory.get(itemName, exact);
        if (item == null) return;
        depositOne(item);
    }


    /**
     * Deposits a specific item by its {@link Rs2ItemModel} reference.
     *
     * @param rs2Item the Rs2Item to deposit
     */
    public static void depositOne(Rs2ItemModel rs2Item) {
        if (rs2Item == null || !isOpen()) return;
        if (!Rs2Inventory.hasItem(rs2Item.getId())) return;
        
        invokeMenu(2, rs2Item);
    }

    /**
     * Deposits a specified quantity of an item by its ID into the deposit box.
     *
     * @param itemId the ID of the item to deposit
     * @param amount the quantity of the item to deposit
     */
    public static void depositX(int itemId, int amount) {
        Rs2ItemModel rs2Item = Rs2Inventory.get(itemId);
        if (rs2Item == null || !isOpen()) return;
        depositX(rs2Item, amount);
    }

    /**
     * Deposits a specified quantity of an item by its name into the deposit box.
     *
     * @param itemName the name of the item to deposit
     * @param amount the quantity of the item to deposit
     */
    public static void depositX(String itemName, int amount) {
        depositX(itemName, amount, false);
    }

    /**
     * Deposits a specified quantity of an item by its name into the deposit box,
     * with an option for partial or exact name matching.
     *
     * @param itemName the name of the item to deposit
     * @param amount the quantity of the item to deposit
     * @param exact {@code true} for exact name matching, {@code false} for partial matching
     */
    public static void depositX(String itemName, int amount, boolean exact) {
        Rs2ItemModel rs2Item = Rs2Inventory.get(itemName, exact);
        if (rs2Item == null || !isOpen()) return;
        depositX(rs2Item, amount);
    }

    /**
     * Deposits a specified quantity of an item by its {@link Rs2ItemModel} reference into the deposit box.
     *
     * @param rs2Item the {@link Rs2ItemModel} to deposit
     * @param amount the quantity of the item to deposit
     */
    public static void depositX(Rs2ItemModel rs2Item, int amount) {
        if (rs2Item == null || !isOpen()) return;
        if (!Rs2Inventory.hasItem(rs2Item.getId())) return;

        invokeMenu(5, rs2Item);

        sleep(Rs2Random.randomGaussian(1100, 200));
        Rs2Keyboard.typeString(String.valueOf(amount));
        Rs2Keyboard.enter();
    }

    /**
     * Deposits all equipment into the deposit box.
     */
    public static void depositEquipment() {
        if (!isOpen()) return;
        Widget depositWornItems = Rs2Widget.findWidget("Deposit worn items", List.of(getDepositBoxWidget()), false);
        if (depositWornItems == null) return;

        Rs2Widget.clickWidget(depositWornItems);
    }
    
    private static void invokeMenu(int entryIndex, Rs2ItemModel item) {
        int identifier = entryIndex;
        String option = "";
        MenuAction action = MenuAction.CC_OP;
        
        switch (identifier) {
            case 2:
                option = "Deposit-1";
                break;
            case 3:
                option = "Deposit-5";
                break;
            case 4:
                option = "Deposit-10";
                break;
            case 5:
                option = "Deposit-X";
                break;
            case 6:
                option = "Deposit-All";
                //action = MenuAction.CC_OP_LOW_PRIORITY;
                break;
        }
        
        Rectangle itemBoundingBox = itemBounds(item);
        
        Microbot.doInvoke(new NewMenuEntry(item.getSlot(), DEPOSITBOX_INVENTORY_ITEM_CONTAINER_COMPONENT_ID, action.getId(), identifier, item.getId(), option),  (itemBoundingBox == null) ? new Rectangle(1, 1) : itemBoundingBox);
    }

    /**
     * Retrieves the list of item widgets in the deposit box container.
     *
     * @return a list of item widgets, or {@code null} if the deposit box is not open or the container is not found
     */
    public static List<Widget> getItems() {
        if (!isOpen()) return null;
        Widget depositBoxInventoryContainer = Rs2Widget.getWidget(DEPOSITBOX_INVENTORY_ITEM_CONTAINER_COMPONENT_ID);
        if (depositBoxInventoryContainer != null) {
            return Arrays.asList(depositBoxInventoryContainer.getDynamicChildren());
        }
        return null;
    }

    /**
     * Retrieves the widget of an item based on the specified slot ID in the deposit box.
     *
     * @param slotId the slot ID of the item
     * @return the widget for the specified slot, or {@code null} if the slot is invalid or the items list is unavailable
     */
    public static Widget getItemWidget(int slotId) {
        List<Widget> items = getItems();
        if (items == null) return null;
        if (slotId < 0 || slotId >= items.size()) return null;
        return items.get(slotId);
    }

    /**
     * Gets the bounding rectangle for the slot of the specified item in the deposit box.
     *
     * @param rs2Item the item to retrieve the bounds for
     * @return the bounding rectangle of the item's slot, or {@code null} if the item is not found
     */
    public static Rectangle itemBounds(Rs2ItemModel rs2Item) {
        Widget itemWidget = getItemWidget(rs2Item.getSlot());
        if (itemWidget == null) return null;
        return itemWidget.getBounds();
    }

    /**
     * Returns the nearest accessible deposit box to the local player’s current location.
     *
     * @return the nearest {@link DepositBoxLocation}, or {@code null} if no accessible deposit box was found
     */
    public static DepositBoxLocation getNearestDepositBox() {
        return getNearestDepositBox(Microbot.getClient().getLocalPlayer().getWorldLocation());
    }

    /**
     * Returns the nearest accessible deposit box to the specified world point,
     * using a default search radius of 15 tiles.
     *
     * @param worldPoint the origin from which to search for deposit boxes
     * @return the nearest {@link DepositBoxLocation}, or {@code null} if no accessible deposit box was found
     */
    public static DepositBoxLocation getNearestDepositBox(WorldPoint worldPoint) {
        return getNearestDepositBox(worldPoint, 15);
    }

    /**
     * Finds the nearest accessible deposit box from the given world point.
     * <p>
     * First, scans for in-world deposit box objects within
     * {@code maxObjectSearchRadius} tiles of {@code worldPoint} whose
     * {@link DepositBoxLocation#hasRequirements()} passes. If one or more
     * are found, returns the closest. Otherwise, performs a full pathfinding
     * search (including transports) to all accessible deposit box coordinates
     * and returns the box at the end of the shortest path.
     * </p>
     *
     * @param worldPoint            the starting location for the search
     * @param maxObjectSearchRadius the tile radius to scan for deposit box objects
     * @return the nearest {@link DepositBoxLocation}, or {@code null} if no accessible deposit box could be reached
     */
    public static DepositBoxLocation getNearestDepositBox(WorldPoint worldPoint, int maxObjectSearchRadius) {
        Microbot.log("Finding nearest deposit box...");

        Set<DepositBoxLocation> accessibleDepositBoxes = Arrays.stream(DepositBoxLocation.values())
                .filter(DepositBoxLocation::hasRequirements)
                .collect(Collectors.toSet());

        if (accessibleDepositBoxes.isEmpty()) {
            Microbot.log("No accessible deposit boxes found");
            return null;
        }

        if (Objects.equals(Microbot.getClient().getLocalPlayer().getWorldLocation(), worldPoint)) {
            List<TileObject> bankObjs = List.of(Rs2GameObject.findDepositBox(maxObjectSearchRadius));

            Optional<DepositBoxLocation> byObject = bankObjs.stream()
                    .map(obj -> {
                        DepositBoxLocation closest = accessibleDepositBoxes.stream()
                                .min(Comparator.comparingInt(b -> obj.getWorldLocation().distanceTo(b.getWorldPoint())))
                                .orElse(null);

                        int dist = closest == null
                                ? Integer.MAX_VALUE
                                : obj.getWorldLocation().distanceTo(closest.getWorldPoint());

                        return new AbstractMap.SimpleEntry<>(closest, dist);
                    })
                    .filter(e -> e.getKey() != null && e.getValue() <= maxObjectSearchRadius)
                    .min(Comparator.comparingInt(Map.Entry::getValue))
                    .map(Map.Entry::getKey);

            if (byObject.isPresent()) {
                Microbot.log("Found nearest deposit box (object): " + byObject.get());
                return byObject.get();
            }
        }

        Set<WorldPoint> targets = accessibleDepositBoxes.stream()
                .map(DepositBoxLocation::getWorldPoint)
                .collect(Collectors.toSet());

        if (ShortestPathPlugin.getPathfinderConfig().getTransports().isEmpty()) {
            ShortestPathPlugin.getPathfinderConfig().refresh();
        }

        Pathfinder pf = new Pathfinder(ShortestPathPlugin.getPathfinderConfig(), worldPoint, targets);
        pf.run();

        List<WorldPoint> path = pf.getPath();
        if (path.isEmpty()) {
            Microbot.log("Unable to find path to any deposit box");
            return null;
        }

        WorldPoint nearestTile = path.get(path.size() - 1);
        Optional<DepositBoxLocation> byPath = accessibleDepositBoxes.stream()
                .filter(b -> b.getWorldPoint().equals(nearestTile))
                .findFirst();

        if (byPath.isPresent()) {
            Microbot.log("Found nearest deposit box (shortest path): " + byPath.get());
            return byPath.get();
        }

        Microbot.log("Nearest deposit box point " + nearestTile + " did not match any DepositBoxLocation");
        return null;
    }

    /**
     * Walk to deposit box location
     *
     * @param depositBoxLocation
     * @return true if player location is less than 4 tiles away from the deposit box location
     */
    public static boolean walkToDepositBox(DepositBoxLocation depositBoxLocation) {
        if (isOpen()) return true;
        Rs2Player.toggleRunEnergy(true);
        Microbot.status = "Walking to nearest deposit box " + depositBoxLocation.name();
        Rs2Walker.walkTo(depositBoxLocation.getWorldPoint(), 4);
        return depositBoxLocation.getWorldPoint().distanceTo2D(Microbot.getClient().getLocalPlayer().getWorldLocation()) <= 4;
    }
    
    /**
     * Walk to the nearest deposit box location
     *
     * @return true if player location is less than 4 tiles away from the deposit box location
     */
    public static boolean walkToDepositBox() {
        return walkToDepositBox(getNearestDepositBox());
    }

    /**
     * Walk to the nearest deposit box location
     *
     * @return true if deposit box interface is open
     */
    public static boolean walkToAndUseDepositBox() {
        return walkToAndUseDepositBox(getNearestDepositBox());
    }

    /**
     * Walk to deposit box location & use deposit box
     *
     * @param depositBoxLocation
     * @return true if deposit box interface is open
     */
    public static boolean walkToAndUseDepositBox(DepositBoxLocation depositBoxLocation) {
        if (isOpen()) return true;
        Rs2Player.toggleRunEnergy(true);
        Microbot.status = "Walking to nearest deposit box " + depositBoxLocation.name();
        boolean result = depositBoxLocation.getWorldPoint().distanceTo(Microbot.getClient().getLocalPlayer().getWorldLocation()) <= 8;
        if (result) {
            return openDepositBox();
        } else {
            Rs2Walker.walkTo(depositBoxLocation.getWorldPoint());
        }
        return false;
    }

    /**
     * Banks items if your inventory does not have enough empty slots (0 empty slots being full).
     * Will walk back to the initial player location passed as a parameter.
     *
     * @param itemNames           List of item names to deposit.
     * @param initialPlayerLocation The initial location of the player to return to after banking.
     * @param emptySlotCount      The minimum number of empty slots required in the inventory.
     * @return true if items were successfully banked and the player returned to the initial location, false otherwise.
     */
    public static boolean bankItemsAndWalkBackToOriginalPosition(List<String> itemNames, WorldPoint initialPlayerLocation, int emptySlotCount) {
        return bankItemsAndWalkBackToOriginalPosition(itemNames, false, initialPlayerLocation, emptySlotCount, 4);
    }

    /**
     * Banks items if the inventory is full and returns to the initial player location.
     *
     * @param itemNames           List of item names to deposit.
     * @param initialPlayerLocation The initial location of the player to return to after banking.
     * @return true if items were successfully banked and the player returned to the initial location, false otherwise.
     */
    public static boolean bankItemsAndWalkBackToOriginalPosition(List<String> itemNames, WorldPoint initialPlayerLocation) {
        return bankItemsAndWalkBackToOriginalPosition(itemNames, false, initialPlayerLocation, 0, 4);
    }

    /**
     * Banks items at a deposit box if the inventory does not have enough empty slots (0 empty slots being full).
     * Will walk back to the initial player location passed as a parameter.
     *
     * @param itemNames           List of item names to deposit.
     * @param exactItemNames      Whether to match item names exactly or partially.
     * @param initialPlayerLocation The initial location of the player to return to after banking.
     * @param emptySlotCount      The minimum number of empty slots required in the inventory.
     * @param distance            Maximum distance to allow from the initial location when returning.
     * @return true if items were successfully banked and the player returned to the initial location, false otherwise.
     */
    public static boolean bankItemsAndWalkBackToOriginalPosition(List<String> itemNames, boolean exactItemNames, WorldPoint initialPlayerLocation, int emptySlotCount, int distance) {
        if (Rs2Inventory.getEmptySlots() <= emptySlotCount) {
            boolean isDepositBoxOpen = Rs2DepositBox.walkToAndUseDepositBox();
            if (isDepositBoxOpen) {
                depositAll(itemNames, exactItemNames);
            }
            return false;
        }

        if (distance > 10) distance = 10;

        if (initialPlayerLocation.distanceTo(Rs2Player.getWorldLocation()) > distance || !Rs2Tile.isTileReachable(initialPlayerLocation)) {
            Rs2Walker.walkTo(initialPlayerLocation, distance);
        } else {
            Rs2Walker.walkFastCanvas(initialPlayerLocation);
        }

        return !(Rs2Inventory.getEmptySlots() <= emptySlotCount) && initialPlayerLocation.distanceTo(Rs2Player.getWorldLocation()) <= distance;
    }

    /**
     * Banks items if the inventory does not have enough empty slots (0 empty slots being full).
     * Will walk back to the initial player location passed as a parameter.
     *
     * @param itemNames           List of item names to deposit.
     * @param initialPlayerLocation The initial location of the player to return to after banking.
     * @param emptySlotCount      The minimum number of empty slots required in the inventory.
     * @param distance            Maximum distance to allow from the initial location when returning.
     * @return true if items were successfully banked and the player returned to the initial location, false otherwise.
     */
    public static boolean bankItemsAndWalkBackToOriginalPosition(List<String> itemNames, WorldPoint initialPlayerLocation, int emptySlotCount, int distance) {
        return bankItemsAndWalkBackToOriginalPosition(itemNames, false, initialPlayerLocation, emptySlotCount, distance);
    }
}
