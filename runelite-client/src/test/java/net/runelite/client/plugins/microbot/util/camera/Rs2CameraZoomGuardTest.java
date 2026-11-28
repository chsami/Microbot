package net.runelite.client.plugins.microbot.util.camera;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ScriptID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class Rs2CameraZoomGuardTest {

    private MockedStatic<Microbot> microbot;
    private Client client;

    @Before
    public void setUp() {
        client = mock(Client.class);
        ClientThread clientThread = mock(ClientThread.class);
        doAnswer(inv -> {
            Runnable runnable = inv.getArgument(0);
            runnable.run();
            return null;
        }).when(clientThread).invokeLater(any(Runnable.class));

        microbot = Mockito.mockStatic(Microbot.class);
        microbot.when(Microbot::getClient).thenReturn(client);
        microbot.when(Microbot::getClientThread).thenReturn(clientThread);
    }

    @After
    public void tearDown() {
        microbot.close();
    }

    @Test
    public void setZoomRunsTheZoomScriptWhenLoggedIn() {
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);

        Rs2Camera.setZoom(200);

        verify(client).runScript(ScriptID.CAMERA_DO_ZOOM, 200, 200);
    }

    @Test
    public void setZoomSkipsTheZoomScriptWhileLoading() {
        when(client.getGameState()).thenReturn(GameState.LOADING);

        Rs2Camera.setZoom(200);

        verify(client, never()).runScript(ScriptID.CAMERA_DO_ZOOM, 200, 200);
    }

    @Test
    public void setZoomSkipsTheZoomScriptWhileHopping() {
        when(client.getGameState()).thenReturn(GameState.HOPPING);

        Rs2Camera.setZoom(200);

        verify(client, never()).runScript(ScriptID.CAMERA_DO_ZOOM, 200, 200);
    }

    @Test
    public void setZoomSkipsTheZoomScriptAtTheLoginScreen() {
        when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);

        Rs2Camera.setZoom(200);

        verify(client, never()).runScript(ScriptID.CAMERA_DO_ZOOM, 200, 200);
    }
}
