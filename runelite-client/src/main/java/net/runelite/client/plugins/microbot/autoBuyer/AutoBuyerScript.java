package net.runelite.client.plugins.microbot.autoBuyer;

import net.runelite.api.ChatMessageType;
import net.runelite.api.ItemComposition;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.questhelper.requirements.item.ItemRequirement;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeSlots;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import org.apache.commons.lang3.tuple.Pair;
import net.runelite.client.plugins.microbot.questhelper.QuestHelperPlugin;

import java.util.*;
import java.util.stream.Collectors;

import java.util.concurrent.TimeUnit;

public class AutoBuyerScript extends Script {

    public static boolean test = false;
    private static boolean initialized = false;
    private static int initialCount = 0;
    private static int totalBought = 0;
    private static Map<String, Integer> itemsList;
    private Integer timesToClickIncrease = null;
    private Integer percent = null;

    public boolean run(AutoBuyerConfig config) {

        Microbot.enableAutoRunOn = false;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run() || !isRunning()) return;
                long startTime = System.currentTimeMillis();

                String listOfItemsToBuy;
                if (getQuestHelperPlugin().getSelectedQuest() != null && config.buyQuest()) {
                    final List<Integer> idsList = Microbot.getClientThread().runOnClientThreadOptional(getQuestHelperPlugin()::itemsToTag).orElse(new ArrayList<>());

                    final Set<Integer> ids = idsList.stream()
                            .distinct()
                            .filter(this::isTradeable)
                            .limit(250)
                            .collect(Collectors.toCollection(TreeSet::new));

                    listOfItemsToBuy = getItemRequirements()
                            .stream()
                            .filter(item -> ids.contains(item.getId())) // Filter items based on the ids list
                            .map(item -> getItemName(item.getId()) + "[" + item.getQuantity() + "]") // Format: "Name[Quantity]"
                            .collect(Collectors.joining(",")); // Joins names with ", "

                    if(!listOfItemsToBuy.isEmpty()) Microbot.log("Using quest helper item list: " + listOfItemsToBuy);
                    else Microbot.log("Requirements already met check bank");


                } else {
                    Microbot.log("Using manual item list");
                    if (getQuestHelperPlugin().getSelectedQuest() == null) Microbot.log("maybe because you forgot to select Quest in the Quest helper ?");
                    listOfItemsToBuy = config.listOfItemsToBuy().replaceAll("\\s*,\\s*", ",");
                }

                if (!initialized) {
                    if (listOfItemsToBuy.length() <= 0) {
                        Microbot.log("No items found.");
                        shutdown();
                    }
                    itemsList = mapItems(splitItemsByCommas(listOfItemsToBuy));
                    initialCount = itemsList.size();
                    initialized = true;

                    if (config.pricePerItem().equals(Percentage.PERCENT_5))
                        timesToClickIncrease = 1;
                    else if (config.pricePerItem().equals(Percentage.PERCENT_10)) {
                        timesToClickIncrease = 2;
                    }
                    else {
                        percent = config.pricePerItem().getValue();
                    }
                    if (!isRunning())
                        return;
                }

                if (!Rs2GrandExchange.isOpen()) {
                    Rs2GrandExchange.openExchange();
                }

                itemsList.forEach((itemName, quantity) -> {
                    if (!isRunning())
                        return;
                    // Try to collect items to bank to free up slots
                    if (!hasFreeSlots()) {
                        if (canFreeUpSlots()) {
                            Rs2GrandExchange.collectToBank();
                            Microbot.log("Items bought from G.E. are collected to your bank.");
                        } else {
                            Microbot.log("All G.E. slots are in use, either abort or wait until one comes available.");
                            return;
                        }
                    }

                    boolean result;
                    if (timesToClickIncrease != null ) {
                        result = Rs2GrandExchange.buyItemAbove5Percent(itemName, quantity, timesToClickIncrease);
                    } else {
                        result = Rs2GrandExchange.buyItemAboveXPercent(itemName, quantity, percent);
                    }

                    if (!result) {
                        Microbot.log("Could not buy '" + itemName + "'. Skipping it.");
                        Rs2GrandExchange.backToOverview();
                    }
                    itemsList.remove(itemName); // Remove from list so we don't buy the same item again
                    totalBought++;
                });

                // Loop until we bought every item
                if (totalBought < initialCount)
                    return;

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);
                Microbot.getClientThread().runOnClientThreadOptional(() -> {
                Microbot.getClient().addChatMessage(ChatMessageType.ENGINE, "", "Made with love by Acun.", "Acun", false);

                if (config.buyQuest()) {
                    Microbot.getClient().addChatMessage(ChatMessageType.ENGINE, "", "Auto-buying quest items enabled! Churr bro ! - Wassupzzz", "Wassupzzz", false);
                }

                return null;
            });
                Microbot.log("Finished buying.");
                shutdown();
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    private boolean canFreeUpSlots() {
        return Rs2GrandExchange.hasSoldOffer() || Rs2GrandExchange.hasBoughtOffer();
    }

    private String[] splitItemsByCommas(String input) {
        // Split the input string by commas
        return input.split(",");
    }

    private boolean hasFreeSlots() {
        Pair<GrandExchangeSlots, Integer> availableSlots = Rs2GrandExchange.getAvailableSlot();
        return Integer.parseInt(String.valueOf(availableSlots.getRight())) > 0;
    }


    private Map<String, Integer> mapItems(String[] items) {
        Map<String, Integer> itemMap = new HashMap<>();

        // Process each item
        for (String item : items) {
            try {
                // Check if the item contains the quantity part
                if (item.contains("[")) {
                    // Split the item into name and quantity parts
                    String[] parts = item.split("\\[");
                    String name = parts[0].trim();
                    String quantityStr = parts[1].replace("]", "").trim();

                    // Convert the quantity to an integer
                    int quantity = Integer.parseInt(quantityStr);

                    // Store the item and its quantity in the map
                    itemMap.put(name, quantity);
                } else {
                    // If quantity is missing, default it to 1
                    itemMap.put(item.trim(), 1);
                }
            } catch (NumberFormatException e) {
                Microbot.log(item + " has an invalid quantity. Quantity must be a number.");
                shutdown();
            }
        }

        return itemMap;
    }

    @Override
    public void shutdown() {
        initialized = false;
        initialCount = 0;
        totalBought = 0;
        itemsList.clear();
        timesToClickIncrease = null;
        percent = null;
        super.shutdown();
    }

    public String getItemName(int itemId) {
        ItemComposition item = Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getItemManager().getItemComposition(itemId)).orElse(null);
        return item.getName();
    }

    public List<ItemRequirement> getItemRequirements() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> getQuestHelperPlugin().getSelectedQuest().getItemRequirements()).orElse(null);
    }

    public boolean isTradeable(int itemId) {
        ItemComposition item = Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getItemManager().getItemComposition(itemId)).orElse(null);
        return item.isTradeable();
    }

    protected QuestHelperPlugin getQuestHelperPlugin() {
        return (QuestHelperPlugin) Microbot.getPluginManager().getPlugins().stream().filter(x -> x instanceof QuestHelperPlugin).findFirst().orElse(null);
    }
}