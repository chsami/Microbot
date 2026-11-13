package net.runelite.client.plugins.microbot.questhelper;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class QuestScriptOptionSelectionPendingTest
{
	@Test
	public void freshOptionListIsDismissedRightAway()
	{
		QuestScript script = new QuestScript();

		assertFalse(script.isAwaitingOptionSelection(false, 10_000L));
	}

	@Test
	public void pendingSelectionHoldsBackTheDismissClick()
	{
		QuestScript script = new QuestScript();

		assertTrue(script.isAwaitingOptionSelection(true, 10_000L));
		assertTrue(script.isAwaitingOptionSelection(true, 10_700L));
		assertTrue(script.isAwaitingOptionSelection(true, 11_400L));
	}

	@Test
	public void selectionPendingForTooLongIsDismissed()
	{
		QuestScript script = new QuestScript();

		long now = 10_000L;
		assertTrue(script.isAwaitingOptionSelection(true, now));
		while (now < 14_900L)
		{
			now += 700L;
			assertTrue(script.isAwaitingOptionSelection(true, now));
		}
		assertFalse(script.isAwaitingOptionSelection(true, now + 700L));
	}

	@Test
	public void laterDialogueStartsItsOwnWait()
	{
		QuestScript script = new QuestScript();

		assertTrue(script.isAwaitingOptionSelection(true, 10_000L));
		assertTrue(script.isAwaitingOptionSelection(true, 10_700L));
		assertTrue(script.isAwaitingOptionSelection(true, 60_000L));
	}

	@Test
	public void freshListResetsTheWait()
	{
		QuestScript script = new QuestScript();

		assertTrue(script.isAwaitingOptionSelection(true, 10_000L));
		assertTrue(script.isAwaitingOptionSelection(true, 12_000L));
		assertTrue(script.isAwaitingOptionSelection(true, 14_000L));
		assertFalse(script.isAwaitingOptionSelection(false, 14_500L));
		assertTrue(script.isAwaitingOptionSelection(true, 15_200L));
	}

	@Test
	public void chainOfOptionMenusEachGetTheirOwnWait()
	{
		QuestScript script = new QuestScript();

		script.noteOptionPressed(10_000L);
		assertTrue(script.isAwaitingOptionSelection(true, 10_000L));
		script.noteOptionPressed(12_500L);
		assertTrue(script.isAwaitingOptionSelection(true, 12_500L));
		script.noteOptionPressed(15_000L);
		assertTrue(script.isAwaitingOptionSelection(true, 15_000L));
		assertTrue(script.isAwaitingOptionSelection(true, 17_400L));
		assertTrue(script.isAwaitingOptionSelection(true, 19_800L));
		assertTrue(script.isAwaitingOptionSelection(true, 22_200L));
		assertFalse(script.isAwaitingOptionSelection(true, 22_400L));
	}

	@Test
	public void justPressedOptionHoldsBackTheDismissClickWhateverTheListShows()
	{
		QuestScript script = new QuestScript();

		script.noteOptionPressed(10_000L);

		assertTrue(script.isAwaitingOptionSelection(false, 10_600L));
		assertTrue(script.isAwaitingOptionSelection(false, 11_700L));
		assertFalse(script.isAwaitingOptionSelection(false, 11_800L));
	}

	@Test
	public void pressDoesNotHoldALaterUnrelatedMenu()
	{
		QuestScript script = new QuestScript();

		script.noteOptionPressed(10_000L);

		assertFalse(script.isAwaitingOptionSelection(false, 20_000L));
	}
}
