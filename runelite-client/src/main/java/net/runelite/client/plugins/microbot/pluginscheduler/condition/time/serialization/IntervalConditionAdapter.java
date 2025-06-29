package net.runelite.client.plugins.microbot.pluginscheduler.condition.time.serialization;

import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.IntervalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.SingleTriggerTimeCondition;

import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Serializes and deserializes IntervalCondition objects
 */
@Slf4j
public class IntervalConditionAdapter implements JsonSerializer<IntervalCondition>, JsonDeserializer<IntervalCondition> {
    @Override
    public JsonElement serialize(IntervalCondition src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject json = new JsonObject();
        json.addProperty("version", IntervalCondition.getVersion());
        // Add type information
        json.addProperty("type", IntervalCondition.class.getName());
        
        // Create data object
        JsonObject data = new JsonObject();
        
        // Store interval as seconds for cross-platform compatibility
        data.addProperty("intervalSeconds", src.getInterval().getSeconds());
        
        // Store min/max interval information if using randomized intervals
        if (src.isRandomize()) {
            data.addProperty("minIntervalSeconds", src.getMinInterval().getSeconds());
            data.addProperty("maxIntervalSeconds", src.getMaxInterval().getSeconds());
            data.addProperty("isMinMaxRandomized", true);
        }
        
        // Store randomization settings
        data.addProperty("randomize", src.isRandomize());
        data.addProperty("randomFactor", src.getRandomFactor());
        data.addProperty("maximumNumberOfRepeats", src.getMaximumNumberOfRepeats());
        data.addProperty("currentValidResetCount", src.getCurrentValidResetCount());
        // Store next trigger time if available
        ZonedDateTime nextTrigger = src.getNextTriggerTime();
        ZonedDateTime currentTriggerDateTime = src.getCurrentTriggerTime().get();
        

        if (nextTrigger != null) {
            data.addProperty("nextTriggerTimeMillis", nextTrigger.toInstant().toEpochMilli());
        }
        
        // Serialize initial delay condition if it exists
        if (src.getInitialDelayCondition() != null) {
            SingleTriggerTimeCondition delayCondition = src.getInitialDelayCondition();
            JsonObject initialDelayData = new JsonObject();
            initialDelayData.addProperty("targetTimeMillis", 
                    delayCondition.getTargetTime().toInstant().toEpochMilli());
            data.add("initialDelayCondition", initialDelayData);
            data.add("initialCondition", context.serialize(delayCondition));
        }
        
        // Add data to wrapper
        json.add("data", data);
        
        return json;
    }
    
    @Override
    public IntervalCondition deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) 
            throws JsonParseException {
        
        JsonObject jsonObject = json.getAsJsonObject();
        

        // Check if this is using the type/data wrapper format
        if (jsonObject.has("type") && jsonObject.has("data")) {
            jsonObject = jsonObject.getAsJsonObject("data");
        }
        
        if (jsonObject.has("version")) {
            String version = jsonObject.get("version").getAsString();
            if (!IntervalCondition.getVersion().equals(version)) {
                log.warn("Version mismatch: expected {}, got {}", IntervalCondition.getVersion(), version);
                throw new JsonParseException("Version mismatch");
            }
        }
        // Parse interval
        long intervalSeconds = jsonObject.get("intervalSeconds").getAsLong();
        Duration interval = Duration.ofSeconds(intervalSeconds);
        
        // Extract initial delay information if present
        Long initialDelaySeconds = null;
        SingleTriggerTimeCondition intialCondition = null;
        if (jsonObject.has("initialDelayCondition")) {
        
            JsonObject initialDelayData = jsonObject.getAsJsonObject("initialDelayCondition");
            JsonObject initialConditionData= jsonObject.getAsJsonObject("initialCondition");
            intialCondition = context.deserialize(initialConditionData, SingleTriggerTimeCondition.class);
         

            if (initialDelayData.has("targetTimeMillis")) {
                long targetTimeMillis = initialDelayData.get("targetTimeMillis").getAsLong();
                
                // Calculate the initial delay in seconds from now to target time
                long nowMillis = System.currentTimeMillis();
                if (targetTimeMillis < nowMillis) {
                    // If the target time is in the past, set initial delay to 0
                    initialDelaySeconds = 0L;
                } else {
                    // Calculate the delay in milliseconds and convert to seconds
                    long delayMillis = Math.max(0, targetTimeMillis - nowMillis);
                    initialDelaySeconds = (Long)(delayMillis / 1000);
                }
            }
            if (intialCondition != null) {
                log.debug("\nInitial delay condition: {} \n- target time {} \n- initialDelaySeconds {}", intialCondition.toString(),
                        intialCondition.getTargetTime().toInstant().toEpochMilli(),initialDelaySeconds);
            }else{
                throw new JsonParseException("Initial delay condition is null");                
            }
        }
        
        // Check if this is using min/max randomization
        IntervalCondition condition = null;
        if (jsonObject.has("isMinMaxRandomized") && jsonObject.get("isMinMaxRandomized").getAsBoolean()) {
            long minIntervalSeconds = jsonObject.get("minIntervalSeconds").getAsLong();
            long maxIntervalSeconds = jsonObject.get("maxIntervalSeconds").getAsLong();
            Duration minInterval = Duration.ofSeconds(minIntervalSeconds);
            Duration maxInterval = Duration.ofSeconds(maxIntervalSeconds);
            
            condition = IntervalCondition.createRandomized(minInterval, maxInterval);
           
            
            
        }
       
        
        // Parse randomization settings for the traditional approach
        boolean randomize = jsonObject.has("randomize") && jsonObject.get("randomize").getAsBoolean();
        double randomFactor = randomize && jsonObject.has("randomFactor") ? 
                            jsonObject.get("randomFactor").getAsDouble() : 0.0;
        long maximumNumberOfRepeats = jsonObject.has("maximumNumberOfRepeats") ? 
                            jsonObject.get("maximumNumberOfRepeats").getAsLong() : 0;
        if(condition == null) {
            if (intialCondition != null) {
                // Create a new condition with the initial delay
                condition =  new IntervalCondition(interval, randomize, randomFactor, maximumNumberOfRepeats, (Long)intialCondition.getDefinedDelay().toSeconds());
            } else if (initialDelaySeconds != null && initialDelaySeconds > 0) {
                // Create a new condition with the initial delay
                condition =  new IntervalCondition(interval, randomize, randomFactor, maximumNumberOfRepeats, initialDelaySeconds);
            }
                        
        }




         if (intialCondition!= null && intialCondition.getTargetTime().isBefore(ZonedDateTime.now(ZoneId.systemDefault()))) {
            Duration initialDelay = Duration.ofSeconds(intialCondition.getDefinedDelay().toSeconds());
            Duration remaingDuration = Duration.between(intialCondition.getTargetTime(),ZonedDateTime.now(ZoneId.systemDefault()));
            log.debug  ("\nInitial delay condition: {} \n- target time {} \n- initialDelaySeconds {}", intialCondition.toString(),
                    intialCondition.getTargetTime().toString(),remaingDuration.getSeconds());
            condition =  new IntervalCondition(
                condition.getInterval(),
                condition.getMinInterval(),
                condition.getMaxInterval(),
                condition.isRandomize(),
                condition.getRandomFactor(),
                condition.getMaximumNumberOfRepeats(),
                remaingDuration.getSeconds()
            );
        }
        else if (initialDelaySeconds != null && initialDelaySeconds > 0) {
            // Create a new condition with the initial delay
            condition =  new IntervalCondition(
                condition.getInterval(),
                condition.getMinInterval(),
                condition.getMaxInterval(),
                condition.isRandomize(),
                condition.getRandomFactor(),
                condition.getMaximumNumberOfRepeats(),
                initialDelaySeconds
            );
        }
        if (jsonObject.has("currentValidResetCount")){
            if (jsonObject.get("currentValidResetCount").isJsonNull()) {
                condition.setCurrentValidResetCount(0);
            }else{            
                try {
                    condition.setCurrentValidResetCount(jsonObject.get("currentValidResetCount").getAsLong());
                } catch (Exception e) {
                    log.warn("Invalid currentValidResetCount value: {}", jsonObject.get("currentValidResetCount").getAsString());
                }
                
            }
        }
        if (jsonObject.has("nextTriggerTimeMillis")) {
            long nextTriggerMillis = jsonObject.get("nextTriggerTimeMillis").getAsLong();
            ZonedDateTime nextTrigger = ZonedDateTime.ofInstant(
                Instant.ofEpochMilli(nextTriggerMillis), 
                ZoneId.systemDefault()
            );
            ZonedDateTime currentTriggerDateTime = condition.getCurrentTriggerTime().get();
            condition.setNextTriggerTime(nextTrigger);            
        }
        return condition;
        
        
       
    }
}