package net.runelite.client.plugins.microbot.util.walker.transport;

import net.runelite.api.coords.WorldPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class TransportRefusalLedger {

    public static final int STRIKE_LIMIT = 2;
    public static final int FALL_THROUGH_STRIKE_LIMIT = 3;

    private final Map<List<WorldPoint>, Integer> strikes = new ConcurrentHashMap<>();
    private final Map<List<WorldPoint>, Integer> fallThroughs = new ConcurrentHashMap<>();
    private final Set<List<WorldPoint>> talkFirstTried = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<WorldPoint[]> walkScopedBlocks = new ConcurrentLinkedQueue<>();

    public int registerRefusal(WorldPoint origin, WorldPoint destination) {
        if (origin == null || destination == null) {
            return 0;
        }
        return strikes.merge(List.of(origin, destination), 1, Integer::sum);
    }

    public int registerFallThrough(WorldPoint origin, WorldPoint destination) {
        if (origin == null || destination == null) {
            return 0;
        }
        return fallThroughs.merge(List.of(origin, destination), 1, Integer::sum);
    }

    public static boolean isStrikeOut(int strikeCount) {
        return strikeCount == STRIKE_LIMIT;
    }

    /** One refusal is enough when the walk can go round on foot instead. */
    public static boolean isStrikeOut(int strikeCount, boolean onFootRouteExists) {
        return onFootRouteExists ? strikeCount >= 1 : isStrikeOut(strikeCount);
    }

    /**
     * True when the walk target lies on the surface and the transport only takes the player away from it: from the
     * target's own plane underground or to another plane, or from a plane that is not the target's to one even farther
     * from it (an upstairs castle staircase on the way to a ground-level target). Such a transport is a detour, so
     * walking round is possible and one refusal is enough.
     */
    public static boolean onFootRouteLikely(WorldPoint origin, WorldPoint destination, WorldPoint target) {
        if (origin == null || destination == null || target == null) {
            return false;
        }
        final int undergroundY = 6400;
        boolean surface = target.getY() < undergroundY && origin.getY() < undergroundY;
        boolean targetOnOriginLevel = target.getPlane() == origin.getPlane();
        boolean leavesLevel = destination.getPlane() != origin.getPlane() || destination.getY() >= undergroundY;
        boolean movesAwayFromTargetLevel = !targetOnOriginLevel && destination.getY() < undergroundY
                && Math.abs(destination.getPlane() - target.getPlane()) > Math.abs(origin.getPlane() - target.getPlane());
        return surface && ((targetOnOriginLevel && leavesLevel) || movesAwayFromTargetLevel);
    }

    public static boolean isFallThroughStrikeOut(int fallThroughCount) {
        return fallThroughCount == FALL_THROUGH_STRIKE_LIMIT;
    }

    public static boolean isStillAtOrigin(WorldPoint player, WorldPoint origin, WorldPoint destination) {
        if (player == null || origin == null || destination == null || player.getPlane() != origin.getPlane()) {
            return false;
        }
        int toOrigin = player.distanceTo2D(origin);
        if (toOrigin > 1) {
            return false;
        }
        return player.getPlane() != destination.getPlane() || toOrigin < player.distanceTo2D(destination);
    }

    public int strikes(WorldPoint origin, WorldPoint destination) {
        if (origin == null || destination == null) {
            return 0;
        }
        return strikes.getOrDefault(List.of(origin, destination), 0);
    }

    public int fallThroughs(WorldPoint origin, WorldPoint destination) {
        if (origin == null || destination == null) {
            return 0;
        }
        return fallThroughs.getOrDefault(List.of(origin, destination), 0);
    }

    public void clear(WorldPoint origin, WorldPoint destination) {
        if (origin == null || destination == null) {
            return;
        }
        strikes.remove(List.of(origin, destination));
        fallThroughs.remove(List.of(origin, destination));
    }

    public void recordWalkScopedBlock(WorldPoint origin, WorldPoint destination) {
        if (origin == null || destination == null) {
            return;
        }
        walkScopedBlocks.add(new WorldPoint[]{origin, destination});
    }

    /** True the first time per walk for this edge: the talk-first recovery runs once, never in a loop. */
    public boolean claimTalkFirst(WorldPoint origin, WorldPoint destination) {
        return origin != null && destination != null && talkFirstTried.add(List.of(origin, destination));
    }

    public List<WorldPoint[]> drainWalkScopedBlocks() {
        talkFirstTried.clear();
        strikes.clear();
        fallThroughs.clear();
        List<WorldPoint[]> drained = new ArrayList<>();
        WorldPoint[] edge;
        while ((edge = walkScopedBlocks.poll()) != null) {
            drained.add(edge);
        }
        return drained;
    }
}
