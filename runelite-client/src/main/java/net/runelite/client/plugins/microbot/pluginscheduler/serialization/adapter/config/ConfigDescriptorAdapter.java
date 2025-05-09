package net.runelite.client.plugins.microbot.pluginscheduler.serialization.adapter.config;

import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.*;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * Adapter for serializing and deserializing ConfigDescriptor objects
 * This allows us to store and restore complete configuration structures
 * without needing the actual plugin classes at deserialization time
 */
@Slf4j
public class ConfigDescriptorAdapter implements JsonSerializer<ConfigDescriptor>, JsonDeserializer<ConfigDescriptor> {

    @Override
    public JsonElement serialize(ConfigDescriptor src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject result = new JsonObject();
        
        // Serialize group
        if (src.getGroup() != null) {
            result.add("group", context.serialize(src.getGroup(), ConfigGroup.class));
        }
        
        // Serialize sections
        if (src.getSections() != null && !src.getSections().isEmpty()) {
            result.add("sections", context.serialize(src.getSections(), Collection.class));
        }
        
        // Serialize items
        if (src.getItems() != null && !src.getItems().isEmpty()) {
            result.add("items", context.serialize(src.getItems(), Collection.class));
        }
        
        // Serialize information if present
        if (src.getInformation() != null) {
            result.add("information", context.serialize(src.getInformation(), ConfigInformation.class));
        }
        
        return result;
    }

    @Override
    public ConfigDescriptor deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        
        // Deserialize group
        ConfigGroup group = null;
        if (jsonObject.has("group")) {
            group = context.deserialize(jsonObject.get("group"), ConfigGroup.class);
        }
        
        // Deserialize sections
        Collection<ConfigSectionDescriptor> sections = Collections.emptyList();
        if (jsonObject.has("sections")) {
            Type sectionListType = new TypeToken<Collection<ConfigSectionDescriptor>>() {}.getType();
            sections = context.deserialize(jsonObject.get("sections"), sectionListType);
        }
        
        // Deserialize items
        Collection<ConfigItemDescriptor> items = Collections.emptyList();
        if (jsonObject.has("items")) {
            Type itemsListType = new TypeToken<Collection<ConfigItemDescriptor>>() {}.getType();
            items = context.deserialize(jsonObject.get("items"), itemsListType);
        }
        
        // Deserialize information
        ConfigInformation information = null;
        if (jsonObject.has("information")) {
            information = context.deserialize(jsonObject.get("information"), ConfigInformation.class);
        }
        
        return new ConfigDescriptor(group, sections, items, information);
    }
    
    private static class TypeToken<T> {
        private TypeToken() {}
        
        public Type getType() {
            return getClass().getGenericSuperclass();
        }
    }
}