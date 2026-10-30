package net.runelite.client.plugins.microbot.questhelper;

import java.util.List;
import java.util.Map;

/**
 * Chooses the menu action an NPC step should use, from the step text and the NPC's own actions.
 */
public final class QuestNpcActions
{
	/** Verbs the step text may use, mapped to the menu actions that carry them out (lower case). */
	private static final Map<String, List<String>> SYNONYMS = Map.of(
		"pickpocket", List.of("pickpocket", "steal-from", "steal"));

	private QuestNpcActions()
	{
	}

	/**
	 * Picks the action: one named in the text, else one the text names by a synonym (pickpocket for Steal-from),
	 * else Talk-to if the NPC has it, else its first non-empty action, else "Talk-to".
	 */
	public static String choose(List<String> text, String[] npcActions)
	{
		if (npcActions == null)
		{
			return "Talk-to";
		}
		for (String action : npcActions)
		{
			if (action != null && !action.isEmpty() && textContains(text, action))
			{
				return action;
			}
		}
		for (Map.Entry<String, List<String>> entry : SYNONYMS.entrySet())
		{
			if (!textContains(text, entry.getKey()))
			{
				continue;
			}
			for (String action : npcActions)
			{
				if (action != null && entry.getValue().contains(action.toLowerCase()))
				{
					return action;
				}
			}
		}
		String fallback = null;
		for (String action : npcActions)
		{
			if (action == null || action.isEmpty())
			{
				continue;
			}
			if ("Talk-to".equalsIgnoreCase(action))
			{
				return action;
			}
			if (fallback == null)
			{
				fallback = action;
			}
		}
		return fallback != null ? fallback : "Talk-to";
	}

	private static boolean textContains(List<String> text, String word)
	{
		if (text == null)
		{
			return false;
		}
		String lower = word.toLowerCase();
		for (String line : text)
		{
			if (line != null && line.toLowerCase().contains(lower))
			{
				return true;
			}
		}
		return false;
	}
}
