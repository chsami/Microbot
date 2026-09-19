package net.runelite.client.plugins.microbot.util.bank;

import net.runelite.api.Client;
import net.runelite.api.WorldType;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.After;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.EnumSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

public class Rs2BankMirrorCacheTest {
    @After
    public void clearMirror() {
        Rs2Bank.invalidateBankMirrorCache(null);
    }

    @Test
    public void restoredSnapshotIsProfileScopedAndNotALiveBankEpoch() {
        Client client = mock(Client.class);
        ConfigManager config = mock(ConfigManager.class);
        when(client.getWorldType()).thenReturn(EnumSet.noneOf(WorldType.class));
        when(config.getRSProfileKey()).thenReturn("profile-a", "profile-b");
        when(config.getRSProfileConfiguration("microbot", "bankitems"))
                .thenReturn("[995,5,0]", (String) null);
        when(config.getRSProfileConfiguration("microbot", "bankLastOpenedAt"))
                .thenReturn("123456", (String) null);

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            microbot.when(Microbot::getClient).thenReturn(client);
            microbot.when(Microbot::getConfigManager).thenReturn(config);

            Rs2Bank.restoreBankMirrorCache();
            assertTrue(Rs2Bank.hasBankMirrorSnapshot());
            assertEquals(123456L, Rs2Bank.getBankLastOpenedAt());
            assertEquals(0, Rs2Bank.getBankLiveEpoch());
            assertEquals(1, Rs2Bank.bankItems().size());
            assertEquals(995, Rs2Bank.bankItems().get(0).getId());
            assertEquals(5, Rs2Bank.bankItems().get(0).getQuantity());
            microbot.verify(Microbot::getClientThread, never());

            Rs2Bank.restoreBankMirrorCache();
            assertFalse(Rs2Bank.hasBankMirrorSnapshot());
            assertEquals(0L, Rs2Bank.getBankLastOpenedAt());
        }
    }
}
