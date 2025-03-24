package net.runelite.client.plugins.microbot.pluginscheduler.type;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.runelite.api.Skill;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ConditionManager;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ConditionType;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.LootItemCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.NotCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.OrCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.SkillLevelCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.SkillXpCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.TimeCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.event.ScheduledStopEvent;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class Scheduled {
    private transient Plugin plugin;
    private String name;
    private ScheduleType scheduleType;
    private int intervalValue; // The numeric value for the interval
    private String duration; // Optional duration to run the plugin
    private boolean enabled;
    private ZonedDateTime lastRunTime; // When the plugin last ran
    private ZonedDateTime nextRunTime; // When the plugin should next run
    private ZonedDateTime scheduledStopDateTime; // When the plugin is scheduled to stop
    private String cleanName;
    private ConditionManager conditionManager;
    private boolean stopOnConditionsMet;
    // Static formatter for time display
    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm");
    private static final SimpleDateFormat DATE_TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public Scheduled(String pluginName, ScheduleType scheduleType, int intervalValue,
                     String duration, boolean enabled) {
        this.name = pluginName;
        this.scheduleType = scheduleType != null ? scheduleType : ScheduleType.HOURS;
        this.intervalValue = Math.max(1, intervalValue); // Ensure interval is at least 1
        this.duration = duration;
        this.enabled = enabled;
        this.cleanName = pluginName.replaceAll("<html>|</html>", "")
                .replaceAll("<[^>]*>([^<]*)</[^>]*>", "$1")
                .replaceAll("<[^>]*>", "");
        
        this.conditionManager = new ConditionManager();
        this.stopOnConditionsMet = true;
                
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
                    addCondition(TimeCondition.fromDuration(durationObj));
                }
            } catch (Exception e) {
                // Invalid duration format, no condition added
            }
        }
    }
    /**
     * Creates a scheduled plugin based primarily on conditions rather than time intervals.
     */
    public static Scheduled createConditionBased(String pluginName, boolean enabled) {
        Scheduled plugin = new Scheduled(pluginName, ScheduleType.HOURS, 24, "", enabled);
        // We set a default long interval since the conditions will likely trigger before this
        return plugin;
    }
    public Plugin getPlugin() {
        if (plugin == null) {
            plugin = Microbot.getPluginManager().getPlugins().stream()
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
            calculateScheduledStopDateTime();
            Microbot.getClientThread().runOnSeperateThread(() -> {
                Microbot.startPlugin(plugin);
                return false;
            });

            // Update the last run time and calculate next run
            updateAfterRun();

            return true;
        } catch (Exception e) {
            scheduledStopDateTime = null;
            return false;
        }
    }

    public boolean stop() {
        if (getPlugin() == null) {
            return false;
        }
    
        try {
            conditionManager.unregisterEvents();
            Microbot.getClientThread().runOnSeperateThread(() -> {
                Microbot.getEventBus().post(new ScheduledStopEvent(plugin, scheduledStopDateTime));
                return false;
            });
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean forceStop() {
        if (getPlugin() == null) {
            return false;
        }

        try {
            Microbot.getClientThread().runOnSeperateThread(() -> {
                Microbot.stopPlugin(plugin);
                return false;
            });
            return true;
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
    public void updateAfterRun() {
        lastRunTime = roundToMinutes(ZonedDateTime.now(ZoneId.systemDefault()));

        // Calculate next run time based on schedule type and interval
        switch (scheduleType) {
            case MINUTES:
                nextRunTime = lastRunTime.plusMinutes(intervalValue);
                break;
            case HOURS:
                nextRunTime = lastRunTime.plusHours(intervalValue);
                break;
            case DAYS:
                nextRunTime = lastRunTime.plusDays(intervalValue);
                break;
            default:
                nextRunTime = lastRunTime.plusHours(1); // Default fallback
        }
        scheduledStopDateTime = null;
    }


    /**
     * Get a formatted display of the interval
     */
    public String getIntervalDisplay() {
        return "Every " + intervalValue + " " + scheduleType.toString().toLowerCase();
    }


    /**
     * Get a formatted display of when this plugin will run next
     */
    public String getNextRunDisplay() {
        ZonedDateTime currentTime = roundToMinutes(ZonedDateTime.now(ZoneId.systemDefault()));

        if (!enabled) {
            return "Disabled";
        }

        if (isRunning()) {
            return "Running";
        }

        if (!currentTime.isBefore(nextRunTime)) {
            return "Ready to run";
        }

        long timeUntilMillis = java.time.Duration.between(currentTime, nextRunTime).toMillis();
        long hours = TimeUnit.MILLISECONDS.toHours(timeUntilMillis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(timeUntilMillis) % 60;

        if (hours > 0) {
            return String.format("In %dh %dm", hours, minutes);
        } else {
            return String.format("In %dm", minutes);
        }
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
    public static String toJson(List<Scheduled> plugins) {
        Gson gson = new GsonBuilder()
            .registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeAdapter())
            .create();
        return gson.toJson(plugins);
    }

    /**
     * Parse JSON into a list of ScheduledPlugin objects
     */
    public static List<Scheduled> fromJson(String json) {
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            Gson gson = new GsonBuilder()
                .registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeAdapter())
                .create();
            Type listType = new TypeToken<ArrayList<Scheduled>>() {}.getType();
            List<Scheduled> plugins = gson.fromJson(json, listType);

            // Fix any null scheduleType values
            for (Scheduled plugin : plugins) {
                if (plugin.getScheduleType() == null) {
                    plugin.scheduleType = ScheduleType.HOURS; // Default to HOURS
                }
            }

            return plugins;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Scheduled that = (Scheduled) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(scheduleType, that.scheduleType) &&
                intervalValue == that.intervalValue;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, scheduleType, intervalValue);
    }

        /**
     * Calculate and set the stop time based on the duration
     */
    public void calculateScheduledStopDateTime() {
        if (duration == null || duration.isEmpty()) {
            this.scheduledStopDateTime = null;
            return;
        }
        
        long durationMinutes = getDurationMinutes();
        if (durationMinutes > 0) {
            ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
            this.scheduledStopDateTime = now.plusMinutes(durationMinutes);
        } else {
            this.scheduledStopDateTime = null;
        }
    }

    // Add methods for condition management
    public void addCondition(Condition condition) {
        conditionManager.addCondition(condition);
    }

    public List<Condition> getConditions() {
        return conditionManager.getConditions();
    }

    public boolean shouldStopOnConditionsMet() {
        return stopOnConditionsMet;
    }

    public void setStopOnConditionsMet(boolean stopOnConditionsMet) {
        this.stopOnConditionsMet = stopOnConditionsMet;
    }

    public boolean areConditionsMet() {
        return conditionManager.areConditionsMet();
    }

    public String getConditionsDescription() {
        return conditionManager.getDescription();
    }

    // Modify the stop logic to check conditions
    public boolean checkConditionsAndStop() {
        if (stopOnConditionsMet && areConditionsMet()) {
            return stop();
        }
        return false;
    }
    /**
     * Updates or adds a condition at runtime.
     * This can be used by plugins to dynamically update their stopping conditions.
     * 
     * @param condition The condition to add or update
     * @return This Scheduled instance for method chaining
     */
    public Scheduled updateCondition(Condition condition) {
        // Check if we already have a condition of the same type
        boolean found = false;
        for (int i = 0; i < conditionManager.getConditions().size(); i++) {
            Condition existing = conditionManager.getConditions().get(i);
            if (existing.getClass().equals(condition.getClass())) {
                // Replace the existing condition
                conditionManager.getConditions().set(i, condition);
                found = true;
                break;
            }
        }
        
        // If not found, add it
        if (!found) {
            conditionManager.addCondition(condition);
        }
        
        return this;
    }
    public List<Map<String, Object>> serializeConditions() {
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (Condition condition : conditionManager.getConditions()) {
            Map<String, Object> conditionMap = new HashMap<>();
            conditionMap.put("type", condition.getType().getIdentifier());
            
            // Add type-specific properties
            if (condition instanceof TimeCondition) {
                TimeCondition timeCondition = (TimeCondition) condition;
                conditionMap.put("endTime", timeCondition.getEndTime().toInstant().toEpochMilli());
            } 
            else if (condition instanceof SkillLevelCondition) {
                SkillLevelCondition skillCondition = (SkillLevelCondition) condition;
                conditionMap.put("skill", skillCondition.getSkill().getName());
                conditionMap.put("targetLevel", skillCondition.getTargetLevel());
            }
            else if (condition instanceof SkillXpCondition) {
                SkillXpCondition xpCondition = (SkillXpCondition) condition;
                conditionMap.put("skill", xpCondition.getSkill().getName());
                conditionMap.put("targetXp", xpCondition.getTargetXp());
            }
            else if (condition instanceof LootItemCondition) {
                LootItemCondition itemCondition = (LootItemCondition) condition;
                conditionMap.put("itemName", itemCondition.getItemName());
                conditionMap.put("targetAmount", itemCondition.getTargetAmount());
                // Store current tracking state for persistence
                conditionMap.put("currentTrackedCount", itemCondition.getCurrentTrackedCount());
                conditionMap.put("lastInventoryCount", itemCondition.getLastInventoryCount());
                conditionMap.put("lastBankedCount", itemCondition.getLastBankedCount());
                conditionMap.put("startTime", itemCondition.getStartTime().toEpochMilli());
            }
            
            result.add(conditionMap);
        }
        
        return result;
    }
    public Map<String, Object> serializeConditionTree() {
        // For simple cases (just AND or OR at root level)
        if (conditionManager.getConditions().isEmpty()) {
            return null;
        }
        
        // Serialize the logical structure
        return serializeLogicalCondition(conditionManager.getRootCondition());
    }
    
    private Map<String, Object> serializeLogicalCondition(Condition condition) {
        Map<String, Object> result = new HashMap<>();
        
        if (condition instanceof AndCondition) {
            AndCondition andCond = (AndCondition) condition;
            result.put("type", "AND");
            List<Map<String, Object>> children = new ArrayList<>();
            for (Condition child : andCond.getConditions()) {
                children.add(serializeLogicalCondition(child));
            }
            result.put("conditions", children);
        }
        else if (condition instanceof OrCondition) {
            OrCondition orCond = (OrCondition) condition;
            result.put("type", "OR");
            List<Map<String, Object>> children = new ArrayList<>();
            for (Condition child : orCond.getConditions()) {
                children.add(serializeLogicalCondition(child));
            }
            result.put("conditions", children);
        }
        else if (condition instanceof NotCondition) {
            NotCondition notCond = (NotCondition) condition;
            result.put("type", "NOT");
            result.put("condition", serializeLogicalCondition(notCond.getCondition()));
        }
        else {
            // Primitive condition
            result.put("type", condition.getType().getIdentifier());
            
            // Add type-specific properties
            if (condition instanceof TimeCondition) {
                TimeCondition timeCondition = (TimeCondition) condition;
                result.put("endTime", timeCondition.getEndTime().toInstant().toEpochMilli());
            } 
            else if (condition instanceof SkillLevelCondition) {
                SkillLevelCondition skillCondition = (SkillLevelCondition) condition;
                result.put("skill", skillCondition.getSkill().getName());
                result.put("targetLevel", skillCondition.getTargetLevel());
            }
            else if (condition instanceof SkillXpCondition) {
                SkillXpCondition xpCondition = (SkillXpCondition) condition;
                result.put("skill", xpCondition.getSkill().getName());
                result.put("targetXp", xpCondition.getTargetXp());
            }
            else if (condition instanceof LootItemCondition) {
                LootItemCondition itemCondition = (LootItemCondition) condition;
                result.put("itemName", itemCondition.getItemName());
                result.put("targetAmount", itemCondition.getTargetAmount());
                // Store current tracking state for persistence
                result.put("currentTrackedCount", itemCondition.getCurrentTrackedCount());
                result.put("lastInventoryCount", itemCondition.getLastInventoryCount());
                result.put("lastBankedCount", itemCondition.getLastBankedCount());
                result.put("startTime", itemCondition.getStartTime().toEpochMilli());
            }
        }
        
        return result;
    }
    // Add deserialization method for conditions
    public void deserializeConditions(List<Map<String, Object>> conditionsList) {
        if (conditionsList == null) return;
        
        for (Map<String, Object> conditionMap : conditionsList) {
            String typeStr = (String) conditionMap.get("type");
            ConditionType type = ConditionType.fromIdentifier(typeStr);
            
            if (type == null) continue;
            
            Condition condition = null;
            
            switch (type) {
                case TIME:
                    long endTimeMillis = ((Number) conditionMap.get("endTime")).longValue();
                    ZonedDateTime endTime = ZonedDateTime.ofInstant(
                        Instant.ofEpochMilli(endTimeMillis), 
                        ZoneId.systemDefault()
                    );
                    condition = new TimeCondition(endTime);
                    break;
                    
                case SKILL_LEVEL:
                    String skillName = (String) conditionMap.get("skill");
                    int targetLevel = ((Number) conditionMap.get("targetLevel")).intValue();
                    Skill skill = Skill.valueOf(skillName);
                    condition = new SkillLevelCondition(skill, targetLevel);
                    break;
                    
                case SKILL_XP:
                    String skillXpName = (String) conditionMap.get("skill");
                    int targetXp = ((Number) conditionMap.get("targetXp")).intValue();
                    Skill skillXp = Skill.valueOf(skillXpName);
                    condition = new SkillXpCondition(skillXp, targetXp);
                    break;
                    
                case ITEM:
                    String itemName = (String) conditionMap.get("itemName");
                    int targetAmount = ((Number) conditionMap.get("targetAmount")).intValue();
                    
                    // Create builder for LootItemCondition
                    LootItemCondition.LootItemConditionBuilder builder = LootItemCondition.builder()
                        .itemName(itemName)
                        .targetAmount(targetAmount);
                    
                    // Create the condition
                    LootItemCondition lootCondition = builder.build();
                    
                    // Restore tracking state if available
                    if (conditionMap.containsKey("currentTrackedCount")) {
                        int currentTrackedCount = ((Number) conditionMap.get("currentTrackedCount")).intValue();
                        // Use reflection or setter if available to set the current tracked count
                        // For example: lootCondition.setCurrentTrackedCount(currentTrackedCount);
                    }
                    
                    if (conditionMap.containsKey("lastInventoryCount")) {
                        int lastInventoryCount = ((Number) conditionMap.get("lastInventoryCount")).intValue();
                        // Use reflection or setter if available
                        // For example: lootCondition.setLastInventoryCount(lastInventoryCount);
                    }
                    
                    if (conditionMap.containsKey("lastBankedCount")) {
                        int lastBankedCount = ((Number) conditionMap.get("lastBankedCount")).intValue();
                        // Use reflection or setter if available
                        // For example: lootCondition.setLastBankedCount(lastBankedCount);
                    }
                    
                    condition = lootCondition;
                    break;
            }
            
            if (condition != null) {
                addCondition(condition);
            }
        }
    }
    public void setLogicalCondition(LogicalCondition condition) {
        this.conditionManager.setRootCondition(condition);
    }
    /**
     * Get a formatted display of when this plugin will run next, including condition information.
     * 
     * @param currentTimeMillis Current system time in milliseconds
     * @return Human-readable description of next run time or condition status
     */
    public String getNextRunDisplay(long currentTimeMillis) {
        if (!enabled) {
            return "Disabled";
        }

        if (isRunning()) {
            return "Running";
        }
        
        // Check for condition-based execution
        if (!conditionManager.getConditions().isEmpty()) {
            double progressPct = getConditionProgress();
            if (progressPct > 0) {
                return String.format("%.1f%% complete", progressPct);
            }
        }

        // Handle time-based scheduling
        if (!isDueToRun()) {
            ZonedDateTime currentTime = ZonedDateTime.ofInstant(
                    Instant.ofEpochMilli(currentTimeMillis), 
                    ZoneId.systemDefault());
                    
            long timeUntilMillis = Duration.between(currentTime, nextRunTime).toMillis();
            
            if (timeUntilMillis <= 0) {
                return "Ready to run";
            }
            
            long hours = TimeUnit.MILLISECONDS.toHours(timeUntilMillis);
            long minutes = TimeUnit.MILLISECONDS.toMinutes(timeUntilMillis) % 60;

            if (hours > 0) {
                return String.format("In %dh %dm", hours, minutes);
            } else {
                return String.format("In %dm", minutes);
            }
        }
        
        return "Ready to run";
    }

    /**
     * Estimates overall progress percentage across all conditions.
     * Returns 0 if progress cannot be determined.
     */
    private double getConditionProgress() {
        List<Condition> conditions = conditionManager.getConditions();
        if (conditions.isEmpty()) {
            return 0;
        }
        
        double totalProgress = 0;
        int countableConditions = 0;
        
        for (Condition condition : conditions) {
            if (condition instanceof LootItemCondition) {
                LootItemCondition itemCondition = (LootItemCondition) condition;
                totalProgress += itemCondition.getProgressPercentage();
                countableConditions++;
            } 
            else if (condition instanceof SkillXpCondition) {
                SkillXpCondition xpCondition = (SkillXpCondition) condition;
                totalProgress += xpCondition.getProgressPercentage();
                countableConditions++;
            }
            else if (condition instanceof TimeCondition) {
                TimeCondition timeCondition = (TimeCondition) condition;
                Duration total = timeCondition.getTotalDuration();
                Duration remaining = timeCondition.getTimeRemaining();
                
                if (!total.isZero()) {
                    double pct = 100.0 * (1.0 - (double)remaining.toMillis() / total.toMillis());
                    totalProgress += pct;
                    countableConditions++;
                }
            }
        }
        
        if (countableConditions == 0) {
            return 0;
        }
        
        return totalProgress / countableConditions;
    }
    
}
