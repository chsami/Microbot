package net.runelite.client.plugins.microbot.agentserver.handler;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.CollisionMap;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.live.LiveCollisionSnapshot;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.live.LiveEdgeSource;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TileHandlerTest {

	private static final int X = 3200;
	private static final int Y = 3200;

	@Test
	@SuppressWarnings("unchecked")
	public void liveEdgeOverridesStaticAndIsReportedAsConflict() {
		CollisionMap map = mock(CollisionMap.class);
		when(map.staticEdge(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(true);
		when(map.hasRegion(X, Y)).thenReturn(true);
		LiveEdgeSource live = (x, y, z, flag) -> x == X && y == Y && flag == LiveCollisionSnapshot.FLAG_NORTH ? Boolean.FALSE : null;

		Map<String, Object> result = TileHandler.describe(new WorldPoint(X, Y, 0), map, live);

		Map<String, Object> north = (Map<String, Object>) ((Map<String, Object>) result.get("canMove")).get("north");
		assertEquals(true, north.get("static"));
		assertEquals(false, north.get("live"));
		assertEquals(false, north.get("effective"));
		assertEquals(List.of("north"), result.get("conflicts"));
		assertEquals(false, result.get("blocked"));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void southAndWestReadTheNeighbouringTilesEdges() {
		CollisionMap map = mock(CollisionMap.class);
		when(map.staticEdge(X, Y - 1, 0, LiveCollisionSnapshot.FLAG_NORTH)).thenReturn(true);
		when(map.staticEdge(X - 1, Y, 0, LiveCollisionSnapshot.FLAG_EAST)).thenReturn(true);

		Map<String, Object> canMove = (Map<String, Object>) TileHandler.describe(new WorldPoint(X, Y, 0), map, null).get("canMove");

		assertEquals(true, ((Map<String, Object>) canMove.get("south")).get("effective"));
		assertEquals(true, ((Map<String, Object>) canMove.get("west")).get("effective"));
		assertEquals(false, ((Map<String, Object>) canMove.get("north")).get("effective"));
	}

	@Test
	public void tileWithNoOpenEdgeIsBlocked() {
		CollisionMap map = mock(CollisionMap.class);
		Map<String, Object> result = TileHandler.describe(new WorldPoint(X, Y, 0), map, null);
		assertEquals(true, result.get("blocked"));
		assertEquals(false, result.get("regionMapped"));
	}
}
