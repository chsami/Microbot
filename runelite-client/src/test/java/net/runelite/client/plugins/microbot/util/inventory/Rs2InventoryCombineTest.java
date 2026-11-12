package net.runelite.client.plugins.microbot.util.inventory;

import net.runelite.client.plugins.microbot.Microbot;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.stream.Stream;

import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

public class Rs2InventoryCombineTest {

    private static final int HAMMER = 2347;
    private static final int BAR = 2349;

    @Test
    public void combineByIdReturnsFalseWithoutSelectingWhenSecondaryItemIsMissing() {
        Rs2ItemModel hammer = item(HAMMER, "Hammer", 0);

        try (MockedStatic<Rs2Inventory> inventory = Mockito.mockStatic(Rs2Inventory.class, Answers.CALLS_REAL_METHODS);
             MockedStatic<Microbot> microbot = Mockito.mockStatic(Microbot.class))
        {
            inventory.when(Rs2Inventory::items).thenAnswer(invocation -> Stream.of(hammer));

            assertFalse(Rs2Inventory.combine(HAMMER, BAR));

            inventory.verify(() -> Rs2Inventory.use(any(Rs2ItemModel.class)), never());
        }
    }

    @Test
    public void combineByIdReturnsFalseWhenPrimaryItemIsMissing() {
        Rs2ItemModel hammer = item(HAMMER, "Hammer", 0);

        try (MockedStatic<Rs2Inventory> inventory = Mockito.mockStatic(Rs2Inventory.class, Answers.CALLS_REAL_METHODS);
             MockedStatic<Microbot> microbot = Mockito.mockStatic(Microbot.class))
        {
            inventory.when(Rs2Inventory::items).thenAnswer(invocation -> Stream.of(hammer));

            assertFalse(Rs2Inventory.combine(BAR, HAMMER));

            inventory.verify(() -> Rs2Inventory.use(any(Rs2ItemModel.class)), never());
        }
    }

    @Test
    public void combineByNameReturnsFalseWithoutSelectingWhenSecondaryItemIsMissing() {
        Rs2ItemModel hammer = item(HAMMER, "Hammer", 0);

        try (MockedStatic<Rs2Inventory> inventory = Mockito.mockStatic(Rs2Inventory.class, Answers.CALLS_REAL_METHODS);
             MockedStatic<Microbot> microbot = Mockito.mockStatic(Microbot.class))
        {
            inventory.when(Rs2Inventory::items).thenAnswer(invocation -> Stream.of(hammer));

            assertFalse(Rs2Inventory.combine("Hammer", "Bronze bar"));

            inventory.verify(() -> Rs2Inventory.use(any(Rs2ItemModel.class)), never());
        }
    }

    private static Rs2ItemModel item(int id, String name, int slot) {
        Rs2ItemModel item = mock(Rs2ItemModel.class);
        when(item.getId()).thenReturn(id);
        when(item.getName()).thenReturn(name);
        when(item.getSlot()).thenReturn(slot);
        return item;
    }
}
