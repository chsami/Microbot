package net.runelite.client.plugins.microbot.pluginscheduler.condition.skill.serialization;

import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.skill.SkillLevelCondition;

import java.lang.reflect.Type;

/**
 * Serializes and deserializes SkillLevelCondition objects
 */
@Slf4j
public class SkillLevelConditionAdapter implements JsonSerializer<SkillLevelCondition>, JsonDeserializer<SkillLevelCondition> {
    
    @Override
    public JsonElement serialize(SkillLevelCondition src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject json = new JsonObject();
        
        // Add type information
        json.addProperty("type", SkillLevelCondition.class.getName());
        
        // Create data object
        JsonObject data = new JsonObject();
        
        // Store skill information
        Skill skill = src.getSkill();
        data.addProperty("skill", skill != null ? skill.name() : "OVERALL");
        
        // Store level information
        data.addProperty("targetLevelMin", src.getTargetLevelMin());
        data.addProperty("targetLevelMax", src.getTargetLevelMax());
        data.addProperty("relative", src.isRelative());
        data.addProperty("version", SkillLevelCondition.getVersion());
        
        // Add data to wrapper
        json.add("data", data);
        
        return json;
    }
    
    @Override
    public SkillLevelCondition deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) 
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
            if (!version.equals(SkillLevelCondition.getVersion())) {
                log.warn("Version mismatch in SkillLevelCondition: expected {}, got {}", 
                        SkillLevelCondition.getVersion(), version);
                throw new JsonParseException("Version mismatch in SkillLevelCondition: expected " +
                        SkillLevelCondition.getVersion() + ", got " + version);
            }
        }
        
        // Get skill
        Skill skill;
        if (dataObj.get("skill").getAsString().equals("OVERALL")) {
            skill = Skill.OVERALL;
        } else {
            skill = Skill.valueOf(dataObj.get("skill").getAsString());
        }
        
        // Get level information
        int targetLevelMin = dataObj.get("targetLevelMin").getAsInt();
        int targetLevelMax = dataObj.get("targetLevelMax").getAsInt();
        boolean relative = dataObj.get("relative").getAsBoolean();
        
        // Create condition
        return new SkillLevelCondition(skill, targetLevelMin, targetLevelMax, relative);
        
    }
}