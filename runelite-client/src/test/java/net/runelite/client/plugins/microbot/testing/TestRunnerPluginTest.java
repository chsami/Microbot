package net.runelite.client.plugins.microbot.testing;

import net.runelite.api.GameState;
import net.runelite.client.plugins.PluginDescriptor;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestRunnerPluginTest {

    @Test
    public void runnerStartsBeforePersistedTestTargets() {
        PluginDescriptor descriptor = TestRunnerPlugin.class.getAnnotation(PluginDescriptor.class);
        assertTrue(descriptor.alwaysOn());
        assertTrue(descriptor.priority());
    }

    @Test
    public void loggedInStateAloneIsNotPlayable() {
        assertFalse(TestRunnerPlugin.isClientReady(GameState.LOGGED_IN, false, false));
    }

    @Test
    public void welcomeScreenBlocksTargetPluginStartup() {
        assertFalse(TestRunnerPlugin.isClientReady(GameState.LOGGED_IN, true, true));
    }

    @Test
    public void loggedInPlayerWithoutWelcomeScreenIsPlayable() {
        assertTrue(TestRunnerPlugin.isClientReady(GameState.LOGGED_IN, true, false));
    }

    @Test
    public void localPlayerDoesNotMakeOtherGameStatesPlayable() {
        assertFalse(TestRunnerPlugin.isClientReady(GameState.LOGIN_SCREEN, true, false));
        assertFalse(TestRunnerPlugin.isClientReady(GameState.LOADING, true, false));
        assertFalse(TestRunnerPlugin.isClientReady(GameState.HOPPING, true, false));
    }
}
