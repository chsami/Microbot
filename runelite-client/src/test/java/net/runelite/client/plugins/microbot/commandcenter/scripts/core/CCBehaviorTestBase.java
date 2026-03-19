package net.runelite.client.plugins.microbot.commandcenter.scripts.core;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Abstract base for CCBehavior unit tests.
 * Subclasses implement 3 abstract methods and inherit 3 contract tests automatically.
 *
 * Usage:
 * <pre>
 * public class EatingBehaviorTest extends CCBehaviorTestBase&lt;EatingBehavior&gt; {
 *     &#064;Override protected EatingBehavior createDefaultBehavior() {
 *         return new EatingBehavior(50) {
 *             &#064;Override protected int getHpPercent() { return 100; }
 *             &#064;Override protected boolean hasFood() { return false; }
 *             &#064;Override public void execute() {}
 *         };
 *     }
 *     &#064;Override protected int expectedPriority() { return 10; }
 *     &#064;Override protected String expectedName() { return "Eating"; }
 *     // ... additional shouldActivate tests ...
 * }
 * </pre>
 */
public abstract class CCBehaviorTestBase<B extends CCBehavior> {

    /** Create a testable instance with safe (no-op) game query overrides. */
    protected abstract B createDefaultBehavior();

    /** Expected return value of priority(). */
    protected abstract int expectedPriority();

    /** Expected return value of name(). */
    protected abstract String expectedName();

    @Test
    public void contract_priorityMatchesExpected() {
        assertEquals(expectedPriority(), createDefaultBehavior().priority());
    }

    @Test
    public void contract_nameMatchesExpected() {
        assertEquals(expectedName(), createDefaultBehavior().name());
    }

    @Test
    public void contract_resetIsSafe() {
        createDefaultBehavior().reset(); // must not throw
    }
}
