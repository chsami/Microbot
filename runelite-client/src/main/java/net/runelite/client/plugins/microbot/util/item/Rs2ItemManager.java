package net.runelite.client.plugins.microbot.util.item;

import net.runelite.api.ItemComposition;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.http.api.item.ItemPrice;

import java.util.Collections;
import java.util.List;

public class Rs2ItemManager {


    public Rs2ItemManager() {
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
