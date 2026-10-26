package net.runelite.client.plugins.microbot.ddchopper;

import java.util.function.Predicate;

/**
 * XP and rate data used to pick the tree with the best XP/hour.
 *
 * <p>Sources: OSRS Wiki "Woodcutting" (experience per log: Tree 25, Oak 37.5, Willow 67.5, Teak 85, Maple 100,
 * Mahogany 125, Yew 175, Magic 250, Redwood 380; level requirements 1/15/30/35/45/50/60/75/90) and the wiki's per-axe
 * chop-chance tables. The logs/hour figures below are approximate power-chop rates, rounded from the wiki success
 * chances at the level a tree is first used; normal and oak trees fall often, so their effective rate includes
 * walking to the next tree. They are only ever compared with each other, so the ordering matters more than the
 * absolute values. Members-only Teak is the standard mid-level power-chop from 35, Mahogany from 50.</p>
 */
public final class WoodcuttingTable
{
	/** XP per log, indexed like {@link TreeType}. */
	private static final double[] XP = {25.0, 37.5, 67.5, 85.0, 100.0, 125.0, 175.0, 250.0, 380.0};

	/**
	 * Approximate logs/hour, rows per {@link TreeType}, columns per {@link Axe} ordinal
	 * (bronze, iron, steel, black, mithril, adamant, rune, dragon, crystal).
	 */
	private static final int[][] LOGS_PER_HOUR = {
		{500, 520, 560, 590, 630, 670, 700, 720, 740},  // Tree
		{520, 550, 600, 640, 700, 760, 810, 840, 860},  // Oak
		{560, 600, 660, 710, 780, 850, 920, 960, 990},  // Willow
		{480, 510, 550, 580, 630, 690, 740, 770, 790},  // Teak
		{330, 350, 380, 400, 430, 470, 500, 520, 540},  // Maple
		{360, 380, 410, 440, 480, 520, 560, 580, 600},  // Mahogany
		{240, 255, 280, 300, 330, 360, 385, 400, 415},  // Yew
		{120, 128, 140, 150, 165, 180, 192, 200, 208},  // Magic
		{150, 158, 172, 184, 200, 215, 230, 240, 250},  // Redwood
	};

	private WoodcuttingTable()
	{
	}

	public static double xpPerLog(TreeType tree)
	{
		return XP[tree.ordinal()];
	}

	public static int logsPerHour(TreeType tree, Axe axe)
	{
		return LOGS_PER_HOUR[tree.ordinal()][axe.ordinal()];
	}

	public static double xpPerHour(TreeType tree, Axe axe)
	{
		return xpPerLog(tree) * logsPerHour(tree, axe);
	}

	/**
	 * The tree with the highest XP/hour that this level (and membership) allows.
	 *
	 * @param available restricts to trees the script can actually reach; null means all
	 */
	public static TreeType bestTree(int woodcuttingLevel, Axe axe, boolean members, Predicate<TreeType> available)
	{
		TreeType best = TreeType.NORMAL;
		double bestXp = -1;
		for (TreeType tree : TreeType.values())
		{
			if (woodcuttingLevel < tree.getLevel() || (tree.isMembers() && !members)
				|| (available != null && !available.test(tree)))
			{
				continue;
			}
			double xp = xpPerHour(tree, axe);
			if (xp > bestXp)
			{
				bestXp = xp;
				best = tree;
			}
		}
		return best;
	}
}
