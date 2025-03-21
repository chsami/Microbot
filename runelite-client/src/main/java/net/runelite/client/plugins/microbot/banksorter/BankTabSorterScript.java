package net.runelite.client.plugins.microbot.banksorter;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemContainer;
import net.runelite.api.Point;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.mouse.Mouse;

import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class BankTabSorterScript extends Script {
    @Inject
    Client client;

    Mouse mouse;

    private static final Pattern ITEM_NUMBER_PATTERN = Pattern.compile("^(.*?)(?:\\s*\\((\\d+)\\))?$");


    public boolean run() {
        mouse = Microbot.getMouse();
        mainScheduledFuture = scheduledExecutorService.schedule(() -> {
            if (!arePlaceholdersEnabled()) {
                Microbot.log("WARNING: Placeholders are not enabled! Please enable placeholders before sorting.");
                Microbot.log("Click the placeholder button in the bank interface and try again.");
                return false;
            }

            Microbot.log("Starting bank tab sorting!");
            // Get the visible items in the current bank tab
            List<BankItem> visibleItems = getVisibleBankItems();

            if (visibleItems.isEmpty()) {
                Microbot.log("No items found in the current tab");
                return false;
            }
            // Sort the items
            List<BankItem> sortedItems = sortItemsByName(visibleItems);

            // Perform the actual sorting in the bank
            rearrangeBankItems(sortedItems);

            Microbot.log("Bank tab sorting completed!");
            return true;
        }, 0, TimeUnit.SECONDS);
        return true;
    }

    private List<BankItem> getVisibleBankItems() {
        return Microbot.getClientThread().runOnClientThread(() -> {
            List<BankItem> items = new ArrayList<>();

            // Get the bank item container widget
            Widget bankContainer = client.getWidget(ComponentID.BANK_ITEM_CONTAINER);
            if (bankContainer == null) {
                Microbot.log("Bank container not found");
                return items;
            }

            // Get all item widgets in the container
            Widget[] bankItemWidgets = bankContainer.getDynamicChildren();

            // Process each widget to extract item information
            for (int i = 0; i < bankItemWidgets.length; i++) {
                Widget widget = bankItemWidgets[i];

                // Skip empty slots
                if (widget.getItemId() == -1 || widget.isHidden()) {
                    continue;
                }

                // Get item details
                int itemId = widget.getItemId();
                int quantity = widget.getItemQuantity();
                String itemName = client.getItemDefinition(itemId).getName();

                // Add to our list with the widget index as the original position
                items.add(new BankItem(itemId, itemName, quantity, i));
            }

            return items;
        });
    }

    private void rearrangeBankItems(List<BankItem> sortedItems) {
        if (sortedItems.isEmpty()) {
            return;
        }

        Microbot.log("Starting enhanced bank item rearrangement with " + sortedItems.size() + " items");

        // Keep track of which items we've moved already
        Set<String> movedItems = new HashSet<>();

        // Process each position in the sorted order
        for (int targetPos = 0; targetPos < sortedItems.size(); targetPos++) {
            // Re-fetch the bank container for each operation
            Widget bankContainer = client.getWidget(ComponentID.BANK_ITEM_CONTAINER);
            if (bankContainer == null) {
                Microbot.log("Bank container disappeared during sorting");
                return;
            }

            // Re-get all item widgets after each move
            Widget[] bankItemWidgets = bankContainer.getDynamicChildren();

            // Get the item that should be in this target position
            BankItem targetItem = sortedItems.get(targetPos);
            String targetItemKey = targetItem.getId() + "-" + targetItem.getName() + "-" + targetItem.getQuantity();

            // Skip if we've already moved this item
            if (movedItems.contains(targetItemKey)) {
                continue;
            }

            // Log which row we're working on (for debugging and user feedback)
            int currentRow = targetPos / 8 + 1;
            int posInRow = targetPos % 8 + 1;
            Microbot.log("Sorting position: Row " + currentRow + ", Item " + posInRow +
                    " (" + targetItem.getName() + ")");

            // Check if the correct item is already in this position
            Widget widgetAtTargetPos = null;
            for (Widget widget : bankItemWidgets) {
                if (widget.getIndex() == targetPos) {
                    widgetAtTargetPos = widget;
                    break;
                }
            }

            // Check if the correct item is already there
            if (widgetAtTargetPos != null && widgetAtTargetPos.getItemId() == targetItem.getId()) {
                Microbot.log(targetItem.getName() + " already in the correct position " + targetPos);
                movedItems.add(targetItemKey);
                continue;
            }

            // Find where our target item currently is
            Widget sourceWidget = null;
            for (Widget widget : bankItemWidgets) {
                if (widget.getItemId() == targetItem.getId()) {
                    // For items with the same ID, we'd ideally check quantity as well
                    sourceWidget = widget;
                    break;
                }
            }

            if (sourceWidget == null) {
                Microbot.log("Cannot find widget for item: " + targetItem.getName());
                continue;
            }

            // Make sure we have a valid target position
            if (widgetAtTargetPos == null) {
                Microbot.log("Cannot find widget at target position: " + targetPos);
                continue;
            }

            Microbot.log("Moving " + targetItem.getName() + " from position " +
                    sourceWidget.getIndex() + " to position " + targetPos);

            // Calculate drag points
            Point sourcePoint = new Point(
                    sourceWidget.getCanvasLocation().getX() + sourceWidget.getWidth() / 2,
                    sourceWidget.getCanvasLocation().getY() + sourceWidget.getHeight() / 2
            );

            Point targetPoint = new Point(
                    widgetAtTargetPos.getCanvasLocation().getX() + widgetAtTargetPos.getWidth() / 2,
                    widgetAtTargetPos.getCanvasLocation().getY() + widgetAtTargetPos.getHeight() / 2
            );

            // Execute the drag
            if (!sourcePoint.equals(targetPoint)) {
                Microbot.getMouse().drag(sourcePoint, targetPoint);
            }

            // Mark this item as moved
            movedItems.add(targetItemKey);

            // Allow a short delay between operations
            try {
                Thread.sleep(250 + (int) (Math.random() * 150));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        Microbot.log("Enhanced bank tab sorting completed!");
    }

    private List<BankItem> sortItemsByName(List<BankItem> items) {
        if (items.isEmpty()) {
            return items;
        }

        // Step 1: Group items by base name
        Map<String, List<BankItem>> itemGroups = new HashMap<>();

        for (BankItem item : items) {
            Matcher matcher = ITEM_NUMBER_PATTERN.matcher(item.getName());
            String baseName = matcher.matches() ? matcher.group(1).trim() : item.getName();

            if (!itemGroups.containsKey(baseName)) {
                itemGroups.put(baseName, new ArrayList<>());
            }
            itemGroups.get(baseName).add(item);
        }

        // Step 2: Sort items within each group (highest number first)
        for (List<BankItem> group : itemGroups.values()) {
            Collections.sort(group, (item1, item2) -> {
                Matcher matcher1 = ITEM_NUMBER_PATTERN.matcher(item1.getName());
                Matcher matcher2 = ITEM_NUMBER_PATTERN.matcher(item2.getName());

                int num1 = matcher1.matches() && matcher1.group(2) != null
                        ? Integer.parseInt(matcher1.group(2)) : 0;
                int num2 = matcher2.matches() && matcher2.group(2) != null
                        ? Integer.parseInt(matcher2.group(2)) : 0;

                // Reverse order - highest first
                return Integer.compare(num2, num1);
            });
        }

        // Step 3: Separate groups into teleport and non-teleport, then sort alphabetically
        List<Map.Entry<String, List<BankItem>>> teleportGroups = new ArrayList<>();
        List<Map.Entry<String, List<BankItem>>> nonTeleportGroups = new ArrayList<>();

        for (Map.Entry<String, List<BankItem>> entry : itemGroups.entrySet()) {
            if (entry.getKey().toLowerCase().contains("teleport")) {
                teleportGroups.add(entry);
            } else {
                nonTeleportGroups.add(entry);
            }
        }

        Collections.sort(teleportGroups, (a, b) -> a.getKey().compareToIgnoreCase(b.getKey()));
        Collections.sort(nonTeleportGroups, (a, b) -> a.getKey().compareToIgnoreCase(b.getKey()));

        // Step 4: Create final sorted list using a row-based approach
        List<BankItem> sortedItems = new ArrayList<>();
        int columns = 8; // Bank width

        // Process non-teleport groups first
        processSameNameGroups(sortedItems, nonTeleportGroups, columns);

        // Ensure teleport items start on a new row
        while (sortedItems.size() % columns != 0) {
            sortedItems.add(null); // Add null placeholders to complete the row
        }

        // Process teleport groups
        processSameNameGroups(sortedItems, teleportGroups, columns);

        // Remove null placeholders
        sortedItems.removeIf(Objects::isNull);

        return sortedItems;
    }

    private void processSameNameGroups(List<BankItem> sortedItems,
                                       List<Map.Entry<String, List<BankItem>>> groups,
                                       int columns) {
        for (Map.Entry<String, List<BankItem>> group : groups) {
            List<BankItem> groupItems = group.getValue();
            int groupSize = groupItems.size();

            // If this group fits in a single row
            if (groupSize <= columns) {
                // Ensure we start at position 0 of a row
                if (sortedItems.size() % columns != 0) {
                    // Fill the current row with nulls to start a new one
                    while (sortedItems.size() % columns != 0) {
                        sortedItems.add(null);
                    }
                }

                // Add all items from this group (which will start at position 0)
                sortedItems.addAll(groupItems);
            } else {
                // Group is larger than one row

                // Ensure we start at position 0 of a row
                if (sortedItems.size() % columns != 0) {
                    // Fill the current row with nulls to start a new one
                    while (sortedItems.size() % columns != 0) {
                        sortedItems.add(null);
                    }
                }

                // Add all items from this group, filling rows completely
                for (BankItem item : groupItems) {
                    sortedItems.add(item);
                }
            }
        }
    }

    private boolean arePlaceholdersEnabled() {
        return Microbot.getClientThread().runOnClientThread(() -> {
            // Get the placeholder button widget
            Widget placeholderButton = client.getWidget(786472);

            if (placeholderButton == null) {
                // If we can't find the button, we're uncertain - warn the user
                return false;
            }

            // The button is considered "selected" when placeholders are enabled
            // Check the sprite ID - when enabled/selected it should be 1356
            // If it's 1357, it's not selected
            return placeholderButton.getSpriteId() == 179;
        });
    }
}
