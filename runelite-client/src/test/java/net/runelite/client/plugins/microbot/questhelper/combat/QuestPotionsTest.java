package net.runelite.client.plugins.microbot.questhelper.combat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class QuestPotionsTest
{
	@Test
	public void oldPotionAndBoostFieldsMigrateIntoTheList()
	{
		QuestCombatLoadout loadout = new QuestCombatLoadout();
		loadout.setPotion(QuestCombatLoadout.Potion.PRAYER_POTION);
		loadout.setPotionQty(4);
		loadout.setBoostType(QuestCombatLoadout.BoostType.SUPER_ATTACK_STRENGTH);
		loadout.setBoostDefence(true);
		loadout.setBoostQty(2);
		loadout.normalised();

		List<QuestPotions.Row> rows = loadout.potionRows();
		assertEquals(4, rows.size());
		assertEquals(QuestPotions.Kind.PRAYER_POTION, rows.get(0).kind());
		assertEquals(4, rows.get(0).getQty());
		assertEquals(2, loadout.boostQtyOf(QuestBoosts.Potion.SUPER_DEFENCE));
		assertTrue(loadout.wantsPrayerPotions());
		assertTrue(loadout.wantsBoosts());
		assertEquals(10, loadout.potionSlots());
	}

	@Test
	public void emptyOldFieldsGiveNoPotions()
	{
		QuestCombatLoadout loadout = QuestCombatLoadout.bare();
		assertFalse(loadout.wantsPotions());
		assertTrue(loadout.potionRows().isEmpty());
	}

	@Test
	public void agentServerPotionsListIsParsedAndWinsOverOldFields()
	{
		QuestCombatLoadout base = QuestCombatLoadout.fromMap(Collections.singletonMap("potion", "prayer_potion"));
		Map<String, Object> body = Collections.singletonMap("potions", Arrays.asList(
			Map.of("type", "super_restore", "qty", 3), Map.of("type", "Antifire potion", "qty", 1)));
		QuestCombatLoadout out = QuestCombatLoadout.applyOverrides(base, body, Collections::emptyList);
		assertEquals(2, out.potionRows().size());
		assertEquals(QuestPotions.Kind.SUPER_RESTORE, out.potionRows().get(0).kind());
		assertEquals(1, out.potionRows(QuestPotions.Role.ANTIFIRE).size());
	}

	@Test(expected = IllegalArgumentException.class)
	public void unknownPotionTypeIsRejected()
	{
		QuestCombatLoadout.applyOverrides(new QuestCombatLoadout(), Collections.singletonMap("potions",
			Collections.singletonList(Map.of("type", "nectar", "qty", 1))), Collections::emptyList);
	}

	@Test
	public void userBroughtPotionsAreNotDepositedUnlessASlotIsNeeded()
	{
		HashSet<Integer> snapshot = new HashSet<>(Arrays.asList(139, 1001));
		List<String> drink = Collections.singletonList("Drink");
		assertFalse(QuestPotions.mayDepositUserItem(139, "Prayer potion(3)", drink, snapshot, false));
		assertTrue(QuestPotions.mayDepositUserItem(139, "Prayer potion(3)", drink, snapshot, true));
		// not in the snapshot: free to deposit
		assertTrue(QuestPotions.mayDepositUserItem(141, "Prayer potion(2)", drink, snapshot, false));
		// user brought it but it is not a consumable
		assertTrue(QuestPotions.mayDepositUserItem(1001, "Bronze axe", Collections.singletonList("Wield"), snapshot, false));
		// teleports are protected too
		assertFalse(QuestPotions.mayDepositUserItem(1001, "Varrock teleport", Collections.emptyList(), snapshot, false));
	}
}
