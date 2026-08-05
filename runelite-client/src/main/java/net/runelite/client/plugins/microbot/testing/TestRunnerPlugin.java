package net.runelite.client.plugins.microbot.testing;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginInstantiationException;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.accountselector.AutoLoginPlugin;

import javax.inject.Inject;
import javax.swing.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@PluginDescriptor(
        name = "Test Runner",
        hidden = true,
        alwaysOn = true,
        priority = true
)
@Slf4j
public class TestRunnerPlugin extends Plugin {

    private static final String PROP_TEST_MODE = "microbot.test.mode";
    private static final String PROP_TEST_SCRIPT = "microbot.test.script";
    private static final String PROP_TEST_TIMEOUT = "microbot.test.timeout";

    @Inject
    private PluginManager pluginManager;

    @Inject
    private Client client;

    private ScheduledExecutorService executor;
    private boolean testStarted = false;

    @Override
    protected void startUp() {
        if (!isTestMode()) return;

        log.info("[TestRunner] Test mode active. Target plugin: {}", getTargetPluginName());
        executor = Executors.newSingleThreadScheduledExecutor();

        prepareTargetPlugin();
        enableAutoLogin();

        long timeout = getTimeout();
        executor.schedule(() -> {
            log.error("[TestRunner] Test timed out after {}ms", timeout);
            TestResult result = new TestResult(getTargetPluginName());
            result.complete("timeout");
            TestResultWriter.write(result);
            System.exit(2);
        }, timeout, TimeUnit.MILLISECONDS);
    }

    private void prepareTargetPlugin() {
        Plugin target = findTargetPlugin();
        if (target == null) {
            return;
        }

        try {
            if (pluginManager.isActive(target)) {
                pluginManager.stopPlugin(target);
            }
            // Test targets are process-scoped. Leaving this persisted as enabled makes them start
            // before the runner can observe a playable client on the next JVM launch.
            pluginManager.setPluginEnabled(target, false);
        } catch (PluginInstantiationException e) {
            throw new IllegalStateException("Unable to prepare target plugin " + getTargetPluginName(), e);
        }
    }

    private void enableAutoLogin() {
        for (Plugin plugin : pluginManager.getPlugins()) {
            if (plugin instanceof AutoLoginPlugin) {
                if (pluginManager.isPluginActive(plugin)) {
                    log.info("[TestRunner] AutoLogin already active");
                    return;
                }

                log.info("[TestRunner] Enabling AutoLogin plugin");
                pluginManager.setPluginEnabled(plugin, true);
                SwingUtilities.invokeLater(() -> {
                    try {
                        pluginManager.startPlugin(plugin);
                        log.info("[TestRunner] AutoLogin started");
                    } catch (PluginInstantiationException e) {
                        log.error("[TestRunner] Failed to start AutoLogin", e);
                        TestResult result = new TestResult(getTargetPluginName());
                        result.addError("Failed to start AutoLogin: " + e.getMessage());
                        result.complete("login_failure");
                        TestResultWriter.write(result);
                        System.exit(4);
                    }
                });
                return;
            }
        }
        log.warn("[TestRunner] AutoLogin plugin not found — login must happen externally");
    }

    @Override
    protected void shutDown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (!isTestMode()) return;
        if (testStarted) return;
        if (!isClientReady()) return;

        testStarted = true;
        log.info("[TestRunner] Client is playable. Starting target plugin...");

        executor.execute(this::enableTargetPlugin);
    }

    private boolean isClientReady() {
        Widget playWidget = client.getWidget(InterfaceID.WelcomeScreen.PLAY);
        boolean welcomeScreenVisible = playWidget != null && !playWidget.isHidden();
        return isClientReady(client.getGameState(), client.getLocalPlayer() != null, welcomeScreenVisible);
    }

    static boolean isClientReady(GameState gameState, boolean localPlayerAvailable, boolean welcomeScreenVisible) {
        return gameState == GameState.LOGGED_IN && localPlayerAvailable && !welcomeScreenVisible;
    }

    private void enableTargetPlugin() {
        String targetName = getTargetPluginName();
        log.info("[TestRunner] Looking for plugin: '{}'", targetName);

        Plugin plugin = findTargetPlugin();
        if (plugin != null) {
            PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
            log.info("[TestRunner] Found plugin: {} ({})", descriptor.name(), plugin.getClass().getSimpleName());
            pluginManager.setPluginEnabled(plugin, true);

            SwingUtilities.invokeLater(() -> {
                try {
                    boolean started = pluginManager.startPlugin(plugin);
                    log.info("[TestRunner] {} plugin: {}", started ? "Started" : "Target already active", descriptor.name());
                } catch (PluginInstantiationException e) {
                    log.error("[TestRunner] Failed to start plugin", e);
                    TestResult result = new TestResult(targetName);
                    result.addError("Failed to start plugin: " + e.getMessage());
                    result.complete("crash");
                    TestResultWriter.write(result);
                    System.exit(3);
                } finally {
                    // Keep the target active for this process without auto-starting it on the next run.
                    pluginManager.setPluginEnabled(plugin, false);
                }
            });
            return;
        }

        log.error("[TestRunner] Plugin '{}' not found! Available plugins:", targetName);
        for (Plugin availablePlugin : pluginManager.getPlugins()) {
            PluginDescriptor d = availablePlugin.getClass().getAnnotation(PluginDescriptor.class);
            if (d != null) log.error("  - {}", d.name());
        }

        TestResult result = new TestResult(targetName);
        result.addError("Plugin not found: " + targetName);
        result.complete("crash");
        TestResultWriter.write(result);
        System.exit(3);
    }

    private Plugin findTargetPlugin() {
        String targetName = getTargetPluginName();
        if (targetName.isBlank()) {
            return null;
        }

        for (Plugin plugin : pluginManager.getPlugins()) {
            PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
            if (descriptor != null && descriptor.name().contains(targetName)) {
                return plugin;
            }
        }
        return null;
    }

    private static boolean isTestMode() {
        return "true".equals(System.getProperty(PROP_TEST_MODE));
    }

    private static String getTargetPluginName() {
        return System.getProperty(PROP_TEST_SCRIPT, "");
    }

    private static long getTimeout() {
        try {
            return Long.parseLong(System.getProperty(PROP_TEST_TIMEOUT, "120000"));
        } catch (NumberFormatException e) {
            return 120000;
        }
    }
}
