package net.runelite.client.plugins.microbot.util.walker.recovery;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.walker.recovery.RouteRecovery.RecoveryClickAction;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * Decision table for {@link RouteRecovery#decideRecoveryClick} — the recovery block's click/wait/replan
 * choice, extracted pure after two live-debugged incidents in the same guard thicket:
 * <ul>
 *   <li>Clock Tower: a stale recovery click (decided at pass start) canceled an in-flight door-open and
 *       dragged the player away — hence action-in-flight preemption, checked FIRST.</li>
 *   <li>Port Sarim: the walled-target guard had its replan cooldown in the ENTRY condition, so while the
 *       cooldown ran the check vanished and recovery clicked through the shop wall — hence the pinned rule
 *       that the cooldown selects REPLAN vs WAIT and can never yield CLICK.</li>
 * </ul>
 * Every row here is a guard interaction that previously had to be discovered with a live walk.
 */
public class RecoveryClickDecisionTest {

    private static final int WALLED_RADIUS = 9;
    private static final long COOLDOWN_MS = 5_000L;
    private static final long NOW = 1_000_000L;

    private static WorldPoint wp(int x, int y) {
        return new WorldPoint(x, y, 0);
    }

    private static final WorldPoint PLAYER = wp(3008, 3208);
    private static final WorldPoint NEAR_WALLED = wp(3010, 3207);   // 2 tiles, through the shop wall
    private static final WorldPoint REACHABLE_TILE = wp(3006, 3208);
    private static final WorldPoint FAR_TARGET = wp(3030, 3208);    // beyond the walled radius

    /** BFS containing the player's side of the wall only. */
    private static Set<WorldPoint> bfs() {
        return new HashSet<>(Arrays.asList(PLAYER, REACHABLE_TILE, wp(3007, 3208), wp(3008, 3207)));
    }

    private static RecoveryClickAction decide(WorldPoint target, boolean settling, boolean moving,
                                              Set<WorldPoint> reachable, long lastReplanAt) {
        return RouteRecovery.decideRecoveryClick(target, PLAYER, settling, moving, reachable,
                WALLED_RADIUS, NOW, lastReplanAt, COOLDOWN_MS);
    }

    // --- action-in-flight preemption (checked first, wins over everything) --------------------------

    @Test
    public void doorSettlingYieldsEvenWithAPerfectTarget() {
        assertEquals(RecoveryClickAction.YIELD_ACTION_IN_FLIGHT,
                decide(REACHABLE_TILE, true, false, bfs(), 0L));
    }

    @Test
    public void movingYieldsEvenWithAPerfectTarget() {
        assertEquals(RecoveryClickAction.YIELD_ACTION_IN_FLIGHT,
                decide(REACHABLE_TILE, false, true, bfs(), 0L));
    }

    @Test
    public void preemptionWinsOverWalledTarget() {
        // Order pinned: while a door-open is in flight we yield — we do NOT replan out from under it.
        assertEquals(RecoveryClickAction.YIELD_ACTION_IN_FLIGHT,
                decide(NEAR_WALLED, true, false, bfs(), 0L));
    }

    // --- walled target: cooldown selects REPLAN vs WAIT, never CLICK --------------------------------

    @Test
    public void walledTargetReplansWhenCooldownElapsed() {
        assertEquals(RecoveryClickAction.REPLAN_WALLED,
                decide(NEAR_WALLED, false, false, bfs(), NOW - COOLDOWN_MS - 1));
    }

    @Test
    public void walledTargetDuringCooldownWaitsAndNeverClicks() {
        // THE Port Sarim regression: guard replanned at T, then at T+5s the cooldown swallowed the whole
        // check and recovery clicked (3010,3207) from (3008,3208) through the west wall. A walled target
        // must never resolve to CLICK, whatever the cooldown state.
        assertEquals(RecoveryClickAction.WAIT_WALLED,
                decide(NEAR_WALLED, false, false, bfs(), NOW - 1_000L));
    }

    // --- click paths --------------------------------------------------------------------------------

    @Test
    public void reachableTargetClicks() {
        assertEquals(RecoveryClickAction.CLICK, decide(REACHABLE_TILE, false, false, bfs(), 0L));
    }

    @Test
    public void unreachableBeyondRadiusStaysTrusted() {
        // The BFS cannot vouch past the walled radius — distant targets keep the old trusting behavior.
        assertEquals(RecoveryClickAction.CLICK, decide(FAR_TARGET, false, false, bfs(), 0L));
    }

    @Test
    public void emptyOrNullBfsDisablesTheWalledCheck() {
        assertEquals(RecoveryClickAction.CLICK,
                decide(NEAR_WALLED, false, false, Collections.emptySet(), 0L));
        assertEquals(RecoveryClickAction.CLICK, decide(NEAR_WALLED, false, false, null, 0L));
    }

    // --- no target ----------------------------------------------------------------------------------

    @Test
    public void nullTargetAndPlayerTileFallThrough() {
        assertEquals(RecoveryClickAction.NO_TARGET, decide(null, false, false, bfs(), 0L));
        assertEquals(RecoveryClickAction.NO_TARGET, decide(PLAYER, false, false, bfs(), 0L));
    }
}
