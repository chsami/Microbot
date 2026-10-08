package net.runelite.client.plugins.microbot.api.player;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.player.models.Rs2PlayerModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class Rs2PlayerModelNullLocalPlayerTest {
    private final Map<String, Object> original = new HashMap<>();
    private ClientThread clientThread;

    @Before
    public void setUp() throws Exception {
        Client client = mock(Client.class);
        clientThread = mock(ClientThread.class);
        replace("client", client);
        replace("clientThread", clientThread);
        when(client.getLocalPlayer()).thenReturn(null);
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
    public void positionAccessorsReturnNullWithoutLocalPlayer() {
        Rs2PlayerModel player = new Rs2PlayerModel();
        assertNull(player.getWorldLocation());
        assertNull(player.getWorldView());
        assertNull(player.getLocalLocation());
        assertNull(player.projectActorLocationToMainWorld());
        verifyNoInteractions(clientThread);
    }
}
