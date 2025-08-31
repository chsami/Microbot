package net.runelite.client.plugins.microbot.GeoffPlugins.construction2;

import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.GeoffPlugins.construction2.enums.Construction2State;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Construction2Script extends Script {

    private static final int DEFAULT_DELAY = 600;
    private Construction2State state = Construction2State.Idle;
    private WorldPoint workingTile = null;

    // NOTE: For the arrays below, the first ID is the BUILD OBJECT ID, the second is the EMPTY OBJECT ID
    private static final List<Integer> OAK_DUNGEON_DOOR = List.of(13344, 15328);
    private static final List<Integer> OAK_LARDER = List.of(13566, 15403);
    private static final List<Integer> MAHOGANY_TABLE = List.of(13298, 15298);
    private static final List<Integer> MYTHICAL_CAPE_MOUNT = List.of(15394, 31986);

    private static List<Integer> objectIDs = List.of(0);

    public static boolean firstRun = false;

    public GameObject getClosestTileGameObject(List<Integer> objIDs) {
        List<GameObject> objects = Rs2GameObject.getGameObjects();
        GameObject closest = null;
        WorldPoint playerLocation = Rs2Player.getWorldLocation();

        for (GameObject obj : objects) {
            if (objIDs.contains(obj.getId())) {
                if (closest == null || Rs2Walker.getDistanceBetween(playerLocation, obj.getWorldLocation()) < Rs2Walker.getDistanceBetween(playerLocation, closest.getWorldLocation())) {
                    closest = obj;
                }
            }
        }
        return closest;
    }

    public TileObject getClosestTileTileObject(List<Integer> objIDs) {
        List<TileObject> tileObjects = Rs2GameObject.getTileObjects();
        TileObject closest = null;
        WorldPoint playerLocation = Rs2Player.getWorldLocation();

        for (TileObject obj : tileObjects) {
            if (objIDs.contains(obj.getId())) {
                if (closest == null || Rs2Walker.getDistanceBetween(playerLocation, obj.getWorldLocation()) < Rs2Walker.getDistanceBetween(playerLocation, closest.getWorldLocation())) {
                    closest = obj;
                }
            }
        }
        return closest;
    }

    public Rs2NpcModel getButler() {
        return Rs2Npc.getNpc("Demon butler");
    }

    public boolean hasDialogueOptionToUnnote() {
        return Rs2Widget.findWidget("Un-note", null) != null;
    }

    public boolean hasDialogueRepeatLastTask() { return Rs2Widget.hasWidget("Repeat last task?"); }

    public boolean hasPayButlerDialogue() {
        return Rs2Widget.findWidget("must render unto me the 10,000 coins that are due", null) != null;
    }

    public boolean hasDialogueOptionToPay() {
        return Rs2Widget.findWidget("Okay, here's 10,000 coins.", null) != null;
    }

    public boolean hasFurnitureInterfaceOpen() {
        Widget furnitureWidget = Rs2Widget.findWidget("Furniture", null);
        if (furnitureWidget != null) {
            System.out.println("Furniture interface is open.");
            return true;
        }
        System.out.println("Furniture interface is not open.");
        return false;
    }

    public boolean hasRemoveDoorInterfaceOpen() {
        return Rs2Widget.findWidget("Really remove it?", null) != null;
    }

    public boolean hasRemoveLarderInterfaceOpen() {
        return Rs2Widget.findWidget("Really remove it?", null) != null;
    }

    public boolean hasRemoveTableInterfaceOpen() {
        return Rs2Widget.findWidget("Really remove it?", null) != null;
    }

    public boolean hasRemoveCapeMountInterfaceOpen() {
        return Rs2Widget.findWidget("Really remove it?", null) != null;
    }

    public boolean run(Construction2Config config) {
        int actionDelay = config.useCustomDelay() ? config.actionDelay() : DEFAULT_DELAY;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                Rs2Tab.switchTo(InterfaceTab.INVENTORY);
                calculateState(config);
                switch (state) {
                    case Build:
                        grabPlanksWhileWeBuild(config, actionDelay);
                        buildSpace(config, actionDelay);
                        break;
                    case Remove:
                        removeSpace(config, actionDelay);
                        break;
                    case Butler:
                        grabPlanksWhileWeBuild(config, actionDelay);
                        break;
                    default:
                        break;
                }
            } catch (Exception ex) {
                System.out.println("Error in scheduled task: " + ex.getMessage());
            }
        }, 0, actionDelay, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }

    public void grabPlanksWhileWeBuild(Construction2Config config, int actionDelay){
        if(getButler() != null) {
            sleepUntil(()-> getButler() != null && getButler().isInteractingWithPlayer(), Rs2Random.between(750,1500));
            if(!getButler().isInteractingWithPlayer()){
                if (Rs2Inventory.count(config.selectedMode().getPlankItemId()) <= Rs2Random.between(10, 18)) butler(config, actionDelay);
            } else {
                butler(config, actionDelay);
            }
        } else {
            if(firstRun){
                callTheButler();
                if(getButler() != null) firstRun = false;
            }
        }
    }

    private void calculateState(Construction2Config config) {
        boolean hasRequiredPlanks;
        NPC butler = getButler();
        switch (config.selectedMode()) {
            case OAK_DUNGEON_DOOR:
                objectIDs = OAK_DUNGEON_DOOR;
                hasRequiredPlanks =  Rs2Inventory.hasItemAmount(config.selectedMode().getPlankItemId(), 10);
                break;
            case OAK_LARDER:
                objectIDs = OAK_LARDER;
                hasRequiredPlanks =  Rs2Inventory.hasItemAmount(config.selectedMode().getPlankItemId(), 8);
                break;
            case MAHOGANY_TABLE:
                objectIDs = MAHOGANY_TABLE;
                hasRequiredPlanks =  Rs2Inventory.hasItemAmount(config.selectedMode().getPlankItemId(), 6);
                break;
            default:
                return;
        }

        if (workingTile == null) {
            if(!objectIDs.equals(OAK_DUNGEON_DOOR)){
                workingTile = getClosestTileGameObject(objectIDs).getWorldLocation();
            } else {
                workingTile = getClosestTileTileObject(objectIDs).getWorldLocation();
            }
        }

        GameObject objOnWorkingTile = null;
        TileObject tileObjOnWorkingTile = null;
        if(!objectIDs.equals(OAK_DUNGEON_DOOR)){
            objOnWorkingTile = Rs2GameObject.getGameObject(workingTile);
        } else {
            tileObjOnWorkingTile = Rs2GameObject.getTileObject(workingTile);
        }

        if (objOnWorkingTile == null && tileObjOnWorkingTile == null) {
            // Find new working tile
            if(!objectIDs.equals(OAK_DUNGEON_DOOR)){
                workingTile = getClosestTileGameObject(objectIDs).getWorldLocation();
                objOnWorkingTile = Rs2GameObject.getGameObject(workingTile);
            } else {
                workingTile = getClosestTileTileObject(objectIDs).getWorldLocation();
                tileObjOnWorkingTile = Rs2GameObject.getTileObject(workingTile);
            }
        }

        if(!objectIDs.equals(OAK_DUNGEON_DOOR)){
            if (objOnWorkingTile.getId() == objectIDs.get(0)) {
                state = Construction2State.Remove;
            } else if (objOnWorkingTile.getId() == objectIDs.get(1) && hasRequiredPlanks) {
                state = Construction2State.Build;
            } else if (objOnWorkingTile.getId() == objectIDs.get(1)) {
                state = Construction2State.Butler;
            } else if (!objectIDs.contains(objOnWorkingTile.getId())) {
                state = Construction2State.Idle;
                Microbot.getNotifier().notify("Looks like we are no longer in our house.");
                returnToTheHouse(objectIDs);
            }
        } else {
            if (tileObjOnWorkingTile.getId() == objectIDs.get(0)) {
                state = Construction2State.Remove;
            } else if (tileObjOnWorkingTile.getId() == objectIDs.get(1) && hasRequiredPlanks) {
                state = Construction2State.Build;
            } else if (tileObjOnWorkingTile.getId() == objectIDs.get(1)) {
                state = Construction2State.Butler;
            } else if (!objectIDs.contains(tileObjOnWorkingTile.getId())) {
                state = Construction2State.Idle;
                Microbot.getNotifier().notify("Looks like we are no longer in our house.");
                returnToTheHouse(objectIDs);
            }
        }
    }

    private void returnToTheHouse(List<Integer> objIDs){
        GameObject housePortal = Rs2GameObject.getGameObject("Portal");
        firstRun = true; //we're returning from break we'll need to recall the butler
        if(housePortal != null){
            if(Rs2GameObject.interact(housePortal, "Build mode")){
                sleepUntil(()-> Rs2Player.getWorldLocation() != null
                        && Rs2Player.getWorldLocation().getRegionX() == 29
                            && Rs2Player.getWorldLocation().getRegionY() == 89, Rs2Random.between(10000,20000));
                sleep(2000,5000);
            }
            if(objIDs.equals(OAK_DUNGEON_DOOR)){
                // We need to enter the dungeon
                if(Rs2GameObject.getGameObject("Dungeon entrance", true) !=null){
                    if(Rs2GameObject.interact("Dungeon entrance", "Enter", true)){
                        sleepUntil(()-> Rs2Player.isMoving(), Rs2Random.between(500,1000));
                        sleepUntil(()-> !Rs2Player.isMoving(), Rs2Random.between(10000,15000));
                    }
                }
            }
        } else {
            Microbot.getNotifier().notify("Can't find the house portal!");
            shutdown();
        }
    }

    private void buildSpace(Construction2Config config, int actionDelay) {
        GameObject gameObjSpace = Rs2GameObject.getGameObject(workingTile);
        TileObject tileObjSpace = Rs2GameObject.getTileObject(workingTile);
        if(!objectIDs.equals(OAK_DUNGEON_DOOR)){
            GameObject space = gameObjSpace;
            int spaceId = space != null ? space.getId() : -1;
            char buildKey = '1';

            switch (config.selectedMode()) {
                case OAK_DUNGEON_DOOR:
                    buildKey = '1';
                    break;
                case OAK_LARDER:
                    buildKey = '2';
                    break;
                case MAHOGANY_TABLE:
                    buildKey = '6';
                    break;
                // case MYTHICAL_CAPE:
                //     buildKey = '4';
                //     break;
                default:
                    return;
            }

            if (space == null) return;
            if (Rs2GameObject.interact(space, "Build")) {
                System.out.println("Interacted with build space: " + space.getId());
                sleepUntilOnClientThread(this::hasFurnitureInterfaceOpen, 2500);
                System.out.println("Pressing key: " + buildKey);
                Rs2Keyboard.keyPress(buildKey); // Ensure this is the correct key for the selected build option
                sleepUntilOnClientThread(() -> spaceId != space.getId(), 2500);
                System.out.println("Built object: " + config.selectedMode());
            } else {
                System.out.println("Failed to interact with build space: " + space.getId());
            }
        } else {
            TileObject space = tileObjSpace;
            int spaceId = space != null ? space.getId() : -1;
            char buildKey = '1';

            switch (config.selectedMode()) {
                case OAK_DUNGEON_DOOR:
                    buildKey = '1';
                    break;
                case OAK_LARDER:
                    buildKey = '2';
                    break;
                case MAHOGANY_TABLE:
                    buildKey = '6';
                    break;
                // case MYTHICAL_CAPE:
                //     buildKey = '4';
                //     break;
                default:
                    return;
            }

            if (space == null) return;
            if (Rs2GameObject.interact(space, "Build")) {
                System.out.println("Interacted with build space: " + space.getId());
                sleepUntilOnClientThread(this::hasFurnitureInterfaceOpen, 2500);
                System.out.println("Pressing key: " + buildKey);
                Rs2Keyboard.keyPress(buildKey); // Ensure this is the correct key for the selected build option
                sleepUntilOnClientThread(() -> spaceId != space.getId(), 2500);
                System.out.println("Built object: " + config.selectedMode());
            } else {
                System.out.println("Failed to interact with build space: " + space.getId());
            }
        }
    }

    private void removeSpace(Construction2Config config, int actionDelay) {
        GameObject gameObjSpace = Rs2GameObject.getGameObject(workingTile);
        TileObject tileObjSpace = Rs2GameObject.getTileObject(workingTile);

        if(!objectIDs.equals(OAK_DUNGEON_DOOR)){
            GameObject builtObject = gameObjSpace;
            int spaceId = builtObject != null ? builtObject.getId() : -1;

            if (builtObject == null) return;
            if(builtObject.getId() == 15328 || builtObject.getId() == 15403 || builtObject.getId() == 15298 || builtObject.getId() == 31986) return;

            if (Rs2GameObject.interact(builtObject, "Remove")) {
                System.out.println("Interacted with remove option: " + builtObject.getId());
                sleepUntilOnClientThread(() -> hasRemoveInterfaceOpen(config), 2500);
                Rs2Keyboard.keyPress('1');
                sleepUntilOnClientThread(() -> spaceId != builtObject.getId(), 2500);
                System.out.println("Removed object: " + config.selectedMode());
            } else {
                System.out.println("Failed to interact with remove option: " + builtObject.getId());
            }
        } else {
            TileObject builtObject = tileObjSpace;
            int spaceId = builtObject != null ? builtObject.getId() : -1;

            if (builtObject == null) return;
            if(builtObject.getId() == 15328 || builtObject.getId() == 15403 || builtObject.getId() == 15298 || builtObject.getId() == 31986) return;

            if (Rs2GameObject.interact(builtObject, "Remove")) {
                System.out.println("Interacted with remove option: " + builtObject.getId());
                sleepUntilOnClientThread(() -> hasRemoveInterfaceOpen(config), 2500);
                Rs2Keyboard.keyPress('1');
                sleepUntilOnClientThread(() -> spaceId != builtObject.getId(), 2500);
                System.out.println("Removed object: " + config.selectedMode());
            } else {
                System.out.println("Failed to interact with remove option: " + builtObject.getId());
            }
        }
    }

    private void butler(Construction2Config config, int actionDelay) {
        var butler = getButler();
        if(butler == null) callTheButler();
        boolean butlerIsInteracting = butler.isInteractingWithPlayer();

        if (!butlerIsInteracting) {
            callTheButler();
        }

        if (Rs2Dialogue.isInDialogue() || Rs2Npc.interact(butler, "Talk-to")) {
            sleep(500);
            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
            sleep(400, 1000);
            if (Rs2Widget.findWidget("Go to the bank...", null) != null) {
                Rs2Inventory.useItemOnNpc(config.selectedMode().getPlankItemId() + 1, butler.getId()); // + 1 for noted item
                sleepUntilOnClientThread(() -> Rs2Widget.hasWidget("Dost thou wish me to exchange that certificate"));
                Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                sleepUntilOnClientThread(() -> Rs2Widget.hasWidget("Select an option"));
                Rs2Keyboard.typeString("1");
                sleepUntilOnClientThread(() -> Rs2Widget.hasWidget("Enter amount:"));
                Rs2Keyboard.typeString("28");
                Rs2Keyboard.enter();
            } else if (hasDialogueOptionToUnnote()) {
                Rs2Keyboard.keyPress('1');
                sleepUntilOnClientThread(() -> !hasDialogueOptionToUnnote());
            } else if (hasPayButlerDialogue() || hasDialogueOptionToPay()) {
                Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                sleep(400, 1000);
                if (hasDialogueOptionToPay()) {
                    Rs2Keyboard.keyPress('1');
                }
            } else if(hasDialogueRepeatLastTask()){
                Rs2Keyboard.keyPress('1');
            }
        }
    }

    public void callTheButler(){
        Rs2Tab.switchTo(InterfaceTab.SETTINGS);

        Widget houseOptionWidget = Rs2Widget.getWidget(7602207);
        sleepUntil(()-> houseOptionWidget != null, Rs2Random.between(2000,5000));
        if (houseOptionWidget != null) Rs2Widget.clickWidget(houseOptionWidget);

        sleep(1000,2000); // allow the client to load the widget

        Widget callServantWidget = Rs2Widget.getWidget(370,22);
        sleepUntil(()-> callServantWidget != null, Rs2Random.between(2000,5000));
        if (callServantWidget != null) Rs2Widget.clickWidget(callServantWidget);

        sleepUntil(()-> Rs2Dialogue.isInDialogue(), Rs2Random.between(2000,5000));
    }

    private boolean hasRemoveInterfaceOpen(Construction2Config config) {
        switch (config.selectedMode()) {
            case OAK_DUNGEON_DOOR:
                return hasRemoveDoorInterfaceOpen();
            case OAK_LARDER:
                return hasRemoveLarderInterfaceOpen();
            case MAHOGANY_TABLE:
                return hasRemoveTableInterfaceOpen();
            // case MYTHICAL_CAPE:
            // return hasRemoveCapeMountInterfaceOpen();
            default:
                return false;
        }
    }

    public Construction2State getState() {
        return state;
    }
}
