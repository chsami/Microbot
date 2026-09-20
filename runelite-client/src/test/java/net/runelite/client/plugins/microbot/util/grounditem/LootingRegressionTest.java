package net.runelite.client.plugins.microbot.util.grounditem;

import com.google.common.collect.HashBasedTable;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.grounditems.GroundItem;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.ApiTestClient;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class LootingRegressionTest {
    @Test public void exceptionDoesNotLeaveScriptsPaused() throws Exception {
        boolean previous = Microbot.pauseAllScripts.getAndSet(false);
        try (ApiTestClient env = new ApiTestClient()) {
            try { Rs2GroundItem.runWhilePaused(() -> { throw new IllegalStateException("expected"); }); fail(); }
            catch (IllegalStateException expected) { }
            assertFalse(Microbot.pauseAllScripts.get());
        } finally { Microbot.pauseAllScripts.set(previous); }
    }

    @Test public void userPauseDuringLootIsNotCleared() throws Exception {
        boolean previous = Microbot.pauseAllScripts.getAndSet(false);
        try (ApiTestClient env = new ApiTestClient()) {
            Rs2GroundItem.runWhilePaused(() -> { Microbot.pauseAllScripts.set(true); return true; });
            assertTrue(Microbot.pauseAllScripts.get());
        } finally { Microbot.pauseAllScripts.set(previous); }
    }

    @Test public void fullInventoryDoesNotReportSuccessfulLoot() throws Exception {
        try (ApiTestClient env = new ApiTestClient(); MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            GroundItem item = GroundItem.builder().id(1).quantity(1).stackable(false).build();
            assertFalse(Rs2GroundItem.coreLoot(item));
        }
    }

    @Test public void quantityDecreaseCompletesGroundChangeWait() throws Exception {
        GroundItem item = GroundItem.builder().id(1).quantity(3).location(new WorldPoint(3200, 3200, 0)).build();
        var table = HashBasedTable.<WorldPoint, Integer, GroundItem>create();
        table.put(item.getLocation(), item.getId(), item);
        try (ApiTestClient env = new ApiTestClient(); MockedStatic<Rs2GroundItem> ground = mockStatic(Rs2GroundItem.class, CALLS_REAL_METHODS)) {
            ground.when(Rs2GroundItem::getGroundItems).thenReturn(table);
            assertTrue(Rs2GroundItem.waitForGroundItemDespawn(() -> item.setQuantity(2), item));
        }
    }
}
