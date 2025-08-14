package net.runelite.client.plugins.microbot.util.item;

import net.runelite.api.ItemComposition;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.http.api.item.ItemPrice;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Rs2ItemManager {


    public Rs2ItemManager() {
    }

    /**
     * Gets item ID by name  consider add it to Microbot's Rs2ItemManager.
     * This uses the efficient RuneLite ItemManager search functionality.
     */
    public static int getItemIdByName(String itemName, boolean fuzzy) {
        if (itemName == null || itemName.trim().isEmpty()) {
            return -1;
        }
        if(Rs2Bank.hasBankItem(itemName ,true)){
            return Rs2Bank.getBankItem(itemName ,true).getId();
        }
        if (Rs2Inventory.hasItem(itemName,true)){
            return Rs2Inventory.get(itemName).getId();
        }

        try {
            // Use Rs2ItemManager for efficient item lookup
             // Fallback: try direct ItemManager search with more control
            try {
                var searchResults = Microbot.getItemManager().search(itemName.trim());
                if (!searchResults.isEmpty()) {
                    // Return the first exact or closest match
                    Map<Integer,Integer> itemCounts = new HashMap<>();
                    for (var item : searchResults) {
                        if (item.getName().equalsIgnoreCase(itemName.trim())) {
                            return item.getId(); // Exact match preferred
                        }
                        itemCounts.put(item.getId(), Rs2Inventory.itemQuantity(item.getId()) + Rs2Bank.count(item.getId()));
                    }
                    if (fuzzy) {
                        // get item with the most quantity in inventory or bank                                                
                        // If fuzzy search allowed, return item with highest quantity
                        return itemCounts.entrySet().stream()
                                .max(Map.Entry.comparingByValue())
                                .map(Map.Entry::getKey)
                                .orElse(searchResults.get(0).getId());
                    }
                }
            } catch (Exception fallbackError) {
                log.error("Both primary and fallback item search failed for '{}': {}", itemName, fallbackError.getMessage());
            }            
        } catch (Exception e) {
            log.warn("Primary item search failed for '{}', trying fallback approach: {}", itemName, e.getMessage());                        
        }
            
        return -1; // Not found
    }

    /**
     * Searches for items containing the given query string in their name.
     *
     * @param query The string to search for.
     * @return A list of Rs2ItemSearchModel objects matching the search.
     */
    public List<ItemPrice> searchItem(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String lowerQuery = query.toLowerCase();
        return Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getItemManager().search(query)).orElse(Collections.emptyList());
    }

    // get item id by name
    public int getItemId(String itemName) {
        var items =searchItem(itemName);
        return items.get(0).getId();
    }

    public ItemComposition getItemComposition(int itemId) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getItemManager().getItemComposition(itemId)).orElse(null);
    }

    public int getPrice(int itemId) {
        ItemComposition itemComposition = getItemComposition(itemId);
        if (itemComposition == null) {
            return -1;
        }
        return itemComposition.getPrice();
    }

    // get GE price
    public int getGEPrice(String itemName) {
        return Rs2GrandExchange.getOfferPrice(getItemId(itemName));
    }

    public int getGEPrice(int itemId) {
        return Rs2GrandExchange.getOfferPrice(itemId);
    }


}
