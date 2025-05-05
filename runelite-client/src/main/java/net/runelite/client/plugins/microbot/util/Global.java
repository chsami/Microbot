package net.runelite.client.plugins.microbot.util;

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

    public static void sleep(int start) {
        if (Microbot.getClient().isClientThread()) return;
        long startTime = System.currentTimeMillis();
        do {
        } while (System.currentTimeMillis() - startTime < start);
    }

    public static void sleep(int start, int end) {
        int randomSleep = Rs2Random.between(start, end);
        sleep(randomSleep);
    }

    public static void sleepGaussian(int mean, int stddev) {
        int randomSleep = Rs2Random.randomGaussian(mean, stddev);
        sleep(randomSleep);
    }

    @SneakyThrows
    public static <T> T sleepUntilNotNull(Callable<T> method, int time) {
        if (Microbot.getClient().isClientThread()) return null;
        boolean done;
        T methodResponse;
        long startTime = System.currentTimeMillis();
        do {
            methodResponse = method.call();
            done = methodResponse != null;
            sleep(100);
        } while (!done && System.currentTimeMillis() - startTime < time);
        return methodResponse;
    }

    public static boolean sleepUntil(BooleanSupplier awaitedCondition) {
        return sleepUntil(awaitedCondition, 5000);
    }

    public static boolean sleepUntil(BooleanSupplier awaitedCondition, int time) {
        if (Microbot.getClient().isClientThread()) return false;
        boolean done = false;
        long startTime = System.currentTimeMillis();
        do {
            done = awaitedCondition.getAsBoolean();
            sleep(100);
        } while (!done && System.currentTimeMillis() - startTime < time);
        return done;
    }

    public static boolean sleepUntil(BooleanSupplier awaitedCondition, Runnable action, long timeoutMillis, int sleepMillis) {
        long startTime = System.nanoTime();
        long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() - startTime < timeoutNanos) {
            action.run();
            if (awaitedCondition.getAsBoolean()) {
                return true;
            }
            sleep(sleepMillis);
        }
        return false;
    }

    public static boolean sleepUntilTrue(BooleanSupplier awaitedCondition) {
        if (Microbot.getClient().isClientThread()) return false;
        long startTime = System.currentTimeMillis();
        do {
            if (awaitedCondition.getAsBoolean()) {
                return true;
            }
            sleep(100);
        } while (System.currentTimeMillis() - startTime < 5000);
        return false;
    }

    public static boolean sleepUntilTrue(BooleanSupplier awaitedCondition, int time, int timeout) {
        if (Microbot.getClient().isClientThread()) return false;
        long startTime = System.currentTimeMillis();
        do {
            if (awaitedCondition.getAsBoolean()) {
                return true;
            }
            sleep(time);
        } while (System.currentTimeMillis() - startTime < timeout);
        return false;
    }

    public static boolean sleepUntilTrue(BooleanSupplier awaitedCondition, BooleanSupplier resetCondition, int time, int timeout) {
        if (Microbot.getClient().isClientThread()) return false;
        long startTime = System.currentTimeMillis();
        do {
            if (resetCondition.getAsBoolean()) {
                startTime = System.currentTimeMillis();
            }
            if (awaitedCondition.getAsBoolean()) {
                return true;
            }
            sleep(time);
        } while (System.currentTimeMillis() - startTime < timeout);
        return false;
    }

    public static void sleepUntilOnClientThread(BooleanSupplier awaitedCondition) {
        sleepUntilOnClientThread(awaitedCondition, Rs2Random.between(2500, 5000));
    }

    public static void sleepUntilOnClientThread(BooleanSupplier awaitedCondition, int time) {
        if (Microbot.getClient().isClientThread()) return;
        boolean done;
        long startTime = System.currentTimeMillis();
        do {
            done = Microbot.getClientThread().runOnClientThreadOptional(awaitedCondition::getAsBoolean)
                    .orElse(false);
        } while (!done && System.currentTimeMillis() - startTime < time);
    }

    public boolean sleepUntilTick(int ticksToWait) {
        int startTick = Microbot.getClient().getTickCount();
        return Global.sleepUntil(() -> Microbot.getClient().getTickCount() >= startTick + ticksToWait, ticksToWait * 600 + 2000);
    }

}