package net.runelite.client.plugins.microbot.shortestpath;

import net.runelite.client.input.KeyManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class ShortestPathPluginTeardownTest {

    private ExecutorService originalExecutor;

    @Before
    public void saveStaticState() {
        originalExecutor = ShortestPathPlugin.getPathfindingExecutor();
    }

    @After
    public void restoreStaticState() {
        ShortestPathPlugin.setPathfindingExecutor(originalExecutor);
        ShortestPathPlugin.pathfinderConfig = null;
    }

    @Test
    public void teardownAfterFailedStartUpRunsExitWithoutScript() throws Exception {
        WalkingNoticeOverlay walkingNoticeOverlay = mock(WalkingNoticeOverlay.class);
        ShortestPathPlugin plugin = new ShortestPathPlugin(walkingNoticeOverlay);
        ClientToolbar clientToolbar = mock(ClientToolbar.class);
        setField(plugin, "keyManager", mock(KeyManager.class));
        setField(plugin, "overlayManager", mock(OverlayManager.class));
        setField(plugin, "clientToolbar", clientToolbar);
        ShortestPathPlugin.pathfinderConfig = null;

        ExecutorService executor = Executors.newSingleThreadExecutor();
        ShortestPathPlugin.setPathfindingExecutor(executor);

        assertNull(plugin.getShortestPathScript());

        plugin.shutDown();

        assertTrue(executor.isShutdown());
        assertNull(ShortestPathPlugin.getPathfindingExecutor());
        verify(clientToolbar, never()).removeNavigation(any());
        verify(walkingNoticeOverlay).clear();
    }

    @Test(timeout = 10000)
    public void concurrentExitStopsExecutorOnceAndDoesNotThrow() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger stopCalls = new AtomicInteger();
        ExecutorService executor = mock(ExecutorService.class);
        doAnswer(invocation -> {
            if (stopCalls.incrementAndGet() == 1) {
                entered.countDown();
                release.await();
            }
            return Collections.emptyList();
        }).when(executor).shutdownNow();
        ShortestPathPlugin.setPathfindingExecutor(executor);

        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread first = new Thread(() -> {
            try {
                ShortestPathPlugin.exit();
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        first.start();
        assertTrue(entered.await(5, TimeUnit.SECONDS));

        ShortestPathPlugin.exit();

        release.countDown();
        first.join();

        assertNull(failure.get());
        assertEquals(1, stopCalls.get());
        assertNull(ShortestPathPlugin.getPathfindingExecutor());
    }

    @Test
    public void exitWithoutExecutorIsNoOp() {
        ShortestPathPlugin.setPathfindingExecutor(null);

        ShortestPathPlugin.exit();

        assertNull(ShortestPathPlugin.getPathfindingExecutor());
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = ShortestPathPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
