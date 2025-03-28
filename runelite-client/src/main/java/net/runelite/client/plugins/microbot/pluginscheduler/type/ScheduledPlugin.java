package net.runelite.client.plugins.microbot.pluginscheduler.type;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.api.StoppingConditionProvider;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ConditionManager;

import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;

import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.IntervalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.event.ScheduledStopEvent;

import net.runelite.client.plugins.microbot.pluginscheduler.serialization.ScheduledSerializer;



import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;


import java.util.List;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Slf4j
public class ScheduledPlugin {
    private transient Plugin plugin;
    private String name;
    private ScheduleType scheduleType;
    private int scheduleIntervalValue; // The numeric value for the interval
    private String duration; // Optional duration to run the plugin
    private boolean enabled;
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            stopConditionManager.unregisterEvents();
            runCount = 0;
        } else {
            stopConditionManager.registerEvents();
            registerPluginStoppingConditions();
            updateAfterRun();
            
        }
    }
    private ZonedDateTime lastRunTime; // When the plugin last ran
    private ZonedDateTime nextRunTime; // When the plugin should next run

    private String cleanName;
    private ConditionManager stopConditionManager;
    private boolean stopInitiated = false;

    private boolean allowRandomScheduling = true; // Whether this plugin can be randomly scheduled
    private int runCount = 0; // Track how many times this plugin has been run

    
    private ZonedDateTime stopInitiatedTime; // When the first stop was attempted
    private ZonedDateTime lastStopAttemptTime; // When the last stop attempt was made
    private Duration softStopRetryInterval = Duration.ofSeconds(10); // Default 10 seconds between retries
    private Duration hardStopTimeout = Duration.ofSeconds(30); // Default 30 seconds before hard stop

    
    private transient Thread stopMonitorThread;
    private transient volatile boolean isMonitoringStop = false;

    // Static formatter for time display
    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm");
    private static final SimpleDateFormat DATE_TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public ScheduledPlugin(String pluginName, ScheduleType scheduleType, int scheduleIntervalValue,
            String duration, boolean enabled, boolean allowRandomScheduling) {
        this.name = pluginName;
        this.scheduleType = scheduleType != null ? scheduleType : ScheduleType.HOURS;
        this.scheduleIntervalValue = Math.max(0, scheduleIntervalValue); // 0 mean only run once
        this.duration = duration;
        this.enabled = enabled;
        this.allowRandomScheduling = allowRandomScheduling;
        this.cleanName = pluginName.replaceAll("<html>|</html>", "")
                .replaceAll("<[^>]*>([^<]*)</[^>]*>", "$1")
                .replaceAll("<[^>]*>", "");

        this.stopConditionManager = new ConditionManager();
        log.info("Scheduled plugin '{}' with interval {} {}", name,
                scheduleIntervalValue, scheduleType.toString().toLowerCase());
        // Set nextRunTime to now by default (run immediately)
        this.nextRunTime = roundToMinutes(ZonedDateTime.now(ZoneId.systemDefault()));
        
        // If duration is specified, add a TimeCondition automatically
        if (duration != null && !duration.isEmpty()) {
            try {
                String[] parts = duration.split(":");
                if (parts.length == 2) {
                    int hours = Integer.parseInt(parts[0]);
                    int minutes = Integer.parseInt(parts[1]);
                    Duration durationObj = Duration.ofHours(hours).plusMinutes(minutes);
                    // Add time condition
                    addCondition(new IntervalCondition(durationObj));
                }
            } catch (Exception e) {
                // Invalid duration format, no condition added
            }
        }
        registerPluginStoppingConditions();
    }

    // For backwards compatibility, add this constructor that defaults to true for
    // allowRandomScheduling
    public ScheduledPlugin(String pluginName, ScheduleType scheduleType, int scheduleIntervalValue,
            String duration, boolean enabled) {
        this(pluginName, scheduleType, scheduleIntervalValue, duration, enabled, true);
    }

    public Plugin getPlugin() {
        if (this.plugin == null) {
            this.plugin = Microbot.getPluginManager().getPlugins().stream()
                    .filter(p -> Objects.equals(p.getName(), name))
                    .findFirst()
                    .orElse(null);
        }
        return plugin;
    }

    public boolean start() {
        if (getPlugin() == null) {
            return false;
        }

        try {
            registerPluginStoppingConditions();
            // Log defined conditions when starting
            logDefinedConditions();
            Microbot.getClientThread().runOnSeperateThread(() -> {
                Microbot.startPlugin(plugin);
                return false;
            });
            stopInitiated = false;
            // Update the last run time and calculate next run
            stopConditionManager.reset();
            stopConditionManager.registerEvents();
            updateAfterRun();

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean softStop() {
        if (getPlugin() == null) {
            return false;
        }

        try {
            stopConditionManager.unregisterEvents();
            Microbot.getClientThread().runOnSeperateThread(() -> {
                ZonedDateTime current_time = ZonedDateTime.now(ZoneId.systemDefault());
                Microbot.getEventBus().post(new ScheduledStopEvent(plugin, current_time));
                return false;
            });
            stopInitiated = true;
            stopInitiatedTime = ZonedDateTime.now();
            lastStopAttemptTime = ZonedDateTime.now();
            
            // Start monitoring for successful stop
            startStopMonitoringThread();
            
            if (getPlugin() instanceof StoppingConditionProvider) {
                log.info("Unregistering stopping conditions for plugin '{}'", name);
            }
            return !isRunning();
        } catch (Exception e) {
            return false;
        }
    }

    public boolean hardStop() {
        if (getPlugin() == null) {
            return false;
        }

        try {
            Microbot.getClientThread().runOnSeperateThread(() -> {
                Microbot.stopPlugin(plugin);
                return false;
            });
            stopInitiated = true;
            stopInitiatedTime = ZonedDateTime.now();
            
            // Start monitoring for successful stop
            startStopMonitoringThread();
            
            return !isRunning();
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isRunning() {
        return getPlugin() != null && Microbot.getPluginManager().isPluginEnabled(plugin);
    }

    /**
     * Round time to nearest minute (remove seconds and milliseconds)
     */
    private ZonedDateTime roundToMinutes(ZonedDateTime time) {
        return time.withSecond(0).withNano(0);
    }

    /**
     * Set when this plugin should next run
     */
    public void setNextRunTime(ZonedDateTime time) {
        this.nextRunTime = roundToMinutes(time);

    }

    /**
     * Check if the plugin is due to run
     */
    public boolean isDueToRun() {
        ZonedDateTime currentTime = roundToMinutes(ZonedDateTime.now(ZoneId.systemDefault()));

        if (!enabled) {
            return false;
        }

        return !currentTime.isBefore(nextRunTime);
    }

    /**
     * Update the lastRunTime to now and calculate the next run time
     */
    private void updateAfterRun() {
        lastRunTime = roundToMinutes(ZonedDateTime.now(ZoneId.systemDefault()));
        
        // For one-time scheduled plugins, don't set a next run time
        if (scheduleIntervalValue == 0) {
            log.debug("One-time plugin {} completed, not scheduling next run", name);
            nextRunTime = null;
            return;
        }
        
        // Calculate next run time based on schedule type and interval
        ZonedDateTime calculatedNextRunTime;
        switch (scheduleType) {
            case MINUTES:
                calculatedNextRunTime = lastRunTime.plusMinutes(scheduleIntervalValue);
                break;
            case HOURS:
                calculatedNextRunTime = lastRunTime.plusHours(scheduleIntervalValue);
                break;
            case DAYS:
                calculatedNextRunTime = lastRunTime.plusDays(scheduleIntervalValue);
                break;
            default:
                calculatedNextRunTime = lastRunTime.plusHours(1); // Default fallback
        }
        
        log.info("Updated next run time for '{}' from {} to {}", 
                name,
                nextRunTime != null ? nextRunTime.format(DATE_TIME_FORMATTER) : "null",
                calculatedNextRunTime.format(DATE_TIME_FORMATTER));
        
        nextRunTime = calculatedNextRunTime;
    }

    /**
     * Get a formatted display of the interval
     */
    public String getIntervalDisplay() {
        return "Every " + scheduleIntervalValue + " " + scheduleType.toString().toLowerCase();
    }

    /**
     * Get a formatted display of when this plugin will run next
     */
    public String getNextRunDisplay() {
        return getNextRunDisplay(System.currentTimeMillis());
    }

   

    /**
     * Get a formatted time string for the next run
     */
    public String getNextRunTimeString() {
        return nextRunTime.format(TIME_FORMATTER);
    }

    /**
     * Get the duration in minutes
     */
    public long getDurationMinutes() {
        if (duration == null || duration.isEmpty()) {
            return 0;
        }

        try {
            String[] parts = duration.split(":");
            if (parts.length == 2) {
                int hours = Integer.parseInt(parts[0]);
                int minutes = Integer.parseInt(parts[1]);
                return hours * 60L + minutes;
            }
        } catch (Exception e) {
            // Fall through to return 0
        }
        return 0;
    }

    
    /**
     * Convert a list of ScheduledPlugin objects to JSON
     */
    public static String toJson(List<ScheduledPlugin> plugins) {
        return ScheduledSerializer.toJson(plugins);
    }


        /**
     * Parse JSON into a list of ScheduledPlugin objects
     */
    public static List<ScheduledPlugin> fromJson(String json) {
        return ScheduledSerializer.fromJson(json);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ScheduledPlugin that = (ScheduledPlugin) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(scheduleType, that.scheduleType) &&
                scheduleIntervalValue == that.scheduleIntervalValue;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, scheduleType, scheduleIntervalValue);
    }

    // Add methods for condition management
    public void addCondition(Condition condition) {
        stopConditionManager.addCondition(condition);
    }

    public List<Condition> getConditions() {
        return stopConditionManager.getConditions();
    }

    // Determine if plugin should stop based on conditions and/or duration
    public boolean shouldStop() {

        // Check if conditions are met and we should stop when conditions are met
        if (areConditionsMet()) {
            return true;
        }

        return false;
    }

    public boolean areConditionsMet() {
        return stopConditionManager.areConditionsMet();
    }

    public String getConditionsDescription() {
        return stopConditionManager.getDescription();
    }

    // Modify the stop logic to check conditions
    public boolean checkConditionsAndStop() {
        ZonedDateTime now = ZonedDateTime.now();
        
        if (shouldStop()) {
            // Initial stop attempt
            if (!stopInitiated) {
                logDefinedConditionWithStates();
                log.info("Stopping plugin {} due to conditions being met - initiating soft stop", name);
                return softStop(); // This will start the monitoring thread
            }
            // Plugin didn't stop after previous attempts
            else if (isRunning()) {
                Duration timeSinceFirstAttempt = Duration.between(stopInitiatedTime, now);
                Duration timeSinceLastAttempt = Duration.between(lastStopAttemptTime, now);
                
                // Force hard stop if we've waited too long
                if (timeSinceFirstAttempt.compareTo(hardStopTimeout) > 0) {
                    log.warn("Plugin {} failed to respond to soft stop after {} seconds - forcing hard stop", 
                             name, timeSinceFirstAttempt.toSeconds());
                    
                    // Stop current monitoring and start new one for hard stop
                    stopMonitoringThread();
                    return hardStop();
                }
                // Retry soft stop at configured intervals
                else if (timeSinceLastAttempt.compareTo(softStopRetryInterval) > 0) {
                    log.info("Plugin {} still running after soft stop - retrying (attempt time: {} seconds)", 
                             name, timeSinceFirstAttempt.toSeconds());
                    lastStopAttemptTime = now;
                    return softStop();
                }
            }
            // Monitor thread will handle the successful stop case
        }
        // Reset stop tracking if conditions no longer require stopping
        else if (stopInitiated) {
            log.info("Plugin {} conditions no longer require stopping - resetting stop state", name);
            stopInitiated = false;
            stopInitiatedTime = null;
            lastStopAttemptTime = null;
            stopMonitoringThread();
        }
        
        return isRunning();
    }

    /**
     * Logs all defined conditions when plugin starts
     */
    private void logDefinedConditions() {
        logConditionInfo("START", true);
    }

    /**
     * Logs which conditions are met and which aren't when plugin stops
     */
    private void logDefinedConditionWithStates() {
        logConditionInfo("STOP", true);
    }

    /**
     * Creates a consolidated log of all condition-related information
     * @param logType The type of log (START, STOP, UPDATE)
     * @param includeDetails Whether to include full details of conditions
     */
    public void logConditionInfo(String logType, boolean includeDetails) {
        List<Condition> conditionList = stopConditionManager.getConditions();
        StringBuilder sb = new StringBuilder();
        
        sb.append("Plugin '").append(cleanName).append("' [").append(logType).append("]: ");

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
        boolean logConditionStatus = "STOP".equals(logType) || "UPDATE".equals(logType);
        int metCount = 0;
        
        for (int i = 0; i < conditionList.size(); i++) {
            Condition condition = conditionList.get(i);
            boolean isSatisfied = condition.isSatisfied();
            if (isSatisfied) metCount++;
            
            // Use the new getStatusInfo method for detailed status
            sb.append("  ").append(i + 1).append(". ")
              .append(condition.getStatusInfo(0, logConditionStatus).replace("\n", "\n    "));
            
            sb.append("\n");
        }
        
        if (logConditionStatus) {
            sb.append("Summary: ").append(metCount).append("/").append(conditionList.size())
              .append(" conditions met");
        }
        
        log.info(sb.toString());
    }

    /**
     * Updates or adds a condition at runtime.
     * This can be used by plugins to dynamically update their stopping conditions.
     * 
     * @param condition The condition to add or update
     * @return This ScheduledPlugin instance for method chaining
     */
    public ScheduledPlugin updateCondition(Condition condition) {
        // Check if we already have a condition of the same type
        boolean found = false;
        for (int i = 0; i < stopConditionManager.getConditions().size(); i++) {
            Condition existing = stopConditionManager.getConditions().get(i);
            if (existing.getClass().equals(condition.getClass())) {
                // Replace the existing condition
                stopConditionManager.getConditions().set(i, condition);
                found = true;
                break;
            }
        }

        // If not found, add it
        if (!found) {
            stopConditionManager.addCondition(condition);
        }

        return this;
    }

    /**
     * Registers any custom stopping conditions provided by the plugin.
     * These conditions are combined with existing conditions using AND logic
     * to ensure plugin-defined conditions have the highest priority.
     * 
     * @param plugin    The plugin that might provide conditions
     * @param scheduled The scheduled instance managing the plugin
     */
    public void registerPluginStoppingConditions() {
        if (this.plugin == null) {
            this.plugin = getPlugin();
        }
        log.info("Registering stopping conditions for plugin '{}'", name);
        if (this.plugin instanceof StoppingConditionProvider) {
            StoppingConditionProvider provider = (StoppingConditionProvider) plugin;

            // Get conditions from the provider
            List<Condition> pluginConditions = provider.getStoppingConditions();
            if (pluginConditions != null && !pluginConditions.isEmpty()) {
                // Create a new AND condition as the root

                AndCondition rootAndCondition = new AndCondition();

                // Get or create plugin's logical structure
                LogicalCondition pluginLogic = provider.getLogicalConditionStructure();

                if (pluginLogic != null) {
                    rootAndCondition.addCondition(pluginLogic);
                } else {
                    // Create a simple AND for plugin conditions
                    AndCondition pluginAndCondition = new AndCondition();
                    for (Condition condition : pluginConditions) {
                        pluginAndCondition.addCondition(condition);
                    }
                    rootAndCondition.addCondition(pluginAndCondition);
                }

                rootAndCondition.reset();
                // Set the new root condition
                getStopConditionManager().setPluginCondition(rootAndCondition);
                
                // Log with the consolidated method
                logConditionInfo("PLUGIN_CONDITIONS", true);
            } else {
                log.info("Plugin '{}' implements StoppingConditionProvider but provided no conditions",
                        plugin.getName());
            }
        }
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

       

        // One-time plugin that has already run
        if (scheduleIntervalValue == 0 && runCount > 0) {
            return "Completed";
        }

        // Check for condition-based execution
        if (isRunning() && !stopConditionManager.getConditions().isEmpty()) {
            double progressPct = getConditionProgress();
            if (progressPct > 0) {
                return String.format("Running Progress %.1f%% complete", progressPct);
            }
        }else{
            if (isRunning()) {
                return "Running";
            }
        }

        // If next run time is null, can't determine
        if (nextRunTime == null) {
            return "Not scheduled";
        }

        // Handle time-based scheduling
        ZonedDateTime currentTime = ZonedDateTime.ofInstant(
                Instant.ofEpochMilli(currentTimeMillis),
                ZoneId.systemDefault());

        // If it's due to run now
        if (!currentTime.isBefore(nextRunTime)) {
            return "Ready to run";
        }

        // Calculate time until next run
        long timeUntilMillis = Duration.between(currentTime, nextRunTime).toMillis();
        long hours = TimeUnit.MILLISECONDS.toHours(timeUntilMillis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(timeUntilMillis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(timeUntilMillis) % 60;
        if (hours > 0) {
            return String.format("In %dh %02dm", hours, minutes);
        } else if (minutes > 0) {
            return String.format("In %02dm %02ds", minutes, seconds);
        } else if (seconds > 0) {
            return String.format("In %ds", seconds);
        } else {
            return "Ready to run";
        }
    }

    /**
     * Calculates overall progress percentage across all conditions.
     * This respects the logical structure of conditions.
     * Returns 0 if progress cannot be determined.
     */
    public double getConditionProgress() {
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
    public int getTotalConditionCount() {
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
    public int getMetConditionCount() {
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

    public void incrementRunCount() {
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
     * Starts a background thread to monitor for successful plugin stops
     * and update the next run time when appropriate.
     */
    private void startStopMonitoringThread() {
        // Don't start a new thread if one is already running
        if (isMonitoringStop) {
            return;
        }
        
        isMonitoringStop = true;
        
        stopMonitorThread = new Thread(() -> {
            try {
                log.debug("Stop monitoring thread started for plugin '{}'", name);
                
                // Keep checking until the stop completes or is abandoned
                while (stopInitiated && isMonitoringStop) {
                    // Check if plugin has stopped running
                    if (!isRunning()) {
                        // Plugin has successfully stopped - update next run time
                        log.info("Plugin '{}' has successfully stopped - updating next run time", name);
                        updateAfterRun();
                        stopInitiated = false;
                        stopInitiatedTime = null;
                        lastStopAttemptTime = null;
                        break;
                    }
                    
                    // Check every 500ms to be responsive but not wasteful
                    Thread.sleep(500);
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
            isMonitoringStop = false;
            stopMonitorThread.interrupt();
            stopMonitorThread = null;
        }
    }

}
