package net.runelite.client.plugins.microbot.api.actor;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ScreenshotAnalyzer {

    private static final long CLAUDE_TIMEOUT_SECONDS = 60;
    private static final Path SCREENSHOT_DIR = Path.of(System.getProperty("java.io.tmpdir"), "microbot-test-screenshots");

    public enum SuggestedAction {
        RETRY_LOGIN,
        WAIT,
        ABORT,
        UNKNOWN
    }

    public static class AnalysisResult {
        public final SuggestedAction action;
        public final String explanation;
        public final String rawResponse;

        AnalysisResult(SuggestedAction action, String explanation, String rawResponse) {
            this.action = action;
            this.explanation = explanation;
            this.rawResponse = rawResponse;
        }

        @Override
        public String toString() {
            return "AnalysisResult{action=" + action + ", explanation='" + explanation + "'}";
        }
    }

    public static Path captureScreenshot(String label) {
        try {
            Files.createDirectories(SCREENSHOT_DIR);
            String filename = label.replaceAll("[^a-zA-Z0-9_-]", "_") + "_" + System.currentTimeMillis() + ".png";
            Path screenshotPath = SCREENSHOT_DIR.resolve(filename);

            Robot robot = new Robot();
            Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            BufferedImage capture = robot.createScreenCapture(screenRect);
            ImageIO.write(capture, "png", screenshotPath.toFile());

            log.info("Screenshot saved to: {}", screenshotPath);
            return screenshotPath;
        } catch (Exception e) {
            log.error("Failed to capture screenshot", e);
            return null;
        }
    }

    public static Path captureWindow(Window window, String label) {
        try {
            Files.createDirectories(SCREENSHOT_DIR);
            String filename = label.replaceAll("[^a-zA-Z0-9_-]", "_") + "_" + System.currentTimeMillis() + ".png";
            Path screenshotPath = SCREENSHOT_DIR.resolve(filename);

            Robot robot = new Robot();
            Rectangle bounds = window.getBounds();
            BufferedImage capture = robot.createScreenCapture(bounds);
            ImageIO.write(capture, "png", screenshotPath.toFile());

            log.info("Window screenshot saved to: {}", screenshotPath);
            return screenshotPath;
        } catch (Exception e) {
            log.error("Failed to capture window screenshot, falling back to full screen", e);
            return captureScreenshot(label);
        }
    }

    public static AnalysisResult analyzeStuckState(Path screenshotPath, String context) {
        if (screenshotPath == null || !Files.exists(screenshotPath)) {
            log.warn("No screenshot available for analysis");
            return new AnalysisResult(SuggestedAction.RETRY_LOGIN, "No screenshot available", "");
        }

        String prompt = buildPrompt(context, screenshotPath);

        try {
            log.info("Asking Claude CLI to analyze screenshot...");
            String response = invokeClaude(prompt);

            if (response == null || response.isEmpty()) {
                log.warn("Empty response from Claude CLI");
                return new AnalysisResult(SuggestedAction.RETRY_LOGIN, "Empty response from Claude", "");
            }

            log.info("Claude analysis:\n{}", response);
            return parseResponse(response);

        } catch (Exception e) {
            log.error("Failed to invoke Claude CLI", e);
            return new AnalysisResult(SuggestedAction.RETRY_LOGIN, "Claude CLI invocation failed: " + e.getMessage(), "");
        }
    }

    private static String buildPrompt(String context, Path screenshotPath) {
        return "You are analyzing a screenshot from a RuneLite (Old School RuneScape) client during an automated integration test.\n\n"
            + "Context: " + context + "\n\n"
            + "Look at the screenshot at: " + screenshotPath.toAbsolutePath() + "\n\n"
            + "Based on what you see, determine the best course of action. Common scenarios:\n"
            + "- Login screen visible: the test should retry clicking login (RETRY_LOGIN)\n"
            + "- Game is loading/connecting: the test should wait (WAIT)\n"
            + "- Error dialog or ban screen: the test should abort (ABORT)\n"
            + "- Already logged in: the test should wait for state to propagate (WAIT)\n"
            + "- World switcher confirmation dialog: the test should retry (RETRY_LOGIN)\n"
            + "- Client crashed or frozen: the test should abort (ABORT)\n\n"
            + "Respond with EXACTLY this format (3 lines, no markdown):\n"
            + "ACTION: <RETRY_LOGIN|WAIT|ABORT>\n"
            + "REASON: <one line explanation of what you see>\n"
            + "DETAIL: <any additional detail about the screen state>";
    }

    private static String invokeClaude(String prompt) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
            "claude",
            "-p", prompt,
            "--allowedTools", "Read",
            "--no-input"
        );
        pb.redirectErrorStream(true);
        pb.environment().put("TERM", "dumb");

        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        boolean finished = process.waitFor(CLAUDE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            log.warn("Claude CLI timed out after {}s", CLAUDE_TIMEOUT_SECONDS);
            return null;
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            log.warn("Claude CLI exited with code {}: {}", exitCode, output);
        }

        return output.toString().trim();
    }

    private static AnalysisResult parseResponse(String response) {
        String upper = response.toUpperCase();

        SuggestedAction action = SuggestedAction.UNKNOWN;
        String reason = response;

        if (upper.contains("ACTION: RETRY_LOGIN") || upper.contains("ACTION:RETRY_LOGIN")) {
            action = SuggestedAction.RETRY_LOGIN;
        } else if (upper.contains("ACTION: WAIT") || upper.contains("ACTION:WAIT")) {
            action = SuggestedAction.WAIT;
        } else if (upper.contains("ACTION: ABORT") || upper.contains("ACTION:ABORT")) {
            action = SuggestedAction.ABORT;
        } else if (upper.contains("RETRY") || upper.contains("LOGIN")) {
            action = SuggestedAction.RETRY_LOGIN;
        } else if (upper.contains("WAIT") || upper.contains("LOADING")) {
            action = SuggestedAction.WAIT;
        } else if (upper.contains("ABORT") || upper.contains("ERROR") || upper.contains("BAN")) {
            action = SuggestedAction.ABORT;
        }

        for (String line : response.split("\n")) {
            if (line.toUpperCase().startsWith("REASON:")) {
                reason = line.substring("REASON:".length()).trim();
                break;
            }
        }

        return new AnalysisResult(action, reason, response);
    }
}
