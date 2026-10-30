package net.runelite.client.plugins.microbot.questhelper.combat;

import static net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatHandler.PromptDecision.AUTO_APPLY;
import static net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatHandler.PromptDecision.SHOW;
import static net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatHandler.decidePrompt;
import static org.junit.Assert.assertEquals;

import net.runelite.client.plugins.microbot.questhelper.QuestHelperConfig.SetupPromptOption;
import org.junit.Test;

public class QuestCombatPromptPolicyTest
{
	@Test
	public void perQuestSetupAlwaysApplies()
	{
		for (SetupPromptOption p : SetupPromptOption.values())
		{
			assertEquals(AUTO_APPLY, decidePrompt(p, true, false, true, true));
		}
	}

	@Test
	public void neverAlwaysAutoApplies()
	{
		assertEquals(AUTO_APPLY, decidePrompt(SetupPromptOption.NEVER, false, false, true, true));
	}

	@Test
	public void alwaysShows()
	{
		assertEquals(SHOW, decidePrompt(SetupPromptOption.ALWAYS, false, true, false, false));
	}

	@Test
	public void onlyWhenNeeded()
	{
		SetupPromptOption p = SetupPromptOption.ONLY_WHEN_NEEDED;
		assertEquals(AUTO_APPLY, decidePrompt(p, false, true, false, false));
		assertEquals(AUTO_APPLY, decidePrompt(p, false, false, false, false));
		assertEquals(AUTO_APPLY, decidePrompt(p, false, true, true, false));
		assertEquals(SHOW, decidePrompt(p, false, false, true, false));
		assertEquals(SHOW, decidePrompt(p, false, true, true, true));
	}
}
