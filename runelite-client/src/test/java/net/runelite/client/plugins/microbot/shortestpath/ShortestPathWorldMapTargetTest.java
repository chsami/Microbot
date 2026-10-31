package net.runelite.client.plugins.microbot.shortestpath;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.api.worldmap.WorldMapData;
import net.runelite.client.plugins.microbot.questhelper.util.worldmap.WorldMapArea;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.SplitFlagMap;
import net.runelite.client.ui.JagexColors;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.util.ColorUtil;
import org.junit.Before;
import org.junit.Test;

import java.awt.Rectangle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ShortestPathWorldMapTargetTest {

    private Client client;
    private WorldMap worldMap;
    private Widget currentMapName;
    private ShortestPathPlugin plugin;

    @Before
    public void setUp() throws Exception {
        client = mock(Client.class);
        worldMap = mock(WorldMap.class);
        WorldMapData worldMapData = mock(WorldMapData.class);
        Widget mapView = mock(Widget.class);
        Widget mapList = mock(Widget.class);
        currentMapName = mock(Widget.class);

        when(client.getWorldMap()).thenReturn(worldMap);
        when(client.isMenuOpen()).thenReturn(false);
        when(client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER)).thenReturn(mapView);
        when(client.getWidget(InterfaceID.Worldmap.MAPLIST_DISPLAY)).thenReturn(mapList);
        when(mapList.getChild(4)).thenReturn(currentMapName);
        when(mapView.getBounds()).thenReturn(new Rectangle(10, 20, 640, 480));
        when(worldMap.getWorldMapZoom()).thenReturn(4f);
        when(worldMap.getWorldMapData()).thenReturn(worldMapData);
        when(worldMapData.surfaceContainsPosition(anyInt(), anyInt())).thenReturn(true);

        plugin = new ShortestPathPlugin(mock(WalkingNoticeOverlay.class));
        Field clientField = ShortestPathPlugin.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(plugin, client);
    }

    @Test
    public void motherlodeMineClickOnDwarvenMinesMapResolvesToRealMineTile() throws Exception {
        assertEquals(new WorldPoint(3756, 5664, 0), clickMap("Dwarven Mines", 3116, 9696));
    }

    @Test
    public void sameDisplayTileOnMisthalinUndergroundStaysAtDraynorSewers() throws Exception {
        assertEquals(new WorldPoint(3116, 9696, 0), clickMap("Misthalin Underground", 3116, 9696));
    }

    @Test
    public void surfaceClickIsUnchanged() throws Exception {
        assertEquals(new WorldPoint(3222, 3218, 0), clickMap("Gielinor Surface", 3222, 3218));
    }

    @Test
    public void unknownMapAreaKeepsDisplayTile() throws Exception {
        assertEquals(new WorldPoint(3116, 9696, 0), clickMap("Some New Area", 3116, 9696));
    }

    @Test
    public void toRealWorldPointIgnoresAnyArea() {
        WorldPoint display = new WorldPoint(3116, 9696, 0);
        assertEquals(display, ShortestPathPlugin.toRealWorldPoint(display, null));
        assertEquals(display, ShortestPathPlugin.toRealWorldPoint(display, WorldMapArea.ANY));
    }

    @Test
    public void motherlodeTargetMarkerIsDrawnWhereTheMapShowsTheMine() {
        WorldMapPoint marker = ShortestPathPlugin.createTargetMarker(new WorldPoint(3756, 5664, 0));
        assertEquals(new WorldPoint(3116, 9696, 0), marker.getWorldPoint());
        assertEquals(new WorldPoint(3116, 9696, 0), marker.getTarget());
        assertEquals("Target", marker.getName());
        assertTrue(marker.isJumpOnClick());
    }

    @Test
    public void dungeonClickRoundTripsToTheClickedMarkerPosition() throws Exception {
        WorldPoint target = clickMap("Dwarven Mines", 3116, 9696);
        assertEquals(new WorldPoint(3116, 9696, 0), ShortestPathPlugin.createTargetMarker(target).getWorldPoint());
    }

    @Test
    public void surfaceTargetMarkerIsUnchanged() {
        WorldMapPoint marker = ShortestPathPlugin.createTargetMarker(new WorldPoint(3222, 3218, 0));
        assertEquals(new WorldPoint(3222, 3218, 0), marker.getWorldPoint());
        assertEquals(new WorldPoint(3222, 3218, 0), marker.getTarget());
    }

    @Test
    public void corsairCoveDungeonClickLandsOnPlaneOne() throws Exception {
        // Drawn in place, but the dungeon is on plane 1; plane 0 there is solid rock.
        assertEquals(new WorldPoint(2012, 8999, 1), clickMap("Feldip Hills Underground", 2012, 8999));
    }

    @Test
    public void depositBoxClickSnapsToGroundBesideIt() throws Exception {
        withCollisionMap(() -> {
            WorldPoint box = new WorldPoint(3759, 5664, 0); // DepositBoxLocation.MOTHERLODE_MINE, an object tile
            WorldPoint display = ShortestPathPlugin.createTargetMarker(box).getWorldPoint();
            WorldPoint snapped = clickForTarget("Dwarven Mines", display.getX(), display.getY());
            assertTrue("deposit box must snap to ground next to it, got " + snapped,
                    snapped != null && !snapped.equals(box) && snapped.distanceTo2D(box) <= 2);
        });
    }

    @Test
    public void setTargetWithNoGroundNearbyKeepsTheCurrentRoute() throws Exception {
        withCollisionMap(() -> {
            ShortestPathScript script = mock(ShortestPathScript.class);
            Field scriptField = ShortestPathPlugin.class.getDeclaredField("shortestPathScript");
            scriptField.setAccessible(true);
            scriptField.set(plugin, script);

            // Plane 0 under Corsair Cove Dungeon: mapped, and solid rock for far more than 16 tiles.
            clickMap("Some New Area", 2012, 8999);
            MenuEntry entry = mock(MenuEntry.class);
            when(entry.getOption()).thenReturn("Set");
            when(entry.getTarget()).thenReturn(ColorUtil.wrapWithColorTag("Target", JagexColors.MENU_TARGET));
            Method click = ShortestPathPlugin.class.getDeclaredMethod("onMenuOptionClicked", MenuEntry.class);
            click.setAccessible(true);
            click.invoke(plugin, entry);

            verify(script, never()).setTriggerWalker(any());
            verify(script, never()).setTriggerWalker(any(), any());
            verify(client).addChatMessage(eq(ChatMessageType.GAMEMESSAGE), eq(""),
                    contains("No walkable ground near that spot"), eq(""));
        });
    }

    @Test
    public void unmappedDungeonClickIsRefusedWithAMessage() throws Exception {
        when(client.getWorldMap().getWorldMapData().surfaceContainsPosition(anyInt(), anyInt())).thenReturn(false);
        assertNull(clickForTarget("Some New Area", 3116, 9696));
        verify(client).addChatMessage(eq(ChatMessageType.GAMEMESSAGE), eq(""),
                contains("Can't set a target in this dungeon"), eq(""));
    }

    private interface CheckedRunnable {
        void run() throws Exception;
    }

    private static void withCollisionMap(CheckedRunnable body) throws Exception {
        PathfinderConfig previous = ShortestPathPlugin.pathfinderConfig;
        ShortestPathPlugin.pathfinderConfig = new PathfinderConfig(
                SplitFlagMap.fromResources(), new HashMap<>(), Collections.emptyList(), null, null);
        try {
            body.run();
        } finally {
            ShortestPathPlugin.pathfinderConfig = previous;
        }
    }

    private WorldPoint clickForTarget(String mapName, int x, int y) throws Exception {
        clickMap(mapName, x, y);
        Method method = ShortestPathPlugin.class.getDeclaredMethod("getClickedWalkablePoint", String.class);
        method.setAccessible(true);
        return (WorldPoint) method.invoke(plugin, "Target");
    }

    private WorldPoint clickMap(String mapName, int x, int y) throws Exception {
        when(currentMapName.getText()).thenReturn(mapName);
        when(worldMap.getWorldMapPosition()).thenReturn(new Point(x, y));
        Point canvas = plugin.mapWorldPointToGraphicsPoint(new WorldPoint(x, y, 0));
        when(client.getMouseCanvasPosition()).thenReturn(canvas);

        Method method = ShortestPathPlugin.class.getDeclaredMethod("getSelectedWorldPoint");
        method.setAccessible(true);
        return (WorldPoint) method.invoke(plugin);
    }
}
