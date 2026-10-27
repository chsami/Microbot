package net.runelite.client.plugins.microbot.ddchopper;

import net.runelite.api.Experience;

/** Pure time-to-level maths and formatting for the overlay and the side panel. */
public final class XpEta
{
	/** Shown when there is no meaningful estimate (no XP rate yet, level cap reached). */
	public static final String UNKNOWN = "-";

	/** Returned by the millis methods when no estimate can be made. */
	public static final long NONE = -1;

	private static final double MS_PER_HOUR = 3_600_000.0;
	private static final long SECOND_MS = 1_000;
	private static final long MINUTE_MS = 60 * SECOND_MS;
	private static final long HOUR_MS = 60 * MINUTE_MS;

	private XpEta()
	{
	}

	/** Millis until {@code targetLevel} at {@code xpPerHour}; 0 when already there, {@link #NONE} without a rate. */
	public static long millisToLevel(int targetLevel, long currentXp, long xpPerHour)
	{
		int target = Math.min(targetLevel, GoalTracker.MAX_LEVEL);
		long remaining = Experience.getXpForLevel(target) - currentXp;
		if (remaining <= 0)
		{
			return 0;
		}
		if (xpPerHour <= 0)
		{
			return NONE;
		}
		return (long) Math.ceil(remaining * MS_PER_HOUR / xpPerHour);
	}

	/** Millis until the level after {@code level}; {@link #NONE} at the level cap or without a rate. */
	public static long millisToNextLevel(int level, long currentXp, long xpPerHour)
	{
		if (level >= GoalTracker.MAX_LEVEL)
		{
			return NONE;
		}
		return millisToLevel(level + 1, currentXp, xpPerHour);
	}

	/** "59s", "1m 50s", "1h 5m", or {@link #UNKNOWN} for a negative value. */
	public static String format(long millis)
	{
		if (millis < 0)
		{
			return UNKNOWN;
		}
		if (millis < MINUTE_MS)
		{
			return millis / SECOND_MS + "s";
		}
		if (millis < HOUR_MS)
		{
			return millis / MINUTE_MS + "m " + millis % MINUTE_MS / SECOND_MS + "s";
		}
		return millis / HOUR_MS + "h " + millis % HOUR_MS / MINUTE_MS + "m";
	}
}
