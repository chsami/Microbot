package net.runelite.client.plugins.microbot.pluginscheduler;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.api.StoppingConditionProvider;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.type.ScheduledPlugin;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@PluginDescriptor(
        name = PluginDescriptor.Mocrosoft + "Plugin Scheduler",
        description = "Schedule plugins at your will",
        tags = {"microbot", "schedule", "automation"},
        enabledByDefault = false
)
public class SchedulerPlugin extends Plugin {
    @Inject
    private SchedulerConfig config;

    @Provides
    SchedulerConfig provideConfig(ConfigManager configManager) {
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
    private ScheduledPlugin currentPlugin;

    private List<ScheduledPlugin> scheduledPlugins = new ArrayList<>();

    public boolean isRunning() {
        return currentPlugin != null && currentPlugin.isRunning();
    }
    

    @Getter
    
    private boolean inLoginScreen = false;
    
    
    private int initCheckCount = 0;
    private static final int MAX_INIT_CHECKS = 10;

    @Getter
    private SchedulerState currentState = SchedulerState.UNINITIALIZED;
    private boolean manualStop = false;

    @Override
    protected void startUp() {
        
        panel = new SchedulerPanel(this, config);

        final BufferedImage icon = ImageUtil.loadImageResource(SchedulerPlugin.class, "icon.png");
        navButton = NavigationButton.builder()
                .tooltip("Plugin Scheduler")
                .priority(10)
                .icon(icon)
                .panel(panel)
                .build();

        clientToolbar.addNavigation(navButton);

        // Load saved schedules from config
        

        // Check initialization status before fully enabling scheduler
        checkInitialization();

        // Run the main loop
        updateTask = executorService.scheduleAtFixedRate(() -> {
            SwingUtilities.invokeLater(() -> {
                // Only run scheduling logic if fully initialized
                if (    isSchedulerActive()) {
                    checkSchedule();
                } else if (currentState == SchedulerState.INITIALIZING || currentState == SchedulerState.UNINITIALIZED ) {
                    // Retry initialization check if not already checking
                    checkInitialization();
                }
                updatePanels();
            });
        }, 0, 1, TimeUnit.SECONDS);              
    }
   
    /**
     * Sets the current scheduler state and updates UI
     */
    private void setState(SchedulerState newState) {
        if (currentState != newState) {
            log.debug("Scheduler state changed: {} -> {}", currentState, newState);
            currentState = newState;
            updatePanels();
        }
    }

    /**
     * Checks if all required plugins are loaded and initialized.
     * This runs until initialization is complete or max check count is reached.
     */
    private void checkInitialization() {
        if (currentState != SchedulerState.INITIALIZING &&
            currentState != SchedulerState.UNINITIALIZED) {
            return;
        }
        
        setState(SchedulerState.INITIALIZING);
        
        // Schedule repeated checks until initialized or max checks reached
        
        SwingUtilities.invokeLater(() -> {
            // Check if client is at login screen
            boolean isAtLoginScreen = inLoginScreen;
            List<Plugin> conditionProviders = new ArrayList<>();
            if  (Microbot.getPluginManager() == null|| Microbot.getClient() == null) {
                
                
            }else{
                // Find all plugins implementing StoppingConditionProvider
                conditionProviders = Microbot.getPluginManager().getPlugins().stream()
                    .filter(plugin -> plugin instanceof StoppingConditionProvider
                            )
                    .collect(Collectors.toList());
                List<Plugin> enabledList = conditionProviders.stream()
                    .filter(plugin -> Microbot.getPluginManager().isPluginEnabled(plugin))
                    .collect(Collectors.toList());
            }
            
            isAtLoginScreen = isAtLoginScreen || Microbot.getClient().getGameState() == GameState.LOGIN_SCREEN;
            // If all conditions met, mark as initialized
            if (isAtLoginScreen) {
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
                log.warn("Scheduler initialization timed out - proceeding anyway");
                loadScheduledPlugin();
            
                setState(SchedulerState.READY);
            } 
            // Otherwise, schedule another check
            else {
                log.info("Waiting for initialization: loginScreen={}, providers={}/{}, checks={}/{}",
                    isAtLoginScreen, 
                    conditionProviders.stream().count(),
                    conditionProviders.size(),
                    initCheckCount,
                    MAX_INIT_CHECKS
                    );
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
        clientToolbar.removeNavigation(navButton);
        forceStopCurrentPlugin();
        
        if (updateTask != null) {
            updateTask.cancel(false);
            updateTask = null;
        }

        if (schedulerWindow != null) {
            schedulerWindow.dispose(); // This will stop the timer
            schedulerWindow = null;
        }
    }

   /**
     * Starts the scheduler
    */
    public void startScheduler() {
        // If already active, nothing to do
        if (isSchedulerActive()) {
            log.info("Scheduler already active");
            return;
        }
                        
        // If initialized, start immediately
        if (SchedulerState.READY == currentState || currentState == SchedulerState.HOLD) {
            
            setState(SchedulerState.SCHEDULING);
            log.info("Plugin Scheduler started");
            
            // Check schedule immediately when started
            SwingUtilities.invokeLater(() -> {
                checkSchedule();
            });
            return;
        }                            
    }
    public boolean isSchedulerActive(){

        return  currentState != SchedulerState.UNINITIALIZED &&
                currentState != SchedulerState.INITIALIZING &&
                currentState != SchedulerState.ERROR &&
                currentState != SchedulerState.HOLD &&
                currentState != SchedulerState.READY;
    }
    /**
     * Stops the scheduler
     */
    public void stopScheduler() {
        if (!isSchedulerActive()) {
            return; // Already stopped
        }
        
        log.info("Stopping scheduler...");                        
        // Force stop any running plugin
        forceStopCurrentPlugin();        
        // Final state after fully stopped
        setState(SchedulerState.HOLD);
    }

    private void checkSchedule() {
     
        
        // Clean up completed one-time plugins
        cleanupCompletedOneTimePlugins();
                            
        // Check if we need to stop the current plugin
        if (currentPlugin != null && currentPlugin.isRunning()) {
            checkCurrentPlugin();
        }
        
        // If no plugin is running, check for scheduled plugins due to run
        if (!isRunning()) {
            scheduleNextPlugin();
        }
    }

    /**
     * Checks if the current plugin should be stopped based on conditions
     */
    private void checkCurrentPlugin() {
        if (currentPlugin == null || !currentPlugin.isRunning()) {
            return;
        }
        
        // Call the update hook if the plugin is a condition provider
        Plugin runningPlugin = currentPlugin.getPlugin();
        if (runningPlugin instanceof StoppingConditionProvider) {
            ((StoppingConditionProvider) runningPlugin).onConditionCheck();
        }
        
        // Log condition progress if debug mode is enabled
        if (config.debugMode()) {
            // Log current progress of all conditions
            currentPlugin.logConditionInfo("DEBUG_CHECK Running Plugin", true);
            
            // If there are progress-tracking conditions, log their progress percentage
            double overallProgress = currentPlugin.getConditionProgress();
            if (overallProgress > 0) {
                log.info("Overall condition progress for '{}': {}%", 
                    currentPlugin.getCleanName(), 
                    String.format("%.1f", overallProgress));
            }
        }
        
        // Check if conditions are met
        if (currentPlugin.checkConditionsAndStop()) {                       
            if (!currentPlugin.isRunning()){
                log.info("Plugin '{}' stopped because conditions were met", 
                currentPlugin.getCleanName());
                currentPlugin = null;
                setState(SchedulerState.SCHEDULING);
            }
        }
    }

    /**
     * Schedules the next plugin to run if none is running
     */
    private void scheduleNextPlugin() {
        // First, prioritize non-randomizable plugins
        List<ScheduledPlugin> dueNonRandomPlugins = scheduledPlugins.stream()
                .filter(plugin -> plugin.isDueToRun() && plugin.isEnabled() && !plugin.isAllowRandomScheduling())
                .collect(Collectors.toList());
        
        if (!dueNonRandomPlugins.isEmpty()) {
            // Start the first non-random plugin (priority order)
            startPlugin(dueNonRandomPlugins.get(0));
            saveScheduledPlugins();
            return;
        }
        
        // If no non-random plugins are due, get all randomizable plugins that are due
        List<ScheduledPlugin> dueRandomPlugins = scheduledPlugins.stream()
                .filter(plugin -> plugin.isDueToRun() && plugin.isEnabled() && plugin.isAllowRandomScheduling())
                .collect(Collectors.toList());
        
        if (!dueRandomPlugins.isEmpty()) {
            // Use weighted random selection based on run count
            ScheduledPlugin selected = selectPluginWeighted(dueRandomPlugins);
            startPlugin(selected);            
            saveScheduledPlugins();
        }
    }
    /**
     * Selects a plugin using weighted random selection.
     * Plugins with lower run counts have higher probability of being selected.
     */
    private ScheduledPlugin selectPluginWeighted(List<ScheduledPlugin> plugins) {
        // Return the only plugin if there's just one
        if (plugins.size() == 1) {
            return plugins.get(0);
        }
        
        // Calculate weights - plugins with lower run counts get higher weights
        // Find the maximum run count
        int maxRuns = plugins.stream()
                .mapToInt(ScheduledPlugin::getRunCount)
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

    public void startPlugin(ScheduledPlugin scheduledPlugin) {
        if (scheduledPlugin == null) return;
        log.info("Starting scheduled plugin: " + scheduledPlugin.getCleanName());
     
        
        setState(SchedulerState.STARTING_PLUGIN);
        currentPlugin = scheduledPlugin;

        if (!scheduledPlugin.start()) {
            log.error("Failed to start plugin: " + scheduledPlugin.getCleanName());
            currentPlugin = null;
            //setState(SchedulerState.ERROR);
            return;
        }
        if (!Microbot.isLoggedIn()) {
            //setState(SchedulerState.WAITING_FOR_LOGIN);
            Microbot.getClientThread().runOnClientThread(Login::new);
            SwingUtilities.invokeLater(() -> {
                // TODO Retry starting the plugin after login ->  should we retry login multiple times? with retry count?
                return;
            });
        }

       
        scheduledPlugin.incrementRunCount();
        setState(SchedulerState.RUNNING_PLUGIN);
    }

    public void forceStopCurrentPlugin() {
        if (currentPlugin != null) {
            log.info("Force Stopping current plugin: " + currentPlugin.getCleanName());
            setState(SchedulerState.STOPPING_PLUGIN);
            
            if (currentPlugin.hardStop()) {
                currentPlugin = null;
                // Set to READY if we're not manually stopped
                setState(manualStop ? SchedulerState.HOLD : SchedulerState.READY);
            } else {
                log.error("Failed to stop plugin: " + currentPlugin.getCleanName());
                setState(SchedulerState.ERROR);
            }
        }
        updatePanels();
    }

    @Subscribe
    public void onPluginChanged(PluginChanged event) {
        if (currentPlugin != null && event.getPlugin() == currentPlugin.getPlugin() && !currentPlugin.isRunning()) {
            currentPlugin = null;
            if (    currentState == SchedulerState.RUNNING_PLUGIN 
                || currentState == SchedulerState.STOPPING_PLUGIN 
                || currentState == SchedulerState.WAITING_FOR_LOGIN
                || currentState == SchedulerState.STARTING_PLUGIN) {                
                setState(SchedulerState.SCHEDULING);
            }
            updatePanels();
        }
    }

    /**
     * Update all UI panels with the current state
     */
    private void updatePanels() {
        if (panel != null) {
            panel.refresh();
        }

        if (schedulerWindow != null && schedulerWindow.isVisible()) {
            schedulerWindow.refresh();
        }
    }

    public void addScheduledPlugin(ScheduledPlugin plugin) {
        plugin.setLastRunTime(ZonedDateTime.now(ZoneId.systemDefault()));
        scheduledPlugins.add(plugin);
        saveScheduledPlugins();
    }

    public void removeScheduledPlugin(ScheduledPlugin plugin) {
        scheduledPlugins.remove(plugin);
        saveScheduledPlugins();
    }

    public void updateScheduledPlugin(ScheduledPlugin oldPlugin, ScheduledPlugin newPlugin) {
        int index = scheduledPlugins.indexOf(oldPlugin);
        if (index >= 0) {
            scheduledPlugins.set(index, newPlugin);
            saveScheduledPlugins();
        }
    }
     /**
     * Adds conditions to a scheduled plugin
     */
    private void saveConditionsToScheduledPlugin(ScheduledPlugin plugin, List<Condition> conditions, 
                                      boolean requireAll, boolean stopOnConditionsMet) {
        if (plugin == null) return;
        
        // Clear existing conditions
        plugin.getStopConditionManager().getConditions().clear();
        
        // Add new conditions
        for (Condition condition : conditions) {
            plugin.addCondition(condition);
        }
        
        // Set condition manager properties
        if (requireAll) {
            plugin.getStopConditionManager().setRequireAll();
        } else {
            plugin.getStopConditionManager().setRequireAny();
        }
        
        
        
        // Save to config
        saveScheduledPlugins();
    }
   /**
     * Gets the list of plugins that have stop conditions set
     */
    public List<ScheduledPlugin> getScheduledPluginsWithStopConditions() {
        return scheduledPlugins.stream()
                .filter(p -> !p.getStopConditionManager().getConditions().isEmpty())
                .collect(Collectors.toList());
    }
    public List<ScheduledPlugin> getScheduledPlugins() {
        return new ArrayList<>(scheduledPlugins);
    }

    public void saveScheduledPlugins() {
        // Convert to JSON and save to config
        String json = ScheduledPlugin.toJson(scheduledPlugins);

        log.info("Saving scheduled plugins to config: {}", json);
        //config.setScheduledPlugins(json);
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
            String json = Microbot.getConfigManager().getConfiguration(SchedulerConfig.CONFIG_GROUP, "scheduledPlugins");
            log.info("Loading scheduled plugins from config: {}", json);
            
            if (json != null && !json.isEmpty()) {
                scheduledPlugins = ScheduledPlugin.fromJson(json);
                
                // Apply stop settings from config to all loaded plugins
                for (ScheduledPlugin plugin : scheduledPlugins) {
                    // Set timeout values from config
                    plugin.setSoftStopRetryInterval(Duration.ofSeconds(config.softStopRetrySeconds()));
                    plugin.setHardStopTimeout(Duration.ofSeconds(config.hardStopTimeoutSeconds()));
                    
                    // Resolve plugin references
                    resolvePluginReferences(plugin);
                    
                    log.info("Loaded scheduled plugin: {} with {} conditions: \nuserCondition ({}): \n\t{}\npluginConditions({}): \n\t{} ", 
                            plugin.getName(), 
                            plugin.getStopConditionManager().getConditions().size(),
                            plugin.getStopConditionManager().getUserLogicalCondition().getTotalConditionCount(),
                            plugin.getStopConditionManager().getUserLogicalCondition().getDescription(),
                            plugin.getStopConditionManager().getPluginCondition().getTotalConditionCount(),
                            plugin.getStopConditionManager().getPluginCondition().getDescription()
                            );
                    
                    // Log condition details at debug level
                    if (config.debugMode()) {
                        plugin.logConditionInfo("LOADING", true);
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
     * This must be done after deserialization since Plugin objects can't be serialized directly.
     */
    private void resolvePluginReferences(ScheduledPlugin scheduled) {
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
            if (plugin instanceof StoppingConditionProvider) {
                log.debug("Found StoppingConditionProvider plugin: {}", plugin.getName());
                // This will preserve user-defined conditions while adding plugin-defined ones
                scheduled.registerPluginStoppingConditions();
            }
        } else {
            log.warn("Could not find plugin with name: {}", scheduled.getName());
        }
    }

    public List<String> getAvailablePlugins() {
        return Microbot.getPluginManager().getPlugins().stream()
                .filter(plugin -> {
                    PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
                    return descriptor != null && descriptor.canBeScheduled();
                })
                .map(Plugin::getName)
                .sorted()
                .collect(Collectors.toList());
    }

    public ScheduledPlugin getNextScheduledPlugin() {
        if (scheduledPlugins.isEmpty()) {
            return null;
        }

        ScheduledPlugin nextPlugin = null;
        ZonedDateTime earliestNextRun = null;

        for (ScheduledPlugin plugin : scheduledPlugins) {
            if (!plugin.isEnabled()) {
                continue;
            }

            ZonedDateTime nextRunTime = plugin.getNextRunTime();
            if (nextRunTime != null && (earliestNextRun == null || nextRunTime.isBefore(earliestNextRun))) {
                earliestNextRun = nextRunTime;
                nextPlugin = plugin;
            }
        }

        return nextPlugin;
    }
    /**
     * Checks if a plugin is currently scheduled and running.
     * 
     * @param plugin The plugin to check
     * @return true if the plugin is scheduled and running
     */
    public boolean isPluginScheduled(Plugin plugin) {
        return scheduledPlugins.stream()
                .anyMatch(scheduled -> scheduled.getPlugin() == plugin && scheduled.isEnabled());
    }

    /**
     * Gets the scheduled instance for a plugin if it exists.
     * 
     * @param plugin The plugin to look up
     * @return The ScheduledPlugin instance or null if not found
     */
    public ScheduledPlugin getScheduleForPlugin(Plugin plugin) {
        return scheduledPlugins.stream()
                .filter(scheduled -> scheduled.getPlugin() == plugin)
                .findFirst()
                .orElse(null);
    }

    /**
     * Gets the condition status for a scheduled plugin.
     * 
     * @param plugin The plugin to check
     * @return A map of condition descriptions to their status, or empty if plugin not scheduled
     */
    public Map<String, Boolean> getConditionStatus(Plugin plugin) {
        ScheduledPlugin scheduled = getScheduleForPlugin(plugin);
        if (scheduled == null) {
            return Collections.emptyMap();
        }
        
        Map<String, Boolean> status = new HashMap<>();
        for (Condition condition : scheduled.getStopConditionManager().getConditions()) {
            status.put(condition.getDescription(), condition.isSatisfied());
        }
        
        return status;
    }

      
    /**
     * Gets condition progress for a scheduled plugin.
     * @param scheduled The scheduled plugin
     * @return Progress percentage (0-100)
     */
    public double getConditionProgress(ScheduledPlugin scheduled) {
        if (scheduled == null || scheduled.getStopConditionManager().getConditions().isEmpty()) {
            return 0;
        }
        
        return scheduled.getConditionProgress();
    }
    
    /**
     * Gets the list of plugins that have conditions set
     */
    public List<ScheduledPlugin> getPluginsWithConditions() {
        return scheduledPlugins.stream()
                .filter(p -> !p.getStopConditionManager().getConditions().isEmpty())
                .collect(Collectors.toList());
    }
    
    /**
     * Adds conditions to a scheduled plugin
     */
    public void saveConditionsToPlugin(ScheduledPlugin plugin, List<Condition> conditions, 
                                      boolean requireAll, boolean stopOnConditionsMet) {
        if (plugin == null) return;
        
        // Clear existing conditions
        plugin.getStopConditionManager().getConditions().clear();
        
        // Add new conditions
        for (Condition condition : conditions) {
            plugin.addCondition(condition);
        }
        
        // Set condition manager properties
        if (requireAll) {
            plugin.getStopConditionManager().setRequireAll();
        } else {
            plugin.getStopConditionManager().setRequireAny();
        }
        
        
        
        // Save to config
        saveScheduledPlugins();
    }

    /**
     * Returns the currently running scheduled plugin
     * @return The currently running plugin or null if none is running
     */
    public ScheduledPlugin getCurrentPlugin() {
        return currentPlugin;
    }

    /**
     * Checks if a completed one-time plugin should be removed
     * @param scheduled The scheduled plugin to check
     * @return True if the plugin should be removed
     */
    private boolean shouldRemoveCompletedOneTimePlugin(ScheduledPlugin scheduled) {
        return scheduled.getScheduleIntervalValue() == 0 && 
               scheduled.getRunCount() > 0 &&
               !scheduled.isRunning();
    }

    /**
     * Cleans up the scheduled plugins list by removing completed one-time plugins
     */
    private void cleanupCompletedOneTimePlugins() {
        List<ScheduledPlugin> toRemove = scheduledPlugins.stream()
                .filter(this::shouldRemoveCompletedOneTimePlugin)
                .collect(Collectors.toList());
        
        if (!toRemove.isEmpty()) {
            scheduledPlugins.removeAll(toRemove);
            saveScheduledPlugins();
            log.info("Removed {} completed one-time plugins", toRemove.size());
        }
    }
    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged) {
        log.info(getName() + " - Game state changed: " + gameStateChanged.getGameState());
        if (gameStateChanged.getGameState() == GameState.LOGGED_IN) {
            // If the game state is LOGGED_IN, start the scheduler            
        } else if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN) {
            // If the game state is LOGIN_SCREEN, stop the current plugin
            System.out.println("LOGIN_SCREEN");
            forceStopCurrentPlugin();
        } else if (gameStateChanged.getGameState() == GameState.HOPPING) {
            // If the game state is HOPPING, stop the current plugin
            forceStopCurrentPlugin();
        } else if (gameStateChanged.getGameState() == GameState.CONNECTION_LOST) {
            // If the game state is CONNECTION_LOST, stop the current plugin
            forceStopCurrentPlugin();
        }
        if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN) {
            // If the game state is LOGIN_SCREEN, stop the current plugin
            inLoginScreen = true;

        }
        
        if (gameStateChanged.getGameState() == GameState.HOPPING  ){

        }
        if (gameStateChanged.getGameState() == GameState.CONNECTION_LOST) {
            
        }
    }
    @Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (event.getGroup().equals("PluginScheduler"))
		{
			for (ScheduledPlugin plugin : scheduledPlugins) {
                plugin.setSoftStopRetryInterval(Duration.ofSeconds(config.softStopRetrySeconds()));
                plugin.setHardStopTimeout(Duration.ofSeconds(config.hardStopTimeoutSeconds()));
            }
		}
	}
    
}
