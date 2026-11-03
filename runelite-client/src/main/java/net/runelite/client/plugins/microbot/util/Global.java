package net.runelite.client.plugins.microbot.util;

import lombok.SneakyThrows;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.ScriptPauseController;
import net.runelite.client.plugins.microbot.util.antiban.SessionFatigue;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

public class Global {
    static ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(10);

    /**
     * Manual pause parks background waits without losing their stack or timeout budget.
     * Automatic human takeover remains terminal, as before.
     *
     * <p>Deliberately not terminal on {@code pauseAllScripts}. That flag doubles as a transient
     * guard a script raises around its own action sequence and then keeps waiting inside; see
     * {@code Rs2GroundItem.runWhilePaused}. Making these waits no-ops there would break it.
     */
    private static boolean humanOwnsInput() {
        return !ScriptPauseController.awaitResume() || InputArbiter.isHuman();
    }

    private static long activeTimeMillis() {
        return TimeUnit.NANOSECONDS.toMillis(ScriptPauseController.activeTimeNanos());
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

    /**
     * Polls a condition off-thread and runs the callback once it holds. Stops without running the
     * callback if the human takes over.
     *
     * <p>The future is held per call, not in a static field: two concurrent callers raced on that
     * and could cancel each other. Cancellation is non-interrupting because the task cancels
     * itself, and {@code cancel(true)} would leave an interrupt flag on a pooled thread.
     */
    public static ScheduledFuture<?> awaitExecutionUntil(Runnable callback, BooleanSupplier awaitedCondition, int time) {
        final AtomicReference<ScheduledFuture<?>> holder = new AtomicReference<>();
        final AtomicBoolean finished = new AtomicBoolean();

        Runnable poll = () -> {
            if (finished.get() || ScriptPauseController.isPaused()) return;
            boolean human = humanOwnsInput();
            if (!human && !awaitedCondition.getAsBoolean()) return;
            if (!finished.compareAndSet(false, true)) return;
            cancelQuietly(holder);
            if (!human) callback.run();
        };

        ScheduledFuture<?> future = scheduledExecutorService.scheduleWithFixedDelay(poll, 0, time, TimeUnit.MILLISECONDS);
        holder.set(future);
        // The first poll runs with zero initial delay, so it can finish before the line above.
        if (finished.get()) cancelQuietly(holder);
        return future;
    }

    private static void cancelQuietly(AtomicReference<ScheduledFuture<?>> holder) {
        ScheduledFuture<?> future = holder.get();
        if (future != null) future.cancel(false);
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
            // A pause can begin during the last slice, including a short single-slice wait.
            if (humanOwnsInput()) return;
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
        final long endTime = activeTimeMillis()+timeoutMillis;
        do {
            if (Thread.currentThread().isInterrupted() || humanOwnsInput()) {
                return null;
            }
            methodResponse = method.call();
            done = methodResponse != null;
            sleep(sleepMillis);
        } while (!done && !Thread.currentThread().isInterrupted() && activeTimeMillis() < endTime);
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
        long startTime = activeTimeMillis();
        try {
            while (!Thread.currentThread().isInterrupted() && activeTimeMillis() - startTime < time) {
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
        long startTime = ScriptPauseController.activeTimeNanos();
        long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        try {
            while (!Thread.currentThread().isInterrupted() && ScriptPauseController.activeTimeNanos() - startTime < timeoutNanos) {
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
        long startTime = activeTimeMillis();
        try {
            do {
                if (Thread.currentThread().isInterrupted() || humanOwnsInput()) {
                    return false;
                }
                if (awaitedCondition.getAsBoolean()) {
                    return true;
                }
                sleep(nextPollIntervalMs());
            } while (!Thread.currentThread().isInterrupted() && activeTimeMillis() - startTime < 5000);
        } catch (Exception e) {
            Microbot.logStackTrace("Global Sleep: ", e);
        }
        return false;
    }

    public static boolean sleepUntilTrue(BooleanSupplier awaitedCondition, int time, int timeout) {
        if (Microbot.getClient().isClientThread()) return false;
        long startTime = activeTimeMillis();
        try {
            do {
                if (Thread.currentThread().isInterrupted() || humanOwnsInput()) {
                    return false;
                }
                if (awaitedCondition.getAsBoolean()) {
                    return true;
                }
                sleep(time);
            } while (!Thread.currentThread().isInterrupted() && activeTimeMillis() - startTime < timeout);
        } catch (Exception e) {
            Microbot.logStackTrace("Global Sleep: ", e);
        }
        return false;
    }

    public static boolean sleepUntilTrue(BooleanSupplier awaitedCondition, BooleanSupplier resetCondition, int time, int timeout) {
        if (Microbot.getClient().isClientThread()) return false;
        long startTime = activeTimeMillis();
        try {
            do {
                if (Thread.currentThread().isInterrupted() || humanOwnsInput()) {
                    return false;
                }
                if (resetCondition.getAsBoolean()) {
                    startTime = activeTimeMillis();
                }
                if (awaitedCondition.getAsBoolean()) {
                    return true;
                }
                sleep(time);
            } while (!Thread.currentThread().isInterrupted() && activeTimeMillis() - startTime < timeout);
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
        if (Microbot.getClient().isClientThread()) return;
        boolean done;
        long startTime = activeTimeMillis();
        try {
            do {
                // Never calls sleep(): it spins on the client-thread round trip.
                if (Thread.currentThread().isInterrupted() || humanOwnsInput()) {
                    return;
                }
                done = Microbot.getClientThread().runOnClientThreadOptional(awaitedCondition::getAsBoolean).orElse(false);
            } while (!done && !Thread.currentThread().isInterrupted() && activeTimeMillis() - startTime < time);
        } catch (Exception e) {
            Microbot.logStackTrace("Global Sleep: ", e);
        }
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
            while (!Thread.currentThread().isInterrupted()) {
                long generation = ScriptPauseController.getGeneration();
                broadcaster.awaitNextTick();
                if (humanOwnsInput()) return false;
                // A tick consumed during pause is not a fresh tick after resume.
                if (generation == ScriptPauseController.getGeneration()) return true;
            }
            return false;
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
            while (!Thread.currentThread().isInterrupted()) {
                long generation = ScriptPauseController.getGeneration();
                boolean tick = broadcaster.awaitNextTick(timeoutMs);
                if (humanOwnsInput()) return false;
                // A tick/timeout during pause is not a fresh tick after resume.
                if (generation == ScriptPauseController.getGeneration()) return tick;
            }
            return false;
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
