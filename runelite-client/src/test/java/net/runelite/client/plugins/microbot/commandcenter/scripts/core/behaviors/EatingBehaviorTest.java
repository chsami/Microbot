package net.runelite.client.plugins.microbot.commandcenter.scripts.core.behaviors;

import org.junit.Test;
import static org.junit.Assert.*;

public class EatingBehaviorTest {

    private EatingBehavior behaviorWith(int threshold, int hpPercent, boolean hasFood) {
        return new EatingBehavior(threshold) {
            @Override protected int getHpPercent() { return hpPercent; }
            @Override protected boolean hasFood() { return hasFood; }
            @Override public void execute() { /* no-op in test */ }
        };
    }

    @Test
    public void shouldActivate_whenHpBelowThreshold_andHasFood() {
        EatingBehavior b = behaviorWith(50, 40, true);
        assertTrue(b.shouldActivate());
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

    @Test
    public void priority_is10() {
        assertEquals(10, behaviorWith(50, 100, true).priority());
    }
}
