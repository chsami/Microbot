package net.runelite.client.plugins.microbot.api.npc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.WorldView;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
public final class Rs2NpcCache {

    private final Client client;
    private final ClientThread clientThread;

    private int lastUpdateNpcs = -1;
    private List<Object> lastContext = java.util.Collections.emptyList();
    private List<Rs2NpcModel> npcs = new ArrayList<>();

    @Inject
    public Rs2NpcCache(Client client, ClientThread clientThread) {
        this.client = client;
        this.clientThread = clientThread;
    }

    public Rs2NpcQueryable query() {
        return new Rs2NpcQueryable();
    }

    /**
     * Get all NPCs in the current scene across all world views
     *
     * @return Stream of Rs2NpcModel
     */
    public Stream<Rs2NpcModel> getStream() {
        return clientThread.invoke(this::snapshot).stream();
    }

    // Membership is copied on the client thread; the models remain live wrappers.
    private List<Rs2NpcModel> snapshot() {
        if (client.getLocalPlayer() == null || client.getGameState() != net.runelite.api.GameState.LOGGED_IN) {
            npcs = java.util.Collections.emptyList();
            lastUpdateNpcs = -1;
            lastContext = java.util.Collections.emptyList();
            return npcs;
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
        if (lastUpdateNpcs == client.getTickCount() && lastContext.equals(context)) return npcs;
        List<Rs2NpcModel> result = new ArrayList<>();

        for (WorldView worldView : views) {

            result.addAll(worldView.npcs()
                    .stream()
                    .filter(Objects::nonNull)
                    .map(Rs2NpcModel::new)
                    .collect(Collectors.toList()));
        }

        npcs = java.util.Collections.unmodifiableList(result);
        lastContext = context;
        lastUpdateNpcs = client.getTickCount();
        return npcs;
    }

    /**
     * @deprecated Use {@link Microbot#getRs2NpcCache()}.getStream() instead
     */
    @Deprecated(since = "2.1.8", forRemoval = true)
    public static Stream<Rs2NpcModel> getNpcsStream() {
        return Microbot.getRs2NpcCache().getStream();
    }
}
