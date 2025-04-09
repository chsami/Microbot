package net.runelite.client.plugins.microbot.pluginscheduler;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

import static net.runelite.client.plugins.microbot.pluginscheduler.SchedulerPlugin.configGroup;

@ConfigGroup(configGroup)
public interface SchedulerConfig extends Config {
    @ConfigItem(
            keyName = "scheduledPlugins",
            name = "Scheduled Plugins",
            description = "JSON representation of scheduled scripts",
            hidden = true
    )
    default String scheduledPlugins() {
        return "";
    }

    @ConfigItem(
            keyName = "scheduledPlugins",
            name = "Scheduled Plugins",
            description = "JSON representation of scheduled scripts",
            hidden = true
    )
    void setScheduledPlugins(String json);

    @ConfigItem(
            keyName = "logOut",
            name = "logOut",
            description = "logOut",
            hidden = true
    )
    default Boolean logOut() { return false; };
}