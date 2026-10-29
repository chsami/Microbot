package net.runelite.client.plugins.microbot.util.player;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.playerstate.Rs2PlayerStateCache;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Rs2PlayerIsInCaveTest {
    private final Map<String, Object> original = new HashMap<>();
    private Rs2PlayerStateCache stateCache;
    private WorldView worldView;

    @Before
    public void setUp() throws Exception {
        Client client = mock(Client.class);
        stateCache = mock(Rs2PlayerStateCache.class);
        worldView = mock(WorldView.class);
        replace("client", client);
        replace("rs2PlayerStateCache", stateCache);
        when(client.getTopLevelWorldView()).thenReturn(worldView);
        when(worldView.isInstance()).thenReturn(false);
    }

    private void replace(String name, Object value) throws Exception {
        Field field = Microbot.class.getDeclaredField(name);
        field.setAccessible(true);
        original.put(name, field.get(null));
        field.set(null, value);
    }

    @After
    public void tearDown() throws Exception {
        for (Map.Entry<String, Object> entry : original.entrySet()) {
            Field field = Microbot.class.getDeclaredField(entry.getKey());
            field.setAccessible(true);
            field.set(null, entry.getValue());
        }
    }

    @Test
    public void nullLocationIsNotInCave() {
        when(stateCache.getLocalPlayerPosition()).thenReturn(null);
        assertFalse(Rs2Player.isInCave());
    }

    @Test
    public void undergroundLocationIsInCave() {
        when(stateCache.getLocalPlayerPosition()).thenReturn(new WorldPoint(3200, 9600, 0));
        assertTrue(Rs2Player.isInCave());
    }

    @Test
    public void surfaceOrInstanceLocationIsNotInCave() {
        when(stateCache.getLocalPlayerPosition()).thenReturn(new WorldPoint(3222, 3218, 0));
        assertFalse(Rs2Player.isInCave());
        when(stateCache.getLocalPlayerPosition()).thenReturn(new WorldPoint(3200, 9600, 0));
        when(worldView.isInstance()).thenReturn(true);
        assertFalse(Rs2Player.isInCave());
    }
}
