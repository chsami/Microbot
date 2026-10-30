package net.runelite.client.plugins.microbot.questhelper;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Where the Ectofuntus inputs (bones, pots, buckets) come from, decided without touching the game: the bank is looked
 * in once per item and, when it cannot cover the need, remembered as empty for a cooldown so the remainder is bought
 * instead of walking to the bank again and again. Pure.
 */
public final class EctofuntusSupply
{
	/** How long "the bank had none of it" is trusted. */
	public static final long COOLDOWN_MS = 15 * 60_000L;

	public enum Stage
	{
		/** Everything is carried. */
		NONE,
		/** Some input has not been looked for in the bank yet: visit it and take exactly what is short. */
		BANK,
		/** The bank was checked for every short input and could not cover it: buy the rest. */
		BUY
	}

	private final Map<Integer, Long> notInBankAt = new HashMap<>();

	public void markNotInBank(int itemId, long now)
	{
		notInBankAt.put(itemId, now);
	}

	public void forget(int itemId)
	{
		notInBankAt.remove(itemId);
	}

	public void forgetAll()
	{
		notInBankAt.clear();
	}

	public boolean knownNotInBank(int itemId, long now)
	{
		Long at = notInBankAt.get(itemId);
		return at != null && now - at < COOLDOWN_MS;
	}

	/** The next source for the inputs still short ({@code shortfall}: item id to how many are missing). */
	public Stage stage(Map<Integer, Integer> shortfall, long now)
	{
		boolean any = false;
		for (Map.Entry<Integer, Integer> entry : shortfall.entrySet())
		{
			if (entry.getValue() <= 0)
			{
				continue;
			}
			any = true;
			if (!knownNotInBank(entry.getKey(), now))
			{
				return Stage.BANK;
			}
		}
		return any ? Stage.BUY : Stage.NONE;
	}

	/** What is short: wanted minus carried, per item id, never negative. */
	public static Map<Integer, Integer> shortfall(Map<Integer, Integer> wanted, Map<Integer, Integer> carried)
	{
		Map<Integer, Integer> out = new LinkedHashMap<>();
		for (Map.Entry<Integer, Integer> entry : wanted.entrySet())
		{
			out.put(entry.getKey(), Math.max(0, entry.getValue() - carried.getOrDefault(entry.getKey(), 0)));
		}
		return out;
	}

	/** Exactly what to take from the bank: the shortfall, no more than the bank holds or the inventory fits. */
	public static int withdrawCount(int shortfall, int inBank, int freeSlots)
	{
		return Math.max(0, Math.min(shortfall, Math.min(inBank, freeSlots)));
	}

	/** Whether the bank, with this much in it, cannot cover the whole shortfall (so the rest has to be bought). */
	public static boolean bankFallsShort(int shortfall, int inBank)
	{
		return inBank < shortfall;
	}

	/** Slots to empty so {@code toWithdraw} items and the Ecto-token stack fit. */
	public static int slotsToFree(int toWithdraw, int freeSlots)
	{
		return Math.max(0, toWithdraw + 1 - Math.max(0, freeSlots));
	}
}
