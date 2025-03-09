package net.runelite.client.plugins.microbot.bee.MossKiller;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.bee.MossKiller.Enums.CombatMode;

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

    @ConfigItem(
            keyName = "equipmentGuide",
            name = "Equipment Details",
            description = "List of required equipment.",
            position = 1,
            section = advancedGuideSection // Links this to the Advanced Guide section
    )
    default String equipmentGuide() {
        return "1x Rune Chainbody\n"
                + "1x Rune Scimitar\n"
                + "(optional) 1x Bryophyta Staff (Uncharged)\n"
                + "--\n"
                + "EVERYTHING BELOW THIS POINT IS MINIMUM RECOMMENDED AMOUNTS, THE SCRIPT WILL STOP WHEN YOU RUN OUT OF SUPPLIES\n"
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
            position = 0,
            section = advancedGuideSection // Links this to the Advanced Guide section
    )
    default String instructionsGuide() {
        return  "Select Wildy Mode.\n"
                + "For the first run start the plugin near a bank in f2p with no armor or weapons and every listed piece of equipment in the bank.\n"
                + "Turn on Teleportation spells in Web Walker configuration. Turn on Breakhandler. Turn on PK skull prevention in OSRS settings and have fixed mode enabled.\n"
                + "Minimum required skill levels:\n"
                + "- 40 Range\n"
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
            keyName = "guide",
            name = "How to Use",
            description = "Basic usage instructions for MossKiller",
            position = 1,
            section = basicGuideSection // Belongs to Basic Guide
    )
    default String GUIDE() {
        return "NORMAL: Have runes for teleport to Varrock, swordfish, and bronze axe in the bank. Start in Varrock East Bank. Turn off Teleportation spells in Web Walker configuration. Turn on Breakhandler.\n"
        + "ADVANCED: See Advanced Guide.\n"
        + "TIPS: For tips with the plugin visit the Discord -> Community Plugins -> Moss Killer Plugin";
    }

    @ConfigItem(
            keyName = "wildySelector",
            name = "Wildy Mode",
            description = "Enable this for killing Moss Giants in the Wilderness.",
            position = 10
    )
    default boolean wildy() {
        return false;
    }

    @ConfigItem(
            keyName = "combatMode",
            name = "Combat Mode",
            description = "Select the combat mode: Flee, Fight, or Lure (Currently only Fight)",
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
            position = 7
    )
    default boolean isHideOverlay() {
        return false;
    }


    @ConfigItem(
            keyName = "buryBones",
            name = "Bury Bones",
            description = "Select this if you want to bury bones",
            position = 9
    )
    default boolean buryBones() {
        return false;
    }

    @ConfigItem(
            keyName = "alchLoot",
            name = "Alch loot",
            description = "Select this if you want to loot alchables and alch them and loot coins",
            position = 8
    )
    default boolean alchLoot() {
        return false;
    }

    @ConfigItem(
            keyName = "forceDefensive",
            name = "Force Defensive",
            description = "Select this if you want to autocast defensive after 50 Defence.",
            position = 5,
            section = advancedGuideSection
    )
    default boolean forceDefensive() {
        return false;
    }

    // -------------------------------------------
// Configuration Items (Under Basic Guide)
// -------------------------------------------
    @ConfigItem(
            keyName = "keyThreshold",
            name = "Key Threshold",
            description = "How many Mossy Keys should be collected before killing the boss.",
            position = 8,
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
            section = basicGuideSection // Belongs to Basic Guide
    )
    default boolean hopWhenPlayerIsNear() {
        return true;
    }

    @ConfigItem(
            keyName = "isSlashWeaponEquipped",
            name = "Slash Weapon Equipped?",
            description = "Do you have a slash weapon equipped for spider's web? If False, have a knife in the bank.",
            position = 7,
            section = basicGuideSection // Belongs to Basic Guide
    )
    default boolean isSlashWeaponEquipped() {
        return true;
    }

}