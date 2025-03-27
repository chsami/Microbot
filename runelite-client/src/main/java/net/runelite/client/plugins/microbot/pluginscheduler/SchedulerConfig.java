package net.runelite.client.plugins.microbot.pluginscheduler;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("PluginScheduler")
public interface SchedulerConfig extends Config {
    final static String CONFIG_GROUP = "PluginScheduler";
    @ConfigItem(
            keyName = "scheduledPlugins",
            name = "Scheduled Plugins",
            description = "JSON representation of scheduled scripts",
            hidden = true
    )
    default String scheduledPlugins() {
        return "";
    }
   
    void setScheduledPlugins(String json);

    @ConfigItem(
        keyName = "debugMode",
        name = "Debug Mode",
        description = "Enable detailed logging of condition checks and progress"
    )
    default boolean debugMode() {
        return false;
    }
    void setDebugMode(boolean debugMode);


    @ConfigItem(
        keyName = "softStopRetrySeconds",
        name = "Soft Stop Retry (seconds)",
        description = "Time in seconds between soft stop retry attempts"
    )
    default int softStopRetrySeconds() {
        return 60;
    }

    @ConfigItem(
        keyName = "hardStopTimeoutSeconds",
        name = "Hard Stop Timeout (seconds)",
        description = "Time in seconds before forcing a hard stop after initial soft stop attempt"
    )
    default int hardStopTimeoutSeconds() {
        return 360;
    }
}