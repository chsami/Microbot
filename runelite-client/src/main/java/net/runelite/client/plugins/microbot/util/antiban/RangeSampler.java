package net.runelite.client.plugins.microbot.util.antiban;

import java.util.Random;

/**
 * Samples a value from a configured range and keeps it for a period, so a setting such as "random mouse movement chance
 * 5 to 20 percent" is a different exact number in each session or stretch of play instead of a constant. Pure: time and
 * the random source are passed in.
 */
public final class RangeSampler
{
	private final long periodMs;
	private double current;
	private double sampledMin = Double.NaN;
	private double sampledMax = Double.NaN;
	private long sampledAtMs = Long.MIN_VALUE;

	public RangeSampler(long periodMs)
	{
		this.periodMs = periodMs;
	}

	/** A value in [min, max] (swapped if given the wrong way round), resampled when the period ran out or the range changed. */
	public synchronized double get(double min, double max, long nowMs, Random random)
	{
		double low = Math.min(min, max);
		double high = Math.max(min, max);
		boolean changed = low != sampledMin || high != sampledMax;
		if (changed || sampledAtMs == Long.MIN_VALUE || nowMs - sampledAtMs >= periodMs)
		{
			current = between(low, high, random);
			sampledMin = low;
			sampledMax = high;
			sampledAtMs = nowMs;
		}
		return current;
	}

	/** A uniform value in [min, max]; both ends are reachable. */
	public static double between(double min, double max, Random random)
	{
		double low = Math.min(min, max);
		double high = Math.max(min, max);
		return low + (high - low) * random.nextDouble();
	}

	/** An integer in [min, max], both ends inclusive. */
	public static int betweenInclusive(int min, int max, Random random)
	{
		int low = Math.min(min, max);
		int high = Math.max(min, max);
		return low + random.nextInt(high - low + 1);
	}
}
