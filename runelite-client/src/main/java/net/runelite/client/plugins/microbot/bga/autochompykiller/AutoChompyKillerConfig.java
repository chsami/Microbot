package net.runelite.client.plugins.microbot.bga.autochompykiller;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("autochompykiller")
public interface AutoChompyKillerConfig extends Config {
    
    enum RunEnergyOption {
        NONE("None"),
        STAMINA_POTION("Stamina potion"),
        SUPER_ENERGY_POTION("Super energy potion"),
        ENERGY_POTION("Energy potion"),
        STRANGE_FRUIT("Strange fruit");
        
        private final String name;
        
        RunEnergyOption(String name) {
            this.name = name;
        }
        
        @Override
        public String toString() {
            return name;
        }
    }

    @ConfigSection(
        name = "Optionals",
        description = "Optionals",
        position = 0
    )
    String OPTIONALS_SECTION = "optionals";

    @ConfigItem(
        keyName = "pluckChompys",
        name = "Pluck chompy's",
        description = "Pluck chompy's for feathers (disable for faster kills)",
        section = OPTIONALS_SECTION,
        position = 0
    )
    default boolean pluckChompys() {
        return true;
    }

    @ConfigSection(
        name = "Run energy management",
        description = "Run energy management",
        position = 1
    )
    String RUN_ENERGY_SECTION = "runenergy";

    @ConfigItem(
        keyName = "runEnergyOption",
        name = "Restore with",
        description = "Select which item to use to restore run energy",
        section = RUN_ENERGY_SECTION,
        position = 0
    )
    default RunEnergyOption runEnergyOption() {
        return RunEnergyOption.NONE;
    }

    @ConfigItem(
        keyName = "dropEmptyVials",
        name = "Drop empty vials",
        description = "Drop empty vials from drinking potions",
        section = RUN_ENERGY_SECTION,
        position = 1
    )
    default boolean dropEmptyVials() {
        return false;
    }

    @ConfigSection(
        name = "Stop conditions",
        description = "Conditions to automatically stop the script",
        position = 2
    )
    String STOP_CONDITIONS_SECTION = "stopconditions";

    @ConfigItem(
        keyName = "stopOnKillCount",
        name = "Stop on kill count",
        description = "Stop the script after killing a specific number of chompys",
        section = STOP_CONDITIONS_SECTION,
        position = 0
    )
    default boolean stopOnKillCount() {
        return false;
    }

    @ConfigItem(
        keyName = "killCount",
        name = "Kill count",
        description = "Number of chompys to kill before stopping",
        section = STOP_CONDITIONS_SECTION,
        position = 1
    )
    default int killCount() {
        return 100;
    }

    @ConfigItem(
        keyName = "stopOnChompyChickPet",
        name = "Stop on chompy chick pet",
        description = "Stop the script when receiving the chompy chick pet",
        section = STOP_CONDITIONS_SECTION,
        position = 2
    )
    default boolean stopOnChompyChickPet() {
        return false;
    }

    @ConfigItem(
        keyName = "logoutOnCompletion",
        name = "Logout on completion",
        description = "Logout when stop conditions are met (waits until out of combat)",
        section = STOP_CONDITIONS_SECTION,
        position = 3
    )
    default boolean logoutOnCompletion() {
        return false;
    }
}