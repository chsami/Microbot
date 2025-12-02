package net.runelite.client.plugins.microbot.util.walker;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.annotations.Component;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.*;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.devtools.MovementFlag;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.shortestpath.*;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
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
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2Pvp;
import net.runelite.client.plugins.microbot.util.poh.PohTeleports;
import net.runelite.client.plugins.microbot.util.poh.PohTransport;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;

import javax.inject.Named;
import java.awt.*;
import java.util.*;
import java.util.List;
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
    static volatile WorldPoint currentTarget;
    static int nextWalkingDistance = 10;

    static final int OFFSET = 10; // max offset of the exact area we teleport to

    // Set this to true, if you want to calculate the path but do not want to walk to it
    static boolean debug = false;

    @Named("disableWalkerUpdate")
    static boolean disableWalkerUpdate;

    public static boolean disableTeleports = false;

    // Trapdoor and manhole mappings for open/closed states
    private static final Map<Integer, Integer> OPEN_TO_CLOSED_MAPPINGS = Map.of(
        1581, 1579, // open trapdoor -> closed trapdoor
        882, 881    // open manhole -> closed manhole
    );

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
    public static WalkerState walkWithState(WorldPoint target, int distance) {
        boolean walkWithBankedTransports = config.walkWithBankedTransports();
        if (walkWithBankedTransports){
            return walkWithBankedTransportsAndState(target, distance,false);
        }else {
            return walkWithStateInternal(target, distance);
        }
    }
    /**
     * Replaces the walkTo method
     *
     * @param target
     * @param distance
     * @return
     */
    private static WalkerState walkWithStateInternal(WorldPoint target, int distance) {
        if (Rs2Tile.getReachableTilesFromTile(Rs2Player.getWorldLocation(), distance).containsKey(target)
                || !Rs2Tile.isWalkable(LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), target)) && Rs2Player.getWorldLocation().distanceTo(target) <= distance) {
            return WalkerState.ARRIVED;
        }
        final Pathfinder pathfinder = ShortestPathPlugin.getPathfinder();
        if (pathfinder != null && !pathfinder.isDone())
            return WalkerState.MOVING;
        if ((currentTarget != null && currentTarget.equals(target)) && ShortestPathPlugin.getMarker() != null)
            return WalkerState.MOVING;
        setTarget(target);
        ShortestPathPlugin.setReachedDistance(distance);
        stuckCount = 0;

        if (Microbot.getClient().isClientThread()) {
            log.warn("Please do not call the walker from the main thread");
            return WalkerState.EXIT;
        }

		/*
			Close worldmap if it's open, this can happen when the walker is called from the panel or worldmap set target
		 */
		closeWorldMap();
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
     * Core walk method contains all the logic to successfully walk to the destination
     * this contains doors, game objects, teleports, spells etc...
     *
     * @param target
     * @param distance
     */
    private static WalkerState processWalk(WorldPoint target, int distance) {
        if (debug) {
            log.info("Pathfinder in debug, exiting");
            return WalkerState.EXIT;
        }
        try {
            if (!Microbot.isLoggedIn()) {
                setTarget(null);
            }

            // storing the reference ensures pathfinder cannot become null during
            Pathfinder pathfinder = ShortestPathPlugin.getPathfinder();
            if (pathfinder == null) {
                if (ShortestPathPlugin.getMarker() == null) {
                    setTarget(null);
                }
                pathfinder = sleepUntilNotNull(ShortestPathPlugin::getPathfinder, 2_000);
                if (pathfinder == null) {
                    log.error("Pathfinder took to long to initialize, exiting walker: 140");
                    setTarget(null);
                    return WalkerState.EXIT;
                }
            }

            if (!pathfinder.isDone()) {
                boolean isDone = sleepUntilTrue(pathfinder::isDone, 100, 10_000);
                if (!isDone) {
                    log.error("Pathfinder took to long to calculate path, exiting: 149");
                    setTarget(null);
                    return WalkerState.EXIT;
                }
            }

            if (ShortestPathPlugin.getMarker() == null) {
                log.error("marker is null, exiting: 156");
                setTarget(null);
                return WalkerState.EXIT;
            }

            final List<WorldPoint> path = pathfinder.getPath();
            final WorldPoint dst;
            if (path == null || path.isEmpty()) {
                log.debug("Path is {}, using current location as destination", path == null ? "null" : "empty");
                dst = Rs2Player.getWorldLocation();
            } else {
                dst = path.get(path.size()-1);
            }

            if (dst == null || dst.distanceTo(target) > distance) {
                log.warn("Location {} impossible to reach", dst);
                setTarget(null);
                return WalkerState.UNREACHABLE;
            }

            if (path == null || path.isEmpty()) {
                return WalkerState.ARRIVED;
            }

            if (isNear(dst)) {
                setTarget(null);
            }

            checkIfStuck();
            if (stuckCount > 10) {
                var moveableTiles = Rs2Tile.getReachableTilesFromTile(Rs2Player.getWorldLocation(), 5).keySet().toArray(new WorldPoint[0]);
                if (moveableTiles.length > 0) {
                    walkMiniMap(moveableTiles[Rs2Random.between(0, moveableTiles.length)]);
                    sleepGaussian(1000, 300);
                    stuckCount = 0;
                }
            }

            int indexOfStartPoint = getClosestTileIndex(path);
            if (indexOfStartPoint == -1) {
                log.error("The walker is confused, unable to find our starting point in the web, exiting.");
                setTarget(null);
                log.error("pathfinder is null, exiting: 255");
                return WalkerState.EXIT;
            }

            lastPosition = Rs2Player.getWorldLocation();

            if (Rs2Player.getWorldLocation().distanceTo(target) == 0 || path.size() <= 1) {
                setTarget(null);
                return WalkerState.ARRIVED;
            }

            // Edgeville/ardy wilderness lever warning
            if (Rs2Widget.isWidgetVisible(229, 1)) {
                if (Rs2Dialogue.getDialogueText().equalsIgnoreCase("Warning! The lever will teleport you deep into the Wilderness.")) {
                    log.info("Detected Wilderness lever warning, interacting...");
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
                WorldPoint nextWorldPoint = i + 1 < path.size() ? path.get(i + 1) : null;
                if (config.drawDebugPanel()) {
                    System.out.printf("start loop %s, from=%s, to=%s\n", i, currentWorldPoint, nextWorldPoint);
                }

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

                //Again, would be nice to have access to current node, since we're going to have to handle transports in instance (PoH)
                boolean inInstance = Microbot.getClient().getTopLevelWorldView().isInstance();
                if (PohTeleports.isInHouse() || !inInstance) {
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
                    var finalTile = (config.randomizeFinalTile() && moveableTiles.length > 0) ? moveableTiles[Rs2Random.between(0, moveableTiles.length)] : path.get(path.size() - 1);

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
                log.info("Pathfinder was interrupted, exiting: 397");
                setTarget(null);
                return WalkerState.EXIT;
            }
            log.error("Exception in Rs2Walker:", ex);
        }
        log.info("Exiting walker: 403");
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

        NewMenuEntry entry = new NewMenuEntry()
                .param0(canvasX)
                .param1(canvasY)
                .type(MenuAction.WALK)
                .identifier(0)
                .itemId(-1)
                .option("Walk here");

        Microbot.doInvoke(entry,
                new Rectangle(1, 1, Microbot.getClient().getCanvasWidth(), Microbot.getClient().getCanvasHeight()));
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
            log.error("Tried to walk worldpoint {} using the canvas but localpoint returned null", worldPoint);
            return false;
        }

        canv = Perspective.localToCanvas(Microbot.getClient(), localPoint, Microbot.getClient().getTopLevelWorldView().getPlane());

        int canvasX = canv != null ? canv.getX() : -1;
        int canvasY = canv != null ? canv.getY() : -1;

        //if the tile is not on screen, use minimap
        if (!Rs2Camera.isTileOnScreen(localPoint) || canvasX < 0 || canvasY < 0) {
            return Rs2Walker.walkMiniMap(worldPoint);
        }

        NewMenuEntry entry = new NewMenuEntry()
                .param0(canvasX)
                .param1(canvasY)
                .type(MenuAction.WALK)
                .identifier(0)
                .itemId(0)
                .option("Walk here");

        Microbot.doInvoke(entry,
                new Rectangle(canvasX, canvasY, Microbot.getClient().getCanvasWidth(), Microbot.getClient().getCanvasHeight()));
        return true;
    }

    public static WorldPoint walkCanvas(WorldPoint worldPoint) {
        LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), worldPoint);
        if (localPoint == null) {
            log.error("Tried to walkCanvas but localpoint returned null");
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
        // Create a WorldArea centered on the worldPoint by calculating the south-west corner
        WorldPoint pathPoint_SW = new WorldPoint(
                path.get(path.size() - 1).getX() - 2,
                path.get(path.size() - 1).getY() - 2,
                path.get(path.size() - 1).getPlane()
        );
        // Create a WorldArea centered on the worldPoint by calculating the south-west corner
        WorldPoint objectPoint_SW = new WorldPoint(
                destination.getX() - 2,
                destination.getY() - 2,
                destination.getPlane()
        );
        WorldArea pathArea = new WorldArea(pathPoint_SW, 5, 5);
        WorldArea objectArea = new WorldArea(objectPoint_SW, 5, 5);
        if (!pathArea.intersectsWith2D(objectArea)) {
            return Integer.MAX_VALUE;
        }

        return path.size();
    }

    /**
     * Calculates the total number of tiles from a given path to a destination.
     * This method validates that the path can actually reach the destination by checking
     * if the path's endpoint intersects with the destination area.
     *
     * @param path A list of WorldPoint objects representing the calculated path
     * @param destination The target WorldPoint destination to validate against
     * @return The total number of tiles in the path if valid, or Integer.MAX_VALUE if the path
     *         is empty, on different planes, or doesn't reach the destination
     */
    public static int getTotalTilesFromPath(List<WorldPoint> path, WorldPoint destination) {
        if (path.isEmpty() || path.get(path.size() - 1).getPlane() != destination.getPlane()) return Integer.MAX_VALUE;

        // Create centered WorldAreas instead of corner-based
        WorldPoint pathEndpoint = path.get(path.size() - 1);
        WorldPoint pathSouthWest = new WorldPoint(
                pathEndpoint.getX() - 4,
                pathEndpoint.getY() - 4,
                pathEndpoint.getPlane()
        );
        WorldArea pathArea = new WorldArea(pathSouthWest, 8, 8);

        WorldPoint destSouthWest = new WorldPoint(
                destination.getX() - 4,
                destination.getY() - 4,
                destination.getPlane()
        );
        WorldArea objectArea = new WorldArea(destSouthWest, 8, 8);

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
    public static boolean canReach(WorldPoint worldPoint, int sizeX, int sizeY, int pathSizeX, int pathSizeY,boolean useBankedItems) {
        boolean originalUseBankItems = ShortestPathPlugin.getPathfinderConfig().isUseBankItems();
        WorldArea pathArea = null;

        // Create centered WorldArea for the object instead of corner-based
        WorldPoint objectSouthWest = new WorldPoint(
                worldPoint.getX() - (sizeX + 2) / 2,
                worldPoint.getY() - (sizeY + 2) / 2,
                worldPoint.getPlane()
        );
        WorldArea objectArea = new WorldArea(objectSouthWest, sizeX + 2, sizeY + 2);

        try {
            ShortestPathPlugin.getPathfinderConfig().setUseBankItems(useBankedItems);
            ShortestPathPlugin.getPathfinderConfig().refresh(worldPoint);
            if (ShortestPathPlugin.getPathfinderConfig().getTransports().isEmpty()) {
                ShortestPathPlugin.getPathfinderConfig().refresh(worldPoint);
            }
            Pathfinder pathfinder = new Pathfinder(ShortestPathPlugin.getPathfinderConfig(), Rs2Player.getWorldLocation(), worldPoint);
            pathfinder.run();

            // Create centered WorldArea for the path endpoint instead of corner-based
            WorldPoint pathEndpoint = pathfinder.getPath().get(pathfinder.getPath().size() - 1);
            WorldPoint pathSouthWest = new WorldPoint(
                    pathEndpoint.getX() - pathSizeX / 2,
                    pathEndpoint.getY() - pathSizeY / 2,
                    pathEndpoint.getPlane()
            );
            pathArea = new WorldArea(pathSouthWest, pathSizeX, pathSizeY);
        } catch (Exception e) {
            log.trace("Exception in canReach: {} - ", e.getMessage(), e);
            return false;
        } finally {
            ShortestPathPlugin.getPathfinderConfig().setUseBankItems(originalUseBankItems);
            ShortestPathPlugin.getPathfinderConfig().refresh(worldPoint);
        }
        return pathArea != null ? pathArea.intersectsWith2D(objectArea) : false;
    }
    public static boolean canReach(WorldPoint worldPoint, int sizeX, int sizeY, int pathSizeX, int pathSizeY) {
        return canReach(worldPoint, sizeX, sizeY, pathSizeX, pathSizeY, false);
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
    public static boolean canReach(WorldPoint worldPoint, int sizeX, int sizeY, boolean useBankedItems) {
        return canReach(worldPoint, sizeX, sizeY, 2, 2, useBankedItems);
    }
    public static boolean canReach(WorldPoint worldPoint, boolean useBankedItems) {
        return canReach(worldPoint, 2, 2, 2, 2, useBankedItems);
    }
    public static boolean canReach(WorldPoint worldPoint, int sizeX, int sizeY, boolean useBankedItems, int pathSizeX, int pathSizeY) {
        return canReach(worldPoint, sizeX, sizeY, pathSizeX, pathSizeY, useBankedItems);
    }


    /**
     * Retrieves the walk path from the player's current location to the specified target location.
     * @param start The starting `WorldPoint` from which the path should be calculated.
     * @param target The target `WorldPoint` to which the path should be calculated.
     * @return A list of `WorldPoint` objects representing the path from the player's current location to the target.
     */
    public static List<WorldPoint> getWalkPath(WorldPoint start, WorldPoint target) {
        long startTime = System.nanoTime();
        ShortestPathPlugin.getPathfinderConfig().refresh(target);
        long pathfinderStartTime = System.nanoTime();
        Pathfinder pathfinderLocal = new Pathfinder(ShortestPathPlugin.getPathfinderConfig(), start, target);
        pathfinderLocal.run();
        List<WorldPoint> path = pathfinderLocal.getPath();
        long pathfinderEndTime = System.nanoTime();
        long totalEndTime = System.nanoTime();
        double configTimeMs = (pathfinderStartTime - startTime) / 1_000_000.0;
        double pathfinderTimeMs = (pathfinderEndTime - pathfinderStartTime) / 1_000_000.0;
        double totalTimeMs = (totalEndTime - startTime) / 1_000_000.0;

        StringBuilder performanceLog = new StringBuilder();
        performanceLog.append("getWalkPath Performance: ")
                .append("Config: ").append(String.format("%.2f ms", configTimeMs))
                .append(", Pathfinder: ").append(String.format("%.2f ms", pathfinderTimeMs))
                .append(", Total: ").append(String.format("%.2f ms", totalTimeMs))
                .append(" | Path: ").append(start).append(" -> ").append(target)
                .append(" (").append(path.size()).append(" waypoints)");

        log.debug(performanceLog.toString());

        return path;
    }
    /**
     * Retrieves the walk path from the player's current location to the specified target location.
     *
     * @param target The target `WorldPoint` to which the path should be calculated.
     * @return A list of `WorldPoint` objects representing the path from the player's current location to the target.
     */
    public static List<WorldPoint> getWalkPath(WorldPoint target) {
        return getWalkPath(Rs2Player.getWorldLocation(), target);
    }

    /**
     * Retrieves all transports found along the given path starting from a specific index.
     * Uses the default preferred transport type of TELEPORTATION_ITEM.
     *
     * @param path A list of WorldPoint objects representing the path to analyze
     * @param indexOfStartPoint The starting index in the path to begin searching for transports
     * @return A list of Transport objects found along the path, prioritizing teleportation items
     */
    public static List<Transport> getTransportsForPath(List<WorldPoint> path, int indexOfStartPoint) {
        return getTransportsForPath(path, indexOfStartPoint, TransportType.TELEPORTATION_ITEM, false);
    }

    /**
     * Retrieves all transports found along the given path starting from a specific index.
     * Analyzes the path for available transport options, prioritizing the specified transport type.
     *
     * This method examines each point in the path starting from the given index and identifies
     * available transport options (teleportation items, spells, objects, etc.) that can be used
     * to optimize travel. Transport types are sorted with teleportation items getting highest priority.
     *
     * @param path A list of WorldPoint objects representing the path to analyze
     * @param indexOfStartPoint The starting index in the path to begin searching for transports
     * @param prefTransportType The preferred transport type to prioritize in the search
     * @return A list of Transport objects found along the path, sorted by transport type priority
     */
    public static List<Transport> getTransportsForPath(List<WorldPoint> path, int indexOfStartPoint, TransportType prefTransportType) {
        return getTransportsForPath(path, indexOfStartPoint, prefTransportType, false);
    }

    /**
     * Retrieves all transports found along the given path starting from a specific index.
     * Analyzes the path for available transport options, prioritizing the specified transport type.
     * This version applies filtering and requirement setup for transports that require items.
     *
     * This method examines each point in the path starting from the given index and identifies
     * available transport options (teleportation items, spells, objects, etc.) that can be used
     * to optimize travel. Transport types are sorted with teleportation items getting highest priority.
     *
     * @param path A list of WorldPoint objects representing the path to analyze
     * @param indexOfStartPoint The starting index in the path to begin searching for transports
     * @param prefTransportType The preferred transport type to prioritize in the search
     * @param applyFiltering Whether to apply transport filtering and requirement setup
     * @return A list of Transport objects found along the path, sorted by transport type priority
     */
    public static List<Transport> getTransportsForPath(List<WorldPoint> path, int indexOfStartPoint, TransportType prefTransportType, boolean applyFiltering) {
        List<Transport> transportList = new ArrayList<>();
        int currentIndex = indexOfStartPoint;

        // Loop through the path until the end
        while (currentIndex < path.size()) {
            WorldPoint currentPoint = path.get(currentIndex);
            // Get any transports that start at this point (or keyed by this point)
            Set<Transport> transportsAtPoint = ShortestPathPlugin.getTransports()
                    .getOrDefault(currentPoint, new HashSet<>());
            boolean foundTransport = false;
            // sort by type to prioritize teleportation items first, then other types
            transportsAtPoint = transportsAtPoint.stream()
                    .sorted(Comparator.comparing(Transport::getType, (type1, type2) -> {
                        // sort teleportation items by preference transport type for the current path point.

                        if (type1 == prefTransportType && type2 != prefTransportType) {
                            return -1;
                        }
                        if (type2 == prefTransportType && type1 != prefTransportType) {
                            return 1;
                        }
                        // For all other types, use natural enum ordering
                        return type1.compareTo(type2);
                    }))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
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

        log.info("\n\nFound " + transportList.size() + " transports for path from " +
                path.get(0) + " to " + path.get(path.size() - 1));

        // Apply filtering and requirement setup if requested
        if (applyFiltering) {
            transportList = applyTransportFiltering(transportList);
        }

        return transportList;
    }

    /**
     * Applies transport filtering and requirement setup for transport items.
     * This method filters transports to only include those that require items and
     * sets up item requirements for fairy rings and currency-based transports.
     *
     * @param transports The list of transports to filter and process
     * @return The filtered and processed list of transports
     */
    private static List<Transport> applyTransportFiltering(List<Transport> transports) {
        return transports.stream()
                .filter(t -> t.getType() == TransportType.TELEPORTATION_ITEM || t.getType() == TransportType.FAIRY_RING ||
                        t.getType() == TransportType.TELEPORTATION_SPELL || t.getType() == TransportType.CANOE ||
                        t.getType() == TransportType.BOAT || t.getType() == TransportType.CHARTER_SHIP ||
                        t.getType() == TransportType.SHIP || t.getType() == TransportType.MINECART ||
                        t.getType() == TransportType.MAGIC_CARPET)
                .peek(t -> {
                    // Set fairy ring requirements if not already set
                    if (t.getType() == TransportType.FAIRY_RING &&
                            ((t.getItemIdRequirements() == null || t.getItemIdRequirements().isEmpty()) ) &&  Microbot.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE)  != 1) {
                        t.setItemIdRequirements(Set.of(Set.of(
                                ItemID.DRAMEN_STAFF,
                                ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF
                        )));
                    }

                    // Set currency requirements for currency-based transports
                    if (isCurrencyBasedTransport(t.getType()) &&
                            (t.getItemIdRequirements() == null || t.getItemIdRequirements().isEmpty()) &&
                            t.getCurrencyName() != null && !t.getCurrencyName().isEmpty() && t.getCurrencyAmount() > 0) {
                        int currencyItemId = getCurrencyItemId(t.getCurrencyName());
                        if (currencyItemId != -1) {
                            t.setItemIdRequirements(Set.of(Set.of(currencyItemId)));
                            log.debug("Set currency requirement for {}: {} x{} (ID: {})",
                                    t.getType(), t.getCurrencyName(), t.getCurrencyAmount(), currencyItemId);
                        }
                    }
                })
                .collect(Collectors.toList());
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
                log.error("Unable to find pickaxe to mine rockfall");
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

            if (object.getId() == ObjectID.MOTHERLODE_ROCKFALL_1 || object.getId() == ObjectID.MOTHERLODE_ROCKFALL_2) {
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

        if (isInstance && (toWp == null || fromWp == null)) {
            System.out.println("HandleDoor: isInstance=true fromWp=" + fromWp + ", toWp=" + toWp);
            //This happens when we're inside the PoH and the next tile is the teleport destination
            //Rs2WorldPoint.convertInstancedWorldPoint(rawTo) -> LocalPoint l = Rs2LocalPoint.fromWorldInstance(worldPoint) returns null
            return false;
        }

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
                // Ignore imposter objects
                if (comp == null || comp.getImpostorIds() != null || comp.getName().equals("null")) continue;

                String action = Arrays.stream(comp.getActions())
                        .filter(Objects::nonNull)
                        .filter(act -> doorActions.stream().anyMatch(dact -> act.toLowerCase().startsWith(dact.toLowerCase())))
                        .min(Comparator.comparing(act -> doorActions.indexOf(doorActions.stream().filter(dact -> act.toLowerCase().startsWith(dact)).findFirst().orElse(""))))
                        .orElse(null);

                if (action == null) continue;

                boolean found = false;

                final String name = comp.getName();

                if (object instanceof WallObject) {
                    int orientation = ((WallObject) object).getOrientationA();

                    if (searchNeighborPoint(orientation, probe, fromWp) || searchNeighborPoint(orientation, probe, toWp)) {
                        log.info("Found WallObject door - name {} with action {} at {} - from {} to {}", name, action, probe, fromWp, toWp);
                        found = true;
                    }
                } else {
                    if (name != null && name.toLowerCase().contains("door")) {
                        log.info("Found GameObject door - name {} with action {} at {} - from {} to {}", name, action, probe, fromWp, toWp);
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
        if (Rs2Dialogue.getDialogueText().toLowerCase().contains("two-factor authentication options") || Rs2Dialogue.getDialogueText().toLowerCase().contains("hopefully you will learn<br>much from us.")) {
            Rs2Dialogue.sleepUntilHasContinue();
            sleepUntil(() -> !Rs2Dialogue.hasContinue() || Rs2Dialogue.getDialogueText().toLowerCase().contains("to pass you must answer me"), Rs2Dialogue::clickContinue, 5000, Rs2Random.between(600, 800));
            if (!Rs2Dialogue.isInDialogue()) return true;
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
        WorldPoint _currentTarget = currentTarget;
        Rs2Walker.setTarget(null);
        Rs2Walker.setTarget(_currentTarget);
    }

    /**
     * @param target
     */
    public static void setTarget(WorldPoint target) {
        if (target != null && !Microbot.isLoggedIn()) {
            log.warn("Unable to set target: not logged in");
            return;
        }
        Player localPlayer = Microbot.getClient().getLocalPlayer();
        if (!ShortestPathPlugin.isStartPointSet() && localPlayer == null) {
            log.warn("Start point is not set and player is null");
            return;
        }

        currentTarget = target;

        if (target == null) {
            synchronized (ShortestPathPlugin.getPathfinderMutex()) {
                final Pathfinder pathfinder = ShortestPathPlugin.getPathfinder();
                if (pathfinder != null) {
                    pathfinder.cancel();
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
            final Pathfinder pathfinder = ShortestPathPlugin.getPathfinder();
            if (ShortestPathPlugin.isStartPointSet() && pathfinder != null) {
                start = pathfinder.getStart();
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

        Pathfinder pathfinder = ShortestPathPlugin.getPathfinder();
        if (pathfinder != null) {
            pathfinder.cancel();
            ShortestPathPlugin.getPathfinderFuture().cancel(true);
        }

        if (ShortestPathPlugin.getPathfindingExecutor() == null) {
            ThreadFactory shortestPathNaming = new ThreadFactoryBuilder().setNameFormat("shortest-path-%d").build();
            ShortestPathPlugin.setPathfindingExecutor(Executors.newSingleThreadExecutor(shortestPathNaming));
        }

        ShortestPathPlugin.getPathfinderConfig().refresh();
        if (Rs2Player.isInCave()) {
            pathfinder = new Pathfinder(ShortestPathPlugin.getPathfinderConfig(), start, ends);
            pathfinder.run();
            ShortestPathPlugin.getPathfinderConfig().setIgnoreTeleportAndItems(true);
            Pathfinder pathfinderWithoutTeleports = new Pathfinder(ShortestPathPlugin.getPathfinderConfig(), start, ends);
            pathfinderWithoutTeleports.run();
            var lastPath = pathfinderWithoutTeleports.getPath().get(pathfinderWithoutTeleports.getPath().size()-1);
            var pathWithoutTeleportsIsReachable = lastPath.distanceTo(ends.stream().findFirst().orElse(lastPath)) <= config.reachedDistance();
            if (pathWithoutTeleportsIsReachable && pathfinder.getPath().size() >= pathfinderWithoutTeleports.getPath().size()) {
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
                log.error("getTile instancedWorldPoint is null");
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
    private static boolean handleTransports(List<WorldPoint> path, int indexOfStartPoint) {
        Set<Transport> transports = ShortestPathPlugin.getTransports().getOrDefault(path.get(indexOfStartPoint), new HashSet<>());
        log.debug("Found transports: {}", transports.stream().map(Transport::getDisplayInfo).collect(Collectors.joining(", ")));
        for (Transport transport : transports) {
            Collection<WorldPoint> worldPointCollections;
            //in some cases the getOrigin is null, for teleports that start the player location
            if (transport.getOrigin() == null) {
                worldPointCollections = Collections.singleton(null);
            } else {
                worldPointCollections = WorldPoint.toLocalInstance(Microbot.getClient().getTopLevelWorldView(), transport.getOrigin());
            }
            log.debug("Considering transport: {}", transport.getDisplayInfo());
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
                                sleepUntil(Rs2Dialogue::isInDialogue,600*2);

                                if (Objects.equals(transport.getName(), "Veos") && Objects.equals(transport.getAction(), "Talk-to")) {
                                    sleepUntil(() -> !Rs2Dialogue.hasContinue(), Rs2Dialogue::clickContinue, 5000, Rs2Random.between(600, 800));
                                    Rs2Dialogue.clickOption("Can you take me somewhere?");
                                    sleepUntil(() -> !Rs2Dialogue.hasContinue() && !Rs2Dialogue.hasSelectAnOption(), Rs2Dialogue::clickContinue, 5000, Rs2Random.between(600, 800));
                                    Rs2Dialogue.clickOption(transport.getDisplayInfo());
                                    sleepUntil(() -> !Rs2Dialogue.hasContinue() && !Rs2Dialogue.hasSelectAnOption(), Rs2Dialogue::clickContinue, 5000, Rs2Random.between(600, 800));
                                }

                                if (Objects.equals(transport.getName(), "Captain Magoro") && Objects.equals(transport.getAction(), "Talk-to")) {
                                    sleepUntil(() -> !Rs2Dialogue.hasContinue(), Rs2Dialogue::clickContinue, 5000, Rs2Random.between(600, 800));
                                    Rs2Dialogue.clickOption(transport.getDisplayInfo());
                                    sleepUntil(() -> !Rs2Dialogue.hasContinue() && !Rs2Dialogue.hasSelectAnOption(), Rs2Dialogue::clickContinue, 5000, Rs2Random.between(600, 800));
                                }

                                if (Rs2Dialogue.clickOption("I'm just going to Pirates' cove")){
                                    sleep(600 * 2);
                                    Rs2Dialogue.clickContinue();
                                } else if (Objects.equals(transport.getName(), "Mountain Guide")) {
                                    Rs2Dialogue.clickOption(transport.getDisplayInfo());
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

                    log.debug("Handling {} transport: {}", transport.getType(), transport.getDisplayInfo());
                    if (transport.getType() == TransportType.POH) {
                        if (handlePohTransport(transport)) {
                            sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(transport.getDestination()) < OFFSET, 10000);
                            break;
                        }
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
                            Rs2Tab.switchTo(InterfaceTab.INVENTORY);
                            break;
                        }
                    }

                    if (transport.getObjectId() <= 0) break;

                    // Use class-level constants for object ID mapping
                    List<Integer> objectIdsToSearch = new ArrayList<>();
                    objectIdsToSearch.add(transport.getObjectId());

                    // If this transport is for an open trapdoor, also search for the closed version
                    if (OPEN_TO_CLOSED_MAPPINGS.containsKey(transport.getObjectId())) {
                        objectIdsToSearch.add(OPEN_TO_CLOSED_MAPPINGS.get(transport.getObjectId()));
                    }

                    List<TileObject> objects = Rs2GameObject.getAll(o -> objectIdsToSearch.contains(o.getId()), transport.getOrigin(), 10).stream()
                            .sorted(Comparator.comparingInt(o -> o.getWorldLocation().distanceTo(transport.getOrigin())))
                            .collect(Collectors.toList());

                    TileObject object = objects.stream().findFirst().orElse(null);
                    if (object instanceof GroundObject) {
                        object = objects.stream()
                                .filter(o -> !Objects.equals(o.getWorldLocation(), Microbot.getClient().getLocalPlayer().getWorldLocation()))
                                .min(Comparator.comparing(o -> ((TileObject) o).getWorldLocation().distanceTo(transport.getOrigin()))
                                        .thenComparing(o -> ((TileObject) o).getWorldLocation().distanceTo(transport.getDestination()))).orElse(null);
                    }

                    if (object != null) {
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

    /**
     * Handles the transportation process specifically for instances of PohTransport.
     * Any Transport param that reaches this is assumed to be a PohTransport.
     *
     * @param transport the transport object to be checked and processed
     * @return true if the transport is an instance of PohTransport and its transport method executes successfully, false otherwise
     */
    private static boolean handlePohTransport(Transport transport) {
        if(!(transport instanceof PohTransport)) {
            throw new IllegalStateException("handlePohTransport should not be called for non-PohTransports");
        }
        return ((PohTransport)transport).execute();
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
        for (Map.Entry<Integer, Integer> entry : OPEN_TO_CLOSED_MAPPINGS.entrySet()) {
            final int closedTrapdoorId = entry.getKey();
            final int openTrapdoorId = entry.getValue();

            if (transport.getObjectId() == openTrapdoorId) {
                if (tileObject.getId() == closedTrapdoorId) {
                    Rs2GameObject.interact(tileObject, "Open");
                    sleepUntil(() -> Rs2GameObject.exists(openTrapdoorId));
                    TileObject openTrapdoor = Rs2GameObject.getAll(o -> o.getId() == openTrapdoorId, tileObject.getWorldLocation(), 10).stream().findFirst().orElse(null);
                    if (openTrapdoor != null) {
                        Rs2GameObject.interact(openTrapdoor, transport.getAction());
                    }
                } else if (tileObject.getId() == openTrapdoorId) {
                    Rs2GameObject.interact(tileObject, transport.getAction());
                }
                sleepUntil(() -> !Rs2Player.isAnimating());
                sleepUntilTrue(() -> Rs2Player.getWorldLocation().distanceTo(transport.getDestination()) < OFFSET);
                return true;
            }
        }

        //Al kharid broken wall will animate once and then stop and then animate again
        if (tileObject.getId() == ObjectID.KHARID_POSHWALL_TOPLESS || tileObject.getId() == ObjectID.KHARID_BIGWINDOW) {
            Rs2Player.waitForAnimation();
            Rs2Player.waitForAnimation();
            return true;
        }
        // Handle Leaves Traps in Isafdar Forest
        if (tileObject.getId() == ObjectID.REGICIDE_PITFALL_SIDE) {
            Rs2Player.waitForAnimation(1200);
            if (Rs2Player.getWorldLocation().getY() > 6400) {
                Rs2GameObject.interact(ObjectID.REGICIDE_TRAP_HAND_HOLDS);
                sleepUntil(() -> Rs2Player.getWorldLocation().getY() < 6400);
            } else {
                sleepUntil(() -> !Rs2Player.isMoving() && !Rs2Player.isAnimating());
            }
            return true;
        }
        // Handle Ferox Encalve Barrier
        if (tileObject.getId() == ObjectID.WILDY_HUB_ENTRY_BARRIER || tileObject.getId() == ObjectID.WILDY_HUB_ENTRY_BARRIER_M) {
            if (Rs2Dialogue.isInDialogue()) {
                if (Rs2Dialogue.getDialogueText().toLowerCase().contains("when returning to the enclave")) {
                    Rs2Dialogue.clickContinue();
                    Rs2Dialogue.sleepUntilSelectAnOption();
                    Rs2Dialogue.keyPressForDialogueOption("Yes, and don't ask again.");
                    Rs2Dialogue.sleepUntilNotInDialogue();
                    return true;
                }
            }
        }
        // Handle Cobwebs blocking path
        if (tileObject.getId() == ObjectID.BIGWEB_SLASHABLE && !Rs2Equipment.isWearing(ItemID.ARANEA_BOOTS)) {
            sleepUntil(() -> !Rs2Player.isMoving() && !Rs2Player.isAnimating(1200));
            final WorldPoint webLocation = tileObject.getWorldLocation();
            final WorldPoint currentPlayerPoint = Microbot.getClient().getLocalPlayer().getWorldLocation();
            boolean doesWebStillExist = Rs2GameObject.getAll(o -> Objects.equals(webLocation, o.getWorldLocation()) && o.getId() == ObjectID.BIGWEB_SLASHABLE).stream().findFirst().isPresent();
            if (doesWebStillExist) {
                sleepUntil(() -> Rs2GameObject.getAll(o -> Objects.equals(webLocation, o.getWorldLocation()) && o.getId() == ObjectID.BIGWEB_SLASHABLE).stream().findFirst().isEmpty(),
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
        if (tileObject.getId() == ObjectID.KARAM_DUNGEON_STONE1 || tileObject.getId() == ObjectID.KARAM_DUNGEON_STONE2) {
            Rs2Player.waitForAnimation(600 * 7);
            return true;
        }

        // Handle Morte Myre Cave Agility Shortcut
        if (tileObject.getId() == ObjectID.FAIRY2_ROUTE_CAVEWALLTUNNEL) {
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
        if (tileObject.getId() == ObjectID.CAVEWALL_SHORTCUT_ROYAL_TITANS_EAST || tileObject.getId() == ObjectID.CAVEWALL_SHORTCUT_ROYAL_TITANS_WEST) {
            Rs2Player.waitForAnimation();
        }

        // Handle Rev Cave Dialogue
        if (tileObject.getId() == ObjectID.WILD_CAVE_ENTRANCE_LOW) {
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

        if (tileObject.getId() == ObjectID.HEROROCKSLIDE) {
            Rs2Player.waitForAnimation(600 * 4);
            return true;
        }

        if (Rs2GameObject.getObjectIdsByName("Fossil_Rowboat").contains(tileObject.getId())) {
            if (transport.getDisplayInfo() == null || transport.getDisplayInfo().isEmpty()) return false;

            char option = transport.getDisplayInfo().charAt(0);
            Rs2Dialogue.sleepUntilSelectAnOption();
            Rs2Keyboard.keyPress(option);
            sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo2D(transport.getDestination()) < OFFSET, 10000);
            return true;
        }

        // Handle door/gate near wilderness agility course
        if (tileObject.getId() == ObjectID.BALANCEGATE52A || tileObject.getId() == ObjectID.BALANCEGATE52B_RIGHT || tileObject.getId() == ObjectID.BALANCEGATE52B_LEFT) {
            Rs2Player.waitForAnimation(600 * 4);
            return true;
        }

        if (tileObject.getId() == ObjectID.AERIAL_FISHING_BOAT) {
            Rs2Dialogue.sleepUntilSelectAnOption();
            Rs2Dialogue.clickOption(transport.getDisplayInfo(), true);
            sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo2D(transport.getDestination()) < OFFSET, 10000);
            return true;
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
        if (Rs2Pvp.isInWilderness() && (Rs2Pvp.getWildernessLevelFrom(Rs2Player.getWorldLocation()) > (transport.getMaxWildernessLevel() + 1)))
            return false;
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
                succesfullAction = handleWearableTeleports(transport, itemId) || handleInventoryTeleports(transport, itemId);
            }
        }
        return succesfullAction;
    }

    private static boolean handleInventoryTeleports(Transport transport, int itemId) {
        Rs2ItemModel rs2Item = Rs2Inventory.get(itemId);
        if (rs2Item == null) return false;

        // A list of generic teleports that can be used if no parsable destination action is found
        List<String> genericKeyWords = Arrays.asList(
                "invoke", "empty", "consume", "open", "teleport", "rub", "break", "reminisce", "signal", "play", "commune", "squash"
        );

        // Return true when the item does not use a generic keyword to teleport to its destination
        boolean hasParsableDestination = transport.getDisplayInfo().contains(":");
        String destination = hasParsableDestination
                ? transport.getDisplayInfo().split(":")[1].trim().toLowerCase()
                : transport.getDisplayInfo().trim().toLowerCase();

        boolean wildernessTransport = PathfinderConfig.isInWilderness(WorldPointUtil.packWorldPoint(transport.getDestination()));

        log.debug("Trying to find action for destination={}", destination);
        // Check if item has destination as direct action
        String itemAction = rs2Item.getAction(destination);

        // Check if item has destination as sub-menu action
        Map.Entry<String,Integer> sub = rs2Item.getIndexOfSubAction(destination);
        if (itemAction == null && sub != null && sub.getKey() != null) {
            itemAction = destination;
        }

        // If there's only one destination with the item possible, a generic action will also work
        if (itemAction == null && !hasParsableDestination) {
            itemAction = rs2Item.getActionFromList(genericKeyWords);
        }

        if (itemAction != null) {
            boolean interaction = Rs2Inventory.interact(rs2Item, itemAction);
            if (!interaction) {
                return false;
            } else if (wildernessTransport) {
                Rs2Dialogue.sleepUntilInDialogue();
                return Rs2Dialogue.clickOption("Yes", "Okay");
            }
            return true;
        }

        // If no location-based action found, try generic actions
        itemAction = rs2Item.getActionFromList(genericKeyWords);

        if (itemAction == null) {
            log.debug("No generic keyword found for={}, genericKeywords={}", itemAction, String.join(",", genericKeyWords));
            return false;
        }

        if (Rs2Inventory.interact(itemId, itemAction)) {
            log.debug("Traveling with genericAction={}, to {} - ({})", itemAction, transport.getDisplayInfo(), transport.getDestination());

            if (itemAction.equalsIgnoreCase("open") && itemId == ItemID.BOOKOFSCROLLS_CHARGED) {
                return handleMasterScrollBook(destination);
            } else if (wildernessTransport) {
                Rs2Dialogue.sleepUntilInDialogue();
                return Rs2Dialogue.clickOption("Yes", "Okay");
            } else {
                Rs2Player.waitForAnimation();
                log.info("Unsure how to handle this itemTransport={} action={}", transport, itemAction);
            }
        }
        return false;
    }

    private static boolean handleWearableTeleports(Transport transport, int itemId) {
        Rs2ItemModel rs2Item = Rs2Equipment.get(itemId);
        if (rs2Item == null) return false;
        if (transport.getDisplayInfo().contains(":")) {
            String[] values = transport.getDisplayInfo().split(":");
            String destination = values[1].trim().toLowerCase();

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
            log.info("Traveling to {} - ({})", transport.getDisplayInfo(), transport.getDestination());
            return true;
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
        if (worldPoints == null || worldPoints.length < 2 || worldPoints[0] == null || worldPoints[1] == null) {
            throw new IllegalArgumentException("isInArea requires two WorldPoints.");
        }
        WorldPoint a = worldPoints[0];
        WorldPoint b = worldPoints[1];
        final int aX = a.getX(), aY = a.getY();
        final int bX = b.getX(), bY = b.getY();

        final int minX = Math.min(aX, bX);
        final int maxX = Math.max(aX, bX);
        final int minY = Math.min(aY, bY);
        final int maxY = Math.max(aY, bY);

        final WorldPoint playerLocation = Rs2Player.getWorldLocation();
        final int playerX = playerLocation.getX();
        final int playerY = playerLocation.getY();

        // draws box from 2 points to check against all variations of player X,Y from said points.
        return (playerX >= minX && playerX <= maxX && playerY >= minY && playerY <= maxY);
    }

    /**
     * Checks if the player's current location is within the specified range from the given center point.
     *
     * @param centerOfArea a WorldPoint which is the center of the desired area,
     * @param range        an int of range to which the boundaries will be drawn in a square,
     * @return true if the player's current location is within the specified area, false otherwise
     */
    public static boolean isInArea(WorldPoint centerOfArea, int range) {
        WorldPoint seCorner = new WorldPoint(centerOfArea.getX() + range, centerOfArea.getY() - range, centerOfArea.getPlane());
        WorldPoint nwCorner = new WorldPoint(centerOfArea.getX() - range, centerOfArea.getY() + range, centerOfArea.getPlane());
        return isInArea(seCorner, nwCorner); // call to our sibling
    }

    public static boolean isNear() {
        final Pathfinder pathfinder = ShortestPathPlugin.getPathfinder();
        if (pathfinder == null) return false; // idk are we near if we don't have a path?
        final List<WorldPoint> path = pathfinder.getPath();
        if (path == null) return false;

        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        int index = IntStream.range(0, pathfinder.getPath().size())
                .filter(f -> path.get(f).distanceTo2D(playerLocation) < 3)
                .findFirst().orElse(-1);
        return index >= Math.max(path.size() - 10, 0);
    }

    /**
     * @param target
     * @return
     */
    public static boolean isNear(WorldPoint target) {
        return Rs2Player.getWorldLocation().equals(target);
    }

    public static boolean isNearPath() {
        final Pathfinder pathfinder = ShortestPathPlugin.getPathfinder();
        if (pathfinder == null) return true;

        final List<WorldPoint> path = pathfinder.getPath();
        if (path == null || path.isEmpty()) return true;

        final WorldPoint loc = Rs2Player.getWorldLocation();
        if (loc == null) return true;

        if (config.recalculateDistance() < 0 || lastPosition.equals(lastPosition = loc)) {
            return true;
        }

        if (config.usePoh() && PohTeleports.isInHouse()) {
            //Would be nice to have access to current node here and check if the current Node is a POH transport node.
            return true;
        }

        var reachableTiles = Rs2Tile.getReachableTilesFromTile(Rs2Player.getWorldLocation(), config.recalculateDistance() - 1);
        for (WorldPoint point : path) {
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
        Set<WorldPoint> ends = Set.of(endpoint);
        Pathfinder pathfinder = new Pathfinder(ShortestPathPlugin.getPathfinderConfig(), startpoint, ends);
        pathfinder.run();
        return pathfinder.getPath().size();
    }

    private static boolean handleSpiritTree(Transport transport) {
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
            Rs2Tab.switchTo(InterfaceTab.CHAT);
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

            NewMenuEntry destinationMenuEntry = new NewMenuEntry()
                    .option("Select")
                    .target("")
                    .identifier(1)
                    .type(MenuAction.CC_OP)
                    .param0(destinationWidget.getIndex())
                    .param1(minigameWidgetParent.getId())
                    .forceLeftClick(false);

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
            log.error("Unable to find canoe action");
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
                    log.error("Canoe shape text is not visible within timeout period");
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
                    log.error("Destination map is not visible within timeout period");
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
        @Component
        int VARLAMORE_QUETZAL_MAP = InterfaceID.QuetzalMenu.CONTENTS;
        @Component
        int VARLAMORE_QUETZAL_OPTIONS = InterfaceID.QuetzalMenu.ICONS;
        String displayInfo = transport.getDisplayInfo();
        if (displayInfo == null || displayInfo.isEmpty()) return false;

        Rs2NpcModel renu = Rs2Npc.getNpc(NpcID.QUETZAL_CHILD_GREEN);

        if (Rs2Tile.isTileReachable(transport.getOrigin()) && Rs2Npc.interact(renu, "travel")) {
            Rs2Player.waitForWalking();
            boolean isVarlamoreMapVisible = sleepUntilTrue(() -> Rs2Widget.isWidgetVisible(VARLAMORE_QUETZAL_MAP), 100, 10000);

            if (!isVarlamoreMapVisible) {
                log.error("Varlamore Map Widget not visable within timeout");
                return false;
            }

            Widget quetzalMapWidget = Rs2Widget.getWidget(VARLAMORE_QUETZAL_OPTIONS);
            List<Widget> quetzalMapChildren = Arrays.stream(quetzalMapWidget.getDynamicChildren())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            Widget actionWidget = Rs2Widget.findWidget(displayInfo, quetzalMapChildren, false);
            if (actionWidget != null) {
                Rs2Widget.clickWidget(actionWidget);
                log.info("Traveling to {} - ({})", transport.getDisplayInfo(), transport.getDestination());
                return sleepUntilTrue(() -> Rs2Player.getWorldLocation().distanceTo2D(transport.getDestination()) < OFFSET, 100, 5000);
            }
        }
        return false;
    }

    private static boolean handleMasterScrollBook(String destination) {
        boolean isMasterScrollBookOpen = sleepUntilTrue(() -> Rs2Widget.isWidgetVisible(InterfaceID.Bookofscrolls.CONTENTS), 100, 10000);
        if (!isMasterScrollBookOpen) {
            log.error("Master Scroll Book did not open within timeout period");
            return false;
        }

        Widget bookOfScrollsWidget = Rs2Widget.getWidget(InterfaceID.Bookofscrolls.CONTENTS);
        List<Widget> bookOfScrollsChildren = Arrays.stream(bookOfScrollsWidget.getStaticChildren())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Widget destinationWidget = Rs2Widget.findWidget(destination, bookOfScrollsChildren, false);
        if (destinationWidget == null) return false;
        boolean interaction = Rs2Widget.clickWidget(destinationWidget);
        if (interaction && destination.equalsIgnoreCase("Revenant cave")) {
            Rs2Dialogue.sleepUntilInDialogue();
            return Rs2Dialogue.clickOption("Yes, teleport me now");
        }
        return interaction;
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
        log.info("Charter Ship NPC: " + npcName + " - " + (npc != null ? npc.getId() : "not found"));
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

            NewMenuEntry destinationMenuEntry = new NewMenuEntry()
                    .option(destinationText)
                    .target("")
                    .identifier(1)
                    .type(MenuAction.CC_OP)
                    .param0(destinationWidget.getIndex())
                    .param1(destinationWidget.getId())
                    .forceLeftClick(false);

            Microbot.doInvoke(destinationMenuEntry, new Rectangle(1, 1));
            return true;
        }
        return false;
    }
    /**
     * interact with interfaces like spirit tree etc...
     *
     * @param transport
     */
    private static boolean  interactWithAdventureLog(Transport transport) {
        if (transport.getDisplayInfo() == null || transport.getDisplayInfo().isEmpty()) return false;

        // Wait for the widget to become visible
        boolean isAdventureLogVisible = sleepUntilTrue(() -> !Rs2Widget.isHidden(ComponentID.ADVENTURE_LOG_CONTAINER), Rs2Player::isMoving, 100, 10000);

        if (!isAdventureLogVisible) {
            log.error("Widget did not become visible within the timeout.");
            return false;
        }

        String destinationString = transport.getDisplayInfo().replaceAll("^\\d+:\\s*", "");
        Widget destinationWidget = Rs2Widget.findWidget(destinationString, List.of(Rs2Widget.getWidget(187, 3)));
        if (destinationWidget == null) return false;

        Rs2Widget.clickWidget(destinationWidget);
        log.info("Traveling to {} - ({})", transport.getDisplayInfo(), transport.getDestination());
        return sleepUntilTrue(() -> Rs2Player.getWorldLocation().distanceTo2D(transport.getDestination()) < OFFSET, 100, 5000);
    }

    private static boolean handleGlider(Transport transport) {
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
            log.error("Widget did not become visible within the timeout.");
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
                log.error("{} not found on the interface.", displayInfo);
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

    private static boolean handleFairyRing(Transport transport) {

        Rs2ItemModel startingWeapon = null;

        TileObject fairyRingObject = PohTeleports.isInHouse() ? PohTeleports.getFairyRings() : Rs2GameObject.getAll(o -> Objects.equals(o.getWorldLocation(), transport.getOrigin())).stream().findFirst().orElse(null);
        if (fairyRingObject == null) return false;

        if (!PohTeleports.isInHouse() && !Rs2GameObject.canWalkTo(fairyRingObject, 25)) return false;

        boolean hasLumbridgeElite = Microbot.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE) == 1;

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

        String lastDestinationAction = "last-destination (" + transport.getDisplayInfo() + ")";
        String treeLastDestinationAction = "Ring-last-destination (" + transport.getDisplayInfo() + ")";
        ObjectComposition composition = Rs2GameObject.convertToObjectComposition(fairyRingObject);
        log.info("Interacting with Fairy Ring @ {}", fairyRingObject.getWorldLocation());

        // we can use the last-destination to handle fairy rings
        if (Rs2GameObject.hasAction(composition, lastDestinationAction, true)) {
            Rs2GameObject.interact(fairyRingObject, lastDestinationAction);
        } else if (Rs2GameObject.hasAction(composition, treeLastDestinationAction, true)) {
            Rs2GameObject.interact(fairyRingObject, treeLastDestinationAction);
        } else {
            // We have to configure fairy rings through the interface
            if (Rs2GameObject.hasAction(composition, "Configure", true)) {
                Rs2GameObject.interact(fairyRingObject, "Configure");
            } else if (Rs2GameObject.hasAction(composition, "Ring-configure", true)) {
                Rs2GameObject.interact(fairyRingObject, "Ring-configure");
            }
            sleepUntil(() -> !Rs2Player.isMoving() && !Rs2Widget.isHidden(ComponentID.FAIRY_RING_TELEPORT_BUTTON), 10000);

            rotateSlotToDesiredRotation(SLOT_ONE, Rs2Widget.getWidget(SLOT_ONE).getRotationY(), getDesiredRotation(transport.getDisplayInfo().charAt(0)), SLOT_ONE_ACW_ROTATION, SLOT_ONE_CW_ROTATION);
            rotateSlotToDesiredRotation(SLOT_TWO, Rs2Widget.getWidget(SLOT_TWO).getRotationY(), getDesiredRotation(transport.getDisplayInfo().charAt(1)), SLOT_TWO_ACW_ROTATION, SLOT_TWO_CW_ROTATION);
            rotateSlotToDesiredRotation(SLOT_THREE, Rs2Widget.getWidget(SLOT_THREE).getRotationY(), getDesiredRotation(transport.getDisplayInfo().charAt(2)), SLOT_THREE_ACW_ROTATION, SLOT_THREE_CW_ROTATION);
            Rs2Widget.clickWidget(ComponentID.FAIRY_RING_TELEPORT_BUTTON);
        }

        sleepUntil(() -> Rs2Player.getGraphicId() == fairyRingGraphicId, 5000);
        sleepUntil(() -> Objects.equals(Rs2Player.getWorldLocation(), transport.getDestination()) && Rs2Player.getGraphicId() != fairyRingGraphicId, 10000);

        if (startingWeapon != null) {
            Rs2ItemModel finalStartingWeapon = startingWeapon;
            Rs2Inventory.equip(finalStartingWeapon.getId());
            sleepUntil(() -> Rs2Equipment.isWearing(finalStartingWeapon.getId()));
        }
        return true;
    }

    /**
     * Rotates a fairy ring slot to the desired rotation value.
     * Calculates the most efficient rotation direction (clockwise or anticlockwise)
     * and performs the necessary number of rotations to reach the target.
     *
     * @param slotId The widget ID of the slot to rotate
     * @param currentRotation The current rotation value of the slot
     * @param desiredRotation The target rotation value to achieve
     * @param slotAcwRotationId The widget ID for anticlockwise rotation button
     * @param slotCwRotationId The widget ID for clockwise rotation button
     */
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

    /**
     * Maps fairy ring letters to their corresponding rotation values.
     * Each letter corresponds to a specific rotation degree needed for fairy ring teleportation.
     *
     * @param letter The fairy ring letter (A-Z) to get rotation for
     * @return The rotation value (0, 512, 1024, or 1536) for the letter, or -1 if invalid
     */
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

    /**
     * Checks if the specified item ID corresponds to a teleportation item.
     * This method examines all available transports and determines if the given item
     * can be used for teleportation purposes, including special items like dramen staff.
     *
     * @param itemId The item ID to check for teleportation capabilities
     * @return true if the item is a teleportation item, false otherwise
     */
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
        teleportItemIds.add(ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF);

        return teleportItemIds.contains(itemId);
    }


    /**
     * Finds the nearest accessible target from a list of WorldPoints using pathfinding.
     * This is a generalized version of the logic used in Rs2Bank.getNearestBank().
     *
     * @param startPoint The starting location for pathfinding
     * @param targets List of target WorldPoints to evaluate
     * @param tolerance Tolerance in tiles for matching the final path point to targets (default: 2)
     * @return The index of the nearest accessible target in the list, or -1 if none are reachable
     */
    public static int findNearestAccessibleTarget(WorldPoint startPoint, List<WorldPoint> targets, boolean useBankItems, int tolerance) {
        if (targets == null || targets.isEmpty()) {
            return -1;
        }

        if (startPoint == null) {
            startPoint = Rs2Player.getWorldLocation();
        }

        if (startPoint == null) {
            log.warn("Unable to determine starting point for pathfinding");
            return -1;
        }

        // Convert list to set for pathfinder
        Set<WorldPoint> targetSet = new HashSet<>(targets);

        // Store original configuration to restore later
        boolean originalUseBankItems =  ShortestPathPlugin.getPathfinderConfig().isUseBankItems();
        try {
            ShortestPathPlugin.getPathfinderConfig().setUseBankItems(useBankItems);
            // Configure pathfinder
            ShortestPathPlugin.getPathfinderConfig().refresh();
            // Run pathfinder
            Pathfinder pf = new Pathfinder(ShortestPathPlugin.getPathfinderConfig(), startPoint, targetSet);
            pf.run();

            List<WorldPoint> path = pf.getPath();
            if (path.isEmpty()) {
                log.debug("Unable to find path to any target from starting point: " + startPoint);
                return -1;
            }

            // Find which target corresponds to the end of the path
            WorldPoint nearestTile = path.get(path.size() - 1);
            WorldArea nearestTileArea = new WorldArea(nearestTile, tolerance, tolerance);

            // Find the target that matches the final path destination
            for (int i = 0; i < targets.size(); i++) {
                WorldPoint target = targets.get(i);
                WorldArea targetArea = new WorldArea(target, tolerance, tolerance);
                if (targetArea.intersectsWith2D(nearestTileArea)) {
                    log.debug("Found nearest accessible target at index " + i + ": " + target + " (path ended at: " + nearestTile + ")");
                    return i;
                }
            }

            log.debug("Path found but no target matched the destination: " + nearestTile);
            return -1;

        } finally {
            // Always restore original configuration
            ShortestPathPlugin.getPathfinderConfig().setUseBankItems(originalUseBankItems);
            ShortestPathPlugin.getPathfinderConfig().refresh();
        }
    }

    /**
     * Finds the nearest accessible target from a list of WorldPoints using pathfinding.
     * Uses default tolerance of 2 tiles and no bank item usage.
     *
     * @param startPoint The starting location for pathfinding
     * @param targets List of target WorldPoints to evaluate
     * @return The index of the nearest accessible target in the list, or -1 if none are reachable
     */
    public static int findNearestAccessibleTarget(WorldPoint startPoint, List<WorldPoint> targets) {
        return findNearestAccessibleTarget(startPoint, targets, false, 2);
    }

    /**
     * Finds the nearest accessible target from a list of WorldPoints using pathfinding.
     * Uses the player's current location as starting point.
     *
     * @param targets List of target WorldPoints to evaluate
     * @param useBankItems Whether to enable bank item usage for transport calculations
     * @return The index of the nearest accessible target in the list, or -1 if none are reachable
     */
    public static int findNearestAccessibleTarget(List<WorldPoint> targets, boolean useBankItems) {
        return findNearestAccessibleTarget(Rs2Player.getWorldLocation(), targets, useBankItems, 2);
    }

    /**
     * Finds the nearest accessible target from a list of WorldPoints using pathfinding.
     * Uses the player's current location as starting point and no bank item usage.
     *
     * @param targets List of target WorldPoints to evaluate
     * @return The index of the nearest accessible target in the list, or -1 if none are reachable
     */
    public static int findNearestAccessibleTarget(List<WorldPoint> targets) {
        return findNearestAccessibleTarget(Rs2Player.getWorldLocation(), targets, false, 2);
    }

    /**
     * Prepares and analyzes required transport items for reaching a destination.
     * Similar but improved to Rs2Slayer.prepareItemTransports()
     *
     * @param destination The target location to reach
     * @param useBankItems Whether to consider bank items in pathfinding
     * @return List of Transport objects that are missing required items
     */
    public static List<Transport> getTransportsForDestination(WorldPoint destination, boolean useBankItems) {
        return getTransportsForDestination(destination, useBankItems, TransportType.TELEPORTATION_ITEM);
    }

    /**
     * Prepares and analyzes required transport items for reaching a destination.
     * Similar but improved to Rs2Slayer.prepareItemTransports()
     *
     * @param destination The target location to reach
     * @param useBankItems Whether to consider bank items in pathfinding
     * @param prefTransportType The preferred transport type to prioritize
     * @return List of Transport objects that are missing required items
     */
    public static List<Transport> getTransportsForDestination(WorldPoint destination, boolean useBankItems, TransportType prefTransportType) {
        if (destination == null) {
            return new ArrayList<>();
        }

        boolean originalUseBankItems = ShortestPathPlugin.getPathfinderConfig().isUseBankItems();
        try {
            // Store and configure pathfinder settings
            ShortestPathPlugin.getPathfinderConfig().setUseBankItems(useBankItems);
            ShortestPathPlugin.getPathfinderConfig().refresh();
            // Run pathfinder
            Pathfinder pf = new Pathfinder(ShortestPathPlugin.getPathfinderConfig(), Rs2Player.getWorldLocation(), destination);
            pf.run();

            List<WorldPoint> path = pf.getPath();
            if (path.isEmpty()) {
                log.debug("Unable to find path to destination: " + destination);
                return new ArrayList<>();
            }

            // Get transports along the path
            List<Transport> transports = getTransportsForPath(path, 0, prefTransportType, true);

            // Log found transports for debugging
            transports.forEach(t -> log.debug("Transport found: " + t));

            return transports;

        } finally {
            // Always restore original configuration
            ShortestPathPlugin.getPathfinderConfig().setUseBankItems(originalUseBankItems);
            ShortestPathPlugin.getPathfinderConfig().refresh();
        }
    }

    /**
     * Prepares and analyzes required transport items for reaching a destination.
     * Uses bank items in calculations by default.
     *
     * @param destination The target location to reach
     * @return List of Transport objects that are missing required items
     */
    public static List<Transport> prepareTransportsForDestination(WorldPoint destination) {
        return getTransportsForDestination(destination, true);
    }

    /**
     * Checks if the player has the required items for a specific transport.
     * Similar to Rs2Slayer.hasRequiredTeleportItem() but accessible in Rs2Walker.
     *
     * @param transport The transport to check requirements for
     * @return true if the player has all required items, false otherwise
     */
    public static boolean hasRequiredTransportItems(Transport transport) {
        if (transport == null) {
            return false;
        }

        if (transport.getType() == TransportType.FAIRY_RING) {
            return Rs2Inventory.hasItem(ItemID.DRAMEN_STAFF) ||
                    Rs2Equipment.isWearing(ItemID.DRAMEN_STAFF) ||
                    Rs2Inventory.hasItem(ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF) ||
                    Rs2Equipment.isWearing(ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF) ||  Microbot.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE)  == 1;
        } else if (transport.getType() == TransportType.TELEPORTATION_ITEM ||
                transport.getType() == TransportType.TELEPORTATION_SPELL || transport.getType() == TransportType.CANOE ||
                transport.getType() == TransportType.BOAT || transport.getType() == TransportType.CHARTER_SHIP ||
                transport.getType() == TransportType.SHIP || transport.getType() == TransportType.MINECART ||
                transport.getType() == TransportType.MAGIC_CARPET
        ) {
            if (transport.getType() == TransportType.TELEPORTATION_SPELL && transport.getDisplayInfo() != null) {
                // Extract spell name from displayInfo (handle potential format "spellname:option")
                String spellName = transport.getDisplayInfo().contains(":")
                        ? transport.getDisplayInfo().split(":")[0].trim()
                        : transport.getDisplayInfo().trim();
                // Find matching Rs2Spells enum by name (case-insensitive partial match)
                boolean hasMultipleDestination = transport.getDisplayInfo().contains(":");
                String displayInfo = hasMultipleDestination
                        ? transport.getDisplayInfo().split(":")[0].trim().toLowerCase()
                        : transport.getDisplayInfo();
                log.debug("Looking for spell rune requirements for: '{}' - display info {}", spellName, displayInfo);
                Rs2Spells rs2Spell = Rs2Magic.getRs2Spell(displayInfo);
                return Rs2Magic.hasRequiredRunes(rs2Spell);
            }
            if (isCurrencyBasedTransport(transport.getType()) &&
                    (transport.getItemIdRequirements() == null || transport.getItemIdRequirements().isEmpty()) &&
                    transport.getCurrencyName() != null && !transport.getCurrencyName().isEmpty() && transport.getCurrencyAmount() > 0) {
                int currencyItemId = getCurrencyItemId(transport.getCurrencyName());
                return Rs2Inventory.count(currencyItemId) >= transport.getCurrencyAmount();
            }
            if (transport.getItemIdRequirements() == null || transport.getItemIdRequirements().isEmpty()) {
                return true; // No requirements specified
            }

            return transport.getItemIdRequirements()
                    .stream()
                    .flatMap(Collection::stream)
                    .anyMatch(itemId -> Rs2Equipment.isWearing(itemId) || Rs2Inventory.hasItem(itemId));
        }

        return true; // For other transport types, assume available for now -> we need to think about later
    }

    /**
     * Filters a list of transports to return only those missing required items.
     * Similar to Rs2Slayer.getMissingItemTransports() but accessible in Rs2Walker.
     *
     * @param transports List of transports to check
     * @return List of transports that are missing required items
     */
    public static List<Transport> getMissingTransports(List<Transport> transports) {
        if (transports == null) {
            return new ArrayList<>();
        }

        return transports.stream()
                .filter(t -> !hasRequiredTransportItems(t))
                .collect(Collectors.toList());
    }

    /**
     * Extracts item IDs and their required quantities for the given transports that are missing and available in bank.
     * Enhanced version that uses Rs2Magic and Rs2Spells systems for actual rune quantities on teleportation spells.
     *
     * @param transports List of transports to check for missing items
     * @return Map where key=itemId and value=quantity needed (actual quantities for teleportation spells)
     */
    public static Map<Integer, Integer> getMissingTransportItemIdsWithQuantities(List<Transport> transports) {
        if (transports == null) {
            return new HashMap<>();
        }

        Map<Integer, Integer> itemQuantityMap = new HashMap<>();

        transports.stream()
                .forEach(transport -> {
                    // Special handling for teleportation spells - use actual rune requirements
                    if (transport.getType() == TransportType.TELEPORTATION_SPELL) {
                        Map<Integer, Integer> spellRuneRequirements = getSpellRuneRequirements(transport);
                        if (!spellRuneRequirements.isEmpty()) {
                            // Check if any of the required runes are available in bank
                            spellRuneRequirements.forEach((runeItemId, requiredQuantity) -> {
                                try {
                                    int bankQuantity = Rs2Bank.count(runeItemId);
                                    if (bankQuantity >= requiredQuantity) {
                                        int currentQuantity = itemQuantityMap.getOrDefault(runeItemId, 0);
                                        itemQuantityMap.put(runeItemId, currentQuantity + requiredQuantity);
                                        log.debug("Added teleportation spell rune requirement: {} (ID: {}) x{} (bank has: {})",
                                                runeItemId, runeItemId, requiredQuantity, bankQuantity);
                                    }
                                } catch (Exception e) {
                                    log.debug("Could not check bank for rune " + runeItemId + ": " + e.getMessage());
                                }
                            });
                        }
                        return; // Skip normal item requirement processing for spell transports
                    }

                    // Normal processing for non-spell transports
                    if (transport.getItemIdRequirements() != null) {
                        for (Set<Integer> alternativeItems : transport.getItemIdRequirements()) {
                            // For each alternative set, we need ANY one of these items
                            // Check if we have any of the alternatives in bank
                            boolean hasAnyAlternative = alternativeItems.stream()
                                    .anyMatch(itemId -> {
                                        try {
                                            if (isCurrencyBasedTransport(transport.getType()) && transport.getCurrencyAmount() > 0) {
                                                // For currency-based transports, check if we have enough currency
                                                return Rs2Bank.count(itemId) >= transport.getCurrencyAmount();
                                            } else {
                                                // For regular items, just check if we have the item
                                                return Rs2Bank.hasItem(itemId);
                                            }
                                        } catch (Exception e) {
                                            log.debug("Could not check bank for item " + itemId + ": " + e.getMessage());
                                            return false;
                                        }
                                    });

                            if (hasAnyAlternative) {
                                // Find the first available alternative in bank and add it to our map
                                alternativeItems.stream()
                                        .filter(itemId -> {
                                            try {
                                                if (isCurrencyBasedTransport(transport.getType()) && transport.getCurrencyAmount() > 0) {
                                                    // For currency-based transports, check if we have enough currency
                                                    return Rs2Bank.count(itemId) >= transport.getCurrencyAmount();
                                                } else {
                                                    // For regular items, just check if we have the item
                                                    return Rs2Bank.hasItem(itemId);
                                                }
                                            } catch (Exception e) {
                                                log.debug("Could not check bank for item " + itemId + ": " + e.getMessage());
                                                return false;
                                            }
                                        })
                                        .findFirst()
                                        .ifPresent(itemId -> {
                                            // Determine required quantity based on transport type
                                            int requiredQuantity;
                                            if (isCurrencyBasedTransport(transport.getType()) && transport.getCurrencyAmount() > 0) {
                                                // For currency-based transports, use the actual currency amount
                                                requiredQuantity = transport.getCurrencyAmount();
                                                log.debug("Currency-based transport {} requires {} x{}",
                                                        transport.getType(), transport.getCurrencyName(), requiredQuantity);
                                            } else {
                                                // For regular items (teleportation items, fairy rings, etc.), assume 1 is needed
                                                requiredQuantity = 1;
                                            }

                                            int currentQuantity = itemQuantityMap.getOrDefault(itemId, 0);
                                            itemQuantityMap.put(itemId, currentQuantity + requiredQuantity);
                                        });
                                break; // Only need one item from this alternative set
                            }
                        }
                    }
                });

        return itemQuantityMap;
    }

    /**
     * Gets the actual rune requirements for a teleportation spell transport.
     * Maps spell names to Rs2Spells enum and extracts rune quantities.
     *
     * @param transport The teleportation spell transport
     * @return Map of item IDs to required quantities for the spell's runes
     */
    private static Map<Integer, Integer> getSpellRuneRequirements(Transport transport) {
        Map<Integer, Integer> runeRequirements = new HashMap<>();
        if (transport.getType() != TransportType.TELEPORTATION_SPELL || transport.getDisplayInfo() == null) {
            return runeRequirements;
        }
        try {
            // Extract spell name from displayInfo (handle potential format "spellname:option")
            String spellName = transport.getDisplayInfo().contains(":")
                    ? transport.getDisplayInfo().split(":")[0].trim()
                    : transport.getDisplayInfo().trim();
            // Find matching Rs2Spells enum by name (case-insensitive partial match)
            boolean hasMultipleDestination = transport.getDisplayInfo().contains(":");
            String displayInfo = hasMultipleDestination
                    ? transport.getDisplayInfo().split(":")[0].trim().toLowerCase()
                    : transport.getDisplayInfo();
            log.debug("Looking for spell rune requirements for: '{}' - display info {}", spellName, displayInfo);
            Rs2Spells rs2Spell = Rs2Magic.getRs2Spell(displayInfo);
            if (rs2Spell == null) return runeRequirements;
            // Get rune requirements and check for elemental runes that might be provided by staves
            Map<Runes, Integer> requiredRunes = Rs2Magic.getRequiredRunes(rs2Spell,1,true);
            List<Runes> elementalRunes = rs2Spell.getElementalRunes();
            log.debug("Spell '{}' requires {} runes, including {} elemental runes",
                    spellName, requiredRunes.size(), elementalRunes.size());
            // Convert rune requirements to item IDs with quantities
            requiredRunes.forEach((rune, quantity) -> {
                int runeItemId = rune.getItemId();
                runeRequirements.put(runeItemId, quantity);
                log.debug("Spell '{}' requires {} x {} (ID: {})",
                        spellName, quantity, rune.name(), runeItemId);
            });

        } catch (Exception e) {
            log.warn("Error getting spell rune requirements for transport '{}': {}",
                    transport.getDisplayInfo(), e.getMessage());
        }

        return runeRequirements;
    }

    /**
     * Checks if a transport type is currency-based (requires coins or other currency).
     *
     * @param transportType The transport type to check
     * @return true if the transport type requires currency
     */
    private static boolean isCurrencyBasedTransport(TransportType transportType) {
        return transportType == TransportType.BOAT ||
                transportType == TransportType.CHARTER_SHIP ||
                transportType == TransportType.SHIP ||
                transportType == TransportType.MINECART ||
                transportType == TransportType.MAGIC_CARPET;
    }

    /**
     * Maps currency name to item ID from RuneLite API.
     *
     * @param currencyName The name of the currency (e.g., "Coins")
     * @return The item ID for the currency, or -1 if not found
     */
    private static int getCurrencyItemId(String currencyName) {
        if (currencyName == null || currencyName.trim().isEmpty()) {
            return -1;
        }

        String currency = currencyName.trim().toLowerCase();
        switch (currency) {
            case "coins":
                return ItemID.COINS;
            case "ecto-token":
                return ItemID.ECTOTOKEN;
            // Add more currencies as needed
            default:
                log.warn("Unknown currency type: {}", currencyName);
                return -1;
        }
    }

    /**
     * Extracts item IDs that are missing for the given transports and available in bank.
     * Legacy method maintained for backward compatibility.
     * Similar to Rs2Slayer.getMissingItemIds() but accessible in Rs2Walker.
     *
     * @param transports List of transports to check for missing items
     * @return List of item IDs that are needed and available in bank
     */
    public static List<Integer> getMissingTransportItemIds(List<Transport> transports) {
        return new ArrayList<>(getMissingTransportItemIdsWithQuantities(transports).keySet());
    }

    /**
     * Compares the efficiency of traveling directly to a target versus going via bank first.
     * This is useful when transport items may be needed from the bank.
     *
     * @param target The target destination
     * @param startPoint Starting location (null to use current player location)
     * @return TransportRouteAnalysis containing the analysis of both routes
     */
    public static TransportRouteAnalysis compareRoutes(WorldPoint startPoint,WorldPoint target) {
        long totalStartTime = System.nanoTime();
        StringBuilder performanceLog = new StringBuilder();
        performanceLog.append("\n\t=== compareRoutes Performance Analysis ===\n");
        if (target == null) {
            return new TransportRouteAnalysis(new ArrayList<>(), null, null,new ArrayList<>(),new ArrayList<>(), "Target location is null");
        }

        if (startPoint == null) {
            startPoint = Rs2Player.getWorldLocation();
        }

        if (startPoint == null) {
            return new TransportRouteAnalysis(new ArrayList<>(), null, null, new ArrayList<>(),new ArrayList<>(),"Cannot determine starting location");
        }

        try {
            // Get direct path distance with timing
            performanceLog.append("\tStart Point: ").append(startPoint).append(", Target: ").append(target).append("\n");
            long directPathStartTime = System.nanoTime();
            List<WorldPoint> directPath = getWalkPath(startPoint, target);
            long directPathEndTime = System.nanoTime();
            double directPathTimeMs = (directPathEndTime - directPathStartTime) / 1_000_000.0;

            int directDistance = getTotalTilesFromPath(directPath, target);
            performanceLog.append("\t-Direct path calculation: ").append(String.format("%.2f ms", directPathTimeMs))
                    .append(" (").append(directPath.size()).append(" waypoints, ").append(directDistance).append(" tiles)\n");

            // Find nearest bank and calculate banking route distance
            BankLocation nearestBank = null;
            List<WorldPoint> pathToBank  = new ArrayList<>();
            List<WorldPoint> pathWithBankedItemsToTarget = new ArrayList<>();
            int bankingRouteDistance = -1;

            try {




                boolean originalUseBankItems = ShortestPathPlugin.getPathfinderConfig().isUseBankItems();
                try {
                    ShortestPathPlugin.getPathfinderConfig().setUseBankItems(true);
                    ShortestPathPlugin.getPathfinderConfig().refresh(target);

                    performanceLog.append("\t-Bank items available: ").append(Rs2Bank.bankItems().size()).append("\n");

                    long pathWithBankedItemsStartTime = System.nanoTime();
                    pathWithBankedItemsToTarget = getWalkPath(startPoint, target);
                    long pathWithBankedItemsEndTime = System.nanoTime();
                    double pathWithBankedItemsTimeMs = (pathWithBankedItemsEndTime - pathWithBankedItemsStartTime) / 1_000_000.0;

                    int distanceWithBankedItemsToTarget = getTotalTilesFromPath(pathWithBankedItemsToTarget, target);
                    bankingRouteDistance = distanceWithBankedItemsToTarget;

                    performanceLog.append("\t-Path from start to target with banked items: ").append(String.format("%.2f ms", pathWithBankedItemsTimeMs))
                            .append(" (").append(pathWithBankedItemsToTarget.size()).append(" waypoints, ").append(distanceWithBankedItemsToTarget).append(" tiles)\n");
                    performanceLog.append("\t-Total banking route distance: ").append(bankingRouteDistance).append(" tiles\n");

                } finally {
                    // Always restore original configuration
                    ShortestPathPlugin.getPathfinderConfig().setUseBankItems(false);
                    ShortestPathPlugin.getPathfinderConfig().refresh();
                }
                if (bankingRouteDistance<directDistance){
                    long bankSearchStartTime = System.nanoTime();
                    nearestBank = Rs2Bank.getNearestBank(startPoint);
                    long bankSearchEndTime = System.nanoTime();
                    double bankSearchTimeMs = (bankSearchEndTime - bankSearchStartTime) / 1_000_000.0;
                    if (nearestBank != null) {
                        performanceLog.append("\t-Nearest bank search: ").append(String.format("%.2f ms", bankSearchTimeMs));
                        WorldPoint bankLocation = nearestBank.getWorldPoint();
                        performanceLog.append("\t -> Found: ").append(nearestBank).append(" at ").append(bankLocation).append("\n");

                        // Calculate distance from start point to bank
                        long pathToBankStartTime = System.nanoTime();
                        pathToBank = getWalkPath(startPoint, bankLocation);
                        long pathToBankEndTime = System.nanoTime();
                        double pathToBankTimeMs = (pathToBankEndTime - pathToBankStartTime) / 1_000_000.0;

                        int distanceToBank = getTotalTilesFromPath(pathToBank, bankLocation);
                        performanceLog.append("\t-Path to bank calculation: ").append(String.format("%.2f ms", pathToBankTimeMs))
                                .append(" (").append(pathToBank.size()).append(" waypoints, ").append(distanceToBank).append(" tiles)\n");
                        bankingRouteDistance += distanceToBank;
                    } else {
                        performanceLog.append("\t -> No accessible bank found\n");
                    }
                }

            } catch (Exception e) {
                performanceLog.append("Banking route calculation failed: ").append(e.getMessage()).append("\n");
                log.debug("Could not calculate banking route: " + e.getMessage());
            }

            long totalEndTime = System.nanoTime();
            double totalTimeMs = (totalEndTime - totalStartTime) / 1_000_000.0;
            performanceLog.append("\t=== Total compareRoutes time: ").append(String.format("%.2f ms", totalTimeMs)).append(" ===\n");

            if (bankingRouteDistance == -1) {
                performanceLog.append("\tResult: Direct route only (banking route unavailable)\n");
                log.info(performanceLog.toString());
                return new TransportRouteAnalysis(directPath, null, null, new ArrayList<>(),new ArrayList<>(),
                        "Direct route only (banking route unavailable)");
            }

            boolean directIsFaster = directDistance <= bankingRouteDistance;
            String recommendation = directIsFaster ?
                    String.format("\tDirect route is faster (%d vs %d tiles)", directDistance, bankingRouteDistance) :
                    String.format("\tBanking route is faster (%d vs %d tiles)", bankingRouteDistance, directDistance);

            performanceLog.append("\tResult:\n\t\t ").append(recommendation).append("\n");
            log.info(performanceLog.toString());

            return new TransportRouteAnalysis(directPath,
                    nearestBank, nearestBank != null ? nearestBank.getWorldPoint() : null,pathToBank,pathWithBankedItemsToTarget, recommendation);

        } catch (Exception e) {
            long totalEndTime = System.nanoTime();
            double totalTimeMs = (totalEndTime - totalStartTime) / 1_000_000.0;
            performanceLog.append("ERROR after ").append(String.format("%.2f ms", totalTimeMs)).append(": ").append(e.getMessage()).append("\n");
            log.warn(performanceLog.toString());
            log.warn("Error comparing routes to " + target + ": " + e.getMessage());
            e.printStackTrace();
            return new TransportRouteAnalysis(new ArrayList<>(), null, null,new ArrayList<>(),new ArrayList<>(), "Error calculating routes: " + e.getMessage());
        }
    }

    /**
     * Compares direct vs banking route using current player location as start point.
     */
    public static TransportRouteAnalysis compareRoutes(WorldPoint target) {
        return compareRoutes(null,target);
    }

    /**
     * Travels to the target destination using the legacy walkTo-based approach with transport support.
     * Uses default settings: considers bank items and allows efficiency-based banking decisions.
     *
     * @param target The destination to travel to
     * @return true if travel was successful, false otherwise
     */
    public static boolean walkWithBankedTransports(WorldPoint target) {
        return walkWithBankedTransports(target, false);
    }
    public static boolean walkWithBankedTransports(WorldPoint target, boolean forceBanking) {
        return walkWithBankedTransportsAndState(target, 10, forceBanking) == WalkerState.ARRIVED;
    }
    public static boolean walkWithBankedTransports(WorldPoint target, int distance, boolean forceBanking){
        WalkerState state = walkWithBankedTransportsAndState(target, distance, forceBanking);
        return state == WalkerState.ARRIVED;

    }
    /**
     * Travels to the target destination using the legacy walkTo-based approach with transport support.
     * Analyzes whether to go directly or via bank first for transport items.
     *
     * @param target The destination to travel to
     * @param forceBanking If true, forces banking route regardless of efficiency
     * @return true if travel was successful, false otherwise
     */
    public static WalkerState walkWithBankedTransportsAndState(WorldPoint target, int distance, boolean forceBanking) {
        if (target == null) {
            log.warn("Cannot travel to null target location");
            return WalkerState.EXIT;
        }
        if (Microbot.getClient().isClientThread()) {
            log.error("Please do not call the walker from the main thread");
            return WalkerState.EXIT;
        }
        if (Rs2Tile.getReachableTilesFromTile(Rs2Player.getWorldLocation(), distance).containsKey(target)
                || !Rs2Tile.isWalkable(LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), target)) && Rs2Player.getWorldLocation().distanceTo(target) <= distance) {
            return WalkerState.ARRIVED;
        }
        final Pathfinder pathfinder = ShortestPathPlugin.getPathfinder();
        if (pathfinder != null && !pathfinder.isDone())
            return WalkerState.MOVING;
        if ((currentTarget != null && currentTarget.equals(target)) && ShortestPathPlugin.getMarker() != null){
            return WalkerState.MOVING;
        }
        Rs2Walker.currentTarget =  null;
        // Check what transport items are needed
        TransportRouteAnalysis comparison = compareRoutes(target);
        List<Transport> missingTransports = getMissingTransports(getTransportsForDestination(target, true, TransportType.TELEPORTATION_SPELL));

        Map<Integer, Integer> missingItemsWithQuantities = getMissingTransportItemIdsWithQuantities(missingTransports);
        if(!missingTransports.isEmpty()){
            log.info("\n\tFor {} transports to destination in the bank to target {} we found {} missing items",
                    missingTransports.size(), target, missingItemsWithQuantities.size());
        }
        // If no missing transport items, go directly
        if (missingItemsWithQuantities.isEmpty() && !forceBanking) {
            log.info("\n\tNo missing transport items, traveling directly to: \n\t" + target);
            setTarget(null); // Clear target to avoid conflicts
            WalkerState state = walkWithStateInternal(target, distance);
            if (state == WalkerState.ARRIVED) {
                log.info("\n\tArrived directly at target: " + target);
            } else {
                log.warn("\n\tFailed to arrive directly at target: " + target + ", state: " + state);
                setTarget(null);
                return state;

            }
            return state;
        } else {
            // Compare routes if we have missing items that could be obtained from bank
            // Use config for minimum bank route savings
            int minBankRouteSavings = config != null ? config.minBankRouteSavings() : 0;
            int tileSavings = comparison.getTileSavings();
            boolean bankRouteIsBetter = !comparison.isDirectIsFaster() && tileSavings >= minBankRouteSavings;
            // If forced banking or banking route is more efficient (with min savings), go via bank
            if (forceBanking || bankRouteIsBetter) {
                if (comparison.getNearestBank() != null) {
                    log.info("\n\tUsing banking route: \n\t\tStart: {} -> Bank: {} -> Target: {}",
                            Rs2Player.getWorldLocation(), comparison.getBankLocation(), target);
                    // Handle the complete banking workflow using legacy walkTo approach
                    return walkWithBankingState(comparison.getBankLocation(), missingItemsWithQuantities, target, distance);
                } else {
                    log.warn("\n\tBanking route requested but no accessible bank found, trying direct route");
                    setTarget(null); // Clear target to avoid conflicts
                    return walkWithStateInternal(target, distance);
                }
            } else {
                log.info("\n\tDirect route is more efficient despite missing items or does not meet min savings, traveling directly");
                setTarget(null); // Clear target to avoid conflicts
                return walkWithStateInternal(target, distance);
            }
        }


    }





    /**
     * Handles the complete banking workflow using legacy walkTo: walk to bank, open, withdraw items, close, continue to target.
     * Enhanced version that accepts a map of item IDs with their required quantities and returns boolean.
     *
     * @param bankLocation The bank location to visit
     * @param missingItemsWithQuantities Map of item IDs and their required quantities
     * @param finalTarget The final destination after banking
     * @return true if the banking workflow was successful, false otherwise
     */
    private static boolean walkWithBanking(WorldPoint bankLocation, Map<Integer, Integer> missingItemsWithQuantities, WorldPoint finalTarget) {
        return walkWithBankingState(bankLocation, missingItemsWithQuantities, finalTarget, 10)== WalkerState.ARRIVED;
    }

    /**
     * Handles the complete banking workflow using walkWithState: walk to bank, open, withdraw items, close, continue to target.
     * Enhanced version that accepts a map of item IDs with their required quantities and returns WalkerState.
     *
     * @param missingItemsWithQuantities Map of item IDs and their required quantities
     * @param finalTarget The final destination after banking
     * @return WalkerState indicating the result of the banking workflow
     */
    private static WalkerState walkWithBankingState(WorldPoint bankLocation,
                                                    Map<Integer, Integer> missingItemsWithQuantities,
                                                    WorldPoint finalTarget,int distance) {
        try {
            if (bankLocation == null || finalTarget == null) {
                log.warn("Cannot perform banking workflow with null locations");
                return WalkerState.EXIT;
            }
            // Step 1: Walk to bank
            setTarget(null); // Clear current target to avoid conflicts
            WalkerState bankWalkResult = walkWithStateInternal(bankLocation, distance);
            if (bankWalkResult != WalkerState.ARRIVED) {
                log.warn("Failed to arrive at bank at: " + bankLocation + ", state: " + bankWalkResult);
                return bankWalkResult;
            }
            log.info("Arrived at bank location: " + bankLocation);
            // Step 2: Open bank
            if (!Rs2Bank.openBank()) {
                log.warn("Failed to open bank at: " + bankLocation);
                return WalkerState.EXIT;
            }
            if(!sleepUntil(()-> Rs2Bank.isOpen(), 8000)) {
                log.warn("Failed to open bank within timeout at: " + bankLocation);
                return WalkerState.EXIT;
            }

            // Step 3: Withdraw missing transport items
            if (!missingItemsWithQuantities.isEmpty()) {
                log.debug("Withdrawing transport items with quantities: " + missingItemsWithQuantities);

                // Withdraw the correct amount of each unique item
                for (Map.Entry<Integer, Integer> entry : missingItemsWithQuantities.entrySet()) {
                    int itemId = entry.getKey();
                    int amountNeeded = entry.getValue();
                    int currentCount = Rs2Inventory.count(itemId);
                    int amountToWithdraw = Math.max(0, amountNeeded );

                    if (amountToWithdraw > 0) {
                        if (Rs2Bank.hasBankItem(itemId, amountToWithdraw)) {
                            log.debug("Withdrawing {} x {} (item ID: {})", amountToWithdraw, itemId, itemId);
                            Rs2Bank.withdrawX(itemId, amountToWithdraw);
                            sleepUntil(() -> Rs2Inventory.count(itemId) >= currentCount + amountToWithdraw, 3000);
                        } else {
                            log.warn("Required transport item {} not found in bank (need {} but bank has less)",
                                    itemId, amountToWithdraw);
                        }
                    } else {
                        log.debug("Already have enough of item {}: {} (need {})", itemId, currentCount, amountNeeded);
                    }
                }

                // Wait a bit for all withdrawals to complete
                sleep(600); // 1 tick
            }

            // Step 4: Close bank
            Rs2Bank.closeBank();
            sleepUntil(() -> !Rs2Bank.isOpen(), 3000);
            if (Rs2Bank.isOpen()) {
                log.warn("Failed to close bank after withdrawals");
                return WalkerState.EXIT;
            }
            ShortestPathPlugin.getPathfinderConfig().setUseBankItems(false);
            ShortestPathPlugin.getPathfinderConfig().refresh(finalTarget);
            // Step 5: Continue to final target
            log.debug("Banking complete, continuing to final target: " + finalTarget);
            setTarget(null); // Clear current target to avoid conflicts
            return walkWithStateInternal(finalTarget, distance);

        } catch (Exception e) {
            log.error("Error in banking workflow: " + e.getMessage(), e);
            return WalkerState.EXIT;
        }
    }

    public static boolean closeWorldMap() {
        if (!Rs2Widget.isWidgetVisible(InterfaceID.Worldmap.CLOSE)) return false;
        Widget closeButton = Rs2Widget.getWidget(InterfaceID.Worldmap.CLOSE);
        if (closeButton != null) {
            Rectangle closeButtonBounds = closeButton.getBounds();
            NewMenuEntry closeEntry = new NewMenuEntry()
                    .option("Close")
                    .target("")
                    .identifier(1)
                    .type(MenuAction.CC_OP)
                    .param0(-1)
                    .param1(InterfaceID.Worldmap.CLOSE)
                    .forceLeftClick(false);

            Microbot.doInvoke(closeEntry, closeButtonBounds != null && Rs2UiHelper.isRectangleWithinCanvas(closeButtonBounds) ? closeButtonBounds : Rs2UiHelper.getDefaultRectangle());
        }
        return sleepUntil(() -> !Rs2Widget.isWidgetVisible(InterfaceID.Worldmap.CLOSE), 3000);
    }
}

