package net.runelite.client.plugins.microbot.util.logging;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tiny helpers for rate-limited logging patterns used across long-lived clients.
 * Keeps call sites readable and consistent.
 */
public final class Rs2LogRateLimit
{
	private Rs2LogRateLimit()
	{
	}

	/** @return true exactly once per JVM lifetime for the given token. */
	public static boolean once(AtomicBoolean token)
	{
		return token != null && token.compareAndSet(false, true);
	}

	/**
	 * Increment and return true when counter hits 1 or every {@code interval} after.
	 * Useful for "log summary every N occurrences".
	 */
	public static boolean everyN(AtomicInteger counter, int interval)
	{
		if (counter == null || interval <= 0)
		{
			return false;
		}
		int n = counter.incrementAndGet();
		return n == 1 || n % interval == 0;
	}
}

