package net.runelite.client.plugins.microbot.util.walker;

/** Immutable outcome counters for one overlapping shadow-evidence coverage tag. */
public final class Rs2PlannerShadowCoverageStats
{
	private final long completed;
	private final long matches;
	private final long divergences;
	private final long failures;

	Rs2PlannerShadowCoverageStats(long matches, long divergences, long failures)
	{
		this.matches = requireNonNegative(matches, "matches");
		this.divergences = requireNonNegative(divergences, "divergences");
		this.failures = requireNonNegative(failures, "failures");
		this.completed = Math.addExact(Math.addExact(matches, divergences), failures);
	}

	private static long requireNonNegative(long value, String name)
	{
		if (value < 0)
		{
			throw new IllegalArgumentException(name + " must be non-negative");
		}
		return value;
	}

	public long getCompleted() { return completed; }
	public long getMatches() { return matches; }
	public long getDivergences() { return divergences; }
	public long getFailures() { return failures; }
}
