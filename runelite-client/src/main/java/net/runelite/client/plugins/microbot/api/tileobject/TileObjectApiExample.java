package net.runelite.client.plugins.microbot.api.tileobject;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;

import java.util.List;

/**
 * Example usage of the Tile Object Cache & Queryable API
 *
 * IMPORTANT: Always use Microbot.getRs2TileObjectCache().query() to create queries.
 * Never instantiate Rs2TileObjectQueryable directly.
 *
 * - Rs2TileObjectCache: Singleton cache accessed via Microbot.getRs2TileObjectCache()
 * - query(): Returns a fluent queryable interface for filtering tile objects
 */
public class TileObjectApiExample {

    public static void examples() {
        Rs2TileObjectCache cache = Microbot.getRs2TileObjectCache();

        // Example 1: Get the nearest tile object
        Rs2TileObjectModel nearestObject = cache.query().nearest();

        // Example 2: Get the nearest tile object within 10 tiles
        Rs2TileObjectModel nearestObjectWithinRange = cache.query().nearest(10);

        // Example 3: Find a tile object by name
        Rs2TileObjectModel tree = cache.query().withName("Oak tree").nearest();

        // Example 4: Find a tile object by multiple names
        Rs2TileObjectModel bankObj = cache.query().withNames("Bank booth", "Bank chest", "Bank").nearest();

        // Example 5: Find a tile object by ID
        Rs2TileObjectModel objectById = cache.query().withId(1234).nearest();

        // Example 6: Find a tile object by multiple IDs
        Rs2TileObjectModel objectByIds = cache.query().withIds(1234, 5678, 9012).nearest();

        // Example 7: Get all tile objects matching a partial name
        List<Rs2TileObjectModel> doorObjects = cache.query()
                .where(obj -> obj.getName() != null && obj.getName().toLowerCase().contains("door"))
                .toList();

        // Example 8: Find the nearest reachable tile object
        Rs2TileObjectModel reachableObject = cache.query()
                .withName("Ladder")
                .nearestReachable();

        // Example 9: Find the nearest reachable tile object within 15 tiles
        Rs2TileObjectModel nearbyReachable = cache.query()
                .withName("Furnace")
                .nearestReachable(15);

        // Example 10: Get all tile objects as a list
        List<Rs2TileObjectModel> allObjects = cache.query().toList();

        // Example 11: Count tile objects matching criteria
        int treeCount = cache.query()
                .where(obj -> obj.getName() != null && obj.getName().contains("tree"))
                .count();

        // Example 12: Find the nearest bank and interact with it
        boolean clickedBank = cache.query()
                .withNames("Bank booth", "Bank chest", "Bank")
                .interact("Bank");

        // Example 13: Find the nearest object by name and interact with a specific action
        boolean clickedOpen = cache.query()
                .withName("Door")
                .interact("Open");

        // Example 14: Find objects within a specific distance and interact
        boolean clickedChop = cache.query()
                .withNames("Tree", "Oak tree", "Willow tree")
                .interact("Chop down", 5);

        // Example 15: Direct stream access when needed
        Rs2TileObjectModel firstObject = cache.getStream()
                .filter(obj -> obj.getName() != null)
                .findFirst()
                .orElse(null);
    }
}
