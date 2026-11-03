package net.runelite.client.plugins.microbot;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.input.InputLoop;
import org.mockito.MockedStatic;
import org.slf4j.event.Level;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ScriptPauseControllerTest
{
	private static final class PauseAwareTestScript extends Script
	{
		void startLoop()
		{
			scheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> { }, 1, 1, TimeUnit.DAYS);
		}
	}

	@PluginDescriptor(name = "<html>[<font color=green>D</font>] Pause Test Plugin")
	private static final class PauseAwareTestPlugin extends Plugin
	{
		private final PauseAwareTestScript script = new PauseAwareTestScript();
	}

	@PluginDescriptor(name = "<html>[<font color=green>D</font>] Multi Script Plugin")
	private static final class MultiScriptTestPlugin extends Plugin
	{
		private final PauseAwareTestScript first = new PauseAwareTestScript();
		private final PauseAwareTestScript second = new PauseAwareTestScript();
	}

	private ScriptScheduledExecutor executor;
	private Client client;
	private Object previousClient;

	@Before
	public void setUp() throws Exception
	{
		ScriptPauseController.setPaused(false).get(2, TimeUnit.SECONDS);
		client = mock(Client.class);
		Field field = Microbot.class.getDeclaredField("client");
		field.setAccessible(true);
		previousClient = field.get(null);
		field.set(null, client);
		executor = new ScriptScheduledExecutor(2, task -> new Thread(task, "pause-test"));
		InputArbiter.resetForTest();
	}

	@After
	public void tearDown() throws Exception
	{
		executor.shutdownNow();
		ScriptPauseController.setPaused(false).get(2, TimeUnit.SECONDS);
		assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
		Field field = Microbot.class.getDeclaredField("client");
		field.setAccessible(true);
		field.set(null, previousClient);
		InputArbiter.resetForTest();
	}

	@Test
	public void periodicTasksKeepStateAndResumeWithoutBeingCancelled() throws Exception
	{
		ScriptPauseController.setPaused(true).get(2, TimeUnit.SECONDS);
		AtomicInteger state = new AtomicInteger(7);
		CountDownLatch resumed = new CountDownLatch(1);
		Future<?> loop = executor.scheduleWithFixedDelay(() -> {
			state.incrementAndGet();
			resumed.countDown();
		}, 0, 10, TimeUnit.MILLISECONDS);
		assertFalse(resumed.await(100, TimeUnit.MILLISECONDS));
		assertEquals(7, state.get());
		assertFalse(loop.isDone());
		ScriptPauseController.setPaused(false).get(2, TimeUnit.SECONDS);
		assertTrue(resumed.await(2, TimeUnit.SECONDS));
		assertTrue(state.get() > 7);
		loop.cancel(true);
	}

	@Test
	public void fixedRateTaskDoesNotReplayMissedTicksAfterInFlightPause() throws Exception
	{
		CountDownLatch paused = new CountDownLatch(1);
		CountDownLatch nextTick = new CountDownLatch(1);
		AtomicInteger calls = new AtomicInteger();
		java.util.concurrent.atomic.AtomicLong resumedAt = new java.util.concurrent.atomic.AtomicLong();
		java.util.concurrent.atomic.AtomicLong nextAt = new java.util.concurrent.atomic.AtomicLong();
		Future<?> loop = executor.scheduleAtFixedRate(() -> {
			if (calls.incrementAndGet() == 1) {
				ScriptPauseController.setPaused(true);
				paused.countDown();
				ScriptPauseController.awaitResume();
				resumedAt.set(System.nanoTime());
			} else {
				nextAt.compareAndSet(0, System.nanoTime());
				nextTick.countDown();
			}
		}, 0, 100, TimeUnit.MILLISECONDS);
		assertTrue(paused.await(2, TimeUnit.SECONDS));
		assertFalse(nextTick.await(250, TimeUnit.MILLISECONDS));
		ScriptPauseController.setPaused(false).get(2, TimeUnit.SECONDS);
		assertTrue(nextTick.await(2, TimeUnit.SECONDS));
		assertTrue("Missed ticks replayed immediately", nextAt.get() - resumedAt.get() >= TimeUnit.MILLISECONDS.toNanos(90));
		loop.cancel(true);
	}

	@Test
	public void queuedOneShotWaitsForResumeAndKeepsItsResult() throws Exception
	{
		ScriptPauseController.setPaused(true).get(2, TimeUnit.SECONDS);
		Future<Integer> task = executor.schedule(() -> 42, 0, TimeUnit.MILLISECONDS);
		assertPending(task);
		ScriptPauseController.setPaused(false).get(2, TimeUnit.SECONDS);
		assertEquals(Integer.valueOf(42), task.get(2, TimeUnit.SECONDS));
	}

	@Test
	public void inFlightWaitPreservesStackAndDoesNotConsumeTimeoutDuringPause() throws Exception
	{
		CountDownLatch conditionChecked = new CountDownLatch(1);
		AtomicBoolean satisfied = new AtomicBoolean();
		Future<Integer> task = executor.submit(() -> {
			int localState = 42;
			boolean success = Global.sleepUntil(() -> {
				if (conditionChecked.getCount() > 0) {
					ScriptPauseController.setPaused(true);
					conditionChecked.countDown();
				}
				return satisfied.get();
			}, 250);
			return success ? localState : -1;
		});
		assertTrue(conditionChecked.await(2, TimeUnit.SECONDS));
		ScriptPauseController.setPaused(true).get(2, TimeUnit.SECONDS);
		long frozenTime = ScriptPauseController.activeTimeNanos();
		try {
			task.get(400, TimeUnit.MILLISECONDS);
			fail("Paused wait completed");
		} catch (TimeoutException expected) {
			assertEquals(frozenTime, ScriptPauseController.activeTimeNanos());
		}
		satisfied.set(true);
		ScriptPauseController.setPaused(false).get(2, TimeUnit.SECONDS);
		assertEquals(Integer.valueOf(42), task.get(2, TimeUnit.SECONDS));
	}

	@Test
	public void waitNeverBlocksClientOrSwingThread() throws Exception
	{
		ScriptPauseController.setPaused(true).get(2, TimeUnit.SECONDS);
		when(client.isClientThread()).thenReturn(true);
		assertFalse(ScriptPauseController.awaitResume());
		when(client.isClientThread()).thenReturn(false);
		SwingUtilities.invokeAndWait(() -> assertFalse(ScriptPauseController.awaitResume()));
	}

	@Test
	public void sleepStillWaitsOnSwingThreadWhenItIsNotTheClientThread() throws Exception
	{
		when(client.isClientThread()).thenReturn(false);
		AtomicLong elapsedNanos = new AtomicLong();
		SwingUtilities.invokeAndWait(() -> {
			long start = System.nanoTime();
			Global.sleep(80);
			elapsedNanos.set(System.nanoTime() - start);
		});
		assertTrue("Swing-thread wait returned before the requested delay", elapsedNanos.get() >= TimeUnit.MILLISECONDS.toNanos(60));
	}

	@Test
	public void pauseRemainsIndependentOfLegacyPauseWriters() throws Exception
	{
		boolean original = Microbot.pauseAllScripts.get();
		try {
			ScriptPauseController.setPaused(true).get(2, TimeUnit.SECONDS);
			Microbot.pauseAllScripts.set(false);
			assertTrue(ScriptPauseController.isPaused());
			Microbot.pauseAllScripts.set(true);
			ScriptPauseController.setPaused(false).get(2, TimeUnit.SECONDS);
			assertTrue(Microbot.pauseAllScripts.get());
		} finally {
			Microbot.pauseAllScripts.set(original);
		}
	}

	@Test
	public void pauseRejectsNewAndInFlightGestures() throws Exception
	{
		AtomicBoolean ran = new AtomicBoolean();
		ScriptPauseController.setPaused(true).get(2, TimeUnit.SECONDS);
		assertEquals(InputLoop.Result.ABORTED, InputLoop.run(emit -> ran.set(true)));
		assertFalse(ran.get());

		ScriptPauseController.setPaused(false).get(2, TimeUnit.SECONDS);
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch continueGesture = new CountDownLatch(1);
		java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor();
		try
		{
			Future<InputLoop.Result> gesture = worker.submit(() -> InputLoop.run(emit -> {
				entered.countDown();
				try
				{
					continueGesture.await();
				}
				catch (InterruptedException ex)
				{
					Thread.currentThread().interrupt();
				}
				emit.checkpoint();
			}));
			assertTrue(entered.await(2, TimeUnit.SECONDS));
			ScriptPauseController.setPaused(true).get(2, TimeUnit.SECONDS);
			ScriptPauseController.setPaused(false).get(2, TimeUnit.SECONDS);
			continueGesture.countDown();
			assertEquals(InputLoop.Result.ABORTED, gesture.get(2, TimeUnit.SECONDS));
		}
		finally
		{
			worker.shutdownNow();
			assertTrue(worker.awaitTermination(2, TimeUnit.SECONDS));
		}
	}

	@Test
	public void pausedWorkerCanBeInterruptedForPluginShutdown() throws Exception
	{
		ScriptPauseController.setPaused(true).get(2, TimeUnit.SECONDS);
		Future<?> task = executor.submit(() -> fail("Cancelled task ran"));
		assertPending(task);
		assertTrue(task.cancel(true));
		executor.shutdownNow();
		assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
	}

	@Test
	public void scriptStartedWhilePausedLogsOnceForThatStart() throws Exception
	{
		PauseAwareTestScript script = new PauseAwareTestScript();
		ScriptPauseController.setPaused(true).get(2, TimeUnit.SECONDS);
		try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class, CALLS_REAL_METHODS))
		{
			microbot.when(Microbot::getEventBus).thenReturn(null);
			script.startLoop();
			assertFalse(script.run());
			assertFalse(script.run());
			microbot.verify(() -> Microbot.log("Pause Aware Test is waiting: scripts are paused. Click the amber play button next to the Enable/Disable Input button to resume.", Level.WARN), times(1));
		}
		finally
		{
			script.shutdown();
			script.scheduledExecutorService.shutdownNow();
		}
	}

	@Test
	public void scriptWaitMessageUsesOwningPluginDescriptorName() throws Exception
	{
		PauseAwareTestPlugin plugin = new PauseAwareTestPlugin();
		PluginManager pluginManager = mock(PluginManager.class);
		when(pluginManager.getPlugins()).thenReturn(java.util.Collections.singleton(plugin));
		ScriptPauseController.setPaused(true).get(2, TimeUnit.SECONDS);
		try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class, CALLS_REAL_METHODS))
		{
			microbot.when(Microbot::getEventBus).thenReturn(null);
			microbot.when(Microbot::getPluginManager).thenReturn(pluginManager);
			plugin.script.startLoop();
			assertFalse(plugin.script.run());
			microbot.verify(() -> Microbot.log("Pause Test Plugin is waiting: scripts are paused. Click the amber play button next to the Enable/Disable Input button to resume.", Level.WARN), times(1));
		}
		finally
		{
			plugin.script.shutdown();
			plugin.script.scheduledExecutorService.shutdownNow();
		}
	}

	@Test
	public void pluginWithSeveralScriptsAnnouncesWaitOncePerPause() throws Exception
	{
		MultiScriptTestPlugin plugin = new MultiScriptTestPlugin();
		PluginManager pluginManager = mock(PluginManager.class);
		when(pluginManager.getPlugins()).thenReturn(java.util.Collections.singleton(plugin));
		ScriptPauseController.setPaused(true).get(2, TimeUnit.SECONDS);
		try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class, CALLS_REAL_METHODS))
		{
			microbot.when(Microbot::getEventBus).thenReturn(null);
			microbot.when(Microbot::getPluginManager).thenReturn(pluginManager);

			assertFalse(plugin.first.run());
			assertFalse(plugin.second.run());
			microbot.verify(() -> Microbot.log("Multi Script Plugin is waiting: scripts are paused. Click the amber play button next to the Enable/Disable Input button to resume.", Level.WARN), times(1));

			ScriptPauseController.setPaused(false).get(2, TimeUnit.SECONDS);
			plugin.first.shutdown();
			plugin.second.shutdown();
			ScriptPauseController.setPaused(true).get(2, TimeUnit.SECONDS);

			assertFalse(plugin.first.run());
			assertFalse(plugin.second.run());
			microbot.verify(() -> Microbot.log("Multi Script Plugin is waiting: scripts are paused. Click the amber play button next to the Enable/Disable Input button to resume.", Level.WARN), times(2));
		}
		finally
		{
			plugin.first.shutdown();
			plugin.first.scheduledExecutorService.shutdownNow();
			plugin.second.shutdown();
			plugin.second.scheduledExecutorService.shutdownNow();
		}
	}

	@Test
	public void anonymousScriptWaitMessageUsesGenericName() throws Exception
	{
		Script script = new Script() { };
		ScriptPauseController.setPaused(true).get(2, TimeUnit.SECONDS);
		try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class, CALLS_REAL_METHODS))
		{
			microbot.when(Microbot::getEventBus).thenReturn(null);
			script.scheduledExecutorService.scheduleWithFixedDelay(() -> { }, 1, 1, TimeUnit.DAYS);
			assertFalse(script.run());
			microbot.verify(() -> Microbot.log("A script is waiting: scripts are paused. Click the amber play button next to the Enable/Disable Input button to resume.", Level.WARN), times(1));
		}
		finally
		{
			script.shutdown();
			script.scheduledExecutorService.shutdownNow();
		}
	}

	private static void assertPending(Future<?> task) throws Exception
	{
		try {
			task.get(100, TimeUnit.MILLISECONDS);
			fail("Task ran during manual pause");
		} catch (TimeoutException expected) {
			assertFalse(task.isDone());
		}
	}
}
