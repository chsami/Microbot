package net.runelite.client.plugins.microbot.collector;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("collector")
@ConfigInformation("<b>Mort Myre Fungus Collection:</b> <br />" +
        "   - Requires Silver sickle (b) in inventory <br />" +
        "   - Ardy cloak, Dramen staff & Ring of Dueling equipped <br />" +
        "<b>Snape Grass Collection:</b> <br />" +
        "   - Collects from ground spawns in Hosidius <br />" +
        "<b>Super Antipoison Collection:</b> <br />" +
        "   - Requires Rings of Dueling <br />" +
        "<b>Seaweed Spores:</b> <br />" +
        "   - Equip fishbowl helm & diving apparatus <br />")
public interface CollectorConfig extends Config {
    @ConfigSection(
            name = "Basic Collecting",
            description = "Settings for basic collection activities",
            position = 0
    )
    String basicCollecting = "basicCollecting";

    @ConfigSection(
            name = "Seaweed",
            description = "Settings for seaweed collection and alching",
            position = 1
    )
    String seaweedSection = "seaweedSection";

    enum CollectionType {
        NONE("None"),
        MORT_MYRE_FUNGUS("Mort Myre Fungus"),
        SNAPE_GRASS("Snape Grass"),
        SUPER_ANTI_POISON("Super Antipoison");

        private final String name;

        CollectionType(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    @ConfigItem(
            keyName = "collectionType",
            name = "Basic",
            description = "Select what to collect",
            position = 0,
            section = basicCollecting
    )
    default CollectionType collectionType() {
        return CollectionType.NONE;
    }

    @ConfigItem(
            keyName = "enableSeaweed",
            name = "Enable Seaweed",
            description = "Enable seaweed collection",
            position = 0,
            section = seaweedSection
    )
    default boolean enableSeaweed() {
        return false;
    }

    @ConfigItem(
            keyName = "enableSeaweedAlching",
            name = "Enable Alching",
            description = "Enable alching while collecting seaweed",
            position = 1,
            section = seaweedSection
    )
    default boolean enableSeaweedAlching() {
        return false;
    }

    @ConfigItem(
            keyName = "alchItemName",
            name = "Item to Alch",
            description = "Name or ID of the item to alch while collecting seaweed",
            position = 2,
            section = seaweedSection
    )
    default String alchItemName() {
        return "";
    }
}
