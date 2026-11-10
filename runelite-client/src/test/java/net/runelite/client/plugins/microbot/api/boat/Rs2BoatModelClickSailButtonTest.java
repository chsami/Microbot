package net.runelite.client.plugins.microbot.api.boat;

import net.runelite.api.WorldEntity;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.api.boat.models.Rs2BoatModel;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

public class Rs2BoatModelClickSailButtonTest {
    private MockedStatic<Rs2Widget> widgets;
    private Rs2BoatModel boat;

    @Before
    public void setUp() {
        widgets = mockStatic(Rs2Widget.class);
        boat = new Rs2BoatModel(mock(WorldEntity.class));
    }

    @After
    public void tearDown() {
        widgets.close();
    }

    private void facilitiesRows(Widget rows) {
        widgets.when(() -> Rs2Widget.getWidget(InterfaceID.SailingSidepanel.FACILITIES_ROWS)).thenReturn(rows);
    }

    @Test
    public void returnsFalseWhenFacilitiesRowsHaveNoDynamicChildren() {
        Widget rows = mock(Widget.class);
        when(rows.getDynamicChildren()).thenReturn(new Widget[0]);
        facilitiesRows(rows);

        assertFalse(boat.clickSailButton());
        widgets.verify(() -> Rs2Widget.clickWidget(any(Widget.class)), never());
    }

    @Test
    public void returnsFalseWhenDynamicChildrenAreNull() {
        Widget rows = mock(Widget.class);
        when(rows.getDynamicChildren()).thenReturn(null);
        facilitiesRows(rows);

        assertFalse(boat.clickSailButton());
        widgets.verify(() -> Rs2Widget.clickWidget(any(Widget.class)), never());
    }

    @Test
    public void returnsFalseWhenFacilitiesRowsWidgetIsMissing() {
        facilitiesRows(null);

        assertFalse(boat.clickSailButton());
        widgets.verify(() -> Rs2Widget.clickWidget(any(Widget.class)), never());
    }

    @Test
    public void clicksFirstRowWhenPopulated() {
        Widget rows = mock(Widget.class);
        Widget sailButton = mock(Widget.class);
        when(rows.getDynamicChildren()).thenReturn(new Widget[]{sailButton, mock(Widget.class)});
        facilitiesRows(rows);
        widgets.when(() -> Rs2Widget.clickWidget(sailButton)).thenReturn(true);

        assertTrue(boat.clickSailButton());
        widgets.verify(() -> Rs2Widget.clickWidget(sailButton));
    }
}
