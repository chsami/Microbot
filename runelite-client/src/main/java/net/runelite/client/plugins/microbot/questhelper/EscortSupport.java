package net.runelite.client.plugins.microbot.questhelper;

import net.runelite.api.coords.WorldPoint;

import java.util.regex.Pattern;

/**
 * Pure helpers for escort steps (an NPC follows the player to a point and stops if the player gets ahead):
 * the text matcher, the per-tick decision and the hop target. No client state: unit-testable.
 */
public final class EscortSupport
{
	/** Longest single hop toward the target, in tiles. */
	public static final int MAX_HOP = 3;
	/** The follower must be this close before the next hop. */
	public static final int KEEP_CLOSE = 2;
	/** Further behind than this and the follower is considered lost. */
	public static final int LOST_DISTANCE = 6;
	/** The player counts as arrived within this many tiles of the target. */
	public static final int ARRIVED_DISTANCE = 2;

	private static final Pattern ESCORT_TEXT = Pattern.compile(
		"(?:^|[.!?]\\s+)(?:lead|escort|accompany)\\s+\\w+\\b[^.!?]*\\bto\\b"
			+ "|\\b(?:stop|stops|stopped|keeps?|keep)\\s+following\\b"
			+ "|\\bfollow(?:s|ing)?\\s+you\\b"
			+ "|(?:^|[.!?]\\s+)take\\s+(?:him|her|them)\\s+(?:\\w+\\s+){0,3}?to\\b",
		Pattern.CASE_INSENSITIVE);

	public enum Action
	{
		/** At the target: nothing to do, the quest advances on its own. */
		ARRIVED,
		/** No follower visible: wait for it / go back toward its last position. */
		FIND_FOLLOWER,
		/** Follower lost: walk back toward it. */
		CATCH_UP,
		/** Follower lost and adjacent: talk to it again. */
		TALK_TO_FOLLOWER,
		/** Follower is behind but still following: wait for it. */
		WAIT_FOR_FOLLOWER,
		/** Follower is close: take one short hop. */
		HOP
	}

	private EscortSupport()
	{
	}

	/** True when the step text reads like "lead / escort X to Y" or "whenever they stop following you". */
	public static boolean matchesEscortText(String text)
	{
		return text != null && ESCORT_TEXT.matcher(text).find();
	}

	/**
	 * @param waitedOut we already waited the full timeout for the follower to come within {@link #KEEP_CLOSE}
	 */
	public static Action decide(WorldPoint player, WorldPoint follower, boolean followerInteracting,
		boolean waitedOut, WorldPoint target)
	{
		if (player == null || target == null)
		{
			return Action.ARRIVED;
		}
		if (player.getPlane() == target.getPlane() && player.distanceTo(target) <= ARRIVED_DISTANCE
			&& (follower == null || player.distanceTo(follower) <= KEEP_CLOSE + 1))
		{
			return Action.ARRIVED;
		}
		if (follower == null)
		{
			return Action.FIND_FOLLOWER;
		}
		int gap = player.distanceTo(follower);
		boolean lost = gap > LOST_DISTANCE || (waitedOut && gap > KEEP_CLOSE && !followerInteracting);
		if (lost)
		{
			return gap <= KEEP_CLOSE ? Action.TALK_TO_FOLLOWER : Action.CATCH_UP;
		}
		if (gap > KEEP_CLOSE && !waitedOut)
		{
			return Action.WAIT_FOR_FOLLOWER;
		}
		return Action.HOP;
	}

	/** The tile at most {@code maxHop} tiles from {@code from} on the straight line toward {@code target}. */
	public static WorldPoint nextHop(WorldPoint from, WorldPoint target, int maxHop)
	{
		int dx = target.getX() - from.getX();
		int dy = target.getY() - from.getY();
		int dist = Math.max(Math.abs(dx), Math.abs(dy));
		if (dist <= maxHop)
		{
			return target;
		}
		int sx = (int) Math.round((double) dx * maxHop / dist);
		int sy = (int) Math.round((double) dy * maxHop / dist);
		return new WorldPoint(from.getX() + sx, from.getY() + sy, from.getPlane());
	}

	/** Progress token for the stuck watchdog: changes whenever the escort hops/talks or the gap closes. */
	public static long progressToken(int actions, int gapToFollower)
	{
		return actions * 1000L + Math.max(0, Math.min(gapToFollower, 999));
	}
}
