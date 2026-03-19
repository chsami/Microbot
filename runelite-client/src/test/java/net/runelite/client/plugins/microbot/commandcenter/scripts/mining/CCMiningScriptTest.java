package net.runelite.client.plugins.microbot.commandcenter.scripts.mining;

import org.junit.Test;
import static org.junit.Assert.*;

public class CCMiningScriptTest {

    private CCMiningScript scriptWith(boolean playerBusy, boolean rockFound) {
        return new CCMiningScript() {
            @Override protected boolean isPlayerBusy() { return playerBusy; }
            @Override protected boolean findAndMineRock() { return rockFound; }
            @Override protected void dropAllOre() { }
        };
    }

    @Test
    public void mining_whenAnimating_stays() {
        assertEquals(CCMiningScript.State.MINING,
            scriptWith(true, false).onTick(CCMiningScript.State.MINING));
    }

    @Test
    public void mining_whenRockFound_stays() {
        assertEquals(CCMiningScript.State.MINING,
            scriptWith(false, true).onTick(CCMiningScript.State.MINING));
    }

    @Test
    public void mining_whenNoRock_goesIdle() {
        assertEquals(CCMiningScript.State.IDLE,
            scriptWith(false, false).onTick(CCMiningScript.State.MINING));
    }

    @Test
    public void idle_whenRockFound_mines() {
        assertEquals(CCMiningScript.State.MINING,
            scriptWith(false, true).onTick(CCMiningScript.State.IDLE));
    }

    @Test
    public void idle_whenNoRock_staysIdle() {
        assertEquals(CCMiningScript.State.IDLE,
            scriptWith(false, false).onTick(CCMiningScript.State.IDLE));
    }
}
