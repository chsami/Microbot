package net.runelite.client.plugins.microbot.shortestpath.pathfinder.live;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static net.runelite.api.Constants.REGION_SIZE;
import static net.runelite.api.Constants.SCENE_SIZE;

/**
 * Pure helpers that turn a per-scene {@link LiveCollisionSnapshot} into region deltas and merge them into
 * the accumulating region map. Split out so the whole accumulation model is unit-testable without a client.
 */
final class LiveCollisionRegions {

    private LiveCollisionRegions() {
    }

    /**
     * Merges every <em>known</em> edge of {@code scene} into {@code regions}, keyed by region. For each
     * region the scene touches, the merged result prefers the scene's value where the scene knows the edge
     * and keeps the previously stored value otherwise. Touched region ids are added to {@code dirty}.
     *
     * @return {@code true} if any region changed (i.e. the scene contributed at least one known edge).
     */
    static boolean mergeSceneInto(Map<Integer, LiveCollisionRegion> regions, Set<Integer> dirty,
                                  LiveCollisionSnapshot scene) {
        if (scene == null) {
            return false;
        }
        final int baseX = scene.getBaseX();
        final int baseY = scene.getBaseY();
        final int planes = scene.getPlaneCount();

        final Map<Integer, Builder> builders = new HashMap<>();
        for (int z = 0; z < planes; z++) {
            for (int lx = 0; lx < SCENE_SIZE; lx++) {
                for (int ly = 0; ly < SCENE_SIZE; ly++) {
                    final int wx = baseX + lx;
                    final int wy = baseY + ly;
                    final Boolean north = scene.edge(wx, wy, z, LiveCollisionSnapshot.FLAG_NORTH);
                    final Boolean east = scene.edge(wx, wy, z, LiveCollisionSnapshot.FLAG_EAST);
                    if (north == null && east == null) {
                        continue;
                    }
                    final int rid = LiveCollisionView.regionId(wx, wy);
                    final Builder b = builders.computeIfAbsent(rid, k -> new Builder(planes));
                    final int idx = LiveCollisionRegion.index(wx % REGION_SIZE, wy % REGION_SIZE, z);
                    if (north != null) {
                        b.northKnown.set(idx);
                        if (north) {
                            b.northValue.set(idx);
                        }
                    }
                    if (east != null) {
                        b.eastKnown.set(idx);
                        if (east) {
                            b.eastValue.set(idx);
                        }
                    }
                }
            }
        }

        boolean changed = false;
        for (Map.Entry<Integer, Builder> e : builders.entrySet()) {
            final int rid = e.getKey();
            final LiveCollisionRegion delta = e.getValue().build();
            regions.put(rid, merge(regions.get(rid), delta));
            dirty.add(rid);
            changed = true;
        }
        return changed;
    }

    /**
     * Merges {@code delta} onto {@code existing}, preferring {@code delta}'s value where it knows an edge
     * and keeping {@code existing}'s value where only it does. {@code existing} may be {@code null}.
     */
    static LiveCollisionRegion merge(LiveCollisionRegion existing, LiveCollisionRegion delta) {
        if (existing == null) {
            return delta;
        }
        final int planeCount = Math.max(existing.getPlaneCount(), delta.getPlaneCount());
        return new LiveCollisionRegion(planeCount,
                mergeKnown(existing.northKnownWords(), delta.northKnownWords()),
                mergeValue(existing.northKnownWords(), existing.northValueWords(),
                        delta.northKnownWords(), delta.northValueWords()),
                mergeKnown(existing.eastKnownWords(), delta.eastKnownWords()),
                mergeValue(existing.eastKnownWords(), existing.eastValueWords(),
                        delta.eastKnownWords(), delta.eastValueWords()));
    }

    private static BitSet mergeKnown(long[] existingKnown, long[] deltaKnown) {
        final BitSet k = BitSet.valueOf(existingKnown);
        k.or(BitSet.valueOf(deltaKnown));
        return k;
    }

    /** value = deltaVal where deltaKnown, else existingVal where existingKnown, else 0. */
    private static BitSet mergeValue(long[] existingKnown, long[] existingValue,
                                     long[] deltaKnown, long[] deltaValue) {
        final BitSet dKnown = BitSet.valueOf(deltaKnown);
        final BitSet result = BitSet.valueOf(deltaValue);
        result.and(dKnown); // delta contribution, masked to where delta is known

        final BitSet keepExisting = BitSet.valueOf(existingKnown);
        keepExisting.andNot(dKnown); // edges only the existing region knows
        final BitSet ev = BitSet.valueOf(existingValue);
        ev.and(keepExisting);

        result.or(ev);
        return result;
    }

    /** Mutable per-region accumulator used only while splitting a single scene. */
    private static final class Builder {
        private final int planeCount;
        private final BitSet northKnown = new BitSet();
        private final BitSet northValue = new BitSet();
        private final BitSet eastKnown = new BitSet();
        private final BitSet eastValue = new BitSet();

        Builder(int planeCount) {
            this.planeCount = planeCount;
        }

        LiveCollisionRegion build() {
            return new LiveCollisionRegion(planeCount, northKnown, northValue, eastKnown, eastValue);
        }
    }
}
