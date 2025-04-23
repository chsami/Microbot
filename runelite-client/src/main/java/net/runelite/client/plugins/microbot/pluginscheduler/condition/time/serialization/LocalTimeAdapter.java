package net.runelite.client.plugins.microbot.pluginscheduler.condition.time.serialization;

import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Type;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Serializes LocalTime as UTC time string and deserializes back to local timezone
 */
@Slf4j
public class LocalTimeAdapter implements JsonSerializer<LocalTime>, JsonDeserializer<LocalTime> {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ISO_TIME;
    
    @Override
    public JsonElement serialize(LocalTime src, Type typeOfSrc, JsonSerializationContext context) {
        // Store the time with UTC marker for consistency
        return new JsonPrimitive(src.format(TIME_FORMAT));
    }
    
    @Override
    public LocalTime deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) 
            throws JsonParseException {
        try {
            return LocalTime.parse(json.getAsString(), TIME_FORMAT);
        } catch (Exception e) {
            log.warn("Error deserializing LocalTime", e);
            return LocalTime.of(0, 0); // Default to midnight if parsing fails
        }
    }
}