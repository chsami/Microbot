package net.runelite.client.plugins.microbot.MKE.wintertodt;

import com.google.inject.Provides;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.StatChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.misc.TimeUtils;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.time.Instant;

/**
 * Enhanced Wintertodt bot plugin with improved event handling and statistics tracking.
 * Provides comprehensive monitoring of bot activities and game events.
 */
@PluginDescriptor(
        name = PluginDescriptor.MKE + " AI-Wintertodt",
        description = "Advanced Wintertodt bot with human-like behavior and robust error handling",
        tags = {"Wintertodt", "microbot", "firemaking", "minigame", "enhanced"},
        enabledByDefault = false
)
@Slf4j
public class MKE_WintertodtPlugin extends Plugin {

    // Core plugin components
    @Inject
    private MKE_WintertodtScript wintertodtScript;
    @Inject
    private MKE_WintertodtConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private MKE_WintertodtOverlay wintertodtOverlay;

    // Game statistics tracking
    @Getter(AccessLevel.PACKAGE)
    private int won;

    @Getter(AccessLevel.PACKAGE)
    private int lost;

    @Getter(AccessLevel.PACKAGE)
    private int logsCut;

    @Getter(AccessLevel.PACKAGE)
    private int logsFletched;

    @Getter(AccessLevel.PACKAGE)
    private int braziersFixed;

    @Getter(AccessLevel.PACKAGE)
    private int braziersLit;

    @Getter
    @Setter
    private int foodConsumed;

    @Getter
    @Setter
    private int timesBanked;

    @Getter(AccessLevel.PACKAGE)
    private boolean scriptStarted;

    private Instant scriptStartTime;

    // Additional tracking for enhanced features
    private int consecutiveWins;
    private int consecutiveLosses;
    private long lastActionTime;
    private boolean emergencyMode;

    /**
     * Provides the configuration instance for dependency injection.
     */
    @Provides
    MKE_WintertodtConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(MKE_WintertodtConfig.class);
    }

    /**
     * Calculates and formats the total runtime of the bot.
     * @return Formatted runtime string
     */
    protected String getTimeRunning() {
        return scriptStartTime != null ?
                TimeUtils.getFormattedDurationBetween(scriptStartTime, Instant.now()) :
                "Not started";
    }

    /**
     * Resets all statistics and tracking variables to initial state.
     */
    private void reset() {
        log.info("=== COMPLETE PLUGIN STATE RESET ===");
        
        // Reset all statistics
        this.won = 0;
        this.lost = 0;
        this.logsCut = 0;
        this.logsFletched = 0;
        this.braziersFixed = 0;
        this.braziersLit = 0;
        this.foodConsumed = 0;
        this.timesBanked = 0;
        
        // Reset runtime tracking
        this.scriptStartTime = null;
        this.scriptStarted = false;
        
        // Reset advanced tracking
        this.consecutiveWins = 0;
        this.consecutiveLosses = 0;
        this.lastActionTime = System.currentTimeMillis();
        this.emergencyMode = false;
        
        log.info("All plugin statistics and state reset to defaults");
    }

    /**
     * Handles chat messages to track game events and update statistics.
     */
    @Subscribe
    public void onChatMessage(ChatMessage chatMessage) {
        if (!scriptStarted || !isInWintertodtRegion()) {
            return;
        }

        ChatMessageType chatMessageType = chatMessage.getType();

        if (chatMessageType != ChatMessageType.GAMEMESSAGE && chatMessageType != ChatMessageType.SPAM) {
            return;
        }

        String message = chatMessage.getMessage();
        
        // Forward to script for fletching interrupt detection
        MKE_WintertodtScript.onChatMessage(message);
        
        // Process message for statistics and logging
        processGameMessage(message);
        
        // Log important messages
        logImportantMessages(message);
    }

    /**
     * Processes specific game messages to update statistics and handle events.
     * @param message Game message to process
     */
    private void processGameMessage(String message) {
        try {
            lastActionTime = System.currentTimeMillis();

            // Track resource gathering
            if (message.startsWith("You get some bruma root")) {
                logsCut++;
                log.debug("Bruma root obtained - Total logs cut: {}", logsCut);
            }

            if (message.startsWith("You carefully fletch the root")) {
                logsFletched++;
                log.debug("Root fletched - Total: {}", logsFletched);
            }

            // Track brazier interactions
            if (message.startsWith("You fix the brazier")) {
                braziersFixed++;
                log.info("Brazier fixed - Total: {}", braziersFixed);
            }

            if (message.startsWith("You light the brazier")) {
                braziersLit++;
                log.info("Brazier lit - Total: {}", braziersLit);
            }

            // Track damage events
            if (message.startsWith("The cold of")) {
                log.debug("Ambient cold damage taken");
            }

            if (message.startsWith("The freezing cold attack")) {
                log.debug("Snowfall damage taken");
            }

            if (message.startsWith("The brazier is broken and shrapnel")) {
                log.debug("Brazier explosion damage taken");
            }

            // Track inventory events
            if (message.startsWith("Your inventory is too full")) {
                log.warn("Inventory full - may need to optimize inventory management");
            }

            if (message.startsWith("You have run out of bruma roots")) {
                log.debug("Out of bruma roots");
            }

            // Track brazier state changes
            if (message.startsWith("The brazier has gone out")) {
                log.debug("Brazier went out");
            }

            // Track game outcomes
            if (message.startsWith("You have helped enough to earn a supply crate")) {
                won++;
                consecutiveWins++;
                consecutiveLosses = 0;
                log.info("=== GAME WON! === Total wins: {}, Consecutive wins: {}", won, consecutiveWins);

                // Reset emergency mode on successful game
                if (emergencyMode) {
                    resetEmergencyMode();
                }
            }

            if (message.startsWith("You did not earn enough points")) {
                lost++;
                consecutiveLosses++;
                consecutiveWins = 0;
                log.warn("=== GAME LOST === Total losses: {}, Consecutive losses: {}", lost, consecutiveLosses);

                // Enable emergency mode after multiple consecutive losses
                if (consecutiveLosses >= 3) {
                    emergencyMode = true;
                    log.error("Emergency mode ENABLED after {} consecutive losses - will be more conservative", consecutiveLosses);
                }
            }

            // Track points and rewards
            if (message.contains("supply crate")) {
                log.info("Supply crate obtained");
            }

            // Track construction XP from fixing
            if (message.contains("Construction experience")) {
                log.debug("Construction XP gained from brazier fixing");
            }

            // Track deaths or major events
            if (message.contains("Oh dear, you are dead!")) {
                log.error("PLAYER DEATH DETECTED!");
                handlePlayerDeath();
            }

        } catch (Exception e) {
            log.error("Error processing game message: '{}' - {}", message, e.getMessage(), e);
        }
    }

    /**
     * Logs important messages for debugging and monitoring.
     * @param message Game message to potentially log
     */
    private void logImportantMessages(String message) {
        try {
            // Log all wintertodt-related messages at debug level
            if (message.toLowerCase().contains("wintertodt") ||
                message.toLowerCase().contains("brazier") ||
                message.toLowerCase().contains("bruma") ||
                message.toLowerCase().contains("cold") ||
                message.toLowerCase().contains("pyromancer")) {
                log.debug("Wintertodt event: {}", message);
            }

            // Log critical events at higher levels
            if (message.contains("supply crate") ||
                message.contains("not earn enough") ||
                message.contains("helped enough")) {
                log.info("Game outcome: {}", message);
            }

            // Log damage events
            if (message.contains("damage") || 
                message.contains("cold") || 
                message.contains("shrapnel") ||
                message.contains("freezing")) {
                log.debug("Damage event: {}", message);
            }

            // Log interruption events
            if (message.contains("too full") ||
                message.contains("run out") ||
                message.contains("gone out")) {
                log.debug("Interruption event: {}", message);
            }

        } catch (Exception e) {
            log.error("Error logging message: '{}' - {}", message, e.getMessage());
        }
    }

    /**
     * Enhanced player death handling with more comprehensive recovery.
     */
    private void handlePlayerDeath() {
        log.error("=== PLAYER DEATH DETECTED ===");
        log.error("Current stats - Wins: {}, Losses: {}, Win Rate: {:.1f}%", 
                  won, lost, getTotalGames() > 0 ? (won * 100.0 / getTotalGames()) : 0);
        log.error("Actions taken - Logs: {}, Fletched: {}, Braziers Fixed: {}, Lit: {}", 
                  logsCut, logsFletched, braziersFixed, braziersLit);
        
        // Set emergency mode and stop navigation
        emergencyMode = true;
        Rs2Walker.setTarget(null);
        
        // Stop the bot safely
        log.error("Stopping bot due to player death");
        shutDown();
    }

    /**
     * Gets total games played.
     * @return total games (wins + losses)
     */
    public int getTotalGames() {
        return won + lost;
    }

    /**
     * Gets current win rate as percentage.
     * @return win rate percentage
     */
    public double getWinRate() {
        int total = getTotalGames();
        return total > 0 ? (won * 100.0 / total) : 0.0;
    }

    /**
     * Enhanced emergency mode reset with logging.
     */
    public void resetEmergencyMode() {
        if (emergencyMode) {
            emergencyMode = false;
            consecutiveLosses = 0;
            log.info("Emergency mode RESET - bot returned to normal operation");
        }
    }

    /**
     * Logs comprehensive statistics periodically.
     */
    private void logPeriodicStats() {
        if (scriptStarted && getTotalGames() > 0) {
            log.info("=== PERIODIC STATS ===");
            log.info("Runtime: {}", getTimeRunning());
            log.info("Games - Won: {}, Lost: {}, Win Rate: {:.1f}%", won, lost, getWinRate());
            log.info("Actions - Logs: {}, Fletched: {}, Food: {}, Banking: {}", 
                     logsCut, logsFletched, foodConsumed, timesBanked);
            log.info("Braziers - Fixed: {}, Lit: {}", braziersFixed, braziersLit);
            log.info("Status: {}, Emergency Mode: {}", emergencyMode ? "EMERGENCY" : "NORMAL", emergencyMode);
            
            if (consecutiveWins >= 5) {
                log.info("Hot streak! {} consecutive wins", consecutiveWins);
            }
            if (consecutiveLosses >= 2) {
                log.warn("Losing streak: {} consecutive losses", consecutiveLosses);
            }
        }
    }

    /**
     * Enhanced startup with better logging.
     */
    @Override
    protected void startUp() throws AWTException {
        log.info("=== STARTING ENHANCED WINTERTODT BOT ===");
        log.info("Version: {}", MKE_WintertodtScript.version);
        
        // ALWAYS start with a complete reset
        reset();
        
        this.scriptStartTime = Instant.now();
        this.scriptStarted = true;

        // Add overlay to display statistics
        if (overlayManager != null) {
            overlayManager.add(wintertodtOverlay);
            log.debug("Overlay added successfully");
        }

        // Reset script state before starting
        MKE_WintertodtScript.resetActions = true;
        
        // Start the main bot script
        wintertodtScript.run(config, this);

        log.info("=== WINTERTODT BOT STARTED SUCCESSFULLY ===");
        log.info("Bot will begin with fresh state and comprehensive logging enabled");
        
        // Log initial configuration
        logConfiguration();
    }

    /**
     * Logs the current configuration settings.
     */
    private void logConfiguration() {
        log.info("=== CONFIGURATION ===");
        log.info("Fletching roots: {}", config.fletchRoots());
        log.info("Fix braziers: {}", config.fixBrazier());
        log.info("Relight braziers: {}", config.relightBrazier());
        log.info("Humanized timing: {}", config.humanizedTiming());
        log.info("Random mouse movements: {}", config.randomMouseMovements());
        
        // Log healing strategy configuration
        boolean usesPotions = config.rejuvenationPotions() && !config.useFoodManagement();
        boolean usesFood = config.useFoodManagement() && !config.rejuvenationPotions();
        
        if (usesPotions) {
            log.info("Healing Strategy: Rejuvenation Potions");
        } else if (usesFood) {
            log.info("Healing Strategy: Food ({}), Amount: {}", config.food().getName(), config.foodAmount());
        } else if (config.rejuvenationPotions() && config.useFoodManagement()) {
            log.warn("Both healing methods enabled - defaulting to potions if Druidic Ritual is finished ({})", config.food().getName());
        } else {
            log.warn("No healing method enabled - defaulting to potions if Druidic Ritual is finished ({})", config.food().getName());
        }
        
        log.info("Brazier location: {}", config.brazierLocation());
    }

    /**
     * Enhanced shutdown with better logging.
     */
    @Override
    protected void shutDown() {
        log.info("=== SHUTTING DOWN WINTERTODT BOT ===");
        
        // Log final statistics
        logPeriodicStats();

        // Stop the script and remove overlay
        if (wintertodtScript != null) {
            wintertodtScript.shutdown();
            log.debug("Script shutdown completed");
        }

        if (overlayManager != null) {
            overlayManager.remove(wintertodtOverlay);
            log.debug("Overlay removed");
        }

        log.info("=== WINTERTODT BOT SHUTDOWN COMPLETED ===");
        log.info("Final stats - Runtime: {}, Games: {}, Win Rate: {:.1f}%", 
                 getTimeRunning(), getTotalGames(), getWinRate());
    }

    /**
     * Checks if the player is currently in the Wintertodt region.
     * @return true if in Wintertodt region, false otherwise
     */
    private boolean isInWintertodtRegion() {
        try {
            Player localPlayer = Microbot.getClient().getLocalPlayer();
            return localPlayer != null && localPlayer.getWorldLocation().getRegionID() == 6462;
        } catch (Exception e) {
            log.error("Error checking Wintertodt region", e);
            return false;
        }
    }

    /**
     * Handles hitsplat applied events for damage tracking.
     */
    @Subscribe
    public void onHitsplatApplied(HitsplatApplied hitsplatApplied) {
        try {
            MKE_WintertodtScript.onHitsplatApplied(hitsplatApplied);
        } catch (Exception e) {
            log.error("Error handling hitsplat event", e);
        }
    }

    /**
     * Tracks skill experience changes to update action statistics.
     */
    @Subscribe
    public void onStatChanged(StatChanged event) {
        try {
            MKE_WintertodtScript.onStatChanged(event);

            if (!scriptStarted || !isInWintertodtRegion()) {
                return;
            }

            // Track woodcutting experience gains
            if (event.getSkill() == Skill.WOODCUTTING) {
                logsCut++;
                lastActionTime = System.currentTimeMillis();
                log.debug("Log cut - Total: {}", logsCut);
            }

            // Track fletching experience gains
            if (event.getSkill() == Skill.FLETCHING) {
                logsFletched++;
                lastActionTime = System.currentTimeMillis();
                log.debug("Log fletched - Total: {}", logsFletched);
            }

            // Track other relevant skills
            if (event.getSkill() == Skill.FIREMAKING) {
                lastActionTime = System.currentTimeMillis();
                log.debug("Firemaking XP gained");
            }

            if (event.getSkill() == Skill.CONSTRUCTION) {
                lastActionTime = System.currentTimeMillis();
                log.debug("Construction XP gained (brazier fix)");
            }

        } catch (Exception e) {
            log.error("Error handling stat change event", e);
        }
    }

    /**
     * Checks if the bot is in emergency mode (after multiple failures).
     * @return true if in emergency mode
     */
    public boolean isEmergencyMode() {
        return emergencyMode;
    }

    /**
     * Gets the time since last recorded action.
     * @return milliseconds since last action
     */
    public long getTimeSinceLastAction() {
        return System.currentTimeMillis() - lastActionTime;
    }
    
    /**
     * Checks if the startup sequence has been completed.
     * @return true if startup completed
     */
    public boolean isStartupCompleted() {
        return wintertodtScript != null && wintertodtScript.isStartupCompleted();
    }
    
    /**
     * Gets the current startup phase description.
     * @return startup phase description
     */
    public String getStartupPhase() {
        if (wintertodtScript != null && wintertodtScript.getStartupManager() != null) {
            return wintertodtScript.getStartupManager().getCurrentPhase().getDescription();
        }
        return "Initializing...";
    }
    
    /**
     * Gets the current startup status message.
     * @return startup status message
     */
    public String getStartupStatus() {
        if (wintertodtScript != null && wintertodtScript.getStartupManager() != null) {
            return wintertodtScript.getStartupManager().getStatusMessage();
        }
        return "Starting up...";
    }
}