package net.runelite.client.plugins.microbot.questhelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import net.runelite.api.coords.WorldPoint;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Test;

public class QuestPauseAndStopTest
{
	@After
	public void clean()
	{
		QuestPause.resume();
		QuestPreflight.resetToIdle();
	}

	@Test
	public void pauseOnlyCancelsTheQuestLoopsOwnWalk()
	{
		WorldPoint walk = new WorldPoint(2592, 3336, 0);
		assertTrue(QuestPause.shouldCancelWalk(true, walk));
		assertFalse(QuestPause.shouldCancelWalk(false, walk));
		assertFalse(QuestPause.shouldCancelWalk(true, null));
	}

	@Test
	public void stopBeatsPauseAndPauseBeatsRun()
	{
		assertEquals(QuestPause.Action.ABORT, QuestPause.decide(false, true));
		assertEquals(QuestPause.Action.ABORT, QuestPause.decide(false, false));
		assertEquals(QuestPause.Action.WAIT, QuestPause.decide(true, true));
		assertEquals(QuestPause.Action.RUN, QuestPause.decide(true, false));
	}

	@Test
	public void toggleFlipsAndReportsTheNewState()
	{
		assertFalse(QuestPause.isPaused());
		assertTrue(QuestPause.toggle());
		assertTrue(QuestPause.isPaused());
		assertFalse(QuestPause.toggle());
		assertFalse(QuestPause.isPaused());
	}

	@Test
	public void aPausedRoutineWaitsThenContinuesWhereItWasOnResume() throws Exception
	{
		QuestPause.pause();
		AtomicBoolean on = new AtomicBoolean(true);
		AtomicBoolean result = new AtomicBoolean();
		CountDownLatch done = new CountDownLatch(1);
		Thread routine = new Thread(() ->
		{
			result.set(QuestPause.awaitResume(on::get, 10));
			done.countDown();
		});
		routine.start();
		assertFalse("still frozen while paused", done.await(150, TimeUnit.MILLISECONDS));
		QuestPause.resume();
		assertTrue(done.await(2, TimeUnit.SECONDS));
		assertTrue("resumed, not aborted", result.get());
	}

	@Test
	public void stoppingWhilePausedEndsTheWaitWithAnAbort() throws Exception
	{
		QuestPause.pause();
		AtomicBoolean on = new AtomicBoolean(true);
		AtomicBoolean result = new AtomicBoolean(true);
		CountDownLatch done = new CountDownLatch(1);
		Thread routine = new Thread(() ->
		{
			result.set(QuestPause.awaitResume(on::get, 10));
			done.countDown();
		});
		routine.start();
		on.set(false);
		assertTrue(done.await(2, TimeUnit.SECONDS));
		assertFalse(result.get());
	}

	@Test
	public void anInterruptEndsAWaitingOrRunningRoutine() throws Exception
	{
		QuestPause.pause();
		AtomicBoolean result = new AtomicBoolean(true);
		CountDownLatch done = new CountDownLatch(1);
		Thread routine = new Thread(() ->
		{
			result.set(QuestPause.awaitResume(() -> true, 10_000));
			done.countDown();
		});
		routine.start();
		routine.interrupt();
		assertTrue(done.await(2, TimeUnit.SECONDS));
		assertFalse(result.get());
		QuestPause.resume();
		Thread.currentThread().interrupt();
		assertFalse("an interrupted thread must stop between actions", QuestPause.awaitResume(() -> true, 10));
		Thread.interrupted();
	}

	@Test
	public void stopPutsTheBuyingPreflightBackToIdle()
	{
		QuestPreflight.setStatus(QuestPreflight.BUYING);
		assertTrue(QuestPreflight.isInProgress());
		QuestPreflight.resetToIdle();
		assertFalse(QuestPreflight.isInProgress());
		assertEquals(QuestPreflight.PENDING, QuestPreflight.getStatus());
	}

	@Test
	public void onlyOneLoopRunsAtATimeAndTheOtherIsTurnedAway() throws Exception
	{
		TickGuard guard = new TickGuard();
		AtomicInteger ticks = new AtomicInteger();
		CountDownLatch inside = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		Runnable slow = guard.guard(() ->
		{
			ticks.incrementAndGet();
			inside.countDown();
			try
			{
				release.await(5, TimeUnit.SECONDS);
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
			}
		});
		Thread first = new Thread(slow, "QuestScript-5");
		first.start();
		assertTrue(inside.await(2, TimeUnit.SECONDS));
		assertTrue(guard.isBusy());
		assertSame(first, guard.owner());

		// the second loop (QuestScript-7 in the live log) is skipped, it does not run the buy a second time
		Thread second = new Thread(slow, "QuestScript-7");
		second.start();
		second.join(2000);
		assertEquals(1, ticks.get());

		release.countDown();
		first.join(2000);
		assertFalse(guard.isBusy());
		assertNull(guard.owner());
		slow.run();
		assertEquals("free again after the first loop left", 2, ticks.get());
	}

	@Test
	public void theBuyMutexTurnsASecondBuyerAwayAndAllowsTheSameThreadBackIn() throws Exception
	{
		TickGuard buy = new TickGuard();
		CountDownLatch inside = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		AtomicBoolean secondRan = new AtomicBoolean();
		Thread first = new Thread(() -> buy.callOrElse(() ->
		{
			inside.countDown();
			try
			{
				release.await(5, TimeUnit.SECONDS);
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
			}
			return buy.callOrElse(() -> Boolean.TRUE, Boolean.FALSE); // nested call by the owner goes through
		}, Boolean.FALSE));
		first.start();
		assertTrue(inside.await(2, TimeUnit.SECONDS));
		Boolean second = buy.callOrElse(() ->
		{
			secondRan.set(true);
			return Boolean.TRUE;
		}, Boolean.FALSE);
		assertFalse(second);
		assertFalse(secondRan.get());
		release.countDown();
		first.join(2000);
		assertTrue(buy.callOrElse(() -> Boolean.TRUE, Boolean.FALSE));
	}
}
