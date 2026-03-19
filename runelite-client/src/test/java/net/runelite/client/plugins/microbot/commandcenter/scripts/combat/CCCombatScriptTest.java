package net.runelite.client.plugins.microbot.commandcenter.scripts.combat;

import org.junit.Test;
import static org.junit.Assert.*;

public class CCCombatScriptTest {

    private CCCombatScript scriptWith(boolean inCombat, boolean monsterFound) {
        return new CCCombatScript() {
            @Override protected boolean isInCombat() { return inCombat; }
            @Override protected boolean findAndAttackMonster() { return monsterFound; }
            @Override protected void checkProgression() { }
            @Override protected boolean isProgressionEnabled() { return false; }
        };
    }

    private CCCombatScript scriptWithProgression(boolean inCombat, boolean monsterFound, boolean progression) {
        return new CCCombatScript() {
            @Override protected boolean isInCombat() { return inCombat; }
            @Override protected boolean findAndAttackMonster() { return monsterFound; }
            @Override protected void checkProgression() {
                if (progression) { /* would log suggestion */ }
            }
            @Override protected boolean isProgressionEnabled() { return progression; }
        };
    }

    @Test
    public void fighting_whenInCombat_stays() {
        assertEquals(CCCombatScript.State.FIGHTING,
            scriptWith(true, false).onTick(CCCombatScript.State.FIGHTING));
    }

    @Test
    public void fighting_whenTargetDied_goesIdle() {
        assertEquals(CCCombatScript.State.IDLE,
            scriptWith(false, false).onTick(CCCombatScript.State.FIGHTING));
    }

    @Test
    public void idle_whenMonsterFound_attacks() {
        assertEquals(CCCombatScript.State.FIGHTING,
            scriptWith(false, true).onTick(CCCombatScript.State.IDLE));
    }

    @Test
    public void idle_whenNoMonster_stays() {
        assertEquals(CCCombatScript.State.IDLE,
            scriptWith(false, false).onTick(CCCombatScript.State.IDLE));
    }

    @Test
    public void idle_progressionEnabled_completesWithoutError() {
        // Verifies that progression check runs without exception
        CCCombatScript s = scriptWithProgression(false, true, true);
        assertEquals(CCCombatScript.State.FIGHTING,
            s.onTick(CCCombatScript.State.IDLE));
    }

    @Test
    public void idle_progressionDisabled_completesWithoutError() {
        CCCombatScript s = scriptWithProgression(false, true, false);
        assertEquals(CCCombatScript.State.FIGHTING,
            s.onTick(CCCombatScript.State.IDLE));
    }
}
