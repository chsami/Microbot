package net.runelite.client.plugins.microbot.commandcenter.scripts.cooking;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("cc-cooking")
public interface CCCookingConfig extends Config {
    @ConfigSection(name = "General", description = "General settings", position = 0)
    String generalSection = "general";

    @ConfigItem(keyName = "food", name = "Food Type", description = "Which food to cook",
        position = 0, section = generalSection)
    default CookableFood food() { return CookableFood.SHRIMP; }
}
