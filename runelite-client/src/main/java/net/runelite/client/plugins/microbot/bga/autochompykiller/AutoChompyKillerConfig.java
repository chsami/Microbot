package net.runelite.client.plugins.microbot.bga.autochompykiller;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("autochompykiller")
public interface AutoChompyKillerConfig extends Config {
    @ConfigItem(
            keyName = "guide",
            name = "How to use",
            description = "How to use this plugin",
            position = 1
    )
    default String GUIDE() {
        return  "Auto Chompy Killer - start near some toads with ogre bow and arrows equipped. You might want to babysit this one.";
    }
}