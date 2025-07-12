package net.runelite.client.plugins.microbot.tempoross;

import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

import java.util.function.BooleanSupplier;

public enum State {
    ATTACK_TEMPOROSS(() -> TemporossScript.ENERGY >= 94, null),
    SECOND_FILL(() -> getCookedFish() == 0, ATTACK_TEMPOROSS),
    THIRD_COOK(() -> getCookedFish() == ((TemporossScript.temporossConfig.solo() && TemporossScript.ESSENCE > 20) ? 19 : getAllFish()) || TemporossScript.INTENSITY >= 92 || (TemporossScript.ENERGY < 50 && getAllFish() > 16 && !TemporossScript.temporossConfig.solo()), SECOND_FILL),
    THIRD_CATCH(() -> getAllFish() >= ((TemporossScript.temporossConfig.solo() && TemporossScript.ESSENCE > 20) ? 19 : getTotalAvailableFishSlots()), THIRD_COOK),
    EMERGENCY_FILL(() -> getAllFish() == 0, THIRD_CATCH),
    INITIAL_FILL(() -> getCookedFish() == 0, THIRD_CATCH),
    SECOND_COOK(() -> getCookedFish() == (TemporossScript.temporossConfig.solo() ? 17 : getAllFish()), INITIAL_FILL),
    SECOND_CATCH(() -> getAllFish() >= (TemporossScript.temporossConfig.solo() ? 17 : getTotalAvailableFishSlots()), SECOND_COOK),
    INITIAL_COOK(() -> getRawFish() == 0, SECOND_CATCH),
    INITIAL_CATCH(() -> getRawFish() >= 7 || getAllFish() >= 10, INITIAL_COOK);

    public final BooleanSupplier isComplete;
    public final State next;

    State(BooleanSupplier isComplete, State next) {
        this.isComplete = isComplete;
        this.next = next;
    }

    public boolean isComplete() {
        return this.isComplete.getAsBoolean();
    }

    public static int getRawFish() {
        return Rs2Inventory.count(ItemID.TEMPOROSS_RAW_HARPOONFISH);
    }

    public static int getAllFish() {
        return getRawFish() + getCookedFish();
    }

    public static int getCookedFish() {
        return Rs2Inventory.count(ItemID.TEMPOROSS_HARPOONFISH);
    }

    public static int getTotalAvailableFishSlots() {
        return Rs2Inventory.getEmptySlots() + getAllFish();
    }

    public String toString() {
        return name().toLowerCase().replace("_", " ");
    }
}
