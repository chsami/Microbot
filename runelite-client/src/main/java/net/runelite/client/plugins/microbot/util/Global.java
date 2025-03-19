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
        private boolean isSimpleSleep = false;
        private int sleepDuration = 0;
        private boolean isRandomSleep = false;
        private int minSleep = 0;
        private int maxSleep = 0;
        private boolean isGaussianSleep = false;
        private int meanSleep = 0;
        private int stdDevSleep = 0;
        private BooleanSupplier condition;
        private BooleanSupplier resetCondition;
        private Runnable periodicAction;
        private long timeout = 5000;
        private int sleepInterval = 100;
        private boolean onClientThread = false;
        private int ticksToWait = 0;
        private int startTick = 0;
        private Object notNullResult;
        private boolean isNotNullCheck = false;

        /**
         * Configures a fixed duration sleep.
         *
         * @param milliseconds The time to sleep in milliseconds
         * @return This SleepBuilder instance for method chaining
         */
        public SleepBuilder until(int milliseconds) {
            this.isSimpleSleep = true;
            this.sleepDuration = milliseconds;
            return this;
        }

        /**
         * Configures a random sleep between the specified start and end times.
         *
         * @param start The minimum sleep time in milliseconds
         * @param end   The maximum sleep time in milliseconds
         * @return This SleepBuilder instance for method chaining
         */
        public SleepBuilder between(int start, int end) {
            this.isRandomSleep = true;
            this.minSleep = start;
            this.maxSleep = end;
            return this;
        }

        /**
         * Configures a sleep with duration determined by a Gaussian distribution.
         *
         * @param mean   The mean sleep time in milliseconds
         * @param stdDev The standard deviation of the sleep time in milliseconds
         * @return This SleepBuilder instance for method chaining
         */
        public SleepBuilder gaussian(int mean, int stdDev) {
            this.isGaussianSleep = true;
            this.meanSleep = mean;
            this.stdDevSleep = stdDev;
            return this;
        }

        /**
         * Sets the condition to wait for before continuing execution.
         *
         * @param condition A BooleanSupplier that returns true when the wait should end
         * @return This SleepBuilder instance for method chaining
         */
        public SleepBuilder until(BooleanSupplier condition) {
            this.condition = condition;
            return this;
        }

        /**
         * Sets a condition that, when true, will reset the timeout timer.
         *
         * @param resetCondition A BooleanSupplier that returns true when the timeout should be reset
         * @return This SleepBuilder instance for method chaining
         */
        public SleepBuilder withReset(BooleanSupplier resetCondition) {
            this.resetCondition = resetCondition;
            return this;
        }

        /**
         * Sets an action to be performed periodically while waiting for the condition.
         *
         * @param action The action to perform periodically
         * @return This SleepBuilder instance for method chaining
         */
        public SleepBuilder withAction(Runnable action) {
            this.periodicAction = action;
            return this;
        }

        /**
         * Sets the maximum time to wait for the condition in milliseconds.
         *
         * @param millis The timeout duration in milliseconds
         * @return This SleepBuilder instance for method chaining
         */
        public SleepBuilder timeout(long millis) {
            this.timeout = millis;
            return this;
        }

        /**
         * Sets the interval between condition checks in milliseconds.
         *
         * @param millis The interval in milliseconds
         * @return This SleepBuilder instance for method chaining
         */
        public SleepBuilder interval(int millis) {
            this.sleepInterval = millis;
            return this;
        }

        /**
         * Configures the condition to be checked on the client thread.
         *
         * @return This SleepBuilder instance for method chaining
         */
        public SleepBuilder onClientThread() {
            this.onClientThread = true;
            return this;
        }

        /**
         * Configures the sleep to wait for a specified number of game ticks.
         *
         * @param ticks The number of game ticks to wait
         * @return This SleepBuilder instance for method chaining
         */
        public SleepBuilder ticks(int ticks) {
            this.ticksToWait = ticks;
            if (this.condition != null) {
                throw new IllegalStateException("Cannot set both condition and ticksToWait");
            }
            this.condition = (() -> Microbot.getClient().getTickCount() >= ticksToWait + startTick);
            return this;
        }

        /**
         * Sets the condition to wait until the specified method returns a non-null result.
         * The result will be stored and returned by execute().
         *
         * @param <T>    The type of the result
         * @param method A Callable that returns the result to check for non-null
         * @return This SleepBuilder instance for method chaining
         */
        public <T> SleepBuilder untilNotNull(Callable<T> method) {
            this.isNotNullCheck = true;
            this.condition = () -> {
                try {
                    T result = (T) method.call();
                    if (result != null) {
                        this.notNullResult = result;
                        return true;
                    }
                    return false;
                } catch (Exception e) {
                    return false;
                }
            };
            return this;
        }

        /**
         * Executes the sleep operation with all configured parameters.
         *
         * @param <T> The return type, either Boolean for condition-based waits or the result type for untilNotNull
         * @return For condition-based waits: Boolean.TRUE if the condition was met, Boolean.FALSE if timed out
         * For untilNotNull: The non-null result if found, null if timed out
         */
        @SuppressWarnings("unchecked")
        public <T> T execute() {
            if (isSimpleSleep) {
                if (sleepDuration <= 0 || Microbot.getClient().isClientThread()) {
                    return (T) Boolean.TRUE;
                }
                try {
                    Thread.sleep(sleepDuration);
                    return (T) Boolean.TRUE;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return (T) Boolean.FALSE;
                }
            }

            if (isRandomSleep) {
                if (minSleep > maxSleep || Microbot.getClient().isClientThread()) {
                    return (T) Boolean.TRUE;
                }
                try {
                    int randomSleep = Rs2Random.between(minSleep, maxSleep);
                    Thread.sleep(randomSleep);
                    return (T) Boolean.TRUE;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return (T) Boolean.FALSE;
                }
            }

            if (isGaussianSleep) {
                if (Microbot.getClient().isClientThread()) {
                    return (T) Boolean.TRUE;
                }
                try {
                    int randomSleep = Rs2Random.randomGaussian(meanSleep, stdDevSleep);
                    Thread.sleep(randomSleep);
                    return (T) Boolean.TRUE;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return (T) Boolean.FALSE;
                }
            }

            if (Microbot.getClient().isClientThread() && !onClientThread) {
                return isNotNullCheck ? (T) notNullResult : (T) Boolean.FALSE;
            }

            boolean result = false;
            long startTime = System.currentTimeMillis();
            startTick = Microbot.getClient().getTickCount();

            while (!Thread.currentThread().isInterrupted()) {
                // Check for timeout
                if (System.currentTimeMillis() - startTime >= timeout) {
                    System.out.println(this.condition.toString() + " timed out");
                    break;
                }

                if (periodicAction != null) {
                    if (onClientThread) {
                        Microbot.getClientThread().invokeLater(periodicAction);
                    } else {
                        periodicAction.run();
                    }
                }

                // Check condition
                boolean conditionMet;
                if (onClientThread) {
                    conditionMet = Microbot.getClientThread().runOnClientThread(condition::getAsBoolean);
                } else {
                    conditionMet = condition.getAsBoolean();
                }

                if (conditionMet) {
                    result = true;
                    break;
                }

                if (resetCondition != null) {
                    boolean shouldReset;
                    if (onClientThread) {
                        shouldReset = Microbot.getClientThread().runOnClientThread(resetCondition::getAsBoolean) && Microbot.isLoggedIn();
                    } else {
                        shouldReset = resetCondition.getAsBoolean() && Microbot.isLoggedIn();
                    }

                    if (shouldReset) {
                        startTime = System.currentTimeMillis();
                    }
                }

                try {
                    Thread.sleep(sleepInterval);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return isNotNullCheck ? null : (T) Boolean.FALSE;
                }
            }

            if (isNotNullCheck) {
                return (T) notNullResult;
            } else {
                return (T) (result ? Boolean.TRUE : Boolean.FALSE);
            }
        }
    }


    /**
     * Creates a new SleepBuilder instance for configuring sleep operations.
     *
     * @return A new SleepBuilder instance
     */
    public static SleepBuilder sleep() {
        return new SleepBuilder();
    }


    /**
     * @deprecated Since 1.7.9, use sleep().until(time) instead
     */
    @Deprecated
    public static void sleep(int start) {
        sleep().until(start).execute();
    }

    /**
     * @deprecated Since 1.7.9, use sleep().between(start, end) instead
     */
    @Deprecated
    public static void sleep(int start, int end) {
        sleep().between(start, end).execute();
    }

    /**
     * @deprecated Since 1.7.9, use sleep().gaussian(mean, stddev) instead
     */
    @Deprecated
    public static void sleepGaussian(int mean, int stddev) {
        sleep().gaussian(mean, stddev).execute();
    }

    /**
     * @deprecated Since 1.7.9, use sleep().until(awaitedCondition).execute() instead
     */
    @Deprecated
    public static boolean sleepUntil(BooleanSupplier awaitedCondition) {
        return sleep().until(awaitedCondition).execute();
    }

    /**
     * @deprecated Since 1.7.9, use sleep().until(awaitedCondition).timeout(time).execute() instead
     */
    @Deprecated
    public static boolean sleepUntil(BooleanSupplier awaitedCondition, int time) {
        return sleep().until(awaitedCondition).timeout(time).execute();
    }

    /**
     * @deprecated Since 1.7.9, use sleep().until(awaitedCondition).withAction(action).timeout(timeoutMillis).interval(sleepMillis).execute() instead
     */
    @Deprecated
    public static boolean sleepUntil(BooleanSupplier awaitedCondition, Runnable action, long timeoutMillis, int sleepMillis) {
        return sleep().until(awaitedCondition).withAction(action).timeout(timeoutMillis).interval(sleepMillis).execute();
    }

    /**
     * @deprecated Since 1.7.9, use sleep().until(awaitedCondition).execute() instead
     */
    @Deprecated
    public static boolean sleepUntilTrue(BooleanSupplier awaitedCondition) {
        return sleep().until(awaitedCondition).execute();
    }

    /**
     * @deprecated Since 1.7.9, use sleep().until(awaitedCondition).interval(time).timeout(timeout).execute() instead
     */
    @Deprecated
    public static boolean sleepUntilTrue(BooleanSupplier awaitedCondition, int time, int timeout) {
        return sleep().until(awaitedCondition).interval(time).timeout(timeout).execute();
    }

    /**
     * @deprecated Since 1.7.9, use sleep().until(awaitedCondition).withReset(resetCondition).interval(time).timeout(timeout).execute() instead
     */
    @Deprecated
    public static boolean sleepUntil(BooleanSupplier awaitedCondition, BooleanSupplier resetCondition, int timeout) {
        return sleep().until(awaitedCondition).withReset(resetCondition).timeout(timeout).execute();
    }

    /**
     * @deprecated Since 1.7.9, use sleep().until(awaitedCondition).withReset(resetCondition).interval(time).timeout(timeout).execute() instead
     */
    @Deprecated
    public static boolean sleepUntilTrue(BooleanSupplier awaitedCondition, BooleanSupplier resetCondition, int time, int timeout) {
        return sleep().until(awaitedCondition).withReset(resetCondition).interval(time).timeout(timeout).execute();
    }

    /**
     * @deprecated Since 1.7.9, use sleep().until(awaitedCondition).onClientThread().execute() instead
     */
    @Deprecated
    public static void sleepUntilOnClientThread(BooleanSupplier awaitedCondition) {
        sleep().until(awaitedCondition).onClientThread().execute();
    }

    /**
     * @deprecated Since 1.7.9, use sleep().until(awaitedCondition).onClientThread().timeout(time).execute() instead
     */
    @Deprecated
    public static void sleepUntilOnClientThread(BooleanSupplier awaitedCondition, int time) {
        sleep().until(awaitedCondition).onClientThread().timeout(time).execute();
    }

    /**
     * @deprecated Since 1.7.9, use sleep().timeout(time).untilNotNull(method) instead
     */
    @Deprecated
    @SneakyThrows
    public static <T> T sleepUntilNotNull(Callable<T> method, int time) {
        return sleep().timeout(time).untilNotNull(method).execute();
    }
}