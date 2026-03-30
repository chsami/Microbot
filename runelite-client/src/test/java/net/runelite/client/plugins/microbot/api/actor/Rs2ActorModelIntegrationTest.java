package net.runelite.client.plugins.microbot.api.actor;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.RuneLite;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.security.LoginManager;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

@Slf4j
public class Rs2ActorModelIntegrationTest {

    private static final int TARGET_WORLD = 382;
    private static final long LOGGED_IN_SETTLE_MS = 5000;
    private static final int MAX_RETRIES = 3;
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final int SCREENSHOT_ANALYZE_AFTER_ATTEMPT = 2;

    @BeforeClass
    public static void startClient() throws Exception {
        log.info("=== Starting RuneLite client for integration test ===");
        Thread clientThread = new Thread(() -> {
            try {
                RuneLite.main(new String[]{"--developer-mode"});
            } catch (Exception e) {
                log.error("Failed to start RuneLite", e);
            }
        }, "RuneLite-Test-Launcher");
        clientThread.setDaemon(true);
        clientThread.start();

        log.info("Waiting for Microbot to fully initialize (clientThread injection)...");
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
            performLoginWithWatchdog();
        }

        log.info("Logged in! Waiting {}ms for game state to settle...", LOGGED_IN_SETTLE_MS);
        Thread.sleep(LOGGED_IN_SETTLE_MS);
        log.info("=== Client ready for tests ===");
    }

    private static void performLoginWithWatchdog() throws Exception {
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

            if (attempt >= SCREENSHOT_ANALYZE_AFTER_ATTEMPT) {
                log.info("Taking screenshot for Claude analysis...");
                Path screenshot = ScreenshotAnalyzer.captureScreenshot("login_stuck_attempt_" + attempt);
                ScreenshotAnalyzer.AnalysisResult analysis = ScreenshotAnalyzer.analyzeStuckState(
                    screenshot,
                    "The integration test is stuck trying to log in. "
                        + "This is attempt " + attempt + " of " + MAX_LOGIN_ATTEMPTS + ". "
                        + "The test sets world " + TARGET_WORLD + " and presses Enter twice to submit login (Jagex account, no password). "
                        + "Game state: " + getGameStateString()
                );

                log.info("Claude suggests: {} - {}", analysis.action, analysis.explanation);

                switch (analysis.action) {
                    case ABORT:
                        throw new RuntimeException("Claude analysis recommends ABORT: " + analysis.explanation
                            + "\nFull analysis:\n" + analysis.rawResponse);

                    case WAIT:
                        log.info("Claude says to wait. Waiting 10s before next attempt...");
                        Thread.sleep(10000);
                        boolean loggedInAfterWait = waitForConditionSafe("Login after wait", 10, Microbot::isLoggedIn);
                        if (loggedInAfterWait) {
                            log.info("Login succeeded after waiting (Claude's advice worked)");
                            return;
                        }
                        break;

                    case RETRY_LOGIN:
                    case UNKNOWN:
                    default:
                        log.info("Retrying login in 5s...");
                        Thread.sleep(5000);
                        break;
                }
            } else {
                log.info("Retrying in 5s...");
                Thread.sleep(5000);
            }

            if (attempt == MAX_LOGIN_ATTEMPTS) {
                log.error("All login attempts exhausted. Taking final screenshot...");
                Path finalScreenshot = ScreenshotAnalyzer.captureScreenshot("login_final_failure");
                ScreenshotAnalyzer.AnalysisResult finalAnalysis = ScreenshotAnalyzer.analyzeStuckState(
                    finalScreenshot,
                    "FINAL FAILURE: All " + MAX_LOGIN_ATTEMPTS + " login attempts failed. "
                        + "Game state: " + getGameStateString() + ". "
                        + "Provide a detailed explanation of what's visible on screen and why login might be failing."
                );
                throw new RuntimeException(
                    "Failed to login after " + MAX_LOGIN_ATTEMPTS + " attempts.\n"
                        + "Claude's final analysis: " + finalAnalysis.explanation + "\n"
                        + "Full response:\n" + finalAnalysis.rawResponse
                );
            }
        }
    }

    private static String getGameStateString() {
        try {
            Client client = Microbot.getClient();
            if (client == null) return "CLIENT_NULL";
            GameState gs = client.getGameState();
            String extra = "";
            if (gs == GameState.LOGIN_SCREEN) {
                extra = ", loginIndex=" + client.getLoginIndex();
            }
            return gs.name() + extra;
        } catch (Exception e) {
            return "ERROR(" + e.getMessage() + ")";
        }
    }

    @Test
    public void testLocalPlayerGetWorldLocation() {
        log.info("--- Test: Local Player getWorldLocation ---");

        retryUntilSuccess("localPlayer.getWorldLocation()", () -> {
            Player localPlayer = Microbot.getClient().getLocalPlayer();
            assertNotNull("Local player should not be null", localPlayer);

            Rs2ActorModel model = new Rs2ActorModel(localPlayer);

            WorldPoint location = model.getWorldLocation();
            assertNotNull("World location should not be null", location);
            assertTrue("X coordinate should be positive", location.getX() > 0);
            assertTrue("Y coordinate should be positive", location.getY() > 0);

            log.info("  Location: {}", location);
        });
    }

    @Test
    public void testLocalPlayerGetWorldView() {
        log.info("--- Test: Local Player getWorldView ---");

        retryUntilSuccess("localPlayer.getWorldView()", () -> {
            Player localPlayer = Microbot.getClient().getLocalPlayer();
            assertNotNull("Local player should not be null", localPlayer);

            Rs2ActorModel model = new Rs2ActorModel(localPlayer);

            WorldView wv = model.getWorldView();
            assertNotNull("WorldView should not be null", wv);
            log.info("  WorldView id={}, isTopLevel={}", wv.getId(), wv.isTopLevel());
        });
    }

    @Test
    public void testLocalPlayerGetWorldLocationConsistency() {
        log.info("--- Test: Location consistency across 10 rapid calls ---");

        retryUntilSuccess("rapid getWorldLocation() calls", () -> {
            Player localPlayer = Microbot.getClient().getLocalPlayer();
            assertNotNull("Local player should not be null", localPlayer);

            Rs2ActorModel model = new Rs2ActorModel(localPlayer);
            List<WorldPoint> locations = new ArrayList<>();

            for (int i = 0; i < 10; i++) {
                WorldPoint loc = model.getWorldLocation();
                assertNotNull("Location call " + i + " should not be null", loc);
                locations.add(loc);
            }

            WorldPoint first = locations.get(0);
            for (int i = 1; i < locations.size(); i++) {
                int dist = first.distanceTo(locations.get(i));
                assertTrue("Rapid calls should return nearby locations (got distance " + dist + ")", dist <= 5);
            }

            log.info("  All 10 calls returned consistent locations near {}", first);
        });
    }

    @Test
    public void testAllVisiblePlayersGetWorldLocation() {
        log.info("--- Test: All visible players getWorldLocation ---");

        retryUntilSuccess("visible players getWorldLocation()", () -> {
            List<Player> players = Microbot.getClient().getPlayers();
            log.info("  Found {} visible players", players.size());
            assertTrue("Should see at least 1 player (self)", players.size() >= 1);

            int successCount = 0;
            List<String> errors = new ArrayList<>();

            for (Player player : players) {
                try {
                    Rs2ActorModel model = new Rs2ActorModel(player);
                    WorldPoint loc = model.getWorldLocation();
                    assertNotNull("Location should not be null for player", loc);
                    successCount++;
                } catch (Exception e) {
                    String name = "unknown";
                    try { name = player.getName(); } catch (Exception ignored) {}
                    errors.add(name + ": " + e.getMessage());
                }
            }

            log.info("  {}/{} players returned valid locations", successCount, players.size());
            if (!errors.isEmpty()) {
                log.error("  Failures: {}", errors);
                fail("Some players failed getWorldLocation(): " + errors);
            }
        });
    }

    @Test
    public void testAllVisibleNpcsGetWorldLocation() {
        log.info("--- Test: All visible NPCs getWorldLocation ---");

        retryUntilSuccess("visible NPCs getWorldLocation()", () -> {
            List<NPC> npcs = Microbot.getClient().getNpcs();
            log.info("  Found {} visible NPCs", npcs.size());
            assertTrue("Should see at least 1 NPC", npcs.size() >= 1);

            int successCount = 0;
            List<String> errors = new ArrayList<>();

            for (NPC npc : npcs) {
                try {
                    Rs2ActorModel model = new Rs2ActorModel(npc);
                    WorldPoint loc = model.getWorldLocation();
                    assertNotNull("Location should not be null for NPC", loc);
                    successCount++;
                } catch (Exception e) {
                    String name = "unknown";
                    try { name = npc.getName(); } catch (Exception ignored) {}
                    errors.add(name + ": " + e.getMessage());
                }
            }

            log.info("  {}/{} NPCs returned valid locations", successCount, npcs.size());
            if (!errors.isEmpty()) {
                log.error("  Failures: {}", errors);
                fail("Some NPCs failed getWorldLocation(): " + errors);
            }
        });
    }

    @Test
    public void testActorModelDelegationMethods() {
        log.info("--- Test: Rs2ActorModel delegation methods ---");

        retryUntilSuccess("delegation methods", () -> {
            Player localPlayer = Microbot.getClient().getLocalPlayer();
            assertNotNull("Local player should not be null", localPlayer);

            Rs2ActorModel model = new Rs2ActorModel(localPlayer);

            WorldView wv = model.getWorldView();
            assertNotNull("getWorldView() should not be null", wv);

            WorldPoint loc = model.getWorldLocation();
            assertNotNull("getWorldLocation() should not be null", loc);

            int combatLevel = model.getCombatLevel();
            assertTrue("Combat level should be > 0", combatLevel > 0);

            String name = model.getName();
            assertNotNull("Player name should not be null", name);
            assertFalse("Player name should not be empty", name.isEmpty());

            int animation = model.getAnimation();

            log.info("  name={}, combat={}, animation={}, location={}, worldViewId={}",
                name, combatLevel, animation, loc, wv.getId());
        });
    }

    private void retryUntilSuccess(String testName, Runnable test) {
        List<Throwable> failures = new ArrayList<>();

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                test.run();
                if (attempt > 1) {
                    log.info("  [{}] Succeeded on attempt {}/{}", testName, attempt, MAX_RETRIES);
                }
                return;
            } catch (Throwable t) {
                failures.add(t);
                log.warn("  [{}] Attempt {}/{} failed: {}", testName, attempt, MAX_RETRIES, t.getMessage());

                if (attempt == MAX_RETRIES) {
                    log.info("  [{}] Final attempt failed, capturing screenshot for analysis...", testName);
                    Path screenshot = ScreenshotAnalyzer.captureScreenshot("test_failure_" + testName);
                    ScreenshotAnalyzer.AnalysisResult analysis = ScreenshotAnalyzer.analyzeStuckState(
                        screenshot,
                        "Test '" + testName + "' failed after " + MAX_RETRIES + " attempts. "
                            + "Last error: " + t.getMessage() + ". "
                            + "The test exercises Rs2ActorModel methods on a live RuneLite client. "
                            + "Game state: " + getGameStateString()
                    );
                    log.info("  Claude analysis of failure: {}", analysis);
                }

                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during retry wait", ie);
                    }
                }
            }
        }

        log.error("  [{}] All {} attempts failed", testName, MAX_RETRIES);
        for (int i = 0; i < failures.size(); i++) {
            log.error("    Attempt {} error:", i + 1, failures.get(i));
        }
        throw new AssertionError(
            testName + " failed after " + MAX_RETRIES + " attempts. Last error: " + failures.get(failures.size() - 1).getMessage(),
            failures.get(failures.size() - 1)
        );
    }

    private static void waitForCondition(String description, long timeoutSeconds, BooleanSupplier condition) throws InterruptedException {
        if (!waitForConditionSafe(description, timeoutSeconds, condition)) {
            log.error("Timed out waiting for '{}', capturing screenshot...", description);
            Path screenshot = ScreenshotAnalyzer.captureScreenshot("timeout_" + description);
            ScreenshotAnalyzer.AnalysisResult analysis = ScreenshotAnalyzer.analyzeStuckState(
                screenshot,
                "The test timed out waiting for condition: '" + description + "' after " + timeoutSeconds + "s. "
                    + "Game state: " + getGameStateString()
            );
            throw new RuntimeException(
                "Timed out waiting for: " + description + " (after " + timeoutSeconds + "s)\n"
                    + "Claude analysis: " + analysis.explanation + "\n"
                    + "Full response:\n" + analysis.rawResponse
            );
        }
    }

    private static boolean waitForConditionSafe(String description, long timeoutSeconds, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000);
        while (System.currentTimeMillis() < deadline) {
            try {
                if (condition.getAsBoolean()) {
                    return true;
                }
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
