package net.runelite.client.plugins.microbot.util.grounditem;

import lombok.Data;
import lombok.Getter;
import net.runelite.api.ItemComposition;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;

import java.time.Duration;
import java.time.Instant;

/**
 * Enhanced model for ground items with caching, tick tracking, and despawn utilities.
 * Provides a comprehensive replacement for the deprecated RS2Item class.
 * Includes looting utility methods and value-based filtering for automation.
 * 
 * @author Vox
 * @version 2.0
 */
@Data
@Getter
public class Rs2GroundItemModel {
    
    private final TileItem tileItem;
    private final Tile tile;
    private final ItemComposition itemComposition;
    private final WorldPoint location;
    private final int id;
    private final int quantity;
    private final String name;
    private final boolean isOwned;
    private final boolean isLootAble;
    private final long creationTime;
    private final int creationTick;
    
    // Despawn tracking fields
    private final Duration despawnTime;
    private final Instant spawnTime;
    
    /**
     * Creates a new Rs2GroundItemModel from a TileItem and Tile.
     * 
     * @param tileItem The TileItem from the game
     * @param tile The tile the item is on
     */
    public Rs2GroundItemModel(TileItem tileItem, Tile tile) {
        this.tileItem = tileItem;
        this.tile = tile;
        this.location = tile.getWorldLocation();
        this.id = tileItem.getId();
        this.quantity = tileItem.getQuantity();
        this.isOwned = tileItem.getOwnership() == TileItem.OWNERSHIP_SELF;
        this.isLootAble = !(tileItem.getOwnership() == TileItem.OWNERSHIP_OTHER);
        this.creationTime = System.currentTimeMillis();
        this.creationTick = Microbot.getClientThread().runOnClientThreadOptional(
            () -> Microbot.getClient().getTickCount()
        ).orElse(0);
        
        // Initialize despawn tracking
        this.spawnTime = Instant.now();
        this.despawnTime = Duration.ofSeconds(tileItem.getDespawnTime());
        
        // Load item composition on client thread
        this.itemComposition = Microbot.getClientThread().runOnClientThreadOptional(
            () -> Microbot.getClient().getItemDefinition(id)
        ).orElse(null);
        
        // Get name from composition or use default
        this.name = (itemComposition != null) ? itemComposition.getName() : "Unknown Item";
    }
    
    // ============================================
    // Time and Tick Tracking Methods
    // ============================================
    
    /**
     * Gets the number of ticks since this item was created.
     * 
     * @return The number of ticks since creation
     */
    public int getTicksSinceCreation() {
        return Microbot.getClientThread().runOnClientThreadOptional(
            () -> Microbot.getClient().getTickCount() - creationTick
        ).orElse(0);
    }
    
    /**
     * Gets the number of ticks since this item spawned (alias for getTicksSinceCreation).
     * 
     * @return The number of ticks since spawn
     */
    public int getTicksSinceSpawn() {
        return getTicksSinceCreation();
    }
    
    /**
     * Gets the time in milliseconds since this item was created.
     * 
     * @return Milliseconds since creation
     */
    public long getTimeSinceCreation() {
        return System.currentTimeMillis() - creationTime;
    }
    
    // ============================================
    // Despawn Tracking Methods
    // ============================================
    
    /**
     * Gets the absolute time when this item will despawn.
     * 
     * @return Instant when the item despawns
     */
    public Instant getDespawnTime() {
        return spawnTime.plus(despawnTime);
    }
    
    /**
     * Gets the duration remaining until this item despawns.
     * 
     * @return Duration until despawn, or Duration.ZERO if already despawned
     */
    public Duration getTimeUntilDespawn() {
        Instant despawnTime = getDespawnTime();
        Instant now = Instant.now();
        
        if (now.isAfter(despawnTime)) {
            return Duration.ZERO;
        }
        
        return Duration.between(now, despawnTime);
    }
    
    /**
     * Gets the number of ticks remaining until this item despawns.
     * 
     * @return Ticks until despawn, or 0 if already despawned
     */
    public int getTicksUntilDespawn() {
        Duration timeLeft = getTimeUntilDespawn();
        return (int) (timeLeft.toMillis() / 600); // 600ms per tick
    }
    
    /**
     * Gets the number of seconds remaining until this item despawns.
     * 
     * @return Seconds until despawn, or 0 if already despawned
     */
    public long getSecondsUntilDespawn() {
        return getTimeUntilDespawn().getSeconds();
    }
    
    /**
     * Checks if this item has despawned based on time.
     * 
     * @return true if the item should have despawned
     */
    public boolean isDespawned() {
        return Instant.now().isAfter(getDespawnTime());
    }
    
    /**
     * Checks if this item will despawn within the specified number of seconds.
     * 
     * @param seconds The time threshold in seconds
     * @return true if the item will despawn within the given time
     */
    public boolean willDespawnWithin(long seconds) {
        return getSecondsUntilDespawn() <= seconds;
    }
    
    /**
     * Checks if this item will despawn within the specified number of ticks.
     * 
     * @param ticks The time threshold in ticks
     * @return true if the item will despawn within the given ticks
     */
    public boolean willDespawnWithinTicks(int ticks) {
        return getTicksUntilDespawn() <= ticks;
    }
    
    // ============================================
    // Item Property Methods
    // ============================================
    
    /**
     * Checks if this item is stackable.
     * 
     * @return true if stackable, false otherwise
     */
    public boolean isStackable() {
        return itemComposition != null && itemComposition.isStackable();
    }
    
    /**
     * Checks if this item is noted.
     * 
     * @return true if noted, false otherwise
     */
    public boolean isNoted() {
        return itemComposition != null && itemComposition.getNote() != -1;
    }
    
    /**
     * Gets the item's store value.
     * 
     * @return The item's store value
     */
    public int getValue() {
        return itemComposition != null ? itemComposition.getPrice() : 0;
    }
    
    /**
     * Gets the item's Grand Exchange price.
     * 
     * @return The item's GE price
     */
    public int getPrice() {
        return Rs2GrandExchange.getPrice(this.id);
    }
    
    /**
     * Gets the total value of this item stack (quantity * unit value).
     * 
     * @return The total stack value
     */
    public int getTotalValue() {
        return getValue() * quantity;
    }
    
    /**
     * Gets the total Grand Exchange value of this item stack.
     * 
     * @return The total stack GE value
     */
    public int getTotalGeValue() {
        return getPrice() * quantity;
    }
    
    /**
     * Gets the item's high alchemy value.
     * 
     * @return The high alchemy value
     */
    public int getHaPrice() {
        return itemComposition != null ? itemComposition.getHaPrice() : 0;
    }
    
    /**
     * Gets the total high alchemy value of this item stack.
     * 
     * @return The total stack high alchemy value
     */
    public int getTotalHaValue() {
        return getHaPrice() * quantity;
    }
    
    /**
     * Gets the item's low alchemy value.
     * This is calculated as 40% of the store price.
     * 
     * @return The low alchemy value
     */
    public int getLaValue() {
        return itemComposition != null ? (int)(itemComposition.getPrice() * 0.4) : 0;
    }
    
    /**
     * Gets the total low alchemy value of this item stack.
     * 
     * @return The total stack low alchemy value
     */
    public int getTotalLaValue() {
        return getLaValue() * quantity;
    }
    
    /**
     * Checks if this item is members-only.
     * 
     * @return true if members-only, false otherwise
     */
    public boolean isMembers() {
        return itemComposition != null && itemComposition.isMembers();
    }
    
    /**
     * Checks if this item is tradeable.
     * 
     * @return true if tradeable, false otherwise
     */
    public boolean isTradeable() {
        return itemComposition != null && itemComposition.isTradeable();
    }
    
    /**
     * Gets the item's inventory actions.
     * 
     * @return Array of inventory actions
     */
    public String[] getInventoryActions() {
        return itemComposition != null ? itemComposition.getInventoryActions() : new String[0];
    }
    
    // ============================================
    // Looting Utility Methods
    // ============================================
    
    /**
     * Checks if this item is worth looting based on minimum value.
     * 
     * @param minValue The minimum value threshold
     * @return true if the item's total value meets the threshold
     */
    public boolean isWorthLooting(int minValue) {
        return getTotalValue() >= minValue;
    }
    
    /**
     * Checks if this item is worth looting based on Grand Exchange value.
     * 
     * @param minGeValue The minimum GE value threshold
     * @return true if the item's total GE value meets the threshold
     */
    public boolean isWorthLootingGe(int minGeValue) {
        return getTotalGeValue() >= minGeValue;
    }
    
    /**
     * Checks if this item is worth high alching based on profit margin.
     * 
     * @param minProfit The minimum profit threshold
     * @return true if high alching would be profitable
     */
    public boolean isProfitableToHighAlch(int minProfit) {
        // High alch value minus nature rune cost (estimated)
        int profit = getTotalHaValue() - (quantity * 200); // Assuming 200gp nature rune cost
        return profit >= minProfit;
    }
    
    /**
     * Checks if this item is a commonly desired loot type.
     * Includes coins, gems, ores, logs, herbs, and high-value items.
     * 
     * @return true if the item is commonly looted
     */
    public boolean isCommonLoot() {
        if (name == null) return false;
        
        String lowerName = name.toLowerCase();
        
        // Always loot coins
        if (lowerName.contains("coins")) return true;
        
        // High value items
        if (getTotalValue() >= 1000) return true;
        
        // Common valuable items
        return lowerName.contains("gem") ||
               lowerName.contains("ore") ||
               lowerName.contains("bar") ||
               lowerName.contains("log") ||
               lowerName.contains("herb") ||
               lowerName.contains("seed") ||
               lowerName.contains("rune") ||
               lowerName.contains("arrow") ||
               lowerName.contains("bolt");
    }
    
    /**
     * Checks if this item should be prioritized for urgent looting.
     * Based on high value and short despawn time.
     * 
     * @return true if the item should be prioritized
     */
    public boolean shouldPrioritize() {
        // High value items or items about to despawn
        return getTotalValue() >= 5000 || willDespawnWithin(30);
    }
    
    // ============================================
    // Distance and Position Methods
    // ============================================
    
    /**
     * Gets the distance to this item from the player.
     * 
     * @return The distance in tiles
     */
    public int getDistanceFromPlayer() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            WorldPoint playerLocation = Microbot.getClient().getLocalPlayer().getWorldLocation();
            return playerLocation.distanceTo(location);
        }).orElse(Integer.MAX_VALUE);
    }
    
    /**
     * Checks if this item is within a certain distance from the player.
     * 
     * @param maxDistance The maximum distance in tiles
     * @return true if within distance, false otherwise
     */
    public boolean isWithinDistanceFromPlayer(int maxDistance) {
        return getDistanceFromPlayer() <= maxDistance;
    }
    
    /**
     * Gets the distance to this item from a specific point.
     * 
     * @param point The world point to measure from
     * @return The distance in tiles
     */
    public int getDistanceFrom(WorldPoint point) {
        return location.distanceTo(point);
    }
    
    /**
     * Checks if this item is within a certain distance from a specific point.
     * 
     * @param point The world point to measure from
     * @param maxDistance The maximum distance in tiles
     * @return true if within distance, false otherwise
     */
    public boolean isWithinDistanceFrom(WorldPoint point, int maxDistance) {
        return getDistanceFrom(point) <= maxDistance;
    }
    
    // ============================================
    // Utility Methods
    // ============================================
    
    /**
     * Gets a string representation of this item.
     * 
     * @return String representation
     */
    @Override
    public String toString() {
        return String.format("Rs2GroundItemModel{id=%d, name='%s', quantity=%d, location=%s, owned=%s, lootable=%s, value=%d, despawnIn=%ds}", 
                id, name, quantity, location, isOwned, isLootAble, getTotalValue(), getSecondsUntilDespawn());
    }
    
    /**
     * Gets a detailed string representation including all properties.
     * 
     * @return Detailed string representation
     */
    public String toDetailedString() {
        return String.format(
            "Rs2GroundItemModel{" +
            "id=%d, name='%s', quantity=%d, location=%s, " +
            "owned=%s, lootable=%s, stackable=%s, noted=%s, tradeable=%s, " +
            "value=%d, geValue=%d, haValue=%d, totalValue=%d, " +
            "ticksSinceSpawn=%d, ticksUntilDespawn=%d, secondsUntilDespawn=%d" +
            "}", 
            id, name, quantity, location,
            isOwned, isLootAble, isStackable(), isNoted(), isTradeable(),
            getValue(), getPrice(), getHaPrice(), getTotalValue(),
            getTicksSinceSpawn(), getTicksUntilDespawn(), getSecondsUntilDespawn()
        );
    }
}
