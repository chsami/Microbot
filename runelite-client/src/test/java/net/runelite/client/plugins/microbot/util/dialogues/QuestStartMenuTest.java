package net.runelite.client.plugins.microbot.util.dialogues;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class QuestStartMenuTest
{
	@Test
	public void matchesQuestStartTitles()
	{
		assertTrue(QuestStartMenu.isTitle("Start Doric's Quest?"));
		assertTrue(QuestStartMenu.isTitle("<col=000080>Start Cook's Assistant?</col>"));
		assertTrue(QuestStartMenu.isTitle("  Start The Restless Ghost?  "));
	}

	@Test
	public void rejectsOtherTitles()
	{
		assertFalse(QuestStartMenu.isTitle("Select an Option"));
		assertFalse(QuestStartMenu.isTitle("Start Doric's Quest"));
		assertFalse(QuestStartMenu.isTitle("Restart Doric's Quest?"));
		assertFalse(QuestStartMenu.isTitle("Start ?"));
		assertFalse(QuestStartMenu.isTitle(null));
		assertFalse(QuestStartMenu.isTitle(""));
	}

	@Test
	public void parsesOptionsAfterTitle()
	{
		List<String> opts = QuestStartMenu.parseOptions(Arrays.asList("Start Doric's Quest?", "Yes.", "", "<col=ff0000>No.</col>", null));
		assertEquals(Arrays.asList("Yes.", "No."), opts);
		assertTrue(QuestStartMenu.parseOptions(Collections.emptyList()).isEmpty());
		assertTrue(QuestStartMenu.parseOptions(null).isEmpty());
	}

	@Test
	public void findsYes()
	{
		assertEquals(0, QuestStartMenu.yesIndex(Arrays.asList("Yes.", "No.")));
		assertEquals(1, QuestStartMenu.yesIndex(Arrays.asList("No.", "Yes")));
		assertEquals(-1, QuestStartMenu.yesIndex(Arrays.asList("No.")));
	}
}
