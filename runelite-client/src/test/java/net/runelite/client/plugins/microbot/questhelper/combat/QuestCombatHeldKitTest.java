package net.runelite.client.plugins.microbot.questhelper.combat;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatLoadout.Style;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestStyleSwap.Ahead;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class QuestCombatHeldKitTest
{
	@Test
	public void kitNeededBeforeTalkStepThatStartsSwapFight()
	{
		// talk step (current) -> kill knight (needs swap, same NPC/area)
		assertEquals(0, QuestStyleSwap.upcomingSwapKill(false, Arrays.asList(new Ahead(true, true, true))));
		assertEquals(1, QuestStyleSwap.upcomingSwapKill(false,
			Arrays.asList(new Ahead(false, false, true), new Ahead(true, true, true))));
	}

	@Test
	public void noLookAheadWhenNotNeeded()
	{
		assertEquals(-1, QuestStyleSwap.upcomingSwapKill(true, Arrays.asList(new Ahead(true, true, true))));
		assertEquals(-1, QuestStyleSwap.upcomingSwapKill(false, Arrays.asList(new Ahead(true, false, true))));
		assertEquals(-1, QuestStyleSwap.upcomingSwapKill(false, Arrays.asList(new Ahead(true, true, false))));
		// an earlier plain kill step comes first
		assertEquals(-1, QuestStyleSwap.upcomingSwapKill(false,
			Arrays.asList(new Ahead(true, false, true), new Ahead(true, true, true))));
		assertEquals(-1, QuestStyleSwap.upcomingSwapKill(false, null));
	}

	@Test
	public void kitMissingDecision()
	{
		assertTrue(QuestStyleSwap.kitMissing("Shortbow", false, "Bronze arrow", 100, 100));
		assertTrue(QuestStyleSwap.kitMissing("Shortbow", true, "Bronze arrow", 100, 75));
		assertFalse(QuestStyleSwap.kitMissing("Shortbow", true, "Bronze arrow", 100, 100));
		assertFalse(QuestStyleSwap.kitMissing("Staff of air", true, "", 0, 0));
	}

	@Test
	public void stalledFightSwapsEvenWithEmptyProtectionData()
	{
		// overhead data present but no icon read: health stuck 12 s -> swap
		assertNull(QuestStyleSwap.wantedStyle(Style.MELEE, Style.MELEE, Style.RANGED,
			EnumSet.noneOf(Style.class), 1_000L, 5_000L));
		assertEquals(Style.RANGED, QuestStyleSwap.wantedStyle(Style.MELEE, Style.MELEE, Style.RANGED,
			EnumSet.noneOf(Style.class), 1_000L, 13_500L));
		assertEquals(Style.RANGED, QuestStyleSwap.wantedStyle(Style.MELEE, Style.MELEE, Style.RANGED,
			EnumSet.of(Style.MELEE), 1_000L, 2_000L));
		assertEquals(Style.MELEE, QuestStyleSwap.wantedStyle(Style.RANGED, Style.MELEE, Style.RANGED, null,
			1_000L, 14_000L));
	}

	@Test
	public void drinksHeldPrayerPotionWhateverTheLoadout()
	{
		assertEquals(15, QuestPrayer.drinkThreshold(0, 40));
		assertEquals(29, QuestPrayer.drinkThreshold(0, 99));
		assertEquals(20, QuestPrayer.drinkThreshold(20, 99));
		assertTrue(QuestPrayer.shouldDrinkHeldPotion(true, 12, 15, true, 5_000, 1_800));
		assertFalse(QuestPrayer.shouldDrinkHeldPotion(true, 30, 15, true, 5_000, 1_800));
		assertFalse(QuestPrayer.shouldDrinkHeldPotion(false, 5, 15, true, 5_000, 1_800));
		assertFalse(QuestPrayer.shouldDrinkHeldPotion(true, 5, 15, false, 5_000, 1_800));
		assertFalse(QuestPrayer.shouldDrinkHeldPotion(true, 5, 15, true, 500, 1_800));
	}

	@Test
	public void stopsPrayingOnlyWhenDryWithoutPotion()
	{
		assertTrue(QuestPrayer.shouldStopPraying(true, 0, false));
		assertFalse(QuestPrayer.shouldStopPraying(true, 0, true));
		assertFalse(QuestPrayer.shouldStopPraying(true, 3, false));
		assertFalse(QuestPrayer.shouldStopPraying(false, 0, false));
	}

	@Test
	public void heldBoostDecision()
	{
		int[] boosted = new int[Skill.values().length];
		int[] real = new int[Skill.values().length];
		boosted[Skill.STRENGTH.ordinal()] = 70;
		real[Skill.STRENGTH.ordinal()] = 70;
		assertEquals(EnumSet.of(Skill.STRENGTH), QuestBoosts.skillsOfHeld("Divine super strength potion(4)"));
		assertEquals(EnumSet.of(Skill.ATTACK, Skill.STRENGTH), QuestBoosts.skillsOfHeld("Combat potion(3)"));
		assertTrue(QuestBoosts.skillsOfHeld("Lobster").isEmpty());
		assertTrue(QuestBoosts.shouldDrinkHeld(EnumSet.of(Skill.STRENGTH), boosted, real, false, 9_999, 3_000));
		assertFalse(QuestBoosts.shouldDrinkHeld(EnumSet.of(Skill.STRENGTH), boosted, real, true, 9_999, 3_000));
		boosted[Skill.STRENGTH.ordinal()] = 80;
		assertFalse(QuestBoosts.shouldDrinkHeld(EnumSet.of(Skill.STRENGTH), boosted, real, false, 9_999, 3_000));
		assertFalse(QuestBoosts.shouldDrinkHeld(Collections.<Skill>emptySet(), boosted, real, false, 9_999, 3_000));
	}
}
