package net.runelite.client.plugins.microbot.api.player;

import net.runelite.client.plugins.microbot.api.player.models.Rs2PlayerModel;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Example usage of the Player API
 *
 * This demonstrates how to query players using the new API structure:
 * - Rs2PlayerCache: Caches players for efficient querying
 * - Rs2PlayerQueryable: Provides a fluent interface for filtering and querying players
 */
public class PlayerApiExample {

    public static void examples() {
        // Create a new cache instance
        Rs2PlayerCache cache = new Rs2PlayerCache();

        // Example 1: Get the nearest player (excluding local player)
        Rs2PlayerModel nearestPlayer = cache.query().nearest();

        // Example 2: Get the nearest player within 10 tiles
        Rs2PlayerModel nearestPlayerWithinRange = cache.query().nearest(10);

        // Example 3: Find a player by name
        Rs2PlayerModel playerByName = cache.query().withName("PlayerName");

        // Example 4: Find a player by multiple names
        Rs2PlayerModel anyOfThesePlayers = cache.query().withNames("Player1", "Player2", "Player3");

        // Example 5: Find a player by ID
        Rs2PlayerModel playerById = cache.query().withId(12345);

        // Example 6: Find a player by multiple IDs
        Rs2PlayerModel playerByIds = cache.query().withIds(12345, 67890, 11111);

        // Example 8: Find all friends
        List<Rs2PlayerModel> friends = cache.query()
                .where(Rs2PlayerModel::isFriend)
                .toList();

        // Example 9: Find all clan members
        List<Rs2PlayerModel> clanMembers = cache.query()
                .where(Rs2PlayerModel::isClanMember)
                .toList();

        // Example 10: Find all friends chat members
        List<Rs2PlayerModel> fcMembers = cache.query()
                .where(Rs2PlayerModel::isFriendsChatMember)
                .toList();

        // Example 11: Find players in combat (with health bar visible)
        List<Rs2PlayerModel> playersInCombat = cache.query()
                .where(player -> player.getHealthRatio() != -1)
                .toList();

        // Example 12: Find nearest player who is not in your clan
        Rs2PlayerModel nearestNonClan = cache.query()
                .where(player -> !player.isClanMember())
                .where(player -> !player.isFriend())
                .nearest();

        // Example 13: Find players with skull (PvP)
        List<Rs2PlayerModel> skulledPlayers = cache.query()
                .where(player -> player.getSkullIcon() != -1)
                .toList();

        // Example 14: Find players with prayer active (overhead icon)
        List<Rs2PlayerModel> playersWithPrayer = cache.query()
                .where(player -> player.getOverheadIcon() != null)
                .toList();

        // Example 15: Find nearest player that is animating (doing something)
        Rs2PlayerModel animatingPlayer = cache.query()
                .where(player -> player.getAnimation() != -1)
                .nearest();

        // Example 16: Complex query - Find nearest low health enemy player within 5 tiles
        Rs2PlayerModel target = cache.query()
                .where(player -> !player.isFriend())
                .where(player -> !player.isClanMember())
                .where(player -> player.getHealthRatio() > 0)
                .where(player -> player.getHealthRatio() < player.getHealthScale() / 2)
                .nearest(5);

        // Example 17: Find all players on the same team (Castle Wars, etc.)
        int myTeam = 1; // Example team ID
        List<Rs2PlayerModel> teammates = cache.query()
                .where(player -> player.getTeam() == myTeam)
                .toList();

        // Example 18: Static method to get stream directly
        Rs2PlayerModel firstPlayer = Rs2PlayerCache.getPlayersStream()
                .filter(player -> player.getName() != null)
                .findFirst()
                .orElse(null);

        // Example 19: Get all players
        List<Rs2PlayerModel> allPlayersIncludingMe = Rs2PlayerCache.getPlayersStream()
                .collect(Collectors.toList());

        // Example 20: Find players by partial name match
        Rs2PlayerModel playerContainingName = cache.query()
                .where(player -> player.getName() != null &&
                       player.getName().toLowerCase().contains("iron"))
                .first();
    }
}
