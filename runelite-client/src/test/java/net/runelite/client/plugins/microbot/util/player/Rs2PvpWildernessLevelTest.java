package net.runelite.client.plugins.microbot.util.player;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class Rs2PvpWildernessLevelTest {
    @Test
    public void nullPointIsNotInWilderness() {
        assertEquals(0, Rs2Pvp.getWildernessLevelFrom(null));
    }

    @Test
    public void mainWildernessPointHasLevel() {
        assertEquals(11, Rs2Pvp.getWildernessLevelFrom(new WorldPoint(3100, 3600, 0)));
    }

    @Test
    public void pointOutsideWildernessIsLevelZero() {
        assertEquals(0, Rs2Pvp.getWildernessLevelFrom(new WorldPoint(3222, 3218, 0)));
    }
}
