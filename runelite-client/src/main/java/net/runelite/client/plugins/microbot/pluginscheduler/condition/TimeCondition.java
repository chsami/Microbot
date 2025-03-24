package net.runelite.client.plugins.microbot.pluginscheduler.condition;

import lombok.Getter;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.Duration;
import java.time.format.DateTimeFormatter;

/**
 * Time-based condition for script execution using ZonedDateTime.
 */
@Getter
public class TimeCondition implements Condition {
    private final ZonedDateTime startTime;
    private final ZonedDateTime endTime;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    
    /**
     * Creates a time condition that will be met at the specified end time.
     */
    public TimeCondition(ZonedDateTime endTime) {
        this.startTime = ZonedDateTime.now(ZoneId.systemDefault());
        this.endTime = endTime;
    }
    
    /**
     * Creates a time condition that will be met after running for the specified duration.
     */
    public static TimeCondition fromDuration(Duration duration) {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        ZonedDateTime end = now.plus(duration);
        return new TimeCondition(end);
    }
    
    /**
     * Create a time condition with random end time between min and max durations.
     */
    public static TimeCondition createRandomized(Duration minDuration, Duration maxDuration) {
        if (minDuration.equals(maxDuration)) {
            return fromDuration(minDuration);
        }
        
        long minSeconds = minDuration.getSeconds();
        long maxSeconds = maxDuration.getSeconds();
        long range = maxSeconds - minSeconds;
        
        // Generate random duration within range
        long randomSeconds = minSeconds + (long)(Math.random() * range);
        Duration randomDuration = Duration.ofSeconds(randomSeconds);
        
        return fromDuration(randomDuration);
    }
    
    @Override
    public boolean isMet() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        return now.isEqual(endTime) || now.isAfter(endTime);
    }
    
    /**
     * Gets remaining time until condition is met
     */
    public Duration getTimeRemaining() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        return Duration.between(now, endTime).isNegative() ? Duration.ZERO : Duration.between(now, endTime);
    }
    
    /**
     * Gets the total duration of this time condition
     */
    public Duration getTotalDuration() {
        return Duration.between(startTime, endTime);
    }
    
    @Override
    public String getDescription() {
        Duration duration = getTotalDuration();
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        
        StringBuilder sb = new StringBuilder("Run until ");
        sb.append(endTime.format(DATE_TIME_FORMATTER));
        sb.append(" (");
        
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0 || hours > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || (hours == 0 && minutes == 0)) sb.append(seconds).append("s");
        
        sb.append(" total)");
        return sb.toString();
    }
    
    @Override
    public ConditionType getType() {
        return ConditionType.TIME;
    }
}