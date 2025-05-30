package net.runelite.client.plugins.microbot.util.gameobject;

import net.runelite.api.Tile;
import net.runelite.api.WallObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.MenuAction;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import java.util.Arrays;
import java.util.Objects;

public class Rs2WallObject {
    public static WallObject findWallObjectById(int id) {
        return Arrays.stream(Microbot.getClient().getScene().getTiles()[Microbot.getClient().getPlane()])
                .flatMap(Arrays::stream)
                .filter(Objects::nonNull)
                .map(Tile::getWallObject)
                .filter(Objects::nonNull)
                .filter(wall -> wall.getId() == id)
                .findFirst()
                .orElse(null);
    }

    public static boolean interact(WallObject wallObject) {
        return interact(wallObject, 1);
    }

    public static boolean interact(WallObject wallObject, int attempts) {
        if (wallObject == null) return false;
        if (Microbot.getClient().getLocalPlayer().getWorldLocation().distanceTo(wallObject.getWorldLocation()) > 51) {
            Microbot.log("WallObject with id " + wallObject.getId() + " is not close enough to interact with. Walking to the object....");
            Rs2Walker.walkTo(wallObject.getWorldLocation());
            return false;
        }
        try {
            ObjectComposition objComp = Rs2GameObject.convertToObjectComposition(wallObject);
            if (objComp == null) return false;
            Microbot.status = "Interact " + objComp.getName();
            MenuAction menuAction = MenuAction.GAME_OBJECT_FIRST_OPTION;
            if (!Rs2Camera.isTileOnScreen(wallObject.getLocalLocation())) {
                Rs2Camera.turnTo(wallObject);
            }
            for (int i = 0; i < attempts; i++) {
                Microbot.doInvoke(new NewMenuEntry(
                        wallObject.getLocalLocation().getSceneX(),
                        wallObject.getLocalLocation().getSceneY(),
                        menuAction.getId(),
                        wallObject.getId(),
                        -1,
                        "",
                        objComp.getName(),
                        wallObject
                ), Rs2UiHelper.getObjectClickbox(wallObject));
                try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            }
        } catch (Exception ex) {
            Microbot.log("Failed to interact with wall object " + ex.getMessage());
        }
        return true;
    }
} 