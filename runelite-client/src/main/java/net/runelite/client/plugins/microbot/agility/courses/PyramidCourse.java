package net.runelite.client.plugins.microbot.agility.courses;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.GroundObject;
import net.runelite.api.ItemID;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.agility.models.AgilityObstacleModel;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.agility.courses.PyramidObstacleData.ObstacleArea;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class PyramidCourse implements AgilityCourseHandler {
    
    private static final WorldPoint START_POINT = new WorldPoint(3354, 2830, 0);
    private static final WorldPoint SIMON_LOCATION = new WorldPoint(3343, 2827, 0);
    private static final String SIMON_NAME = "Simon Templeton";
    private static final int PYRAMID_TOP_REGION = 12105;
    
    // Centralized state tracking
    private final PyramidState state = new PyramidState();
    
    
    // Obstacle areas are now defined in PyramidObstacleData for better maintainability
    private static final List<ObstacleArea> OBSTACLE_AREAS = PyramidObstacleData.OBSTACLE_AREAS;
    
    @Override
    public WorldPoint getStartPoint() {
        return START_POINT;
    }
    
    @Override
    public List<AgilityObstacleModel> getObstacles() {
        // Return all unique obstacle IDs for compatibility
        return Arrays.asList(
            new AgilityObstacleModel(10857), // Stairs
            new AgilityObstacleModel(10865), // Low wall
            new AgilityObstacleModel(10860), // Ledge
            new AgilityObstacleModel(10867), // Plank (main object)
            new AgilityObstacleModel(10868), // Plank end (clickable)
            new AgilityObstacleModel(10859), // Gap jump
            new AgilityObstacleModel(10882), // Gap (floor 1)
            new AgilityObstacleModel(10886), // Ledge 3
            new AgilityObstacleModel(10884), // Gap (floor 2)
            new AgilityObstacleModel(10861), // Gap
            new AgilityObstacleModel(10888), // Ledge 2
            new AgilityObstacleModel(10851), // Climbing rocks
            new AgilityObstacleModel(10855)  // Doorway
        );
    }
    
    @Override
    public TileObject getCurrentObstacle() {
        WorldPoint playerPos = Rs2Player.getWorldLocation();
        
        // Null check for player position (can happen during logout/disconnect)
        if (playerPos == null) {
            if (log.isDebugEnabled()) {
                log.debug("Player position is null (likely during logout/disconnect) - returning null");
            }
            return null;
        }
        
        if (log.isDebugEnabled()) {
            log.debug("=== getCurrentObstacle called - Player at {} (plane: {}) ===", playerPos, playerPos.getPlane());
            log.debug("FLAG STATES: CrossGap={}, XpObstacle={}, PyramidTurnIn={}", 
                state.isDoingCrossGap(), state.isDoingXpObstacle(), state.isHandlingPyramidTurnIn());
        }
        
        // Check if we should turn in pyramids (either inventory full OR reached random threshold) AND we're on ground level
        int pyramidCount = Rs2Inventory.count(ItemID.PYRAMID_TOP);
        boolean shouldTurnIn = (Rs2Inventory.isFull() || pyramidCount >= state.getPyramidTurnInThreshold()) && playerPos.getPlane() == 0;
        
        if (shouldTurnIn) {
            if (pyramidCount > 0) {
                // We have pyramid tops - handle turn-in
                if (!state.isHandlingPyramidTurnIn()) {
                    if (log.isDebugEnabled()) {
                        if (Rs2Inventory.isFull()) {
                            log.debug("Inventory is full with {} pyramid tops - going to Simon Templeton", pyramidCount);
                        } else {
                            log.debug("Reached threshold of {} pyramids (have {}) - going to Simon Templeton", 
                                state.getPyramidTurnInThreshold(), pyramidCount);
                        }
                    }
                    state.startPyramidTurnIn();
                }
                
                // Handle pyramid turn-in
                if (handlePyramidTurnIn()) {
                    return null; // Return null to prevent obstacle interaction
                }
            } else if (Rs2Inventory.isFull()) {
                // Inventory is full but no pyramid tops - stop and warn
                Microbot.showMessage("Inventory is full but no pyramid tops found! Clear inventory to continue.");
                log.warn("Inventory full without pyramid tops - stopping");
                return null;
            }
        } else if (!Rs2Inventory.isFull() && pyramidCount < state.getPyramidTurnInThreshold() && state.isHandlingPyramidTurnIn()) {
            // Only clear the turn-in flag if we were actively handling turn-in but pyramid count dropped
            // This preserves the threshold until we actually complete a pyramid
            state.clearPyramidTurnIn();
        }
        
        // NEVER return an obstacle while moving or animating
        if (Rs2Player.isMoving() || Rs2Player.isAnimating()) {
            log.debug("Player is moving/animating, returning null to prevent clicking");
            return null;
        }
        
        // Check for empty waterskins and drop them
        if (handleEmptyWaterskins()) {
            return null; // Return null to prevent obstacle interaction this cycle
        }
        
        // Special blocking for Cross Gap obstacles - don't return any obstacle while doing Cross Gap
        if (state.isDoingCrossGap()) {
            log.debug("Cross Gap flag is SET - blocking all obstacle selection");
            return null;
        }
        
        // Block all obstacles while doing any XP-granting obstacle (plank, gap, ledge, etc)
        if (state.isDoingXpObstacle()) {
            log.debug("Currently doing XP-granting obstacle, blocking all other obstacles until XP received");
            return null;
        }
        
        // Double-check movement after a brief moment - animations can have pauses
        Global.sleep(35, 65); // Brief jittered delay
        
        // Recheck after the brief pause
        if (Rs2Player.isMoving() || Rs2Player.isAnimating()) {
            log.debug("Player started moving/animating after brief pause, returning null");
            return null;
        }
        
        // Prevent getting obstacles too quickly after starting one
        if (state.isObstacleCooldownActive()) {
            log.debug("Obstacle cooldown active, returning null to prevent spam clicking");
            return null;
        }
        
        // Find the obstacle area containing the player
        ObstacleArea currentArea = null;
        
        // Debug: log areas being checked for current plane
        if (log.isDebugEnabled()) {
            log.debug("Checking areas for plane {} player position {}:", playerPos.getPlane(), playerPos);
            for (ObstacleArea area : OBSTACLE_AREAS) {
                if (area.plane == playerPos.getPlane()) {
                    boolean contains = area.containsPlayer(playerPos);
                    log.debug("  - Area: {} at ({},{}) to ({},{}) - contains player: {}", 
                        area.name, area.minX, area.minY, area.maxX, area.maxY, contains);
                    if (contains) {
                        log.debug("    -> Obstacle ID: {} at location: {}", area.obstacleId, area.obstacleLocation);
                    }
                }
            }
        }
        
        for (ObstacleArea area : OBSTACLE_AREAS) {
            if (area.containsPlayer(playerPos)) {
                // Special check for climbing rocks - skip if we've recently clicked them
                if (area.obstacleId == 10851 && area.name.contains("grab pyramid")) {
                    if (state.isClimbingRocksCooldownActive()) {
                        log.debug("Recently clicked climbing rocks, skipping to next area");
                        continue;
                    }
                }
                
                currentArea = area;
                if (log.isDebugEnabled()) {
                    log.debug("Found player in area: {} (obstacle ID: {})", area.name, area.obstacleId);
                    // Debug: log if this is a plank area
                    if (area.obstacleId == 10868) {
                        log.debug("  Player in PLANK area - should look for plank end ground object");
                    }
                }
                break;
            }
        }
        
        if (currentArea == null) {
            if (log.isDebugEnabled()) {
                log.debug("Player not in any defined obstacle area at {} (plane: {})", playerPos, playerPos.getPlane());
            }
            
            // Special check for floor 4 start position
            if (playerPos.getPlane() == 2 && playerPos.getX() == 3041 && playerPos.getY() == 4695) {
                if (log.isDebugEnabled()) {
                    log.debug("SPECIAL CASE: Player at floor 4 start position (3041, 4695)");
                }
                // Manually find the gap
                TileObject gap = findNearestObstacleWithinDistance(playerPos, 10859, 5);
                if (gap != null) {
                    if (log.isDebugEnabled()) {
                        log.debug("Found Gap manually at {}", gap.getWorldLocation());
                    }
                    return gap;
                }
            }
            
            // Log all areas on current plane for debugging
            if (log.isDebugEnabled()) {
                log.debug("Available areas on plane {}:", playerPos.getPlane());
                int count = 0;
                for (ObstacleArea area : OBSTACLE_AREAS) {
                    if (area.plane == playerPos.getPlane()) {
                        log.debug("  - {} at ({},{}) to ({},{})", 
                            area.name, area.minX, area.minY, area.maxX, area.maxY);
                        count++;
                        if (count > 10) {
                            log.debug("  ... and more areas");
                            break;
                        }
                    }
                }
            }
            
            // Special case: If player just climbed to floor 1, direct them to low wall
            if (playerPos.getPlane() == 1 && playerPos.getX() >= 3354 && playerPos.getX() <= 3355 && playerPos.getY() == 2833) {
                log.debug("Player just arrived on floor 1, looking for low wall");
                // Find the low wall obstacle
                TileObject lowWall = findNearestObstacle(playerPos, 10865);
                if (lowWall != null) {
                    return lowWall;
                }
            }
            
            // Try to find the nearest obstacle on the current plane
            log.debug("Looking for nearest pyramid obstacle...");
            return findNearestPyramidObstacle(playerPos);
        }
        
        if (log.isDebugEnabled()) {
            log.debug("Player in area for: {} at {} (plane: {})", currentArea.name, playerPos, playerPos.getPlane());
        }
        
        // Find the specific obstacle instance
        TileObject obstacle = null;
        
        // For gaps and ledges, always find the nearest one since there can be multiple
        // Also for floor 4, always use nearest search since obstacles can be multi-tile
        if (currentArea.obstacleId == 10859 || currentArea.obstacleId == 10861 || currentArea.obstacleId == 10884 || currentArea.obstacleId == 10860 || playerPos.getPlane() == 2) {
            if (log.isDebugEnabled()) {
                log.debug("Looking for nearest {}", currentArea.name);
            }
            
            // Use strict sequential checking to prevent skipping ahead
            obstacle = findNearestObstacleStrict(playerPos, currentArea.obstacleId, currentArea);
        } else {
            obstacle = findObstacleAt(currentArea.obstacleLocation, currentArea.obstacleId);
            
            if (obstacle == null) {
                if (log.isDebugEnabled()) {
                    log.debug("Could not find {} (ID: {}) at expected location {}", 
                        currentArea.name, currentArea.obstacleId, currentArea.obstacleLocation);
                }
                // Try to find any instance of this obstacle type nearby with strict checking
                obstacle = findNearestObstacleStrict(playerPos, currentArea.obstacleId, currentArea);
            }
        }
        
        if (obstacle != null) {
            if (log.isDebugEnabled()) {
                log.debug("Selected obstacle: {} (ID: {}) at {} for player at {}", 
                    currentArea.name, currentArea.obstacleId, obstacle.getWorldLocation(), playerPos);
            }
            
            // Track long-animation gap obstacles specifically
            // These gaps have long animations that move the player >3 tiles
            if (currentArea.name.contains("Gap") || 
                currentArea.obstacleId == 10882) { // Gap (floor 1) also has long animation
                // Cross gap time is tracked in startCrossGap
                state.startCrossGap(); // Set flag that we're doing Cross Gap-type obstacle
                if (log.isDebugEnabled()) {
                    log.debug("Detected long-animation gap obstacle (ID: {}) - setting flag to block all other obstacles", 
                        currentArea.obstacleId);
                }
            }
            
            // Track any XP-granting obstacle (gaps, planks, ledges, low walls)
            // These give XP: Low wall (8), Ledge (52), Gap/Plank (56.4)
            // These don't give XP: Stairs (0), Doorway (0), Climbing rocks (0)
            if (currentArea.obstacleId == 10865 || // Low wall
                currentArea.obstacleId == 10860 || // Ledge
                currentArea.obstacleId == 10868 || // Plank
                currentArea.obstacleId == 10859 || // Gap
                currentArea.obstacleId == 10861 || // Gap
                currentArea.obstacleId == 10882 || // Gap
                currentArea.obstacleId == 10884 || // Gap Cross
                currentArea.obstacleId == 10886 || // Ledge
                currentArea.obstacleId == 10888) { // Ledge
                state.startXpObstacle();
                log.debug("Starting XP-granting obstacle - blocking all clicks until XP received");
            }
        } else {
            log.error("Could not find any obstacle for area: {} (ID: {})", currentArea.name, currentArea.obstacleId);
        }
        
        // Special handling for pyramid top region - if completed, look for stairs down
        if (obstacle == null && playerPos.getRegionID() == PYRAMID_TOP_REGION && playerPos.getPlane() == 3) {
            TileObject stairs = Rs2GameObject.getTileObject(10857);
            if (stairs != null) {
                log.debug("No obstacle found on pyramid top, found stairs to go back down");
                return stairs;
            }
        }
        
        return obstacle;
    }
    
    private TileObject findObstacleAt(WorldPoint location, int obstacleId) {
        if (log.isDebugEnabled()) {
            log.debug("findObstacleAt: Looking for obstacle {} at {}", obstacleId, location);
        }
        
        // Special handling for plank end which is a ground object
        if (obstacleId == 10868) {
            List<GroundObject> groundObjects = Rs2GameObject.getGroundObjects();
            if (log.isDebugEnabled()) {
                log.debug("Looking for plank end at {}, checking {} ground objects", location, groundObjects.size());
            }
            for (GroundObject go : groundObjects) {
                if (go.getId() == obstacleId && go.getWorldLocation().equals(location)) {
                    if (log.isDebugEnabled()) {
                        log.debug("Found plank end (ground object) at {}", go.getWorldLocation());
                    }
                    return go;
                }
            }
            if (log.isDebugEnabled()) {
                log.debug("No plank end found at expected location {}", location);
                // List all plank ends found
                for (GroundObject go : groundObjects) {
                    if (go.getId() == obstacleId) {
                        log.debug("  Found plank end at {} (not at expected location)", go.getWorldLocation());
                    }
                }
            }
            return null;
        }
        
        // Normal game objects
        List<TileObject> obstacles = Rs2GameObject.getAll(obj -> 
            obj.getId() == obstacleId && 
            obj.getWorldLocation().equals(location)
        );
        
        if (log.isDebugEnabled()) {
            log.debug("Found {} obstacles with ID {} at {}", obstacles.size(), obstacleId, location);
        }
        
        if (obstacles.isEmpty()) {
            if (log.isDebugEnabled()) {
                // Log all obstacles of this type on the current plane
                List<TileObject> allObstaclesOfType = Rs2GameObject.getAll(obj -> 
                    obj.getId() == obstacleId && 
                    obj.getPlane() == location.getPlane()
                );
                log.debug("No obstacle found at exact location. Found {} obstacles with ID {} on plane {}:", 
                    allObstaclesOfType.size(), obstacleId, location.getPlane());
                for (TileObject obj : allObstaclesOfType) {
                    log.debug("  - {} at {}", obstacleId, obj.getWorldLocation());
                }
            }
            return null;
        }
        
        return obstacles.get(0);
    }
    
    private TileObject findNearestObstacleStrict(WorldPoint playerPos, int obstacleId, ObstacleArea currentArea) {
        if (log.isDebugEnabled()) {
            log.debug("Looking for obstacle {} with strict sequential checking", obstacleId);
        }
        
        // Special handling for floor 4 gaps FIRST - need to select the correct one
        // Check if we're on floor 4 (plane 2) and looking for a gap, regardless of exact area name
        if (playerPos.getPlane() == 2 && obstacleId == 10859) {
            // If player is after low wall at (3043, 4701-4702), we need the second gap
            if (playerPos.getX() == 3043 && playerPos.getY() >= 4701) {
                log.debug("Player after low wall on floor 4, looking for second gap at (3048, 4695)");
                // Find the gap at (3048, 4695) specifically
                List<TileObject> gaps = Rs2GameObject.getAll(obj -> 
                    obj.getId() == obstacleId && 
                    obj.getPlane() == playerPos.getPlane() &&
                    obj.getWorldLocation().getX() >= 3047 && obj.getWorldLocation().getX() <= 3049 &&
                    obj.getWorldLocation().getY() >= 4694 && obj.getWorldLocation().getY() <= 4696
                );
                
                if (!gaps.isEmpty()) {
                    TileObject secondGap = gaps.get(0);
                    if (log.isDebugEnabled()) {
                        log.debug("Found second gap at {}", secondGap.getWorldLocation());
                    }
                    return secondGap;
                } else {
                    log.debug("Could not find second gap on floor 4!");
                }
            }
            // If player is at start of floor 4, we need the first gap
            else if (playerPos.getX() >= 3040 && playerPos.getX() <= 3042 && 
                     playerPos.getY() >= 4695 && playerPos.getY() <= 4697) {
                log.debug("Player at start of floor 4, looking for first gap");
                // Find the gap at (3040, 4697) specifically
                List<TileObject> gaps = Rs2GameObject.getAll(obj -> 
                    obj.getId() == obstacleId && 
                    obj.getPlane() == playerPos.getPlane() &&
                    obj.getWorldLocation().getX() >= 3039 && obj.getWorldLocation().getX() <= 3041 &&
                    obj.getWorldLocation().getY() >= 4696 && obj.getWorldLocation().getY() <= 4698
                );
                
                if (!gaps.isEmpty()) {
                    TileObject firstGap = gaps.get(0);
                    if (log.isDebugEnabled()) {
                        log.debug("Found first gap at {}", firstGap.getWorldLocation());
                    }
                    return firstGap;
                }
            }
        }
        
        // Special handling for floor 2 gaps to prevent skipping ahead
        if (playerPos.getPlane() == 2 && (obstacleId == 10859 || obstacleId == 10861 || obstacleId == 10884) && !currentArea.name.contains("floor 4")) {
            // Only search in a very limited area based on the current area definition
            List<TileObject> obstacles = Rs2GameObject.getAll(obj -> {
                if (obj.getId() != obstacleId || obj.getPlane() != playerPos.getPlane()) {
                    return false;
                }
                
                WorldPoint objLoc = obj.getWorldLocation();
                
                // For floor 2 gaps, use very strict position checking
                if (currentArea.name.contains("Gap Cross 1")) {
                    // First gap should be around (3356, 2835)
                    return objLoc.getX() == 3356 && objLoc.getY() >= 2835 && objLoc.getY() <= 2837;
                } else if (currentArea.name.contains("Gap Jump")) {
                    // Gap jump should be around (3356, 2841)
                    return objLoc.getX() == 3356 && objLoc.getY() >= 2838 && objLoc.getY() <= 2844;
                } else if (currentArea.name.contains("Gap Cross 2")) {
                    // Gap cross 2 should be around (3356, 2849)
                    return objLoc.getX() >= 3356 && objLoc.getX() <= 3360 && objLoc.getY() >= 2848 && objLoc.getY() <= 2850;
                } else if (currentArea.name.contains("Gap jump") && currentArea.name.contains("end")) {
                    // End gap jump should be around (3365, 2833)
                    return objLoc.getX() >= 3363 && objLoc.getX() <= 3367 && objLoc.getY() >= 2833 && objLoc.getY() <= 2834;
                }
                
                // Default: must be within 8 tiles
                return objLoc.distanceTo(playerPos) <= 8;
            });
            
            if (!obstacles.isEmpty()) {
                TileObject nearest = obstacles.stream()
                    .min((a, b) -> Integer.compare(
                        a.getWorldLocation().distanceTo(playerPos),
                        b.getWorldLocation().distanceTo(playerPos)
                    ))
                    .orElse(null);
                    
                if (nearest != null) {
                    if (log.isDebugEnabled()) {
                        log.debug("Found strictly checked obstacle at {}", nearest.getWorldLocation());
                    }
                    return nearest;
                }
            }
        }
        
        // For floor 3 gaps, use longer distance
        if (playerPos.getPlane() == 3 && obstacleId == 10859) {
            return findNearestObstacleWithinDistance(playerPos, obstacleId, 20);
        }
        
        // For other obstacles, use normal nearest search but with distance limit
        return findNearestObstacleWithinDistance(playerPos, obstacleId, 10);
    }
    
    private TileObject findNearestObstacleWithinDistance(WorldPoint playerPos, int obstacleId, int maxDistance) {
        if (log.isDebugEnabled()) {
            log.debug("Looking for obstacle {} within {} tiles", obstacleId, maxDistance);
        }
        
        List<TileObject> obstacles = Rs2GameObject.getAll(obj -> 
            obj.getId() == obstacleId && 
            obj.getPlane() == playerPos.getPlane() &&
            obj.getWorldLocation().distanceTo(playerPos) <= maxDistance
        );
        
        if (obstacles.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("No obstacles found within {} tiles", maxDistance);
            }
            return null;
        }
        
        // Log all found obstacles for debugging
        if (log.isDebugEnabled()) {
            log.debug("Found {} obstacles within {} tiles:", obstacles.size(), maxDistance);
            for (TileObject obj : obstacles) {
                log.debug("  - {} at {} (distance: {})", 
                    obstacleId, obj.getWorldLocation(), obj.getWorldLocation().distanceTo(playerPos));
            }
        }
        
        return obstacles.stream()
            .min((a, b) -> Integer.compare(
                a.getWorldLocation().distanceTo(playerPos),
                b.getWorldLocation().distanceTo(playerPos)
            ))
            .orElse(null);
    }
    
    private TileObject findNearestObstacle(WorldPoint playerPos, int obstacleId) {
        // Special case for Ledge on floor 2 - different ledges based on position
        if (obstacleId == 10860 && playerPos.getPlane() == 2) {
            if (log.isDebugEnabled()) {
                log.debug("Special handling for floor 2 Ledge at player position {}", playerPos);
            }
            
            // If player is anywhere in the path from Gap 10861 to Ledge, use east ledge
            if ((playerPos.getX() >= 3372 && playerPos.getX() <= 3373 && playerPos.getY() >= 2841 && playerPos.getY() <= 2850) ||
                (playerPos.getX() >= 3364 && playerPos.getX() <= 3373 && playerPos.getY() >= 2849 && playerPos.getY() <= 2850)) {
                log.debug("Player in path from Gap 10861 to Ledge, looking for east Ledge at (3372, 2839)");
                
                // Find the specific ledge at (3372, 2839)
                TileObject eastLedge = findObstacleAt(new WorldPoint(3372, 2839, 2), obstacleId);
                if (eastLedge != null) {
                    if (log.isDebugEnabled()) {
                        log.debug("Found east Ledge at {}", eastLedge.getWorldLocation());
                    }
                    return eastLedge;
                } else {
                    log.debug("Could not find east Ledge at expected location (3372, 2839)");
                    // Try to find any ledge on east side as fallback
                    List<TileObject> eastLedges = Rs2GameObject.getAll(obj -> 
                        obj.getId() == obstacleId && 
                        obj.getPlane() == playerPos.getPlane() &&
                        obj.getWorldLocation().getX() >= 3372 && obj.getWorldLocation().getX() <= 3373 &&
                        obj.getWorldLocation().getY() >= 2837 && obj.getWorldLocation().getY() <= 2841
                    );
                    if (!eastLedges.isEmpty()) {
                        return eastLedges.get(0);
                    }
                }
            }
            
            // Default behavior - look for middle ledge
            List<TileObject> obstacles = Rs2GameObject.getAll(obj -> 
                obj.getId() == obstacleId && 
                obj.getPlane() == playerPos.getPlane() &&
                obj.getWorldLocation().getX() < 3370 && // Exclude east side ledges
                obj.getWorldLocation().getY() >= 2840 && obj.getWorldLocation().getY() <= 2851 && // Middle Y range
                obj.getWorldLocation().distanceTo(playerPos) <= 20
            );
            
            // Log all ledges found for debugging
            if (log.isDebugEnabled()) {
                log.debug("Found {} potential ledges on floor 2:", obstacles.size());
                for (TileObject obj : obstacles) {
                    log.debug("  - Ledge at {}", obj.getWorldLocation());
                }
            }
            
            // Find the ledge closest to the expected position (3364, 2841)
            WorldPoint expectedLedgePos = new WorldPoint(3364, 2841, 2);
            TileObject bestLedge = obstacles.stream()
                .min((a, b) -> Integer.compare(
                    a.getWorldLocation().distanceTo(expectedLedgePos),
                    b.getWorldLocation().distanceTo(expectedLedgePos)
                ))
                .orElse(null);
                
            if (bestLedge != null) {
                if (log.isDebugEnabled()) {
                    log.debug("Selected ledge at {} (closest to expected position {})", 
                        bestLedge.getWorldLocation(), expectedLedgePos);
                }
                return bestLedge;
            } else {
                log.warn("No suitable ledge found on floor 2!");
                return null;
            }
        }
        // Special handling for plank end which is a ground object
        if (obstacleId == 10868) {
            List<GroundObject> groundObjects = Rs2GameObject.getGroundObjects();
            List<GroundObject> nearbyPlanks = new ArrayList<>();
            
            for (GroundObject go : groundObjects) {
                if (go.getId() == obstacleId && 
                    go.getPlane() == playerPos.getPlane() &&
                    go.getWorldLocation().distanceTo(playerPos) <= 15) {
                    nearbyPlanks.add(go);
                }
            }
            
            if (nearbyPlanks.isEmpty()) {
                log.debug("No plank ends (ground objects) found nearby");
                return null;
            }
            
            if (log.isDebugEnabled()) {
                log.debug("Found {} plank ends nearby", nearbyPlanks.size());
                for (GroundObject go : nearbyPlanks) {
                    log.debug("  - Plank end at {} (distance: {})", 
                        go.getWorldLocation(), go.getWorldLocation().distanceTo(playerPos));
                }
            }
            
            // Return closest plank end
            return nearbyPlanks.stream()
                .min((a, b) -> Integer.compare(
                    a.getWorldLocation().distanceTo(playerPos),
                    b.getWorldLocation().distanceTo(playerPos)
                ))
                .orElse(null);
        }
        
        // Normal game objects
        List<TileObject> obstacles = Rs2GameObject.getAll(obj -> 
            obj.getId() == obstacleId && 
            obj.getPlane() == playerPos.getPlane() &&
            obj.getWorldLocation().distanceTo(playerPos) <= 15
        );
        
        if (obstacles.isEmpty()) {
            return null;
        }
        
        // Log all found obstacles for debugging
        if (log.isDebugEnabled()) {
            log.debug("Found {} obstacles with ID {} on plane {}:", obstacles.size(), obstacleId, playerPos.getPlane());
            for (TileObject obj : obstacles) {
                log.debug("  - {} at {} (distance: {})", 
                    obstacleId, obj.getWorldLocation(), obj.getWorldLocation().distanceTo(playerPos));
            }
        }
        
        // For stairs on floor 1, we need to filter out the wrong stairs
        if (obstacleId == 10857 && playerPos.getPlane() == 1) {
            // If player just climbed up and is at start position (3354-3355, 2833), we should NOT return any stairs
            // The player should go to the low wall instead
            if (playerPos.getX() >= 3354 && playerPos.getX() <= 3355 && playerPos.getY() >= 2833 && playerPos.getY() <= 2835) {
                log.debug("Player just climbed to floor 1, should not interact with stairs yet");
                return null;
            }
            
            // Filter out stairs that are at the wrong location
            // The correct stairs to floor 2 are at (3356, 2831)
            obstacles = obstacles.stream()
                .filter(obj -> {
                    WorldPoint loc = obj.getWorldLocation();
                    // Only consider stairs in the southwest area of floor 1
                    return loc.getX() >= 3356 && loc.getX() <= 3360 && 
                           loc.getY() >= 2831 && loc.getY() <= 2833;
                })
                .collect(Collectors.toList());
                
            if (obstacles.isEmpty()) {
                log.debug("No appropriate stairs found for progression");
                return null;
            }
        }
        
        // For low wall on floor 1, make sure we get the north end
        if (obstacleId == 10865 && playerPos.getPlane() == 1 && 
            playerPos.getX() == 3354 && playerPos.getY() <= 2840) {
            // Sort by Y coordinate descending to get northernmost wall
            obstacles.sort((a, b) -> Integer.compare(
                b.getWorldLocation().getY(), 
                a.getWorldLocation().getY()
            ));
            
            // Return the northernmost low wall
            if (!obstacles.isEmpty()) {
                TileObject northWall = obstacles.get(0);
                if (log.isDebugEnabled()) {
                    log.debug("Selected northernmost low wall at {}", northWall.getWorldLocation());
                }
                return northWall;
            }
        }
        
        // Return closest reachable obstacle
        return obstacles.stream()
            .filter(this::isObstacleReachable)
            .min((a, b) -> Integer.compare(
                a.getWorldLocation().distanceTo(playerPos),
                b.getWorldLocation().distanceTo(playerPos)
            ))
            .orElse(obstacles.get(0));
    }
    
    private TileObject findNearestPyramidObstacle(WorldPoint playerPos) {
        List<Integer> pyramidObstacleIds = Arrays.asList(
            10857, 10865, 10860, 10867, 10868, 10859, 10882, 10886, 10884, 10861, 10888, 10851, 10855
        );
        
        // Special handling for floor 1 start position
        if (playerPos.getPlane() == 1 && playerPos.getX() >= 3354 && playerPos.getX() <= 3355 && playerPos.getY() >= 2833 && playerPos.getY() <= 2835) {
            // Player just climbed to floor 1, exclude stairs from search
            pyramidObstacleIds = Arrays.asList(
                10865, 10860, 10867, 10868, 10859, 10882, 10886, 10884, 10861, 10888, 10851, 10855
            );
            log.debug("Excluding stairs from search at floor 1 start position");
        }
        
        List<Integer> finalObstacleIds = pyramidObstacleIds;
        
        // First check for ground objects (plank ends)
        List<GroundObject> groundObjects = Rs2GameObject.getGroundObjects();
        for (GroundObject go : groundObjects) {
            if (go.getId() == 10868 && 
                go.getPlane() == playerPos.getPlane() &&
                go.getWorldLocation().distanceTo(playerPos) <= 15) {
                if (log.isDebugEnabled()) {
                    log.debug("Found nearby plank end (ground object) at {}", go.getWorldLocation());
                }
                return go;
            }
        }
        
        // Use longer search distance for floor 3
        int searchDistance = (playerPos.getPlane() == 3) ? 25 : 15;
        
        // Then check normal game objects
        List<TileObject> nearbyObstacles = Rs2GameObject.getAll(obj -> 
            finalObstacleIds.contains(obj.getId()) && 
            obj.getPlane() == playerPos.getPlane() &&
            obj.getWorldLocation().distanceTo(playerPos) <= searchDistance
        );
        
        if (nearbyObstacles.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("No pyramid obstacles found within {} tiles on plane {}", searchDistance, playerPos.getPlane());
            }
            // Try expanding search radius for floor 4 (pyramid top area)
            if (playerPos.getPlane() == 2 && playerPos.getX() >= 3040 && playerPos.getX() <= 3050) {
                if (log.isDebugEnabled()) {
                    log.debug("Expanding search for floor 4 pyramid top area...");
                }
                nearbyObstacles = Rs2GameObject.getAll(obj -> 
                    finalObstacleIds.contains(obj.getId()) && 
                    obj.getPlane() == playerPos.getPlane()
                );
            }
        }
        
        if (log.isDebugEnabled()) {
            log.debug("Found {} pyramid obstacles nearby:", nearbyObstacles.size());
            for (TileObject obj : nearbyObstacles) {
                log.debug("  - ID {} at {} (distance: {})", 
                    obj.getId(), obj.getWorldLocation(), obj.getWorldLocation().distanceTo(playerPos));
            }
        }
        
        return nearbyObstacles.stream()
            .filter(obj -> isObstacleReachable(obj))
            .min((a, b) -> Integer.compare(
                a.getWorldLocation().distanceTo(playerPos),
                b.getWorldLocation().distanceTo(playerPos)
            ))
            .orElse(null);
    }
    
    private boolean isObstacleReachable(TileObject obstacle) {
        if (obstacle instanceof GameObject) {
            GameObject go = (GameObject) obstacle;
            return Rs2GameObject.canReach(go.getWorldLocation(), go.sizeX() + 2, go.sizeY() + 2, 4, 4);
        } else if (obstacle instanceof GroundObject) {
            return Rs2GameObject.canReach(obstacle.getWorldLocation(), 2, 2);
        } else if (obstacle instanceof WallObject) {
            return Rs2GameObject.canReach(obstacle.getWorldLocation(), 1, 1);
        } else {
            return Rs2GameObject.canReach(obstacle.getWorldLocation(), 2, 2);
        }
    }
    
    @Override
    public boolean handleWalkToStart(WorldPoint playerLocation) {
        // Only walk to start if on ground level
        if (playerLocation.getPlane() == 0) {
            // Check if we should handle pyramid turn-in instead of walking to start
            int pyramidCount = Rs2Inventory.count(ItemID.PYRAMID_TOP);
            boolean shouldTurnIn = pyramidCount > 0 && (Rs2Inventory.isFull() || pyramidCount >= state.getPyramidTurnInThreshold());
            
            if (shouldTurnIn) {
                if (!state.isHandlingPyramidTurnIn()) {
                    if (log.isDebugEnabled()) {
                        if (Rs2Inventory.isFull()) {
                            log.debug("Inventory is full with {} pyramid tops - going to Simon instead of pyramid start", pyramidCount);
                        } else {
                            log.debug("Reached threshold of {} pyramids (have {}) - going to Simon instead of pyramid start", 
                                state.getPyramidTurnInThreshold(), pyramidCount);
                        }
                    }
                    state.startPyramidTurnIn();
                }
                // Handle turn-in instead of walking to start
                handlePyramidTurnIn();
                return true; // Return true to prevent other actions
            }
            
            int distanceToStart = playerLocation.distanceTo(START_POINT);
            if (distanceToStart > 3) {
                // Try to directly click on the pyramid stairs if visible AND reachable
                List<TileObject> stairsCandidates = Rs2GameObject.getAll(obj ->
                    obj.getId() == 10857 &&
                    obj.getPlane() == playerLocation.getPlane() &&
                    obj.getWorldLocation().distanceTo(playerLocation) <= 10 &&
                    obj.getWorldLocation().distanceTo(START_POINT) <= 2 &&
                    Rs2GameObject.canReach(obj.getWorldLocation())
                );
                if (!stairsCandidates.isEmpty()) {
                    TileObject pyramidStairs = stairsCandidates.stream()
                        .min(Comparator.comparingInt(obj -> obj.getWorldLocation().distanceTo(playerLocation)))
                        .orElse(null);
                    if (pyramidStairs != null) {
                        log.debug("Clicking directly on pyramid stairs (reachable from current position)");
                        if (Rs2GameObject.interact(pyramidStairs)) {
                            Global.sleep(600, 800); // Small delay after clicking
                            return true;
                        }
                    }
                }
                
                // Can't reach stairs directly (e.g., coming from Simon with climbing rocks in the way)
                // Use Rs2Walker to navigate around obstacles
                if (log.isDebugEnabled()) {
                    log.debug("Walking to pyramid start point - stairs not reachable directly (distance: {})", distanceToStart);
                }
                Rs2Walker.walkTo(START_POINT, 2);
                return true;
            }
        }
        return false;
    }
    
    @Override
    public boolean waitForCompletion(int agilityExp, int plane) {
        // Mark that we've started an obstacle
        state.recordObstacleStart();
        
        // Note: The flags state.isDoingCrossGap() and state.isDoingXpObstacle() 
        // are set by getCurrentObstacle() and should remain set during this wait
        
        // Simplified wait logic using XP drops as primary signal
        double initialHealth = Rs2Player.getHealthPercentage();
        int timeoutMs = 8000; // 8 second timeout
        final long startTime = System.currentTimeMillis();
        
        // Track XP gains
        int lastKnownXp = agilityExp;
        boolean receivedXp = false;
        boolean hitByStoneBlock = false;
        
        // Track starting position
        WorldPoint startPos = Rs2Player.getWorldLocation();
        
        // Check if we're at the climbing rocks position (pyramid collection)
        boolean isClimbingRocksForPyramid = startPos.getPlane() == 3 && 
            startPos.getX() >= 3042 && startPos.getX() <= 3043 &&
            startPos.getY() >= 4697 && startPos.getY() <= 4698;
        
        if (log.isDebugEnabled()) {
            log.debug("Starting obstacle at {}, initial XP: {}", startPos, agilityExp);
            log.debug("Flags: CrossGap={}, XpObstacle={}", state.isDoingCrossGap(), state.isDoingXpObstacle());
        }
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            int currentXp = Microbot.getClient().getSkillExperience(Skill.AGILITY);
            int currentPlane = Microbot.getClient().getTopLevelWorldView() != null
                ? Microbot.getClient().getTopLevelWorldView().getPlane()
                : Rs2Player.getWorldLocation().getPlane();
            double currentHealth = Rs2Player.getHealthPercentage();
            WorldPoint currentPos = Rs2Player.getWorldLocation();
            
            // Special case: Climbing rocks for pyramid collection (no XP)
            if (isClimbingRocksForPyramid) {
                if (!Rs2Player.isMoving() && !Rs2Player.isAnimating() && System.currentTimeMillis() - startTime > 1500) {
                    log.debug("Climbing rocks action completed");
                    state.recordClimbingRocks();
                    // Clear any flags that might have been set
                    if (state.isDoingXpObstacle()) {
                        log.debug("WARNING: Clearing XP obstacle flag from climbing rocks path");
                        state.clearXpObstacle();
                    }
                    if (state.isDoingCrossGap()) {
                        state.clearCrossGap();
                    }
                    Global.sleep(300, 400);
                    return true;
                }
                Global.sleep(50);
                continue;
            }
            
            // Check for XP gain
            if (currentXp != lastKnownXp) {
                int xpGained = currentXp - lastKnownXp;
                
                // Check if this is a stone block (12 XP)
                if (xpGained == 12) {
                    log.debug("Hit by stone block (12 XP) - clearing flags to allow immediate retry");
                    hitByStoneBlock = true;
                    lastKnownXp = currentXp;
                    
                    // Clear flags to allow immediate retry of the obstacle
                    if (state.isDoingCrossGap()) {
                        state.clearCrossGap();
                    }
                    if (state.isDoingXpObstacle()) {
                        state.clearXpObstacle();
                    }
                    
                    // Return immediately to retry the obstacle
                    Global.sleep(300, 400); // Small delay before retry
                    return true;
                }
                
                // Any other XP gain means obstacle is complete (for XP-granting obstacles)
                if (log.isDebugEnabled()) {
                    log.debug("Received {} XP - obstacle complete!", xpGained);
                }
                receivedXp = true;
                lastKnownXp = currentXp;
                
                // Check if this was a Cross Gap obstacle
                boolean wasCrossGap = state.isDoingCrossGap();
                
                // For Cross Gap, ensure minimum time has passed even with XP
                if (wasCrossGap && System.currentTimeMillis() - startTime < 3500) {
                    long waitTime = 3500 - (System.currentTimeMillis() - startTime);
                    if (log.isDebugEnabled()) {
                        log.debug("Cross Gap - waiting additional {}ms for minimum duration", waitTime);
                    }
                    Global.sleep((int)waitTime);
                }
                
                // Clear flags since we received XP
                if (state.isDoingCrossGap()) {
                    log.debug("Cross Gap completed with XP - clearing flag");
                    state.clearCrossGap();
                }
                if (state.isDoingXpObstacle()) {
                    log.debug("XP obstacle completed - clearing flag");
                    state.clearXpObstacle();
                }
                
                // Add delay to ensure animation finishes
                // Cross Gap needs longer delay even after XP
                if (wasCrossGap) {
                    log.debug("Cross Gap - waiting longer for animation to fully complete");
                    Global.sleep(800, 1000);
                } else {
                    Global.sleep(200, 300);
                }
                return true;
            }
            
            // Quick checks for other completion conditions
            
            // Plane change (stairs/doorway)
            if (currentPlane != plane) {
                log.debug("Plane changed - obstacle complete");
                // Clear flags when plane changes
                if (state.isDoingCrossGap()) {
                    log.debug("Clearing Cross Gap flag due to plane change");
                    state.clearCrossGap();
                }
                if (state.isDoingXpObstacle()) {
                    log.debug("Clearing XP obstacle flag due to plane change");
                    state.clearXpObstacle();
                }
                Global.sleep(200, 300);
                return true;
            }
            
            // Health loss (failed obstacle)
            if (currentHealth < initialHealth) {
                log.debug("Failed obstacle (lost health)");
                // Clear flags if we failed
                if (state.isDoingCrossGap()) {
                    state.clearCrossGap();
                }
                if (state.isDoingXpObstacle()) {
                    state.clearXpObstacle();
                }
                return true;
            }
            
            // For non-XP obstacles (stairs, doorway), check if not moving/animating
            // Only check after at least 1 second to allow obstacle to start
            if (System.currentTimeMillis() - startTime > 1000) {
                // If we haven't received XP and are not moving/animating, check if we moved
                if (!receivedXp && !Rs2Player.isMoving() && !Rs2Player.isAnimating()) {
                    int distanceMoved = currentPos.distanceTo(startPos);
                    
                    // Special handling for Cross Gap - ALWAYS wait for XP or timeout, never complete on movement
                    if (state.isDoingCrossGap()) {
                        // Cross Gap must wait for XP drop or full timeout
                        // Never complete based on movement or animation state
                        continue; // Always continue waiting for Cross Gap
                    }
                    
                    // If we're expecting XP (flag is set), don't complete based on movement alone
                    if (state.isDoingXpObstacle()) {
                        
                        // For non-Cross-Gap XP obstacles, use normal logic
                        // Keep waiting for XP - don't complete based on movement
                        if (System.currentTimeMillis() - startTime < 4000) {
                            continue; // Keep waiting for XP
                        }
                        // After 4 seconds without XP, check if we at least moved
                        if (distanceMoved >= 3) {
                            if (log.isDebugEnabled()) {
                                log.debug("WARNING: Expected XP but didn't receive it after 4s - completing based on movement");
                                log.debug("Cross Gap flag state before returning: {}", state.isDoingCrossGap());
                                log.debug("XP obstacle flag state before returning: {}", state.isDoingXpObstacle());
                            }
                            // Clear XP obstacle flag but NOT Cross Gap flag
                            // Cross Gap needs to wait for XP regardless of movement
                            state.clearXpObstacle();
                            if (log.isDebugEnabled()) {
                                log.debug("After clearing XP flag - Cross Gap: {}, XP obstacle: {}", 
                                    state.isDoingCrossGap(), state.isDoingXpObstacle());
                            }
                            return true;
                        }
                    }
                    
                    // For non-XP obstacles, movement indicates completion
                    if (distanceMoved >= 3 && !state.isDoingXpObstacle()) {
                        if (log.isDebugEnabled()) {
                            log.debug("Non-XP obstacle complete (moved {} tiles)", distanceMoved);
                        }
                        
                        // Note: We don't clear Cross Gap or XP obstacle flags here
                        // They should only be cleared by XP receipt or timeout
                        
                        Global.sleep(300, 400);
                        return true;
                    }
                    
                    // If we were hit by stone block and haven't received proper XP, retry
                    if (hitByStoneBlock && !receivedXp && System.currentTimeMillis() - startTime > 2000) {
                        log.debug("Stone block interrupted obstacle, no proper XP received - retrying");
                        // Clear flags since we're going to retry
                        if (state.isDoingCrossGap()) {
                            log.debug("Clearing Cross Gap flag for retry");
                            state.clearCrossGap();
                        }
                        if (state.isDoingXpObstacle()) {
                            log.debug("Clearing XP obstacle flag for retry");
                            state.clearXpObstacle();
                        }
                        Global.sleep(800, 1200);
                        return false; // Retry the obstacle
                    }
                }
            }
            
            Global.sleep(50);
        }
        
        // Timeout reached
        if (log.isDebugEnabled()) {
            log.debug("Timeout after {}ms - checking if made progress", timeoutMs);
        }
        int distanceMoved = Rs2Player.getWorldLocation().distanceTo(startPos);
        
        // Clear flags on timeout
        if (state.isDoingCrossGap()) {
            log.debug("Clearing Cross Gap flag due to timeout");
            state.clearCrossGap();
        }
        if (state.isDoingXpObstacle()) {
            log.debug("Clearing XP obstacle flag due to timeout");
            state.clearXpObstacle();
        }
        
        // If we received XP or moved significantly, consider it successful
        if (receivedXp || distanceMoved >= 3) {
            if (log.isDebugEnabled()) {
                log.debug("Made progress despite timeout (XP: {}, moved: {} tiles)", receivedXp, distanceMoved);
            }
            return true;
        }
        
        log.debug("No progress made - will retry");
        return false;
    }
    
    @Override
    public Integer getRequiredLevel() {
        return 30;
    }
    
    @Override
    public boolean canBeBoosted() {
        return true;
    }
    
    @Override
    public int getLootDistance() {
        return 5; // Pyramid tops can be a bit further away
    }
    
    private boolean handlePyramidTurnIn() {
        try {
            // Check if we still have pyramid tops
            if (!Rs2Inventory.contains(ItemID.PYRAMID_TOP)) {
                log.debug("No pyramid tops found in inventory - returning to course");
                state.clearPyramidTurnIn();
                return false;
            }
            
            // Try to find Simon
            NPC simon = Rs2Npc.getNpc(SIMON_NAME);
            
            // If Simon is found and reachable, use pyramid top on him
            if (simon != null && Rs2GameObject.canReach(simon.getWorldLocation())) {
                log.debug("Simon found and reachable, using pyramid top");
                
                // Handle dialogue first if already in dialogue
                if (Rs2Dialogue.isInDialogue()) {
                    // Continue through dialogue
                    if (Rs2Dialogue.hasContinue()) {
                        Rs2Dialogue.clickContinue();
                        Global.sleep(600, 1000);
                        return true;
                    }
                    
                    // Select option to claim reward if available
                    if (Rs2Dialogue.hasDialogueOption("I've got some pyramid tops for you.")) {
                        Rs2Dialogue.clickOption("I've got some pyramid tops for you.");
                        Global.sleep(600, 1000);
                        return true;
                    }
                } else {
                    // Not in dialogue, use pyramid top on Simon
                    boolean used = Rs2Inventory.useItemOnNpc(ItemID.PYRAMID_TOP, simon);
                    if (used) {
                        log.debug("Successfully used pyramid top on Simon");
                        Global.sleepUntil(() -> Rs2Dialogue.isInDialogue(), 3000);
                    } else {
                        log.debug("Failed to use pyramid top on Simon");
                    }
                }
                return true;
            }
            
            // Simon not found or not reachable, walk to him
            if (log.isDebugEnabled()) {
                log.debug("Simon not found or not reachable, walking to location {}", SIMON_LOCATION);
            }
            Rs2Walker.walkTo(SIMON_LOCATION, 2);
            Rs2Player.waitForWalking();
            
            // Check if we've completed the turn-in (no pyramids left and not in dialogue)
            if (!Rs2Inventory.contains(ItemID.PYRAMID_TOP) && !Rs2Dialogue.isInDialogue()) {
                log.debug("Pyramid tops turned in successfully");
                state.clearPyramidTurnIn();
                
                // Walk back towards the pyramid start
                WorldPoint currentPos = Rs2Player.getWorldLocation();
                if (currentPos.distanceTo(START_POINT) > 10) {
                    log.debug("Walking back to pyramid start");
                    Rs2Walker.walkTo(START_POINT);
                }
                return false; // Done with turn-in, can resume obstacles
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("Error in handlePyramidTurnIn", e);
            state.clearPyramidTurnIn();
            return false;
        }
    }
    
    /**
     * Checks for empty waterskins in inventory and drops them
     * @return true if waterskins were dropped, false otherwise
     */
    private boolean handleEmptyWaterskins() {
        if (Rs2Inventory.contains(ItemID.WATERSKIN0)) {
            log.debug("Found empty waterskin(s), dropping them");
            Rs2Inventory.drop(ItemID.WATERSKIN0);
            Global.sleep(300, 500);
            return true;
        }
        return false;
    }
    
}