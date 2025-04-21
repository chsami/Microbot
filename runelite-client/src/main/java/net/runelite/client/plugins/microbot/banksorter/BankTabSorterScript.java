package net.runelite.client.plugins.microbot.banksorter;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
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
            // Get the items in current tab
            var currentTab = Rs2Bank.getCurrentTab();
            var indexStart = getTabStartIndex(currentTab);
            var indexEnd = getTabStartIndex(currentTab + 1);
            List<BankItem> items = new ArrayList<>();
            for (Widget widget : Rs2Bank.getItems()) {
                int index = widget.getIndex();
                String name = widget.getName().replaceAll(".*<col=[^>]*>(.*?)</col>.*", "$1");
                if (indexStart <= index && index < indexEnd) {
                    items.add(new BankItem(widget.getItemId(), name));
                }
            }
            // Sort the items
            List<BankItem> sortedItems = sortItemsByName(items);

            // Perform the actual sorting in the bank
            rearrangeBankItems(sortedItems);

            Microbot.log("Bank tab sorting completed!");
            return true;
        }, 0, TimeUnit.SECONDS);
        return true;
    }

    private void rearrangeBankItems(List<BankItem> sorted) {
        if (sorted.isEmpty()) {
            return;
        }
        Microbot.log("Starting bank item rearrangement with " + sorted.size() + " items");


        for (int i = 0; i < sorted.size(); i++) {
            BankItem item = sorted.get(i);
            if (!isRunning()) break;

            int currentTab = Rs2Bank.getCurrentTab();
            int tabStartIndex = getTabStartIndex(currentTab);
            int oldSlot = Rs2Bank.getItems().stream().filter(_item -> Objects.equals(_item.getItemId(), item.getId())).findFirst().get().getIndex();
            int newSlot = tabStartIndex + i;

            Widget sourceWidget = Rs2Bank.getItemWidget(oldSlot);
            Widget targetWidget = Rs2Bank.getItemWidget(newSlot);

            if (sourceWidget == null || targetWidget == null) {
                Microbot.log("No widget found.");
                continue;
            }

            if (sourceWidget.getItemId() == targetWidget.getItemId()) {
                Microbot.log(item.getName() + " already in the correct spot!");
                continue;
            }

            Microbot.log("Moving " + item.getName() + " from position " +
                    oldSlot + " to position " + newSlot);

            // Calculate drag points
            Point sourcePoint = new Point(
                    Rs2Random.between(
                            sourceWidget.getCanvasLocation().getX(),
                            sourceWidget.getCanvasLocation().getX() + sourceWidget.getWidth()),
                    Rs2Random.between(
                            sourceWidget.getCanvasLocation().getY(),
                            sourceWidget.getCanvasLocation().getY() + sourceWidget.getHeight())
            );

            Point targetPoint = new Point(
                    Rs2Random.between(
                            targetWidget.getCanvasLocation().getX(),
                            targetWidget.getCanvasLocation().getX() + targetWidget.getWidth()
                    ),
                    Rs2Random.between(
                            targetWidget.getCanvasLocation().getY(),
                            targetWidget.getCanvasLocation().getY() + targetWidget.getHeight()
                    )
            );

            // Execute the drag
            if (!sourcePoint.equals(targetPoint)) {
                Microbot.getMouse().drag(sourcePoint, targetPoint);
            }

            // Allow a short delay between operations
            sleep(Rs2Random.between(400, 500));
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

        // Step 3: Separate groups into rune, teleport, and non-teleport
        List<Map.Entry<String, List<BankItem>>> runeGroups = new ArrayList<>();
        List<Map.Entry<String, List<BankItem>>> teleportGroups = new ArrayList<>();
        List<Map.Entry<String, List<BankItem>>> nonTeleportGroups = new ArrayList<>();

        for (Map.Entry<String, List<BankItem>> entry : itemGroups.entrySet()) {
            if (entry.getKey().toLowerCase().endsWith(" rune")) {
                runeGroups.add(entry);
            } else if (entry.getKey().toLowerCase().contains("teleport")) {
                teleportGroups.add(entry);
            } else {
                nonTeleportGroups.add(entry);
            }
        }

        runeGroups.sort(Comparator.comparing(a -> a.getKey().toLowerCase()));
        teleportGroups.sort(Comparator.comparing(a -> a.getKey().toLowerCase()));
        nonTeleportGroups = sortByNameSimilarity(nonTeleportGroups);

        // Step 4: Create final sorted list using a row-based approach
        List<BankItem> sortedItems = new ArrayList<>();
        int columns = 8; // Bank width

        // Process non-teleport groups first
        processSameNameGroups(sortedItems, nonTeleportGroups, columns);

        // Process rune groups
        processSameNameGroups(sortedItems, runeGroups, columns);

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

    // Method to sort groups by name similarity using a greedy nearest-neighbor algorithm
    private List<Map.Entry<String, List<BankItem>>> sortByNameSimilarity(
            List<Map.Entry<String, List<BankItem>>> groups) {
        // Copy the list to avoid modifying the original
        List<Map.Entry<String, List<BankItem>>> unsorted = new ArrayList<>(groups);
        List<Map.Entry<String, List<BankItem>>> sorted = new ArrayList<>();

        if (unsorted.isEmpty()) {
            return sorted;
        }

        // Start with the first group in the list
        Map.Entry<String, List<BankItem>> current = unsorted.remove(0);
        sorted.add(current);

        // Greedily pick the next group with the highest Jaro-Winkler similarity
        while (!unsorted.isEmpty()) {
            Map.Entry<String, List<BankItem>> nearest = null;
            double maxSimilarity = -1.0;

            for (Map.Entry<String, List<BankItem>> candidate : unsorted) {
                double similarity = jaroWinklerSimilarity(current.getKey().toLowerCase(),
                        candidate.getKey().toLowerCase());
                if (similarity > maxSimilarity) {
                    maxSimilarity = similarity;
                    nearest = candidate;
                }
            }

            if (null != nearest) {
                sorted.add(nearest);
                unsorted.remove(nearest);
                current = nearest;
            } else {
                break;
            }
        }

        return sorted;
    }

    // Computes the Jaro-Winkler similarity between two strings.
    private double jaroWinklerSimilarity(String s1, String s2) {
        double jaroSim = jaroSimilarity(s1, s2);
        // Compute the length of common prefix (up to 4 characters)
        int prefixLength = 0;
        for (int i = 0; i < Math.min(4, Math.min(s1.length(), s2.length())); i++) {
            if (s1.charAt(i) == s2.charAt(i)) {
                prefixLength++;
            } else {
                break;
            }
        }
        return jaroSim + (prefixLength * 0.1 * (1 - jaroSim));
    }

    // Helper method to compute the Jaro similarity between two strings.
    private double jaroSimilarity(String s1, String s2) {
        if (s1.equals(s2)) {
            return 1.0;
        }

        int len1 = s1.length();
        int len2 = s2.length();

        if (0 == len1 || 0 == len2) {
            return 0.0;
        }

        // The matching window is half the length of the longer string minus one.
        int matchDistance = Math.max(len1, len2) / 2 - 1;

        boolean[] s1Matches = new boolean[len1];
        boolean[] s2Matches = new boolean[len2];

        int matches = 0;
        for (int i = 0; i < len1; i++) {
            int start = Math.max(0, i - matchDistance);
            int end = Math.min(i + matchDistance + 1, len2);
            for (int j = start; j < end; j++) {
                if (s2Matches[j]) {
                    continue;
                }
                if (s1.charAt(i) != s2.charAt(j)) {
                    continue;
                }
                s1Matches[i] = true;
                s2Matches[j] = true;
                matches++;
                break;
            }
        }

        if (0 == matches) {
            return 0.0;
        }

        double transpositions = 0;
        int k = 0;
        for (int i = 0; i < len1; i++) {
            if (!s1Matches[i]) {
                continue;
            }
            while (!s2Matches[k]) {
                k++;
            }
            if (s1.charAt(i) != s2.charAt(k)) {
                transpositions++;
            }
            k++;
        }
        transpositions /= 2.0;

        return ((matches / (double) len1) +
                (matches / (double) len2) +
                ((matches - transpositions) / matches)) / 3.0;
    }


    /**
     * Calculates the starting index for a given tab in the global bank container
     */
    private int getTabStartIndex(int tabNumber) {
        if (tabNumber == 0) {
            // For tab 0 (main tab), items start after all other tab items
            int itemsInOtherTabs = 0;
            for (int i = 0; i < 9; i++) {
                int tabVar = 4171 + i; // BANK_TAB_ONE_COUNT to BANK_TAB_NINE_COUNT
                itemsInOtherTabs += Microbot.getVarbitValue(tabVar);
            }
            return itemsInOtherTabs;
        } else {
            // For tabs 1-9, calculate the starting position by counting items in previous tabs
            int startPos = 0;
            for (int i = 0; i < tabNumber - 1; i++) {
                int tabVar = 4171 + i; // BANK_TAB_ONE_COUNT to BANK_TAB_NINE_COUNT
                startPos += Microbot.getVarbitValue(tabVar);
            }
            return startPos;
        }
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
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
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
        }).orElse(false);
    }
}
