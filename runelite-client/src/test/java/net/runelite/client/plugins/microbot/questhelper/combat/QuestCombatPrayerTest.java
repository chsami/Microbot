package net.runelite.client.plugins.microbot.questhelper.combat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import org.junit.Test;

public class QuestCombatPrayerTest
{
	private static final QuestPrayer.Unlocks NONE = new QuestPrayer.Unlocks();

	@Test
	public void meleeAtLevel36PicksIncredibleReflexesUltimateStrengthSteelSkin()
	{
		assertEquals(Arrays.asList(Rs2PrayerEnum.INCREDIBLE_REFLEXES, Rs2PrayerEnum.ULTIMATE_STRENGTH,
				Rs2PrayerEnum.STEEL_SKIN).size(), 3);
		assertTrue(QuestPrayer.bestOffensive(QuestCombatLoadout.Style.MELEE, 36, NONE)
			.containsAll(Arrays.asList(Rs2PrayerEnum.INCREDIBLE_REFLEXES, Rs2PrayerEnum.ULTIMATE_STRENGTH,
				Rs2PrayerEnum.STEEL_SKIN)));
	}

	@Test
	public void meleeLowLevel()
	{
		assertEquals(Collections.singletonList(Rs2PrayerEnum.THICK_SKIN),
			QuestPrayer.bestOffensive(QuestCombatLoadout.Style.MELEE, 3, NONE));
		assertEquals(Arrays.asList(Rs2PrayerEnum.BURST_STRENGTH, Rs2PrayerEnum.THICK_SKIN),
			QuestPrayer.bestOffensive(QuestCombatLoadout.Style.MELEE, 6, NONE));
	}

	@Test
	public void pietyReplacesTheMeleeSet()
	{
		QuestPrayer.Unlocks u = new QuestPrayer.Unlocks();
		u.piety = true;
		u.chivalry = true;
		assertEquals(Collections.singletonList(Rs2PrayerEnum.PIETY),
			QuestPrayer.bestOffensive(QuestCombatLoadout.Style.MELEE, 70, u));
		assertEquals(Collections.singletonList(Rs2PrayerEnum.CHIVALRY),
			QuestPrayer.bestOffensive(QuestCombatLoadout.Style.MELEE, 65, u));
	}

	@Test
	public void rangedAndMagic()
	{
		assertEquals(Collections.singletonList(Rs2PrayerEnum.HAWK_EYE),
			QuestPrayer.bestOffensive(QuestCombatLoadout.Style.RANGED, 36, NONE));
		assertEquals(Collections.singletonList(Rs2PrayerEnum.MYSTIC_LORE),
			QuestPrayer.bestOffensive(QuestCombatLoadout.Style.MAGIC, 36, NONE));
		QuestPrayer.Unlocks u = new QuestPrayer.Unlocks();
		u.rigour = true;
		u.augury = true;
		assertEquals(Collections.singletonList(Rs2PrayerEnum.RIGOUR),
			QuestPrayer.bestOffensive(QuestCombatLoadout.Style.RANGED, 74, u));
		assertEquals(Collections.singletonList(Rs2PrayerEnum.AUGURY),
			QuestPrayer.bestOffensive(QuestCombatLoadout.Style.MAGIC, 77, u));
		assertTrue(QuestPrayer.bestOffensive(QuestCombatLoadout.Style.RANGED, 7, NONE).isEmpty());
	}

	@Test
	public void rechargeThreshold()
	{
		assertTrue(QuestPrayer.needsRecharge(0, 36));
		assertTrue(QuestPrayer.needsRecharge(17, 36));
		assertFalse(QuestPrayer.needsRecharge(18, 36));
		assertTrue(QuestPrayer.needsRecharge(9, 10));
		assertFalse(QuestPrayer.needsRecharge(10, 10));
	}

	@Test
	public void defaultPrayerAndAltars()
	{
		assertEquals(QuestCombatLoadout.Prayer.BEST_OFFENSIVE, QuestPrayer.defaultPrayer(true, 4));
		assertEquals(QuestCombatLoadout.Prayer.OFF, QuestPrayer.defaultPrayer(true, 3));
		assertEquals(QuestCombatLoadout.Prayer.OFF, QuestPrayer.defaultPrayer(false, 50));
		assertEquals(new WorldPoint(3243, 3206, 0), QuestPrayer.nearestKnownAltar(new WorldPoint(3220, 3220, 0)));
		assertTrue(QuestPrayer.inWilderness(new WorldPoint(3240, 3600, 0)));
		assertFalse(QuestPrayer.inWilderness(new WorldPoint(3253, 3484, 0)));
	}
}
