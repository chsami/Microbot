package net.runelite.client.plugins.microbot.VoxPlugins.util.pvm;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.handlers.Rs2CombatHandler;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.handlers.Rs2MovementHandler;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.model.ProjectileData;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.trackers.Rs2ProjectileTracker;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import javax.inject.Singleton;
import java.util.Optional;

/**
 * OPTIONAL automatic action manager - reactive automation for simple plugins
 *
 * NOTE: This is an OPTIONAL helper for plugins that want basic automation.
 * Advanced plugins (Hunllef, Inferno, etc.) should use Rs2PvMCombat action queue directly
 * and make their own decisions about when to eat/dodge/pray.
 *
 * This manager provides pre-built reactive behaviors:
 * - Auto-eat when health drops below threshold
 * - Auto-dodge when standing on hazards
 * - Auto-prayer switch on projectiles (basic only)
 * - Auto-attack when weapon ready
 *
 * All features are DISABLED by default and must be explicitly enabled.
 * Use this for simple slayer tasks or basic PVM, NOT for complex boss mechanics.
 */
@Slf4j
@Singleton
public class Rs2AutoActionManager {

    private static Rs2AutoActionManager instance;

    // configuration
    private boolean autoEatEnabled = false;
    private int eatHealthThreshold = 50; // eat below 50 HP

    private boolean autoDodgeEnabled = false;

    private boolean autoPrayerEnabled = false;

    private boolean autoAttackEnabled = false;
    private NPC autoAttackTarget = null;

    // handler references
    private Rs2PvMCombat pvmCombat;
    private Rs2MovementHandler movementHandler;
    private Rs2CombatHandler combatHandler;
    private Rs2ProjectileTracker projectileTracker;

    // track last queued actions to prevent duplicates
    private volatile boolean eatActionQueued = false;
    private volatile boolean dodgeActionQueued = false;
    private volatile boolean attackActionQueued = false;

    // track game state for change detection
    private volatile boolean wasOnHazard = false;
    private volatile NPC lastTarget = null;
    private volatile int lastHealth = 0;

    private Rs2AutoActionManager() {
    }

    public static synchronized Rs2AutoActionManager getInstance() {
        if (instance == null) {
            instance = new Rs2AutoActionManager();
        }
        return instance;
    }

    /**
     * initialize with handler references
     */
    public void initialize(Rs2PvMCombat pvmCombat, Rs2MovementHandler movementHandler,
                          Rs2CombatHandler combatHandler, Rs2ProjectileTracker projectileTracker) {
        this.pvmCombat = pvmCombat;
        this.movementHandler = movementHandler;
        this.combatHandler = combatHandler;
        this.projectileTracker = projectileTracker;
        log.info("Auto action manager initialized");
    }

    /**
     * process automatic actions every game tick
     *
     * IMPORTANT: This is OPTIONAL reactive automation for simple plugins.
     * All features are DISABLED by default.
     *
     * For complex boss mechanics (Hunllef, Inferno, Raids):
     * - Disable this manager (disableAll())
     * - Use Rs2PvMCombat action queue directly
     * - Implement custom logic in your plugin
     *
     * This manager is best for:
     * - Simple slayer tasks
     * - Basic PVM (dragons, GWD, etc.)
     * - Testing/debugging the framework
     */
    public void processAutoActions() {
        // detect game state changes and cancel stale actions
        detectGameStateChanges();

        // priority order matters!
        // 1. survival (eat)
        // 2. dodge hazards
        // 3. prayer switching
        // 4. attacking

        if (autoEatEnabled) {
            checkAutoEat();
        }

        if (autoDodgeEnabled) {
            checkAutoDodge();
        }

        if (autoPrayerEnabled) {
            checkAutoPrayer();
        }

        if (autoAttackEnabled && autoAttackTarget != null) {
            checkAutoAttack();
        }
    }

    /**
     * detect game state changes and cancel stale actions
     */
    private void detectGameStateChanges() {
        // check health changes (healing)
        int currentHp = Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS);
        if (currentHp > lastHealth && eatActionQueued) {
            // healed - cancel eat actions
            cancelActionsByType("Auto-eat");
            eatActionQueued = false;
            log.debug("Cancelled eat actions - health increased to {}", currentHp);
        }
        lastHealth = currentHp;

        // check hazard status changes
        boolean onHazardNow = movementHandler != null && movementHandler.isPlayerOnHazard();
        if (!onHazardNow && wasOnHazard && dodgeActionQueued) {
            // moved off hazard - cancel dodge actions
            cancelActionsByType("Auto-dodge");
            dodgeActionQueued = false;
            log.debug("Cancelled dodge actions - no longer on hazard");
        }
        wasOnHazard = onHazardNow;

        // check target changes
        if (autoAttackTarget != null && autoAttackTarget != lastTarget) {
            if (lastTarget != null) {
                // target changed - cancel old attack actions
                cancelActionsByType("Auto-attack");
                attackActionQueued = false;
                log.debug("Cancelled attack actions - target changed");
            }
            lastTarget = autoAttackTarget;
        }

        // check if target died
        if (autoAttackTarget != null && autoAttackTarget.isDead() && attackActionQueued) {
            // target died - cancel attack actions
            cancelActionsByType("Auto-attack");
            attackActionQueued = false;
            log.debug("Cancelled attack actions - target died");
        }
    }

    /**
     * cancel queued actions by description pattern
     */
    private void cancelActionsByType(String descriptionPattern) {
        if (pvmCombat == null) return;

        int currentTick = Microbot.getClient().getTickCount();

        // check next 10 ticks for matching actions
        for (int i = 0; i < 10; i++) {
            int tick = currentTick + i;
            pvmCombat.getActionsForTick(tick).stream()
                .filter(action -> action.getDescription().contains(descriptionPattern))
                .findAny()
                .ifPresent(action -> {
                    pvmCombat.cancelActionsForTick(tick);
                });
        }
    }

    /**
     * auto-eat when health below threshold
     * uses Rs2Player.eatAt() which automatically finds best food in inventory
     */
    private void checkAutoEat() {
        // skip if already queued
        if (eatActionQueued) return;

        double currentHp = Rs2Player.getHealthPercentage();

        if (currentHp <= eatHealthThreshold) {
            // delegate to Rs2Player which handles best food detection
            queueEatAction();
            eatActionQueued = true;
        }
    }

    /**
     * auto-dodge when standing on hazard OR when hazard approaching
     * PREDICTIVE: checks 2 ticks ahead for moving hazards (tornadoes)
     */
    private void checkAutoDodge() {
        if (movementHandler == null) return;

        // skip if already queued
        if (dodgeActionQueued) return;

        // PREDICTIVE CHECK: will player be in danger within 2 ticks?
        // critical for moving hazards like Hunllef tornadoes
        if (movementHandler.willPlayerBeInDanger(2)) {
            queueDodgeAction(2); // use predictive safe tiles
            dodgeActionQueued = true;
            return;
        }

        // REACTIVE CHECK: already on hazard (fallback)
        if (movementHandler.isPlayerOnHazard()) {
            queueDodgeAction(0); // immediate dodge needed
            dodgeActionQueued = true;
        }
    }

    /**
     * auto prayer switch based on incoming projectiles
     */
    private void checkAutoPrayer() {
        if (projectileTracker == null) return;

        // get projectiles targeting player
        projectileTracker.stream()
            .filter(ProjectileData::isTargetingPlayer)
            .filter(data -> data.getTicksUntilImpact() <= 2) // 2 tick window
            .findFirst()
            .ifPresent(projectile -> {
                queuePrayerSwitchAction(projectile);
            });
    }

    /**
     * auto-attack when weapon off cooldown and pattern allows
     */
    private void checkAutoAttack() {
        if (combatHandler == null || autoAttackTarget == null) return;

        // skip if already queued
        if (attackActionQueued) return;

        // check if target still valid
        if (autoAttackTarget.isDead() || !Rs2Npc.hasLineOfSight(new Rs2NpcModel(autoAttackTarget))) {
            return;
        }

        // check if can attack (weapon cooldown + boss pattern)
        if (!combatHandler.isWeaponOnCooldown()) {
            int npcIndex = autoAttackTarget.getIndex();

            // check boss pattern if registered
            if (combatHandler.getBossPattern(npcIndex).isPresent()) {
                if (combatHandler.canAttackBoss(npcIndex, 6)) {
                    queueAttackAction();
                    attackActionQueued = true;
                }
            } else {
                // no pattern tracking - just attack
                queueAttackAction();
                attackActionQueued = true;
            }
        }
    }

    /**
     * queue eat action
     * delegates to Rs2Player.eatAt() which handles best food selection automatically
     */
    private void queueEatAction() {
        // implement interface methods
        Rs2PvMCombat.PvMAction wrappedAction = new Rs2PvMCombat.PvMAction() {
            @Override
            public boolean execute() {
                // use Rs2Player.eatAt() which automatically finds best food
                // fastFood=false for compatibility with combat
                boolean success = Rs2Player.eatAt(100, false); // 100% threshold since we already checked
                // reset queued flag after execution
                eatActionQueued = false;
                return success;
            }

            @Override
            public int getPriority() {
                return Rs2PvMCombat.Priority.CONSUME;
            }

            @Override
            public String getDescription() {
                return "Auto-eat (HP: " + Rs2Player.getHealthPercentage() + "%)";
            }

            @Override
            public boolean requiresNoTickLoss() {
                return false; // can eat while moving
            }
        };

        pvmCombat.queueActionRelative(wrappedAction, 0);
        log.debug("Queued auto-eat action");
    }

    /**
     * queue dodge action with predictive safe tile selection
     *
     * @param ticksAhead how many ticks ahead to check for safety (0 = immediate, 2 = predictive)
     */
    private void queueDodgeAction(int ticksAhead) {
        Rs2PvMCombat.PvMAction dodgeAction = new Rs2PvMCombat.PvMAction() {
            @Override
            public boolean execute() {
                // use predictive safe tiles if looking ahead, otherwise immediate
                Optional<WorldPoint> safeTile = ticksAhead > 0
                    ? movementHandler.getPredictiveNearestSafeTile(ticksAhead)
                    : movementHandler.getNearestSafeTile();

                if (safeTile.isPresent()) {
                    Rs2Walker.walkFastCanvas(safeTile.get());
                    // reset queued flag after execution
                    dodgeActionQueued = false;
                    log.debug("Dodged to safe tile (predictive: {} ticks)", ticksAhead);
                    return true;
                }
                dodgeActionQueued = false;
                log.warn("No safe tiles found! (predictive: {} ticks)", ticksAhead);
                return false;
            }

            @Override
            public int getPriority() {
                return Rs2PvMCombat.Priority.DODGE;
            }

            @Override
            public String getDescription() {
                return ticksAhead > 0
                    ? "Auto-dodge (predictive " + ticksAhead + " ticks)"
                    : "Auto-dodge hazard";
            }

            @Override
            public boolean requiresNoTickLoss() {
                return false; // can move anytime
            }
        };

        pvmCombat.queueActionRelative(dodgeAction, 0);
        log.debug("Queued auto-dodge action (predictive: {} ticks)", ticksAhead);
    }

    /**
     * queue prayer switch action
     */
    private void queuePrayerSwitchAction(ProjectileData projectile) {
        Rs2PvMCombat.PvMAction prayerAction = new Rs2PvMCombat.PvMAction() {
            @Override
            public boolean execute() {
                // determine prayer based on projectile type
                Rs2PrayerEnum prayer = determinePrayerForProjectile(projectile);
                if (prayer != null) {
                    return Rs2Prayer.toggle(prayer, true);
                }
                return false;
            }

            @Override
            public int getPriority() {
                return Rs2PvMCombat.Priority.PRAYER;
            }

            @Override
            public String getDescription() {
                return "Auto prayer switch (projectile: " + projectile.getId() + ")";
            }

            @Override
            public boolean requiresNoTickLoss() {
                return false; // prayers don't cause tick loss
            }
        };

        // queue for impact tick (projectile impact - 1 tick for prayer activation)
        int queueTick = Math.max(0, projectile.getTicksUntilImpact() - 1);
        pvmCombat.queueActionRelative(prayerAction, queueTick);
        log.debug("Queued auto prayer switch for tick +{}", queueTick);
    }

    /**
     * queue attack action
     */
    private void queueAttackAction() {
        Rs2PvMCombat.PvMAction attackAction = new Rs2PvMCombat.PvMAction() {
            @Override
            public boolean execute() {
                if (autoAttackTarget == null || autoAttackTarget.isDead()) {
                    attackActionQueued = false;
                    return false;
                }

                // use pattern-aware attack if boss registered
                int npcIndex = autoAttackTarget.getIndex();
                boolean success;
                if (combatHandler.getBossPattern(npcIndex).isPresent()) {
                    success = combatHandler.coordinatedAttackWithPattern(autoAttackTarget, 6);
                } else {
                    success = Rs2Npc.attack(new Rs2NpcModel(autoAttackTarget));
                }

                // reset queued flag after execution
                attackActionQueued = false;
                return success;
            }

            @Override
            public int getPriority() {
                return Rs2PvMCombat.Priority.ATTACK;
            }

            @Override
            public String getDescription() {
                return "Auto-attack " + (autoAttackTarget != null ? autoAttackTarget.getName() : "target");
            }

            @Override
            public boolean requiresNoTickLoss() {
                return true; // attacks require no tick loss
            }
        };

        pvmCombat.queueAction(attackAction);
        log.debug("Queued auto-attack action");
    }

    /**
     * determine prayer based on projectile type
     * can be enhanced with projectile registry
     */
    private Rs2PrayerEnum determinePrayerForProjectile(ProjectileData projectile) {
        // basic implementation - can be enhanced with ProjectileRegistry
        int projectileId = projectile.getId();

        // example mappings (extend based on boss)
        // these are placeholder IDs - replace with actual projectile IDs
        if (projectileId >= 1000 && projectileId < 2000) {
            return Rs2PrayerEnum.PROTECT_MAGIC;
        } else if (projectileId >= 2000 && projectileId < 3000) {
            return Rs2PrayerEnum.PROTECT_RANGE;
        }

        return null;
    }

    // ============================================
    // Configuration Methods
    // ============================================

    public void setAutoEat(boolean enabled, int healthThreshold) {
        this.autoEatEnabled = enabled;
        this.eatHealthThreshold = healthThreshold;
        log.info("Auto-eat: enabled={}, threshold={}hp", enabled, healthThreshold);
    }

    public void setAutoDodge(boolean enabled) {
        this.autoDodgeEnabled = enabled;
        log.info("Auto-dodge: enabled={}", enabled);
    }

    public void setAutoPrayer(boolean enabled) {
        this.autoPrayerEnabled = enabled;
        log.info("Auto-prayer: enabled={}", enabled);
    }

    public void setAutoAttack(boolean enabled, NPC target) {
        this.autoAttackEnabled = enabled;
        this.autoAttackTarget = target;
        log.info("Auto-attack: enabled={}, target={}", enabled, target != null ? target.getName() : "none");
    }

    public void disableAutoAttack() {
        this.autoAttackEnabled = false;
        this.autoAttackTarget = null;
    }

    public void disableAll() {
        this.autoEatEnabled = false;
        this.autoDodgeEnabled = false;
        this.autoPrayerEnabled = false;
        this.autoAttackEnabled = false;
        this.autoAttackTarget = null;

        // reset queued flags
        this.eatActionQueued = false;
        this.dodgeActionQueued = false;
        this.attackActionQueued = false;

        // reset state tracking
        this.wasOnHazard = false;
        this.lastTarget = null;
        this.lastHealth = 0;

        log.info("All auto actions disabled");
    }

    // getters for status
    public boolean isAutoEatEnabled() { return autoEatEnabled; }
    public boolean isAutoDodgeEnabled() { return autoDodgeEnabled; }
    public boolean isAutoPrayerEnabled() { return autoPrayerEnabled; }
    public boolean isAutoAttackEnabled() { return autoAttackEnabled; }
    public int getEatHealthThreshold() { return eatHealthThreshold; }
    public NPC getAutoAttackTarget() { return autoAttackTarget; }
}
