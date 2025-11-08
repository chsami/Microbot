package net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.handlers;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.Rs2PvMCombat;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.registry.HazardRegistry;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;

/**
 * thread-safe handler for Woox walking during boss hazard phases
 * generic implementation for DPS maintenance while avoiding damage
 *
 * WOOX WALKING PATTERN:
 * - Attack boss → move to safe tile → repeat
 * - Timing based on weapon attack speed
 * - Path generation along safe zones (lines, perimeters, custom)
 * - Hazard-aware filtering (tornadoes, acid, floor tiles)
 *
 * USAGE EXAMPLES:
 * - Vorkath acid phase: 5-tile line east-west
 * - Hunllef floor tiles: perimeter movement around edges
 * - Zulrah toxic clouds: safe tile navigation
 *
 * Author: Voxslyvae
 */
@Slf4j
@Singleton
public class Rs2WooxWalkHandler {

    private static Rs2WooxWalkHandler instance;

    // active woox walk configuration
    private volatile WooxWalkConfig activeConfig = null;
    private volatile boolean isActive = false;

    // movement state
    private volatile WorldPoint currentTargetTile = null;
    private volatile int lastAttackTick = -1;
    private volatile int lastMoveTick = -1;

    // path generation state
    private List<WorldPoint> currentPath = new ArrayList<>();
    private int currentPathIndex = 0;
    private boolean reverseDirection = false;

    private Rs2WooxWalkHandler() {
    }

    public static synchronized Rs2WooxWalkHandler getInstance() {
        if (instance == null) {
            instance = new Rs2WooxWalkHandler();
        }
        return instance;
    }

    /**
     * immutable woox walk configuration
     */
    @Data
    public static class WooxWalkConfig {
        private final SafeZoneType zoneType;
        private final WorldPoint startPoint;
        private final WorldPoint endPoint;
        private final int width; // for line patterns
        private final int height; // for area patterns
        private final List<WorldPoint> customTiles; // for custom patterns
        private final int weaponAttackSpeed; // ticks between attacks
        private final int attackRange; // max attack distance from target
        private final NPC target; // boss to attack
        private final boolean filterHazards; // use HazardRegistry for filtering

        /**
         * create line pattern config (e.g., Vorkath acid walk)
         */
        public static WooxWalkConfig linePattern(WorldPoint start, WorldPoint end, int width,
                                                   int weaponSpeed, int attackRange, NPC target) {
            return new WooxWalkConfig(
                SafeZoneType.LINE,
                start,
                end,
                width,
                1,
                null,
                weaponSpeed,
                attackRange,
                target,
                true
            );
        }

        /**
         * create perimeter pattern config (e.g., Hunllef edges)
         */
        public static WooxWalkConfig perimeterPattern(WorldArea area, int weaponSpeed,
                                                       int attackRange, NPC target) {
            // extract perimeter tiles from area
            List<WorldPoint> perimeterTiles = extractPerimeterTiles(area);

            return new WooxWalkConfig(
                SafeZoneType.PERIMETER,
                null,
                null,
                0,
                0,
                perimeterTiles,
                weaponSpeed,
                attackRange,
                target,
                true
            );
        }

        /**
         * create custom pattern config (manually specified tiles)
         */
        public static WooxWalkConfig customPattern(List<WorldPoint> safeTiles, int weaponSpeed,
                                                     int attackRange, NPC target) {
            return new WooxWalkConfig(
                SafeZoneType.CUSTOM,
                null,
                null,
                0,
                0,
                safeTiles,
                weaponSpeed,
                attackRange,
                target,
                true
            );
        }

        /**
         * extract perimeter tiles from world area
         */
        private static List<WorldPoint> extractPerimeterTiles(WorldArea area) {
            List<WorldPoint> perimeter = new ArrayList<>();

            int minX = area.getX();
            int maxX = area.getX() + area.getWidth() - 1;
            int minY = area.getY();
            int maxY = area.getY() + area.getHeight() - 1;
            int plane = area.getPlane();

            // top edge
            for (int x = minX; x <= maxX; x++) {
                perimeter.add(new WorldPoint(x, maxY, plane));
            }

            // right edge (skip corner already added)
            for (int y = maxY - 1; y >= minY; y--) {
                perimeter.add(new WorldPoint(maxX, y, plane));
            }

            // bottom edge (skip corner)
            for (int x = maxX - 1; x >= minX; x--) {
                perimeter.add(new WorldPoint(x, minY, plane));
            }

            // left edge (skip both corners)
            for (int y = minY + 1; y < maxY; y++) {
                perimeter.add(new WorldPoint(minX, y, plane));
            }

            return perimeter;
        }
    }

    /**
     * safe zone type enumeration
     */
    public enum SafeZoneType {
        LINE,      // back-and-forth along a line (Vorkath)
        PERIMETER, // circular movement around area edges (Hunllef)
        CUSTOM     // user-defined tile list
    }

    /**
     * configure and start woox walking
     *
     * @param config woox walk configuration
     * @return true if started successfully
     */
    public boolean startWooxWalk(WooxWalkConfig config) {
        if (config == null) {
            log.warn("Cannot start woox walk: config is null");
            return false;
        }

        if (config.getTarget() == null || config.getTarget().isDead()) {
            log.warn("Cannot start woox walk: target is null or dead");
            return false;
        }

        // generate initial path
        List<WorldPoint> path = generatePath(config);
        if (path.isEmpty()) {
            log.warn("Cannot start woox walk: no valid safe tiles found");
            return false;
        }

        activeConfig = config;
        currentPath = path;
        currentPathIndex = 0;
        reverseDirection = false;
        isActive = true;

        log.info("Started woox walking: zone_type={}, tiles={}, weapon_speed={}",
            config.getZoneType(), path.size(), config.getWeaponAttackSpeed());

        return true;
    }

    /**
     * stop woox walking
     */
    public void stopWooxWalk() {
        if (isActive) {
            log.info("Stopped woox walking");
        }

        isActive = false;
        activeConfig = null;
        currentPath.clear();
        currentPathIndex = 0;
        currentTargetTile = null;
        lastAttackTick = -1;
        lastMoveTick = -1;
        reverseDirection = false;
    }

    /**
     * check if woox walking is active
     */
    public boolean isActive() {
        return isActive;
    }

    /**
     * execute one tick of woox walking
     * call this every game tick (600ms)
     *
     * @return true if action was taken
     */
    public boolean tick() {
        if (!isActive || activeConfig == null) {
            return false;
        }

        // validate target still alive
        if (activeConfig.getTarget() == null || activeConfig.getTarget().isDead()) {
            log.warn("Target died, stopping woox walk");
            stopWooxWalk();
            return false;
        }

        int currentTick = Microbot.getClient().getTickCount();
        WorldPoint playerPos = Rs2Player.getWorldLocation();

        // regenerate path if hazards changed
        if (activeConfig.isFilterHazards()) {
            List<WorldPoint> newPath = generatePath(activeConfig);
            if (!newPath.equals(currentPath)) {
                log.debug("Hazard configuration changed, regenerating path (old: {}, new: {})",
                    currentPath.size(), newPath.size());
                currentPath = newPath;
                currentPathIndex = 0;
            }
        }

        if (currentPath.isEmpty()) {
            log.warn("No safe tiles available, stopping woox walk");
            stopWooxWalk();
            return false;
        }

        // ATTACK PHASE: attack if weapon cooldown elapsed
        int ticksSinceLastAttack = currentTick - lastAttackTick;
        boolean shouldAttack = lastAttackTick < 0 || ticksSinceLastAttack >= activeConfig.getWeaponAttackSpeed();

        if (shouldAttack) {
            // check if in attack range
            WorldPoint targetPos = activeConfig.getTarget().getWorldLocation();
            int distanceToTarget = playerPos.distanceTo(targetPos);

            if (distanceToTarget <= activeConfig.getAttackRange()) {
                // attack target
                Rs2PvMCombat.getInstance().attackNpc(activeConfig.getTarget());
                lastAttackTick = currentTick;
                log.debug("Woox walk: ATTACK (tick: {}, distance: {})", currentTick, distanceToTarget);
                return true;
            } else {
                log.debug("Woox walk: Cannot attack, out of range (distance: {}, max: {})",
                    distanceToTarget, activeConfig.getAttackRange());
            }
        }

        // MOVE PHASE: move if not attacking
        int ticksSinceLastMove = currentTick - lastMoveTick;
        boolean shouldMove = ticksSinceLastMove >= 1; // can move every tick

        if (shouldMove) {
            WorldPoint nextTile = getNextTile(playerPos);

            if (nextTile != null && !nextTile.equals(playerPos)) {
                // move to next tile
                Rs2Walker.walkFastLocal(nextTile);
                currentTargetTile = nextTile;
                lastMoveTick = currentTick;
                log.debug("Woox walk: MOVE to {} (tick: {})", nextTile, currentTick);
                return true;
            }
        }

        return false;
    }

    /**
     * generate path based on configuration
     */
    private List<WorldPoint> generatePath(WooxWalkConfig config) {
        List<WorldPoint> basePath = new ArrayList<>();

        switch (config.getZoneType()) {
            case LINE:
                basePath = generateLinePath(config.getStartPoint(), config.getEndPoint(), config.getWidth());
                break;

            case PERIMETER:
                basePath = new ArrayList<>(config.getCustomTiles());
                break;

            case CUSTOM:
                basePath = new ArrayList<>(config.getCustomTiles());
                break;
        }

        // filter hazardous tiles
        if (config.isFilterHazards()) {
            HazardRegistry hazardRegistry = HazardRegistry.getInstance();
            basePath = basePath.stream()
                .filter(tile -> !hazardRegistry.isTileHazardous(tile))
                .collect(Collectors.toList());
        }

        // filter tiles out of attack range
        WorldPoint targetPos = config.getTarget().getWorldLocation();
        int attackRange = config.getAttackRange();

        basePath = basePath.stream()
            .filter(tile -> tile.distanceTo(targetPos) <= attackRange)
            .collect(Collectors.toList());

        return basePath;
    }

    /**
     * generate line path (back-and-forth pattern)
     */
    private List<WorldPoint> generateLinePath(WorldPoint start, WorldPoint end, int width) {
        List<WorldPoint> path = new ArrayList<>();

        int minX = Math.min(start.getX(), end.getX());
        int maxX = Math.max(start.getX(), end.getX());
        int minY = Math.min(start.getY(), end.getY());
        int maxY = Math.max(start.getY(), end.getY());
        int plane = start.getPlane();

        // determine if line is horizontal or vertical
        boolean horizontal = maxX - minX >= maxY - minY;

        if (horizontal) {
            // horizontal line with width
            for (int x = minX; x <= maxX; x++) {
                for (int yOffset = 0; yOffset < width; yOffset++) {
                    path.add(new WorldPoint(x, minY + yOffset, plane));
                }
            }
        } else {
            // vertical line with width
            for (int y = minY; y <= maxY; y++) {
                for (int xOffset = 0; xOffset < width; xOffset++) {
                    path.add(new WorldPoint(minX + xOffset, y, plane));
                }
            }
        }

        return path;
    }

    /**
     * get next tile in path (back-and-forth or circular)
     */
    private WorldPoint getNextTile(WorldPoint currentPlayerPos) {
        if (currentPath.isEmpty()) {
            return null;
        }

        // if player is already on a path tile, advance to next
        int currentPlayerIndex = currentPath.indexOf(currentPlayerPos);
        if (currentPlayerIndex >= 0) {
            currentPathIndex = currentPlayerIndex;
        }

        // determine next index based on zone type
        if (activeConfig.getZoneType() == SafeZoneType.LINE) {
            // back-and-forth pattern
            if (!reverseDirection) {
                currentPathIndex++;
                if (currentPathIndex >= currentPath.size()) {
                    currentPathIndex = currentPath.size() - 2;
                    reverseDirection = true;
                }
            } else {
                currentPathIndex--;
                if (currentPathIndex < 0) {
                    currentPathIndex = 1;
                    reverseDirection = false;
                }
            }
        } else {
            // circular pattern (PERIMETER, CUSTOM)
            currentPathIndex++;
            if (currentPathIndex >= currentPath.size()) {
                currentPathIndex = 0; // wrap around
            }
        }

        // ensure index is valid
        if (currentPathIndex < 0 || currentPathIndex >= currentPath.size()) {
            currentPathIndex = 0;
        }

        return currentPath.get(currentPathIndex);
    }

    /**
     * get current safe tile count
     */
    public int getSafeTileCount() {
        return currentPath.size();
    }

    /**
     * get current path (for debugging/overlay)
     */
    public Optional<List<WorldPoint>> getCurrentPath() {
        if (!isActive) {
            return Optional.empty();
        }
        return Optional.of(new ArrayList<>(currentPath));
    }

    /**
     * get current target tile
     */
    public Optional<WorldPoint> getCurrentTargetTile() {
        if (!isActive) {
            return Optional.empty();
        }
        return Optional.ofNullable(currentTargetTile);
    }

    /**
     * get ticks until next attack
     */
    public int getTicksUntilNextAttack() {
        if (!isActive || activeConfig == null || lastAttackTick < 0) {
            return 0;
        }

        int currentTick = Microbot.getClient().getTickCount();
        int ticksSinceLastAttack = currentTick - lastAttackTick;
        int ticksRemaining = activeConfig.getWeaponAttackSpeed() - ticksSinceLastAttack;

        return Math.max(0, ticksRemaining);
    }

    /**
     * clear all state
     */
    public void clear() {
        stopWooxWalk();
        log.debug("Cleared woox walk handler");
    }
}
