package net.runelite.client.plugins.microbot.questhelper;

/**
 * Hysteresis for a combined (conditional) step: once the script commits to a sub-step it keeps that sub-step for a
 * few ticks, until the sub-step's interaction has been tried, so a condition that flips every tick cannot make the
 * script walk back and forth between two targets.
 */
public final class StepCommitment<T>
{
	private final int maxTicks;
	private T committed;
	private int ticks;
	private boolean tried;

	public StepCommitment(int maxTicks)
	{
		this.maxTicks = maxTicks;
	}

	/** The step to act on this tick, given the one the conditional step currently reports as active. */
	public T select(T active)
	{
		if (active == null)
		{
			reset();
			return null;
		}
		if (committed != null && committed != active && !tried && ticks < maxTicks)
		{
			ticks++;
			return committed;
		}
		if (committed != active)
		{
			committed = active;
			ticks = 0;
			tried = false;
		}
		return committed;
	}

	/** Marks the committed step's interaction as tried, releasing the commitment. */
	public void markTried()
	{
		tried = true;
	}

	public void reset()
	{
		committed = null;
		ticks = 0;
		tried = false;
	}
}
