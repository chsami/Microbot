package net.runelite.client.plugins.microbot.util.inventory;

import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.concurrent.Callable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Rs2ItemModelHaPriceTest {

    private static final int ITEM_ID = 1127;

    @Test
    public void unloadedCompositionReturnsZeroHaPriceAndIsNotProfitable() {
        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.runOnClientThreadOptional(any())).thenReturn(Optional.empty());

        try (MockedStatic<Microbot> microbot = Mockito.mockStatic(Microbot.class)) {
            microbot.when(Microbot::getClientThread).thenReturn(clientThread);

            Rs2ItemModel item = Rs2ItemModel.createFromCache(ITEM_ID, 1, 0);

            assertEquals(0, item.getHaPrice());
            assertFalse(item.isHaProfitable());
        }
    }

    @Test
    public void haPriceLoadsCompositionLazily() {
        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.runOnClientThreadOptional(any())).thenAnswer(invocation ->
                Optional.ofNullable(((Callable<?>) invocation.getArgument(0)).call()));

        ItemComposition composition = mock(ItemComposition.class);
        when(composition.getName()).thenReturn("Rune platebody");
        when(composition.getHaPrice()).thenReturn(39000);
        when(composition.getInventoryActions()).thenReturn(new String[0]);
        Client client = mock(Client.class);
        when(client.getItemDefinition(ITEM_ID)).thenReturn(composition);
        ItemManager itemManager = mock(ItemManager.class);
        when(itemManager.getItemPrice(anyInt())).thenReturn(0L);

        try (MockedStatic<Microbot> microbot = Mockito.mockStatic(Microbot.class)) {
            microbot.when(Microbot::getClientThread).thenReturn(clientThread);
            microbot.when(Microbot::getClient).thenReturn(client);
            microbot.when(Microbot::getItemManager).thenReturn(itemManager);

            Rs2ItemModel item = Rs2ItemModel.createFromCache(ITEM_ID, 1, 0);

            assertEquals(39000, item.getHaPrice());
        }
    }
}
