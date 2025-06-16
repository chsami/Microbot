package net.runelite.client.plugins.microbot.geflipper;

import net.runelite.api.ItemComposition;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.globval.VarbitIndices;
import net.runelite.client.game.ItemManager;
import net.runelite.http.api.item.ItemPrice;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class GeFlipperScript extends Script {
    public static final String VERSION = "1.0";
    public static int profit = 0;
    public static long startTime = 0;
    public static int startGp = 0;
    public static int currentGp = 0;

    private List<String> items = new ArrayList<>();
    private int index = 0;

    private void loadItems(GeFlipperConfig config) {
        if (!config.items().trim().isEmpty()) {
            items = Arrays.stream(config.items().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            return;
        }
        items = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Set<String> names = new TreeSet<>();
            ItemManager itemManager = Microbot.getItemManager();
            for (Field f : ItemID.class.getFields()) {
                if (f.getType() != int.class || !Modifier.isStatic(f.getModifiers())) continue;
                try {
                    int id = (int) f.get(null);
                    ItemComposition comp = itemManager.getItemComposition(id);
                    if (comp != null && comp.isTradeable() && !comp.isMembers() && !comp.getName().equalsIgnoreCase("null")) {
                        names.add(comp.getName());
                    }
                } catch (IllegalAccessException ignored) {}
            }
            return new ArrayList<>(names);
        }).orElse(new ArrayList<>());
    }

    private int getItemId(String name) {
        List<ItemPrice> list = Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getItemManager().search(name)).orElse(Collections.emptyList());
        if (list.isEmpty()) return -1;
        return list.get(0).getId();
    }

    private int getCurrentGp() {
        return Optional.ofNullable(Rs2Inventory.get(ItemID.COINS_995))
                .map(Rs2ItemModel::getQuantity)
                .orElse(0);
    }

    public boolean run(GeFlipperConfig config) {
        loadItems(config);
        startTime = System.currentTimeMillis();
        currentGp = getCurrentGp();
        startGp = currentGp;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run() || !isRunning()) return;
                if (index >= items.size()) {
                    Microbot.status = "Finished";
                    return;
                }
                currentGp = getCurrentGp();
                String item = items.get(index);
                int itemId = getItemId(item);
                int gePrice = itemId == -1 ? 0 : Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getItemManager().getItemPrice(itemId)).orElse(0);
                if (gePrice > currentGp) {
                    index++;
                    return;
                }
                if (!Rs2GrandExchange.isOpen()) {
                    Rs2GrandExchange.openExchange();
                    return;
                }
                Microbot.status = "Flipping " + item;
                if (Rs2GrandExchange.buyItemAbove5Percent(item, 1)) {
                    int buyPrice = Microbot.getVarbitValue(VarbitIndices.GE_OFFER_PRICE_PER_ITEM);
                    sleepUntil(Rs2GrandExchange::hasFinishedBuyingOffers);
                    Rs2GrandExchange.collectToInventory();
                    if (Rs2Inventory.hasItem(item)) {
                        if (Rs2GrandExchange.sellItemUnder5Percent(item)) {
                            int sellPrice = Microbot.getVarbitValue(VarbitIndices.GE_OFFER_PRICE_PER_ITEM);
                            sleepUntil(Rs2GrandExchange::hasFinishedSellingOffers);
                            Rs2GrandExchange.collectToInventory();
                            profit += sellPrice - buyPrice;
                        }
                    }
                    Rs2GrandExchange.backToOverview();
                    currentGp = getCurrentGp();
                    profit = currentGp - startGp;
                }
                index++;
            } catch (Exception ex) {
                Microbot.logStackTrace("GeFlipper", ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        profit = 0;
        startTime = 0;
        items.clear();
        index = 0;
    }
}
