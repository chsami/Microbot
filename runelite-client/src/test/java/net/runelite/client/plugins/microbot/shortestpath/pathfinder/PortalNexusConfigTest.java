package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import net.runelite.client.plugins.microbot.shortestpath.ShortestPathConfig;
import org.junit.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PortalNexusConfigTest {
    @Test
    public void nexusDefaultsToEnabledForExistingUsers() {
        assertTrue(new ShortestPathConfig() { }.usePortalNexus());
    }

    @Test
    public void disablingNexusChangesCacheKeyWithoutDisablingPoh() throws Exception {
        PathfinderConfig config = mock(PathfinderConfig.class, CALLS_REAL_METHODS);
        Field poh = PathfinderConfig.class.getDeclaredField("usePoh");
        Field nexus = PathfinderConfig.class.getDeclaredField("usePortalNexus");
        Method bits = PathfinderConfig.class.getDeclaredMethod("packTransportRefreshToggleBits");
        poh.setAccessible(true);
        nexus.setAccessible(true);
        bits.setAccessible(true);
        poh.setBoolean(config, true);
        nexus.setBoolean(config, true);
        long enabled = (long) bits.invoke(config);
        nexus.setBoolean(config, false);
        long disabled = (long) bits.invoke(config);
        assertNotEquals(enabled, disabled);
        assertEquals(1, Long.bitCount(enabled ^ disabled));
        assertTrue(poh.getBoolean(config));
    }
}
