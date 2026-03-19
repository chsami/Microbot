package net.runelite.client.plugins.microbot.commandcenter.scripts.woodcutting;

import net.runelite.client.plugins.microbot.commandcenter.scripts.core.CCScriptTestUtils;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CCWoodcuttingScriptTest {

    private CCWoodcuttingScript scriptWith(boolean playerBusy, boolean treeFound) {
        return new CCWoodcuttingScript() {
            @Override protected boolean isPlayerBusy() { return playerBusy; }
            @Override protected boolean findAndChopTree() { return treeFound; }
            @Override protected void dropAllLogs() { }
        };
    }

    @Test
    public void chopping_whenAnimating_stays() {
        CCWoodcuttingScript s = scriptWith(true, false);
        assertEquals(CCWoodcuttingScript.State.CHOPPING,
            s.onTick(CCWoodcuttingScript.State.CHOPPING));
    }

    @Test
    public void chopping_whenTreeFound_stays() {
        CCWoodcuttingScript s = scriptWith(false, true);
        assertEquals(CCWoodcuttingScript.State.CHOPPING,
            s.onTick(CCWoodcuttingScript.State.CHOPPING));
    }

    @Test
    public void chopping_whenNoTree_goesIdle() {
        CCWoodcuttingScript s = scriptWith(false, false);
        assertEquals(CCWoodcuttingScript.State.IDLE,
            s.onTick(CCWoodcuttingScript.State.CHOPPING));
    }

    @Test
    public void idle_whenTreeFound_chops() {
        CCWoodcuttingScript s = scriptWith(false, true);
        assertEquals(CCWoodcuttingScript.State.CHOPPING,
            s.onTick(CCWoodcuttingScript.State.IDLE));
    }

    @Test
    public void idle_whenNoTree_staysIdle() {
        CCWoodcuttingScript s = scriptWith(false, false);
        assertEquals(CCWoodcuttingScript.State.IDLE,
            s.onTick(CCWoodcuttingScript.State.IDLE));
    }

    @Test
    public void chopping_whenInventoryFull_andConfigDrop_dropsLogs() {
        boolean[] dropCalled = new boolean[1];
        CCWoodcuttingConfig config = mock(CCWoodcuttingConfig.class, CALLS_REAL_METHODS);
        when(config.bankLogs()).thenReturn(false);

        CCWoodcuttingScript s = new CCWoodcuttingScript() {
            @Override protected boolean isPlayerBusy() { return false; }
            @Override protected boolean findAndChopTree() { return false; }
            @Override protected boolean isInventoryFull() { return true; }
            @Override protected void dropAllLogs() { dropCalled[0] = true; }
        };
        CCScriptTestUtils.injectConfig(s, config);

        assertEquals(CCWoodcuttingScript.State.CHOPPING,
            s.onTick(CCWoodcuttingScript.State.CHOPPING));
        assertTrue("dropAllLogs should be called", dropCalled[0]);
    }
}
