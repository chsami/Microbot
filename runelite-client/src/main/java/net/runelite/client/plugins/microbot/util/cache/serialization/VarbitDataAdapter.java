package net.runelite.client.plugins.microbot.util.cache.serialization;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.cache.model.VarbitData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Gson TypeAdapter for VarbitData serialization/deserialization.
 * Stores varbit data as a compact object with optional contextual information.
 */
public class VarbitDataAdapter extends TypeAdapter<VarbitData> {
    
    @Override
    public void write(JsonWriter out, VarbitData value) throws IOException {
        if (value == null) {
            out.nullValue();
        } else {
            out.beginObject();
            
            // Core data
            out.name("value").value(value.getValue());
            out.name("lastUpdated").value(value.getLastUpdated());
            
            // Previous value (nullable)
            if (value.getPreviousValue() != null) {
                out.name("previousValue").value(value.getPreviousValue());
            }
            
            // Player location (nullable)
            if (value.getPlayerLocation() != null) {
                out.name("location");
                out.beginArray();
                out.value(value.getPlayerLocation().getX());
                out.value(value.getPlayerLocation().getY());
                out.value(value.getPlayerLocation().getPlane());
                out.endArray();
            }
            
            // Nearby NPCs (optional)
            if (!value.getNearbyNpcIds().isEmpty()) {
                out.name("nearbyNpcs");
                out.beginArray();
                for (Integer npcId : value.getNearbyNpcIds()) {
                    out.value(npcId);
                }
                out.endArray();
            }
            
            // Nearby objects (optional)
            if (!value.getNearbyObjectIds().isEmpty()) {
                out.name("nearbyObjects");
                out.beginArray();
                for (Integer objectId : value.getNearbyObjectIds()) {
                    out.value(objectId);
                }
                out.endArray();
            }
            
            out.endObject();
        }
    }

    @Override
    public VarbitData read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        
        int value = 0;
        long lastUpdated = System.currentTimeMillis();
        Integer previousValue = null;
        WorldPoint playerLocation = null;
        List<Integer> nearbyNpcIds = new ArrayList<>();
        List<Integer> nearbyObjectIds = new ArrayList<>();
        
        in.beginObject();
        while (in.hasNext()) {
            String name = in.nextName();
            switch (name) {
                case "value":
                    value = in.nextInt();
                    break;
                case "lastUpdated":
                    lastUpdated = in.nextLong();
                    break;
                case "previousValue":
                    if (in.peek() != JsonToken.NULL) {
                        previousValue = in.nextInt();
                    } else {
                        in.nextNull();
                    }
                    break;
                case "location":
                    if (in.peek() != JsonToken.NULL) {
                        in.beginArray();
                        int x = in.nextInt();
                        int y = in.nextInt();
                        int plane = in.nextInt();
                        playerLocation = new WorldPoint(x, y, plane);
                        in.endArray();
                    } else {
                        in.nextNull();
                    }
                    break;
                case "nearbyNpcs":
                    if (in.peek() != JsonToken.NULL) {
                        in.beginArray();
                        while (in.hasNext()) {
                            nearbyNpcIds.add(in.nextInt());
                        }
                        in.endArray();
                    } else {
                        in.nextNull();
                    }
                    break;
                case "nearbyObjects":
                    if (in.peek() != JsonToken.NULL) {
                        in.beginArray();
                        while (in.hasNext()) {
                            nearbyObjectIds.add(in.nextInt());
                        }
                        in.endArray();
                    } else {
                        in.nextNull();
                    }
                    break;
                default:
                    in.skipValue(); // Skip unknown fields for forwards compatibility
                    break;
            }
        }
        in.endObject();
        
        return new VarbitData(value, lastUpdated, previousValue, playerLocation, 
                             nearbyNpcIds.isEmpty() ? Collections.emptyList() : nearbyNpcIds,
                             nearbyObjectIds.isEmpty() ? Collections.emptyList() : nearbyObjectIds);
    }
}
