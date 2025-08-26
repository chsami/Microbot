package net.runelite.client.plugins.microbot.farmTreeRun;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.farmTreeRun.enums.FruitTreeEnum;
import net.runelite.client.plugins.microbot.farmTreeRun.enums.HardTreeEnums;
import net.runelite.client.plugins.microbot.farmTreeRun.enums.TreeEnums;

/**
 * Made by Acun
 */
@ConfigGroup("example")
@ConfigInformation("Start anywhere you want. <br><br> Required items in bank:\n" +
        "<ol>\n" +
        "    <li>5.000 gp</li>\n" +
        "    <li>Selected saplings</li>\n" +
        "    <li>Spade</li>\n" +
        "    <li>Rake</li>\n" +
        "    <li>Seed dibber</li>\n" +
        "    <li>Law rune (10)</li>\n" +
        "    <li>Fire rune (30)</li>\n" +
        "    <li>Air rune (30)</li>\n" +
        "    <li>Earth rune (30)</li>\n" +
        "    <li>Water rune (30)</li>\n" +
        "</ol>" +
        "<br> Recommended:\n" +
        "<ol>\n" +
        "    <li>Taverley teleport tab</li>\n" +
        "    <li>Skills necklace (2 to 6)</li>\n" +
        "</ol>" +
        "<br> Optional:\n" +
        "<ol>\n" +
        "    <li>Items for protection payment</li>\n" +
        "    <li>Filled Bottomless compost bucket</li>\n" +
        "</ol>" +
        "<br> Extra information:\n" +
        "<br> If you want to stop the script during your farm run (maybe it gets stuck or whatever reason), make sure to disable 'Banking' and disable patches you previously ran." +
        "Happy botting\n" +
        "<br><br>UI and new trees added thanks to Diogenes and T\n" +
        "<br><br> The tree order is as follows: GS Fruit → GS Tree → TGV Fruit → Farming Guild Tree → Farming Guild Fruit → Taverley → Falador → Lumbridge → Varrock → Brimhaven Fruit → Catherby Fruit → Fossil A/B/C → Lletya Fruit → Auburnvale Tree → Kastori Fruit → Avium Savannah Hardwood. <br><br>Patches are listed in the order they will be attended filtered by type\n"
)
public interface FarmTreeRunConfig extends Config {
    public static final boolean DEBUG_MODE = System.getProperty("java.vm.info", "").contains("sharing");

    /* =========================
     * Sections (as requested)
     * ========================= */
    @ConfigSection(
            name = "Sapling selection",
            description = "Choose which saplings to plant",
            position = 1
    )
    String saplingSection = "saplingSection";

    @ConfigSection(
            name = "Protection",
            description = "Configure payment (protection) per tree type",
            position = 2
    )
    String protectionSection = "protectionSection";

    @ConfigSection(
            name = "Gear",
            description = "General gear and run settings",
            position = 3
    )
    String gearSection = "gearSection";

    @ConfigSection(
            name = "Tree patches",
            description = "Select which regular tree patches to use",
            position = 4
    )
    String treePatchesSection = "treePatchesSection";

    @ConfigSection(
            name = "Fruit tree patches",
            description = "Select which fruit tree patches to use",
            position = 5
    )
    String fruitTreePatchesSection = "fruitTreePatchesSection";

    @ConfigSection(
            name = "Hardwood patches",
            description = "Select which hardwood patches to use",
            position = 6
    )
    String hardTreePatchesSection = "hardTreePatchesSection";

    /* =========================
     * Sapling selection
     * ========================= */
    @ConfigItem(
            keyName = "treeSapling",
            name = "Tree sapling",
            description = "Select tree sapling to use",
            position = 0,
            section = saplingSection
    )
    default TreeEnums selectedTree() { return TreeEnums.MAPLE; }

    @ConfigItem(
            keyName = "fruitTreeSapling",
            name = "Fruit tree sapling",
            description = "Select fruit tree sapling to use",
            position = 1,
            section = saplingSection
    )
    default FruitTreeEnum selectedFruitTree() { return FruitTreeEnum.PAPAYA; }

    @ConfigItem(
            keyName = "Fossil Island Tree",
            name = "Hard sapling",
            description = "Select Hard tree sapling to use",
            position = 2,
            section = saplingSection
    )
    default HardTreeEnums selectedHardTree() { return HardTreeEnums.MAHOGANY; }

    /* =========================
     * Protection
     * ========================= */
    @ConfigItem(
            keyName = "protectTree",
            name = "Protect trees",
            description = "Do you want to protect your trees?",
            position = 0,
            section = protectionSection
    )
    default boolean protectTrees() { return true; }

    @ConfigItem(
            keyName = "protectFruitTree",
            name = "Protect fruit trees",
            description = "Do you want to protect your fruit trees?",
            position = 1,
            section = protectionSection
    )
    default boolean protectFruitTrees() { return false; }

    @ConfigItem(
            keyName = "protectHardTree",
            name = "Protect Hard trees",
            description = "Do you want to protect your hard wood ;)?",
            position = 2,
            section = protectionSection
    )
    default boolean protectHardTrees() { return false; }

    /* =========================
     * Gear
     * ========================= */
    @ConfigItem(
            keyName = "banking",
            name = "Banking",
            description = "Enabling this will run to bank and reset inventory with required items.",
            position = 0,
            section = gearSection
    )
    default boolean banking() { return true; }

    @ConfigItem(
            keyName = "useCompost",
            name = "Use compost",
            description = "Only bottomless compost bucket is supported",
            position = 1,
            section = gearSection
    )
    default boolean useCompost() { return true; }

    @ConfigItem(
            keyName = "useGraceful",
            name = "Use graceful",
            description = "Enable if you want to wear graceful outfit",
            position = 2,
            section = gearSection
    )
    default boolean useGraceful() { return true; }

    @ConfigItem(
            keyName = "useSkillsNecklace",
            name = "Use Skills Necklace",
            description = "Useful if you don't have Spirit tree or Farming cape",
            position = 3,
            section = gearSection
    )
    default boolean useSkillsNecklace() { return true; }

    @ConfigItem(
            keyName = "useEnergyPotion",
            name = "Use Energy Potion",
            description = "Useful if you want to have a faster run",
            position = 4,
            section = gearSection
    )
    default boolean useEnergyPotion() { return true; }

    /* =========================
     * Tree patches (regular) — ordered to match run:
     * GS Tree → Farming Guild Tree → Taverley → Falador → Lumbridge → Varrock → Auburnvale
     * ========================= */
    @ConfigItem(
            keyName = "gnomeStrongholdTree",
            name = "Gnome Stronghold",
            description = "Gnome Stronghold tree patch",
            position = 0,
            section = treePatchesSection
    )
    default boolean gnomeStrongholdTreePatch() { return true; }

    @ConfigItem(
            keyName = "farmingGuildTree",
            name = "Farming Guild",
            description = "FarmingGuild tree patch",
            position = 1,
            section = treePatchesSection
    )
    default boolean farmingGuildTreePatch() { return true; }

    @ConfigItem(
            keyName = "taverley",
            name = "Taverley",
            description = "Taverley tree patch",
            position = 2,
            section = treePatchesSection
    )
    default boolean taverleyTreePatch() { return true; }

    @ConfigItem(
            keyName = "falador",
            name = "Falador",
            description = "Falador tree patch",
            position = 3,
            section = treePatchesSection
    )
    default boolean faladorTreePatch() { return true; }

    @ConfigItem(
            keyName = "lumbridge",
            name = "Lumbridge",
            description = "Lumbridge tree patch",
            position = 4,
            section = treePatchesSection
    )
    default boolean lumbridgeTreePatch() { return true; }

    @ConfigItem(
            keyName = "varrock",
            name = "Varrock",
            description = "Varrock tree patch",
            position = 5,
            section = treePatchesSection
    )
    default boolean varrockTreePatch() { return true; }

    @ConfigItem(
            keyName = "AuburnvaleTree",
            name = "Auburnvale",
            description = "Auburnvale tree patch",
            position = 6,
            section = treePatchesSection
    )
    default boolean auburnTreePatch() { return true; }

    /* =========================
     * Fruit tree patches — ordered to match run:
     * GS Fruit → TGV Fruit → Farming Guild Fruit → Brimhaven → Catherby → Lletya → Kastori
     * ========================= */
    @ConfigItem(
            keyName = "gnomeStrongholdFruitTree",
            name = "Gnome Stronghold",
            description = "Gnome Stronghold fruit tree patch",
            position = 0,
            section = fruitTreePatchesSection
    )
    default boolean gnomeStrongholdFruitTreePatch() { return true; }

    @ConfigItem(
            keyName = "treeGnomeVillage",
            name = "Tree gnome village",
            description = "Tree gnome village fruit tree patch",
            position = 1,
            section = fruitTreePatchesSection
    )
    default boolean treeGnomeVillageFruitTreePatch() { return true; }

    @ConfigItem(
            keyName = "farmingGuildFruitTree",
            name = "Farming Guild",
            description = "Farming guild fruit tree patch",
            position = 2,
            section = fruitTreePatchesSection
    )
    default boolean farmingGuildFruitTreePatch() { return false; }

    @ConfigItem(
            keyName = "brimhaven",
            name = "Brimhaven",
            description = "Brimhaven fruit tree patch",
            position = 3,
            section = fruitTreePatchesSection
    )
    default boolean brimhavenFruitTreePatch() { return true; }

    @ConfigItem(
            keyName = "catherby",
            name = "Catherby",
            description = "Catherby fruit tree patch",
            position = 4,
            section = fruitTreePatchesSection
    )
    default boolean catherbyFruitTreePatch() { return true; }

    @ConfigItem(
            keyName = "lletya",
            name = "Lletya",
            description = "Lletya tree patch",
            position = 5,
            section = fruitTreePatchesSection
    )
    default boolean lletyaFruitTreePatch() { return false; }

    @ConfigItem(
            keyName = "kastoriFruitTreePatch",
            name = "Kastori",
            description = "Enable Kastori fruit tree patch",
            position = 6,
            section = fruitTreePatchesSection
    )
    default boolean kastoriFruitTreePatch() { return true; }

    /* =========================
     * Hardwood patches — run uses Fossil Island (x3) then Avium Savannah
     * ========================= */
    @ConfigItem(
            keyName = "fossil",
            name = "Fossil Island",
            description = "Fossil Island tree patch x3",
            position = 0,
            section = hardTreePatchesSection
    )
    default boolean fossilTreePatch() { return true; }

    @ConfigItem(
            keyName = "aviumSavannahHardwood",
            name = "Avium Savannah",
            description = "Enable this hardwood tree patch",
            position = 1,
            section = hardTreePatchesSection
    )
    default boolean aviumSavannahHardwoodPatch() { return false; }
}
