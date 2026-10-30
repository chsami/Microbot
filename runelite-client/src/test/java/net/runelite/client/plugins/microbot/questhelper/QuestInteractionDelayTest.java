package net.runelite.client.plugins.microbot.questhelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Random;
import net.runelite.client.plugins.microbot.util.antiban.HumanizedTiming;
import org.junit.Test;

public class QuestInteractionDelayTest
{
	@Test
	public void nothingIsAllowedInCombatKillStepsDialogueOrLowHealth()
	{
		assertFalse(QuestInteractionDelay.allowed(true, false, false, false, "Climb the ladder."));
		assertFalse(QuestInteractionDelay.allowed(false, true, false, false, "Kill the mourner."));
		assertFalse(QuestInteractionDelay.allowed(false, false, true, false, "Talk to Elena."));
		assertFalse(QuestInteractionDelay.allowed(false, false, false, true, "Climb the ladder."));
		assertTrue(QuestInteractionDelay.allowed(false, false, false, false, "Climb the ladder."));
		assertTrue(QuestInteractionDelay.allowed(false, false, false, false, null));
	}

	@Test
	public void timedAndSpamClickStepsAreNeverPaused()
	{
		assertFalse(QuestInteractionDelay.allowed(false, false, false, false, "Pick the lock of the prison door."));
		assertFalse(QuestInteractionDelay.allowed(false, false, false, false, "Run away from the guard."));
		assertFalse(QuestInteractionDelay.allowed(false, false, false, false, "Dodge the boulders."));
		assertFalse(QuestInteractionDelay.allowed(false, false, false, false, "Quickly open the gate."));
	}

	@Test
	public void timedWordsMatchWholeWordsOnly()
	{
		assertFalse(QuestInteractionDelay.allowed(false, false, false, false, "Keep clicking the lever."));
		assertFalse(QuestInteractionDelay.allowed(false, false, false, false, "Click it repeatedly."));
		assertFalse(QuestInteractionDelay.allowed(false, false, false, false, "Search the crate before leaving."));
		assertFalse(QuestInteractionDelay.allowed(false, false, false, false, "Spam the door until it opens."));
		assertFalse(QuestInteractionDelay.allowed(false, false, false, false, "Watch out for the trap."));
		assertTrue(QuestInteractionDelay.allowed(false, false, false, false, "Talk to Brunhilda about the rune stones."));
		assertTrue(QuestInteractionDelay.allowed(false, false, false, false, "Climb the ladder in the keepsake room."));
	}

	@Test
	public void noDelayWhenNotAllowedOrWithoutATimingModel()
	{
		HumanizedTiming timing = new HumanizedTiming(new Random(1), true);
		Random random = new Random(1);
		for (int i = 0; i < 200; i++)
		{
			assertEquals(0, QuestInteractionDelay.delayMs(timing, random, false));
			assertEquals(0, QuestInteractionDelay.delayMs(null, random, true));
		}
	}

	@Test
	public void aboutAThirdOfRestedClicksPauseAndEveryPauseIsBounded()
	{
		HumanizedTiming timing = new HumanizedTiming(new Random(7), true);
		Random random = new Random(11);
		int pauses = 0;
		int clicks = 4000;
		for (int i = 0; i < clicks; i++)
		{
			long delay = QuestInteractionDelay.delayMs(timing, random, true);
			if (delay > 0)
			{
				pauses++;
				assertTrue("delay " + delay, delay >= QuestInteractionDelay.MIN_DELAY_MS
					&& delay <= QuestInteractionDelay.MAX_DELAY_MS);
			}
		}
		double share = pauses / (double) clicks;
		assertTrue("share " + share, share > 0.26 && share < 0.34);
	}

	@Test
	public void tirednessRaisesTheChanceAndTheLength()
	{
		HumanizedTiming rested = new HumanizedTiming(new Random(3), true);
		HumanizedTiming tired = new HumanizedTiming(new Random(3), true);
		tired.update(0, false);
		tired.update(200L * 60_000L, false); // far past full fatigue
		assertTrue(tired.fatigue() > 0.99);
		Random a = new Random(5);
		Random b = new Random(5);
		int restedPauses = 0;
		int tiredPauses = 0;
		long restedTotal = 0;
		long tiredTotal = 0;
		for (int i = 0; i < 4000; i++)
		{
			long r = QuestInteractionDelay.delayMs(rested, a, true);
			long t = QuestInteractionDelay.delayMs(tired, b, true);
			restedPauses += r > 0 ? 1 : 0;
			tiredPauses += t > 0 ? 1 : 0;
			restedTotal += r;
			tiredTotal += t;
		}
		assertTrue(tiredPauses > restedPauses);
		assertTrue(tiredTotal / Math.max(1, tiredPauses) > restedTotal / Math.max(1, restedPauses));
	}
}
