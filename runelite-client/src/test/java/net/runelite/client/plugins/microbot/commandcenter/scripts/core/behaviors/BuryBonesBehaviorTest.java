package net.runelite.client.plugins.microbot.commandcenter.scripts.core.behaviors;

import net.runelite.client.plugins.microbot.commandcenter.scripts.core.CCBehaviorTestBase;
import org.junit.Test;
import static org.junit.Assert.*;

public class BuryBonesBehaviorTest extends CCBehaviorTestBase<BuryBonesBehavior> {

    @Override
    protected BuryBonesBehavior createDefaultBehavior() {
        return new BuryBonesBehavior() {
            @Override protected boolean hasBones() { return false; }
            @Override protected boolean isInCombat() { return false; }
            @Override public void execute() {}
        };
    }

    @Override protected int expectedPriority() { return 45; }
    @Override protected String expectedName() { return "BuryBones"; }

    private BuryBonesBehavior behaviorWith(boolean hasBones, boolean inCombat) {
        return new BuryBonesBehavior() {
            @Override protected boolean hasBones() { return hasBones; }
            @Override protected boolean isInCombat() { return inCombat; }
            @Override public void execute() {}
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
