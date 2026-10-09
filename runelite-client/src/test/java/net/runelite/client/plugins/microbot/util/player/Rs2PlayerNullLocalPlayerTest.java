package net.runelite.client.plugins.microbot.util.player;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import net.runelite.api.Client;
import net.runelite.api.Scene;
import net.runelite.api.WorldView;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class Rs2PlayerNullLocalPlayerTest {
    private final Map<String, Object> original = new HashMap<>();
    private Scene scene;

    @Before
    public void setUp() throws Exception {
        Client client = mock(Client.class);
        ClientThread clientThread = mock(ClientThread.class);
        WorldView worldView = mock(WorldView.class);
        scene = mock(Scene.class);
        replace("client", client);
        replace("clientThread", clientThread);
        when(clientThread.runOnClientThreadOptional(any())).thenAnswer(invocation ->
                Optional.ofNullable(((Callable<?>) invocation.getArgument(0)).call()));
        when(client.getLocalPlayer()).thenReturn(null);
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
    public void worldLocationIsNullWithoutLocalPlayer() {
        when(scene.isInstance()).thenReturn(false);
        assertNull(Rs2Player.getWorldLocation_Internal());
        when(scene.isInstance()).thenReturn(true);
        assertNull(Rs2Player.getWorldLocation_Internal());
    }

    @Test
    public void accessorsReturnNoPlayerDefaultsWithoutLocalPlayer() {
        assertEquals(-1, Rs2Player.getPoseAnimation());
        assertEquals(0, Rs2Player.getCombatLevel());
        assertNull(Rs2Player.getLocalLocation());
        assertEquals(-1, Rs2Player.getGraphicId());
        assertFalse(Rs2Player.hasSpotAnimation(1));
    }
}
