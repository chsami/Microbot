package net.runelite.client.plugins.microbot.questhelper;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
}
