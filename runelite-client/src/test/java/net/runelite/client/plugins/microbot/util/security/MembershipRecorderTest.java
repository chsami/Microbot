package net.runelite.client.plugins.microbot.util.security;

import java.util.concurrent.TimeUnit;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigProfile;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class MembershipRecorderTest {
    private static final long DAY = TimeUnit.DAYS.toMillis(1);

    private final java.util.concurrent.atomic.AtomicInteger saves = new java.util.concurrent.atomic.AtomicInteger();
    private final MembershipRecorder recorder = new MembershipRecorder(saves::incrementAndGet);
    private final Client client = mock(Client.class);
    private final ConfigManager configManager = mock(ConfigManager.class);
    private final ConfigProfile profile = mock(ConfigProfile.class);

    @Before public void setUp() {
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getLocalPlayer()).thenReturn(mock(Player.class));
        when(configManager.getProfile()).thenReturn(profile);
        when(profile.getName()).thenReturn("profile");
    }

    private void tick(int credit, int memberFlag) {
        when(client.getVarpValue(VarPlayerID.ACCOUNT_CREDIT)).thenReturn(credit);
        when(client.getVarcIntValue(VarClientID.PLAYERMEMBER)).thenReturn(memberFlag);
        recorder.onGameTick(client, configManager, Runnable::run);
    }

    @Test public void savesMembershipOnlyWhenItChanges() {
        tick(0, 0);   // before the server sends the values
        tick(12, 1);
        tick(12, 1);
        tick(26, 1);  // a bond redeemed during the session

        verify(configManager).setMember(profile, false);
        verify(configManager).setMemberExpireDays(profile, 0);
        verify(configManager).setMemberExpireDays(profile, 12);
        verify(configManager).setMemberExpireDays(profile, 26);
        verify(configManager, times(2)).setMember(profile, true);
        verify(configManager, times(3)).setMemberExpireDaysTimeStemp(eq(profile), anyLong());
        assertEquals(3, saves.get()); // the open profiles panel reloads after each save
    }

    @Test public void aMemberWithNoCreditDaysLeftIsStillMembers() {
        tick(0, 1);

        verify(configManager).setMember(profile, true);
    }

    @Test public void waitsUntilThePlayerIsLoaded() {
        when(client.getLocalPlayer()).thenReturn(null);
        tick(12, 1);

        verifyNoInteractions(configManager);
    }

    @Test public void savesAgainAfterTheNextLogin() {
        tick(12, 1);
        recorder.reset();
        tick(12, 1);

        verify(configManager, times(2)).setMemberExpireDays(profile, 12);
    }

    @Test public void countsTheSavedDaysDown() {
        long saved = 1_000 * DAY;
        when(profile.getMemberExpireDaysTimeStemp()).thenReturn(saved);
        when(profile.getMemberExpireDays()).thenReturn(10L);

        assertEquals(10, MembershipRecorder.remainingDays(profile, saved));
        assertEquals(7, MembershipRecorder.remainingDays(profile, saved + 3 * DAY));
        assertEquals(0, MembershipRecorder.remainingDays(profile, saved + 30 * DAY));
        assertEquals("Members · 7 days left", MembershipRecorder.describe(profile, saved + 3 * DAY));
        assertEquals("Free-to-play", MembershipRecorder.describe(profile, saved + 30 * DAY));
    }

    @Test public void isUnknownBeforeTheFirstLogin() {
        assertEquals(-1, MembershipRecorder.remainingDays(profile, System.currentTimeMillis()));
        assertEquals("Unknown until login", MembershipRecorder.describe(profile, System.currentTimeMillis()));
    }

    @Test public void expiredMembershipIsFreeToPlayForDisplayAndWorldChoice() {
        long saved = 1_000 * DAY;
        when(profile.getMemberExpireDaysTimeStemp()).thenReturn(saved);
        when(profile.getMemberExpireDays()).thenReturn(10L);
        when(profile.isMember()).thenReturn(true); // still set from the last login

        assertTrue(MembershipRecorder.isMember(profile, saved + 3 * DAY));
        assertFalse(MembershipRecorder.isMember(profile, saved + 30 * DAY));
        assertEquals("Free-to-play", MembershipRecorder.describe(profile, saved + 30 * DAY));
    }

    @Test public void aMemberRecordedWithNoCreditDaysStaysMembers() {
        when(profile.getMemberExpireDaysTimeStemp()).thenReturn(DAY);
        when(profile.getMemberExpireDays()).thenReturn(0L);
        when(profile.isMember()).thenReturn(true);

        assertTrue(MembershipRecorder.isMember(profile, 30 * DAY));
        assertEquals("Members", MembershipRecorder.describe(profile, 30 * DAY));
    }
}
