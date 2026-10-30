package net.runelite.client.plugins.microbot.shortestpath;

import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.api.worldmap.WorldMapData;
import net.runelite.client.plugins.microbot.questhelper.util.worldmap.WorldMapArea;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import org.junit.Before;
import org.junit.Test;

import java.awt.Rectangle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
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
