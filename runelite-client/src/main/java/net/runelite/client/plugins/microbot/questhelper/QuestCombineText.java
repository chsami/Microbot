package net.runelite.client.plugins.microbot.questhelper;

import java.util.List;
import java.util.regex.Pattern;

/** Whether a step's text asks the player to combine items in the inventory ("Use the three map parts together"). */
public final class QuestCombineText
{
	private static final Pattern COMBINE = Pattern.compile(
		"(?i)(?<![a-z])(?:use\\b.*\\b(?:together|with each other)|combine|put\\b.*\\btogether)(?![a-z])");

	private QuestCombineText()
	{
	}

	public static boolean isCombineText(List<String> stepText)
	{
		if (stepText == null)
		{
			return false;
		}
		for (String line : stepText)
		{
			if (line != null && COMBINE.matcher(line).find())
			{
				return true;
			}
		}
		return false;
	}
}
