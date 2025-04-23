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
        
        // Add specific properties for GatheredResourceCondition
        result.addProperty("itemName", src.getItemName());
        result.addProperty("targetCountMin", src.getTargetCountMin());
        result.addProperty("targetCountMax", src.getTargetCountMax());
        result.addProperty("currentTargetCount", src.getCurrentTargetCount());
        result.addProperty("currentGatheredCount", src.getCurrentGatheredCount());
        result.addProperty("includeNoted", src.isIncludeNoted());
        
        // Add relevant skills array
        JsonArray skillsArray = new JsonArray();
        for (Skill skill : src.getRelevantSkills()) {
            skillsArray.add(skill.name());
        }
        result.add("relevantSkills", skillsArray);
        
        return result;
    }

    @Override
    public GatheredResourceCondition deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) 
            throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        
        // Extract basic properties
        String itemName = jsonObject.has("itemName") ? jsonObject.get("itemName").getAsString() : "";
        int targetCountMin = jsonObject.has("targetCountMin") ? jsonObject.get("targetCountMin").getAsInt() : 1;
        int targetCountMax = jsonObject.has("targetCountMax") ? jsonObject.get("targetCountMax").getAsInt() : targetCountMin;
        boolean includeNoted = jsonObject.has("includeNoted") && jsonObject.get("includeNoted").getAsBoolean();
        
        // Extract relevant skills if present
        List<Skill> relevantSkills = new ArrayList<>();
        if (jsonObject.has("relevantSkills")) {
            JsonArray skillsArray = jsonObject.getAsJsonArray("relevantSkills");
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