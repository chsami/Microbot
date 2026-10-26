package net.runelite.client.plugins.microbot.questhelper.combat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestBoosts.Potion;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatLoadout.BoostType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class QuestCombatBoostTest
{
	private static int[] levels(int attack, int strength, int defence)
	{
		int[] out = new int[Skill.values().length];
		out[Skill.ATTACK.ordinal()] = attack;
		out[Skill.STRENGTH.ordinal()] = strength;
		out[Skill.DEFENCE.ordinal()] = defence;
		return out;
	}

	@Test
	public void choicesNameTheirPotions()
	{
		assertEquals(Arrays.asList(Potion.SUPER_ATTACK, Potion.SUPER_STRENGTH),
			QuestBoosts.potionsFor(BoostType.SUPER_ATTACK_STRENGTH, false));
		assertEquals(Arrays.asList(Potion.SUPER_ATTACK, Potion.SUPER_STRENGTH, Potion.SUPER_DEFENCE),
			QuestBoosts.potionsFor(BoostType.SUPER_ATTACK_STRENGTH, true));
		assertEquals(Collections.singletonList(Potion.COMBAT), QuestBoosts.potionsFor(BoostType.COMBAT, false));
		assertEquals(Arrays.asList(Potion.ATTACK, Potion.STRENGTH),
			QuestBoosts.potionsFor(BoostType.ATTACK_STRENGTH, false));
		assertTrue(QuestBoosts.potionsFor(BoostType.NONE, true).isEmpty());
	}

	@Test
	public void namesMatchAnyDoseButNotSimilarPotions()
	{
		assertEquals(Potion.SUPER_ATTACK, QuestBoosts.byItemName("Super attack(3)"));
		assertEquals(Potion.COMBAT, QuestBoosts.byItemName("Combat potion (4)"));
		assertEquals(Potion.SUPER_COMBAT, QuestBoosts.byItemName("Super combat potion(4)"));
		assertNull(QuestBoosts.byItemName("Prayer potion(4)"));
		assertNull(QuestBoosts.byItemName(null));
	}

	@Test
	public void drinksOnlyWhenTheBoostIsAbsent()
	{
		assertTrue(QuestBoosts.needsDrink(40, 40));
		assertTrue(QuestBoosts.needsDrink(42, 40));
		assertFalse(QuestBoosts.needsDrink(43, 40));

		int[] real = levels(40, 40, 40);
		assertTrue(QuestBoosts.shouldDrink(Potion.SUPER_ATTACK, levels(40, 50, 40), real, true, false, 10_000, 3_000));
		assertFalse(QuestBoosts.shouldDrink(Potion.SUPER_ATTACK, levels(50, 40, 40), real, true, false, 10_000, 3_000));
		// a combat potion raises two skills: drink when either one has run down
		assertTrue(QuestBoosts.shouldDrink(Potion.COMBAT, levels(50, 40, 40), real, true, false, 10_000, 3_000));
		assertFalse(QuestBoosts.shouldDrink(Potion.COMBAT, levels(50, 50, 40), real, true, false, 10_000, 3_000));
	}

	@Test
	public void respectsHeldProtectedAndRateLimit()
	{
		int[] real = levels(40, 40, 40);
		assertFalse(QuestBoosts.shouldDrink(Potion.SUPER_STRENGTH, real, real, false, false, 10_000, 3_000));
		assertFalse(QuestBoosts.shouldDrink(Potion.SUPER_STRENGTH, real, real, true, true, 10_000, 3_000));
		assertFalse(QuestBoosts.shouldDrink(Potion.SUPER_STRENGTH, real, real, true, false, 1_000, 3_000));
		assertFalse(QuestBoosts.shouldDrink(null, real, real, true, false, 10_000, 3_000));
	}

	@Test
	public void doseIdsListTheFourDoseFirst()
	{
		List<Integer> ids = Potion.SUPER_ATTACK.doseIds();
		assertEquals(4, ids.size());
		assertEquals(Integer.valueOf(2436), ids.get(0));
	}

	@Test
	public void agentServerBodyParsesBoosts()
	{
		java.util.Map<String, Object> boosts = new java.util.HashMap<>();
		boosts.put("type", "super_attack_strength");
		boosts.put("defence", true);
		boosts.put("qty", 2);
		java.util.Map<String, Object> body = new java.util.HashMap<>();
		body.put("boosts", boosts);
		QuestCombatLoadout loadout = QuestCombatLoadout.applyOverrides(QuestCombatLoadout.bare(), body, null);
		assertTrue(loadout.wantsBoosts());
		assertEquals(3, loadout.boostPotions().size());
		assertEquals(2, loadout.getBoostQty());
		body.put("boosts", Collections.singletonMap("type", "none"));
		assertFalse(QuestCombatLoadout.applyOverrides(loadout, body, null).wantsBoosts());
	}
}
