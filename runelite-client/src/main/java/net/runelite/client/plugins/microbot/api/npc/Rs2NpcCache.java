package net.runelite.client.plugins.microbot.api.npc;

import net.runelite.api.WorldView;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Rs2NpcCache {

    private static int lastUpdateNpcs = 0;
    private static List<Rs2NpcModel> npcs = new ArrayList<>();

    public Rs2NpcQueryable query() {
        return new Rs2NpcQueryable();
    }

    /**
     * Get all NPCs in the current scene across all world views
     *
     * @return Stream of Rs2NpcModel
     */
    public static Stream<Rs2NpcModel> getNpcsStream() {

        if (lastUpdateNpcs >= Microbot.getClient().getTickCount()) {
            return npcs.stream();
        }

        List<Rs2NpcModel> result = new ArrayList<>();

        for (var id : Microbot.getWorldViewIds()) {
            WorldView worldView = Microbot.getClient().getWorldView(id);
            if (worldView == null) {
                continue;
            }

            result.addAll(worldView.npcs()
                    .stream()
                    .filter(Objects::nonNull)
                    .map(Rs2NpcModel::new)
                    .collect(Collectors.toList()));
        }

        npcs = result;
        lastUpdateNpcs = Microbot.getClient().getTickCount();
        return result.stream();
    }
}
