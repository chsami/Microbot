package net.runelite.client.plugins.microbot.util.inventory;

import java.awt.Rectangle;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.ApiTestClient;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class InventoryDispatchRegressionTest
{
    @Test
    public void missingInterfaceSlotChangedItemAndUnknownActionDoNotDispatch() throws Exception
    {
        try (ApiTestClient env = new ApiTestClient();
             MockedStatic<Rs2Tab> tabs = mockStatic(Rs2Tab.class);
             MockedStatic<Microbot> api = mockStatic(Microbot.class, CALLS_REAL_METHODS)) {
            AtomicInteger submissions = new AtomicInteger();
            api.when(() -> Microbot.tryDoInvoke(any(), any())).thenAnswer(i -> { submissions.incrementAndGet(); return true; });
            Rs2ItemModel item = mock(Rs2ItemModel.class);
            when(item.getSlot()).thenReturn(2);
            when(item.getId()).thenReturn(100);
            when(item.getName()).thenReturn("Test item");
            when(item.getInventoryActions()).thenReturn(new String[]{"Eat"});
            assertFalse(Rs2Inventory.interact(item, "Eat"));
            Widget container = mock(Widget.class);
            when(container.getId()).thenReturn(ComponentID.INVENTORY_CONTAINER);
            when(env.client.getWidget(ComponentID.INVENTORY_CONTAINER)).thenReturn(container);
            Widget child = mock(Widget.class);
            when(child.getIndex()).thenReturn(1);
            when(child.getItemId()).thenReturn(100);
            when(container.getChildren()).thenAnswer(i -> { env.requireClientThread(); return new Widget[]{child}; });
            assertFalse(Rs2Inventory.interact(item, "Eat"));
            when(child.getIndex()).thenReturn(2);
            when(child.getItemId()).thenReturn(101);
            assertFalse(Rs2Inventory.interact(item, "Eat"));
            when(child.getItemId()).thenReturn(100);
            when(child.getActions()).thenAnswer(i -> { env.requireClientThread(); return new String[]{"Eat"}; });
            when(child.getBounds()).thenAnswer(i -> { env.requireClientThread(); return new Rectangle(10, 10, 30, 30); });
            assertFalse(Rs2Inventory.interact(item, "Teleport"));
            assertEquals(0, submissions.get());
            assertTrue(Rs2Inventory.interact(item, "Eat"));
            assertEquals(1, submissions.get());
            api.when(() -> Microbot.tryDoInvoke(any(), any())).thenReturn(false);
            assertFalse(Rs2Inventory.interact(item, "Eat"));
        }
    }

    @Test
    public void widgetReplacementBetweenCaptureAndSubmissionIsRejected() throws Exception
    {
        try (ApiTestClient env = new ApiTestClient(); MockedStatic<Rs2Tab> tabs = mockStatic(Rs2Tab.class)) {
            Rs2ItemModel item = mock(Rs2ItemModel.class);
            when(item.getSlot()).thenReturn(0);
            when(item.getId()).thenReturn(100);
            Widget container = mock(Widget.class);
            Widget child = mock(Widget.class);
            when(container.getId()).thenReturn(ComponentID.INVENTORY_CONTAINER);
            when(child.getItemId()).thenReturn(100);
            when(child.getActions()).thenReturn(new String[]{"Eat"});
            when(child.getBounds()).thenReturn(new Rectangle(10, 10, 30, 30));
            when(container.getChildren()).thenReturn(new Widget[]{child});
            AtomicInteger lookups = new AtomicInteger();
            when(env.client.getWidget(ComponentID.INVENTORY_CONTAINER)).thenAnswer(i ->
                    lookups.incrementAndGet() == 1 ? container : mock(Widget.class));
            assertFalse(Rs2Inventory.interact(item, "Eat"));
            assertEquals(2, lookups.get());
        }
    }
}
