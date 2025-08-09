package net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.collection;

import lombok.Getter;
import lombok.EqualsAndHashCode;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;

import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.Priority;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementType;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.ScheduleContext;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.Requirement;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.grounditem.models.Rs2SpawnLocation;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;

import org.slf4j.event.Level;

/**
 * Represents an item requirement that can be fulfilled by looting an item from a spawn location.
 * Extends the Requirement class directly to add loot-specific functionality.
 */
@Getter
@EqualsAndHashCode(callSuper = true)
public class LootRequirement extends Requirement {

    /**
     * Default item count for this requirement.
     * This can be overridden if the plugin requires a specific count.
     */
    private final Map<Integer,Integer> amounts;
    private Map<Integer,Integer> collectedAmounts; 
    
    /**
     * The spawn locations for this item requirement.
     */
    private final Rs2SpawnLocation spawnLocation;
    
    
    /**
     * Maximum time to wait for collecting items in milliseconds.
     */
    private final Duration timeout ;
    /**
     * define how far apart spawn locations can be to be considered part of the same cluster.
     */
    private final int clusterProximity; 
  

       /**
     * Represents a cluster of nearby spawn locations.
     */
    private static class SpawnCluster {
        List<WorldPoint> locations;
        WorldPoint center;
        double averageDistance;
        boolean reachable;
        
        SpawnCluster(List<WorldPoint> locations) {
            this.locations = locations;
            this.center = calculateCenter(locations);
            this.averageDistance = calculateAverageDistance();
            this.reachable = false;
        }
        
        private WorldPoint calculateCenter(List<WorldPoint> points) {
            int sumX = points.stream().mapToInt(WorldPoint::getX).sum();
            int sumY = points.stream().mapToInt(WorldPoint::getY).sum();
            int sumPlane = points.stream().mapToInt(WorldPoint::getPlane).sum();
            
            return new WorldPoint(
                sumX / points.size(),
                sumY / points.size(),
                sumPlane / points.size()
            );
        }
        
        private double calculateAverageDistance() {
            WorldPoint playerLocation = Rs2Player.getWorldLocation();
            return locations.stream()
                .mapToInt(location -> location.distanceTo(playerLocation))
                .average()
                .orElse(Double.MAX_VALUE);
        }
    }
    public String getName() {
        // Use the first item ID as the name, or "Unknown Item" if no IDs are provided
        return ids.isEmpty() ? "Unknown Item" : spawnLocation.getItemName();
    }
    
    /**
     * Returns a multi-line display string with detailed loot requirement information.
     * Uses StringBuilder with tabs for proper formatting.
     * 
     * @return A formatted string containing loot requirement details
     */
    public String displayString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Loot Requirement Details ===\n");
        sb.append("Name:\t\t\t").append(getName()).append("\n");
        sb.append("Type:\t\t\t").append(getRequirementType().name()).append("\n");
        sb.append("Priority:\t\t").append(getPriority().name()).append("\n");
        sb.append("Rating:\t\t\t").append(getRating()).append("/10\n");
        sb.append("Amounts per id:\t\t\t").append(amounts).append("\n");
        sb.append("Amounts Collected per id:\t").append(collectedAmounts).append("\n");
        sb.append("Item IDs:\t\t").append(getIds().toString()).append("\n");
        sb.append("clusterProximity:\t").append(clusterProximity).append(" tiles\n");
        sb.append("Timeout:\t\t").append(timeout.toSeconds()).append(" seconds\n");
        sb.append("Description:\t\t").append(getDescription() != null ? getDescription() : "No description").append("\n");
        
        if (spawnLocation != null) {
            sb.append("\n--- Spawn Location Information ---\n");
            sb.append(spawnLocation.displayString());
            
            // Add availability check
            sb.append("Currently Available:\t").append(isSpawnAvailable() ? "Yes" : "No").append("\n");
        } else {
            sb.append("Spawn Location:\t\tNot specified\n");
        }
        
        return sb.toString();
    }

    /**
     * Full constructor for a loot item requirement with schedule context.
     */
    public LootRequirement(
            List<Integer> itemIds,
            int amount,
            Priority priority,
            int rating,
            String description,
            ScheduleContext scheduleContext,
            Rs2SpawnLocation spawnLocation,
            int clusterProximity,
            Duration timeout) {
        
        super(RequirementType.INVENTORY, priority, rating, description, 
              itemIds, scheduleContext);
        
        // Create amounts map with the same amount for all item IDs
        Map<Integer, Integer> amountsBuilder = new HashMap<>();
        for (Integer itemId : itemIds) {
            amountsBuilder.put(itemId, amount);
            collectedAmounts.put(itemId, 0); // Initialize collected amounts to 0
        }
        this.amounts = Map.copyOf(amountsBuilder);
        this.spawnLocation = spawnLocation;
        this.clusterProximity = clusterProximity;
        this.timeout = timeout;
    }

    /**
     * Full constructor for a loot item requirement.
     * Defaults to ScheduleContext.BOTH for backwards compatibility.
     */
    public LootRequirement(
            List<Integer> itemIds,
            int amount,
            Priority priority,
            int rating,
            String description,
            Rs2SpawnLocation spawnLocation,
            int clusterProximity,
            Duration timeout) {
        
        super(RequirementType.INVENTORY, priority, rating, description, 
              itemIds, ScheduleContext.BOTH); // Default to BOTH for backwards compatibility
        
        // Create amounts map with the same amount for all item IDs
        Map<Integer, Integer> amountsBuilder = new HashMap<>();
        for (Integer itemId : itemIds) {
            amountsBuilder.put(itemId, amount);
            collectedAmounts.put(itemId, 0); // Initialize collected amounts to 0
        }
        this.amounts = Map.copyOf(amountsBuilder);
        this.spawnLocation = spawnLocation;
        this.clusterProximity = clusterProximity;
        this.timeout = timeout;
    }
    
    /**
     * Simple constructor for a loot item requirement with mandatory priority.
     */
    public LootRequirement(
            int itemId,
            int amount,
            String description,
            Rs2SpawnLocation spawnLocation) {
        this(
            Arrays.asList(itemId),
            amount,
            Priority.MANDATORY,
            5,
            description,
            spawnLocation,
            20, // Default cluster proximity of 20 tiles
            Duration.of(2, java.time.temporal.ChronoUnit.MINUTES) // Default timeout of 2 minutes
        );
        
    }

   
    
    /**
     * Gets the primary item ID (first in the list).
     * Used for backward compatibility with code that expects a single item ID.
     * 
     * @return The primary item ID, or -1 if there are no item IDs
     */
    public int getPrimaryItemId() {
        return ids.isEmpty() ? -1 : ids.get(0);
    }
    
    /**
     * Check if this requirement accepts a specific item ID.
     * 
     * @param itemId The item ID to check
     * @return true if this requirement can be fulfilled by the specified item ID, false otherwise
     */
    public boolean acceptsItemId(int itemId) {
        return ids.contains(itemId);
    }
    
    /**
     * Gets the list of item IDs for this requirement.
     * Provided for backward compatibility.
     * 
     * @return The list of item IDs
     */
    public List<Integer> getItemIds() {
        return ids;
    }
    
 
   
    /**
     * Main loot collection method with configurable cluster proximity.
     * Finds the best reachable cluster of spawn locations and efficiently collects items.
     * 
     * @param clusterProximity The maximum distance between spawn locations to be considered part of the same cluster
     * @return true if the required amount was successfully collected, false otherwise
     */
    private boolean collectLootItems(CompletableFuture<Boolean> scheduledFuture) {

       
        if (isFulfilled()) {            
            return true;
        }
        
        if (spawnLocation == null || spawnLocation.getLocations() == null || spawnLocation.getLocations().isEmpty()) {
            Microbot.status = "No spawn locations for " + getName();
            return false;
        }
        
        try {
            // Find the best reachable cluster
            SpawnCluster bestCluster = findBestReachableCluster(clusterProximity);
            if (bestCluster == null) {
                Microbot.status = "No reachable spawn clusters found for " + getName();
                return false;
            }
            
            Microbot.status = "Found cluster with " + bestCluster.locations.size() + " spawn locations for " + getName();
            
            // Move to the cluster center
            if (!moveToCluster(bestCluster)) {
                Microbot.status = "Failed to reach cluster for " + getName();
                return false;
            }
            
            // Collect items from the cluster
            return collectFromCluster(scheduledFuture, bestCluster);
            
        } catch (Exception e) {
            Microbot.logStackTrace("LootItemRequirement.collectLootItems", e);
            return false;
        }
    }
    
  
    
    /**
     * Finds the best reachable cluster of spawn locations.
     */
    private SpawnCluster findBestReachableCluster(int clusterProximity) {
        List<WorldPoint> allLocations = spawnLocation.getLocations();
        List<SpawnCluster> clusters = new ArrayList<>();
        Set<WorldPoint> processedLocations = new HashSet<>();
        
        // Create clusters by grouping nearby locations
        for (WorldPoint location : allLocations) {
            if (processedLocations.contains(location)) continue;
            
            List<WorldPoint> clusterLocations = new ArrayList<>();
            clusterLocations.add(location);
            processedLocations.add(location);
            
            // Find all locations within cluster proximity
            for (WorldPoint otherLocation : allLocations) {
                if (processedLocations.contains(otherLocation)) continue;
                
                boolean isNearCluster = clusterLocations.stream()
                    .anyMatch(clusterLoc -> clusterLoc.distanceTo(otherLocation) <= clusterProximity);
                
                if (isNearCluster) {
                    clusterLocations.add(otherLocation);
                    processedLocations.add(otherLocation);
                }
            }
            
            clusters.add(new SpawnCluster(clusterLocations));
        }
        
        // Check reachability and find the best cluster
        SpawnCluster bestCluster = null;
        double bestScore = Double.MAX_VALUE;
        
        for (SpawnCluster cluster : clusters) {
            // Check if the cluster center is reachable
            cluster.reachable = Rs2Walker.canReach(cluster.center);
            
            if (cluster.reachable) {
                // Score based on distance and cluster size (prefer closer clusters with more spawns)
                double score = cluster.averageDistance / Math.max(1, cluster.locations.size());
                
                if (score < bestScore) {
                    bestScore = score;
                    bestCluster = cluster;
                }
            }
        }
        
        return bestCluster;
    }
    
    /**
     * Moves the player to the cluster center.
     */
    private boolean moveToCluster(SpawnCluster cluster) {
        WorldPoint currentPosition = Rs2Player.getWorldLocation();
        
        // Check if we're already near the cluster
        boolean nearCluster = cluster.locations.stream()
            .anyMatch(location -> currentPosition.distanceTo(location) <= 15);
        
        if (nearCluster) {
            return true;
        }
        
        // Walk to the cluster center
        Microbot.status = "Moving to loot cluster for " + getName();
        if (!Rs2Walker.walkTo(cluster.center)) {
            return false;
        }
        
        // Wait for arrival
        return sleepUntil(() -> {
            WorldPoint playerLoc = Rs2Player.getWorldLocation();
            return cluster.locations.stream()
                .anyMatch(location -> playerLoc.distanceTo(location) <= 15);
        }, 30000);
    }
    
    /**
     * Collects items from the cluster with proper banking and respawn handling.
     */
    private boolean collectFromCluster(CompletableFuture<Boolean> scheduledFuture,SpawnCluster cluster) {
        long startTime = System.currentTimeMillis();              
        long lastItemFoundTime = System.currentTimeMillis();
                
        while (!isFulfilled() && 
               (System.currentTimeMillis() - startTime) < timeout.toMillis()) {            
            if (scheduledFuture != null && scheduledFuture.isCancelled() || scheduledFuture.isDone()) {
                Microbot.log("Loot collection cancelled or completed prematurely: " + getName());
                return false; // Stop if the scheduled future is cancelled or done
            }
            // Check inventory space and bank if needed
            if (Rs2Inventory.isFull() && !Rs2Inventory.contains(getItemIds().get(0))) {
                if (!handleBanking(cluster.center)) {
                    Microbot.status = "Banking failed while collecting " + getName();
                    return false;
                }
                startTime = System.currentTimeMillis(); // Reset start time after banking

                continue;
            }
            
            // Try to loot items in the cluster area
            boolean itemFound = false;
            for (int itemId : getItemIds()) {
                int requiredAmount = amounts.get(itemId);
                int collectedAmout = collectedAmounts.get(itemId);
                  Microbot.status = "Collecting " + itemId + " from cluster (" + collectedAmout + "/" + amounts.get(itemId) + ")";    
                 // Check if we have enough
                if (collectedAmout >= requiredAmount) {
                    Microbot.status = "Successfully collected " + requiredAmount + "x " + getName();
                    return true;
                }
            
                // Check for items within the cluster area
                if (Rs2GroundItem.exists(itemId, 25)) {
                    if (Rs2GroundItem.loot(itemId, 25)) {
                        itemFound = true;
                        lastItemFoundTime = System.currentTimeMillis();
                        
                        // Wait for inventory update
                        int finalCurrentCount = collectedAmout;
                        sleepUntil(() -> Rs2Inventory.itemQuantity(itemId)> finalCurrentCount, 3000);
                        
                        collectedAmout += Rs2Inventory.itemQuantity(itemId)-  finalCurrentCount; // Increment count after successful loot
                        Microbot.status = "Collecting " + getName() + " (" + collectedAmout + "/" + requiredAmount + ")";
                        collectedAmounts.put(itemId, collectedAmout);
                        break;
                    }
                }
            }
            
            if (itemFound) {
                lastItemFoundTime = System.currentTimeMillis(); // Reset last found time
            } else {
                // No items found, wait for respawn
                long timeSinceLastItem = System.currentTimeMillis() - lastItemFoundTime;
                Duration itemRespwanTime = spawnLocation.getRespawnTime() != null ? spawnLocation.getRespawnTime() : Duration.ofSeconds(30);
                // If we haven't found any items for too long, the cluster might be depleted
                if (timeSinceLastItem > 30000) { // 30 seconds
                    Microbot.status = "No items found in cluster for 30s, checking other areas...";
                    
                    // Try checking a bit further from cluster center
                    boolean foundNearby = false;
                    for (WorldPoint location : cluster.locations) {
                        if (Rs2Player.getWorldLocation().distanceTo(location) <= 30) {
                            for (int itemId : getItemIds()) {
                                if (Rs2GroundItem.exists(itemId, 5)) {
                                    Rs2Walker.walkTo(location);
                                    sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(location) <= 10, 15000);
                                    foundNearby = true;
                                    break;
                                }
                            }
                            if (foundNearby) break;
                        }
                    }
                    
                    if (!foundNearby) {
                        Microbot.status = "Cluster appears depleted, waiting for respawn...";
                    }
                                        
                }
                
                Microbot.status = "Waiting for " + getName() + " to respawn in cluster...";
                sleep(Rs2Random.between((int)(timeSinceLastItem -itemRespwanTime.toMillis()) , (int)itemRespwanTime.toMillis()));
            }
        }
        
        // Final check
        return isFulfilled();
    }
    
    /**
     * Handles banking when inventory is full.
     */
    private boolean handleBanking(WorldPoint returnLocation) {      
        
        try {          
            final WorldPoint localReturnLocation = returnLocation != null ? returnLocation : Rs2Player.getWorldLocation();  
        

            // Find and use nearest bank
            if (!Rs2Bank.walkToBankAndUseBank()) {
                return false;
            }
                                    
            //Rs2Bank.depositAllExcept();// transportation related items...  we need to impplement these in the future
            Rs2Bank.depositAll((item)->getIds().contains(item.getId()) );// transportation related items...  we need to impplement these in the future
            sleepUntil(() -> getItemIds().stream().allMatch(id -> !Rs2Inventory.hasItem(id) ), 5000); // Wait until all items are deposited
            // use the rs2transport or nviation or we have called it to get  transportation items to the spwan location ..  sowe we dont deposit it.            
            Rs2Bank.closeBank();
            sleepUntil( () -> !Rs2Bank.isOpen(), 5000); // Wait until bank is closed   
            // Return to collection area
            Rs2Walker.walkTo(localReturnLocation);
            return sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(localReturnLocation) <= 10, 30000);
            
        } catch (Exception e) {
            Microbot.logStackTrace("LootItemRequirement.handleBanking", e);
            return false;
        }
    }
    
   
    
    /**
     * Checks if this item is currently available to loot.
     * 
     * @return true if the item is available to loot, false otherwise
     */
    private boolean isSpawnAvailable() {
        if (spawnLocation == null || spawnLocation.getLocations() == null || spawnLocation.getLocations().isEmpty()) {
            return false;
        }
        
        // Check if we're near any spawn location
        WorldPoint currentPosition = Rs2Player.getWorldLocation();
        for (WorldPoint location : spawnLocation.getLocations()) {
            if (location.distanceTo(currentPosition) <= 20) {
                // Check if any of our target items are available to loot
                for (int itemId : getItemIds()) {
                    if (Rs2GroundItem.exists(itemId, 15)) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }

    /**
     * Implements the abstract fulfillRequirement method from the base Requirement class.
     * Attempts to fulfill this loot requirement by collecting items from spawn locations.
     * 
     * @param executorService The ScheduledExecutorService on which fulfillment is running
     * @return true if the requirement was successfully fulfilled, false otherwise
     */
    @Override
    public boolean fulfillRequirement(CompletableFuture<Boolean> scheduledFuture) {
        if (Microbot.getClient().isClientThread()) {
            Microbot.log("Please run fulfillRequirement() on a non-client thread.", Level.ERROR);
            return false;
        }
        try {
            // Check if the requirement is already fulfilled
            if (isFulfilled()) {
                return true;
            }
                       
            // Attempt to collect the required items
            boolean success = collectLootItems(scheduledFuture);
            
            if (!success && isMandatory()) {
                Microbot.log("MANDATORY loot requirement failed: " + getName());
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            Microbot.log("Error fulfilling loot requirement " + getName() + ": " + e.getMessage());
            return !isMandatory(); // Don't fail mandatory requirements due to exceptions
        }
    }
    
    /**
     * Checks if we already have the required amount of items.
     * 
     * @return true if we have enough items, false otherwise
     */
    public boolean isFulfilled() {
        boolean hasRequiredAmount = collectedAmounts.entrySet().stream()
            .allMatch(entry -> entry.getValue() >= amounts.getOrDefault(entry.getKey(), 0));
        if (hasRequiredAmount) {
            Microbot.status = "Already have all required items for " + getName();
            return true;
        }
        return false;
    }


}
