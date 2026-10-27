package net.runelite.client.plugins.microbot.ddchopper;

import java.util.EnumMap;
import java.util.EnumSet;
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

	/** Generous Grand Exchange prices used when the live lookup fails (returns 0 or less). */
	public static int fallbackPrice(Axe axe)
	{
		switch (axe)
		{
			case BRONZE:
				return 100;
			case IRON:
				return 200;
			case STEEL:
				return 500;
			case BLACK:
				return 700;
			case MITHRIL:
				return 1_000;
			case ADAMANT:
				return 2_500;
			case RUNE:
				return 15_000;
			case DRAGON:
				return 100_000;
			default:
				return 0;
		}
	}

	/** The live price when it is positive, otherwise the fallback, so a failed lookup never reads as unaffordable. */
	public static int effectivePrice(Axe axe, int livePrice)
	{
		return livePrice > 0 ? livePrice : fallbackPrice(axe);
	}

	public static final double BUDGET_FACTOR = 1.3;
	public static final int BUDGET_MARGIN = 100;

	/** Coins to have on hand for an axe: price x 1.3 plus a small margin. */
	public static long coinBudget(int price)
	{
		return (long) Math.ceil(price * BUDGET_FACTOR) + BUDGET_MARGIN;
	}

	/** Coins to take out of the bank: only the shortfall to the budget, never more than the bank holds. */
	public static long coinsToWithdraw(int price, long inventoryCoins, long bankCoins)
	{
		return Math.max(0, Math.min(coinBudget(price) - inventoryCoins, bankCoins));
	}

	/** The steps of acquiring an axe that repeat across ticks, each with how many tries it gets without progress. */
	public enum Step
	{
		/** Walking to a bank and opening it; high because the walk takes several ticks. */
		OPEN_BANK(10),
		/** Taking coins out of the bank for the purchase. */
		WITHDRAW_COINS(3),
		/** Walking to the Grand Exchange and opening it; high because the walk takes several ticks. */
		OPEN_GRAND_EXCHANGE(10),
		/** Taking an owned axe out of the bank. */
		FETCH_AXE(3),
		/** Re-reading the bank because an axe it listed was not there. */
		RESCAN_BANK(3);

		private final int limit;

		Step(int limit)
		{
			this.limit = limit;
		}
	}

	/**
	 * Attempt tracker for one "acquire the desired axe" operation: every repeatable step is bounded, so a step that
	 * keeps failing ends the operation (the caller then falls back to an axe it already has) instead of looping.
	 */
	public static final class Attempts
	{
		private final Map<Step, Integer> counts = new EnumMap<>(Step.class);

		/** Records one try of {@code step}; true while within its limit, false once it is exhausted. */
		public boolean tryStep(Step step)
		{
			return counts.merge(step, 1, Integer::sum) <= step.limit;
		}

		/** Forgets the tries of {@code step} after it made progress. */
		public void progress(Step step)
		{
			counts.remove(step);
		}

		/** Forgets everything, when the operation ends. */
		public void clear()
		{
			counts.clear();
		}
	}

	/** The axe to fall back on: the best usable owned axe that has not been given up on. Null when none. */
	public static Axe fallbackAxe(Set<Axe> owned, Set<Axe> unusable, int woodcuttingLevel)
	{
		Set<Axe> candidates = EnumSet.noneOf(Axe.class);
		candidates.addAll(owned);
		candidates.removeAll(unusable);
		return bestOwnedUsable(candidates, woodcuttingLevel);
	}

	public enum BuyStep
	{
		/** Coins on hand do not cover it and the bank has not been scanned: open a bank first. */
		SCAN_BANK,
		BUY,
		NONE
	}

	public static final class BuyDecision
	{
		public final BuyStep step;
		public final Axe axe;
		public final int price;
		public final long coins;

		BuyDecision(BuyStep step, Axe axe, int price, long coins)
		{
			this.step = step;
			this.axe = axe;
			this.price = price;
			this.coins = coins;
		}
	}

	/**
	 * Whether and what to buy. Coins are inventory plus bank, but bank coins only count once the bank was scanned this
	 * session; when they have not been and inventory coins alone are not enough, ask for a scan instead of giving up.
	 * {@code prices} are effective prices (see {@link #effectivePrice}).
	 */
	public static BuyDecision decideBuy(AxeChoice choice, int woodcuttingLevel, Axe owned, long inventoryCoins,
		long bankCoins, boolean bankScanned, Map<Axe, Integer> prices)
	{
		long coins = inventoryCoins + (bankScanned ? bankCoins : 0);
		Axe buy = chooseToBuy(choice, woodcuttingLevel, coins, prices);
		if (buy != null && (owned == null || buy.ordinal() > owned.ordinal()))
		{
			return new BuyDecision(BuyStep.BUY, buy, prices.get(buy), coins);
		}
		if (!bankScanned)
		{
			return new BuyDecision(BuyStep.SCAN_BANK, null, 0, coins);
		}
		return new BuyDecision(BuyStep.NONE, null, 0, coins);
	}

	/** True when a level-up unlocked a better axe than the one in use. */
	public static boolean upgradeUnlocked(Axe current, int woodcuttingLevel, boolean includeUnbuyable)
	{
		Axe best = bestUnlocked(woodcuttingLevel, includeUnbuyable);
		return current == null || (best != null && best.ordinal() > current.ordinal());
	}
}
