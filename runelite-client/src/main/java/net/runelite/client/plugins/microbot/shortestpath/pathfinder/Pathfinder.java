package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
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
    // Primitive view of targets — iterated on every popped node in run().
    // Avoids autoboxing vs. iterating Set<Integer>.
    private final int[] targetsPacked;

    private final PathfinderConfig config;
    private final CollisionMap map;
    private final boolean targetInWilderness;

    // Walking subgraph uses A* (boundary is a PQ keyed on f = g + Chebyshev heuristic),
    // so among walking nodes the search picks the most promising direction first.
    // Transports stay in a separate PQ keyed on g-cost only — they're picked when their
    // travel cost is cheaper than any frontier walking node's g-cost, preserving the
    // existing "try cheap transports before walking farther" selection behavior.
    //
    // Comparator chain is (fCost, gCost, tiebreaker):
    //   1. fCost — standard A* primary ordering.
    //   2. gCost — required for correctness under early-discovery. addNeighbors() marks
    //      a neighbor visited at insert time (not at pop), so a node only ever enters
    //      the PQ once. If two equal-fCost nodes have different gCost, popping the
    //      higher-gCost one first would fix their shared neighbor's gCost to a
    //      suboptimal value (because visited is already set when the lower-g node later
    //      tries to discover the same neighbor). Preferring lower gCost on ties keeps
    //      early-discovery optimal.
    //   3. tiebreaker — per-node random. Among nodes with identical (f, g) — common in
    //      open-grid regions where many tiles share the same distance-from-start and
    //      distance-to-goal — this rotates the exploration order each run so paths
    //      diverge tile-by-tile between successive searches with the same endpoints.
    //      Kills the deterministic "identical route every trip" fingerprint.
    private final Queue<Node> boundary = new PriorityQueue<>(4096,
            Comparator.comparingInt(Node::fCost)
                    .thenComparingInt(n -> n.cost)
                    .thenComparingInt(n -> n.tiebreaker));
    private final Queue<Node> pending = new PriorityQueue<>(256);
    private final VisitedTiles visited;

    private volatile List<WorldPoint> path = Collections.emptyList();
    private volatile List<WorldPoint> smoothedPath = Collections.emptyList();
    private volatile boolean pathNeedsUpdate = false;
    private volatile boolean smoothed = false;
    private volatile Node bestLastNode;
    // True when the BFS exhausted both boundary and pending without reaching a
    // target — the destination is genuinely unreachable from the source given
    // the current transport graph. Distinguished from cutoff timeout (queues
    // still hold candidates) and target-found (loop broke early). Walker uses
    // this to bail UNREACHABLE instead of walking a partial path toward the
    // closest-by-heuristic node, which on a quest-locked area produced a
    // 60-second tour around the locked region in the wild.
    @Getter
    private volatile boolean searchExhausted = false;
    /**
     * Teleportation transports are updated when this changes.
     * Can be either:
     * 0 = all teleports can be used (e.g. Chronicle)
     * 20 = most teleports can be used (e.g. Varrock Teleport)
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
        this.targetsPacked = new int[targets.size()];
        int idx = 0;
        for (Integer t : targets) {
            this.targetsPacked[idx++] = t;
        }
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
        Node lastNode = bestLastNode; // For thread safety, read bestLastNode once
        if (lastNode == null) {
            return path;
        }

        if (pathNeedsUpdate) {
            path = lastNode.getPath();
            pathNeedsUpdate = false;
            smoothed = false;
        }

        return path;
    }

    /**
     * Smoothed view of {@link #getPath()} for walker consumption. Collapses
     * straight-line runs of adjacent tiles using line-of-sight checks so that
     * the walker's main loop iterates fewer waypoints and issues fewer
     * minimap clicks. Falls back to the raw path while the pathfinder is
     * still running (smoothing only runs once after completion).
     */
    public List<WorldPoint> getWalkablePath() {
        List<WorldPoint> raw = getPath();
        if (!done || raw == null || raw.isEmpty()) {
            return raw;
        }
        if (!smoothed) {
            smoothedPath = PathSmoother.smooth(raw, map, buildTransportAnchors(raw));
            smoothed = true;
        }
        return smoothedPath;
    }

    // Tiles in the raw path that are transport origins or destinations. The smoother
    // must not collapse across these — some gates (e.g., Tutorial Island rat-cage
    // gates) aren't encoded as collision walls, so canStep would otherwise glide
    // past the transport edge and hide the gate from the walker.
    private Set<WorldPoint> buildTransportAnchors(List<WorldPoint> path) {
        Map<WorldPoint, Set<Transport>> transports = config.getTransports();
        if (transports == null || transports.isEmpty() || path == null || path.isEmpty()) {
            return Collections.emptySet();
        }
        Set<WorldPoint> anchors = new HashSet<>();
        for (WorldPoint p : path) {
            Set<Transport> fromP = transports.get(p);
            if (fromP == null) continue;
            anchors.add(p);
            for (Transport t : fromP) {
                WorldPoint dest = t.getDestination();
                if (dest != null) anchors.add(dest);
            }
        }
        return anchors;
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
                neighbor.heuristic = heuristicToNearestTarget(neighbor.packedPosition);
                boundary.add(neighbor);
                ++stats.nodesChecked;
            }
        }
    }

    // Admissible A* heuristic: Chebyshev 2D to the nearest target, with a modulo-6400
    // fallback for the surface↔underground Y-offset convention (OSRS shifts underground
    // coords by +6400 on the Y axis, so Varrock sewers live at y≈9800 while Varrock sits
    // at y≈3400). Plain Chebyshev would claim ~6200 tiles to any underground point, which
    // misdirects A* into expanding the surface southward instead of routing through a
    // nearby ladder/stairs transport. Taking min(direct, mod-6400) stays admissible
    // because reaching a y-mirrored underground point still requires ≥ one transport
    // (cost ≥ 0) on top of the mod-6400 walking distance.
    private static final int UNDERGROUND_Y_OFFSET = 6400;

    private int heuristicToNearestTarget(int packedPos) {
        int posX = WorldPointUtil.unpackWorldX(packedPos);
        int posY = WorldPointUtil.unpackWorldY(packedPos);
        int best = Integer.MAX_VALUE;
        for (int target : targetsPacked) {
            int tx = WorldPointUtil.unpackWorldX(target);
            int ty = WorldPointUtil.unpackWorldY(target);
            int dx = Math.abs(posX - tx);
            int direct = Math.max(dx, Math.abs(posY - ty));
            int wrapped = Math.max(dx, Math.abs(((posY % UNDERGROUND_Y_OFFSET) - (ty % UNDERGROUND_Y_OFFSET))));
            int h = Math.min(direct, wrapped);
            if (h < best) {
                best = h;
            }
        }
        return best;
    }

    @Override
    public void run() {
        log.info("[Pathfinder] run() started: src={}, dst={}, cutoff={}ms",
                WorldPointUtil.toString(start), WorldPointUtil.toString(targets), config.getCalculationCutoffMillis());
        try {
            stats.start();
            Node startNode = new Node(start, null);
            startNode.heuristic = heuristicToNearestTarget(start);
            boundary.add(startNode);

            int bestDistance = Integer.MAX_VALUE;
            long bestHeuristic = Integer.MAX_VALUE;
            long cutoffDurationMillis = config.getCalculationCutoffMillis();
            long cutoffTimeMillis = System.currentTimeMillis() + cutoffDurationMillis;
            config.refreshTeleports(start, 31);
            while (!cancelled && (!boundary.isEmpty() || !pending.isEmpty())) {
                Node b = boundary.peek();
                Node p = pending.peek();
                Node node;
                if (p != null && (b == null || p.cost < b.cost)) {
                    node = pending.poll();
                } else {
                    node = boundary.poll();
                }

                if (wildernessLevel > 0) {
                    boolean update = false;

                    if (wildernessLevel > 30 && !config.isInLevel30Wilderness(node.packedPosition)) {
                        wildernessLevel = 30;
                        update = true;
                    }
                    if (wildernessLevel > 20 && !config.isInLevel20Wilderness(node.packedPosition)) {
                        wildernessLevel = 20;
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

                final int nodePos = node.packedPosition;
                boolean reached = false;
                for (int target : targetsPacked) {
                    if (nodePos == target) {
                        bestLastNode = node;
                        pathNeedsUpdate = true;
                        reached = true;
                        break;
                    }
                    int distance = WorldPointUtil.distanceBetween(nodePos, target);
                    long heuristic = distance + (long) WorldPointUtil.distanceBetween(nodePos, target, 2);
                    if (heuristic < bestHeuristic || (heuristic <= bestHeuristic && distance < bestDistance)) {
                        bestLastNode = node;
                        pathNeedsUpdate = true;
                        bestDistance = distance;
                        bestHeuristic = heuristic;
                        cutoffTimeMillis = System.currentTimeMillis() + cutoffDurationMillis;
                    }
                }
                if (reached) break;

                if (System.currentTimeMillis() > cutoffTimeMillis) {
                    log.info("[Pathfinder] Cutoff reached. bestDistance={}, nodesChecked={}", bestDistance, stats.getNodesChecked());
                    break;
                }

                addNeighbors(node);
            }

            // Capture exhaustion before finally clears the queues. Both empty
            // after the while loop means BFS terminated by the loop condition
            // (no more nodes to expand) — i.e. dest is unreachable. Cutoff and
            // target-reached exits leave queues non-empty.
            searchExhausted = !cancelled && boundary.isEmpty() && pending.isEmpty();
            log.info("[Pathfinder] Loop exited. cancelled={}, boundaryEmpty={}, pendingEmpty={}, bestLastNode={}",
                    cancelled, boundary.isEmpty(), pending.isEmpty(),
                    bestLastNode == null ? "null" : WorldPointUtil.toString(bestLastNode.packedPosition));
        } catch (Exception e) {
            log.error("[Pathfinder] Exception in run(): ", e);
        } finally {
            done = !cancelled;

            boundary.clear();
            pending.clear();
            visited.clear();

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
