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
        
        // Add specific properties for InventoryItemCountCondition
        result.addProperty("itemName", src.getItemName());
        result.addProperty("targetCountMin", src.getTargetCountMin());
        result.addProperty("targetCountMax", src.getTargetCountMax());
        result.addProperty("currentTargetCount", src.getCurrentTargetCount());
        result.addProperty("currentItemCount", src.getCurrentItemCount());
        result.addProperty("includeNoted", src.isIncludeNoted());
        result.addProperty("satisfied", src.isSatisfied());
        
        return result;
    }

    @Override
    public InventoryItemCountCondition deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) 
            throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        
        // Extract basic properties
        String itemName = jsonObject.has("itemName") ? jsonObject.get("itemName").getAsString() : "";
        int targetCountMin = jsonObject.has("targetCountMin") ? jsonObject.get("targetCountMin").getAsInt() : 1;
        int targetCountMax = jsonObject.has("targetCountMax") ? jsonObject.get("targetCountMax").getAsInt() : targetCountMin;
        boolean includeNoted = jsonObject.has("includeNoted") && jsonObject.get("includeNoted").getAsBoolean();
        
        // Create the condition
        return InventoryItemCountCondition.builder()
                .itemName(itemName)
                .targetCountMin(targetCountMin)
                .targetCountMax(targetCountMax)
                .includeNoted(includeNoted)
                .build();
    }
}