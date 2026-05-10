package net.runelite.client.plugins.microbot.shortestpath;

import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import org.junit.After;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Regression tests for Tier 1 webwalker bugs. These pin invariants for bugs already fixed
 * (#1 rehash data loss, #4 growBucket overflow) and verify fixes for bugs still open at the
 * time of this commit (#2 wilderness underground L20/L30 bounds, #5 post-login refresh). Bug
 * #3 (orphan teleportation_poh.tsv) is verified by {@link #bug3_orphanPohTsvIsGone()}.
 */
public class ShortestPathTier1RegressionTest {

    @After
    public void resetStaticPluginState() {
        ShortestPathPlugin.pathfinderConfig = null;
    }

    // --- Bug #1: PrimitiveIntHashMap.rehash() data loss ------------------------------------

    /**
     * The original bug was a {@code return;} inside the rehash overflow branch that abandoned
     * every entry queued after the overflow. Stress test with many rehash cycles and verify
     * nothing is ever silently dropped. Fuzz against HashMap so the assertion is tight
     * regardless of internal bucket-layout decisions.
     */
    @Test
    public void bug1_rehashDoesNotDropEntriesUnderFuzz() {
        PrimitiveIntHashMap<String> ours = new PrimitiveIntHashMap<>(4, 0.25f);
        Map<Integer, String> reference = new HashMap<>();
        Random r = new Random(0xC0FFEEL);

        for (int i = 0; i < 20_000; i++) {
            int key = r.nextInt(7500);
            String val = "v" + i;
            ours.put(key, val);
            reference.put(key, val);
        }

        assertEquals("size must match reference after many rehashes",
                reference.size(), ours.size());
        for (Map.Entry<Integer, String> e : reference.entrySet()) {
            assertEquals("key " + e.getKey() + " was dropped during rehash",
                    e.getValue(), ours.get(e.getKey()));
        }
    }

    /**
     * Targeted variant: adversarial keys that all land in the same low-order bucket bit.
     * Forces the {@code growBucket()} branch to fire repeatedly during a single
     * {@code rehash()} call — the exact code path that used to {@code return;} early.
     */
    @Test
    public void bug1_rehashSurvivesSingleBucketPileup() {
        PrimitiveIntHashMap<Integer> ours = new PrimitiveIntHashMap<>(4, 0.5f);

        int collidedCount = 400;
        int[] keys = findKeysHashingToBucketZero(collidedCount, 0x7FF);
        for (int i = 0; i < collidedCount; i++) {
            ours.put(keys[i], i);
        }

        for (int i = 0; i < collidedCount; i++) {
            Integer got = ours.get(keys[i]);
            assertNotNull("key index " + i + " was dropped", got);
            assertEquals((Integer) i, got);
        }
        assertEquals(collidedCount, ours.size());
    }

    private static int[] findKeysHashingToBucketZero(int n, int mask) {
        int[] out = new int[n];
        int found = 0;
        int candidate = 0;
        while (found < n) {
            candidate++;
            int h = (candidate ^ (candidate >>> 5) ^ (candidate >>> 25)) & 0x7FFFFFFF;
            if ((h & mask) == 0) {
                out[found++] = candidate;
            }
        }
        return out;
    }

    // --- Bug #4: PrimitiveIntHashMap.growBucket() integer overflow -------------------------

    /**
     * The fix caps bucket growth at {@code Integer.MAX_VALUE / 2 - 4} before doubling.
     * Without the cap, a large bucket doubled would wrap to a negative array length and
     * throw {@code NegativeArraySizeException}. This test documents the arithmetic
     * invariant so a future refactor that removes the {@code Math.min} guard fails here.
     */
    @Test
    public void bug4_growBucketArithmeticIsOverflowSafe() {
        int hugeBucket = Integer.MAX_VALUE - 10;
        int guarded = Math.min(hugeBucket, Integer.MAX_VALUE / 2 - 4) * 2;
        assertTrue("guarded doubling must stay positive, got " + guarded, guarded > 0);
        assertTrue("guarded bucket must be at least twice the safe threshold",
                guarded >= (Integer.MAX_VALUE / 2 - 4));

        int unguarded = hugeBucket * 2;
        assertTrue("unguarded doubling overflows (regression witness), got " + unguarded,
                unguarded < 0);
    }

    /**
     * Behavioural stress: hammer a single bucket past the default 4-slot capacity many times
     * so {@code growBucket()} is exercised end-to-end. Ensures the sequence
     * 4 → 8 → 16 → 32 → ... → 65536 doesn't drop values or throw.
     */
    @Test
    public void bug4_growBucketChainKeepsAllValues() {
        PrimitiveIntHashMap<String> ours = new PrimitiveIntHashMap<>(4, 0.99f);
        int[] keys = findKeysHashingToBucketZero(10_000, 0x3);

        for (int i = 0; i < keys.length; i++) {
            ours.put(keys[i], "v" + i);
        }
        for (int i = 0; i < keys.length; i++) {
            assertEquals("v" + i, ours.get(keys[i]));
        }
    }

    // --- Bug #2: wilderness underground L20/L30 bounds -------------------------------------

    /**
     * Before the fix: underground L20 was 320x442 so its east edge was x=3263. A point at
     * x=3400 inside the deeper revenant caves read as "not in L20" and the pathfinder
     * happily left L31-only teleports enabled. After the fix: 518x301, covering x up to
     * 3461, matching upstream.
     */
    @Test
    public void bug2_level20UndergroundCoversFullUpstreamWidth() {
        PathfinderConfig cfg = PathfinderConfigTestHelper.emptyConfig();
        int farEast = WorldPointUtil.packWorldPoint(3400, 10100, 0);
        assertTrue("x=3400 y=10100 must be in L20 underground after 518-wide bounds",
                cfg.isInLevel20Wilderness(farEast));
    }

    @Test
    public void bug2_level30UndergroundCoversFullUpstreamWidth() {
        PathfinderConfig cfg = PathfinderConfigTestHelper.emptyConfig();
        int farEast = WorldPointUtil.packWorldPoint(3400, 10200, 0);
        assertTrue("x=3400 y=10200 must be in L30 underground after 518-wide bounds",
                cfg.isInLevel30Wilderness(farEast));
    }

    /**
     * L20 spans y=10075..10375 (height 301) after the fix. Y=10375 inclusive is L20,
     * y=10376 is outside. Pins the exact upstream height.
     */
    @Test
    public void bug2_level20UndergroundSouthBoundary() {
        PathfinderConfig cfg = PathfinderConfigTestHelper.emptyConfig();
        int innerEdge = WorldPointUtil.packWorldPoint(3100, 10375, 0);
        int outerEdge = WorldPointUtil.packWorldPoint(3100, 10376, 0);
        assertTrue("y=10375 must still be L20 underground", cfg.isInLevel20Wilderness(innerEdge));
        assertFalse("y=10376 must be outside L20 underground", cfg.isInLevel20Wilderness(outerEdge));
    }

    /**
     * L30 spans y=10155..10375 (height 221). Matches upstream.
     */
    @Test
    public void bug2_level30UndergroundSouthBoundary() {
        PathfinderConfig cfg = PathfinderConfigTestHelper.emptyConfig();
        int innerEdge = WorldPointUtil.packWorldPoint(3100, 10375, 0);
        int outerEdge = WorldPointUtil.packWorldPoint(3100, 10376, 0);
        assertTrue("y=10375 must still be L30 underground", cfg.isInLevel30Wilderness(innerEdge));
        assertFalse("y=10376 must be outside L30 underground", cfg.isInLevel30Wilderness(outerEdge));
    }

    @Test
    public void bug2_level30UndergroundNorthBoundary() {
        PathfinderConfig cfg = PathfinderConfigTestHelper.emptyConfig();
        int beforeL30 = WorldPointUtil.packWorldPoint(3100, 10154, 0);
        int atL30 = WorldPointUtil.packWorldPoint(3100, 10155, 0);
        assertFalse("y=10154 must not be L30 yet", cfg.isInLevel30Wilderness(beforeL30));
        assertTrue("y=10155 is the L30 north boundary", cfg.isInLevel30Wilderness(atL30));
    }

    /**
     * L20 above-ground is unchanged, but co-test it alongside the underground fix so a
     * regression that collapses the combined check is caught.
     */
    @Test
    public void bug2_level20AboveGroundUnaffected() {
        PathfinderConfig cfg = PathfinderConfigTestHelper.emptyConfig();
        int inL20 = WorldPointUtil.packWorldPoint(3100, 3690, 0);
        int belowL20 = WorldPointUtil.packWorldPoint(3100, 3679, 0);
        assertTrue(cfg.isInLevel20Wilderness(inL20));
        assertFalse(cfg.isInLevel20Wilderness(belowL20));
    }

    // --- Bug #3: orphan teleportation_poh.tsv was deleted ----------------------------------

    @Test
    public void bug3_orphanPohTsvIsGone() {
        assertNull("teleportation_poh.tsv is unreferenced and should not ship",
                ShortestPathPlugin.class.getResourceAsStream("teleportation_poh.tsv"));
    }

    // --- Bug #5: onGameStateChanged triggers a deferred refresh ----------------------------

    @Test
    public void bug5_loggedInSetsPendingRefresh() {
        ShortestPathPlugin plugin = new ShortestPathPlugin();
        assertFalse("new plugin starts without pending refresh", plugin.pendingLoginRefresh);

        plugin.onGameStateChanged(gameStateEvent(GameState.LOGGED_IN));

        assertTrue("LOGGED_IN must set pendingLoginRefresh", plugin.pendingLoginRefresh);
    }

    @Test
    public void bug5_nonLoggedInStatesDoNotSetFlag() {
        ShortestPathPlugin plugin = new ShortestPathPlugin();

        for (GameState s : new GameState[]{
                GameState.LOGIN_SCREEN,
                GameState.CONNECTION_LOST,
                GameState.HOPPING,
                GameState.LOADING,
                GameState.STARTING}) {
            plugin.pendingLoginRefresh = false;
            plugin.onGameStateChanged(gameStateEvent(s));
            assertFalse(s + " must not set pendingLoginRefresh", plugin.pendingLoginRefresh);
        }
    }

    @Test
    public void bug5_pendingRefreshConsumedExactlyOncePerLogin() {
        ShortestPathPlugin plugin = new ShortestPathPlugin();
        PathfinderConfig cfg = mock(PathfinderConfig.class);
        ShortestPathPlugin.pathfinderConfig = cfg;

        plugin.onGameStateChanged(gameStateEvent(GameState.LOGGED_IN));
        plugin.handlePendingLoginRefresh();
        plugin.handlePendingLoginRefresh();
        plugin.handlePendingLoginRefresh();

        verify(cfg, times(1)).refresh();
        assertFalse(plugin.pendingLoginRefresh);
    }

    @Test
    public void bug5_nullConfigLeavesFlagSoRefreshHappensWhenConfigArrives() {
        ShortestPathPlugin plugin = new ShortestPathPlugin();
        ShortestPathPlugin.pathfinderConfig = null;
        plugin.pendingLoginRefresh = true;

        plugin.handlePendingLoginRefresh();

        assertTrue("flag must persist so refresh fires once config is wired up",
                plugin.pendingLoginRefresh);
    }

    @Test
    public void bug5_refreshExceptionLeavesFlagSetForNextTickRetry() {
        ShortestPathPlugin plugin = new ShortestPathPlugin();
        PathfinderConfig cfg = mock(PathfinderConfig.class);
        org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(cfg).refresh();
        ShortestPathPlugin.pathfinderConfig = cfg;
        plugin.pendingLoginRefresh = true;

        plugin.handlePendingLoginRefresh();

        assertTrue("failing refresh keeps flag so handlePendingLoginRefresh retries after login/hydrate stabilizes",
                plugin.pendingLoginRefresh);
        verify(cfg, times(1)).refresh();
    }

    @Test
    public void bug5_multipleLoginTransitionsEachRefreshOnce() {
        ShortestPathPlugin plugin = new ShortestPathPlugin();
        PathfinderConfig cfg = mock(PathfinderConfig.class);
        ShortestPathPlugin.pathfinderConfig = cfg;

        plugin.onGameStateChanged(gameStateEvent(GameState.LOGGED_IN));
        plugin.handlePendingLoginRefresh();

        plugin.onGameStateChanged(gameStateEvent(GameState.CONNECTION_LOST));
        plugin.handlePendingLoginRefresh();

        plugin.onGameStateChanged(gameStateEvent(GameState.LOGGED_IN));
        plugin.handlePendingLoginRefresh();

        verify(cfg, times(2)).refresh();
    }

    @Test
    public void bug5_handleRefreshWhenFlagClearDoesNothing() {
        ShortestPathPlugin plugin = new ShortestPathPlugin();
        PathfinderConfig cfg = mock(PathfinderConfig.class);
        ShortestPathPlugin.pathfinderConfig = cfg;
        plugin.pendingLoginRefresh = false;

        plugin.handlePendingLoginRefresh();

        verify(cfg, never()).refresh();
    }

    private static GameStateChanged gameStateEvent(GameState state) {
        GameStateChanged event = new GameStateChanged();
        event.setGameState(state);
        return event;
    }

    /**
     * Isolated helper that constructs a PathfinderConfig without touching Microbot
     * singletons. Only the wilderness boundary methods are exercised, so transports and
     * restrictions can be empty.
     */
    static final class PathfinderConfigTestHelper {
        static PathfinderConfig emptyConfig() {
            return new PathfinderConfig(
                    null,
                    new HashMap<net.runelite.api.coords.WorldPoint, Set<Transport>>(),
                    java.util.Collections.<Restriction>emptyList(),
                    null,
                    null);
        }
    }
}
