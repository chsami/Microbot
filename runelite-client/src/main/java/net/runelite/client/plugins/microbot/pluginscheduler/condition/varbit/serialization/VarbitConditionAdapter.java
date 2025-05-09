package net.runelite.client.plugins.microbot.pluginscheduler.condition.varbit.serialization;

import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.varbit.VarbitCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.varbit.VarbitCondition.VarType;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.varbit.VarbitCondition.ComparisonOperator;

import java.lang.reflect.Type;

/**
 * Serializes and deserializes VarbitCondition objects
 */
@Slf4j
public class VarbitConditionAdapter implements JsonSerializer<VarbitCondition>, JsonDeserializer<VarbitCondition> {
    
    @Override
    public JsonElement serialize(VarbitCondition src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject json = new JsonObject();
        
        // Add type information
        json.addProperty("type", VarbitCondition.class.getName());
        
        // Create data object
        JsonObject data = new JsonObject();
        
        // Store basic properties
        data.addProperty("name", src.getName());
        data.addProperty("version", src.getVersion());
        data.addProperty("varType", src.getVarType().toString());
        data.addProperty("varId", src.getVarId());
        data.addProperty("operator", src.getOperator().name()); // Use name() instead of toString()
        
        // Store target value information
        data.addProperty("targetValue", src.getTargetValue());
        data.addProperty("relative", src.isRelative());
        data.addProperty("randomized", src.isRandomized());
        
        // Store randomization range if using randomization
        if (src.isRandomized()) {
            data.addProperty("targetValueMin", src.getTargetValueMin());
            data.addProperty("targetValueMax", src.getTargetValueMax());
        }
        
        // Add data to wrapper
        json.add("data", data);
        
        return json;
    }
    
    @Override
    public VarbitCondition deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) 
            throws JsonParseException {
        
        JsonObject jsonObject = json.getAsJsonObject();
        
        // Check if this is using the type/data wrapper format
        if (jsonObject.has("type") && jsonObject.has("data")) {
            jsonObject = jsonObject.getAsJsonObject("data");
        }
        
        if (jsonObject.has("version")) {
            // Parse basic properties
            String version = jsonObject.get("version").getAsString();
            if (!version.equals(VarbitCondition.getVersion())) {
                
                throw new JsonParseException("Version mismatch: expected " + VarbitCondition.getVersion() + 
                        ", got " + version);
            }
        }
        
        String name = jsonObject.get("name").getAsString();
        VarType varType = VarType.valueOf(jsonObject.get("varType").getAsString());
        int varId = jsonObject.get("varId").getAsInt();
        
        // Get operator - handle both name and display name formats for backward compatibility
        String operatorStr = jsonObject.get("operator").getAsString();
        ComparisonOperator operator;
        
        try {
            // Try parsing as enum name first (new format)
            operator = ComparisonOperator.valueOf(operatorStr);
        } catch (IllegalArgumentException e) {
            // If that fails, try matching by display name (old format)
            operator = getOperatorByDisplayName(operatorStr);
            if (operator == null) {
                // If all parsing fails, default to EQUALS
                log.warn("Unknown operator '{}', defaulting to EQUALS", operatorStr);
                operator = ComparisonOperator.EQUALS;
            }
        }
        
        boolean relative = jsonObject.has("relative") && jsonObject.get("relative").getAsBoolean();
        
        // Check if this is using randomization
        boolean randomized = jsonObject.has("randomized") && jsonObject.get("randomized").getAsBoolean();
        
        if (randomized) {
            int targetValueMin = jsonObject.get("targetValueMin").getAsInt();
            int targetValueMax = jsonObject.get("targetValueMax").getAsInt();
            
            // Create with randomization
            if (relative) {
                return VarbitCondition.createRelativeRandomized(name, varType, varId, 
                        targetValueMin, targetValueMax, operator);
            } else {
                return VarbitCondition.createRandomized(name, varType, varId, 
                        targetValueMin, targetValueMax, operator);
            }
        } else {
            // Regular non-randomized condition
            int targetValue = jsonObject.get("targetValue").getAsInt();
            
            if (relative) {
                return VarbitCondition.createRelative(name, varType, varId, targetValue, operator);
            } else {
                return new VarbitCondition(name, varType, varId, targetValue, operator);
            }
        }
        
    }
    
    /**
     * Helper method to get an operator by its display name
     * Used for backward compatibility with old serialized data
     */
    private ComparisonOperator getOperatorByDisplayName(String displayName) {
        for (ComparisonOperator op : ComparisonOperator.values()) {
            if (op.getDisplayName().equalsIgnoreCase(displayName)) {
                return op;
            }
        }
        return null;
    }
}