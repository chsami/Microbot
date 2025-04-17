package net.runelite.client.plugins.microbot.quest.logic;

import net.runelite.api.ItemID;
import net.runelite.api.NpcID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.questhelper.requirements.item.ItemRequirement;
import net.runelite.client.plugins.microbot.questhelper.steps.NpcStep;
import net.runelite.client.plugins.microbot.questhelper.steps.ObjectStep;
import net.runelite.client.plugins.microbot.questhelper.steps.QuestStep;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

/**
 * Custom logic for RuneMysteries Quest
 */
public class RuneMysteries extends BaseQuest {
    @Override
    public boolean executeCustomLogic() {
        QuestStep questStep = getQuestHelperPlugin().getSelectedQuest().getCurrentStep().getActiveStep();
        if (questStep.getText().get(0).equalsIgnoreCase("Bring the research notes to Sedridor in the Wizard Tower's basement.")) {
            if (!Rs2Inventory.hasItem("notes")) {
                if (Rs2Player.isInCombat()) {
                    Microbot.showMessage("You are in combat, please finish the fight before continuing.");
                    return false;
                }
                if (Rs2Dialogue.isInDialogue()) {
                    Rs2Dialogue.clickContinue();
                    return false;
                }
                var npc = Rs2Npc.getNpc(NpcID.AUBURY_11434);
                if (new WorldPoint(3253, 3401, 0).distanceTo(Rs2Player.getWorldLocation()) > 3) {
                    Rs2Walker.walkTo(3253, 3401, 0, 3);
                } else if (!Rs2Dialogue.isInDialogue()) {
                    Rs2Npc.interact(npc, "Talk-to");
                }
                return false;
            }
        }
        try {
            var objectStep = (ObjectStep) questStep;
            if (objectStep.getRequirements() != null && objectStep.getRequirements().stream().findFirst().orElse(null) instanceof ItemRequirement
            && ((ItemRequirement) objectStep.getRequirements().stream().findFirst().get()).getId() == ItemID.AIR_TALISMAN) {
                if (!Rs2Inventory.hasItem(ItemID.AIR_TALISMAN)) {
                    if (Rs2Dialogue.hasSelectAnOption()) {
                        Rs2Dialogue.keyPressForDialogueOption("What did you want me to do again?");
                        return false;
                    } else if (Rs2Dialogue.isInDialogue()) {
                        Rs2Dialogue.clickContinue();
                        return false;
                    }
                    var npc = Rs2Npc.getNpc(NpcID.DUKE_HORACIO);
                    if (npc == null) {
                        Rs2Walker.walkTo(3209, 3222, 1, 3);
                    } else {
                        Rs2Npc.interact(npc, "Talk-to");
                    }
                    return false;
                }
            } else if (Rs2Dialogue.hasDialogueOption("Climb down the stairs.")) {
                Rs2Dialogue.keyPressForDialogueOption("Climb down the stairs.");
                return false;
            }
        } catch (Exception ex) {
            //ignore error
        }
        try {
            var npcStep = (NpcStep) questStep;
            if (npcStep.getRequirements() != null && npcStep.getRequirements().stream().findFirst().orElse(null) instanceof ItemRequirement) {
                ItemRequirement itemRequirement = (ItemRequirement) npcStep.getRequirements().stream().findFirst().get();
                if (!Rs2Inventory.hasItem(itemRequirement.getId())) {
                    if (Rs2Dialogue.isInDialogue()) {
                        Rs2Dialogue.clickContinue();
                        return false;
                    }
                    var npc = Rs2Npc.getNpc(NpcID.ARCHMAGE_SEDRIDOR);
                    if (new WorldPoint(3105, 9571, 0).distanceTo(Rs2Player.getWorldLocation()) > 3) {
                        Rs2Walker.walkTo(3105, 9571, 0, 3);
                    } else if (!Rs2Dialogue.isInDialogue()) {
                        Rs2Npc.interact(npc, "Talk-to");
                    }
                    return false;
                }
            } else if (Rs2Dialogue.hasDialogueOption("Anything useful in that package I gave you?")) {
                Rs2Dialogue.keyPressForDialogueOption("Anything useful in that package I gave you?");
                return false;
            }
        } catch (Exception ex) {
            //ignore error
        }
        if (Rs2Dialogue.hasDialogueOption("Go ahead.")) {
            Rs2Dialogue.keyPressForDialogueOption("Go ahead.");
            return false;
        }
        return true;
    }
}
