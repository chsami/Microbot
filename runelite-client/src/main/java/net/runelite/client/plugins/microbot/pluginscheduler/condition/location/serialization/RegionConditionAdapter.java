package net.runelite.client.plugins.microbot.pluginscheduler.condition.location.serialization;

import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.location.RegionCondition;

import java.lang.reflect.Type;
import java.util.Set;

/**
 * Serializes and deserializes RegionCondition objects
 */
@Slf4j
public class RegionConditionAdapter implements JsonSerializer<RegionCondition>, JsonDeserializer<RegionCondition> {
    
    @Override
    public JsonElement serialize(RegionCondition src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject json = new JsonObject();
        
        // Add type information
        json.addProperty("type", RegionCondition.class.getName());
        
        // Create data object
        JsonObject data = new JsonObject();
        
        // Store region information
        data.addProperty("name", src.getName());
        JsonArray regionIds = new JsonArray();
        for (Integer regionId : src.getTargetRegions()) {
            regionIds.add(regionId);
        }
        data.add("regionIds", regionIds);
        data.addProperty("version", RegionCondition.getVersion());
        
        // Add data to wrapper
        json.add("data", data);
        
        return json;
    }
    
    @Override
    public RegionCondition deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) 
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
            if (!version.equals(RegionCondition.getVersion())) {                
                throw new JsonParseException("Version mismatch in RegionCondition: expected " +
                        RegionCondition.getVersion() + ", got " + version);
            }
        }
        
        // Get region information
        String name = dataObj.get("name").getAsString();
        JsonArray regionIdsArray = dataObj.getAsJsonArray("regionIds");
        int[] regionIds = new int[regionIdsArray.size()];
        
        for (int i = 0; i < regionIdsArray.size(); i++) {
            regionIds[i] = regionIdsArray.get(i).getAsInt();
        }
        
        // Create condition
        return new RegionCondition(name, regionIds);
            
        
    }
}