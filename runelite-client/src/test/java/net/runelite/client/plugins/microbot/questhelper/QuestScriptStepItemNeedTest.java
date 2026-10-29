package net.runelite.client.plugins.microbot.questhelper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class QuestScriptStepItemNeedTest
{
	@Test
	public void ladderCarryAlongDoesNotTriggerABankTrip()
	{
		assertFalse(QuestScript.stepNeedsItemRequirement(false, "Climb up the ladder in the chapel room east of you.", false, false));
		assertFalse(QuestScript.stepNeedsItemRequirement(false, "Push the wall to the south-east to enter the storage room", false, false));
		assertFalse(QuestScript.stepNeedsItemRequirement(false, null, false, false));
	}

	@Test
	public void equippedHighlightedAndIconItemsAreFetched()
	{
		assertTrue(QuestScript.stepNeedsItemRequirement(false, "Enter the fortress.", true, false));
		assertTrue(QuestScript.stepNeedsItemRequirement(false, "Go to the hole.", false, true));
		assertTrue(QuestScript.stepNeedsItemRequirement(true, "Go to the hole.", false, false));
	}

	@Test
	public void handInTextIsFetched()
	{
		assertTrue(QuestScript.stepNeedsItemRequirement(false, "Bring Drezel 50 UNNOTED rune/pure essence in the underground of the Salve Temple.", false, false));
		assertTrue(QuestScript.stepNeedsItemRequirement(false, "Hand the ring to Bob.", false, false));
		assertTrue(QuestScript.stepNeedsItemRequirement(false, "USE the cabbage on the hole.", false, false));
		assertFalse(QuestScript.stepNeedsItemRequirement(false, "Browse the housewares.", false, false));
	}

	/** Dragon Slayer I, Klarense: "buy his boat" with 2,000 coins listed; the bot stood there with no coins. */
	@Test
	public void payingAnNpcNeedsTheCoins()
	{
		String klarense = "Talk to Klarense on the south Port Sarim docks and buy his boat.";
		assertTrue(QuestScript.stepNeedsItemRequirement(false, klarense, false, false));
		assertTrue(QuestScript.stepNeedsItemRequirement(false, "Pay the ferryman.", false, false));
		assertTrue(QuestScript.stepNeedsItemRequirement(false, "Bribe the guard.", false, false));
		// coins on an NPC step count whatever the text says; other carry-alongs stay informational
		assertTrue(QuestScript.stepNeedsItemRequirement(false, "Talk to the captain.", false, false, true));
		assertFalse(QuestScript.stepNeedsItemRequirement(false, "Talk to the captain.", false, false, false));
		assertFalse("a word inside another word is no payment", QuestScript.stepNeedsItemRequirement(false, "Climb the payload ladder.", false, false));
	}
}
