package net.runelite.client.plugins.microbot.bga.autoboltenchanter;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.bga.autoboltenchanter.enums.BoltType;

@ConfigGroup("AutoBoltEnchanter")
public interface AutoBoltEnchanterConfig extends Config {
    
    @ConfigSection(
        name = "General settings",
        description = "Basic plugin configuration",
        position = 0
    )
    String generalSection = "general";
    
    @ConfigItem(
        keyName = "boltType",
        name = "Bolt type",
        description = "Select which type of bolt to enchant",
        section = generalSection
    )
    default BoltType boltType() {
        return BoltType.SAPPHIRE;
    }
    
    @ConfigItem(
        keyName = "debugMode",
        name = "Debug mode",
        description = "Show detailed debug information in console",
        section = generalSection
    )
    default boolean debugMode() {
        return false;
    }
}