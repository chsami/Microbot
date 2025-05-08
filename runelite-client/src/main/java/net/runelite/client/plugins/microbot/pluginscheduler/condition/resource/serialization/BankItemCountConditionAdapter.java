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
        
        // Add type information
        result.addProperty("type", BankItemCountCondition.class.getName());
        
        // Create data object
        JsonObject data = new JsonObject();
        
        // Add version information
        data.addProperty("version", BankItemCountCondition.getVersion());
        
        // Add specific properties for BankItemCountCondition
        data.addProperty("itemName", src.getItemName());
        data.addProperty("targetCountMin", src.getTargetCountMin());
        data.addProperty("targetCountMax", src.getTargetCountMax());                    
        // Add data to wrapper
        result.add("data", data);
        
        return result;
    }

    @Override
    public BankItemCountCondition deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) 
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
            if (!version.equals(BankItemCountCondition.getVersion())) {

                throw new JsonParseException("Version mismatch in BankItemCountCondition: expected " +
                        BankItemCountCondition.getVersion() + ", got " + version);
            }
        }
        
        // Extract basic properties
        String itemName = dataObj.has("itemName") ? dataObj.get("itemName").getAsString() : "";
        int targetCountMin = dataObj.has("targetCountMin") ? dataObj.get("targetCountMin").getAsInt() : 1;
        int targetCountMax = dataObj.has("targetCountMax") ? dataObj.get("targetCountMax").getAsInt() : targetCountMin;
        
        // Create the condition
        return BankItemCountCondition.builder()
                .itemName(itemName)
                .targetCountMin(targetCountMin)
                .targetCountMax(targetCountMax)
                .build();
   
    }
}