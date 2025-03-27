package net.runelite.client.plugins.microbot.pluginscheduler.serialization;

import com.google.gson.*;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ConditionManager;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles serialization and deserialization of ConditionManager
 */
public class ConditionManagerAdapter implements JsonSerializer<ConditionManager>, JsonDeserializer<ConditionManager> {
    
    @Override
    public JsonElement serialize(ConditionManager src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject result = new JsonObject();
        
        // Only serialize user-defined logical condition
        LogicalCondition userCondition = src.getUserCondition();
        if (userCondition != null) {
            result.add("userLogicalCondition", context.serialize(userCondition));
        }
        
        return result;
    }
    
    @Override
    public ConditionManager deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) 
                throws JsonParseException {
        ConditionManager manager = new ConditionManager();
        
        if (!json.isJsonObject()) {
            return manager; // Return empty manager for non-object elements
        }
        
        JsonObject jsonObject = json.getAsJsonObject();
        
        // Handle userLogicalCondition properly
        if (jsonObject.has("userLogicalCondition")) {
            JsonObject userLogicalObj = jsonObject.getAsJsonObject("userLogicalCondition");
            
            // Handle case where userLogicalCondition might have an empty conditions array
            if (userLogicalObj.has("conditions")) {
                JsonArray conditionsArray = userLogicalObj.getAsJsonArray("conditions");
                if (conditionsArray.size() > 0) {
                    // Only process if there are actual conditions
                    LogicalCondition logicalCondition = context.deserialize(userLogicalObj, LogicalCondition.class);
                    if (logicalCondition != null) {
                        manager.setUserLogicalCondition(logicalCondition);
                    }
                }
            }
        }
        
        // Handle direct conditions array if present
        if (jsonObject.has("conditions")) {
            JsonArray conditionsArray = jsonObject.getAsJsonArray("conditions");
            List<Condition> conditions = new ArrayList<>();
            
            for (JsonElement element : conditionsArray) {
                Condition condition = context.deserialize(element, Condition.class);
                if (condition != null) {
                    conditions.add(condition);
                }
            }
            
            // Add all successfully deserialized conditions
            for (Condition condition : conditions) {
                manager.addCondition(condition);
            }
        }
        
        // Handle requireAll property if present
        if (jsonObject.has("requireAll")) {
            boolean requireAll = jsonObject.get("requireAll").getAsBoolean();
            if (requireAll) {
                manager.setRequireAll();
            } else {
                manager.setRequireAny();
            }
        }
        
        return manager;
    }
}
