package net.runelite.client.plugins.microbot.commandcenter.scripts.cooking;

import org.junit.Test;
import static org.junit.Assert.*;

public class CCCookingScriptTest {

    private CCCookingScript scriptWith(boolean playerBusy, boolean hasRawFood, boolean rangeFound) {
        return new CCCookingScript() {
            @Override protected boolean isPlayerBusy() { return playerBusy; }
            @Override protected boolean hasRawFood() { return hasRawFood; }
            @Override protected boolean findAndCook() { return rangeFound; }
        };
    }

    @Test
    public void cooking_whenAnimating_stays() {
        assertEquals(CCCookingScript.State.COOKING,
            scriptWith(true, true, false).onTick(CCCookingScript.State.COOKING));
    }

    @Test
    public void cooking_whenNoRawFood_goesIdle() {
        assertEquals(CCCookingScript.State.IDLE,
            scriptWith(false, false, true).onTick(CCCookingScript.State.COOKING));
    }

    @Test
    public void cooking_whenRangeFound_stays() {
        assertEquals(CCCookingScript.State.COOKING,
            scriptWith(false, true, true).onTick(CCCookingScript.State.COOKING));
    }

    @Test
    public void idle_whenHasRawFood_cooks() {
        assertEquals(CCCookingScript.State.COOKING,
            scriptWith(false, true, false).onTick(CCCookingScript.State.IDLE));
    }

    @Test
    public void idle_whenNoRawFood_staysIdle() {
        assertEquals(CCCookingScript.State.IDLE,
            scriptWith(false, false, false).onTick(CCCookingScript.State.IDLE));
    }
}
