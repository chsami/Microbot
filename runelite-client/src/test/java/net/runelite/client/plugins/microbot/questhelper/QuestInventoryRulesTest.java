package net.runelite.client.plugins.microbot.questhelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import net.runelite.client.plugins.microbot.questhelper.planner.QuestProfile;
import org.junit.Test;

public class QuestInventoryRulesTest
{
	@Test
	public void trivialEnemiesNeedNoFood()
	{
		assertEquals(0, QuestInventoryRules.foodQtyForMaxLevel(1));
		assertEquals(0, QuestInventoryRules.foodQtyForMaxLevel(10));
	}

	@Test
	public void realFightsGetAFewFood()
	{
		assertEquals(5, QuestInventoryRules.foodQtyForMaxLevel(11));
		assertEquals(5, QuestInventoryRules.foodQtyForMaxLevel(60));
	}

	@Test
	public void unknownLevelIsNotTreatedAsWeak()
	{
		assertEquals(5, QuestInventoryRules.foodQtyForMaxLevel(0));
	}

	@Test
	public void defaultProfileFoodFollowsTheCount()
	{
		assertEquals("", QuestProfile.defaultFor("Witch's Potion", true, 0).getSupplies().getFood());
		assertEquals(0, QuestProfile.defaultFor("Witch's Potion", true, 0).getSupplies().getFoodQty());
		QuestProfile.Supplies five = QuestProfile.defaultFor("Q", true, 5).getSupplies();
		assertEquals("Lobster", five.getFood());
		assertEquals(5, five.getFoodQty());
	}

	@Test
	public void questStartKeepsCoinsRunesNeededAndSelectedSupplies()
	{
		Set<Integer> needed = new HashSet<>(Arrays.asList(1929));
		Set<String> supplies = new HashSet<>(Collections.singletonList("lobster"));
		assertTrue(QuestInventoryRules.keepAtQuestStart(995, "Coins", needed, supplies));
		assertTrue(QuestInventoryRules.keepAtQuestStart(554, "Fire rune", needed, supplies));
		assertTrue(QuestInventoryRules.keepAtQuestStart(1929, "Bucket of water", needed, supplies));
		assertTrue(QuestInventoryRules.keepAtQuestStart(379, "Lobster", needed, supplies));
	}

	@Test
	public void questStartBanksOtherQuestsItemsAndFood()
	{
		Set<Integer> needed = new HashSet<>(Arrays.asList(1929));
		Set<String> supplies = Collections.emptySet();
		assertFalse(QuestInventoryRules.keepAtQuestStart(379, "Lobster", needed, supplies));
		assertFalse(QuestInventoryRules.keepAtQuestStart(221, "Eye of newt", needed, supplies));
		assertFalse(QuestInventoryRules.keepAtQuestStart(361, "Tuna", needed, supplies));
	}

	@Test
	public void selectedSuppliesIncludeFoodStaminaAndItems()
	{
		QuestProfile.Supplies supplies = new QuestProfile.Supplies();
		supplies.setFood("Lobster");
		supplies.setFoodQty(5);
		Set<String> names = QuestInventoryRules.selectedSupplyNames(supplies);
		assertTrue(names.contains("lobster"));
		assertTrue(names.contains("stamina potion"));
		supplies.setFoodQty(0);
		assertFalse(QuestInventoryRules.selectedSupplyNames(supplies).contains("lobster"));
	}

	@Test
	public void withdrawalOnlyTakesWhatFits()
	{
		assertEquals(0, QuestInventoryRules.fitQuantity(false, false, 5, 0));
		assertEquals(3, QuestInventoryRules.fitQuantity(false, false, 5, 3));
		assertEquals(5, QuestInventoryRules.fitQuantity(false, false, 5, 27));
		assertEquals(0, QuestInventoryRules.fitQuantity(true, false, 1000, 0));
		assertEquals(1000, QuestInventoryRules.fitQuantity(true, false, 1000, 1));
		assertEquals(1000, QuestInventoryRules.fitQuantity(true, true, 1000, 0));
		assertEquals(0, QuestInventoryRules.fitQuantity(false, false, 0, 5));
	}

	@Test
	public void burntMeatIsNeverBought()
	{
		assertFalse(QuestInventoryRules.geTradable(2146, true));
		assertTrue(QuestInventoryRules.geTradable(379, true));
		assertFalse(QuestInventoryRules.geTradable(379, false));
	}

	@Test
	public void slotsNeededCountsStacksOnce()
	{
		assertEquals(12, QuestInventoryRules.slotsNeeded(false, false, 12));
		assertEquals(1, QuestInventoryRules.slotsNeeded(true, false, 500));
		assertEquals(0, QuestInventoryRules.slotsNeeded(true, true, 500));
		assertEquals(0, QuestInventoryRules.slotsNeeded(false, false, 0));
	}
}
