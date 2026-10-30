package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;

import java.util.List;
import java.util.Random;

/**
 * Small per-walk randomness so the same route is not clicked identically every run.
 *
 * <p>Safety by construction: a varied minimap click is always another tile of the raw route that the
 * pathfinder already produced (so it is walkable, on the door/transport-respecting path), is only ever
 * earlier along the route than the tile the walker chose (never beyond a door or gate it stopped
 * short of), is never the final destination, and is never used near the end of the route where
 * exact-tile quest steps, dig spots and escort hand-offs happen. Off unless a caller enables it.</p>
 */
public final class WalkPathVariation
{
	/** Route tiles left after the click target below which nothing is varied (exact-tile arrivals). */
	static final int MIN_REMAINING_TILES = 12;
	/** The varied click must stay at least this far from the player so it still makes progress. */
	static final int MIN_PLAYER_DISTANCE = 3;

	private static volatile boolean enabled;
	private static volatile int strength = 2;
	private static volatile Random walkRandom = new Random();
	private static volatile Object seededFor;

	private WalkPathVariation()
	{
	}

	/** Live setting; called by the quest helper each tick. Strength 1 low, 2 medium, 3 high. */
	public static void configure(boolean on, int level)
	{
		enabled = on;
		strength = Math.max(1, Math.min(3, level));
		net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathJitter.configure(on, strength);
	}

	public static boolean isEnabled()
	{
		return enabled;
	}

	public static int getStrength()
	{
		return strength;
	}

	/** Reseeds on a new walk target so one walk is internally consistent but walks differ. */
	static Random randomFor(Object walkTarget)
	{
		if (!java.util.Objects.equals(seededFor, walkTarget))
		{
			seededFor = walkTarget;
			walkRandom = new Random(System.nanoTime() ^ (walkTarget == null ? 0 : walkTarget.hashCode()));
		}
		return walkRandom;
	}

	/** Maximum route tiles the click may be pulled back, from the strength setting. */
	static int maxPullBack(int level)
	{
		return Math.max(1, Math.min(3, level)) + 1; // 2, 3, 4
	}

	/**
	 * Returns the minimap click target to use: either {@code target} or an earlier tile of {@code rawPath}.
	 */
	public static WorldPoint varyMinimapTarget(List<WorldPoint> rawPath, WorldPoint target,
		WorldPoint player, Object walkTarget)
	{
		if (!enabled)
		{
			return target;
		}
		return vary(rawPath, target, player, strength, randomFor(walkTarget));
	}

	/** Pure selection, exposed for tests. */
	static WorldPoint vary(List<WorldPoint> rawPath, WorldPoint target, WorldPoint player, int level, Random rng)
	{
		if (rawPath == null || target == null || player == null)
		{
			return target;
		}
		int index = rawPath.lastIndexOf(target);
		if (index < 0 || rawPath.size() - 1 - index < MIN_REMAINING_TILES)
		{
			return target;
		}
		int pull = rng.nextInt(maxPullBack(level) + 1);
		for (int back = pull; back > 0; back--)
		{
			int i = index - back;
			if (i < 0)
			{
				continue;
			}
			WorldPoint candidate = rawPath.get(i);
			if (candidate.getPlane() == target.getPlane()
				&& candidate.getPlane() == player.getPlane()
				&& candidate.distanceTo(player) >= MIN_PLAYER_DISTANCE)
			{
				return candidate;
			}
		}
		return target;
	}

	/** Range for the minimap cadence: widens with strength, never below 1. */
	public static int nextCadence(int min, int maxExclusive, Random rng)
	{
		int widen = enabled ? Math.max(0, strength / 3) : 0;
		int lo = Math.max(1, min - widen / 2);
		int hi = Math.max(lo + 1, maxExclusive + widen);
		return lo + rng.nextInt(hi - lo);
	}
}
