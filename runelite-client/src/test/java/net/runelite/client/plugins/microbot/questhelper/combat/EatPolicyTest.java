package net.runelite.client.plugins.microbot.questhelper.combat;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EatPolicyTest
{
	@Test
	public void globalUsedWithoutSetupAndSetupOverrides()
	{
		assertEquals(30, EatPolicy.base(30, null));
		QuestCombatLoadout skip = QuestCombatLoadout.skipped();
		assertEquals(30, EatPolicy.base(30, skip));
		QuestCombatLoadout own = new QuestCombatLoadout();
		assertEquals(own.getEatAtPercent(), EatPolicy.base(30, own));
	}

	@Test
	public void clampedAndVarianceBounded()
	{
		assertEquals(10, EatPolicy.base(1, null));
		assertEquals(90, EatPolicy.base(99, null));
		Random rng = new Random(1);
		for (int i = 0; i < 500; i++)
		{
			int o = EatPolicy.rollOffset(rng);
			assertTrue(o >= -5 && o <= 5);
			int t = EatPolicy.threshold(50, null, o);
			assertTrue(t >= 45 && t <= 55);
		}
	}
}
