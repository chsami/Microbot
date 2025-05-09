package net.runelite.client.plugins.microbot.pluginscheduler.serialization.adapter.config;

import com.google.gson.*;
import net.runelite.client.config.Range;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

/**
 * Adapter for serializing and deserializing Range annotations
 */
public class RangeAdapter implements JsonSerializer<Range>, JsonDeserializer<Range> {

    @Override
    public JsonElement serialize(Range src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject result = new JsonObject();
        result.addProperty("min", src.min());
        result.addProperty("max", src.max());
        return result;
    }

    @Override
    public Range deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        
        final int min = jsonObject.has("min") ? jsonObject.get("min").getAsInt() : 0;
        final int max = jsonObject.has("max") ? jsonObject.get("max").getAsInt() : Integer.MAX_VALUE;
        
        // Create a proxy implementation of Range annotation
        return new Range() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return Range.class;
            }

            @Override
            public int min() {
                return min;
            }

            @Override
            public int max() {
                return max;
            }
        };
    }
}