package net.runelite.client.plugins.microbot.pluginscheduler.condition.time.serialization;

import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.SingleTriggerTimeCondition;

import java.lang.reflect.Type;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Custom serializer/deserializer for SingleTriggerTimeCondition
 */
@Slf4j
public class SingleTriggerTimeConditionAdapter implements JsonSerializer<SingleTriggerTimeCondition>, JsonDeserializer<SingleTriggerTimeCondition> {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ISO_TIME;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_DATE;
    
    @Override
    public JsonElement serialize(SingleTriggerTimeCondition src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject json = new JsonObject();
        
        // Add type information
        json.addProperty("type", SingleTriggerTimeCondition.class.getName());
        
        // Create data object
        JsonObject data = new JsonObject();
        
        // Get source timezone
        ZoneId sourceZone = ZoneId.systemDefault();
                        
        // Store times in UTC, converting from source timezone
        LocalDate today = LocalDate.now();
        
        // Convert start time to UTC        
        ZonedDateTime targetUtc = src.getTargetTime().withZoneSameInstant(ZoneId.of("UTC"));
        data.addProperty("version", src.getVersion());
        data.addProperty("targetTime", targetUtc.toLocalTime().format(TIME_FORMAT));
        data.addProperty("targetDate", targetUtc.toLocalDate().format(DATE_FORMAT));
        
        // Mark that these are UTC times for future compatibility
        data.addProperty("timeFormat", "UTC");
        // Store trigger state
        data.addProperty("maximumNumberOfRepeats", src.getMaximumNumberOfRepeats());
        
        // Add data to wrapper
        json.add("data", data);
        
        return json;
    }
    
    @Override
    public SingleTriggerTimeCondition deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) 
            throws JsonParseException {
      
        JsonObject jsonObject = json.getAsJsonObject();
        
        // Check if this is a typed format or direct format
        JsonObject dataObj;
        if (jsonObject.has("type") && jsonObject.has("data")) {
            dataObj = jsonObject.getAsJsonObject("data");
        } else {
            // Legacy format - use the object directly
            dataObj = jsonObject;
        }
        
        // Get timezone (fallback to system default)
        ZoneId zoneId = ZoneId.systemDefault();
        if (dataObj.has("version")) {
            if (!dataObj.get("version").getAsString().equals(SingleTriggerTimeCondition.getVersion())) {
                throw new JsonParseException("Version mismatch: expected " + SingleTriggerTimeCondition.getVersion() + 
                        ", got " + dataObj.get("version").getAsString());
            }
        }
    
        // Parse time values
        LocalTime serializedStartTime = LocalTime.parse(dataObj.get("targetTime").getAsString(), TIME_FORMAT);
        // Parse date values
        LocalDate serializedStartDate = LocalDate.parse(dataObj.get("targetDate").getAsString(), DATE_FORMAT);
        // Convert to ZonedDateTime
        ZonedDateTime targetZoned = ZonedDateTime.of(serializedStartDate, serializedStartTime, zoneId);
        // Create condition
        SingleTriggerTimeCondition condition = new SingleTriggerTimeCondition(targetZoned);
        
        return condition;
        
    }
}