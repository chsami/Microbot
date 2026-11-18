package net.runelite.client.plugins.microbot.util.coords;

import net.runelite.api.Client;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Rs2WorldPointStaleCheckpointTileTest
{
    private static final int BASE_X = 3136;
    private static final int BASE_Y = 3136;
    private static final int SIZE = 104;
    private static final WorldPoint SOURCE = new WorldPoint(BASE_X + 50, BASE_Y + 50, 0);
    private static final WorldPoint MIDDLE = new WorldPoint(BASE_X + 53, BASE_Y + 50, 0);
    private static final WorldPoint TARGET = new WorldPoint(BASE_X + 55, BASE_Y + 50, 0);

    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Tile> rs2Tile;
    private Tile sourceTile;
    private Tile targetTile;

    @Before
    public void setUp()
    {
        sourceTile = mock(Tile.class);
        targetTile = mock(Tile.class);

        Tile[][][] tiles = new Tile[4][SIZE][SIZE];
        tiles[0][50][50] = sourceTile;
        tiles[0][55][50] = targetTile;

        Scene scene = mock(Scene.class);
        when(scene.getTiles()).thenReturn(tiles);

        WorldView worldView = mock(WorldView.class);
        when(worldView.getBaseX()).thenReturn(BASE_X);
        when(worldView.getBaseY()).thenReturn(BASE_Y);
        when(worldView.getSizeX()).thenReturn(SIZE);
        when(worldView.getSizeY()).thenReturn(SIZE);
        when(worldView.getId()).thenReturn(-1);
        when(worldView.getScene()).thenReturn(scene);

        Client client = mock(Client.class);
        when(client.getTopLevelWorldView()).thenReturn(worldView);

        microbot = Mockito.mockStatic(Microbot.class);
        microbot.when(Microbot::getClient).thenReturn(client);

        rs2Tile = Mockito.mockStatic(Rs2Tile.class);
    }

    @After
    public void tearDown()
    {
        rs2Tile.close();
        microbot.close();
    }

    @Test
    public void pathToReturnsNullWhenACheckpointTileHasLostItsWorldView()
    {
        Tile resolved = mock(Tile.class);
        when(resolved.getWorldLocation()).thenReturn(MIDDLE);
        Tile stale = mock(Tile.class);
        when(stale.getWorldLocation()).thenThrow(new NullPointerException("wv"));
        List<Tile> checkpoints = Arrays.asList(resolved, stale);
        rs2Tile.when(() -> Rs2Tile.pathTo(sourceTile, targetTile)).thenReturn(checkpoints);
        rs2Tile.when(() -> Rs2Tile.fullPathTo(sourceTile, targetTile)).thenReturn(checkpoints);

        assertNull(new Rs2WorldPoint(SOURCE).pathTo(TARGET));
        assertNull(new Rs2WorldPoint(SOURCE).pathTo(TARGET, true));
        assertEquals(Integer.MAX_VALUE, new Rs2WorldPoint(SOURCE).distanceToPath(TARGET));
    }

    @Test
    public void pathToReturnsNullWhenACheckpointTileHasNoWorldLocation()
    {
        Tile unresolved = mock(Tile.class);
        rs2Tile.when(() -> Rs2Tile.pathTo(sourceTile, targetTile)).thenReturn(Arrays.asList(unresolved));

        assertNull(new Rs2WorldPoint(SOURCE).pathTo(TARGET));
    }

    @Test
    public void pathToStillReturnsEveryCheckpointWhenAllTilesResolve()
    {
        Tile first = mock(Tile.class);
        when(first.getWorldLocation()).thenReturn(MIDDLE);
        Tile second = mock(Tile.class);
        when(second.getWorldLocation()).thenReturn(TARGET);
        rs2Tile.when(() -> Rs2Tile.pathTo(sourceTile, targetTile)).thenReturn(Arrays.asList(first, second));

        assertEquals(Arrays.asList(MIDDLE, TARGET), new Rs2WorldPoint(SOURCE).pathTo(TARGET));
        assertEquals(5, new Rs2WorldPoint(SOURCE).distanceToPath(TARGET));
    }
}
