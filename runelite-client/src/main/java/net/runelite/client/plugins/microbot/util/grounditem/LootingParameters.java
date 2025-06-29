package net.runelite.client.plugins.microbot.util.grounditem;

public class LootingParameters {

    private int minValue, maxValue, range, minItems, minQuantity, minInvSlots;
    private boolean delayedLooting, antiLureProtection;
    private String[] names;
    private String[] ignoredNames;

    /**
     * This constructor is used to create a new LootingParameters object.
     * It sets the minimum value, maximum value, range, minimum items, delayed looting, and anti-lure protection.
     *
     * @param minValue           The minimum value of the items to be looted.
     * @param maxValue           The maximum value of the items to be looted.
     * @param range              The range within which the items to be looted are located.
     * @param minItems           The minimum number of items to be looted.
     * @param minInvSlots        The minimum number of inventory slots to have open.
     * @param delayedLooting     A boolean indicating whether looting should be delayed.
     * @param antiLureProtection A boolean indicating whether anti-lure protection should be enabled.
     */
    public LootingParameters(int minValue, int maxValue, int range, int minItems, int minInvSlots, boolean delayedLooting, boolean antiLureProtection) {
        setValues(minValue, maxValue, range, minItems, 1, minInvSlots, delayedLooting, antiLureProtection, null, null);
    }

    /**
     * This constructor is used to create a new LootingParameters object.
     * It sets the range, minimum items, minimum quantity, delayed looting, anti-lure protection, and names of the items to be looted.
     *
     * @param range              The range within which the items to be looted are located.
     * @param minItems           The minimum number of items to be looted.
     * @param minQuantity        The minimum quantity of items to be looted.
     * @param minInvSlots        The minimum number of inventory slots to have open.
     * @param delayedLooting     A boolean indicating whether looting should be delayed.
     * @param antiLureProtection A boolean indicating whether anti-lure protection should be enabled.
     * @param names              The names of the items to be looted.
     */
    public LootingParameters(int range, int minItems, int minQuantity, int minInvSlots, boolean delayedLooting, boolean antiLureProtection, String... names) {
        setValues(0, 0, range, minItems, minQuantity, minInvSlots, delayedLooting, antiLureProtection, null, names);
    }

    /**
     * This constructor is used to create a new LootingParameters object.
     * It sets the range, minimum items, minimum quantity, delayed looting, anti-lure protection, and names of the items to be looted.
     *
     * @param minValue           The minimum value of the items to be looted.
     * @param maxValue           The maximum value of the items to be looted.
     * @param range              The range within which the items to be looted are located.
     * @param minItems           The minimum number of items to be looted.
     * @param minInvSlots        The minimum number of inventory slots to have open.
     * @param delayedLooting     A boolean indicating whether looting should be delayed.
     * @param antiLureProtection A boolean indicating whether anti-lure protection should be enabled.
     * @param ignoredNames       The names of the items to be ignored.
     */

    public LootingParameters(int minValue, int maxValue, int range, int minItems, int minInvSlots, boolean delayedLooting, boolean antiLureProtection, String[] ignoredNames) {
        setValues(minValue, maxValue, range, minItems, 1, minInvSlots, delayedLooting, antiLureProtection, ignoredNames, null);
    }

    private void setValues(int minValue, int maxValue, int range, int minItems, int minQuantity, int minInvSlots, boolean delayedLooting, boolean antiLureProtection, String[] ignoredNames, String[] names) {
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.range = range;
        this.minItems = minItems;
        this.minQuantity = minQuantity;
        this.minInvSlots = minInvSlots;
        this.delayedLooting = delayedLooting;
        this.antiLureProtection = antiLureProtection;
        this.ignoredNames = ignoredNames;
        this.names = names;
    }


    // Getters
    public int getMinValue() {
        return minValue;
    }

    public int getMaxValue() {
        return maxValue;
    }

    public int getRange() {
        return range;
    }

    public int getMinItems() {
        return minItems;
    }

    public int getMinQuantity() {
        return minQuantity;
    }

    public int getMinInvSlots() {
        return minInvSlots;
    }

    public boolean isDelayedLooting() {
        return delayedLooting;
    }

    public boolean isAntiLureProtection() {
        return antiLureProtection;
    }

    public String[] getIgnoredNames() {
        return ignoredNames;
    }

    public String[] getNames() {
        return names;
    }

}
