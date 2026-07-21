package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldPoint;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.*;

@Slf4j
public class CollisionMap {
    // Enum.values() makes copies every time which hurts performance in the hotpath
    private static final OrdinalDirection[] ORDINAL_VALUES = OrdinalDirection.values();

    private final SplitFlagMap collisionData;

    public byte[] getPlanes() {
        return collisionData.getRegionMapPlaneCounts();
    }

    public CollisionMap(SplitFlagMap collisionData) {
        this.collisionData = collisionData;
    }

    private boolean get(int x, int y, int z, int flag) {
        return collisionData.get(x, y, z, flag);
    }

    public boolean n(int x, int y, int z) {
        return get(x, y, z, 0);
    }

    public boolean s(int x, int y, int z) {
        return n(x, y - 1, z);
    }

    public boolean e(int x, int y, int z) {
        return get(x, y, z, 1);
    }

    public boolean w(int x, int y, int z) {
        return e(x - 1, y, z);
    }

    private boolean ne(int x, int y, int z) {
        return n(x, y, z) && e(x, y + 1, z) && e(x, y, z) && n(x + 1, y, z);
    }

    private boolean nw(int x, int y, int z) {
        return n(x, y, z) && w(x, y + 1, z) && w(x, y, z) && n(x - 1, y, z);
    }

    private boolean se(int x, int y, int z) {
        return s(x, y, z) && e(x, y - 1, z) && e(x, y, z) && s(x + 1, y, z);
    }

    private boolean sw(int x, int y, int z) {
        return s(x, y, z) && w(x, y - 1, z) && w(x, y, z) && s(x - 1, y, z);
    }

    public boolean isBlocked(int x, int y, int z) {
        return !n(x, y, z) && !s(x, y, z) && !e(x, y, z) && !w(x, y, z);
    }

    /**
     * Single walking step permission check from (x,y,z) in direction (dx,dy).
     * Used by {@link PathSmoother}. Graph expansion uses {@link #fillTraversableLegacy}
     * instead; they intentionally differ where legacy blocked-tile logic diverges from
     * {@code canStep}.
     */
    public boolean canStep(int x, int y, int z, int dx, int dy) {
        if (dx == 0 && dy == 0) return true;
        if (dx < -1 || dx > 1 || dy < -1 || dy > 1) return false;
        if (isBlocked(x, y, z)) {
            if (isBlocked(x + dx, y + dy, z)) return false;
            if (dx != 0 && dy != 0) {
                return !isBlocked(x + dx, y, z) && !isBlocked(x, y + dy, z);
            }
            return true;
        }
        if (dx == -1 && dy == 0) return w(x, y, z);
        if (dx == 1 && dy == 0) return e(x, y, z);
        if (dx == 0 && dy == -1) return s(x, y, z);
        if (dx == 0 && dy == 1) return n(x, y, z);
        if (dx == -1 && dy == -1) return sw(x, y, z);
        if (dx == 1 && dy == -1) return se(x, y, z);
        if (dx == -1 && dy == 1) return nw(x, y, z);
        if (dx == 1 && dy == 1) return ne(x, y, z);
        return false;
    }

    /**
     * Legacy neighbor traversability for {@link #getNeighbors} / {@link #getReverseNeighbors}.
     * {@link #canStep} remains for {@link PathSmoother} line traces.
     */
    private void fillTraversableLegacy(int x, int y, int z, boolean[] out) {
        if (isBlocked(x, y, z)) {
            boolean westBlocked = isBlocked(x - 1, y, z);
            boolean eastBlocked = isBlocked(x + 1, y, z);
            boolean southBlocked = isBlocked(x, y - 1, z);
            boolean northBlocked = isBlocked(x, y + 1, z);
            boolean southWestBlocked = isBlocked(x - 1, y - 1, z);
            boolean southEastBlocked = isBlocked(x + 1, y - 1, z);
            boolean northWestBlocked = isBlocked(x - 1, y + 1, z);
            boolean northEastBlocked = isBlocked(x + 1, y + 1, z);
            out[0] = !westBlocked;
            out[1] = !eastBlocked;
            out[2] = !southBlocked;
            out[3] = !northBlocked;
            out[4] = !southWestBlocked && !westBlocked && !southBlocked;
            out[5] = !southEastBlocked && !eastBlocked && !southBlocked;
            out[6] = !northWestBlocked && !westBlocked && !northBlocked;
            out[7] = !northEastBlocked && !eastBlocked && !northBlocked;
        } else {
            out[0] = w(x, y, z);
            out[1] = e(x, y, z);
            out[2] = s(x, y, z);
            out[3] = n(x, y, z);
            out[4] = sw(x, y, z);
            out[5] = se(x, y, z);
            out[6] = nw(x, y, z);
            out[7] = ne(x, y, z);
        }
    }

    private static int packedPointFromOrdinal(int startPacked, OrdinalDirection direction) {
        final int x = WorldPointUtil.unpackWorldX(startPacked);
        final int y = WorldPointUtil.unpackWorldY(startPacked);
        final int plane = WorldPointUtil.unpackWorldPlane(startPacked);
        return WorldPointUtil.packWorldPoint(x + direction.x, y + direction.y, plane);
    }

    // This is only safe if pathfinding is single-threaded
    private final List<Node> neighbors = new ArrayList<>(16);
    private final boolean[] traversable = new boolean[8];
    private final boolean[] traversableReverseAccum = new boolean[8];

    public static final Set<Integer> ignoreCollisionPacked;
    static {
        int[][] coords = {
            {3142, 3457, 0}, {3141, 3457, 0}, {3142, 3457, 0}, {3141, 3458, 0},
            {3141, 3456, 0}, {3142, 3456, 0}, {2744, 3153, 0}, {2745, 3153, 0},
            {3674, 3882, 0}, {3673, 3884, 0}, {3673, 3885, 0}, {3673, 3886, 0},
            {3672, 3888, 0}, {3675, 3893, 0}, {3678, 3893, 0}, {3684, 3845, 0},
            {3670, 3836, 0}, {3672, 3862, 0}
        };
        Set<Integer> set = new HashSet<>(coords.length * 2);
        for (int[] c : coords) {
            set.add(WorldPointUtil.packWorldPoint(c[0], c[1], c[2]));
        }
        ignoreCollisionPacked = Collections.unmodifiableSet(set);
    }

    private volatile int cachedRegionId = -1;
    private volatile long cachedRegionIdTime = 0;
    private static final long REGION_CACHE_MS = 5000;
    private static final int TOA_PUZZLE_REGION = 14162;
    // Extra g-cost (in tile-distance units) for stepping onto a tile next to an aggressive-NPC
    // hazard. High enough to strongly prefer a detour, but a penalty (not a block) so a true
    // chokepoint is still traversable.
    private static final int DANGEROUS_TILE_PENALTY = 100;

    private int getCachedRegionId() {
        long now = System.currentTimeMillis();
        if (now - cachedRegionIdTime > REGION_CACHE_MS) {
            try {
                WorldPoint loc = Rs2Player.getWorldLocation();
                cachedRegionId = loc != null ? loc.getRegionID() : -1;
            } catch (Exception e) {
                cachedRegionId = -1;
            }
            cachedRegionIdTime = now;
        }
        return cachedRegionId;
    }

    public List<Node> getNeighbors(Node node, VisitedTiles visited, PathfinderConfig config, Set<Integer> targets) {
        final int x = WorldPointUtil.unpackWorldX(node.packedPosition);
        final int y = WorldPointUtil.unpackWorldY(node.packedPosition);
        final int z = WorldPointUtil.unpackWorldPlane(node.packedPosition);

        neighbors.clear();

        Set<Transport> transports = config.getTransportsPacked().getOrDefault(node.packedPosition, Collections.emptySet());

        int moaSeenHere = 0;
        int moaAddedHere = 0;
        int moaVisited = 0;
        int moaIgnored = 0;
        List<Integer> moaCosts = null;

        // Transports are pre-filtered by PathfinderConfig.refreshTransports
        // Thus any transports in the list are guaranteed to be valid per the user's settings
        for (Transport transport : transports) {
            boolean isMoa = transport.getType() == TransportType.SEASONAL_TRANSPORT
                    && transport.getDisplayInfo() != null
                    && transport.getDisplayInfo().toLowerCase().contains("map of alacrity");
            if (isMoa) moaSeenHere++;

            //START microbot variables
            if (visited.get(transport.getDestination())) {
                if (isMoa) moaVisited++;
                continue;
            }

            if (TransportType.isTeleport(transport.getType(), transport.getOrigin())) {
                if (config.isIgnoreTeleportAndItems()) {
                    if (isMoa) moaIgnored++;
                    continue;
                }
                int cost = config.getDistanceBeforeUsingTeleport() + transport.getDuration();
                neighbors.add(new TransportNode(transport.getDestination(), node, cost));
                if (isMoa) {
                    moaAddedHere++;
                    if (moaCosts == null) moaCosts = new ArrayList<>();
                    moaCosts.add(cost);
                }
            } else {
                neighbors.add(new TransportNode(transport.getDestination(), node, transport.getDuration()));
            }
            //END microbot variables
        }

        if (moaSeenHere > 0) {
            log.debug("[MoA] getNeighbors @ ({},{},{}): seen={} added={} visited={} ignored={} (distanceBeforeUsingTeleport={}, costs={})",
                    x, y, z, moaSeenHere, moaAddedHere, moaVisited, moaIgnored,
                    config.getDistanceBeforeUsingTeleport(),
                    moaCosts == null ? "[]" : moaCosts);
        }

        fillTraversableLegacy(x, y, z, traversable);

        for (int i = 0; i < traversable.length; i++) {
            OrdinalDirection d = ORDINAL_VALUES[i];
            int neighborPacked = packedPointFromOrdinal(node.packedPosition, d);
            if (visited.get(neighborPacked)) continue;
            if (config.getRestrictedPointsPacked().contains(neighborPacked)) continue;
            if (config.getCustomRestrictions().contains(neighborPacked)) continue;
            if (config.isBlockedTransportStep(node.packedPosition, neighborPacked)) continue;

            if (ignoreCollisionPacked.contains(node.packedPosition)) {
                neighbors.add(new Node(neighborPacked, node));
                continue;
            }

            /**
             * This piece of code is designed to allow web walker to be used in toa puzzle room
             * it will dodge specific tiles in the sequence room
             */
            if (getCachedRegionId() == TOA_PUZZLE_REGION) {
                if (!targets.contains(neighborPacked)) {
                    WorldPoint globalWorldPoint = Rs2WorldPoint.convertInstancedWorldPoint(WorldPointUtil.unpackWorldPoint(neighborPacked));
                    if (globalWorldPoint != null) {
                        TileObject go = Rs2GameObject.getGroundObject(globalWorldPoint);
                        if (go != null && go.getId() == 45340) {
                            continue;
                        }
                    }
                }
            }

            if (traversable[i]) {
                if (config.isAvoidDangerousNpcs()
                        && config.isDangerousAdjacentTile(neighborPacked)
                        && !targets.contains(neighborPacked)) {
                    // Penalty (not a skip): the path keeps >=2 tiles from the hazard when a
                    // reasonable detour exists, but a chokepoint still routes through.
                    int penalizedCost = node.cost
                            + WorldPointUtil.distanceBetween(node.packedPosition, neighborPacked)
                            + DANGEROUS_TILE_PENALTY;
                    neighbors.add(new Node(neighborPacked, node, penalizedCost));
                } else {
                    neighbors.add(new Node(neighborPacked, node));
                }
            } else if (Math.abs(d.x + d.y) == 1 && isBlocked(x + d.x, y + d.y, z)) {
                // The transport starts from a blocked adjacent tile, e.g. fairy ring
                // Only checks non-teleport transports (includes portals and levers, but not items and spells)
                Set<Transport> neighborTransports = config.getTransportsPacked().getOrDefault(neighborPacked, Collections.emptySet());
                for (Transport transport : neighborTransports) {
                    if (transport.getOrigin() == null || visited.get(transport.getOrigin())) {
                        continue;
                    }
                    neighbors.add(new Node(transport.getOrigin(), node));
                }
            }
        }

        return neighbors;
    }

    /**
     * Predecessor expansion for bidirectional search: every forward edge {@code pred → node} appears as
     * a {@code node} expansion to {@code pred}. Origin-less teleports are omitted (caller builds
     * {@code incomingByDestPacked} without them).
     */
    public List<Node> getReverseNeighbors(Node node, VisitedTiles visitedBackward, PathfinderConfig config,
            Set<Integer> puzzleAllowPacked, Map<Integer, Set<Transport>> incomingByDestPacked) {
        final int x = WorldPointUtil.unpackWorldX(node.packedPosition);
        final int y = WorldPointUtil.unpackWorldY(node.packedPosition);
        final int z = WorldPointUtil.unpackWorldPlane(node.packedPosition);

        neighbors.clear();

        if (incomingByDestPacked != null) {
            Set<Transport> incoming = incomingByDestPacked.getOrDefault(node.packedPosition, Collections.emptySet());
            for (Transport transport : incoming) {
                WorldPoint origin = transport.getOrigin();
                if (origin == null) {
                    continue;
                }
                int originPacked = WorldPointUtil.packWorldPoint(origin);
                if (visitedBackward.get(originPacked)) {
                    continue;
                }
                if (TransportType.isTeleport(transport.getType(), transport.getOrigin())) {
                    if (config.isIgnoreTeleportAndItems()) {
                        continue;
                    }
                    neighbors.add(new TransportNode(origin, node, config.getDistanceBeforeUsingTeleport() + transport.getDuration()));
                } else {
                    neighbors.add(new TransportNode(origin, node, transport.getDuration()));
                }
            }
        }

        for (int i = 0; i < 8; i++) {
            OrdinalDirection d = ORDINAL_VALUES[i];
            fillTraversableLegacy(x - d.x, y - d.y, z, traversable);
            traversableReverseAccum[i] = traversable[i];
        }
        System.arraycopy(traversableReverseAccum, 0, traversable, 0, 8);

        for (int i = 0; i < traversable.length; i++) {
            OrdinalDirection d = ORDINAL_VALUES[i];
            int prevPacked = WorldPointUtil.packWorldPoint(x - d.x, y - d.y, z);
            if (visitedBackward.get(prevPacked)) {
                continue;
            }
            if (config.getRestrictedPointsPacked().contains(prevPacked)) {
                continue;
            }
            if (config.getCustomRestrictions().contains(prevPacked)) {
                continue;
            }
            if (config.isBlockedTransportStep(prevPacked, node.packedPosition)) {
                continue;
            }

            if (ignoreCollisionPacked.contains(node.packedPosition)) {
                neighbors.add(new Node(prevPacked, node));
                continue;
            }

            if (getCachedRegionId() == TOA_PUZZLE_REGION) {
                if (!puzzleAllowPacked.contains(prevPacked)) {
                    WorldPoint globalWorldPoint = Rs2WorldPoint.convertInstancedWorldPoint(WorldPointUtil.unpackWorldPoint(prevPacked));
                    if (globalWorldPoint != null) {
                        TileObject go = Rs2GameObject.getGroundObject(globalWorldPoint);
                        if (go != null && go.getId() == 45340) {
                            continue;
                        }
                    }
                }
            }

            if (traversable[i]) {
                if (config.isAvoidDangerousNpcs() && config.isDangerousAdjacentTile(prevPacked)) {
                    // Mirror the forward danger penalty so the bidirectional search costs the same
                    // edge consistently from both ends and can't pick a hazard-adjacent meeting.
                    int penalizedCost = node.cost
                            + WorldPointUtil.distanceBetween(node.packedPosition, prevPacked)
                            + DANGEROUS_TILE_PENALTY;
                    neighbors.add(new Node(prevPacked, node, penalizedCost));
                } else {
                    neighbors.add(new Node(prevPacked, node));
                }
            } else if (Math.abs(d.x + d.y) == 1
                    && isBlocked(WorldPointUtil.unpackWorldX(prevPacked), WorldPointUtil.unpackWorldY(prevPacked), z)) {
                int wx = WorldPointUtil.unpackWorldX(prevPacked);
                int wy = WorldPointUtil.unpackWorldY(prevPacked);
                Set<Transport> ts = config.getTransportsPacked().getOrDefault(prevPacked, Collections.emptySet());
                for (Transport transport : ts) {
                    if (transport.getOrigin() == null) {
                        continue;
                    }
                    if (WorldPointUtil.packWorldPoint(transport.getOrigin()) != prevPacked) {
                        continue;
                    }
                    neighbors.add(new Node(WorldPointUtil.packWorldPoint(wx, wy, z), node));
                }
            }
        }

        return neighbors;
    }
}
