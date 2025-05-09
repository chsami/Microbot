package net.runelite.client.plugins.microbot.pluginscheduler.serialization.adapter.config;

import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.ConfigSectionDescriptor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

/**
 * Adapter for serializing and deserializing ConfigSectionDescriptor objects
 */
@Slf4j
public class ConfigSectionDescriptorAdapter implements JsonSerializer<ConfigSectionDescriptor>, JsonDeserializer<ConfigSectionDescriptor> {

    @Override
    public JsonElement serialize(ConfigSectionDescriptor src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject result = new JsonObject();
        
        // Serialize the key
        result.addProperty("key", src.getKey());
        
        // Serialize the section annotation
        if (src.getSection() != null) {
            result.add("section", context.serialize(src.getSection(), ConfigSection.class));
        }
        
        return result;
    }

    @Override
    public ConfigSectionDescriptor deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        
        // Deserialize the key
        String key = null;
        if (jsonObject.has("key")) {
            key = jsonObject.get("key").getAsString();
        }
        
        // Deserialize the section annotation
        ConfigSection section = null;
        if (jsonObject.has("section")) {
            section = context.deserialize(jsonObject.get("section"), ConfigSection.class);
        }
        
        // Create a ConfigSectionDescriptor instance 
        return new ConfigSectionDescriptor(key, section);
    }
}