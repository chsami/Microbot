package net.runelite.client.plugins.microbot.pluginscheduler.condition.skill.serialization;

import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.skill.SkillXpCondition;

import java.lang.reflect.Type;

/**
 * Serializes and deserializes SkillXpCondition objects
 */
@Slf4j
public class SkillXpConditionAdapter implements JsonSerializer<SkillXpCondition>, JsonDeserializer<SkillXpCondition> {
    
    @Override
    public JsonElement serialize(SkillXpCondition src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject json = new JsonObject();
        
        // Add type information
        json.addProperty("type", SkillXpCondition.class.getName());
        
        // Create data object
        JsonObject data = new JsonObject();
        
        // Store skill information
        Skill skill = src.getSkill();
        data.addProperty("skill", skill != null ? skill.name() : "OVERALL");
        
        // Store XP information
        data.addProperty("targetXpMin", src.getTargetXpMin());
        data.addProperty("targetXpMax", src.getTargetXpMax());
        data.addProperty("relative", src.isRelative());
        data.addProperty("version", src.getVersion());
        
        // Add data to wrapper
        json.add("data", data);
        
        return json;
    }
    
    @Override
    public SkillXpCondition deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) 
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
            if (!version.equals(SkillXpCondition.getVersion())) {
                throw new JsonParseException("Version mismatch in SkillXpCondition: expected " +
                        SkillXpCondition.getVersion() + ", got " + version);
            }
        }
        
        // Get skill
        Skill skill;
        if (dataObj.get("skill").getAsString().equals("OVERALL")) {
            skill = Skill.OVERALL;
        } else {
            skill = Skill.valueOf(dataObj.get("skill").getAsString());
        }
        
        // Get XP information
        long targetXpMin = dataObj.get("targetXpMin").getAsLong();
        long targetXpMax = dataObj.get("targetXpMax").getAsLong();
        boolean relative = dataObj.get("relative").getAsBoolean();
        
        // Create condition
        return new SkillXpCondition(skill, targetXpMin, targetXpMax, relative);
        
       
    }
}
