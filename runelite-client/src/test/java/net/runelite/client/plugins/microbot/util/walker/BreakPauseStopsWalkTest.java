package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.ScriptPauseController;
import static org.junit.Assert.assertTrue;
import org.junit.After;
import org.junit.Test;

/**
 * A break (pauseAllScripts) or manual pause must cancel an in-progress walk at the walker's single
 * stop choke point, so the walker does not keep issuing route/minimap clicks for the whole break.
 */
public class BreakPauseStopsWalkTest
{
	private final WorldPoint target = new WorldPoint(2673, 3298, 0);

	@After
	public void tearDown()
	{
		Microbot.pauseAllScripts.set(false);
		ScriptPauseController.setPaused(false);
	}

	@Test
	public void breakCancelsTheWalk()
	{
		Microbot.pauseAllScripts.set(true);
		assertTrue("a requested break must stop the walker", Rs2Walker.isWalkCancelled(target));
	}

	@Test
	public void manualPauseCancelsTheWalk()
	{
		ScriptPauseController.setPaused(true);
		assertTrue("a manual pause must stop the walker", Rs2Walker.isWalkCancelled(target));
	}
}
