package net.runelite.client.plugins.microbot.pluginscheduler.model;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Supplier;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigDescriptor;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.api.SchedulablePlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ConditionManager;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.OrCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.enums.UpdateOption;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.IntervalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.SingleTriggerTimeCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.TimeCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.TimeWindowCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.config.ScheduleEntryConfigManager;
import net.runelite.client.plugins.microbot.pluginscheduler.event.PluginScheduleEntrySoftStopEvent;
import net.runelite.client.plugins.microbot.pluginscheduler.serialization.ScheduledSerializer;

@Data
@AllArgsConstructor
@Getter
@Slf4j
public class PluginScheduleEntry implements AutoCloseable {

    // Remove the duplicate executor and use the shared one from ConditionManager
    
    // Store the scheduled futures so they can be cancelled later
    private transient ScheduledFuture<?> startConditionWatchdogFuture;
    private transient ScheduledFuture<?> stopConditionWatchdogFuture;
    private transient Plugin plugin;
    private String name;    
    private boolean enabled;
    private boolean hasStarted = false; // Flag to indicate if the plugin has started
    @Setter
    private boolean needsStopCondition = false; // Flag to indicate if a time-based stop condition is needed    
    private transient ScheduleEntryConfigManager scheduleEntryConfigManager; 

    // New fields for tracking stop reason
    private String lastStopReason;
    private boolean lastRunSuccessful;
    private StopReason stopReasonType = StopReason.NONE;
    private Duration lastRunDuration = Duration.ZERO; // Duration of the last run
    private ZonedDateTime lastRunStartTime; // When the plugin started running
    private ZonedDateTime lastRunEndTime; // When the plugin finished running
    /**
    * Enumeration of reasons why a plugin might stop
    */
    public enum StopReason {
        NONE,
        CONDITIONS_MET,
        MANUAL_STOP,
        PLUGIN_FINISHED,
        ERROR,
        SCHEDULED_STOP,
        HARD_STOP_TIMEOUT
    }
 
    private ZonedDateTime lastRunTime; // When the plugin last ran    

    private String cleanName;
    final private ConditionManager stopConditionManager;
    final private ConditionManager startConditionManager;
    private boolean stopInitiated = false;
    private boolean finished  = false; // Flag to indicate if the plugin has finished its task

    private boolean allowRandomScheduling = true; // Whether this plugin can be randomly scheduled
    private int runCount = 0; // Track how many times this plugin has been run
    
    // Watchdog configuration
    private boolean autoStartWatchdogs = true;  // Whether to auto-start watchdogs on creation
    private boolean watchdogsEnabled = true;    // Whether watchdogs are allowed to run

    private ZonedDateTime stopInitiatedTime; // When the first stop was attempted
    private ZonedDateTime lastStopAttemptTime; // When the last stop attempt was made
    private Duration softStopRetryInterval = Duration.ofSeconds(30); // Default 30 seconds between retries
    private Duration hardStopTimeout = Duration.ofMinutes(4); // Default 2 Minutes before hard stop

    
    private transient Thread stopMonitorThread;
    private transient volatile boolean isMonitoringStop = false;

    // Static formatter for time display
    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");    
    private int priority = 0; // Higher numbers = higher priority
    private boolean isDefault = false; // Flag to indicate if this is a default plugin        
    /**
     * Sets the serialized ConfigDescriptor for this schedule entry
     * This is used during deserialization
     * 
     * @param serializedConfigDescriptor The serialized ConfigDescriptor as a JsonObject
     */
    public void setSerializedConfigDescriptor(ConfigDescriptor serializedConfigDescriptor) {        
        // If we already have a scheduleEntryConfigManager, update it with the new config
        if (this.scheduleEntryConfigManager != null) {
            this.scheduleEntryConfigManager.setConfigScheduleEntryDescriptor(serializedConfigDescriptor);
        }
    }
    
    /**
     * Gets the serialized ConfigDescriptor for this schedule entry
     * 
     * @return The serialized ConfigDescriptor as a JsonObject, or null if not set
     */
    public ConfigDescriptor getConfigScheduleEntryDescriptor() {
        // If we have a scheduleEntryConfigManager, get the serialized config from it
        if (this.scheduleEntryConfigManager != null) {
            return this.scheduleEntryConfigManager.getConfigScheduleEntryDescriptor();
        }        
        return null;
    }
    public PluginScheduleEntry(String pluginName, String duration, boolean enabled, boolean allowRandomScheduling) {
        this(pluginName, parseDuration(duration), enabled, allowRandomScheduling);
    }
    private TimeCondition mainTimeStartCondition;
    private static Duration parseDuration(String duration) {
        // If duration is specified, parse it
        if (duration != null && !duration.isEmpty()) {
            try {
                String[] parts = duration.split(":");
                if (parts.length == 2) {
                    int hours = Integer.parseInt(parts[0]);
                    int minutes = Integer.parseInt(parts[1]);
                    return Duration.ofHours(hours).plusMinutes(minutes);                    
                }
            } catch (Exception e) {
                // Invalid duration format, no condition added
                throw new IllegalArgumentException("Invalid duration format: " + duration);
            }
        }
        return null;
    }
   
    public PluginScheduleEntry(String pluginName, Duration interval, boolean enabled, boolean allowRandomScheduling) { //allowRandomScheduling .>allows soft start
        this(pluginName, new IntervalCondition(interval), enabled, allowRandomScheduling);                
    }
    
    public PluginScheduleEntry(String pluginName, TimeCondition startingCondition, boolean enabled, boolean allowRandomScheduling) {
        this(pluginName, startingCondition, enabled, allowRandomScheduling, true);
    }

    public PluginScheduleEntry( String pluginName, 
                                TimeCondition startingCondition, 
                                boolean enabled, 
                                boolean allowRandomScheduling, 
                                boolean autoStartWatchdogs) {
        this.name = pluginName;        
        this.enabled = enabled;
        this.allowRandomScheduling = allowRandomScheduling;
        this.autoStartWatchdogs = autoStartWatchdogs;
        this.cleanName = pluginName.replaceAll("<html>|</html>", "")
                .replaceAll("<[^>]*>([^<]*)</[^>]*>", "$1")
                .replaceAll("<[^>]*>", "");

        this.stopConditionManager = new ConditionManager();
        this.startConditionManager = new ConditionManager();
        
        // Check if this is a default/1-second interval plugin
        boolean isDefaultByScheduleType = false;
        if (startingCondition != null) {
            if (startingCondition instanceof IntervalCondition) {
                IntervalCondition interval = (IntervalCondition) startingCondition;
                if (interval.getInterval().getSeconds() <= 1) {
                    isDefaultByScheduleType = true;
                }
            }
            this.mainTimeStartCondition = startingCondition;
            startConditionManager.setUserLogicalCondition(new OrCondition(startingCondition));
        }
        
        // If it's a default by schedule type, enforce the default settings
        if (isDefaultByScheduleType) {
            this.isDefault = true;
            this.priority = 0;
        }
        //registerPluginConditions();
        scheduleConditionWatchdogs(10000, UpdateOption.SYNC);                
        // Only start watchdogs if auto-start is enabled
        if (autoStartWatchdogs) {
            //stopConditionManager.resumeWatchdogs();
            //startConditionManager.resumeWatchdogs();
        }
        
        // Always register events if enabled
        if (enabled) {
            startConditionManager.registerEvents();
        }else {
            startConditionManager.unregisterEventsAndPauseWatchdogs();
            stopConditionManager.unregisterEventsAndPauseWatchdogs();
        }                        
    }

    /**
     * Creates a scheduled event with a one-time trigger at a specific time
     * 
     * @param pluginName The plugin name
     * @param triggerTime The time when the plugin should trigger once
     * @param enabled Whether the schedule is enabled
     * @return A new PluginScheduleEntry configured to trigger once at the specified time
     */
    public static PluginScheduleEntry createOneTimeSchedule(String pluginName, ZonedDateTime triggerTime, boolean enabled) {
        SingleTriggerTimeCondition condition = new SingleTriggerTimeCondition(triggerTime);
        PluginScheduleEntry entry = new PluginScheduleEntry(
            pluginName, 
            condition, 
            enabled, 
            false); // One-time events are typically not randomized
        
        return entry;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return; // No change in enabled state
        }
        this.enabled = enabled;
        if (!enabled) {
            stopConditionManager.unregisterEventsAndPauseWatchdogs();
            startConditionManager.unregisterEventsAndPauseWatchdogs();
            runCount = 0;
        } else {
            stopConditionManager.registerEvents();
            startConditionManager.registerEvents();
            //log  this object id-> memory hashcode
            log.info("PluginScheduleEntry {} - {} - {} - {} - {}", this.hashCode(), this.name, this.cleanName, this.enabled, this.allowRandomScheduling);
            //registerPluginConditions();                        
            this.finished = false; // Reset finished state when re-enabled
            this.setLastStopReason("");
            this.setLastRunSuccessful(false);
            this.setStopReasonType(PluginScheduleEntry.StopReason.NONE);
            
            // Resume watchdogs if they were previously configured and watchdogs are enabled
            if (watchdogsEnabled) {
                startConditionManager.resumeWatchdogs();
                stopConditionManager.resumeWatchdogs();
            }
        }
    }

    /**
     * Controls whether watchdogs are allowed to run for this schedule entry.
     * This provides a way to temporarily disable watchdogs without losing their configuration.
     * 
     * @param enabled true to enable watchdogs, false to disable them
     */
    public void setWatchdogsEnabled(boolean enabled) {
        if (this.watchdogsEnabled == enabled) {
            return; // No change
        }
        
        this.watchdogsEnabled = enabled;
        
        if (enabled) {
            // Resume watchdogs if the plugin is enabled
            if (this.enabled) {
                startConditionManager.resumeWatchdogs();
                stopConditionManager.resumeWatchdogs();
                log.debug("Watchdogs resumed for '{}'", name);
            }
        } else {
            // Pause watchdogs regardless of plugin state
            startConditionManager.pauseWatchdogs();
            stopConditionManager.pauseWatchdogs();
            log.debug("Watchdogs paused for '{}'", name);
        }
    }
    
    /**
     * Checks if watchdogs are currently running for this schedule entry
     * 
     * @return true if at least one watchdog is running
     */
    public boolean areWatchdogsRunning() {
        return startConditionManager.areWatchdogsRunning() || 
               stopConditionManager.areWatchdogsRunning();
    }

    /**
     * Manually start the condition watchdogs for this schedule entry.
     * This will only have an effect if watchdogs are enabled and the plugin is enabled.
     * 
     * @param intervalMillis The interval at which to check for condition changes
     * @param updateOption How to handle condition changes
     * @return true if watchdogs were successfully started
     */
    public boolean startConditionWatchdogs(long intervalMillis, UpdateOption updateOption) {
        if (!watchdogsEnabled || !enabled) {
            return false;
        }
        
        return scheduleConditionWatchdogs(intervalMillis, updateOption);
    }

    /**
     * Stops all watchdogs associated with this schedule entry
     */
    public void stopWatchdogs() {
        log.debug("Stopping all watchdogs for '{}'", name);
        startConditionManager.pauseWatchdogs();
        stopConditionManager.pauseWatchdogs();
    }
    
    public Plugin getPlugin() {
        if (this.plugin == null) {
            this.plugin = Microbot.getPluginManager().getPlugins().stream()
                    .filter(p -> Objects.equals(p.getName(), name))
                    .findFirst()
                    .orElse(null);
            
            // Initialize scheduleEntryConfigManager when plugin is first retrieved
            if (this.plugin instanceof SchedulablePlugin && scheduleEntryConfigManager == null) {
                SchedulablePlugin schedulablePlugin = (SchedulablePlugin) this.plugin;
                log.info("Plugin '{}' is a SchedulablePlugin", name);
                ConfigDescriptor descriptor = schedulablePlugin.getConfigDescriptor();
                if (descriptor != null) {
                    scheduleEntryConfigManager = new ScheduleEntryConfigManager(descriptor);
                }
            }
        }
        return plugin;
    }

    public boolean start(boolean logConditions) {
        if (getPlugin() == null) {
            return false;
        }

        try {
            if (!this.isEnabled())
            {
                log.info("Plugin '{}' is disabled, not starting", name);
                return false;
            }
            // Log defined conditions when starting
            if (logConditions) {
                log.info("Starting plugin '{}' with conditions:", name);
                logStartConditionsWithDetails();
                logStopConditionsWithDetails();
            }
            
            
            // Reset stop conditions before starting
            resetStopConditions();
            this.setLastStopReason("");
            this.setLastRunSuccessful(false);
            this.setStopReasonType(PluginScheduleEntry.StopReason.NONE);
            this.finished = false; // Reset finished state when starting
            
            // Set scheduleMode to true in plugin config
            if (scheduleEntryConfigManager != null) {
                scheduleEntryConfigManager.setScheduleMode(true);
                log.debug("Set scheduleMode=true for plugin '{}'", name);
            }
            
            Microbot.getClientThread().runOnSeperateThread(() -> {
                Plugin plugin = getPlugin();
                if (plugin == null) {
                    log.error("Plugin '{}' not found -> can't start plugin", name);
                    return false;
                }                
                Microbot.startPlugin(plugin);
                return false;
            });
            stopInitiated = false;
            hasStarted = true;
            lastRunDuration = Duration.ZERO; // Reset last run duration
            lastRunStartTime = ZonedDateTime.now(); // Set the start time of the last run
            // Register/unregister appropriate event handlers
            stopConditionManager.registerEvents();
            startConditionManager.unregisterEvents();            
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void softStop(boolean successfulRun) {
        if (getPlugin() == null) {
            return;
        }

        try {
            // Reset start conditions
            startConditionManager.reset();
            startConditionManager.registerEvents();
            stopConditionManager.unregisterEvents();
            
            Microbot.getClientThread().runOnSeperateThread(() -> {
                ZonedDateTime current_time = ZonedDateTime.now(ZoneId.systemDefault());
                Microbot.getEventBus().post(new PluginScheduleEntrySoftStopEvent(plugin, current_time));
                return false;                
            });
            
            stopInitiated = true;
            stopInitiatedTime = ZonedDateTime.now();
            lastStopAttemptTime = ZonedDateTime.now();
            lastRunDuration = Duration.between(lastRunStartTime, ZonedDateTime.now());
            lastRunEndTime = ZonedDateTime.now();
            // Start monitoring for successful stop
            startStopMonitoringThread(successfulRun);            

            if (getPlugin() instanceof SchedulablePlugin) {
                log.info("Unregistering stopping conditions for plugin '{}'", name);
            }
            return;
        } catch (Exception e) {
            return;
        }
    }

    public void hardStop(boolean successfulRun) {
        if (getPlugin() == null) {
            return;
        }

        try {
            
            
            Microbot.getClientThread().runOnSeperateThread(() -> {
                log.info("Hard stopping plugin '{}'", name);
                Plugin stopPlugin = Microbot.getPlugin(plugin.getClass().getName());
                Microbot.stopPlugin(stopPlugin);
                return false;
            });
            stopInitiated = true;
            stopInitiatedTime = ZonedDateTime.now();
            lastStopAttemptTime = ZonedDateTime.now();
            // Start monitoring for successful stop
            startStopMonitoringThread(successfulRun);
            
            return;
        } catch (Exception e) {
            return;
        }
    }

     /**
     * Starts a monitoring thread that tracks the stopping process of a plugin.
     * <p>
     * This method creates a daemon thread that periodically checks if a plugin
     * that is in the process of stopping has completed its shutdown. When the plugin
     * successfully stops, this method updates the next scheduled run time and clears
     * all stopping-related state flags.
     * <p>
     * The monitoring thread will only be started if one is not already running
     * (controlled by the isMonitoringStop flag). It checks the plugin's running state
     * every 500ms until the plugin stops or monitoring is canceled.
     * <p>
     * The thread is created as a daemon thread to prevent it from blocking JVM shutdown.
     */
    private void startStopMonitoringThread(boolean successfulRun) {
        // Don't start a new thread if one is already running
        if (isMonitoringStop) {
            return;
        }
        
        isMonitoringStop = true;
        
        stopMonitorThread = new Thread(() -> {
            try {
                log.info("Stop monitoring thread started for plugin '{}'", name);
                
                // Keep checking until the stop completes or is abandoned
                while (stopInitiated && isMonitoringStop) {
                    // Check if plugin has stopped running
                    if (!isRunning()) {
                        
                        log.info("\nPlugin '{}' has successfully stopped - updating state - successfulRun {}", name, successfulRun);
                        
                        // Set scheduleMode back to false when the plugin stops
                        if (scheduleEntryConfigManager != null) {
                            scheduleEntryConfigManager.setScheduleMode(false);
                            log.debug("Set scheduleMode=false for plugin '{}'", name);
                        }
                        
                        // Update lastRunTime and start conditions for next run
                        if (successfulRun) {
                            resetStartConditions();
                            // Increment the run count since we completed a full run
                            incrementRunCount();
                        }else{
                            setEnabled(false);// disable the plugin if it was not successful?
                        }
                        
                        
                        
                        
                        finished = false; // Reset finished state
                        // Reset stop state
                        stopInitiated = false;
                        hasStarted = false;
                        stopInitiatedTime = null;
                        lastStopAttemptTime = null;
                        break;
                    }
                    
                    // Check every 500ms to be responsive but not wasteful
                    Thread.sleep(300);
                }
            } catch (InterruptedException e) {
                // Thread was interrupted, just exit
                log.debug("Stop monitoring thread for '{}' was interrupted", name);
            } finally {
                isMonitoringStop = false;
                log.debug("Stop monitoring thread exited for plugin '{}'", name);
            }
        });
        
        stopMonitorThread.setName("StopMonitor-" + name);
        stopMonitorThread.setDaemon(true); // Use daemon thread to not prevent JVM exit
        stopMonitorThread.start();
    }

    /**
     * Stops the monitoring thread if it's running
     */
    private void stopMonitoringThread() {
        if (isMonitoringStop && stopMonitorThread != null) {
            log.info("Stopping monitoring thread for plugin '{}'", name);
            isMonitoringStop = false;
            stopMonitorThread.interrupt();
            stopMonitorThread = null;
        }
    }

    /**
     * Checks if this plugin schedule has any defined stop conditions
     * 
     * @return true if at least one stop condition is defined
     */
    public boolean hasAnyStopConditions() {
        return stopConditionManager != null && 
               !stopConditionManager.getConditions().isEmpty();
    }
    
    /**
     * Checks if this plugin has any one-time stop conditions that can only trigger once
     * 
     * @return true if at least one single-trigger condition exists in the stop conditions
     */
    public boolean hasAnyOneTimeStopConditions() {
        return stopConditionManager != null && 
               stopConditionManager.hasAnyOneTimeConditions();
    }
    
    /**
     * Checks if any stop conditions have already triggered and cannot trigger again
     * 
     * @return true if at least one stop condition has triggered and cannot trigger again
     */
    public boolean hasTriggeredOneTimeStopConditions() {
        return stopConditionManager != null && 
               stopConditionManager.hasTriggeredOneTimeConditions();
    }
    
    /**
     * Determines if the stop conditions can trigger again in the future
     * Considers the nested logical structure and one-time conditions
     * 
     * @return true if the stop condition structure can trigger again
     */
    public boolean canStopTriggerAgain() {
        return stopConditionManager != null && 
               stopConditionManager.canTriggerAgain();
    }
    
    /**
     * Gets the next time when any stop condition is expected to trigger
     * 
     * @return Optional containing the next stop trigger time, or empty if none exists
     */
    public Optional<ZonedDateTime> getNextStopTriggerTime() {
        if (stopConditionManager == null) {
            return Optional.empty();
        }
        return stopConditionManager.getCurrentTriggerTime();
    }
    
    /**
     * Gets a human-readable string representing when the next stop condition will trigger
     * 
     * @return String with the time until the next stop trigger, or a message if none exists
     */
    public String getNextStopTriggerTimeString() {
        if (stopConditionManager == null) {
            return "No stop conditions defined";
        }
        return stopConditionManager.getCurrentTriggerTimeString();
    }
    
    /**
     * Checks if the stop conditions are fulfillable based on their structure and state
     * A condition is considered unfulfillable if it contains one-time conditions that
     * have all already triggered in an OR structure, or if any have triggered in an AND structure
     * 
     * @return true if the stop conditions can still be fulfilled
     */
    public boolean hasFullfillableStopConditions() {
        if (!hasAnyStopConditions()) {
            return false;
        }
        
        // If we have any one-time conditions that can't trigger again
        // and the structure is such that it can't satisfy anymore, then it's not fulfillable
        if (hasAnyOneTimeStopConditions() && !canStopTriggerAgain()) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Gets the remaining duration until the next stop condition trigger
     * 
     * @return Optional containing the duration until next stop trigger, or empty if none available
     */
    public Optional<Duration> getDurationUntilStopTrigger() {
        if (stopConditionManager == null) {
            return Optional.empty();
        }
        return stopConditionManager.getDurationUntilNextTrigger();
    }
    
       
    

    public boolean isRunning() {
        return getPlugin() != null && Microbot.getPluginManager().isPluginEnabled(plugin) && hasStarted;
    }

    /**
     * Round time to nearest minute (remove seconds and milliseconds)
     */
    private ZonedDateTime roundToMinutes(ZonedDateTime time) {
        return time.withSecond(0).withNano(0);
    }
    private void logStartCondtions() {
        List<Condition> conditionList = startConditionManager.getConditions();
        logConditionInfo(conditionList,"Defined Start Conditions", true);
    }
    private void logStartConditionsWithDetails() {
        List<Condition> conditionList = startConditionManager.getConditions();
        logConditionInfo(conditionList,"Defined Start Conditions", true);
    }

    /**
     * Checks if this plugin schedule has any defined start conditions
     * 
     * @return true if at least one start condition is defined
     */
    public boolean hasAnyStartConditions() {
        return startConditionManager != null && 
               !startConditionManager.getConditions().isEmpty();
    }
    
    /**
     * Checks if this plugin has any one-time start conditions that can only trigger once
     * 
     * @return true if at least one single-trigger condition exists in the start conditions
     */
    public boolean hasAnyOneTimeStartConditions() {
        return startConditionManager != null && 
               startConditionManager.hasAnyOneTimeConditions();
    }
    
    /**
     * Checks if any start conditions have already triggered and cannot trigger again
     * 
     * @return true if at least one start condition has triggered and cannot trigger again
     */
    public boolean hasTriggeredOneTimeStartConditions() {
        return startConditionManager != null && 
               startConditionManager.hasTriggeredOneTimeConditions();
    }
    
    /**
     * Determines if the start conditions can trigger again in the future
     * Considers the nested logical structure and one-time conditions
     * 
     * @return true if the start condition structure can trigger again
     */
    public boolean canStartTriggerAgain() {
        return startConditionManager != null && 
               startConditionManager.canTriggerAgain();
    }
    
    /**
     * Gets the next time when any start condition is expected to trigger
     * 
     * @return Optional containing the next start trigger time, or empty if none exists
     */
    public Optional<ZonedDateTime> getCurrentStartTriggerTime() {
        if (startConditionManager == null) {
            return Optional.empty();
        }
        return startConditionManager.getCurrentTriggerTime();
    }
    
    /**
     * Gets a human-readable string representing when the next start condition will trigger
     * 
     * @return String with the time until the next start trigger, or a message if none exists
     */
    public String getCurrentStartTriggerTimeString() {
        if (startConditionManager == null) {
            return "No start conditions defined";
        }
        return startConditionManager.getCurrentTriggerTimeString();
    }
    
    /**
     * Checks if the start conditions are fulfillable based on their structure and state
     * A condition is considered unfulfillable if it contains one-time conditions that
     * have all already triggered in an OR structure, or if any have triggered in an AND structure
     * 
     * @return true if the start conditions can still be fulfilled
     */
    public boolean hasFullfillableStartConditions() {
        if (!hasAnyStartConditions()) {
            return false;
        }
        
        // If we have any one-time conditions that can't trigger again
        // and the structure is such that it can't satisfy anymore, then it's not fulfillable
        if (hasAnyOneTimeStartConditions() && !canStartTriggerAgain()) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Gets the remaining duration until the next start condition trigger
     * 
     * @return Optional containing the duration until next start trigger, or empty if none available
     */
    public Optional<Duration> getDurationUntilStartTrigger() {
        if (startConditionManager == null) {
            return Optional.empty();
        }
        return startConditionManager.getDurationUntilNextTrigger();
    }
    /**
     * Gets a detailed description of the stop conditions status
     * 
     * @return A string with detailed information about stop conditions
     */
    public String getDetailedStopConditionsStatus() {
        if (!hasAnyStopConditions()) {
            return "No stop conditions defined";
        }
        
        StringBuilder sb = new StringBuilder("Stop conditions: ");
        
        // Add logic type
        sb.append(stopConditionManager.requiresAll() ? "ALL must be met" : "ANY can be met");
        
        // Add fulfillability status
        if (!hasFullfillableStopConditions()) {
            sb.append(" (UNFULFILLABLE)");
        }
        
        // Add condition count
        int total = getTotalStopConditionCount();
        int satisfied = getSatisfiedStopConditionCount();
        sb.append(String.format(" - %d/%d conditions met", satisfied, total));
        
        // Add next trigger time if available
        Optional<ZonedDateTime> nextTrigger = getNextStopTriggerTime();
        if (nextTrigger.isPresent()) {
            sb.append(" - Next trigger: ").append(getNextStopTriggerTimeString());
        }
        
        return sb.toString();
    }
    /**
     * Gets a detailed description of the start conditions status
     * 
     * @return A string with detailed information about start conditions
     */
    public String getDetailedStartConditionsStatus() {
        if (!hasAnyStartConditions()) {
            return "No start conditions defined";
        }
        
        StringBuilder sb = new StringBuilder("Start conditions: ");
        
        // Add logic type
        sb.append(startConditionManager.requiresAll() ? "ALL must be met" : "ANY can be met");
        
        // Add fulfillability status
        if (!hasFullfillableStartConditions()) {
            sb.append(" (UNFULFILLABLE)");
        }
        
        // Add condition count and satisfaction status
        int totalStartConditions = startConditionManager.getConditions().size();
        long satisfiedStartConditions = startConditionManager.getConditions().stream()
                .filter(Condition::isSatisfied)
                .count();
        sb.append(String.format(" - %d/%d conditions met", satisfiedStartConditions, totalStartConditions));
        
        // Add next trigger time if available
        Optional<ZonedDateTime> nextTrigger = getCurrentStartTriggerTime();
        if (nextTrigger.isPresent()) {
            sb.append(" - Next trigger: ").append(getCurrentStartTriggerTimeString());
        }
        
        return sb.toString();
    }
    
    /**
     * Determines if the plugin should be started immediately based on its current
     * start condition status
     * 
     * @return true if the plugin should be started immediately
     */
    public boolean shouldStartImmediately() {
        // If no start conditions, don't start automatically
        if (!hasAnyStartConditions()) {
            return false;
        }
        
        // If start conditions are met, start the plugin
        if (startConditionManager.areConditionsMet()) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Logs the defined start conditions with their current states
     */
    private void logDefinedStartConditionWithStates() {
        logStartConditionsWithDetails();
        
        // If the conditions are unfulfillable, log a warning
        if (!hasFullfillableStartConditions()) {
            log.warn("Plugin {} has unfulfillable start conditions - may not start properly", name);
        }
        
        // Log progress percentage
        double progress = startConditionManager.getProgressTowardNextTrigger();
        log.info("Plugin {} start condition progress: {:.2f}%", name, progress);
    }
    
    /**
    * Updates the isDueToRun method to use the diagnostic helper for logging
    */
    public boolean isDueToRun() {
        // Check if we're already running
        if (isRunning()) {
            return false;
        }
        
        // For plugins with start conditions, check if those conditions are met
        if (!hasAnyStartConditions()) {
            //log.info("No start conditions defined for plugin '{}'", name);
            return false;
        }
        
        
        
        // Log at appropriate levels
        if (Microbot.isDebug()) {
            // Build comprehensive log info using our diagnostic helper
            String diagnosticInfo = diagnoseStartConditions();
            // In debug mode, log the full detailed diagnostics
            log.debug("\n[isDueToRun] - \n"+diagnosticInfo);
        }
          
        
        // Check if start conditions are met
        return startConditionManager.areConditionsMet();
    }    

    /**
    * Updates the primary time condition for this plugin schedule entry.
    * This method replaces the original time condition that was added when the entry was created,
    * but preserves any additional conditions that might have been added later.
    * 
    * @param newTimeCondition The new time condition to use
    * @return true if a time condition was found and replaced, false otherwise
    */
    public boolean updatePrimaryTimeCondition(TimeCondition newTimeCondition) {
        if (startConditionManager == null || newTimeCondition == null) {
            return false;
        }     
        startConditionManager.pauseWatchdogs();           
        // First, find the existing time condition. We'll assume the first time condition 
        // we find is the primary one that was added at creation
        TimeCondition existingTimeCondition = this.mainTimeStartCondition;                
        
        // If we found a time condition, replace it
        if (existingTimeCondition != null) {
            Optional<ZonedDateTime> currentTrigDateTime = existingTimeCondition.getCurrentTriggerTime();
            Optional<ZonedDateTime> newTrigDateTime = newTimeCondition.getCurrentTriggerTime();
            log.info("Replacing time condition {} with {}", 
                    existingTimeCondition.getDescription(), 
                    newTimeCondition.getDescription());
            
            
            boolean isDefaultByScheduleType = this.isDefault();
          
            
            // Check if new condition is a one-second interval (default)
            boolean willBeDefaultByScheduleType = false;
            if (newTimeCondition instanceof IntervalCondition) {
                IntervalCondition intervalCondition = (IntervalCondition) newTimeCondition;
                if (intervalCondition.getInterval().getSeconds() <= 1) {
                    willBeDefaultByScheduleType = true;
                }
            }
            
            // Remove the existing condition and add the new one
            if (startConditionManager.removeCondition(existingTimeCondition)) {
                if (!startConditionManager.containsCondition(newTimeCondition)) {
                    startConditionManager.addUserCondition(newTimeCondition);
                }
                
                // Update default status if needed
                if (willBeDefaultByScheduleType) {
                    //this.setDefault(true);
                    //this.setPriority(0);
                } else if (isDefaultByScheduleType && !willBeDefaultByScheduleType) {
                    // Only change from default if it was set automatically by condition type
                    //this.setDefault(false);
                }                
                
                this.mainTimeStartCondition = newTimeCondition;                                
            }
            if (currentTrigDateTime.isPresent() && newTrigDateTime.isPresent()) {
                // Check if the new trigger time is different from the current one
                if (!currentTrigDateTime.get().equals(newTrigDateTime.get())) {
                    log.info("\n\tUpdated main start time for Plugin'{}'\nfrom {}\nto {}", 
                            name, 
                            currentTrigDateTime.get().format(DATE_TIME_FORMATTER),
                            newTrigDateTime.get().format(DATE_TIME_FORMATTER));                    
                } else {
                    log.info("\n\tStart next time for Pugin '{}' remains unchanged", name);
                }
            }
        } else {
            // No existing time condition found, just add the new one
            log.info("No existing time condition found, adding new condition: {}", 
                    newTimeCondition.getDescription());
            // Check if the condition already exists before adding it
            if (startConditionManager.containsCondition(newTimeCondition)) {
                log.info("Condition {} already exists in the manager, not adding a duplicate", 
                newTimeCondition.getDescription());
                // Still need to update start conditions in case the existing one needs resetting                                                
            }else{
                startConditionManager.addUserCondition(newTimeCondition);
            }            
            this.mainTimeStartCondition = newTimeCondition;                 
            //updateStartConditions();// we have new condition ->  new start time ?
        }     
        startConditionManager.resumeWatchdogs();   
        return true;
    }

    /**
     * Update the lastRunTime to now and reset start conditions
     */
    private void resetStartConditions() {
        // Update last run time
        lastRunTime = roundToMinutes(ZonedDateTime.now(ZoneId.systemDefault()));
        Optional<ZonedDateTime> nextTriggerTimeBeforeReset = getCurrentStartTriggerTime();
        // Handle time conditions
        if (startConditionManager != null) {
            log.info("\nUpdating start conditions for plugin '{}'", name);
            startConditionManager.reset();
            Optional<ZonedDateTime> triggerTimeAfterReset = getCurrentStartTriggerTime();                      
            // Update the nextRunTime for legacy compatibility if possible
            
            if (triggerTimeAfterReset.isPresent()) {
                ZonedDateTime nextRunTime = triggerTimeAfterReset.get();
                log.info("\n\tUpdated run time for Plugin '{}'\nruntime before: {}\n next runtime: {}", 
                        name, 
                        nextTriggerTimeBeforeReset.map(t -> t.format(DATE_TIME_FORMATTER)).orElse("N/A"),
                        nextRunTime.format(DATE_TIME_FORMATTER));
            } else {
                // No future trigger time found
                if (hasTriggeredOneTimeStartConditions() && !canStartTriggerAgain()) {
                    log.info("One-time conditions for {} triggered, not scheduling next run", name);
                }
            }
        }
    }

    /**
     * Reset stop conditions
     */
    private void resetStopConditions() {
        if (stopConditionManager != null) {
            stopConditionManager.reset();            
            // Log that stop conditions were reset
            log.debug("Reset stop conditions for plugin '{}'", name);
        }
    }

    
    /**
     * Get a formatted display of the scheduling interval
     */
    public String getIntervalDisplay() {
        if (!hasAnyStartConditions()) {
            return "No schedule defined";
        }
        
        List<TimeCondition> timeConditions = startConditionManager.getTimeConditions();
        if (timeConditions.isEmpty()) {
            return "Non-time conditions only";
        }
        
        // Check for common condition types
        if (timeConditions.size() == 1) {
            TimeCondition condition = timeConditions.get(0);
            
            if (condition instanceof SingleTriggerTimeCondition) {
                ZonedDateTime triggerTime = ((SingleTriggerTimeCondition) condition).getTargetTime();
                return "Once at " + triggerTime.format(DATE_TIME_FORMATTER);
            } 
            else if (condition instanceof IntervalCondition) {
                Duration interval = ((IntervalCondition) condition).getInterval();
                long hours = interval.toHours();
                long minutes = interval.toMinutes() % 60;
                
                if (hours > 0) {
                    return String.format("Every %d hour%s %s", 
                            hours, 
                            hours > 1 ? "s" : "",
                            minutes > 0 ? minutes + " min" : "");
                } else {
                    return String.format("Every %d minute%s", 
                            minutes, 
                            minutes > 1 ? "s" : "");
                }
            }
            else if (condition instanceof TimeWindowCondition) {
                TimeWindowCondition windowCondition = (TimeWindowCondition) condition;
                LocalTime startTime = windowCondition.getStartTime();
                LocalTime endTime = windowCondition.getEndTime();
                
                return String.format("Between %s and %s daily", 
                        startTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                        endTime.format(DateTimeFormatter.ofPattern("HH:mm")));
            }
        }
        
        // If we have multiple time conditions or other complex scenarios
        return "Complex time schedule";
    }

    /**
     * Get a formatted display of when this plugin will run next
     */
    public String getNextRunDisplay() {
        return getNextRunDisplay(System.currentTimeMillis());
    }

    /**
     * Get a formatted display of when this plugin will run next, including
     * condition information.
     * 
     * @param currentTimeMillis Current system time in milliseconds
     * @return Human-readable description of next run time or condition status
     */
    public String getNextRunDisplay(long currentTimeMillis) {
        if (!enabled) {
            return "Disabled";
        }

        // If plugin is running, show progress or status information
        if (isRunning()) {
            if (!stopConditionManager.getConditions().isEmpty()) {
                double progressPct = getStopConditionProgress();
                if (progressPct > 0 && progressPct < 100) {
                    return String.format("Running (%.1f%% complete)", progressPct);
                }
                return "Running with conditions";
            }
            return "Running";
        }
        
        // Check for start conditions
        if (hasAnyStartConditions()) {
            // Check if we can determine the next trigger time
            Optional<ZonedDateTime> nextTrigger = getCurrentStartTriggerTime();
            if (nextTrigger.isPresent()) {
                ZonedDateTime triggerTime = nextTrigger.get();
                ZonedDateTime currentTime = ZonedDateTime.ofInstant(
                        Instant.ofEpochMilli(currentTimeMillis),
                        ZoneId.systemDefault());
                
                // If it's due to run now
                if (!currentTime.isBefore(triggerTime)) {
                    return "Due to run";
                }
                
                // Calculate time until next run
                Duration timeUntil = Duration.between(currentTime, triggerTime);
                long hours = timeUntil.toHours();
                long minutes = timeUntil.toMinutes() % 60;
                long seconds = timeUntil.getSeconds() % 60;
                
                if (hours > 0) {
                    return String.format("In %dh %dm", hours, minutes);
                } else if (minutes > 0) {
                    return String.format("In %dm %ds", minutes, seconds);
                } else {
                    return String.format("In %ds", seconds);
                }
            } else if (shouldStartImmediately()) {
                return "Due to run";
            } else if (hasTriggeredOneTimeStartConditions() && !canStartTriggerAgain()) {
                return "Completed";
            }
            
            return "Waiting for conditions";
        }
        
        
        
        return "Schedule not set";
    }
    
    public void addStartCondition(Condition condition) {
        startConditionManager.addUserCondition(condition);
    }
    public void addStopCondition(Condition condition) {
        stopConditionManager.addUserCondition(condition);
    }

    public List<Condition> getStopConditions() {
        return stopConditionManager.getConditions();
    }
    public boolean hasStopConditions() {
        return stopConditionManager.hasConditions();
    }
    public boolean hasStartConditions() {
        return startConditionManager.hasConditions();
    }
    public List<Condition> getStartConditions() {
        return startConditionManager.getConditions();
    }

    // Determine if plugin should stop based on conditions and/or duration
    public boolean shouldStop() {
        if (finished) {
            return true; // Plugin has finished its run
        }
        if (isRunning()) {
            if (!isEnabled()){
                return true; //enabled was disabled -> stop the plugin gracefully -> soft stop should be trigged when possible
            }
        }
        // Check if conditions are met and we should stop when conditions are met
        if (areStopConditionsMet() ) {
            return true;
        }

        return false;
    }

    public boolean areStopConditionsMet() {
        if (stopConditionManager.getConditions().isEmpty()) {
            return false;
        }
        return stopConditionManager.areConditionsMet();
    }
    public boolean areStartConditionsMet() {
        return startConditionManager.areConditionsMet();
    }

    public String getConditionsDescription() {
        return stopConditionManager.getDescription();
    }
    public boolean stop(boolean successfulRun) {
        ZonedDateTime now = ZonedDateTime.now();
        // Initial stop attempt
        if (!stopInitiated) {
            logStopConditionsWithDetails();
            log.info("Stopping plugin {} due to conditions being met - initiating soft stop", name);
            this.softStop(successfulRun); // This will start the monitoring thread
        }
        // Plugin didn't stop after previous attempts
        else if (isRunning()) {
            Duration timeSinceFirstAttempt = Duration.between(stopInitiatedTime, now);
            Duration timeSinceLastAttempt = Duration.between(lastStopAttemptTime, now);
            
            // Force hard stop if we've waited too long
            if ( hardStopTimeout.compareTo(Duration.ZERO) > 0 && timeSinceFirstAttempt.compareTo(hardStopTimeout) > 0 
                && (getPlugin() instanceof SchedulablePlugin)
                && ((SchedulablePlugin) getPlugin()).isHardStoppable()) {
                log.warn("Plugin {} failed to respond to soft stop after {} seconds - forcing hard stop", 
                         name, timeSinceFirstAttempt.toSeconds());
                
                // Stop current monitoring and start new one for hard stop
                stopMonitoringThread();
                this.hardStop(true);
            }
            // Retry soft stop at configured intervals
            else if (timeSinceLastAttempt.compareTo(softStopRetryInterval) > 0) {
                log.info("Plugin {} still running after soft stop - retrying (attempt time: {} seconds)", 
                         name, timeSinceFirstAttempt.toSeconds());
                lastStopAttemptTime = now;
                this.softStop(true);
            }else if (hardStopTimeout.compareTo(Duration.ZERO) > 0  && timeSinceLastAttempt.compareTo(hardStopTimeout.multipliedBy(2)) > 0) {                    
                log.error("Forcibly shutting down the client due to unresponsive plugin: {}", name);

                // Schedule client shutdown on the client thread to ensure it happens safely
                Microbot.getClientThread().invoke(() -> {
                    try {
                        // Log that we're shutting down
                        log.warn("Initiating emergency client shutdown due to plugin: {} cant be stopped", name);
                        
                        // Give a short delay for logging to complete
                        Thread.sleep(1000);
                        
                        // Forcibly exit the JVM with a non-zero status code to indicate abnormal termination
                        System.exit(1);
                    } catch (Exception e) {
                        log.error("Failed to shut down client", e);
                        // Ultimate fallback
                        Runtime.getRuntime().halt(1);
                    }
                    return true;
                });  
            }
        }
        return this.stopInitiated;
    }
    
    public boolean checkConditionsAndStop(boolean successfulRun) {        
        
        if (shouldStop()) {
            this.stopInitiated = this.stop(successfulRun);
            // Monitor thread will handle the successful stop case
        }
        // Reset stop tracking if conditions no longer require stopping
        else if (stopInitiated) {
            log.info("Plugin {} conditions no longer require stopping - resetting stop state", name);
            this.stopInitiated = false;
            this.stopInitiatedTime = null;
            this.lastStopAttemptTime = null;
            stopMonitoringThread();
        }
        return this.stopInitiated;
        
    }

    /**
     * Logs all defined conditions when plugin starts
     */
    private void logStopConditions() {
        List<Condition> conditionList = stopConditionManager.getConditions();
        logConditionInfo(conditionList,"Defined Stop Conditions", true);
    }

    /**
     * Logs which conditions are met and which aren't when plugin stops
     */
    private void logStopConditionsWithDetails() {
        List<Condition> conditionList = stopConditionManager.getConditions();
        logConditionInfo(conditionList,"Defined Stop Conditions", true);
    }

    
    

    /**
     * Creates a consolidated log of all condition-related information
     * @param logINFOHeader The header to use for the log message
     * @param includeDetails Whether to include full details of conditions
     */
    public void logConditionInfo(List<Condition> conditionList, String logINFOHeader, boolean includeDetails) {
        
        StringBuilder sb = new StringBuilder();
        
        sb.append("\nPlugin '").append(cleanName).append("' [").append(logINFOHeader).append("]: ");

        if (conditionList.isEmpty()) {
            sb.append("No stop conditions defined");
            log.info(sb.toString());
            return;
        }
        
        // Basic condition count and logic
        sb.append(conditionList.size()).append(" condition(s) using ")
          .append(stopConditionManager.requiresAll() ? "AND" : "OR").append(" logic\n");
        
        if (!includeDetails) {
            log.info(sb.toString());
            return;
        }
        
        // Detailed condition listing with status
        
        int metCount = 0;
        
        for (int i = 0; i < conditionList.size(); i++) {
            Condition condition = conditionList.get(i);
            boolean isSatisfied = condition.isSatisfied();
            if (isSatisfied) metCount++;
            
            // Use the new getStatusInfo method for detailed status
            sb.append("  ").append(i + 1).append(". ")
              .append(condition.getStatusInfo(0, includeDetails).replace("\n", "\n    "));
            
            sb.append("\n");
        }
        
        if (includeDetails) {
            sb.append("Summary: ").append(metCount).append("/").append(conditionList.size())
              .append(" conditions met");
        }
        
        log.info(sb.toString());
    }



    

    // /**
    //  * Registers any custom stopping conditions provided by the plugin.
    //  * These conditions are combined with existing conditions using AND logic
    //  * to ensure plugin-defined conditions have the highest priority.
    //  * 
    //  * @param plugin    The plugin that might provide conditions
    //  * @param scheduled The scheduled instance managing the plugin
    //  */
    // public void registerPluginStoppingConditions() {
    //     if (this.plugin == null) {
    //         this.plugin = getPlugin();
    //     }
    //     log.info("Registering stopping conditions for plugin '{}'", name);
    //     if (this.plugin instanceof SchedulablePlugin) {
    //         SchedulablePlugin provider = (SchedulablePlugin) plugin;

    //         // Get conditions from the provider
            
    //         List<Condition> pluginConditions = provider.getStopCondition().getConditions();
    //         if (pluginConditions != null && !pluginConditions.isEmpty()) {                
    //             // Get or create plugin's logical structure
                
    //             LogicalCondition pluginLogic = provider.getStopCondition();                                
    //             // Set the new root condition                
    //             getStopConditionManager().setPluginCondition(pluginLogic);
                
    //             // Log with the consolidated method
    //             logStopConditionsWithDetails();
    //         } else {
    //             log.info("Plugin '{}' implements StoppingConditionProvider but provided no conditions",
    //                                     plugin.getName());
    //         }
    //     }
    // }
    // private boolean registerPluginStartingConditions(){
    //     if (this.plugin == null) {
    //         this.plugin = getPlugin();
    //     }
    //     log.info("Registering start conditions for plugin '{}'", name);
    //     if (this.plugin instanceof SchedulablePlugin) {
    //         SchedulablePlugin provider = (SchedulablePlugin) plugin;

    //         // Get conditions from the provider
    //         if (provider.getStartCondition() == null) {
    //             log.warn("Plugin '{}' implements ConditionProvider but provided no start conditions", plugin.getName());
    //             return false;
    //         }
    //         List<Condition> pluginConditions = provider.getStartCondition().getConditions();
    //         if (pluginConditions != null && !pluginConditions.isEmpty()) {
    //             // Create a new AND condition as the root

                

    //             // Get or create plugin's logical structure
    //             LogicalCondition pluginLogic = provider.getStartCondition();

    //             if (pluginLogic != null) {
    //                 for (Condition condition : pluginConditions) {
    //                     if(pluginLogic.contains(condition)){
    //                         continue;
    //                     }
    //                 }
                    
    //             }else{
    //                 throw new RuntimeException("Plugin '"+name+"' implements ConditionProvider but provided no conditions");
    //             }
                                
    //             // Set the new root condition
    //             getStartConditionManager().setPluginCondition(pluginLogic);
                
    //             // Log with the consolidated method
    //             logStartConditionsWithDetails();
    //         } else {
    //             log.info("Plugin '{}' implements condition Provider but provided no explicit start conditions defined",
    //                     plugin.getName());
    //         }
    //     }
    //     return true;

    // }
   
/**
     * Registers conditions from the plugin in an efficient manner.
     * This method uses the new updatePluginCondition approach to intelligently
     * merge conditions while preserving state and reducing unnecessary reinitializations.
     * 
     * @param updateMode Controls how conditions are merged (default: ADD_ONLY)
     */
    private void registerPluginConditions(UpdateOption updateOption) {
        if (this.plugin == null) {
            this.plugin = getPlugin();
        }
        
        log.info("Registering plugin conditions for plugin '{}' with update mode: {}", name, updateOption);
        
        
        // Register start conditions
        boolean startConditionsUpdated = registerPluginStartingConditions(updateOption);
        
        // Register stop conditions
        boolean stopConditionsUpdated = registerPluginStoppingConditions(updateOption);
        
        if (startConditionsUpdated || stopConditionsUpdated) {
            log.info("Successfully updated plugin conditions for '{}'", name);
            
            // Optimize structure if changes were made
            if (updateOption != UpdateOption.REMOVE_ONLY) {
                optimizeConditionStructures();
            }
        } else {
            log.debug("No changes needed to plugin conditions for '{}'", name);
        }
    }
    
    /**
     * Default version of registerPluginConditions that uses ADD_ONLY mode
     */
    private void registerPluginConditions() {
        registerPluginConditions(UpdateOption.SYNC);
    }

    /**
     * Registers or updates starting conditions from the plugin.
     * Uses the updatePluginCondition method to efficiently merge conditions.
     * 
     * @return true if conditions were updated, false if no changes were needed
     */
    private boolean registerPluginStartingConditions(UpdateOption updateOption) {
        if (this.plugin == null) {
            this.plugin = getPlugin();
        }
        
        log.debug("Registering start conditions for plugin '{}'", name);
        this.startConditionManager.pauseWatchdogs();
        this.startConditionManager.setPluginCondition(new OrCondition());
        if (!(this.plugin instanceof SchedulablePlugin)) {
            log.debug("Plugin '{}' is not a SchedulablePlugin, skipping start condition registration", name);
            return false;
        }
        
        SchedulablePlugin provider = (SchedulablePlugin) plugin;
        
        // Get conditions from the provider
        if (provider.getStartCondition() == null) {
            log.warn("Plugin '{}' implements ConditionProvider but provided no start conditions", plugin.getName());
            return false;
        }
        
        List<Condition> pluginConditions = provider.getStartCondition().getConditions();
        if (pluginConditions == null || pluginConditions.isEmpty()) {
            log.debug("Plugin '{}' provided no explicit start conditions", plugin.getName());
            return false;
        }
        
        // Get or create plugin's logical structure
        LogicalCondition pluginLogic = provider.getStartCondition();
        
        if (pluginLogic == null) {
            log.warn("Plugin '{}' returned null start condition", name);
            return false;
        }
        // Use the new update method with the specified option
        boolean updated = getStartConditionManager().updatePluginCondition(pluginLogic, updateOption);
    
        // Log with a consolidated method if changes were made
        if (updated) {
            log.debug("Updated start conditions for plugin '{}'", name);
            logStartConditionsWithDetails();
            
            // Validate the condition structure
            validateStartConditions();
        }
        this.startConditionManager.resumeWatchdogs();
        
        return updated;
    }

    /**
     * Registers or updates stopping conditions from the plugin.
     * Uses the updatePluginCondition method to efficiently merge conditions.
     * 
     * @return true if conditions were updated, false if no changes were needed
     */
    private boolean registerPluginStoppingConditions(UpdateOption updateOption) {
        if (this.plugin == null) {
            this.plugin = getPlugin();
        }
        this.stopConditionManager.pauseWatchdogs();
        this.stopConditionManager.setPluginCondition(new OrCondition());
        log.debug("Registering stopping conditions for plugin '{}'", name);
        
        if (!(this.plugin instanceof SchedulablePlugin)) {
            log.debug("Plugin '{}' is not a SchedulablePlugin, skipping stop condition registration", name);
            return false;
        }
        
        SchedulablePlugin provider = (SchedulablePlugin) plugin;
        
        // Get conditions from the provider
        if (provider.getStopCondition()  == null) {
            log.debug("Plugin '{}' provided no explicit stop conditions", plugin.getName());
            return false;
        }
        List<Condition> pluginConditions = provider.getStopCondition().getConditions();
        if (pluginConditions == null || pluginConditions.isEmpty()) {
            log.debug("Plugin '{}' provided no explicit stop conditions", plugin.getName());
            return false;
        }
        
        // Get plugin's logical structure
        LogicalCondition pluginLogic = provider.getStopCondition();
        
        if (pluginLogic == null) {
            log.warn("Plugin '{}' returned null stop condition", name);
            return false;
        }
        
        // Use the new update method with the specified option
        boolean updated = getStopConditionManager().updatePluginCondition(pluginLogic, updateOption);
    
        // Log with the consolidated method if changes were made
        if (updated) {
            log.debug("Updated stop conditions for plugin '{}'", name);
            logStopConditionsWithDetails();
            
            // Validate the condition structure
            validateStopConditions();
        }
        this.stopConditionManager.resumeWatchdogs();
        
        return updated;
    }
    
    /**
     * Creates and schedules watchdogs to monitor for condition changes from the plugin.
     * This allows plugins to dynamically update their conditions at runtime,
     * and have those changes automatically detected and integrated.
     * 
     * Both start and stop condition watchdogs are scheduled using the shared thread pool
     * from ConditionManager to avoid creating redundant resources.
     *
     * @param checkIntervalMillis How often to check for condition changes in milliseconds
     * @param updateMode Controls how conditions are merged during updates
     * @return true if at least one watchdog was successfully scheduled
     */
    public boolean scheduleConditionWatchdogs(long checkIntervalMillis, UpdateOption updateOption) {
        if(this.plugin == null) {
            this.plugin = getPlugin();
        }
        
        if (!watchdogsEnabled) {
            log.debug("Watchdogs are disabled for '{}', not scheduling", name);
            return false;
        }
        
        log.info("Scheduling condition watchdogs for plugin '{}' with interval {}ms using update mode: {}", 
                 name, checkIntervalMillis, updateOption);
                 
        if (!(this.plugin instanceof SchedulablePlugin)) {            
            log.debug("Cannot schedule condition watchdogs for non-SchedulablePlugin");
            return false;                                        
        }
        
        // Cancel any existing watchdog tasks first
        //cancelConditionWatchdogs();
        
        SchedulablePlugin schedulablePlugin = (SchedulablePlugin) this.plugin;
        boolean anyScheduled = false;
        
        try {
            // Create suppliers that get the current plugin conditions
            Supplier<LogicalCondition> startConditionSupplier = 
                () -> schedulablePlugin.getStartCondition();
            
            Supplier<LogicalCondition> stopConditionSupplier = 
                () -> schedulablePlugin.getStopCondition();
            
            // Schedule the start condition watchdog
            startConditionWatchdogFuture = startConditionManager.scheduleConditionWatchdog(
                startConditionSupplier,
                checkIntervalMillis,
                updateOption
            );
            
            // Schedule the stop condition watchdog
            stopConditionWatchdogFuture = stopConditionManager.scheduleConditionWatchdog(
                stopConditionSupplier,
                checkIntervalMillis,
                updateOption
            );
            
            anyScheduled = true;
            log.info("Scheduled condition watchdogs for plugin '{}' with interval {}ms using update mode: {}", 
                     name, checkIntervalMillis, updateOption);
        } catch (Exception e) {
            log.error("Failed to schedule condition watchdogs for '{}'", name, e);
        }
        
        return anyScheduled;
    }

    /**
     * Schedules condition watchdogs with the default ADD_ONLY update mode.
     * 
     * @param checkIntervalMillis How often to check for condition changes in milliseconds
     * @return true if at least one watchdog was successfully scheduled
     */
    public boolean scheduleConditionWatchdogs(long checkIntervalMillis) {
        return scheduleConditionWatchdogs(checkIntervalMillis, UpdateOption.SYNC);
    }
    
/**
     * Validates the start conditions structure and logs any issues found.
     * This helps identify potential problems with condition hierarchies.
     */
    private void validateStartConditions() {
        LogicalCondition startLogical = getStartConditionManager().getFullLogicalCondition();
        if (startLogical != null) {
            List<String> issues = startLogical.validateStructure();
            if (!issues.isEmpty()) {
                log.warn("Validation issues found in start conditions for '{}':", name);
                for (String issue : issues) {
                    log.warn("  - {}", issue);
                }
            }
        }
    }
       /**
     * Validates the stop conditions structure and logs any issues found.
     * This helps identify potential problems with condition hierarchies.
     */
    private void validateStopConditions() {
        LogicalCondition stopLogical = getStopConditionManager().getFullLogicalCondition();
        if (stopLogical != null) {
            List<String> issues = stopLogical.validateStructure();
            if (!issues.isEmpty()) {
                log.warn("Validation issues found in stop conditions for '{}':", name);
                for (String issue : issues) {
                    log.warn("  - {}", issue);
                }
            }
        }
    }
      /**
     * Optimizes both start and stop condition structures by flattening unnecessary nesting
     * and removing empty logical conditions.
     */
    private void optimizeConditionStructures() {
        // Optimize start conditions
        LogicalCondition startLogical = getStartConditionManager().getFullLogicalCondition();
        if (startLogical != null) {
            boolean optimized = startLogical.optimizeStructure();
            if (optimized) {
                log.debug("Optimized start condition structure for '{}'", name);
            }
        }
        
        // Optimize stop conditions
        LogicalCondition stopLogical = getStopConditionManager().getFullLogicalCondition();
        if (stopLogical != null) {
            boolean optimized = stopLogical.optimizeStructure();
            if (optimized) {
                log.debug("Optimized stop condition structure for '{}'", name);
            }
        }
    }
 
    
    /**
     * Checks if any condition watchdogs are currently active for this plugin.
     * 
     * @return true if at least one watchdog is active
     */
    public boolean hasActiveWatchdogs() {
        return (startConditionManager != null && startConditionManager.areWatchdogsRunning()) || 
               (stopConditionManager != null && stopConditionManager.areWatchdogsRunning());
    }
    
    /**
     * Properly clean up resources when this object is closed or disposed.
     * This is more reliable than using finalize() which is deprecated.
     */
    @Override
    public void close() {
        // Clean up watchdogs and other resources
        //cancelConditionWatchdogs();
        
        // Stop any monitoring threads
        stopMonitoringThread();
        
        // Ensure both condition managers are closed properly
        if (startConditionManager != null) {
            startConditionManager.close();
        }
        
        if (stopConditionManager != null) {
            stopConditionManager.close();
        }
        
        log.debug("Resources cleaned up for plugin schedule entry: '{}'", name);
    }
   
    /**
     * Calculates overall progress percentage across all conditions.
     * This respects the logical structure of conditions.
     * Returns 0 if progress cannot be determined.
     */
    public double getStopConditionProgress() {
        // If there are no conditions, no progress to report
        if (stopConditionManager == null || stopConditionManager.getConditions().isEmpty()) {
            return 0;
        }
        
        // If using logical root condition, respect its logical structure
        LogicalCondition rootLogical = stopConditionManager.getFullLogicalCondition();
        if (rootLogical != null) {
            return rootLogical.getProgressPercentage();
        }
        
        // Fallback for direct condition list: calculate based on AND/OR logic
        boolean requireAll = stopConditionManager.requiresAll();
        List<Condition> conditions = stopConditionManager.getConditions();
        
        if (requireAll) {
            // For AND logic, use the minimum progress (weakest link)
            return conditions.stream()
                .mapToDouble(Condition::getProgressPercentage)
                .min()
                .orElse(0.0);
        } else {
            // For OR logic, use the maximum progress (strongest link)
            return conditions.stream()
                .mapToDouble(Condition::getProgressPercentage)
                .max()
                .orElse(0.0);
        }
    }

    /**
     * Gets the total number of conditions being tracked.
     */
    public int getTotalStopConditionCount() {
        if (stopConditionManager == null) {
            return 0;
        }
        
        LogicalCondition rootLogical = stopConditionManager.getFullLogicalCondition();
        if (rootLogical != null) {
            return rootLogical.getTotalConditionCount();
        }
        
        return stopConditionManager.getConditions().stream()
            .mapToInt(Condition::getTotalConditionCount)
            .sum();
    }

    /**
     * Gets the number of conditions that are currently met.
     */
    public int getSatisfiedStopConditionCount() {
        if (stopConditionManager == null) {
            return 0;
        }
        
        LogicalCondition rootLogical = stopConditionManager.getFullLogicalCondition();
        if (rootLogical != null) {
            return rootLogical.getMetConditionCount();
        }
        
        return stopConditionManager.getConditions().stream()
            .mapToInt(Condition::getMetConditionCount)
            .sum();
    }
    public LogicalCondition getLogicalStopCondition() {
        return stopConditionManager.getFullLogicalCondition();
    }


    // Add getter/setter for the new fields
    public boolean isAllowRandomScheduling() {
        return allowRandomScheduling;
    }

    public void setAllowRandomScheduling(boolean allowRandomScheduling) {
        this.allowRandomScheduling = allowRandomScheduling;
    }

    public int getRunCount() {
        return runCount;
    }

    private void incrementRunCount() {
        this.runCount++;
    }

    // Setter methods for the configurable timeouts
    public void setSoftStopRetryInterval(Duration interval) {
        this.softStopRetryInterval = interval;
    }

    public void setHardStopTimeout(Duration timeout) {
        this.hardStopTimeout = timeout;
    }

 

    /**
     * Convert a list of ScheduledPlugin objects to JSON
     */
    public static String toJson(List<PluginScheduleEntry> plugins, String version) {
        return ScheduledSerializer.toJson(plugins, version);
    }


        /**
     * Parse JSON into a list of ScheduledPlugin objects
     */
    public static List<PluginScheduleEntry> fromJson(String json, String version) {
        return ScheduledSerializer.fromJson(json, version);
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != getClass()) return false;
        
        PluginScheduleEntry that = (PluginScheduleEntry) o;
        
        // Two entries are equal if:
        // 1. They have the same name AND
        // 2. They have the same start conditions and stop conditions
        //    OR they are the same object reference
        
        if (!Objects.equals(name, that.name)) return false;
        
        // If they're the same name, we need to distinguish by conditions
        if (startConditionManager != null && that.startConditionManager != null) {
            if (!startConditionManager.getConditions().equals(that.startConditionManager.getConditions())) {
                return false;
            }
        } else if (startConditionManager != null || that.startConditionManager != null) {
            return false;
        }
        
        if (stopConditionManager != null && that.stopConditionManager != null) {
            return stopConditionManager.getConditions().equals(that.stopConditionManager.getConditions());
        } else {
            return stopConditionManager == null && that.stopConditionManager == null;
        }
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (startConditionManager != null ? startConditionManager.getConditions().hashCode() : 0);
        result = 31 * result + (stopConditionManager != null ? stopConditionManager.getConditions().hashCode() : 0);
        return result;
    }

    public int getPriority() {
        return priority;
    }
    
    public void setPriority(int priority) {
        this.priority = priority;
    }
    
    public boolean isDefault() {
        return isDefault;
    }
    
    public void setDefault(boolean isDefault) {
        log.info("Setting default to {} for plugin '{}'", isDefault, name);
        this.isDefault = isDefault;
    }
    /**
    * Generic helper method to build condition diagnostics for both start and stop conditions
    * 
    * @param isStartCondition Whether to diagnose start conditions (true) or stop conditions (false)
    * @return A detailed diagnostic string
    */
    private String buildConditionDiagnostics(boolean isStartCondition) {
        StringBuilder sb = new StringBuilder();
        String conditionType = isStartCondition ? "Start" : "Stop";
        ConditionManager conditionManager = isStartCondition ? startConditionManager : stopConditionManager;
        List<Condition> conditions = isStartCondition ? getStartConditions() : getStopConditions();
        
        // Header with plugin name
        sb.append("[").append(cleanName).append("] ").append(conditionType).append(" condition diagnostics:\n");
        
        // Check if running (only relevant for start conditions)
        if (isStartCondition && isRunning()) {
            sb.append("- Plugin is already running (will not start again until stopped)\n");
            return sb.toString();
        }
        
        // Check for conditions
        if (conditions.isEmpty()) {
            sb.append("- No ").append(conditionType.toLowerCase()).append(" conditions defined\n");
            return sb.toString();
        }
        
        // Condition logic type
        sb.append("- Logic: ")
        .append(conditionManager.requiresAll() ? "ALL conditions must be met" : "ANY condition can be met")
        .append("\n");
        
        // Condition description
        sb.append("- Conditions: ")
        .append(conditionManager.getDescription())
        .append("\n");
        
        // Check if they can be fulfilled
        boolean canBeFulfilled = isStartCondition ? 
                hasFullfillableStartConditions() : 
                hasFullfillableStopConditions();
        
        if (!canBeFulfilled) {
            sb.append("- Conditions cannot be fulfilled (e.g., one-time conditions already triggered)\n");
        }
        
        // Progress
        double progress = isStartCondition ? 
                conditionManager.getProgressTowardNextTrigger() : 
                getStopConditionProgress();
        sb.append("- Progress: ")
        .append(String.format("%.1f%%", progress))
        .append("\n");
        
        // Next trigger time
        Optional<ZonedDateTime> nextTrigger = isStartCondition ? 
                getCurrentStartTriggerTime() : 
                getNextStopTriggerTime();
        
        sb.append("- Next trigger: ");
        if (nextTrigger.isPresent()) {
            ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
            ZonedDateTime triggerTime = nextTrigger.get();
            
            sb.append(triggerTime).append("\n");
            sb.append("- Current time: ").append(now).append("\n");
            
            if (triggerTime.isBefore(now)) {
                sb.append("- Trigger time is in the past but conditions not met - may need reset\n");
            } else {
                Duration timeUntil = Duration.between(now, triggerTime);
                sb.append("- Time until trigger: ").append(formatDuration(timeUntil)).append("\n");
            }
        } else {
            sb.append("No future trigger time determined\n");
        }
        
        // Overall condition status
        boolean areConditionsMet = isStartCondition ? 
                startConditionManager.areConditionsMet() : 
                areStopConditionsMet();
        
        sb.append("- Status: ")
        .append(areConditionsMet ? 
                "CONDITIONS MET - Plugin is " + (isStartCondition ? "due to run" : "due to stop") : 
                "CONDITIONS NOT MET - Plugin " + (isStartCondition ? "will not run" : "will continue running"))
        .append("\n");
        
        // Individual condition status
        sb.append("- Individual conditions:\n");
        for (int i = 0; i < conditions.size(); i++) {
            Condition condition = conditions.get(i);
            sb.append("  ").append(i+1).append(". ")
            .append(condition.getDescription())
            .append(": ")
            .append(condition.isSatisfied() ? "SATISFIED" : "NOT SATISFIED");
            
            // Add progress if available
            double condProgress = condition.getProgressPercentage();
            if (condProgress > 0 && condProgress < 100) {
                sb.append(String.format(" (%.1f%%)", condProgress));
            }
            
            // For time conditions, show next trigger time
            if (condition instanceof TimeCondition) {
                Optional<ZonedDateTime> condTrigger = condition.getCurrentTriggerTime();
                if (condTrigger.isPresent()) {
                    sb.append(" (next trigger: ").append(condTrigger.get()).append(")");
                }
            }
            
            sb.append("\n");
        }
        
        return sb.toString();
    }

    /**
     * Performs a diagnostic check on start conditions and returns detailed information
     * about why a plugin might not be due to run
     * 
     * @return A string containing diagnostic information
     */
    public String diagnoseStartConditions() {
        return buildConditionDiagnostics(true);
    }

    /**
     * Performs a diagnostic check on stop conditions and returns detailed information
     * about why a plugin might or might not be due to stop
     * 
     * @return A string containing diagnostic information
     */
    public String diagnoseStopConditions() {
        return buildConditionDiagnostics(false);
    }

    /**
     * Formats a duration in a human-readable way
     */
    private String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        if (seconds < 60) {
            return seconds + " seconds";
        } else if (seconds < 3600) {
            return String.format("%dm %ds", seconds / 60, seconds % 60);
        } else if (seconds < 86400) {
            return String.format("%dh %dm %ds", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
        } else {
            return String.format("%dd %dh %dm", seconds / 86400, (seconds % 86400) / 3600, (seconds % 3600) / 60);
        }
    }

    /**
     * Checks whether this schedule entry contains only time-based conditions.
     * This is useful to determine if the plugin schedule is purely time-based
     * or if it has other types of conditions (e.g., resource, skill, etc.).
     *
     * @return true if the schedule only contains TimeCondition instances, false otherwise
     */
    public boolean hasOnlyTimeConditions() {
        // Check if start conditions contain only time conditions
        if (startConditionManager != null && !startConditionManager.hasOnlyTimeConditions()) {
            return false;
        }
        
        // Check if stop conditions contain only time conditions
        if (stopConditionManager != null && !stopConditionManager.hasOnlyTimeConditions()) {
            return false;
        }
        
        // Both condition managers contain only time conditions (or are empty)
        return true;
    }
    
    /**
     * Returns all non-time-based conditions from both start and stop conditions.
     * This can help identify which non-time conditions are present in the schedule.
     *
     * @return A list of all non-TimeCondition instances in this schedule entry
     */
    public List<Condition> getNonTimeConditions() {
        List<Condition> nonTimeConditions = new ArrayList<>();
        
        // Add non-time conditions from start conditions
        if (startConditionManager != null) {
            nonTimeConditions.addAll(startConditionManager.getNonTimeConditions());
        }
        
        // Add non-time conditions from stop conditions
        if (stopConditionManager != null) {
            nonTimeConditions.addAll(stopConditionManager.getNonTimeConditions());
        }
        
        return nonTimeConditions;
    }

    /**
     * Checks if this plugin would be due to run based only on its time conditions,
     * ignoring any non-time conditions that may be present in the schedule.
     * This is useful to determine if a plugin is being blocked from running by
     * time conditions or by other types of conditions.
     * 
     * @return true if the plugin would be scheduled to run based solely on time conditions
     */
    public boolean wouldRunBasedOnTimeConditionsOnly() {
        // Check if we're already running
        if (isRunning()) {
            return false;
        }
        
        // If no start conditions defined, plugin can't run automatically
        if (!hasAnyStartConditions()) {
            return false;
        }
        
        // Check if time conditions alone would be satisfied
        return startConditionManager.wouldBeTimeOnlySatisfied();
    }
    
    /**
     * Provides detailed diagnostic information about why a plugin is or isn't
     * running based on its time conditions only.
     * 
     * @return A diagnostic string explaining the time condition status
     */
    public String diagnoseTimeConditionScheduling() {
        StringBuilder sb = new StringBuilder();
        sb.append("Time condition scheduling diagnosis for '").append(cleanName).append("':\n");
        
        // First check if plugin is already running
        if (isRunning()) {
            sb.append("Plugin is already running - will not be scheduled again until stopped.\n");
            return sb.toString();
        }
        
        // Check if there are any start conditions
        if (!hasAnyStartConditions()) {
            sb.append("No start conditions defined - plugin can't be automatically scheduled.\n");
            return sb.toString();
        }
        
        // Get time-only condition status
        boolean wouldRunOnTimeOnly = startConditionManager.wouldBeTimeOnlySatisfied();
        boolean allConditionsMet = startConditionManager.areConditionsMet();
        
        sb.append("Time conditions only: ").append(wouldRunOnTimeOnly ? "WOULD RUN" : "WOULD NOT RUN").append("\n");
        sb.append("All conditions: ").append(allConditionsMet ? "SATISFIED" : "NOT SATISFIED").append("\n");
        
        // If time conditions would run but all conditions wouldn't, non-time conditions are blocking
        if (wouldRunOnTimeOnly && !allConditionsMet) {
            sb.append("Plugin is being blocked by non-time conditions.\n");
            
            // List the non-time conditions that are not satisfied
            List<Condition> nonTimeConditions = startConditionManager.getNonTimeConditions();
            sb.append("Non-time conditions blocking execution:\n");
            
            for (Condition condition : nonTimeConditions) {
                if (!condition.isSatisfied()) {
                    sb.append("  - ").append(condition.getDescription())
                      .append(" (").append(condition.getType()).append(")\n");
                }
            }
        } 
        // If time conditions would not run, show time condition status
        else if (!wouldRunOnTimeOnly) {
            sb.append("Plugin is waiting for time conditions to be met.\n");
            
            // Show next trigger time if available
            Optional<ZonedDateTime> nextTrigger = startConditionManager.getCurrentTriggerTime();
            if (nextTrigger.isPresent()) {
                ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
                Duration until = Duration.between(now, nextTrigger.get());
                
                sb.append("Next time trigger at: ").append(nextTrigger.get())
                  .append(" (").append(formatDuration(until)).append(" from now)\n");
            } else {
                sb.append("No future time trigger determined.\n");
            }
        }
        
        // Add detailed time condition diagnosis from condition manager
        sb.append("\n").append(startConditionManager.diagnoseTimeConditionsSatisfaction());
        
        return sb.toString();
    }
    
    /**
     * Creates a modified version of this schedule entry that contains only time conditions.
     * This is useful for evaluating how the plugin would be scheduled if only time 
     * conditions were considered.
     * 
     * @return A new PluginScheduleEntry with the same configuration but only time conditions
     */
    public PluginScheduleEntry createTimeOnlySchedule() {
        // Create a new schedule entry with the same basic properties
        PluginScheduleEntry timeOnlyEntry = new PluginScheduleEntry(
            name, 
            mainTimeStartCondition != null ? mainTimeStartCondition : null, 
            enabled,
            allowRandomScheduling
        );
        
        // Create time-only condition managers
        if (startConditionManager != null) {
            ConditionManager timeOnlyStartManager = startConditionManager.createTimeOnlyConditionManager();
            timeOnlyEntry.startConditionManager.setUserLogicalCondition(
                timeOnlyStartManager.getUserLogicalCondition());
            timeOnlyEntry.startConditionManager.setPluginCondition(
                timeOnlyStartManager.getPluginCondition());
        }
        
        if (stopConditionManager != null) {
            ConditionManager timeOnlyStopManager = stopConditionManager.createTimeOnlyConditionManager();
            timeOnlyEntry.stopConditionManager.setUserLogicalCondition(
                timeOnlyStopManager.getUserLogicalCondition());
            timeOnlyEntry.stopConditionManager.setPluginCondition(
                timeOnlyStopManager.getPluginCondition());
        }
        
        return timeOnlyEntry;
    }

    
}
