package net.runelite.client.plugins.microbot.ddchopper;

import org.junit.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AxePlannerTest
{
	@Test
	public void requirementsMatchTheWiki()
	{
		int[] wc = {1, 1, 6, 11, 21, 31, 41, 61, 71};
		int[] atk = {1, 1, 5, 10, 20, 30, 40, 60, 70};
		for (Axe axe : Axe.values())
		{
			assertEquals(wc[axe.ordinal()], axe.getWoodcuttingLevel());
			assertEquals(atk[axe.ordinal()], axe.getAttackLevel());
		}
	}

	@Test
	public void autoPicksBestOwnedUsableAxe()
	{
		Set<Axe> owned = EnumSet.of(Axe.BRONZE, Axe.STEEL, Axe.RUNE);
		assertEquals(Axe.BRONZE, AxePlanner.chooseOwned(AxeChoice.AUTO, owned, 1));
		assertEquals(Axe.STEEL, AxePlanner.chooseOwned(AxeChoice.AUTO, owned, 6));
		assertEquals(Axe.STEEL, AxePlanner.chooseOwned(AxeChoice.AUTO, owned, 40));
		assertEquals(Axe.RUNE, AxePlanner.chooseOwned(AxeChoice.AUTO, owned, 41));
	}

	@Test
	public void noUsableAxeGivesNull()
	{
		assertNull(AxePlanner.chooseOwned(AxeChoice.AUTO, EnumSet.of(Axe.RUNE), 40));
		assertNull(AxePlanner.chooseOwned(AxeChoice.AUTO, EnumSet.noneOf(Axe.class), 99));
	}

	@Test
	public void specificChoiceWinsWhenOwnedAndUsable()
	{
		Set<Axe> owned = EnumSet.of(Axe.IRON, Axe.MITHRIL);
		assertEquals(Axe.IRON, AxePlanner.chooseOwned(AxeChoice.IRON, owned, 30));
		// Not owned: falls back to the best owned axe.
		assertEquals(Axe.MITHRIL, AxePlanner.chooseOwned(AxeChoice.RUNE, owned, 30));
		// Owned but too high a level: falls back.
		assertEquals(Axe.IRON, AxePlanner.chooseOwned(AxeChoice.MITHRIL, owned, 20));
	}

	@Test
	public void wieldingNeedsAttackButChoppingDoesNot()
	{
		assertTrue(Axe.RUNE.canChop(41));
		assertFalse(Axe.RUNE.canWield(1));
		assertTrue(Axe.RUNE.canWield(40));
	}

	@Test
	public void buysBestAffordableUsableAxe()
	{
		Map<Axe, Integer> prices = new EnumMap<>(Axe.class);
		prices.put(Axe.BRONZE, 20);
		prices.put(Axe.IRON, 50);
		prices.put(Axe.STEEL, 200);
		prices.put(Axe.MITHRIL, 1000);
		prices.put(Axe.RUNE, 9000);
		assertEquals(Axe.IRON, AxePlanner.chooseToBuy(AxeChoice.AUTO, 5, 300, prices));
		assertEquals(Axe.STEEL, AxePlanner.chooseToBuy(AxeChoice.AUTO, 10, 300, prices));
		assertEquals(Axe.MITHRIL, AxePlanner.chooseToBuy(AxeChoice.AUTO, 25, 2000, prices));
		assertEquals(Axe.MITHRIL, AxePlanner.chooseToBuy(AxeChoice.AUTO, 45, 2000, prices));
		assertEquals(Axe.RUNE, AxePlanner.chooseToBuy(AxeChoice.AUTO, 45, 20000, prices));
		assertNull(AxePlanner.chooseToBuy(AxeChoice.AUTO, 45, 5, prices));
	}

	@Test
	public void crystalIsNeverBought()
	{
		Map<Axe, Integer> prices = new EnumMap<>(Axe.class);
		prices.put(Axe.CRYSTAL, 1);
		assertNull(AxePlanner.chooseToBuy(AxeChoice.CRYSTAL, 99, 1_000_000, prices));
	}

	@Test
	public void levelUpUnlocksUpgrade()
	{
		assertFalse(AxePlanner.upgradeUnlocked(Axe.IRON, 5, false));
		assertTrue(AxePlanner.upgradeUnlocked(Axe.IRON, 6, false));
		assertFalse(AxePlanner.upgradeUnlocked(Axe.DRAGON, 70, false));
		assertTrue(AxePlanner.upgradeUnlocked(Axe.DRAGON, 71, true));
		assertTrue(AxePlanner.upgradeUnlocked(null, 1, false));
	}
}
