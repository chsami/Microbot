package net.runelite.client.plugins.microbot.pluginscheduler.condition.resource.serialization;

import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.resource.GatheredResourceCondition;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for handling serialization and deserialization of GatheredResourceCondition objects.
 */
@Slf4j
public class GatheredResourceConditionAdapter implements JsonSerializer<GatheredResourceCondition>, JsonDeserializer<GatheredResourceCondition> {

    @Override
    public JsonElement serialize(GatheredResourceCondition src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject result = new JsonObject();
        
        // Add type information
        result.addProperty("type", GatheredResourceCondition.class.getName());
        
        // Create data object
        JsonObject data = new JsonObject();
        
        // Add version information
        data.addProperty("version", GatheredResourceCondition.getVersion());
        
        // Add specific properties for GatheredResourceCondition
        data.addProperty("itemName", src.getItemName());
        data.addProperty("targetCountMin", src.getTargetCountMin());
        data.addProperty("targetCountMax", src.getTargetCountMax());                
        data.addProperty("includeNoted", src.isIncludeNoted());
        
        // Add relevant skills array
        JsonArray skillsArray = new JsonArray();
        for (Skill skill : src.getRelevantSkills()) {
            skillsArray.add(skill.name());
        }
        data.add("relevantSkills", skillsArray);
        
        // Add data to wrapper
        result.add("data", data);
        
        return result;
    }

    @Override
    public GatheredResourceCondition deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) 
            throws JsonParseException {
        
        JsonObject jsonObject = json.getAsJsonObject();
        
        // Check if this is a typed format or direct format
        JsonObject dataObj;
        if (jsonObject.has("type") && jsonObject.has("data")) {
            dataObj = jsonObject.getAsJsonObject("data");
        } else {
            // Legacy format - use the object directly
            dataObj = jsonObject;
        }
        
        // Version check
        if (dataObj.has("version")) {
            String version = dataObj.get("version").getAsString();
            if (!version.equals(GatheredResourceCondition.getVersion())) {
                log.warn("Version mismatch in GatheredResourceCondition: expected {}, got {}", 
                        GatheredResourceCondition.getVersion(), version);
                throw new JsonParseException("Version mismatch in GatheredResourceCondition: expected " +
                        GatheredResourceCondition.getVersion() + ", got " + version);
            }
        }
        
        // Extract basic properties
        String itemName = dataObj.has("itemName") ? dataObj.get("itemName").getAsString() : "";
        int targetCountMin = dataObj.has("targetCountMin") ? dataObj.get("targetCountMin").getAsInt() : 1;
        int targetCountMax = dataObj.has("targetCountMax") ? dataObj.get("targetCountMax").getAsInt() : targetCountMin;
        boolean includeNoted = dataObj.has("includeNoted") && dataObj.get("includeNoted").getAsBoolean();
        
        // Extract relevant skills if present
        List<Skill> relevantSkills = new ArrayList<>();
        if (dataObj.has("relevantSkills")) {
            JsonArray skillsArray = dataObj.getAsJsonArray("relevantSkills");
            for (JsonElement skillElement : skillsArray) {
                try {
                    Skill skill = Skill.valueOf(skillElement.getAsString());
                    relevantSkills.add(skill);
                } catch (IllegalArgumentException e) {
                    log.warn("Unknown skill: {}", skillElement.getAsString());
                }
            }
        }
        
        // Create the condition
        return GatheredResourceCondition.builder()
                .itemName(itemName)
                .targetCountMin(targetCountMin)
                .targetCountMax(targetCountMax)
                .includeNoted(includeNoted)
                .relevantSkills(relevantSkills.isEmpty() ? null : relevantSkills)
                .build();
      
    }
}