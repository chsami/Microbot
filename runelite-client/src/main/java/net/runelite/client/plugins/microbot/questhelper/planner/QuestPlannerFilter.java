package net.runelite.client.plugins.microbot.questhelper.planner;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Which quests the planner lists: the ones the player can do now, or all of them, narrowed by a search. Pure. */
public final class QuestPlannerFilter
{
	/** One quest in the list. {@code reason} says why it is greyed out; empty when available. */
	public static final class Row
	{
		public final String name;
		public final boolean available;
		public final String reason;

		public Row(String name, boolean available, String reason)
		{
			this.name = name;
			this.available = available;
			this.reason = reason == null ? "" : reason;
		}
	}

	private QuestPlannerFilter()
	{
	}

	/** Rows matching every word of {@code search}, available ones only unless {@code showAll}. Order is kept. */
	public static List<Row> filter(List<Row> rows, String search, boolean showAll)
	{
		String[] words = search == null ? new String[0] : search.trim().toLowerCase(Locale.ROOT).split("\\s+");
		List<Row> out = new ArrayList<>();
		for (Row row : rows)
		{
			if (!showAll && !row.available)
			{
				continue;
			}
			String lower = row.name.toLowerCase(Locale.ROOT);
			boolean match = true;
			for (String word : words)
			{
				if (!word.isEmpty() && !lower.contains(word))
				{
					match = false;
					break;
				}
			}
			if (match)
			{
				out.add(row);
			}
		}
		return out;
	}

	/** Joins the unmet requirement texts into one reason line, at most {@code max} of them. */
	public static String reasonOf(List<String> unmet, int max)
	{
		if (unmet == null || unmet.isEmpty())
		{
			return "";
		}
		List<String> shown = unmet.subList(0, Math.min(max, unmet.size()));
		String text = String.join("; ", shown);
		return unmet.size() > max ? text + " (+" + (unmet.size() - max) + " more)" : text;
	}
}
