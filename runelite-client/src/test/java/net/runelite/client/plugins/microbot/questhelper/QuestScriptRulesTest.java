package net.runelite.client.plugins.microbot.questhelper;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class QuestScriptRulesTest
{
	@Test
	public void killImperativesAreKillSteps()
	{
		assertTrue(KillStepRule.isKillText(Collections.singletonList("Kill the Black Knight (level 33).")));
		assertTrue(KillStepRule.isKillText(Collections.singletonList("Defeat the <col=ff0000>ghast</col>.")));
		assertTrue(KillStepRule.isKillText(Collections.singletonList("Go upstairs and kill the guard.")));
	}

	@Test
	public void talkStepsAreNotKillSteps()
	{
		assertFalse(KillStepRule.isKillText(Collections.singletonList("Talk to Ivan to kill the vampyres.")));
		assertFalse(KillStepRule.isKillText(Collections.singletonList("Speak to Captain Siad about the fight.")));
		assertFalse(KillStepRule.isKillText(Collections.singletonList("Climb the ladder past the skilled guards.")));
		assertFalse(KillStepRule.isKillText(Collections.singletonList("Enter the Fight Arena.")));
	}

	@Test
	public void killStepNeedsAnAttackableTarget()
	{
		assertFalse(KillStepRule.isKillStep(Collections.singletonList("Kill the guard."), false));
		assertTrue(KillStepRule.isKillStep(Collections.singletonList("Kill the guard."), true));
	}

	@Test
	public void previousLineMatchesIgnoringTagsAndCase()
	{
		assertTrue(QuestDialogueRules.previousLineMatches(null, ""));
		assertTrue(QuestDialogueRules.previousLineMatches("Second question.",
			"<col=0>Second  question.</col> True or false: x"));
		assertFalse(QuestDialogueRules.previousLineMatches("Second question.", "Third question."));
		assertFalse(QuestDialogueRules.previousLineMatches("Second question.", null));
	}

	@Test
	public void fallbackOptionPrefersStepWords()
	{
		assertEquals("I want to buy some beer.", QuestDialogueRules.pickFallbackOption(
			Arrays.asList("Goodbye.", "I want to buy some beer.", "What's new?"),
			Collections.singletonList("Buy a beer from the bartender")));
	}

	@Test
	public void fallbackOptionSkipsRefusals()
	{
		assertEquals("Tell me about it.", QuestDialogueRules.pickFallbackOption(
			Arrays.asList("<col=0>[1] Goodbye.</col>", "No.", "Tell me about it."), Collections.emptyList()));
		assertNull(QuestDialogueRules.pickFallbackOption(Collections.emptyList(), Collections.emptyList()));
	}

	@Test
	public void tradeActionPreferredOnlyForShopSteps()
	{
		String[] actions = {"Talk-to", null, "Trade", null, null};
		assertEquals("Trade", QuestDialogueRules.preferTradeAction(true, actions));
		assertNull(QuestDialogueRules.preferTradeAction(false, actions));
		assertNull(QuestDialogueRules.preferTradeAction(true, new String[]{"Talk-to"}));
		assertTrue(QuestDialogueRules.isBuyText(Collections.singletonList("Buy a pot from the shop.")));
		assertFalse(QuestDialogueRules.isBuyText(Collections.singletonList("Talk to the buyer.")));
	}

	@Test
	public void shopChunksUseFixedBuyAmounts()
	{
		assertEquals(Arrays.asList(10, 5, 1, 1), QuestDialogueRules.shopBuyChunks(17));
	}
}
