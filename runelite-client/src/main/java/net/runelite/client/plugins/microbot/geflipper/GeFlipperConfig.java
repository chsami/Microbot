package net.runelite.client.plugins.microbot.geflipper;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("GeFlipper")
public interface GeFlipperConfig extends Config {
    @ConfigItem(
            keyName = "items",
            name = "Items to Flip",
            description = "Comma separated list of items to flip. Leave empty to use all F2P items",
            position = 0
    )
    default String items() { return ""; }
}
