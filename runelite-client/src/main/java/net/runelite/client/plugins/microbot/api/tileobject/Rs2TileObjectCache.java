package net.runelite.client.plugins.microbot.api.tileobject;

import net.runelite.api.GameObject;
import net.runelite.api.Player;
import net.runelite.api.Tile;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class Rs2TileObjectCache {

    private static int lastUpdateObjects = 0;
    private static List<Rs2TileObjectModel> tileObjects = new ArrayList<>();

    public Rs2TileObjectQueryable query() {
        return new Rs2TileObjectQueryable();
    }

    /**
     * Get all tile objects in the current scene
     * @return Stream of Rs2TileObjectModel
     */
    public static Stream<Rs2TileObjectModel> getObjectsStream() {

        if (lastUpdateObjects >= Microbot.getClient().getTickCount()) {
            return tileObjects.stream();
        }

        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null) return Stream.empty();

        List<Rs2TileObjectModel> result = new ArrayList<>();

        var tileValues = Microbot.getClient().getTopLevelWorldView().getScene().getTiles()[Microbot.getClient().getTopLevelWorldView().getPlane()];

        for (Tile[] tileValue : tileValues) {
            for (Tile tile : tileValue) {
                if (tile == null) continue;

                if (tile.getGameObjects() != null) {
                    for (GameObject gameObject : tile.getGameObjects()) {
                        if (gameObject == null) continue;
                        if (gameObject.getSceneMinLocation().equals(tile.getSceneLocation())) {
                            result.add(new Rs2TileObjectModel(gameObject));
                        }
                    }
                }
                if (tile.getGroundObject() != null) {
                    result.add(new Rs2TileObjectModel(tile.getGroundObject()));
                }
                if (tile.getWallObject() != null) {
                    result.add(new Rs2TileObjectModel(tile.getWallObject()));
                }
                if (tile.getDecorativeObject() != null)  {
                    result.add(new Rs2TileObjectModel(tile.getDecorativeObject()));
                }
            }
        }

        tileObjects = result;
        lastUpdateObjects = Microbot.getClient().getTickCount();
        return result.stream();
    }
}
