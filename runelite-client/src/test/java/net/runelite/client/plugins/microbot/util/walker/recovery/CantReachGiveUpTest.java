package net.runelite.client.plugins.microbot.util.walker.recovery;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import net.runelite.client.plugins.microbot.Microbot;
import org.junit.After;
import org.junit.Test;

/** After repeated "I can't reach that!" the target is skipped: no pause, no modal, no latched flag. */
public class CantReachGiveUpTest
{
	@After
	public void reset()
	{
		Microbot.cantReachTarget = false;
		Microbot.cantReachTargetRetries = 0;
		Microbot.pauseAllScripts.set(false);
	}

	@Test
	public void givingUpClearsTheLatchAndNeverPausesScripts()
	{
		Microbot.cantReachTarget = true;
		Microbot.cantReachTargetRetries = 4;
		Microbot.pauseAllScripts.set(false);

		CantReachTargetRecovery.giveUp("an object");

		assertFalse("later interactions must not start another recovery", Microbot.cantReachTarget);
		assertEquals(0, Microbot.cantReachTargetRetries);
		assertFalse("scripts keep running", Microbot.pauseAllScripts.get());
		assertFalse(CantReachTargetRecovery.shouldStart(true, Microbot.cantReachTarget));
	}
}
