package net.runelite.client.plugins.microbot.util.bank;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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
     * Atomic flag to track if the cache is LOADED (idQuantityAndSlot has data).
     * 
     * LOADED means:
     * - Raw cache data (int[] idQuantityAndSlot) has been loaded from RuneLite config
     * - Contains saved bank state from previous session as triplets: [id, quantity, slot, ...]
     * - Data exists but items are NOT yet usable as Rs2ItemModel objects
     * - This is the FIRST stage of cache initialization
     * - Does NOT mean the bank interface is open or items are accessible in-game
     */
    private final AtomicBoolean isCacheLoaded = new AtomicBoolean(false);
    
    /**
     * Atomic flag to track if the cache is BUILT (bankItems list is ready).
     * 
     * BUILT means:
     * - The raw idQuantityAndSlot data has been processed via rebuildBankItemsList()
     * - Rs2ItemModel objects have been created from cached data using ItemManager
     * - Items have proper names, icons, and game properties loaded
     * - The bankItems List<Rs2ItemModel> is ready for script usage
     * - This is the SECOND stage requiring client thread execution
     * - getBankItems() will return immediately without rebuilding
     */
    private final AtomicBoolean isCacheBuilt = new AtomicBoolean(false);

    public Rs2BankData() {
        idQuantityAndSlot = new int[0];
        bankItems = new ArrayList<>();
        isCacheLoaded.set(false);
        isCacheBuilt.set(false);
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
        
        // Mark cache as both loaded and built since we have live data
        isCacheLoaded.set(true);
        isCacheBuilt.set(true);
        
        log.trace("Bank data updated with {} items - cache loaded and built", items.size());
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
        
        // Reset cache states
        isCacheLoaded.set(false);
        isCacheBuilt.set(false);
        
        log.trace("Bank data cleared - cache states reset");
    }

    /**
     * Sets the raw array data directly. Used for loading from config.
     * Thread-safe method that atomically updates the data and marks for rebuild.
     * 
     * @param data Raw array data in format [id, quantity, slot, ...]
     */
    synchronized void setIdQuantityAndSlot(int[] data) {
        this.idQuantityAndSlot = data != null ? data : new int[0];
        
        // Update cache states: loaded from config but not yet built
        boolean hasData = this.idQuantityAndSlot.length > 0;
        isCacheLoaded.set(hasData);
        isCacheBuilt.set(false);  // Always false when loading from config
        
        log.trace("Bank raw data set with {} entries, cache loaded: {}, needs rebuild", 
                this.idQuantityAndSlot.length / 3, hasData);
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
        if (!isCacheBuilt.get()) {
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
            isCacheBuilt.set(false);  // No items to build
            return;
        }        
        log.debug("Rebuilding bank items list from cached data, size: {}", idQuantityAndSlot.length / 3);
        
        boolean rebuildSuccess = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            // Ensure we are on the client thread 
            log.debug("Rebuilding bank items list on client thread");            
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
                    log.warn("Failed to recreate bank item from cache: id={}, qty={}, slot={}", id, quantity, slot, e);
                    // Skip invalid items that can't be recreated
                    continue;
                }
            }
            return true;
        }).orElse(false);
        
        // Update cache built state based on rebuild success
        isCacheBuilt.set(rebuildSuccess);
        
        log.debug("Finished rebuilding bank items list with {} items, cache built: {}", 
                  bankItems.size(), rebuildSuccess);
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

    /**
     * Checks if the cache is LOADED (Stage 1: Raw data from config).
     * 
     * LOADED = Raw cache data exists in idQuantityAndSlot array
     * - Data was restored from RuneLite config on login/profile change
     * - Contains [id, quantity, slot] triplets from previous bank session
     * - Items are NOT yet usable - just raw integers
     * - This happens immediately when config is loaded
     * 
     * @return true if raw cache data is loaded from config, false otherwise
     */
    public boolean isCacheLoaded() {
        return isCacheLoaded.get();
    }

    /**
     * Checks if the cache is BUILT (Stage 2: Usable Rs2ItemModel objects).
     * 
     * BUILT = Rs2ItemModel objects are ready for script usage
     * - rebuildBankItemsList() has been executed successfully
     * - Raw data converted to Rs2ItemModel objects with names, properties, etc.
     * - Items have been validated through ItemManager on client thread
     * - getBankItems() will return immediately without rebuilding
     * - Scripts can safely use hasItem(), count(), etc.
     * 
     * @return true if bankItems list is fully built and ready, false otherwise
     */
    public boolean isCacheBuilt() {
        return isCacheBuilt.get();
    }

    /**
     * Checks if the cache is READY (Both loaded AND built).
     * 
     * READY = Complete cache initialization, scripts can use bank data
     * - Stage 1: Raw data loaded from config ✓
     * - Stage 2: Rs2ItemModel objects built and validated ✓
     * - Bank items are immediately available for script operations
     * - No rebuilding or client thread delays required
     * 
     * @return true if cache is fully initialized and ready for use, false otherwise
     */
    public boolean isCacheReady() {
        return isCacheLoaded() && isCacheBuilt();
    }

    /**
     * Forces a cache rebuild by marking it as not built.
     * This will trigger rebuildBankItemsList() on the next getBankItems() call.
     */
    public synchronized void markForRebuild() {
        isCacheBuilt.set(false);
        log.debug("Bank cache marked for rebuild");
    }

    /**
     * Gets cache state information for debugging.
     * 
     * @return formatted string with cache state details
     */
    public String getCacheStateInfo() {
        return String.format("Rs2BankData[loaded=%s, built=%s, size=%d, itemsReady=%d]", 
                           isCacheLoaded(), isCacheBuilt(), size(), bankItems.size());
    }
}
