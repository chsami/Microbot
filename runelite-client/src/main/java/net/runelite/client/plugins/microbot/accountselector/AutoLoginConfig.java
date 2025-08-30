package net.runelite.client.plugins.microbot.accountselector;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("AutoLoginConfig")
public interface AutoLoginConfig extends Config {
    @ConfigSection(
            name = "General",
            description = "General",
            position = 0,
            closedByDefault = false
    )
    String generalSection = "general";

    @ConfigItem(
            keyName = "World",
            name = "World",
            description = "World",
            position = 0,
            section = generalSection
    )
    default int world() { return 360; }

    @ConfigItem(
            keyName = "Is Member",
            name = "Is Member",
            description = "Use member worlds",
            position = 1,
            section = generalSection
    )
    default boolean isMember() { return false; }

    @ConfigItem(
            keyName = "RandomWorld",
            name = "Use Random World",
            description = "Use random worlds",
            position = 2,
            section = generalSection
    )
    default boolean useRandomWorld() { return true; }

    @ConfigSection(
            name = "Region Filter",
            description = "Filter random world selection by region",
            position = 10,
            closedByDefault = false
    )
    String regionSection = "region";


    @ConfigItem(
            keyName = "AllowUK",
            name = "UK",
            description = "Allow UK worlds",
            position = 1,
            section = regionSection
    )
    default boolean allowUK() { return true; }

    @ConfigItem(
            keyName = "AllowUS",
            name = "US",
            description = "Allow US worlds",
            position = 2,
            section = regionSection
    )
    default boolean allowUS() { return true; }

    @ConfigItem(
            keyName = "AllowGermany",
            name = "Germany",
            description = "Allow German worlds",
            position = 3,
            section = regionSection
    )
    default boolean allowGermany() { return true; }

    @ConfigItem(
            keyName = "AllowAustralia",
            name = "Australia",
            description = "Allow Australian worlds",
            position = 4,
            section = regionSection
    )
    default boolean allowAustralia() { return true; }
}
