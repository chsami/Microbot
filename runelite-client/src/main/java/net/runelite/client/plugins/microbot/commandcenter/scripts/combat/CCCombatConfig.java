package net.runelite.client.plugins.microbot.commandcenter.scripts.combat;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("cc-combat")
public interface CCCombatConfig extends Config {
    @ConfigSection(name = "General", description = "General settings", position = 0)
    String generalSection = "general";

    @ConfigItem(keyName = "monsterName", name = "Monster", description = "Name of monster to attack",
        position = 0, section = generalSection)
    default String monsterName() { return "Chicken"; }

    @ConfigItem(keyName = "eatPercent", name = "Eat at HP%", description = "Eat food when HP drops below this percentage",
        position = 1, section = generalSection)
    default int eatPercent() { return 50; }

    @ConfigItem(keyName = "lootItems", name = "Loot Items", description = "Comma-separated item names to loot",
        position = 2, section = generalSection)
    default String lootItems() { return "Bones,Feather"; }

    @ConfigItem(keyName = "buryBones", name = "Bury Bones", description = "Automatically bury bones",
        position = 3, section = generalSection)
    default boolean buryBones() { return true; }

    @ConfigItem(keyName = "lootRadius", name = "Loot Radius", description = "Radius to search for loot",
        position = 4, section = generalSection)
    default int lootRadius() { return 10; }

    @ConfigItem(keyName = "progression", name = "Enable Progression", description = "Log monster upgrade suggestions at level thresholds",
        position = 5, section = generalSection)
    default boolean progression() { return false; }
}
