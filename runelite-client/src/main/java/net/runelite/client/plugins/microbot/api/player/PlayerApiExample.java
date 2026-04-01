package net.runelite.client.plugins.microbot.api.player;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.player.models.Rs2PlayerModel;

import java.util.List;

/**
 * Example usage of the Player Cache & Queryable API
 *
 * IMPORTANT: Always use Microbot.getRs2PlayerCache().query() to create queries.
 * Never instantiate Rs2PlayerQueryable directly.
 *
 * - Rs2PlayerCache: Singleton cache accessed via Microbot.getRs2PlayerCache()
 * - query(): Returns a fluent queryable interface for filtering players
 */
public class PlayerApiExample {

    public static void examples() {
        Rs2PlayerCache cache = Microbot.getRs2PlayerCache();

        // Example 1: Get the nearest player
        Rs2PlayerModel nearestPlayer = cache.query().nearest();

        // Example 2: Get the nearest player within 10 tiles
        Rs2PlayerModel nearestPlayerWithinRange = cache.query().nearest(10);

        // Example 3: Find a player by name
        Rs2PlayerModel specificPlayer = cache.query().withName("Zezima").nearest();

        // Example 4: Find a player by multiple names
        Rs2PlayerModel friend = cache.query().withNames("Alice", "Bob", "Charlie").nearest();

        // Example 5: Find a player by ID
        Rs2PlayerModel playerById = cache.query().withId(1234).nearest();

        // Example 6: Get all players within 5 tiles as a list
        List<Rs2PlayerModel> nearbyPlayers = cache.query()
                .within(5)
                .toList();

        // Example 7: Find players that are animating (e.g. skilling or in combat)
        List<Rs2PlayerModel> animatingPlayers = cache.query()
                .where(player -> player.getAnimation() != -1)
                .toList();

        // Example 8: Find players that are moving
        List<Rs2PlayerModel> movingPlayers = cache.query()
                .where(player -> player.getPoseAnimation() != player.getIdlePoseAnimation())
                .toList();

        // Example 9: Count players in the current area
        int playerCount = cache.query().count();

        // Example 10: Count players within 15 tiles
        int nearbyCount = cache.query().within(15).count();

        // Example 11: Find the nearest player currently interacting with something
        Rs2PlayerModel interactingPlayer = cache.query()
                .where(player -> player.isInteracting())
                .nearest();

        // Example 12: Get all players as a list
        List<Rs2PlayerModel> allPlayers = cache.query().toList();

        // Example 13: Direct stream access when needed
        Rs2PlayerModel firstPlayer = cache.getStream()
                .filter(player -> player.getName() != null)
                .findFirst()
                .orElse(null);

        // Example 14: Find players with a specific combat level
        List<Rs2PlayerModel> highLevelPlayers = cache.query()
                .where(player -> player.getCombatLevel() >= 100)
                .toList();

        // Example 15: Find the nearest reachable player
        Rs2PlayerModel reachablePlayer = cache.query().nearestReachable();

        // Example 16: Find friends nearby
        List<Rs2PlayerModel> friends = cache.query()
                .where(Rs2PlayerModel::isFriend)
                .within(20)
                .toList();

        // Example 17: Find clan members nearby
        List<Rs2PlayerModel> clanMembers = cache.query()
                .where(Rs2PlayerModel::isClanMember)
                .toList();

        // Example 18: Find players with a skull (PvP/risk)
        List<Rs2PlayerModel> skulledPlayers = cache.query()
                .where(player -> player.getSkullIcon() != -1)
                .toList();

        // Example 19: Find the nearest skulled player within 10 tiles
        Rs2PlayerModel nearestSkulled = cache.query()
                .where(player -> player.getSkullIcon() != -1)
                .nearest(10);
    }
}
