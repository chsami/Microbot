package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathConfig;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

public class Rs2WalkerFinalTileTest
{
	private static final WorldPoint TARGET = new WorldPoint(3222, 3218, 0);

	private ShortestPathConfig originalConfig;
	private ShortestPathConfig config;
	private MockedStatic<Rs2Tile> tile;

	@Before
	public void setUp()
	{
		originalConfig = Rs2Walker.config;
		config = mock(ShortestPathConfig.class);
		Rs2Walker.config = config;
		tile = Mockito.mockStatic(Rs2Tile.class);
		tile.when(() -> Rs2Tile.isTileReachable(any(WorldPoint.class))).thenReturn(true);
		tile.when(() -> Rs2Tile.getReachableTilesFromTile(any(WorldPoint.class), anyInt()))
			.thenAnswer(invocation -> reachableAround(invocation.getArgument(0), invocation.getArgument(1)));
	}

	@After
	public void tearDown()
	{
		tile.close();
		Rs2Walker.config = originalConfig;
	}

	@Test
	public void randomizeOnSpreadsFinishAroundGoalUpToThreeTiles()
	{
		when(config.randomizeFinalTile()).thenReturn(true);

		Set<WorldPoint> zone = Rs2Walker.randomizedFinishZone(TARGET, TARGET, 5);

		assertEquals(49, zone.size());
		assertTrue(zone.contains(TARGET.dx(3).dy(-3)));
		tile.verify(() -> Rs2Tile.getReachableTilesFromTile(eq(TARGET), eq(3)));
	}

	@Test
	public void randomizeOnHonoursSmallerFinishDistance()
	{
		when(config.randomizeFinalTile()).thenReturn(true);

		assertEquals(9, Rs2Walker.randomizedFinishZone(TARGET, TARGET, 1).size());
		assertEquals(Collections.singleton(TARGET), Rs2Walker.randomizedFinishZone(TARGET, TARGET, 0));
	}

	@Test
	public void randomizeOffKeepsExactGoal()
	{
		when(config.randomizeFinalTile()).thenReturn(false);

		assertTrue(Rs2Walker.randomizedFinishZone(TARGET, TARGET, 5).isEmpty());
		tile.verify(() -> Rs2Tile.getReachableTilesFromTile(any(WorldPoint.class), anyInt()), never());
	}

	@Test
	public void noZoneWhenGoalUnreachableOrPathEndsFarAway()
	{
		when(config.randomizeFinalTile()).thenReturn(true);

		assertTrue(Rs2Walker.randomizedFinishZone(TARGET, TARGET.dx(5), 5).isEmpty());
		assertTrue(Rs2Walker.randomizedFinishZone(TARGET, new WorldPoint(3222, 3218, 1), 5).isEmpty());

		tile.when(() -> Rs2Tile.isTileReachable(any(WorldPoint.class))).thenReturn(false);
		assertTrue(Rs2Walker.randomizedFinishZone(TARGET, TARGET, 5).isEmpty());
	}

	@Test
	public void pickFinishTileStaysInsideZone()
	{
		when(config.randomizeFinalTile()).thenReturn(true);
		Set<WorldPoint> zone = Rs2Walker.randomizedFinishZone(TARGET, TARGET, 5);

		Set<WorldPoint> picked = new HashSet<>();
		for (int i = 0; i < 500; i++)
		{
			WorldPoint finalTile = Rs2Walker.pickFinishTile(zone, TARGET);
			assertTrue(zone.contains(finalTile));
			picked.add(finalTile);
		}
		assertTrue(picked.stream().anyMatch(p -> p.distanceTo(TARGET) > 1));
	}

	@Test
	public void pickFinishTileFallsBackWhenZoneEmpty()
	{
		assertEquals(TARGET, Rs2Walker.pickFinishTile(Collections.emptySet(), TARGET));
	}

	private static HashMap<WorldPoint, Integer> reachableAround(WorldPoint origin, int radius)
	{
		HashMap<WorldPoint, Integer> tiles = new HashMap<>();
		for (int dx = -radius; dx <= radius; dx++)
		{
			for (int dy = -radius; dy <= radius; dy++)
			{
				tiles.put(origin.dx(dx).dy(dy), Math.max(Math.abs(dx), Math.abs(dy)));
			}
		}
		return tiles;
	}
}
