package net.runelite.client.plugins.microbot.util.gameobject;

import lombok.Data;
import lombok.Getter;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;

/**
 * Enhanced model for game objects with caching and tick tracking.
 * Provides a unified interface for GameObject, GroundObject, WallObject, and DecorativeObject.
 */
@Data
@Getter
public class Rs2ObjectModel {
    
    /**
     * Enum representing different types of game objects.
     */
    public enum ObjectType {
        GAME_OBJECT(10, "GameObject"),
        GROUND_OBJECT(2, "GroundObject"),
        WALL_OBJECT(1, "WallObject"),
        DECORATIVE_OBJECT(3, "DecorativeObject"),
        TILE_OBJECT(0, "TileObject"); // Generic tile object fallback
        
        private final int typeId;
        private final String typeName;
        
        ObjectType(int typeId, String typeName) {
            this.typeId = typeId;
            this.typeName = typeName;
        }
        
        public int getTypeId() {
            return typeId;
        }
        
        public String getTypeName() {
            return typeName;
        }
        
        /**
         * Determines the ObjectType from a TileObject instance.
         * Uses proper type hierarchy checking to avoid misclassification.
         * 
         * @param tileObject The TileObject to analyze
         * @return The corresponding ObjectType
         */
        public static ObjectType fromTileObject(TileObject tileObject) {
            if (tileObject == null) {
                return TILE_OBJECT;
            }
           
            // Check specific types first (most specific to least specific)
            // This is important because GameObject extends TileObject, so we need to check it first
            if (tileObject instanceof GameObject) {
                return GAME_OBJECT;
            } else if (tileObject instanceof WallObject) {
                return WALL_OBJECT;
            } else if (tileObject instanceof DecorativeObject) {
                return DECORATIVE_OBJECT;
            } else if (tileObject instanceof GroundObject) {
                return GROUND_OBJECT;
            } else {
                // Generic TileObject (should be rare)
                return TILE_OBJECT;
            }
        }
    }
    
    private final TileObject tileObject; // The actual game object (GameObject, GroundObject, etc.)
    private final Tile tile;
    private final int sizeX; // Width in tiles (1 for single-tile objects)
    private final int sizeY; // Height in tiles (1 for single-tile objects)    
    
    public ObjectType getObjectType(){
        return ObjectType.fromTileObject(tileObject);
    }
    public WorldPoint getLocation() {
        return getCanonicalLocation();
    }
    
    public int getId() {
        return tileObject.getId();
    }
    
    /**
     * Gets the width of the object in tiles.
     * 
     * @return The width in tiles (1 for single-tile objects)
     */
    public int getSizeX() {
        return sizeX;
    }
    
    /**
     * Gets the height of the object in tiles.
     * 
     * @return The height in tiles (1 for single-tile objects)
     */
    public int getSizeY() {
        return sizeY;
    }
    
    private final String name;
    private final long creationTime;
    private final int creationTick;
    ObjectComposition objectComposition; // Cached object composition for performance
    /**
     * Creates a new Rs2ObjectModel from a TileObject, automatically determining the object type.
     * This is the preferred constructor as it handles type detection automatically.
     * 
     * @param tileObject The TileObject (GameObject, GroundObject, WallObject, or DecorativeObject)
     * @param tile The tile the object is on
     */
    public Rs2ObjectModel(TileObject tileObject, Tile tile) {
        this.tileObject = tileObject;
        this.tile = tile;
        
        // Calculate size and canonical location based on object type
        if (tileObject instanceof GameObject) {
            GameObject gameObject = (GameObject) tileObject;
            this.sizeX = gameObject.sizeX();
            this.sizeY = gameObject.sizeY();
        
        } else {
            // Single-tile objects (GroundObject, WallObject, DecorativeObject)
            this.sizeX = 1;
            this.sizeY = 1;            
        }
        
        this.name = getObjectName(tileObject.getId());
        this.creationTime = System.currentTimeMillis();
        this.creationTick = Microbot.getClientThread().runOnClientThreadOptional(() ->
            Microbot.getClient().getTickCount()).orElse(0);
    }
    
    /**
     * Creates a new Rs2ObjectModel from a GameObject.
     * 
     * @param gameObject The GameObject
     * @param tile The tile the object is on
     * @param objectType The type of object (deprecated - will be auto-detected)
     * @deprecated Use {@link #Rs2ObjectModel(TileObject, Tile)} instead for automatic type detection
     */
    @Deprecated
    public Rs2ObjectModel(GameObject gameObject, Tile tile, ObjectType objectType) {
        this((TileObject) gameObject, tile);
    }
    
    /**
     * Creates a new Rs2ObjectModel from a GroundObject.
     * 
     * @param groundObject The GroundObject
     * @param tile The tile the object is on
     * @param objectType The type of object (deprecated - will be auto-detected)
     * @deprecated Use {@link #Rs2ObjectModel(TileObject, Tile)} instead for automatic type detection
     */
    @Deprecated
    public Rs2ObjectModel(GroundObject groundObject, Tile tile, ObjectType objectType) {
        this((TileObject) groundObject, tile);
    }
    
    /**
     * Creates a new Rs2ObjectModel from a WallObject.
     * 
     * @param wallObject The WallObject
     * @param tile The tile the object is on
     * @param objectType The type of object (deprecated - will be auto-detected)
     * @deprecated Use {@link #Rs2ObjectModel(TileObject, Tile)} instead for automatic type detection
     */
    @Deprecated
    public Rs2ObjectModel(WallObject wallObject, Tile tile, ObjectType objectType) {
        this((TileObject) wallObject, tile);
    }
    
    /**
     * Creates a new Rs2ObjectModel from a DecorativeObject.
     * 
     * @param decorativeObject The DecorativeObject
     * @param tile The tile the object is on
     * @param objectType The type of object (deprecated - will be auto-detected)
     * @deprecated Use {@link #Rs2ObjectModel(TileObject, Tile)} instead for automatic type detection
     */
    @Deprecated
    public Rs2ObjectModel(DecorativeObject decorativeObject, Tile tile, ObjectType objectType) {
        this((TileObject) decorativeObject, tile);
    }
    
    /**
     * Gets the object name from the game's object definitions.
     * 
     * @param objectId The object ID
     * @return The object name or "Unknown Object" if not found
     */
    private String getObjectName(int objectId) {
        try {
            return Microbot.getClientThread().runOnClientThreadOptional(() -> {
                ObjectComposition composition = getObjectComposition();
                return composition != null ? composition.getName() : "Unknown Object";
            }).orElse("Unknown Object");
        } catch (Exception e) {
            return "Unknown Object";
        }
    }
    
    /**
     * Gets the number of ticks since this object was created.
     * 
     * @return The number of ticks since creation
     */
    public int getTicksSinceCreation() {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
            Microbot.getClient().getTickCount() - creationTick).orElse(0);
    }
    
    /**
     * Gets the time in milliseconds since this object was created.
     * 
     * @return Milliseconds since creation
     */
    public long getTimeSinceCreation() {
        return System.currentTimeMillis() - creationTime;
    }
    
    /**
     * Gets the distance to this object from the player.
     * 
     * @return The distance in tiles
     */
    public int getDistanceFromPlayer() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            WorldPoint playerLocation = Microbot.getClient().getLocalPlayer().getWorldLocation();
            return playerLocation.distanceTo(getLocation());
        }).orElse(Integer.MAX_VALUE);
    }
    
    /**
     * Checks if this object is within a certain distance from the player.
     * 
     * @param maxDistance The maximum distance in tiles
     * @return true if within distance, false otherwise
     */
    public boolean isWithinDistanceFromPlayer(int maxDistance) {
        return getDistanceFromPlayer() <= maxDistance;
    }

    
    
    /**
     * Gets the object composition for this object.
     * 
     * @return The ObjectComposition or null if not available
     */
    public ObjectComposition getObjectComposition() {
        if (this.objectComposition == null){
            this.objectComposition =  Microbot.getClientThread().runOnClientThreadOptional(() -> 
            Microbot.getClient().getObjectDefinition(tileObject.getId())
            ).orElse(null);
        }
        return this.objectComposition;
    }
    
    /**
     * Gets the object's actions.
     * 
     * @return Array of actions or empty array if not available
     */
    public String[] getActions() {
        ObjectComposition composition = getObjectComposition();
        return composition != null ? composition.getActions() : new String[0];
    }
    
    /**
     * Checks if this object has a specific action.
     * 
     * @param action The action to check for
     * @return true if the object has the action, false otherwise
     */
    public boolean hasAction(String action) {
        String[] actions = getActions();
        for (String objectAction : actions) {
            if (objectAction != null && objectAction.equalsIgnoreCase(action)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Gets the object's width.
     * 
     * @return The object's width or 1 if not available
     */
    public int getWidth() {
        ObjectComposition composition = getObjectComposition();
        return composition != null ? composition.getSizeX() : 1;
    }
    
    /**
     * Gets the object's height.
     * 
     * @return The object's height or 1 if not available
     */
    public int getHeight() {
        ObjectComposition composition = getObjectComposition();
        return composition != null ? composition.getSizeY() : 1;
    }
    
    /**
     * Checks if this object is solid (blocks movement).
     * 
     * @return true if solid, false otherwise
     */
    public boolean isSolid() {
        ObjectComposition composition = getObjectComposition();
        return composition != null && composition.getImpostorIds() == null;
    }
    
    /**
     * Gets the world location of this object.
     * This is an alias for getLocation() for compatibility with pathing utilities.
     * 
     * @return The world location
     */
    public WorldPoint getWorldLocation() {
        return getLocation();
    }
    
    /**
     * Gets the object type as an integer value.
     * 1=wall, 2=ground, 3=decorative, 10=game
     * 
     * @return The type as an integer
     */
    public int getType() {
        return getObjectType().getTypeId();
    }
    
    /**
     * Gets the object type as a string name.
     * 
     * @return The type name as a string
     */
    public String getTypeName() {
        return getObjectType().getTypeName();
    }
    
    /**
     * Checks if this object blocks line of sight.
     * Uses a comprehensive heuristic based on object type, properties, and known behaviors.
     * 
     * @return true if likely to block LOS, false otherwise
     */
    public boolean blocksLineOfSight() {
        // Wall objects typically block LOS
        if (getObjectType() == ObjectType.WALL_OBJECT) {
            return true;
        }
        
        // Ground objects typically don't block LOS
        if (getObjectType() == ObjectType.GROUND_OBJECT) {
            return false;
        }
        
        // For game objects, use more sophisticated checks
        if (getObjectType() == ObjectType.GAME_OBJECT) {
            // Get object composition for more details
            ObjectComposition composition = getObjectComposition();
            if (composition == null) {
                return false;
            }
            
            // Check object size - larger objects are more likely to block sight
            if (composition.getSizeX() > 1 || composition.getSizeY() > 1) {
                return true;
            }
            
            // Check specific action keywords that suggest blocking objects
            String[] actions = composition.getActions();
            if (actions != null) {
                for (String action : actions) {
                    if (action != null && (
                            action.contains("Open") || 
                            action.contains("Close") || 
                            action.contains("Push") || 
                            action.contains("Enter"))) {
                        return true; // Doors, gates, etc. typically block sight
                    }
                }
            }
            
            // Known categories of blocking objects
            String name = composition.getName().toLowerCase();
            if (name.contains("wall") || 
                name.contains("door") || 
                name.contains("gate") || 
                name.contains("fence") ||
                name.contains("pillar") ||
                name.contains("barrier")) {
                return true;
            }
        }
        
        // Decorative objects sometimes block LOS
        if (getObjectType() == ObjectType.DECORATIVE_OBJECT) {
            // Get object composition for more details
            ObjectComposition composition = getObjectComposition();
            if (composition == null) {
                return false;
            }
            
            // Larger decorative objects may block sight
            if (composition.getSizeX() > 1 && composition.getSizeY() > 1) {
                return true;
            }
            
            // Check by name
            String name = composition.getName().toLowerCase();
            return name.contains("pillar") || 
                   name.contains("statue") || 
                   name.contains("archway");
        }
        
        return false;
    }
    
    /**
     * Gets the underlying TileObject (could be GameObject, GroundObject, WallObject, or DecorativeObject).
     * 
     * @return The TileObject instance
     */
    public TileObject getTileObject() {
        return tileObject;
    }

    /**
     * Gets a string representation of this object.
     * 
     * @return String representation
     */
    @Override
    public String toString() {
        return String.format("Rs2ObjectModel{type=%s, id=%d, name='%s', location=%s}", 
                getObjectType(), getId(), getName(), getLocation());
    }
    /**
     * Gets the canonical world location for a GameObject.
     * For multi-tile objects, this returns the southwest tile location.
     * 
     * @param gameObject The GameObject to get the canonical location for
     * @param tile The tile from the event
     * @return The canonical world location
     */
    public WorldPoint getCanonicalLocation() {
        if (getObjectType() != ObjectType.GAME_OBJECT) {
            return tileObject.getWorldLocation(); // For single-tile objects, just return the world location
        }
        GameObject gameObject = (GameObject) tileObject;
        // For multi-tile objects, we need to ensure we use the southwest tile consistently
        Point sceneMinLocation = gameObject.getSceneMinLocation();
        Point currentSceneLocation = tile.getSceneLocation();
        
        // If this is the southwest tile, use this tile's location
        if (sceneMinLocation != null && currentSceneLocation != null && 
            sceneMinLocation.getX() == currentSceneLocation.getX() && 
            sceneMinLocation.getY() == currentSceneLocation.getY()) {
            return tile.getWorldLocation();
        }
        
        // Otherwise, we need to calculate the southwest tile's world location
        // This is tricky without scene-to-world conversion, so we'll use a different approach
        WorldPoint currentLocation = tile.getWorldLocation();
        if (sceneMinLocation != null && currentSceneLocation != null) {
            int deltaX = currentSceneLocation.getX() - sceneMinLocation.getX();
            int deltaY = currentSceneLocation.getY() - sceneMinLocation.getY();
            return new WorldPoint(currentLocation.getX() - deltaX, currentLocation.getY() - deltaY, currentLocation.getPlane());
        }
        
        return currentLocation;
    }
}
