package net.runelite.client.plugins.microbot.jad;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(JadConfig.configGroup)
@ConfigInformation("This plugin will pray switch jad attacks and attack healers. Supports up to 3 jads")
public interface JadConfig extends Config {
    String configGroup = "micro-jadhelper";
    String shouldAttackHealers = "shouldAttackHealers";

    @ConfigItem(
            keyName = shouldAttackHealers,
            name = "Attack Healers",
            description = "Enable this setting to handle jad healers",
            position = 0
    )
    default boolean shouldAttackHealers() {
        return true;
    }
}
