package net.runelite.client.plugins.microbot.util.cache.serialization;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import net.runelite.client.plugins.microbot.util.cache.model.SkillData;

import java.io.IOException;

/**
 * Gson TypeAdapter for SkillData serialization/deserialization.
 * Stores skill data as a compact array: [level, boostedLevel, experience, lastUpdated, previousLevel, previousExperience].
 * Previous values are optional and stored as null if not available.
 */
public class SkillDataAdapter extends TypeAdapter<SkillData> {
    
    @Override
    public void write(JsonWriter out, SkillData value) throws IOException {
        if (value == null) {
            out.nullValue();
        } else {
            out.beginArray();
            out.value(value.getLevel());
            out.value(value.getBoostedLevel());
            out.value(value.getExperience());
            out.value(value.getLastUpdated());
            
            // Write previous level (nullable)
            if (value.getPreviousLevel() != null) {
                out.value(value.getPreviousLevel());
            } else {
                out.nullValue();
            }
            
            // Write previous experience (nullable)
            if (value.getPreviousExperience() != null) {
                out.value(value.getPreviousExperience());
            } else {
                out.nullValue();
            }
            
            out.endArray();
        }
    }

    @Override
    public SkillData read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        
        in.beginArray();
        int level = in.nextInt();
        int boostedLevel = in.nextInt();
        int experience = in.nextInt();
        
        // Handle backwards compatibility - check if more elements exist
        long lastUpdated = System.currentTimeMillis();
        Integer previousLevel = null;
        Integer previousExperience = null;
        
        if (in.hasNext()) {
            lastUpdated = in.nextLong();
            
            if (in.hasNext()) {
                if (in.peek() != JsonToken.NULL) {
                    previousLevel = in.nextInt();
                } else {
                    in.nextNull();
                }
                
                if (in.hasNext()) {
                    if (in.peek() != JsonToken.NULL) {
                        previousExperience = in.nextInt();
                    } else {
                        in.nextNull();
                    }
                }
            }
        }
        
        in.endArray();
        
        return new SkillData(level, boostedLevel, experience, lastUpdated, previousLevel, previousExperience);
    }
}
