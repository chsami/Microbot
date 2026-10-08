package net.runelite.client.plugins.microbot.util.magic;

import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.awt.Rectangle;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Rs2MagicMissingSpellWidgetTest {

    private MockedStatic<Rs2Widget> widgets;
    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Tab> tabs;
    private MockedStatic<Rs2Magic> magic;

    @Before
    public void setUp() {
        widgets = Mockito.mockStatic(Rs2Widget.class);
        microbot = Mockito.mockStatic(Microbot.class);
        microbot.when(Microbot::getClient).thenReturn(mock(Client.class));
        tabs = Mockito.mockStatic(Rs2Tab.class);
        magic = Mockito.mockStatic(Rs2Magic.class, Mockito.CALLS_REAL_METHODS);
        magic.when(() -> Rs2Magic.canCast(any(MagicAction.class))).thenReturn(true);
    }

    @After
    public void tearDown() {
        magic.close();
        tabs.close();
        microbot.close();
        widgets.close();
    }

    private void spellbookWithoutMatch() {
        Widget root = mock(Widget.class);
        when(root.getStaticChildren()).thenReturn(new Widget[]{mock(Widget.class)});
        widgets.when(() -> Rs2Widget.getWidget(218, 0)).thenReturn(root);
        widgets.when(() -> Rs2Widget.findWidget(anyString(), anyList())).thenReturn(null);
    }

    @Test
    public void widgetLookupReturnsSentinelsWhenSpellWidgetMissing() {
        spellbookWithoutMatch();
        assertEquals(-1, MagicAction.VARROCK_TELEPORT.getWidgetId());
        assertNull(MagicAction.VARROCK_TELEPORT.getActions());
    }

    @Test
    public void widgetLookupReturnsSentinelsWhenSpellbookNotLoaded() {
        widgets.when(() -> Rs2Widget.getWidget(218, 0)).thenReturn(null);
        assertEquals(-1, MagicAction.VARROCK_TELEPORT.getWidgetId());
        assertNull(MagicAction.VARROCK_TELEPORT.getActions());
    }

    @Test
    public void castFailsInsteadOfThrowingWhenSpellWidgetMissing() {
        spellbookWithoutMatch();
        assertFalse(Rs2Magic.cast(MagicAction.VARROCK_TELEPORT, "cast", 1));
        microbot.verify(() -> Microbot.doInvoke(any(), any(Rectangle.class)), Mockito.never());
    }

    @Test
    public void castFailsWhenResolvedWidgetDisappears() {
        Widget root = mock(Widget.class);
        Widget spell = mock(Widget.class);
        when(spell.getId()).thenReturn(14286870);
        when(root.getStaticChildren()).thenReturn(new Widget[]{spell});
        widgets.when(() -> Rs2Widget.getWidget(218, 0)).thenReturn(root);
        widgets.when(() -> Rs2Widget.findWidget(eq("Varrock Teleport"), anyList())).thenReturn(spell);
        widgets.when(() -> Rs2Widget.getWidget(anyInt())).thenReturn(null);
        assertFalse(Rs2Magic.cast(MagicAction.VARROCK_TELEPORT, "cast", 1));
        microbot.verify(() -> Microbot.doInvoke(any(), any(Rectangle.class)), Mockito.never());
    }
}
