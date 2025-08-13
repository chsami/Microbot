package net.runelite.client.plugins.microbot.crafting;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.crafting.enums.*;

@ConfigGroup(CraftingConfig.GROUP)
public interface CraftingConfig extends Config {

    String GROUP = "Crafting";

    @ConfigSection(
            name = "General",
            description = "General",
            position = 0
    )
    String generalSection = "general";
    @ConfigSection(
            name = "Gems",
            description = "Config for gem cutting",
            position = 1,
            closedByDefault = true
    )
    String gemSection = "gem";
    @ConfigSection(
            name = "Glass",
            description = "Config for glass blowing",
            position = 2,
            closedByDefault = true
    )
    String glassSection = "glass";
    @ConfigItem(
            keyName = "fletchIntoBoltTips",
            name = "Fletch into Bolt Tips",
            description = "Fletch cut gems into bolt tips if possible",
            position = 1,
            section = gemSection
    )
    default boolean fletchIntoBoltTips() {
        return false;
    }
    @ConfigSection(
            name = "Staffs",
            description = "Config for staff making",
            position = 2,
            closedByDefault = true
    )
    String staffSection = "staff";
    @ConfigSection(
            name = "Flax",
            description = "Configure Settings for Flax Spinning activity",
            position = 3,
            closedByDefault = true
    )
    String flaxSpinSection = "flaxspin";

    @ConfigSection(
            name = "Dragon Leather",
            description = "Configure settings for Dragon Leather activity",
            position = 4,
            closedByDefault = true
    )
    String dragonLeatherSection = "dragonLeather";

    @ConfigItem(
            keyName = "Activity",
            name = "Activity",
            description = "Choose the type of crafting activity to perform",
            position = 0,
            section = generalSection
    )
    default Activities activityType() {
        return Activities.NONE;
    }

    @ConfigItem(
            keyName = "Afk",
            name = "Random AFKs",
            description = "Randomly afks between 3 and 60 seconds",
            position = 1,
            section = generalSection
    )
    default boolean Afk() {
        return false;
    }

    @ConfigItem(
            keyName = "Gem",
            name = "Gem",
            description = "Choose the type of gem to cut",
            position = 0,
            section = gemSection
    )
    default Gems gemType() {
        return Gems.NONE;
    }

    @ConfigItem(
            keyName = "Glass",
            name = "Glass",
            description = "Choose the type of glass item to blow",
            position = 0,
            section = glassSection
    )
    default Glass glassType() {
        return Glass.NONE;
    }

    @ConfigItem(
            keyName = "Staffs",
            name = "Staffs",
            description = "Choose the type of battlestaff to make",
            position = 0,
            section = staffSection
    )
    default Staffs staffType() {
        return Staffs.NONE;
    }

    @ConfigItem(
            name = "Location",
            description = "Choose Location where to spin flax",
            keyName = "flaxSpinLocation",
            position = 0,
            section = flaxSpinSection
    )
    default FlaxSpinLocations flaxSpinLocation() {
        return FlaxSpinLocations.NONE;
    }

    @ConfigItem(
            name = "Armour",
            description = "Choose type of dragon leather armour",
            keyName = "dragonLeatherArmour",
            position = 0,
            section = dragonLeatherSection
    )
    default DragonLeatherArmour dragonLeatherType() { return DragonLeatherArmour.NONE; }

    @ConfigItem(
            name = "Use Costume Needle",
            description = "Use costume needle instead of regular needle + thread (saves inventory slot)",
            keyName = "useCostumeNeedle",
            position = 1,
            section = dragonLeatherSection
    )
    default boolean useCostumeNeedle() { return false; }
}
