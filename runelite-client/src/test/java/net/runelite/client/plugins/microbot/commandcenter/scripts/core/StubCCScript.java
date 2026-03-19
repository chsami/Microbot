package net.runelite.client.plugins.microbot.commandcenter.scripts.core;

/**
 * Minimal CCScript for testing behaviors that need a script reference.
 * Overrides reportStatus() and resetAntiban() to no-op (avoids game API calls).
 * Provides controllable getMillisSinceLastStateChange() for StuckDetectionBehavior.
 */
public class StubCCScript extends CCScript<StubCCScript.State> {

    public enum State { ACTIVE }

    // Override the inherited 10-thread pool with a zero-core pool to avoid
    // spawning idle threads in tests (Script base class creates it at init time).
    { scheduledExecutorService = java.util.concurrent.Executors.newScheduledThreadPool(0); }

    private long stubbedMillisSinceLastStateChange = 0;

    @Override protected void configure() {}
    @Override protected State getInitialState() { return State.ACTIVE; }
    @Override protected State onTick(State currentState) { return currentState; }
    @Override void reportStatus(boolean running) {}
    @Override void resetAntiban() {}

    /** Set the value returned by getMillisSinceLastStateChange(). */
    public void setStubbedMillisSinceLastStateChange(long millis) {
        this.stubbedMillisSinceLastStateChange = millis;
    }

    @Override
    public long getMillisSinceLastStateChange() {
        return stubbedMillisSinceLastStateChange;
    }
}
