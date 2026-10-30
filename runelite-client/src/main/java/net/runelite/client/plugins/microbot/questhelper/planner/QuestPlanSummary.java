package net.runelite.client.plugins.microbot.questhelper.planner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.runelite.client.plugins.microbot.questhelper.QuestPurchasePlan;

/**
 * The one combined shopping list and cost of a whole queue, and the approval the single confirmation grants. Pure:
 * lines are merged by name across quests (what one quest leaves in the bank counts for the next), items of a quest
 * whose profile is bank-only are never bought, and a supply the player switched off for the Grand Exchange is left out.
 */
public final class QuestPlanSummary
{
	/** What one queued quest wants: its profile, the required items and the priced supplies. */
	public static final class Entry
	{
		public final QuestProfile profile;
		public final List<QuestInfo.ItemLine> required;
		public final List<QuestInfo.ItemLine> supplies;

		public Entry(QuestProfile profile, List<QuestInfo.ItemLine> required, List<QuestInfo.ItemLine> supplies)
		{
			this.profile = profile;
			this.required = required;
			this.supplies = supplies;
		}
	}

	private final List<QuestPurchasePlan.Line> lines;
	private final List<String> itemNames;
	private final long total;

	private QuestPlanSummary(List<QuestPurchasePlan.Line> lines, List<String> itemNames)
	{
		this.lines = lines;
		this.itemNames = itemNames;
		long sum = 0;
		for (QuestPurchasePlan.Line line : lines)
		{
			sum += line.total();
		}
		this.total = sum;
	}

	private static final class Merged
	{
		String name;
		int quantity;
		int owned;
		int unitPrice;
	}

	/** Merges the entries into one list of things to buy. */
	public static QuestPlanSummary of(List<Entry> entries)
	{
		Map<String, Merged> merged = new LinkedHashMap<>();
		List<String> everything = new ArrayList<>();
		for (Entry entry : entries)
		{
			List<QuestInfo.ItemLine> wanted = new ArrayList<>();
			if (entry.profile.geAllowed())
			{
				for (QuestInfo.ItemLine line : entry.required)
				{
					if (line.tradable)
					{
						wanted.add(line);
					}
				}
				for (QuestInfo.ItemLine line : entry.supplies)
				{
					if (line.tradable)
					{
						wanted.add(line);
					}
				}
			}
			for (QuestInfo.ItemLine line : wanted)
			{
				String key = line.name.trim().toLowerCase(Locale.ROOT);
				Merged item = merged.computeIfAbsent(key, k ->
				{
					Merged created = new Merged();
					created.name = line.name;
					created.owned = line.onPlayer + line.inBank;
					return created;
				});
				item.quantity += line.quantity;
				item.owned = Math.max(item.owned, line.onPlayer + line.inBank);
				item.unitPrice = Math.max(item.unitPrice, line.unitPrice);
				if (!everything.contains(line.name))
				{
					everything.add(line.name);
				}
			}
		}
		List<QuestPurchasePlan.Line> lines = new ArrayList<>();
		for (Merged item : merged.values())
		{
			int missing = Math.max(0, item.quantity - item.owned);
			if (missing > 0)
			{
				lines.add(new QuestPurchasePlan.Line(item.name, missing, item.unitPrice));
			}
		}
		return new QuestPlanSummary(lines, everything);
	}

	public List<QuestPurchasePlan.Line> lines()
	{
		return lines;
	}

	/** Every item the plan may touch, bought or not: these are the names the approval covers. */
	public List<String> itemNames()
	{
		return itemNames;
	}

	public long total()
	{
		return total;
	}

	/** The approval granted by confirming this summary. */
	public QuestApproval approval()
	{
		return QuestApproval.of(itemNames, total);
	}
}
