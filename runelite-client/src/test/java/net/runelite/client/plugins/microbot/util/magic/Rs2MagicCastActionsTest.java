package net.runelite.client.plugins.microbot.util.magic;

import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.awt.Rectangle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Rs2MagicCastActionsTest {

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

    @Test
    public void castReadsSpellActionsOnceWhenTheyVanishBetweenReads() {
        Widget root = mock(Widget.class);
        Widget spell = mock(Widget.class);
        when(spell.getId()).thenReturn(14286892);
        when(spell.getBounds()).thenReturn(new Rectangle(0, 0, 10, 10));
        when(spell.getActions()).thenReturn(new String[]{"Cast"}).thenReturn(null);
        when(root.getStaticChildren()).thenReturn(new Widget[]{spell});
        widgets.when(() -> Rs2Widget.getWidget(218, 0)).thenReturn(root);
        widgets.when(() -> Rs2Widget.findWidget(eq("High Level Alchemy"), anyList())).thenReturn(spell);
        widgets.when(() -> Rs2Widget.getWidget(anyInt())).thenReturn(spell);

        assertTrue(Rs2Magic.cast(MagicAction.HIGH_LEVEL_ALCHEMY, "cast", 1));

        ArgumentCaptor<NewMenuEntry> entry = ArgumentCaptor.forClass(NewMenuEntry.class);
        microbot.verify(() -> Microbot.doInvoke(entry.capture(), any(Rectangle.class)));
        assertEquals(MenuAction.CC_OP, entry.getValue().getType());
        assertEquals(14286892, entry.getValue().getParam1());
    }
}
