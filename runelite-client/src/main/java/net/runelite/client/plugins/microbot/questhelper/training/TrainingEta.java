package net.runelite.client.plugins.microbot.questhelper.training;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Rough time guesses for training a skill; only used to show an ETA in the plan. Pure. */
public final class TrainingEta
{
	private static final Map<String, Integer> XP_PER_HOUR = new HashMap<>();

	static
	{
		XP_PER_HOUR.put("FIREMAKING", 30_000);
		XP_PER_HOUR.put("COOKING", 40_000);
		XP_PER_HOUR.put("SMITHING", 25_000);
		XP_PER_HOUR.put("FLETCHING", 40_000);
		XP_PER_HOUR.put("CRAFTING", 30_000);
		XP_PER_HOUR.put("MAGIC", 20_000);
		XP_PER_HOUR.put("THIEVING", 25_000);
		XP_PER_HOUR.put("MINING", 12_000);
		XP_PER_HOUR.put("WOODCUTTING", 15_000);
		XP_PER_HOUR.put("COMBAT", 30_000);
	}

	private static final int DEFAULT_XP_PER_HOUR = 20_000;

	private TrainingEta()
	{
	}

	/** Experience needed for a level (the standard table), level 1 is 0. */
	public static int xpForLevel(int level)
	{
		double points = 0;
		for (int l = 1; l < Math.max(1, Math.min(level, 126)); l++)
		{
			points += Math.floor(l + 300.0 * Math.pow(2.0, l / 7.0));
		}
		return (int) Math.floor(points / 4);
	}

	/** Guessed minutes to go from one level to another; at least 1 when there is anything to do. */
	public static int minutes(String skill, int fromLevel, int toLevel)
	{
		if (toLevel <= fromLevel)
		{
			return 0;
		}
		int rate = XP_PER_HOUR.getOrDefault(skill == null ? "" : skill.toUpperCase(Locale.ROOT), DEFAULT_XP_PER_HOUR);
		double xp = xpForLevel(toLevel) - xpForLevel(fromLevel);
		return (int) Math.max(1, Math.ceil(xp / rate * 60.0));
	}
}
