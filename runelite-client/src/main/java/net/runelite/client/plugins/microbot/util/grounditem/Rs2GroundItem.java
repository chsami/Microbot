package net.runelite.client.plugins.microbot.util.grounditem;

import com.google.common.collect.Table;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
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

    public static boolean runWhilePaused(BooleanSupplier booleanSupplier) {
        final boolean paused = Microbot.pauseAllScripts.getAndSet(true);
        final boolean success = booleanSupplier.getAsBoolean();
        if (!paused && !Microbot.pauseAllScripts.compareAndSet(true, false)) {
            log.warn("Another script unpaused all scripts");
        }
        return success;
    }

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
                    Microbot.doInvoke(new NewMenuEntry()
                            .option(action)
                            .param0(param0)
                            .param1(param1)
                            .opcode(menuAction.getId())
                            .identifier(identifier)
                            .itemId(-1)
                            .target(target)
                            ,
                canvas.getBounds());
                }
            } else {
                Microbot.doInvoke(new NewMenuEntry()
                        .option(action)
                        .param0(param0)
                        .param1(param1)
                        .opcode(menuAction.getId())
                        .identifier(identifier)
                        .itemId(-1)
                        .target(target)
                        ,
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
        return getAllFromWorldPoint(range, Microbot.getClient().getLocalPlayer().getWorldLocation());
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
         final RS2Item rs2Item = Arrays.stream(Rs2GroundItem.getAll(range))
                .filter(item -> hasLineOfSight(item.getTile()))
                .filter(item -> {
                    final long totalPrice = (long) Microbot.getClientThread().runOnClientThreadOptional(() ->
                            Microbot.getItemManager().getItemPrice(item.getItem().getId()) * item.getTileItem().getQuantity()).orElse(0);
                    return totalPrice >= value;
                }).findFirst().orElse(null);

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
    public static boolean waitForGroundItemDespawn(Runnable actionWhileWaiting,GroundItem groundItem){
        sleepUntil(() ->  {
            actionWhileWaiting.run();
            sleepUntil(() -> groundItem != getGroundItems().get(groundItem.getLocation(), groundItem.getId()), Rs2Random.between(600, 2100));
            return groundItem != getGroundItems().get(groundItem.getLocation(), groundItem.getId());
        });
        return groundItem != getGroundItems().get(groundItem.getLocation(), groundItem.getId());
    }

    public static boolean coreLoot(GroundItem groundItem) {
        int quantity = Math.min(groundItem.isStackable() ? 1 : groundItem.getQuantity(),
                Rs2Inventory.emptySlotCount());

        if (quantity == 0 && groundItem.isStackable()) {
            if (!Rs2Inventory.hasItem(groundItem.getId())) return false;
            quantity = 1;
        }

        final int quantFinal = quantity;
        return runWhilePaused(() -> {
            for (int i = 0; i < quantFinal; i++) {
                waitForGroundItemDespawn(() -> interact(groundItem), groundItem);
            }
            return true;
        });
    }

    private static boolean validateLoot(Predicate<GroundItem> filter) {
        // If there are no more lootable items we successfully looted everything in the filter
        // true to let the script know that we successfully looted
        boolean hasLootableItems = hasLootableItems(filter);
        // If we reach this statement, we most likely still have items to loot, and we return false to the script
        // Script above can handle extra logic if the looting failed
        return !hasLootableItems;
    }





    private static Predicate<GroundItem> baseRangeAndOwnershipFilter(LootingParameters params) {
        final WorldPoint me = Microbot.getClient().getLocalPlayer().getWorldLocation();
        final boolean anti = params.isAntiLureProtection();
        return gi ->
                gi.getLocation().distanceTo(me) < params.getRange() &&
                        (!anti || gi.getOwnership() == OWNERSHIP_SELF);
    }

    private static boolean passesIgnoredNames(GroundItem gi, Set<String> ignoredLower) {
        if (ignoredLower == null || ignoredLower.isEmpty()) return true;
        final String name = gi.getName() == null ? "" : gi.getName().trim().toLowerCase();
        for (String needle : ignoredLower) {
            if (name.contains(needle)) return false;
        }
        return true;
    }

    private static boolean ensureSpaceFor(GroundItem gi, LootingParameters params) {
        if (Rs2Inventory.emptySlotCount() > params.getMinInvSlots()) {
            return true;
        }

        if (params.isEatFoodForSpace() && !canTakeGroundItem(gi) && !Rs2Inventory.getInventoryFood().isEmpty()) {
            if (Rs2Player.eatAt(100)) {
                Rs2Player.waitForAnimation();
            }
        }
        return canTakeGroundItem(gi);
    }

    private static boolean lootWithFilter(
            LootingParameters params,
            Predicate<GroundItem> itemPredicate,
            Set<String> ignoredLower
    ) {
        final Predicate<GroundItem> base = baseRangeAndOwnershipFilter(params);
        final Predicate<GroundItem> combined = base.and(itemPredicate);

        final List<GroundItem> groundItems = getGroundItems().values().stream()
                .filter(combined)
                .collect(Collectors.toList());

        if (groundItems.size() < params.getMinItems()) return false;

        if (params.isDelayedLooting()) {
            final GroundItem soonest = groundItems.stream()
                    .min(Comparator.comparingInt(Rs2GroundItem::calculateDespawnTime))
                    .orElse(null);
            if (soonest == null) return false;
            if (calculateDespawnTime(soonest) > DESPAWN_DELAY_THRESHOLD_TICKS) return false;
        }

        return runWhilePaused(() -> {
            for (GroundItem gi : groundItems) {
                if (gi.getQuantity() < params.getMinQuantity()) continue;
                if (!passesIgnoredNames(gi, ignoredLower)) continue;
                if (!ensureSpaceFor(gi, params)) continue;
                coreLoot(gi);
            }
            return validateLoot(combined);
        });
    }

    private static Set<String> toLowerTrimmedSet(String[] arr) {
        if (arr == null || arr.length == 0) return Collections.emptySet();
        Set<String> out = new HashSet<>(arr.length);
        for (String s : arr) {
            if (s != null) {
                final String t = s.trim().toLowerCase();
                if (!t.isEmpty()) out.add(t);
            }
        }
        return out;
    }


    public static boolean lootItemBasedOnValue(LootingParameters params) {
        Predicate<GroundItem> byValue = gi -> {
            final int qty = Math.max(1, gi.getQuantity());
            final int price = gi.getGePrice();
            return price > params.getMinValue() && (price / qty) < params.getMaxValue();
        };

        final Set<String> ignoredLower = toLowerTrimmedSet(params.getIgnoredNames());
        return lootWithFilter(params, byValue, ignoredLower);
    }

    public static boolean lootItemsBasedOnNames(LootingParameters params) {
        final Set<String> needles = toLowerTrimmedSet(params.getNames());
        if (needles.isEmpty()) return false;

        Predicate<GroundItem> byNames = gi -> {
            final String n = gi.getName() == null ? "" : gi.getName().trim().toLowerCase();
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
        final Predicate<GroundItem> filter = groundItem ->
                groundItem.getLocation().equals(location) && groundItem.getItemId() == itemId;

        List<GroundItem> groundItems = getGroundItems().values().stream()
                .filter(filter)
                .collect(Collectors.toList());

        return runWhilePaused(() -> {
            for (GroundItem groundItem : groundItems) {
                coreLoot(groundItem);
            }
            return validateLoot(filter);
        });
    }



    private static boolean hasLootableItems(Predicate<GroundItem> filter) {
        List<GroundItem> groundItems = getGroundItems().values().stream()
                .filter(filter)
                .collect(Collectors.toList());

        return !groundItems.isEmpty();
    }

    public static boolean isItemBasedOnValueOnGround(int value, int range) {
        return Arrays.stream(Rs2GroundItem.getAll(range)).anyMatch(rs2Item -> {
            final long totalPrice = (long) Microbot.getClientThread().runOnClientThreadOptional(() ->
                    Microbot.getItemManager().getItemPrice(rs2Item.getItem().getId()) * rs2Item.getTileItem().getQuantity()).orElse(0);
            return totalPrice >= value;
        });
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
