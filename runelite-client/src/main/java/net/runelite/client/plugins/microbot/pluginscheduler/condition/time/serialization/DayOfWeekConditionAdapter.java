package net.runelite.client.plugins.microbot.pluginscheduler.condition.time.serialization;

import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.DayOfWeekCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.IntervalCondition;

import java.lang.reflect.Type;
import java.time.DayOfWeek;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Serializes and deserializes DayOfWeekCondition objects
 */
@Slf4j
public class DayOfWeekConditionAdapter implements JsonSerializer<DayOfWeekCondition>, JsonDeserializer<DayOfWeekCondition> {
    @Override
    public JsonElement serialize(DayOfWeekCondition src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject json = new JsonObject();
        
        // Add type information
        json.addProperty("type", DayOfWeekCondition.class.getName());
        
        // Create data object
        JsonObject data = new JsonObject();
        
        // Serialize active days as an array of day values
        JsonArray daysArray = new JsonArray();
        for (DayOfWeek day : src.getActiveDays()) {
            daysArray.add(day.getValue()); // getValue() returns 1-7 for MON-SUN
        }
        data.add("activeDays", daysArray);
        data.addProperty("version", src.getVersion());
        data.addProperty("maximumNumberOfRepeats", src.getMaximumNumberOfRepeats());
        data.addProperty("maxRepeatsPerDay", src.getMaxRepeatsPerDay());
        data.addProperty("maxRepeatsPerWeek", src.getMaxRepeatsPerWeek());
        
        // Serialize the interval condition if it exists
        Optional<IntervalCondition> intervalCondition = src.getIntervalCondition();
        if (intervalCondition.isPresent()) {
            // Use a separate serializer for the interval condition
            JsonElement intervalJson = context.serialize(intervalCondition.get(), IntervalCondition.class);
            data.add("intervalCondition", intervalJson);
        }
        
        // Add data to wrapper
        json.add("data", data);
        
        return json;
    }
    
    @Override
    public DayOfWeekCondition deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) 
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
        
        Set<DayOfWeek> activeDays = EnumSet.noneOf(DayOfWeek.class);
        

        if (dataObj.has("version")) {
            String version = dataObj.get("version").getAsString();
            if (!version.equals(DayOfWeekCondition.getVersion())) {
                throw new JsonParseException("Version mismatch: expected " + DayOfWeekCondition.getVersion() + 
                        ", got " + version);                                        
            }
        }
        // Parse active days
        if (dataObj.has("activeDays")) {
            JsonArray daysArray = dataObj.getAsJsonArray("activeDays");
            for (JsonElement element : daysArray) {
                int dayValue = element.getAsInt();
                // DayOfWeek.of expects 1-7 for MON-SUN
                activeDays.add(DayOfWeek.of(dayValue));
            }
        }
        
        // Get maximum number of repeats
        long maximumNumberOfRepeats = 0;
        if (dataObj.has("maximumNumberOfRepeats")) {
            maximumNumberOfRepeats = dataObj.get("maximumNumberOfRepeats").getAsLong();
        }
        
        // Get maximum number of repeats per day
        long maxRepeatsPerDay = 0;
        if (dataObj.has("maxRepeatsPerDay")) {
            maxRepeatsPerDay = dataObj.get("maxRepeatsPerDay").getAsLong();
        }
        
        // Get maximum number of repeats per week (new field)
        long maxRepeatsPerWeek = 0;
        if (dataObj.has("maxRepeatsPerWeek")) {
            maxRepeatsPerWeek = dataObj.get("maxRepeatsPerWeek").getAsLong();
        }
        
        // Create the day of week condition with all limits
        DayOfWeekCondition condition = new DayOfWeekCondition(maximumNumberOfRepeats, maxRepeatsPerDay, maxRepeatsPerWeek, activeDays);
        
        // If there's an interval condition, deserialize and add it
        if (dataObj.has("intervalCondition")) {
            JsonElement intervalJson = dataObj.get("intervalCondition");
            IntervalCondition intervalCondition = context.deserialize(intervalJson, IntervalCondition.class);
            condition.setIntervalCondition(intervalCondition);
        }
        
        return condition;
       
    }
}