package net.runelite.client.plugins.microbot.questhelper;

import java.util.Collection;
import net.runelite.api.CollisionDataFlag;
import java.util.Comparator;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/**
 * Picks the tile to stand on for an object step. The object's own tile is usually not walkable (cupboard, crate,
 * staircase), and the closest walkable tile may be on the far side of a wall. The right tile is one the object can be
 * used from and that the real route planner can reach, including through closed doors and gates it opens on the way.
 */
public final class QuestObjectApproach
{
	/** Route length for a tile the planner cannot reach. */
	public static final int UNREACHABLE = -1;

	/** Collision flags of the world tile at (x, y) on the object's floor. */
	@FunctionalInterface
	public interface CollisionFlags
	{
		int at(int worldX, int worldY);
	}

	private QuestObjectApproach()
	{
	}

	/**
	 * The candidates of a search step in the order to try them: when some show the sign the step is looking for (the
	 * "Mew!" over a kitten crate) only those, nearest first; otherwise all of them, nearest first.
	 */
	public static <T> java.util.List<T> orderForSearch(Collection<T> candidates, Predicate<T> showsHint,
		Comparator<T> nearestFirst)
	{
		java.util.List<T> hinted = new java.util.ArrayList<>();
		for (T candidate : candidates)
		{
			if (showsHint.test(candidate))
			{
				hinted.add(candidate);
			}
		}
		java.util.List<T> ordered = new java.util.ArrayList<>(hinted.isEmpty() ? candidates : hinted);
		ordered.sort(nearestFirst);
		return ordered;
	}

	/**
	 * The next candidate of a search step that goes through many identical objects (crates, bookcases): the best by
	 * {@code order} among those not searched yet, so each is tried once and neighbours follow each other; when every one
	 * has been tried, the best overall, to go round again.
	 */
	public static <T> T nextToSearch(Collection<T> candidates, Predicate<T> alreadySearched, Comparator<T> order)
	{
		T best = null;
		T bestSearched = null;
		for (T candidate : candidates)
		{
			if (alreadySearched.test(candidate))
			{
				if (bestSearched == null || order.compare(candidate, bestSearched) < 0)
				{
					bestSearched = candidate;
				}
			}
			else if (best == null || order.compare(candidate, best) < 0)
			{
				best = candidate;
			}
		}
		return best != null ? best : bestSearched;
	}

	/**
	 * Whether {@code tile} touches the object's footprint along a cardinal edge that is not closed by a wall, fence,
	 * cage bars or a gate. Line of sight is no substitute: it passes through bars, which is how a tile outside a cage
	 * looked like a tile beside the crate inside it.
	 */
	public static boolean touchesWithOpenEdge(WorldPoint tile, Collection<WorldPoint> footprint, CollisionFlags flags)
	{
		for (WorldPoint part : footprint)
		{
			if (part.getPlane() != tile.getPlane())
			{
				continue;
			}
			int dx = tile.getX() - part.getX();
			int dy = tile.getY() - part.getY();
			if (Math.abs(dx) + Math.abs(dy) != 1)
			{
				continue;
			}
			// the edge between the two tiles must not block movement from either side
			int tileSide;
			int partSide;
			if (dx == -1)
			{
				tileSide = CollisionDataFlag.BLOCK_MOVEMENT_EAST;
				partSide = CollisionDataFlag.BLOCK_MOVEMENT_WEST;
			}
			else if (dx == 1)
			{
				tileSide = CollisionDataFlag.BLOCK_MOVEMENT_WEST;
				partSide = CollisionDataFlag.BLOCK_MOVEMENT_EAST;
			}
			else if (dy == -1)
			{
				tileSide = CollisionDataFlag.BLOCK_MOVEMENT_NORTH;
				partSide = CollisionDataFlag.BLOCK_MOVEMENT_SOUTH;
			}
			else
			{
				tileSide = CollisionDataFlag.BLOCK_MOVEMENT_SOUTH;
				partSide = CollisionDataFlag.BLOCK_MOVEMENT_NORTH;
			}
			if ((flags.at(tile.getX(), tile.getY()) & tileSide) == 0
				&& (flags.at(part.getX(), part.getY()) & partSide) == 0)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * @param objectTile       the tile of the object
	 * @param candidates       walkable tiles near the object
	 * @param usableFrom       whether the object can be used from the tile (not through a wall)
	 * @param routeLength      length of the planned route from the player to the tile, or {@link #UNREACHABLE}
	 * @return the usable, reachable tile with the shortest route (nearest to the object on ties), or {@code null}
	 */
	public static WorldPoint choose(WorldPoint objectTile, Collection<WorldPoint> candidates,
		Predicate<WorldPoint> usableFrom, ToIntFunction<WorldPoint> routeLength)
	{
		WorldPoint best = null;
		int bestLength = Integer.MAX_VALUE;
		Comparator<WorldPoint> tieBreak = Comparator
			.comparingInt((WorldPoint tile) -> tile.distanceTo2D(objectTile))
			.thenComparingInt(WorldPoint::getX)
			.thenComparingInt(WorldPoint::getY);
		for (WorldPoint tile : candidates)
		{
			if (!usableFrom.test(tile))
			{
				continue;
			}
			int length = routeLength.applyAsInt(tile);
			if (length == UNREACHABLE)
			{
				continue;
			}
			if (length < bestLength || (length == bestLength && tieBreak.compare(tile, best) < 0))
			{
				best = tile;
				bestLength = length;
			}
		}
		return best;
	}

	/**
	 * Whether the player is close enough to click the object (same plane, within {@code range} tiles). Both points are
	 * scene-local, so it holds inside instanced areas, where world coordinates of the player (template) and of objects
	 * (instance) differ.
	 */
	public static boolean withinClickRange(LocalPoint player, LocalPoint object, int playerPlane, int objectPlane, int range)
	{
		if (player == null || object == null || playerPlane != objectPlane)
		{
			return false;
		}
		int tiles = Math.max(Math.abs(player.getX() - object.getX()), Math.abs(player.getY() - object.getY()))
			/ Perspective.LOCAL_TILE_SIZE;
		return tiles <= range;
	}
}
