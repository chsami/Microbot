package net.runelite.client.plugins.microbot.bee.monkscript;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("monkkiller")
public interface MonkKillerConfig extends Config {

    @ConfigItem(
            keyName = "guide",
            name = "Guide",
            description = "How to use the plugin",
            position = 0 // You can adjust this to fit the ordering of your config items
    )
    default String GUIDE() {
        return "This plugin is for defence pures ONLY. NOT intended for any other use. Start the plugin wherever and it will go to monks in edgeville. Put on autologin and your character in defensive attack mode. If defence level is low (under 15) bring some food. Will go to monks and attack them intelligently. Takes into consideration when auto-retaliate stops after an hour or so and when monks are all being used up by other players. Recommended to use breakhandler.\n";
    }

    @ConfigItem(
            keyName = "defenseLevel",
            name = "Stop Defence When",
            description = "Stops the script when this defence level is reached"
    )
    @Range(
            min = 1,
            max = 99
    )
    default int defenseLevel() {
        return 60; // Default value set to 60
    }
}
