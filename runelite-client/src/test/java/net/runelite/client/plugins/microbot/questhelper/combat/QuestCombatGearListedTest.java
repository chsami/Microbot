package net.runelite.client.plugins.microbot.questhelper.combat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatLoadout.GearItem;
import org.junit.Test;

public class QuestCombatGearListedTest
{
	private final List<GearItem> gear = Arrays.asList(
		new GearItem(1205, "Bronze dagger", "weapon", 1),
		new GearItem(-1, "Wooden shield", "shield", 1));

	@Test
	public void listedByIdOrByNameWhenThereIsNoId()
	{
		assertTrue(QuestCombatLoadout.gearItemListed(gear, 1205, "anything"));
		assertTrue(QuestCombatLoadout.gearItemListed(gear, 99, "wooden SHIELD"));
	}

	@Test
	public void anUnlistedItemIsNotGear()
	{
		assertFalse(QuestCombatLoadout.gearItemListed(gear, 99, "Pie dish"));
		assertFalse(QuestCombatLoadout.gearItemListed(gear, 99, null));
	}
}
