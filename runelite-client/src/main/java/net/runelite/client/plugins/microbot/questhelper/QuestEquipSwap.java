package net.runelite.client.plugins.microbot.questhelper;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Equipping an item pushes the one it replaces into the inventory. Bring exactly what is needed: whatever appeared in the
 * inventory because of the swap goes back to the bank. Pure, so the choice can be tested.
 */
public final class QuestEquipSwap
{
	private static final java.util.regex.Pattern TELEPORT_JEWELLERY = java.util.regex.Pattern.compile(
		"(?i)^(?:ring of wealth|amulet of glory|skills necklace|games necklace|combat bracelet|ring of dueling|necklace of passage"
			+ "|digsite pendant|burning amulet|slayer ring|ring of the elements|xeric's talisman|amulet of eternal glory).*");

	private QuestEquipSwap()
	{
	}

	/** Jewellery that teleports: kept in the inventory, never sent back to the bank by a swap. */
	public static boolean isTeleportJewellery(String itemName)
	{
		return itemName != null && TELEPORT_JEWELLERY.matcher(itemName.trim()).matches();
	}

	/**
	 * @param before item ids in the inventory before equipping
	 * @param after  item ids in the inventory after equipping
	 * @return ids that are new in the inventory (the displaced gear), in order, none of which were carried before
	 */
	public static Set<Integer> displaced(Collection<Integer> before, Collection<Integer> after)
	{
		Set<Integer> out = new LinkedHashSet<>();
		for (Integer id : after)
		{
			if (id != null && !before.contains(id))
			{
				out.add(id);
			}
		}
		return out;
	}
}
