package net.runelite.client.plugins.microbot.ddchopper;

import java.util.List;
import java.util.Random;

/**
 * Time-boxed rotation between spots. Each visit lasts a random time inside [min, max] minutes of active play; when it
 * runs out the next spot in the pool (round-robin) takes over. Pure and clock-injected for tests.
 */
public final class SpotRotation
{
	private final Random random;
	private final int minMinutes;
	private final int maxMinutes;
	private Spot current;
	private long deadline;

	public SpotRotation(Random random, int minMinutes, int maxMinutes)
	{
		this.random = random;
		this.minMinutes = Math.max(1, Math.min(minMinutes, maxMinutes));
		this.maxMinutes = Math.max(1, Math.max(minMinutes, maxMinutes));
	}

	/** The spot to be at now. {@code activeMillis} is play time, so breaks do not burn the timer. */
	public Spot current(List<Spot> pool, long activeMillis)
	{
		if (pool.isEmpty())
		{
			current = null;
			return null;
		}
		if (pool.size() == 1)
		{
			current = pool.get(0);
			deadline = activeMillis + pickMillis();
			return current;
		}
		int index = current == null ? -1 : pool.indexOf(current);
		if (index < 0)
		{
			current = pool.get(0);
			deadline = activeMillis + pickMillis();
		}
		else if (activeMillis >= deadline)
		{
			current = pool.get((index + 1) % pool.size());
			deadline = activeMillis + pickMillis();
		}
		return current;
	}

	public long remainingMillis(long activeMillis)
	{
		return current == null ? 0 : Math.max(0, deadline - activeMillis);
	}

	private long pickMillis()
	{
		return (minMinutes + random.nextInt(maxMinutes - minMinutes + 1)) * 60_000L;
	}
}
