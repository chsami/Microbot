package net.runelite.client.plugins.microbot.breakhandler.breakhandlerv2;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.client.config.ConfigProfile;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.discord.Rs2Discord;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.LoginManager;
import net.runelite.client.plugins.microbot.util.world.Rs2WorldUtil;
import net.runelite.client.ui.ClientUI;
import net.runelite.http.api.worlds.WorldRegion;
import net.runelite.client.plugins.Plugin;

import javax.inject.Singleton;
import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

/**
 * Break Handler V2 Script
 * Enhanced break handler with profile-based login and intelligent world selection
 * Version: 2.0.0
 */
@Singleton
@Slf4j
public class BreakHandlerV2Script extends Script {

    // Instance tracking for debugging
    private static int instanceCounter = 0;
    private final int instanceId;

    public BreakHandlerV2Script() {
        instanceId = ++instanceCounter;
        System.out.println("[DEBUG] BreakHandlerV2Script instance #" + instanceId + " created. Hash: " + System.identityHashCode(this));
    }

    @Getter
    private BreakHandlerV2Config config;

    // Timing variables (volatile for thread visibility from overlay/UI threads)
    private volatile Instant nextBreakTime;
    private volatile Instant breakEndTime;
    private volatile Instant loginAttemptTime;

    // State tracking
    private int loginRetryCount = 0;
    private int safetyCheckAttempts = 0;
    private int preBreakWorld = -1;
    private ConfigProfile activeProfile;
    private boolean unexpectedLogoutDetected = false;
    private String originalWindowTitle = "";
    private boolean pluginStopTriggered = false;
    private boolean pluginRestartPending = false;
    private String stoppedPluginClassName = PluginStopOption.NONE_VALUE;
    private Instant pluginStopEarliestTime = Instant.MIN;
    private Instant pluginRestartAllowedAt = Instant.MIN;

    // Persisted break keys
    private static final String PERSISTED_BREAK_END_KEY = "persistedBreakEnd";
    private static final String PERSISTED_BREAK_LOGOUT_KEY = "persistedBreakLogout";

    // Break duration in milliseconds
    private long currentBreakDuration = 0;
    private boolean logoutBreakActive = false;

    // Login retry backoff constants
    private static final int MAX_LOGIN_ATTEMPTS = 10;
    private static final int INITIAL_FAST_RETRIES = 3;
    private static final int BACKOFF_BASE_DELAY_MS = 30000; // 30 seconds

    // Safety check backoff constants
    private static final int MAX_SAFETY_CHECK_ATTEMPTS = 60;
    private static final int SAFETY_CHECK_DELAY_MS = 5000; // 5 seconds between checks

    public static String version = "2.0.0";

    /**
     * Run the break handler script
     */
    public boolean run(BreakHandlerV2Config config) {
        this.config = config;
        BreakHandlerV2State.setState(BreakHandlerV2State.WAITING_FOR_BREAK);

        // Initialize next break time immediately to prevent null values in overlay
        scheduleNextBreak();
        log.info("[BreakHandlerV2] Initial break scheduled for {}", nextBreakTime);
        // Load active profile
        loadActiveProfile();
        restorePersistedBreakState();
        initializeBreakIfOutsideSchedule();
        originalWindowTitle = ClientUI.getFrame().getTitle();
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run() && !config.autoLogin() && BreakHandlerV2State.getCurrentState() != BreakHandlerV2State.LOGIN_REQUESTED) return;

                // Ensure previously stopped plugin is restarted once we're logged back in, even if the state machine
                // hasn't reached BREAK_ENDING yet (e.g., manual login after extended sleep).
                attemptPluginRestartIfLoggedIn();


                // Detect unexpected logout while waiting for break
                detectUnexpectedLogout();
                enforceLogoutDuringActiveBreak();
                updateWindowTitle();

                // Main state machine
                switch (BreakHandlerV2State.getCurrentState()) {
                    case WAITING_FOR_BREAK:
                        handleWaitingForBreak();
                        break;
                    case BREAK_REQUESTED:
                        handleBreakRequested();
                        break;
                    case INITIATING_BREAK:
                        handleInitiatingBreak();
                        break;
                    case LOGOUT_REQUESTED:
                        handleLogoutRequested();
                        break;
                    case LOGGED_OUT:
                        handleLoggedOut();
                        break;
                    case LOGIN_REQUESTED:
                        handleLoginRequested();
                        break;
                    case LOGGING_IN:
                        handleLoggingIn();
                        break;
                    case LOGIN_EXTENDED_SLEEP:
                        handleLoginExtendedSleep();
                        break;
                    case BREAK_ENDING:
                        handleBreakEnding();
                        break;
                    case PROFILE_SWITCHING:
                        handleProfileSwitching();
                        break;
                }

            } catch (Exception ex) {
                log.error("[BreakHandlerV2] Error in main loop", ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);

        return true;
    }

    /**
     * Load the active profile from config manager
     */
    private void loadActiveProfile() {
        if (config.useActiveProfile()) {
            try {
                activeProfile = Microbot.getConfigManager().getProfile();
            } catch (Exception ex) {
                log.error("[BreakHandlerV2] Failed to load active profile", ex);
            }
        }
    }

    /**
     * Handle WAITING_FOR_BREAK state
     * Schedules next break and monitors for break time
     */
    private void handleWaitingForBreak() {
        // Handle play schedule logic - trigger break if outside scheduled hours
        if (isOutsidePlaySchedule()) {
            log.info("[BreakHandlerV2] Outside play schedule, requesting break");
            transitionToState(BreakHandlerV2State.BREAK_REQUESTED);
            return;
        }

        applyPreBreakPluginStopLead();

        // When play schedule is enabled, take a break as soon as the schedule window ends
        if (config.usePlaySchedule()) {
            if (nextBreakTime != null && Instant.now().isAfter(nextBreakTime)) {
                log.info("[BreakHandlerV2] Play schedule window ended, requesting break");
                transitionToState(BreakHandlerV2State.BREAK_REQUESTED);
            }
            return;
        }

        // Check if it's time for a break (only when play schedule is disabled)
        if (nextBreakTime != null && Instant.now().isAfter(nextBreakTime)) {
            log.info("[BreakHandlerV2] Break time reached, requesting break");
            transitionToState(BreakHandlerV2State.BREAK_REQUESTED);
        }
    }

    /**
     * Handle BREAK_REQUESTED state
     * Initiates break based on configuration
     */
    private void handleBreakRequested() {
        stopConfiguredPluginIfNeeded();

        // If breakEndTime is already set, we're in a no-logout break waiting for it to end
        if (breakEndTime != null) {
            // Check if break is over
            if (Instant.now().isAfter(breakEndTime)) {
                log.info("[BreakHandlerV2] No-logout break ended");
                Microbot.pauseAllScripts.set(false);
                transitionToState(BreakHandlerV2State.BREAK_ENDING);
            }
            return;
        }

        // Store current world before break
        if (Microbot.isLoggedIn()) {
            preBreakWorld = Microbot.getClient().getWorld();
        }

        // Force logout if outside play schedule OR if logoutOnBreak is enabled
        boolean shouldLogout = isOutsidePlaySchedule() || config.logoutOnBreak();

        if (shouldLogout) {
            String logoutReason = isOutsidePlaySchedule() ? "play schedule" : "configuration";
            log.info("[BreakHandlerV2] Starting break (with logout - {})", logoutReason);
            transitionToState(BreakHandlerV2State.INITIATING_BREAK);
        } else {
            log.info("[BreakHandlerV2] Starting break (no logout - scripts paused)");
            beginPauseBreak();
        }
    }

    /**
     * Handle INITIATING_BREAK state
     * Performs safety checks before logout with backoff retry
     */
    private void handleInitiatingBreak() {
        stopConfiguredPluginIfNeeded();

        if (!Microbot.isLoggedIn()) {
            log.info("[BreakHandlerV2] Already logged out, transitioning to LOGGED_OUT");
            setBreakTimer(true);
            sendBreakStartedNotification(true);
            safetyCheckAttempts = 0; // Reset counter
            transitionToState(BreakHandlerV2State.LOGGED_OUT);
            return;
        }

        // Safety check if enabled
        if (config.safetyCheck()) {
            boolean isInCombat = Rs2Player.isInCombat();
            boolean isInteracting = Rs2Player.isInteracting();

            if (isInCombat || isInteracting) {
                safetyCheckAttempts++;

                if (safetyCheckAttempts >= MAX_SAFETY_CHECK_ATTEMPTS) {
                    log.warn("[BreakHandlerV2] Safety check max attempts ({}) reached, forcing break",
                             MAX_SAFETY_CHECK_ATTEMPTS);

                    String unsafeReason = isInCombat && isInteracting ? "in combat and interacting"
                                        : isInCombat ? "in combat"
                                        : "interacting";

                    sendDiscordNotification("Safety Check Failed",
                        "Failed to achieve safe conditions after " + MAX_SAFETY_CHECK_ATTEMPTS + " attempts.\n" +
                        "Player still " + unsafeReason + ".\n" +
                        "Forcing break anyway.");

                    safetyCheckAttempts = 0; // Reset counter
                } else {
                    log.debug("[BreakHandlerV2] Waiting for safe conditions... (attempt {}/{})",
                             safetyCheckAttempts, MAX_SAFETY_CHECK_ATTEMPTS);
                    sleep(SAFETY_CHECK_DELAY_MS);
                    return; // Stay in this state and check again
                }
            } else {
                // Safe conditions met
                if (safetyCheckAttempts > 0) {
                    log.info("[BreakHandlerV2] Safe conditions achieved after {} attempts", safetyCheckAttempts);
                }
                safetyCheckAttempts = 0; // Reset counter
            }
        }

        // Proceed to logout
        beginLogoutBreak();
    }

    /**
     * Handle LOGOUT_REQUESTED state
     * Performs logout
     */
    private void handleLogoutRequested() {
        if (!Microbot.isLoggedIn()) {
            log.info("[BreakHandlerV2] Logout successful");
            persistBreakState(true);
            transitionToState(BreakHandlerV2State.LOGGED_OUT);
            return;
        }

        try {
            log.info("[BreakHandlerV2] Attempting logout...");
            Rs2Player.logout();
            sleep(2000, 3000);
        } catch (Exception ex) {
            log.error("[BreakHandlerV2] Error during logout", ex);
        }
    }

    /**
     * Handle LOGGED_OUT state
     * Waits for break duration to complete
     */
    private void handleLoggedOut() {
        if (breakEndTime == null) {
            log.error("[BreakHandlerV2] Break end time not set, resetting");
            transitionToState(BreakHandlerV2State.WAITING_FOR_BREAK);
            return;
        }

        if (isOutsidePlaySchedule()) {
            extendBreakUntilSchedule();
            return;
        }

        // Check if break is over
        if (Instant.now().isAfter(breakEndTime)) {
            if (config.autoLogin()) {
                log.info("[BreakHandlerV2] Break ended, requesting login");
                loginRetryCount = 0;
                transitionToState(BreakHandlerV2State.LOGIN_REQUESTED);
            } else {
                log.info("[BreakHandlerV2] Break ended, auto-login disabled");
                sendDiscordNotification("Break Ended", "Auto-login is disabled");
                transitionToState(BreakHandlerV2State.BREAK_ENDING);
            }
        }
    }

    /**
     * Handle LOGIN_REQUESTED state
     * Initiates login with profile data and world selection
     * Uses exponential backoff: first 3 attempts are fast, then 30s incremental delays
     */
    private void handleLoginRequested() {
        // Check if already logged in
        if (Microbot.isLoggedIn()) {
            log.info("[BreakHandlerV2] Already logged in");
            transitionToState(BreakHandlerV2State.BREAK_ENDING);
            return;
        }

        // Check retry limit (max 10 attempts)
        if (loginRetryCount >= MAX_LOGIN_ATTEMPTS) {
            log.error("[BreakHandlerV2] Max login attempts ({}) reached", MAX_LOGIN_ATTEMPTS);
            sendDiscordNotification("Login Failed",
                "Max login attempts (" + MAX_LOGIN_ATTEMPTS + ") reached. Giving up.");
            transitionToState(BreakHandlerV2State.LOGIN_EXTENDED_SLEEP);
            return;
        }

        // Validate profile
        if (activeProfile == null) {
            log.error("[BreakHandlerV2] No active profile available for login");
            sendDiscordNotification("Login Failed", "No active profile available");
            transitionToState(BreakHandlerV2State.WAITING_FOR_BREAK);
            return;
        }

        // Apply backoff delay if needed (after first 3 attempts)
        if (loginRetryCount >= INITIAL_FAST_RETRIES) {
            int backoffDelay = calculateLoginBackoffDelay(loginRetryCount);
            log.info("[BreakHandlerV2] Applying backoff delay: {} seconds", backoffDelay / 1000);
            sleep(backoffDelay);
        } else if (loginRetryCount > 0) {
            // Small delay between initial fast retries (5 seconds)
            sleep(5000);
        }

        // Select world based on configuration
        int targetWorld = selectWorld();

        if (targetWorld == -1) {
            log.error("[BreakHandlerV2] Failed to select valid world");
            loginRetryCount++;
            return;
        }

        log.info("[BreakHandlerV2] Attempting login to world {} (attempt {}/{})",
                 targetWorld, loginRetryCount + 1, MAX_LOGIN_ATTEMPTS);

        // Perform login
        boolean loginInitiated = LoginManager.login(
            activeProfile.getName(),
            activeProfile.getPassword(),
            targetWorld
        );

        if (loginInitiated) {
            loginRetryCount++;
            loginAttemptTime = Instant.now();
            transitionToState(BreakHandlerV2State.LOGGING_IN);
        } else {
            log.error("[BreakHandlerV2] Failed to initiate login");
            loginRetryCount++;
        }
    }

    /**
     * Calculate exponential backoff delay for login retries
     * First 3 attempts: 5s delay
     * After that: 30s, 60s, 90s, 120s, etc.
     */
    private int calculateLoginBackoffDelay(int attemptCount) {
        if (attemptCount < INITIAL_FAST_RETRIES) {
            return 5000; // 5 seconds for initial retries
        }
        // Exponential backoff: 30s * (attempt - 3)
        int backoffMultiplier = attemptCount - INITIAL_FAST_RETRIES + 1;
        return BACKOFF_BASE_DELAY_MS * backoffMultiplier;
    }

    /**
     * Handle LOGGING_IN state
     * Monitors login progress
     */
    private void handleLoggingIn() {
        // Check if logged in
        if (Microbot.isLoggedIn()) {
            log.info("[BreakHandlerV2] Login successful");
            sendDiscordNotification("Login Successful",
                "Logged into world " + Microbot.getClient().getWorld());
            clearPersistedBreakState();
            transitionToState(BreakHandlerV2State.BREAK_ENDING);
            return;
        }

        // Check for timeout (10 seconds)
        if (loginAttemptTime != null &&
            Instant.now().isAfter(loginAttemptTime.plusSeconds(10))) {
            log.warn("[BreakHandlerV2] Login timeout, retrying");
            transitionToState(BreakHandlerV2State.LOGIN_REQUESTED);
        }
    }

    /**
     * Handle LOGIN_EXTENDED_SLEEP state
     * Extended wait after multiple failed login attempts
     */
    private void handleLoginExtendedSleep() {
        log.info("[BreakHandlerV2] Entering extended sleep (5 minutes)");
        sleep(300000); // 5 minutes
        loginRetryCount = 0;
        transitionToState(BreakHandlerV2State.WAITING_FOR_BREAK);
    }

    /**
     * Handle BREAK_ENDING state
     * Finalizes break and schedules next break
     */
    private void handleBreakEnding() {
        log.info("[BreakHandlerV2] Break cycle complete");

        // set restart delay window
        pluginRestartAllowedAt = Instant.now().plusSeconds(Math.max(0, config.startPluginDelaySeconds()));

        startConfiguredPluginIfNeeded();

        // Reset variables
        breakEndTime = null;
        loginAttemptTime = null;
        loginRetryCount = 0;
        safetyCheckAttempts = 0;
        preBreakWorld = -1;
        unexpectedLogoutDetected = false;
        pluginStopTriggered = false;
        pluginRestartAllowedAt = Instant.MIN;
        pluginStopEarliestTime = Instant.MIN;

        // Unpause scripts
        Microbot.pauseAllScripts.set(false);

        // Schedule next break
        scheduleNextBreak();

        String breakMessage = nextBreakTime != null
                ? "Next break scheduled for " + nextBreakTime
                : "Using play schedule: " + config.playSchedule().displayString();
        sendDiscordNotification("Break Ended", breakMessage);
        clearPersistedBreakState();

        transitionToState(BreakHandlerV2State.WAITING_FOR_BREAK);
    }

    /**
     * Handle PROFILE_SWITCHING state
     * Future implementation for multi-account support
     */
    private void handleProfileSwitching() {
        // Placeholder for future profile switching functionality
        log.info("[BreakHandlerV2] Profile switching not yet implemented");
        transitionToState(BreakHandlerV2State.WAITING_FOR_BREAK);
    }

    /**
     * Detect unexpected logout (kicked, disconnected, etc.)
     * Handles case where player is logged out while waiting for a scheduled break
     */
    private void detectUnexpectedLogout() {
        // Only check if we're in WAITING_FOR_BREAK state and have a scheduled break
        if (BreakHandlerV2State.getCurrentState() != BreakHandlerV2State.WAITING_FOR_BREAK) {
            unexpectedLogoutDetected = false; // Reset flag when not in WAITING_FOR_BREAK
            return;
        }

        if (nextBreakTime == null) {
            return;
        }

        // Reset flag when player is logged in
        if (Microbot.isLoggedIn()) {
            unexpectedLogoutDetected = false;
            return;
        }

        // Check if player is logged out unexpectedly
        if (!Microbot.isLoggedIn() && !unexpectedLogoutDetected) {
            long secondsUntilBreak = Instant.now().until(nextBreakTime, ChronoUnit.SECONDS);

            if (secondsUntilBreak > 0) {
                log.warn("[BreakHandlerV2] Unexpected logout detected with {}s until scheduled break", secondsUntilBreak);
                unexpectedLogoutDetected = true; // Prevent repeated detections

                // Handle based on configuration
                if (config.autoLogin()) {
                    log.info("[BreakHandlerV2] Auto-login enabled, attempting to log back in");
                    loginRetryCount = 0;
                    transitionToState(BreakHandlerV2State.LOGIN_REQUESTED);
                } else {
                    log.info("[BreakHandlerV2] Auto-login disabled, pausing break timer");
                    // Keep the state as WAITING_FOR_BREAK but don't count time while logged out
                    // The timer will resume when player logs back in manually
                    sendDiscordNotification("Unexpected Logout",
                        "Player logged out with " + (secondsUntilBreak / 60) + " minutes until break.\nAuto-login is disabled.");
                }
            }
        }
    }

    /**
     * Ensures we are logged out while a break timer is active.
     */
    private void enforceLogoutDuringActiveBreak() {
        long breakRemainingSeconds = getBreakTimeRemaining();

        if (breakRemainingSeconds <= 0 || !Microbot.isLoggedIn() || !logoutBreakActive) {
            return;
        }

        BreakHandlerV2State state = BreakHandlerV2State.getCurrentState();

        if (state != BreakHandlerV2State.LOGOUT_REQUESTED &&
            state != BreakHandlerV2State.INITIATING_BREAK) {
            log.warn("[BreakHandlerV2] Break active ({}s remaining) but player is logged in; forcing logout",
                breakRemainingSeconds);
            transitionToState(BreakHandlerV2State.LOGOUT_REQUESTED);
        }
    }

    /**
     * Select world based on configuration and profile
     */
	private int selectWorld() {
		// When world switching is disabled, reuse whatever world is already selected
		if (config.ignoreWorldSwitching()) {
			int currentWorld = Microbot.getClient() != null ? Microbot.getClient().getWorld() : -1;
			log.info("[BreakHandlerV2] World switching ignored; reusing current selected world {}", currentWorld);
			return currentWorld > 0 ? currentWorld : -1; // -1 skips setWorld(), leaving client selection unchanged
		}

		boolean membersOnly = config.respectMemberStatus() &&
		                      activeProfile != null &&
		                      activeProfile.isMember();

		WorldRegion region = config.regionPreference().getWorldRegion();
		Integer preferredWorld = resolveProfilePreferredWorld(region);

		int targetWorld = -1;

		switch (config.worldSelectionMode()) {
			case CURRENT_PREFERRED_WORLD:
				if (preferredWorld != null) {
					targetWorld = preferredWorld;
					break;
				}

				if (preBreakWorld != -1 && Rs2WorldUtil.canAccessWorld(preBreakWorld)) {
					targetWorld = preBreakWorld;
					break;
				}

				targetWorld = Rs2WorldUtil.getRandomAccessibleWorldFromRegion(
					region,
					config.avoidEmptyWorlds(),
					config.avoidOvercrowdedWorlds(),
					membersOnly
				);
				break;

			case RANDOM_WORLD:
				targetWorld = Rs2WorldUtil.getRandomAccessibleWorld(
					config.avoidEmptyWorlds(),
					config.avoidOvercrowdedWorlds(),
					membersOnly
				);
				break;

			case REGIONAL_RANDOM:
				targetWorld = Rs2WorldUtil.getRandomAccessibleWorldFromRegion(
					region,
					config.avoidEmptyWorlds(),
					config.avoidOvercrowdedWorlds(),
					membersOnly
				);
				break;

			case BEST_POPULATION:
				targetWorld = Rs2WorldUtil.getBestAccessibleWorldForLogin(
					false, // by population, not ping
					region,
					config.avoidEmptyWorlds(),
					config.avoidOvercrowdedWorlds(),
					membersOnly
				);
				break;

			case BEST_PING:
				targetWorld = Rs2WorldUtil.getBestAccessibleWorldForLogin(
					true, // by ping
					region,
					config.avoidEmptyWorlds(),
					config.avoidOvercrowdedWorlds(),
					membersOnly
				);
				break;
		}

		log.info("[BreakHandlerV2] Selected world: {} (mode: {}, members: {})",
		         targetWorld, config.worldSelectionMode(), membersOnly);

		return targetWorld;
	}

	private Integer resolveProfilePreferredWorld(WorldRegion region) {
		if (activeProfile == null || activeProfile.getSelectedWorld() == null) {
			return null;
		}

		int selectedWorld = activeProfile.getSelectedWorld();

		// -1 = random members world, -2 = random F2P world
		if (selectedWorld == -1) {
			if (!config.respectMemberStatus() || activeProfile.isMember()) {
				return Rs2WorldUtil.getRandomAccessibleWorldFromRegion(
					region,
					config.avoidEmptyWorlds(),
					config.avoidOvercrowdedWorlds(),
					true
				);
			}
			log.warn("[BreakHandlerV2] Profile requests random members world but account is F2P");
			return null;
		}

		if (selectedWorld == -2) {
			return Rs2WorldUtil.getRandomAccessibleWorldFromRegion(
				region,
				config.avoidEmptyWorlds(),
				config.avoidOvercrowdedWorlds(),
				false
			);
		}

		if (!Rs2WorldUtil.canAccessWorld(selectedWorld)) {
			log.warn("[BreakHandlerV2] Profile preferred world {} is not accessible, falling back", selectedWorld);
			return null;
		}

		return selectedWorld;
	}

	/**
	 * Schedule the next break
	 */
	private void scheduleNextBreak() {
		if (config.usePlaySchedule()) {
			if (!config.playSchedule().isOutsideSchedule()) {
				Duration timeUntilEnd = config.playSchedule().timeUntilScheduleEnds();
				nextBreakTime = Instant.now().plus(timeUntilEnd);
                log.info("[BreakHandlerV2] Play schedule active ({}), break when schedule ends in {} minutes",
                        config.playSchedule().name(), timeUntilEnd.toMinutes());
            } else {
                nextBreakTime = null;
                log.info("[BreakHandlerV2] Outside play schedule ({}), currently on break",
                        config.playSchedule().name());
            }
            updatePluginStopLeadTime();
            return;
        }

		int minMinutes = config.minPlaytime();
		int maxMinutes = config.maxPlaytime();

		int playtimeMinutes = Rs2Random.between(minMinutes, maxMinutes);
		nextBreakTime = Instant.now().plus(playtimeMinutes, ChronoUnit.MINUTES);

		log.info("[BreakHandlerV2] Next break in {} minutes", playtimeMinutes);

        updatePluginStopLeadTime();
	}

    /**
     * Calculates when we should pre-stop the selected plugin before the break.
     */
    private void updatePluginStopLeadTime() {
        if (config == null) {
            pluginStopEarliestTime = Instant.MIN;
            return;
        }

        int leadSeconds = Math.max(0, config.stopPluginLeadSeconds());
        if (nextBreakTime != null && leadSeconds > 0) {
            pluginStopEarliestTime = nextBreakTime.minusSeconds(leadSeconds);
        } else {
            pluginStopEarliestTime = Instant.MIN;
        }
    }

    /**
     * If within the lead window, stop the configured plugin ahead of the break.
     */
    private void applyPreBreakPluginStopLead() {
        updatePluginStopLeadTime();

        if (pluginStopTriggered || config == null) {
            return;
        }

        if (pluginStopEarliestTime == null || pluginStopEarliestTime == Instant.MIN) {
            return;
        }

        if (Instant.now().isAfter(pluginStopEarliestTime) || Instant.now().equals(pluginStopEarliestTime)) {
            stopConfiguredPluginIfNeeded();
        }
    }

	/**
	 * Calculate break duration
	 */
    private long calculateBreakDuration() {
        // If outside play schedule, break until next play time
        if (isOutsidePlaySchedule()) {
            Duration timeUntilPlaySchedule = config.playSchedule().timeUntilNextSchedule();
            long durationMs = timeUntilPlaySchedule.toMillis();
			log.info("[BreakHandlerV2] Play schedule break duration: {} minutes (until next scheduled play time)",
					durationMs / 60000);
			return durationMs;
		}

		int minMinutes = config.minBreakDuration();
		int maxMinutes = config.maxBreakDuration();

		int breakMinutes = Rs2Random.between(minMinutes, maxMinutes);
		log.info("[BreakHandlerV2] Break duration: {} minutes", breakMinutes);

		return breakMinutes * 60000L; // Convert to milliseconds
	}

    /**
     * Stops a configured Microbot plugin once per break cycle.
     */
    private void stopConfiguredPluginIfNeeded() {
        if (pluginStopTriggered || config == null) {
            return;
        }

        // Respect pre-break lead time while waiting
        if (BreakHandlerV2State.getCurrentState() == BreakHandlerV2State.WAITING_FOR_BREAK
                && pluginStopEarliestTime != null
                && pluginStopEarliestTime != Instant.MIN
                && Instant.now().isBefore(pluginStopEarliestTime)) {
            return;
        }

        String normalizedSelection = PluginStopHelper.normalizeStoredValue(config.pluginToStop(), Microbot.getPluginManager());
        if (PluginStopHelper.isNone(normalizedSelection)) {
            return;
        }

        Plugin pluginInstance = PluginStopHelper.findPluginInstance(
                normalizedSelection,
                config.pluginToStop(),
                Microbot.getPluginManager());

        if (pluginInstance == null) {
            log.warn("[BreakHandlerV2] Could not resolve plugin to stop for value '{}' (normalized '{}')",
                    config.pluginToStop(), normalizedSelection);
            return;
        }

        boolean wasEnabled = Microbot.isPluginEnabled(pluginInstance);
        boolean stopResult = Microbot.stopPlugin(pluginInstance);
        boolean nowEnabled = Microbot.isPluginEnabled(pluginInstance);

        log.info("[BreakHandlerV2] Stop request for {} -> {} (wasEnabled={}, nowEnabled={}, stopResult={})",
                PluginStopHelper.resolveDisplayName(normalizedSelection, Microbot.getPluginManager()),
                (!nowEnabled) ? "stopped/closed" : "still active",
                wasEnabled, nowEnabled, stopResult);

        sleep(5000);

        if (!nowEnabled) {
            pluginStopTriggered = true;
            pluginRestartPending = true;
            stoppedPluginClassName = normalizedSelection;
            // Ensure any running scripts pause while stopped
            Microbot.pauseAllScripts.set(true);
        } else {
            // Leave pluginStopTriggered false so we can retry on the next tick
            pluginRestartPending = false;
        }
    }

    /**
     * Starts previously stopped plugin after break ends.
     */
    private void startConfiguredPluginIfNeeded() {
        if (!pluginRestartPending || PluginStopHelper.isNone(stoppedPluginClassName)) {
            return;
        }

        if (pluginRestartAllowedAt != null
                && pluginRestartAllowedAt != Instant.MIN
                && Instant.now().isBefore(pluginRestartAllowedAt)) {
            return;
        }

        Plugin pluginInstance = PluginStopHelper.findPluginInstance(
                stoppedPluginClassName,
                stoppedPluginClassName,
                Microbot.getPluginManager());

        if (pluginInstance == null) {
            log.warn("[BreakHandlerV2] Could not resolve plugin to restart for stored class '{}'", stoppedPluginClassName);
        }

        boolean started = pluginInstance != null
                ? Microbot.startPlugin(pluginInstance)
                : Microbot.startPlugin(stoppedPluginClassName);
        boolean nowEnabled = Microbot.isPluginEnabled(pluginInstance != null ? pluginInstance : Microbot.getPlugin(stoppedPluginClassName));

        log.info("[BreakHandlerV2] Restart request for {} -> {} (startedCall={}, nowEnabled={})",
                PluginStopHelper.resolveDisplayName(stoppedPluginClassName, Microbot.getPluginManager()),
                nowEnabled ? "running" : "not running",
                started, nowEnabled);

        if (nowEnabled) {
            Microbot.pauseAllScripts.set(false);
            pluginRestartPending = false;
            stoppedPluginClassName = PluginStopOption.NONE_VALUE;
            pluginRestartAllowedAt = Instant.MIN;
        }
    }

    /**
     * Safely attempts to restart the configured plugin when the client is logged in.
     */
    private void attemptPluginRestartIfLoggedIn() {
        if (!pluginRestartPending || !Microbot.isLoggedIn()) {
            return;
        }
        // Don't restart while a break (or its login flow) is still active
        if (BreakHandlerV2State.isBreakActive()) {
            return;
        }
        startConfiguredPluginIfNeeded();
    }

    /**
     * Transition to a new state
     */
    private void transitionToState(BreakHandlerV2State newState) {
        BreakHandlerV2State oldState = BreakHandlerV2State.getCurrentState();
        log.info("[BreakHandlerV2] State transition: {} -> {}", oldState, newState);
        BreakHandlerV2State.setState(newState);
    }

    /**
     * Send Discord notification if enabled
     */
    private void sendDiscordNotification(String title, String message) {
        if (!config.enableDiscordWebhook()) {
            return;
        }

        if (activeProfile == null || activeProfile.getDiscordWebhookUrl() == null) {
            return;
        }

        try {
            String playerName = activeProfile.getName();
            Rs2Discord.sendCustomNotification(
                title,
                message,
                Rs2Discord.convertColorToInt(Color.CYAN),
                playerName != null ? playerName : "Unknown",
                "BreakHandler V2"
            );
        } catch (Exception ex) {
            log.error("[BreakHandlerV2] Failed to send Discord notification", ex);
        }
    }

    /**
     * Get time until next break in seconds
     */
    public long getTimeUntilBreak() {
        if (nextBreakTime == null) {
            return -1;
        }
        return Instant.now().until(nextBreakTime, ChronoUnit.SECONDS);
    }

    /**
     * Get time remaining in break in seconds
     */
    public long getBreakTimeRemaining() {
        if (breakEndTime == null) {
            return -1;
        }
        return Instant.now().until(breakEndTime, ChronoUnit.SECONDS);
    }

    /**
     * Checks if currently outside play schedule hours.
     */
    private boolean isOutsidePlaySchedule() {
        return config.usePlaySchedule() && config.playSchedule().isOutsideSchedule();
    }

    @Override
    public void shutdown() {
        super.shutdown();
        log.info("[BreakHandlerV2] Shutting down");

        // Reset state
        BreakHandlerV2State.setState(BreakHandlerV2State.WAITING_FOR_BREAK);
        Microbot.pauseAllScripts.set(false);

        // Clear timers
        nextBreakTime = null;
        breakEndTime = null;
        loginAttemptTime = null;

        // Reset counters and flags
        unexpectedLogoutDetected = false;
        loginRetryCount = 0;
        safetyCheckAttempts = 0;
        logoutBreakActive = false;
        pluginStopTriggered = false;
        pluginRestartPending = false;
        stoppedPluginClassName = PluginStopOption.NONE_VALUE;
        pluginRestartAllowedAt = Instant.MIN;
        pluginStopEarliestTime = Instant.MIN;
    }

    private void updateWindowTitle() {
        BreakHandlerV2State state = BreakHandlerV2State.getCurrentState();

        if (getBreakTimeRemaining() > 0) {
            ClientUI.getFrame().setTitle(originalWindowTitle + " - " + state.toString() + ": " +
                    formatDuration(Duration.ofSeconds(Math.max(0, getBreakTimeRemaining()))));
        }
    }

    /**
     * Rehydrates a persisted break if one is saved and still active.
     */
    private void restorePersistedBreakState() {
        if (Microbot.getConfigManager() == null) {
            return;
        }

        Long savedEnd = Microbot.getConfigManager().getConfiguration(BreakHandlerV2Config.configGroup, PERSISTED_BREAK_END_KEY, Long.class);
        Boolean savedLogout = Microbot.getConfigManager().getConfiguration(BreakHandlerV2Config.configGroup, PERSISTED_BREAK_LOGOUT_KEY, Boolean.class);

        if (savedEnd == null) {
            return;
        }

        Instant persistedEnd = Instant.ofEpochMilli(savedEnd);
        if (persistedEnd.isAfter(Instant.now())) {
            breakEndTime = persistedEnd;
            logoutBreakActive = Boolean.TRUE.equals(savedLogout);
            log.info("[BreakHandlerV2] Restored active break until {}", breakEndTime);
            stopConfiguredPluginIfNeeded();

            if (Boolean.TRUE.equals(savedLogout)) {
                if (Microbot.isLoggedIn()) {
                    transitionToState(BreakHandlerV2State.INITIATING_BREAK);
                } else {
                    transitionToState(BreakHandlerV2State.LOGGED_OUT);
                }
            } else {
                Microbot.pauseAllScripts.set(true);
                transitionToState(BreakHandlerV2State.BREAK_REQUESTED);
            }
        } else {
            clearPersistedBreakState();
        }
    }

    /**
     * Starts a break immediately if the current time is outside the configured play schedule.
     */
    private void initializeBreakIfOutsideSchedule() {
        if (!isOutsidePlaySchedule()) {
            return;
        }

        setBreakTimer(true);
        stopConfiguredPluginIfNeeded();
        sendBreakStartedNotification(true);
        log.info("[BreakHandlerV2] Outside play schedule on startup, enforcing break until {}", breakEndTime);

        if (Microbot.isLoggedIn()) {
            transitionToState(BreakHandlerV2State.INITIATING_BREAK);
        } else {
            transitionToState(BreakHandlerV2State.LOGGED_OUT);
        }
    }

    private void persistBreakState(boolean logoutBreak) {
        if (Microbot.getConfigManager() == null || breakEndTime == null) {
            return;
        }
        logoutBreakActive = logoutBreak;
        Microbot.getConfigManager().setConfiguration(BreakHandlerV2Config.configGroup, PERSISTED_BREAK_END_KEY, breakEndTime.toEpochMilli());
        Microbot.getConfigManager().setConfiguration(BreakHandlerV2Config.configGroup, PERSISTED_BREAK_LOGOUT_KEY, logoutBreak);
    }

    private void clearPersistedBreakState() {
        if (Microbot.getConfigManager() == null) {
            return;
        }
        logoutBreakActive = false;
        Microbot.getConfigManager().unsetConfiguration(BreakHandlerV2Config.configGroup, PERSISTED_BREAK_END_KEY);
        Microbot.getConfigManager().unsetConfiguration(BreakHandlerV2Config.configGroup, PERSISTED_BREAK_LOGOUT_KEY);
    }

    private void extendBreakUntilSchedule() {
        setBreakTimer(true);
        log.info("[BreakHandlerV2] Still outside play schedule; staying logged out until {}", breakEndTime);
    }

    private void beginPauseBreak() {
        setBreakTimer(false);
        sendBreakStartedNotification(false);
        Microbot.pauseAllScripts.set(true);
    }

    private void beginLogoutBreak() {
        setBreakTimer(true);
        sendBreakStartedNotification(true);
        transitionToState(BreakHandlerV2State.LOGOUT_REQUESTED);
    }

    private void setBreakTimer(boolean logoutBreak) {
        currentBreakDuration = calculateBreakDuration();
        breakEndTime = Instant.now().plus(currentBreakDuration, ChronoUnit.MILLIS);
        persistBreakState(logoutBreak);
    }

    private void sendBreakStartedNotification(boolean logoutBreak) {
        String message = logoutBreak
            ? "Type: Logout break\nDuration: " + (currentBreakDuration / 60000) + " minutes"
            : "Duration: " + (currentBreakDuration / 60000) + " minutes (no logout)";
        sendDiscordNotification("Break Started", message);
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
}
