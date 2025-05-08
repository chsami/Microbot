package net.runelite.client.plugins.microbot.pluginscheduler.condition.npc.serialization;

import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.npc.NpcKillCountCondition;

import java.lang.reflect.Type;

/**
 * Serializes and deserializes NpcKillCountCondition objects
 */
@Slf4j
public class NpcKillCountConditionAdapter implements JsonSerializer<NpcKillCountCondition>, JsonDeserializer<NpcKillCountCondition> {
    
    @Override
    public JsonElement serialize(NpcKillCountCondition src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject json = new JsonObject();
        
        // Add type information
        json.addProperty("type", NpcKillCountCondition.class.getName());
        
        // Create data object
        JsonObject data = new JsonObject();
        
        // Store NPC information
        data.addProperty("npcName", src.getNpcName());
        data.addProperty("targetCountMin", src.getTargetCountMin());
        data.addProperty("targetCountMax", src.getTargetCountMax());        
        data.addProperty("version", NpcKillCountCondition.getVersion());
        
        // Add data to wrapper
        json.add("data", data);
        
        return json;
    }
    
    @Override
    public NpcKillCountCondition deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) 
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
            if (!version.equals(NpcKillCountCondition.getVersion())) {                
                throw new JsonParseException("Version mismatch in NpcKillCountCondition: expected " +
                        NpcKillCountCondition.getVersion() + ", got " + version);
            }
        }
        
        // Get NPC information
        String npcName = dataObj.get("npcName").getAsString();
        int targetCountMin = dataObj.get("targetCountMin").getAsInt();
        int targetCountMax = dataObj.get("targetCountMax").getAsInt();
        
        // Create using builder pattern
        NpcKillCountCondition condition = NpcKillCountCondition.builder()
            .npcName(npcName)
            .targetCountMin(targetCountMin)
            .targetCountMax(targetCountMax)
            .build();
            
        return condition;
        
       
    }
}