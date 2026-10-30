package net.runelite.client.plugins.microbot.questhelper.planner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.Test;

public class QuestRandomPickerTest
{
	private static QuestRandomPicker.Candidate ok(String name)
	{
		return new QuestRandomPicker.Candidate(name, true, false, false);
	}

	private final List<QuestRandomPicker.Candidate> all = Arrays.asList(
		ok("B"), ok("A"), ok("C"),
		new QuestRandomPicker.Candidate("NotDoable", false, false, false),
		new QuestRandomPicker.Candidate("TwoPlayers", true, true, false),
		new QuestRandomPicker.Candidate("NeedsItems", true, false, true),
		ok("Failed"));

	@Test
	public void excludesUndoableMultiplayerBlockedAndSkippedQuests()
	{
		List<QuestRandomPicker.Candidate> out = QuestRandomPicker.eligible(all, new ArrayList<>(), Arrays.asList("Failed"));
		assertEquals(3, out.size());
		assertEquals("A", out.get(0).name);
	}

	@Test
	public void poolRestrictsTheChoice()
	{
		for (long seed = 0; seed < 20; seed++)
		{
			assertEquals("C", QuestRandomPicker.pick(all, Arrays.asList("C", "NotDoable"), null, new Random(seed)));
		}
		assertNull(QuestRandomPicker.pick(all, Arrays.asList("NotDoable"), null, new Random(1)));
	}

	@Test
	public void sameSeedGivesTheSamePickAndSeedsSpreadOverTheQuests()
	{
		assertEquals(QuestRandomPicker.pick(all, null, null, new Random(42)),
			QuestRandomPicker.pick(all, null, null, new Random(42)));
		Set<String> seen = new HashSet<>();
		Random random = new Random(7);
		for (int i = 0; i < 200; i++)
		{
			seen.add(QuestRandomPicker.pick(all, null, null, random));
		}
		assertEquals(new HashSet<>(Arrays.asList("A", "B", "C", "Failed")), seen);
	}

	@Test
	public void nothingDoableGivesNull()
	{
		assertNull(QuestRandomPicker.pick(new ArrayList<>(), null, null, new Random(1)));
	}

	@Test
	public void pauseStaysInsideTheConfiguredMinutes()
	{
		for (long seed = 0; seed < 200; seed++)
		{
			long ms = QuestRandomPicker.pauseMs(1, 10, 0.0, new Random(seed));
			assertTrue(ms >= 60_000L && ms <= 600_000L);
			long tired = QuestRandomPicker.pauseMs(1, 10, 1.0, new Random(seed));
			assertTrue(tired >= ms && tired <= 600_000L);
		}
		assertEquals(120_000L, QuestRandomPicker.pauseMs(2, 2, 0.5, new Random(3)));
		assertTrue(QuestRandomPicker.pauseMs(10, 1, 0, new Random(3)) >= 60_000L);
	}
}
