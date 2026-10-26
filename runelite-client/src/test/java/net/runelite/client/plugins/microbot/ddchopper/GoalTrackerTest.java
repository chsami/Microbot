package net.runelite.client.plugins.microbot.ddchopper;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class GoalTrackerTest
{
	@Test
	public void targetLevelStopsAtTarget()
	{
		assertNull(GoalTracker.stopReason(GoalType.TARGET_LEVEL, 29, 30, 0, 0));
		assertNotNull(GoalTracker.stopReason(GoalType.TARGET_LEVEL, 30, 30, 0, 0));
		assertNotNull(GoalTracker.stopReason(GoalType.TARGET_LEVEL, 45, 30, 0, 0));
	}

	@Test
	public void runTimeStopsWhenElapsed()
	{
		long limit = GoalTracker.runLimitMillis(1, 30);
		assertEquals(90 * 60_000L, limit);
		assertNull(GoalTracker.stopReason(GoalType.RUN_TIME, 10, 99, limit - 1, limit));
		assertNotNull(GoalTracker.stopReason(GoalType.RUN_TIME, 10, 99, limit, limit));
	}

	@Test
	public void untilStoppedOnlyStopsAtMaxLevel()
	{
		assertNull(GoalTracker.stopReason(GoalType.UNTIL_STOPPED, 98, 30, Long.MAX_VALUE, 0));
		assertNotNull(GoalTracker.stopReason(GoalType.UNTIL_STOPPED, 99, 30, 0, 0));
	}

	@Test
	public void levelIgnoredByRunTimeGoal()
	{
		assertNull(GoalTracker.stopReason(GoalType.RUN_TIME, 80, 30, 0, 60_000));
	}
}
