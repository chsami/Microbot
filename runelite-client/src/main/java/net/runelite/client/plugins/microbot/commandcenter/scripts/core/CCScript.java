package net.runelite.client.plugins.microbot.commandcenter.scripts.core;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.Config;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.commandcenter.status.BotStatusModel;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Base class for all Command Center scripts.
 * Provides behavior system, status reporting, antiban integration, and state machine support.
 *
 * @param <S> Enum type representing script states
 */
@Slf4j
public abstract class CCScript<S extends Enum<S>> extends Script {

    private final List<CCBehavior> behaviors = new ArrayList<>();
    @Getter private S currentState;
    private String scriptName;
    private Consumer<Object> antiBanTemplate;
    @Getter private WorldPoint activityLocation;
    private long lastStateChangeMs;

    // --- Template methods for subclasses ---

    /** Register behaviors and set antiban template. Called once during run(). */
    protected abstract void configure();

    /** Return the starting state for this script's state machine. */
    protected abstract S getInitialState();

    /** Called every tick (when no behavior activated). Return next state. */
    protected abstract S onTick(S currentState);

    // --- Framework API for subclasses ---

    protected final void registerBehavior(CCBehavior behavior) {
        behaviors.add(behavior);
    }

    protected final void setActivityLocation(WorldPoint point) {
        this.activityLocation = point;
    }

    protected final WorldPoint getActivityLocation() {
        return activityLocation;
    }

    protected final void setAntiBanTemplate(Consumer<Object> template) {
        this.antiBanTemplate = template;
    }

    // --- Lifecycle ---

    public boolean run(Config config, String name) {
        this.scriptName = name;
        this.currentState = getInitialState();
        this.lastStateChangeMs = System.currentTimeMillis();

        // Configure behaviors and antiban
        configure();
        behaviors.sort(Comparator.comparingInt(CCBehavior::priority));

        // Antiban setup
        resetAntiban();
        if (antiBanTemplate != null) {
            antiBanTemplate.accept(null); // templates are static, param unused
        }

        // Status API
        reportStatus(true);

        // Save initial location
        if (activityLocation == null && Microbot.isLoggedIn()) {
            activityLocation = Rs2Player.getWorldLocation();
        }

        // Tick loop
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (!Microbot.isLoggedIn()) return;
                if (Rs2AntibanSettings.actionCooldownActive) return;

                // Run behaviors by priority
                boolean behaviorHandled = false;
                for (CCBehavior behavior : behaviors) {
                    if (behavior.shouldActivate()) {
                        behavior.execute();
                        behaviorHandled = true;
                        break;
                    }
                }

                // If no behavior activated, run script logic
                if (!behaviorHandled) {
                    S nextState = onTick(currentState);
                    if (nextState != currentState) {
                        log.debug("[{}] State: {} -> {}", scriptName, currentState, nextState);
                        currentState = nextState;
                        lastStateChangeMs = System.currentTimeMillis();
                    }
                }
            } catch (Exception e) {
                log.error("[{}] Tick error: {}", scriptName, e.getMessage(), e);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);

        log.info("[{}] Started", scriptName);
        return true;
    }

    @Override
    public void shutdown() {
        // Reset behaviors
        for (CCBehavior behavior : behaviors) {
            try {
                behavior.reset();
            } catch (Exception e) {
                log.warn("[{}] Error resetting behavior {}: {}", scriptName, behavior.name(), e.getMessage());
            }
        }
        behaviors.clear();

        // Antiban reset
        resetAntiban();

        // Status API
        reportStatus(false);

        log.info("[{}] Stopped", scriptName);

        // Parent cleanup (cancels futures, resets walker, etc.)
        super.shutdown();
    }

    /** Reset the script state machine to its initial state. Used by StuckDetectionBehavior. */
    public void resetToInitialState() {
        S initial = getInitialState();
        log.info("[{}] Resetting state: {} -> {}", scriptName, currentState, initial);
        currentState = initial;
        lastStateChangeMs = System.currentTimeMillis();
    }

    /** Milliseconds since last state change. Used by StuckDetectionBehavior. */
    public long getMillisSinceLastStateChange() {
        return System.currentTimeMillis() - lastStateChangeMs;
    }

    // --- Internal helpers (package-private for test overrides) ---

    /** Report script status to BotStatusModel if available. */
    void reportStatus(boolean running) {
        try {
            BotStatusModel statusModel = Microbot.getInjector().getInstance(BotStatusModel.class);
            if (statusModel != null) {
                statusModel.setActiveScript(scriptName, running);
            }
        } catch (Exception e) {
            log.debug("BotStatusModel not available: {}", e.getMessage());
        }
    }

    /** Reset antiban settings. Package-private for test overrides. */
    void resetAntiban() {
        Rs2Antiban.resetAntibanSettings();
    }
}
