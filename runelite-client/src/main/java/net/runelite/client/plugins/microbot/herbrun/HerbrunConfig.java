package net.runelite.client.plugins.microbot.herbrun;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("LiftedMango's-Herbrun")
@ConfigInformation("IMPORTANT!<br/>Ensure your chosen seeds match the herb currently planted. For example, if growing snapdragon, make sure snapdragon is already in the patch; otherwise, the herbs won’t note.<br/><br/>If weeds grow right after picking the seeds, it may skip the patch without planting new seeds.<br/> <br/>Start next to a bank<br/><br /><p>Have the following in your bank:</p>\n" +
        "<ol>\n" +
        "    <li>Ardougne cloak</li>\n" +
        "    <li>Ultracompost or Bottomless compost bucket (filled)</li>\n" +
        "    <li>Magic Secateurs</li>\n" +
        "    <li>Seed dibber</li>\n" +
        "    <li>Spade</li>\n" +
        "    <li>Rake</li>\n" +
        "    <li>Ectophial, or if no ectophial, Fenkenstrain Teleport Tab</li>\n" +
        "    <li>Any Quetzal whistle (WITH CHARGES), or Civitas Teleport Tab</li>\n" +
        "    <li>Stony Basalt/Trollheim tab</li>\n" +
        "    <li>Icy Basalt</li>\n" +
        "    <li>Skills necklace</li>\n" +
        "    <li>Explorer's Ring</li>\n" +
        "    <li>Herb seeds</li>\n" +
        "    <li>Camelot teleport tab</li>\n" +
        "    <li>Xeric's talisman</li>\n" +
        "    <li>Harmony Teleport tab</li>\n" +
        "</ol>")

public interface HerbrunConfig extends Config {


    @ConfigItem(
            keyName = "fastHerb",
            name = "Fast Herb Picking?",
            description = "Enable quick herb picking",
            position = 4,
            section = settingsSection
    )
    default boolean FAST_HERB() {
        return false;
    }

    @ConfigItem(
            keyName = "bottomless",
            name = "Use bottomless bucket?",
            description = "Should  bottomless bucket be withdrawn from the bank?",
            position = 4,
            section = settingsSection
    )
    default boolean COMPOST() {
        return true;
    }

    @ConfigItem(
            keyName = "enableGearing",
            name = "Enable Gearing",
            description = "Enable Gearing? Helps with debugging a specific location...",
            position = 13,
            section = settingsSection
    )
    default boolean enableGearing() {
        return true;
    }

    @ConfigItem(
            keyName = "farmingCape",
            name = "Use Farming cape?",
            description = "Use Farming cape instead of Skills necklace?",
            position = 4,
            section = settingsSection
    )
    default boolean FARMING_CAPE() {
        return false;
    }

    @ConfigItem(
            keyName = "ardougne_teleport",
            name = "Use Ardougne cloak?",
            description = "Does your ardougne cloak still have charges for today? If not the script will use Ardy tab",
            position = 4,
            section = settingsSection
    )
    default boolean ARDOUGNE_TELEPORT_OPTION() {
        return true;
    }

    @ConfigItem(
            keyName = "falador_teleport",
            name = "Use Explorer's ring?",
            description = "Does your ring still have charges for today? If not the script will use falador tab",
            position = 4,
            section = settingsSection
    )
    default boolean FALADOR_TELEPORT_OPTION() {
        return true;
    }

    @ConfigItem(
            keyName = "graceful",
            name = "Equip graceful?",
            description = "Should graceful be equipped from bank?",
            position = 4,
            section = settingsSection
    )
    default boolean GRACEFUL() {
        return true;
    }


    @ConfigItem(
            keyName = "seedTypes",
            name = "Seeds to use",
            description = "Which seeds to use for the herb run?",
            position = 3,
            section = settingsSection
    )
    default HerbrunInfo.seedType SEED() {
        return HerbrunInfo.seedType.KWUARM_SEED;
    }

    @ConfigItem(
            keyName = "cloakType",
            name = "Cloak to use",
            description = "Which cloak to use for the herb run?",
            position = 3,
            section = settingsSection
    )
    default HerbrunInfo.cloak CLOAK() {
        return HerbrunInfo.cloak.ARDOUGNE_CLOAK_3;
    }

    @ConfigItem(
            keyName = "ringType",
            name = "Explorers ring to use",
            description = "Which explorers ring to use for the herb run?",
            position = 3,
            section = settingsSection
    )
    default HerbrunInfo.ring RING() {
        return HerbrunInfo.ring.EXPLORERS_RING_3;
    }

    @ConfigItem(
            keyName = "morytanita_teleport",
            name = "Use Ectophial?",
            description = "Do you have an ectophial? If not Fenkenstrain tab will be used.",
            position = 4,
            section = settingsSection
    )
    default boolean USE_ECTOPHIAL() {
        return true;
    }

    @ConfigItem(
            keyName = "varlamore_teleport",
            name = "Use Quetzal Whistle?",
            description = "Use Quetzal Whistle? If not Civitas tab will be used.",
            position = 4,
            section = settingsSection
    )
    default boolean USE_QUETZAL_WHISTLE() {
        return true;
    }

    @ConfigItem(
            keyName = "trollHeim teleport Type",
            name = "Trollheim teleport to use?",
            description = "Which trollheim teleport to use??",
            position = 3,
            section = settingsSection
    )
    default HerbrunInfo.trollheimTeleport TROLLHEIMTELEPORT() {
        return HerbrunInfo.trollheimTeleport.STONY_BASALT;
    }

    // Location toggles for each patch location
    @ConfigItem(
            keyName = "enableTrollheim",
            name = "Enable Trollheim Patch",
            description = "Enable Trollheim patch in herb run",
            position = 1,
            section = locationSection
    )
    default boolean enableTrollheim() {
        return true;
    }

    @ConfigItem(
            keyName = "enableCatherby",
            name = "Enable Catherby Patch",
            description = "Enable Catherby patch in herb run",
            position = 2,
            section = locationSection
    )
    default boolean enableCatherby() {
        return true;
    }

    @ConfigItem(
            keyName = "enableMorytania",
            name = "Enable Morytania Patch",
            description = "Enable Morytania patch in herb run",
            position = 3,
            section = locationSection
    )
    default boolean enableMorytania() {
        return true;
    }

    @ConfigItem(
            keyName = "enableVarlamore",
            name = "Enable Varlamore Patch",
            description = "Enable Varlamore patch in herb run",
            position = 4,
            section = locationSection
    )
    default boolean enableVarlamore() {
        return true;
    }

    @ConfigItem(
            keyName = "enableHosidius",
            name = "Enable Hosidius Patch",
            description = "Enable Hosidius patch in herb run",
            position = 5,
            section = locationSection
    )
    default boolean enableHosidius() {
        return true;
    }

    @ConfigItem(
            keyName = "enableArdouge",
            name = "Enable Ardouge Patch",
            description = "Enable Ardouge patch in herb run",
            position = 6,
            section = locationSection
    )
    default boolean enableArdougne() { return true; }

    @ConfigItem(
            keyName = "enableFalador",
            name = "Enable Falador Patch",
            description = "Enable Falador patch in herb run",
            position = 7,
            section = locationSection
    )
    default boolean enableFalador() {
        return true;
    }

    @ConfigItem(
            keyName = "enableWeiss",
            name = "Enable Weiss Patch",
            description = "Enable Weiss patch in herb run",
            position = 8,
            section = locationSection
    )
    default boolean enableWeiss() {
        return true;
    }

    @ConfigItem(
            keyName = "enableGuild",
            name = "Enable Farming Guild Patch",
            description = "Enable Farming Guild patch in herb run",
            position = 9,
            section = locationSection
    )
    default boolean enableGuild() {
        return true;
    }

    @ConfigItem(
            keyName = "enableHarmony",
            name = "Enable Harmony Island Patch",
            description = "Enable Harmony Island patch in herb run",
            position = 9,
            section = locationSection
    )
    default boolean enableHarmony() {
        return false;
    }


    @ConfigSection(
            name = "Settings",
            description = "Settings",
            position = 2
    )
    String settingsSection = "Settings";

    @ConfigSection(
            name = "Location toggles",
            description = "Location toggles",
            position = 3
    )
    String locationSection = "Location";
/*    @ConfigItem(
            keyName = "Ore",
            name = "Ore",
            description = "Choose the ore",
            position = 0
    )
    default List<String> ORE()
    {
        return Rocks.TIN;
    }*/

}
