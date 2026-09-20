package net.runelite.client.plugins.microbot.util.grounditem;

import java.util.*;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.grounditems.GroundItem;
import net.runelite.client.plugins.microbot.api.ApiTestClient;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class LootEngineSelectionTest {
    @Test public void executionRefreshesCandidatesAndUsesRealPickupByDefault() throws Exception {
        try (ApiTestClient env = new ApiTestClient(); MockedStatic<GroundItemPickup> pickup = mockStatic(GroundItemPickup.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            GroundItemPickup.Snapshot item = fixture(env, "Bones", 1, 50);
            LootingParameters params = new LootingParameters(10, 1, 1, 0, false, false, "bones");
            Rs2LootEngine.Builder plan = Rs2LootEngine.with(params).addByNames();
            pickup.verify(() -> GroundItemPickup.snapshot(), never());
            pickup.when(GroundItemPickup::snapshot).thenReturn(List.of(item));
            pickup.when(() -> GroundItemPickup.take(item, 0)).thenReturn(GroundItemPickup.Result.COLLECTED);
            inventory.when(Rs2Inventory::emptySlotCount).thenReturn(28);
            assertTrue(plan.loot());
            pickup.verify(() -> GroundItemPickup.take(item, 0));
            pickup.when(GroundItemPickup::snapshot).thenReturn(Collections.emptyList());
            assertFalse(plan.loot());
        }
    }

    @Test public void ignoredItemsDoNotSatisfyMinimumAndReservedSlotsAreEnforced() throws Exception {
        try (ApiTestClient env = new ApiTestClient(); MockedStatic<GroundItemPickup> pickup = mockStatic(GroundItemPickup.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            GroundItemPickup.Snapshot item = fixture(env, "Bones", 1, 50);
            pickup.when(GroundItemPickup::snapshot).thenReturn(List.of(item));
            LootingParameters params = new LootingParameters(0, 0, 10, 1, 2, false, false);
            params.setIgnoredNames(new String[]{"bones"});
            Rs2LootEngine.Builder plan = Rs2LootEngine.with(params).addByValue();
            assertFalse(plan.loot());
            params.setIgnoredNames(null);
            inventory.when(Rs2Inventory::emptySlotCount).thenReturn(2);
            assertFalse(plan.loot());
            assertEquals(GroundItemPickup.Result.NO_SPACE, plan.getLastResult());
            pickup.verify(() -> GroundItemPickup.take(any(), anyInt()), never());
        }
    }

    @Test public void valueBoundsUseLongStackTotals() throws Exception {
        try (ApiTestClient env = new ApiTestClient(); MockedStatic<GroundItemPickup> pickup = mockStatic(GroundItemPickup.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            GroundItemPickup.Snapshot item = fixture(env, "Runes", 2_000_000, 5_000);
            assertEquals(10_000_000_000L, item.totalValue);
            pickup.when(GroundItemPickup::snapshot).thenReturn(List.of(item));
            pickup.when(() -> GroundItemPickup.take(item, 0)).thenReturn(GroundItemPickup.Result.GROUND_CHANGED);
            inventory.when(Rs2Inventory::emptySlotCount).thenReturn(28);
            LootingParameters params = new LootingParameters(1, Integer.MAX_VALUE, 10, 1, 0, false, false);
            Rs2LootEngine.Builder plan = Rs2LootEngine.with(params).addByValue();
            assertFalse(plan.loot());
            params.setMaxValue(0);
            assertTrue(plan.loot());
            assertEquals(GroundItemPickup.Result.GROUND_CHANGED, plan.getLastResult());
        }
    }

    private GroundItemPickup.Snapshot fixture(ApiTestClient env, String name, int quantity, int price) throws Exception {
        Player player = mock(Player.class);
        when(env.client.getLocalPlayer()).thenReturn(player);
        WorldPoint point = new WorldPoint(3200, 3200, 0);
        when(player.getWorldLocation()).thenReturn(point);
        ItemManager prices = mock(ItemManager.class);
        when(prices.getItemPrice(100)).thenReturn(price);
        env.install("itemManager", prices);
        GroundItem item = GroundItem.builder().id(100).quantity(quantity).name(name).location(point).build();
        return env.call(() -> new GroundItemPickup.Snapshot(mock(Rs2TileItemModel.class), item, mock(WorldView.class), 100));
    }
}
