package net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.handlers;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Item;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.model.ConsumableAction;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * thread-safe handler for tracking consumable usage
 * monitors eating and drinking to detect tick loss
 * integrates with Rs2CombatHandler for attack timing
 */
@Slf4j
@Singleton
public class Rs2ConsumableHandler {

    private static Rs2ConsumableHandler instance;

    // track last consumable action
    private volatile ConsumableAction lastConsumableAction;

    // history of recent consumable actions (max 10)
    private final ConcurrentLinkedDeque<ConsumableAction> consumableHistory = new ConcurrentLinkedDeque<>();
    private static final int MAX_HISTORY_SIZE = 10;

    private Rs2ConsumableHandler() {
    }

    public static synchronized Rs2ConsumableHandler getInstance() {
        if (instance == null) {
            instance = new Rs2ConsumableHandler();
        }
        return instance;
    }

    /**
     * handle menu option clicked to track consumable usage
     * called by Rs2PvMEventManager
     */
    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        String menuOption = event.getMenuOption();

        // check for consumable actions
        if (isConsumableAction(menuOption)) {
            // capture inventory snapshot before consumption
            Rs2ItemModel[] inventorySnapshot = Rs2Inventory.items().toArray(Rs2ItemModel[]::new);

            int itemId = event.getItemId();
            int slot = event.getParam0();

            // determine consumable type
            ConsumableAction.ConsumableType type = ConsumableAction.ConsumableType.fromItemId(itemId);

            // create immutable action record
            ConsumableAction action = ConsumableAction.builder()
                .oldInventory(inventorySnapshot)
                .itemId(itemId)
                .slot(slot)
                .timestamp(System.currentTimeMillis())
                .type(type)
                .build();

            // update tracking
            lastConsumableAction = action;
            consumableHistory.addFirst(action);

            // limit history size
            while (consumableHistory.size() > MAX_HISTORY_SIZE) {
                consumableHistory.removeLast();
            }

            log.debug("Tracked consumable: item_id={}, type={}, delay_ticks={}",
                itemId, type, type.getDelayTicks());
        }
    }

    /**
     * check if menu option is a consumable action
     */
    private boolean isConsumableAction(String menuOption) {
        return menuOption.equals("Eat") ||
               menuOption.equals("Drink") ||
               menuOption.equals("Consume");
    }

    /**
     * check if player ate or drank recently (within N milliseconds)
     */
    public boolean consumedRecently(long thresholdMs) {
        if (lastConsumableAction == null) {
            return false;
        }
        return lastConsumableAction.getAgeMs() <= thresholdMs;
    }

    /**
     * check if player is currently losing ticks from eating/drinking
     * uses the delay from ConsumableType
     */
    public boolean isLosingTickFromConsumable() {
        if (lastConsumableAction == null) {
            return false;
        }

        // check if within delay window
        long tickDelayMs = lastConsumableAction.getDelayTicks() * 600L; // 600ms per tick
        return lastConsumableAction.getAgeMs() <= tickDelayMs;
    }

    /**
     * get ticks remaining until can attack again after consuming
     */
    public int getTicksUntilCanAttack() {
        if (lastConsumableAction == null || !isLosingTickFromConsumable()) {
            return 0;
        }

        long elapsedMs = lastConsumableAction.getAgeMs();
        long tickDelayMs = lastConsumableAction.getDelayTicks() * 600L;
        long remainingMs = tickDelayMs - elapsedMs;

        return (int) Math.ceil(remainingMs / 600.0);
    }

    /**
     * get last consumable action
     */
    public Optional<ConsumableAction> getLastConsumableAction() {
        return Optional.ofNullable(lastConsumableAction);
    }

    /**
     * get consumable history (most recent first)
     */
    public List<ConsumableAction> getConsumableHistory() {
        return new ArrayList<>(consumableHistory);
    }

    /**
     * check if player ate food recently (within N ticks)
     */
    public boolean ateRecently(int tickThreshold) {
        if (lastConsumableAction == null) {
            return false;
        }

        ConsumableAction.ConsumableType type = lastConsumableAction.getType();
        if (type == ConsumableAction.ConsumableType.FOOD ||
            type == ConsumableAction.ConsumableType.CRYSTAL_FOOD ||
            type == ConsumableAction.ConsumableType.COMBO_FOOD) {

            long ticksAgo = lastConsumableAction.getAgeMs() / 600L;
            return ticksAgo <= tickThreshold;
        }

        return false;
    }

    /**
     * check if player drank potion recently (within N ticks)
     */
    public boolean drankRecently(int tickThreshold) {
        if (lastConsumableAction == null) {
            return false;
        }

        ConsumableAction.ConsumableType type = lastConsumableAction.getType();
        if (type == ConsumableAction.ConsumableType.POTION) {
            long ticksAgo = lastConsumableAction.getAgeMs() / 600L;
            return ticksAgo <= tickThreshold;
        }

        return false;
    }

    /**
     * clear all tracking data
     */
    public void clear() {
        lastConsumableAction = null;
        consumableHistory.clear();
        log.debug("Cleared consumable tracking");
    }
}
