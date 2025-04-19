package net.runelite.client.plugins.microbot.pluginscheduler.condition.resource.serialization;

import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.resource.ProcessItemCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.resource.ProcessItemCondition.ItemTracker;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.resource.ProcessItemCondition.TrackingMode;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for handling serialization and deserialization of ProcessItemCondition objects.
 */
@Slf4j
public class ProcessItemConditionAdapter implements JsonSerializer<ProcessItemCondition>, JsonDeserializer<ProcessItemCondition> {

    @Override
    public JsonElement serialize(ProcessItemCondition src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject result = new JsonObject();
        
        // Store tracking mode
        result.addProperty("trackingMode", src.getTrackingMode().name());
        
        // Store count targets
        result.addProperty("targetCountMin", src.getTargetCountMin());
        result.addProperty("targetCountMax", src.getTargetCountMax());
        result.addProperty("currentTargetCount", src.getCurrentTargetCount());
        result.addProperty("processedCount", src.getProcessedCount());
        result.addProperty("satisfied", src.isSatisfied());
        
        // Serialize source items
        JsonArray sourceItems = new JsonArray();
        for (ItemTracker tracker : src.getSourceItems()) {
            JsonObject item = new JsonObject();
            item.addProperty("itemName", tracker.getItemName());
            item.addProperty("quantityPerProcess", tracker.getQuantityPerProcess());
            sourceItems.add(item);
        }
        result.add("sourceItems", sourceItems);
        
        // Serialize target items
        JsonArray targetItems = new JsonArray();
        for (ItemTracker tracker : src.getTargetItems()) {
            JsonObject item = new JsonObject();
            item.addProperty("itemName", tracker.getItemName());
            item.addProperty("quantityPerProcess", tracker.getQuantityPerProcess());
            targetItems.add(item);
        }
        result.add("targetItems", targetItems);
        
        return result;
    }

    @Override
    public ProcessItemCondition deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) 
            throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        
        // Parse tracking mode
        TrackingMode trackingMode = TrackingMode.EITHER; // Default
        if (jsonObject.has("trackingMode")) {
            try {
                trackingMode = TrackingMode.valueOf(jsonObject.get("trackingMode").getAsString());
            } catch (IllegalArgumentException e) {
                log.warn("Unknown tracking mode: {}", jsonObject.get("trackingMode").getAsString());
            }
        }
        
        // Parse target counts
        int targetCountMin = jsonObject.has("targetCountMin") ? jsonObject.get("targetCountMin").getAsInt() : 1;
        int targetCountMax = jsonObject.has("targetCountMax") ? jsonObject.get("targetCountMax").getAsInt() : targetCountMin;
        
        // Parse source items
        List<ItemTracker> sourceItems = new ArrayList<>();
        if (jsonObject.has("sourceItems")) {
            JsonArray sourceItemsArray = jsonObject.getAsJsonArray("sourceItems");
            for (JsonElement element : sourceItemsArray) {
                JsonObject itemObj = element.getAsJsonObject();
                String itemName = itemObj.get("itemName").getAsString();
                int quantity = itemObj.has("quantityPerProcess") ? 
                        itemObj.get("quantityPerProcess").getAsInt() : 1;
                
                sourceItems.add(new ItemTracker(itemName, quantity));
            }
        }
        
        // Parse target items
        List<ItemTracker> targetItems = new ArrayList<>();
        if (jsonObject.has("targetItems")) {
            JsonArray targetItemsArray = jsonObject.getAsJsonArray("targetItems");
            for (JsonElement element : targetItemsArray) {
                JsonObject itemObj = element.getAsJsonObject();
                String itemName = itemObj.get("itemName").getAsString();
                int quantity = itemObj.has("quantityPerProcess") ? 
                        itemObj.get("quantityPerProcess").getAsInt() : 1;
                
                targetItems.add(new ItemTracker(itemName, quantity));
            }
        }
        
        // Create the condition using builder
        return ProcessItemCondition.builder()
                .sourceItems(sourceItems)
                .targetItems(targetItems)
                .trackingMode(trackingMode)
                .targetCountMin(targetCountMin)
                .targetCountMax(targetCountMax)
                .build();
    }
}