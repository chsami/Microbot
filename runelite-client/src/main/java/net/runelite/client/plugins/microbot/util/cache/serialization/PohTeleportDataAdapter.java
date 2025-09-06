
package net.runelite.client.plugins.microbot.util.cache.serialization;

import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.util.poh.data.PohTeleport;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class PohTeleportDataAdapter implements JsonSerializer<Map<String, List<PohTeleport>>>,
        JsonDeserializer<Map<String, List<PohTeleport>>> {

    @Override
    public JsonElement serialize(Map<String, List<PohTeleport>> src, Type typeOfSrc, JsonSerializationContext context) {
        JsonArray array = new JsonArray();

        for (Map.Entry<String, List<PohTeleport>> entry : src.entrySet()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("pohTeleport", entry.getKey());

            JsonArray transports = new JsonArray();
            for (PohTeleport transport : entry.getValue()) {
                JsonObject tObj = new JsonObject();
                tObj.addProperty("class", transport.getClass().getName());
                tObj.addProperty("name", transport.name()); // assuming PohTransport is enum
                transports.add(tObj);
            }

            obj.add("transports", transports);
            array.add(obj);
        }

        return array;
    }

    @Override
    public Map<String, List<PohTeleport>> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        Map<String, List<PohTeleport>> map = new HashMap<>();
        JsonArray array = json.getAsJsonArray();

        for (JsonElement elem : array) {
            JsonObject obj = elem.getAsJsonObject();
            String type = obj.get("pohTeleport").getAsString();

            List<PohTeleport> transports = new ArrayList<>();
            JsonArray transportsJson = obj.getAsJsonArray("transports");

            for (JsonElement tElem : transportsJson) {
                JsonObject tObj = tElem.getAsJsonObject();
                String className = tObj.get("class").getAsString();
                String name = tObj.get("name").getAsString();

                try {
                    Class<?> clazz = Class.forName(className);
                    if (clazz.isEnum() && PohTeleport.class.isAssignableFrom(clazz)) {
                        @SuppressWarnings("unchecked")
                        PohTeleport transport = (PohTeleport) Enum.valueOf(
                                (Class<Enum>) clazz.asSubclass(Enum.class),
                                name
                        );
                        transports.add(transport);
                    } else {
                        throw new JsonParseException("Not a valid PohTransport enum: " + className);
                    }
                } catch (ClassNotFoundException e) {
                    log.error("Unknown class during deserialization: {}", className, e);
                    throw new JsonParseException("Unknown class: " + className, e);
                }
            }

            map.put(type, transports);
        }

        return map;
    }
}