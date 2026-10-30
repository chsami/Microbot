package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class QuestWalkCharterRuleTest
{
	@Test
	public void charterShipsAreOffByDefault()
	{
		assertFalse(QuestWalkRule.charterPermitted(false));
		assertTrue(QuestWalkRule.charterPermitted(true));
	}

	@Test
	public void aRefusedTransportHoldsBackTheTeleportStageOnly()
	{
		assertTrue(QuestWalkRule.stageSkipped(QuestWalkRule.Stage.TELEPORTS, true));
		assertFalse(QuestWalkRule.stageSkipped(QuestWalkRule.Stage.TELEPORTS, false));
		assertFalse(QuestWalkRule.stageSkipped(QuestWalkRule.Stage.CROSSINGS, true));
	}

	@Test
	public void onlyCheapFaresAreFetched()
	{
		assertTrue(QuestWalkRule.fareFetchAllowed(30));
		assertFalse(QuestWalkRule.fareFetchAllowed(4400));
	}
}
