package net.runelite.client.plugins.microbot.commandcenter.scripts.woodcutting;

import org.junit.Test;
import static org.junit.Assert.*;

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
}
