package net.runelite.client.plugins.microbot.util.bank;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Thread-safe data class for caching bank items with ID, quantity, and slot information.
 * 
 * <p><strong>Thread Safety:</strong></p>
 * <ul>
 *   <li>All public methods are synchronized to ensure thread-safe access</li>
 *   <li>The {@code needsRebuild} flag is volatile for cross-thread visibility</li>
 *   <li>Defensive copying is used in {@code getBankItems()} to prevent external modification</li>
 *   <li>Atomic updates ensure consistent state during concurrent access</li>
 * </ul>
 * 
 * <p><strong>Usage Context:</strong></p>
 * <ul>
 *   <li>Updates happen from client thread via {@code ItemContainerChanged} events</li>
 *   <li>Reads happen from multiple threads (UI, script threads, etc.)</li>
 *   <li>Configuration loading/saving happens during login/logout events</li>
 * </ul>
 * 
 * <p>Enhanced for Rs2Bank ecosystem with optimized caching and rebuilding strategies.</p>
 */
@Data
@Slf4j
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
     * Made volatile to ensure visibility across threads.
     */
    private volatile boolean needsRebuild = true;

    public Rs2BankData() {
        idQuantityAndSlot = new int[0];
        bankItems = new ArrayList<>();
        needsRebuild = true;
    }

    /**
     * Sets bank data from a list of Rs2ItemModel objects.
     * Thread-safe method that atomically updates all bank data.
     * 
     * @param items List of Rs2ItemModel objects representing bank items
     */
    synchronized void set(List<Rs2ItemModel> items) {
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
        
        // Atomic update of all state
        idQuantityAndSlot = newIdQuantityAndSlot;
        bankItems.clear();
        bankItems.addAll(items);
        needsRebuild = false;
        
        log.trace("Bank data updated with {} items", items.size());
    }

    /**
     * Sets bank data from an array of Rs2ItemModel objects.
     * Thread-safe method that delegates to the synchronized set method.
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
     * Thread-safe method that atomically clears all state.
     */
    synchronized void setEmpty() {
        idQuantityAndSlot = new int[0];
        bankItems.clear();
        needsRebuild = false;
        log.trace("Bank data cleared");
    }

    /**
     * Sets the raw array data directly. Used for loading from config.
     * Thread-safe method that atomically updates the data and marks for rebuild.
     * 
     * @param data Raw array data in format [id, quantity, slot, ...]
     */
    synchronized void setIdQuantityAndSlot(int[] data) {
        this.idQuantityAndSlot = data != null ? data : new int[0];
        needsRebuild = true;  // Mark for rebuild since we're loading from config
        log.trace("Bank raw data set with {} entries, marked for rebuild", 
                this.idQuantityAndSlot.length / 3);
    }

    /**
     * Gets the raw array data. Used for saving to config.
     * Thread-safe method that returns a defensive copy.
     * 
     * @return Defensive copy of raw array data in format [id, quantity, slot, ...]
     */
    synchronized int[] getIdQuantityAndSlot() {
        return idQuantityAndSlot != null ? idQuantityAndSlot.clone() : new int[0];
    }

    /**
     * Gets the live bank items list. This is the primary access method that avoids rebuilding.
     * Thread-safe method that returns a defensive copy to prevent external modification.
     * Only rebuilds the list if the cache data has changed since last access.
     * 
     * @return Defensive copy of bank items list
     */
    public synchronized List<Rs2ItemModel> getBankItems() {
        if (needsRebuild) {
            rebuildBankItemsList();
        }
        // Return defensive copy to prevent external modification
        return new ArrayList<>(bankItems);
    }

    /**
     * Rebuilds the bankItems list from the cached array data.
     * Called only when needsRebuild is true to minimize performance impact.
     * Must be called from synchronized context.
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
                Rs2ItemModel item  = new Rs2ItemModel(id, quantity, slot); // lazy loading with Rs2ItemModel.createFromCache(id, quantity, slot);                                       
                bankItems.add(item);
            } catch (Exception e) {
                log.warn("Failed to recreate bank item from cache: id={}, qty={}, slot={}", id, quantity, slot, e);
                // Skip invalid items that can't be recreated
                continue;
            }
        }
        needsRebuild = false;
        log.trace("Rebuilt bank items list with {} items", bankItems.size());
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
     * Thread-safe method.
     * 
     * @return Number of items stored in the cache
     */
    public synchronized int size() {
        return idQuantityAndSlot != null ? idQuantityAndSlot.length / 3 : 0;
    }

    /**
     * Checks if the cache is empty.
     * Thread-safe method.
     * 
     * @return true if no items are cached, false otherwise
     */
    public synchronized boolean isEmpty() {
        return size() == 0;
    }
}
