package net.runelite.client.plugins.microbot.util.walker;

/**
 * Coordinate-free process-lifetime timing aggregate for authoritative canary decisions.
 *
 * <p>The planning duration starts when an active route is submitted (or immediately before a
 * synchronous/cave local search) and ends only after comparison, fallback/selection and upstream-route
 * materialization have completed. It therefore measures when a route can actually become executable,
 * rather than adding two independently reported engine search times after the fact.</p>
 */
public final class Rs2PlannerCanaryPerformanceStats
{
	private final long planningSamples;
	private final long planningNanosTotal;
	private final long planningNanosMax;
	private final long localSearchNanosTotal;
	private final long localSearchNanosMax;
	private final long upstreamSearchSamples;
	private final long upstreamSearchNanosTotal;
	private final long upstreamSearchNanosMax;

	Rs2PlannerCanaryPerformanceStats(
		long planningSamples,
		long planningNanosTotal,
		long planningNanosMax,
		long localSearchNanosTotal,
		long localSearchNanosMax,
		long upstreamSearchSamples,
		long upstreamSearchNanosTotal,
		long upstreamSearchNanosMax)
	{
		this.planningSamples = requireNonNegative(planningSamples, "planningSamples");
		this.planningNanosTotal = requireNonNegative(planningNanosTotal, "planningNanosTotal");
		this.planningNanosMax = requireNonNegative(planningNanosMax, "planningNanosMax");
		this.localSearchNanosTotal = requireNonNegative(
			localSearchNanosTotal, "localSearchNanosTotal");
		this.localSearchNanosMax = requireNonNegative(localSearchNanosMax, "localSearchNanosMax");
		this.upstreamSearchSamples = requireNonNegative(
			upstreamSearchSamples, "upstreamSearchSamples");
		this.upstreamSearchNanosTotal = requireNonNegative(
			upstreamSearchNanosTotal, "upstreamSearchNanosTotal");
		this.upstreamSearchNanosMax = requireNonNegative(
			upstreamSearchNanosMax, "upstreamSearchNanosMax");
		if (upstreamSearchSamples > planningSamples)
		{
			throw new IllegalArgumentException(
				"upstream search samples cannot exceed canary planning samples");
		}
	}

	private static long requireNonNegative(long value, String name)
	{
		if (value < 0L)
		{
			throw new IllegalArgumentException(name + " must be non-negative");
		}
		return value;
	}

	public long getPlanningSamples() { return planningSamples; }
	public long getPlanningNanosTotal() { return planningNanosTotal; }
	public long getPlanningNanosMax() { return planningNanosMax; }
	public long getLocalSearchNanosTotal() { return localSearchNanosTotal; }
	public long getLocalSearchNanosMax() { return localSearchNanosMax; }
	public long getUpstreamSearchSamples() { return upstreamSearchSamples; }
	public long getUpstreamSearchNanosTotal() { return upstreamSearchNanosTotal; }
	public long getUpstreamSearchNanosMax() { return upstreamSearchNanosMax; }
}
