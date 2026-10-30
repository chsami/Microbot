package net.runelite.client.plugins.microbot.questhelper.planner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class QuestPlannerFilterTest
{
	private final List<QuestPlannerFilter.Row> rows = Arrays.asList(
		new QuestPlannerFilter.Row("Cooks Assistant", true, ""),
		new QuestPlannerFilter.Row("Dragon Slayer I", false, "32 Quest points"),
		new QuestPlannerFilter.Row("Dragon Slayer II", false, "Dragon Slayer I"),
		new QuestPlannerFilter.Row("Priest in Peril", true, ""));

	@Test
	public void defaultsToDoableQuestsOnly()
	{
		List<QuestPlannerFilter.Row> out = QuestPlannerFilter.filter(rows, "", false);
		assertEquals(2, out.size());
		assertEquals("Cooks Assistant", out.get(0).name);
	}

	@Test
	public void showAllKeepsGreyedQuestsWithTheirReason()
	{
		List<QuestPlannerFilter.Row> out = QuestPlannerFilter.filter(rows, "", true);
		assertEquals(4, out.size());
		assertEquals("32 Quest points", out.get(1).reason);
	}

	@Test
	public void searchMatchesEveryWordInAnyOrderAndCase()
	{
		assertEquals(2, QuestPlannerFilter.filter(rows, "slayer dragon", true).size());
		assertEquals(0, QuestPlannerFilter.filter(rows, "slayer", false).size());
		assertEquals(1, QuestPlannerFilter.filter(rows, "PRIEST", false).size());
	}

	@Test
	public void reasonLineIsCapped()
	{
		assertEquals("a; b (+1 more)", QuestPlannerFilter.reasonOf(Arrays.asList("a", "b", "c"), 2));
		assertTrue(QuestPlannerFilter.reasonOf(Arrays.asList(), 2).isEmpty());
	}
}
