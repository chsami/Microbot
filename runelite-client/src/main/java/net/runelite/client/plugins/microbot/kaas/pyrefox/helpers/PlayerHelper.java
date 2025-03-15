package net.runelite.client.plugins.microbot.kaas.pyrefox.helpers;

import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

public class PlayerHelper
{
	/**
	 * Checks if the Player is wearing at least one of the required boots.
	 * @return true if equipped.
	 */
	public static boolean playerHasAxeOnHim()
	{
		var weapon = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
		if (weapon.getName().toLowerCase().contains("axe"))
			return true;

		return Rs2Inventory.contains(
			i -> i.getName().toLowerCase().contains("axe")
		);
	}
}
