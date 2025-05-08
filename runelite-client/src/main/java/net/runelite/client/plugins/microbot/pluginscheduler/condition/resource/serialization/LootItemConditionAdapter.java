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
        
        // Add type information
        result.addProperty("type", LootItemCondition.class.getName());
        
        // Create data object
        JsonObject data = new JsonObject();
        
        // Add version information
        data.addProperty("version", LootItemCondition.getVersion());
        
        // Add specific properties for LootItemCondition
        data.addProperty("itemName", src.getItemName());
        data.addProperty("targetAmountMin", src.getTargetAmountMin());
        data.addProperty("targetAmountMax", src.getTargetAmountMax());
        data.addProperty("includeNoneOwner", src.isIncludeNoneOwner());
        data.addProperty("includeNoted", src.isIncludeNoted());
        
        // Add data to wrapper
        result.add("data", data);
        
        return result;
    }

    @Override
    public LootItemCondition deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) 
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
            if (!version.equals(LootItemCondition.getVersion())) {
                log.warn("Version mismatch in LootItemCondition: expected {}, got {}", 
                        LootItemCondition.getVersion(), version);
                throw new JsonParseException("Version mismatch in LootItemCondition: expected " +
                        LootItemCondition.getVersion() + ", got " + version);
            }
        }
        
        // Extract basic properties
        String itemName = dataObj.has("itemName") ? dataObj.get("itemName").getAsString() : "";
        int targetAmountMin = dataObj.has("targetAmountMin") ? dataObj.get("targetAmountMin").getAsInt() : 1;
        int targetAmountMax = dataObj.has("targetAmountMax") ? dataObj.get("targetAmountMax").getAsInt() : targetAmountMin;
        boolean includeNoted = dataObj.has("includeNoted") && dataObj.get("includeNoted").getAsBoolean();
        boolean includeNoneOwner = dataObj.has("includeNoneOwner") && dataObj.get("includeNoneOwner").getAsBoolean();
        
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