package net.runelite.client.plugins.microbot.shortestpath.pathfinder.live;

import net.runelite.client.plugins.microbot.shortestpath.pathfinder.SplitFlagMap;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.BitSet;

import static net.runelite.api.Constants.SCENE_SIZE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The conflict tally is log-only telemetry, but its two buckets must be RIGHT or the numbers will
 * misdirect the persistent-live-store decision. Uses the real shipped map: whatever the static value
 * of a probe edge is, a live snapshot that agrees counts nothing, and a snapshot that disagrees
 * lands in exactly one bucket. Unknown edges never count.
 */
public class LiveCollisionConflictsTest {

    private static SplitFlagMap staticMap;

    // Lumbridge courtyard: mapped, ordinary ground.
    private static final int PROBE_X = 3222;
    private static final int PROBE_Y = 3218;
    private static final int BASE_X = PROBE_X - 10;
    private static final int BASE_Y = PROBE_Y - 10;

    @BeforeClass
    public static void load() {
        staticMap = SplitFlagMap.fromResources();
    }

    private static LiveCollisionSnapshot snapshotWithNorthEdge(boolean value) {
        BitSet northKnown = new BitSet();
        BitSet northValue = new BitSet();
        int index = (PROBE_Y - BASE_Y) * SCENE_SIZE + (PROBE_X - BASE_X);
        northKnown.set(index);
        if (value) {
            northValue.set(index);
        }
        return new LiveCollisionSnapshot(BASE_X, BASE_Y, 1,
                northKnown, northValue, new BitSet(), new BitSet());
    }

    @Test
    public void agreementCountsNothing() {
        boolean statik = staticMap.get(PROBE_X, PROBE_Y, 0, LiveCollisionSnapshot.FLAG_NORTH);
        LiveCollisionConflicts.Tally tally =
                LiveCollisionConflicts.tally(snapshotWithNorthEdge(statik), staticMap);
        assertTrue("a live edge equal to static must not count as a conflict", tally.isEmpty());
    }

    @Test
    public void disagreementLandsInExactlyOneBucket() {
        boolean statik = staticMap.get(PROBE_X, PROBE_Y, 0, LiveCollisionSnapshot.FLAG_NORTH);
        LiveCollisionConflicts.Tally tally =
                LiveCollisionConflicts.tally(snapshotWithNorthEdge(!statik), staticMap);
        assertEquals(1, tally.liveOpensStatic + tally.liveBlocksStatic + tally.liveOpensSealed);
        if (statik) {
            assertEquals("live=blocked where static=open is liveBlocksStatic", 1, tally.liveBlocksStatic);
        } else if (probeTileSealedInStatic()) {
            // static=blocked on EVERY edge is the floorless/unmapped case, which is deliberately
            // bucketed apart so it cannot drown the interpretable counts.
            assertEquals("live=open on a sealed static tile is liveOpensSealed",
                    1, tally.liveOpensSealed);
        } else {
            assertEquals("live=open on a partially-open static tile is liveOpensStatic",
                    1, tally.liveOpensStatic);
        }
    }

    /**
     * Mirrors {@code LiveCollisionConflicts.staticTileSealed}. A blocked north edge alone does NOT
     * imply the tile is merely partially open — if all four edges are blocked the disagreement is
     * bucketed as sealed, so asserting liveOpensStatic off {@code statik == false} could fail on a
     * perfectly correct result.
     */
    private static boolean probeTileSealedInStatic() {
        return !staticMap.get(PROBE_X, PROBE_Y, 0, LiveCollisionSnapshot.FLAG_NORTH)
                && !staticMap.get(PROBE_X, PROBE_Y - 1, 0, LiveCollisionSnapshot.FLAG_NORTH)
                && !staticMap.get(PROBE_X, PROBE_Y, 0, LiveCollisionSnapshot.FLAG_EAST)
                && !staticMap.get(PROBE_X - 1, PROBE_Y, 0, LiveCollisionSnapshot.FLAG_EAST);
    }

    /**
     * The first live data set was ~44k "conflicts" in EVERY scene — about two planes' worth — because a
     * floorless upper plane has no static data, SplitFlagMap.get returns false for it (reads as solid
     * wall), and the live scene's empty collision flags read as open. That noise must land in the sealed
     * bucket, leaving the regular interpretable counts at zero while preserving the sealed count for
     * its dedicated telemetry field.
     */
    @Test
    public void floorlessUpperPlaneNoiseIsBucketedSeparately() {
        int emptyPlane = 3;
        assertTrue("precondition: the shipped map has no data for an empty upper plane here",
                !staticMap.get(PROBE_X, PROBE_Y, emptyPlane, LiveCollisionSnapshot.FLAG_NORTH));

        BitSet northKnown = new BitSet();
        BitSet northValue = new BitSet();
        int index = emptyPlane * SCENE_SIZE * SCENE_SIZE
                + (PROBE_Y - BASE_Y) * SCENE_SIZE + (PROBE_X - BASE_X);
        northKnown.set(index);
        northValue.set(index); // live: walkable, as empty collision flags always are
        LiveCollisionSnapshot snapshot = new LiveCollisionSnapshot(BASE_X, BASE_Y, 4,
                northKnown, northValue, new BitSet(), new BitSet());

        LiveCollisionConflicts.Tally tally = LiveCollisionConflicts.tally(snapshot, staticMap);
        assertEquals("no-data tiles must not inflate the real conflict count", 0, tally.liveOpensStatic);
        assertEquals(1, tally.liveOpensSealed);
        assertTrue("sealed-only openings stay out of the regular conflict buckets", tally.isEmpty());
    }

    @Test
    public void unknownEdgesNeverCount() {
        LiveCollisionSnapshot allUnknown = new LiveCollisionSnapshot(BASE_X, BASE_Y, 1,
                new BitSet(), new BitSet(), new BitSet(), new BitSet());
        assertTrue(LiveCollisionConflicts.tally(allUnknown, staticMap).isEmpty());
        assertTrue(LiveCollisionConflicts.tally(null, staticMap).isEmpty());
        assertTrue(LiveCollisionConflicts.tally(allUnknown, null).isEmpty());
    }
}
