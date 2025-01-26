package net.runelite.client.plugins.microbot.plankrunner;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.plankrunner.enums.Plank;
import net.runelite.client.plugins.microbot.plankrunner.enums.SawmillLocation;

@ConfigGroup(PlankRunnerConfig.configGroup)
@ConfigInformation(
        "• This plugin will craft planks at the sawmill" +
        "• Ensure you have logs in the bank & enough GP for sawmill costs <br />"
)
public interface PlankRunnerConfig extends Config {
    
    String configGroup = "micro-plankrunner";
    String plank = "plank";
    String sawmillLocation = "sawmilllocation";
    String useEnergyRestorePotions = "useEnergyRestorePotions";
    String drinkAtPercent = "drinkAtPercent";
    String toggleOverlay = "toggleOverlay";

    @ConfigSection(
            name = "General Settings",
            description = "Configure general plugin configuration & preferences",
            position = 0
    )
    String generalSection = "general";
    
    @ConfigSection(
            name = "Supplies",
            description = "Supplies Settings",
            position = 1
    )
    String suppliesSection = "supplies";

    @ConfigSection(
            name = "Overlay",
            description = "Overlay Settings",
            position = 2
    )
    String overlaySection = "overlay";

    @ConfigItem(
            keyName = plank,
            name = "Plank",
            description = "Choose the planks to make",
            position = 0,
            section = generalSection
    )
    default Plank plank() {
        return Plank.PLANK;
    }

    @ConfigItem(
            keyName = sawmillLocation,
            name = "Sawmill Location",
            description = "Choose the Sawmill Location",
            position = 1,
            section = generalSection
    )
    default SawmillLocation sawmillLocation() {
        return SawmillLocation.VARROCK;
    }

    @ConfigItem(
            keyName = useEnergyRestorePotions,
            name = "Use Energy Restore Potions",
            description = "Should withdraw & use stamina potions OR energy potions",
            position = 2,
            section = suppliesSection
    )
    default boolean useEnergyRestorePotions() {
        return false;
    }

    @Range(
            min = 1,
            max = 100
    )
    @ConfigItem(
            keyName = drinkAtPercent,
            name = "Drink Energy Restore At",
            description = "Run energy should drink stamina or energy potions at",
            position = 3,
            section = suppliesSection
    )
    default int drinkAtPercent() {
        return 45;
    }

    @ConfigItem(
            keyName = toggleOverlay,
            name = "Toggle Overlay",
            description = "Should hide the overlay",
            position = 0,
            section = overlaySection
    )
    default boolean toggleOverlay() {
        return false;
    }
}
