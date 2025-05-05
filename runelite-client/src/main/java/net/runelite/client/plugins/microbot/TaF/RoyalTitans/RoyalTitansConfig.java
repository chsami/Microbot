package net.runelite.client.plugins.microbot.TaF.RoyalTitans;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;

@ConfigInformation(
        "<html>" +
                "<h2 style='color: #6d9eeb;'>Royal Titans Bot by TaF</h2>" +
                "<p>This script kills the Royal Titans with another bot or player.</p>" +
                "<h3 style='color: #93c47d;'>Requirements:</h3>" +
                "<ul>" +
                "<li><b>Combat Styles:</b> All 3 combat styles are required</li>" +
                "<li><b>Magic Gear:</b> Must provide +70 magic bonus (or +39 with void mage)</li>" +
                "<li><b>Ranged Gear:</b> Must use a weapon with range of 7+ (crossbows/bowfa)</li>" +
                "<li><b>Melee Gear:</b> Best available melee gear for optimal killing speeds</li>" +
                "<li><b>Spell Selection:</b> Preselect autocast spell for minions (i.e. Water spells for fire minions/walls)</li>" +
                "</ul>" +
                "<p style='color: #cc0000;'><i>Note: Both bots should be configured to focus on different titans for best efficiency.</i></p>" +
                "</html>")
@ConfigGroup("RoyalTitans")
public interface RoyalTitansConfig extends Config {
    @ConfigSection(
            name = "General Settings",
            description = "General settings",
            position = 0
    )
    String generalSection = "General Settings";
    @ConfigSection(
            name = "Combat",
            description = "Combat settings",
            position = 1
    )
    String combatSection = "Combat";
    @ConfigSection(
            name = "Equipment settings",
            description = "Settings for equipment",
            position = 2
    )
    String equipmentSettings = "EquipmentSettings";
    @ConfigSection(
            name = "Banking & Supply settings",
            description = "Settings for resupplying and banking",
            position = 3
    )
    String supplySettings = "SupplySettings";

    @ConfigSection(
            name = "Looting settings",
            description = "Settings for looting",
            position = 4
    )
    String lootingSettings = "LootingSettings";

    // General
    @ConfigItem(
            keyName = "teammateName",
            name = "Teammate name",
            description = "The name of your teammate. This is case sensitive.",
            position = 0,
            section = generalSection
    )
    default String teammateName() {
        return "";
    }

    @ConfigItem(
            keyName = "resupplyWithTeammate",
            name = "Leave with teammate",
            description = "If enabled, the bot leave if your teammate leaves. It will attempt to resupply and go back to royal titans. If disabled, the bot will continue to fight until it runs out of supplies.",
            section = generalSection,
            position = 1
    )
    default boolean resupplyWithTeammate() {
        return false;
    }

    @ConfigItem(
            keyName = "currentBotInstanceOwner",
            name = "Are you instance owner?",
            description = "If enabled, this bot instance will create the instance, otherwise, it will join the teammates instance",
            section = generalSection,
            position = 2
    )
    default boolean currentBotInstanceOwner() {
        return false;
    }

    @ConfigItem(
            keyName = "waitingTimeForTeammate",
            name = "The amount of time (in seconds) to wait for your teammate at the entrance",
            description = "The amount of time (in seconds) to wait for your teammate before shutting down the script.",
            section = generalSection,
            position = 3
    )
    default int waitingTimeForTeammate() {
        return 600;
    }

    @Range(
            min = 120,
            max = 900
    )
    /// Combat
    @ConfigItem(
            keyName = "useSpecialAttacks",
            name = "Use Special Attacks",
            description = "If enabled, the bot will use special attacks when available.",
            section = combatSection,
            position = 0
    )
    default boolean useSpecialAttacks() {
        return false;
    }

    @ConfigItem(
            keyName = "specialAttackWeapon",
            name = "Special attack gear",
            description = "The InventorySetup for the use of your special attack weapon.",
            section = combatSection,
            position = 1
    )
    default InventorySetup specialAttackWeapon() {
        return null;
    }

    @ConfigItem(
            keyName = "specialAttackWeaponStyle",
            name = "Special attack weapon style",
            description = "Is the special attack weapon a ranged or melee weapon?",
            section = combatSection,
            position = 2
    )
    default SpecialAttackWeaponStyle specialAttackWeaponStyle() {
        return SpecialAttackWeaponStyle.MELEE;
    }

    @ConfigItem(
            keyName = "specEnergyConsumed",
            name = "Spec Energy Consumed",
            description = "Spec energy used per special attack",
            position = 3,
            section = combatSection
    )
    default int specEnergyConsumed() {
        return 50;
    }

    @ConfigItem(
            keyName = "royalTitanToFocus",
            name = "Royal Titan to focus",
            description = "Select which Royal Titan the bot will focus on. The other bot should focus on the other one.",
            section = combatSection,
            position = 4
    )
    default RoyalTitan royalTitanToFocus() {
        return RoyalTitan.FIRE_TITAN;
    }

    @ConfigItem(
            keyName = "minionResponsibility",
            name = "Which minions are you responsible for?",
            description = "The bot is expecting you to only be responsible for the minions you select here. It assumes the other one handles the other ones.",
            section = combatSection,
            position = 5
    )
    default Minions minionResponsibility() {
        return Minions.FIRE_MINIONS;
    }

    @ConfigItem(
            keyName = "enableOffensivePrayer",
            name = "Enable Offensive Prayer",
            description = "Toggle to enable or disable offensive prayer during combat",
            section = combatSection,
            position = 6
    )
    default boolean enableOffensivePrayer() {
        return false;
    }

    @ConfigItem(
            keyName = "minEatPercent",
            name = "Minimum Health Percent",
            description = "Percentage of health below which the bot will eat food",
            section = combatSection,
            position = 7
    )
    default int minEatPercent() {
        return 50;
    }

    @ConfigItem(
            keyName = "minPrayerPercent",
            name = "Minimum Prayer Percent",
            description = "Percentage of prayer points below which the bot will drink a prayer potion",
            section = combatSection,
            position = 8
    )
    default int minPrayerPercent() {
        return 35;
    }

    @ConfigItem(
            keyName = "healthThreshold",
            name = "Health Threshold to Exit",
            description = "Minimum health percentage to stay and fight",
            section = combatSection,
            position = 9
    )
    default int healthThreshold() {
        return 50;
    }

    // Equipment
    @ConfigItem(
            keyName = "meleeEquipment",
            name = "Melee equipment",
            description = "The InventorySetup of the melee armor to equip.",
            section = equipmentSettings,
            position = 0
    )
    default InventorySetup meleeEquipment() {
        return null;
    }

    @ConfigItem(
            keyName = "rangedEquipment",
            name = "Range equipment",
            description = "The InventorySetup of the range armor to equip.",
            section = equipmentSettings,
            position = 1
    )
    default InventorySetup rangedEquipment() {
        return null;
    }

    @ConfigItem(
            keyName = "magicEquipment",
            name = "Magic equipment",
            description = "The InventorySetup of the magic armor to equip.",
            section = equipmentSettings,
            position = 2
    )
    default InventorySetup magicEquipment() {
        return null;
    }

    // Banking & Supply
    @ConfigItem(
            keyName = "inventorySetup",
            name = "Inventory setup",
            description = "Inventory setup & equipment config to use.",
            section = supplySettings,
            position = 0
    )
    default InventorySetup inventorySetup() {
        return null;
    }

    @ConfigItem(
            keyName = "emergencyTeleport",
            name = "Emergency teleport item ID",
            description = "The ID of the item to use to teleport out of the boss room. If set to 0 or no value, it will exit the room normally and walk to a bank",
            section = supplySettings,
            position = 1
    )
    default int emergencyTeleport() {
        return 0;
    }

    @ConfigItem(
            keyName = "boostedStatsThreshold",
            name = "% Boosted Stats Threshold",
            description = "The threshold for using a potion when the boosted stats are below the maximum.",
            section = supplySettings,
            position = 2
    )
    @Range(
            min = 1,
            max = 100
    )
    default int boostedStatsThreshold() {
        return 25;
    }

    // Looting
    @ConfigItem(
            keyName = "lootingTitan",
            name = "Titan to loot",
            description = "Select which titan to look & how to approach looting",
            section = lootingSettings,
            position = 0
    )
    default LootingTitan loot() {
        return LootingTitan.ALTERNATE;
    }

    // Enums
    enum SpecialAttackWeaponStyle {RANGED, MELEE}

    enum RoyalTitan {ICE_TITAN, FIRE_TITAN}

    enum Minions {ICE_MINIONS, FIRE_MINIONS}

    enum LootingTitan {ICE_TITAN, FIRE_TITAN, ALTERNATE, RANDOM}
}
