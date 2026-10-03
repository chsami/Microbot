package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RouteObjectRangePolicyTest
{
	private static final WorldPoint ORIGIN = new WorldPoint(3200, 3200, 0);

	@Test
	public void standardObjectTransportsMayDispatchAtRange()
	{
		assertTrue(RouteObjectRangePolicy.isRangedObjectDispatchEligible(edge(
			Rs2TransportType.TRANSPORT, Rs2TransportExecutor.OBJECT,
			new WorldPoint(3200, 3200, 1), "Climb-up", "Ladder")));
		assertTrue(RouteObjectRangePolicy.isRangedObjectDispatchEligible(edge(
			Rs2TransportType.TRANSPORT, Rs2TransportExecutor.OBJECT,
			new WorldPoint(3200, 3200, 1), "Climb", "Staircase")));
		assertTrue(RouteObjectRangePolicy.isRangedObjectDispatchEligible(edge(
			Rs2TransportType.TRANSPORT, Rs2TransportExecutor.OBJECT,
			new WorldPoint(3201, 3200, 0), "Open", "Door")));
	}

	@Test
	public void shortFenceAndRailingShortcutsMayDispatchAtRange()
	{
		assertTrue(RouteObjectRangePolicy.isRangedObjectDispatchEligible(edge(
			Rs2TransportType.AGILITY_SHORTCUT, Rs2TransportExecutor.OBJECT,
			new WorldPoint(3201, 3200, 0), "Squeeze-through", "Loose Railing")));
		assertTrue(RouteObjectRangePolicy.isRangedObjectDispatchEligible(edge(
			Rs2TransportType.AGILITY_SHORTCUT, Rs2TransportExecutor.OBJECT,
			new WorldPoint(3203, 3200, 0), "Climb-over", "Broken fence")));
		assertTrue(RouteObjectRangePolicy.isRangedObjectDispatchEligible(edge(
			Rs2TransportType.AGILITY_SHORTCUT, Rs2TransportExecutor.OBJECT,
			new WorldPoint(3202, 3200, 0), "Jump-over", "LOW FENCE")));
		assertTrue(RouteObjectRangePolicy.isRangedObjectDispatchEligible(edge(
			Rs2TransportType.AGILITY_SHORTCUT, Rs2TransportExecutor.OBJECT,
			new WorldPoint(3203, 3200, 0), "Jump", "Broken Fence")));
	}

	@Test
	public void agilityShortcutsMustBeShortSamePlaneObjectHops()
	{
		assertFalse(RouteObjectRangePolicy.isEligibleFenceShortcut(
			ORIGIN, null, "Climb-over", "Fence"));
		assertFalse(RouteObjectRangePolicy.isRangedObjectDispatchEligible(edge(
			Rs2TransportType.AGILITY_SHORTCUT, Rs2TransportExecutor.OBJECT,
			new WorldPoint(3204, 3200, 0), "Climb-over", "Fence")));
		assertFalse(RouteObjectRangePolicy.isRangedObjectDispatchEligible(edge(
			Rs2TransportType.AGILITY_SHORTCUT, Rs2TransportExecutor.OBJECT,
			new WorldPoint(3201, 3200, 1), "Climb-over", "Fence")));
		assertFalse(RouteObjectRangePolicy.isRangedObjectDispatchEligible(edge(
			Rs2TransportType.AGILITY_SHORTCUT, Rs2TransportExecutor.OBJECT,
			ORIGIN, "Climb-over", "Fence")));
	}

	@Test
	public void steppingStonesAndGenericActionsStayOnOrigin()
	{
		assertFalse(RouteObjectRangePolicy.isRangedObjectDispatchEligible(edge(
			Rs2TransportType.AGILITY_SHORTCUT, Rs2TransportExecutor.OBJECT,
			new WorldPoint(3201, 3200, 0), "Cross", "Stepping stone")));
		assertFalse(RouteObjectRangePolicy.isRangedObjectDispatchEligible(edge(
			Rs2TransportType.AGILITY_SHORTCUT, Rs2TransportExecutor.OBJECT,
			new WorldPoint(3201, 3200, 0), "Jump", "Fence")));
		assertFalse(RouteObjectRangePolicy.isRangedObjectDispatchEligible(edge(
			Rs2TransportType.AGILITY_SHORTCUT, Rs2TransportExecutor.OBJECT,
			new WorldPoint(3201, 3200, 0), "Cross", "Railing")));
		assertFalse(RouteObjectRangePolicy.isRangedObjectDispatchEligible(edge(
			Rs2TransportType.AGILITY_SHORTCUT, Rs2TransportExecutor.OBJECT,
			new WorldPoint(3201, 3200, 0), "Climb-over", "Fenceline")));
	}

	@Test
	public void requiresExactObjectExecutorAndEdge()
	{
		assertFalse(RouteObjectRangePolicy.isRangedObjectDispatchEligible(null));
		assertFalse(RouteObjectRangePolicy.isRangedObjectDispatchEligible(edge(
			Rs2TransportType.TRANSPORT, Rs2TransportExecutor.UNSUPPORTED,
			new WorldPoint(3201, 3200, 0), "Open", "Door")));
		assertFalse(RouteObjectRangePolicy.isRangedObjectDispatchEligible(edge(
			Rs2TransportType.BOAT, Rs2TransportExecutor.OBJECT,
			new WorldPoint(3201, 3200, 0), "Climb-over", "Fence")));
	}

	private static Rs2TransportEdge edge(Rs2TransportType type, Rs2TransportExecutor executor,
	                                     WorldPoint destination, String action, String target)
	{
		return new Rs2TransportEdge(
			ORIGIN, destination, type, executor, Rs2TerminalTravelMode.UNSUPPORTED,
			"test", action, target, 1, 1, false, false, true, 0, "", 0, List.of());
	}
}
