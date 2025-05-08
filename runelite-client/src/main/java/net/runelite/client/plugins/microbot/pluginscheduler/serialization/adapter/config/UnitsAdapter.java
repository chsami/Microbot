package net.runelite.client.plugins.microbot.pluginscheduler.serialization.adapter.config;

import com.google.gson.*;
import net.runelite.client.config.Units;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

/**
 * Adapter for serializing and deserializing Units annotations
 */
public class UnitsAdapter implements JsonSerializer<Units>, JsonDeserializer<Units> {

    @Override
    public JsonElement serialize(Units src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject result = new JsonObject();
        result.addProperty("value", src.value());
        return result;
    }

    @Override
    public Units deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        
        final String value = jsonObject.has("value") ? jsonObject.get("value").getAsString() : "";
        
        // Create a proxy implementation of Units annotation
        return new Units() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return Units.class;
            }

            @Override
            public String value() {
                return value;
            }
        };
    }
}