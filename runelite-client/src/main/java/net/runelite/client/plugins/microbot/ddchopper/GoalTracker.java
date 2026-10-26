package net.runelite.client.plugins.microbot.ddchopper;

/** Pure stop condition so it can be unit tested. Run time counts active (non-break) play time only. */
public final class GoalTracker
{
	public static final int MAX_LEVEL = 99;

	private GoalTracker()
	{
	}

	/** Returns why the script should stop now, or null to keep going. */
	public static String stopReason(GoalType type, int level, int targetLevel, long activeMillis, long runLimitMillis)
	{
		if (level >= MAX_LEVEL)
		{
			return "reached level " + MAX_LEVEL;
		}
		switch (type)
		{
			case TARGET_LEVEL:
				return level >= targetLevel ? "reached target level " + targetLevel : null;
			case RUN_TIME:
				return activeMillis >= runLimitMillis ? "run time of " + (runLimitMillis / 60_000) + " min elapsed" : null;
			default:
				return null;
		}
	}

	public static long runLimitMillis(int hours, int minutes)
	{
		return (Math.max(0, hours) * 60L + Math.max(0, minutes)) * 60_000L;
	}
}
