package net.runelite.client.plugins.microbot.util.grounditem;

import java.awt.Polygon;
import java.util.List;
import java.lang.reflect.Method;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.plugins.microbot.api.ApiTestClient;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.util.reflection.Rs2Reflection;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PickupSceneTest {
    @Test public void selectedItemsMissingActionsAndReplacedScenesRejectPickup() throws Exception {
        try (ApiTestClient env = new ApiTestClient()) {
            WorldView view = mock(WorldView.class);
            Scene scene = mock(Scene.class);
            Tile tile = mock(Tile.class);
            TileItem item = mock(TileItem.class);
            ItemComposition definition = mock(ItemComposition.class);
            when(env.client.getGameState()).thenReturn(GameState.LOGGED_IN);
            when(view.getId()).thenReturn(-1);
            when(env.client.getWorldView(-1)).thenReturn(view);
            when(view.getScene()).thenReturn(scene);
            when(scene.getTiles()).thenReturn(new Tile[][][]{{{tile}}});
            when(tile.getLocalLocation()).thenReturn(new LocalPoint(64, 64, -1));
            when(tile.getGroundItems()).thenReturn(List.of(item));
            when(item.getId()).thenReturn(100);
            when(env.client.getItemDefinition(100)).thenReturn(definition);
            when(env.client.getCanvasWidth()).thenReturn(800);
            when(env.client.getCanvasHeight()).thenReturn(600);
            Rs2TileItemModel model = new Rs2TileItemModel(tile, item, view);
            Method prepare = GroundItemPickup.class.getDeclaredMethod("prepare", Rs2TileItemModel.class, String.class);
            prepare.setAccessible(true);
            env.call(() -> {
                try (MockedStatic<Rs2Reflection> actions = mockStatic(Rs2Reflection.class);
                     MockedStatic<Perspective> perspective = mockStatic(Perspective.class)) {
                    actions.when(() -> Rs2Reflection.getGroundItemActionsStrict(definition)).thenReturn(new String[]{null, null, "Take"});
                    perspective.when(() -> Perspective.getCanvasTilePoly(env.client, tile.getLocalLocation()))
                            .thenReturn(new Polygon(new int[]{10, 30, 30, 10}, new int[]{10, 10, 30, 30}, 4));
                    assertNotNull(prepare.invoke(null, model, "Take"));
                    when(env.client.isWidgetSelected()).thenReturn(true);
                    assertNull(prepare.invoke(null, model, "Take"));
                    when(env.client.isWidgetSelected()).thenReturn(false);
                    actions.when(() -> Rs2Reflection.getGroundItemActionsStrict(definition)).thenReturn(new String[0]);
                    assertNull(prepare.invoke(null, model, "Take"));
                    actions.when(() -> Rs2Reflection.getGroundItemActionsStrict(definition)).thenReturn(new String[]{null, null, "Take"});
                    when(tile.getGroundItems()).thenReturn(List.of(mock(TileItem.class)));
                    assertNull(prepare.invoke(null, model, "Take"));
                    when(tile.getGroundItems()).thenReturn(List.of(item));
                    when(env.client.getWorldView(-1)).thenReturn(mock(WorldView.class));
                    assertNull(prepare.invoke(null, model, "Take"));
                }
                return null;
            });
        }
    }
}
