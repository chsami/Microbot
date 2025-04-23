package net.runelite.client.plugins.microbot.pluginscheduler.condition.resource.serialization;

import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.resource.BankItemCountCondition;

import java.lang.reflect.Type;

/**
 * Adapter for handling serialization and deserialization of BankItemCountCondition objects.
 */
@Slf4j
public class BankItemCountConditionAdapter implements JsonSerializer<BankItemCountCondition>, JsonDeserializer<BankItemCountCondition> {

    @Override
    public JsonElement serialize(BankItemCountCondition src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject result = new JsonObject();
        
        // Add specific properties for BankItemCountCondition
        result.addProperty("itemName", src.getItemName());
        result.addProperty("targetCountMin", src.getTargetCountMin());
        result.addProperty("targetCountMax", src.getTargetCountMax());
        result.addProperty("currentTargetCount", src.getCurrentTargetCount());
        result.addProperty("currentItemCount", src.getCurrentItemCount());
        result.addProperty("satisfied", src.isSatisfied());
        
        return result;
    }

    @Override
    public BankItemCountCondition deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) 
            throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        
        // Extract basic properties
        String itemName = jsonObject.has("itemName") ? jsonObject.get("itemName").getAsString() : "";
        int targetCountMin = jsonObject.has("targetCountMin") ? jsonObject.get("targetCountMin").getAsInt() : 1;
        int targetCountMax = jsonObject.has("targetCountMax") ? jsonObject.get("targetCountMax").getAsInt() : targetCountMin;
        
        // Create the condition
        return BankItemCountCondition.builder()
                .itemName(itemName)
                .targetCountMin(targetCountMin)
                .targetCountMax(targetCountMax)
                .build();
    }
}