package net.runelite.client.plugins.microbot.inventorysetups;

import java.util.Collection;
import net.runelite.api.ItemID;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InventorySetupsVariationMappingTest
{
	@Test
	public void unmappedItemMapsToItself()
	{
		assertEquals(ItemID.ABYSSAL_WHIP, InventorySetupsVariationMapping.map(ItemID.ABYSSAL_WHIP));
	}

	@Test
	public void divineRunePouchCountsAsARunePouch()
	{
		assertEquals(ItemID.RUNE_POUCH, InventorySetupsVariationMapping.map(ItemID.DIVINE_RUNE_POUCH));
		assertTrue(InventorySetupsVariationMapping.getVariations(ItemID.RUNE_POUCH).contains(ItemID.DIVINE_RUNE_POUCH));
		assertTrue(InventorySetupsVariationMapping.getVariations(ItemID.RUNE_POUCH).contains(ItemID.RUNE_POUCH));
	}

	@Test
	public void godCapesAreInterchangeable()
	{
		int saradomin = InventorySetupsVariationMapping.map(ItemID.SARADOMIN_CAPE);
		assertEquals(saradomin, InventorySetupsVariationMapping.map(ItemID.GUTHIX_CAPE));
		assertEquals(saradomin, InventorySetupsVariationMapping.map(ItemID.ZAMORAK_CAPE));
		Collection<Integer> variations = InventorySetupsVariationMapping.getVariations(saradomin);
		assertTrue(variations.contains(ItemID.SARADOMIN_CAPE));
		assertTrue(variations.contains(ItemID.ZAMORAK_CAPE));
	}

	@Test
	public void lockedFireCapeCountsAsFireCape()
	{
		assertEquals(InventorySetupsVariationMapping.map(ItemID.FIRE_CAPE), InventorySetupsVariationMapping.map(ItemID.FIRE_CAPE_L));
	}

	@Test
	public void twistedAncestralCountsAsAncestral()
	{
		assertEquals(InventorySetupsVariationMapping.map(ItemID.ANCESTRAL_HAT), InventorySetupsVariationMapping.map(ItemID.TWISTED_ANCESTRAL_HAT));
	}

	@Test
	public void wornGracefulMapsBackToTheInventoryItem()
	{
		assertEquals(Integer.valueOf(ItemID.GRACEFUL_HOOD_11851), InventorySetupsVariationMapping.INVERTED_WORN_ITEMS.get(ItemID.GRACEFUL_HOOD));
	}

	@Test
	public void itemWithoutCustomMappingHasItselfAsVariation()
	{
		assertTrue(InventorySetupsVariationMapping.getVariations(ItemID.ABYSSAL_WHIP).contains(ItemID.ABYSSAL_WHIP));
	}
}
