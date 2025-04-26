package net.runelite.client.plugins.microbot.quest.logic;

import net.runelite.api.ItemID;
import net.runelite.api.ObjectID;
import net.runelite.api.Quest;
import net.runelite.client.plugins.microbot.questhelper.steps.QuestStep;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

/**
 * Romeo and Juliet quest custom logic
 */
public class RomeoAndJuliet extends BaseQuest {
    @Override
    public boolean executeCustomLogic() {
        QuestStep questStep = getQuestHelperPlugin().getSelectedQuest().getCurrentStep().getActiveStep();
        if (getQuestHelperPlugin().getSelectedQuest().getQuest().getId() == Quest.ROMEO__JULIET.getId()) {
            if (Rs2Dialogue.hasDialogueOptionTitle("Start the Romeo & Juliet quest?")) {
                Rs2Dialogue.keyPressForDialogueOption("Yes.");
                return false;
            }
            if (questStep.getText().contains("Bring the cadava berries to the Apothecary in south east Varrock.")) {
                boolean hasCadavaBerries = fetchCadavaBerries();
                if (!hasCadavaBerries) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean fetchCadavaBerries() {
        if (Rs2Inventory.hasItem(ItemID.CADAVA_BERRIES)) {
            return true;
        }
        if (Rs2Walker.walkTo(3266, 3374, 0, 10)) {
            Rs2GameObject.interact(new int[] {ObjectID.CADAVA_BUSH, ObjectID.CADAVA_BUSH_23626, ObjectID.CADAVA_BUSH_23627}, "take");
            Rs2Player.waitForWalking();
            Rs2Inventory.waitForInventoryChanges(2000);
        }

        return Rs2Inventory.hasItem(ItemID.CADAVA_BERRIES);
    }
}
