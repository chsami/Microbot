package net.runelite.client.plugins.microbot.ddchopper;

import net.runelite.api.coords.WorldPoint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Pure tree and spot decision (no game access) so it can be unit tested. */
public final class ChopPlanner
{
	private ChopPlanner()
	{
	}

	public static final class Input
	{
		public int level = 1;
		public Axe axe = Axe.BRONZE;
		public TreeChoice treeChoice = TreeChoice.AUTO;
		public Spot spotChoice = Spot.AUTO;
		public ChopMode mode = ChopMode.POWER_CHOP;
		public boolean members;
		public boolean allowRestricted;
		public boolean rotate;
		/** Spots the user picked to rotate between; empty means every eligible spot of the tree. */
		public Set<Spot> rotateSpots = new HashSet<>();
		/** May be null. */
		public WorldPoint player;
	}

	public static final class Plan
	{
		public final TreeType tree;
		public final Spot spot;
		/** Spots to rotate between (rank ordered); a single entry when rotation is off. */
		public final List<Spot> pool;
		/** Why a choice was overridden; empty when everything was honoured. */
		public final List<String> notes;

		Plan(TreeType tree, Spot spot, List<Spot> pool, List<String> notes)
		{
			this.tree = tree;
			this.spot = spot;
			this.pool = pool;
			this.notes = notes;
		}
	}

	public static Plan plan(Input in)
	{
		List<String> notes = new ArrayList<>();
		TreeType autoTree = WoodcuttingTable.bestTree(in.level, in.axe, in.members,
			t -> !Spot.forTree(t, in.level, in.members, in.allowRestricted).isEmpty());
		TreeType tree = autoTree;

		TreeType forced = in.treeChoice.tree();
		if (forced != null)
		{
			if (!Spot.forTree(forced, in.level, in.members, in.allowRestricted).isEmpty())
			{
				tree = forced;
			}
			else
			{
				notes.add(forced + " is not available (level, membership or no reachable spot); using Auto");
			}
		}

		Spot chosen = null;
		if (in.spotChoice != Spot.AUTO)
		{
			Spot s = in.spotChoice;
			if (!s.eligible(in.level, in.members, in.allowRestricted))
			{
				notes.add(s + " is not usable (level, membership or restricted); using Auto");
			}
			else if (forced == null || forced == s.getTree())
			{
				chosen = s;
				if (tree != s.getTree())
				{
					tree = s.getTree();
				}
			}
			else
			{
				notes.add(s + " has no " + forced + "; using Auto");
			}
		}

		List<Spot> pool = new ArrayList<>();
		if (chosen == null)
		{
			List<Spot> spots = Spot.forTree(tree, in.level, in.members, in.allowRestricted);
			spots.sort(rankOrder(in));
			if (in.rotate)
			{
				for (Spot s : spots)
				{
					if (in.rotateSpots.contains(s))
					{
						pool.add(s);
					}
				}
				if (pool.isEmpty())
				{
					pool.addAll(spots);
				}
			}
			else if (!spots.isEmpty())
			{
				pool.add(spots.get(0));
			}
			if (!pool.isEmpty())
			{
				chosen = pool.get(0);
			}
		}
		else
		{
			pool.add(chosen);
		}
		return new Plan(tree, chosen, pool, notes);
	}

	/** Parses a comma separated list of spot names (enum name or label, case-insensitive); unknown names are ignored. */
	public static Set<Spot> parseSpots(String text)
	{
		Set<Spot> out = new LinkedHashSet<>();
		if (text == null)
		{
			return out;
		}
		for (String token : text.split(","))
		{
			String t = token.trim();
			if (t.isEmpty())
			{
				continue;
			}
			for (Spot s : Spot.values())
			{
				if (s != Spot.AUTO && (s.name().equalsIgnoreCase(t) || s.getLabel().equalsIgnoreCase(t) || s.toString().equalsIgnoreCase(t)))
				{
					out.add(s);
				}
			}
		}
		return out;
	}

	private static Comparator<Spot> rankOrder(Input in)
	{
		return Comparator.<Spot>comparingInt(s -> s.rank(in.mode)).thenComparingDouble(s -> in.player == null ? 0
			: Math.hypot(in.player.getX() - s.center().getX(), in.player.getY() - s.center().getY()));
	}
}
