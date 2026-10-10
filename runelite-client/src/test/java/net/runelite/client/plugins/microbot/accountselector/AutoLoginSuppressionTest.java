package net.runelite.client.plugins.microbot.accountselector;

import net.runelite.api.GameState;
import org.junit.Test;
import static org.junit.Assert.*;

public class AutoLoginSuppressionTest {
    @Test public void ordinaryLogoutAndWorldHopDoNotSuppressAutoLogin() {
        AutoLoginSuppression suppression = new AutoLoginSuppression();
        for (GameState state : new GameState[] {GameState.LOGGED_IN, GameState.LOADING,
                GameState.HOPPING, GameState.LOGIN_SCREEN, GameState.LOGGING_IN, GameState.LOGGED_IN}) {
            suppression.observe(state);
            assertFalse(suppression.isSuppressed());
        }
    }

    @Test public void restartCanClearSuppressionWithoutManualLogin() {
        AutoLoginSuppression suppression = new AutoLoginSuppression();
        suppression.request();
        suppression.observe(GameState.LOGIN_SCREEN);
        assertTrue(suppression.isSuppressed());
        suppression.clear();
        assertFalse(suppression.isSuppressed());
    }
    @Test public void terminalLogoutRemainsSuppressedUntilManualLoginAfterLogout() {
        AutoLoginSuppression suppression = new AutoLoginSuppression();
        suppression.request();
        suppression.observe(GameState.LOGGED_IN);
        assertTrue(suppression.isSuppressed());
        suppression.observe(GameState.LOGIN_SCREEN);
        suppression.observe(GameState.LOADING);
        assertTrue(suppression.isSuppressed());
        suppression.observe(GameState.LOGGED_IN);
        assertFalse(suppression.isSuppressed());
    }
}
