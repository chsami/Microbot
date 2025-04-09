package net.runelite.client.plugins.microbot.birdhouseruns;

import lombok.Getter;
import net.runelite.api.ItemID;

public class FornBirdhouseRunsInfo {
    public static states botStatus;

    public enum states {
        GEARING,
        TELEPORTING,
        VERDANT_TELEPORT,
        MUSHROOM_TELEPORT,
        DISMANTLE_HOUSE_1,
        BUILD_HOUSE_1,
        SEED_HOUSE_1,
        DISMANTLE_HOUSE_2,
        BUILD_HOUSE_2,
        SEED_HOUSE_2,
        DISMANTLE_HOUSE_3,
        BUILD_HOUSE_3,
        SEED_HOUSE_3,
        DISMANTLE_HOUSE_4,
        BUILD_HOUSE_4,
        SEED_HOUSE_4,
        FINISHING,
        FINISHED
    }
}
