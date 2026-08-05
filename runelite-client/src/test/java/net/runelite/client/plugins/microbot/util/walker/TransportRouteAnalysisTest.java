package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TransportRouteAnalysisTest
{
	@Test
	public void exactStepsRetainSelectedTransportIdentityAndOrder()
	{
		WorldPoint start = new WorldPoint(3200, 3200, 0);
		WorldPoint directMiddle = new WorldPoint(3201, 3200, 0);
		WorldPoint target = new WorldPoint(3202, 3200, 0);
		WorldPoint bank = new WorldPoint(3199, 3200, 0);
		WorldPoint landing = new WorldPoint(3000, 3000, 0);
		Rs2TransportEdge directEdge = edge(directMiddle, target, "direct-selected");
		Rs2TransportEdge bankEdge = edge(null, landing, "bank-selected");

		List<WorldPoint> directPath = new ArrayList<>(List.of(start, directMiddle, target));
		List<Rs2RouteStep> directSteps = new ArrayList<>(List.of(
			Rs2RouteStep.walk(start, directMiddle),
			Rs2RouteStep.transport(directMiddle, target, directEdge)));
		TransportRouteAnalysis analysis = new TransportRouteAnalysis(
			directPath,
			null,
			bank,
			List.of(start, bank),
			List.of(bank, landing, target),
			"test",
			2,
			3,
			directSteps,
			List.of(Rs2RouteStep.walk(start, bank)),
			List.of(
				Rs2RouteStep.transport(bank, landing, bankEdge),
				Rs2RouteStep.walk(landing, target)));

		directPath.clear();
		directSteps.clear();

		assertTrue(analysis.isDirectRouteStepsExact());
		assertTrue(analysis.isRouteToBankStepsExact());
		assertTrue(analysis.isRouteFromBankStepsExact());
		assertEquals(3, analysis.getDirectPath().size());
		assertSame(directEdge, analysis.getDirectTransportEdges().get(0));
		assertSame(bankEdge, analysis.getTransportEdgesFromBank().get(0));
		assertEquals(List.of(directEdge), analysis.getDirectTransportEdges());
		assertEquals(List.of(bankEdge), analysis.getBankingTransportEdges());

		try
		{
			analysis.getRouteFromBankSteps().add(Rs2RouteStep.walk(landing, target));
			fail("exact route steps must be immutable");
		}
		catch (UnsupportedOperationException expected)
		{
			// Expected.
		}
	}

	@Test
	public void legacyConstructorsDoNotPretendReconstructedEdgesAreExact()
	{
		WorldPoint start = new WorldPoint(3200, 3200, 0);
		WorldPoint target = new WorldPoint(3201, 3200, 0);
		TransportRouteAnalysis analysis = new TransportRouteAnalysis(
			List.of(start, target), null, null, List.of(), List.of(), "legacy");

		assertFalse(analysis.isDirectRouteStepsExact());
		assertFalse(analysis.isRouteToBankStepsExact());
		assertFalse(analysis.isRouteFromBankStepsExact());
		assertTrue(analysis.getDirectTransportEdges().isEmpty());
	}

	@Test(expected = IllegalArgumentException.class)
	public void exactStepsMustDescribeEveryPathEdge()
	{
		WorldPoint start = new WorldPoint(3200, 3200, 0);
		WorldPoint target = new WorldPoint(3201, 3200, 0);
		new TransportRouteAnalysis(
			List.of(start, target), null, null, List.of(), List.of(), "invalid", 1, -1,
			List.of(), List.of(), List.of());
	}

	private static Rs2TransportEdge edge(WorldPoint origin, WorldPoint destination, String displayInfo)
	{
		return new Rs2TransportEdge(
			origin,
			destination,
			Rs2TransportType.TELEPORTATION_ITEM,
			Rs2TransportExecutor.ITEM_TELEPORT,
			Rs2TerminalTravelMode.UNSUPPORTED,
			displayInfo,
			"Teleport",
			"test item",
			-1,
			1,
			true,
			false,
			false,
			0,
			"",
			0,
			List.of());
	}
}
