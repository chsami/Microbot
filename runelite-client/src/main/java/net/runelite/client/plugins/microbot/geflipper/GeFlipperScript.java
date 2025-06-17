package net.runelite.client.plugins.microbot.geflipper;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.api.ItemID;
import net.runelite.api.ItemComposition;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

@Slf4j
public class GeFlipperScript extends Script {
    // Prices are retrieved from the OSRS Wiki 5m API
    // Trade volume continues to use GE Tracker
    private static final String PRICE_API = "https://prices.runescape.wiki/api/v1/osrs/5m?id=";
    private static final String USER_AGENT = "Microbot GE Flipper";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    private static final int MAX_TRADE_LIMIT = 50;
    private static final int GE_SLOT_COUNT = 3;
    private static final int MIN_VOLUME = 100;
    private static final int MIN_PROFIT = 1;

    private final Queue<Integer> items = new ArrayDeque<>();
    private final java.util.List<Integer> f2pItems = new java.util.ArrayList<>();
    private final java.util.Random random = new java.util.Random();
    private final java.util.Set<Integer> marginChecked = new java.util.HashSet<>();

    private GeFlipperPlugin plugin;
    private GeFlipperConfig config;
    private boolean running;

    private static class ActiveOffer {
        int itemId;
        int buyPrice;
        int sellPrice;
        int quantity;
        int slot;
        boolean buying;
        boolean marginCheck;
    }

    private long lastAction;
    private final java.util.List<ActiveOffer> offers = new java.util.ArrayList<>();
    private final Limits limits = new Limits();

    private JsonObject parseJson(String json) {
        JsonReader reader = new JsonReader(new StringReader(json));
        reader.setLenient(true);
        try {
            var element = new JsonParser().parse(reader);
            if (!element.isJsonObject()) {
                log.error("Response was not JSON: {}", json.length() > 100 ? json.substring(0, 100) : json);
                return null;
            }
            return element.getAsJsonObject();
        } catch (Exception ex) {
            log.error("Failed to parse JSON", ex);
            return null;
        }
    }

    private int[] fetchPrices(int itemId) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(PRICE_API + itemId))
                    .header("User-Agent", USER_AGENT)
                    .build();
            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.info("Price fetch failed: {}", resp.statusCode());
                return null;
            }
            JsonObject obj = parseJson(resp.body());
            if (obj == null || !obj.has("data")) {
                return null;
            }
            JsonObject data = obj.getAsJsonObject("data");
            JsonObject item = data.getAsJsonObject(String.valueOf(itemId));
            if (item == null || item.get("avgHighPrice").isJsonNull() || item.get("avgLowPrice").isJsonNull()) {
                return null;
            }
            int high = item.get("avgHighPrice").getAsInt();
            int low = item.get("avgLowPrice").getAsInt();
            return new int[]{high, low};
        } catch (Exception ex) {
            log.error("Failed to fetch prices for {}", itemId, ex);
            return null;
        }
    }

    private int getCoins() {
        return Rs2Inventory.itemQuantity(ItemID.COINS_995);
    }

    private String getItemName(int itemId) {
        ItemComposition item = Microbot.getClientThread()
                .runOnClientThreadOptional(() -> Microbot.getItemManager().getItemComposition(itemId))
                .orElse(null);
        return item != null ? item.getName() : "";
    }

    private java.util.List<Integer> loadF2pItems() {
        return Microbot.getClientThread().runOnClientThread(() -> {
            java.util.List<Integer> list = new java.util.ArrayList<>();
            for (java.lang.reflect.Field f : ItemID.class.getFields()) {
                if (!java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getType() != int.class)
                    continue;
                try {
                    int id = f.getInt(null);
                    ItemComposition comp = Microbot.getItemManager().getItemComposition(id);
                    if (comp != null && !comp.isMembers() && comp.isTradeable()) {
                        list.add(id);
                    }
                } catch (Exception ignored) {
                }
            }
            java.util.Collections.shuffle(list, random);
            return list;
        });
    }

    private int pollRandomItem() {
        if (items.isEmpty()) {
            items.addAll(f2pItems);
        }
        int index = random.nextInt(items.size());
        java.util.Iterator<Integer> it = items.iterator();
        for (int i = 0; i < index; i++) it.next();
        int val = it.next();
        it.remove();
        return val;
    }

    public boolean run(GeFlipperPlugin plugin, GeFlipperConfig config) {
        if (running) {
            return false;
        }
        this.plugin = plugin;
        this.config = config;
        running = true;
        Microbot.enableAutoRunOn = false;

        f2pItems.clear();
        f2pItems.addAll(loadF2pItems());
        items.clear();
        items.addAll(f2pItems);
        marginChecked.clear();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) {
                    Microbot.status = "Not logged in";
                    return;
                }
                if (!super.run()) {
                    Microbot.status = "Paused";
                    return;
                }

                if (!Rs2GrandExchange.isOpen()) {
                    Microbot.status = "Opening GE";
                    Rs2GrandExchange.openExchange();
                    return;
                }

                processOffers();

                if (offers.size() >= GE_SLOT_COUNT) {
                    Microbot.status = "Waiting for slot";
                    return;
                }

                if (System.currentTimeMillis() - lastAction < config.delay()) {
                    Microbot.status = "Delaying";
                    return;
                }

                if (items.isEmpty()) {
                    Microbot.status = "Loading items";
                    if (f2pItems.isEmpty()) {
                        f2pItems.addAll(loadF2pItems());
                    }
                    items.addAll(f2pItems);
                    if (items.isEmpty()) {
                        Microbot.status = "Queue empty";
                        return;
                    }
                }

                int next = pollRandomItem();
                ActiveOffer offer = prepareItem(next);
                if (offer == null) {
                    items.offer(next);
                    java.util.List<Integer> tmp = new java.util.ArrayList<>(items);
                    java.util.Collections.shuffle(tmp, random);
                    items.clear();
                    items.addAll(tmp);
                    lastAction = System.currentTimeMillis();
                    return;
                }

                var slotInfo = Rs2GrandExchange.getAvailableSlot();
                if (slotInfo.getLeft() == null || slotInfo.getLeft().ordinal() >= GE_SLOT_COUNT) {
                    items.offer(next);
                    java.util.List<Integer> tmp = new java.util.ArrayList<>(items);
                    java.util.Collections.shuffle(tmp, random);
                    items.clear();
                    items.addAll(tmp);
                    return;
                }

                String itemName = getItemName(next);
                Microbot.status = "Buying " + itemName;
                Rs2GrandExchange.buyItem(itemName, offer.buyPrice, offer.quantity);
                offer.slot = slotInfo.getLeft().ordinal();
                offer.buying = true;
                offers.add(offer);
                lastAction = System.currentTimeMillis();
            } catch (Exception ex) {
                log.error("Error in GE flipper", ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    private ActiveOffer prepareItem(int itemId) {
        String itemName = getItemName(itemId);
        if (itemName == null || itemName.isEmpty()) return null;
        try {
            int highVol = Rs2GrandExchange.getSellingVolume(itemId);
            int lowVol = Rs2GrandExchange.getBuyingVolume(itemId);

            int[] prices = fetchPrices(itemId);
            if (prices == null) {
                Microbot.log(itemName + " price lookup failed, skipping");
                Microbot.status = "No price";
                return null;
            }
            int high = prices[0];
            int low = prices[1];
            int sellPrice = (int) Math.ceil(high * 1.05); // sell for +5%
            int buyPrice = (int) Math.floor(low * 0.95); // buy for -5%

            Integer limit = limits.fetchLimit(itemId);
            if (high == 0 || low == 0) {
                Microbot.log(itemName + " price lookup failed, skipping");
                Microbot.status = "No price";
                return null;
            }
            if (sellPrice - buyPrice < MIN_PROFIT) {
                Microbot.log(itemName + " margin below " + MIN_PROFIT + "gp, skipping");
                Microbot.status = "Bad margin";
                return null;
            }
            if (highVol < MIN_VOLUME || lowVol < MIN_VOLUME) {
                Microbot.log(itemName + " volume too low, skipping");
                Microbot.status = "Low volume";
                return null;
            }
            if (limit == null || limit <= 0) {
                Microbot.log(itemName + " limit fetch failed");
                Microbot.status = "No limit";
                return null;
            }
            int remaining = limits.getRemaining(itemId, limit);
            if (remaining <= 0) {
                Microbot.log(itemName + " reached trade limit, waiting");
                Microbot.status = "Limit reached";
                return null;
            }

            int coins = getCoins();
            int quantity;
            ActiveOffer offer = new ActiveOffer();
            offer.itemId = itemId;
            if (!marginChecked.contains(itemId)) {
                offer.buyPrice = (int) Math.ceil(high * 1.05); // check margin +5%
                offer.sellPrice = (int) Math.floor(low * 0.95); // sell quickly -5%
                if (coins < offer.buyPrice) {
                    Microbot.log("Not enough gp to buy " + itemName);
                    Microbot.status = "Insufficient gp";
                    return null;
                }
                quantity = 1;
                offer.marginCheck = true;
            } else {
                quantity = Math.min(Math.min(Math.min(limit, MAX_TRADE_LIMIT), remaining), coins / buyPrice);
                if (quantity <= 0) {
                    Microbot.log("Not enough gp to buy " + itemName);
                    Microbot.status = "Insufficient gp";
                    return null;
                }
                offer.buyPrice = buyPrice;
                offer.sellPrice = sellPrice;
                offer.marginCheck = false;
            }
            offer.quantity = quantity;
            return offer;
        } catch (Exception ex) {
            log.error("Failed to fetch info for {}", itemName, ex);
            return null;
        }
    }

    private void processOffers() {
        var geOffers = Microbot.getClient().getGrandExchangeOffers();
        java.util.Iterator<ActiveOffer> it = offers.iterator();
        while (it.hasNext()) {
            ActiveOffer offer = it.next();
            if (offer.slot >= geOffers.length) {
                it.remove();
                continue;
            }
            var geOffer = geOffers[offer.slot];
            if (geOffer == null) {
                continue;
            }
            if (offer.buying) {
                if (geOffer.getState() == net.runelite.api.GrandExchangeOfferState.BOUGHT) {
                    Rs2GrandExchange.collect(false);
                    offer.buying = false;
                    String name = getItemName(offer.itemId);
                    Microbot.status = "Selling " + name;
                    Rs2GrandExchange.sellItem(name, offer.quantity, offer.sellPrice);
                }
            } else {
                if (geOffer.getState() == net.runelite.api.GrandExchangeOfferState.SOLD) {
                    Rs2GrandExchange.collectToBank();
                    if (!offer.marginCheck) {
                        plugin.addProfit((offer.sellPrice - offer.buyPrice) * offer.quantity);
                    }
                    limits.reduceRemaining(offer.itemId, offer.quantity);
                    items.offer(offer.itemId);
                    marginChecked.add(offer.itemId);
                    java.util.List<Integer> tmp = new java.util.ArrayList<>(items);
                    java.util.Collections.shuffle(tmp, random);
                    items.clear();
                    items.addAll(tmp);
                    it.remove();
                    lastAction = System.currentTimeMillis();
                }
            }
        }
    }

    public void onGameTick() {
        // not used
    }

    @Override
    public void shutdown() {
        super.shutdown();
        running = false;
        offers.clear();
        items.clear();
        limits.clear();
        marginChecked.clear();
    }

}