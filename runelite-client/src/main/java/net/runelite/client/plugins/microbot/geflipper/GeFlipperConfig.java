package net.runelite.client.plugins.microbot.geflipper;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("geflipper")
public interface GeFlipperConfig extends Config {
    String CONFIG_GROUP = "geflipper";

    @ConfigItem(
            keyName = "delay",
            name = "Delay between items (ms)",
            description = "Delay in milliseconds before flipping the next item",
            position = 0
    )
    default int delay() { return 3000; }
}
