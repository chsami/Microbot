package net.runelite.client.plugins.microbot.geflipper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import lombok.extern.slf4j.Slf4j;

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
    // Trade limits fetched from GE Tracker; no OSRS Wiki calls
    private static final String LIMIT_API = "https://www.ge-tracker.com/api/items/";
    private static final String USER_AGENT = "Microbot GE Flipper";
    private static final long FOUR_HOURS_MS = TimeUnit.HOURS.toMillis(4);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    private final Map<Integer, Integer> remainingLimits = new HashMap<>();
    private final Map<Integer, Long> resetTimes = new HashMap<>();

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

    public Integer fetchLimit(int itemId) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(LIMIT_API + itemId))
                    .header("User-Agent", USER_AGENT)
                    .build();
            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.info("Limit fetch failed: {}", resp.statusCode());
                return null;
            }
            JsonObject obj = parseJson(resp.body());
            if (obj == null || !obj.has("data")) {
                return null;
            }
            JsonObject data = obj.getAsJsonObject("data");
            if (data.has("limit") && !data.get("limit").isJsonNull()) {
                return data.get("limit").getAsInt();
            }
        } catch (Exception ex) {
            log.error("Failed to fetch limit for {}", itemId, ex);
        }
        return null;
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
    }
}