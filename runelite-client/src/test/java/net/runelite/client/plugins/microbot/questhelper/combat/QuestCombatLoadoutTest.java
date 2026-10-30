package net.runelite.client.plugins.microbot.questhelper.combat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class QuestCombatLoadoutTest
{
	@Test
	public void jsonRoundTripKeepsEveryField()
	{
		QuestCombatLoadout loadout = new QuestCombatLoadout();
		loadout.setGearSetup("Melee");
		loadout.setFood("Shark");
		loadout.setFoodQty(12);
		loadout.setEatAtPercent(60);
		loadout.setPotion(QuestCombatLoadout.Potion.SUPER_RESTORE);
		loadout.setPotionQty(3);
		loadout.setPrayer(QuestCombatLoadout.Prayer.PROTECT_MAGIC);
		loadout.setDrinkAtPrayer(25);
		loadout.setStyle(QuestCombatLoadout.Style.RANGED);

		assertEquals(loadout, QuestCombatLoadout.fromJson(loadout.toJson()));
	}

	@Test
	public void skipRoundTrips()
	{
		QuestCombatLoadout loaded = QuestCombatLoadout.fromJson(QuestCombatLoadout.skipped().toJson());
		assertTrue(loaded.isSkip());
		assertFalse(loaded.wantsFood());
	}

	@Test
	public void badJsonIsNull()
	{
		assertNull(QuestCombatLoadout.fromJson("{not json"));
		assertNull(QuestCombatLoadout.fromJson(""));
	}

	@Test
	public void partialJsonGetsClampedDefaults()
	{
		QuestCombatLoadout loaded = QuestCombatLoadout.fromJson("{\"foodQty\":99,\"eatAtPercent\":0}");
		assertEquals(27, loaded.getFoodQty());
		assertEquals(1, loaded.getEatAtPercent());
		assertEquals(QuestCombatLoadout.Style.AUTO, loaded.getStyle());
	}

	@Test
	public void agentMapParsesLooseEnumText()
	{
		Map<String, Object> map = new HashMap<>();
		map.put("potion", "prayer potion");
		map.put("prayer", "protect_melee");
		map.put("foodQty", 5.0);
		QuestCombatLoadout loaded = QuestCombatLoadout.fromMap(map);
		assertEquals(QuestCombatLoadout.Potion.PRAYER_POTION, loaded.getPotion());
		assertEquals(QuestCombatLoadout.Prayer.PROTECT_MELEE, loaded.getPrayer());
		assertEquals(5, loaded.getFoodQty());
	}

	@Test
	public void agentMapRejectsUnknownValues()
	{
		try
		{
			QuestCombatLoadout.fromMap(Collections.singletonMap("style", "stabbing"));
			fail();
		}
		catch (IllegalArgumentException expected)
		{
		}
	}

	@Test
	public void detectsMagicImmunityNotes()
	{
		assertTrue(QuestCombatLoadout.mentionsMagicImmunity(Arrays.asList("Boss (level 90)", "It is immune to magic")));
		assertTrue(QuestCombatLoadout.mentionsMagicImmunity(Collections.singletonList("You cannot use Magic here")));
		assertFalse(QuestCombatLoadout.mentionsMagicImmunity(Collections.singletonList("Giant spider (level 50)")));
		assertFalse(QuestCombatLoadout.mentionsMagicImmunity(null));
	}

	@Test
	public void killTextNeedsAWordStart()
	{
		assertTrue(QuestCombatDetector.isKillText(Collections.singletonList("Kill the guard.")));
		assertTrue(QuestCombatDetector.isKillText(Collections.singletonList("Defeat Bob")));
		assertFalse(QuestCombatDetector.isKillText(Collections.singletonList("Train your Skill")));
	}
}
