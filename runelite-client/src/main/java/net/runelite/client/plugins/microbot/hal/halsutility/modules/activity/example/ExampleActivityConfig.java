package net.runelite.client.plugins.microbot.hal.halsutility.modules.activity.example;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("exampleactivity")
public interface ExampleActivityConfig extends Config {
    
    @ConfigSection(
            name = "General Settings",
            description = "General settings for the activity module",
            position = 0
    )
    String generalSection = "general";
    
    @ConfigItem(
            keyName = "enabled",
            name = "Enabled",
            description = "Enable example activity module",
            section = generalSection,
            position = 0
    )
    default boolean enabled() {
        return false;
    }
    
    @ConfigItem(
            keyName = "activityType",
            name = "Activity Type",
            description = "Type of activity to perform",
            section = generalSection,
            position = 1
    )
    default String activityType() {
        return "Clue Scroll";
    }
    
    @ConfigSection(
            name = "Activity Settings",
            description = "Settings specific to the activity",
            position = 1
    )
    String activitySection = "activity";
    
    @ConfigItem(
            keyName = "maxSteps",
            name = "Maximum Steps",
            description = "Maximum number of steps to complete",
            section = activitySection,
            position = 0
    )
    default int maxSteps() {
        return 10;
    }
    
    @ConfigItem(
            keyName = "autoTeleport",
            name = "Auto Teleport",
            description = "Automatically teleport when needed",
            section = activitySection,
            position = 1
    )
    default boolean autoTeleport() {
        return true;
    }
    
    @ConfigItem(
            keyName = "useStamina",
            name = "Use Stamina Potions",
            description = "Use stamina potions for running",
            section = activitySection,
            position = 2
    )
    default boolean useStamina() {
        return false;
    }
} 