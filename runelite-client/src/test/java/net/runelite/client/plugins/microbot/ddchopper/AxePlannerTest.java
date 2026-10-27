package net.runelite.client.plugins.microbot.ddchopper;

import org.junit.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AxePlannerTest
{
	private static final Set<Axe> NONE = EnumSet.noneOf(Axe.class);

	@Test
	public void requirementsMatchTheWiki()
	{
		int[] wc = {1, 1, 6, 11, 21, 31, 41, 61, 71};
		int[] atk = {1, 1, 5, 10, 20, 30, 40, 60, 70};
		for (Axe axe : Axe.values())
		{
			assertEquals(wc[axe.ordinal()], axe.getWoodcuttingLevel());
			assertEquals(atk[axe.ordinal()], axe.getAttackLevel());
		}
	}

	@Test
	public void autoPicksBestOwnedUsableAxe()
	{
		Set<Axe> owned = EnumSet.of(Axe.BRONZE, Axe.STEEL, Axe.RUNE);
		assertEquals(Axe.BRONZE, AxePlanner.chooseOwned(AxeChoice.AUTO, owned, 1));
		assertEquals(Axe.STEEL, AxePlanner.chooseOwned(AxeChoice.AUTO, owned, 6));
		assertEquals(Axe.STEEL, AxePlanner.chooseOwned(AxeChoice.AUTO, owned, 40));
		assertEquals(Axe.RUNE, AxePlanner.chooseOwned(AxeChoice.AUTO, owned, 41));
	}

	@Test
	public void noUsableAxeGivesNull()
	{
		assertNull(AxePlanner.chooseOwned(AxeChoice.AUTO, EnumSet.of(Axe.RUNE), 40));
		assertNull(AxePlanner.chooseOwned(AxeChoice.AUTO, EnumSet.noneOf(Axe.class), 99));
	}

	@Test
	public void specificChoiceWinsWhenOwnedAndUsable()
	{
		Set<Axe> owned = EnumSet.of(Axe.IRON, Axe.MITHRIL);
		assertEquals(Axe.IRON, AxePlanner.chooseOwned(AxeChoice.IRON, owned, 30));
		// Not owned: falls back to the best owned axe.
		assertEquals(Axe.MITHRIL, AxePlanner.chooseOwned(AxeChoice.RUNE, owned, 30));
		// Owned but too high a level: falls back.
		assertEquals(Axe.IRON, AxePlanner.chooseOwned(AxeChoice.MITHRIL, owned, 20));
	}

	@Test
	public void wieldingNeedsAttackButChoppingDoesNot()
	{
		assertTrue(Axe.RUNE.canChop(41));
		assertFalse(Axe.RUNE.canWield(1));
		assertTrue(Axe.RUNE.canWield(40));
	}

	@Test
	public void buysBestAffordableUsableAxe()
	{
		Map<Axe, Integer> prices = new EnumMap<>(Axe.class);
		prices.put(Axe.BRONZE, 20);
		prices.put(Axe.IRON, 50);
		prices.put(Axe.STEEL, 200);
		prices.put(Axe.MITHRIL, 1000);
		prices.put(Axe.RUNE, 9000);
		assertEquals(Axe.IRON, AxePlanner.chooseToBuy(AxeChoice.AUTO, 5, 300, prices, NONE));
		assertEquals(Axe.STEEL, AxePlanner.chooseToBuy(AxeChoice.AUTO, 10, 400, prices, NONE));
		assertEquals(Axe.MITHRIL, AxePlanner.chooseToBuy(AxeChoice.AUTO, 25, 2000, prices, NONE));
		assertEquals(Axe.MITHRIL, AxePlanner.chooseToBuy(AxeChoice.AUTO, 45, 2000, prices, NONE));
		assertEquals(Axe.RUNE, AxePlanner.chooseToBuy(AxeChoice.AUTO, 45, 20000, prices, NONE));
		assertNull(AxePlanner.chooseToBuy(AxeChoice.AUTO, 45, 5, prices, NONE));
	}

	@Test
	public void crystalIsNeverBought()
	{
		Map<Axe, Integer> prices = new EnumMap<>(Axe.class);
		prices.put(Axe.CRYSTAL, 1);
		assertNull(AxePlanner.chooseToBuy(AxeChoice.CRYSTAL, 99, 1_000_000, prices, NONE));
	}

	@Test
	public void attemptsAreBoundedPerStep()
	{
		AxePlanner.Attempts attempts = new AxePlanner.Attempts();
		for (int i = 0; i < 3; i++)
		{
			assertTrue(attempts.tryStep(AxePlanner.Step.WITHDRAW_COINS));
		}
		assertFalse(attempts.tryStep(AxePlanner.Step.WITHDRAW_COINS));
		// Another step has its own budget.
		assertTrue(attempts.tryStep(AxePlanner.Step.FETCH_AXE));
	}

	@Test
	public void progressAndClearRestoreTheBudget()
	{
		AxePlanner.Attempts attempts = new AxePlanner.Attempts();
		for (int i = 0; i < 4; i++)
		{
			attempts.tryStep(AxePlanner.Step.WITHDRAW_COINS);
		}
		attempts.progress(AxePlanner.Step.WITHDRAW_COINS);
		assertTrue(attempts.tryStep(AxePlanner.Step.WITHDRAW_COINS));
		for (int i = 0; i < 4; i++)
		{
			attempts.tryStep(AxePlanner.Step.FETCH_AXE);
		}
		attempts.clear();
		assertTrue(attempts.tryStep(AxePlanner.Step.FETCH_AXE));
	}

	@Test
	public void walkingStepsGetMoreTriesThanBankActions()
	{
		AxePlanner.Attempts attempts = new AxePlanner.Attempts();
		for (int i = 0; i < 10; i++)
		{
			assertTrue(attempts.tryStep(AxePlanner.Step.OPEN_BANK));
		}
		assertFalse(attempts.tryStep(AxePlanner.Step.OPEN_BANK));
	}

	@Test
	public void coinsBetweenPriceAndBudgetAreNotAffordable()
	{
		Map<Axe, Integer> prices = new EnumMap<>(Axe.class);
		prices.put(Axe.MITHRIL, 1000);
		long budget = AxePlanner.coinBudget(1000);
		assertNull(AxePlanner.chooseToBuy(AxeChoice.AUTO, 25, 1000, prices, NONE));
		assertNull(AxePlanner.chooseToBuy(AxeChoice.AUTO, 25, budget - 1, prices, NONE));
		assertEquals(Axe.MITHRIL, AxePlanner.chooseToBuy(AxeChoice.AUTO, 25, budget, prices, NONE));
		AxePlanner.BuyDecision broke = AxePlanner.decideBuy(AxeChoice.AUTO, 25, Axe.BRONZE, 1000, 0, true, prices, NONE);
		assertEquals(AxePlanner.BuyStep.NONE, broke.step);
	}

	@Test
	public void abandonedAxeIsNeverChosenAgainEvenWhenACheaperOneIsAffordable()
	{
		Map<Axe, Integer> prices = new EnumMap<>(Axe.class);
		prices.put(Axe.MITHRIL, 1000);
		prices.put(Axe.ADAMANT, 5000);
		// A specific, unaffordable Adamant choice falls back to the cheaper Mithril ...
		assertEquals(Axe.MITHRIL, AxePlanner.chooseToBuy(AxeChoice.ADAMANT, 35, 2000, prices, NONE));
		AxePlanner.BuyDecision first = AxePlanner.decideBuy(AxeChoice.ADAMANT, 35, Axe.BRONZE, 2000, 0, true, prices, NONE);
		assertEquals(Axe.MITHRIL, first.axe);
		// ... and once that buy is abandoned there is nothing left to buy.
		Set<Axe> abandoned = EnumSet.of(Axe.MITHRIL);
		assertNull(AxePlanner.chooseToBuy(AxeChoice.ADAMANT, 35, 2000, prices, abandoned));
		AxePlanner.BuyDecision again = AxePlanner.decideBuy(AxeChoice.ADAMANT, 35, Axe.BRONZE, 2000, 0, true, prices, abandoned);
		assertEquals(AxePlanner.BuyStep.NONE, again.step);
	}
}
