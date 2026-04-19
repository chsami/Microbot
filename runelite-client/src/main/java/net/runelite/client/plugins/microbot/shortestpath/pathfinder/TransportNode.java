package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import net.runelite.api.coords.WorldPoint;

public class TransportNode extends Node implements Comparable<TransportNode> {
    public TransportNode(WorldPoint point, Node previous, int travelTime) {
        // Use Node(int, Node, int cost) which assigns cost directly. The WorldPoint
        // Node constructor re-adds previous.cost via its cost(previous, wait) method,
        // which caused (a) double-counting when we passed prev.cost + travelTime as
        // wait and (b) integer overflow for plane-crossing transports with travelTime=0
        // because its distance fallback returns Integer.MAX_VALUE across planes.
        super(net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil.packWorldPoint(point),
                previous,
                (previous != null ? previous.cost : 0) + travelTime);
    }

    @Override
    public int compareTo(TransportNode other) {
        return Integer.compare(cost, other.cost);
    }
}
