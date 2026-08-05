package net.runelite.client.plugins.microbot.util.walker;

/**
 * Immutable, planner-independent measurements for one route calculation.
 *
 * <p>A value of {@link #UNAVAILABLE} means an engine cannot expose that measurement without
 * changing its search contract. Keeping that distinction explicit prevents the comparison harness
 * from silently treating missing data as zero.</p>
 */
public final class Rs2RouteMetrics
{
	public static final long UNAVAILABLE = -1L;

	private final long searchNanos;
	private final long pathCost;
	private final long nodesChecked;
	private final long transportsChecked;
	private final long liveCollisionEdgesChecked;

	Rs2RouteMetrics(
		long searchNanos,
		long pathCost,
		long nodesChecked,
		long transportsChecked)
	{
		this(searchNanos, pathCost, nodesChecked, transportsChecked, UNAVAILABLE);
	}

	Rs2RouteMetrics(
		long searchNanos,
		long pathCost,
		long nodesChecked,
		long transportsChecked,
		long liveCollisionEdgesChecked)
	{
		validateOptionalMetric("searchNanos", searchNanos);
		validateOptionalMetric("pathCost", pathCost);
		validateOptionalMetric("nodesChecked", nodesChecked);
		validateOptionalMetric("transportsChecked", transportsChecked);
		validateOptionalMetric("liveCollisionEdgesChecked", liveCollisionEdgesChecked);
		this.searchNanos = searchNanos;
		this.pathCost = pathCost;
		this.nodesChecked = nodesChecked;
		this.transportsChecked = transportsChecked;
		this.liveCollisionEdgesChecked = liveCollisionEdgesChecked;
	}

	private static void validateOptionalMetric(String name, long value)
	{
		if (value < 0 && value != UNAVAILABLE)
		{
			throw new IllegalArgumentException(name + " must be non-negative or UNAVAILABLE");
		}
	}

	public long getSearchNanos()
	{
		return searchNanos;
	}

	public boolean hasSearchNanos()
	{
		return searchNanos != UNAVAILABLE;
	}

	public long getPathCost()
	{
		return pathCost;
	}

	public boolean hasPathCost()
	{
		return pathCost != UNAVAILABLE;
	}

	public long getNodesChecked()
	{
		return nodesChecked;
	}

	public boolean hasNodesChecked()
	{
		return nodesChecked != UNAVAILABLE;
	}

	public long getTransportsChecked()
	{
		return transportsChecked;
	}

	public boolean hasTransportsChecked()
	{
		return transportsChecked != UNAVAILABLE;
	}

	public long getLiveCollisionEdgesChecked()
	{
		return liveCollisionEdgesChecked;
	}

	public boolean hasLiveCollisionEdgesChecked()
	{
		return liveCollisionEdgesChecked != UNAVAILABLE;
	}
}
