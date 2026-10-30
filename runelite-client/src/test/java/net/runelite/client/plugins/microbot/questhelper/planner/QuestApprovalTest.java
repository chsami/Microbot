package net.runelite.client.plugins.microbot.questhelper.planner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import net.runelite.client.plugins.microbot.questhelper.QuestPurchasePlan;
import org.junit.Test;

public class QuestApprovalTest
{
	private static QuestInfo.ItemLine line(String name, int qty, int owned, int bank, int price, boolean tradable)
	{
		return new QuestInfo.ItemLine(name, qty, Arrays.asList(1), tradable, owned, bank, price, "quest", "");
	}

	@Test
	public void coversApprovedItemsInsideTheBudgetPlusSlack()
	{
		QuestApproval approval = QuestApproval.of(Arrays.asList("Rope", "Lobster"), 1000);
		assertTrue(approval.covers(Arrays.asList("rope"), 900));
		assertTrue(approval.covers(Arrays.asList("Rope", "Lobster"), 1250));
		assertFalse(approval.covers(Arrays.asList("Rope"), 1251));
	}

	@Test
	public void anUnapprovedItemIsRefusedWithItsName()
	{
		QuestApproval approval = QuestApproval.of(Arrays.asList("Rope"), 1000);
		List<String> wanted = Arrays.asList("Rope", "Dragon bones");
		assertFalse(approval.covers(wanted, 10));
		assertTrue(approval.explainRefusal(wanted, 10).contains("Dragon bones"));
	}

	@Test
	public void spendingCountsAgainstTheBudget()
	{
		QuestApproval approval = QuestApproval.of(Arrays.asList("Rope"), 1000);
		approval.record(900);
		assertFalse(approval.covers(Arrays.asList("Rope"), 400));
		assertTrue(approval.covers(Arrays.asList("Rope"), 300));
	}

	@Test
	public void everythingApprovesAnything()
	{
		QuestApproval approval = QuestApproval.everything();
		assertTrue(approval.covers(Arrays.asList("anything"), Long.MAX_VALUE / 4));
		assertTrue(approval.isEverything());
	}

	@Test
	public void summaryMergesQuestsAndSubtractsWhatIsOwned()
	{
		QuestProfile a = QuestProfile.defaultFor("A", false);
		QuestProfile b = QuestProfile.defaultFor("B", false);
		QuestPlanSummary summary = QuestPlanSummary.of(Arrays.asList(
			new QuestPlanSummary.Entry(a, Arrays.asList(line("Rope", 1, 0, 0, 100, true), line("Bucket", 1, 1, 0, 50, true)),
				Arrays.asList()),
			new QuestPlanSummary.Entry(b, Arrays.asList(line("Rope", 2, 0, 1, 100, true)), Arrays.asList())));
		assertEquals(1, summary.lines().size());
		QuestPurchasePlan.Line rope = summary.lines().get(0);
		assertEquals("Rope", rope.name);
		assertEquals(2, rope.quantity);
		assertEquals(200, summary.total());
		assertTrue(summary.approval().covers(Arrays.asList("Rope", "Bucket"), 200));
		assertFalse(summary.approval().covers(Arrays.asList("Lobster"), 1));
	}

	@Test
	public void bankOnlyQuestsAndUntradableItemsAreNeverBought()
	{
		QuestProfile bankOnly = QuestProfile.defaultFor("A", false);
		bankOnly.setObtain(QuestProfile.Obtain.BANK_ONLY);
		QuestProfile normal = QuestProfile.defaultFor("B", false);
		QuestPlanSummary summary = QuestPlanSummary.of(Arrays.asList(
			new QuestPlanSummary.Entry(bankOnly, Arrays.asList(line("Rope", 1, 0, 0, 100, true)), Arrays.asList()),
			new QuestPlanSummary.Entry(normal, Arrays.asList(line("Quest scroll", 1, 0, 0, 0, false)),
				Arrays.asList(line("Stamina potion(4)", 2, 0, 0, 5000, true)))));
		assertEquals(1, summary.lines().size());
		assertEquals("Stamina potion(4)", summary.lines().get(0).name);
		assertEquals(10_000, summary.total());
		assertFalse(summary.approval().approves("Rope"));
	}
}
