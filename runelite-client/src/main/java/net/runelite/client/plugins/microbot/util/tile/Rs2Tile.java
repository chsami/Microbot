package net.runelite.client.plugins.microbot.util.tile;

import lombok.Getter;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.devtools.MovementFlag;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.apache.commons.lang3.tuple.MutablePair;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Rs2Tile {

    @Getter
    public static List<MutablePair<WorldPoint, Integer>> dangerousGraphicsObjectTiles = new ArrayList<>();

    private static ScheduledExecutorService tileExecutor;


    public static void init() {
        if (tileExecutor == null) {
            tileExecutor = Executors.newSingleThreadScheduledExecutor();
            tileExecutor.scheduleWithFixedDelay(() -> {
                if (dangerousGraphicsObjectTiles.isEmpty()) return;

                for (MutablePair<WorldPoint, Integer> dangerousTile : dangerousGraphicsObjectTiles) {
                    dangerousTile.setValue(dangerousTile.getValue() - 600);
                }
                dangerousGraphicsObjectTiles = dangerousGraphicsObjectTiles.stream().filter(x -> x.getValue() > 0).collect(Collectors.toList());
            }, 0, 600, TimeUnit.MILLISECONDS);
        }
    }


    public static void addDangerousGraphicsObjectTile(GraphicsObject graphicsObject, int time) {
        WorldPoint worldPoint;

        if (Microbot.getClient().getTopLevelWorldView().getScene().isInstance()) {
            worldPoint = WorldPoint.fromLocalInstance(Microbot.getClient(), graphicsObject.getLocation());
        } else {
            worldPoint = WorldPoint.fromLocal(Microbot.getClient(), graphicsObject.getLocation());
        }

        if (worldPoint == null) return;

        final MutablePair<WorldPoint, Integer> dangerousTile = MutablePair.of(worldPoint, time);

        dangerousGraphicsObjectTiles.add(dangerousTile);

        if (Rs2Player.getWorldLocation().equals(worldPoint)) {
            Microbot.getClientThread().runOnSeperateThread(() -> {
                final WorldPoint safeTile = Rs2Tile.getSafeTile();
                System.out.println(safeTile);
                Rs2Walker.walkFastCanvas(safeTile);
                return true;
            });
        }
    }

    /**
     * Returns a safe tile based on dangerous tiles
     *
     * @return list of safe tile, sorted on the closest tile to the player
     */
    public static List<WorldPoint> getSafeTiles(int radius) {
        List<WorldPoint> safeTiles = new ArrayList<>();

        for (WorldPoint walkableTile : getWalkableTilesAroundPlayer(radius)) {
            boolean isDangerousTile = dangerousGraphicsObjectTiles.stream().anyMatch(x -> x.getKey().equals(walkableTile));
            if (isDangerousTile) continue;
            safeTiles.add(walkableTile);
        }
        return safeTiles;
    }

    public static WorldPoint getSafeTile() {

        List<WorldPoint> safeTiles = getSafeTiles(4)
                .stream()
                .sorted(Comparator.comparingInt(value -> value.distanceTo(Rs2Player.getWorldLocation()))).collect(Collectors.toList());
        if (safeTiles.isEmpty()) return null;

        return safeTiles.get(0);
    }

    public static boolean isWalkable(Tile tile) {
        Client client = Microbot.getClient();
        if (client.getCollisionMaps() != null) {
            int[][] flags = client.getCollisionMaps()[client.getPlane()].getFlags();
            int data = flags[tile.getSceneLocation().getX()][tile.getSceneLocation().getY()];

            Set<MovementFlag> movementFlags = MovementFlag.getSetFlags(data);

            if (movementFlags.isEmpty()) {
                return true;
            }
            return false;
        }
        return true;
    }

    public static boolean isWalkable(LocalPoint localPoint) {
        if (localPoint == null)
            return true;

        Client client = Microbot.getClient();
        if (client.getCollisionMaps() != null) {
            int[][] flags = client.getCollisionMaps()[client.getPlane()].getFlags();
            int data = flags[localPoint.getSceneX()][localPoint.getSceneY()];

            Set<MovementFlag> movementFlags = MovementFlag.getSetFlags(data);

            if (movementFlags.contains(MovementFlag.BLOCK_MOVEMENT_FULL)
                    || movementFlags.contains(MovementFlag.BLOCK_MOVEMENT_FLOOR))
                return false;
        }
        return true;
    }

    public static List<WorldPoint> getWalkableTilesAroundPlayer(int radius) {
        return getWalkableTilesAroundTile(Rs2Player.getWorldLocation(), radius);
    }

    public static List<WorldPoint> getWalkableTilesAroundTile(WorldPoint point, int radius) {
        List<WorldPoint> worldPoints = new ArrayList<>();
        LocalPoint playerLocalPosition = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), point);

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                if (dx == 0 && dy == 0) continue; // Skip the player's current position
                LocalPoint localPoint;
                WorldPoint worldPoint;
                localPoint = new LocalPoint(playerLocalPosition.getX() + (dx * 128), playerLocalPosition.getY() + (dy * 128), -1);
                if (Microbot.getClient().getTopLevelWorldView().getScene().isInstance()) {
                    worldPoint = WorldPoint.fromLocalInstance(Microbot.getClient(), localPoint);
                } else {
                    worldPoint = WorldPoint.fromLocal(Microbot.getClient(), localPoint);
                }
                if (!isWalkable(localPoint)) continue;

                worldPoints.add(worldPoint);
            }
        }
        return worldPoints;
    }

    public static HashMap<WorldPoint, Integer> getReachableTilesFromTile(WorldPoint tile, int distance) {
        var tileDistances = new HashMap<WorldPoint, Integer>();
        tileDistances.put(tile, 0);

        for (int i = 0; i < distance + 1; i++) {
            int dist = i;
            for (var kvp : tileDistances.entrySet().stream().filter(x -> x.getValue() == dist).collect(Collectors.toList())) {
                var point = kvp.getKey();
                LocalPoint localPoint;
                if (Microbot.getClient().isInInstancedRegion()) {
                    var worldPoint = WorldPoint.toLocalInstance(Microbot.getClient(), point).stream().findFirst().get();
                    localPoint = LocalPoint.fromWorld(Microbot.getClient(), worldPoint);
                } else
                    localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), point);

                if (Microbot.getClient().getCollisionMaps() != null && localPoint != null) {
                    int[][] flags = Microbot.getClient().getCollisionMaps()[Microbot.getClient().getPlane()].getFlags();
                    int data = flags[localPoint.getSceneX()][localPoint.getSceneY()];

                    Set<MovementFlag> movementFlags = MovementFlag.getSetFlags(data);

                    if (movementFlags.contains(MovementFlag.BLOCK_MOVEMENT_FULL)
                            || movementFlags.contains(MovementFlag.BLOCK_MOVEMENT_FLOOR)) {
                        tileDistances.remove(point);
                        continue;
                    }

                    if (kvp.getValue() >= distance)
                        continue;

                    if (!movementFlags.contains(MovementFlag.BLOCK_MOVEMENT_EAST))
                        tileDistances.putIfAbsent(point.dx(1), dist + 1);
                    if (!movementFlags.contains(MovementFlag.BLOCK_MOVEMENT_WEST))
                        tileDistances.putIfAbsent(point.dx(-1), dist + 1);
                    if (!movementFlags.contains(MovementFlag.BLOCK_MOVEMENT_NORTH))
                        tileDistances.putIfAbsent(point.dy(1), dist + 1);
                    if (!movementFlags.contains(MovementFlag.BLOCK_MOVEMENT_SOUTH))
                        tileDistances.putIfAbsent(point.dy(-1), dist + 1);
                }
            }
        }

        return tileDistances;
    }

    public static List<LocalPoint> getTilesAroundPlayer(int radius) {
        List<LocalPoint> localPoints = new ArrayList<>();
        LocalPoint playerLocalPosition = Rs2Player.getLocalLocation();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                if (dx == 0 && dy == 0) continue; // Skip the player's current position
                LocalPoint localPoint;
                WorldPoint worldPoint;
                localPoint = new LocalPoint(playerLocalPosition.getX() + (dx * 128), playerLocalPosition.getY() + (dy * 128), -1);
                localPoints.add(localPoint);
            }
        }
        return localPoints;
    }

    public static boolean isTileReachable(WorldPoint targetPoint) {
        boolean[][] visited = new boolean[104][104];
        int[][] flags = Microbot.getClient().getCollisionMaps()[Microbot.getClient().getPlane()].getFlags();
        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        int startX = 0;
        int startY = 0;
        int startPoint = 0;
        if (Microbot.getClient().isInInstancedRegion()) {
            LocalPoint localPoint = Rs2Player.getLocalLocation();
             startX = localPoint.getSceneX();
             startY = localPoint.getSceneY();
             startPoint = (startX << 16) | startY;
        } else {
             startX = playerLoc.getX() - Microbot.getClient().getBaseX();
             startY = playerLoc.getY() - Microbot.getClient().getBaseY();
             startPoint = (startX << 16) | startY;
        }

        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(startPoint);
        visited[startX][startY] = true;

        while (!queue.isEmpty()) {
            int point = queue.poll();
            int x = point >> 16;
            int y = point & 0xFFFF;

            if (isWithinBounds(x, y)) {
                checkAndAddNeighbour(queue, visited, flags, x, y, -1, 0, CollisionDataFlag.BLOCK_MOVEMENT_WEST);
                checkAndAddNeighbour(queue, visited, flags, x, y, 1, 0, CollisionDataFlag.BLOCK_MOVEMENT_EAST);
                checkAndAddNeighbour(queue, visited, flags, x, y, 0, -1, CollisionDataFlag.BLOCK_MOVEMENT_SOUTH);
                checkAndAddNeighbour(queue, visited, flags, x, y, 0, 1, CollisionDataFlag.BLOCK_MOVEMENT_NORTH);
            }
        }

        return isVisited(targetPoint, visited);
    }

    public static boolean areSurroundingTilesWalkable(WorldPoint worldPoint, int sizeX, int sizeY) {
        for (int dx = -1; dx <= sizeX; dx++) {
            for (int dy = -1; dy <= sizeY; dy++) {
                // Skip the inside tiles, only check the border
                if (dx >= 0 && dx < sizeX && dy >= 0 && dy < sizeY) {
                    continue;
                }

                int checkX = worldPoint.getX() + dx;
                int checkY = worldPoint.getY() + dy;

                if (isTileReachable(new WorldPoint(checkX, checkY, worldPoint.getPlane()))) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean isWithinBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < 104 && y < 104;
    }

    private static void checkAndAddNeighbour(ArrayDeque<Integer> queue, boolean[][] visited, int[][] flags, int x, int y, int dx, int dy, int blockMovementFlag) {
        int nx = x + dx;
        int ny = y + dy;

        if (isWithinBounds(nx, ny) && !visited[nx][ny] && (flags[x][y] & blockMovementFlag) == 0 && (flags[nx][ny] & CollisionDataFlag.BLOCK_MOVEMENT_FULL) == 0) {
            queue.add((nx << 16) | ny);
            visited[nx][ny] = true;
        }
    }

    private static boolean isVisited(WorldPoint worldPoint, boolean[][] visited) {
        int baseX = 0;
        int baseY = 0;
        int x = 0;
        int y = 0;
        if (Microbot.getClient().isInInstancedRegion()) {
            LocalPoint localPoint = Rs2Player.getLocalLocation();
            x = localPoint.getSceneX();
            y = localPoint.getSceneY();
        } else {
             baseX = Microbot.getClient().getTopLevelWorldView().getBaseX();
             baseY = Microbot.getClient().getTopLevelWorldView().getBaseY();
             x = worldPoint.getX() - baseX;
             y = worldPoint.getY() - baseY;
        }


        return isWithinBounds(x, y) && visited[x][y];
    }
}
