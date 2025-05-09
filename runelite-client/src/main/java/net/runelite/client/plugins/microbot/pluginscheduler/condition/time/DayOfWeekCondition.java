package net.runelite.client.plugins.microbot.pluginscheduler.condition.time;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.OrCondition;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalField;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Condition that is met on specific days of the week.
 * This allows scheduling tasks to run only on certain days.
 */
@Getter
@Slf4j
@EqualsAndHashCode(callSuper = true)
public class DayOfWeekCondition extends TimeCondition {
    
    public static String getVersion() {
        return "0.0.1";
    }
    private final Set<DayOfWeek> activeDays;
    private final long maxRepeatsPerDay;
    private final long maxRepeatsPerWeek;
    private transient ZonedDateTime nextTriggerDay;
    private transient Map<LocalDate, Integer> dailyResetCounts = new HashMap<>();
    private transient Map<Integer, Integer> weeklyResetCounts = new HashMap<>();
    
    private final AndCondition combinedCondition;
    public void setIntervalCondition(IntervalCondition intervalCondition) {
        combinedCondition.addCondition(intervalCondition);
    }
    public Optional<IntervalCondition> getIntervalCondition() {     
        assert(combinedCondition.getConditions().size() >= 1 && combinedCondition.getConditions().size() <= 2);
        for (Condition condition : combinedCondition.getConditions()) {
            if (condition instanceof IntervalCondition) {
                return Optional.of((IntervalCondition) condition);
            }
        }
        return Optional.empty();
    }
    public boolean hasIntervalCondition() {
        return getIntervalCondition().isPresent();
    }
    /**
     * Creates a day of week condition for the specified days.
     * This condition will be satisfied when the current day of the week matches any day in the provided set.
     *
     * @param maximumNumberOfRepeats The maximum number of times this condition can trigger (0 for unlimited)
     * @param activeDays The set of days on which this condition should be active
     */
    public DayOfWeekCondition(long maximumNumberOfRepeats, Set<DayOfWeek> activeDays) {
        this(maximumNumberOfRepeats, 0, 0, activeDays);
    }
    
    /**
     * Creates a day of week condition for the specified days with limits on per-day usage.
     * This condition will be satisfied when the current day of the week matches any day in 
     * the provided set and hasn't exceeded the daily repeat limit.
     *
     * @param maximumNumberOfRepeats The maximum number of times this condition can trigger overall (0 for unlimited)
     * @param maxRepeatsPerDay The maximum number of times this condition can trigger per day (0 for unlimited)
     * @param activeDays The set of days on which this condition should be active
     */
    public DayOfWeekCondition(long maximumNumberOfRepeats, long maxRepeatsPerDay, Set<DayOfWeek> activeDays) {
        this(maximumNumberOfRepeats, maxRepeatsPerDay, 0, activeDays);
    }
    
    /**
     * Creates a day of week condition for the specified days with limits on per-day and per-week usage.
     *
     * @param maximumNumberOfRepeats The maximum number of times this condition can trigger overall (0 for unlimited)
     * @param maxRepeatsPerDay The maximum number of times this condition can trigger per day (0 for unlimited)
     * @param maxRepeatsPerWeek The maximum number of times this condition can trigger per week (0 for unlimited)
     * @param activeDays The set of days on which this condition should be active
     */
    public DayOfWeekCondition(long maximumNumberOfRepeats, long maxRepeatsPerDay, long maxRepeatsPerWeek, Set<DayOfWeek> activeDays) {
        super(maximumNumberOfRepeats);
        this.activeDays = EnumSet.copyOf(activeDays);
        this.maxRepeatsPerDay = maxRepeatsPerDay;
        this.maxRepeatsPerWeek = maxRepeatsPerWeek;
        this.dailyResetCounts = new HashMap<>();
        this.weeklyResetCounts = new HashMap<>();
        updateNextTriggerDay();
        this.combinedCondition = new AndCondition();
        this.combinedCondition.addCondition(this);
    }
    
    /**
     * Creates a day of week condition for the specified days.
     * This condition will be satisfied when the current day of the week matches any of the provided days.
     *
     * @param maximumNumberOfRepeats The maximum number of times this condition can trigger (0 for unlimited)
     * @param days The array of days on which this condition should be active
     */
    public DayOfWeekCondition(long maximumNumberOfRepeats, DayOfWeek... days) {
        this(maximumNumberOfRepeats, 0, 0, days);
    }
    
    /**
     * Creates a day of week condition for the specified days with limits on per-day usage.
     *
     * @param maximumNumberOfRepeats The maximum number of times this condition can trigger overall (0 for unlimited)
     * @param maxRepeatsPerDay The maximum number of times this condition can trigger per day (0 for unlimited)
     * @param days The array of days on which this condition should be active
     */
    public DayOfWeekCondition(long maximumNumberOfRepeats, long maxRepeatsPerDay, DayOfWeek... days) {
        this(maximumNumberOfRepeats, maxRepeatsPerDay, 0, days);
    }
    
    /**
     * Creates a day of week condition for the specified days with limits on per-day and per-week usage.
     *
     * @param maximumNumberOfRepeats The maximum number of times this condition can trigger overall (0 for unlimited)
     * @param maxRepeatsPerDay The maximum number of times this condition can trigger per day (0 for unlimited)
     * @param maxRepeatsPerWeek The maximum number of times this condition can trigger per week (0 for unlimited)
     * @param days The array of days on which this condition should be active
     */
    public DayOfWeekCondition(long maximumNumberOfRepeats, long maxRepeatsPerDay, long maxRepeatsPerWeek, DayOfWeek... days) {
        super(maximumNumberOfRepeats);
        this.activeDays = EnumSet.noneOf(DayOfWeek.class);
        this.activeDays.addAll(Arrays.asList(days));
        this.maxRepeatsPerDay = maxRepeatsPerDay;
        this.maxRepeatsPerWeek = maxRepeatsPerWeek;
        this.dailyResetCounts = new HashMap<>();
        this.weeklyResetCounts = new HashMap<>();
        updateNextTriggerDay();
        this.combinedCondition = new AndCondition();
        this.combinedCondition.addCondition(this);
    }
    
    /**
     * Creates a condition for weekdays (Monday through Friday)
     */
    public static DayOfWeekCondition weekdays() {
        return new DayOfWeekCondition(0,
                DayOfWeek.MONDAY, 
                DayOfWeek.TUESDAY, 
                DayOfWeek.WEDNESDAY, 
                DayOfWeek.THURSDAY, 
                DayOfWeek.FRIDAY);
    }
    
    /**
     * Creates a condition for weekends (Saturday and Sunday)
     */
    public static DayOfWeekCondition weekends() {
        return new DayOfWeekCondition(0,
                DayOfWeek.SATURDAY, 
                DayOfWeek.SUNDAY);
    }
    
    /**
     * Creates a condition for weekdays with specified daily limits
     */
    public static DayOfWeekCondition weekdaysWithDailyLimit(long maxRepeatsPerDay) {
        return new DayOfWeekCondition(0, maxRepeatsPerDay,
                DayOfWeek.MONDAY, 
                DayOfWeek.TUESDAY, 
                DayOfWeek.WEDNESDAY, 
                DayOfWeek.THURSDAY, 
                DayOfWeek.FRIDAY);
    }
    
    /**
     * Creates a condition for weekends with specified daily limits
     */
    public static DayOfWeekCondition weekendsWithDailyLimit(long maxRepeatsPerDay) {
        return new DayOfWeekCondition(0, maxRepeatsPerDay,
                DayOfWeek.SATURDAY, 
                DayOfWeek.SUNDAY);
    }
    
    /**
     * Creates a condition for weekdays with specified weekly limit
     */
    public static DayOfWeekCondition weekdaysWithWeeklyLimit(long maxRepeatsPerWeek) {
        return new DayOfWeekCondition(0, 0, maxRepeatsPerWeek,
                DayOfWeek.MONDAY, 
                DayOfWeek.TUESDAY, 
                DayOfWeek.WEDNESDAY, 
                DayOfWeek.THURSDAY, 
                DayOfWeek.FRIDAY);
    }
    
    /**
     * Creates a condition for weekends with specified weekly limit
     */
    public static DayOfWeekCondition weekendsWithWeeklyLimit(long maxRepeatsPerWeek) {
        return new DayOfWeekCondition(0, 0, maxRepeatsPerWeek,
                DayOfWeek.SATURDAY, 
                DayOfWeek.SUNDAY);
    }
    
    /**
     * Creates a condition for all days with specified daily and weekly limits
     */
    public static DayOfWeekCondition allDaysWithLimits(long maxRepeatsPerDay, long maxRepeatsPerWeek) {
        return new DayOfWeekCondition(0, maxRepeatsPerDay, maxRepeatsPerWeek,
                DayOfWeek.MONDAY, 
                DayOfWeek.TUESDAY, 
                DayOfWeek.WEDNESDAY, 
                DayOfWeek.THURSDAY, 
                DayOfWeek.FRIDAY,
                DayOfWeek.SATURDAY, 
                DayOfWeek.SUNDAY);
    }

    /**
     * Creates a day of week condition from a ZonedDateTime, using its day of week.
     * 
     * @param dateTime The date/time to extract day of week from
     * @return A condition for that specific day of the week
     */
    public static DayOfWeekCondition fromZonedDateTime(ZonedDateTime dateTime) {
        DayOfWeek day = dateTime.getDayOfWeek();
        return new DayOfWeekCondition(0, day);
    }

    /**
     * Creates a randomized DayOfWeekCondition with a random number of consecutive days.
     * 
     * @param minDays Minimum number of consecutive days
     * @param maxDays Maximum number of consecutive days
     * @return A condition with randomly selected consecutive days
     */
    public static DayOfWeekCondition createRandomized(int minDays, int maxDays) {
        int numDays = Rs2Random.between(minDays, maxDays);
        numDays = Math.min(numDays, 7); // Cap at 7 days
        
        // Randomly pick a starting day
        int startDayIndex = Rs2Random.between(0, 6);
        Set<DayOfWeek> selectedDays = EnumSet.noneOf(DayOfWeek.class);
        
        for (int i = 0; i < numDays; i++) {
            int dayIndex = (startDayIndex + i) % 7;
            selectedDays.add(DayOfWeek.of(dayIndex == 0 ? 7 : dayIndex)); // DayOfWeek is 1-based
        }
        
        return new DayOfWeekCondition(0, selectedDays);
    }

    /**
     * {@inheritDoc}
     * Determines if the condition is currently satisfied based on whether the current
     * day of the week is in the set of active days and the condition can still trigger.
     * Also checks if the daily and weekly reset counts haven't exceeded the maximum allowed.
     *
     * @return true if today is one of the active days and neither the overall, daily, nor weekly 
     *         limits have been exceeded, false otherwise
     */
    @Override
    public boolean isSatisfied() { 
        // First make sure this condition can trigger again
        if (!canTriggerAgain()) {
            return false;
        }
        
        // Check if today is an active day
        ZonedDateTime now = getNow();
        DayOfWeek today = now.getDayOfWeek();
        if (!activeDays.contains(today)) {
            return false;
        }
        
        // Check daily reset count hasn't been exceeded
        LocalDate todayDate = now.toLocalDate();
        int todayCount = dailyResetCounts.getOrDefault(todayDate, 0);
        if (maxRepeatsPerDay > 0 && todayCount >= maxRepeatsPerDay) {
            return false;
        }
        
        // Check weekly reset count hasn't been exceeded
        if (maxRepeatsPerWeek > 0) {
            int currentWeek = getCurrentWeekNumber(now);
            int weekCount = weeklyResetCounts.getOrDefault(currentWeek, 0);
            if (weekCount >= maxRepeatsPerWeek) {
                return false;
            }
        }
        
        // If we have an interval condition, it must also be satisfied
        IntervalCondition intervalCondition = getIntervalCondition().orElse(null);
        if (intervalCondition != null && !intervalCondition.isSatisfied()) {
            return false;
        }
        
        return true;
    }

    /**
     * {@inheritDoc}
     * Provides a user-friendly description of this condition, showing which days are active.
     * Special cases are handled for "Every day", "Weekdays", and "Weekends".
     *
     * @return A human-readable string describing the active days for this condition
     */
    @Override
    public String getDescription() {
        if (activeDays.isEmpty()) {
            return "No active days";
        }
        
        String daysDescription;
        if (activeDays.size() == 7) {
            daysDescription = "Every day";
        } else if (activeDays.size() == 5 && 
            activeDays.contains(DayOfWeek.MONDAY) &&
            activeDays.contains(DayOfWeek.TUESDAY) &&
            activeDays.contains(DayOfWeek.WEDNESDAY) &&
            activeDays.contains(DayOfWeek.THURSDAY) &&
            activeDays.contains(DayOfWeek.FRIDAY)) {
            daysDescription = "Weekdays";
        } else if (activeDays.size() == 2 && 
            activeDays.contains(DayOfWeek.SATURDAY) &&
            activeDays.contains(DayOfWeek.SUNDAY)) {
            daysDescription = "Weekends";
        } else {
            daysDescription = "On " + activeDays.stream()
                .map(day -> day.toString().charAt(0) + day.toString().substring(1).toLowerCase())
                .collect(Collectors.joining(", "));
        }
        
        StringBuilder sb = new StringBuilder(daysDescription);
        
        if (maxRepeatsPerDay > 0) {
            sb.append(" (max ").append(maxRepeatsPerDay).append(" per day)");
        }
        
        if (maxRepeatsPerWeek > 0) {
            sb.append(" (max ").append(maxRepeatsPerWeek).append(" per week)");
        }
        
        return sb.toString();
    }
    
    /**
     * Provides a detailed description of the condition, including the current day,
     * whether today is active, the next upcoming active day, progress percentage,
     * and basic condition information.
     *
     * @return A detailed human-readable description with status information
     */
    public String getDetailedDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append(getDescription());
        
        ZonedDateTime now = getNow();
        DayOfWeek today = now.getDayOfWeek();
        LocalDate todayDate = now.toLocalDate();
        int currentWeek = getCurrentWeekNumber(now);
        
        sb.append("\nToday is ").append(today.toString().charAt(0) + today.toString().substring(1).toLowerCase());
        sb.append(" (").append(activeDays.contains(today) ? "active" : "inactive").append(")");
        
        // Daily and weekly usage
        if (activeDays.contains(today)) {
            int todayUsage = dailyResetCounts.getOrDefault(todayDate, 0);
            sb.append("\nToday's usage: ").append(todayUsage);
            if (maxRepeatsPerDay > 0) {
                sb.append("/").append(maxRepeatsPerDay);
            }
            
            int weekUsage = weeklyResetCounts.getOrDefault(currentWeek, 0);
            sb.append("\nThis week's usage: ").append(weekUsage);
            if (maxRepeatsPerWeek > 0) {
                sb.append("/").append(maxRepeatsPerWeek);
            }
        }
        
        // Show next trigger day if today is not active or limits are reached
        boolean dailyLimitReached = maxRepeatsPerDay > 0 && 
                                   dailyResetCounts.getOrDefault(todayDate, 0) >= maxRepeatsPerDay;
        boolean weeklyLimitReached = maxRepeatsPerWeek > 0 && 
                                    weeklyResetCounts.getOrDefault(currentWeek, 0) >= maxRepeatsPerWeek;
        
        if (!activeDays.contains(today) || dailyLimitReached || weeklyLimitReached) {
            if (nextTriggerDay != null) {
                DayOfWeek nextDay = nextTriggerDay.getDayOfWeek();
                long daysUntil = ChronoUnit.DAYS.between(now.toLocalDate(), nextTriggerDay.toLocalDate());
                
                sb.append("\nNext active day: ")
                  .append(nextDay.toString().charAt(0) + nextDay.toString().substring(1).toLowerCase())
                  .append(" (in ").append(daysUntil).append(daysUntil == 1 ? " day)" : " days)");
                
                if (weeklyLimitReached) {
                    // Calculate days until next week starts
                    LocalDate today_date = now.toLocalDate();
                    LocalDate startOfNextWeek = today_date.plusDays(8 - today.getValue()); // Monday is 1, Sunday is 7
                    long daysUntilNextWeek = ChronoUnit.DAYS.between(today_date, startOfNextWeek);
                    
                    sb.append("\nWeekly limit reached. New week starts in ").append(daysUntilNextWeek)
                      .append(daysUntilNextWeek == 1 ? " day" : " days");
                }
            }
        }
        
        // Include interval condition details if present
        getIntervalCondition().ifPresent(intervalCondition -> {
            sb.append("\n\nInterval condition: ").append(intervalCondition.getDescription());
            sb.append("\nInterval satisfied: ").append(intervalCondition.isSatisfied());
            
            // Add next trigger time if available
            intervalCondition.getCurrentTriggerTime().ifPresent(triggerTime -> {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                sb.append("\nNext interval trigger: ").append(triggerTime.format(formatter));
                
                if (now.isBefore(triggerTime)) {
                    Duration timeUntil = Duration.between(now, triggerTime);
                    long seconds = timeUntil.getSeconds();
                    sb.append(" (in ").append(String.format("%02d:%02d:%02d", 
                        seconds / 3600, (seconds % 3600) / 60, seconds % 60)).append(")");
                } else {
                    sb.append(" (ready now)");
                }
            });
        });
        
        sb.append("\nProgress: ").append(String.format("%.1f%%", getProgressPercentage()));
        sb.append("\n").append(super.getDescription());
        
        return sb.toString();
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        
        // Basic information
        sb.append("DayOfWeekCondition:\n");
        sb.append("  ┌─ Configuration ─────────────────────────────\n");
        sb.append("  │ Active Days: ").append(getDescription()).append("\n");
        if (maxRepeatsPerDay > 0) {
            sb.append("  │ Max Repeats Per Day: ").append(maxRepeatsPerDay).append("\n");
        }
        if (maxRepeatsPerWeek > 0) {
            sb.append("  │ Max Repeats Per Week: ").append(maxRepeatsPerWeek).append("\n");
        }
        
// Add interval condition information if present
        getIntervalCondition().ifPresent(intervalCondition -> {
            sb.append("  │ Interval: ").append(intervalCondition.toString()).append("\n");
        });
        // Staus information
        sb.append("  ├─ Status ──────────────────────────────────\n");
        sb.append("  │ Satisfied: ").append(isSatisfied()).append("\n");
        
        ZonedDateTime now = getNow();
        DayOfWeek today = now.getDayOfWeek();
        LocalDate todayDate = now.toLocalDate();
        int currentWeek = getCurrentWeekNumber(now);
        
        sb.append("  │ Current Day: ").append(today.toString().charAt(0) + today.toString().substring(1).toLowerCase())
          .append(" (").append(activeDays.contains(today) ? "active" : "inactive").append(")\n");
        
        if (activeDays.contains(today)) {
            int todayUsage = dailyResetCounts.getOrDefault(todayDate, 0);
            sb.append("  │ Today's Usage: ").append(todayUsage);
            if (maxRepeatsPerDay > 0) {
                sb.append("/").append(maxRepeatsPerDay);
            }
            sb.append("\n");
            
            int weekUsage = weeklyResetCounts.getOrDefault(currentWeek, 0);
            sb.append("  │ This Week's Usage: ").append(weekUsage);
            if (maxRepeatsPerWeek > 0) {
                sb.append("/").append(maxRepeatsPerWeek);
            }
            sb.append("\n");
        }
        
        // If not active today or limits reached, show next active day
        boolean dailyLimitReached = maxRepeatsPerDay > 0 && 
                                   dailyResetCounts.getOrDefault(todayDate, 0) >= maxRepeatsPerDay;
        boolean weeklyLimitReached = maxRepeatsPerWeek > 0 && 
                                    weeklyResetCounts.getOrDefault(currentWeek, 0) >= maxRepeatsPerWeek;
        
        if ((!activeDays.contains(today) || dailyLimitReached || weeklyLimitReached) 
            && nextTriggerDay != null) {
            
            DayOfWeek nextDay = nextTriggerDay.getDayOfWeek();
            long daysUntil = ChronoUnit.DAYS.between(now.toLocalDate(), nextTriggerDay.toLocalDate());
            
            sb.append("  │ Next Active Day: ")
              .append(nextDay.toString().charAt(0) + nextDay.toString().substring(1).toLowerCase())
              .append(" (").append(nextTriggerDay.toLocalDate())
              .append(", in ").append(daysUntil).append(daysUntil == 1 ? " day)\n" : " days)\n");
            
            if (weeklyLimitReached) {
                LocalDate today_date = now.toLocalDate();
                LocalDate startOfNextWeek = today_date.plusDays(8 - today.getValue());
                long daysUntilNextWeek = ChronoUnit.DAYS.between(today_date, startOfNextWeek);
                
                sb.append("  │ Weekly Limit Reached: New week starts in ")
                  .append(daysUntilNextWeek).append(daysUntilNextWeek == 1 ? " day\n" : " days\n");
            }
        }
        
        sb.append("  │ Progress: ").append(String.format("%.1f%%", getProgressPercentage())).append("\n");
        
        // Tracking info
        sb.append("  └─ Tracking ────────────────────────────────\n");
        sb.append("    Reset Count: ").append(currentValidResetCount);
        if (this.getMaximumNumberOfRepeats() > 0) {
            sb.append("/").append(getMaximumNumberOfRepeats());
        } else {
            sb.append(" (unlimited)");
        }
        sb.append("\n");
        if (lastValidResetTime != null) {
            sb.append("    Last Reset: ").append(lastValidResetTime.format(dateTimeFormatter)).append("\n");
        }
        sb.append("    Can Trigger Again: ").append(canTriggerAgain()).append("\n");
        
        return sb.toString();
    }

    /**
     * Updates the tracking of which days have been used and how many times
     * Also tracks weekly usage
     */
    private void updateDailyResetCount() {
        ZonedDateTime now = getNow();
        LocalDate today = now.toLocalDate();
        int currentWeek = getCurrentWeekNumber(now);
        
        // Increment today's reset count
        dailyResetCounts.put(today, dailyResetCounts.getOrDefault(today, 0) + 1);
        
        // Increment this week's reset count
        weeklyResetCounts.put(currentWeek, weeklyResetCounts.getOrDefault(currentWeek, 0) + 1);
        
        // Clean up old entries (optional, to prevent map growth)
        Set<LocalDate> oldDates = new HashSet<>();
        for (LocalDate date : dailyResetCounts.keySet()) {
            if (ChronoUnit.DAYS.between(date, today) > 14) { // Keep only last two weeks
                oldDates.add(date);
            }
        }
        for (LocalDate oldDate : oldDates) {
            dailyResetCounts.remove(oldDate);
        }
        
        // Clean up old week entries
        Set<Integer> oldWeeks = new HashSet<>();
        for (Integer week : weeklyResetCounts.keySet()) {
            if (week < currentWeek - 2) { // Keep only last few weeks
                oldWeeks.add(week);
            }
        }
        for (Integer oldWeek : oldWeeks) {
            weeklyResetCounts.remove(oldWeek);
        }
    }
    
    /**
     * Gets the current ISO week number in the year
     */
    private int getCurrentWeekNumber(ZonedDateTime dateTime) {
        WeekFields weekFields = WeekFields.of(Locale.getDefault());
        return dateTime.get(weekFields.weekOfWeekBasedYear());
    }
    
    /**
     * Updates the next trigger day based on current day and daily/weekly limits
     */
    private void updateNextTriggerDay() {
        ZonedDateTime now = getNow();
        DayOfWeek today = now.getDayOfWeek();
        LocalDate todayDate = now.toLocalDate();
        int currentWeek = getCurrentWeekNumber(now);
        
        // If today is active and hasn't reached limits, it's the trigger day
        if (activeDays.contains(today) && 
            (maxRepeatsPerDay <= 0 || dailyResetCounts.getOrDefault(todayDate, 0) < maxRepeatsPerDay) &&
            (maxRepeatsPerWeek <= 0 || weeklyResetCounts.getOrDefault(currentWeek, 0) < maxRepeatsPerWeek)) {
            nextTriggerDay = now.truncatedTo(ChronoUnit.DAYS);
            return;
        }
        
        // Check if we've hit the weekly limit but not the daily limit
        boolean weeklyLimitReached = (maxRepeatsPerWeek > 0 && 
                                     weeklyResetCounts.getOrDefault(currentWeek, 0) >= maxRepeatsPerWeek);
        
        // If weekly limit reached, find first active day in next week
        if (weeklyLimitReached) {
            // Start from next Monday (first day of next week)
            LocalDate mondayNextWeek = todayDate.plusDays(8 - today.getValue());
            ZonedDateTime nextWeekStart = now.with(mondayNextWeek.atStartOfDay());
            
            // Find first active day in next week
            for (int daysToAdd = 0; daysToAdd < 7; daysToAdd++) {
                ZonedDateTime checkDay = nextWeekStart.plusDays(daysToAdd);
                if (activeDays.contains(checkDay.getDayOfWeek())) {
                    nextTriggerDay = checkDay;
                    return;
                }
            }
        }
        
        // Otherwise, find the next active day in current week that hasn't reached daily limit
        int daysToAdd = 1;
        while (daysToAdd <= 7) {
            ZonedDateTime checkDay = now.plusDays(daysToAdd);
            DayOfWeek checkDayOfWeek = checkDay.getDayOfWeek();
            LocalDate checkDate = checkDay.toLocalDate();
            int checkWeek = getCurrentWeekNumber(checkDay);
            
            // Skip to next week if current week has reached limit
            if (weeklyLimitReached && checkWeek == currentWeek) {
                // Skip to Monday of next week
                int daysToMonday = 8 - today.getValue();
                daysToAdd = daysToMonday;
                continue;
            }
            
            if (activeDays.contains(checkDayOfWeek) && 
                (maxRepeatsPerDay <= 0 || dailyResetCounts.getOrDefault(checkDate, 0) < maxRepeatsPerDay) &&
                (maxRepeatsPerWeek <= 0 || weeklyResetCounts.getOrDefault(checkWeek, 0) < maxRepeatsPerWeek)) {
                // Found the next active day that hasn't reached limits
                nextTriggerDay = now.plusDays(daysToAdd).truncatedTo(ChronoUnit.DAYS);
                return;
            }
            
            daysToAdd++;
        }
        
        // If no active days found within next 7 days, set to null
        nextTriggerDay = null;
    }

    /**
     * {@inheritDoc}
     * Resets the condition state and increments the reset counter.
     * For day of week conditions, this tracks when the condition was last reset,
     * updates daily and weekly usage counters, and recalculates the next trigger day.
     *
     * @param randomize Whether to randomize aspects of the condition (not used for this condition type)
     */
    @Override
    public void reset(boolean randomize) {
        updateValidReset();
        getIntervalCondition()
                .ifPresent(IntervalCondition::reset);
        updateDailyResetCount();
        updateNextTriggerDay();
    }
    
    /**
     * {@inheritDoc}
     * Calculates a percentage indicating progress toward the next active day.
     * If today is an active day and hasn't reached limits, returns 100%. 
     * Otherwise, returns a percentage based on how close we are to the next active day.
     *
     * @return A percentage from 0-100 indicating progress toward the next active day
     */
    @Override
    public double getProgressPercentage() {
        if (isSatisfied()) {
            return 100.0;
        }
        
        // If no active days, return 0
        if (activeDays.isEmpty()) {
            return 0.0;
        }
        
        ZonedDateTime now = getNow();
        DayOfWeek today = now.getDayOfWeek();
        LocalDate todayDate = now.toLocalDate();
        int currentWeek = getCurrentWeekNumber(now);
        
        // If today is an active day but reached daily limit
        if (activeDays.contains(today) && maxRepeatsPerDay > 0) {
            int todayCount = dailyResetCounts.getOrDefault(todayDate, 0);
            
            if (todayCount >= maxRepeatsPerDay) {
                // Calculate hours until midnight as progress toward next day
                long hoursUntilMidnight = 24 - now.getHour();
                return 100.0 * ((24.0 - hoursUntilMidnight) / 24.0);
            }
        }
        
        // If weekly limit reached
        if (maxRepeatsPerWeek > 0) {
            int weekCount = weeklyResetCounts.getOrDefault(currentWeek, 0);
            
            if (weekCount >= maxRepeatsPerWeek) {
                // Calculate days until next week as progress
                int daysUntilNextWeek = 8 - today.getValue(); // Days until next Monday
                return 100.0 * ((7.0 - daysUntilNextWeek) / 7.0);
            }
        }
        
        // Calculate days until the next active day
        if (nextTriggerDay == null) {
            updateNextTriggerDay();
        }
        
        if (nextTriggerDay != null) {
            long daysUntil = ChronoUnit.DAYS.between(now.toLocalDate(), nextTriggerDay.toLocalDate());
            if (daysUntil == 0) {
                return 100.0; // Same day
            } else if (daysUntil >= 7) {
                return 0.0; // A week or more away
            } else {
                return 100.0 * (1.0 - (daysUntil / 7.0));
            }
        }
        
        return 0.0;
    }
    
    /**
     * {@inheritDoc}
     * Calculates the next time this condition will be satisfied.
     * This accounts for daily and weekly limits and finding the next valid active day.
     *
     * @return An Optional containing the time when this condition will next be satisfied,
     *         or empty if the condition cannot trigger again or has no active days
     */
    @Override
    public Optional<ZonedDateTime> getCurrentTriggerTime() {
        if (!canTriggerAgain()) {
            return Optional.empty();
        }
        
        ZonedDateTime now = getNow();
        DayOfWeek today = now.getDayOfWeek();
        LocalDate todayDate = now.toLocalDate();
        int currentWeek = getCurrentWeekNumber(now);
        
        // If today is active and hasn't reached daily or weekly limits
        if (activeDays.contains(today) && 
            (maxRepeatsPerDay <= 0 || dailyResetCounts.getOrDefault(todayDate, 0) < maxRepeatsPerDay) &&
            (maxRepeatsPerWeek <= 0 || weeklyResetCounts.getOrDefault(currentWeek, 0) < maxRepeatsPerWeek)) {
            IntervalCondition intervalCondition = getIntervalCondition().orElse(null);
            if (intervalCondition != null) {                        
                return Optional.of(intervalCondition.getCurrentTriggerTime().orElse(null));                
            }
            return Optional.of(now.minusSeconds(1));
            
        }
        
        // If no active days defined, return empty
        if (activeDays.isEmpty()) {
            return Optional.empty();
        }
        
        // If nextTriggerDay isn't calculated yet or needs update
        if (nextTriggerDay == null) {
            updateNextTriggerDay();
        }
        
        return Optional.ofNullable(nextTriggerDay);
    }
    
    /**
     * Returns the count of resets that have occurred on the given date.
     * This can be used to track usage across days.
     * 
     * @param date The date to check
     * @return The number of resets that occurred on that date
     */
    public int getResetCountForDate(LocalDate date) {
        return dailyResetCounts.getOrDefault(date, 0);
    }
    
    /**
     * Returns the count of resets that have occurred in the given week.
     * This can be used to track usage across weeks.
     * 
     * @param weekNumber The ISO week number to check
     * @return The number of resets that occurred in that week
     */
    public int getResetCountForWeek(int weekNumber) {
        return weeklyResetCounts.getOrDefault(weekNumber, 0);
    }
    
    /**
     * Returns the count of resets that have occurred in the current week.
     * 
     * @return The number of resets that occurred in the current week
     */
    public int getCurrentWeekResetCount() {
        ZonedDateTime now = getNow();
        int currentWeek = getCurrentWeekNumber(now);
        return weeklyResetCounts.getOrDefault(currentWeek, 0);
    }
    
    /**
     * Checks if this DayOfWeekCondition has reached its daily limit for the current day.
     * 
     * @return true if the daily limit has been reached, false otherwise (including if there's no limit)
     */
    public boolean isDailyLimitReached() {
        if (maxRepeatsPerDay <= 0) {
            return false; // No daily limit
        }
        
        ZonedDateTime now = getNow();
        LocalDate today = now.toLocalDate();
        int todayCount = dailyResetCounts.getOrDefault(today, 0);
        
        return todayCount >= maxRepeatsPerDay;
    }
    
    /**
     * Checks if this DayOfWeekCondition has reached its weekly limit for the current week.
     * 
     * @return true if the weekly limit has been reached, false otherwise (including if there's no limit)
     */
    public boolean isWeeklyLimitReached() {
        if (maxRepeatsPerWeek <= 0) {
            return false; // No weekly limit
        }
        
        ZonedDateTime now = getNow();
        int currentWeek = getCurrentWeekNumber(now);
        int weekCount = weeklyResetCounts.getOrDefault(currentWeek, 0);
        
        return weekCount >= maxRepeatsPerWeek;
    }
    
    /**
     * Finds the next day that is NOT an active day.
     * This can be useful for determining when the condition will stop being satisfied.
     * 
     * @return An Optional containing the start time of the next non-active day,
     *         or empty if all days are active
     */
    public Optional<ZonedDateTime> getNextNonActiveDay() {
        ZonedDateTime now = getNow();
        DayOfWeek today = now.getDayOfWeek();
        
        // If today is already non-active, return today
        if (!activeDays.contains(today)) {
            return Optional.of(now.truncatedTo(ChronoUnit.DAYS));
        }
        
        // Otherwise, find the next non-active day
        int daysToAdd = 1;
        while (daysToAdd <= 7) {
            DayOfWeek nextDay = today.plus(daysToAdd);
            if (!activeDays.contains(nextDay)) {
                // Found the next non-active day
                ZonedDateTime nextNonActiveDay = now.plusDays(daysToAdd)
                    .truncatedTo(ChronoUnit.DAYS); // Start of the day
                return Optional.of(nextNonActiveDay);
            }
            daysToAdd++;
        }
        
        // If all days are active, return empty
        return Optional.empty();
    }
    
    /**
     * Gets the date when the next week starts (Monday)
     * 
     * @return The date of next Monday
     */
    public LocalDate getNextWeekStartDate() {
        ZonedDateTime now = getNow();
        DayOfWeek today = now.getDayOfWeek();
        return now.toLocalDate().plusDays(8 - today.getValue()); // Monday is 1, Sunday is 7
    }
    
    /**
     * Handles game tick events from the RuneLite event bus.
     * This method exists primarily to ensure the condition stays registered with the event bus.
     * 
     * @param tick The game tick event
     */
    @Subscribe
    public void onGameTick(GameTick tick) {
        // Just used to ensure we stay registered with the event bus
    }
    
    /**
     * Creates a combined condition that requires both specific days of the week and a time interval.
     * This factory method makes it easy to create a schedule that runs for specific durations on certain days.
     * 
     * @param dayCondition The day of week condition that specifies on which days the condition is active
     * @param intervalDuration The duration for the interval condition (how long to run)
     * @return An AndCondition that combines both conditions
     */
    public static DayOfWeekCondition withInterval(DayOfWeekCondition dayCondition, Duration intervalDuration) {
        dayCondition.setIntervalCondition( new IntervalCondition(intervalDuration));                        
        return dayCondition;
    }
    
    /**
     * Creates a combined condition that requires both specific days of the week and a randomized time interval.
     * 
     * @param dayCondition The day of week condition that specifies on which days the condition is active
     * @param minDuration The minimum duration for the interval
     * @param maxDuration The maximum duration for the interval
     * @return An AndCondition that combines both conditions
     */
    public static DayOfWeekCondition withRandomizedInterval(DayOfWeekCondition dayCondition, 
                                                  Duration minDuration, 
                                                  Duration maxDuration) {
        dayCondition.setIntervalCondition (IntervalCondition.createRandomized(minDuration, maxDuration));        
        return dayCondition;
    }
    
    /**
     * Factory method to create a condition that runs only on weekdays with a specified interval.
     * 
     * @param intervalHours The number of hours to run for
     * @return A combined condition that runs for the specified duration on weekdays
     */
    public static DayOfWeekCondition weekdaysWithHourLimit(int intervalHours) {
        return withInterval(weekdays(), Duration.ofHours(intervalHours));
    }
    
    /**
     * Factory method to create a condition that runs only on weekends with a specified interval.
     * 
     * @param intervalHours The number of hours to run for
     * @return A combined condition that runs for the specified duration on weekends
     */
    public static DayOfWeekCondition weekendsWithHourLimit(int intervalHours) {
        return withInterval(weekends(), Duration.ofHours(intervalHours));
    }
    
    /**
     * Factory method to create a condition for specific days with a limit on triggers per day
     * and a time limit per session.
     * 
     * @param days The days on which the condition should be active
     * @param maxRepeatsPerDay Maximum number of times the condition can trigger per day
     * @param sessionDuration How long each session should last
     * @return A combined condition with both day and interval constraints
     */
    public static DayOfWeekCondition createDailySchedule(Set<DayOfWeek> days, long maxRepeatsPerDay, Duration sessionDuration) {
        DayOfWeekCondition dayCondition = new DayOfWeekCondition(0, maxRepeatsPerDay, days);
        return withInterval(dayCondition, sessionDuration);
    }
    
    /**
     * Factory method to create a condition for specific days with a limit on triggers per day
     * and a randomized time limit per session.
     * 
     * @param days The days on which the condition should be active
     * @param maxRepeatsPerDay Maximum number of times the condition can trigger per day
     * @param minSessionDuration Minimum session duration
     * @param maxSessionDuration Maximum session duration
     * @return A combined condition with both day and randomized interval constraints
     */
    public static DayOfWeekCondition createRandomizedDailySchedule(Set<DayOfWeek> days, 
                                                        long maxRepeatsPerDay, 
                                                        Duration minSessionDuration,
                                                        Duration maxSessionDuration) {
        DayOfWeekCondition dayCondition = new DayOfWeekCondition(0, maxRepeatsPerDay, days);
        return withRandomizedInterval(dayCondition, minSessionDuration, maxSessionDuration);
    }
    
    /**
     * Factory method to create a humanized play schedule that mimics natural human gaming patterns.
     * Creates a schedule that:
     * - Plays more on weekends (2 sessions of 1-3 hours each)
     * - Plays less on weekdays (1 session of 30min-1.5hrs)
     * 
     * @return A natural-seeming play schedule
     */
    public static OrCondition createHumanizedPlaySchedule() {
        // Create weekday condition: 1 session per day, 30-90 minutes each
        DayOfWeekCondition weekdayCondition = new DayOfWeekCondition(0, 1, EnumSet.of(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, 
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));
        Condition weekdaySchedule = withRandomizedInterval(
                weekdayCondition, 
                Duration.ofMinutes(30), 
                Duration.ofMinutes(90));
        
        // Create weekend condition: 2 sessions per day, 1-3 hours each
        DayOfWeekCondition weekendCondition = new DayOfWeekCondition(0, 2, EnumSet.of(
                DayOfWeek.SATURDAY, DayOfWeek.SUNDAY));
        Condition weekendSchedule = withRandomizedInterval(
                weekendCondition, 
                Duration.ofHours(1), 
                Duration.ofHours(3));
        
        // Use OrCondition to combine them
        OrCondition combinedSchedule = 
                new OrCondition();
        combinedSchedule.addCondition(weekdaySchedule);
        combinedSchedule.addCondition(weekendSchedule);
        
        return combinedSchedule;
    }
}