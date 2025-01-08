package net.runelite.client.plugins.microbot.prayer.burybones;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.prayer.burybones.enums.Activity;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Ashes;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Bones;

@ConfigGroup("BuryBones")
public interface BuryBonesConfig extends Config {
    String configGroup = "bury-bones";
    String activityKeyName = "activity";
    String bonesKeyName = "bones";
    String ashesKeyName = "ashes";
    String afkKeyName = "afk";
    String afkMinKeyName = "afkMin";
    String afkMaxKeyName = "afkMax";

    @ConfigSection(
            name = "General Settings",
            description = "Configure general plugin configuration & preferences",
            position = 0
    )
    String generalSection = "general";

    @ConfigSection(
            name = "Afk Settings",
            description = "Configure AFK settings",
            position = 1
    )
    String afkSection = "afk";

    @ConfigItem(
            keyName = activityKeyName,
            name = "Activity",
            description = "The activity to perform",
            position = 0,
            section = generalSection
    )
    default Activity activity() {
        return Activity.BURY;
    }

    @ConfigItem(
            keyName = bonesKeyName,
            name = "Bones",
            description = "The bones to bury",
            position = 1,
            section = generalSection
    )
    default Rs2Bones bones() {
        return Rs2Bones.BONES;
    }

    @ConfigItem(
            keyName = ashesKeyName,
            name = "Ashes",
            description = "The ashes to scatter",
            position = 2,
            section = generalSection
    )
    default Rs2Ashes ashes() {
        return Rs2Ashes.FIENDISH_ASHES;
    }

    @ConfigItem(
            keyName = afkKeyName,
            name = "Random AFKs",
            description = "Enable random AFKs",
            position = 0,
            section = afkSection
    )
    default boolean Afk() {
        return false;
    }

    @ConfigItem(
            keyName = afkMinKeyName,
            name = "Afk Min",
            description = "Minimum time to AFK in seconds",
            position = 1,
            section = afkSection
    )
    default int AfkMin() {
        return 3;
    }

    @ConfigItem(
            keyName = afkMaxKeyName,
            name = "Afk Max",
            description = "Maximum time to AFK in seconds",
            position = 2,
            section = afkSection
    )
    default int AfkMax() {
        return 10;
    }
}
