package net.runelite.client.plugins.microbot.questhelper;

import java.util.Arrays;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class QuestPurchasePlanTest
{
	private static QuestPurchasePlan plan(long inv, long bank)
	{
		return new QuestPurchasePlan(Arrays.asList(
			new QuestPurchasePlan.Line("Rope", 1, 30), new QuestPurchasePlan.Line("Air rune", 6, 70)), inv, bank);
	}

	@Test
	public void totalsAndOfferFactor()
	{
		assertEquals(450, plan(0, 0).total());
		assertEquals(120, QuestPurchasePlan.offerPrice(100));
		assertEquals(1, QuestPurchasePlan.offerPrice(0));
	}

	@Test
	public void bankCoinsCountTowardAffordability()
	{
		QuestPurchasePlan p = plan(0, 4_748_117);
		assertTrue(p.affordable());
		assertEquals(4_748_117, p.coins());
		assertEquals(QuestPurchasePlan.Decision.BUY, p.decide(false));
		assertEquals(QuestPurchasePlan.Decision.ASK, p.decide(true));
	}

	@Test
	public void shortfallStopsWithoutConfirmationAndAsksWithIt()
	{
		QuestPurchasePlan p = plan(100, 50);
		assertFalse(p.affordable());
		assertEquals(300, p.shortfallGp());
		assertEquals(QuestPurchasePlan.Decision.INSUFFICIENT, p.decide(false));
		assertEquals(QuestPurchasePlan.Decision.ASK, p.decide(true));
		assertTrue(p.insufficientMessage().contains("Rope x1, Air rune x6"));
		assertTrue(p.insufficientMessage().contains("300"));
	}

	@Test
	public void unknownBankIsReportedAndNotCounted()
	{
		QuestPurchasePlan p = plan(450, -1);
		assertFalse(p.bankChecked());
		assertTrue(p.affordable());
		assertTrue(plan(10, -1).insufficientMessage().contains("bank not checked"));
	}

	@Test
	public void anUnaffordableOptionalLineIsDroppedAndTheRequiredOnesJudged()
	{
		QuestPurchasePlan p = new QuestPurchasePlan(Arrays.asList(
			new QuestPurchasePlan.Line("Rope", 1, 30), new QuestPurchasePlan.Line("Cape", 1, 5000, true)), 100, 0);
		QuestPurchasePlan shrunk = QuestPurchasePlan.shrinkToAcceptable(p, QuestPurchasePlan::affordable);
		assertTrue(shrunk.affordable());
		assertEquals("Rope x1", shrunk.describeItems());
		QuestPurchasePlan poor = new QuestPurchasePlan(p.lines(), 10, 0);
		assertFalse(QuestPurchasePlan.shrinkToAcceptable(poor, QuestPurchasePlan::affordable).affordable());
	}
}
