package net.runelite.client.plugins.microbot.util.grounditem;

import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.grounditems.GroundItem;
import net.runelite.client.plugins.grounditems.GroundItemsPlugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.models.RS2Item;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.reflection.Rs2Reflection;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;

import java.awt.*;
import java.time.Instant;
import java.util.List;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static net.runelite.api.TileItem.OWNERSHIP_SELF;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Todo: rework this class to not be dependant on the grounditem plugin
 */
@Slf4j
public class Rs2GroundItem {

    private static boolean interact(RS2Item rs2Item, String action) {
        if (rs2Item == null) return false;
        try {
            interact(new InteractModel(rs2Item.getTileItem().getId(), rs2Item.getTile().getWorldLocation(), rs2Item.getItem().getName()), action);
        } catch (Exception ex) {
            Microbot.logStackTrace("Rs2GroundItem", ex);
        }
        return true;
    }

    /**
     * Interacts with a ground item by performing a specified action.
     *
     * @param groundItem The ground item to interact with.
     * @param action     The action to perform on the ground item.
     *
     * @return true if the interaction was successful, false otherwise.
     */
    private static boolean interact(InteractModel groundItem, String action) {
        if (groundItem == null) return false;
        try {

            int param0;
            int param1;
            int identifier;
            String target;
            MenuAction menuAction = MenuAction.CANCEL;
            ItemComposition item;

            item = Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getItemDefinition(groundItem.getId())).orElse(null);
            if (item == null) return false;
            identifier = groundItem.getId();

            LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient(), groundItem.getLocation());
            if (localPoint == null) return false;

            param0 = localPoint.getSceneX();
            target = "<col=ff9040>" + groundItem.getName();
            param1 = localPoint.getSceneY();

            String[] groundActions = Rs2Reflection.getGroundItemActions(item);

            int index = -1;
            for (int i = 0; i < groundActions.length; i++) {
                String groundAction = groundActions[i];
                if (groundAction == null || !groundAction.equalsIgnoreCase(action)) continue;
                index = i;
            }

            if (Microbot.getClient().isWidgetSelected()) {
                menuAction = MenuAction.WIDGET_TARGET_ON_GROUND_ITEM;
            } else if (index == 0) {
                menuAction = MenuAction.GROUND_ITEM_FIRST_OPTION;
            } else if (index == 1) {
                menuAction = MenuAction.GROUND_ITEM_SECOND_OPTION;
            } else if (index == 2) {
                menuAction = MenuAction.GROUND_ITEM_THIRD_OPTION;
            } else if (index == 3) {
                menuAction = MenuAction.GROUND_ITEM_FOURTH_OPTION;
            } else if (index == 4) {
                menuAction = MenuAction.GROUND_ITEM_FIFTH_OPTION;
            }
            LocalPoint localPoint1 = LocalPoint.fromWorld(Microbot.getClient(), groundItem.location);
            if (localPoint1 != null) {
                Polygon canvas = Perspective.getCanvasTilePoly(Microbot.getClient(), localPoint1);
                if (canvas != null) {
                    Microbot.doInvoke(new NewMenuEntry(action, param0, param1, menuAction.getId(), identifier, -1, target),
                            canvas.getBounds());
                }
            } else {
                Microbot.doInvoke(new NewMenuEntry(action, param0, param1, menuAction.getId(), identifier, -1, target),
                        new Rectangle(1, 1, Microbot.getClient().getCanvasWidth(), Microbot.getClient().getCanvasHeight()));

            }
        } catch (Exception ex) {
            Microbot.log(ex.getMessage());
            ex.printStackTrace();
        }
        return true;
    }

    public static boolean interact(GroundItem groundItem) {
        return interact(new InteractModel(groundItem.getId(), groundItem.getLocation(), groundItem.getName()), "Take");
    }

    private static int calculateDespawnTime(GroundItem groundItem) {
        Instant spawnTime = groundItem.getSpawnTime();
        if (spawnTime == null) {
            return 0;
        }

        Instant despawnTime = spawnTime.plus(groundItem.getDespawnTime());
        if (Instant.now().isAfter(despawnTime)) {
            // that's weird
            return 0;
        }
        long despawnTimeMillis = despawnTime.toEpochMilli() - Instant.now().toEpochMilli();

        return (int) (despawnTimeMillis / 600);
    }

    /**
     * Returns all the ground items at a tile on the current plane.
     *
     * @param x The x position of the tile in the world.
     * @param y The y position of the tile in the world.
     *
     * @return An array of the ground items on the specified tile.
     */
    public static RS2Item[] getAllAt(int x, int y) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (!Microbot.isLoggedIn()) {
                return null;
            }
            List<RS2Item> list = new ArrayList<>();

            Tile tile = Rs2Tile.getTile(x, y);
            if (tile == null) {
                return null;
            }

            List<TileItem> groundItems = tile.getGroundItems();

            if (groundItems != null && !groundItems.isEmpty()) {
                for (TileItem groundItem : groundItems) {
                    RS2Item rs2Item = new RS2Item(Microbot.getItemManager().getItemComposition(groundItem.getId()), tile, groundItem);
                    list.add(rs2Item);
                }
            }
            return list.toArray(new RS2Item[list.size()]);
        }).orElse(new RS2Item[] {});
    }

    public static RS2Item[] getAll(int range) {
        List<RS2Item> temp = new ArrayList<>();
        int pX = Microbot.getClient().getLocalPlayer().getWorldLocation().getX();
        int pY = Microbot.getClient().getLocalPlayer().getWorldLocation().getY();
        int minX = pX - range, minY = pY - range;
        int maxX = pX + range, maxY = pY + range;
        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                RS2Item[] items = getAllAt(x, y);
                if (items != null)
                    for (RS2Item item : items) {
                        if (item == null) {
                            continue;
                        }
                        temp.add(item);
                    }
            }
        }
        //sort on closest item first
        temp = temp.stream().sorted(Comparator
                        .comparingInt(value -> value.getTile().getLocalLocation()
                                .distanceTo(Microbot.getClient().getLocalPlayer().getLocalLocation())))
                .collect(Collectors.toList());
        //filter out items based on value


        return temp.toArray(new RS2Item[temp.size()]);
    }

    /**
     * Retrieves all RS2Item objects within a specified range of a WorldPoint, sorted by distance.
     * 
     * @param range The radius in tiles to search around the given world point
     * @param worldPoint The center WorldPoint to search around
     * @return An array of RS2Item objects found within the specified range, sorted by proximity
     *         to the center point (closest first). Returns an empty array if no items are found.
     */
    public static RS2Item[] getAllFromWorldPoint(int range, WorldPoint worldPoint) {
        List<RS2Item> temp = new ArrayList<>();
        int safespotX = worldPoint.getX();
        int safespotY = worldPoint.getY();

        int minX = safespotX - range, minY = safespotY - range;
        int maxX = safespotX + range, maxY = safespotY + range;

        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                RS2Item[] items = getAllAt(x, y);
                if (items != null) {
                    for (RS2Item item : items) {
                        if (item == null) {
                            continue;
                        }
                        temp.add(item);
                    }
                }
            }
        }

        // Sort items based on distance from the safespot
        temp = temp.stream()
                .sorted(Comparator.comparingInt(value ->
                        value.getTile().getLocalLocation().distanceTo(new LocalPoint(safespotX, safespotY))))
                .collect(Collectors.toList());

        return temp.toArray(new RS2Item[temp.size()]);
    }


    public static boolean loot(String lootItem, int range) {
        return loot(lootItem, 1, range);
    }

    public static boolean pickup(String lootItem, int range) {
        return loot(lootItem, 1, range);
    }

    public static boolean take(String lootItem, int range) {
        return loot(lootItem, 1, range);
    }

    public static boolean loot(String lootItem, int minQuantity, int range) {
        if (Rs2Inventory.isFull(lootItem)) return false;
        RS2Item[] groundItems = Microbot.getClientThread().runOnClientThreadOptional(() ->
                Rs2GroundItem.getAll(range)
        ).orElse(new RS2Item[] {});
        for (RS2Item rs2Item : groundItems) {
            if (rs2Item.getItem().getName().equalsIgnoreCase(lootItem) && rs2Item.getTileItem().getQuantity() >= minQuantity) {
                interact(rs2Item);
                return true;
            }
        }
        return false;
    }

    public static boolean lootItemBasedOnValue(int value, int range) {
        RS2Item[] groundItems = Microbot.getClientThread().runOnClientThreadOptional(() ->
                Rs2GroundItem.getAll(range)
        ).orElse(new RS2Item[] {});
        final int invSize = Rs2Inventory.size();
        for (RS2Item rs2Item : groundItems) {
            if (!hasLineOfSight(rs2Item.getTile())) continue;
            long totalPrice = (long) Microbot.getClientThread().runOnClientThreadOptional(() ->
                    Microbot.getItemManager().getItemPrice(rs2Item.getItem().getId()) * rs2Item.getTileItem().getQuantity()).orElse(0);
            if (totalPrice >= value) {
                if (Rs2Inventory.isFull()) {
                    if (Rs2Player.eatAt(100)) {
                        Rs2Player.waitForAnimation();
                        boolean result = interact(rs2Item);
                        if (result) {
                            sleepUntil(() -> invSize != Rs2Inventory.size());
                        }
                        return result;
                    }
                }
                boolean result = interact(rs2Item);
                if (result) {
                    sleepUntil(() -> invSize != Rs2Inventory.size());
                }
                return result;
            }
        }
        return false;
    }

    /**
     * Waits for the ground item to despawn while performing an action. (The action should be an interaction with the ground item)
     *<p> This method proves to be more reliable than {@link Rs2Inventory#waitForInventoryChanges} as it could cause endless loops of trying to loot the same item if the items was looted by another player
     * or if the player has a Open Herb Sack, Gem Bag or Seed Box etc... and the item was deposited directly into one of those containers bypassing the inventory, resulting in no inventory change.
     *
     * <p> This method won't be plagued by the same issues as it monitors the ground item itself for despawn/change.
     *
     * @param actionWhileWaiting The action to perform while waiting for the item to despawn
     * @param groundItem The ground item to monitor for despawn
     * @return true if the ground item despawns, false otherwise
     */
    public static boolean waitForGroundItemDespawn(Runnable actionWhileWaiting,GroundItem groundItem){
        sleepUntil(() ->  {
            actionWhileWaiting.run();
            sleepUntil(() -> groundItem != getGroundItems().get(groundItem.getLocation(), groundItem.getId()), Rs2Random.between(600, 2100));
            return groundItem != getGroundItems().get(groundItem.getLocation(), groundItem.getId());
        });
        return groundItem != getGroundItems().get(groundItem.getLocation(), groundItem.getId());
    }

    private static boolean coreLoot(GroundItem groundItem) {
        int quantity = groundItem.isStackable() ? 1 : groundItem.getQuantity();
        if (Rs2Inventory.getEmptySlots() < quantity) {
            quantity = Rs2Inventory.getEmptySlots();
        }
        for (int i = 0; i < quantity; i++) {

            /**
             *  if the number of empty slots is less than the item quantity,
             *  return true only if the item is stackable and is already present in the inventory.
             *  Otherwise, return false.
             */
            if (Rs2Inventory.getEmptySlots() < quantity) {
                if (!groundItem.isStackable())
                    return false;
                if (!Rs2Inventory.hasItem(groundItem.getId()))
                    return false;
            }
            Microbot.pauseAllScripts = true;
            /** switched to waitForGroundItemDespawn instead of waitForInventoryChanges
             *  as waitForInventoryChanges can cause endless loops of trying to loot the same item
             *  even after it has been successfully looted by the player or another player.
             *  Or if the player has a Open Herb Sack, Gem Bag or Seed Box etc it wont trigger an inventory change.
             */

            waitForGroundItemDespawn(() -> interact(groundItem), groundItem);
//            Rs2Inventory.waitForInventoryChanges(() -> interact(groundItem));
        }
        return true;
    }

    private static boolean validateLoot(Predicate<GroundItem> filter) {
        boolean hasLootableItems = hasLootableItems(filter);
        //If there are no more lootable items we succesfully looted everything in the filter
        // true to let the script know that we succesfully looted
        if (!hasLootableItems) {
            Microbot.pauseAllScripts = false;
            return true;
        }
        // This is needed to make sure we dont get stuck in a endless pause if something goes wrong
        Microbot.pauseAllScripts = false;
        // If we reach this statement, we most likely still have items to loot, and we return false to the script
        // Script above can handle extra logic if the looting failed
        return false;
    }


    public static boolean lootItemBasedOnValue(LootingParameters params) {
        Predicate<GroundItem> filter = groundItem -> groundItem.getGePrice() > params.getMinValue() && (groundItem.getGePrice() / groundItem.getQuantity()) < params.getMaxValue() &&
                groundItem.getLocation().distanceTo(Microbot.getClient().getLocalPlayer().getWorldLocation()) < params.getRange() &&
                (!params.isAntiLureProtection() || (params.isAntiLureProtection() && groundItem.getOwnership() == OWNERSHIP_SELF));

        List<GroundItem> groundItems = getGroundItems().values().stream()
                .filter(filter)
                .collect(Collectors.toList());

        if (groundItems.size() < params.getMinItems()) return false;
        if (params.isDelayedLooting()) {
            // Get the ground item with the lowest despawn time
            GroundItem item = groundItems.stream().min(Comparator.comparingInt(Rs2GroundItem::calculateDespawnTime)).orElse(null);
            assert item != null;
            if (calculateDespawnTime(item) > 150) return false;
        }

        for (GroundItem groundItem : groundItems) {
            if (groundItem.getQuantity() < params.getMinItems()) continue;
            if (Rs2Inventory.getEmptySlots() < params.getMinInvSlots()) return true;
            coreLoot(groundItem);
        }

        return validateLoot(filter);
    }

    public static boolean lootItemsBasedOnNames(LootingParameters params) {
        final Predicate<GroundItem> filter = groundItem ->
                groundItem.getLocation().distanceTo(Microbot.getClient().getLocalPlayer().getWorldLocation()) < params.getRange() &&
                        (!params.isAntiLureProtection() || (params.isAntiLureProtection() && groundItem.getOwnership() == OWNERSHIP_SELF)) &&
                        Arrays.stream(params.getNames()).anyMatch(name -> groundItem.getName().trim().toLowerCase().contains(name.trim().toLowerCase()));
        List<GroundItem> groundItems = getGroundItems().values().stream()
                .filter(filter)
                .collect(Collectors.toList());
        if (groundItems.size() < params.getMinItems()) return false;
        if (params.isDelayedLooting()) {
            // Get the ground item with the lowest despawn time
            GroundItem item = groundItems.stream().min(Comparator.comparingInt(Rs2GroundItem::calculateDespawnTime)).orElse(null);
            assert item != null;
            if (calculateDespawnTime(item) > 150) return false;
        }

        for (GroundItem groundItem : groundItems) {
            if (groundItem.getQuantity() < params.getMinQuantity()) continue;
            if (Rs2Inventory.getEmptySlots() <= params.getMinInvSlots()) return true;
            coreLoot(groundItem);
        }
        return validateLoot(filter);
    }

    /**
     * Loots items based on their location and item ID.
     * @param location
     * @param itemId
     * @return
     */
    public static boolean lootItemsBasedOnLocation(WorldPoint location, int itemId) {
        final Predicate<GroundItem> filter = groundItem ->
                groundItem.getLocation().equals(location) && groundItem.getItemId() == itemId;

        List<GroundItem> groundItems = getGroundItems().values().stream()
                .filter(filter)
                .collect(Collectors.toList());

        for (GroundItem groundItem : groundItems) {
            coreLoot(groundItem);
        }
        return validateLoot(filter);
    }

    // Loot untradables
    public static boolean lootUntradables(LootingParameters params) {
        final Predicate<GroundItem> filter = groundItem ->
                groundItem.getLocation().distanceTo(Microbot.getClient().getLocalPlayer().getWorldLocation()) < params.getRange() &&
                        (!params.isAntiLureProtection() || (params.isAntiLureProtection() && groundItem.getOwnership() == OWNERSHIP_SELF)) &&
                        !groundItem.isTradeable() &&
                        groundItem.getId() != ItemID.COINS_995;
        List<GroundItem> groundItems = getGroundItems().values().stream()
                .filter(filter)
                .collect(Collectors.toList());
        if (groundItems.size() < params.getMinItems()) return false;
        if (params.isDelayedLooting()) {
            // Get the ground item with the lowest despawn time
            GroundItem item = groundItems.stream().min(Comparator.comparingInt(Rs2GroundItem::calculateDespawnTime)).orElse(null);
            assert item != null;
            if (calculateDespawnTime(item) > 150) return false;
        }

        for (GroundItem groundItem : groundItems) {
            if (groundItem.getQuantity() < params.getMinQuantity()) continue;
            if (Rs2Inventory.getEmptySlots() <= params.getMinInvSlots()) return true;
            coreLoot(groundItem);
        }
        return validateLoot(filter);
    }

    // Loot coins
    public static boolean lootCoins(LootingParameters params) {
        final Predicate<GroundItem> filter = groundItem ->
                groundItem.getLocation().distanceTo(Microbot.getClient().getLocalPlayer().getWorldLocation()) < params.getRange() &&
                        (!params.isAntiLureProtection() || (params.isAntiLureProtection() && groundItem.getOwnership() == OWNERSHIP_SELF)) &&
                        groundItem.getId() == ItemID.COINS_995;
        List<GroundItem> groundItems = getGroundItems().values().stream()
                .filter(filter)
                .collect(Collectors.toList());
        if (groundItems.size() < params.getMinItems()) return false;
        if (params.isDelayedLooting()) {
            // Get the ground item with the lowest despawn time
            GroundItem item = groundItems.stream().min(Comparator.comparingInt(Rs2GroundItem::calculateDespawnTime)).orElse(null);
            assert item != null;
            if (calculateDespawnTime(item) > 150) return false;
        }

        for (GroundItem groundItem : groundItems) {
            if (groundItem.getQuantity() < params.getMinQuantity()) continue;
            if (Rs2Inventory.getEmptySlots() <= params.getMinInvSlots()) return true;
            coreLoot(groundItem);
        }
        return validateLoot(filter);
    }


    private static boolean hasLootableItems(Predicate<GroundItem> filter) {
        List<GroundItem> groundItems = getGroundItems().values().stream()
                .filter(filter)
                .collect(Collectors.toList());

        return !groundItems.isEmpty();
    }

    public static boolean isItemBasedOnValueOnGround(int value, int range) {
        RS2Item[] groundItems = Microbot.getClientThread().runOnClientThreadOptional(() ->
                Rs2GroundItem.getAll(range)
        ).orElse(new RS2Item[] {});
        for (RS2Item rs2Item : groundItems) {
            long totalPrice = (long) Microbot.getClientThread().runOnClientThreadOptional(() ->
                    Microbot.getItemManager().getItemPrice(rs2Item.getItem().getId()) * rs2Item.getTileItem().getQuantity()).orElse(0);
            if (totalPrice >= value) {
                return true;
            }
        }
        return false;
    }

    @Deprecated(since = "1.4.6, use lootItemsBasedOnNames(LootingParameters params)", forRemoval = true)
    public static boolean lootAllItemBasedOnValue(int value, int range) {
        RS2Item[] groundItems = Microbot.getClientThread().runOnClientThreadOptional(() ->
                Rs2GroundItem.getAll(range)
        ).orElse(new RS2Item[] {});
        Rs2Inventory.dropEmptyVials();
        for (RS2Item rs2Item : groundItems) {
            if (Rs2Inventory.isFull(rs2Item.getItem().getName())) continue;
            long totalPrice = (long) Microbot.getClientThread().runOnClientThreadOptional(() ->
                    Microbot.getItemManager().getItemPrice(rs2Item.getItem().getId()) * rs2Item.getTileItem().getQuantity()).orElse(0);
            if (totalPrice >= value) {
                return interact(rs2Item);
            }
        }
        return false;
    }

    /**
     * TODO: rework this to make use of the coreloot method
     * @param itemId
     * @return
     */
    public static boolean loot(int itemId) {
        return loot(itemId, 50);
    }
    public static boolean loot(int itemId, int range) {
        if (Rs2Inventory.isFull(itemId)) return false;
        RS2Item[] groundItems = Microbot.getClientThread().runOnClientThreadOptional(() ->
                Rs2GroundItem.getAll(range)
        ).orElse(new RS2Item[] {});
        for (RS2Item rs2Item : groundItems) {
            if (rs2Item.getItem().getId() == itemId) {
                interact(rs2Item);
                return true;
            }
        }
        return false;
    }

    public static boolean lootAtGePrice(int minGePrice) {
        return lootItemBasedOnValue(minGePrice, 14);
    }

    public static boolean pickup(int itemId) {
        return loot(itemId);
    }

    public static boolean take(int itemId) {
        return loot(itemId);
    }

    public static boolean interact(RS2Item rs2Item) {
        return interact(rs2Item, "Take");
    }

    public static boolean interact(String itemName, String action) {
        return interact(itemName, action, 255);
    }

    public static boolean interact(String itemName, String action, int range) {
        RS2Item[] groundItems = Microbot.getClientThread().runOnClientThreadOptional(() -> Rs2GroundItem.getAll(range))
                .orElse(new RS2Item[] {});
        for (RS2Item rs2Item : groundItems) {
            if (rs2Item.getItem().getName().equalsIgnoreCase(itemName)) {
                interact(rs2Item, action);
                return true;
            }
        }
        return false;
    }

    public static boolean interact(int itemId, String action, int range) {
        RS2Item[] groundItems = Microbot.getClientThread().runOnClientThreadOptional(() -> Rs2GroundItem.getAll(range))
                .orElse(new RS2Item[] {});;
        for (RS2Item rs2Item : groundItems) {
            if (rs2Item.getItem().getId() == itemId) {
                interact(rs2Item, action);
                return true;
            }
        }
        return false;
    }

    public static boolean exists(int id, int range) {
        RS2Item[] groundItems = Microbot.getClientThread().runOnClientThreadOptional(() -> Rs2GroundItem.getAll(range))
                .orElse(new RS2Item[] {});
        for (RS2Item rs2Item : groundItems) {
            if (rs2Item.getItem().getId() == id) {
                return true;
            }
        }
        return false;
    }

    public static boolean exists(String itemName, int range) {
        RS2Item[] groundItems = Microbot.getClientThread().runOnClientThreadOptional(() -> Rs2GroundItem.getAll(range)).orElse(new RS2Item[] {});
        for (RS2Item rs2Item : groundItems) {
            if (rs2Item.getItem().getName().equalsIgnoreCase(itemName)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasLineOfSight(Tile tile) {
        if (tile == null) return false;
        return new WorldArea(
                tile.getWorldLocation(),
                1,
                1)
                .hasLineOfSightTo(Microbot.getClient().getTopLevelWorldView(), Microbot.getClient().getLocalPlayer().getWorldLocation().toWorldArea());
    }

    /**
     * Loot first item based on worldpoint & id
     * @param worldPoint
     * @param itemId
     * @return
     */
    @Deprecated(since = "1.7.9, use lootItemsBasedOnLocation(WorldPoint location, int itemId)", forRemoval = true)
    public static boolean loot(final WorldPoint worldPoint, final int itemId)
    {
        final Optional<RS2Item> item = Arrays.stream(Rs2GroundItem.getAllAt(worldPoint.getX(), worldPoint.getY()))
                .filter(i -> i.getItem().getId() == itemId)
                .findFirst();
        return Rs2GroundItem.interact(item.orElse(null));
    }

    /**
     * This is to avoid concurrency issues with the original list
     * @return
     */
    public static Table<WorldPoint, Integer, GroundItem> getGroundItems() {
        return ImmutableTable.copyOf(GroundItemsPlugin.getCollectedGroundItems());
    }
}
