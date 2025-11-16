package net.runelite.client.plugins.microbot.api.player;

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

        // Get all players using the existing Rs2Player utility
        List<Rs2PlayerModel> result = Microbot.getClient().getTopLevelWorldView().players()
                .stream()
                .filter(Objects::nonNull)
                .map(Rs2PlayerModel::new)
                .collect(Collectors.toList());

        players = result;
        lastUpdatePlayers = Microbot.getClient().getTickCount();
        return result.stream();
    }
}
