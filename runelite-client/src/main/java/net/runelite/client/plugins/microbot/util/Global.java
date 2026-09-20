package net.runelite.client.plugins.microbot.util;

import lombok.SneakyThrows;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.antiban.SessionFatigue;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

public class Global {
    static ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(10);

    /**
     * Every wait here treats a human takeover as terminal.
     *
     * <p>Deliberately not terminal on {@code pauseAllScripts}. That flag doubles as a transient
     * guard a script raises around its own action sequence and then keeps waiting inside; see
     * {@code Rs2GroundItem.runWhilePaused}. Making these waits no-ops there would break it.
     */
    private static boolean humanOwnsInput() {
        return InputArbiter.isHuman();
    }

    private static final int SLEEP_SLICE_MS = 50;

    private static final int POLL_MIN_MS = 40;
    private static final int POLL_MAX_MS = 320;
    private static final double POLL_LOG_MEAN = 4.41;
    private static final double POLL_LOG_SIGMA = 0.22;

    static int nextPollIntervalMs() {
        double gaussian = ThreadLocalRandom.current().nextGaussian();
        double sample = Math.exp(POLL_LOG_MEAN + POLL_LOG_SIGMA * gaussian);
        if (sample < POLL_MIN_MS) return POLL_MIN_MS;
        if (sample > POLL_MAX_MS) return POLL_MAX_MS;
        return (int) sample;
    }

    /** Each request owns its cancellation, yields to human input, and completes after its callback returns. */
    public static ScheduledFuture<?> awaitExecutionUntil(Runnable callback, BooleanSupplier awaitedCondition, int time) {
        if (time <= 0) throw new IllegalArgumentException("Polling interval must be positive");
        AwaitTask task = new AwaitTask(callback, java.util.Objects.requireNonNull(awaitedCondition));
        task.attach(scheduledExecutorService.scheduleWithFixedDelay(task, 0, time, TimeUnit.MILLISECONDS));
        return task;
    }

    private static final class AwaitTask extends FutureTask<Void> implements ScheduledFuture<Void> {
        private final BooleanSupplier condition;
        private volatile ScheduledFuture<?> polling;
        private volatile boolean interruptPolling;

        private AwaitTask(Runnable callback, BooleanSupplier condition) {
            super(java.util.Objects.requireNonNull(callback), null);
            this.condition = condition;
        }

        private void attach(ScheduledFuture<?> future) {
            polling = future;
            // The scheduler may have completed the request before returning its handle.
            if (isDone()) future.cancel(interruptPolling);
        }

        @Override public void run() {
            if (isDone()) return;
            if (humanOwnsInput()) { cancel(false); return; }
            try {
                if (condition.getAsBoolean()) {
                    if (humanOwnsInput()) cancel(false);
                    else super.run();
                }
            } catch (Throwable failure) {
                setException(failure);
            }
        }

        @Override protected void done() {
            ScheduledFuture<?> future = polling;
            if (future != null) future.cancel(interruptPolling);
        }

        @Override public boolean cancel(boolean mayInterruptIfRunning) {
            interruptPolling = mayInterruptIfRunning;
            return super.cancel(mayInterruptIfRunning);
        }

        @Override public long getDelay(TimeUnit unit) {
            ScheduledFuture<?> future = polling;
            return future == null ? 0 : future.getDelay(unit);
        }

        @Override public int compareTo(Delayed other) {
            return Long.compare(getDelay(TimeUnit.NANOSECONDS), other.getDelay(TimeUnit.NANOSECONDS));
        }
    }


    /** Sliced so a takeover cuts the remainder short. Every fixed-sleep wrapper funnels here. */
    public static void sleep(int start) {
        if (Microbot.getClient().isClientThread()) return;
        long remaining = start;
        while (remaining > 0) {
            if (humanOwnsInput()) return;
            long slice = Math.min(remaining, SLEEP_SLICE_MS);
            try {
                Thread.sleep(slice);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
            remaining -= slice;
        }
    }

    public static void sleep(int start, int end) {
        int randomSleep = Rs2Random.between(start, end);
        sleep(randomSleep);
    }

    public static void sleepGaussian(int mean, int stddev) {
        int randomSleep = Rs2Random.randomGaussian(mean, stddev);
        sleep(randomSleep);
    }

    public static void sleepFatigued(int ms) {
        sleep(SessionFatigue.applyTo(ms));
    }

    public static void sleepFatigued(int start, int end) {
        sleep(SessionFatigue.applyTo(Rs2Random.between(start, end)));
    }

    public static void sleepGaussianFatigued(int mean, int stddev) {
        sleep(SessionFatigue.applyTo(Rs2Random.randomGaussian(mean, stddev)));
    }

    private static final int TICK_MS = 600;
    private static final int TICK_JITTER_SIGMA_MS = 80;

    static int nextTickJitterMs(int ticks) {
        int base = Math.max(0, ticks * TICK_MS);
        double g = ThreadLocalRandom.current().nextGaussian();
        int sample = (int) Math.round(base + g * TICK_JITTER_SIGMA_MS);
        int floor = Math.max(100, base - 3 * TICK_JITTER_SIGMA_MS);
        int ceil = base + 3 * TICK_JITTER_SIGMA_MS;
        if (sample < floor) return floor;
        if (sample > ceil) return ceil;
        return sample;
    }

    public static void sleepTickJitter(int ticks) {
        sleep(nextTickJitterMs(ticks));
    }

    public static void sleepTickJitterFatigued(int ticks) {
        sleep(SessionFatigue.applyTo(nextTickJitterMs(ticks)));
    }

    @SneakyThrows
    public static <T> T sleepUntilNotNull(Callable<T> method, int timeoutMillis, int sleepMillis) {
        if (Microbot.getClient().isClientThread()) return null;
        boolean done;
        T methodResponse;
        final long endTime = System.currentTimeMillis()+timeoutMillis;
        do {
            if (Thread.currentThread().isInterrupted() || humanOwnsInput()) {
                return null;
            }
            methodResponse = method.call();
            done = methodResponse != null;
            sleep(sleepMillis);
        } while (!done && !Thread.currentThread().isInterrupted() && System.currentTimeMillis() < endTime);
        return methodResponse;
    }

    public static <T> T sleepUntilNotNull(Callable<T> method, int timeoutMillis) {
        return sleepUntilNotNull(method, timeoutMillis, 100);
    }

    /**
     * Polls until the supplied condition becomes true or timeout elapses.
     * Must not be invoked on the client thread; callers should run on script/executor threads.
     */
    public static boolean sleepUntil(BooleanSupplier awaitedCondition) {
        return sleepUntil(awaitedCondition, 5000);
    }

    /**
     * Polls until the supplied condition becomes true or the given duration elapses.
     * No-op on the client thread to avoid blocking RuneLite.
     */
    public static boolean sleepUntil(BooleanSupplier awaitedCondition, int time) {
        if (Microbot.getClient().isClientThread()) return false;
        long startTime = System.currentTimeMillis();
        try {
            while (!Thread.currentThread().isInterrupted() && System.currentTimeMillis() - startTime < time) {
                if (humanOwnsInput()) return false;
                if (awaitedCondition.getAsBoolean()) return true;
                sleep(nextPollIntervalMs());
            }
        } catch (Exception e) {
            Microbot.logStackTrace("Global Sleep: ", e);
        }
        return false;
    }

    public static boolean sleepUntil(BooleanSupplier awaitedCondition, Runnable action, long timeoutMillis, int sleepMillis) {
        if (Microbot.getClient().isClientThread()) return false;
        long startTime = System.nanoTime();
        long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        try {
            while (!Thread.currentThread().isInterrupted() && System.nanoTime() - startTime < timeoutNanos) {
                if (humanOwnsInput()) return false;
                if (awaitedCondition.getAsBoolean()) {
                    return true;
                }
				action.run();
                sleep(sleepMillis);
            }
        } catch (Exception e) {
            Microbot.logStackTrace("Global Sleep: ", e);
        }
        return false;
    }

    public static boolean sleepUntilTrue(BooleanSupplier awaitedCondition) {
        if (Microbot.getClient().isClientThread()) return false;
        long startTime = System.currentTimeMillis();
        try {
            do {
                if (Thread.currentThread().isInterrupted() || humanOwnsInput()) {
                    return false;
                }
                if (awaitedCondition.getAsBoolean()) {
                    return true;
                }
                sleep(nextPollIntervalMs());
            } while (!Thread.currentThread().isInterrupted() && System.currentTimeMillis() - startTime < 5000);
        } catch (Exception e) {
            Microbot.logStackTrace("Global Sleep: ", e);
        }
        return false;
    }

    public static boolean sleepUntilTrue(BooleanSupplier awaitedCondition, int time, int timeout) {
        if (Microbot.getClient().isClientThread()) return false;
        long startTime = System.currentTimeMillis();
        try {
            do {
                if (Thread.currentThread().isInterrupted() || humanOwnsInput()) {
                    return false;
                }
                if (awaitedCondition.getAsBoolean()) {
                    return true;
                }
                sleep(time);
            } while (!Thread.currentThread().isInterrupted() && System.currentTimeMillis() - startTime < timeout);
        } catch (Exception e) {
            Microbot.logStackTrace("Global Sleep: ", e);
        }
        return false;
    }

    public static boolean sleepUntilTrue(BooleanSupplier awaitedCondition, BooleanSupplier resetCondition, int time, int timeout) {
        if (Microbot.getClient().isClientThread()) return false;
        long startTime = System.currentTimeMillis();
        try {
            do {
                if (Thread.currentThread().isInterrupted() || humanOwnsInput()) {
                    return false;
                }
                if (resetCondition.getAsBoolean()) {
                    startTime = System.currentTimeMillis();
                }
                if (awaitedCondition.getAsBoolean()) {
                    return true;
                }
                sleep(time);
            } while (!Thread.currentThread().isInterrupted() && System.currentTimeMillis() - startTime < timeout);
        } catch (Exception e) {
            Microbot.logStackTrace("Global Sleep: ", e);
        }
        return false;
    }

    /**
     * Polls a condition on the client thread until true or timeout, without blocking the client thread itself.
     */
    public static void sleepUntilOnClientThread(BooleanSupplier awaitedCondition) {
        sleepUntilOnClientThread(awaitedCondition, Rs2Random.between(2500, 5000));
    }

    public static void sleepUntilOnClientThread(BooleanSupplier awaitedCondition, int time) {
        awaitOnClientThread(awaitedCondition, time);
    }

    /**
     * True only if observed before timeout. False on interruption or client-thread misuse.
     * The condition must be short and nonblocking; the caller polls off-thread at most every 40ms.
     */
    public static boolean awaitOnClientThread(BooleanSupplier awaitedCondition, int timeoutMillis) {
        if (Microbot.getClient().isClientThread() || timeoutMillis <= 0) return false;
        long start = System.nanoTime();
        long duration = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (!Thread.currentThread().isInterrupted() && !humanOwnsInput() && System.nanoTime() - start < duration) {
            boolean done = Microbot.getClientThread()
                    .runOnClientThreadOptional(awaitedCondition::getAsBoolean).orElse(false);
            if (Thread.currentThread().isInterrupted() || humanOwnsInput()) return false;
            if (done) return System.nanoTime() - start < duration;
            long remaining = duration - (System.nanoTime() - start);
            if (remaining <= 0) return false;
            try {
                TimeUnit.NANOSECONDS.sleep(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(POLL_MIN_MS)));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    @Deprecated
    public boolean sleepUntilTick(int ticksToWait) {
        return sleepTicks(ticksToWait);
    }

    /**
     * The one wait that cannot be immediate: it blocks on a {@code CountDownLatch} released by the
     * GameTick event, so a takeover only surfaces at the next tick or the latch timeout. Checked
     * either side of the await.
     */
    public static boolean sleepUntilNextTick() {
        if (Microbot.getClient().isClientThread()) return false;
        if (humanOwnsInput()) return false;
        GameTickBroadcaster broadcaster = Microbot.getGameTickBroadcaster();
        if (broadcaster == null) return false;
        try {
            return broadcaster.awaitNextTick() && !humanOwnsInput();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public static boolean sleepUntilNextTick(long timeoutMs) {
        if (Microbot.getClient().isClientThread()) return false;
        if (humanOwnsInput()) return false;
        GameTickBroadcaster broadcaster = Microbot.getGameTickBroadcaster();
        if (broadcaster == null) return false;
        try {
            return broadcaster.awaitNextTick(timeoutMs) && !humanOwnsInput();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public static boolean sleepTicks(int ticks) {
        if (Microbot.getClient().isClientThread()) return false;
        for (int i = 0; i < ticks; i++) {
            if (!sleepUntilNextTick()) {
                return false;
            }
        }
        return true;
    }
}
