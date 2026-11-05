package net.runelite.client.plugins.microbot.util.depositbox;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2BankObjects;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2PathApi;
import net.runelite.client.plugins.microbot.util.walker.Rs2RouteRequest;
import net.runelite.client.plugins.microbot.util.walker.Rs2RouteResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

public class Rs2DepositBoxWalkBackSelectionTest {

    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Player> player;
    private MockedStatic<Rs2BankObjects> bankObjects;
    private MockedStatic<Rs2PathApi> pathApi;
    private MockedStatic<Rs2Inventory> inventory;
    private MockedStatic<Rs2DepositBox> depositBox;

    @Before
    public void setUp() {
        microbot = Mockito.mockStatic(Microbot.class, Mockito.CALLS_REAL_METHODS);
        microbot.when(Microbot::getClientThread).thenReturn(mock(ClientThread.class));
        microbot.when(() -> Microbot.getVarbitValue(anyInt())).thenReturn(0);
        player = Mockito.mockStatic(Rs2Player.class);
        bankObjects = Mockito.mockStatic(Rs2BankObjects.class);
        pathApi = Mockito.mockStatic(Rs2PathApi.class);
        inventory = Mockito.mockStatic(Rs2Inventory.class);
        inventory.when(Rs2Inventory::getEmptySlots).thenReturn(0);
        depositBox = Mockito.mockStatic(Rs2DepositBox.class, Mockito.CALLS_REAL_METHODS);
        depositBox.when(() -> Rs2DepositBox.walkToAndUseDepositBox(any(DepositBoxLocation.class))).thenReturn(false);
    }

    @After
    public void tearDown() {
        depositBox.close();
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
    public void walkBackOverloadsUseTheClosestDepositBoxOnFoot() {
        WorldPoint barbarianVillage = new WorldPoint(3081, 3420, 0);
        player.when(Rs2Player::getWorldLocation).thenReturn(barbarianVillage);
        routes(routeTo(barbarianVillage, DepositBoxLocation.EDGEVILLE.getWorldPoint()),
                routeTo(barbarianVillage, DepositBoxLocation.LUMBRIDGE.getWorldPoint()));

        Rs2DepositBox.bankItemsAndWalkBackToOriginalPosition(List.of("Logs"), barbarianVillage);
        Rs2DepositBox.bankItemsAndWalkBackToOriginalPosition(List.of("Logs"), barbarianVillage, 0);
        Rs2DepositBox.bankItemsAndWalkBackToOriginalPosition(List.of("Logs"), barbarianVillage, 0, 4);
        Rs2DepositBox.bankItemsAndWalkBackToOriginalPosition(List.of("Logs"), false, barbarianVillage, 0, 4);

        depositBox.verify(() -> Rs2DepositBox.walkToAndUseDepositBox(DepositBoxLocation.EDGEVILLE), times(4));
        depositBox.verify(() -> Rs2DepositBox.walkToAndUseDepositBox(DepositBoxLocation.LUMBRIDGE), never());
        pathApi.verify(() -> Rs2PathApi.plan(argThat(request -> !request.isTeleportsIgnored())), never());
        microbot.verify(() -> Microbot.log(
                "Using deposit box at EDGEVILLE (closest on foot, so the walk back stays short)"), times(4));
    }

    @Test
    public void fallsBackToTeleportRouteQuietlyWhenNoDepositBoxIsWalkable() {
        WorldPoint island = new WorldPoint(2852, 3235, 0);
        player.when(Rs2Player::getWorldLocation).thenReturn(island);
        routes(routeTo(island, new WorldPoint(2853, 3235, 0)),
                routeTo(island, DepositBoxLocation.LUMBRIDGE.getWorldPoint()));

        Rs2DepositBox.bankItemsAndWalkBackToOriginalPosition(List.of("Logs"), island);
        Rs2DepositBox.bankItemsAndWalkBackToOriginalPosition(List.of("Logs"), island);

        depositBox.verify(() -> Rs2DepositBox.walkToAndUseDepositBox(DepositBoxLocation.LUMBRIDGE), times(2));
        microbot.verify(() -> Microbot.log(
                "No deposit box within walking distance, choosing one you can reach with teleports."), times(1));
        microbot.verify(() -> Microbot.log(startsWith("Nearest deposit box point")), never());
        microbot.verify(() -> Microbot.log("Unable to find path to any deposit box"), never());
    }
}
