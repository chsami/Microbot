package net.runelite.client.plugins.microbot.pluginscheduler.condition.time.serialization;

import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.IntervalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.SingleTriggerTimeCondition;
import java.time.format.DateTimeFormatter;
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
        ZonedDateTime nextTrigger = src.getNextTriggerTimeWithPause().orElse(null);        
        

        if (nextTrigger != null) {
            data.addProperty("nextTriggerTimeMillis", nextTrigger.toInstant().toEpochMilli());
        }
        
        // Serialize initial delay condition if it exists
        if (src.getInitialDelayCondition() != null) {
            SingleTriggerTimeCondition delayCondition = src.getInitialDelayCondition();            
            if (delayCondition.getNextTriggerTimeWithPause().orElse(null) != null) {
                data.addProperty("targetTimeMillis", delayCondition.getNextTriggerTimeWithPause().get().toInstant().toEpochMilli());
            }                    
            data.add("initialDelayCondition", context.serialize(delayCondition));
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
            intialCondition = context.deserialize(initialDelayData, SingleTriggerTimeCondition.class);         
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
            long intitalDelayFromCondition = intialCondition.getNextTriggerTimeWithPause().get().toInstant().toEpochMilli() - System.currentTimeMillis();
            if (intialCondition != null) {
                // Format times for better readability
                ZonedDateTime targetTime = intialCondition.getNextTriggerTimeWithPause().get();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                String formattedTargetTime = targetTime.format(formatter);
                
                // Convert milliseconds to duration for proper formatting
                Duration delayFromCondition = Duration.ofMillis(Math.max(0, intitalDelayFromCondition));
                String formattedDelay = String.format("%02d:%02d:%02d", 
                    delayFromCondition.toHours(),
                    delayFromCondition.toMinutesPart(),
                    delayFromCondition.toSecondsPart()
                );
                
                // Convert initialDelaySeconds to duration for formatting
                Duration initialDelay = Duration.ofSeconds(initialDelaySeconds != null ? initialDelaySeconds : 0);
                String formattedInitialDelay = String.format("%02d:%02d:%02d",
                    initialDelay.toHours(),
                    initialDelay.toMinutesPart(),
                    initialDelay.toSecondsPart()
                );
                
                Optional<ZonedDateTime> nextTriggerWithPause = intialCondition.getNextTriggerTimeWithPause();
                String formattedNextTriggerWithPause = nextTriggerWithPause
                    .map(time -> time.format(formatter))
                    .orElse("Not set");
                
                // Check if next trigger time is before current time
                boolean isBeforeCurrent = nextTriggerWithPause
                    .map(time -> time.isBefore(ZonedDateTime.now(ZoneId.systemDefault())))
                    .orElse(false);
                
                log.info("\nInitial delay condition: {}\n- Target time: {}\n- Initial delay: {} ({})\n- Delay from condition: {} ({})\n- Next trigger with pause: {}\n- Is before current time: {}\n",
                    intialCondition.toString(),
                    formattedTargetTime,
                    formattedInitialDelay,
                    initialDelaySeconds + " seconds",
                    formattedDelay,
                    (intitalDelayFromCondition / 1000) + " seconds",
                    formattedNextTriggerWithPause,
                    isBeforeCurrent
                );
            } else {
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
                // Create a new condition with the initial delay from the initial condition
                condition =  new IntervalCondition(interval, randomize, randomFactor, maximumNumberOfRepeats, (Long)intialCondition.getDefinedDelay().toSeconds());
            } else if (initialDelaySeconds != null && initialDelaySeconds > 0) {
                // Create a new condition with the initial delay
                condition =  new IntervalCondition(interval, randomize, randomFactor, maximumNumberOfRepeats, initialDelaySeconds);
            }else{
                // Create a new condition without initial delay
                condition =  new IntervalCondition(interval, randomize, randomFactor, maximumNumberOfRepeats);
            }

                        
        }




         if (intialCondition!= null  && intialCondition.getNextTriggerTimeWithPause().orElse(null) !=null && intialCondition.getNextTriggerTimeWithPause().get().isBefore(ZonedDateTime.now(ZoneId.systemDefault()))) {
            Duration initialDelay = Duration.ofSeconds(intialCondition.getDefinedDelay().toSeconds());
            Duration remaingDuration = Duration.between(intialCondition.getNextTriggerTimeWithPause().get(),ZonedDateTime.now(ZoneId.systemDefault()));
            Duration remaingDuration__ = Duration.between(ZonedDateTime.now(ZoneId.systemDefault()),intialCondition.getNextTriggerTimeWithPause().get());
            log.info  ("\nInitial delay condition: {} \n- next targeted trigger time {} \n-remaning {} -difference: {}", intialCondition.toString(),
                    intialCondition.getNextTriggerTimeWithPause().get().toString(),remaingDuration.getSeconds(),remaingDuration__.getSeconds());
            condition =  new IntervalCondition(
                condition.getInterval(),
                condition.getMinInterval(),
                condition.getMaxInterval(),
                condition.isRandomize(),
                condition.getRandomFactor(),
                condition.getMaximumNumberOfRepeats(),
                remaingDuration__.getSeconds()
            );
        }
        else if (initialDelaySeconds != null && initialDelaySeconds > 0) {
            // Create a new condition with the initial delay
            log.info("\nInitial delay condition: {} \n- next targeted trigger time {} \n- initial delay seconds {}, before: {}", 
                condition.toString(),
                condition.getNextTriggerTimeWithPause().orElse(null),
                initialDelaySeconds,
                condition.getNextTriggerTimeWithPause().orElse(null).isBefore(ZonedDateTime.now(ZoneId.systemDefault())) ? "yes" : "no"
                );
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