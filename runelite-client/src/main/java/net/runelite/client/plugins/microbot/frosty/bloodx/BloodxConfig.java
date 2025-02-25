package net.runelite.client.plugins.microbot.frosty.bloodx;

import net.runelite.client.config.*;

@ConfigGroup("Frosty")
public interface BloodxConfig extends Config {

    @ConfigItem(
            keyName = "useHat",
            name = "Use Hat of the Eye",
            description = "Whether to check for Hat of the Eye before starting."
    )
    default boolean useHatOfTheEye() {
        return true;
    }

    @ConfigItem(
            keyName = "autoBank",
            name = "Auto Bank",
            description = "Whether the bot should auto-bank when out of Pure Essence."
    )
    default boolean autoBank() {
        return true;
    }
}