package net.runelite.client.plugins.microbot.util.walker;

/** Immutable summary of the latest completed production shadow comparison. */
public final class Rs2PlannerShadowComparison
{
	public enum Status
	{
		MATCH,
		DIVERGENCE,
		FAILED
	}

	private final Status status;
	private final String shadowEngineId;
	private final Rs2PlannerShadowContext context;
	private final boolean terminationMatches;
	private final boolean endpointMatches;
	private final boolean costComparable;
	private final boolean costMatches;
	private final boolean selectedTransportsMatch;
	private final boolean pathMatches;
	private final long shadowSearchNanos;
	private final long localSearchNanos;
	private final String failureType;

	private Rs2PlannerShadowComparison(
		Status status,
		String shadowEngineId,
		Rs2PlannerShadowContext context,
		boolean terminationMatches,
		boolean endpointMatches,
		boolean costComparable,
		boolean costMatches,
		boolean selectedTransportsMatch,
		boolean pathMatches,
		long shadowSearchNanos,
		long localSearchNanos,
		String failureType)
	{
		this.status = status;
		this.shadowEngineId = shadowEngineId;
		this.context = context;
		this.terminationMatches = terminationMatches;
		this.endpointMatches = endpointMatches;
		this.costComparable = costComparable;
		this.costMatches = costMatches;
		this.selectedTransportsMatch = selectedTransportsMatch;
		this.pathMatches = pathMatches;
		this.shadowSearchNanos = shadowSearchNanos;
		this.localSearchNanos = localSearchNanos;
		this.failureType = failureType;
	}

	static Rs2PlannerShadowComparison compare(
		String engineId,
		Rs2PlannerShadowContext context,
		Rs2RouteResult local,
		Rs2RouteResult shadow)
	{
		boolean termination = local.getTerminationReason() == shadow.getTerminationReason();
		boolean endpoint = local.getEndpoint().equals(shadow.getEndpoint());
		boolean comparable = local.getMetrics().hasPathCost() && shadow.getMetrics().hasPathCost();
		boolean cost = comparable
			&& local.getMetrics().getPathCost() == shadow.getMetrics().getPathCost();
		boolean transports = sameSelectedTransports(local, shadow);
		boolean path = local.getPath().equals(shadow.getPath());
		Status status = termination && endpoint && comparable && cost && transports
			? Status.MATCH : Status.DIVERGENCE;
		return new Rs2PlannerShadowComparison(
			status, engineId, context, termination, endpoint, comparable, cost, transports, path,
			shadow.getMetrics().getSearchNanos(), local.getMetrics().getSearchNanos(), null);
	}

	static Rs2PlannerShadowComparison failed(
		String engineId, Rs2PlannerShadowContext context, Rs2RouteResult local,
		RuntimeException failure)
	{
		return new Rs2PlannerShadowComparison(
			Status.FAILED, engineId, context, false, false, false, false, false, false,
			Rs2RouteMetrics.UNAVAILABLE, local.getMetrics().getSearchNanos(),
			failure.getClass().getSimpleName());
	}

	private static boolean sameSelectedTransports(Rs2RouteResult local, Rs2RouteResult shadow)
	{
		java.util.List<Rs2RouteStep> localSteps = local.getTransportSteps();
		java.util.List<Rs2RouteStep> shadowSteps = shadow.getTransportSteps();
		if (localSteps.size() != shadowSteps.size())
		{
			return false;
		}
		for (int i = 0; i < localSteps.size(); i++)
		{
			Rs2TransportEdge localEdge = localSteps.get(i).getTransport().orElseThrow(
				IllegalStateException::new);
			Rs2TransportEdge shadowEdge = shadowSteps.get(i).getTransport().orElseThrow(
				IllegalStateException::new);
			if (localEdge.getSourceIdentity() != shadowEdge.getSourceIdentity())
			{
				return false;
			}
		}
		return true;
	}

	public Status getStatus() { return status; }
	public String getShadowEngineId() { return shadowEngineId; }
	public Rs2PlannerShadowContext getContext() { return context; }
	public boolean isTerminationMatches() { return terminationMatches; }
	public boolean isEndpointMatches() { return endpointMatches; }
	public boolean isCostComparable() { return costComparable; }
	public boolean isCostMatches() { return costMatches; }
	public boolean isSelectedTransportsMatch() { return selectedTransportsMatch; }
	public boolean isPathMatches() { return pathMatches; }
	public long getShadowSearchNanos() { return shadowSearchNanos; }
	public long getLocalSearchNanos() { return localSearchNanos; }
	public String getFailureType() { return failureType; }
}
