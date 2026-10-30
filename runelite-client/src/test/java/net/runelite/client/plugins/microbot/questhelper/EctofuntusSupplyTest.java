package net.runelite.client.plugins.microbot.questhelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.client.plugins.microbot.questhelper.EctofuntusSupply.Stage;
import org.junit.Test;

public class EctofuntusSupplyTest
{
	private static Map<Integer, Integer> map(int... pairs)
	{
		Map<Integer, Integer> m = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			m.put(pairs[i], pairs[i + 1]);
		}
		return m;
	}

	@Test
	public void shortfallSubtractsWhatIsCarried()
	{
		// 4 rounds wanted, 3 bones already carried, no pots or buckets
		Map<Integer, Integer> shortfall = EctofuntusSupply.shortfall(map(526, 4, 1931, 4, 1925, 4), map(526, 3));
		assertEquals(Integer.valueOf(1), shortfall.get(526));
		assertEquals(Integer.valueOf(4), shortfall.get(1931));
		assertEquals(Integer.valueOf(4), shortfall.get(1925));
		assertEquals(Integer.valueOf(0), EctofuntusSupply.shortfall(map(526, 2), map(526, 9)).get(526));
	}

	@Test
	public void theBankIsVisitedOnceThenTheRestIsBought()
	{
		EctofuntusSupply supply = new EctofuntusSupply();
		Map<Integer, Integer> shortfall = map(1931, 4, 1925, 4);
		assertEquals(Stage.BANK, supply.stage(shortfall, 0));
		supply.markNotInBank(1931, 10);
		assertEquals(Stage.BANK, supply.stage(shortfall, 20)); // the buckets were not looked for yet
		supply.markNotInBank(1925, 20);
		assertEquals(Stage.BUY, supply.stage(shortfall, 30));
	}

	@Test
	public void anythingCarriedMeansNoStage()
	{
		assertEquals(Stage.NONE, new EctofuntusSupply().stage(map(526, 0, 1931, 0), 0));
	}

	@Test
	public void theMemoryExpiresAfterTheCooldownAndCanBeForgotten()
	{
		EctofuntusSupply supply = new EctofuntusSupply();
		supply.markNotInBank(1931, 1_000);
		assertTrue(supply.knownNotInBank(1931, 1_000 + EctofuntusSupply.COOLDOWN_MS - 1));
		assertFalse(supply.knownNotInBank(1931, 1_000 + EctofuntusSupply.COOLDOWN_MS));
		supply.forget(1931);
		assertFalse(supply.knownNotInBank(1931, 1_001));
	}

	@Test
	public void aBankVisitTakesExactlyWhatIsShortAndFits()
	{
		assertEquals(3, EctofuntusSupply.withdrawCount(3, 10, 12));
		assertEquals(2, EctofuntusSupply.withdrawCount(3, 2, 12));
		assertEquals(0, EctofuntusSupply.withdrawCount(3, 0, 12));
		assertEquals(1, EctofuntusSupply.withdrawCount(3, 10, 1));
		assertTrue(EctofuntusSupply.bankFallsShort(4, 0));
		assertFalse(EctofuntusSupply.bankFallsShort(4, 4));
	}

	@Test
	public void roomIsMadeForTheInputsAndTheTokenStack()
	{
		assertEquals(0, EctofuntusSupply.slotsToFree(6, 12));
		assertEquals(1, EctofuntusSupply.slotsToFree(6, 6));
		assertEquals(7, EctofuntusSupply.slotsToFree(12, 6));
		assertEquals(0, EctofuntusSupply.slotsToFree(0, 3));
	}

	@Test
	public void fourSetsTakeOneTripOnceTheInventoryIsCleared()
	{
		// 12 free slots: 3 sets a trip, so 4 sets need two trips
		assertEquals(3, EctofuntusPlan.tripCapacity(12, 0));
		// after banking the unneeded items 13+ slots are free: all 4 sets in one trip
		assertEquals(4, EctofuntusPlan.tripCapacity(13, 0));
	}
}
