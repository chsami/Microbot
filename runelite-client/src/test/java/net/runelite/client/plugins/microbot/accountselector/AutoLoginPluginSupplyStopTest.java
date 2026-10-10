package net.runelite.client.plugins.microbot.accountselector;

import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.ScriptStarted;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import org.junit.Test;
import static org.mockito.Mockito.*;

public class AutoLoginPluginSupplyStopTest {
    @Test public void terminalStopAndManualGameStateAreForwarded() {
        AutoLoginPlugin plugin = new AutoLoginPlugin();
        plugin.accountSelectorScript = mock(AutoLoginScript.class);
        plugin.onAutoLoginSuppressionRequest(new AutoLoginSuppressionRequest());
        verify(plugin.accountSelectorScript).suppressAutoLogin();
        GameStateChanged event = new GameStateChanged();
        event.setGameState(GameState.LOGIN_SCREEN);
        plugin.onGameStateChanged(event);
        verify(plugin.accountSelectorScript).observeSuppressionGameState(GameState.LOGIN_SCREEN);
    }

    @Test public void onlyGameplayRunResumesAutoLogin() {
        AutoLoginPlugin plugin = new AutoLoginPlugin();
        plugin.accountSelectorScript = mock(AutoLoginScript.class);
        plugin.onScriptStarted(new ScriptStarted(mock(AutoLoginScript.class)));
        plugin.onScriptStarted(new ScriptStarted(mock(BreakHandlerScript.class)));
        verify(plugin.accountSelectorScript, never()).resumeAutoLogin();
        plugin.onScriptStarted(new ScriptStarted(mock(Script.class)));
        verify(plugin.accountSelectorScript).resumeAutoLogin();
    }
}
