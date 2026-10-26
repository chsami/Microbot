package net.runelite.client.plugins.microbot.questhelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class QuestCoinsAndSpendTest
{
	@Test
	public void theLiveGoblinMailRetryTakesTheShortfallFromTheBank()
	{
		// need 1,040, 963 in the inventory, about 4.6M in the bank
		assertEquals(77, QuestCoins.toWithdraw(1_040, 963, 4_600_000));
	}

	@Test
	public void noBankTripWhenTheInventoryAlreadyPays()
	{
		assertEquals(0, QuestCoins.toWithdraw(1_040, 1_040, 0));
		assertEquals(0, QuestCoins.toWithdraw(500, 963, 4_600_000));
	}

	@Test
	public void stopsOnlyWhenTheInventoryAndTheBankTogetherCannotPay()
	{
		assertEquals(-1, QuestCoins.toWithdraw(1_040, 963, 50));
		assertEquals(77, QuestCoins.toWithdraw(1_040, 963, 77));
		assertEquals(-1, QuestCoins.toWithdraw(1_040, 0, 1_039));
	}

	@Test
	public void anUnreadBankIsTriedAndCheckedAfterwards()
	{
		assertEquals(77, QuestCoins.toWithdraw(1_040, 963, -1));
	}

	@Test
	public void theStopMessageNamesBothPurses()
	{
		String message = QuestCoins.shortMessage("Goblin mail", 520, 1_040, 963, 10);
		assertTrue(message.contains("Goblin mail"));
		assertTrue(message.contains("963 in the inventory"));
		assertTrue(message.contains("10 in the bank"));
	}

	@Test
	public void automatedModeBuysInsideTheMaxSpendAndSkipsAbove()
	{
		assertEquals(QuestSpendRules.Decision.BUY, QuestSpendRules.decide(50_000, 100_000, 1_000_000));
		assertEquals(QuestSpendRules.Decision.BUY, QuestSpendRules.decide(100_000, 100_000, 100_000));
		assertEquals(QuestSpendRules.Decision.OVER_MAX_SPEND, QuestSpendRules.decide(100_001, 100_000, 5_000_000));
		assertEquals(QuestSpendRules.Decision.CANNOT_AFFORD, QuestSpendRules.decide(60_000, 100_000, 10_000));
		assertEquals(QuestSpendRules.Decision.OVER_MAX_SPEND, QuestSpendRules.decide(1, 0, 1_000));
	}
}
