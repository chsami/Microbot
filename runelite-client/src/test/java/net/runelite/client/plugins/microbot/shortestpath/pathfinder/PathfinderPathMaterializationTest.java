package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class PathfinderPathMaterializationTest
{
	@Test
	public void completedRoutePreservesExactTransportAndMetrics()
	{
		WorldPoint start = new WorldPoint(3222, 3218, 0);
		WorldPoint destination = new WorldPoint(3222, 9618, 0);
		Transport exact = new Transport(
			start, destination, "synthetic", TransportType.TRANSPORT, false,
			"Climb-down", "Tunnel", 1001, 3);
		PathfinderConfig config = new PathfinderConfig(
			SplitFlagMap.fromResources(), new HashMap<>(), Collections.emptyList(), null, null);

		Pathfinder completed = Pathfinder.completedRoute(
			config,
			start,
			Set.of(destination),
			List.of(start, destination),
			List.of(exact),
			PathTerminationReason.TARGET_REACHED,
			3L,
			1234L,
			7L,
			2L,
			5L);

		assertTrue(completed.isDone());
		assertEquals(List.of(start, destination), completed.getPath());
		assertEquals(1, completed.getPathEdges().size());
		assertSame(exact, completed.getPathEdges().get(0).getTransport());
		assertEquals(PathTerminationReason.TARGET_REACHED, completed.getTerminationReason());
		assertEquals(3L, completed.getSelectedPathCost());
		assertEquals(1234L, completed.getStats().getElapsedTimeNanos());
		assertEquals(7, completed.getStats().getNodesChecked());
		assertEquals(2, completed.getStats().getTransportsChecked());
		assertEquals(5L, completed.getStats().getLiveCollisionEdgesChecked());
	}

    @Test
    public void newerBestNodeRematerializesAfterAnEarlierLiveRead() throws Exception
    {
        int start = WorldPointUtil.packWorldPoint(new WorldPoint(3000, 3200, 0));
        Pathfinder pathfinder = new Pathfinder(
                mock(PathfinderConfig.class), start, Collections.singleton(start));

        Node first = new Node(new WorldPoint(3002, 3200, 0),
                new Node(new WorldPoint(3001, 3200, 0),
                        new Node(new WorldPoint(3000, 3200, 0), null)));
        setBestLastNode(pathfinder, first);
        markLegacyPathDirtyIfPresent(pathfinder);
        assertEquals(3, pathfinder.getPath().size());

        Node latest = new Node(new WorldPoint(3005, 3200, 0),
                new Node(new WorldPoint(3004, 3200, 0),
                        new Node(new WorldPoint(3003, 3200, 0), first)));
        setBestLastNode(pathfinder, latest);

        List<WorldPoint> latestPath = pathfinder.getPath();
        assertEquals("a live reader must not leave the completed route on an older node",
                6, latestPath.size());
        assertEquals("typed edges and path points must describe the same node chain",
                latestPath.size() - 1, pathfinder.getPathEdges().size());
    }

    private static void setBestLastNode(Pathfinder pathfinder, Node node) throws Exception
    {
        Field field = Pathfinder.class.getDeclaredField("bestLastNode");
        field.setAccessible(true);
        field.set(pathfinder, node);
    }

    /**
     * Models the old dirty-flag implementation so this regression would fail before identity invalidation.
     */
    private static void markLegacyPathDirtyIfPresent(Pathfinder pathfinder) throws Exception
    {
        try
        {
            Field field = Pathfinder.class.getDeclaredField("pathNeedsUpdate");
            field.setAccessible(true);
            field.setBoolean(pathfinder, true);
        }
        catch (NoSuchFieldException ignored)
        {
            // Current implementation invalidates by Node identity and has no dirty flag.
        }
    }
}
