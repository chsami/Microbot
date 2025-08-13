package net.runelite.client.plugins.microbot.bga.autoessencemining;

import net.runelite.client.config.*;

@ConfigGroup("EssenceMining")
public interface EssenceMiningConfig extends Config {
    @ConfigItem(
            keyName = "Guide",
            name = "Usage guide",
            description = "Usage guide",
            position = 1
    )
    default String GUIDE() {
        return  "Begin anywhere with a pickaxe wielded or in your inventory...";
    }
}