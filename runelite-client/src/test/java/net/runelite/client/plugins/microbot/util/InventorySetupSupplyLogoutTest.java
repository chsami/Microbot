package net.runelite.client.plugins.microbot.util;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsStackCompareID;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class InventorySetupSupplyLogoutTest {
    @Test public void requiredInventoryItemMissingRequestsTerminalLogout() {
        checkMissing(false, true, true, true);
    }

    @Test public void requiredGearMissingRequestsTerminalLogout() {
        checkMissing(true, true, true, true);
    }

    @Test public void bankMirrorNotReadyDoesNotRequestLogout() {
        checkMissing(false, false, false, true);
        checkMissing(true, false, false, true);
    }

    @Test public void existingInventoryCallerPausesWithoutLoggingOutOrCancelling() {
        checkMissing(false, true, false, false);
    }

    @Test public void existingGearCallerPausesWithoutLoggingOutOrCancelling() {
        checkMissing(true, true, false, false);
    }

    @Test public void defaultPolicyDoesNotPauseForAnUnreadyBankMirror() {
        checkMissing(false, false, false, false);
        checkMissing(true, false, false, false);
    }

    private void checkMissing(boolean gear, boolean mirrorReady, boolean expectLogout, boolean optIn) {
        boolean pausedBefore = Microbot.pauseAllScripts.getAndSet(false);
        InventorySetup setup = mock(InventorySetup.class);
        InventorySetupsItem row = new InventorySetupsItem(100, "Required supply", 1, true,
                InventorySetupsStackCompareID.None, false, -1);
        when(setup.getInventory()).thenReturn(gear ? Collections.emptyList() : Collections.singletonList(row));
        when(setup.getEquipment()).thenReturn(gear ? Collections.singletonList(row) : Collections.emptyList());
        Client client = mock(Client.class);
        ClientThread thread = mock(ClientThread.class);
        when(thread.runOnClientThreadOptional(any())).thenReturn(Optional.empty());
        try (var microbot = mockStatic(Microbot.class);
             var bank = mockStatic(Rs2Bank.class);
             var inventory = mockStatic(Rs2Inventory.class);
             var equipment = mockStatic(Rs2Equipment.class);
             var player = mockStatic(Rs2Player.class)) {
            microbot.when(Microbot::getClient).thenReturn(client);
            microbot.when(Microbot::getClientThread).thenReturn(thread);
            bank.when(Rs2Bank::isOpen).thenReturn(true);
            bank.when(() -> Rs2Bank.verifyBankMirrorAfterOpen(true, 0)).thenReturn(mirrorReady);
            inventory.when(Rs2Inventory::items).thenAnswer(call -> Stream.empty());
            inventory.when(() -> Rs2Inventory.items(any(Predicate.class))).thenAnswer(call -> Stream.empty());
            equipment.when(Rs2Equipment::all).thenAnswer(call -> Stream.empty());
            ScheduledFuture<?> scheduler = mock(ScheduledFuture.class);
            Rs2InventorySetup loader = new Rs2InventorySetup(setup, scheduler);
            if (optIn) {
                assertSame(loader, loader.withMissingSupplyPolicy(Rs2InventorySetup.MissingSupplyPolicy.LOGOUT_AND_STOP));
            }
            assertFalse(gear ? loader.loadEquipment(false) : loader.loadInventory(false));
            player.verify(Rs2Player::logoutWithoutAutoLogin, times(expectLogout ? 1 : 0));
            verify(scheduler, times(expectLogout ? 1 : 0)).cancel(false);
            assertEquals(!optIn && mirrorReady, Microbot.pauseAllScripts.get());
        } finally {
            Microbot.pauseAllScripts.set(pausedBefore);
        }
    }
}
