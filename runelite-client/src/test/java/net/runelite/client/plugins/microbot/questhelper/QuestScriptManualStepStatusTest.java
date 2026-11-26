package net.runelite.client.plugins.microbot.questhelper;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.questhelper.questhelpers.QuestHelper;
import net.runelite.client.plugins.microbot.questhelper.steps.DetailedQuestStep;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class QuestScriptManualStepStatusTest
{
	private static final String CONTINUING = "Quest helper: continuing";

	private String previousStatus;
	private DetailedQuestStep castSpell;
	private DetailedQuestStep useMushroom;

	@Before
	public void setUp()
	{
		previousStatus = Microbot.status;
		Microbot.status = "";
		castSpell = new DetailedQuestStep(mock(QuestHelper.class), "Cast the druidic spell next to a rotten log in Mort Myre to grow a mushroom.");
		useMushroom = new DetailedQuestStep(mock(QuestHelper.class), "Use the mushroom on the brown stone.");
	}

	@After
	public void tearDown()
	{
		Microbot.status = previousStatus;
	}

	@Test
	public void manualStepSetsPausedStatusAndNoticesOncePerStep()
	{
		QuestScript script = new QuestScript();

		assertTrue(script.holdForPlayer(castSpell, QuestScript.MANUAL_STEP_STATUS));
		assertEquals(QuestScript.MANUAL_STEP_STATUS, Microbot.status);
		assertTrue(Microbot.status.startsWith("Quest helper: paused"));
		assertFalse(script.holdForPlayer(castSpell, QuestScript.MANUAL_STEP_STATUS));
	}

	@Test
	public void manualStatusStaysWhileTheSameStepIsActive()
	{
		QuestScript script = new QuestScript();
		script.holdForPlayer(castSpell, QuestScript.MANUAL_STEP_STATUS);

		script.clearManualStepStatus(castSpell);

		assertEquals(QuestScript.MANUAL_STEP_STATUS, Microbot.status);
	}

	@Test
	public void manualStatusClearsWhenTheNextStepIsActive()
	{
		QuestScript script = new QuestScript();
		script.holdForPlayer(castSpell, QuestScript.MANUAL_STEP_STATUS);

		script.clearManualStepStatus(useMushroom);

		assertEquals(CONTINUING, Microbot.status);
		assertTrue(script.holdForPlayer(castSpell, QuestScript.MANUAL_STEP_STATUS));
	}

	@Test
	public void npcWaitStatusClearsWhenTheNpcAppears()
	{
		QuestScript script = new QuestScript();
		script.holdForPlayer(castSpell, QuestScript.NPC_WAIT_STATUS);

		script.clearManualStepStatus(null);

		assertEquals(CONTINUING, Microbot.status);
	}

	@Test
	public void otherStatusesAreLeftAlone()
	{
		QuestScript script = new QuestScript();
		Microbot.status = "Quest helper: walking to bank for missing items";

		script.clearManualStepStatus(useMushroom);

		assertEquals("Quest helper: walking to bank for missing items", Microbot.status);
	}

	@Test
	public void noticeNamesTheStepText()
	{
		assertEquals("Quest helper: paused, this step is not automated. Do it yourself and it will continue: "
				+ "Cast the druidic spell next to a rotten log in Mort Myre to grow a mushroom.",
			QuestScript.manualStepNotice(castSpell));
	}
}
