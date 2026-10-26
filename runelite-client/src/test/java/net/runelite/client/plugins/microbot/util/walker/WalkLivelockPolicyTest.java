package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.client.plugins.microbot.util.walker.recovery.WalkLivelockPolicy;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WalkLivelockPolicyTest
{
	@Test
	public void abortsOnlyAfterThreshold()
	{
		assertFalse(WalkLivelockPolicy.shouldAbort(1000, 1000 + 45_000, 45_000));
		assertTrue(WalkLivelockPolicy.shouldAbort(1000, 1000 + 45_001, 45_000));
	}

	@Test
	public void disabledOrUnstartedNeverAborts()
	{
		assertFalse(WalkLivelockPolicy.shouldAbort(1000, 10_000_000, 0));
		assertFalse(WalkLivelockPolicy.shouldAbort(0, 10_000_000, 45_000));
	}
}
