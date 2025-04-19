package net.runelite.client.plugins.microbot.pluginscheduler.condition.time.serialization;

import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Serializes/deserializes LocalDate with ISO format
 */
@Slf4j
public class LocalDateAdapter implements JsonSerializer<LocalDate>, JsonDeserializer<LocalDate> {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_DATE;
    
    @Override
    public JsonElement serialize(LocalDate src, Type typeOfSrc, JsonSerializationContext context) {
        return new JsonPrimitive(src.format(DATE_FORMAT));
    }
    
    @Override
    public LocalDate deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) 
            throws JsonParseException {
        try {
            return LocalDate.parse(json.getAsString(), DATE_FORMAT);
        } catch (Exception e) {
            log.warn("Error deserializing LocalDate", e);
            return LocalDate.now(); // Default to today if parsing fails
        }
    }
}