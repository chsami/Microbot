package net.runelite.client.plugins.microbot.util.inventory;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class Rs2InventoryStaminaNameTest
{
	@Test
	public void plainAndExtendedStaminaMatchWithAnyDose()
	{
		assertTrue(Rs2Inventory.isStaminaPotionName("Stamina potion(4)"));
		assertTrue(Rs2Inventory.isStaminaPotionName("Extended stamina potion(2)"));
		assertTrue(Rs2Inventory.isStaminaPotionName("Extended stamina potion"));
	}

	@Test
	public void otherPotionsDoNotMatch()
	{
		assertFalse(Rs2Inventory.isStaminaPotionName("Energy potion(4)"));
		assertFalse(Rs2Inventory.isStaminaPotionName("Super energy(3)"));
		assertFalse(Rs2Inventory.isStaminaPotionName(null));
	}
}
