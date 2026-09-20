package net.runelite.client.plugins.microbot.api;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.player.Rs2PlayerCache;
import net.runelite.client.plugins.microbot.api.tileitem.Rs2TileItemCache;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.api.actor.Rs2ActorModel;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.api.widgets.Widget;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ApiBoundaryRegressionTest
{
    @Test
    public void boatQueriesDoNotShareAnotherPlayersResult() throws Exception
    {
        try (ApiTestClient env = new ApiTestClient()) {
            env.installCache("rs2BoatCache", net.runelite.client.plugins.microbot.api.boat.Rs2BoatCache.class);
            WorldView top = mock(WorldView.class);
            when(top.isTopLevel()).thenReturn(true);
            when(env.client.getTopLevelWorldView()).thenReturn(top);
            IndexedObjectSet<WorldEntity> boats = mock(IndexedObjectSet.class);
            doReturn(boats).when(top).worldEntities();
            Player[] players = new Player[2];
            WorldView[] views = new WorldView[2];
            for (int i = 0; i < 2; i++) {
                int id = i + 1;
                players[i] = mock(Player.class);
                views[i] = mock(WorldView.class);
                when(views[i].getId()).thenReturn(id);
                when(players[i].getWorldView()).thenReturn(views[i]);
                when(players[i].getLocalLocation()).thenReturn(new LocalPoint(128, 128, id));
                when(env.client.getWorldView(id)).thenReturn(views[i]);
                WorldEntity boat = mock(WorldEntity.class);
                when(boat.getWorldView()).thenReturn(views[i]);
                when(boats.byIndex(id)).thenAnswer(invocation -> { env.requireClientThread(); return boat; });
            }
            when(env.client.getLocalPlayer()).thenReturn(players[0]);
            assertSame(views[0], Microbot.getRs2BoatCache().getLocalBoat().getWorldView());
            assertSame(views[1], Microbot.getRs2BoatCache().getBoat(
                    new net.runelite.client.plugins.microbot.api.player.models.Rs2PlayerModel(players[1])).getWorldView());
            when(players[1].getWorldView()).thenReturn(top);
            assertNull(Microbot.getRs2BoatCache().getBoat(
                    new net.runelite.client.plugins.microbot.api.player.models.Rs2PlayerModel(players[1])));
            assertSame(views[0], Microbot.getRs2BoatCache().getLocalBoat().getWorldView());
        }
    }

    @Test
    public void detachedEntitiesCannotDispatch() throws Exception
    {
        try (ApiTestClient env = new ApiTestClient()) {
            when(env.client.getGameState()).thenReturn(GameState.LOGGED_IN);
            WorldView stale = mock(WorldView.class);
            NPC npc = mock(NPC.class);
            when(npc.getWorldView()).thenReturn(stale);
            assertFalse(new Rs2NpcModel(npc).click("Talk-to"));
            assertFalse(new Rs2TileItemModel(mock(Tile.class), mock(TileItem.class), stale).click("Take"));
        }
    }

    @Test
    public void queryConstructionCollectsOnClientThreadAndInvalidatesWithinTick() throws Exception
    {
        try (ApiTestClient env = new ApiTestClient()) {
            Player player = mock(Player.class);
            WorldView view = mock(WorldView.class);
            Scene scene = mock(Scene.class);
            when(env.client.getLocalPlayer()).thenAnswer(i -> { env.requireClientThread(); return player; });
            when(env.client.getGameState()).thenReturn(GameState.LOGGED_IN);
            when(env.client.getWorldView(-1)).thenReturn(view);
            Microbot.getWorldViewIds().add(-1);
            when(view.getScene()).thenReturn(scene);
            IndexedObjectSet<NPC> npcs = mock(IndexedObjectSet.class);
            IndexedObjectSet<Player> players = mock(IndexedObjectSet.class);
            doReturn(npcs).when(view).npcs();
            doReturn(players).when(view).players();
            when(npcs.stream()).thenAnswer(i -> { env.requireClientThread(); return Stream.of(mock(NPC.class)); });
            when(players.stream()).thenAnswer(i -> { env.requireClientThread(); return Stream.of(player); });
            Tile tile = mock(Tile.class);
            when(tile.getGroundItems()).thenAnswer(i -> { env.requireClientThread(); return List.of(mock(TileItem.class)); });
            when(scene.getTiles()).thenAnswer(i -> { env.requireClientThread(); return new Tile[][][]{{{tile}}}; });
            env.installCache("rs2NpcCache", Rs2NpcCache.class);
            env.installCache("rs2PlayerCache", Rs2PlayerCache.class);
            env.installCache("rs2TileItemCache", Rs2TileItemCache.class);
            assertEquals(1, Microbot.getRs2NpcCache().query().count());
            assertEquals(1, Microbot.getRs2PlayerCache().query().count());
            assertSame(view, Microbot.getRs2TileItemCache().query().first().getWorldView());
            // Two simultaneous readers of a newly invalidated tick must perform one refresh.
            when(env.client.getTickCount()).thenReturn(1);
            ExecutorService readers = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            try {
                Future<Integer> a = readers.submit(() -> { start.await(); return Microbot.getRs2NpcCache().query().count(); });
                Future<Integer> b = readers.submit(() -> { start.await(); return Microbot.getRs2NpcCache().query().count(); });
                start.countDown();
                assertEquals(Integer.valueOf(1), a.get(5, TimeUnit.SECONDS));
                assertEquals(Integer.valueOf(1), b.get(5, TimeUnit.SECONDS));
            } finally { readers.shutdownNow(); }
            verify(npcs, times(2)).stream();
            when(view.getScene()).thenReturn(mock(Scene.class));
            Microbot.getRs2NpcCache().query().count();
            verify(npcs, times(3)).stream();
            when(env.client.getGameState()).thenReturn(GameState.HOPPING);
            assertEquals(0, Microbot.getRs2NpcCache().query().count());
            assertEquals(0, Microbot.getRs2PlayerCache().query().count());
            assertEquals(0, Microbot.getRs2TileItemCache().query().count());
        }
    }

    @Test
    public void groundItemRejectsUnknownActionWhilePreservingWidgetTargeting() throws Exception
    {
        try (ApiTestClient env = new ApiTestClient()) {
            WorldView view = mock(WorldView.class);
            Scene scene = mock(Scene.class);
            Tile tile = mock(Tile.class);
            TileItem item = mock(TileItem.class);
            ItemComposition definition = mock(ItemComposition.class);
            when(env.client.getGameState()).thenReturn(GameState.LOGGED_IN);
            when(env.client.getWorldView(-1)).thenReturn(view);
            when(view.getId()).thenReturn(-1);
            when(view.getScene()).thenReturn(scene);
            when(scene.getTiles()).thenReturn(new Tile[][][]{{{tile}}});
            when(tile.getLocalLocation()).thenReturn(new LocalPoint(64, 64, -1));
            when(tile.getGroundItems()).thenReturn(List.of(item));
            when(env.client.getItemDefinition(0)).thenReturn(definition);
            when(env.client.isWidgetSelected()).thenReturn(true);
            Rs2TileItemModel model = new Rs2TileItemModel(tile, item, view);
            java.lang.reflect.Method prepare = Rs2TileItemModel.class.getDeclaredMethod("prepareDispatch", String.class);
            prepare.setAccessible(true);
            env.call(() -> {
                try (org.mockito.MockedStatic<net.runelite.client.plugins.microbot.util.reflection.Rs2Reflection> reflection =
                             mockStatic(net.runelite.client.plugins.microbot.util.reflection.Rs2Reflection.class);
                     org.mockito.MockedStatic<Perspective> perspective = mockStatic(Perspective.class)) {
                    reflection.when(() -> net.runelite.client.plugins.microbot.util.reflection.Rs2Reflection.getGroundItemActions(definition))
                            .thenReturn(new String[]{null, null, "Take"});
                    assertNull(prepare.invoke(model, "Unknown"));
                    Map.Entry<MenuEntry, ?> matched = (Map.Entry<MenuEntry, ?>) prepare.invoke(model, "Take");
                    assertEquals(MenuAction.WIDGET_TARGET_ON_GROUND_ITEM, matched.getKey().getType());
                    reflection.when(() -> net.runelite.client.plugins.microbot.util.reflection.Rs2Reflection.getGroundItemActions(definition))
                            .thenReturn(new String[0]);
                    Map.Entry<MenuEntry, ?> empty = (Map.Entry<MenuEntry, ?>) prepare.invoke(model, "");
                    assertEquals(MenuAction.WIDGET_TARGET_ON_GROUND_ITEM, empty.getKey().getType());
                    when(env.client.isWidgetSelected()).thenReturn(false);
                    assertNull(prepare.invoke(model, "Unknown"));
                    reflection.when(() -> net.runelite.client.plugins.microbot.util.reflection.Rs2Reflection.getGroundItemActions(definition))
                            .thenReturn(new String[]{null, null, "Take"});
                    Map.Entry<MenuEntry, ?> take = (Map.Entry<MenuEntry, ?>) prepare.invoke(model, "Take");
                    assertEquals(MenuAction.GROUND_ITEM_THIRD_OPTION, take.getKey().getType());
                }
                return null;
            });
        }
    }

    @Test
    public void npcDistanceUsesTilesAndRejectsDifferentViewsAndPlanes() throws Exception
    {
        try (ApiTestClient env = new ApiTestClient()) {
            NPC npc = mock(NPC.class);
            Player player = mock(Player.class);
            WorldView view = mock(WorldView.class);
            when(env.client.getLocalPlayer()).thenReturn(player);
            when(player.getWorldView()).thenReturn(view);
            when(npc.getWorldView()).thenReturn(view);
            when(player.getWorldLocation()).thenReturn(new WorldPoint(3200, 3200, 0));
            when(npc.getWorldLocation()).thenReturn(new WorldPoint(3201, 3201, 0));
            Rs2NpcModel model = new Rs2NpcModel(npc);
            assertEquals(1, model.getDistanceFromPlayer());
            assertTrue(model.isWithinDistanceFromPlayer(1));
            assertFalse(model.isWithinDistanceFromPlayer(0));
            when(npc.getWorldLocation()).thenReturn(new WorldPoint(3200, 3200, 1));
            assertFalse(model.isWithinDistanceFromPlayer(Integer.MAX_VALUE));
            when(npc.getWorldView()).thenReturn(mock(WorldView.class));
            assertFalse(model.isWithinDistanceFromPlayer(Integer.MAX_VALUE));
        }
    }

    @Test
    public void actorReadsAndMutationsAndWidgetTextAreConfined() throws Exception
    {
        try (ApiTestClient env = new ApiTestClient()) {
            Actor actor = mock(Actor.class);
            when(actor.getAnimation()).thenAnswer(i -> { env.requireClientThread(); return 42; });
            doAnswer(i -> { env.requireClientThread(); return null; }).when(actor).setAnimation(7);
            when(actor.getLocalLocation()).thenAnswer(i -> { env.requireClientThread(); return null; });
            Rs2ActorModel model = new Rs2ActorModel(actor);
            assertEquals(42, model.getAnimation());
            assertNull(model.getLocalLocation());
            model.setAnimation(7);
            verify(actor).setAnimation(7);
            Widget widget = mock(Widget.class);
            when(env.client.getWidget(1, 2)).thenReturn(widget);
            when(widget.getText()).thenAnswer(i -> { env.requireClientThread(); return "ready"; });
            assertEquals("ready", Rs2Widget.getChildWidgetText(1, 2));
            // A detached/replaced widget must not be clicked.
            assertFalse(Rs2Widget.clickWidget(widget));
        }
    }

    @Test
    public void nameMatchingIsLocaleIndependentAndNullSafe() throws Exception
    {
        Locale previous = Locale.getDefault();
        try (ApiTestClient env = new ApiTestClient()) {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            NPC npc = mock(NPC.class);
            when(npc.getName()).thenReturn("GOBLIN");
            Rs2NpcModel model = new Rs2NpcModel(npc);
            assertTrue(Rs2NpcModel.matches(false, "goblin").test(model));
            assertTrue(Rs2NpcModel.matches(true, "Goblin").test(model));
            assertFalse(Rs2NpcModel.matches(false, (String) null).test(model));
            assertFalse(Rs2NpcModel.matches(false, (String[]) null).test(model));
        } finally { Locale.setDefault(previous); }
    }

    @Test
    public void valuesUseMarketPricesLongArithmeticAndExplicitAlchemyCosts() throws Exception
    {
        try (ApiTestClient env = new ApiTestClient()) {
            TileItem item = mock(TileItem.class);
            when(item.getId()).thenReturn(1);
            when(item.getQuantity()).thenReturn(2_000_000);
            ItemComposition definition = mock(ItemComposition.class);
            when(definition.getId()).thenReturn(1);
            when(definition.getPrice()).thenReturn(10_000);
            when(env.client.getItemDefinition(1)).thenReturn(definition);
            ItemManager prices = mock(ItemManager.class);
            when(prices.getItemPrice(1)).thenReturn(5_000);
            env.install("itemManager", prices);
            Rs2TileItemModel model = new Rs2TileItemModel(mock(Tile.class), item, mock(WorldView.class));
            assertEquals(10_000_000_000L, model.getTotalGeValueLong());
            assertEquals(20_000_000_000L, model.getTotalBaseValueLong());
            assertEquals(12_000_000_000L, model.getTotalHighAlchValueLong());
            assertEquals(Integer.MAX_VALUE, model.getTotalGeValue());
            assertTrue(model.isProfitableToHighAlch());
            assertFalse(model.isProfitableToHighAlch(1000));
            when(prices.getItemPrice(1)).thenReturn(6000);
            assertFalse(model.isProfitableToHighAlch());
            when(prices.getItemPrice(1)).thenReturn(6001);
            assertFalse(model.isProfitableToHighAlch());
        }
    }
}
