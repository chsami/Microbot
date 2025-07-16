package net.runelite.client.plugins.microbot.construction;

import net.runelite.api.ItemID;
import net.runelite.api.NPC;
import net.runelite.api.SpriteID;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.construction.enums.ConstructionState;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.concurrent.TimeUnit;


public class ConstructionScript extends Script {

    ConstructionPlugin plugin;

    ConstructionState state = ConstructionState.Idle;

    public TileObject getOakLarderSpace() {
        return Rs2GameObject.getGameObject(15403);
    }

    public TileObject getOakLarder() {
        return Rs2GameObject.getGameObject(13566);
    }

    public NPC getButler() {
        return Rs2Npc.getNpc("Demon butler");
    }

    public NPC getPhials() { return Rs2Npc.getNpc("Phials"); }

    private final int HOUSE_PORTAL_OBJECT = 4525;

    private final int OUTSIDE_HOUSE_PORTAL_OBJECT = 15478;

    public boolean hasFurnitureInterfaceOpen() {
        return Rs2Widget.findWidget("Furniture", null) != null;
    }

    public boolean run(ConstructionConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                Rs2Tab.switchToInventoryTab();
                calculateState(config);
                if (state == ConstructionState.Build) {
                    build();
                } else if (state == ConstructionState.Remove) {
                    remove();
                } else if (state == ConstructionState.Butler) {
                    butler();
                }
                else if (state == ConstructionState.Phials) {
                    phials();
                }
                //Microbot.log(hasPayButlerDialogue());
            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }

    private void calculateState(ConstructionConfig config) {
        TileObject oakLarderSpace = getOakLarderSpace();
        TileObject oakLarder = getOakLarder();
        NPC butler = getButler();
        boolean hasRequiredPlanks = Rs2Inventory.hasItemAmount(ItemID.OAK_PLANK, Rs2Random.between(7, 16));

        // 1. FIRST: Handle error states
        if (oakLarderSpace == null && oakLarder == null) {
            state = ConstructionState.Idle;
            Microbot.getNotifier().notify("Looks like we are no longer in our house.");
            shutdown();
            return;
        }

        // 2. SECOND: Handle removal (highest priority - clear obstacles)
        if (oakLarderSpace == null && oakLarder != null) {
            state = ConstructionState.Remove;
            return;
        }

        // 3. THIRD: Handle building when we have materials
        if (oakLarderSpace != null && oakLarder == null && hasRequiredPlanks) {
            state = ConstructionState.Build;
            return;
        }

        // 4. FOURTH: Handle resource gathering - use configured method
        if (oakLarderSpace != null && oakLarder == null && !hasRequiredPlanks) {
            if (config.usePhials()) {
                state = ConstructionState.Phials;
                return;
            } else if (butler != null) {
                state = ConstructionState.Butler;
                return;
            }
        }

        // 5. DEFAULT: Idle state
        state = ConstructionState.Idle;
    }

    public void leaveHouse() {
        Microbot.log("Attempting to leave house...");

        Rs2Tab.switchToSettingsTab();
        sleep(1200);

        String[] actions = Rs2Widget.getWidget(7602235).getActions(); // 116.59
        boolean isControlsInterfaceVisible = actions != null && actions.length == 0;
        if (!isControlsInterfaceVisible) {
            Rs2Widget.clickWidget(7602235);
            sleepUntil(() -> Rs2Widget.isWidgetVisible(7602207));
        }
        //house icon
        if (Rs2Widget.clickWidget(7602207)) {
            sleep(1200);
        } else {
            Microbot.log("House Options button not found.");
            return;
        }

        // Click Leave House
        if (Rs2Widget.clickWidget(24248341)) {
            sleep(3000);
        } else {
            Microbot.log("Leave House button not found.");
        }


    }
    public void unnotePlanks() {
        if (Rs2Widget.getWidget(14352385) == null) {
            Rs2Inventory.useItemOnNpc(8779, 1614);
            Rs2Player.waitForWalking();
            sleepUntil(() -> Rs2Widget.getWidget(14352385) != null, 5000);
            Rs2Keyboard.keyPress('3');
            Rs2Inventory.waitForInventoryChanges(2000);
            sleep(2400,3000);
        }
    }

    private void enterHouse() {
        //entering house
        TileObject portalObject = Rs2GameObject.getGameObject(OUTSIDE_HOUSE_PORTAL_OBJECT);
        if (portalObject == null) {
            Microbot.log("Not outside house, OUTSIDE_HOUSE_PORTAL_OBJECT not found.");
            return;
        }
        boolean interacted = Rs2GameObject.interact(portalObject, "Build mode");
        sleep(2400, 3000);
        Microbot.log("Rs2GameObject.interact returned: " + interacted);
        Rs2Player.waitForWalking();
        sleep(2400, 3000);
    }

    private void phials() {
        // Step 1: Leave the house
        leaveHouse();

        // Wait for us to be outside the house
        sleepUntil(() -> Rs2GameObject.getGameObject(OUTSIDE_HOUSE_PORTAL_OBJECT) != null, 5000);

        //walk to Phials
        Rs2Walker.walkTo(new WorldPoint(2949,3213,0));
        Rs2Player.waitForWalking();

        // Step 2: Unnote planks with Phials
        unnotePlanks();

        // Wait for inventory to have the required planks
        boolean hasEnoughPlanks = sleepUntil(() -> Rs2Inventory.hasItemAmount(ItemID.OAK_PLANK, 8), 5000);
        if (!hasEnoughPlanks) {
            return;
        }
        enterHouse();
        // Wait for us to be back in the house
        boolean backInHouse = sleepUntil(() -> Rs2GameObject.getGameObject(HOUSE_PORTAL_OBJECT) != null, 5000);
    }

    private void build() {
        TileObject oakLarderSpace = getOakLarderSpace();
        if (oakLarderSpace == null) return;
        if (Rs2GameObject.interact(oakLarderSpace, "Build")) {
            sleepUntilOnClientThread(() -> hasFurnitureInterfaceOpen(), 5000);
            Rs2Keyboard.keyPress('2');
            sleepUntilOnClientThread(() -> getOakLarder() != null, 5000);
        }
    }

    private void remove() {
        TileObject oaklarder = getOakLarder();
        if (oaklarder == null) return;
        if (Rs2GameObject.interact(oaklarder, "Remove")) {
            Rs2Dialogue.sleepUntilHasQuestion("Really remove it?");
            Rs2Dialogue.keyPressForDialogueOption(1);
            sleepUntil(() -> getOakLarderSpace() != null, 5000);
        }
    }

    private void butler() {
        NPC butler = getButler();
        boolean butlerIsToFar;
        if (butler == null) return;
        butlerIsToFar = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            int distance = butler.getWorldLocation().distanceTo(Microbot.getClient().getLocalPlayer().getWorldLocation());
            return distance > 3;
        }).orElse(false);
        if (!butlerIsToFar) {
            Rs2Npc.interact(butler, "talk-to");
        } else {
            Rs2Tab.switchToSettingsTab();
            sleep(800, 1800);
            Widget houseOptionWidget = Rs2Widget.findWidget(SpriteID.OPTIONS_HOUSE_OPTIONS, null);
            if (houseOptionWidget != null)
                Microbot.getMouse().click(houseOptionWidget.getCanvasLocation());
            sleep(800, 1800);
            Widget callServantWidget = Rs2Widget.findWidget("Call Servant", null);
            if (callServantWidget != null)
                Microbot.getMouse().click(callServantWidget.getCanvasLocation());
        }

        Rs2Dialogue.sleepUntilInDialogue();

        if (Rs2Dialogue.hasQuestion("Repeat last task?")) {
            Rs2Dialogue.keyPressForDialogueOption(1);
            Rs2Random.waitEx(2400, 300);
            Rs2Dialogue.sleepUntilInDialogue();
            return;
        }

        if (Rs2Dialogue.hasSelectAnOption()) {
            if (Rs2Dialogue.hasDialogueOption("Go to the bank...")) {
                Rs2Dialogue.sleepUntilHasDialogueText("Dost thou wish me to exchange that certificate");
                Rs2Dialogue.clickContinue();
                Rs2Dialogue.sleepUntilSelectAnOption();
                Rs2Dialogue.keyPressForDialogueOption(1);
                Rs2Widget.sleepUntilHasWidget("Enter amount:");
                Rs2Keyboard.typeString("28");
                Rs2Keyboard.enter();
                Rs2Dialogue.clickContinue();
                Rs2Random.waitEx(2400, 300);
                Rs2Dialogue.sleepUntilInDialogue();
                return;
            }
        }

        if (Rs2Dialogue.hasDialogueText("must render unto me the 10,000 coins that are due")) {
            Rs2Dialogue.clickContinue();
            Rs2Random.waitEx(1200, 300);
            Rs2Dialogue.sleepUntilSelectAnOption();
            Rs2Dialogue.keyPressForDialogueOption(1);
        }
    }
}
