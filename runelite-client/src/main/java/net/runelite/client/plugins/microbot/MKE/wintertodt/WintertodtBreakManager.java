package net.runelite.client.plugins.microbot.MKE.wintertodt;

import lombok.Getter;
import lombok.Setter;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.MKE.wintertodt.enums.State;

import java.awt.Point;
import java.time.Duration;
import java.util.Random;

/**
 * Custom break management system for Wintertodt script.
 * Handles both short AFK breaks (mouse offscreen) and longer logout breaks.
 */
public class WintertodtBreakManager {
    
    @Getter
    private static boolean breakActive = false;
    
    @Getter
    private static boolean afkBreakActive = false;
    
    @Getter
    private static boolean logoutBreakActive = false;
    
    private static int breakTimeRemaining = 0; // in seconds
    private static int nextBreakIn = 0; // in seconds
    
    @Getter
    @Setter
    private static boolean lockState = false;
    
    private final MKE_WintertodtConfig config;
    private final Random random = new Random();
    
    // Break timing
    private long lastBreakTime = 0;
    private long nextBreakCheck = 0;
    
    @Getter
    private long waitingForSafeSpotSince = 0; // Track when we started waiting for safe spot
    
    @Getter
    private boolean isWalkingToSafeSpot = false;
    
    // AFK break data
    private Point originalMousePosition = null;
    private boolean mouseOffscreen = false;
    
    // Safe locations for breaks
    public static final WorldPoint BANK_LOCATION = new WorldPoint(1640, 3944, 0);
    public static final WorldPoint BOSS_ROOM = new WorldPoint(1630, 3982, 0);
    public static final int SAFE_SPOT_RADIUS = 3; // Radius around boss room for safe spot
    private static final long MAX_WAIT_FOR_SAFE_SPOT_MS = 600000; // 10 minutes
    
    public enum BreakType {
        AFK_SHORT("AFK Break", "Moving mouse offscreen for 1-6 minutes"),
        LOGOUT_LONG("Logout Break", "Logging out for 5-40 minutes");
        
        private final String name;
        private final String description;
        
        BreakType(String name, String description) {
            this.name = name;
            this.description = description;
        }
        
        public String getName() { return name; }
        public String getDescription() { return description; }
    }
    
    public WintertodtBreakManager(MKE_WintertodtConfig config) {
        this.config = config;
        initializeBreakTimer();
    }
    
    /**
     * Initialize the break timer with random interval
     */
    private void initializeBreakTimer() {
        if (!config.enableCustomBreaks()) {
            return;
        }
        
        // Schedule first break randomly between min and max interval
        int minInterval = config.minBreakInterval();
        int maxInterval = config.maxBreakInterval();
        nextBreakIn = Rs2Random.between(minInterval * 60, maxInterval * 60);
        
        Microbot.log(String.format("Next break scheduled in %d minutes", nextBreakIn / 60));
    }
    
    /**
     * Main update method - call this from the script loop
     */
    public boolean update() {
        try {
            if (!config.enableCustomBreaks()) {
                return false;
            }
            
            long currentTime = System.currentTimeMillis();
            
            // Check if we should evaluate for a break
            if (currentTime >= nextBreakCheck) {
                nextBreakCheck = currentTime + 1000; // Check every second
                
                // Countdown to next break
                if (nextBreakIn > 0 && !breakActive) {
                    nextBreakIn--;
                }
                
                // Countdown active break
                if (breakTimeRemaining > 0 && breakActive) {
                    breakTimeRemaining--;
                }
                
                // Time to start a break
                if (nextBreakIn <= 0 && !breakActive && !lockState) {
                    if (isInSafeLocationForBreak()) {
                        // Reset safe spot waiting timer since we found a safe spot
                        waitingForSafeSpotSince = 0;
                        isWalkingToSafeSpot = false;
                        startRandomBreak();
                    } else {
                        // Start tracking how long we've been waiting for safe spot
                        if (waitingForSafeSpotSince == 0) {
                            waitingForSafeSpotSince = currentTime;
                            Microbot.log("Started waiting for safe location for break");
                        }
                        
                        // Check if we've been waiting too long (10 minutes)
                        long waitingTime = currentTime - waitingForSafeSpotSince;
                        if (waitingTime >= MAX_WAIT_FOR_SAFE_SPOT_MS) {
                            if (!isWalkingToSafeSpot) {
                                Microbot.log("Waiting for safe spot timed out, requesting walk to boss room safe area");
                                isWalkingToSafeSpot = true;
                                return true; // Signal to walk
                            } else if (isInSafeLocationForBreak()) {
                                // We've arrived at the safe spot
                                Microbot.log("Arrived at safe spot, starting break");
                                waitingForSafeSpotSince = 0;
                                isWalkingToSafeSpot = false;
                                startRandomBreak();
                            }
                        } else {
                            // Delay break by 30 seconds if not in safe location and haven't waited too long
                            nextBreakIn = 30;
                            long remainingWait = (MAX_WAIT_FOR_SAFE_SPOT_MS - waitingTime) / 1000;
                            if (remainingWait % 60 == 0) { // Log every minute
                                Microbot.log("Delaying break - not in safe location (will force walk in " + remainingWait / 60 + " minutes)");
                            }
                        }
                    }
                }
                
                // Time to end a break
                if (breakTimeRemaining <= 0 && breakActive) {
                    endCurrentBreak();
                }
            }
            
        } catch (Exception e) {
            Microbot.log("Error in WintertodtBreakManager: " + e.getMessage());
            e.printStackTrace();
            // Emergency cleanup
            emergencyCleanup();
        }
        return false;
    }
    
    /**
     * Start a random break (either AFK or logout)
     */
    private void startRandomBreak() {
        // Determine break type based on config percentages
        boolean shouldLogout = random.nextInt(100) < config.logoutBreakChance();
        
        if (shouldLogout) {
            startLogoutBreak();
        } else {
            startAfkBreak();
        }
    }
    
    /**
     * Start an AFK break (mouse offscreen)
     */
    private void startAfkBreak() {
        int duration = Rs2Random.between(config.afkBreakMinDuration(), config.afkBreakMaxDuration());
        breakTimeRemaining = duration * 60; // convert to seconds
        
        afkBreakActive = true;
        breakActive = true;
        
        // Store original mouse position
        originalMousePosition = Microbot.getMouse().getMousePosition();
        
        // Move mouse offscreen
        Microbot.naturalMouse.moveOffScreen(90);
        
        lastBreakTime = System.currentTimeMillis();
        
        Microbot.log(String.format("Started AFK break for %d minutes", duration));
    }
    
    /**
     * Start a logout break
     */
    private void startLogoutBreak() {
        int duration = Rs2Random.between(config.logoutBreakMinDuration(), config.logoutBreakMaxDuration());
        breakTimeRemaining = duration * 60; // convert to seconds
        
        logoutBreakActive = true;
        breakActive = true;
        
        // Logout the player
        Rs2Player.logout();
        
        lastBreakTime = System.currentTimeMillis();
        
        Microbot.log(String.format("Started logout break for %d minutes", duration));
    }
    
    /**
     * End the current break
     */
    private void endCurrentBreak() {
        if (afkBreakActive) {
            endAfkBreak();
        } else if (logoutBreakActive) {
            endLogoutBreak();
        }
        
        // Schedule next break
        scheduleNextBreak();
    }
    
    /**
     * End AFK break
     */
    private void endAfkBreak() {
        afkBreakActive = false;
        breakActive = false;
        mouseOffscreen = false;
        
        // Restore mouse position with some randomization
        if (originalMousePosition != null) {
            int deltaX = random.nextInt(100) - 50;
            int deltaY = random.nextInt(100) - 50;
            Microbot.getMouse().move(
                originalMousePosition.x + deltaX, 
                originalMousePosition.y + deltaY
            );
            originalMousePosition = null;
        }
        
        Microbot.log("AFK break ended - resuming activities");
    }
    
    /**
     * End logout break
     */
    private void endLogoutBreak() {
        logoutBreakActive = false;
        breakActive = false;
        
        Microbot.log("Logout break ended - ready to resume");
        // Note: The script will handle re-login logic
    }
    
    /**
     * Schedule the next break
     */
    private void scheduleNextBreak() {
        int minInterval = config.minBreakInterval();
        int maxInterval = config.maxBreakInterval();
        nextBreakIn = Rs2Random.between(minInterval * 60, maxInterval * 60);
        
        Microbot.log(String.format("Next break scheduled in %d minutes", nextBreakIn / 60));
    }
    
    /**
     * Check if player is in a safe location for breaks
     */
    private boolean isInSafeLocationForBreak() {
        try {
            WorldPoint playerLocation = Rs2Player.getWorldLocation();
            if (playerLocation == null) return false;
            
            // Safe at bank
            if (playerLocation.distanceTo(BANK_LOCATION) <= 10) {
                return true;
            }
            
            // Safe at boss room when not actively doing anything (expanded to include safe spot radius)
            if (playerLocation.distanceTo(BOSS_ROOM) <= Math.max(6, SAFE_SPOT_RADIUS) && 
                !Rs2Player.isAnimating() && 
                !Rs2Player.isMoving() &&
                !Rs2Player.isInteracting()) {
                return true;
            }
            
            // Safe when in BANKING or WAITING states (expanded to include safe spot radius)
            State currentState = MKE_WintertodtScript.state;
            if ((currentState == State.BANKING || currentState == State.WAITING) && 
                playerLocation.distanceTo(BOSS_ROOM) <= Math.max(6, SAFE_SPOT_RADIUS)) {
                return true;
            }
            
            // Force safe when we're walking to safe spot and arrived
            if (isWalkingToSafeSpot && playerLocation.distanceTo(BOSS_ROOM) <= SAFE_SPOT_RADIUS &&
                !Rs2Player.isAnimating() && !Rs2Player.isMoving()) {
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            Microbot.log("Error checking safe location: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Emergency cleanup in case of errors
     */
    private void emergencyCleanup() {
        breakActive = false;
        afkBreakActive = false;
        logoutBreakActive = false;
        mouseOffscreen = false;
        originalMousePosition = null;
        breakTimeRemaining = 0;
    }
    
    /**
     * Get formatted time remaining in current break
     */
    public static String getBreakTimeRemaining() {
        if (!breakActive) return "No break active";
        
        Duration duration = Duration.ofSeconds(breakTimeRemaining);
        long hours = duration.toHours();
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.getSeconds() % 60;
        
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
    
    /**
     * Get formatted time until next break
     */
    public static String getTimeUntilNextBreak() {
        if (breakActive) return "Break active";
        
        Duration duration = Duration.ofSeconds(nextBreakIn);
        long hours = duration.toHours();
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.getSeconds() % 60;
        
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
    
    /**
     * Force start a break (for manual trigger)
     */
    public void forceBreak(BreakType type) {
        if (breakActive) {
            Microbot.log("Break already active, ignoring force break request");
            return;
        }
        
        if (type == BreakType.AFK_SHORT) {
            startAfkBreak();
        } else {
            startLogoutBreak();
        }
    }
    
    /**
     * Force end current break
     */
    public void forceEndBreak() {
        if (!breakActive) {
            return;
        }
        
        breakTimeRemaining = 0;
        endCurrentBreak();
    }
    
    /**
     * Reset the break manager
     */
    public void reset() {
        emergencyCleanup();
        waitingForSafeSpotSince = 0;
        isWalkingToSafeSpot = false;
        initializeBreakTimer();
    }
    
    /**
     * Shutdown the break manager
     */
    public void shutdown() {
        emergencyCleanup();
        nextBreakIn = 0;
        waitingForSafeSpotSince = 0;
        isWalkingToSafeSpot = false;
        Microbot.log("Break manager shutdown");
    }
} 