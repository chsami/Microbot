package net.runelite.client.plugins.microbot.thieving.summergarden;

import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.util.Text.sanitize;

enum BotState {
    EXIT_GARDEN,
    COMPLETE_AND_RESET,
    RETURN_TO_HOUSE,
    MAKE_LAST_JUICE,
    RUN
}

// TODO I can replace the findDoor with the exact tile. check the findDoor function.

public class SummerGardenScript extends Script {
    private static final int OUT_OF_SUPPLY_SOUND = 3283; // anma_puzzle_complete
    private static final int INV_FULL_SOUND = 2277;
    private static final String NPC_NAME_OSMAN = "Osman";
    private static final String NPC_NAME_APPRENTICE = "Apprentice";
    private static final int MAX_DISTANCE = 2350;
    private static final WorldArea WORLD_AREA_HOUSE = new WorldArea(3318, 3137, 7, 5, 0);
    private static final WorldPoint WORLD_POINT_OUTSIDE_HOUSE_DOOR = new WorldPoint(3321, 3142, 0);
    private static final WorldPoint WORLD_POINT_INSIDE_HOUSE = new WorldPoint(3321, 3138, 0);
    private static final WorldPoint WORLD_POINT_MAZE_STARTING_LOCATION = new WorldPoint(2910, 5481, 0);
    private static final int REGION_ALKHARID = 13105;
    private static final int REGION_GARDEN = 11605;
    private static final int OBJECT_HOUSE_DOOR_CLOSED = 1535;
    private static final int OBJECT_SUMMER_TREE = 12943;
    private static final int OBJECT_BEER_GLASS_SHELF = 21794;


    private Actor lastInteractedActor = null;
    public boolean sendRs2InventoryFullNotification = false;
    private BotState botState = BotState.RUN;

    public boolean run(SummerGardenConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!super.run()) return;
            try {
                if (Microbot.getClient().getGameState() != GameState.LOGGED_IN) {
                    return;
                }

                Widget clickHereToPlayButton = Rs2Widget.getWidget(24772680); //on login screen
                if (clickHereToPlayButton != null) {
                    return;
                }

                if (config.autoHandInAndReset()) {
                    var interactingActor = Rs2Player.getInteracting();
                    if (interactingActor != null) {
                        lastInteractedActor = interactingActor;
                    }

                    if (botState == BotState.RUN) {
                        if (Rs2Inventory.hasItemAmount("Summer sq'irk", 2, false, true) && Rs2Inventory.count("Summer sq'irkjuice") == 25) {
                            if (isInGarden()) {
                                botState = BotState.EXIT_GARDEN;
                            } else if (isInAlKharid()) {
                                botState = BotState.MAKE_LAST_JUICE;
                            }
                        } else if (Rs2Inventory.hasItem("Summer sq'irkjuice") && isInAlKharid()) {
                            botState = BotState.COMPLETE_AND_RESET;
                        } else if (!Rs2Inventory.hasItem("Summer sq'irk")
                                && !Rs2Inventory.hasItemAmount("Beer glass", 25, false, true)
                                && !Rs2Inventory.hasItem("Summer sq'irkjuice")
                                && isInAlKharid()) {
                            botState = BotState.RETURN_TO_HOUSE;
                        }
                    } else if (botState == BotState.EXIT_GARDEN) {
                        exitGarden();
                    } else if (botState == BotState.MAKE_LAST_JUICE) {
                        makeLastJuice();
                    } else if (botState == BotState.COMPLETE_AND_RESET) {
                        completeAndReset();
                        if (config.sendInvFullNotification() && !sendRs2InventoryFullNotification) {
                            // this line causes a freeze when using dev mode
                            //Microbot.getClient().playSoundEffect(OUT_OF_SUPPLY_SOUND, SoundEffectVolume.HIGH);
                            Microbot.log("You need a beer glass and a pestle and mortar to make sq'irk juice.");
                            sendRs2InventoryFullNotification = true;
                        }
                        return;
                    } else if (botState == BotState.RETURN_TO_HOUSE) {
                        handleReturnToHouse();
                        return;
                    }
                }

                if (config.autoMazeCompletion()) {
                    doMaze(config);
                }
            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }

    private boolean isInAlKharid() {
        return Microbot.getClient().getLocalPlayer().getWorldLocation().getRegionID() == REGION_ALKHARID;
    }

    private boolean isInGarden() {
        return Microbot.getClient().getLocalPlayer().getWorldLocation().getRegionID() == REGION_GARDEN;
    }

    private boolean isInHouseArea() {
        return Microbot.getClient().getLocalPlayer().getWorldLocation().isInArea(WORLD_AREA_HOUSE);
    }

    private WallObject getHouseDoor() {
        //var houseDoorTest = Rs2GameObject.findDoor(OBJECT_HOUSE_DOOR_CLOSED);

        LocalPoint doorLp = LocalPoint.fromWorld(Microbot.getClient(), 3321, 3142);
        if (doorLp == null) {
            return null;
        }

        Scene scene = Microbot.getClient().getScene();
        var doorTile = scene.getTiles()[0][doorLp.getSceneX()][doorLp.getSceneY()];
        if (doorTile == null) {
            return null;
        }

        WallObject wall = doorTile.getWallObject();
        if (wall != null && wall.getId() == OBJECT_HOUSE_DOOR_CLOSED) {
            return wall;
        }

        return null;
    }

    // Returns true if successfully entered the house.
    private boolean goInsideHouse() {
        // The player is not in Al Kharid or is somehow upstairs?
        if (!isInAlKharid() || Microbot.getClient().getPlane() != 0) {
            return false;
        }

        // If the player is outside the house then attempt to open the door (if it's closed) and go in
        if (!isInHouseArea()) {
            // The door is closed and the player is outside the house area. Open it and wait until it's open.
            if (getHouseDoor() != null) {
                Rs2GameObject.interact(OBJECT_HOUSE_DOOR_CLOSED);
                sleepUntil(() -> getHouseDoor() == null, 10000);
            }

            // The door is still closed?
            if (getHouseDoor() != null) {
                return false;
            }

            Rs2Walker.walkFastCanvas(WORLD_POINT_INSIDE_HOUSE);
            sleepUntil(() -> isInHouseArea(), 5000);
        }

        return isInHouseArea();
    }

    private void exitGarden() {
        // Can't find the fountain?
        if (Rs2GameObject.findObjectById(12941) == null) {
            return;
        }

        // If the player is still in the garden then click the fountain to exit and wait until the player is teleported out.
        if (isInGarden()) {
            Rs2GameObject.interact(12941);
            sleepUntil(() -> isInHouseArea(), 10000);
        }

        // The player is not in Al Kharid or in the house
        if (!isInAlKharid() || !isInHouseArea()) {
            return;
        }

        if (Rs2Inventory.hasItemAmount("Summer sq'irk", 2, false, true)) {
            botState = BotState.MAKE_LAST_JUICE;
        } else {
            botState = BotState.COMPLETE_AND_RESET;
        }
    }

    private void doMaze(SummerGardenConfig config) {
        // If the player's Rs2Inventory is full and there's no empty beer glasses, or if the player isn't in the garden
        if ((Rs2Inventory.isFull() && !Rs2Inventory.hasItem("beer glass")) || !isInGarden()) {
            return;
        }

        // Use sq'irk with pestle and mortar
        if (Rs2Inventory.hasItemAmount("Summer sq'irk", 2, false, true)) {
            if (Rs2Inventory.hasItem("beer glass") && Rs2Inventory.hasItem("Pestle and mortar")) {
                Rs2Inventory.use("Pestle and mortar");
                Rs2Inventory.use("Summer sq'irk");
                sendRs2InventoryFullNotification = false;
            }
        }

        // Click tree
        if (Microbot.getClient().getLocalPlayer().getWorldLocation().equals(WORLD_POINT_MAZE_STARTING_LOCATION)) {
            if (config.waitForOneClick() || ElementalCollisionDetector.getTicksUntilStart() == 0) {
                Rs2GameObject.interact(OBJECT_SUMMER_TREE);
                sleepUntil(() -> Rs2Player.isMoving());
                sleepUntil(() -> !Rs2Player.isMoving(), 30000);
                sleepUntilOnClientThread(() -> Microbot.getClient().getLocalPlayer().getWorldLocation().getY() < 5481);
                sleep(1500);//caught or success timeout
            }
            return;
        }

        // The player is inside the garden so the gate doesn't need to be clicked.
        if (Microbot.getClient().getLocalPlayer().getWorldLocation().getY() >= 5481) {
            return;
        }

        // Click Gate
        TileObject gate = Rs2GameObject.findObjectById(ObjectID.GATE_11987);
        if (gate != null) {
            Rs2GameObject.interact(gate);
            sleepUntil(Rs2Player::isMoving);
            sleepUntil(() -> !Rs2Player.isMoving());
            sleepUntilOnClientThread(() -> Microbot.getClient().getLocalPlayer().getWorldLocation().equals(WORLD_POINT_MAZE_STARTING_LOCATION));
        }
    }

    private void completeAndReset() {
        var osmanLocation = new WorldPoint(3296, 3181, 0);

        // The player is not in Al Kharid or is somehow upstairs?
        if (!isInAlKharid() || Microbot.getClient().getPlane() != 0) {
            return;
        }

        // The player doesn't have any juice to hand in.
        if (!Rs2Inventory.hasItem("Summer sq'irkjuice")) {
            botState = BotState.RETURN_TO_HOUSE;
            return;
        }

        // The door is closed and the player is inside the house area. Open it and wait until it's open.
        if (getHouseDoor() != null && isInHouseArea()) {
            Rs2GameObject.interact(OBJECT_HOUSE_DOOR_CLOSED);
            sleepUntil(() -> getHouseDoor() == null, 10000);
        }

        // Check if the player has arrived at Osman's location, if not then walk there.
        var npcOsman = Rs2Npc.getNpc(NPC_NAME_OSMAN);
        if (npcOsman == null) {
            var osmanLocalLocation = LocalPoint.fromWorld(Microbot.getClient(), osmanLocation);
            if (osmanLocalLocation != null) {
                Rs2Walker.walkTo(osmanLocation);
                return;
            }
            return;
        }

        // Interact with Osman.
        if (lastInteractedActor == null || !Objects.equals(lastInteractedActor.getName(), NPC_NAME_OSMAN)) {
            Rs2Npc.interact(NPC_NAME_OSMAN, "Talk-to");
            sleepUntil(() -> Rs2Player.getInteracting() != null, 2000);
            return;
        }

        // If the player is interacting with Osman then handle the dialogue.
        if (Objects.equals(lastInteractedActor.getName(), NPC_NAME_OSMAN)) {
            var npcDialogueText = Microbot.getClient().getWidget(WidgetInfo.DIALOG_NPC_TEXT);
            if (npcDialogueText != null && npcDialogueText.getText().equals("Hello again.")) {
                if (Rs2Widget.hasWidget("Click here to continue")) {
                    Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                }
            }

            //sleepUntil(() -> Microbot.getClient().getWidget(WidgetInfo.DIALOG_OPTION_OPTIONS) != null, 2000);
            var playerDialogueOptionsWidget = Microbot.getClient().getWidget(WidgetInfo.DIALOG_OPTION_OPTIONS);
            if (playerDialogueOptionsWidget != null) {
                var dialogueOptions = playerDialogueOptionsWidget.getChildren();
                if (dialogueOptions != null) {
                    if (sanitize(dialogueOptions[0].getText()).equals("Select an Option") && sanitize(dialogueOptions[1].getText()).equals("I'd like to talk about sq'irks.")) {
                        Rs2Widget.clickWidget("I'd like to talk about sq'irks.");
                    }
                }
            }

            //sleepUntil(() -> Microbot.getClient().getWidget(WidgetInfo.DIALOG_PLAYER_TEXT) != null, 2000);
            var playerDialogueText = Microbot.getClient().getWidget(WidgetInfo.DIALOG_PLAYER_TEXT);
            if (playerDialogueText != null) {
                if (playerDialogueText.getText().equals("I'd like to talk about sq'irks.") || playerDialogueText.getText().equals("I have some sq'irk juice for you.")) {
                    if (Rs2Widget.hasWidget("Click here to continue")) {
                        Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                    }
                }
            }

            //sleepUntil(() -> Microbot.getClient().getWidget(WidgetInfo.DIALOG_SPRITE_TEXT) != null, 2000);
            var npcDialogueSpriteText = Microbot.getClient().getWidget(WidgetInfo.DIALOG_SPRITE_TEXT);
            if (npcDialogueSpriteText != null) {
                if (npcDialogueSpriteText.getText().contains("Osman imparts some Thieving advice to you as a<br>reward for the sq'irk juice.")) {
                    if (Rs2Widget.hasWidget("Click here to continue")) {
                        Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                        botState = BotState.RETURN_TO_HOUSE;
                    }
                }
            }
        }
    }

    private void handleReturnToHouse() {
        // If the player isn't inside the house then walk to the door, outside the house.
        if (!isInHouseArea()) {
            var outsideHouseDoorLocalLocation = LocalPoint.fromWorld(Microbot.getClient(), WORLD_POINT_OUTSIDE_HOUSE_DOOR);
            if (outsideHouseDoorLocalLocation != null && Microbot.getClient().getLocalPlayer().getLocalLocation().distanceTo(outsideHouseDoorLocalLocation) >= MAX_DISTANCE) {
                Rs2Walker.walkTo(WORLD_POINT_OUTSIDE_HOUSE_DOOR);
                return;
            }
        }

        // The player isn't inside the house.
        if (!goInsideHouse()) {
            return;
        }

        // Interact with shelf to get beer glass.
        while (Rs2Inventory.count("Beer glass") < 25) {
            Rs2GameObject.interact(OBJECT_BEER_GLASS_SHELF);
            sleep(3000);
        }

        if (!Rs2Inventory.hasItemAmount("Beer glass", 25, false, true)) {
            if (Rs2Inventory.count("Beer glass") > 25) {
                Rs2Inventory.drop("Beer glass");
                sleepUntil(() -> Rs2Inventory.count("Beer glass") == 25, 2000);
            }
            return;
        }

        // Check if the player has arrived at the Apprentice's location.
        var npcApprentice = Rs2Npc.getNpc(NPC_NAME_APPRENTICE);
        if (npcApprentice == null) {
            return;
        }

        // Interact with the apprentice.
        if (lastInteractedActor == null || !Objects.equals(lastInteractedActor.getName(), NPC_NAME_APPRENTICE)) {
            Rs2Npc.interact(NPC_NAME_APPRENTICE, "Teleport");
            sleepUntil(() -> isInGarden(), 10000);
        }

        // If the player is in the garden then this part is completed.
        if (isInGarden()) {
            botState = BotState.RUN;
        }
    }

    private void makeLastJuice() {
        if (!Rs2Inventory.hasItemAmount("Summer sq'irk", 2, false, true)) {
            botState = BotState.COMPLETE_AND_RESET;
            return;
        }

        // The player isn't inside the house.
        if (!goInsideHouse()) {
            return;
        }

        if (Rs2Inventory.isFull()) {
            Rs2Inventory.drop("Summer sq'irkjuice");
            sleepUntil(() -> !Rs2Inventory.isFull(), 2000);
        }

        if (!Rs2Inventory.hasItemAmount("Beer glass", 1, false, true)) {
            if (Rs2Inventory.count("Beer glass") == 0) {
                Rs2GameObject.interact(OBJECT_BEER_GLASS_SHELF);
                sleepUntil(() -> Rs2Inventory.hasItem("beer glass"), 5000);
                sleep(3000);
            }

            if (Rs2Inventory.count("Beer glass") > 1) {
                Rs2Inventory.drop("Beer glass");
                sleepUntil(() -> Rs2Inventory.count("Beer glass") == 1, 5000);
            }
        }

        if (!Rs2Inventory.hasItem("beer glass")) {
            return;
        }

        if (Rs2Inventory.hasItem("beer glass") && Rs2Inventory.hasItem("Pestle and mortar")) {
            Rs2Inventory.use("Pestle and mortar");
            Rs2Inventory.use("Summer sq'irk");
            sleepUntil(() -> Rs2Inventory.count("Beer glass") == 0, 2000);
        }

        while (Rs2Inventory.count("Summer sq'irkjuice") != 26) {
            Rs2GroundItem.pickup("Summer sq'irkjuice", 200);
            sleepUntil(() -> Rs2Inventory.hasItemAmount("Summer sq'irk", 26, false, true), 5000);
            sleep(3000);
        }

        botState = BotState.COMPLETE_AND_RESET;
    }
}
