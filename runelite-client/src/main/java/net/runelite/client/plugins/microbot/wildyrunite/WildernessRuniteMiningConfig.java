package net.runelite.client.plugins.microbot.wildyrunite;

import net.runelite.client.config.*;

@ConfigGroup("WildernessRuniteMining")
public interface WildernessRuniteMiningConfig extends Config {
    @ConfigItem(
            keyName = "oreLimit",
            name = "Ore limit before banking",
            description = "Number of Runite ores required before banking. Set 0 to use default behavior.",
            position = 1
    )
    default int oreLimit() {
        return 0; // 0 = use prayer-based fallback
    }

    @ConfigItem(
            keyName = "stopAfterOneRun",
            name = "Stop After One Run",
            description = "Stops the script after one full inventory is mined and banked"
    )
    default boolean stopAfterOneRun() {
        return false;
    }

}
