package net.runelite.client.plugins.microbot.util.input;

import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.Global;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The waits observe a human takeover. Drives the real {@link Global} methods rather than stubbing
 * them, so what is asserted is the elapsed behaviour a script would see.
 */
public class YieldOnHumanTest
{
	private Object previousClient;

	@Before
	public void before() throws Exception
	{
		Client client = mock(Client.class);
		when(client.isClientThread()).thenReturn(false);
		previousClient = swapStatic("client", client);
		InputArbiter.resetForTest();
		PointerState.reset();
	}

	@After
	public void after() throws Exception
	{
		swapStatic("client", previousClient);
		InputArbiter.resetForTest();
		PointerState.reset();
	}

	@Test
	public void longFixedSleepDoesNotFinishItsRemainingTime()
	{
		long start = System.nanoTime();
		takeOver();

		Global.sleep(30_000);

		long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
		assertTrue("a 30s sleep must be cut short by a takeover, took " + elapsedMs + "ms", elapsedMs < 1_000);
	}

	@Test
	public void aSleepAlreadyRunningIsCutShort() throws Exception
	{
		Thread sleeper = new Thread(() -> Global.sleep(30_000));
		long start = System.nanoTime();
		sleeper.start();

		Thread.sleep(80);
		takeOver();
		sleeper.join(3_000);

		long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
		assertFalse("sleeper thread should have returned", sleeper.isAlive());
		assertTrue("takeover mid-sleep must cut the remainder, took " + elapsedMs + "ms", elapsedMs < 2_000);
	}

	@Test
	public void sleepUntilStopsPollingAndReportsFailure()
	{
		takeOver();
		AtomicInteger polls = new AtomicInteger();

		boolean result = Global.sleepUntil(() -> {
			polls.incrementAndGet();
			return false;
		}, 30_000);

		assertFalse(result);
		assertEquals("the condition must not be polled at all under HUMAN", 0, polls.get());
	}

	@Test
	public void sleepUntilWithActionStopsRunningTheAction()
	{
		takeOver();
		AtomicInteger actions = new AtomicInteger();

		boolean result = Global.sleepUntil(() -> false, actions::incrementAndGet, 30_000L, 50);

		assertFalse(result);
		assertEquals(0, actions.get());
	}

	@Test
	public void sleepUntilTrueOverloadsAllStop()
	{
		takeOver();

		assertFalse(Global.sleepUntilTrue(() -> true));
		assertFalse(Global.sleepUntilTrue(() -> true, 50, 30_000));
		assertFalse(Global.sleepUntilTrue(() -> true, () -> false, 50, 30_000));
	}

	@Test
	public void sleepUntilNotNullStops()
	{
		takeOver();

		assertNull(Global.sleepUntilNotNull(() -> "value", 30_000));
	}

	@Test
	public void awaitExecutionUntilStopsPollingAndSkipsTheCallback() throws Exception
	{
		takeOver();
		AtomicInteger callbacks = new AtomicInteger();

		ScheduledFuture<?> future = Global.awaitExecutionUntil(callbacks::incrementAndGet, () -> true, 10);

		Thread.sleep(200);
		assertTrue("poller must cancel itself under HUMAN", future.isCancelled() || future.isDone());
		assertEquals("the callback belongs to the condition, not to the abort", 0, callbacks.get());
	}

	@Test
	public void awaitExecutionUntilStillRunsTheCallbackNormally() throws Exception
	{
		AtomicInteger callbacks = new AtomicInteger();

		Global.awaitExecutionUntil(callbacks::incrementAndGet, () -> true, 10);

		Thread.sleep(200);
		assertEquals("exactly once: the task cancels itself after firing", 1, callbacks.get());
	}

	@Test
	public void concurrentAwaitExecutionUntilCallsDoNotCancelEachOther() throws Exception
	{
		AtomicInteger first = new AtomicInteger();
		AtomicInteger second = new AtomicInteger();

		// In one static field these raced, each cancelling the other's future.
		Global.awaitExecutionUntil(first::incrementAndGet, () -> true, 10);
		Global.awaitExecutionUntil(second::incrementAndGet, () -> true, 10);

		Thread.sleep(300);
		assertEquals(1, first.get());
		assertEquals(1, second.get());
	}

	@Test
	public void waitsResumeOnceTheIdleWindowElapses()
	{
		takeOver();
		assertFalse(Global.sleepUntil(() -> true, 200));

		InputArbiter.onRealButtonReleased(MouseEvent.BUTTON1);
		InputArbiter.setIdleResumeMs(0);
		assertFalse(InputArbiter.isHuman());

		assertTrue("after resume the waits behave normally again", Global.sleepUntil(() -> true, 200));
	}

	/**
	 * The gate in {@code Script.run()} is what idles a script on takeover, and every other test here
	 * passes without it: the waits returning early only matter if the loop then declines to run.
	 */
	@Test
	public void theScriptLoopGateDeclinesToRunOnTakeover() throws Exception
	{
		// run() consults the tutorial-island varp before reaching the gate, and that walks a cache
		// nothing else in these tests needs. Real instance: the class is final.
		net.runelite.client.callback.ClientThread clientThread =
			mock(net.runelite.client.callback.ClientThread.class);
		when(clientThread.runOnClientThreadOptional(org.mockito.ArgumentMatchers.any()))
			.thenReturn(java.util.Optional.empty());
		Object previousCache = swapStatic("rs2PlayerStateCache",
			new net.runelite.client.plugins.microbot.api.playerstate.Rs2PlayerStateCache(
				new net.runelite.client.eventbus.EventBus(), Microbot.getClient(), clientThread));
		try
		{
			net.runelite.client.plugins.microbot.Script script =
				new net.runelite.client.plugins.microbot.Script()
				{
					@Override
					protected boolean runsWhileLoggedOut()
					{
						return true;
					}
				};

			PointerState.setFromBot(100, 100);
			assertTrue("baseline, or the assertion below would hold for the wrong reason", script.run());

			takeOver();

			assertFalse(script.run());
		}
		finally
		{
			swapStatic("rs2PlayerStateCache", previousCache);
		}
	}

	private void takeOver()
	{
		PointerState.setFromBot(100, 100);
		InputArbiter.onRealButtonPressed(MouseEvent.BUTTON1);
		assertTrue(InputArbiter.isHuman());
	}

	private static Object swapStatic(String name, Object value) throws Exception
	{
		Field field = Microbot.class.getDeclaredField(name);
		field.setAccessible(true);
		Object previous = field.get(null);
		field.set(null, value);
		return previous;
	}
}
