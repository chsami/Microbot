package net.runelite.client.plugins.microbot.pluginscheduler.serialization.adapter.config;

import com.google.gson.*;
import net.runelite.client.config.ConfigInformation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

/**
 * Adapter for serializing and deserializing ConfigInformation annotations
 */
public class ConfigInformationAdapter implements JsonSerializer<ConfigInformation>, JsonDeserializer<ConfigInformation> {

    @Override
    public JsonElement serialize(ConfigInformation src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject result = new JsonObject();
        result.addProperty("value", src.value());
        return result;
    }

    @Override
    public ConfigInformation deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        
        final String value = jsonObject.has("value") ? jsonObject.get("value").getAsString() : "";
        
        // Create a proxy implementation of ConfigInformation annotation
        return new ConfigInformation() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return ConfigInformation.class;
            }

            @Override
            public String value() {
                return value;
            }
        };
    }
}