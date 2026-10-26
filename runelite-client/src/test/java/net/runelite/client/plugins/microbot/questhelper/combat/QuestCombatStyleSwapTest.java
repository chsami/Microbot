package net.runelite.client.plugins.microbot.questhelper.combat;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import net.runelite.api.HeadIcon;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatLoadout.FightSetup;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatLoadout.SecondaryStyle;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatLoadout.Style;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class QuestCombatStyleSwapTest
{
	@Test
	public void detectsSwapText()
	{
		assertTrue(QuestStyleSwap.textNeedsSwap(Arrays.asList("Defeat the knight, swapping combat styles to bypass his prayers.")));
		assertTrue(QuestStyleSwap.textNeedsSwap(Arrays.asList("Bring two combat styles.")));
		assertTrue(QuestStyleSwap.textNeedsSwap(Arrays.asList("He will bypass his prayers if you switch combat styles.")));
		assertFalse(QuestStyleSwap.textNeedsSwap(Arrays.asList("Kill the goblin.")));
		assertFalse(QuestStyleSwap.textNeedsSwap(null));
		assertTrue(QuestStyleSwap.isStylePlaceholder("Two combat styles"));
		assertFalse(QuestStyleSwap.isStylePlaceholder("Rune scimitar"));
	}

	@Test
	public void defaultSecondaryIsBestTrainedOther()
	{
		// melee 46, ranged 13, magic 1: melee primary gives ranged
		assertEquals(Style.RANGED, QuestStyleSwap.defaultSecondary(Style.MELEE, 46, 13, 1, null));
		assertEquals(Style.MAGIC, QuestStyleSwap.defaultSecondary(Style.MELEE, 46, 13, 40, null));
		assertEquals(Style.MELEE, QuestStyleSwap.defaultSecondary(Style.RANGED, 46, 13, 1, null));
		assertEquals(Style.MAGIC, QuestStyleSwap.defaultSecondary(Style.MELEE, 46, 13, 1, EnumSet.of(Style.RANGED)));
		assertEquals(Style.RANGED, QuestStyleSwap.defaultSecondary(Style.MELEE, 46, 5, 5, null));
		assertNull(QuestStyleSwap.defaultSecondary(Style.MELEE, 46, 0, 0, null));
	}

	@Test
	public void defaultKits()
	{
		assertEquals("Shortbow", QuestStyleSwap.defaultKit(Style.RANGED)[0]);
		assertEquals("Bronze arrow", QuestStyleSwap.defaultKit(Style.RANGED)[1]);
		assertEquals("100", QuestStyleSwap.defaultKit(Style.RANGED)[2]);
		assertEquals("Staff of air", QuestStyleSwap.defaultKit(Style.MAGIC)[0]);
		assertEquals("200", QuestStyleSwap.defaultKit(Style.MAGIC)[2]);
		assertTrue(QuestStyleSwap.knownItemId("Shortbow") > 0);
		assertEquals(-1, QuestStyleSwap.knownItemId("Abyssal whip"));
	}

	@Test
	public void planDefaultsFromDetection()
	{
		QuestStyleSwap.Plan plan = QuestStyleSwap.plan("knight", null, true, Style.MELEE, 46, 13, 1, Collections.emptyList());
		assertEquals(Style.RANGED, plan.secondary);
		assertEquals("Shortbow", plan.weapon);
		assertEquals("Bronze arrow", plan.ammo);
		assertEquals(100, plan.ammoQty);
		assertTrue(plan.slotsUsed().contains("shield"));
		assertTrue(plan.slotsUsed().contains("ammo"));
		assertNull(QuestStyleSwap.plan("knight", null, false, Style.MELEE, 46, 13, 1, Collections.emptyList()));
	}

	@Test
	public void planHonoursChoices()
	{
		FightSetup none = new FightSetup();
		none.setSecondaryStyle(SecondaryStyle.NONE);
		assertNull(QuestStyleSwap.plan("k", none, true, Style.MELEE, 46, 13, 1, null));

		FightSetup magic = new FightSetup();
		magic.setSecondaryStyle(SecondaryStyle.MAGIC);
		QuestStyleSwap.Plan plan = QuestStyleSwap.plan("k", magic, false, Style.MELEE, 46, 13, 1, null);
		assertEquals(Style.MAGIC, plan.secondary);
		assertEquals("Staff of air", plan.weapon);
		assertEquals(200, plan.ammoQty);

		FightSetup custom = new FightSetup();
		custom.setSecondaryWeapon("Oak shortbow");
		custom.setSecondaryAmmo("Iron arrow");
		custom.setSecondaryAmmoQty(50);
		plan = QuestStyleSwap.plan("k", custom, true, Style.MELEE, 46, 13, 1, null);
		assertEquals("Oak shortbow", plan.weapon);
		assertEquals(50, plan.ammoQty);

		FightSetup same = new FightSetup();
		same.setSecondaryStyle(SecondaryStyle.MELEE);
		assertNull(QuestStyleSwap.plan("k", same, true, Style.MELEE, 46, 13, 1, null));
	}

	@Test
	public void readsProtectedStyles()
	{
		assertNull(QuestStyleSwap.protectedStyles(null));
		assertTrue(QuestStyleSwap.protectedStyles(new short[]{-1, -1}).isEmpty());
		assertEquals(EnumSet.of(Style.MELEE), QuestStyleSwap.protectedStyles(new short[]{-1, (short) HeadIcon.MELEE.ordinal()}));
		assertEquals(EnumSet.of(Style.RANGED, Style.MAGIC),
			QuestStyleSwap.protectedStyles(new short[]{(short) HeadIcon.RANGE_MAGE.ordinal()}));
		assertTrue(QuestStyleSwap.protectedStyles(new short[]{(short) HeadIcon.SMITE.ordinal(), 500}).isEmpty());
	}

	@Test
	public void swapDecision()
	{
		Set<Style> melee = EnumSet.of(Style.MELEE);
		Set<Style> ranged = EnumSet.of(Style.RANGED);
		assertEquals(Style.RANGED, QuestStyleSwap.swapTarget(Style.MELEE, Style.MELEE, Style.RANGED, melee));
		assertEquals(Style.MELEE, QuestStyleSwap.swapTarget(Style.RANGED, Style.MELEE, Style.RANGED, ranged));
		assertNull(QuestStyleSwap.swapTarget(Style.RANGED, Style.MELEE, Style.RANGED, melee));
		assertNull(QuestStyleSwap.swapTarget(Style.MELEE, Style.MELEE, Style.RANGED, EnumSet.noneOf(Style.class)));
		assertNull(QuestStyleSwap.swapTarget(Style.MELEE, Style.MELEE, Style.RANGED, null));
		assertNull(QuestStyleSwap.swapTarget(Style.MELEE, Style.MELEE, Style.RANGED, EnumSet.of(Style.MELEE, Style.RANGED)));
		// a third style worn: go to whichever planned style is open
		assertEquals(Style.MELEE, QuestStyleSwap.swapTarget(Style.MAGIC, Style.MELEE, Style.RANGED, EnumSet.of(Style.MAGIC, Style.RANGED)));
	}

	@Test
	public void rateLimitAndFallback()
	{
		assertTrue(QuestStyleSwap.swapAllowed(0, 100));
		assertFalse(QuestStyleSwap.swapAllowed(1000, 1600));
		assertTrue(QuestStyleSwap.swapAllowed(1000, 2200));
		assertFalse(QuestStyleSwap.fallbackDue(1000, 5000));
		assertTrue(QuestStyleSwap.fallbackDue(1000, 1000 + QuestStyleSwap.FALLBACK_STALL_MS));
		assertEquals(Style.RANGED, QuestStyleSwap.fallbackTarget(Style.MELEE, Style.MELEE, Style.RANGED));
		assertEquals(Style.MELEE, QuestStyleSwap.fallbackTarget(Style.RANGED, Style.MELEE, Style.RANGED));
	}

	@Test
	public void loadoutSwapPlanSkipsWhenSkipped()
	{
		QuestCombatLoadout loadout = QuestCombatLoadout.skipped();
		assertNull(loadout.swapPlanFor("k", true, Style.MELEE, 46, 13, 1, null));
		QuestCombatLoadout bare = QuestCombatLoadout.bare();
		assertEquals(Style.RANGED, bare.swapPlanFor("k", true, Style.MELEE, 46, 13, 1, null).secondary);
	}

	@Test
	public void weaponSlots()
	{
		assertTrue(QuestStyleSwap.weaponSlots("Shortbow").contains("shield"));
		assertFalse(QuestStyleSwap.weaponSlots("Staff of air").contains("shield"));
		assertFalse(QuestStyleSwap.weaponSlots("Rune crossbow").contains("shield"));
	}
}
