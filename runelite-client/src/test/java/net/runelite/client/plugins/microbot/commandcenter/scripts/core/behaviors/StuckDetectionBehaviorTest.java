package net.runelite.client.plugins.microbot.commandcenter.scripts.core.behaviors;

import net.runelite.client.plugins.microbot.commandcenter.scripts.core.CCBehaviorTestBase;
import net.runelite.client.plugins.microbot.commandcenter.scripts.core.StubCCScript;
import org.junit.Test;
import static org.junit.Assert.*;

public class StuckDetectionBehaviorTest extends CCBehaviorTestBase<StuckDetectionBehavior> {

    @Override
    protected StuckDetectionBehavior createDefaultBehavior() {
        return new StuckDetectionBehavior();
    }

    @Override protected int expectedPriority() { return 1; }
    @Override protected String expectedName() { return "StuckDetection"; }

    @Test
    public void shouldActivate_whenStuckBeyondThreshold() {
        StubCCScript stub = new StubCCScript();
        stub.setStubbedMillisSinceLastStateChange(61_000);

        StuckDetectionBehavior b = new StuckDetectionBehavior();
        b.setScript(stub);

        assertTrue("Should activate when idle > 60s", b.shouldActivate());
    }

    @Test
    public void shouldNotActivate_whenWithinThreshold() {
        StubCCScript stub = new StubCCScript();
        stub.setStubbedMillisSinceLastStateChange(30_000);

        StuckDetectionBehavior b = new StuckDetectionBehavior();
        b.setScript(stub);

        assertFalse("Should not activate within threshold", b.shouldActivate());
    }

    @Test
    public void shouldNotActivate_whenScriptIsNull() {
        StuckDetectionBehavior b = new StuckDetectionBehavior();
        // script is null by default
        assertFalse("Should not activate without script reference", b.shouldActivate());
    }

    @Test
    public void shouldNotActivate_atExactThreshold() {
        StubCCScript stub = new StubCCScript();
        stub.setStubbedMillisSinceLastStateChange(60_000);

        StuckDetectionBehavior b = new StuckDetectionBehavior();
        b.setScript(stub);

        assertFalse("Should not activate at exactly 60s (strict >)", b.shouldActivate());
    }
}
