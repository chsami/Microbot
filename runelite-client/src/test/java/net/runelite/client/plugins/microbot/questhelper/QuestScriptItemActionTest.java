package net.runelite.client.plugins.microbot.questhelper;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class QuestScriptItemActionTest
{
	private static final String[] FOOD = {"Eat", null, null, null, "Drop"};

	@Test
	public void useStepOnFoodDoesNotPickEatFromSubstring()
	{
		assertEquals("use", QuestScript.chooseItemAction(
			Collections.singletonList("Use the cooked meat on the wheat."), FOOD));
	}

	@Test
	public void stepWithoutMatchingActionFallsBackToUseNotFirstAction()
	{
		assertEquals("use", QuestScript.chooseItemAction(
			Collections.singletonList("Combine the bread with the cheese."), FOOD));
	}

	@Test
	public void explicitActionWordStillMatches()
	{
		assertEquals("Eat", QuestScript.chooseItemAction(
			Collections.singletonList("Eat the cake to continue."), FOOD));
	}

	@Test
	public void matchesActionAcrossTextLines()
	{
		String[] book = {"Read", null, null, null, "Drop"};
		assertEquals("Read", QuestScript.chooseItemAction(
			Arrays.asList("Talk to the wizard.", "Then read the book."), book));
	}

	@Test
	public void nullInputsFallBackToUse()
	{
		assertEquals("use", QuestScript.chooseItemAction(null, FOOD));
		assertEquals("use", QuestScript.chooseItemAction(Collections.singletonList("Eat it."), null));
	}
}
