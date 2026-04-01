package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class Pathfinder implements Runnable {
    private PathfinderStats stats;
    @Getter
    private volatile boolean done = false;
    private volatile boolean cancelled = false;

    private final int start;
    private final Set<Integer> targets;

    private final PathfinderConfig config;
    private final CollisionMap map;
    private final boolean targetInWilderness;

    // Capacities should be enough to store all nodes without requiring the queue to grow
    // They were found by checking the max queue size
    private final Deque<Node> boundary = new ArrayDeque<>(4096);
    private final Queue<Node> pending = new PriorityQueue<>(256);
    private final VisitedTiles visited;

    private volatile List<WorldPoint> path = Collections.emptyList();
    private boolean pathNeedsUpdate = false;
    private volatile Node bestLastNode;
    /**
     * Teleportation transports are updated when this changes.
     * Can be either:
     * 20 = all teleports can be used (e.g. Varrock Teleport)
     * 30 = some teleports can be used (e.g. Amulet of Glory)
     * 31 = no teleports can be used
     */
    private int wildernessLevel;

    public Pathfinder(PathfinderConfig config, int start, Set<Integer> targets) {
        stats = new PathfinderStats();
        this.config = config;
        this.map = config.getMap();
        this.start = start;
        this.targets = targets;
        visited = new VisitedTiles(map);
        targetInWilderness = PathfinderConfig.isInWildernessPackedPoint(targets);
        wildernessLevel = 31;
        log.debug("Created Pathfinder src={} dst={} config={}",
                WorldPointUtil.toString(this.start),
                WorldPointUtil.toString(this.targets),
                config
        );
    }

    public Pathfinder(PathfinderConfig config, WorldPoint start, Set<WorldPoint> targets) {
        this(config, WorldPointUtil.packWorldPoint(start), targets.stream().map(WorldPointUtil::packWorldPoint).collect(Collectors.toSet()));
    }

    public Pathfinder(PathfinderConfig config, WorldPoint start, WorldPoint target) {
        this(config, start, Set.of(target));
    }

    public WorldPoint getStart() {
        return WorldPointUtil.unpackWorldPoint(start);
    }

    public Set<WorldPoint> getTargets() {
        return targets.stream().map(WorldPointUtil::unpackWorldPoint).collect(Collectors.toSet());
    }

    public void cancel() {
        cancelled = true;
    }

    public PathfinderStats getStats() {
        if (stats.started && stats.ended) {
            return stats;
        }

        // Don't give incomplete results
        return null;
    }

    public List<WorldPoint> getPath() {
        if (!done && !cancelled) throw new IllegalStateException("Pathfinder is not done");
        if (cancelled) {
            log.warn("Getting cancelled path");
        }
        Node lastNode = bestLastNode; // For thread safety, read bestLastNode once
        if (lastNode == null) {
            return path;
        }

        if (pathNeedsUpdate) {
            path = lastNode.getPath();
            pathNeedsUpdate = false;
        }

        return path;
    }

    private void addNeighbors(Node node) {
        List<Node> nodes = map.getNeighbors(node, visited, config, targets);
        for (Node neighbor : nodes) {
            if (config.avoidWilderness(node.packedPosition, neighbor.packedPosition, targetInWilderness)) {
                continue;
            }

            visited.set(neighbor.packedPosition);
            if (neighbor instanceof TransportNode) {
                pending.add(neighbor);
                ++stats.transportsChecked;
            } else {
                boundary.addLast(neighbor);
                ++stats.nodesChecked;
            }
        }
    }

    @Override
    public void run() {
        log.info("[Pathfinder] run() started: src={}, dst={}, cutoff={}ms",
                WorldPointUtil.toString(start), WorldPointUtil.toString(targets), config.getCalculationCutoffMillis());
        try {
            stats.start();
            boundary.addFirst(new Node(start, null));

            int bestDistance = Integer.MAX_VALUE;
            long bestHeuristic = Integer.MAX_VALUE;
            long cutoffDurationMillis = config.getCalculationCutoffMillis();
            long cutoffTimeMillis = System.currentTimeMillis() + cutoffDurationMillis;

            config.refreshTeleports(start, 31);
            while (!cancelled && (!boundary.isEmpty() || !pending.isEmpty())) {
                Node node = boundary.peekFirst();
                Node p = pending.peek();

                if (p != null && (node == null || p.cost < node.cost)) {
                    node = pending.poll();
                } else {
                    node = boundary.removeFirst();
                }

                if (wildernessLevel > 0) {
                    boolean update = false;

                    if (wildernessLevel > 29 && !config.isInLevel29Wilderness(node.packedPosition)) {
                        wildernessLevel = 29;
                        update = true;
                    }
                    if (wildernessLevel > 19 && !config.isInLevel19Wilderness(node.packedPosition)) {
                        wildernessLevel = 19;
                        update = true;
                    }
                    if (wildernessLevel > 0 && !PathfinderConfig.isInWilderness(node.packedPosition)) {
                        wildernessLevel = 0;
                        update = true;
                    }
                    if (update) {
                        config.refreshTeleports(node.packedPosition, wildernessLevel);
                    }
                }

                if (targets.contains(node.packedPosition)) {
                    bestLastNode = node;
                    pathNeedsUpdate = true;
                    break;
                }

                for (int target : targets) {
                    int distance = WorldPointUtil.distanceBetween(node.packedPosition, target);
                    long heuristic = distance + (long) WorldPointUtil.distanceBetween(node.packedPosition, target, 2);

                    if (heuristic < bestHeuristic || (heuristic <= bestHeuristic && distance < bestDistance)) {

                        bestLastNode = node;
                        pathNeedsUpdate = true;
                        bestDistance = distance;
                        bestHeuristic = heuristic;
                        cutoffTimeMillis = System.currentTimeMillis() + cutoffDurationMillis;
                    }
                }

                if (System.currentTimeMillis() > cutoffTimeMillis) {
                    log.info("[Pathfinder] Cutoff reached. bestDistance={}, nodesChecked={}", bestDistance, stats.getNodesChecked());
                    break;
                }

                addNeighbors(node);
            }

            log.info("[Pathfinder] Loop exited. cancelled={}, boundaryEmpty={}, pendingEmpty={}, bestLastNode={}",
                    cancelled, boundary.isEmpty(), pending.isEmpty(),
                    bestLastNode == null ? "null" : WorldPointUtil.toString(bestLastNode.packedPosition));
        } catch (Exception e) {
            log.error("[Pathfinder] Exception in run(): ", e);
        } finally {
            done = !cancelled;

            boundary.clear();
            visited.clear();
            pending.clear();

            stats.end();

            log.info("[Pathfinder] run() completed. done={}, cancelled={}, stats={}",
                    done, cancelled, getStats() != null ? getStats().toString() : "null");
        }
    }

    public static class PathfinderStats {
        @Getter
        private int nodesChecked = 0, transportsChecked = 0;
        private long startNanos, endNanos;
        private volatile boolean started = false, ended = false;

        public int getTotalNodesChecked() {
            return nodesChecked + transportsChecked;
        }

        public long getElapsedTimeNanos() {
            return endNanos - startNanos;
        }

        private void start() {
            started = true;
            nodesChecked = 0;
            transportsChecked = 0;
            startNanos = System.nanoTime();
        }

        private void end() {
            endNanos = System.nanoTime();
            ended = true;
        }

        @Override
        public String toString() {
            return String.format("PathfinderStats(nodes=%d,transports=%d,time=%dms)", nodesChecked, transportsChecked, getElapsedTimeNanos() / 1_000_000);
        }
    }
}
