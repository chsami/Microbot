package net.runelite.client.plugins.microbot.pluginscheduler.condition.time.serialization;

import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Type;
import java.time.Duration;

/**
 * Custom adapter for serializing/deserializing java.time.Duration objects
 * This avoids reflection issues with Java modules
 */
@Slf4j
public class DurationAdapter implements JsonSerializer<Duration>, JsonDeserializer<Duration> {
    
    @Override
    public JsonElement serialize(Duration src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("seconds", src.getSeconds());
        jsonObject.addProperty("nanos", src.getNano());
        return jsonObject;
    }
    
    @Override
    public Duration deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) 
            throws JsonParseException {
        try {
            JsonObject jsonObject = json.getAsJsonObject();
            long seconds = jsonObject.get("seconds").getAsLong();
            int nanos = jsonObject.has("nanos") ? jsonObject.get("nanos").getAsInt() : 0;
            return Duration.ofSeconds(seconds, nanos);
        } catch (Exception e) {
            log.error("Error deserializing Duration", e);
            return Duration.ZERO;
        }
    }
}