package net.runelite.client.plugins.microbot.frosty.threatdetector;

import net.runelite.api.coords.WorldPoint;
import java.util.ArrayList;

public class SafeZoneHelper {
    private static final ArrayList<Edge> FEROX_ENCLAVE = new ArrayList<>();

    private static class Edge {
        public int x1, y1, x2, y2;
        Edge(int x1, int y1, int x2, int y2) {
            this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
        }
    }

    public SafeZoneHelper() {
        FEROX_ENCLAVE.add(new Edge(3125, 3639, 3138, 3639));
        FEROX_ENCLAVE.add(new Edge(3138, 3639, 3138, 3647));
        FEROX_ENCLAVE.add(new Edge(3138, 3647, 3156, 3647));
        FEROX_ENCLAVE.add(new Edge(3156, 3647, 3156, 3636));
        FEROX_ENCLAVE.add(new Edge(3156, 3636, 3154, 3636));
        FEROX_ENCLAVE.add(new Edge(3154, 3636, 3154, 3626));
        FEROX_ENCLAVE.add(new Edge(3154, 3626, 3151, 3622));
        FEROX_ENCLAVE.add(new Edge(3151, 3622, 3144, 3620));
        FEROX_ENCLAVE.add(new Edge(3144, 3620, 3142, 3618));
        FEROX_ENCLAVE.add(new Edge(3142, 3618, 3138, 3618));
        FEROX_ENCLAVE.add(new Edge(3138, 3618, 3138, 3617));
        FEROX_ENCLAVE.add(new Edge(3138, 3617, 3125, 3617));
        FEROX_ENCLAVE.add(new Edge(3125, 3617, 3125, 3627));
        FEROX_ENCLAVE.add(new Edge(3125, 3627, 3123, 3627));
        FEROX_ENCLAVE.add(new Edge(3123, 3627, 3123, 3633));
        FEROX_ENCLAVE.add(new Edge(3123, 3633, 3125, 3633));
        FEROX_ENCLAVE.add(new Edge(3125, 3633, 3125, 3639));
    }

    public boolean isInsideFerox(WorldPoint test) {
        Edge testRay = new Edge(test.getX(), test.getY(), 0, 0);
        int intersections = 0;
        for (Edge i : FEROX_ENCLAVE) {
            if (hasIntersection(testRay, i)) {
                intersections++;
            }
        }
        return intersections % 2 == 1;
    }

    private static boolean hasIntersection(Edge lhs, Edge rhs) {
        return (ccw(lhs.x1, lhs.y1, rhs.x1, rhs.y1, rhs.x2, rhs.y2) != ccw(lhs.x2, lhs.y2, rhs.x1, rhs.y1, rhs.x2, rhs.y2))
                && (ccw(lhs.x1, lhs.y1, lhs.x2, lhs.y2, rhs.x1, rhs.y1) != ccw(lhs.x1, lhs.y1, lhs.x2, lhs.y2, rhs.x2, rhs.y2));
    }

    private static boolean ccw(int x1, int y1, int x2, int y2, int x3, int y3) {
        return (y3 - y1) * (x2 - x1) > (y2 - y1) * (x3 - x1);
    }
}
