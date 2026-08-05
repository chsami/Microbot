package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.CollisionMap;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.SplitFlagMap;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Pre-flight rejection of destinations that are not walkable terrain.
 *
 * <p>Regression for a walk that spent ~80s and 100+ tiles routing from Draynor to the Dwarven Mine
 * before reporting {@code partial-retries-exhausted}. The goal {@code (3087,9720)} is inside the
 * rock east of the mine: blocked, with zero walkable tiles within 6. The pathfinder was correct —
 * it stopped at {@code (3056,9720)}, the last walkable tile before the rock face — but the failure
 * read as a walker fault rather than a bad coordinate. The intended destination was the Motherlode
 * cave mouth at {@code (3060,9766)}.
 */
public class TargetWalkabilityPreflightTest {

    private static CollisionMap map;

    /** Inside the rock east of the Dwarven Mine — the coordinate that produced the bad walk. */
    private static final WorldPoint BURIED_IN_ROCK = new WorldPoint(3087, 9720, 0);
    /** Last walkable tile the pathfinder could actually reach along that corridor. */
    private static final WorldPoint LAST_WALKABLE = new WorldPoint(3056, 9720, 0);
    /** The real Motherlode Mine entrance ("Enter;Cave;26654" in transports.tsv). */
    private static final WorldPoint MLM_CAVE_MOUTH = new WorldPoint(3060, 9766, 0);

    @BeforeClass
    public static void load() {
        map = new CollisionMap(SplitFlagMap.fromResources());
    }

    @Test
    public void aTargetBuriedInRockIsRejected() {
        assertTrue("precondition: the region must be mapped, or the guard correctly abstains",
                map.hasRegion(BURIED_IN_ROCK.getX(), BURIED_IN_ROCK.getY()));
        assertTrue("precondition: the tile itself is blocked",
                map.isBlocked(BURIED_IN_ROCK.getX(), BURIED_IN_ROCK.getY(), 0));
        assertFalse("a goal with no walkable tile within the arrival distance must be rejected "
                        + "before the walk starts",
                Rs2PathApi.hasWalkableTileWithin(map, BURIED_IN_ROCK, 5));
    }

    @Test
    public void legitimateUndergroundTargetsAreAccepted() {
        assertTrue("the last walkable tile in the corridor must still be accepted",
                Rs2PathApi.hasWalkableTileWithin(map, LAST_WALKABLE, 5));
        assertTrue("the Motherlode cave mouth is a valid destination and must not be rejected",
                Rs2PathApi.hasWalkableTileWithin(map, MLM_CAVE_MOUTH, 5));
    }

    /**
     * The guard must abstain wherever it lacks data. An unmapped region reads as fully blocked, so
     * a naive check would reject every instance and every region absent from the collision map.
     */
    @Test
    public void unmappedRegionsAreNeverRejected() {
        WorldPoint offMap = new WorldPoint(0, 0, 0);
        assertFalse("precondition: this region genuinely has no collision data",
                map.hasRegion(offMap.getX(), offMap.getY()));
        assertTrue("no collision data must mean 'let the pathfinder try', not 'unreachable'",
                Rs2PathApi.hasWalkableTileWithin(map, offMap, 5));
        assertTrue("a null map must never block a walk",
                Rs2PathApi.hasWalkableTileWithin(null, BURIED_IN_ROCK, 5));
    }

    /** A generous arrival distance reaches real ground, so the same goal becomes acceptable. */
    @Test
    public void aLargeArrivalDistanceReachesWalkableGround() {
        assertFalse(Rs2PathApi.hasWalkableTileWithin(map, BURIED_IN_ROCK, 5));
        assertTrue("with a 40 tile tolerance the corridor is inside the search box",
                Rs2PathApi.hasWalkableTileWithin(map, BURIED_IN_ROCK, 40));
    }

    @Test
    public void theRejectionPointsAtTheNearestRealTile() {
        WorldPoint nearest = Rs2PathApi.nearestWalkableTile(map, BURIED_IN_ROCK, 48);
        assertNotNull("the warning must name a concrete tile so the coordinate can be corrected",
                nearest);
        assertFalse("the suggested tile must itself be walkable",
                map.isBlocked(nearest.getX(), nearest.getY(), 0));
    }
}
