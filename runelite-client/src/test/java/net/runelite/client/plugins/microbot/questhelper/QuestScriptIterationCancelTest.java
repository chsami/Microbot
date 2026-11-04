package net.runelite.client.plugins.microbot.questhelper;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.Test;
import org.slf4j.event.Level;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class QuestScriptIterationCancelTest
{
	@Test
	public void cancelInterruptsTheIterationInFlight() throws Exception
	{
		QuestScript script = new QuestScript();
		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch never = new CountDownLatch(1);
		AtomicBoolean interrupted = new AtomicBoolean();
		Thread worker = new Thread(() -> script.runTracked(() ->
		{
			started.countDown();
			try
			{
				never.await();
			}
			catch (InterruptedException e)
			{
				interrupted.set(true);
			}
		}));
		worker.start();
		assertTrue(started.await(5, TimeUnit.SECONDS));

		script.cancelInFlightIteration();
		worker.join(5_000);

		assertFalse(worker.isAlive());
		assertTrue(interrupted.get());
	}

	@Test
	public void cancelAfterTheIterationFinishedLeavesItsThreadAlone()
	{
		QuestScript script = new QuestScript();
		script.runTracked(() -> { });

		script.cancelInFlightIteration();

		assertFalse(Thread.interrupted());
	}

	@Test
	public void cancelWithoutAnyIterationIsANoOp()
	{
		new QuestScript().cancelInFlightIteration();

		assertFalse(Thread.interrupted());
	}

	@Test
	public void interruptedIterationCountsAsCancelled()
	{
		QuestScript script = new QuestScript();
		assertFalse(script.isIterationCancelled());

		Thread.currentThread().interrupt();
		try
		{
			assertTrue(script.isIterationCancelled());
		}
		finally
		{
			Thread.interrupted();
		}
	}

	@Test
	public void cancelledIterationStopsWithoutBlamingThePlayer()
	{
		QuestScript script = new QuestScript();
		String previousStatus = Microbot.status;
		Microbot.status = "Waiting for Grand Exchange offers to fill";
		Thread.currentThread().interrupt();
		try
		{
			script.stopQuesterWithReason("Grand Exchange offers did not fill", Level.ERROR);

			assertEquals("Waiting for Grand Exchange offers to fill", Microbot.status);
		}
		finally
		{
			Thread.interrupted();
			Microbot.status = previousStatus;
		}
	}

	@Test
	public void stopLoopEndsTheQuestLoopWithoutResettingOtherScripts()
	{
		QuestHelperConfig config = mock(QuestHelperConfig.class);
		when(config.startStopQuestHelper()).thenReturn(false);
		QuestScript script = new QuestScript();
		boolean previousPause = Microbot.pauseAllScripts.get();
		Microbot.pauseAllScripts.set(true);
		try
		{
			script.run(config, null);
			assertTrue(script.isRunning());
			assertTrue(script.isIterationCancelled());

			script.stopLoop();

			assertFalse(script.isRunning());
			assertTrue(Microbot.pauseAllScripts.get());
		}
		finally
		{
			Microbot.pauseAllScripts.set(previousPause);
		}
	}
}
