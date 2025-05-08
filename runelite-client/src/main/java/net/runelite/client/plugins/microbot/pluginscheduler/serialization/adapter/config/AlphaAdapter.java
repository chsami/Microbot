package net.runelite.client.plugins.microbot.pluginscheduler.serialization.adapter.config;

import com.google.gson.*;
import net.runelite.client.config.Alpha;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

/**
 * Adapter for serializing and deserializing Alpha annotations
 */
public class AlphaAdapter implements JsonSerializer<Alpha>, JsonDeserializer<Alpha> {

    @Override
    public JsonElement serialize(Alpha src, Type typeOfSrc, JsonSerializationContext context) {
        // Alpha annotation has no properties, so we just return an empty object
        return new JsonObject();
    }

    @Override
    public Alpha deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        // Create a proxy implementation of Alpha annotation
        return new Alpha() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return Alpha.class;
            }
        };
    }
}