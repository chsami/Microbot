package net.runelite.client.plugins.microbot.questhelper.scripted;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AttemptBudgetTest
{
	@Test
	public void cutsOffAStallAndForgivesProgress()
	{
		AttemptBudget budget = new AttemptBudget();
		assertTrue(budget.tryUse("TALK", 1, 2));
		assertTrue(budget.tryUse("TALK", 1, 2));
		assertFalse(budget.tryUse("TALK", 1, 2));
		assertTrue(budget.tryUse("TALK", 2, 2));
	}

	@Test
	public void keysAreIndependent()
	{
		AttemptBudget budget = new AttemptBudget();
		assertTrue(budget.tryUse("A", 1, 1));
		assertFalse(budget.tryUse("A", 1, 1));
		assertTrue(budget.tryUse("B", 1, 1));
	}
}
