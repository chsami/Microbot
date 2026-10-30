package net.runelite.client.plugins.microbot.questhelper;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatLoadout;
import net.runelite.client.plugins.microbot.questhelper.planner.QuestProfile;

/**
 * Pure rules for what the inventory may hold when a queued quest starts, and how much of a bank item fits.
 * Kept free of game types so they can be tested.
 */
public final class QuestInventoryRules
{
	/** Enemies at or below this level are trivial: a quest whose fights are all this weak needs no food. */
	public static final int TRIVIAL_ENEMY_LEVEL = 10;

	/** Food count suggested for a quest with real fights. */
	public static final int DEFAULT_FOOD_QTY = 5;

	private QuestInventoryRules()
	{
	}

	/**
	 * Default food for a quest.
	 *
	 * @param maxEnemyLevel highest enemy level in the quest's fights, 0 when a level is not known
	 */
	public static int foodQtyForMaxLevel(int maxEnemyLevel)
	{
		return maxEnemyLevel > 0 && maxEnemyLevel <= TRIVIAL_ENEMY_LEVEL ? 0 : DEFAULT_FOOD_QTY;
	}

	/** Items whose cache flag says tradeable but that the Grand Exchange does not trade (Burnt meat). */
	private static final Set<Integer> NEVER_ON_GE = new HashSet<>(java.util.Arrays.asList(2146));

	/** Whether an item can be bought on the Grand Exchange: the cache flag, minus known exceptions. Not price based. */
	public static boolean geTradable(int itemId, boolean cacheTradeable)
	{
		return cacheTradeable && !NEVER_ON_GE.contains(itemId);
	}

	/** Lower-case base names of the supplies the user selected for a quest (food, potions, stamina, items). */
	public static Set<String> selectedSupplyNames(QuestProfile.Supplies supplies)
	{
		Set<String> out = new HashSet<>();
		if (supplies == null)
		{
			return out;
		}
		if (supplies.getFoodQty() > 0 && supplies.getFood() != null && !supplies.getFood().trim().isEmpty())
		{
			out.add(baseName(supplies.getFood()));
		}
		if (supplies.isStaminaOn() && supplies.getStaminaQty() > 0)
		{
			out.add(baseName(net.runelite.client.plugins.microbot.questhelper.combat.QuestPotions.Kind.STAMINA.itemName()));
		}
		if (supplies.isEnergyFallback() && supplies.getEnergyQty() > 0)
		{
			out.add(baseName(net.runelite.client.plugins.microbot.questhelper.combat.QuestPotions.Kind.ENERGY.itemName()));
		}
		for (net.runelite.client.plugins.microbot.questhelper.combat.QuestPotions.Kind kind : QuestProfile.POTION_KINDS)
		{
			if (supplies.potionQty(kind) > 0)
			{
				out.add(baseName(kind.itemName()));
			}
		}
		if (supplies.getItems() != null)
		{
			for (QuestCombatLoadout.InventoryItem item : supplies.getItems())
			{
				if (item.getQty() > 0 && item.getName() != null)
				{
					out.add(baseName(item.getName()));
				}
			}
		}
		return out;
	}

	/** Item name without a trailing dose such as "(4)", lower case. */
	public static String baseName(String name)
	{
		return name == null ? "" : name.replaceAll("\\s*\\(\\d+\\)$", "").trim().toLowerCase(Locale.ROOT);
	}

	/**
	 * Whether an inventory item stays when a quest starts: coins, runes, what this quest needs and the supplies the
	 * user selected for it. Everything else (other quests' items and food) goes to the bank.
	 */
	public static boolean keepAtQuestStart(int itemId, String itemName, Set<Integer> neededIds,
		Set<String> selectedSupplyNames)
	{
		if (itemId == 995 || neededIds.contains(itemId))
		{
			return true;
		}
		String base = baseName(itemName);
		return base.endsWith(" rune") || base.endsWith(" runes") || selectedSupplyNames.contains(base);
	}

	/** Inventory slots a withdrawal of {@code qty} takes: a stack that is already held or a stackable item takes one. */
	public static int slotsNeeded(boolean stackable, boolean alreadyHeld, int qty)
	{
		if (qty <= 0)
		{
			return 0;
		}
		if (stackable)
		{
			return alreadyHeld ? 0 : 1;
		}
		return qty;
	}

	/** How much of a withdrawal fits in the free slots (0 when none). */
	public static int fitQuantity(boolean stackable, boolean alreadyHeld, int qty, int freeSlots)
	{
		if (qty <= 0)
		{
			return 0;
		}
		if (stackable)
		{
			return alreadyHeld || freeSlots > 0 ? qty : 0;
		}
		return Math.min(qty, Math.max(0, freeSlots));
	}
}
