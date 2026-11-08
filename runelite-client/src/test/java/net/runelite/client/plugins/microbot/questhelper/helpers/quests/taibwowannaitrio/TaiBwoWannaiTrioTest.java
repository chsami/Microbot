package net.runelite.client.plugins.microbot.questhelper.helpers.quests.taibwowannaitrio;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.questhelper.QuestHelperConfig;
import net.runelite.client.plugins.microbot.questhelper.questinfo.QuestVarPlayer;
import net.runelite.client.plugins.microbot.questhelper.requirements.Requirement;
import net.runelite.client.plugins.microbot.questhelper.steps.ConditionalStep;
import net.runelite.client.plugins.microbot.questhelper.steps.QuestStep;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TaiBwoWannaiTrioTest
{
	private static final int QUEST_VARP = QuestVarPlayer.QUEST_TAI_BWO_WANNAI_TRIO.getId();

	private TaiBwoWannaiTrio helper;
	private Map<Integer, QuestStep> steps;
	private Client client;

	@Before
	public void setUp()
	{
		helper = new TaiBwoWannaiTrio();
		helper.setConfig(mock(QuestHelperConfig.class));
		steps = helper.loadSteps();
		client = mock(Client.class);
	}

	@Test
	public void startQuestChecksSessionStartLatchBeforeHutZone()
	{
		for (int state = 0; state <= 2; state++)
		{
			List<Map.Entry<Requirement, QuestStep>> entries = entriesOf(steps.get(state));
			assertSame(helper.startedQuestDuringSession, entries.get(1).getKey());
			assertSame(helper.talkToTimfrakuStart, entries.get(1).getValue());
			assertSame(helper.inTimfrakusHut, entries.get(2).getKey());
		}
	}

	@Test
	public void latchSeenDuringStartStepMarksStateSyncedAfterQuestAdvances()
	{
		when(client.getVarpValue(QUEST_VARP)).thenReturn(2);
		assertTrue(helper.startedQuestDuringSession.check(client));

		when(client.getVarpValue(QUEST_VARP)).thenReturn(3);
		assertTrue(helper.syncedState.check(client));
	}

	@Test
	public void questAlreadyPastStartStillNeedsJournalSync()
	{
		when(client.getVarpValue(QUEST_VARP)).thenReturn(3);
		assertFalse(helper.syncedState.check(client));
	}

	private static List<Map.Entry<Requirement, QuestStep>> entriesOf(QuestStep step)
	{
		assertTrue(step instanceof ConditionalStep);
		return new ArrayList<>(((ConditionalStep) step).getStepsMap().entrySet());
	}
}
