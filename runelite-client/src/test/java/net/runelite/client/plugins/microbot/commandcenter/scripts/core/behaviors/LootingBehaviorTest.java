package net.runelite.client.plugins.microbot.commandcenter.scripts.core.behaviors;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class LootingBehaviorTest {

    private LootingBehavior behaviorWith(List<String> itemNames, int radius, boolean matchingItemOnGround) {
        return new LootingBehavior(itemNames, radius) {
            @Override protected boolean hasMatchingGroundItem() { return matchingItemOnGround; }
            @Override public void execute() { /* no-op */ }
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

    @Test
    public void priority_is40() {
        assertEquals(40, behaviorWith(List.of(), 10, false).priority());
    }

    @Test
    public void name_isLooting() {
        assertEquals("Looting", behaviorWith(List.of(), 10, false).name());
    }
}
