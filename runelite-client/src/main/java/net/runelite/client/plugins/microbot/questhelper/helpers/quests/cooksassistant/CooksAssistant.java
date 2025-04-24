package net.runelite.client.plugins.microbot.questhelper.helpers.quests.cooksassistant;

import net.runelite.api.ItemID;
import net.runelite.api.NpcID;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.questhelper.panel.PanelDetails;
import net.runelite.client.plugins.microbot.questhelper.questhelpers.BasicQuestHelper;
import net.runelite.client.plugins.microbot.questhelper.requirements.item.ItemRequirement;
import net.runelite.client.plugins.microbot.questhelper.rewards.ExperienceReward;
import net.runelite.client.plugins.microbot.questhelper.rewards.QuestPointReward;
import net.runelite.client.plugins.microbot.questhelper.rewards.UnlockReward;
import net.runelite.client.plugins.microbot.questhelper.steps.NpcStep;
import net.runelite.client.plugins.microbot.questhelper.steps.QuestStep;

import java.util.*;

public class CooksAssistant extends BasicQuestHelper {

    ItemRequirement egg, milk, flour;
    QuestStep finishQuest;

    @Override
    public Map<Integer, QuestStep> loadSteps() {
        setupRequirements();
        setupSteps();

        Map<Integer, QuestStep> steps = new HashMap<>();
        // Only add finish quest step
        steps.put(0, finishQuest);
        steps.put(1, finishQuest);
        return steps;
    }

    @Override
    protected void setupRequirements() {
        egg = new ItemRequirement("Egg", ItemID.EGG);
        milk = new ItemRequirement("Bucket of milk", ItemID.BUCKET_OF_MILK);
        flour = new ItemRequirement("Pot of flour", ItemID.POT_OF_FLOUR);
    }

    public void setupSteps() {
        finishQuest = new NpcStep(this, NpcID.COOK_4626, new WorldPoint(3206, 3214, 0),
                "Give the Cook in Lumbridge Castle's kitchen the required items to finish the quest.",
                egg, milk, flour);
        finishQuest.addDialogSteps("What's wrong?", "Can I help?", "Yes.");
    }

    @Override
    public List<ItemRequirement> getItemRequirements() {
        return Arrays.asList(egg, flour, milk);
    }

    @Override
    public QuestPointReward getQuestPointReward() {
        return new QuestPointReward(1);
    }

    @Override
    public List<ExperienceReward> getExperienceRewards() {
        return Collections.singletonList(new ExperienceReward(Skill.COOKING, 300));
    }

    @Override
    public List<UnlockReward> getUnlockRewards() {
        return Collections.singletonList(new UnlockReward("Permission to use The Cook's range."));
    }

    @Override
    public List<PanelDetails> getPanels() {
        // Show a simple panel with the hand-in
        return Collections.singletonList(new PanelDetails("Finishing Cook's Assistant", Collections.singletonList(finishQuest), egg, flour, milk));
    }
}