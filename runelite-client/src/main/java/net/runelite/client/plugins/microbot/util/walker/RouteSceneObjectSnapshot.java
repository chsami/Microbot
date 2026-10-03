package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** A bounded, single-client-thread read of objects beside the upcoming raw route. */
final class RouteSceneObjectSnapshot {

    private static final int MAX_EDGES = 17;
    private static final int MAX_RADIUS = 13;

    private RouteSceneObjectSnapshot() {
    }

    static final class Entry {
        private final TileObject object;
        private final WorldPoint location;

        private Entry(TileObject object, WorldPoint location) {
            this.object = object;
            this.location = location;
        }

        TileObject object() {
            return object;
        }

        WorldPoint location() {
            return location;
        }
    }

    /**
     * Read the wall and game objects around the next route edges. Both references and world
     * locations are captured in one client-thread invocation; no scene-wide traversal is needed.
     * The caller must use a fresh snapshot for each pass because doors can transform in place.
     */
    static List<Entry> capture(List<WorldPoint> rawPath, int startEdge, int maxEdges,
                               WorldPoint playerLoc, int radiusTiles) {
        List<WorldPoint> nearbyTiles = nearbyRouteTiles(rawPath, startEdge, maxEdges,
                playerLoc, radiusTiles);
        if (nearbyTiles.isEmpty()) {
            return Collections.emptyList();
        }
        return Microbot.getClientThread().invoke(() -> captureOnClientThread(nearbyTiles,
                playerLoc, Math.min(radiusTiles, MAX_RADIUS)));
    }

    /** Tile locations that could hold a door beside an in-range route edge. */
    static List<WorldPoint> nearbyRouteTiles(List<WorldPoint> rawPath, int startEdge, int maxEdges,
                                             WorldPoint playerLoc, int radiusTiles) {
        if (rawPath == null || rawPath.size() < 2 || playerLoc == null
                || startEdge < 0 || startEdge >= rawPath.size() - 1
                || maxEdges <= 0 || radiusTiles < 0) {
            return Collections.emptyList();
        }
        int radius = Math.min(radiusTiles, MAX_RADIUS);
        int endExclusive = Math.min(rawPath.size() - 1, startEdge + Math.min(maxEdges, MAX_EDGES));
        Set<WorldPoint> tiles = new LinkedHashSet<>();
        for (int edge = startEdge; edge < endExclusive; edge++) {
            addNearEndpoint(tiles, rawPath.get(edge), playerLoc, radius);
            addNearEndpoint(tiles, rawPath.get(edge + 1), playerLoc, radius);
        }
        return List.copyOf(tiles);
    }

    private static void addNearEndpoint(Set<WorldPoint> tiles, WorldPoint endpoint,
                                        WorldPoint playerLoc, int radius) {
        if (endpoint == null || endpoint.getPlane() != playerLoc.getPlane()
                || endpoint.distanceTo2D(playerLoc) > radius + 1) {
            return;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                WorldPoint candidate = new WorldPoint(endpoint.getX() + dx,
                        endpoint.getY() + dy, endpoint.getPlane());
                if (candidate.distanceTo2D(playerLoc) <= radius) {
                    tiles.add(candidate);
                }
            }
        }
    }

    private static List<Entry> captureOnClientThread(List<WorldPoint> nearbyTiles,
                                                      WorldPoint playerLoc, int radius) {
        Client client = Microbot.getClient();
        WorldView worldView = client == null ? null : client.getTopLevelWorldView();
        if (worldView == null || worldView.isInstance()) {
            return Collections.emptyList();
        }
        Scene scene = worldView.getScene();
        Tile[][][] tiles = scene == null ? null : scene.getTiles();
        if (tiles == null) {
            return Collections.emptyList();
        }
        Set<TileObject> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Entry> objects = new ArrayList<>();
        for (WorldPoint point : nearbyTiles) {
            LocalPoint local = LocalPoint.fromWorld(worldView, point);
            if (local == null) {
                continue;
            }
            int x = local.getSceneX();
            int y = local.getSceneY();
            // Bridge tiles may have an object on a lower scene plane even when its world
            // location is on the player's plane. The location check below removes others.
            for (int plane = 0; plane <= worldView.getPlane() && plane < tiles.length; plane++) {
                Tile[][] planeTiles = tiles[plane];
                if (planeTiles == null || x < 0 || x >= planeTiles.length
                        || planeTiles[x] == null || y < 0 || y >= planeTiles[x].length) {
                    continue;
                }
                Tile tile = planeTiles[x][y];
                if (tile == null) {
                    continue;
                }
                addObject(objects, seen, tile.getWallObject(), playerLoc, radius);
                GameObject[] gameObjects = tile.getGameObjects();
                if (gameObjects != null) {
                    for (GameObject gameObject : gameObjects) {
                        addObject(objects, seen, gameObject, playerLoc, radius);
                    }
                }
            }
        }
        return List.copyOf(objects);
    }

    private static void addObject(List<Entry> objects, Set<TileObject> seen, TileObject object,
                                  WorldPoint playerLoc, int radius) {
        if (object == null || !seen.add(object)) {
            return;
        }
        WorldPoint location = object.getWorldLocation();
        if (location != null && location.getPlane() == playerLoc.getPlane()
                && location.distanceTo2D(playerLoc) <= radius) {
            objects.add(new Entry(object, location));
        }
    }
}
