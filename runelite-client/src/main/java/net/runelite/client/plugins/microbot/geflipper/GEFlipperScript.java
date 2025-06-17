package net.runelite.client.plugins.microbot.geflipper;

import net.runelite.api.GameState;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.item.Rs2ItemManager;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

public class GEFlipperScript extends Script {
    public static String status = "Idle";
    public static int profit = 0;
    private long startTime;
    private final Rs2ItemManager itemManager = new Rs2ItemManager();

    private static final List<String> F2P_ITEMS = Arrays.asList(
            "Lobster", "Rune scimitar", "Rune 2h sword", "Maple logs",
            "Adamant arrow", "Mithril ore", "Rune sword", "Steel platebody",
            "Coal", "Iron ore", "Steel bar", "Mithril bar", "Runite ore",
            "Feather", "Air rune", "Fire rune", "Water rune", "Nature rune",
            "Chaos rune", "Oak logs", "Willow logs", "Adamant platebody",
            "Adamant platelegs", "Mithril platebody", "Mithril platelegs",
            "Gold bar", "Law rune", "Mind rune", "Swordfish"
    );

    private static final long TRADE_LIMIT_MS = TimeUnit.HOURS.toMillis(4);
    private final Map<String, Long> lastFlipped = new HashMap<>();

    public boolean run(GEFlipperConfig config) {
        Rs2AntibanSettings.naturalMouse = true;
        startTime = System.currentTimeMillis();
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!Microbot.isLoggedIn() || Microbot.getClient().getGameState() != GameState.LOGGED_IN)
                return;
            if (!super.run())
                return;
            try {
                if (!Rs2GrandExchange.isOpen()) {
                    status = "Opening GE";
                    Rs2GrandExchange.openExchange();
                    return;
                }
                int gp = Rs2Inventory.count("Coins");
                for (String itemName : F2P_ITEMS) {
                    long last = lastFlipped.getOrDefault(itemName, 0L);
                    if (System.currentTimeMillis() - last < TRADE_LIMIT_MS)
                        continue;

                    int itemId = itemManager.getItemId(itemName);
                    int buyPrice = Rs2GrandExchange.getOfferPrice(itemId);
                    int sellPrice = Rs2GrandExchange.getSellPrice(itemId);
                    int volume = Rs2GrandExchange.getSellingQuantity(itemId);
                    int margin = sellPrice - buyPrice;
                    if (margin < config.minMargin() || volume < config.minVolume())
                        continue;
                    int quantity = Math.min(gp / buyPrice, 100); // simple calc
                    if (quantity <= 0)
                        continue;
                    status = "Buying " + itemName;
                    if (Rs2GrandExchange.buyItem(itemName, buyPrice, quantity)) {
                        profit += margin * quantity;
                        lastFlipped.put(itemName, System.currentTimeMillis());
                        break;
                    }
                }
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 3000, TimeUnit.MILLISECONDS);
        return true;
    }

    public static String getProfitPerHour() {
        long timeRan = System.currentTimeMillis() - Microbot.getScriptTimer();
        if (timeRan <= 0) {
            return "0";
        }
        double ph = profit * 3600000d / timeRan;
        return String.format("%,.0f", ph);
    }

    @Override
    public void shutdown() {
        super.shutdown();
        Rs2AntibanSettings.naturalMouse = false;
    }
}
