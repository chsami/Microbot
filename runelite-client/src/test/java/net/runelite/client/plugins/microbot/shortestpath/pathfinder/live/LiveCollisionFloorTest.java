package net.runelite.client.plugins.microbot.shortestpath.pathfinder.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import net.runelite.client.plugins.microbot.shortestpath.pathfinder.CollisionMap;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.SplitFlagMap;
import org.junit.Test;

/**
 * Open air above the ground reads as walkable in the live collision flags, and live overrides the static map: the walker
 * planned a route from Falador castle's upper floor straight across the moat. A tile with no floor must stay blocked.
 */
public class LiveCollisionFloorTest
{
	private static final int SCENE = 104;
	// a scene whose south-west corner is chosen so that the castle and the timber house sit well inside it
	private static final int BASE_X = 2940;
	private static final int BASE_Y = 3290;

	/** A live capture in which every tile reads open (flags 0) on both floors. */
	private static int[][][] allOpen()
	{
		return new int[2][SCENE][SCENE];
	}

	private static boolean[][][] groundFloorOnly()
	{
		boolean[][][] floors = new boolean[2][SCENE][SCENE];
		for (int x = 0; x < SCENE; x++)
		{
			for (int y = 0; y < SCENE; y++)
			{
				floors[0][x][y] = true;
			}
		}
		return floors;
	}

	private static Boolean north(LiveCollisionSnapshot snapshot, int x, int y, int z)
	{
		return snapshot.edge(x, y, z, LiveCollisionSnapshot.FLAG_NORTH);
	}

	private static Boolean east(LiveCollisionSnapshot snapshot, int x, int y, int z)
	{
		return snapshot.edge(x, y, z, LiveCollisionSnapshot.FLAG_EAST);
	}

	@Test
	public void emptyUpperFloorsAreUnknownSoTheStaticMapDecidesEastOfFaladorCastle()
	{
		LiveCollisionSnapshot snapshot = LiveCollisionCapture.build(
			BASE_X, BASE_Y, 2, allOpen(), null, groundFloorOnly());
		for (int x : new int[] {2988, 2990, 2992, 2994})
		{
			assertEquals("east of the castle at " + x, null, east(snapshot, x, 3329, 1));
			assertEquals(null, north(snapshot, x, 3329, 1));
		}
		assertEquals("the ground below is still open", Boolean.TRUE, east(snapshot, 2990, 3329, 0));
	}

	@Test
	public void theOldCaptureWithoutFloorsWouldHaveCalledThemOpen()
	{
		LiveCollisionSnapshot snapshot = LiveCollisionCapture.build(BASE_X, BASE_Y, 2, allOpen(), null);
		assertEquals(Boolean.TRUE, east(snapshot, 2990, 3329, 1));
	}

	@Test
	public void theArdougneTimberHouseUpperFloorOutsideItsWallsIsUnknown()
	{
		LiveCollisionSnapshot snapshot = LiveCollisionCapture.build(
			2580, 3260, 2, allOpen(), null, groundFloorOnly());
		assertEquals(null, north(snapshot, 2617, 3294, 1));
		assertEquals(null, east(snapshot, 2617, 3294, 1));
	}

	@Test
	public void aFlooredUpperTileKeepsTheLiveAnswer()
	{
		boolean[][][] floors = groundFloorOnly();
		int sx = 2990 - BASE_X;
		int sy = 3329 - BASE_Y;
		floors[1][sx][sy] = true;
		floors[1][sx + 1][sy] = true;
		LiveCollisionSnapshot snapshot = LiveCollisionCapture.build(BASE_X, BASE_Y, 2, allOpen(), null, floors);
		assertEquals(Boolean.TRUE, east(snapshot, 2990, 3329, 1));
	}

	@Test
	public void theStaticMapBlocksThoseEdgesToo()
	{
		CollisionMap staticMap = new CollisionMap(SplitFlagMap.fromResources());
		assertNotNull(staticMap);
		// where the static map already blocks, the live capture must not open: nothing on plane 1 beside the castle is open
		assertFalse(staticMap.e(2990, 3329, 1));
		assertTrue(CAPTURE_VERSION_BUMPED());
	}

	private static boolean CAPTURE_VERSION_BUMPED()
	{
		return LiveCollisionCapture.CAPTURE_VERSION >= 4;
	}

	/** A real surface with no paint or model (a ship deck, scaffolding, a Low Detail scene) must not be blocked, and open air must not be opened. */
	@Test
	public void aFloorlessTileNeitherOpensNorBlocksWhateverTheLiveFlagsSay()
	{
		int[][][] blocked = new int[2][SCENE][SCENE];
		for (int x = 0; x < SCENE; x++)
		{
			for (int y = 0; y < SCENE; y++)
			{
				blocked[1][x][y] = net.runelite.api.CollisionDataFlag.BLOCK_MOVEMENT_FULL;
			}
		}
		LiveCollisionSnapshot open = LiveCollisionCapture.build(BASE_X, BASE_Y, 2, allOpen(), null, groundFloorOnly());
		LiveCollisionSnapshot shut = LiveCollisionCapture.build(BASE_X, BASE_Y, 2, blocked, null, groundFloorOnly());
		assertEquals(null, east(open, 2990, 3329, 1));
		assertEquals(null, east(shut, 2990, 3329, 1));
		assertEquals(null, north(open, 2990, 3329, 1));
		assertEquals(null, north(shut, 2990, 3329, 1));
	}
}
