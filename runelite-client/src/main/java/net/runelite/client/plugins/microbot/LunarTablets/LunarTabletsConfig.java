package net.runelite.client.plugins.microbot.LunarTablets;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("lunartablets")
@ConfigInformation("Start on Lunar-Isle with Lunar spells. Make sure you have your staff of X equipped and enough laws, astrals, and soft clay.")
public interface LunarTabletsConfig extends Config {

    @ConfigItem(
            keyName = "selectedTablet",
            name = "Lunar Tablets",
            description = "Select the lunar tablet to craft",
            position = 0
    )
    default LunarTablet selectedTablet() {
        return LunarTablet.MOONCLAN_TELEPORT; // Default selection
    }

    enum LunarTablet {
        MOONCLAN_TELEPORT("Moonclan Teleport", 8008),
        OURANIA_TELEPORT("Ourania Teleport", 8009),
        WATERBIRTH_TELEPORT("Waterbirth Teleport", 8010),
        BARBARIAN_TELEPORT("Barbarian Teleport", 8011),
        KHAZARD_TELEPORT("Khazard Teleport", 8012),
        FISHING_GUILD_TELEPORT("Fishing Guild Teleport", 8013),
        CATHERBY_TELEPORT("Catherby Teleport", 8014),
        ICE_PLATEAU_TELEPORT("Ice Plateau Teleport", 8015);

        private final String name;
        private final int itemId;

        LunarTablet(String name, int itemId) {
            this.name = name;
            this.itemId = itemId;
        }

        public String getName() {
            return name;
        }

        public int getItemId() {
            return itemId;
        }
    }
}