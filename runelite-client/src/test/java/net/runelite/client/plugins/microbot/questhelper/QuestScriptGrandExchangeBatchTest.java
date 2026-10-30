package net.runelite.client.plugins.microbot.questhelper;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class QuestScriptGrandExchangeBatchTest
{
	@Test
	public void freeToPlaySlotsBuyFirstThreeOfSix()
	{
		List<String> toBuy = new ArrayList<>(Arrays.asList("a", "b", "c", "d", "e", "f"));
		assertTrue(QuestScript.trimToGrandExchangeSlots(toBuy, 3));
		assertEquals(Arrays.asList("a", "b", "c"), toBuy);
	}

	@Test
	public void eighteenItemsOnEightSlotsTakeThreeRounds()
	{
		List<String> left = new ArrayList<>();
		for (int i = 0; i < 18; i++)
		{
			left.add("item" + i);
		}
		int rounds = 0;
		while (!left.isEmpty())
		{
			List<String> batch = new ArrayList<>(left);
			QuestScript.trimToGrandExchangeSlots(batch, 8);
			left.removeAll(batch);
			rounds++;
		}
		assertEquals(3, rounds);
	}

	@Test
	public void enoughSlotsKeepsEveryItem()
	{
		List<String> toBuy = new ArrayList<>(Arrays.asList("a", "b", "c"));
		assertFalse(QuestScript.trimToGrandExchangeSlots(toBuy, 8));
		assertEquals(Arrays.asList("a", "b", "c"), toBuy);
	}

	@Test
	public void exactSlotCountKeepsEveryItem()
	{
		List<String> toBuy = new ArrayList<>(Arrays.asList("a", "b", "c"));
		assertFalse(QuestScript.trimToGrandExchangeSlots(toBuy, 3));
		assertEquals(3, toBuy.size());
	}

	@Test
	public void singleFreeSlotBuysOneItemAtATime()
	{
		List<String> toBuy = new ArrayList<>(Arrays.asList("a", "b"));
		assertTrue(QuestScript.trimToGrandExchangeSlots(toBuy, 1));
		assertEquals(Arrays.asList("a"), toBuy);
	}

	@Test
	public void requiredItemsAreKeptBeforeOptionalOnes()
	{
		List<String> toBuy = new ArrayList<>(Arrays.asList("opt1", "req1", "opt2", "req2"));
		assertTrue(QuestScript.trimToGrandExchangeSlots(toBuy, 2, item -> item.startsWith("opt")));
		assertEquals(Arrays.asList("req1", "req2"), toBuy);
	}
}
