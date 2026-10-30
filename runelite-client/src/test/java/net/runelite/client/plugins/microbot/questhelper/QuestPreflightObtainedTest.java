package net.runelite.client.plugins.microbot.questhelper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class QuestPreflightObtainedTest
{
	@Test
	public void runeMysteriesGivenItemsExcluded()
	{
		assertTrue(QuestPreflight.obtainedDuringQuest("RUNE_MYSTERIES", "Air talisman", null, false, true));
		assertTrue(QuestPreflight.obtainedDuringQuest("RUNE_MYSTERIES", "Research notes", null, false, true));
		assertTrue(QuestPreflight.obtainedDuringQuest("RUNE_MYSTERIES", "Research package", null, false, true));
	}

	@Test
	public void tooltipFlagExcluded()
	{
		assertTrue(QuestPreflight.obtainedDuringQuest("X", "Hammer", "Can be obtained during the quest.", false, true));
	}

	@Test
	public void notStartedLaterStepOnlyExcluded()
	{
		assertTrue(QuestPreflight.obtainedDuringQuest("X", "Key", null, true, false));
		assertFalse(QuestPreflight.obtainedDuringQuest("X", "Key", null, true, true));
		assertFalse(QuestPreflight.obtainedDuringQuest("X", "Key", null, false, false));
	}
}
