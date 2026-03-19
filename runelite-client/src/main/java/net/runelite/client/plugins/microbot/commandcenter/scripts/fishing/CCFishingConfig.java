package net.runelite.client.plugins.microbot.commandcenter.scripts.fishing;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("cc-fishing")
public interface CCFishingConfig extends Config {
    @ConfigSection(name = "General", position = 0)
    String generalSection = "general";

    @ConfigItem(keyName = "fishType", name = "Fish Type", description = "Which fish to catch",
        position = 0, section = generalSection)
    default FishType fishType() { return FishType.SHRIMP; }

    @ConfigItem(keyName = "bankFish", name = "Bank Fish", description = "Bank fish instead of dropping",
        position = 1, section = generalSection)
    default boolean bankFish() { return true; }
}
