package net.runelite.client.plugins.microbot.questhelper;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class QuestScriptCarryCapTest
{
	@Test
	public void capsFiftyEssenceToAnEmptyInventory()
	{
		assertEquals(28, QuestScript.carryCap(50, 0, 28, 0));
	}

	@Test
	public void keepsSlotsForOtherMissingItems()
	{
		assertEquals(27, QuestScript.carryCap(50, 0, 28, 1));
	}

	@Test
	public void countsWhatIsAlreadyHeld()
	{
		assertEquals(27, QuestScript.carryCap(50, 27, 1, 1));
		assertEquals(27, QuestScript.carryCap(50, 27, 0, 0));
	}

	@Test
	public void neverRaisesTheRequirement()
	{
		assertEquals(5, QuestScript.carryCap(5, 0, 28, 0));
	}
}
