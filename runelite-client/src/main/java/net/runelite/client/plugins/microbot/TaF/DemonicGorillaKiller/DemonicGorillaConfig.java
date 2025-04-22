package net.runelite.client.plugins.microbot.TaF.DemonicGorillaKiller;

import lombok.Getter;
import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

@ConfigInformation("IMPORTANT!<br/>"
        + "This plugin kills Demonic Gorillas, including combat, prayer, banking, and restocking.<br/><br/>"
        + "<p>Have the following in your bank and inventory setup:</p>\n"
        + "<ol>\n"
        + "    <li><b>Inventory Setup named \"Demonic Gorilla\"</b> – includes all required supplies</li>\n"
        + "    <li>Royal Seed Pot</li>\n"
        + "</ol>\n"
        + "By default, it will auto loot most drops and alch rune items while retreating when low on supplies to restock.<br/><br/>"
        + "<br/>"
        + "Configure options in the settings to enable offensive prayers, auto gear change, and looting preferences.<br/>"
        + "</html>")


@ConfigGroup("Demonic Gorillas")
public interface DemonicGorillaConfig extends Config {
    @ConfigSection(
            name = "Demonic Gorilla settings",
            description = "Settings for Demonic Gorilla Slayer",
            position = 1
    )
    String demonicGorillaSection = "DemonicGorilla";
    @ConfigSection(
            name = "Looting",
            description = "Settings for item looting",
            position = 2
    )
    String lootingSection = "looting";
    @ConfigSection(
            name = "Food and Potions",
            description = "Settings for banking and required supplies",
            position = 3
    )
    String bankingAndSuppliesSection = "bankingAndSupplies";
    @ConfigSection(
            name = "Gear Settings",
            description = "Specify gear sets and combat styles for switching",
            position = 4,
            closedByDefault = true
    )
    String gearSettingsSection = "gearSettings";

    @ConfigItem(
            keyName = "enableOffensivePrayer",
            name = "Enable Offensive Prayers",
            description = "Toggle to enable or disable offensive prayers during combat",
            section = demonicGorillaSection,
            position = 2
    )
    default boolean enableOffensivePrayer() {
        return false;
    }

    @ConfigItem(
            keyName = "enableSpecialAttacks",
            name = "Enable Automatical special attacks",
            description = "Toggle to enable or disable automatic special attacks during combat - Assumes 50% special energy for weapon",
            section = demonicGorillaSection,
            position = 3
    )
    default boolean enableAutoSpecialAttacks() {
        return false;
    }

    @ConfigItem(
            keyName = "lootItems",
            name = "Loot Items",
            description = "Comma-separated list of item names to loot regardless of value",
            section = lootingSection,
            position = 0
    )
    default String lootItems() {
        return "Zenyte shard,Ballista spring,Ballista limbs,Ballista frame,Monkey tail,Heavy frame,Light frame,Rune platelegs,Rune plateskirt,Rune chainbody,Dragon scimitar,Law rune,Death rune,Runite bolts,Grimy kwuarm,Grimy cadantine,Grimy dwarf weed,Grimy lantadyme,Ranarr seed,Snapdragon seed,Torstol seed,Yew seed,Magic seed,Palm tree seed,Spirit seed,Dragonfruit tree seed,Celastrus seed,Redwood tree seed,Prayer potion(3),Shark,Coins,Saradomin brew(2),Rune javelin heads,Dragon javelin heads,Adamantite bar,Diamond,Runite bar";
    }

    @ConfigItem(
            keyName = "scatterAshes",
            name = "Scatter Ashes",
            description = "Scatter Malicious ashes upon looting",
            section = lootingSection,
            position = 2
    )
    default boolean scatterAshes() {
        return false;
    }

    @ConfigItem(
            keyName = "lootMyLootOnly",
            name = "Only loot my loot",
            description = "Only loot your own loot",
            section = lootingSection,
            position = 3
    )
    default boolean lootMyLootOnly() {
        return false;
    }

    @ConfigItem(
            keyName = "minEatPercent",
            name = "Minimum Health Percent",
            description = "Percentage of health below which the bot will eat food",
            section = bankingAndSuppliesSection,
            position = 0
    )
    default int minEatPercent() {
        return 65;
    }

    @ConfigItem(
            keyName = "minPrayerPercent",
            name = "Minimum Prayer Percent",
            description = "Percentage of prayer points below which the bot will drink a prayer potion",
            section = bankingAndSuppliesSection,
            position = 1
    )
    default int minPrayerPercent() {
        return 30;
    }

    @ConfigItem(
            keyName = "healthThreshold",
            name = "Health Threshold to Exit",
            description = "Minimum health percentage to stay and fight",
            section = bankingAndSuppliesSection,
            position = 2
    )
    default int healthThreshold() {
        return 70;
    }

    @ConfigItem(
            keyName = "boostedStatsThreshold",
            name = "% Boosted Stats Threshold",
            description = "The threshold for using a potion when the boosted stats are below the maximum.",
            section = bankingAndSuppliesSection,
            position = 5
    )
    @Range(
            min = 1,
            max = 100
    )
    default int boostedStatsThreshold() {
        return 5;
    }

    @ConfigItem(
            keyName = "gearSetup",
            name = "Gear & Inventory setup",
            description = "Gear and inventory setup that is used for banking",
            section = gearSettingsSection,
            position = 0
    )
    default InventorySetup gearSetup() {
        return null;
    }

    @ConfigItem(
            keyName = "useRangeStyle",
            name = "Use Range Style",
            description = "Toggle to use range gear and style",
            section = gearSettingsSection,
            position = 1
    )
    default boolean useRangeStyle() {
        return false;
    }

    @ConfigItem(
            keyName = "rangeGear",
            name = "Range Gear",
            description = "Inventory setup to use when using ranged",
            section = gearSettingsSection,
            position = 2
    )
    default InventorySetup rangeGear() {
        return null;
    }

    @ConfigItem(
            keyName = "useMagicStyle",
            name = "Use Magic Style",
            description = "Toggle to use magic gear and style",
            section = gearSettingsSection,
            position = 3
    )
    default boolean useMagicStyle() {
        return false;
    }

    @ConfigItem(
            keyName = "magicGear",
            name = "Magic Gear",
            description = "Inventory setup to use when using magic",
            section = gearSettingsSection,
            position = 4
    )
    default InventorySetup magicGear() {
        return null;
    }

    @ConfigItem(
            keyName = "useMeleeStyle",
            name = "Use Melee Style",
            description = "Toggle to use melee gear and style",
            section = gearSettingsSection,
            position = 5
    )
    default boolean useMeleeStyle() {
        return false;
    }

    @ConfigItem(
            keyName = "meleeGear",
            name = "Melee Gear",
            description = "Inventory setup to use when using melee",
            section = gearSettingsSection,
            position = 6
    )
    default InventorySetup meleeGear() {
        return null;
    }
}



