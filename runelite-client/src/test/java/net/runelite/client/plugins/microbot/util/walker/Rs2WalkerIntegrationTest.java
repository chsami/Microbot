package net.runelite.client.plugins.microbot.util.walker;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.RuneLite;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.LoginManager;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

@Slf4j
public class Rs2WalkerIntegrationTest {

    private static final int TARGET_WORLD = 382;
    private static final long LOGGED_IN_SETTLE_MS = 5000;
    private static final int MAX_LOGIN_ATTEMPTS = 5;

    @BeforeClass
    public static void startClient() throws Exception {
        log.info("=== Starting RuneLite client for Walker integration test ===");
        Thread clientThread = new Thread(() -> {
            try {
                RuneLite.main(new String[]{"--developer-mode"});
            } catch (Exception e) {
                log.error("Failed to start RuneLite", e);
            }
        }, "RuneLite-Test-Launcher");
        clientThread.setDaemon(true);
        clientThread.start();

        log.info("Waiting for Microbot to fully initialize...");
        waitForCondition("Microbot.getClientThread() != null", 90, () ->
            Microbot.getClientThread() != null
        );
        log.info("Microbot initialized.");

        log.info("Waiting for login screen...");
        waitForCondition("Login screen", 90, () -> {
            Client client = Microbot.getClient();
            return client != null && (
                client.getGameState() == GameState.LOGIN_SCREEN ||
                client.getGameState() == GameState.LOGGED_IN
            );
        });

        if (!Microbot.isLoggedIn()) {
            log.info("On login screen. Will attempt login to world {}...", TARGET_WORLD);
            Thread.sleep(5000);
            performLogin();
        }

        log.info("Logged in! Waiting {}ms for game state to settle...", LOGGED_IN_SETTLE_MS);
        Thread.sleep(LOGGED_IN_SETTLE_MS);

        log.info("Waiting for ShortestPathPlugin to initialize...");
        waitForCondition("ShortestPathPlugin config", 30, () ->
            Rs2Walker.config != null
        );
        log.info("ShortestPathPlugin config ready.");

        waitForCondition("PathfinderConfig", 30, () ->
            ShortestPathPlugin.getPathfinderConfig() != null
                && ShortestPathPlugin.getPathfinderConfig().getMap() != null
        );
        log.info("PathfinderConfig and collision map ready.");

        log.info("=== Client ready for walker tests ===");
    }

    @Test
    public void testWalkDoesNotCrashOnClientThread() throws Exception {
        log.info("--- Test: Walker processes path without client thread errors ---");

        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        log.info("Player location: {}", playerLoc);
        assertNotNull("Player location should not be null", playerLoc);

        WorldPoint target = new WorldPoint(3222, 3218, 0);
        if (playerLoc.distanceTo(target) <= 4) {
            target = new WorldPoint(3207, 3210, 0);
        }

        final WorldPoint walkTarget = target;
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        ExecutorService scriptExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "WalkerTest-Script");
            t.setDaemon(true);
            return t;
        });

        log.info("Walking to {} (will let it run 15s to verify no client thread crashes)...", walkTarget);
        scriptExecutor.submit(() -> {
            try {
                Rs2Walker.walkWithState(walkTarget, 4);
            } catch (Throwable t) {
                if (!(t.getCause() instanceof InterruptedException)
                        && !t.getMessage().contains("Interrupted")) {
                    log.error("Walker threw unexpected exception", t);
                    errorRef.set(t);
                }
            } finally {
                latch.countDown();
            }
        });

        latch.await(15, TimeUnit.SECONDS);
        scriptExecutor.shutdownNow();
        latch.await(5, TimeUnit.SECONDS);

        Throwable error = errorRef.get();
        if (error != null) {
            log.error("Unexpected walker error: ", error);
        }
        assertNull("Walker should not throw client thread errors: " + error, error);
        log.info("Walker ran for 15s without client thread errors - PASS");
    }

    @Test
    public void testPathfinderCreationAndCompletion() throws Exception {
        log.info("--- Test: Pathfinder creation and completion ---");

        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        WorldPoint nearbyTarget = new WorldPoint(playerLoc.getX() + 10, playerLoc.getY() + 10, playerLoc.getPlane());
        log.info("Player at: {}, target: {}", playerLoc, nearbyTarget);

        Rs2Walker.setTarget(null);
        Thread.sleep(500);

        log.info("Setting target...");
        Rs2Walker.setTarget(nearbyTarget);

        log.info("Waiting for pathfinder to be created...");
        long deadline = System.currentTimeMillis() + 5000;
        while (ShortestPathPlugin.getPathfinder() == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        assertNotNull("Pathfinder should be created after setTarget", ShortestPathPlugin.getPathfinder());
        log.info("Pathfinder created: {}", ShortestPathPlugin.getPathfinder());

        log.info("Waiting for pathfinder.isDone()...");
        deadline = System.currentTimeMillis() + 15000;
        while (!ShortestPathPlugin.getPathfinder().isDone() && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }

        boolean done = ShortestPathPlugin.getPathfinder().isDone();
        log.info("Pathfinder isDone: {}", done);

        if (done) {
            var path = ShortestPathPlugin.getPathfinder().getPath();
            log.info("Path size: {}, first: {}, last: {}",
                path != null ? path.size() : "null",
                path != null && !path.isEmpty() ? path.get(0) : "N/A",
                path != null && !path.isEmpty() ? path.get(path.size() - 1) : "N/A");
        } else {
            log.error("Pathfinder did NOT complete within 15 seconds!");
        }

        Rs2Walker.setTarget(null);

        assertTrue("Pathfinder should complete within 15 seconds", done);
    }

    private static void performLogin() throws Exception {
        for (int attempt = 1; attempt <= MAX_LOGIN_ATTEMPTS; attempt++) {
            log.info("Login attempt {}/{}...", attempt, MAX_LOGIN_ATTEMPTS);
            try {
                LoginManager.setWorld(TARGET_WORLD);
                Thread.sleep(1000);
                LoginManager.submitLoginForTest();
            } catch (Exception e) {
                log.warn("Login attempt {} threw: {}", attempt, e.getMessage());
            }

            boolean loggedIn = waitForConditionSafe("Login", 20, Microbot::isLoggedIn);
            if (loggedIn) {
                log.info("Login successful on attempt {}", attempt);
                return;
            }
            log.warn("Login attempt {} did not succeed.", attempt);
            if (attempt < MAX_LOGIN_ATTEMPTS) {
                Thread.sleep(5000);
            }
        }
        throw new RuntimeException("Failed to login after " + MAX_LOGIN_ATTEMPTS + " attempts");
    }

    private static void waitForCondition(String description, long timeoutSeconds, BooleanSupplier condition) throws InterruptedException {
        if (!waitForConditionSafe(description, timeoutSeconds, condition)) {
            throw new RuntimeException("Timed out waiting for: " + description + " (after " + timeoutSeconds + "s)");
        }
    }

    private static boolean waitForConditionSafe(String description, long timeoutSeconds, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000);
        while (System.currentTimeMillis() < deadline) {
            try {
                if (condition.getAsBoolean()) return true;
            } catch (Exception e) {
                log.debug("Condition check for '{}' threw: {}", description, e.getMessage());
            }
            Thread.sleep(500);
        }
        return false;
    }

    @FunctionalInterface
    private interface BooleanSupplier {
        boolean getAsBoolean() throws Exception;
    }
}
