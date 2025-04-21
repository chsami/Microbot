package net.runelite.client.plugins.microbot.util.tile;

import lombok.Getter;
import net.runelite.api.*;
import net.runelite.api.coords.Direction;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.devtools.MovementFlag;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.CollisionMap;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.coords.Rs2LocalPoint;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldArea;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldPoint;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.apache.commons.lang3.tuple.MutablePair;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public abstract class Rs2Tile implements Tile {

    @Getter
    public static List<MutablePair<WorldPoint, Integer>> dangerousGraphicsObjectTiles = new ArrayList<>();

    private static ScheduledExecutorService tileExecutor;

    /**
     * Initializes the tile executor
     * This will handle the removal of dangerous tiles after a certain amount of time
     */
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

    /**
     * Adds a dangerous tile to the list of dangerous tiles
     * @param graphicsObject the graphics object
     * @param time the time a tile is dangerous in milliseconds
     */
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

            return movementFlags.isEmpty();
        }
        return true;
    }

    public static boolean isWalkable(WorldPoint worldPoint) {
        Client client = Microbot.getClient();
        LocalPoint localPoint = LocalPoint.fromWorld(client, worldPoint);
        if (localPoint == null) {
            return false;
        }
        return isWalkable(localPoint);
    }


    public static boolean isWalkable(LocalPoint localPoint) {
        if (localPoint == null)
            return true;

        Client client = Microbot.getClient();
        if (client.getCollisionMaps() != null) {
            int[][] flags = client.getCollisionMaps()[client.getPlane()].getFlags();
            int data = flags[localPoint.getSceneX()][localPoint.getSceneY()];

            Set<MovementFlag> movementFlags = MovementFlag.getSetFlags(data);

            return !movementFlags.contains(MovementFlag.BLOCK_MOVEMENT_FULL)
                    && !movementFlags.contains(MovementFlag.BLOCK_MOVEMENT_FLOOR);
        }
        return true;
    }

    public static List<WorldPoint> getWalkableTilesAroundPlayer(int radius) {
        return getWalkableTilesAroundTile(Rs2Player.getWorldLocation(), radius);
    }

    public static List<WorldPoint> getWalkableTilesAroundTile(WorldPoint point, int radius) {
        List<WorldPoint> worldPoints = new ArrayList<>();
        LocalPoint playerLocalPosition = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), point);

        if (playerLocalPosition == null) return new ArrayList<>();

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

    /**
     * This method calculates the reachable tiles from a given starting tile without
     * considering any collision data. It is essentially a wrapper around the
     * getReachableTilesFromTile method that sets the 'ignoreCollision' parameter
     * to true, meaning collision checks will be bypassed during the distance
     * calculation.
     *
     * The method performs the same distance calculation but disregards movement
     * restrictions imposed by collisions, allowing for a broader range of reachable
     * tiles.
     *
     * @param tile The starting tile from which reachable tiles will be calculated.
     * @param distance The maximum distance to calculate to neighboring tiles.
     * @return A HashMap containing WorldPoints and their corresponding distances
     *         from the start tile, ignoring collision data.
     */
    public static HashMap<WorldPoint, Integer> getReachableTilesFromTileIgnoreCollision(WorldPoint tile, int distance) {
        return getReachableTilesFromTile(tile, distance, true);
    }

    /**
     * This method calculates the distances to a specified tile in the game world
     * using a breadth-first search (BFS) algorithm, considering movement restrictions
     * and collision data. The distances are stored in a HashMap where the key is a
     * WorldPoint (representing a tile location), and the value is the distance
     * from the starting tile. The method accounts for movement flags that block
     * movement in specific directions (east, west, north, south) and removes
     * unreachable tiles based on collision data.
     *
     * The method iterates over a range of distances, progressively updating
     * reachable tiles and adding them to the tileDistances map. It checks if a
     * tile can be reached by verifying its collision flags and whether it’s blocked
     * for movement in any direction.
     *
     * @param tile The starting tile for the distance calculation.
     * @param distance The maximum distance to calculate to neighboring tiles.
     * @param ignoreCollision If true, ignores collision data during the calculation.
     * @return A HashMap containing WorldPoints and their corresponding distances from the start tile.
     */
    public static HashMap<WorldPoint, Integer> getReachableTilesFromTile(WorldPoint tile, int distance, boolean ignoreCollision) {
        var tileDistances = new HashMap<WorldPoint, Integer>();
        tileDistances.put(tile, 0);

        for (int i = 0; i < distance + 1; i++) {
            int dist = i;
            for (var kvp : tileDistances.entrySet().stream().filter(x -> x.getValue() == dist).collect(Collectors.toList())) {
                var point = kvp.getKey();
                LocalPoint localPoint;
                if (Microbot.getClient().getTopLevelWorldView().isInstance()) {
                    var worldPoint = WorldPoint.toLocalInstance(Microbot.getClient().getTopLevelWorldView(), point).stream().findFirst().orElse(null);
                    if (worldPoint == null) break;
                    localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), worldPoint);
                } else
                    localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), point);

                CollisionData[] collisionMap = Microbot.getClient().getTopLevelWorldView().getCollisionMaps();
                if (collisionMap != null && localPoint != null) {
                    CollisionData collisionData = collisionMap[Microbot.getClient().getTopLevelWorldView().getPlane()];
                    int[][] flags = collisionData.getFlags();
                    int data = flags[localPoint.getSceneX()][localPoint.getSceneY()];

                    Set<MovementFlag> movementFlags = MovementFlag.getSetFlags(data);

                    if (!ignoreCollision && !tile.equals(point)) {
                        if (movementFlags.contains(MovementFlag.BLOCK_MOVEMENT_FULL)
                                || movementFlags.contains(MovementFlag.BLOCK_MOVEMENT_FLOOR)) {
                            tileDistances.remove(point);
                            continue;
                        }
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

    /**
     * This method calculates the reachable tiles from a given starting tile
     * considering collision data during the distance calculation. It is a wrapper
     * around the getReachableTilesFromTile method that sets the 'ignoreCollision'
     * parameter to false, meaning collision checks will be applied when calculating
     * reachable tiles.
     *
     * The method calculates the distance to neighboring tiles while taking into
     * account any movement restrictions imposed by collision data, ensuring that
     * only tiles that are not blocked for movement are considered reachable.
     *
     * @param tile The starting tile from which reachable tiles will be calculated.
     * @param distance The maximum distance to calculate to neighboring tiles.
     * @return A HashMap containing WorldPoints and their corresponding distances
     *         from the start tile, considering collision data.
     */
    public static HashMap<WorldPoint, Integer> getReachableTilesFromTile(WorldPoint tile, int distance) {
        return getReachableTilesFromTile(tile, distance, false);
    }

    /**
     * This method checks if a given target tile (WorldPoint) is reachable from the
     * player's current location, considering collision data and the plane of the
     * world. The method uses a breadth-first search (BFS) algorithm to traverse
     * neighboring tiles while checking for movement blocks in the four cardinal
     * directions (north, south, east, west). It ensures the target tile is within
     * the same plane as the player and that movement between tiles is not blocked.
     *
     * The method initializes a queue to explore the world grid, marking visited
     * tiles to avoid revisiting. It checks the flags for collision data to determine
     * whether movement is allowed in each direction, and only adds neighboring tiles
     * to the queue if they are not blocked. Finally, it verifies if the target point
     * has been visited during the traversal and returns true if reachable, false otherwise.
     *
     * @param targetPoint The WorldPoint representing the target tile to check for
     *                    reachability.
     * @return True if the target tile is reachable from the player's location,
     *         otherwise false.
     */
    public static boolean isTileReachable(WorldPoint targetPoint) {
        if (targetPoint == null) return false;
        if (targetPoint.getPlane() != Rs2Player.getWorldLocation().getPlane()) return false;
        if (CollisionMap.ignoreCollision.contains(targetPoint)) return true;
        boolean[][] visited = new boolean[104][104];
        int[][] flags = Microbot.getClient().getCollisionMaps()[Microbot.getClient().getPlane()].getFlags();
        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        int startX = 0;
        int startY = 0;
        int startPoint = 0;
        if (Microbot.getClient().getTopLevelWorldView().getScene().isInstance()) {
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

    /**
     * Checks if any of the tiles immediately surrounding the given object are walkable & reachable.
     * The object is defined by its position (worldPoint) and its size (sizeX x sizeY).
     *
     * @param worldPoint The central point of the object.
     * @param sizeX      The size of the object along the X-axis.
     * @param sizeY      The size of the object along the Y-axis.
     * @return true if any surrounding tile is walkable, false otherwise.
     */
    public static boolean areSurroundingTilesWalkable(WorldPoint worldPoint, int sizeX, int sizeY) {
        int plane = worldPoint.getPlane();

        // Calculate the boundaries of the object
        int minX = worldPoint.getX() - (sizeX - 1) / 2;
        int minY = worldPoint.getY() - (sizeY - 1) / 2;
        int maxX = minX + sizeX - 1;
        int maxY = minY + sizeY - 1;

        // Loop over the tiles surrounding the object
        for (int x = minX - 1; x <= maxX + 1; x++) {
            for (int y = minY - 1; y <= maxY + 1; y++) {
                // Skip the tiles that are part of the object itself
                if (x >= minX && x <= maxX && y >= minY && y <= maxY) {
                    continue;
                }

                // Check if the surrounding tile is walkable
                if (isTileReachable(new WorldPoint(x, y, plane))) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * This method checks if the given coordinates (x, y) are within the valid bounds
     * of the game world grid. It ensures that the coordinates are non-negative and
     * within the range of the grid dimensions (0 to 103 for both x and y).
     *
     * The method is used to prevent out-of-bounds errors when accessing world tiles
     * by ensuring that the coordinates provided for the tile are within the valid
     * range before performing further operations.
     *
     * @param x The x-coordinate of the tile to check.
     * @param y The y-coordinate of the tile to check.
     * @return True if the coordinates are within bounds (0 <= x, y < 104), otherwise false.
     */
    private static boolean isWithinBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < 104 && y < 104;
    }

    /**
     * This method checks a neighboring tile and adds it to the queue if it is valid
     * and not blocked for movement. It considers both the current tile's collision
     * data and the neighboring tile’s collision flags to determine whether movement
     * in the specified direction (dx, dy) is possible. The method ensures the neighboring
     * tile is within bounds, hasn't been visited, and doesn't have movement restrictions
     * (such as full-block movement or movement in the specified direction).
     *
     * The method performs a bitwise check on the tile’s flags to determine if movement
     * in the given direction is allowed and ensures that the neighboring tile is not
     * already visited before adding it to the queue.
     *
     * @param queue The queue that stores the coordinates of tiles to be visited.
     * @param visited A 2D boolean array tracking which tiles have already been visited.
     * @param flags A 2D array containing the collision flags for each tile.
     * @param x The current tile’s x-coordinate.
     * @param y The current tile’s y-coordinate.
     * @param dx The change in x-coordinate for the neighboring tile.
     * @param dy The change in y-coordinate for the neighboring tile.
     * @param blockMovementFlag The collision flag that blocks movement in a given direction.
     */
    private static void checkAndAddNeighbour(ArrayDeque<Integer> queue, boolean[][] visited, int[][] flags, int x, int y, int dx, int dy, int blockMovementFlag) {
        int nx = x + dx;
        int ny = y + dy;

        if (isWithinBounds(nx, ny) && !visited[nx][ny] && (flags[x][y] & blockMovementFlag) == 0 && (flags[nx][ny] & CollisionDataFlag.BLOCK_MOVEMENT_FULL) == 0) {
            queue.add((nx << 16) | ny);
            visited[nx][ny] = true;
        }
    }

    /**
     * This method checks whether a given WorldPoint has been visited during the
     * traversal of the game world. It calculates the tile’s local coordinates relative
     * to the base coordinates, considering whether the client is in an instanced region
     * or not. The method then checks if the calculated coordinates are within bounds
     * and if the tile has been marked as visited in the provided visited array.
     *
     * The method ensures that the given WorldPoint corresponds to a valid tile on
     * the game map by verifying if its coordinates fall within the bounds of the
     * world grid, and if so, it checks whether that tile has already been visited
     * during the search or traversal process.
     *
     * @param worldPoint The WorldPoint representing the tile to check for visit status.
     * @param visited A 2D boolean array tracking visited tiles during world traversal.
     * @return True if the tile has been visited and is within bounds, otherwise false.
     */
    private static boolean isVisited(WorldPoint worldPoint, boolean[][] visited) {
        int baseX = 0;
        int baseY = 0;
        int x = 0;
        int y = 0;
        if (Microbot.getClient().getTopLevelWorldView().getScene().isInstance()) {
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

    /**
     * Gets the neighboring tile in the specified direction from the source tile.
     * <p>
     * This method calculates the neighboring tile based on the given direction
     * (NORTH, SOUTH, EAST, WEST) and returns the corresponding WorldPoint.
     *
     * @param direction The direction in which to find the neighboring tile.
     * @param source    The source tile from which to find the neighboring tile.
     *
     * @return The neighboring tile in the specified direction.
     *
     * @throws IllegalArgumentException if the direction is not one of the expected values.
     */
    private static WorldPoint getNeighbour(Direction direction, WorldPoint source) {
        switch (direction) {
            case NORTH:
                return source.dy(1);
            case SOUTH:
                return source.dy(-1);
            case WEST:
                return source.dx(-1);
            case EAST:
                return source.dx(1);
            default:
                throw new IllegalArgumentException();
        }
    }

    /**
     * Finds the nearest walkable tile from the given source tile.
     * <p>
     * This method iterates through all possible directions (NORTH, SOUTH, EAST, WEST)
     * from the source tile and checks if the neighboring tile in that direction is walkable.
     * If a walkable tile is found, it is returned.
     *
     * @param source The source tile from which to find the nearest walkable tile.
     *
     * @return The nearest walkable tile, or null if no walkable tile is found.
     */
    public static WorldPoint getNearestWalkableTile(WorldPoint source) {
        for (Direction direction : Direction.values()) {
            WorldPoint neighbour = getNeighbour(direction, source);
            if (neighbour.equals(Rs2Player.getWorldLocation())) continue;
            if (isWalkable(neighbour)) {
                return neighbour;
            }
        }

        return null;
    }

    /**
     * This method attempts to find the nearest walkable tile from a given source
     * tile that has a line of sight, meaning there are no obstacles or walls blocking
     * the path. It first checks if the source tile itself is walkable, considering
     * both its tile validity and whether it is free of obstacles. If the source tile
     * is walkable, it is returned immediately.
     *
     * If the source tile is not walkable, the method checks the neighboring tiles
     * in all directions (north, south, east, west, etc.) to find the closest walkable
     * tile. It excludes the player’s current location from being considered as a
     * valid neighbor. The method ensures that each neighbor is not blocked by walls
     * and that it is valid and walkable, or alternatively a bank booth.
     *
     * If no walkable tile is found, the method returns null.
     *
     * @param source The WorldPoint representing the source tile from which to
     *               search for a walkable neighbor.
     * @return The nearest walkable tile with a line of sight, or null if no such
     *         tile is found.
     */
    public static WorldPoint getNearestWalkableTileWithLineOfSight(WorldPoint source) {
        // check if source is walkable
        if (!tileHasWalls(source)
                && isValidTile(getTile(source.getX(), source.getY()))
                && (isWalkable(LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), source.getX(), source.getY())) || isBankBooth(source))) {
            return source;
        }
        //check if neightbours are walkable
        for (Direction direction : Direction.values()) {
            WorldPoint neighbour = getNeighbour(direction, source);
            if (neighbour.equals(Rs2Player.getWorldLocation())) continue;
            if (!tileHasWalls(neighbour)
                    && isValidTile(getTile(neighbour.getX(), neighbour.getY()))
                    && (isWalkable(LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), neighbour.getX(), neighbour.getY())) || isBankBooth(neighbour))) {
                return neighbour;
            }
        }

        return null;
    }

    /**
     * Finds the nearest walkable tile around a specified game object that the player can interact with.
     *
     * <p>This method calculates the closest walkable tile adjacent to the given game object, considering the player's current position.
     * It ensures that both the player and the object are on the same plane before proceeding. The method retrieves interactable points
     * around the object, filters out non-walkable tiles, and selects the closest one to the player.</p>
     *
     * @param tileObject The {@link GameObject} for which to find the nearest walkable tile.
     * @return An {@link Rs2WorldPoint} representing the nearest walkable tile around the object, or {@code null} if none are found.
     */
    public static Rs2WorldPoint getNearestWalkableTile(GameObject tileObject) {
        // Cache player's location and top-level world view
        Rs2WorldPoint playerLocation = Rs2Player.getRs2WorldPoint();
        WorldView topLevelWorldView = Microbot.getClient().getTopLevelWorldView();

        // Check if player and object are on the same plane
        if (playerLocation.getPlane() != tileObject.getWorldLocation().getPlane()) {
            return null;
        }

        // Get the world area of the game object
        Rs2WorldArea gameObjectArea = new Rs2WorldArea(Objects.requireNonNull(Rs2GameObject.getWorldArea(tileObject)));

        // Get interactable points around the game object
        List<WorldPoint> interactablePoints = getInteractablePoints(gameObjectArea, topLevelWorldView);

        if (interactablePoints.isEmpty()) {
            return null; // No interactable points found
        }

        // Filter points that are walkable
        List<WorldPoint> walkablePoints = interactablePoints.stream()
                .filter(Rs2Tile::isWalkable)
                .collect(Collectors.toList());

        if (walkablePoints.isEmpty()) {
            return null; // No walkable points available
        }

        // Find the nearest walkable interact point to the player
        WorldPoint nearestPoint = walkablePoints.stream()
                .min(Comparator.comparingInt(playerLocation::distanceToPath))
                .orElse(null);

        return new Rs2WorldPoint(nearestPoint);
    }

    /**
     * Retrieves a list of interactable points around a given game object area.
     *
     * <p>This method calculates interactable points around the specified game object area. If no initial interactable points
     * are found, it expands the area and collects new points, excluding those within the original object area. It also filters out
     * points from which the object cannot be reached via melee attacks or points that have walls obstructing interaction.</p>
     *
     * @param gameObjectArea   The {@link Rs2WorldArea} representing the area of the game object.
     * @param topLevelWorldView The top-level {@link WorldView} of the game client.
     * @return A {@link List} of {@link WorldPoint} objects that are interactable around the game object.
     */
    private static List<WorldPoint> getInteractablePoints(Rs2WorldArea gameObjectArea, WorldView topLevelWorldView) {
        // Get initial interactable points
        List<WorldPoint> interactablePoints = new ArrayList<>(gameObjectArea.getInteractable());

        if (interactablePoints.isEmpty()) {
            // If no interactable points, expand the area and get new points
            Rs2WorldArea expandedArea = gameObjectArea.offset(1);
            interactablePoints = expandedArea.toWorldPointList();

            // Remove points inside the game object area
            interactablePoints.removeIf(gameObjectArea::contains);

            // Remove points from which the object cannot be melee'd
            interactablePoints.removeIf(point -> !gameObjectArea.canMelee(topLevelWorldView, new Rs2WorldArea(point.toWorldArea())));
        } else {
            // Filter points from which the object can be melee'd
            interactablePoints = interactablePoints.stream()
                    .filter(point -> gameObjectArea.canMelee(topLevelWorldView, new Rs2WorldArea(point.toWorldArea())))
                    .collect(Collectors.toList());

            if (interactablePoints.isEmpty()) {
                // If no melee points, remove points with walls
                interactablePoints = gameObjectArea.getInteractable();
                interactablePoints.removeIf(Rs2Tile::tileHasWalls);
            }
        }

        return interactablePoints;
    }

    /**
     * This method checks if the given tile (WorldPoint) contains any walls or
     * obstacles by searching through the game’s wall objects. It filters the
     * list of wall objects to find any that match the provided source tile's
     * location. If a wall object is found at the specified location, the method
     * returns true, indicating that the tile has walls; otherwise, it returns false.
     *
     * The method utilizes the stream API to filter the wall objects and check if
     * any of them match the WorldPoint provided. If no matching wall object is
     * found, the tile is considered free of walls.
     *
     * @param source The WorldPoint representing the tile to check for walls.
     * @return True if the tile has walls or obstacles, false otherwise.
     */
    public static boolean tileHasWalls(WorldPoint source) {
        return Rs2GameObject.getWallObjects().stream().filter(x -> x.getWorldLocation().equals(source)).findFirst().orElse(null) != null;
    }

    /**
     * This method checks if the given tile (WorldPoint) contains a bank booth.
     * It searches through the game’s game objects to find any object that matches
     * the specified location. If a game object is found at the given WorldPoint,
     * the method retrieves its object composition and checks if its name is "bank booth".
     *
     * If the object at the tile is a bank booth (case-insensitive), the method
     * returns true; otherwise, it returns false. If no game object is found at
     * the specified location, the method returns false.
     *
     * @param source The WorldPoint representing the tile to check for a bank booth.
     * @return True if the tile contains a bank booth, false otherwise.
     */
    public static boolean isBankBooth(WorldPoint source) {
        GameObject gameObject = Rs2GameObject.getGameObjects().stream().filter(x -> x.getWorldLocation().equals(source)).findFirst().orElse(null);
        if (gameObject != null) {
            ObjectComposition objectComposition = Rs2GameObject.convertGameObjectToObjectComposition(gameObject);
            return objectComposition != null && objectComposition.getName().equalsIgnoreCase("bank booth");
        }
        return false;
    }

    /**
     * This method retrieves the tile at the specified coordinates (x, y) on the current plane.
     * It first creates a WorldPoint for the given coordinates and checks if the point is within
     * the scene using the `isInScene` method. If the WorldPoint is valid and within the scene,
     * it converts the WorldPoint to a LocalPoint, then retrieves and returns the corresponding
     * Tile from the game scene.
     *
     * If the WorldPoint is out of bounds or the LocalPoint is null, the method returns null
     * to indicate that no valid tile is found at the given coordinates.
     *
     * @param x The x-coordinate of the tile.
     * @param y The y-coordinate of the tile.
     * @return The Tile at the specified coordinates, or null if the tile is invalid or not in the scene.
     */
    public static Tile getTile(int x, int y) {
        WorldPoint worldPoint = new WorldPoint(x, y, Microbot.getClient().getTopLevelWorldView().getPlane());
        LocalPoint localPoint;

        if (Microbot.getClient().getTopLevelWorldView().getScene().isInstance()) {
            localPoint = Rs2LocalPoint.fromWorldInstance(worldPoint);
        } else {
            localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), worldPoint);
        }

        if (localPoint == null) return null;

        return Microbot.getClient().getTopLevelWorldView().getScene().getTiles()[worldPoint.getPlane()][localPoint.getSceneX()][localPoint.getSceneY()];
    }

    /**
     * This method checks if the given Tile is valid for movement. It retrieves the collision
     * flags for the tile’s location from the game’s collision map and checks if any movement
     * restrictions are applied. The method returns true if the tile has no movement flags
     * (i.e., it is not blocked), indicating the tile is valid for movement. Otherwise, it returns
     * false if the tile is blocked by movement flags.
     *
     * The method utilizes the collision map for the current plane and the `MovementFlag` class
     * to determine whether the tile is accessible or restricted by obstacles.
     *
     * @param tile The Tile to check for validity.
     * @return True if the tile is valid (not blocked by movement restrictions), false otherwise.
     */
    public static boolean isValidTile(Tile tile) {
        if (tile == null) return false;
        int[][] flags = Microbot.getClient().getCollisionMaps()[Microbot.getClient().getPlane()].getFlags();
        int data = flags[tile.getSceneLocation().getX()][tile.getSceneLocation().getY()];

        Set<MovementFlag> movementFlags = MovementFlag.getSetFlags(data);

        if (movementFlags.isEmpty())
        {
            return true;
        }
        return false;
    }

    /**
     * This method finds a path from the source tile to the destination tile using BFS.
     * Unlike the checkpoint method, this version returns every single tile along the path.
     * It performs a breadth-first search with collision checking, and then backtracks from the
     * destination (or closest reachable tile) to the source, adding each tile encountered.
     *
     * @param source The starting tile.
     * @param other The destination tile.
     * @return A list of tiles representing every step from the source to the destination,
     *         or null if no path is found.
     */
    public static List<Tile> fullPathTo(Tile source, Tile other) {
        int z = source.getPlane();
        if (z != other.getPlane()) {
            return null;
        }

        CollisionData[] collisionData = Microbot.getClient().getTopLevelWorldView().getCollisionMaps();
        if (collisionData == null) {
            return null;
        }

        int[][] directions = new int[128][128];
        int[][] distances = new int[128][128];
        int[] bufferX = new int[4096];
        int[] bufferY = new int[4096];

        // Initialize the directions and distances arrays
        for (int i = 0; i < 128; ++i) {
            for (int j = 0; j < 128; ++j) {
                directions[i][j] = 0;
                distances[i][j] = Integer.MAX_VALUE;
            }
        }

        Point p1 = source.getSceneLocation();
        Point p2 = other.getSceneLocation();

        int middleX = p1.getX();
        int middleY = p1.getY();
        int currentX = middleX;
        int currentY = middleY;
        int offsetX = 64;
        int offsetY = 64;

        // Start position: mark as visited
        directions[offsetX][offsetY] = 99;
        distances[offsetX][offsetY] = 0;
        int index1 = 0;
        bufferX[0] = currentX;
        int index2 = 1;
        bufferY[0] = currentY;
        int[][] collisionDataFlags = collisionData[z].getFlags();

        boolean isReachable = false;

        // BFS search for the destination tile
        while (index1 != index2) {
            currentX = bufferX[index1];
            currentY = bufferY[index1];
            index1 = (index1 + 1) & 4095;
            int currentMapX = currentX - middleX + offsetX;
            int currentMapY = currentY - middleY + offsetY;

            if ((currentX == p2.getX()) && (currentY == p2.getY())) {
                isReachable = true;
                break;
            }

            int currentDistance = distances[currentMapX][currentMapY] + 1;

            // Check all eight possible directions:
            // West
            if (currentMapX > 0 && directions[currentMapX - 1][currentMapY] == 0 &&
                    (collisionDataFlags[currentX - 1][currentY] & 19136776) == 0) {
                bufferX[index2] = currentX - 1;
                bufferY[index2] = currentY;
                index2 = (index2 + 1) & 4095;
                directions[currentMapX - 1][currentMapY] = 2;
                distances[currentMapX - 1][currentMapY] = currentDistance;
            }
            // East
            if (currentMapX < 127 && directions[currentMapX + 1][currentMapY] == 0 &&
                    (collisionDataFlags[currentX + 1][currentY] & 19136896) == 0) {
                bufferX[index2] = currentX + 1;
                bufferY[index2] = currentY;
                index2 = (index2 + 1) & 4095;
                directions[currentMapX + 1][currentMapY] = 8;
                distances[currentMapX + 1][currentMapY] = currentDistance;
            }
            // South
            if (currentMapY > 0 && directions[currentMapX][currentMapY - 1] == 0 &&
                    (collisionDataFlags[currentX][currentY - 1] & 19136770) == 0) {
                bufferX[index2] = currentX;
                bufferY[index2] = currentY - 1;
                index2 = (index2 + 1) & 4095;
                directions[currentMapX][currentMapY - 1] = 1;
                distances[currentMapX][currentMapY - 1] = currentDistance;
            }
            // North
            if (currentMapY < 127 && directions[currentMapX][currentMapY + 1] == 0 &&
                    (collisionDataFlags[currentX][currentY + 1] & 19136800) == 0) {
                bufferX[index2] = currentX;
                bufferY[index2] = currentY + 1;
                index2 = (index2 + 1) & 4095;
                directions[currentMapX][currentMapY + 1] = 4;
                distances[currentMapX][currentMapY + 1] = currentDistance;
            }
            // South-West
            if (currentMapX > 0 && currentMapY > 0 && directions[currentMapX - 1][currentMapY - 1] == 0 &&
                    (collisionDataFlags[currentX - 1][currentY - 1] & 19136782) == 0 &&
                    (collisionDataFlags[currentX - 1][currentY] & 19136776) == 0 &&
                    (collisionDataFlags[currentX][currentY - 1] & 19136770) == 0) {
                bufferX[index2] = currentX - 1;
                bufferY[index2] = currentY - 1;
                index2 = (index2 + 1) & 4095;
                directions[currentMapX - 1][currentMapY - 1] = 3;
                distances[currentMapX - 1][currentMapY - 1] = currentDistance;
            }
            // North-West
            if (currentMapX > 0 && currentMapY < 127 && directions[currentMapX - 1][currentMapY + 1] == 0 &&
                    (collisionDataFlags[currentX - 1][currentY + 1] & 19136824) == 0 &&
                    (collisionDataFlags[currentX - 1][currentY] & 19136776) == 0 &&
                    (collisionDataFlags[currentX][currentY + 1] & 19136800) == 0) {
                bufferX[index2] = currentX - 1;
                bufferY[index2] = currentY + 1;
                index2 = (index2 + 1) & 4095;
                directions[currentMapX - 1][currentMapY + 1] = 6;
                distances[currentMapX - 1][currentMapY + 1] = currentDistance;
            }
            // South-East
            if (currentMapX < 127 && currentMapY > 0 && directions[currentMapX + 1][currentMapY - 1] == 0 &&
                    (collisionDataFlags[currentX + 1][currentY - 1] & 19136899) == 0 &&
                    (collisionDataFlags[currentX + 1][currentY] & 19136896) == 0 &&
                    (collisionDataFlags[currentX][currentY - 1] & 19136770) == 0) {
                bufferX[index2] = currentX + 1;
                bufferY[index2] = currentY - 1;
                index2 = (index2 + 1) & 4095;
                directions[currentMapX + 1][currentMapY - 1] = 9;
                distances[currentMapX + 1][currentMapY - 1] = currentDistance;
            }
            // North-East
            if (currentMapX < 127 && currentMapY < 127 && directions[currentMapX + 1][currentMapY + 1] == 0 &&
                    (collisionDataFlags[currentX + 1][currentY + 1] & 19136992) == 0 &&
                    (collisionDataFlags[currentX + 1][currentY] & 19136896) == 0 &&
                    (collisionDataFlags[currentX][currentY + 1] & 19136800) == 0) {
                bufferX[index2] = currentX + 1;
                bufferY[index2] = currentY + 1;
                index2 = (index2 + 1) & 4095;
                directions[currentMapX + 1][currentMapY + 1] = 12;
                distances[currentMapX + 1][currentMapY + 1] = currentDistance;
            }
        }

        // If the destination wasn't directly reached, find the closest reachable tile within a 21x21 area.
        if (!isReachable) {
            int upperboundDistance = Integer.MAX_VALUE;
            int pathLength = Integer.MAX_VALUE;
            int checkRange = 10;
            int approxDestinationX = p2.getX();
            int approxDestinationY = p2.getY();
            for (int i = approxDestinationX - checkRange; i <= approxDestinationX + checkRange; ++i) {
                for (int j = approxDestinationY - checkRange; j <= approxDestinationY + checkRange; ++j) {
                    int currentMapX = i - middleX + offsetX;
                    int currentMapY = j - middleY + offsetY;
                    if (currentMapX >= 0 && currentMapY >= 0 &&
                            currentMapX < 128 && currentMapY < 128 && distances[currentMapX][currentMapY] < 100) {

                        int deltaX = Math.abs(i - approxDestinationX);
                        int deltaY = Math.abs(j - approxDestinationY);
                        int distanceSquared = deltaX * deltaX + deltaY * deltaY;

                        if (distanceSquared < upperboundDistance ||
                                (distanceSquared == upperboundDistance && distances[currentMapX][currentMapY] < pathLength)) {
                            upperboundDistance = distanceSquared;
                            pathLength = distances[currentMapX][currentMapY];
                            currentX = i;
                            currentY = j;
                        }
                    }
                }
            }
            if (upperboundDistance == Integer.MAX_VALUE) {
                return null;
            }
        }

        // Backtrack from the found destination (or closest tile) to the source,
        // adding every tile along the path.
        List<Tile> fullPath = new ArrayList<>();
        Tile[][][] tiles = Microbot.getClient().getScene().getTiles();
        fullPath.add(tiles[source.getPlane()][currentX][currentY]);

        while (p1.getX() != currentX || p1.getY() != currentY) {
            int direction = directions[currentX - middleX + offsetX][currentY - middleY + offsetY];
            if ((direction & 2) != 0) {
                ++currentX;
            } else if ((direction & 8) != 0) {
                --currentX;
            }
            if ((direction & 1) != 0) {
                ++currentY;
            } else if ((direction & 4) != 0) {
                --currentY;
            }
            fullPath.add(tiles[source.getPlane()][currentX][currentY]);
        }

        // The backtracked path runs from the destination (or nearest tile) back to the source,
        // so we reverse it to have the path go from source to destination.
        Collections.reverse(fullPath);
        return fullPath;
    }


    /**
     * This method attempts to find a path from the source tile to the destination tile
     * using a pathfinding algorithm. It employs a breadth-first search (BFS) approach
     * that considers the game's collision data and movement restrictions for each tile.
     * The method calculates the shortest possible path to the target tile while accounting
     * for walkability, obstacles, and the ability to traverse tiles in all directions (north,
     * south, east, west, and diagonal directions). It returns a list of tiles representing the
     * path from the source to the destination.
     *
     * The algorithm checks the walkability of each tile by evaluating the collision flags for
     * each tile and moves through the tiles that are not blocked. If the target tile is unreachable,
     * the method attempts to find the closest reachable tile within a 21x21 area surrounding the target
     * tile.
     *
     * If a valid path is found, the method traces the path from the destination back to the source
     * using the directions and distances calculated during the search. It then constructs and returns
     * a list of checkpoint tiles along the path (up to a maximum of 25 tiles).
     *
     * @param source The starting tile from which to find the path.
     * @param other The destination tile to reach.
     * @return A list of tiles representing the path from source to destination, or null if no path is found.
     */
    public static List<Tile> pathTo(Tile source,Tile other)
    {

        int z = source.getPlane();
        if (z != other.getPlane())
        {
            return null;
        }

        CollisionData[] collisionData = Microbot.getClient().getTopLevelWorldView().getCollisionMaps();
        if (collisionData == null)
        {
            return null;
        }

        int[][] directions = new int[128][128];
        int[][] distances = new int[128][128];
        int[] bufferX = new int[4096];
        int[] bufferY = new int[4096];

        // Initialise directions and distances
        for (int i = 0; i < 128; ++i)
        {
            for (int j = 0; j < 128; ++j)
            {
                directions[i][j] = 0;
                distances[i][j] = Integer.MAX_VALUE;
            }
        }

        Point p1 = source.getSceneLocation();
        Point p2 = other.getSceneLocation();

        int middleX = p1.getX();
        int middleY = p1.getY();
        int currentX = middleX;
        int currentY = middleY;
        int offsetX = 64;
        int offsetY = 64;
        // Initialise directions and distances for starting tile
        directions[offsetX][offsetY] = 99;
        distances[offsetX][offsetY] = 0;
        int index1 = 0;
        bufferX[0] = currentX;
        int index2 = 1;
        bufferY[0] = currentY;
        int[][] collisionDataFlags = collisionData[z].getFlags();

        boolean isReachable = false;

        while (index1 != index2)
        {
            currentX = bufferX[index1];
            currentY = bufferY[index1];
            index1 = index1 + 1 & 4095;
            // currentX is for the local coordinate while currentMapX is for the index in the directions and distances arrays
            int currentMapX = currentX - middleX + offsetX;
            int currentMapY = currentY - middleY + offsetY;
            if ((currentX == p2.getX()) && (currentY == p2.getY()))
            {
                isReachable = true;
                break;
            }

            int currentDistance = distances[currentMapX][currentMapY] + 1;
            if (currentMapX > 0 && directions[currentMapX - 1][currentMapY] == 0 && (collisionDataFlags[currentX - 1][currentY] & 19136776) == 0)
            {
                // Able to move 1 tile west
                bufferX[index2] = currentX - 1;
                bufferY[index2] = currentY;
                index2 = index2 + 1 & 4095;
                directions[currentMapX - 1][currentMapY] = 2;
                distances[currentMapX - 1][currentMapY] = currentDistance;
            }

            if (currentMapX < 127 && directions[currentMapX + 1][currentMapY] == 0 && (collisionDataFlags[currentX + 1][currentY] & 19136896) == 0)
            {

                // Able to move 1 tile east
                bufferX[index2] = currentX + 1;
                bufferY[index2] = currentY;
                index2 = index2 + 1 & 4095;
                directions[currentMapX + 1][currentMapY] = 8;
                distances[currentMapX + 1][currentMapY] = currentDistance;
            }

            if (currentMapY > 0 && directions[currentMapX][currentMapY - 1] == 0 && (collisionDataFlags[currentX][currentY - 1] & 19136770) == 0)
            {
                // Able to move 1 tile south
                bufferX[index2] = currentX;
                bufferY[index2] = currentY - 1;
                index2 = index2 + 1 & 4095;
                directions[currentMapX][currentMapY - 1] = 1;
                distances[currentMapX][currentMapY - 1] = currentDistance;
            }

            if (currentMapY < 127 && directions[currentMapX][currentMapY + 1] == 0 && (collisionDataFlags[currentX][currentY + 1] & 19136800) == 0)
            {
                // Able to move 1 tile north
                bufferX[index2] = currentX;
                bufferY[index2] = currentY + 1;
                index2 = index2 + 1 & 4095;
                directions[currentMapX][currentMapY + 1] = 4;
                distances[currentMapX][currentMapY + 1] = currentDistance;
            }

            if (currentMapX > 0 && currentMapY > 0 && directions[currentMapX - 1][currentMapY - 1] == 0 && (collisionDataFlags[currentX - 1][currentY - 1] & 19136782) == 0 && (collisionDataFlags[currentX - 1][currentY] & 19136776) == 0 && (collisionDataFlags[currentX][currentY - 1] & 19136770) == 0)
            {
                // Able to move 1 tile south-west
                bufferX[index2] = currentX - 1;
                bufferY[index2] = currentY - 1;
                index2 = index2 + 1 & 4095;
                directions[currentMapX - 1][currentMapY - 1] = 3;
                distances[currentMapX - 1][currentMapY - 1] = currentDistance;
            }

            if (currentMapX > 0 && currentMapY < 127 && directions[currentMapX - 1][currentMapY + 1] == 0 && (collisionDataFlags[currentX - 1][currentY + 1] & 19136824) == 0 && (collisionDataFlags[currentX - 1][currentY] & 19136776) == 0 && (collisionDataFlags[currentX][currentY + 1] & 19136800) == 0)
            {
                // Able to move 1 tile north-west
                bufferX[index2] = currentX - 1;
                bufferY[index2] = currentY + 1;
                index2 = index2 + 1 & 4095;
                directions[currentMapX - 1][currentMapY + 1] = 6;
                distances[currentMapX - 1][currentMapY + 1] = currentDistance;
            }

            if (currentMapX < 127 && currentMapY > 0 && directions[currentMapX + 1][currentMapY - 1] == 0 && (collisionDataFlags[currentX + 1][currentY - 1] & 19136899) == 0 && (collisionDataFlags[currentX + 1][currentY] & 19136896) == 0 && (collisionDataFlags[currentX][currentY - 1] & 19136770) == 0)
            {
                // Able to move 1 tile south-east
                bufferX[index2] = currentX + 1;
                bufferY[index2] = currentY - 1;
                index2 = index2 + 1 & 4095;
                directions[currentMapX + 1][currentMapY - 1] = 9;
                distances[currentMapX + 1][currentMapY - 1] = currentDistance;
            }

            if (currentMapX < 127 && currentMapY < 127 && directions[currentMapX + 1][currentMapY + 1] == 0 && (collisionDataFlags[currentX + 1][currentY + 1] & 19136992) == 0 && (collisionDataFlags[currentX + 1][currentY] & 19136896) == 0 && (collisionDataFlags[currentX][currentY + 1] & 19136800) == 0)
            {
                // Able to move 1 tile north-east
                bufferX[index2] = currentX + 1;
                bufferY[index2] = currentY + 1;
                index2 = index2 + 1 & 4095;
                directions[currentMapX + 1][currentMapY + 1] = 12;
                distances[currentMapX + 1][currentMapY + 1] = currentDistance;
            }
        }
        if (!isReachable)
        {
            // Try find a different reachable tile in the 21x21 area around the target tile, as close as possible to the target tile
            int upperboundDistance = Integer.MAX_VALUE;
            int pathLength = Integer.MAX_VALUE;
            int checkRange = 10;
            int approxDestinationX = p2.getX();
            int approxDestinationY = p2.getY();
            for (int i = approxDestinationX - checkRange; i <= checkRange + approxDestinationX; ++i)
            {
                for (int j = approxDestinationY - checkRange; j <= checkRange + approxDestinationY; ++j)
                {
                    int currentMapX = i - middleX + offsetX;
                    int currentMapY = j - middleY + offsetY;
                    if (currentMapX >= 0 && currentMapY >= 0 && currentMapX < 128 && currentMapY < 128 && distances[currentMapX][currentMapY] < 100)
                    {
                        int deltaX = 0;
                        if (i < approxDestinationX)
                        {
                            deltaX = approxDestinationX - i;
                        }
                        else if (i > approxDestinationX)
                        {
                            deltaX = i - (approxDestinationX);
                        }

                        int deltaY = 0;
                        if (j < approxDestinationY)
                        {
                            deltaY = approxDestinationY - j;
                        }
                        else if (j > approxDestinationY)
                        {
                            deltaY = j - (approxDestinationY);
                        }

                        int distanceSquared = deltaX * deltaX + deltaY * deltaY;
                        if (distanceSquared < upperboundDistance || distanceSquared == upperboundDistance && distances[currentMapX][currentMapY] < pathLength)
                        {
                            upperboundDistance = distanceSquared;
                            pathLength = distances[currentMapX][currentMapY];
                            currentX = i;
                            currentY = j;
                        }
                    }
                }
            }
            if (upperboundDistance == Integer.MAX_VALUE)
            {
                // No path found
                return null;
            }
        }

        // Getting path from directions and distances
        bufferX[0] = currentX;
        bufferY[0] = currentY;
        int index = 1;
        int directionNew;
        int directionOld;
        for (directionNew = directionOld = directions[currentX - middleX + offsetX][currentY - middleY + offsetY]; p1.getX() != currentX || p1.getY() != currentY; directionNew = directions[currentX - middleX + offsetX][currentY - middleY + offsetY])
        {
            if (directionNew != directionOld)
            {
                // "Corner" of the path --> new checkpoint tile
                directionOld = directionNew;
                bufferX[index] = currentX;
                bufferY[index++] = currentY;
            }

            if ((directionNew & 2) != 0)
            {
                ++currentX;
            }
            else if ((directionNew & 8) != 0)
            {
                --currentX;
            }

            if ((directionNew & 1) != 0)
            {
                ++currentY;
            }
            else if ((directionNew & 4) != 0)
            {
                --currentY;
            }
        }

        int checkpointTileNumber = 1;
        Tile[][][] tiles = Microbot.getClient().getScene().getTiles();
        List<Tile> checkpointTiles = new ArrayList<>();
        while (index-- > 0)
        {
            checkpointTiles.add(tiles[source.getPlane()][bufferX[index]][bufferY[index]]);
            if (checkpointTileNumber == 25)
            {
                // Pathfinding only supports up to the 25 first checkpoint tiles
                break;
            }
            checkpointTileNumber++;
        }
        return checkpointTiles;
    }

    /**
     * Hovers over the given tile using the natural mouse.
     *
     * @param tile The tile to hover over.
     * @return True if successfully hovered, otherwise false.
     */
    public static boolean hoverOverTile(Tile tile) {
        if (!Rs2AntibanSettings.naturalMouse) {
            if (Rs2AntibanSettings.devDebug)
                Microbot.log("Natural mouse is not enabled, can't hover");
            return false;
        }

        Point point = Rs2UiHelper.getClickingPoint(Rs2UiHelper.getTileClickbox(tile), true);

        // If the point is 1,1 then the tile is not on screen and we should return false
        if (point.getX() == 1 && point.getY() == 1) return false;

        Microbot.getNaturalMouse().moveTo(point.getX(), point.getY());
        return true;
    }
}
