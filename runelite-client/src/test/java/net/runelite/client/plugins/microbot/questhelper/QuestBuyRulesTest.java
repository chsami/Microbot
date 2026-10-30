package net.runelite.client.plugins.microbot.questhelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class QuestBuyRulesTest
{
	private static List<QuestBuyRules.Candidate> games()
	{
		List<QuestBuyRules.Candidate> out = new ArrayList<>();
		for (int charge = 1; charge <= 8; charge++)
		{
			out.add(new QuestBuyRules.Candidate(3867 - (charge - 1) * 2, "Games necklace(" + charge + ")"));
		}
		return out;
	}

	@Test
	public void optionalOnlyFailureNeverStops()
	{
		assertFalse(QuestBuyRules.mustStop(0, false));
	}

	@Test
	public void requiredFailureStopsOnlyWhenNothingWasPlaced()
	{
		assertTrue(QuestBuyRules.mustStop(0, true));
		assertFalse(QuestBuyRules.mustStop(1, true));
	}

	@Test
	public void jewelleryBuysFullestChargeWithPrice()
	{
		// only (8) has price data, as on the real Grand Exchange
		assertEquals(3853, QuestBuyRules.pickBuyId(games(), id -> id == 3853 ? 1200 : -1));
	}

	@Test
	public void jewelleryNeverPicksTheLowChargeWhenNothingIsPriced()
	{
		assertEquals(3853, QuestBuyRules.pickBuyId(games(), id -> -1));
	}

	@Test
	public void jewelleryFallsBackToNextChargeWhenFullestUnpriced()
	{
		assertEquals(3855, QuestBuyRules.pickBuyId(games(), id -> id == 3855 ? 900 : -1));
	}

	@Test
	public void nonJewelleryStillPicksCheapestPriced()
	{
		List<QuestBuyRules.Candidate> c = Arrays.asList(new QuestBuyRules.Candidate(1, "Cake"),
			new QuestBuyRules.Candidate(2, "Cake copy"), new QuestBuyRules.Candidate(3, "Cake x"));
		assertEquals(2, QuestBuyRules.pickBuyId(c, id -> id == 1 ? Integer.MAX_VALUE : (id == 2 ? 10 : 50)));
		assertEquals(-1, QuestBuyRules.pickBuyId(Collections.emptyList(), id -> 1));
	}

	@Test
	public void jewelleryFamilyDetection()
	{
		assertTrue(QuestBuyRules.isChargedJewellery("Games necklace(1)"));
		assertTrue(QuestBuyRules.isChargedJewellery("Ring of dueling(8)"));
		assertTrue(QuestBuyRules.isChargedJewellery("Amulet of glory(4)"));
		assertFalse(QuestBuyRules.isChargedJewellery("Amulet of glory(t4)"));
		assertFalse(QuestBuyRules.isChargedJewellery("Prayer potion(4)"));
		assertFalse(QuestBuyRules.isChargedJewellery("Games necklace"));
	}

	@Test
	public void optionalSupplyIsNotRequiredButRequirementIs()
	{
		List<QuestPreflight.Need> needs = Arrays.asList(
			QuestPreflight.supplyNeed("Teleport", Arrays.asList(3867), 1, true));
		assertFalse(QuestPreflight.compute(needs, new HashMap<>()).get(0).required);
		List<QuestPreflight.Need> both = Arrays.asList(needs.get(0),
			new QuestPreflight.Need("Teleport", Arrays.asList(3867, 3865), 1, true));
		assertTrue(QuestPreflight.compute(both, new HashMap<>()).get(0).required);
	}

	@Test
	public void anyOwnedChargeSatisfiesTheNeedSoNothingIsBought()
	{
		List<Integer> family = new ArrayList<>();
		for (int i = 0; i < 8; i++)
		{
			family.add(3853 + i * 2);
		}
		List<QuestPreflight.Need> needs = Arrays.asList(
			QuestPreflight.supplyNeed("Teleport to Burthorpe", Arrays.asList(3867), 1, true));
		List<QuestPreflight.Need> widened = QuestPreflight.widen(needs, id -> family);
		assertTrue(widened.get(0).supply);
		// the bank holds a Games necklace(5), not the (1) the setup recorded
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(3859, 1);
		assertEquals(0, QuestPreflight.compute(widened, owned).get(0).missing);
		// without widening the (1) alone is missing
		assertEquals(1, QuestPreflight.compute(needs, owned).get(0).missing);
		// nothing owned: still a shortfall to buy
		assertEquals(1, QuestPreflight.compute(widened, new HashMap<>()).get(0).missing);
	}

	@Test
	public void widenKeepsNonVariantItemsAsTheyAre()
	{
		List<QuestPreflight.Need> needs = Arrays.asList(
			new QuestPreflight.Need("Hammer", Arrays.asList(2347), 1, false));
		List<QuestPreflight.Need> widened = QuestPreflight.widen(needs, Collections::singletonList);
		assertEquals(Arrays.asList(2347), widened.get(0).ids);
	}
}
