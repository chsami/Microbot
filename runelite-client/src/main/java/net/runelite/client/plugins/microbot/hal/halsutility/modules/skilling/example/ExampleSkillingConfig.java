package net.runelite.client.plugins.microbot.hal.halsutility.modules.skilling.example;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("exampleskilling")
public interface ExampleSkillingConfig extends Config {
    
    @ConfigSection(
            name = "General Settings",
            description = "General settings for the skilling module",
            position = 0
    )
    String generalSection = "general";
    
    @ConfigItem(
            keyName = "enabled",
            name = "Enabled",
            description = "Enable example skilling module",
            section = generalSection,
            position = 0
    )
    default boolean enabled() {
        return false;
    }
    
    @ConfigItem(
            keyName = "skillToTrain",
            name = "Skill to Train",
            description = "Which skill to train",
            section = generalSection,
            position = 1
    )
    default String skillToTrain() {
        return "Woodcutting";
    }
    
    @ConfigSection(
            name = "Advanced Settings",
            description = "Advanced configuration options",
            position = 1
    )
    String advancedSection = "advanced";
    
    @ConfigItem(
            keyName = "autoDrop",
            name = "Auto Drop",
            description = "Automatically drop items when inventory is full",
            section = advancedSection,
            position = 0
    )
    default boolean autoDrop() {
        return true;
    }
} 