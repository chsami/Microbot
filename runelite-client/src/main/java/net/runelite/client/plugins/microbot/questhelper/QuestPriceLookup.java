package net.runelite.client.plugins.microbot.questhelper;

import java.util.function.IntSupplier;
import java.util.function.LongConsumer;

/** Retry and fallback rules for Grand Exchange price lookups, so one transient failure never stalls a quest. */
public final class QuestPriceLookup
{
	/** Used only when every lookup failed for an item that is tradable; the offer is then raised on retries. */
	public static final int FALLBACK_PRICE = 1_000;

	private QuestPriceLookup()
	{
	}

	/**
	 * Calls {@code lookup} up to {@code attempts} times with a doubling pause (baseDelayMs, 2x, 4x...) in between and
	 * returns the first price that is positive and below Integer.MAX_VALUE, else {@code fallback}.
	 */
	public static int withRetry(IntSupplier lookup, int attempts, long baseDelayMs, LongConsumer sleeper, int fallback)
	{
		long delay = baseDelayMs;
		for (int i = 0; i < attempts; i++)
		{
			int price;
			try
			{
				price = lookup.getAsInt();
			}
			catch (RuntimeException e)
			{
				price = -1;
			}
			if (price > 0 && price < Integer.MAX_VALUE)
			{
				return price;
			}
			if (i < attempts - 1)
			{
				sleeper.accept(delay);
				delay *= 2;
			}
		}
		return fallback;
	}
}
