package net.runelite.client.plugins.microbot.frosty.trueblood;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.frosty.trueblood.enums.HomeTeleports;
import net.runelite.client.plugins.microbot.frosty.trueblood.enums.Teleports;

@ConfigGroup("Frosty")
public interface TrueBloodConfig extends Config {
    @ConfigSection(
            name = "Settings",
            description = "Settings",
            position = 2
    )
    String settingsSection = "Settings";

    @ConfigItem(
            keyName = "teleports",
            name = "Teleports",
            description = "If checked, we bank using Crafting Guild bank",
            position = 1,
            section = settingsSection
    )
    default Teleports teleports() {
        return Teleports.CRAFTING_CAPE;
    }

    @ConfigItem(
            keyName = "home teleports",
            name = "Going home",
            description = "Method of getting to POH",
            position = 2,
            section = settingsSection
    )
    default HomeTeleports homeTeleports() {
        return HomeTeleports.CONSTRUCTION_CAPE;
    }

    @ConfigItem(
            keyName = "has74Agility",
            name = "Has 74 Agility",
            description = "Check this if your player has 74 Agility to use shortcuts.",
            position = 3
    )
    default boolean has74Agility() {
        return false;
    }

    @ConfigItem(
            keyName = "useBloodEssence",
            name = "Use Blood Essence",
            description = "Check this if you want to use Blood Essence during runecrafting.",
            position = 4
    )
    default boolean useBloodEssence() {
        return false;
    }
}
