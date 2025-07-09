package net.runelite.client.plugins.microbot.pluginscheduler.condition.time;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ConditionType;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.enums.RepeatCycle;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.ui.TimeConditionPanelUtil;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

import java.time.Duration;
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
@EqualsAndHashCode(callSuper = false, exclude = {   })
@Slf4j
public class TimeWindowCondition extends TimeCondition {
    
    // Constants for unlimited date ranges
    public static final LocalDate UNLIMITED_START_DATE = LocalDate.of(1900, 1, 1);
    public static final LocalDate UNLIMITED_END_DATE = LocalDate.of(2100, 12, 31);
    
    public static String getVersion() {
        return "0.0.3";
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
    //@Getter
    //@Setter
    
    @Getter
    @Setter
    private transient LocalDateTime currentEndDateTime;
   
    // Randomization
    private boolean useRandomization = false;
    private int randomizerValue = 0; // Randomization value, depends on the repeat cycle
    // randomizerValueUnit is now automatically determined based on repeatCycle - no longer stored as field
    // Cached timezone for computation - not serialized
    private transient ZoneId zoneId;



    @Getter
    @Setter
    private transient int transientNumberOfResetsWithinDailyInterval = 0; // Number of resets since last calculation

    /**
     * Checks if the start date represents an unlimited (no restriction) start date
     * @return true if start date is unlimited
     */
    public boolean isUnlimitedStartDate() {
        return startDate != null && startDate.equals(UNLIMITED_START_DATE);
    }
    
    /**
     * Checks if the end date represents an unlimited (no restriction) end date
     * @return true if end date is unlimited
     */
    public boolean isUnlimitedEndDate() {
        return endDate != null && endDate.equals(UNLIMITED_END_DATE);
    }
    
    /**
     * Checks if this condition has unlimited date range (both start and end are unlimited)
     * @return true if both dates are unlimited
     */
    public boolean hasUnlimitedDateRange() {
        return isUnlimitedStartDate() && isUnlimitedEndDate();
    }

    
    /**
     * Creates a time window condition with just daily start and end times.
     * Uses unlimited date range (no start/end date restrictions), daily repeat cycle, and unlimited repeats.
     *
     * @param startTime The daily start time of the window
     * @param endTime The daily end time of the window
     */
    public TimeWindowCondition(LocalTime startTime, LocalTime endTime) {
        this(
            startTime, 
            endTime, 
            UNLIMITED_START_DATE, 
            UNLIMITED_END_DATE,
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
            long maximumNumberOfRepeats
            ) {
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
        this.randomizerValue = 0;
        this.useRandomization = false;
        
        // Initialize next window times based on repeat cycle
        calculateNextWindow(getNow().toLocalDateTime());
    }
    /**
     * Factory method to create a simple daily time window that repeats every day.
     * Creates a window that starts and ends at the specified times each day,
     * with unlimited date range (no start/end date restrictions).
     *
     * @param startTime The daily start time of the window
     * @param endTime The daily end time of the window
     * @return A configured TimeWindowCondition for daily repetition
     */
    public static TimeWindowCondition createDaily(LocalTime startTime, LocalTime endTime) {
        return new TimeWindowCondition(
            startTime, 
            endTime, 
            UNLIMITED_START_DATE, 
            UNLIMITED_END_DATE,
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
    private void calculateNextWindow(LocalDateTime lastValidTime) {
        ZonedDateTime now = ZonedDateTime.now(getZoneId());
        LocalDateTime nowLocal = now.toLocalDateTime();


        LocalDateTime referenceTime = lastValidTime != null ? lastValidTime : nowLocal;
        
        //LocalDateTime todayStartDateTime = LocalDateTime.of(nowLocal.toLocalDate(), startTime);
        //LocalDateTime todayEndDateTime = LocalDateTime.of(nowLocal.toLocalDate(), endTime);
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
                calculateCycleWindow(referenceTime);
                
                break;
            case HOURS:
                calculateCycleWindow(referenceTime);
               
                break;
                
            default:
                log.warn("Unsupported repeat cycle: {}", repeatCycle);
                break;
        }
        log.info(this.getDetailedDescription());
        if(Microbot.isDebug() ) log.info("After calculate new cycle window : \n" +  this,Level.INFO);
        // Apply randomization if enabled
        
        // Only check end date bounds if not unlimited
        if (!isUnlimitedEndDate()) {
            LocalDateTime lastEnd = LocalDateTime.of(endDate, endTime);
            if (getNextTriggerTimeWithPause().orElse(null) != null) {
                LocalDateTime nextTrigger = getNextTriggerTimeWithPause().get().toLocalDateTime();
                if (nextTrigger.isAfter(lastEnd)){
                    setNextTriggerTime(null);
                    this.currentEndDateTime = null;
                }
            }else{
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
                setNextTriggerTime(todayStartDateTime.atZone(getZoneId()));
                this.currentEndDateTime = todayEndDateTime;
            } else {
                setNextTriggerTime(todayEndDateTime.plusDays(1).atZone(getZoneId()));
                this.currentEndDateTime = todayEndDateTime.plusDays(1);
                
            }
        }else{
            // If the reset time is after the end of the window, set to null
            if (lastValidResetTime.isAfter(currentEndDateTime)) {
                 setNextTriggerTime(null);
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
        // First, determine the bounds of today's overall window
        LocalDate today = now.toLocalDate();
        LocalDateTime currentDayWindowStart = LocalDateTime.of(today, startTime);
        LocalDateTime currentDayWindowEnd = LocalDateTime.of(today, endTime);
        
        // Handle cross-midnight windows
        if (currentDayWindowEnd.isBefore(currentDayWindowStart)) {
            currentDayWindowEnd = currentDayWindowEnd.plusDays(1);
        }
                

        
        LocalDateTime nextTriggerTime =  calculateNextStartWindow(referenceTime);
        setNextTriggerTime(nextTriggerTime.atZone(getZoneId()));        
        if (Microbot.isDebug()) log.info("calculation of new cycle window after calculation of next start window:\n {}", this);
        
        
        // If next interval starts after the outer window end, it's not valid today      
        LocalDate startday = nextTriggerTime.toLocalDate();           
        this.currentEndDateTime = LocalDateTime.of(startday, endTime);        
        if (currentEndDateTime.isBefore(nextTriggerTime)) {
            this.currentEndDateTime = calculateNextTime( this.currentEndDateTime);  
            LocalDateTime endDateTimeNextDay = LocalDateTime.of(startday.plusDays(1), endTime);
            if (this.currentEndDateTime.isBefore(nextTriggerTime) || endDateTimeNextDay.isBefore(this.currentEndDateTime)) {
                throw new IllegalStateException("Invalid end time calculation: " + this.currentEndDateTime);
            }
        }      
    
    }

    private LocalDateTime calculateNextTime( LocalDateTime referenceTime) {
        LocalDateTime nextStartTime;
        
        if (repeatIntervalUnit == 0) {
            return referenceTime;
        }   
        
        // First calculate the base next time without randomization
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
                nextStartTime = referenceTime.plusWeeks(repeatIntervalUnit);
                break;
            default:
                log.warn("Unsupported repeat cycle: {}", repeatCycle);
                nextStartTime = referenceTime;
                break;
        }
        log.info("Base next start time calculated: \n\t{}\n\t{}", nextStartTime);
        
        // Apply user-configured randomization if enabled
        if (useRandomization && randomizerValue > 0) {
            // Calculate maximum allowed randomization based on interval and cycle
            int maxAllowedRandomization = calculateMaxAllowedRandomization();
            
            // Cap the randomizer value to the maximum allowed
            int cappedRandomizerValue = Math.min(randomizerValue, maxAllowedRandomization);
            
            // Generate random offset between -cappedRandomizerValue and +cappedRandomizerValue
            int randomOffset = Rs2Random.between(-cappedRandomizerValue, cappedRandomizerValue);
            
            // Store the base time before applying randomization for logging
            LocalDateTime baseTime = nextStartTime;
            
            // Automatically determine the appropriate randomization unit based on repeat cycle
            RepeatCycle randomUnit = getAutomaticRandomizerValueUnit();
            switch (randomUnit) {
                case SECONDS:
                    nextStartTime = nextStartTime.plusSeconds(randomOffset);
                    break;
                case MINUTES:
                    nextStartTime = nextStartTime.plusMinutes(randomOffset);
                    break;
                case HOURS:
                    nextStartTime = nextStartTime.plusHours(randomOffset);
                    break;
                default:
                    // Default to minutes if unsupported unit
                    nextStartTime = nextStartTime.plusMinutes(randomOffset);
                    break;
            }
            
            log.info("Applied randomization: {} {} offset to next trigger time. Base: {}, Final: {} (capped from {} to {})", 
                randomOffset, randomUnit, baseTime, nextStartTime, 
                randomizerValue, cappedRandomizerValue);
        }
        log.info("Next start time after randomization: {}", nextStartTime);
        return nextStartTime;
    }
    
    /**
     * Calculates the maximum allowed randomization value based on the repeat cycle and interval.
     * This ensures randomization stays within meaningful bounds relative to the interval.
     * 
     * @return Maximum allowed randomization value in the automatic randomization unit
     */
    private int calculateMaxAllowedRandomization() {
        // Convert interval to the same unit as randomization for comparison
        RepeatCycle randomUnit = getAutomaticRandomizerValueUnit();
        return TimeConditionPanelUtil.calculateMaxAllowedRandomization(getRepeatCycle(), getRepeatIntervalUnit());
        // Calculate total interval in the randomization unit
    }
    
    /**
     * Converts an interval value from one unit to another for comparison purposes.
     * 
     * @param value The interval value to convert
     * @param fromUnit The original unit
     * @param toUnit The target unit
     * @return The converted value
     */
    public static long convertToRandomizationUnit(int value, RepeatCycle fromUnit, RepeatCycle toUnit) {
        // Convert to seconds first, then to target unit
        long totalSeconds;
        switch (fromUnit) {
            case MINUTES:
                totalSeconds = value * 60L;
                break;
            case HOURS:
                totalSeconds = value * 3600L;
                break;
            case DAYS:
                totalSeconds = value * 86400L;
                break;
            case WEEKS:
                totalSeconds = value * 604800L;
                break;
            default:
                totalSeconds = value;
                break;
        }
        
        // Convert from seconds to target unit
        switch (toUnit) {
            case SECONDS:
                return totalSeconds;
            case MINUTES:
                return totalSeconds / 60L;
            case HOURS:
                return totalSeconds / 3600L;
            default:
                return totalSeconds / 60L; // Default to minutes
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
            
            nextStartTime = calculateNextTime(referenceTime);
           
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
        if (getNextTriggerTimeWithPause().isPresent()) {
            return isSatisfied(getNextTriggerTimeWithPause().get().toLocalDateTime());
        }        
        return false;
    }
    @Override
    public boolean isSatisfiedAt(ZonedDateTime triggerTime) {
        return isSatisfied(triggerTime.toLocalDateTime());
        
    }
    
    private boolean isSatisfied(LocalDateTime currentStartDateTime) {
        if (isPaused()) {
            return false;
        }
        if (!canTriggerAgain()) {
            return false;
        }
        ZonedDateTime now = ZonedDateTime.now(getZoneId());
        LocalDateTime nowLocal = now.toLocalDateTime();
        LocalDate today = now.toLocalDate();
        LocalDate dayBefore = today.minusDays(1);
        LocalDateTime currentDayWindowStart = LocalDateTime.of(today, startTime);
        LocalDateTime beforeDayWindowStart = LocalDateTime.of( dayBefore,  startTime);            
        LocalDateTime beforeDayWindowEnd = LocalDateTime.of(dayBefore, endTime);    

        // For non-daily or interval > 1 day cycles, check against calculated next window
        if (currentStartDateTime == null || currentEndDateTime == null) {                
            
            return false; // No more windows in range
            
        }
        if ((currentStartDateTime.isAfter(beforeDayWindowStart) && currentEndDateTime.isBefore(beforeDayWindowEnd))) {
            lastValidResetTime = currentDayWindowStart;
            this.calculateNextWindow(this.lastValidResetTime);
            
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
        if (getNextTriggerTimeWithPause().orElse(null) == null || currentEndDateTime == null) {
            log.debug("Unable to calculate progress - window bounds are null");
            return 0.0;
        }
        
        ZonedDateTime now = ZonedDateTime.now(getZoneId());
        LocalDateTime nowLocal = now.toLocalDateTime();
        LocalDateTime currentStartDateTime = getNextTriggerTimeWithPause().get().toLocalDateTime();
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
        // Add date range information only if not unlimited
        if (!hasUnlimitedDateRange()) {
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            description.append(" ( Between: ");
            
            if (isUnlimitedStartDate()) {
                description.append("No start limit");
            } else {
                description.append(startDate.format(dateFormatter));
            }
            
            description.append(" to ");
            
            if (isUnlimitedEndDate()) {
                description.append("No end limit");
            } else {
                description.append(endDate.format(dateFormatter));
            }
            
            description.append(")");
        }
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
     * @param randomizerValue Maximum number of minutes to randomize by (plus or minus)
     */
    public void setRandomization(boolean useRandomization, int randomizerValue) {
        this.useRandomization = useRandomization;
        this.randomizerValue = Math.max(0, randomizerValue);
    }
   /**
     * Configures time randomization for this window.
     * When enabled, the start and end times will be adjusted by a random
     * amount within the specified range each time the condition is reset.
     *
     * @param useRandomization Whether to enable time randomization     
     */
    public void setRandomization(boolean useRandomization) {
        this.useRandomization = useRandomization;
        this.randomizerValue = 0;
    }
    
    /**
     * Sets the randomization value without changing the enabled state.
     * 
     * @param randomizerValue The randomization value to set
     */
    public void setRandomizerValue(int randomizerValue) {
        this.randomizerValue = Math.max(0, randomizerValue);
    }
    
    /**
     * Gets the randomization value.
     * 
     * @return The current randomization value
     */
    public int getRandomizerValue() {
        return this.randomizerValue;
    }
    
    /**
     * Gets the randomization unit that is automatically determined based on the repeat cycle.
     * 
     * @return The current automatically determined randomization unit
     */
    public RepeatCycle getRandomizerValueUnit() {
        return getAutomaticRandomizerValueUnit();
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

  
    public void hardReset() {
        this.currentValidResetCount = 0;
        resume();
        this.lastValidResetTime = LocalDateTime.now();
        this.setNextTriggerTime(null);
        
        this.currentEndDateTime = null;
        this.useRandomization = true;
        this.transientNumberOfResetsWithinDailyInterval = 0;
        // Initialize next window times based on repeat cycle
        calculateNextWindow(this.lastValidResetTime);
  
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
        // Store current time as the reset reference
        log.debug("Last reset time: {}", lastValidResetTime);
        this.lastValidResetTime = LocalDateTime.now();                                
        ZonedDateTime now = ZonedDateTime.now(getZoneId());
        LocalDateTime nowLocal = now.toLocalDateTime();        
        // If we are have a current window and we are within the window or after it, we need to force an advance
        Optional<ZonedDateTime>    currentZoneStartDateTime =     getNextTriggerTimeWithPause();
        
        if (currentZoneStartDateTime ==null || !currentZoneStartDateTime.isPresent()) {
            return;
        }
        LocalDateTime  currentStartDateTime =currentZoneStartDateTime.get().toLocalDateTime();            
        boolean needsAdvance = currentZoneStartDateTime!=null && currentZoneStartDateTime.isPresent() && nowLocal.isAfter(currentZoneStartDateTime.get().toLocalDateTime());                           
        // If this the next  start window that's passed or any window that needs advancing
        if (needsAdvance && canTriggerAgain() ) {     
            this.currentValidResetCount++;       
            calculateNextWindow(this.lastValidResetTime);            
        }          
        if (nowLocal.isAfter(currentStartDateTime) && nowLocal.isBefore(this.currentEndDateTime)) {
            transientNumberOfResetsWithinDailyInterval++;
        }else {
            transientNumberOfResetsWithinDailyInterval = 0;
        }       
        // Log the new window for debugging
        if (currentStartDateTime != null && this.currentEndDateTime != null) {
            log.debug("Next window after reset: {} to {}", 
                DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(currentStartDateTime),
                DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(currentEndDateTime));
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
        if (getNextTriggerTimeWithPause().orElse(null) == null || currentEndDateTime == null || !canTriggerAgain()) {
            return Optional.empty();
        }
        ZonedDateTime now = ZonedDateTime.now(getZoneId());
        LocalDateTime currentStartDateTime = getNextTriggerTimeWithPause().get().toLocalDateTime();
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
        
        // If end date is unlimited, only check parent class logic
        if (isUnlimitedEndDate()) {
            return canTrigger;
        }
        
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
        sb.append("  │ Date Range: ");
        if (isUnlimitedStartDate()) {
            sb.append("No start limit");
        } else {
            sb.append(startDate.format(dateFormatter));
        }
        sb.append(" to ");
        if (isUnlimitedEndDate()) {
            sb.append("No end limit");
        } else {
            sb.append(endDate.format(dateFormatter));
        }
        sb.append("\n");
        sb.append("  │ Repeat: ").append(repeatCycle)
          .append(", Unit: ").append(repeatIntervalUnit).append("\n");
        sb.append("  │ Timezone: ").append(getZoneId().getId()).append("\n");
        
        // Status information
        sb.append("  ├─ Status ──────────────────────────────────\n");
        sb.append("  │ Satisfied: ").append(isSatisfied()).append("\n");
        sb.append("  │ Paused: ").append(isPaused()).append("\n");
        if (getNextTriggerTimeWithPause().orElse(null) != null && currentEndDateTime != null) {
            sb.append("  │ Current Window: ").append(getNextTriggerTimeWithPause().get().toLocalDateTime().format(dateTimeFormatter))
              .append("\n  │ To: ").append(currentEndDateTime.format(dateTimeFormatter)).append("\n");
        } else if (getNextTriggerTimeWithPause().orElse(null) != null) {
            sb.append("  │ Current Window: ").append(getNextTriggerTimeWithPause().get().toLocalDateTime().format(dateTimeFormatter))
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
            RepeatCycle randomUnit = getAutomaticRandomizerValueUnit();
            int maxAllowedRandomization = calculateMaxAllowedRandomization();
            int cappedRandomizerValue = Math.min(randomizerValue, maxAllowedRandomization);
            
            // Original configured value
            sb.append("  │ Random Range: ±").append(randomizerValue).append(" ").append(randomUnit.toString().toLowerCase()).append("\n");
            
            // Actual capped value used in calculations
            if (cappedRandomizerValue != randomizerValue) {
                sb.append("  │ Capped Range: ±").append(cappedRandomizerValue).append(" ").append(randomUnit.toString().toLowerCase())
                  .append(" (limited from ").append(randomizerValue).append(")\n");
            }
            
            // Maximum allowed randomization for this interval
            sb.append("  │ Max Allowed: ±").append(maxAllowedRandomization).append(" ").append(randomUnit.toString().toLowerCase()).append("\n");
            
            // Show the automatic unit determination
            sb.append("  │ Random Unit: ").append(randomUnit.toString().toLowerCase())
              .append(" (auto-determined from ").append(repeatCycle.toString().toLowerCase()).append(" cycle)\n");
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
        
        // Add pause information
        if (isPaused()) {
            sb.append("    Paused: Yes\n");
            Duration currentPauseDuration = Duration.between(pauseStartTime, getNow());
            sb.append("    Current Pause Duration: ").append(formatDuration(currentPauseDuration)).append("\n");
        }
        if (totalPauseDuration.getSeconds() > 0) {
            sb.append("    Total Pause Duration: ").append(formatDuration(totalPauseDuration)).append("\n");
        }
        
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
        sb.append("\nTime Window Condition:\ntActive from ").append(startTime.format(timeFormatter))
          .append(" to ").append(endTime.format(timeFormatter)).append("\n");
        
        // Repeat cycle
        if (repeatCycle == RepeatCycle.ONE_TIME) {
            sb.append("Schedule: One time only");
            if (!hasUnlimitedDateRange()) {
                sb.append(" (");
                if (isUnlimitedStartDate()) {
                    sb.append("No start limit");
                } else {
                    sb.append(startDate.format(dateFormatter));
                }
                sb.append(" - ");
                if (isUnlimitedEndDate()) {
                    sb.append("No end limit");
                } else {
                    sb.append(endDate.format(dateFormatter));
                }
                sb.append(")");
            }
            sb.append("\n");
        } else {
            sb.append("Schedule: Repeats every ").append(repeatIntervalUnit).append(" ")
              .append(repeatCycle.toString().toLowerCase()).append("\n");
            if (!hasUnlimitedDateRange()) {
                sb.append("Valid period: ");
                if (isUnlimitedStartDate()) {
                    sb.append("No start limit");
                } else {
                    sb.append(startDate.format(dateFormatter));
                }
                sb.append(" - ");
                if (isUnlimitedEndDate()) {
                    sb.append("No end limit");
                } else {
                    sb.append(endDate.format(dateFormatter));
                }
                sb.append("\n");
            }
        }
        
        // Status information
        boolean satisfied = isSatisfied();
        sb.append("Status: ").append(satisfied ? "Active (in time window)" : "Inactive (outside time window)").append("\n");
        
        // Current window information
        ZonedDateTime now = ZonedDateTime.now(getZoneId());
        LocalDateTime nowLocal = now.toLocalDateTime();
        
        if (getNextTriggerTimeWithPause() != null && currentEndDateTime != null) {
            sb.append("Current window: ").append(getNextTriggerTimeWithPause().get().toLocalDateTime().format(dateTimeFormatter))
              .append(" to ").append(currentEndDateTime.format(dateTimeFormatter)).append("\n");
              
            if (nowLocal.isAfter(getNextTriggerTimeWithPause().get().toLocalDateTime()) && nowLocal.isBefore(currentEndDateTime)) {
                sb.append("Time remaining: ")
                  .append(ChronoUnit.MINUTES.between(nowLocal, currentEndDateTime))
                  .append(" minutes\n");
                sb.append("Progress: ").append(String.format("%.1f%%", getProgressPercentage())).append("\n");
            } else if (nowLocal.isBefore(getNextTriggerTimeWithPause().get().toLocalDateTime())) {
                sb.append("Window starts in: ")
                  .append(ChronoUnit.MINUTES.between(nowLocal, getNextTriggerTimeWithPause().get().toLocalDateTime()))
                  .append(" minutes\n");
            } else {
                sb.append("Window has passed\n");
            }
        } else {
            sb.append("No active window available\n");
        }
        if (useRandomization) {
            sb.append("  │ Random Range: ±").append(randomizerValue);
            switch (repeatCycle) {
                case MINUTES:
                    sb.append(" millisec\n");
                    break;
                case HOURS:
                    sb.append(" seconds\n");
                    break;
                case DAYS:
                    sb.append(" minutes\n");
                    break;         
                case WEEKS:
                    sb.append(" hours\n");
                    break;   
                default:
                    break;
            }
        }else {
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

    /**
     * Called when the condition is resumed.
     * Shifts time windows by the pause duration to maintain the same
     * relative timing after a pause.
     * 
     * @param pauseDuration The duration of the most recent pause
     */
    @Override
    protected void onResume(Duration pauseDuration) {
        if (isPaused()) {
            return;
        }
        
        // Get the original trigger time (since isPaused=false at this point after resume())
        // getNextTriggerTimeWithPause() now returns the original nextTriggerTime without pause adjustments
        ZonedDateTime originalTriggerTime = getNextTriggerTimeWithPause().orElse(null);
        
        if (originalTriggerTime != null) {
            // Shift the original trigger time by the pause duration to preserve timing
            ZonedDateTime shiftedTriggerTime = originalTriggerTime.plus(pauseDuration);
            LocalDateTime shiftedLocalTime = shiftedTriggerTime.toLocalDateTime();
            
            // Validate that the shifted time still falls within allowed bounds
            boolean isValidShiftedTime = isShiftedTimeWithinBounds(shiftedLocalTime);
            
            if (isValidShiftedTime) {
                // Shifted time is valid, use it
                setNextTriggerTime(shiftedTriggerTime);
                
                // Also shift the current end time by the same duration to maintain window length
                if (currentEndDateTime != null) {
                    LocalDateTime shiftedEndTime = currentEndDateTime.plus(pauseDuration);
                    // Validate that the shifted end time is also within bounds
                    if (isShiftedEndTimeWithinBounds(shiftedEndTime)) {
                        currentEndDateTime = shiftedEndTime;
                    } else {
                        // If shifted end time goes out of bounds, recalculate the window
                        log.warn("Shifted end time {} goes beyond allowed bounds, recalculating window", shiftedEndTime);
                        calculateNextWindow(getNow().toLocalDateTime());
                        return;
                    }
                }
                
                // Shift the last valid reset time if it exists
                if (lastValidResetTime != null) {
                    lastValidResetTime = lastValidResetTime.plus(pauseDuration);
                }
                
                log.debug("TimeWindowCondition resumed after {}, window shifted by pause duration, new trigger time: {}", 
                        formatDuration(pauseDuration), getNextTriggerTimeWithPause().orElse(null));
            } else {
                // Shifted time goes out of bounds, recalculate next valid window
                log.warn("Shifted trigger time {} goes beyond allowed bounds, recalculating next valid window", shiftedLocalTime);
                calculateNextWindow(getNow().toLocalDateTime());
            }
        } else {
            // If no trigger time was set, calculate a new window from current time
            // This should only happen if the condition was never properly initialized
            log.warn("TimeWindowCondition resumed but no trigger time was set, recalculating window");
            calculateNextWindow(getNow().toLocalDateTime());
        }
    }
    
    /**
     * Validates that a shifted trigger time is still within the allowed time window and date bounds.
     * 
     * @param shiftedTime The shifted trigger time to validate
     * @return true if the shifted time is within bounds, false otherwise
     */
    private boolean isShiftedTimeWithinBounds(LocalDateTime shiftedTime) {
        // Check date range bounds (if not unlimited)
        if (!isUnlimitedStartDate() && shiftedTime.toLocalDate().isBefore(startDate)) {
            log.debug("Shifted time {} is before start date {}", shiftedTime, startDate);
            return false;
        }
        
        if (!isUnlimitedEndDate()) {
            LocalDateTime lastValidDateTime = LocalDateTime.of(endDate, endTime);
            if (shiftedTime.isAfter(lastValidDateTime)) {
                log.debug("Shifted time {} is after end date/time {}", shiftedTime, lastValidDateTime);
                return false;
            }
        }
        
        // Check daily time bounds
        LocalTime shiftedLocalTime = shiftedTime.toLocalTime();
        
        // Handle cross-midnight windows
        if (endTime.isBefore(startTime)) {
            // Cross-midnight window (e.g., 22:00 to 06:00)
            boolean isInFirstPart = !shiftedLocalTime.isBefore(startTime); // >= startTime
            boolean isInSecondPart = !shiftedLocalTime.isAfter(endTime);   // <= endTime
            
            if (!(isInFirstPart || isInSecondPart)) {
                log.debug("Shifted time {} is outside cross-midnight window {} to {}", 
                         shiftedLocalTime, startTime, endTime);
                return false;
            }
        } else {
            // Normal window (e.g., 09:00 to 17:00)
            if (shiftedLocalTime.isBefore(startTime) || shiftedLocalTime.isAfter(endTime)) {
                log.debug("Shifted time {} is outside time window {} to {}", 
                         shiftedLocalTime, startTime, endTime);
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Validates that a shifted end time is still within the allowed date bounds.
     * 
     * @param shiftedEndTime The shifted end time to validate
     * @return true if the shifted end time is within bounds, false otherwise
     */
    private boolean isShiftedEndTimeWithinBounds(LocalDateTime shiftedEndTime) {
        // Only need to check date bounds for end time, not daily time bounds
        if (!isUnlimitedEndDate()) {
            LocalDateTime lastValidDateTime = LocalDateTime.of(endDate, endTime);
            if (shiftedEndTime.isAfter(lastValidDateTime)) {
                log.debug("Shifted end time {} is after end date/time {}", shiftedEndTime, lastValidDateTime);
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Gets the estimated time until this time window condition will be satisfied.
     * This provides a more accurate estimate by considering the window start time,
     * repeat cycles, and current window state.
     * 
     * @return Optional containing the estimated duration until satisfaction, or empty if not determinable
     */
    @Override
    public Optional<Duration> getEstimatedTimeWhenIsSatisfied() {
        // If the condition is already satisfied (we're in the window), return zero
        if (isSatisfied()) {
            return Optional.of(Duration.ZERO);
        }
        
        // If we can't trigger again, return empty
        if (!canTriggerAgain()) {
            return Optional.empty();
        }
        
        // Get the next trigger time with pause adjustments
        Optional<ZonedDateTime> triggerTime = getNextTriggerTimeWithPause();
        if (!triggerTime.isPresent()) {
            // Fallback to regular getCurrentTriggerTime
            triggerTime = getCurrentTriggerTime();
        }
        
        if (triggerTime.isPresent()) {
            ZonedDateTime now = getEffectiveNow();
            Duration duration = Duration.between(now, triggerTime.get());
            
            // Apply randomization if enabled to provide a range estimate
            if (useRandomization && randomizerValue > 0) {
                // Add some uncertainty based on the randomizer value
                Duration randomComponent = Duration.of(randomizerValue, 
                    getRandomizerChronoUnit());
                duration = duration.plus(randomComponent.dividedBy(2)); // Add half the random range
            }
            
            // Ensure we don't return negative durations
            if (duration.isNegative()) {
                return Optional.of(Duration.ZERO);
            }
            return Optional.of(duration);
        }
        
        return Optional.empty();
    }
    
    /**
     * Helper method to get the ChronoUnit for randomization based on the repeat cycle
     */
    private java.time.temporal.ChronoUnit getRandomizerChronoUnit() {
        RepeatCycle automaticUnit = getAutomaticRandomizerValueUnit();
        switch (automaticUnit) {
            case SECONDS:
                return java.time.temporal.ChronoUnit.SECONDS;
            case MINUTES:
                return java.time.temporal.ChronoUnit.MINUTES;
            case HOURS:
                return java.time.temporal.ChronoUnit.HOURS;
            case DAYS:
                return java.time.temporal.ChronoUnit.DAYS;
            default:
                return java.time.temporal.ChronoUnit.MINUTES;
        }
    }
    
    /**
     * Automatically determines the appropriate randomization unit based on the repeat cycle.
     * This ensures randomization uses sensible granularity relative to the repeat interval.
     * 
     * @return The appropriate RepeatCycle for randomization based on the current repeatCycle
     */
    private RepeatCycle getAutomaticRandomizerValueUnit() {
        switch (repeatCycle) {
            case MINUTES:
                return RepeatCycle.SECONDS; // For minute intervals, randomize in seconds
            case HOURS:
                return RepeatCycle.MINUTES; // For hour intervals, randomize in minutes
            case DAYS:
                return RepeatCycle.MINUTES; // For day intervals, randomize in minutes
            case WEEKS:
                return RepeatCycle.HOURS;   // For week intervals, randomize in hours
            case ONE_TIME:
                return RepeatCycle.MINUTES; // For one-time, use minutes as default
            default:
                return RepeatCycle.MINUTES; // Default fallback to minutes
        }
    }
}