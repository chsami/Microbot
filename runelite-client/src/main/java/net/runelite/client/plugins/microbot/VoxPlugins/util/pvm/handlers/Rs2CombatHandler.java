package net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.handlers;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPC;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.enums.CombatStyle;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.enums.WeaponStyle;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.model.AttackState;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.model.BossAttackPattern;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.model.TickLossState;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * thread-safe handler for combat timing and tick loss detection
 * coordinates with consumable and weapon switch handlers
 * ensures attacks only happen when no tick loss will occur
 * supports boss attack pattern tracking (4:1, 5:1, custom ratios)
 */
@Slf4j
@Singleton
public class Rs2CombatHandler {

    private static Rs2CombatHandler instance;

    @Inject
    private Rs2ConsumableHandler consumableHandler;

    @Inject
    private Rs2WeaponSwitchHandler weaponSwitchHandler;

    // attack state tracking
    private volatile AttackState attackState = AttackState.NOT_ATTACKING;
    private volatile int lastAttackTick = -1;
    private volatile int weaponAttackSpeed = 4; // default: most weapons are 4 ticks

    // weapon tracking with enum-based detection
    private volatile int lastEquippedWeaponId = -1;
    private volatile WeaponStyle lastWeaponStyle = WeaponStyle.UNKNOWN;
    private volatile CombatStyle lastCombatStyle = null;
    private volatile int lastAttackRange = 1; // melee default

    // boss attack pattern tracking (key: npcIndex)
    private final Map<Integer, BossAttackPattern> bossPatterns = new ConcurrentHashMap<>();

    private Rs2CombatHandler() {
    }

    public static synchronized Rs2CombatHandler getInstance() {
        if (instance == null) {
            instance = new Rs2CombatHandler();
        }
        return instance;
    }

    /**
     * get current tick loss state
     * checks all sources of tick loss (eating, weapon switching, movement)
     */
    public TickLossState getTickLossState() {
        // check if just ate/drank (highest priority)
        if (consumableHandler != null && consumableHandler.isLosingTickFromConsumable()) {
            return TickLossState.LOSING;
        }

        // check if weapon switching (1-tick delay)
        if (weaponSwitchHandler != null && weaponSwitchHandler.isSwitchingWeapon()) {
            return TickLossState.POTENTIAL;
        }

        // check if player is moving
        if (Rs2Player.isMoving()) {
            return TickLossState.POTENTIAL;
        }

        // check if player is animating (excluding combat animations)
        if (Rs2Player.isAnimating()) {
            int animationId = Microbot.getClient().getLocalPlayer().getAnimation();
            // combat animations are typically 400-500 range, allow those
            if (animationId != -1 && (animationId < 390 || animationId > 530)) {
                return TickLossState.POTENTIAL;
            }
        }

        return TickLossState.NONE;
    }

    /**
     * check if player can attack this tick
     */
    public boolean canAttackThisTick() {
        TickLossState state = getTickLossState();
        if (state != TickLossState.NONE) {
            return false;
        }

        // check if weapon is on cooldown
        if (isWeaponOnCooldown()) {
            return false;
        }

        return true;
    }

    /**
     * check if weapon is on cooldown
     */
    public boolean isWeaponOnCooldown() {
        if (lastAttackTick < 0) {
            return false;
        }

        int currentTick = Microbot.getClient().getTickCount();
        int ticksSinceAttack = currentTick - lastAttackTick;

        return ticksSinceAttack < weaponAttackSpeed;
    }

    /**
     * get ticks until next attack available
     */
    public int getTicksUntilNextAttack() {
        if (lastAttackTick < 0) {
            return 0;
        }

        int currentTick = Microbot.getClient().getTickCount();
        int ticksSinceAttack = currentTick - lastAttackTick;
        int remaining = weaponAttackSpeed - ticksSinceAttack;

        return Math.max(0, remaining);
    }

    /**
     * coordinated attack that checks tick loss state
     * only attacks if no tick loss will occur
     */
    public boolean coordinatedAttack(NPC target) {
        // validate target
        if (target == null || target.isDead()) {
            log.debug("Cannot attack: target is null or dead");
            return false;
        }

        // check tick loss state
        TickLossState tickLossState = getTickLossState(); // we can not attack when we have for example eaten -> attack delays  for certain actions
        if (tickLossState != TickLossState.NONE) {
            log.debug("Cannot attack: tick loss state = {}", tickLossState);
            return false;
        }

        // check weapon cooldown
        if (isWeaponOnCooldown()) {
            log.debug("Cannot attack: weapon on cooldown ({} ticks remaining)",
                getTicksUntilNextAttack());
            return false;
        }
        Rs2NpcModel targetNpc = new Rs2NpcModel(target);
        // perform attack
        boolean success = Rs2Npc.attack(targetNpc);
        if (success) {
            lastAttackTick = Microbot.getClient().getTickCount();
            log.debug("Attacked target: {}, next attack in {} ticks",
                target.getName(), weaponAttackSpeed);
        }

        return success;
    }

    /**
     * detect and update weapon attack speed based on equipped weapon
     * automatically called when needed, no manual setting required
     * uses direct varbit detection (no external API dependencies)
     */
    private void updateWeaponAttackSpeed() {
        try {
            // get equipped weapon from equipment slot
            Rs2ItemModel weaponSlot =
                Rs2Equipment.get(net.runelite.api.EquipmentInventorySlot.WEAPON);

            int currentWeaponId = (weaponSlot != null) ? weaponSlot.getId() : -1;

            // get weapon style from varbit 43 (attack style selector)
            int attackStyleVarbit = Microbot.getVarbitValue(43); // Varbit 43 = attack style (0-6)
            WeaponStyle currentWeaponStyle = WeaponStyle.fromVarbitValue(attackStyleVarbit);

            // check if weapon or style changed
            boolean weaponChanged = (currentWeaponId != lastEquippedWeaponId);
            boolean styleChanged = (currentWeaponStyle != lastWeaponStyle);

            // only recalculate if something changed
            if (!weaponChanged && !styleChanged && lastEquippedWeaponId != -1) {
                return; // no change, use cached values
            }

            if (weaponSlot == null) {
                // no weapon equipped (unarmed)
                this.weaponAttackSpeed = 4; // unarmed attack speed is 4 ticks
                this.lastEquippedWeaponId = -1;
                this.lastWeaponStyle = WeaponStyle.UNKNOWN;
                this.lastCombatStyle = CombatStyle.MELEE; // unarmed is melee
                this.lastAttackRange = 1; // melee range

                if (weaponChanged) {
                    log.info("Weapon removed (unarmed): speed=4 ticks, range=1");
                }
                return;
            }

            int weaponId = weaponSlot.getId();

            // get weapon type varbit (determines base weapon category)
            int weaponTypeVarbit = Microbot.getVarbitValue(VarbitID.COMBAT_WEAPON_CATEGORY);

            // calculate speed, range, and combat style
            int speed = calculateWeaponSpeed(weaponTypeVarbit, currentWeaponStyle);
            int range = calculateAttackRange(weaponTypeVarbit, currentWeaponStyle);
            CombatStyle combatStyle = determineCombatStyle(weaponTypeVarbit);

            // update cached values
            this.weaponAttackSpeed = speed;
            this.lastEquippedWeaponId = weaponId;
            this.lastWeaponStyle = currentWeaponStyle;
            this.lastCombatStyle = combatStyle;
            this.lastAttackRange = range;

            if (weaponChanged) {
                log.info("Weapon changed: ID={}, type={}, style={}, speed={} ticks, range={}",
                    weaponId, weaponTypeVarbit, currentWeaponStyle.getDisplayName(), speed, range);
            } else if (styleChanged) {
                log.info("Attack style changed: {}, speed={} ticks, range={}",
                    currentWeaponStyle.getDisplayName(), speed, range);
            }

        } catch (Exception ex) {
            log.error("Failed to detect weapon attack speed: {}", ex.getMessage());
            this.weaponAttackSpeed = 4; // fallback to default
        }
    }
    
    /**
     * calculate weapon attack speed based on weapon type and attack style
     * uses game data to determine accurate speeds
     * reference: https://oldschool.runescape.wiki/w/Attack_speed
     */
    private int calculateWeaponSpeed(int weaponTypeVarbit, WeaponStyle weaponStyle) {
        // weapon type speeds (in ticks)
        switch (weaponTypeVarbit) {
            case 0: // unarmed
                return 4;
            case 1: // axe
                return 5;
            case 2: // hammer/mace
                return 5;
            case 3: // sword (scimitar, longsword, etc)
                return weaponStyle == WeaponStyle.AGGRESSIVE ? 4 : 5;
            case 4: // scimitar
                return 4;
            case 5: // two-handed sword
                return 6;
            case 6: // pickaxe
                return 5;
            case 7: // claws
                return 4;
            case 8: // halberd
                return 7;
            case 9: // spear
                return 5;
            case 10: // 2h weapon (godswords, etc)
                return 6;
            case 11: // bow
                return 5;
            case 12: // crossbow
                return 6; // most crossbows
            case 13: // staff
                return 5; // standard staves
            case 14: // whip
                return 4;
            case 15: // thrownaxe
                return 5;
            case 16: // dart
                return 3;
            case 17: // knife
                return 3;
            case 18: // chinchompa
                return 5;
            case 19: // ballista
                return 6;
            case 20: // blowpipe
                return 2; // blowpipe is 2 ticks
            case 21: // salamander
                return 5;
            case 22: // partisan/ancient mace
                return 5;
            case 23: // bulwark
                return 7;
            case 24: // banner/flags
                return 4;
            case 25: // powered staff
                return 4; // trident, sang, etc
            case 26: // shortbow
                return 4;
            case 27: // comp bow
                return 5;
            default:
                log.warn("Unknown weapon type: {}, defaulting to 4 ticks", weaponTypeVarbit);
                return 4;
        }
    }

    /**
     * calculate attack range based on weapon type and attack style
     * reference: https://oldschool.runescape.wiki/w/Attack_range
     */
    private int calculateAttackRange(int weaponTypeVarbit, WeaponStyle weaponStyle) {
        switch (weaponTypeVarbit) {
            case 0: // unarmed
                return 1;
            case 1: // axe
            case 2: // hammer/mace
            case 3: // sword
            case 4: // scimitar
            case 6: // pickaxe
            case 7: // claws
            case 14: // whip
                return 1; // melee range
            case 5: // two-handed sword
            case 10: // 2h weapon (godswords, etc)
            case 22: // partisan/ancient mace
                return 1; // melee range
            case 8: // halberd
                return 2; // halberd has 2 range
            case 9: // spear
                return weaponStyle == WeaponStyle.CONTROLLED ? 2 : 1;
            case 11: // bow
            case 26: // shortbow
                return weaponStyle == WeaponStyle.LONGRANGE ? 10 : 7; // shortbow
            case 27: // comp bow
                return weaponStyle == WeaponStyle.LONGRANGE ? 10 : 9; // longbow
            case 12: // crossbow
                return weaponStyle == WeaponStyle.LONGRANGE ? 9 : 7; // crossbow
            case 13: // staff
            case 25: // powered staff
                return weaponStyle == WeaponStyle.LONGRANGE ? 10 : 7; // magic
            case 15: // thrownaxe
            case 16: // dart
            case 17: // knife
                return weaponStyle == WeaponStyle.LONGRANGE ? 5 : 4; // thrown
            case 18: // chinchompa
                return weaponStyle == WeaponStyle.LONGRANGE ? 10 : 9; // chinchompa
            case 19: // ballista
                return weaponStyle == WeaponStyle.LONGRANGE ? 10 : 9; // ballista
            case 20: // blowpipe
                return weaponStyle == WeaponStyle.LONGRANGE ? 7 : 5; // blowpipe
            case 21: // salamander
                return 8; // salamander
            case 23: // bulwark
                return 1; // melee
            case 24: // banner/flags
                return 1; // melee
            default:
                log.warn("Unknown weapon type for range: {}, defaulting to 1", weaponTypeVarbit);
                return 1;
        }
    }

    /**
     * determine combat style (melee/ranged/magic) from weapon type
     */
    private CombatStyle determineCombatStyle(int weaponTypeVarbit) {
        switch (weaponTypeVarbit) {
            case 0: // unarmed
            case 1: // axe
            case 2: // hammer/mace
            case 3: // sword
            case 4: // scimitar
            case 5: // two-handed sword
            case 6: // pickaxe
            case 7: // claws
            case 8: // halberd
            case 9: // spear
            case 10: // 2h weapon
            case 14: // whip
            case 22: // partisan
            case 23: // bulwark
            case 24: // banner
                return CombatStyle.MELEE;

            case 11: // bow
            case 12: // crossbow
            case 15: // thrownaxe
            case 16: // dart
            case 17: // knife
            case 18: // chinchompa
            case 19: // ballista
            case 20: // blowpipe
            case 26: // shortbow
            case 27: // comp bow
                return CombatStyle.RANGED;

            case 13: // staff
            case 21: // salamander
            case 25: // powered staff
                return CombatStyle.MAGIC;

            default:
                log.warn("Unknown weapon type for combat style: {}, defaulting to MELEE", weaponTypeVarbit);
                return CombatStyle.MELEE;
        }
    }
    
    /**
     * get current weapon attack speed
     * automatically detects and updates if needed
     */
    public int getWeaponAttackSpeed() {
        // update speed before returning to ensure accuracy
        updateWeaponAttackSpeed();
        return weaponAttackSpeed;
    }

    /**
     * reset attack cooldown (for manual attacks outside handler)
     */
    public void resetAttackCooldown() {
        lastAttackTick = Microbot.getClient().getTickCount();
        log.debug("Reset attack cooldown");
    }

    /**
     * get ticks since last attack
     */
    public int getTicksSinceLastAttack() {
        if (lastAttackTick < 0) {
            return Integer.MAX_VALUE;
        }

        int currentTick = Microbot.getClient().getTickCount();
        return currentTick - lastAttackTick;
    }

    /**
     * check if can perform non-attack action without losing combat tick
     * useful for determining if can eat, drink, or switch gear
     */
    public boolean canPerformNonAttackAction() {
        // check if weapon is off cooldown (safe to perform action)
        if (isWeaponOnCooldown()) {
            int ticksRemaining = getTicksUntilNextAttack();
            // safe if 2+ ticks remaining before next attack
            return ticksRemaining >= 2;
        }

        // weapon ready - not safe to perform action without losing attack
        return false;
    }

    /**
     * get current attack state
     */
    public AttackState getAttackState() {
        return attackState;
    }

    /**
     * update attack state based on current tick
     * call this every game tick to maintain accurate state
     */
    public void updateAttackState() {
        int currentTick = Microbot.getClient().getTickCount();

        if (lastAttackTick < 0) {
            attackState = AttackState.NOT_ATTACKING;
            return;
        }

        int ticksSinceAttack = currentTick - lastAttackTick;

        if (ticksSinceAttack == 1) {
            attackState = AttackState.DELAYED_FIRST_TICK;
        } else if (ticksSinceAttack < weaponAttackSpeed) {
            attackState = AttackState.DELAYED;
        } else {
            attackState = AttackState.NOT_ATTACKING;
        }
    }

    /**
     * register boss for pattern tracking
     * creates 4:1 pattern by default
     */
    public void registerBoss4To1Pattern(int npcId, int npcIndex) {
        BossAttackPattern pattern = BossAttackPattern.create4To1Pattern(npcId, npcIndex);
        bossPatterns.put(npcIndex, pattern);
        log.debug("Registered 4:1 pattern for boss: npc_id={}, npc_index={}", npcId, npcIndex);
    }

    /**
     * register boss for 5:1 pattern tracking
     */
    public void registerBoss5To1Pattern(int npcId, int npcIndex) {
        BossAttackPattern pattern = BossAttackPattern.create5To1Pattern(npcId, npcIndex);
        bossPatterns.put(npcIndex, pattern);
        log.debug("Registered 5:1 pattern for boss: npc_id={}, npc_index={}", npcId, npcIndex);
    }

    /**
     * register boss with custom attack ratio
     */
    public void registerBossCustomPattern(int npcId, int npcIndex, int ratio) {
        BossAttackPattern pattern = BossAttackPattern.createCustomPattern(npcId, npcIndex, ratio);
        bossPatterns.put(npcIndex, pattern);
        log.debug("Registered {}:1 pattern for boss: npc_id={}, npc_index={}", ratio, npcId, npcIndex);
    }

    /**
     * add animation to exclude from boss attack count
     * example: Hunllef stomp (doesn't count toward 4:1 cycle)
     */
    public void excludeAnimationFromPattern(int npcIndex, int animationId) {
        BossAttackPattern pattern = bossPatterns.get(npcIndex);
        if (pattern != null) {
            BossAttackPattern updated = pattern.withExcludedAnimation(animationId);
            bossPatterns.put(npcIndex, updated);
            log.debug("Excluded animation {} from pattern for npc_index={}", animationId, npcIndex);
        }
    }

    /**
     * record player attack against boss
     * updates pattern tracking
     */
    public void recordPlayerAttackOnBoss(int npcIndex) {
        BossAttackPattern pattern = bossPatterns.get(npcIndex);
        if (pattern != null && pattern.isPatternActive()) {
            int currentTick = Microbot.getClient().getTickCount();
            BossAttackPattern updated = pattern.withPlayerAttack(currentTick);
            bossPatterns.put(npcIndex, updated);

            log.debug("Player attack recorded: npc_index={}, count={}/{}, pattern_progress={:.0f}%",
                npcIndex, updated.getPlayerAttackCount(), updated.getTargetRatio(),
                updated.getPatternProgress() * 100);
        }
    }

    /**
     * record boss attack
     * resets pattern if not excluded animation
     */
    public void recordBossAttack(int npcIndex, int animationId) {
        BossAttackPattern pattern = bossPatterns.get(npcIndex);
        if (pattern != null && pattern.isPatternActive()) {
            int currentTick = Microbot.getClient().getTickCount();
            BossAttackPattern updated = pattern.withBossAttack(currentTick, animationId);
            bossPatterns.put(npcIndex, updated);

            boolean isExcluded = pattern.getExcludedAnimations() != null &&
                pattern.getExcludedAnimations().contains(animationId);

            log.debug("Boss attack recorded: npc_index={}, animation={}, excluded={}, pattern_reset={}",
                npcIndex, animationId, isExcluded, !isExcluded);
        }
    }

    /**
     * get boss attack pattern
     */
    public Optional<BossAttackPattern> getBossPattern(int npcIndex) {
        return Optional.ofNullable(bossPatterns.get(npcIndex));
    }

    /**
     * check if can attack boss based on pattern
     * returns false if pattern is complete (waiting for boss attack)
     */
    public boolean canAttackBoss(int npcIndex, int bossAttackDelay) {
        BossAttackPattern pattern = bossPatterns.get(npcIndex);
        if (pattern == null || !pattern.isPatternActive()) {
            return true; // no pattern tracking, allow attack
        }

        int currentTick = Microbot.getClient().getTickCount();
        return pattern.isReadyForPlayerAttack(currentTick, bossAttackDelay);
    }

    /**
     * check if expecting boss attack next
     */
    public boolean isExpectingBossAttack(int npcIndex) {
        BossAttackPattern pattern = bossPatterns.get(npcIndex);
        if (pattern == null || !pattern.isPatternActive()) {
            return false;
        }

        return pattern.isExpectingBossAttack();
    }

    /**
     * get attacks remaining before pattern completes
     */
    public int getAttacksRemainingInPattern(int npcIndex) {
        BossAttackPattern pattern = bossPatterns.get(npcIndex);
        if (pattern == null || !pattern.isPatternActive()) {
            return 0;
        }

        return pattern.getAttacksRemaining();
    }

    /**
     * reset boss pattern
     */
    public void resetBossPattern(int npcIndex) {
        BossAttackPattern pattern = bossPatterns.get(npcIndex);
        if (pattern != null) {
            BossAttackPattern reset = pattern.withReset();
            bossPatterns.put(npcIndex, reset);
            log.debug("Reset boss pattern for npc_index={}", npcIndex);
        }
    }

    /**
     * activate boss pattern tracking
     */
    public void activateBossPattern(int npcIndex) {
        BossAttackPattern pattern = bossPatterns.get(npcIndex);
        if (pattern != null) {
            BossAttackPattern activated = pattern.withActivated();
            bossPatterns.put(npcIndex, activated);
            log.debug("Activated boss pattern for npc_index={}", npcIndex);
        }
    }

    /**
     * deactivate boss pattern tracking
     */
    public void deactivateBossPattern(int npcIndex) {
        BossAttackPattern pattern = bossPatterns.get(npcIndex);
        if (pattern != null) {
            BossAttackPattern deactivated = pattern.withDeactivated();
            bossPatterns.put(npcIndex, deactivated);
            log.debug("Deactivated boss pattern for npc_index={}", npcIndex);
        }
    }

    /**
     * unregister boss pattern
     */
    public void unregisterBossPattern(int npcIndex) {
        bossPatterns.remove(npcIndex);
        log.debug("Unregistered boss pattern for npc_index={}", npcIndex);
    }

    /**
     * coordinated attack with boss pattern support
     * checks tick loss, weapon cooldown, and boss pattern state
     */
    public boolean coordinatedAttackWithPattern(NPC target, int bossAttackDelay) {
        // validate target
        if (target == null || target.isDead()) {
            log.debug("Cannot attack: target is null or dead");
            return false;
        }

        int npcIndex = target.getIndex();

        // check boss pattern first
        if (!canAttackBoss(npcIndex, bossAttackDelay)) {
            log.debug("Cannot attack: waiting for boss attack (pattern complete)");
            return false;
        }

        // check tick loss state
        TickLossState tickLossState = getTickLossState();
        if (tickLossState != TickLossState.NONE) {
            log.debug("Cannot attack: tick loss state = {}", tickLossState);
            return false;
        }

        // check weapon cooldown
        if (isWeaponOnCooldown()) {
            log.debug("Cannot attack: weapon on cooldown ({} ticks remaining)",
                getTicksUntilNextAttack());
            return false;
        }

        Rs2NpcModel targetNpc = new Rs2NpcModel(target);

        // perform attack
        boolean success = Rs2Npc.attack(targetNpc);
        if (success) {
            lastAttackTick = Microbot.getClient().getTickCount();
            attackState = AttackState.DELAYED_FIRST_TICK;

            // record player attack in pattern
            recordPlayerAttackOnBoss(npcIndex);

            log.debug("Attacked target: {}, next attack in {} ticks, pattern progress: {}/{}",
                target.getName(), weaponAttackSpeed,
                getAttacksRemainingInPattern(npcIndex) > 0 ?
                    getBossPattern(npcIndex).map(BossAttackPattern::getPlayerAttackCount).orElse(0) : "N/A",
                getBossPattern(npcIndex).map(BossAttackPattern::getTargetRatio).orElse(0));
        }

        return success;
    }

    /**
     * get current weapon style (enum-based)
     * automatically updates if weapon or style changed
     *
     * @return current weapon style enum
     */
    public WeaponStyle getWeaponStyle() {
        updateWeaponAttackSpeed(); // ensure up-to-date
        return lastWeaponStyle;
    }

    /**
     * get current weapon style as varbit value (0-6)
     * useful for direct varbit comparisons
     *
     * @return varbit value (0-6) or -1 if unknown
     */
    public int getWeaponStyleVarbit() {
        updateWeaponAttackSpeed(); // ensure up-to-date
        return lastWeaponStyle.getVarbitValue();
    }

    /**
     * get current combat style (melee/ranged/magic)
     * automatically updates if weapon changed
     *
     * @return current combat style
     */
    public CombatStyle getCombatStyle() {
        updateWeaponAttackSpeed(); // ensure up-to-date
        return lastCombatStyle;
    }

    /**
     * get current attack range
     * automatically updates if weapon or style changed
     * accounts for longrange style (+2 range for ranged/magic)
     *
     * @return attack range in tiles
     */
    public int getAttackRange() {
        updateWeaponAttackSpeed(); // ensure up-to-date
        return lastAttackRange;
    }

    /**
     * check if current weapon style is melee
     */
    public boolean isMeleeStyle() {
        return getCombatStyle() == CombatStyle.MELEE;
    }

    /**
     * check if current weapon style is ranged
     */
    public boolean isRangedStyle() {
        return getCombatStyle() == CombatStyle.RANGED;
    }

    /**
     * check if current weapon style is magic
     */
    public boolean isMagicStyle() {
        return getCombatStyle() == CombatStyle.MAGIC;
    }

    /**
     * check if using longrange attack style
     * longrange provides +2 attack range and +3 defence XP
     */
    public boolean isUsingLongrange() {
        return getWeaponStyle() == WeaponStyle.LONGRANGE;
    }

    /**
     * check if using rapid attack style
     * rapid provides faster attack speed for ranged weapons
     */
    public boolean isUsingRapid() {
        return getWeaponStyle() == WeaponStyle.RAPID;
    }

    /**
     * get equipped weapon ID
     *
     * @return weapon item ID or -1 if none equipped
     */
    public int getEquippedWeaponId() {
        updateWeaponAttackSpeed(); // ensure up-to-date
        return lastEquippedWeaponId;
    }

    /**
     * clear all tracking data
     */
    public void clear() {
        attackState = AttackState.NOT_ATTACKING;
        lastAttackTick = -1;
        weaponAttackSpeed = 4;
        lastEquippedWeaponId = -1;
        lastWeaponStyle = WeaponStyle.UNKNOWN;
        lastCombatStyle = null;
        lastAttackRange = 1;
        bossPatterns.clear();
        log.debug("Cleared combat tracking and boss patterns");
    }
}
