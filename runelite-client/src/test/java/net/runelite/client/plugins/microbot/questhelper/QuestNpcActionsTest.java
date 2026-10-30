package net.runelite.client.plugins.microbot.questhelper;

import static org.junit.Assert.assertEquals;

import java.util.List;
import org.junit.Test;

public class QuestNpcActionsTest
{
	private static final String PICKPOCKET_STEP = "Pickpocket workmen until you get an animal skull and a specimen brush.";

	@Test
	public void pickpocketTextUsesStealFrom()
	{
		String[] workman = {"Talk-to", null, "Steal-from", null, null};
		assertEquals("Steal-from", QuestNpcActions.choose(List.of(PICKPOCKET_STEP), workman));
	}

	@Test
	public void plainPickpocketActionStillMatches()
	{
		String[] npc = {"Talk-to", "Pickpocket"};
		assertEquals("Pickpocket", QuestNpcActions.choose(List.of(PICKPOCKET_STEP), npc));
	}

	@Test
	public void actionNamedInTextBeatsSynonym()
	{
		String[] npc = {"Talk-to", "Steal", "Attack"};
		assertEquals("Attack", QuestNpcActions.choose(List.of("Attack the guard, then pickpocket him."), npc));
	}

	@Test
	public void noMatchFallsBackToTalkTo()
	{
		String[] npc = {"Attack", "Talk-to", "Steal-from"};
		assertEquals("Talk-to", QuestNpcActions.choose(List.of("Speak to the guide."), npc));
	}

	@Test
	public void noTalkToFallsBackToFirstAction()
	{
		String[] npc = {null, "Attack"};
		assertEquals("Attack", QuestNpcActions.choose(List.of("Wander around."), npc));
	}
}
