package net.runelite.client.plugins.microbot.pluginscheduler.serialization.adapter;

import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigDescriptor;

import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ConditionManager;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.TimeCondition;

import net.runelite.client.plugins.microbot.pluginscheduler.model.PluginScheduleEntry;


import java.lang.reflect.Type;
import java.time.Duration;
import java.time.ZonedDateTime;



/**
 * Custom adapter for PluginScheduleEntry that correctly handles mainTimeStartCondition
 * and serializes/deserializes ConfigDescriptor
 */
@Slf4j
public class PluginScheduleEntryAdapter implements JsonSerializer<PluginScheduleEntry>, JsonDeserializer<PluginScheduleEntry> {

    @Override
    public JsonElement serialize(PluginScheduleEntry src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject result = new JsonObject();
        
        // Serialize all fields
        result.addProperty("name", src.getName());
        result.addProperty("enabled", src.isEnabled());
        result.addProperty("cleanName", src.getCleanName());
        result.addProperty("needsStopCondition", src.isNeedsStopCondition());
        // Serialize time fields
        if (src.getLastRunTime() != null) {
            result.addProperty("lastRunTime", src.getLastRunTime().toInstant().toEpochMilli());
        }
        
        // Serialize condition managers
        if (src.getStopConditionManager() != null) {
            result.add("stopConditionManager", context.serialize(src.getStopConditionManager()));
        }
        if (src.getStartConditionManager() != null) {
            result.add("startConditionManager", context.serialize(src.getStartConditionManager()));
        }
        
        // Serialize the main time condition 
        if (src.getMainTimeStartCondition() != null) {
            result.add("mainTimeStartCondition", context.serialize(src.getMainTimeStartCondition()));
        }
        
        // Serialize other properties
        result.addProperty("stopInitiated", src.isStopInitiated());
        result.addProperty("allowRandomScheduling", src.isAllowRandomScheduling());
        result.addProperty("runCount", src.getRunCount());
        
        // Serialize durations
        if (src.getSoftStopRetryInterval() != null) {
            result.add("softStopRetryInterval", context.serialize(src.getSoftStopRetryInterval()));
        }
        if (src.getHardStopTimeout() != null) {
            result.add("hardStopTimeout", context.serialize(src.getHardStopTimeout()));
        }
        
        // Serialize priority and default flag
        result.addProperty("priority", src.getPriority());
        result.addProperty("isDefault", src.isDefault());
        ConfigDescriptor configDescriptor = src.getConfigScheduleEntryDescriptor() != null ? src.getConfigScheduleEntryDescriptor(): null;
        if (configDescriptor != null) {
            result.add("configDescriptor", context.serialize(configDescriptor));
        }else {            
            result.add("configDescriptor", new JsonObject());
        }
            
        return result;
    }

    @Override
    public PluginScheduleEntry deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) 
            throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        
        // Get basic properties
        String name = jsonObject.get("name").getAsString();
        boolean enabled = jsonObject.has("enabled") ? jsonObject.get("enabled").getAsBoolean() : false;
        // Handle mainTimeStartCondition
        TimeCondition mainTimeCondition = null;
        if (jsonObject.has("mainTimeStartCondition")) {
            try {
                mainTimeCondition = context.deserialize(
                    jsonObject.get("mainTimeStartCondition"), TimeCondition.class);                                               
            } catch (Exception e) {
                log.error("Failed to parse mainTimeStartCondition", e);
            }
        }
        // Create a basic plugin entry first
        PluginScheduleEntry entry = new PluginScheduleEntry(name, (TimeCondition)mainTimeCondition, enabled, false);        
        // Deserialize cleanName if available
        if (jsonObject.has("cleanName")) {
            entry.setCleanName(jsonObject.get("cleanName").getAsString());
        }
        
        // Deserialize time fields
        if (jsonObject.has("lastRunTime")) {
            ZonedDateTime lastRunTime = ZonedDateTime.now().withNano(0);
            entry.setLastRunTime(lastRunTime);  // Placeholder to ensure field exists
            
            try {
                long timestamp = jsonObject.get("lastRunTime").getAsLong();
                ZonedDateTime prvLastRunTime  = (ZonedDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(timestamp), 
                    java.time.ZoneId.systemDefault()));
                entry.setLastRunTime(prvLastRunTime);
            } catch (Exception e) {
                log.error("Failed to parse lastRunTime", e);
            }
        }
        
        // Deserialize condition managers
        if (jsonObject.has("stopConditionManager")) {
            ConditionManager stopManager = context.deserialize(
                jsonObject.get("stopConditionManager"), ConditionManager.class);           
            if (!entry.getStopConditionManager().getUserConditions().isEmpty()) {
                throw new Error("StopConditionManager should be empty");
            } else {
                for (Condition condition : stopManager.getUserConditions()) {    
                    if(entry.getStopConditionManager().containsCondition(condition)){
                        throw new Error("Condition already exists in startConditionManager");
                    }                               
                    entry.addStopCondition(condition);                
                }                    
            }
        }        
        if (jsonObject.has("startConditionManager")) {
            ConditionManager startManager = context.deserialize(
                jsonObject.get("startConditionManager"), ConditionManager.class);                                                

            for (Condition condition : startManager.getUserConditions()) {                
                if(!entry.getStartConditionManager().containsCondition(condition)){
                    entry.addStartCondition(condition);                                            
                }                
            }
        }                        
        
        // Deserialize other properties
        if (jsonObject.has("stopInitiated")) {
            entry.setStopInitiated(jsonObject.get("stopInitiated").getAsBoolean());
        }
        
        if (jsonObject.has("allowRandomScheduling")) {
            entry.setAllowRandomScheduling(jsonObject.get("allowRandomScheduling").getAsBoolean());
        }
        
        if (jsonObject.has("runCount")) {
            int runCount = jsonObject.get("runCount").getAsInt();            
        }
        
        // Deserialize durations
        if (jsonObject.has("softStopRetryInterval")) {
            Duration softStopRetryInterval = context.deserialize(
                jsonObject.get("softStopRetryInterval"), Duration.class);
            entry.setSoftStopRetryInterval(softStopRetryInterval);
        }
        
        if (jsonObject.has("hardStopTimeout")) {
            Duration hardStopTimeout = context.deserialize(
                jsonObject.get("hardStopTimeout"), Duration.class);
            entry.setHardStopTimeout(hardStopTimeout);
        }
        
        // Deserialize priority and default flag
        if (jsonObject.has("priority")) {
            entry.setPriority(jsonObject.get("priority").getAsInt());
        }
        
        if (jsonObject.has("isDefault")) {
            entry.setDefault(jsonObject.get("isDefault").getAsBoolean());
        }
        if (jsonObject.has("needsStopCondition")) {
            entry.setNeedsStopCondition(jsonObject.get("needsStopCondition").getAsBoolean());
        }else{
            entry.setNeedsStopCondition(false);
        }
        //entry.registerPluginConditions();
        
        // Deserialize ConfigDescriptor if available
        // This will be applied when the plugin instance is retrieved via getPlugin()
        if (jsonObject.has("configDescriptor")) {
            // We'll need to load the plugin first to get its descriptor schema
            // The deserialized config will be applied when the plugin is loaded
            JsonObject configDescriptorObj = jsonObject.getAsJsonObject("configDescriptor");
            ConfigDescriptor configDescriptor = context.deserialize(configDescriptorObj, ConfigDescriptor.class);
            // Store this object for later use when the plugin is loaded
            entry.setSerializedConfigDescriptor(configDescriptor);
        }
        
        return entry;
    }
}