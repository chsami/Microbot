package net.runelite.client.plugins.microbot.questhelper.combat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.client.plugins.microbot.questhelper.QuestPreflight;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class QuestSupplyNeedsTest
{
	private static QuestCombatLoadout loadout()
	{
		QuestCombatLoadout l = new QuestCombatLoadout();
		l.setSkip(false);
		l.setFoodQty(0);
		l.setUseStamina(true);
		l.setStaminaType(QuestCombatLoadout.StaminaType.STAMINA);
		l.setStaminaQty(2);
		return l;
	}

	@Test
	public void staminaIsNeededWithAllDoseIds()
	{
		List<QuestPreflight.Need> needs = QuestSupplyNeeds.fromLoadout(loadout(), n -> -1, id -> true);
		assertEquals(1, needs.size());
		assertEquals(2, needs.get(0).quantity);
		assertTrue(needs.get(0).supply);
		assertTrue(needs.get(0).ids.contains(12627));
	}

	@Test
	public void bankedLowerDoseCountsAndOnlyShortfallIsBought()
	{
		List<QuestPreflight.Need> needs = QuestSupplyNeeds.fromLoadout(loadout(), n -> -1, id -> true);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(12629, 1); // Stamina potion(2) in the bank
		QuestPreflight.Shortfall s = QuestPreflight.compute(needs, owned).get(0);
		assertEquals(1, s.owned);
		assertEquals(1, s.missing);
		assertEquals("Stamina potion (short 1 - will buy)", QuestPreflight.purchaseLabel(s));
	}

	@Test
	public void missingSupplyIsLabelledAndNothingUnselectedIsAdded()
	{
		QuestCombatLoadout l = loadout();
		l.setUseStamina(false);
		assertTrue(QuestSupplyNeeds.fromLoadout(l, n -> 1, id -> true).isEmpty());
		l.setUseStamina(true);
		QuestPreflight.Shortfall s = QuestPreflight.compute(
			QuestSupplyNeeds.fromLoadout(l, n -> -1, id -> true), new HashMap<>()).get(0);
		assertEquals("Stamina potion (missing - will buy)", QuestPreflight.purchaseLabel(s));
	}

	@Test
	public void foodAndCustomItemsOnlyWhenResolvableAndTradable()
	{
		QuestCombatLoadout l = loadout();
		l.setUseStamina(false);
		l.setFood("Lobster");
		l.setFoodQty(5);
		l.getInventoryItems().add(new QuestCombatLoadout.InventoryItem("Rope", 954, 1, false));
		l.getInventoryItems().add(new QuestCombatLoadout.InventoryItem("Untradable thing", 99999, 1, false));
		List<QuestPreflight.Need> needs = QuestSupplyNeeds.fromLoadout(l, n -> n.equals("Lobster") ? 379 : -1, id -> id != 99999);
		assertEquals(Arrays.asList("Lobster", "Rope"), Arrays.asList(needs.get(0).name, needs.get(1).name));
		assertEquals(2, needs.size());
	}

	@Test
	public void staminaVariantsCountAsStamina()
	{
		assertTrue(QuestPotions.isRunEnergyName("Extended stamina potion"));
		assertTrue(QuestPotions.isRunEnergyName("Stamina potion"));
		assertFalse(QuestPotions.isRunEnergyName("Prayer potion"));
		assertTrue(QuestPotions.runPotionMatches("Extended stamina potion", "Stamina potion"));
		assertFalse(QuestPotions.runPotionMatches("Extended stamina potion", "Energy potion"));
	}

	@Test
	public void extendedStaminaDosesCountTowardStamina()
	{
		List<QuestPreflight.Need> needs = QuestSupplyNeeds.fromLoadout(loadout(), n -> -1, id -> true);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(31638, 2); // Extended stamina potion(4) x2
		assertEquals(0, QuestPreflight.compute(needs, owned).get(0).missing);
	}
}
