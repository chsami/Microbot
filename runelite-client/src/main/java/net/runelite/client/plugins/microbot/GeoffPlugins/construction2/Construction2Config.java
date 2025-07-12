package net.runelite.client.plugins.microbot.GeoffPlugins.construction2;

import net.runelite.api.gameval.ItemID;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(Construction2Config.GROUP)
public interface Construction2Config extends Config {

    String GROUP = "Construction2";

    enum ConstructionMode {
        OAK_LARDER("Oak Larder", ItemID.PLANK_OAK),
        OAK_DUNGEON_DOOR("Oak Dungeon Door", ItemID.PLANK_OAK),
        MAHOGANY_TABLE("Mahogany Table", ItemID.PLANK_MAHOGANY);
        // MYTHICAL_CAPE("Mythical Cape Mount", ItemID.MYTHICAL_CAPE); broken, keeps trying to remove the guild trophy space instead of build shits weird idk

        private final String name;
        private final int plankItemId;

        ConstructionMode(String name, int plankItemId) {
            this.name = name;
            this.plankItemId = plankItemId;
        }

        @Override
        public String toString() {
            return name;
        }

        public int getPlankItemId() {
            return plankItemId;
        }
    }

    @ConfigSection(
            name = "General",
            description = "General configuration",
            position = 0
    )
    String generalSection = "general";

    @ConfigItem(
            keyName = "Guide",
            name = "How to use",
            description = "How to use the script",
            position = 1,
            section = generalSection
    )
    default String GUIDE() {
        return "This script supports oak larder, oak dungeon doors, mahogany table with a demon butler. " +
                "Call the butler and use the planks on him you're going to use." +
                " Then start the plugin next to the build space with " +
                "coins, a saw, a hammer and your noted planks and the rest of your inventory un-noted planks.";
    }

    @ConfigItem(
            keyName = "selectedMode",
            name = "Mode",
            description = "Select the construction mode",
            position = 2,
            section = generalSection
    )
    default ConstructionMode selectedMode() {
        return ConstructionMode.OAK_DUNGEON_DOOR;
    }

    @ConfigItem(
            keyName = "useCustomDelay",
            name = "Use Custom Delay",
            description = "Toggle to use custom action delay",
            position = 3,
            section = generalSection
    )
    default boolean useCustomDelay() {
        return false;
    }

    @ConfigItem(
            keyName = "actionDelay",
            name = "Action Delay",
            description = "Adjust the delay (in milliseconds) between actions",
            position = 4,
            section = generalSection
    )
    default int actionDelay() {
        return 600; // Default delay of 600 milliseconds
    }
}
