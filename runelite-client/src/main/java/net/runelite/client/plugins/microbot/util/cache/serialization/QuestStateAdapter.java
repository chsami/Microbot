package net.runelite.client.plugins.microbot.util.cache.serialization;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import net.runelite.api.QuestState;

import java.io.IOException;

/**
 * Gson TypeAdapter for QuestState enum serialization/deserialization.
 * Stores quest states as their ordinal values for compact representation.
 */
public class QuestStateAdapter extends TypeAdapter<QuestState> {
    
    @Override
    public void write(JsonWriter out, QuestState value) throws IOException {
        if (value == null) {
            out.nullValue();
        } else {
            out.value(value.ordinal());
        }
    }

    @Override
    public QuestState read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        
        // Handle both string names (legacy) and ordinal values (new format)
        if (in.peek() == JsonToken.STRING) {
            // Legacy format: enum name
            String stateName = in.nextString();
            try {
                return QuestState.valueOf(stateName);
            } catch (IllegalArgumentException e) {
                throw new IOException("Invalid quest state name: " + stateName, e);
            }
        } else {
            // New format: ordinal value
            int ordinal = in.nextInt();
            QuestState[] states = QuestState.values();
            
            if (ordinal >= 0 && ordinal < states.length) {
                return states[ordinal];
            } else {
                throw new IOException("Invalid quest state ordinal: " + ordinal);
            }
        }
    }
}
