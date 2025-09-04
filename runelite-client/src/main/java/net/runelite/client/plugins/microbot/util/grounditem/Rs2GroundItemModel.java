package net.runelite.client.plugins.microbot.util.grounditem;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Constants;
import net.runelite.api.ItemComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.util.RSTimeUnit;

import java.awt.Rectangle;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

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
@EqualsAndHashCode
@Slf4j
public class Rs2GroundItemModel {
    
    private final TileItem tileItem;
    private final Tile tile;
    private ItemComposition itemComposition;
    private final WorldPoint location;
    private final int id;
    private final int quantity;
    private String name;
    private final boolean isOwned;
    private final boolean isLootAble;
    private final long creationTime;
    private final int creationTick;
    
    // Despawn tracking fields - following GroundItemsOverlay pattern
    private final Instant spawnTime;
    private final Duration despawnDuration;
    private final Duration visibleDuration;
    
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
        this.creationTick =  Microbot.getClient().getTickCount();

        
        // Initialize despawn tracking following GroundItemsPlugin.buildGroundItem() pattern
        this.spawnTime = Instant.now();
        
        // Calculate despawn time exactly like GroundItemsPlugin.buildGroundItem()
        // final int despawnTime = item.getDespawnTime() - client.getTickCount();
        // .despawnTime(Duration.of(despawnTime, RSTimeUnit.GAME_TICKS))
        int despawnTime = 0;
        int visibleTime = 0;
        if (tileItem.getDespawnTime() > this.creationTick){
            despawnTime = tileItem.getDespawnTime() - this.creationTick;
           
        }
        if (tileItem.getVisibleTime() > this.creationTick) {
            visibleTime = tileItem.getVisibleTime() - this.creationTick;   
        }
        // Use the exact same pattern as official RuneLite GroundItemsPlugin
        this.despawnDuration = Duration.of(despawnTime, RSTimeUnit.GAME_TICKS);
        this.visibleDuration = Duration.of(visibleTime, RSTimeUnit.GAME_TICKS);
        
        // Initialize composition and name as null for lazy loading
        this.itemComposition = null;
        this.name = null;
        log.debug("Created Rs2GroundItemModel: {} x{} at {} | Spawn: {} | Despawn: {} (Local) | tick despawn: {} | current tick: {}", 
            getName(), quantity, location, 
            spawnTime.atZone(ZoneOffset.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            getDespawnTime().atZone(ZoneOffset.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            tileItem.getDespawnTime(),
             this.creationTick
            );
    }

    /**
     * Lazy loads the ItemComposition if not already loaded.
     * This ensures we can work with ground items while minimizing performance impact.
     */
    private void ensureCompositionLoaded() {
        if (itemComposition == null && id > 0) {
            try {
                this.itemComposition = Microbot.getClientThread().runOnClientThreadOptional(() -> 
                    Microbot.getClient().getItemDefinition(id)
                ).orElse(null);
                
                if (itemComposition != null) {
                    this.name = itemComposition.getName();
                } else {
                    log.warn("Failed to load ItemComposition for ground item id: {}, setting default name", id);
                    this.name = "Unknown Item";
                }
            } catch (Exception e) {
                log.warn("Error loading ItemComposition for ground item id: {}, using defaults: {}", id, e.getMessage());
                this.name = "Unknown Item";
                this.itemComposition = null;
            }
        }
    }

    /**
     * Gets the item name, loading composition if needed.
     * 
     * @return The item name or "Unknown Item" if composition fails to load
     */
    public String getName() {
        if (name == null) {
            ensureCompositionLoaded();
        }
        return name != null ? name : "Unknown Item";
    }

    /**
     * Gets the item composition, loading it if needed.
     * 
     * @return The ItemComposition or null if it fails to load
     */
    public ItemComposition getItemComposition() {
        if (itemComposition == null) {
            ensureCompositionLoaded();
        }
        return itemComposition;
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
        return  Microbot.getClient().getTickCount() - creationTick;        
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
     * Gets the spawn time as an Instant (following GroundItemsOverlay pattern).
     * 
     * @return Instant when the item spawned
     */
    public Instant getSpawnTime() {
        return spawnTime;
    }
    
    /**
     * Gets the despawn duration for this item.
     * 
     * @return Duration until despawn from spawn time
     */
    public Duration getDespawnDuration() {
        return despawnDuration;
    }
    
    /**
     * Gets the absolute time when this item will despawn (following GroundItemsOverlay pattern).
     * 
     * @return Instant when the item despawns
     */
    public Instant getDespawnTime() {
        return spawnTime.plus(despawnDuration);
    }
    
    /**
     * Gets the UTC timestamp when this item will despawn.
     * 
     * @return UTC timestamp in milliseconds when item despawns
     */
    public long getDespawnTimestampUtc() {
        return getDespawnTime().toEpochMilli();
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
        // Convert despawnDuration back to ticks using RSTimeUnit pattern
        long despawnTicks = despawnDuration.toMillis() / Constants.GAME_TICK_LENGTH;
        
        int currentTick = Microbot.getClientThread().runOnClientThreadOptional(
            () -> Microbot.getClient().getTickCount()
        ).orElse((int)(creationTick + despawnTicks + 1)); // Fallback assumes despawned
        
        int ticksSinceSpawn = currentTick - creationTick;
        long ticksRemaining = despawnTicks - ticksSinceSpawn;
        return Math.max(0, (int)ticksRemaining);
    }
    
    // ============================================
    // UTC Timestamp Getter Methods
    // ============================================
    
    /**
     * Gets the UTC spawn time as ZonedDateTime.
     * 
     * @return Spawn time in UTC
     */
    public ZonedDateTime getSpawnTimeUtc() {
        return spawnTime.atZone(ZoneOffset.UTC);
    }
    
    /**
     * Gets the UTC spawn timestamp in milliseconds.
     * 
     * @return Spawn timestamp in UTC milliseconds
     */
    public long getSpawnTimestampUtc() {
        return spawnTime.toEpochMilli();
    }
    
    /**
     * Gets the total number of ticks this item should exist before despawning.
     * 
     * @return Total despawn ticks
     */
    public int getDespawnTicks() {
        return (int)(despawnDuration.toMillis() / Constants.GAME_TICK_LENGTH);
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
     * Checks if this item has despawned based on game ticks.
     * This is more accurate than real-time checking since OSRS is tick-based.
     * 
     * @return true if the item should have despawned
     */
    public boolean isDespawned() {
        if (isPersistened()) {
            return false; // Persisted items never despawn
        }
    
		return Instant.now().isAfter(getDespawnTime());
        // Use tick-based calculation for more accurate game timing
        //long despawnTicks = despawnDuration.toMillis() / Constants.GAME_TICK_LENGTH;        
        //int currentTick = Microbot.getClient().getTickCount();        
        //int ticksSinceSpawn = currentTick - creationTick;
        //return ticksSinceSpawn >= despawnTicks;
    }
    public boolean isPersistened(){
        // Check if the despawn duration is negative or very large
        return despawnDuration.isNegative() ||despawnDuration.isZero() || despawnDuration.toMillis() > 24 * 60 * 60 * 1000; // More than 24 hours
    }
    
    /**
     * Checks if this item has despawned based on UTC timestamp (fallback method).
     * Less accurate than tick-based method but useful when client is unavailable.
     * 
     * @return true if the item should have despawned based on time
     */
    public boolean isDespawnedByTime() {
        return ZonedDateTime.now(ZoneOffset.UTC).isAfter(getDespawnTime().atZone(ZoneOffset.UTC));
    }    /**
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
        try {
            ItemComposition composition = getItemComposition();
            return composition != null && composition.isStackable();
        } catch (Exception e) {
            log.warn("Error checking if item is stackable for id: {}: {}", id, e.getMessage());
            return false;
        }
    }
    
    /**
     * Checks if this item is noted.
     * 
     * @return true if noted, false otherwise
     */
    public boolean isNoted() {
        try {
            ItemComposition composition = getItemComposition();
            return composition != null && composition.getNote() != -1;
        } catch (Exception e) {
            log.warn("Error checking if item is noted for id: {}: {}", id, e.getMessage());
            return false;
        }
    }
    
    /**
     * Gets the item's store value.
     * 
     * @return The item's store value
     */
    public int getValue() {
        try {
            ItemComposition composition = getItemComposition();
            return composition != null ? composition.getPrice() : 0;
        } catch (Exception e) {
            log.warn("Error getting value for item id: {}: {}", id, e.getMessage());
            return 0;
        }
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
        try {
            ItemComposition composition = getItemComposition();
            return composition != null ? composition.getHaPrice() : 0;
        } catch (Exception e) {
            log.warn("Error getting high alchemy price for item id: {}: {}", id, e.getMessage());
            return 0;
        }
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
        try {
            ItemComposition composition = getItemComposition();
            return composition != null ? (int)(composition.getPrice() * 0.4) : 0;
        } catch (Exception e) {
            log.warn("Error getting low alchemy value for item id: {}: {}", id, e.getMessage());
            return 0;
        }
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
        try {
            ItemComposition composition = getItemComposition();
            return composition != null && composition.isMembers();
        } catch (Exception e) {
            log.warn("Error checking if item is members-only for id: {}: {}", id, e.getMessage());
            return false;
        }
    }
    
    /**
     * Checks if this item is tradeable.
     * 
     * @return true if tradeable, false otherwise
     */
    public boolean isTradeable() {
        try {
            ItemComposition composition = getItemComposition();
            return composition != null && composition.isTradeable();
        } catch (Exception e) {
            log.warn("Error checking if item is tradeable for id: {}: {}", id, e.getMessage());
            return false;
        }
    }
    
    /**
     * Gets the item's inventory actions.
     * 
     * @return Array of inventory actions
     */
    public String[] getInventoryActions() {
        try {
            ItemComposition composition = getItemComposition();
            return composition != null ? composition.getInventoryActions() : new String[0];
        } catch (Exception e) {
            log.warn("Error getting inventory actions for item id: {}: {}", id, e.getMessage());
            return new String[0];
        }
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
            WorldPoint playerLocation = 
            Rs2Player.getWorldLocation();
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
    // Scene and Viewport Detection Methods
    // ============================================
    
    /**
     * Checks if this ground item is still present in the current scene.
     * This verifies that the TileItem still exists on its tile in the scene.
     * 
     * @return true if the item is still in the current scene, false otherwise
     */
    public boolean isInCurrentScene() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            try {
                Scene scene = Microbot.getClient().getTopLevelWorldView().getScene();
                
                // Check if the world point is within current scene bounds using WorldPoint.isInScene
                if (!WorldPoint.isInScene(scene, location.getX(), location.getY())) {
                    return false;
                }
                
                // Convert world point to local coordinates for scene tile access
                LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), location);
                if (localPoint == null) {
                    return false;
                }
                
                // Get the tile from the scene using local coordinates
                Tile[][][] sceneTiles = scene.getTiles();
                int plane = location.getPlane();
                int sceneX = localPoint.getSceneX();
                int sceneY = localPoint.getSceneY();
                
                // Validate scene coordinates
                if (plane < 0 || plane >= sceneTiles.length ||
                    sceneX < 0 || sceneX >= Constants.SCENE_SIZE ||
                    sceneY < 0 || sceneY >= Constants.SCENE_SIZE) {
                    return false;
                }
                
                Tile sceneTile = sceneTiles[plane][sceneX][sceneY];
                if (sceneTile == null) {
                    return false;
                }
                
                // Check if our TileItem is still on this tile
                if (sceneTile.getGroundItems() != null) {
                    for (TileItem item : sceneTile.getGroundItems()) {
                        if (item.getId() == this.id && 
                            item.getQuantity() == this.quantity &&
                            item.equals(this.tileItem)) {
                            return true;
                        }
                    }
                }
                
                return false;
            } catch (Exception e) {
                log.warn("Error checking if ground item is in current scene: {}", e.getMessage());
                return false;
            }
        }).orElse(false);
    }
    
    /**
     * Checks if this ground item is visible and clickable in the current viewport.
     * This combines scene presence checking with viewport visibility detection.
     * 
     * @return true if the item is visible and clickable in the viewport, false otherwise
     */
    public boolean isVisibleInViewport() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            try {
                // First check if item is still in scene
                if (!isInCurrentScene()) {
                    return false;
                }
                
                // Convert world location to canvas point using Perspective.localToCanvas
                LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), location);
                if (localPoint == null) {
                    return false;
                }
                
                Point canvasPoint = Perspective.localToCanvas(Microbot.getClient(), localPoint, location.getPlane());
                if (canvasPoint == null) {
                    return false;
                }
                
                // Check if the point is within the viewport bounds
                // Following the pattern from Rs2ObjectCacheUtils.isPointInViewport
                int viewportX = Microbot.getClient().getViewportXOffset();
                int viewportY = Microbot.getClient().getViewportYOffset();
                int viewportWidth = Microbot.getClient().getViewportWidth();
                int viewportHeight = Microbot.getClient().getViewportHeight();
                
                return canvasPoint.getX() >= viewportX && 
                       canvasPoint.getX() <= viewportX + viewportWidth &&
                       canvasPoint.getY() >= viewportY && 
                       canvasPoint.getY() <= viewportY + viewportHeight;
                       
            } catch (Exception e) {
                log.warn("Error checking if ground item is visible in viewport: {}", e.getMessage());
                return false;
            }
        }).orElse(false);
    }
    
    /**
     * Gets the canvas point for this ground item if it's visible.
     * Useful for click operations and overlay rendering.
     * 
     * @return the Point on the canvas, or null if not visible
     */
    public Point getCanvasPoint() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            try {
                if (!isInCurrentScene()) {
                    return null;
                }
                
                LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), location);
                if (localPoint == null) {
                    return null;
                }
                
                return Perspective.localToCanvas(Microbot.getClient(), localPoint, location.getPlane());
            } catch (Exception e) {
                log.warn("Error getting canvas point for ground item: {}", e.getMessage());
                return null;
            }
        }).orElse(null);
    }
    
    /**
     * Checks if this ground item is clickable (in scene and in viewport).
     * Convenience method that combines isInCurrentScene() and isVisibleInViewport().
     * 
     * @return true if the item can be clicked, false otherwise
     */
    public boolean isClickable() {
        return isInCurrentScene() && isVisibleInViewport();
    }
    
    /**
     * Checks if this ground item is currently clickable by the player within a specific distance.
     * This combines scene presence, viewport visibility, and distance checks.
     * 
     * @param maxDistance The maximum interaction distance in tiles
     * @return true if the item is clickable within the specified distance, false otherwise
     */
    public boolean isClickable(int maxDistance) {
        // Check if item is lootable first
        if (!isLootAble) {
            return false;
        }
        
        // Check if item has despawned
        if (isDespawned()) {
            return false;
        }
        
        // Check distance from player
        if (!isWithinDistanceFromPlayer(maxDistance)) {
            return false;
        }
        
        // Check if visible in viewport (includes scene presence check)
        return isVisibleInViewport();
    }
    
    /**
     * Gets the viewport bounds as a Rectangle for utility calculations.
     * Following the pattern from Rs2ObjectCacheUtils.getViewportBounds.
     * 
     * @return Rectangle representing the current viewport bounds, or null if unavailable
     */
    public Rectangle getViewportBounds() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            try {
                int viewportX = Microbot.getClient().getViewportXOffset();
                int viewportY = Microbot.getClient().getViewportYOffset();
                int viewportWidth = Microbot.getClient().getViewportWidth();
                int viewportHeight = Microbot.getClient().getViewportHeight();
                
                return new Rectangle(viewportX, viewportY, viewportWidth, viewportHeight);
            } catch (Exception e) {
                log.warn("Error getting viewport bounds: {}", e.getMessage());
                return null;
            }
        }).orElse(null);
    }
    
    // ============================================
    // Utility Methods
    // ============================================
    
    /**
     * Gets a string representation of this item with UTC timing information.
     * 
     * @return String representation
     */
    @Override
    public String toString() {
        return String.format("Rs2GroundItemModel{id=%d, name='%s', quantity=%d, location=%s, owned=%s, lootable=%s, value=%d, despawnTicksLeft=%d, spawnTimeUtc='%s', despawnTimeUtc='%s'}", 
                id, name, quantity, location, isOwned, isLootAble, getTotalValue(), getTicksUntilDespawn(), 
                getSpawnTimeUtc().toString(), getDespawnTime().atZone(ZoneOffset.UTC).toString());
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
