package net.runelite.client.plugins.microbot.util.camera;

import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.awt.event.KeyEvent;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class Rs2CameraSettleTest {

    private static final int FULL_TURN = 16384;
    private static final int TICK_MS = 20;
    private static final int HELD_SPEED = 160;
    private static final int COAST_DECAY = 20;
    private static final int WINDOW_SKIPPING_SPEED = 1200;
    private static final int MAX_POLL_MS = 320;
    private static final int SCHEDULING_SLACK_MS = 1000;

    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Keyboard> keyboard;
    private ScheduledExecutorService ticker;
    private Client client;
    private ClientThread clientThread;

    private final AtomicInteger cycle = new AtomicInteger();
    private final AtomicInteger yaw = new AtomicInteger();
    private final AtomicInteger pitch = new AtomicInteger(2048);
    private final AtomicInteger velocity = new AtomicInteger();
    private final AtomicInteger heldKey = new AtomicInteger(-1);
    private final AtomicInteger heldSpeed = new AtomicInteger(HELD_SPEED);
    private final AtomicInteger coastDecay = new AtomicInteger(COAST_DECAY);
    private final AtomicLong travelled = new AtomicLong();
    private final AtomicBoolean spinForever = new AtomicBoolean();
    private final AtomicBoolean onClientThread = new AtomicBoolean();
    private final AtomicBoolean isClientThread = new AtomicBoolean();

    @Before
    public void setUp() {
        client = mock(Client.class);
        when(client.isClientThread()).thenAnswer(inv -> isClientThread.get());
        when(client.getCameraYaw()).thenAnswer(inv -> onClientThread(yaw));
        when(client.getCameraPitch()).thenAnswer(inv -> onClientThread(pitch));
        when(client.getGameCycle()).thenAnswer(inv -> onClientThread(cycle));

        clientThread = mock(ClientThread.class);
        when(clientThread.runOnClientThreadOptional(any())).thenAnswer(inv -> {
            Callable<?> callable = inv.getArgument(0);
            onClientThread.set(true);
            try {
                return Optional.ofNullable(callable.call());
            } finally {
                onClientThread.set(false);
            }
        });

        microbot = Mockito.mockStatic(Microbot.class);
        microbot.when(Microbot::getClient).thenReturn(client);
        microbot.when(Microbot::getClientThread).thenReturn(clientThread);

        keyboard = Mockito.mockStatic(Rs2Keyboard.class);
        keyboard.when(() -> Rs2Keyboard.keyHold(anyInt())).thenAnswer(inv -> {
            heldKey.set(inv.getArgument(0));
            return null;
        });
        keyboard.when(() -> Rs2Keyboard.keyRelease(anyInt())).thenAnswer(inv -> {
            heldKey.compareAndSet(inv.getArgument(0), -1);
            return null;
        });

        ticker = Executors.newSingleThreadScheduledExecutor();
        ticker.scheduleAtFixedRate(this::tick, TICK_MS, TICK_MS, TimeUnit.MILLISECONDS);
    }

    @After
    public void tearDown() {
        ticker.shutdownNow();
        keyboard.close();
        microbot.close();
    }

    private int onClientThread(AtomicInteger value) {
        if (!onClientThread.get()) {
            throw new IllegalStateException("camera state must be read on the client thread");
        }
        return value.get();
    }

    private void tick() {
        int key = heldKey.get();
        int speed = heldSpeed.get();
        int decay = coastDecay.get();
        int v;
        if (spinForever.get()) {
            v = speed;
        } else if (key == KeyEvent.VK_LEFT) {
            v = -speed;
        } else if (key == KeyEvent.VK_RIGHT) {
            v = speed;
        } else {
            int current = velocity.get();
            v = current > 0 ? Math.max(0, current - decay) : Math.min(0, current + decay);
        }
        velocity.set(v);
        travelled.addAndGet(Math.abs(v));
        yaw.set(Math.floorMod(yaw.get() + v, FULL_TURN));
        cycle.incrementAndGet();
    }

    @Test
    public void setAngleReturnsOnlyAfterYawStopsCoasting() throws Exception {
        Rs2Camera.setAngle(90, 10);

        int yawAtReturn = yaw.get();
        assertEquals("camera must be at rest when setAngle returns", 0, velocity.get());
        Thread.sleep(200);
        assertEquals("yaw kept changing after setAngle returned", yawAtReturn, yaw.get());
        assertTrue("turn did not reach the target window", Rs2Camera.isAngleGood(90, 40));
    }

    @Test
    public void rightTurnStopsOnceItPassesANarrowWindow() {
        heldSpeed.set(WINDOW_SKIPPING_SPEED);
        coastDecay.set(WINDOW_SKIPPING_SPEED);

        Rs2Camera.setAngle(90, 5);

        assertEquals(-1, heldKey.get());
        assertTrue("camera turned " + travelled.get() + " units", travelled.get() < FULL_TURN);
        assertTrue(Rs2Camera.getAngleTo(90) > 0);
    }

    @Test
    public void leftTurnStopsOnceItPassesANarrowWindow() {
        heldSpeed.set(WINDOW_SKIPPING_SPEED);
        coastDecay.set(WINDOW_SKIPPING_SPEED);

        Rs2Camera.setAngle(270, 5);

        assertEquals(-1, heldKey.get());
        assertTrue("camera turned " + travelled.get() + " units", travelled.get() < FULL_TURN);
        assertTrue(Rs2Camera.getAngleTo(270) < 0);
    }

    @Test
    public void settleWaitIsBoundedWhenCameraNeverStops() {
        spinForever.set(true);

        long start = System.nanoTime();
        boolean settled = Rs2Camera.awaitCameraSettled();
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertFalse(settled);
        assertTrue("settle wait ran " + elapsedMs + "ms", elapsedMs < Rs2Camera.SETTLE_TIMEOUT_MS + MAX_POLL_MS + SCHEDULING_SLACK_MS);
    }

    @Test
    public void failedClientThreadReadFallsBackToTheLiveCameraAngles() {
        doReturn(Optional.empty()).when(clientThread).runOnClientThreadOptional(any());
        doReturn(Rs2Camera.toClientAngleUnits(1536)).when(client).getCameraYaw();
        doReturn(Rs2Camera.toClientAngleUnits(383)).when(client).getCameraPitch();

        assertEquals(1536, Rs2Camera.getYaw());
        assertEquals(383, Rs2Camera.getPitch());
    }

    @Test
    public void adjustPitchLeavesTheKeysAloneWhenAlreadyAtTarget() {
        Rs2Camera.adjustPitch(Rs2Camera.cameraPitchPercentage());

        keyboard.verify(() -> Rs2Keyboard.keyHold(anyInt()), never());
        keyboard.verify(() -> Rs2Keyboard.keyRelease(anyInt()), never());
    }

    @Test
    public void settleWaitNeverRunsOnTheClientThread() {
        isClientThread.set(true);

        assertFalse(Rs2Camera.awaitCameraSettled());
        verify(clientThread, never()).runOnClientThreadOptional(any());
    }
}
