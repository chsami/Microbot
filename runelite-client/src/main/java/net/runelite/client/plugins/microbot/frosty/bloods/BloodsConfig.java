package net.runelite.client.plugins.microbot.frosty.bloods;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.frosty.bloods.enums.HomeTeleports;
import net.runelite.client.plugins.microbot.frosty.bloods.enums.Teleports;

@ConfigGroup("Frosty")
public interface BloodsConfig extends Config {
    String toggleRunesCrafted = "toggleRunesCrafted";
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
    default Teleports teleports() {return Teleports.CRAFTING_CAPE;
    }
    @ConfigItem(
            keyName = "home teleports",
            name = "Going home",
            description = "Method of getting to POH",
            position = 2,
            section = settingsSection
    )
    default HomeTeleports homeTeleports() { return HomeTeleports.CONSTRUCTION_CAPE;}

    @ConfigItem(
            keyName = "useBloodEssence",
            name = "Use Blood Essence",
            description = "Check this if you want to use Blood Essence during runecrafting.",
            position = 3
    )
    default boolean useBloodEssence() {
        return false;
    }

    @ConfigSection(
            name = "Overlay",
            description = "Overlay Settings",
            position = 2
    )
    String overlaySection = "overlay";

    @ConfigItem(
            keyName = toggleRunesCrafted,
            name = "Toggle Runes Crafted",
            description = "Hide runes crafted",
            position = 0,
            section = overlaySection
    )
    default boolean toggleRunesCrafted() {
        return false;
    }
}
