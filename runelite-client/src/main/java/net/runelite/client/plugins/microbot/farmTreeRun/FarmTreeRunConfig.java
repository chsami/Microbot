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
        "<br> If you want to stop the script during your farm run (maybe it gets stuck or whatever reason), make sure to disable 'Banking' and disable patches you previously ran. <br> Happy botting\n"
)
public interface FarmTreeRunConfig extends Config {

    public static final boolean DEBUG_MODE = System.getProperty("java.vm.info", "").contains("sharing");

    @ConfigSection(
            name = "General",
            description = "General",
            position = 1
    )
    String generalSection = "general";

    // Tree patches section
    @ConfigSection(
            name = "Tree patches",
            description = "Select which tree patches to use",
            position = 2
    )
    String treePatchesSection = "treePatchesSection";

    // Fruit tree patches section
    @ConfigSection(
            name = "Fruit tree patches",
            description = "Select which fruit tree patches to use",
            position = 3
    )
    String fruitTreePatchesSection = "fruitTreePatchesSection";

//    TODO: Not implemented yet
//    @ConfigItem(
//            keyName = "trackRuneLite",
//            name = "Use RuneLite time tracking plugin",
//            description = "When enabled it tracks RuneLite farm patch times. Only when select farm patches below are fully grown, it will start the farm run.",
//            position = 1,
//            section = generalSection
//    )
//    default boolean trackRuneLiteTimeTracking()
//    {
//        return false;
//    }

    @ConfigItem(
            keyName = "banking",
            name = "Banking",
            description = "Enabling this will run to bank and reset inventory with required items.",
            position = 1,
            section = generalSection
    )
    default boolean banking() {
        return true;
    }

    @ConfigItem(
            keyName = "treeSapling",
            name = "Tree sapling",
            description = "Select tree sapling to use",
            position = 2,
            section = generalSection
    )
    default TreeEnums selectedTree() {
        return TreeEnums.MAPLE;
    }

    @ConfigItem(
            keyName = "protectTree",
            name = "Protect trees",
            description = "Do you want to protect your trees?",
            position = 3,
            section = generalSection
    )
    default boolean protectTrees() {
        return true;
    }

    @ConfigItem(
            keyName = "fruitTreeSapling",
            name = "Fruit tree sapling",
            description = "Select fruit tree sapling to use",
            position = 4,
            section = generalSection
    )
    default FruitTreeEnum selectedFruitTree() {
        return FruitTreeEnum.PAPAYA;
    }

    @ConfigItem(
            keyName = "protectFruitTree",
            name = "Protect fruit trees",
            description = "Do you want to protect your fruit trees?",
            position = 5,
            section = generalSection
    )
    default boolean protectFruitTrees() {
        return false;
    }

    @ConfigItem(
            keyName = "Fossil Island Tree",
            name = "Hard sapling",
            description = "Select Hard tree sapling to use",
            position = 6,
            section = generalSection
    )
    default HardTreeEnums selectedHardTree() {
        return HardTreeEnums.MAHOGANY;
    }

    @ConfigItem(
            keyName = "protectHardTree",
            name = "Protect Hard trees",
            description = "Do you want to protect your hard wood ;) ?",
            position = 7,
            section = generalSection
    )
    default boolean protectHardTrees() {
        return false;
    }

    @ConfigItem(
            keyName = "useCompost",
            name = "Use compost",
            description = "Only bottomless compost bucket is supported",
            position = 8,
            section = generalSection
    )
    default boolean useCompost() {
        return true;
    }

    @ConfigItem(
            keyName = "useGraceful",
            name = "Use graceful",
            description = "Enable if you want to wear graceful outfit",
            position = 9,
            section = generalSection
    )
    default boolean useGraceful() {
        return true;
    }

    @ConfigItem(
            keyName = "useSkillsNecklace",
            name = "Use Skills Necklace",
            description = "Useful if you don't have Spirit tree or Farming cape",
            position = 10,
            section = generalSection
    )
    default boolean useSkillsNecklace() {
        return true;
    }

    @ConfigItem(
            keyName = "useEnergyPotion",
            name = "Use Energy Potion",
            description = "Useful if you want to have a faster run",
            position = 11,
            section = generalSection
    )
    default boolean useEnergyPotion() {
        return true;
    }

    @ConfigItem(
            keyName = "useSpiritMode",
            name = "Spirit Mode BETA (Advanced users)",
            description = "Use Spirit tree preferred routing, requires you to modify spirit_trees.tsv",
            position = 12,
            section = generalSection
    )
    default boolean useSpiritMode() {
        return true;
    }

    @ConfigItem(
            keyName = "falador",
            name = "Falador",
            description = "Falador tree patch",
            position = 0,
            section = treePatchesSection
    )
    default boolean faladorTreePatch() {
        return true;
    }

    @ConfigItem(
            keyName = "gnomeStrongholdTree",
            name = "Gnome Stronghold",
            description = "Gnome Stronghold tree patch",
            position = 1,
            section = treePatchesSection
    )
    default boolean gnomeStrongholdTreePatch() {
        return true;
    }

    @ConfigItem(
            keyName = "lumbridge",
            name = "Lumbridge",
            description = "Lumbridge tree patch",
            position = 2,
            section = treePatchesSection
    )
    default boolean lumbridgeTreePatch() {
        return true;
    }

    @ConfigItem(
            keyName = "taverley",
            name = "Taverley",
            description = "Taverley tree patch",
            position = 3,
            section = treePatchesSection
    )
    default boolean taverleyTreePatch() {
        return true;
    }

    @ConfigItem(
            keyName = "varrock",
            name = "Varrock",
            description = "Varrock tree patch",
            position = 4,
            section = treePatchesSection
    )
    default boolean varrockTreePatch() {
        return true;
    }

    @ConfigItem(
            keyName = "fossil",
            name = "Fossil Island",
            description = "Fossil Island tree patch x3",
            position = 4,
            section = treePatchesSection
    )
    default boolean fossilTreePatch() {
        return true;
    }

    @ConfigItem(
            keyName = "farmingGuildTree",
            name = "Farming Guild",
            description = "FarmingGuild tree patch",
            position = 5,
            section = treePatchesSection
    )
    default boolean farmingGuildTreePatch() {
        return true;
    }


    @ConfigItem(
            keyName = "brimhaven",
            name = "Brimhaven",
            description = "Brimhaven fruit tree patch",
            position = 1,
            section = fruitTreePatchesSection
    )
    default boolean brimhavenFruitTreePatch() {
        return true;
    }

    @ConfigItem(
            keyName = "catherby",
            name = "Catherby",
            description = "Catherby fruit tree patch",
            position = 2,
            section = fruitTreePatchesSection
    )
    default boolean catherbyFruitTreePatch() {
        return true;
    }

    @ConfigItem(
            keyName = "gnomeStrongholdFruitTree",
            name = "Gnome Stronghold",
            description = "Gnome Stronghold fruit tree patch",
            position = 3,
            section = fruitTreePatchesSection
    )
    default boolean gnomeStrongholdFruitTreePatch() {
        return true;
    }

    @ConfigItem(
            keyName = "treeGnomeVillage",
            name = "Tree gnome village",
            description = "Tree gnome village tree patch",
            position = 4,
            section = fruitTreePatchesSection
    )
    default boolean treeGnomeVillageFruitTreePatch() {
        return true;
    }

    @ConfigItem(
            keyName = "lletya",
            name = "Lletya",
            description = "Lletya tree patch",
            position = 5,
            section = fruitTreePatchesSection
    )
    default boolean lletyaFruitTreePatch() {
        return false;
    }

    @ConfigItem(
            keyName = "farmingGuildFruitTree",
            name = "Farming Guild",
            description = "Farming guild fruit tree patch",
            position = 6,
            section = fruitTreePatchesSection
    )
    default boolean farmingGuildFruitTreePatch() {
        return false;
    }
}
