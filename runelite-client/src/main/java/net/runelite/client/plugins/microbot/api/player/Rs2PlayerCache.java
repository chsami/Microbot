package net.runelite.client.plugins.microbot.api.player;

import net.runelite.api.WorldView;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.player.models.Rs2PlayerModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Rs2PlayerCache {

    private static int lastUpdatePlayers = 0;
    private static List<Rs2PlayerModel> players = new ArrayList<>();

    public Rs2PlayerQueryable query() {
        return new Rs2PlayerQueryable();
    }

    /**
     * Get all players in the current scene
     * @return Stream of Rs2PlayerModel
     */
    public static Stream<Rs2PlayerModel> getPlayersStream() {

        if (lastUpdatePlayers >= Microbot.getClient().getTickCount()) {
            return players.stream();
        }

        List<Rs2PlayerModel> result = new ArrayList<>();

        for (var id : Microbot.getWorldViewIds()) {
            WorldView worldView = Microbot.getClient().getWorldView(id);
            if (worldView == null) {
                continue;
            }
            // Get all players using the existing Rs2Player utility
            players.addAll(worldView.players()
                    .stream()
                    .filter(Objects::nonNull)
                    .map(Rs2PlayerModel::new)
                    .collect(Collectors.toList()));
        }


        players = result;
        lastUpdatePlayers = Microbot.getClient().getTickCount();
        return players.stream();
    }
}
