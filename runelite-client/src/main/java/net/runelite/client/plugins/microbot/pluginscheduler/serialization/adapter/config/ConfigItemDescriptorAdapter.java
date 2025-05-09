package net.runelite.client.plugins.microbot.pluginscheduler.serialization.adapter.config;

import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.*;

import java.lang.reflect.Type;

/**
 * Adapter for serializing and deserializing ConfigItemDescriptor objects
 */
@Slf4j
public class ConfigItemDescriptorAdapter implements JsonSerializer<ConfigItemDescriptor>, JsonDeserializer<ConfigItemDescriptor> {

    @Override
    public JsonElement serialize(ConfigItemDescriptor src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject result = new JsonObject();
        
        // Serialize the ConfigItem annotation
        if (src.getItem() != null) {
            result.add("item", context.serialize(src.getItem(), ConfigItem.class));
        }
        
        // Serialize the type
        if (src.getType() != null) {
            result.addProperty("type", src.getType().getTypeName());
        }
        
        // Serialize Range annotation if present
        if (src.getRange() != null) {
            result.add("range", context.serialize(src.getRange(), Range.class));
        }
        
        // Serialize Alpha annotation if present
        if (src.getAlpha() != null) {
            result.add("alpha", context.serialize(src.getAlpha(), Alpha.class));
        }
        
        // Serialize Units annotation if present
        if (src.getUnits() != null) {
            result.add("units", context.serialize(src.getUnits(), Units.class));
        }
        
        return result;
    }

    @Override
    public ConfigItemDescriptor deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        
        // Deserialize the ConfigItem annotation
        ConfigItem item = null;
        if (jsonObject.has("item")) {
            item = context.deserialize(jsonObject.get("item"), ConfigItem.class);
        }
        
        // Deserialize the type
        Type itemType = null;
        if (jsonObject.has("type")) {
            String typeName = jsonObject.get("type").getAsString();
            try {
                // Try to load the class if available
                itemType = Class.forName(typeName);
            } catch (ClassNotFoundException e) {
                // If class isn't available, store the type name as a placeholder
                log.debug("Class not found for type: {}", typeName);
                // For primitive types, handle them specially
                if (typeName.equals("boolean") || typeName.equals("java.lang.Boolean")) {
                    itemType = boolean.class;
                } else if (typeName.equals("int") || typeName.equals("java.lang.Integer")) {
                    itemType = int.class;
                } else if (typeName.equals("double") || typeName.equals("java.lang.Double")) {
                    itemType = double.class;
                } else if (typeName.equals("long") || typeName.equals("java.lang.Long")) {
                    itemType = long.class;
                } else if (typeName.equals("float") || typeName.equals("java.lang.Float")) {
                    itemType = float.class;
                } else if (typeName.equals("java.lang.String")) {
                    itemType = String.class;
                } else {
                    // Use Object as fallback
                    itemType = Object.class;
                }
            }
        }
        
        // Deserialize Range annotation if present
        Range range = null;
        if (jsonObject.has("range")) {
            range = context.deserialize(jsonObject.get("range"), Range.class);
        }
        
        // Deserialize Alpha annotation if present
        Alpha alpha = null;
        if (jsonObject.has("alpha")) {
            alpha = context.deserialize(jsonObject.get("alpha"), Alpha.class);
        }
        
        // Deserialize Units annotation if present
        Units units = null;
        if (jsonObject.has("units")) {
            units = context.deserialize(jsonObject.get("units"), Units.class);
        }
        
        return new ConfigItemDescriptor(item, itemType, range, alpha, units);
    }
}