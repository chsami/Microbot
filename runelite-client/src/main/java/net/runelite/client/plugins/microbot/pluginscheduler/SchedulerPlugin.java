package net.runelite.client.plugins.microbot.pluginscheduler;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.slf4j.event.Level;

import com.google.inject.Provides;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Notification;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.accountselector.AutoLoginPlugin;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerConfig;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerPlugin;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.pluginscheduler.api.SchedulablePlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.event.PluginScheduleEntryFinishedEvent;
import net.runelite.client.plugins.microbot.pluginscheduler.model.PluginScheduleEntry;
import net.runelite.client.plugins.microbot.pluginscheduler.ui.SchedulerPanel;
import net.runelite.client.plugins.microbot.pluginscheduler.ui.SchedulerWindow;
import net.runelite.client.plugins.microbot.pluginscheduler.ui.Antiban.AntibanDialogWindow;
import net.runelite.client.plugins.microbot.pluginscheduler.ui.util.SchedulerUIUtils;
import net.runelite.client.plugins.microbot.util.antiban.AntibanPlugin;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.antiban.enums.CombatSkills;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(name = PluginDescriptor.Mocrosoft + PluginDescriptor.VOX
        + "Plugin Scheduler", description = "Schedule plugins at your will", tags = { "microbot", "schedule",
                "automation" }, enabledByDefault = false,priority = false)
public class SchedulerPlugin extends Plugin {
    public static String VERSION = "0.1.0";
    @Inject
    private SchedulerConfig config;
    final static String configGroup = "pluginscheduler";

    @Provides
    public SchedulerConfig provideConfig(ConfigManager configManager) {
        if (configManager == null) {
            return null;
        }
        return configManager.getConfig(SchedulerConfig.class);
    }

    @Inject
    private ClientToolbar clientToolbar;
    @Inject
    private ScheduledExecutorService executorService;

    private NavigationButton navButton;
    private SchedulerPanel panel;
    private ScheduledFuture<?> updateTask;
    private SchedulerWindow schedulerWindow;
    @Getter
    private PluginScheduleEntry currentPlugin;
    private List<PluginScheduleEntry> scheduledPlugins = new ArrayList<>();

    // private final Map<String, PluginScheduleEntry> nextPluginCache = new
    // HashMap<>();

    private int initCheckCount = 0;
    private static final int MAX_INIT_CHECKS = 10;

    @Getter
    private SchedulerState currentState = SchedulerState.UNINITIALIZED;
    private GameState lastGameState = GameState.UNKNOWN;

    // Activity and state tracking
    private final Map<Skill, Integer> skillExp = new EnumMap<>(Skill.class);
    private Skill lastSkillChanged;
    private Instant lastActivityTime = Instant.now();
    private Instant loginTime;
    private Activity currentActivity;
    private ActivityIntensity currentIntensity;
    @Getter
    private int idleTime = 0;

    // Break tracking

    private Duration currentBreakDuration = Duration.ZERO;
    private Duration timeUntilNextBreak = Duration.ZERO;
    private Optional<ZonedDateTime> breakStartTime = Optional.empty();
    // login tracking
    private Thread loginMonitor;
    @Inject
    private Notifier notifier;
        @Override
    protected void startUp() {

        panel = new SchedulerPanel(this);

        final BufferedImage icon = ImageUtil.loadImageResource(SchedulerPlugin.class, "calendar-icon.png");
        navButton = NavigationButton.builder()
                .tooltip("Plugin Scheduler")
                .priority(10)
                .icon(icon)
                .panel(panel)
                .build();

        clientToolbar.addNavigation(navButton);

        // Load saved schedules from config

        // Check initialization status before fully enabling scheduler
        //checkInitialization();

        // Run the main loop
        updateTask = executorService.scheduleWithFixedDelay(() -> {
            SwingUtilities.invokeLater(() -> {
                // Only run scheduling logic if fully initialized
                if (currentState.isSchedulerActive()) {
                    checkSchedule();
                } else if (currentState == SchedulerState.INITIALIZING
                        || currentState == SchedulerState.UNINITIALIZED) {
                    // Retry initialization check if not already checking
                    checkInitialization();
                }
                updatePanels();
            });
        }, 0, 1, TimeUnit.SECONDS);
    }

    /**
     * Checks if all required plugins are loaded and initialized.
     * This runs until initialization is complete or max check count is reached.
     */
    private void checkInitialization() {
        if (!currentState.isInitializing()) {
            return;
        }
        if (Microbot.getClientThread() == null || Microbot.getClient() == null) {
            return;
        }
        setState(SchedulerState.INITIALIZING);       
        // Schedule repeated checks until initialized or max checks reached
        
        Microbot.getClientThread().invokeLater(() -> {
            // Check if client is at login screen
            List<Plugin> conditionProviders = new ArrayList<>();
            if (Microbot.getPluginManager() == null || Microbot.getClient() == null) {
                return;

            } else {
                // Find all plugins implementing ConditionProvider
                conditionProviders = Microbot.getPluginManager().getPlugins().stream()
                        .filter(plugin -> plugin instanceof SchedulablePlugin)
                        .collect(Collectors.toList());
                List<Plugin> enabledList = conditionProviders.stream()
                        .filter(plugin -> Microbot.getPluginManager().isPluginEnabled(plugin))
                        .collect(Collectors.toList());
            }

            boolean isAtLoginScreen = Microbot.getClient().getGameState() == GameState.LOGIN_SCREEN;
            boolean isLoggedIn = Microbot.getClient().getGameState() == GameState.LOGGED_IN;
            boolean isAtLoginAuth = Microbot.getClient().getGameState() == GameState.LOGIN_SCREEN_AUTHENTICATOR;
            // If any conditions met, mark as initialized
            if (isAtLoginScreen || isLoggedIn || isAtLoginAuth) {
                log.info("Scheduler initialization complete - {} stopping condition providers loaded",
                        conditionProviders.size());

                loadScheduledPlugin();
                for (Plugin plugin : conditionProviders) {
                    try {
                        Microbot.getClientThread().runOnSeperateThread(() -> {
                            Microbot.stopPlugin(plugin);
                            return false;
                        });
                    } catch (Exception e) {
                    }
                }
                setState(SchedulerState.READY);

                // Initial cleanup of one-time plugins after loading
                cleanupCompletedOneTimePlugins();
            }
            // If max checks reached, mark as initialized but log warning
            else if (++initCheckCount >= MAX_INIT_CHECKS) {
                log.warn("Scheduler initialization timed out");
                loadScheduledPlugin();

                setState(SchedulerState.ERROR);
            }
            // Otherwise, schedule another check
            else {
                log.info("Waiting for initialization: loginScreen={}, providers={}/{}, checks={}/{}",
                        isAtLoginScreen,
                        conditionProviders.stream().count(),
                        conditionProviders.size(),
                        initCheckCount,
                        MAX_INIT_CHECKS);
                setState(SchedulerState.INITIALIZING);
                checkInitialization();
            }
        });

    }

    public void openSchedulerWindow() {
        if (schedulerWindow == null) {
            schedulerWindow = new SchedulerWindow(this);
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
        saveScheduledPlugins();
        clientToolbar.removeNavigation(navButton);
        forceStopCurrentPluginScheduleEntry(true);
        for (PluginScheduleEntry entry : scheduledPlugins) {
            entry.close();
        }
        if (this.loginMonitor != null && this.loginMonitor.isAlive()) {
            this.loginMonitor.interrupt();
            this.loginMonitor = null;
        }
        if (updateTask != null) {
            updateTask.cancel(false);
            updateTask = null;
        }

        if (schedulerWindow != null) {
            schedulerWindow.dispose(); // This will stop the timer
            schedulerWindow = null;
        }
        this.currentState = SchedulerState.UNINITIALIZED;
        this.lastGameState = GameState.UNKNOWN;
    }

    /**
     * Starts the scheduler
     */
    public void startScheduler() {
        Microbot.log("Starting scheduler request...", Level.INFO);
        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            // If already active, nothing to do
            if (currentState.isSchedulerActive()) {
                log.info("Scheduler already active");
                return true;
            }
            // If initialized, start immediately
            if (SchedulerState.READY == currentState || currentState == SchedulerState.HOLD) {
                setState(SchedulerState.SCHEDULING);
                log.info("Plugin Scheduler started");
                // Check schedule immediately when started
                SwingUtilities.invokeLater(() -> {
                    checkSchedule();
                });
                return true;
            }
            return true;
        });
        return;
    }

    /**
     * Stops the scheduler
     */
    public void stopScheduler() {
        if (loginMonitor != null && loginMonitor.isAlive()) {
            loginMonitor.interrupt();
        }
        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (!currentState.isSchedulerActive()) {
                return false; // Already stopped
            }
            setState(SchedulerState.HOLD);
            log.info("Stopping scheduler...");
            if (currentPlugin != null) {
                forceStopCurrentPluginScheduleEntry(true);
            }
            // Final state after fully stopped, disable the plugins we auto-enabled
            if (isBreakHandlerEnabled() && config.enableBreakHandlerAutomatically()) {
                if (disableBreakHandler()) {
                    log.info("Automatically disabled BreakHandler plugin");
                }
            }

            setState(SchedulerState.HOLD);
            if(config.autoLogOutOnStop()){
                logout();
            }

            log.info("Scheduler stopped - status: {}", currentState);
            return false;
        });
    }

    private void checkSchedule() {
        // Update break status
        boolean isOnBreak = isOnBreak();
        if (SchedulerState.LOGIN == currentState ||
                SchedulerState.WAITING_FOR_LOGIN == currentState ||
                SchedulerState.HARD_STOPPING_PLUGIN == currentState ||
                SchedulerState.SOFT_STOPPING_PLUGIN == currentState ||
                currentState == SchedulerState.HOLD) {
            return;
        }
        // First, check if we need to stop the current plugin
        if (isScheduledPluginRunning()) {
            checkCurrentPlugin();

        }
        // If no plugin is running, check for scheduled plugins
        if (!isScheduledPluginRunning()) {
            int minTimeToNextScheduleForTakingABreak = config.minTimeToNextScheduleForTakingABreak();
            PluginScheduleEntry nextPluginWith = null;
            PluginScheduleEntry nextPluginPossible = getNextScheduledPlugin(false, null).orElse(null);
            
            if (minTimeToNextScheduleForTakingABreak == 0) { // 0 means no break
                minTimeToNextScheduleForTakingABreak = 1;
                nextPluginWith = getNextScheduledPlugin(true, null).orElse(null);
            } else {
                minTimeToNextScheduleForTakingABreak = Math.max(1, minTimeToNextScheduleForTakingABreak);
                // Get the next scheduled plugin within minTimeToNextScheduleForTakingABreak
                nextPluginWith = getNextScheduledPluginWithinTime(
                        Duration.ofMinutes(minTimeToNextScheduleForTakingABreak));
            }

            if (    nextPluginWith == null && 
                    nextPluginPossible != null && 
                    !nextPluginPossible.hasOnlyTimeConditions() 
                    && !isOnBreak && !Microbot.isLoggedIn() ){                    
                // when the the next possible plugin is not a time condition and we are not logged in
                log.info("Login required before the next possible plugin can run -> must check" + nextPluginPossible.getCleanName());
                startLoginMonitoringThread();
                return;                
            }

            if (nextPluginWith != null) {
                boolean nextWithinFlag = false;

                int withinSeconds = Rs2Random.between(15, 30); // is there plugin upcoming within 15-30, than we stop
                                                               // the break
                
                if (nextPluginWith.getCurrentStartTriggerTime().isPresent()) {
                    nextWithinFlag = Duration
                            .between(ZonedDateTime.now(ZoneId.systemDefault()),
                                    nextPluginWith.getCurrentStartTriggerTime().get())
                            .compareTo(Duration.ofSeconds(withinSeconds)) < 0;
                } else {
                    if (nextPluginWith.isDueToRun()) {
                        nextWithinFlag = true;
                    }else {
                        
                    }
                }
                // If we're on a break, interrupt it

                if (isOnBreak && (nextWithinFlag)) {
                    log.info("\n\tInterrupting active break to start scheduled plugin: {}", nextPluginWith.getCleanName());
                    interruptBreak();

                }
                if (currentState == SchedulerState.SHORT_BREAK && nextWithinFlag) {
                    setState(SchedulerState.WAITING_FOR_SCHEDULE);
                }

                if (!currentState.isActivelyRunning() && !currentState.isAboutStarting()) {
                    
                    
                    scheduleNextPlugin();
                } else {
                    if(currentPlugin==null){                                                
                        currentState = SchedulerState.WAITING_FOR_SCHEDULE;                        
                    }else{
                        if (!currentPlugin.isRunning() && !currentState.isAboutStarting()) {
                            currentState = SchedulerState.WAITING_FOR_SCHEDULE;
                            log.info("Plugin is not running, and it not about to start");
                        }
                        checkCurrentPlugin();
                        
                    }
                    

                }
            } else {
                // If we're not on a break and there's nothing running, take a short break until
                // next plugin
                if (!isOnBreak() &&
                        currentState != SchedulerState.WAITING_FOR_SCHEDULE &&
                        currentState == SchedulerState.SCHEDULING) {
                    
                    int breakDuration = Math.max(minTimeToNextScheduleForTakingABreak, config.maxBreakDuratation());
                    startShortBreakUntilNextPlugin(config.autoLogOutOnBreak(), breakDuration);// short break with logout and max 3 minutes
                }else if(currentState != SchedulerState.WAITING_FOR_SCHEDULE && currentState == SchedulerState.SHORT_BREAK){
                    //make a resume break function  when no plugin is upcoming and the left break time is smaller than "threshold"
                    //currentBreakDuration  -> last set break duration type "Duration"
                    //breakStartTime breakStartTime -> last set break start time type "Optional<ZonedDateTime>"
                    //breakStartTime.get().plus(currentBreakDuration) -> break end time type "ZonedDateTime"
                    extendBreakIfNeeded(nextPluginWith, 30);
                }
            }

        }
        // Clean up completed one-time plugins
        cleanupCompletedOneTimePlugins();

    }

    /**
     * Interrupts an active break to allow a plugin to start
     */
    private void interruptBreak() {
        
        currentBreakDuration = Duration.ZERO;
        breakStartTime = Optional.empty();

        if (!isBreakHandlerEnabled()) {
            return;
        }

        log.info("Interrupting active break to start scheduled plugin");

        // Set break duration to 0 to end the break
        BreakHandlerScript.breakDuration = 0;

        // Also reset the breakNow setting if it was set
        Microbot.getConfigManager().setConfiguration(BreakHandlerConfig.configGroup, "breakNow", false);

        // Ensure we're not locked for future breaks
        unlockBreakHandler();

        // Wait a moment for the break to fully end
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (BreakHandlerScript.isBreakActive()) {
            SwingUtilities.invokeLater(() -> {
                log.info("Break was not interrupted successfully");
                interruptBreak();
            });
            return;
        }
        log.info("\nBreak interrupted successfully");
        if (currentState == SchedulerState.SHORT_BREAK) {
            // If we were on a short break, reset the state to scheduling
            setState(SchedulerState.SCHEDULING);
        } else {
            // Otherwise, set to waiting for schedule
            setState(SchedulerState.WAITING_FOR_SCHEDULE);
        }
    }

    /**
     * Starts a short break until the next plugin is scheduled to run
     */
    private boolean startShortBreakUntilNextPlugin(boolean logout, 
        int beakDurationMinutes) {
        if (!isBreakHandlerEnabled()) {
            return false;
        }
        if (BreakHandlerScript.isLockState())
            BreakHandlerScript.setLockState(false);
        PluginScheduleEntry nextPlugin = getNextScheduledPlugin();
        Duration timeUntilNext = Duration.ZERO;
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        Microbot.getConfigManager().setConfiguration(BreakHandlerConfig.configGroup, "Logout", logout);
        if (nextPlugin == null) {
            timeUntilNext = Duration.between(now, now.plusMinutes(beakDurationMinutes));
        } else {
            Optional<ZonedDateTime> nextStartTime = nextPlugin.getCurrentStartTriggerTime();
            if (!nextStartTime.isPresent()) {
                // just a random time until next plugin
                timeUntilNext = Duration.ofSeconds(Rs2Random.between(60, Math.max(80, beakDurationMinutes * 60)));
            } else {
                timeUntilNext = Duration.between(now, nextStartTime.get());
            }
        }

        // Only start a break if we have more than 60 seconds until the next plugin
        if (timeUntilNext.getSeconds() <= 60) {

            return false;
        }
        if (nextPlugin != null) {
            log.info("Starting short break until next plugin: {} (scheduled in {})",
                    nextPlugin.getCleanName(), formatDuration(timeUntilNext));
        }

        // Subtract 10 seconds to ensure we're back before the plugin needs to start
        long breakSeconds = Math.max(10, timeUntilNext.getSeconds() - 10);

        // Configure a break that ends just before the next plugin starts
        BreakHandlerScript.breakDuration = (int) breakSeconds;
        currentBreakDuration = Duration.ofSeconds(breakSeconds);
        BreakHandlerScript.breakIn = 0;

        // Set state to indicate we're in a controlled short break
        sleepUntil(() -> BreakHandlerScript.isBreakActive(), 1000);
        
        if (!BreakHandlerScript.isBreakActive()) {
            log.info("Break handler is not locked, unable to start short break");
            return false;
        }
        setState(SchedulerState.SHORT_BREAK);

        return true;
    }

    /**
     * Format a duration for display
     */
    private String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.getSeconds() % 60;

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }

    /**
     * Schedules the next plugin to run if none is running
     */
    private void scheduleNextPlugin() {
        // Check if a non-default plugin is coming up soon
        boolean prioritizeNonDefaultPlugins = config.prioritizeNonDefaultPlugins();
        int nonDefaultPluginLookAheadMinutes = config.nonDefaultPluginLookAheadMinutes();
        
        if (prioritizeNonDefaultPlugins) {
            // Look for any upcoming non-default plugin within the configured time window
            PluginScheduleEntry upcomingNonDefault = getNextScheduledPlugin(false, Duration.ofMinutes(nonDefaultPluginLookAheadMinutes))
                .filter(plugin -> !plugin.isDefault())
                .orElse(null);
                
            // If we found an upcoming non-default plugin, check if it's already due to run
            if (upcomingNonDefault != null && !upcomingNonDefault.isDueToRun()) {
                // Get the next plugin that's due to run now
                Optional<PluginScheduleEntry> nextDuePlugin = getNextScheduledPlugin(true, null);
                
                // If the next due plugin is a default plugin, don't start it
                // Instead, wait for the non-default plugin
                if (nextDuePlugin.isPresent() && nextDuePlugin.get().isDefault()) {
                    log.info("Not starting default plugin '{}' because non-default plugin '{}' is scheduled within {} minutes",
                        nextDuePlugin.get().getCleanName(),
                        upcomingNonDefault.getCleanName(),
                        nonDefaultPluginLookAheadMinutes);
                    return;
                }
            }
        }
        
        // Get the next plugin that's due to run
        Optional<PluginScheduleEntry> selected = getNextScheduledPlugin(true, null);
        if (selected.isEmpty()) {
            return;
        }
        
        // If we're on a break, interrupt it, only we have initialized the break
        if (isOnBreak() && currentBreakDuration != null && currentBreakDuration.getSeconds() > 0) {
            log.info("\nInterrupting active break to start scheduled plugin: \n\t{}", selected.get().getCleanName());
            interruptBreak();
        }
     
       
        log.info("\nStarting scheduled plugin: \n\t{}\ncurrent state \n\t{}", selected.get().getCleanName(),this.currentState);
        startPluginScheduleEntry(selected.get());
        if (!selected.get().isRunning()) {
            saveScheduledPlugins();
        }
    }

    /**
     * Selects a plugin using weighted random selection.
     * Plugins with lower run counts have higher probability of being selected.
     */
    private PluginScheduleEntry selectPluginWeighted(List<PluginScheduleEntry> plugins) {
        // Return the only plugin if there's just one
        if (plugins.size() == 1) {
            return plugins.get(0);
        }

        // Calculate weights - plugins with lower run counts get higher weights
        // Find the maximum run count
        int maxRuns = plugins.stream()
                .mapToInt(PluginScheduleEntry::getRunCount)
                .max()
                .orElse(0);

        // Add 1 to avoid division by zero and to ensure all plugins have some chance
        maxRuns = maxRuns + 1;

        // Calculate weights as (maxRuns + 1) - runCount for each plugin
        // This gives higher weight to plugins that have run less often
        double[] weights = new double[plugins.size()];
        double totalWeight = 0;

        for (int i = 0; i < plugins.size(); i++) {
            // Weight = (maxRuns + 1) - plugin's run count
            weights[i] = maxRuns - plugins.get(i).getRunCount() + 1;
            totalWeight += weights[i];
        }

        // Generate random value between 0 and totalWeight
        double randomValue = Math.random() * totalWeight;

        // Select plugin based on random value and weights
        double weightSum = 0;
        for (int i = 0; i < plugins.size(); i++) {
            weightSum += weights[i];
            if (randomValue < weightSum) {
                // Log the selection for debugging
                log.debug("Selected plugin '{}' with weight {}/{} (run count: {})",
                        plugins.get(i).getCleanName(),
                        weights[i],
                        totalWeight,
                        plugins.get(i).getRunCount());
                return plugins.get(i);
            }
        }

        // Fallback to the last plugin (shouldn't normally happen)
        return plugins.get(plugins.size() - 1);
    }

    public void startPluginScheduleEntry(PluginScheduleEntry scheduledPlugin) {

        Microbot.getClientThread().runOnClientThreadOptional(() -> {

            if (scheduledPlugin == null)
                return false;
            // Ensure BreakHandler is enabled when we start a plugin
            if (!isBreakHandlerEnabled() && config.enableBreakHandlerAutomatically()) {
                log.info("Start enabling BreakHandler plugin");
                if (enableBreakHandler()) {
                    log.info("Automatically enabled BreakHandler plugin");
                }
            }

            // Ensure Antiban is enabled when we start a plugin -> should be allways
            // enabled?
            if (!isAntibanEnabled()) {
                log.info("Start enabling Antiban plugin");
                if (enableAntiban()) {
                    log.info("Automatically enabled Antiban plugin");
                }
            }
            
            // Ensure break handler is unlocked before starting a plugin
            unlockBreakHandler();

            // If we're on a break, interrupt it
            if (isOnBreak()) {
                interruptBreak();
            }
            SchedulerState stateBeforeScheduling = currentState;
            
            currentPlugin = scheduledPlugin;

            // Check for stop conditions if enforcement is enabled -> ensure we have stop
            // condition so the plugin doesn't run forever (only manual stop possible
            // otherwise)
            if (config.enforceTimeBasedStopCondition() && scheduledPlugin.isNeedsStopCondition()
                    && scheduledPlugin.getStopConditionManager().getTimeConditions().isEmpty() && SchedulerState.SCHEDULING == currentState) {
                // If the user chooses to add stop conditions, we wait for them to be added
                // and then continue the scheduling process
                // If the user chooses not to add stop conditions, we proceed with the plugin
                // start
                // If the user cancels, we reset the state and do not start the plugin
                // Show confirmation dialog on EDT to prevent blocking
                // Start the dialog in a separate thread to avoid blocking the EDT
                setState(SchedulerState.WAITING_FOR_STOP_CONDITION);
                startAddStopConditionDialog(scheduledPlugin, stateBeforeScheduling);
                log.info("No stop conditions set for plugin: " + scheduledPlugin.getCleanName());
                return false;
            } else {
                if (currentState != SchedulerState.STARTING_PLUGIN){
                    setState(SchedulerState.STARTING_PLUGIN);
                    // Stop conditions exist or enforcement disabled - proceed normally
                    continueStartingPluginScheduleEntry(scheduledPlugin);
                }
                return true;
            }
        });
    }

    private void startAddStopConditionDialog(PluginScheduleEntry scheduledPlugin,
            SchedulerState stateBeforeScheduling) {
        // Show confirmation dialog on EDT to prevent blocking
        Microbot.getClientThread().runOnSeperateThread(() -> {
            // Create dialog with timeout
            final JOptionPane optionPane = new JOptionPane(
                    "Plugin '" + scheduledPlugin.getCleanName() + "' has no stop time based conditions set.\n" +
                            "It will run until manually stopped or a other condition (when defined).\n\n" +
                            "Would you like to configure stop conditions now?",
                    JOptionPane.QUESTION_MESSAGE,
                    JOptionPane.YES_NO_CANCEL_OPTION);

            final JDialog dialog = optionPane.createDialog("No Stop Conditions");

            // Create timer for dialog timeout
            int timeoutSeconds = config.dialogTimeoutSeconds();
            if (timeoutSeconds <= 0) {
                timeoutSeconds = 30; // Default timeout if config value is invalid
            }

            final Timer timer = new Timer(timeoutSeconds * 1000, e -> {
                dialog.setVisible(false);
                dialog.dispose();
            });
            timer.setRepeats(false);
            timer.start();

            // Update dialog title to show countdown
            final int finalTimeoutSeconds = timeoutSeconds;
            final Timer countdownTimer = new Timer(1000, new ActionListener() {
                int remainingSeconds = finalTimeoutSeconds;

                @Override
                public void actionPerformed(ActionEvent e) {
                    remainingSeconds--;
                    if (remainingSeconds > 0) {
                        dialog.setTitle("No Stop Conditions (Timeout: " + remainingSeconds + "s)");
                    } else {
                        dialog.setTitle("No Stop Conditions (Timing out...)");
                    }
                }
            });
            countdownTimer.start();

            try {
                dialog.setVisible(true); // blocks until dialog is closed or timer expires
            } finally {
                timer.stop();
                countdownTimer.stop();
            }

            // Handle user choice or timeout
            Object selectedValue = optionPane.getValue();
            int result = selectedValue instanceof Integer ? (Integer) selectedValue : JOptionPane.CLOSED_OPTION;
            log.info("User selected: " + result);
            if (result == JOptionPane.YES_OPTION) {
                // User wants to add stop conditions
                openSchedulerWindow();
                if (schedulerWindow != null) {
                    // Switch to stop conditions tab
                    schedulerWindow.selectPlugin(scheduledPlugin);
                    schedulerWindow.switchToStopConditionsTab();
                    schedulerWindow.toFront();

                    // Start a timer to check if conditions have been added
                    int conditionTimeoutSeconds = config.conditionConfigTimeoutSeconds();
                    if (conditionTimeoutSeconds <= 0) {
                        conditionTimeoutSeconds = 60; // Default if config value is invalid
                    }

                    final Timer conditionTimer = new Timer(conditionTimeoutSeconds * 1000, evt -> {
                        // Check if any time conditions have been added
                        if (scheduledPlugin.getStopConditionManager().getConditions().isEmpty()) {
                            log.info("No conditions added within timeout period. Returning to previous state.");
                            currentPlugin = null;
                            setState(stateBeforeScheduling);

                            SwingUtilities.invokeLater(() -> {
                                JOptionPane.showMessageDialog(
                                        schedulerWindow,
                                        "No time conditions were added within the timeout period.\n" +
                                                "Plugin start has been canceled.",
                                        "Configuration Timeout",
                                        JOptionPane.WARNING_MESSAGE);
                            });
                        }else{
                            // Stop the timer if conditions are added
                            
                            log.info("Stop conditions added successfully for plugin: " + scheduledPlugin.getCleanName());
                            setState(SchedulerState.STARTING_PLUGIN);
                            continueStartingPluginScheduleEntry(scheduledPlugin);
                        }
                    });
                    conditionTimer.setRepeats(false);
                    conditionTimer.start();
                }
            } else if (result == JOptionPane.NO_OPTION) {
                setState(SchedulerState.STARTING_PLUGIN);
                // User confirms to run without stop conditions
                continueStartingPluginScheduleEntry(scheduledPlugin);
                scheduledPlugin.setNeedsStopCondition(false);
                log.info("User confirmed to run plugin without stop conditions: {}", scheduledPlugin.getCleanName());
            } else {
                // User canceled or dialog timed out - abort starting
                log.info("Plugin start canceled by user or timed out: {}", scheduledPlugin.getCleanName());
                scheduledPlugin.setNeedsStopCondition(false);
                currentPlugin = null;
                setState(stateBeforeScheduling);
            }
            return null;
        });
    }

    /**
     * Resets any pending plugin start operation
     */
    public void resetPendingStart() {
        if (currentState == SchedulerState.STARTING_PLUGIN || currentState == SchedulerState.WAITING_FOR_LOGIN || 
                currentState == SchedulerState.WAITING_FOR_STOP_CONDITION) {

            currentPlugin = null;
            setState(SchedulerState.SCHEDULING);
        }
    }
    public void continuePendingStart(PluginScheduleEntry scheduledPlugin) {
        if (currentState == SchedulerState.WAITING_FOR_STOP_CONDITION ) {            
            if (currentPlugin != null && !currentPlugin.isRunning()  && currentPlugin.equals(scheduledPlugin)) {
                setState(SchedulerState.STARTING_PLUGIN);   
                log.info("Continuing pending start for plugin: " + scheduledPlugin.getCleanName());
                this.continueStartingPluginScheduleEntry(scheduledPlugin);                
            }
        }
    }
    /**
     * Continues the plugin starting process after stop condition checks
     */
    private void continueStartingPluginScheduleEntry(PluginScheduleEntry scheduledPlugin) {        
        if (scheduledPlugin == null || currentState  != SchedulerState.STARTING_PLUGIN) {
            currentPlugin = null;
            setState(SchedulerState.SCHEDULING);                
            return;
        }
        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (scheduledPlugin.isRunning()) {
                log.info("Plugin started successfully: " + scheduledPlugin.getCleanName());    
                setState(SchedulerState.RUNNING_PLUGIN);
                return true;
            }
            if (!Microbot.isLoggedIn()) {
                log.info("Login required before running plugin: " + scheduledPlugin.getCleanName());
                startLoginMonitoringThread();
                return false;
            }
            if (!scheduledPlugin.start(false)) {
                log.error("Failed to start plugin: " + scheduledPlugin.getCleanName());
                currentPlugin = null;
                setState(SchedulerState.SCHEDULING);
                return false;
            }
          
            Microbot.getClientThread().invokeLater( ()->{
               continueStartingPluginScheduleEntry(scheduledPlugin);
            });
            return false;
            
            
        });
    }

    public void forceStopCurrentPluginScheduleEntry(boolean successful) {
        if (currentPlugin != null && currentPlugin.isRunning()) {
            log.info("Force Stopping current plugin: " + currentPlugin.getCleanName());
            if (currentState == SchedulerState.RUNNING_PLUGIN) {
                setState(SchedulerState.HARD_STOPPING_PLUGIN);
            }
            currentPlugin.hardStop(successful);
            // Wait a short time to see if the plugin stops immediately
            if (currentPlugin != null) {

                if (!currentPlugin.isRunning()) {
                    log.info("Plugin stopped successfully: " + currentPlugin.getCleanName());

                } else {
                    SwingUtilities.invokeLater(() -> {
                        forceStopCurrentPluginScheduleEntry(successful);
                    });
                    log.info("Failed to hard stop plugin: " + currentPlugin.getCleanName());
                }
            }
        }
        updatePanels();
    }

    /**
     * Update all UI panels with the current state
     */
    void updatePanels() {
        if (panel != null) {
            panel.refresh();
        }

        if (schedulerWindow != null && schedulerWindow.isVisible()) {
            schedulerWindow.refresh();
        }
    }

    public void addScheduledPlugin(PluginScheduleEntry plugin) {
        plugin.setLastRunTime(ZonedDateTime.now(ZoneId.systemDefault()));
        scheduledPlugins.add(plugin);
    }

    public void removeScheduledPlugin(PluginScheduleEntry plugin) {
        plugin.setEnabled(false);
        scheduledPlugins.remove(plugin);
    }

    public void updateScheduledPlugin(PluginScheduleEntry oldPlugin, PluginScheduleEntry newPlugin) {
        int index = scheduledPlugins.indexOf(oldPlugin);
        if (index >= 0) {
            scheduledPlugins.set(index, newPlugin);
        }
    }


    /**
     * Adds conditions to a scheduled plugin with support for saving to a specific file
     * 
     * @param plugin The plugin to add conditions to
     * @param userStopConditions List of stop conditions
     * @param userStartConditions List of start conditions
     * @param requireAll Whether all conditions must be met
     * @param stopOnConditionsMet Whether to stop the plugin when conditions are met
     * @param saveFile Optional file to save the conditions to, or null to use default config
     */
    public void saveUserConditionsToScheduledPlugin(PluginScheduleEntry plugin, List<Condition> userStopConditions,
            List<Condition> userStartConditions, boolean requireAll, boolean stopOnConditionsMet, File saveFile) {
        if (plugin == null)
            return;
        List<Condition> stopPluginConditions  = plugin.getStopConditionManager().getPluginCondition().getConditions();
        
        // Remove any existing stop conditions which are not user-defined
        for (Condition condition : userStopConditions) {
            if (stopPluginConditions.contains(condition)) {
                userStopConditions.remove(condition);
            }
        }
        // Clear existing conditions
        plugin.getStopConditionManager().getUserConditions().clear();

        // Add new user conditions
        for (Condition condition : userStopConditions) {
            plugin.addStopCondition(condition);
        }
        
        // Add start conditions if provided
        if (userStartConditions != null && !userStartConditions.isEmpty()) {
            List<Condition> startPluginConditions  = plugin.getStartConditionManager().getPluginCondition().getConditions();        
            // Remove any existing start conditions which are not user-defined -> is a plugin condition, avoid duplication
            for (Condition condition : userStartConditions) {
                if (startPluginConditions.contains(condition)) {
                    userStartConditions.remove(condition);
                }
            }
            plugin.getStartConditionManager().getUserConditions().clear();

            for (Condition condition : userStartConditions) {
                plugin.addStartCondition(condition);
            }
        }

        // Set condition manager properties
        if (requireAll) {
            plugin.getStopConditionManager().setRequireAll();
        } else {
            plugin.getStopConditionManager().setRequireAny();
        }

        // Save to specified file if provided, otherwise to config
        if (saveFile != null) {
            saveScheduledPluginsToFile(saveFile);
        } else {
            // Save to config
            saveScheduledPlugins();
        }
    }
    
    /**
     * Saves scheduled plugins to a specific file
     * 
     * @param file The file to save to
     * @return true if save was successful, false otherwise
     */
    public boolean saveScheduledPluginsToFile(File file) {
        try {
            // Convert to JSON
            String json = PluginScheduleEntry.toJson(scheduledPlugins, this.VERSION);
            
            // Write to file
            java.nio.file.Files.writeString(file.toPath(), json);
            log.info("Saved scheduled plugins to file: {}", file.getAbsolutePath());
            return true;
        } catch (Exception e) {
            log.error("Error saving scheduled plugins to file", e);
            return false;
        }
    }
    
    /**
     * Loads scheduled plugins from a specific file
     * 
     * @param file The file to load from
     * @return true if load was successful, false otherwise
     */
    public boolean loadScheduledPluginsFromFile(File file) {
        try {
            stopScheduler();
            if(currentPlugin != null && currentPlugin.isRunning()){
                forceStopCurrentPluginScheduleEntry(false);
                log.info("Stopping current plugin before loading new schedule");                
            }
            sleepUntil(() -> (currentPlugin == null || !currentPlugin.isRunning()), 2000);
            // Read JSON from file
            String json = java.nio.file.Files.readString(file.toPath());
            log.info("Loading scheduled plugins from file: {}", file.getAbsolutePath());
            
            // Parse JSON
            List<PluginScheduleEntry> loadedPlugins = PluginScheduleEntry.fromJson(json,  this.VERSION); 
            if (loadedPlugins == null) {
                log.error("Failed to parse JSON from file");
                return false;
            }
            
            // Resolve plugin references
            for (PluginScheduleEntry entry : loadedPlugins) {
                resolvePluginReferences(entry);
            }
            
            // Replace current plugins
            scheduledPlugins = loadedPlugins;
            
            // Update UI
            SwingUtilities.invokeLater(this::updatePanels);
            return true;
        } catch (Exception e) {
            log.error("Error loading scheduled plugins from file", e);
            return false;
        }
    }
    
    /**
     * Adds stop conditions to a scheduled plugin
     */
    public void saveUserStopConditionsToPlugin(PluginScheduleEntry plugin, List<Condition> conditions,
            boolean requireAll, boolean stopOnConditionsMet) {
        // Call the enhanced version with null file to use default config
        saveUserConditionsToPlugin(plugin, conditions, null, requireAll, stopOnConditionsMet, null);
    }
    
    /**
     * Adds conditions to a scheduled plugin with support for saving to a specific file
     * 
     * @param plugin The plugin to add conditions to
     * @param stopConditions List of stop conditions
     * @param startConditions List of start conditions (optional, can be null)
     * @param requireAll Whether all conditions must be met
     * @param stopOnConditionsMet Whether to stop the plugin when conditions are met
     * @param saveFile Optional file to save the conditions to, or null to use default config
     */
    public void saveUserConditionsToPlugin(PluginScheduleEntry plugin, List<Condition> stopConditions,
            List<Condition> startConditions, boolean requireAll, boolean stopOnConditionsMet, File saveFile) {
        if (plugin == null)
            return;

        // Clear existing stop conditions
        plugin.getStopConditionManager().getUserConditions().clear();

        // Add new stop conditions
        for (Condition condition : stopConditions) {
            plugin.addStopCondition(condition);
        }
        
        // Add start conditions if provided
        if (startConditions != null) {
            plugin.getStartConditionManager().getUserConditions().clear();
            for (Condition condition : startConditions) {
                plugin.addStartCondition(condition);
            }
        }

        // Set condition manager properties
        if (requireAll) {
            plugin.getStopConditionManager().setRequireAll();
        } else {
            plugin.getStopConditionManager().setRequireAny();
        }

        // Save to specified file if provided, otherwise to config
        if (saveFile != null) {
            saveScheduledPluginsToFile(saveFile);
        } else {
            // Save to config
            saveScheduledPlugins();
        }
    }

    /**
     * Gets the list of plugins that have stop conditions set
     */
    public List<PluginScheduleEntry> getScheduledPluginsWithStopConditions() {
        return scheduledPlugins.stream()
                .filter(p -> !p.getStopConditionManager().getConditions().isEmpty())
                .collect(Collectors.toList());
    }

    public List<PluginScheduleEntry> getScheduledPlugins() {
        return new ArrayList<>(scheduledPlugins);
    }

    public void saveScheduledPlugins() {
        // Convert to JSON and save to config
        String json = PluginScheduleEntry.toJson(scheduledPlugins, this.VERSION);

        // log.info("Saving scheduled plugins to config: {}", json);
        // config.setScheduledPlugins(json);
        if (Microbot.getConfigManager() == null) {
            return;
        }
        Microbot.getConfigManager().setConfiguration(SchedulerConfig.CONFIG_GROUP, "scheduledPlugins", json);

    }

    private void loadScheduledPlugin() {
        try {
            // Load from config and parse JSON
            if (Microbot.getConfigManager() == null) {
                return;
            }
            String json = Microbot.getConfigManager().getConfiguration(SchedulerConfig.CONFIG_GROUP,
                    "scheduledPlugins");
            log.info("Loading scheduled plugins from config: {}\n\n", json);

            if (json != null && !json.isEmpty()) {
                scheduledPlugins = PluginScheduleEntry.fromJson(json,  this.VERSION);

                // Apply stop settings from config to all loaded plugins
                for (PluginScheduleEntry plugin : scheduledPlugins) {
                    // Set timeout values from config
                    plugin.setSoftStopRetryInterval(Duration.ofSeconds(config.softStopRetrySeconds()));
                    plugin.setHardStopTimeout(Duration.ofSeconds(config.hardStopTimeoutSeconds()));

                    // Resolve plugin references
                    resolvePluginReferences(plugin);

                    StringBuilder logMessage = new StringBuilder();
                    logMessage.append(String.format("\nLoaded scheduled plugin: %s with %d conditions:\n",
                            plugin.getName(),
                            plugin.getStopConditionManager().getConditions().size()));

                    // Stop conditions section
                    logMessage.append(String.format("Stop user condition (%d):\n\t%s\n",
                            plugin.getStopConditionManager().getUserLogicalCondition().getTotalConditionCount(),
                            plugin.getStopConditionManager().getUserLogicalCondition().getDescription()));
                    logMessage.append(String.format("Stop plugin conditions (%d):\n\t%s\n",
                            plugin.getStopConditionManager().getPluginCondition().getTotalConditionCount(),
                            plugin.getStopConditionManager().getPluginCondition().getDescription()));

                    // Start conditions section
                    logMessage.append(String.format("Start user condition (%d):\n\t%s\n",
                            plugin.getStartConditionManager().getUserLogicalCondition().getTotalConditionCount(),
                            plugin.getStartConditionManager().getUserLogicalCondition().getDescription()));
                    logMessage.append(String.format("Start plugin conditions (%d):\n\t%s",
                            plugin.getStartConditionManager().getPluginCondition().getTotalConditionCount(),
                            plugin.getStartConditionManager().getPluginCondition().getDescription()));

                    log.info(logMessage.toString());

                    // Log condition details at debug level
                    if (Microbot.isDebug()) {
                        plugin.logConditionInfo(plugin.getStopConditionManager().getConditions(),
                                "LOADING - Stop Conditions", true);
                        plugin.logConditionInfo(plugin.getStartConditionManager().getConditions(),
                                "LOADING - Start Conditions", true);
                    }
                }

                // Force UI update after loading plugins
                SwingUtilities.invokeLater(this::updatePanels);
            }
        } catch (Exception e) {
            log.error("Error loading scheduled plugins", e);
            scheduledPlugins = new ArrayList<>();
        }
    }

    /**
     * Resolves plugin references for a ScheduledPlugin instance.
     * This must be done after deserialization since Plugin objects can't be
     * serialized directly.
     */
    private void resolvePluginReferences(PluginScheduleEntry scheduled) {
        if (scheduled.getName() == null) {
            return;
        }

        // Find the plugin by name
        Plugin plugin = Microbot.getPluginManager().getPlugins().stream()
                .filter(p -> p.getName().equals(scheduled.getName()))
                .findFirst()
                .orElse(null);

        if (plugin != null) {
            scheduled.setPlugin(plugin);

            // If plugin implements StoppingConditionProvider, make sure any plugin-defined
            // conditions are properly registered
            if (plugin instanceof SchedulablePlugin) {
                log.debug("Found StoppingConditionProvider plugin: {}", plugin.getName());
                // This will preserve user-defined conditions while adding plugin-defined ones
                //scheduled.registerPluginStoppingConditions();
            }
        } else {
            log.warn("Could not find plugin with name: {}", scheduled.getName());
        }
    }

    public List<String> getAvailablePlugins() {
        return Microbot.getPluginManager().getPlugins().stream()
                .filter(plugin -> {
                    PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
                    return descriptor != null && plugin instanceof SchedulablePlugin;
                            
                })
                .map(Plugin::getName)
                .sorted()
                .collect(Collectors.toList());
    }

    public PluginScheduleEntry getNextScheduledPlugin() {
        return getNextScheduledPlugin(true, null).orElse(null);
    }

    private PluginScheduleEntry getNextScheduledPluginWithinTime(Duration timeWindow) {
        return getNextScheduledPlugin(false, timeWindow).orElse(null);
    }

    /**
     * Core method to find the next plugin based on various criteria.
     * This uses sortPluginScheduleEntries with weighted selection to handle
     * randomizable plugins.
     * 
     * @param isDueToRun If true, only returns plugins that are due to run now
     * @param timeWindow If not null, limits to plugins triggered within this time
     *                   window
     * @return Optional containing the next plugin to run, or empty if none match
     *         criteria
     */
    public Optional<PluginScheduleEntry> getNextScheduledPlugin(boolean isDueToRun, 
                                                                Duration timeWindow) {

        if (scheduledPlugins.isEmpty()) {
            return Optional.empty();
        }

        // Apply filters based on parameters
        List<PluginScheduleEntry> filteredPlugins = scheduledPlugins.stream()
                .filter(PluginScheduleEntry::isEnabled)
                .filter(plugin -> {
                    // Filter by whether it's due to run now if requested
                    if (isDueToRun && !plugin.isDueToRun()) {
                        log.debug("Plugin '{}' is not due to run", plugin.getCleanName());
                        return false;
                    }

                    // Filter by time window if specified
                    if (timeWindow != null) {
                        Optional<ZonedDateTime> nextStartTime = plugin.getCurrentStartTriggerTime();
                        if (!nextStartTime.isPresent()) {
                            log.debug("Plugin '{}' has no trigger time", plugin.getCleanName());
                            return false;
                        }

                        ZonedDateTime cutoffTime = ZonedDateTime.now(ZoneId.systemDefault()).plus(timeWindow);
                        if (nextStartTime.get().isAfter(cutoffTime)) {
                            log.debug("Plugin '{}' trigger time is after cutoff", plugin.getCleanName());
                            return false;
                        }
                    }

                    // Must have a valid next trigger time
                    return plugin.getCurrentStartTriggerTime().isPresent();
                })
                .collect(Collectors.toList());

        if (filteredPlugins.isEmpty()) {
            return Optional.empty();
        }

        // Find the highest priority plugins first (to maintain compatibility with old
        // selection logic)
        int highestPriority = filteredPlugins.stream()
                .mapToInt(PluginScheduleEntry::getPriority)
                .max()
                .orElse(0);

        // Filter to just the highest priority plugins
        List<PluginScheduleEntry> highestPriorityPlugins = filteredPlugins.stream()
                .filter(p -> p.getPriority() == highestPriority)
                .collect(Collectors.toList());

        // Then prefer non-default plugins if any exist
        List<PluginScheduleEntry> candidatePlugins;
        List<PluginScheduleEntry> nonDefaultPlugins = highestPriorityPlugins.stream()
                .filter(p -> !p.isDefault())
                .collect(Collectors.toList());

        candidatePlugins = !nonDefaultPlugins.isEmpty() ? nonDefaultPlugins : highestPriorityPlugins;

        // Sort the candidate plugins with weighted selection
        // This handles both randomizable and non-randomizable plugins
        List<PluginScheduleEntry> sortedCandidates = sortPluginScheduleEntries(candidatePlugins, true);
        // The first plugin after sorting is our selected plugin
        if (!sortedCandidates.isEmpty()) {
            PluginScheduleEntry selectedPlugin = sortedCandidates.get(0);
            return Optional.of(selectedPlugin);
        }

        return Optional.empty();
    }

    /**
     * Helper method to check if all plugins in a list have the same start trigger
     * time
     * (truncated to millisecond precision for stable comparisons)
     * 
     * @param plugins List of plugins to check
     * @return true if all plugins have the same trigger time
     */
    private boolean isAllSameTimestamp(List<PluginScheduleEntry> plugins) {
        if (plugins.size() <= 1) {
            return true;
        }

        ZonedDateTime firstTime = null;
        for (PluginScheduleEntry plugin : plugins) {
            Optional<ZonedDateTime> timeOpt = plugin.getCurrentStartTriggerTime();
            if (!timeOpt.isPresent()) {
                return false; // If any plugin doesn't have a time, they're not all the same
            }

            // Truncate to millisecond precision for stable comparison
            ZonedDateTime time = timeOpt.get().truncatedTo(ChronoUnit.MILLIS);

            if (firstTime == null) {
                firstTime = time;
            } else if (!firstTime.isEqual(time)) {
                return false; // Found a different timestamp
            }
        }

        return true; // All timestamps are equal
    }

    /**
     * Checks if the current plugin should be stopped based on conditions
     */
    private void checkCurrentPlugin() {
        if (currentPlugin == null || !currentPlugin.isRunning()) {
            // should not happen because only called when is isScheduledPluginRunning() is
            // true
            return;
        }

        // Call the update hook if the plugin is a condition provider
        Plugin runningPlugin = currentPlugin.getPlugin();
        if (runningPlugin instanceof SchedulablePlugin) {
            ((SchedulablePlugin) runningPlugin).onStopConditionCheck();
        }

        // Log condition progress if debug mode is enabled
        if (Microbot.isDebug()) {
            // Log current progress of all conditions
            currentPlugin.logConditionInfo(currentPlugin.getStopConditions(), "DEBUG_CHECK Running Plugin", true);

            // If there are progress-tracking conditions, log their progress percentage
            double overallProgress = currentPlugin.getStopConditionProgress();
            if (overallProgress > 0) {
                log.info("Overall condition progress for '{}': {}%",
                        currentPlugin.getCleanName(),
                        String.format("%.1f", overallProgress));
            }
        }

        // Check if conditions are met
        boolean stopStarted = currentPlugin.checkConditionsAndStop(true);
        if (currentPlugin.isRunning() && !stopStarted && currentState != SchedulerState.SOFT_STOPPING_PLUGIN && currentPlugin.isDefault()){
            boolean prioritizeNonDefaultPlugins = config.prioritizeNonDefaultPlugins();
            // Use the configured look-ahead time window
            int nonDefaultPluginLookAheadMinutes = config.nonDefaultPluginLookAheadMinutes(); 
            PluginScheduleEntry nextPluginWithin = getNextScheduledPlugin(true, Duration.ofMinutes(nonDefaultPluginLookAheadMinutes)).orElse(null);
            
            if (nextPluginWithin != null && !nextPluginWithin.isDefault()) {
                //String builder
                StringBuilder sb = new StringBuilder();
                sb.append("Plugin '").append(currentPlugin.getCleanName()).append("' is running and has a next scheduled plugin within ")
                        .append(nonDefaultPluginLookAheadMinutes)
                        .append(" minutes that is not a default plugin: '")
                        .append(nextPluginWithin.getCleanName()).append("'");
                log.info(sb.toString());
                    
            } 
            
            if(prioritizeNonDefaultPlugins && nextPluginWithin != null && !nextPluginWithin.isDefault()){
                stopStarted = currentPlugin.stop(true);
            }
        }
        if (stopStarted) {
            if (config.notificationsOn()){
                String notificationMessage = "SoftStop Plugin '" + currentPlugin.getCleanName() + "' stopped because conditions were met";         
                notifier.notify(Notification.ON, notificationMessage);
            }
            log.info("Plugin '{}' stopped because conditions were met",
                    currentPlugin.getCleanName());
            // Set state to indicate we're stopping the plugin
            setState(SchedulerState.SOFT_STOPPING_PLUGIN);
        }
        if (!currentPlugin.isRunning()) {
            log.info("Plugin '{}' stopped because conditions were met",
                    currentPlugin.getCleanName());
            currentPlugin = null;
            setState(SchedulerState.SCHEDULING);
        }
    }

    /**
     * Gets condition progress for a scheduled plugin.
     * 
     * @param scheduled The scheduled plugin
     * @return Progress percentage (0-100)
     */
    public double getStopConditionProgress(PluginScheduleEntry scheduled) {
        if (scheduled == null || scheduled.getStopConditionManager().getConditions().isEmpty()) {
            return 0;
        }

        return scheduled.getStopConditionProgress();
    }

    /**
     * Gets the list of plugins that have conditions set
     */
    public List<PluginScheduleEntry> getPluginsWithConditions() {
        return scheduledPlugins.stream()
                .filter(p -> !p.getStopConditionManager().getConditions().isEmpty())
                .collect(Collectors.toList());
    }

   

    /**
     * Adds conditions to a scheduled plugin with support for saving to a specific file
     * 
     * @param plugin The plugin to add conditions to
     * @param userStopConditions List of stop conditions
     * @param userStartConditions List of start conditions
     * @param requireAll Whether all conditions must be met
     * @param stopOnConditionsMet Whether to stop the plugin when conditions are met
     * @param saveFile Optional file to save the conditions to, or null to use default config
     */
    public void saveConditionsToPlugin(PluginScheduleEntry plugin, List<Condition> stopConditions,
            List<Condition> startConditions, boolean requireAll, boolean stopOnConditionsMet, File saveFile) {
        if (plugin == null)
            return;

        // Clear existing stop conditions
        plugin.getStopConditionManager().getConditions().clear();

        // Add new stop conditions
        for (Condition condition : stopConditions) {
            plugin.addStopCondition(condition);
        }
        
        // Add start conditions if provided
        if (startConditions != null) {
            plugin.getStartConditionManager().getConditions().clear();
            for (Condition condition : startConditions) {
                plugin.addStartCondition(condition);
            }
        }

        // Set condition manager properties
        if (requireAll) {
            plugin.getStopConditionManager().setRequireAll();
        } else {
            plugin.getStopConditionManager().setRequireAny();
        }

        // Save to specified file if provided, otherwise to config
        if (saveFile != null) {
            saveScheduledPluginsToFile(saveFile);
        } else {
            // Save to config
            saveScheduledPlugins();
        }
    }

    /**
     * Returns the currently running scheduled plugin
     * 
     * @return The currently running plugin or null if none is running
     */
    public PluginScheduleEntry getCurrentPlugin() {
        return currentPlugin;
    }

    /**
     * Checks if a specific plugin schedule entry is currently running
     * This explicitly compares by reference, not just by name
     */
    public boolean isRunningEntry(PluginScheduleEntry entry) {
        return entry.isRunning();
    }

    /**
     * Checks if a completed one-time plugin should be removed
     * 
     * @param scheduled The scheduled plugin to check
     * @return True if the plugin should be removed
     */
    private boolean shouldRemoveCompletedOneTimePlugin(PluginScheduleEntry scheduled) {
        // Check if it's been run at least once and is not currently running
        boolean hasRun = scheduled.getRunCount() > 0 && !scheduled.isRunning();

        // Check if it can't be triggered again (based on start conditions)
        boolean cantTriggerAgain = !scheduled.canStartTriggerAgain();

        return hasRun && cantTriggerAgain;
    }

    /**
     * Cleans up the scheduled plugins list by removing completed one-time plugins
     */
    private void cleanupCompletedOneTimePlugins() {
        List<PluginScheduleEntry> toRemove = scheduledPlugins.stream()
                .filter(this::shouldRemoveCompletedOneTimePlugin)
                .collect(Collectors.toList());

        if (!toRemove.isEmpty()) {
            scheduledPlugins.removeAll(toRemove);
            saveScheduledPlugins();
            log.info("Removed {} completed one-time plugins", toRemove.size());
        }
    }

    public boolean isScheduledPluginRunning() {
        return currentPlugin != null && currentPlugin.isRunning();
    }

    private boolean isAutoLoginEnabled() {
        return Microbot.isPluginEnabled(AutoLoginPlugin.class);
    }

    private boolean isBreakHandlerEnabled() {
        return Microbot.isPluginEnabled(BreakHandlerPlugin.class);
    }

    private boolean isAntibanEnabled() {
        return Microbot.isPluginEnabled(AntibanPlugin.class);
    }

    /**
     * Forces the bot to take a break immediately if BreakHandler is enabled
     * 
     * @return true if break was initiated, false otherwise
     */
    private boolean forceBreak() {
        if (!isBreakHandlerEnabled()) {
            log.warn("Cannot force break: BreakHandler plugin not enabled");
            return false;
        }

        // Set the breakNow config to true to trigger a break
        Microbot.getConfigManager().setConfiguration(BreakHandlerConfig.configGroup, "breakNow", true);
        Microbot.getConfigManager().setConfiguration(BreakHandlerConfig.configGroup, "logout", true);
        log.info("Break requested via forceBreak()");
        return true;
    }

    private boolean takeMicroBreak() {
        if (!isAntibanEnabled()) {
            log.warn("Cannot take micro break: Antiban plugin not enabled");
            return false;
        }
        if (Rs2Player.isFullHealth()) {
            if (Rs2Antiban.takeMicroBreakByChance() || BreakHandlerScript.isBreakActive())
                setState(SchedulerState.SHORT_BREAK);
            return true;
        }
        return false;
    }

    private boolean lockBreakHandler() {
        // Check if BreakHandler is enabled and not already locked
        if (isBreakHandlerEnabled() && !BreakHandlerScript.isBreakActive() && !BreakHandlerScript.isLockState()) {
            BreakHandlerScript.setLockState(true);
            return true; // Successfully locked
        }
        return false; // Failed to lock
    }

    private void unlockBreakHandler() {
        // Check if BreakHandler is enabled and not already unlocked
        if (isBreakHandlerEnabled() && BreakHandlerScript.isLockState()) {
            BreakHandlerScript.setLockState(false);
        }

    }

    /**
     * Checks if the bot is currently on a break
     * 
     * @return true if on break, false otherwise
     */
    public boolean isOnBreak() {
        // Check if BreakHandler is enabled and on a break
        return isBreakHandlerEnabled() && BreakHandlerScript.isBreakActive();
    }

    /**
     * Gets the current activity being performed
     * 
     * @return The current activity, or null if not tracking
     */
    public Activity getCurrentActivity() {
        return currentActivity;
    }

    /**
     * Gets the current activity intensity
     * 
     * @return The current activity intensity, or null if not tracking
     */
    public ActivityIntensity getCurrentIntensity() {
        return currentIntensity;
    }

    /**
     * Gets the current idle time in game ticks
     * 
     * @return Idle time (ticks)
     */
    public int getIdleTime() {
        return idleTime;
    }

    /**
     * Gets the time elapsed since login
     * 
     * @return Duration since login, or Duration.ZERO if not logged in
     */
    public Duration getLoginDuration() {
        if (loginTime == null) {
            return Duration.ZERO;
        }
        return Duration.between(loginTime, Instant.now());
    }

    /**
     * Gets the time since last detected activity
     * 
     * @return Duration since last activity
     */
    public Duration getTimeSinceLastActivity() {
        return Duration.between(lastActivityTime, Instant.now());
    }

    /**
     * Gets the time until the next scheduled break
     * 
     * @return Duration until next break
     */
    public Duration getTimeUntilNextBreak() {
        if (isBreakHandlerEnabled() && BreakHandlerScript.breakIn > 0) {
            return Duration.ofSeconds(BreakHandlerScript.breakIn);
        }
        return timeUntilNextBreak;
    }

    /**
     * Gets the duration of the current break
     * 
     * @return Current break duration
     */
    public Duration getCurrentBreakDuration() {
        if (isBreakHandlerEnabled() && BreakHandlerScript.breakDuration > 0) {
            return Duration.ofSeconds(BreakHandlerScript.breakDuration);
        }
        return currentBreakDuration;
    }

    private Optional<Duration> getScheduleInterval(PluginScheduleEntry plugin) {
        if (plugin.hasAnyStartConditions()) {
            Optional<ZonedDateTime> nextTrigger = plugin.getCurrentStartTriggerTime();
            if (nextTrigger.isPresent()) {
                ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
                return Optional.of(Duration.between(now, nextTrigger.get()));
            }
        }
        return Optional.empty();
    }

    public void startLoginMonitoringThread() {
        String  pluginName = "";
        if (!currentState.isSchedulerActive() || (currentState == SchedulerState.SHORT_BREAK || currentState == SchedulerState.RUNNING_PLUGIN || currentState == SchedulerState.LOGIN ) ||(Microbot.isLoggedIn())) {
            log.info("Login monitoring thread not started, current state: {} - {}", currentState,currentState.isWaiting() );
            return;
        }        
        if (currentPlugin != null) {
            pluginName = currentPlugin.getName();                
        }
        
        if (loginMonitor != null && loginMonitor.isAlive()) {
            log.info("Login monitoring thread already running for plugin '{}'", pluginName);
            return;
        }
        setState(SchedulerState.LOGIN);
        this.loginMonitor = new Thread(() -> {
            try {
                
                log.debug("Login monitoring thread started for plugin");
                int loginAttempts = 0;
                final int MAX_LOGIN_ATTEMPTS = 6;

                // Keep checking until login completes or max attempts reached
                while (loginAttempts < MAX_LOGIN_ATTEMPTS) {
                    // Wait for login attempt to complete
                    
                    log.info("Login attempt {} of {}",
                            loginAttempts, MAX_LOGIN_ATTEMPTS);
                    // Try login again if needed
                    
                    if (loginAttempts < MAX_LOGIN_ATTEMPTS) {                    
                        login();
                    }
                    if (Microbot.isLoggedIn()) {
                        // Successfully logged in, now increment the run count                        
                        if (currentPlugin != null) {
                            log.info("Login successful, finalizing plugin start: {}", currentPlugin.getName());
                            setState(SchedulerState.STARTING_PLUGIN);
                            Microbot.getClientThread().invokeLater(() -> {
                                continueStartingPluginScheduleEntry(currentPlugin);
                                // setState(SchedulerState.RUNNING_PLUGIN);
                            });    
                            return;
                        }else{
                            log.info("Login successful, but no plugin to start back to scheduling");
                            setState(SchedulerState.SCHEDULING);
                        }                       
                        return;
                    }
                    if (Microbot.getClient().getGameState() != GameState.LOGGED_IN &&
                            Microbot.getClient().getGameState() != GameState.LOGGING_IN) {
                        loginAttempts++;
                    }
                    Thread.sleep(2000);
                }

                // If we get here, login failed too many times
                log.error("Failed to login after {} attempts",
                        MAX_LOGIN_ATTEMPTS);
                SwingUtilities.invokeLater(() -> {
                    // Clean up and set proper state
                    if (currentPlugin != null && currentPlugin.isRunning()) {
                        currentPlugin.softStop(false);
                        setState(SchedulerState.SOFT_STOPPING_PLUGIN);
                    } else {
                        if (currentPlugin != null) {
                            currentPlugin.setEnabled(false);
                        }
                        currentPlugin = null;
                        
                        setState(SchedulerState.SCHEDULING);
                    }

                });
            } catch (InterruptedException e) {
                if (currentPlugin != null) log.debug("Login monitoring thread for '{}' was interrupted", currentPlugin.getName());
            }
        });

        loginMonitor.setName("LoginMonitor - " + pluginName);
        loginMonitor.setDaemon(true);
        loginMonitor.start();
    }

    private void logout() {
        if (Microbot.getClient().getGameState() == GameState.LOGGED_IN) {
            if (isAutoLoginEnabled()) {
                boolean successfulDisabled = disableAutoLogin();
                if (!successfulDisabled) {
                    Microbot.getClientThread().invokeLater(() -> {
                        logout();
                    });                    
                    return;
                }
            }

            Rs2Player.logout();
        }
    }

    private void login() {      
        // First check if AutoLogin plugin is available and enabled
        if (!isAutoLoginEnabled() && config.autoLogIn()) {
            // Try to enable AutoLogin plugin
            if (enableAutoLogin()) {                
                // Give it a moment to initialize
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        // Fallback to manual login if AutoLogin is not available
        // log.info("Using manual login (AutoLogin plugin not available)");
        boolean successfulLogin = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            // check which login index means we are in authifcation or not a member
            // TODO add these to "LOGIN" class ->
            // net.runelite.client.plugins.microbot.util.security
            int currentLoginIndex = Microbot.getClient().getLoginIndex();
            boolean tryMemberWorld =  config.worldType() == 2 || config.worldType() ==1 ; // TODO get correct one
            if (currentLoginIndex == 4 || currentLoginIndex == 3) { // we are in the auth screen and cannot login
                // 3 mean wrong authtifaction
                return false; // we are in auth
            }
            if (currentLoginIndex == 34) { // we are not a member and cannot login
                if (isAutoLoginEnabled() || config.autoLogInWorld() == 1) {                    
                    Microbot.getConfigManager().setConfiguration("AutoLoginConfig", "World",
                            Login.getRandomWorld(false));
                }
                int loginScreenWidth = 804;
                int startingWidth = (Microbot.getClient().getCanvasWidth() / 2) - (loginScreenWidth / 2);
                Microbot.getMouse().click(365 + startingWidth, 308); // clicks a button "OK" when you've been
                                                                     // disconnected
                sleep(600);
                if (config.worldType() != 2){
                    // Show dialog for free world selection using the SchedulerUIUtils class
                    SchedulerUIUtils.showNonMemberWorldDialog(currentPlugin, config, (switchToFreeWorlds) -> {
                        if (!switchToFreeWorlds) {
                            // User chose not to switch to free worlds or dialog timed out
                            if (currentPlugin != null) {
                                currentPlugin.setEnabled(false);
                                currentPlugin = null;
                                setState(SchedulerState.SCHEDULING);
                                log.info("Login to member world canceled, stopping current plugin");
                            }
                        }
                    });
                }
                tryMemberWorld = false; // we are not a member

                
            }
            if (currentLoginIndex == 2) {
                // connected to the server
            }

            if (isAutoLoginEnabled()) {                
                ConfigManager configManager = Microbot.getConfigManager();
                if (configManager != null) {
                    configManager.setConfiguration("AutoLoginConfig", "World",
                            Login.getRandomWorld(tryMemberWorld));                    
                }
                // Give it a moment to initialize
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } else {
                log.info("forced login by scheduler plugin -> currentLoginIndex: {} - member {}", currentLoginIndex,
                        tryMemberWorld);
                new Login(Login.getRandomWorld(tryMemberWorld));
            }
            return true;
        }).orElse(false);        
        if (!successfulLogin) {
            currentPlugin.setEnabled(false);
            currentPlugin = null;
            setState(SchedulerState.SCHEDULING);
            log.error("Failed to login, stopping plugin: {}", currentPlugin.getName());
            //stopScheduler();
        }
    }

    /**
     * Prints detailed diagnostic information about all scheduled plugins
     */
    public void debugAllScheduledPlugins() {
        log.info("==== PLUGIN SCHEDULER DIAGNOSTICS ====");
        log.info("Current state: {}", currentState);
        log.info("Number of scheduled plugins: {}", scheduledPlugins.size());

        for (PluginScheduleEntry plugin : scheduledPlugins) {
            log.info("\n----- Plugin: {} -----", plugin.getCleanName());
            log.info("Enabled: {}", plugin.isEnabled());
            log.info("Running: {}", plugin.isRunning());
            log.info("Is default: {}", plugin.isDefault());
            log.info("Due to run: {}", plugin.isDueToRun());
            log.info("Has start conditions: {}", plugin.hasAnyStartConditions());

            if (plugin.hasAnyStartConditions()) {
                log.info("Start conditions met: {}", plugin.getStartConditionManager().areConditionsMet());

                // Get next trigger time if any
                Optional<ZonedDateTime> nextTrigger = plugin.getCurrentStartTriggerTime();
                log.info("Next trigger time: {}",
                        nextTrigger.isPresent() ? nextTrigger.get() : "None found");

                // Print detailed diagnostics
                log.info("\nDetailed start condition diagnosis:");
                log.info(plugin.diagnoseStartConditions());
            }
        }
        log.info("==== END DIAGNOSTICS ====");
    }

    /**
     * Enables the AutoLogin plugin
     * 
     * @return true if plugin was enabled successfully, false otherwise
     */
    private boolean enableAutoLogin() {
        ConfigManager configManager = Microbot.getConfigManager();
        if( configManager != null) {
            if (config.autoLogInWorld() == 0) {            
                configManager.setConfiguration("AutoLoginConfig", "RandomWorld", true);
            
            }
            configManager.setConfiguration("AutoLoginConfig", "World", config.autoLogInWorld());
        }
        
        if (isAutoLoginEnabled()) {
            return true; // Already enabled
        }
        
        Microbot.getClientThread().runOnSeperateThread(() -> {
            Plugin autoLoginPlugin = Microbot.getPlugin(AutoLoginPlugin.class.getName());
            if (autoLoginPlugin == null) {
                log.error("Failed to find AutoLoginPlugin");
                return false;
            }
            log.info("AutoLoginPlugin starting");
            Microbot.startPlugin(autoLoginPlugin);
            return true;
        });

        log.info("AutoLoginPlugin enabled");
        return true;
    }

    /**
     * Disables the AutoLogin plugin
     * 
     * @return true if plugin was disabled successfully, false otherwise
     */
    private boolean disableAutoLogin() {
        if (!isAutoLoginEnabled()) {
            return true; // Already disabled
        }
        Microbot.getClientThread().runOnSeperateThread(() -> {
            Plugin autoLoginPlugin = Microbot.getPlugin(AutoLoginPlugin.class.getName());
            if (autoLoginPlugin == null) {
                log.error("Failed to find AutoLoginPlugin");
                return false;
            }
            Microbot.stopPlugin(autoLoginPlugin);
            return true;
        });
        if (isAutoLoginEnabled()) {
            SwingUtilities.invokeLater(() -> {
                disableAutoLogin();
            });
            return false;
        }
        log.info("AutoLoginPlugin disabled");
        return true;
    }

    /**
     * Enables the BreakHandler plugin
     * 
     * @return true if plugin was enabled successfully, false otherwise
     */
    private boolean enableBreakHandler() {
        if (isBreakHandlerEnabled()) {
            return true; // Already enabled
        }
        Microbot.getClientThread().runOnSeperateThread(() -> {
            Plugin breakHandlerPlugin = Microbot.getPlugin(BreakHandlerPlugin.class.getName());
            log.info("BreakHandlerPlugin suggested to be enabled");
            if (breakHandlerPlugin == null) {
                log.error("Failed to find BreakHandlerPlugin");
                return false;
            }
            log.info("BreakHandlerPlugin starting");
            Microbot.startPlugin(breakHandlerPlugin);
            return true;
        });

        log.info("BreakHandlerPlugin wait");
        sleepUntil(() -> isBreakHandlerEnabled(), 500);
        if (!isBreakHandlerEnabled()) {
            log.error("Failed to enable BreakHandlerPlugin");
            return false;
        }
        log.info("BreakHandlerPlugin enabled");
        return true;
    }

    /**
     * Disables the BreakHandler plugin
     * 
     * @return true if plugin was disabled successfully, false otherwise
     */
    private boolean disableBreakHandler() {
        if (!isBreakHandlerEnabled()) {
            log.info("BreakHandlerPlugin already disabled");
            return true; // Already disabled
        }
        BreakHandlerScript.setLockState(false); // Ensure we unlock before disabling
        log.info("disableBreakHandler - are we on client thread->; {}", Microbot.getClient().isClientThread());

        Microbot.getClientThread().runOnSeperateThread(() -> {
            Plugin breakHandlerPlugin = Microbot.getPlugin(BreakHandlerPlugin.class.getName());
            if (breakHandlerPlugin == null) {
                log.error("Failed to find BreakHandlerPlugin");
                return false;
            }
            log.info("BreakHandlerPlugin stopping");
            Microbot.stopPlugin(breakHandlerPlugin);
            return true;
        });

        if (isBreakHandlerEnabled()) {
            SwingUtilities.invokeLater(() -> {
                disableBreakHandler();
            });

            return false;
        }
        log.info("BreakHandlerPlugin disabled");
        return true;
    }

    /**
     * Enables the Antiban plugin
     * 
     * @return true if plugin was enabled successfully, false otherwise
     */
    private boolean enableAntiban() {
        if (isAntibanEnabled()) {
            return true; // Already enabled
        }
        Microbot.getClientThread().runOnSeperateThread(() -> {
            Plugin antibanPlugin = Microbot.getPlugin(AntibanPlugin.class.getName());
            log.info("AntibanPlugin suggested to be enabled");
            if (antibanPlugin == null) {
                log.error("Failed to find AntibanPlugin");
                return false;
            }
            log.info("AntibanPlugin starting");
            Microbot.startPlugin(antibanPlugin);
            return true;
        });

        log.info("AntibanPlugin wait");
        sleepUntil(() -> isAntibanEnabled(), 500);
        if (!isAntibanEnabled()) {
            log.error("Failed to enable AntibanPlugin");
            return false;
        }
        log.info("AntibanPlugin enabled");
        return true;
    }

    /**
     * Disables the Antiban plugin
     * 
     * @return true if plugin was disabled successfully, false otherwise
     */
    private boolean disableAntiban() {

        if (!isAntibanEnabled()) {
            log.info("AntibanPlugin already disabled");
            return true; // Already disabled
        }

        Microbot.getClientThread().runOnSeperateThread(() -> {
            Plugin antibanPlugin = Microbot.getPlugin(AntibanPlugin.class.getName());
            if (antibanPlugin == null) {
                log.error("Failed to find AntibanPlugin");
                return false;
            }
            log.info("AntibanPlugin stopping");
            Microbot.stopPlugin(antibanPlugin);
            return true;
        });

        if (isAntibanEnabled()) {
            SwingUtilities.invokeLater(() -> {
                disableAntiban();
            });
            log.error("Failed to disable AntibanPlugin");
            return false;
        }
        log.info("AntibanPlugin disabled");
        return true;
    }

    public void openAntibanSettings() {
        // Get the parent frame
        SwingUtilities.invokeLater(() -> {
            try {
                // Use the utility class to open the Antiban settings in a new window
                AntibanDialogWindow.showAntibanSettings(panel);
            } catch (Exception ex) {
                log.error("Error opening Antiban settings: {}", ex.getMessage());
            }
        });
    }

    /**
     * Sets the current scheduler state and updates UI
     */
    private void setState(SchedulerState newState) {
        if (currentState != newState) {
            log.debug("Scheduler state changed: {} -> {}", currentState, newState);
            breakStartTime = Optional.empty();
            // Set additional state information based on context
            switch (newState) {
                case INITIALIZING:
                    newState.setStateInformation(String.format("Checking for required plugins (%d/%d)",
                            initCheckCount, MAX_INIT_CHECKS));
                    break;

                case ERROR:
                    newState.setStateInformation(String.format(
                            "Initialization failed after %d/%d attempts. Client may not be at login screen.",
                            initCheckCount, MAX_INIT_CHECKS));
                    break;

                case WAITING_FOR_LOGIN:
                    newState.setStateInformation("Waiting for player to login to start plugin");
                    break;

                case SOFT_STOPPING_PLUGIN:
                    newState.setStateInformation(
                            currentPlugin != null ? "Attempting to gracefully stop " + currentPlugin.getCleanName()
                                    : "Attempting to gracefully stop plugin");
                    break;

                case HARD_STOPPING_PLUGIN:
                    newState.setStateInformation(
                            currentPlugin != null ? "Forcing " + currentPlugin.getCleanName() + " to stop"
                                    : "Forcing plugin to stop");
                    break;

                case RUNNING_PLUGIN:
                    newState.setStateInformation(
                            currentPlugin != null ? "Running " + currentPlugin.getCleanName() : "Running plugin");
                    break;

                case STARTING_PLUGIN:
                    newState.setStateInformation(
                            currentPlugin != null ? "Starting " + currentPlugin.getCleanName() : "Starting plugin");
                    break;

                case SHORT_BREAK:
                    PluginScheduleEntry nextPlugin = getNextScheduledPlugin();
                    if (nextPlugin != null) {
                        newState.setStateInformation("Taking short break until " +
                                nextPlugin.getCleanName() + " is scheduled to run");
                    } else {
                        newState.setStateInformation("Taking a short break");
                    }
                    breakStartTime = Optional.of(ZonedDateTime.now(ZoneId.systemDefault()));
                    break;

                case WAITING_FOR_SCHEDULE:
                    newState.setStateInformation("Waiting for the next scheduled plugin to become due");
                    break;

                case SCHEDULING:
                    newState.setStateInformation("Actively checking plugin schedules");
                    break;

                case READY:
                    newState.setStateInformation("Ready to run - click Start to begin scheduling");
                    break;

                case HOLD:
                    newState.setStateInformation("Scheduler was manually stopped");
                    break;

                default:
                    newState.setStateInformation(""); // Clear any previous information
                    break;
            }

            currentState = newState;
            SwingUtilities.invokeLater(this::updatePanels);
        }
    }

    /**
     * Checks if the player has been idle for longer than the specified timeout
     * 
     * @param timeout The timeout in game ticks
     * @return True if idle for longer than timeout
     */
    public boolean isIdleTooLong(int timeout) {
        return idleTime > timeout && !isOnBreak();
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        // Update idle time tracking
        if (!Rs2Player.isAnimating() && !Rs2Player.isMoving() && !isOnBreak()
                && this.currentState == SchedulerState.RUNNING_PLUGIN) {
            idleTime++;
        } else {
            idleTime = 0;
        }
    }

    @Subscribe
    public void onPluginScheduleEntryFinishedEvent(PluginScheduleEntryFinishedEvent event) {
        if (currentPlugin != null && event.getPlugin() == currentPlugin.getPlugin()) {
            log.info("Plugin '{}' self-reported as finished: {} (Success: {})",
                    currentPlugin.getCleanName(),
                    event.getReason(),
                    event.isSuccess());
            if (config.notificationsOn()){                
                String notificationMessage = "Plugin '" + currentPlugin.getCleanName() + "' finished: " + event.getReason();
                if (event.isSuccess()) {
                    notificationMessage += " (Success)";
                } else {
                    notificationMessage += " (Failed)";
                }
                notifier.notify(Notification.ON, notificationMessage);
                
            }
            // Update the stop reason information
            currentPlugin.setLastStopReason(event.getReason());
            currentPlugin.setLastRunSuccessful(event.isSuccess());
            currentPlugin.setStopReasonType(PluginScheduleEntry.StopReason.PLUGIN_FINISHED);

            // Stop the plugin with the success state from the event
            if (currentState == SchedulerState.RUNNING_PLUGIN) {
                setState(SchedulerState.SOFT_STOPPING_PLUGIN);
            }
            currentPlugin.setFinished(true);
            currentPlugin.checkConditionsAndStop(event.isSuccess());
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged) {
        log.info("\n"+getName() + " - Game state changed: " + gameStateChanged.getGameState());

        // Track login time
        if (gameStateChanged.getGameState() == GameState.LOGGED_IN
                && (lastGameState == GameState.LOGIN_SCREEN || lastGameState == GameState.HOPPING)) {
            loginTime = Instant.now();
            log.info("Login detected, setting login time: {}", loginTime);

            // Reset idle counter on login
            idleTime = 0;
        }

        if (gameStateChanged.getGameState() == GameState.LOGGED_IN) {
            // If the game state is LOGGED_IN, start the scheduler

        } else if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN) {
            // If the game state is LOGIN_SCREEN, stop the current plugin

            // Clear login time when logging out
            loginTime = null;
        } else if (gameStateChanged.getGameState() == GameState.HOPPING) {
            // If the game state is HOPPING, stop the current plugin

        } else if (gameStateChanged.getGameState() == GameState.CONNECTION_LOST) {
            // If the game state is CONNECTION_LOST, stop the current plugin
            // Clear login time when connection is lost
            loginTime = null;

        } else if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN_AUTHENTICATOR) {
            // If the game state is LOGGING_IN, stop the current plugin

            // Clear login time when logging out
            loginTime = null;
            stopScheduler();

        }

        this.lastGameState = gameStateChanged.getGameState();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (event.getGroup().equals("PluginScheduler")) {
            for (PluginScheduleEntry plugin : scheduledPlugins) {
                plugin.setSoftStopRetryInterval(Duration.ofSeconds(config.softStopRetrySeconds()));
                plugin.setHardStopTimeout(Duration.ofSeconds(config.hardStopTimeoutSeconds()));
            }
        }
    }

    @Subscribe
    public void onStatChanged(StatChanged statChanged) {
        // Reset idle time when gaining experience
        idleTime = 0;
        lastActivityTime = Instant.now();

        final Skill skill = statChanged.getSkill();
        final int exp = statChanged.getXp();
        final Integer previous = skillExp.put(skill, exp);

        if (lastSkillChanged != null && (lastSkillChanged.equals(skill) ||
                (CombatSkills.isCombatSkill(lastSkillChanged) && CombatSkills.isCombatSkill(skill)))) {

            return;
        }

        lastSkillChanged = skill;

        if (previous == null || previous >= exp) {
            return;
        }

        // Update our local tracking of activity
        Activity activity = Activity.fromSkill(skill);
        if (activity != null) {
            currentActivity = activity;
            if (Microbot.isDebug()) {
                log.debug("Activity updated from skill: {} -> {}", skill.getName(), activity);
            }
        }

        ActivityIntensity intensity = ActivityIntensity.fromSkill(skill);
        if (intensity != null) {
            currentIntensity = intensity;
        }
    }

    @Subscribe
    public void onPluginChanged(PluginChanged event) {
        if (currentPlugin != null && event.getPlugin() == currentPlugin.getPlugin()) {
            // The plugin changed state - check if it's no longer running
            boolean isRunningNow = currentPlugin.isRunning();
            boolean wasStartedByScheduler = currentPlugin.isHasStarted();

            // If plugin was running but is now stopped
            if (!isRunningNow) {
                log.info("Plugin '{}' state change detected: running -> stopped", currentPlugin.getCleanName());

                // Check if this was an expected stop based on our current state
                boolean wasExpectedStop = (currentState == SchedulerState.SOFT_STOPPING_PLUGIN ||
                        currentState == SchedulerState.HARD_STOPPING_PLUGIN);

                // If the stop wasn't initiated by us, it was unexpected (error or manual stop)
                if (!wasExpectedStop && currentState == SchedulerState.RUNNING_PLUGIN) {
                    log.warn("Plugin '{}' stopped unexpectedly while in {} state",
                            currentPlugin.getCleanName(), currentState.name());

                    // Set error information
                    currentPlugin.setLastStopReason("Plugin stopped unexpectedly");
                    currentPlugin.setLastRunSuccessful(false);
                    currentPlugin.setStopReasonType(PluginScheduleEntry.StopReason.ERROR);

                    // Disable the plugin to prevent it from running again until issue is fixed
                    currentPlugin.setEnabled(false);

                    // Set state to error
                    setState(SchedulerState.SCHEDULING);
                } else if (currentState == SchedulerState.SOFT_STOPPING_PLUGIN) {                    
                    // If we were soft stopping and it completed, make sure stop reason is set
                    if (currentPlugin.getStopReasonType() == PluginScheduleEntry.StopReason.NONE) {
                        // Set stop reason if it wasn't already set
                        currentPlugin.setStopReasonType(PluginScheduleEntry.StopReason.SCHEDULED_STOP);
                        currentPlugin.setLastStopReason("Scheduled stop completed successfully");
                        currentPlugin.setLastRunSuccessful(true);
                    }
                } else if (currentState == SchedulerState.HARD_STOPPING_PLUGIN) {                    
                    // Hard stop completed
                    if (currentPlugin.getStopReasonType() == PluginScheduleEntry.StopReason.NONE) {
                        currentPlugin.setStopReasonType(PluginScheduleEntry.StopReason.HARD_STOP_TIMEOUT);
                        currentPlugin.setLastStopReason("Plugin was forcibly stopped after timeout");
                        currentPlugin.setLastRunSuccessful(false);
                    }
                }

                // Return to scheduling state regardless of stop reason
                if (currentState != SchedulerState.HOLD) {
                    log.info("Plugin '{}' stopped - returning to scheduling state with reason: {}",
                            currentPlugin.getCleanName(),
                            currentPlugin.getLastStopReason());
                    setState(SchedulerState.SCHEDULING);
                }
                log.info("current state after plugin stop: {}", currentState);

                // Clear current plugin reference
                currentPlugin = null;
            } else if (isRunningNow && wasStartedByScheduler && currentState != SchedulerState.STARTING_PLUGIN) {
                // Plugin was started outside our control or restarted - this is unexpected but
                // we'll monitor it
                log.info("Plugin '{}' started or restarted outside scheduler control", event.getPlugin().getName());
            }

            SwingUtilities.invokeLater(this::updatePanels);
        }
    }

    /**
     * Gets the time remaining until the next scheduled plugin is due to run
     * 
     * @return Duration until next plugin or null if no plugins scheduled
     */
    public Duration getTimeBeforeNextScheduledPlugin() {
        PluginScheduleEntry nextPlugin = getNextScheduledPlugin();
        if (nextPlugin == null) {
            return null;
        }

        // Get the next trigger time for this plugin
        Optional<ZonedDateTime> nextTriggerTime = nextPlugin.getCurrentStartTriggerTime();
        if (!nextTriggerTime.isPresent()) {
            return null;
        }

        // Calculate time until trigger
        return Duration.between(ZonedDateTime.now(ZoneId.systemDefault()), nextTriggerTime.get());
    }

    /**
     * Sorts a list of plugins according to a consistent order, with weighted
     * selection for randomizable plugins:
     * 1. Enabled plugins first
     * 2. Then by priority (highest first)
     * 3. Then by non-default status (non-default first)
     * 4. For non-randomizable plugins: by trigger time (earliest first)
     * 5. For randomizable plugins with equal priority: weighted by run count (less
     * run = higher chance)
     * 6. Finally by name and object identity for stable ordering
     * 
     * @param plugins                The list of plugins to sort
     * @param applyWeightedSelection Whether to apply weighted selection for
     *                               randomizable plugins
     * @return A sorted copy of the input list
     */
    public List<PluginScheduleEntry> sortPluginScheduleEntries(List<PluginScheduleEntry> plugins,
            boolean applyWeightedSelection) {
        if (plugins == null || plugins.isEmpty()) {
            return new ArrayList<>();
        }

        List<PluginScheduleEntry> sortedPlugins = new ArrayList<>(plugins);

        // First, sort by all the stable criteria
        sortedPlugins.sort((p1, p2) -> {
            // First sort by enabled status (enabled plugins first)
            if (p1.isEnabled() != p2.isEnabled()) {
                return p1.isEnabled() ? -1 : 1;
            }

            // For running plugins, prioritize current running plugin at the top
            boolean p1IsRunning = p1.isRunning();
            boolean p2IsRunning = p2.isRunning();

            if (p1IsRunning != p2IsRunning) {
                return p1IsRunning ? -1 : 1;
            }

            // Then sort by priority (highest first)
            int priorityCompare = Integer.compare(p2.getPriority(), p1.getPriority());
            if (priorityCompare != 0) {
                return priorityCompare;
            }

            // Prefer non-default plugins
            if (p1.isDefault() != p2.isDefault()) {
                return p1.isDefault() ? 1 : -1;
            }

            // Prefer non-randomizable plugins
            if (p1.isAllowRandomScheduling() != p2.isAllowRandomScheduling()) {
                return p1.isAllowRandomScheduling() ? 1 : -1;
            }

            // For non-randomizable plugins, sort by trigger time (earliest first)
            if (!p1.isAllowRandomScheduling() && !p2.isAllowRandomScheduling()) {
                Optional<ZonedDateTime> time1 = p1.getCurrentStartTriggerTime();
                Optional<ZonedDateTime> time2 = p2.getCurrentStartTriggerTime();

                if (time1.isPresent() && time2.isPresent()) {
                    // Truncate to milliseconds for stable comparison
                    ZonedDateTime t1 = time1.get().truncatedTo(ChronoUnit.MILLIS);
                    ZonedDateTime t2 = time2.get().truncatedTo(ChronoUnit.MILLIS);
                    int timeCompare = t1.compareTo(t2);
                    if (timeCompare != 0) {
                        return timeCompare;
                    }
                } else if (time1.isPresent()) {
                    return -1; // p1 has time, p2 doesn't
                } else if (time2.isPresent()) {
                    return 1; // p2 has time, p1 doesn't
                }
            }

            // As final tiebreakers use plugin name and object identity
            int nameCompare = p1.getName().compareTo(p2.getName());
            if (nameCompare != 0) {
                return nameCompare;
            }

            // Last resort: use object identity hash code for stable ordering
            return Integer.compare(System.identityHashCode(p1), System.identityHashCode(p2));
        });

        // If we're not applying weighted selection, we're done
        if (!applyWeightedSelection) {
            return sortedPlugins;
        }

        // Now we need to look for groups of randomizable plugins at the same priority
        // and apply weighted selection
        List<PluginScheduleEntry> result = new ArrayList<>();
        List<PluginScheduleEntry> randomizableGroup = new ArrayList<>();
        Integer currentPriority = null;
        boolean currentDefault = false;

        // Iterate through sorted plugins to find groups with the same priority and
        // default status
        for (int i = 0; i < sortedPlugins.size(); i++) {
            PluginScheduleEntry current = sortedPlugins.get(i);

            // Skip non-randomizable plugins (they're already properly sorted)
            if (!current.isAllowRandomScheduling()) {
                // If we had a randomizable group, process it before adding this
                // non-randomizable plugin
                if (!randomizableGroup.isEmpty()) {
                    result.addAll(applyWeightedSorting(randomizableGroup));
                    randomizableGroup.clear();
                }

                result.add(current);
                continue;
            }

            // Check if this is part of an existing group
            if (currentPriority != null
                    && current.getPriority() == currentPriority
                    && current.isDefault() == currentDefault) {
                // Same group, add to current batch of randomizable plugins
                randomizableGroup.add(current);
            } else {
                // New group - process previous group if it exists
                if (!randomizableGroup.isEmpty()) {
                    result.addAll(applyWeightedSorting(randomizableGroup));
                    randomizableGroup.clear();
                }

                // Start new group
                randomizableGroup.add(current);
                currentPriority = current.getPriority();
                currentDefault = current.isDefault();
            }
        }

        // Process any remaining group
        if (!randomizableGroup.isEmpty()) {
            result.addAll(applyWeightedSorting(randomizableGroup));
        }

        return result;
    }

    /**
     * Sort a group of randomizable plugins using a weighted approach based on run
     * counts.
     * Plugins with fewer runs get sorted ahead of plugins with more runs, following
     * the weighting system used in the old selectPluginWeighted method.
     * 
     * @param plugins A list of randomizable plugins with the same priority and
     *                default status
     * @return A list sorted by weighted run count
     */
    private List<PluginScheduleEntry> applyWeightedSorting(List<PluginScheduleEntry> plugins) {
        if (plugins.size() <= 1) {
            return new ArrayList<>(plugins);
        }

        // Similar to the old selectPluginWeighted, but we're sorting instead of
        // selecting one

        // First, find the maximum run count
        int maxRuns = plugins.stream()
                .mapToInt(PluginScheduleEntry::getRunCount)
                .max()
                .orElse(0);

        // Add 1 to avoid division by zero and ensure all have a chance
        maxRuns = maxRuns + 1;

        // Calculate weights for each plugin
        final Map<PluginScheduleEntry, Double> weights = new HashMap<>();
        double totalWeight = 0;

        for (PluginScheduleEntry plugin : plugins) {
            // Weight = (maxRuns + 1) - plugin's run count
            double weight = maxRuns - plugin.getRunCount() + 1;
            weights.put(plugin, weight);
            totalWeight += weight;
        }

        // Create weighted comparison
        Comparator<PluginScheduleEntry> weightedComparator = (p1, p2) -> {
            // Higher weight (fewer runs) should come first
            double weight1 = weights.getOrDefault(p1, 0.0);
            double weight2 = weights.getOrDefault(p2, 0.0);

            if (Double.compare(weight1, weight2) != 0) {
                // Higher weight first
                return Double.compare(weight2, weight1);
            }

            // If weights are equal, use name and identity for stable sorting
            int nameCompare = p1.getName().compareTo(p2.getName());
            if (nameCompare != 0) {
                return nameCompare;
            }

            return Integer.compare(System.identityHashCode(p1), System.identityHashCode(p2));
        };

        // Sort plugins based on weight
        List<PluginScheduleEntry> sortedPlugins = new ArrayList<>(plugins);
        sortedPlugins.sort(weightedComparator);

        if (log.isDebugEnabled()) {
            for (int i = 0; i < sortedPlugins.size(); i++) {
                PluginScheduleEntry plugin = sortedPlugins.get(i);
                double weight = weights.get(plugin);
                double weightPercentage = (weight / totalWeight) * 100.0;
                log.debug("Weighted sorting position {}: '{}' with weight {:.2f}/{:.2f} ({:.2f}%) (run count: {})",
                        i, plugin.getCleanName(), weight, totalWeight, weightPercentage, plugin.getRunCount());
            }
        }

        return sortedPlugins;
    }

    /**
     * Sorts all scheduled plugins according to a consistent order.
     * See {@link #sortPluginScheduleEntries(List, boolean)} for the sorting
     * criteria.
     * 
     * @param applyWeightedSelection Whether to apply weighted selection for
     *                               randomizable plugins
     * @return A sorted list of all scheduled plugins
     */
    public List<PluginScheduleEntry> sortPluginScheduleEntries(boolean applyWeightedSelection) {
        return sortPluginScheduleEntries(scheduledPlugins, applyWeightedSelection);
    }

    /**
     * Overloaded method that calls sortPluginScheduleEntries without weighted
     * selection by default
     */
    public List<PluginScheduleEntry> sortPluginScheduleEntries(List<PluginScheduleEntry> plugins) {
        return sortPluginScheduleEntries(plugins, false);
    }

    /**
     * Overloaded method that calls sortPluginScheduleEntries without weighted
     * selection by default
     */
    public List<PluginScheduleEntry> sortPluginScheduleEntries() {
        return sortPluginScheduleEntries(scheduledPlugins, false);
    }

    /**
     * Extends an active break when it's about to end and there are no upcoming plugins
     * @param thresholdSeconds Time in seconds before break end when we consider extending
     * @return true if break was extended, false otherwise
     */
    private boolean extendBreakIfNeeded(PluginScheduleEntry nextPlugin, int thresholdSeconds) {
        // Check if we're on a break and have break information
        if (!isOnBreak() || !breakStartTime.isPresent() || currentBreakDuration.equals(Duration.ZERO) 
        || currentState != SchedulerState.SHORT_BREAK) {
            return false;
        }

        // Calculate when the current break will end
        ZonedDateTime breakEndTime = breakStartTime.get().plus(currentBreakDuration);
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        
        // Calculate how much time is left in the current break
        Duration timeRemaining = Duration.between(now, breakEndTime);
        
        // If the break is about to end within the threshold seconds
        if (timeRemaining.getSeconds() <= thresholdSeconds) {                                    
            if (nextPlugin == null) {
                // No upcoming plugin, extend the break
                log.info("Break is about to end in {} seconds with no upcoming plugins. Extending break.", 
                    timeRemaining.getSeconds());
                
                // Calculate new break duration (existing duration + config duration)
                int extensionMinutes = config.maxBreakDuratation();
                long newBreakSeconds = Rs2Random.normalRange(60, extensionMinutes * 60, 0.4);
                newBreakSeconds  = Math.min( 60 , newBreakSeconds);
                
                // Configure extended break
                BreakHandlerScript.breakDuration = (int) newBreakSeconds;
                currentBreakDuration = Duration.ofSeconds(newBreakSeconds);
                // We don't need to reset breakIn since we're already in a break
                
                // Update break start time to now so the duration calculation works correctly
                breakStartTime = Optional.of(now);
                
                log.info("Break extended by {} minutes. New end time: {}", 
                    extensionMinutes, now.plus(Duration.ofSeconds(newBreakSeconds)));
                
                return true;
            }
        }
        
        return false;
    }
}
