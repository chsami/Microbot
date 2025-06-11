package net.runelite.client.plugins.microbot.util.walker;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Point;
import net.runelite.api.*;
import net.runelite.api.annotations.Component;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.devtools.MovementFlag;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathConfig;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.coords.Rs2LocalPoint;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldArea;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldPoint;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2Pvp;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;

import javax.inject.Named;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static net.runelite.client.plugins.microbot.util.Global.*;

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
    
    @Named("disableWalkerUpdate")
    static boolean disableWalkerUpdate;

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
                sleepUntil(() -> {
                    Widget checkBoxWidget = Rs2Widget.getWidget(565, 20);
                    if (checkBoxWidget == null) return false;
                    return checkBoxWidget.getSpriteId() != 941;
                });
                Rs2Widget.clickWidget(565, 17);
            }
            
            // entering down ladder strong hold of security
            if (Rs2Widget.clickWidget(579, 20)) {
                sleepUntil(() -> {
                    Widget checkBoxWidget = Rs2Widget.getWidget(579, 20);
                    if (checkBoxWidget == null) return false;
                    return checkBoxWidget.getSpriteId() != 941;
                });
                Rs2Widget.clickWidget(579, 17);
            }


            if (Rs2Widget.enterWilderness()) {
                sleepUntil(Rs2Player::isAnimating);
            }

            boolean doorOrTransportResult = false;
            for (int i = indexOfStartPoint; i < path.size(); i++) {
                WorldPoint currentWorldPoint = path.get(i);

                System.out.println("start loop " + i);

				// add breakpoint here

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
                
                doorOrTransportResult = handleRockfall(path, i);
                if (doorOrTransportResult) {
                    System.out.println("break out of rockfall");
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
            Microbot.logStackTrace("Rs2Walker", ex);
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
                .filter(Rs2Tile::isWalkable).min(Comparator.comparingInt(Rs2Player.getWorldLocation()::distanceTo))
                .orElse(null);
        // Priority to a walkable tile, otherwise walk to the first tile next to locatable
        if (walkableInteractPoint != null) {
            if(walkableInteractPoint.equals(Rs2Player.getWorldLocation()))
                return;
            walkFastLocal(LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), walkableInteractPoint));
        } else {
            walkFastLocal(LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), Objects.requireNonNull(interactablePoints.stream().min(Comparator.comparingInt(Rs2Player.getWorldLocation()::distanceTo))
                    .orElse(null))));
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
        if (!disableWalkerUpdate && !Rs2MiniMap.isPointInsideMinimap(point)) return false;

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
     * @param start source
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
        return canReach(worldPoint, sizeX, sizeY, 3, 3);
    }

    /**
     * used for quest script interacting with object
     * also used for finding the nearest bank
     * @param worldPoint
     * @return
     */
    public static boolean canReach(WorldPoint worldPoint) {
        return canReach(worldPoint, 2, 2, 2, 2);
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
    ShortestPathPlugin.getPathfinderConfig().refresh();
    Pathfinder pathfinder = new Pathfinder(ShortestPathPlugin.getPathfinderConfig(), Rs2Player.getWorldLocation(), target);
    pathfinder.run();
    return pathfinder.getPath();
}

    /**
     * Retrieves all TELEPORTATION_ITEM type transports found along the given path.
     *
     * @param path A list of WorldPoint objects representing the path.
     * @return A list of Transport objects that are item transports.
     */
    public static List<Transport> getTransportsForPath(List<WorldPoint> path, int indexOfStartPoint) {
        List<Transport> transportList = new ArrayList<>();
        int currentIndex = indexOfStartPoint;

        // Loop through the path until the end
        while (currentIndex < path.size()) {
            WorldPoint currentPoint = path.get(currentIndex);
            // Get any transports that start at this point (or keyed by this point)
            Set<Transport> transportsAtPoint = ShortestPathPlugin.getTransports()
                    .getOrDefault(currentPoint, new HashSet<>());
            boolean foundTransport = false;

            // Iterate over each available transport
            for (Transport transport : transportsAtPoint) {

                // Special handling for teleportation transports
                if (transport.getType() == TransportType.TELEPORTATION_ITEM ||
                        transport.getType() == TransportType.TELEPORTATION_SPELL)
                {
                    // For teleportation, we assume origin is null and simply check if the destination exists in the path.
                    if (path.contains(transport.getDestination())) {
                        transportList.add(transport);
                        int destIndex = path.indexOf(transport.getDestination());
                        // Advance the current index to the destination tile (or at least one forward)
                        currentIndex = destIndex > currentIndex ? destIndex : currentIndex + 1;
                        foundTransport = true;
                        break;
                    }
                }

                // For non-teleportation transports (or if teleportation had a valid origin, though typically null):
                Collection<WorldPoint> originPoints;
                if (transport.getOrigin() == null) {
                    originPoints = Collections.singleton(null);
                } else {
                    originPoints = WorldPoint.toLocalInstance(
                            Microbot.getClient().getTopLevelWorldView(), transport.getOrigin());
                }

                for (WorldPoint origin : originPoints) {
                    // If an origin is defined but the player's plane doesn't match, skip it.
                    if (transport.getOrigin() != null &&
                            Rs2Player.getWorldLocation().getPlane() != transport.getOrigin().getPlane()) {
                        continue;
                    }

                    // For non-teleportation transports, ensure both origin and destination exist in the path
                    // and that the destination comes after the origin.
                    if (transport.getType() != TransportType.TELEPORTATION_ITEM &&
                            transport.getType() != TransportType.TELEPORTATION_SPELL) {
                        int indexOfOrigin = path.indexOf(transport.getOrigin());
                        int indexOfDestination = path.indexOf(transport.getDestination());
                        if (indexOfOrigin == -1 || indexOfDestination == -1 || indexOfDestination < indexOfOrigin) {
                            continue;
                        }
                    }

                    // If the current path point equals the transport's origin then add it.
                    if (currentPoint.equals(origin)) {
                        transportList.add(transport);
                        int destIndex = path.indexOf(transport.getDestination());
                        currentIndex = destIndex > currentIndex ? destIndex : currentIndex + 1;
                        foundTransport = true;
                        break;
                    }
                }
                if (foundTransport) {
                    break;
                }
            }

            if (!foundTransport) {
                currentIndex++;
            }
        }
        return transportList;
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
    
    private static boolean handleRockfall(List<WorldPoint> path, int index) {
        if (ShortestPathPlugin.getPathfinder() == null) return false;

        if (index == path.size() - 1) return false;
        
        // If we are in instance, ignore checking RegionID
        if(Microbot.getClient().getTopLevelWorldView().isInstance()) return false;
        
        // If we are not inside of the Motherloade mine, ignore the following logic
        if (Rs2Player.getWorldLocation().getRegionID() != 14936 || currentTarget.getRegionID() != 14936) return false;
        
        // We kill the path if no pickaxe is found to avoid walking around like an idiot
        if (!Rs2Inventory.hasItem("pickaxe")) {
            if (!Rs2Equipment.isWearing("pickaxe")) {
                Microbot.log("Unable to find pickaxe to mine rockfall");
                setTarget(null);
                return false;
            }
        }
        
        // Check current index & next index for rockfall
        for (int rockIndex = index; rockIndex < index + 2; rockIndex++) {
            var point = path.get(rockIndex);

            TileObject object = null;
            var tile = Rs2GameObject.getTiles(3).stream()
                    .filter(x -> x.getWorldLocation().equals(point))
                    .findFirst().orElse(null);

            if (tile != null)
                object = Rs2GameObject.getGameObject(point);

            if (object == null) continue;

            if (object.getId() == ObjectID.ROCKFALL || object.getId() == ObjectID.ROCKFALL_26680) {
                Rs2GameObject.interact(object, "mine");
                return sleepUntil(() -> Rs2GameObject.getGameObject(point) == null);
            }
        }
        
        return false;
    }

    private static boolean handleDoors(List<WorldPoint> path, int index) {
        if (ShortestPathPlugin.getPathfinder() == null || index >= path.size() - 1) return false;

        List<String> doorActions = List.of("pay-toll", "pick-lock", "walk-through", "go-through", "open");
        boolean isInstance = Microbot.getClient()
                .getTopLevelWorldView()
                .getScene()
                .isInstance();

        WorldPoint rawFrom = path.get(index);
        WorldPoint rawTo = path.get(index + 1);
        WorldPoint fromWp = isInstance
                ? Rs2WorldPoint.convertInstancedWorldPoint(rawFrom)
                : rawFrom;
        WorldPoint toWp = isInstance
                ? Rs2WorldPoint.convertInstancedWorldPoint(rawTo)
                : rawTo;

        boolean diagonal = Math.abs(fromWp.getX() - toWp.getX()) > 0
                && Math.abs(fromWp.getY() - toWp.getY()) > 0;

        for (int offset = 0; offset <= 1; offset++) {
            int doorIdx = index + offset;
            if (doorIdx >= path.size()) continue;

            WorldPoint rawDoorWp = path.get(doorIdx);
            WorldPoint doorWp = isInstance
                    ? Rs2WorldPoint.convertInstancedWorldPoint(rawDoorWp)
                    : rawDoorWp;

            List<WorldPoint> probes = new ArrayList<>();
            probes.add(doorWp);
            if (diagonal) {
                probes.add(new WorldPoint(toWp.getX(), fromWp.getY(), doorWp.getPlane()));
                probes.add(new WorldPoint(fromWp.getX(), toWp.getY(), doorWp.getPlane()));
            }

            for (WorldPoint probe : probes) {
				boolean adjacentToPath = probe.distanceTo(fromWp) <= 1 || probe.distanceTo(toWp) <= 1;
				if (!adjacentToPath || !Objects.equals(probe.getPlane(), Microbot.getClient().getLocalPlayer().getWorldLocation().getPlane())) continue;

                WallObject wall = Rs2GameObject.getWallObject(o -> o.getWorldLocation().equals(probe), probe, 3);

                TileObject object = (wall != null)
                        ? wall
                        : Rs2GameObject.getGameObject(o -> o.getWorldLocation().equals(probe), probe, 3);
                if (object == null) continue;

                ObjectComposition comp = Rs2GameObject.convertToObjectComposition(object);
				// We include the name "null" here to ignore imposter objects
                if (comp == null || comp.getName().equals("null")) continue;

                String action = Arrays.stream(comp.getActions())
					.filter(Objects::nonNull)
					.filter(act -> doorActions.stream().anyMatch(dact -> act.toLowerCase().startsWith(dact.toLowerCase())))
					.min(Comparator.comparing(act -> doorActions.indexOf(doorActions.stream().filter(dact -> act.toLowerCase().startsWith(dact)).findFirst().orElse(""))))
					.orElse(null);

                if (action == null) continue;

                boolean found = false;

                if (object instanceof WallObject) {
                    int orientation = ((WallObject) object).getOrientationA();

                    if (searchNeighborPoint(orientation, probe, fromWp) || searchNeighborPoint(orientation, probe, toWp)) {
                        found = true;
                    }
                } else {
                    String name = comp.getName();
                    if (name != null && name.toLowerCase().contains("door")) {
                        found = true;
                    }
                }

                if (found) {
                    if (!handleDoorException(object, action)) {
                        Rs2GameObject.interact(object, action);
                        Rs2Player.waitForWalking();
                    }
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean handleDoorException(TileObject object, String action) {
        if (isInStrongholdOfSecurity()) {
            return handleStrongholdOfSecurityAnswer(object, action);
        }
        return false;
    }

    private static boolean isInStrongholdOfSecurity() {
        List<Integer> mapRegionIds = List.of(7505, 7504, 7760, 7503, 7759, 7758, 7757, 8013, 7756, 8012, 8017, 8530, 9297);
        return mapRegionIds.contains(Rs2Player.getWorldLocation().getRegionID());
    }

    private static boolean handleStrongholdOfSecurityAnswer(TileObject object, String action) {
        Rs2GameObject.interact(object, action);
        boolean isInDialogue = Rs2Dialogue.sleepUntilInDialogue();

        // Not all the doors ask questions, so only if dialogue is shown we will attempt to get the answer
        if (!isInDialogue) return true;

        // Skip over first door dialogue & don't forget to set up two-factor warning
        if (Rs2Dialogue.getDialogueText() != null) {
            if (Rs2Dialogue.getDialogueText().contains("two-factor authentication options") || Rs2Dialogue.getDialogueText().contains("Hopefully you will learn<br>much from us.")) {
                Rs2Dialogue.sleepUntilHasContinue();
                sleepUntil(() -> !Rs2Dialogue.hasContinue() || Rs2Dialogue.getDialogueText().contains("To pass you must answer me"), Rs2Dialogue::clickContinue, 5000, Rs2Random.between(600, 800));
                if (!Rs2Dialogue.isInDialogue()) return true;
            }
        }

        String dialogueAnswer = null;
        int attempts = 0;
        final int maxAttempts = 5;

        // We attempt to find the answer multiple times in-case there is dialogue that appears before the question
        while (dialogueAnswer == null && attempts < maxAttempts) {
            if (currentTarget == null) break;
            dialogueAnswer = StrongholdAnswer.findAnswer(Rs2Dialogue.getDialogueText());
            if (dialogueAnswer == null) {
                Rs2Dialogue.clickContinue();
                Rs2Random.waitEx(800, 100);
            }
            attempts++;
        }

        if (dialogueAnswer != null) {
            Rs2Dialogue.clickContinue();
            Rs2Dialogue.sleepUntilSelectAnOption();
            Rs2Dialogue.clickOption(dialogueAnswer);
            Rs2Dialogue.sleepUntilHasContinue();
            sleepUntil(() -> !Rs2Dialogue.hasContinue(), Rs2Dialogue::clickContinue, 5000, Rs2Random.between(600, 800));
            Rs2Player.waitForAnimation(1200);
            return true;
        }

        return false;
    }

    /**
     * Determines whether a given neighbor tile lies immediately adjacent to
     * a reference tile, in the direction specified by a wall orientation code.
     *
     * @param orientation the wall orientation code:
     *                    <ul>
     *                      <li>1 = west</li>
     *                      <li>2 = north</li>
     *                      <li>4 = east</li>
     *                      <li>8 = south</li>
     *                      <li>16 = northwest</li>
     *                      <li>32 = northeast</li>
     *                      <li>64 = southeast</li>
     *                      <li>128 = southwest</li>
     *                    </ul>
     * @param point       the reference {@link WorldPoint} representing the tile at the wall’s base
     * @param neighbor    the {@link WorldPoint} to test for adjacency
     * @return {@code true} if {@code neighbor} is exactly one tile away from {@code point}
     *         in the direction indicated by {@code orientation}, {@code false} otherwise
     */
    private static boolean searchNeighborPoint(int orientation, WorldPoint point, WorldPoint neighbor) {
        int dx = neighbor.getX() - point.getX();
        int dy = neighbor.getY() - point.getY();

        switch (orientation) {
            case 1:   // west
                return dx == -1 && dy == 0;
            case 2:   // north
                return dx == 0  && dy == 1;
            case 4:   // east
                return dx == 1  && dy == 0;
            case 8:   // south
                return dx == 0  && dy == -1;
            case 16:  // northwest
                return dx == -1 && dy == 1;
            case 32:  // northeast
                return dx == 1  && dy == 1;
            case 64:  // southeast
                return dx == 1  && dy == -1;
            case 128: // southwest
                return dx == -1 && dy == -1;
            default:
                return false;
        }
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
        return restartPathfinding(start, Set.of(end));
    }

    public static boolean restartPathfinding(WorldPoint start, Set<WorldPoint> ends) {
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
            Pathfinder pathfinder = new Pathfinder(ShortestPathPlugin.getPathfinderConfig(), start, ends);
            pathfinder.run();
            ShortestPathPlugin.getPathfinderConfig().setIgnoreTeleportAndItems(true);
            Pathfinder pathfinderWithoutTeleports = new Pathfinder(ShortestPathPlugin.getPathfinderConfig(), start, ends);
            pathfinderWithoutTeleports.run();
            if (pathfinder.getPath().size() >= pathfinderWithoutTeleports.getPath().size()) {
                ShortestPathPlugin.setPathfinder(pathfinderWithoutTeleports);
            } else {
                ShortestPathPlugin.setPathfinder(pathfinder);
            }
            ShortestPathPlugin.getPathfinderConfig().setIgnoreTeleportAndItems(false);
        } else {
            ShortestPathPlugin.setPathfinder(new Pathfinder(ShortestPathPlugin.getPathfinderConfig(), start, ends));
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
                    if (TransportType.isTeleport(transport.getType()) && Rs2Player.getWorldLocation().distanceTo(transport.getDestination()) < 3) continue;

                    // we don't need to check for teleportation_item & teleportation_spell as they will be set on the first tile
                    if (!TransportType.isTeleport(transport.getType())) {
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
                        if (transport.getType() == TransportType.SHIP || transport.getType() == TransportType.NPC || transport.getType() == TransportType.BOAT) {

                            Rs2NpcModel npc = Rs2Npc.getNpc(transport.getName());

                            if (Rs2Npc.canWalkTo(npc, 20) && Rs2Npc.interact(npc, transport.getAction())) {
                                Rs2Player.waitForWalking();
                                if (Rs2Dialogue.clickOption("I'm just going to Pirates' cove")) {
                                    sleep(600 * 2);
                                    Rs2Dialogue.clickContinue();
                                }
                                sleepUntil(() -> !Rs2Player.isAnimating());
                                sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(transport.getDestination()) < 10);
                                sleep(600 * 6);
                            } else {
                                Rs2Walker.walkFastCanvas(path.get(i));
                                sleep(1200, 1600);
                            }
                        }

                        if (transport.getType() == TransportType.CHARTER_SHIP) {
                            if (handleCharterShip(transport)) {
                                sleepUntil(() -> !Rs2Player.isAnimating());
                                sleepUntilTrue(() -> Rs2Player.getWorldLocation().distanceTo(transport.getDestination()) < 10);
                                sleep(600 * 4); // wait 4 extra ticks before walking
                                break;
                            }
                        }
                    }

                    if (handleTrapdoor(transport)) {
                        sleepUntil(() -> !Rs2Player.isAnimating());
                        sleepUntilTrue(() -> Rs2Player.getWorldLocation().distanceTo(transport.getDestination()) < 10);
                        break;
                    }
                    
                    if (transport.getType() == TransportType.CANOE) {
                        if (handleCanoe(transport)) {
                            sleep(600 * 2); // wait 2 extra ticks before walking
                            break;
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
                        if (handleFairyRing(transport)) {
							sleepUntilTrue(() -> Rs2Player.getWorldLocation().distanceTo(transport.getDestination()) < OFFSET);
							break;
						}
                    }
                    
                    if (transport.getType() == TransportType.TELEPORTATION_MINIGAME) {
                        if (handleMinigameTeleport(transport)) {
                            sleepUntilTrue(() -> Rs2Player.getWorldLocation().distanceTo(transport.getDestination()) < (OFFSET * 2));
                            break;
                        }
                    }

                    if (transport.getType() == TransportType.TELEPORTATION_ITEM) {
                        if (handleTeleportItem(transport)) {
                            sleepUntil(() -> !Rs2Player.isAnimating());
                            sleepUntilTrue(() -> Rs2Player.getWorldLocation().distanceTo(transport.getDestination()) < OFFSET);
                            break;
                        }
                    }

                    if (transport.getType() == TransportType.TELEPORTATION_SPELL) {
                        if (handleTeleportSpell(transport)) {
                            sleepUntil(() -> !Rs2Player.isAnimating());
                            sleepUntilTrue(() -> Rs2Player.getWorldLocation().distanceTo(transport.getDestination()) < OFFSET);
                            Rs2Tab.switchToInventoryTab();
                            break;
                        }
                    }
                    

					List<TileObject> objects = Rs2GameObject.getAll(o -> o.getId() == transport.getObjectId(), transport.getOrigin(), 10).stream()
						.sorted(Comparator.comparingInt(o -> o.getWorldLocation().distanceTo(transport.getOrigin())))
						.collect(Collectors.toList());

					TileObject object = objects.stream().findFirst().orElse(null);
					if (object instanceof GroundObject) {
						object = objects.stream()
							.filter(o -> !Objects.equals(o.getWorldLocation(), Microbot.getClient().getLocalPlayer().getWorldLocation()))
							.min(Comparator.comparing(o -> ((TileObject) o).getWorldLocation().distanceTo(transport.getOrigin()))
								.thenComparing(o -> ((TileObject) o).getWorldLocation().distanceTo(transport.getDestination()))).orElse(null);
					}
					if (object != null && object.getId() == transport.getObjectId()) {
						System.out.println("Object Type: " + Rs2GameObject.getObjectType(object));

						if (!(object instanceof GroundObject)) {
							if (!Rs2Tile.isTileReachable(transport.getOrigin())) {
								break;
							}
						}

						handleObject(transport, object);
						sleepUntil(() -> !Rs2Player.isAnimating());
						return sleepUntilTrue(() -> Rs2Player.getWorldLocation().distanceTo(transport.getDestination()) < OFFSET);
					}
                }
            }
        }
        return false;
    }

    private static void handleObject(Transport transport, TileObject tileObject) {
        Rs2GameObject.interact(tileObject, transport.getAction());
        if (handleObjectExceptions(transport, tileObject)) return;
        if (transport.getDestination().getPlane() == Rs2Player.getWorldLocation().getPlane()) {
            if (transport.getType() == TransportType.AGILITY_SHORTCUT) {
                Rs2Player.waitForAnimation();
                sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(transport.getDestination()) <= 2, 10000);
            } else if (transport.getType() == TransportType.MINECART) {
                if (interactWithAdventureLog(transport)) {
                    sleep(600 * 2); // wait extra 2 game ticks before moving
                } else {
                    sleepUntil(() -> Rs2Player.getPoseAnimation() == 2148, 5000);
                    sleepUntil(() -> Rs2Player.getPoseAnimation() != 2148, 10000);
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
            sleepUntil(() -> !Rs2Player.isMoving() && !Rs2Player.isAnimating(1200));
			final WorldPoint webLocation = tileObject.getWorldLocation();
			final WorldPoint currentPlayerPoint = Microbot.getClient().getLocalPlayer().getWorldLocation();
			boolean doesWebStillExist = Rs2GameObject.getAll(o -> Objects.equals(webLocation, o.getWorldLocation()) && o.getId() == ObjectID.WEB).stream().findFirst().isPresent();
			if (doesWebStillExist) {
				sleepUntil(() -> Rs2GameObject.getAll(o -> Objects.equals(webLocation, o.getWorldLocation()) && o.getId() == ObjectID.WEB).stream().findFirst().isEmpty(),
				() -> {
					Rs2GameObject.interact(tileObject, "slash");
					Rs2Player.waitForAnimation();
				}, 8000, 1200);
			}
			Rs2Walker.walkFastCanvas(transport.getDestination());
            return sleepUntil(() -> !Objects.equals(currentPlayerPoint, Microbot.getClient().getLocalPlayer().getWorldLocation()));
        }
        
        // Handle Brimhaven Dungeon Entrance
        if (tileObject.getId() == 20877) {
            if (Rs2Player.isMoving()) {
                Rs2Player.waitForWalking();
            }
            Rs2Dialogue.sleepUntilHasQuestion("Pay 875 coins to enter?");
            Rs2Dialogue.clickOption("Yes");
            sleepUntil(() -> Rs2Player.getWorldLocation().equals(transport.getDestination()));
            return true;
        }
        // Handle Brimhaven Dungeon Stepping Stones
        if (tileObject.getId() == ObjectID.STEPPING_STONE_21738 || tileObject.getId() == ObjectID.STEPPING_STONE_21739) {
            Rs2Player.waitForAnimation(600 * 7);
            return true;
        }
        
        // Handle Morte Myre Cave Agility Shortcut
        if (tileObject.getId() == ObjectID.CAVE_ENTRANCE_16308) {
            Rs2Player.waitForAnimation((600 * 4 ) + 300);
            return true;
        }
        
        // Handle Crash Site Cavern Gate
        if (tileObject.getId() == 28807 && transport.getOrigin().equals(new WorldPoint(2435,3519, 0))) {
            if (Rs2Player.isMoving()) {
                Rs2Player.waitForWalking();
            }
            Rs2Dialogue.sleepUntilInDialogue();
            Rs2Dialogue.clickOption("yes");
            return true;
        }
        
        // Handle Cave Entrance inside of Asgarnia Ice Caves
        if (tileObject.getId() == ObjectID.TUNNEL_55988 || tileObject.getId() == ObjectID.TUNNEL_55989) {
            Rs2Player.waitForAnimation();
        }

        // Handle Rev Cave Dialogue
        if (tileObject.getId() == ObjectID.CAVERN_31555) {
            if (Rs2Player.isMoving()) {
                Rs2Player.waitForWalking();
            }
            Widget dialogueSprite = Rs2Dialogue.getDialogueSprite();
            if (dialogueSprite != null && dialogueSprite.getItemId() == 1004) {
                Rs2Dialogue.clickContinue();
                Rs2Dialogue.sleepUntilSelectAnOption();
                Rs2Dialogue.clickOption("Yes, don't ask again");
                Rs2Dialogue.sleepUntilNotInDialogue();
            }
            return true;
        }

        if (tileObject.getId() == ObjectID.ROCK_SLIDE) {
            Rs2Player.waitForAnimation(600 * 4);
            return true;
        }

		if (Rs2GameObject.getObjectIdsByName("Fossil_Rowboat").contains(tileObject.getId())) {
			if (transport.getDisplayInfo() == null || transport.getDisplayInfo().isEmpty()) return false;

			char option = transport.getDisplayInfo().charAt(0);
			Rs2Dialogue.sleepUntilSelectAnOption();
			Rs2Keyboard.keyPress(option);
			sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo2D(transport.getDestination()) < OFFSET, 10000);
		}
        return false;
    }
    
    private static boolean handleWildernessObelisk(Transport transport) {
        GameObject obelisk = Rs2GameObject.getGameObject(obj -> obj.getId() == transport.getObjectId(), transport.getOrigin());
        
        if (obelisk != null) {
            Rs2GameObject.interact(obelisk, transport.getAction());
            sleepUntil(() -> Rs2GameObject.getGameObject(obj -> obj.getId() == transport.getObjectId(), transport.getOrigin()) != null);
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
        List<String> genericKeyWords = Arrays.asList("invoke", "empty", "consume", "teleport", "rub", "break", "reminisce", "signal", "play","commune");

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

        // Check the first character of the item name, if it is a number return true
        boolean hasMenuOption = !transport.getDisplayInfo().isEmpty() && Character.isDigit(transport.getDisplayInfo().charAt(0));

        if (!hasMenuOption) {
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

                if (itemAction.equalsIgnoreCase("teleport") && transport.getDisplayInfo().toLowerCase().contains("revenant cave teleport")) {
                    Rs2Dialogue.sleepUntilHasDialogueOption("Yes, teleport me now");
                    Rs2Dialogue.clickOption("Yes, teleport me now");
                }

				if (itemAction.equalsIgnoreCase("break") && itemId == ItemID.ICE_PLATEAU_TELEPORT) {
					Rs2Dialogue.sleepUntilHasQuestion("Teleport into the DEEP wilderness?");
					Rs2Dialogue.clickOption("Yes");
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
                return sleepUntilTrue(() -> Rs2Player.getWorldLocation().distanceTo2D(transport.getDestination()) < OFFSET, 100, 8000);
            }
        }
        else {
            return interactWithNewRuneliteMenu(transport,itemId);

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
                return sleepUntilTrue(() -> Rs2Player.getWorldLocation().distanceTo2D(transport.getDestination()) < OFFSET, 100, 8000);
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
            Microbot.getClientThread().runOnSeperateThread(() -> restartPathfinding(start, ShortestPathPlugin.getPathfinder().getTargets()));
        } else {
            restartPathfinding(start, ShortestPathPlugin.getPathfinder().getTargets());
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

        if (!Rs2Widget.isWidgetVisible(ComponentID.ADVENTURE_LOG_CONTAINER)) {
            TileObject spiritTree = Rs2GameObject.findObjectById(objectId);
            if (!Rs2GameObject.interact(spiritTree, "Travel")) {
                return false;
            }
        }

        return interactWithAdventureLog(transport);
    }

    private static boolean handleMinigameTeleport(Transport transport) {
        final Object[] selectedOpListener = new Object[]{489, 0, 0};
        final List<Integer> teleportGraphics = List.of(800, 802, 803, 804);

        @Component final int GROUPING_BUTTON_COMPONENT_ID = 46333957; // 707.5

        @Component final int DROPDOWN_BUTTON_COMPONENT_ID = 4980760; // 76.24
        final int DROPDOWN_SELECTED_SPRITE_ID = 773;

        @Component final int MINIGAME_LIST = 4980758; // 76.22
        @Component final int SELECTED_MINIGAME = 4980747; // 76.11
        @Component final int TELEPORT_BUTTON = 4980768; // 76.32

        // Minigame teleports cant be used if a dialogue is open.
        if (Rs2Dialogue.isInDialogue()) {
            var playerLocation = Rs2Player.getLocalLocation();
            walkFastLocal(playerLocation);
        }

        if (Rs2Tab.getCurrentTab() != InterfaceTab.CHAT) {
            Rs2Tab.switchToGroupingTab();
            sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.CHAT);
        }

        Widget groupingBtn = Rs2Widget.getWidget(GROUPING_BUTTON_COMPONENT_ID);
        if (groupingBtn == null) return false;

        if (!Arrays.equals(groupingBtn.getOnOpListener(), selectedOpListener)) {
            Rs2Widget.clickWidget(groupingBtn);
            sleepUntil(() -> Arrays.equals(groupingBtn.getOnOpListener(), selectedOpListener));
        }

        boolean hasMultipleDestination = transport.getDisplayInfo().contains(":");
        String destination = hasMultipleDestination
                ? transport.getDisplayInfo().split(":")[0].trim().toLowerCase()
                : transport.getDisplayInfo().trim().toLowerCase();

        Widget selectedWidget = Rs2Widget.getWidget(SELECTED_MINIGAME);
        if (selectedWidget == null) return false;
        if (!selectedWidget.getText().equalsIgnoreCase(destination)) {
            Widget dropdownBtn = Rs2Widget.getWidget(DROPDOWN_BUTTON_COMPONENT_ID);
            if (dropdownBtn == null) return false;

            if (dropdownBtn.getSpriteId() != DROPDOWN_SELECTED_SPRITE_ID) {
                Rs2Widget.clickWidget(dropdownBtn);
                sleepUntil(() -> Rs2Widget.findWidget(DROPDOWN_SELECTED_SPRITE_ID, List.of(Rs2Widget.getWidget(DROPDOWN_BUTTON_COMPONENT_ID))) != null);
            }

            Widget minigameWidgetParent = Rs2Widget.getWidget(MINIGAME_LIST);
            if (minigameWidgetParent == null) return false;
            List<Widget> minigameWidgetList = Arrays.stream(minigameWidgetParent.getDynamicChildren())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            Widget destinationWidget = Rs2Widget.findWidget(destination, minigameWidgetList);
            if (destinationWidget == null) return false;

            NewMenuEntry destinationMenuEntry = new NewMenuEntry("Select", "", 1, MenuAction.CC_OP, destinationWidget.getIndex(), minigameWidgetParent.getId(), false);
            Microbot.doInvoke(destinationMenuEntry, new Rectangle(1, 1));
            sleepUntil(() -> Rs2Widget.getWidget(SELECTED_MINIGAME).getText().equalsIgnoreCase(destination));
        }

        Widget teleportBtn = Rs2Widget.getWidget(TELEPORT_BUTTON);
        if (teleportBtn == null) return false;
        Rs2Widget.clickWidget(teleportBtn);

        if (transport.getDisplayInfo().toLowerCase().contains("rat pits")) {
            Rs2Dialogue.sleepUntilSelectAnOption();
            Rs2Dialogue.clickOption(transport.getDisplayInfo().split(":")[1].trim().toLowerCase());
        }

        sleepUntil(Rs2Player::isAnimating);
        return sleepUntilTrue(() -> !Rs2Player.isAnimating() && teleportGraphics.stream().noneMatch(Rs2Player::hasSpotAnimation), 100, 20000);
    }

    private static boolean handleCanoe(Transport transport) {
        String displayInfo = transport.getDisplayInfo();
        if (displayInfo == null || displayInfo.isEmpty()) return false;

        List<String> validActions = List.of("chop-down", "shape-canoe", "float canoe", "paddle canoe");
        ObjectComposition CANOE_COMPOSITION = Rs2GameObject.convertToObjectComposition(transport.getObjectId());
        if (CANOE_COMPOSITION == null) return false;

        String currentAction = Arrays.stream(CANOE_COMPOSITION.getActions())
                .filter(Objects::nonNull)
                .filter(act -> validActions.contains(act.toLowerCase())).findFirst().orElse(null);
        if (currentAction == null || currentAction.isEmpty()) {
            Microbot.log("Unable to find canoe action");
            return false;
        }

        switch (currentAction) {
            case "Chop-down":
                Rs2GameObject.interact(transport.getObjectId(), "Chop-down");
                sleepUntil(() -> Rs2Player.isAnimating(1200));
                return sleepUntilTrue(() -> {
                    ObjectComposition composition = Rs2GameObject.convertToObjectComposition(transport.getObjectId());

                    if (composition == null) return false;
                    return Arrays.stream(composition.getActions()).filter(Objects::nonNull).noneMatch(currentAction::equals) && !Rs2Player.isAnimating();
                }, 300, 10000);
            case "Shape-Canoe":
                @Component final int CANOE_SELECTION_PARENT = 27262976; // 416.3
                @Component final int CANOE_SHAPING_TEXT = 27262986; // 416.10

                Rs2GameObject.interact(transport.getObjectId(), "Shape-Canoe");
                boolean isCanoeShapeTextVisible = sleepUntilTrue(() -> Rs2Widget.isWidgetVisible(CANOE_SHAPING_TEXT), 100, 10000);
                if (!isCanoeShapeTextVisible) {
                    Microbot.log("Canoe shape text is not visible within timeout period");
                    return false;
                }

                final int woodcuttingLevel = Rs2Player.getRealSkillLevel(Skill.WOODCUTTING);
                String canoeOption;
                if (woodcuttingLevel >= 57) {
                    canoeOption = "Waka canoe";
                } else if (woodcuttingLevel >= 42) {
                    canoeOption = "Stable dugout canoe";
                } else if (woodcuttingLevel >= 27) {
                    canoeOption = "Dugout canoe";
                } else if (woodcuttingLevel >= 12) {
                    canoeOption = "Log canoe";
                } else {
                    // Not high enough level to make any canoe
                    return false;
                }

                Widget canoeSelectionParentWidget = Rs2Widget.getWidget(CANOE_SELECTION_PARENT);
                if (canoeSelectionParentWidget == null) return false;
                Widget canoeSelectionWidget = Rs2Widget.findWidget("Make " + canoeOption, List.of(canoeSelectionParentWidget));
                Rs2Widget.clickWidget(canoeSelectionWidget);
                sleepUntil(() -> Rs2Player.isAnimating(1200));
                return sleepUntilTrue(() -> {
                    ObjectComposition composition = Rs2GameObject.convertToObjectComposition(transport.getObjectId());

                    if (composition == null) return false;
                    return Arrays.stream(composition.getActions()).filter(Objects::nonNull).noneMatch(currentAction::equals) && !Rs2Player.isAnimating();
                }, 300, 10000);
            case "Float Canoe":
                Rs2GameObject.interact(transport.getObjectId(), "Float Canoe");
                sleepUntil(() -> Rs2Player.isAnimating(1200));
                return sleepUntilTrue(() -> {
                    ObjectComposition composition = Rs2GameObject.convertToObjectComposition(transport.getObjectId());

                    if (composition == null) return false;
                    return Arrays.stream(composition.getActions()).filter(Objects::nonNull).noneMatch(currentAction::equals) && !Rs2Player.isAnimating();
                }, 300, 10000);
            case "Paddle Canoe":
                @Component final int DESTINATION_MAP_PARENT = 42401792; // 647.3
                @Component final int DESTINATION_LIST = 42401795; // 647.13

                Rs2GameObject.interact(transport.getObjectId(), "Paddle Canoe");

                boolean isDestinationMapVisible = sleepUntilTrue(() -> Rs2Widget.isWidgetVisible(DESTINATION_MAP_PARENT), 100, 10000);
                if (!isDestinationMapVisible) {
                    Microbot.log("Destination map is not visible within timeout period");
                    return false;
                }

                Widget destinationListWidget = Rs2Widget.getWidget(DESTINATION_LIST);
                if (destinationListWidget == null) return false;
                Widget destination = Rs2Widget.findWidget("Travel to " + displayInfo, List.of(destinationListWidget), false);
                Rs2Widget.clickWidget(destination);

                Rs2Dialogue.waitForCutScene(100, 15000);
                return sleepUntilTrue(() -> Rs2Player.getWorldLocation().distanceTo2D(transport.getDestination()) < (OFFSET * 2), 100, 5000);
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

    private static boolean handleCharterShip(Transport transport) {
        String npcName = transport.getName();

        Rs2NpcModel npc = Rs2Npc.getNpc(npcName);

        if (Rs2Npc.canWalkTo(npc, 20) && Rs2Npc.interact(npc, transport.getAction())) {
            Rs2Player.waitForWalking();
            sleepUntil(() -> Rs2Widget.isWidgetVisible(885, 4));
            List<Widget> destinationWidgets = Arrays.stream(Rs2Widget.getWidget(885, 4).getDynamicChildren())
                    .filter(w -> w.getActions() != null)
                    .collect(Collectors.toList());

            if (destinationWidgets.isEmpty()) return false;

            String destinationText = transport.getDisplayInfo();

            Widget destinationWidget = Rs2Widget.findWidget(destinationText, destinationWidgets);
            if (destinationWidget == null) return false;

            boolean isWidgetVisible = Microbot.getClientThread().runOnClientThreadOptional(() -> !destinationWidget.isHidden()).orElse(false);

            NewMenuEntry destinationMenuEntry = new NewMenuEntry(destinationText, "", 1, MenuAction.CC_OP, destinationWidget.getIndex(), destinationWidget.getId(), false);
            Microbot.doInvoke(destinationMenuEntry, (Rs2UiHelper.isRectangleWithinCanvas(destinationWidget.getBounds()) && isWidgetVisible) ? destinationWidget.getBounds() : new Rectangle(1,1));
            return true;
        }
        return false;
    }
    /**
     * interact with interfaces like spirit tree & xeric talisman etc...
     *
     * @param transport
     */
    private static boolean  interactWithAdventureLog(Transport transport) {
        if (transport.getDisplayInfo() == null || transport.getDisplayInfo().isEmpty()) return false;

        // Wait for the widget to become visible
        boolean isAdventureLogVisible = sleepUntilTrue(() -> !Rs2Widget.isHidden(ComponentID.ADVENTURE_LOG_CONTAINER), Rs2Player::isMoving, 100, 10000);

        if (!isAdventureLogVisible) {
            Microbot.log("Widget did not become visible within the timeout.");
            return false;
        }

        String destinationString = transport.getDisplayInfo().replaceAll("^\\d+:\\s*", "");
        Widget destinationWidget = Rs2Widget.findWidget(destinationString, List.of(Rs2Widget.getWidget(187, 3)));
        if (destinationWidget == null) return false;

        Rs2Widget.clickWidget(destinationWidget);
        Microbot.log("Traveling to " + transport.getDisplayInfo());
        return sleepUntilTrue(() -> Rs2Player.getWorldLocation().distanceTo2D(transport.getDestination()) < OFFSET, 100, 5000);
    }

    private static boolean interactWithNewRuneliteMenu(Transport transport,int itemId) {
        if (transport.getDisplayInfo() == null || transport.getDisplayInfo().isEmpty()) return false;

		Pattern pattern = Pattern.compile("^(\\d+)\\.");
		Matcher matcher = pattern.matcher(transport.getDisplayInfo());
		if (matcher.find()) {
			int menuOption = Integer.parseInt(matcher.group(1));
			String[] values = transport.getDisplayInfo().split(":");
			String destination = values[1].trim();
			int identifier = NewMenuEntry.findIdentifier(menuOption, getIdentifierOffset(transport.getDisplayInfo()));
			Rs2Inventory.interact(itemId, destination, identifier);
			if (transport.getDisplayInfo().toLowerCase().contains("burning amulet")) {
				Rs2Dialogue.sleepUntilHasDialogueOption("Okay, teleport to level");
				Rs2Dialogue.clickOption("Okay, teleport to level");
			}
			Microbot.log("Traveling to " + transport.getDisplayInfo());
			return sleepUntilTrue(() -> Rs2Player.getWorldLocation().distanceTo2D(transport.getDestination()) < OFFSET, 100, 5000);
		}
        return false;
    }

    private static int getIdentifierOffset(String itemName) {
        String lowerCaseItemName = itemName.toLowerCase();
        if (lowerCaseItemName.contains("ring of dueling") ||
                lowerCaseItemName.contains("games necklace") ||
                lowerCaseItemName.contains("skills necklace") ||
                lowerCaseItemName.contains("amulet of glory") ||
                lowerCaseItemName.contains("ring of wealth") ||
                lowerCaseItemName.contains("combat bracelet") ||
                lowerCaseItemName.contains("digsite pendant") ||
                lowerCaseItemName.contains("necklace of passage") ||
                lowerCaseItemName.contains("camulet") ||
                lowerCaseItemName.contains("burning amulet")) {
            return 6;
        } else if (lowerCaseItemName.contains("xeric's talisman") ||
                lowerCaseItemName.contains("slayer ring") ||
				lowerCaseItemName.contains("construct. cape")) {
            return 4;
        } else if (lowerCaseItemName.contains("kharedst's memoirs") ||
                   lowerCaseItemName.contains("giantsoul amulet")) {
            return 3;
        } else if (lowerCaseItemName.contains("enchanted lyre")) {
            return 2;
        } else {
            return 4; // Default offset if no match is found
        }
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

    private static final int SLOT_ONE_CW_ROTATION = 26083347;
    private static final int SLOT_ONE_ACW_ROTATION = 26083348;
    private static final int SLOT_TWO_CW_ROTATION = 26083349;
    private static final int SLOT_TWO_ACW_ROTATION = 26083350;
    private static final int SLOT_THREE_CW_ROTATION = 26083351;
    private static final int SLOT_THREE_ACW_ROTATION = 26083352;
    private static int fairyRingGraphicId = 569;

    public static boolean handleFairyRing(Transport transport) {

		Rs2ItemModel startingWeapon = null;

		TileObject fairyRingObject = Rs2GameObject.getAll(o -> Objects.equals(o.getWorldLocation(), transport.getOrigin())).stream().findFirst().orElse(null);
		if (fairyRingObject == null) return false;

		if (!Rs2GameObject.canWalkTo(fairyRingObject, 25)) return false;

		boolean hasLumbridgeElite = Microbot.getVarbitValue(Varbits.DIARY_LUMBRIDGE_ELITE) == 1;

		if (!hasLumbridgeElite) {
			if (Rs2Equipment.isWearing(EquipmentInventorySlot.WEAPON)) {
				startingWeapon = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
			}

			if (!Rs2Equipment.isWearing("Dramen staff") && !Rs2Equipment.isWearing("Lunar staff")) {
				if (Rs2Inventory.contains("Dramen staff")) {
					Rs2Inventory.equip("Dramen staff");
					sleepUntil(() -> Rs2Equipment.isWearing("Dramen staff"));
				} else if (Rs2Inventory.contains("Lunar staff")) {
					Rs2Inventory.equip("Lunar staff");
					sleepUntil(() -> Rs2Equipment.isWearing("Lunar staff"));
				} else {
					return false;
				}
			}
		}

		Microbot.log("Interacting with Fairy Ring @ " + fairyRingObject.getWorldLocation());
		Rs2GameObject.interact(fairyRingObject, "Configure");
		sleepUntil(() -> !Rs2Player.isMoving() && !Rs2Widget.isHidden(ComponentID.FAIRY_RING_TELEPORT_BUTTON), 10000);

		rotateSlotToDesiredRotation(SLOT_ONE, Rs2Widget.getWidget(SLOT_ONE).getRotationY(), getDesiredRotation(transport.getDisplayInfo().charAt(0)), SLOT_ONE_ACW_ROTATION, SLOT_ONE_CW_ROTATION);
		rotateSlotToDesiredRotation(SLOT_TWO, Rs2Widget.getWidget(SLOT_TWO).getRotationY(), getDesiredRotation(transport.getDisplayInfo().charAt(1)), SLOT_TWO_ACW_ROTATION, SLOT_TWO_CW_ROTATION);
		rotateSlotToDesiredRotation(SLOT_THREE, Rs2Widget.getWidget(SLOT_THREE).getRotationY(), getDesiredRotation(transport.getDisplayInfo().charAt(2)), SLOT_THREE_ACW_ROTATION, SLOT_THREE_CW_ROTATION);
		Rs2Widget.clickWidget(ComponentID.FAIRY_RING_TELEPORT_BUTTON);

		sleepUntil(() -> Rs2Player.hasSpotAnimation(fairyRingGraphicId));
		sleepUntil(() -> Objects.equals(Rs2Player.getWorldLocation(), transport.getDestination()) && !Rs2Player.hasSpotAnimation(fairyRingGraphicId), 10000);

		if (startingWeapon != null) {
			Rs2ItemModel finalStartingWeapon = startingWeapon;
			Rs2Inventory.equip(finalStartingWeapon.getId());
			sleepUntil(() -> Rs2Equipment.isWearing(finalStartingWeapon.getId()));
		}
		return true;
    }

	private static void rotateSlotToDesiredRotation(int slotId, int currentRotation, int desiredRotation, int slotAcwRotationId, int slotCwRotationId) {
		int anticlockwiseTurns = (desiredRotation - currentRotation + 2048) % 2048;
		int clockwiseTurns = (currentRotation - desiredRotation + 2048) % 2048;

		int turns = Math.min(clockwiseTurns, anticlockwiseTurns) / 512;
		boolean rotateCW = clockwiseTurns <= anticlockwiseTurns;
		int rotationWidget = rotateCW ? slotCwRotationId : slotAcwRotationId;

		for (int i = 0; i < turns; i++) {
			final int previousRotation = currentRotation;
			Rs2Widget.clickWidget(rotationWidget);

			sleepUntil(() -> {
				Widget slotWidget = Rs2Widget.getWidget(slotId);
				return slotWidget != null && slotWidget.getRotationY() != previousRotation;
			}, 2000);

			Widget slotWidget = Rs2Widget.getWidget(slotId);
			if (slotWidget != null) {
				currentRotation = slotWidget.getRotationY();
			} else {
				break;
			}
		}

		sleepUntil(() -> {
			Widget slotWidget = Rs2Widget.getWidget(slotId);
			return slotWidget != null && slotWidget.getRotationY() == desiredRotation;
		}, 3000);
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

	public static boolean isTeleportItem(int itemId) {
		if (ShortestPathPlugin.getPathfinderConfig().getAllTransports().isEmpty()) {
			ShortestPathPlugin.getPathfinderConfig().refresh();
		}

		Set<Integer> teleportItemIds = ShortestPathPlugin.getPathfinderConfig().getAllTransports().values()
			.stream()
			.flatMap(Set::stream)
			.filter(t -> TransportType.isTeleport(t.getType()))
			.map(Transport::getItemIdRequirements)
			.flatMap(Set::stream)
			.flatMap(Set::stream)
			.collect(Collectors.toSet());

		// Items that are not included in transports
		teleportItemIds.add(ItemID.DRAMEN_STAFF);
		teleportItemIds.add(ItemID.LUNAR_STAFF);

		return teleportItemIds.contains(itemId);
	}
}
