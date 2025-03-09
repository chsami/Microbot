package net.runelite.client.plugins.microbot.breakhandler;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.util.antiban.enums.PlaySchedule;

@ConfigGroup(BreakHandlerConfig.configGroup)
public interface BreakHandlerConfig extends Config {
    // Group name constant for configuration grouping.
    String configGroup = "break-handler";
    String hideOverlay = "hideOverlay";

    // ============================================================
    // Break Timing Settings
    // ============================================================
    @ConfigSection(
            name = "Break Timing",
            description = "Configure when and how long breaks occur",
            position = 0
    )
    String breakTimingSettings = "breakTimingSettings";

    @ConfigItem(
            keyName = "Min Playtime",
            name = "Min Playtime",
            description = "Time until break start in minutes",
            position = 0,
            section = breakTimingSettings
    )
    default int timeUntilBreakStart() {
        return 60;
    }

    @ConfigItem(
            keyName = "Max Playtime",
            name = "Max Playtime",
            description = "Time until break ends in minutes",
            position = 1,
            section = breakTimingSettings
    )
    default int timeUntilBreakEnd() {
        return 120;
    }

    @ConfigItem(
            keyName = "Min BreakTime",
            name = "Min BreakTime",
            description = "Break duration start in minutes",
            position = 2,
            section = breakTimingSettings
    )
    default int breakDurationStart() {
        return 10;
    }

    @ConfigItem(
            keyName = "Max BreakTime",
            name = "Max BreakTime",
            description = "Break duration end in minutes",
            position = 3,
            section = breakTimingSettings
    )
    default int breakDurationEnd() {
        return 15;
    }

    @ConfigItem(
            keyName = "breakNow",
            name = "Break Now",
            description = "Toggle this to start a break immediately",
            position = 4,
            section = breakTimingSettings
    )
    default boolean breakNow() {
        return false;
    }

    // ============================================================
    // Break Behavior Options
    // ============================================================
    @ConfigSection(
            name = "Break Behavior",
            description = "Configure what happens during breaks",
            position = 1
    )
    String breakBehaviorOptions = "breakBehaviorOptions";

    @ConfigItem(
            keyName = "OnlyMicroBreaks",
            name = "Micro Breaks Only",
            description = "Only use micro breaks if enabled",
            position = 0,
            section = breakBehaviorOptions
    )
    default boolean onlyMicroBreaks() {
        return false;
    }

    @ConfigItem(
            keyName = "Logout",
            name = "Logout",
            description = "Logout when taking a break",
            position = 1,
            section = breakBehaviorOptions
    )
    default boolean logoutAfterBreak() {
        return true;
    }

    @ConfigItem(
            keyName = "useRandomWorld",
            name = "Use RandomWorld",
            description = "Change to a random world once break is finished",
            position = 2,
            section = breakBehaviorOptions
    )
    default boolean useRandomWorld() {
        return false;
    }

    // ============================================================
    // Play Schedule Configuration Section
    // ============================================================
    @ConfigSection(
            name = "Play Schedule",
            description = "Options related to using a play schedule",
            position = 2
    )
    String usePlaySchedule = "usePlaySchedule";

    @ConfigItem(
            keyName = "UsePlaySchedule",
            name = "Use Play Schedule",
            description = "Enable or disable the use of a play schedule",
            position = 0,
            section = usePlaySchedule
    )
    default boolean usePlaySchedule() {
        return false;
    }

    @ConfigItem(
            keyName = "PlaySchedule",
            name = "Play Schedule",
            description = "Select the play schedule",
            position = 1,
            section = usePlaySchedule
    )
    default PlaySchedule playSchedule() {
        return PlaySchedule.MEDIUM_DAY;
    }

    // ============================================================
    // Overlay Settings
    // ============================================================
    @ConfigItem(
            keyName = "hideOverlay",
            name = "Hide Overlay",
            description = "Select this if you want to hide overlay",
            position = 0
    )
    default boolean isHideOverlay() {
        return false;
    }
}