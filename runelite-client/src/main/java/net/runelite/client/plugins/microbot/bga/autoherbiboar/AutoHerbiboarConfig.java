package net.runelite.client.plugins.microbot.bga.autoherbiboar;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("AutoHerbiboar")
public interface AutoHerbiboarConfig extends Config {
    
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
            name = "Optionals to use",
            description = "Optionals to use",
            position = 0
    )
    String OPTIONALS_SECTION = "optionals";

    @ConfigItem(
            keyName = "useHerbSack",
            name = "Herb sack",
            description = "",
            section = OPTIONALS_SECTION,
            position = 0
    )
    default boolean useHerbSack() {
        return false;
    }
    @ConfigItem(
            keyName = "useMagicSecateurs",
            name = "Magic secateurs",
            description = "",
            section = OPTIONALS_SECTION,
            position = 1
    )
    default boolean useMagicSecateurs() {
        return false;
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

    @ConfigSection(
        name = "Fossils to drop",
        description = "Fossils you don't want to keep",
        position = 2,
        closedByDefault = true
    )
    String FOSSILS_SECTION = "fossils";

    @ConfigItem(
        keyName = "dropSmallFossil",
        name = "Small fossil",
        description = "",
        section = FOSSILS_SECTION,
        position = 0
    )
    default boolean dropSmallFossil() {
        return false;
    }

    @ConfigItem(
        keyName = "dropMediumFossil",
        name = "Medium fossil",
        description = "",
        section = FOSSILS_SECTION,
        position = 1
    )
    default boolean dropMediumFossil() {
        return false;
    }

    @ConfigItem(
        keyName = "dropLargeFossil",
        name = "Large fossil",
        description = "",
        section = FOSSILS_SECTION,
        position = 2
    )
    default boolean dropLargeFossil() {
        return false;
    }

    @ConfigItem(
        keyName = "dropRareFossil",
        name = "Rare fossil",
        description = "",
        section = FOSSILS_SECTION,
        position = 3
    )
    default boolean dropRareFossil() {
        return false;
    }

    @ConfigSection(
        name = "Herbs to drop",
        description = "Herbs you don't want to keep",
        position = 3,
        closedByDefault = true
    )
    String HERBS_SECTION = "herbs";

    @ConfigItem(
        keyName = "dropGuam",
        name = "Guam",
        description = "",
        section = HERBS_SECTION,
        position = 0
    )
    default boolean dropGuam() { return false; }

    @ConfigItem(
        keyName = "dropMarrentill",
        name = "Marrentill",
        description = "",
        section = HERBS_SECTION,
        position = 1
    )
    default boolean dropMarrentill() { return false; }

    @ConfigItem(
        keyName = "dropTarromin",
        name = "Tarromin",
        description = "",
        section = HERBS_SECTION,
        position = 2
    )
    default boolean dropTarromin() { return false; }

    @ConfigItem(
        keyName = "dropHarralander",
        name = "Harralander",
        description = "",
        section = HERBS_SECTION,
        position = 3
    )
    default boolean dropHarralander() { return false; }

    @ConfigItem(
        keyName = "dropRanarr",
        name = "Ranarr",
        description = "",
        section = HERBS_SECTION,
        position = 4
    )
    default boolean dropRanarr() { return false; }

    @ConfigItem(
        keyName = "dropToadflax",
        name = "Toadflax",
        description = "",
        section = HERBS_SECTION,
        position = 5
    )
    default boolean dropToadflax() { return false; }

    @ConfigItem(
        keyName = "dropIrit",
        name = "Irit",
        description = "",
        section = HERBS_SECTION,
        position = 6
    )
    default boolean dropIrit() { return false; }

    @ConfigItem(
        keyName = "dropAvantoe",
        name = "Avantoe",
        description = "",
        section = HERBS_SECTION,
        position = 7
    )
    default boolean dropAvantoe() { return false; }

    @ConfigItem(
        keyName = "dropKwuarm",
        name = "Kwuarm",
        description = "",
        section = HERBS_SECTION,
        position = 8
    )
    default boolean dropKwuarm() { return false; }

    @ConfigItem(
        keyName = "dropSnapdragon",
        name = "Snapdragon",
        description = "",
        section = HERBS_SECTION,
        position = 9
    )
    default boolean dropSnapdragon() { return false; }

    @ConfigItem(
        keyName = "dropCadantine",
        name = "Cadantine",
        description = "",
        section = HERBS_SECTION,
        position = 10
    )
    default boolean dropCadantine() { return false; }

    @ConfigItem(
        keyName = "dropLantadyme",
        name = "Lantadyme",
        description = "",
        section = HERBS_SECTION,
        position = 11
    )
    default boolean dropLantadyme() { return false; }

    @ConfigItem(
        keyName = "dropDwarfWeed",
        name = "Dwarf weed",
        description = "",
        section = HERBS_SECTION,
        position = 12
    )
    default boolean dropDwarfWeed() { return false; }

    @ConfigItem(
        keyName = "dropTorstol",
        name = "Torstol",
        description = "",
        section = HERBS_SECTION,
        position = 13
    )
    default boolean dropTorstol() { return false; }
}
