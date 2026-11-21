package net.runelite.client.plugins.microbot.util.player;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Scene;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.playerstate.Rs2PlayerStateCache;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Rs2PlayerDistanceToTest {
    private final Map<String, Object> original = new HashMap<>();
    private Rs2PlayerStateCache stateCache;
    private Scene scene;

    @Before
    public void setUp() throws Exception {
        Client client = mock(Client.class);
        WorldView worldView = mock(WorldView.class);
        stateCache = mock(Rs2PlayerStateCache.class);
        scene = mock(Scene.class);
        replace("client", client);
        replace("rs2PlayerStateCache", stateCache);
        when(client.getTopLevelWorldView()).thenReturn(worldView);
        when(worldView.getScene()).thenReturn(scene);
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
    public void nullLocationInInstanceIsUnknownDistance() {
        when(scene.isInstance()).thenReturn(true);
        when(stateCache.getLocalPlayerPosition()).thenReturn(null);
        assertEquals(Integer.MAX_VALUE, Rs2Player.distanceTo(new WorldPoint(3200, 3200, 0)));
    }

    @Test
    public void nullLocationOutsideInstanceIsUnknownDistance() {
        when(scene.isInstance()).thenReturn(false);
        when(stateCache.getLocalPlayerPosition()).thenReturn(null);
        assertEquals(Integer.MAX_VALUE, Rs2Player.distanceTo(new WorldPoint(3200, 3200, 0)));
    }

    @Test
    public void nullEndpointInInstanceIsUnknownDistance() {
        when(scene.isInstance()).thenReturn(true);
        when(stateCache.getLocalPlayerPosition()).thenReturn(new WorldPoint(3200, 3200, 0));
        assertEquals(Integer.MAX_VALUE, Rs2Player.distanceTo(null));
    }

    @Test
    public void instanceUsesDirectDistance() {
        when(scene.isInstance()).thenReturn(true);
        when(stateCache.getLocalPlayerPosition()).thenReturn(new WorldPoint(3200, 3200, 0));
        assertEquals(5, Rs2Player.distanceTo(new WorldPoint(3205, 3203, 0)));
    }
}
