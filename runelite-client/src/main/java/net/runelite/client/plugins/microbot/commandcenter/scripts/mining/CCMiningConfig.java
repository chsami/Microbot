package net.runelite.client.plugins.microbot.commandcenter.scripts.mining;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("ccmining")
public interface CCMiningConfig extends Config {
    @ConfigSection(name = "General", position = 0)
    String generalSection = "general";

    @ConfigItem(keyName = "rock", name = "Rock Type", description = "Which rock to mine",
        position = 0, section = generalSection)
    default MiningRock rock() { return MiningRock.COPPER; }

    @ConfigItem(keyName = "bankOre", name = "Bank Ore", description = "Bank ore instead of dropping",
        position = 1, section = generalSection)
    default boolean bankOre() { return true; }
}
