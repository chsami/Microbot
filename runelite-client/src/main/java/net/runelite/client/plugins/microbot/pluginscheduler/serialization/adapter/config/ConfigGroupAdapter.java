package net.runelite.client.plugins.microbot.pluginscheduler.serialization.adapter.config;

import com.google.gson.*;
import net.runelite.client.config.ConfigGroup;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

/**
 * Adapter for serializing and deserializing ConfigGroup annotations
 * Since annotations are immutable, we need to create a proxy implementation
 */
public class ConfigGroupAdapter implements JsonSerializer<ConfigGroup>, JsonDeserializer<ConfigGroup> {

    @Override
    public JsonElement serialize(ConfigGroup src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject result = new JsonObject();
        result.addProperty("value", src.value());    
        return result;
    }

    @Override
    public ConfigGroup deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        
        final String value = jsonObject.get("value").getAsString();        
        // Create a proxy implementation of ConfigGroup annotation
        return new ConfigGroup() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return ConfigGroup.class;
            }

            @Override
            public String value() {
                return value;
            }
            
        };
    }
}