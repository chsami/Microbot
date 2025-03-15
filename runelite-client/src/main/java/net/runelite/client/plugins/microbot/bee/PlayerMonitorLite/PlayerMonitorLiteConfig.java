package net.runelite.client.plugins.microbot.bee.PlayerMonitorLite;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("PlayerMonitorLite")
public interface PlayerMonitorLiteConfig extends Config {

    @ConfigItem(
            keyName = "pluginDescription",
            name = "What This Does:",
            description = "Information about this plugin",
            position = 0
    )
    default String pluginDescription() {
        return "This plugin when switched on will log you out when someone within the given alarm radius can attack you.";
    }

    @ConfigItem(
            keyName = "alarmRadius",
            name = "Alarm Radius",
            description = "The radius (in tiles) to check for players who can attack you",
            position = 1
    )
    default int alarmRadius() {
        return 15;
    }
}