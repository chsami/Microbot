package net.runelite.client.plugins.microbot.breakhandler.breakhandlerv2;

import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import org.junit.After;
import org.junit.Test;

import java.time.Instant;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BreakHandlerV2DeferralTest {
    @After
    public void clearPluginLock() {
        BreakHandlerScript.setLockState(false);
    }

    @Test
    public void defersRequestedBreakWhilePluginLockIsHeld() {
        BreakHandlerScript.setLockState(true);

        assertTrue(BreakHandlerV2Script.shouldDeferRequestedBreak(null));
    }

    @Test
    public void doesNotDeferNewBreakWhenPluginLockIsReleased() {
        BreakHandlerScript.setLockState(false);

        assertFalse(BreakHandlerV2Script.shouldDeferRequestedBreak(null));
    }

    @Test
    public void doesNotDeferActiveNoLogoutBreakWhenPluginLockIsHeld() {
        BreakHandlerScript.setLockState(true);

        assertFalse(BreakHandlerV2Script.shouldDeferRequestedBreak(Instant.now()));
    }

    @Test
    public void continuesActiveBreakWhenGlobalPauseBlocksScripts() {
        assertTrue(BreakHandlerV2Script.shouldContinueWhenScriptGuardBlocks(
                true,
                BreakHandlerV2State.BREAK_REQUESTED,
                false));
    }

    @Test
    public void doesNotContinueNormalWaitingStateWhenGlobalPauseBlocksScripts() {
        assertFalse(BreakHandlerV2Script.shouldContinueWhenScriptGuardBlocks(
                true,
                BreakHandlerV2State.WAITING_FOR_BREAK,
                false));
    }

    @Test
    public void continuesWaitingStateAfterPreBreakPluginStopPausedScripts() {
        assertTrue(BreakHandlerV2Script.shouldContinueWhenScriptGuardBlocks(
                true,
                BreakHandlerV2State.WAITING_FOR_BREAK,
                true));
    }

    @Test
    public void doesNotBypassScriptGuardWhenScriptsAreNotPaused() {
        assertFalse(BreakHandlerV2Script.shouldContinueWhenScriptGuardBlocks(
                false,
                BreakHandlerV2State.BREAK_REQUESTED,
                false));
    }
}
