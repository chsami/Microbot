package net.runelite.client.plugins.microbot.hal.halsutility.modules.bossing.example;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("examplebossing")
public interface ExampleBossingConfig extends Config {
    
    @ConfigSection(
            name = "General Settings",
            description = "General settings for the bossing module",
            position = 0
    )
    String generalSection = "general";
    
    @ConfigItem(
            keyName = "enabled",
            name = "Enabled",
            description = "Enable example bossing module",
            section = generalSection,
            position = 0
    )
    default boolean enabled() {
        return false;
    }
    
    @ConfigItem(
            keyName = "bossName",
            name = "Boss Name",
            description = "Name of the boss to fight",
            section = generalSection,
            position = 1
    )
    default String bossName() {
        return "Zulrah";
    }
    
    @ConfigSection(
            name = "Combat Settings",
            description = "Settings for combat and gear",
            position = 1
    )
    String combatSection = "combat";
    
    @ConfigItem(
            keyName = "prayerFlick",
            name = "Prayer Flick",
            description = "Automatically flick prayers",
            section = combatSection,
            position = 0
    )
    default boolean prayerFlick() {
        return true;
    }
    
    @ConfigItem(
            keyName = "autoEat",
            name = "Auto Eat",
            description = "Automatically eat when health is low",
            section = combatSection,
            position = 1
    )
    default boolean autoEat() {
        return true;
    }
    
    @ConfigItem(
            keyName = "eatAtHealth",
            name = "Eat at Health",
            description = "Health percentage to eat at",
            section = combatSection,
            position = 2
    )
    default int eatAtHealth() {
        return 50;
    }
    
    @ConfigSection(
            name = "Loot Settings",
            description = "Settings for loot collection",
            position = 2
    )
    String lootSection = "loot";
    
    @ConfigItem(
            keyName = "autoLoot",
            name = "Auto Loot",
            description = "Automatically collect loot",
            section = lootSection,
            position = 0
    )
    default boolean autoLoot() {
        return true;
    }
    
    @ConfigItem(
            keyName = "lootThreshold",
            name = "Loot Threshold",
            description = "Minimum value to loot",
            section = lootSection,
            position = 1
    )
    default int lootThreshold() {
        return 1000;
    }
} 