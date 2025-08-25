package net.runelite.client.plugins.microbot.agility.courses;

import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Encapsulates state tracking for the Agility Pyramid course.
 * Centralizes all state management to avoid scattered static variables.
 */
public class PyramidState {
    
    // Timing and cooldown tracking
    private volatile long lastObstacleStartTime = 0;
    private volatile long lastClimbingRocksTime = 0;
    
    // State flags - using AtomicBoolean for thread safety
    private final AtomicBoolean currentlyDoingCrossGap = new AtomicBoolean(false);
    private final AtomicBoolean currentlyDoingXpObstacle = new AtomicBoolean(false);
    private final AtomicBoolean handlingPyramidTurnIn = new AtomicBoolean(false);
    
    // Random turn-in threshold (4-6 pyramids)
    private volatile int pyramidTurnInThreshold = generateNewThreshold();
    
    // Cooldown constants (in nanoseconds for precise timing)
    private static final long OBSTACLE_COOLDOWN = TimeUnit.MILLISECONDS.toNanos(1500); // 1.5 seconds between obstacles
    private static final long CLIMBING_ROCKS_COOLDOWN = TimeUnit.MILLISECONDS.toNanos(30000); // 30 seconds - pyramid respawn time
    
    /**
     * Records that an obstacle was just started
     */
    public void recordObstacleStart() {
        lastObstacleStartTime = System.nanoTime();
    }
    
    /**
     * Checks if enough time has passed since last obstacle
     */
    public boolean isObstacleCooldownActive() {
        return System.nanoTime() - lastObstacleStartTime < OBSTACLE_COOLDOWN;
    }
    
    /**
     * Records that climbing rocks were clicked and generates new random threshold
     */
    public void recordClimbingRocks() {
        lastClimbingRocksTime = System.nanoTime();
        // Generate a new random threshold for the next pyramid run
        pyramidTurnInThreshold = generateNewThreshold();
    }
    
    /**
     * Checks if climbing rocks are on cooldown
     */
    public boolean isClimbingRocksCooldownActive() {
        return System.nanoTime() - lastClimbingRocksTime < CLIMBING_ROCKS_COOLDOWN;
    }
    
    /**
     * Sets the Cross Gap flag (for long-animation gap obstacles)
     */
    public void startCrossGap() {
        currentlyDoingCrossGap.set(true);
    }
    
    /**
     * Clears the Cross Gap flag
     */
    public void clearCrossGap() {
        currentlyDoingCrossGap.set(false);
    }
    
    /**
     * Checks if currently doing a Cross Gap obstacle
     */
    public boolean isDoingCrossGap() {
        return currentlyDoingCrossGap.get();
    }
    
    /**
     * Sets the XP obstacle flag
     */
    public void startXpObstacle() {
        currentlyDoingXpObstacle.set(true);
    }
    
    /**
     * Clears the XP obstacle flag
     */
    public void clearXpObstacle() {
        currentlyDoingXpObstacle.set(false);
    }
    
    /**
     * Checks if currently doing an XP-granting obstacle
     */
    public boolean isDoingXpObstacle() {
        return currentlyDoingXpObstacle.get();
    }
    
    /**
     * Sets the pyramid turn-in flag
     */
    public void startPyramidTurnIn() {
        handlingPyramidTurnIn.set(true);
    }
    
    /**
     * Clears the pyramid turn-in flag
     */
    public void clearPyramidTurnIn() {
        handlingPyramidTurnIn.set(false);
        // Threshold is regenerated when grabbing the pyramid top (recordClimbingRocks), not after turn-in
    }
    
    /**
     * Checks if currently handling pyramid turn-in
     */
    public boolean isHandlingPyramidTurnIn() {
        return handlingPyramidTurnIn.get();
    }
    
    /**
     * Gets the current pyramid turn-in threshold
     */
    public int getPyramidTurnInThreshold() {
        return pyramidTurnInThreshold;
    }
    
    /**
     * Package-private setter for unit testing purposes to avoid randomness in tests
     */
    void setPyramidTurnInThresholdForTesting(int value) {
        this.pyramidTurnInThreshold = value;
    }
    
    /**
     * Generates a new random threshold between 4 and 6 (inclusive)
     */
    private int generateNewThreshold() {
        return Rs2Random.betweenInclusive(4, 6);
    }
    
    /**
     * Resets all state flags (useful for plugin restart)
     */
    public void reset() {
        lastObstacleStartTime = 0;
        lastClimbingRocksTime = 0;
        currentlyDoingCrossGap.set(false);
        currentlyDoingXpObstacle.set(false);
        handlingPyramidTurnIn.set(false);
        pyramidTurnInThreshold = generateNewThreshold();
    }
}