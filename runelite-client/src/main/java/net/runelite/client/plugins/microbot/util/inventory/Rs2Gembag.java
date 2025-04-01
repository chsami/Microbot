package net.runelite.client.plugins.microbot.util.inventory;

import lombok.Getter;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemID;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Rs2Gembag {
    @Getter
    private static final List<Rs2ItemModel> gemBagContents = new ArrayList<>();

    private static final Pattern GEM_PATTERN = Pattern.compile("You just found (?:an|a) (sapphire|emerald|ruby|diamond|dragonstone)!", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHECK_PATTERN = Pattern.compile("Sapphires: (\\d+) / Emeralds: (\\d+) / Rubies: (\\d+) / Diamonds: (\\d+) / Dragonstones: (\\d+)");

    @Getter
    private static final List<Integer> gemBagItemIds = List.of(ItemID.GEM_BAG_12020, ItemID.OPEN_GEM_BAG);
    private static boolean unknown = true;

    /**
     * Handles chat messages to update gem bag contents when gems are found or checked.
     *
     * @param event The ChatMessage event triggered by in-game messages.
     */
    public static void onChatMessage(ChatMessage event) {
        if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM) return;
        if (!hasGemBag()) return;

        String message = event.getMessage();
        Matcher gemMatcher = GEM_PATTERN.matcher(message);

        if (gemMatcher.find()) {
            if (!isGemBagOpen()) return;
            Rs2ItemModel gemItem = getGemItemModel("uncut " + gemMatcher.group(1));
            if (gemItem != null) updateGem(gemItem, 1);
        }

        String cleanedMessage = message.replace("<br>", " / ");

        Matcher checkMatcher = CHECK_PATTERN.matcher(cleanedMessage);
        if (checkMatcher.find()) {
            updateGemBagContents(checkMatcher);
        }
    }

    public static void onMenuOptionClicked(MenuOptionClicked event) {
        if (!hasGemBag() || !isGemBagOpen()) return;

        String itemName = Rs2UiHelper.stripColTags(event.getMenuTarget());
        if (!itemName.toLowerCase().contains("gem bag")) return;

        switch (event.getMenuOption().toLowerCase()) {
            case "empty":
                Microbot.getClientThread().runOnSeperateThread(() -> {
                    if (Rs2Bank.isOpen()) {
                        resetGemBagContents();
                        unknown = false;
                    } else {
                        List<Rs2ItemModel> before = Rs2Inventory.items(item -> item.getName().matches("(?i)uncut sapphire|uncut emerald|uncut ruby|uncut diamond|uncut dragonstone")).collect(Collectors.toList());
                        boolean hasInventoryChanged = Rs2Inventory.waitForInventoryChanges(10000);
                        if (hasInventoryChanged) {
                            calculateEmptiedGems(before);
                        }
                    }
                    return true;
                });
                break;
            case "fill":
                Microbot.getClientThread().runOnSeperateThread(() -> {
                    List<Rs2ItemModel> before = Rs2Inventory.items(item -> item.getName().matches("(?i)uncut sapphire|uncut emerald|uncut ruby|uncut diamond|uncut dragonstone")).collect(Collectors.toList());
                    boolean hasInventoryChanged = Rs2Inventory.waitForInventoryChanges(10000);
                    if (hasInventoryChanged) {
                        handleFill(before);
                    }
                    return true;
                });
                break;
        }
    }

    private static void handleFill(List<Rs2ItemModel> inventoryBeforeFill) {
        List<Rs2ItemModel> afterFill = Rs2Inventory.items(item -> item.getName().matches("(?i)uncut sapphire|uncut emerald|uncut ruby|uncut diamond|uncut dragonstone")).collect(Collectors.toList());
        Map<String, Integer> beforeMap = countGems(inventoryBeforeFill);
        Map<String, Integer> afterMap = countGems(afterFill);

        beforeMap.forEach((name, count) -> {
            if (afterMap.isEmpty() || !afterMap.containsKey(name)) {
                updateGem(getGemItemModel(name), count);
            } else {
                int afterCount = afterMap.getOrDefault(name, 0);
                if (afterCount < count) {
                    updateGem(getGemItemModel(name), count - afterCount);
                }
            }
        });
    }

    private static Map<String, Integer> countGems(List<Rs2ItemModel> gems) {
        Map<String, Integer> map = new HashMap<>();
        for (Rs2ItemModel gem : gems) {
            map.put(gem.getName().toLowerCase(), map.getOrDefault(gem.getName().toLowerCase(), 0) + 1);
        }
        return map;
    }

    private static void calculateEmptiedGems(List<Rs2ItemModel> beforeEmpty) {
        List<Rs2ItemModel> afterEmpty = Rs2Inventory.items(item -> item.getName().matches("(?i)uncut sapphire|uncut emerald|uncut ruby|uncut diamond|uncut dragonstone")).collect(Collectors.toList());
        Map<String, Integer> beforeMap = countGems(beforeEmpty);
        Map<String, Integer> afterMap = countGems(afterEmpty);

        afterMap.forEach((name, count) -> {
            int diff = count - beforeMap.getOrDefault(name, 0);
            if (diff > 0) {
                updateGem(getGemItemModel(name), -diff);
            }
        });
    }

    /**
     * Returns an Rs2ItemModel for the given gem name.
     *
     * @param gemName The name of the gem.
     * @return Rs2ItemModel object representing the gem.
     */
    private static Rs2ItemModel getGemItemModel(String gemName) {
        int itemId;

        switch (gemName.toLowerCase()) {
            case "uncut sapphire":
                itemId = ItemID.UNCUT_SAPPHIRE;
                break;
            case "uncut emerald":
                itemId = ItemID.UNCUT_EMERALD;
                break;
            case "uncut ruby":
                itemId = ItemID.UNCUT_RUBY;
                break;
            case "uncut diamond":
                itemId = ItemID.UNCUT_DIAMOND;
                break;
            case "uncut dragonstone":
                itemId = ItemID.UNCUT_DRAGONSTONE;
                break;
            default:
                itemId = -1;
        }
        if (itemId == -1) return null;

        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            ItemComposition itemComposition = Microbot.getClient().getItemDefinition(itemId);
            return new Rs2ItemModel(new Item(itemId, 0), itemComposition, getGemSlot(gemName));
        }).orElse(null);
    }

    private static int getGemSlot(String gemName) {
        switch (gemName.toLowerCase()) {
            case "uncut sapphire":
                return 0;
            case "uncut emerald":
                return 1;
            case "uncut ruby":
                return 2;
            case "uncut diamond":
                return 3;
            case "uncut dragonstone":
                return 4;
            default:
                return -1;
        }
    }

    /**
     * Adds or updates a gem in the gem bag contents.
     *
     * @param gem      The Rs2ItemModel object representing the gem.
     * @param quantity The quantity of the gem to add.
     */
    private static void updateGem(Rs2ItemModel gem, int quantity) {
        Optional<Rs2ItemModel> existingGem = gemBagContents.stream()
                .filter(item -> item.equals(gem))
                .findFirst();

        if (existingGem.isPresent()) {
            Rs2ItemModel existingGemItem = existingGem.get();
            existingGemItem.setQuantity(existingGemItem.getQuantity() + quantity);
        } else {
            gem.setQuantity(quantity);
            gemBagContents.add(gem);
        }
    }

    /**
     * Clears the gem bag contents.
     */
    public static void resetGemBagContents() {
        gemBagContents.clear();
    }

    /**
     * Updates the gem bag contents based on parsed chat messages.
     *
     * @param matcher The regex matcher containing gem counts.
     */
    private static void updateGemBagContents(Matcher matcher) {
        resetGemBagContents();
        updateGem(getGemItemModel("uncut sapphire"), Integer.parseInt(matcher.group(1)));
        updateGem(getGemItemModel("uncut emerald"), Integer.parseInt(matcher.group(2)));
        updateGem(getGemItemModel("uncut ruby"), Integer.parseInt(matcher.group(3)));
        updateGem(getGemItemModel("uncut diamond"), Integer.parseInt(matcher.group(4)));
        updateGem(getGemItemModel("uncut dragonstone"), Integer.parseInt(matcher.group(5)));

        if (unknown) unknown = false;
    }

    /**
     * Gets the count of a specific gem in the gem bag.
     *
     * @param gem The name of the gem.
     * @return The count of the specified gem.
     */
    public static int getGemCount(String gem) {
        if (!hasGemBag() || unknown) return 0;
        return gemBagContents.stream()
                .filter(item -> item.getName().equalsIgnoreCase(gem))
                .findFirst()
                .map(Rs2ItemModel::getQuantity)
                .orElse(0);
    }

    /**
     * Gets the total count of all gems in the gem bag.
     *
     * @return The total count of all gems.
     */
    public static int getTotalGemCount() {
        if (!hasGemBag() || unknown) return 0;
        return gemBagContents.stream()
                .mapToInt(Rs2ItemModel::getQuantity)
                .sum();
    }

    /**
     * Checks if any gem slot in the gem bag is full (quantity 60).
     *
     * @return True if any gem slot is full, false otherwise.
     */
    public static boolean isAnyGemSlotFull() {
        if (!hasGemBag() || unknown) return false;
        return gemBagContents.stream().anyMatch(item -> item.getQuantity() == 60);
    }

    /**
     * Initiates checking the gem bag contents.
     */
    public static void checkGemBag() {
        if (!hasGemBag()) {
            Microbot.log("No gem bag found in inventory.");
            return;
        }
        if (Rs2Inventory.hasItem(ItemID.GEM_BAG_12020)) {
            Rs2Inventory.interact(ItemID.GEM_BAG_12020, "Open");
            Global.sleepUntil(() -> Rs2Inventory.hasItem(ItemID.OPEN_GEM_BAG));
        }
        Rs2Inventory.interact(ItemID.OPEN_GEM_BAG, "Check");
    }

    /**
     * Checks if the player has a gem bag in their inventory.
     *
     * @return True if the gem bag is in the inventory, false otherwise.
     */
    public static boolean hasGemBag() {
        return gemBagItemIds.stream().anyMatch(Rs2Inventory::hasItem);
    }

    public static boolean isGemBagOpen() {
        return Rs2Inventory.hasItem(ItemID.OPEN_GEM_BAG);
    }

    public static boolean isUnknown() {
        return unknown && hasGemBag();
    }
}