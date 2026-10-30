package net.runelite.client.plugins.microbot.util.walker.recovery;

import static org.junit.Assert.assertFalse;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.QuestWalkRule;
import org.junit.After;
import org.junit.Test;

/** The no-modal skip is for hands-off quest runs; every other script keeps the pause and the message. */
public class CantReachScopeTest
{
	@After
	public void reset()
	{
		QuestWalkRule.setActive(false);
		Microbot.cantReachTarget = false;
		Microbot.cantReachTargetRetries = 0;
		Microbot.pauseAllScripts.set(false);
	}

	@Test
	public void aQuestRunSkipsTheTargetWithoutPausing()
	{
		QuestWalkRule.setActive(true);
		Microbot.cantReachTarget = true;
		Microbot.cantReachTargetRetries = 4;
		CantReachTargetRecovery.onExhausted("an object");
		assertFalse(Microbot.pauseAllScripts.get());
		assertFalse(Microbot.cantReachTarget);
	}
}
