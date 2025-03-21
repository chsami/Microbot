package net.runelite.client.plugins.microbot.scriptscheduler;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@PluginDescriptor(
        name = PluginDescriptor.Mocrosoft + "Script Scheduler",
        description = "Schedule different scripts to run at specific times",
        tags = {"microbot", "schedule", "automation"},
        enabledByDefault = false
)
public class ScriptSchedulerPlugin extends Plugin {
    @Inject
    private ScriptSchedulerConfig config;

    @Provides
    ScriptSchedulerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ScriptSchedulerConfig.class);
    }

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private ScheduledExecutorService executorService;

    private NavigationButton navButton;
    private ScriptSchedulerPanel panel;
    private ScheduledFuture<?> updateTask;
    private ScriptSchedulerWindow schedulerWindow;

    @Getter
    private ScheduledScript currentScript;

    private List<ScheduledScript> scheduledScripts = new ArrayList<>();
    @Getter
    private boolean isRunning = false;
    private ScheduledFuture<?> scriptStopTask;

    @Override
    protected void startUp() {
        panel = new ScriptSchedulerPanel(this, config);

        final BufferedImage icon = ImageUtil.loadImageResource(ScriptSchedulerPlugin.class, "icon.png");
        navButton = NavigationButton.builder()
                .tooltip("Script Scheduler")
                .priority(10)
                .icon(icon)
                .panel(panel)
                .build();

        clientToolbar.addNavigation(navButton);

        // Load saved schedules from config
        loadScheduledScripts();

        // Run the main loop
        updateTask = executorService.scheduleAtFixedRate(() -> {
            SwingUtilities.invokeLater(() -> {
                checkSchedule();
                updatePanels();
            });
        }, 0, 1, TimeUnit.SECONDS);
    }

    public void openSchedulerWindow() {
        if (schedulerWindow == null) {
            schedulerWindow = new ScriptSchedulerWindow(this);
        }

        if (!schedulerWindow.isVisible()) {
            schedulerWindow.setVisible(true);
        } else {
            schedulerWindow.toFront();
            schedulerWindow.requestFocus();
        }
    }

    @Override
    protected void shutDown() {
        clientToolbar.removeNavigation(navButton);
        stopCurrentScript();

        if (updateTask != null) {
            updateTask.cancel(false);
            updateTask = null;
        }

        if (schedulerWindow != null) {
            schedulerWindow.dispose();
            schedulerWindow = null;
        }
    }

    private void checkSchedule() {
        long currentTime = System.currentTimeMillis();

        for (ScheduledScript script : scheduledScripts) {
            if (script.isDueToRun(currentTime) && !isRunning) {
                // Run the script
                startScript(script);
                saveScheduledScripts();

                // Schedule script to stop if it has a duration
                if (script.getDuration() != null && !script.getDuration().isEmpty()) {
                    scheduleScriptStop(script);
                }

                // Only run one script at a time
                break;
            }
        }
    }

    private void scheduleScriptStop(ScheduledScript script) {
        // Cancel any existing stop task
        if (scriptStopTask != null && !scriptStopTask.isDone()) {
            scriptStopTask.cancel(false);
            scriptStopTask = null;
        }

        long durationMinutes = script.getDurationMinutes();
        if (durationMinutes > 0) {
            scriptStopTask = executorService.schedule(
                    this::stopCurrentScript,
                    durationMinutes,
                    TimeUnit.MINUTES
            );
        }
    }

    public void startScript(ScheduledScript script) {
        if (script == null) return;
        log.info("Starting scheduled script: " + script.getCleanName());
        currentScript = script;

        if (!script.start()) {
            log.error("Failed to start script: " + script.getCleanName());
            currentScript = null;
            isRunning = false;
            return;
        }

        if (!Microbot.isLoggedIn()) {
            Microbot.getClientThread().runOnClientThread(Login::new);
        }
        updatePanels();
    }

    public void stopCurrentScript() {
        if (currentScript != null) {
            log.info("Stopping current script: " + currentScript.getCleanName());
            if (currentScript.stop()) {
                currentScript = null;
                isRunning = false;
            } else {
                log.error("Failed to stop script: " + currentScript.getCleanName());
            }
        }
        updatePanels();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGIN_SCREEN) {
            stopCurrentScript();
        }
    }

    @Subscribe
    public void onPluginChanged(PluginChanged event) {
        if (currentScript != null && event.getPlugin() == currentScript.getPlugin() && !currentScript.isRunning()) {
            currentScript = null;
            isRunning = false;
            updatePanels();
        }
    }

    /**
     * Update all UI panels with the current state
     */
    private void updatePanels() {
        if (panel != null) {
            panel.updateScriptInfo();
            panel.updateNextScriptInfo();
        }

        if (schedulerWindow != null && schedulerWindow.isVisible()) {
            schedulerWindow.refreshData();
            schedulerWindow.updateControlButton();
        }
    }

    public void addScheduledScript(ScheduledScript script) {
        script.setLastRunTime(System.currentTimeMillis());
        scheduledScripts.add(script);
        saveScheduledScripts();
    }

    public void removeScheduledScript(ScheduledScript script) {
        scheduledScripts.remove(script);
        saveScheduledScripts();
    }

    public void updateScheduledScript(ScheduledScript oldScript, ScheduledScript newScript) {
        int index = scheduledScripts.indexOf(oldScript);
        if (index >= 0) {
            scheduledScripts.set(index, newScript);
            saveScheduledScripts();
        }
    }

    public List<ScheduledScript> getScheduledScripts() {
        return new ArrayList<>(scheduledScripts);
    }

    public void saveScheduledScripts() {
        // Convert to JSON and save to config
        String json = ScheduledScript.toJson(scheduledScripts);
        config.setScheduledScripts(json);
    }

    private void loadScheduledScripts() {
        // Load from config and parse JSON
        String json = config.scheduledScripts();
        if (json != null && !json.isEmpty()) {
            scheduledScripts = ScheduledScript.fromJson(json);
        }
    }

    public List<String> getAvailableScripts() {
        return Microbot.getPluginManager().getPlugins().stream()
                .filter(plugin -> {
                    PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
                    return descriptor != null && descriptor.canBeScheduled();
                })
                .map(Plugin::getName)
                .sorted()
                .collect(Collectors.toList());
    }

    public ScheduledScript getNextScheduledScript() {
        if (scheduledScripts.isEmpty()) {
            return null;
        }

        ScheduledScript nextScript = null;
        long earliestNextRun = Long.MAX_VALUE;

        for (ScheduledScript script : scheduledScripts) {
            if (!script.isEnabled()) {
                continue;
            }

            if (script.getNextRunTime() < earliestNextRun) {
                earliestNextRun = script.getNextRunTime();
                nextScript = script;
            }
        }

        return nextScript;
    }
}
