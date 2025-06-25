package net.runelite.client.plugins.microbot.MKE.wintertodt;

import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.StatChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.MKE.wintertodt.enums.State;
import net.runelite.client.plugins.microbot.MKE.wintertodt.startup.WintertodtStartupManager;
import net.runelite.client.plugins.microbot.MKE.wintertodt.startup.gear.WintertodtAxeManager;
import net.runelite.client.plugins.microbot.MKE.wintertodt.startup.gear.WintertodtGearManager;
import net.runelite.client.plugins.microbot.MKE.wintertodt.startup.location.WintertodtLocationManager;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.runelite.api.Constants.GAME_TICK_LENGTH;
import static net.runelite.api.ObjectID.*;
import static net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory.isItemSelected;
import static net.runelite.client.plugins.microbot.util.player.Rs2Player.eatAt;

/**
 * Enhanced Wintertodt bot script with improved state management,
 * human-like behaviors, and robust error handling.
 *
 * Features:
 * - Intelligent state machine with proper state locking
 * - Human-like timing variations and micro-behaviors
 * - Comprehensive error handling and recovery
 * - Advanced warmth and health management
 * - Optimized resource collection and usage
 * - Anti-detection measures and natural mouse movements
 * - Integrated break system with WintertodtBreakManager support
 * - Proper break handler state management
 * - Emergency situation handling during breaks
 *
 * Break System Integration:
 * - Respects BreakHandlerScript lock states
 * - Supports Rs2Antiban features
 * - Handles break triggers from multiple sources
 * - Maintains critical functions during breaks (eating, emergency banking)
 * - Proper state transitions in and out of breaks
 *
 * Enhanced Antiban Integration:
 * - Uses Rs2Antiban firemaking setup template
 * - Proper action cooldown integration with custom logic
 * - Mouse movement randomization for natural behavior
 * - Overlay integration showing antiban status
 * - Activity-specific behavior patterns for Wintertodt
 *
 * WintertodtBreakManager:
 * - Smart custom break system with AFK and logout breaks
 * - Location-aware break triggers (only in safe areas)
 * - Configurable break intervals and durations
 * - Natural break activities and timing patterns
 * - Separate from Break Handler (for longer breaks)
 *
 * @version 1.0.0
 * @author MakeCD
 */
public class MKE_WintertodtScript extends Script {
    public static final String version = "1.0.0";

    // State management
    public static State state = State.BANKING;
    public static boolean resetActions = false;
    private static boolean lockState = false;
    private static long lastStateChange = System.currentTimeMillis();

    // Configuration and plugin references
    static MKE_WintertodtConfig config;
    static MKE_WintertodtPlugin plugin;
    
    // Startup system
    private WintertodtStartupManager startupManager;
    
    // Custom break management system
    private WintertodtBreakManager breakManager;

    // World locations for key areas
    private final WorldPoint BOSS_ROOM = new WorldPoint(1630, 3982, 0);
    private final WorldPoint CRATE_LOCATION = new WorldPoint(1634, 3982, 0);
    private final WorldPoint CRATE_STAND_LOCATION = new WorldPoint(1633, 3982, 0);
    private final WorldPoint SPROUTING_ROOTS = new WorldPoint(1635, 3978, 0);
    private final WorldPoint SPROUTING_ROOTS_STAND = new WorldPoint(1634, 3978, 0);
    private final WorldPoint BANK_LOCATION = new WorldPoint(1640, 3944, 0);

    // Game state tracking
    private GameObject currentBrazier;
    private boolean isInitialized = false;
    private boolean startupCompleted = false;
    private Random random = new Random();
    private boolean wasOnBreak = false;

    // Wintertodt round timer tracking
    private long roundEndTime = -1;
    private long nextRoundStartTime = -1;
    private boolean mouseMovedOffscreenForRound = false;
    private boolean hoveredForNextRound = false;
    private boolean isHoverBeforeStartTimeCalculated = false;
    private int hoverBeforeStartTime = 0; // Time in ms before round start to begin hovering
    private int lastKnownRemainingSeconds = -1;

    // Spam clicking variables for natural game start behavior
    private boolean spamClickingActive = false;
    private long spamClickStartTime = 0;
    private long spamClickEndTime = 0;
    private GameObject spamClickTarget = null;
    private int spamClicksPerformed = 0;
    private long lastSpamClick = 0;

    // For historical round time tracking
    private final java.util.List<Long> previousRoundDurations = new java.util.LinkedList<>();
    private long currentRoundStartTime = 0;

    // Flag to prioritize brazier lighting at round start
    private boolean shouldPriorizeBrazierAtStart = false;

    // For overlay
    public static double historicalEstimateSecondsLeft = 0;

    // --- Action Timers based on XP Drops ---
    private static long lastWoodcuttingXpDropTime = 0;
    private static long lastFletchingXpDropTime = 0;
    private static long lastFiremakingXpDropTime = 0;

    // --- live HP-rate tracking & action duration averages --------------
    private int     prevWintertodtHp     = -1;
    private long    prevHpTimestamp      = 0;
    private double  hpPerSecond          = 0;      // averaged team DPS
    public static double  estimatedSecondsLeft = 999;
    private double addToEstimatedTimePerCycleMs = 1200;

    // average action durations (ms) – refined live while the script runs
    public static double  avgChopMs   = 2800;
    public static double  avgFletchMs = 2200;
    public static double  avgFeedMs   = 1600;

    public static double cycleTimeSec = 0;
    public static int maxCyclesPossible = 0;

    // --- Action Plan ---
    private static int targetRootsForThisRun = 0;
    public static int rootsToChopGoal = 0;
    public static int currentBurnableCount = 0;

    /* per-run fletch / feed goals & progress ----------------------*/
    public static int fletchGoal       = 0;
    public static int feedGoal         = 0;
    public static int fletchedThisRun  = 0;
    public static int fedThisRun       = 0;

    private static final double SAFETY_BUFFER_SEC = 5; // keep this free for walking, delays, flame-out, etc.
    
    /* How much extra we chop on top of the strict time calculation
       (makes sure we don't run out of logs when estimates are a bit optimistic) */
    public static final int EXTRA_ROOTS_BUFFER = 2;
    // --------------------------------------------------------------------------

    // Performance tracking
    private long lastPerformanceCheck = 0;
    private int actionsPerformed = 0;
    private int consecutiveFailures = 0;
    
    // Mouse and camera movement tracking
    private long lastMouseMovement = 0;
    private long lastCameraMovement = 0;
    private static final int CAMERA_MOVEMENT_MIN_DELAY = 8000; // 8 seconds minimum

    // ────────────── HEALING STRATEGY VARIABLES ──────────────────────────────────
    private boolean usesPotions = false;

    /* Default round length (ms) used until we have real data */
    private static final long DEFAULT_ROUND_DURATION_MS = 250_000;

    /** max distance (tiles) from our brazier at which chopping is allowed */
    private static final int CHOPPING_RADIUS = 10;

    // Object IDs for rejuvenation potion creation
    private static final int CRATE_OBJECT_ID = 29320; // Crate for concoctions
    private static final int SPROUTING_ROOTS_OBJECT_ID = 29315; // Sprouting roots for herbs
    

    /* Returns the closest Bruma root that is on the same side as the
       selected brazier (<= 8 tiles from that brazier). */
    private GameObject getOwnSideRoot()
    {
        WorldPoint ref = config.brazierLocation().getBRAZIER_LOCATION();
        return Rs2GameObject.getGameObjects(10).stream()
                .filter(o -> o.getId() == ObjectID.BRUMA_ROOTS)
                .filter(o -> o.getWorldLocation().distanceTo(ref) <= 10)
                .min(java.util.Comparator.comparingInt(o -> o.getWorldLocation()
                                                           .distanceTo(ref)))
                .orElse(null);
    }

    // ---------------------------------------------------------------

    // ----------------  event hook  ---------------------------------
    public static void onStatChanged(StatChanged event)
    {
        long now = System.currentTimeMillis();
        long duration;

        switch (event.getSkill()) {
            case WOODCUTTING:
                if (state == State.CHOP_ROOTS)
                {
                    // ---------- root counter -----------
                    if (targetRootsForThisRun > 0)
                        rootsChoppedThisRun++;

                    if (rootsChoppedThisRun >= targetRootsForThisRun && targetRootsForThisRun > 0)
                    {
                        setLockState(State.CHOP_ROOTS, false);
                        changeState(State.WAITING);
                        targetRootsForThisRun = 0;
                        rootsChoppedThisRun   = 0;
                        return;                           // stop further processing
                    }
                    // ----------------------------------------

                    if (lastWoodcuttingXpDropTime > 0)
                    {
                        duration = now - lastWoodcuttingXpDropTime;
                        if (duration > 600 && duration < 10000)
                            noteActionDuration("CHOP", duration);
                    }
                    lastWoodcuttingXpDropTime = now;
                }
                break;
            case FLETCHING:
                if (state == State.FLETCH_LOGS) {
                    if (fletchGoal > 0) fletchedThisRun++;   // progress
                    if (lastFletchingXpDropTime > 0) {
                        duration = now - lastFletchingXpDropTime;
                        if (duration > 600 && duration < 10000) { // Sanity check
                            noteActionDuration("FLETCH", duration);
                        }
                    }
                    lastFletchingXpDropTime = now;
                }
                break;
            case FIREMAKING:
                if (state == State.BURN_LOGS) {
                    if (feedGoal > 0)  fedThisRun++;        // progress
                    if (lastFiremakingXpDropTime > 0) {
                        duration = now - lastFiremakingXpDropTime;
                        if (duration > 600 && duration < 10000) { // Sanity check
                            noteActionDuration("FEED", duration);
                        }
                    }
                    lastFiremakingXpDropTime = now;
                }
                break;
        }
    }
    // ---------------------------------------------------------------

    // --------------- planning a new run ----------------------------
    public  static int rootsChoppedThisRun   = 0;


    /**
     * Enum for tracking what caused fletching to stop
     */
    public enum FletchingInterruptType {
        DAMAGE_COLD("Damaged by Wintertodt Cold"),
        DAMAGE_SNOWFALL("Damaged by Wintertodt Snowfall"), 
        DAMAGE_BRAZIER("Brazier Shattered"),
        OUT_OF_ROOTS("All roots fletched"),
        INVENTORY_FULL("Inventory became full"),
        ROUND_ENDED("Wintertodt round ended"),
        NO_MORE_ROOTS("No more roots to fletch"),
        PLAYER_DIED("Player died"),
        PLAYER_MOVED("Player started moving"),
        PLAYER_ATE("Player ate food"),
        BRAZIER_WENT_OUT("Brazier went out"),
        BRAZIER_BROKEN("Brazier broke"),
        MANUAL_STOP("Manually stopped"),
        TIMEOUT("Fletching timeout"),
        ANIMATION_TIMEOUT("No fletching animation for 3+ seconds"),
        UNKNOWN("Unknown cause");
        
        private final String description;
        
        FletchingInterruptType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }

    /**
     * Enum for tracking what caused feeding to stop
     */
    public enum FeedingInterruptType {
        DAMAGE_COLD("Damaged by Wintertodt Cold"),
        DAMAGE_SNOWFALL("Damaged by Wintertodt Snowfall"), 
        DAMAGE_BRAZIER("Brazier Shattered"),
        OUT_OF_ITEMS("All items fed"),
        INVENTORY_EMPTY("No more items to feed"),
        ROUND_ENDED("Wintertodt round ended"),
        NO_BURNING_BRAZIER("Brazier went out"),
        PLAYER_DIED("Player died"),
        PLAYER_MOVED("Player started moving"),
        PLAYER_ATE("Player ate food"),
        BRAZIER_WENT_OUT("Brazier went out"),
        BRAZIER_BROKEN("Brazier broke"),
        MANUAL_STOP("Manually stopped"),
        TIMEOUT("Feeding timeout"),
        ANIMATION_TIMEOUT("No feeding animation for 3+ seconds"),
        WARMTH_TOO_LOW("Warmth too low, need to eat"),
        UNKNOWN("Unknown cause");
        
        private final String description;
        
        FeedingInterruptType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }

    /**
     * Class to track fletching state robustly
     */
    public static class FletchingState {
        private boolean isActive = false;
        private long startTime = 0;
        private int rootsAtStart = 0;
        private WorldPoint startLocation = null;
        private double healthAtStart = 0;
        private int warmthAtStart = 0;
        private FletchingInterruptType lastInterruptType = null;
        private long lastInterruptTime = 0;
        
        public void startFletching() {
            isActive = true;
            startTime = System.currentTimeMillis();
            rootsAtStart = Rs2Inventory.count(ItemID.BRUMA_ROOT);
            startLocation = Rs2Player.getWorldLocation();
            healthAtStart = Rs2Player.getHealthPercentage();
            warmthAtStart = getWarmthLevel();
            lastInterruptType = null;
            Microbot.log("Fletching started - tracking " + rootsAtStart + " roots");
        }
        
        public void stopFletching(FletchingInterruptType reason) {
            if (isActive) {
                isActive = false;
                lastInterruptType = reason;
                lastInterruptTime = System.currentTimeMillis();
                long duration = lastInterruptTime - startTime;
                Microbot.log("Fletching stopped: " + reason.getDescription() + " (Duration: " + duration + "ms)");
            }
        }
        
        public boolean isActive() {
            return isActive;
        }
        
        public long getStartTime() {
            return startTime;
        }
        
        public int getRootsAtStart() {
            return rootsAtStart;
        }
        
        public WorldPoint getStartLocation() {
            return startLocation;
        }
        
        public double getHealthAtStart() {
            return healthAtStart;
        }

        public int getWarmthAtStart() {
            return warmthAtStart;
        }
        
        public FletchingInterruptType getLastInterruptType() {
            return lastInterruptType;
        }
        
        public long getDuration() {
            return isActive ? System.currentTimeMillis() - startTime : lastInterruptTime - startTime;
        }
    }

    /**
     * Class to track feeding state robustly
     */
    public static class FeedingState {
        private boolean isActive = false;
        private long startTime = 0;
        private int itemsAtStart = 0;
        private WorldPoint startLocation = null;
        private double healthAtStart = 0;
        private int warmthAtStart = 0;
        private FeedingInterruptType lastInterruptType = null;
        private long lastInterruptTime = 0;
        
        public void startFeeding() {
            isActive = true;
            startTime = System.currentTimeMillis();
            itemsAtStart = Rs2Inventory.count(ItemID.BRUMA_ROOT) + Rs2Inventory.count(ItemID.BRUMA_KINDLING);
            startLocation = Rs2Player.getWorldLocation();
            healthAtStart = Rs2Player.getHealthPercentage();
            warmthAtStart = getWarmthLevel();
            lastInterruptType = null;
            Microbot.log("Feeding started - tracking " + itemsAtStart + " items");
        }
        
        public void stopFeeding(FeedingInterruptType reason) {
            if (isActive) {
                isActive = false;
                lastInterruptType = reason;
                lastInterruptTime = System.currentTimeMillis();
                long duration = lastInterruptTime - startTime;
                Microbot.log("Feeding stopped: " + reason.getDescription() + " (Duration: " + duration + "ms)");
            }
        }
        
        public boolean isActive() {
            return isActive;
        }
        
        public long getStartTime() {
            return startTime;
        }
        
        public int getItemsAtStart() {
            return itemsAtStart;
        }
        
        public WorldPoint getStartLocation() {
            return startLocation;
        }
        
        public double getHealthAtStart() {
            return healthAtStart;
        }
        
        public int getWarmthAtStart() {
            return warmthAtStart;
        }
        
        public FeedingInterruptType getLastInterruptType() {
            return lastInterruptType;
        }
        
        public long getDuration() {
            return isActive ? System.currentTimeMillis() - startTime : lastInterruptTime - startTime;
        }
    }

    private static FletchingState fletchingState = new FletchingState();
    private static FeedingState feedingState = new FeedingState();

    /**
     * Comprehensive fletching state checker that only returns false when specific stopping conditions are met
     */
    private boolean isCurrentlyFletching() {
        // If we haven't started fletching, return false
        if (!fletchingState.isActive()) {
            return false;
        }
        // Check all possible interruption conditions
        FletchingInterruptType interruptType = checkFletchingInterruptions();
        if (interruptType != null) {
            fletchingState.stopFletching(interruptType);
            return false;
        }
        // If no interruptions detected and we started fletching, we're still fletching
        return true;
    }

    /**
     * Checks for all possible fletching interruption conditions
     */
    private FletchingInterruptType checkFletchingInterruptions() {
        // Check if we ran out of roots
        int currentRoots = Rs2Inventory.count(ItemID.BRUMA_ROOT);
        if (currentRoots == 0) {
            return FletchingInterruptType.OUT_OF_ROOTS;
        }
        
        // Check if round ended
        GameState gameState = analyzeGameState();
        if (!gameState.isWintertodtAlive || gameState.wintertodtHp == 0) {
            return FletchingInterruptType.ROUND_ENDED;
        }
        
        // Check if we have no more roots to fletch
        if (Rs2Inventory.count(ItemID.BRUMA_ROOT) == 0) {
            return FletchingInterruptType.NO_MORE_ROOTS;
        }
        
        // Check if player moved from start location
        WorldPoint currentLocation = Rs2Player.getWorldLocation();
        if (!fletchingState.getStartLocation().equals(currentLocation)) {
            return FletchingInterruptType.PLAYER_MOVED;
        }
        
        // Check if player ate (warmth increased) (Foods usually increase at least 30 warmth)
        if (getWarmthLevel() > fletchingState.getWarmthAtStart() + 29) {
            return FletchingInterruptType.PLAYER_ATE;
        }
        
        // Check for fletching animation timeout (no animation seen for 3+ seconds)
        long currentTime = System.currentTimeMillis();
        if (lastFletchingAnimationTime > 0 && 
            currentTime - lastFletchingAnimationTime > FLETCHING_ANIMATION_TIMEOUT) {
            return FletchingInterruptType.ANIMATION_TIMEOUT;
        }
        
        // Check for timeout (fletching taking too long)
        long duration = fletchingState.getDuration();
        if (duration > 60000) { // 60 seconds timeout
            return FletchingInterruptType.TIMEOUT;
        }
        
        // Check if brazier went out or broke (priority to fix)
        if (gameState.brokenBrazier != null || 
            (gameState.burningBrazier == null && gameState.brazier != null)) {
            return FletchingInterruptType.BRAZIER_WENT_OUT;
        }
        
        return null; // No interruption detected
    }

    /**
     * Comprehensive feeding state checker that only returns false when specific stopping conditions are met
     */
    private boolean isCurrentlyFeeding() {
        // If we haven't started feeding, return false
        if (!feedingState.isActive()) {
            return false;
        }
        
        // Check all possible interruption conditions
        FeedingInterruptType interruptType = checkFeedingInterruptions();
        if (interruptType != null) {
            feedingState.stopFeeding(interruptType);
            return false;
        }
        
        // If no interruptions detected and we started feeding, we're still feeding
        return true;
    }

    /**
     * Checks for all possible feeding interruption conditions
     */
    private FeedingInterruptType checkFeedingInterruptions() {
        // Check if we ran out of items to feed
        int currentItems = Rs2Inventory.count(ItemID.BRUMA_ROOT) + Rs2Inventory.count(ItemID.BRUMA_KINDLING);
        if (currentItems == 0) {
            return FeedingInterruptType.OUT_OF_ITEMS;
        }
        
        // Check if round ended
        GameState gameState = analyzeGameState();
        if (!gameState.isWintertodtAlive || gameState.wintertodtHp == 0) {
            return FeedingInterruptType.ROUND_ENDED;
        }
        
        // Check if brazier went out or is broken
        if (gameState.burningBrazier == null) {
            if (gameState.brokenBrazier != null) {
                return FeedingInterruptType.BRAZIER_BROKEN;
            } else {
                return FeedingInterruptType.BRAZIER_WENT_OUT;
            }
        }
        
        // Check if player moved from start location
        WorldPoint currentLocation = Rs2Player.getWorldLocation();
        if (!feedingState.getStartLocation().equals(currentLocation)) {
            return FeedingInterruptType.PLAYER_MOVED;
        }
        
        // Check if player ate (warmth increased)
        if (getWarmthLevel() > feedingState.getWarmthAtStart() + 29) {
            return FeedingInterruptType.PLAYER_ATE;
        }
        
        // Check for feeding animation timeout (no animation seen for 3+ seconds)
        long currentTime = System.currentTimeMillis();
        if (lastFeedingAnimationTime > 0 && 
            currentTime - lastFeedingAnimationTime > FEEDING_ANIMATION_TIMEOUT) {
            return FeedingInterruptType.ANIMATION_TIMEOUT;
        }
        
        // Check for timeout (feeding taking too long)
        long duration = feedingState.getDuration();
        if (duration > 45000) { // 45 seconds timeout
            return FeedingInterruptType.TIMEOUT;
        }
        
        // Check if warmth is too low (need to eat)
        int currentWarmth = getWarmthLevel();
        if (currentWarmth <= config.eatAtWarmthLevel()) {
            return FeedingInterruptType.WARMTH_TOO_LOW;
        }
        
        return null; // No interruption detected
    }

    /**
     * Chat message handler for fletching and feeding interruptions
     */
    public static void handleChatInterruption(String message) {
        // Handle fletching interruptions
        if (fletchingState.isActive()) {
            FletchingInterruptType fletchingInterruptType = null;
            
            if (message.startsWith("The cold of")) {
                fletchingInterruptType = FletchingInterruptType.DAMAGE_COLD;
            } else if (message.startsWith("The freezing cold attack")) {
                fletchingInterruptType = FletchingInterruptType.DAMAGE_SNOWFALL;
            } else if (message.startsWith("The brazier is broken and shrapnel")) {
                fletchingInterruptType = FletchingInterruptType.DAMAGE_BRAZIER;
            } else if (message.startsWith("The brazier has gone out")) {
                fletchingInterruptType = FletchingInterruptType.BRAZIER_WENT_OUT;
            } else if (message.startsWith("Your inventory is too full")) {
                fletchingInterruptType = FletchingInterruptType.INVENTORY_FULL;
            } else if (message.startsWith("You have run out of bruma roots")) {
                fletchingInterruptType = FletchingInterruptType.OUT_OF_ROOTS;
            } else if (message.startsWith("You fix the brazier")) {
                // Brazier was fixed - allow fletching to resume
                Microbot.log("Brazier fixed - fletching can resume");
                return; // Don't stop fletching, just log
            } else if (message.startsWith("You light the brazier")) {
                // Brazier was lit - allow fletching to resume
                Microbot.log("Brazier lit - fletching can resume");
                return; // Don't stop fletching, just log
            }
            
            if (fletchingInterruptType != null) {
                fletchingState.stopFletching(fletchingInterruptType);
                resetActions = true;
                // Reset animation tracking when interrupted
                lastFletchingAnimationTime = 0;
            }
        }
        
        // Handle feeding interruptions
        if (feedingState.isActive()) {
            FeedingInterruptType feedingInterruptType = null;
            
            if (message.startsWith("The cold of")) {
                feedingInterruptType = FeedingInterruptType.DAMAGE_COLD;
            } else if (message.startsWith("The freezing cold attack")) {
                feedingInterruptType = FeedingInterruptType.DAMAGE_SNOWFALL;
            } else if (message.startsWith("The brazier is broken and shrapnel")) {
                feedingInterruptType = FeedingInterruptType.DAMAGE_BRAZIER;
            } else if (message.startsWith("The brazier has gone out")) {
                feedingInterruptType = FeedingInterruptType.BRAZIER_WENT_OUT;
            } else if (message.startsWith("Your inventory is too full")) {
                feedingInterruptType = FeedingInterruptType.INVENTORY_EMPTY;
            } else if (message.startsWith("You have run out of bruma")) {
                feedingInterruptType = FeedingInterruptType.OUT_OF_ITEMS;
            } else if (message.startsWith("You fix the brazier")) {
                // Brazier was fixed - allow feeding to resume
                Microbot.log("Brazier fixed - feeding can resume");
                return; // Don't stop feeding, just log
            } else if (message.startsWith("You light the brazier")) {
                // Brazier was lit - allow feeding to resume
                Microbot.log("Brazier lit - feeding can resume");
                return; // Don't stop feeding, just log
            }
            
            if (feedingInterruptType != null) {
                feedingState.stopFeeding(feedingInterruptType);
                resetActions = true;
                // Reset animation tracking when interrupted
                lastFeedingAnimationTime = 0;
            }
        }
    }

    /**
     * Changes the bot's current state with optional state locking.
     * Includes logging and timing for debugging purposes.
     *
     * @param newState The state to transition to
     */
    private static void changeState(State newState) {
        changeState(newState, false);
    }

    /**
     * Changes the bot's current state with optional state locking.
     *
     * @param newState The state to transition to
     * @param lock Whether to lock the state against changes
     */
    private static void changeState(State newState, boolean lock) {
        // Prevent state changes if currently locked or trying to change to same state
        if (state == newState || lockState) {
            return;
        }

        /* ────────── graceful exit from FLETCH_LOGS ────────── */
        if (state == State.FLETCH_LOGS && fletchingState.isActive()) {
            // Make sure the tracking object is closed properly
            fletchingState.stopFletching(FletchingInterruptType.MANUAL_STOP);
            // Reset animation watchdog so stale timestamps aren't carried over
            lastFletchingAnimationTime = 0;
            // Ensure we never carry the lock of the previous state forward
            setLockState(State.FLETCH_LOGS, false);
        }
        
        /* ────────── graceful exit from BURN_LOGS ─────────── */
        if (state == State.BURN_LOGS && feedingState.isActive()) {
            // Make sure the feeding tracking object is closed properly
            feedingState.stopFeeding(FeedingInterruptType.MANUAL_STOP);
            // Reset animation watchdog so stale timestamps aren't carried over
            lastFeedingAnimationTime = 0;
            // Ensure we never carry the lock of the previous state forward
            setLockState(State.BURN_LOGS, false);
        }
        /* ─────────────────────────────────────────────────────────── */

        // Reset XP timers on state change to not carry over duration calculations
        if (state == State.CHOP_ROOTS)  lastWoodcuttingXpDropTime = 0;
        if (state == State.FLETCH_LOGS) lastFletchingXpDropTime   = 0;
        if (state == State.BURN_LOGS)   lastFiremakingXpDropTime  = 0;

        System.out.println(String.format("[%d] State transition: %s -> %s %s",
                System.currentTimeMillis(), state, newState, lock ? "(LOCKED)" : ""));

        state = newState;
        resetActions = true;
        lastStateChange = System.currentTimeMillis();
        setLockState(newState, lock);
    }

    /**
     * Sets the state lock to prevent unwanted state changes during critical operations.
     *
     * @param currentState The current state being locked/unlocked
     * @param lock Whether to enable or disable the lock
     */
    private static void setLockState(State currentState, boolean lock) {
        if (lockState == lock) return;

        lockState = lock;
        System.out.println(String.format("State %s has %s state locking",
                currentState.toString(), lock ? "ENABLED" : "DISABLED"));
    }

    /**
     * Determines if the bot should start fletching roots based on configuration and inventory.
     *
     * @return true if should fletch roots, false otherwise
     */
    private static boolean shouldFletchRoots() {
        // Check if fletching is enabled in config
        if (!config.fletchRoots()) {
            return false;
        }

        // Check if we have roots to fletch
        if (!Rs2Inventory.hasItem(ItemID.BRUMA_ROOT)) {
            setLockState(State.FLETCH_LOGS, false);
            return false;
        }

        // Check if we have a knife
        if (!Rs2Inventory.hasItem(ItemID.KNIFE)) {
            System.out.println("Fletching enabled but no knife found in inventory");
            return false;
        }

        changeState(State.FLETCH_LOGS, true);
        return true;
    }

    /**
     * Handles hitsplat applied events to trigger action resets when player takes damage.
     *
     * @param hitsplatApplied The hitsplat event
     */
    public static void onHitsplatApplied(HitsplatApplied hitsplatApplied) {
        Actor actor = hitsplatApplied.getActor();

        // Only process if it's the local player taking damage
        if (actor != Microbot.getClient().getLocalPlayer()) {
            return;
        }

        System.out.println("Player took damage");
        
        
        resetActions = true;
    }

    /**
     * Main script execution method with comprehensive error handling and state management.
     *
     * @param config Configuration object
     * @param plugin Plugin instance
     * @return true if script started successfully
     */
    public boolean run(MKE_WintertodtConfig config, MKE_WintertodtPlugin plugin) {
        // COMPLETE STATE RESET - Start completely fresh
        resetAllScriptState();
        
        // Reset lastStateChange immediately when plugin starts
        lastStateChange = System.currentTimeMillis();
        Microbot.log("Plugin started - all state reset to fresh start");
        
        // Store references for this run
        MKE_WintertodtScript.config = config;
        MKE_WintertodtScript.plugin = plugin;
        
        // Initialize startup manager
        startupManager = new WintertodtStartupManager(config);
        
        // Initialize break manager
        breakManager = new WintertodtBreakManager(config);
        
        // Always configure antiban on every plugin start
        configureAntibanSettings();
        
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                // Pre-execution checks
                if (!Microbot.isLoggedIn()) {
                    isInitialized = false;
                    lastStateChange = System.currentTimeMillis(); // Reset on logout
                    Microbot.log("Player logged out - reset initialization flag");
                    return;
                }

                if (!super.run()) return;

                // Update break manager and handle breaks
                if (breakManager != null && breakManager.update()) {
                    setLockState(state, false); // Force unlock current state
                    changeState(State.WALKING_TO_SAFE_SPOT_FOR_BREAK);
                    return;
                }
                
                if (shouldPauseForBreaks()) {
                    wasOnBreak = true;
                    return; // Pause script if any break is active
                }

                if (wasOnBreak) {
                    lastStateChange = System.currentTimeMillis();
                    Microbot.log("Resuming from break, resetting stuck state timer.");
                    wasOnBreak = false;
                }

                // Performance monitoring
                long loopStartTime = System.currentTimeMillis();

                // Handle startup sequence first
                if (!startupCompleted) {
                    if (!executeStartupSequence()) {
                        return;
                    }
                }
                
                // Initialize script if needed
                if (!isInitialized) {
                    if (!initializeScript(config, plugin)) {
                        return;
                    }
                }

                // Gather current game state
                GameState gameState = analyzeGameState();

                // Emergency situation handling
                if (handleEmergencySituations(gameState)) {
                    return;
                }

                // Human-like behaviors
                performHumanLikeBehaviors();

                // Core maintenance tasks
                performMaintenanceTasks(gameState);

                // Main game logic based on current state
                executeMainGameLogic(gameState);

                // Performance tracking
                trackPerformance(loopStartTime);

            } catch (Exception ex) {
                handleScriptException(ex);
            }
        }, 0, 80, TimeUnit.MILLISECONDS); // Slightly slower for more human-like behavior

        return true;
    }

    /**
     * Completely resets all script state to ensure fresh start.
     */
    private void resetAllScriptState() {
        Microbot.log("=== COMPLETE SCRIPT STATE RESET ===");
        
        // Reset main state variables
        state = State.BANKING;
        resetActions = false;
        lockState = false;
        lastStateChange = System.currentTimeMillis();
        
        // Reset initialization flags
        isInitialized = false;
        startupCompleted = false;
        
        // Reset round timer tracking
        roundEndTime = -1;
        nextRoundStartTime = -1;
        mouseMovedOffscreenForRound = false;
        hoveredForNextRound = false;
        isHoverBeforeStartTimeCalculated = false;
        hoverBeforeStartTime = 0;
        lastKnownRemainingSeconds = -1;
        currentRoundStartTime = 0;
        previousRoundDurations.clear();
        previousRoundDurations.add(DEFAULT_ROUND_DURATION_MS);
        
        // Reset spam clicking variables
        spamClickingActive = false;
        spamClickStartTime = 0;
        spamClickEndTime = 0;
        spamClickTarget = null;
        spamClicksPerformed = 0;
        lastSpamClick = 0;
        shouldPriorizeBrazierAtStart = false;
        
        // Reset HP tracking
        prevWintertodtHp = -1;
        prevHpTimestamp = 0;
        hpPerSecond = 0;
        estimatedSecondsLeft = 999;
        historicalEstimateSecondsLeft = 0;
        
        // Reset action timing averages
        avgChopMs = 2800;
        avgFletchMs = 2200;
        avgFeedMs = 1600;
        cycleTimeSec = 0;
        maxCyclesPossible = 0;
        
        // Reset action plan variables
        targetRootsForThisRun = 0;
        rootsToChopGoal = 0;
        fletchGoal = 0;
        feedGoal = 0;
        rootsChoppedThisRun = 0;
        fletchedThisRun = 0;
        fedThisRun = 0;
        currentBurnableCount = 0;
        
        // Reset XP drop timers
        lastWoodcuttingXpDropTime = 0;
        lastFletchingXpDropTime = 0;
        lastFiremakingXpDropTime = 0;
        
        // Reset feeding animation tracking
        lastFeedingAnimationTime = 0;
        
        // Reset human behavior variables
        lastMouseMovement = 0;
        lastCameraMovement = 0;
        
        // Reset performance tracking
        lastPerformanceCheck = 0;
        actionsPerformed = 0;
        consecutiveFailures = 0;
        
        // Reset fletching state
        if (fletchingState.isActive()) {
            fletchingState.stopFletching(FletchingInterruptType.MANUAL_STOP);
        }
        fletchingState = new FletchingState();
        
        // Reset feeding state
        if (feedingState.isActive()) {
            feedingState.stopFeeding(FeedingInterruptType.MANUAL_STOP);
        }
        feedingState = new FeedingState();
        
        // Reset waiting for round end flag
        waitingForRoundEnd = false;
        
        // Reset game state
        currentBrazier = null;

        // Reset startup manager if it exists
        if (startupManager != null) {
            startupManager.reset();
        }
        
        // Reset startup completion flag
        startupCompleted = false;
        
        Microbot.log("All script state variables reset to default values");
    }

    /**
     * Configures antiban settings for Wintertodt specifically.
     * This method runs every time the plugin starts to ensure proper configuration.
     */
    private void configureAntibanSettings() {
        try {
            Microbot.log("Configuring antiban settings for Wintertodt...");
            
            // Reset and apply firemaking setup
            Rs2Antiban.resetAntibanSettings();
            Rs2Antiban.antibanSetupTemplates.applyFiremakingSetup();
            Rs2Antiban.setActivity(Activity.GENERAL_FIREMAKING);
            
            // Override some settings for Wintertodt-specific behavior
            Rs2AntibanSettings.takeMicroBreaks = false; // Disabled - using custom microbreak system
            Rs2AntibanSettings.microBreakChance = 0.0; // Disabled
            Rs2AntibanSettings.actionCooldownChance = 0.15; // 15% chance for action cooldown
            Rs2AntibanSettings.moveMouseRandomly = true;
            Rs2AntibanSettings.moveMouseRandomlyChance = 0.36;
            Rs2AntibanSettings.moveMouseOffScreen = true;
            Rs2AntibanSettings.moveMouseOffScreenChance = 0.34;
            Rs2Antiban.setActivityIntensity(ActivityIntensity.MODERATE);
            
            // Log antiban configuration
            Microbot.log("=== Antiban Configuration ===");
            Microbot.log("Activity: " + Rs2Antiban.getActivity().getMethod());
            Microbot.log("Play Style: " + Rs2Antiban.getPlayStyle().getName());
            Microbot.log("Micro Breaks: " + (Rs2AntibanSettings.takeMicroBreaks ? "Enabled" : "Disabled"));
            Microbot.log("Action Cooldown: " + (Rs2AntibanSettings.usePlayStyle ? "Enabled" : "Disabled"));
            Microbot.log("Mouse Randomization: " + (Rs2AntibanSettings.moveMouseRandomly ? "Enabled" : "Disabled"));
            Microbot.log("============================");
            
            Microbot.log("Antiban system configured for Wintertodt with firemaking profile");
            
        } catch (Exception e) {
            Microbot.log("Error configuring antiban settings: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Executes the smart startup sequence to prepare for Wintertodt.
     * This handles navigation from anywhere in the game, gear setup, and inventory preparation.
     *
     * @return true if startup completed successfully
     */
    private boolean executeStartupSequence() {
        try {
            Microbot.log("Executing smart startup sequence...");
            
            // Update Microbot status
            Microbot.status = startupManager.getCurrentPhase().getDescription();
            
            // Execute the startup sequence
            if (startupManager.executeStartupSequence()) {
                startupCompleted = true;
                Microbot.log("Smart startup sequence completed successfully!");
                return true;
            } else {
                Microbot.log("Smart startup sequence failed");
                return false;
            }
            
        } catch (Exception e) {
            Microbot.log("Error during startup sequence: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Initializes the script with proper validation and setup.
     *
     * @param config Configuration object
     * @param plugin Plugin instance
     * @return true if initialization successful
     */
    private boolean initializeScript(MKE_WintertodtConfig config, MKE_WintertodtPlugin plugin) {
        try {
            Microbot.log("Initializing Enhanced Wintertodt Script...");

            // Ensure lastStateChange is current (should already be set in run() method)
            lastStateChange = System.currentTimeMillis();
            Microbot.log("Script initialization - lastStateChange confirmed reset");

            // Initial state
            state = State.BANKING;

            // Reset break system state (handled by WintertodtBreakManager)
            
            // Ensure break handler is not locked initially
            if (BreakHandlerScript.isLockState()) {
                BreakHandlerScript.setLockState(false);
                Microbot.log("Reset break handler lock state during initialization");
            }

            // Validate equipment setup
            if (!validateEquipmentSetup()) {
                return false;
            }

            // Validate inventory setup
            if (!validateInventorySetup()) {
                return false;
            }

            /* -------- seed round-duration history with default -------- */
            previousRoundDurations.clear();
            previousRoundDurations.add(DEFAULT_ROUND_DURATION_MS);

            isInitialized = true;

            Microbot.log("Script initialized successfully!");
            
            // Fix camera settings on script start
            Microbot.log("Checking and adjusting camera settings...");
            fixCameraPitchIfNeeded();
            fixCameraZoomIfNeeded();

            /* -------- Figure-out the correct starting state -------- */
            GameState gs = analyzeGameState();     // one cheap scan
            determineInitialState(gs);
            Microbot.log("Initial state decided: " + state);

            resetActionPlanning();        // always start with a clean slate

            return true;

        } catch (Exception e) {
            Microbot.log("Failed to initialize script: " + e.getMessage());
            return false;
        }
    }

    /**
     * Validates the player's equipment setup with automatic axe detection.
     *
     * @return true if equipment is valid
     */
    private boolean validateEquipmentSetup() {
        // Get automatic axe decision
        WintertodtAxeManager.AxeDecision axeDecision = WintertodtAxeManager.determineOptimalAxeSetup();
        Microbot.log("Automatic axe validation: " + axeDecision.toString());
        
        if (axeDecision.shouldEquipAxe()) {
            if (!Rs2Equipment.isWearing(axeDecision.getAxeId())) {
                if (Rs2Inventory.hasItem(axeDecision.getAxeId())) {
                    Microbot.log("Equipping axe from inventory: " + axeDecision.getAxeName());
                    Rs2Inventory.wield(axeDecision.getAxeId());
                    sleepUntilTrue(() -> Rs2Equipment.isWearing(axeDecision.getAxeId()), 100, 5000);
                    return Rs2Equipment.isWearing(axeDecision.getAxeId());
                }
                Microbot.showMessage("No suitable axe found! The startup system should have handled this.");
                return false;
            }
            Microbot.log("Using equipped axe: " + axeDecision.getAxeName());
        } else {
            if (!Rs2Inventory.hasItem(axeDecision.getAxeId())) {
                Microbot.showMessage("Axe should be in inventory but not found! Startup system issue.");
                return false;
            }
            Microbot.log("Using axe from inventory: " + axeDecision.getAxeName());
        }

        return true;
    }

    /**
     * Validates the player's inventory setup.
     *
     * @return true if inventory is valid
     */
    private boolean validateInventorySetup() {
        // Check for required tools based on configuration
        if (config.fletchRoots() && !Rs2Inventory.hasItem(ItemID.KNIFE)) {
            Microbot.log("Fletching enabled but no knife in inventory - this is okay, will get one from bank");
        }

        if (config.fixBrazier() && !Rs2Inventory.hasItem(ItemID.HAMMER)) {
            Microbot.log("Brazier fixing enabled but no hammer in inventory - this is okay, will get one from bank");
        }

        // Check if we need tinderbox (only if not using bruma torch)
        if (!Rs2Equipment.isWearing(ItemID.BRUMA_TORCH) &&
                !Rs2Equipment.isWearing(ItemID.BRUMA_TORCH_OFFHAND) &&
                !Rs2Inventory.hasItem(ItemID.TINDERBOX)) {
            Microbot.log("No bruma torch equipped and no tinderbox in inventory - this is okay, will get one from bank");
        }

        return true;
    }

    /**
     * Analyzes the current game state and returns a comprehensive state object.
     */
    private GameState analyzeGameState() {
        GameState gameState = new GameState();

        try {
            /* ----- round-timer + HP based lifecycle detection ----- */
            int timerSeconds = getWintertodtRemainingTime();      //  -1 when timer widget not visible
            boolean timerVisible = timerSeconds > 0;              // visible  => round break

            // Read HP from the "Wintertodt's Energy" widget (396,26)
            int wtHp = -1;
            Widget energyWidget = Rs2Widget.getWidget(396, 26);
            if (energyWidget != null) {
                Matcher m = Pattern.compile("(\\d+)").matcher(energyWidget.getText());
                if (m.find()) {
                    wtHp = Integer.parseInt(m.group(1));
                }
            }

            // --- keep running HP/s statistics ----------------
            updateWintertodtHpTracking(wtHp);
            // -------------------------------------------------------

            boolean roundActive = !timerVisible && wtHp != 0;     // active only if timer gone AND HP not 0

            gameState.wintertodtRespawning = !roundActive;        // includes HP 0 or timer visible
            gameState.isWintertodtAlive   = roundActive;
            gameState.wintertodtHp        = wtHp;
            /* ----------------------------------------------------- */

            gameState.playerWarmth      = getWarmthLevel();
            gameState.playerIsLowWarmth = gameState.playerWarmth < config.warmthTreshhold();

            // Object detection
            gameState.brazier        = Rs2GameObject.findObject(BRAZIER_29312,
                                          config.brazierLocation().getOBJECT_BRAZIER_LOCATION());
            gameState.brokenBrazier  = Rs2GameObject.findObject(BRAZIER_29313,
                                          config.brazierLocation().getOBJECT_BRAZIER_LOCATION());
            gameState.burningBrazier = Rs2GameObject.findObject(BURNING_BRAZIER_29314,
                                          config.brazierLocation().getOBJECT_BRAZIER_LOCATION());

            // Health and food management - determine healing strategy
            usesPotions = config.rejuvenationPotions() && !config.useFoodManagement();

            QuestState druidicRitual = Rs2Player.getQuestState(Quest.DRUIDIC_RITUAL);
            if (druidicRitual != QuestState.FINISHED && usesPotions) {
                Microbot.log("Druidic Ritual not finished - defaulting to food (no potions)");
                usesPotions = false;
            }
            
            // Fallback to food if both are enabled (shouldn't happen with proper config)
            if (config.rejuvenationPotions() && config.useFoodManagement() && druidicRitual == QuestState.FINISHED && !usesPotions) {
                Microbot.log("Both potions and food management enabled - defaulting to potions");
                usesPotions = true;
            }
            
            // Fallback to food if neither is enabled
            if (!config.rejuvenationPotions() && !config.useFoodManagement()) {
                if (druidicRitual == QuestState.FINISHED && !usesPotions) {
                    Microbot.log("Druidic Ritual finished - defaulting to potions");
                    usesPotions = true;
                } else if (usesPotions) {
                    Microbot.log("Neither potions nor food management enabled and Druidic Ritual not finished - defaulting to food");
                    usesPotions = false;
                }
            }
            
            String foodType = usesPotions ? "Rejuvenation potion " : config.food().getName();
            int foodCount   = Rs2Inventory.count(foodType);     // current food in inventory
            boolean inBossRoom =
                    Rs2Player.getWorldLocation().distanceTo(BOSS_ROOM) < 20;

            boolean lowAndOutOfFood =
                    (foodCount == 0) &&
                    (gameState.playerWarmth <= config.eatAtWarmthLevel());

            /* ─────────── BANKING RULES FOR REJUVENATION POTIONS ─────────── */
            if (usesPotions) {
                // When using rejuvenation potions, we make them instead of banking
                if (gameState.wintertodtRespawning) {
                    // During round break, make potions if we need them
                    gameState.needBanking = false; // Never bank with rejuv potions
                    gameState.needPotions = foodCount < config.minFood();
                } else if (inBossRoom) {
                    // During active round, only make potions if we're out AND low warmth
                    gameState.needBanking = false;
                    gameState.needPotions = lowAndOutOfFood;
                } else {
                    // In lobby area with rejuv potions
                    gameState.needBanking = false;
                    gameState.needPotions = foodCount < config.minFood();
                }
            } else {
                // Original banking logic for regular food
                if (gameState.wintertodtRespawning) {
                    gameState.needBanking = foodCount < config.minFood();
                } else if (inBossRoom) {
                    gameState.needBanking = lowAndOutOfFood;
                } else {
                    gameState.needBanking = foodCount < config.minFood();
                }
                gameState.needPotions = false;
            }

            // Inventory state
            gameState.inventoryFull = Rs2Inventory.isFull();
            gameState.hasItemsToBurn = Rs2Inventory.hasItem(ItemID.BRUMA_KINDLING) || Rs2Inventory.hasItem(ItemID.BRUMA_ROOT);
            gameState.hasRootsToFletch = Rs2Inventory.hasItem(ItemID.BRUMA_ROOT);

            // For overlay action plan
            currentBurnableCount = Rs2Inventory.count(ItemID.BRUMA_ROOT) + Rs2Inventory.count(ItemID.BRUMA_KINDLING);

        } catch (Exception e) {
            System.err.println("Error analyzing game state: " + e.getMessage());
        }

        return gameState;
    }

    /**
     * Handles emergency situations that require immediate attention.
     *
     * @param gameState Current game state
     * @return true if emergency was handled, false otherwise
     */
    private boolean handleEmergencySituations(GameState gameState) {
        // Don't handle emergencies during breaks unless critical
        if (BreakHandlerScript.isBreakActive() || Rs2AntibanSettings.microBreakActive) {
            // Only handle critical dialogs during breaks
            if (Rs2Widget.hasWidget("Leave and lose all progress")) {
                Microbot.log("Critical dialog detected during break, handling...");
                Rs2Keyboard.typeString("1");
                sleepGaussian(1600, 400);
                return true;
            }
            return false;
        }

        // Handle "Leave and lose all progress" dialog
        if (Rs2Widget.hasWidget("Leave and lose all progress")) {
            Microbot.log("Detected leave dialog, selecting option 1");
            Rs2Keyboard.typeString("1");
            sleepGaussian(1600, 400);
            return true;
        }

        // Handle very low warmth emergency (but respect break state)
        if (gameState.playerWarmth < 20 && !Rs2Inventory.hasItem(config.food().getName())) {
            Microbot.log("EMERGENCY: Very low warmth without food!");
            // Unlock break handler for emergency banking
            if (BreakHandlerScript.isLockState()) {
                BreakHandlerScript.setLockState(false);
                Microbot.log("Emergency unlock of break handler for critical banking");
            }
            changeState(State.BANKING);
            return true;
        }

        // Handle stuck state (no state change for too long) - but don't interfere with breaks
        if (System.currentTimeMillis() - lastStateChange > 120000 && !BreakHandlerScript.isLockState()) {
            Microbot.log("Possible stuck state detected after 2 minutes, resetting...");
            resetActions = true;
            setLockState(state, false);
            lastStateChange = System.currentTimeMillis(); // Reset the timer
            return true;
        }

        return false;
    }

    /**
     * Performs human-like behaviors to avoid detection.
     */
    private void performHumanLikeBehaviors() {
        if (!config.humanizedTiming()) return;

        long currentTime = System.currentTimeMillis();

        // Random mouse movements
        if (config.randomMouseMovements() && currentTime - lastMouseMovement > 15000 + random.nextInt(10000)) {
            if (random.nextInt(100) < 15) { // 15% chance
                // Simulate checking other areas occasionally
                if (currentBrazier != null && random.nextBoolean()) {
                    Rs2GameObject.hoverOverObject(currentBrazier);
                }
                lastMouseMovement = currentTime;
            }
        }

        // Random camera movements
        if (config.randomMouseMovements() && shouldPerformCameraMovement(currentTime)) {
            performHumanLikeCameraMovement();
        }

        // Variable delay based on recent actions
        if (resetActions && config.humanizedTiming()) {
            int baseDelay = 150;
            int variation = 50 + random.nextInt(100);
            sleepGaussian(baseDelay, variation);
        }
    }

    /**
     * Determines if we should perform a camera movement based on timing and bot state.
     * Only moves camera when bot is idle to avoid interrupting actions.
     */
    private boolean shouldPerformCameraMovement(long currentTime) {
        // Check if enough time has passed since last camera movement
        long timeSinceLastMove = currentTime - lastCameraMovement;
        if (timeSinceLastMove < CAMERA_MOVEMENT_MIN_DELAY) {
            return false;
        }

        // Random chance that increases over time
        double baseChance = 0.001; // 1% base chance per call
        double timeMultiplier = Math.min(3.0, timeSinceLastMove / (double)CAMERA_MOVEMENT_MIN_DELAY);
        double finalChance = baseChance * timeMultiplier;

        if (random.nextDouble() > finalChance) {
            return false;
        }

        // Only move camera when bot is in idle states and not actively doing anything
        boolean isIdleState = (state == State.WAITING || state == State.BANKING);
        boolean isNotAnimating = !Rs2Player.isAnimating();
        boolean isNotMoving = !Rs2Player.isMoving();
        boolean isNotInteracting = !Rs2Player.isInteracting();

        return isIdleState && isNotAnimating && isNotMoving && isNotInteracting;
    }

    /**
     * Performs a natural, human-like camera movement by briefly holding the arrow
     * keys rather than teleporting the camera to a new angle.  Yaw and pitch
     * deltas follow Gaussian distributions so that small adjustments are common
     * while large "look around" sweeps are rare.
     */
    private void performHumanLikeCameraMovement()
    {
        try
        {
            /* ---------- decide yaw delta -------------------------------- */
            //  μ = 0°,  σ = 15°  →  68 % of moves within ±15°, rarely >±45°
            int yawDelta = (int) Math.round(random.nextGaussian() * 15);
            yawDelta = Math.max(-60, Math.min(60, yawDelta));      // clamp

            // 25 % chance to flip direction to make "about face" rarer
            if (yawDelta == 0 || (Math.abs(yawDelta) < 10 && random.nextInt(4) == 0))
            {
                yawDelta = (random.nextBoolean() ? 1 : -1) * (10 + random.nextInt(10));
            }

            int targetYaw = (Rs2Camera.getAngle() + yawDelta + 360) % 360;

            /* ---------- decide if we also adjust pitch ------------------ */
            boolean changePitch = random.nextInt(100) < 35;   // 35 % of the time
            float   targetPitchPct = Rs2Camera.cameraPitchPercentage();

            if (changePitch)
            {
                // μ = 0, σ = 8 % of full range, clamped to 35 – 85 %
                float deltaPct = (float) (random.nextGaussian() * 0.08);
                targetPitchPct = Math.max(0.35f, Math.min(0.85f, targetPitchPct + deltaPct));
            }

            /* ---------- make the movement with key taps ----------------- */
            // Hold key for a duration proportional to needed rotation
            int degreesToMove = Math.abs(Rs2Camera.getAngleTo(targetYaw));
            int pressTime = 80 + (int) (degreesToMove * 2.2);       // ms
            pressTime = (int) (pressTime * (0.8 + random.nextGaussian() * 0.1));
            pressTime = Math.max(60, pressTime);                    // safety

            if (Rs2Camera.getAngleTo(targetYaw) > 0)
            {
                Rs2Keyboard.keyHold(java.awt.event.KeyEvent.VK_LEFT);
                sleepGaussian(pressTime, (int) (pressTime * 0.15));
                Rs2Keyboard.keyRelease(java.awt.event.KeyEvent.VK_LEFT);
            }
            else
            {
                Rs2Keyboard.keyHold(java.awt.event.KeyEvent.VK_RIGHT);
                sleepGaussian(pressTime, (int) (pressTime * 0.15));
                Rs2Keyboard.keyRelease(java.awt.event.KeyEvent.VK_RIGHT);
            }

            /* small random pause between yaw and pitch */
            if (changePitch) sleepGaussian(120, 40);

            if (changePitch)
            {
                float now = Rs2Camera.cameraPitchPercentage();
                boolean pitchUp = now < targetPitchPct;
                int    pitchKey = pitchUp ? java.awt.event.KeyEvent.VK_UP
                                          : java.awt.event.KeyEvent.VK_DOWN;

                // Duration scaled to percentage difference
                int pctDiff    = (int) (Math.abs(targetPitchPct - now) * 100);
                int pitchTime  = 50 + (int) (pctDiff * 8);
                pitchTime = (int) (pitchTime * (0.85 + random.nextGaussian() * 0.12));
                pitchTime = Math.max(40, pitchTime);

                Rs2Keyboard.keyHold(pitchKey);
                sleepGaussian(pitchTime, (int) (pitchTime * 0.20));
                Rs2Keyboard.keyRelease(pitchKey);
            }

            // Update timestamp
            lastCameraMovement = System.currentTimeMillis();
            
            // Check and fix camera settings after movement
            sleepGaussian(200, 100); // Small delay before checking
            fixCameraPitchIfNeeded();
            fixCameraZoomIfNeeded();
        }
        catch (Exception ignored) {}
    }

    /**
     * Checks if camera pitch is too low and adjusts it if needed using keyboard simulation.
     */
    private void fixCameraPitchIfNeeded() {
        try {
            int currentPitch = Rs2Camera.getPitch();

            // If pitch is too low (camera looking too far down), adjust it to a medium-high angle
            if (currentPitch < 230) { // Adjust this threshold as needed
                Microbot.log("Camera pitch too low (" + currentPitch + "), adjusting...");

                // Adjust to a pitch between 60% and 90%
                float targetPitchPercent = 0.6f + random.nextFloat() * 0.3f;
                Rs2Camera.adjustPitch(targetPitchPercent);

                int newPitch = Rs2Camera.getPitch();
                Microbot.log("Camera pitch adjusted from " + currentPitch + " to " + newPitch);
            }

        } catch (Exception e) {
            System.err.println("Error fixing camera pitch: " + e.getMessage());
        }
    }

    /**
     * Checks if camera zoom is too close/far and adjusts it if needed.
     */
    private void fixCameraZoomIfNeeded() {
        try {
            int currentZoom = Rs2Camera.getZoom();
            
            // If zoom is too close (< 250) or too far (> 350), adjust to medium range
            if (currentZoom < 250 || currentZoom > 350) {
                
                // Set zoom to a value between 250-350 for good visibility
                int targetZoom = 250 + random.nextInt(100);
                Rs2Camera.setZoom(targetZoom);
                
                // Small delay after zoom adjustment
                sleepGaussian(300, 100);
            }

        } catch (Exception e) {
            System.err.println("Error fixing camera zoom: " + e.getMessage());
        }
    }

    /**
     * Performs regular maintenance tasks like dropping items and damage avoidance.
     *
     * @param gameState Current game state
     */
    private void performMaintenanceTasks(GameState gameState) {
        if (state == State.WALKING_TO_SAFE_SPOT_FOR_BREAK) {
            return;
        }
        // Skip maintenance during breaks except for critical tasks
        if (BreakHandlerScript.isBreakActive() || Rs2AntibanSettings.microBreakActive) {
            // Only handle critical eating during breaks
            if (gameState.playerWarmth <= 20) {
                handleEating(gameState);
            }
            return;
        }

        // Drop unnecessary items
        dropUnnecessaryItems();

        // Dodge falling snow/damage
        dodgeSnowfallDamage(gameState);

        // Handle eating
        handleEating(gameState);

        // Periodic camera check (every 2 minutes during normal operation)
        if (System.currentTimeMillis() - lastCameraMovement > 120000) {
            fixCameraPitchIfNeeded();
            fixCameraZoomIfNeeded();
            lastCameraMovement = System.currentTimeMillis(); // Reset timer to avoid frequent checks
        }

        // Banking check (respect break handler state) - BUT NOT for rejuvenation potions
        if (gameState.needBanking && !usesPotions) {
            if (BreakHandlerScript.isLockState()) {
                BreakHandlerScript.setLockState(false);
                Microbot.log("Unlocking break handler for emergency banking");
            }
            setLockState(State.BANKING, false);
            changeState(State.BANKING);
        }
        
        // NEW: Handle potion needs for rejuvenation potions
        if (gameState.needPotions && usesPotions) {
            if (BreakHandlerScript.isLockState()) {
                BreakHandlerScript.setLockState(false);
                Microbot.log("Unlocking break handler for potion creation");
            }
            handlePotionCreation(gameState);
        }
    }

    /**
     * Executes the main game logic based on current state.
     *
     * @param gameState Current game state
     */
    private void executeMainGameLogic(GameState gameState) {
        // Skip main game logic during breaks (except banking/potions)
        if ((BreakHandlerScript.isBreakActive() || Rs2AntibanSettings.microBreakActive) && 
            state != State.BANKING && state != State.GET_CONCOCTIONS && 
            state != State.GET_HERBS && state != State.MAKE_POTIONS) {
            Microbot.log("Skipping main game logic due to active break");
            return;
        }

        // Update round timer tracking
        updateRoundTimer();

        // Update game state
        gameState = analyzeGameState();

        // Handle potion creation if needed - but don't return early
        if (gameState.needPotions && usesPotions && 
            state != State.GET_CONCOCTIONS && state != State.GET_HERBS && state != State.MAKE_POTIONS) {
            handlePotionCreation(gameState);
            // Don't return here - let the state execution happen below
        }

        // Determine if we should be doing main loop activities (only if not in potion states)
        if (!gameState.needBanking && state != State.GET_CONCOCTIONS && state != State.GET_HERBS && state != State.MAKE_POTIONS && state != State.WALKING_TO_SAFE_SPOT_FOR_BREAK) {
            if (!gameState.isWintertodtAlive) {
                handleWintertodtDown(gameState);
            } else {
                handleMainGameLoop(gameState);
            }
        }

        // Execute state-specific logic - THIS IS CRITICAL
        executeStateLogic(gameState);
    }

    /**
     * Handles the rejuvenation potion creation workflow
     */
    private void handlePotionCreation(GameState gameState) {
        // Check if we're already in a potion-making state
        if (state == State.GET_CONCOCTIONS || state == State.GET_HERBS || state == State.MAKE_POTIONS) {
            // Already in a potion state, let the state handlers do their work
            return;
        }

        // Determine what we need and start the appropriate state
        int concoctionCount = Rs2Inventory.count(ItemID.REJUVENATION_POTION_UNF);
        int herbCount = Rs2Inventory.count(ItemID.BRUMA_HERB);
        int currentPotions = Rs2Inventory.count("Rejuvenation potion ");
        int potionsNeeded = config.foodAmount() - currentPotions;

        Microbot.log("=== POTION CREATION ANALYSIS ===");
        Microbot.log("Potions needed: " + potionsNeeded);
        Microbot.log("Current potions: " + currentPotions + "/" + config.foodAmount());
        Microbot.log("Concoctions: " + concoctionCount + ", Herbs: " + herbCount);
        Microbot.log("Current state: " + state);

        // If we have enough potions, don't do anything
        if (potionsNeeded <= 0) {
            Microbot.log("Have enough potions, no creation needed");
            return;
        }

        // If we have both ingredients, make potions
        if (concoctionCount > 0 && herbCount > 0) {
            Microbot.log("Have both ingredients, transitioning to MAKE_POTIONS");
            changeState(State.MAKE_POTIONS);
        }
        // If we need concoctions, get them first
        else if (concoctionCount < potionsNeeded) {
            Microbot.log("Need more concoctions, transitioning to GET_CONCOCTIONS");
            changeState(State.GET_CONCOCTIONS);
        }
        // If we need herbs, get them
        else if (herbCount < potionsNeeded) {
            Microbot.log("Need more herbs, transitioning to GET_HERBS");
            changeState(State.GET_HERBS);
        }
    }

    /**
     * Handles the situation when Wintertodt is down/respawning.
     *
     * @param gameState Current game state
     */
    private void handleWintertodtDown(GameState gameState) {
        // Don't interrupt potion creation when Wintertodt is down
        if (state == State.GET_CONCOCTIONS || state == State.GET_HERBS || state == State.MAKE_POTIONS) {
            return; // Continue with potion creation
        }
        
        if (state != State.ENTER_ROOM && state != State.WAITING && state != State.BANKING) {
            setLockState(State.GLOBAL, false);
            changeState(State.WAITING);
        }
    }

    /**
     * Handles the main game loop when Wintertodt is alive.
     *
     * @param gameState Current game state
     */
    private void handleMainGameLoop(GameState gameState)
    {
        /* If we're waiting for round to end, don't do any activities */
        if (waitingForRoundEnd) {
            return; // Just idle until round ends naturally
        }

        /* Prioritize brazier lighting immediately when round starts */
        if (shouldPriorizeBrazierAtStart) {
            Microbot.log("Round start priority active - checking if brazier needs lighting");
            if (shouldLightBrazier(gameState)) {
                Microbot.log("Prioritizing brazier lighting at round start (state: " + state + ")");
                return; // Light the brazier first, then resume normal flow next tick
            } else {
                // Brazier is already lit or doesn't need lighting, reset the flag
                shouldPriorizeBrazierAtStart = false;
                Microbot.log("Brazier lighting priority completed or not needed (state: " + state + ")");
            }
        }

        /*  If time is almost over just finish up     */
        if (estimatedSecondsLeft < 8)
        {
            // try a last-second fix if brazier broke
            if (gameState.brokenBrazier != null && config.fixBrazier())
            {
                changeState(State.BURN_LOGS);             // BURN_LOGS handler contains "fix" logic
                return;
            }

            if (gameState.hasItemsToBurn)
                shouldBurnLogs(gameState);

            return;                                       // otherwise idle / hover
        }

        // Normal flow (smart chop / fletch / burn)
        if (shouldStartOrContinueChopping(gameState)) return;
        if (shouldFletchRoots())        return;
        if (shouldBurnLogs(gameState))  return;

        // If we get here and have no items, reset the visual plan on the overlay
        if (!gameState.hasItemsToBurn) {
            targetRootsForThisRun = 0;
        }
    }

    /**
     * Executes logic specific to the current state.
     *
     * @param gameState Current game state
     */
    private void executeStateLogic(GameState gameState) {
        switch (state) {
            case BANKING:
                handleBankingState(gameState);
                break;
            case ENTER_ROOM:
                handleEnterRoomState(gameState);
                break;
            case WAITING:
                handleWaitingState(gameState);
                break;
            case LIGHT_BRAZIER:
                handleLightBrazierState(gameState);
                break;
            case CHOP_ROOTS:
                handleChopRootsState(gameState);
                break;
            case FLETCH_LOGS:
                handleFletchLogsState(gameState);
                break;
            case BURN_LOGS:
                handleBurnLogsState(gameState);
                break;
            case GET_CONCOCTIONS:
                handleGetConcoctionsState(gameState);
                break;
            case GET_HERBS:
                handleGetHerbsState(gameState);
                break;
            case MAKE_POTIONS:
                handleMakePotionsState(gameState);
                break;
            case WALKING_TO_SAFE_SPOT_FOR_BREAK:
                handleWalkingToSafeSpotForBreakState(gameState);
                break;
        }
    }

    /**
     * Handles the banking state logic.
     */
    private void handleBankingState(GameState gameState) {
        try {
            /* leave the arena BEFORE doing anything else */
            if (!attemptLeaveWintertodt()) {
                return;                           // still inside → try again next tick
            }

            // Skip banking entirely if using rejuvenation potions
            if (usesPotions) {
                Microbot.log("Using rejuvenation potions - redirecting to potion creation instead of banking");
                changeState(State.GET_CONCOCTIONS);
                return;
            }

            if (!executeBankingLogic()) return;

            // Check if ready to continue
            String foodType = usesPotions ? "Rejuvenation potion " : config.food().getName();
            if (Rs2Player.isFullHealth() && Rs2Inventory.hasItemAmount(foodType, config.foodAmount(), false, true)) {
                plugin.setTimesBanked(plugin.getTimesBanked() + 1);

                // Handle break system properly
                if (shouldTriggerBreak()) {
                    handleBreakTrigger();
                    return;
                }
                
                // Handle force break trigger from config
                if (config.forceBreakNow() && breakManager != null) {
                    // Reset the config toggle
                    Microbot.getConfigManager().setConfiguration("wintertodt", "ForceBreakNow", false);
                    // Force a break (30% chance for logout, 70% for AFK)
                    WintertodtBreakManager.BreakType breakType = random.nextInt(100) < 30 
                        ? WintertodtBreakManager.BreakType.LOGOUT_LONG 
                        : WintertodtBreakManager.BreakType.AFK_SHORT;
                    breakManager.forceBreak(breakType);
                    Microbot.log("Force break triggered from config - type: " + breakType.getName());
                    return;
                }

                // Unlock break handler state after successful banking
                if (BreakHandlerScript.isLockState()) {
                    BreakHandlerScript.setLockState(false);
                    Microbot.log("Unlocking break handler state after banking");
                }

                changeState(State.ENTER_ROOM);
            }

        } catch (Exception e) {
            System.err.println("Error in banking state: " + e.getMessage());
            consecutiveFailures++;
        }
    }

    /**
     * Handles the enter room state logic.
     */
    private void handleEnterRoomState(GameState gameState) {
        try {
            // Navigate to boss room
            if (!gameState.wintertodtRespawning && !gameState.isWintertodtAlive) {
                if (Rs2Player.getWorldLocation().distanceTo(BOSS_ROOM) > 10) {
                    Rs2Walker.walkTo(BOSS_ROOM, 8);
                    Rs2Player.waitForWalking();
                }
            } else {
                // Set break handler lock when entering active game state
                if (!BreakHandlerScript.isLockState() && !BreakHandlerScript.isBreakActive()) {
                    BreakHandlerScript.setLockState(true);
                    Microbot.log("Locking break handler state for active gameplay");
                    lastStateChange = System.currentTimeMillis();
                }
                changeState(State.WAITING);
            }

        } catch (Exception e) {
            System.err.println("Error in enter room state: " + e.getMessage());
        }
    }

    /**
     * Handles the waiting state logic.
     */
    private void handleWaitingState(GameState gameState) {
        try {
            // Execute spam clicking if in the appropriate time window (this runs during countdown)
            executeSpamClicking(gameState);

            // Move to brazier area
            navigateToBrazier();

            // Check if we should light brazier
            shouldLightBrazier(gameState);

            // Handle round timer-based mouse behavior
            handleRoundTimerMouseBehavior(gameState);

        } catch (Exception e) {
            System.err.println("Error in waiting state: " + e.getMessage());
        }
    }

    /**
     * Handles the light brazier state logic.
     */
    private void handleLightBrazierState(GameState gameState) {
        try {
            // Abort lighting if the round has ended while we were in this state
            if (!gameState.isWintertodtAlive || gameState.wintertodtHp == 0) {
                setLockState(State.LIGHT_BRAZIER, false);
                shouldPriorizeBrazierAtStart = false; // Reset priority flag
                changeState(State.WAITING);
                return;
            }

            if (gameState.brazier != null) {
                if (Rs2GameObject.interact(gameState.brazier, "light")) {
                    Microbot.log("Lighting brazier");
                    
                    // Reset priority flag after successful lighting attempt
                    if (shouldPriorizeBrazierAtStart) {
                        shouldPriorizeBrazierAtStart = false;
                        Microbot.log("Brazier lighting priority completed - resuming normal flow");
                    }
                    
                    Rs2Player.waitForXpDrop(Skill.FIREMAKING, 3000);
                    actionsPerformed++;
                    
                    // CRITICAL FIX: Transition to appropriate next state after lighting
                    setLockState(State.LIGHT_BRAZIER, false);
                    
                    // Determine next state based on current situation
                    if (gameState.hasItemsToBurn) {
                        changeState(State.BURN_LOGS);
                        Microbot.log("Transitioning to BURN_LOGS after lighting brazier");
                    } else if (!gameState.inventoryFull) {
                        changeState(State.CHOP_ROOTS);
                        Microbot.log("Transitioning to CHOP_ROOTS after lighting brazier");
                    } else {
                        changeState(State.WAITING);
                        Microbot.log("Transitioning to WAITING after lighting brazier");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error in light brazier state: " + e.getMessage());
        }
    }

    /**
     * Handles the chop roots state logic.
     */
    private void handleChopRootsState(GameState gameState)
    {
        try
        {
            // stop when plan met (root-count already added in onStatChanged)
            if (targetRootsForThisRun > 0
                && (Rs2Inventory.count(ItemID.BRUMA_ROOT)
                    + Rs2Inventory.count(ItemID.BRUMA_KINDLING)) >= targetRootsForThisRun)
            {
                setLockState(State.CHOP_ROOTS, false);
                changeState(State.WAITING);
                targetRootsForThisRun = 0;
                rootsChoppedThisRun = 0;
                return;
            }

            /* stop chopping if inventory became full */
            if (gameState.inventoryFull) {
                setLockState(State.CHOP_ROOTS, false);
                changeState(State.WAITING);      // next loop decides what to do (usually burn)
                return;
            }

            /* safety: if we somehow got pushed away, move back */
            if (Rs2Player.getWorldLocation()
                         .distanceTo(config.brazierLocation().getBRAZIER_LOCATION()) > CHOPPING_RADIUS)
            {
                navigateToBrazier();        // walk back, try chopping next loop
                return;
            }

            if (!Rs2Player.isAnimating())
            {
                GameObject root = getOwnSideRoot();
                if (root != null && Rs2GameObject.interact(root, "Chop"))
                {
                    sleepUntilTrue(Rs2Player::isAnimating, 100, 3000);
                    maybeNudgeMouse();
                    resetActions = false;
                    actionsPerformed++;

                    if (Rs2AntibanSettings.usePlayStyle)
                        Rs2Antiban.actionCooldown();
                }
            }
        } catch (Exception e) {
            System.err.println("Error in chop roots state: " + e.getMessage());
        }
    }

    /**
     * Handles the fletch logs state logic.
     * Includes brazier maintenance (fixing and relighting) as priority actions
     * before continuing with fletching.
     */
    private void handleFletchLogsState(GameState gameState) {
        try {
            // Update fletching animation tracking
            updateFletchingAnimationTracking();
            
            /* ---------- PRIORITY BLOCK 1: FIX BROKEN BRAZIER FIRST ----------- */
            if (gameState.brokenBrazier != null && config.fixBrazier()) {
                // Stop fletching temporarily to fix brazier
                if (fletchingState.isActive()) {
                    fletchingState.stopFletching(FletchingInterruptType.BRAZIER_BROKEN);
                }
                
                // Deselect any items before fixing
                deselectSelectedItem();
                
                Rs2GameObject.interact(gameState.brokenBrazier, "fix");
                Microbot.log("Fixing broken brazier (priority during fletching)");
                resetActions = true;
                actionsPerformed++;
                return; // Wait for next tick after the fix attempt
            }
            /* ----------------------------------------------------------------- */

            /* ---------- PRIORITY BLOCK 2: RELIGHT BRAZIER SECOND ------------ */
            if (gameState.burningBrazier == null && gameState.brazier != null && 
                config.relightBrazier() && gameState.isWintertodtAlive) {
                
                // Stop fletching temporarily to relight brazier
                if (fletchingState.isActive()) {
                    fletchingState.stopFletching(FletchingInterruptType.BRAZIER_WENT_OUT);
                }
                
                // Deselect any items before relighting
                deselectSelectedItem();
                
                Rs2GameObject.interact(gameState.brazier, "light");
                Microbot.log("Relighting brazier (priority during fletching)");
                resetActions = true;
                actionsPerformed++;
                return; // Wait for next tick after the relight attempt
            }
            /* ----------------------------------------------------------------- */
            
            /* ------- Safety: nothing but the knife may be selected ----------- */
            if (isItemSelected() && !isKnifeSelected()) {
                deselectSelectedItem();
            }

            int rootCount = Rs2Inventory.count(ItemID.BRUMA_ROOT);

            /* --- recalculate threshold only when starting a new full inventory cycle --- */
            if (rootCount > lastInventoryCount) {
                // Root count increased = we just chopped new roots = start of new cycle
                knifePreselectThreshold = 1 + Rs2Random.randomGaussian(3, 10);  // 1-10 roots
                Microbot.log("New inventory cycle detected, knife threshold: " + knifePreselectThreshold);
            }
            lastInventoryCount = rootCount;

            /* END fletching as soon as no roots remain ---------------- */
            if (rootCount == 0) {
                if (fletchingState.isActive()) {
                    fletchingState.stopFletching(FletchingInterruptType.OUT_OF_ROOTS);
                }
                
                deselectSelectedItem();
                setLockState(State.FLETCH_LOGS, false);
                changeState(State.WAITING);
                return;
            }

            /* ---------- smart pre-selection when many roots ---------- */
            if (rootCount > knifePreselectThreshold) {
                /*  we postpone knife-selection until we have reached the brazier */
            } else {
                // Few roots left – ensure knife is un-selected for next tasks
                deselectSelectedItem();
            }

            /* ---------- start / continue fletching ------------------- */
            if (!isCurrentlyFletching() && gameState.burningBrazier != null) {
                sleepGaussian(250, 150);
                if (random.nextInt(100) < 10) {
                    sleepGaussian(400, 600);
                }
                navigateToBrazier();

                // Keep knife in slot-27 optimisation
                Rs2ItemModel knife = Rs2Inventory.get("knife");
                if (knife != null && knife.getSlot() != 27) {
                    sleepGaussian(GAME_TICK_LENGTH * 2, 200);
                    if (Rs2Inventory.moveItemToSlot(knife, 27)) {
                        sleepUntilTrue(() -> Rs2Inventory.slotContains(27, "knife"), 100, 5000);
                    }
                }

                // Perform the click and start tracking fletching
                boolean fletchingStarted = false;
                
                if (isKnifeSelected()) {
                    boolean rootStillThere = lastHoveredRoot != null
                            && Rs2Inventory.contains(r -> r.getSlot() == lastHoveredRoot.getSlot());

                    Rs2ItemModel targetRoot = rootStillThere
                            ? lastHoveredRoot
                            : Rs2Inventory.getRandom(ItemID.BRUMA_ROOT);

                    if (targetRoot != null) {
                        Rs2Inventory.interact(targetRoot);
                        fletchingStarted = true;
                    }

                    lastHoveredRoot = null;
                } else {
                    if (Rs2Inventory.combineClosest(ItemID.KNIFE, ItemID.BRUMA_ROOT)) {
                        fletchingStarted = true;
                    }
                }
                
                if (fletchingStarted) {
                    fletchingState.startFletching();
                    // Initialize animation tracking for new fletching session
                    lastFletchingAnimationTime = System.currentTimeMillis();
                    maybeNudgeMouse();
                    resetActions = false;
                    actionsPerformed++;

                    if (Rs2AntibanSettings.usePlayStyle) {
                        Rs2Antiban.actionCooldown();
                    }
                }
            }

            /* Pre-select knife and hover when we have many roots */
            if (rootCount > knifePreselectThreshold && !isKnifeSelected() && gameState.burningBrazier != null) {
                Rs2Inventory.interact(ItemID.KNIFE, "Use");
                sleepGaussian(120, 40);

                lastHoveredRoot = null;
                for (int slot = 27; slot >= 0; slot--) {
                    Rs2ItemModel cand = Rs2Inventory.getItemInSlot(slot);
                    if (cand != null && cand.getId() == ItemID.BRUMA_ROOT) {
                        lastHoveredRoot = cand;
                        Rs2Inventory.hover(cand);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error in fletch logs state: " + e.getMessage());
        }
    }

    /**
     * Handles the burn logs state logic.
     * Always fixes or relights the brazier first, then feeds.
     * Includes comprehensive feeding state tracking and interruption detection.
     */
    private void handleBurnLogsState(GameState gameState) {
        try {
            // Update feeding animation tracking
            updateFeedingAnimationTracking();
            
            /* ---------- PRIORITY BLOCK 1: FIX BROKEN BRAZIER FIRST ----------- */
            if (gameState.brokenBrazier != null && config.fixBrazier()) {
                // Stop feeding temporarily to fix brazier
                if (feedingState.isActive()) {
                    feedingState.stopFeeding(FeedingInterruptType.BRAZIER_BROKEN);
                }
                
                Rs2GameObject.interact(gameState.brokenBrazier, "fix");
                Microbot.log("Fixing broken brazier");
                resetActions = true;
                actionsPerformed++;
                return; // Wait for next tick after the fix attempt
            }
            /* ----------------------------------------------------------------- */

            /* ---------- PRIORITY BLOCK 2: RELIGHT BRAZIER SECOND ------------ */
            TileObject burningBrazier = gameState.burningBrazier;  // side-specific
            if (burningBrazier == null && gameState.brazier != null && 
                config.relightBrazier() && gameState.isWintertodtAlive) {

                sleepGaussian(200, 150);
                
                // Stop feeding temporarily to relight brazier
                if (feedingState.isActive()) {
                    feedingState.stopFeeding(FeedingInterruptType.BRAZIER_WENT_OUT);
                }
                
                Rs2GameObject.interact(gameState.brazier, "light");
                Microbot.log("Relighting brazier");
                resetActions = true;
                actionsPerformed++;
                return; // Wait for next tick after the relight attempt
            }
            /* ----------------------------------------------------------------- */

            int currentItems = Rs2Inventory.count(ItemID.BRUMA_ROOT) + Rs2Inventory.count(ItemID.BRUMA_KINDLING);

            /* END feeding as soon as no items remain ---------------- */
            if (currentItems == 0) {
                if (feedingState.isActive()) {
                    feedingState.stopFeeding(FeedingInterruptType.OUT_OF_ITEMS);
                }
                return;
            }

            /* ---------- start / continue feeding ------------------- */
            if (!isCurrentlyFeeding() && 
                gameState.hasItemsToBurn) {

                sleepGaussian(200, 150);
                if (random.nextInt(100) < 10) {
                    sleepGaussian(400, 600);
                }
                
                if (Rs2GameObject.interact(burningBrazier, "feed")) {
                    feedingState.startFeeding();
                    // Initialize animation tracking for new feeding session
                    lastFeedingAnimationTime = System.currentTimeMillis();
                    resetActions = false;
                    actionsPerformed++;
                    Microbot.log("Started feeding brazier");
                    maybeNudgeMouse();
                }
            }

            /* reset the whole plan after last feed of the run */
            if (feedGoal > 0 && fedThisRun >= feedGoal) {
                targetRootsForThisRun = 0;
                rootsToChopGoal       = 0;
                fletchGoal            = 0;
                feedGoal              = 0;
                rootsChoppedThisRun   = 0;
                fletchedThisRun       = 0;
                fedThisRun            = 0;
            }

        } catch (Exception e) {
            System.err.println("Error in burn logs state: " + e.getMessage());
        }
    }


    /**
     * Gets total count of rejuvenation potions (all dose variations)
     */
    private static int getTotalRejuvenationPotions() {
        return Rs2Inventory.count("Rejuvenation potion (1)") + 
               Rs2Inventory.count("Rejuvenation potion (2)") + 
               Rs2Inventory.count("Rejuvenation potion (3)") + 
               Rs2Inventory.count("Rejuvenation potion (4)");
    }

    /**
     * Simplified - Handles getting concoctions from the crate
     */
    private void handleGetConcoctionsState(GameState gameState) {
        try {
            // If we're outside, enter the game room first
            if (!WintertodtLocationManager.isInsideGameRoom() || Rs2Player.getWorldLocation().distanceTo(CRATE_STAND_LOCATION) > 3) {
                Rs2Walker.walkTo(CRATE_STAND_LOCATION, 3);
                Rs2Player.waitForWalking();
                return;
            }
            
            // Navigate to crate area
            if (Rs2Player.getWorldLocation().distanceTo(CRATE_STAND_LOCATION) <= 3 && Rs2Player.getWorldLocation().distanceTo(CRATE_STAND_LOCATION) > 1) {
                Rs2Walker.walkFastCanvas(CRATE_STAND_LOCATION);
                Rs2Player.waitForWalking();
                return;
            }

            // ROCK SOLID COUNTING
            int currentPotions = getTotalRejuvenationPotions();
            int currentConcoctions = Rs2Inventory.count(ItemID.REJUVENATION_POTION_UNF);
            int totalHealingItems = currentPotions + currentConcoctions; // What we can actually use
            int needed = config.foodAmount() - totalHealingItems;
            
            Microbot.log("CONCOCTIONS: Need " + needed + " more (potions: " + currentPotions + ", concoctions: " + currentConcoctions + ", target: " + config.foodAmount() + ")");
            
            if (needed <= 0) {
                Microbot.log("Have enough total healing items (" + totalHealingItems + "/" + config.foodAmount() + "), moving to herbs");
                changeState(State.GET_HERBS);
                return;
            }

            // Find and interact with crate
            TileObject crate = Rs2GameObject.findObject(CRATE_OBJECT_ID, CRATE_LOCATION);
            if (crate == null) {
                crate = Rs2GameObject.getGameObjects(CRATE_OBJECT_ID).stream()
                    .filter(c -> c.getWorldLocation().distanceTo(CRATE_LOCATION) <= 2)
                    .findFirst()
                    .orElse(null);
            }
            
            if (crate != null) {
                if (Rs2GameObject.interact(crate, "Take-concoction")) {
                    // Wait for inventory change
                    int beforeCount = Rs2Inventory.count(ItemID.REJUVENATION_POTION_UNF);
                    sleepUntilTrue(() -> Rs2Inventory.count(ItemID.REJUVENATION_POTION_UNF) > beforeCount, 100, 3000);
                    actionsPerformed++;
                    Microbot.log("Got concoction!");
                } else {
                    Microbot.log("Failed to interact with crate, retrying...");
                }
            } else {
                Microbot.log("Could not find crate");
                // If we have some concoctions, try to proceed
                if (currentConcoctions > 0) {
                    changeState(State.GET_HERBS);
                } else {
                    sleepGaussian(2000, 500);
                }
            }

        } catch (Exception e) {
            System.err.println("Error in get concoctions state: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Simplified - Handles getting herbs from sprouting roots
     */
    private void handleGetHerbsState(GameState gameState) {
        try {
            // If we're outside, enter the game room first
            if (!WintertodtLocationManager.isInsideGameRoom() || Rs2Player.getWorldLocation().distanceTo(SPROUTING_ROOTS_STAND) > 3) {
                Rs2Walker.walkTo(SPROUTING_ROOTS_STAND, 3);
                Rs2Player.waitForWalking();
                return;
            }
            
            // ROCK SOLID COUNTING
            int currentPotions = getTotalRejuvenationPotions();
            int currentConcoctions = Rs2Inventory.count(ItemID.REJUVENATION_POTION_UNF);
            int currentHerbs = Rs2Inventory.count(ItemID.BRUMA_HERB);
            int totalHealingItems = currentPotions + currentConcoctions; // What we can actually use for healing
            int potionsWeMakeCanMake = Math.min(currentConcoctions, currentHerbs); // Potions we can make right now
            int totalAfterCombining = currentPotions + potionsWeMakeCanMake;
            
            Microbot.log("HERBS: Currently have " + totalHealingItems + "/" + config.foodAmount() + " healing items");
            Microbot.log("HERBS: After combining would have " + totalAfterCombining + "/" + config.foodAmount() + " (concoctions: " + currentConcoctions + ", herbs: " + currentHerbs + ")");
            
            // If we already have enough total healing items, go straight to combining
            if (totalAfterCombining >= config.foodAmount()) {
                Microbot.log("Have enough herbs for combining, moving to make potions");
                changeState(State.MAKE_POTIONS);
                return;
            }
            
            // We need more herbs - calculate how many
            int herbsNeeded = currentConcoctions - currentHerbs;
            
            // Navigate to sprouting roots
            if (Rs2Player.getWorldLocation().distanceTo(SPROUTING_ROOTS_STAND) <= 3 && Rs2Player.getWorldLocation().distanceTo(SPROUTING_ROOTS_STAND) > 1) {
                Rs2Walker.walkFastCanvas(SPROUTING_ROOTS_STAND);
                Rs2Player.waitForWalking();
                return;
            }

            // If we're animating (picking), just wait
            if (Rs2Player.isAnimating()) {
                Microbot.log("Picking herbs... (need " + herbsNeeded + " more)");
                return;
            }

            // Find sprouting roots and pick
            TileObject roots = Rs2GameObject.findObject(SPROUTING_ROOTS_OBJECT_ID, SPROUTING_ROOTS);
            if (roots == null) {
                roots = Rs2GameObject.getGameObjects(SPROUTING_ROOTS_OBJECT_ID).stream()
                    .filter(r -> r.getWorldLocation().distanceTo(SPROUTING_ROOTS) <= 2)
                    .findFirst()
                    .orElse(null);
            }
            
            if (roots != null) {
                Microbot.log("Picking herbs (need " + herbsNeeded + " more)");
                if (Rs2GameObject.interact(roots, "Pick")) {
                    sleepUntilTrue(() -> Rs2Player.isAnimating(), 100, 2000);
                    actionsPerformed++;
                } else {
                    Microbot.log("Failed to interact with sprouting roots");
                }
            } else {
                Microbot.log("Could not find sprouting roots");
                // If we have some herbs and concoctions, try to make potions
                if (currentHerbs > 0 && currentConcoctions > 0) {
                    changeState(State.MAKE_POTIONS);
                } else {
                    sleepGaussian(2000, 500);
                }
            }

        } catch (Exception e) {
            System.err.println("Error in get herbs state: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handles combining concoctions and herbs WITHOUT animation checks
     */
    private void handleMakePotionsState(GameState gameState) {
        try {
            // ROCK SOLID COUNTING
            int currentPotions = getTotalRejuvenationPotions();
            int currentConcoctions = Rs2Inventory.count(ItemID.REJUVENATION_POTION_UNF);
            int currentHerbs = Rs2Inventory.count(ItemID.BRUMA_HERB);
            
            Microbot.log("COMBINING: potions: " + currentPotions + ", concoctions: " + currentConcoctions + ", herbs: " + currentHerbs + ", target: " + config.foodAmount());
            
            // Check if we have enough potions now
            if (currentPotions >= config.foodAmount()) {
                Microbot.log("Have enough rejuvenation potions (" + currentPotions + "/" + config.foodAmount() + "), cleaning up and organizing inventory");
                organizeRejuvenationPotionsInInventory();
                changeState(State.ENTER_ROOM);
                return;
            }
            
            // Check if we need more ingredients
            if (currentConcoctions <= 0 && currentPotions < config.foodAmount()) {
                Microbot.log("Need more concoctions to reach target");
                changeState(State.GET_CONCOCTIONS);
                return;
            }
            
            if (currentHerbs <= 0 && currentConcoctions > 0 && currentPotions < config.foodAmount()) {
                Microbot.log("Need more herbs to combine");
                changeState(State.GET_HERBS);
                return;
            }

            // FIXED: Just combine immediately when we have both ingredients - NO ANIMATION CHECKS!
            if (currentConcoctions > 0 && currentHerbs > 0) {
                int potionsToMake = Math.min(currentConcoctions, currentHerbs);
                Microbot.log("Starting combination (will make " + potionsToMake + " potions)");
                if (Rs2Inventory.combineClosest(ItemID.REJUVENATION_POTION_UNF, ItemID.BRUMA_HERB)) {
                    // Small delay to let the combination process
                    sleepUntilTrue(() -> Rs2Inventory.count(ItemID.REJUVENATION_POTION_1) + Rs2Inventory.count(ItemID.REJUVENATION_POTION_2) + Rs2Inventory.count(ItemID.REJUVENATION_POTION_3) + Rs2Inventory.count(ItemID.REJUVENATION_POTION_4) >= potionsToMake, 100, 5000);
                    actionsPerformed++;
                    Microbot.log("Combination attempted - checking results next tick");
                } else {
                    Microbot.log("Failed to start combining - retrying next tick");
                    sleepGaussian(1000, 300);
                }
            } else {
                // Shouldn't get here, but handle it
                Microbot.log("No valid combinations possible - checking what we need");
                if (currentPotions >= config.foodAmount()) {
                    cleanupExtraResources();
                    changeState(State.ENTER_ROOM);
                } else if (currentConcoctions <= 0) {
                    changeState(State.GET_CONCOCTIONS);
                } else {
                    changeState(State.GET_HERBS);
                }
            }

        } catch (Exception e) {
            System.err.println("Error in make potions state: " + e.getMessage());
        }
    }

    /**
     * Organizes rejuvenation potions in the inventory for optimal layout
     */
    private void organizeRejuvenationPotionsInInventory() {
        try {
            Microbot.log("Cleaning up extra resources before organizing potions...");
            
            // Clean up first to make space
            cleanupExtraResources();
            sleepGaussian(500, 200); // Wait for cleanup to complete
            
            Microbot.log("Organizing rejuvenation potions in inventory...");
            
            // Get all rejuvenation potions (all dose variations)
            List<Rs2ItemModel> allPotions = new ArrayList<>();
            for (Rs2ItemModel item : Rs2Inventory.items()) {
                if (item != null && item.getName() != null && item.getName().startsWith("Rejuvenation potion")) {
                    allPotions.add(item);
                }
            }
            
            if (allPotions.isEmpty()) {
                Microbot.log("No rejuvenation potions found to organize");
                return;
            }
            
            // Sort potions by dose (4-dose first, then 3, 2, 1)
            allPotions.sort((a, b) -> {
                int doseA = extractDoseFromName(a.getName());
                int doseB = extractDoseFromName(b.getName());
                return Integer.compare(doseB, doseA); // Higher dose first
            });
            
            // Target slots for food items (leftmost column: 0, 4, 8, 12, 16, 20, 24)
            int[] targetSlots = {0, 4, 8, 12, 16, 20, 24}; // Upper left column going down
            
            int slotIndex = 0;
            for (Rs2ItemModel potion : allPotions) {
                if (slotIndex >= targetSlots.length) {
                    break; // No more target slots available
                }
                
                int targetSlot = targetSlots[slotIndex];
                
                // Check if target slot is empty
                Rs2ItemModel slotItem = Rs2Inventory.get(targetSlot);
                boolean slotIsEmpty = (slotItem == null);
                
                // Only move if not already in target position
                if (potion.getSlot() != targetSlot) {
                    if (slotIsEmpty) {
                        Microbot.log("Moving " + potion.getName() + " from slot " + potion.getSlot() + " to empty slot " + targetSlot);
                        
                        if (Rs2Inventory.moveItemToSlot(potion, targetSlot)) {
                            sleepUntilTrue(() -> Rs2Inventory.slotContains(targetSlot, potion.getName()), 100, 2000);
                            sleepGaussian(300, 100); // Small delay between moves for natural behavior
                        }
                    } else {
                        Microbot.log("Target slot " + targetSlot + " is occupied by " + slotItem.getName() + ", skipping to next slot");
                        continue; // Skip this slot and try the next one
                    }
                } else {
                    Microbot.log("Potion " + potion.getName() + " already in target slot " + targetSlot);
                }
                
                slotIndex++;
            }
            
            Microbot.log("Inventory organization completed - potions arranged in leftmost column starting from slot 0");
            
        } catch (Exception e) {
            Microbot.log("Error organizing rejuvenation potions: " + e.getMessage());
        }
    }
    
    /**
     * Extracts the dose number from a potion name (e.g., "Rejuvenation potion (4)" -> 4)
     */
    private int extractDoseFromName(String name) {
        try {
            if (name.contains("(4)")) return 4;
            if (name.contains("(3)")) return 3;
            if (name.contains("(2)")) return 2;
            if (name.contains("(1)")) return 1;
            return 0; // Unknown dose
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Simplified cleanup
     */
    private void cleanupExtraResources() {
        try {
            Microbot.log("Cleaning up extra resources...");
            
            // Drop extra concoctions
            if (Rs2Inventory.hasItem(ItemID.REJUVENATION_POTION_UNF)) {
                Rs2Inventory.dropAll(ItemID.REJUVENATION_POTION_UNF);
                sleepGaussian(300, 100);
            }
            
            // Drop extra herbs
            if (Rs2Inventory.hasItem(ItemID.BRUMA_HERB)) {
                Rs2Inventory.dropAll(ItemID.BRUMA_HERB);
                sleepGaussian(300, 100);
            }
            
            int finalPotions = getTotalRejuvenationPotions();
            Microbot.log("Cleanup completed - final potion count: " + finalPotions);
            
        } catch (Exception e) {
            Microbot.log("Error during cleanup: " + e.getMessage());
        }
    }

    /**
     * Determines if the player should start burning logs.
     *
     * @param gameState Current game state
     * @return true if should burn logs
     */
    private boolean shouldBurnLogs(GameState gameState) {
        if (!gameState.hasItemsToBurn) {
            setLockState(State.BURN_LOGS, false);
            return false;
        }
        changeState(State.BURN_LOGS, true);
        return true;
    }

    /**
     * Handles eating and health management.
     *
     * @param gameState Current game state
     * @return true if food was consumed
     */
    private boolean handleEating(GameState gameState) {
        if (gameState.playerWarmth <= config.eatAtWarmthLevel()) {
            try {
                /* Always deselect knife before eating */
                if (isKnifeSelected()) {
                    deselectSelectedItem();
                    sleepGaussian(200, 50);  // Small delay to ensure deselection
                }

                if (usesPotions) {
                    List<Rs2ItemModel> rejuvenationPotions = Rs2Inventory.getPotions();
                    if (!rejuvenationPotions.isEmpty()) {
                        Rs2Inventory.interact(rejuvenationPotions.get(0), "Drink");
                        sleepGaussian(600, 150);
                        plugin.setFoodConsumed(plugin.getFoodConsumed() + 1);
                        resetActions = true;
                        return true;
                    }
                } else {
                    if (Rs2Player.useFood()) {
                        sleepGaussian(600, 150);
                        plugin.setFoodConsumed(plugin.getFoodConsumed() + 1);
                        Rs2Inventory.dropAll("jug");
                        resetActions = true;
                        return true;
                    }
                }
            } catch (Exception e) {
                System.err.println("Error handling eating: " + e.getMessage());
            }
        }
        return false;
    }

    /**
     * Determines if we should start a new run of chopping or continue the current one.
     * This method embodies the "Action Plan" logic.
     *
     * @param gameState Current game state
     * @return true if the bot should be in the CHOP_ROOTS state
     */
    private boolean shouldStartOrContinueChopping(GameState gameState) {
        // Don't chop if we are full or have items but our plan is complete.
        if (gameState.inventoryFull || (gameState.hasItemsToBurn && targetRootsForThisRun == 0)) {
            return false;
        }

        /* ensure we are close enough to the brazier side first */
        double dist = Rs2Player.getWorldLocation()
                               .distanceTo(config.brazierLocation().getBRAZIER_LOCATION());
        if (dist > CHOPPING_RADIUS) {
            navigateToBrazier();           // walk closer before planning chop
            return false;                  // do NOT switch state this tick
        }

        // --- Plan a new run if we don't have one ---
        if (targetRootsForThisRun == 0) {
            // Only plan a new run if we have nothing to burn.
            if (gameState.hasItemsToBurn) {
                return false;
            }

            // Calculate the total time it takes to get and process one log
            double singleLogProcessingTimeSec = (avgChopMs + (config.fletchRoots() ? avgFletchMs : 0) + avgFeedMs + addToEstimatedTimePerCycleMs) / 1000.0;
            if (singleLogProcessingTimeSec <= 0.1) {
                return false; // Avoid division by zero if averages are not ready
            }

            // Calculate how many logs we can realistically process in the time remaining
            double availableTimeSec = estimatedSecondsLeft - SAFETY_BUFFER_SEC; // Extra buffer for starting a new run
            int maxLogsInTime = (int) (availableTimeSec / singleLogProcessingTimeSec);

            // We can't chop more than our available inventory space
            int availableInvSlots = Rs2Inventory.getEmptySlots();

            // The plan is to chop the minimum of what we have time for vs what we have space for
            int calculatedTarget = Math.min(maxLogsInTime + EXTRA_ROOTS_BUFFER, availableInvSlots);

            if (calculatedTarget <= 0) {
                targetRootsForThisRun = 0;
                rootsChoppedThisRun = 0;
                return false; // Not enough time or space to even start
            }

            targetRootsForThisRun = calculatedTarget;
            rootsChoppedThisRun = 0;
            rootsToChopGoal = targetRootsForThisRun; // Update for overlay
            Microbot.log("New Action Plan: Chop " + targetRootsForThisRun + " roots.");

            /* set fletch / feed goals & reset progress  */
            fletchGoal      = config.fletchRoots() ? targetRootsForThisRun : 0;
            feedGoal        = targetRootsForThisRun;
            fletchedThisRun = 0;
            fedThisRun      = 0;
        }

        // --- Execute the current plan ---
        int currentRoots = Rs2Inventory.count(ItemID.BRUMA_ROOT) + Rs2Inventory.count(ItemID.BRUMA_KINDLING);
        if (currentRoots < targetRootsForThisRun) {
            changeState(State.CHOP_ROOTS, true);
            return true; // We need to chop more to meet our goal
        } else {
            // We have met our goal for this run. Reset the plan for the next run.
            targetRootsForThisRun = 0;
            rootsChoppedThisRun = 0;
            // Don't reset rootsToChopGoal here, so overlay shows the last goal until a new one is made.
            return false; // Goal met, stop chopping.
        }
    }

    /**
     * Determines if the player should light the brazier.
     *
     * @param gameState Current game state
     * @return true if should light brazier
     */
    private boolean shouldLightBrazier(GameState gameState) {
        // Don't try to light if already in lighting state to avoid infinite loops
        if (state == State.LIGHT_BRAZIER) {
            return false;
        }
        
        if (!gameState.isWintertodtAlive) return false;          // round not active
        if (gameState.wintertodtHp == 0)   return false;         // boss just died
        if (gameState.needBanking)         return false;
        
        // Allow lighting during CHOP_ROOTS only if it's a priority at round start
        if (state == State.CHOP_ROOTS && !shouldPriorizeBrazierAtStart) return false;

        if (gameState.brazier == null || gameState.burningBrazier != null) {
            setLockState(State.LIGHT_BRAZIER, false);
            // Reset priority flag if brazier is already lit or doesn't exist
            if (shouldPriorizeBrazierAtStart) {
                shouldPriorizeBrazierAtStart = false;
                Microbot.log("Brazier priority reset - brazier already lit or unavailable");
            }
            return false;
        }

        changeState(State.LIGHT_BRAZIER, true);
        return true;
    }

    /**
     * Drops unnecessary items based on configuration.
     */
    private void dropUnnecessaryItems() {
        try {
            // Drop knife if fletching is disabled
            if (!config.fletchRoots() && Rs2Inventory.hasItem(ItemID.KNIFE)) {
                Rs2Inventory.drop(ItemID.KNIFE);
                sleepGaussian(300, 100);
            }

            // Drop hammer if fixing is disabled
            if (!config.fixBrazier() && Rs2Inventory.hasItem(ItemID.HAMMER)) {
                Rs2Inventory.drop(ItemID.HAMMER);
                sleepGaussian(300, 100);
            }

            // Drop tinderbox if using bruma torch
            if ((Rs2Equipment.isWearing(ItemID.BRUMA_TORCH) ||
                    Rs2Equipment.isWearing(ItemID.BRUMA_TORCH_OFFHAND)) &&
                    Rs2Inventory.hasItem(ItemID.TINDERBOX)) {
                Rs2Inventory.drop(ItemID.TINDERBOX);
                sleepGaussian(300, 100);
            }

        } catch (Exception e) {
            System.err.println("Error dropping items: " + e.getMessage());
        }
    }

    /**
     * Navigates to the preferred brazier location.
     */
    private void navigateToBrazier() {
        try {
            WorldPoint brazierLocation = config.brazierLocation().getBRAZIER_LOCATION();
            double distance = Rs2Player.getWorldLocation().distanceTo(brazierLocation);

            if (distance > 8) {
                Rs2Walker.walkTo(brazierLocation, 3);
                Rs2Player.waitForWalking();
            } else if (distance > 2) {
                Rs2Walker.walkFastCanvas(brazierLocation);
                sleepGaussian(GAME_TICK_LENGTH, 100);
            }

        } catch (Exception e) {
            System.err.println("Error navigating to brazier: " + e.getMessage());
        }
    }

    /**
     * Dodges snowfall damage by tracking specific projectiles.
     */
    private void dodgeSnowfallDamage(GameState gameState) {
        try {
            if (!resetActions) {
                // Track projectiles to detect incoming snowfall damage
                Deque<Projectile> projectiles = Microbot.getClient().getProjectiles();
                
                for (Projectile projectile : projectiles) {
                    if (projectile.getId() == 501 && projectile.getHeight() == 150) { //Somewhere is said this -1268, by testing runelite shows 150
                        // Check if there's a graphics object with ID 502 within 1 tile
                        boolean hasNearbyGraphics = false;
                        WorldPoint playerLocation = Rs2Player.getWorldLocation();
                        
                        for (GraphicsObject graphicsObject : Microbot.getClient().getGraphicsObjects()) {
                            if (graphicsObject.getId() == 502) {
                                WorldPoint dangerZone = WorldPoint.fromLocalInstance(
                                        Microbot.getClient(), graphicsObject.getLocation());
                                
                                if (dangerZone.distanceTo(playerLocation) <= 1) {
                                    hasNearbyGraphics = true;
                                    break;
                                }
                            }
                        }
                        
                        if (hasNearbyGraphics) {
                            // 80% chance to dodge when all conditions are met
                            if (random.nextInt(100) < 80) {
                                // Dodge by moving one tile south
                                WorldPoint safeSpot = new WorldPoint(
                                        Rs2Player.getWorldLocation().getX(),
                                        Rs2Player.getWorldLocation().getY() - 1,
                                        Rs2Player.getWorldLocation().getPlane()
                                );

                                Rs2Walker.walkFastCanvas(safeSpot);
                                Rs2Player.waitForWalking(1500);
                                resetActions = true;
                                Microbot.log("Dodged snowfall damage (80% chance triggered)");
                                Microbot.log("Waiting for burning brazier to go out after snowfall damage...");
                                Rs2GameObject.hoverOverObject(Rs2GameObject.findReachableObject("brazier", false, 4, Rs2Player.getWorldLocation()));

                                boolean brazierWentOut = sleepUntilTrue(
                                        () -> {
                                            // Wait until burning brazier is gone OR we're no longer in burn state
                                            GameObject burningBrazier = Rs2GameObject.getGameObject(BURNING_BRAZIER_29314,5);
                                            return burningBrazier == null || (state != State.BURN_LOGS && state != State.FLETCH_LOGS);
                                        },
                                        100,  // Check every 100ms
                                        5000 // Timeout after 5 seconds
                                );

                                if (brazierWentOut) {
                                    Microbot.log("Brazier state changed, continuing...");
                                } else {
                                    Microbot.log("Timeout waiting for brazier change - continuing anyway");
                                }
                            } else {
                                Microbot.log("Snowfall detected but purposely NOT dodging (20% chance - staying put for realism ;) )");
                            }
                            
                            break; // Only need to process once per projectile detection
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error dodging snowfall: " + e.getMessage());
        }
    }

    /**
     * Handles the banking logic including rejuvenation potions and regular food.
     *
     * @return true if banking completed successfully
     */
    private boolean executeBankingLogic() {
        try {
            return handleRegularBankingLogic();
        } catch (Exception e) {
            System.err.println("Error in banking logic: " + e.getMessage());
            return false;
        }
    }

    /**
     * Handles regular banking for food and supplies.
     *
     * @return true if banking completed successfully
     */
    private boolean handleRegularBankingLogic() {
        try {
            // Heal up if needed
            if (!Rs2Player.isFullHealth() && Rs2Inventory.hasItem(config.food().getName(), false)) {
                eatAt(99);
                return true;
            }

            // Check if we have enough food
            if (Rs2Inventory.hasItemAmount(config.food().getName(), config.foodAmount())) {
                // Before leaving bank, always check if we can upgrade our gear
                if (Rs2Bank.isOpen()) {
                    checkAndUpgradeGear();
                    depositUnnecessaryItems(); // Always clean inventory after potential gear swaps
                }
                changeState(State.ENTER_ROOM);
                return true;
            }

            // Navigate to bank
            if (Rs2Player.getWorldLocation().distanceTo(BANK_LOCATION) > 6) {
                Rs2Walker.walkTo(BANK_LOCATION);
                Rs2Player.waitForWalking();
            }

            // Prevent bug that causes bot to not being able to wear items in bank by adding inventory open command first
            if (!Rs2Inventory.isOpen()) {
                Rs2Inventory.open();
            }

            // Open bank
            Rs2Bank.useBank();
            if (!Rs2Bank.isOpen()) return true;

            // ALWAYS check and upgrade gear when banking
            checkAndUpgradeGear();
            depositUnnecessaryItems(); // Clean inventory after gear swaps and before withdrawing

            // Count current food
            int currentFoodCount = (int) Rs2Inventory.getInventoryFood().stream().count();

            // Withdraw required items
            withdrawRequiredItems();

            // Check food availability
            if (!Rs2Bank.hasBankItem(config.food().getName(), config.foodAmount(), true)) {
                Microbot.showMessage("Insufficient food in bank! Please restock.");
                Microbot.pauseAllScripts = true;
                return false;
            }

            // Withdraw food
            int foodNeeded = config.foodAmount() - currentFoodCount;
            if (foodNeeded > 0) {
                Rs2Bank.withdrawX(config.food().getId(), foodNeeded);
            }

            return sleepUntilTrue(
                    () -> Rs2Inventory.hasItemAmount(config.food().getName(), config.foodAmount(), false, true),
                    100, 5000
            );

        } catch (Exception e) {
            System.err.println("Error in regular banking logic: " + e.getMessage());
            return false;
        }
    }

    /**
     * Withdraws required items with automatic axe handling.
     */
    private void withdrawRequiredItems() {
        try {
            // Get automatic axe decision
            WintertodtAxeManager.AxeDecision axeDecision = WintertodtAxeManager.determineOptimalAxeSetup();
            
            // Withdraw hammer for fixing
            if (config.fixBrazier() && !Rs2Inventory.hasItem("hammer")) {
                Rs2Bank.withdrawX(true, "hammer", 1);
                sleepGaussian(300, 100);
            }

            // Withdraw tinderbox if needed (no bruma torch equipped)
            if (!Rs2Equipment.isWearing(ItemID.BRUMA_TORCH) &&
                    !Rs2Equipment.isWearing(ItemID.BRUMA_TORCH_OFFHAND) &&
                    !Rs2Inventory.hasItem("tinderbox")) {
                Rs2Bank.withdrawX(true, "tinderbox", 1, true);
                sleepGaussian(300, 100);
            }

            // Withdraw knife for fletching
            if (config.fletchRoots() && !Rs2Inventory.hasItem("knife")) {
                Rs2Bank.withdrawX(true, "knife", 1, true);
                sleepGaussian(300, 100);
            }

            // Handle axe based on automatic decision
            if (axeDecision.shouldKeepInInventory() && !Rs2Inventory.hasItem(axeDecision.getAxeId())) {
                Rs2Bank.withdrawX(true, axeDecision.getAxeName(), 1);
                sleepGaussian(300, 100);
                Microbot.log("Withdrew axe for inventory: " + axeDecision.getAxeName());
            }

        } catch (Exception e) {
            System.err.println("Error withdrawing required items: " + e.getMessage());
        }
    }

    /**
     * Gets the current warmth level from the game interface.
     *
     * @return warmth level percentage (0-100)
     */
    public static int getWarmthLevel() {
        try {
            String warmthWidgetText = Rs2Widget.getChildWidgetText(396, 20);

            if (warmthWidgetText == null || warmthWidgetText.isEmpty()) {
                return 100; // Default to full warmth if widget not found
            }

            // Primary pattern: digits before %
            Pattern pattern = Pattern.compile("(\\d+)%");
            Matcher matcher = pattern.matcher(warmthWidgetText);

            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }

            // Fallback pattern: any digits
            Pattern fallbackPattern = Pattern.compile("\\d+");
            Matcher fallbackMatcher = fallbackPattern.matcher(warmthWidgetText);

            if (fallbackMatcher.find()) {
                return Integer.parseInt(fallbackMatcher.group());
            }

            return 100; // Default if no pattern matches

        } catch (Exception e) {
            System.err.println("Error getting warmth level: " + e.getMessage());
            return 100; // Safe default
        }
    }

    /**
     * Tracks performance metrics and handles consecutive failures.
     *
     * @param loopStartTime Start time of the current loop
     */
    private void trackPerformance(long loopStartTime) {
        long loopTime = System.currentTimeMillis() - loopStartTime;

        // Log performance occasionally
        if (System.currentTimeMillis() - lastPerformanceCheck > 60000) { // Every minute
            System.out.println(String.format("Performance: %d actions in last minute, avg loop time: %dms",
                    actionsPerformed, loopTime));

            // Reset counters
            actionsPerformed = 0;
            lastPerformanceCheck = System.currentTimeMillis();

            // Reset consecutive failures if we're performing actions
            if (actionsPerformed > 0) {
                consecutiveFailures = 0;
            }
        }

        // Handle too many consecutive failures
        if (consecutiveFailures > 10) {
            Microbot.log("Too many consecutive failures, resetting state");
            setLockState(state, false);
            resetActions = true;
            consecutiveFailures = 0;
        }
    }



    /**
     * Determines if a break should be triggered based on various conditions.
     *
     * @return true if break should be triggered
     */
    private boolean shouldTriggerBreak() {
        // Check if new custom break system is active
        if (breakManager != null && WintertodtBreakManager.isBreakActive()) {
            return true;
        }
        
        // Check if break handler has a break active
        if (BreakHandlerScript.isBreakActive()) {
            return true;
        }
        


        return false;
    }

    /**
     * Handles the triggering and execution of breaks.
     */
    private void handleBreakTrigger() {
        try {
            // Handle new custom break system
            if (breakManager != null && WintertodtBreakManager.isBreakActive()) {
                // Custom break manager is handling the break, we just pause
                Microbot.log("Custom break system active, pausing script");
                return;
            }
            
            // If break handler has a break active, respect it
            if (BreakHandlerScript.isBreakActive()) {
                Microbot.log("Break handler break active, pausing script");
                return;
            }
            
        } catch (Exception e) {
            System.err.println("Error handling break trigger: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handles script exceptions with proper logging and recovery.
     *
     * @param ex The exception to handle
     */
    private void handleScriptException(Exception ex) {
        Microbot.log("Script error: " + ex.getMessage());
        ex.printStackTrace();
        consecutiveFailures++;

        // Reset state on critical errors
        if (consecutiveFailures > 5) {
            setLockState(state, false);
            resetActions = true;
        }
    }

    /**
     * Checks for gear upgrades during banking but preserves already-arranged inventory.
     */
    private void checkAndUpgradeGear() {
        try {
            Microbot.log("Checking for gear upgrades during banking...");
            
            // Check if inventory is already optimally arranged (tools in correct slots)
            boolean inventoryAlreadyArranged = isInventoryOptimallyArranged();
            
            if (inventoryAlreadyArranged) {
                Microbot.log("Inventory already optimally arranged - skipping full gear setup");
                
                // Only check if we're missing any required tools
                if (isMissingRequiredTools()) {
                    Microbot.log("Missing some tools - withdrawing only what's needed");
                    withdrawMissingToolsOnly();
                } else {
                    Microbot.log("All required tools present - no gear changes needed");
                }
                return;
            }
            
            // Full gear analysis and setup if inventory not arranged
            Microbot.log("Running full gear analysis and setup...");
            WintertodtGearManager tempGearManager = new WintertodtGearManager(config);
            if (tempGearManager.setupOptimalGear()) {
                Microbot.log("Gear upgrades applied successfully during banking");
                logCurrentGearSetup(tempGearManager);
            } else {
                Microbot.log("Gear upgrade check completed with warnings");
            }
            
        } catch (Exception e) {
            Microbot.log("Error during gear upgrade check: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Checks if inventory tools are already in optimal positions.
     */
    private boolean isInventoryOptimallyArranged() {
        try {
            // Check if key tools are in their expected slots
            Rs2ItemModel slot27 = Rs2Inventory.get(27); // Knife slot
            Rs2ItemModel slot26 = Rs2Inventory.get(26); // Hammer slot  
            Rs2ItemModel slot24 = Rs2Inventory.get(24); // Axe slot
            
            // Get axe decision for current setup
            WintertodtAxeManager.AxeDecision axeDecision = WintertodtAxeManager.determineOptimalAxeSetup();
            
            boolean knifeCorrect = !config.fletchRoots() || 
                                  (slot27 != null && slot27.getId() == ItemID.KNIFE);
            boolean hammerCorrect = !config.fixBrazier() || 
                                   (slot26 != null && slot26.getId() == ItemID.HAMMER);
            boolean axeCorrect = axeDecision.shouldEquipAxe() || 
                               (slot24 != null && slot24.getId() == axeDecision.getAxeId());
            
            return knifeCorrect && hammerCorrect && axeCorrect;
            
        } catch (Exception e) {
            Microbot.log("Error checking inventory arrangement: " + e.getMessage());
            return false; // Assume not arranged on error
        }
    }
    
    /**
     * Checks if we're missing any required tools.
     */
    private boolean isMissingRequiredTools() {
        WintertodtAxeManager.AxeDecision axeDecision = WintertodtAxeManager.determineOptimalAxeSetup();
        
        boolean hasAxe = axeDecision.shouldEquipAxe() ? 
                        Rs2Equipment.isWearing(axeDecision.getAxeId()) :
                        Rs2Inventory.hasItem(axeDecision.getAxeId());
                        
        boolean hasFireTool = Rs2Equipment.isWearing(ItemID.BRUMA_TORCH) ||
                             Rs2Equipment.isWearing(ItemID.BRUMA_TORCH_OFFHAND) ||
                             Rs2Inventory.hasItem(ItemID.TINDERBOX);
                             
        boolean hasKnife = !config.fletchRoots() || Rs2Inventory.hasItem(ItemID.KNIFE);
        boolean hasHammer = !config.fixBrazier() || Rs2Inventory.hasItem(ItemID.HAMMER);
        
        return !hasAxe || !hasFireTool || !hasKnife || !hasHammer;
    }
    
    /**
     * Withdraws only missing tools without disturbing arranged inventory.
     */
    private void withdrawMissingToolsOnly() {
        try {
            WintertodtAxeManager.AxeDecision axeDecision = WintertodtAxeManager.determineOptimalAxeSetup();
            
            // Check and withdraw missing axe
            if (!axeDecision.shouldEquipAxe() && !Rs2Inventory.hasItem(axeDecision.getAxeId())) {
                if (Rs2Bank.hasItem(axeDecision.getAxeId())) {
                    Rs2Bank.withdrawOne(axeDecision.getAxeId());
                    sleepUntilTrue(() -> Rs2Inventory.hasItem(axeDecision.getAxeId()), 100, 3000);
                    Microbot.log("Withdrew missing axe: " + axeDecision.getAxeName());
                }
            }
            
            // Check and withdraw missing knife
            if (config.fletchRoots() && !Rs2Inventory.hasItem(ItemID.KNIFE)) {
                if (Rs2Bank.hasItem(ItemID.KNIFE)) {
                    Rs2Bank.withdrawOne(ItemID.KNIFE);
                    sleepUntilTrue(() -> Rs2Inventory.hasItem(ItemID.KNIFE), 100, 3000);
                    Microbot.log("Withdrew missing knife");
                }
            }
            
            // Check and withdraw missing hammer
            if (config.fixBrazier() && !Rs2Inventory.hasItem(ItemID.HAMMER)) {
                if (Rs2Bank.hasItem(ItemID.HAMMER)) {
                    Rs2Bank.withdrawOne(ItemID.HAMMER);
                    sleepUntilTrue(() -> Rs2Inventory.hasItem(ItemID.HAMMER), 100, 3000);
                    Microbot.log("Withdrew missing hammer");
                }
            }
            
            // Check and withdraw missing tinderbox
            if (!Rs2Equipment.isWearing(ItemID.BRUMA_TORCH) && 
                !Rs2Equipment.isWearing(ItemID.BRUMA_TORCH_OFFHAND) &&
                !Rs2Inventory.hasItem(ItemID.TINDERBOX)) {
                if (Rs2Bank.hasItem(ItemID.TINDERBOX)) {
                    Rs2Bank.withdrawOne(ItemID.TINDERBOX);
                    sleepUntilTrue(() -> Rs2Inventory.hasItem(ItemID.TINDERBOX), 100, 3000);
                    Microbot.log("Withdrew missing tinderbox");
                }
            }
            
        } catch (Exception e) {
            Microbot.log("Error withdrawing missing tools: " + e.getMessage());
        }
    }
    
    /**
     * Logs current gear setup for debugging.
     */
    private void logCurrentGearSetup(WintertodtGearManager gearManager) {
        List<String> gearLog = gearManager.getGearAnalysisLog();
        for (String logEntry : gearLog) {
            Microbot.log("Gear: " + logEntry);
        }
    }

    /**
     * Cleanup method called when script shuts down.
     */
    @Override
    public void shutdown() {
        Microbot.log("Shutting down Enhanced Wintertodt Script");
        
        // Complete state reset to ensure clean shutdown
        resetAllScriptState();
        
        // Reset any active antiban states
        Rs2AntibanSettings.actionCooldownActive = false;

        
        // Unlock any break handler states
        if (BreakHandlerScript.isLockState()) {
            BreakHandlerScript.setLockState(false);
            Microbot.log("Unlocked break handler state during shutdown");
        }
        
        // Reset antiban settings but don't override user preferences
        Rs2Antiban.resetAntibanSettings(false);
        
        // Shutdown break manager
        if (breakManager != null) {
            breakManager.shutdown();
        }
        
        super.shutdown();
        Microbot.log("Script shutdown completed with full state reset");
    }

    /**
     * Gets the remaining time until next Wintertodt round from the game widget.
     * 
     * @return remaining seconds until next round, or -1 if not available
     */
    private int getWintertodtRemainingTime() {
        try {
            String timerText = Rs2Widget.getChildWidgetText(396, 26);
            if (timerText == null || timerText.isEmpty()) {
                return -1;
            }
            
            // Parse text like "The Wintertodt returns in: 0:05" or "The Wintertodt returns in: 1:23"
            String[] parts = timerText.split(":");
            if (parts.length >= 2) {
                String timePart = parts[parts.length - 1].trim(); // Get the last part after ":"
                String minutePart = parts[parts.length - 2].trim(); // Get the minute part
                
                // Extract just the numbers
                String minuteStr = minutePart.replaceAll("\\D+", "");
                String secondStr = timePart.replaceAll("\\D+", "");
                
                if (!minuteStr.isEmpty() && !secondStr.isEmpty()) {
                    int minutes = Integer.parseInt(minuteStr);
                    int seconds = Integer.parseInt(secondStr);
                    return (minutes * 60) + seconds;
                }
            }
            
            return -1;
            
        } catch (Exception e) {
            System.err.println("Error parsing Wintertodt timer: " + e.getMessage());
            return -1;
        }
    }

    /**
     * Updates the round timer tracking based on current game state.
     */
    private void updateRoundTimer() {
        try {
            int remainingSeconds = getWintertodtRemainingTime();
            long currentTime = System.currentTimeMillis();

            // If we have a valid remaining time
            if (remainingSeconds > 0) {
                // Calculate when the next round will start
                nextRoundStartTime = currentTime + (remainingSeconds * 1000);
                
                // If this is the first time we're seeing the timer, record round end time
                if (roundEndTime == -1) {
                    roundEndTime = currentTime;
                    mouseMovedOffscreenForRound = false;
                    hoveredForNextRound = false;
                    isHoverBeforeStartTimeCalculated = false;
                    hoverBeforeStartTime = 0;
                    Microbot.log("New Wintertodt round ended, next round starts in " + remainingSeconds + " seconds");

                    // Setup spam clicking for next round start
                    setupSpamClickingForRoundStart();

                    // Record round duration
                    if (currentRoundStartTime > 0) {
                        long roundDuration = currentTime - currentRoundStartTime;
                        if (roundDuration > 30000 && roundDuration < 600000) { // 30s-10min sanity check
                            previousRoundDurations.add(roundDuration);
                            if (previousRoundDurations.size() > 10) { // Keep last 10 rounds
                                ((java.util.LinkedList<Long>) previousRoundDurations).removeFirst();
                            }
                            Microbot.log("Logged round duration: " + (roundDuration / 1000) + "s. History size: " + previousRoundDurations.size());
                        }
                        currentRoundStartTime = 0;
                    }
                }
                
                lastKnownRemainingSeconds = remainingSeconds;
            } else if (lastKnownRemainingSeconds > 0 && remainingSeconds == -1) {
                // Timer widget disappeared, round likely started
                roundEndTime = -1;
                nextRoundStartTime = -1;
                mouseMovedOffscreenForRound = false;
                hoveredForNextRound = false;
                isHoverBeforeStartTimeCalculated = false;
                hoverBeforeStartTime = 0;
                lastKnownRemainingSeconds = -1;
                currentRoundStartTime = System.currentTimeMillis();
                
                // Prioritize brazier lighting at round start
                shouldPriorizeBrazierAtStart = true;
                
                Microbot.log("Wintertodt round started! Prioritizing brazier lighting.");
            }
            
        } catch (Exception e) {
            System.err.println("Error updating round timer: " + e.getMessage());
        }
    }

    /**
     * Determines what the next interactive object should be for hovering.
     * 
     * @param gameState Current game state
     * @return GameObject to hover over, or null if none appropriate
     */
    private GameObject getNextInteractiveObject(GameState gameState) {
        try {
            // Priority 1: Broken brazier (if we can fix it)
            if (gameState.brokenBrazier != null && config.fixBrazier()) {
                Microbot.log("DEBUG: Will hover over broken brazier for fixing");
                return gameState.brokenBrazier;
            }
            
            // Priority 2: Unlit brazier (if we need to light it)
            if (gameState.brazier != null && gameState.burningBrazier == null && !gameState.needBanking && !gameState.needPotions) {
                Microbot.log("DEBUG: Will hover over unlit brazier for lighting");
                return gameState.brazier;
            }
            
            // Priority 3: Burning brazier (if we have items to burn)
            if (gameState.burningBrazier != null && gameState.hasItemsToBurn) {
                Microbot.log("DEBUG: Will hover over burning brazier for feeding");
                return gameState.burningBrazier;
            }
            
            // Priority 4: Bruma roots on our side (if we intend to chop)
            if (!gameState.inventoryFull && !gameState.hasItemsToBurn && !gameState.needBanking && !gameState.needPotions) {
                GameObject root = getOwnSideRoot();
                if (root != null) {
                    Microbot.log("DEBUG: Will hover over bruma roots for chopping");
                    return root;
                }
            }
            
            // Priority 5: Any available brazier as fallback (prefer burning > unlit > broken)
            if (gameState.burningBrazier != null) {
                Microbot.log("DEBUG: Fallback to burning brazier");
                return gameState.burningBrazier;
            }
            if (gameState.brazier != null) {
                Microbot.log("DEBUG: Fallback to unlit brazier");
                return gameState.brazier;
            }
            if (gameState.brokenBrazier != null) {
                Microbot.log("DEBUG: Fallback to broken brazier");
                return gameState.brokenBrazier;
            }
            
            Microbot.log("DEBUG: No interactive objects found for hovering");
            return null;
            
        } catch (Exception e) {
            System.err.println("Error determining next interactive object: " + e.getMessage());
            return null;
        }
    }

    /**
     * Handles round timer-based mouse behavior during waiting state.
     * 
     * @param gameState Current game state
     */
    private void handleRoundTimerMouseBehavior(GameState gameState) {
        try {
            // Only apply this behavior if we're in waiting state during a round break
            if (state != State.WAITING || nextRoundStartTime == -1) {
                return;
            }
            
            long currentTime = System.currentTimeMillis();
            long timeUntilStart = nextRoundStartTime - currentTime;
            
            // Move mouse offscreen after round ends (do this once per round)
            if (!mouseMovedOffscreenForRound && timeUntilStart > 10000) { // More than 10 seconds left
                sleepGaussian(1000, 800);
                Rs2Antiban.moveMouseOffScreen(85);
                mouseMovedOffscreenForRound = true;
            }
            
            // Calculate hover time once per round (when to start hovering before round begins)
            if (!isHoverBeforeStartTimeCalculated && timeUntilStart > 1000) {
                isHoverBeforeStartTimeCalculated = true;
                // Random time between 1-8 seconds before round start to begin hovering
                hoverBeforeStartTime = Rs2Random.randomGaussian(1000, 8000);
                if (hoverBeforeStartTime <= 0) {
                    hoverBeforeStartTime = Rs2Random.randomGaussian(2000, 4000);
                }
                hoverBeforeStartTime = Math.max(500, Math.min(8000, hoverBeforeStartTime)); // Clamp to reasonable range
                Microbot.log("Will hover " + (hoverBeforeStartTime / 1000.0) + " seconds before round starts");
            }
            
            // Start hovering when time remaining reaches our calculated hover time (only if not spam clicking)
            if (!hoveredForNextRound && !spamClickingActive && timeUntilStart > 0 && timeUntilStart <= hoverBeforeStartTime) {
                GameObject nextObject = getNextInteractiveObject(gameState);
                if (nextObject != null) {
                    Rs2GameObject.hoverOverObject(nextObject);
                    hoveredForNextRound = true;
                    Microbot.log("Hovering over next interactive object: " + nextObject.getId() + 
                               " (" + (timeUntilStart / 1000.0) + "s before round start)");
                } else {
                    Microbot.log("Could not find interactive object to hover over");
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error handling round timer mouse behavior: " + e.getMessage());
        }
    }

    /**
     * Inner class to hold comprehensive game state information.
     */
    private static class GameState {
        boolean wintertodtRespawning;
        boolean isWintertodtAlive;
        int playerWarmth;
        boolean playerIsLowWarmth;
        GameObject brazier;
        GameObject brokenBrazier;
        GameObject burningBrazier;
        boolean needBanking;
        boolean needPotions = false; // For rejuvenation potion logic
        int wintertodtHp = -1;
        boolean inventoryFull;
        boolean hasItemsToBurn;
        boolean hasRootsToFletch;
    }

    /* ---------------------------------------------------------
     *  estimate seconds until kill from live HP / DPS
     * --------------------------------------------------------- */
    private void updateWintertodtHpTracking(int currentHp)
    {
        long now = System.currentTimeMillis();

        if (prevWintertodtHp != -1 && currentHp < prevWintertodtHp)
        {
            int  diffHp = prevWintertodtHp - currentHp;
            long diffMs = now - prevHpTimestamp;
            if (diffMs > 400)                                      // ignore <½-tick noise
            {
                double newHpPerSec = diffHp / (diffMs / 1000d);
                // simple EMA to smooth the DPS figure
                hpPerSecond = hpPerSecond == 0 ? newHpPerSec
                                               : (hpPerSecond * 0.7) + (newHpPerSec * 0.3);
            }
        }

        prevWintertodtHp = currentHp;
        prevHpTimestamp  = now;

        if (hpPerSecond > 0.01) {
            double dpsBasedEstimate = currentHp / hpPerSecond;

            if (!previousRoundDurations.isEmpty()) {
                double avgDurationMs = previousRoundDurations.stream().mapToLong(Long::longValue).average().orElse(0);
                if (avgDurationMs > 0) {
                    // Scale historical average by current HP percentage.
                    double historicalScaledEstimate = (avgDurationMs / 1000.0) * (currentHp / 100.0);
                    historicalEstimateSecondsLeft = historicalScaledEstimate;

                    // Blend the two estimates. Give more weight to historical as it's more stable.
                    estimatedSecondsLeft = (dpsBasedEstimate * 0.2) + (historicalScaledEstimate * 0.8);
                } else {
                    estimatedSecondsLeft = dpsBasedEstimate;
                    historicalEstimateSecondsLeft = 0;
                }
            } else {
                estimatedSecondsLeft = dpsBasedEstimate;
                historicalEstimateSecondsLeft = 0;
            }
        } else {
            estimatedSecondsLeft = 999;
            historicalEstimateSecondsLeft = 0;
        }
    }

    /* ---- update average action durations live --------------------------- */
    private static void noteActionDuration(String type, long duration) {
        double dur = duration;
        switch (type) {
            case "CHOP":
                avgChopMs = (avgChopMs * 0.8) + (dur * 0.2);
                break;
            case "FLETCH":
                avgFletchMs = (avgFletchMs * 0.8) + (dur * 0.2);
                break;
            case "FEED":
                avgFeedMs = (avgFeedMs * 0.8) + (dur * 0.2);
                break;
        }

        /* ---- recompute full-cycle estimate for overlay ---- */
        cycleTimeSec = (avgChopMs
                       + (config != null && config.fletchRoots() ? avgFletchMs : 0)
                       + avgFeedMs) / 1000.0;
    }

    private void noteActionDuration(String type, long startMs, long endMs)
    {
        noteActionDuration(type, endMs - startMs);
    }

    /* --------------------------------------------------------------------- */

    /**
     * Chooses the proper starting state the very first time the script
     * enters the main loop.
     */
    private void determineInitialState(GameState gs)
    {
        /* If startup was skipped (we're already in game room and ready), go straight to game logic */
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation.getY() > 3967 && // Inside game room 
            playerLocation.distanceTo(config.brazierLocation().getBRAZIER_LOCATION()) <= 15) {
            
            Microbot.log("Starting in game room - skipping banking phase and going directly to game logic");
            
            // If using rejuvenation potions and we don't have enough, we need to get them first
            if (usesPotions) {
                int currentPotions = Rs2Inventory.count("Rejuvenation potion ");
                if (currentPotions < config.minFood()) {
                    Microbot.log("Using rejuvenation potions but don't have enough - need to make them");
                    changeState(State.GET_CONCOCTIONS);
                    return;
                }
            }
            
            /* Go directly to appropriate game state */
            if (!gs.isWintertodtAlive) {
                changeState(State.WAITING);
                return;
            }
            
            if (gs.burningBrazier == null) {
                changeState(State.WAITING); // WAITING handler will light
                return;
            }
            
            if (gs.hasItemsToBurn) {
                changeState(State.BURN_LOGS);
                return;
            }
            
            if (!gs.inventoryFull) {
                changeState(State.CHOP_ROOTS);
                return;
            }
            
            changeState(State.WAITING);
            return;
        }
        
        /* 1. Bank first if food / tools are missing (but not for rejuvenation potions) */
        if (gs.needBanking)
        {
            changeState(State.BANKING);
            return;
        }

        /* Handle rejuvenation potions - if we need potions and we're using rejuv potions */
        if (usesPotions) {
            int currentPotions = Rs2Inventory.count("Rejuvenation potion ");
            if (currentPotions < config.minFood()) {
                Microbot.log("Need rejuvenation potions - starting potion creation workflow");
                changeState(State.GET_CONCOCTIONS);
                return;
            }
        }

        /* 2. Make sure we are on the correct side of the arena */
        if (Rs2Player.getWorldLocation()
                     .distanceTo(config.brazierLocation().getBRAZIER_LOCATION()) > 12)
        {
            changeState(State.ENTER_ROOM);               // walk inside
            return;
        }

        /* 3. Round not active yet → wait / hover */
        if (!gs.isWintertodtAlive)
        {
            changeState(State.WAITING);
            return;
        }

        /* 4. Round active – decide first action */
        if (gs.burningBrazier == null)                // need to light or relight
        {
            changeState(State.WAITING);               // WAITING handler will light
            return;
        }

        if (gs.hasItemsToBurn)
        {
            changeState(State.BURN_LOGS);
            return;
        }

        if (!gs.inventoryFull)
        {
            changeState(State.CHOP_ROOTS);
            return;
        }

        /* fallback */
        changeState(State.WAITING);
    }

    /* ------------ tiny mouse nudge after some clicks ------------------- */
    private void maybeNudgeMouse()
    {
        if (!config.humanizedTiming()) return;

        /*  1. decide whether we nudge at all
              probability = 18 % + |N(0,1)|·10   (≈ 5 % – 40 %)           */
        int moveChance = (int) Math.round(18 + Math.abs(random.nextGaussian()) * 10);
        moveChance = Math.max(5, Math.min(40, moveChance));

        if (random.nextInt(100) >= moveChance)
        {
            return;                                 // no micro-movement this time
        }

        try
        {
            Point start = Microbot.getMouse().getMousePosition();
            if (start == null) return;

            /* 2. first micro-movement  (σ ≈ 20 px, capped ±80) */
            int dx = (int) (random.nextGaussian() * 20);
            int dy = (int) (random.nextGaussian() * 20);
            dx = Math.max(-80, Math.min(80, dx));
            dy = Math.max(-80, Math.min(80, dy));

            Microbot.getMouse().move(start.x + dx, start.y + dy);

            /* 3. ~50 % chance of a quick follow-up wobble             */
            if (random.nextBoolean())
            {
                sleepGaussian(30, 15);              // brief pause

                int dx2 = (int) (random.nextGaussian() * 10);
                int dy2 = (int) (random.nextGaussian() * 10);
                dx2 = Math.max(-30, Math.min(30, dx2));
                dy2 = Math.max(-30, Math.min(30, dy2));

                Microbot.getMouse().move(start.x + dx + dx2,
                                          start.y + dy + dy2);
            }
        }
        catch (Exception ignored) {}
    }

    /* ------------ knife selection helpers ------------------------------ */
    private boolean isKnifeSelected()
    {
        return Rs2Inventory.isItemSelected()
               && Rs2Inventory.getSelectedItemId() == ItemID.KNIFE;
    }

    public static void deselectSelectedItem()
    {
        // Clicking the selected item to unselect it
        if (Rs2Inventory.isItemSelected())
        {
            Rs2Inventory.interact(Rs2Inventory.getSelectedItemId(), "Use");
            sleepGaussian(120, 40);
        }
    }
    // -------------------------------------------------------------------------

    /* remembers the root we last hovered while knife was selected */
    private Rs2ItemModel lastHoveredRoot = null;

    /** randomized threshold for knife pre-selection (recalculated once per full inventory cycle) */
    private int knifePreselectThreshold = 6;
    
    /** tracks the last inventory count to detect when we start a new full inventory cycle */
    private int lastInventoryCount = 0;

    /* ═══════════════════  LEAVING THE ARENA  ═════════════════════ */

    /** Rough check: Y-coordinate north of the doors means we are still inside */
    private boolean isInsideWintertodtArea()
    {
        return WintertodtLocationManager.isInsideGameRoom();
    }

    /**
     * Wrapper method that handles script-specific state management when leaving Wintertodt.
     * The actual door interaction is delegated to WintertodtLocationManager.
     *
     * @return true once the player is outside (south of the doors)
     */
    private boolean attemptLeaveWintertodt()
    {
        /* Already outside? → reset once and nothing else to do */
        if (!isInsideWintertodtArea())
        {
            if (previouslyInsideArena)
            {
                resetActionPlanning();
                previouslyInsideArena = false;
                waitingForRoundEnd = false; // Reset waiting flag when outside
            }
            return true;
        }
        previouslyInsideArena = true;

        /* Check points before leaving - wait for rewards if we have enough */
        int currentPoints = getWintertodtPoints();
        if (currentPoints >= 500) {
            Microbot.log("Have " + currentPoints + " points - waiting near door for round to end naturally");
            waitingForRoundEnd = true; // Set flag to prevent resuming activities
        } else {
            /* Reset waiting flag if points dropped below threshold */
            waitingForRoundEnd = false;
        }

        // Use the location manager's method for the actual door interaction
        return WintertodtLocationManager.attemptLeaveWintertodt();
    }

    /* ═════════════════════════════════════════════════════════════════════ */

    /** remembers whether the player was inside the arena in the last tick */
    private boolean previouslyInsideArena = true;

    /** tracks if we're waiting for round to end naturally to collect rewards */
    private boolean waitingForRoundEnd = false;

    /* ═══════════════════  ACTION / PLAN RESET  ═════════════════════ */
    /** Clears all per-round goals, counters, locks & cooldown stamps */
    private void resetActionPlanning()
    {
        Microbot.log("-- Resetting action plan & cooldowns");

        targetRootsForThisRun = 0;
        rootsToChopGoal       = 0;
        fletchGoal            = 0;
        feedGoal              = 0;

        rootsChoppedThisRun   = 0;
        fletchedThisRun       = 0;
        fedThisRun            = 0;
        currentBurnableCount  = 0;

        lastWoodcuttingXpDropTime = 0;
        lastFletchingXpDropTime   = 0;
        lastFiremakingXpDropTime  = 0;

        // Reset inventory cycle tracking
        lastInventoryCount = 0;
        knifePreselectThreshold = 6; // Reset to default

        // Reset waiting for round end flag
        waitingForRoundEnd = false;
        
        // Reset brazier priority flag
        shouldPriorizeBrazierAtStart = false;

        setLockState(state, false);      // release any state locks
        resetActions = true;             // force re-evaluation on next loop
    }
    /* ═══════════════════════════════════════════════════════════════ */

    /**
     * Gets the current Wintertodt points from the game interface.
     * 
     * @return current points, or 0 if not available
     */
    private int getWintertodtPoints() {
        try {
            String pointsText = Rs2Widget.getChildWidgetText(396, 6);
            if (pointsText == null || pointsText.isEmpty()) {
                return 0;
            }
            
            // Parse text like "Points<br>25" or just "25"
            String[] parts = pointsText.split("<br>");
            String numberPart = parts.length > 1 ? parts[1] : parts[0];
            
            // Extract just the numbers
            String pointsStr = numberPart.replaceAll("\\D+", "");
            
            if (!pointsStr.isEmpty()) {
                return Integer.parseInt(pointsStr);
            }
            
            return 0;
            
        } catch (Exception e) {
            System.err.println("Error getting Wintertodt points: " + e.getMessage());
            return 0;
        }
    }
    
    /**
     * Gets the startup manager instance.
     * @return startup manager
     */
    public WintertodtStartupManager getStartupManager() {
        return startupManager;
    }
    
    /**
     * Checks if the startup sequence has been completed.
     * @return true if startup completed
     */
    public boolean isStartupCompleted() {
        return startupCompleted;
    }
    
    /**
     * Get the break manager instance
     * @return break manager
     */
    public WintertodtBreakManager getBreakManager() {
        return breakManager;
    }

    /**
     * Public wrapper so the plugin can forward chat messages
     * to the new feeding interrupt handler without accessing it directly.
     */
    public static void onChatMessage(String message)
    {
        handleChatInterruption(message);
    }

    // Add this as a class field near the top with other fields
    private static long lastFletchingAnimationTime = 0;
    private static final long FLETCHING_ANIMATION_TIMEOUT = 3000; // 3 seconds
    
    // Feeding animation tracking
    private static long lastFeedingAnimationTime = 0;
    private static final long FEEDING_ANIMATION_TIMEOUT = 3000; // 3 seconds

    /**
     * Updates fletching animation tracking to detect when fletching stops unexpectedly
     */
    private void updateFletchingAnimationTracking() {
        // Only track animation when we're actively fletching
        if (!fletchingState.isActive()) {
            return;
        }
        
        // Check if player is currently animating
        if (Rs2Player.isAnimating()) {
            lastFletchingAnimationTime = System.currentTimeMillis();
        }
        
        // If we just started fletching, initialize the timer
        if (lastFletchingAnimationTime == 0) {
            lastFletchingAnimationTime = System.currentTimeMillis();
        }
    }

    /**
     * Updates feeding animation tracking to detect when feeding stops unexpectedly
     */
    private void updateFeedingAnimationTracking() {
        // Only track animation when we're actively feeding
        if (!feedingState.isActive()) {
            return;
        }
        
        // Check if player is currently animating
        if (Rs2Player.isAnimating()) {
            lastFeedingAnimationTime = System.currentTimeMillis();
        }
        
        // If we just started feeding, initialize the timer
        if (lastFeedingAnimationTime == 0) {
            lastFeedingAnimationTime = System.currentTimeMillis();
        }
    }

    /**
     * Sets up spam clicking behavior for the upcoming round start.
     * This creates natural-looking clicking behavior before and after the game starts.
     */
    private void setupSpamClickingForRoundStart() {
        try {
            if (nextRoundStartTime <= 0) {
                return;
            }
            
            // Calculate when to start spam clicking (2-6 seconds before round start)
            int preStartDuration = 1000 + random.nextInt(3000); // 1-4 seconds
            spamClickStartTime = nextRoundStartTime - preStartDuration;
            
            // Calculate when to stop spam clicking (1-3 seconds after round start)
            int postStartDuration = 500 + random.nextInt(1500); // 0.5-2 seconds
            spamClickEndTime = nextRoundStartTime + postStartDuration;
            
            // Reset spam click tracking
            spamClickTarget = null;
            spamClicksPerformed = 0;
            lastSpamClick = 0;
            spamClickingActive = false;
            
            Microbot.log("Spam clicking scheduled: " + (preStartDuration / 1000.0) + "s before start, " + 
                        (postStartDuration / 1000.0) + "s after start");
            
        } catch (Exception e) {
            System.err.println("Error setting up spam clicking: " + e.getMessage());
        }
    }

    /**
     * Executes spam clicking behavior during the appropriate time window.
     */
    private void executeSpamClicking(GameState gameState) {
        try {
            long currentTime = System.currentTimeMillis();
            
            // Check if we should be spam clicking
            if (currentTime < spamClickStartTime || currentTime > spamClickEndTime) {
                if (spamClickingActive) {
                    spamClickingActive = false;
                    Microbot.log("Spam clicking finished. Performed " + spamClicksPerformed + " clicks.");
                }
                return;
            }
            
            // Activate spam clicking if not already active
            if (!spamClickingActive) {
                spamClickingActive = true;
                spamClickTarget = determineSpamClickTarget(gameState);
                Microbot.log("Started spam clicking on: " + 
                           (spamClickTarget != null ? spamClickTarget.getId() : "no target"));
            }
            
            // Perform spam clicks with natural timing
            if (spamClickTarget != null && currentTime - lastSpamClick > getNextSpamClickDelay()) {
                performSpamClick();
                lastSpamClick = currentTime;
                spamClicksPerformed++;
            }
            
        } catch (Exception e) {
            System.err.println("Error executing spam clicking: " + e.getMessage());
        }
    }

    /**
     * Determines the best target for spam clicking (usually the brazier we'll interact with).
     */
    private GameObject determineSpamClickTarget(GameState gameState) {
        // Priority 1: Unlit brazier (what we'll light when round starts)
        if (gameState.brazier != null && gameState.burningBrazier == null) {
            return gameState.brazier;
        }
        
        // Priority 2: Broken brazier (if we can fix it)
        if (gameState.brokenBrazier != null && config.fixBrazier()) {
            return gameState.brokenBrazier;
        }
        
        // Priority 3: Any brazier as fallback
        if (gameState.burningBrazier != null) {
            return gameState.burningBrazier;
        }
        
        return gameState.brazier;
    }

    /**
     * Performs a single spam click with natural variation.
     */
    private void performSpamClick() {
        try {
            if (spamClickTarget == null) {
                return;
            }
            
            // Just hover and click without actually interacting
            Rs2GameObject.hoverOverObject(spamClickTarget);
            
            // Small delay between hover and click for realism
            sleepGaussian(20, 10);

            Microbot.log("Spam clicking on: " + spamClickTarget.getId());
            
            // Perform a light click (we don't want to actually interact yet)
            Microbot.getMouse().click();
            
        } catch (Exception e) {
            // Ignore errors during spam clicking to avoid disrupting main logic
        }
    }

    /**
     * Gets the delay until the next spam click with natural variation.
     */
    private int getNextSpamClickDelay() {
        // Base delay: 100-300ms with Gaussian distribution
        int baseDelay = 150;
        int variation = 75;
        int delay = (int) (baseDelay + (random.nextGaussian() * variation));
        
        // Clamp to reasonable range
        return Math.max(50, Math.min(500, delay));
    }

    /**
     * Deposits any non-essential items from the inventory.
     * This is useful for cleaning up after gear swaps.
     */
    private void depositUnnecessaryItems() {
        if (!Rs2Bank.isOpen()) {
            return;
        }

        // Determine the list of essential items to keep
        WintertodtAxeManager.AxeDecision axeDecision = WintertodtAxeManager.determineOptimalAxeSetup();
        List<String> keepItems = new ArrayList<>(Arrays.asList("hammer", "tinderbox", "knife"));

        if (usesPotions) {
            keepItems.add("Rejuvenation potion");
        } else {
            keepItems.add(config.food().getName());
        }

        if (axeDecision.shouldKeepInInventory()) {
            keepItems.add(axeDecision.getAxeName());
        }

        // Deposit everything except the essential items
        Rs2Bank.depositAllExcept(keepItems.toArray(new String[0]));
        sleepGaussian(300, 100);
        Microbot.log("Deposited unnecessary items from inventory.");
    }

    private void handleWalkingToSafeSpotForBreakState(GameState gameState) {
        WorldPoint safeSpot = WintertodtBreakManager.BOSS_ROOM;
        int radius = WintertodtBreakManager.SAFE_SPOT_RADIUS;
        if (Rs2Player.getWorldLocation().distanceTo(safeSpot) > radius) {
            Rs2Walker.walkTo(safeSpot, radius);
            Rs2Player.waitForWalking();
        } else {
            // We have arrived. The break manager will detect this and start the break.
            // We can transition to WAITING state.
            changeState(State.WAITING);
        }
    }

    /**
     * Checks if the script should pause due to break system.
     *
     * @return true if script should pause
     */
    private boolean shouldPauseForBreaks() {
        // Pause if new custom break system is active
        if (breakManager != null && WintertodtBreakManager.isBreakActive()) {
            return true;
        }
        
        // Pause if break handler is active
        if (BreakHandlerScript.isBreakActive()) {
            return true;
        }



        // Pause if action cooldown is active
        if (Rs2AntibanSettings.actionCooldownActive) {
            return true;
        }

        // Pause if universal antiban has paused all scripts
        if (Rs2AntibanSettings.universalAntiban && Microbot.pauseAllScripts) {
            return true;
        }

        return false;
    }
}