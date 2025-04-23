package net.runelite.client.plugins.microbot.pluginscheduler.condition.resource.serialization;

import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.resource.LootItemCondition;

import java.lang.reflect.Type;

/**
 * Adapter for handling serialization and deserialization of LootItemCondition objects.
 */
@Slf4j
public class LootItemConditionAdapter implements JsonSerializer<LootItemCondition>, JsonDeserializer<LootItemCondition> {

    @Override
    public JsonElement serialize(LootItemCondition src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject result = new JsonObject();
        
        // Add specific properties for LootItemCondition
        result.addProperty("itemName", src.getItemName());
        result.addProperty("targetAmountMin", src.getTargetAmountMin());
        result.addProperty("targetAmountMax", src.getTargetAmountMax());
        result.addProperty("currentTargetAmount", src.getCurrentTargetAmount());
        result.addProperty("currentTrackedCount", src.getCurrentTrackedCount());
        result.addProperty("includeNoted", src.isIncludeNoted());
        result.addProperty("includeNoneOwner", src.isIncludeNoneOwner());
        
        return result;
    }

    @Override
    public LootItemCondition deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) 
            throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        
        // Extract basic properties
        String itemName = jsonObject.has("itemName") ? jsonObject.get("itemName").getAsString() : "";
        int targetAmountMin = jsonObject.has("targetAmountMin") ? jsonObject.get("targetAmountMin").getAsInt() : 1;
        int targetAmountMax = jsonObject.has("targetAmountMax") ? jsonObject.get("targetAmountMax").getAsInt() : targetAmountMin;
        boolean includeNoted = jsonObject.has("includeNoted") && jsonObject.get("includeNoted").getAsBoolean();
        boolean includeNoneOwner = jsonObject.has("includeNoneOwner") && jsonObject.get("includeNoneOwner").getAsBoolean();
        
        // Create the condition
        return LootItemCondition.builder()
                .itemName(itemName)
                .targetAmountMin(targetAmountMin)
                .targetAmountMax(targetAmountMax)
                .includeNoted(includeNoted)
                .includeNoneOwner(includeNoneOwner)
                .build();
    }
}