package net.runelite.client.plugins.microbot.questhelper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RandomEventDismissRuleTest
{
	@Test
	public void firstSightingDismisses()
	{
		assertTrue(new RandomEventDismissRule().shouldDismiss(7, 1_000));
	}

	@Test
	public void sameNpcIsRateLimited()
	{
		RandomEventDismissRule r = new RandomEventDismissRule();
		assertTrue(r.shouldDismiss(7, 1_000));
		assertFalse(r.shouldDismiss(7, 3_999));
		assertTrue(r.shouldDismiss(7, 4_000));
	}

	@Test
	public void differentNpcIsIndependent()
	{
		RandomEventDismissRule r = new RandomEventDismissRule();
		assertTrue(r.shouldDismiss(7, 1_000));
		assertTrue(r.shouldDismiss(8, 1_100));
	}
}
