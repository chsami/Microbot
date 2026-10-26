package net.runelite.client.plugins.microbot.questhelper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class KillStepRuleTest
{
	@Test
	public void twilightsPromiseKnightIsKillText()
	{
		assertTrue(KillStepRule.isKillText(Collections.singletonList(
			"Defeat the knight, swapping combat styles to bypass his prayers.")));
	}

	@Test
	public void talkStepMentioningFightIsNotKillText()
	{
		assertFalse(KillStepRule.isKillText(Arrays.asList("Talk to Ivan about the kill")));
	}

	@Test
	public void attacksWhenNotInteractingWithTargetEvenIfInCombat()
	{
		assertTrue(KillStepRule.shouldClickAttack(false, 5_000, 1_200));
	}

	@Test
	public void doesNotClickWhenAlreadyFightingOrRateLimited()
	{
		assertFalse(KillStepRule.shouldClickAttack(true, 5_000, 1_200));
		assertFalse(KillStepRule.shouldClickAttack(false, 300, 1_200));
	}
}
