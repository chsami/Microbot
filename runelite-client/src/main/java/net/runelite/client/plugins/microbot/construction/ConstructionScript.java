package net.runelite.client.plugins.microbot.construction;

import net.runelite.api.ItemID;
import net.runelite.api.NPC;
import net.runelite.api.SpriteID;
import net.runelite.api.TileObject;
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
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.concurrent.TimeUnit;


public class ConstructionScript extends Script {

    ConstructionState state = ConstructionState.Idle;
    
    public TileObject getOakLarderSpace() {
        return Rs2GameObject.findObjectById(15403);
    }

    public TileObject getOakLarder() {
        return Rs2GameObject.findObjectById(13566);
    }

    public NPC getButler() {
        return Rs2Npc.getNpc("Demon butler");
    }

    public boolean hasFurnitureInterfaceOpen() {
        return Rs2Widget.findWidget("Furniture", null) != null;
    }

    public boolean run(ConstructionConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                Rs2Tab.switchToInventoryTab();
                calculateState();
                if (state == ConstructionState.Build) {
                    build();
                } else if (state == ConstructionState.Remove) {
                    remove();
                } else if (state == ConstructionState.Butler) {
                    butler();
                }
                //System.out.println(hasPayButlerDialogue());
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

    private void calculateState() {
        TileObject oakLarderSpace = getOakLarderSpace();
        TileObject oakLarder = getOakLarder();
        NPC butler = getButler();
        boolean hasRequiredPlanks = Rs2Inventory.hasItemAmount(ItemID.OAK_PLANK, Rs2Random.between(8, 16));
        if (oakLarderSpace == null && oakLarder != null) {
            state = ConstructionState.Remove;
        } else if (oakLarderSpace != null && oakLarder == null && hasRequiredPlanks) {
            state = ConstructionState.Build;
        } else if (oakLarderSpace != null && oakLarder == null && butler != null) {
            state = ConstructionState.Butler;
        } else if (oakLarderSpace == null && oakLarder == null) {
            state = ConstructionState.Idle;
            Microbot.getNotifier().notify("Looks like we are no longer in our house.");
            shutdown();
        }
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
