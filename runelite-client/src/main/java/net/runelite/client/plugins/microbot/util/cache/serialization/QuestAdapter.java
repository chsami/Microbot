package net.runelite.client.plugins.microbot.util.cache.serialization;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import net.runelite.api.Quest;

import java.io.IOException;

/**
 * Gson TypeAdapter for Quest enum serialization/deserialization.
 * Stores quests as their ordinal values for compact representation.
 */
public class QuestAdapter extends TypeAdapter<Quest> {
    
    @Override
    public void write(JsonWriter out, Quest value) throws IOException {
        if (value == null) {
            out.nullValue();
        } else {
            out.value(value.ordinal());
        }
    }

    @Override
    public Quest read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        
        // Handle both string names (legacy) and ordinal values (new format)
        if (in.peek() == JsonToken.STRING) {
            // Legacy format: enum name
            String questName = in.nextString();
            try {
                return Quest.valueOf(questName);
            } catch (IllegalArgumentException e) {
                throw new IOException("Invalid quest name: " + questName, e);
            }
        } else {
            // New format: ordinal value
            int ordinal = in.nextInt();
            Quest[] quests = Quest.values();
            
            if (ordinal >= 0 && ordinal < quests.length) {
                return quests[ordinal];
            } else {
                throw new IOException("Invalid quest ordinal: " + ordinal);
            }
        }
    }
}
