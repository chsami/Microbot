package net.runelite.client.plugins.microbot.geflipper;

import lombok.extern.slf4j.Slf4j;

import net.runelite.client.plugins.microbot.Microbot;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
public class Limits {
    private static final long FOUR_HOURS_MS = TimeUnit.HOURS.toMillis(4);
    private static final String LIMIT_API = "https://prices.runescape.wiki/api/v1/osrs/limit?id=";
    private static final String USER_AGENT = "Microbot GE Flipper";
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private final Map<Integer, Integer> remainingLimits = new HashMap<>();
    private final Map<Integer, Long> resetTimes = new HashMap<>();

    private final Map<Integer, Integer> limitCache = new HashMap<>();

    public Integer fetchLimit(int itemId, String itemName) {
        Integer cached = limitCache.get(itemId);
        if (cached != null) {
            return cached;
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(LIMIT_API + itemId))
                .header("User-Agent", USER_AGENT)
                .build();
        try {
            HttpResponse<String> resp = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                Microbot.log(itemName + " limit fetch failed: " + resp.statusCode());
                return null;
            }
            JsonReader reader = new JsonReader(new StringReader(resp.body()));
            reader.setLenient(true);
            JsonObject obj = new JsonParser().parse(reader).getAsJsonObject();
            JsonObject data = obj.getAsJsonObject("data");
            if (data == null || data.get("limit") == null || data.get("limit").isJsonNull()) {
                Microbot.log(itemName + " limit fetch failed");
                return null;
            }
            int limit = data.get("limit").getAsInt();
            limitCache.put(itemId, limit);
            return limit;
        } catch (Exception ex) {
            log.error("Limit fetch error", ex);
            return null;
        }
    }

    public int getRemaining(int itemId, int limit) {
        long now = System.currentTimeMillis();
        long reset = resetTimes.getOrDefault(itemId, 0L);
        if (now >= reset) {
            remainingLimits.put(itemId, limit);
            resetTimes.put(itemId, now + FOUR_HOURS_MS);
        }
        return remainingLimits.getOrDefault(itemId, limit);
    }

    public void reduceRemaining(int itemId, int qty) {
        remainingLimits.compute(itemId, (k, v) -> {
            if (v == null) return 0;
            int newVal = v - qty;
            return Math.max(newVal, 0);
        });
    }

    public void clear() {
        remainingLimits.clear();
        resetTimes.clear();
        limitCache.clear();
    }
}