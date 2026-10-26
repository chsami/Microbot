package net.runelite.client.plugins.microbot.ddchopper.forestry;

import java.util.EnumMap;
import java.util.Map;

/** Gives every event a time and attempt budget; an event that overruns it is ignored for a cooldown. Pure. */
public final class ForestryTracker
{
	public static final long MAX_EVENT_MS = 75_000;
	public static final int MAX_ATTEMPTS = 25;
	public static final long COOLDOWN_MS = 5 * 60_000;

	private final Map<ForestryEvent, Long> startedAt = new EnumMap<>(ForestryEvent.class);
	private final Map<ForestryEvent, Integer> attempts = new EnumMap<>(ForestryEvent.class);
	private final Map<ForestryEvent, Long> ignoredUntil = new EnumMap<>(ForestryEvent.class);

	/** True when the event may be worked on now. Counts an attempt; gives up on overrun. */
	public boolean attempt(ForestryEvent event, long nowMs)
	{
		Long until = ignoredUntil.get(event);
		if (until != null)
		{
			if (nowMs < until)
			{
				return false;
			}
			ignoredUntil.remove(event);
		}
		long start = startedAt.computeIfAbsent(event, e -> nowMs);
		int n = attempts.merge(event, 1, Integer::sum);
		if (nowMs - start > MAX_EVENT_MS || n > MAX_ATTEMPTS)
		{
			done(event);
			ignoredUntil.put(event, nowMs + COOLDOWN_MS);
			return false;
		}
		return true;
	}

	/** The event is gone (or finished): forget its budget. */
	public void done(ForestryEvent event)
	{
		startedAt.remove(event);
		attempts.remove(event);
	}

	public boolean isIgnored(ForestryEvent event, long nowMs)
	{
		Long until = ignoredUntil.get(event);
		return until != null && nowMs < until;
	}

	public void reset()
	{
		startedAt.clear();
		attempts.clear();
		ignoredUntil.clear();
	}
}
