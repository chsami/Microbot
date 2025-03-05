package net.runelite.client.plugins.microbot.runecrafting.rcx;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("rcx") // Identifier for the plugin configuration
public interface RcxConfig extends Config {

    @ConfigItem(
            keyName = "altarLocation",
            name = "Locations Location",
            description = "The location of the altar to runecraft at. E.g., 'Mind Locations'."
    )
    default String altarLocation() {
        return "Mind Locations";
    }

    @ConfigItem(
            keyName = "essenceType",
            name = "Essence Type",
            description = "The type of essence to use (Pure Essence or Rune Essence)."
    )
    default String essenceType() {
        return "Pure Essence";
    }

    @ConfigItem(
            keyName = "useAntiban",
            name = "Enable Antiban",
            description = "Whether to enable antiban features like random mouse movements."
    )
    default boolean useAntiban() {
        return true;
    }

    @ConfigItem(
            keyName = "teleportItemId",
            name = "Teleport Item ID",
            description = "The item ID of the teleport to POH. Default is 9790 (Teleport to POH)."
    )
    default int teleportItemId() {
        return 9790;
    }
}
