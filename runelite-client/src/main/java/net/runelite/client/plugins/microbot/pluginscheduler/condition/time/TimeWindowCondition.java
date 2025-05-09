package net.runelite.client.plugins.microbot.pluginscheduler.condition.time;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ConditionType;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.enums.RepeatCycle;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import java.util.logging.Level;

@Data
@EqualsAndHashCode(callSuper = false, exclude = {   
})
@Slf4j
public class TimeWindowCondition extends TimeCondition {
    
    public static String getVersion() {
        return "0.0.1";
    }
    // Time window bounds (daily start/end times)
    private final LocalTime startTime;
    private final LocalTime endTime;

    // Date range for validity period 
    private final LocalDate startDate;
    private final LocalDate endDate;
    
    // Repeat cycle configuration
    private final RepeatCycle repeatCycle;
    private final int repeatIntervalUnit; //based on the repeat cycle, defines the interval unit (e.g., days, weeks, etc.)
    

    // Next window tracking (for non-daily cycles)
    private transient LocalDateTime currentStartDateTime;
    private transient LocalDateTime currentEndDateTime;
   
    // Randomization
    private transient boolean useRandomization = false;
    private transient int randomizeMinutes = 0; // Minutes to randomize start/end by
    
    // Cached timezone for computation - not serialized
    private transient ZoneId zoneId;


    private transient int randomStartMinutes = 0; // Minutes to randomize start/end by
    private transient int randomEndMinutes = 0; // Minutes to randomize start/end by

    private transient int transientNumberOfResetsWithinDailyInterval = 0; // Number of resets since last calculation

    
    /**
     * Creates a time window condition with just daily start and end times.
     * Uses default values for other parameters: current date for start date, 
     * one month in the future for end date, daily repeat cycle, and unlimited repeats.
     *
     * @param startTime The daily start time of the window
     * @param endTime The daily end time of the window
     */
    public TimeWindowCondition(LocalTime startTime, LocalTime endTime) {
        this(
            startTime, 
            endTime, 
            LocalDate.now(), 
            LocalDate.now().plusMonths(1),
            RepeatCycle.DAYS,
            1,
            0// 0 means infinity 
        );
    }

    /**
     * Creates a time window condition with all parameters specified.
     * This is the full constructor that allows complete configuration of the time window.
     *
     * @param startTime The daily start time of the window
     * @param endTime The daily end time of the window
     * @param startDate The earliest date the window can be active
     * @param endDate The latest date the window can be active
     * @param repeatCycle The cycle type for window repetition (DAYS, WEEKS, etc.)
     * @param repeatIntervalUnit The interval between repetitions (e.g., 2 for every 2 days)
     * @param maximumNumberOfRepeats Maximum number of times this condition can trigger (0 for unlimited)
     */
    public TimeWindowCondition(
            LocalTime startTime, 
            LocalTime endTime, 
            LocalDate startDate,
            LocalDate endDate,
            RepeatCycle repeatCycle,
            int repeatIntervalUnit,
            long maximumNumberOfRepeats) {
        super(maximumNumberOfRepeats);
                
        this.startTime = startTime;
        this.endTime = endTime;
        this.startDate = startDate;
        this.endDate = endDate;
        this.repeatCycle = repeatCycle;
        this.repeatIntervalUnit = Math.max(1, repeatIntervalUnit); // Ensure positive interval
        this.zoneId = ZoneId.systemDefault(); // Initialize with system default
        this.lastValidResetTime = LocalDateTime.now();
        transientNumberOfResetsWithinDailyInterval = 0;
        
        // Initialize next window times based on repeat cycle
        calculateNextWindow();
    }
    /**
     * Factory method to create a simple daily time window that repeats every day.
     * Creates a window that starts and ends at the specified times each day,
     * valid for one year from the current date.
     *
     * @param startTime The daily start time of the window
     * @param endTime The daily end time of the window
     * @return A configured TimeWindowCondition for daily repetition
     */
    public static TimeWindowCondition createDaily(LocalTime startTime, LocalTime endTime) {
        return new TimeWindowCondition(
            startTime, 
            endTime, 
            LocalDate.now(), 
            LocalDate.now().plusYears(1),
            RepeatCycle.DAYS,
            1,
            0// 0 means infinity
        );
    }

    /**
     * {@inheritDoc}
     * Returns the type of this condition, which is TIME.
     *
     * @return The condition type ConditionType.TIME
     */
    @Override
    public ConditionType getType() {
        return ConditionType.TIME;
    }
    /**
     * Gets the timezone used for time calculations in this condition.
     * 
     * @return The ZoneId representing the timezone
     */
    public ZoneId getZoneId() {        
        return zoneId;
    }

    /**
     * Sets the timezone to use for time calculations in this condition.
     * Changes to the timezone will affect when the time window activates.
     * 
     * @param zoneId The timezone to use for calculations
     */
    public void setZoneId(ZoneId zoneId) {
        this.zoneId = zoneId;        
    }

   /**
     * Calculate the next window start and end times based on current time and reset settings
     */
    private void calculateNextWindow() {
        ZonedDateTime now = ZonedDateTime.now(getZoneId());
        LocalDateTime nowLocal = now.toLocalDateTime();
        LocalDateTime referenceTime = lastValidResetTime != null ? lastValidResetTime : nowLocal;
        
        LocalDateTime todayStartDateTime = LocalDateTime.of(nowLocal.toLocalDate(), startTime);
        LocalDateTime todayEndDateTime = LocalDateTime.of(nowLocal.toLocalDate(), endTime);
        if(Microbot.isDebug() ) log.info("Calculating next window - current: \n" +  this,Level.INFO);
        
        switch (repeatCycle) {
            case ONE_TIME:
                calculateOneTimeWindow(referenceTime);
                break;
            case DAYS:              
                calculateCycleWindow(referenceTime);                
                break;
            case WEEKS:
                calculateCycleWindow(referenceTime);
                break;
            case MINUTES:
            case HOURS:
                calculateCycleWindow(referenceTime);
                break;
            default:
                log.warn("Unsupported repeat cycle: {}", repeatCycle);
                break;
        }
        if(Microbot.isDebug() ) log.info("After calculate new cycle window : \n" +  this,Level.INFO);
        // Apply randomization if enabled
        if (useRandomization && randomizeMinutes > 0 && currentStartDateTime != null && this.currentEndDateTime != null) {        
            currentStartDateTime = currentStartDateTime.plusMinutes(randomStartMinutes);
            if (!LocalDateTime.of(endDate, endTime).isBefore(this.currentEndDateTime.plusMinutes(randomEndMinutes))) {                
                this.currentEndDateTime = this.currentEndDateTime.plusMinutes(randomEndMinutes);
            }else {
                this.currentEndDateTime = this.currentEndDateTime.minusMinutes(randomEndMinutes);
            }
            
        }
        LocalDateTime lastEnd = LocalDateTime.of(endDate, endTime);
        if (currentStartDateTime != null) {
            if (this.currentStartDateTime.isAfter(lastEnd)){
                this.currentStartDateTime = null;
                this.currentEndDateTime = null;
            }
        }
        if(Microbot.isDebug() ) log.info("Calculating done -  new time window: \n" +  this,Level.INFO);
    }

    /**
     * Calculates window for ONE_TIME repeat cycle
     */
    private void calculateOneTimeWindow(LocalDateTime referenceTime) {
        ZonedDateTime now = ZonedDateTime.now(getZoneId());
        LocalDateTime nowLocal = now.toLocalDateTime();
        LocalDateTime todayStartDateTime = LocalDateTime.of(nowLocal.toLocalDate(), startTime);
        LocalDateTime todayEndDateTime = LocalDateTime.of(nowLocal.toLocalDate(), endTime);
        if (transientNumberOfResetsWithinDailyInterval == 0 ){
            if (todayEndDateTime.isBefore(todayStartDateTime)) {
                todayEndDateTime = todayEndDateTime.plusDays(1);
            }
            if (referenceTime.isAfter(todayStartDateTime) && referenceTime.isBefore(todayEndDateTime)) {
                this.currentStartDateTime = todayStartDateTime;
                this.currentEndDateTime = todayEndDateTime;
            } else {
                this.currentStartDateTime = todayEndDateTime.plusDays(1);
                this.currentEndDateTime = todayEndDateTime.plusDays(1);
                
            }
        }else{
            // If the reset time is after the end of the window, set to null
            if (lastValidResetTime.isAfter(currentEndDateTime)) {
                this.currentStartDateTime = null;
                this.currentEndDateTime = null;
            } else {
                // wait until we are outside current vaild window
            }        
        }
    }

    

    /**
     * Calculates window for sub-day repeat cycles (MINUTES, HOURS)
     */
    private void calculateCycleWindow(LocalDateTime referenceTime) {
        ZonedDateTime now = ZonedDateTime.now(getZoneId());
        LocalDateTime nowLocal = now.toLocalDateTime();
        // First, determine the bounds of today's overall window
        LocalDate today = now.toLocalDate();
        LocalDateTime currentDayWindowStart = LocalDateTime.of(today, startTime);
        LocalDateTime currentDayWindowEnd = LocalDateTime.of(today, endTime);
        
        // Handle cross-midnight windows
        if (currentDayWindowEnd.isBefore(currentDayWindowStart)) {
            currentDayWindowEnd = currentDayWindowEnd.plusDays(1);
        }
                

        
                        
        this.currentStartDateTime = calculateNextStartWindow(referenceTime);        
        if (Microbot.isDebug()) log.info("calculation of new cycle window after calculation of next start window:\n {}", this);
        
        
        // If next interval starts after the outer window end, it's not valid today      
        LocalDate startday = currentStartDateTime.toLocalDate();           
        this.currentEndDateTime = LocalDateTime.of(startday, endTime);        
        if (currentEndDateTime.isBefore(currentStartDateTime)) {
            this.currentEndDateTime = currentEndDateTime.plusDays(1);
        }      
    }
    
    /**
     * Helper method to calculate interval from a reference point
     */
    private LocalDateTime calculateNextStartWindow( LocalDateTime referenceTime) {
        LocalDateTime nextStartTime;
        ZonedDateTime now = ZonedDateTime.now(getZoneId());        
        LocalDateTime nowLocal = now.toLocalDateTime();
        LocalDate today = now.toLocalDate();
        LocalDateTime currentDayWindowStart = LocalDateTime.of(today, startTime);
        LocalDateTime currentDayWindowEnd = LocalDateTime.of(today, endTime);
        if (this.currentValidResetCount > 0) {
            
        
            switch (repeatCycle) {
                case ONE_TIME:
                    nextStartTime = referenceTime;
                    break;
                case MINUTES:
                    nextStartTime = referenceTime.plusMinutes(repeatIntervalUnit);
                    break;
                case HOURS:
                    nextStartTime = referenceTime.plusHours(repeatIntervalUnit);
                    break;
                case DAYS:                                        
                    nextStartTime = referenceTime.plusDays(repeatIntervalUnit);
                    break;
                case WEEKS:
                    nextStartTime =  referenceTime.plusWeeks(repeatIntervalUnit);                                
                default:
                    log.warn("Unsupported repeat cycle: {}", repeatCycle);
                    nextStartTime=  referenceTime;
            }
        }else {
            if (nowLocal.isBefore(currentDayWindowEnd)) {                
                nextStartTime = currentDayWindowStart;
            } else {
                nextStartTime = currentDayWindowStart.plusDays(1);
            }
        }
        
        if (nextStartTime.isBefore(currentDayWindowStart)) {
            nextStartTime = currentDayWindowStart;
        }else if (nextStartTime.isBefore(currentDayWindowEnd)) {
            
        }else if (nextStartTime.isAfter(currentDayWindowEnd)) {
            LocalDate nextDay = now.toLocalDate().plusDays(1);
            nextStartTime = LocalDateTime.of(nextDay, startTime);
        }
        return nextStartTime;
        
       
    }

   
    /**
     * {@inheritDoc}
     * Determines if the current time is within the configured time window.
     * Checks if the current time is after the start time and before the end time
     * of the current window, and if the condition can still trigger.
     *
     * @return true if the current time is within the active window and the condition can trigger,
     *         false otherwise
     */
    @Override
    public boolean isSatisfied() {
        if (!canTriggerAgain()) {
            return false;
        }
        ZonedDateTime now = ZonedDateTime.now(getZoneId());
        LocalDateTime nowLocal = now.toLocalDateTime();
        LocalDate today = now.toLocalDate();
        LocalDate dayBefore = today.minusDays(1);
        LocalDate nextDay = today.plusDays(1);
        LocalDateTime currentDayWindowStart = LocalDateTime.of(today, startTime);
        LocalDateTime currentDayWindowEnd = LocalDateTime.of(today, endTime); 
        LocalDateTime beforeDayWindowStart = LocalDateTime.of( dayBefore,  startTime);            
        LocalDateTime beforeDayWindowEnd = LocalDateTime.of(dayBefore, endTime);    

        LocalDateTime nextDayWindowStart = LocalDateTime.of(nextDay, startTime);
        LocalDateTime nextDayWindowEnd = LocalDateTime.of(nextDay, endTime);
        // For non-daily or interval > 1 day cycles, check against calculated next window
        if (currentStartDateTime == null || currentEndDateTime == null) {                
            
            return false; // No more windows in range
            
        }
        if ((currentStartDateTime.isAfter(beforeDayWindowStart) && currentEndDateTime.isBefore(beforeDayWindowEnd))) {
            lastValidResetTime = currentDayWindowStart;
            this.calculateNextWindow();
            
        }
        // Check if window has passed - but don't auto-recalculate
        // Let the scheduler decide when to reset the condition
        if (nowLocal.isAfter(currentEndDateTime)) {            
            return false;
        }
        
        // Check if within next window
        return nowLocal.isAfter(currentStartDateTime) && nowLocal.isBefore(currentEndDateTime);
               
    }

    /**
     * {@inheritDoc}
     * Calculates progress through the current time window as a percentage.
     * Returns 0% if outside the window or 0-100% based on how much of the window has elapsed.
     *
     * @return A percentage from 0-100 indicating progress through the current time window
     */
    @Override
    public double getProgressPercentage() {
        if (!isSatisfied()) {
            return 0.0;
        }
        
        // If our window bounds aren't set, we can't calculate progress
        if (currentStartDateTime == null || currentEndDateTime == null) {
            log.debug("Unable to calculate progress - window bounds are null");
            return 0.0;
        }
        
        ZonedDateTime now = ZonedDateTime.now(getZoneId());
        LocalDateTime nowLocal = now.toLocalDateTime();
        
        // Calculate total window duration in seconds
        long totalDuration = ChronoUnit.SECONDS.between(currentStartDateTime, currentEndDateTime);
        if (totalDuration <= 0) {
            log.debug("Invalid window duration: {} seconds", totalDuration);
            return 0.0;
        }
        
        // Calculate elapsed duration in seconds
        long elapsedDuration = ChronoUnit.SECONDS.between(currentStartDateTime, nowLocal);
        
        // Calculate percentage - cap at 100%
        double percentage = Math.min(100.0, (elapsedDuration * 100.0) / totalDuration);
        
        log.debug("Progress calculation: {}% ({}/{} seconds)", 
            String.format("%.1f", percentage),
            elapsedDuration, 
            totalDuration);
        
        return percentage;
    }

    /**
     * {@inheritDoc}
     * Provides a user-friendly description of this time window condition.
     * Includes the time range, repeat information, and timezone.
     *
     * @return A human-readable string describing the time window parameters
     */
    @Override
    public String getDescription() {
        StringBuilder description = new StringBuilder("Time Window: ");
        
        // Format times
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        String timeRangeStr = startTime.format(timeFormatter) + " to " + endTime.format(timeFormatter);
        description.append(timeRangeStr);
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            description.append(" ( Between: ")
                      .append(startDate.format(dateFormatter))
                      .append(" to ")
                      .append(endDate.format(dateFormatter))
                      .append(")");
        // Add repeat information
        if (repeatCycle != RepeatCycle.ONE_TIME) {
            
            description.append(" (")
                      .append(repeatCycle.getDisplayName().replace("X", Integer.toString(repeatIntervalUnit)))
                      .append(")");
        }
        
        // Add timezone information for clarity
        //description.append(" [").append(getZoneId().getId()).append("]");
        description.append("\n"+super.getDescription());
        return description.toString();
    }

    
    /**
     * Configures time randomization for this window.
     * When enabled, the start and end times will be adjusted by a random
     * amount within the specified range each time the condition is reset.
     *
     * @param useRandomization Whether to enable time randomization
     * @param randomizeMinutes Maximum number of minutes to randomize by (plus or minus)
     */
    public void setRandomization(boolean useRandomization, int randomizeMinutes) {
        this.useRandomization = useRandomization;
        this.randomizeMinutes = Math.max(0, randomizeMinutes);
    }

   
    
    /**
     * Custom deserialization method to initialize transient fields.
     * This ensures that the timezone is properly set after deserialization.
     *
     * @return The properly initialized deserialized object
     */
    public Object readResolve() {
        // Initialize timezone if needed
        if (zoneId == null) {
            zoneId = ZoneId.systemDefault();
        }        
        return this;
    }

    /**
     * Resets the time window condition with default settings.
     * Calculates the next time window based on current time and settings.
     * This is a shorthand for reset(false).
     */
    public void reset() {
        reset(false);
    }

    /**
     * Resets the time window condition with specified randomization settings.
     * Updates the randomization parameters and calculates the next time window.
     *
     * @param randomize Whether to apply randomization to window times
     * @param randomizeMinutes Maximum minutes to randomize by (plus or minus)
     */
    public void reset(boolean randomize, int randomizeMinutes) {
        this.useRandomization = randomize;
        this.randomizeMinutes = randomizeMinutes;
        reset(randomize);
    }
  

    /**
     * {@inheritDoc}
     * Resets the time window condition and calculates the next active window.
     * Updates the reset count, applies randomization if enabled, and advances
     * the window if necessary based on current time.
     *
     * @param randomize Whether to apply randomization to window times
     */
    @Override
    public void reset(boolean randomize) {
        this.useRandomization = randomize;        
        // Store current time as the reset reference
        log.debug("Last reset time: {}", lastValidResetTime);
        this.lastValidResetTime = LocalDateTime.now();                                
        ZonedDateTime now = ZonedDateTime.now(getZoneId());
        LocalDateTime nowLocal = now.toLocalDateTime();        
        // If we are have a current window and we are within the window or after it, we need to force an advance
        boolean needsAdvance = currentStartDateTime != null && nowLocal.isAfter(currentStartDateTime);                           
        // If this the next  start window that's passed or any window that needs advancing
        if (needsAdvance && canTriggerAgain() ) {     
            this.currentValidResetCount++;       
            calculateNextWindow();            
        }          
        if (nowLocal.isAfter(this.currentStartDateTime) && nowLocal.isBefore(this.currentEndDateTime)) {
            transientNumberOfResetsWithinDailyInterval++;
        }else {
            transientNumberOfResetsWithinDailyInterval = 0;
        }       
        // Log the new window for debugging
        if (this.currentStartDateTime != null && this.currentEndDateTime != null) {
            log.debug("Next window after reset: {} to {}", 
                DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(this.currentStartDateTime),
                DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(this.currentEndDateTime));
        } else {
            log.debug("No next window available after reset");
        }
    }
    
    /**
     * {@inheritDoc}
     * Indicates whether this condition uses randomization.
     *
     * @return true if randomization is enabled, false otherwise
     */
    @Override
    public boolean isUseRandomization() {
        return useRandomization;
    }

    /**
     * {@inheritDoc}
     * Calculates the next time this condition will be satisfied (the start of the next window).
     * If already within a window, returns a time slightly in the past to indicate the condition
     * is currently satisfied.
     *
     * @return An Optional containing the time when the next window starts,
     *         or empty if no future windows are scheduled or the condition cannot trigger again
     */
    @Override
    public Optional<ZonedDateTime> getCurrentTriggerTime() {
        if (currentStartDateTime == null || currentEndDateTime == null || !canTriggerAgain()) {
            return Optional.empty();
        }
        ZonedDateTime now = ZonedDateTime.now(getZoneId());
        
        // If the condition is already satisfied (we're in the window), return the current time
        if (isSatisfied()) {
            assert(!currentStartDateTime.isAfter(now.toLocalDateTime()) && !currentEndDateTime.isBefore(now.toLocalDateTime()));
            return Optional.of(currentStartDateTime.atZone(getZoneId())); // Slightly in the past to indicate "ready now"
        }
                                
        // If our window calculation failed or hasn't been done, calculate it
        if (currentStartDateTime == null) {
            return Optional.empty();
        }else{
            return Optional.of(currentStartDateTime.atZone(getZoneId()));    
        }                        
    }
    @Override
    public boolean canTriggerAgain(){

        boolean canTrigger = super.canTriggerAgain();
        
        LocalDateTime lastDateTime = LocalDateTime.of( endDate, endTime);
        if (canTrigger ) {
            ZonedDateTime now = ZonedDateTime.now(getZoneId());
            LocalDateTime nowLocal = now.toLocalDateTime();
            return nowLocal.isBefore(lastDateTime);
        }
        return canTrigger;

    }

    /**
     * {@inheritDoc}
     * Generates a detailed string representation of this time window condition.
     * Includes configuration, status, window times, randomization settings,
     * and trigger count information formatted with visual separators.
     *
     * @return A multi-line string representation with detailed state information
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        
        // Basic information
        sb.append("TimeWindowCondition:\n");
        sb.append("  ┌─ Configuration ─────────────────────────────\n");
        sb.append("  │ Time Range: ").append(startTime.format(timeFormatter))
          .append(" to ").append(endTime.format(timeFormatter)).append("\n");
        sb.append("  │ Date Range: ").append(startDate.format(dateFormatter))
          .append(" to ").append(endDate.format(dateFormatter)).append("\n");
        sb.append("  │ Repeat: ").append(repeatCycle)
          .append(", Unit: ").append(repeatIntervalUnit).append("\n");
        sb.append("  │ Timezone: ").append(getZoneId().getId()).append("\n");
        
        // Status information
        sb.append("  ├─ Status ──────────────────────────────────\n");
        sb.append("  │ Satisfied: ").append(isSatisfied()).append("\n");
        if (currentStartDateTime != null && currentEndDateTime != null) {
            sb.append("  │ Current Window: ").append(currentStartDateTime.format(dateTimeFormatter))
              .append("\n  │ To: ").append(currentEndDateTime.format(dateTimeFormatter)).append("\n");
        } else if (currentStartDateTime != null) {
            sb.append("  │ Current Window: ").append(currentStartDateTime.format(dateTimeFormatter))
              .append("\n  │ To: Not available\n");
        } else {
            sb.append("  │ Current Window: Not available\n");
        }
        if (isSatisfied()) {
            sb.append("  │ Progress: ").append(String.format("%.1f%%", getProgressPercentage())).append("\n");
        }
        
        // Randomization
        sb.append("  ├─ Randomization ────────────────────────────\n");
        sb.append("  │ Randomization: ").append(useRandomization ? "Enabled" : "Disabled").append("\n");
        if (useRandomization) {
            sb.append("  │ Random Range: ±").append(randomizeMinutes).append(" minutes\n");
            sb.append("  │ Applied Offset: Start ").append(randomStartMinutes)
              .append(" min, End ").append(randomEndMinutes).append(" min\n");
        }
        
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
        sb.append("    Daily Reset Count: ").append(transientNumberOfResetsWithinDailyInterval).append("\n");
        sb.append("    Can Trigger Again: ").append(canTriggerAgain()).append("\n");
        
        return sb.toString();
    }
    
    /**
     * Provides a detailed description of the time window condition with status information.
     * Includes the window times, repeat cycle, current status, progress, randomization,
     * and tracking information in a human-readable format.
     *
     * @return A detailed multi-line string with current status and configuration details
     */
    public String getDetailedDescription() {
        StringBuilder sb = new StringBuilder();
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        
        // Basic description
        sb.append("Time Window Condition: Active from ").append(startTime.format(timeFormatter))
          .append(" to ").append(endTime.format(timeFormatter)).append("\n");
        
        // Repeat cycle
        if (repeatCycle == RepeatCycle.ONE_TIME) {
            sb.append("Schedule: One time only (").append(startDate.format(dateFormatter))
              .append(" - ").append(endDate.format(dateFormatter)).append(")\n");
        } else {
            sb.append("Schedule: Repeats every ").append(repeatIntervalUnit).append(" ")
              .append(repeatCycle.toString().toLowerCase()).append("\n");
            sb.append("Valid period: ").append(startDate.format(dateFormatter))
              .append(" - ").append(endDate.format(dateFormatter)).append("\n");
        }
        
        // Status information
        boolean satisfied = isSatisfied();
        sb.append("Status: ").append(satisfied ? "Active (in time window)" : "Inactive (outside time window)").append("\n");
        
        // Current window information
        ZonedDateTime now = ZonedDateTime.now(getZoneId());
        LocalDateTime nowLocal = now.toLocalDateTime();
        
        if (currentStartDateTime != null && currentEndDateTime != null) {
            sb.append("Current window: ").append(currentStartDateTime.format(dateTimeFormatter))
              .append(" to ").append(currentEndDateTime.format(dateTimeFormatter)).append("\n");
              
            if (nowLocal.isAfter(currentStartDateTime) && nowLocal.isBefore(currentEndDateTime)) {
                sb.append("Time remaining: ")
                  .append(ChronoUnit.MINUTES.between(nowLocal, currentEndDateTime))
                  .append(" minutes\n");
                sb.append("Progress: ").append(String.format("%.1f%%", getProgressPercentage())).append("\n");
            } else if (nowLocal.isBefore(currentStartDateTime)) {
                sb.append("Window starts in: ")
                  .append(ChronoUnit.MINUTES.between(nowLocal, currentStartDateTime))
                  .append(" minutes\n");
            } else {
                sb.append("Window has passed\n");
            }
        } else {
            sb.append("No active window available\n");
        }
        
        // Randomization
        if (useRandomization) {
            sb.append("Randomization: Enabled (±").append(randomizeMinutes).append(" minutes)\n");
            if (randomStartMinutes != 0 || randomEndMinutes != 0) {
                sb.append("Current offsets: Start ").append(randomStartMinutes)
                  .append(" min, End ").append(randomEndMinutes).append(" min\n");
            }
        } else {
            sb.append("Randomization: Disabled\n");
        }
        
        // Reset tracking
        sb.append("Reset count: ").append(currentValidResetCount);
        if (getMaximumNumberOfRepeats() > 0) {
            sb.append("/").append(getMaximumNumberOfRepeats());
        } else {
            sb.append(" (unlimited)");
        }
        sb.append("\n");
        
        if (lastValidResetTime != null) {
            sb.append("Last reset: ").append(lastValidResetTime.format(dateTimeFormatter)).append("\n");
        }
        
        // Timezone information
        sb.append("Timezone: ").append(getZoneId().getId()).append("\n");
        
        return sb.toString();
    }
}