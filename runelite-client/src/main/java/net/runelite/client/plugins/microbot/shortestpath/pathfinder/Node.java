package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class Node {
    public final int packedPosition;
    public final Node previous;
    public final int cost;
    // Per-node random value used as a secondary priority-queue comparator. Breaks ties
    // between equal-cost nodes in random order so the pathfinder explores equivalent
    // routes in a different sequence each run, producing distinct (but still optimal)
    // tile sequences between the same start/target pair.
    public final int tiebreaker;

    public Node(WorldPoint position, Node previous, int wait) {
        this.packedPosition = WorldPointUtil.packWorldPoint(position);
        this.previous = previous;
        this.cost = cost(previous, wait);
        this.tiebreaker = ThreadLocalRandom.current().nextInt();
    }

    public Node(WorldPoint position, Node previous) {
        this(position, previous, cost(position, previous));
    }

    public Node(int packedPosition, Node previous, int cost) {
        this.packedPosition = packedPosition;
        this.previous = previous;
        this.cost = cost;
        this.tiebreaker = ThreadLocalRandom.current().nextInt();
    }

    public Node(int packedPosition, Node previous) {
        this(packedPosition, previous, cost(packedPosition, previous));
    }

    public List<WorldPoint> getPath() {
        List<Node> nodes = getNodePath();
        List<WorldPoint> path = new ArrayList<>(nodes.size());
        for (Node n : nodes) {
            path.add(WorldPointUtil.unpackWorldPoint(n.packedPosition));
        }
        return path;
    }

    List<Node> getNodePath() {
        List<Node> path = new ArrayList<>();
        for (Node n = this; n != null; n = n.previous) {
            path.add(n);
        }
        Collections.reverse(path);
        return path;
    }

    public List<Integer> getPathPacked() {
        List<Integer> path = new ArrayList<>();
        for (Node n = this; n != null; n = n.previous) {
            path.add(n.packedPosition);
        }
        Collections.reverse(path);
        return path;
    }

    private int cost(Node previous, int wait) {
        int previousCost = 0;
        int distance = 0;

        if (previous != null) {
            previousCost = previous.cost;
            // Travel wait time is converted to distance as if the player is walking 1 tile/tick.
            // TODO: reduce the distance if the player is currently running and has enough run energy for the distance?
            distance = wait > 0 ? wait : WorldPointUtil.distanceBetween(previous.packedPosition, packedPosition);
        }

        return previousCost + distance;
    }

    private static int cost(int packedPosition, Node previous) {
        int previousCost = 0;
        int travelTime = 0;

        if (previous != null) {
            previousCost = previous.cost;
            // Travel wait time in TransportNode and distance is compared as if the player is walking 1 tile/tick.
            // TODO: reduce the distance if the player is currently running and has enough run energy for the distance?
            travelTime = WorldPointUtil.distanceBetween(previous.packedPosition, packedPosition);
        }

        return previousCost + travelTime;
    }

    private static int cost(WorldPoint worldPoint, Node previous) {
        int previousCost = 0;
        int travelTime = 0;

        if (previous != null) {
            previousCost = previous.cost;
            // Travel wait time in TransportNode and distance is compared as if the player is walking 1 tile/tick.
            // TODO: reduce the distance if the player is currently running and has enough run energy for the distance?
            travelTime = WorldPointUtil.distanceBetween(previous.packedPosition, WorldPointUtil.packWorldPoint(worldPoint));
        }

        return previousCost + travelTime;
    }
}
