package net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.serialization;

import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.OrCondition;

import java.lang.reflect.Type;

/**
 * Serializes and deserializes LogicalCondition objects
 */
@Slf4j
public class LogicalConditionAdapter implements JsonSerializer<LogicalCondition>, JsonDeserializer<LogicalCondition> {
    @Override
    public JsonElement serialize(LogicalCondition src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject json = new JsonObject();
        
        // Add class info to distinguish different logical conditions
        json.addProperty("class", src.getClass().getName());
        
        // Serialize the conditions with proper type wrapping
        JsonArray conditionsArray = new JsonArray();
        for (Condition condition : src.getConditions()) {
            // Create a properly typed wrapper for each condition
            JsonObject typedCondition = new JsonObject();
            typedCondition.addProperty("type", condition.getClass().getName());
            
            // Serialize the condition data and add it to the wrapper
            JsonElement conditionData = context.serialize(condition);
            typedCondition.add("data", conditionData);
            
            conditionsArray.add(typedCondition);
        }        
        json.add("conditions", conditionsArray);
        
        return json;
    }
    
    @Override
    public LogicalCondition deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) 
            throws JsonParseException {
        try {
            JsonObject jsonObject = json.getAsJsonObject();
            LogicalCondition logicalCondition;
            
            // Determine the concrete class using exact class name matching
            if (jsonObject.has("class")) {
                String className = jsonObject.get("class").getAsString();
                
                // Use exact class name matching
                if (className.endsWith(".OrCondition")) {
                    logicalCondition = new OrCondition();
                } else if (className.endsWith(".AndCondition")) {
                    logicalCondition = new AndCondition();
                } else {
                    // Default fallback
                    log.warn("Unknown logical condition class: {}, defaulting to AndCondition", className);
                    logicalCondition = new AndCondition();
                }
            } else {
                // Default if no class info
                logicalCondition = new AndCondition();
            }
            
            // Handle conditions
            if (jsonObject.has("conditions")) {                
                JsonArray conditionsArray = jsonObject.getAsJsonArray("conditions");
                for (JsonElement element : conditionsArray) {
                    try {
                        // Check if this is a wrapped condition from ConditionTypeAdapter
                        if (element.isJsonObject()) {
                            JsonObject conditionObj = element.getAsJsonObject();
                            
                            // Handle the typed wrapper structure from ConditionTypeAdapter
                            if (conditionObj.has("type") && conditionObj.has("data")) {
                                // This is the format from ConditionTypeAdapter
                                Condition condition = context.deserialize(conditionObj, Condition.class);
                                if (condition != null) {
                                    logicalCondition.addCondition(condition);
                                }
                            } else if (conditionObj.has("data")) {
                                // Try to get the condition directly from the data field
                                Condition condition = context.deserialize(conditionObj.get("data"), Condition.class);
                                if (condition != null) {
                                    logicalCondition.addCondition(condition);
                                }
                            } else {
                                // Try to deserialize directly
                                Condition condition = context.deserialize(element, Condition.class);
                                if (condition != null) {
                                    logicalCondition.addCondition(condition);
                                }
                            }
                        } else {
                            // Try to deserialize directly
                            Condition condition = context.deserialize(element, Condition.class);
                            if (condition != null) {
                                logicalCondition.addCondition(condition);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Failed to deserialize a condition in logical condition", e);
                    }
                }
            }
            
            return logicalCondition;
        } catch (Exception e) {
            log.error("Error deserializing LogicalCondition", e);
            // Return empty AndCondition on error
            return new AndCondition();
        }
    }
}