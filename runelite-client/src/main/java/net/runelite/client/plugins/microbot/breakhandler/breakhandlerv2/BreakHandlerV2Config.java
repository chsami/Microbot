package net.runelite.client.plugins.microbot.breakhandler.breakhandlerv2;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.util.antiban.enums.PlaySchedule;
import net.runelite.client.plugins.microbot.util.world.RegionPreference;
import net.runelite.client.plugins.microbot.util.world.WorldSelectionMode;
import net.runelite.client.plugins.microbot.breakhandler.breakhandlerv2.PluginStopOption;

@ConfigGroup(BreakHandlerV2Config.configGroup)
public interface BreakHandlerV2Config extends Config {
    String configGroup = "break-handler-v2";

    // ========== BREAK TIMING SECTION ==========
    @ConfigSection(
        name = "Break Timing",
        description = "Configure break timing and duration",
        position = 0
    )
    String breakTimingSettings = "breakTimingSettings";

    @ConfigItem(
        keyName = "minPlaytime",
        name = "Min Playtime (minutes)",
        description = "Minimum time to play before taking a break",
        position = 0,
        section = breakTimingSettings
    )
    @Range(min = 1, max = 600)
    default int minPlaytime() {
        return 45;
    }

    @ConfigItem(
        keyName = "maxPlaytime",
        name = "Max Playtime (minutes)",
        description = "Maximum time to play before taking a break",
        position = 1,
        section = breakTimingSettings
    )
    @Range(min = 1, max = 600)
    default int maxPlaytime() {
        return 90;
    }

    @ConfigItem(
        keyName = "minBreakDuration",
        name = "Min Break Duration (minutes)",
        description = "Minimum break duration",
        position = 2,
        section = breakTimingSettings
    )
    @Range(min = 1, max = 600)
    default int minBreakDuration() {
        return 5;
    }

    @ConfigItem(
        keyName = "maxBreakDuration",
        name = "Max Break Duration (minutes)",
        description = "Maximum break duration",
        position = 3,
        section = breakTimingSettings
    )
    @Range(min = 1, max = 600)
    default int maxBreakDuration() {
        return 15;
    }

    @ConfigItem(
        keyName = "enableLongBreaks",
        name = "Enable Long Breaks",
        description = "Enable a second independent long-break timer",
        position = 4,
        section = breakTimingSettings
    )
    default boolean enableLongBreaks() {
        return false;
    }

    @ConfigItem(
        keyName = "minLongBreakInterval",
        name = "Min Long Break Interval (minutes)",
        description = "Minimum time to play before a long break can trigger",
        position = 5,
        section = breakTimingSettings
    )
    @Range(min = 1, max = 600)
    default int minLongBreakInterval() {
        return 20;
    }

    @ConfigItem(
        keyName = "maxLongBreakInterval",
        name = "Max Long Break Interval (minutes)",
        description = "Maximum time to play before a long break can trigger",
        position = 6,
        section = breakTimingSettings
    )
    @Range(min = 1, max = 600)
    default int maxLongBreakInterval() {
        return 30;
    }

    @ConfigItem(
        keyName = "minLongBreakDuration",
        name = "Min Long Break Duration (minutes)",
        description = "Minimum long-break duration",
        position = 7,
        section = breakTimingSettings
    )
    @Range(min = 1, max = 600)
    default int minLongBreakDuration() {
        return 8;
    }

    @ConfigItem(
        keyName = "maxLongBreakDuration",
        name = "Max Long Break Duration (minutes)",
        description = "Maximum long-break duration",
        position = 8,
        section = breakTimingSettings
    )
    @Range(min = 1, max = 600)
    default int maxLongBreakDuration() {
        return 10;
    }

    // ========== BREAK BEHAVIOR SECTION ==========
    @ConfigSection(
        name = "Break Behavior",
        description = "Configure how breaks work",
        position = 1
    )
    String breakBehaviorOptions = "breakBehaviorOptions";

    @ConfigItem(
        keyName = "logoutOnBreak",
        name = "Logout on Break",
        description = "Logout when taking a break",
        position = 0,
        section = breakBehaviorOptions
    )
    default boolean logoutOnBreak() {
        return true;
    }

    @ConfigItem(
        keyName = "safetyCheck",
        name = "Safety Check",
        description = "Wait until not in combat/interaction before breaking (tries up to 12 times over ~60 seconds)",
        position = 1,
        section = breakBehaviorOptions
    )
    default boolean safetyCheck() {
        return true;
    }

    @ConfigItem(
        keyName = "pluginToStop",
        name = "Stop Plugin On Break",
        description = "Select a Microbot or Plugin Hub plugin to stop automatically when a break begins.",
        position = 2,
        section = breakBehaviorOptions
    )
    default String pluginToStop() {
        return PluginStopOption.NONE_VALUE;
    }

    @ConfigItem(
        keyName = "stopPluginLeadSeconds",
        name = "Stop Lead Time (sec)",
        description = "Stop the selected plugin this many seconds before a break starts.",
        position = 3,
        section = breakBehaviorOptions
    )
    @Range(min = 0, max = 300)
    default int stopPluginLeadSeconds() {
        return 0;
    }

    @ConfigItem(
        keyName = "startPluginDelaySeconds",
        name = "Restart Delay (sec)",
        description = "Wait this many seconds after a break ends before restarting the stopped plugin.",
        position = 4,
        section = breakBehaviorOptions
    )
    @Range(min = 0, max = 300)
    default int startPluginDelaySeconds() {
        return 0;
    }

    // ========== LOGIN & WORLD SECTION ==========
    @ConfigSection(
        name = "Login & World Selection",
        description = "Configure login and world selection behavior",
        position = 2
    )
    String loginWorldSettings = "loginWorldSettings";

    @ConfigItem(
        keyName = "autoLogin",
        name = "Auto Login",
        description = "Automatically log back in after break using profile data",
        position = 0,
        section = loginWorldSettings
    )
    default boolean autoLogin() {
        return true;
    }

    @ConfigItem(
        keyName = "ignoreWorldSwitching",
        name = "Stay On Current World",
        description = "When enabled, BreakHandler V2 will not change worlds on login and will reuse whichever world is already selected.",
        position = 1,
        section = loginWorldSettings
    )
    default boolean ignoreWorldSwitching() {
        return false;
    }

    @ConfigItem(
        keyName = "worldSelectionMode",
        name = "World Selection Mode",
        description = "How to select worlds when logging back in",
        position = 2,
        section = loginWorldSettings
    )
    default WorldSelectionMode worldSelectionMode() {
        return WorldSelectionMode.CURRENT_PREFERRED_WORLD;
    }

    @ConfigItem(
        keyName = "regionPreference",
        name = "Region Preference",
        description = "Preferred region for world selection",
        position = 3,
        section = loginWorldSettings
    )
    default RegionPreference regionPreference() {
        return RegionPreference.ANY_REGION;
    }

    @ConfigItem(
        keyName = "avoidEmptyWorlds",
        name = "Avoid Empty Worlds",
        description = "Avoid worlds with very few players",
        position = 4,
        section = loginWorldSettings
    )
    default boolean avoidEmptyWorlds() {
        return true;
    }

    @ConfigItem(
        keyName = "avoidOvercrowdedWorlds",
        name = "Avoid Crowded Worlds",
        description = "Avoid worlds with too many players",
        position = 5,
        section = loginWorldSettings
    )
    default boolean avoidOvercrowdedWorlds() {
        return true;
    }

    // ========== PROFILE SETTINGS SECTION ==========
    @ConfigSection(
        name = "Profile Settings",
        description = "Profile and account management",
        position = 3
    )
    String profileSettings = "profileSettings";

    @ConfigItem(
        keyName = "useActiveProfile",
        name = "Use Active Profile",
        description = "Use the currently active profile for login credentials",
        position = 0,
        section = profileSettings
    )
    default boolean useActiveProfile() {
        return true;
    }

    @ConfigItem(
        keyName = "respectMemberStatus",
        name = "Respect Member Status",
        description = "Use profile's member status to select appropriate worlds",
        position = 1,
        section = profileSettings
    )
    default boolean respectMemberStatus() {
        return true;
    }

    // ========== NOTIFICATIONS SECTION ==========
    @ConfigSection(
        name = "Notifications",
        description = "Discord webhook and notification settings",
        position = 4
    )
    String notificationSettings = "notificationSettings";

    @ConfigItem(
        keyName = "enableDiscordWebhook",
        name = "Enable Discord Webhook",
        description = "Send notifications via Discord webhook from profile",
        position = 0,
        section = notificationSettings
    )
    default boolean enableDiscordWebhook() {
        return false;
    }

    @ConfigItem(
        keyName = "notifyOnBreakStart",
        name = "Notify on Break Start",
        description = "Send notification when break starts",
        position = 1,
        section = notificationSettings
    )
    default boolean notifyOnBreakStart() {
        return true;
    }

    @ConfigItem(
        keyName = "notifyOnBreakEnd",
        name = "Notify on Break End",
        description = "Send notification when break ends",
        position = 2,
        section = notificationSettings
    )
    default boolean notifyOnBreakEnd() {
        return true;
    }

    @ConfigItem(
        keyName = "notifyOnLoginFail",
        name = "Notify on Login Failure",
        description = "Send notification when login fails",
        position = 3,
        section = notificationSettings
    )
    default boolean notifyOnLoginFail() {
        return true;
    }

    // ========== PLAY SCHEDULE SECTION ==========
    @ConfigSection(
        name = "Play Schedule",
        description = "Options related to using a play schedule",
        position = 5
    )
    String playScheduleSettings = "playScheduleSettings";

    @ConfigItem(
        keyName = "usePlaySchedule",
        name = "Use Play Schedule",
        description = "Enable or disable the use of a play schedule. When enabled, the bot will only run during the scheduled hours.",
        position = 0,
        section = playScheduleSettings
    )
    default boolean usePlaySchedule() {
        return false;
    }

    @ConfigItem(
        keyName = "playSchedule",
        name = "Play Schedule",
        description = "Select the play schedule to use. The bot will take breaks outside of these hours.",
        position = 1,
        section = playScheduleSettings
    )
    default PlaySchedule playSchedule() {
        return PlaySchedule.MEDIUM_DAY;
    }

    // ========== OVERLAY SETTINGS ==========
    @ConfigItem(
        keyName = "hideOverlay",
        name = "Hide Overlay",
        description = "Hide the break handler overlay",
        position = 0
    )
    default boolean hideOverlay() {
        return false;
    }

    @ConfigItem(
        keyName = "showDetailedInfo",
        name = "Show Detailed Info",
        description = "Show detailed information in overlay",
        position = 1
    )
    default boolean showDetailedInfo() {
        return true;
    }
}
