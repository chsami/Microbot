package net.runelite.client.plugins.microbot.shortestpath;

import net.runelite.client.plugins.microbot.util.walker.WalkerState;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ShortestPathScriptInputYieldTest
{
	@Test
	public void humanInputExitPreservesManualWalkForResume()
	{
		assertTrue(ShortestPathScript.shouldPreserveTargetAfterExit(
			WalkerState.EXIT, true));
		assertFalse(ShortestPathScript.shouldPreserveTargetAfterExit(
			WalkerState.EXIT, false));
		assertFalse(ShortestPathScript.shouldPreserveTargetAfterExit(
			WalkerState.ARRIVED, true));
	}
}
