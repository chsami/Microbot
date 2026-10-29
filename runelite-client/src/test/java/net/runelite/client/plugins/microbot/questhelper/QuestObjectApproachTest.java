package net.runelite.client.plugins.microbot.questhelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

/**
 * The object's own tile is never walkable, and the closest walkable tile can be across a wall. The approach tile must be
 * one the object can be used from and the planner (which opens closed doors and gates on its route) can reach.
 * Biohazard supplied four shapes of this: cupboard in a house, staircase behind a door, crate in a caged area behind a
 * gate, and the way back out through a gate and a door.
 */
public class QuestObjectApproachTest
{
	private static WorldPoint at(int x, int y, int plane)
	{
		return new WorldPoint(x, y, plane);
	}

	private static WorldPoint choose(WorldPoint object, Set<WorldPoint> acrossWall, Map<WorldPoint, Integer> routes)
	{
		List<WorldPoint> candidates = Arrays.asList(routes.keySet().toArray(new WorldPoint[0]));
		return QuestObjectApproach.choose(object, candidates, tile -> !acrossWall.contains(tile),
			tile -> routes.getOrDefault(tile, QuestObjectApproach.UNREACHABLE));
	}

	/** #82: cupboard 2517,3276 inside Sarah's house; the nearest walkable tile outside is across the wall. */
	@Test
	public void cupboardInAHouseIsApproachedFromInsideNotAcrossTheWall()
	{
		WorldPoint cupboard = at(2517, 3276, 0);
		WorldPoint outside = at(2516, 3277, 0);
		WorldPoint inside = at(2517, 3275, 0);
		Map<WorldPoint, Integer> routes = new HashMap<>();
		routes.put(outside, 4);
		routes.put(inside, 12); // through the house door at 2519,3275
		assertEquals(inside, choose(cupboard, Collections.singleton(outside), routes));
	}

	/** #83: staircase 2542,3324 in the west room, closed door 2546,3325 between it and the player. */
	@Test
	public void staircaseBehindAClosedDoorIsApproachedFromItsOwnRoom()
	{
		WorldPoint staircase = at(2542, 3324, 0);
		WorldPoint eastSide = at(2541, 3324, 0);
		WorldPoint westRoom = at(2543, 3324, 0);
		Map<WorldPoint, Integer> routes = new HashMap<>();
		routes.put(eastSide, 3);
		routes.put(westRoom, 9);
		assertEquals(westRoom, choose(staircase, Collections.singleton(eastSide), routes));
	}

	/** #84: crate 2554,3327 upstairs in a cage behind gate 2058 at 2551,3326. */
	@Test
	public void crateInACagedAreaIsApproachedThroughTheGate()
	{
		WorldPoint crate = at(2554, 3327, 1);
		WorldPoint outsideCage = at(2555, 3327, 1);
		WorldPoint inCage = at(2553, 3326, 1);
		Map<WorldPoint, Integer> routes = new HashMap<>();
		routes.put(outsideCage, 5);
		routes.put(inCage, 7);
		assertEquals(inCage, choose(crate, Collections.singleton(outsideCage), routes));
	}

	/** #85: from inside the cage out to the staircase: a gate that closed behind the player, then a door. */
	@Test
	public void routeThroughAChainOfClosedGateAndDoorIsAccepted()
	{
		WorldPoint staircase = at(2543, 3325, 1);
		WorldPoint nextToStairs = at(2544, 3325, 1);
		Map<WorldPoint, Integer> routes = new HashMap<>();
		routes.put(nextToStairs, 21); // gate 2058, door 2034, then the corridor
		assertEquals(nextToStairs, choose(staircase, Collections.emptySet(), routes));
	}

	/**
	 * #84 again, with the collision of the cage: bars let you see the crate but block the edge, so the tile outside the
	 * cage touches the crate's neighbour, not the crate. A reachable tile on the wrong side must lose to the one inside.
	 */
	@Test
	public void cageBarsMakeTheOutsideTileUnusableEvenThoughItIsCloserAndSeesTheCrate()
	{
		WorldPoint crate = at(2554, 3327, 1);
		WorldPoint outsideCage = at(2555, 3327, 1);
		WorldPoint inCage = at(2553, 3327, 1);
		Map<Long, Integer> flags = new HashMap<>();
		// bars on the east edge of the crate tile and the matching west edge of the outside tile
		flags.put(key(crate), CollisionDataFlag.BLOCK_MOVEMENT_EAST);
		flags.put(key(outsideCage), CollisionDataFlag.BLOCK_MOVEMENT_WEST);
		QuestObjectApproach.CollisionFlags collision = (x, y) -> flags.getOrDefault(key(x, y), 0);
		List<WorldPoint> footprint = Collections.singletonList(crate);

		assertFalse(QuestObjectApproach.touchesWithOpenEdge(outsideCage, footprint, collision));
		assertTrue(QuestObjectApproach.touchesWithOpenEdge(inCage, footprint, collision));

		Map<WorldPoint, Integer> routes = new HashMap<>();
		routes.put(outsideCage, 3); // reachable now, but on the wrong side of the bars
		routes.put(inCage, 9); // through the gate
		WorldPoint chosen = QuestObjectApproach.choose(crate, Arrays.asList(outsideCage, inCage),
			tile -> QuestObjectApproach.touchesWithOpenEdge(tile, footprint, collision),
			tile -> routes.getOrDefault(tile, QuestObjectApproach.UNREACHABLE));
		assertEquals(inCage, chosen);
	}

	/** Staircases cover several tiles: the north and east sides are usable even though the south-west tile is not there. */
	@Test
	public void aMultiTileObjectCanBeUsedFromAnyOfItsSides()
	{
		WorldPoint southWest = at(2543, 3325, 1);
		List<WorldPoint> footprint = Arrays.asList(southWest, at(2544, 3325, 1), at(2543, 3326, 1), at(2544, 3326, 1));
		QuestObjectApproach.CollisionFlags open = (x, y) -> 0;
		WorldPoint east = at(2545, 3326, 1);
		WorldPoint north = at(2544, 3327, 1);
		assertTrue(QuestObjectApproach.touchesWithOpenEdge(east, footprint, open));
		assertTrue(QuestObjectApproach.touchesWithOpenEdge(north, footprint, open));
		assertFalse("not from the south-west tile alone",
			QuestObjectApproach.touchesWithOpenEdge(east, Collections.singletonList(southWest), open));
	}

	@Test
	public void onlyCardinalNeighboursOnTheSameFloorTouch()
	{
		WorldPoint object = at(100, 100, 0);
		QuestObjectApproach.CollisionFlags open = (x, y) -> 0;
		List<WorldPoint> footprint = Collections.singletonList(object);
		assertTrue(QuestObjectApproach.touchesWithOpenEdge(at(101, 100, 0), footprint, open));
		assertFalse(QuestObjectApproach.touchesWithOpenEdge(at(101, 101, 0), footprint, open));
		assertFalse(QuestObjectApproach.touchesWithOpenEdge(at(102, 100, 0), footprint, open));
		assertFalse(QuestObjectApproach.touchesWithOpenEdge(at(101, 100, 1), footprint, open));
	}

	private static long key(WorldPoint point)
	{
		return key(point.getX(), point.getY());
	}

	private static long key(int x, int y)
	{
		return ((long) x << 32) | y;
	}

	@Test
	public void shortestRouteWinsAndTiesPreferTheTileNearestTheObject()
	{
		WorldPoint object = at(100, 100, 0);
		WorldPoint near = at(101, 100, 0);
		WorldPoint far = at(103, 100, 0);
		Map<WorldPoint, Integer> routes = new HashMap<>();
		routes.put(far, 6);
		routes.put(near, 6);
		assertEquals(near, choose(object, Collections.emptySet(), routes));
		routes.put(far, 4);
		assertEquals(far, choose(object, Collections.emptySet(), routes));
	}

	@Test
	public void nothingUsableOrReachableGivesNoTile()
	{
		WorldPoint object = at(100, 100, 0);
		WorldPoint blockedSide = at(101, 100, 0);
		WorldPoint sealed = at(99, 100, 0);
		Map<WorldPoint, Integer> routes = new HashMap<>();
		routes.put(blockedSide, 3);
		routes.put(sealed, QuestObjectApproach.UNREACHABLE);
		assertNull(choose(object, Collections.singleton(blockedSide), routes));
		assertNull(choose(object, Collections.emptySet(), Collections.emptyMap()));
	}

	/** Local points are in scene space, so the check holds whatever world coordinates an instance reports. */
	@Test
	public void aTargetFiftyTilesAwayIsWalkedToNotClicked()
	{
		net.runelite.api.coords.LocalPoint me = new net.runelite.api.coords.LocalPoint(20 * 128 + 64, 20 * 128 + 64);
		assertFalse(QuestObjectApproach.withinClickRange(me, new net.runelite.api.coords.LocalPoint(70 * 128, 20 * 128), 0, 0, 20));
		assertTrue(QuestObjectApproach.withinClickRange(me, new net.runelite.api.coords.LocalPoint(30 * 128, 25 * 128), 0, 0, 20));
		assertFalse("another plane", QuestObjectApproach.withinClickRange(me, new net.runelite.api.coords.LocalPoint(30 * 128, 25 * 128), 0, 1, 20));
		assertFalse(QuestObjectApproach.withinClickRange(null, me, 0, 0, 20));
	}
}
