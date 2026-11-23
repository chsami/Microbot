package net.runelite.client.plugins.microbot;

import java.util.Optional;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.util.antiban.SessionFatigue;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class ScriptGameReadyTest {
    private static final WorldPoint LUMBRIDGE = new WorldPoint(3222, 3218, 0);
    private final ConfigManager config = mock(ConfigManager.class);

    @Test public void loggedOutSkipsTick() {
        assertFalse(run(new Script() { }, false, null));
    }

    @Test public void loggedInWithoutPositionSkipsTick() {
        assertFalse(run(new Script() { }, true, null));
    }

    @Test public void loggedInWithPositionRuns() {
        assertTrue(run(new Script() { }, true, LUMBRIDGE));
        verify(config, atLeastOnce()).setConfiguration(any(), any(), any(Object.class));
    }

    @Test public void skippedTickLeavesRunAndConfigAlone() {
        run(new Script() { }, true, null);
        verifyNoInteractions(config);
    }

    @Test public void optOutRunsWhileLoggedOut() {
        Script script = new Script() {
            @Override protected boolean runsWhileLoggedOut() {
                return true;
            }
        };
        assertTrue(run(script, false, null));
    }

    private boolean run(Script script, boolean loggedIn, WorldPoint position) {
        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.runOnClientThreadOptional(any())).thenReturn(Optional.empty());
        BlockingEventManager blocking = mock(BlockingEventManager.class);
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class, CALLS_REAL_METHODS);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<SessionFatigue> fatigue = mockStatic(SessionFatigue.class)) {
            microbot.when(Microbot::isLoggedIn).thenReturn(loggedIn);
            microbot.when(Microbot::getEventBus).thenReturn(null);
            microbot.when(Microbot::getBlockingEventManager).thenReturn(blocking);
            microbot.when(Microbot::getClientThread).thenReturn(clientThread);
            microbot.when(Microbot::getConfigManager).thenReturn(config);
            player.when(Rs2Player::hasCompletedTutorialIsland).thenReturn(true);
            player.when(Rs2Player::getWorldLocation).thenReturn(position);
            fatigue.when(SessionFatigue::isActive).thenReturn(true);
            return script.run();
        } finally {
            script.shutdown();
            script.scheduledExecutorService.shutdownNow();
        }
    }
}
