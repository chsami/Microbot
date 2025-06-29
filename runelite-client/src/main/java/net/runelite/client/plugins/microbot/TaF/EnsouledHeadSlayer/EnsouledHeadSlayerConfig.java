package net.runelite.client.plugins.microbot.TaF.EnsouledHeadSlayer;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;

@ConfigInformation("Farms all the ensouled heads in your bank, equip all your combat gear before starting the script.")
@ConfigGroup("EnsouledHeadSlayer")
public interface EnsouledHeadSlayerConfig extends Config {
    @ConfigItem(
            keyName = "food",
            name = "Food",
            description = "Which food should be used?",
            position = 1
    )
    default Rs2Food food() {
        return Rs2Food.KARAMBWAN;
    }
    @ConfigItem(
            keyName = "foodAmount",
            name = "Food amount",
            description = "How much food to use?",
            position = 2
    )
    @Range(
            min = 1,
            max = 20
    )
    default int foodAmount() {
        return 6;
    }
    @ConfigItem(
            keyName = "useGamesNecklaceForBanking",
            name = "Use games necklace for banking",
            description = "Use games necklace for banking?",
            position = 3
    )
    default boolean useGamesNecklaceForBanking() {
        return false;
    }

    @ConfigItem(
            keyName = "useArcheusLibraryTeleport",
            name = "Use Archeus Library teleport",
            description = "Use Archeus Library teleport for faster transport?",
            position = 4
    )
    default boolean useArcheusLibraryTeleport() {
        return false;
    }

    @ConfigItem(
            keyName = "useInventorySetup",
            name = "Use Inventory Setup",
            description = "Uses a predefined inventory setup. This overrides other options.",
            position = 5
    )
    default boolean useInventorySetup() {
        return false;
    }

    @ConfigItem(
            keyName = "inventorySetup",
            name = "Inventory Setup",
            description = "Uses a predefined inventory setup. This overrides other options.",
            position = 6
    )
    default InventorySetup inventorySetup() {
        return null;
    }
    @ConfigItem(
            keyName = "ensouledHeads",
            name = "Ensouled head",
            description = "Which ensouled head to farm?",
            position = 7
    )
    default EnsouledHeads ensouledHeads() {
        return EnsouledHeads.IMP;
    }

    @ConfigItem(
            keyName = "startingState",
            name = "Override starting state",
            description = "Override the starting state of the script.",
            position = 8
    )
    default EnsouledHeadSlayerStatus startingState() {
        return EnsouledHeadSlayerStatus.BANKING;
    }
}
