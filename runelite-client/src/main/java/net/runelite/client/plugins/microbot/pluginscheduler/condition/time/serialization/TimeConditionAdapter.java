package net.runelite.client.plugins.microbot.pluginscheduler.condition.time.serialization;
import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.DayOfWeekCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.IntervalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.SingleTriggerTimeCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.TimeCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.TimeWindowCondition;

import java.lang.reflect.Type;
import java.util.Map;

// Then create a new TimeConditionAdapter.java class:
@Slf4j
public class TimeConditionAdapter implements JsonDeserializer<TimeCondition> {
    @Override
    public TimeCondition deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) 
            throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        
        // Handle typed wrapper format
        if (jsonObject.has("type") && jsonObject.has("data")) {
            String type = jsonObject.get("type").getAsString();
            JsonObject data = jsonObject.getAsJsonObject("data");
            
            // Create a new object with the same structure as expected by type-specific deserializers
            JsonObject unwrappedJson = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : data.entrySet()) {
                unwrappedJson.add(entry.getKey(), entry.getValue());
            }
            
            // Determine the concrete type from the type field
            if (type.endsWith("IntervalCondition")) {
                return context.deserialize(unwrappedJson, IntervalCondition.class);
            } else if (type.endsWith("SingleTriggerTimeCondition")) {
                return context.deserialize(unwrappedJson, SingleTriggerTimeCondition.class);
            } else if (type.endsWith("TimeWindowCondition")) {
                return context.deserialize(unwrappedJson, TimeWindowCondition.class);
            } else if (type.endsWith("DayOfWeekCondition")) {
                return context.deserialize(unwrappedJson, DayOfWeekCondition.class);
            }
        }
        
        // Legacy format - determine type from properties
        if (jsonObject.has("intervalSeconds")) {
            return context.deserialize(jsonObject, IntervalCondition.class);
        } else if (jsonObject.has("targetTime")) {
            return context.deserialize(jsonObject, SingleTriggerTimeCondition.class);
        } else if (jsonObject.has("startTime") && jsonObject.has("endTime")) {
            return context.deserialize(jsonObject, TimeWindowCondition.class);
        } else if (jsonObject.has("activeDays")) {
            return context.deserialize(jsonObject, DayOfWeekCondition.class);
        }
        
        throw new JsonParseException("Unknown TimeCondition type");
    }
}