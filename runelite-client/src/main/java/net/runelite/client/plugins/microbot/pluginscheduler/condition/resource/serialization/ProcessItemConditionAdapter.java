package net.runelite.client.plugins.microbot.pluginscheduler.condition.resource.serialization;

import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.resource.ProcessItemCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.resource.ProcessItemCondition.ItemTracker;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.resource.ProcessItemCondition.TrackingMode;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Adapter for handling serialization and deserialization of ProcessItemCondition objects.
 */
@Slf4j
public class ProcessItemConditionAdapter implements JsonSerializer<ProcessItemCondition>, JsonDeserializer<ProcessItemCondition> {

    @Override
    public JsonElement serialize(ProcessItemCondition src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject result = new JsonObject();
        
        // Add type information
        result.addProperty("type", ProcessItemCondition.class.getName());
        
        // Create data object
        JsonObject data = new JsonObject();
        
        // Add version information
        data.addProperty("version", ProcessItemCondition.getVersion());
        
        // Add specific properties for ProcessItemCondition
        data.addProperty("targetCountMin", src.getTargetCountMin());
        data.addProperty("targetCountMax", src.getTargetCountMax());        
        data.addProperty("trackingMode", src.getTrackingMode().name());
        
        // Serialize sourceItems
        if (src.getSourceItems() != null && !src.getSourceItems().isEmpty()) {
            JsonArray sourceItemsArray = new JsonArray();
            for (ItemTracker item : src.getSourceItems()) {
                JsonObject itemObj = new JsonObject();
                itemObj.addProperty("patternString", item.getItemPattern().pattern());
                itemObj.addProperty("quantityPerProcess", item.getQuantityPerProcess());
                sourceItemsArray.add(itemObj);
            }
            data.add("sourceItems", sourceItemsArray);
        }
        
        // Serialize targetItems
        if (src.getTargetItems() != null && !src.getTargetItems().isEmpty()) {
            JsonArray targetItemsArray = new JsonArray();
            for (ItemTracker item : src.getTargetItems()) {
                JsonObject itemObj = new JsonObject();
                itemObj.addProperty("patternString", item.getItemPattern().pattern());
                itemObj.addProperty("quantityPerProcess", item.getQuantityPerProcess());
                targetItemsArray.add(itemObj);
            }
            data.add("targetItems", targetItemsArray);
        }
        
        // Add data to wrapper
        result.add("data", data);
        
        return result;
    }

    @Override
    public ProcessItemCondition deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) 
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
        
        // Version check
        if (dataObj.has("version")) {
            String version = dataObj.get("version").getAsString();
            if (!version.equals(ProcessItemCondition.getVersion())) {                
                throw new JsonParseException("Version mismatch in ProcessItemCondition: expected " +
                        ProcessItemCondition.getVersion() + ", got " + version);
            }
        }
        
        // Extract basic properties
        int targetCountMin = dataObj.has("targetCountMin") ? dataObj.get("targetCountMin").getAsInt() : 1;
        int targetCountMax = dataObj.has("targetCountMax") ? dataObj.get("targetCountMax").getAsInt() : targetCountMin;
        
        // Deserialize tracking mode
        TrackingMode trackingMode = TrackingMode.EITHER; // Default
        if (dataObj.has("trackingMode")) {
            try {
                trackingMode = TrackingMode.valueOf(dataObj.get("trackingMode").getAsString());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid tracking mode: {}", dataObj.get("trackingMode").getAsString());
            }
        }
        
        // Deserialize sourceItems
        List<ItemTracker> sourceItems = new ArrayList<>();
        if (dataObj.has("sourceItems")) {
            JsonArray sourceItemsArray = dataObj.getAsJsonArray("sourceItems");
            for (JsonElement element : sourceItemsArray) {
                JsonObject itemObj = element.getAsJsonObject();
                String patternString = itemObj.get("patternString").getAsString();
                int quantity = itemObj.get("quantityPerProcess").getAsInt();
                
                // Create ItemTracker directly since its constructor needs pattern
                ItemTracker tracker = new ItemTracker(patternString, quantity);
                sourceItems.add(tracker);
            }
        }
        
        // Deserialize targetItems
        List<ItemTracker> targetItems = new ArrayList<>();
        if (dataObj.has("targetItems")) {
            JsonArray targetItemsArray = dataObj.getAsJsonArray("targetItems");
            for (JsonElement element : targetItemsArray) {
                JsonObject itemObj = element.getAsJsonObject();
                String patternString = itemObj.get("patternString").getAsString();
                int quantity = itemObj.get("quantityPerProcess").getAsInt();
                
                // Create ItemTracker directly since its constructor needs pattern
                ItemTracker tracker = new ItemTracker(patternString, quantity);
                targetItems.add(tracker);
            }
        }
        
        // Create the condition
        return ProcessItemCondition.builder()
                .sourceItems(sourceItems)
                .targetItems(targetItems)
                .trackingMode(trackingMode)
                .targetCountMin(targetCountMin)
                .targetCountMax(targetCountMax)
                .build();
                    
       
    }
}