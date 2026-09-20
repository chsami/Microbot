package net.runelite.client.plugins.microbot.api.player;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.WorldView;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.player.models.Rs2PlayerModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
public final class Rs2PlayerCache {

    private final Client client;
    private final ClientThread clientThread;

    private int lastUpdatePlayers = -1;
    private List<Object> lastContext = java.util.Collections.emptyList();
    private List<Rs2PlayerModel> players = new ArrayList<>();

    @Inject
    public Rs2PlayerCache(Client client, ClientThread clientThread) {
        this.client = client;
        this.clientThread = clientThread;
    }

    public Rs2PlayerQueryable query() {
        return new Rs2PlayerQueryable();
    }

    /**
     * Get all players in the current scene
     * @return Stream of Rs2PlayerModel
     */
    public Stream<Rs2PlayerModel> getStream() {
        return clientThread.invoke(this::snapshot).stream();
    }

    // Membership is copied on the client thread; the models remain live wrappers.
    private List<Rs2PlayerModel> snapshot() {
        if (client.getLocalPlayer() == null || client.getGameState() != net.runelite.api.GameState.LOGGED_IN) {
            players = java.util.Collections.emptyList();
            lastUpdatePlayers = -1;
            lastContext = java.util.Collections.emptyList();
            return players;
        }
        List<WorldView> views = new ArrayList<>();
        List<Object> context = new ArrayList<>();
        context.add(client.getWorld());
        context.add(client.getLocalPlayer());
        for (int id : Microbot.getWorldViewIds()) {
            WorldView view = client.getWorldView(id);
            if (view == null) continue;
            views.add(view);
            context.add(view);
            context.add(view.getScene());
            context.add(view.getPlane());
            context.add(view.getBaseX());
            context.add(view.getBaseY());
        }
        if (lastUpdatePlayers == client.getTickCount() && lastContext.equals(context)) return players;
        List<Rs2PlayerModel> result = new ArrayList<>();

        for (WorldView worldView : views) {
            result.addAll(worldView.players()
                    .stream()
                    .filter(Objects::nonNull)
                    .map(Rs2PlayerModel::new)
                    .collect(Collectors.toList()));
        }

        players = java.util.Collections.unmodifiableList(result);
        lastContext = context;
        lastUpdatePlayers = client.getTickCount();
        return players;
    }

    /**
     * @deprecated Use {@link Microbot#getRs2PlayerCache()}.getStream() instead
     */
    @Deprecated(since = "2.1.8", forRemoval = true)
    public static Stream<Rs2PlayerModel> getPlayersStream() {
        return Microbot.getRs2PlayerCache().getStream();
    }
}
