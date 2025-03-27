package net.runelite.client.plugins.microbot.pluginscheduler.condition.time;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ConditionType;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Condition that is met on specific days of the week.
 * This allows scheduling tasks to run only on certain days.
 */
@Getter
@Slf4j
public class DayOfWeekCondition implements Condition {
    private final Set<DayOfWeek> activeDays;
    private boolean registered = false;
    
    /**
     * Creates a day of week condition for the specified days
     */
    public DayOfWeekCondition(Set<DayOfWeek> activeDays) {
        this.activeDays = EnumSet.copyOf(activeDays);
    }
    
    /**
     * Creates a day of week condition for the specified days
     */
    public DayOfWeekCondition(DayOfWeek... days) {
        this.activeDays = EnumSet.noneOf(DayOfWeek.class);
        this.activeDays.addAll(Arrays.asList(days));
    }
    
    /**
     * Creates a condition for weekdays (Monday through Friday)
     */
    public static DayOfWeekCondition weekdays() {
        return new DayOfWeekCondition(
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
        return new DayOfWeekCondition(
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
        return new DayOfWeekCondition(day);
    }

    /**
     * Creates a randomized DayOfWeekCondition with a random number of consecutive days.
     * 
     * @param minDays Minimum number of consecutive days
     * @param maxDays Maximum number of consecutive days
     * @return A condition with randomly selected consecutive days
     */
    public static DayOfWeekCondition createRandomized(int minDays, int maxDays) {
        // Ensure valid range
        minDays = Math.max(1, Math.min(7, minDays));
        maxDays = Math.max(minDays, Math.min(7, maxDays));
        
        // Generate random number of days
        int numDays = minDays + (int)(Math.random() * (maxDays - minDays + 1));
        
        // Generate random starting day (0-6)
        int startDayIndex = (int)(Math.random() * 7);
        DayOfWeek startDay = DayOfWeek.of(startDayIndex % 7 + 1); // Convert 0-6 to 1-7
        
        Set<DayOfWeek> selectedDays = EnumSet.noneOf(DayOfWeek.class);
        
        // Add consecutive days starting from the random start day
        for (int i = 0; i < numDays; i++) {
            int dayIndex = (startDayIndex + i) % 7;
            DayOfWeek day = DayOfWeek.of(dayIndex % 7 + 1); // Convert 0-6 to 1-7
            selectedDays.add(day);
        }
        
        return new DayOfWeekCondition(selectedDays);
    }

    /**
     * Gets the duration of a week this condition is active.
     * Since DayOfWeekCondition repeats weekly, a full duration would be 7 days.
     * This returns the proportion of the week that is covered.
     * 
     * @return The duration this condition is active in a week
     */
    public Duration getTotalDuration() {
        // Each day represents 24 hours of a 168-hour week (7 days)
        long hoursActive = activeDays.size() * 24L;
        return Duration.ofHours(hoursActive);
    }
    /**
     * Gets the calculated end time for the current or next active day.
     * If today is an active day, returns the end of today.
     * If today is not active, returns the start of the next active day.
     * 
     * @return The end time of the current active day or start of the next active day
     */
    public ZonedDateTime getEndTime() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        DayOfWeek today = now.getDayOfWeek();
        
        // If today is an active day, return end of today
        if (activeDays.contains(today)) {
            return now.toLocalDate().atTime(LocalTime.MAX).atZone(ZoneId.systemDefault());
        }
        
        // Find the next active day
        for (int i = 1; i <= 7; i++) {
            DayOfWeek nextDay = today.plus(i);
            if (activeDays.contains(nextDay)) {
                // Return the start of that day
                return now.toLocalDate().plusDays(i).atStartOfDay().atZone(ZoneId.systemDefault());
            }
        }
        
        // If no active days found (shouldn't happen with valid configuration)
        return now.plusDays(7);
    }
    @Override
    public boolean isMet() {
        // Register for events if not already registered
        if (!registered) {
            Microbot.getEventBus().register(this);
            registered = true;
        }
        
        DayOfWeek today = LocalDate.now().getDayOfWeek();
        boolean result = activeDays.contains(today);
        
        if (result) {
            log.debug("Day of week condition met: today is {}", today);
        }
        
        return result;
    }
    
    @Subscribe
    public void onGameTick(GameTick event) {
        // This ensures we check the condition on every game tick
    }
    
    @Override
    public void reset() {
        // Nothing to reset for this condition
    }
    
    @Override
    public String getDescription() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        DayOfWeek today = now.getDayOfWeek();
        boolean isActiveToday = activeDays.contains(today);
        
        StringBuilder sb = new StringBuilder("Active on: ");
        sb.append(activeDays.stream()
                .map(day -> day.toString().charAt(0) + day.toString().substring(1).toLowerCase())
                .collect(Collectors.joining(", ")));
        
        // Add progress information
        if (isActiveToday) {
            // Calculate how much of today is left
            LocalTime currentTime = now.toLocalTime();
            LocalTime endOfDay = LocalTime.MAX;
            
            // Calculate percentage of day completed
            double minutesInDay = 24 * 60;
            double minutesPassed = currentTime.getHour() * 60 + currentTime.getMinute();
            double progressPercent = 100.0 * (minutesPassed / minutesInDay);
            
            sb.append(String.format(", progress: %.1f%%", progressPercent));
            sb.append(" (active today)");
        } else {
            // Find the next active day
            int daysUntilNext = 0;
            for (int i = 1; i <= 7; i++) {
                DayOfWeek nextDay = today.plus(i);
                if (activeDays.contains(nextDay)) {
                    daysUntilNext = i;
                    break;
                }
            }
            
            sb.append(String.format(", next active in %d day%s", 
                    daysUntilNext, daysUntilNext > 1 ? "s" : ""));
        }
        
        return sb.toString();
    }
    
    @Override
    public ConditionType getType() {
        return ConditionType.TIME;
    }
    
    @Override
    public void unregisterEvents() {
        if (registered) {
            try {
                Microbot.getEventBus().unregister(this);
            } catch (Exception e) {
                log.debug("Error unregistering day of week condition", e);
            }
            registered = false;
        }
    }
}