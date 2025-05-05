package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldPoint;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.*;

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

    private static int packedPointFromOrdinal(int startPacked, OrdinalDirection direction) {
        final int x = WorldPointUtil.unpackWorldX(startPacked);
        final int y = WorldPointUtil.unpackWorldY(startPacked);
        final int plane = WorldPointUtil.unpackWorldPlane(startPacked);
        return WorldPointUtil.packWorldPoint(x + direction.x, y + direction.y, plane);
    }

    // This is only safe if pathfinding is single-threaded
    private final List<Node> neighbors = new ArrayList<>(16);
    private final boolean[] traversable = new boolean[8];

    public static final List<WorldPoint> ignoreCollision = Arrays.asList(
            new WorldPoint(3142, 3457, 0),
            new WorldPoint(3141, 3457, 0),
            new WorldPoint(3142, 3457, 0),
            new WorldPoint(3141, 3458, 0),
            new WorldPoint(3141, 3456, 0),
            new WorldPoint(3142, 3456, 0),
            new WorldPoint(2744, 3153, 0),
            new WorldPoint(2745, 3153, 0),
            new WorldPoint(3674, 3882, 0),
            new WorldPoint(3673, 3884, 0),
            new WorldPoint(3673, 3885, 0),
            new WorldPoint(3673, 3886, 0),
            new WorldPoint(3672, 3888, 0),
            new WorldPoint(3675, 3893, 0),
            new WorldPoint(3678, 3893, 0),
            new WorldPoint(3684, 3845, 0),
            new WorldPoint(3670, 3836, 0),
            new WorldPoint(3672, 3862, 0)
    );

    public List<Node> getNeighbors(Node node, VisitedTiles visited, PathfinderConfig config, Set<WorldPoint> targets) {
        final int x = WorldPointUtil.unpackWorldX(node.packedPosition);
        final int y = WorldPointUtil.unpackWorldY(node.packedPosition);
        final int z = WorldPointUtil.unpackWorldPlane(node.packedPosition);

        neighbors.clear();

        @SuppressWarnings("unchecked") // Casting EMPTY_LIST to List<Transport> is safe here
        Set<Transport> transports = config.getTransportsPacked().getOrDefault(node.packedPosition, (Set<Transport>)Collections.EMPTY_SET);

        // Transports are pre-filtered by PathfinderConfig.refreshTransports
        // Thus any transports in the list are guaranteed to be valid per the user's settings
        for (Transport transport : transports) {
            //START microbot variables
            if (visited.get(transport.getDestination())) continue;
            if (config.isIgnoreTeleportAndItems() && TransportType.isTeleport(transport.getType())) continue;
            if (TransportType.isTeleport(transport.getType())) {
                neighbors.add(new TransportNode(transport.getDestination(), node, config.getDistanceBeforeUsingTeleport() + transport.getDuration()));
            } else {
                neighbors.add(new TransportNode(transport.getDestination(), node, transport.getDuration()));
            }
            //END microbot variables
        }

        if (isBlocked(x, y, z)) {
            boolean westBlocked = isBlocked(x - 1, y, z);
            boolean eastBlocked = isBlocked(x + 1, y, z);
            boolean southBlocked = isBlocked(x, y - 1, z);
            boolean northBlocked = isBlocked(x, y + 1, z);
            boolean southWestBlocked = isBlocked(x - 1, y - 1, z);
            boolean southEastBlocked = isBlocked(x + 1, y - 1, z);
            boolean northWestBlocked = isBlocked(x - 1, y + 1, z);
            boolean northEastBlocked = isBlocked(x + 1, y + 1, z);
            traversable[0] = !westBlocked;
            traversable[1] = !eastBlocked;
            traversable[2] = !southBlocked;
            traversable[3] = !northBlocked;
            traversable[4] = !southWestBlocked && !westBlocked && !southBlocked;
            traversable[5] = !southEastBlocked && !eastBlocked && !southBlocked;
            traversable[6] = !northWestBlocked && !westBlocked && !northBlocked;
            traversable[7] = !northEastBlocked && !eastBlocked && !northBlocked;
        } else {
            traversable[0] = w(x, y, z);
            traversable[1] = e(x, y, z);
            traversable[2] = s(x, y, z);
            traversable[3] = n(x, y, z);
            traversable[4] = sw(x, y, z);
            traversable[5] = se(x, y, z);
            traversable[6] = nw(x, y, z);
            traversable[7] = ne(x, y, z);
        }

        for (int i = 0; i < traversable.length; i++) {
            OrdinalDirection d = ORDINAL_VALUES[i];
            int neighborPacked = packedPointFromOrdinal(node.packedPosition, d);
            if (visited.get(neighborPacked)) continue;
            if (config.getRestrictedPointsPacked().contains(neighborPacked)) continue;
            if (config.getCustomRestrictions().contains(neighborPacked)) continue;

            if (ignoreCollision.contains(new WorldPoint(x, y, z))) {
                neighbors.add(new Node(neighborPacked, node));
                continue;
            }

            /**
             * This piece of code is designed to allow web walker to be used in toa puzzle room
             * it will dodge specific tiles in the sequence room
             */
            if (Rs2Player.getWorldLocation().getRegionID() == 14162) { //toa puzzle room
                final int lx = WorldPointUtil.unpackWorldX(neighborPacked);
                final int ly = WorldPointUtil.unpackWorldY(neighborPacked);
                final int lz = WorldPointUtil.unpackWorldPlane(neighborPacked);
                if (targets.stream().noneMatch(tgts -> Objects.equals(tgts, new WorldPoint(lx, ly, lz)))) {
                    WorldPoint globalWorldPoint = Rs2WorldPoint.convertInstancedWorldPoint(new WorldPoint(lx, ly, lz));
                    if (globalWorldPoint != null) {
                        TileObject go = Rs2GameObject.findGroundObjectByLocation(globalWorldPoint);
                        if (go != null && go.getId() == 45340) {
                            continue;
                        }
                    }
                }
            }

            if (traversable[i]) {
                neighbors.add(new Node(neighborPacked, node));
            } else if (Math.abs(d.x + d.y) == 1 && isBlocked(x + d.x, y + d.y, z)) {
                // The transport starts from a blocked adjacent tile, e.g. fairy ring
                // Only checks non-teleport transports (includes portals and levers, but not items and spells)
                @SuppressWarnings("unchecked") // Casting EMPTY_LIST to List<Transport> is safe here
                Set<Transport> neighborTransports = config.getTransportsPacked().getOrDefault(neighborPacked, (Set<Transport>)Collections.EMPTY_SET);
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
}
