package net.runelite.client.plugins.microbot.ddchopper;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Decides when crowding justifies a world hop: more than {@code maxOthers} other players for a grace period, hops at
 * least {@code minGapMillis} apart, and no more than {@code maxPerHour} per hour. Pure and clock-injected for tests.
 */
public final class HopGate
{
	public enum Decision
	{
		/** Not crowded. */
		STAY,
		/** Crowded, but still inside the grace period or the minimum gap between hops. */
		WAIT,
		/** Crowded and the hourly cap is used up. */
		CAPPED,
		/** Hop now. */
		HOP
	}

	public static final long GRACE_MILLIS = 10_000L;
	public static final long MIN_GAP_MILLIS = 10_000L;
	private static final long HOUR = 3_600_000L;

	private final int maxOthers;
	private final boolean seekBusy;
	private final long graceMillis;
	private final long minGapMillis;
	private final int maxPerHour;
	private final Deque<Long> hops = new ArrayDeque<>();
	private long crowdedSince = -1;
	private long lastHop = -1;

	public HopGate(int maxOthers, int maxPerHour)
	{
		this(maxOthers, GRACE_MILLIS, MIN_GAP_MILLIS, maxPerHour);
	}

	public HopGate(int maxOthers, long graceMillis, long minGapMillis, int maxPerHour)
	{
		this(maxOthers, graceMillis, minGapMillis, maxPerHour, false);
	}

	/** Inverted rule for Forestry: hop when fewer than {@code minPlayers} others are at the spot. */
	public static HopGate seekBusy(int minPlayers, int maxPerHour)
	{
		return new HopGate(minPlayers, GRACE_MILLIS, MIN_GAP_MILLIS, maxPerHour, true);
	}

	private HopGate(int maxOthers, long graceMillis, long minGapMillis, int maxPerHour, boolean seekBusy)
	{
		this.seekBusy = seekBusy;
		this.maxOthers = maxOthers;
		this.graceMillis = graceMillis;
		this.minGapMillis = minGapMillis;
		this.maxPerHour = maxPerHour;
	}

	public Decision decide(int others, long now)
	{
		if (seekBusy ? others >= maxOthers : others <= maxOthers)
		{
			crowdedSince = -1;
			return Decision.STAY;
		}
		if (crowdedSince < 0)
		{
			crowdedSince = now;
		}
		if (now - crowdedSince < graceMillis)
		{
			return Decision.WAIT;
		}
		if (lastHop >= 0 && now - lastHop < minGapMillis)
		{
			return Decision.WAIT;
		}
		while (!hops.isEmpty() && now - hops.peekFirst() >= HOUR)
		{
			hops.pollFirst();
		}
		if (hops.size() >= maxPerHour)
		{
			return Decision.CAPPED;
		}
		return Decision.HOP;
	}

	/** Call when a hop was issued; the new world gets a fresh grace period. */
	public void recordHop(long now)
	{
		lastHop = now;
		hops.addLast(now);
		crowdedSince = -1;
	}

	/** Parses a comma/space separated world list such as "301, 302 303". Bad tokens are ignored. */
	public static Set<Integer> parseWorlds(String text)
	{
		Set<Integer> out = new HashSet<>();
		if (text == null)
		{
			return out;
		}
		for (String token : text.split("[,;\\s]+"))
		{
			try
			{
				if (!token.isEmpty())
				{
					out.add(Integer.parseInt(token));
				}
			}
			catch (NumberFormatException ignored)
			{
				// skip
			}
		}
		return out;
	}
}
