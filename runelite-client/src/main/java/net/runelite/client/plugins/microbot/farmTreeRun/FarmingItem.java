package net.runelite.client.plugins.microbot.farmTreeRun;

import lombok.Getter;

@Getter
class FarmingItem {
    private final int itemId;
    private final int quantity;
    private final boolean noted;
    private final boolean optional;

    public FarmingItem(int itemId, int quantity) {
        this(itemId, quantity, false, false); // Default `noted` to false
    }

    public FarmingItem(int itemId, int quantity, boolean noted) {
        this.itemId = itemId;
        this.quantity = quantity;
        this.noted = noted;
        this.optional = false;
    }

    public FarmingItem(int itemId, int quantity, boolean noted, boolean optional) {
        this.itemId = itemId;
        this.quantity = quantity;
        this.noted = noted;
        this.optional = optional;
    }
}
