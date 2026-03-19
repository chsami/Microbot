package net.runelite.client.plugins.microbot.commandcenter.scripts.core.behaviors;

import net.runelite.client.plugins.microbot.commandcenter.scripts.core.CCBehaviorTestBase;
import org.junit.Test;
import static org.junit.Assert.*;

public class EatingBehaviorTest extends CCBehaviorTestBase<EatingBehavior> {

    @Override
    protected EatingBehavior createDefaultBehavior() {
        return new EatingBehavior(50) {
            @Override protected int getHpPercent() { return 100; }
            @Override protected boolean hasFood() { return false; }
            @Override public void execute() {}
        };
    }

    @Override protected int expectedPriority() { return 10; }
    @Override protected String expectedName() { return "Eating"; }

    private EatingBehavior behaviorWith(int threshold, int hpPercent, boolean hasFood) {
        return new EatingBehavior(threshold) {
            @Override protected int getHpPercent() { return hpPercent; }
            @Override protected boolean hasFood() { return hasFood; }
            @Override public void execute() {}
        };
    }

    @Test
    public void shouldActivate_whenHpBelowThreshold_andHasFood() {
        assertTrue(behaviorWith(50, 40, true).shouldActivate());
    }

    @Test
    public void shouldNotActivate_whenHpAboveThreshold() {
        assertFalse(behaviorWith(50, 60, true).shouldActivate());
    }

    @Test
    public void shouldNotActivate_whenNoFood() {
        assertFalse(behaviorWith(50, 30, false).shouldActivate());
    }

    @Test
    public void shouldNotActivate_whenHpExactlyAtThreshold() {
        assertFalse(behaviorWith(50, 50, true).shouldActivate());
    }
}
