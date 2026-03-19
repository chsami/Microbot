package net.runelite.client.plugins.microbot.commandcenter.scripts.fishing;

import org.junit.Test;
import static org.junit.Assert.*;

public class CCFishingScriptTest {

    private CCFishingScript scriptWith(boolean playerBusy, boolean spotFound, boolean hasTool) {
        return new CCFishingScript() {
            @Override protected boolean isPlayerBusy() { return playerBusy; }
            @Override protected boolean findAndFish() { return spotFound; }
            @Override protected boolean hasRequiredTool() { return hasTool; }
            @Override protected void dropAllFish() { }
        };
    }

    @Test
    public void fishing_whenAnimating_stays() {
        assertEquals(CCFishingScript.State.FISHING,
            scriptWith(true, false, true).onTick(CCFishingScript.State.FISHING));
    }

    @Test
    public void fishing_whenSpotFound_stays() {
        assertEquals(CCFishingScript.State.FISHING,
            scriptWith(false, true, true).onTick(CCFishingScript.State.FISHING));
    }

    @Test
    public void fishing_whenNoSpot_goesIdle() {
        assertEquals(CCFishingScript.State.IDLE,
            scriptWith(false, false, true).onTick(CCFishingScript.State.FISHING));
    }

    @Test
    public void idle_whenSpotFound_fishes() {
        assertEquals(CCFishingScript.State.FISHING,
            scriptWith(false, true, true).onTick(CCFishingScript.State.IDLE));
    }

    @Test
    public void fishing_whenMissingTool_goesIdle() {
        assertEquals(CCFishingScript.State.IDLE,
            scriptWith(false, false, false).onTick(CCFishingScript.State.FISHING));
    }

    @Test
    public void idle_whenNoSpot_staysIdle() {
        assertEquals(CCFishingScript.State.IDLE,
            scriptWith(false, false, true).onTick(CCFishingScript.State.IDLE));
    }
}
