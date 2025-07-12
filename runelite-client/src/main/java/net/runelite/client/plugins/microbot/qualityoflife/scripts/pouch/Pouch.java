package net.runelite.client.plugins.microbot.qualityoflife.scripts.pouch;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

public enum Pouch {
    SMALL(new int[]{ItemID.RCU_POUCH_SMALL}, new int[]{3}, new int[]{3}, 1),
    MEDIUM(new int[]{ItemID.RCU_POUCH_MEDIUM, ItemID.RCU_POUCH_MEDIUM_DEGRADE}, new int[]{6}, new int[]{3}, 25),
    LARGE(new int[]{ItemID.RCU_POUCH_LARGE, ItemID.RCU_POUCH_LARGE_DEGRADE}, new int[]{9}, new int[]{7}, 50),
    GIANT(new int[]{ItemID.RCU_POUCH_GIANT, ItemID.RCU_POUCH_GIANT_DEGRADE}, new int[]{12}, new int[]{9}, 75),
    // degradedBaseHoldAmount for colossal pouch is dynamic, it starts at 35 and lowers
    // each time you use the degraded pouch. We'll see it to 25 to be safe
    // holdAmount -1 for colossal pouch because it is calculated based on the rc level
    COLOSSAL(new int[]{ItemID.RCU_POUCH_COLOSSAL, ItemID.RCU_POUCH_COLOSSAL_DEGRADE}, new int[]{8, 16, 27, 40}, new int[]{6, 13, 23, 35}, 25);


    private final int[] baseHoldAmount;

    private int getBaseHoldAmount() {
        int index = (this == COLOSSAL) ? getColossalHoldAmountIndex() : 0;
        return degraded ? degradedBaseHoldAmount[index] : baseHoldAmount[index];
    }

    private final int[] degradedBaseHoldAmount;

    @Getter(AccessLevel.PUBLIC)
    @Setter(AccessLevel.PACKAGE)
    private int[] itemIds;
    @Getter(AccessLevel.PUBLIC)
    @Setter(AccessLevel.PACKAGE)
    private int holding;
    @Getter
    private boolean degraded;
    @Getter(AccessLevel.PUBLIC)
    @Setter(AccessLevel.PACKAGE)
    private boolean unknown = true;
    @Getter(AccessLevel.PACKAGE)
    @Setter(AccessLevel.PACKAGE)
    private int levelRequired;


    Pouch(int[] itemIds, int[] holdAmount, int[] degradedHoldAmount, int levelRequired) {
        this.itemIds = itemIds;
        this.baseHoldAmount = holdAmount;
        this.degradedBaseHoldAmount = degradedHoldAmount;
        this.levelRequired = levelRequired;
    }

    public int getHoldAmount() {
        return degraded ? getDegradedBaseHoldAmount() : getBaseHoldAmount();
    }

    public int getRemaining() {
        final int holdAmount = degraded ? getDegradedBaseHoldAmount() : getBaseHoldAmount();
        return holdAmount - holding;
    }
    
    private int getDegradedBaseHoldAmount() {
        int index = (this == COLOSSAL) ? getColossalHoldAmountIndex() : 0;
        return degradedBaseHoldAmount[index];
    }

    void addHolding(int delta) {
        holding += delta;

        final int holdAmount = degraded ? getDegradedBaseHoldAmount() : getBaseHoldAmount();
        if (holding < 0) {
            holding = 0;
        }
        System.out.println(delta + " " + holding + " " + holdAmount);
        if (holding > holdAmount) {
            holding = holdAmount;
        }
    }

    void degrade(boolean state) {
        if (state != degraded) {
            degraded = state;
            final int holdAmount = degraded ? getDegradedBaseHoldAmount() : getBaseHoldAmount();
            holding = Math.min(holding, holdAmount);
        }
    }

    static Pouch forItem(int itemId) {
        switch (itemId) {
            case ItemID.RCU_POUCH_SMALL:
                return SMALL;
            case ItemID.RCU_POUCH_MEDIUM:
            case ItemID.RCU_POUCH_MEDIUM_DEGRADE:
                return MEDIUM;
            case ItemID.RCU_POUCH_LARGE:
            case ItemID.RCU_POUCH_LARGE_DEGRADE:
                return LARGE;
            case ItemID.RCU_POUCH_GIANT:
            case ItemID.RCU_POUCH_GIANT_DEGRADE:
                return GIANT;
            case ItemID.RCU_POUCH_COLOSSAL:
            case ItemID.RCU_POUCH_COLOSSAL_DEGRADE:
            case ItemID.DEVIOUS_GLOWINGPOUCH_COLOSSAL:
                return COLOSSAL;
            default:
                return null;
        }
    }

    public boolean fill() {
        if (!hasRequiredRunecraftingLevel()) return false;
        if (!hasItemsToFillPouch()) return false;
        if (!hasPouchInInventory()) return false;

        if (getRemaining() > 0) {
            for (int i = 0; i < itemIds.length; i++) {
                if (Rs2Inventory.interact(itemIds[i], "fill"))
                    return true;
            }
        }

        return false;
    }

    public boolean empty() {
        if (!hasRequiredRunecraftingLevel()) return false;
        if (!hasPouchInInventory()) return false;

        if (getHolding() > 0) {
            for (int i = 0; i < itemIds.length; i++) {
                if (Rs2Inventory.interact(itemIds[i], "empty"))
                    return true;
            }
        }

        return false;
    }

    public boolean check() {
        if (!hasRequiredRunecraftingLevel()) return false;

        for (int i = 0; i < itemIds.length; i++) {
            if (Rs2Inventory.interact(itemIds[i], "check"))
                return true;
        }

        return false;
    }

    public boolean hasRequiredRunecraftingLevel() {
        return Rs2Player.getSkillRequirement(Skill.RUNECRAFT, getLevelRequired());
    }

    public boolean hasItemsToFillPouch() {
        return Rs2Inventory.hasItem(ItemID.BLANKRUNE_HIGH) || Rs2Inventory.hasItem(ItemID.BLANKRUNE_DAEYALT) || Rs2Inventory.hasItem(ItemID.GOTR_GUARDIAN_ESSENCE);
    }

    public boolean hasPouchInInventory() {
        return Rs2Inventory.hasItem(itemIds);
    }

    public int getColossalHoldAmountIndex() {
        int runecraftLevel = Rs2Player.getBoostedSkillLevel(Skill.RUNECRAFT);

        if (runecraftLevel >= 85) {
            return 3;
        } else if (runecraftLevel >= 75) {
            return 2;
        } else if (runecraftLevel >= 50) {
            return 1;
        } else {
            return 0;
        }
    }
}