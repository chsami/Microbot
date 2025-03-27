package net.runelite.client.plugins.microbot.VoxPlugins.schedulable.woodcutting;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.woodcutting.enums.WoodcuttingResetOptions;
import net.runelite.client.plugins.microbot.woodcutting.enums.WoodcuttingTree;

@ConfigGroup("SchedulableWoodcutting")
public interface SchedulableWoodcuttingConfig extends Config {
    @ConfigSection(
            name = "General",
            description = "General settings",
            position = 0
    )
    String generalSection = "general";

    @ConfigItem(
            keyName = "tree",
            name = "Tree Type",
            description = "Select the type of tree to cut",
            position = 0,
            section = generalSection
    )
    default WoodcuttingTree tree() {
        return WoodcuttingTree.TREE;
    }

    @ConfigItem(
            keyName = "bankLogs",
            name = "Bank Logs",
            description = "Bank logs instead of dropping them",
            position = 1,
            section = generalSection
    )
    default boolean bankLogs() {
        return false;
    }

    @ConfigItem(
            keyName = "maxDistance",
            name = "Max Distance",
            description = "Maximum distance to search for trees from starting position",
            position = 2,
            section = generalSection
    )
    default int maxDistance() {
        return 20;
    }

    @ConfigSection(
            name = "Stop Conditions",
            description = "Conditions for when the plugin should stop",
            position = 1
    )
    String stopSection = "stopConditions";

    @ConfigItem(
            keyName = "minLogs",
            name = "Minimum Logs",
            description = "Minimum number of logs to collect before stopping",
            position = 0,
            section = stopSection
    )
    default int minLogs() {
        return 5;
    }

    @ConfigItem(
            keyName = "maxLogs",
            name = "Maximum Logs",
            description = "Maximum number of logs to collect before stopping",
            position = 1,
            section = stopSection
    )
    default int maxLogs() {
        return 10;
    }

    @ConfigItem(
            keyName = "minRuntime",
            name = "Minimum Runtime (minutes)",
            description = "Minimum time to run before stopping",
            position = 2,
            section = stopSection
    )
    default int minRuntime() {
        return 1;
    }

    @ConfigItem(
            keyName = "maxRuntime",
            name = "Maximum Runtime (minutes)",
            description = "Maximum time to run before stopping",
            position = 3,
            section = stopSection
    )
    default int maxRuntime() {
        return 2;
    }

    @ConfigItem(
            keyName = "lastLocation",
            name = "Last Location",
            description = "Last woodcutting location",
            hidden = true
    )
    default String lastLocation() {
        return "";
    }

    @ConfigItem(
            keyName = "lastLocation",
            name = "Last Location",
            description = "Last woodcutting location",
            hidden = true
    )
    void setLastLocation(String location);
}