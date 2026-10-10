package net.runelite.client.plugins.microbot.accountselector;

import net.runelite.api.GameState;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicLong;
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
    @Test public void failedLogoutExpiresWithoutAnyGameStateEvent() {
        AtomicLong clock = new AtomicLong();
        AutoLoginSuppression suppression = new AutoLoginSuppression(clock::get);
        suppression.request();
        clock.set(AutoLoginSuppression.AWAIT_LOGOUT_MILLIS - 1);
        assertTrue(suppression.isSuppressed());
        clock.incrementAndGet();
        assertFalse(suppression.isSuppressed());
    }

    @Test public void laterUnrelatedLogoutDoesNotConsumeExpiredRequest() {
        AtomicLong clock = new AtomicLong();
        AutoLoginSuppression suppression = new AutoLoginSuppression(clock::get);
        suppression.request();
        clock.set(AutoLoginSuppression.AWAIT_LOGOUT_MILLIS);
        suppression.observe(GameState.LOGIN_SCREEN);
        assertFalse(suppression.isSuppressed());
    }

    @Test public void delayedLogoutWithinDeadlineRemainsSuppressedIndefinitely() {
        AtomicLong clock = new AtomicLong();
        AutoLoginSuppression suppression = new AutoLoginSuppression(clock::get);
        suppression.request();
        clock.set(AutoLoginSuppression.AWAIT_LOGOUT_MILLIS - 1);
        suppression.observe(GameState.LOGIN_SCREEN);
        clock.set(AutoLoginSuppression.AWAIT_LOGOUT_MILLIS * 100);
        suppression.observe(GameState.LOGIN_SCREEN);
        assertTrue(suppression.isSuppressed());
        suppression.observe(GameState.LOGGED_IN);
        assertFalse(suppression.isSuppressed());
    }

    @Test public void repeatedPendingRequestsDoNotExtendDeadline() {
        AtomicLong clock = new AtomicLong();
        AutoLoginSuppression suppression = new AutoLoginSuppression(clock::get);
        suppression.request();
        clock.set(AutoLoginSuppression.AWAIT_LOGOUT_MILLIS - 1);
        suppression.request();
        clock.incrementAndGet();
        assertFalse(suppression.isSuppressed());
    }

    @Test public void newRequestAfterExpiryGetsItsOwnDeadline() {
        AtomicLong clock = new AtomicLong();
        AutoLoginSuppression suppression = new AutoLoginSuppression(clock::get);
        suppression.request();
        clock.set(AutoLoginSuppression.AWAIT_LOGOUT_MILLIS);
        suppression.request();
        assertTrue(suppression.isSuppressed());
        clock.set(AutoLoginSuppression.AWAIT_LOGOUT_MILLIS * 2);
        assertFalse(suppression.isSuppressed());
    }
}
