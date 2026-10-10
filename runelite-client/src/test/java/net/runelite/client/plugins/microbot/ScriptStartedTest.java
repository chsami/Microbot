package net.runelite.client.plugins.microbot;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.microbot.accountselector.AutoLoginSuppressionRequest;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ScriptStartedTest {
    @Test public void zeroDelaySupplyFailureIsNotClearedByItsOwnStartAnnouncement() throws Exception {
        EventBus bus = new EventBus();
        Block block = new Block();
        bus.register(block);
        Fixture script = new Fixture();
        CountDownLatch failurePosted = new CountDownLatch(1);
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            microbot.when(Microbot::getEventBus).thenReturn(bus);
            ScheduledFuture<?> task = script.scheduleFailure(bus, failurePosted);
            assertTrue(failurePosted.await(2, TimeUnit.SECONDS));
            assertTrue(block.blocked.get());
            task.cancel(false);
        } finally {
            script.scheduledExecutorService.shutdownNow();
            bus.unregister(block);
        }
    }

    @Test public void schedulingAnnouncesStartWithoutWaitingForLoginAndOnlyOncePerRun() {
        EventBus bus = new EventBus();
        Starts starts = new Starts();
        bus.register(starts);
        Fixture script = new Fixture();
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            microbot.when(Microbot::getEventBus).thenReturn(bus);
            ScheduledFuture<?> first = script.schedule(false);
            ScheduledFuture<?> auxiliary = script.schedule(true);
            assertEquals(1, starts.count);
            assertSame(script, starts.last);
            first.cancel(false);
            auxiliary.cancel(false);
            script.shutdown();
            script.schedule(false).cancel(false);
            assertEquals(2, starts.count);
        } finally {
            script.scheduledExecutorService.shutdownNow();
            bus.unregister(starts);
        }
    }

    public static final class Starts {
        int count;
        Script last;
        @Subscribe public void onScriptStarted(ScriptStarted event) {
            count++;
            last = event.getScript();
        }
    }

    public static final class Block {
        final AtomicBoolean blocked = new AtomicBoolean();
        @Subscribe public void onScriptStarted(ScriptStarted event) { blocked.set(false); }
        @Subscribe public void onAutoLoginSuppressionRequest(AutoLoginSuppressionRequest event) { blocked.set(true); }
    }

    private static final class Fixture extends Script {
        ScheduledFuture<?> scheduleFailure(EventBus bus, CountDownLatch posted) {
            return scheduledExecutorService.scheduleWithFixedDelay(() -> {
                bus.post(new AutoLoginSuppressionRequest());
                posted.countDown();
            }, 0, 1, TimeUnit.DAYS);
        }
        ScheduledFuture<?> schedule(boolean fixedRate) {
            return fixedRate
                    ? scheduledExecutorService.scheduleAtFixedRate(() -> { }, 1, 1, TimeUnit.DAYS)
                    : scheduledExecutorService.scheduleWithFixedDelay(() -> { }, 1, 1, TimeUnit.DAYS);
        }
    }
}
