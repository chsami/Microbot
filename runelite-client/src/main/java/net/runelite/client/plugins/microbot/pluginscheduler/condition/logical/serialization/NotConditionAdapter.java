package net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.serialization;

import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.NotCondition;

import java.lang.reflect.Type;

/**
 * Serializes and deserializes NotCondition objects
 */
@Slf4j
public class NotConditionAdapter implements JsonSerializer<NotCondition>, JsonDeserializer<NotCondition> {
    @Override
    public JsonElement serialize(NotCondition src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject json = new JsonObject();
        
        // Add class info
        json.addProperty("class", src.getClass().getName());
        
        // NotCondition only has one inner condition
        Condition innerCondition = src.getCondition();
        
        // Create a properly typed wrapper for the inner condition
        JsonObject typedCondition = new JsonObject();
        typedCondition.addProperty("type", innerCondition.getClass().getName());
        
        // Serialize the condition data and add it to the wrapper
        JsonElement conditionData = context.serialize(innerCondition);
        typedCondition.add("data", conditionData);
        
        // Add the inner condition to the object
        json.add("condition", typedCondition);
        
        return json;
    }
    
    @Override
    public NotCondition deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) 
            throws JsonParseException {
        try {
            JsonObject jsonObject = json.getAsJsonObject();
            
            // Handle inner condition
            if (jsonObject.has("condition")) {
                log.info("Deserializing NOT condition inner condition: {}", 
                         jsonObject.get("condition").toString());
                         
                JsonElement element = jsonObject.get("condition");
                Condition innerCondition = context.deserialize(element, Condition.class);
                
                if (innerCondition != null) {
                    return new NotCondition(innerCondition);
                } else {
                    log.error("Failed to deserialize inner condition for NotCondition");
                }
            } else {
                log.error("NotCondition JSON missing 'condition' field");
            }
            
            // If we reach here, something went wrong
            throw new JsonParseException("Invalid NotCondition JSON format");
        } catch (Exception e) {
            log.error("Error deserializing NotCondition", e);
            throw new JsonParseException("Failed to deserialize NotCondition: " + e.getMessage());
        }
    }
}