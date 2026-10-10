package net.runelite.client.plugins.microbot.util.walker.transport;

import net.runelite.api.coords.WorldPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class TransportRefusalLedger {

    public static final int STRIKE_LIMIT = 2;

    private final Map<List<WorldPoint>, Integer> strikes = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<WorldPoint[]> walkScopedBlocks = new ConcurrentLinkedQueue<>();

    public int registerRefusal(WorldPoint origin, WorldPoint destination) {
        if (origin == null || destination == null) {
            return 0;
        }
        return strikes.merge(List.of(origin, destination), 1, Integer::sum);
    }

    public static boolean isStrikeOut(int strikeCount) {
        return strikeCount == STRIKE_LIMIT;
    }

    public int strikes(WorldPoint origin, WorldPoint destination) {
        if (origin == null || destination == null) {
            return 0;
        }
        return strikes.getOrDefault(List.of(origin, destination), 0);
    }

    public void clear(WorldPoint origin, WorldPoint destination) {
        if (origin == null || destination == null) {
            return;
        }
        strikes.remove(List.of(origin, destination));
    }

    public void recordWalkScopedBlock(WorldPoint origin, WorldPoint destination) {
        if (origin == null || destination == null) {
            return;
        }
        walkScopedBlocks.add(new WorldPoint[]{origin, destination});
    }

    public List<WorldPoint[]> drainWalkScopedBlocks() {
        strikes.clear();
        List<WorldPoint[]> drained = new ArrayList<>();
        WorldPoint[] edge;
        while ((edge = walkScopedBlocks.poll()) != null) {
            drained.add(edge);
        }
        return drained;
    }
}
