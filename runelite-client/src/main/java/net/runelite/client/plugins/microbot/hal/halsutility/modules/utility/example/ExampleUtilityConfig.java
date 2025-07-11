package net.runelite.client.plugins.microbot.hal.halsutility.modules.utility.example;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("exampleutility")
public interface ExampleUtilityConfig extends Config {
    
    @ConfigSection(
            name = "General Settings",
            description = "General settings for the utility module",
            position = 0
    )
    String generalSection = "general";
    
    @ConfigItem(
            keyName = "enabled",
            name = "Enabled",
            description = "Enable example utility module",
            section = generalSection,
            position = 0
    )
    default boolean enabled() {
        return false;
    }
    
    @ConfigItem(
            keyName = "utilityType",
            name = "Utility Type",
            description = "Type of utility to run",
            section = generalSection,
            position = 1
    )
    default String utilityType() {
        return "Auto Clicker";
    }
    
    @ConfigSection(
            name = "Timing Settings",
            description = "Settings for timing and intervals",
            position = 1
    )
    String timingSection = "timing";
    
    @ConfigItem(
            keyName = "interval",
            name = "Interval (ms)",
            description = "Interval between actions in milliseconds",
            section = timingSection,
            position = 0
    )
    default int interval() {
        return 1000;
    }
    
    @ConfigItem(
            keyName = "randomize",
            name = "Randomize Timing",
            description = "Add randomization to timing to avoid detection",
            section = timingSection,
            position = 1
    )
    default boolean randomize() {
        return true;
    }
} 