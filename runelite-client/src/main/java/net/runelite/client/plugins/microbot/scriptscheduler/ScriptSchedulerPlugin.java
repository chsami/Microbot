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
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.accountselector.AutoLoginPlugin;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerPlugin;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

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

        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        navButton = NavigationButton.builder()
                .tooltip("Script Scheduler")
                .priority(10)
                .icon(image)
                .panel(panel)
                .build();

        clientToolbar.addNavigation(navButton);

        // Load saved schedules from config
        loadScheduledScripts();

        executorService.schedule(() -> {
            if (panel != null) {
                panel.updateScriptListsWhenReady();
            }
        }, 2, TimeUnit.SECONDS);

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
            return;
        }

        schedulerTask = executorService.scheduleAtFixedRate(() -> {
            if (!isRunning) {
                checkSchedule();
            }
        }, 0, 10, TimeUnit.SECONDS);
    }

    private void stopScheduler() {
        if (schedulerTask != null) {
            schedulerTask.cancel(false);
            schedulerTask = null;
        }
    }

    private void checkSchedule() {
        LocalDateTime now = LocalDateTime.now();

        // Check if any scheduled script should run now
        for (ScheduledScript scheduledScript : scheduledScripts) {
            if (scheduledScript.shouldRunNow(now)) {
                // Stop current script if running
                stopCurrentScript();

                // Start the scheduled script
                startScript(scheduledScript.getScriptName());

                // Schedule script to stop after duration if specified
                scheduleScriptStop(scheduledScript);

                return;
            }
        }

        // If no scheduled script is running and default script is set, run it
        if (currentScript == null && config.defaultScript() != null && !config.defaultScript().isEmpty()) {
            startScript(config.defaultScript());
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
                Global.sleepUntil((Microbot::isLoggedIn), 10000);
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
        }
    }

    public void stopCurrentScript() {
        if (currentScript != null) {
            log.info("Stopping current script: " + currentScriptName);

            // Cancel any pending stop task
            if (scriptStopTask != null && !scriptStopTask.isDone()) {
                scriptStopTask.cancel(false);
                scriptStopTask = null;
            }
            Microbot.getClientThread().runOnSeperateThread(() -> {
                Microbot.stopPlugin(currentScript);
                return false;
            });
            currentScript = null;
            currentScriptName = null;
            scriptStartTime = null;
            isRunning = false;
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
        // Check if the current script has finished
        if (isRunning && currentScript != null && !Microbot.isPluginEnabled(currentScript.getClass())) {
            isRunning = false;
            currentScript = null;
            currentScriptName = null;
            scriptStartTime = null;
        }
    }

    public void addScheduledScript(ScheduledScript script) {
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

    private void saveScheduledScripts() {
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
                .filter(x -> x.getClass().getPackage().getName().toLowerCase().contains("microbot")
                        && !x.getClass().getSimpleName().equalsIgnoreCase("QuestHelperPlugin")
                        && !x.getClass().getSimpleName().equalsIgnoreCase("MInventorySetupsPlugin")
                        && !x.getClass().getSimpleName().equalsIgnoreCase("MicrobotPlugin")
                        && !x.getClass().getSimpleName().equalsIgnoreCase("MicrobotConfigPlugin")
                        && !x.getClass().getSimpleName().equalsIgnoreCase("ShortestPathPlugin")
                        && !x.getClass().getSimpleName().equalsIgnoreCase("AntibanPlugin")
                        && !x.getClass().getSimpleName().equalsIgnoreCase("ExamplePlugin"))
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