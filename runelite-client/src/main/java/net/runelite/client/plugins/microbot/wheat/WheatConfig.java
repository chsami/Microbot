package net.runelite.client.plugins.microbot.wheat;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(WheatConfig.GROUP)
public interface WheatConfig extends Config {
    String GROUP = "Wheat";

    @ConfigItem(
            keyName = "guide",
            name = "How to use",
            description = "How to use this plugin",
            position = 0,
            section = generalSection
    )
    default String GUIDE() {
        return "Start next to/in Draynor Village Bank or the wheat field in Draynor Village";
    }

    @ConfigSection(
            name = "General",
            description = "General",
            position = 0
    )
    String generalSection = "general";

    @ConfigItem(
            keyName = "Stop after death",
            name = "Stop after death",
            description = "Stop after death",
            position = 1,
            section = generalSection
    )
    default boolean stopAfterDeath() {
        return true;
    }


}
