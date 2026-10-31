package net.runelite.client.plugins.microbot.inventorysetups;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class InventorySetupTestData
{
	private InventorySetupTestData()
	{
	}

	public static InventorySetupsItem item(int id, String name, int quantity, int slot)
	{
		return new InventorySetupsItem(id, name, quantity, false, InventorySetupsStackCompareID.None, false, slot);
	}

	public static InventorySetupsItem fuzzy(int id, String name, int quantity, int slot)
	{
		return new InventorySetupsItem(id, name, quantity, true, InventorySetupsStackCompareID.None, false, slot);
	}

	public static InventorySetupsItem emptiedSlot(int slot, boolean fuzzy)
	{
		return new InventorySetupsItem(-1, "", 0, fuzzy, InventorySetupsStackCompareID.None, false, slot);
	}

	public static List<InventorySetupsItem> emptyContainer(int size)
	{
		List<InventorySetupsItem> container = new ArrayList<>();
		for (int i = 0; i < size; i++)
		{
			container.add(InventorySetupsItem.getDummyItem());
		}
		return container;
	}

	public static InventorySetup setup(String name, List<InventorySetupsItem> inventory, List<InventorySetupsItem> equipment)
	{
		return setup(name, inventory, equipment, null, null, null, new HashMap<>());
	}

	public static InventorySetup setup(String name, List<InventorySetupsItem> inventory, List<InventorySetupsItem> equipment,
		List<InventorySetupsItem> runePouch, List<InventorySetupsItem> boltPouch, List<InventorySetupsItem> quiver,
		Map<Integer, InventorySetupsItem> additional)
	{
		return new InventorySetup(inventory, equipment, runePouch, boltPouch, quiver, additional, name, "",
			Color.RED, false, null, false, false, 0, false, -1);
	}
}
