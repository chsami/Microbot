package net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.model;

import lombok.Builder;
import lombok.Value;
import net.runelite.api.Item;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;

/**
 * immutable data class for tracking consumable actions (eating, drinking)
 * used to adjust combat timing based on consumption delays
 * based on RLCGPerformanceTracker's ItemMenuAction pattern
 * thread-safe by design (all fields final)
 */
@Value
@Builder
public class ConsumableAction {
    Rs2ItemModel[] oldInventory;  // snapshot before consumption
    int itemId;
    int slot;
    long timestamp;
    ConsumableType type;

    /**
     * get age in milliseconds
     */
    public long getAgeMs() {
        return System.currentTimeMillis() - timestamp;
    }

    /**
     * check if action is stale (older than threshold)
     */
    public boolean isStale(long maxAgeMs) {
        return getAgeMs() > maxAgeMs;
    }

    /**
     * get delay in ticks caused by this consumable
     */
    public int getDelayTicks() {
        return type.getDelayTicks();
    }

    /**
     * consumable types and their delays
     */
    public enum ConsumableType {
        FOOD(3),              // regular food: 3 tick delay
        CRYSTAL_FOOD(2),      // gauntlet crystal food: 2 tick delay
        POTION(3),            // potions: 3 tick delay
        COMBO_FOOD(3),        // combo food (e.g., karambwan): 3 ticks
        UNKNOWN(3);           // default to 3 ticks

        private final int delayTicks;

        ConsumableType(int delayTicks) {
            this.delayTicks = delayTicks;
        }

        public int getDelayTicks() {
            return delayTicks;
        }

        /**
         * determine consumable type from item id
         */
        public static ConsumableType fromItemId(int itemId) {
            // gauntlet crystal food (perfected)
            if (itemId == 23981 || itemId == 23982 || itemId == 23983 || itemId == 23984) {
                return CRYSTAL_FOOD;
            }

            // karambwan (combo food)
            if (itemId == 3144) {
                return COMBO_FOOD;
            }

            // default to regular food or potion (3 ticks)
            return FOOD;
        }
    }
}
