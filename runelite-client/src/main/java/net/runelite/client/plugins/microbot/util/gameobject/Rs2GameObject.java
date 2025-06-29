package net.runelite.client.plugins.microbot.util.gameobject;

import lombok.SneakyThrows;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.coords.Rs2LocalPoint;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldArea;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * TODO: This class should be cleaned up, less methods by passing filters instead of multiple parameters
 */
public class Rs2GameObject {
    private static final Function<Tile, Collection<? extends GameObject>> GAMEOBJECT_EXTRACTOR = tile -> Arrays.asList(tile.getGameObjects());
    private static final Function<Tile, Collection<? extends GroundObject>> GROUNDOBJECT_EXTRACTOR = tile ->
            Collections.singletonList(tile.getGroundObject());
    private static final Function<Tile, Collection<? extends DecorativeObject>> DECORATIVEOBJECT_EXTRACTOR = tile ->
            Collections.singletonList(tile.getDecorativeObject());
    private static final Function<Tile, Collection<? extends WallObject>> WALLOBJECT_EXTRACTOR = tile ->
            Collections.singletonList(tile.getWallObject());
    private static final Function<Tile, Collection<? extends TileObject>> TILEOBJECT_EXTRACTOR = tile -> Arrays.asList(
            tile.getDecorativeObject(),
            tile.getGroundObject(),
            tile.getWallObject()
    );

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
        return clickObject(tileObject, "");
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

    public static boolean exists(int id) {
        return findObjectById(id) != null;
    }

	public static boolean canReach(WorldPoint target, int objectSizeX, int objectSizeY, int pathSizeX, int pathSizeY) {
		if (target == null) return false;

		List<WorldPoint> path = Rs2Player.getRs2WorldPoint().pathTo(target, true);
		if (path == null || path.isEmpty()) return false;

		WorldArea pathArea = new WorldArea(path.get(path.size() - 1), pathSizeX, pathSizeY);
		WorldArea objectArea = new WorldArea(target, objectSizeX + 2, objectSizeY + 2);

		return pathArea.intersectsWith2D(objectArea);
	}

	public static boolean canReach(WorldPoint target, int objectSizeX, int objectSizeY) {
		return canReach(target, objectSizeX, objectSizeY, 3, 3);
	}

	public static boolean canReach(WorldPoint target) {
		return canReach(target, 2, 2, 2, 2);
	}

	@Deprecated
    public static TileObject findObjectById(int id) {
        return getAll(o -> o.getId() == id).stream().findFirst().orElse(null);
    }

    @Deprecated
    public static TileObject findObjectByLocation(WorldPoint worldPoint) {
        return getAll(o -> o.getWorldLocation().equals(worldPoint)).stream().findFirst().orElse(null);
    }

    @Deprecated
    public static TileObject findGameObjectByLocation(WorldPoint worldPoint) {
        return getGameObject(o -> o.getWorldLocation().equals(worldPoint));
    }

    /**
     * find ground object by location
     *
     * @param worldPoint
     * @return groundobject
     */
    @Deprecated
    public static TileObject findGroundObjectByLocation(WorldPoint worldPoint) {
        return getGroundObject(worldPoint);
    }

    @Deprecated
    public static TileObject findObjectByIdAndDistance(int id, int distance) {
        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null) return null;
        LocalPoint anchor = player.getLocalLocation();
        return getAll(o -> o.getId() == id).stream().filter(withinTilesPredicate(Rs2LocalPoint.worldToLocalDistance(distance), anchor)).findFirst().orElse(null);
    }

    @Deprecated
    public static GameObject findObjectById(int id, int x) {
        return getGameObject(o -> o.getId() == id && o.getWorldLocation().getX() == x);
    }

    @Deprecated
    public static GameObject findObject(int id, WorldPoint worldPoint) {
        return getGameObject(o -> o.getId() == id && o.getWorldLocation().equals(worldPoint));
    }
    
    @Deprecated
    public static ObjectComposition findObjectComposition(int id) {
        return convertToObjectComposition(id);
    }

    @Deprecated
    public static GameObject get(String name) {
        return get(name, false);
    }

    @Deprecated
    public static GameObject get(String name, boolean exact) {
       return getGameObject(name, exact);
    }

    @Deprecated
    public static GameObject findObject(String objectName, boolean exact, int distance, boolean checkLineOfSight, WorldPoint anchorPoint) {
        return getGameObjects(nameMatches(objectName, exact), anchorPoint, distance).stream().filter(o -> !checkLineOfSight || Rs2GameObject.hasLineOfSight(o)).findFirst().orElse(null);
    }

    /**
     * Finds a reachable game object by name within a specified distance from an anchor point, optionally checking for a specific action.
     *
     * @param objectName  The name of the game object to find.
     * @param exact       Whether to match the name exactly or partially.
     * @param distance    The maximum distance from the anchor point to search for the game object.
     * @param anchorPoint The point from which to measure the distance.
     * @param checkAction Whether to check for a specific action on the game object.
     * @param action      The action to check for if checkAction is true.
     * @return The nearest reachable game object that matches the criteria, or null if none is found.
     */
    @Deprecated
    public static GameObject findReachableObject(String objectName, boolean exact, int distance, WorldPoint anchorPoint, boolean checkAction, String action) {
        Predicate<TileObject> namePred = nameMatches(objectName, exact);

        Predicate<GameObject> filter = o -> {
            if (!Rs2GameObject.isReachable(o)) {
                return false;
            }

            if (!namePred.test(o)) {
                return false;
            }

            if (checkAction) {
                ObjectComposition comp = convertToObjectComposition(o);
                return hasAction(comp, action);
            }

            return true;
        };

        return getGameObjects(filter, anchorPoint, distance)
                .stream()
                .min(Comparator.comparingInt(o ->
                        Rs2Player.getRs2WorldPoint()
                                .distanceToPath(o.getWorldLocation())))
                .orElse(null);
    }

    /**
     * Finds a reachable game object by name within a specified distance from an anchor point.
     *
     * @param objectName  The name of the game object to find.
     * @param exact       Whether to match the name exactly or partially.
     * @param distance    The maximum distance from the anchor point to search for the game object.
     * @param anchorPoint The point from which to measure the distance.
     * @return The nearest reachable game object that matches the criteria, or null if none is found.
     */
    @Deprecated
    public static GameObject findReachableObject(String objectName, boolean exact, int distance, WorldPoint anchorPoint) {
        return findReachableObject(objectName, exact, distance, anchorPoint, false, "");
    }

    public static boolean hasAction(ObjectComposition objComp, String action, boolean exact) {
        if (objComp == null) return false;

        return Arrays.stream(objComp.getActions())
                .filter(Objects::nonNull)
                .anyMatch(a -> exact ? a.equalsIgnoreCase(action) : a.toLowerCase().contains(action.toLowerCase()));
    }

    public static boolean hasAction(ObjectComposition objComp, String action) {
        return hasAction(objComp, action, true);
    }

    /**
     * Imposter objects are objects that have their menu action changed but still remain the same object.
     * for example: farming patches
     */
    @Deprecated
    public static GameObject findObjectByImposter(int id, String action) {
        return findObjectByImposter(id, action, true);
    }

    @Deprecated
    public static GameObject findObjectByImposter(int id, String optionName, boolean exact) {
        return getGameObjects(o -> o.getId() == id)
                .stream()
                .filter(o -> {
                    ObjectComposition comp = convertToObjectComposition(o);
                    return hasAction(comp, optionName, exact);
                })
                .findFirst()
                .orElse(null);
    }

    public static GameObject findBank(int maxSearchRadius) {
        Predicate<GameObject> bankableFilter = gameObject -> {
            WorldPoint loc = gameObject.getWorldLocation();

            //cooks guild (exception)
            if ((loc.equals(new WorldPoint(3147, 3449, 0)) || loc.equals(new WorldPoint(3148, 3449, 0))) && !BankLocation.COOKS_GUILD.hasRequirements()) {
                return false;
            }

            //farming guild (exception)
            //At the farming guild there’s 2 banks, one in the southern half of the guild and one northern part of the guild which requires a certain higher farming level to enter
            if ((loc.equals(new WorldPoint(1248, 3759, 0)) || loc.equals(new WorldPoint(1249, 3759, 0))) && !Rs2Player.getSkillRequirement(Skill.FARMING, 85, true)) {
                return false;
            }

			// Lunar Isle (exception)
			// There is a bank booth @ Lunar Isle that is only accessible when Dream Mentor is completed
			if (loc.equals(new WorldPoint(2099, 3920, 0)) && Rs2Player.getQuestState(Quest.DREAM_MENTOR) != QuestState.FINISHED) {
				return false;
			}

			// Lunar Isle (additional exception to not use these banks if no seal of passage)
			if ((loc.equals(new WorldPoint(2098, 3920, 0)) || loc.equals(new WorldPoint(2097, 3920, 0))) &&
				!(Rs2Inventory.hasItem(ItemID.LUNAR_SEAL_OF_PASSAGE) || Rs2Equipment.isWearing(ItemID.LUNAR_SEAL_OF_PASSAGE))) {
				return false;
			}

            ObjectComposition comp = convertToObjectComposition(gameObject);
            if (comp == null) return false;
            return hasAction(comp, "Bank", false) || hasAction(comp, "Collect", false);
        };

        return getGameObjects(o -> Arrays.stream(Rs2BankID.bankIds).anyMatch(bid -> o.getId() == bid), maxSearchRadius).stream()
                .filter(bankableFilter)
                .findFirst()
                .orElse(null);
    }

    public static GameObject findBank() {
        return findBank(20);
    }

    /**
     * Find nearest Deposit box
     *
     * @return GameObject
     */
    public static GameObject findDepositBox() {
        return findDepositBox(20);
    }

    public static GameObject findDepositBox(int maxSearchRadius) {
        Predicate<GameObject> depositableFilter = gameObject -> {
            ObjectComposition comp = convertToObjectComposition(gameObject);
            if (comp == null) return false;
            return hasAction(comp, "Deposit", false);
        };
        return getGameObjects(o -> Arrays.stream(Rs2BankID.bankIds).anyMatch(bid -> o.getId() == bid), maxSearchRadius).stream()
                .filter(depositableFilter)
                .findFirst()
                .orElse(null);
    }

    public static WallObject findGrandExchangeBooth(int maxSearchRadius) {
        Integer[] grandExchangeBoothIds = new Integer[]{10060, 30389};
        return getWallObjects(o -> Arrays.stream(grandExchangeBoothIds).anyMatch(gid -> o.getId() == gid) && Rs2Tile.isTileReachable(o.getWorldLocation()), maxSearchRadius).stream()
                .findFirst()
                .orElse(null);
    }

    public static WallObject findGrandExchangeBooth() {
        return findGrandExchangeBooth(20);
    }

    @Deprecated
    public static ObjectComposition convertGameObjectToObjectComposition(TileObject tileObject) {
        return convertToObjectComposition(tileObject);
    }

    @Deprecated
    public static ObjectComposition convertGameObjectToObjectComposition(int objectId) {
        return convertToObjectComposition(objectId);
    }

	public static String getObjectType(TileObject object)
	{
		String type;
		if (object instanceof WallObject) {
			type = "WallObject";
		} else if (object instanceof DecorativeObject) {
			type = "DecorativeObject";
		} else if (object instanceof GameObject) {
			type = "GameObject";
		} else if (object instanceof GroundObject) {
			type = "GroundObject";
		} else {
			type = "TileObject";
		}
		return type;
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

    public static List<TileObject> getAll() {
        return getAll(o -> true);
    }

    public static <T extends TileObject> List<TileObject> getAll(Predicate<? super T> predicate) {
        return getAll(predicate, Constants.SCENE_SIZE);
    }

	public static <T extends TileObject> List<TileObject> getAll(Predicate<? super T> predicate, int distance) {
		Player player = Microbot.getClient().getLocalPlayer();
		if (player == null) {
			return Collections.emptyList();
		}
		return getAll(predicate, player.getWorldLocation(), distance);
	}

	public static <T extends TileObject> List<TileObject> getAll(Predicate<? super T> predicate, WorldPoint anchor) {
		return getAll(predicate, anchor, Constants.SCENE_SIZE);
	}

    public static <T extends TileObject> List<TileObject> getAll(Predicate<? super T> predicate, WorldPoint anchor, int distance) {
        List<TileObject> all = new ArrayList<>();
        all.addAll(fetchGameObjects(predicate, anchor, distance));
		all.addAll(fetchTileObjects(predicate, anchor, distance));
        return all;
    }

    public static TileObject getTileObject(int id) {
        return getTileObject(o -> o.getId() == id);
    }

    public static TileObject getTileObject(int id, int distance) {
        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null) {
            return null;
        }
        return getTileObject(id, player.getWorldLocation(), distance);
    }

    public static TileObject getTileObject(int id, WorldPoint anchor) {
        return getTileObject(id, anchor, (Constants.SCENE_SIZE / 2));
    }

    public static TileObject getTileObject(int id, WorldPoint anchor, int distance) {
        return getTileObject(o -> o.getId() == id, anchor, distance);
    }

    public static TileObject getTileObject(Integer[] ids) {
        Set<Integer> idSet = Stream.of(ids).collect(Collectors.toSet());
        return getTileObject(o -> idSet.contains(o.getId()));
    }

    public static TileObject getTileObject(Integer[] ids, int distance) {
        Set<Integer> idSet = Stream.of(ids).collect(Collectors.toSet());
        return getTileObject(o -> idSet.contains(o.getId()), distance);
    }

    public static TileObject getTileObject(String objectName, boolean exact) {
        return getTileObject(nameMatches(objectName, exact));
    }

    public static TileObject getTileObject(String objectName) {
        return getTileObject(objectName, false);
    }

    public static TileObject getTileObject(String objectName, boolean exact, int distance) {
        return getTileObject(nameMatches(objectName, exact), distance);
    }

    public static TileObject getTileObject(String objectName, int distance) {
        return getTileObject(objectName, false, distance);
    }

    public static TileObject getTileObject(String objectName, boolean exact, WorldPoint anchor) {
        return getTileObject(nameMatches(objectName, exact), anchor);
    }

    public static TileObject getTileObject(String objectName, WorldPoint anchor) {
        return getTileObject(objectName, false, anchor);
    }

    public static TileObject getTileObject(String objectName, boolean exact, LocalPoint anchorLocal) {
        return getTileObject(nameMatches(objectName, exact), anchorLocal);
    }

    public static TileObject getTileObject(String objectName, LocalPoint anchorLocal) {
        return getTileObject(objectName, false, anchorLocal);
    }

    public static TileObject getTileObject(String objectName, boolean exact, WorldPoint anchor, int distance) {
        return getTileObject(nameMatches(objectName, exact), anchor, distance);
    }

    public static TileObject getTileObject(String objectName, WorldPoint anchor, int distance) {
        return getTileObject(objectName, false, anchor, distance);
    }

    public static TileObject getTileObject(String objectName, boolean exact, LocalPoint anchorLocal, int distance) {
        return getTileObject(nameMatches(objectName, exact), anchorLocal, distance);
    }

    public static TileObject getTileObject(String objectName, LocalPoint anchorLocal, int distance) {
        return getTileObject(nameMatches(objectName, false), anchorLocal, distance);
    }

    public static TileObject getTileObject(Predicate<TileObject> predicate) {
        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null) {
            return null;
        }
        return getTileObject(predicate, player.getWorldLocation());
    }

    public static TileObject getTileObject(WorldPoint anchor) {
        return getTileObject(o -> true, anchor);
    }

    public static TileObject getTileObject(LocalPoint anchorLocal) {
        return getTileObject(o -> true, anchorLocal);
    }

    public static TileObject getTileObject(WorldPoint anchor, int distance) {
        return getTileObject(o -> true, anchor, distance);
    }

    public static TileObject getTileObject(LocalPoint anchorLocal, int distance) {
        return getTileObject(o -> true, anchorLocal, distance);
    }

    public static TileObject getTileObject(Predicate<TileObject> predicate, int distance) {
        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null) {
            return null;
        }
        return getTileObject(predicate, player.getWorldLocation(), distance);
    }

    public static TileObject getTileObject(Predicate<TileObject> predicate, WorldPoint anchor) {
        return getTileObject(predicate, anchor, (Constants.SCENE_SIZE / 2));
    }

    public static TileObject getTileObject(Predicate<TileObject> predicate, LocalPoint anchorLocal) {
        return getTileObject(predicate, anchorLocal, (Constants.SCENE_SIZE / 2) * Perspective.LOCAL_TILE_SIZE);
    }

    public static TileObject getTileObject(Predicate<TileObject> predicate, WorldPoint anchor, int distance) {
        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null) {
            return null;
        }
        LocalPoint anchorLocal = LocalPoint.fromWorld(player.getWorldView(), anchor);
        if (anchorLocal == null) {
            return null;
        }
        return getTileObject(predicate, anchorLocal, Rs2LocalPoint.worldToLocalDistance(distance));
    }

    public static TileObject getTileObject(Predicate<TileObject> predicate, LocalPoint anchorLocal, int distance) {
        return getSceneObject(TILEOBJECT_EXTRACTOR, predicate, anchorLocal, distance);
    }

    public static List<TileObject> getTileObjects() {
        return getTileObjects(o -> true);
    }

    public static List<TileObject> getTileObjects(int distance) {
        return getTileObjects(o -> true, distance);
    }

    public static List<TileObject> getTileObjects(Predicate<TileObject> predicate, int distance) {
        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null) {
            return Collections.emptyList();
        }
        return getTileObjects(predicate, player.getWorldLocation(), distance);
    }

    public static List<TileObject> getTileObjects(WorldPoint anchor) {
        return getTileObjects(o -> true, anchor);
    }

    public static List<TileObject> getTileObjects(LocalPoint anchorLocal) {
        return getTileObjects(o -> true, anchorLocal);
    }

    public static List<TileObject> getTileObjects(Predicate<TileObject> predicate) {
        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null) {
            return Collections.emptyList();
        }
        return getTileObjects(predicate, player.getWorldLocation());
    }

    public static List<TileObject> getTileObjects(Predicate<TileObject> predicate, WorldPoint anchor) {
        return getTileObjects(predicate, anchor, (Constants.SCENE_SIZE / 2));
    }

    public static List<TileObject> getTileObjects(Predicate<TileObject> predicate, LocalPoint anchorLocal) {
        return getTileObjects(predicate, anchorLocal, (Constants.SCENE_SIZE / 2) * Perspective.LOCAL_TILE_SIZE);
    }

    public static List<TileObject> getTileObjects(Predicate<TileObject> predicate, WorldPoint anchor, int distance) {
        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null) {
            return Collections.emptyList();
        }
        LocalPoint anchorLocal = LocalPoint.fromWorld(player.getWorldView(), anchor);
        if (anchorLocal == null) {
            return Collections.emptyList();
        }
        return getTileObjects(predicate, anchorLocal, Rs2LocalPoint.worldToLocalDistance(distance));
    }

    public static List<TileObject> getTileObjects(Predicate<TileObject> predicate, LocalPoint anchorLocal, int distance) {
        return getSceneObjects(TILEOBJECT_EXTRACTOR, predicate, anchorLocal, distance);
    }

    public static GameObject getGameObject(int id) {
        return getGameObject(id, (Constants.SCENE_SIZE / 2));
    }

    public static GameObject getGameObject(int id, int distance) {
        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null) {
            return null;
        }
        return getGameObject(id, player.getWorldLocation(), distance);
    }

    public static GameObject getGameObject(int id, WorldPoint anchor) {
        return getGameObject(id, anchor, (Constants.SCENE_SIZE / 2));
    }

    public static GameObject getGameObject(int id, WorldPoint anchor, int distance) {
        return getGameObject(o -> o.getId() == id, anchor, distance);
    }

    public static GameObject getGameObject(Integer[] ids) {
        Set<Integer> idSet = Stream.of(ids).collect(Collectors.toSet());
        return getGameObject(o -> idSet.contains(o.getId()));
    }

    @Deprecated
    public static GameObject findObject(Integer[] ids) {
        Set<Integer> idSet = Stream.of(ids).collect(Collectors.toSet());
        return getGameObject(o -> idSet.contains(o.getId()));
    }

    public static GameObject getGameObject(Integer[] ids, int distance) {
        Set<Integer> idSet = Stream.of(ids).collect(Collectors.toSet());
        return getGameObject(o -> idSet.contains(o.getId()), distance);
    }

    public static GameObject getGameObject(String objectName, boolean exact, int distance) {
        return getGameObject(nameMatches(objectName, exact), distance);
    }

    public static GameObject getGameObject(String objectName, boolean exact) {
        return getGameObject(nameMatches(objectName, exact));
    }

    public static GameObject getGameObject(String objectName) {
        return getGameObject(objectName, false);
    }

    public static GameObject getGameObject(String objectName, int distance) {
        return getGameObject(objectName, false, distance);
    }

    public static GameObject getGameObject(String objectName, boolean exact, WorldPoint anchor) {
        return getGameObject(nameMatches(objectName, exact), anchor);
    }

    public static GameObject getGameObject(String objectName, WorldPoint anchor) {
        return getGameObject(objectName, false, anchor);
    }

    public static GameObject getGameObject(String objectName, boolean exact, LocalPoint anchorLocal) {
        return getGameObject(nameMatches(objectName, exact), anchorLocal);
    }

    public static GameObject getGameObject(String objectName, LocalPoint anchorLocal) {
        return getGameObject(objectName, false, anchorLocal);
    }

    public static GameObject getGameObject(String objectName, boolean exact, WorldPoint anchor, int distance) {
        return getGameObject(nameMatches(objectName, exact), anchor, distance);
    }

    public static GameObject getGameObject(String objectName, WorldPoint anchor, int distance) {
        return getGameObject(objectName, false, anchor, distance);
    }

    public static GameObject getGameObject(String objectName, boolean exact, LocalPoint anchorLocal, int distance) {
        return getGameObject(nameMatches(objectName, exact), anchorLocal, distance);
    }

    public static GameObject getGameObject(String objectName, LocalPoint anchorLocal, int distance) {
        return getGameObject(objectName, false, anchorLocal, distance);
    }

    public static GameObject getGameObject(Predicate<GameObject> predicate) {
        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null) {
            return null;
        }
        return getGameObject(predicate, player.getWorldLocation());
    }

    public static GameObject getGameObject(WorldPoint anchor) {
        return getGameObject(o -> true, anchor);
    }

    public static GameObject getGameObject(LocalPoint anchorLocal) {
        return getGameObject(o -> true, anchorLocal);
    }

    public static GameObject getGameObject(WorldPoint anchor, int distance) {
        return getGameObject(o -> true, anchor, distance);
    }

    public static GameObject getGameObject(LocalPoint anchorLocal, int distance) {
        return getGameObject(o -> true, anchorLocal, distance);
    }

    public static GameObject getGameObject(Predicate<GameObject> predicate, int distance) {
        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null) {
            return null;
        }
        return getGameObject(predicate, player.getWorldLocation(), distance);
    }

    public static GameObject getGameObject(Predicate<GameObject> predicate, WorldPoint anchor) {
        return getGameObject(predicate, anchor, (Constants.SCENE_SIZE / 2));
    }

    public static GameObject getGameObject(Predicate<GameObject> predicate, LocalPoint anchorLocal) {
        return getGameObject(predicate, anchorLocal, (Constants.SCENE_SIZE / 2) * Perspective.LOCAL_TILE_SIZE);
    }

    public static GameObject getGameObject(Predicate<GameObject> predicate, WorldPoint anchor, int distance) {
        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null) {
            return null;
        }
        LocalPoint anchorLocal = LocalPoint.fromWorld(player.getWorldView(), anchor);
        if (anchorLocal == null) {
            return null;
        }
        return getGameObject(predicate, anchorLocal, Rs2LocalPoint.worldToLocalDistance(distance));
    }

    public static GameObject getGameObject(Predicate<GameObject> predicate, LocalPoint anchorLocal, int distance) {
        return getSceneObject(GAMEOBJECT_EXTRACTOR, predicate, anchorLocal, distance);
    }

    public static List<GameObject> getGameObjects() {
        return getGameObjects(o -> true);
    }

    public static List<GameObject> getGameObjects(int distance) {
        return getGameObjects(o -> true, distance);
    }

    public static List<GameObject> getGameObjects(Predicate<GameObject> predicate, int distance) {
        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null) {
            return Collections.emptyList();
        }
        return getGameObjects(predicate, player.getWorldLocation(), distance);
    }

    public static List<GameObject> getGameObjects(WorldPoint anchor) {
        return getGameObjects(o -> true, anchor);
    }

    public static List<GameObject> getGameObjects(LocalPoint anchorLocal) {
        return getGameObjects(o -> true, anchorLocal);
    }

    public static List<GameObject> getGameObjects(Predicate<GameObject> predicate) {
        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null) {
            return Collections.emptyList();
        }
        return getGameObjects(predicate, player.getWorldLocation());
    }

    public static List<GameObject> getGameObjects(Predicate<GameObject> predicate, WorldPoint anchor) {
        return getGameObjects(predicate, anchor, (Constants.SCENE_SIZE / 2));
    }

    public static List<GameObject> getGameObjects(Predicate<GameObject> predicate, LocalPoint anchorLocal) {
        return getGameObjects(predicate, anchorLocal, (Constants.SCENE_SIZE / 2) * Perspective.LOCAL_TILE_SIZE);
    }

    public static List<GameObject> getGameObjects(Predicate<GameObject> predicate, WorldPoint anchor, int distance) {
        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null) {
            return Collections.emptyList();
        }
        LocalPoint anchorLocal = LocalPoint.fromWorld(player.getWorldView(), anchor);
        if (anchorLocal == null) {
            return Collections.emptyList();
        }
        return getGameObjects(predicate, anchorLocal, Rs2LocalPoint.worldToLocalDistance(distance));
    }

    public static List<GameObject> getGameObjects(Predicate<GameObject> predicate, LocalPoint anchorLocal, int distance) {
        return getSceneObjects(GAMEOBJECT_EXTRACTOR, predicate, anchorLocal, distance);
    }

    public static GroundObject getGroundObject(int id) {
        return getGroundObject(id, (Constants.SCENE_SIZE / 2));
    }

    public static GroundObject getGroundObject(int id, int distance) {
        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null) {
            return null;
        }
        return getGroundObject(id, player.getWorldLocation(), distance);
    }

    public static GroundObject getGroundObject(int id, WorldPoint anchor) {
        return getGroundObject(id, anchor, (Constants.SCENE_SIZE / 2));
    }

    public static GroundObject getGroundObject(int id, WorldPoint anchor, int distance) {
        return getGroundObject(o -> o.getId() == id, anchor, distance);
    }

    public static GroundObject getGroundObject(Integer[] ids) {
        Set<Integer> idSet = Stream.of(ids).collect(Collectors.toSet());
        return getGroundObject(o -> idSet.contains(o.getId()));
    }

    public static GroundObject getGroundObject(Integer[] ids, int distance) {
        Set<Integer> idSet = Stream.of(ids).collect(Collectors.toSet());
        return getGroundObject(o -> idSet.contains(o.getId()), distance);
    }

    public static GroundObject getGroundObject(String objectName, boolean exact, int distance) {
        return getGroundObject(nameMatches(objectName, exact), distance);
    }

    public static GroundObject getGroundObject(String objectName, boolean exact) {
        return getGroundObject(nameMatches(objectName, exact));
    }

    public static GroundObject getGroundObject(String objectName) {
        return getGroundObject(objectName, false);
    }

    public static GroundObject getGroundObject(String objectName, int distance) {
        return getGroundObject(objectName, false, distance);
    }

    public static GroundObject getGroundObject(String objectName, boolean exact, WorldPoint anchor) {
        return getGroundObject(nameMatches(objectName, exact), anchor);
    }

    public static GroundObject getGroundObject(String objectName, WorldPoint anchor) {
        return getGroundObject(objectName, false, anchor);
    }

    public static GroundObject getGroundObject(String objectName, boolean exact, LocalPoint anchorLocal) {
        return getGroundObject(nameMatches(objectName, exact), anchorLocal);
    }

    public static GroundObject getGroundObject(String objectName, LocalPoint anchorLocal) {
        return getGroundObject(objectName, false, anchorLocal);
    }

    public static GroundObject getGroundObject(String objectName, boolean exact, WorldPoint anchor, int distance) {
        return getGroundObject(nameMatches(objectName, exact), anchor, distance);
    }

    public static GroundObject getGroundObject(String objectName, WorldPoint anchor, int distance) {
        return getGroundObject(objectName, false, anchor, distance);
    }

    public static GroundObject getGroundObject(String objectName, boolean exact, LocalPoint anchorLocal, int distance) {
        return getGroundObject(nameMatches(objectName, exact), anchorLocal, distance);
    }

    public static GroundObject getGroundObject(String objectName, LocalPoint anchorLocal, int distance) {
        return getGroundObject(objectName, false, anchorLocal, distance);
    }

    public static GroundObject getGroundObject(Predicate<GroundObject> predicate) {
        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null) {
            return null;
        }
        return getGroundObject(predicate, player.getWorldLocation());
    }

    public static GroundObject getGroundObject(WorldPoint anchor) {
        return getGroundObject(o -> true, anchor);
    }

    public static GroundObject getGroundObject(LocalPoint anchorLocal) {
        return getGroundObject(o -> true, anchorLocal);
    }

    public static GroundObject getGroundObject(WorldPoint anchor, int distance) {
        return getGroundObject(o -> true, anchor, distance);
    }

    public static GroundObject getGroundObject(LocalPoint anchorLocal, int distance) {
        return getGroundObject(o -> true, anchorLocal, distance);
    }

    public static GroundObject getGroundObject(Predicate<GroundObject> predicate, int distance) {
        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null) {
            return null;
        }
        return getGroundObject(predicate, player.getWorldLocation(), distance);
    }

    public static GroundObject getGroundObject(Predicate<GroundObject> predicate, WorldPoint anchor) {
        return getGroundObject(predicate, anchor, (Constants.SCENE_SIZE / 2));
    }

    public static GroundObject getGroundObject(Predicate<GroundObject> predicate, LocalPoint anchorLocal) {
        return getGroundObject(predicate, anchorLocal, (Constants.SCENE_SIZE / 2) * Perspective.LOCAL_TILE_SIZE);
    }

    public static GroundObject getGroundObject(Predicate<GroundObject> predicate, WorldPoint anchor, int distance) {
        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null) {
            return null;
        }
        LocalPoint anchorLocal = LocalPoint.fromWorld(player.getWorldView(), anchor);
        if (anchorLocal == null) {
            return null;
        }
        return getGroundObject(predicate, anchorLocal, Rs2LocalPoint.worldToLocalDistance(distance));
    }

    public static GroundObject getGroundObject(Predicate<GroundObject> predicate, LocalPoint anchorLocal, int distance) {
        return getSceneObject(GROUNDOBJECT_EXTRACTOR, predicate, anchorLocal, distance);
    }

    public static List<GroundObject> getGroundObjects() {
        return getGroundObjects(o -> true);
    }

    public static List<GroundObject> getGroundObjects(int distance) {
        return getGroundObjects(o -> true, distance);
    }

    public static List<GroundObject> getGroundObjects(Predicate<GroundObject> predicate, int distance) {
        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null) {
            return Collections.emptyList();
        }
        return getGroundObjects(predicate, player.getWorldLocation(), distance);
    }

    public static List<GroundObject> getGroundObjects(WorldPoint anchor) {
        return getGroundObjects(o -> true, anchor);
    }

    public static List<GroundObject> getGroundObjects(LocalPoint anchorLocal) {
        return getGroundObjects(o -> true, anchorLocal);
    }

    public static List<GroundObject> getGroundObjects(Predicate<GroundObject> predicate) {
        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null) {
            return Collections.emptyList();
        }
        return getGroundObjects(predicate, player.getWorldLocation());
    }

    public static List<GroundObject> getGroundObjects(Predicate<GroundObject> predicate, WorldPoint anchor) {
        return getGroundObjects(predicate, anchor, (Constants.SCENE_SIZE / 2));
    }

    public static List<GroundObject> getGroundObjects(Predicate<GroundObject> predicate, LocalPoint anchorLocal) {
        return getGroundObjects(predicate, anchorLocal, (Constants.SCENE_SIZE / 2) * Perspective.LOCAL_TILE_SIZE);
    }

    public static List<GroundObject> getGroundObjects(Predicate<GroundObject> predicate, WorldPoint anchor, int distance) {
        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null) {
            return Collections.emptyList();
        }
        LocalPoint anchorLocal = LocalPoint.fromWorld(player.getWorldView(), anchor);
        if (anchorLocal == null) {
            return Collections.emptyList();
        }
        return getGroundObjects(predicate, anchorLocal, Rs2LocalPoint.worldToLocalDistance(distance));
    }

    public static List<GroundObject> getGroundObjects(Predicate<GroundObject> predicate, LocalPoint anchorLocal, int distance) {
        return getSceneObjects(GROUNDOBJECT_EXTRACTOR, predicate, anchorLocal, distance);
    }

    public static WallObject getWallObject(int id) {
        return getWallObject(id, (Constants.SCENE_SIZE / 2));
    }

    public static WallObject getWallObject(int id, int distance) {
        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null) {
            return null;
        }
        return getWallObject(id, player.getWorldLocation(), distance);
    }

    public static WallObject getWallObject(int id, WorldPoint anchor) {
        return getWallObject(id, anchor, (Constants.SCENE_SIZE / 2));
    }

    public static WallObject getWallObject(int id, WorldPoint anchor, int distance) {
        return getWallObject(o -> o.getId() == id, anchor, distance);
    }

    public static WallObject getWallObject(Integer[] ids) {
        Set<Integer> idSet = Stream.of(ids).collect(Collectors.toSet());
        return getWallObject(o -> idSet.contains(o.getId()));
    }

    public static WallObject getWallObject(Integer[] ids, int distance) {
        Set<Integer> idSet = Stream.of(ids).collect(Collectors.toSet());
        return getWallObject(o -> idSet.contains(o.getId()), distance);
    }

    public static WallObject getWallObject(String objectName, boolean exact, int distance) {
        return getWallObject(nameMatches(objectName, exact), distance);
    }

    public static WallObject getWallObject(String objectName, boolean exact) {
        return getWallObject(nameMatches(objectName, exact));
    }

    public static WallObject getWallObject(String objectName) {
        return getWallObject(objectName, false);
    }

    public static WallObject getWallObject(String objectName, int distance) {
        return getWallObject(objectName, false, distance);
    }

    public static WallObject getWallObject(String objectName, boolean exact, WorldPoint anchor) {
        return getWallObject(nameMatches(objectName, exact), anchor);
    }

    public static WallObject getWallObject(String objectName, WorldPoint anchor) {
        return getWallObject(objectName, false, anchor);
    }

    public static WallObject getWallObject(String objectName, boolean exact, LocalPoint anchorLocal) {
        return getWallObject(nameMatches(objectName, exact), anchorLocal);
    }

    public static WallObject getWallObject(String objectName, LocalPoint anchorLocal) {
        return getWallObject(objectName, false, anchorLocal);
    }

    public static WallObject getWallObject(String objectName, boolean exact, WorldPoint anchor, int distance) {
        return getWallObject(nameMatches(objectName, exact), anchor, distance);
    }

    public static WallObject getWallObject(String objectName, WorldPoint anchor, int distance) {
        return getWallObject(objectName, false, anchor, distance);
    }

    public static WallObject getWallObject(String objectName, boolean exact, LocalPoint anchorLocal, int distance) {
        return getWallObject(nameMatches(objectName, exact), anchorLocal, distance);
    }

    public static WallObject getWallObject(String objectName, LocalPoint anchorLocal, int distance) {
        return getWallObject(objectName, false, anchorLocal, distance);
    }

    public static WallObject getWallObject(Predicate<WallObject> predicate) {
        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null) {
            return null;
        }
        return getWallObject(predicate, player.getWorldLocation());
    }

    public static WallObject getWallObject(WorldPoint anchor) {
        return getWallObject(o -> true, anchor);
    }

    public static WallObject getWallObject(LocalPoint anchorLocal) {
        return getWallObject(o -> true, anchorLocal);
    }

    public static WallObject getWallObject(WorldPoint anchor, int distance) {
        return getWallObject(o -> true, anchor, distance);
    }

    public static WallObject getWallObject(LocalPoint anchorLocal, int distance) {
        return getWallObject(o -> true, anchorLocal, distance);
    }

    public static WallObject getWallObject(Predicate<WallObject> predicate, int distance) {
        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null) {
            return null;
        }
        return getWallObject(predicate, player.getWorldLocation(), distance);
    }

    public static WallObject getWallObject(Predicate<WallObject> predicate, WorldPoint anchor) {
        return getWallObject(predicate, anchor, (Constants.SCENE_SIZE / 2));
    }

    public static WallObject getWallObject(Predicate<WallObject> predicate, LocalPoint anchorLocal) {
        return getWallObject(predicate, anchorLocal, (Constants.SCENE_SIZE / 2) * Perspective.LOCAL_TILE_SIZE);
    }

    public static WallObject getWallObject(Predicate<WallObject> predicate, WorldPoint anchor, int distance) {
        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null) {
            return null;
        }
        LocalPoint anchorLocal = LocalPoint.fromWorld(player.getWorldView(), anchor);
        if (anchorLocal == null) {
            return null;
        }
        return getWallObject(predicate, anchorLocal, Rs2LocalPoint.worldToLocalDistance(distance));
    }

    public static WallObject getWallObject(Predicate<WallObject> predicate, LocalPoint anchorLocal, int distance) {
        return getSceneObject(WALLOBJECT_EXTRACTOR, predicate, anchorLocal, distance);
    }

    public static List<WallObject> getWallObjects() {
        return getWallObjects(o -> true);
    }

    public static List<WallObject> getWallObjects(int distance) {
        return getWallObjects(o -> true, distance);
    }

    public static List<WallObject> getWallObjects(Predicate<WallObject> predicate, int distance) {
        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null) {
            return Collections.emptyList();
        }
        return getWallObjects(predicate, player.getWorldLocation(), distance);
    }

    public static List<WallObject> getWallObjects(WorldPoint anchor) {
        return getWallObjects(o -> true, anchor);
    }

    public static List<WallObject> getWallObjects(LocalPoint anchorLocal) {
        return getWallObjects(o -> true, anchorLocal);
    }

    public static List<WallObject> getWallObjects(Predicate<WallObject> predicate) {
        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null) {
            return Collections.emptyList();
        }
        return getWallObjects(predicate, player.getWorldLocation());
    }

    public static List<WallObject> getWallObjects(Predicate<WallObject> predicate, WorldPoint anchor) {
        return getWallObjects(predicate, anchor, (Constants.SCENE_SIZE / 2));
    }

    public static List<WallObject> getWallObjects(Predicate<WallObject> predicate, LocalPoint anchorLocal) {
        return getWallObjects(predicate, anchorLocal, (Constants.SCENE_SIZE / 2) * Perspective.LOCAL_TILE_SIZE);
    }

    public static List<WallObject> getWallObjects(Predicate<WallObject> predicate, WorldPoint anchor, int distance) {
        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null) {
            return Collections.emptyList();
        }
        LocalPoint anchorLocal = LocalPoint.fromWorld(player.getWorldView(), anchor);
        if (anchorLocal == null) {
            return Collections.emptyList();
        }
        return getWallObjects(predicate, anchorLocal, Rs2LocalPoint.worldToLocalDistance(distance));
    }

    public static List<WallObject> getWallObjects(Predicate<WallObject> predicate, LocalPoint anchorLocal, int distance) {
        return getSceneObjects(WALLOBJECT_EXTRACTOR, predicate, anchorLocal, distance);
    }

    public static DecorativeObject getDecorativeObject(int id) {
        return getDecorativeObject(id, (Constants.SCENE_SIZE / 2));
    }

    public static DecorativeObject getDecorativeObject(int id, int distance) {
        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null) {
            return null;
        }
        return getDecorativeObject(id, player.getWorldLocation(), distance);
    }

    public static DecorativeObject getDecorativeObject(int id, WorldPoint anchor) {
        return getDecorativeObject(id, anchor, (Constants.SCENE_SIZE / 2));
    }

    public static DecorativeObject getDecorativeObject(int id, WorldPoint anchor, int distance) {
        return getDecorativeObject(o -> o.getId() == id, anchor, distance);
    }

    public static DecorativeObject getDecorativeObject(Integer[] ids) {
        Set<Integer> idSet = Stream.of(ids).collect(Collectors.toSet());
        return getDecorativeObject(o -> idSet.contains(o.getId()));
    }

    public static DecorativeObject getDecorativeObject(Integer[] ids, int distance) {
        Set<Integer> idSet = Stream.of(ids).collect(Collectors.toSet());
        return getDecorativeObject(o -> idSet.contains(o.getId()), distance);
    }

    public static DecorativeObject getDecorativeObject(String objectName, boolean exact, int distance) {
        return getDecorativeObject(nameMatches(objectName, exact), distance);
    }

    public static DecorativeObject getDecorativeObject(String objectName, boolean exact) {
        return getDecorativeObject(nameMatches(objectName, exact));
    }

    public static DecorativeObject getDecorativeObject(String objectName) {
        return getDecorativeObject(objectName, false);
    }

    public static DecorativeObject getDecorativeObject(String objectName, int distance) {
        return getDecorativeObject(objectName, false, distance);
    }

    public static DecorativeObject getDecorativeObject(String objectName, boolean exact, WorldPoint anchor) {
        return getDecorativeObject(nameMatches(objectName, exact), anchor);
    }

    public static DecorativeObject getDecorativeObject(String objectName, WorldPoint anchor) {
        return getDecorativeObject(objectName, false, anchor);
    }

    public static DecorativeObject getDecorativeObject(String objectName, boolean exact, LocalPoint anchorLocal) {
        return getDecorativeObject(nameMatches(objectName, exact), anchorLocal);
    }

    public static DecorativeObject getDecorativeObject(String objectName, LocalPoint anchorLocal) {
        return getDecorativeObject(objectName, false, anchorLocal);
    }

    public static DecorativeObject getDecorativeObject(String objectName, boolean exact, WorldPoint anchor, int distance) {
        return getDecorativeObject(nameMatches(objectName, exact), anchor, distance);
    }

    public static DecorativeObject getDecorativeObject(String objectName, WorldPoint anchor, int distance) {
        return getDecorativeObject(objectName, false, anchor, distance);
    }

    public static DecorativeObject getDecorativeObject(String objectName, boolean exact, LocalPoint anchorLocal, int distance) {
        return getDecorativeObject(nameMatches(objectName, exact), anchorLocal, distance);
    }

    public static DecorativeObject getDecorativeObject(String objectName, LocalPoint anchorLocal, int distance) {
        return getDecorativeObject(objectName, false, anchorLocal, distance);
    }

    public static DecorativeObject getDecorativeObject(Predicate<DecorativeObject> predicate) {
        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null) {
            return null;
        }
        return getDecorativeObject(predicate, player.getWorldLocation());
    }

    public static DecorativeObject getDecorativeObject(WorldPoint anchor) {
        return getDecorativeObject(o -> true, anchor);
    }

    public static DecorativeObject getDecorativeObject(LocalPoint anchorLocal) {
        return getDecorativeObject(o -> true, anchorLocal);
    }

    public static DecorativeObject getDecorativeObject(WorldPoint anchor, int distance) {
        return getDecorativeObject(o -> true, anchor, distance);
    }

    public static DecorativeObject getDecorativeObject(LocalPoint anchorLocal, int distance) {
        return getDecorativeObject(o -> true, anchorLocal, distance);
    }

    public static DecorativeObject getDecorativeObject(Predicate<DecorativeObject> predicate, int distance) {
        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null) {
            return null;
        }
        return getDecorativeObject(predicate, player.getWorldLocation(), distance);
    }

    public static DecorativeObject getDecorativeObject(Predicate<DecorativeObject> predicate, WorldPoint anchor) {
        return getDecorativeObject(predicate, anchor, (Constants.SCENE_SIZE / 2));
    }

    public static DecorativeObject getDecorativeObject(Predicate<DecorativeObject> predicate, LocalPoint anchorLocal) {
        return getDecorativeObject(predicate, anchorLocal, (Constants.SCENE_SIZE / 2) * Perspective.LOCAL_TILE_SIZE);
    }

    public static DecorativeObject getDecorativeObject(Predicate<DecorativeObject> predicate, WorldPoint anchor, int distance) {
        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null) {
            return null;
        }
        LocalPoint anchorLocal = LocalPoint.fromWorld(player.getWorldView(), anchor);
        if (anchorLocal == null) {
            return null;
        }
        return getDecorativeObject(predicate, anchorLocal, Rs2LocalPoint.worldToLocalDistance(distance));
    }

    public static DecorativeObject getDecorativeObject(Predicate<DecorativeObject> predicate, LocalPoint anchorLocal, int distance) {
        return getSceneObject(DECORATIVEOBJECT_EXTRACTOR, predicate, anchorLocal, distance);
    }

    public static List<DecorativeObject> getDecorativeObjects() {
        return getDecorativeObjects(o -> true);
    }

    public static List<DecorativeObject> getDecorativeObjects(int distance) {
        return getDecorativeObjects(o -> true, distance);
    }

    public static List<DecorativeObject> getDecorativeObjects(Predicate<DecorativeObject> predicate, int distance) {
        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null) {
            return Collections.emptyList();
        }
        return getDecorativeObjects(predicate, player.getWorldLocation(), distance);
    }

    public static List<DecorativeObject> getDecorativeObjects(WorldPoint anchor) {
        return getDecorativeObjects(o -> true, anchor);
    }

    public static List<DecorativeObject> getDecorativeObjects(LocalPoint anchorLocal) {
        return getDecorativeObjects(o -> true, anchorLocal);
    }

    public static List<DecorativeObject> getDecorativeObjects(Predicate<DecorativeObject> predicate) {
        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null) {
            return Collections.emptyList();
        }
        return getDecorativeObjects(predicate, player.getWorldLocation());
    }

    public static List<DecorativeObject> getDecorativeObjects(Predicate<DecorativeObject> predicate, WorldPoint anchor) {
        return getDecorativeObjects(predicate, anchor, (Constants.SCENE_SIZE / 2));
    }

    public static List<DecorativeObject> getDecorativeObjects(Predicate<DecorativeObject> predicate, LocalPoint anchorLocal) {
        return getDecorativeObjects(predicate, anchorLocal, (Constants.SCENE_SIZE / 2) * Perspective.LOCAL_TILE_SIZE);
    }

    public static List<DecorativeObject> getDecorativeObjects(Predicate<DecorativeObject> predicate, WorldPoint anchor, int distance) {
        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null) {
            return Collections.emptyList();
        }
        LocalPoint anchorLocal = LocalPoint.fromWorld(player.getWorldView(), anchor);
        if (anchorLocal == null) {
            return Collections.emptyList();
        }
        return getDecorativeObjects(predicate, anchorLocal, Rs2LocalPoint.worldToLocalDistance(distance));
    }

    public static List<DecorativeObject> getDecorativeObjects(Predicate<DecorativeObject> predicate, LocalPoint anchorLocal, int distance) {
        return getSceneObjects(DECORATIVEOBJECT_EXTRACTOR, predicate, anchorLocal, distance);
    }

    @Nullable
    public static <T extends TileObject> ObjectComposition convertToObjectComposition(T object) {
        return convertToObjectComposition(object.getId(), false);
    }

    @Nullable
    public static ObjectComposition convertToObjectComposition(int objectId) {
        return convertToObjectCompositionInternal(objectId, false);
    }

    @Nullable
    public static <T extends TileObject> ObjectComposition convertToObjectComposition(T object, boolean ignoreImpostor) {
        return convertToObjectCompositionInternal(object.getId(), ignoreImpostor);
    }

    @Nullable
    public static ObjectComposition convertToObjectComposition(int objectId, boolean ignoreImpostor) {
        return convertToObjectCompositionInternal(objectId, ignoreImpostor);
    }

    public static <T> Optional<T> pickClosest(Collection<T> candidates, Function<T, WorldPoint> locFn, WorldPoint anchor) {
        return candidates.stream()
                .filter(Objects::nonNull)
                .min(Comparator.comparingInt(c -> locFn.apply(c).distanceTo(anchor)));
    }

    // private methods
    private static <T extends TileObject> Stream<T> getSceneObjects(Function<Tile, Collection<? extends T>> extractor) {
        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null) return Stream.empty();

        Scene scene = player.getWorldView().getScene();
        Tile[][][] tiles = scene.getTiles();
        if (tiles == null) return Stream.empty();

        List<T> result = new ArrayList<>();
        int z = player.getWorldView().getPlane();

        for (int x = 0; x < Constants.SCENE_SIZE; x++) {
            for (int y = 0; y < Constants.SCENE_SIZE; y++) {
                Tile tile = tiles[z][x][y];
                if (tile == null) continue;

                Collection<? extends T> objs = extractor.apply(tile);
                if (objs != null) {
                    for (T obj : objs) {
                        if (obj == null) continue;

                        if (obj instanceof GameObject) {
                            GameObject gameObject = (GameObject) obj;
                            if (gameObject.getSceneMinLocation().equals(tile.getSceneLocation())) {
                                result.add(obj);
                            }
                        } else {
                            if (obj.getLocalLocation().equals(tile.getLocalLocation())) {
                                result.add(obj);
                            }
                        }
                    }
                }
            }
        }

        return result.stream();
    }

    private static <T extends TileObject> List<T> getSceneObjects(Function<Tile, Collection<? extends T>> extractor, Predicate<T> predicate, LocalPoint anchorLocal, int distance) {
        if (distance > Rs2LocalPoint.worldToLocalDistance(Constants.SCENE_SIZE)) {
            distance = Rs2LocalPoint.worldToLocalDistance(Constants.SCENE_SIZE);
        }

        return getSceneObjects(extractor)
                .filter(withinTilesPredicate(distance, anchorLocal))
                .filter(predicate)
                .sorted(Comparator.comparingInt(o -> o.getLocalLocation().distanceTo(anchorLocal)))
                .collect(Collectors.toList());
    }

    private static <T extends TileObject> T getSceneObject(Function<Tile, Collection<? extends T>> extractor, Predicate<T> predicate, LocalPoint anchorLocal, int distance) {
        return getSceneObjects(extractor, predicate, anchorLocal, distance)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private static boolean isWithinTiles(LocalPoint anchor, LocalPoint objLoc, int distance) {
        int dx = Math.abs(anchor.getX() - objLoc.getX());
        int dy = Math.abs(anchor.getY() - objLoc.getY());

        if (distance == 0) {
            // exactly one tile away, no diagonals
            return (dx == Perspective.LOCAL_TILE_SIZE && dy == 0)
                    || (dy == Perspective.LOCAL_TILE_SIZE && dx == 0);
        } else {
            return objLoc.distanceTo(anchor) <= distance;
        }
    }

    private static <T extends TileObject> Predicate<T> withinTilesPredicate(int distance, LocalPoint anchor) {
        return to -> isWithinTiles(anchor, to.getLocalLocation(), distance);
    }

    private static Optional<String> getCompositionName(TileObject obj) {
        ObjectComposition comp = convertToObjectComposition(obj);
        if (comp == null) {
            return Optional.empty();
        }
        String name = comp.getName();
        return (name == null || name.equals("null"))
                ? Optional.empty()
                : Optional.of(Rs2UiHelper.stripColTags(name));
    }

    private static <T extends TileObject> Predicate<T> nameMatches(String objectName, boolean exact) {
        String normalizedForIds = objectName.toLowerCase().replace(" ", "_");
        Set<Integer> ids = new HashSet<>(getObjectIdsByName(normalizedForIds));

        String lower = objectName.toLowerCase();

        return obj -> {
            if (!ids.isEmpty() && !ids.contains(obj.getId())) {
                return false;
            }

            return getCompositionName(obj)
                    .map(compName -> exact ? compName.equalsIgnoreCase(objectName) : compName.toLowerCase().contains(lower))
                    .orElse(false);
        };
    }

	@SuppressWarnings("unchecked")
	private static <T extends TileObject> List<T> fetchTileObjects(Predicate<? super T> predicate, WorldPoint anchor, int distance) {
		return (List<T>) getTileObjects((Predicate<TileObject>) predicate, anchor, distance);
	}

	@SuppressWarnings("unchecked")
	private static <T extends TileObject> List<T> fetchGameObjects(Predicate<? super T> predicate, WorldPoint anchor, int distance) {
		return (List<T>) getGameObjects((Predicate<GameObject>) predicate, anchor, distance);
	}

	@SuppressWarnings("unchecked")
	private static <T extends TileObject> List<T> fetchTileObjects(Predicate<? super T> predicate, WorldPoint anchor) {
		return fetchTileObjects(predicate, anchor, Constants.SCENE_SIZE);
	}

	@SuppressWarnings("unchecked")
	private static <T extends TileObject> List<T> fetchGameObjects(Predicate<? super T> predicate, WorldPoint anchor) {
		return fetchTileObjects(predicate, anchor, Constants.SCENE_SIZE);
	}

    @SuppressWarnings("unchecked")
    private static <T extends TileObject> List<T> fetchTileObjects(Predicate<? super T> predicate, int distance) {
		Player player = Microbot.getClient().getLocalPlayer();
		if (player == null) {
			return Collections.emptyList();
		}
        return fetchTileObjects(predicate, player.getWorldLocation(), distance);
    }

    @SuppressWarnings("unchecked")
    private static <T extends TileObject> List<T> fetchGameObjects(Predicate<? super T> predicate, int distance) {
		Player player = Microbot.getClient().getLocalPlayer();
		if (player == null) {
			return Collections.emptyList();
		}
        return fetchGameObjects(predicate, player.getWorldLocation(), distance);
    }

    @SuppressWarnings("unchecked")
    private static <T extends TileObject> List<T> fetchTileObjects(Predicate<? super T> predicate) {
        return fetchTileObjects(predicate, Constants.SCENE_SIZE);
    }

    @SuppressWarnings("unchecked")
    private static <T extends TileObject> List<T> fetchGameObjects(Predicate<? super T> predicate) {
        return fetchGameObjects(predicate, Constants.SCENE_SIZE);
    }

    @Nullable
    private static ObjectComposition convertToObjectCompositionInternal(int objectId, boolean ignoreImpostor) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            ObjectComposition comp = Microbot.getClient().getObjectDefinition(objectId);
            if (comp == null) return null;
            return (ignoreImpostor || comp.getImpostorIds() == null) ? comp : comp.getImpostor();
        }).orElse(null);
    }

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

            ObjectComposition objComp = convertToObjectComposition(object);
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
        String lowerName = name.toLowerCase();

        Class<?>[] classesToScan = {
                net.runelite.api.ObjectID.class,
                net.runelite.api.gameval.ObjectID.class,
                net.runelite.client.plugins.microbot.util.gameobject.ObjectID.class
        };

        for (Class<?> clazz : classesToScan) {
            for (Field f : clazz.getFields()) {
                if (f.getType() != int.class) continue;

                if (f.getName().toLowerCase().contains(lowerName)) {
                    f.setAccessible(true);
                    ids.add(f.getInt(null));
                }
            }
        }
        return ids;
    }

    @Nullable
    @Deprecated
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
     *
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

    public static WorldArea getWorldArea(GameObject gameObject) {
        if (!gameObject.getLocalLocation().isInScene()) {
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
            if (Rs2AntibanSettings.devDebug)
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