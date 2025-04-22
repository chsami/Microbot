package net.runelite.client.plugins.microbot.mining;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.mining.enums.Rocks;
import net.runelite.client.plugins.microbot.util.inventory.InteractOrder;

@ConfigGroup("Mining")
@ConfigInformation("<h2>Auto Mining</h2>" +
        "<h3>Version: "+ AutoMiningScript.version + "</h3>" +
        "<p>1. <strong>Ore Selection:</strong> Choose the type of ore you wish to mine. The default ore is <em>TIN</em>.</p>" +
        "<p></p>"+
        "<p>2. <strong>Distance to Stray:</strong> Set the maximum distance in tiles that the bot can travel from its initial position. The default distance is <em>20 tiles</em>.</p>" +
        "<p></p>"+
        "<p>3. <strong>Banking Option:</strong> Enable or disable the use of a bank. If enabled, the bot will walk back to the original location after banking. The default setting is <em>disabled</em>.</p>" +
        "<p></p>"+
        "<p>4. <strong>Items to Bank:</strong> Specify the items to be banked, separated by commas. The default value is <em>'ore'</em>.</p>"+
        "<p></p>"+
        "<p>5. <strong>Basalt:</strong> If mining basalt, ensure UseBank is checked and it will automatically note at Snowflake</em>.</p>")

public interface AutoMiningConfig extends Config {
    @ConfigSection(
            name = "General",
            description = "General",
            position = 0
    )
    String generalSection = "general";

    @ConfigSection(
            name = "Dropping",
            description = "Dropping settings",
            position = 1
    )
    String droppingSection = "droppingSection";

    @ConfigSection(
            name = "Banking",
            description = "Banking settings",
            position = 2
    )
    String bankingSection = "bankingSection";

    @ConfigItem(
            keyName = "Ore",
            name = "Ore",
            description = "Choose the ore",
            position = 0,
            section = generalSection
    )
    default Rocks ORE()
    {
        return Rocks.TIN;
    }

    @ConfigItem(
            keyName = "DistanceToStray",
            name = "Distance to Stray",
            description = "Set how far you can travel from your initial position in tiles",
            position = 2,
            section = generalSection
    )
    default int distanceToStray()
    {
        return 20;
    }

    @ConfigItem(
            keyName = "maxPlayersInArea",
            name = "Max players in area",
            description = "If more players than this are nearby, hop worlds. 0 = disable",
            position = 3,
            section = generalSection
    )
    default int maxPlayersInArea() {
        return 0;
    }

    @ConfigItem(
            keyName = "UseBank",
            name = "UseBank",
            description = "Use bank and walk back to original location",
            position = 0,
            section = bankingSection
    )
    default boolean useBank()
    {
        return false;
    }

    @ConfigItem(
            keyName = "ItemsToBank",
            name = "Items to bank (Comma seperated)",
            description = "Items to bank",
            position = 1,
            section = bankingSection
    )
    default String itemsToBank() {
        return "ore";
    }

    @ConfigItem(
            keyName = "dropOrder",
            name = "Drop Order",
            description = "Order for dropping items",
            position = 0,
            section = droppingSection
    )
    default InteractOrder interactOrder() {
        return InteractOrder.STANDARD;
    }

    @ConfigItem(
            keyName = "itemsToKeep",
            name = "Items to keep (Comma seperated)",
            description = "Items to keep when dropping ore",
            position = 1,
            section = droppingSection
    )
    default String itemsToKeep() {
        return "pickaxe";
    }
}