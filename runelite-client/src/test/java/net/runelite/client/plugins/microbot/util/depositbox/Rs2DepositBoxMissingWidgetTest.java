package net.runelite.client.plugins.microbot.util.depositbox;

import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

public class Rs2DepositBoxMissingWidgetTest {

    private MockedStatic<Rs2Widget> widgets;
    private MockedStatic<Rs2Inventory> inventory;

    @Before
    public void setUp() {
        widgets = Mockito.mockStatic(Rs2Widget.class);
        widgets.when(Rs2Widget::isDepositBoxWidgetOpen).thenReturn(true);
        inventory = Mockito.mockStatic(Rs2Inventory.class);
        inventory.when(Rs2Inventory::isEmpty).thenReturn(false);
    }

    @After
    public void tearDown() {
        inventory.close();
        widgets.close();
    }

    private void depositBoxWidget(Widget widget) {
        widgets.when(() -> Rs2Widget.getWidget(192, 0)).thenReturn(widget);
    }

    @Test
    public void depositAllReturnsWhenWidgetMissingAfterOpenCheck() {
        depositBoxWidget(null);

        Rs2DepositBox.depositAll();

        widgets.verify(() -> Rs2Widget.findWidget(anyString(), anyList(), anyBoolean()), never());
        widgets.verify(() -> Rs2Widget.clickWidget(any(Widget.class)), never());
    }

    @Test
    public void closeDepositBoxReturnsFalseWhenWidgetMissingAfterOpenCheck() {
        depositBoxWidget(null);

        assertFalse(Rs2DepositBox.closeDepositBox());

        widgets.verify(() -> Rs2Widget.findWidget(anyString(), anyList(), anyBoolean()), never());
    }

    @Test
    public void depositEquipmentReturnsWhenWidgetMissingAfterOpenCheck() {
        depositBoxWidget(null);

        Rs2DepositBox.depositEquipment();

        widgets.verify(() -> Rs2Widget.findWidget(anyString(), anyList(), anyBoolean()), never());
        widgets.verify(() -> Rs2Widget.clickWidget(any(Widget.class)), never());
    }

    @Test
    public void depositAllSearchesTheFetchedWidget() {
        Widget box = mock(Widget.class);
        Widget button = mock(Widget.class);
        depositBoxWidget(box);
        widgets.when(() -> Rs2Widget.findWidget("Deposit Inventory", List.of(box), false)).thenReturn(button);

        Rs2DepositBox.depositAll();

        widgets.verify(() -> Rs2Widget.clickWidget(eq(button)));
    }

    @Test
    public void depositEquipmentSearchesTheFetchedWidget() {
        Widget box = mock(Widget.class);
        Widget button = mock(Widget.class);
        depositBoxWidget(box);
        widgets.when(() -> Rs2Widget.findWidget("Deposit worn items", List.of(box), false)).thenReturn(button);

        Rs2DepositBox.depositEquipment();

        widgets.verify(() -> Rs2Widget.clickWidget(eq(button)));
    }
}
