package net.runelite.client.plugins.microbot.vorkath;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("Vorkath Config")
public interface VorkathConfig extends Config {
    @ConfigItem(
            keyName = "guide",
            name = "How to use",
            description = "How to use this plugin",
            position = 0
    )
    default String GUIDE() {
        return "1.Use the equipment inventory plugin to setup your plugin and give it the name 'vorkath'\n" +
                "More information about inventory plugin: https://github.com/dillydill123/inventory-setups?tab=readme-ov-file#creating-a-new-setup" +
                "2.Make sure to start at a bank";
    }

    @ConfigSection(
            name = "Loot",
            description = "Loot",
            position = 3
    )
    String lootSection = "Loot";

    @ConfigSection(
            name = "Teleports",
            description = "Teleports",
            position = 4
    )
    String teleportSection = "Teleports";

    @ConfigSection(
            name = "POH",
            description = "POH",
            position = 5
    )
    String pohSection = "POH";

    @ConfigSection(
            name = "Prayers",
            description = "Prayers",
            position = 6
    )
    String prayerSection = "Prayers";

    @ConfigItem(
            keyName = "Teleport",
            name = "Teleport",
            description = "Choose your mode of Teleport",
            position = 0,
            section = teleportSection
    )
    default Teleport teleportMode()
    {
        return Teleport.VARROCK_TAB;
    }

    @ConfigItem(
            keyName = "Price of items to loot",
            name = "Price of items to loot",
            description = "Price of items to loot comma seperated",
            position = 0,
            section = lootSection
    )
    default int priceOfItemsToLoot()
    {
        return 5000;
    }

    @ConfigItem(
            keyName = "SellItemsAtXKills",
            name = "Sell items every X kills",
            description = "Sell items every X kills",
            position = 1,
            section = lootSection
    )
    default int SellItemsAtXKills()
    {
        return 15;
    }

    @ConfigItem(
            keyName = "ItemsToNotSell",
            name = "Items to not sell",
            description = "Items to not sell comma seperated",
            position = 2,
            section = lootSection
    )
    default String ItemsToNotSell()
    {
        return "item1,item2";
    }

    @ConfigItem(
            keyName = "KillsPerTrip",
            name = "KC per Trip",
            description = "kills per trip before banking(0 to disable it)",
            position = 1,
            section = lootSection
    )
    default int KillsPerTrip() {
        return 0;
    }

    @ConfigItem(
            keyName = "PohInRellekka",
            name = "POH in Rellekka",
            description = "Teleport to POH in Rellekka and exit via portal",
            position = 0,
            section = pohSection
    )
    default boolean pohInRellekka() {
    return false;
    }

    @ConfigItem(
            keyName = "RejuvenationPool",
            name = "Rejuvenation Pool",
            description = "Use POH rejuv Pool to restore stats",
            position = 1,
            section = pohSection
    )
    default boolean rejuvinationPool() {
        return false;
    }

    @ConfigItem(
            keyName = "Rigour",
            name = "Rigour",
            description = "Activate Rigour? (Make sure you have it unlocked and have 74 prayer!)",
            position = 4,
            section = prayerSection
    )
    default boolean activateRigour() {
        return false;
    }
}
