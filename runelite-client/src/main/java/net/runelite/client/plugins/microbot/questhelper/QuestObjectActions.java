package net.runelite.client.plugins.microbot.questhelper;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chooses the menu action an object step should use, from the step text and the object's own actions.
 */
public final class QuestObjectActions
{
	private static final Pattern RIGHT_CLICK = Pattern.compile("right-click +([A-Za-z][A-Za-z-]*)",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern TAGS = Pattern.compile("<[^>]*>");

	private QuestObjectActions()
	{
	}

	/** The verb after "Right-click" in the step text (e.g. "Fill"), capitalised, or null when the text has none. */
	public static String rightClickAction(List<String> text)
	{
		if (text == null)
		{
			return null;
		}
		for (String line : text)
		{
			if (line == null)
			{
				continue;
			}
			Matcher m = RIGHT_CLICK.matcher(TAGS.matcher(line).replaceAll(""));
			if (m.find())
			{
				String verb = m.group(1);
				return Character.toUpperCase(verb.charAt(0)) + verb.substring(1).toLowerCase();
			}
		}
		return null;
	}

	/**
	 * Picks the action: a "Right-click X" verb the object has, else the first object action named in the text,
	 * else the object's first non-empty action (its default), else "".
	 */
	public static String choose(List<String> text, String[] objectActions)
	{
		if (objectActions == null)
		{
			return "";
		}
		String verb = rightClickAction(text);
		if (verb != null)
		{
			for (String action : objectActions)
			{
				if (action != null && action.equalsIgnoreCase(verb))
				{
					return action;
				}
			}
		}
		if (text != null)
		{
			for (String action : objectActions)
			{
				if (action == null || action.isEmpty())
				{
					continue;
				}
				String lower = action.toLowerCase();
				for (String line : text)
				{
					if (line != null && line.toLowerCase().contains(lower))
					{
						return action;
					}
				}
			}
		}
		for (String action : objectActions)
		{
			if (action != null && !action.isEmpty())
			{
				return action;
			}
		}
		return "";
	}

	/** Distance at which an approach counts as arrived: loose only when the tile is reachable without crossing a wall. */
	public static int arrivalTolerance(boolean tileReachableFromHere)
	{
		return tileReachableFromHere ? 3 : 0;
	}

	/** True when the object's actions include the wanted one (case-insensitive). */
	public static boolean hasAction(String[] objectActions, String wanted)
	{
		if (objectActions == null || wanted == null)
		{
			return false;
		}
		for (String action : objectActions)
		{
			if (action != null && action.equalsIgnoreCase(wanted))
			{
				return true;
			}
		}
		return false;
	}
}
