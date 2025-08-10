package net.runelite.client.plugins.microbot.breakhandler;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Constants;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.pluginscheduler.util.SchedulerPluginUtil;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.events.PluginPauseEvent;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.ui.ClientUI;
import net.runelite.http.api.worlds.WorldRegion;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
 * Micro breaks follow: WAITING_FOR_BREAK -> BREAK_REQUESTED -> MICRO_BREAK_ACTIVE -> BREAK_ENDING
 * 
 * @version 2.0.0
 */
@Slf4j
public class BreakHandlerScript extends Script {
    public static String version = "2.0.0";
    
    // Constants for configuration and timing
    private static final int SCHEDULER_INTERVAL_MS = Constants.GAME_TICK_LENGTH;
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
            log.info("Break handler lock state changed: {} -> {}", currentState, state);
        }
    }
    
    // UI and configuration
    private String originalWindowTitle = "";
    private BreakHandlerConfig config;

    /**
     * Checks if a break is currently active (any break state except waiting).
     */
    public static boolean isBreakActive() {
        return currentState.get() != BreakHandlerState.WAITING_FOR_BREAK;
    }
    
    /**
     * Checks if a micro break is currently active.
     */
    public static boolean isMicroBreakActive() {
        BreakHandlerState state = currentState.get();
        return state == BreakHandlerState.MICRO_BREAK_ACTIVE ||
               (Rs2AntibanSettings.takeMicroBreaks && Rs2AntibanSettings.microBreakActive);
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
                processBreakHandlerStateMachine();
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
            case MICRO_BREAK_ACTIVE:
                handleMicroBreakActiveState();
                break;
            case LOGIN_REQUESTED:
                handleLoginRequestedState();
                break;
            case LOGGING_IN:
                handleLoggingInState();
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
            if (state == BreakHandlerState.LOGGED_OUT || state == BreakHandlerState.MICRO_BREAK_ACTIVE) {
                if (state == BreakHandlerState.LOGGED_OUT){
                    transitionToState(BreakHandlerState.LOGGING_IN);
                }else if (state == BreakHandlerState.MICRO_BREAK_ACTIVE) {
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
        boolean normalBreakTime = breakIn <= 0 && !isLockState();
        boolean microBreakTime = Rs2AntibanSettings.microBreakActive && !isLockState();
        
        if (normalBreakTime || microBreakTime) {
            log.info("Break time reached - Normal: {}, Micro: {}", normalBreakTime, microBreakTime);
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
        } else {
            safeConditionWaitStartTime = Instant.now();
        }
        
        // Check for safe conditions periodically
        Instant now = Instant.now();
        if (Duration.between(lastCombatCheckTime, now).toMillis() >= getCombatCheckIntervalMs()) {
            lastCombatCheckTime = now;
            
            if (isSafeToBreak()) {
                log.info("Safe conditions met, initiating break");
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
        log.info("Initiating break - pausing all scripts");
        
        // Pause all scripts
        Microbot.pauseAllScripts.compareAndSet(false, true);
        PluginPauseEvent.setPaused(true);
        Rs2Walker.setTarget(null);
        // Determine next state based on break type
        if (Rs2AntibanSettings.microBreakActive && (config.onlyMicroBreaks() || !shouldLogout())) {
            setBreakDuration();
            transitionToState(BreakHandlerState.MICRO_BREAK_ACTIVE);
        } else {
            transitionToState(BreakHandlerState.LOGOUT_REQUESTED);
        }
    }

    /**
     * Handles client shutdown when configured to shutdown the entire client during breaks.
     * This will completely close the RuneLite client.
     */
    private void handleClientShutdown() {
        try {
            log.info("Shutting down RuneLite client due to break handler configuration");
            
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
        int currentRetryCount = retryCount.get();
        Instant currentStateChangeTime = stateChangeTime.get();
        
        if (currentRetryCount >= getMaxLogoutRetries()) {
            log.warn("Max logout retries reached, continuing with logged-in break");
            setBreakDuration();
            transitionToState(BreakHandlerState.MICRO_BREAK_ACTIVE);
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
            if (scheduledFuture != null && !scheduledFuture.isDone()) {
                scheduledFuture.cancel(true);
            }
            // Check on next cycle if we successfully logged out
            scheduledFuture = scheduledExecutorService.schedule(() -> {
                if (!Microbot.isLoggedIn()) {
                    log.info("Logout successful");
                    setBreakDuration();
                    transitionToState(BreakHandlerState.LOGGED_OUT);
                }
            }, 2000, TimeUnit.MILLISECONDS);
            
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
        // Check if break should end
        if (breakDuration <= 0 || config.breakEndNow()) {
            log.info("Break duration completed, requesting login");
            transitionToState(BreakHandlerState.LOGIN_REQUESTED);
        }
    }

    /**
     * State: MICRO_BREAK_ACTIVE
     * In micro break state (no logout), waiting for duration to complete.
     */
    private void handleMicroBreakActiveState() {
        // Check if micro break should end
        if ((breakDuration <= 0 && !Rs2AntibanSettings.microBreakActive) || config.breakEndNow()) {
            log.info("Micro break completed");
            transitionToState(BreakHandlerState.BREAK_ENDING);
        }
    }

    /**
     * State: LOGIN_REQUESTED
     * Break ended, attempting to login.
     */
    private void handleLoginRequestedState() {
        if (Microbot.isLoggedIn()) {
            log.info("Already logged in, proceeding to break ending");
            transitionToState(BreakHandlerState.BREAK_ENDING);
            return;
        }
        
        log.info("Attempting login");
        try {
            // Use the Login utility class to handle login
            if (Login.activeProfile != null) {
                if (config.useRandomWorld()) {
                    // Get random world with region filter for break handler
                    int randomWorld = getRandomWorldWithRegionFilter(config);
                    new Login(randomWorld);
                } else {
                    new Login();
                }
            } else {
                // If no active profile, use default login
                if (config.useRandomWorld()) {
                    // Get random world with region filter for break handler
                    int randomWorld = getRandomWorldWithRegionFilter(config);
                    new Login(randomWorld);
                } else {
                    new Login();
                }
            }
            transitionToState(BreakHandlerState.LOGGING_IN);
        } catch (Exception ex) {
            log.error("Error initiating login", ex);
            // Retry login request after delay
            scheduledFuture = scheduledExecutorService.schedule(() -> {
                if (currentState.get() == BreakHandlerState.LOGIN_REQUESTED) {
                    retryCount.incrementAndGet();
                }
            }, getLoginRetryDelayMs(), TimeUnit.MILLISECONDS);
        }
    }

    /**
     * State: LOGGING_IN
     * Currently attempting to log in, with retry logic.
     */
    private void handleLoggingInState() {
        if (Microbot.isLoggedIn()) {
            log.info("Login successful");
            transitionToState(BreakHandlerState.BREAK_ENDING);
            return;
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
            log.warn("Max login retries reached, staying logged out");
            // Stay in logged out state and wait for manual intervention or next attempt
            setBreakDuration(); // Reset break duration to prevent immediate retry
            transitionToState(BreakHandlerState.LOGGED_OUT);
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
        
        // Handle world switching if configured
        handleWorldSwitching();
        
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
        return isOutsidePlaySchedule() || config.logoutAfterBreak();
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

    private List<WorldRegion> getAllowedRegions(BreakHandlerConfig config) {
        List<WorldRegion> allowedRegions = new ArrayList<>();
        
        if (config.allowUK()) {
            allowedRegions.add(WorldRegion.UNITED_KINGDOM);
        }
        if (config.allowUS()) {
            allowedRegions.add(WorldRegion.UNITED_STATES_OF_AMERICA);
        }
        if (config.allowGermany()) {
            allowedRegions.add(WorldRegion.GERMANY);
        }
        if (config.allowAustralia()) {
            allowedRegions.add(WorldRegion.AUSTRALIA);
        }
        
        return allowedRegions;
    }
    
    private int getRandomWorldWithRegionFilter(BreakHandlerConfig config) {
        List<WorldRegion> allowedRegions = getAllowedRegions(config);
        
        // Use automatic member detection from profiles
        boolean isMember = Rs2Player.isMember();
        
        if (allowedRegions.isEmpty()) {
            // If no regions allowed, use default method
            return Login.getRandomWorld(isMember);
        }
        
        // Pick a random region from allowed regions
        Random random = new Random();
        WorldRegion selectedRegion = allowedRegions.get(random.nextInt(allowedRegions.size()));
        
        return Login.getRandomWorld(isMember, selectedRegion);
    }

    /**
     * Handles world switching based on configuration.
     */
    private void handleWorldSwitching() {
        if (config.useRandomWorld()) {
            try {
                int randomWorld = getRandomWorldWithRegionFilter(config);
                Microbot.hopToWorld(randomWorld);
            } catch (Exception ex) {
                // Silent error handling
            }
        }
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
            breakIn--;
        }

        // Count down active break duration
        if ((state == BreakHandlerState.LOGGED_OUT || state == BreakHandlerState.MICRO_BREAK_ACTIVE) 
            && breakDuration >= 0) {
            breakDuration--;
        }
    }

    /**
     * Updates window title based on current state and break information.
     */
    private void updateWindowTitle() {
        BreakHandlerState state = currentState.get();
        
        if (state == BreakHandlerState.LOGGED_OUT || state == BreakHandlerState.MICRO_BREAK_ACTIVE) {
            String breakType = state == BreakHandlerState.MICRO_BREAK_ACTIVE ? "Micro Break" : "Break";
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
        return lockState.get() || SchedulerPluginUtil.hasLockedSchedulablePlugins();
    }
}
