package net.runelite.client.plugins.microbot.hal.blessedwine;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;

@ConfigInformation("This plugin will handle prayer using wines.\n" +
        "Ensure you have enough blessed bone shards, \n" +
        "Ensure you have  1 wine per 400 shards, \n" +
        "Ensure you have 1 calcified moth per 10,400 shards, \n" +
        "You must have access to Cam Torum. \n" +
        "- Hal")
@ConfigGroup("blessedwine")
public interface BlessedWineConfig extends Config {
    // Config hooks go here
}