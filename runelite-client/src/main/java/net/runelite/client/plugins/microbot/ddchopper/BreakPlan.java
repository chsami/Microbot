package net.runelite.client.plugins.microbot.ddchopper;

import java.util.Random;

/**
 * Picks play and break lengths (minutes) at random inside the configured ranges. The picked values are pushed to
 * Microbot's own Break Handler, which does the actual pausing, logging out and logging back in.
 */
public final class BreakPlan
{
	private final int minPlay;
	private final int maxPlay;
	private final int minBreak;
	private final int maxBreak;
	private final Random random;

	public BreakPlan(int minPlay, int maxPlay, int minBreak, int maxBreak, Random random)
	{
		this.minPlay = Math.max(1, Math.min(minPlay, maxPlay));
		this.maxPlay = Math.max(1, Math.max(minPlay, maxPlay));
		this.minBreak = Math.max(1, Math.min(minBreak, maxBreak));
		this.maxBreak = Math.max(1, Math.max(minBreak, maxBreak));
		this.random = random;
	}

	public int nextPlayMinutes()
	{
		return minPlay + random.nextInt(maxPlay - minPlay + 1);
	}

	public int nextBreakMinutes()
	{
		return minBreak + random.nextInt(maxBreak - minBreak + 1);
	}

	public int minPlay()
	{
		return minPlay;
	}

	public int maxPlay()
	{
		return maxPlay;
	}

	public int minBreak()
	{
		return minBreak;
	}

	public int maxBreak()
	{
		return maxBreak;
	}
}
