package net.runelite.client.plugins.microbot.VoxPlugins.util.pvm;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.handlers.*;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.model.TickLossState;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * tick-perfect action queue with priority system
 * coordinates all Phase 2 handlers for optimal PvM performance
 * executes actions in priority order while respecting tick loss
 */
@Slf4j
@Singleton
public class Rs2PvMCombat {

    private static Rs2PvMCombat instance;

    @Inject
    private Rs2PrayerHandler prayerHandler;

    @Inject
    private Rs2MovementHandler movementHandler;

    @Inject
    private Rs2CombatHandler combatHandler;

    @Inject
    private Rs2WeaponSwitchHandler weaponSwitchHandler;

    @Inject
    private Rs2ConsumableHandler consumableHandler;

    // action queue (tick -> list of actions)
    private final Map<Integer, List<PvMAction>> actionQueue = new ConcurrentHashMap<>();

    // action history for debugging
    private final Deque<ExecutedAction> actionHistory = new ConcurrentLinkedDeque<>();
    private static final int MAX_HISTORY_SIZE = 50;

    private Rs2PvMCombat() {
    }

    public static synchronized Rs2PvMCombat getInstance() {
        if (instance == null) {
            instance = new Rs2PvMCombat();
        }
        return instance;
    }

    /**
     * queue action for specific absolute tick
     */
    public void queueAction(PvMAction action, int targetTick) {
        actionQueue.computeIfAbsent(targetTick, k -> new ArrayList<>()).add(action);
        log.debug("Queued action: {} for absolute tick {}", action.getDescription(), targetTick);
    }

    /**
     * queue action for next available tick
     */
    public void queueAction(PvMAction action) {
        int currentTick = Microbot.getClient().getTickCount();
        queueAction(action, currentTick + 1);
    }

    /**
     * queue action for relative tick (current tick + offset)
     * example: queueActionRelative(action, 2) executes 2 ticks from now
     */
    public void queueActionRelative(PvMAction action, int tickOffset) {
        int currentTick = Microbot.getClient().getTickCount();
        int targetTick = currentTick + tickOffset;
        queueAction(action, targetTick);
        log.debug("Queued action: {} for relative tick +{} (absolute: {})",
            action.getDescription(), tickOffset, targetTick);
    }

    /**
     * queue multiple actions with relative tick offsets
     * example: queueActionSequence([attack, eat, attack], [0, 1, 3])
     * executes: attack now, eat +1 tick, attack +3 ticks
     */
    public void queueActionSequence(List<PvMAction> actions, List<Integer> tickOffsets) {
        if (actions.size() != tickOffsets.size()) {
            log.error("Action sequence size mismatch: {} actions, {} offsets",
                actions.size(), tickOffsets.size());
            return;
        }

        for (int i = 0; i < actions.size(); i++) {
            queueActionRelative(actions.get(i), tickOffsets.get(i));
        }

        log.debug("Queued action sequence: {} actions over {} ticks",
            actions.size(), tickOffsets.stream().max(Integer::compare).orElse(0));
    }

    /**
     * execute all actions for current tick
     * actions executed in priority order
     */
    public void executeQueuedActions() {
        int currentTick = Microbot.getClient().getTickCount();
        List<PvMAction> actions = actionQueue.remove(currentTick);

        if (actions == null || actions.isEmpty()) {
            return;
        }

        log.debug("Executing {} actions for tick {}", actions.size(), currentTick);

        // sort by priority (lower value = higher priority)
        actions.sort(Comparator.comparingInt(PvMAction::getPriority));

        for (PvMAction action : actions) {
            executeAction(action);
        }
    }

    /**
     * execute single action with tick loss validation
     */
    private void executeAction(PvMAction action) {
        // check tick loss state before executing
        TickLossState tickLoss = combatHandler != null
            ? combatHandler.getTickLossState()
            : TickLossState.NONE;

        if (action.requiresNoTickLoss() && tickLoss != TickLossState.NONE) {
            log.debug("Skipping action {} due to tick loss: {}", action.getDescription(), tickLoss);
            recordActionExecution(action, false, "Tick loss: " + tickLoss);
            return;
        }

        // execute action
        long startTime = System.currentTimeMillis();
        boolean success = false;
        String result = "Unknown";

        try {
            success = action.execute();
            result = success ? "Success" : "Failed";
        } catch (Exception e) {
            log.error("Action {} threw exception: {}", action.getDescription(), e.getMessage());
            result = "Exception: " + e.getMessage();
        }

        long executionTime = System.currentTimeMillis() - startTime;
        recordActionExecution(action, success, result);

        log.debug("Executed action: {} - {} ({}ms)", action.getDescription(), result, executionTime);
    }

    /**
     * record action execution in history
     */
    private void recordActionExecution(PvMAction action, boolean success, String result) {
        ExecutedAction executed = new ExecutedAction(
            action.getDescription(),
            action.getPriority(),
            Microbot.getClient().getTickCount(),
            System.currentTimeMillis(),
            success,
            result
        );

        actionHistory.addFirst(executed);

        // limit history size
        while (actionHistory.size() > MAX_HISTORY_SIZE) {
            actionHistory.removeLast();
        }
    }

    /**
     * handle automatic prayer switching (highest priority)
     * checks both projectiles and animations
     */
    public boolean handleAutoPrayer() {
        if (prayerHandler == null) {
            return false;
        }

        // projectile-based switching (highest priority)
        if (prayerHandler.handleIncomingProjectiles()) {
            return true;
        }

        // animation-based switching (fallback)
        return prayerHandler.handleNpcAnimations();
    }

    /**
     * handle automatic hazard dodging
     */
    public boolean handleAutoDodge() {
        if (movementHandler == null) {
            return false;
        }

        return movementHandler.autoDodge();
    }

    /**
     * handle automatic hazard dodging while maintaining attack range
     */
    public boolean handleAutoDodgeInRange(WorldPoint target, int attackRange) {
        if (movementHandler == null) {
            return false;
        }

        return movementHandler.autoDodgeInRange(target, attackRange);
    }

    /**
     * generate action combination: weapon switch + attack
     * queues weapon switch at tick 0, attack at tick 1
     */
    public void queueWeaponSwitchCombo(PvMAction weaponSwitch, PvMAction attack) {
        List<PvMAction> actions = Arrays.asList(weaponSwitch, attack);
        List<Integer> offsets = Arrays.asList(0, 1); // switch now, attack next tick
        queueActionSequence(actions, offsets);
        log.debug("Queued weapon switch combo");
    }

    /**
     * generate action combination: consume + attack
     * queues consume at tick 0, attack at tick 3 (safe delay)
     */
    public void queueConsumeAttackCombo(PvMAction consume, PvMAction attack) {
        List<PvMAction> actions = Arrays.asList(consume, attack);
        List<Integer> offsets = Arrays.asList(0, 3); // consume now, attack after 3 ticks
        queueActionSequence(actions, offsets);
        log.debug("Queued consume + attack combo");
    }

    /**
     * generate action combination: prayer + dodge + attack
     * immediate prayer switch, dodge next tick, attack when safe
     */
    public void queueDefensiveCombo(PvMAction prayer, PvMAction dodge, PvMAction attack) {
        List<PvMAction> actions = Arrays.asList(prayer, dodge, attack);
        List<Integer> offsets = Arrays.asList(0, 1, 3); // prayer now, dodge +1, attack +3
        queueActionSequence(actions, offsets);
        log.debug("Queued defensive combo");
    }

    /**
     * generate woox walk sequence: attack + move
     * timing depends on weapon speed
     * blowpipe: attack tick 0, move tick 2
     * lance: attack tick 0, move tick 4
     */
    public void queueWooxWalkSequence(PvMAction attack, PvMAction move, int weaponSpeed) {
        List<PvMAction> actions = Arrays.asList(attack, move);
        // move on the tick before next attack becomes available
        int moveOffset = weaponSpeed - 1;
        List<Integer> offsets = Arrays.asList(0, moveOffset);
        queueActionSequence(actions, offsets);
        log.debug("Queued woox walk: weapon_speed={}, move_offset={}", weaponSpeed, moveOffset);
    }

    /**
     * generate tick-eating sequence: eat + karambwan
     * both on same tick for instant combo eat
     */
    public void queueComboEatSequence(PvMAction eat, PvMAction karambwan) {
        List<PvMAction> actions = Arrays.asList(eat, karambwan);
        List<Integer> offsets = Arrays.asList(0, 0); // both same tick
        queueActionSequence(actions, offsets);
        log.debug("Queued combo eat");
    }

    /**
     * cancel actions for specific tick
     */
    public void cancelActionsForTick(int tick) {
        List<PvMAction> removed = actionQueue.remove(tick);
        if (removed != null && !removed.isEmpty()) {
            log.debug("Cancelled {} actions for tick {}", removed.size(), tick);
        }
    }

    /**
     * cancel all future actions
     */
    public void cancelFutureActions() {
        int currentTick = Microbot.getClient().getTickCount();
        int cancelled = 0;

        Iterator<Map.Entry<Integer, List<PvMAction>>> iterator = actionQueue.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, List<PvMAction>> entry = iterator.next();
            if (entry.getKey() > currentTick) {
                cancelled += entry.getValue().size();
                iterator.remove();
            }
        }

        if (cancelled > 0) {
            log.debug("Cancelled {} future actions", cancelled);
        }
    }

    /**
     * get actions queued for specific tick
     */
    public List<PvMAction> getActionsForTick(int tick) {
        return actionQueue.getOrDefault(tick, new ArrayList<>());
    }

    /**
     * get all queued actions
     */
    public Map<Integer, List<PvMAction>> getAllQueuedActions() {
        return new HashMap<>(actionQueue);
    }

    /**
     * get action execution history
     */
    public List<ExecutedAction> getActionHistory() {
        return new ArrayList<>(actionHistory);
    }

    /**
     * clear action queue
     */
    public void clearQueue() {
        actionQueue.clear();
        log.debug("Cleared action queue");
    }

    /**
     * clear action history
     */
    public void clearHistory() {
        actionHistory.clear();
        log.debug("Cleared action history");
    }

    /**
     * clear everything
     */
    public void clear() {
        clearQueue();
        clearHistory();
    }

    /**
     * interface for PvM actions
     */
    public interface PvMAction {
        /**
         * execute the action
         * returns true if successful
         */
        boolean execute();

        /**
         * get action priority (lower = higher priority)
         * 1 = prayer (survival)
         * 2 = dodge (avoid damage)
         * 3 = weapon switch (prepare attack)
         * 4 = attack (deal damage)
         * 5 = consume (healing/buffs)
         */
        int getPriority();

        /**
         * get action description for logging
         */
        String getDescription();

        /**
         * check if action requires no tick loss
         * true for attacks, false for prayers/dodging
         */
        boolean requiresNoTickLoss();
    }

    /**
     * record of executed action
     */
    @Getter
    public static class ExecutedAction {
        private final String description;
        private final int priority;
        private final int gameTick;
        private final long timestamp;
        private final boolean success;
        private final String result;

        public ExecutedAction(String description, int priority, int gameTick,
                            long timestamp, boolean success, String result) {
            this.description = description;
            this.priority = priority;
            this.gameTick = gameTick;
            this.timestamp = timestamp;
            this.success = success;
            this.result = result;
        }

        @Override
        public String toString() {
            return String.format("[T%d] %s - %s (Priority: %d)",
                gameTick, description, result, priority);
        }
    }

    /**
     * action priority constants
     */
    public static class Priority {
        public static final int PRAYER = 1;         // survival - highest priority
        public static final int DODGE = 2;          // avoid damage
        public static final int WEAPON_SWITCH = 3;  // prepare attack
        public static final int ATTACK = 4;         // deal damage
        public static final int CONSUME = 5;        // healing/buffs - lowest priority
    }
}
