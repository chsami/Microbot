package net.runelite.client.plugins.microbot.bossing.giantmole;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.aiofighter.enums.DefaultLooterStyle;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
@ConfigInformation("<h2>S-1D Giant Mole</h2>\n" +
        "<h3>BETA PREVIEW 1</h3>\n" +
        "<p>1. <strong>Select your Giant Mole inventory setup</strong>.</p>\n" +
        "<p>2. <strong>Include a teleport to Falador</strong> for banking.</p>\n" +
        "<p>3. <strong>Start in Falador</strong> with either an empty inventory or your complete setup.</p>\n" +
        "<p>4. <strong>Falador hard diary</strong> is required for the mole locator.</p>\n" +
        "<p>5. <strong>Spec Weapon</strong> use QoL to select spec weapon.</p>\n" +
        "<p></p>\n" +
        "<p><strong>FEEDBACK:</strong> If you encounter any bugs or need assistance, please message us on Discord.</p>\n"
)
@ConfigGroup("giantmole")
public interface GiantMoleConfig extends Config {

    @ConfigSection(
            name = "lootSection",
            description = "Looting settings",
            position = 1
    )
    String lootSection = "looting";

    @ConfigItem(
            keyName = "useQuickPrayer",
            name = "Use quick prayer",
            description = "Use quick prayer",
            position = 0
    )
    default boolean useQuickPrayer() {
        return true;
    }

    // config item for inventory setup
    @ConfigItem(
            keyName = "inventorySetup",
            name = "Inventory Setup",
            description = "Inventory Setup",
            position = 1
    )
    default InventorySetup inventorySetup() {
        return null;
    }

    //Use rock cake
    @ConfigItem(
            keyName = "useRockCake",
            name = "Use rock cake",
            description = "Will rock cake down to 1hp",
            position = 101
    )
    default boolean useRockCake() { return false; }

    @ConfigItem(
            keyName = "Loot items",
            name = "Auto loot items",
            description = "Enable/disable loot items",
            position = 0,
            section = lootSection
    )
    default boolean toggleLootItems() {
        return true;
    }

    @ConfigItem(
            name = "Loot Style",
            keyName = "lootStyle",
            position = 1,
            description = "Choose Looting Style",
            section = lootSection
    )
    default DefaultLooterStyle looterStyle() {
        return DefaultLooterStyle.MIXED;
    }

    @ConfigItem(
            name = "List of Items",
            keyName = "listOfItemsToLoot",
            position = 2,
            description = "List of items to loot",
            section = lootSection
    )
    default String listOfItemsToLoot() {
        return "bones,ashes";
    }

    @ConfigItem(
            keyName = "Min Price of items to loot",
            name = "Min. Price of items to loot",
            description = "Min. Price of items to loot",
            position = 10,
            section = lootSection
    )
    default int minPriceOfItemsToLoot() {
        return 5000;
    }

    @ConfigItem(
            keyName = "Max Price of items to loot",
            name = "Max. Price of items to loot",
            description = "Max. Price of items to loot default is set to 10M",
            position = 11,
            section = lootSection
    )
    default int maxPriceOfItemsToLoot() {
        return 10000000;
    }
    // toggle scatter

    @ConfigItem(
            keyName = "Loot arrows",
            name = "Auto loot arrows",
            description = "Enable/disable loot arrows",
            position = 20,
            section = lootSection
    )
    default boolean toggleLootArrows() {
        return false;
    }

    // toggle loot runes
    @ConfigItem(
            keyName = "Loot runes",
            name = "Loot runes",
            description = "Enable/disable loot runes",
            position = 30,
            section = lootSection
    )
    default boolean toggleLootRunes() {
        return false;
    }

    // toggle loot coins
    @ConfigItem(
            keyName = "Loot coins",
            name = "Loot coins",
            description = "Enable/disable loot coins",
            position = 40,
            section = lootSection
    )
    default boolean toggleLootCoins() {
        return false;
    }

    // toggle loot untreadables
    @ConfigItem(
            keyName = "Loot untradables",
            name = "Loot untradables",
            description = "Enable/disable loot untradables",
            position = 50,
            section = lootSection
    )
    default boolean toggleLootUntradables() {
        return false;
    }

    @ConfigItem(
            keyName = "Bury Bones",
            name = "Bury Bones",
            description = "Picks up and Bury Bones",
            position = 96,
            section = lootSection
    )
    default boolean toggleBuryBones() {
        return false;
    }

    // only loot my items
    @ConfigItem(
            keyName = "onlyLootMyItems",
            name = "Only Loot My Items",
            description = "Only loot items that are dropped for/by you",
            position = 99,
            section = lootSection
    )
    default boolean toggleOnlyLootMyItems() {
        return false;
    }

    //Force loot regardless if we are in combat or not
    @ConfigItem(
            keyName = "forceLoot",
            name = "Force Loot",
            description = "Force loot regardless if we are in combat or not",
            position = 100,
            section = lootSection
    )
    default boolean toggleForceLoot() {
        return false;
    }

    // toggleHighAlchProfitable
    @ConfigItem(
            keyName = "toggleHighAlchProfitable",
            name = "High Alch Profitable",
            description = "Enable/disable high alch profitable items",
            position = 200
    )
    default boolean toggleHighAlchProfitable() {
        return false;
    }

}
