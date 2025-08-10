package net.runelite.client.plugins.microbot.breakhandler;

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
    WAITING_FOR_BREAK,
    
    /**
     * Break has been requested but waiting for safe conditions.
     * Monitoring for combat/interaction to end before pausing scripts.
     */
    BREAK_REQUESTED,
    
    /**
     * Safe conditions met, initiating break by pausing scripts.
     * Brief transition state.
     */
    INITIATING_BREAK,
    
    /**
     * Logout has been requested and is being attempted.
     * May retry logout if unsuccessful.
     */
    LOGOUT_REQUESTED,
    
    /**
     * Successfully logged out and in break state.
     * Waiting for break duration to complete.
     */
    LOGGED_OUT,
    
    /**
     * Micro break is active - scripts paused but no logout.
     * Used for short antiban breaks.
     */
    MICRO_BREAK_ACTIVE,
    
    /**
     * Break duration completed, requesting login.
     * Attempting to log back in.
     */
    LOGIN_REQUESTED,
    
    /**
     * Currently attempting to log in.
     * May retry if unsuccessful.
     */
    LOGGING_IN,
    
    /**
     * Break ended successfully, resuming normal operation.
     * Brief transition state before returning to WAITING_FOR_BREAK.
     */
    BREAK_ENDING
}
