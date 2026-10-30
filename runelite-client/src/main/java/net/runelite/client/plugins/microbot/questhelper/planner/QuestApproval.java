package net.runelite.client.plugins.microbot.questhelper.planner;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What the player approved to buy at the planner's single confirmation: the item names and a gp budget. While the
 * queue runs, a purchase that this covers goes ahead without a dialog; one it does not cover skips its quest.
 */
public final class QuestApproval
{
	/** The budget may be exceeded by this fraction (prices move between the summary and the buy). */
	public static final double SLACK = 0.25;

	private Set<String> items = new LinkedHashSet<>();
	private long budget;
	/** Anything goes (the Agent Server queue, where the caller already decided). */
	private boolean everything;
	/** Gp already spent under this approval. */
	private long spent;

	public QuestApproval()
	{
	}

	/** Approves exactly these items up to the budget. */
	public static QuestApproval of(Collection<String> names, long budgetGp)
	{
		QuestApproval approval = new QuestApproval();
		for (String name : names)
		{
			approval.items.add(key(name));
		}
		approval.budget = Math.max(0, budgetGp);
		return approval;
	}

	/** Approves every purchase. */
	public static QuestApproval everything()
	{
		QuestApproval approval = new QuestApproval();
		approval.everything = true;
		return approval;
	}

	private static String key(String name)
	{
		return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
	}

	public boolean isEverything()
	{
		return everything;
	}

	public long budget()
	{
		return budget;
	}

	public boolean approves(String itemName)
	{
		return everything || (items != null && items.contains(key(itemName)));
	}

	/** True when every item is approved and the total fits the budget (plus the slack). */
	public boolean covers(List<String> itemNames, long totalGp)
	{
		if (everything)
		{
			return true;
		}
		for (String name : itemNames)
		{
			if (!approves(name))
			{
				return false;
			}
		}
		return spent + totalGp <= (long) Math.ceil(budget * (1 + SLACK));
	}

	/** Also approves these items, adding gp to the budget (training steps added by a requirement plan). */
	public void allow(Collection<String> names, long extraGp)
	{
		if (everything)
		{
			return;
		}
		for (String name : names)
		{
			items.add(key(name));
		}
		budget += Math.max(0, extraGp);
	}

	/** Counts a purchase against the budget. */
	public void record(long totalGp)
	{
		spent += Math.max(0, totalGp);
	}

	/** Why a purchase is not covered, for the log. */
	public String explainRefusal(List<String> itemNames, long totalGp)
	{
		StringBuilder sb = new StringBuilder();
		for (String name : itemNames)
		{
			if (!approves(name))
			{
				sb.append(sb.length() == 0 ? "" : ", ").append(name);
			}
		}
		if (sb.length() > 0)
		{
			return "not pre-approved: " + sb;
		}
		return String.format(Locale.ROOT, "~%,d gp is over the approved budget of %,d gp (%,d already spent)", totalGp, budget,
			spent);
	}
}
