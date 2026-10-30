package net.runelite.client.plugins.microbot.questhelper;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import net.runelite.api.coords.WorldPoint;

/**
 * Which owned teleport (a tablet in the inventory, or worn jewellery) to use when a quest walk is stuck far from its
 * goal: the one that lands nearest the goal, and only when that is clearly nearer than the player is now. Pure, so the
 * choice can be tested. Never a spell, and never an item the player does not own.
 */
public final class QuestStuckTeleports
{
	/** A goal further than this from the player (underground read as the surface above it) is worth teleporting toward. */
	public static final int FAR_GOAL_TILES = 100;
	/** The landing must beat the current distance by this much to be worth a teleport. */
	private static final int MIN_GAIN = 50;

	/** One owned teleport: the item (lower case) it is found by, where it lands, and how it is used. */
	public static final class Option
	{
		public final String item;
		public final WorldPoint landing;
		/** The inventory action ("Break") or the worn item's teleport action ("Edgeville"). */
		public final String action;
		public final boolean worn;

		Option(String item, WorldPoint landing, String action, boolean worn)
		{
			this.item = item;
			this.landing = landing;
			this.action = action;
			this.worn = worn;
		}
	}

	private static final List<Option> OPTIONS = List.of(
		new Option("varrock teleport", new WorldPoint(3213, 3424, 0), "Break", false),
		new Option("lumbridge teleport", new WorldPoint(3222, 3218, 0), "Break", false),
		new Option("falador teleport", new WorldPoint(2966, 3379, 0), "Break", false),
		new Option("camelot teleport", new WorldPoint(2757, 3478, 0), "Break", false),
		new Option("ardougne teleport", new WorldPoint(2662, 3307, 0), "Break", false),
		new Option("amulet of glory", new WorldPoint(3087, 3496, 0), "Edgeville", true),
		new Option("ring of wealth", new WorldPoint(3164, 3478, 0), "Grand Exchange", true));

	private QuestStuckTeleports()
	{
	}

	/** Underground maps start here; surface regions in the 6,400-7,500 band (Castle Drakan, Sanguine) are not shifted. */
	private static final int UNDERGROUND_Y = 9000;
	/** The underground map sits 6,400 tiles north of the surface it lies under. */
	private static final int UNDERGROUND_SHIFT = 6400;

	/** The surface-equivalent y of a point. */
	private static int surfaceY(WorldPoint p)
	{
		return p.getY() >= UNDERGROUND_Y ? p.getY() - UNDERGROUND_SHIFT : p.getY();
	}

	/** Tiles between two points, underground ones read as the surface above them. */
	static int distance(WorldPoint a, WorldPoint b)
	{
		return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(surfaceY(a) - surfaceY(b)));
	}

	/** Whether the goal is far enough from the player for a teleport to be the way out. */
	public static boolean goalIsFar(WorldPoint at, WorldPoint goal)
	{
		return at != null && goal != null && distance(at, goal) > FAR_GOAL_TILES;
	}

	/**
	 * @param inventory names of the inventory items (tablets are found here)
	 * @param worn      names of the worn items (jewellery is found here, so one in the inventory never counts)
	 * @return the owned option landing nearest the goal and clearly nearer than {@code at}; null when none helps
	 */
	public static Option choose(WorldPoint at, WorldPoint goal, Collection<String> inventory, Collection<String> worn)
	{
		if (!goalIsFar(at, goal))
		{
			return null;
		}
		int current = distance(at, goal);
		Option best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (Option option : OPTIONS)
		{
			if (!owns(option.worn ? worn : inventory, option))
			{
				continue;
			}
			int distance = distance(option.landing, goal);
			if (distance + MIN_GAIN < current && distance < bestDistance)
			{
				best = option;
				bestDistance = distance;
			}
		}
		return best;
	}

	private static boolean owns(Collection<String> owned, Option option)
	{
		for (String name : owned)
		{
			if (name != null && name.toLowerCase(Locale.ROOT).startsWith(option.item))
			{
				return true;
			}
		}
		return false;
	}
}
