package net.runelite.client.plugins.microbot.inventorysetups;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static net.runelite.client.plugins.microbot.inventorysetups.InventorySetupTestData.emptyContainer;
import static net.runelite.client.plugins.microbot.inventorysetups.InventorySetupTestData.item;
import static net.runelite.client.plugins.microbot.inventorysetups.InventorySetupTestData.setup;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class InventorySetupTest
{
	@Test
	public void shortContainersArePaddedWithEmptySlots()
	{
		List<InventorySetupsItem> inventory = emptyContainer(27);
		inventory.set(26, item(ItemID.SHARK, "Shark", 1, 26));
		InventorySetup setup = setup("Script", inventory, Collections.singletonList(item(ItemID.ABYSSAL_WHIP, "Abyssal whip", 1, 0)));

		setup.padContainers();

		assertEquals(MInventorySetupsPlugin.NUM_INVENTORY_ITEMS, setup.getInventory().size());
		assertEquals(MInventorySetupsPlugin.NUM_EQUIPMENT_ITEMS, setup.getEquipment().size());
		assertEquals(ItemID.SHARK, setup.getInventory().get(26).getId());
		assertEquals(ItemID.ABYSSAL_WHIP, setup.getEquipment().get(0).getId());
		assertTrue(InventorySetupsItem.itemIsDummy(setup.getInventory().get(27)));
		assertTrue(InventorySetupsItem.itemIsDummy(setup.getEquipment().get(13)));
	}

	@Test
	public void updatingWithShortListsPadsThem()
	{
		InventorySetup setup = setup("Script", emptyContainer(28), emptyContainer(14));

		setup.updateInventory(Arrays.asList(item(ItemID.SHARK, "Shark", 1, 0)));
		setup.updateEquipment(Collections.emptyList());

		assertEquals(MInventorySetupsPlugin.NUM_INVENTORY_ITEMS, setup.getInventory().size());
		assertEquals(MInventorySetupsPlugin.NUM_EQUIPMENT_ITEMS, setup.getEquipment().size());
		assertEquals(ItemID.SHARK, setup.getInventory().get(0).getId());
	}

	@Test
	public void fullAndMissingContainersAreLeftAlone()
	{
		List<InventorySetupsItem> inventory = emptyContainer(28);
		InventorySetup setup = setup("Full", inventory, null);

		setup.padContainers();

		assertSame(inventory, setup.getInventory());
		assertNull(setup.getEquipment());
	}
}
