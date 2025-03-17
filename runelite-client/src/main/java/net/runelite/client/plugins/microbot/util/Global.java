package net.runelite.client.plugins.microbot.util;

import com.google.common.base.Stopwatch;
import lombok.SneakyThrows;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

public class Global {
    static ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(10);
    static ScheduledFuture<?> scheduledFuture;

    public static ScheduledFuture<?> awaitExecutionUntil(Runnable callback, BooleanSupplier awaitedCondition, int time) {
        scheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (awaitedCondition.getAsBoolean()) {
                scheduledFuture.cancel(true);
                scheduledFuture = null;
                callback.run();
            }
        }, 0, time, TimeUnit.MILLISECONDS);
        return scheduledFuture;
    }

    /**
     * Sleeps (blocking) for a set number of milliseconds,
     * checking for interruption and returning immediately if interrupted.
     */
    public static void sleep(int time) {
        if (time <= 0) return;
        // Avoid sleeping on the client thread
        if (Microbot.getClient().isClientThread()) return;
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            // Restore the interrupt status and return
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Sleeps for a random duration between start and end (inclusive).
     * Also checks for interruption, returning immediately if interrupted.
     */
    public static void sleep(int start, int end) {
        if (start > end) return;
        int randomSleep = Rs2Random.between(start, end);
        sleep(randomSleep);
    }

    /**
     * Sleeps using a Gaussian-distributed random around mean, with stddev standard deviations.
     * Also checks for interruption, returning if interrupted.
     */
    public static void sleepGaussian(int mean, int stddev) {
        int randomSleep = Rs2Random.randomGaussian(mean, stddev);
        sleep(randomSleep);
    }

    /**
     * Sleep until a condition is true, or 5 seconds is reached, whichever comes first.
     */
    public static boolean sleepUntil(BooleanSupplier awaitedCondition) {
        return sleepUntil(awaitedCondition, 5000);
    }

    /**
     * Sleep until a condition is true or time milliseconds is reached.
     */
    public static boolean sleepUntil(BooleanSupplier awaitedCondition, int time) {
        if (Microbot.getClient().isClientThread()) return false;
        boolean done;
        long startTime = System.currentTimeMillis();

        while (!Thread.currentThread().isInterrupted() && (System.currentTimeMillis() - startTime < time)) {
            done = awaitedCondition.getAsBoolean();
            if (done) {
                return true;
            }
            sleep(100);
        }
        return awaitedCondition.getAsBoolean();
    }

    public static boolean sleepUntil(BooleanSupplier awaitedCondition, BooleanSupplier resetCondition, int timeout) {
        final Stopwatch watch = Stopwatch.createStarted();
        while (!awaitedCondition.getAsBoolean() && watch.elapsed(TimeUnit.MILLISECONDS) < timeout) {
            sleep(100);
            if (resetCondition.getAsBoolean() && Microbot.isLoggedIn()) {
                watch.reset();
                watch.start();
            }
        }
        return awaitedCondition.getAsBoolean();
    }

    /**
     * Sleeps until a specified condition is met, running an action periodically, or until a timeout is reached.
     */
    public static boolean sleepUntil(BooleanSupplier awaitedCondition, Runnable action, long timeoutMillis, int sleepMillis) {
        long startTime = System.nanoTime();
        long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);

        while (!Thread.currentThread().isInterrupted() && (System.nanoTime() - startTime < timeoutNanos)) {
            action.run();
            if (awaitedCondition.getAsBoolean()) {
                return true;
            }
            sleep(sleepMillis);
        }
        return awaitedCondition.getAsBoolean();
    }

    /**
     * Sleeps until not null or a designated max time. (Example usage)
     */
    @SneakyThrows
    public static <T> T sleepUntilNotNull(Callable<T> method, int time) {
        if (Microbot.getClient().isClientThread()) return null;
        long startTime = System.currentTimeMillis();
        T response;
        do {
            if (Thread.currentThread().isInterrupted()) {
                return null;
            }
            response = method.call();
            if (response != null) {
                return response;
            }
            sleep(100);
        } while (System.currentTimeMillis() - startTime < time);
        return null;
    }

    /**
     * Sleeps until the given condition is true or a specified timeout is reached, on the client thread.
     */
    public static boolean sleepUntil(BooleanSupplier awaitedCondition, int intervalMillis, int timeoutMillis) {
        if (Microbot.getClient().isClientThread()) return false;
        long startTime = System.currentTimeMillis();
        while (!Thread.currentThread().isInterrupted() && (System.currentTimeMillis() - startTime < timeoutMillis)) {
            if (awaitedCondition.getAsBoolean()) {
                return true;
            }
            sleep(intervalMillis);
        }
        return awaitedCondition.getAsBoolean();
    }

    public static void sleepUntilOnClientThread(BooleanSupplier awaitedCondition) {
        sleepUntilOnClientThread(awaitedCondition, 5000);
    }

    public static void sleepUntilOnClientThread(BooleanSupplier awaitedCondition, int time) {
        if (Microbot.getClient().isClientThread()) return;
        long startTime = System.currentTimeMillis();
        while (!Thread.currentThread().isInterrupted() && (System.currentTimeMillis() - startTime < time)) {
            // Run the condition on the client thread
            if (Microbot.getClientThread().runOnClientThread(awaitedCondition::getAsBoolean)) {
                return;
            }
            sleep(100);
        }
    }

    public static void sleepUntilTick(int ticksToWait) {
        int startTick = Microbot.getClient().getTickCount();
        // Typically ~600 ms per game tick; we add 2s grace.
        sleepUntil(() ->
                        Microbot.getClient().getTickCount() >= (startTick + ticksToWait),
                ticksToWait * 600 + 2000
        );
    }
}
