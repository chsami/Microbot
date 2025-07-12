package net.runelite.client.plugins.microbot.util.cache.serialization;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import net.runelite.api.Skill;

import java.io.IOException;

/**
 * Gson TypeAdapter for Skill enum serialization/deserialization.
 * Stores skills as their ordinal values for compact representation.
 */
public class SkillAdapter extends TypeAdapter<Skill> {
    
    @Override
    public void write(JsonWriter out, Skill value) throws IOException {
        if (value == null) {
            out.nullValue();
        } else {
            out.value(value.ordinal());
        }
    }

    @Override
    public Skill read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        
        // Handle both string names (legacy) and ordinal values (new format)
        if (in.peek() == JsonToken.STRING) {
            // Legacy format: enum name
            String skillName = in.nextString();
            try {
                return Skill.valueOf(skillName);
            } catch (IllegalArgumentException e) {
                throw new IOException("Invalid skill name: " + skillName, e);
            }
        } else {
            // New format: ordinal value
            int ordinal = in.nextInt();
            Skill[] skills = Skill.values();
            
            if (ordinal >= 0 && ordinal < skills.length) {
                return skills[ordinal];
            } else {
                throw new IOException("Invalid skill ordinal: " + ordinal);
            }
        }
    }
}
