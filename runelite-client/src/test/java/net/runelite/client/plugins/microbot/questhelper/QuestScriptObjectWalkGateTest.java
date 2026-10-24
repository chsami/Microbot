package net.runelite.client.plugins.microbot.questhelper;

import net.runelite.client.plugins.microbot.util.walker.WalkerState;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class QuestScriptObjectWalkGateTest
{
	@Test
	public void interactsWithObjectAcrossWaterWhenWalkerCannotRoute()
	{
		assertFalse(QuestScript.shouldKeepWalkingToObject(false, false, WalkerState.UNREACHABLE, true));
	}

	@Test
	public void waitsWhenWalkerCannotRouteAndNoObjectIsLoaded()
	{
		assertTrue(QuestScript.shouldKeepWalkingToObject(false, false, WalkerState.UNREACHABLE, false));
	}

	@Test
	public void waitsWhenWalkEndedWithoutRouteForAnyOtherReason()
	{
		assertTrue(QuestScript.shouldKeepWalkingToObject(false, false, WalkerState.ARRIVED, true));
		assertTrue(QuestScript.shouldKeepWalkingToObject(false, false, WalkerState.EXIT, true));
		assertTrue(QuestScript.shouldKeepWalkingToObject(false, false, WalkerState.MOVING, true));
	}

	@Test
	public void keepsWalkingWhileActiveRouteEndsAtStep()
	{
		assertTrue(QuestScript.shouldKeepWalkingToObject(true, true, WalkerState.MOVING, true));
	}

	@Test
	public void interactsWhenActiveRouteEndsElsewhere()
	{
		assertFalse(QuestScript.shouldKeepWalkingToObject(true, false, WalkerState.MOVING, true));
	}
}
