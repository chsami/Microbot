package net.runelite.client.plugins.microbot.pluginscheduler.condition.time.util;

import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.OrCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.DayOfWeekCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.IntervalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.TimeWindowCondition;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Set;

/**
 * Utility class providing factory methods for creating time-based conditions
 * for the plugin scheduler system. These methods create start conditions with 
 * configurable parameters and appropriate caps.
 */
public final class TimeConditionUtil {
    
    // Maximum durations to prevent excessive sessions
    private static final Duration MAX_WEEKDAY_SESSION = Duration.ofHours(4);
    private static final Duration MAX_WEEKEND_SESSION = Duration.ofHours(6);
    private static final int MAX_DAILY_REPEATS = 5;
    private static final int MAX_WEEKLY_REPEATS = 20;
    
    // Private constructor to prevent instantiation
    private TimeConditionUtil() {}

    /**
     * Creates a day of week condition with configurable daily limits
     * 
     * @param maxRepeatsPerDay Maximum number of times to trigger per day (capped at 5)
     * @param days The days on which the condition should be active
     * @return A DayOfWeekCondition with the specified settings
     */
    public static DayOfWeekCondition createDailyLimitedCondition(int maxRepeatsPerDay, DayOfWeek... days) {
        // Cap daily repeats at the maximum value
        int cappedRepeats = Math.min(maxRepeatsPerDay, MAX_DAILY_REPEATS);
        return new DayOfWeekCondition(0, cappedRepeats, days);
    }
    
    /**
     * Creates a day of week condition with configurable weekly limits
     * 
     * @param maxRepeatsPerWeek Maximum number of times to trigger per week (capped at 20)
     * @param days The days on which the condition should be active
     * @return A DayOfWeekCondition with the specified settings
     */
    public static DayOfWeekCondition createWeeklyLimitedCondition(int maxRepeatsPerWeek, DayOfWeek... days) {
        // Cap weekly repeats at the maximum value
        int cappedRepeats = Math.min(maxRepeatsPerWeek, MAX_WEEKLY_REPEATS);
        return new DayOfWeekCondition(0, 0, cappedRepeats, days);
    }
    
    /**
     * Creates a day of week condition with both daily and weekly limits
     * 
     * @param maxRepeatsPerDay Maximum number of times to trigger per day (capped at 5)
     * @param maxRepeatsPerWeek Maximum number of times to trigger per week (capped at 20)
     * @param days The days on which the condition should be active
     * @return A DayOfWeekCondition with the specified settings
     */
    public static DayOfWeekCondition createDailyAndWeeklyLimitedCondition(
            int maxRepeatsPerDay, int maxRepeatsPerWeek, DayOfWeek... days) {
        // Cap daily and weekly repeats at the maximum values
        int cappedDailyRepeats = Math.min(maxRepeatsPerDay, MAX_DAILY_REPEATS);
        int cappedWeeklyRepeats = Math.min(maxRepeatsPerWeek, MAX_WEEKLY_REPEATS);
        return new DayOfWeekCondition(0, cappedDailyRepeats, cappedWeeklyRepeats, days);
    }
    
    /**
     * Creates a combined condition for running on weekdays with a specified session duration
     * 
     * @param sessionHours Duration of each session in hours (capped at 4 hours for weekdays)
     * @param maxRepeatsPerDay Maximum repeats per day (optional, defaults to 1)
     * @return A combined condition for weekday play
     */
    public static DayOfWeekCondition createWeekdaySessionCondition(float sessionHours, int maxRepeatsPerDay) {
        // Cap the session duration
        float cappedHours = Math.min(sessionHours, MAX_WEEKDAY_SESSION.toHours());
        int cappedRepeats = Math.min(maxRepeatsPerDay, MAX_DAILY_REPEATS);
        
        // Calculate minutes portion for partial hours
        int hours = (int)cappedHours;
        int minutes = (int)((cappedHours - hours) * 60);
        
        // Create the interval condition
        IntervalCondition sessionDuration = new IntervalCondition(
                Duration.ofHours(hours).plusMinutes(minutes));
        
        // Create day of week condition for weekdays
        DayOfWeekCondition weekdays = new DayOfWeekCondition(
                0, cappedRepeats, 
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, 
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);                
        weekdays.setIntervalCondition(sessionDuration);
        return weekdays;
    }
    
    /**
     * Creates a combined condition for running on weekdays with a specified session duration
     * Uses default of 1 repeat per day
     * 
     * @param sessionHours Duration of each session in hours (capped at 4 hours for weekdays)
     * @return A combined condition for weekday play
     */
    public static DayOfWeekCondition createWeekdaySessionCondition(float sessionHours) {
        return createWeekdaySessionCondition(sessionHours, 1);
    }
    
    /**
     * Creates a combined condition for running on weekends with a specified session duration
     * 
     * @param sessionHours Duration of each session in hours (capped at 6 hours for weekends)
     * @param maxRepeatsPerDay Maximum repeats per day (optional, defaults to 2)
     * @return A combined condition for weekend play
     */
    public static DayOfWeekCondition createWeekendSessionCondition(float sessionHours, int maxRepeatsPerDay) {
        // Cap the session duration
        float cappedHours = Math.min(sessionHours, MAX_WEEKEND_SESSION.toHours());
        int cappedRepeats = Math.min(maxRepeatsPerDay, MAX_DAILY_REPEATS);
        
        // Calculate minutes portion for partial hours
        int hours = (int)cappedHours;
        int minutes = (int)((cappedHours - hours) * 60);
        
        // Create the interval condition
        IntervalCondition sessionDuration = new IntervalCondition(
                Duration.ofHours(hours).plusMinutes(minutes));
        
        // Create day of week condition for weekends
        DayOfWeekCondition weekends = new DayOfWeekCondition(
                0, cappedRepeats, 
                DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
        weekends.setIntervalCondition(sessionDuration);
        
        
        return weekends;
    }
    
    /**
     * Creates a combined condition for running on weekends with a specified session duration
     * Uses default of 2 repeats per day
     * 
     * @param sessionHours Duration of each session in hours (capped at 6 hours for weekends)
     * @return A combined condition for weekend play
     */
    public static DayOfWeekCondition createWeekendSessionCondition(float sessionHours) {
        return createWeekendSessionCondition(sessionHours, 2);
    }
    
    /**
     * Creates a condition for randomized session durations on specified days
     * 
     * @param minSessionHours Minimum session hours
     * @param maxSessionHours Maximum session hours (capped based on weekday/weekend)
     * @param maxRepeatsPerDay Maximum repeats per day (capped at 5)
     * @param days The days on which the condition should be active
     * @return A combined condition with randomized session length
     */
    public static DayOfWeekCondition createRandomizedSessionCondition(
            float minSessionHours, float maxSessionHours, int maxRepeatsPerDay, DayOfWeek... days) {
        
        // Determine if the days contain only weekends, only weekdays, or mixed
        boolean hasWeekend = false;
        boolean hasWeekday = false;
        
        for (DayOfWeek day : days) {
            if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
                hasWeekend = true;
            } else {
                hasWeekday = true;
            }
        }
        
        // Apply appropriate caps based on day type
        float cappedMaxHours;
        if (hasWeekend && !hasWeekday) {
            // Weekend-only cap
            cappedMaxHours = Math.min(maxSessionHours, MAX_WEEKEND_SESSION.toHours());
        } else {
            // Apply weekday cap (more restrictive) if any weekday is included
            cappedMaxHours = Math.min(maxSessionHours, MAX_WEEKDAY_SESSION.toHours());
        }
        
        // Ensure min <= max
        float cappedMinHours = Math.min(minSessionHours, cappedMaxHours);
        
        // Create min/max durations
        int minHours = (int)cappedMinHours;
        int minMinutes = (int)((cappedMinHours - minHours) * 60);
        Duration minDuration = Duration.ofHours(minHours).plusMinutes(minMinutes);
        
        int maxHours = (int)cappedMaxHours;
        int maxMinutes = (int)((cappedMaxHours - maxHours) * 60);
        Duration maxDuration = Duration.ofHours(maxHours).plusMinutes(maxMinutes);
        
        // Create randomized interval condition
        IntervalCondition intervalCondition = IntervalCondition.createRandomized(
                minDuration, maxDuration);
        
        // Create day of week condition
        int cappedRepeats = Math.min(maxRepeatsPerDay, MAX_DAILY_REPEATS);
        DayOfWeekCondition dayCondition = new DayOfWeekCondition(0, cappedRepeats, days);
        dayCondition.setIntervalCondition(intervalCondition);
        
        
        return dayCondition;
    }
    
    /**
     * Creates a balanced weekly schedule with different session durations for different days
     * and an overall weekly limit.
     * 
     * @param weekdaySessionHours Session duration for weekdays (capped at 4 hours)
     * @param weekendSessionHours Session duration for weekends (capped at 6 hours)
     * @param weekdayRepeatsPerDay Maximum repeats per weekday (capped at 3)
     * @param weekendRepeatsPerDay Maximum repeats per weekend day (capped at 4)
     * @param weeklyLimit Overall weekly limit (capped at 20)
     * @return A condition that combines all these restrictions
     */
    public static AndCondition createBalancedWeeklySchedule(
            float weekdaySessionHours, float weekendSessionHours,
            int weekdayRepeatsPerDay, int weekendRepeatsPerDay, int weeklyLimit) {
        
        // Cap input values
        float cappedWeekdayHours = Math.min(weekdaySessionHours, MAX_WEEKDAY_SESSION.toHours());
        float cappedWeekendHours = Math.min(weekendSessionHours, MAX_WEEKEND_SESSION.toHours());
        int cappedWeekdayRepeats = Math.min(weekdayRepeatsPerDay, 3); // Stricter cap for weekdays
        int cappedWeekendRepeats = Math.min(weekendRepeatsPerDay, 4); // Looser cap for weekends
        int cappedWeeklyLimit = Math.min(weeklyLimit, MAX_WEEKLY_REPEATS);
        
        // Convert hours to durations
        int weekdayHours = (int)cappedWeekdayHours;
        int weekdayMinutes = (int)((cappedWeekdayHours - weekdayHours) * 60);
        Duration weekdayDuration = Duration.ofHours(weekdayHours).plusMinutes(weekdayMinutes);
        
        int weekendHours = (int)cappedWeekendHours;
        int weekendMinutes = (int)((cappedWeekendHours - weekendHours) * 60);
        Duration weekendDuration = Duration.ofHours(weekendHours).plusMinutes(weekendMinutes);
        
        // Create weekday condition
        DayOfWeekCondition weekdays = new DayOfWeekCondition(
                0, cappedWeekdayRepeats, 0,
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
        
        IntervalCondition weekdayInterval = new IntervalCondition(weekdayDuration);
        weekdays.setIntervalCondition(weekdayInterval);
        
        
        // Create weekend condition
        DayOfWeekCondition weekends = new DayOfWeekCondition(
                0, cappedWeekendRepeats, 0,
                DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
        
        IntervalCondition weekendInterval = new IntervalCondition(weekendDuration);
        weekends.setIntervalCondition(weekendInterval);
        
        


        // Create OR condition to combine weekday and weekend options
        OrCondition dayOptions = new OrCondition();
        dayOptions.addCondition(weekdays);
        dayOptions.addCondition(weekends);
        
        // Create weekly limit condition that applies to all days
        DayOfWeekCondition weeklyLimitCondition = new DayOfWeekCondition(
                0, 0, cappedWeeklyLimit, 
                DayOfWeek.values());
        
        // Combine day options with weekly limit
        AndCondition finalCondition = new AndCondition();
        finalCondition.addCondition(weeklyLimitCondition);
        finalCondition.addCondition(dayOptions);
        
        return finalCondition;
    }
    
    /**
     * Creates a scheduled time window condition based on time of day.
     * 
     * @param startHour Starting hour (0-23)
     * @param startMinute Starting minute (0-59)
     * @param endHour Ending hour (0-23)
     * @param endMinute Ending minute (0-59)
     * @param days Days of week to apply this schedule
     * @return A combined time window and day of week condition
     */
    public static AndCondition createTimeWindowSchedule(
            int startHour, int startMinute, int endHour, int endMinute, DayOfWeek... days) {
        
        // Validate and cap time values
        startHour = Math.min(Math.max(startHour, 0), 23);
        startMinute = Math.min(Math.max(startMinute, 0), 59);
        endHour = Math.min(Math.max(endHour, 0), 23);
        endMinute = Math.min(Math.max(endMinute, 0), 59);
        
        // Create time window condition
        TimeWindowCondition timeWindow = new TimeWindowCondition(
                LocalTime.of(startHour, startMinute),
                LocalTime.of(endHour, endMinute),
                LocalDate.now(),
                LocalDate.now().plus(1, ChronoUnit.YEARS),
                null, 1, 0);
        
        // Create day of week condition
        DayOfWeekCondition dayCondition = new DayOfWeekCondition(0, days);
        
        // Combine conditions
        AndCondition condition = new AndCondition();
        condition.addCondition(dayCondition);
        condition.addCondition(timeWindow);
        
        return condition;
    }
    
    /**
     * Creates a randomized time window schedule.
     * 
     * @param baseStartHour Base starting hour (0-23)
     * @param baseStartMinute Base starting minute (0-59)
     * @param baseEndHour Base ending hour (0-23)
     * @param baseEndMinute Base ending minute (0-59)
     * @param randomizeMinutes Amount to randomize times by (±minutes)
     * @param days Days of week to apply this schedule
     * @return A combined randomized time window and day of week condition
     */
    public static AndCondition createRandomizedTimeWindowSchedule(
            int baseStartHour, int baseStartMinute, int baseEndHour, int baseEndMinute,
            int randomizeMinutes, DayOfWeek... days) {
        
        // Validate and cap time values
        baseStartHour = Math.min(Math.max(baseStartHour, 0), 23);
        baseStartMinute = Math.min(Math.max(baseStartMinute, 0), 59);
        baseEndHour = Math.min(Math.max(baseEndHour, 0), 23);
        baseEndMinute = Math.min(Math.max(baseEndMinute, 0), 59);
        randomizeMinutes = Math.min(Math.max(randomizeMinutes, 0), 60);
        
        // Create time window condition
        TimeWindowCondition timeWindow = new TimeWindowCondition(
                LocalTime.of(baseStartHour, baseStartMinute),
                LocalTime.of(baseEndHour, baseEndMinute),
                LocalDate.now(),
                LocalDate.now().plus(1, ChronoUnit.YEARS),
                null, 1, 0);
        
        // Set randomization if requested
        if (randomizeMinutes > 0) {
            timeWindow.setRandomization(true, randomizeMinutes);
        }
        
        // Create day of week condition
        DayOfWeekCondition dayCondition = new DayOfWeekCondition(0, days);
        
        // Combine conditions
        AndCondition condition = new AndCondition();
        condition.addCondition(dayCondition);
        condition.addCondition(timeWindow);
        
        return condition;
    }
    
    /**
     * Creates a humanized play schedule that mimics natural human gaming patterns
     * with appropriate limits.
     * 
     * @param weekdayMaxHours Maximum session hours for weekdays
     * @param weekendMaxHours Maximum session hours for weekends
     * @param weeklyMaxRepeats Maximum weekly repeats overall
     * @return A realistic human-like play schedule
     */
    public static AndCondition createHumanizedPlaySchedule(
            float weekdayMaxHours, float weekendMaxHours, int weeklyMaxRepeats) {
        
        // Cap input values
        float cappedWeekdayHours = Math.min(weekdayMaxHours, MAX_WEEKDAY_SESSION.toHours());
        float cappedWeekendHours = Math.min(weekendMaxHours, MAX_WEEKEND_SESSION.toHours());
        int cappedWeeklyRepeats = Math.min(weeklyMaxRepeats, MAX_WEEKLY_REPEATS);
        
        // Calculate min duration as ~60% of max
        float weekdayMinHours = cappedWeekdayHours * 0.6f;
        float weekendMinHours = cappedWeekendHours * 0.6f;
        
        // Convert to Duration objects
        int weekdayMinH = (int)weekdayMinHours;
        int weekdayMinM = (int)((weekdayMinHours - weekdayMinH) * 60);
        Duration weekdayMinDuration = Duration.ofHours(weekdayMinH).plusMinutes(weekdayMinM);
        
        int weekdayMaxH = (int)cappedWeekdayHours;
        int weekdayMaxM = (int)((cappedWeekdayHours - weekdayMaxH) * 60);
        Duration weekdayMaxDuration = Duration.ofHours(weekdayMaxH).plusMinutes(weekdayMaxM);
        
        int weekendMinH = (int)weekendMinHours;
        int weekendMinM = (int)((weekendMinHours - weekendMinH) * 60);
        Duration weekendMinDuration = Duration.ofHours(weekendMinH).plusMinutes(weekendMinM);
        
        int weekendMaxH = (int)cappedWeekendHours;
        int weekendMaxM = (int)((cappedWeekendHours - weekendMaxH) * 60);
        Duration weekendMaxDuration = Duration.ofHours(weekendMaxH).plusMinutes(weekendMaxM);
        
        // Monday/Wednesday/Friday: 1 session per day, shorter
        DayOfWeekCondition mwfDays = new DayOfWeekCondition(
            0, 1, 0, // 1 per day, no specific weekly limit
            DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY
        );
        
        IntervalCondition mwfSession = IntervalCondition.createRandomized(
            weekdayMinDuration, weekdayMaxDuration
        );
        mwfDays.setIntervalCondition(mwfSession);
        
        
        
        
        // Tuesday/Thursday: 2 sessions per day, even shorter
        DayOfWeekCondition ttDays = new DayOfWeekCondition(
            0, 2, 0, // 2 per day, no specific weekly limit
            DayOfWeek.TUESDAY, DayOfWeek.THURSDAY
        );
        
        // Make TT sessions ~75% of MWF sessions
        Duration ttMinDuration = Duration.ofMillis((long)(weekdayMinDuration.toMillis() * 0.75));
        Duration ttMaxDuration = Duration.ofMillis((long)(weekdayMaxDuration.toMillis() * 0.75));
        
        IntervalCondition ttSession = IntervalCondition.createRandomized(
            ttMinDuration, ttMaxDuration
        );
        ttDays .setIntervalCondition(ttSession);
        
        
        
        
        // Weekend: 2-3 sessions per day, longer
        DayOfWeekCondition weekendDays = new DayOfWeekCondition(
            0, 3, 0, // Up to 3 per day
            DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
        );
        
        IntervalCondition weekendSession = IntervalCondition.createRandomized(
            weekendMinDuration, weekendMaxDuration
        );

        weekendDays.setIntervalCondition(weekendSession);
        
        
        // Create an OR condition for the day schedules
        OrCondition daySchedules = new OrCondition();
        daySchedules.addCondition(mwfDays);
        daySchedules.addCondition(ttDays);
        daySchedules.addCondition(weekendDays);
        
        // Apply weekly limit
        DayOfWeekCondition weeklyLimit = new DayOfWeekCondition(
            0, 0, cappedWeeklyRepeats, EnumSet.allOf(DayOfWeek.class)
        );
        
        // Combine with the weekly limit
        AndCondition finalSchedule = new AndCondition();
        finalSchedule.addCondition(weeklyLimit);
        finalSchedule.addCondition(daySchedules);
        
        return finalSchedule;
    }
    
    /**
     * Creates a "work-life balance" schedule that simulates a player with a daytime job,
     * playing evenings on weekdays and more on weekends.
     * 
     * @param weekdayMaxHours Maximum session hours for weekdays
     * @param weekendMaxHours Maximum session hours for weekends
     * @param weeklyMaxRepeats Maximum weekly repeats overall
     * @return A realistic work-life balance schedule
     */
    public static Condition createWorkLifeBalanceSchedule(
            float weekdayMaxHours, float weekendMaxHours, int weeklyMaxRepeats) {
        
        // Cap input values
        float cappedWeekdayHours = Math.min(weekdayMaxHours, MAX_WEEKDAY_SESSION.toHours());
        float cappedWeekendHours = Math.min(weekendMaxHours, MAX_WEEKEND_SESSION.toHours());
        int cappedWeeklyRepeats = Math.min(weeklyMaxRepeats, MAX_WEEKLY_REPEATS);
        
        // Calculate min duration as ~70% of max for more predictable evening sessions
        float weekdayMinHours = cappedWeekdayHours * 0.7f;
        float weekendMinHours = cappedWeekendHours * 0.6f; // More variation on weekends
        
        // Convert to Duration objects
        int weekdayMinH = (int)weekdayMinHours;
        int weekdayMinM = (int)((weekdayMinHours - weekdayMinH) * 60);
        Duration weekdayMinDuration = Duration.ofHours(weekdayMinH).plusMinutes(weekdayMinM);
        
        int weekdayMaxH = (int)cappedWeekdayHours;
        int weekdayMaxM = (int)((cappedWeekdayHours - weekdayMaxH) * 60);
        Duration weekdayMaxDuration = Duration.ofHours(weekdayMaxH).plusMinutes(weekdayMaxM);
        
        int weekendMinH = (int)weekendMinHours;
        int weekendMinM = (int)((weekendMinHours - weekendMinH) * 60);
        Duration weekendMinDuration = Duration.ofHours(weekendMinH).plusMinutes(weekendMinM);
        
        int weekendMaxH = (int)cappedWeekendHours;
        int weekendMaxM = (int)((cappedWeekendHours - weekendMaxH) * 60);
        Duration weekendMaxDuration = Duration.ofHours(weekendMaxH).plusMinutes(weekendMaxM);
        
        // Weekday schedule (Mon-Fri) with evening hours
        TimeWindowCondition eveningHours = new TimeWindowCondition(
            LocalTime.of(18, 0), // 6:00 PM
            LocalTime.of(23, 0), // 11:00 PM
            LocalDate.now(),
            LocalDate.now().plusYears(1),
            null, 1, 0
        );
        eveningHours.setRandomization(true, 30); // Randomize by ±30 minutes
        
        // MWF - lighter play (1 session)
        DayOfWeekCondition mwfDays = new DayOfWeekCondition(0, 1, 0, 
            DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY
        );
        
        // TT - slightly more play (2 sessions)
        DayOfWeekCondition ttDays = new DayOfWeekCondition(0, 2, 0,
            DayOfWeek.TUESDAY, DayOfWeek.THURSDAY
        );
        
        // Weekday session duration
        IntervalCondition weekdaySession = IntervalCondition.createRandomized(
            weekdayMinDuration, weekdayMaxDuration
        );
        
        // Combine MWF schedule
        AndCondition mwfSchedule = new AndCondition();
        mwfSchedule.addCondition(mwfDays);
        mwfSchedule.addCondition(eveningHours);
        mwfSchedule.addCondition(weekdaySession);
        
        // Combine TT schedule
        AndCondition ttSchedule = new AndCondition();
        ttSchedule.addCondition(ttDays);
        ttSchedule.addCondition(eveningHours);
        ttSchedule.addCondition(weekdaySession);
        
        // Weekend schedule (Sat-Sun)
        DayOfWeekCondition weekendDays = new DayOfWeekCondition(0, 3, 0,
            DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
        );
        
        // Weekend has more flexible hours
        TimeWindowCondition flexibleHours = new TimeWindowCondition(
            LocalTime.of(10, 0),  // 10:00 AM
            LocalTime.of(23, 59), // 11:59 PM
            LocalDate.now(),
            LocalDate.now().plusYears(1),
            null, 1, 0
        );
        flexibleHours.setRandomization(true, 60); // Randomize by ±60 minutes
        
        // Weekend session duration
        IntervalCondition weekendSession = IntervalCondition.createRandomized(
            weekendMinDuration, weekendMaxDuration
        );
        
        // Combine weekend schedule
        AndCondition weekendSchedule = new AndCondition();
        weekendSchedule.addCondition(weekendDays);
        weekendSchedule.addCondition(flexibleHours);
        weekendSchedule.addCondition(weekendSession);
        
        // Combine all daily schedules with OR
        OrCondition allDaySchedules = new OrCondition();
        allDaySchedules.addCondition(mwfSchedule);
        allDaySchedules.addCondition(ttSchedule);
        allDaySchedules.addCondition(weekendSchedule);
        
        // Apply weekly limit
        DayOfWeekCondition weeklyLimit = new DayOfWeekCondition(
            0, 0, cappedWeeklyRepeats, EnumSet.allOf(DayOfWeek.class)
        );
        
        // Apply weekly limit to the combined schedule
        AndCondition finalSchedule = new AndCondition();
        finalSchedule.addCondition(weeklyLimit);
        finalSchedule.addCondition(allDaySchedules);
        
        return finalSchedule;
    }
    
    /**
     * Provides diagnostic information about an AndCondition, explaining whether
     * each component is satisfied and why.
     * 
     * @param condition The AndCondition to diagnose
     * @return A detailed diagnostic report
     */
    public static String diagnoseCombinedCondition(AndCondition condition) {
        StringBuilder sb = new StringBuilder();
        sb.append("Combined condition status: ").append(condition.isSatisfied() ? "SATISFIED" : "NOT SATISFIED").append("\n");
        sb.append("Analyzing individual conditions:\n");
        
        for (int i = 0; i < condition.getConditions().size(); i++) {
            Condition subCondition = condition.getConditions().get(i);
            boolean satisfied = subCondition.isSatisfied();
            
            sb.append(i + 1).append(". ");
            sb.append(subCondition.getClass().getSimpleName()).append(": ");
            sb.append(satisfied ? "SATISFIED" : "NOT SATISFIED").append("\n");
            sb.append("   - ").append(subCondition.getDescription().replace("\n", "\n   - ")).append("\n");
            
            if (!satisfied) {
                // Special handling for different condition types
                if (subCondition instanceof DayOfWeekCondition) {
                    DayOfWeekCondition dayCondition = (DayOfWeekCondition) subCondition;
                    sb.append("   - Today is not an active day or has reached daily/weekly limit\n");
                    sb.append("   - Current day usage: ").append(
                        dayCondition.getResetCountForDate(LocalDate.now())).append("\n");
                    sb.append("   - Daily limit reached: ").append(dayCondition.isDailyLimitReached()).append("\n");
                    sb.append("   - Current week usage: ").append(dayCondition.getCurrentWeekResetCount()).append("\n");
                    sb.append("   - Weekly limit reached: ").append(dayCondition.isWeeklyLimitReached()).append("\n");
                    
                    // Show next trigger day
                    dayCondition.getCurrentTriggerTime().ifPresent(time -> 
                        sb.append("   - Next active day: ").append(time.toLocalDate()).append("\n"));
                }
                else if (subCondition instanceof IntervalCondition) {
                    IntervalCondition intervalCondition = (IntervalCondition) subCondition;
                    sb.append("   - Interval not yet elapsed\n");
                    intervalCondition.getCurrentTriggerTime().ifPresent(time -> 
                        sb.append("   - Next trigger time: ").append(time).append("\n"));
                }
                else if (subCondition instanceof TimeWindowCondition) {
                    TimeWindowCondition timeWindow = (TimeWindowCondition) subCondition;
                    sb.append("   - Outside of configured time window\n");
                    sb.append("   - Current time window: ")
                      .append(timeWindow.getStartTime()).append(" - ")
                      .append(timeWindow.getEndTime()).append("\n");
                }
            }
        }
        
        return sb.toString();
    }
}