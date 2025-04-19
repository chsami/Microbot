package net.runelite.client.plugins.microbot.bee.MossKiller;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.bee.MossKiller.Enums.AttackStyle;
import net.runelite.client.plugins.microbot.bee.MossKiller.Enums.CombatMode;
import net.runelite.client.plugins.microbot.bee.MossKiller.Enums.GearEnums;

@ConfigGroup(MossKillerConfig.configGroup)
public interface MossKillerConfig extends Config {

    String configGroup = "moss-killer";
    String hideOverlay = "hideOverlay";

    @ConfigSection(
            name = "Basic Guide",
            description = "Basic guide settings for MossKiller",
            position = 0,
            closedByDefault = false
    )
    String basicGuideSection = "basicGuideSection";

    @ConfigSection(
            name = "Advanced Guide",
            description = "Detailed guide for advanced users",
            position = 2,
            closedByDefault = true
    )
    String advancedGuideSection = "advancedGuideSection";

    @ConfigSection(
            name = "Wildy Safing",
            description = "Options specific to the safespot mode in wildy",
            position = 3,
            closedByDefault = true
    )
    String saferSection = "saferSection";

    @ConfigItem(
            keyName = "equipmentGuide",
            name = "Equipment Details",
            description = "List of required equipment.",
            position = 2,
            section = advancedGuideSection
    )
    default String equipmentGuide() {
        return "1x Rune Chainbody\n"
                + "1x Rune Scimitar\n"
                + "(optional) 1x Bryophyta Staff (Uncharged)\n"
                + "--\n"
                + "EVERYTHING BELOW THIS POINT IS MINIMUM RECOMMENDED AMOUNTS AS THE SCRIPT WILL STOP WHEN YOU RUN OUT OF SUPPLIES\n"
                + "--\n"
                + "10x Maple shortbow\n"
                + "10x Maple longbow\n"
                + "10x Studded Chaps\n"
                + "10x Blue wizard hat\n"
                + "10x Amulet of power\n"
                + "10x Leather boots\n"
                + "10x Leather vambraces\n"
                + "10x Staff of Fire\n"
                + "50x Strength potion (4)\n"
                + "100x Energy potion (4)\n"
                + "200x Law rune\n"
                + "500x Death rune\n"
                + "1000x Adamant Arrows\n"
                + "2000x Swordfish\n"
                + "10000x Mind rune\n"
                + "20000x Air rune";
    }

    @ConfigItem(
            keyName = "instructionsGuide",
            name = "Instructions",
            description = "Advanced usage instructions for MossKiller.",
            position = 1,
            section = advancedGuideSection
    )
    default String instructionsGuide() {
        return  "Select Wildy Mode.\n"
                + "For the first run start the plugin near a bank in f2p with no armor or weapons and every listed piece of equipment in the bank.\n"
                + "Turn on Breakhandler. Have fixed mode enabled.\n"
                + "Minimum required skill levels:\n"
                + "- 30 Range\n"
                + "- 41 Mage\n"
                + "- 40 Attack\n"
                + "- 40 Defense\n"
                + "Ideal skill levels:\n"
                + "- 70 Strength\n"
                + "- 55 Magic\n"
                + "- 43 Prayer\n"
                + "---------------------------------\n"
                + "This plugin will train your defense to 50 automatically";
    }

    @ConfigItem(
            keyName = "Filler",
            name = "",
            description = "filler text to differentiate the config",
            position = 3,
            section = saferSection
    )
    default String fillerTest() {
        return  "         ↓↓↓ RANGED SETTINGS ↓↓↓";
    }

    @ConfigItem(
            keyName = "instructionsForSafer",
            name = "Instructions",
            description = "Safer Mode instructions for MossKiller.",
            position = 1,
            section = saferSection
    )
    default String instructionsGuideSafer() {
        return  "Select Wildy Safer Mode.\n"
                + "Have fixed mode enabled.\n" +
                "Turn on LiteMode in Player Monitor.\n" +
                "Have AutoLogin Plugin enabled.\n" +
                "Select your cape of choice in the drop down menu. Must have:\n" +
                "2x Leather Boots \n" +
                "2x Leather vambraces \n" +
                "2x cape\n" +
                "\n"
                + "Minimum required skill levels for magic is level 13 Magic.\n"
                + "Min Equipment for Magic mode:\n"
                + "- 2x amulet of magic \n"
                + "- 2x staff of fire \n"
                + "Required skill levels for Ranged:\n"
                + "- 30 Ranged\n"
                + "Min Equipment for Ranged:\n"
                + "- 2x maple Shortbow \n"
                + "- Mithril Arrows (Edit Amount Below) \n"
                + "----------------------\n"
                + "If using ranged the rest of Ranged Equipment you must manually choose from dropdown menu (and have 2x of each).";
    }


    @ConfigItem(
            keyName = "guide",
            name = "How to Use",
            description = "Basic usage instructions for MossKiller",
            position = 1,
            section = basicGuideSection
    )
    default String GUIDE() {
        return "NORMAL: Have runes for teleport to Varrock, swordfish, and bronze axe in the bank. Start in Varrock East Bank. Turn off Teleportation spells in Web Walker configuration. Turn on Breakhandler.\n"
        + "ADVANCED: See Advanced Guide.\n"
        + "TIPS: For tips with the plugin visit the Microbot Discord -> Community Plugins -> Moss Killer Plugin";
    }

    @ConfigItem(
            keyName = "wildySelector",
            name = "Wildy Mode",
            description = "Enable this for killing Moss Giants in the Wilderness.",
            position = 0,
            section = advancedGuideSection
    )
    default boolean wildy() {
        return false;
    }

    @ConfigItem(
            keyName = "wildySaferSelector",
            name = "Wildy Safer Mode",
            description = "Enable this for killing Moss Giants in the Wilderness By Safing.",
            position = 0,
            section = saferSection
    )
    default boolean wildySafer() {
        return false;
    }


    @ConfigItem(
            keyName = "attackStyleSelector",
            name = "Attack style",
            description = "Select which attack style to use for wildy safespot",
            position = 1,
            section = saferSection
    )
    default AttackStyle attackStyle() {
        return AttackStyle.MAGIC;
    }

    @ConfigItem(
            keyName = "mithrilArrowAmount",
            name = "Mithril Arrow Amount",
            description = "Number of mithril arrows to use per trip if using Ranged",
            position = 4,
            section = saferSection
    )
    @Range(min = 1, max = 5000)  // This adds validation
    default int mithrilArrowAmount() {
        return 500;
    }

    @ConfigItem(
            keyName = "rangedAmulet",
            name = "Amulet",
            description = "Select which amulet to use for ranging",
            position = 5,
            section = saferSection
    )
    default GearEnums.RangedAmulet rangedAmulet() {
        return GearEnums.RangedAmulet.ACCURACY;
    }

    @ConfigItem(
            keyName = "rangedTorso",
            name = "Torso",
            description = "Select which body armor to use for ranging",
            position = 6,
            section = saferSection
    )
    default GearEnums.RangedTorso rangedTorso() {
        return GearEnums.RangedTorso.LEATHER;
    }

    @ConfigItem(
            keyName = "rangedChaps",
            name = "Chaps",
            description = "Select which leg armor to use for ranging",
            position = 6,
            section = saferSection
    )
    default GearEnums.RangedChaps rangedChaps() {
        return GearEnums.RangedChaps.LEATHER;
    }

    @ConfigItem(
            keyName = "cape",
            name = "Cape",
            description = "Select which cape to use (for both magic and ranging) - you must have a cape",
            position = 2,
            section = saferSection
    )
    default GearEnums.Cape cape() {
        return GearEnums.Cape.ORANGE_CAPE;
    }

    @ConfigItem(
            keyName = "combatMode",
            name = "Combat Mode",
            description = "Select the combat mode: Flee, Fight, or Lure (Currently only Fight Supported)",
            position = 3,
            section = advancedGuideSection
    )
    default CombatMode combatMode() {
        return CombatMode.FIGHT; // Default option
    }


    @ConfigItem(
            keyName = "hideOverlay",
            name = "Overlay Hider",
            description = "Select this if you want to hide the overlay",
            position = 10
    )
    default boolean isHideOverlay() {
        return false;
    }


    @ConfigItem(
            keyName = "buryBones",
            name = "Bury Bones",
            description = "Select this if you want to bury bones",
            position = 8
    )
    default boolean buryBones() {
        return false;
    }

    @ConfigItem(
            keyName = "alchLoot",
            name = "Alch loot",
            description = "Select this if you want to loot alchables and alch them and loot coins",
            position = 9
    )
    default boolean alchLoot() {
        return false;
    }

    @ConfigItem(
            keyName = "forceDefensive",
            name = "Force Defensive",
            description = "Select this if you want to autocast defensive after 50 Defence.",
            position = 5
    )
    default boolean forceDefensive() {
        return false;
    }

    @ConfigItem(
            keyName = "keyThreshold",
            name = "Key Threshold",
            description = "How many Mossy Keys should be collected before killing the boss.",
            position = 7,
            section = basicGuideSection
    )
    default int keyThreshold() {
        return 30;
    }

    @ConfigItem(
            keyName = "defenceLevel",
            name = "Stop Defence When",
            description = "Stops the script when this defence level is reached.",
            position = 3,
            section = basicGuideSection
    )
    @Range(min = 1, max = 125)
    default int defenseLevel() {
        return 125;
    }

    @ConfigItem(
            keyName = "attackLevel",
            name = "Stop Attack When",
            description = "Stops the script when this attack level is reached.",
            position = 4,
            section = basicGuideSection
    )
    @Range(min = 1, max = 125)
    default int attackLevel() {
        return 125;
    }

    @ConfigItem(
            keyName = "strengthLevel",
            name = "Stop Strength When",
            description = "Stops the script when this strength level is reached.",
            position = 5,
            section = basicGuideSection
    )
    @Range(min = 1, max = 125)
    default int strengthLevel() {
        return 125;
    }

    @ConfigItem(
            keyName = "hopWhenPlayerIsNear",
            name = "Hop Worlds When Players Near",
            description = "Hop worlds when players are near you fighting Moss Giants.",
            position = 6,
            section = basicGuideSection
    )
    default boolean hopWhenPlayerIsNear() {
        return true;
    }

    @ConfigItem(
            keyName = "isSlashWeaponEquipped",
            name = "Slash Weapon Equipped?",
            description = "Do you have a slash weapon equipped for spider's web? If False, have a knife in the bank.",
            position = 6,
            section = basicGuideSection
    )
    default boolean isSlashWeaponEquipped() {
        return true;
    }

}