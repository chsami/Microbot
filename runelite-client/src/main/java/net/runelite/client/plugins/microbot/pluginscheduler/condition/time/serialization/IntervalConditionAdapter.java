package net.runelite.client.plugins.microbot.pluginscheduler.condition.time.serialization;

import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.IntervalCondition;

import java.lang.reflect.Type;
import java.time.Duration;
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
        if (src.isRandomized()) {
            data.addProperty("minIntervalSeconds", src.getMinInterval().getSeconds());
            data.addProperty("maxIntervalSeconds", src.getMaxInterval().getSeconds());
            data.addProperty("isMinMaxRandomized", true);
        }
        
        // Store randomization settings
        data.addProperty("randomize", src.isRandomize());
        data.addProperty("randomFactor", src.getRandomFactor());
        data.addProperty("maximumNumberOfRepeats", src.getMaximumNumberOfRepeats());
        
        // Store next trigger time if available
        Optional<ZonedDateTime> nextTrigger = src.getCurrentTriggerTime();
        if (nextTrigger.isPresent()) {
            data.addProperty("nextTriggerTimeMillis", nextTrigger.get().toInstant().toEpochMilli());
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
        
        // Check if this is using min/max randomization
        if (jsonObject.has("isMinMaxRandomized") && jsonObject.get("isMinMaxRandomized").getAsBoolean()) {
            long minIntervalSeconds = jsonObject.get("minIntervalSeconds").getAsLong();
            long maxIntervalSeconds = jsonObject.get("maxIntervalSeconds").getAsLong();
            Duration minInterval = Duration.ofSeconds(minIntervalSeconds);
            Duration maxInterval = Duration.ofSeconds(maxIntervalSeconds);
            
            return IntervalCondition.createRandomized(minInterval, maxInterval);
        }
        
        // Parse randomization settings for the traditional approach
        boolean randomize = jsonObject.has("randomize") && jsonObject.get("randomize").getAsBoolean();
        double randomFactor = randomize && jsonObject.has("randomFactor") ? 
                            jsonObject.get("randomFactor").getAsDouble() : 0.0;
        long maximumNumberOfRepeats = jsonObject.has("maximumNumberOfRepeats") ? 
                            jsonObject.get("maximumNumberOfRepeats").getAsLong() : 0;
                            
        // Create the condition and return it
        return new IntervalCondition(interval, randomize, randomFactor, maximumNumberOfRepeats);
       
    }
}