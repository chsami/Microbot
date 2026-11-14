package net.runelite.client.plugins.microbot.questhelper;

import net.runelite.client.plugins.microbot.Microbot;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
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

	private static long reachTheLimit(QuestScript script, Object step, long now)
	{
		for (int i = 0; i < 3; i++)
		{
			assertTrue(script.allowUnhandledOptionDismiss(step, now));
			now += 10_000L;
		}
		assertFalse(script.allowUnhandledOptionDismiss(step, now));
		return now;
	}

	@Test
	public void unhandledMenuOfTheSameStepStopsBeingDismissedAfterTheLimit()
	{
		QuestScript script = new QuestScript();
		Object step = new Object();

		long now = reachTheLimit(script, step, 100_000L);

		assertFalse(script.allowUnhandledOptionDismiss(step, now + 700L));
	}

	@Test
	public void newStepGetsItsOwnDismissAttempts()
	{
		QuestScript script = new QuestScript();

		long now = reachTheLimit(script, new Object(), 100_000L);

		assertTrue(script.allowUnhandledOptionDismiss(new Object(), now + 700L));
	}

	@Test
	public void pressedOptionRestoresTheDismissAttempts()
	{
		QuestScript script = new QuestScript();
		Object step = new Object();

		long now = reachTheLimit(script, step, 100_000L);

		script.noteOptionPressed(now + 700L);

		assertTrue(script.allowUnhandledOptionDismiss(step, now + 1_400L));
	}

	@Test
	public void menuClosedByThePlayerAfterTheLimitRestoresTheDismissAttempts()
	{
		QuestScript script = new QuestScript();
		Object step = new Object();

		long now = reachTheLimit(script, step, 100_000L);

		script.noteOptionDialogueClosed();

		assertTrue(script.allowUnhandledOptionDismiss(step, now + 700L));
		assertTrue(script.allowUnhandledOptionDismiss(step, now + 1_400L));
		assertTrue(script.allowUnhandledOptionDismiss(step, now + 2_100L));
		assertFalse(script.allowUnhandledOptionDismiss(step, now + 2_800L));
	}

	@Test
	public void menuClosedByTheScriptBelowTheLimitKeepsTheCount()
	{
		QuestScript script = new QuestScript();
		Object step = new Object();

		assertTrue(script.allowUnhandledOptionDismiss(step, 100_000L));
		script.noteOptionDialogueClosed();
		assertTrue(script.allowUnhandledOptionDismiss(step, 110_000L));
		script.noteOptionDialogueClosed();
		assertTrue(script.allowUnhandledOptionDismiss(step, 120_000L));
		script.noteOptionDialogueClosed();

		assertFalse(script.allowUnhandledOptionDismiss(step, 130_000L));
	}

	@Test
	public void stoppingTheScriptRestoresTheDismissAttempts()
	{
		QuestScript script = new QuestScript();
		Object step = new Object();

		long now = reachTheLimit(script, step, 100_000L);

		script.stopLoop();

		assertTrue(script.allowUnhandledOptionDismiss(step, now + 700L));
	}

	@Test
	public void quietMinuteWithoutAnUnhandledMenuRestoresTheDismissAttempts()
	{
		QuestScript script = new QuestScript();
		Object step = new Object();

		assertTrue(script.allowUnhandledOptionDismiss(step, 100_000L));
		assertTrue(script.allowUnhandledOptionDismiss(step, 110_000L));
		assertTrue(script.allowUnhandledOptionDismiss(step, 120_000L));

		assertTrue(script.allowUnhandledOptionDismiss(step, 181_000L));
		assertTrue(script.allowUnhandledOptionDismiss(step, 191_000L));
		assertTrue(script.allowUnhandledOptionDismiss(step, 201_000L));
		assertFalse(script.allowUnhandledOptionDismiss(step, 211_000L));
	}

	@Test
	public void heldMenuDoesNotExpireWhileItStaysOpen()
	{
		QuestScript script = new QuestScript();
		Object step = new Object();

		long now = reachTheLimit(script, step, 100_000L);
		for (int i = 0; i < 200; i++)
		{
			now += 700L;
			assertFalse(script.allowUnhandledOptionDismiss(step, now));
		}
	}

	@Test
	public void playerIsToldOncePerHeldMenu()
	{
		QuestScript script = new QuestScript();
		Object step = new Object();

		long now = reachTheLimit(script, step, 100_000L);
		assertTrue(script.takeUnhandledOptionNotice());
		assertFalse(script.allowUnhandledOptionDismiss(step, now + 700L));
		assertFalse(script.takeUnhandledOptionNotice());

		script.noteOptionDialogueClosed();
		reachTheLimit(script, step, now + 1_400L);

		assertTrue(script.takeUnhandledOptionNotice());
	}

	@Test
	public void pausedStatusIsReplacedOnceTheHeldMenuCloses()
	{
		QuestScript script = new QuestScript();
		Object step = new Object();

		reachTheLimit(script, step, 100_000L);
		String paused = "Quest helper: paused, I don't know which dialogue option to pick here. Choose it yourself (see the step text in the Quest Helper panel) and I will continue.";
		Microbot.status = paused;

		script.noteOptionDialogueClosed();

		assertEquals("Quest helper: continuing", Microbot.status);
	}

	@Test
	public void otherStatusIsKeptWhenTheHeldMenuCloses()
	{
		QuestScript script = new QuestScript();
		Object step = new Object();

		reachTheLimit(script, step, 100_000L);
		Microbot.status = "Walking";

		script.noteOptionDialogueClosed();

		assertEquals("Walking", Microbot.status);
	}

	@Test
	public void retryStatusCountsTheAttemptsAndClearsOnAPress()
	{
		QuestScript script = new QuestScript();
		Object step = new Object();

		assertTrue(script.allowUnhandledOptionDismiss(step, 100_000L));
		assertEquals("Quest helper: no matching dialogue option, retrying (1/3)", script.unhandledOptionRetryStatus());
		assertTrue(script.allowUnhandledOptionDismiss(step, 110_000L));
		assertEquals("Quest helper: no matching dialogue option, retrying (2/3)", script.unhandledOptionRetryStatus());
		Microbot.status = script.unhandledOptionRetryStatus();

		script.noteOptionPressed(111_000L);

		assertEquals("Quest helper: continuing", Microbot.status);
	}

	@Test
	public void noticePutsTheActionFirst()
	{
		assertEquals("Quest helper: paused, pick the dialogue option yourself and it will continue. It does not know which option to pick for: the current step",
			QuestScript.unhandledOptionNotice(null));
	}

	@Test
	public void pressDoesNotHoldALaterUnrelatedMenu()
	{
		QuestScript script = new QuestScript();

		script.noteOptionPressed(10_000L);

		assertFalse(script.isAwaitingOptionSelection(false, 20_000L));
	}
}

