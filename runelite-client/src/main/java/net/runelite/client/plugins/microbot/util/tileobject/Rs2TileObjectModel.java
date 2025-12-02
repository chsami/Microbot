package net.runelite.client.plugins.microbot.util.tileobject;

import lombok.Getter;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;


public class Rs2TileObjectModel implements TileObject {

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
        this.tileObject = tileObject;
        this.tileObjectType = TileObjectType.GENERIC;
    }

    @Getter
    private final TileObjectType tileObjectType;
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
        return tileObject.getWorldLocation();
    }

    public String getName() {
        return Microbot.getClientThread().invoke(() -> {
            ObjectComposition composition = Microbot.getClient().getObjectDefinition(tileObject.getId());
            if(composition.getImpostorIds() != null)
            {
                composition = composition.getImpostor();
            }
            if(composition == null)
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
        return tileObject.getClickbox();
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
            if(composition.getImpostorIds() != null)
            {
                composition = composition.getImpostor();
            }
            return composition;
        });
    }

    /**
     * Clicks on the specified tile object with no specific action.
     * Delegates to Rs2GameObject.clickObject.
     *
     * @param action the action to perform (e.g., "Open", "Climb")
     * @return true if the interaction was successful, false otherwise
     */
    public boolean click(String action) {
        if (Microbot.getClient().getLocalPlayer().getWorldLocation().distanceTo(getWorldLocation()) > 51) {
            Microbot.log("Object with id " + getId() + " is not close enough to interact with. Walking to the object....");
            Rs2Walker.walkTo(getWorldLocation());
            return false;
        }

        try {

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


            int index = 0;
            String objName = "";
            if (action != null) {
                //performance improvement to only get compoisiton if action has been specified
                var objComp = getObjectComposition();
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

                objName = objComp.getName();

                // both hands must be free before using MINECART
                if (objComp.getName().toLowerCase().contains("train cart")) {
                    Rs2Equipment.unEquip(EquipmentInventorySlot.WEAPON);
                    Rs2Equipment.unEquip(EquipmentInventorySlot.SHIELD);
                    sleepUntil(() -> Rs2Equipment.get(EquipmentInventorySlot.WEAPON) == null && Rs2Equipment.get(EquipmentInventorySlot.SHIELD) == null);
                }
            }

            if (index == -1) {
                Microbot.log("Failed to interact with object " + getId() + " " + action);
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
                    .gameObject(tileObject)
                    ,
                Rs2UiHelper.getObjectClickbox(tileObject));
// MenuEntryImpl(getOption=Use, getTarget=Barrier, getIdentifier=43700, getType=GAME_OBJECT_THIRD_OPTION, getParam0=53, getParam1=51, getItemId=-1, isForceLeftClick=true, getWorldViewId=-1, isDeprioritized=false)
            //Rs2Reflection.invokeMenu(param0, param1, menuAction.getId(), object.getId(),-1, "", "", -1, -1);

        } catch (Exception ex) {
            Microbot.log("Failed to interact with object " + ex.getMessage());
        }

        return true;
    }

}
