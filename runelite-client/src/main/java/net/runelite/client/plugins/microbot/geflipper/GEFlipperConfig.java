package net.runelite.client.plugins.microbot.geflipper;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("geflipper")
public interface GEFlipperConfig extends Config {
    @ConfigItem(
            keyName = "minVolume",
            name = "Minimum Volume",
            description = "Skip items with volume below this",
            position = 1
    )
    default int minVolume() { return 500; }

    @ConfigItem(
            keyName = "minMargin",
            name = "Minimum Margin",
            description = "Minimum gp margin to flip",
            position = 2
    )
    default int minMargin() { return 10; }
}
