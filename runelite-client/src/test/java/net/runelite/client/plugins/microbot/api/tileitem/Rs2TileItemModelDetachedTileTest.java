package net.runelite.client.plugins.microbot.api.tileitem;

import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Rs2TileItemModelDetachedTileTest {

    @Test
    public void locationAccessorsReturnNullWhenTileWorldViewIsGone() {
        Tile tile = mock(Tile.class);
        when(tile.getWorldLocation()).thenThrow(new NullPointerException("Cannot invoke \"net.runelite.api.WorldView.getBaseX()\" because \"wv\" is null"));
        when(tile.getLocalLocation()).thenThrow(new NullPointerException());
        Rs2TileItemModel model = new Rs2TileItemModel(tile, mock(TileItem.class));

        assertNull(model.getWorldLocation());
        assertNull(model.getLocalLocation());
    }

    @Test
    public void locationAccessorsReturnNullWithoutTile() {
        Rs2TileItemModel model = new Rs2TileItemModel(null, mock(TileItem.class));

        assertNull(model.getWorldLocation());
        assertNull(model.getLocalLocation());
    }

    @Test
    public void locationAccessorsDelegateToTile() {
        Tile tile = mock(Tile.class);
        WorldPoint worldPoint = new WorldPoint(3200, 3200, 0);
        LocalPoint localPoint = new LocalPoint(6400, 6400, -1);
        when(tile.getWorldLocation()).thenReturn(worldPoint);
        when(tile.getLocalLocation()).thenReturn(localPoint);
        Rs2TileItemModel model = new Rs2TileItemModel(tile, mock(TileItem.class));

        assertEquals(worldPoint, model.getWorldLocation());
        assertEquals(localPoint, model.getLocalLocation());
    }
}
