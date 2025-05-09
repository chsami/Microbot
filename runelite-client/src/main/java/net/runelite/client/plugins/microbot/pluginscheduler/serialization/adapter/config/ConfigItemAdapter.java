package net.runelite.client.plugins.microbot.pluginscheduler.serialization.adapter.config;

import com.google.gson.*;
import net.runelite.client.config.ConfigItem;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

/**
 * Adapter for serializing and deserializing ConfigItem annotations
 * Since annotations are immutable, we need to create a proxy implementation
 */
public class ConfigItemAdapter implements JsonSerializer<ConfigItem>, JsonDeserializer<ConfigItem> {

    @Override
    public JsonElement serialize(ConfigItem src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject result = new JsonObject();
        result.addProperty("keyName", src.keyName());
        result.addProperty("name", src.name());
        result.addProperty("description", src.description());
        result.addProperty("section", src.section());
        result.addProperty("position", src.position());
        result.addProperty("hidden", src.hidden());
        result.addProperty("secret", src.secret());
        result.addProperty("warning", src.warning());
        return result;
    }

    @Override
    public ConfigItem deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        
        final String keyName = jsonObject.has("keyName") ? jsonObject.get("keyName").getAsString() : "";
        final String name = jsonObject.has("name") ? jsonObject.get("name").getAsString() : "";
        final String description = jsonObject.has("description") ? jsonObject.get("description").getAsString() : "";
        final String section = jsonObject.has("section") ? jsonObject.get("section").getAsString() : "";
        final int position = jsonObject.has("position") ? jsonObject.get("position").getAsInt() : 0;
        final boolean hidden = jsonObject.has("hidden") && jsonObject.get("hidden").getAsBoolean();
        final boolean secret = jsonObject.has("secret") && jsonObject.get("secret").getAsBoolean();
        final String warning = jsonObject.has("warning") ? jsonObject.get("warning").getAsString() : "";
        
        // Create a proxy implementation of ConfigItem annotation
        return new ConfigItem() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return ConfigItem.class;
            }

            @Override
            public String keyName() {
                return keyName;
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
            public String section() {
                return section;
            }

            @Override
            public int position() {
                return position;
            }

            @Override
            public boolean hidden() {
                return hidden;
            }
            
            @Override
            public boolean secret() {
                return secret;
            }
            
            @Override
            public String warning() {
                return warning;
            }
        };
    }
}