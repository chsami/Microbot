package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
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
        assertFalse(Rs2InteractionApproach.isReady(null));
    }
}
