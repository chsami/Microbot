package net.runelite.client.plugins.microbot.ddchopper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.EnumMap;
import java.util.Map;
import org.junit.Test;

public class AxeBuyAndClicksTest {
	private static Map<Axe, Integer> prices(int live) {
		Map<Axe, Integer> m = new EnumMap<>(Axe.class);
		for (Axe a : Axe.values()) if (a.isBuyable()) m.put(a, AxePlanner.effectivePrice(a, live));
		return m;
	}

	@Test
	public void level21PlayerWithBankCoinsBuysMithril() {
		AxePlanner.BuyDecision d = AxePlanner.decideBuy(AxeChoice.AUTO, 21, Axe.BRONZE, 20_000, 4_600_000, true, prices(900));
		assertEquals(AxePlanner.BuyStep.BUY, d.step);
		assertEquals(Axe.MITHRIL, d.axe);
		assertEquals(900, d.price);
		assertEquals(4_620_000L, d.coins);
	}

	@Test
	public void failedPriceLookupUsesFallbackNotUnaffordable() {
		assertEquals(AxePlanner.fallbackPrice(Axe.MITHRIL), AxePlanner.effectivePrice(Axe.MITHRIL, 0));
		assertEquals(AxePlanner.fallbackPrice(Axe.MITHRIL), AxePlanner.effectivePrice(Axe.MITHRIL, -1));
		assertEquals(1234, AxePlanner.effectivePrice(Axe.MITHRIL, 1234));
		AxePlanner.BuyDecision d = AxePlanner.decideBuy(AxeChoice.AUTO, 21, Axe.BRONZE, 20_000, 0, true, prices(0));
		assertEquals(Axe.MITHRIL, d.axe);
	}

	@Test
	public void everyBuyableAxeHasAFallback() {
		for (Axe a : Axe.values()) assertEquals(a.isBuyable(), AxePlanner.fallbackPrice(a) > 0);
	}

	@Test
	public void unscannedBankWithTooFewCoinsAsksForAScan() {
		AxePlanner.BuyDecision d = AxePlanner.decideBuy(AxeChoice.AUTO, 21, Axe.BRONZE, 50, 4_600_000, false, prices(900));
		assertEquals(AxePlanner.BuyStep.SCAN_BANK, d.step);
		assertNull(d.axe);
		assertEquals(50L, d.coins);
	}

	@Test
	public void scannedAndBrokeMeansNone() {
		AxePlanner.BuyDecision d = AxePlanner.decideBuy(AxeChoice.AUTO, 21, Axe.BRONZE, 50, 10, true, prices(900));
		assertEquals(AxePlanner.BuyStep.NONE, d.step);
	}

	@Test
	public void noDowngradeBuy() {
		AxePlanner.BuyDecision d = AxePlanner.decideBuy(AxeChoice.AUTO, 21, Axe.MITHRIL, 1_000_000, 0, true, prices(900));
		assertEquals(AxePlanner.BuyStep.NONE, d.step);
	}

	@Test
	public void withdrawsOnlyTheBudgetNeverTheWholeStack() {
		assertEquals(1_000L, 900 * 1 + 100L);
		// 900 gp axe: budget 1.3x + 100 = 1270
		assertEquals(1_270L, AxePlanner.coinBudget(900));
		assertEquals(1_270L, AxePlanner.coinsToWithdraw(900, 0, 4_600_000));
		assertEquals(1_070L, AxePlanner.coinsToWithdraw(900, 200, 4_600_000));
		assertEquals(0L, AxePlanner.coinsToWithdraw(900, 20_000, 4_600_000));
		assertEquals(500L, AxePlanner.coinsToWithdraw(900, 0, 500));
		assertEquals(0L, AxePlanner.coinsToWithdraw(900, 0, 0));
	}

	@Test
	public void successfulClicksNeverBlacklist() {
		ClickTracker<String> t = new ClickTracker<>();
		for (int i = 0; i < 50; i++) assertFalse(t.record("willow", true));
	}

	@Test
	public void fourNonStartingClicksBlacklist() {
		ClickTracker<String> t = new ClickTracker<>();
		assertFalse(t.record("oak", false));
		assertFalse(t.record("oak", false));
		assertFalse(t.record("oak", false));
		assertTrue(t.record("oak", false));
		assertFalse(t.record("oak", false));
	}

	@Test
	public void successResetsAndOtherTreesCountSeparately() {
		ClickTracker<String> t = new ClickTracker<>();
		t.record("a", false);
		t.record("a", false);
		t.record("a", false);
		assertFalse(t.record("a", true));
		assertFalse(t.record("a", false));
		t.record("b", false);
		t.record("a", false);
		assertFalse(t.record("a", false));
	}
}
