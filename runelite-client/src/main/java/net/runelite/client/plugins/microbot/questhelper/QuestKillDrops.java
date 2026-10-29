package net.runelite.client.plugins.microbot.questhelper;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The item a "kill X for a key" step is after, read from the step's text. Pure, so it can be tested. */
public final class QuestKillDrops
{
	private static final Pattern FOR_ITEM = Pattern.compile(
		"(?i)\\bfor (?:a|an|the|some) ([a-z][a-z' -]{1,30}?)\\s*(?:[.!,;]|$)");

	private QuestKillDrops()
	{
	}

	/**
	 * @return the dropped item's name ("key", "magenta key"), or null when the text does not say a kill is for an item
	 */
	public static String dropName(List<String> stepText)
	{
		if (stepText == null)
		{
			return null;
		}
		for (String line : stepText)
		{
			if (line == null)
			{
				continue;
			}
			Matcher matcher = FOR_ITEM.matcher(line.trim());
			if (matcher.find())
			{
				return matcher.group(1).trim();
			}
		}
		return null;
	}
}
