package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import net.runelite.api.coords.WorldPoint;

/**
 * Remembers the quest walk target that no route reaches from where the player stood, so the quest loop stops
 * searching for it every few seconds. Each repeat failure for the same target doubles the wait, up to
 * {@link #MAX_MS}; a different target starts again from {@link #FIRST_MS}. The wait only holds while the player is
 * still near the spot where the search failed: moving away or changing plane (for example after crossing the
 * sea by hand) makes the route worth trying again.
 */
final class UnreachableBackoff
{
	static final long FIRST_MS = 30_000L;
	static final long MAX_MS = 300_000L;
	/** The player counts as having moved on after this many tiles from where the search failed. */
	static final int MOVED_ON_TILES = 50;

	private WorldPoint target;
	private WorldPoint failedFrom;
	private long retryAtMs;
	private long waitMs;

	/** Notes a failed walk. Returns true when the caller should log it: the first failure for this target. */
	synchronized boolean record(WorldPoint failed, WorldPoint playerAt, long nowMs)
	{
		boolean sameTarget = failed.equals(target);
		waitMs = sameTarget ? Math.min(waitMs * 2, MAX_MS) : FIRST_MS;
		retryAtMs = nowMs + waitMs;
		target = failed;
		failedFrom = playerAt;
		return !sameTarget;
	}

	synchronized boolean isBackedOff(WorldPoint walkTarget, WorldPoint playerAt, long nowMs)
	{
		return walkTarget != null && walkTarget.equals(target) && nowMs < retryAtMs && !movedOn(playerAt);
	}

	/** The target being waited out from {@code playerAt}, or {@code null} once the wait is over. */
	synchronized WorldPoint target(WorldPoint playerAt, long nowMs)
	{
		return nowMs < retryAtMs && !movedOn(playerAt) ? target : null;
	}

	synchronized long remainingMs(long nowMs)
	{
		return Math.max(0, retryAtMs - nowMs);
	}

	/** Forgets the target if it is the one given up on (the walk reached it). */
	synchronized void clear(WorldPoint reached)
	{
		if (reached != null && reached.equals(target))
		{
			reset();
		}
	}

	synchronized void reset()
	{
		target = null;
		failedFrom = null;
		retryAtMs = 0;
		waitMs = 0;
	}

	private boolean movedOn(WorldPoint playerAt)
	{
		return playerAt != null && failedFrom != null
			&& (playerAt.getPlane() != failedFrom.getPlane() || playerAt.distanceTo2D(failedFrom) > MOVED_ON_TILES);
	}
}
