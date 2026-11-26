package net.runelite.client.plugins.microbot.ui;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MicrobotProfilePanelBankPinTest {
    @Test
    public void fourDigitsCanBeSaved() {
        assertTrue(MicrobotProfilePanel.canSaveBankPin("0123"));
    }

    @Test
    public void anEmptyFieldKeepsTheSavedPin() {
        assertTrue(MicrobotProfilePanel.canSaveBankPin(""));
    }

    @Test
    public void anythingElseIsRefused() {
        for (String bad : new String[]{"123", "12345", "12a4", "12 34", "12" + (char) 0x0663 + (char) 0x0664}) {
            assertFalse(bad, MicrobotProfilePanel.canSaveBankPin(bad));
        }
    }
}
