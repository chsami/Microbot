package net.runelite.client.plugins.microbot.api.tileitem;

import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;

import javax.inject.Inject;
import java.util.List;

/**
 * Example usage of the Ground Item API
 * This demonstrates how to query ground items using the new API structure:
 * - Rs2GroundItemCache: Caches ground items for efficient querying
 * - Rs2GroundItemQueryable: Provides a fluent interface for filtering and querying ground items
 */
public class TileItemApiExample {

    @Inject
    Rs2TileItemCache cache;

    public void examples() {
        // Create a new cache instance

        // Example 1: Get the nearest ground item
        Rs2TileItemModel nearestItem = cache.query().nearest();

        // Example 2: Get the nearest ground item within 10 tiles
        Rs2TileItemModel nearestItemWithinRange = cache.query().nearest(10);

        // Example 3: Find a ground item by name
        Rs2TileItemModel coins = cache.query().withName("Coins").nearest();

        // Example 4: Find a ground item by multiple names
        Rs2TileItemModel loot = cache.query().withNames("Dragon bones", "Dragon scale", "Dragon dagger").nearest();

        // Example 5: Find a ground item by ID
        Rs2TileItemModel itemById = cache.query().withId(995).nearest(); // Coins

        // Example 6: Find a ground item by multiple IDs
        Rs2TileItemModel itemByIds = cache.query().withIds(995, 526, 537).nearest(); // Coins, Bones, Dragon bones

        // Example 7: Get all ground items worth more than 1000 gp
        List<Rs2TileItemModel> valuableItems = cache.query()
                .where(item -> item.getTotalValue() >= 1000)
                .toList();

        // Example 8: Find nearest lootable item
        Rs2TileItemModel lootableItem = cache.query()
                .where(Rs2TileItemModel::isLootAble)
                .nearest();

        // Example 9: Find items owned by player
        List<Rs2TileItemModel> ownedItems = cache.query()
                .where(Rs2TileItemModel::isOwned)
                .toList();

        // Example 10: Find stackable items
        List<Rs2TileItemModel> stackableItems = cache.query()
                .where(Rs2TileItemModel::isStackable)
                .toList();

        // Example 11: Find noted items
        List<Rs2TileItemModel> notedItems = cache.query()
                .where(Rs2TileItemModel::isNoted)
                .toList();

        // Example 13: Find items about to despawn
        List<Rs2TileItemModel> despawningItems = cache.query()
                .where(item -> item.willDespawnWithin(30))
                .toList();

        // Example 16: Complex query - Find nearest valuable lootable item within 15 tiles
        Rs2TileItemModel target = cache.query()
                .where(Rs2TileItemModel::isLootAble)
                .where(item -> item.getTotalGeValue() >= 5000)
                .where(item -> item.isDespawned())
                .nearest(15);

        // Example 17: Find items by quantity
        List<Rs2TileItemModel> largeStacks = cache.query()
                .where(item -> item.getQuantity() >= 100)
                .toList();

        // Example 18: Find tradeable items
        List<Rs2TileItemModel> tradeableItems = cache.query()
                .where(Rs2TileItemModel::isTradeable)
                .toList();

        // Example 19: Find members items
        List<Rs2TileItemModel> membersItems = cache.query()
                .where(Rs2TileItemModel::isMembers)
                .toList();

        // Example 20: Static method to get stream directly
        Rs2TileItemModel firstItem = Rs2TileItemCache.getGroundItemsStream()
                .filter(item -> item.getName() != null)
                .findFirst()
                .orElse(null);

        // Example 21: Find items by partial name match
        List<Rs2TileItemModel> herbItems = cache.query()
                .where(item -> item.getName() != null &&
                       item.getName().toLowerCase().contains("herb"))
                .toList();
    }
}
