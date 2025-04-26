package net.runelite.client.plugins.microbot.OreWorks;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.mining.enums.AltOre;
import net.runelite.client.plugins.microbot.mining.enums.Rocks;
import net.runelite.client.plugins.microbot.smelting.enums.Bars;
import net.runelite.client.plugins.microbot.util.inventory.InteractOrder;

@ConfigGroup("OreWorks")
@ConfigInformation(
        "<h2>OreWorks</h2>" +
                "<h3>Version: " + OreWorksScript.version + "</h3>" +
                "<p>Your all-in-one mining, smelting, and banking assistant.</p>" +
                "<p><strong>Steel Bar Setup:</strong> Mine Iron + Coal → Smelt → Bank Steel Bars.</p>"
)
public interface OreWorksConfig extends Config {

    @ConfigSection(
            name = "Mining",
            description = "Mining behavior and movement settings",
            position = 0
    )
    String miningSection = "mining";

    @ConfigSection(
            name = "Smelting",
            description = "Smelting settings when inventory is full",
            position = 1
    )
    String smeltingSection = "smelting";

    @ConfigSection(
            name = "Banking",
            description = "Options for banking ores and bars",
            position = 2
    )
    String bankingSection = "banking";

    @ConfigSection(
            name = "Dropping",
            description = "Options for dropping ores instead of banking",
            position = 3
    )
    String droppingSection = "dropping";

    // ---- MINING SECTION ----

    @ConfigItem(
            keyName = "ore",
            name = "Ore",
            description = "Ore to mine",
            position = 0,
            section = miningSection
    )
    default Rocks ORE() { return Rocks.TIN; }

    @ConfigItem(
            keyName = "distanceToStray",
            name = "Max Distance",
            description = "Max distance from start",
            position = 1,
            section = miningSection
    )
    default int distanceToStray() { return 20; }

    @ConfigItem(
            keyName = "maxPlayersInArea",
            name = "Max Players Nearby",
            description = "Hop worlds if more than this amount (0 = disable)",
            position = 2,
            section = miningSection
    )
    default int maxPlayersInArea() { return 0; }

    // ---- SMELTING SECTION ----

    @ConfigItem(
            keyName = "smelt",
            name = "Auto Smelt",
            description = "Enable smelting after full inventory",
            position = 0,
            section = smeltingSection
    )
    default boolean smelt() { return false; }

    @ConfigItem(
            keyName = "mineCoal",
            name = "Mine Coal",
            description = "Mine coal if needed for bars",
            position = 1,
            section = smeltingSection
    )
    default boolean mineCoal() { return false; }

    @ConfigItem(
            keyName = "coalAmount",
            name = "Coal Amount",
            description = "Coal needed before resuming",
            position = 2,
            section = smeltingSection
    )
    default int coalAmount() { return 0; }

    @ConfigItem(
            keyName = "altOre",
            name = "Alternative Ore",
            description = "Second ore (Coal/Tin/Copper)",
            position = 3,
            section = smeltingSection
    )
    default AltOre altOre() { return AltOre.COAL; }

    // ---- BANKING SECTION ----

    @ConfigItem(
            keyName = "useBank",
            name = "Use Bank",
            description = "Bank ores/bars instead of dropping",
            position = 0,
            section = bankingSection
    )
    default boolean useBank() { return false; }

    @ConfigItem(
            keyName = "itemsToBank",
            name = "Items to Bank",
            description = "Comma-separated list, e.g., ore, coal",
            position = 1,
            section = bankingSection
    )
    default String itemsToBank() { return "ore"; }

    @ConfigItem(
            keyName = "barsToBank",
            name = "Bars to Bank",
            description = "Select which bars to bank",
            position = 2,
            section = bankingSection
    )
    default Bars barsToBank() { return Bars.BRONZE; }

    // ---- DROPPING SECTION ----

    @ConfigItem(
            keyName = "dropOrder",
            name = "Drop Order",
            description = "Inventory drop pattern: Standard/Custom",
            position = 0,
            section = droppingSection
    )
    default InteractOrder interactOrder() { return InteractOrder.STANDARD; }

    @ConfigItem(
            keyName = "itemsToKeep",
            name = "Items to Keep",
            description = "Items not to drop (e.g., Pickaxe)",
            position = 1,
            section = droppingSection
    )
    default String itemsToKeep() { return "pickaxe"; }
}