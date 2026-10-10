package net.runelite.client.plugins.microbot.accountselector;

import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.GameState;

/** Tracks the requested logout separately from a subsequent manual login. */
final class AutoLoginSuppression {
    private enum State { ALLOWED, AWAITING_LOGOUT, LOGGED_OUT }

    private final AtomicReference<State> state = new AtomicReference<>(State.ALLOWED);

    void request() {
        state.compareAndSet(State.ALLOWED, State.AWAITING_LOGOUT);
    }

    void observe(GameState gameState) {
        state.updateAndGet(current -> {
            if (current == State.AWAITING_LOGOUT && gameState == GameState.LOGIN_SCREEN) {
                return State.LOGGED_OUT;
            }
            if (current == State.LOGGED_OUT && gameState == GameState.LOGGED_IN) {
                return State.ALLOWED;
            }
            return current;
        });
    }

    boolean isSuppressed() {
        return state.get() != State.ALLOWED;
    }

    void clear() {
        state.set(State.ALLOWED);
    }
}
