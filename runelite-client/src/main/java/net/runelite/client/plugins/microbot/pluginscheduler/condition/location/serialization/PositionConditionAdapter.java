package net.runelite.client.plugins.microbot.pluginscheduler.condition.location.serialization;

import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.location.PositionCondition;

import java.lang.reflect.Type;

/**
 * Serializes and deserializes PositionCondition objects
 */
@Slf4j
public class PositionConditionAdapter implements JsonSerializer<PositionCondition>, JsonDeserializer<PositionCondition> {
    
    @Override
    public JsonElement serialize(PositionCondition src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject json = new JsonObject();
        
        // Add type information
        json.addProperty("type", PositionCondition.class.getName());
        
        // Create data object
        JsonObject data = new JsonObject();
        
        // Store position information
        data.addProperty("name", src.getName());
        WorldPoint point = src.getTargetPosition();
        data.addProperty("x", point.getX());
        data.addProperty("y", point.getY());
        data.addProperty("plane", point.getPlane());
        data.addProperty("maxDistance", src.getMaxDistance());
        data.addProperty("version", PositionCondition.getVersion());
        
        // Add data to wrapper
        json.add("data", data);
        
        return json;
    }
    
    @Override
    public PositionCondition deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) 
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
            if (!version.equals(PositionCondition.getVersion())) {                
                throw new JsonParseException("Version mismatch in PositionCondition: expected " +
                        PositionCondition.getVersion() + ", got " + version);
            }
        }
        
        // Get position information
        String name = dataObj.get("name").getAsString();
        int x = dataObj.get("x").getAsInt();
        int y = dataObj.get("y").getAsInt();
        int plane = dataObj.get("plane").getAsInt();
        int maxDistance = dataObj.get("maxDistance").getAsInt();
        
        // Create condition
        return new PositionCondition(name, x, y, plane, maxDistance);
        
    }
}