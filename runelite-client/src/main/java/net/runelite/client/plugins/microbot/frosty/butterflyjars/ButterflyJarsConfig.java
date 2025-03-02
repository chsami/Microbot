package net.runelite.client.plugins.microbot.frosty.butterflyjars;

import net.runelite.client.config.*;

@ConfigGroup("butterflyjars")
public interface ButterflyJarsConfig extends Config {

    @ConfigItem(
            keyName = "minimumStock",
            name = "Minimum Stock Before Hop",
            description = "Set the minimum stock level before hopping worlds",
            position = 1
    )
    default int minimumStock() {
        return 28;
    }
}
