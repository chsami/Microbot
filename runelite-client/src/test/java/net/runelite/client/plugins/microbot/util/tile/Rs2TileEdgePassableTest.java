package net.runelite.client.plugins.microbot.util.tile;

import net.runelite.api.CollisionDataFlag;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The collision rule behind "can I walk through that door now".
 * <p>
 * This is the signal the door waits release on, so a wrong answer either strands the walker in front
 * of an open door or sends it on while the door is still shut. The walker previously had no direct
 * reading at all — it inferred an opened door from the player having already moved through it, which
 * is why doors could not be chained.
 */
public class Rs2TileEdgePassableTest {

    private static final int SIZE = 8;
    private static final int FROM_X = 3;
    private static final int FROM_Y = 3;

    private static int[][] openField() {
        return new int[SIZE][SIZE];
    }

    // ---- cardinal steps: the door case ---------------------------------------------------------

    @Test
    public void cardinalStepIsAllowedAcrossAnOpenEdge() {
        int[][] flags = openField();
        assertTrue("north", Rs2Tile.isStepAllowed(flags, FROM_X, FROM_Y, 0, 1));
        assertTrue("south", Rs2Tile.isStepAllowed(flags, FROM_X, FROM_Y, 0, -1));
        assertTrue("east", Rs2Tile.isStepAllowed(flags, FROM_X, FROM_Y, 1, 0));
        assertTrue("west", Rs2Tile.isStepAllowed(flags, FROM_X, FROM_Y, -1, 0));
    }

    /** A shut door sets the blocking flag for its direction on the tile you are stepping OUT of. */
    @Test
    public void cardinalStepIsRefusedWhenThatDirectionIsBlocked() {
        int[][] flags = openField();
        flags[FROM_X][FROM_Y] = CollisionDataFlag.BLOCK_MOVEMENT_NORTH;
        assertFalse("the blocked direction", Rs2Tile.isStepAllowed(flags, FROM_X, FROM_Y, 0, 1));
        assertTrue("every other direction stays open", Rs2Tile.isStepAllowed(flags, FROM_X, FROM_Y, 1, 0));
        assertTrue(Rs2Tile.isStepAllowed(flags, FROM_X, FROM_Y, 0, -1));
    }

    /**
     * Each direction has its own flag, so a door blocking north must not be read as blocking east.
     * Getting this wrong would release the wait on the wrong door edge entirely.
     */
    @Test
    public void eachDirectionReadsItsOwnFlag() {
        int[][] flags = openField();
        flags[FROM_X][FROM_Y] = CollisionDataFlag.BLOCK_MOVEMENT_EAST;
        assertFalse(Rs2Tile.isStepAllowed(flags, FROM_X, FROM_Y, 1, 0));
        assertTrue(Rs2Tile.isStepAllowed(flags, FROM_X, FROM_Y, 0, 1));

        flags[FROM_X][FROM_Y] = CollisionDataFlag.BLOCK_MOVEMENT_WEST;
        assertFalse(Rs2Tile.isStepAllowed(flags, FROM_X, FROM_Y, -1, 0));
        assertTrue(Rs2Tile.isStepAllowed(flags, FROM_X, FROM_Y, 1, 0));

        flags[FROM_X][FROM_Y] = CollisionDataFlag.BLOCK_MOVEMENT_SOUTH;
        assertFalse(Rs2Tile.isStepAllowed(flags, FROM_X, FROM_Y, 0, -1));
        assertTrue(Rs2Tile.isStepAllowed(flags, FROM_X, FROM_Y, 0, 1));
    }

    /** Somewhere you cannot stand is not somewhere you can step, however open the edge is. */
    @Test
    public void stepIntoAFullyBlockedTileIsRefused() {
        int[][] flags = openField();
        flags[FROM_X][FROM_Y + 1] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
        assertFalse(Rs2Tile.isStepAllowed(flags, FROM_X, FROM_Y, 0, 1));
    }

    // ---- diagonals: corners may not be cut ------------------------------------------------------

    @Test
    public void diagonalIsAllowedWhenBothComponentsAreOpen() {
        assertTrue(Rs2Tile.isStepAllowed(openField(), FROM_X, FROM_Y, 1, 1));
    }

    @Test
    public void diagonalIsRefusedWhenEitherComponentIsBlocked() {
        int[][] north = openField();
        north[FROM_X][FROM_Y] = CollisionDataFlag.BLOCK_MOVEMENT_NORTH;
        assertFalse(Rs2Tile.isStepAllowed(north, FROM_X, FROM_Y, 1, 1));

        int[][] east = openField();
        east[FROM_X][FROM_Y] = CollisionDataFlag.BLOCK_MOVEMENT_EAST;
        assertFalse(Rs2Tile.isStepAllowed(east, FROM_X, FROM_Y, 1, 1));
    }

    /** The two tiles the diagonal cuts through must also permit it — no squeezing past a corner. */
    @Test
    public void diagonalIsRefusedWhenTheCutTilesBlockIt() {
        int[][] flags = openField();
        flags[FROM_X + 1][FROM_Y] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
        assertFalse(Rs2Tile.isStepAllowed(flags, FROM_X, FROM_Y, 1, 1));

        int[][] other = openField();
        other[FROM_X][FROM_Y + 1] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
        assertFalse(Rs2Tile.isStepAllowed(other, FROM_X, FROM_Y, 1, 1));
    }

    @Test
    public void steppingNowhereIsAlwaysAllowed() {
        int[][] flags = openField();
        flags[FROM_X][FROM_Y] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
        assertTrue(Rs2Tile.isStepAllowed(flags, FROM_X, FROM_Y, 0, 0));
    }
}
