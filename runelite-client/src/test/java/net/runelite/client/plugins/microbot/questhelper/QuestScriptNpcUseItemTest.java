package net.runelite.client.plugins.microbot.questhelper;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.questhelper.questhelpers.QuestHelper;
import net.runelite.client.plugins.microbot.questhelper.steps.DetailedQuestStep;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class QuestScriptNpcUseItemTest
{
	private static final int DRUID_POUCH = 2958;
	private static final String GHAST_STEP = "Use the filled druid pouch on a ghast to make it attackable and kill it. You'll need to kill 3.";

	@Test
	public void useItemOnNamedNpcIsDetectedInKillStep()
	{
		assertTrue(QuestScript.usesItemOn(Collections.singletonList(GHAST_STEP), "Ghast"));
	}

	@Test
	public void plainKillStepIsNotAUseItemStep()
	{
		assertFalse(QuestScript.usesItemOn(Collections.singletonList("Kill the ghast."), "Ghast"));
	}

	@Test
	public void useItemOnAnotherTargetIsNotDetected()
	{
		assertFalse(QuestScript.usesItemOn(Collections.singletonList("Use the mirror on Filliman Tarlock."), "Ghast"));
	}

	@Test
	public void targetNameBeforeUseOnIsNotDetected()
	{
		assertFalse(QuestScript.usesItemOn(Arrays.asList("Kill the ghast.", "Then use the bones on the altar."), "Ghast"));
	}

	@Test
	public void missingNameOrTextIsNotDetected()
	{
		assertFalse(QuestScript.usesItemOn(Collections.singletonList(GHAST_STEP), null));
		assertFalse(QuestScript.usesItemOn(Collections.singletonList(GHAST_STEP), "null"));
		assertFalse(QuestScript.usesItemOn(Collections.singletonList(GHAST_STEP), " "));
		assertFalse(QuestScript.usesItemOn(null, "Ghast"));
	}

	@Test
	public void useIconItemOnNpcInKillStepDoesNotAttack()
	{
		assertFalse(QuestScript.attacksNpc(Collections.singletonList(GHAST_STEP), DRUID_POUCH, "Ghast"));
	}

	@Test
	public void killStepWithoutIconItemAttacks()
	{
		assertTrue(QuestScript.attacksNpc(Collections.singletonList(GHAST_STEP), -1, "Ghast"));
		assertTrue(QuestScript.attacksNpc(Collections.singletonList("Kill the ghast."), -1, "Ghast"));
	}

	@Test
	public void killStepWithIconItemForAnotherTargetAttacks()
	{
		assertTrue(QuestScript.attacksNpc(Collections.singletonList("Kill the ghast."), DRUID_POUCH, "Ghast"));
	}

	@Test
	public void stepWithoutKillWordDoesNotAttack()
	{
		assertFalse(QuestScript.attacksNpc(Collections.singletonList("Talk to Filliman Tarlock."), -1, "Filliman Tarlock"));
		assertFalse(QuestScript.attacksNpc(null, -1, "Ghast"));
	}

	@Test
	public void plainStepWithoutPointOrItemUseIsUnautomated()
	{
		DetailedQuestStep step = new DetailedQuestStep(mock(QuestHelper.class), "Cast the druidic spell next to a rotten log.");
		assertTrue(QuestScript.isUnautomatedStep(step, null, false));
	}

	@Test
	public void stepWithPointOrItemUseIsAutomated()
	{
		DetailedQuestStep step = new DetailedQuestStep(mock(QuestHelper.class), "Pick up the mirror.");
		assertFalse(QuestScript.isUnautomatedStep(step, new WorldPoint(3437, 3337, 0), false));
		assertFalse(QuestScript.isUnautomatedStep(step, null, true));
	}

	@Test
	public void specialisedStepTypesAreNotReportedAsUnautomated()
	{
		DetailedQuestStep step = new DetailedQuestStep(mock(QuestHelper.class), "Click the button.")
		{
		};
		assertFalse(QuestScript.isUnautomatedStep(step, null, false));
	}
}
