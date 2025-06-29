package net.runelite.client.plugins.microbot.TaF.GiantSeaweedFarmer;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;

@ConfigInformation("Farms giant seaweed underwater on the fossil island. Can be started anywhere. Ensure you have the fishbowl helmet and apparatus in your bank.")
@ConfigGroup("GiantSeaweedFarmer")
public interface GiantSeaweedFarmerConfig extends Config {
    @ConfigItem(
            keyName = "returnToBank",
            name = "Return to bank",
            description = "Should the bot return to bank after the seaweed run?",
            position = 1
    )
    default boolean returnToBank() {
        return false;
    }
    @ConfigItem(
            keyName = "compostType",
            name = "Compost type",
            description = "Which compost type should be used?",
            position = 2
    )
    default CompostType compostType() {
        return CompostType.NONE;
    }
    public enum CompostType {
        NONE,
        COMPOST,
        SUPERCOMPOST,
        ULTRACOMPOST,
        BOTTOMLESS_COMPOST_BUCKET,

    }
}
