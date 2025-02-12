package net.runelite.client.plugins.microbot.looter;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.looter.enums.DefaultLooterStyle;
import net.runelite.client.plugins.microbot.looter.enums.FlaxLocations;
import net.runelite.client.plugins.microbot.looter.enums.LooterActivity;
import net.runelite.client.plugins.microbot.looter.enums.NatureRuneChestLocations;

@ConfigGroup("AutoLooter")
public interface AutoLooterConfig extends Config {

    @ConfigSection(
            name = "General",
            description = "Configure global plugin settings",
            position = 0,
            closedByDefault = false
    )
    String generalSection = "Configure global plugin settings";
    @ConfigSection(
            name = "Default",
            description = "Configure Default Activity",
            position = 1,
            closedByDefault = true
    )
    String defaultSection = "Configure Default Activity Settings";
    @ConfigSection(
            name = "Flax",
            description = "Configure Flax Activity",
            position = 1,
            closedByDefault = true
    )
    String flaxSection = "Configure Flax Activity Settings";
    @ConfigSection(
            name = "Nature Rune Chest",
            description = "Configure Nature Rune Chest Activity",
            position = 2,
            closedByDefault = true
    )
    String natureRuneChestSection = "Configure Nature Rune Chest Activity Settings";

    @ConfigItem(
            name = "Activity",
            keyName = "lootingActivity",
            position = 0,
            description = "Choose Looting Activity",
            section = generalSection
    )
    default LooterActivity looterActivity() {
        return LooterActivity.DEFAULT;
    }

    @Range(max = 28)
    @ConfigItem(
            name = "Min. free slots",
            keyName = "minFreeSlots",
            position = 1,
            description = "Minimum amount of slots",
            section = generalSection
    )
    default int minFreeSlots() {
        return 2;
    }

    @ConfigItem(
            keyName = "Hop",
            name = "Auto World Hop",
            description = "Auto Hop when no loot is found within 5 attempts or when player is detected",
            position = 2,
            section = generalSection
    )
    default boolean worldHop() {
        return false;
    }

    @ConfigItem(
            keyName = "useNextWorld",
            name = "Use Next World",
            description = "When enabled, it will hop to the next world, instead of using a random world",
            position = 3,
            section = generalSection
    )
    default boolean useNextWorld() {
        return false;
    }

    @ConfigItem(
            name = "Loot Style",
            keyName = "lootStyle",
            position = 0,
            description = "Choose Looting Style",
            section = defaultSection
    )
    default DefaultLooterStyle looterStyle() {
        return DefaultLooterStyle.ITEM_LIST;
    }

    @ConfigItem(
            name = "Distance to Stray",
            keyName = "distanceToStray",
            position = 1,
            description = "Radius of tiles to stray/look for items",
            section = defaultSection
    )
    default int distanceToStray() {
        return 20;
    }

    @ConfigItem(
            name = "List of Items",
            keyName = "listOfItemsToLoot",
            position = 2,
            description = "List of items to loot",
            section = defaultSection
    )
    default String listOfItemsToLoot() {
        return "bones,ashes";
    }

    @ConfigItem(
            name = "Min. GE price of Item",
            keyName = "minGEPriceOfItem",
            position = 3,
            description = "Minimum GE price of item to loot",
            section = defaultSection
    )
    default int minPriceOfItem() {
        return 1000;
    }

    @ConfigItem(
            name = "Max GE price of Item",
            keyName = "maxGEPriceOfItem",
            position = 4,
            description = "Maximum GE price of item to loot",
            section = defaultSection
    )
    default int maxPriceOfItem() {
        return Integer.MAX_VALUE;
    }

    @ConfigItem(
            name = "Loot My Items Only",
            keyName = "lootMyItemsOnly",
            position = 5,
            description = "Toggles check for ownership of grounditem",
            section = defaultSection
    )
    default boolean toggleLootMyItemsOnly() {
        return false;
    }

    @ConfigItem(
            name = "Delayed Looting",
            keyName = "delayedLooting",
            position = 6,
            description = "Toggles Delayed Looting",
            section = defaultSection
    )
    default boolean toggleDelayedLooting() {
        return false;
    }

    @ConfigItem(
            name = "Location",
            keyName = "pickLocation",
            position = 0,
            description = "Choose location where to pick flax",
            section = flaxSection
    )
    default FlaxLocations flaxLocation() {
        return FlaxLocations.SEERS_VILLAGE;
    }

    @ConfigItem(
            name = "Location",
            keyName = "natureRuneLocation",
            position = 0,
            description = "Choose location where to loot nature runes",
            section = natureRuneChestSection
    )
    default NatureRuneChestLocations natureRuneChestLocation() {
        return NatureRuneChestLocations.EAST_ARDOUGNE;
    }
}
