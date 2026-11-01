package net.runelite.client.plugins.microbot.inventorysetups.serialization;

import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupLayoutUtilities;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem;
import lombok.Value;
import net.runelite.client.plugins.banktags.tabs.Layout;

import java.util.ArrayList;
import java.util.List;

// a class intended to be imported and exported.
// The reason for this is to include data that inventory setups may not handle, such as layouts.
@Value
public class InventorySetupPortable
{

	InventorySetupSerializable setup;

	// We don't store the layout tag info since it might change when importing. The name is not guaranteed.
	int[] layout;

	static public InventorySetupPortable convertFromInventorySetup(final InventorySetup setup, final InventorySetupLayoutUtilities layoutUtilities)
	{
		final Layout layout = layoutUtilities.getSetupLayout(setup);
		InventorySetupSerializable serializable = InventorySetupSerializable.convertFromInventorySetup(setup);
		return new InventorySetupPortable(serializable, layout.getLayout());
	}

	static public ArrayList<InventorySetupPortable> convertFromListOfSetups(final List<InventorySetup> setups, final InventorySetupLayoutUtilities layoutUtilities)
	{
		ArrayList<InventorySetupPortable> portables = new ArrayList<>();
		for (final InventorySetup setup : setups)
		{
			portables.add(convertFromInventorySetup(setup, layoutUtilities));
		}
		return portables;
	}

	public InventorySetup getSerializedSetup()
	{
		InventorySetup imported = InventorySetupSerializable.convertToInventorySetup(setup);
		List<InventorySetupsItem> inventory = imported.getInventory();
		if (inventory != null)
		{
			for (int i = 0; i < inventory.size(); i++)
			{
				InventorySetupsItem item = inventory.get(i);
				if (!InventorySetupsItem.isEmptySlot(item) && item.getSlot() < 0)
				{
					item.setSlot(i);
				}
			}
		}
		return imported;
	}
}
