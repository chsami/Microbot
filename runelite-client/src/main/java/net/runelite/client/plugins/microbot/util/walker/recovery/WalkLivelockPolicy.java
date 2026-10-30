package net.runelite.client.plugins.microbot.util.walker.recovery;

/**
 * Decides when a walk that has stopped closing distance on its goal is aborted instead of merely
 * reported. The 300 s observe-only budget let livelocked walks (Lumbridge stairs, exempt exits
 * refunding the tail cap) run for minutes; a shorter, configurable abort frees the caller to recover.
 *
 * <p>Pure apart from the configurable threshold; the walker performs the abort.
 */
public final class WalkLivelockPolicy
{
	/** Default no-distance-gain window before a walk is aborted. */
	public static final long DEFAULT_ABORT_MS = 45_000L;

	private static volatile long abortMs = DEFAULT_ABORT_MS;
	private static volatile String lastAbortReason;
	private static volatile long lastAbortAtMs;

	private WalkLivelockPolicy()
	{
	}

	/** Sets the abort window in ms; {@code <= 0} disables the abort. */
	public static void setAbortMs(long ms)
	{
		abortMs = ms;
	}

	public static long getAbortMs()
	{
		return abortMs;
	}

	public static boolean shouldAbort(long lastProgressAtMs, long nowMs, long thresholdMs)
	{
		return lastProgressAtMs > 0L && thresholdMs > 0L && nowMs - lastProgressAtMs > thresholdMs;
	}

	public static void recordAbort(String reason, long nowMs)
	{
		lastAbortReason = reason;
		lastAbortAtMs = nowMs;
	}

	/** Reason of the most recent livelock abort, or null if none yet. */
	public static String getLastAbortReason()
	{
		return lastAbortReason;
	}

	public static long getLastAbortAtMs()
	{
		return lastAbortAtMs;
	}
}
