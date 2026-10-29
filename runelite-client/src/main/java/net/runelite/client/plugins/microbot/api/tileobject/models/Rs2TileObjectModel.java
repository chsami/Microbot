package net.runelite.client.plugins.microbot.api.tileobject.models;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.player.models.Rs2PlayerModel;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.walker.recovery.CantReachTargetRecovery;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldArea;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.api.IEntity;
import net.runelite.client.plugins.microbot.api.boat.Rs2BoatCache;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Arrays;
import java.util.List;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class Rs2TileObjectModel implements TileObject, IEntity {

    public Rs2TileObjectModel(GameObject gameObject) {
        this.tileObject = gameObject;
        this.tileObjectType = TileObjectType.GAME;
    }

    public Rs2TileObjectModel(DecorativeObject tileObject) {
        this.tileObject = tileObject;
        this.tileObjectType = TileObjectType.DECORATIVE;
    }

    public Rs2TileObjectModel(WallObject tileObject) {
        this.tileObject = tileObject;
        this.tileObjectType = TileObjectType.WALL;
    }

    public Rs2TileObjectModel(GroundObject tileObject) {
        this.tileObject = tileObject;
        this.tileObjectType = TileObjectType.GROUND;
    }

    public Rs2TileObjectModel(TileObject tileObject) {
        TileObject unwrapped = tileObject instanceof Rs2TileObjectModel
                ? ((Rs2TileObjectModel) tileObject).getTileObject()
                : tileObject;
        this.tileObject = unwrapped;
        this.tileObjectType = typeOf(unwrapped);
    }

    static TileObjectType typeOf(TileObject tileObject) {
        if (tileObject instanceof GameObject) {
            return TileObjectType.GAME;
        }
        if (tileObject instanceof WallObject) {
            return TileObjectType.WALL;
        }
        if (tileObject instanceof DecorativeObject) {
            return TileObjectType.DECORATIVE;
        }
        if (tileObject instanceof GroundObject) {
            return TileObjectType.GROUND;
        }
        return TileObjectType.GENERIC;
    }

    static final int MAX_INTERACT_DISTANCE = 51;

    @Getter
    private final TileObjectType tileObjectType;
    @Getter
    private final TileObject tileObject;
    private String[] actions;


    @Override
    public long getHash() {
        return tileObject.getHash();
    }

    @Override
    public int getX() {
        return tileObject.getX();
    }

    @Override
    public int getY() {
        return tileObject.getY();
    }

    @Override
    public int getZ() {
        return tileObject.getZ();
    }

    @Override
    public int getPlane() {
        return tileObject.getPlane();
    }

    @Override
    public WorldView getWorldView() {
        return tileObject.getWorldView();
    }

    public int getId() {
        return tileObject.getId();
    }

    @Override
    public @NotNull WorldPoint getWorldLocation() {
        WorldPoint worldLocation = tileObject.getWorldLocation();

        if (!(tileObject instanceof GameObject)) {
            return worldLocation;
        }

        GameObject go = (GameObject) tileObject;
        WorldView wv = getWorldView();
        Point sceneMin = go.getSceneMinLocation();

        if (wv == null || sceneMin == null) {
            return worldLocation;
        }

        return WorldPoint.fromScene(wv, sceneMin.getX(), sceneMin.getY(), wv.getPlane());
    }

    public String getName() {
        return Microbot.getClientThread().invoke(() -> {
            ObjectComposition composition = Microbot.getClient().getObjectDefinition(tileObject.getId());
            if (composition != null && composition.getImpostorIds() != null) {
                composition = composition.getImpostor();
            }
            if (composition == null)
                return null;
            return Rs2UiHelper.stripColTags(composition.getName());
        });
    }

    @Override
    public @NotNull LocalPoint getLocalLocation() {
        return tileObject.getLocalLocation();
    }

    @Override
    public @Nullable Point getCanvasLocation() {
        return tileObject.getCanvasLocation();
    }

    @Override
    public @Nullable Point getCanvasLocation(int zOffset) {
        return tileObject.getCanvasLocation();
    }

    @Override
    public @Nullable Polygon getCanvasTilePoly() {
        return tileObject.getCanvasTilePoly();
    }

    @Override
    public @Nullable Point getCanvasTextLocation(Graphics2D graphics, String text, int zOffset) {
        return tileObject.getCanvasTextLocation(graphics, text, zOffset);
    }

    @Override
    public @Nullable Point getMinimapLocation() {
        return tileObject.getMinimapLocation();
    }

    @Override
    public @Nullable Shape getClickbox() {
        return Microbot.getClientThread().runOnClientThreadOptional(tileObject::getClickbox).orElse(null);
    }

    @Override
    public @Nullable String getOpOverride(int index) {
        return tileObject.getOpOverride(index);
    }

    @Override
    public boolean isOpShown(int index) {
        return tileObject.isOpShown(index);
    }

    public ObjectComposition getObjectComposition() {
        return Microbot.getClientThread().invoke(() -> {
            ObjectComposition composition = Microbot.getClient().getObjectDefinition(tileObject.getId());
            if (composition != null && composition.getImpostorIds() != null) {
                ObjectComposition impostor = composition.getImpostor();
                if (impostor != null) {
                    composition = impostor;
                }
            }
            return composition;
        });
    }

    public static @Nullable ObjectComposition compositionOf(int objectId) {
        return compositionOf(objectId, false);
    }

    public static @Nullable ObjectComposition compositionOf(int objectId, boolean ignoreImpostor) {
        return Microbot.getClientThread().invoke(() -> {
            ObjectComposition composition = Microbot.getClient().getObjectDefinition(objectId);
            if (composition == null || ignoreImpostor || composition.getImpostorIds() == null) {
                return composition;
            }
            ObjectComposition impostor = composition.getImpostor();
            return impostor != null ? impostor : composition;
        });
    }

    private ObjectComposition getVisibleObjectComposition() {
        return Microbot.getClientThread().invoke(() -> {
            ObjectComposition composition = Microbot.getClient().getObjectDefinition(tileObject.getId());
            if (composition != null && composition.getImpostorIds() != null) {
                return composition.getImpostor();
            }
            return composition;
        });
    }

    public int sizeX() {
        if (!(tileObject instanceof GameObject)) {
            return 1;
        }
        GameObject gameObject = (GameObject) tileObject;
        Integer size = Microbot.getClientThread().invoke(() -> gameObject.sizeX());
        return size != null ? size : 1;
    }

    public int sizeY() {
        if (!(tileObject instanceof GameObject)) {
            return 1;
        }
        GameObject gameObject = (GameObject) tileObject;
        Integer size = Microbot.getClientThread().invoke(() -> gameObject.sizeY());
        return size != null ? size : 1;
    }

    public @Nullable WorldArea getWorldArea() {
        WorldPoint southWest = getWorldLocation();
        if (southWest == null) {
            return null;
        }
        return new WorldArea(southWest, sizeX(), sizeY());
    }

    public boolean occupies(WorldPoint point) {
        if (point == null) {
            return false;
        }
        WorldPoint southWest = getWorldLocation();
        if (southWest == null || southWest.getPlane() != point.getPlane()) {
            return false;
        }
        int dx = point.getX() - southWest.getX();
        int dy = point.getY() - southWest.getY();
        return dx >= 0 && dx < sizeX() && dy >= 0 && dy < sizeY();
    }

    public boolean hasAction(String action) {
        return matchesAction(action, true);
    }

    public boolean hasActionContaining(String action) {
        return matchesAction(action, false);
    }

    public @Nullable String findAction(String action) {
        if (action == null || action.isBlank()) {
            return null;
        }
        String[] actions = Microbot.getClientThread().invoke(() -> {
            ObjectComposition composition = getVisibleObjectComposition();
            return composition != null ? composition.getActions() : null;
        });
        if (actions == null) {
            return null;
        }
        String wanted = Rs2UiHelper.stripColTags(action).trim();
        String needle = wanted.toLowerCase();
        String partial = null;
        for (String candidate : actions) {
            if (candidate == null) {
                continue;
            }
            String stripped = Rs2UiHelper.stripColTags(candidate);
            if (stripped.equalsIgnoreCase(wanted)) {
                return stripped;
            }
            if (partial == null && stripped.toLowerCase().contains(needle)) {
                partial = stripped;
            }
        }
        return partial;
    }

    private boolean matchesAction(String action, boolean exact) {
        if (action == null || action.isEmpty()) {
            return false;
        }
        String[] actions = Microbot.getClientThread().invoke(() -> {
            ObjectComposition composition = getVisibleObjectComposition();
            return composition != null ? composition.getActions() : null;
        });
        if (actions == null) {
            return false;
        }
        String needle = action.toLowerCase();
        for (String candidate : actions) {
            if (candidate == null) {
                continue;
            }
            String stripped = Rs2UiHelper.stripColTags(candidate);
            if (exact ? stripped.equalsIgnoreCase(action) : stripped.toLowerCase().contains(needle)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasLineOfSight() {
        return hasLineOfSight(new Rs2PlayerModel().getWorldLocation());
    }

    public boolean hasLineOfSight(WorldPoint from) {
        if (from == null) {
            return false;
        }
        WorldView worldView = getWorldView();
        if (worldView == null) {
            return false;
        }
        WorldArea objectArea;
        WorldArea fromArea;
        if (tileObject instanceof GameObject) {
            objectArea = getWorldArea();
            fromArea = from.toWorldArea();
        } else {
            WorldPoint location = getRuneLiteWorldLocation();
            if (location == null) {
                return false;
            }
            objectArea = new WorldArea(location, 2, 2);
            fromArea = new WorldArea(from.getX(), from.getY(), 2, 2, from.getPlane());
        }
        if (objectArea == null) {
            return false;
        }
        WorldArea target = objectArea;
        return Microbot.getClientThread()
                .runOnClientThreadOptional(() -> target.hasLineOfSightTo(worldView, fromArea))
                .orElse(false);
    }

    public boolean canWalkTo(int distance) {
        WorldArea objectArea = templateArea();
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (objectArea == null || playerLocation == null) {
            return false;
        }
        for (WorldPoint tile : Rs2Tile.getReachableTilesFromTile(playerLocation, distance).keySet()) {
            if (tile.distanceTo(objectArea) < 2) {
                return true;
            }
        }
        return false;
    }

    private @Nullable WorldArea templateArea() {
        if (!(tileObject instanceof GameObject)) {
            WorldPoint location = getRuneLiteWorldLocation();
            return location != null ? new WorldArea(location, 2, 2) : null;
        }
        WorldPoint southWest = getWorldLocation();
        WorldView worldView = getWorldView();
        if (southWest == null || worldView == null) {
            return null;
        }
        WorldPoint template = Microbot.getClientThread().invoke(() -> {
            if (!Microbot.getClient().isInInstancedRegion()) {
                return southWest;
            }
            LocalPoint local = LocalPoint.fromWorld(worldView, southWest);
            return local != null ? WorldPoint.fromLocalInstance(Microbot.getClient(), local) : southWest;
        });
        return new WorldArea(template != null ? template : southWest, sizeX(), sizeY());
    }

    public boolean hover() {
        if (!Rs2AntibanSettings.naturalMouse) {
            if (Rs2AntibanSettings.devDebug) {
                Microbot.log("Natural mouse is not enabled, can't hover");
            }
            return false;
        }
        Rectangle clickbox = Rs2UiHelper.getObjectClickbox(tileObject);
        if (Rs2UiHelper.isFallbackRectangle(clickbox)) {
            return false;
        }
        Point point = Rs2UiHelper.getClickingPoint(clickbox, true);
        Microbot.getNaturalMouse().moveTo(point.getX(), point.getY());
        return true;
    }

    @Override
    public boolean isReachable() {
        WorldView objectWorldView = getWorldView();
        if (objectWorldView == null) {
            return false;
        }

        WorldView playerWorldView = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player player = Microbot.getClient().getLocalPlayer();
            return player != null ? player.getWorldView() : null;
        }).orElse(null);

        if (playerWorldView == null) {
            return false;
        }

        // Objects in different world views are not connected by walking, so there is no path.
        if (objectWorldView.getId() != playerWorldView.getId()) {
            return false;
        }

        // Ask whether a tile BESIDE this object can be stood on - not whether the object's own
        // tile can be. You interact with a booth, a tree or a rock from an adjacent tile and never
        // from the tile it occupies, so a solid object's own tile is never walkable and the
        // IEntity default (Rs2Reachable on getWorldLocation()) answers "no" for every one of them.
        if (tileObject instanceof GameObject) {
            WorldArea area = getWorldArea();
            if (area == null) {
                return false;
            }
            Rs2WorldArea objectArea = new Rs2WorldArea(area);
            List<WorldPoint> interactablePoints = objectArea.getInteractable();
            if (interactablePoints.isEmpty()) {
                interactablePoints.addAll(objectArea.offset(1).toWorldPointList());
                interactablePoints.removeIf(objectArea::contains);
            }
            return interactablePoints.stream()
                    .filter(Rs2Tile::isWalkable)
                    .anyMatch(Rs2Tile::isTileReachable);
        }

        return Rs2Tile.isTileObjectReachable(tileObject);
    }

    public boolean click() {
        return click("");
    }

    private boolean recoverFromCantReach() {
        if (!CantReachTargetRecovery.shouldStart(
                Microbot.isCantReachTargetDetectionEnabled, Microbot.cantReachTarget)) {
            return true;
        }
        if (CantReachTargetRecovery.retryExhausted(
                Microbot.cantReachTargetRetries, Rs2Random.between(3, 5))) {
            int retries = CantReachTargetRecovery.giveUp();
            Microbot.pauseAllScripts.compareAndSet(false, true);
            Microbot.showMessage("Your bot tried to interact with an object for "
                    + retries + " times but failed. Please take a look at what is happening.");
            return false;
        }
        WorldPoint objectLocation = getRuneLiteWorldLocation();
        if (objectLocation == null) {
            return false;
        }
        Microbot.cantReachTargetRetries++;
        Microbot.log("[Interact] can't-reach recovery: walking to object " + getId()
                + " at " + objectLocation + " (attempt " + Microbot.cantReachTargetRetries + ")");
        if (!CantReachTargetRecovery.walkTo(objectLocation, 2)) {
            return false;
        }
        Microbot.pauseAllScripts.compareAndSet(true, false);
        Microbot.cantReachTarget = false;
        Microbot.cantReachTargetRetries = 0;
        return true;
    }

    public WorldPoint getRuneLiteWorldLocation() {
        return Microbot.getClientThread().invoke(() -> tileObject.getWorldLocation());
    }

    int distanceToPlayer() {
        LocalPoint objectLocal = tileObject.getLocalLocation();
        LocalPoint playerLocal = Microbot.getClientThread().invoke(() -> {
            Player player = Microbot.getClient().getLocalPlayer();
            return player != null ? player.getLocalLocation() : null;
        });
        if (playerLocal != null && objectLocal != null && objectLocal.isInScene()) {
            int dx = playerLocal.getSceneX() - objectLocal.getSceneX();
            int dy = playerLocal.getSceneY() - objectLocal.getSceneY();
            return Math.max(Math.abs(dx), Math.abs(dy));
        }
        WorldPoint playerWorld = Rs2Player.getWorldLocation();
        WorldPoint objectWorld = getRuneLiteWorldLocation();
        if (playerWorld == null || objectWorld == null) {
            return -1;
        }
        return playerWorld.distanceTo(objectWorld);
    }

    /**
     * Clicks this object with the given action, or with its first option when the action is null or empty.
     * An action the object does not have is refused rather than replaced by the first option,
     * unless a widget is selected, in which case the selected item or spell is used on the object.
     * Rs2GameObject.clickObject delegates here.
     *
     * @param action the action to perform (e.g., "Open", "Climb")
     * @return true if the interaction was sent, false otherwise
     */
    public boolean click(String action) {
        return click(action, false);
    }

    /**
     * Rs2GameObject's old behaviour: sends the first option when the object does not have the action.
     * Only Rs2GameObject.clickObject should call this; it goes when Rs2GameObject is removed.
     */
    @Deprecated(since = "2.6.44", forRemoval = true)
    public boolean clickOrFirstOption(String action) {
        return click(action, true);
    }

    private boolean click(String action, boolean firstOptionIfMissing) {
        try {
            WorldView worldView = getWorldView();
            if (worldView == null) {
                log.warn("Failed to interact with object {} - object is no longer in a loaded world view", getId());
                return false;
            }

            boolean widgetSelected = Microbot.getClient().isWidgetSelected();
            int index = 0;
            String objName = "";
            if (action != null) {
                //performance improvement to only get compoisiton if action has been specified
                var objComp = getVisibleObjectComposition();
                if (objComp == null) {
                    log.warn("Failed to interact with object {} - object not currently visible", getId());
                    return false;
                }
                String[] actions = objComp.getActions();
                String wanted = Rs2UiHelper.stripColTags(action).trim();

                if (!wanted.isEmpty()) {
                    index = -1;
                    for (int i = 0; i < actions.length; i++) {
                        if (actions[i] == null) continue;
                        if (wanted.equalsIgnoreCase(Rs2UiHelper.stripColTags(actions[i]))) {
                            index = i;
                            break;
                        }
                    }
                    if (index == -1 && !widgetSelected) {
                        if (!firstOptionIfMissing) {
                            log.warn("Failed to interact with object {} ({}) - action '{}' not found, actions are {}",
                                    getId(), objComp.getName(), action, Arrays.toString(actions));
                            return false;
                        }
                        log.warn("Object {} ({}) has no '{}' action, sending its first option; the query API refuses this",
                                getId(), objComp.getName(), action);
                        index = 0;
                    }
                }

                objName = objComp.getName();
            }

            if (!recoverFromCantReach()) {
                return false;
            }

            int distance = distanceToPlayer();
            if (distance < 0) {
                return false;
            }
            if (distance > MAX_INTERACT_DISTANCE) {
                Microbot.log("Object with id " + getId() + " is not close enough to interact with. Walking to the object....");
                Rs2Walker.walkTo(getRuneLiteWorldLocation());
                return false;
            }

            int param0;
            int param1;
            MenuAction menuAction = MenuAction.WALK;


            Microbot.status = action + " " + getName();

            if (getTileObjectType() == TileObjectType.GAME) {
                GameObject obj = (GameObject) tileObject;
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
                param0 = getLocalLocation().getSceneX();
                param1 = getLocalLocation().getSceneY();
            }

            // both hands must be free before using MINECART
            if (objName != null && objName.toLowerCase().contains("train cart")) {
                Rs2Equipment.unEquip(EquipmentInventorySlot.WEAPON);
                Rs2Equipment.unEquip(EquipmentInventorySlot.SHIELD);
                sleepUntil(() -> Rs2Equipment.get(EquipmentInventorySlot.WEAPON) == null && Rs2Equipment.get(EquipmentInventorySlot.SHIELD) == null);
            }

            if (widgetSelected) {
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

            if (!Rs2Camera.isTileOnScreen(getLocalLocation())) {
                Rs2Camera.turnTo(tileObject);
            }


            Microbot.doInvoke(new NewMenuEntry()
                            .param0(param0)
                            .param1(param1)
                            .opcode(menuAction.getId())
                            .identifier(getId())
                            .itemId(-1)
                            .option(action)
                            .target(objName)
                            .setWorldViewId(worldView.getId())
                            .gameObject(tileObject)
                    ,
                    Rs2UiHelper.getObjectClickbox(tileObject));
// MenuEntryImpl(getOption=Use, getTarget=Barrier, getIdentifier=43700, getType=GAME_OBJECT_THIRD_OPTION, getParam0=53, getParam1=51, getItemId=-1, isForceLeftClick=true, getWorldViewId=-1, isDeprioritized=false)
            //Rs2Reflection.invokeMenu(param0, param1, menuAction.getId(), object.getId(),-1, "", "", -1, -1);

        } catch (Exception ex) {
            log.error("Failed to interact with object: ", ex);
            return false;
        }

        return true;
    }

}
