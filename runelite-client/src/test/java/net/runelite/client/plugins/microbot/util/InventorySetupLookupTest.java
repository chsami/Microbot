package net.runelite.client.plugins.microbot.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.inventorysetups.MInventorySetupsPlugin;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class InventorySetupLookupTest {
    private List<InventorySetup> previous;

    @Before public void saveSetups() {
        previous = MInventorySetupsPlugin.inventorySetups;
    }

    @After public void restoreSetups() {
        MInventorySetupsPlugin.inventorySetups = previous;
    }

    private static InventorySetup named(String name) {
        InventorySetup setup = mock(InventorySetup.class);
        when(setup.getName()).thenReturn(name);
        return setup;
    }

    @Test public void lookupIsFalseWhenSetupsNotLoaded() {
        MInventorySetupsPlugin.inventorySetups = null;
        assertFalse(Rs2InventorySetup.isInventorySetup("Birdhouse"));
    }

    @Test public void nameConstructorLeavesSetupNullWhenSetupsNotLoaded() {
        MInventorySetupsPlugin.inventorySetups = null;
        Rs2InventorySetup loader = new Rs2InventorySetup("Birdhouse", mock(ScheduledFuture.class));
        assertNull(loader.inventorySetup);
    }

    @Test public void nameConstructorFallsBackToDefaultSetup() {
        InventorySetup fallback = named("default");
        MInventorySetupsPlugin.inventorySetups = new ArrayList<>(List.of(named("Other"), fallback));
        Rs2InventorySetup loader = new Rs2InventorySetup("Birdhouse", mock(ScheduledFuture.class));
        assertSame(fallback, loader.inventorySetup);
    }

    @Test public void lookupMatchesNameIgnoringCase() {
        MInventorySetupsPlugin.inventorySetups = new ArrayList<>(Arrays.asList(null, named("Birdhouse")));
        assertTrue(Rs2InventorySetup.isInventorySetup("birdhouse"));
        assertFalse(Rs2InventorySetup.isInventorySetup("Herb run"));
    }
}
