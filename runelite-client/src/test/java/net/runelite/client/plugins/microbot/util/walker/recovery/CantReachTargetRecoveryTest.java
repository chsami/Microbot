package net.runelite.client.plugins.microbot.util.walker.recovery;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class CantReachTargetRecoveryTest
{
	private static final WorldPoint OBJECT_TARGET = new WorldPoint(3200, 3200, 0);
	private static final WorldPoint NPC_TARGET = new WorldPoint(3210, 3210, 0);

	@Test
	public void closedDoorDispatchRunsInsideObjectRecoveryWithoutReplacingTarget()
	{
		assertNestedDoorDispatch(OBJECT_TARGET);
	}

	@Test
	public void closedDoorDispatchRunsInsideNpcRecoveryWithoutReplacingTarget()
	{
		assertNestedDoorDispatch(NPC_TARGET);
	}

	@Test
	public void retryExhaustionStillTerminatesAndScopeAlwaysReleases()
	{
		assertFalse(CantReachTargetRecovery.retryExhausted(2, 3));
		assertTrue(CantReachTargetRecovery.retryExhausted(3, 3));

		try
		{
			CantReachTargetRecovery.walkTo(OBJECT_TARGET, 2, (target, distance) -> {
				throw new IllegalStateException("walk failed");
			});
		}
		catch (IllegalStateException expected)
		{
			assertEquals("walk failed", expected.getMessage());
		}
		assertTrue(CantReachTargetRecovery.shouldStart(true, true));
	}

	private static void assertNestedDoorDispatch(WorldPoint originalTarget)
	{
		AtomicReference<WorldPoint> walkedTarget = new AtomicReference<>();
		AtomicInteger doorClicks = new AtomicInteger();
		AtomicInteger nestedRecoveries = new AtomicInteger();

		assertTrue(CantReachTargetRecovery.shouldStart(true, true));
		boolean walked = CantReachTargetRecovery.walkTo(originalTarget, 2, (target, distance) -> {
			walkedTarget.set(target);
			assertEquals(2, distance.intValue());
			if (CantReachTargetRecovery.shouldStart(true, true))
			{
				nestedRecoveries.incrementAndGet();
			}
			else
			{
				doorClicks.incrementAndGet();
			}
			return true;
		});

		assertTrue(walked);
		assertSame(originalTarget, walkedTarget.get());
		assertEquals(1, doorClicks.get());
		assertEquals(0, nestedRecoveries.get());
		assertTrue(CantReachTargetRecovery.shouldStart(true, true));
	}
}
