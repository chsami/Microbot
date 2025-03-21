package net.runelite.client.plugins.microbot.scriptscheduler;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
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
import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
    private Client client;

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
    private ScheduledFuture<?> schedulerTask;
    private ScriptSchedulerWindow schedulerWindow;

    @Getter
    private Plugin currentScript;

    @Getter
    private String currentScriptName;

    @Getter
    private LocalDateTime scriptStartTime;

    private List<ScheduledScript> scheduledScripts = new ArrayList<>();
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

        startScheduler();
    }

    public void openSchedulerWindow() {
        if (schedulerWindow == null) {
            schedulerWindow = new ScriptSchedulerWindow(this, this.config);
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
        stopScheduler();
        stopCurrentScript();

        if (schedulerWindow != null) {
            schedulerWindow.dispose();
            schedulerWindow = null;
        }
    }

    private void startScheduler() {
        if (schedulerTask != null && !schedulerTask.isDone()) {
            return; // Scheduler is already running
        }

        // Check for scripts to run every minute
        schedulerTask = executorService.scheduleAtFixedRate(() -> {
            try {
                checkSchedule();
            } catch (Exception e) {
                log.error("Error in script scheduler", e);
            }
        }, 0, 1, TimeUnit.MINUTES);
    }

    private void stopScheduler() {
        if (schedulerTask != null) {
            schedulerTask.cancel(false);
            schedulerTask = null;
        }
    }

    private void checkSchedule() {
        LocalDateTime now = LocalDateTime.now();

        for (ScheduledScript script : scheduledScripts) {
            if (script.isDueToRun(now) && !isRunning) {
                // Run the script
                startScript(script.getScriptName());

                // Update the last run time
                script.setLastRunTime(now);
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

    public void startScript(String scriptName) {
        if (scriptName == null || scriptName.isEmpty()) {
            return;
        }

        try {
            log.info("Starting scheduled script: " + scriptName);
            currentScriptName = scriptName;
            scriptStartTime = LocalDateTime.now();

            Plugin script = Microbot.getPluginManager().getPlugins().stream()
                    .filter(plugin -> Objects.equals(plugin.getName(), scriptName))
                    .findFirst()
                    .orElseThrow();

            currentScript = script;
            isRunning = true;
            if (!Microbot.isLoggedIn()) {
                new Login();
                Global.sleepUntil((Microbot::isLoggedIn), 20000);
            }
            Microbot.getClientThread().runOnSeperateThread(() -> {
                Microbot.startPlugin(script);
                return false;
            });
        } catch (Exception e) {
            log.error("Failed to start script: " + scriptName, e);
            currentScriptName = null;
            scriptStartTime = null;
            currentScript = null;
            isRunning = false;
            updatePanels();
        }
    }

    public void stopCurrentScript() {
        if (currentScript != null) {
            log.info("Stopping current script: " + currentScriptName);

            Microbot.getClientThread().runOnSeperateThread(() -> {
                Microbot.stopPlugin(currentScript);
                return false;
            });
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGIN_SCREEN) {
            stopCurrentScript();
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        updatePanels();
    }

    @Subscribe
    public void onPluginChanged(PluginChanged event) {
        if (event.getPlugin() == currentScript) {
            if (!Microbot.getPluginManager().isPluginEnabled(currentScript)) {
                currentScript = null;
                currentScriptName = null;
                scriptStartTime = null;
                isRunning = false;
            }
            updatePanels();
        }
    }

    /**
     * Update all UI panels with the current state
     */
    private void updatePanels() {
        // Update the main panel
        if (panel != null) {
            panel.updateScriptInfo();
            panel.updateNextScriptInfo();
        }

        // Update the scheduler window if it's open
        if (schedulerWindow != null && schedulerWindow.isVisible()) {
            schedulerWindow.updateControlButton();
        }
    }

    public void addScheduledScript(ScheduledScript script) {
        script.setLastRunTime(LocalDateTime.now());
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

    /**
     * Determines the next script that will run based on the current time and schedule.
     * @return The next ScheduledScript to run, or null if none are scheduled
     */
    public ScheduledScript getNextScheduledScript() {
        if (scheduledScripts.isEmpty()) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();

        ScheduledScript nextScript = null;
        LocalDateTime nextRunTime = null;

        for (ScheduledScript script : scheduledScripts) {
            if (!script.isEnabled()) {
                continue;
            }

            LocalDateTime scriptNextRun = script.getNextRunTime(now);

            // Update the next script if this one runs sooner
            if (scriptNextRun != null && (nextRunTime == null || scriptNextRun.isBefore(nextRunTime))) {
                nextRunTime = scriptNextRun;
                nextScript = script;
            }
        }

        return nextScript;
    }
}
