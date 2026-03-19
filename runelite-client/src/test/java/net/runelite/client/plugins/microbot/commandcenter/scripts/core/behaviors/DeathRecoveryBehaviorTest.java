package net.runelite.client.plugins.microbot.commandcenter.scripts.core.behaviors;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.commandcenter.scripts.core.CCBehaviorTestBase;
import org.junit.Test;
import static org.junit.Assert.*;

public class DeathRecoveryBehaviorTest extends CCBehaviorTestBase<DeathRecoveryBehavior> {

    @Override
    protected DeathRecoveryBehavior createDefaultBehavior() {
        return new DeathRecoveryBehavior(() -> null) {
            @Override protected boolean isPlayerDead() { return false; }
            @Override public void execute() {}
        };
    }

    @Override protected int expectedPriority() { return 5; }
    @Override protected String expectedName() { return "DeathRecovery"; }

    private DeathRecoveryBehavior behaviorWith(boolean isDead) {
        return new DeathRecoveryBehavior(() -> new WorldPoint(3200, 3200, 0)) {
            @Override protected boolean isPlayerDead() { return isDead; }
            @Override public void execute() {}
        };
    }

    @Test
    public void shouldActivate_whenPlayerDead() {
        assertTrue(behaviorWith(true).shouldActivate());
    }

    @Test
    public void shouldNotActivate_whenAlive() {
        assertFalse(behaviorWith(false).shouldActivate());
    }
}
