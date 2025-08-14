package net.runelite.client.plugins.microbot.bga.autofishing.managers;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.bga.autofishing.enums.Fish;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.depositbox.Rs2DepositBox;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import javax.inject.Singleton;
import java.util.Arrays;
import java.util.Objects;

/**
 * Manages location finding, pathfinding, and travel for fishing operations
 */
@Singleton
public class LocationManager {
    
    private WorldPoint fishingLocation;
    private WorldPoint originalLocation;
    
    /**
     * Initialize with player's current location as fishing spot
     */
    public void initializeFishingLocation() {
        if (originalLocation == null) {
            originalLocation = Rs2Player.getWorldLocation();
            fishingLocation = originalLocation;
            Microbot.log("Fishing location set to: " + fishingLocation);
        }
    }
    
    /**
     * Finds optimal fishing spot for the given fish type
     */
    public WorldPoint findOptimalFishingSpot(Fish fish) {
        try {
            Rs2NpcModel fishingSpot = getFishingSpot(fish);
            if (fishingSpot != null) {
                return fishingSpot.getWorldLocation();
            }
            
            // Fallback to original location if no spot found
            return originalLocation != null ? originalLocation : Rs2Player.getWorldLocation();
        } catch (Exception e) {
            Microbot.log("Error finding fishing spot: " + e.getMessage());
            return Rs2Player.getWorldLocation();
        }
    }
    
    /**
     * Walks to specified location with validation
     */
    public boolean walkToLocation(WorldPoint target) {
        if (target == null) {
            Microbot.log("Cannot walk to null location");
            return false;
        }
        
        try {
            if (isAtLocation(target, 3)) {
                return true; // Already at location
            }
            
            Microbot.log("Walking to location: " + target);
            return Rs2Walker.walkTo(target);
        } catch (Exception e) {
            Microbot.log("Error walking to location: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Checks if player is at fishing location
     */
    public boolean isAtFishingLocation() {
        if (fishingLocation == null) {
            return false;
        }
        return isAtLocation(fishingLocation, 5);
    }
    
    /**
     * Finds nearest deposit box location
     */
    public WorldPoint findNearestDepositBox() {
        try {
            return Rs2DepositBox.walkToAndUseDepositBox() ? Rs2Player.getWorldLocation() : null;
        } catch (Exception e) {
            Microbot.log("Error finding deposit box: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Checks if deposit box is nearby (within reasonable distance)
     */
    public boolean isDepositBoxNearby() {
        try {
            // This is a heuristic check - if deposit box utility thinks it can walk to one, it's nearby
            return Rs2DepositBox.walkToAndUseDepositBox();
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Finds nearest bank location
     */
    public BankLocation findNearestBank() {
        try {
            return Rs2Bank.getNearestBank();
        } catch (Exception e) {
            Microbot.log("Error finding nearest bank: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Walks to nearest bank and opens it
     */
    public boolean walkToBankAndOpen() {
        try {
            BankLocation nearestBank = findNearestBank();
            if (nearestBank == null) {
                Microbot.log("No bank location found");
                return false;
            }
            
            Microbot.log("Walking to bank: " + nearestBank);
            
            // Use Rs2Bank utility for walking and opening
            if (Rs2Bank.isNearBank(nearestBank, 8)) {
                return Rs2Bank.openBank();
            } else {
                return Rs2Bank.walkToBankAndUseBank(nearestBank);
            }
        } catch (Exception e) {
            Microbot.log("Error walking to bank: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Walks to deposit box and opens it
     */
    public boolean walkToDepositBoxAndOpen() {
        try {
            Microbot.log("Walking to deposit box");
            return Rs2DepositBox.walkToAndUseDepositBox();
        } catch (Exception e) {
            Microbot.log("Error walking to deposit box: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Returns to the original fishing location
     */
    public boolean returnToFishingLocation() {
        if (fishingLocation == null) {
            Microbot.log("No fishing location set to return to");
            return false;
        }
        
        return walkToLocation(fishingLocation);
    }
    
    /**
     * Updates the fishing location to current position
     */
    public void updateFishingLocation() {
        fishingLocation = Rs2Player.getWorldLocation();
        Microbot.log("Updated fishing location to: " + fishingLocation);
    }
    
    /**
     * Gets the current fishing location
     */
    public WorldPoint getFishingLocation() {
        return fishingLocation;
    }
    
    /**
     * Sets a specific fishing location
     */
    public void setFishingLocation(WorldPoint location) {
        this.fishingLocation = location;
        if (originalLocation == null) {
            originalLocation = location;
        }
        Microbot.log("Fishing location set to: " + location);
    }
    
    /**
     * Checks if player is at specified location within distance
     */
    private boolean isAtLocation(WorldPoint target, int distance) {
        if (target == null) {
            return false;
        }
        
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null) {
            return false;
        }
        
        return playerLocation.distanceTo(target) <= distance;
    }
    
    /**
     * Gets fishing spot NPC for the given fish type
     */
    private Rs2NpcModel getFishingSpot(Fish fish) {
        try {
            return Arrays.stream(fish.getFishingSpot())
                    .mapToObj(Rs2Npc::getNpc)
                    .filter(Objects::nonNull)
                    .min((spot1, spot2) -> {
                        // Prefer closer spots
                        WorldPoint playerLoc = Rs2Player.getWorldLocation();
                        if (playerLoc == null) return 0;
                        
                        double dist1 = spot1.getWorldLocation().distanceTo(playerLoc);
                        double dist2 = spot2.getWorldLocation().distanceTo(playerLoc);
                        return Double.compare(dist1, dist2);
                    })
                    .orElse(null);
        } catch (Exception e) {
            Microbot.log("Error getting fishing spot: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Validates that the player can reach the fishing location
     */
    public boolean canReachFishingLocation() {
        if (fishingLocation == null) {
            return false;
        }
        
        // Simple distance check - if too far, might not be reachable
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null) {
            return false;
        }
        
        // If more than 100 tiles away, might be unreachable (different areas)
        return playerLocation.distanceTo(fishingLocation) <= 100;
    }
    
    /**
     * Calculates travel time estimate to location
     */
    public long estimateTravelTime(WorldPoint target) {
        if (target == null) {
            return 0;
        }
        
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null) {
            return 0;
        }
        
        // Rough estimate: 1 tile per second + overhead
        int distance = playerLocation.distanceTo(target);
        return (distance * 1000) + 5000; // 5 second overhead
    }
}