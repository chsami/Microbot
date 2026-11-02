package net.runelite.client.plugins.microbot.util.inventory;

import net.runelite.api.Client;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.shop.Rs2Shop;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.event.Level;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

public class Rs2InventoryInvokeMenuTest {

    private static final int SHOP_INVENTORY_ITEM_CONTAINER = 19726336;
    private static final int DESTROY_CONFIRM_ID = 38273025;

    @Test
    public void sellItemFallsBackToItemActionsWhenNoContainerChildMatchesSlot() {
        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.runOnClientThreadOptional(any())).thenAnswer(invocation ->
                Optional.ofNullable(((Callable<?>) invocation.getArgument(0)).call()));

        Widget otherSlot = mock(Widget.class);
        when(otherSlot.getIndex()).thenReturn(0);
        Widget container = mock(Widget.class);
        when(container.getChildren()).thenReturn(new Widget[]{otherSlot, null});

        Rs2ItemModel item = mock(Rs2ItemModel.class);
        when(item.getName()).thenReturn("Pot");
        when(item.getId()).thenReturn(1931);
        when(item.getSlot()).thenReturn(5);
        when(item.getInventoryActions()).thenReturn(new String[]{"Value", "Sell 1", "Sell 5", "Sell 10", "Sell 50"});

        try (MockedStatic<Rs2Inventory> inventory = Mockito.mockStatic(Rs2Inventory.class, Answers.CALLS_REAL_METHODS);
             MockedStatic<Microbot> microbot = Mockito.mockStatic(Microbot.class);
             MockedStatic<Rs2Widget> widgets = Mockito.mockStatic(Rs2Widget.class);
             MockedStatic<Rs2Tab> tabs = Mockito.mockStatic(Rs2Tab.class);
             MockedStatic<Rs2Bank> bank = Mockito.mockStatic(Rs2Bank.class);
             MockedStatic<Rs2GrandExchange> ge = Mockito.mockStatic(Rs2GrandExchange.class);
             MockedStatic<Rs2Shop> shop = Mockito.mockStatic(Rs2Shop.class))
        {
            microbot.when(Microbot::getClientThread).thenReturn(clientThread);
            shop.when(Rs2Shop::isOpen).thenReturn(true);
            widgets.when(() -> Rs2Widget.getWidget(SHOP_INVENTORY_ITEM_CONTAINER)).thenReturn(container);
            inventory.when(() -> Rs2Inventory.get("Pot", true)).thenReturn(item);
            inventory.when(Rs2Inventory::isItemSelected).thenReturn(false);
            inventory.when(() -> Rs2Inventory.itemBounds(item)).thenReturn(null);

            assertTrue(Rs2Inventory.sellItem("Pot", "1"));

            microbot.verify(() -> Microbot.doInvoke(argThat(entry ->
                    entry.getIdentifier() == 2
                            && entry.getParam0() == 5
                            && entry.getParam1() == SHOP_INVENTORY_ITEM_CONTAINER), any()));
        }
    }

    @Test
    public void destroyOnClientThreadReturnsWithoutTouchingMissingConfirmWidget() {
        Client client = mock(Client.class);
        when(client.isClientThread()).thenReturn(true);
        Rs2ItemModel item = destroyableItem();

        try (MockedStatic<Rs2Inventory> inventory = Mockito.mockStatic(Rs2Inventory.class, Answers.CALLS_REAL_METHODS);
             MockedStatic<Microbot> microbot = Mockito.mockStatic(Microbot.class);
             MockedStatic<Rs2Widget> widgets = Mockito.mockStatic(Rs2Widget.class);
             MockedStatic<Rs2Tab> tabs = Mockito.mockStatic(Rs2Tab.class);
             MockedStatic<Rs2Bank> bank = Mockito.mockStatic(Rs2Bank.class);
             MockedStatic<Rs2GrandExchange> ge = Mockito.mockStatic(Rs2GrandExchange.class);
             MockedStatic<Rs2Shop> shop = Mockito.mockStatic(Rs2Shop.class))
        {
            stubDestroyPath(microbot, widgets, inventory, item);
            microbot.when(Microbot::getClient).thenReturn(client);

            assertFalse(Rs2Inventory.interact(item, "Destroy"));

            microbot.verify(() -> Microbot.doInvoke(argThat(entry -> entry.getIdentifier() == 2), any()));
            microbot.verify(() -> Microbot.log(eq("Could not confirm destroying Bonds: the destroy confirmation did not appear in time. If it is open, click Yes. If not, right-click Bonds and choose Destroy yourself."), eq(Level.WARN)));
            assertEquals("Destroy Bonds not confirmed", Microbot.status);
            widgets.verify(() -> Rs2Widget.getWidget(584, 1), never());
            widgets.verify(() -> Rs2Widget.clickWidget(anyInt()), never());
        }
    }

    @Test
    public void destroyReturnsWhenConfirmWidgetIsMissingAfterWait() {
        Rs2ItemModel item = destroyableItem();

        try (MockedStatic<Rs2Inventory> inventory = Mockito.mockStatic(Rs2Inventory.class, Answers.CALLS_REAL_METHODS);
             MockedStatic<Microbot> microbot = Mockito.mockStatic(Microbot.class);
             MockedStatic<Global> global = Mockito.mockStatic(Global.class);
             MockedStatic<Rs2Widget> widgets = Mockito.mockStatic(Rs2Widget.class);
             MockedStatic<Rs2Tab> tabs = Mockito.mockStatic(Rs2Tab.class);
             MockedStatic<Rs2Bank> bank = Mockito.mockStatic(Rs2Bank.class);
             MockedStatic<Rs2GrandExchange> ge = Mockito.mockStatic(Rs2GrandExchange.class);
             MockedStatic<Rs2Shop> shop = Mockito.mockStatic(Rs2Shop.class))
        {
            stubDestroyPath(microbot, widgets, inventory, item);
            global.when(() -> Global.sleepUntil(any(BooleanSupplier.class))).thenReturn(true);
            widgets.when(() -> Rs2Widget.getWidget(584, 1)).thenReturn(null);

            assertFalse(Rs2Inventory.interact(item, "Destroy"));

            microbot.verify(() -> Microbot.log(eq("Could not confirm destroying Bonds: the Yes button was not found. Click Yes on the dialog yourself."), eq(Level.WARN)));
            assertEquals("Destroy Bonds not confirmed", Microbot.status);
            widgets.verify(() -> Rs2Widget.clickWidget(anyInt()), never());
        }
    }

    @Test
    public void destroyClicksConfirmWidgetWhenDialogOpens() {
        Rs2ItemModel item = destroyableItem();
        Widget confirm = mock(Widget.class);
        when(confirm.getId()).thenReturn(DESTROY_CONFIRM_ID);

        try (MockedStatic<Rs2Inventory> inventory = Mockito.mockStatic(Rs2Inventory.class, Answers.CALLS_REAL_METHODS);
             MockedStatic<Microbot> microbot = Mockito.mockStatic(Microbot.class);
             MockedStatic<Global> global = Mockito.mockStatic(Global.class);
             MockedStatic<Rs2Widget> widgets = Mockito.mockStatic(Rs2Widget.class);
             MockedStatic<Rs2Tab> tabs = Mockito.mockStatic(Rs2Tab.class);
             MockedStatic<Rs2Bank> bank = Mockito.mockStatic(Rs2Bank.class);
             MockedStatic<Rs2GrandExchange> ge = Mockito.mockStatic(Rs2GrandExchange.class);
             MockedStatic<Rs2Shop> shop = Mockito.mockStatic(Rs2Shop.class))
        {
            stubDestroyPath(microbot, widgets, inventory, item);
            global.when(() -> Global.sleepUntil(any(BooleanSupplier.class))).thenReturn(true);
            widgets.when(() -> Rs2Widget.getWidget(584, 1)).thenReturn(confirm);

            assertTrue(Rs2Inventory.interact(item, "Destroy"));

            widgets.verify(() -> Rs2Widget.clickWidget(DESTROY_CONFIRM_ID));
            microbot.verify(() -> Microbot.log(anyString(), eq(Level.WARN)), never());
        }
    }

    private static Rs2ItemModel destroyableItem() {
        Rs2ItemModel item = mock(Rs2ItemModel.class);
        when(item.getName()).thenReturn("Bonds");
        when(item.getId()).thenReturn(13190);
        when(item.getSlot()).thenReturn(3);
        when(item.getInventoryActions()).thenReturn(new String[]{"Redeem", "Destroy"});
        return item;
    }

    private static void stubDestroyPath(MockedStatic<Microbot> microbot, MockedStatic<Rs2Widget> widgets,
                                        MockedStatic<Rs2Inventory> inventory, Rs2ItemModel item) {
        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.runOnClientThreadOptional(any())).thenAnswer(invocation ->
                Optional.ofNullable(((Callable<?>) invocation.getArgument(0)).call()));
        Widget container = mock(Widget.class);
        when(container.getChildren()).thenReturn(new Widget[0]);

        microbot.when(Microbot::getClientThread).thenReturn(clientThread);
        widgets.when(() -> Rs2Widget.getWidget(ComponentID.INVENTORY_CONTAINER)).thenReturn(container);
        inventory.when(Rs2Inventory::isItemSelected).thenReturn(false);
        inventory.when(() -> Rs2Inventory.itemBounds(item)).thenReturn(null);
    }
}
