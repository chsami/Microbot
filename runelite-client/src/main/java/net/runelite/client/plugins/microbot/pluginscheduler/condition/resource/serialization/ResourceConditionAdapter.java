package net.runelite.client.plugins.microbot.pluginscheduler.condition.resource.serialization;

import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.resource.ResourceCondition;

import java.lang.reflect.Type;
import java.util.regex.Pattern;

/**
 * Adapter for handling serialization and deserialization of ResourceCondition objects.
 */
@Slf4j
public class ResourceConditionAdapter implements JsonSerializer<ResourceCondition>, JsonDeserializer<ResourceCondition> {

    @Override
    public JsonElement serialize(ResourceCondition src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject result = new JsonObject();
        
        // Add type information
        result.addProperty("type", src.getClass().getName());
        
        // Create data object
        JsonObject data = new JsonObject();
        
        // Add version information - use specific version if available, or default
        try {
            String version = (String) src.getClass().getMethod("getVersion").invoke(null);
            data.addProperty("version", version);
        } catch (Exception e) {
            data.addProperty("version", "0.0.1");
            log.debug("Could not get version for {}, using default", src.getClass().getName());
        }
        
        // Add itemName pattern - a common property for all resource conditions
        data.addProperty("itemName", src.getItemName());
        
        // Add data to wrapper
        result.add("data", data);
        
        return result;
    }

    @Override
    public ResourceCondition deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) 
            throws JsonParseException {
        // This base adapter doesn't handle deserialization directly
        // It's expected that specific subclass adapters will handle their own types
        throw new JsonParseException("Cannot deserialize abstract ResourceCondition directly");
    }
    
    /**
     * Helper method to extract a Pattern object from JSON
     */
    protected Pattern deserializePattern(JsonObject jsonObject, String fieldName) {
        if (jsonObject.has(fieldName)) {
            String patternStr = jsonObject.get(fieldName).getAsString();
            try {
                return Pattern.compile(patternStr);
            } catch (Exception e) {
                log.warn("Failed to parse pattern: {}", patternStr, e);
            }
        }
        return null;
    }
}