package net.runelite.client.plugins.microbot.util.antiban;

import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;

/**
 * When the slower, varied "human" mouse speed applies. It is on by default, but a click that cannot wait (eating, prayer,
 * dodging, a spam-click step) and fast activity (high or extreme intensity, or a fight) keep the quick motion templates.
 * Callers mark such a stretch with {@link #enterTimeCritical()} and {@link #exitTimeCritical()} (try/finally).
 */
public final class HumanMouseSpeed
{
	private static final ThreadLocal<Integer> TIME_CRITICAL_DEPTH = ThreadLocal.withInitial(() -> 0);

	private HumanMouseSpeed()
	{
	}

	public static void enterTimeCritical()
	{
		TIME_CRITICAL_DEPTH.set(TIME_CRITICAL_DEPTH.get() + 1);
	}

	public static void exitTimeCritical()
	{
		int depth = TIME_CRITICAL_DEPTH.get() - 1;
		if (depth <= 0)
		{
			TIME_CRITICAL_DEPTH.remove();
		}
		else
		{
			TIME_CRITICAL_DEPTH.set(depth);
		}
	}

	/** Whether the calling thread is inside a time-critical stretch. */
	public static boolean isTimeCritical()
	{
		return TIME_CRITICAL_DEPTH.get() > 0;
	}

	/** Pure decision: use the human speed only when it is enabled and nothing needs the fast motion. */
	public static boolean applies(boolean enabled, ActivityIntensity intensity, boolean inCombat, boolean timeCritical)
	{
		if (!enabled || timeCritical || inCombat)
		{
			return false;
		}
		return intensity != ActivityIntensity.HIGH && intensity != ActivityIntensity.EXTREME;
	}
}
