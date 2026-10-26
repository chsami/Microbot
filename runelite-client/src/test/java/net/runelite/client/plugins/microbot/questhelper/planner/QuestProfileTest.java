package net.runelite.client.plugins.microbot.questhelper.planner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatLoadout;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestPotions;
import org.junit.Test;

public class QuestProfileTest
{
	@Test
	public void defaultIsCombatWhenTheQuestHasFightsElseTravel()
	{
		assertEquals(QuestProfile.Type.COMBAT, QuestProfile.defaultFor("Imp Catcher", true).getType());
		assertEquals(QuestProfile.Type.TRAVEL, QuestProfile.defaultFor("Cooks Assistant", false).getType());
		assertTrue(QuestProfile.defaultFor("x", false).geAllowed());
	}

	@Test
	public void travelLoadoutBringsStaminaAndGearAndNothingElse()
	{
		QuestProfile profile = QuestProfile.defaultFor("Q", false);
		profile.getSupplies().setStaminaQty(3);
		profile.setTravelGear(Arrays.asList(new QuestCombatLoadout.GearItem(1, "Amulet of glory", "amulet", 1)));
		QuestCombatLoadout loadout = profile.travelLoadout(0);
		assertFalse(loadout.isSkip());
		assertTrue(loadout.wantsStamina());
		assertEquals(3, loadout.getStaminaQty());
		assertEquals(1, loadout.getGearItems().size());
		assertEquals(0, loadout.getFoodQty());
		assertFalse(loadout.wantsPotions());
	}

	@Test
	public void suppliesCarryFoodPotionsFallbackAndTeleports()
	{
		QuestProfile profile = QuestProfile.defaultFor("Q", false);
		QuestProfile.Supplies supplies = profile.getSupplies();
		supplies.setFood("Lobster");
		supplies.setFoodQty(8);
		supplies.setEatAtPercent(60);
		supplies.setPotionQty(QuestPotions.Kind.PRAYER_POTION, 2);
		supplies.setPotionQty(QuestPotions.Kind.SUPER_COMBAT, 1);
		supplies.setEnergyFallback(true);
		supplies.getItems().add(new QuestCombatLoadout.InventoryItem("Varrock teleport", -1, 3, false));
		QuestCombatLoadout loadout = profile.travelLoadout(0);
		assertEquals("Lobster", loadout.getFood());
		assertEquals(8, loadout.getFoodQty());
		assertEquals(60, loadout.getEatAtPercent());
		assertTrue(loadout.wantsPrayerPotions());
		assertEquals(1, loadout.getInventoryItems().size());
		assertEquals(1, loadout.potionRows(QuestPotions.Role.ENERGY).size());
		assertEquals(1, loadout.potionRows(QuestPotions.Role.BOOST).size());
	}

	@Test
	public void combatKeepsItsStoredLoadoutUnlessSuppliesWereEdited()
	{
		QuestCombatLoadout stored = QuestCombatLoadout.bare();
		stored.setFood("Shark");
		stored.setFoodQty(12);
		QuestProfile profile = QuestProfile.defaultFor("Q", true);
		QuestCombatLoadout kept = profile.loadoutFor(stored, null, 0);
		assertEquals("Shark", kept.getFood());
		assertEquals(12, kept.getFoodQty());

		profile.getSupplies().setFood("Lobster");
		profile.getSupplies().setFoodQty(4);
		profile.getSupplies().setCustomised(true);
		QuestCombatLoadout edited = profile.loadoutFor(stored, null, 0);
		assertEquals("Lobster", edited.getFood());
		assertEquals(4, edited.getFoodQty());
	}

	@Test
	public void minimumStaminaRaisesTheCountButNeverLowersIt()
	{
		QuestProfile profile = QuestProfile.defaultFor("Q", false);
		profile.getSupplies().setStaminaQty(1);
		assertEquals(3, profile.travelLoadout(3).getStaminaQty());
		profile.setMinStamina(0);
		assertEquals(1, profile.travelLoadout(3).getStaminaQty());
		profile.getSupplies().setStaminaOn(false);
		assertEquals(0, profile.travelLoadout(3).getStaminaQty());
		assertEquals(4, QuestProfile.defaultFor("Q", false).effectiveMinStamina(9));
	}

	@Test
	public void combatMinimumStaminaIsAppliedWithoutTouchingTheRest()
	{
		QuestCombatLoadout stored = QuestCombatLoadout.bare();
		stored.setFood("Shark");
		stored.setFoodQty(5);
		QuestProfile profile = QuestProfile.defaultFor("Q", true);
		profile.setMinStamina(2);
		QuestCombatLoadout loadout = profile.loadoutFor(stored, null, 0);
		assertEquals(2, loadout.getStaminaQty());
		assertTrue(loadout.wantsStamina());
		assertEquals("Shark", loadout.getFood());
	}

	@Test
	public void geSwitchPerSupplyDropsItFromTheBuyList()
	{
		QuestProfile profile = QuestProfile.defaultFor("Q", false);
		profile.getSupplies().setFood("Lobster");
		profile.getSupplies().setFoodQty(5);
		List<QuestProfile.SupplyLine> all = profile.getSupplies().buyable(0);
		assertEquals(2, all.size());
		profile.getSupplies().setBuys("food", false);
		assertEquals(1, profile.getSupplies().buyable(0).size());
		profile.getSupplies().setBuys("stamina", false);
		assertTrue(profile.getSupplies().buyable(0).isEmpty());
	}

	@Test
	public void copySettingsKeepsTheQuestName()
	{
		QuestProfile source = QuestProfile.defaultFor("A", true);
		source.setObtain(QuestProfile.Obtain.BANK_ONLY);
		source.getSupplies().setFood("Tuna");
		QuestProfile target = QuestProfile.defaultFor("B", false);
		target.copySettingsFrom(source);
		assertEquals("B", target.getQuestName());
		assertEquals(QuestProfile.Type.COMBAT, target.getType());
		assertEquals("Tuna", target.getSupplies().getFood());
		target.getSupplies().setFood("Cake");
		assertEquals("Tuna", source.getSupplies().getFood());
	}

	@Test
	public void jsonRoundTripAndGarbageAreSafe()
	{
		QuestProfile profile = QuestProfile.defaultFor("Q", true);
		profile.setMinStamina(2);
		profile.getSupplies().setBuys("food", false);
		QuestProfile back = QuestProfile.fromJson(profile.toJson());
		assertEquals(2, back.getMinStamina());
		assertFalse(back.getSupplies().buys("food"));
		assertEquals(null, QuestProfile.fromJson("{{"));
	}
}
