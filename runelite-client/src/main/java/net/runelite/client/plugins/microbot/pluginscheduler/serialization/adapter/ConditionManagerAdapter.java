package net.runelite.client.plugins.microbot.pluginscheduler.serialization.adapter;

import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ConditionManager;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.TimeCondition;

import java.lang.reflect.Type;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles serialization and deserialization of ConditionManager
 * with improved timezone handling for time-based conditions
 */
@Slf4j
public class ConditionManagerAdapter implements JsonSerializer<ConditionManager>, JsonDeserializer<ConditionManager> {
    
    @Override
    public JsonElement serialize(ConditionManager src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject result = new JsonObject();
        
        // Add type information to identify whether this is a start or stop condition manager
        // This will be detected and used during deserialization
        result.addProperty("requireAll", src.requiresAll());
        
        // Serialize plugin-defined logical condition (if present)
        LogicalCondition pluginCondition = src.getPluginCondition();
        if (pluginCondition != null && !pluginCondition.getConditions().isEmpty()) {
            result.add("pluginLogicalCondition", context.serialize(pluginCondition));
        }
        // Serialize user-defined logical condition
        LogicalCondition userCondition = src.getUserLogicalCondition();
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
        
        // Set requireAll based on serialized value
        if (jsonObject.has("requireAll")) {
            boolean requireAll = jsonObject.get("requireAll").getAsBoolean();
            if (requireAll) {
                manager.setRequireAll();
            } else {
                manager.setRequireAny();
            }
        }
        
        // Handle pluginLogicalCondition if present
        if (jsonObject.has("pluginLogicalCondition")) {
            JsonObject pluginLogicalObj = jsonObject.getAsJsonObject("pluginLogicalCondition");
            
            // Only process if there are actual conditions
            if (pluginLogicalObj.has("conditions") && 
                pluginLogicalObj.getAsJsonArray("conditions").size() > 0) {
                LogicalCondition logicalCondition = context.deserialize(
                    pluginLogicalObj, LogicalCondition.class);
                if (logicalCondition != null) {
                    manager.setPluginCondition(logicalCondition);
                }
            }
        }
        
        // Handle userLogicalCondition properly
        if (jsonObject.has("userLogicalCondition")) {
            JsonObject userLogicalObj = jsonObject.getAsJsonObject("userLogicalCondition");
            
            // Handle case where userLogicalCondition might have an empty conditions array
            if (userLogicalObj.has("conditions")) {
                
                JsonArray conditionsArray = userLogicalObj.getAsJsonArray("conditions");
                if (conditionsArray.size() > 0) {
                    // Only process if there are actual conditions
                    LogicalCondition logicalCondition = context.deserialize(
                        userLogicalObj, LogicalCondition.class);
                    if (logicalCondition != null) {
                        manager.setUserLogicalCondition(logicalCondition);
                    
                    }
                }
            }
        }
        
        
        
        return manager;
    }
    
  
}
