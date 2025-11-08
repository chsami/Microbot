package net.runelite.client.plugins.microbot.VoxPlugins.util.pvm;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.NPC;
import net.runelite.api.events.*;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.registry.HazardRegistry;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.registry.ProjectileRegistry;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.trackers.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * central event hub for all PvM tracking
 * subscribes to RuneLite events and distributes to specialized trackers
 * thread-safe singleton design
 */
@Slf4j
@Singleton
public class Rs2PvMEventManager {

    @Getter
    private static Rs2PvMEventManager instance;

    private final EventBus eventBus;
    private final AtomicBoolean isActive = new AtomicBoolean(false);

    // specialized trackers
    @Getter
    private Rs2ProjectileTracker projectileTracker;

    @Getter
    private Rs2AnimationTracker animationTracker;

    @Getter
    private Rs2HazardTracker hazardTracker;

    @Getter
    private Rs2HitsplatTracker hitsplatTracker;

    @Getter
    private Rs2BossPatternTracker bossPatternTracker;

    @Getter
    private Rs2PlayerAttackTracker playerAttackTracker;

    // combat orchestrator
    @Getter
    private Rs2PvMCombat pvmCombat;

    // automatic action manager
    @Getter
    private Rs2AutoActionManager autoActionManager;

    // registries (injected singletons)
    @Inject
    private HazardRegistry hazardRegistry;

    @Inject
    private ProjectileRegistry projectileRegistry;

    @Inject
    private Rs2PvMEventManager(EventBus eventBus) {
        this.eventBus = eventBus;
        instance = this;
    }

    /**
     * initialize all trackers and register event subscriptions
     */
    public void start() {
        if (isActive.compareAndSet(false, true)) {
            // initialize trackers
            this.projectileTracker = Rs2ProjectileTracker.getInstance();
            this.animationTracker = Rs2AnimationTracker.getInstance();
            this.hazardTracker = Rs2HazardTracker.getInstance();
            this.hitsplatTracker = Rs2HitsplatTracker.getInstance();
            this.bossPatternTracker = Rs2BossPatternTracker.getInstance();
            this.playerAttackTracker = Rs2PlayerAttackTracker.getInstance();

            // initialize combat orchestrator
            this.pvmCombat = Rs2PvMCombat.getInstance();

            // initialize automatic action manager
            this.autoActionManager = Rs2AutoActionManager.getInstance();
            this.autoActionManager.initialize(
                pvmCombat,
                net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.handlers.Rs2MovementHandler.getInstance(),
                net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.handlers.Rs2CombatHandler.getInstance(),
                projectileTracker
            );

            // register with event bus
            eventBus.register(this);

            log.info("PvM event manager started - all trackers initialized");
        } else {
            log.warn("PvM event manager already running");
        }
    }

    /**
     * stop event manager and cleanup
     */
    public void stop() {
        if (isActive.compareAndSet(true, false)) {
            // unregister from event bus
            eventBus.unregister(this);

            // clear all trackers
            if (projectileTracker != null) projectileTracker.invalidateAll();
            if (animationTracker != null) animationTracker.clear();
            if (hazardTracker != null) hazardTracker.invalidateAll();
            if (hitsplatTracker != null) hitsplatTracker.invalidateAll();
            if (bossPatternTracker != null) bossPatternTracker.clear();
            if (playerAttackTracker != null) playerAttackTracker.clear();

            // clear combat orchestrator
            if (pvmCombat != null) pvmCombat.clear();

            // disable all automatic actions
            if (autoActionManager != null) autoActionManager.disableAll();

            // clear registries
            if (hazardRegistry != null) hazardRegistry.clearAll();
            if (projectileRegistry != null) projectileRegistry.clearAll();

            log.info("PvM event manager stopped - all trackers cleared");
        }
    }

    /**
     * check if event manager is active
     */
    public boolean isActive() {
        return isActive.get();
    }

    // ============================================
    // Event Subscriptions (distribute to trackers)
    // ============================================

    @Subscribe
    public void onProjectileMoved(ProjectileMoved event) {
        if (!isActive.get()) return;

        // check if projectile is targeting player
        Actor target = event.getProjectile().getTargetActor();
        boolean targetingPlayer = target != null &&
            target.equals(Microbot.getClient().getLocalPlayer());

        // distribute to projectile tracker
        if (projectileTracker != null) {
            projectileTracker.onProjectileMoved(event, targetingPlayer);
        }
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        if (!isActive.get()) return;

        // distribute to animation tracker (filters internally)
        if (animationTracker != null) {
            animationTracker.onAnimationChanged(event);
        }
    }

    @Subscribe
    public void onPostAnimation(PostAnimation event) {
        if (!isActive.get()) return;

        // PostAnimation fires after animation is fully loaded
        // more reliable than AnimationChanged for detecting attack animations
        // distribute to animation tracker for enhanced detection
        if (animationTracker != null) {
            animationTracker.onPostAnimation(event);
        }
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {
        if (!isActive.get()) return;

        int objectId = event.getGameObject().getId();

        // check if object is hazardous
        if (hazardRegistry != null && hazardRegistry.isHazardousObject(objectId)) {
            // distribute to hazard tracker
            if (hazardTracker != null) {
                hazardTracker.onGameObjectSpawned(event);
            }
        }
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event) {
        if (!isActive.get()) return;

        int objectId = event.getGameObject().getId();

        // check if object is hazardous
        if (hazardRegistry != null && hazardRegistry.isHazardousObject(objectId)) {
            // distribute to hazard tracker
            if (hazardTracker != null) {
                hazardTracker.onGameObjectDespawned(event);
            }
        }
    }

    @Subscribe
    public void onGraphicsObjectCreated(GraphicsObjectCreated event) {
        if (!isActive.get()) return;

        int graphicId = event.getGraphicsObject().getId();

        // check if graphic is hazardous
        if (hazardRegistry != null && hazardRegistry.isHazardousGraphic(graphicId)) {
            // distribute to hazard tracker
            if (hazardTracker != null) {
                hazardTracker.onGraphicsObjectCreated(event);
            }
        }
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event) {
        if (!isActive.get()) return;

        int npcId = event.getNpc().getId();

        // check if NPC is registered as hazardous
        if (hazardRegistry != null && hazardRegistry.isHazardousNpc(npcId)) {
            // distribute to hazard tracker
            if (hazardTracker != null) {
                hazardTracker.onNpcSpawned(event);
            }
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        if (!isActive.get()) return;

        int npcId = event.getNpc().getId();

        // check if NPC is registered as hazardous
        if (hazardRegistry != null && hazardRegistry.isHazardousNpc(npcId)) {
            // distribute to hazard tracker
            if (hazardTracker != null) {
                hazardTracker.onNpcDespawned(event);
            }
        }
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event) {
        if (!isActive.get()) return;

        // distribute all hitsplats to tracker (filters internally)
        if (hitsplatTracker != null) {
            hitsplatTracker.onHitsplatApplied(event);
        }

        // distribute to player attack tracker (tracks player hits for weapon swap timing)
        if (playerAttackTracker != null) {
            playerAttackTracker.onHitsplatApplied(event);
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (!isActive.get()) return;

        // clear all trackers on logout/hopping
        switch (event.getGameState()) {
            case HOPPING:
            case LOGIN_SCREEN:
            case LOGGING_IN:
                log.debug("Game state changed to {}, clearing all trackers", event.getGameState());
                if (projectileTracker != null) projectileTracker.invalidateAll();
                if (animationTracker != null) animationTracker.clear();
                if (hazardTracker != null) hazardTracker.invalidateAll();
                if (hitsplatTracker != null) hitsplatTracker.invalidateAll();
                if (bossPatternTracker != null) bossPatternTracker.clear();
                if (playerAttackTracker != null) playerAttackTracker.clear();
                break;
            default:
                break;
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (!isActive.get()) return;

        long startTime = System.currentTimeMillis();

        // CRITICAL: process prayer flicking FIRST (must happen at start of tick)
        // lazy flicking and 1-tick flicking require precise timing
        net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.handlers.Rs2PrayerHandler prayerHandler =
            net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.handlers.Rs2PrayerHandler.getInstance();
        if (prayerHandler != null) {
            prayerHandler.processFlicks();
        }

        // process automatic actions (reactive automation)
        // monitors game state and queues actions based on configuration
        if (autoActionManager != null) {
            autoActionManager.processAutoActions();
        }

        // execute queued actions (manual + automatic)
        // ensures all actions execute before any game state updates
        if (pvmCombat != null) {
            pvmCombat.executeQueuedActions();
        }

        // cleanup stale data from all trackers
        if (projectileTracker != null) projectileTracker.onGameTick();
        if (animationTracker != null) animationTracker.onGameTick();
        if (hazardTracker != null) hazardTracker.onGameTick();
        if (hitsplatTracker != null) hitsplatTracker.onGameTick();
        if (bossPatternTracker != null) bossPatternTracker.onGameTick();
        if (playerAttackTracker != null) playerAttackTracker.onGameTick();

        long executionTime = System.currentTimeMillis() - startTime;

        // warn if execution took longer than 600ms (1 game tick)
        if (executionTime > 600) {
            log.warn("GameTick processing took {}ms (exceeded 600ms tick limit!)", executionTime);
        } else if (executionTime > 300) {
            log.debug("GameTick processing took {}ms (>50% of tick)", executionTime);
        }
    }
}
