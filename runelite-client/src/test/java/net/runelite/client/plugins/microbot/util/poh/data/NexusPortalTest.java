package net.runelite.client.plugins.microbot.util.poh.data;

import org.junit.Test;
import static org.junit.Assert.*;

public class NexusPortalTest {
    @Test
    public void unknownAndEmptySlotsDoNotIndexOutsideTheCatalog() {
        assertNull(NexusPortal.fromVarbitValue(-1));
        assertNull(NexusPortal.fromVarbitValue(0));
        assertNull(NexusPortal.fromVarbitValue(32));
        assertNull(NexusPortal.fromVarbitValue(Integer.MAX_VALUE));
    }

    @Test
    public void knownSlotsKeepTheirMappings() {
        assertEquals(NexusPortal.VARROCK, NexusPortal.fromVarbitValue(1));
        assertEquals(NexusPortal.LUMBRIDGE, NexusPortal.fromVarbitValue(2));
        assertEquals(NexusPortal.CIVITAS_ILLA_FORTIS, NexusPortal.fromVarbitValue(31));
    }
}
