package net.runelite.client.plugins.microbot.questhelper.combat;

import static net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatHandler.AnswerAction.ACCEPT;
import static net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatHandler.AnswerAction.IGNORE;
import static net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatHandler.AnswerAction.REPROMPT;
import static net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatHandler.AnswerAction.STOP;
import static net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatHandler.decide;
import static net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatSetupDialog.Outcome.CANCEL;
import static net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatSetupDialog.Outcome.DISMISSED;
import static net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatSetupDialog.Outcome.SAVE;
import static net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatSetupDialog.Outcome.SKIP;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class QuestCombatAnswerDecisionTest
{
	@Test
	public void staleGenerationIsIgnored()
	{
		assertEquals(IGNORE, decide(CANCEL, 1, 2, 1));
		assertEquals(IGNORE, decide(SAVE, 1, 2, 1));
	}

	@Test
	public void saveSkipAcceptedCancelStops()
	{
		assertEquals(ACCEPT, decide(SAVE, 3, 3, 1));
		assertEquals(ACCEPT, decide(SKIP, 3, 3, 1));
		assertEquals(STOP, decide(CANCEL, 3, 3, 1));
	}

	@Test
	public void dismissedRepromptsOnceThenStops()
	{
		assertEquals(REPROMPT, decide(DISMISSED, 3, 3, 1));
		assertEquals(STOP, decide(DISMISSED, 3, 3, 2));
	}
}
