package net.runelite.client.plugins.microbot.autoworldhopper;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.plugins.microbot.autoworldhopper.enums.WorldMembershipFilter;

@ConfigGroup(AutoWorldHopperConfig.GROUP)
public interface AutoWorldHopperConfig extends Config {
    String GROUP = "autoworldhopper";

    @ConfigSection(
            name = "World Settings",
            description = "Configure world selection preferences",
            position = 0,
            closedByDefault = false
    )
    String worldSection = "worldSection";

    @ConfigSection(
            name = "Hop Triggers",
            description = "Configure when to hop worlds",
            position = 1,
            closedByDefault = false
    )
    String triggersSection = "triggersSection";

    @ConfigSection(
            name = "Advanced",
            description = "Advanced settings",
            position = 2,
            closedByDefault = true
    )
    String advancedSection = "advancedSection";

    // =========================================
    // ========== WORLD SETTINGS ==============
    // =========================================

    @ConfigItem(
            keyName = "enabled",
            name = "Enable Auto World Hopper",
            description = "Enable or disable the auto world hopper",
            position = 0,
            section = worldSection
    )
    default boolean enabled() {
        return false;
    }

    @Range(min = 0, max = 300)
    @ConfigItem(
            keyName = "startupDelay",
            name = "Startup Delay (seconds)",
            description = "Delay in seconds before triggers become active (allows time for walking/setup)",
            position = 1,
            section = worldSection
    )
    default int startupDelay() {
        return 30;
    }

    @ConfigItem(
            keyName = "membershipFilter",
            name = "World Type",
            description = "Choose between member worlds, free worlds, or both",
            position = 1,
            section = worldSection
    )
    default WorldMembershipFilter membershipFilter() {
        return WorldMembershipFilter.BOTH;
    }

    @ConfigItem(
            keyName = "avoidPvpWorlds",
            name = "Avoid PvP Worlds",
            description = "Skip PvP and high-risk worlds when hopping",
            position = 2,
            section = worldSection
    )
    default boolean avoidPvpWorlds() {
        return true;
    }

    @ConfigItem(
            keyName = "avoidSkillTotalWorlds",
            name = "Avoid Skill Total Worlds",
            description = "Skip worlds with skill total requirements",
            position = 3,
            section = worldSection
    )
    default boolean avoidSkillTotalWorlds() {
        return true;
    }

    // =========================================
    // ========== HOP TRIGGERS ================
    // =========================================

    @ConfigItem(
            keyName = "enablePlayerDetection",
            name = "Enable Player Detection",
            description = "Hop worlds when too many players are detected nearby",
            position = 0,
            section = triggersSection
    )
    default boolean enablePlayerDetection() {
        return true;
    }

    @Range(min = 0, max = 20)
    @ConfigItem(
            keyName = "maxPlayers",
            name = "Max Players Nearby",
            description = "Maximum number of other players allowed nearby (0 = zero tolerance, hop if ANY players detected)",
            position = 1,
            section = triggersSection
    )
    default int maxPlayers() {
        return 3;
    }

    @Range(min = 0, max = 50)
    @ConfigItem(
            keyName = "detectionRadius",
            name = "Detection Radius",
            description = "Radius in tiles to check for other players (0 = same tile only)",
            position = 2,
            section = triggersSection
    )
    default int detectionRadius() {
        return 10;
    }

    @ConfigItem(
            keyName = "showPlayerRadius",
            name = "Show Player Detection Radius",
            description = "Visually display the player detection radius on the game canvas",
            position = 3,
            section = triggersSection
    )
    default boolean showPlayerRadius() {
        return true;
    }

    @ConfigItem(
            keyName = "enableTimeHopping",
            name = "Enable Time-based Hopping",
            description = "Hop worlds after a set amount of time",
            position = 3,
            section = triggersSection
    )
    default boolean enableTimeHopping() {
        return false;
    }

    @Range(min = 1, max = 60)
    @ConfigItem(
            keyName = "hopIntervalMinutes",
            name = "Hop Interval (minutes)",
            description = "Time in minutes before automatically hopping worlds",
            position = 4,
            section = triggersSection
    )
    default int hopIntervalMinutes() {
        return 10;
    }

    @ConfigItem(
            keyName = "enableChatDetection",
            name = "Enable Chat Detection",
            description = "Hop worlds when someone else speaks in public chat",
            position = 5,
            section = triggersSection
    )
    default boolean enableChatDetection() {
        return true;
    }

    @ConfigItem(
            keyName = "ignoreFriends",
            name = "Ignore Friends Chat",
            description = "Don't hop when friends speak in public chat",
            position = 6,
            section = triggersSection
    )
    default boolean ignoreFriends() {
        return true;
    }

    // =========================================
    // =========== ADVANCED SECTION ===========
    // =========================================

    @Range(min = 1, max = 10)
    @ConfigItem(
            keyName = "hopCooldown",
            name = "Hop Cooldown (seconds)",
            description = "Minimum time between world hops to avoid spam",
            position = 0,
            section = advancedSection
    )
    default int hopCooldown() {
        return 5;
    }

    @Range(min = 0, max = 30)
    @ConfigItem(
            keyName = "randomDelay",
            name = "Random Delay (seconds)",
            description = "Add random delay before hopping (0 = no delay)",
            position = 1,
            section = advancedSection
    )
    default int randomDelay() {
        return 3;
    }

    @ConfigItem(
            keyName = "showNotifications",
            name = "Show Notifications",
            description = "Show chat notifications when hopping worlds",
            position = 2,
            section = advancedSection
    )
    default boolean showNotifications() {
        return true;
    }

    @ConfigItem(
            keyName = "debugMode",
            name = "Debug Mode",
            description = "Show debug information in overlay",
            position = 3,
            section = advancedSection
    )
    default boolean debugMode() {
        return false;
    }
}
