package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.GameObject;
import net.runelite.api.WallObject;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public class Rs2InteractionApproachTest {
    @Test
    public void onlyNearbySameFloorObjectsQualify() {
        WorldPoint player = new WorldPoint(3200, 3200, 0);
        assertTrue(Rs2InteractionApproach.withinRange(player, new WorldPoint(3212, 3200, 0)));
        assertFalse(Rs2InteractionApproach.withinRange(player, new WorldPoint(3213, 3200, 0)));
        assertFalse(Rs2InteractionApproach.withinRange(player, new WorldPoint(3200, 3200, 1)));
        assertFalse(Rs2InteractionApproach.withinRange(null, player));
        assertFalse(Rs2InteractionApproach.withinRange(player, null));
    }

    @Test
    public void absentObjectDoesNotEndWalking() {
        assertFalse(Rs2InteractionApproach.isReady((GameObject) null));
        assertFalse(Rs2InteractionApproach.isReady((WallObject) null));
    }

    @Test
    public void wallObjectRequiresReachableTileBesideIt() {
        WorldPoint target = new WorldPoint(3200, 3200, 0);
        assertTrue(Rs2InteractionApproach.hasReachableWallApproach(target, Arrays.asList(
                new WorldPoint(3201, 3200, 0), new WorldPoint(3210, 3210, 0))));
        assertFalse(Rs2InteractionApproach.hasReachableWallApproach(target, Arrays.asList(
                new WorldPoint(3201, 3200, 1), new WorldPoint(3210, 3210, 0))));
        assertFalse(Rs2InteractionApproach.hasReachableWallApproach(target, Collections.emptyList()));
    }
}
