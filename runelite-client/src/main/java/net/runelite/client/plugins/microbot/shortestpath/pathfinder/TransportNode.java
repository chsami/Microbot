package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;

public class TransportNode extends Node implements Comparable<TransportNode> {
    public final TransportType transportType;
    public final String displayInfo;

    public TransportNode(WorldPoint position, Node previous, int travelTime, TransportType transportType, String displayInfo) {
        super(position, previous, cost(previous, travelTime));
        this.transportType = transportType;
        this.displayInfo = displayInfo;
    }

    private static int cost(Node previous, int travelTime) {
        return (previous != null ? previous.cost : 0) + travelTime;
    }

    @Override
    public int compareTo(TransportNode other) {
        return Integer.compare(cost, other.cost);
    }
}
