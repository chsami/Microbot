package net.runelite.client.plugins.microbot.util;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.ApiTestClient;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class GlobalAwaitRegressionTest
{
    @After public void clearInterrupt() { Thread.interrupted(); }

    @Test
    public void humanTakeoverPreventsClientThreadPollingAndTutorialExecution() throws Exception
    {
        try (ApiTestClient env = new ApiTestClient();
             org.mockito.MockedStatic<net.runelite.client.plugins.microbot.util.input.InputArbiter> input =
                     mockStatic(net.runelite.client.plugins.microbot.util.input.InputArbiter.class);
             org.mockito.MockedStatic<net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard> keyboard =
                     mockStatic(net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard.class)) {
            input.when(net.runelite.client.plugins.microbot.util.input.InputArbiter::isHuman).thenReturn(true);
            assertFalse(Global.awaitOnClientThread(() -> { fail("human-owned wait evaluated"); return true; }, 1000));
            assertFalse(new Script() {}.run());
            keyboard.verify(net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard::releaseHeldKeys);
            verify(env.client, never()).getGameState();
        }
    }

    @Test
    public void independentWaitsCompleteAndCancelExactlyOnce() throws Exception
    {
        ScheduledExecutorService original = Global.scheduledExecutorService;
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        List<Runnable> polls = new ArrayList<>();
        List<ScheduledFuture<?>> handles = new ArrayList<>();
        when(scheduler.scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), any()))
                .thenAnswer(i -> {
                    polls.add(i.getArgument(0));
                    ScheduledFuture<?> handle = mock(ScheduledFuture.class);
                    handles.add(handle);
                    return handle;
                });
        Global.scheduledExecutorService = scheduler;
        try {
            AtomicBoolean readyA = new AtomicBoolean();
            AtomicBoolean readyB = new AtomicBoolean();
            AtomicInteger callbacks = new AtomicInteger();
            ScheduledFuture<?> a = Global.awaitExecutionUntil(callbacks::incrementAndGet, readyA::get, 40);
            ScheduledFuture<?> b = Global.awaitExecutionUntil(callbacks::incrementAndGet, readyB::get, 40);
            readyB.set(true);
            polls.get(1).run();
            assertTrue(b.isDone());
            assertFalse(a.isDone());
            verify(handles.get(1)).cancel(false);
            verify(handles.get(0), never()).cancel(anyBoolean());
            readyA.set(true);
            polls.get(0).run();
            polls.get(1).run();
            polls.get(0).run();
            assertEquals(2, callbacks.get());
            a.get(1, TimeUnit.SECONDS);
            b.get(1, TimeUnit.SECONDS);
            ScheduledFuture<?> cancelled = Global.awaitExecutionUntil(callbacks::incrementAndGet, () -> true, 40);
            assertTrue(cancelled.cancel(true));
            polls.get(2).run();
            assertEquals(2, callbacks.get());
            verify(handles.get(2)).cancel(true);
        } finally { Global.scheduledExecutorService = original; }
    }

    @Test
    public void immediateCompletionBeforeHandleAssignmentIsSafe() throws Exception
    {
        ScheduledExecutorService original = Global.scheduledExecutorService;
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> handle = mock(ScheduledFuture.class);
        when(scheduler.scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), any()))
                .thenAnswer(i -> { ((Runnable) i.getArgument(0)).run(); return handle; });
        Global.scheduledExecutorService = scheduler;
        try {
            AtomicInteger calls = new AtomicInteger();
            ScheduledFuture<?> result = Global.awaitExecutionUntil(() -> {
                assertFalse(Thread.currentThread().isInterrupted());
                calls.incrementAndGet();
            }, () -> true, 40);
            result.get(1, TimeUnit.SECONDS);
            assertEquals(1, calls.get());
            verify(handle).cancel(false);
        } finally { Global.scheduledExecutorService = original; }
    }

    @Test
    public void cancellationInterruptsInFlightConditionWithoutRunningCallback() throws Exception {
        ScheduledExecutorService original = Global.scheduledExecutorService;
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        Global.scheduledExecutorService = scheduler;
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch exited = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger callbacks = new AtomicInteger();
        AtomicBoolean interrupted = new AtomicBoolean();
        try {
            ScheduledFuture<?> future = Global.awaitExecutionUntil(callbacks::incrementAndGet, () -> {
                entered.countDown();
                try { release.await(); }
                catch (InterruptedException expected) { interrupted.set(true); Thread.currentThread().interrupt(); }
                finally { exited.countDown(); }
                return true;
            }, 40);
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertTrue(future.cancel(true));
            assertTrue(exited.await(5, TimeUnit.SECONDS));
            assertTrue(interrupted.get());
            assertTrue(future.isCancelled());
            assertEquals(0, callbacks.get());
        } finally {
            release.countDown();
            scheduler.shutdownNow();
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
            Global.scheduledExecutorService = original;
        }
    }

    @Test
    public void clientThreadPollingIsBoundedAndInterruptible() throws Exception
    {
        try (ApiTestClient env = new ApiTestClient()) {
            AtomicInteger calls = new AtomicInteger();
            assertFalse(Global.awaitOnClientThread(() -> {
                env.requireClientThread();
                calls.incrementAndGet();
                return false;
            }, 125));
            assertTrue(calls.get() <= 4);
            assertTrue(Global.awaitOnClientThread(() -> true, 1000));
            Thread.currentThread().interrupt();
            assertFalse(Global.awaitOnClientThread(() -> { fail("interrupted wait evaluated"); return true; }, 1000));
            assertTrue(Thread.interrupted());
            assertFalse(env.call(() -> Global.awaitOnClientThread(() -> true, 1000)));
        }
    }

    @Test
    public void incompleteTutorialCanRunOnlyWhenUnpausedAndNotInterrupted() {
        boolean previous = Microbot.pauseAllScripts.get();
        try (org.mockito.MockedStatic<Microbot> api = mockStatic(Microbot.class);
             org.mockito.MockedStatic<net.runelite.client.plugins.microbot.util.player.Rs2Player> player =
                     mockStatic(net.runelite.client.plugins.microbot.util.player.Rs2Player.class);
             org.mockito.MockedStatic<net.runelite.client.plugins.microbot.util.antiban.SessionFatigue> fatigue =
                     mockStatic(net.runelite.client.plugins.microbot.util.antiban.SessionFatigue.class)) {
            api.when(Microbot::isLoggedIn).thenReturn(true);
            Microbot.pauseAllScripts.set(false);
            Script script = new Script() {};
            assertTrue(script.run());
            Microbot.pauseAllScripts.set(true);
            assertFalse(script.run());
            Microbot.pauseAllScripts.set(false);
            Thread.currentThread().interrupt();
            assertFalse(script.run());
            assertTrue(Thread.interrupted());
        } finally { Microbot.pauseAllScripts.set(previous); }
    }

    @Test
    public void pauseAndInterruptionPrecedeTutorialAndGameReads() throws Exception
    {
        boolean previous = Microbot.pauseAllScripts.get();
        try (ApiTestClient env = new ApiTestClient()) {
            Script script = new Script() {};
            Microbot.pauseAllScripts.set(true);
            assertFalse(script.run());
            Microbot.pauseAllScripts.set(false);
            Thread.currentThread().interrupt();
            assertFalse(script.run());
            assertTrue(Thread.interrupted());
            verify(env.client, never()).getGameState();
        } finally { Microbot.pauseAllScripts.set(previous); }
    }
}
