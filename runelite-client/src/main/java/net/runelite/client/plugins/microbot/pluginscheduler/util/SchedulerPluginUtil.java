package net.runelite.client.plugins.microbot.pluginscheduler.util;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.swing.SwingUtilities;

import org.slf4j.event.Level;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.accountselector.AutoLoginPlugin;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerConfig;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerPlugin;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import java.time.format.DateTimeFormatter;

import net.runelite.client.plugins.microbot.pluginscheduler.SchedulerPlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LockCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.model.PluginScheduleEntry;
import net.runelite.client.plugins.microbot.util.antiban.AntibanPlugin;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
@Slf4j
public class SchedulerPluginUtil{
    /**
     * Format a duration for display
     * 
     * @param duration The duration to format
     * @return Formatted string representation (e.g., "1h 30m 15s" or "45s")
     */
    public static String formatDuration(Duration duration) {
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
     * Checks if a specific plugin is enabled
     * 
     * @param pluginClass The class of the plugin to check
     * @return True if the plugin is enabled, false otherwise
     */
    public static boolean isPluginEnabled(Class<? extends Plugin> pluginClass) {
        return Microbot.isPluginEnabled(pluginClass);
    }

    public static boolean isBreakHandlerEnabled() {
        return isPluginEnabled(BreakHandlerPlugin.class);
    }

    public static boolean isAntibanEnabled() {
        return isPluginEnabled(AntibanPlugin.class);
    }

    public static boolean isAutoLoginEnabled() {
        return isPluginEnabled(AutoLoginPlugin.class);
    }

    /**
     * Enables a specific plugin
     * 
     * @param pluginClass The class of the plugin to enable
     * @return true if plugin was enabled successfully, false otherwise
     */
    public static boolean enablePlugin(Class<? extends Plugin> pluginClass) {
        if (isPluginEnabled(pluginClass)) {
            log.info("Plugin {} already enabled", pluginClass.getSimpleName());
            return true; // Already enabled
        }
        
        Microbot.getClientThread().runOnSeperateThread(() -> {
            Plugin plugin = Microbot.getPlugin(pluginClass.getName());
            log.info("Plugin {} suggested to be enabled", pluginClass.getSimpleName());
            if (plugin == null) {
                log.error("Failed to find plugin {}", pluginClass.getSimpleName());
                return false;
            }
            log.info("Plugin {} starting", pluginClass.getSimpleName());
            Microbot.startPlugin(plugin);
            return true;
        });

        log.info("Plugin {} wait", pluginClass.getSimpleName());
        sleepUntil(() -> isPluginEnabled(pluginClass), 500);
        if (!isPluginEnabled(pluginClass)) {
            log.error("Failed to enable plugin {}", pluginClass.getSimpleName());
            return false;
        }
        
        log.info("Plugin {} enabled", pluginClass.getSimpleName());
        return true;
    }

    /**
     * Disables a specific plugin
     * 
     * @param pluginClass The class of the plugin to disable
     * @return true if plugin was disabled successfully, false otherwise
     */
    public static boolean disablePlugin(Class<? extends Plugin> pluginClass) {
        if (!isPluginEnabled(pluginClass)) {
            log.info("Plugin {} already disabled", pluginClass.getSimpleName());
            return true; // Already disabled
        }
        
        log.info("disablePlugin {} - are we on client thread->; {}", 
            pluginClass.getSimpleName(), Microbot.getClient().isClientThread());

        Microbot.getClientThread().runOnSeperateThread(() -> {
            Plugin plugin = Microbot.getPlugin(pluginClass.getName());
            if (plugin == null) {
                log.error("Failed to find plugin {}", pluginClass.getSimpleName());
                return false;
            }
            log.info("Plugin {} stopping", pluginClass.getSimpleName());
            Microbot.stopPlugin(plugin);
            return true;
        });

        if (isPluginEnabled(pluginClass)) {
            SwingUtilities.invokeLater(() -> {
                disablePlugin(pluginClass);
            });
            return false;
        }
        
        log.info("Plugin {} disabled", pluginClass.getSimpleName());
        return true;
    }

    /**
     * Checks if all plugins in a list have the same start trigger time
     * (truncated to millisecond precision for stable comparisons)
     * 
     * @param plugins List of plugins to check
     * @return true if all plugins have the same trigger time
     */
    public static boolean isAllSameTimestamp(List<PluginScheduleEntry> plugins) {
        if (plugins == null || plugins.size() <= 1) {
            return true; // Empty or single-element list has same timestamps by definition
        }

        // Get first plugin's trigger time as reference
        Optional<ZonedDateTime> firstTime = plugins.get(0).getCurrentStartTriggerTime();
        if (firstTime.isEmpty()) {
            // If first plugin has no timestamp, check if all others also have no timestamp
            return plugins.stream().allMatch(p -> p.getCurrentStartTriggerTime().isEmpty());
        }

        // Compare each plugin's trigger time with the first one
        ZonedDateTime reference = firstTime.get().truncatedTo(ChronoUnit.MILLIS);
        return plugins.stream()
                .allMatch(p -> {
                    Optional<ZonedDateTime> time = p.getCurrentStartTriggerTime();
                    return time.isPresent() && 
                           time.get().truncatedTo(ChronoUnit.MILLIS).equals(reference);
                });
    }

    /**
     * Selects a plugin using weighted random selection.
     * Plugins with lower run counts have higher probability of being selected.
     * 
     * @param plugins List of plugins to select from
     * @return The selected plugin
     */
    public static PluginScheduleEntry selectPluginWeighted(List<PluginScheduleEntry> plugins) {
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
    public static List<PluginScheduleEntry> applyWeightedSorting(List<PluginScheduleEntry> plugins) {
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
     * Helper method to enable the BreakHandler plugin
     */
    public static boolean enableBreakHandler() {
        return enablePlugin(BreakHandlerPlugin.class);
    }

    /**
     * Helper method to disable the BreakHandler plugin
     */
    public static boolean disableBreakHandler() {
        if (isPluginEnabled(BreakHandlerPlugin.class)) {
            BreakHandlerScript.setLockState(false); // Ensure we unlock before disabling
        }
        return disablePlugin(BreakHandlerPlugin.class);
    }

    /**
     * Helper method to enable the AutoLogin plugin
     */
    public static boolean enableAutoLogin() {
        return enablePlugin(AutoLoginPlugin.class);
    }

    /**
     * Helper method to enable the AutoLogin plugin with configuration
     *
     * @param randomWorld Whether to use a random world
     * @param worldNumber The world number to use if not random
     * @return true if plugin was enabled successfully, false otherwise
     */
    public static boolean enableAutoLogin(boolean randomWorld, int worldNumber) {
        ConfigManager configManager = Microbot.getConfigManager();
        if(configManager != null) {
            configManager.setConfiguration("AutoLoginConfig", "RandomWorld", randomWorld);
            configManager.setConfiguration("AutoLoginConfig", "World", worldNumber);
        }
        
        return enableAutoLogin();
    }

    /**
     * Helper method to disable the AutoLogin plugin
     */
    public static boolean disableAutoLogin() {
        return disablePlugin(AutoLoginPlugin.class);
    }

    /**
     * Helper method to enable the Antiban plugin
     */
    public static boolean enableAntiban() {
        return enablePlugin(AntibanPlugin.class);
    }

    /**
     * Helper method to disable the Antiban plugin
     */
    public static boolean disableAntiban() {
        return disablePlugin(AntibanPlugin.class);
    }

    /**
     * Checks if the bot is currently on a break
     * 
     * @return true if on break, false otherwise
     */
    public static boolean isOnBreak() {
        // Check if BreakHandler is enabled and on a break
        return isBreakHandlerEnabled() && BreakHandlerScript.isBreakActive();
    }

    /**
     * Forces the bot to take a break immediately if BreakHandler is enabled
     * 
     * @return true if break was initiated, false otherwise
     */
    public static boolean forceBreak() {
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

    /**
     * Attempts to take a micro break if conditions are favorable
     * 
     * @param setState A callback function to update the scheduler state
     * @return true if a micro break was taken, false otherwise
     */
    public static boolean takeMicroBreak(Runnable setState) {
        if (!isAntibanEnabled()) {
            log.warn("Cannot take micro break: Antiban plugin not enabled");
            return false;
        }
        if (Rs2Player.isFullHealth()) {
            if (Rs2Antiban.takeMicroBreakByChance() || BreakHandlerScript.isBreakActive()) {
                if (setState != null) {
                    setState.run();
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Locks the break handler to prevent breaks from occurring
     * 
     * @return true if the break handler was successfully locked, false otherwise
     */
    public static boolean lockBreakHandler() {
        // Check if BreakHandler is enabled and not already locked
        if (isBreakHandlerEnabled() && !BreakHandlerScript.isBreakActive() && !BreakHandlerScript.isLockState()) {
            BreakHandlerScript.setLockState(true);
            return true; // Successfully locked
        }
        return false; // Failed to lock
    }

    /**
     * Unlocks the break handler to allow breaks to occur
     */
    public static void unlockBreakHandler() {
        // Check if BreakHandler is enabled and not already unlocked
        if (isBreakHandlerEnabled() && BreakHandlerScript.isLockState()) {
            BreakHandlerScript.setLockState(false);
        }
    }

    /**
     * Logs out the player if they are currently logged in
     */
    public static void logout() {
        if (Microbot.getClient().getGameState() == net.runelite.api.GameState.LOGGED_IN) {
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





    /**
     * Sorts a list of plugins according to a consistent order, with weighted
     * selection for randomizable plugins:
     * 1. Enabled plugins first
     * 2. Running status (running plugins first)
     * 3. Due-to-run status (due plugins first) - prioritizes actionable plugins
     * 4. Next run time (earliest first) - within due/not-due groups
     * 5. Priority (highest first) - within due/runtime groups  
     * 6. Non-default status (non-default first)
     * 7. Prefer non-randomizable plugins (for ties in timing)
     * 8. For randomizable plugins with equal criteria: weighted by run count
     * 9. Finally by name and object identity for stable ordering
     * 
     * @param plugins                The list of plugins to sort
     * @param applyWeightedSelection Whether to apply weighted selection for
     *                               randomizable plugins
     * @return A sorted copy of the input list
     */
    public static List<PluginScheduleEntry> sortPluginScheduleEntries(List<PluginScheduleEntry> plugins,
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

            // Sort by due-to-run status first (due plugins first)
            boolean p1IsDue = p1.isDueToRun();
            boolean p2IsDue = p2.isDueToRun();

            if (p1IsDue != p2IsDue) {
                return p1IsDue ? -1 : 1;
            }

            // Then sort by next run time (earliest first) - within due/not-due groups
            Optional<ZonedDateTime> time1 = p1.getCurrentStartTriggerTime();
            Optional<ZonedDateTime> time2 = p2.getCurrentStartTriggerTime();

            if (time1.isPresent() && time2.isPresent()) {
                ZonedDateTime t1 = time1.get().truncatedTo(ChronoUnit.SECONDS);
                ZonedDateTime t2 = time2.get().truncatedTo(ChronoUnit.SECONDS);
                int timeCompare = t1.compareTo(t2);
                float timeDifference = Duration.between(t1, t2).toMillis();
                int priorityCompare = Integer.compare(p2.getPriority(), p1.getPriority());
                if (timeCompare != 0 && priorityCompare == 0) {
                    log.debug("Comparing times: {}() vs {}() -> result: {} ({} ms difference)", 
                        t1.format(DateTimeFormatter.ISO_ZONED_DATE_TIME), 
                        t2.format(DateTimeFormatter.ISO_ZONED_DATE_TIME), 
                        timeCompare
                        , timeDifference);
                    return timeCompare;
                }
            } else if (time1.isPresent()) {
                return -1; // p1 has time, p2 doesn't
            } else if (time2.isPresent()) {
                return 1; // p2 has time, p1 doesn't
            }

            // Then sort by priority within due/runtime groups (highest first)
            int priorityCompare = Integer.compare(p2.getPriority(), p1.getPriority());
            if (priorityCompare != 0) {
                return priorityCompare;
            }

            // Prefer non-default plugins
            if (p1.isDefault() != p2.isDefault()) {
                return p1.isDefault() ? 1 : -1;
            }

            // Prefer non-randomizable plugins for deterministic behavior
            if (p1.isAllowRandomScheduling() != p2.isAllowRandomScheduling()) {
                return p1.isAllowRandomScheduling() ? 1 : -1;
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

        // Now we need to look for groups of randomizable plugins at the same priority,
        // default status, and similar timing for weighted selection
        List<PluginScheduleEntry> result = new ArrayList<>();
        List<PluginScheduleEntry> randomizableGroup = new ArrayList<>();
        Integer currentPriority = null;
        boolean currentDefault = false;
        ZonedDateTime currentTimeGroup = null;
        final Duration TIME_GROUP_WINDOW = Duration.ofMinutes(5); // Group plugins within 5 minutes

        // Iterate through sorted plugins to find groups with the same priority,
        // default status, and similar timing
        for (int i = 0; i < sortedPlugins.size(); i++) {
            PluginScheduleEntry current = sortedPlugins.get(i);

            // Skip non-randomizable plugins (they're already properly sorted by time)
            if (!current.isAllowRandomScheduling()) {
                // If we had a randomizable group, process it before adding this
                // non-randomizable plugin
                if (!randomizableGroup.isEmpty()) {
                    result.addAll(SchedulerPluginUtil.applyWeightedSorting(randomizableGroup));
                    randomizableGroup.clear();
                }

                result.add(current);
                continue;
            }

            // Get the trigger time for timing group comparison
            Optional<ZonedDateTime> triggerTime = current.getCurrentStartTriggerTime();
            ZonedDateTime currentTime = triggerTime.map(t -> t.truncatedTo(ChronoUnit.MINUTES)).orElse(null);

            // Check if this is part of an existing group (same priority, default status, and timing)
            boolean sameGroup = currentPriority != null
                    && current.getPriority() == currentPriority
                    && current.isDefault() == currentDefault;

            // Add timing group check - plugins should be in same time window for randomization
            if (sameGroup && currentTimeGroup != null && currentTime != null) {
                Duration timeDifference = Duration.between(currentTimeGroup, currentTime).abs();
                sameGroup = timeDifference.compareTo(TIME_GROUP_WINDOW) <= 0;
            } else if (sameGroup) {
                // If one has time and other doesn't, they're not in the same group
                sameGroup = (currentTimeGroup == null && currentTime == null);
            }

            if (sameGroup) {
                // Same group, add to current batch of randomizable plugins
                randomizableGroup.add(current);
            } else {
                // New group - process previous group if it exists
                if (!randomizableGroup.isEmpty()) {
                    result.addAll(SchedulerPluginUtil.applyWeightedSorting(randomizableGroup));
                    randomizableGroup.clear();
                }

                // Start new group
                randomizableGroup.add(current);
                currentPriority = current.getPriority();
                currentDefault = current.isDefault();
                currentTimeGroup = currentTime;
            }
        }

        // Process any remaining group
        if (!randomizableGroup.isEmpty()) {
            result.addAll(SchedulerPluginUtil.applyWeightedSorting(randomizableGroup));
        }

        return result;
    }



    /**
     * Overloaded method that calls sortPluginScheduleEntries without weighted
     * selection by default
     */
    public static List<PluginScheduleEntry> sortPluginScheduleEntries(List<PluginScheduleEntry> plugins) {
        return sortPluginScheduleEntries(plugins, false);
    }

  

    public static  Optional<Duration> getScheduleInterval(PluginScheduleEntry plugin) {
        if (plugin.hasAnyStartConditions()) {
            Optional<ZonedDateTime> nextTrigger = plugin.getCurrentStartTriggerTime();
            if (nextTrigger.isPresent()) {
                ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
                return Optional.of(Duration.between(now, nextTrigger.get()));
            }
        }
        return Optional.empty();
    }



    /**
     * Formats a reason message for better readability by splitting it into multiple lines
     * if it's too long or contains natural break points.
     * 
     * @param reason The original reason message
     * @return A formatted reason message with appropriate line breaks
     */
    public static String formatReasonMessage(String reason) {
        if (reason == null || reason.isEmpty()) {
            return "";
        }

        // Maximum line length before seeking a break point
        final int MAX_LINE_LENGTH = 80;
        
        // If the reason is already short, return it as is
        if (reason.length() <= MAX_LINE_LENGTH) {
            return reason;
        }

        StringBuilder formatted = new StringBuilder();
        
        // First check if the message already contains a colon
        // If so, we'll format differently
        if (reason.contains(":")) {
            String[] parts = reason.split(":", 2);
            formatted.append(parts[0].trim()).append(":");
            
            // Process the part after the colon
            String afterColon = parts[1].trim();
            
            // If what comes after the colon is still long, format it further
            if (afterColon.length() > MAX_LINE_LENGTH) {
                formatted.append("\n    "); // Indent the continuation
                formatted.append(formatLongText(afterColon, MAX_LINE_LENGTH));
            } else {
                formatted.append("\n    ").append(afterColon);
            }
        } else {
            // No colon in the message, format as regular long text
            formatted.append(formatLongText(reason, MAX_LINE_LENGTH));
        }
        
        return formatted.toString();
    }
    
    /**
     * Helper method to format a long text by inserting line breaks at natural points
     * 
     * @param text The text to format
     * @param maxLineLength The maximum length for each line
     * @return Formatted text with line breaks
     */
    private static String formatLongText(String text, int maxLineLength) {
        if (text == null || text.isEmpty() || text.length() <= maxLineLength) {
            return text;
        }
        
        StringBuilder result = new StringBuilder();
        int currentPosition = 0;
        
        while (currentPosition < text.length()) {
            int endPosition = Math.min(currentPosition + maxLineLength, text.length());
            
            // If we're not at the end of the text, look for a natural break point
            if (endPosition < text.length()) {
                // Look for natural break points: period, comma, space, etc.
                int breakPoint = findBreakPoint(text, currentPosition, endPosition);
                if (breakPoint > currentPosition) {
                    endPosition = breakPoint;
                }
            }
            
            // Add the current segment
            if (result.length() > 0) {
                result.append("\n    "); // Indent continuation lines
            }
            result.append(text.substring(currentPosition, endPosition).trim());
            
            // Move to next segment
            currentPosition = endPosition;
        }
        
        return result.toString();
    }
    
    /**
     * Finds a suitable break point in text between start and end positions
     * 
     * @param text The text to analyze
     * @param start Start position to search from
     * @param end End position to search to
     * @return Position of a good break point, or end if none found
     */
    private static int findBreakPoint(String text, int start, int end) {
        // Search backward from the end position for a good break point
        for (int i = end; i > start; i--) {
            char c = text.charAt(i);
            
            // Good break points in priority order
            if (c == '.' || c == '!' || c == '?') {
                return i + 1; // Break after sentence-ending punctuation
            } else if (c == ';' || c == ':') {
                return i + 1; // Break after semicolons or colons
            } else if (c == ',') {
                return i + 1; // Break after commas
            } else if (Character.isWhitespace(c)) {
                return i + 1; // Break after whitespace
            }
        }
        
        // If no good break point found, just use the end position
        return end;
    }

    /**
     * Logs detailed information about the sorted plugin list for debugging table ordering.
     * This shows the order plugins appear in the schedule table and explains the sorting criteria.
     * 
     * @param sortedPlugins The sorted list of plugins as they appear in the table
     */
    public static void logPluginScheduleEntryList(List<PluginScheduleEntry> sortedPlugins) {
        StringBuilder tableOrderLog = new StringBuilder();
        tableOrderLog.append("\n=== SCHEDULE TABLE ORDERING DEBUG ===\n");
        tableOrderLog.append("Plugins are sorted by priority order:\n");
        tableOrderLog.append("1. Enabled status (enabled first)\n");
        tableOrderLog.append("2. Running status (running first)\n");
        tableOrderLog.append("3. Due-to-run status (due first)\n");
        tableOrderLog.append("4. Next run time (earliest first)\n");
        tableOrderLog.append("5. Priority level (highest first)\n");
        tableOrderLog.append("6. Default status (non-default first)\n");
        tableOrderLog.append("7. Random scheduling (non-random first)\n");
        tableOrderLog.append("8. Plugin name (alphabetical)\n");
        tableOrderLog.append("9. Object identity (stable ordering)\n\n");
        tableOrderLog.append("Total plugins: ").append(sortedPlugins.size()).append("\n\n");

        for (int i = 0; i < sortedPlugins.size(); i++) {
            PluginScheduleEntry plugin = sortedPlugins.get(i);
            tableOrderLog.append(String.format("Row %d: %s\n", i, plugin.getCleanName()));
            
            // Priority information
            tableOrderLog.append(String.format("  Priority: %d %s\n", 
                plugin.getPriority(), 
                plugin.isDefault() ? "(DEFAULT - always priority 0)" : ""));
            
            // Status information
            tableOrderLog.append(String.format("  Status: %s%s%s\n",
                plugin.isEnabled() ? "ENABLED" : "DISABLED",
                plugin.isRunning() ? " | RUNNING" : "",
                plugin.isStopInitiated() ? " | STOPPING" : ""));
            
            // Next schedule time
            Optional<ZonedDateTime> nextTime = plugin.getCurrentStartTriggerTime();
            if (nextTime.isPresent()) {
                tableOrderLog.append(String.format("  Next Run: %s\n", 
                    nextTime.get().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
            } else {
                tableOrderLog.append("  Next Run: No trigger time available\n");
            }
            
            // Condition information
            boolean hasStartConditions = plugin.hasAnyStartConditions();
            boolean hasStopConditions = plugin.hasAnyStopConditions();
            tableOrderLog.append(String.format("  Conditions: Start=%s, Stop=%s\n",
                hasStartConditions ? String.format("%d conditions", plugin.getStartConditionManager().getConditions().size()) : "None",
                hasStopConditions ? String.format("%d conditions", plugin.getStopConditionManager().getConditions().size()) : "None"));
            
            // Start condition readiness
            if (hasStartConditions) {
                boolean canStart = plugin.canBeStarted();
                boolean isDue = plugin.isDueToRun();
                tableOrderLog.append(String.format("  Start Ready: %s | Due to Run: %s\n", 
                    canStart ? "YES" : "NO", 
                    isDue ? "YES" : "NO"));
            }
            
            // Plugin properties
            tableOrderLog.append(String.format("  Properties: RandomScheduling=%s, AllowContinue=%s, RunCount=%d\n",
                plugin.isAllowRandomScheduling() ? "YES" : "NO",
                plugin.isAllowContinue() ? "YES" : "NO",
                plugin.getRunCount()));
            
            // One-time schedule information
            if (plugin.hasAnyOneTimeStartConditions()) {
                boolean hasTriggered = plugin.hasTriggeredOneTimeStartConditions();
                boolean canTriggerAgain = plugin.canStartTriggerAgain();
                tableOrderLog.append(String.format("  One-Time: HasTriggered=%s, CanTriggerAgain=%s\n",
                    hasTriggered ? "YES" : "NO",
                    canTriggerAgain ? "YES" : "NO"));
            }
            
            // Last run information
            if (plugin.getRunCount() > 0) {
                tableOrderLog.append(String.format("  Last Run: %s (%s)\n",
                    plugin.getLastStopReasonType().getDescription(),
                    plugin.isLastRunSuccessful() ? "SUCCESS" : "FAILED"));
            }
            
            tableOrderLog.append("\n");
        }

      

        log.info(tableOrderLog.toString());
    }


     /**
     * Detects if any enabled SchedulablePlugin has locked LockConditions.
     * This prevents the break handler from taking breaks during critical plugin operations.
     * 
     * @return true if any schedulable plugin has locked conditions, false otherwise
     */
    public static boolean hasLockedSchedulablePlugins() {
        try {
            // Get all enabled plugins from the plugin manager
            return Microbot.getPluginManager().getPlugins().stream()
                .filter(plugin -> Microbot.getPluginManager().isPluginEnabled(plugin))
                .filter(plugin -> plugin instanceof net.runelite.client.plugins.microbot.pluginscheduler.api.SchedulablePlugin)
                .map(plugin -> (net.runelite.client.plugins.microbot.pluginscheduler.api.SchedulablePlugin) plugin)
                .anyMatch(schedulablePlugin -> {
                    try {
                        // Get the stop condition from the schedulable plugin
                        LogicalCondition stopCondition = schedulablePlugin.getStopCondition();
                        if (stopCondition != null) {
                            // Find all LockConditions in the logical condition structure using the utility method
                            List<LockCondition> lockConditions = stopCondition.findAllLockConditions();
                            // Check if any LockCondition is currently locked
                            return lockConditions.stream().anyMatch(LockCondition::isLocked);
                        }
                        return false;
                    } catch (Exception e) {
                        Microbot.log("Error checking stop conditions for schedulable plugin - " + e.getMessage());
                        return false;
                    }
                });
        } catch (Exception e) {
            Microbot.log("Error checking schedulable plugins for lock conditions: " + e.getMessage());
            return false;
        }
    }

    /**
     * Gets the time until the next scheduled plugin will run.
     * This method checks the SchedulerPlugin for the upcoming plugin and calculates
     * the duration until it's scheduled to execute.
     * 
     * @return Optional containing the duration until the next plugin runs, 
     *         or empty if no plugin is upcoming or time cannot be determined
     */
    public static Optional<Duration> getTimeUntilUpComingScheduledPlugin() {
        try {
            // Get the SchedulerPlugin instance
            SchedulerPlugin schedulerPlugin = (SchedulerPlugin) Microbot.getPlugin(SchedulerPlugin.class.getName());
            
            // Check if scheduler plugin exists and is running
            if (schedulerPlugin == null) {
                Microbot.log("SchedulerPlugin is not loaded, cannot determine next plugin time", Level.DEBUG);
                return Optional.empty();
            }
            
            // Check if the scheduler is in an active state
            if (!schedulerPlugin.getCurrentState().isSchedulerActive()) {
                Microbot.log("SchedulerPlugin is not in active state: " + schedulerPlugin.getCurrentState(), Level.DEBUG);
                return Optional.empty();
            }
            
            // Get the upcoming plugin
            PluginScheduleEntry upcomingPlugin = schedulerPlugin.getUpComingPlugin();
            if (upcomingPlugin == null) {
                Microbot.log("No upcoming plugin found in scheduler", Level.DEBUG);
                return Optional.empty();
            }
            
            // Get the time until the next run for this plugin
            Optional<Duration> timeUntilRun = upcomingPlugin.getTimeUntilNextRun();
            if (!timeUntilRun.isPresent()) {
                Microbot.log("Cannot determine time until next run for plugin: " + upcomingPlugin.getCleanName(), Level.DEBUG);
                return Optional.empty();
            }
            
            Duration duration = timeUntilRun.get();
            
            // Log the result for debugging
            Microbot.log("Next plugin '" + upcomingPlugin.getCleanName() + "' scheduled in: " + 
                        formatDuration(duration), Level.DEBUG);
            
            return Optional.of(duration);
            
        } catch (Exception e) {
            Microbot.log("Error getting time until next scheduled plugin: " + e.getMessage(), Level.ERROR);
            return Optional.empty();
        }
    }
    
    /**
     * Gets information about the next scheduled plugin.
     * This method provides both the plugin entry and the time until it runs.
     * 
     * @return Optional containing a formatted string with plugin name and time until run,
     *         or empty if no plugin is upcoming
     */
    public static Optional<String> getUpComingScheduledPluginInfo() {
        try {
            SchedulerPlugin schedulerPlugin = (SchedulerPlugin) Microbot.getPlugin(SchedulerPlugin.class.getName());
            
            if (schedulerPlugin == null || !schedulerPlugin.getCurrentState().isSchedulerActive()) {
                return Optional.empty();
            }
            
            PluginScheduleEntry upcomingPlugin = schedulerPlugin.getUpComingPlugin();
            if (upcomingPlugin == null) {
                return Optional.empty();
            }
            
            Optional<Duration> timeUntilRun = upcomingPlugin.getTimeUntilNextRun();
            if (!timeUntilRun.isPresent()) {
                return Optional.of("Next plugin: " + upcomingPlugin.getCleanName() + " (time unknown)");
            }
            
            String formattedTime = formatDuration(timeUntilRun.get());
            return Optional.of("Next plugin: " + upcomingPlugin.getCleanName() + " in " + formattedTime);
            
        } catch (Exception e) {
            Microbot.log("Error getting next scheduled plugin info: " + e.getMessage(), Level.ERROR);
            return Optional.empty();
        }
    }
    
    /**
     * Gets the next scheduled plugin entry with its complete information.
     * This provides access to the full PluginScheduleEntry object.
     * 
     * @return Optional containing the next scheduled plugin entry,
     *         or empty if no plugin is upcoming
     */
    public static Optional<PluginScheduleEntry> getNextUpComingPluginScheduleEntry() {
        try {
            SchedulerPlugin schedulerPlugin = (SchedulerPlugin) Microbot.getPlugin(SchedulerPlugin.class.getName());
            
            if (schedulerPlugin == null || !schedulerPlugin.getCurrentState().isSchedulerActive()) {
                return Optional.empty();
            }
            
            PluginScheduleEntry upcomingPlugin = schedulerPlugin.getUpComingPlugin();
            return Optional.ofNullable(upcomingPlugin);
            
        } catch (Exception e) {
            Microbot.log("Error getting next scheduled plugin entry: " + e.getMessage(), Level.ERROR);
            return Optional.empty();
        }
    }
    
    /**
     * Gets the estimated time until the next scheduled plugin will be ready to run.
     * This method uses the enhanced estimation system to provide more accurate
     * predictions by considering both current plugin stop conditions and upcoming
     * plugin start conditions.
     * 
     * @return Optional containing the estimated duration until the next plugin runs, 
     *         or empty if no plugin is upcoming or time cannot be determined
     */
    public static Optional<Duration> getEstimatedTimeUntilNextScheduledPlugin() {
        try {
            // Get the SchedulerPlugin instance
            SchedulerPlugin schedulerPlugin = (SchedulerPlugin) Microbot.getPlugin(SchedulerPlugin.class.getName());
            
            // Check if scheduler plugin exists and is running
            if (schedulerPlugin == null) {
                Microbot.log("SchedulerPlugin is not loaded, cannot determine estimated next plugin time", Level.DEBUG);
                return Optional.empty();
            }
            
            // Check if the scheduler is in an active state
            if (!schedulerPlugin.getCurrentState().isSchedulerActive()) {
                Microbot.log("SchedulerPlugin is not in active state: " + schedulerPlugin.getCurrentState(), Level.DEBUG);
                return Optional.empty();
            }
            
            // Get the estimated schedule time using the new system
            Optional<Duration> estimatedTime = schedulerPlugin.getUpComingEstimatedScheduleTime();
            
            if (estimatedTime.isPresent()) {
                Duration duration = estimatedTime.get();
                
                // Log the result for debugging
                Microbot.log("Next plugin estimated to be scheduled in: " + 
                            formatDuration(duration), Level.DEBUG);
                
                return Optional.of(duration);
            } else {
                Microbot.log("Cannot estimate time until next scheduled plugin", Level.DEBUG);
                return Optional.empty();
            }
            
        } catch (Exception e) {
            Microbot.log("Error getting estimated time until next scheduled plugin: " + e.getMessage(), Level.ERROR);
            return Optional.empty();
        }
    }
    
    /**
     * Gets enhanced information about the next scheduled plugin using the estimation system.
     * This method provides more accurate predictions by considering both current plugin
     * stop conditions and upcoming plugin start conditions.
     * 
     * @return Optional containing a formatted string with plugin name and estimated time until run,
     *         or empty if no plugin is upcoming
     */
    public static Optional<String> getNextScheduledPluginInfoWithEstimation() {
        try {
            SchedulerPlugin schedulerPlugin = (SchedulerPlugin) Microbot.getPlugin(SchedulerPlugin.class.getName());
            
            if (schedulerPlugin == null || !schedulerPlugin.getCurrentState().isSchedulerActive()) {
                return Optional.empty();
            }
            
            PluginScheduleEntry upcomingPlugin = schedulerPlugin.getUpComingPlugin();
            if (upcomingPlugin == null) {
                return Optional.empty();
            }
            
            // Use the enhanced estimation system
            Optional<Duration> estimatedTime = schedulerPlugin.getUpComingEstimatedScheduleTime();
            if (!estimatedTime.isPresent()) {
                return Optional.of("Next plugin: " + upcomingPlugin.getCleanName() + " (estimation unavailable)");
            }
            
            String formattedTime = formatDuration(estimatedTime.get());
            return Optional.of("Next plugin: " + upcomingPlugin.getCleanName() + " estimated in " + formattedTime);
            
        } catch (Exception e) {
            Microbot.log("Error getting next scheduled plugin info with estimation: " + e.getMessage(), Level.ERROR);
            return Optional.empty();
        }
    }
    
    /**
     * Gets enhanced information about the next scheduled plugin within a time window.
     * This method provides predictions for plugins that will be ready within the specified timeframe.
     * 
     * @param timeWindow The time window to look ahead for upcoming plugins
     * @return Optional containing a formatted string with plugin name and estimated time until run,
     *         or empty if no plugin is upcoming within the window
     */
    public static Optional<String> getNextScheduledPluginInfoWithinTimeWindow(Duration timeWindow) {
        try {
            SchedulerPlugin schedulerPlugin = (SchedulerPlugin) Microbot.getPlugin(SchedulerPlugin.class.getName());
            
            if (schedulerPlugin == null || !schedulerPlugin.getCurrentState().isSchedulerActive()) {
                return Optional.empty();
            }
            
            // Get plugin within the time window
            PluginScheduleEntry upcomingPlugin = schedulerPlugin.getUpComingPluginWithinTime(timeWindow);
            if (upcomingPlugin == null) {
                return Optional.empty();
            }
            
            // Use the enhanced estimation system for the time window
            Optional<Duration> estimatedTime = schedulerPlugin.getUpComingEstimatedScheduleTimeWithinTime(timeWindow);
            if (!estimatedTime.isPresent()) {
                return Optional.of("Next plugin within " + formatDuration(timeWindow) + ": " + 
                                 upcomingPlugin.getCleanName() + " (estimation unavailable)");
            }
            
            String formattedTime = formatDuration(estimatedTime.get());
            return Optional.of("Next plugin within " + formatDuration(timeWindow) + ": " + 
                             upcomingPlugin.getCleanName() + " estimated in " + formattedTime);
            
        } catch (Exception e) {
            Microbot.log("Error getting next scheduled plugin info within time window: " + e.getMessage(), Level.ERROR);
            return Optional.empty();
        }
    }
    
}