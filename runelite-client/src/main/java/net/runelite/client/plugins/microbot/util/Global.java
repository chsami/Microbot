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

    public static class SleepBuilder {
        private BooleanSupplier condition;
        private BooleanSupplier resetCondition;
        private Runnable periodicAction;
        private long timeout = 5000;
        private int sleepInterval = 100;
        private boolean onClientThread = false;
        private int startValue = 0;
        private int endValue = 0;
        private boolean useGaussian = false;
        private int mean = 0;
        private int stdDev = 0;
        private int ticksToWait = 0;
        private boolean isTickWait = false;

        // Simple sleep for fixed duration
        public void until(int milliseconds) {
            if (milliseconds <= 0) return;
            // Avoid sleeping on the client thread
            if (Microbot.getClient().isClientThread()) return;
            try {
                Thread.sleep(milliseconds);
            } catch (InterruptedException e) {
                // Restore the interrupt status and return
                Thread.currentThread().interrupt();
            }
        }

        // Sleep for random duration between start and end
        public void between(int start, int end) {
            if (start > end) return;
            int randomSleep = Rs2Random.between(start, end);
            until(randomSleep);
        }

        // Sleep with Gaussian distribution
        public void gaussian(int mean, int stdDev) {
            int randomSleep = Rs2Random.randomGaussian(mean, stdDev);
            until(randomSleep);
        }

        // Set condition to wait for
        public SleepBuilder until(BooleanSupplier condition) {
            this.condition = condition;
            return this;
        }

        // Set reset condition
        public SleepBuilder withReset(BooleanSupplier resetCondition) {
            this.resetCondition = resetCondition;
            return this;
        }

        // Set periodic action
        public SleepBuilder withAction(Runnable action) {
            this.periodicAction = action;
            return this;
        }

        // Set timeout
        public SleepBuilder timeout(long millis) {
            this.timeout = millis;
            return this;
        }

        // Set sleep interval
        public SleepBuilder interval(int millis) {
            this.sleepInterval = millis;
            return this;
        }

        // Set to run on client thread
        public SleepBuilder onClientThread() {
            this.onClientThread = true;
            return this;
        }

        // Set to wait for ticks
        public SleepBuilder ticks(int ticks) {
            this.ticksToWait = ticks;
            this.isTickWait = true;
            return this;
        }

        // Execute the sleep operation
        public boolean execute() {

            // Handle tick-based waiting
            if (isTickWait) {
                int startTick = Microbot.getClient().getTickCount();
                // Typically ~600 ms per game tick; we add 2s grace.
                return sleepUntilCondition(() ->
                                Microbot.getClient().getTickCount() >= (startTick + ticksToWait),
                        ticksToWait * 600 + 2000);
            }

            // Handle client thread execution
            if (onClientThread) {
                return sleepUntilOnClientThread();
            }

            // Handle reset condition
            if (resetCondition != null) {
                return sleepUntilWithReset();
            }

            // Handle periodic action
            if (periodicAction != null) {
                return sleepUntilWithAction();
            }

            // Standard sleep until
            return sleepUntilCondition(condition, timeout);
        }

        private boolean sleepUntilCondition(BooleanSupplier condition, long timeout) {
            if (Microbot.getClient().isClientThread()) return false;
            long startTime = System.currentTimeMillis();

            while (!Thread.currentThread().isInterrupted() && (System.currentTimeMillis() - startTime < timeout)) {
                if (condition.getAsBoolean()) {
                    return true;
                }
                try {
                    Thread.sleep(sleepInterval);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return condition.getAsBoolean();
        }

        private boolean sleepUntilWithReset() {
            final Stopwatch watch = Stopwatch.createStarted();
            while (!condition.getAsBoolean() && watch.elapsed(TimeUnit.MILLISECONDS) < timeout) {
                try {
                    Thread.sleep(sleepInterval);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                if (resetCondition.getAsBoolean() && Microbot.isLoggedIn()) {
                    watch.reset();
                    watch.start();
                }
            }
            return condition.getAsBoolean();
        }

        private boolean sleepUntilWithAction() {
            long startTime = System.nanoTime();
            long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeout);

            while (!Thread.currentThread().isInterrupted() && (System.nanoTime() - startTime < timeoutNanos)) {
                periodicAction.run();
                if (condition.getAsBoolean()) {
                    return true;
                }
                try {
                    Thread.sleep(sleepInterval);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return condition.getAsBoolean();
        }

        private boolean sleepUntilOnClientThread() {
            if (Microbot.getClient().isClientThread()) return false;
            long startTime = System.currentTimeMillis();
            while (!Thread.currentThread().isInterrupted() && (System.currentTimeMillis() - startTime < timeout)) {
                // Run the condition on the client thread
                if (Microbot.getClientThread().runOnClientThread(condition::getAsBoolean)) {
                    return true;
                }
                try {
                    Thread.sleep(sleepInterval);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return false;
        }
    }

    // Factory method to create a new SleepBuilder
    public static SleepBuilder sleep() {
        return new SleepBuilder();
    }

    /**
     * Sleeps (blocking) for a set number of milliseconds,
     * checking for interruption and returning immediately if interrupted.
     */
    public static void sleep(int time) {
        sleep().until(time);
    }

    /**
     * Sleeps for a random duration between start and end (inclusive).
     * Also checks for interruption, returning immediately if interrupted.
     */
    public static void sleep(int start, int end) {
        sleep().between(start, end);
    }

    /**
     * Sleeps using a Gaussian-distributed random around mean, with stddev standard deviations.
     * Also checks for interruption, returning if interrupted.
     */
    public static void sleepGaussian(int mean, int stddev) {
        sleep().gaussian(mean, stddev);
    }

    /**
     * Sleep until a condition is true, or 5 seconds is reached, whichever comes first.
     */
    public static boolean sleepUntil(BooleanSupplier awaitedCondition) {
        return sleep().until(awaitedCondition).execute();
    }

    /**
     * Sleep until a condition is true or time milliseconds is reached.
     */
    public static boolean sleepUntil(BooleanSupplier awaitedCondition, int time) {
        return sleep().until(awaitedCondition).timeout(time).execute();
    }

    public static boolean sleepUntil(BooleanSupplier awaitedCondition, BooleanSupplier resetCondition, int timeout) {
        return sleep().until(awaitedCondition).withReset(resetCondition).timeout(timeout).execute();
    }

    /**
     * Sleeps until a specified condition is met, running an action periodically, or until a timeout is reached.
     */
    public static boolean sleepUntil(BooleanSupplier awaitedCondition, Runnable action, long timeoutMillis, int sleepMillis) {
        return sleep().until(awaitedCondition).withAction(action).timeout(timeoutMillis).interval(sleepMillis).execute();
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
        return sleep().until(awaitedCondition).interval(intervalMillis).timeout(timeoutMillis).execute();
    }

    public static void sleepUntilOnClientThread(BooleanSupplier awaitedCondition) {
        sleep().until(awaitedCondition).onClientThread().execute();
    }

    public static void sleepUntilOnClientThread(BooleanSupplier awaitedCondition, int time) {
        sleep().until(awaitedCondition).onClientThread().timeout(time).execute();
    }

    public static void sleepUntilTick(int ticksToWait) {
        sleep().ticks(ticksToWait).execute();
    }
}
