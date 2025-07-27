package net.runelite.client.plugins.microbot.TaF.GemCrabKiller;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;

@ConfigGroup("GemCrabKiller")
@ConfigInformation("This plugin kills the Gem Crab boss. if inventory setups is enabled, the bot will handle the full banking and restocking loop, otherwise, it will kill the crab till out of supplies or the inventory is full with loot.")
public interface GemCrabKillerConfig extends Config {
    @ConfigItem(
            keyName = "useInventorySetup",
            name = "Use Inventory Setup?",
            description = "When enabled, the bot will use the specified inventory setup for banking and supplies",
            position = 1
    )
    default boolean useInventorySetup() {
        return false;
    }

    @ConfigItem(
            keyName = "inventorySetup",
            name = "Inventory Setup",
            description = "The inventory setup to use for banking and supplies",
            position = 2
    )
    default InventorySetup inventorySetup() {
        return null;
    }

    @ConfigItem(
            keyName = "useOffensivePotions",
            name = "Use Offensive Potions?",
            description = "When enabled, the bot will use offensive potions during combat similar to AIO Fighter",
            position = 3
    )
    default boolean useOffensivePotions() {
        return false;
    }

    @ConfigItem(
            keyName = "lootCrab",
            name = "Mine and loot the Gem Crab?",
            description = "When enabled, the bot will attempt to mine the Gem Crab after the kill",
            position = 4
    )
    default boolean lootCrab() {
        return false;
    }

    @ConfigItem(
            keyName = "dharokMode",
            name = "Dharok mode?",
            description = "When enabled, the bot will stay below 10hp",
            position = 4
    )
    default boolean dharokMode() {
        return false;
    }
    @ConfigItem(
            keyName = "overrideState",
            name = "Override state?",
            description = "Enable to override script starting state",
            position = 5
    )
    default boolean overrideState() {
        return false;
    }

    @ConfigItem(
            keyName = "startState",
            name = "Starting state",
            description = "The starting state of the bot. This is only used if override state is enabled.",
            position = 6
    )
    default GemCrabKillerState startState() {
        return GemCrabKillerState.FIGHTING;
    }
}
