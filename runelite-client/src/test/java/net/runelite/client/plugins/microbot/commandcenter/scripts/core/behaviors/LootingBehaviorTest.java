package net.runelite.client.plugins.microbot.commandcenter.scripts.core.behaviors;

import net.runelite.client.plugins.microbot.commandcenter.scripts.core.CCBehaviorTestBase;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class LootingBehaviorTest extends CCBehaviorTestBase<LootingBehavior> {

    @Override
    protected LootingBehavior createDefaultBehavior() {
        return new LootingBehavior(List.of(), 10) {
            @Override protected boolean hasMatchingGroundItem() { return false; }
            @Override public void execute() {}
        };
    }

    @Override protected int expectedPriority() { return 40; }
    @Override protected String expectedName() { return "Looting"; }

    private LootingBehavior behaviorWith(List<String> itemNames, int radius, boolean matchingItemOnGround) {
        return new LootingBehavior(itemNames, radius) {
            @Override protected boolean hasMatchingGroundItem() { return matchingItemOnGround; }
            @Override public void execute() {}
        };
    }

    @Test
    public void shouldActivate_whenItemsConfigured_andMatchingItemOnGround() {
        assertTrue(behaviorWith(List.of("Bones"), 10, true).shouldActivate());
    }

    @Test
    public void shouldNotActivate_whenNoItemsConfigured() {
        assertFalse(behaviorWith(List.of(), 10, true).shouldActivate());
    }

    @Test
    public void shouldNotActivate_whenNoMatchingItemOnGround() {
        assertFalse(behaviorWith(List.of("Bones"), 10, false).shouldActivate());
    }
}
