package net.runelite.client.plugins.microbot.pluginscheduler;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.api.StoppingConditionProvider;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.event.ScheduledStopEvent;
import net.runelite.client.plugins.microbot.pluginscheduler.type.Scheduled;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.image.BufferedImage;
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
    private Scheduled currentPlugin;

    private List<Scheduled> scheduledPlugins = new ArrayList<>();

    public boolean isRunning() {
        return currentPlugin != null && currentPlugin.isRunning();
    }
    private ScheduledFuture<?> pluginStopTask;

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
        loadScheduledPlugin();

        // Run the main loop
        updateTask = executorService.scheduleAtFixedRate(() -> {
            SwingUtilities.invokeLater(() -> {
                checkSchedule();
                updatePanels();
            });
        }, 0, 1, TimeUnit.SECONDS);              
    }
    /**
     * Registers any custom stopping conditions provided by the plugin.
     * These conditions are combined with existing conditions using AND logic
     * to ensure plugin-defined conditions have the highest priority.
     * 
     * @param plugin The plugin that might provide conditions
     * @param scheduled The scheduled instance managing the plugin
     */
    private void registerPluginStoppingConditions(Plugin plugin, Scheduled scheduled) {
        if (plugin instanceof StoppingConditionProvider) {
            StoppingConditionProvider provider = (StoppingConditionProvider) plugin;
            
            // Get conditions from the provider
            List<Condition> pluginConditions = provider.getStoppingConditions();
            if (pluginConditions != null && !pluginConditions.isEmpty()) {
                // Create a new AND condition as the root
                AndCondition rootAndCondition = new AndCondition();
                
                // Add existing conditions from the scheduler (if any)
                List<Condition> existingConditions = scheduled.getConditionManager().getConditions();
                if (!existingConditions.isEmpty()) {
                    // Preserve existing logical structure if present
                    LogicalCondition existingLogic = scheduled.getConditionManager().getRootCondition();
                    if (existingLogic != null) {
                        rootAndCondition.addCondition(existingLogic);
                    } else {
                        // If no logical structure, add conditions individually
                        for (Condition condition : existingConditions) {
                            rootAndCondition.addCondition(condition);
                        }
                    }
                }
                
                // Get or create plugin's logical structure
                LogicalCondition pluginLogic = provider.getLogicalConditionStructure();
                
                if (pluginLogic != null) {
                    // Add plugin's custom logical structure
                    rootAndCondition.addCondition(pluginLogic);
                } else {
                    // Create a simple AND for plugin conditions
                    AndCondition pluginAndCondition = new AndCondition();
                    for (Condition condition : pluginConditions) {
                        pluginAndCondition.addCondition(condition);
                    }
                    rootAndCondition.addCondition(pluginAndCondition);
                }
                
                // Set the new root condition
                scheduled.setLogicalCondition(rootAndCondition);
                
                // Ensure we'll stop when conditions are met
                scheduled.setStopOnConditionsMet(true);
            }
        }
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
        stopCurrentPlugin();

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
        // Check if current plugin should stop based on conditions
        if (currentPlugin != null && currentPlugin.isRunning()) {
            // Call the update hook if the plugin is a condition provider
            Plugin runningPlugin = currentPlugin.getPlugin();
            if (runningPlugin instanceof StoppingConditionProvider) {
                ((StoppingConditionProvider) runningPlugin).onConditionCheck();
            }
            
            // Check if conditions are met
            if (currentPlugin.checkConditionsAndStop()) {
                log.info("Plugin '{}' stopped because conditions were met", 
                        currentPlugin.getCleanName());
                currentPlugin = null;
            }
        }
        // Check for scheduled plugins due to run
        for (Scheduled plugin : scheduledPlugins) {
            if (plugin.isDueToRun() && !isRunning()) {
                // Run the plugin
                startPlugin(plugin);
                saveScheduledPlugins();
                break;
            }
        }
    }

    private void schedulePluginStop(Scheduled plugin) {
        // Cancel any existing stop task
        if (pluginStopTask != null && !pluginStopTask.isDone()) {
            pluginStopTask.cancel(false);
            pluginStopTask = null;
        }

        long durationMinutes = plugin.getDurationMinutes();
        if (durationMinutes > 0) {
            
            
            pluginStopTask = executorService.schedule(
                    this::stopCurrentPlugin,
                    durationMinutes,
                    TimeUnit.MINUTES
            );
        }
    }

    public void startPlugin(Scheduled plugin) {
        if (plugin == null) return;
        log.info("Starting scheduled plugin: " + plugin.getCleanName());
        currentPlugin = plugin;

        if (!plugin.start()) {
            log.error("Failed to start plugin: " + plugin.getCleanName());
            currentPlugin = null;
            return;
        }
        // Register any stopping conditions the plugin provides
        registerPluginStoppingConditions(plugin.getPlugin(), plugin);

        if (!Microbot.isLoggedIn()) {
            Microbot.getClientThread().runOnClientThread(Login::new);
        }
        
        updatePanels();
    }

    public void stopCurrentPlugin() {
        if (currentPlugin != null) {
            log.info("Stopping current plugin: " + currentPlugin.getCleanName());
            if (currentPlugin.stop()) {
                currentPlugin = null;
            } else {
                log.error("Failed to stop plugin: " + currentPlugin.getCleanName());
            }
        }
        updatePanels();
    }

    @Subscribe
    public void onPluginChanged(PluginChanged event) {
        if (currentPlugin != null && event.getPlugin() == currentPlugin.getPlugin() && !currentPlugin.isRunning()) {
            currentPlugin = null;
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

    public void addScheduledPlugin(Scheduled plugin) {
        plugin.setLastRunTime(ZonedDateTime.now(ZoneId.systemDefault()));
        scheduledPlugins.add(plugin);
        saveScheduledPlugins();
    }

    public void removeScheduledPlugin(Scheduled plugin) {
        scheduledPlugins.remove(plugin);
        saveScheduledPlugins();
    }

    public void updateScheduledPlugin(Scheduled oldPlugin, Scheduled newPlugin) {
        int index = scheduledPlugins.indexOf(oldPlugin);
        if (index >= 0) {
            scheduledPlugins.set(index, newPlugin);
            saveScheduledPlugins();
        }
    }

    public List<Scheduled> getScheduledPlugins() {
        return new ArrayList<>(scheduledPlugins);
    }

    public void saveScheduledPlugins() {
        // Convert to JSON and save to config
        String json = Scheduled.toJson(scheduledPlugins);
        config.setScheduledPlugins(json);
    }

    private void loadScheduledPlugin() {
        // Load from config and parse JSON
        String json = config.scheduledPlugins();
        if (json != null && !json.isEmpty()) {
            scheduledPlugins = Scheduled.fromJson(json);
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

    public Scheduled getNextScheduledPlugin() {
        if (scheduledPlugins.isEmpty()) {
            return null;
        }

        Scheduled nextPlugin = null;
        ZonedDateTime earliestNextRun = null;

        for (Scheduled plugin : scheduledPlugins) {
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
     * @return The Scheduled instance or null if not found
     */
    public Scheduled getScheduleForPlugin(Plugin plugin) {
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
        Scheduled scheduled = getScheduleForPlugin(plugin);
        if (scheduled == null) {
            return Collections.emptyMap();
        }
        
        Map<String, Boolean> status = new HashMap<>();
        for (Condition condition : scheduled.getConditionManager().getConditions()) {
            status.put(condition.getDescription(), condition.isMet());
        }
        
        return status;
    }
}
