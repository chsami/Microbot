package net.runelite.client.plugins.microbot.construction;

import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.NPC;
import net.runelite.api.SpriteID;
import net.runelite.api.TileObject;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.construction.enums.ConstructionState;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.concurrent.TimeUnit;


public class ConstructionScript extends Script {
    public static String version = "1.1";
    public Integer lardersBuilt = 0;
    public Integer lardersPerHour = 0;
    public Boolean servantsBagEmpty = false;
    public Boolean insufficientCoins = false;

    ConstructionState state = ConstructionState.Idle;

    public TileObject getOakLarderSpace() {
        return Rs2GameObject.getGameObject(15403);
    }

    public TileObject getOakLarder() {
        return Rs2GameObject.getGameObject(13566);
    }

    public Rs2NpcModel getButler() {
        return Rs2Npc.getNpc("Demon butler");
    }

    public boolean hasFurnitureInterfaceOpen() {
        return Rs2Widget.findWidget("Furniture", null) != null;
    }

    public boolean run(ConstructionConfig config) {
        lardersBuilt = 0;
        lardersPerHour = 0;
        servantsBagEmpty = false;
        insufficientCoins = false;
        state = ConstructionState.Starting;

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
                } else if (state == ConstructionState.Stopped) {
                    servantsBagEmpty = false;
                    insufficientCoins = false;
                    Microbot.stopPlugin(Microbot.getPlugin("ConstructionPlugin"));
                    shutdown();
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
        var butler = getButler();
        int plankCount = Rs2Inventory.itemQuantity(ItemID.PLANK_OAK);

        if (servantsBagEmpty && insufficientCoins) {
            state = ConstructionState.Stopped;
            Microbot.getNotifier().notify("Insufficient coins to pay butler!");
            Microbot.log("Insufficient coins to pay butler!");
            return;
        }
        if (oakLarderSpace == null && oakLarder != null) {
            state = ConstructionState.Remove;
        } else if (oakLarderSpace != null && oakLarder == null) {
            if (butler != null) {
                if (plankCount > 16) {
                    state = ConstructionState.Build;
                } else {
                    state = ConstructionState.Butler;
                }
            } else {
                if (plankCount >= 8) {
                    state = ConstructionState.Build;
                } else {
                    state = ConstructionState.Idle;
                }
            }
        } else if (oakLarderSpace == null) {
            state = ConstructionState.Idle;
            Microbot.getNotifier().notify("Looks like we are no longer in our house.");
            shutdown();
        }
    }

    private void build() {
        TileObject oakLarderSpace = getOakLarderSpace();
        if (oakLarderSpace == null) return;
        if (Rs2GameObject.interact(oakLarderSpace, "Build")) {
            sleepUntil(this::hasFurnitureInterfaceOpen, 1200);
            Rs2Keyboard.keyPress('2');
            sleepUntil(() -> getOakLarder() != null, 1200);
            if (getOakLarder() != null)
            {
                lardersBuilt++;
            }
        }
    }

    private void remove() {
        TileObject oakLarder = getOakLarder();
        if (oakLarder == null) return;
        if (Rs2GameObject.interact(oakLarder, "Remove")) {
            Rs2Dialogue.sleepUntilInDialogue();

            // Butler spoke with us in the same tick/after we attempted to remove larder
            if (!Rs2Dialogue.hasQuestion("Really remove it?"))
            {
                sleep(600);
                Rs2GameObject.interact(oakLarder, "Remove");
            }
            Rs2Dialogue.sleepUntilHasDialogueOption("Yes");
            Rs2Dialogue.keyPressForDialogueOption(1);
            sleepUntil(() -> getOakLarderSpace() != null, 1800);
        }
    }

    private void butler() {
        var butler = getButler();
        boolean butlerIsTooFar;
        if (butler == null) return;
        butlerIsTooFar = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            int distance = butler.getWorldLocation().distanceTo(Microbot.getClient().getLocalPlayer().getWorldLocation());
            return distance > 3;
        }).orElse(false);
        if (!butlerIsTooFar) {
            // somehow the butler was found but 'talk-to' is not available (butler is gone)
            if(!Rs2Npc.interact(butler, "talk-to")) return;
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
            return;
        }

        if (Rs2Dialogue.hasSelectAnOption()) {
            if (Rs2Dialogue.hasDialogueOption("Go to the bank...")) {
                Rs2Dialogue.sleepUntilHasDialogueText("Dost thou wish me to exchange that certificate");
                Rs2Dialogue.clickContinue();
                Rs2Dialogue.sleepUntilSelectAnOption();
                Rs2Dialogue.keyPressForDialogueOption(1);
                Rs2Widget.sleepUntilHasWidget("Enter amount:");
                Rs2Keyboard.typeString("24");
                Rs2Keyboard.enter();
                Rs2Dialogue.clickContinue();
                return;
            }
        }

        if (Rs2Dialogue.hasDialogueText("must render unto me the 10,000 coins that are due")) {
            servantsBagEmpty = true;

            Rs2Dialogue.clickContinue();
            Rs2Dialogue.sleepUntilSelectAnOption();

            if (!Rs2Dialogue.hasDialogueOption("here's 10,000 coins")) {
                insufficientCoins = true;
                return;
            }

            Rs2Dialogue.keyPressForDialogueOption(1);
        }
    }
}
