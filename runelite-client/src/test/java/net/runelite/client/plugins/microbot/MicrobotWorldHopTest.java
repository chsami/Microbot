package net.runelite.client.plugins.microbot;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.World;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.WorldService;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.HopBudget;
import net.runelite.client.plugins.microbot.util.shop.Rs2Shop;
import net.runelite.http.api.worlds.WorldResult;
import net.runelite.http.api.worlds.WorldType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.EnumSet;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MicrobotWorldHopTest {

    private MockedStatic<Microbot> microbot;
    private Client client;
    private World target;
    private final AtomicInteger clientThreadCalls = new AtomicInteger();
    private final AtomicBoolean switcherLoaded = new AtomicBoolean();
    private final AtomicInteger openCall = new AtomicInteger(-1);
    private final AtomicInteger hopCall = new AtomicInteger(-1);

    @Before
    public void setUp() {
        client = mock(Client.class);
        target = mock(World.class);
        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.runOnClientThreadOptional(any())).thenAnswer(inv -> {
            clientThreadCalls.incrementAndGet();
            Callable<?> callable = inv.getArgument(0);
            return Optional.ofNullable(callable.call());
        });
        Widget buttons = mock(Widget.class);
        when(client.getWidget(InterfaceID.Worldswitcher.BUTTONS)).thenAnswer(inv -> switcherLoaded.get() ? buttons : null);
        doAnswer(inv -> {
            openCall.set(clientThreadCalls.get());
            return null;
        }).when(client).openWorldHopper();
        doAnswer(inv -> {
            hopCall.set(clientThreadCalls.get());
            return null;
        }).when(client).hopToWorld(any());

        microbot = Mockito.mockStatic(Microbot.class, Mockito.CALLS_REAL_METHODS);
        microbot.when(Microbot::getClient).thenReturn(client);
        microbot.when(Microbot::getClientThread).thenReturn(clientThread);
    }

    @After
    public void tearDown() {
        Microbot.cantHopWorld = false;
        microbot.close();
    }

    private static final WorldPoint REFUSED_AT = new WorldPoint(3221, 3218, 0);

    private void stubWorlds() {
        net.runelite.http.api.worlds.World f2p = mock(net.runelite.http.api.worlds.World.class);
        when(f2p.getTypes()).thenReturn(EnumSet.noneOf(WorldType.class));
        net.runelite.http.api.worlds.World members = mock(net.runelite.http.api.worlds.World.class);
        when(members.getTypes()).thenReturn(EnumSet.of(WorldType.MEMBERS));
        WorldResult result = mock(WorldResult.class);
        when(result.findWorld(308)).thenReturn(f2p);
        when(result.findWorld(302)).thenReturn(members);
        WorldService worldService = mock(WorldService.class);
        when(worldService.getWorlds()).thenReturn(result);
        microbot.when(Microbot::getWorldService).thenReturn(worldService);
    }

    @Test
    public void membersAreaRefusalBlocksNonMembersHopFromTheSameTile() {
        stubWorlds();
        microbot.when(Microbot::isLoggedIn).thenReturn(true);
        when(client.getWorld()).thenReturn(301);
        Microbot.markNonMembersHopRefused(REFUSED_AT);
        try (MockedStatic<Rs2Player> player = Mockito.mockStatic(Rs2Player.class);
             MockedStatic<HopBudget> hopBudget = Mockito.mockStatic(HopBudget.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(REFUSED_AT);

            assertFalse(Microbot.hopToWorld(308));

            hopBudget.verify(HopBudget::getInstance, never());
        }
        assertTrue(Microbot.cantHopWorld);
        verify(client, never()).hopToWorld(any());
    }

    @Test
    public void membersAreaRefusalDoesNotBlockMembersWorlds() {
        stubWorlds();
        Microbot.markNonMembersHopRefused(REFUSED_AT);
        try (MockedStatic<Rs2Player> player = Mockito.mockStatic(Rs2Player.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(REFUSED_AT);

            assertFalse(Microbot.isNonMembersHopRefused(302));
            assertTrue(Microbot.isNonMembersHopRefused(308));
        }
    }

    @Test
    public void membersAreaRefusalClearsOnceThePlayerHasMoved() {
        stubWorlds();
        Microbot.markNonMembersHopRefused(REFUSED_AT);
        try (MockedStatic<Rs2Player> player = Mockito.mockStatic(Rs2Player.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(REFUSED_AT.dx(1));

            assertFalse(Microbot.isNonMembersHopRefused(308));
            assertFalse(Microbot.cantHopWorld);

            player.when(Rs2Player::getWorldLocation).thenReturn(REFUSED_AT);
            assertFalse(Microbot.isNonMembersHopRefused(308));
        }
    }

    @Test
    public void flagWithoutARefusalLocationDoesNotBlockHops() {
        stubWorlds();
        Microbot.markNonMembersHopRefused(null);
        try (MockedStatic<Rs2Player> player = Mockito.mockStatic(Rs2Player.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(REFUSED_AT);

            assertFalse(Microbot.isNonMembersHopRefused(308));
            assertFalse(Microbot.cantHopWorld);
        }
    }

    @Test
    public void staleRefusalDoesNotStopLaterHopAttempts() {
        stubWorlds();
        microbot.when(Microbot::isLoggedIn).thenReturn(true);
        when(client.getWorld()).thenReturn(301);
        Microbot.markNonMembersHopRefused(REFUSED_AT);
        try (MockedStatic<Rs2Player> player = Mockito.mockStatic(Rs2Player.class);
             MockedStatic<Rs2Combat> combat = Mockito.mockStatic(Rs2Combat.class);
             MockedStatic<Rs2Bank> bank = Mockito.mockStatic(Rs2Bank.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(3165, 3487, 0));
            combat.when(Rs2Combat::inCombat).thenReturn(false);
            bank.when(Rs2Bank::isOpen).thenReturn(true);

            assertFalse(Microbot.hopToWorld(308));

            combat.verify(Rs2Combat::inCombat, atLeastOnce());
        }
        assertFalse(Microbot.cantHopWorld);
    }

    @Test
    public void opensSwitcherThenHopsOnceListHasLoaded() {
        doAnswer(inv -> {
            openCall.set(clientThreadCalls.get());
            new Thread(() -> {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ignored) {
                }
                switcherLoaded.set(true);
            }).start();
            return null;
        }).when(client).openWorldHopper();

        assertTrue(Microbot.hopViaWorldSwitcher(target, 3000));

        InOrder order = inOrder(client);
        order.verify(client, atLeastOnce()).openWorldHopper();
        order.verify(client).hopToWorld(target);
        assertNotEquals(openCall.get(), hopCall.get());
    }

    @Test
    public void hopsImmediatelyWhenSwitcherAlreadyOpen() {
        switcherLoaded.set(true);

        assertTrue(Microbot.hopViaWorldSwitcher(target, 3000));

        verify(client, never()).openWorldHopper();
        verify(client).hopToWorld(target);
    }

    @Test
    public void blockedHopIsRefusedBeforeWaitingForTheHopLimit() {
        HopBudget budget = mock(HopBudget.class);
        when(budget.millisUntilHopAllowed()).thenThrow(new AssertionError("waited for the hop limit"));
        when(budget.cachedMillisUntilHopAllowed()).thenThrow(new AssertionError("waited for the hop limit"));
        microbot.when(Microbot::isLoggedIn).thenReturn(true);
        when(client.getWorld()).thenReturn(301);
        try (MockedStatic<HopBudget> hopBudget = Mockito.mockStatic(HopBudget.class);
             MockedStatic<Rs2Combat> combat = Mockito.mockStatic(Rs2Combat.class);
             MockedStatic<Rs2Bank> bank = Mockito.mockStatic(Rs2Bank.class)) {
            hopBudget.when(HopBudget::getInstance).thenReturn(budget);
            combat.when(Rs2Combat::inCombat).thenReturn(false);
            bank.when(Rs2Bank::isOpen).thenReturn(true);

            assertFalse(Microbot.hopToWorld(302));
        }

        verify(client, never()).hopToWorld(any());
    }

    @Test
    public void refusesHopWhenWorldListIsNotLoaded() {
        WorldService worldService = mock(WorldService.class);
        when(worldService.getWorlds()).thenReturn(null);
        microbot.when(Microbot::getWorldService).thenReturn(worldService);

        assertFalse(hopWithNoBlockers(302));

        verify(client, never()).createWorld();
        verify(client, never()).hopToWorld(any());
    }

    @Test
    public void refusesHopWhenWorldServiceIsMissing() {
        microbot.when(Microbot::getWorldService).thenReturn(null);

        assertFalse(hopWithNoBlockers(302));

        verify(client, never()).createWorld();
        verify(client, never()).hopToWorld(any());
    }

    private boolean hopWithNoBlockers(int worldNumber) {
        microbot.when(Microbot::isLoggedIn).thenReturn(true);
        when(client.getWorld()).thenReturn(301);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        HopBudget budget = mock(HopBudget.class);
        try (MockedStatic<HopBudget> hopBudget = Mockito.mockStatic(HopBudget.class);
             MockedStatic<Rs2Combat> combat = Mockito.mockStatic(Rs2Combat.class);
             MockedStatic<Rs2Bank> bank = Mockito.mockStatic(Rs2Bank.class);
             MockedStatic<Rs2Shop> shop = Mockito.mockStatic(Rs2Shop.class);
             MockedStatic<Rs2Dialogue> dialogue = Mockito.mockStatic(Rs2Dialogue.class)) {
            hopBudget.when(HopBudget::getInstance).thenReturn(budget);
            return Microbot.hopToWorld(worldNumber);
        }
    }

    @Test
    public void doesNotHopWhenSwitcherNeverLoads() {
        assertFalse(Microbot.hopViaWorldSwitcher(target, 300));

        verify(client, atLeastOnce()).openWorldHopper();
        verify(client, never()).hopToWorld(any());
        assertEquals(-1, hopCall.get());
    }
}
