package net.runelite.client.plugins.microbot.mining.fxminer.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.World;
import net.runelite.api.coords.WorldPoint;

import java.util.Random;


@Getter
@RequiredArgsConstructor
public enum MiningLocation {
        RANDOM_POINT_1(new WorldPoint(2629, 3142, 0)),
        RANDOM_POINT_2(new WorldPoint(2630, 3142, 0)),
        RANDOM_POINT_3(new WorldPoint(2629, 3144, 0)),
        RANDOM_POINT_4(new WorldPoint(2628, 3145, 0)),
        IRON_ORE_LOC(new WorldPoint(2627, 3141, 0)),
        NULL(null);
        private static final Random RANDOM = new Random();
        private final WorldPoint worldPoint;

        public static MiningLocation getRandomMiningLocation() {
        MiningLocation[] spots = values();
        return spots[RANDOM.nextInt(spots.length)];
        }
    }
