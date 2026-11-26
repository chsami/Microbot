package net.runelite.client.plugins.microbot.util.bank;

import net.runelite.api.Client;
import net.runelite.api.widgets.ComponentID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;

public class Rs2BankPinEntryTest {

    private static final String[] INSTRUCTIONS = {"FIRST digit", "SECOND digit", "THIRD digit", "FOURTH digit"};

    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Widget> widgets;

    private int position;
    private int missesLeft;
    private boolean instructionShown;
    private final List<String> clicks = new ArrayList<>();
    private final StringBuilder entered = new StringBuilder();

    @Before
    public void setUp() {
        Client client = mock(Client.class);
        microbot = Mockito.mockStatic(Microbot.class);
        microbot.when(Microbot::getClient).thenReturn(client);

        position = 0;
        missesLeft = 0;
        instructionShown = true;

        widgets = Mockito.mockStatic(Rs2Widget.class);
        widgets.when(() -> Rs2Widget.isWidgetVisible(ComponentID.BANK_PIN_CONTAINER))
                .thenAnswer(inv -> position < INSTRUCTIONS.length);
        widgets.when(() -> Rs2Widget.hasWidgetText(anyString(), eq(213), eq(10), eq(false)))
                .thenAnswer(inv -> instructionShown
                        && position < INSTRUCTIONS.length
                        && INSTRUCTIONS[position].equals(inv.getArgument(0)));
        widgets.when(() -> Rs2Widget.clickWidget(anyString(), eq(Optional.of(213)), eq(0), eq(true)))
                .thenAnswer(inv -> {
                    String digit = inv.getArgument(0);
                    clicks.add(digit);
                    if (missesLeft > 0) {
                        missesLeft--;
                        return false;
                    }
                    entered.append(digit);
                    position++;
                    return true;
                });
    }

    @After
    public void tearDown() {
        widgets.close();
        microbot.close();
    }

    @Test
    public void entersAllFourDigitsInOrder() {
        assertTrue(Rs2Bank.handleBankPin("1234"));
        assertEquals("1234", entered.toString());
    }

    @Test
    public void resumesFromTheDigitTheKeypadAsksFor() {
        position = 1;

        assertTrue(Rs2Bank.handleBankPin("1234"));
        assertEquals("234", entered.toString());
    }

    @Test
    public void retriesADigitWhosePressMissed() {
        missesLeft = 1;

        assertTrue(Rs2Bank.handleBankPin("5678"));
        assertEquals("5678", entered.toString());
        assertEquals(5, clicks.size());
    }

    @Test
    public void failsWithoutPressingWhenNoInstructionIsShown() {
        instructionShown = false;

        assertFalse(Rs2Bank.handleBankPin("1234"));
        assertTrue(clicks.isEmpty());
    }
}
