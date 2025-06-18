package net.runelite.client.plugins.microbot.inventorysetups.serialization;

import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsStackCompareID;

import lombok.Value;

import javax.annotation.Nullable;

@Value
public class InventorySetupItemSerializable
{
	int id;
	@Nullable
	Integer q;		// Quantity (null = 1)
	@Nullable
	Boolean f;		// Fuzzy (null = FALSE)
	@Nullable
	InventorySetupsStackCompareID sc;	// Stack Compare (null = NONE)

	static public InventorySetupItemSerializable convertFromInventorySetupItem(final InventorySetupsItem item)
	{
		if (item == null || InventorySetupsItem.itemIsDummy(item))
		{
			return null;
		}
		Integer quantity = item.getQuantity() != 1 ? item.getQuantity() : null;
		Boolean fuzzy = item.isFuzzy() ? Boolean.TRUE : null;
		InventorySetupsStackCompareID sc = item.getStackCompare() != InventorySetupsStackCompareID.None ? item.getStackCompare() : null;
		return new InventorySetupItemSerializable(item.getId(), quantity, fuzzy, sc);
	}

	static public InventorySetupsItem convertToInventorySetupItem(final InventorySetupItemSerializable is)
	{
		if (is == null)
		{
			return InventorySetupsItem.getDummyItem();
		}
		int id = is.getId();
		// Name is not saved in the serializable object. It must be obtained from the item manager at runtime
		String name = "";
		int quantity = is.getQ() != null ? is.getQ() : 1;
		boolean fuzzy = is.getF() != null ? is.getF() : Boolean.FALSE;
		InventorySetupsStackCompareID sc = is.getSc() != null ? is.getSc() : InventorySetupsStackCompareID.None;
		return new InventorySetupsItem(id, name, quantity, fuzzy, sc);
	}
}
