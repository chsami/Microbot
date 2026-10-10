package net.runelite.client.plugins.microbot.accountselector;

import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TerminalSupplyLogoutTest {
    @Test public void newScriptDuringBankCloseStaysUnblockedAndLogoutIsObserved() {
        AtomicBoolean loggedIn = new AtomicBoolean(true);
        AtomicBoolean blocked = new AtomicBoolean();
        Client client = mock(Client.class);
        EventBus bus = mock(EventBus.class);
        doAnswer(call -> { blocked.set(true); return null; }).when(bus).post(any(AutoLoginSuppressionRequest.class));
        try (var microbot = mockStatic(Microbot.class);
             var bank = mockStatic(Rs2Bank.class);
             var widget = mockStatic(Rs2Widget.class);
             var player = mockStatic(Rs2Player.class, CALLS_REAL_METHODS)) {
            microbot.when(Microbot::getClient).thenReturn(client);
            microbot.when(Microbot::getEventBus).thenReturn(bus);
            microbot.when(Microbot::isLoggedIn).thenAnswer(call -> loggedIn.get());
            bank.when(Rs2Bank::isOpen).thenReturn(true, false);
            widget.when(() -> Rs2Widget.clickChildWidget(786434, 11)).thenAnswer(call -> {
                blocked.set(false); // another script starts while the previous bank is closing
                return true;
            });
            player.when(Rs2Player::logout).thenAnswer(call -> { loggedIn.set(false); return null; });
            assertEquals(Rs2Player.TerminalLogoutState.LOGGED_OUT, Rs2Player.logoutWithoutAutoLogin());
            assertFalse(blocked.get());
            verify(bus, times(1)).post(any(AutoLoginSuppressionRequest.class));
            player.verify(Rs2Player::logout, times(1));
        }
    }

    @Test public void blockedBankCloseDoesNotClaimLogoutOrDispatchLogout() {
        Client client = mock(Client.class);
        EventBus bus = mock(EventBus.class);
        try (var microbot = mockStatic(Microbot.class);
             var bank = mockStatic(Rs2Bank.class);
             var widget = mockStatic(Rs2Widget.class);
             var player = mockStatic(Rs2Player.class, CALLS_REAL_METHODS)) {
            microbot.when(Microbot::getClient).thenReturn(client);
            microbot.when(Microbot::getEventBus).thenReturn(bus);
            microbot.when(Microbot::isLoggedIn).thenReturn(true);
            bank.when(Rs2Bank::isOpen).thenReturn(true);
            assertEquals(Rs2Player.TerminalLogoutState.INPUT_BLOCKED, Rs2Player.logoutWithoutAutoLogin());
            verify(bus).post(any(AutoLoginSuppressionRequest.class));
            player.verify(Rs2Player::logout, never());
        }
    }
}
