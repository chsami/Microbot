package net.runelite.client.plugins.microbot.pluginscheduler.serialization.adapter.config;

import com.google.gson.*;
import net.runelite.client.config.ConfigSection;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

/**
 * Adapter for serializing and deserializing ConfigSection annotations
 * Since annotations are immutable, we need to create a proxy implementation
 */
public class ConfigSectionAdapter implements JsonSerializer<ConfigSection>, JsonDeserializer<ConfigSection> {

    @Override
    public JsonElement serialize(ConfigSection src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject result = new JsonObject();
        result.addProperty("name", src.name());
        result.addProperty("description", src.description());
        result.addProperty("position", src.position());
        result.addProperty("closedByDefault", src.closedByDefault());
        return result;
    }

    @Override
    public ConfigSection deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        
        final String name = jsonObject.has("name") ? jsonObject.get("name").getAsString() : "";
        final String description = jsonObject.has("description") ? jsonObject.get("description").getAsString() : "";
        final int position = jsonObject.has("position") ? jsonObject.get("position").getAsInt() : 0;
        final boolean closedByDefault = jsonObject.has("closedByDefault") && jsonObject.get("closedByDefault").getAsBoolean();
        
        // Create a proxy implementation of ConfigSection annotation
        return new ConfigSection() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return ConfigSection.class;
            }

            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return description;
            }

            @Override
            public int position() {
                return position;
            }

            @Override
            public boolean closedByDefault() {
                return closedByDefault;
            }
        };
    }
}