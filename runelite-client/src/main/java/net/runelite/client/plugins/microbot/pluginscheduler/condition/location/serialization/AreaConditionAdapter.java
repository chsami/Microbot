package net.runelite.client.plugins.microbot.pluginscheduler.condition.location.serialization;

import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldArea;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.location.AreaCondition;

import java.lang.reflect.Type;

/**
 * Serializes and deserializes AreaCondition objects
 */
@Slf4j
public class AreaConditionAdapter implements JsonSerializer<AreaCondition>, JsonDeserializer<AreaCondition> {
    
    @Override
    public JsonElement serialize(AreaCondition src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject json = new JsonObject();
        
        // Add type information
        json.addProperty("type", AreaCondition.class.getName());
        
        // Create data object
        JsonObject data = new JsonObject();
        
        // Store area information
        data.addProperty("name", src.getName());
        WorldArea area = src.getArea();
        data.addProperty("x", area.getX());
        data.addProperty("y", area.getY());
        data.addProperty("width", area.getWidth());
        data.addProperty("height", area.getHeight());
        data.addProperty("plane", area.getPlane());
        data.addProperty("version", AreaCondition.getVersion());
        
        // Add data to wrapper
        json.add("data", data);
        
        return json;
    }
    
    @Override
    public AreaCondition deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) 
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
            if (!version.equals(AreaCondition.getVersion())) {
                
               throw new JsonParseException("Version mismatch in AreaCondition: expected " + 
                       AreaCondition.getVersion() + ", got " + version);
            }
        }
        
        // Get area information
        String name = dataObj.get("name").getAsString();
        int x = dataObj.get("x").getAsInt();
        int y = dataObj.get("y").getAsInt();
        int width = dataObj.get("width").getAsInt();
        int height = dataObj.get("height").getAsInt();
        int plane = dataObj.get("plane").getAsInt();
        
        // Create area and condition
        WorldArea area = new WorldArea(x, y, width, height, plane);
        return new AreaCondition(name, area);
            
       
    }
}