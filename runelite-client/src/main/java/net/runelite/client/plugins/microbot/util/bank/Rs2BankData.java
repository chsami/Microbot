package net.runelite.client.plugins.microbot.util.bank;

import lombok.Data;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Data class for caching bank items with ID, quantity, and slot information.
 * Similar to QuestBankData but enhanced for Rs2Bank ecosystem.
 */
@Data
public class Rs2BankData {
    /**
     * Array storing bank item data in triplets: [id, quantity, slot, id, quantity, slot, ...]
     * Each item uses 3 consecutive array positions for complete bank state tracking.
     */
    int[] idQuantityAndSlot;
    
    /**
     * Cached live list of bank items. Only rebuilt when idQuantityAndSlot changes.
     * This prevents unnecessary rebuilding on every access.
     */
    private List<Rs2ItemModel> bankItems;
    
    /**
     * Flag to track if the cached bankItems list needs to be rebuilt.
     */
    private boolean needsRebuild = true;

    public Rs2BankData() {
        idQuantityAndSlot = new int[0];
        bankItems = new ArrayList<>();
        needsRebuild = true;
    }

    /**
     * Sets bank data from a list of Rs2ItemModel objects.
     * 
     * @param items List of Rs2ItemModel objects representing bank items
     */
    void set(List<Rs2ItemModel> items) {
        if (items == null || items.isEmpty()) {
            setEmpty();
            return;
        }
        
        int[] newIdQuantityAndSlot = new int[items.size() * 3];
        for (int i = 0; i < items.size(); i++) {
            Rs2ItemModel item = items.get(i);
            int baseIndex = i * 3;
            newIdQuantityAndSlot[baseIndex] = item.getId();
            newIdQuantityAndSlot[baseIndex + 1] = item.getQuantity();
            newIdQuantityAndSlot[baseIndex + 2] = item.getSlot();
        }
        idQuantityAndSlot = newIdQuantityAndSlot;
        
        // Update the live bankItems list directly to avoid rebuilding
        bankItems.clear();
        bankItems.addAll(items);
        needsRebuild = false;
    }

    /**
     * Sets bank data from an array of Rs2ItemModel objects.
     * 
     * @param items Array of Rs2ItemModel objects representing bank items
     */
    void set(Rs2ItemModel[] items) {
        if (items == null || items.length == 0) {
            setEmpty();
            return;
        }
        set(Arrays.asList(items));
    }

    /**
     * Clears all bank data.
     */
    void setEmpty() {
        idQuantityAndSlot = new int[0];
        bankItems.clear();
        needsRebuild = false;
    }

    /**
     * Sets the raw array data directly. Used for loading from config.
     * 
     * @param data Raw array data in format [id, quantity, slot, ...]
     */
    void setIdQuantityAndSlot(int[] data) {
        this.idQuantityAndSlot = data != null ? data : new int[0];
        needsRebuild = true;  // Mark for rebuild since we're loading from config
    }

    /**
     * Gets the raw array data. Used for saving to config.
     * 
     * @return Raw array data in format [id, quantity, slot, ...]
     */
    int[] getIdQuantityAndSlot() {
        return idQuantityAndSlot;
    }

    /**
     * Gets the live bank items list. This is the primary access method that avoids rebuilding.
     * Only rebuilds the list if the cache data has changed since last access.
     * 
     * @return Live list of Rs2ItemModel objects representing the cached bank items
     */
    public List<Rs2ItemModel> getBankItems() {
        if (needsRebuild) {
            rebuildBankItemsList();
        }
        return bankItems;
    }

    /**
     * Rebuilds the bankItems list from the cached array data.
     * Called only when needsRebuild is true to minimize performance impact.
     */
    private void rebuildBankItemsList() {
        bankItems.clear();

        if (idQuantityAndSlot == null || idQuantityAndSlot.length < 3) {
            needsRebuild = false;
            return;
        }

        // Process items in triplets: [id, quantity, slot]
        for (int i = 0; i < idQuantityAndSlot.length - 2; i += 3) {
            int id = idQuantityAndSlot[i];
            int quantity = idQuantityAndSlot[i + 1];
            int slot = idQuantityAndSlot[i + 2];
            
            // Create Rs2ItemModel from cached data
            try {
                Rs2ItemModel item = Rs2ItemModel.createFromCache(id, quantity, slot);
                bankItems.add(item);
            } catch (Exception e) {
                // Skip invalid items that can't be recreated
                continue;
            }
        }
        needsRebuild = false;
    }

    /**
     * Converts the stored data back to a list of Rs2ItemModel objects.
     * 
     * @return List of Rs2ItemModel objects representing the cached bank items
     * @deprecated Use getBankItems() instead for better performance
     */
    @Deprecated
    List<Rs2ItemModel> getAsList() {
        return getBankItems();
    }

    /**
     * Gets the number of cached bank items.
     * 
     * @return Number of items stored in the cache
     */
    public int size() {
        return idQuantityAndSlot != null ? idQuantityAndSlot.length / 3 : 0;
    }

    /**
     * Checks if the cache is empty.
     * 
     * @return true if no items are cached, false otherwise
     */
    public boolean isEmpty() {
        return size() == 0;
    }
}
