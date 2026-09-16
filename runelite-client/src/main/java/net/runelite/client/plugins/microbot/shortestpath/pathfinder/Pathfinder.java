package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;
import net.runelite.client.plugins.microbot.util.walker.WebWalkLog;

import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.event.Level;

@Slf4j
public class Pathfinder implements Runnable {
    /**
     * Detailed pathfinder traces — set logger {@code net.runelite.client.plugins.microbot.shortestpath.pathfinder}
     * to DEBUG, or use {@link Microbot#log(org.slf4j.event.Level, String, Object...)} routing via Microbot.
     */
    private static void pathfinderDiag(String format, Object... args) {
        Microbot.log(Level.DEBUG, "[PathfinderDiag] " + format, args);
    }

    private static final Comparator<Node> NODE_ORDER = Comparator
            .comparingInt((Node n) -> n.cost)
            .thenComparingInt(n -> n.tiebreaker);

    /**
     * Bidirectional search only for single-target routes at least this Chebyshev distance apart.
     * Medium-range paths often expand fewer nodes unidirectionally; very long routes (e.g. surface↔underground)
     * benefit from meet-in-the-middle. Wilderness {@code refreshTeleports} stays forward-only.
     */
    private static final int BIDIRECTIONAL_MIN_CHEBYSHEV = 2000;
    private PathfinderStats stats;
    @Getter
    private volatile boolean done = false;
    private volatile boolean cancelled = false;
    @Getter
    private volatile PathTerminationReason terminationReason;
    /** Search cost of the returned raw path, or {@code -1} when no path node was selected. */
    @Getter
    private volatile long selectedPathCost = -1L;

    private final int start;
    private final Set<Integer> targets;
    // Primitive view of targets — iterated on every popped node in run().
    // Avoids autoboxing vs. iterating Set<Integer>.
    private final int[] targetsPacked;

    private final PathfinderConfig config;
    private CollisionMap map;
    private final boolean targetInWilderness;

    // Both walking and transport frontiers are ordered by travelled cost. A geometric
    // heuristic is not admissible in a graph containing canoes, teleports and other
    // long-distance edges: it can permanently visit a farther transport origin before
    // a cheaper origin whose straight-line direction initially points away from the
    // target. Cost ordering matches the reviewed upstream search semantics and keeps
    // exact selected transport identity stable across the engine boundary.
    //
    // Comparator chain is (gCost, tiebreaker):
    //   1. gCost — required for correctness because addNeighbors() marks a neighbor
    //      visited at insert time and therefore never relaxes it later.
    //   2. tiebreaker — per-node random. Among nodes with identical cost — common in
    //      open-grid regions — this rotates the exploration order each run so paths
    //      diverge tile-by-tile between successive searches with the same endpoints.
    //      Kills the deterministic "identical route every trip" fingerprint.
    private final Queue<Node> boundary = new PriorityQueue<>(4096, NODE_ORDER);
    private final Queue<Node> pending = new PriorityQueue<>(256, NODE_ORDER);
    private final Queue<Node> boundaryBackward = new PriorityQueue<>(4096, NODE_ORDER);
    private final Queue<Node> pendingBackward = new PriorityQueue<>(256, NODE_ORDER);
    private VisitedTiles visited;

    private volatile List<WorldPoint> path = Collections.emptyList();
    private volatile List<WorldPoint> smoothedPath = Collections.emptyList();
    /** Node identity represented by {@link #path}; avoids a lost-update race with live path readers. */
    private volatile Node materializedPathLastNode;
    private volatile boolean smoothed = false;
    private volatile Node bestLastNode;
    /** When set, {@link #getPath()} returns this list (bidirectional join or early exact hit). */
    private volatile List<WorldPoint> joinedPath;
    /** Edge-preserving counterpart to {@link #joinedPath}. */
    private volatile List<PathEdge> joinedPathEdges;
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
        this.start = start;
        this.targets = targets;
        this.targetsPacked = new int[targets.size()];
        int idx = 0;
        for (Integer t : targets) {
            this.targetsPacked[idx++] = t;
        }
        targetInWilderness = PathfinderConfig.isInWildernessPackedPoint(targets);
        wildernessLevel = 31;
        WebWalkLog.pf("created src={} dst={} config={}",
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

    /**
     * Materialize a completed planner-independent route behind the legacy concrete pathfinder surface.
     *
     * <p>This is a transitional adapter for the shortest-path overlays and out-of-tree callers that still
     * consume {@code ShortestPathPlugin.pathfinder}. New walker code must consume the immutable route
     * contract instead. Remove this factory with the local planner after the staged rollout sunset.</p>
     */
    public static Pathfinder completedRoute(
            PathfinderConfig config,
            WorldPoint start,
            Set<WorldPoint> targets,
            List<WorldPoint> path,
            List<Transport> transportsByStep,
            PathTerminationReason terminationReason,
            long selectedPathCost,
            long searchNanos,
            long nodesChecked,
            long transportsChecked,
            long liveCollisionEdgesChecked) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(targets, "targets");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(transportsByStep, "transportsByStep");
        Objects.requireNonNull(terminationReason, "terminationReason");
        if (!path.isEmpty() && !start.equals(path.get(0))) {
            throw new IllegalArgumentException("materialized route must start at the requested start");
        }
        if (terminationReason == PathTerminationReason.TARGET_REACHED
                && (path.isEmpty() || !targets.contains(path.get(path.size() - 1)))) {
            throw new IllegalArgumentException("reached route must end at a requested target");
        }
        if (selectedPathCost < -1L || searchNanos < -1L || nodesChecked < -1L
                || transportsChecked < -1L || liveCollisionEdgesChecked < -1L) {
            throw new IllegalArgumentException("materialized route metrics must be non-negative or unavailable");
        }

        Pathfinder completed = new Pathfinder(config, start, targets);
        List<WorldPoint> immutablePath = Collections.unmodifiableList(new ArrayList<>(path));
        completed.map = config.getMap();
        completed.joinedPath = immutablePath;
        completed.joinedPathEdges = PathEdge.fromMaterializedRoute(immutablePath, transportsByStep);
        completed.terminationReason = terminationReason;
        completed.selectedPathCost = selectedPathCost;
        completed.cancelled = false;
        completed.done = true;
        completed.stats.complete(
                metricOrZero(searchNanos),
                metricAsInt(nodesChecked),
                metricAsInt(transportsChecked),
                metricOrZero(liveCollisionEdgesChecked));
        return completed;
    }

    private static long metricOrZero(long metric) {
        return metric < 0L ? 0L : metric;
    }

    private static int metricAsInt(long metric) {
        if (metric < 0L) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, metric);
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
        List<WorldPoint> joined = joinedPath;
        if (joined != null) {
            return joined;
        }
        Node lastNode = bestLastNode; // For thread safety, read bestLastNode once
        if (lastNode == null) {
            return path;
        }

        List<WorldPoint> currentPath = path;
        if (materializedPathLastNode != lastNode) {
            // The walker may read a partial path while this search is still running. Identity-based
            // invalidation is required here: a reader clearing a shared dirty flag can otherwise erase
            // a newer pathfinder-thread update, leaving getPath() and getPathEdges() on different nodes.
            currentPath = Collections.unmodifiableList(lastNode.getPath());
            path = currentPath;
            materializedPathLastNode = lastNode;
            smoothed = false;
        }

        return currentPath;
    }

    /**
     * Materialized edges for the current best route. Transport edges retain the exact catalog entry
     * selected by the search; callers outside shortest-path should map them to owned immutable values.
     */
    public List<PathEdge> getPathEdges() {
        List<PathEdge> joined = joinedPathEdges;
        if (joined != null) {
            return joined;
        }
        return PathEdge.fromForwardChain(bestLastNode);
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
            smoothedPath = PathSmoother.smooth(raw, map, buildTransportAnchors(raw), config.getBlockedTransportEdgesPacked());
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
                boundary.add(neighbor);
                ++stats.nodesChecked;
            }
        }
    }

    private int minChebyshevStartToAnyTarget() {
        int best = Integer.MAX_VALUE;
        for (int t : targetsPacked) {
            int d = Math.max(
                    Math.abs(WorldPointUtil.unpackWorldX(start) - WorldPointUtil.unpackWorldX(t)),
                    Math.abs(WorldPointUtil.unpackWorldY(start) - WorldPointUtil.unpackWorldY(t)));
            if (d < best) {
                best = d;
            }
        }
        return best;
    }

    // ---- sealed-target fast path ---------------------------------------------------------------

    /** Reverse-flood budget: clears any fenced yard or walled room in well under this, ~1-3ms. */
    private static final int SEALED_PROBE_NODE_BUDGET = 1024;
    private static final int SEALED_SUBSTITUTE_TARGET_CAP = 8;
    /** The rim substitutes can themselves prove unreachable; they get a short leash, not 18s. */
    private static final long SEALED_SUBSTITUTE_CUTOFF_MS = 2_000L;
    /**
     * Node budget for the substitute pass. The time leash alone still allowed a 2-million-node flood
     * (a sealed moat tile whose rim is itself an unreachable pocket): two seconds at search speed IS
     * the flood. Truncating a genuinely long approach to a sealed destination is fine — the walker
     * walks the partial path, replans closer, and the next probe answers from nearer.
     */
    private static final long SEALED_SUBSTITUTE_NODE_BUDGET = 50_000L;

    /** The targets the search LOOPS actually chase; equals {@link #targetsPacked} except in sealed mode. */
    private volatile int[] searchTargetsPacked;
    private volatile boolean sealedTargetMode;
    private long cutoffOverrideMillis = -1L;
    /** See {@link #getReachedSealedSubstitute()}. Volatile: written by the search thread, read by the walker. */
    private volatile int reachedSealedSubstitutePacked = -1;

    private long effectiveCutoffMillis() {
        long configured = config.getCalculationCutoffMillis();
        return cutoffOverrideMillis > 0 ? Math.min(cutoffOverrideMillis, configured) : configured;
    }

    /**
     * The rim substitute the sealed-target search actually REACHED, or {@code null}. Non-null only
     * when the destination was proven sealed AND the substitute pass ended ON a walkable rim tile —
     * i.e. this run's path is a complete route to the closest standable spot beside the sealed
     * pocket. The walker uses it to retarget the walk to the rim ONCE: without that, the
     * SEARCH_EXHAUSTED termination made every pass treat the plan as partial and replan it, and
     * each replan re-ran this substitute search — the 17:54 walk burned 50k nodes per replan
     * crawling ~300 tiles toward a destination one tile inside a fence.
     */
    public WorldPoint getReachedSealedSubstitute() {
        int packed = reachedSealedSubstitutePacked;
        return packed == -1 ? null : WorldPointUtil.unpackWorldPoint(packed);
    }

    /**
     * The nearest normalized walkable rim tile of a proven-sealed destination, even when the
     * bounded substitute search did not reach it. The walker can plan this ordinary tile with its
     * normal full budget instead of chaining partial sealed-target searches.
     */
    public WorldPoint getNearestSealedRimSubstitute() {
        int[] chased = searchTargetsPacked;
        if (!sealedTargetMode || chased == null || chased.length == 0) {
            return null;
        }
        return WorldPointUtil.unpackWorldPoint(chased[0]);
    }

    /**
     * Bounded reverse flood from the single target, deciding whether its graph component is provably
     * SEALED — unreachable by walking, by any transport whose origin exists, and not landed in by any
     * anywhere-teleport.
     * <p>
     * Exists because an unreachable destination made the forward search flood the ENTIRE world
     * component before giving up: measured 37 times in one evening at ~1.1M nodes and 1.2-3.8s of CPU
     * each, mostly for destinations TWO TILES away (a sealed map-data tile, or an interaction target
     * the caller asked for by coordinate). The reverse flood explores only the target's own component,
     * which for every observed case is tiny, and answers in ~1ms.
     * <p>
     * Correctness leans on three things. The flood uses {@code getReverseNeighbors} with the
     * incoming-transports index, so a room entered by a staircase or door transport GROWS past its
     * walls and reads reachable — an upstairs destination is never falsely sealed. Anywhere-teleports
     * (null origin, excluded from that index) are checked per component tile instead. And the budget
     * makes big components INCONCLUSIVE rather than sealed: only a frontier that genuinely drains
     * under budget without touching {@code start} proves anything.
     *
     * @return {@code null} when reachable or inconclusive (run the normal search); otherwise the
     * component's walkable rim — same-plane cardinal neighbours just outside it with at least one
     * open edge — nearest-first to the goal, possibly empty (a void tile with a void rim).
     */
    private int[] sealedTargetSubstitutes(int goalPacked) {
        final Map<Integer, Set<Transport>> incoming = new HashMap<>(512);
        final Set<Integer> anywhereTeleportDests = new HashSet<>();
        for (Map.Entry<WorldPoint, Set<Transport>> e : config.getTransports().entrySet()) {
            for (Transport t : e.getValue()) {
                if (t.getDestination() == null) {
                    continue;
                }
                int dp = WorldPointUtil.packWorldPoint(t.getDestination());
                if (t.getOrigin() == null) {
                    anywhereTeleportDests.add(dp);
                } else {
                    incoming.computeIfAbsent(dp, k -> new HashSet<>()).add(t);
                }
            }
        }
        final Set<Integer> puzzleAllow = new HashSet<>(4);
        puzzleAllow.add(goalPacked);
        puzzleAllow.add(start);
        final VisitedTiles probeVisited = new VisitedTiles(map);
        final ArrayDeque<Node> frontier = new ArrayDeque<>();
        final Set<Integer> component = new LinkedHashSet<>();
        frontier.add(new Node(goalPacked, null));
        probeVisited.set(goalPacked);
        int expanded = 0;
        while (!frontier.isEmpty()) {
            if (expanded >= SEALED_PROBE_NODE_BUDGET) {
                return null; // big component: inconclusive, let the real search decide
            }
            Node n = frontier.poll();
            expanded++;
            if (anywhereTeleportDests.contains(n.packedPosition)) {
                return null; // an anywhere-teleport lands inside: reachable
            }
            component.add(n.packedPosition);
            for (Node pred : map.getReverseNeighbors(n, probeVisited, config, puzzleAllow, incoming)) {
                if (pred.packedPosition == start) {
                    return null; // reachable
                }
                probeVisited.set(pred.packedPosition);
                frontier.add(pred);
            }
        }

        final int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        final Set<Integer> rim = new LinkedHashSet<>();
        for (int packed : component) {
            final int x = WorldPointUtil.unpackWorldX(packed);
            final int y = WorldPointUtil.unpackWorldY(packed);
            final int z = WorldPointUtil.unpackWorldPlane(packed);
            for (int[] d : dirs) {
                final int nx = x + d[0];
                final int ny = y + d[1];
                final int np = WorldPointUtil.packWorldPoint(nx, ny, z);
                if (component.contains(np) || rim.contains(np)) {
                    continue;
                }
                for (int[] out : dirs) {
                    if (map.canStep(nx, ny, z, out[0], out[1])) {
                        rim.add(np);
                        break;
                    }
                }
            }
        }
        // Nearest to START, not to the goal: the reachable rim is on the approach side, and ranking
        // it first lets the substitute search REACH a target in hundreds of nodes. Goal-side rim
        // tiles are usually inside the sealed pocket's far side — unreachable by construction — and
        // ranking them first burned the whole substitute node budget on best-effort (measured 50k
        // nodes at Shantay Pass vs a direct walk to the near-side rim).
        final List<Integer> nearest = new ArrayList<>(rim);
        nearest.sort(Comparator.comparingInt(p -> WorldPointUtil.distanceBetween(p, start)));
        final int take = Math.min(SEALED_SUBSTITUTE_TARGET_CAP, nearest.size());
        final int[] substitutes = new int[take];
        for (int i = 0; i < take; i++) {
            substitutes[i] = nearest.get(i);
        }
        WebWalkLog.pf("target_sealed dst={} component={} rim={} probeNodes={}",
                WorldPointUtil.toString(goalPacked), component.size(), rim.size(), expanded);
        return substitutes;
    }

    /**
     * A sealed component's nearest rim tile can itself be another sealed map-data tile. Resolve
     * those nested shells here, before publishing a substitute to the walker, so one requested goal
     * produces one effective approach target instead of a chain of recursive retargets.
     */
    private int[] normalizeSealedRim(int[] initial) {
        List<Integer> current = Arrays.stream(initial).boxed().collect(Collectors.toList());
        Set<Integer> probed = new HashSet<>();
        for (int depth = 0; depth < 4 && !current.isEmpty(); depth++) {
            int candidate = current.get(0);
            if (!probed.add(candidate)) {
                break;
            }
            int[] nested = sealedTargetSubstitutes(candidate);
            if (nested == null || nested.length == 0) {
                break;
            }
            LinkedHashSet<Integer> next = new LinkedHashSet<>(current);
            next.remove(candidate);
            for (int substitute : nested) {
                if (!probed.contains(substitute)) {
                    next.add(substitute);
                }
            }
            current = new ArrayList<>(next);
            current.sort(Comparator.comparingInt(p -> WorldPointUtil.distanceBetween(p, start)));
            if (current.size() > SEALED_SUBSTITUTE_TARGET_CAP) {
                current = new ArrayList<>(current.subList(0, SEALED_SUBSTITUTE_TARGET_CAP));
            }
        }
        return current.stream().mapToInt(Integer::intValue).toArray();
    }

    private void buildIncomingByDestination(Map<Integer, Set<Transport>> out) {
        out.clear();
        for (Map.Entry<WorldPoint, Set<Transport>> e : config.getTransports().entrySet()) {
            for (Transport t : e.getValue()) {
                if (t.getDestination() == null || t.getOrigin() == null) {
                    continue;
                }
                int dp = WorldPointUtil.packWorldPoint(t.getDestination());
                out.computeIfAbsent(dp, k -> new HashSet<>()).add(t);
            }
        }
    }

    private static void maybeImproveMeeting(Node forwardNode, Node backwardNode, long[] bestCost, Node[] bestForward, Node[] bestBackward) {
        long sum = (long) forwardNode.cost + (long) backwardNode.cost;
        if (sum < bestCost[0]) {
            bestCost[0] = sum;
            bestForward[0] = forwardNode;
            bestBackward[0] = backwardNode;
        }
    }

    private List<WorldPoint> combineBidirectionalPath(Node forwardAtMeet, Node backwardAtMeet) {
        List<WorldPoint> head = forwardAtMeet.getPath();
        List<WorldPoint> full = new ArrayList<>(head.size() + 64);
        full.addAll(head);

        List<PathEdge> edges = PathEdge.fromBidirectionalChains(forwardAtMeet, backwardAtMeet);
        for (Node from = backwardAtMeet; from != null && from.previous != null; from = from.previous) {
            Node to = from.previous;
            full.add(WorldPointUtil.unpackWorldPoint(to.packedPosition));
        }
        joinedPathEdges = edges;
        return full;
    }

    private void addNeighborsForwardWithMeet(Node node, Map<Integer, Node> forwardAt, Map<Integer, Node> backwardAt,
            long[] bestMeetingCost, Node[] meetF, Node[] meetB) {
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
                boundary.add(neighbor);
                ++stats.nodesChecked;
            }
            forwardAt.putIfAbsent(neighbor.packedPosition, neighbor);
            Node b = backwardAt.get(neighbor.packedPosition);
            if (b != null) {
                maybeImproveMeeting(neighbor, b, bestMeetingCost, meetF, meetB);
            }
        }
    }

    private void addNeighborsBackwardWithMeet(Node node, VisitedTiles visitedB, Map<Integer, Set<Transport>> incoming,
            Set<Integer> puzzleAllow, Map<Integer, Node> forwardAt, Map<Integer, Node> backwardAt,
            long[] bestMeetingCost, Node[] meetF, Node[] meetB) {
        List<Node> nodes = map.getReverseNeighbors(node, visitedB, config, puzzleAllow, incoming);
        for (Node pred : nodes) {
            if (config.avoidWilderness(pred.packedPosition, node.packedPosition, targetInWilderness)) {
                continue;
            }

            visitedB.set(pred.packedPosition);
            if (pred instanceof TransportNode) {
                pendingBackward.add(pred);
                ++stats.transportsChecked;
            } else {
                boundaryBackward.add(pred);
                ++stats.nodesChecked;
            }
            backwardAt.putIfAbsent(pred.packedPosition, pred);
            Node f = forwardAt.get(pred.packedPosition);
            if (f != null) {
                maybeImproveMeeting(f, pred, bestMeetingCost, meetF, meetB);
            }
        }
    }

    private void runUnidirectional() {
        Node startNode = new Node(start, null);
        boundary.add(startNode);

        int bestDistance = Integer.MAX_VALUE;
        long bestHeuristic = Integer.MAX_VALUE;
        long cutoffDurationMillis = effectiveCutoffMillis();
        long cutoffTimeMillis = System.currentTimeMillis() + cutoffDurationMillis;
        config.refreshTeleports(start, 31);
        boolean reachedGoal = false;
        boolean timedOut = false;
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
            for (int target : searchTargetsPacked) {
                if (nodePos == target) {
                    bestLastNode = node;
                    reached = true;
                    break;
                }
                int distance = WorldPointUtil.distanceBetween(nodePos, target);
                long heuristic = distance + (long) WorldPointUtil.distanceBetween(nodePos, target, 2);
                if (heuristic < bestHeuristic || (heuristic <= bestHeuristic && distance < bestDistance)) {
                    bestLastNode = node;
                    bestDistance = distance;
                    bestHeuristic = heuristic;
                    cutoffTimeMillis = System.currentTimeMillis() + cutoffDurationMillis;
                }
            }
            if (reached) {
                reachedGoal = true;
                break;
            }

            if (System.currentTimeMillis() > cutoffTimeMillis
                    || (sealedTargetMode && stats.getNodesChecked() > SEALED_SUBSTITUTE_NODE_BUDGET)) {
                timedOut = true;
                WebWalkLog.pf("cutoff bestDist={} nodes={} sealedMode={}", bestDistance, stats.getNodesChecked(), sealedTargetMode);
                break;
            }

            addNeighbors(node);
        }

        String uniExit = cancelled ? "cancelled"
                : reachedGoal ? "reached-goal"
                : timedOut ? "time-cutoff"
                : (boundary.isEmpty() && pending.isEmpty()) ? "queues-drained" : "loop-ended";
        pathfinderDiag("uni finished exit=%s cancelled=%s boundaryEmpty=%s pendingEmpty=%s bestLastNode=%s cutoffMs=%d",
                uniExit,
                cancelled,
                boundary.isEmpty(),
                pending.isEmpty(),
                bestLastNode == null ? "null" : WorldPointUtil.toString(bestLastNode.packedPosition),
                config.getCalculationCutoffMillis());

        WebWalkLog.pf("uni_loop_exit cancelled={} bEmpty={} pEmpty={} bestLast={}",
                cancelled, boundary.isEmpty(), pending.isEmpty(),
                bestLastNode == null ? "null" : WorldPointUtil.toString(bestLastNode.packedPosition));

        terminationReason = cancelled ? PathTerminationReason.CANCELLED
                : reachedGoal ? PathTerminationReason.TARGET_REACHED
                : timedOut ? PathTerminationReason.CUTOFF_REACHED
                : PathTerminationReason.SEARCH_EXHAUSTED;
    }

    private void runBidirectional() {
        int goalPacked = searchTargetsPacked[0];
        Map<Integer, Set<Transport>> incoming = new HashMap<>(512);
        buildIncomingByDestination(incoming);

        Set<Integer> puzzleAllow = new HashSet<>(targets.size() + 1);
        for (int t : searchTargetsPacked) {
            puzzleAllow.add(t);
        }
        puzzleAllow.add(start);

        VisitedTiles visitedB = new VisitedTiles(map);
        Map<Integer, Node> forwardAt = new HashMap<>(4096);
        Map<Integer, Node> backwardAt = new HashMap<>(4096);
        long[] bestMeetingCost = new long[]{Long.MAX_VALUE};
        Node[] meetF = new Node[1];
        Node[] meetB = new Node[1];

        Node startNode = new Node(start, null);
        boundary.add(startNode);
        forwardAt.put(start, startNode);

        Node goalNode = new Node(goalPacked, null);
        boundaryBackward.add(goalNode);
        backwardAt.put(goalPacked, goalNode);

        int bestDistance = Integer.MAX_VALUE;
        long bestHeuristic = Integer.MAX_VALUE;
        long cutoffDurationMillis = effectiveCutoffMillis();
        long cutoffTimeMillis = System.currentTimeMillis() + cutoffDurationMillis;
        config.refreshTeleports(start, 31);
        boolean timedOut = false;

        while (!cancelled && (!boundary.isEmpty() || !pending.isEmpty() || !boundaryBackward.isEmpty() || !pendingBackward.isEmpty())) {
            if (!boundary.isEmpty() || !pending.isEmpty()) {
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
                if (nodePos == goalPacked) {
                    joinedPathEdges = PathEdge.fromForwardChain(node);
                    joinedPath = node.getPath();
                    selectedPathCost = node.cost;
                    bestLastNode = null;
                    WebWalkLog.pf("bidir forward_hit_goal");
                    break;
                }

                for (int target : searchTargetsPacked) {
                    int distance = WorldPointUtil.distanceBetween(nodePos, target);
                    long heuristic = distance + (long) WorldPointUtil.distanceBetween(nodePos, target, 2);
                    if (heuristic < bestHeuristic || (heuristic <= bestHeuristic && distance < bestDistance)) {
                        bestLastNode = node;
                        bestDistance = distance;
                        bestHeuristic = heuristic;
                        cutoffTimeMillis = System.currentTimeMillis() + cutoffDurationMillis;
                    }
                }

                addNeighborsForwardWithMeet(node, forwardAt, backwardAt, bestMeetingCost, meetF, meetB);
            }

            if (joinedPath != null) {
                break;
            }

            if (!boundaryBackward.isEmpty() || !pendingBackward.isEmpty()) {
                Node b = boundaryBackward.peek();
                Node p = pendingBackward.peek();
                Node node;
                if (p != null && (b == null || p.cost < b.cost)) {
                    node = pendingBackward.poll();
                } else {
                    node = boundaryBackward.poll();
                }

                if (node.packedPosition == start) {
                    joinedPath = combineBidirectionalPath(forwardAt.get(start), node);
                    selectedPathCost = node.cost;
                    bestLastNode = null;
                    WebWalkLog.pf("bidir backward_hit_start");
                    break;
                }

                addNeighborsBackwardWithMeet(node, visitedB, incoming, puzzleAllow, forwardAt, backwardAt, bestMeetingCost, meetF, meetB);
            }

            if (System.currentTimeMillis() > cutoffTimeMillis
                    || (sealedTargetMode && stats.getNodesChecked() > SEALED_SUBSTITUTE_NODE_BUDGET)) {
                timedOut = true;
                WebWalkLog.pf("bidir_cutoff nodes={} sealedMode={}", stats.getNodesChecked(), sealedTargetMode);
                break;
            }
        }

        if (joinedPath == null && meetF[0] != null && meetB[0] != null && bestMeetingCost[0] < Long.MAX_VALUE) {
            joinedPath = combineBidirectionalPath(meetF[0], meetB[0]);
            selectedPathCost = bestMeetingCost[0];
            bestLastNode = null;
            WebWalkLog.pf("bidir meet_at={} cost={}",
                    WorldPointUtil.toString(meetF[0].packedPosition), bestMeetingCost[0]);
        }

        pathfinderDiag("bidir finished joinedPath=%s meetCost=%s forwardFrontier=%d/%d backwardFrontier=%d/%d cancelled=%s",
                joinedPath == null ? "null" : joinedPath.size(),
                bestMeetingCost[0] == Long.MAX_VALUE ? "n/a" : bestMeetingCost[0],
                boundary.size(),
                pending.size(),
                boundaryBackward.size(),
                pendingBackward.size(),
                cancelled);

        WebWalkLog.pf("bidir_exit joined={} meetCost={}",
                joinedPath == null ? "null" : Integer.toString(joinedPath.size()),
                bestMeetingCost[0] == Long.MAX_VALUE ? "n/a" : Long.toString(bestMeetingCost[0]));

        terminationReason = cancelled ? PathTerminationReason.CANCELLED
                : joinedPath != null ? PathTerminationReason.TARGET_REACHED
                : timedOut ? PathTerminationReason.CUTOFF_REACHED
                : PathTerminationReason.SEARCH_EXHAUSTED;
    }

    @Override
    public void run() {
        WebWalkLog.pf("run_start src={} dst={} cutoffMs={}",
                WorldPointUtil.toString(start), WorldPointUtil.toString(targets), config.getCalculationCutoffMillis());
        path = Collections.emptyList();
        smoothedPath = Collections.emptyList();
        materializedPathLastNode = null;
        smoothed = false;
        joinedPath = null;
        joinedPathEdges = null;
        terminationReason = null;
        selectedPathCost = -1L;
        try {
            // Pathfinder instances are commonly constructed on the client thread and submitted to the
            // shortest-path executor. Resolve both ThreadLocal-backed objects here so the collision map,
            // visited state and pinned live snapshot all belong to the search thread for this run.
            map = config.getMap();
            visited = new VisitedTiles(map);
            // Pin the live-collision snapshot for this whole search so a mid-search swap on the client
            // thread cannot mix two scenes into one path. No-op when live collision is disabled.
            map.beginSearch();
            stats.start();

            searchTargetsPacked = targetsPacked;
            sealedTargetMode = false;
            cutoffOverrideMillis = -1L;
            reachedSealedSubstitutePacked = -1;
            if (targetsPacked.length == 1 && targetsPacked[0] != start) {
                int[] rim = null;
                try {
                    rim = sealedTargetSubstitutes(targetsPacked[0]);
                    if (rim != null && rim.length > 0) {
                        rim = normalizeSealedRim(rim);
                    }
                } catch (RuntimeException probeFailure) {
                    // The probe is an optimisation; any anomaly degrades to the full search, never
                    // to a failed run. (First seen with a mocked CollisionMap whose VisitedTiles had
                    // no region planes.)
                    log.debug("[Pathfinder] sealed-target probe failed, running full search: {}",
                            probeFailure.toString());
                }
                if (rim != null) {
                    sealedTargetMode = true;
                    if (rim.length == 0) {
                        // A sealed component with a void rim (off-map or instance-template garbage):
                        // nothing to walk toward, nothing to search for.
                        WebWalkLog.pf("target_sealed no_walkable_rim dst={}",
                                WorldPointUtil.toString(targetsPacked[0]));
                        terminationReason = PathTerminationReason.SEARCH_EXHAUSTED;
                        return;
                    }
                    // Search for the rim instead: the walk still ends beside the sealed area — the
                    // same best-effort the old full flood produced — at a thousandth of the cost.
                    searchTargetsPacked = rim;
                    cutoffOverrideMillis = SEALED_SUBSTITUTE_CUTOFF_MS;
                }
            }

            int minCheb = minChebyshevStartToAnyTarget();
            boolean useBidir = searchTargetsPacked.length == 1
                    && minCheb >= BIDIRECTIONAL_MIN_CHEBYSHEV;
            pathfinderDiag("run mode decision useBidir=%s minCheb=%d bidirThreshold=%d targetsPacked=%d cutoffMs=%d cancelAlready=%s",
                    useBidir,
                    minCheb,
                    BIDIRECTIONAL_MIN_CHEBYSHEV,
                    targetsPacked.length,
                    config.getCalculationCutoffMillis(),
                    cancelled);
            if (useBidir) {
                WebWalkLog.pf("mode bidir cheb>={}", BIDIRECTIONAL_MIN_CHEBYSHEV);
                runBidirectional();
            } else {
                runUnidirectional();
            }
            // Reaching a rim substitute is not reaching the caller's target, and the substitute pass
            // hitting its short leash (the rim itself can be unreachable — a sealed tile inside a
            // locked interior) changes nothing either: the original destination's unreachability is
            // already PROVEN, and callers keying decisions off the termination must hear exactly that.
            // A genuinely REACHED rim is remembered before the remap, though — it is the walker's
            // signal to retarget the walk to the rim once instead of replaying this search forever.
            if (sealedTargetMode && (terminationReason == PathTerminationReason.TARGET_REACHED
                    || terminationReason == PathTerminationReason.CUTOFF_REACHED)) {
                if (terminationReason == PathTerminationReason.TARGET_REACHED) {
                    if (bestLastNode != null) {
                        reachedSealedSubstitutePacked = bestLastNode.packedPosition;
                    } else if (joinedPath != null && !joinedPath.isEmpty()) {
                        reachedSealedSubstitutePacked = WorldPointUtil.packWorldPoint(
                                joinedPath.get(joinedPath.size() - 1));
                    }
                }
                terminationReason = PathTerminationReason.SEARCH_EXHAUSTED;
            }
        } catch (Exception e) {
            terminationReason = PathTerminationReason.FAILED;
            log.error("[Pathfinder] Exception in run(): ", e);
        } finally {
            if (terminationReason == null) {
                terminationReason = cancelled
                        ? PathTerminationReason.CANCELLED
                        : PathTerminationReason.SEARCH_EXHAUSTED;
            }
            if (selectedPathCost < 0 && bestLastNode != null) {
                selectedPathCost = bestLastNode.cost;
            }
            done = !cancelled;

            boundary.clear();
            pending.clear();
            boundaryBackward.clear();
            pendingBackward.clear();
            if (visited != null) {
                visited.clear();
            }

            stats.end(map == null ? 0L : map.getLiveEdgeQueries());

            WebWalkLog.pf("run_done done={} cancelled={} termination={} stats={}",
                    done, cancelled, terminationReason,
                    getStats() != null ? getStats().toString() : "null");
        }
    }

    public static class PathfinderStats {
        @Getter
        private int nodesChecked = 0, transportsChecked = 0;
		@Getter
		private long liveCollisionEdgesChecked = 0L;
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

		private void complete(
				long elapsedNanos,
				int nodesChecked,
				int transportsChecked,
				long liveCollisionEdgesChecked) {
			this.started = true;
			this.nodesChecked = nodesChecked;
			this.transportsChecked = transportsChecked;
			this.liveCollisionEdgesChecked = liveCollisionEdgesChecked;
			this.startNanos = 0L;
			this.endNanos = elapsedNanos;
			this.ended = true;
		}

		private void end(long liveCollisionEdgesChecked) {
			this.liveCollisionEdgesChecked = liveCollisionEdgesChecked;
            endNanos = System.nanoTime();
            ended = true;
        }

        @Override
        public String toString() {
			return String.format("PathfinderStats(nodes=%d,transports=%d,liveEdges=%d,time=%dms)",
				nodesChecked, transportsChecked, liveCollisionEdgesChecked,
				getElapsedTimeNanos() / 1_000_000);
        }
    }
}
