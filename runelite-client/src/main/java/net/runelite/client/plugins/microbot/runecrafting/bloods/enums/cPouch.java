package net.runelite.client.plugins.microbot.runecrafting.bloods.enums;

import net.runelite.api.Skill;
import net.runelite.api.Varbits;
import net.runelite.api.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

public enum cPouch
{
    COLOSSAL(ItemID.COLOSSAL_POUCH, ItemID.COLOSSAL_POUCH_26786, Varbits.ESSENCE_POUCH_COLOSSAL_AMOUNT);

    private final int itemId;
    private final int degradedItemId;
    private final int varbitId;

    cPouch(int itemId, int degradedItemId, int varbitId)
    {
        this.itemId = itemId;
        this.degradedItemId = degradedItemId;
        this.varbitId = varbitId;
    }

    /**
     * Gets the current amount of essence stored in the pouch.
     */
    public int getCurrentEssence()
    {
        return Microbot.getClient().getVarbitValue(varbitId);
    }

    /**
     * Determines if the pouch is degraded.
     */
    public boolean isDegraded()
    {
        return getCurrentEssence() < getMaxCapacity();
    }

    /**
     * Gets the max capacity of the pouch based on Runecrafting level.
     */
    public int getMaxCapacity()
    {
        int rc = Rs2Player.getRealSkillLevel(Skill.RUNECRAFT);

        if (rc >= 85)
        {
            return 40;
        }
        else if (rc >= 75)
        {
            return 27;
        }
        else if (rc >= 50)
        {
            return 16;
        }
        else
        {
            return 8;
        }
    }

    /**
     * Checks how much more essence can be added to the pouch.
     */
    public int getRemainingCapacity()
    {
        return getMaxCapacity() - getCurrentEssence();
    }

    /**
     * Determines if the pouch is empty.
     */
    public boolean isEmpty()
    {
        return getCurrentEssence() == 0;
    }

    /**
     * Determines if the pouch is full.
     */
    public boolean isFull() {
        if (!Rs2Inventory.isEmpty() == Rs2Inventory.isFull() &&
                Rs2Inventory.allPouchesFull()) ;{
                    Rs2Bank.closeBank();
        }
        return false;
    }
    /**
     * Fills the pouch with pure essence.
     */
    public boolean fill()
    {
        if (!hasRequiredRunecraftingLevel()) return false;
        if (!hasPouchInInventory()) return false;
        if (!hasEssenceInInventory()) return false;
        if (isFull()) return false;

        if (!Rs2Bank.isOpen()) {
            Rs2Bank.openBank();
            Rs2Random.randomGaussian(900, 200);
        }
        while (!isFull() || !Rs2Inventory.isFull()) {
            if (!Rs2Bank.isOpen()) {
                Rs2Bank.openBank();
            }
            Rs2Random.randomGaussian(300, 100);
        }
            if (!Rs2Inventory.hasItem("Pure essence") && !Rs2Inventory.isFull()) {
                Rs2Bank.withdrawAll("Pure essence");
                Rs2Inventory.waitForInventoryChanges(800);
            }
        return Rs2Inventory.interact(itemId, "Fill");
    }

    public boolean empty()
    {
        if (!hasRequiredRunecraftingLevel()) return false;
        if (!hasPouchInInventory()) return false;
        if (isEmpty()) return false;

        return Rs2Inventory.interact(itemId, "Empty");
    }

    public boolean check()
    {
        if (!hasRequiredRunecraftingLevel()) return false;
        if (!hasPouchInInventory()) return false;

        return Rs2Inventory.interact(itemId, "Check");
    }

    /**
     * Determines if the player has the required Runecrafting level.
     */
    public boolean hasRequiredRunecraftingLevel()
    {
        return Rs2Player.getSkillRequirement(Skill.RUNECRAFT, 50);
    }

    /**
     * Determines if the pouch is in the player's inventory.
     */
    public boolean hasPouchInInventory()
    {
        return Rs2Inventory.hasItem(itemId) || Rs2Inventory.hasItem(degradedItemId);
    }

    /**
     * Determines if the player has essence to fill the pouch.
     */
    public boolean hasEssenceInInventory()
    {
        return Rs2Inventory.hasItem(ItemID.PURE_ESSENCE) || Rs2Inventory.hasItem(ItemID.DAEYALT_ESSENCE) || Rs2Inventory.hasItem(ItemID.GUARDIAN_ESSENCE);
    }
}
