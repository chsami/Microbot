package net.runelite.client.plugins.microbot.gboc;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
//import net.runelite.client.plugins.microbot.gboc.scripts.HerbRunScript;

@ConfigGroup("gboc")
public interface GbocConfig extends Config {
    enum AvailableScript {
        NONE("None"),
        //        HERB_RUN("Herb Run"),
        NMZ("NMZ");

        private final String name;

        AvailableScript(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    enum ClickMode {
        BLOCK("Block"),
        AUTO("Auto-click");

        private final String name;

        ClickMode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    @ConfigItem(
            keyName = "clickMode",
            name = "Click Mode",
            description = "What clicking mode to use",
            position = 0
    )
    default ClickMode clickMode() {
        return ClickMode.BLOCK;
    }

    @ConfigItem(
            keyName = "selectedScript",
            name = "Select Script",
            description = "Choose which script to run",
            position = 1
    )
    default AvailableScript selectedScript() {
        return AvailableScript.NONE;
    }

//    @ConfigSection(
//        name = "Herbrun",
//        description = "Herbrun specific configuration",
//        position = 2,
//        closedByDefault = true
//    )
//    String herbRunSection = "herbRunSection";
//
//    @ConfigItem(
//        keyName = "herbRunSeed",
//        name = "Seed",
//        description = "Name of the seed to use",
//        section = herbRunSection,
//        position = 3
//    )
//    default String herbRunSeed() {
//        return "Snapdragon seed";
//    }
}