package net.runelite.client.plugins.microbot.pluginscheduler.condition.resource.serialization;

import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.resource.InventoryItemCountCondition;

import java.lang.reflect.Type;

/**
 * Adapter for handling serialization and deserialization of InventoryItemCountCondition objects.
 */
@Slf4j
public class InventoryItemCountConditionAdapter implements JsonSerializer<InventoryItemCountCondition>, JsonDeserializer<InventoryItemCountCondition> {

    @Override
    public JsonElement serialize(InventoryItemCountCondition src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject result = new JsonObject();
        
        // Add type information
        result.addProperty("type", InventoryItemCountCondition.class.getName());
        
        // Create data object
        JsonObject data = new JsonObject();
        
        // Add version information
        data.addProperty("version", InventoryItemCountCondition.getVersion());
        
        // Add specific properties for InventoryItemCountCondition
        data.addProperty("itemName", src.getItemName());
        data.addProperty("targetCountMin", src.getTargetCountMin());
        data.addProperty("targetCountMax", src.getTargetCountMax());
        data.addProperty("includeNoted", src.isIncludeNoted());
        data.addProperty("currentTargetCount", src.getCurrentTargetCount());
        data.addProperty("currentItemCount", src.getCurrentItemCount());
        
        // Add data to wrapper
        result.add("data", data);
        
        return result;
    }

    @Override
    public InventoryItemCountCondition deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) 
            throws JsonParseException {
        try {
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
                if (!version.equals(InventoryItemCountCondition.getVersion())) {
                    log.warn("Version mismatch in InventoryItemCountCondition: expected {}, got {}", 
                            InventoryItemCountCondition.getVersion(), version);
                }
            }
            
            // Extract basic properties
            String itemName = dataObj.has("itemName") ? dataObj.get("itemName").getAsString() : "";
            int targetCountMin = dataObj.has("targetCountMin") ? dataObj.get("targetCountMin").getAsInt() : 1;
            int targetCountMax = dataObj.has("targetCountMax") ? dataObj.get("targetCountMax").getAsInt() : targetCountMin;
            boolean includeNoted = dataObj.has("includeNoted") && dataObj.get("includeNoted").getAsBoolean();
            
            // Create the condition
            return InventoryItemCountCondition.builder()
                    .itemName(itemName)
                    .targetCountMin(targetCountMin)
                    .targetCountMax(targetCountMax)
                    .includeNoted(includeNoted)
                    .build();
        } catch (Exception e) {
            log.error("Error deserializing InventoryItemCountCondition", e);
            // Return a default condition on error
            return InventoryItemCountCondition.builder()
                    .itemName("Unknown")
                    .targetCountMin(1)
                    .targetCountMax(1)
                    .includeNoted(false)
                    .build();
        }
    }
}