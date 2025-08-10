package net.runelite.client.plugins.microbot.breakhandler;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
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

    @ConfigItem(
            keyName = "breakEndNow",
            name = "End Break Now",
            description = "Toggle this to stop the current break immediately",
            position = 5,
            section = breakTimingSettings
    )
    default boolean breakEndNow() {
        return false;
    }

    // ============================================================
    // Region Filter
    // ============================================================
    @ConfigSection(
            name = "Region Filter",
            description = "Filter world selection by region",
            position = 1,
            closedByDefault = false
    )
    String regionSection = "region";

    @ConfigItem(
            keyName = "useRandomWorld",
            name = "Use Random World",
            description = "Change to a random world once break is finished",
            position = 0,
            section = regionSection
    )
    default boolean useRandomWorld() {
        return false;
    }

    @ConfigItem(
            keyName = "AllowUK",
            name = "UK",
            description = "Allow UK worlds",
            position = 1,
            section = regionSection
    )
    default boolean allowUK() { return true; }

    @ConfigItem(
            keyName = "AllowUS",
            name = "US",
            description = "Allow US worlds",
            position = 2,
            section = regionSection
    )
    default boolean allowUS() { return true; }

    @ConfigItem(
            keyName = "AllowGermany",
            name = "Germany",
            description = "Allow German worlds",
            position = 3,
            section = regionSection
    )
    default boolean allowGermany() { return true; }

    @ConfigItem(
            keyName = "AllowAustralia",
            name = "Australia",
            description = "Allow Australian worlds",
            position = 4,
            section = regionSection
    )
    default boolean allowAustralia() { return true; }

    // ============================================================
    // Break Behavior Options
    // ============================================================
    @ConfigSection(
            name = "Break Behavior",
            description = "Configure what happens during breaks",
            position = 2
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
            keyName = "shutdownClient",
            name = "Shutdown Client",
            description = "<html><b style='color:red;'>WARNING:</b> This will completely shutdown the entire RuneLite client during breaks.<br/>Use with caution - you will need to manually restart the client after breaks.</html>",
            position = 2,
            section = breakBehaviorOptions
    )
    default boolean shutdownClient() {
        return false;
    }

    // ============================================================
    // Advanced Options Section
    // ============================================================
    @ConfigSection(
            name = "Advanced Options",
            description = "Advanced timing and retry configuration (for experienced users)",
            position = 3
    )
    String advancedOptions = "advancedOptions";

    @ConfigItem(
            keyName = "combatCheckInterval",
            name = "Combat Check Interval",
            description = "How often to check for safe conditions when waiting to break (in seconds)",
            position = 0,
            section = advancedOptions
    )
    @Range(min = 1, max = 60)
    default int combatCheckInterval() {
        return 30;
    }

    @ConfigItem(
            keyName = "logoutRetryDelay",
            name = "Logout Retry Delay",
            description = "Delay between logout attempts (in seconds)",
            position = 1,
            section = advancedOptions
    )
    @Range(min = 1, max = 30)
    default int logoutRetryDelay() {
        return 3;
    }

    @ConfigItem(
            keyName = "loginRetryDelay",
            name = "Login Retry Delay",
            description = "Delay between login attempts (in seconds)",
            position = 2,
            section = advancedOptions
    )
    @Range(min = 1, max = 60)
    default int loginRetryDelay() {
        return 5;
    }

    @ConfigItem(
            keyName = "maxLogoutRetries",
            name = "Max Logout Retries",
            description = "Maximum number of logout attempts before giving up",
            position = 3,
            section = advancedOptions
    )
    @Range(min = 1, max = 20)
    default int maxLogoutRetries() {
        return 5;
    }

    @ConfigItem(
            keyName = "maxLoginRetries",
            name = "Max Login Retries",
            description = "Maximum number of login attempts before giving up",
            position = 4,
            section = advancedOptions
    )
    @Range(min = 1, max = 20)
    default int maxLoginRetries() {
        return 3;
    }

    @ConfigItem(
            keyName = "safeConditionTimeout",
            name = "Safe Condition Timeout",
            description = "Maximum time to wait for safe conditions before skipping break (in minutes)",
            position = 5,
            section = advancedOptions
    )
    @Range(min = 1, max = 10)
    default int safeConditionTimeout() {
        return 10;
    }

    // ============================================================
    // Play Schedule Configuration Section
    // ============================================================
    @ConfigSection(
            name = "Play Schedule",
            description = "Options related to using a play schedule",
            position = 3
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