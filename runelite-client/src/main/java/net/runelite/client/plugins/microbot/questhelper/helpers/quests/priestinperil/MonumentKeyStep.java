package net.runelite.client.plugins.microbot.questhelper.helpers.quests.priestinperil;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.questhelper.questhelpers.QuestHelper;
import net.runelite.client.plugins.microbot.questhelper.requirements.Requirement;
import net.runelite.client.plugins.microbot.questhelper.steps.DetailedQuestStep;

/** Marks the "use the golden key on the right monument" step, which the quest script resolves by trying each monument. */
public class MonumentKeyStep extends DetailedQuestStep
{
	public MonumentKeyStep(QuestHelper questHelper, WorldPoint worldPoint, String text, Requirement... requirements)
	{
		super(questHelper, worldPoint, text, requirements);
	}
}
