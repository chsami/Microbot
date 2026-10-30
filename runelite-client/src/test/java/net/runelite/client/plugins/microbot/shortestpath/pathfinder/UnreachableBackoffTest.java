package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class UnreachableBackoffTest
{
	private static final WorldPoint ELENA = new WorldPoint(2592, 3336, 0);
	private static final WorldPoint ZEAH = new WorldPoint(1884, 3481, 0);
	private static final WorldPoint OTHER = new WorldPoint(3222, 3218, 0);

	@Test
	public void firstFailureIsReportedOnceAndBacksOff()
	{
		UnreachableBackoff backoff = new UnreachableBackoff();
		assertTrue(backoff.record(ELENA, ZEAH, 0));
		assertFalse(backoff.record(ELENA, ZEAH, 40_000));
		assertTrue(backoff.isBackedOff(ELENA, ZEAH, 41_000));
		assertEquals(ELENA, backoff.target(ZEAH, 41_000));
	}

	@Test
	public void waitDoublesUpToTheCap()
	{
		UnreachableBackoff backoff = new UnreachableBackoff();
		backoff.record(ELENA, ZEAH, 0);
		assertEquals(UnreachableBackoff.FIRST_MS, backoff.remainingMs(0));
		backoff.record(ELENA, ZEAH, 0);
		assertEquals(UnreachableBackoff.FIRST_MS * 2, backoff.remainingMs(0));
		for (int i = 0; i < 10; i++)
		{
			backoff.record(ELENA, ZEAH, 0);
		}
		assertEquals(UnreachableBackoff.MAX_MS, backoff.remainingMs(0));
	}

	@Test
	public void otherTargetsAndExpiryAreNotBackedOff()
	{
		UnreachableBackoff backoff = new UnreachableBackoff();
		backoff.record(ELENA, ZEAH, 0);
		assertFalse(backoff.isBackedOff(OTHER, ZEAH, 1_000));
		assertFalse(backoff.isBackedOff(ELENA, ZEAH, UnreachableBackoff.FIRST_MS));
		assertNull(backoff.target(ZEAH, UnreachableBackoff.FIRST_MS));
	}

	@Test
	public void newTargetRestartsTheWait()
	{
		UnreachableBackoff backoff = new UnreachableBackoff();
		backoff.record(ELENA, ZEAH, 0);
		backoff.record(ELENA, ZEAH, 0);
		assertTrue(backoff.record(OTHER, ZEAH, 0));
		assertEquals(UnreachableBackoff.FIRST_MS, backoff.remainingMs(0));
	}

	@Test
	public void movingAwayOrChangingPlaneEndsTheWait()
	{
		UnreachableBackoff backoff = new UnreachableBackoff();
		backoff.record(ELENA, ZEAH, 0);
		assertTrue(backoff.isBackedOff(ELENA, new WorldPoint(1900, 3490, 0), 1_000));
		assertFalse(backoff.isBackedOff(ELENA, new WorldPoint(3055, 3245, 0), 1_000));
		assertFalse(backoff.isBackedOff(ELENA, new WorldPoint(1884, 3481, 1), 1_000));
		assertNull(backoff.target(new WorldPoint(3055, 3245, 0), 1_000));
	}

	@Test
	public void clearOnlyForgetsTheGivenTarget()
	{
		UnreachableBackoff backoff = new UnreachableBackoff();
		backoff.record(ELENA, ZEAH, 0);
		backoff.clear(OTHER);
		assertTrue(backoff.isBackedOff(ELENA, ZEAH, 1));
		backoff.clear(ELENA);
		assertFalse(backoff.isBackedOff(ELENA, ZEAH, 1));
	}

	@Test
	public void resetClearsTheState()
	{
		UnreachableBackoff backoff = new UnreachableBackoff();
		backoff.record(ELENA, ZEAH, 0);
		backoff.reset();
		assertFalse(backoff.isBackedOff(ELENA, ZEAH, 1));
		assertNull(backoff.target(ZEAH, 1));
	}
}
