package net.runelite.client.plugins.microbot.breakhandler;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.discord.Rs2Discord;
import net.runelite.client.plugins.microbot.util.events.PluginPauseEvent;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2PlayerModel;
import net.runelite.client.plugins.microbot.util.security.LoginManager;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.world.Rs2WorldUtil;
import net.runelite.client.ui.ClientUI;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.GameState;

/**
 * Enhanced BreakHandlerScript with state-based break management for reliable automation.
 * 
 * This script provides comprehensive break handling with proper state transitions, combat/interaction
 * monitoring, and reliable login/logout sequences. It prevents interruption of critical activities
 * and ensures safe break execution.
 * 
 * Key Features:
 * - State-based break management with proper transitions
 * - Combat and interaction detection before pausing scripts
 * - Reliable logout/login sequences with retry mechanisms
 * - Integration with micro-break antiban system
 * - Lock state management for preventing breaks during critical operations
 * - Play schedule enforcement with automatic logout
 * - Configurable retry delays and timeouts
 * 
 * State Flow:
 * WAITING_FOR_BREAK -> BREAK_REQUESTED -> INITIATING_BREAK -> LOGOUT_REQUESTED -> 
 * LOGGED_OUT -> LOGIN_REQUESTED -> LOGGING_IN -> BREAK_ENDING -> WAITING_FOR_BREAK
 * 
 * Micro breaks follow: WAITING_FOR_BREAK -> BREAK_REQUESTED -> INGAME_BREAK_ACTIVE -> BREAK_ENDING
 * 
 * @version 2.0.0
 */
@Slf4j
public class BreakHandlerScript extends Script {
    public static String version = "2.1.0";
    
    // ban detection constants  
    private static final int BANNED_LOGIN_INDEX = 14;
    
    // ban detection state
    public static boolean isBanned = false;
    private static String lastKnownPlayerName = "";
    private boolean wasLoggedIn = false;
    
    // Constants for configuration and timing
    private static final int SCHEDULER_INTERVAL_MS = 1000;
    private static final int MINUTES_TO_SECONDS = 60;
    
    // Configuration-dependent timing values (accessed through helper methods)
    private int getCombatCheckIntervalMs() {
        return config.combatCheckInterval() * 1000;
    }
    
    private int getLogoutRetryDelayMs() {
        return config.logoutRetryDelay() * 1000;
    }
    
    private int getLoginRetryDelayMs() {
        return config.loginRetryDelay() * 1000;
    }
    
    private int getMaxLogoutRetries() {
        return config.maxLogoutRetries();
    }
    
    private int getMaxLoginRetries() {
        return config.maxLoginRetries();
    }
    
    private int getSafeConditionTimeoutMs() {
        return config.safeConditionTimeout() * 60 * 1000; // Convert minutes to milliseconds
    }

    // Core break timing variables
    public static int breakIn = -1;
    public static int breakDuration = -1;
    public static Duration setBreakDurationTime = Duration.ZERO;
    public static int totalBreaks = 0;
    
    // State management - Thread-safe using atomic references
    private static final AtomicReference<BreakHandlerState> currentState = new AtomicReference<>(BreakHandlerState.WAITING_FOR_BREAK);
    private static final AtomicReference<Instant> stateChangeTime = new AtomicReference<>(Instant.now());
    private static final AtomicInteger retryCount = new AtomicInteger(0);
    private static Instant lastCombatCheckTime = Instant.now();
    private static Instant safeConditionWaitStartTime = null;
    private static volatile Instant loginWatchdogStartTime = null;
    @Getter
    private static volatile Instant extendedSleepStartTime = null;
    
    // Lock state management
    public static AtomicBoolean lockState = new AtomicBoolean(false);
    
    /**
     * Sets the manual lock state and logs the change.
     * When locked, breaks are prevented from starting.
     */
    public static void setLockState(boolean state) {
        boolean currentState = BreakHandlerScript.lockState.get();
        if (currentState != state) {
            BreakHandlerScript.lockState.set(state);
            log.debug("Break handler lock state changed: {} -> {}", currentState, state);
        }
    }
    
    // UI and configuration
    private String originalWindowTitle = "";
    private BreakHandlerConfig config;

    // Track world state across breaks
    private int preBreakWorld = -1;
    private boolean loggedOutDuringBreak = false;

    /**
     * Checks if a break is currently active (any break state except waiting).
     */
    public static boolean isBreakActive() {
        return currentState.get() != BreakHandlerState.WAITING_FOR_BREAK;
    }
    
    /**
     * Checks if a micro break is currently active.
     */
    public static boolean isIngameBreakActive() {
        BreakHandlerState state = currentState.get();
        return state == BreakHandlerState.INGAME_BREAK_ACTIVE ||
               (Rs2AntibanSettings.takeMicroBreaks && Rs2AntibanSettings.microBreakActive);
    }
    public static boolean isMicroBreakActive() {
        return Rs2AntibanSettings.takeMicroBreaks && Rs2AntibanSettings.microBreakActive;
    }
    /**
     * Gets the current break handler state.
     */
    public static BreakHandlerState getCurrentState() {
        return currentState.get();
    }

    /**
     * Formats a duration with header text.
     */
    public static String formatDuration(Duration duration, String header) {
        return String.format(header + " %s", formatDuration(duration));
    }

    /**
     * Formats a duration into HH:MM:SS format.
     */
    public static String formatDuration(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            return "00:00:00";
        }
        long hours = duration.toHours();
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.getSeconds() % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    /**
     * Main entry point for the break handler script.
     */
    public boolean run(BreakHandlerConfig config) {
        this.config = config;        
        originalWindowTitle = ClientUI.getFrame().getTitle();        
        // Initialize state and timing
        currentState.set(BreakHandlerState.WAITING_FOR_BREAK);
        stateChangeTime.set(Instant.now());
        retryCount.set(0);        
        initializeNextBreakTimer();        
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                super.run();
                
                // check for ban detection first
                checkForBan();
                updatePlayerNameCache();
                
                // only continue with break handling if not banned
                if (!isBanned) {
                    processBreakHandlerStateMachine();
                }
            } catch (Exception ex) {
                log.error("Error in break handler main loop", ex);
            }
        }, 0, SCHEDULER_INTERVAL_MS, TimeUnit.MILLISECONDS);
        
        return true;
    }

    /**
     * Main state machine processor - handles all break-related state transitions.
     */
    private void processBreakHandlerStateMachine() {
        // Handle immediate user config toggles first
        if (handleConfigToggles()) {
            return;
        }        
        // Process current state
        BreakHandlerState state = currentState.get();
        switch (state) {
            case WAITING_FOR_BREAK:
                handleWaitingForBreakState();
                break;
            case BREAK_REQUESTED:
                handleBreakRequestedState();
                break;
            case INITIATING_BREAK:
                handleInitiatingBreakState();
                break;
            case LOGOUT_REQUESTED:
                handleLogoutRequestedState();
                break;
            case LOGGED_OUT:
                handleLoggedOutState();
                break;
            case INGAME_BREAK_ACTIVE:
                handleLoginBreakActiveState();
                break;
            case LOGIN_REQUESTED:
                handleLoginRequestedState();
                break;
            case LOGGING_IN:
                handleLoggingInState();
                break;
            case LOGIN_EXTENDED_SLEEP:
                handleLoginExtendedSleepState();
                break;
            case BREAK_ENDING:
                handleBreakEndingState();
                break;
        }
        
        // Update UI regardless of state
        updateBreakTimers();
        updateWindowTitle();
    }

    /**
     * Handles immediate config toggle requests (breakNow, breakEndNow).
     */
    private boolean handleConfigToggles() {
        BreakHandlerState state = currentState.get();
        
        if (config.breakNow() && state == BreakHandlerState.WAITING_FOR_BREAK && !isLockState()) {
            log.info("Manual break requested");
            transitionToState(BreakHandlerState.BREAK_REQUESTED);
            resetConfigToggles();
            return true;
        }

        if (config.breakEndNow() && isBreakActive() && state != BreakHandlerState.BREAK_ENDING && state != BreakHandlerState.LOGGING_IN) {
            log.info("Manual break end requested");
            if (state == BreakHandlerState.LOGGED_OUT || state == BreakHandlerState.INGAME_BREAK_ACTIVE) {
                if (state == BreakHandlerState.LOGGED_OUT){
                    transitionToState(BreakHandlerState.LOGIN_REQUESTED);
                }else if (state == BreakHandlerState.INGAME_BREAK_ACTIVE) {
                    transitionToState(BreakHandlerState.BREAK_ENDING);
                }
                resetConfigToggles();
                return true;
            }         
        }
        
        return false;
    }

    /**
     * State: WAITING_FOR_BREAK
     * Normal operation - counting down to next break and checking for break conditions.
     */
    private void handleWaitingForBreakState() {
        // Handle play schedule logic
        if (config.playSchedule().isOutsideSchedule() && config.usePlaySchedule() && !isLockState()) {
            log.info("Outside play schedule, requesting break");
            transitionToState(BreakHandlerState.BREAK_REQUESTED);
            return;
        }
        
        // Check for normal break conditions
        boolean normalBreakTime = (breakIn <= 0 && !isLockState()) && !config.onlyMicroBreaks();
        boolean microBreakInitiated = Rs2AntibanSettings.microBreakActive && !isLockState();
        
        if (normalBreakTime || microBreakInitiated) {
            log.info("Break time reached \n\t- Normal: {}, Micro: {}", normalBreakTime, microBreakInitiated);
            transitionToState(BreakHandlerState.BREAK_REQUESTED);
        }
    }

    /**
     * State: BREAK_REQUESTED
     * Break should start but waiting for safe conditions (not in combat/interacting).
     */
    private void handleBreakRequestedState() {
        // Check for timeout on waiting for safe conditions
        if (safeConditionWaitStartTime != null) {
            long waitTime = Duration.between(safeConditionWaitStartTime, Instant.now()).toMillis();
            if (waitTime > getSafeConditionTimeoutMs()) {
                // Check if client shutdown is configured for timeout scenarios
                if (config.shutdownClient()) {
                    log.warn("Timeout waiting for safe conditions with client shutdown enabled - shutting down RuneLite client");
                    // Pause all scripts before shutdown
                    Microbot.pauseAllScripts.compareAndSet(false, true);
                    PluginPauseEvent.setPaused(true);
                    handleClientShutdown();
                    return;
                } else {
                    log.warn("Timeout waiting for safe conditions, skipping this break and waiting for next one");
                    // Skip this break and wait for the next one
                    initializeNextBreakTimer();
                    transitionToState(BreakHandlerState.WAITING_FOR_BREAK);
                    return;
                }
            }
        }
        
        // Check for safe conditions periodically
        Instant now = Instant.now();
        if (safeConditionWaitStartTime==null || Duration.between(lastCombatCheckTime, now).toMillis() >= getCombatCheckIntervalMs()) {
            lastCombatCheckTime = now;
            if(safeConditionWaitStartTime == null) {
                safeConditionWaitStartTime = now;
            }
            if (isSafeToBreak()) {
                log.debug("Safe conditions met, initiating break");
                transitionToState(BreakHandlerState.INITIATING_BREAK);
            } else {
                log.debug("Waiting for safe conditions - Combat: {}, Interacting: {}", 
                         Rs2Player.isInCombat(), Rs2Player.isInteracting());
            }
        }
    }

    /**
     * State: INITIATING_BREAK
     * Safe to pause scripts, starting break process.
     */
    private void handleInitiatingBreakState() {
        log.debug("Initiating break - pausing all scripts");
        
        // Pause all scripts
        Microbot.pauseAllScripts.compareAndSet(false, true);
        PluginPauseEvent.setPaused(true);
        Rs2Walker.setTarget(null);

        // Remember the world we were in before the break
        preBreakWorld = Microbot.getClient().getWorld();

        // Determine next state based on break type
        setBreakDuration();
        if ((Rs2AntibanSettings.microBreakActive && config.onlyMicroBreaks())) {            
            transitionToState(BreakHandlerState.INGAME_BREAK_ACTIVE);
        } else if (!shouldLogout()){              
            transitionToState(BreakHandlerState.INGAME_BREAK_ACTIVE);
        }else{      
            transitionToState(BreakHandlerState.LOGOUT_REQUESTED);
        }
    }

    /**
     * Handles client shutdown when configured to shutdown the entire client during breaks.
     * This will completely close the RuneLite client.
     */
    private void handleClientShutdown() {
        try {
            log.warn("Shutting down RuneLite client due to break handler configuration");
            
            // Update break statistics before shutdown
            updateBreakStatistics();
            
            // Clean shutdown of the client
            ClientUI.getFrame().setTitle(originalWindowTitle + " - Shutting Down");
            if (scheduledFuture != null && !scheduledFuture.isDone()) {
                scheduledFuture.cancel(true);
            }
            // Schedule shutdown to allow logging to complete
            scheduledFuture = scheduledExecutorService.schedule(() -> {
                System.exit(0);
            }, 1000, TimeUnit.MILLISECONDS);
            
        } catch (Exception ex) {
            log.error("Error during client shutdown", ex);
            // Force shutdown if graceful shutdown fails
            System.exit(1);
        }
    }

    /**
     * State: LOGOUT_REQUESTED
     * Attempting to logout, with retry logic.
     */
    private void handleLogoutRequestedState() {
        if (!Microbot.isLoggedIn()) {     
            retryCount.set(0);
            log.debug("Logout successful");                    
            transitionToState(BreakHandlerState.LOGGED_OUT);
            return;
        }
        int currentRetryCount = retryCount.get();
        Instant currentStateChangeTime = stateChangeTime.get();
        
        if (currentRetryCount >= getMaxLogoutRetries()) {
            log.warn("Max logout retries reached, continuing with logged-in break");
            transitionToState(BreakHandlerState.INGAME_BREAK_ACTIVE);
            return;
        }
        
        // Check if enough time has passed for retry
        if (currentRetryCount > 0 && Duration.between(currentStateChangeTime, Instant.now()).toMillis() < getLogoutRetryDelayMs()) {
            long remainingTime = getLogoutRetryDelayMs() - Duration.between(currentStateChangeTime, Instant.now()).toMillis();
            log.debug("Waiting for next logout retry ({} ms remaining)", remainingTime);
            return;
        }        
        log.info("Attempting logout (attempt {}/{})", currentRetryCount + 1, getMaxLogoutRetries());
        try {
            Rs2Player.logout();
            // Don't immediately transition - wait for next cycle to check if logout was successful
            retryCount.incrementAndGet();
            stateChangeTime.set(Instant.now());                      
            // Check on next cycle if we successfully logged out           
            
        } catch (Exception ex) {
            log.error("Error during logout attempt", ex);
            retryCount.incrementAndGet();
            stateChangeTime.set(Instant.now());
        }
    }

    /**
     * State: LOGGED_OUT
     * Successfully logged out, waiting for break duration to complete.
     */
    private void handleLoggedOutState() {
        if(!Microbot.isLoggedIn()) {
            // Check if break should end
            if (breakDuration <= 0 || config.breakEndNow()) {
                log.debug("Break duration completed, requesting login");
                transitionToState(BreakHandlerState.LOGIN_REQUESTED);
            }
        }else{
            log.error("Unexpected state: Logged in while in LOGGED_OUT state. Resetting state.");
            if (breakDuration <= 0 || config.breakEndNow()){
                // Reset state to waiting for break if logged in unexpectedly
                transitionToState(BreakHandlerState.BREAK_ENDING);
            } else {
                resetBreakState();
            }
        }
    }

    /**
     * State: INGAME_BREAK_ACTIVE
     * In micro break state (no logout), waiting for duration to complete.
     */
    private void handleLoginBreakActiveState() {
        // Check if micro break should end
        if ((breakDuration <= 0 && !Rs2AntibanSettings.microBreakActive) || config.breakEndNow()) {
            log.debug("Micro break completed");
            transitionToState(BreakHandlerState.BREAK_ENDING);
        }
    }

    /**
     * State: LOGIN_REQUESTED
     * Break ended, attempting to login with intelligent world selection and watchdog.
     */
    private void handleLoginRequestedState() {
        if (Microbot.isLoggedIn()) {
            log.debug("Already logged in, proceeding to break ending");
            loginWatchdogStartTime = null; // reset watchdog
            transitionToState(BreakHandlerState.BREAK_ENDING);
            return;
        }
        
        // initialize login watchdog timer
        if (loginWatchdogStartTime == null) {
            loginWatchdogStartTime = Instant.now();
            log.debug("Login watchdog started for {} minutes", config.loginWatchdogTimeout());
        }
        
        boolean membersOnly = config.membersOnly();
        log.info("Attempting intelligent login with world selection");
        try {
            int targetWorld = -1;                       
            // use world selection mode if no last world or last world not accessible
            
            switch (config.worldSelectionMode()) {
                case CURRENT_PREFERRED_WORLD:
                    if (preBreakWorld != -1) {
                        boolean isAccessible = Rs2WorldUtil.canAccessWorld(preBreakWorld);
                        if (isAccessible) {
                            targetWorld = preBreakWorld;
                            log.info("Using last world before break: {}", targetWorld);
                        } else {
                            log.warn("Last world {} is not accessible, falling back to world selection mode", preBreakWorld);
                        }
                    }
        
                    // no specific world selection - use default login
                    break;
                    
                case RANDOM_WORLD:
                    targetWorld = Rs2WorldUtil.getRandomAccessibleWorldFromRegion(
                        config.regionPreference().getWorldRegion(),
                        config.avoidEmptyWorlds(),
                        config.avoidOvercrowdedWorlds(),
                        membersOnly);
                    break;
                    
                case BEST_POPULATION:
                    targetWorld = Rs2WorldUtil.getBestAccessibleWorldForLogin(
                        false,
                        config.regionPreference().getWorldRegion(),
                        config.avoidEmptyWorlds(),
                        config.avoidOvercrowdedWorlds(),
                        membersOnly
                        );
                    break;
                    
                case BEST_PING:
                    targetWorld = Rs2WorldUtil.getBestAccessibleWorldForLogin(
                        true,
                        config.regionPreference().getWorldRegion(),
                        config.avoidEmptyWorlds(),
                        config.avoidOvercrowdedWorlds(),
                        membersOnly
                        );
                    break;
                    
                case REGIONAL_RANDOM:
                    targetWorld = Rs2WorldUtil.getRandomAccessibleWorldFromRegion(
                        config.regionPreference().getWorldRegion(),
                        config.avoidEmptyWorlds(),
                        config.avoidOvercrowdedWorlds(),
                        membersOnly
                        );
                    break;
                    
                default:
                    // fallback to current world
                    break;
                }
        
            
            // perform login attempt
            boolean loginInitiated;
            if (targetWorld != -1) {
                log.info("Attempting login to selected world: {}", targetWorld);
                loginInitiated = LoginManager.login(targetWorld);
            } else {
                log.info("Using default login (current world or last used)");
                loginInitiated = LoginManager.login();
            }

            if (!loginInitiated) {
                log.debug("Login manager rejected new attempt (gameState: {}, attemptActive: {})",
                    LoginManager.getGameState(), LoginManager.isLoginAttemptActive());
            }

            // immediately transition to logging in state to prevent multiple login instances
            transitionToState(BreakHandlerState.LOGGING_IN);
            
        } catch (Exception ex) {
            log.error("Error initiating login", ex);
            // still transition to prevent getting stuck in login requested state
            transitionToState(BreakHandlerState.LOGGING_IN);
        }
    }

    /**
     * State: LOGGING_IN
     * Currently attempting to log in, with retry logic and watchdog timeout.
     */
    private void handleLoggingInState() {
        if (Microbot.isLoggedIn()) {
            log.info("Login successful");
            loginWatchdogStartTime = null; // reset watchdog
            retryCount.set(0);
            transitionToState(BreakHandlerState.BREAK_ENDING);
            return;
        }
        
        // check login watchdog timeout
        if (loginWatchdogStartTime != null) {
            long watchdogTime = Duration.between(loginWatchdogStartTime, Instant.now()).toMinutes();
            if (watchdogTime >= config.loginWatchdogTimeout()) {
                log.warn("Login watchdog timeout reached after {} minutes, entering extended sleep", watchdogTime);
                loginWatchdogStartTime = null; // reset watchdog
                extendedSleepStartTime = Instant.now();
                transitionToState(BreakHandlerState.LOGIN_EXTENDED_SLEEP);
                return;
            }
        }
        
        // Get current values atomically
        int currentRetryCount = retryCount.get();
        Instant currentStateChangeTime = stateChangeTime.get();
        
        // Check for timeout or max retries
        long loginTime = Duration.between(currentStateChangeTime, Instant.now()).toMillis();
        if (loginTime > (getLoginRetryDelayMs() * (currentRetryCount + 1)) && currentRetryCount < getMaxLoginRetries()) {
            log.info("Login retry {} of {}", currentRetryCount + 1, getMaxLoginRetries());
            retryCount.incrementAndGet();
            transitionToState(BreakHandlerState.LOGIN_REQUESTED);
        } else if (currentRetryCount >= getMaxLoginRetries()) {
            log.warn("Max login retries reached for this attempt, returning to login request");
            // Reset retry count and return to login request to try again
            retryCount.set(0);
            transitionToState(BreakHandlerState.LOGIN_REQUESTED);
        }
    }
    
    /**
     * State: LOGIN_EXTENDED_SLEEP
     * Extended sleep state after login watchdog timeout to avoid constant retry attempts.
     */
    private void handleLoginExtendedSleepState() {
        if (Microbot.isLoggedIn()) {
            log.info("Login successful during extended sleep, proceeding to break ending");
            extendedSleepStartTime = null; // reset extended sleep timer
            transitionToState(BreakHandlerState.BREAK_ENDING);
            return;
        }
        
        if (extendedSleepStartTime == null) {
            extendedSleepStartTime = Instant.now();
            log.info("Extended sleep started for {} minutes", config.extendedSleepDuration());
            return;
        }
        
        // check if extended sleep period is complete
        long sleepTime = Duration.between(extendedSleepStartTime, Instant.now()).toMinutes();
        if (sleepTime >= config.extendedSleepDuration()) {
            log.info("Extended sleep period completed after {} minutes, resuming login attempts", sleepTime);
            extendedSleepStartTime = null; // reset extended sleep timer
            retryCount.set(0); // reset retry count for fresh attempt
            transitionToState(BreakHandlerState.LOGIN_REQUESTED);
        } else {
            // still in extended sleep - show progress occasionally
            if (sleepTime % 5 == 0) { // every 5 minutes
                long remaining = config.extendedSleepDuration() - sleepTime;
                log.debug("Extended sleep in progress - {} minutes remaining", remaining);
            }
        }
    }

    /**
     * State: BREAK_ENDING
     * Break ended successfully, cleaning up and resuming normal operation.
     */
    private void handleBreakEndingState() {
        log.info("Break ending, resuming normal operation");
        
        // Resume scripts and reset state
        resumeFromBreak();
        
        // Note: World selection is now handled during login in handleLoginRequestedState()
        // No need for separate world switching here
        
        // Update statistics and UI
        updateBreakStatistics();
        resetWindowTitle();
        
        // Clean up break state
        cleanupBreakState();
        
        // Reset config toggles
        resetConfigToggles();
        
        // Return to waiting state
        initializeNextBreakTimer();
        transitionToState(BreakHandlerState.WAITING_FOR_BREAK);
    }

    /**
     * Transitions to a new state and resets relevant counters.
     * This method is thread-safe and can be called from any thread.
     */
    private static void transitionToState(BreakHandlerState newState) {
        BreakHandlerState oldState = currentState.get();
        if (oldState != newState) {
            log.debug("State transition: {} -> {}", oldState, newState);
            currentState.set(newState);
            stateChangeTime.set(Instant.now());
            retryCount.set(0);
            
            // Reset safe condition wait time when leaving BREAK_REQUESTED
            if (newState != BreakHandlerState.BREAK_REQUESTED) {
                safeConditionWaitStartTime = null;
            }
        }
    }

    /**
     * Checks if it's safe to break (not in combat, not interacting).
     */
    private boolean isSafeToBreak() {
        return !Rs2Player.isInCombat() && !Rs2Player.isInteracting();
    }

    /**
     * Determines if logout should occur based on configuration and conditions.
     */
    private boolean shouldLogout() {
        // Only attempt to logout during a normal break. When a micro break is
        // active we should remain logged in regardless of the logout setting.
        return !Rs2AntibanSettings.microBreakActive &&
            (isOutsidePlaySchedule() || config.logoutAfterBreak());
    }

    /**
     * Initializes the timer for the next break.
     */
    private void initializeNextBreakTimer() {
        breakIn = Rs2Random.between(
            config.timeUntilBreakStart() * MINUTES_TO_SECONDS, 
            config.timeUntilBreakEnd() * MINUTES_TO_SECONDS
        );
        log.debug("Next break in {} seconds", breakIn);
    }

    /**
     * Sets the break duration based on configuration and break type.
     */
    private void setBreakDuration() {
        if (Rs2AntibanSettings.microBreakActive) {
            // Micro break duration - use proper range and convert minutes to seconds
            breakDuration = Rs2Random.between(
                Rs2AntibanSettings.microBreakDurationLow * MINUTES_TO_SECONDS,
                Rs2AntibanSettings.microBreakDurationHigh * MINUTES_TO_SECONDS
            );
            setBreakDurationTime = Duration.ofSeconds(breakDuration);
            log.debug("Micro break duration set to {} seconds ({} minutes)", 
                     breakDuration, breakDuration / MINUTES_TO_SECONDS);
        } else if (isOutsidePlaySchedule()) {
            // For play schedule, break until next play time
            Duration timeUntilPlaySchedule = config.playSchedule().timeUntilNextSchedule();
            breakDuration = (int) timeUntilPlaySchedule.toSeconds();
            setBreakDurationTime = Duration.ofSeconds(breakDuration);
            log.info("Play schedule break duration set to {} seconds", breakDuration);
        } else {
            // Normal break duration
            breakDuration = Rs2Random.between(
                config.breakDurationStart() * MINUTES_TO_SECONDS,
                config.breakDurationEnd() * MINUTES_TO_SECONDS
            );
            setBreakDurationTime = Duration.ofSeconds(breakDuration);
            log.debug("Normal break duration set to {} seconds", breakDuration);
        }
    }

    /**
     * Resumes scripts and resets break timing.
     */
    private void resumeFromBreak() {
        Microbot.pauseAllScripts.compareAndSet(true, false);
        PluginPauseEvent.setPaused(false);
        Rs2AntibanSettings.microBreakActive = false;
    }


    /**
     * Updates break statistics.
     */
    private void updateBreakStatistics() {
        totalBreaks++;
        log.info("Break completed. Total breaks: {}", totalBreaks);
    }

    /**
     * Resets window title to original.
     */
    private void resetWindowTitle() {
        ClientUI.getFrame().setTitle(originalWindowTitle);
    }

    /**
     * Cleans up break-related state variables.
     */
    private void cleanupBreakState() {
        breakDuration = -1;
        setBreakDurationTime = Duration.ZERO;
    }

    /**
     * Resets config toggles to false after processing.
     */
    private void resetConfigToggles() {
        // Note: These will reset automatically due to the config system
        // This method exists for potential future toggle handling
        if (Microbot.getConfigManager() == null) {
           return;
        }
        Microbot.getConfigManager().setConfiguration(BreakHandlerConfig.configGroup, "breakNow", false);
        Microbot.getConfigManager().setConfiguration(BreakHandlerConfig.configGroup, "breakEndNow", false);
    }

    /**
     * Updates break-related timers and duration calculations.
     */
    private void updateBreakTimers() {
        BreakHandlerState state = currentState.get();
     
        // Count down to next break (only in waiting state and when logged in)
        if (state == BreakHandlerState.WAITING_FOR_BREAK && breakIn >= 0 && Microbot.isLoggedIn()) {
            if (!config.onlyMicroBreaks()){
                breakIn--;    
            }
            
        }

        // Count down active break duration
        if ((state == BreakHandlerState.LOGGED_OUT || state == BreakHandlerState.INGAME_BREAK_ACTIVE) 
            && breakDuration >= 0) {
            breakDuration--;
        }
    }

    /**
     * Updates window title based on current state and break information.
     */
    private void updateWindowTitle() {
        BreakHandlerState state = currentState.get();
        
        if (state == BreakHandlerState.LOGGED_OUT || state == BreakHandlerState.INGAME_BREAK_ACTIVE) {
            String breakType = state == BreakHandlerState.INGAME_BREAK_ACTIVE ? "In Game Break(Microbreak)" : "Break";
            ClientUI.getFrame().setTitle(originalWindowTitle + " - " + breakType + ": " + 
                                       formatDuration(Duration.ofSeconds(Math.max(0, breakDuration))));
        } else if (isBreakActive()) {
            ClientUI.getFrame().setTitle(originalWindowTitle + " - " + state.toString().replace("_", " "));
        }
    }

    /**
     * Checks if currently outside play schedule hours.
     */
    private boolean isOutsidePlaySchedule() {
        return config.playSchedule().isOutsideSchedule() && config.usePlaySchedule();
    }

    @Override
    public void shutdown() {
        BreakHandlerState state = currentState.get();
        if(scheduledFuture != null && !scheduledFuture.isDone()) {
            scheduledFuture.cancel(true);
        }        
        log.info("Break handler shutting down. Current state: {}", state);
        
        // If we're in a break state, try to clean up gracefully
        if (state == BreakHandlerState.LOGGED_OUT) {
            log.info("Attempting to resume from logged out state");
            transitionToState(BreakHandlerState.LOGIN_REQUESTED);
        } else if (isBreakActive()) {
            log.info("Attempting to end break gracefully");
            transitionToState(BreakHandlerState.BREAK_ENDING);
        }        
        resetWindowTitle();
        resumeFromBreak();
        super.shutdown();
    }

    /**
     * Resets the break handler to initial state.
     */
    public void reset() {
        log.info("Resetting break handler");
        resetBreakState();
        transitionToState(BreakHandlerState.WAITING_FOR_BREAK);
        initializeNextBreakTimer();
    }

    /**
     * Centralized method to reset all break-related state.
     */
    private void resetBreakState() {
        breakIn = -1;
        breakDuration = -1;
        setBreakDurationTime = Duration.ZERO;
        retryCount.set(0);
        safeConditionWaitStartTime = null;
        lastCombatCheckTime = Instant.now();
    }

    /**
     * Checks if the break handler is currently in a locked state.
     * This includes both the manual lock state and any locked conditions from schedulable plugins.
     */
    public static boolean isLockState() {
        return lockState.get();
    }
    
    /**
     * checks for ban screen during login attempt or when logged out
     */
    private void checkForBan() {
        GameState gameState = Microbot.getClient().getGameState();
        
        // detect ban screen on login screen
        boolean banDetected = gameState == GameState.LOGIN_SCREEN
                && Microbot.getClient().getLoginIndex() == BANNED_LOGIN_INDEX;
        
        if (banDetected && !isBanned) {
            isBanned = true;
            handleBanDetection();
        }
    }

    /**
     * updates cached player name when logged in
     */
    private void updatePlayerNameCache() {
        boolean currentlyLoggedIn = Microbot.isLoggedIn();
        
        // detect fresh login - update player name cache
        if (currentlyLoggedIn && !wasLoggedIn) {
            Rs2PlayerModel localPlayer = Rs2Player.getLocalPlayer();
            if (localPlayer != null && localPlayer.getName() != null) {
                lastKnownPlayerName = localPlayer.getName();
                log.info("Updated cached player name: {}", lastKnownPlayerName);
            }
        }
        
        wasLoggedIn = currentlyLoggedIn;
    }

    /**
     * handles ban detection - sends notification and shuts down plugins
     */
    private void handleBanDetection() {
        log.info("Ban screen detected for player: {}", lastKnownPlayerName);
        
        // send discord notification if webhook is configured
        Rs2Discord.sendAlert("BAN", "Ban screen detected during login attempt.", 0xDC143C, lastKnownPlayerName, "BreakHandler");
        
        // shutdown break handler plugin
        shutdownPlugin();
    }

    /**
     * shuts down break handler plugin when ban is detected
     */
    private void shutdownPlugin() {
        try {
            log.info("Shutting down {} plugin due to ban detection", BreakHandlerPlugin.class.getSimpleName());
            Microbot.stopPlugin(BreakHandlerPlugin.class);
            
        } catch (Exception ex) {
            log.error("Error shutting down {} plugin", BreakHandlerPlugin.class.getSimpleName(), ex);
        }
    }
}
