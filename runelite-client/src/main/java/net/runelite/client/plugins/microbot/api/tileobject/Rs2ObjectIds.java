package net.runelite.client.plugins.microbot.api.tileobject;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Rs2ObjectIds {

    private static final Map<String, List<Integer>> BY_NAME = new ConcurrentHashMap<>();

    private Rs2ObjectIds() {
    }

    public static List<Integer> byName(String name) {
        return BY_NAME.computeIfAbsent(name, key -> {
            List<Integer> ids = new ArrayList<>();
            String needle = key.toLowerCase();
            Class<?>[] constants = {
                    net.runelite.api.ObjectID.class,
                    net.runelite.api.gameval.ObjectID.class,
                    MicrobotObjectID.class
            };
            for (Class<?> type : constants) {
                for (Field field : type.getFields()) {
                    if (field.getType() != int.class || !field.getName().toLowerCase().contains(needle)) {
                        continue;
                    }
                    try {
                        ids.add(field.getInt(null));
                    } catch (IllegalAccessException ignored) {
                    }
                }
            }
            return ids;
        });
    }
}
