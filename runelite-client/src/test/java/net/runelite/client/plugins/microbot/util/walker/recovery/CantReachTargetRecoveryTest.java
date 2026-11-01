package net.runelite.client.plugins.microbot.util.walker.recovery;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.Scene;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

	@Test
	public void givingUpClearsTheLatchAndReportsTheAttempts()
	{
		boolean savedCantReach = Microbot.cantReachTarget;
		int savedRetries = Microbot.cantReachTargetRetries;
		try
		{
			Microbot.cantReachTarget = true;
			Microbot.cantReachTargetRetries = 4;

			assertEquals(4, CantReachTargetRecovery.giveUp());
			assertFalse(Microbot.cantReachTarget);
			assertEquals(0, Microbot.cantReachTargetRetries);
			assertFalse(CantReachTargetRecovery.shouldStart(true, Microbot.cantReachTarget));

			Microbot.cantReachTarget = true;
			Microbot.cantReachTargetRetries = 2;
			CantReachTargetRecovery.reset();
			assertFalse(Microbot.cantReachTarget);
			assertEquals(0, Microbot.cantReachTargetRetries);
		}
		finally
		{
			Microbot.cantReachTarget = savedCantReach;
			Microbot.cantReachTargetRetries = savedRetries;
		}
	}

	@Test
	public void staleAttemptsDoNotCarryOverToALaterRecovery()
	{
		boolean savedCantReach = Microbot.cantReachTarget;
		int savedRetries = Microbot.cantReachTargetRetries;
		try
		{
			CantReachTargetRecovery.reset();
			long start = 1_000_000L;

			assertEquals(1, CantReachTargetRecovery.countAttempt(start));
			assertEquals(2, CantReachTargetRecovery.countAttempt(start + 1_000));
			assertEquals(3, CantReachTargetRecovery.countAttempt(start + 2_000));
			assertEquals(3, CantReachTargetRecovery.retries(start + 2_000 + CantReachTargetRecovery.RETRY_WINDOW_MS));

			long later = start + 2_000 + CantReachTargetRecovery.RETRY_WINDOW_MS + 1;
			assertEquals(0, CantReachTargetRecovery.retries(later));
			assertFalse(CantReachTargetRecovery.retryExhausted(CantReachTargetRecovery.retries(later), 3));
			assertEquals(1, CantReachTargetRecovery.countAttempt(later));
		}
		finally
		{
			Microbot.cantReachTarget = savedCantReach;
			Microbot.cantReachTargetRetries = savedRetries;
		}
	}

	@Test
	public void instanceLocationIsConvertedToTheTemplateTile()
	{
		WorldView worldView = instanceWorldView(true);

		assertEquals(new WorldPoint(1878, 7096, 0),
			CantReachTargetRecovery.templateLocation(worldView, new WorldPoint(12630, 12568, 0)));
	}

	@Test
	public void locationOutsideAnInstanceIsUnchanged()
	{
		WorldPoint location = new WorldPoint(12630, 12568, 0);
		WorldPoint outOfScene = new WorldPoint(3200, 3200, 0);

		assertSame(location, CantReachTargetRecovery.templateLocation(instanceWorldView(false), location));
		assertSame(outOfScene, CantReachTargetRecovery.templateLocation(instanceWorldView(true), outOfScene));
		assertSame(location, CantReachTargetRecovery.templateLocation(null, location));
	}

	private static WorldView instanceWorldView(boolean instance)
	{
		int[][][] chunks = new int[4][13][13];
		chunks[0][5][6] = (234 << 14) | (887 << 3);
		Scene scene = mock(Scene.class);
		when(scene.isInstance()).thenReturn(instance);
		when(scene.getInstanceTemplateChunks()).thenReturn(chunks);
		WorldView worldView = mock(WorldView.class);
		when(worldView.getScene()).thenReturn(scene);
		when(worldView.getId()).thenReturn(-1);
		when(worldView.getBaseX()).thenReturn(12584);
		when(worldView.getBaseY()).thenReturn(12520);
		when(worldView.getSizeX()).thenReturn(104);
		when(worldView.getSizeY()).thenReturn(104);
		return worldView;
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
