package net.runelite.client.plugins.microbot.util.grounditem;

import com.google.common.collect.Table;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.grounditems.GroundItem;
import net.runelite.client.plugins.grounditems.GroundItemsPlugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
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
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static net.runelite.api.TileItem.OWNERSHIP_SELF;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Todo: rework this class to not be dependant on the grounditem plugin
 */
@Slf4j
@Deprecated(since = "2.1.0 - Use Rs2TileItemCache/Rs2TileItemQuery instead", forRemoval = true)
public class Rs2GroundItem {
    private static final int DESPAWN_DELAY_THRESHOLD_TICKS = 150;

    private static final java.util.concurrent.locks.ReentrantLock LOOT_LOCK = new java.util.concurrent.locks.ReentrantLock();
    private static final java.util.concurrent.atomic.AtomicInteger LOOT_DEPTH = new java.util.concurrent.atomic.AtomicInteger();

    public static boolean isLooting() { return LOOT_DEPTH.get() > 0; }

    /** Serializes looting and pauses script loops without changing the user's pause flag. */
    public static boolean runWhilePaused(BooleanSupplier action) {
        if (GroundItemPickup.cancelled() || Microbot.getClient().isClientThread()) return false;
        boolean locked = false;
        try {
            locked = LOOT_LOCK.tryLock(5, java.util.concurrent.TimeUnit.SECONDS);
            if (!locked || GroundItemPickup.cancelled()) return false;
            LOOT_DEPTH.incrementAndGet();
            try { return action.getAsBoolean(); }
            finally { LOOT_DEPTH.decrementAndGet(); }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        } finally { if (locked) LOOT_LOCK.unlock(); }
    }
    private static boolean interact(RS2Item item, String action) {
        if (item == null || Microbot.getClient().isClientThread()) return false;
        net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel model =
                Microbot.getClientThread().runOnClientThreadOptional(() ->
                        new net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel(item.getTile(), item.getTileItem())).orElse(null);
        return model != null && GroundItemPickup.click(model, action);
    }

    private static boolean interact(InteractModel item, String action) {
        if (item == null) return false;
        GroundItem request = GroundItem.builder().id(item.getId()).location(item.getLocation()).build();
        GroundItemPickup.Snapshot target = GroundItemPickup.find(request);
        return target != null && GroundItemPickup.click(target.model, action);
    }

    public static boolean interact(GroundItem item) {
        GroundItemPickup.Snapshot target = GroundItemPickup.find(item);
        return target != null && GroundItemPickup.click(target.model, "Take");
    }
    public static int calculateDespawnTime(GroundItem groundItem) {
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

    private static final RS2Item[] EMPTY_ARRAY = new RS2Item[0];

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
            if (!Microbot.isLoggedIn()) return EMPTY_ARRAY;

            final Tile tile = Rs2Tile.getTile(x, y);
            if (tile == null) return EMPTY_ARRAY;

            List<TileItem> groundItems = tile.getGroundItems();
            if (groundItems == null) return EMPTY_ARRAY;

            return groundItems.stream()
                    .map(groundItem -> new RS2Item(Microbot.getItemManager().getItemComposition(groundItem.getId()), tile, groundItem))
                    .toArray(RS2Item[]::new);
        }).orElse(EMPTY_ARRAY);
    }

    public static RS2Item[] getAll(int range) {
        return getAllFromWorldPoint(range, Rs2Player.getWorldLocation());
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
        if (worldPoint == null) return (RS2Item[]) EMPTY_ARRAY;

        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
                    List<RS2Item> temp = new ArrayList<>();
                    final int pX = worldPoint.getX();
                    final int pY = worldPoint.getY();
                    final int minX = pX - range, minY = pY - range;
                    final int maxX = pX + range, maxY = pY + range;
                    for (int x = minX; x <= maxX; x++) {
                        for (int y = minY; y <= maxY; y++) {
                            for (RS2Item item : getAllAt(x, y)) {
                                if (item == null) continue;
                                temp.add(item);
                            }
                        }
                    }
                    //sort on closest item first
                    return temp.stream().sorted(Comparator.comparingInt(value -> value.getTile().getLocalLocation()
                                    .distanceTo(Microbot.getClient().getLocalPlayer().getLocalLocation())))
                            .toArray(RS2Item[]::new);
                }).orElse(EMPTY_ARRAY);
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
        final RS2Item item = Arrays.stream(Rs2GroundItem.getAll(range))
                .filter(rs2Item -> rs2Item.getItem().getName().equalsIgnoreCase(lootItem) && rs2Item.getTileItem().getQuantity() >= minQuantity)
                .findFirst().orElse(null);

        return interact(item);
    }

    public static boolean lootItemBasedOnValue(int value, int range) {
        RS2Item[] items = Rs2GroundItem.getAll(range);
        final long[] prices = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            long[] result = new long[items.length];
            for (int i = 0; i < items.length; i++) {
                result[i] = (long) Microbot.getItemManager().getItemPrice(items[i].getItem().getId()) * items[i].getTileItem().getQuantity();
            }
            return result;
        }).orElse(new long[items.length]);

        RS2Item rs2Item = null;
        for (int i = 0; i < items.length; i++) {
            if (hasLineOfSight(items[i].getTile()) && prices[i] >= value) {
                rs2Item = items[i];
                break;
            }
        }

        if (rs2Item == null) return false;
        if (Rs2Inventory.isFull() && Rs2Player.eatAt(100)) Rs2Player.waitForAnimation();
        if (!interact(rs2Item)) return false;
        return Rs2Inventory.waitForInventoryChanges(5_000);
    }

    /**
     * Waits for the ground item to despawn while performing an action. (The action should be an interaction with the ground item)
     *<p> This method proves to be more reliable than {@link Rs2Inventory#waitForInventoryChanges} as it could cause endless loops of trying to loot the same item if the items was looted by another player
     * or if the player has an Open Herb Sack, Gem Bag or Seed Box etc... and the item was deposited directly into one of those containers bypassing the inventory, resulting in no inventory change.
     *
     * <p> This method won't be plagued by the same issues as it monitors the ground item itself for despawn/change.
     *
     * @param actionWhileWaiting The action to perform while waiting for the item to despawn
     * @param groundItem The ground item to monitor for despawn
     * @return true if the ground item despawns, false otherwise
     */
    public static boolean waitForGroundItemDespawn(Runnable action, GroundItem item) {
        if (item == null || GroundItemPickup.cancelled() || Microbot.getClient().isClientThread()) return false;
        int quantity = item.getQuantity();
        action.run();
        return sleepUntil(() -> {
            GroundItem current = getGroundItems().get(item.getLocation(), item.getId());
            return current != item || current.getQuantity() < quantity;
        }, 5000);
    }

    public static GroundItemPickup.Result coreLootResult(GroundItem item) {
        if (GroundItemPickup.cancelled()) return GroundItemPickup.Result.CANCELLED;
        if (item == null || !canTakeGroundItem(item)) return GroundItemPickup.Result.NO_SPACE;
        GroundItemPickup.Snapshot target = GroundItemPickup.find(item);
        final GroundItemPickup.Result[] result = {GroundItemPickup.Result.REJECTED};
        runWhilePaused(() -> {
            result[0] = GroundItemPickup.take(target, 0);
            return result[0].madeProgress();
        });
        return result[0];
    }

    /** True only for an observed inventory gain; ground disappearance has its own detailed result. */
    public static boolean coreLoot(GroundItem item) {
        return coreLootResult(item) == GroundItemPickup.Result.COLLECTED;
    }
    private static boolean validateLoot(Predicate<GroundItem> filter) {
        // If there are no more lootable items we successfully looted everything in the filter
        // true to let the script know that we successfully looted
        boolean hasLootableItems = hasLootableItems(filter);
        // If we reach this statement, we most likely still have items to loot, and we return false to the script
        // Script above can handle extra logic if the looting failed
        return !hasLootableItems;
    }





    private static boolean lootWithFilter(LootingParameters params, Predicate<GroundItem> predicate, Set<String> ignored) {
        return Rs2LootEngine.with(params).addCustom("legacy", predicate, ignored).loot();
    }
    private static Set<String> toLowerTrimmedSet(String[] arr) {
        if (arr == null || arr.length == 0) return Collections.emptySet();
        Set<String> out = new HashSet<>(arr.length);
        for (String s : arr) {
            if (s != null) {
                final String t = s.trim().toLowerCase(java.util.Locale.ROOT);
                if (!t.isEmpty()) out.add(t);
            }
        }
        return out;
    }


    public static boolean lootItemBasedOnValue(LootingParameters params) {
        return Rs2LootEngine.with(params).addByValue().loot();
    }
    public static boolean lootItemsBasedOnNames(LootingParameters params) {
        final Set<String> needles = toLowerTrimmedSet(params.getNames());
        if (needles.isEmpty()) return false;

        Predicate<GroundItem> byNames = gi -> {
            final String n = gi.getName() == null ? "" : gi.getName().trim().toLowerCase(java.util.Locale.ROOT);
            for (String needle : needles) {
                if (n.contains(needle)) return true;
            }
            return false;
        };

        return lootWithFilter(params, byNames, /*ignoredLower*/ null);
    }

    public static boolean lootUntradables(LootingParameters params) {
        Predicate<GroundItem> untradables = gi ->
                !gi.isTradeable() && gi.getId() != ItemID.COINS_995;

        return lootWithFilter(params, untradables, /*ignoredLower*/ null);
    }

    public static boolean lootCoins(LootingParameters params) {
        Predicate<GroundItem> coins = gi -> gi.getId() == ItemID.COINS_995;
        return lootWithFilter(params, coins, /*ignoredLower*/ null);
    }


    /**
     * Loots items based on their location and item ID.
     * @param location
     * @param itemId
     * @return
     */
    public static boolean lootItemsBasedOnLocation(WorldPoint location, int itemId) {
        LootingParameters params = new LootingParameters(0, 0, Integer.MAX_VALUE, 1, 0, false, false);
        return Rs2LootEngine.with(params).addCustom("location", item -> item.getId() == itemId
                && item.getLocation().equals(location), null).loot();
    }
    private static boolean hasLootableItems(Predicate<GroundItem> filter) {
        List<GroundItem> groundItems = getGroundItems().values().stream()
                .filter(filter)
                .collect(Collectors.toList());

        return !groundItems.isEmpty();
    }

    public static boolean isItemBasedOnValueOnGround(int value, int range) {
        RS2Item[] items = Rs2GroundItem.getAll(range);
        final long[] prices = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            long[] result = new long[items.length];
            for (int i = 0; i < items.length; i++) {
                result[i] = (long) Microbot.getItemManager().getItemPrice(items[i].getItem().getId()) * items[i].getTileItem().getQuantity();
            }
            return result;
        }).orElse(new long[items.length]);

        for (long price : prices) {
            if (price >= value) return true;
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
        final RS2Item item = Arrays.stream(Rs2GroundItem.getAll(range))
                .filter(rs2Item -> rs2Item.getItem().getId() == itemId)
                .findFirst().orElse(null);
        return interact(item);
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
        final RS2Item item = Arrays.stream(Rs2GroundItem.getAll(range))
                .filter(rs2Item -> rs2Item.getItem().getName().equalsIgnoreCase(itemName))
                .findFirst().orElse(null);
        return interact(item, action);
    }

    public static boolean interact(int itemId, String action, int range) {
        final RS2Item item = Arrays.stream(Rs2GroundItem.getAll(range))
                .filter(rs2Item -> rs2Item.getItem().getId() == itemId)
                .findFirst().orElse(null);
        return interact(item, action);
    }

    public static boolean exists(int id, int range) {
        return Arrays.stream(Rs2GroundItem.getAll(range)).anyMatch(rs2Item -> rs2Item.getItem().getId() == id);
    }

    public static boolean exists(String itemName, int range) {
        return Arrays.stream(Rs2GroundItem.getAll(range)).anyMatch(rs2Item -> rs2Item.getItem().getName().equalsIgnoreCase(itemName));
    }

    public static boolean hasLineOfSight(Tile tile) {
        if (tile == null) return false;
        return tile.getWorldLocation().toWorldArea()
                .hasLineOfSightTo(Microbot.getClient().getTopLevelWorldView(), Rs2Player.getWorldLocation().toWorldArea());
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
        return GroundItemsPlugin.getCollectedGroundItems();
    }

    public static boolean canTakeGroundItem(GroundItem groundItem) {
        int maxQuantity = groundItem.isStackable() ? 1 : groundItem.getQuantity();
        int availableSlots = Rs2Inventory.emptySlotCount();
        int quantity = Math.min(maxQuantity, availableSlots);

        if (quantity == 0 && groundItem.isStackable()) {
            return Rs2Inventory.hasItem(groundItem.getId());
        }

        return quantity > 0;
    }
}
