package net.runelite.client.plugins.microbot.mining;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.mining.enums.Rocks;

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
        "<p>5. <strong>Basalt:</strong> If mining basalt, ensure UseBank is checked and it will automatically note at Snowflake</em>.</p>"+
        "<p></p>"+
        "<p>6. <strong>Drop ores in inventory:</strong> If enabled, the bot will drop all ores in the inventory when its full. The default setting is <em>disabled</em>.</p>")

public interface AutoMiningConfig extends Config {
    @ConfigSection(
            name = "General",
            description = "General",
            position = 0
    )
    String generalSection = "general";

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
            keyName = "UseBank",
            name = "UseBank",
            description = "Use bank and walk back to original location",
            position = 3,
            section = generalSection
    )
    default boolean useBank()
    {
        return false;
    }

    @ConfigItem(
            keyName = "ItemsToBank",
            name = "Items to bank (Comma seperated)",
            description = "Items to bank",
            position = 4
    )
    default String itemsToBank() {
        return "ore";
    }

    @ConfigItem(
            keyName = "dropOres",
            name = "dropOres",
            description = "Drop all ores in inventory once full",
            position = 5,
            section = generalSection
    )
    default boolean dropOres()
    {
        return false;
    }
}