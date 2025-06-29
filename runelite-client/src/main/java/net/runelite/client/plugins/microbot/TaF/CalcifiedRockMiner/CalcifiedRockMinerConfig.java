package net.runelite.client.plugins.microbot.TaF.CalcifiedRockMiner;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;

@ConfigInformation(
        "CalcifiedRockMiner")
@ConfigGroup("CalcifiedRockMiner")
public interface CalcifiedRockMinerConfig extends Config {
    @ConfigItem(
            keyName = "dropDeposits",
            name = "Drop deposits & gems",
            description = "When enabled, the deposits and any uncut gems will be dropped",
            position = 1
    )
    default boolean dropDeposits() {
        return false;
    }

    @ConfigItem(
            keyName = "crushDeposits",
            name = "Crush deposits",
            description = "When enabled, the deposits will be crushed before banking",
            position = 2
    )
    default boolean crushDeposits() {
        return false;
    }


    @ConfigItem(
            keyName = "focusCrackedWaterDeposits",
            name = "Focus on cracked water deposits",
            description = "Go out of the way to focus on mining cracked water deposits",
            position = 3
    )
    default boolean focusCrackedWaterDeposits() {
        return false;
    }

    @ConfigItem(
            keyName = "maxPlayersInArea",
            name = "Max players in area",
            description = "If more players than this are nearby, hop worlds. 0 = disable",
            position = 4
    )
    default int maxPlayersInArea() {
        return 0;
    }
}
