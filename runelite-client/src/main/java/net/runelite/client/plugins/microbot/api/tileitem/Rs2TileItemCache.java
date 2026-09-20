package net.runelite.client.plugins.microbot.api.tileitem;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.WorldView;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Cache for tile items in the current scene.
 * Uses polling-based approach to ensure reliability, as ItemSpawned/ItemDespawned events
 * are not always triggered consistently.
 */
@Singleton
public final class Rs2TileItemCache {

    private final Client client;
    private final ClientThread clientThread;

    private int lastUpdateTick = 0;
    private List<Rs2TileItemModel> tileItems = new ArrayList<>();

    @Inject
    public Rs2TileItemCache(Client client, ClientThread clientThread) {
        this.client = client;
        this.clientThread = clientThread;
    }

    public Rs2TileItemQueryable query() {
        return new Rs2TileItemQueryable();
    }

    /**
     * Get all tile items in the current scene across all world views.
     * Refreshes the cache once per game tick by polling all tiles.
     * This ensures reliability even when ItemSpawned/ItemDespawned events don't fire.
     *
     * @return Stream of Rs2TileItemModel
     */
    public Stream<Rs2TileItemModel> getStream() {
        if (lastUpdateTick >= client.getTickCount()) {
            return tileItems.stream();
        }

        Player player = client.getLocalPlayer();
        if (player == null) return Stream.empty();

        List<Rs2TileItemModel> result = new ArrayList<>();

        for (var id : Microbot.getWorldViewIds()) {
            WorldView worldView = client.getWorldView(id);
            if (worldView == null) {
                continue;
            }

            Tile[][] tiles = worldView.getScene().getTiles()[worldView.getPlane()];
            for (Tile[] tileRow : tiles) {
                for (Tile tile : tileRow) {
                    if (tile == null) continue;

                    List<TileItem> items = tile.getGroundItems();
                    if (items == null || items.isEmpty()) continue;

                    for (TileItem item : items) {
                        if (item != null) {
                            result.add(new Rs2TileItemModel(tile, item, worldView));
                        }
                    }
                }
            }
        }

        tileItems = result;
        lastUpdateTick = client.getTickCount();
        return result.stream();
    }

    /**
     * @deprecated Use {@link Microbot#getRs2TileItemCache()}.getStream() instead
     */
    @Deprecated(since = "2.1.8", forRemoval = true)
    public static Stream<Rs2TileItemModel> getTileItemsStream() {
        return Microbot.getRs2TileItemCache().getStream();
    }
}
