package net.runelite.client.plugins.microbot.frosty.wildthingsarehere;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("wildthingsarehere")
public interface WildConfig extends Config {
    @ConfigItem(
            keyName = "eatThreshold",
            name = "Eat at HP",
            description = "The HP threshold at which to eat food",
            position = 1
    )
    default int eatThreshold() {
        return 30;
    }
}
