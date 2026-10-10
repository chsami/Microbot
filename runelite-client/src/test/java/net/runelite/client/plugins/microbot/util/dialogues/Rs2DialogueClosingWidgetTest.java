package net.runelite.client.plugins.microbot.util.dialogues;

import net.runelite.api.Client;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Rs2DialogueClosingWidgetTest {

    private MockedStatic<Rs2Widget> widgets;
    private MockedStatic<Microbot> microbot;
    private Client client;

    @Before
    public void setUp() {
        widgets = Mockito.mockStatic(Rs2Widget.class);
        widgets.when(() -> Rs2Widget.isWidgetVisible(InterfaceID.DIALOG_OPTION, 1)).thenReturn(true);

        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.runOnClientThreadOptional(any())).thenAnswer(invocation -> {
            Callable<?> callable = invocation.getArgument(0);
            return Optional.ofNullable(callable.call());
        });
        client = mock(Client.class);
        microbot = Mockito.mockStatic(Microbot.class);
        microbot.when(Microbot::getClientThread).thenReturn(clientThread);
        microbot.when(Microbot::getClient).thenReturn(client);
    }

    @After
    public void tearDown() {
        microbot.close();
        widgets.close();
    }

    private void dialogOpenAtCheck(Widget atCheck, Widget atFetch) {
        widgets.when(() -> Rs2Widget.getWidget(InterfaceID.DIALOG_OPTION, 1)).thenReturn(atCheck);
        when(client.getWidget(InterfaceID.DIALOG_OPTION, 1)).thenReturn(atFetch);
    }

    private void dialogOpen(Widget container) {
        dialogOpenAtCheck(container, container);
    }

    private static Widget textWidget(String text) {
        Widget widget = mock(Widget.class);
        when(widget.getText()).thenReturn(text);
        return widget;
    }

    private static Widget optionContainer(Widget... children) {
        Widget container = mock(Widget.class);
        when(container.getDynamicChildren()).thenReturn(children);
        return container;
    }

    @Test
    public void getQuestionReturnsNullWhenDialogClosesAfterCheck() {
        Widget open = optionContainer(textWidget("Pay 200 coins?"));
        dialogOpenAtCheck(open, null);

        assertNull(Rs2Dialogue.getQuestion());
    }

    @Test
    public void hasQuestionReturnsFalseWhenDialogClosesAfterCheck() {
        Widget open = optionContainer(textWidget("Pay 200 coins?"));
        dialogOpenAtCheck(open, null);

        assertFalse(Rs2Dialogue.hasQuestion("pay"));
    }

    @Test
    public void getQuestionReturnsNullWhenChildrenAreEmptyOrNull() {
        Widget empty = optionContainer();
        dialogOpen(empty);
        assertNull(Rs2Dialogue.getQuestion());

        Widget nullFirst = optionContainer((Widget) null);
        dialogOpen(nullFirst);
        assertNull(Rs2Dialogue.getQuestion());

        Widget nullText = optionContainer(textWidget(null));
        dialogOpen(nullText);
        assertNull(Rs2Dialogue.getQuestion());
    }

    @Test
    public void getQuestionStripsColourTags() {
        Widget open = optionContainer(textWidget("<col=800000>Pay 200 coins?</col>"));
        dialogOpen(open);

        assertEquals("Pay 200 coins?", Rs2Dialogue.getQuestion());
        assertTrue(Rs2Dialogue.hasQuestion("200 coins"));
    }

    @Test
    public void getDialogueOptionsSkipsMissingChildrenAndText() {
        Widget yes = textWidget("Yes");
        Widget container = optionContainer(textWidget("Pay?"), null, textWidget(null), textWidget(" "), yes);
        dialogOpen(container);

        List<Widget> options = Rs2Dialogue.getDialogueOptions();
        assertEquals(1, options.size());
        assertEquals(yes, options.get(0));
    }

    @Test
    public void getDialogueOptionsReturnsEmptyWhenDialogClosesAfterCheck() {
        Widget open = optionContainer(textWidget("Pay?"), textWidget("Yes"));
        dialogOpenAtCheck(open, null);

        assertTrue(Rs2Dialogue.getDialogueOptions().isEmpty());
    }

    @Test
    public void hasDialogueOptionTitleReturnsFalseWhenTitleTextMissing() {
        Widget container = optionContainer(textWidget(null), textWidget("Yes"));
        dialogOpen(container);

        assertFalse(Rs2Dialogue.hasDialogueOptionTitle("Pay"));
    }
}
