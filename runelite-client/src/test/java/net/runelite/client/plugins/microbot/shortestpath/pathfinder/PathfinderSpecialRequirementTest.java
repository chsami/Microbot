package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import net.runelite.client.plugins.microbot.shortestpath.Transport;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PathfinderSpecialRequirementTest {
    @Test
    public void specialLevelsParticipateInTransportAvailability() {
        int[] required = new int[Transport.REQUIREMENT_LEVEL_COUNT];
        required[Transport.TOTAL_LEVEL_INDEX] = 2000;
        required[Transport.COMBAT_LEVEL_INDEX] = 40;
        required[Transport.QUEST_POINTS_INDEX] = 100;

        int[] current = new int[Transport.REQUIREMENT_LEVEL_COUNT];
        current[Transport.TOTAL_LEVEL_INDEX] = 2000;
        current[Transport.COMBAT_LEVEL_INDEX] = 40;
        current[Transport.QUEST_POINTS_INDEX] = 100;

        assertTrue(PathfinderConfig.meetsRequiredLevels(required, current));

        current[Transport.COMBAT_LEVEL_INDEX] = 39;
        assertFalse(PathfinderConfig.meetsRequiredLevels(required, current));
        current[Transport.COMBAT_LEVEL_INDEX] = 40;
        current[Transport.TOTAL_LEVEL_INDEX] = 1999;
        assertFalse(PathfinderConfig.meetsRequiredLevels(required, current));
        current[Transport.TOTAL_LEVEL_INDEX] = 2000;
        current[Transport.QUEST_POINTS_INDEX] = 99;
        assertFalse(PathfinderConfig.meetsRequiredLevels(required, current));
    }

    @Test
    public void malformedLevelArraysFailClosed() {
        assertFalse(PathfinderConfig.meetsRequiredLevels(
                new int[Transport.REQUIREMENT_LEVEL_COUNT],
                new int[Transport.REQUIREMENT_LEVEL_COUNT - 1]));
        assertFalse(PathfinderConfig.meetsRequiredLevels(null, new int[Transport.REQUIREMENT_LEVEL_COUNT]));
    }
}
