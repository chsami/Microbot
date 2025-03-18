package net.runelite.client.plugins.microbot.util.antiban.enums;

import java.time.Duration;
import java.time.LocalTime;

/**
 * The PlaySchedule enum represents predefined play time schedules for the bot, simulating real-life play patterns.
 *
 * <p>
 * Each schedule defines a specific time range during which the bot is active, allowing for more human-like
 * behavior by simulating different play habits throughout the day. These schedules are managed and enforced
 * by the Break Handler plugin, which controls when the bot should be active or inactive based on the selected schedule.
 * </p>
 *
 * <h3>Main Features:</h3>
 * <ul>
 *   <li>Predefined Time Ranges: Each schedule has a start and end time that corresponds to specific periods of the day
 *   (e.g., morning, afternoon, evening).</li>
 *   <li>Varied Durations: Schedules vary in length, offering short, medium, and long play periods for each time of day.</li>
 *   <li>Realistic Break Simulation: The bot automatically checks whether it is within or outside the defined play schedule,
 *   allowing for breaks and simulating a more realistic user pattern. This is managed by the Break Handler plugin.</li>
 * </ul>
 *
 * <h3>Usage:</h3>
 * <p>
 * The <code>PlaySchedule</code> enum is used to control when the bot is active during specific periods of the day.
 * By adhering to these schedules, the bot avoids continuous operation and mimics a more realistic user behavior.
 * The Break Handler plugin ensures that the bot respects the defined schedules and manages any necessary pauses.
 * </p>
 *
 * <h3>Schedule Checks:</h3>
 * <p>
 * The <code>isOutsideSchedule()</code> method determines whether the current time is outside of the defined play schedule.
 * This is useful for ensuring that the bot only runs during the specified time periods, as controlled by the Break Handler plugin.
 * The <code>timeUntilNextSchedule()</code> method calculates how long the bot must wait until the next play period starts,
 * helping to schedule breaks and restarts efficiently.
 * </p>
 */

public enum PlaySchedule {
    SHORT_MORNING(LocalTime.of(8, 0), LocalTime.of(9, 0)),
    MEDIUM_MORNING(LocalTime.of(7, 0), LocalTime.of(10, 0)),
    LONG_MORNING(LocalTime.of(6, 0), LocalTime.of(12, 0)),

    SHORT_AFTERNOON(LocalTime.of(12, 0), LocalTime.of(13, 0)),
    MEDIUM_AFTERNOON(LocalTime.of(12, 0), LocalTime.of(15, 0)),
    LONG_AFTERNOON(LocalTime.of(12, 0), LocalTime.of(18, 0)),

    SHORT_EVENING(LocalTime.of(18, 0), LocalTime.of(19, 0)),
    MEDIUM_EVENING(LocalTime.of(17, 0), LocalTime.of(20, 0)),
    LONG_EVENING(LocalTime.of(17, 0), LocalTime.of(23, 0)),

    SHORT_DAY(LocalTime.of(9, 0), LocalTime.of(17, 0)),
    MEDIUM_DAY(LocalTime.of(8, 0), LocalTime.of(18, 0)),
    LONG_DAY(LocalTime.of(6, 0), LocalTime.of(22, 0)),

    SHORT_NIGHT(LocalTime.of(23, 0), LocalTime.of(7, 0)),
    MEDIUM_NIGHT(LocalTime.of(21, 0), LocalTime.of(9, 0)),
    LONG_NIGHT(LocalTime.of(19, 0), LocalTime.of(11, 0));

    private final LocalTime startTime;
    private final LocalTime endTime;

    PlaySchedule(LocalTime startTime, LocalTime endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public boolean isOutsideSchedule() {
        LocalTime currentTime = LocalTime.now();
        return currentTime.isBefore(startTime) || currentTime.isAfter(endTime);
    }

    public Duration timeUntilNextSchedule() {
        LocalTime currentTime = LocalTime.now();
        if (currentTime.isBefore(startTime)) {
            return Duration.between(currentTime, startTime);
        } else if (currentTime.isAfter(endTime)) {
            return Duration.between(currentTime, startTime).plusDays(1);
        } else {
            return Duration.ZERO;
        }
    }

}
