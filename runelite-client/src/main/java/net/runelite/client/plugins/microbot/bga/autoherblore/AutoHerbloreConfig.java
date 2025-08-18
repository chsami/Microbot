package net.runelite.client.plugins.microbot.bga.autoherblore;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

import net.runelite.client.plugins.microbot.bga.autoherblore.enums.CleanHerbMode;
import net.runelite.client.plugins.microbot.bga.autoherblore.enums.HerblorePotion;
import net.runelite.client.plugins.microbot.bga.autoherblore.enums.Mode;
import net.runelite.client.plugins.microbot.bga.autoherblore.enums.UnfinishedPotionMode;

@ConfigGroup("AutoHerblore")
public interface AutoHerbloreConfig extends Config {
    @ConfigSection(
        name = "General",
        description = "General settings",
        position = 0
    )
    String GENERAL_SECTION = "general";

    @ConfigItem(
        keyName = "mode",
        name = "Mode",
        description = "Select herblore mode",
        section = GENERAL_SECTION
    )
    default Mode mode() {
        return Mode.CLEAN_HERBS;
    }


    @ConfigSection(
        name = "Clean herbs",
        description = "Clean herb options",
        position = 1,
        closedByDefault = true
    )
    String CLEAN_HERBS_SECTION = "cleanHerbs";

    @ConfigItem(
        keyName = "cleanHerbMode",
        name = "Herb selection",
        description = "Select specific herb to clean or clean any available",
        section = CLEAN_HERBS_SECTION
    )
    default CleanHerbMode cleanHerbMode() {
        return CleanHerbMode.ANY_AND_ALL;
    }

    @ConfigSection(
        name = "Make unfinished potions",
        description = "Unfinished potion options",
        position = 2,
        closedByDefault = true
    )
    String UNFINISHED_POTIONS_SECTION = "unfinishedPotions";

    @ConfigItem(
        keyName = "unfinishedPotionMode",
        name = "Unfinished potion selection",
        description = "Select specific unfinished potion to make or make any available",
        section = UNFINISHED_POTIONS_SECTION
    )
    default UnfinishedPotionMode unfinishedPotionMode() {
        return UnfinishedPotionMode.ANY_AND_ALL;
    }

    @ConfigSection(
        name = "Make finished potions",
        description = "Finished potion options",
        position = 3,
        closedByDefault = true
    )
    String FINISHED_POTIONS_SECTION = "finishedPotions";

    @ConfigItem(
        keyName = "finishedPotion",
        name = "Finished potion",
        description = "Select finished potion to make",
        section = FINISHED_POTIONS_SECTION
    )
    default HerblorePotion finishedPotion() {
        return HerblorePotion.ATTACK;
    }

    @ConfigSection(
        name = "Optionals",
        description = "Optional features",
        position = 4,
        closedByDefault = true
    )
    String OPTIONALS_SECTION = "optionals";

    @ConfigItem(
        keyName = "useAmuletOfChemistry",
        name = "Amulet of chem. or Alch. amulet",
        description = "Automatically withdraw and equip Amulet of Chemistry when making finished potions. Will re-equip when it breaks.",
        section = OPTIONALS_SECTION
    )
    default boolean useAmuletOfChemistry() {
        return false;
    }
}

