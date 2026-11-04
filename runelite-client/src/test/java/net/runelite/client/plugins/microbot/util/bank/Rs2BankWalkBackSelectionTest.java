package net.runelite.client.plugins.microbot.util.bank;

import net.runelite.api.Client;
import net.runelite.api.WorldType;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2BankObjects;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2PathApi;
import net.runelite.client.plugins.microbot.util.walker.Rs2RouteRequest;
import net.runelite.client.plugins.microbot.util.walker.Rs2RouteResult;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.EnumSet;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

public class Rs2BankWalkBackSelectionTest {

    private static final String FALLBACK_MESSAGE =
            "No bank within walking distance, choosing a bank you can reach with teleports.";

    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Player> player;
    private MockedStatic<Rs2BankObjects> bankObjects;
    private MockedStatic<Rs2PathApi> pathApi;
    private MockedStatic<Rs2Inventory> inventory;
    private MockedStatic<Rs2Bank> bank;

    @Before
    public void setUp() {
        microbot = Mockito.mockStatic(Microbot.class, Mockito.CALLS_REAL_METHODS);
        Client client = mock(Client.class);
        when(client.getWorldType()).thenReturn(EnumSet.noneOf(WorldType.class));
        microbot.when(Microbot::getClient).thenReturn(client);
        microbot.when(() -> Microbot.getVarbitValue(anyInt())).thenReturn(0);
        player = Mockito.mockStatic(Rs2Player.class);
        bankObjects = Mockito.mockStatic(Rs2BankObjects.class);
        pathApi = Mockito.mockStatic(Rs2PathApi.class);
        inventory = Mockito.mockStatic(Rs2Inventory.class);
        bank = Mockito.mockStatic(Rs2Bank.class, Mockito.CALLS_REAL_METHODS);
        bank.when(() -> Rs2Bank.walkToBankAndUseBank(any(BankLocation.class))).thenReturn(false);
        inventory.when(Rs2Inventory::emptySlotCount).thenReturn(0);
    }

    @After
    public void tearDown() {
        bank.close();
        inventory.close();
        pathApi.close();
        bankObjects.close();
        player.close();
        microbot.close();
    }

    private static Rs2RouteResult routeTo(WorldPoint... path) {
        Rs2RouteResult route = mock(Rs2RouteResult.class);
        when(route.getPath()).thenReturn(List.of(path));
        return route;
    }

    private void routes(Rs2RouteResult walking, Rs2RouteResult withTeleports) {
        pathApi.when(() -> Rs2PathApi.plan(any(Rs2RouteRequest.class))).thenAnswer(invocation -> {
            Rs2RouteRequest request = invocation.getArgument(0);
            return request.isTeleportsIgnored() ? walking : withTeleports;
        });
    }

    @Test
    public void walkBackOverloadsBankAtTheClosestBankOnFoot() {
        WorldPoint barbarianVillage = new WorldPoint(3081, 3420, 0);
        player.when(Rs2Player::getWorldLocation).thenReturn(barbarianVillage);
        routes(routeTo(barbarianVillage, BankLocation.EDGEVILLE.getWorldPoint()),
                routeTo(barbarianVillage, BankLocation.LUMBRIDGE_TOP.getWorldPoint()));

        Rs2Bank.bankItemsAndWalkBackToOriginalPosition(List.of("Logs"), barbarianVillage);
        Rs2Bank.bankItemsAndWalkBackToOriginalPosition(List.of("Logs"), barbarianVillage, 0);
        Rs2Bank.bankItemsAndWalkBackToOriginalPosition(List.of("Logs"), barbarianVillage, 0, 4);

        bank.verify(() -> Rs2Bank.walkToBankAndUseBank(BankLocation.EDGEVILLE), times(3));
        bank.verify(() -> Rs2Bank.walkToBankAndUseBank(BankLocation.LUMBRIDGE_TOP), never());
        pathApi.verify(() -> Rs2PathApi.plan(argThat(request -> !request.isTeleportsIgnored())), never());
        microbot.verify(() -> Microbot.log(
                "Banking at EDGEVILLE (closest bank on foot, so the walk back stays short)"), times(3));
        microbot.verify(() -> Microbot.log("Finding nearest bank..."), never());
    }

    @Test
    public void fallsBackToTeleportRouteQuietlyWhenNoBankIsWalkable() {
        WorldPoint island = new WorldPoint(2852, 3235, 0);
        player.when(Rs2Player::getWorldLocation).thenReturn(island);
        routes(routeTo(island, new WorldPoint(2853, 3235, 0)),
                routeTo(island, BankLocation.LUMBRIDGE_TOP.getWorldPoint()));

        Rs2Bank.bankItemsAndWalkBackToOriginalPosition(List.of("Logs"), island);
        Rs2Bank.bankItemsAndWalkBackToOriginalPosition(List.of("Logs"), island);

        bank.verify(() -> Rs2Bank.walkToBankAndUseBank(BankLocation.LUMBRIDGE_TOP), times(2));
        microbot.verify(() -> Microbot.log(FALLBACK_MESSAGE), times(1));
        microbot.verify(() -> Microbot.log(startsWith("No bank reachable")), never());
        microbot.verify(() -> Microbot.log("Unable to find path to nearest bank"), never());
        microbot.verify(() -> Microbot.log("Finding nearest bank..."), times(2));
        microbot.verify(() -> Microbot.log("Found nearest bank (shortest path): LUMBRIDGE_TOP"), times(2));
    }

    @Test
    public void walkBackDoesNotSearchForABankWhileInventoryHasRoom() {
        WorldPoint origin = new WorldPoint(3081, 3420, 0);
        player.when(Rs2Player::getWorldLocation).thenReturn(origin);
        inventory.when(Rs2Inventory::emptySlotCount).thenReturn(20);
        try (MockedStatic<Rs2Walker> walker = Mockito.mockStatic(Rs2Walker.class);
             MockedStatic<Rs2Tile> tile = Mockito.mockStatic(Rs2Tile.class)) {
            tile.when(() -> Rs2Tile.isTileReachable(origin)).thenReturn(true);

            Rs2Bank.bankItemsAndWalkBackToOriginalPosition(List.of("Logs"), origin);

            pathApi.verifyNoInteractions();
            bank.verify(() -> Rs2Bank.walkToBankAndUseBank(any(BankLocation.class)), never());
            microbot.verify(() -> Microbot.log(anyString()), never());
        }
    }
}
