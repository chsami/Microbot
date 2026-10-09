package net.runelite.client.plugins.microbot.util.coords;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

public class Rs2WorldPointNullPointTest
{
    private static final WorldPoint TARGET = new WorldPoint(3200, 3200, 0);

    @Test
    public void pathToReturnsNullWhenWrappedPointIsNull()
    {
        Rs2WorldPoint point = new Rs2WorldPoint(null);

        assertNull(point.pathTo(TARGET));
        assertNull(point.pathTo(TARGET, true));
    }

    @Test
    public void pathToReturnsNullWhenTargetIsNull()
    {
        assertNull(new Rs2WorldPoint(TARGET).pathTo(null));
    }

    @Test
    public void distanceToPathIsUnreachableWhenWrappedPointIsNull()
    {
        assertEquals(Integer.MAX_VALUE, new Rs2WorldPoint(null).distanceToPath(TARGET));
    }

    @Test
    public void objectMethodsTolerateNullWrappedPoint()
    {
        Rs2WorldPoint empty = new Rs2WorldPoint(null);

        assertEquals(new Rs2WorldPoint(null), empty);
        assertNotEquals(new Rs2WorldPoint(TARGET), empty);
        assertNotEquals(empty, new Rs2WorldPoint(TARGET));
        assertEquals(0, empty.hashCode());
        assertEquals("null", empty.toString());
    }
}
