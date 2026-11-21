package net.runelite.client.plugins.microbot.util.grounditem;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.grounditems.GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;

public class Rs2LootEngineNullPlayerLocationTest {

    private static final WorldPoint PLAYER = new WorldPoint(3200, 3200, 0);

    private MockedStatic<Rs2Player> rs2Player;
    private MockedStatic<Rs2GroundItem> rs2GroundItem;
    private MockedStatic<Rs2Inventory> rs2Inventory;
    private final List<GroundItem> looted = new ArrayList<>();

    @Before
    public void setUp() {
        GroundItem item = GroundItem.builder()
                .id(526)
                .name("Bones")
                .quantity(1)
                .gePrice(100)
                .location(new WorldPoint(3201, 3200, 0))
                .build();
        Table<WorldPoint, Integer, GroundItem> table = HashBasedTable.create();
        table.put(item.getLocation(), item.getId(), item);

        rs2Player = Mockito.mockStatic(Rs2Player.class);
        rs2GroundItem = Mockito.mockStatic(Rs2GroundItem.class);
        rs2GroundItem.when(Rs2GroundItem::getGroundItems).thenReturn(table);
        rs2GroundItem.when(() -> Rs2GroundItem.runWhilePaused(any()))
                .thenAnswer(inv -> inv.<BooleanSupplier>getArgument(0).getAsBoolean());
        rs2Inventory = Mockito.mockStatic(Rs2Inventory.class);
        rs2Inventory.when(Rs2Inventory::emptySlotCount).thenReturn(28);
    }

    @After
    public void tearDown() {
        rs2Inventory.close();
        rs2GroundItem.close();
        rs2Player.close();
    }

    private boolean lootByValue() {
        LootingParameters params = new LootingParameters(10, 1_000_000, 10, 1, 0, false, false);
        return Rs2LootEngine.with(params)
                .withLootAction(looted::add)
                .addByValue()
                .loot();
    }

    @Test
    public void nullPlayerLocationCollectsNothing() {
        rs2Player.when(Rs2Player::getWorldLocation).thenReturn(null);

        lootByValue();

        assertTrue(looted.isEmpty());
    }

    @Test
    public void knownPlayerLocationLootsItemInRange() {
        rs2Player.when(Rs2Player::getWorldLocation).thenReturn(PLAYER);

        lootByValue();

        assertEquals(1, looted.size());
    }
}
