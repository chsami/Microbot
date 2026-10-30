package net.runelite.client.plugins.microbot.util.walker.scene;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.CollisionDataFlag;
import org.junit.Test;

/** A one-tile-wide cave corridor (x=10, y=5..15) cut by a blockage at (10,10), like the Sourhog cave. */
public class SceneNavigatorTest
{
	private static int[][] cave()
	{
		int[][] flags = new int[SceneNavigator.SIZE][SceneNavigator.SIZE];
		for (int[] column : flags)
		{
			Arrays.fill(column, CollisionDataFlag.BLOCK_MOVEMENT_FLOOR);
		}
		for (int y = 5; y <= 15; y++)
		{
			flags[10][y] = 0;
		}
		flags[10][10] = CollisionDataFlag.BLOCK_MOVEMENT_OBJECT;
		return flags;
	}

	private static SceneNavigator.Obstacle blockage()
	{
		return new SceneNavigator.Obstacle("blockage", 10, 10, 10, 10);
	}

	@Test
	public void aBlockedCorridorHasNoWalkingPath()
	{
		int[][] flags = cave();
		boolean[][] goal = SceneNavigator.goalTiles(flags, 10, 6, 0);
		assertNull(SceneNavigator.path(flags, 10, 14, goal));
		assertFalse(SceneNavigator.onGoalSide(flags, 10, 14, goal));
	}

	@Test
	public void theOpenPartOfTheCorridorIsWalkedStraight()
	{
		int[][] flags = cave();
		List<int[]> path = SceneNavigator.path(flags, 10, 14, SceneNavigator.goalTiles(flags, 10, 11, 0));
		assertEquals(3, path.size());
		assertEquals(11, path.get(2)[1]);
		assertTrue(SceneNavigator.path(flags, 10, 11, SceneNavigator.goalTiles(flags, 10, 11, 0)).isEmpty());
	}

	@Test
	public void theBlockageThatJoinsTheTwoSidesIsFound()
	{
		int[][] flags = cave();
		boolean[][] goal = SceneNavigator.goalTiles(flags, 10, 6, 1);
		SceneNavigator.Crossing crossing = SceneNavigator.findCrossing(flags, 10, 14, goal, Collections.singletonList(blockage()));
		assertNotNull(crossing);
		assertEquals(11, crossing.near[1]);
		assertEquals(9, crossing.far[1]);
	}

	@Test
	public void anObstacleThatLeadsNowhereIsIgnored()
	{
		int[][] flags = cave();
		SceneNavigator.Obstacle elsewhere = new SceneNavigator.Obstacle("rock", 10, 14, 10, 14);
		boolean[][] goal = SceneNavigator.goalTiles(flags, 10, 6, 1);
		assertNull(SceneNavigator.findCrossing(flags, 10, 14, goal, Collections.singletonList(elsewhere)));
	}

	/** The bug: climbing twice puts you back. Once across there is a path, so no passage is chosen. */
	@Test
	public void onceAcrossTheBlockageIsNotUsedAgain()
	{
		int[][] flags = cave();
		boolean[][] goal = SceneNavigator.goalTiles(flags, 10, 6, 1);
		assertNull(SceneNavigator.findCrossing(flags, 10, 8, goal, Collections.singletonList(blockage())));
		assertTrue(SceneNavigator.onGoalSide(flags, 10, 8, goal));
		assertFalse(SceneNavigator.onGoalSide(flags, 10, 12, goal));
	}

	@Test
	public void onlyThePassageThatReachesTheGoalIsChosen()
	{
		int[][] flags = cave();
		flags[10][8] = CollisionDataFlag.BLOCK_MOVEMENT_OBJECT;
		SceneNavigator.Obstacle second = new SceneNavigator.Obstacle("second", 10, 8, 10, 8);
		boolean[][] goal = SceneNavigator.goalTiles(flags, 10, 6, 0);
		List<SceneNavigator.Obstacle> both = Arrays.asList(second, blockage());
		// from the entrance the first blockage leads only to the pocket between them, the second reaches the goal
		SceneNavigator.Crossing fromPocket = SceneNavigator.findCrossing(flags, 10, 9, goal, both);
		assertSame("second", fromPocket.obstacle.ref);
		assertNull(SceneNavigator.findCrossing(flags, 10, 12, goal, both));
	}

	@Test
	public void wallEdgesAndDiagonalsAreHonoured()
	{
		int[][] flags = new int[SceneNavigator.SIZE][SceneNavigator.SIZE];
		flags[5][5] = CollisionDataFlag.BLOCK_MOVEMENT_EAST;
		assertFalse(SceneNavigator.canStep(flags, 5, 5, 1, 0));
		assertTrue(SceneNavigator.canStep(flags, 5, 5, 0, 1));
		flags[6][6] = CollisionDataFlag.BLOCK_MOVEMENT_OBJECT;
		assertFalse(SceneNavigator.canStep(flags, 5, 5, 1, 1));
		assertFalse(SceneNavigator.standable(flags, -1, 3));
	}
}
