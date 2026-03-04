package net.runelite.client.plugins.microbot.aiosuperheat;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.aiosuperheat.enums.SuperHeatItem;

@ConfigGroup("AIOSuperHeat")
public interface AIOSuperHeatConfig extends Config {
    @ConfigSection(name = "General", description = "General Settings", position = 0)
    String generalSection = "general";

    @ConfigItem(keyName = "itemToSuperHeat", name = "Item to Superheat", description = "Choose the item to superheat", position = 0, section = generalSection)
    default SuperHeatItem itemToSuperHeat() {
        return SuperHeatItem.IRON;
    }
}
