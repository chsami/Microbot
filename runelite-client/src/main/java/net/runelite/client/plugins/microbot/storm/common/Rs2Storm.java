package net.runelite.client.plugins.microbot.storm.common;

import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class Rs2Storm {
    private static Set<Integer> recentItems = new HashSet<>();


    // For item name (String)
    public static Rs2ItemModel getRandomItemWithLimit(String itemName, int max_tries) {
        List<Rs2ItemModel> matchingItems = Rs2Inventory.items().stream()
                .filter(item -> item.getName().equalsIgnoreCase(itemName))
                .collect(Collectors.toList());

        if (matchingItems.isEmpty()) {
            return null; // No items match the provided name
        }

        return getRandomItemFromListWithLimit(matchingItems, max_tries);
    }
    public static Rs2ItemModel getRandomItemWithLimit(int itemId, int max_tries) {
        List<Rs2ItemModel> matchingItems = Rs2Inventory.items().stream()
                .filter(item -> item.id == itemId)
                .collect(Collectors.toList());

        if (matchingItems.isEmpty()) {
            return null; // No items match the provided ID
        }

        Rs2ItemModel selectedItem = null;
        int tries = 0;

        while (tries < max_tries) {
            int randomIndex = ThreadLocalRandom.current().nextInt(matchingItems.size());
            selectedItem = matchingItems.get(randomIndex);

            // Check if the item has been selected recently
            if (!recentItems.contains(selectedItem.getSlot())) {
                break;
            }

            tries++;
        }

        // Update recent items list
        recentItems.add(Objects.requireNonNull(selectedItem).getSlot());
        if (recentItems.size() > max_tries) {
            Iterator<Integer> iterator = recentItems.iterator();
            iterator.next();
            iterator.remove(); // Remove the oldest item
        }

        return selectedItem;
    }
    public static Rs2ItemModel getRandomItemFromListWithLimit(List<Rs2ItemModel> matchingItems, int max_tries) {
        Rs2ItemModel selectedItem = null;
        int tries = 0;

        while (tries < max_tries) {
            int randomIndex = ThreadLocalRandom.current().nextInt(matchingItems.size());
            selectedItem = matchingItems.get(randomIndex);

            // Ensure the item hasn't been selected recently
            if (!recentItems.contains(selectedItem.getSlot())) {
                break;
            }

            tries++;
        }

        // Update recent item history
        recentItems.add(Objects.requireNonNull(selectedItem).getSlot());
        if (recentItems.size() > max_tries) {
            recentItems.remove(0); // Remove the oldest item
        }

        return selectedItem;
    }
}
