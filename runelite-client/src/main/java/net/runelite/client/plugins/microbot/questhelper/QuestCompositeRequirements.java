package net.runelite.client.plugins.microbot.questhelper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.runelite.client.plugins.microbot.questhelper.requirements.item.ItemRequirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.item.ItemRequirements;
import net.runelite.client.plugins.microbot.questhelper.requirements.util.LogicType;

/**
 * Turns a missing item requirement into the concrete items to fetch. A composite requirement ("telegrab or 10,000
 * coins") has no id or quantity of its own, so a bank trip or a purchase cannot act on it: it resolves to one child
 * (OR) or to all of its unmet children (AND). Pure, so the choice can be tested.
 */
public final class QuestCompositeRequirements
{
	private QuestCompositeRequirements()
	{
	}

	/** What a requirement comes down to: items to fetch, and parts nothing can fetch (the shortfall to report). */
	public static final class Resolution
	{
		public final List<ItemRequirement> fetch = new ArrayList<>();
		public final List<ItemRequirement> unobtainable = new ArrayList<>();
	}

	/**
	 * @param requirement the requirement that is not met
	 * @param onPlayer    whether a requirement is met by what the player carries or wears
	 * @param bankCovers  whether the bank holds enough of a requirement to withdraw it
	 * @param tradable    whether a requirement can be bought on the Grand Exchange
	 * @return what to fetch (empty when an OR is already met by one of its children) and, when no child of an OR can
	 *         be fetched from the bank or bought, those children as the shortfall
	 */
	public static Resolution resolve(ItemRequirement requirement, Predicate<ItemRequirement> onPlayer,
		Predicate<ItemRequirement> bankCovers, Predicate<ItemRequirement> tradable)
	{
		Resolution out = new Resolution();
		collect(requirement, onPlayer, bankCovers, tradable, out);
		return out;
	}

	private static void collect(ItemRequirement requirement, Predicate<ItemRequirement> onPlayer,
		Predicate<ItemRequirement> bankCovers, Predicate<ItemRequirement> tradable, Resolution out)
	{
		if (!(requirement instanceof ItemRequirements))
		{
			out.fetch.add(requirement);
			return;
		}
		ItemRequirements composite = (ItemRequirements) requirement;
		List<ItemRequirement> children = composite.getItemRequirements();
		if (children == null || children.isEmpty())
		{
			return;
		}
		if (composite.getLogicType() == LogicType.OR)
		{
			for (ItemRequirement child : children)
			{
				if (onPlayer.test(child))
				{
					return;
				}
			}
			ItemRequirement chosen = pick(children, bankCovers);
			if (chosen == null)
			{
				chosen = pick(children, tradable);
			}
			if (chosen == null)
			{
				out.unobtainable.addAll(children);
				return;
			}
			collect(chosen, onPlayer, bankCovers, tradable, out);
			return;
		}
		for (ItemRequirement child : children)
		{
			if (!onPlayer.test(child))
			{
				collect(child, onPlayer, bankCovers, tradable, out);
			}
		}
	}

	/** The first child the test accepts; a nested composite counts when any of its leaves is accepted. */
	private static ItemRequirement pick(List<ItemRequirement> children, Predicate<ItemRequirement> test)
	{
		for (ItemRequirement child : children)
		{
			if (child instanceof ItemRequirements)
			{
				for (ItemRequirement leaf : resolve(child, c -> false, c -> true, c -> true).fetch)
				{
					if (test.test(leaf))
					{
						return child;
					}
				}
			}
			else if (test.test(child))
			{
				return child;
			}
		}
		return null;
	}
}
