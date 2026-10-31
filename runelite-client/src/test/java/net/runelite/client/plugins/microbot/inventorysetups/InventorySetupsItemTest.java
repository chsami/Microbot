package net.runelite.client.plugins.microbot.inventorysetups;

import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InventorySetupsItemTest
{
	private static InventorySetupsItem item(int id, String name, boolean fuzzy)
	{
		return new InventorySetupsItem(id, name, 1, fuzzy, InventorySetupsStackCompareID.None, false, 0);
	}

	@Test
	public void fuzzyNameDropsTheDoseSoAnyDoseMatches()
	{
		assertEquals("Prayer potion", item(ItemID._4DOSEPRAYERRESTORE, "Prayer potion(4)", true).getName());
		assertEquals("Ring of dueling", item(ItemID.RING_OF_DUELING_8, "Ring of dueling(8)", true).getName());
		assertEquals("Amulet of glory", item(ItemID.AMULET_OF_GLORY_6, "Amulet of glory(6)", true).getName());
	}

	@Test
	public void strictNameKeepsTheDose()
	{
		assertEquals("Prayer potion(4)", item(ItemID._4DOSEPRAYERRESTORE, "Prayer potion(4)", false).getName());
	}

	@Test
	public void fuzzyNameOnlyDropsATrailingCount()
	{
		assertEquals("Amethyst dart(p++)", item(ItemID.AMETHYST_DART_P_, "Amethyst dart(p++)", true).getName());
		assertEquals("Shark", item(ItemID.SHARK, "Shark", true).getName());
	}

	@Test
	public void nameThatIsOnlyACountIsKept()
	{
		assertEquals("(4)", item(1, "(4)", true).getName());
	}

	@Test
	public void barrowsChargeIsDroppedSoEveryDegradeStageMatches()
	{
		assertEquals("Dharok's helm", item(ItemID.BARROWS_DHAROK_HEAD_100, "Dharok's helm 100", false).getName());
		assertEquals("Karil's leathertop", item(ItemID.BARROWS_KARIL_BODY_25, "Karil's leathertop 25", true).getName());
		assertEquals("Ahrim's staff", item(ItemID.BARROWS_AHRIM_WEAPON, "Ahrim's staff", false).getName());
	}

	@Test
	public void brokenBarrowsPieceIsNotTreatedAsTheWorkingItem()
	{
		assertEquals("Verac's flail 0", item(ItemID.BARROWS_VERAC_WEAPON_BROKEN, "Verac's flail 0", false).getName());
		assertFalse(InventorySetupsItem.isBarrowsItem("verac's flail 0"));
		assertTrue(InventorySetupsItem.isBarrowsItem("verac's flail 75"));
		assertFalse(InventorySetupsItem.isBarrowsItem("abyssal whip"));
	}

	@Test
	public void strictItemsMatchById()
	{
		InventorySetupsItem whip = item(ItemID.ABYSSAL_WHIP, "Abyssal whip", false);
		assertTrue(whip.matches(item(ItemID.ABYSSAL_WHIP, "Something else", false)));
		assertFalse(whip.matches(item(ItemID.ABYSSAL_TENTACLE, "Abyssal whip", false)));
	}

	@Test
	public void fuzzyItemsMatchByName()
	{
		InventorySetupsItem fuzzyPrayer = item(ItemID._4DOSEPRAYERRESTORE, "Prayer potion(4)", true);
		assertTrue(fuzzyPrayer.matches(item(ItemID._2DOSEPRAYERRESTORE, "Prayer potion(2)", true)));
		assertFalse(fuzzyPrayer.matches(item(ItemID._4DOSE2RESTORE, "Super restore(4)", true)));
	}

	@Test
	public void dummyItemIsRecognised()
	{
		assertTrue(InventorySetupsItem.itemIsDummy(InventorySetupsItem.getDummyItem()));
		assertTrue(InventorySetupsItem.itemIsDummy(new InventorySetupsItem(-1, "", 0, false, null, false, -1)));
	}

	@Test
	public void emptySlotWithSavedSettingsIsNotADummy()
	{
		assertFalse(InventorySetupsItem.itemIsDummy(new InventorySetupsItem(-1, "", 0, true, InventorySetupsStackCompareID.None, false, -1)));
		assertFalse(InventorySetupsItem.itemIsDummy(new InventorySetupsItem(-1, "", 0, false, InventorySetupsStackCompareID.Standard, false, -1)));
		assertFalse(InventorySetupsItem.itemIsDummy(new InventorySetupsItem(-1, "", 0, false, InventorySetupsStackCompareID.None, true, -1)));
	}

	@Test
	public void togglesFlipFuzzyAndLocked()
	{
		InventorySetupsItem shark = item(ItemID.SHARK, "Shark", false);
		shark.toggleIsFuzzy();
		shark.toggleIsLocked();
		assertTrue(shark.isFuzzy());
		assertTrue(shark.isLocked());
		shark.toggleIsFuzzy();
		shark.toggleIsLocked();
		assertFalse(shark.isFuzzy());
		assertFalse(shark.isLocked());
	}

	@Test
	public void stackCompareSymbols()
	{
		assertEquals("", InventorySetupsStackCompareID.getStringFromValue(null));
		assertEquals("", InventorySetupsStackCompareID.getStringFromValue(InventorySetupsStackCompareID.None));
		assertEquals("!=", InventorySetupsStackCompareID.getStringFromValue(InventorySetupsStackCompareID.Standard));
		assertEquals("<", InventorySetupsStackCompareID.getStringFromValue(InventorySetupsStackCompareID.Less_Than));
		assertEquals(">", InventorySetupsStackCompareID.getStringFromValue(InventorySetupsStackCompareID.Greater_Than));
	}
}
