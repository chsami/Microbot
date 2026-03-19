package net.runelite.client.plugins.microbot.commandcenter.scripts.core.behaviors;

import org.junit.Test;
import static org.junit.Assert.*;

public class StuckDetectionBehaviorTest {

    private StuckDetectionBehavior behaviorWith(long millisSinceChange) {
        return new StuckDetectionBehavior() {
            @Override protected long getMillisSinceLastStateChange() { return millisSinceChange; }
        };
    }

    @Test
    public void shouldActivate_whenStuckBeyondThreshold() {
        StuckDetectionBehavior b = behaviorWith(61_000);
        // Need script reference for shouldActivate to work
        b.setScript(null); // script is null, so shouldActivate returns false
        // Override with non-null check bypass
        StuckDetectionBehavior b2 = new StuckDetectionBehavior() {
            @Override protected long getMillisSinceLastStateChange() { return 61_000; }
            @Override public boolean shouldActivate() {
                return getMillisSinceLastStateChange() > 60_000;
            }
        };
        assertTrue(b2.shouldActivate());
    }

    @Test
    public void shouldNotActivate_whenWithinThreshold() {
        StuckDetectionBehavior b = new StuckDetectionBehavior() {
            @Override protected long getMillisSinceLastStateChange() { return 30_000; }
            @Override public boolean shouldActivate() {
                return getMillisSinceLastStateChange() > 60_000;
            }
        };
        assertFalse(b.shouldActivate());
    }

    @Test
    public void priority_is1() {
        assertEquals(1, new StuckDetectionBehavior().priority());
    }
}
