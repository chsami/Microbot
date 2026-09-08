package net.runelite.client.plugins.microbot.shortestpath;

import java.awt.Canvas;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.Keybind;
import net.runelite.client.plugins.microbot.util.walker.WalkerState;
import net.runelite.client.util.HotkeyListener;
import org.junit.Test;

import static org.junit.Assert.*;

public class ShortestPathManualWalkingTest {
    private static final WorldPoint FIRST = new WorldPoint(3200, 3200, 0);
    private static final WorldPoint SECOND = new WorldPoint(3210, 3210, 0);

    @Test
    public void disabledSelectionOnlyPreviewsAndClearKeepsPreference() throws Exception {
        List<WorldPoint> previews = new ArrayList<>();
        try (Harness script = new Harness(previews)) {
            script.toggleWalking();
            script.setTriggerWalker(FIRST);
            script.flushPreview();
            script.startWalkTask();
            assertEquals(List.of(FIRST), previews);
            assertEquals(FIRST, script.getTriggerWalker());
            assertTrue(script.started.isEmpty());
            script.setTriggerWalker(null);
            assertNull(script.getTriggerWalker());
            assertFalse(script.isWalkingEnabled());
            assertEquals(1, script.clears.get());
        }
    }

    @Test
    public void cancelledWorkerCannotClearReplacementOrStartOverlappingWalk() throws Exception {
        List<WorldPoint> previews = new ArrayList<>();
        try (Harness script = new Harness(previews)) {
            script.setTriggerWalker(FIRST);
            script.flushPreview();
            script.startWalkTask();
            assertEquals(FIRST, script.started.poll(5, TimeUnit.SECONDS));
            script.toggleWalking();
            assertTrue(script.interrupted.await(5, TimeUnit.SECONDS));
            script.setTriggerWalker(SECOND);
            script.toggleWalking();
            script.startWalkTask();
            assertTrue(script.started.isEmpty());
            script.release.release();
            script.flushPreview();
            assertEquals(SECOND, script.getTriggerWalker());
            assertEquals(List.of(FIRST, SECOND), previews);
            script.startWalkTask();
            assertEquals(SECOND, script.started.poll(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void pausedWalkRestoresPreviewWithoutRestarting() throws Exception {
        List<WorldPoint> previews = new ArrayList<>();
        try (Harness script = new Harness(previews)) {
            script.setTriggerWalker(FIRST);
            script.flushPreview();
            script.startWalkTask();
            assertEquals(FIRST, script.started.poll(5, TimeUnit.SECONDS));
            script.toggleWalking();
            script.release.release();
            script.flushPreview();
            script.startWalkTask();
            assertEquals(FIRST, script.getTriggerWalker());
            assertEquals(List.of(FIRST, FIRST), previews);
            assertTrue(script.started.isEmpty());
        }
    }

    @Test
    public void clearInvalidatesQueuedPreview() throws Exception {
        List<WorldPoint> previews = new ArrayList<>();
        try (Harness script = new Harness(previews)) {
            script.toggleWalking();
            script.setTriggerWalker(FIRST);
            script.setTriggerWalker(null);
            script.flushPreview();
            assertTrue(previews.isEmpty());
            assertNull(script.getTriggerWalker());
        }
    }

    @Test
    public void hotkeyDefaultsAndHeldKeyOnlyToggleOnce() {
        ShortestPathConfig config = new ShortestPathConfig() {};
        assertEquals(Keybind.NOT_SET, config.toggleWalkingHotkey());
        AtomicInteger presses = new AtomicInteger();
        HotkeyListener listener = new HotkeyListener(config::clearCurrentPathHotkey) {
            @Override
            public void hotkeyPressed() {
                presses.incrementAndGet();
            }
        };
        Canvas source = new Canvas();
        for (int i = 0; i < 3; i++) {
            listener.keyPressed(keyEvent(source, KeyEvent.KEY_PRESSED));
        }
        assertEquals(1, presses.get());
        listener.keyReleased(keyEvent(source, KeyEvent.KEY_RELEASED));
        listener.keyPressed(keyEvent(source, KeyEvent.KEY_PRESSED));
        assertEquals(2, presses.get());
    }

    @Test
    public void noticesFollowUserActionsWithoutPreviewDuplicates() throws Exception {
        List<WorldPoint> previews = new ArrayList<>();
        List<ShortestPathScript.ManualWalkingNotice> notices = new ArrayList<>();
        try (Harness script = new Harness(previews, notices)) {
            script.toggleWalking();
            script.setTriggerWalker(FIRST);
            script.flushPreview();
            script.startWalkTask();
            script.toggleWalking();
            script.flushPreview();
            script.setTriggerWalker(null);
            assertEquals(List.of(ShortestPathScript.ManualWalkingNotice.PAUSED,
                    ShortestPathScript.ManualWalkingNotice.PATH_SET,
                    ShortestPathScript.ManualWalkingNotice.ENABLED,
                    ShortestPathScript.ManualWalkingNotice.CLEARED), notices);
        }
    }

    @Test
    public void pathNoticeUsesConfiguredKeyOrPanelFallback() {
        String keyed = WalkingNoticeOverlay.messageFor(ShortestPathScript.ManualWalkingNotice.PATH_SET,
                new Keybind(KeyEvent.VK_F6, 0));
        assertTrue(keyed.contains("[F6]"));
        String unbound = WalkingNoticeOverlay.messageFor(ShortestPathScript.ManualWalkingNotice.PATH_SET,
                Keybind.NOT_SET);
        assertTrue(unbound.contains("Automatic walking button"));
        assertFalse(unbound.contains("Not set"));
    }

    private static KeyEvent keyEvent(Canvas source, int id) {
        // Synthetic AWT events do not populate the native extended key code.
        return new KeyEvent(source, id, 0, InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_X, 'x') {
            @Override
            public int getExtendedKeyCode() {
                return KeyEvent.VK_X;
            }
        };
    }

    private static final class Harness extends ShortestPathScript implements AutoCloseable {
        final BlockingQueue<Runnable> clientTasks = new LinkedBlockingQueue<>();
        final BlockingQueue<WorldPoint> started = new LinkedBlockingQueue<>();
        final Semaphore release = new Semaphore(0);
        final CountDownLatch interrupted = new CountDownLatch(1);
        final AtomicInteger clears = new AtomicInteger();

        Harness(List<WorldPoint> previews) {
            this(previews, new ArrayList<>());
        }

        Harness(List<WorldPoint> previews, List<ManualWalkingNotice> notices) {
            super(previews::add, notices::add);
        }

        @Override
        boolean loggedIn() {
            return true;
        }

        @Override
        WalkerState executeWalk(WorldPoint target) {
            started.add(target);
            boolean waiting = true;
            while (waiting) {
                try {
                    waiting = !release.tryAcquire(5, TimeUnit.SECONDS);
                    if (waiting) {
                        throw new AssertionError("Walk was not released");
                    }
                } catch (InterruptedException ex) {
                    interrupted.countDown();
                }
            }
            return WalkerState.ARRIVED;
        }

        @Override
        void clearRoute(String reason) {
            clears.incrementAndGet();
        }

        @Override
        void onClientThread(Runnable action) {
            clientTasks.add(action);
        }

        void flushPreview() throws Exception {
            Runnable action = clientTasks.poll(5, TimeUnit.SECONDS);
            assertNotNull("Expected a preview callback", action);
            action.run();
        }

        @Override
        public void close() throws Exception {
            setTriggerWalker(null);
            release.release(10);
            scheduledExecutorService.shutdown();
            assertTrue(scheduledExecutorService.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
