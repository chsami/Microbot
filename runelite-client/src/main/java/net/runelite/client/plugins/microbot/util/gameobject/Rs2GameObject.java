package net.runelite.client.plugins.microbot.util.gameobject;

import lombok.SneakyThrows;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldArea;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

import static net.runelite.api.NullObjectID.NULL_34810;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * TODO: This class should be cleaned up, less methods by passing filters instead of multiple parameters
 */
public class Rs2GameObject {
    public static boolean interact(WorldPoint worldPoint) {
        return interact(worldPoint, "");
    }

    public static boolean interact(WorldPoint worldPoint, String action) {
        TileObject gameObject = findObjectByLocation(worldPoint);
        return clickObject(gameObject, action);
    }

    public static boolean interact(GameObject gameObject) {
        return clickObject(gameObject);
    }

    public static boolean interact(TileObject tileObject) {
        return clickObject(tileObject, null);
    }

    public static boolean interact(TileObject tileObject, String action) {
        return clickObject(tileObject, action);
    }

    public static boolean interact(GameObject gameObject, String action) {
        return clickObject(gameObject, action);
    }

    public static boolean interact(int id) {
        TileObject object = findObjectById(id);
        return clickObject(object);
    }

    public static int interact(List<Integer> ids) {
        for (int objectId : ids) {
            if (interact(objectId)) return objectId;
        }
        return -1;
    }

    public static boolean interact(TileObject tileObject, String action, boolean checkCanReach) {
        if (tileObject == null) return false;
        if (!checkCanReach) return clickObject(tileObject, action);

        if (checkCanReach && Rs2GameObject.hasLineOfSight(tileObject))
            return clickObject(tileObject, action);

        Rs2Walker.walkFastCanvas(tileObject.getWorldLocation());

        return false;
    }


    public static boolean interact(TileObject tileObject, boolean checkCanReach) {
        return interact(tileObject, "", checkCanReach);
    }

    public static boolean interact(int id, boolean checkCanReach) {
        TileObject object = findObjectById(id);
        return interact(object, checkCanReach);
    }


    public static boolean interact(int id, String action) {
        TileObject object = findObjectById(id);
        return clickObject(object, action);
    }

    public static boolean interact(int id, String action, int distance) {
        TileObject object = findObjectByIdAndDistance(id, distance);
        return clickObject(object, action);
    }

    public static boolean interact(String name, String action) {
        TileObject object = get(name);
        return clickObject(object, action);
    }

    public static boolean interact(int[] objectIds, String action) {
        for (int objectId : objectIds) {
            if (interact(objectId, action)) return true;
        }
        return false;
    }

    public static boolean interact(String name) {
        GameObject object = get(name, true);
        return clickObject(object);
    }

    public static boolean interact(String name, boolean exact) {
        GameObject object = get(name, exact);
        return clickObject(object);
    }

    public static boolean interact(String name, String action, boolean exact) {
        GameObject object = get(name, exact);
        return clickObject(object, action);
    }

    @Deprecated(since = "Use findObjectById", forRemoval = true)
    public static ObjectComposition findObject(int id) {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient().getObjectDefinition(id)).orElse(null);
    }

    public static boolean exists(int id) {
        return findObjectById(id) != null;
    }

    public static TileObject findObjectByName(String name) {
        Scene scene = Microbot.getClient().getScene();
        Tile[][][] tiles = scene.getTiles();

        for (int z = 0; z < Constants.MAX_Z; z++) {
            for (int x = 0; x < Constants.SCENE_SIZE; x++) {
                for (int y = 0; y < Constants.SCENE_SIZE; y++) {
                    Tile tile = tiles[z][x][y];
                    if (tile == null) {
                        continue;
                    }

                    for (TileObject object : tile.getGameObjects()) {
                        if (object == null) {
                            continue;
                        }

                        ObjectComposition objComp = getObjectComposition(object);
                        if (objComp != null && objComp.getName().equalsIgnoreCase(name)) {
                            return object;
                        }
                    }

                    WallObject wallObject = tile.getWallObject();
                    if (wallObject != null) {
                        ObjectComposition objComp = getObjectComposition(wallObject);
                        if (objComp != null && objComp.getName().equalsIgnoreCase(name)) {
                            return wallObject;
                        }
                    }

                    DecorativeObject decorativeObject = tile.getDecorativeObject();
                    if (decorativeObject != null) {
                        ObjectComposition objComp = getObjectComposition(decorativeObject);
                        if (objComp != null && objComp.getName().equalsIgnoreCase(name)) {
                            return decorativeObject;
                        }
                    }

                    GroundObject groundObject = tile.getGroundObject();
                    if (groundObject != null) {
                        ObjectComposition objComp = getObjectComposition(groundObject);
                        if (objComp != null && objComp.getName().equalsIgnoreCase(name)) {
                            return groundObject;
                        }
                    }
                }
            }
        }

        return null;
    }

    private static ObjectComposition getObjectComposition(TileObject object) {
        int id = object.getId();
        return Microbot.getClient().getObjectDefinition(id);
    }

    public static TileObject findObjectById(int id) {

        List<GameObject> gameObjects = getGameObjects();

        if (gameObjects == null) return null;

        for (net.runelite.api.GameObject gameObject : gameObjects) {
            if (gameObject.getId() == id)
                return gameObject;
        }

        List<GroundObject> groundObjects = getGroundObjects();

        for (GroundObject groundObject : groundObjects) {
            if (groundObject.getId() == id)
                return groundObject;
        }

        List<WallObject> wallObjects = getWallObjects();


        for (WallObject wallObject : wallObjects) {
            if (wallObject.getId() == id)
                return wallObject;
        }

        List<DecorativeObject> decorationObjects = getDecorationObjects();


        for (DecorativeObject decorativeObject : decorationObjects) {
            if (decorativeObject.getId() == id)
                return decorativeObject;
        }

        return null;
    }

    public static TileObject findObjectByLocation(WorldPoint worldPoint) {

        List<GameObject> gameObjects = getGameObjects();

        if (gameObjects == null) return null;

        for (net.runelite.api.GameObject gameObject : gameObjects) {
            if (gameObject.getWorldLocation().equals(worldPoint))
                return gameObject;
        }

        List<GroundObject> groundObjects = getGroundObjects();

        for (GroundObject groundObject : groundObjects) {
            if (groundObject.getWorldLocation().equals(worldPoint))
                return groundObject;
        }

        List<WallObject> wallObjects = getWallObjects();


        for (WallObject wallObject : wallObjects) {
            if (wallObject.getWorldLocation().equals(worldPoint))
                return wallObject;
        }

        List<DecorativeObject> decorationObjects = getDecorationObjects();


        for (DecorativeObject decorativeObject : decorationObjects) {
            if (decorativeObject.getWorldLocation().equals(worldPoint))
                return decorativeObject;
        }

        return null;
    }

    public static TileObject findGameObjectByLocation(WorldPoint worldPoint) {

        List<GameObject> gameObjects = getGameObjects();

        if (gameObjects == null) return null;

        for (net.runelite.api.GameObject gameObject : gameObjects) {
            if (gameObject.getWorldLocation().equals(worldPoint))
                return gameObject;
        }

        return null;
    }

    /**
     * find ground object by location
     * @param worldPoint
     * @return groundobject
     */
    public static TileObject findGroundObjectByLocation(WorldPoint worldPoint) {

        List<GroundObject> groundObjects = getGroundObjects();

        if (groundObjects == null) return null;

        for (net.runelite.api.GroundObject groundObject : groundObjects) {
            if (groundObject.getWorldLocation().equals(worldPoint))
                return groundObject;
        }

        return null;
    }

    public static TileObject findObjectByIdAndDistance(int id, int distance) {

        List<GameObject> gameObjects = getGameObjectsWithinDistance(distance);

        for (net.runelite.api.GameObject gameObject : gameObjects) {
            if (gameObject.getId() == id)
                return gameObject;
        }

        List<GroundObject> groundObjects = getGroundObjects();

        groundObjects = groundObjects.stream().filter(x -> Microbot.getClient().getLocalPlayer().getWorldLocation().distanceTo(x.getWorldLocation()) < distance).collect(Collectors.toList());


        for (GroundObject groundObject : groundObjects) {
            if (groundObject.getId() == id)
                return groundObject;
        }

        List<WallObject> wallObjects = getWallObjects();

        wallObjects = wallObjects.stream().filter(x -> Microbot.getClient().getLocalPlayer().getWorldLocation().distanceTo(x.getWorldLocation()) < distance).collect(Collectors.toList());


        for (WallObject wallObject : wallObjects) {
            if (wallObject.getId() == id)
                return wallObject;
        }

        List<DecorativeObject> decorationObjects = getDecorationObjects();

        decorationObjects = decorationObjects.stream().filter(x -> Microbot.getClient().getLocalPlayer().getWorldLocation().distanceTo(x.getWorldLocation()) < distance).collect(Collectors.toList());

        for (DecorativeObject decorativeObject : decorationObjects) {
            if (decorativeObject.getId() == id)
                return decorativeObject;
        }

        return null;
    }

    public static List<DecorativeObject> getDecorationObjects() {
        Scene scene = Microbot.getClient().getScene();
        Tile[][][] tiles = scene.getTiles();

        if (tiles == null) return new ArrayList<>();

        int z = Microbot.getClient().getPlane();
        List<DecorativeObject> tileObjects = new ArrayList<>();
        for (int x = 0; x < Constants.SCENE_SIZE; ++x) {
            for (int y = 0; y < Constants.SCENE_SIZE; ++y) {
                Tile tile = tiles[z][x][y];

                if (tile == null) {
                    continue;
                }

                tileObjects.add(tile.getDecorativeObject());
            }
        }


        return Arrays.stream(tileObjects.toArray(new DecorativeObject[0]))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(value -> value.getLocalLocation().distanceTo(Microbot.getClient().getLocalPlayer().getLocalLocation())))
                .collect(Collectors.toList());
    }

    public static GameObject findObjectById(int id, int x) {

        List<GameObject> gameObjects = getGameObjects();

        if (gameObjects == null) return null;

        for (net.runelite.api.GameObject gameObject : gameObjects) {
            if (gameObject.getId() == id && gameObject.getWorldLocation().getX() == x)
                return gameObject;
        }

        return null;
    }

    public static GameObject findObject(int id, WorldPoint worldPoint) {

        List<GameObject> gameObjects = getGameObjects();

        if (gameObjects == null) return null;

        for (net.runelite.api.GameObject gameObject : gameObjects) {
            if (gameObject.getId() == id && gameObject.getWorldLocation().equals(worldPoint))
                return gameObject;
        }

        return null;
    }

    public static ObjectComposition findObjectComposition(int id) {

        List<GameObject> gameObjects = getGameObjects();

        if (gameObjects == null) return null;

        for (net.runelite.api.GameObject gameObject : gameObjects) {
            if (gameObject.getId() == id) {
                return convertGameObjectToObjectComposition(gameObject);
            }
        }
        return null;
    }

    public static GameObject get(String name) {
        return get(name, false);
    }

    public static GameObject get(String name, boolean exact) {
        name = name.toLowerCase();
        // add underscore because the OBJECTID static list contains _ instead of spaces
        List<Integer> ids = getObjectIdsByName(name.replace(" ", "_"));
        List<GameObject> gameObjects = getGameObjects();

        if (gameObjects == null) {
            return null;
        }

        GameObject gameObject = gameObjects.stream()
                .filter(x -> ids.stream().anyMatch(id -> id == x.getId()))
                .min(Comparator.comparingInt(tile -> tile.getWorldLocation().distanceTo(Rs2Player.getWorldLocation())))
                .orElse(null);

        if (gameObject == null) return null;

        ObjectComposition objComp = convertGameObjectToObjectComposition(gameObject.getId());

        if (objComp == null) {
            return null;
        }
        String compName = null;

        try {
            compName = !objComp.getName().equals("null") ? objComp.getName() : (objComp.getImpostor() != null ? objComp.getImpostor().getName() : null);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        if (compName != null) {
            if (!exact && compName.toLowerCase().contains(name)) {
                return gameObject;
            } else if (exact && compName.equalsIgnoreCase(name)) {
                return gameObject;
            }
        }

        return null;
    }

    public static GameObject findObject(String objectName, boolean exact, int distance, boolean checkLineOfSight, WorldPoint anchorPoint) {
        List<GameObject> gameObjects = getGameObjectsWithinDistance(distance, anchorPoint);

        if (gameObjects == null) {
            return null;
        }

        for (GameObject gameObject : gameObjects) {
            if (!Rs2Tile.areSurroundingTilesWalkable(gameObject.getWorldLocation(), gameObject.sizeX(), gameObject.sizeY()))
                continue;

            if (checkLineOfSight && !hasLineOfSight(gameObject))
                continue;

            ObjectComposition objComp = convertGameObjectToObjectComposition(gameObject);

            if (objComp == null) {
                continue;
            }
            String compName;

            try {
                compName = objComp.getName() != null && !objComp.getName().equals("null") ? objComp.getName() : (objComp.getImpostor() != null ? objComp.getImpostor().getName() : null);
            } catch (Exception e) {
                continue;
            }

            if (compName != null) {
                if (!exact && compName.toLowerCase().contains(objectName.toLowerCase())) {
                    return gameObject;
                } else if (exact && compName.equalsIgnoreCase(objectName)) {
                    return gameObject;
                }
            }
        }

        return null;
    }

    /**
 * Finds a reachable game object by name within a specified distance from an anchor point, optionally checking for a specific action.
 *
 * @param objectName The name of the game object to find.
 * @param exact Whether to match the name exactly or partially.
 * @param distance The maximum distance from the anchor point to search for the game object.
 * @param anchorPoint The point from which to measure the distance.
 * @param checkAction Whether to check for a specific action on the game object.
 * @param action The action to check for if checkAction is true.
 * @return The nearest reachable game object that matches the criteria, or null if none is found.
 */
public static GameObject findReachableObject(String objectName, boolean exact, int distance, WorldPoint anchorPoint, boolean checkAction, String action) {
    List<GameObject> gameObjects = getGameObjectsWithinDistance(distance, anchorPoint);
    if (gameObjects == null) {
        return null;
    }

    return gameObjects.stream()
            .filter(Rs2GameObject::isReachable)
            .filter(gameObject -> {
                try {
                    ObjectComposition objComp = convertGameObjectToObjectComposition(gameObject);
                    if (objComp == null) return false;

                    String compName = objComp.getName();
                    if (compName == null || "null".equals(compName)) {
                        if (objComp.getImpostor() != null) {
                            compName = objComp.getImpostor().getName();
                        } else {
                            return false;
                        }
                    }

                    if (compName == null) return false;

                    if (checkAction) {
                        if (!hasAction(objComp, action)) return false;
                    }

                    if (exact) {
                        return compName.equalsIgnoreCase(objectName);
                    } else {
                        return compName.toLowerCase().contains(objectName.toLowerCase());
                    }

                } catch (Exception e) {
                    return false;
                }
            }).min(Comparator.comparingInt(o -> Rs2Player.getRs2WorldPoint().distanceToPath(o.getWorldLocation())))
            .orElse(null);
}

/**
 * Finds a reachable game object by name within a specified distance from an anchor point.
 *
 * @param objectName The name of the game object to find.
 * @param exact Whether to match the name exactly or partially.
 * @param distance The maximum distance from the anchor point to search for the game object.
 * @param anchorPoint The point from which to measure the distance.
 * @return The nearest reachable game object that matches the criteria, or null if none is found.
 */
public static GameObject findReachableObject(String objectName, boolean exact, int distance, WorldPoint anchorPoint) {
    List<GameObject> gameObjects = getGameObjectsWithinDistance(distance, anchorPoint);
    if (gameObjects == null) {
        return null;
    }

    return gameObjects.stream()
            .filter(Rs2GameObject::isReachable)
            .sorted(Comparator.comparingInt(o -> Rs2Player.getRs2WorldPoint().distanceToPath(o.getWorldLocation())))
            .filter(gameObject -> {
                try {
                    ObjectComposition objComp = convertGameObjectToObjectComposition(gameObject);
                    if (objComp == null) return false;

                    String compName = objComp.getName();
                    if (compName == null || "null".equals(compName)) {
                        if (objComp.getImpostor() != null) {
                            compName = objComp.getImpostor().getName();
                        } else {
                            return false;
                        }
                    }

                    if (compName == null) return false;

                    if (exact) {
                        return compName.equalsIgnoreCase(objectName);
                    } else {
                        return compName.toLowerCase().contains(objectName.toLowerCase());
                    }
                } catch (Exception e) {
                    return false;
                }
            })
            .findFirst()
            .orElse(null);
}


    public static boolean hasAction(ObjectComposition objComp, String action) {
        boolean result;

        if (objComp == null) return false;

        result = Arrays.stream(objComp.getActions()).anyMatch(x -> x != null && x.equalsIgnoreCase(action.toLowerCase()));
        if (!result) {
            try {
                result = Arrays.stream(objComp.getImpostor().getActions()).anyMatch(x -> x != null && x.equalsIgnoreCase(action.toLowerCase()));
            } catch (Exception ex) {
                //do nothing
            }
        }
        return result;
    }

    /**
     * Imposter objects are objects that have their menu action changed but still remain the same object.
     * for example: farming patches
     */
    public static GameObject findObjectByImposter(int id, String action) {
        return findObjectByImposter(id, action, true);
    }

    public static GameObject findObjectByImposter(int id, String optionName, boolean exact) {
        List<GameObject> gameObjects = getGameObjects();

        if (gameObjects == null) return null;

        for (net.runelite.api.GameObject gameObject : gameObjects) {

            if (gameObject.getId() != id) continue;

            ObjectComposition objComp = convertGameObjectToObjectComposition(gameObject);

            if (objComp == null) continue;

            try {
                if (objComp.getImpostor() == null) continue;
                if (exact) {
                    if (Arrays.stream(objComp.getImpostor().getActions()).filter(Objects::nonNull)
                            .anyMatch((action) -> action.equalsIgnoreCase(optionName))) {
                        return gameObject;
                    }
                } else {
                    if (Arrays.stream(objComp.getImpostor().getActions()).filter(Objects::nonNull)
                            .anyMatch((action) -> action.toLowerCase().contains(optionName.toLowerCase()))) {
                        return gameObject;
                    }
                }
            } catch (Exception ex) {
                // do nothing
            }
        }

        return null;
    }


    public static GameObject findBank() {
        List<GameObject> gameObjects = getGameObjects();

        List<Integer> possibleBankIds = Arrays.stream(Rs2BankID.bankIds).collect(Collectors.toList());

        possibleBankIds.add(NULL_34810);

        for (GameObject gameObject : gameObjects) {
            if (possibleBankIds.stream().noneMatch(x -> x == gameObject.getId())) continue;

            //cooks guild (exception)
            if (gameObject.getWorldLocation().equals(new WorldPoint(3147, 3449, 0)) || gameObject.getWorldLocation().equals(new WorldPoint(3148, 3449, 0))) {
                if (!BankLocation.COOKS_GUILD.hasRequirements()) continue;
            }
            //farming guild (exception)
            //At the farming guild there’s 2 banks, one in the southern half of the guild and one northern part of the guild which requires a certain higher farming level to enter
            if (gameObject.getWorldLocation().equals(new WorldPoint(1248, 3759, 0)) || gameObject.getWorldLocation().equals(new WorldPoint(1249, 3759, 0))) {
                if (!Rs2Player.getSkillRequirement(Skill.FARMING, 85, true)) continue;
            }

            ObjectComposition objectComposition = convertGameObjectToObjectComposition(gameObject);

            if (objectComposition == null) continue;

            if (Arrays.stream(objectComposition.getActions())
                    .noneMatch(action ->
                            action != null && (
                                    action.toLowerCase().contains("bank") ||
                                            action.toLowerCase().contains("collect"))))
                continue;

            return gameObject;
        }

        return null;
    }

    public static GameObject findChest() {
        List<GameObject> gameObjects = getGameObjects();

        List<Integer> possibleBankIds = Arrays.stream(Rs2BankID.bankIds).collect(Collectors.toList());

        possibleBankIds.add(12308); // RFD chest lumbridge basement
        possibleBankIds.add(31427); // Fossil island chest

        for (GameObject gameObject : gameObjects) {
            if (possibleBankIds.stream().noneMatch(x -> x == gameObject.getId())) continue;

            ObjectComposition objectComposition = convertGameObjectToObjectComposition(gameObject);

            if (objectComposition == null) continue;

            if (objectComposition.getImpostorIds() != null && objectComposition.getImpostorIds().length > 0) {
                if (Arrays.stream(objectComposition.getImpostor().getActions())
                        .anyMatch(action -> action != null && (
                                action.toLowerCase().contains("bank") ||
                                        action.toLowerCase().contains("collect"))))
                    return gameObject;
            }

            if (Arrays.stream(objectComposition.getActions())
                    .anyMatch(action -> action != null && (
                            action.toLowerCase().contains("bank") ||
                                    action.toLowerCase().contains("collect"))))
                return gameObject;

        }

        return null;
    }

    /**
     * Find nearest Deposit box
     *
     * @return GameObject
     */
    public static GameObject findDepositBox() {
        List<GameObject> gameObjects = getGameObjects();

        List<Integer> possibleBankIds = Arrays.stream(Rs2BankID.bankIds).collect(Collectors.toList());
//        possibleBankIds.add(ObjectID.BANK_DEPOSIT_BOX);
//        possibleBankIds.add(ObjectID.BANK_DEPOSIT_CHEST);


        for (GameObject gameObject : gameObjects) {
            if (possibleBankIds.stream().noneMatch(x -> x == gameObject.getId())) continue;

            ObjectComposition objectComposition = convertGameObjectToObjectComposition(gameObject);

            if (objectComposition == null) continue;

            if (objectComposition.getImpostorIds() != null && objectComposition.getImpostorIds().length > 0) {
                if (Arrays.stream(objectComposition.getImpostor().getActions())
                        .anyMatch(action -> action != null && (
                                action.toLowerCase().contains("deposit"))))
                    return gameObject;
            }

            if (Arrays.stream(objectComposition.getActions())
                    .anyMatch(action -> action != null && (
                            action.toLowerCase().contains("deposit"))))
                return gameObject;

        }

        return null;
    }

    @Deprecated(since="1.5.7 - use signature with Integer[] ids", forRemoval = true)
    public static TileObject findObject(List<Integer> ids) {
        for (int id : ids) {
            TileObject object = findObjectById(id);
            if (object == null) continue;
            if (Rs2Player.getWorldLocation().getPlane() != object.getPlane()) continue;
            if (object instanceof GroundObject && !Rs2Walker.canReach(object.getWorldLocation()))
                continue;

            //exceptions if the pathsize needs to be bigger
            if (object.getId() == ObjectID.MARKET_STALL_14936) {
                if (object instanceof GameObject && !Rs2Walker.canReach(object.getWorldLocation(), ((GameObject) object).sizeX(), ((GameObject) object).sizeY(), 4, 4))
                    continue;
            } else if (object.getId() == ObjectID.BEAM_42220) {
                if (object.getWorldLocation().distanceTo(Rs2Player.getWorldLocation()) > 6)
                    continue;
            } else {
                if (object instanceof GameObject && !Rs2Walker.canReach(object.getWorldLocation(), ((GameObject) object).sizeX(), ((GameObject) object).sizeY()))
                    continue;
            }

            return object;
        }
        return null;
    }

    /**
     * Finds the closest matching object id
     * The reason we take the closest matching is to avoid interacting with an object that is to far away
     * @param ids
     * @return
     */
    public static TileObject findObject(Integer[] ids) {
        List<GameObject> gameObjects = getGameObjects();
        if (gameObjects == null) return null;

        TileObject closestObject = null;
        double closestDistance = Double.MAX_VALUE;

        for (net.runelite.api.GameObject gameObject : gameObjects) {
            for (int id : ids) {
                if (gameObject.getId() == id) {
                    double distance = gameObject.getWorldLocation().distanceTo(Rs2Player.getWorldLocation());
                    if (distance < closestDistance) {
                        closestDistance = distance;
                        closestObject = gameObject;
                    }
                }
            }
        }
        return closestObject;
    }

    public static ObjectComposition convertGameObjectToObjectComposition(TileObject tileObject) {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient().getObjectDefinition(tileObject.getId()))
                .orElse(null);
    }

    public static ObjectComposition convertGameObjectToObjectComposition(int objectId) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getObjectDefinition(objectId))
                .orElse(null);
    }

    public static WallObject findDoor(int id) {
        Scene scene = Microbot.getClient().getScene();
        Tile[][][] tiles = scene.getTiles();

        int z = Microbot.getClient().getPlane();
        for (int x = 0; x < Constants.SCENE_SIZE; ++x) {
            for (int y = 0; y < Constants.SCENE_SIZE; ++y) {
                Tile tile = tiles[z][x][y];

                if (tile == null) {
                    continue;
                }
                WallObject wall = tile.getWallObject();
                if (wall != null && wall.getId() == id)
                    return wall;
            }
        }
        return null;
    }

    public static List<Tile> getTiles(int maxTileDistance) {
        int maxDistance = Math.max(2400, maxTileDistance * 128);

        Player player = Microbot.getClient().getLocalPlayer();
        Scene scene = Microbot.getClient().getScene();
        Tile[][][] tiles = scene.getTiles();

        int z = Microbot.getClient().getPlane();
        List<Tile> tileObjects = new ArrayList<>();
        for (int x = 0; x < Constants.SCENE_SIZE; ++x) {
            for (int y = 0; y < Constants.SCENE_SIZE; ++y) {
                Tile tile = tiles[z][x][y];

                if (tile == null) {
                    continue;
                }

                if (player.getLocalLocation().distanceTo(tile.getLocalLocation()) <= maxDistance) {
                    tileObjects.add(tile);
                }

            }
        }

        return tileObjects;
    }

    public static List<Tile> getTiles() {
        return getTiles(2400);
    }

    public static GameObject getGameObject(WorldPoint worldPoint) {
        LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient(), worldPoint);
        Scene scene = Microbot.getClient().getScene();
        Tile[][][] tiles = scene.getTiles();

        int z = Microbot.getClient().getPlane();
        Tile tile = null;
        if (localPoint != null) {
            tile = tiles[z][localPoint.getSceneX()][localPoint.getSceneY()];
        }

        if (tile != null) {
            return Arrays.stream(tile.getGameObjects()).filter(Objects::nonNull).findFirst().orElse(null);
        }
        return null;
    }

    public static GameObject getGameObject(LocalPoint localPoint) {
        Scene scene = Microbot.getClient().getScene();
        Tile[][][] tiles = scene.getTiles();

        int z = Microbot.getClient().getPlane();
        Tile tile = tiles[z][localPoint.getSceneX()][localPoint.getSceneY()];

        return Arrays.stream(tile.getGameObjects()).filter(Objects::nonNull).findFirst().orElse(null);
    }

    public static List<GroundObject> getGroundObjects(int id, WorldPoint anchorPoint) {
        Scene scene = Microbot.getClient().getScene();
        Tile[][][] tiles = scene.getTiles();

        if (tiles == null) return new ArrayList<>();

        int z = Microbot.getClient().getPlane();
        List<GroundObject> tileObjects = new ArrayList<>();
        for (int x = 0; x < Constants.SCENE_SIZE; ++x) {
            for (int y = 0; y < Constants.SCENE_SIZE; ++y) {
                Tile tile = tiles[z][x][y];

                if (tile == null || tile.getGroundObject() == null) {
                    continue;
                }
                if (tile.getGroundObject().getId() == id) {
                    tileObjects.add(tile.getGroundObject());
                }
            }
        }

        return tileObjects.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(tile -> tile.getWorldLocation().distanceTo(anchorPoint)))
                .collect(Collectors.toList());
    }

    /**
     * TODO remove this method, maybe use find or get(int id)
     *
     * @param id
     * @return
     */
    public static List<GameObject> getGameObjects(int id) {
        return getGameObjects(id, Rs2Player.getWorldLocation());
    }

    public static List<GameObject> getGameObjects(int id, WorldPoint anchorPoint) {
        Scene scene = Microbot.getClient().getScene();
        Tile[][][] tiles = scene.getTiles();

        if (tiles == null) return new ArrayList<>();

        int z = Microbot.getClient().getPlane();
        List<GameObject> tileObjects = new ArrayList<>();
        for (int x = 0; x < Constants.SCENE_SIZE; ++x) {
            for (int y = 0; y < Constants.SCENE_SIZE; ++y) {
                Tile tile = tiles[z][x][y];

                if (tile == null) {
                    continue;
                }
                for (GameObject tileObject : tile.getGameObjects()) {
                    if (tileObject != null
                            && tileObject.getSceneMinLocation().equals(tile.getSceneLocation()) && tileObject.getId() == id)
                        tileObjects.add(tileObject);
                }
            }
        }

        return tileObjects.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(tile -> tile.getWorldLocation().distanceTo(anchorPoint)))
                .collect(Collectors.toList());
    }

    public static TileObject getTileObject(int id) {
        return getTileObjects(id, Rs2Player.getWorldLocation()).stream().findFirst().orElse(null);
    }

    public static List<TileObject> getTileObjects(int id) {
        return getTileObjects(id, Rs2Player.getWorldLocation());
    }

    public static List<TileObject> getTileObjects(int id, WorldPoint anchorPoint) {
        return getTileObjects().stream()
                .filter(x -> Objects.nonNull(x) && x.getId() == id)
                .sorted(Comparator.comparingInt(tile -> tile.getWorldLocation().distanceTo(anchorPoint)))
                .collect(Collectors.toList());
    }

    public static List<TileObject> getTileObjects() {
        Scene scene = Microbot.getClient().getScene();
        Tile[][][] tiles = scene.getTiles();

        if (tiles == null) return new ArrayList<>();

        int z = Microbot.getClient().getPlane();
        List<TileObject> tileObjects = new ArrayList<>();
        for (int x = 0; x < Constants.SCENE_SIZE; ++x) {
            for (int y = 0; y < Constants.SCENE_SIZE; ++y) {
                Tile tile = tiles[z][x][y];

                if (tile == null) {
                    continue;
                }

                if (tile.getDecorativeObject() != null
                        && tile.getDecorativeObject().getWorldLocation().equals(tile.getWorldLocation()))
                    tileObjects.add(tile.getDecorativeObject());

                if (tile.getGroundObject() != null
                        && tile.getGroundObject().getWorldLocation().equals(tile.getWorldLocation()))
                    tileObjects.add(tile.getGroundObject());

                if (tile.getWallObject() != null
                        && tile.getWallObject().getWorldLocation().equals(tile.getWorldLocation()))
                    tileObjects.add(tile.getWallObject());
            }
        }

        return tileObjects.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public static List<GameObject> getGameObjects() {
        Scene scene = Microbot.getClient().getTopLevelWorldView().getScene();
        Tile[][][] tiles = scene.getTiles();

        int z = Microbot.getClient().getPlane();
        List<GameObject> tileObjects = new ArrayList<>();
        for (int x = 0; x < Constants.SCENE_SIZE; ++x) {
            for (int y = 0; y < Constants.SCENE_SIZE; ++y) {
                Tile tile = tiles[z][x][y];

                if (tile == null) {
                    continue;
                }
                for (GameObject tileObject : tile.getGameObjects()) {
                    if (tileObject != null
                            && tileObject.getSceneMinLocation().equals(tile.getSceneLocation()))
                        tileObjects.add(tileObject);
                }
            }
        }

        return tileObjects.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(tile -> tile.getWorldLocation().distanceTo(Microbot.getClient().getLocalPlayer().getWorldLocation())))
                .collect(Collectors.toList());
    }

    public static List<GameObject> getGameObjectsWithinDistance(int distance) {
        return getGameObjectsWithinDistance(distance, Rs2Player.getWorldLocation());
    }

    public static List<GameObject> getGameObjectsWithinDistance(int distance, WorldPoint anchorPoint) {
        Scene scene = Microbot.getClient().getScene();
        Tile[][][] tiles = scene.getTiles();

        int z = Microbot.getClient().getPlane();
        List<GameObject> tileObjects = new ArrayList<>();

        for (int x = 0; x < Constants.SCENE_SIZE; ++x) {
            for (int y = 0; y < Constants.SCENE_SIZE; ++y) {
                Tile tile = tiles[z][x][y];

                if (tile == null) {
                    continue;
                }

                for (GameObject tileObject : tile.getGameObjects()) {
                    if (tileObject != null
                            && tileObject.getSceneMinLocation().equals(tile.getSceneLocation())) {

                        int distanceToAnchor = anchorPoint.distanceTo(tileObject.getWorldLocation());

                        if (distance == 0) {
                            // Check in a cross pattern if distance is 0
                            WorldPoint objectLocation = tileObject.getWorldLocation();
                            if ((Math.abs(anchorPoint.getX() - objectLocation.getX()) == 1 && anchorPoint.getY() == objectLocation.getY())
                                    || (Math.abs(anchorPoint.getY() - objectLocation.getY()) == 1 && anchorPoint.getX() == objectLocation.getX())) {
                                tileObjects.add(tileObject);
                            }
                        } else {
                            // Default behavior for distances greater than 0
                            if (distanceToAnchor <= distance) {
                                tileObjects.add(tileObject);
                            }
                        }
                    }
                }
            }
        }

        return tileObjects.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(tile -> tile.getWorldLocation().distanceTo(Microbot.getClient().getLocalPlayer().getWorldLocation())))
                .collect(Collectors.toList());
    }

    public static List<TileObject> getAll() {
        List<TileObject> tileObjects = new ArrayList<>();

        tileObjects.addAll(getGameObjects());
        tileObjects.addAll(getGroundObjects());
        tileObjects.addAll(getWallObjects());

        return tileObjects;
    }


    public static List<GroundObject> getGroundObjects() {
        return getGroundObjects(Constants.SCENE_SIZE);
    }

    public static List<GroundObject> getGroundObjects(int distance) {
        Scene scene = Microbot.getClient().getScene();
        Tile[][][] tiles = scene.getTiles();

        if (tiles == null) return new ArrayList<>();

        int z = Microbot.getClient().getPlane();
        List<GroundObject> tileObjects = new ArrayList<>();
        for (int x = 0; x < Constants.SCENE_SIZE; ++x) {
            for (int y = 0; y < Constants.SCENE_SIZE; ++y) {
                Tile tile = tiles[z][x][y];

                if (tile == null) {
                    continue;
                }

                if (tile.getWorldLocation().distanceTo(Microbot.getClient().getLocalPlayer().getWorldLocation()) > distance)
                    continue;


                tileObjects.add(tile.getGroundObject());
            }
        }

        return tileObjects.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(tile -> tile.getWorldLocation().distanceTo(Microbot.getClient().getLocalPlayer().getWorldLocation())))
                .collect(Collectors.toList());
    }

    public static List<WallObject> getWallObjects() {
        return getWallObjects(Constants.SCENE_SIZE);
    }

    public static List<WallObject> getWallObjects(int distance) {
        Scene scene = Microbot.getClient().getScene();
        Tile[][][] tiles = scene.getTiles();

        if (tiles == null) return new ArrayList<>();

        int z = Microbot.getClient().getPlane();
        List<WallObject> tileObjects = new ArrayList<>();
        for (int x = 0; x < distance; ++x) {
            for (int y = 0; y < distance; ++y) {
                Tile tile = tiles[z][x][y];

                if (tile == null) {
                    continue;
                }

                tileObjects.add(tile.getWallObject());
            }
        }


        return tileObjects.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(tile -> tile.getWorldLocation().distanceTo(Microbot.getClient().getLocalPlayer().getWorldLocation())))
                .collect(Collectors.toList());
    }

    public static List<WallObject> getWallObjects(int id, WorldPoint anchorPoint) {
        Scene scene = Microbot.getClient().getScene();
        Tile[][][] tiles = scene.getTiles();

        if (tiles == null) return new ArrayList<>();

        int z = Microbot.getClient().getPlane();
        List<WallObject> tileObjects = new ArrayList<>();
        for (int x = 0; x < Constants.SCENE_SIZE; ++x) {
            for (int y = 0; y < Constants.SCENE_SIZE; ++y) {
                Tile tile = tiles[z][x][y];

                if (tile == null) {
                    continue;
                }
                if (tile.getWallObject() != null
                        && tile.getWallObject().getId() == id)
                    tileObjects.add(tile.getWallObject());
            }
        }

        return tileObjects.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(tile -> tile.getWorldLocation().distanceTo(anchorPoint)))
                .collect(Collectors.toList());
    }

    // private methods
    private static boolean clickObject(TileObject object) {
        return clickObject(object, "");
    }

    private static boolean clickObject(TileObject object, String action) {
        if (object == null) return false;
        if (Microbot.getClient().getLocalPlayer().getWorldLocation().distanceTo(object.getWorldLocation()) > 51) {
            Microbot.log("Object with id " + object.getId() + " is not close enough to interact with. Walking to the object....");
            Rs2Walker.walkTo(object.getWorldLocation());
            return false;
        }

        try {

            int param0;
            int param1;
            MenuAction menuAction = MenuAction.WALK;

            ObjectComposition objComp = convertGameObjectToObjectComposition(object);
            if (objComp == null) return false;

            Microbot.status = action + " " + objComp.getName();

            if (object instanceof GameObject) {
                GameObject obj = (GameObject) object;
                if (obj.sizeX() > 1) {
                    param0 = obj.getLocalLocation().getSceneX() - obj.sizeX() / 2;
                } else {
                    param0 = obj.getLocalLocation().getSceneX();
                }

                if (obj.sizeY() > 1) {
                    param1 = obj.getLocalLocation().getSceneY() - obj.sizeY() / 2;
                } else {
                    param1 = obj.getLocalLocation().getSceneY();
                }
            } else {
                // Default objects like walls, groundobjects, decorationobjects etc...
                param0 = object.getLocalLocation().getSceneX();
                param1 = object.getLocalLocation().getSceneY();
            }

            int index = 0;
            if (action != null) {
                String[] actions;
                if (objComp.getImpostorIds() != null && objComp.getImpostor() != null) {
                    actions = objComp.getImpostor().getActions();
                } else {
                    actions = objComp.getActions();
                }

                for (int i = 0; i < actions.length; i++) {
                    if (actions[i] == null) continue;
                    if (action.equalsIgnoreCase(Rs2UiHelper.stripColTags(actions[i]))) {
                        index = i;
                        break;
                    }
                }

                if (index == actions.length)
                    index = 0;
            }

            if (index == -1) {
                Microbot.log("Failed to interact with object " + object.getId() + " " + action);
            }


            if (Microbot.getClient().isWidgetSelected()) {
                menuAction = MenuAction.WIDGET_TARGET_ON_GAME_OBJECT;
            } else if (index == 0) {
                menuAction = MenuAction.GAME_OBJECT_FIRST_OPTION;
            } else if (index == 1) {
                menuAction = MenuAction.GAME_OBJECT_SECOND_OPTION;
            } else if (index == 2) {
                menuAction = MenuAction.GAME_OBJECT_THIRD_OPTION;
            } else if (index == 3) {
                menuAction = MenuAction.GAME_OBJECT_FOURTH_OPTION;
            } else if (index == 4) {
                menuAction = MenuAction.GAME_OBJECT_FIFTH_OPTION;
            }

            if (!Rs2Camera.isTileOnScreen(object.getLocalLocation())) {
                Rs2Camera.turnTo(object);
            }

            // both hands must be free before using MINECART
            if (objComp.getName().toLowerCase().contains("train cart")) {
                Rs2Equipment.unEquip(EquipmentInventorySlot.WEAPON);
                Rs2Equipment.unEquip(EquipmentInventorySlot.SHIELD);
                sleepUntil(() -> Rs2Equipment.get(EquipmentInventorySlot.WEAPON) == null && Rs2Equipment.get(EquipmentInventorySlot.SHIELD) == null);
            }


            Microbot.doInvoke(new NewMenuEntry(param0, param1, menuAction.getId(), object.getId(), -1, action, objComp.getName(), object), Rs2UiHelper.getObjectClickbox(object));
// MenuEntryImpl(getOption=Use, getTarget=Barrier, getIdentifier=43700, getType=GAME_OBJECT_THIRD_OPTION, getParam0=53, getParam1=51, getItemId=-1, isForceLeftClick=true, getWorldViewId=-1, isDeprioritized=false)
            //Rs2Reflection.invokeMenu(param0, param1, menuAction.getId(), object.getId(),-1, "", "", -1, -1);

        } catch (Exception ex) {
            Microbot.log("Failed to interact with object " + ex.getMessage());
        }

        return true;
    }

    public static boolean hasLineOfSight(TileObject tileObject) {
        return hasLineOfSight(Rs2Player.getWorldLocation(), tileObject);
    }

    public static boolean hasLineOfSight(WorldPoint point, TileObject tileObject) {
        if (tileObject == null) return false;
        if (tileObject instanceof GameObject) {
            GameObject gameObject = (GameObject) tileObject;
            WorldPoint worldPoint = WorldPoint.fromScene(Microbot.getClient(), gameObject.getSceneMinLocation().getX(), gameObject.getSceneMinLocation().getY(), gameObject.getPlane());
            return new WorldArea(
                    worldPoint,
                    gameObject.sizeX(),
                    gameObject.sizeY())
                    .hasLineOfSightTo(Microbot.getClient().getTopLevelWorldView(), point.toWorldArea());
        } else {
            return new WorldArea(
                    tileObject.getWorldLocation(),
                    2,
                    2)
                    .hasLineOfSightTo(Microbot.getClient().getTopLevelWorldView(), new WorldArea(point.getX(),
                            point.getY(), 2, 2, point.getPlane()));
        }
    }

    @SneakyThrows
    public static List<Integer> getObjectIdsByName(String name) {
        List<Integer> ids = new ArrayList<>();
        ObjectID objectID = new ObjectID();
        Class<?> objectIDClass = ObjectID.class;

        // Loop through all declared fields of the class
        for (Field field : objectIDClass.getDeclaredFields()) {

            // Get the name of the current field
            String fieldName = field.getName();

            // Check if the current field's name matches the desired property name
            if (fieldName.toLowerCase().contains(name)) {
                field.setAccessible(true);
                int propertyValue = (int) field.get(objectID);
                ids.add(propertyValue);
            }
        }
        return ids;
    }

    @Nullable
    public static ObjectComposition getObjectComposition(int id) {
        ObjectComposition objectComposition = Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getObjectDefinition(id))
                .orElse(null);
        if (objectComposition == null) return null;
        return objectComposition.getImpostorIds() == null ? objectComposition : objectComposition.getImpostor();
    }

    public static boolean canWalkTo(TileObject tileObject, int distance) {
        if (tileObject == null) return false;
        WorldArea objectArea;

        if (tileObject instanceof GameObject) {
            GameObject gameObject = (GameObject) tileObject;
            WorldPoint worldPoint = WorldPoint.fromScene(Microbot.getClient(), gameObject.getSceneMinLocation().getX(), gameObject.getSceneMinLocation().getY(), gameObject.getPlane());

            if (Microbot.getClient().isInInstancedRegion()) {
                var localPoint = LocalPoint.fromWorld(Microbot.getClient(), worldPoint);
                worldPoint = WorldPoint.fromLocalInstance(Microbot.getClient(), localPoint);
            }

            objectArea = new WorldArea(
                    worldPoint,
                    gameObject.sizeX(),
                    gameObject.sizeY());
        } else {
            objectArea = new WorldArea(
                    tileObject.getWorldLocation(),
                    2,
                    2);
        }

        var tiles = Rs2Tile.getReachableTilesFromTile(Rs2Player.getWorldLocation(), distance);
        for (var tile : tiles.keySet()) {
            if (tile.distanceTo(objectArea) < 2)
                return true;
        }

        return false;
    }

    /**
     * Returns the object is reachable from the player
     * @param tileObject
     * @return boolean
     */
    public static boolean isReachable(GameObject tileObject) {
        Rs2WorldArea gameObjectArea = new Rs2WorldArea(Objects.requireNonNull(getWorldArea(tileObject)));
        List<WorldPoint> interactablePoints = gameObjectArea.getInteractable();

        if (interactablePoints.isEmpty()) {
            interactablePoints.addAll(gameObjectArea.offset(1).toWorldPointList());
            interactablePoints.removeIf(gameObjectArea::contains);
        }

        WorldPoint walkableInteractPoint = interactablePoints.stream()
                .filter(Rs2Tile::isWalkable)
                .filter(Rs2Tile::isTileReachable)
                .findFirst()
                .orElse(null);
        return walkableInteractPoint != null;
    }

    public static WorldArea getWorldArea(GameObject gameObject)
    {
        if (!gameObject.getLocalLocation().isInScene())
        {
            return null;
        }

        LocalPoint localSWTile = new LocalPoint(
                gameObject.getLocalLocation().getX() - (gameObject.sizeX() - 1) * Perspective.LOCAL_TILE_SIZE / 2,
                gameObject.getLocalLocation().getY() - (gameObject.sizeY() - 1) * Perspective.LOCAL_TILE_SIZE / 2
        );

        LocalPoint localNETile = new LocalPoint(
                gameObject.getLocalLocation().getX() + (gameObject.sizeX() - 1) * Perspective.LOCAL_TILE_SIZE / 2,
                gameObject.getLocalLocation().getY() + (gameObject.sizeY() - 1) * Perspective.LOCAL_TILE_SIZE / 2
        );



        return new Rs2WorldArea(
                WorldPoint.fromLocal(Microbot.getClient(), localSWTile),
                WorldPoint.fromLocal(Microbot.getClient(), localNETile)
        );
    }

    /**
     * Hovers over the given game object using the natural mouse.
     *
     * @param object The game object to hover over.
     * @return True if successfully hovered, otherwise false.
     */
    public static boolean hoverOverObject(TileObject object) {
        if (!Rs2AntibanSettings.naturalMouse) {
            if(Rs2AntibanSettings.devDebug)
                Microbot.log("Natural mouse is not enabled, can't hover");
            return false;
        }
        Point point = Rs2UiHelper.getClickingPoint(Rs2UiHelper.getObjectClickbox(object), true);
        // if the point is 1,1 then the object is not on screen and we should return false
        if (point.getX() == 1 && point.getY() == 1) {
            return false;
        }
        Microbot.getNaturalMouse().moveTo(point.getX(), point.getY());
        return true;
    }
}
