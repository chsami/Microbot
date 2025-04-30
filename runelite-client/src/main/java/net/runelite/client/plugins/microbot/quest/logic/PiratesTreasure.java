package net.runelite.client.plugins.microbot.quest.logic;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.MenuAction;
import net.runelite.api.Point;
import net.runelite.api.Quest;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.quest.MQuestPlugin;
import net.runelite.client.plugins.microbot.questhelper.steps.QuestStep;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import static net.runelite.api.gameval.ItemID.BANANA;
import static net.runelite.api.gameval.ItemID.SPADE;
import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Pirate's Treasure quest custom logic
 */
@Slf4j
public class PiratesTreasure extends BaseQuest {

    public void setMQuestPlugin(MQuestPlugin mQuestPlugin) {
        this.mQuestPlugin = mQuestPlugin;
    }

        private MQuestPlugin mQuestPlugin;

        public PiratesTreasure() {
        }

        public PiratesTreasure(MQuestPlugin mQuestPlugin) {
            this.mQuestPlugin = mQuestPlugin;
        }

    @Override
    public boolean executeCustomLogic() {
        QuestStep questStep = getQuestHelperPlugin().getSelectedQuest().getCurrentStep().getActiveStep();
        if (getQuestHelperPlugin().getSelectedQuest().getQuest().getId() == Quest.PIRATES_TREASURE.getId()) {
            if (questStep.getText().contains("Please open Pirate Treasure's Quest Journal to sync the current quest state.")) {

                Rs2Tab.switchToQuestTab();

                sleep(600, 800);

                directQuestSearch();

                sleep(600, 800);

                Rs2Widget.clickWidget("Pirate's Treasure");

                sleep(600, 800);

                Rs2Widget.clickWidget("Close Floating panel");
                sleep(2000);
                Rs2Widget.clickWidget("Close");

                sleep(2000);

                /* walkTo(3027, 3222, 0);

                if (Rs2Npc.getNpc("Seaman Lorris") != null) {
                    Rs2Npc.interact("Seaman Lorris", "Talk-to");
                    sleepUntil(Rs2Dialogue::isInDialogue);
                    if (Rs2Dialogue.isInDialogue()) {
                        Rs2Dialogue.clickContinue();
                    }
                    sleep(600, 800);
                    if (Rs2Dialogue.isInDialogue()) {
                        Rs2Dialogue.clickContinue();
                    }
                    sleep(600, 800);
                    if (Rs2Dialogue.isInDialogue()) {
                        Rs2Dialogue.clickContinue();
                    }
                } */

                return true;
            }
            if (questStep.getText().contains("Right-click fill the rest of the crate with bananas, then talk to Luthas.")) {
                Rs2Walker.walkTo(2917, 3161, 0);
                sleep(2000);
                collectBananas();

                return true;
            }
            if (questStep.getText().contains("Talk to Luthas and tell him you finished filling the crate.")) {
                Rs2Walker.walkTo(2942, 3150, 0);
                sleep(2000);
                Rs2GameObject.interact(2072, "Search", 10);
                sleep(6000);
                if (!mQuestPlugin.fullCrate) {
                    collectBananas();
                } else {
                    Rs2Npc.interact("Luthas", "Talk-to");
                    sleepUntil(Rs2Dialogue::isInDialogue);
                    Rs2Dialogue.clickContinue();
                    sleep(2000);
                    Rs2Dialogue.clickContinue();
                }
                return true;
            }

            if (questStep.getText().contains("Search the crate in the back room of the Port Sarim food shop. Make sure you're wearing your white apron.")) {
                if (Rs2Dialogue.isInDialogue()) {
                    Rs2Dialogue.clickContinue();
                }
                return true;
            }

            if (questStep.getText().contains("Dig in the middle of the cross in Falador Park, and kill the Gardener (level 4) who appears. Once killed, dig again.")) {
                if (!Rs2Inventory.contains(SPADE)) {
                    System.out.println("here2");
                    Rs2Walker.setTarget(null);
                    sleep(1200);
                    Rs2Walker.walkTo(2982, 3369, 0);
                    sleep(1200);
                    Rs2GroundItem.loot(SPADE);
                    sleep(1200);
                }

                //to give time to kill the npc (you can actually just run away and run back to dig and it works)
                if (Rs2Player.isInCombat()) {
                    sleepUntil(() -> !Rs2Player.isInCombat(), 30000);
                }
                return true;
            }

        }
        return true;
    }

    public static void collectBananas() {
        if (Rs2Inventory.count(BANANA) >= 10) {
            return;
        }

        pickBananasAt(new int[][]{
                {2917, 3161, 0},
                {2920, 3168, 0},
                {2909, 3163, 0}
        });

        if (Rs2Inventory.count(BANANA) >= 10) {
            Rs2Walker.walkTo(2942, 3150, 0);
            sleep(2000);
            Rs2GameObject.interact(2072, "Fill", 10);
            sleep(2000);
        }
    }

    private static void pickBananasAt(int[][] locations) {
        for (int[] location : locations) {
            Rs2Walker.walkTo(location[0], location[1], location[2]);
            sleep(2000);
            pickBananaTree();
        }
    }

    private static void pickBananaTree() {
        for (int i = 0; i < 4; i++) { // 4 picks total
            Rs2GameObject.interact("Banana Tree", "Pick");
            sleep(800, 1500); // Sleep after each click
        }
    }


    private void directQuestSearch() {
        if (openSearchWidget()) {
            sleep(600, 800);
            Rs2Keyboard.typeString("Pirate");
            sleep(800, 1000);
        }
    }

    private boolean openSearchWidget() {
        // Get the widget with accurate coordinates
        int groupId = 26148864 >> 16;
        Widget parentWidget = Microbot.getClient().getWidget(groupId, 0);

        if (parentWidget != null) {
            // From the widget info: RelativeX 161, RelativeY 0, Width 18, Height 17
            // Calculate the exact center point where we need to click
            Point canvasLocation = parentWidget.getCanvasLocation();
            int exactX = canvasLocation.getX() + 161 + 9; // center X
            int exactY = canvasLocation.getY() + 8;       // center Y

            // Create a precise click point directly on the search icon
            Point clickPoint = new Point(exactX, exactY);

            Microbot.log("Clicking search icon at " + clickPoint.getX() + ", " + clickPoint.getY());

            // Create a direct menu entry for clicking the search
            NewMenuEntry entry = new NewMenuEntry(
                    "Open",
                    "Search",
                    2,
                    MenuAction.CC_OP,
                    0,
                    26148864,
                    false
            );

            // Use mouse to click at the exact point
            Microbot.getMouse().click(clickPoint, entry);
            sleep(500, 700);
            return true;
        }
        return false;
    }
}