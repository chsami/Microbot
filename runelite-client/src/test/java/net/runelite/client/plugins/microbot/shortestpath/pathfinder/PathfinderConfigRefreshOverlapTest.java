package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathConfig;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PathfinderConfigRefreshOverlapTest {

    private static final long TIMEOUT_SECONDS = 10L;

    @Test
    public void clientThreadRefreshIsSkippedWhileAnotherRefreshRuns() throws Exception {
        Thread clientThread = Thread.currentThread();
        Client client = mock(Client.class);
        when(client.isClientThread()).thenAnswer(invocation -> Thread.currentThread() == clientThread);
        CountDownLatch scriptRefreshEntered = new CountDownLatch(1);
        CountDownLatch releaseScriptRefresh = new CountDownLatch(1);
        AtomicInteger refreshBodies = new AtomicInteger();
        ShortestPathConfig shortestPathConfig = mock(ShortestPathConfig.class);
        when(shortestPathConfig.calculationCutoff()).thenAnswer(invocation -> {
            refreshBodies.incrementAndGet();
            if (Thread.currentThread() != clientThread) {
                scriptRefreshEntered.countDown();
                releaseScriptRefresh.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
            return 5;
        });
        PathfinderConfig config = new PathfinderConfig(
                null, new HashMap<>(), Collections.emptyList(), client, shortestPathConfig);

        Thread scriptThread = new Thread(config::refresh, "refresh-overlap-script");
        scriptThread.start();
        try {
            assertTrue(scriptRefreshEntered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

            assertFalse(config.tryRefresh());
            assertEquals(1, refreshBodies.get());
        } finally {
            releaseScriptRefresh.countDown();
            scriptThread.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
        }

        assertTrue(config.tryRefresh());
        assertEquals(2, refreshBodies.get());
    }

    @Test
    public void scriptThreadRefreshWaitsForRunningRefresh() throws Exception {
        Client client = mock(Client.class);
        CountDownLatch firstRefreshEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstRefresh = new CountDownLatch(1);
        AtomicInteger activeRefreshBodies = new AtomicInteger();
        AtomicInteger maxActiveRefreshBodies = new AtomicInteger();
        ShortestPathConfig shortestPathConfig = mock(ShortestPathConfig.class);
        when(shortestPathConfig.calculationCutoff()).thenAnswer(invocation -> {
            maxActiveRefreshBodies.accumulateAndGet(activeRefreshBodies.incrementAndGet(), Math::max);
            if (firstRefreshEntered.getCount() > 0) {
                firstRefreshEntered.countDown();
                releaseFirstRefresh.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
            return 5;
        });
        when(shortestPathConfig.useNpcs()).thenAnswer(invocation -> {
            activeRefreshBodies.decrementAndGet();
            return false;
        });
        PathfinderConfig config = new PathfinderConfig(
                null, new HashMap<>(), Collections.emptyList(), client, shortestPathConfig);

        Thread first = new Thread(config::refresh, "refresh-overlap-first");
        Thread second = new Thread(config::refresh, "refresh-overlap-second");
        first.start();
        assertTrue(firstRefreshEntered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        second.start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (second.getState() != Thread.State.WAITING && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertEquals(Thread.State.WAITING, second.getState());
        releaseFirstRefresh.countDown();
        first.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
        second.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));

        assertFalse(first.isAlive());
        assertFalse(second.isAlive());
        assertEquals(1, maxActiveRefreshBodies.get());
    }
}
