package net.runelite.client.plugins.microbot.virewatch;


import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.virewatch.models.PRAY_STYLE;

@ConfigGroup("PVireKillerConfig")
public interface PVirewatchKillerConfig extends Config {

    @ConfigItem(
            keyName = "guide",
            name = "How to use",
            description = "How to use this plugin",
            position = 0
    )
    default String GUIDE() {
        return "Read the description of the settings for more information. Start with inventory and gear setup near the prayer statue and configure the settings. \n HAVE AUTO RETALIATE ON!" ;
    }

    @ConfigItem(
            keyName = "killRadius",
            name = "Tile Radius",
            description = "Radius to kill/roam in",
            position = 1,
            section = combatSection
    )
    default int radius() {
        return 10;
    }

    @ConfigItem(
            keyName = "prayStyle",
            name = "Prayer style",
            description = "Normal prayers are using the interface quick-prayers use the quick prayer orb to turn on prayer",
            position = 2,
            section = combatSection
    )
    default PRAY_STYLE prayStyle() {
        return PRAY_STYLE.NORMAL;
    }


    @ConfigItem(
            keyName = "piety",
            name = "Use piety",
            description = "Use the piety prayer",
            position = 3,
            section = combatSection
    )
    default boolean piety() {
        return false;
    }

    @ConfigItem(
            keyName = "Hitpoints",
            name = "Eat at %",
            description = "Use food below certain hitpoint percent. If there's no food in the inventory, the script stops. Set to 0 in order to disable.",
            position = 4,
            section = combatSection
    )
    default int hitpoints()
    {
        return 20;
    }

    @ConfigItem(
            keyName = "prayAt",
            name = "Recharge prayer when points below",
            description = "At what interval to recharge ur prayers at the altar",
            position = 5,
            section = combatSection
    )
    default int prayAt() {
        return 15;
    }


    @ConfigSection(
            name = "Combat",
            description = "Combat",
            position = 1,
            closedByDefault = false
    )
    String combatSection = "Combat";

    @ConfigSection(
            name = "Loot",
            description = "Loot",
            position = 2,
            closedByDefault = false
    )
    String lootSection = "Loot";

    @ConfigSection(
            name = "Money",
            description = "Money",
            position = 3,
            closedByDefault = false
    )
    String moneySection = "Money";

    @ConfigSection(
            name = "Ticks",
            description = "Ticks",
            position = 4,
            closedByDefault = false
    )
    String tickSection = "Ticks";

    @ConfigSection(
            name = "Performance",
            description = "Performance",
            position = 5,
            closedByDefault = true
    )
    String performanceSection = "Performance";

    @ConfigItem(
            keyName = "Loot items",
            name = "Auto loot items",
            description = "Enable/disable loot items",
            position = 0,
            section = lootSection
    )
    default boolean toggleLootItems() {
        return true;
    }

    @ConfigItem(
            keyName = "teleGrabLoot",
            name = "Telekinetic Grab Loot",
            description = "Use Telekinetic Grab to loot items instead of picking them up directly",
            position = 1,
            section = lootSection
    )
    default boolean teleGrabLoot() {
        return false;
    }

    @ConfigItem(
            keyName = "customLootItems",
            name = "Custom loot items",
            description = "Comma-separated list of item names to loot (e.g. Runite bar,Blood shard,Dragonstone)",
            position = 2,
            section = lootSection
    )
    default String customLootItems() {
        return "Blood shard,Runite bar,Dragon med helm,Dragonstone,Rune dagger,Adamant platelegs,Adamant platebody,Rune full helm,Rune kiteshield,Rune dagger,Blood rune,Runite ore";
    }

    @ConfigItem(
            keyName = "alchItems",
            name = "High Alch items",
            description = "High alch items from their drop table not the rare drop table!",
            position = 1,
            section = moneySection
    )
    default boolean alchItems() {
        return false;
    }

    @ConfigItem(
            keyName = "outOfAreaTicks",
            name = "Return after x ticks out of area",
            description = "Return back to the starting location if x ticks out of the area",
            position = 1,
            section = tickSection
    )
    default int tickToReturn() {
        return 25;
    }

    @ConfigItem(
            keyName = "outOfAreaCombatTicks",
            name = "Return after x out of combat ticks",
            description = "Return back to the starting location if x ticks out of combat",
            position = 2,
            section = tickSection
    )
    default int tickToReturnCombat() {
        return 25;
    }

    @ConfigItem(
            keyName = "disableAreaRender",
            name = "Disable fight area render",
            description = "Disables the fight are tile drawing",
            position = 1,
            section = performanceSection
    )
    default boolean disableFightArea() {
        return false;
    }

    @ConfigItem(
            keyName = "disableStatueOutline",
            name = "Disable statue outline",
            description = "Disables the statue drawing outline!",
            position = 2,
            section = performanceSection
    )
    default boolean disableStatueOutline() {
        return false;
    }

    @ConfigItem(
            keyName = "disableNpcOutline",
            name = "Disable NPC outline",
            description = "Disables the NPC drawing outline!",
            position = 3,
            section = performanceSection
    )
    default boolean disableNPCOutline() {
        return false;
    }

    @ConfigSection(
            name = "Equipment",
            description = "Gear to equip before starting",
            position = 6,
            closedByDefault = false
    )
    String equipmentSection = "Equipment";
    @ConfigItem(
            keyName = "top",
            name = "Top",
            description = "Gear worn in the body slot (e.g. Inquisitor's hauberk)",
            position = 0,
            section = equipmentSection
    )
    default String top() {
        return "Inquisitor's hauberk";
    }

    @ConfigItem(
            keyName = "legs",
            name = "Legs",
            description = "Gear worn in the leg slot (e.g. Inquisitor's plateskirt)",
            position = 1,
            section = equipmentSection
    )
    default String legs() {
        return "Inquisitor's plateskirt";
    }

    @ConfigItem(
            keyName = "boots",
            name = "Boots",
            description = "Gear worn in the boots slot (e.g. Primordial boots)",
            position = 2,
            section = equipmentSection
    )
    default String boots() {
        return "Primordial boots";
    }
}
