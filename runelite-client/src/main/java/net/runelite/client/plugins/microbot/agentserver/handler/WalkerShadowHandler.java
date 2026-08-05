package net.runelite.client.plugins.microbot.agentserver.handler;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import net.runelite.client.plugins.microbot.util.walker.Rs2PathApi;
import net.runelite.client.plugins.microbot.util.walker.Rs2PlannerShadowComparison;
import net.runelite.client.plugins.microbot.util.walker.Rs2PlannerCanaryPerformanceStats;
import net.runelite.client.plugins.microbot.util.walker.Rs2PlannerShadowContext;
import net.runelite.client.plugins.microbot.util.walker.Rs2PlannerShadowCoverageStats;
import net.runelite.client.plugins.microbot.util.walker.Rs2PlannerShadowStats;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportExecutor;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportType;
import net.runelite.client.plugins.microbot.util.walker.Rs2WalkerShadowExecutionStats;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Read-only, coordinate-free production planner shadow evidence. */
public final class WalkerShadowHandler extends AgentHandler
{
	public WalkerShadowHandler(Gson gson)
	{
		super(gson);
	}

	@Override
	public String getPath()
	{
		return "/walker/shadow";
	}

	@Override
	protected void handleRequest(HttpExchange exchange) throws IOException
	{
		try
		{
			requireGet(exchange);
		}
		catch (HttpMethodException error)
		{
			sendJson(exchange, 405, errorResponse(error.getMessage()));
			return;
		}
		sendJson(exchange, 200, snapshot());
	}

	/** Coordinate-free schema-v2 snapshot shared by the read-only endpoint and live test harnesses. */
	public static Map<String, Object> snapshot()
	{
		Rs2PlannerShadowStats stats = Rs2PathApi.getShadowStats();
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("schemaVersion", 2);
		response.put("enabled", Rs2PathApi.isUpstreamPlannerShadowEnabled());
		response.put("plannerMode", Rs2PathApi.getPlannerSelectionMode().name());
		response.put("candidateEngineId", Rs2PathApi.getUpstreamPlannerEngineId());
		response.put("startedAtEpochMillis", stats.getStartedAtEpochMillis());
		Map<String, Object> totals = new LinkedHashMap<>();
		totals.put("submitted", stats.getSubmitted());
		totals.put("completed", stats.getCompleted());
		totals.put("matches", stats.getMatches());
		totals.put("divergences", stats.getDivergences());
		totals.put("failures", stats.getFailures());
		totals.put("staleResults", stats.getStaleResults());
		totals.put("discarded", stats.getDiscarded());
		totals.put("pending", stats.getPending());
		totals.put("routeShapeDifferences", stats.getRouteShapeDifferences());
		totals.put("upstreamCanarySelections", stats.getUpstreamCanarySelections());
		totals.put("localFallbackDivergences", stats.getLocalFallbackDivergences());
		totals.put("localFallbackFailures", stats.getLocalFallbackFailures());
		response.put("totals", totals);

		Map<String, Object> coverage = new LinkedHashMap<>();
		for (Map.Entry<Rs2PlannerShadowContext.Coverage, Rs2PlannerShadowCoverageStats>
			entry : stats.getCoverage().entrySet())
		{
			Rs2PlannerShadowCoverageStats value = entry.getValue();
			Map<String, Long> outcomes = new LinkedHashMap<>();
			outcomes.put("completed", value.getCompleted());
			outcomes.put("matches", value.getMatches());
			outcomes.put("divergences", value.getDivergences());
			outcomes.put("failures", value.getFailures());
			coverage.put(entry.getKey().name(), outcomes);
		}
		response.put("coverage", coverage);
		Map<String, Object> transportExecutors = new LinkedHashMap<>();
		for (Map.Entry<Rs2TransportExecutor, Rs2PlannerShadowCoverageStats>
			entry : stats.getTransportExecutors().entrySet())
		{
			transportExecutors.put(entry.getKey().name(), outcomes(entry.getValue()));
		}
		response.put("transportExecutors", transportExecutors);
		Map<String, Object> transportTypes = new LinkedHashMap<>();
		for (Map.Entry<Rs2TransportType, Rs2PlannerShadowCoverageStats>
			entry : stats.getTransportTypes().entrySet())
		{
			transportTypes.put(entry.getKey().name(), outcomes(entry.getValue()));
		}
		response.put("transportTypes", transportTypes);
		Rs2WalkerShadowExecutionStats executionStats = stats.getExecution();
		Map<String, Long> execution = new LinkedHashMap<>();
		execution.put("terminal", executionStats.getTerminal());
		execution.put("arrived", executionStats.getArrived());
		execution.put("unreachable", executionStats.getUnreachable());
		execution.put("exited", executionStats.getExited());
		execution.put("recoveryTerminal", executionStats.getRecoveryTerminal());
		execution.put("recoveryArrived", executionStats.getRecoveryArrived());
		execution.put("recoveryUnreachable", executionStats.getRecoveryUnreachable());
		execution.put("recoveryExited", executionStats.getRecoveryExited());
		response.put("execution", execution);
		Rs2PlannerCanaryPerformanceStats performanceStats = stats.getCanaryPerformance();
		Map<String, Long> canaryPerformance = new LinkedHashMap<>();
		canaryPerformance.put("planningSamples", performanceStats.getPlanningSamples());
		canaryPerformance.put("planningNanosTotal", performanceStats.getPlanningNanosTotal());
		canaryPerformance.put("planningNanosMax", performanceStats.getPlanningNanosMax());
		canaryPerformance.put("localSearchNanosTotal", performanceStats.getLocalSearchNanosTotal());
		canaryPerformance.put("localSearchNanosMax", performanceStats.getLocalSearchNanosMax());
		canaryPerformance.put("upstreamSearchSamples", performanceStats.getUpstreamSearchSamples());
		canaryPerformance.put("upstreamSearchNanosTotal", performanceStats.getUpstreamSearchNanosTotal());
		canaryPerformance.put("upstreamSearchNanosMax", performanceStats.getUpstreamSearchNanosMax());
		response.put("canaryPerformance", canaryPerformance);
		response.put("latest", Rs2PathApi.getLastShadowComparison()
			.<Object>map(WalkerShadowHandler::comparison).orElse(null));
		response.put("latestRouteShapeDifference", Rs2PathApi.getLastRouteShapeDifference()
			.<Object>map(WalkerShadowHandler::comparison).orElse(null));
		response.put("latestDivergence", Rs2PathApi.getLastDivergence()
			.<Object>map(WalkerShadowHandler::comparison).orElse(null));
		response.put("latestFailure", Rs2PathApi.getLastPlannerFailure()
			.<Object>map(WalkerShadowHandler::comparison).orElse(null));
		return response;
	}

	private static Map<String, Long> outcomes(Rs2PlannerShadowCoverageStats value)
	{
		Map<String, Long> outcomes = new LinkedHashMap<>();
		outcomes.put("completed", value.getCompleted());
		outcomes.put("matches", value.getMatches());
		outcomes.put("divergences", value.getDivergences());
		outcomes.put("failures", value.getFailures());
		return outcomes;
	}

	private static Map<String, Object> comparison(Rs2PlannerShadowComparison comparison)
	{
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("status", comparison.getStatus().name());
		result.put("shadowEngineId", comparison.getShadowEngineId());
		result.put("invocation", comparison.getContext().getInvocation().name());
		ArrayList<String> coverage = new ArrayList<>();
		for (Rs2PlannerShadowContext.Coverage value : comparison.getContext().getCoverage())
		{
			coverage.add(value.name());
		}
		result.put("coverage", coverage);
		result.put("terminationMatches", comparison.isTerminationMatches());
		result.put("endpointMatches", comparison.isEndpointMatches());
		result.put("costComparable", comparison.isCostComparable());
		result.put("costMatches", comparison.isCostMatches());
		result.put("selectedTransportsMatch", comparison.isSelectedTransportsMatch());
		result.put("pathMatches", comparison.isPathMatches());
		ArrayList<String> transportExecutors = new ArrayList<>();
		for (Rs2TransportExecutor value : comparison.getContext().getTransportExecutors())
		{
			transportExecutors.add(value.name());
		}
		result.put("transportExecutors", transportExecutors);
		ArrayList<String> transportTypes = new ArrayList<>();
		for (Rs2TransportType value : comparison.getContext().getTransportTypes())
		{
			transportTypes.add(value.name());
		}
		result.put("transportTypes", transportTypes);
		result.put("localSearchNanos", comparison.getLocalSearchNanos());
		result.put("shadowSearchNanos", comparison.getShadowSearchNanos());
		result.put("failureType", comparison.getFailureType());
		return result;
	}
}
