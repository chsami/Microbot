package net.runelite.client.plugins.microbot.questhelper;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Prepare to fight Elvarg (level 83)." steps: the quest tells the player to get ready before a boss. Pure helpers to
 * recognise them and size the food to bring.
 */
public final class QuestBossPrep
{
	private static final Pattern PREPARE = Pattern.compile("(?i)\\bprepare (?:to fight|for)\\b.*?\\(level (\\d{1,3})\\)");

	private QuestBossPrep()
	{
	}

	/** The boss level named in a "Prepare to fight X (level N)" step, or -1 when the text is not one. */
	public static int bossLevel(List<String> stepText)
	{
		if (stepText == null)
		{
			return -1;
		}
		for (String line : stepText)
		{
			if (line == null)
			{
				continue;
			}
			Matcher matcher = PREPARE.matcher(line);
			if (matcher.find())
			{
				return Integer.parseInt(matcher.group(1));
			}
		}
		return -1;
	}

	/**
	 * How many food items to carry into the fight: more for a higher-level boss and for a player with fewer hitpoints.
	 *
	 * @param bossLevel the boss's combat level
	 * @param hitpoints the player's real hitpoints level
	 */
	public static int foodWanted(int bossLevel, int hitpoints)
	{
		int base = bossLevel >= 80 ? 15 : bossLevel >= 50 ? 10 : 6;
		if (hitpoints >= 80)
		{
			base -= 3;
		}
		return Math.max(5, base);
	}
}
