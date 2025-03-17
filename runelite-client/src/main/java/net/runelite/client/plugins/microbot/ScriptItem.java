package net.runelite.client.plugins.microbot;

import lombok.*;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Represents an item required for scripts with additional properties
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ScriptItem {
    /**
     * Name of the item as a string
     */
    private String name;

    /**
     * ID of the item
     */
    private int id;

    /**
     * How many of this item are needed
     */
    private int quantity;

    /**
     * Whether the item name needs to be matched exactly
     * If false, partial matches are acceptable
     */
    private boolean exact;

    /**
     * Whether the item should be withdrawn in noted form
     */
    private boolean noted;

    /**
     * Whether the item should be worn
     */
    private boolean equipped;

    /**
     * Builder class for ScriptItem
     */
    public static class Builder {
        private String name;
        private int id = -1;
        private int quantity = 1;
        private boolean exact = false;
        private boolean noted = false;
        private boolean equipped = false;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder id(int id) {
            this.id = id;
            return this;
        }

        public Builder quantity(int quantity) {
            this.quantity = quantity;
            return this;
        }

        public Builder exact(boolean exact) {
            this.exact = exact;
            return this;
        }

        public Builder noted(boolean noted) {
            this.noted = noted;
            return this;
        }

        public Builder equipped(boolean equipped) {
            this.equipped = equipped;
            return this;
        }

        public ScriptItem build() {
            if (name == null && id == -1) {
                throw new IllegalStateException("Either name or id must be provided for ScriptItem");
            }

            ScriptItem item = new ScriptItem();
            item.name = this.name;
            item.id = this.id;
            item.quantity = this.quantity;
            item.exact = this.exact;
            item.noted = this.noted;
            item.equipped = this.equipped;
            return item;
        }
    }

    /**
     * Static factory method for starting a builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a string representation of this item for debugging
     */
    @Override
    public String toString() {
        String itemIdentifier = name != null ? name : "ID:" + id;
        return quantity + "x " + itemIdentifier + (noted ? " (noted)" : "") + (exact ? " (exact match)" : "") + (equipped ? " (equipped)" : "");
    }

    /**
     * Checks if the player has this item in inventory
     * @return true if the player has the required amount of this item
     */
    public boolean hasInInventory() {
        if (name != null) {
            return Rs2Inventory.hasItemAmount(name, quantity);
        } else {
            return Rs2Inventory.hasItemAmount(id, quantity);
        }
    }

    /**
     * Checks if the item is equipped
     * @return true if the item is equipped
     */
    public boolean isWearing() {
        if (name != null) {
            return Rs2Equipment.isWearing(name);
        } else {
            return Rs2Equipment.isWearing(id);
        }
    }

    /**
     * Withdraw this item from the bank
     */
    public void withdraw() {
        // Check if we already have the item
        if (hasInInventory()) {
            return;
        }

        // Set withdraw mode if needed
        if (noted) {
            Rs2Bank.setWithdrawAsNote();
        } else {
            Rs2Bank.setWithdrawAsItem();
        }

        // Perform the withdrawal
        if (quantity > 1) {
            if (name != null) {
                Rs2Bank.withdrawX(name, quantity, exact);
            } else {
                Rs2Bank.withdrawX(id, quantity);
            }
        } else {
            if (name != null) {
                Rs2Bank.withdrawOne(name, exact);
            } else {
                Rs2Bank.withdrawOne(id);
            }
        }
    }

    /**
     * Equips the item if it's in the inventory
     */
    public void wear() {
        if (equipped && !isEquipped()) {
            return;
        }

        if (name != null) {
            Rs2Inventory.wear(name);
        } else {
            Rs2Inventory.wear(id);
        }
    }
}
