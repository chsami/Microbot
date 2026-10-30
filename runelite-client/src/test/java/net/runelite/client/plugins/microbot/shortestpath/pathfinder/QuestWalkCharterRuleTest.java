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
		assertTrue(QuestWalkRule.stageSkipped(QuestWalkRule.Stage.TELEPORTS, true, false));
		assertFalse(QuestWalkRule.stageSkipped(QuestWalkRule.Stage.TELEPORTS, false, false));
		assertFalse(QuestWalkRule.stageSkipped(QuestWalkRule.Stage.CROSSINGS, true, false));
	}

	@Test
	public void onlyCheapFaresAreFetched()
	{
		assertTrue(QuestWalkRule.fareFetchAllowed(30));
		assertFalse(QuestWalkRule.fareFetchAllowed(4400));
	}

	@Test
	public void aTransportThatKeepsRefusingEarnsTheTeleportFallback()
	{
		assertFalse(QuestWalkRule.teleportFallbackEarned(1));
		assertTrue(QuestWalkRule.teleportFallbackEarned(2));
		assertFalse(QuestWalkRule.stageSkipped(QuestWalkRule.Stage.TELEPORTS, true, true));
		assertTrue(QuestWalkRule.stageSkipped(QuestWalkRule.Stage.TELEPORTS, true, false));
	}

	@Test
	public void theSecondWalkThatStrikesOutTheSameEdgeAllowsTeleports()
	{
		net.runelite.api.coords.WorldPoint from = new net.runelite.api.coords.WorldPoint(3000, 3000, 0);
		net.runelite.api.coords.WorldPoint to = new net.runelite.api.coords.WorldPoint(3003, 3000, 0);
		try
		{
			QuestWalkRule.setTransportBlockLearned(false);
			QuestWalkRule.recordTransportStrikeOut(from, to);
			assertTrue(QuestWalkRule.skipsStage(QuestWalkRule.Stage.TELEPORTS));
			QuestWalkRule.setTransportBlockLearned(false); // the next walk starts
			assertFalse(QuestWalkRule.skipsStage(QuestWalkRule.Stage.TELEPORTS));
			QuestWalkRule.recordTransportStrikeOut(from, to);
			assertFalse(QuestWalkRule.skipsStage(QuestWalkRule.Stage.TELEPORTS));
			QuestWalkRule.setTransportBlockLearned(false);
			QuestWalkRule.forgetTransportStrikeOuts(from, to);
			QuestWalkRule.recordTransportStrikeOut(from, to);
			assertTrue(QuestWalkRule.skipsStage(QuestWalkRule.Stage.TELEPORTS));
		}
		finally
		{
			QuestWalkRule.forgetTransportStrikeOuts(from, to);
			QuestWalkRule.setTransportBlockLearned(false);
		}
	}
}
