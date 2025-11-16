package net.runelite.client.plugins.microbot.util.world;

import lombok.Builder;
import lombok.Getter;

/**
 * Configuration for world hopping behavior in shop operations.
 * Provides configurable limits and exponential backoff for failed attempts.
 */
@Getter
@Builder
public class WorldHoppingConfig {
    
    /**
     * Whether world hopping is enabled
     */
    @Builder.Default
    private final boolean enabled = true;
    
    /**
     * Maximum number of world hops before giving up
     */
    @Builder.Default
    private final int maxWorldHops = 5;
    
    /**
     * Maximum number of failed attempts per world before hopping
     */
    @Builder.Default
    private final int maxAttemptsPerWorld = 1;
    
    /**
     * Use sequential world progression instead of random selection
     */
    @Builder.Default
    private final boolean useSequentialWorlds = false;
    
    /**
     * Base delay in milliseconds before hopping worlds
     */
    @Builder.Default
    private final int baseHopDelay = 2000;
    
    /**
     * Maximum delay in milliseconds before hopping worlds
     */
    @Builder.Default
    private final int maxHopDelay = 10000;
    
    /**
     * Whether to use exponential backoff for hop delays
     */
    @Builder.Default
    private final boolean useExponentialBackoff = true;
    
    /**
     * Backoff multiplier for exponential delay increase
     */
    @Builder.Default
    private final double backoffMultiplier = 1.5;
    
    /**
     * Calculates the delay for a specific hop attempt using exponential backoff
     */
    public int getHopDelay(int attemptNumber) {
        if (!useExponentialBackoff) {
            return baseHopDelay;
        }
        
        double delay = baseHopDelay * Math.pow(backoffMultiplier, attemptNumber - 1);
        return Math.min((int) delay, maxHopDelay);
    }
    

    /**
     * Creates default configuration for shop operations
     */
    public static WorldHoppingConfig createDefault() {
        return WorldHoppingConfig.builder().build();
    }
    
    /**
     * Creates aggressive configuration for high-frequency trading
     */
    public static WorldHoppingConfig createAggressive() {
        return WorldHoppingConfig.builder()
                .maxWorldHops(20)
                .maxAttemptsPerWorld(2)
                .baseHopDelay(1000)
                .maxHopDelay(5000)
                .backoffMultiplier(1.2)
                .build();
    }
    
    /**
     * Creates conservative configuration for slow/careful operations
     */
    public static WorldHoppingConfig createConservative() {
        return WorldHoppingConfig.builder()
                .maxWorldHops(5)
                .maxAttemptsPerWorld(1)
                .baseHopDelay(5000)
                .maxHopDelay(15000)
                .backoffMultiplier(2.0)
                .build();
    }
    
    @Override
    public String toString() {
        return String.format("WorldHoppingConfig{enabled=%s, maxHops=%d, maxAttemptsPerWorld=%d, " +
                           "baseDelay=%dms, maxDelay=%dms, exponentialBackoff=%s}", 
                           enabled, maxWorldHops, maxAttemptsPerWorld, 
                           baseHopDelay, maxHopDelay, useExponentialBackoff);
    }
}
