package net.runelite.client.plugins.microbot.construction;

import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
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
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.concurrent.TimeUnit;

public class ConstructionScript extends Script {
    public static String version = "1.1";
    public Integer lardersBuilt = 0;
    public Integer lardersPerHour = 0;
    public Boolean servantsBagEmpty = false;
    public Boolean insufficientCoins = false;

    private final int HOUSE_PORTAL_OBJECT = 4525;
    private final int OUTSIDE_HOUSE_PORTAL_OBJECT = 15478;
    private final int NOTED_OAK_PLANK = 8779;

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

    public NPC getPhials() {
        return Rs2Npc.getNpc("Phials");
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
                calculateState(config);
                if (state == ConstructionState.Build) {
                    build();
                } else if (state == ConstructionState.Remove) {
                    remove();
                } else if (state == ConstructionState.Butler) {
                    butler();
                } else if (state == ConstructionState.Phials) {
                    phials();
                } else if (state == ConstructionState.Stopped) {
                    servantsBagEmpty = false;
                    insufficientCoins = false;
                    Microbot.stopPlugin(Microbot.getPlugin("ConstructionPlugin"));
                    shutdown();
                }
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
        var butler = getButler();
        int plankCount = Rs2Inventory.itemQuantity(ItemID.PLANK_OAK);

        if (servantsBagEmpty && insufficientCoins && !config.usePhials()) {
            state = ConstructionState.Stopped;
            Microbot.getNotifier().notify("Insufficient coins to pay butler!");
            Microbot.log("Insufficient coins to pay butler!");
            return;
        }

        if (oakLarderSpace == null && oakLarder != null) {
            state = ConstructionState.Remove;
        } else if (oakLarderSpace != null && oakLarder == null) {
            // Use Phials if configured, regardless of butler presence
            if (config.usePhials()) {
                if (plankCount >= 8) {
                    state = ConstructionState.Build;
                } else {
                    state = ConstructionState.Phials;
                }
            } else if (butler != null) {
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

    private void phials() {
        Microbot.log("Starting phials process...");

        // Step 1: Leave the house
        leaveHouse();
        Microbot.log("Left house");

        // Wait for us to be outside the house
        sleepUntil(() -> Rs2GameObject.getGameObject(OUTSIDE_HOUSE_PORTAL_OBJECT) != null, 5000);
        Microbot.log("Confirmed outside house");

        // Step 2: Walk to Phials
        Rs2Walker.walkTo(new WorldPoint(2949, 3213, 0));
        Rs2Player.waitForWalking();
        Microbot.log("Walked to Phials");

        // Step 3: Unnote planks with Phials
        unnotePlanks();
        Microbot.log("Unnoted successfully");

        // Wait for inventory to have the required planks
        boolean hasEnoughPlanks = sleepUntil(() -> Rs2Inventory.itemQuantity(ItemID.PLANK_OAK) >= 8, 5000);
        Microbot.log("Has enough planks: " + hasEnoughPlanks);

        if (!hasEnoughPlanks) {
            Microbot.log("Failed to get enough planks, aborting phials process");
            return;
        }

        // Step 4: Enter the house
        Microbot.log("Attempting to enter house...");
        enterHouse();

        // Wait for us to be back in the house
        boolean backInHouse = sleepUntil(() -> Rs2GameObject.getGameObject(HOUSE_PORTAL_OBJECT) != null, 5000);
        Microbot.log("Back in house: " + backInHouse);
    }

    public void leaveHouse() {
        Microbot.log("Attempting to leave house...");

        Rs2Tab.switchToSettingsTab();
        sleep(1200);

        String[] actions = Rs2Widget.getWidget(7602235).getActions();
        boolean isControlsInterfaceVisible = actions != null && actions.length == 0;
        if (!isControlsInterfaceVisible) {
            Rs2Widget.clickWidget(7602235);
            sleepUntil(() -> Rs2Widget.isWidgetVisible(7602207));
        }

        // House icon
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
        // Check if we already have enough unnoted planks
        if (Rs2Inventory.itemQuantity(ItemID.PLANK_OAK) >= 8) {
            Microbot.log("Already have enough unnoted planks");
            return;
        }

        // Check if we have noted planks to unnote
        if (!Rs2Inventory.hasItem(NOTED_OAK_PLANK)) {
            Microbot.log("No noted planks found");
            return;
        }

        Microbot.log("Using noted planks on Phials...");
        Rs2Inventory.useItemOnNpc(NOTED_OAK_PLANK, 1614);
        Rs2Player.waitForWalking();

        // Wait for the exchange interface to appear
        boolean interfaceAppeared = sleepUntil(() -> Rs2Widget.getWidget(14352385) != null, 5000);

        if (interfaceAppeared) {
            Microbot.log("Exchange interface appeared, pressing 3...");
            Rs2Keyboard.keyPress('3');
            Rs2Inventory.waitForInventoryChanges(3000);

            // Wait for everything to settle after unnoting
            Microbot.log("Unnoting complete, waiting for client to settle...");
            sleep(1500, 2000);
        } else {
            Microbot.log("Exchange interface never appeared!");
        }
    }

    private void enterHouse() {
        // Make sure player is actually ready for the next action
        Microbot.log("Waiting for player to be ready...");
        sleepUntil(() -> !Rs2Player.isMoving() && !Rs2Player.isAnimating(), 3000);

        // Additional wait to ensure all client states are settled
        sleep(1000, 1500);

        TileObject portalObject = Rs2GameObject.getGameObject(OUTSIDE_HOUSE_PORTAL_OBJECT);
        if (portalObject == null) {
            Microbot.log("Not outside house, OUTSIDE_HOUSE_PORTAL_OBJECT not found.");
            return;
        }

        Microbot.log("Player ready, attempting to interact with portal...");
        boolean interacted = Rs2GameObject.interact(portalObject, "Build mode");
        Microbot.log("Rs2GameObject.interact returned: " + interacted);

        if (interacted) {
            Rs2Player.waitForWalking();

            // Wait for house loading and check for house portal
            boolean enteredHouse = sleepUntil(() -> Rs2GameObject.getGameObject(HOUSE_PORTAL_OBJECT) != null, 8000);
            Microbot.log("Successfully entered house: " + enteredHouse);

            if (!enteredHouse) {
                Microbot.log("Failed to enter house within 8 seconds");
            }
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
            Rs2Dialogue.sleepUntilNotInDialogue();
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