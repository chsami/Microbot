package net.runelite.client.plugins.microbot.commandcenter.scripts.core.behaviors;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.commandcenter.scripts.core.CCBehavior;
import net.runelite.client.plugins.microbot.commandcenter.scripts.core.CCScript;

@Slf4j
public class StuckDetectionBehavior implements CCBehavior {

    private static final long STUCK_THRESHOLD_MS = 60_000;

    @Setter private CCScript<?> script;

    @Override public int priority() { return 1; }
    @Override public String name() { return "StuckDetection"; }

    @Override
    public boolean shouldActivate() {
        return script != null && getMillisSinceLastStateChange() > STUCK_THRESHOLD_MS;
    }

    @Override
    public void execute() {
        log.warn("[StuckDetection] No state change for {}s — resetting to initial state",
            STUCK_THRESHOLD_MS / 1000);
        if (script != null) {
            script.resetToInitialState();
        }
    }

    @Override
    public void reset() { /* stateless */ }

    // --- Overridable for tests ---

    protected long getMillisSinceLastStateChange() {
        return script != null ? script.getMillisSinceLastStateChange() : 0;
    }
}
