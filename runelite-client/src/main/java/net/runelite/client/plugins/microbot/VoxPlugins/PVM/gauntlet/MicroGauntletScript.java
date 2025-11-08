package net.runelite.client.plugins.microbot.VoxPlugins.PVM.gauntlet;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.HeadIcon;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.VoxPlugins.PVM.gauntlet.util.Rs2GauntletUtil;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.Rs2PvMCombat;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.Rs2PvMEventManager;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.enums.CombatStyle;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.handlers.Rs2MovementHandler;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.handlers.Rs2OverheadPrayerHandler;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.handlers.Rs2PrayerHandler;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.handlers.Rs2WeaponSwitchHandler;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.trackers.Rs2AnimationTracker;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.trackers.Rs2BossPatternTracker;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.trackers.Rs2PlayerAttackTracker;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Full automation for Gauntlet Hunllef boss fight
 * Version 4.0 - Event-driven tick-accurate execution with anti-ban features
 *
 * CRITICAL: GameTick event triggers execution on separate thread
 * Keeps client thread unblocked for optimal performance
 * Actions queued via Rs2PvMCombat execute on GameTick event
 *
 * Author: Voxslyvae
 */
@Slf4j
public class MicroGauntletScript extends Script {
    public static double version = 4.0;

    private MicroGauntletConfig config;
    private Rs2PvMCombat pvmCombat;
    private EventBus eventBus;

    // framework trackers (injected from plugin)
    private final Rs2BossPatternTracker bossPatternTracker;
    private final Rs2PlayerAttackTracker playerAttackTracker;
    private final Rs2PrayerHandler prayerHandler;

    // framework handlers
    private Rs2OverheadPrayerHandler overheadPrayerHandler;
    private Rs2WeaponSwitchHandler weaponSwitchHandler;
    private Rs2AnimationTracker animationTracker;
    private Rs2MovementHandler movementHandler;
    private net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.handlers.Rs2CombatHandler combatHandler;

    // state tracking
    private NPC currentHunllef = null;
    private boolean fightActive = false;
    private int lastEatTick = 0; // tick-based cooldown (not milliseconds)
    private int lastWeaponSwitchTick = 0;
    private boolean stompDodgeInProgress = false;
    private boolean isActive = false;

    // 5:1 weapon switching state
    private int primaryWeaponId = -1;     // best weapon (tier 3, highest combat stat)
    private int meleeWeaponId = -1;       // halberd
    private int magicWeaponId = -1;       // staff
    private int rangedWeaponId = -1;      // bow
    private boolean weaponsScanned = false; // flag to scan weapons once per fight

    // tornado tracking for cycle integration
    private java.util.Set<Integer> trackedTornadoIndices = new java.util.HashSet<>();
    private int lastTornadoSpawnTick = 0;

    // anti-ban: mistake tracking
    private boolean shouldMissPrayerSwitch = false;
    private boolean shouldUseWrongWeapon = false;
    private int mistakeCounter = 0;

    // food item IDs
    private static final int CRYSTAL_FOOD = 25960;
    private static final int CORRUPTED_FOOD = 25958;

    public MicroGauntletScript(Rs2BossPatternTracker bossPatternTracker,
                              Rs2PlayerAttackTracker playerAttackTracker,
                              Rs2PrayerHandler prayerHandler,
                              EventBus eventBus) {
        this.bossPatternTracker = bossPatternTracker;
        this.playerAttackTracker = playerAttackTracker;
        this.prayerHandler = prayerHandler;
        this.eventBus = eventBus;
    }

    /**
     * Start the script - register event subscription
     */
    public boolean run(MicroGauntletConfig config) {
        this.config = config;
        this.pvmCombat = Rs2PvMEventManager.getInstance().getPvmCombat();

        // get framework handlers
        this.overheadPrayerHandler = Rs2OverheadPrayerHandler.getInstance();
        this.weaponSwitchHandler = Rs2WeaponSwitchHandler.getInstance();
        this.animationTracker = Rs2PvMEventManager.getInstance().getAnimationTracker();
        this.movementHandler = Rs2MovementHandler.getInstance();
        this.combatHandler = net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.handlers.Rs2CombatHandler.getInstance();

        // register this script with event bus for GameTick events
        if (eventBus != null) {
            eventBus.register(this);
            isActive = true;
            log.info("Gauntlet script started - event-driven mode (v{})", version);
        } else {
            log.error("EventBus is null - cannot start script");
        }

        return true;
    }

    /**
     * GameTick event handler - triggers execution on separate thread
     * CRITICAL: Keeps this handler SHORT - immediately queues work and returns
     * Prevents blocking the client thread
     */
    @Subscribe
    public void onGameTick(GameTick event) {
        if (!isActive) return;

        // Queue execution on separate thread - don't block client thread!
        scheduledExecutorService.submit(() -> {
            try {
                // check if in boss fight
                if (!Rs2GauntletUtil.isBossFightActive()) {
                    if (fightActive) {
                        stopTracking();
                    }
                    return;
                }

                // start tracking when fight begins
                if (!fightActive) {
                    startTracking();
                }

                // execute main combat loop
                executeCombatLoop();

            } catch (Exception ex) {
                log.error("Gauntlet script error on tick {}: {}",
                    Microbot.getClient().getTickCount(), ex.getMessage(), ex);
            }
        });
    }

    /**
     * start framework tracking for Hunllef fight
     */
    private void startTracking() {
        currentHunllef = Rs2Npc.getNpcs()
            .filter(Rs2GauntletUtil::isHunllef)
            .findFirst()
            .orElse(null);

        if (currentHunllef == null) {
            log.warn("Could not find Hunllef NPC to start tracking");
            return;
        }

        // register Hunllef boss pattern (4 attacks, Range -> Mage rotation, starts with Range)
        List<Integer> excludedAnimations = Arrays.asList(Rs2GauntletUtil.BOSS_STOMP_ANIMATION);
        bossPatternTracker.registerHunllefPattern(currentHunllef, excludedAnimations);

        // register player attack tracking (6-hit weapon swap cycle)
        playerAttackTracker.startTrackingHunllef(currentHunllef);

        // scan inventory for available weapons and determine primary weapon
        scanAvailableWeapons();

        // reset anti-ban state for new fight
        mistakeCounter = 0;
        shouldMissPrayerSwitch = false;
        shouldUseWrongWeapon = false;
        lastEatTick = 0;
        lastWeaponSwitchTick = 0;
        stompDodgeInProgress = false;

        fightActive = true;
        log.info("Hunllef fight started - framework tracking active (NPC index: {})", currentHunllef.getIndex());
    }

    /**
     * scan inventory for available gauntlet weapons and determine primary weapon
     */
    private void scanAvailableWeapons() {
        meleeWeaponId = -1;
        magicWeaponId = -1;
        rangedWeaponId = -1;
        primaryWeaponId = -1;

        // scan inventory for weapons (prefer tier 3, then tier 2, then tier 1)
        for (int tier = 3; tier >= 1; tier--) {
            // melee weapons
            if (meleeWeaponId == -1) {
                int halberdId = getWeaponIdForTier(CombatStyle.MELEE, tier);
                if (halberdId > 0 && Rs2Inventory.hasItem(halberdId)) {
                    meleeWeaponId = halberdId;
                }
            }

            // magic weapons
            if (magicWeaponId == -1) {
                int staffId = getWeaponIdForTier(CombatStyle.MAGIC, tier);
                if (staffId > 0 && Rs2Inventory.hasItem(staffId)) {
                    magicWeaponId = staffId;
                }
            }

            // ranged weapons
            if (rangedWeaponId == -1) {
                int bowId = getWeaponIdForTier(CombatStyle.RANGED, tier);
                if (bowId > 0 && Rs2Inventory.hasItem(bowId)) {
                    rangedWeaponId = bowId;
                }
            }
        }

        // determine primary weapon (highest tier + highest combat stat)
        primaryWeaponId = determinePrimaryWeapon();

        // register weapon styles with handler
        if (meleeWeaponId > 0) {
            weaponSwitchHandler.registerWeaponStyle(meleeWeaponId, CombatStyle.MELEE);
        }
        if (magicWeaponId > 0) {
            weaponSwitchHandler.registerWeaponStyle(magicWeaponId, CombatStyle.MAGIC);
        }
        if (rangedWeaponId > 0) {
            weaponSwitchHandler.registerWeaponStyle(rangedWeaponId, CombatStyle.RANGED);
        }

        weaponsScanned = true;
        log.info("Weapons scanned - Melee: {}, Magic: {}, Ranged: {}, Primary: {}",
            meleeWeaponId, magicWeaponId, rangedWeaponId, primaryWeaponId);
    }

    /**
     * get weapon ID for specific style and tier
     */
    private int getWeaponIdForTier(CombatStyle style, int tier) {
        boolean corrupted = config.corruptedMode();

        if (style == CombatStyle.MELEE) {
            if (tier == 3) return corrupted ? Rs2GauntletUtil.CORRUPTED_HALBERD_T3 : Rs2GauntletUtil.CRYSTAL_HALBERD_T3;
            if (tier == 2) return corrupted ? Rs2GauntletUtil.CORRUPTED_HALBERD_T2 : Rs2GauntletUtil.CRYSTAL_HALBERD_T2;
            if (tier == 1) return corrupted ? Rs2GauntletUtil.CORRUPTED_HALBERD_T1 : Rs2GauntletUtil.CRYSTAL_HALBERD_T1;
        } else if (style == CombatStyle.MAGIC) {
            if (tier == 3) return corrupted ? Rs2GauntletUtil.CORRUPTED_STAFF_T3 : Rs2GauntletUtil.CRYSTAL_STAFF_T3;
            if (tier == 2) return corrupted ? Rs2GauntletUtil.CORRUPTED_STAFF_T2 : Rs2GauntletUtil.CRYSTAL_STAFF_T2;
            if (tier == 1) return corrupted ? Rs2GauntletUtil.CORRUPTED_STAFF_T1 : Rs2GauntletUtil.CRYSTAL_STAFF_T1;
        } else if (style == CombatStyle.RANGED) {
            if (tier == 3) return corrupted ? Rs2GauntletUtil.CORRUPTED_BOW_T3 : Rs2GauntletUtil.CRYSTAL_BOW_T3;
            if (tier == 2) return corrupted ? Rs2GauntletUtil.CORRUPTED_BOW_T2 : Rs2GauntletUtil.CRYSTAL_BOW_T2;
            if (tier == 1) return corrupted ? Rs2GauntletUtil.CORRUPTED_BOW_T1 : Rs2GauntletUtil.CRYSTAL_BOW_T1;
        }

        return -1;
    }

    /**
     * determine primary weapon based on tier and combat stats
     */
    private int determinePrimaryWeapon() {
        // find highest tier weapon
        int highestTier = 0;
        if (meleeWeaponId > 0) highestTier = Math.max(highestTier, Rs2GauntletUtil.getWeaponTier(meleeWeaponId));
        if (magicWeaponId > 0) highestTier = Math.max(highestTier, Rs2GauntletUtil.getWeaponTier(magicWeaponId));
        if (rangedWeaponId > 0) highestTier = Math.max(highestTier, Rs2GauntletUtil.getWeaponTier(rangedWeaponId));

        // if multiple tier 3 weapons, pick based on highest combat stat
        List<Integer> tier3Weapons = new java.util.ArrayList<>();
        if (meleeWeaponId > 0 && Rs2GauntletUtil.getWeaponTier(meleeWeaponId) == highestTier) {
            tier3Weapons.add(meleeWeaponId);
        }
        if (magicWeaponId > 0 && Rs2GauntletUtil.getWeaponTier(magicWeaponId) == highestTier) {
            tier3Weapons.add(magicWeaponId);
        }
        if (rangedWeaponId > 0 && Rs2GauntletUtil.getWeaponTier(rangedWeaponId) == highestTier) {
            tier3Weapons.add(rangedWeaponId);
        }

        if (tier3Weapons.isEmpty()) {
            return meleeWeaponId > 0 ? meleeWeaponId : (magicWeaponId > 0 ? magicWeaponId : rangedWeaponId);
        }

        // pick weapon with highest combat stat
        int bestWeapon = tier3Weapons.get(0);
        int bestScore = getCombatScore(bestWeapon);

        for (int weaponId : tier3Weapons) {
            int score = getCombatScore(weaponId);
            if (score > bestScore) {
                bestScore = score;
                bestWeapon = weaponId;
            }
        }

        return bestWeapon;
    }

    /**
     * get combat score for weapon (used to determine primary weapon)
     */
    private int getCombatScore(int weaponId) {
        if (Rs2GauntletUtil.isMeleeWeapon(weaponId)) {
            int attack = Rs2Player.getRealSkillLevel(Skill.ATTACK);
            int strength = Rs2Player.getRealSkillLevel(Skill.STRENGTH);
            return (attack + strength) / 2;
        } else if (Rs2GauntletUtil.isMagicWeapon(weaponId)) {
            return Rs2Player.getRealSkillLevel(Skill.MAGIC);
        } else if (Rs2GauntletUtil.isRangedWeapon(weaponId)) {
            return Rs2Player.getRealSkillLevel(Skill.RANGED);
        }
        return 0;
    }

    /**
     * stop framework tracking when fight ends
     */
    private void stopTracking() {
        if (currentHunllef != null) {
            bossPatternTracker.removePattern(currentHunllef.getIndex());
            playerAttackTracker.removeTracking(currentHunllef.getIndex());
            log.info("Hunllef fight ended - framework tracking stopped");
        }

        currentHunllef = null;
        fightActive = false;
        weaponsScanned = false;
    }

    /**
     * main combat execution loop
     */
    private void executeCombatLoop() {
        // refresh Hunllef reference (NPC ID changes with prayer)
        NPC hunllef = Rs2Npc.getNpcs()
            .filter(Rs2GauntletUtil::isHunllef)
            .findFirst()
            .orElse(null);

        if (hunllef == null || Rs2GauntletUtil.isHunllefDead(hunllef)) {
            if (hunllef != null && Rs2GauntletUtil.isHunllefDead(hunllef)) {
                handleHunllefDeath();
            }
            return;
        }

        // update current hunllef if NPC index changed (prayer switch)
        if (currentHunllef == null || currentHunllef.getIndex() != hunllef.getIndex()) {
            log.debug("Hunllef NPC reference updated (prayer changed)");
            currentHunllef = hunllef;
        }

        // calculate anti-ban mistakes for this cycle (if enabled)
        if (config.antiBanEnabled()) {
            calculateMistakes();
        }

        // monitor boss animations for attack pattern tracking (with null safety)
        if (animationTracker != null) {
            trackBossAttacks(hunllef);
        }

        // track tornado spawns and integrate into attack cycle
        trackTornadoAttacks(hunllef);

        // check for stomp attack and dodge if enabled
        if (config.dodgeStomps()) {
            handleStompDodge(hunllef);
        }

        // priority 0: detect prayer disable and immediately restore
        handlePrayerDisableRecovery(hunllef);

        // priority 1: automatic prayer switching (ONLY if prayers not disabled)
        if (!prayerHandler.isPrayerDisabled() && !shouldMissPrayerSwitch) {
            pvmCombat.handleAutoPrayer();

            // maintain offensive prayer alongside protection prayer
            handleOffensivePrayer();
        } else if (shouldMissPrayerSwitch) {
            log.debug("Anti-ban: intentionally missing prayer switch");
            shouldMissPrayerSwitch = false; // reset after one miss
        }

        // priority 2: automatic hazard dodging while staying in attack range (if enabled)
        if (config.dodgeHazards()) {
            pvmCombat.handleAutoDodgeInRange(hunllef.getWorldLocation(), 6);
        }

        // priority 3: eat if health is low
        handleEating();

        // priority 4: weapon switching when Hunllef changes defensive prayer
        handleWeaponSwitching(hunllef);

        // priority 5: attack hunllef
        handleAttacking(hunllef);
    }

    /**
     * calculate anti-ban mistakes based on configured error rate
     */
    private void calculateMistakes() {
        int errorRate = config.errorRate();
        if (errorRate <= 0) return;

        // every ~30 actions, check if should make a mistake
        mistakeCounter++;
        if (mistakeCounter >= 30) {
            mistakeCounter = 0;

            // random chance based on error rate (0-10%)
            int roll = ThreadLocalRandom.current().nextInt(100);
            if (roll < errorRate) {
                // decide which mistake to make
                int mistakeType = ThreadLocalRandom.current().nextInt(2);
                if (mistakeType == 0) {
                    shouldMissPrayerSwitch = true;
                    log.debug("Anti-ban: scheduled prayer switch miss");
                } else {
                    shouldUseWrongWeapon = true;
                    log.debug("Anti-ban: scheduled wrong weapon use");
                }
            }
        }
    }

    /**
     * track boss attacks for pattern cycle (every 4 attacks = player prayer switch)
     */
    private void trackBossAttacks(NPC hunllef) {
        // check if Hunllef just performed an attack animation
        animationTracker.getLastAttackAnimation(hunllef.getIndex()).ifPresent(animData -> {
            // exclude stomp animation from attack count
            int animId = animData.getAnimationId();

            // check if this is a recent attack (within 2 ticks)
            if (animData.getTicksAgo() <= 2) {
                // record attack for pattern tracking
                bossPatternTracker.recordAttack(hunllef.getIndex(), animId);

                // get recommended prayer for player
                Optional<Rs2PrayerEnum> nextPrayer = bossPatternTracker.getNextPrayer(hunllef.getIndex());
                nextPrayer.ifPresent(prayer -> {
                    // framework will automatically switch to this prayer via handleAutoPrayer()
                    log.debug("Next prayer for Hunllef attack: {}", prayer);
                });
            }
        });
    }

    /**
     * track tornado spawns and integrate into attack cycle
     * CRITICAL: Tornadoes count as one of the 4 attacks in Hunllef's cycle
     * Must record tornado spawn to keep prayer rotation in sync
     */
    private void trackTornadoAttacks(NPC hunllef) {
        // find all tornadoes near Hunllef
        java.util.List<NPC> tornadoes = Rs2Npc.getNpcs()
            .filter(Rs2GauntletUtil::isTornado)
            .collect(java.util.stream.Collectors.toList());

        int currentTick = Microbot.getClient().getTickCount();

        for (NPC tornado : tornadoes) {
            int tornadoIndex = tornado.getIndex();

            // if this is a new tornado we haven't tracked yet
            if (!trackedTornadoIndices.contains(tornadoIndex)) {
                // only count tornadoes that spawn within attack cycle timing
                // tornadoes spawn roughly every 5 ticks (same as Hunllef's attack speed)
                int ticksSinceLastTornado = currentTick - lastTornadoSpawnTick;

                if (ticksSinceLastTornado >= 4 || lastTornadoSpawnTick == 0) {
                    // record this tornado spawn as an attack in the cycle
                    bossPatternTracker.recordAttack(hunllef.getIndex(), -1); // use -1 for tornado "animation"

                    trackedTornadoIndices.add(tornadoIndex);
                    lastTornadoSpawnTick = currentTick;

                    log.debug("Tornado spawn detected, recorded as attack in cycle (tick: {})", currentTick);
                }
            }
        }

        // cleanup dead/despawned tornadoes from tracking
        trackedTornadoIndices.removeIf(index ->
            Rs2Npc.getNpcs().noneMatch(npc -> npc.getIndex() == index && Rs2GauntletUtil.isTornado(npc))
        );
    }

    /**
     * detect and handle stomp attack
     */
    private void handleStompDodge(NPC hunllef) {
        if (stompDodgeInProgress) return; // already dodging

        // check if Hunllef is performing stomp animation
        animationTracker.getLastAttackAnimation(hunllef.getIndex()).ifPresent(animData -> {
            if (animData.getAnimationId() == Rs2GauntletUtil.BOSS_STOMP_ANIMATION && animData.getTicksAgo() <= 1) {
                stompDodgeInProgress = true;
                log.info("Stomp detected - moving away from Hunllef");

                pvmCombat.queueAction(new Rs2PvMCombat.PvMAction() {
                    @Override
                    public boolean execute() {
                        // use Rs2Walker to move away from Hunllef location
                        // find a safe tile at least 2 tiles away that maintains attack range
                        Optional<WorldPoint> safeTile = movementHandler.getNearestSafeTileInRange(hunllef.getWorldLocation(), 6);

                        if (safeTile.isPresent()) {
                            WorldPoint destination = safeTile.get();
                            // ensure destination is at least 2 tiles from Hunllef
                            if (destination.distanceTo(hunllef.getWorldLocation()) >= 2) {
                                net.runelite.client.plugins.microbot.util.walker.Rs2Walker.walkTo(destination);
                                stompDodgeInProgress = false;

                                // Queue attack after movement completes (1 tick for movement to finish)
                                queueAttackHunllef(1);
                                return true;
                            }
                        }

                        // fallback: just dodge to any safe tile
                        boolean moved = movementHandler.dodgeToSafeTile();
                        stompDodgeInProgress = false;

                        // Queue attack after dodge movement (1 tick delay)
                        if (moved) {
                            queueAttackHunllef(1);
                        }
                        return moved;
                    }

                    @Override
                    public int getPriority() {
                        return Rs2PvMCombat.Priority.DODGE;
                    }

                    @Override
                    public String getDescription() {
                        return "Dodge stomp attack";
                    }

                    @Override
                    public boolean requiresNoTickLoss() {
                        return false; // emergency movement
                    }
                });
            }
        });
    }

    /**
     * handle Hunllef death - cleanup and statistics
     */
    private void handleHunllefDeath() {
        if (fightActive) {
            log.info("Hunllef defeated!");
            stopTracking();
        }
    }

    /**
     * handle food consumption with smart timing (only during dodge phases or emergencies)
     * CRITICAL: Eating adds 3-tick delay to attack cooldown (prevents panic eating DPS loss)
     */
    private void handleEating() {
        int currentHp = Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS);
        int maxHp = Rs2Player.getRealSkillLevel(Skill.HITPOINTS);
        int currentTick = Microbot.getClient().getTickCount();

        // check eat cooldown (3 ticks for normal food, 2 for fast food)
        boolean onCooldown = (currentTick - lastEatTick) < 3;
        if (onCooldown) return;

        // get configured thresholds with anti-ban variance
        double emergencyThreshold = config.emergencyEatingThreshold() / 100.0;
        double normalThreshold = config.eatingThreshold() / 100.0;

        // add variance (±5%) if anti-ban enabled
        if (config.antiBanEnabled() && config.timingVariance()) {
            double variance = (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.1; // ±5%
            emergencyThreshold += variance;
            normalThreshold += variance;
        }

        // find available food (validates food exists in inventory)
        String foodName = findAvailableFood();
        if (foodName == null) {
            log.debug("No food available in inventory - cannot eat");
            return; // no food available
        }

        // determine food delay (2 ticks for crystal/corrupted food, 3 for normal)
        int foodDelay = isFastFood(foodName) ? 2 : 3;

        // check if in dodge phase (tornado dodging or stomp)
        // Player is in danger if currently on hazard or will be in danger within next 2 ticks
        boolean inDodgePhase = stompDodgeInProgress ||
            movementHandler.isPlayerOnHazard() ||
            movementHandler.willPlayerBeInDanger(2);

        // emergency eat (life-threatening, eat immediately regardless of phase)
        boolean isEmergency = currentHp < maxHp * emergencyThreshold;

        // normal eat (only during dodge phase to minimize DPS loss)
        boolean shouldNormalEat = currentHp < maxHp * normalThreshold && inDodgePhase;

        if (!isEmergency && !shouldNormalEat) {
            return; // don't eat - not emergency and not in dodge phase
        }

        // add reaction delay if configured
        int reactionDelay = 0;
        if (config.antiBanEnabled() && config.reactionDelay()) {
            reactionDelay = getReactionDelay();
        }

        final String food = foodName;
        final int delay = reactionDelay;
        final int tickDelay = foodDelay;

        if (isEmergency) {
            // emergency eat (higher priority, immediate execution)
            pvmCombat.queueAction(new Rs2PvMCombat.PvMAction() {
                @Override
                public boolean execute() {
                    if (delay > 0) {
                        sleep(delay);
                    }

                    // validate food still exists before eating
                    if (!Rs2Inventory.hasItem(food)) {
                        log.warn("Food disappeared before eating: {}", food);
                        return false;
                    }

                    boolean success = Rs2Inventory.interact(food, "Eat");
                    if (success) {
                        int eatTick = Microbot.getClient().getTickCount();
                        lastEatTick = eatTick;
                        log.info("Emergency ate {} at tick {} (HP: {}/{}), next eat available at tick {}",
                            food, eatTick, currentHp, maxHp, eatTick + tickDelay);

                        // Queue attack for after food delay (eating clears attack queue)
                        // Use tickDelay to account for eating animation (3 ticks normal, 2 ticks fast food)
                        queueAttackHunllef(tickDelay);
                    }
                    return success;
                }

                @Override
                public int getPriority() {
                    return Rs2PvMCombat.Priority.DODGE; // high priority for emergency
                }

                @Override
                public String getDescription() {
                    return "Emergency eat " + food + " (HP: " + currentHp + "/" + maxHp + ")";
                }

                @Override
                public boolean requiresNoTickLoss() {
                    return false; // emergency eating doesn't wait
                }
            });
        } else if (shouldNormalEat) {
            // normal eat during dodge phase (wait for safe tick)
            pvmCombat.queueActionRelative(new Rs2PvMCombat.PvMAction() {
                @Override
                public boolean execute() {
                    if (delay > 0) {
                        sleep(delay);
                    }

                    // validate food still exists before eating
                    if (!Rs2Inventory.hasItem(food)) {
                        log.warn("Food disappeared before eating: {}", food);
                        return false;
                    }

                    boolean success = Rs2Inventory.interact(food, "Eat");
                    if (success) {
                        int eatTick = Microbot.getClient().getTickCount();
                        lastEatTick = eatTick;
                        log.debug("Ate {} during dodge phase at tick {} (HP: {}/{}), next eat at tick {}",
                            food, eatTick, currentHp, maxHp, eatTick + tickDelay);

                        // Queue attack for after food delay (eating clears attack queue)
                        queueAttackHunllef(tickDelay);
                    }
                    return success;
                }

                @Override
                public int getPriority() {
                    return Rs2PvMCombat.Priority.CONSUME;
                }

                @Override
                public String getDescription() {
                    return "Eat " + food + " during dodge (HP: " + currentHp + "/" + maxHp + ")";
                }

                @Override
                public boolean requiresNoTickLoss() {
                    return true; // wait for safe tick
                }
            }, 1); // queue for next tick
        }
    }

    /**
     * check if food is fast food (2-tick delay instead of 3)
     */
    private boolean isFastFood(String foodName) {
        // crystal and corrupted food have 2-tick delay
        return foodName != null && (
            foodName.toLowerCase().contains("crystal") ||
            foodName.toLowerCase().contains("corrupted")
        );
    }

    /**
     * find available food based on config or auto-detect
     */
    private String findAvailableFood() {
        MicroGauntletConfig.FoodType configuredType = config.foodType();

        if (configuredType == MicroGauntletConfig.FoodType.AUTO_DETECT) {
            // auto-detect: check all food types in priority order
            if (Rs2Inventory.hasItem("Cooked paddlefish")) {
                return "Cooked paddlefish";
            } else if (Rs2Inventory.hasItem(CRYSTAL_FOOD)) {
                // get the item to retrieve its name
                var item = Rs2Inventory.get(i -> i.getId() == CRYSTAL_FOOD);
                return item != null ? item.getName() : null;
            } else if (Rs2Inventory.hasItem(CORRUPTED_FOOD)) {
                var item = Rs2Inventory.get(i -> i.getId() == CORRUPTED_FOOD);
                return item != null ? item.getName() : null;
            }
        } else if (configuredType == MicroGauntletConfig.FoodType.COOKED_PADDLEFISH) {
            if (Rs2Inventory.hasItem("Cooked paddlefish")) {
                return "Cooked paddlefish";
            }
        } else if (configuredType == MicroGauntletConfig.FoodType.CRYSTAL_FOOD) {
            if (Rs2Inventory.hasItem(CRYSTAL_FOOD)) {
                var item = Rs2Inventory.get(i -> i.getId() == CRYSTAL_FOOD);
                return item != null ? item.getName() : null;
            }
        } else if (configuredType == MicroGauntletConfig.FoodType.CORRUPTED_FOOD) {
            if (Rs2Inventory.hasItem(CORRUPTED_FOOD)) {
                var item = Rs2Inventory.get(i -> i.getId() == CORRUPTED_FOOD);
                return item != null ? item.getName() : null;
            }
        }

        return null; // no food found
    }

    /**
     * get random reaction delay for anti-ban (50-250ms)
     */
    private int getReactionDelay() {
        return ThreadLocalRandom.current().nextInt(50, 250);
    }

    /**
     * handle 5:1 weapon switching based on player hit count and Hunllef's defensive prayer
     * 5:1 pattern: 5 hits with primary weapon, 1 hit with secondary weapon (counters Hunllef's prayer), switch back
     */
    private void handleWeaponSwitching(NPC hunllef) {
        if (!weaponsScanned || primaryWeaponId < 0) {
            return; // weapons not scanned yet
        }

        // get current hit count in cycle (0-5, resets at 6)
        int hitCount = playerAttackTracker.getHitCount(hunllef.getIndex());
        int cyclePosition = playerAttackTracker.getCyclePosition(hunllef.getIndex()); // 1-6

        // get Hunllef's current defensive prayer
        HeadIcon hunllefPrayer = overheadPrayerHandler.getOverheadPrayer(hunllef);
        if (hunllefPrayer == null) {
            return; // no prayer detected yet
        }

        // check tick cooldown (minimum 1 tick between weapon switches)
        int currentTick = Microbot.getClient().getTickCount();
        if ((currentTick - lastWeaponSwitchTick) < 1) {
            return;
        }

        // get current equipped weapon
        Optional<Integer> currentWeaponOpt = weaponSwitchHandler.getCurrentWeapon();
        if (!currentWeaponOpt.isPresent()) {
            return;
        }
        int currentWeaponId = currentWeaponOpt.get();
        CombatStyle currentStyle = weaponSwitchHandler.getCurrentCombatStyle();

        // check if current weapon style is protected by Hunllef's prayer (detect mistakes)
        boolean currentStyleProtected = false;
        if (currentStyle != null && hunllefPrayer != null) {
            currentStyleProtected = (hunllefPrayer == HeadIcon.MELEE && currentStyle == CombatStyle.MELEE) ||
                                   (hunllefPrayer == HeadIcon.RANGED && currentStyle == CombatStyle.RANGED) ||
                                   (hunllefPrayer == HeadIcon.MAGIC && currentStyle == CombatStyle.MAGIC);
        }

        // 5:1 logic CORRECTED:
        // - Hits 1-5: Execute with primary weapon
        // - After 5th hit lands: Switch to secondary weapon (counters Hunllef's prayer)
        // - Hit 6: Execute with secondary weapon (prevents Hunllef prayer change)
        // - After hit 6 lands: Switch back to primary weapon
        // - Exception: If current weapon style is protected, switch immediately to correct weapon

        // CASE 1: Detect mistake - using protected style (get back in cycle)
        if (currentStyleProtected) {
            log.warn("Detected weapon mistake: using {} against {} prayer - correcting immediately",
                currentStyle, hunllefPrayer);

            // switch to weapon that counters Hunllef's prayer
            int targetWeaponId = getWeaponCounterTo(hunllefPrayer);
            if (targetWeaponId > 0 && targetWeaponId != currentWeaponId) {
                switchWeapon(targetWeaponId, "Correction switch");
            }
            return;
        }

        // CASE 2: After 5th hit lands (cyclePosition now 6, but haven't attacked yet) - switch to secondary
        if (cyclePosition == 6) {
            // 5th hit just landed, now switch to secondary for 6th attack
            int secondaryWeaponId = getWeaponCounterTo(hunllefPrayer);

            if (secondaryWeaponId > 0 && secondaryWeaponId != currentWeaponId) {
                switchWeapon(secondaryWeaponId, "5:1 secondary weapon (for hit 6)");
            }
            return;
        }

        // CASE 3: After 6th hit lands (cyclePosition now 1) - switch back to primary weapon
        if (cyclePosition == 1 && hitCount >= 6) {
            // 6th hit just landed (new cycle), switch back to primary for next 5 hits
            if (currentWeaponId != primaryWeaponId) {
                switchWeapon(primaryWeaponId, "5:1 back to primary");
            }
            return;
        }

        // CASE 4: During hits 1-5 - ensure using primary weapon
        if (cyclePosition >= 2 && cyclePosition <= 5) {
            if (currentWeaponId != primaryWeaponId) {
                // not using primary weapon during primary phase, switch back
                switchWeapon(primaryWeaponId, "5:1 maintaining primary");
            }
        }

        // CASE 5: First hit of fight (hitCount == 0, cyclePosition == 1) - use primary
        if (cyclePosition == 1 && hitCount == 0) {
            if (currentWeaponId != primaryWeaponId) {
                switchWeapon(primaryWeaponId, "5:1 initial primary");
            }
        }
    }

    /**
     * get weapon ID that counters Hunllef's defensive prayer
     */
    private int getWeaponCounterTo(HeadIcon hunllefPrayer) {
        if (hunllefPrayer == HeadIcon.MELEE) {
            // Hunllef protects melee, use ranged or magic
            // prefer whichever has higher tier, if tied prefer ranged
            if (rangedWeaponId > 0 && magicWeaponId > 0) {
                int rangedTier = Rs2GauntletUtil.getWeaponTier(rangedWeaponId);
                int magicTier = Rs2GauntletUtil.getWeaponTier(magicWeaponId);
                return rangedTier >= magicTier ? rangedWeaponId : magicWeaponId;
            }
            return rangedWeaponId > 0 ? rangedWeaponId : magicWeaponId;

        } else if (hunllefPrayer == HeadIcon.RANGED) {
            // Hunllef protects ranged, use magic
            return magicWeaponId > 0 ? magicWeaponId : meleeWeaponId;

        } else if (hunllefPrayer == HeadIcon.MAGIC) {
            // Hunllef protects magic, use ranged
            return rangedWeaponId > 0 ? rangedWeaponId : meleeWeaponId;
        }

        return -1;
    }

    /**
     * detect prayer disable attack and immediately restore prayers
     * CRITICAL: Hunllef has one magic attack per cycle that disables ALL prayers
     * Must restore protection + offensive prayers immediately
     */
    private void handlePrayerDisableRecovery(NPC hunllef) {
        // check if prayers were just disabled
        if (prayerHandler.isPrayerDisabled()) {
            log.warn("Prayer disable detected! Restoring prayers immediately");

            // immediately restore protection prayer based on next expected attack
            Optional<Rs2PrayerEnum> nextPrayer = bossPatternTracker.getNextPrayer(hunllef.getIndex());
            if (nextPrayer.isPresent()) {
                prayerHandler.toggle(nextPrayer.get(), true);
                log.info("Restored protection prayer: {}", nextPrayer.get());
            }

            // restore offensive prayer based on current weapon
            handleOffensivePrayer();

            return;
        }
    }

    /**
     * maintain offensive prayer (Piety/Rigour/Augury) based on current weapon style
     * CRITICAL: Provides 15-20% DPS increase, should be active throughout fight
     */
    private void handleOffensivePrayer() {
        // get current weapon style
        CombatStyle currentStyle = weaponSwitchHandler.getCurrentCombatStyle();
        if (currentStyle == null) {
            return; // no weapon equipped
        }

        // determine which offensive prayer to use
        Rs2PrayerEnum offensivePrayer = null;
        if (currentStyle == CombatStyle.MELEE) {
            offensivePrayer = Rs2PrayerEnum.PIETY;
        } else if (currentStyle == CombatStyle.RANGED) {
            offensivePrayer = Rs2PrayerEnum.RIGOUR;
        } else if (currentStyle == CombatStyle.MAGIC) {
            offensivePrayer = Rs2PrayerEnum.AUGURY;
        }

        // activate offensive prayer if not already active
        if (offensivePrayer != null && !prayerHandler.isPrayerActive(offensivePrayer)) {
            prayerHandler.toggle(offensivePrayer, true);
            log.debug("Activated offensive prayer: {}", offensivePrayer);
        }
    }

    /**
     * switch to weapon by ID
     */
    private void switchWeapon(int targetWeaponId, String reason) {
        // anti-ban: sometimes intentionally skip weapon switch
        if (shouldUseWrongWeapon) {
            log.debug("Anti-ban: intentionally NOT switching weapon ({})", reason);
            shouldUseWrongWeapon = false;
            return;
        }

        // add reaction delay if anti-ban enabled
        int reactionDelay = (config.antiBanEnabled() && config.reactionDelay()) ? getReactionDelay() : 0;

        final int weaponId = targetWeaponId;
        final String switchReason = reason;

        pvmCombat.queueAction(new Rs2PvMCombat.PvMAction() {
            @Override
            public boolean execute() {
                if (reactionDelay > 0) {
                    sleep(reactionDelay);
                }

                boolean success = weaponSwitchHandler.switchToWeapon(weaponId);
                if (success) {
                    int currentTick = Microbot.getClient().getTickCount();
                    lastWeaponSwitchTick = currentTick;
                    log.info("Switched weapon: {} (weapon ID: {})", switchReason, weaponId);
                } else {
                    log.warn("Failed to switch weapon: {} (weapon ID: {})", switchReason, weaponId);
                }
                return success;
            }

            @Override
            public int getPriority() {
                return Rs2PvMCombat.Priority.WEAPON_SWITCH;
            }

            @Override
            public String getDescription() {
                return "Switch weapon: " + switchReason;
            }

            @Override
            public boolean requiresNoTickLoss() {
                return true; // tick-perfect weapon switching
            }
        });
    }

    /**
     * handle attacking Hunllef with attack style validation
     * CRITICAL: Uses weapon cooldown tracking instead of inCombat check
     * Re-clicks attack every weapon speed interval for consistent DPS
     */
    private void handleAttacking(NPC hunllef) {
        // Check if weapon is ready to attack (not on cooldown)
        if (!combatHandler.canAttackThisTick()) {
            return; // weapon still on cooldown
        }

        // Validate attack style before attacking
        CombatStyle currentStyle = weaponSwitchHandler.getCurrentCombatStyle();
        HeadIcon hunllefPrayer = overheadPrayerHandler.getOverheadPrayer(hunllef);

        // Don't attack if using a style that Hunllef is protecting from
        if (currentStyle != null && hunllefPrayer != null) {
            if ((hunllefPrayer == HeadIcon.MELEE && currentStyle == CombatStyle.MELEE) ||
                (hunllefPrayer == HeadIcon.RANGED && currentStyle == CombatStyle.RANGED) ||
                (hunllefPrayer == HeadIcon.MAGIC && currentStyle == CombatStyle.MAGIC)) {
                log.debug("Not attacking - current style {} is protected by Hunllef prayer {}", currentStyle, hunllefPrayer);
                return; // wait for weapon switch
            }
        }

        // Queue immediate attack (0 tick delay)
        queueAttackHunllef(0);
    }

    /**
     * Queue attack on Hunllef after a delay (used after eating/dodging)
     * @param tickDelay number of ticks to wait before attacking
     */
    private void queueAttackHunllef(int tickDelay) {
        if (currentHunllef == null) {
            log.warn("Cannot queue attack - Hunllef reference is null");
            return;
        }

        final NPC target = currentHunllef;

        pvmCombat.queueActionRelative(new Rs2PvMCombat.PvMAction() {
            @Override
            public boolean execute() {
                // Validate target still exists and is attackable
                if (target == null || target.isDead() || !Rs2GauntletUtil.isHunllef(target)) {
                    log.warn("Cannot attack - target invalid");
                    return false;
                }

                // Validate we're using correct attack style
                CombatStyle currentStyle = weaponSwitchHandler.getCurrentCombatStyle();
                HeadIcon hunllefPrayer = overheadPrayerHandler.getOverheadPrayer(target);

                if (currentStyle != null && hunllefPrayer != null) {
                    if ((hunllefPrayer == HeadIcon.MELEE && currentStyle == CombatStyle.MELEE) ||
                        (hunllefPrayer == HeadIcon.RANGED && currentStyle == CombatStyle.RANGED) ||
                        (hunllefPrayer == HeadIcon.MAGIC && currentStyle == CombatStyle.MAGIC)) {
                        log.debug("Skipping queued attack - wrong style {} vs prayer {}", currentStyle, hunllefPrayer);
                        return false; // wrong style, don't attack
                    }
                }

                Rs2Npc.attack(new Rs2NpcModel(target));
                log.debug("Re-clicked attack after delay of {} ticks", tickDelay);
                return true;
            }

            @Override
            public int getPriority() {
                return Rs2PvMCombat.Priority.ATTACK;
            }

            @Override
            public String getDescription() {
                return "Re-attack Hunllef after " + tickDelay + " tick delay";
            }

            @Override
            public boolean requiresNoTickLoss() {
                return true; // wait for weapon to be off cooldown
            }
        }, tickDelay);

        log.debug("Queued attack for +{} ticks from now", tickDelay);
    }

    @Override
    public void shutdown() {
        // unregister from event bus
        if (eventBus != null && isActive) {
            eventBus.unregister(this);
            isActive = false;
            log.info("Gauntlet script unregistered from event bus");
        }

        stopTracking();
        super.shutdown();
    }
}
