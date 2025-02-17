package net.runelite.client.plugins.microbot.util.walker;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Point;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.devtools.MovementFlag;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathConfig;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.coords.Rs2LocalPoint;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldArea;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2Pvp;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static net.runelite.client.plugins.microbot.util.Global.*;
import static net.runelite.client.plugins.microbot.util.walker.Rs2MiniMap.worldToMinimap;

/**
 * TODO:
 * 1. fix teleports starting from inside the POH
 */
@Slf4j
public class Rs2Walker {
    @Setter
    public static ShortestPathConfig config;
    static int stuckCount = 0;
    static WorldPoint lastPosition;
    static WorldPoint currentTarget;
    static int nextWalkingDistance = 10;

    static final int OFFSET = 10; // max offset of the exact area we teleport to

    // Set this to true, if you want to calculate the path but do not want to walk to it
    static boolean debug = false;

    public static boolean disableTeleports = false;

    public static boolean walkTo(int x, int y, int plane) {
        return walkTo(x, y, plane, config.reachedDistance());
    }

    public static boolean walkTo(int x, int y, int plane, int distance) {
        return walkWithState(new WorldPoint(x, y, plane), distance) == WalkerState.ARRIVED;
    }


    public static boolean walkTo(WorldPoint target) {
        return walkWithState(target, config.reachedDistance()) == WalkerState.ARRIVED;
    }

    public static boolean walkTo(WorldPoint target, int distance) {
        return walkWithState(target, distance) == WalkerState.ARRIVED;
    }

    /**
     * Replaces the walkTo method
     *
     * @param target
     * @param distance
     * @return
     */
    public static WalkerState walkWithState(WorldPoint target, int distance) {
        if (Rs2Tile.getReachableTilesFromTile(Rs2Player.getWorldLocation(), distance).containsKey(target)
                || !Rs2Tile.isWalkable(LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), target)) && Rs2Player.getWorldLocation().distanceTo(target) <= distance) {
            return WalkerState.ARRIVED;
        }
        if (ShortestPathPlugin.getPathfinder() != null && !ShortestPathPlugin.getPathfinder().isDone())
            return WalkerState.MOVING;
        if ((currentTarget != null && currentTarget.equals(target)) && ShortestPathPlugin.getMarker() != null)
            return WalkerState.MOVING;
        setTarget(target);
        ShortestPathPlugin.setReachedDistance(distance);
        stuckCount = 0;

        if (Microbot.getClient().isClientThread()) {
            Microbot.log("Please do not call the walker from the main thread");
            return WalkerState.EXIT;
        }
        /**
         * When running the walkTo method from scripts
         * the code will run on the script thread
         * If you really like to run this on a seperate thread because you want to do
         * other actions while walking you can wrap the walkTo from within the script
         * on a seperate thread
         */
        return processWalk(target, distance);
    }

    /**
     * @param target
     * @return
     */
    public static WalkerState walkWithState(WorldPoint target) {
        return walkWithState(target, config.reachedDistance());
    }

    /**
     * Core walk method contains all the logic to succesfully walk to the destination
     * this contains doors, gameobjects, teleports, spells etc...
     *
     * @param target
     * @param distance
     */
    private static WalkerState processWalk(WorldPoint target, int distance) {
        if (debug) return WalkerState.EXIT;
        try {
            if (!Microbot.isLoggedIn()) {
                setTarget(null);
            }
            if (ShortestPathPlugin.getPathfinder() == null) {
                if (ShortestPathPlugin.getMarker() == null) {
                    setTarget(null);
                }
                boolean isInit = sleepUntilTrue(() -> ShortestPathPlugin.getPathfinder() != null, 100, 2000);
                if (!isInit) {
                    Microbot.log("Pathfinder took to long to initialize, exiting walker: 140");
                    setTarget(null);
                    return WalkerState.EXIT;
                }
            }
            if (!ShortestPathPlugin.getPathfinder().isDone()) {
                boolean isDone = sleepUntilTrue(() -> ShortestPathPlugin.getPathfinder().isDone(), 100, 10000);
                if (!isDone) {
                    System.out.println("Pathfinder took to long to calculate path, exiting: 149");
                    setTarget(null);
                    return WalkerState.EXIT;
                }
            }

            if (ShortestPathPlugin.getMarker() == null) {
                Microbot.log("marker is null, exiting: 156");
                setTarget(null);
                return WalkerState.EXIT;
            }

            if (ShortestPathPlugin.getPathfinder() == null) {
                Microbot.log("pathfinder is null, exiting: 162");
                setTarget(null);
                return WalkerState.EXIT;
            }

            List<WorldPoint> path = ShortestPathPlugin.getPathfinder().getPath();
            int pathSize = path.size();


            if (path.get(pathSize - 1).distanceTo(target) > config.reachedDistance()) {
                Microbot.log("Location impossible to reach");
                setTarget(null);
                return WalkerState.UNREACHABLE;
            }

            if (!path.isEmpty() && isNear(path.get(pathSize - 1))) {
                setTarget(null);
            }

            if (Rs2Npc.getNpcsForPlayer(npc -> npc.getId() == 4417).findAny().isPresent()) { //dead tree in draynor
                var moveableTiles = Rs2Tile.getReachableTilesFromTile(Rs2Player.getWorldLocation(), 5).keySet().toArray(new WorldPoint[0]);
                walkMiniMap(moveableTiles[Rs2Random.between(0, moveableTiles.length)]);
                sleepGaussian(1000, 300);
            }

            //avoid tree attacking you in draynor
            checkIfStuck();
            if (stuckCount > 10) {
                var moveableTiles = Rs2Tile.getReachableTilesFromTile(Rs2Player.getWorldLocation(), 5).keySet().toArray(new WorldPoint[0]);
                if (moveableTiles.length > 0) {
                    walkMiniMap(moveableTiles[Rs2Random.between(0, moveableTiles.length)]);
                    sleepGaussian(1000, 300);
                    stuckCount = 0;
                }
            }

            if (ShortestPathPlugin.getPathfinder() == null) {
                setTarget(null);
                return WalkerState.EXIT;
            }

            int indexOfStartPoint = getClosestTileIndex(path);
            if (indexOfStartPoint == -1) {
                Microbot.log("The walker is confused, unable to find our starting point in the web, exiting.");
                setTarget(null);
                return WalkerState.EXIT;
            }

            lastPosition = Rs2Player.getWorldLocation();

            if (Rs2Player.getWorldLocation().distanceTo(target) == 0 || path.size() <= 1) {
                setTarget(null);
                return WalkerState.ARRIVED;
            }

            // Edgeville/ardy wilderness lever warning
            if (Rs2Widget.isWidgetVisible(229, 1)) {
                if (Rs2Dialogue.getDialogueText() == null) return WalkerState.MOVING;
                if (Rs2Dialogue.getDialogueText().equalsIgnoreCase("Warning! The lever will teleport you deep into the Wilderness.")) {
                    Microbot.log("Detected Wilderness lever warning, interacting...");
                    Rs2Dialogue.clickContinue();
                    Rs2Dialogue.sleepUntilHasQuestion("Are you sure you wish to pull it?");
                    Rs2Dialogue.clickOption("Yes, I'm brave.");
                    sleep(1200, 2400);
                }
            }

            // entering desert warning
            if (Rs2Widget.clickWidget(565, 20)) {
                sleep(600);
                Rs2Widget.clickWidget(565, 17);
            }


            if (Rs2Widget.enterWilderness()) {
                sleepUntil(Rs2Player::isAnimating);
            }

            boolean doorOrTransportResult = false;
            for (int i = indexOfStartPoint; i < path.size(); i++) {
                WorldPoint currentWorldPoint = path.get(i);

                System.out.println("start loop " + i);

                if (ShortestPathPlugin.getMarker() == null) {
                    System.out.println("marker is null");
                    break;
                }

                if (!isNearPath()) {
                    System.out.println("No longer near path");
                    if (config.cancelInstead()) {
                        System.out.println("cancel instead of recalculate");
                        setTarget(null);
                    } else {
                        recalculatePath();
                    }
                    break;
                }

                doorOrTransportResult = handleDoors(path, i);
                if (doorOrTransportResult) {
                    System.out.println("break out of door");
                    break;
                }

                if (!Microbot.getClient().getTopLevelWorldView().isInstance()) {
                    doorOrTransportResult = handleTransports(path, i);
                }

                if (doorOrTransportResult) {
                    System.out.println("break out of transport");
                    break;
                }

                if (!Rs2Tile.isTileReachable(currentWorldPoint) && !Microbot.getClient().getTopLevelWorldView().isInstance()) {
                    continue;
                }
                nextWalkingDistance = Rs2Random.between(7, 11);
                if (currentWorldPoint.distanceTo2D(Rs2Player.getWorldLocation()) > nextWalkingDistance) {
                    if (Microbot.getClient().getTopLevelWorldView().isInstance()) {
                        if (Rs2Walker.walkMiniMap(currentWorldPoint)) {
                            final WorldPoint b = currentWorldPoint;
                            sleepUntil(() -> b.distanceTo2D(Rs2Player.getWorldLocation()) < nextWalkingDistance, 2000);
                        }
                    } else {
                        if (currentWorldPoint.distanceTo2D(Rs2Player.getWorldLocation()) > nextWalkingDistance) {
                            if (Rs2Walker.walkMiniMap(getPointWithWallDistance(currentWorldPoint))) {
                                final WorldPoint b = currentWorldPoint;
                                sleepUntil(() -> b.distanceTo2D(Rs2Player.getWorldLocation()) < nextWalkingDistance, 2000);
                            }
                        }
                    }
                }
            }


            if (!doorOrTransportResult) {
                if (!path.isEmpty()) {
                    var moveableTiles = Rs2Tile.getReachableTilesFromTile(path.get(path.size() - 1), Math.min(3, distance)).keySet().toArray(new WorldPoint[0]);
                    var finalTile = moveableTiles.length > 0 ? moveableTiles[Rs2Random.between(0, moveableTiles.length)] : path.get(path.size() - 1);

                    if (Rs2Tile.isTileReachable(finalTile)) {
                        if (Rs2Walker.walkFastCanvas(finalTile)) {
                            sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(finalTile) < 2, 3000);
                        }
                    }

                }
            }
            if (Rs2Player.getWorldLocation().distanceTo(target) < distance) {
                setTarget(null);
                return WalkerState.ARRIVED;
            } else {
                return processWalk(target, distance);
            }
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) {
                setTarget(null);
                return WalkerState.EXIT;
            }
            ex.printStackTrace(System.out);
            Microbot.log("Microbot Walker Exception " + ex.getMessage());
            System.out.println(ex.getMessage());
        }
        return WalkerState.EXIT;
    }

    public static boolean walkNextTo(GameObject target) {
        Rs2WorldArea gameObjectArea = new Rs2WorldArea(Objects.requireNonNull(Rs2GameObject.getWorldArea(target)));
        List<WorldPoint> interactablePoints = gameObjectArea.getInteractable();

        if (interactablePoints.isEmpty()) {
            interactablePoints.addAll(gameObjectArea.offset(1).toWorldPointList());
            interactablePoints.removeIf(gameObjectArea::contains);
        }

        WorldPoint walkableInteractPoint = interactablePoints.stream()
                .filter(Rs2Tile::isWalkable)
                .findFirst()
                .orElse(null);
        // Priority to a walkable tile, otherwise walk to the first tile next to locatable

        if(walkableInteractPoint != null && walkableInteractPoint.equals(Rs2Player.getWorldLocation()))
            return true;
        return walkableInteractPoint != null ? walkTo(walkableInteractPoint) : walkTo(interactablePoints.get(0));
    }

    public static void walkNextToInstance(GameObject target) {
        Rs2WorldArea gameObjectArea = new Rs2WorldArea(Objects.requireNonNull(Rs2GameObject.getWorldArea(target)));
        List<WorldPoint> interactablePoints = gameObjectArea.getInteractable();

        if (interactablePoints.isEmpty()) {
            interactablePoints.addAll(gameObjectArea.offset(1).toWorldPointList());
            interactablePoints.removeIf(gameObjectArea::contains);
        }

        WorldPoint walkableInteractPoint = interactablePoints.stream()
                .filter(Rs2Tile::isWalkable)
                .findFirst()
                .orElse(null);
        // Priority to a walkable tile, otherwise walk to the first tile next to locatable
        if (walkableInteractPoint != null) {
            if(walkableInteractPoint.equals(Rs2Player.getWorldLocation()))
                return;
            walkFastLocal(LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), walkableInteractPoint));
        } else {
            walkFastLocal(LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), interactablePoints.get(0)));
        }
    }

    public static WorldPoint getPointWithWallDistance(WorldPoint target) {
        var tiles = Rs2Tile.getReachableTilesFromTile(target, 1);

        var localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), target);
        if (Microbot.getClient().getTopLevelWorldView().getCollisionMaps() != null && localPoint != null) {
            int[][] flags = Microbot.getClient().getTopLevelWorldView().getCollisionMaps()[Microbot.getClient().getTopLevelWorldView().getPlane()].getFlags();

            if (hasMinimapRelevantMovementFlag(localPoint, flags)) {
                for (var tile : tiles.keySet()) {
                    var localTilePoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), tile);
                    if (localTilePoint == null)
                        continue;

                    if (!hasMinimapRelevantMovementFlag(localTilePoint, flags))
                        return tile;
                }
            }

            int data = flags[localPoint.getSceneX()][localPoint.getSceneY()];

            Set<MovementFlag> movementFlags = MovementFlag.getSetFlags(data);

            if (movementFlags.contains(MovementFlag.BLOCK_MOVEMENT_EAST)
                    || movementFlags.contains(MovementFlag.BLOCK_MOVEMENT_WEST)
                    || movementFlags.contains(MovementFlag.BLOCK_MOVEMENT_NORTH)
                    || movementFlags.contains(MovementFlag.BLOCK_MOVEMENT_SOUTH)) {
                for (var tile : tiles.keySet()) {
                    var localTilePoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), tile);
                    if (localTilePoint == null)
                        continue;

                    int tileData = flags[localTilePoint.getSceneX()][localTilePoint.getSceneY()];
                    Set<MovementFlag> tileFlags = MovementFlag.getSetFlags(tileData);

                    if (tileFlags.isEmpty())
                        return tile;
                }
            }
        }

        return target;
    }

    static boolean hasMinimapRelevantMovementFlag(LocalPoint point, int[][] flagMap) {
        int data = flagMap[point.getSceneX()][point.getSceneY()];
        Set<MovementFlag> movementFlags = MovementFlag.getSetFlags(data);

        if (movementFlags.contains(MovementFlag.BLOCK_MOVEMENT_EAST)
                && Rs2Tile.isWalkable(point.dx(1)))
            return true;

        if (movementFlags.contains(MovementFlag.BLOCK_MOVEMENT_WEST)
                && Rs2Tile.isWalkable(point.dx(-1)))
            return true;

        if (movementFlags.contains(MovementFlag.BLOCK_MOVEMENT_NORTH)
                && Rs2Tile.isWalkable(point.dy(1)))
            return true;

        return movementFlags.contains(MovementFlag.BLOCK_MOVEMENT_SOUTH)
                && Rs2Tile.isWalkable(point.dy(-1));
    }

    public static boolean walkMiniMap(WorldPoint worldPoint, double zoomDistance) {
        if (Microbot.getClient().getMinimapZoom() != zoomDistance)
            Microbot.getClient().setMinimapZoom(zoomDistance);

        Point point = Rs2MiniMap.worldToMinimap(worldPoint);

        if (point == null) return false;
        //if (!Rs2MiniMap.isPointInsideMinimap(point)) return false;

        Microbot.getMouse().click(point);
        return true;
    }


    public static boolean walkMiniMap(WorldPoint worldPoint) {
        return walkMiniMap(worldPoint, 5);
    }

    /**
     * Used in instances like vorkath, jad, nmz
     *
     * @param localPoint A two-dimensional point in the local coordinate space.
     */
    public static void walkFastLocal(LocalPoint localPoint) {
        Point canv = Perspective.localToCanvas(Microbot.getClient(), localPoint, Microbot.getClient().getTopLevelWorldView().getPlane());
        int canvasX = canv != null ? canv.getX() : -1;
        int canvasY = canv != null ? canv.getY() : -1;

        Microbot.doInvoke(new NewMenuEntry(canvasX, canvasY, MenuAction.WALK.getId(), 0, -1, "Walk here"), new Rectangle(1, 1, Microbot.getClient().getCanvasWidth(), Microbot.getClient().getCanvasHeight()));
        //Rs2Reflection.invokeMenu(canvasX, canvasY, MenuAction.WALK.getId(), 0, -1, "Walk here", "", -1, -1);
    }

    public static boolean walkFastCanvas(WorldPoint worldPoint) {
        return walkFastCanvas(worldPoint, true);
    }

    public static boolean walkFastCanvas(WorldPoint worldPoint, boolean toggleRun) {

        Rs2Player.toggleRunEnergy(toggleRun);
        Point canv;
        LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), worldPoint);

        if (Microbot.getClient().getTopLevelWorldView().isInstance() && localPoint == null) {
            localPoint = Rs2LocalPoint.fromWorldInstance(worldPoint);
        }

        if (localPoint == null) {
            Microbot.log("Tried to walk worldpoint " + worldPoint + " using the canvas but localpoint returned null");
            return false;
        }

        canv = Perspective.localToCanvas(Microbot.getClient(), localPoint, Microbot.getClient().getTopLevelWorldView().getPlane());

        int canvasX = canv != null ? canv.getX() : -1;
        int canvasY = canv != null ? canv.getY() : -1;

        //if the tile is not on screen, use minimap
        if (!Rs2Camera.isTileOnScreen(localPoint) || canvasX < 0 || canvasY < 0) {
            return Rs2Walker.walkMiniMap(worldPoint);
        }

        Microbot.doInvoke(new NewMenuEntry(canvasX, canvasY, MenuAction.WALK.getId(), 0, 0, "Walk here"), new Rectangle(canvasX, canvasY, Microbot.getClient().getCanvasWidth(), Microbot.getClient().getCanvasHeight()));
        return true;
    }

    public static WorldPoint walkCanvas(WorldPoint worldPoint) {
        LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), worldPoint);
        if (localPoint == null) {
            Microbot.log("Tried to walkCanvas but localpoint returned null");
            return null;
        }
        Point point = Perspective.localToCanvas(Microbot.getClient(), localPoint, Microbot.getClient().getTopLevelWorldView().getPlane());

        if (point == null) return null;

        Microbot.getMouse().click(point);

        return worldPoint;
    }

    /**
     * Gets the total amount of tiles to travel to destination
     * @param source source
     * @param destination destination
     * @return total amount of tiles
     */
    public static int getTotalTiles(WorldPoint start, WorldPoint destination) {
        if (ShortestPathPlugin.getPathfinderConfig().getTransports().isEmpty()) {
            ShortestPathPlugin.getPathfinderConfig().refresh();
        }
        Pathfinder pathfinder = new Pathfinder(ShortestPathPlugin.getPathfinderConfig(), start, destination);

        pathfinder.run();
        List<WorldPoint> path = pathfinder.getPath();

        if (path.isEmpty() || path.get(path.size() - 1).getPlane() != destination.getPlane()) return Integer.MAX_VALUE;
        WorldArea pathArea = new WorldArea(path.get(path.size() - 1), 2, 2);
        WorldArea objectArea = new WorldArea(destination, 2, 2);
        if (!pathArea.intersectsWith2D(objectArea)) {
            return Integer.MAX_VALUE;
        }

        return path.size();
    }

    /**
     * Gets the total amount of tiles to travel to destination
     * @param destination destination
     * @return total amount of tiles
     */
    public static int getTotalTiles(WorldPoint destination) {
        return getTotalTiles(Rs2Player.getWorldLocation(), destination);
    }

    // takes an avg 200-300 ms
    // Used mainly for agility, might have to tweak this for other stuff
    public static boolean canReach(WorldPoint worldPoint, int sizeX, int sizeY, int pathSizeX, int pathSizeY) {
        if (ShortestPathPlugin.getPathfinderConfig().getTransports().isEmpty()) {
            ShortestPathPlugin.getPathfinderConfig().refresh();
        }
        Pathfinder pathfinder = new Pathfinder(ShortestPathPlugin.getPathfinderConfig(), Rs2Player.getWorldLocation(), worldPoint);
        pathfinder.run();
        WorldArea pathArea = new WorldArea(pathfinder.getPath().get(pathfinder.getPath().size() - 1), pathSizeX, pathSizeY);
        WorldArea objectArea = new WorldArea(worldPoint, sizeX + 2, sizeY + 2);
        return pathArea
                .intersectsWith2D(objectArea);
    }

    // takes an avg 200-300 ms
    // Used mainly for agility, might have to tweak this for other stuff
    public static boolean canReach(WorldPoint worldPoint, int sizeX, int sizeY) {
        if (ShortestPathPlugin.getPathfinderConfig().getTransports().isEmpty()) {
            ShortestPathPlugin.getPathfinderConfig().refresh();
        }
        Pathfinder pathfinder = new Pathfinder(ShortestPathPlugin.getPathfinderConfig(), Rs2Player.getWorldLocation(), worldPoint);
        pathfinder.run();
        WorldArea pathArea = new WorldArea(pathfinder.getPath().get(pathfinder.getPath().size() - 1), 3, 3);
        WorldArea objectArea = new WorldArea(worldPoint, sizeX + 2, sizeY + 2);
        return pathArea
                .intersectsWith2D(objectArea);
    }

    /**
     * used for quest script interacting with object
     * also used for finding the nearest bank
     * @param worldPoint
     * @return
     */
    public static boolean canReach(WorldPoint worldPoint) {
        if (ShortestPathPlugin.getPathfinderConfig().getTransports().isEmpty()) {
            ShortestPathPlugin.getPathfinderConfig().refresh();
        }
        Pathfinder pathfinder = new Pathfinder(ShortestPathPlugin.getPathfinderConfig(), Rs2Player.getWorldLocation(), worldPoint);
        pathfinder.run();
        List<WorldPoint> path = pathfinder.getPath();
        if (path.isEmpty() || path.get(path.size() - 1).getPlane() != worldPoint.getPlane()) return false;
        WorldArea pathArea = new WorldArea(path.get(path.size() - 1), 2, 2);
        WorldArea objectArea = new WorldArea(worldPoint, 2, 2);
        return pathArea
                .intersectsWith2D(objectArea);
    }

    /**
 * Retrieves the walk path from the player's current location to the specified target location.
 *
 * @param target The target `WorldPoint` to which the path should be calculated.
 * @return A list of `WorldPoint` objects representing the path from the player's current location to the target.
 */
public static List<WorldPoint> getWalkPath(WorldPoint target) {
    if (ShortestPathPlugin.getPathfinderConfig().getTransports().isEmpty()) {
        ShortestPathPlugin.getPathfinderConfig().refresh();
    }
    Pathfinder pathfinder = new Pathfinder(ShortestPathPlugin.getPathfinderConfig(), Rs2Player.getWorldLocation(), target);
    pathfinder.run();
    return pathfinder.getPath();
}

    public static boolean isCloseToRegion(int distance, int regionX, int regionY) {
        WorldPoint worldPoint = WorldPoint.fromRegion(Microbot.getClient().getLocalPlayer().getWorldLocation().getRegionID(),
                regionX,
                regionY,
                Microbot.getClient().getTopLevelWorldView().getPlane());

        return worldPoint.distanceTo(Microbot.getClient().getLocalPlayer().getWorldLocation()) < distance;
    }

    public static int distanceToRegion(int regionX, int regionY) {
        WorldPoint worldPoint = WorldPoint.fromRegion(Microbot.getClient().getLocalPlayer().getWorldLocation().getRegionID(),
                regionX,
                regionY,
                Microbot.getClient().getTopLevelWorldView().getPlane());

        return worldPoint.distanceTo(Microbot.getClient().getLocalPlayer().getWorldLocation());
    }

    private static boolean handleDoors(List<WorldPoint> path, int index) {

        if (ShortestPathPlugin.getPathfinder() == null) return false;

        if (index == path.size() - 1) return false;

        var doorActions = Arrays.asList("pay-toll", "pick-lock", "walk-through", "go-through", "open");

        // Check this and the next tile for door objects
        for (int doorIndex = index; doorIndex < index + 2; doorIndex++) {
            var point = path.get(doorIndex);

            // Handle wall and game objects
            TileObject object = null;
            var tile = Rs2GameObject.getTiles(3).stream()
                    .filter(x -> x.getWorldLocation().equals(point))
                    .findFirst().orElse(null);
            if (tile != null)
                object = tile.getWallObject();

            if (object == null)
                object = Rs2GameObject.getGameObject(point);

            if (object == null) continue;

            var objectComp = Rs2GameObject.getObjectComposition(object.getId());
            if (objectComp == null) continue;

            // Match action
            var action = Arrays.stream(objectComp.getActions())
                    .filter(x -> x != null && doorActions.stream().anyMatch(doorAction -> x.toLowerCase().startsWith(doorAction)))
                    .min(Comparator.comparing(x -> doorActions.indexOf(
                            doorActions.stream().filter(doorAction -> x.toLowerCase().startsWith(doorAction)).findFirst().orElse(""))))
                    .orElse(null);

            if (action == null) continue;

            boolean found = false;
            if (object instanceof WallObject) {
                // Match wall objects by orientation
                var orientation = ((WallObject) object).getOrientationA();

                if (doorIndex == index) {
                    // Forward
                    var neighborPoint = path.get(doorIndex + 1);
                    found = searchNeighborPoint(orientation, point, neighborPoint);
                    System.out.println(doorIndex + " wallobject ");
                } else if (doorIndex == index + 1) {
                    // Backward
                    var neighborPoint = path.get(doorIndex - 1);
                    found = searchNeighborPoint(orientation, point, neighborPoint);
                    System.out.println(doorIndex + " else  wallobject ");
                    // Diagonal objects with any orientation
                    if (index + 2 < path.size() && (orientation == 16 || orientation == 32 || orientation == 64 || orientation == 128)) {
                        var prevPoint = path.get(doorIndex - 1);
                        var nextPoint = path.get(doorIndex + 1);

                        if (Math.abs(prevPoint.getX() - nextPoint.getX()) > 0 && Math.abs(prevPoint.getY() - nextPoint.getY()) > 0) {
                            System.out.println("math abs found door");
                            found = true;
                        }
                    }
                }
            } else if (object instanceof GameObject) {
                // Match game objects by name
                // Orientation does not work as game objects are not strictly oriented like walls
                var objectNames = List.of("door");

                if (objectNames.contains(objectComp.getName().toLowerCase())) {
                    System.out.println("found door " + objectComp.getName());
                    found = true;
                }
            }


            if (found) {
                Rs2GameObject.interact(object, action);
                Rs2Player.waitForWalking();
                return true;
            }
        }

        return false;
    }

    private static boolean searchNeighborPoint(int orientation, WorldPoint point, WorldPoint neighborPoint) {
        return orientation == 1 && point.dx(-1).getX() == neighborPoint.getX()
                || orientation == 4 && point.dx(+1).getX() == neighborPoint.getX()
                || orientation == 2 && point.dy(1).getY() == neighborPoint.getY()
                || orientation == 8 && point.dy(-1).getY() == neighborPoint.getY();
    }

    /**
     * @param path list of worldpoints
     * @return closest tile index
     */
    public static int getClosestTileIndex(List<WorldPoint> path) {

        var tiles = Rs2Tile.getReachableTilesFromTile(Rs2Player.getWorldLocation(), 20);

        //Exception to handle objects that handle long animations or walk
        /**
         * Exception to handle objects that handle long animations or walk
         * ignore colission if we did not find a valid tile to walk on
         * this is to ensure we stay on the path even if we are on a agility obstacle
         */
        if (tiles.keySet().isEmpty()) {
            tiles = Rs2Tile.getReachableTilesFromTileIgnoreCollision(Rs2Player.getWorldLocation(), 20);
        }
        final HashMap<WorldPoint, Integer> _tiles = tiles;

        WorldPoint startPoint = path.stream()
                .min(Comparator.comparingInt(a -> _tiles.getOrDefault(a, Integer.MAX_VALUE)))
                .orElse(null);

        boolean noMatchingTileFound = path.stream()
                .allMatch(a -> _tiles.getOrDefault(a, Integer.MAX_VALUE) == Integer.MAX_VALUE);

        /**
         * Check if the startPoint is null or no matching tile is found
         * If either condition is true, proceed to find the closest index in the path list.
         */
        if (startPoint == null || noMatchingTileFound) {
            Optional<Integer> closestIndexOptional = IntStream.range(0, path.size())
                    .boxed()
                    .min(Comparator.comparingInt(i -> Rs2Player.getWorldLocation().distanceTo(path.get(i))));
            if (closestIndexOptional.isPresent()) {
                return closestIndexOptional.get();
            }
        }

        return IntStream.range(0, path.size())
                .filter(i -> path.get(i).equals(startPoint))
                .findFirst()
                .orElse(-1);
    }

    /**
     * Force the walker to recalculate path
     */
    public static void recalculatePath() {
        Rs2Walker.setTarget(null);
        WorldPoint _currentTarget = currentTarget;
        Rs2Walker.setTarget(_currentTarget);
    }

    /**
     * @param target
     */
    public static void setTarget(WorldPoint target) {
        if (target != null && !Microbot.isLoggedIn()) return;
        Player localPlayer = Microbot.getClient().getLocalPlayer();
        if (!ShortestPathPlugin.isStartPointSet() && localPlayer == null) {
            return;
        }

        currentTarget = target;

        if (target == null) {
            synchronized (ShortestPathPlugin.getPathfinderMutex()) {
                if (ShortestPathPlugin.getPathfinder() != null) {
                    ShortestPathPlugin.getPathfinder().cancel();
                }
                ShortestPathPlugin.setPathfinder(null);
            }

            Microbot.getWorldMapPointManager().remove(ShortestPathPlugin.getMarker());
            ShortestPathPlugin.setMarker(null);
            ShortestPathPlugin.setStartPointSet(false);
        } else {
            Microbot.getWorldMapPointManager().removeIf(x -> x == ShortestPathPlugin.getMarker());
            ShortestPathPlugin.setMarker(new WorldMapPoint(target, ShortestPathPlugin.MARKER_IMAGE));
            ShortestPathPlugin.getMarker().setName("Target");
            ShortestPathPlugin.getMarker().setTarget(ShortestPathPlugin.getMarker().getWorldPoint());
            ShortestPathPlugin.getMarker().setJumpOnClick(true);
            Microbot.getWorldMapPointManager().add(ShortestPathPlugin.getMarker());

            WorldPoint start = Microbot.getClient().getTopLevelWorldView().isInstance() ?
                    WorldPoint.fromLocalInstance(Microbot.getClient(), localPlayer.getLocalLocation()) : localPlayer.getWorldLocation();
            ShortestPathPlugin.setLastLocation(start);
            if (ShortestPathPlugin.isStartPointSet() && ShortestPathPlugin.getPathfinder() != null) {
                start = ShortestPathPlugin.getPathfinder().getStart();
            }
            if (Microbot.getClient().isClientThread()) {
                final WorldPoint _start = start;
                Microbot.getClientThread().runOnSeperateThread(() -> restartPathfinding(_start, target));
            } else {
                restartPathfinding(start, target);
            }
        }
    }

    /**
     * @param start
     * @param end
     */
    public static boolean restartPathfinding(WorldPoint start, WorldPoint end) {
        if (Microbot.getClient().isClientThread()) return false;

        if (ShortestPathPlugin.getPathfinder() != null) {
            ShortestPathPlugin.getPathfinder().cancel();
            ShortestPathPlugin.getPathfinderFuture().cancel(true);
        }

        if (ShortestPathPlugin.getPathfindingExecutor() == null) {
            ThreadFactory shortestPathNaming = new ThreadFactoryBuilder().setNameFormat("shortest-path-%d").build();
            ShortestPathPlugin.setPathfindingExecutor(Executors.newSingleThreadExecutor(shortestPathNaming));
        }

        ShortestPathPlugin.getPathfinderConfig().refresh();
        if (Rs2Player.isInCave()) {
            Pathfinder pathfinder = new Pathfinder(ShortestPathPlugin.getPathfinderConfig(), start, end);
            pathfinder.run();
            ShortestPathPlugin.getPathfinderConfig().setIgnoreTeleportAndItems(true);
            Pathfinder pathfinderWithoutTeleports = new Pathfinder(ShortestPathPlugin.getPathfinderConfig(), start, end);
            pathfinderWithoutTeleports.run();
            if (pathfinder.getPath().size() >= pathfinderWithoutTeleports.getPath().size()) {
                ShortestPathPlugin.setPathfinder(pathfinderWithoutTeleports);
            } else {
                ShortestPathPlugin.setPathfinder(pathfinder);
            }
            ShortestPathPlugin.getPathfinderConfig().setIgnoreTeleportAndItems(false);
        } else {
            ShortestPathPlugin.setPathfinder(new Pathfinder(ShortestPathPlugin.getPathfinderConfig(), start, end));
            ShortestPathPlugin.setPathfinderFuture(ShortestPathPlugin.getPathfindingExecutor().submit(ShortestPathPlugin.getPathfinder()));
        }
        return true;
    }

    /**
     * @param point
     * @return
     */
    public static Tile getTile(WorldPoint point) {
        LocalPoint a;
        if (Microbot.getClient().getTopLevelWorldView().isInstance()) {
            WorldPoint instancedWorldPoint = WorldPoint.toLocalInstance(Microbot.getClient().getTopLevelWorldView(), point).stream().findFirst().orElse(null);
            if (instancedWorldPoint == null) {
                Microbot.log("getTile instancedWorldPoint is null");
                return null;
            }
            a = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), instancedWorldPoint);
        } else {
            a = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), point);
        }
        if (a == null) {
            return null;
        }
        return Microbot.getClient().getTopLevelWorldView().getScene().getTiles()[point.getPlane()][a.getSceneX()][a.getSceneY()];
    }

    /**
     * @param path
     * @param indexOfStartPoint
     * @return
     */
    public static boolean handleTransports(List<WorldPoint> path, int indexOfStartPoint) {

        for (Transport transport : ShortestPathPlugin.getTransports().getOrDefault(path.get(indexOfStartPoint), new HashSet<>())) {
            Collection<WorldPoint> worldPointCollections;
            //in some cases the getOrigin is null, for teleports that start the player location
            if (transport.getOrigin() == null) {
                worldPointCollections = Collections.singleton(null);
            } else {
                worldPointCollections = WorldPoint.toLocalInstance(Microbot.getClient().getTopLevelWorldView(), transport.getOrigin());
            }
            for (WorldPoint origin : worldPointCollections) {
                if (transport.getOrigin() != null && Rs2Player.getWorldLocation().getPlane() != transport.getOrigin().getPlane()) {
                    continue;
                }

                for (int i = indexOfStartPoint; i < path.size(); i++) {
                    if (origin != null && origin.getPlane() != Rs2Player.getWorldLocation().getPlane())
                        continue;
                    if (path.stream().noneMatch(x -> x.equals(transport.getDestination()))) continue;
                    if ((transport.getType() == TransportType.TELEPORTATION_ITEM ||
                            transport.getType() == TransportType.TELEPORTATION_SPELL) &&
                                    Rs2Player.getWorldLocation().distanceTo(transport.getDestination()) < 3) continue;

                    // we don't need to check for teleportation_item & teleportation_spell as they will be set on the first tile
                    if (transport.getType() != TransportType.TELEPORTATION_ITEM && transport.getType() != TransportType.TELEPORTATION_SPELL) {
                        int indexOfOrigin = IntStream.range(0, path.size())
                                .filter(f -> path.get(f).equals(transport.getOrigin()))
                                .findFirst()
                                .orElse(-1);
                        int indexOfDestination = IntStream.range(0, path.size())
                                .filter(f -> path.get(f).equals(transport.getDestination()))
                                .findFirst()
                                .orElse(-1);
                        if (indexOfDestination == -1) continue;
                        if (indexOfOrigin == -1) continue;
                        if (indexOfDestination < indexOfOrigin) continue;
                    }

                    if (path.get(i).equals(origin)) {
                        if (transport.getType() == TransportType.SHIP || transport.getType() == TransportType.NPC || transport.getType() == TransportType.BOAT
                                || transport.getType() == TransportType.CHARTER_SHIP) {

                            Rs2NpcModel npc = Rs2Npc.getNpc(transport.getName());

                            if (Rs2Npc.canWalkTo(npc, 20) && Rs2Npc.interact(npc, transport.getAction())) {
                                Rs2Player.waitForWalking();
                                if (Rs2Dialogue.clickOption("I'm just going to Pirates' cove")) {
                                    sleep(600 * 2);
                                    Rs2Dialogue.clickContinue();
                                }
                                sleepUntil(() -> !Rs2Player.isAnimating());
                                sleepUntilTrue(() -> Rs2Player.getWorldLocation().distanceTo(transport.getDestination()) < 10);
                                sleep(600 * 4);
                            } else {
                                Rs2Walker.walkFastCanvas(path.get(i));
                                sleep(1200, 1600);
                            }
                        }
                    }

                    if (handleTrapdoor(transport)) {
                        sleepUntil(() -> !Rs2Player.isAnimating());
                        sleepUntilTrue(() -> Rs2Player.getWorldLocation().distanceTo(transport.getDestination()) < 10);
                        break;
                    }

                    if (transport.getType() == TransportType.CHARTER_SHIP) {
                        if (Rs2Widget.getWidget(72, 0) != null) {
                            Widget destination = Rs2Widget.findWidget(transport.getDisplayInfo(), Arrays.stream(Rs2Widget.getWidget(72, 0).getStaticChildren()).collect(Collectors.toList()), false);
                            if (destination == null) break;

                            Rs2Widget.clickWidget(destination);
                            sleepUntil(() -> !Rs2Player.isAnimating());
                            sleepUntilTrue(() -> Rs2Player.getWorldLocation().distanceTo(transport.getDestination()) < 10);
                            sleep(600 * 2); // wait 2 ticks befor einteracting, this is a delay of ships
                        }
                    }

                    if (transport.getType() == TransportType.SPIRIT_TREE) {
                        if (handleSpiritTree(transport)) {
                            sleepUntil(() -> !Rs2Player.isAnimating());
                            sleepUntilTrue(() -> Rs2Player.getWorldLocation().distanceTo(transport.getDestination()) < 10);
                            break;
                        }
                    }
                    
                    if (transport.getType() == TransportType.QUETZAL) {
                        if (handleQuetzal(transport)) {
                            sleep(600 * 2); // wait 2 extra ticks before walking
                            break;
                        }
                    }
                    
                    if (transport.getType() == TransportType.MAGIC_CARPET) {
                        if (handleMagicCarpet(transport)) {
                            sleep(600 * 2); // wait 2 extra ticks before walking
                            break;
                        }
                    }
                    
                    if (transport.getType() == TransportType.WILDERNESS_OBELISK) {
                        if (handleWildernessObelisk(transport)) {
                            sleep(600 * 2);
                            break;
                        }
                    }

                    if (transport.getType() == TransportType.GNOME_GLIDER) {
                        if (handleGlider(transport)) {
                            sleepUntil(() -> !Rs2Player.isAnimating());
                            sleepUntilTrue(() -> Rs2Player.getWorldLocation().distanceTo(transport.getDestination()) < 10);
                            sleep(600 * 3); // wait 3 extra ticks before walking
                            break;
                        }
                    }

                    if (transport.getType() == TransportType.FAIRY_RING && !Rs2Player.getWorldLocation().equals(transport.getDestination())) {
                        handleFairyRing(transport);
                    }

                    if (transport.getType() == TransportType.TELEPORTATION_ITEM) {
                        if (handleTeleportItem(transport)) {
                            sleepUntil(() -> !Rs2Player.isAnimating());
                            sleepUntilTrue(() -> Rs2Player.getWorldLocation().distanceTo(transport.getDestination()) < 10);
                            break;
                        }
                    }

                    if (transport.getType() == TransportType.TELEPORTATION_SPELL) {
                        //if (Rs2Player.getWorldLocation().distanceTo(transport.getDestination()) < config.distanceBeforeUsingTeleport()) break;
                        if (handleTeleportSpell(transport)) {
                            sleepUntil(() -> !Rs2Player.isAnimating());
                            sleepUntilTrue(() -> Rs2Player.getWorldLocation().distanceTo(transport.getDestination()) < 10);
                            Rs2Tab.switchToInventoryTab();
                            break;
                        }
                    }
                    

                    GameObject gameObject = Rs2GameObject.getGameObjects(transport.getObjectId(), transport.getOrigin()).stream().findFirst().orElse(null);
                    //check game objects
                    if (gameObject != null && gameObject.getId() == transport.getObjectId()) {
                        if (!Rs2Tile.isTileReachable(transport.getOrigin())) {
                            break;
                        }
                        handleObject(transport, gameObject);
                        sleepUntil(() -> !Rs2Player.isAnimating());
                        return sleepUntilTrue(() -> Rs2Player.getWorldLocation().distanceTo(transport.getDestination()) < 10);
                    }

                    //check tile objects
                    List<TileObject> tileObjects = Rs2GameObject.getTileObjects(transport.getObjectId(), transport.getOrigin());
                    TileObject tileObject = tileObjects.stream().findFirst().orElse(null);
                    if (tileObject instanceof GroundObject)
                        tileObject = tileObjects.stream()
                                .filter(x -> !x.getWorldLocation().equals(Rs2Player.getWorldLocation()))
                                .min(Comparator.comparing(x -> ((TileObject) x).getWorldLocation().distanceTo(transport.getOrigin()))
                                        .thenComparing(x -> ((TileObject) x).getWorldLocation().distanceTo(transport.getDestination()))).orElse(null);

                    if (tileObject != null && tileObject.getId() == transport.getObjectId()) {
                        if (tileObject.getId() != 16533 && !Rs2Tile.isTileReachable(transport.getOrigin())) {
                            break;
                        }
                        handleObject(transport, tileObject);
                        sleepUntil(() -> !Rs2Player.isAnimating());
                        return sleepUntilTrue(() -> Rs2Player.getWorldLocation().distanceTo(transport.getDestination()) < 10);
                    }
                    
                    // check wall objects
                    List<WallObject> wallObjects = Rs2GameObject.getWallObjects(transport.getObjectId(), transport.getOrigin());
                    TileObject wallObject = wallObjects.stream().findFirst().orElse(null);
                    if (wallObject != null && wallObject.getId() == transport.getObjectId()) {
                        handleObject(transport, wallObject);
                        sleepUntil(() -> !Rs2Player.isAnimating());
                        return sleepUntilTrue(() -> Rs2Player.getWorldLocation().distanceTo(transport.getDestination()) < 10);
                    }
                }
            }
        }
        return false;
    }

    private static void handleObject(Transport transport, TileObject tileObject) {
        System.out.println("tile object");
        Rs2GameObject.interact(tileObject, transport.getAction());
        if (transport.getDestination().getPlane() == Rs2Player.getWorldLocation().getPlane()) {
            if (handleObjectExceptions(transport, tileObject)) return;
            if (transport.getType() == TransportType.AGILITY_SHORTCUT) {
                Rs2Player.waitForAnimation();
                sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(transport.getDestination()) <= 2, 10000);
            } else if (transport.getType() == TransportType.MINECART) {
                if (interactWithAdventureLog(transport)) {
                    sleep(600 * 2); // wait extra 2 game ticks before moving
                }
            } else if (transport.getType() == TransportType.TELEPORTATION_PORTAL) {
                    sleep(600 * 2); // wait extra 2 game ticks before moving
            } else {
                Rs2Player.waitForWalking();
                Rs2Dialogue.clickOption("Yes please"); //shillo village cart
            }
        } else {
            int z = Rs2Player.getWorldLocation().getPlane();
            sleepUntil(() -> Rs2Player.getWorldLocation().getPlane() != z);
            sleep((int) Rs2Random.gaussRand(1000.0, 300.0));
        }
    }

    private static boolean handleObjectExceptions(Transport transport, TileObject tileObject) {
        //Al kharid broken wall will animate once and then stop and then animate again
        if (tileObject.getId() == ObjectID.BROKEN_WALL_33344 || tileObject.getId() == ObjectID.BIG_WINDOW) {
            Rs2Player.waitForAnimation();
            Rs2Player.waitForAnimation();
            return true;
        }
        // Handle Leaves Traps in Isafdar Forest
        if (tileObject.getId() == ObjectID.LEAVES_3925) {
            Rs2Player.waitForAnimation(1200);
            if (Rs2Player.getWorldLocation().getY() > 6400) {
                Rs2GameObject.interact(ObjectID.PROTRUDING_ROCKS_3927);
                sleepUntil(() -> Rs2Player.getWorldLocation().getY() < 6400);
            } else {
                sleepUntil(() -> !Rs2Player.isMoving() && !Rs2Player.isAnimating());
            }
            return true;
        }
        // Handle Ferox Encalve Barrier
        if (tileObject.getId() == ObjectID.BARRIER_39652 || tileObject.getId() == ObjectID.BARRIER_39653) {
            if (Rs2Dialogue.isInDialogue()) {
                if (Rs2Dialogue.getDialogueText() == null) return false;
                if (Rs2Dialogue.getDialogueText().contains("When returning to the Enclave")) {
                    Rs2Dialogue.clickContinue();
                    Rs2Dialogue.sleepUntilSelectAnOption();
                    Rs2Dialogue.keyPressForDialogueOption("Yes, and don't ask again.");
                    Rs2Dialogue.sleepUntilNotInDialogue();
                    return true;
                }
            }
        }
        // Handle Cobwebs blocking path
        if (tileObject.getId() == ObjectID.WEB) {
            sleepUntil(() -> !Rs2Player.isMoving());
            if (Rs2GameObject.findObjectByIdAndDistance(ObjectID.WEB, 3) != null) {
                Rs2Player.waitForAnimation();
            } else {
                walkFastCanvas(transport.getDestination());
                sleepUntil(() -> Rs2Player.getWorldLocation().equals(transport.getDestination()));
            }
            return true;
        }
        
        // Handle Brimhaven Dungeon Entrance
        if (tileObject.getId() == 20877) {
            Rs2Dialogue.sleepUntilHasQuestion("Pay 875 coins to enter?");
            Rs2Dialogue.clickOption("Yes");
            sleepUntil(() -> Rs2Player.getWorldLocation().equals(transport.getDestination()));
            return true;
        }
        // Handle Brimhaven Dungeon Stepping Stones
        if (tileObject.getId() == ObjectID.STEPPING_STONE_21738 || tileObject.getId() == ObjectID.STEPPING_STONE_21739) {
            Rs2Player.waitForAnimation(4200);
            return true;
        }
        return false;
    }
    
    private static boolean handleWildernessObelisk(Transport transport) {
        GameObject obelisk = Rs2GameObject.getGameObjects(transport.getObjectId(), transport.getOrigin()).stream()
                .findFirst()
                .orElse(null);
        
        if (obelisk != null) {
            Rs2GameObject.interact(obelisk, transport.getAction());
            sleepUntil(() -> Rs2GameObject.getGameObjects(ObjectID.OBELISK_14825, transport.getOrigin()).stream().findFirst().orElse(null) != null);
            walkFastCanvas(transport.getOrigin());
            return sleepUntilTrue(() -> Rs2Player.getWorldLocation().distanceTo2D(transport.getDestination()) < OFFSET, 100, 10000);
        }
        return false;
    }

    private static boolean handleTeleportSpell(Transport transport) {
        if (Rs2Pvp.isInWilderness() && (Rs2Pvp.getWildernessLevelFrom(Rs2Player.getWorldLocation()) > (transport.getMaxWildernessLevel() + 1))) return false;
        boolean hasMultipleDestination = transport.getDisplayInfo().contains(":");
        
        String spellName = hasMultipleDestination
                ? transport.getDisplayInfo().split(":")[0].trim().toLowerCase()
                : transport.getDisplayInfo().toLowerCase();
        
        String option = hasMultipleDestination
                ? transport.getDisplayInfo().split(":")[1].trim().toLowerCase()
                : "cast";
        
        int identifier = hasMultipleDestination
                ? 2
                : 1;

        MagicAction magicSpell = Arrays.stream(MagicAction.values()).filter(x -> x.getName().toLowerCase().contains(spellName)).findFirst().orElse(null);
        if (magicSpell != null) {
            return Rs2Magic.cast(magicSpell, option, identifier);
        }
        return false;
    }

    private static boolean handleTeleportItem(Transport transport) {
        if (Rs2Pvp.isInWilderness() && (Rs2Pvp.getWildernessLevelFrom(Rs2Player.getWorldLocation()) > (transport.getMaxWildernessLevel() + 1))) return false;
        boolean succesfullAction = false;
        for (Set<Integer> itemIds : transport.getItemIdRequirements()) {
            if (succesfullAction)
                break;
            for (Integer itemId : itemIds) {
                if (Rs2Walker.currentTarget == null) break;
                if (Rs2Player.getWorldLocation().distanceTo2D(transport.getDestination()) < config.reachedDistance())
                    break;
                if (succesfullAction) break;

                //If an action is succesfully we break out of the loop
                succesfullAction = handleInventoryTeleports(transport, itemId) || handleWearableTeleports(transport, itemId);

            }
        }
        return succesfullAction;
    }

    public static boolean handleInventoryTeleports(Transport transport, int itemId) {
        Rs2ItemModel rs2Item = Rs2Inventory.get(itemId);
        if (rs2Item == null) return false;

        List<String> locationKeyWords = Arrays.asList("farm", "monastery", "lletya", "prifddinas", "rellekka", "waterbirth island", "neitiznot", "jatiszo",
                "ver sinhaza", "darkmeyer", "slepe", "troll stronghold", "weiss", "ecto", "burgh", "duradel", "gem mine", "nardah", "kalphite cave",
                "kourend woodland", "mount karuulm", "outside", "fishing guild", "otto's grotto", "stronghold slayer cave", "slayer tower", "fremennik", "tarn's lair", "dark beasts");
        List<String> genericKeyWords = Arrays.asList("invoke", "empty", "consume", "teleport", "rub", "break", "reminisce", "signal", "play");

        boolean hasMultipleDestination = transport.getDisplayInfo().contains(":");
        String destination = hasMultipleDestination
                ? transport.getDisplayInfo().split(":")[1].trim().toLowerCase()
                : transport.getDisplayInfo().trim().toLowerCase();

        // Check location keywords based on multiple destinations
        String itemAction = hasMultipleDestination
                ? Arrays.stream(rs2Item.getInventoryActions())
                .filter(action -> action != null && locationKeyWords.stream().anyMatch(keyword ->
                        destination.contains(keyword.toLowerCase()) && action.toLowerCase().contains(keyword.toLowerCase())))
                .findFirst()
                .orElse(null)
                : Arrays.stream(rs2Item.getInventoryActions())
                .filter(action -> action != null && locationKeyWords.stream().anyMatch(keyword -> action.toLowerCase().contains(keyword.toLowerCase())))
                .findFirst()
                .orElse(null);

        // If no location-based action found, try generic actions
        if (itemAction == null) {

            itemAction = Arrays.stream(rs2Item.getInventoryActions())
                    .filter(action -> action != null && genericKeyWords.stream().anyMatch(keyword -> action.toLowerCase().contains(keyword.toLowerCase())))
                    .min(Comparator.comparingInt(action ->
                            genericKeyWords.stream()
                                    .filter(keyword -> action.toLowerCase().contains(keyword.toLowerCase()))
                                    .mapToInt(genericKeyWords::indexOf)
                                    .findFirst()
                                    .orElse(Integer.MAX_VALUE)
                    ))
                    .orElse(null);
        }

        if (itemAction == null) return false;

        if (Rs2Inventory.interact(itemId, itemAction)) {
            if (itemAction.equalsIgnoreCase("rub") && (itemId == ItemID.XERICS_TALISMAN || transport.getDisplayInfo().toLowerCase().contains("skills necklace"))) {
                return interactWithAdventureLog(transport);
            }
            
            if (itemAction.equalsIgnoreCase("rub") && transport.getDisplayInfo().toLowerCase().contains("burning amulet")) {
                Rs2Dialogue.sleepUntilInDialogue();
                Rs2Dialogue.clickOption(destination);
                Rs2Dialogue.sleepUntilHasDialogueOption("Okay, teleport to level");
                Rs2Dialogue.clickOption("Okay, teleport to level");
            }
            
            if (itemAction.equalsIgnoreCase("teleport") && transport.getDisplayInfo().toLowerCase().contains("slayer ring")) {
                Rs2Dialogue.sleepUntilSelectAnOption();
                Rs2Dialogue.clickOption(destination);
            }

            if (itemAction.equalsIgnoreCase("rub") || itemAction.equalsIgnoreCase("reminisce")) {
                Rs2Dialogue.sleepUntilSelectAnOption();
                Rs2Dialogue.clickOption(destination);
            }

            Microbot.log("Traveling to " + transport.getDisplayInfo());
            return sleepUntilTrue(() -> Rs2Player.getWorldLocation().distanceTo2D(transport.getDestination()) < OFFSET, 100, 5000);
        }

        return false;
    }

    private static boolean handleWearableTeleports(Transport transport, int itemId) {
        if (Rs2Equipment.isWearing(itemId)) {
            if (transport.getDisplayInfo().contains(":")) {
                String[] values = transport.getDisplayInfo().split(":");
                String destination = values[1].trim().toLowerCase();
                Rs2ItemModel rs2Item = Rs2Equipment.get(itemId);
                if (transport.getDisplayInfo().toLowerCase().contains("slayer ring")) {
                    Rs2Equipment.invokeMenu(rs2Item, "teleport");
                    Rs2Dialogue.sleepUntilSelectAnOption();
                    Rs2Dialogue.clickOption(destination);
                } else {
                    Rs2Equipment.invokeMenu(rs2Item, destination);
                    if (transport.getDisplayInfo().toLowerCase().contains("burning amulet")) {
                        Rs2Dialogue.sleepUntilInDialogue();
                        Rs2Dialogue.clickOption("Okay, teleport to level");
                    }
                }
                Microbot.log("Traveling to " + transport.getDisplayInfo());
                return sleepUntilTrue(() -> Rs2Player.getWorldLocation().distanceTo2D(transport.getDestination()) < OFFSET, 100, 5000);
            }
        }
        return false;
    }

    public static boolean handleTrapdoor(Transport transport) {
        Map<Integer, Integer> trapdoors = new HashMap<>();
        trapdoors.put(1579, 1581); // closed trapdoor -> open trapdoor
        trapdoors.put(881, 882); // closed manhole -> open manhole (used for varrock sewers)

        for (Map.Entry<Integer, Integer> entry : trapdoors.entrySet()) {
            int closedTrapdoorId = entry.getKey();
            int openTrapdoorId = entry.getValue();

            if (transport.getObjectId() == openTrapdoorId) {
                if (Rs2GameObject.interact(closedTrapdoorId, "Open")) {
                    sleepUntil(() -> Rs2GameObject.exists(openTrapdoorId));
                }
                return Rs2GameObject.interact(openTrapdoorId, transport.getAction());
            }
        }
        return false;
    }

    /**
     * Checks if the player's current location is within the specified area defined by the given world points.
     *
     * @param worldPoints an array of two world points of the NW and SE corners of the area
     * @return true if the player's current location is within the specified area, false otherwise
     */
    public static boolean isInArea(WorldPoint... worldPoints) {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        return playerLocation.getX() <= worldPoints[0].getX() &&   // NW corner x
                playerLocation.getY() >= worldPoints[0].getY() &&   // NW corner y
                playerLocation.getX() >= worldPoints[1].getX() &&   // SE corner x
                playerLocation.getY() <= worldPoints[1].getY();     // SE corner Y
        // draws box from 2 points to check against all variations of player X,Y from said points.
    }

    /**
     * Checks if the player's current location is within the specified range from the given center point.
     *
     * @param centerOfArea a WorldPoint which is the center of the desired area,
     * @param range        an int of range to which the boundaries will be drawn in a square,
     * @return true if the player's current location is within the specified area, false otherwise
     */
    @Deprecated(since = "1.5.5", forRemoval = true)
    public static boolean isInArea(WorldPoint centerOfArea, int range) {
        WorldPoint nwCorner = new WorldPoint(centerOfArea.getX() + range + range, centerOfArea.getY() - range, centerOfArea.getPlane());
        WorldPoint seCorner = new WorldPoint(centerOfArea.getX() - range - range, centerOfArea.getY() + range, centerOfArea.getPlane());
        return isInArea(nwCorner, seCorner); // call to our sibling
    }

    public static boolean isNear() {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        int index = IntStream.range(0, ShortestPathPlugin.getPathfinder().getPath().size())
                .filter(f -> ShortestPathPlugin.getPathfinder().getPath().get(f).distanceTo2D(playerLocation) < 3)
                .findFirst()
                .orElse(-1);
        return index >= ShortestPathPlugin.getPathfinder().getPath().size() - 10;
    }

    /**
     * @param target
     * @return
     */
    public static boolean isNear(WorldPoint target) {
        return Rs2Player.getWorldLocation().equals(target);
    }

    public static boolean isNearPath() {
        if (ShortestPathPlugin.getPathfinder() == null || ShortestPathPlugin.getPathfinder() .getPath() == null || ShortestPathPlugin.getPathfinder().getPath().isEmpty() ||
                config.recalculateDistance() < 0 || lastPosition.equals(lastPosition = Rs2Player.getWorldLocation())) {
            return true;
        }

        var reachableTiles = Rs2Tile.getReachableTilesFromTile(Rs2Player.getWorldLocation(), config.recalculateDistance() - 1);
        for (WorldPoint point : ShortestPathPlugin.getPathfinder().getPath()) {
            if (reachableTiles.containsKey(point)) {
                return true;
            }
        }

        return false;
    }

    private static void checkIfStuck() {
        if (Rs2Player.getWorldLocation().equals(lastPosition)) {
            stuckCount++;
        } else {
            stuckCount = 0;
        }
    }

    /**
     * @param start
     */
    public void setStart(WorldPoint start) {
        if (ShortestPathPlugin.getPathfinder() == null) {
            return;
        }
        ShortestPathPlugin.setStartPointSet(true);
        if (Microbot.getClient().isClientThread()) {
            Microbot.getClientThread().runOnSeperateThread(() -> restartPathfinding(start, ShortestPathPlugin.getPathfinder().getTarget()));
        } else {
            restartPathfinding(start, ShortestPathPlugin.getPathfinder().getTarget());
        }
    }

    /**
     * Checks the distance between startpoint and endpoint using ShortestPath
     *
     * @param startpoint
     * @param endpoint
     * @return distance
     */
    public static int getDistanceBetween(WorldPoint startpoint, WorldPoint endpoint) {
        Pathfinder pathfinder = new Pathfinder(ShortestPathPlugin.getPathfinderConfig(), startpoint, endpoint);
        pathfinder.run();
        return pathfinder.getPath().size();
    }

    public static boolean handleSpiritTree(Transport transport) {
        // Get Transport Information
        String displayInfo = transport.getDisplayInfo();
        int objectId = transport.getObjectId();

        if (displayInfo == null || displayInfo.isEmpty()) return false;

        // Check if the widget is already visible
        if (!Rs2Widget.isHidden(ComponentID.ADVENTURE_LOG_CONTAINER)) {
            System.out.println("Widget is already visible. Skipping interaction.");
            char key = displayInfo.charAt(0);
            Rs2Keyboard.keyPress(key);
            Microbot.log("Pressing: " + key);
            return false;
        }

        // Find the spirit tree object
        TileObject spiritTree = Rs2GameObject.findObjectById(objectId);
        if (spiritTree == null) {
            return false;
        }

        // Interact with the spirit tree
        if (Rs2GameObject.interact(spiritTree, "travel")) {
            return interactWithAdventureLog(transport);
        }

        return false;

    }
    
    private static boolean handleQuetzal(Transport transport) {
        int varlamoreMapParentID = 874;
        String displayInfo = transport.getDisplayInfo();
        if (displayInfo == null || displayInfo.isEmpty()) return false;

        Rs2NpcModel renu = Rs2Npc.getNpc(NpcID.RENU_13350);

        if (Rs2Npc.canWalkTo(renu, 20) && Rs2Npc.interact(renu, "travel")) {
            Rs2Player.waitForWalking();
            boolean isVarlamoreMapVisible = sleepUntilTrue(() -> Rs2Widget.isWidgetVisible(varlamoreMapParentID, 2), 100, 10000);
            
            if (!isVarlamoreMapVisible) {
                Microbot.log("Varlamore Map Widget not visable within timeout");
                return false;
            }
            
            List<Widget> dynamicWidgetChildren = Arrays.stream(Rs2Widget.getWidget(varlamoreMapParentID, 15).getDynamicChildren())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            Widget actionWidget = dynamicWidgetChildren.stream()
                    .filter(w -> Arrays.stream(Objects.requireNonNull(w.getActions())).anyMatch(act -> act.toLowerCase().contains(displayInfo.toLowerCase())))
                    .findFirst()
                    .orElse(null);
            if (actionWidget != null) {
                Rs2Widget.clickWidget(actionWidget);
                Microbot.log("Traveling to " + transport.getDisplayInfo());
                return sleepUntilTrue(() -> Rs2Player.getWorldLocation().distanceTo2D(transport.getDestination()) < OFFSET, 100, 5000);
            }
        }
        return false;
    } 
    
    private static boolean handleMagicCarpet(Transport transport) {
        final int flyingPoseAnimation = 6936;
        var rugMerchant = Rs2Npc.getNpc(transport.getObjectId());
        if (rugMerchant == null) return false;

        Rs2Npc.interact(rugMerchant, transport.getAction());
        Rs2Dialogue.sleepUntilInDialogue();
        Rs2Dialogue.clickOption(transport.getDisplayInfo());
        sleepUntil(() -> Rs2Player.getPoseAnimation() == flyingPoseAnimation, 10000);
        return sleepUntilTrue(() -> Rs2Player.getPoseAnimation() != flyingPoseAnimation, 600,60000);
    }
    /**
     * interact with interfaces like spirit tree & xeric talisman etc...
     *
     * @param transport
     */
    private static boolean interactWithAdventureLog(Transport transport) {
        if (transport.getDisplayInfo() == null || transport.getDisplayInfo().isEmpty()) return false;

        // Wait for the widget to become visible
        boolean isAdventureLogVisible = sleepUntilTrue(() -> !Rs2Widget.isHidden(ComponentID.ADVENTURE_LOG_CONTAINER), Rs2Player::isMoving, 100, 10000);

        if (!isAdventureLogVisible) {
            Microbot.log("Widget did not become visible within the timeout.");
            return false;
        }

        char key = transport.getDisplayInfo().charAt(0);
        Rs2Keyboard.keyPress(key);
        Microbot.log("Traveling to " + transport.getDisplayInfo());
        return sleepUntilTrue(() -> Rs2Player.getWorldLocation().distanceTo2D(transport.getDestination()) < OFFSET, 100, 5000);
    }

    public static boolean handleGlider(Transport transport) {
        int TA_QUIR_PRIW = 9043972;
        int SINDARPOS = 9043975;
        int LEMANTO_ANDRA = 9043978;
        int KAR_HEWO = 9043981;
        int GANDIUS = 9043984;
        int OOKOOKOLLY_UNDRI = 9043993;
        int LEMANTOLLY_UNDRI = 9043989;

        // Get Transport Information
        String displayInfo = transport.getDisplayInfo();
        String npcName = transport.getName();
        String action = transport.getAction();

        final int GLIDER_PARENT_WIDGET = 138;
        final int GLIDER_CHILD_WIDGET = 0;

        // Check if the widget is already visible
        boolean isGliderMenuVisible = Rs2Widget.getWidget(GLIDER_PARENT_WIDGET, GLIDER_CHILD_WIDGET) != null;
        if (!isGliderMenuVisible) {
            // Find the glider NPC
            var gnome = Rs2Npc.getNpc(npcName);  // Use the NPC name to find the NPC
            if (gnome == null) {
                return false;
            }

            // Interact with the gnome glider NPC
            if (Rs2Npc.interact(gnome, action)) {
                sleepUntil(() -> !Rs2Widget.isHidden(GLIDER_PARENT_WIDGET, GLIDER_CHILD_WIDGET));
            }
        }


        // Wait for the widget to become visible
        boolean widgetVisible = sleepUntilTrue(() -> !Rs2Widget.isHidden(GLIDER_PARENT_WIDGET, GLIDER_CHILD_WIDGET), Rs2Player::isMoving, 100, 10000);
        
        if (!widgetVisible) {
            Microbot.log("Widget did not become visible within the timeout.");
            return false;
        }

        if (displayInfo.isEmpty()) return false;

        switch (displayInfo) {
            case "Kar-Hewo":
                return Rs2Widget.clickWidget(KAR_HEWO);
            case "Ta Quir Priw":
                return Rs2Widget.clickWidget(TA_QUIR_PRIW);
            case "Sindarpos":
                return Rs2Widget.clickWidget(SINDARPOS);
            case "Lemanto Andra":
                return Rs2Widget.clickWidget(LEMANTO_ANDRA);
            case "Gandius":
                return Rs2Widget.clickWidget(GANDIUS);
            case "Ookookolly Undri":
                return Rs2Widget.clickWidget(OOKOOKOLLY_UNDRI);
            case "Lemantolly Undri":
                return Rs2Widget.clickWidget(LEMANTOLLY_UNDRI);
            default:
                Microbot.log(displayInfo + " not found on the interface.");
                return false;
        }
    }

    // Constants for widget IDs
    private static final int SLOT_ONE = 26083331;
    private static final int SLOT_TWO = 26083332;
    private static final int SLOT_THREE = 26083333;
    private static final int TELEPORT_BUTTON = 26083354;

    private static final int SLOT_ONE_CW_ROTATION = 26083347;
    private static final int SLOT_ONE_ACW_ROTATION = 26083348;
    private static final int SLOT_TWO_CW_ROTATION = 26083349;
    private static final int SLOT_TWO_ACW_ROTATION = 26083350;
    private static final int SLOT_THREE_CW_ROTATION = 26083351;
    private static final int SLOT_THREE_ACW_ROTATION = 26083352;
    private static Rs2ItemModel startingWeapon = null;
    private static int startingWeaponId;
    private static int fairyRingGraphicId = 569;

    public static void handleFairyRing(Transport transport) {

        // Check if the widget is already visible
        if (!Rs2Widget.isHidden(ComponentID.FAIRY_RING_TELEPORT_BUTTON)) {
            rotateSlotToDesiredRotation(SLOT_ONE, Rs2Widget.getWidget(SLOT_ONE).getRotationY(), getDesiredRotation(transport.getDisplayInfo().charAt(0)), SLOT_ONE_ACW_ROTATION, SLOT_ONE_CW_ROTATION);
            rotateSlotToDesiredRotation(SLOT_TWO, Rs2Widget.getWidget(SLOT_TWO).getRotationY(), getDesiredRotation(transport.getDisplayInfo().charAt(1)), SLOT_TWO_ACW_ROTATION, SLOT_TWO_CW_ROTATION);
            rotateSlotToDesiredRotation(SLOT_THREE, Rs2Widget.getWidget(SLOT_THREE).getRotationY(), getDesiredRotation(transport.getDisplayInfo().charAt(2)), SLOT_THREE_ACW_ROTATION, SLOT_THREE_CW_ROTATION);
            Rs2Widget.clickWidget(TELEPORT_BUTTON);
            
            sleepUntil(() -> Rs2Player.hasSpotAnimation(fairyRingGraphicId));
            sleepUntil(() -> Rs2Player.getWorldLocation().equals(transport.getDestination()) && !Rs2Player.hasSpotAnimation(fairyRingGraphicId), 10000);
            
            // Re-equip the starting weapon if it was unequipped
            if (startingWeapon != null && !Rs2Equipment.isWearing(startingWeaponId)) {
                Microbot.log("Equipping Starting Weapon: " + startingWeaponId);
                Rs2Inventory.equip(startingWeaponId);
                sleepUntil(() -> Rs2Equipment.isWearing(startingWeaponId));
                startingWeapon = null;
                startingWeaponId = 0;
            }
            return;
        }

        if (Microbot.getVarbitValue(Varbits.DIARY_LUMBRIDGE_ELITE) == 1) {
            // Direct interaction without staff if elite Lumbridge Diary is complete
            Microbot.log("Interacting with the fairy ring directly.");
            var fairyRing = Rs2GameObject.findObjectByLocation(transport.getOrigin());
            Rs2GameObject.interact(fairyRing, "Configure");
            Rs2Player.waitForWalking();
        } 
        else {
            // Manage weapon and staff as needed if elite Lumbridge Diary is not complete
            if (startingWeapon == null && Rs2Equipment.hasEquippedSlot(EquipmentInventorySlot.WEAPON)) {
                startingWeapon = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
                startingWeaponId = startingWeapon.getId();
            }

            if (!Rs2Equipment.isWearing("Dramen staff") && !Rs2Equipment.isWearing("Lunar staff")) {
                // Equip Dramen or Lunar staff if not already equipped
                if (Rs2Inventory.contains("Dramen staff")) {
                    Rs2Inventory.equip("Dramen staff");
                    sleep(600);
                } else if (Rs2Inventory.contains("Lunar staff")) {
                    Rs2Inventory.equip("Lunar staff");
                    sleep(600);
                }
            }

            // Interact with fairy ring after equipping the staff
            Microbot.log("Interacting with the fairy ring using a staff. " + transport.getOrigin().getX() + " " + transport.getOrigin().getY());
            var fairyRing = Rs2GameObject.findObjectByLocation(transport.getOrigin());
            if (Rs2GameObject.interact(fairyRing, "Configure")) {
                Rs2Player.waitForWalking();
            } else {
                recalculatePath();
            }
        }
    }

    private static void rotateSlotToDesiredRotation(int slotId, int currentRotation, int desiredRotation, int slotAcwRotationId, int slotCwRotationId) {
        int anticlockwiseTurns = (desiredRotation - currentRotation + 2048) % 2048;
        int clockwiseTurns = (currentRotation - desiredRotation + 2048) % 2048;

        if (clockwiseTurns <= anticlockwiseTurns) {
            System.out.println("Rotating slot " + slotId + " clockwise " + (clockwiseTurns / 512) + " times.");
            for (int i = 0; i < clockwiseTurns / 512; i++) {
                Rs2Widget.clickWidget(slotCwRotationId);
                sleep(600, 1200);
            }
        } else {
            System.out.println("Rotating slot " + slotId + " anticlockwise " + (anticlockwiseTurns / 512) + " times.");
            for (int i = 0; i < anticlockwiseTurns / 512; i++) {
                Rs2Widget.clickWidget(slotAcwRotationId);
                sleep(600, 1200);
            }
        }

    }

    private static int getDesiredRotation(char letter) {
        switch (letter) {
            case 'A':
            case 'I':
            case 'P':
                return 0;
            case 'B':
            case 'J':
            case 'Q':
                return 512;
            case 'C':
            case 'K':
            case 'R':
                return 1024;
            case 'D':
            case 'L':
            case 'S':
                return 1536;
            default:
                return -1;
        }
    }
}
