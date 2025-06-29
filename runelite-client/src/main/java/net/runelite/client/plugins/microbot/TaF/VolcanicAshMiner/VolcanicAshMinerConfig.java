package net.runelite.client.plugins.microbot.TaF.VolcanicAshMiner;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;

@ConfigInformation(
        "VolcanicAshMiner")
@ConfigGroup("VolcanicAshMiner")
public interface VolcanicAshMinerConfig extends Config {
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
