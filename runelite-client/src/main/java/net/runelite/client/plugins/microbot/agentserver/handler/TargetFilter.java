package net.runelite.client.plugins.microbot.agentserver.handler;

import java.util.Collection;
import net.runelite.api.coords.WorldPoint;

/**
 * Picks the exact object or NPC a request names. With several of the same id around (crates on both sides of a wall) the
 * nearest one is not the intended one, so a request may add the tile (x and y together, optionally plane) of an object,
 * or the index or tile of an NPC, and only that target matches.
 */
final class TargetFilter
{
	private TargetFilter()
	{
	}

	/** Whether the request names a tile: both x and y. Only one of them is a malformed request. */
	static boolean namesTile(Integer x, Integer y)
	{
		return x != null && y != null;
	}

	/** Whether x and y were given only half: the request is rejected. */
	static boolean halfATile(Integer x, Integer y)
	{
		return (x == null) != (y == null);
	}

	/** Whether any of the tiles the target covers is the tile asked for; no tile asked for matches everything. */
	static boolean matchesTile(Collection<WorldPoint> coveredTiles, Integer x, Integer y, Integer plane)
	{
		if (halfATile(x, y))
		{
			return false;
		}
		if (!namesTile(x, y))
		{
			return true;
		}
		if (coveredTiles == null)
		{
			return false;
		}
		for (WorldPoint at : coveredTiles)
		{
			if (at != null && at.getX() == x && at.getY() == y && (plane == null || at.getPlane() == plane))
			{
				return true;
			}
		}
		return false;
	}

	/** Single-tile form of {@link #matchesTile(Collection, Integer, Integer, Integer)}. */
	static boolean matchesTile(WorldPoint at, Integer x, Integer y, Integer plane)
	{
		return matchesTile(at == null ? null : java.util.Collections.singletonList(at), x, y, plane);
	}

	/** Whether an NPC is the one asked for: by index when given, otherwise by tile. */
	static boolean matchesNpc(int npcIndex, WorldPoint at, Integer wantedIndex, Integer x, Integer y, Integer plane)
	{
		if (wantedIndex != null)
		{
			return npcIndex == wantedIndex;
		}
		return matchesTile(at, x, y, plane);
	}

	/** Whether the request names a particular target at all. */
	static boolean isSpecific(Integer index, Integer x, Integer y)
	{
		return index != null || namesTile(x, y);
	}
}
