package net.runelite.client.plugins.microbot.bga.autofishing;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.bga.autofishing.enums.Fish;
import net.runelite.client.plugins.microbot.bga.autofishing.enums.HarpoonType;

@ConfigGroup("AutoFishing")
public interface AutoFishingConfig extends Config {
    
    @ConfigSection(
            name = "General",
            description = "General",
            position = 0
    )
    String GENERAL_SECTION = "general";
    
    @ConfigSection(
            name = "Banking",
            description = "Banking configuration",
            position = 1
    )
    String BANKING_SECTION = "banking";

    @ConfigItem(
            keyName = "fish",
            name = "Fish",
            description = "Choose the fish",
            position = 0,
            section = GENERAL_SECTION
    )
    default Fish fish() {
        return Fish.SHRIMP;
    }

    @ConfigItem(
            keyName = "harpoonSpec",
            name = "Harpoon spec",
            description = "Choose the harpoon type",
            position = 1,
            section = GENERAL_SECTION
    )
    default HarpoonType harpoonSpec() {
        return HarpoonType.NONE;
    }

    @ConfigItem(
            keyName = "useBank",
            name = "Use bank",
            description = "Use bank or deposit box and walk back to original location",
            position = 0,
            section = BANKING_SECTION
    )
    default boolean useBank() {
        return false;
    }

    @ConfigItem(
            keyName = "bankClueBottles",
            name = "Clue bottles",
            description = "Should bank clue bottles",
            position = 1,
            section = BANKING_SECTION
    )
    default boolean shouldBankClueBottles() {
        return true;
    }

    @ConfigItem(
            keyName = "bankCaskets",
            name = "Caskets",
            description = "Should bank caskets",
            position = 2,
            section = BANKING_SECTION
    )
    default boolean shouldBankCaskets() {
        return true;
    }

    @ConfigItem(
            keyName = "bankScrollBoxes",
            name = "Scroll boxes",
            description = "Should bank scroll boxes",
            position = 3,
            section = BANKING_SECTION
    )
    default boolean shouldBankScrollBoxes() {
        return true;
    }
}