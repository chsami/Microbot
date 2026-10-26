package net.runelite.client.plugins.microbot.ddchopper;

import java.util.Map;
import java.util.Set;

/** Pure axe decisions (no game access) so they can be unit tested. */
public final class AxePlanner
{
	private AxePlanner()
	{
	}

	/** Highest-tier owned axe that can be used for chopping at this Woodcutting level, or null. */
	public static Axe bestOwnedUsable(Set<Axe> owned, int woodcuttingLevel)
	{
		Axe best = null;
		for (Axe axe : owned)
		{
			if (axe.canChop(woodcuttingLevel) && (best == null || axe.ordinal() > best.ordinal()))
			{
				best = axe;
			}
		}
		return best;
	}

	/** Highest-tier axe the level unlocks, whether owned or not. */
	public static Axe bestUnlocked(int woodcuttingLevel, boolean includeUnbuyable)
	{
		Axe best = null;
		for (Axe axe : Axe.values())
		{
			if (axe.canChop(woodcuttingLevel) && (axe.isBuyable() || includeUnbuyable))
			{
				best = axe;
			}
		}
		return best;
	}

	/**
	 * The axe to chop with from what is owned. A specific choice wins when it is owned and usable; otherwise (and
	 * for Auto) the best owned usable axe. Null when nothing owned works.
	 */
	public static Axe chooseOwned(AxeChoice choice, Set<Axe> owned, int woodcuttingLevel)
	{
		Axe wanted = choice.axe();
		if (wanted != null && owned.contains(wanted) && wanted.canChop(woodcuttingLevel))
		{
			return wanted;
		}
		return bestOwnedUsable(owned, woodcuttingLevel);
	}

	/**
	 * The axe to buy: the specific choice if usable and affordable, else the best affordable usable buyable axe.
	 * Null when nothing qualifies. {@code prices} maps axe to Grand Exchange price.
	 */
	public static Axe chooseToBuy(AxeChoice choice, int woodcuttingLevel, long coins, Map<Axe, Integer> prices)
	{
		Axe wanted = choice.axe();
		if (wanted != null && wanted.isBuyable() && wanted.canChop(woodcuttingLevel) && affordable(wanted, coins, prices))
		{
			return wanted;
		}
		Axe best = null;
		for (Axe axe : Axe.values())
		{
			if (axe.isBuyable() && axe.canChop(woodcuttingLevel) && affordable(axe, coins, prices))
			{
				best = axe;
			}
		}
		return best;
	}

	private static boolean affordable(Axe axe, long coins, Map<Axe, Integer> prices)
	{
		Integer price = prices.get(axe);
		return price != null && price > 0 && price <= coins;
	}

	/** True when a level-up unlocked a better axe than the one in use. */
	public static boolean upgradeUnlocked(Axe current, int woodcuttingLevel, boolean includeUnbuyable)
	{
		Axe best = bestUnlocked(woodcuttingLevel, includeUnbuyable);
		return current == null || (best != null && best.ordinal() > current.ordinal());
	}
}
