package net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.handlers;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.model.HazardData;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.trackers.Rs2HazardTracker;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

/**
 * thread-safe handler for PvM movement, hazard avoidance, and safe tile calculation
 * integrates with Rs2HazardTracker to detect dangerous locations
 * supports moving hazards (tornadoes) and static hazards (floor tiles)
 * handles ALL PvM movement including dodging, pathfinding, and positioning
 */
@Slf4j
@Singleton
public class Rs2MovementHandler {

    private static Rs2MovementHandler instance;

    @Inject
    private Rs2HazardTracker hazardTracker;

    // default search radius for safe tiles
    private int defaultSearchRadius = 10;

    private Rs2MovementHandler() {
    }

    public static synchronized Rs2MovementHandler getInstance() {
        if (instance == null) {
            instance = new Rs2MovementHandler();
        }
        return instance;
    }

    /**
     * check if player is standing on hazard
     */
    public boolean isPlayerOnHazard() {
        if (hazardTracker == null) {
            return false;
        }

        WorldPoint playerLoc = Microbot.getClient().getLocalPlayer().getWorldLocation();
        return hazardTracker.isLocationDangerous(playerLoc);
    }

    /**
     * PREDICTIVE: check if player location will be dangerous within specified ticks
     * critical for moving hazards (tornadoes) - dodge BEFORE they reach you!
     *
     * @param ticksAhead how many ticks into the future to check (1-3 recommended)
     * @return true if player location will be dangerous in the future
     */
    public boolean willPlayerBeInDanger(int ticksAhead) {
        if (hazardTracker == null) {
            return false;
        }

        WorldPoint playerLoc = Microbot.getClient().getLocalPlayer().getWorldLocation();
        return willLocationBeDangerous(playerLoc, ticksAhead);
    }

    /**
     * PREDICTIVE: check if location will be dangerous at future tick
     * considers moving hazard trajectories (e.g., Hunllef tornadoes)
     *
     * @param location the location to check
     * @param ticksAhead how many ticks into the future to predict (1-3 recommended)
     * @return true if location will be dangerous at that future tick
     */
    public boolean willLocationBeDangerous(WorldPoint location, int ticksAhead) {
        if (hazardTracker == null) {
            return false;
        }

        int futureTick = Microbot.getClient().getTickCount() + ticksAhead;

        // check all hazards for predicted danger
        return hazardTracker.stream()
            .filter(HazardData::isActive)
            .anyMatch(hazard -> hazard.willBeDangerousAt(location, futureTick));
    }

    /**
     * check if location is safe (no hazards within danger radius)
     */
    public boolean isSafeLocation(WorldPoint location) {
        if (hazardTracker == null) {
            return true; // assume safe if no tracker
        }

        return !hazardTracker.isLocationDangerous(location);
    }

    /**
     * PREDICTIVE: check if location will remain safe for specified duration
     * ensures tile won't become dangerous from moving hazards
     *
     * @param location the location to check
     * @param ticksAhead how many ticks into the future to verify safety (1-3 recommended)
     * @return true if location is safe now AND will remain safe for duration
     */
    public boolean willLocationRemainSafe(WorldPoint location, int ticksAhead) {
        if (hazardTracker == null) {
            return true; // assume safe if no tracker
        }

        // check if currently dangerous
        if (hazardTracker.isLocationDangerous(location)) {
            return false;
        }

        // check each future tick
        int currentTick = Microbot.getClient().getTickCount();
        for (int i = 1; i <= ticksAhead; i++) {
            int futureTick = currentTick + i;
            boolean dangerous = hazardTracker.stream()
                .filter(HazardData::isActive)
                .anyMatch(hazard -> hazard.willBeDangerousAt(location, futureTick));

            if (dangerous) {
                return false; // will become dangerous at tick i
            }
        }

        return true; // safe for entire duration
    }

    /**
     * check if player is standing under boss/NPC
     * critical for Hunllef stomp attack avoidance
     */
    public boolean isPlayerUnderNpc(WorldPoint npcLocation) {
        if (npcLocation == null) {
            return false;
        }

        WorldPoint playerLoc = Microbot.getClient().getLocalPlayer().getWorldLocation();
        return playerLoc.equals(npcLocation);
    }

    /**
     * check if should avoid melee distance from boss
     * prevents stomp attacks (Hunllef) and other melee mechanics
     */
    public boolean shouldAvoidMelee(WorldPoint bossLocation) {
        if (bossLocation == null) {
            return false;
        }

        WorldPoint playerLoc = Microbot.getClient().getLocalPlayer().getWorldLocation();

        // check if standing under boss (stomp danger!)
        if (playerLoc.equals(bossLocation)) {
            return true;
        }

        // check if in melee distance (1 tile)
        return playerLoc.distanceTo(bossLocation) <= 1;
    }

    /**
     * get all safe tiles within attack range of target
     * useful for maintaining DPS while dodging
     */
    public List<WorldPoint> getSafeTilesInRange(WorldPoint target, int attackRange) {
        if (hazardTracker == null) {
            return new ArrayList<>();
        }

        WorldPoint playerLoc = Microbot.getClient().getLocalPlayer().getWorldLocation();
        List<WorldPoint> safeTiles = new ArrayList<>();

        // search in a square around player
        for (int dx = -attackRange; dx <= attackRange; dx++) {
            for (int dy = -attackRange; dy <= attackRange; dy++) {
                WorldPoint tile = playerLoc.dx(dx).dy(dy);

                // must be in attack range of target
                if (tile.distanceTo(target) <= attackRange) {
                    // must not be dangerous
                    if (!hazardTracker.isLocationDangerous(tile)) {
                        safeTiles.add(tile);
                    }
                }
            }
        }

        return safeTiles;
    }

    /**
     * get all safe tiles within search radius
     */
    public List<WorldPoint> getSafeTiles() {
        return getSafeTiles(defaultSearchRadius);
    }

    /**
     * get all safe tiles within custom search radius
     */
    public List<WorldPoint> getSafeTiles(int searchRadius) {
        if (hazardTracker == null) {
            return new ArrayList<>();
        }

        WorldPoint playerLoc = Microbot.getClient().getLocalPlayer().getWorldLocation();
        List<WorldPoint> safeTiles = new ArrayList<>();

        // search in a square around player
        for (int dx = -searchRadius; dx <= searchRadius; dx++) {
            for (int dy = -searchRadius; dy <= searchRadius; dy++) {
                WorldPoint tile = playerLoc.dx(dx).dy(dy);

                // must not be dangerous
                if (!hazardTracker.isLocationDangerous(tile)) {
                    safeTiles.add(tile);
                }
            }
        }

        return safeTiles;
    }

    /**
     * PREDICTIVE: get safe tiles that will remain safe for specified duration
     * critical for avoiding moving hazards like Hunllef tornadoes
     *
     * @param ticksAhead how many ticks tile must remain safe (1-3 recommended)
     * @return list of tiles safe now and for future ticks
     */
    public List<WorldPoint> getPredictiveSafeTiles(int ticksAhead) {
        return getPredictiveSafeTiles(defaultSearchRadius, ticksAhead);
    }

    /**
     * PREDICTIVE: get safe tiles with custom search radius that will remain safe
     *
     * @param searchRadius radius to search around player
     * @param ticksAhead how many ticks tile must remain safe (1-3 recommended)
     * @return list of tiles safe now and for future ticks
     */
    public List<WorldPoint> getPredictiveSafeTiles(int searchRadius, int ticksAhead) {
        if (hazardTracker == null) {
            return new ArrayList<>();
        }

        WorldPoint playerLoc = Microbot.getClient().getLocalPlayer().getWorldLocation();
        List<WorldPoint> safeTiles = new ArrayList<>();

        // search in a square around player
        for (int dx = -searchRadius; dx <= searchRadius; dx++) {
            for (int dy = -searchRadius; dy <= searchRadius; dy++) {
                WorldPoint tile = playerLoc.dx(dx).dy(dy);

                // tile must be safe NOW and remain safe for duration
                if (willLocationRemainSafe(tile, ticksAhead)) {
                    safeTiles.add(tile);
                }
            }
        }

        return safeTiles;
    }

    /**
     * get nearest safe tile to player
     */
    public Optional<WorldPoint> getNearestSafeTile() {
        List<WorldPoint> safeTiles = getSafeTiles();
        if (safeTiles.isEmpty()) {
            return Optional.empty();
        }

        WorldPoint playerLoc = Microbot.getClient().getLocalPlayer().getWorldLocation();

        // sort by distance to player
        return safeTiles.stream()
            .min(Comparator.comparingInt(tile -> tile.distanceTo(playerLoc)));
    }

    /**
     * PREDICTIVE: get nearest safe tile that will remain safe for duration
     * use this instead of getNearestSafeTile() for moving hazards!
     *
     * @param ticksAhead how many ticks tile must remain safe (1-3 recommended)
     * @return nearest tile safe now and for future ticks
     */
    public Optional<WorldPoint> getPredictiveNearestSafeTile(int ticksAhead) {
        List<WorldPoint> safeTiles = getPredictiveSafeTiles(ticksAhead);
        if (safeTiles.isEmpty()) {
            return Optional.empty();
        }

        WorldPoint playerLoc = Microbot.getClient().getLocalPlayer().getWorldLocation();

        // sort by distance to player
        return safeTiles.stream()
            .min(Comparator.comparingInt(tile -> tile.distanceTo(playerLoc)));
    }

    /**
     * get nearest safe tile that maintains attack range to target
     */
    public Optional<WorldPoint> getNearestSafeTileInRange(WorldPoint target, int attackRange) {
        List<WorldPoint> safeTiles = getSafeTilesInRange(target, attackRange);
        if (safeTiles.isEmpty()) {
            return Optional.empty();
        }

        WorldPoint playerLoc = Microbot.getClient().getLocalPlayer().getWorldLocation();

        // sort by distance to player
        return safeTiles.stream()
            .min(Comparator.comparingInt(tile -> tile.distanceTo(playerLoc)));
    }

    /**
     * get safe tile farthest from ALL hazards
     * useful for tornado dodging (Hunllef) where you want maximum distance
     * prioritizes distance from hazards over proximity to player
     */
    public Optional<WorldPoint> getFarthestFromHazards() {
        List<WorldPoint> safeTiles = getSafeTiles();
        if (safeTiles.isEmpty()) {
            return Optional.empty();
        }

        if (hazardTracker == null) {
            return getNearestSafeTile(); // fallback if no hazards
        }

        List<HazardData> activeHazards = hazardTracker.getActiveHazards();
        if (activeHazards.isEmpty()) {
            return getNearestSafeTile(); // no hazards, just get nearest
        }

        // find tile with maximum minimum distance to any hazard
        return safeTiles.stream()
            .max(Comparator.comparingDouble(tile -> {
                // calculate minimum distance to any hazard
                double minDistanceToHazard = activeHazards.stream()
                    .mapToDouble(hazard -> tile.distanceTo(hazard.getLocation()))
                    .min()
                    .orElse(Double.MAX_VALUE);
                return minDistanceToHazard;
            }));
    }

    /**
     * get safe tile farthest from MOVING hazards only
     * specifically for tornado/moving hazard dodging (Hunllef, Zulrah gas)
     * ignores static hazards like floor tiles
     */
    public Optional<WorldPoint> getFarthestFromMovingHazards() {
        List<WorldPoint> safeTiles = getSafeTiles();
        if (safeTiles.isEmpty()) {
            return Optional.empty();
        }

        if (hazardTracker == null) {
            return getNearestSafeTile(); // fallback
        }

        // filter only moving hazards
        List<HazardData> movingHazards = hazardTracker.getActiveHazards().stream()
            .filter(HazardData::isMoving)
            .collect(java.util.stream.Collectors.toList());

        if (movingHazards.isEmpty()) {
            return getNearestSafeTile(); // no moving hazards
        }

        // find tile with maximum minimum distance to any moving hazard
        return safeTiles.stream()
            .max(Comparator.comparingDouble(tile -> {
                // calculate minimum distance to any moving hazard
                double minDistanceToMovingHazard = movingHazards.stream()
                    .mapToDouble(hazard -> tile.distanceTo(hazard.getLocation()))
                    .min()
                    .orElse(Double.MAX_VALUE);
                return minDistanceToMovingHazard;
            }));
    }

    /**
     * PREDICTIVE: get safe tile farthest from moving hazards at future tick
     * considers where moving hazards will be, not just where they are now
     * critical for fast-moving hazards like Hunllef tornadoes
     *
     * @param ticksAhead how many ticks ahead to check hazard positions
     * @return tile farthest from predicted hazard positions
     */
    public Optional<WorldPoint> getPredictiveFarthestFromMovingHazards(int ticksAhead) {
        List<WorldPoint> safeTiles = getPredictiveSafeTiles(ticksAhead);
        if (safeTiles.isEmpty()) {
            return Optional.empty();
        }

        if (hazardTracker == null) {
            return getNearestSafeTile(); // fallback
        }

        // filter only moving hazards
        List<HazardData> movingHazards = hazardTracker.getActiveHazards().stream()
            .filter(HazardData::isMoving)
            .collect(java.util.stream.Collectors.toList());

        if (movingHazards.isEmpty()) {
            return getPredictiveNearestSafeTile(ticksAhead);
        }

        int futureTick = Microbot.getClient().getTickCount() + ticksAhead;

        // find tile with maximum distance from predicted hazard positions
        return safeTiles.stream()
            .max(Comparator.comparingDouble(tile -> {
                // calculate minimum distance to any hazard at future tick
                double minDistanceToPredictedHazard = movingHazards.stream()
                    .mapToDouble(hazard -> {
                        // get predicted position at future tick
                        WorldPoint predictedPos = hazard.getPredictedLocation(futureTick);
                        return predictedPos != null ?
                            tile.distanceTo(predictedPos) :
                            tile.distanceTo(hazard.getLocation());
                    })
                    .min()
                    .orElse(Double.MAX_VALUE);
                return minDistanceToPredictedHazard;
            }));
    }

    /**
     * get best safe spot using scoring system
     * considers ideal distance from NPCs, distance from hazards, and player proximity
     */
    public Optional<WorldPoint> getBestSafeSpot() {
        return getBestSafeSpot(7, null); // default ideal distance 7 (ranged)
    }

    /**
     * get best safe spot with custom ideal NPC distance
     * @param idealNpcDistance ideal distance from dangerous NPCs (e.g., 7 for ranged, 1 for melee)
     * @param target optional target NPC to maintain distance from
     */
    public Optional<WorldPoint> getBestSafeSpot(int idealNpcDistance, WorldPoint target) {
        List<WorldPoint> safeTiles = getSafeTiles();
        if (safeTiles.isEmpty()) {
            return Optional.empty();
        }

        WorldPoint playerLoc = Microbot.getClient().getLocalPlayer().getWorldLocation();

        // find tile with highest score
        return safeTiles.stream()
            .max(Comparator.comparingDouble(tile ->
                calculateTileScore(tile, playerLoc, idealNpcDistance, target)));
    }

    /**
     * calculate safety score for a tile
     * higher score = safer and more optimal position
     *
     * scoring breakdown:
     * - NPC distance (20pts): prefer ideal distance from target
     * - Player proximity (15pts): prefer closer tiles (less movement)
     * - Moving hazard distance (30pts): maximize distance from moving hazards
     * - Static hazard distance (20pts): avoid static hazards
     *
     * @param tile the tile to score
     * @param playerPos current player position
     * @param idealNpcDistance ideal distance from target NPC
     * @param target target NPC location (null if no specific target)
     * @return score (0-85+, higher is better)
     */
    public double calculateTileScore(WorldPoint tile, WorldPoint playerPos, int idealNpcDistance, WorldPoint target) {
        double score = 0;

        // 1. NPC distance (20pts) - prefer tiles at ideal distance
        if (target != null && idealNpcDistance > 0) {
            double npcDistance = tile.distanceTo(target);
            score += 20 * (1 - Math.abs(npcDistance - idealNpcDistance) / idealNpcDistance);
        }

        // 2. player proximity (15pts) - prefer closer tiles to minimize movement
        double playerDistance = tile.distanceTo(playerPos);
        score += 15 * (1 - Math.min(playerDistance / 10, 1));

        // 3. moving hazard distance (30pts) - maximize distance from moving hazards
        if (hazardTracker != null) {
            List<HazardData> movingHazards = hazardTracker.getActiveHazards().stream()
                .filter(HazardData::isMoving)
                .collect(java.util.stream.Collectors.toList());

            if (!movingHazards.isEmpty()) {
                double minHazardDistance = movingHazards.stream()
                    .mapToDouble(hazard -> tile.distanceTo(hazard.getLocation()))
                    .min()
                    .orElse(Double.MAX_VALUE);

                score += 30 * Math.min(minHazardDistance / 5, 1);
            } else {
                // no moving hazards - give full points
                score += 30;
            }
        }

        // 4. static hazard distance (20pts) - avoid static hazards
        if (hazardTracker != null) {
            List<HazardData> staticHazards = hazardTracker.getActiveHazards().stream()
                .filter(hazard -> !hazard.isMoving())
                .collect(java.util.stream.Collectors.toList());

            if (!staticHazards.isEmpty()) {
                double minStaticDistance = staticHazards.stream()
                    .mapToDouble(hazard -> tile.distanceTo(hazard.getLocation()))
                    .min()
                    .orElse(Double.MAX_VALUE);

                score += 20 * Math.min(minStaticDistance / 3, 1);
            } else {
                // no static hazards - give full points
                score += 20;
            }
        }

        return Math.max(0, score);
    }

    /**
     * get all safe tiles with their scores
     * useful for debugging and visualization
     */
    public Map<WorldPoint, Double> getSafeTileScores(int idealNpcDistance, WorldPoint target) {
        List<WorldPoint> safeTiles = getSafeTiles();
        WorldPoint playerLoc = Microbot.getClient().getLocalPlayer().getWorldLocation();

        Map<WorldPoint, Double> scores = new HashMap<>();
        for (WorldPoint tile : safeTiles) {
            double score = calculateTileScore(tile, playerLoc, idealNpcDistance, target);
            scores.put(tile, score);
        }

        return scores;
    }

    /**
     * dodge to nearest safe tile
     * returns true if found safe tile and started walking
     */
    public boolean dodgeToSafeTile() {
        Optional<WorldPoint> safeTile = getNearestSafeTile();
        if (safeTile.isEmpty()) {
            log.warn("No safe tiles found within search radius {}", defaultSearchRadius);
            return false;
        }

        WorldPoint destination = safeTile.get();
        Rs2Walker.walkTo(destination);
        log.debug("Dodging to safe tile: {}", destination);

        return true;
    }

    /**
     * dodge to nearest safe tile while maintaining attack range
     * useful for bosses where you need to stay in range
     */
    public boolean dodgeToSafeTileInRange(WorldPoint target, int attackRange) {
        Optional<WorldPoint> safeTile = getNearestSafeTileInRange(target, attackRange);
        if (safeTile.isEmpty()) {
            log.warn("No safe tiles found within attack range {} of target", attackRange);
            // fallback to any safe tile
            return dodgeToSafeTile();
        }

        WorldPoint destination = safeTile.get();
        Rs2Walker.walkTo(destination);
        log.debug("Dodging to safe tile in range: {}", destination);

        return true;
    }

    /**
     * get closest hazard to player
     * useful for determining which hazard is most threatening
     */
    public Optional<HazardData> getClosestHazard() {
        if (hazardTracker == null) {
            return Optional.empty();
        }

        WorldPoint playerLoc = Microbot.getClient().getLocalPlayer().getWorldLocation();
        return hazardTracker.getClosestHazard(playerLoc);
    }

    /**
     * get all hazards near player (within distance)
     */
    public List<HazardData> getHazardsNearPlayer(int distance) {
        if (hazardTracker == null) {
            return new ArrayList<>();
        }

        WorldPoint playerLoc = Microbot.getClient().getLocalPlayer().getWorldLocation();
        return hazardTracker.getHazardsNear(playerLoc, distance);
    }

    /**
     * check if should dodge now
     * considers if player is on hazard or hazard is approaching
     */
    public boolean shouldDodgeNow() {
        if (!isPlayerOnHazard()) {
            return false;
        }

        // check if already moving
        if (Rs2Player.isMoving()) {
            // already dodging, check if moving to safe location
            WorldPoint destination = Rs2Player.getWorldLocation();
            if (isSafeLocation(destination)) {
                return false; // already moving to safe tile
            }
        }

        return true;
    }

    /**
     * auto-dodge if standing on hazard
     * returns true if dodged or already safe
     */
    public boolean autoDodge() {
        if (!shouldDodgeNow()) {
            return true; // already safe
        }

        return dodgeToSafeTile();
    }

    /**
     * auto-dodge while maintaining attack range
     */
    public boolean autoDodgeInRange(WorldPoint target, int attackRange) {
        if (!shouldDodgeNow()) {
            return true; // already safe
        }

        return dodgeToSafeTileInRange(target, attackRange);
    }

    /**
     * set default search radius for safe tiles
     */
    public void setDefaultSearchRadius(int radius) {
        this.defaultSearchRadius = radius;
        log.debug("Set default search radius to {}", radius);
    }

    /**
     * get default search radius
     */
    public int getDefaultSearchRadius() {
        return defaultSearchRadius;
    }

    /**
     * calculate safe path around hazards using A* pathfinding
     * returns list of waypoints that avoid hazards
     */
    public List<WorldPoint> calculateSafePath(WorldPoint destination) {
        WorldPoint start = Microbot.getClient().getLocalPlayer().getWorldLocation();
        return calculateSafePath(start, destination, 2); // default 2 ticks per tile (running)
    }

    /**
     * calculate safe path with custom ticks per tile
     * supports woox walking (ticksPerTile = 1 for walking, 2 for running)
     */
    public List<WorldPoint> calculateSafePath(WorldPoint start, WorldPoint end, int ticksPerTile) {
        if (start == null || end == null) {
            return Collections.emptyList();
        }

        int currentTick = Microbot.getClient().getTickCount();

        // a* algorithm with tick validation
        PriorityQueue<PathNode> openSet = new PriorityQueue<>(Comparator.comparingDouble(PathNode::getCost));
        Set<WorldPoint> closedSet = new HashSet<>();
        Map<WorldPoint, PathNode> nodeMap = new HashMap<>();

        PathNode startNode = new PathNode(start, currentTick, null, 0);
        openSet.add(startNode);
        nodeMap.put(start, startNode);

        while (!openSet.isEmpty()) {
            PathNode current = openSet.poll();

            // found destination
            if (current.position.equals(end)) {
                return reconstructPath(current);
            }

            closedSet.add(current.position);

            // explore neighbors
            for (WorldPoint neighbor : getValidNeighbors(current.position)) {
                if (closedSet.contains(neighbor)) {
                    continue;
                }

                int arrivalTick = current.tickCount + ticksPerTile;

                // critical: check if tile is safe at arrival time
                if (!isSafeTileAtTick(neighbor, arrivalTick)) {
                    continue;
                }

                double newCost = current.cost + 1 + heuristic(neighbor, end);

                PathNode existingNode = nodeMap.get(neighbor);
                if (existingNode == null || newCost < existingNode.cost) {
                    PathNode newNode = new PathNode(neighbor, arrivalTick, current, newCost);
                    openSet.add(newNode);
                    nodeMap.put(neighbor, newNode);
                }
            }
        }

        // no safe path found
        return Collections.emptyList();
    }

    /**
     * check if tile will be safe at specific future tick
     * validates against predicted hazard positions
     * critical for woox walking and tornado dodging
     */
    public boolean isSafeTileAtTick(WorldPoint point, int futureTick) {
        if (hazardTracker == null) {
            return true; // assume safe if no tracker
        }

        // check all active hazards
        List<HazardData> activeHazards = hazardTracker.getActiveHazards();
        for (HazardData hazard : activeHazards) {
            // check if hazard will be dangerous at this tick
            if (hazard.willBeDangerousAt(point, futureTick)) {
                return false;
            }
        }

        return true;
    }

    /**
     * manhattan distance heuristic for A*
     */
    private double heuristic(WorldPoint a, WorldPoint b) {
        return a.distanceTo(b);
    }

    /**
     * reconstruct path from end node
     */
    private List<WorldPoint> reconstructPath(PathNode endNode) {
        List<WorldPoint> path = new ArrayList<>();
        PathNode current = endNode;

        while (current != null) {
            path.add(0, current.position);
            current = current.parent;
        }

        return path;
    }

    /**
     * get valid neighbor tiles (8 directions)
     */
    private List<WorldPoint> getValidNeighbors(WorldPoint point) {
        List<WorldPoint> neighbors = new ArrayList<>();
        int plane = point.getPlane();

        // cardinal directions
        addNeighborIfValid(neighbors, new WorldPoint(point.getX() + 1, point.getY(), plane));
        addNeighborIfValid(neighbors, new WorldPoint(point.getX() - 1, point.getY(), plane));
        addNeighborIfValid(neighbors, new WorldPoint(point.getX(), point.getY() + 1, plane));
        addNeighborIfValid(neighbors, new WorldPoint(point.getX(), point.getY() - 1, plane));

        // diagonal directions
        addNeighborIfValid(neighbors, new WorldPoint(point.getX() + 1, point.getY() + 1, plane));
        addNeighborIfValid(neighbors, new WorldPoint(point.getX() + 1, point.getY() - 1, plane));
        addNeighborIfValid(neighbors, new WorldPoint(point.getX() - 1, point.getY() + 1, plane));
        addNeighborIfValid(neighbors, new WorldPoint(point.getX() - 1, point.getY() - 1, plane));

        return neighbors;
    }

    /**
     * add neighbor if within bounds
     */
    private void addNeighborIfValid(List<WorldPoint> neighbors, WorldPoint point) {
        // basic bounds check (can be enhanced with collision detection)
        if (point.getX() >= 0 && point.getY() >= 0) {
            neighbors.add(point);
        }
    }

    /**
     * pathfinding node for A* algorithm
     */
    private static class PathNode {
        final WorldPoint position;
        final int tickCount;
        final PathNode parent;
        final double cost;

        PathNode(WorldPoint position, int tickCount, PathNode parent, double cost) {
            this.position = position;
            this.tickCount = tickCount;
            this.parent = parent;
            this.cost = cost;
        }

        double getCost() {
            return cost;
        }
    }
}
