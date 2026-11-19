package net.runelite.client.plugins.microbot.util.magic;

import net.runelite.api.Client;
import net.runelite.api.SpriteID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
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
import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Rs2MagicSuperHeatSpellWidgetTest {

    private static final Rectangle SPELL_BOUNDS = new Rectangle(600, 300, 24, 24);
    private static final Rectangle CHAT_BOUNDS = new Rectangle(10, 400, 480, 14);

    private MockedStatic<Rs2Widget> widgets;
    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Tab> tabs;
    private MockedStatic<Rs2Inventory> inventory;
    private Widget spellbook;

    @Before
    public void setUp() {
        Rs2Magic.lastSuperHeatFailure = null;
        widgets = Mockito.mockStatic(Rs2Widget.class);
        microbot = Mockito.mockStatic(Microbot.class);
        microbot.when(Microbot::getClient).thenReturn(mock(Client.class));
        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.runOnClientThreadOptional(any())).thenReturn(Optional.of(true));
        microbot.when(Microbot::getClientThread).thenReturn(clientThread);
        tabs = Mockito.mockStatic(Rs2Tab.class);
        tabs.when(Rs2Tab::getCurrentTab).thenReturn(InterfaceTab.MAGIC);
        inventory = Mockito.mockStatic(Rs2Inventory.class);

        Widget chatLine = mock(Widget.class);
        when(chatLine.getSpriteId()).thenReturn(-1);
        when(chatLine.getBounds()).thenReturn(CHAT_BOUNDS);
        widgets.when(() -> Rs2Widget.findWidget(anyString())).thenReturn(chatLine);

        spellbook = mock(Widget.class);
        widgets.when(() -> Rs2Widget.getWidget(218, 3)).thenReturn(spellbook);
    }

    @After
    public void tearDown() {
        inventory.close();
        tabs.close();
        microbot.close();
        widgets.close();
    }

    private void spellInSpellbook(int sprite) {
        Widget spell = mock(Widget.class);
        when(spell.getSpriteId()).thenReturn(sprite);
        when(spell.getBounds()).thenReturn(SPELL_BOUNDS);
        widgets.when(() -> Rs2Widget.findWidget("Superheat Item", List.of(spellbook))).thenReturn(spell);
    }

    @Test
    public void superHeatClicksTheSpellbookWidgetWhenAChatLineMatchesTheSpellName() {
        spellInSpellbook(SpriteID.SPELL_SUPERHEAT_ITEM);
        Rs2ItemModel ore = mock(Rs2ItemModel.class);

        Rs2Magic.superHeat(ore, 0, 0);

        microbot.verify(() -> Microbot.click(SPELL_BOUNDS));
        microbot.verify(() -> Microbot.click(CHAT_BOUNDS), Mockito.never());
        inventory.verify(() -> Rs2Inventory.interact(ore, "cast"));
    }

    @Test
    public void quickCastClicksTheSpellbookWidgetWhenAChatLineMatchesTheSpellName() {
        spellInSpellbook(SpriteID.SPELL_SUPERHEAT_ITEM);

        assertTrue(Rs2Magic.quickCast(MagicAction.SUPERHEAT_ITEM));

        microbot.verify(() -> Microbot.click(SPELL_BOUNDS));
        microbot.verify(() -> Microbot.click(CHAT_BOUNDS), Mockito.never());
    }

    @Test
    public void superHeatLogsWhenTheSpellIsNotInTheSpellbook() {
        Rs2ItemModel ore = mock(Rs2ItemModel.class);

        Rs2Magic.superHeat(ore, 0, 0);

        microbot.verify(() -> Microbot.log(contains("not visible in your spellbook")));
        microbot.verify(() -> Microbot.click(any(Rectangle.class)), Mockito.never());
        inventory.verify(() -> Rs2Inventory.interact(any(Rs2ItemModel.class), anyString()), Mockito.never());
    }

    @Test
    public void superHeatLogsARepeatedFailureOnceAndANewReasonAgain() {
        Rs2ItemModel ore = mock(Rs2ItemModel.class);

        Rs2Magic.superHeat(ore, 0, 0);
        Rs2Magic.superHeat(ore, 0, 0);
        Rs2Magic.superHeat(ore, 0, 0);

        microbot.verify(() -> Microbot.log(contains("not visible in your spellbook")), Mockito.times(1));

        spellInSpellbook(SpriteID.SPELL_SUPERHEAT_ITEM_DISABLED);
        Rs2Magic.superHeat(ore, 0, 0);
        Rs2Magic.superHeat(ore, 0, 0);

        microbot.verify(() -> Microbot.log(contains("greyed out")), Mockito.times(1));
    }

    @Test
    public void superHeatLogsWhenTheSpellIsDisabled() {
        spellInSpellbook(SpriteID.SPELL_SUPERHEAT_ITEM_DISABLED);
        Rs2ItemModel ore = mock(Rs2ItemModel.class);

        Rs2Magic.superHeat(ore, 0, 0);

        microbot.verify(() -> Microbot.log(contains("greyed out")));
        microbot.verify(() -> Microbot.click(any(Rectangle.class)), Mockito.never());
    }

    @Test
    public void quickCastFailsWhenTheSpellIsNotInTheSpellbook() {
        assertFalse(Rs2Magic.quickCast(MagicAction.SUPERHEAT_ITEM));

        microbot.verify(() -> Microbot.click(any(Rectangle.class)), Mockito.never());
    }
}
