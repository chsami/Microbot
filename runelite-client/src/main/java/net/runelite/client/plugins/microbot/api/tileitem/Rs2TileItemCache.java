package net.runelite.client.plugins.microbot.api.tileitem;

import net.runelite.api.Player;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Cache for ground items (tile items) in the current scene.
 * Uses polling-based approach to ensure reliability, as ItemSpawned/ItemDespawned events
 * are not always triggered consistently.
 */
@Singleton
public class Rs2TileItemCache {

    private static int lastUpdateTick = 0;
    private static List<Rs2TileItemModel> groundItems = new ArrayList<>();

    public Rs2TileItemQueryable query() {
        return new Rs2TileItemQueryable();
    }

    /**
     * Get all ground items in the current scene.
     * Refreshes the cache once per game tick by polling all tiles.
     * This ensures reliability even when ItemSpawned/ItemDespawned events don't fire.
     *
     * @return Stream of Rs2TileItemModel
     */
    public static Stream<Rs2TileItemModel> getGroundItemsStream() {
        // Only refresh once per tick to avoid unnecessary scanning
        if (lastUpdateTick >= Microbot.getClient().getTickCount()) {
            return groundItems.stream();
        }

        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null) return Stream.empty();

        List<Rs2TileItemModel> result = new ArrayList<>();

        // Get all tiles in current plane
        var tileValues = Microbot.getClient().getTopLevelWorldView().getScene().getTiles()[Microbot.getClient().getTopLevelWorldView().getPlane()];

        for (Tile[] tileRow : tileValues) {
            for (Tile tile : tileRow) {
                if (tile == null) continue;

                List<TileItem> items = tile.getGroundItems();
                if (items == null || items.isEmpty()) continue;

                // Add all items from this tile to the result
                for (TileItem item : items) {
                    if (item != null) {
                        result.add(new Rs2TileItemModel(tile, item));
                    }
                }
            }
        }

        groundItems = result;
        lastUpdateTick = Microbot.getClient().getTickCount();
        return result.stream();
    }
}
