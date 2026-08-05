package net.runelite.client.plugins.microbot.agentserver.handler;

import com.google.gson.Gson;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WalkerShadowHandlerTest
{
	@Test
	public void snapshotIsCoordinateFreeAndContainsCoverageOutcomes()
	{
		Map<String, Object> snapshot = WalkerShadowHandler.snapshot();
		String json = new Gson().toJson(snapshot);

		assertTrue(snapshot.containsKey("enabled"));
		assertTrue(snapshot.containsKey("plannerMode"));
		assertTrue(snapshot.get("schemaVersion").equals(2));
		assertTrue(snapshot.get("candidateEngineId").toString()
			.startsWith("shortest-path-upstream@"));
		assertTrue(snapshot.containsKey("totals"));
		@SuppressWarnings("unchecked")
		Map<String, Object> totals = (Map<String, Object>) snapshot.get("totals");
		assertTrue(totals.containsKey("upstreamCanarySelections"));
		assertTrue(totals.containsKey("localFallbackDivergences"));
		assertTrue(totals.containsKey("localFallbackFailures"));
		assertTrue(snapshot.containsKey("coverage"));
		assertTrue(snapshot.containsKey("transportExecutors"));
		assertTrue(snapshot.containsKey("transportTypes"));
		assertTrue(snapshot.containsKey("execution"));
		assertTrue(snapshot.containsKey("canaryPerformance"));
		@SuppressWarnings("unchecked")
		Map<String, Object> canaryPerformance =
			(Map<String, Object>) snapshot.get("canaryPerformance");
		assertTrue(canaryPerformance.containsKey("planningSamples"));
		assertTrue(canaryPerformance.containsKey("planningNanosTotal"));
		assertTrue(canaryPerformance.containsKey("planningNanosMax"));
		assertTrue(canaryPerformance.containsKey("localSearchNanosTotal"));
		assertTrue(canaryPerformance.containsKey("upstreamSearchSamples"));
		assertTrue(canaryPerformance.containsKey("upstreamSearchNanosTotal"));
		assertTrue(snapshot.containsKey("latest"));
		assertTrue(snapshot.containsKey("latestRouteShapeDifference"));
		assertTrue(snapshot.containsKey("latestDivergence"));
		assertTrue(snapshot.containsKey("latestFailure"));
		assertTrue(json.contains("SURFACE_COORDINATES_ONLY"));
		assertTrue(json.contains("UNDERGROUND_COORDINATES"));
		assertTrue(json.contains("RECOVERY_REPLAN"));
		assertTrue(json.contains("LIVE_COLLISION_CONSULTED"));
		assertTrue(json.contains("TERMINAL_TRAVEL"));
		assertTrue(json.contains("recoveryArrived"));
		assertFalse(json.contains("\"start\""));
		assertFalse(json.contains("\"target\""));
		assertFalse(json.contains("\"path\""));
	}
}
