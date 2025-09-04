package net.runelite.client.plugins.microbot.breakhandler;

import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;

/**
 * Enumeration representing the various states of the BreakHandler system.
 * This state machine ensures reliable break transitions and prevents interruption
 * of critical game activities.
 */
public enum BreakHandlerState {
    /**
     * Normal operation state - counting down to the next break.
     * Scripts are running normally.
     */
    WAITING_FOR_BREAK("Waiting for break"),
    
    /**
     * Break has been requested but waiting for safe conditions.
     * Monitoring for combat/interaction to end before pausing scripts.
     */
    BREAK_REQUESTED("Break requested"),
    
    /**
     * Safe conditions met, initiating break by pausing scripts.
     * Brief transition state.
     */
    INITIATING_BREAK("Initiating break"),
    
    /**
     * Logout has been requested and is being attempted.
     * May retry logout if unsuccessful.
     */
    LOGOUT_REQUESTED("Logout requested"),
    
    /**
     * Successfully logged out and in break state.
     * Waiting for break duration to complete.
     */
    LOGGED_OUT("Logged out"),
    
    /**
     * In-game break, used by the micro break system and when user doesn't want to log out - scripts paused but no logout.
     * Used for short antiban breaks.
     */
    INGAME_BREAK_ACTIVE("In-game break active"),
    
    /**
     * Break duration completed, requesting login.
     * Attempting to log back in.
     */
    LOGIN_REQUESTED("Login requested"),
    
    /**
     * Currently attempting to log in.
     * May retry if unsuccessful.
     */
    LOGGING_IN("Logging in"),
    
    /**
     * Extended sleep state after login failures.
     * Used when login watchdog timeout is reached to avoid constant retry attempts.
     */
    LOGIN_EXTENDED_SLEEP("Login extended sleep"),
    
    /**
     * Break ended successfully, resuming normal operation.
     * Brief transition state before returning to WAITING_FOR_BREAK.
     */
    BREAK_ENDING("Break ending");

    private final String stateName;

    BreakHandlerState(String stateName) {
        this.stateName = stateName;
    }

    /**
     * Returns a user-friendly string representation of the state.
     * @return formatted state name
     */
    @Override
    public String toString() {
        if ( this == INGAME_BREAK_ACTIVE) {
            if(BreakHandlerScript.isMicroBreakActive()) {
                return "Micro break active";
            } else {
                return "In-game break active";
            }
        }
        return stateName;
    }
}
