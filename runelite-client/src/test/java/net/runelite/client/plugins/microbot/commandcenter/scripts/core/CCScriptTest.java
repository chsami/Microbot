package net.runelite.client.plugins.microbot.commandcenter.scripts.core;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class CCScriptTest {

    /** Minimal concrete CCScript for testing lifecycle. */
    private static class TestScript extends CCScript<TestScript.State> {
        enum State { RUNNING }

        boolean configureCalled = false;
        final List<String> statusCalls = new ArrayList<>();
        boolean antibanResetCalled = false;

        @Override protected void configure() { configureCalled = true; }
        @Override protected State getInitialState() { return State.RUNNING; }
        @Override protected State onTick(State currentState) { return currentState; }

        @Override
        void reportStatus(boolean running) {
            statusCalls.add(running ? "start" : "stop");
        }

        @Override
        void resetAntiban() {
            antibanResetCalled = true;
        }
    }

    /** Test behavior that tracks reset calls. */
    private static class TrackingBehavior implements CCBehavior {
        boolean resetCalled = false;
        @Override public int priority() { return 1; }
        @Override public boolean shouldActivate() { return false; }
        @Override public void execute() { }
        @Override public void reset() { resetCalled = true; }
        @Override public String name() { return "Tracking"; }
    }

    @Test
    public void run_callsSetActiveScriptTrue() {
        TestScript s = new TestScript();
        s.run(null, "Test Script");
        assertTrue("Status should report start", s.statusCalls.contains("start"));
    }

    @Test
    public void shutdown_callsSetActiveScriptFalse() {
        TestScript s = new TestScript();
        s.run(null, "Test Script");
        s.statusCalls.clear();
        s.shutdown();
        assertTrue("Status should report stop", s.statusCalls.contains("stop"));
    }

    @Test
    public void shutdown_resetsAllBehaviors() {
        TrackingBehavior b = new TrackingBehavior();
        TestScript s = new TestScript() {
            @Override protected void configure() {
                registerBehavior(b);
            }
        };
        s.run(null, "Test Script");
        s.shutdown();
        assertTrue("Behavior reset should have been called", b.resetCalled);
    }

    @Test
    public void shutdown_resetsAntibanSettings() {
        TestScript s = new TestScript();
        s.run(null, "Test Script");
        s.antibanResetCalled = false; // reset the flag set during run()
        s.shutdown();
        assertTrue("Antiban should be reset on shutdown", s.antibanResetCalled);
    }
}
