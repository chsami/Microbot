package net.runelite.client.plugins.microbot.geflipper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.api.ItemID;
import net.runelite.api.ItemComposition;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.HashMap;

@Slf4j
public class GeFlipperScript extends Script {
    private static final String PRICE_API = "https://prices.runescape.wiki/api/v1/osrs/5m?id=";
    // Fetch trade limits from GE Tracker. If that fails, fall back to the OSRS Wiki
    private static final String LIMIT_API = "https://www.ge-tracker.com/api/items/";
    private static final String WIKI_LIMIT_API = "https://prices.runescape.wiki/api/v1/osrs/limits?id=";
    private static final int MAX_TRADE_LIMIT = 50;
    private static final int GE_SLOT_COUNT = 3;
    private static final int MIN_VOLUME = 100;
    private static final String USER_AGENT = "Microbot GE Flipper";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    private static final long FOUR_HOURS_MS = TimeUnit.HOURS.toMillis(4);

    private final Queue<Integer> items = new ArrayDeque<>();
    private final java.util.List<Integer> f2pItems = new java.util.ArrayList<>();
    private final java.util.Random random = new java.util.Random();

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
    }

    private long lastAction;
    private final java.util.List<ActiveOffer> offers = new java.util.ArrayList<>();
    private final Map<Integer, Integer> remainingLimits = new HashMap<>();
    private final Map<Integer, Long> limitResetTimes = new HashMap<>();

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
            HttpRequest priceReq = HttpRequest.newBuilder()
                    .uri(URI.create(PRICE_API + itemId))
                    .header("User-Agent", USER_AGENT)
                    .build();

            HttpResponse<String> priceResp = HTTP_CLIENT.send(priceReq, HttpResponse.BodyHandlers.ofString());

            if (priceResp.statusCode() != 200) {
                Microbot.log(itemName + " data fetch failed");
                return null;
            }

            JsonObject obj = parseJson(priceResp.body());
            if (obj == null) {
                return null;
            }

            JsonObject data = obj.has("data") && obj.get("data").isJsonObject() ? obj.getAsJsonObject("data") : obj;
            JsonObject itemData = data.has(Integer.toString(itemId)) && data.get(Integer.toString(itemId)).isJsonObject()
                    ? data.getAsJsonObject(Integer.toString(itemId)) : data;
            if (itemData == null) {
                Microbot.log(itemName + " price data missing, skipping");
                return null;
            }

            int high = itemData.has("avgHighPrice") && !itemData.get("avgHighPrice").isJsonNull()
                    ? itemData.get("avgHighPrice").getAsInt() : 0;
            int low = itemData.has("avgLowPrice") && !itemData.get("avgLowPrice").isJsonNull()
                    ? itemData.get("avgLowPrice").getAsInt() : 0;
            int highVol = itemData.has("highPriceVolume") && !itemData.get("highPriceVolume").isJsonNull()
                    ? itemData.get("highPriceVolume").getAsInt() : 0;
            int lowVol = itemData.has("lowPriceVolume") && !itemData.get("lowPriceVolume").isJsonNull()
                    ? itemData.get("lowPriceVolume").getAsInt() : 0;

            HttpRequest limitReq = HttpRequest.newBuilder()
                    .uri(URI.create(LIMIT_API + itemId))
                    .header("User-Agent", USER_AGENT)
                    .build();
            HttpResponse<String> limitResp = HTTP_CLIENT.send(limitReq, HttpResponse.BodyHandlers.ofString());

            JsonObject limitObj = null;
            if (limitResp.statusCode() == 200) {
                limitObj = parseJson(limitResp.body());
            }
            if (limitResp.statusCode() != 200 || limitObj == null) {
                // Try the OSRS Wiki as a fallback if flipping.gg fails
                HttpRequest wikiReq = HttpRequest.newBuilder()
                        .uri(URI.create(WIKI_LIMIT_API + itemId))
                        .header("User-Agent", USER_AGENT)
                        .build();
                HttpResponse<String> wikiResp = HTTP_CLIENT.send(wikiReq, HttpResponse.BodyHandlers.ofString());
                if (wikiResp.statusCode() == 200) {
                    limitObj = parseJson(wikiResp.body());
                }
                if (limitObj == null) {
                    Microbot.log(itemName + " limit fetch failed: " + limitResp.statusCode());
                    return null;
                }
            }

            JsonObject limitData = limitObj.has("data") && limitObj.get("data").isJsonObject()
                    ? limitObj.getAsJsonObject("data") : limitObj;
            int limit = 0;
            if (limitData.has("limit") && !limitData.get("limit").isJsonNull()) {
                limit = limitData.get("limit").getAsInt();
            } else if (limitData.has(Integer.toString(itemId)) && limitData.get(Integer.toString(itemId)).isJsonObject()) {
                JsonObject byId = limitData.getAsJsonObject(Integer.toString(itemId));
                if (byId.has("limit") && !byId.get("limit").isJsonNull()) {
                    limit = byId.get("limit").getAsInt();
                }
            } else if (limitData.has("item") && limitData.get("item").isJsonObject()) {
                JsonObject item = limitData.getAsJsonObject("item");
                if (item.has("limit") && !item.get("limit").isJsonNull()) {
                    limit = item.get("limit").getAsInt();
                }
            }
            if (high == 0 || low == 0) {
                Microbot.log(itemName + " missing price info, skipping");
                Microbot.status = "No price";
                return null;
            }
            if (high <= low) {
                Microbot.log(itemName + " margin non-positive, skipping");
                Microbot.status = "Bad margin";
                return null;
            }
            if (highVol < MIN_VOLUME || lowVol < MIN_VOLUME) {
                Microbot.log(itemName + " volume too low, skipping");
                Microbot.status = "Low volume";
                return null;
            }
            if (limit <= 0) {
                Microbot.log(itemName + " limit fetch failed");
                Microbot.status = "No limit";
                return null;
            }
            long now = System.currentTimeMillis();
            long reset = limitResetTimes.getOrDefault(itemId, 0L);
            if (now >= reset) {
                remainingLimits.put(itemId, limit);
                limitResetTimes.put(itemId, now + FOUR_HOURS_MS);
            }
            int remaining = remainingLimits.getOrDefault(itemId, limit);
            if (remaining <= 0) {
                Microbot.log(itemName + " reached trade limit, waiting");
                Microbot.status = "Limit reached";
                return null;
            }

            int coins = getCoins();
            int quantity = Math.min(Math.min(Math.min(limit, MAX_TRADE_LIMIT), remaining), coins / low);
            if (quantity <= 0) {
                Microbot.log("Not enough gp to buy " + itemName);
                Microbot.status = "Insufficient gp";
                return null;
            }
            ActiveOffer offer = new ActiveOffer();
            offer.itemId = itemId;
            offer.buyPrice = low;
            offer.sellPrice = high;
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
                    plugin.addProfit((offer.sellPrice - offer.buyPrice) * offer.quantity);
                    updateRemainingLimit(offer.itemId, offer.quantity);
                    items.offer(offer.itemId);
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
        remainingLimits.clear();
        limitResetTimes.clear();
    }

    private void updateRemainingLimit(int itemId, int qty) {
        remainingLimits.compute(itemId, (k, v) -> {
            if (v == null) return 0;
            int newVal = v - qty;
            return Math.max(newVal, 0);
        });
    }
}