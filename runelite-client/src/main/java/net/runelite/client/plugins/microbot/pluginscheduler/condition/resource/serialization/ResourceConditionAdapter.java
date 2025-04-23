package net.runelite.client.plugins.microbot.pluginscheduler.condition.resource.serialization;

import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.resource.ResourceCondition;

import java.lang.reflect.Type;
import java.util.regex.Pattern;

/**
 * Base adapter for handling serialization and deserialization of ResourceCondition objects.
 * This adapter provides common functionality for all resource condition adapters.
 */
@Slf4j
public class ResourceConditionAdapter implements JsonSerializer<ResourceCondition>, JsonDeserializer<ResourceCondition> {

    @Override
    public JsonElement serialize(ResourceCondition src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject result = new JsonObject();
        
        // Store the exact condition type (full class name)
        result.addProperty("conditionType", src.getClass().getName());
        
        // Add common properties for all resource conditions
        if (src.getItemPattern() != null) {
            result.addProperty("itemPattern", src.getItemPattern().pattern());
        }
        
        return result;
    }

    @Override
    public ResourceCondition deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) 
            throws JsonParseException {
        // The base ResourceCondition adapter doesn't directly instantiate objects
        // This will be handled by the specific condition type adapters that extend this class
        log.debug("ResourceConditionAdapter deserialize called, but ResourceCondition is abstract");
        return null;
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