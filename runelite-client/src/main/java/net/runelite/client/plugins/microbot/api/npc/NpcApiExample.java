package net.runelite.client.plugins.microbot.api.npc;

import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;

import java.util.List;

/**
 * Example usage of the NPC API
 *
 * This demonstrates how to query NPCs using the new API structure:
 * - Rs2NpcCache: Caches NPCs for efficient querying
 * - Rs2NpcQueryable: Provides a fluent interface for filtering and querying NPCs
 */
public class NpcApiExample {

    public static void examples() {
        // Create a new cache instance
        Rs2NpcCache cache = new Rs2NpcCache();

        // Example 1: Get the nearest NPC
        Rs2NpcModel nearestNpc = cache.query().nearest();

        // Example 2: Get the nearest NPC within 10 tiles
        Rs2NpcModel nearestNpcWithinRange = cache.query().nearest(10);

        // Example 3: Find an NPC by name
        Rs2NpcModel goblin = cache.query().withName("Goblin").nearest();

        // Example 4: Find an NPC by multiple names
        Rs2NpcModel enemy = cache.query().withNames("Goblin", "Guard", "Dark wizard").nearest();

        // Example 5: Find an NPC by ID
        Rs2NpcModel npcById = cache.query().withId(1234).nearest();

        // Example 6: Find an NPC by multiple IDs
        Rs2NpcModel npcByIds = cache.query().withIds(1234, 5678, 9012).nearest();

        // Example 7: Get all NPCs with a custom filter
        Rs2NpcModel attackingNpc = cache.query()
                .where(npc -> npc.isInteractingWithPlayer())
                .first();

        // Example 8: Chain multiple filters
        Rs2NpcModel lowHealthEnemy = cache.query()
                .where(npc -> npc.getName() != null && npc.getName().contains("Goblin"))
                .where(npc -> npc.getHealthPercentage() < 50)
                .nearest();

        // Example 9: Get all NPCs matching criteria as a list
        List<Rs2NpcModel> allGoblins = cache.query()
                .where(npc -> npc.getName() != null && npc.getName().equalsIgnoreCase("Goblin"))
                .toList();

        // Example 10: Complex query - Find nearest low health NPC within 15 tiles
        Rs2NpcModel target = cache.query()
                .where(npc -> npc.getHealthPercentage() > 0 && npc.getHealthPercentage() < 30)
                .where(npc -> !npc.isDead())
                .nearest(15);

        // Example 11: Find NPCs that are moving
        List<Rs2NpcModel> movingNpcs = cache.query()
                .where(Rs2NpcModel::isMoving)
                .toList();

        // Example 12: Static method to get stream directly
        Rs2NpcModel firstNpc = Rs2NpcCache.getNpcsStream()
                .filter(npc -> npc.getName() != null)
                .findFirst()
                .orElse(null);
    }
}
