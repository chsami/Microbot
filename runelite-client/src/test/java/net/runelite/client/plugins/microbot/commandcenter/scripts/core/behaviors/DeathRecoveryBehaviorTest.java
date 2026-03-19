package net.runelite.client.plugins.microbot.commandcenter.scripts.core.behaviors;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class DeathRecoveryBehaviorTest {

    private DeathRecoveryBehavior behaviorWith(boolean isDead, WorldPoint activityLocation) {
        return new DeathRecoveryBehavior(() -> activityLocation) {
            @Override protected boolean isPlayerDead() { return isDead; }
            @Override public void execute() { /* no-op */ }
        };
    }

    @Test
    public void shouldActivate_whenPlayerDead() {
        assertTrue(behaviorWith(true, new WorldPoint(3200, 3200, 0)).shouldActivate());
    }

    @Test
    public void shouldNotActivate_whenAlive() {
        assertFalse(behaviorWith(false, new WorldPoint(3200, 3200, 0)).shouldActivate());
    }

    @Test
    public void priority_is5() {
        assertEquals(5, behaviorWith(false, null).priority());
    }

    @Test
    public void name_isDeathRecovery() {
        assertEquals("DeathRecovery", behaviorWith(false, null).name());
    }
}
