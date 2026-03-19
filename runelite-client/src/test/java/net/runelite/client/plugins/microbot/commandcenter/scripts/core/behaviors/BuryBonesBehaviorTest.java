package net.runelite.client.plugins.microbot.commandcenter.scripts.core.behaviors;

import org.junit.Test;
import static org.junit.Assert.*;

public class BuryBonesBehaviorTest {

    private BuryBonesBehavior behaviorWith(boolean hasBones, boolean inCombat) {
        return new BuryBonesBehavior() {
            @Override protected boolean hasBones() { return hasBones; }
            @Override protected boolean isInCombat() { return inCombat; }
            @Override public void execute() { /* no-op */ }
        };
    }

    @Test
    public void shouldActivate_whenHasBones_andNotInCombat() {
        assertTrue(behaviorWith(true, false).shouldActivate());
    }

    @Test
    public void shouldNotActivate_whenNoBones() {
        assertFalse(behaviorWith(false, false).shouldActivate());
    }

    @Test
    public void shouldNotActivate_whenInCombat() {
        assertFalse(behaviorWith(true, true).shouldActivate());
    }
}
