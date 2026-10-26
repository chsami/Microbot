package net.runelite.client.plugins.microbot.questhelper.combat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class QuestCombatSetupModelTest
{
	private static final List<String> NO_MAGIC = Collections.singletonList(
		"Temple Guardian (level 30). You cannot use Magic. Rings of recoil will not award the kill.");

	@Test
	public void oldJsonWithoutNewFieldsStillLoads()
	{
		String old = "{\"skip\":false,\"gearSetup\":\"Melee\",\"food\":\"Shark\",\"foodQty\":8,\"eatAtPercent\":55,"
			+ "\"potion\":\"PRAYER_POTION\",\"potionQty\":2,\"prayer\":\"QUICK\",\"drinkAtPrayer\":20,\"style\":\"RANGED\"}";
		QuestCombatLoadout loaded = QuestCombatLoadout.fromJson(old);
		assertEquals("Melee", loaded.getGearSetup());
		assertEquals(QuestCombatLoadout.Style.RANGED, loaded.getStyle());
		assertFalse(loaded.wantsStamina());
		assertTrue(loaded.getInventoryItems().isEmpty());
		assertTrue(loaded.getFights().isEmpty());
		assertEquals(-1, loaded.getReserveSlots());
		assertEquals(2, loaded.getStaminaQty());
		assertEquals(30, loaded.getDrinkStaminaAtEnergy());
	}

	@Test
	public void newFieldsRoundTrip()
	{
		QuestCombatLoadout loadout = new QuestCombatLoadout();
		loadout.setUseStamina(true);
		loadout.setStaminaType(QuestCombatLoadout.StaminaType.ENERGY);
		loadout.setStaminaQty(3);
		loadout.setDrinkStaminaAtEnergy(25);
		loadout.setReserveSlots(7);
		loadout.setGearItems(Collections.singletonList(new QuestCombatLoadout.GearItem(1163, "Rune full helm", "head", 1)));
		loadout.setInventoryItems(Collections.singletonList(new QuestCombatLoadout.InventoryItem("Law rune", -1, 50, true)));
		QuestCombatLoadout.FightSetup fight = new QuestCombatLoadout.FightSetup();
		fight.setStyle(QuestCombatLoadout.Style.RANGED);
		fight.setGearSetup("Ranged");
		loadout.getFights().put("temple guardian", fight);

		QuestCombatLoadout loaded = QuestCombatLoadout.fromJson(loadout.toJson());
		assertEquals(loadout, loaded);
		assertTrue(loaded.wantsStamina());
		assertEquals(QuestCombatLoadout.Style.RANGED, loaded.styleFor("temple guardian"));
		assertEquals("Ranged", loaded.gearFor("temple guardian").getSetup());
		assertEquals(1163, loaded.gearFor("monk of zamorak").getItems().get(0).getId());
	}

	@Test
	public void staminaWorksWhenCombatIsSkipped()
	{
		QuestCombatLoadout loadout = QuestCombatLoadout.skipped();
		loadout.setUseStamina(true);
		assertTrue(loadout.wantsStamina());
		assertFalse(loadout.wantsFood());
		assertTrue(loadout.mainGear().isEmpty());
	}

	@Test
	public void fightWithoutEntryInheritsMainStyleAndGear()
	{
		QuestCombatLoadout loadout = new QuestCombatLoadout();
		loadout.setStyle(QuestCombatLoadout.Style.MAGIC);
		loadout.setGearSetup("Mage");
		assertEquals(QuestCombatLoadout.Style.MAGIC, loadout.styleFor("anything"));
		assertEquals("Mage", loadout.gearFor("anything").getSetup());
	}

	@Test
	public void fightKeyIgnoresCaseLevelAndPunctuation()
	{
		assertEquals("temple guardian", QuestCombatLoadout.fightKey("Temple Guardian (level 30)"));
		assertEquals("monk of zamorak", QuestCombatLoadout.fightKey("  Monk of Zamorak. "));
	}

	@Test
	public void defaultStyleRespectsRestriction()
	{
		assertEquals(QuestCombatLoadout.Style.MELEE,
			QuestCombatLoadout.defaultStyle(QuestCombatLoadout.Style.MAGIC, NO_MAGIC));
		assertEquals(QuestCombatLoadout.Style.RANGED,
			QuestCombatLoadout.defaultStyle(QuestCombatLoadout.Style.RANGED, NO_MAGIC));
		assertEquals(QuestCombatLoadout.Style.MAGIC,
			QuestCombatLoadout.defaultStyle(QuestCombatLoadout.Style.MAGIC, Collections.singletonList("Giant spider")));
		assertEquals(QuestCombatLoadout.Style.AUTO,
			QuestCombatLoadout.defaultStyle(QuestCombatLoadout.Style.AUTO, NO_MAGIC));
		assertEquals(QuestCombatLoadout.Style.RANGED, QuestCombatLoadout.defaultStyle(QuestCombatLoadout.Style.MELEE,
			Collections.singletonList("It is immune to melee")));
	}

	@Test
	public void forbiddenStylesReadsCommonPhrasings()
	{
		assertTrue(QuestCombatLoadout.forbiddenStyles(NO_MAGIC).contains(QuestCombatLoadout.Style.MAGIC));
		assertTrue(QuestCombatLoadout.forbiddenStyles(Collections.singletonList("Immune to ranged attacks"))
			.contains(QuestCombatLoadout.Style.RANGED));
		assertTrue(QuestCombatLoadout.forbiddenStyles(Collections.singletonList("Melee does not work here"))
			.contains(QuestCombatLoadout.Style.MELEE));
		assertTrue(QuestCombatLoadout.forbiddenStyles(Collections.singletonList("Kill the Guard (level 20)")).isEmpty());
	}

	@Test
	public void weaponNameGivesStyle()
	{
		assertEquals(QuestCombatLoadout.Style.MAGIC, QuestCombatLoadout.styleOfWeapon("Staff of air"));
		assertEquals(QuestCombatLoadout.Style.MAGIC, QuestCombatLoadout.styleOfWeapon("Trident of the seas"));
		assertEquals(QuestCombatLoadout.Style.RANGED, QuestCombatLoadout.styleOfWeapon("Magic shortbow"));
		assertEquals(QuestCombatLoadout.Style.RANGED, QuestCombatLoadout.styleOfWeapon("Rune crossbow"));
		assertEquals(QuestCombatLoadout.Style.RANGED, QuestCombatLoadout.styleOfWeapon("Adamant dart"));
		assertEquals(QuestCombatLoadout.Style.MELEE, QuestCombatLoadout.styleOfWeapon("Rune scimitar"));
		assertEquals(QuestCombatLoadout.Style.MELEE, QuestCombatLoadout.styleOfWeapon("Bladed staff"));
		assertEquals(QuestCombatLoadout.Style.MELEE, QuestCombatLoadout.styleOfWeapon(null));
	}

	@Test
	public void agentOverridesParseAllFields()
	{
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("gear", "worn");
		body.put("reserveSlots", 6.0);
		Map<String, Object> stamina = new LinkedHashMap<>();
		stamina.put("type", "energy");
		stamina.put("qty", 4);
		stamina.put("drinkAtEnergy", 20);
		body.put("stamina", stamina);
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("name", "Law rune");
		row.put("qty", 10);
		body.put("inventory", Collections.singletonList(row));
		Map<String, Object> fight = new LinkedHashMap<>();
		fight.put("style", "auto");
		fight.put("gear", Collections.singletonMap("setup", "Ranged"));
		body.put("fights", Collections.singletonMap("Temple Guardian (level 30)", fight));

		QuestCombatLoadout loadout = QuestCombatLoadout.applyOverrides(QuestCombatLoadout.bare(), body,
			() -> Arrays.asList(new QuestCombatLoadout.GearItem(1163, "Rune full helm", "head", 1)));
		assertEquals(1, loadout.getGearItems().size());
		assertEquals(6, loadout.getReserveSlots());
		assertTrue(loadout.wantsStamina());
		assertEquals(QuestCombatLoadout.StaminaType.ENERGY, loadout.getStaminaType());
		assertEquals(4, loadout.getStaminaQty());
		assertEquals(20, loadout.getDrinkStaminaAtEnergy());
		assertEquals("Law rune", loadout.getInventoryItems().get(0).getName());
		assertEquals(QuestCombatLoadout.Style.AUTO, loadout.styleFor("temple guardian"));
		assertEquals("Ranged", loadout.gearFor("temple guardian").getSetup());
		assertFalse(loadout.wantsFood());
	}

	@Test
	public void agentOverridesRejectBadValues()
	{
		assertBad("stamina", Collections.singletonMap("type", "jug"));
		assertBad("reserveSlots", 40);
		assertBad("gear", "naked");
		assertBad("inventory", "lots");
		assertBad("fights", Collections.singletonMap("Guard", Collections.singletonMap("style", "stabbing")));
		assertBad("gear", Collections.singletonMap("items",
			Collections.singletonList(Collections.singletonMap("slot", "head"))));
	}

	private static void assertBad(String field, Object value)
	{
		try
		{
			QuestCombatLoadout.applyOverrides(QuestCombatLoadout.bare(), Collections.singletonMap(field, value), null);
			fail(field);
		}
		catch (IllegalArgumentException expected)
		{
			assertNull(null);
		}
	}

	@Test
	public void killTargetIsParsedFromStepText()
	{
		assertEquals("Temple Guardian", QuestCombatDetector.parseTarget(Collections.singletonList(
			"Kill the Temple Guardian (level 30). It is immune to magic so you will need to use either ranged or melee.")));
		assertEquals("Monk of Zamorak", QuestCombatDetector.parseTarget(Collections.singletonList(
			"Kill a Monk of Zamorak (level 30) for a golden key. You can safespot using the pews.")));
		assertEquals("Bob", QuestCombatDetector.parseTarget(Collections.singletonList("Defeat Bob.")));
		assertEquals(30, QuestCombatDetector.parseLevel(Collections.singletonList("Kill the Temple Guardian (level 30).")));
	}

	@Test
	public void noFightsOnlyCombatControlsAreDisabled()
	{
		assertTrue(QuestCombatSetupDialog.combatControlsEnabled(true));
		assertFalse(QuestCombatSetupDialog.combatControlsEnabled(false));
		assertEquals("Skip combat setup", QuestCombatSetupDialog.skipLabel(true));
		assertEquals("Skip setup", QuestCombatSetupDialog.skipLabel(false));
		assertTrue(QuestCombatSetupDialog.NO_FIGHTS_NOTE.contains("Food and potions are optional."));
	}

	@Test
	public void foodAndPotionsCountTowardSlots()
	{
		assertEquals(0, QuestCombatSetupDialog.foodAndPotionSlots("", 5, false, 2));
		assertEquals(5, QuestCombatSetupDialog.foodAndPotionSlots("Lobster", 5, false, 2));
		assertEquals(7, QuestCombatSetupDialog.foodAndPotionSlots("Lobster", 5, true, 2));
	}

	@Test
	public void optionalFoodStaysWantedWhenNotSkipped()
	{
		QuestCombatLoadout loadout = new QuestCombatLoadout();
		loadout.setFood("Trout");
		loadout.setFoodQty(4);
		assertTrue(loadout.normalised().wantsFood());
	}
}
